# sync.ps1 — 构建时同步 GitHub Releases ⊕ 应用内 about.html → data/changelog.json + 注入版本号
# 失败兜底：gh/网络失败时保留已有 changelog.json 快照继续（退出码 0），绝不阻塞部署
# 用法：./sync.ps1    （在 website/tools/ 下，或 powershell -File sync.ps1）
param(
    [string]$Repo = 'salt-fishes/buguake-timetable'
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8   # gh 输出含中文，统一按 UTF-8 显示
$website = Split-Path $PSScriptRoot -Parent
$repoRoot = Split-Path $website -Parent
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Get-UnmarkedItems([string]$body) {
    $items = @()
    foreach ($line in ($body -split "`n")) {
        $t = $line.Trim()
        if (-not $t) { continue }
        $t = $t -replace '^[·•\-]\s*', ''
        if ($t -match '^v?\d+\.\d' -and $t.Length -lt 40) { continue }  # 剥标题行
        $items += $t
    }
    return $items
}

# 1) 解析 about.html verlist（兜底全集）
$map = @{}
$aboutPath = Join-Path $repoRoot 'ComposeApp/app/src/main/assets/html/about.html'
if (Test-Path $aboutPath) {
    $about = Get-Content $aboutPath -Raw -Encoding UTF8
    foreach ($m in [regex]::Matches($about, '<p><b>(v[\d.]+)</b></p>\s*<ul>(.*?)</ul>', 'Singleline')) {
        $ver = $m.Groups[1].Value.Substring(1)
        $items = @()
        foreach ($li in [regex]::Matches($m.Groups[2].Value, '<li>(.*?)</li>', 'Singleline')) {
            $txt = [regex]::Replace($li.Groups[1].Value, '<[^>]+>', '')
            $txt = [System.Net.WebUtility]::HtmlDecode($txt).Trim()
            if ($txt) { $items += $txt }
        }
        $map[$ver] = @{ version = $ver; date = $null; source = 'about'; url = $null; items = $items }
    }
}

# 2) GitHub Releases（优先覆盖同版本）
# 注意：PS 5.1 管道捕获外部程序输出会按控制台编码（GBK）解码、搅坏 UTF-8 JSON，
# 因此用 cmd 字节级重定向落盘，再按 UTF-8 读取
$relOk = $false
try {
    $tmpDir = Join-Path $PSScriptRoot '.tmp'
    New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
    $relFile = Join-Path $tmpDir 'releases.json'
    cmd /c "gh api repos/$Repo/releases > `"$relFile`"" 2>$null
    if ($LASTEXITCODE -eq 0 -and (Test-Path $relFile) -and (Get-Item $relFile).Length -gt 2) {
        $rels = [IO.File]::ReadAllText($relFile, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
        if ($rels) {
            foreach ($r in $rels) {
                $ver = $r.tag_name -replace '^v', ''
                $map[$ver] = @{
                    version = $ver
                    date    = ([datetime]$r.published_at).ToString('yyyy-MM-dd')
                    source  = 'release'
                    url     = $r.html_url
                    items   = Get-UnmarkedItems ($r.body -replace "^[^\r\n]*\r?\n", '')
                }
            }
            $relOk = $true
        }
    }
    if (-not $relOk) { Write-Warning '[sync] gh api 拉取失败，Releases 部分沿用 about/快照' }
} catch {
    Write-Warning "[sync] Releases 解析异常（$($_.Exception.Message)），沿用 about/快照"
}

# 3) 按语义化版本降序
$ordered = $map.Values | Sort-Object -Property @{Expression = {
    $p = @($_.version -split '\.') | ForEach-Object { [int]$_ }
    while ($p.Count -lt 3) { $p += 0 }
    ($p[0] * 1000000 + $p[1] * 1000 + $p[2])
}; Descending = $true}

if (-not $ordered) {
    Write-Warning '[sync] 无任何版本数据，保留现有 changelog.json'
    exit 0
}

$latest = $ordered[0].version
$out = @{
    generatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    latest      = $latest
    versions    = @($ordered | ForEach-Object {
        @{ version = $_.version; date = $_.date; source = $_.source; url = $_.url; items = @($_.items) }
    })
}
$jsonPath = Join-Path $website 'data/changelog.json'
[IO.File]::WriteAllText($jsonPath, ($out | ConvertTo-Json -Depth 6), $utf8NoBom)
Write-Host "[sync] changelog.json → latest v$latest，$(@($ordered).Count) 个版本（Releases 同步=$(if ($relOk) {'ok'} else {'fallback'}))"

# 4) 版本号注入 index.html（源：build.gradle.kts versionName/versionCode）
$bgPath = Join-Path $repoRoot 'ComposeApp/app/build.gradle.kts'
$indexPath = Join-Path $website 'index.html'
if ((Test-Path $bgPath) -and (Test-Path $indexPath)) {
    $bg = Get-Content $bgPath -Raw -Encoding UTF8
    $vn = if ($bg -match 'versionName\s*=\s*"([^"]+)"') { $Matches[1] } else { $latest }
    $vc = if ($bg -match 'versionCode\s*=\s*(\d+)') { $Matches[1] } else { '' }
    $idx = Get-Content $indexPath -Raw -Encoding UTF8
    $orig = $idx
    $idx = $idx -replace '(data-latest-badge"?>)v[\d.]+', "`${1}v$vn"
    $idx = $idx -replace '(data-latest-text"?>)v[\d.]+', "`${1}v$vn"
    $idx = $idx -replace '(data-latest-download[^>]*>)下载 v[\d.]+', "`${1}下载 v$vn"
    $idx = $idx -replace '(（versionCode )\d+(）)', "`${1}$vc`${2}"
    # 本站直链 APK 路径随版本改写（downloads/buguake-vX.Y.Z.apk，文件由 deploy 前放置服务器）
    $idx = $idx -replace '(downloads/buguake-v)[\d.]+(\.apk)', "`${1}$vn`${2}"
    if ($idx -ne $orig) {
        [IO.File]::WriteAllText($indexPath, $idx, $utf8NoBom)
        Write-Host "[sync] index.html 版本号注入 → v$vn (versionCode $vc)"
    } else {
        Write-Host "[sync] index.html 版本号已是 v$vn"
    }
    # changelog.html 底部直链同步
    $clPath = Join-Path $website 'changelog.html'
    if (Test-Path $clPath) {
        $cl = Get-Content $clPath -Raw -Encoding UTF8
        $cl2 = $cl -replace '(downloads/buguake-v)[\d.]+(\.apk)', "`${1}$vn`${2}"
        if ($cl2 -ne $cl) {
            [IO.File]::WriteAllText($clPath, $cl2, $utf8NoBom)
            Write-Host "[sync] changelog.html 直链注入 → buguake-v$vn.apk"
        }
    }
}
exit 0
