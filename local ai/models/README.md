# Model Repository & Verification Guide

This directory holds the configuration, schemas, and verification tooling for the local models executed by JARVIS on the Samsung Galaxy M31.

---

## 1. Model Catalog

### A. Reasoning Model: Qwen3 1.7B
- **File:** `qwen3-1.7b-instruct-q4_k_m.gguf`
- **Architecture:** Qwen 3 (Alibaba Cloud / Qwen Team)
- **Parameters:** 1.7 Billion
- **Quantization:** Q4_K_M (4-bit medium K-quants)
- **Primary Source:** https://huggingface.co/Qwen/Qwen3-1.7B-GGUF
- **Memory footprint:** ~1.12 GB disk, ~1.36 GB active RAM
- **Role:** Intent classification, multi-step tool reasoning, conversational response generation.

### B. Vision Model: LFM2.5-VL 450M
- **Files:** `lfm2.5-vl-450m-q4_k_m.gguf` + `lfm2.5-vl-450m-mmproj-f16.gguf`
- **Architecture:** Liquid Foundation Model multimodal variant (Liquid AI)
- **Parameters:** 450 Million
- **Quantization:** Q4_K_M
- **Primary Source:** https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF
- **Memory footprint:** ~360 MB (model) + 95 MB (projector)
- **Role:** High-efficiency vision-language reasoning on captured phone-camera frames.

### C. Wake-Word Model: "JARVIS" Keyword Spotter
- **File:** `jarvis_kws.onnx`
- **Format:** ONNX 16kHz Keyword Spotting model
- **Size:** ~1.8 MB
- **Role:** Low-power continuous listening on audio input stream.

---

## 2. On-Device Model Storage Location

Models are stored within the application's isolated sandbox storage:
- **Internal Storage:** `/data/data/com.phonebox.jarvis/files/models/`
- **Scoped External Storage:** `/storage/emulated/0/Android/data/com.phonebox.jarvis/files/models/`

Models are **NEVER** packaged inside the `.apk` file (which would exceed APK size limits and waste cellular data during app updates). Instead, the app provides:
1. In-App Download Manager with progress bar, resume capability, and SHA-256 verification.
2. Manual ADB push support for developer convenience:
   ```bash
   adb push <model-file>.gguf /sdcard/Android/data/com.phonebox.jarvis/files/models/
   ```

