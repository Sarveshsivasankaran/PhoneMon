# JARVIS — Fully Local AI Assistant for PhoneMon

**Target Device:** Samsung Galaxy M31 (Exynos 9611, 6GB RAM, Android 10-12)  
**Host Application:** Independent Android Application (`com.phonebox.jarvis`)  
**Deployment Path:** `local ai/` (Completely isolated from PhoneMon core codebase)

---

## 1. Overview

JARVIS is an entirely on-device, offline, multimodal AI assistant designed to operate directly on the Samsung Galaxy M31. It delivers zero-cloud, privacy-first intelligence featuring natural language reasoning, camera vision perception, offline wake-word ("JARVIS") detection, speech-to-text, neural text-to-speech, persistent SQLite/Room memory, and an agentic tool execution system capable of interacting with PC and connected devices over local, authenticated PhoneMon interfaces.

### Core Philosophy
1. **Zero Cloud Dependency:** No model weights, reasoning tokens, audio bytes, or camera frames are ever transmitted to any external server or cloud service.
2. **Strict Project Isolation:** JARVIS is developed and built completely inside `local ai/` without modifying a single line of code, configuration, or documentation in the host PhoneMon repository.
3. **Hardware-Attuned Efficiency:** Tailored for the ARM64 octa-core Exynos 9611 and 6GB RAM, featuring dynamic sequential model swapping, 4-thread Cortex-A73 core pinning, and thermal-throttling mitigation.
4. **Agentic Safety:** Deterministic tool validation, explicit user authorization gates for destructive commands, and sandboxed interface calls.

---

## 2. Architecture Quick Reference

| Subsystem | Technology | Specifications |
|---|---|---|
| **Reasoning LLM** | Qwen3 1.7B | GGUF Q4_K_M (~1.12 GB), llama.cpp ARM64 runtime, 2048 token context |
| **Vision-Language** | LFM2.5-VL-450M | GGUF/ONNX (~350 MB), ViT image encoder + text decoder |
| **Wake-Word Engine** | Sherpa-ONNX / Porcupine KWS | Keyword: **JARVIS**, offline energy-efficient keyword spotting |
| **Speech-to-Text (STT)**| Sherpa-ONNX Whisper/Zipformer | Offline streaming / chunked ASR, 16kHz mono audio |
| **Text-to-Speech (TTS)**| Android Native Neural TTS | Low-latency offline voice synthesis with pitch/rate modulation |
| **Camera Perception** | Android Jetpack CameraX 1.4 | 3 modes: *Ask & See*, *Live Vision*, *Continuous Monitoring* |
| **Persistent Memory** | Android Jetpack Room / SQLite | Semantic facts, conversation history, user preferences, tool audit logs |
| **Agentic Framework** | JARVIS Agent Orchestrator | Intent analyzer, JSON tool calling, permission validator, observation loop |
| **User Interface** | Jetpack Compose + Material 3 | Futuristic Dark Navy/Cyan/Violet HUD, animated glowing reactor orb |

---

## 3. Directory Layout

```text
local ai/
├── README.md                     # High-level overview, quick start, and operation guide
├── ARCHITECTURE.md               # Detailed technical architecture and system design
├── IMPLEMENTATION_PLAN.md        # Complete multi-phase execution and verification plan
├── HARDWARE_COMPATIBILITY.md     # In-depth Exynos 9611, RAM, NPU, and thermal analysis
├── settings.gradle.kts           # Independent Gradle settings for standalone builds
├── build.gradle.kts              # Independent root build configuration
├── gradle.properties             # Independent build optimization flags
│
├── app/                          # Independent Android Application Module
│   ├── build.gradle.kts          # Dependencies, NDK targets, Room, Compose
│   └── src/main/
│       ├── AndroidManifest.xml   # Standalone manifest with permissions and services
│       ├── java/com/phonebox/jarvis/
│       │   ├── JarvisApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── core/            # AI, Voice, Vision, Memory, Agent modules
│       │   ├── nativebridge/    # JNI bridges to llama.cpp and sherpa
│       │   ├── integrations/    # PhoneMon desktop and local network adapters
│       │   └── ui/              # Futuristic Jetpack Compose HUD and animations
│       └── res/                 # Vector drawables, themes, strings, layouts
│
├── web/                          # Local Web Console & Management Dashboard
│   ├── index.html               # Model management & activeness UI
│   ├── style.css                # Futuristic HUD styling and responsive layout
│   └── dashboard.js             # Polling, model swap, and inference tester
│
├── models/                       # Model configuration, schemas, download specs
│   ├── README.md
│   └── model-config.json
│
├── native/                       # C++ JNI inference engines and audio DSP
│   ├── CMakeLists.txt
│   ├── inference/               # llama.cpp JNI wrapper and context manager
│   └── audio/                   # Low-latency ring-buffer and VAD audio processing
│
└── tests/                        # Comprehensive unit, benchmark, and validation suites
```

---

## 4. Setup and Installation Guide

### Step 1: Prerequisites
- **Computer:** Windows, Linux, or macOS with Android SDK (API 35) and JDK 17 installed.
- **Phone:** Samsung Galaxy M31 (Android 10, 11, or 12) with Developer Options and USB Debugging enabled.
- **Python:** Python 3.8+ (for running the local AI management console).

### Step 2: Build the Standalone JARVIS Application
Run the independent build script inside `local ai/`:

**On Windows (PowerShell):**
```powershell
./local ai/scripts/build.ps1
```

**On Linux / macOS:**
```bash
bash "local ai/scripts/build.sh"
```

The compiled APK will be created at:
`local ai/app/build/outputs/apk/debug/app-debug.apk`

### Step 3: Install the APK on Samsung Galaxy M31
Connect your phone with a USB data cable and run:
```bash
adb install -r "local ai/app/build/outputs/apk/debug/app-debug.apk"
```

### Step 4: Deploy Model Weights
JARVIS runs completely offline using local GGUF models. Push the model files to the application sandbox on the phone:

```bash
# Push Qwen3 1.7B Instruct weights (Reasoning LLM)
adb push qwen3-1.7b-instruct-q4_k_m.gguf /sdcard/Android/data/com.phonebox.jarvis/files/models/

# Push LFM2.5-VL 450M weights and multimodal projector (Vision LLM)
adb push lfm2.5-vl-450m-q4_k_m.gguf /sdcard/Android/data/com.phonebox.jarvis/files/models/
adb push lfm2.5-vl-450m-mmproj-f16.gguf /sdcard/Android/data/com.phonebox.jarvis/files/models/
```
Or run the automated helper script:
```bash
bash "local ai/scripts/push_models.sh"
```

### Step 5: Managing the AI Model and Checking Activeness
You can manage the on-device AI model and check live activeness in two ways:

1. **Inside PhoneMon Desktop Settings:**
   - Open PhoneMon settings in your browser: <http://127.0.0.1:8765>
   - Click on the new **JARVIS Local AI** section (`#ai`) in the left navigation.
   - View active model in RAM, swap models sequentially, verify checksums, check subsystem activeness, and send test prompts.

2. **Via the Dedicated Local AI Web Console:**
   - Run the zero-dependency local web server:
     ```bash
     python "local ai/scripts/web_server.py"
     ```
   - Open <http://127.0.0.1:8780> in any browser.
   - Live telemetry, RAM budget monitor, and interactive prompt tester will appear.

---

## 5. Security & Isolation Guarantee

- **No Root PhoneMon Code Touched:** Git status remains 100% clean across all 685 existing tracked files.
- **Explicit User Confirmation:** Any tool execution affecting PC state, files, or sensitive actions requires manual biometric or button confirmation on the phone screen.
- **Zero Silent Recordings:** The audio and camera pipelines are hardware-indicator bound; microphone status and camera preview states are clearly shown in the HUD.
