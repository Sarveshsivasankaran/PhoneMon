# Technical Architecture: JARVIS On-Device AI Subsystem

---

## 1. Architectural Philosophy & Layered Decomposition

JARVIS is built using a clean, layered Android architecture that strictly separates concerns:
- **Presentation Layer (UI & HUD):** Jetpack Compose, reactive StateFlow UI patterns, animated hardware state visualizers.
- **Agent & Orchestration Layer:** Prompt formulation, intent classification, schema-based tool calling, execution observers, permission gates.
- **Perception Layer (Voice & Vision):** Wake-word spotting (KWS), voice activity detection (VAD), offline ASR, native TTS, CameraX vision stream.
- **Inference & Execution Layer (Core AI):** llama.cpp native C++ engine, ARM NEON SIMD optimizations, dynamic model memory pager.
- **Persistence Layer (Memory & State):** SQLite / Room ORM for semantic conversation turns, long-term user facts, and tool audit logs.
- **Integration Layer:** Secure, token-authenticated local network/USB adapters to PhoneMon desktop agent.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        FUTURISTIC COMPOSE HUD                          │
│     [Arc Reactor Visualizer]  [Glowing Edges]  [Real-Time State Bus]  │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ User Interactions & StateFlow
┌────────────────────────────────────▼───────────────────────────────────┐
│                    JARVIS AGENT ORCHESTRATION ENGINE                   │
│  ┌──────────────────────┐  ┌─────────────────┐  ┌───────────────────┐  │
│  │ Intent Classification│  │ Task & Tool Plan│  │ Safety & Auth Gate│  │
│  └──────────┬───────────┘  └────────┬────────┘  └─────────┬─────────┘  │
└─────────────┼───────────────────────┼─────────────────────┼────────────┘
              │                       │                     │
┌─────────────▼──────────┐ ┌──────────▼──────────┐ ┌────────▼────────────┐
│   PERCEPTION PIPELINE   │ │  CORE LOCAL INFERENCE│ │  MEMORY & KNOWLEDGE │
│  - Wake Word ("JARVIS") │ │  - Qwen3 1.7B GGUF   │ │  - Room DB (SQLite) │
│  - Offline ASR (Sherpa) │ │  - LFM2.5-VL 450M    │ │  - Semantic Facts   │
│  - Offline Neural TTS   │ │  - llama.cpp (ARM64) │ │  - Session Context  │
│  - CameraX Pipeline     │ │  - Memory Pager / LMK│ │  - Audit Logs       │
└────────────────────────┘ └────────────────────┘ └────────────────────┘
              │                                             │
              └───────────────────────┬─────────────────────┘
                                      │
┌─────────────────────────────────────▼──────────────────────────────────┐
│                   LOCAL INTEGRATIONS & DEVICE CONTROL                  │
│   - PhoneMon Desktop Adapter (`/api/settings`, `/api/push`, Status)    │
│   - Local Audio / Media Controls                                       │
│   - Device Diagnostics & Sensors                                       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Details

### 2.1 Core AI & Inference (`core/ai/`)
- **`ModelManager.kt`:** Oversees lifecycle, download status, integrity checking (SHA256), memory monitoring, and sequential loading/unloading between Qwen3 1.7B and LFM2.5-VL.
- **`LlamaBridge.kt` / `native/inference/`:** JNI bridge calling into `llama.cpp` lib:
  - Context initialization with `n_ctx=2048`, `n_threads=4`, `n_batch=512`.
  - Prompt tokenization, generation streaming with early stopping token detection (`<|im_end|>`, `<|endoftext|>`).
  - Sampling controls: Temperature (0.7), Top-P (0.8), Top-K (40), Repetition Penalty (1.1).

### 2.2 Voice & Audio Subsystem (`core/voice/`)
- **`WakeWordDetector.kt`:** Continuous low-power listening via `AudioRecord` (16 kHz, 16-bit PCM mono). Analyzes audio frames using a small ONNX keyword spotter for the wake word **"JARVIS"**.
- **`VoiceActivityDetector.kt`:** Energy and zero-crossing rate VAD to identify speech start and silence boundaries (500ms trailing silence triggers end-of-speech).
- **`SpeechToTextEngine.kt`:** Offline streaming speech recognizer translating audio buffers into text without latency spikes.
- **`TextToSpeechEngine.kt`:** Android `TextToSpeech` integration configured with offline neural voices, modulated rate (1.05x), and adjusted pitch (0.95x) for a distinctive JARVIS voice.

### 2.3 Camera & Vision Subsystem (`core/vision/`)
- **`CameraManager.kt`:** Jetpack CameraX lifecycle wrapper supporting front and back cameras, flashlight toggle, and dynamic preview surface attachment.
- **Operating Modes:**
  1. *Ask and See:* Captures a single high-resolution image analysis frame upon voice command ("JARVIS, what am I looking at?").
  2. *Live Vision:* Periodically samples frames (e.g. 1 frame every 2 seconds) during an active conversation to maintain scene context.
  3. *Continuous Monitoring:* Low-frequency background visual monitoring with user approval.
- **`VisionPreprocessor.kt`:** Resizes frames to 384×384 or 448×448, normalizes RGB tensors, and feeds visual embeddings into the vision projector.

### 2.4 Agentic Tool Registry & Security (`core/agent/`, `core/security/`)
- **`ToolRegistry.kt`:** Defines strictly typed JSON schemas for all available actions:
  - `get_pc_metrics`: CPU, RAM, GPU, and disk status from PhoneMon desktop agent.
  - `control_media`: Play, pause, skip, volume controls on PC.
  - `switch_panel_page`: Remotely switches the active desk panel template.
  - `send_notification`: Triggers desktop toast notifications.
  - `query_device_status`: Battery, thermals, storage of the Galaxy M31.
  - `remember_fact`: Stores a persistent user fact into Room DB.
  - `recall_memory`: Searches stored memory for facts.
- **`PermissionManager.kt`:** Evaluates tool execution risks (READ-ONLY, REVERSIBLE, DESTRUCTIVE). Destructive or external actions mandate explicit user confirmation on the UI.
- **`ToolExecutor.kt`:** Validates parameter constraints against regex and bounds before execution. Arbitrary shell commands are explicitly forbidden.

### 2.5 Persistent Memory Subsystem (`database/`, `core/memory/`)
- **`JarvisDatabase.kt`:** Room database instance storing:
  - `ConversationEntity`: Timestamp, speaker (USER/JARVIS), message content, token count.
  - `MemoryFactEntity`: Subject, predicate, object, confidence, last verified timestamp.
  - `AuditLogEntity`: Tool name, parameters, execution timestamp, result, security status.
- **`MemoryManager.kt`:** Automatically summarizes older turns, extracts key user preferences, and injects relevant context into the LLM system prompt.

