# Arcane v0.2.0-rc13-debug: compact settings and Odin stereo output test.
# This updates the app in place; installed save data and settings are retained.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$gradle = ".\.bootstrap\gradle-8.11.1\bin\gradle.bat"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$pkg = "io.github.arcanearmorcollector.android"
if (-not (Test-Path $gradle)) { throw "Missing Gradle: $gradle" }
if (-not (Test-Path $adb)) { throw "Missing ADB: $adb" }

& $gradle clean assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "Install failed" }

& $adb logcat -c
& $adb shell am force-stop $pkg
& $adb shell am start -n "$pkg/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw "App start failed" }

Write-Host ""
Write-Host "RC13 test:"
Write-Host "1. Open FrostFrontend. Check compact tabs and small name/value rows."
Write-Host "2. Open Graphics > Speaker Test (Left, Right, Both)."
Write-Host "   Listen for a quiet tone from LEFT, then RIGHT, then BOTH speakers."
Write-Host "3. Close FrostFrontend and compare the actual game music/sound."
Write-Host "4. Confirm movement, Relative aim, potions/stats/options still work."
Read-Host "Press Enter after the test"

Write-Host ""
Write-Host "AUDIO / SCENE DIAGNOSTICS"
& $adb logcat -d -v brief -s ArcaneRuffle:D ArcanePhase:D |
    Select-String 'ArcaneAudioTest|Ruffle scene|ArcaneFightEnd|ERROR' |
    Select-Object -Last 65

Write-Host ""
Write-Host "Installed version:"
& $adb shell dumpsys package $pkg |
    Select-String 'versionCode=|versionName='

Write-Host ""
Write-Host "Save data and preferences were not cleared."
