#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./scripts/run-semif.sh [--cpu-only] [--port PORT]

Start the local SemIf service in this terminal. Wait for Ready, then run ./run.sh
in another terminal. Stop the service with Ctrl+C.

  --cpu-only   Use CPU inference (default: all supported layers on GPU)
  --port PORT  Loopback HTTP port (default: 8765)
  -h, --help   Show this help
EOF
}
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

gpu_layers=-1
port=8765
while (( $# )); do
    case "$1" in
        --cpu-only) gpu_layers=0; shift ;;
        --port)
            [[ $# -ge 2 && -n $2 && $2 != --* ]] || fail 'Missing value for --port'
            port=$2; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown option: $1 (use --help)" ;;
    esac
done
[[ $port =~ ^[0-9]{1,5}$ ]] || fail 'Port must be an integer between 1 and 65535.'
(( 10#$port >= 1 && 10#$port <= 65535 )) || fail 'Port must be between 1 and 65535.'
[[ $(uname -s) == Linux ]] || fail 'This script requires Linux; on Windows use run-semif.ps1.'

repository=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd -- "$repository"
python_executable="$repository/.tools/semif-venv/bin/python"
[[ -x $python_executable ]] || fail 'Run ./scripts/setup-semif.sh first (use --cpu-only for CPU inference).'
gguf="$repository/models/qwen3.5-4b/Qwen_Qwen3.5-4B-Q4_K_M.gguf"
tokenizer="$repository/models/qwen3.5-4b-tokenizer"
[[ -s $gguf && -s $tokenizer/tokenizer_config.json && -s $tokenizer/tokenizer.json ]] \
    || fail 'Model/tokenizer files are missing. Run ./scripts/setup-semif.sh without --skip-model-download.'

export TMPDIR="$repository/.tools/tmp"
export HF_HOME="$repository/models/hf-cache"
export HF_HUB_OFFLINE=1
mkdir -p -- "$TMPDIR"
# llama-cpp-python loads the Linux libraries installed by setup-semif.sh.
unset LLAMA_CPP_LIB_PATH
exec "$python_executable" -u tools/semif/server.py \
    --gguf "$gguf" --tokenizer "$tokenizer" --gpu-layers "$gpu_layers" --port "$port"
