param(
    [Parameter(Mandatory)][string]$UnsignedApk,
    [Parameter(Mandatory)][string]$OutputApk,
    [Parameter(Mandatory)][string]$KeyStore,
    [Parameter(Mandatory)][string]$PasswordFile,
    [string]$KeyAlias = 'life-clock',
    [string]$ToolRoot = (Join-Path $PSScriptRoot '..\..\.life-clock-tools')
)

$ErrorActionPreference = 'Stop'
$unsigned = (Resolve-Path -LiteralPath $UnsignedApk).Path
$store = (Resolve-Path -LiteralPath $KeyStore).Path
$password = (Resolve-Path -LiteralPath $PasswordFile).Path
$output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputApk)
if ($output -eq $unsigned) { throw 'Output must differ from the unsigned APK.' }
if (Test-Path -LiteralPath $output) { throw "Output already exists: $output" }
$jdk = Get-ChildItem -LiteralPath $ToolRoot -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
    Select-Object -First 1
if (-not $jdk) { throw 'JDK 21 is required.' }
$signer = Join-Path $ToolRoot 'sdk\build-tools\34.0.0\apksigner.bat'
$previousJava = $env:JAVA_HOME
$staged = "$output.pending"
if (Test-Path -LiteralPath $staged) { throw "Staging file already exists: $staged" }
try {
    $env:JAVA_HOME = $jdk.FullName
    & $signer sign --ks $store --ks-key-alias $KeyAlias --ks-pass "file:$password" --v4-signing-enabled false --out $staged $unsigned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $signer verify --verbose --print-certs $staged
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    Move-Item -LiteralPath $staged -Destination $output
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    $env:JAVA_HOME = $previousJava
    if (Test-Path -LiteralPath $staged) { Remove-Item -LiteralPath $staged }
}
