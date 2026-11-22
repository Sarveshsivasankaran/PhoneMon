#!/usr/bin/env bash
# Push Qwen3 and LFM2.5-VL model weights directly to connected Samsung Galaxy M31 over ADB
set -euo pipefail

TARGET_DIR="/sdcard/Android/data/com.phonebox.jarvis/files/models"
echo "==> Creating remote target directory: ${TARGET_DIR}..."
adb shell "mkdir -p ${TARGET_DIR}"

if [ -f "qwen3-1.7b-instruct-q4_k_m.gguf" ]; then
    echo "==> Pushing Qwen3 1.7B weights..."
    adb push qwen3-1.7b-instruct-q4_k_m.gguf "${TARGET_DIR}/"
fi

if [ -f "lfm2.5-vl-450m-q4_k_m.gguf" ]; then
    echo "==> Pushing LFM2.5-VL 450M weights..."
    adb push lfm2.5-vl-450m-q4_k_m.gguf "${TARGET_DIR}/"
fi

echo "==> Model transfer complete."

