# deploy.ps1 — 先 sync，再把 website 静态文件 scp 到服务器 nginx 目录
# 目标地址不入库：用环境变量 BUGUAKE_DEPLOY_TARGET（形如 root@1.2.3.4）传入
# SSH 需已配公钥（ssh-copy-id），全程免密；密码不写进任何脚本
# 用法：$env:BUGUAKE_DEPLOY_TARGET='root@x.x.x.x'; ./deploy.ps1
param(
    [string]$Target = $env:BUGUAKE_DEPLOY_TARGET,
    [string]$RemotePath = '/var/www/buguake'
)
$ErrorActionPreference = 'Stop'
if (-not $Target) {
    throw '未指定部署目标。先设置：$env:BUGUAKE_DEPLOY_TARGET = "root@<服务器地址>"'
}
$website = Split-Path $PSScriptRoot -Parent

# 1) 同步内容数据（失败不阻塞：sync.ps1 自带快照兜底并以 0 退出）
& (Join-Path $PSScriptRoot 'sync.ps1')

# 2) 上传页面与数据
$pages = @('index.html', 'faq.html', 'changelog.html', 'privacy.html') |
    ForEach-Object { Join-Path $website $_ }
scp -o BatchMode=yes @pages "${Target}:${RemotePath}/"
if ($LASTEXITCODE -ne 0) { throw "scp 页面上传失败（exit $LASTEXITCODE）" }

# 3) 上传静态资源与数据（增量，只传有变化的文件）
scp -r -o BatchMode=yes -q (Join-Path $website 'assets') (Join-Path $website 'data') "${Target}:${RemotePath}/"
if ($LASTEXITCODE -ne 0) { throw "scp 资源上传失败（exit $LASTEXITCODE）" }

# 3.5) 本地产物目录（downloads/ 有 APK 时一并上传；APK 不入 git）
$dlDir = Join-Path $website 'downloads'
if ((Test-Path $dlDir) -and (Get-ChildItem $dlDir -Filter *.apk -ErrorAction SilentlyContinue)) {
    scp -r -o BatchMode=yes -q $dlDir "${Target}:${RemotePath}/"
    if ($LASTEXITCODE -ne 0) { throw "scp APK 上传失败（exit $LASTEXITCODE）" }
}

# 4) 服务器本地冒烟
$check = ssh -o BatchMode=yes $Target "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/"
if ($check -ne '200') { throw "部署后首页状态异常：$check" }
Write-Host "[deploy] 完成 → http://$($Target -replace '.*@','')/"
