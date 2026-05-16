#Requires -Version 5.1
<#
.SYNOPSIS
  在项目根目录创建 .venv-policy，安装 TensorFlow CPU 并导出 policy-v1.tflite。

.DESCRIPTION
  与 GitHub Actions「train-policy-tflite」使用相同训练参数；本机 pip 慢或失败时请用 Actions 下载制品。
#>
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

$Venv = Join-Path $Root ".venv-policy"
if (-not (Test-Path $Venv)) {
  python -m venv $Venv
}
& (Join-Path $Venv "Scripts\python.exe") -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed (exit $LASTEXITCODE)" }
& (Join-Path $Venv "Scripts\pip.exe") install --retries 15 --timeout 120 "tensorflow-cpu>=2.14,<2.19" "numpy>=1.24"
if ($LASTEXITCODE -ne 0) { throw "pip install tensorflow failed (exit $LASTEXITCODE). 可改用 GitHub Actions：train-policy-tflite" }
& (Join-Path $Venv "Scripts\python.exe") tools/ml/train_policy_v1.py --epochs 3 --samples 512
if ($LASTEXITCODE -ne 0) { throw "train_policy_v1.py failed (exit $LASTEXITCODE)" }
Write-Host "完成。输出: app/src/main/assets/ml/xuezhan_mahjong_default/policy-v1.tflite" -ForegroundColor Green
