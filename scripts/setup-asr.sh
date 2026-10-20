#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./scripts/setup-asr.sh

Download and verify the pinned English streaming sherpa-onnx model.
Requires Bash, curl, GNU tar, bzip2, and GNU coreutils (stat and sha256sum).
The archive is cached in .tools/downloads/; the model is extracted into models/.
Rerun this script to resume an interrupted download or reuse verified files.

  -h, --help  Show this help
EOF
}
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

if (( $# )); then
    if (( $# == 1 )) && [[ $1 == --help || $1 == -h ]]; then
        usage
        exit 0
    fi
    fail "Unknown option: $1 (use --help)"
fi

for prerequisite in curl tar bzip2 stat sha256sum; do
    command -v "$prerequisite" >/dev/null \
        || fail "Install $prerequisite before running setup (see docs/asr-setup.md)."
done

repository=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
model_name=sherpa-onnx-streaming-zipformer-en-2023-06-26
archive_name="$model_name.tar.bz2"
archive_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$archive_name"
expected_bytes=310414022
# Measured from the official asset during Windows setup verification.
# GitHub asset 191971614 did not publish a digest; this is not a publisher attestation.
expected_sha256=639e25b578e9e997131402199419c13a941f8e4e198e2da1ce57dbf5cf401282
downloads_directory="$repository/.tools/downloads"
models_directory="$repository/models"
archive_path="$downloads_directory/$archive_name"
partial_path="$archive_path.part"
model_directory="$models_directory/$model_name"
completion_marker="$model_directory/.setup-asr.sha256"
required_files=(
    encoder-epoch-99-avg-1-chunk-16-left-128.onnx
    decoder-epoch-99-avg-1-chunk-16-left-128.onnx
    joiner-epoch-99-avg-1-chunk-16-left-128.onnx
    tokens.txt
    test_wavs/0.wav
)

verify_archive() {
    local candidate=$1 actual_bytes actual_sha256
    actual_bytes=$(stat --format=%s -- "$candidate") \
        || fail "Unable to inspect archive: $candidate"
    [[ $actual_bytes == "$expected_bytes" ]] \
        || fail "Unexpected archive size: $candidate (expected $expected_bytes bytes). File retained for inspection."
    actual_sha256=$(sha256sum -- "$candidate") \
        || fail "Unable to calculate archive checksum: $candidate"
    actual_sha256=${actual_sha256%% *}
    [[ $actual_sha256 == "$expected_sha256" ]] \
        || fail "Archive checksum differs from the pinned official download: $candidate. File retained for inspection."
}

mkdir -p -- "$downloads_directory" "$models_directory"
if [[ ! -f $archive_path ]]; then
    # A complete .part can remain if a previous run stopped before promotion.
    if [[ ! -f $partial_path ]] || [[ $(stat --format=%s -- "$partial_path") != "$expected_bytes" ]]; then
        printf 'Downloading the official ASR model archive (about 296 MiB)...\n'
        curl --fail --location --retry 3 --continue-at - --output "$partial_path" "$archive_url" \
            || fail 'ASR download failed. Rerun this script to resume the partial download.'
    fi
    verify_archive "$partial_path"
    mv -n -- "$partial_path" "$archive_path"
fi
verify_archive "$archive_path"
printf '%s  %s\n' "$expected_sha256" "$archive_name" > "$archive_path.sha256"
printf 'Verified archive SHA-256: %s\n' "$expected_sha256"

# Read the complete listing successfully before considering extraction.
archive_entries=$(tar --list --bzip2 --file "$archive_path") \
    || fail 'Unable to read the model archive. Check GNU tar and bzip2 support.'
[[ -n $archive_entries ]] || fail 'The model archive is empty.'
while IFS= read -r entry; do
    normalized=$entry
    while [[ $normalized == ./* ]]; do normalized=${normalized#./}; done
    [[ $normalized == "$model_name" || $normalized == "$model_name/"* ]] \
        || fail "Archive entry is outside the expected model directory: $entry"
    [[ $normalized != *\\* && $normalized != *:* && /$normalized/ != */../* ]] \
        || fail "Unsafe model archive entry: $entry"
done <<< "$archive_entries"

needs_extraction=true
if [[ -f $completion_marker ]]; then
    if [[ $(< "$completion_marker") == "$expected_sha256" ]]; then
        needs_extraction=false
    fi
fi
for required_file in "${required_files[@]}"; do
    if [[ ! -f "$model_directory/$required_file" || ! -s "$model_directory/$required_file" ]]; then
        needs_extraction=true
        break
    fi
done
if $needs_extraction; then
    # An interrupted extraction must not leave an earlier success marker valid.
    if [[ -e $completion_marker ]]; then : > "$completion_marker"; fi
    printf 'Extracting into %s ...\n' "$models_directory"
    tar --extract --bzip2 --file "$archive_path" --directory "$models_directory" \
        --no-same-owner --no-same-permissions \
        || fail 'Model extraction failed. Rerun this script to complete extraction.'
fi
for required_file in "${required_files[@]}"; do
    [[ -f "$model_directory/$required_file" && -s "$model_directory/$required_file" ]] \
        || fail "Required ASR file is missing or empty: $model_directory/$required_file"
done
if $needs_extraction; then
    printf '%s\n' "$expected_sha256" > "$completion_marker"
fi

printf '\nASR model ready: %s\n' "$model_directory"
printf 'From the repository root, start the Java game with ./run.sh.\n'
printf 'For the optional native recognition check, set this in your terminal:\n'
printf 'export SHERPA_MODEL_DIR=%q\n' "$model_directory"
printf 'Then run: ./gradlew asrSmokeTest\n'
