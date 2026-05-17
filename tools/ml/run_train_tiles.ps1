#Requires -Version 5.1
<#
.SYNOPSIS
  在项目根目录创建 .venv-tiles，安装 TensorFlow CPU 并导出 tiles-v1.tflite。

.DESCRIPTION
  依赖安装请优先用 `tools/ml/install_tiles_venv.ps1`（支持 -Mirror，大包不易断）。
  本脚本在 venv 已存在且已装 TF 时，可直接把训练参数传给 train_tile_classifier_v1.py。
  若 pip 反复失败，可使用 GitHub Actions「train-tiles-tflite」在云端构建后下载制品。

.EXAMPLE
  .\tools\ml\run_train_tiles.ps1 --data-dir F:\AI\majiang\tools\png --epochs 30
#>
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

$Venv = Join-Path $Root ".venv-tiles"
if (-not (Test-Path $Venv)) {
  python -m venv $Venv
}
& (Join-Path $Venv "Scripts\python.exe") -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed (exit $LASTEXITCODE)" }
$py = Join-Path $Venv "Scripts\python.exe"
$tfOk = $false
& $py -c "import tensorflow" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { $tfOk = $true }
if (-not $tfOk) {
  Write-Host "未检测到 TensorFlow，正在安装（首次约数百 MB，请耐心等待）…" -ForegroundColor Yellow
  $Req = Join-Path $PSScriptRoot "requirements-tiles-cpu.txt"
  & (Join-Path $Venv "Scripts\pip.exe") install --retries 25 --timeout 300 --no-cache-dir -r $Req
  if ($LASTEXITCODE -ne 0) {
    throw "pip install 失败。请运行: .\tools\ml\install_tiles_venv.ps1 -Mirror https://pypi.tuna.tsinghua.edu.cn/simple"
  }
}
if ($args.Count -gt 0) {
  & $py tools/ml/train_tile_classifier_v1.py @args
} else {
  & $py tools/ml/train_tile_classifier_v1.py --epochs 8 --n-per-class 200
}
if ($LASTEXITCODE -ne 0) { throw "train_tile_classifier_v1.py failed (exit $LASTEXITCODE)" }
Write-Host "完成。输出: app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite" -ForegroundColor Green
