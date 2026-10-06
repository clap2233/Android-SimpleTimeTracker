param(
    [string]$ToolRoot = (Join-Path $PSScriptRoot '..\..\.life-clock-tools'),
    [string[]]$Tasks = @(':app:assembleBaseDebug', ':domain:test'),
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
function Get-BuildPath([string]$Path, [string]$AliasName) {
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if ($resolved -notmatch '[^\x00-\x7F]') { return $resolved }
    $aliasPath = Join-Path ([System.IO.Path]::GetTempPath()) $AliasName
    if (Test-Path -LiteralPath $aliasPath) {
        $existing = Get-Item -LiteralPath $aliasPath
        if ($existing.LinkType -ne 'Junction' -or $existing.Target -ne $resolved) {
            throw "Build alias already belongs to another directory: $aliasPath"
        }
    } else {
        New-Item -ItemType Junction -Path $aliasPath -Target $resolved | Out-Null
    }
    return $aliasPath
}

$ToolRoot = Get-BuildPath $ToolRoot 'life-clock-tools'
$projectRoot = Get-BuildPath (Join-Path $PSScriptRoot '..') 'life-clock-project'
$jdk = Get-ChildItem -LiteralPath $ToolRoot -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
    Select-Object -First 1
if (-not $jdk) { throw "No JDK found in $ToolRoot" }
if (-not (Test-Path -LiteralPath (Join-Path $ToolRoot 'sdk\platforms\android-35\android.jar'))) {
    throw 'Android SDK platform 35 is required.'
}

$names = @('JAVA_HOME', 'GRADLE_USER_HOME', 'ANDROID_HOME', 'ANDROID_USER_HOME', 'JAVA_TOOL_OPTIONS')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$exitCode = 1
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $jdk.FullName
    $env:GRADLE_USER_HOME = Join-Path $ToolRoot 'gradle-home'
    $env:ANDROID_HOME = Join-Path $ToolRoot 'sdk'
    $env:ANDROID_USER_HOME = Join-Path $ToolRoot 'android-user'
    $javaOptions = @($previous['JAVA_TOOL_OPTIONS'], '-Djavax.net.ssl.trustStoreType=Windows-ROOT', '-Djavax.net.ssl.trustStore=NONE')
    $target = [Uri]'https://services.gradle.org'
    $proxy = [System.Net.WebRequest]::GetSystemWebProxy().GetProxy($target)
    if ($proxy -and $proxy.Authority -ne $target.Authority) {
        $javaOptions += @("-Dhttps.proxyHost=$($proxy.Host)", "-Dhttps.proxyPort=$($proxy.Port)", "-Dhttp.proxyHost=$($proxy.Host)", "-Dhttp.proxyPort=$($proxy.Port)")
    }
    $env:JAVA_TOOL_OPTIONS = ($javaOptions | Where-Object { $_ }) -join ' '
    $arguments = @($Tasks) + @('-Pandroid.overridePathCheck=true', '--console=plain', '--max-workers=4')
    if ($Offline) { $arguments += '--offline' }
    & .\gradlew.bat @arguments
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
exit $exitCode
