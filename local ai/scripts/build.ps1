# Independent build runner for JARVIS Local AI Android project on Windows
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $ScriptDir "..")

Write-Host "==> Building JARVIS standalone Android Application..." -ForegroundColor Cyan
./gradlew.bat assembleDebug testDebugUnitTest --no-daemon --console=plain
Write-Host "==> JARVIS build complete. Artifact at app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Green

