# Arcane v0.2.0: isolated, asset-free first-run SWF import test.
# Builds and installs a DIFFERENT Android package; current Debug saves stay intact.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$gradle = '.\.bootstrap\gradle-8.11.1\bin\gradle.bat'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$game = Join-Path $PSScriptRoot 'app\src\main\assets\www\arcane.swf'
$ruffle = Join-Path $PSScriptRoot 'app\src\main\assets\www\ruffle\ruffle.js'
$privateFolder = Join-Path $PSScriptRoot '.private-game'
$held = Join-Path $privateFolder 'arcane.swf'
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
$package = 'io.github.arcanearmorcollector.android.importtest'

if (-not (Test-Path $gradle)) { throw 'Gradle bootstrap missing. Run prepare.ps1 first.' }
if (-not (Test-Path $adb)) { throw 'ADB missing from Android SDK.' }
if (-not (Test-Path $ruffle)) { throw 'Ruffle runtime missing. Run prepare.ps1 first.' }
if (Test-Path $held) { throw "Previous private backup exists: $held. Restore it before continuing." }

$moved = $false
try {
    if (Test-Path $game) {
        New-Item -ItemType Directory -Path $privateFolder -Force | Out-Null
        Move-Item -LiteralPath $game -Destination $held
        $moved = $true
    }

    & $gradle clean assembleDebug '-ParcaneImportTest=true'
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    if (-not (Test-Path $apk)) { throw "APK missing: $apk" }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $names = @($archive.Entries | ForEach-Object { $_.FullName })
        if (@($names | Where-Object { $_ -match '(^|/)arcane\.swf$' }).Count -gt 0) {
            throw 'SAFETY CHECK FAILED: packaged APK contains the original game.'
        }
        if (-not ($names -contains 'assets/www/ruffle/ruffle.js')) {
            throw 'Ruffle runtime missing from the APK.'
        }
        if (-not ($names -contains 'assets/www/index.html')) {
            throw 'Game wrapper HTML missing from the APK.'
        }
        Write-Host 'PASS: APK contains Ruffle and wrapper, but no original game SWF.'
    } finally {
        $archive.Dispose()
    }
} finally {
    if ($moved) {
        Move-Item -LiteralPath $held -Destination $game
        Write-Host 'PASS: original personal SWF restored to the project.'
    }
}

if (Test-Path $game) {
    & $adb push $game '/sdcard/Download/Arcane-Test-Import.swf'
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy personal game file to Odin Downloads.' }
}

& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'ADB installation failed.' }
& $adb shell am force-stop $package
& $adb shell am start -n "$package/io.github.arcanearmorcollector.android.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'Could not launch import-test app.' }

Write-Host ''
Write-Host 'PASS: isolated import-test APK installed. Original Debug app and saves untouched.'
Write-Host 'On Odin: Select Game File > Downloads > Arcane-Test-Import.swf.'
Write-Host 'Confirm first launch, touchscreen/gamepad, and save after restarting test app.'
