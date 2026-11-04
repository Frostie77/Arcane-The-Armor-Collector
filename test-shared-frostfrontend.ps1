# Isolated shared-FrostFrontend migration test; never touches existing Arcane app data.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$submodule = Join-Path $PSScriptRoot 'third_party\FrostFrontend\frostfrontend\build.gradle'
if (!(Test-Path $submodule)) {
    Write-Host 'Initializing the pinned shared FrostFrontend submodule...'
    & git submodule update --init --recursive
    if ($LASTEXITCODE -ne 0) { throw 'FrostFrontend submodule setup failed.' }
}
if (!(Test-Path $submodule)) { throw 'FrostFrontend library was not found.' }

$ruffle = Join-Path $PSScriptRoot 'app\src\main\assets\www\ruffle\ruffle.js'
$gradle = Join-Path $PSScriptRoot '.bootstrap\gradle-8.11.1\bin\gradle.bat'
if (!(Test-Path $ruffle) -or !(Test-Path $gradle)) {
    Write-Host 'Preparing Ruffle and Gradle build tools...'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File '.\prepare.ps1'
    if ($LASTEXITCODE -ne 0) { throw 'Project preparation failed.' }
}
if (!(Test-Path $gradle)) { throw 'Gradle 8.11.1 is missing.' }
if (!(Test-Path $ruffle)) { throw 'Ruffle assets are missing.' }

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
if (!(Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
}
if (!(Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'JDK is missing.' }
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (!(Test-Path $sdk)) { throw 'Android SDK is missing.' }
$env:ANDROID_HOME = $sdk
Set-Content -Path 'local.properties' -Value "sdk.dir=$($sdk.Replace('\','/'))"

$game = Join-Path $PSScriptRoot 'app\src\main\assets\www\arcane.swf'
$heldFolder = Join-Path $PSScriptRoot '.private-game'
$held = Join-Path $heldFolder 'arcane-shared-frontend-test.swf'
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
$moved = $false
try {
    if (Test-Path $game) {
        if (Test-Path $held) {
            throw "Previous held game file found at $held. Restore it before continuing."
        }
        New-Item -ItemType Directory -Path $heldFolder -Force | Out-Null
        Move-Item -LiteralPath $game -Destination $held
        $moved = $true
    }
    Write-Host 'Building separate Arcane.importtest APK with shared FrostFrontend...'
    & $gradle clean assembleDebug '-ParcaneImportTest=true'
    if ($LASTEXITCODE -ne 0) { throw 'Shared FrostFrontend compilation failed.' }
    if (!(Test-Path $apk)) { throw 'Test APK not found.' }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $entries = @($zip.Entries | ForEach-Object { $_.FullName })
        if (@($entries | Where-Object { $_ -match '(?i)\.swf$' }).Count -gt 0) {
            throw 'TEST FAILED: APK contains the original Flash game.'
        }
        if (!($entries -contains 'assets/www/ruffle/ruffle.js')) {
            throw 'TEST FAILED: Ruffle is missing from the APK.'
        }
        if (!($entries -contains 'assets/www/index.html')) {
            throw 'TEST FAILED: app HTML is missing.'
        }
    } finally { $zip.Dispose() }
} finally {
    if ($moved) {
        Move-Item -LiteralPath $held -Destination $game
        Write-Host 'Personal SWF restored safely.'
    }
}

Write-Host 'PASS: isolated game-free APK built with shared FrostFrontend.'
if (Test-Path $adb) {
    $devices = & $adb devices
    if (($devices -join "`n") -match '(?m)^\S+\s+device$') {
        & $adb install -r $apk
        if ($LASTEXITCODE -ne 0) { throw 'Isolated test APK installation failed.' }
        Write-Host 'PASS: separate .importtest app installed. Existing Arcane remains untouched.'
    } else {
        Write-Host 'Odin not connected/authorized. Install the test APK manually.'
    }
}
Write-Host "Test APK: $apk"
Write-Host 'Test: launch the separate Arcane import-test app and import your game SWF.'
Write-Host 'Verify all five tabs, gamepad mapping and warnings, movement, aiming, touch, stereo, and saved settings.'
Write-Host 'NOTE: stable update checks are disabled in debug/import-test builds by design.'
