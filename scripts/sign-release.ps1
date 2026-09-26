param(
    [Parameter(Mandatory=$true)][string]$ApkSigner,
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$SigningProperties,
    [Parameter(Mandatory=$true)][string]$LegacyDebugKeystore,
    [Parameter(Mandatory=$true)][string]$Lineage,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [Parameter(Mandatory=$true)][string]$Version
)
$ErrorActionPreference = 'Stop'
$signing = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path $SigningProperties))) {
    # The existing private file uses literal Windows paths, not regex escapes.
    if ($line -match '^\s*([^#!=\s]+)\s*=(.*)$') { $signing[$matches[1]] = $matches[2].Trim() }
}
foreach ($name in @('storeFile', 'storeType', 'keyAlias', 'storePassword', 'keyPassword')) {
    if (!$signing[$name]) { throw "Missing signing property: $name" }
}
if (!(Test-Path -LiteralPath $signing.storeFile)) { throw 'Release keystore not found' }
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$release = Join-Path $OutputDirectory "FangJi-CubeTrace-v$Version-release.apk"
$legacy = Join-Path $OutputDirectory "FangJi-CubeTrace-v$Version-legacy-upgrade.apk"
if ((Test-Path -LiteralPath $release) -or (Test-Path -LiteralPath $legacy)) {
    throw 'Refusing to overwrite existing signed artifacts; use a new output directory.'
}
$env:CUBETRACE_RELEASE_STORE_PASSWORD = $signing.storePassword
$env:CUBETRACE_RELEASE_KEY_PASSWORD = $signing.keyPassword
try {
    $releaseSigner = @('--ks', $signing.storeFile, '--ks-type', $signing.storeType,
        '--ks-key-alias', $signing.keyAlias, '--ks-pass', 'env:CUBETRACE_RELEASE_STORE_PASSWORD',
        '--key-pass', 'env:CUBETRACE_RELEASE_KEY_PASSWORD')
    # v0.3.1 was actually published with the stable certificate on every API.
    # Preserve that installed base, including Android 8 which cannot rotate keys.
    & $ApkSigner sign --min-sdk-version 26 --v4-signing-enabled false --out $release @releaseSigner $UnsignedApk
    if ($LASTEXITCODE -ne 0) { throw 'Stable release signing failed' }
    # Separate migration artifact for older debug-signed installations. Android
    # 8 keeps its old key; API 28+ receives the stable key with proof of rotation.
    & $ApkSigner sign --min-sdk-version 26 --rotation-min-sdk-version 28 --v4-signing-enabled false `
        --lineage $Lineage --out $legacy --ks $LegacyDebugKeystore --ks-key-alias androiddebugkey `
        --ks-pass pass:android --key-pass pass:android --next-signer @releaseSigner $UnsignedApk
    if ($LASTEXITCODE -ne 0) { throw 'Legacy migration signing failed' }
    foreach ($artifact in @($release, $legacy)) {
        & $ApkSigner verify --min-sdk-version 26 --max-sdk-version 27 --print-certs $artifact
        if ($LASTEXITCODE -ne 0) { throw 'Android 8 signature verification failed' }
        & $ApkSigner verify --min-sdk-version 28 --print-certs $artifact
        if ($LASTEXITCODE -ne 0) { throw 'Android 9+ signature verification failed' }
    }
} finally {
    Remove-Item Env:CUBETRACE_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:CUBETRACE_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $signing.Clear()
}
