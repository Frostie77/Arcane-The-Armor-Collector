# Arcane v0.2.0 public release: signed APK without the original Flash game.
# Never uninstalls or modifies the currently installed Debug app or its saves.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$gradle = '.\.bootstrap\gradle-8.11.1\bin\gradle.bat'
$game = Join-Path $PSScriptRoot 'app\src\main\assets\www\arcane.swf'
$ruffle = Join-Path $PSScriptRoot 'app\src\main\assets\www\ruffle\ruffle.js'
$privateFolder = Join-Path $PSScriptRoot '.private-game'
$held = Join-Path $privateFolder 'arcane.swf'
$unsigned = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
$releaseDir = Join-Path $PSScriptRoot 'release'
$aligned = Join-Path $releaseDir 'Arcane-v0.2.0-aligned.apk'
$signed = Join-Path $releaseDir 'Arcane-v0.2.0-Android.apk'
$keystore = Join-Path $HOME '.android\Arcane-Release.keystore'
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
$buildToolsDir = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools'
$tools = Get-ChildItem $buildToolsDir -Directory |
    Where-Object { (Test-Path (Join-Path $_.FullName 'apksigner.bat')) -and
                   (Test-Path (Join-Path $_.FullName 'zipalign.exe')) } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not (Test-Path $gradle)) { throw 'Gradle bootstrap missing. Run prepare.ps1 first.' }
if (-not (Test-Path $ruffle)) { throw 'Ruffle runtime missing. Run prepare.ps1 first.' }
if (-not $tools) { throw 'Android zipalign/apksigner not found in SDK build-tools.' }
if (-not (Test-Path $keytool)) { throw 'JDK keytool missing.' }
if (Test-Path $held) { throw "Previous private backup exists: $held. Restore it before continuing." }

$zipalign = Join-Path $tools.FullName 'zipalign.exe'
$apksigner = Join-Path $tools.FullName 'apksigner.bat'
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

function Confirm-GameFree($file) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($file)
    try {
        $names = @($archive.Entries | ForEach-Object { $_.FullName })
        if (@($names | Where-Object { $_ -match '(^|/)arcane\.swf$' }).Count -gt 0)
            { throw "SAFETY CHECK FAILED: Game SWF found inside $file" }
        if (-not ($names -contains 'assets/www/ruffle/ruffle.js'))
            { throw 'Ruffle is missing from the release APK.' }
        if (-not ($names -contains 'assets/www/index.html'))
            { throw 'Wrapper HTML is missing from the release APK.' }
    } finally {
        $archive.Dispose()
    }
}

$moved = $false
try {
    if (Test-Path $game) {
        New-Item -ItemType Directory -Path $privateFolder -Force | Out-Null
        Move-Item -LiteralPath $game -Destination $held
        $moved = $true
    }
    & $gradle clean assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release compilation failed.' }
    if (-not (Test-Path $unsigned)) { throw 'Unsigned release APK not found.' }
    Confirm-GameFree $unsigned
} finally {
    if ($moved) {
        Move-Item -LiteralPath $held -Destination $game
        Write-Host 'PASS: personal game SWF restored.'
    }
}

if (-not (Test-Path $keystore)) {
    New-Item -ItemType Directory -Path (Split-Path $keystore) -Force | Out-Null
    Write-Host 'Create your permanent Android release signing key. Remember its password.'
    & $keytool -genkeypair -keystore $keystore -alias arcane-release -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12 -dname 'CN=Arcane Android Release'
    if ($LASTEXITCODE -ne 0) { throw 'Release signing key creation failed.' }
}
if (Test-Path $aligned) { Remove-Item $aligned -Force }
if (Test-Path $signed) { Remove-Item $signed -Force }

& $zipalign -p -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }
Write-Host 'Sign the release using your permanent keystore password when prompted.'
& $apksigner sign --ks $keystore --ks-key-alias arcane-release --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw 'Release APK signing failed.' }
& $apksigner verify --verbose --print-certs $signed
if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
Confirm-GameFree $signed
Remove-Item $aligned -Force
$hash = (Get-FileHash -Algorithm SHA256 $signed).Hash.ToLowerInvariant()
Set-Content -Path (Join-Path $releaseDir 'Arcane-v0.2.0-Android.sha256.txt') -Value "$hash  Arcane-v0.2.0-Android.apk"
Write-Host 'PASS: SIGNED AND GAME-FREE PUBLIC APK'
Write-Host "APK: $signed"
Write-Host "SHA256: $hash"
Write-Host "Keep the keystore safe: $keystore"
