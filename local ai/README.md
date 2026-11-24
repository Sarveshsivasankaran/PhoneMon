# JARVIS — Fully Local AI Assistant for PhoneMon

## Running inference on the phone

Install the PhoneMon panel and JARVIS APKs from the same release on an ARM64 phone. Pair the desk panel with the desktop agent using its normal USB or Wi-Fi setup. JARVIS uses the panel's authenticated connection.

Open either launcher icon, tap the mascot on the live desk panel, and choose **Setup**. Install chat for Qwen2.5 0.5B or vision for LFM2.5-VL 450M and its projector. Wait for **Model installed**. Model files are SHA-256 verified and stored privately on the phone. Updates preserve weights. After uninstalling or clearing app data, install the weights again. Chat and front-camera analysis work offline after setup.

In the desktop agent's settings, open **JARVIS Local AI** to set the assistant name, personality, model and speaking voice. Save the profile there. The active assistant fetches changes every ten seconds, including after reconnecting. Install the selected model on the phone. Desk-panel operations require a connection. Sensitive actions show their parameters for confirmation on the phone.

## Build

Use JDK 17 and Android SDK 35. Run `scripts/build.ps1` on Windows or `bash scripts/build.sh` on Linux/macOS. The debug APK is generated in `app/build/outputs/apk/debug/`.

## Voice and camera

With microphone permission granted and **Listen for “Jarvis” on this panel** enabled in Setup, say “Jarvis” followed by a question in the same utterance, or speak the question within eight seconds. The screen edges pulse and the supplied badge appears above the input controls. The camera button captures a single frame on demand.

The desktop agent saves the assistant profile locally, and the phone keeps the last profile it received.
