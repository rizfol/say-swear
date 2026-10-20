[CmdletBinding()]
param(
    [ValidateSet('gui', 'cli', 'test', 'build')][string]$Mode = 'gui',
    [switch]$Demo,
    [switch]$QuietCli,
    [string]$JavaHome,
    [string]$Endpoint,
    [ValidateRange(100, 30000)][int]$DecisionTimeoutMs = 2000,
    [string]$AsrModelDirectory
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Set-Location -LiteralPath $PSScriptRoot

# Select Java 21 for this process only; leave system PATH/JAVA_HOME untouched.
$jdkCandidates = @()
if ($JavaHome) { $jdkCandidates += $JavaHome }
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
foreach ($jdkRoot in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java')) {
    if (Test-Path -LiteralPath $jdkRoot) {
        $jdkCandidates += Get-ChildItem -LiteralPath $jdkRoot -Directory |
            Where-Object { $_.Name -match 'jdk-21' } | ForEach-Object { $_.FullName }
    }
}
$selectedJdk = $null
foreach ($jdkCandidate in $jdkCandidates) {
    $javaExecutable = Join-Path $jdkCandidate 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExecutable)) { continue }
    # Java prints its version on stderr; Windows PowerShell wraps that as an error record.
    $savedErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $versionOutput = & $javaExecutable -version 2>&1 | Out-String }
    finally { $ErrorActionPreference = $savedErrorPreference }
    if ($versionOutput -match 'version "21\.') { $selectedJdk = $jdkCandidate; break }
}
if (-not $selectedJdk) { throw 'Java 21 JDK is required. Set JAVA_HOME or pass -JavaHome with its directory.' }
$env:JAVA_HOME = $selectedJdk
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.tools\gradle'
$runtimeTemp = Join-Path $PSScriptRoot '.tools\tmp'
$javaFxCache = Join-Path $PSScriptRoot '.tools\javafx-cache'
New-Item -ItemType Directory -Force -Path $runtimeTemp, $javaFxCache | Out-Null
$env:TEMP = $runtimeTemp
$env:TMP = $runtimeTemp
$env:JAVA_TOOL_OPTIONS = ('{0} "-Djava.io.tmpdir={1}" "-Djavafx.cachedir={2}"' -f $env:JAVA_TOOL_OPTIONS, $runtimeTemp, $javaFxCache).Trim()
if ($Endpoint) { $env:SEMIF_ENDPOINT = $Endpoint }
if ($PSBoundParameters.ContainsKey('DecisionTimeoutMs') -or -not $env:DECISION_TIMEOUT_MS) {
    $env:DECISION_TIMEOUT_MS = [string]$DecisionTimeoutMs
}
if ($AsrModelDirectory) { $env:SHERPA_MODEL_DIR = $AsrModelDirectory }

if ($Mode -in @('test', 'build')) {
    & .\gradlew.bat $Mode --console=plain
} elseif ($Mode -eq 'cli') {
    # A terminal UI must inherit the real console instead of Gradle's forwarded pipes.
    & .\gradlew.bat installDist --console=plain --quiet
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $cliArguments = @('--cli')
    if ($Demo) { $cliArguments += '--demo' }
    if ($QuietCli) { $cliArguments += '--quiet-cli' }
    & (Join-Path $PSScriptRoot 'build\install\say-swear\bin\say-swear.bat') @cliArguments
} else {
    $appArguments = "--$Mode"
    if ($Demo) { $appArguments += ' --demo' }
    & .\gradlew.bat run "--args=$appArguments" --console=plain --quiet
}
exit $LASTEXITCODE
