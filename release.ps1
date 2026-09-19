# 不挂科课表 · 本地发版脚本
#
# 用法（在仓库根目录）：
#   ./release.ps1 -Version 1.6 -Notes @("新增XXX", "修复YYY")           # 完整发版
#   ./release.ps1 -Version 1.6.1 -Notes @("热修XXX") -SkipBuild        # 复用已构建产物
#   ./release.ps1 -Version 1.6 -Notes @("...") -NoPush                 # 仅本地，不推送
#
# 做的事（按序）：
#   1. 版本号落点：build.gradle.kts 的 versionCode(+1)/versionName
#   2. about.html 更新记录顶部插入新版本段（应用内「关于」与 GitHub Release 说明的唯一事实源）
#   3. README 当前版本行与亮点段
#   4. 本地测试 + 签名构建（-SkipBuild 跳过，复用现有 APK）
#   5. git 提交 + 打 tag vX.Y
#   6. gh CLI 创建 GitHub Release（上传本地构建的 APK，说明取自 Notes）
#   7. 推送 main 与 tag（-NoPush 跳过；远端已存在同名 tag 时先删除再推）
#
# 注意：PRIVACY_POLICY.md 与关于页「主要功能」表不自动改，涉及时手动补。

param(
    [string]$Version,
    [string[]]$Notes = @(),
    [switch]$SkipBuild,
    [switch]$NoPush,
    [switch]$NoRelease,
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$gradleFile = Join-Path $root "ComposeApp\app\build.gradle.kts"
$aboutFile = Join-Path $root "ComposeApp\app\src\main\assets\html\about.html"
$readmeFile = Join-Path $root "README.md"
$apkPath = Join-Path $root "ComposeApp\app\build\outputs\apk\release\app-release.apk"

# ---- 1. 计算版本 ----
$gradle = Get-Content $gradleFile -Raw -Encoding UTF8
$curName = [regex]::Match($gradle, 'versionName = "([^"]+)"').Groups[1].Value
$curCode = [int][regex]::Match($gradle, 'versionCode = (\d+)').Groups[1].Value
if (-not $Version) {
    $parts = $curName.Split('.')
    $parts[-1] = [string]([int]$parts[-1] + 1)
    $Version = $parts -join '.'
}
if ($Version -eq $curName) { throw "版本未变化（当前 $curName）：请用 -Version 指定新版本" }
$newCode = $curCode + 1
Write-Host "==> 发版 v$Version（versionCode $newCode，当前 v$curName/$curCode）" -ForegroundColor Cyan

# ---- 2. 版本号落点 ----
$gradle = $gradle -replace 'versionCode = \d+', "versionCode = $newCode" `
                   -replace 'versionName = "[^"]+"', "versionName = `"$Version`""
[System.IO.File]::WriteAllText($gradleFile, $gradle)
Write-Host "==> build.gradle.kts：versionCode=$newCode versionName=$Version"

# ---- 3. about.html 更新记录 ----
if ($Notes.Count -gt 0) {
    $about = Get-Content $aboutFile -Raw -Encoding UTF8
    $li = ($Notes | ForEach-Object { "    <li>$_</li>" }) -join "`n"
    $section = "<p><b>v$Version</b></p>`n  <ul>`n$li`n  </ul>`n  "
    if ($about -notmatch [regex]::Escape("<p><b>v$Version</b></p>")) {
        $about = $about -replace '(<div class="verlist">\s*)', ('$1' + $section)
        [System.IO.File]::WriteAllText($aboutFile, $about)
        Write-Host "==> about.html：已插入 v$Version 更新记录（$($Notes.Count) 条）"
    } else {
        Write-Host "==> about.html：v$Version 段已存在，跳过" -ForegroundColor Yellow
    }
} else {
    Write-Host "==> 未提供 -Notes，about.html 更新记录未改（请手动补）" -ForegroundColor Yellow
}

# ---- 4. README 版本行与亮点段 ----
$readme = Get-Content $readmeFile -Raw -Encoding UTF8
$readme = [regex]::Replace(
    $readme,
    '> 当前版本 \*\*v[^*]+\*\*[^\r\n]*((\r?\n> [^\r\n]*)*)',
    { param($m)
        $hl = if ($Notes.Count -gt 0) { "> v$Version 亮点：" + ($Notes -join "；") + "。" } else { $null }
        $out = "> 当前版本 **v$Version**｜支持 Android 8.0 及以上｜JDK 17 + Android SDK 35 构建"
        if ($hl) { $out += "`n`n$hl" }
        $out
    },
    1,
)
[System.IO.File]::WriteAllText($readmeFile, $readme)
Write-Host "==> README：当前版本已更新为 v$Version"

# ---- 5. 测试与构建 ----
if (-not $SkipBuild) {
    Write-Host "==> 测试与签名构建（需要几分钟）..." -ForegroundColor Cyan
    Push-Location (Join-Path $root "ComposeApp")
    try {
        & .\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw "构建失败（exit $LASTEXITCODE），已中止发版" }
    } finally {
        Pop-Location
    }
} else {
    if (-not (Test-Path $apkPath)) { throw "-SkipBuild 但找不到 APK：$apkPath" }
    Write-Host "==> 跳过构建，复用 $apkPath" -ForegroundColor Yellow
}

# ---- 6. 提交与 tag ----
git -C $root add -- ComposeApp README.md PRIVACY_POLICY.md
$dirty = git -C $root status --porcelain -- ComposeApp README.md PRIVACY_POLICY.md
if ($dirty) {
    git -C $root commit -m "chore(release): v$Version 版本号与更新记录"
    Write-Host "==> 已提交发版改动"
} else {
    Write-Host "==> 无待提交的发版改动" -ForegroundColor Yellow
}
git -C $root tag -f "v$Version" | Out-Null
Write-Host "==> 已打 tag v$Version"

# ---- 7. GitHub Release（本地构建的 APK + 更新记录）----
if (-not $NoRelease) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "未安装 gh CLI，无法创建 Release（先 winget install GitHub.cli 并 gh auth login）" }
    $notesFile = Join-Path $env:TEMP "release-notes-v$Version.md"
    $body = if ($Notes.Count -gt 0) { ("v$Version`n`n" + (($Notes | ForEach-Object { "· $_" }) -join "`n")) } else { "更新内容见应用内「关于 → 更新记录」。" }
    [System.IO.File]::WriteAllText($notesFile, $body)
    # 远端已存在同名 Release/Tag 时先清理（同版本重发）
    gh release view "v$Version" 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { gh release delete "v$Version" --yes }
    gh release create "v$Version" $apkPath --title "v$Version" --notes-file $notesFile
    if ($LASTEXITCODE -ne 0) { throw "gh release create 失败" }
    Write-Host "==> GitHub Release v$Version 已创建（APK + 更新记录）"
}

# ---- 8. 推送 ----
if (-not $NoPush) {
    git -C $root push origin main 2>&1 | Out-Null
    git -C $root push origin ":refs/tags/v$Version" 2>$null | Out-Null   # 远端同名 tag 先删（若有）
    git -C $root push origin "v$Version" 2>&1 | Out-Null
    Write-Host "==> 已推送 main 与 tag v$Version"
} else {
    Write-Host "==> -NoPush：未推送远端（记得手动 git push && git push origin v$Version）" -ForegroundColor Yellow
}

Write-Host "`n发版完成：v$Version（versionCode $newCode）" -ForegroundColor Green
Write-Host "提示：手机端可在「我的 → 检查更新」验证新版本可被发现。"
