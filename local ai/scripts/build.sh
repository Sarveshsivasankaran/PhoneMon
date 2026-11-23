#!/usr/bin/env bash
# Independent build runner for JARVIS Local AI Android project
set -euo pipefail

cd "$(dirname "$0")/.."
echo "==> Building JARVIS standalone Android Application..."
sh ./gradlew assembleDebug testDebugUnitTest --no-daemon --console=plain
echo "==> JARVIS build complete. Artifact at app/build/outputs/apk/debug/app-debug.apk"

