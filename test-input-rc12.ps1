# FrostFrontend / Arcane v0.2.0-rc12-debug input test
# In-place install preserves app preferences and Arcane8 game progress.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$gradle = ".\.bootstrap\gradle-8.11.1\bin\gradle.bat"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$pkg = "io.github.arcanearmorcollector.android"
if (-not (Test-Path $gradle)) { throw "Gradle not found: $gradle" }
if (-not (Test-Path $adb)) { throw "ADB not found: $adb" }
& $gradle clean assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "Install failed" }
& $adb logcat -c
& $adb shell am force-stop $pkg
& $adb shell am start -n "$pkg/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw "Cannot start game" }

Write-Host ""
Write-Host "Test in an arena with Relative touch aiming enabled:"
Write-Host "1. Touch around but OUTSIDE the D-pad squares. Character must NOT aim or attack."
Write-Host "2. Touch ON D-pad squares. Movement should work normally and stop on release."
Write-Host "3. Tap potions, Stats and the game's own Options: they should select, NOT attack/turn."
Write-Host "4. Swipe and tap in open arena: relative aiming and firing should still work."
Write-Host "5. Open FrostFrontend Settings: three section tabs share top row with exit X."
Write-Host "   The old help/navigation row has been removed."
Read-Host "Press Enter when finished with these tests"

Write-Host ""
Write-Host "DIAGNOSTICS"
& $adb logcat -d -v brief -s ArcaneHUD:D ArcaneRuffle:D ArcaneGamepad:D ArcanePhase:D |
    Select-String "ArcaneHUDCanvas|ArcaneHUD\(|touch stage=|movement-browser-mask|ArcaneMovementState|Ruffle scene|ArcaneFightEnd" |
    Select-Object -Last 90

Write-Host ""
Write-Host "App remains installed. Save data was not cleared."
