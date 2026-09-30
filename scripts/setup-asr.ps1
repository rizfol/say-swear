[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$RepositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$ModelName = 'sherpa-onnx-streaming-zipformer-en-2023-06-26'
$ArchiveName = "$ModelName.tar.bz2"
$ArchiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ArchiveName"
$ExpectedBytes = 310414022
# Pinned from the official asset download verified during setup development.
# GitHub asset 191971614 itself does not publish a digest; this is not a publisher attestation.
$ExpectedSha256 = '639e25b578e9e997131402199419c13a941f8e4e198e2da1ce57dbf5cf401282'
$DownloadsDirectory = Join-Path $RepositoryRoot '.tools/downloads'
$ModelsDirectory = Join-Path $RepositoryRoot 'models'
$ArchivePath = Join-Path $DownloadsDirectory $ArchiveName
$PartialPath = "$ArchivePath.part"
$ModelDirectory = Join-Path $ModelsDirectory $ModelName
$RequiredFiles = @(
    'encoder-epoch-99-avg-1-chunk-16-left-128.onnx',
    'decoder-epoch-99-avg-1-chunk-16-left-128.onnx',
    'joiner-epoch-99-avg-1-chunk-16-left-128.onnx',
    'tokens.txt',
    'test_wavs/0.wav'
)

$TarCommand = Get-Command tar.exe -ErrorAction Stop
$CurlCommand = Get-Command curl.exe -ErrorAction Stop
New-Item -ItemType Directory -Path $DownloadsDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $ModelsDirectory -Force | Out-Null

if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) {
    Write-Host "Downloading the official ASR model archive (about 296 MiB)..."
    & $CurlCommand.Source --fail --location --retry 3 --continue-at - --output $PartialPath $ArchiveUrl
    if ($LASTEXITCODE -ne 0) {
        throw 'ASR download failed. Run this script again to resume the partial download.'
    }
    if ((Get-Item -LiteralPath $PartialPath).Length -ne $ExpectedBytes) {
        throw "Unexpected archive size. Expected $ExpectedBytes bytes; partial download retained for inspection."
    }
    Move-Item -LiteralPath $PartialPath -Destination $ArchivePath
}

if ((Get-Item -LiteralPath $ArchivePath).Length -ne $ExpectedBytes) {
    throw "The cached ASR archive has an unexpected size. Inspect $ArchivePath before retrying."
}
$ArchiveSha256 = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ArchiveSha256 -ne $ExpectedSha256) {
    throw "ASR archive checksum differs from the pinned official download. Inspect $ArchivePath before retrying."
}
Set-Content -LiteralPath "$ArchivePath.sha256" -Value "$ArchiveSha256  $ArchiveName" -Encoding ASCII
Write-Host "Verified archive SHA-256: $ArchiveSha256"

# Validate archive member paths before extracting into the repository's models directory.
$ArchiveEntries = & $TarCommand.Source -tf $ArchivePath
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read the model archive. Check that Windows tar supports bzip2 archives.'
}
foreach ($Entry in $ArchiveEntries) {
    $NormalizedEntry = ($Entry -replace '\\', '/') -replace '^\./', ''
    if (($NormalizedEntry -ne $ModelName -and -not $NormalizedEntry.StartsWith("$ModelName/")) `
            -or ($NormalizedEntry.Split('/') -contains '..') -or $NormalizedEntry.Contains(':')) {
        throw "Archive entry is outside the expected model directory: $Entry"
    }
}

$MissingFiles = @($RequiredFiles | Where-Object {
    $CandidatePath = Join-Path $ModelDirectory $_
    -not (Test-Path -LiteralPath $CandidatePath -PathType Leaf) `
        -or (Get-Item -LiteralPath $CandidatePath).Length -eq 0
})
if ($MissingFiles.Count -gt 0) {
    Write-Host "Extracting into $ModelsDirectory ..."
    & $TarCommand.Source -xf $ArchivePath -C $ModelsDirectory
    if ($LASTEXITCODE -ne 0) {
        throw 'Model extraction failed. Rerun the script to complete extraction.'
    }
}
foreach ($RequiredFile in $RequiredFiles) {
    $RequiredPath = Join-Path $ModelDirectory $RequiredFile
    if (-not (Test-Path -LiteralPath $RequiredPath -PathType Leaf) `
            -or (Get-Item -LiteralPath $RequiredPath).Length -eq 0) {
        throw "Required ASR file is missing or empty: $RequiredPath"
    }
}

$env:SHERPA_MODEL_DIR = $ModelDirectory
Write-Host ''
Write-Host "ASR model ready: $ModelDirectory"
Write-Host 'No microphone has been opened.'
Write-Host 'For a different PowerShell process, set:'
Write-Host ('$env:SHERPA_MODEL_DIR = ''' + $ModelDirectory.Replace("'", "''") + "'")
Write-Host 'Then run: .\gradlew.bat asrSmokeTest'
