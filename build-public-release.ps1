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
$resFolder = Join-Path $PSScriptRoot 'app\src\main\res'
$manifest = Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml'
$manifestBackup = Join-Path $HOME 'Downloads\Arcane-Manifest-Backup.xml'
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
if (-not (Test-Path $manifestBackup)) {
    throw "Custom icon manifest backup missing: $manifestBackup. The public build is stopped to avoid replacing your launcher icon."
}
# The original locally designed icon reference was preserved in this backup
# before Git pulled the source-only fallback icon.
$previousManifest = [System.IO.File]::ReadAllText($manifestBackup)
$iconMatch = [regex]::Match($previousManifest, 'android:icon\s*=\s*"(@(mipmap|drawable)/([A-Za-z0-9_]+))"')
if (-not $iconMatch.Success) {
    throw "No Android launcher icon found in backup: $manifestBackup"
}
$iconRef = $iconMatch.Groups[1].Value
$iconType = $iconMatch.Groups[2].Value
$iconName = $iconMatch.Groups[3].Value
$iconFiles = @(Get-ChildItem -Path $resFolder -Recurse -File |
    Where-Object {
        $_.Directory.Name -match "^$iconType($|-)" -and
        $_.BaseName -eq $iconName
    })
if ($iconFiles.Count -eq 0) {
    throw "Your custom $iconRef icon resources are missing from $resFolder. The source-only shield icon will not be substituted."
}
Write-Host "Using your custom launcher icon: $iconRef"

$zipalign = Join-Path $tools.FullName 'zipalign.exe'
$apksigner = Join-Path $tools.FullName 'apksigner.bat'
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

function Confirm-GameFree($file) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($file)
    try {
        $names = @($archive.Entries | ForEach-Object { $_.FullName })
        if (@($names | Where-Object { $_ -match '\.swf$' }).Count -gt 0)
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
$manifestReplaced = $false
try {
    if (Test-Path $game) {
        New-Item -ItemType Directory -Path $privateFolder -Force | Out-Null
        Move-Item -LiteralPath $game -Destination $held
        $moved = $true
    }

    # Use the user's own icon locally without committing any artwork.
    # The original repository manifest is restored byte-for-byte even if build fails.
    $manifestBytes = [System.IO.File]::ReadAllBytes($manifest)
    $currentManifest = [System.Text.Encoding]::UTF8.GetString($manifestBytes)
    if ($currentManifest -notmatch 'android:icon="[^"]+"') {
        throw 'Expected launcher icon attribute not found in AndroidManifest.xml'
    }
    $withCustomIcon = [regex]::Replace($currentManifest,
        'android:icon="[^"]+"', ('android:icon="' + $iconRef + '"'))
    [System.IO.File]::WriteAllText($manifest, $withCustomIcon, (New-Object System.Text.UTF8Encoding($false)))
    $manifestReplaced = $true

    & $gradle clean assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release compilation failed.' }
    if (-not (Test-Path $unsigned)) { throw 'Unsigned release APK not found.' }
    Confirm-GameFree $unsigned
} finally {
    if ($manifestReplaced) {
        [System.IO.File]::WriteAllBytes($manifest, $manifestBytes)
        Write-Host 'PASS: source manifest restored; your custom icon resources were not moved.'
    }
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
$verify = @(& $apksigner verify --verbose --print-certs $signed)
if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
$verify | ForEach-Object { Write-Host $_ }
$signerMatch = [regex]::Match(($verify -join [Environment]::NewLine), 'Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f]{64})')
if (-not $signerMatch.Success) {
    throw 'Could not verify the permanent release signing certificate fingerprint.'
}
$certSha256 = $signerMatch.Groups[1].Value.ToLowerInvariant()
Confirm-GameFree $signed
Remove-Item $aligned -Force
$hash = (Get-FileHash -Algorithm SHA256 $signed).Hash.ToLowerInvariant()
Set-Content -Path (Join-Path $releaseDir 'Arcane-v0.2.0-Android.sha256.txt') -Value "$hash  Arcane-v0.2.0-Android.apk"

# Stage a signed-APK-specific update feed for publication AFTER the actual
# matching GitHub Release and its APK download have been published.
$sourceSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch '^[a-f0-9]{40}
) {
    throw 'Could not identify the release source commit.'
}
$feed = [ordered]@{
    schema_version = 1
    channel = 'stable'
    package = 'io.github.arcanearmorcollector.android'
    latest = [ordered]@{
        package = 'io.github.arcanearmorcollector.android'
        version_name = '0.2.0'
        version_code = 16
        release_tag = 'v0.2.0'
        source_commit = $sourceSha
        certificate_sha256 = $certSha256
        min_sdk = 26
        apk_asset = 'Arcane-v0.2.0-Android.apk'
        apk_size = (Get-Item $signed).Length
        apk_sha256 = $hash
    }
}
$feedFile = Join-Path $releaseDir 'android-stable.json'
[System.IO.File]::WriteAllText($feedFile, ($feed | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'PASS: SIGNED AND GAME-FREE PUBLIC APK'
Write-Host "APK: $signed"
Write-Host "SHA256: $hash"
Write-Host "Update feed STAGED (do not publish until GitHub APK is live): $feedFile"
Write-Host "Keep the keystore safe: $keystore"
