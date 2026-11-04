# Arcane v0.2.0-rc11-debug: repeatable Odin movement + HUD test.
# Run after 'git pull --ff-only origin main'. Does not wipe game data.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$gradle = ".\.bootstrap\gradle-8.11.1\bin\gradle.bat"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$pkg = "io.github.arcanearmorcollector.android"

if (-not (Test-Path $gradle)) { throw "Missing Gradle bootstrap: $gradle" }
if (-not (Test-Path $adb)) { throw "Missing Android ADB: $adb" }

Write-Host "Building clean debug APK..."
& $gradle clean assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

Write-Host "Installing over existing app (save data preserved)..."
& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "ADB install failed" }

Write-Host "Installed version:"
& $adb shell dumpsys package $pkg |
    Select-String 'versionCode=|versionName='

& $adb logcat -c
& $adb shell am force-stop $pkg
& $adb shell am start -n "$pkg/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw "App start failed" }

Write-Host ""
Write-Host "On Odin: enable Touch Settings -> Touch Controls On and Relative aiming."
Write-Host "Enter an arena, leave ALL controls untouched for 15 seconds."
Write-Host "Move LEFT, release and observe; move RIGHT, release and observe."
Write-Host "Tap potions, Stats and the game's own Options icons."
Write-Host "Then test Relative swipe/attack and the Continue/Retry screen."
Read-Host "Press Enter when you have finished the test"

Write-Host ""
Write-Host "Input diagnostics:"
& $adb logcat -d -v brief -s ArcaneRuffle:D ArcaneGamepad:D ArcaneHUD:D ArcanePhase:D |
    Select-String 'ArcaneMovementState|movement-browser-mask|ArcaneHUDCanvas|ArcaneHUD\(|arena touch|Ruffle scene|ArcaneFightEnd|movement-axis' |
    Select-Object -Last 150

Write-Host ""
Write-Host "The app remains installed and its save data has not been cleared."
