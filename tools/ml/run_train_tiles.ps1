#Requires -Version 5.1
<#
.SYNOPSIS
  在项目根目录创建 .venv-tiles，安装 TensorFlow CPU 并导出 tiles-v1.tflite。

.DESCRIPTION
  解决 Windows 下全局 pip 安装 TensorFlow 体积大、耗时长的问题；首次运行请耐心等待。
  若 pip 反复失败，可使用 GitHub Actions「train-tiles-tflite」在云端构建后下载制品。
#>
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

$Venv = Join-Path $Root ".venv-tiles"
if (-not (Test-Path $Venv)) {
  python -m venv $Venv
}
& (Join-Path $Venv "Scripts\python.exe") -m pip install --upgrade pip
& (Join-Path $Venv "Scripts\pip.exe") install "tensorflow-cpu>=2.14,<2.19" "numpy>=1.24"
& (Join-Path $Venv "Scripts\python.exe") tools/ml/train_tile_classifier_v1.py --epochs 8 --n-per-class 200
Write-Host "完成。输出: app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite" -ForegroundColor Green
