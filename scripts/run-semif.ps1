[CmdletBinding()]
param([switch]$CpuOnly, [ValidateRange(1, 65535)][int]$Port = 8765)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repository
$taskTemp = Join-Path $repository '.tools\tmp'
New-Item -ItemType Directory -Force -Path $taskTemp | Out-Null
$env:TEMP = $taskTemp
$env:TMP = $taskTemp
$env:HF_HOME = Join-Path $repository 'models\hf-cache'
$env:HF_HUB_OFFLINE = '1'
$env:LLAMA_CPP_LIB_PATH = Join-Path $repository '.tools\semif-native-avx2'
if (-not (Test-Path -LiteralPath (Join-Path $env:LLAMA_CPP_LIB_PATH 'llama.dll'))) {
    throw 'Run scripts/setup-semif.ps1 to configure the portable native runtime first.'
}
$pythonExecutable = Join-Path $repository '.tools\semif-venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonExecutable)) { throw 'Run scripts/setup-semif.ps1 first.' }
$gpuLayers = if ($CpuOnly) { 0 } else { -1 }
& $pythonExecutable -u tools/semif/server.py --gguf models/qwen3.5-4b/Qwen_Qwen3.5-4B-Q4_K_M.gguf --tokenizer models/qwen3.5-4b-tokenizer --gpu-layers $gpuLayers --port $Port
exit $LASTEXITCODE
