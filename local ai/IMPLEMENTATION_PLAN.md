# JARVIS Implementation Plan

---

## Phase Breakdown & Milestone Verification

### Phase 1: Audit and Isolation (Completed)
- [x] Record Git status and tracked-file baseline (685 tracked files).
- [x] Confirm no pre-existing files exist in `local ai/`.
- [x] Create foundational documentation: `README.md`, `ARCHITECTURE.md`, `HARDWARE_COMPATIBILITY.md`, `IMPLEMENTATION_PLAN.md`.

### Phase 2: Standalone Android Project Setup
- [ ] Establish standalone Gradle project inside `local ai/` (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`).
- [ ] Configure Android Application ID `com.phonebox.jarvis`, compileSdk 35, minSdk 26, targetSdk 35.
- [ ] Configure dependencies: Jetpack Compose Material 3, CameraX 1.4, Room DB 2.6, Coroutines, Navigation, NDK support.
- [ ] Implement `JarvisApplication.kt` and `MainActivity.kt`.

### Phase 3: Core AI Engine & Model Management
- [ ] Implement `ModelConfig.kt` parsing `model-config.json`.
- [ ] Implement `ModelManager.kt` with download manager, SHA256 checksum verification, and storage management.
- [ ] Implement native inference bridge interface (`LlamaBridge.kt`, `LlamaContext.kt`).
- [ ] Implement dynamic sequential model pager (Qwen3 1.7B and LFM2.5-VL memory arbitration).
- [ ] Implement inference loop with cancellation token support and streaming callback tokens.

### Phase 4: Vision Pipeline (CameraX + LFM2.5-VL)
- [ ] Implement `CameraManager.kt` wrapping CameraX `ImageCapture`, `ImageAnalysis`, and `Preview`.
- [ ] Implement frame preprocessor: scaling, cropping, normalization to tensor formats.
- [ ] Implement the three camera modes:
  - *Ask and See* (single-shot capture & inquiry).
  - *Live Vision* (periodic session analysis).
  - *Continuous Monitoring* (low-frequency environmental tracking).
- [ ] Connect vision description output to Qwen3 reasoning context.

### Phase 5: Voice & Audio Pipeline
- [ ] Implement `WakeWordDetector.kt` with 16kHz audio recording, VAD energy filtering, and keyword spotter for **"JARVIS"**.
- [ ] Implement `SpeechToTextEngine.kt` for offline speech-to-text.
- [ ] Implement `TextToSpeechEngine.kt` with pitch/rate customized offline TTS.
- [ ] Implement `JarvisVoiceService.kt` (Android Foreground Service) for optional background wake-word listening.
- [ ] Add microphone security status indicators and explicit listening toggles.

### Phase 6: Agentic Task Execution & PhoneMon Adapter
- [ ] Implement `ToolRegistry.kt` with standard tool schemas (`get_pc_metrics`, `control_media`, `switch_panel_page`, `send_notification`, etc.).
- [ ] Implement `ToolValidator.kt` with strict parameter bounds and sanitization.
- [ ] Implement `PermissionManager.kt` categorizing tools into SAFE, APPROVAL_REQUIRED, and BLOCKED.
- [ ] Implement `PhoneMonAdapter.kt` in `integrations/phonemon/` communicating strictly via supported local PhoneMon HTTP/WebSocket endpoints without modifying any PhoneMon source.

### Phase 7: Persistent Memory Subsystem
- [ ] Configure Room entities: `ConversationEntity`, `MemoryFactEntity`, `AuditLogEntity`.
- [ ] Implement Room DAOs and `JarvisDatabase.kt`.
- [ ] Implement `MemoryManager.kt` for conversation logging, preference recall, and semantic fact retrieval.
- [ ] Provide user controls in UI to view, export, and delete memories.

### Phase 8: Futuristic JARVIS UI (HUD)
- [ ] Design Color Palette: Deep Space Navy (`#060913`), Arc Cyan (`#00F2FE`), Reactor Blue (`#4FACFE`), Pulsing Violet (`#7F00FF`).
- [ ] Implement `ArcReactorVisualizer.kt`: Dynamic pulsating circular orb reacting to real backend states (`OFFLINE`, `STANDBY`, `LISTENING`, `THINKING`, `VISION`, `EXECUTING`, `SPEAKING`, `ERROR`).
- [ ] Implement `GlowingEdgeEffect.kt`: Animated neon borders upon wake-word activation.
- [ ] Implement `AudioWaveformVisualizer.kt`: Real-time audio amplitude bar graph during listening/speaking.
- [ ] Build Chat & Observation stream, Camera Preview overlay, and Device Telemetry cards.

### Phase 9: Verification & Zero-Modification Validation
- [ ] Run full project compilation check.
- [ ] Compare tracked files against the initial 685-file baseline to prove ZERO files outside `local ai/` were touched.
- [ ] Document physical device performance benchmarks and deployment guide.

