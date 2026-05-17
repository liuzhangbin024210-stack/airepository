#Requires -Version 5.1
<#
.SYNOPSIS
  在项目根创建 .venv-tiles 并安装牌面训练依赖（TensorFlow CPU）。

.DESCRIPTION
  大包易断线：默认 --no-cache-dir；可用 -Mirror 指定国内 PyPI 镜像加速/稳定下载。
  例：.\tools\ml\install_tiles_venv.ps1 -Mirror https://pypi.tuna.tsinghua.edu.cn/simple
#>
param(
    [string] $Mirror = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

$Venv = Join-Path $Root ".venv-tiles"
if (-not (Test-Path $Venv)) {
    python -m venv $Venv
}

$pip = Join-Path $Venv "Scripts\pip.exe"
$py = Join-Path $Venv "Scripts\python.exe"
& $py -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed" }

$req = Join-Path $Root "tools/ml/requirements-tiles-cpu.txt"
$pipArgs = @(
    "install", "--no-cache-dir", "--retries", "25", "--timeout", "300",
    "-r", $req
)
if ($Mirror) {
    $pipArgs += "-i"
    $pipArgs += $Mirror
    $uri = [Uri]$Mirror
    $pipArgs += "--trusted-host"
    $pipArgs += $uri.Host
    $pipArgs += "--trusted-host"
    $pipArgs += "files.pythonhosted.org"
}

& $pip @pipArgs
if ($LASTEXITCODE -ne 0) {
    throw "pip 安装失败（常见：IncompleteRead 断线、SSL 错误）。请重试；或换镜像：.\tools\ml\install_tiles_venv.ps1 -Mirror https://mirrors.aliyun.com/pypi/simple/ ；或使用 GitHub Actions（docs/CI-tiles-v1-github.md）。"
}

& $py -c "import tensorflow as tf; print('TensorFlow', tf.__version__)"
Write-Host "完成。训练示例：" -ForegroundColor Green
Write-Host "  .\.venv-tiles\Scripts\python.exe tools/ml/train_tile_classifier_v1.py --data-dir F:\AI\majiang\tools\png --epochs 30"
