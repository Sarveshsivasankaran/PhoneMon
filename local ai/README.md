# JARVIS — Fully Local AI Assistant for PhoneMon

## Running inference on the phone

The Android chat and camera controls use a llama.cpp server on the phone's loopback interface. Install llama-server in a local Android terminal and start it with a GGUF model your phone can fit in RAM:

```sh
llama-server -m /path/to/model.gguf --host 127.0.0.1 --port 8080 -c 1024 -t 2 -b 64
```

For camera questions, start the same server with a supported vision model and its matching `--mmproj` file. One server handles both inputs. The app reports when the runtime is unavailable. Wake recognition requires Android 12 or newer and an installed English on-device recognizer. Typed input also works on earlier supported Android versions.


## Build

Use JDK 17 and Android SDK 35. Run `scripts/build.ps1` on Windows or `bash scripts/build.sh` on Linux/macOS. The debug APK is generated in `app/build/outputs/apk/debug/`.

## Voice and camera

With microphone permission granted and the microphone enabled, say “Jarvis” followed by a question in the same utterance, or speak the question within eight seconds. The screen edges pulse and the supplied badge appears above the input controls. The camera button captures a single frame on demand.

The web dashboard is a development preview. Its simulated telemetry does not measure the phone's separate model-server process.
