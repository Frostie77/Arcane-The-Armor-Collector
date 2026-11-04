# Arcane v0.2.1 candidate: signed, game-free, custom icon required; no install or publish.
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
$aligned = Join-Path $releaseDir 'Arcane-v0.2.1-aligned.apk'
$signed = Join-Path $releaseDir 'Arcane-v0.2.1-Android.apk'
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
if (-not (Test-Path $keystore)) { throw 'Original Arcane signing keystore missing. Refusing to create a replacement.' }
if (Test-Path $held) { throw "Previous private backup exists: $held. Restore it before continuing." }
# Confirm the original stable certificate and version BEFORE running Gradle.
$published = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'updates\android-stable.json') | ConvertFrom-Json
if ($published.channel -ne 'stable' -or $published.latest.version_name -ne '0.2.0' -or
    $published.latest.version_code -ne 16 -or
    $published.latest.certificate_sha256 -notmatch '^[a-fA-F0-9]{64}
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
if ($iconMatch.Groups[3].Value -eq 'ic_arcane_launcher') {
    throw 'Custom icon backup refers to deleted placeholder.'
}
$iconRef = $iconMatch.Groups[1].Value
$iconType = $iconMatch.Groups[2].Value
$iconName = $iconMatch.Groups[3].Value
$iconFiles = @(Get-ChildItem -Path $resFolder -Recurse -File |
    Where-Object {
        $_.Directory.Name -match ('^' + [regex]::Escape($iconType) + '(-|$)') -and
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
    if ($currentManifest -match 'android:icon=') {
        throw 'Unexpected icon in source manifest; no fallback is permitted.'
    }
    if ($currentManifest -notmatch '(?m)^    <application\s*
    $manifestReplaced = $true
    [System.IO.File]::WriteAllText($manifest, $withCustomIcon, (New-Object System.Text.UTF8Encoding($false)))

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
if ($certSha256 -ne $expectedSigner) {
    Remove-Item -LiteralPath $signed -Force
    throw 'CRITICAL: Signing identity differs from published v0.2.0. Candidate deleted.'
}
Confirm-GameFree $signed

# Verify that the signed APK declares the user's actual custom launcher artwork.
$badging = @(& $aapt dump badging $signed)
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the signed APK icon.' }
$iconBadging = ($badging | Where-Object { $_ -match '^application(?:-icon.*)?: ' }) -join [Environment]::NewLine
$expectedIconName = [regex]::Escape($iconName)
if ($iconBadging -notmatch "(?i)$expectedIconName\.(?:png|webp|xml)") {
    throw "Signed APK does not declare the correct custom icon: $iconRef"
}
Write-Host "PASS: custom launcher icon verified in signed APK: $iconRef"
Remove-Item $aligned -Force
$hash = (Get-FileHash -Algorithm SHA256 $signed).Hash.ToLowerInvariant()
Set-Content -Path (Join-Path $releaseDir 'Arcane-v0.2.1-Android.sha256.txt') -Value "$hash  Arcane-v0.2.1-Android.apk"

# Stage a signed-APK-specific update feed for publication AFTER the actual
# matching GitHub Release and its APK download have been published.
$sourceSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch ('^[a-f0-9]{40}' + [char]36)) {
    throw 'Could not identify the release source commit.'
}
$feed = [ordered]@{
    schema_version = 1
    channel = 'stable'
    package = 'io.github.arcanearmorcollector.android'
    latest = [ordered]@{
        package = 'io.github.arcanearmorcollector.android'
        version_name = '0.2.1'
        version_code = 17
        release_tag = 'v0.2.1'
        source_commit = $sourceSha
        certificate_sha256 = $certSha256
        min_sdk = 26
        apk_asset = 'Arcane-v0.2.1-Android.apk'
        apk_size = (Get-Item $signed).Length
        apk_sha256 = $hash
    }
}
$feedFile = Join-Path $releaseDir 'android-stable.json'
[System.IO.File]::WriteAllText($feedFile, ($feed | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'PASS: v0.2.1 SIGNED, GAME-FREE RELEASE CANDIDATE (NOT PUBLISHED)'
Write-Host "APK: $signed"
Write-Host "SHA256: $hash"
Write-Host "Update feed STAGED (do not publish until GitHub APK is live): $feedFile"
Write-Host "PASS: same release signing identity as v0.2.0: $certSha256"
Write-Host "No APK installed; no GitHub release published."
Write-Host "Keep the keystore safe: $keystore"
) {
        throw 'Android application tag not found.'
    }
    $withCustomIcon = $currentManifest.Replace('    <application',
        '    <application' + [Environment]::NewLine + '        android:icon="' + $iconRef + '"')
    $manifestReplaced = $true
    [System.IO.File]::WriteAllText($manifest, $withCustomIcon, (New-Object System.Text.UTF8Encoding($false)))

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
Set-Content -Path (Join-Path $releaseDir 'Arcane-v0.2.1-Android.sha256.txt') -Value "$hash  Arcane-v0.2.1-Android.apk"

# Stage a signed-APK-specific update feed for publication AFTER the actual
# matching GitHub Release and its APK download have been published.
$sourceSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch ('^[a-f0-9]{40}' + [char]36)) {
    throw 'Could not identify the release source commit.'
}
$feed = [ordered]@{
    schema_version = 1
    channel = 'stable'
    package = 'io.github.arcanearmorcollector.android'
    latest = [ordered]@{
        package = 'io.github.arcanearmorcollector.android'
        version_name = '0.2.1'
        version_code = 17
        release_tag = 'v0.2.1'
        source_commit = $sourceSha
        certificate_sha256 = $certSha256
        min_sdk = 26
        apk_asset = 'Arcane-v0.2.1-Android.apk'
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
) {
    throw 'Published Arcane v0.2.0 certificate metadata is missing or unexpected.'
}
$expectedSigner = $published.latest.certificate_sha256.ToLowerInvariant()
$gradleSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'app\build.gradle')
if ($gradleSource -notmatch 'versionCode\s+17' -or $gradleSource -notmatch "versionName\s+'0\.2\.1'") {
    throw 'Expected Android version v0.2.1 / code 17.'
}
if (-not (Test-Path (Join-Path $PSScriptRoot 'third_party\FrostFrontend\frostfrontend\build.gradle'))) {
    throw 'Shared FrostFrontend submodule is missing. Initialize Git submodules.'
}
$aapt = Join-Path $tools.FullName 'aapt.exe'
if (-not (Test-Path $aapt)) { throw 'Cannot inspect built icon: aapt.exe missing.' }
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
if ($iconMatch.Groups[3].Value -eq 'ic_arcane_launcher') {
    throw 'Custom icon backup refers to deleted placeholder.'
}
$iconRef = $iconMatch.Groups[1].Value
$iconType = $iconMatch.Groups[2].Value
$iconName = $iconMatch.Groups[3].Value
$iconFiles = @(Get-ChildItem -Path $resFolder -Recurse -File |
    Where-Object {
        $_.Directory.Name -match ('^' + [regex]::Escape($iconType) + '(-|$)') -and
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
    if ($currentManifest -match 'android:icon=') {
        throw 'Unexpected icon in source manifest; no fallback is permitted.'
    }
    if ($currentManifest -notmatch '(?m)^    <application\s*
    $manifestReplaced = $true
    [System.IO.File]::WriteAllText($manifest, $withCustomIcon, (New-Object System.Text.UTF8Encoding($false)))

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
Set-Content -Path (Join-Path $releaseDir 'Arcane-v0.2.1-Android.sha256.txt') -Value "$hash  Arcane-v0.2.1-Android.apk"

# Stage a signed-APK-specific update feed for publication AFTER the actual
# matching GitHub Release and its APK download have been published.
$sourceSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch ('^[a-f0-9]{40}' + [char]36)) {
    throw 'Could not identify the release source commit.'
}
$feed = [ordered]@{
    schema_version = 1
    channel = 'stable'
    package = 'io.github.arcanearmorcollector.android'
    latest = [ordered]@{
        package = 'io.github.arcanearmorcollector.android'
        version_name = '0.2.1'
        version_code = 17
        release_tag = 'v0.2.1'
        source_commit = $sourceSha
        certificate_sha256 = $certSha256
        min_sdk = 26
        apk_asset = 'Arcane-v0.2.1-Android.apk'
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
) {
        throw 'Android application tag not found.'
    }
    $withCustomIcon = $currentManifest.Replace('    <application',
        '    <application' + [Environment]::NewLine + '        android:icon="' + $iconRef + '"')
    $manifestReplaced = $true
    [System.IO.File]::WriteAllText($manifest, $withCustomIcon, (New-Object System.Text.UTF8Encoding($false)))

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
Set-Content -Path (Join-Path $releaseDir 'Arcane-v0.2.1-Android.sha256.txt') -Value "$hash  Arcane-v0.2.1-Android.apk"

# Stage a signed-APK-specific update feed for publication AFTER the actual
# matching GitHub Release and its APK download have been published.
$sourceSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch ('^[a-f0-9]{40}' + [char]36)) {
    throw 'Could not identify the release source commit.'
}
$feed = [ordered]@{
    schema_version = 1
    channel = 'stable'
    package = 'io.github.arcanearmorcollector.android'
    latest = [ordered]@{
        package = 'io.github.arcanearmorcollector.android'
        version_name = '0.2.1'
        version_code = 17
        release_tag = 'v0.2.1'
        source_commit = $sourceSha
        certificate_sha256 = $certSha256
        min_sdk = 26
        apk_asset = 'Arcane-v0.2.1-Android.apk'
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
