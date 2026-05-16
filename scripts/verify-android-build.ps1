#Requires -Version 5.1
<#
.SYNOPSIS
    Check Android SDK from local.properties and run gradlew assembleDebug.

.DESCRIPTION
    Reads sdk.dir (Gradle form: C\:\\path), verifies platforms\android-*,
    then runs assembleDebug. Exit 2/3 if SDK missing or incomplete.

    Run from repo root:
      powershell -ExecutionPolicy Bypass -File .\scripts\verify-android-build.ps1
#>

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

function Convert-GradleSdkDirToPath {
    param([string]$Line)
    if ($Line -notmatch '^\s*sdk\.dir\s*=\s*(.+)\s*$') { return $null }
    $raw = $Matches[1].Trim()
    $raw = $raw -replace '\\:', ':'
    $raw = $raw -replace '\\\\', '\'
    return $raw
}

$propsPath = Join-Path $RepoRoot "local.properties"
if (-not (Test-Path $propsPath)) {
    Write-Host "[ERROR] Missing local.properties. Create it at repo root with sdk.dir=..." -ForegroundColor Red
    exit 1
}

$sdkDir = $null
Get-Content -LiteralPath $propsPath -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $p = Convert-GradleSdkDirToPath $_
    if ($p) { $sdkDir = $p }
}

if (-not $sdkDir) {
    Write-Host "[ERROR] Could not parse sdk.dir from local.properties." -ForegroundColor Red
    exit 1
}

Write-Host "sdk.dir = $sdkDir"

if (-not (Test-Path $sdkDir)) {
    Write-Host "[HINT] SDK folder not found yet." -ForegroundColor Yellow
    Write-Host "  1) If installing Android Studio: approve UAC, finish installer, open AS once."
    Write-Host "  2) In Android Studio: Settings - Android SDK - install API 35 platform + Build-Tools."
    Write-Host "  3) Match Android SDK Location with sdk.dir in local.properties (default: %LOCALAPPDATA%\Android\Sdk)."
    exit 2
}

$platforms = Join-Path $sdkDir "platforms"
if (-not (Test-Path $platforms) -or -not (Get-ChildItem $platforms -Directory -Filter "android-*" -ErrorAction SilentlyContinue)) {
    Write-Host "[HINT] No platforms\android-* under SDK. Install a platform (API 35) in SDK Manager." -ForegroundColor Yellow
    exit 3
}

$gradle = Join-Path $RepoRoot "gradlew.bat"
if (-not (Test-Path $gradle)) {
    Write-Host "[ERROR] gradlew.bat not found." -ForegroundColor Red
    exit 1
}

Write-Host "Running assembleDebug ..." -ForegroundColor Cyan
& $gradle -p $RepoRoot assembleDebug --no-daemon
exit $LASTEXITCODE
