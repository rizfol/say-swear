[CmdletBinding()]
param([switch]$CpuOnly, [switch]$SkipModelDownload)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repository
$taskTemp = Join-Path $repository '.tools\tmp'
New-Item -ItemType Directory -Force -Path $taskTemp | Out-Null
$env:TEMP = $taskTemp
$env:TMP = $taskTemp
$env:HF_HOME = Join-Path $repository 'models\hf-cache'
$env:HF_HUB_OFFLINE = '0'
$environmentDirectory = Join-Path $repository '.tools\semif-venv'
$pythonExecutable = Join-Path $environmentDirectory 'Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonExecutable)) {
    & python -m venv $environmentDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.12 is recommended; virtual environment creation failed.' }
}
$pipOptions = @('--disable-pip-version-check', '--cache-dir', (Join-Path $repository '.tools\pip-cache'))
& $pythonExecutable -m pip install @pipOptions -r (Join-Path $PSScriptRoot 'requirements-semif.txt')
if ($LASTEXITCODE -ne 0) { throw 'SemIf tokenizer dependency installation failed.' }
if ($CpuOnly) {
    & $pythonExecutable -m pip install @pipOptions --force-reinstall --no-deps 'https://github.com/abetlen/llama-cpp-python/releases/download/v0.3.35/llama_cpp_python-0.3.35-py3-none-win_amd64.whl#sha256=31590ea000d5aff6f05f1e428048e72318a83709288159a5bd4dabec530080bb'
} else {
    & $pythonExecutable -m pip install @pipOptions --force-reinstall --no-deps 'https://github.com/abetlen/llama-cpp-python/releases/download/v0.3.35-cu124/llama_cpp_python-0.3.35-py3-none-win_amd64.whl#sha256=84f7218c1e9cf21014b9770c65064c5a3674dd2f4fbc312c5d6bb40cec0fb269'
}
if ($LASTEXITCODE -ne 0) { throw 'Native llama.cpp installation failed.' }
& $pythonExecutable (Join-Path $PSScriptRoot 'configure-semif-native.py')
if ($LASTEXITCODE -ne 0) { throw 'Portable native runtime configuration failed.' }
& $pythonExecutable -m pip install @pipOptions --no-deps 'git+https://github.com/TheoLeeCJ/SemIf-OpenJev.git@23cf1f39fc9534fe81437200959b6dfc7106e45a'
if ($LASTEXITCODE -ne 0) { throw 'Pinned SemIf installation failed. Git is required.' }
if (-not $SkipModelDownload) {
    & $pythonExecutable (Join-Path $PSScriptRoot 'download-semif-models.py')
    if ($LASTEXITCODE -ne 0) { throw 'Pinned Qwen3.5 4B download failed.' }
}
Write-Host 'SemIf dependencies are installed locally. See docs/semif-setup.md for startup and verification.'
