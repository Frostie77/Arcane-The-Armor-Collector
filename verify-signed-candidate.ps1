# Verify an EXISTING signed v0.2.1 candidate without rebuilding, signing or installing.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$apk = Join-Path $PSScriptRoot 'release\Arcane-v0.2.1-Android.apk'
if (-not (Test-Path $apk)) { throw "Signed APK not found: $apk" }
$sdkTools = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Directory |
    Where-Object { (Test-Path (Join-Path $_.FullName 'aapt.exe')) -and
                   (Test-Path (Join-Path $_.FullName 'apksigner.bat')) } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $sdkTools) { throw 'Android SDK aapt/apksigner not found.' }
$aapt = Join-Path $sdkTools.FullName 'aapt.exe'
$apksigner = Join-Path $sdkTools.FullName 'apksigner.bat'
$feedPath = Join-Path $PSScriptRoot 'updates\android-stable.json'
if (-not (Test-Path $feedPath)) { throw 'Published v0.2.0 feed missing.' }
$previous = Get-Content -Raw -LiteralPath $feedPath | ConvertFrom-Json
if ($previous.channel -ne 'stable' -or
    $previous.package -ne 'io.github.arcanearmorcollector.android' -or
    $previous.latest.version_name -ne '0.2.0' -or
    $previous.latest.version_code -ne 16 -or
    $previous.latest.certificate_sha256 -notmatch '^[a-fA-F0-9]{64}$') {
    throw 'Unexpected v0.2.0 stable release metadata.'
}
$expectedSigner = $previous.latest.certificate_sha256.ToLowerInvariant()
$verify = @(& $apksigner verify --verbose --print-certs $apk)
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$signerMatch = [regex]::Match(($verify -join [Environment]::NewLine),
    'Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f]{64})')
if (-not $signerMatch.Success) { throw 'APK certificate fingerprint missing.' }
if ($signerMatch.Groups[1].Value.ToLowerInvariant() -ne $expectedSigner) {
    throw 'APK uses a different certificate than the installed public v0.2.0 release.'
}
Write-Host 'PASS: original v0.2.0 signing certificate preserved.'

$badging = @(& $aapt dump badging $apk)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect package/version from APK.' }
$pkgMatch = [regex]::Match(($badging -join [Environment]::NewLine),
    "package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
if (-not $pkgMatch.Success -or $pkgMatch.Groups[1].Value -ne 'io.github.arcanearmorcollector.android' -or
    $pkgMatch.Groups[2].Value -ne '17' -or $pkgMatch.Groups[3].Value -ne '0.2.1') {
    throw 'Signed APK package or version does not match Arcane v0.2.1 / code 17.'
}
Write-Host 'PASS: package, version 0.2.1 and code 17.'

$backup = Join-Path $HOME 'Downloads\Arcane-Manifest-Backup.xml'
if (-not (Test-Path $backup)) { throw "Original custom icon manifest backup not found: $backup" }
$backupText = [System.IO.File]::ReadAllText($backup)
$custom = [regex]::Match($backupText, 'android:icon\s*=\s*"@(mipmap|drawable)/([A-Za-z0-9_]+)"')
if (-not $custom.Success -or $custom.Groups[2].Value -eq 'ic_arcane_launcher') {
    throw 'Original custom icon reference missing or points to removed placeholder.'
}
$iconType = $custom.Groups[1].Value
$iconName = $custom.Groups[2].Value
$iconRef = '@' + $iconType + '/' + $iconName
$manifest = @(& $aapt dump xmltree $apk AndroidManifest.xml)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect compiled APK manifest.' }
$iconIdMatch = [regex]::Match(($manifest -join [Environment]::NewLine),
    'android:icon\(0x01010002\)=@0x([0-9a-fA-F]{8})')
if (-not $iconIdMatch.Success) { throw 'APK manifest does not declare a launcher icon.' }
$iconId = $iconIdMatch.Groups[1].Value
$resources = @(& $aapt dump resources $apk)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect compiled APK resources.' }
$namePattern = [regex]::Escape($iconType + '/' + $iconName) + '(?=[:\s]|$)'
$iconRows = @($resources | Where-Object {
    $_ -match "^\s*(?:spec\s+)?resource\s+0x$iconId\b" -and
    $_ -match $namePattern
})
if ($iconRows.Count -eq 0) {
    Write-Host "Manifest icon resource ID: @0x$iconId"
    Write-Host 'Potential icon resource entries:'
    $resources | Select-String -SimpleMatch ($iconType + '/' + $iconName) |
        Select-Object -First 6 | ForEach-Object { Write-Host $_.Line }
    throw "Custom artwork not verified: compiled icon ID did not resolve to $iconRef."
}
Write-Host "PASS: signed APK manifest icon resolves to original custom $iconRef."

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName })
    if (@($entries | Where-Object { $_ -match '(?i)\.swf$' }).Count -ne 0) {
        throw 'SAFETY FAILURE: Original game SWF is packaged in release APK.'
    }
    if (-not ($entries -contains 'assets/www/ruffle/ruffle.js') -or
        -not ($entries -contains 'assets/www/index.html')) {
        throw 'Ruffle or game wrapper assets missing from release APK.'
    }
    $iconLocation = [regex]::Match(($badging -join [Environment]::NewLine),
        "(?m)^application:.*\bicon='([^']+)'").Groups[1].Value
    if (-not $iconLocation) { throw 'Could not locate compiled launcher image path.' }
    $entry = $archive.GetEntry($iconLocation)
    if ($null -eq $entry -or $entry.Length -eq 0) {
        throw "Compiled launcher image file missing or empty in APK: $iconLocation"
    }
    Write-Host "PASS: non-empty compiled launcher image exists at $iconLocation."
} finally { $archive.Dispose() }
$hash = (Get-FileHash -Algorithm SHA256 $apk).Hash.ToLowerInvariant()
Write-Host 'PASS: no original game SWF included; Ruffle and wrapper present.'
Write-Host "PASS: existing signed candidate verified. SHA256: $hash"
Write-Host 'No build, signing, installation, publication, or game-data changes performed.'
