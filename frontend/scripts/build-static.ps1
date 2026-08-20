# 在本机把 Three.js / G6 全部打进静态文件，拷到 dist 与后端 jar 的 static 目录。
# 用法：在 frontend 目录执行  powershell -ExecutionPolicy Bypass -File scripts\build-static.ps1

$ErrorActionPreference = 'Stop'
$Frontend = Split-Path -Parent $PSScriptRoot
$Dist = Join-Path $Frontend 'dist'
$BackendStatic = Join-Path $Frontend '..\backend\src\main\resources\static'

Set-Location $Frontend

Write-Host '==> npm install'
npm install
if ($LASTEXITCODE -ne 0) { throw 'npm install 失败' }

Write-Host '==> vite build（产出纯静态 dist/）'
npm run build
if ($LASTEXITCODE -ne 0) { throw 'npm run build 失败' }

if (-not (Test-Path (Join-Path $Dist 'index.html'))) {
    throw 'dist/index.html 不存在，构建未成功'
}

$assets = Join-Path $Dist 'assets'
$jsCount = @(Get-ChildItem $assets -Filter '*.js' -ErrorAction SilentlyContinue).Count
Write-Host "==> dist/assets 下有 $jsCount 个 JS 文件（应能看到 three-*.js、antv-*.js）"
Get-ChildItem $assets -Filter '*.js' | Select-Object -ExpandProperty Name

Write-Host '==> 复制到后端 static，打 jar 后可直接用 8220 访问页面'
if (Test-Path $BackendStatic) {
    Get-ChildItem $BackendStatic -Force | Where-Object { $_.Name -ne '.gitkeep' } | Remove-Item -Recurse -Force
} else {
    New-Item -ItemType Directory -Path $BackendStatic | Out-Null
}
Copy-Item -Path (Join-Path $Dist '*') -Destination $BackendStatic -Recurse -Force

Write-Host ''
Write-Host '静态包已就绪：'
Write-Host "  1) Nginx：把 $Dist 里全部文件拷到 html 根目录（参考 nginx.conf.example）"
Write-Host '  2) 或 mvn package 后访问 http://服务器:8220/  （页面打进 jar）'
Write-Host '注意：3D 画面在【浏览器】里用显卡画，远程桌面进服务器看经常没有 WebGL，请用本机浏览器打开页面。'
