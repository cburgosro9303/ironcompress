#!/usr/bin/env bash
# Benchmark IronCompress vs Java libraries with configurable data size.
#
# Usage:
#   ./scripts/bench.sh                         # Default data sets (1KB, 100KB, 1MB)
#   ./scripts/bench.sh --size 10MB             # Generate 10MB of compressible text
#   ./scripts/bench.sh --size 1GB              # Generate 1GB of compressible text
#   ./scripts/bench.sh --file /path/to/data    # Benchmark with an external file
#   ./scripts/bench.sh --generate 500MB        # Only generate file, don't benchmark
#
# Supported size formats: 1KB, 100KB, 10MB, 500MB, 1GB
# Native library path defaults to ../rust/target/release

set -euo pipefail
cd "$(dirname "$0")/.."

NATIVE_LIB_PATH="../rust/target/release"
SIZE=""
FILE=""
GENERATE_ONLY=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --size)
            SIZE="$2"
            shift 2
            ;;
        --file)
            FILE="$2"
            shift 2
            ;;
        --generate)
            GENERATE_ONLY="$2"
            shift 2
            ;;
        --native-lib-path)
            NATIVE_LIB_PATH="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--size SIZE] [--file PATH] [--generate SIZE] [--native-lib-path PATH]"
            exit 1
            ;;
    esac
done

generate_file() {
    local size_label="$1"
    local dir="testdata"
    mkdir -p "$dir"
    local file="$dir/${size_label}.bin"

    if [ -f "$file" ]; then
        echo "File already exists: $file ($(wc -c < "$file" | tr -d ' ') bytes)"
    else
        echo "Generating $size_label of compressible text..."
        local phrase="The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. "
        # Parse size to bytes
        local bytes
        bytes=$(echo "$size_label" | awk '{
            s = toupper($0);
            if (s ~ /GB$/) { gsub(/GB$/, "", s); printf "%.0f", s * 1024 * 1024 * 1024 }
            else if (s ~ /MB$/) { gsub(/MB$/, "", s); printf "%.0f", s * 1024 * 1024 }
            else if (s ~ /KB$/) { gsub(/KB$/, "", s); printf "%.0f", s * 1024 }
            else { printf "%.0f", s }
        }')
        # yes exits with SIGPIPE when head closes, so ignore that error
        yes "$phrase" 2>/dev/null | head -c "$bytes" > "$file" || true
        echo "Generated: $file ($(wc -c < "$file" | tr -d ' ') bytes)"
    fi
    echo "$file"
}

# Generate-only mode
if [ -n "$GENERATE_ONLY" ]; then
    generate_file "$GENERATE_ONLY"
    exit 0
fi

# Build the compare command
GRADLE_ARGS="-Pnative.lib.path=$NATIVE_LIB_PATH"

if [ -n "$SIZE" ]; then
    GRADLE_ARGS="$GRADLE_ARGS -Pbenchmark.size=$SIZE"
fi

if [ -n "$FILE" ]; then
    GRADLE_ARGS="$GRADLE_ARGS -Pbenchmark.file=$FILE"
fi

echo "Running benchmark..."
echo "  Native lib: $NATIVE_LIB_PATH"
[ -n "$SIZE" ] && echo "  Size: $SIZE"
[ -n "$FILE" ] && echo "  File: $FILE"
echo ""

# shellcheck disable=SC2086
./gradlew compare $GRADLE_ARGS
