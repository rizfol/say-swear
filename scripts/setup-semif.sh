#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./scripts/setup-semif.sh [--cpu-only] [--skip-model-download] [--python EXECUTABLE]

Install the pinned SemIf runtime and Qwen3.5 4B model locally.
Requires Python 3.12, Git, CMake, a C/C++ compiler, and Make or Ninja.
The default CUDA build also requires the NVIDIA CUDA toolkit (nvcc).

  --cpu-only             Build without CUDA (also use when starting the service)
  --skip-model-download  Install dependencies without downloading model files
  --python EXECUTABLE    Python used to create the venv (default: python3.12 or python3)
  -h, --help             Show this help
EOF
}
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

cuda=ON
skip_models=false
python_command=
while (( $# )); do
    case "$1" in
        --cpu-only) cuda=OFF; shift ;;
        --skip-model-download) skip_models=true; shift ;;
        --python)
            [[ $# -ge 2 && -n $2 && $2 != --* ]] || fail 'Missing value for --python'
            python_command=$2; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown option: $1 (use --help)" ;;
    esac
done
[[ $(uname -s) == Linux ]] || fail 'This script requires Linux; on Windows use setup-semif.ps1.'

# Resolve an explicit relative executable before changing to the repository root.
if [[ -n $python_command ]]; then
    python_command=$(command -v "$python_command") || fail 'The requested Python executable was not found.'
    python_command=$(readlink -f "$python_command")
fi
repository=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd -- "$repository"
python_executable="$repository/.tools/semif-venv/bin/python"
if [[ -e .tools/semif-venv && ! -x $python_executable ]]; then
    fail 'The existing .tools/semif-venv is not a usable Linux venv. Move it aside, then rerun setup.'
fi
if [[ ! -x $python_executable ]]; then
    if [[ -z $python_command ]]; then
        python_command=$(command -v python3.12 || command -v python3) || fail 'Install Python 3.12 with venv support, or pass --python EXECUTABLE.'
    fi
else
    python_command=$python_executable
fi
"$python_command" -c 'import sys; sys.exit(0 if sys.version_info[:2] == (3, 12) else 1)' \
    || fail 'The pinned environment requires Python 3.12. Pass --python /path/to/python3.12; an existing venv must also use 3.12.'
for prerequisite in git cmake cc c++; do
    command -v "$prerequisite" >/dev/null || fail "Install $prerequisite before running setup (see docs/semif-setup.md)."
done
if ! command -v make >/dev/null && ! command -v ninja >/dev/null; then
    fail 'Install Make or Ninja before running setup.'
fi
if [[ $cuda == ON ]] && ! command -v nvcc >/dev/null; then
    fail 'CUDA setup needs nvcc on PATH. Install the CUDA toolkit, or use --cpu-only.'
fi

export TMPDIR="$repository/.tools/tmp"
export HF_HOME="$repository/models/hf-cache"
export HF_HUB_OFFLINE=0
mkdir -p -- "$TMPDIR"
if [[ ! -x $python_executable ]]; then
    "$python_command" -m venv "$repository/.tools/semif-venv" \
        || fail 'Venv creation failed. Install Python 3.12 venv support and retry.'
fi
pip_options=(--disable-pip-version-check --cache-dir "$repository/.tools/pip-cache")
"$python_executable" -m pip install "${pip_options[@]}" -r scripts/requirements-semif.txt

# Rebuild when switching CPU/CUDA; do not reuse a wheel compiled for another backend.
export CMAKE_ARGS="${CMAKE_ARGS:+$CMAKE_ARGS }-DGGML_CUDA=$cuda"
export CMAKE_BUILD_PARALLEL_LEVEL="${CMAKE_BUILD_PARALLEL_LEVEL:-2}"
"$python_executable" -m pip install --disable-pip-version-check --no-cache-dir \
    --force-reinstall --no-deps --no-binary=llama-cpp-python 'llama-cpp-python==0.3.35'
"$python_executable" -m pip install "${pip_options[@]}" --no-deps \
    'git+https://github.com/TheoLeeCJ/SemIf-OpenJev.git@23cf1f39fc9534fe81437200959b6dfc7106e45a'

# Use the installed Linux libraries, without the Windows DLL overlay.
unset LLAMA_CPP_LIB_PATH
"$python_executable" - "$cuda" <<'PY'
import sys
import llama_cpp
from semif_phase1 import llamacpp_backend

if llama_cpp.__version__ != "0.3.35":
    raise SystemExit("Expected llama-cpp-python 0.3.35.")
if sys.argv[1] == "ON" and not llama_cpp.llama_supports_gpu_offload():
    raise SystemExit("The installed runtime has no GPU backend. Check the CUDA build.")
print("SemIf and native runtime imports passed.")
PY
if ! $skip_models; then
    "$python_executable" scripts/download-semif-models.py
fi
printf 'SemIf dependencies installed. Start with ./scripts/run-semif.sh'
if [[ $cuda == OFF ]]; then printf ' --cpu-only'; fi
printf '\n'
if $skip_models; then
    printf 'Model download skipped. Rerun setup without --skip-model-download before first startup.\n'
fi
