#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./run.sh [--mode gui|cli|test|build] [options]

  --mode MODE                 Default: gui
  --demo                      Use the offline demo interpreter
  --quiet-cli                 Plain CLI output without periodic maps
  --java-home DIRECTORY       Use this Java 21 JDK
  --endpoint URL              Override SEMIF_ENDPOINT
  --decision-timeout-ms MS    Override DECISION_TIMEOUT_MS (100–30000)
  --asr-model-dir DIRECTORY   Override SHERPA_MODEL_DIR
  -h, --help                  Show this help

Existing environment settings are preserved unless overridden by an option.
Relative model paths are resolved from the repository root.
EOF
}

fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }
require_value() {
    [[ $# -ge 2 && -n $2 && $2 != --* ]] || fail "Missing value for $1"
}

mode=gui
demo=false
quiet_cli=false
requested_jdk=
while (( $# )); do
    case "$1" in
        --mode) require_value "$@"; mode=$2; shift 2 ;;
        --demo) demo=true; shift ;;
        --quiet-cli) quiet_cli=true; shift ;;
        --java-home) require_value "$@"; requested_jdk=$2; shift 2 ;;
        --endpoint) require_value "$@"; export SEMIF_ENDPOINT="$2"; shift 2 ;;
        --decision-timeout-ms) require_value "$@"; export DECISION_TIMEOUT_MS="$2"; shift 2 ;;
        --asr-model-dir) require_value "$@"; export SHERPA_MODEL_DIR="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown option: $1 (use --help)" ;;
    esac
done
case "$mode" in gui|cli|test|build) ;; *) fail "Unknown mode: $mode" ;; esac
export DECISION_TIMEOUT_MS="${DECISION_TIMEOUT_MS:-2000}"
[[ $DECISION_TIMEOUT_MS =~ ^[0-9]{3,5}$ ]] || fail 'Decision timeout must be an integer between 100 and 30000 ms.'
(( 10#$DECISION_TIMEOUT_MS >= 100 && 10#$DECISION_TIMEOUT_MS <= 30000 )) || fail 'Decision timeout must be between 100 and 30000 ms.'

# Select a JDK for this process; do not change the user's shell configuration.
is_java21() {
    [[ -x $1/bin/java && -x $1/bin/javac ]] || return 1
    local version
    version=$("$1/bin/java" -version 2>&1) || return 1
    [[ $version =~ version\ \"21[.\"] ]]
}
selected_jdk=
if [[ -n $requested_jdk ]]; then
    is_java21 "$requested_jdk" || fail "Not a Java 21 JDK: $requested_jdk"
    selected_jdk=$requested_jdk
else
    candidates=("${JAVA_HOME:-}")
    if java_path=$(command -v java); then
        java_path=$(readlink -f "$java_path")
        candidates+=("$(dirname "$(dirname "$java_path")")")
    fi
    candidates+=(/usr/lib/jvm/*)
    for candidate in "${candidates[@]}"; do
        if [[ -n $candidate ]] && is_java21 "$candidate"; then
            selected_jdk=$candidate
            break
        fi
    done
fi
[[ -n $selected_jdk ]] || fail 'Java 21 JDK is required. Install it, set JAVA_HOME, or pass --java-home DIRECTORY.'
export JAVA_HOME
JAVA_HOME=$(cd -- "$selected_jdk" && pwd -P)
export PATH="$JAVA_HOME/bin:$PATH"

repository=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
cd -- "$repository"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$repository/.tools/gradle}"
export TMPDIR="$repository/.tools/tmp"
javafx_cache="$repository/.tools/javafx-cache"
mkdir -p -- "$TMPDIR" "$javafx_cache"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }\"-Djava.io.tmpdir=$TMPDIR\" \"-Djavafx.cachedir=$javafx_cache\""

if [[ $mode == test || $mode == build ]]; then
    exec ./gradlew "$mode" --console=plain
elif [[ $mode == cli ]]; then
    # Launch directly so JLine inherits the real terminal rather than Gradle pipes.
    ./gradlew installDist --console=plain --quiet
    arguments=(--cli)
    if $demo; then arguments+=(--demo); fi
    if $quiet_cli; then arguments+=(--quiet-cli); fi
    exec "$repository/build/install/say-swear/bin/say-swear" "${arguments[@]}"
else
    arguments=--gui
    if $demo; then arguments+=' --demo'; fi
    exec ./gradlew run "--args=$arguments" --console=plain --quiet
fi
