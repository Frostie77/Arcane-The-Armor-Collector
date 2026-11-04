# Arcane v0.2.0-rc14-debug: native stereo test and bigger FrostFrontend tab lettering
# Safe in-place APK update: existing Arcane8 save and preferences are preserved.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$gradle = ".\.bootstrap\gradle-8.11.1\bin\gradle.bat"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$pkg = "io.github.arcanearmorcollector.android"

if (-not (Test-Path $gradle)) { throw "Gradle not found: $gradle" }
if (-not (Test-Path $adb)) { throw "Android SDK ADB not found: $adb" }

& $gradle clean assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Android build failed" }

& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "Android install failed" }

& $adb logcat -c
& $adb shell am force-stop $pkg
& $adb shell am start -n "$pkg/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw "App could not start" }

Write-Host ""
Write-Host "Arcane rc14 - adjust your Odin MEDIA volume to a comfortable level."
Write-Host "Open FrostFrontend > Graphics > Speaker Test."
Write-Host "A message confirms the test. Listen for LEFT, RIGHT and BOTH tones."
Write-Host "Then confirm tab heading readability and compare ordinary game audio."
Read-Host "Press Enter after testing"

Write-Host ""
Write-Host "NATIVE AND RUFFLE AUDIO LOGS"
& $adb logcat -d -v brief -s ArcaneAudioTest:D ArcaneRuffle:D |
    Select-String 'ArcaneAudioTest|audio|sound|ERROR' |
    Select-Object -Last 90
Write-Host ""
Write-Host "INSTALLED VERSION"
& $adb shell dumpsys package $pkg | Select-String 'versionCode=|versionName='

Write-Host "Save data was NOT cleared."
