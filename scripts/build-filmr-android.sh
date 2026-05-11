#!/usr/bin/env bash
# Convenience wrapper — delegates to filmr/android/build-android.sh.
# Run from the unprocess repo root.
#
# Prerequisites: see filmr/android/build-android.sh
#
# Usage:
#   ./scripts/build-filmr-android.sh            # release
#   ./scripts/build-filmr-android.sh --debug    # debug

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/../../filmr/android/build-android.sh" "$@"
