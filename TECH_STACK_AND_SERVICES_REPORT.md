# PhoneBox: Technical Architecture, Tech Stack Mapping & External Services Analysis

This report provides an in-depth analysis of the **PhoneBox** repository (`phonebox-AI`). It covers every technology stack utilized across all components, maps each stack to its specific function and design rationale, details all external services and APIs, and provides a clear blueprint for copying, forking, and independently deploying this project.

---

## 1. Executive System Overview

PhoneBox turns an Android phone (running Android 5.0 Lollipop or newer) into a customizable desktop desk panel for PC monitoring, AI coding agent supervision, audio/macro touch controls, and smart home automation.

```mermaid
flowchart TD
    subgraph HostPC ["Host Computer (Windows / Linux / macOS)"]
        subgraph Agent [".NET 10 Desktop Agent (PhoneBoxAgent)"]
            Kestrel["Kestrel Web Server (HTTP/HTTPS)"]
            Monitors["System, Process & Media Monitors"]
            AISupervisor["AI Session & Quota Supervisor"]
            ControlDeck["Control Deck & Automation Engine"]
            TrayUI["System Tray & Settings Web Server"]
        end

        LocalServices["Local Daemons & CLIs (gh, codex, docker, obs, discord)"]
        LocalServices <--> Agent
    end

    subgraph PhoneDevice ["Android Device (Android 5.0+)"]
        subgraph AndroidApp ["PhoneBox Android App (DeskPanel)"]
            WebViewCore["Android WebView Engine (androidx.webkit)"]
            subgraph WebAssets ["Embedded Assets (assets/www)"]
                PanelEngine["Vanilla JS 12x6 Grid Engine"]
                WidgetCatalog["128+ Built-in Widgets"]
                I18nEngine["24-Language Localizer"]
            end
            Sensors["Proximity / Light / Battery Sensors"]
        end
    end

    subgraph Transport ["Bi-Directional Communication Layer"]
        USB["USB (ADB Reverse Tunnel + TLS Pinned + Key Auth)"]
        WiFi["Wi-Fi / Tailscale LAN (Direct HTTPS + TLS Pinned + Token Auth)"]
    end

    Agent <==> Transport <==> AndroidApp
    WebAssets <--> WebViewCore
    Sensors --> AndroidApp

    subgraph ExternalWeb ["External Web & Cloud APIs (Optional / Feature-Gated)"]
        PublicAPIs["Open-Meteo, LRCLIB, Radio-Browser, CoinGecko, Hacker News, Frankfurter"]
        UserAccounts["Spotify, Steam, GitHub, Home Assistant, Immich"]
    end

    Agent -.-> ExternalWeb
    AndroidApp -.-> PublicAPIs
```

### Core Architectural Principles
1. **Local-First & Zero Telemetry:** PhoneBox contains **no centralized accounts, cloud relay servers, telemetry, or analytics**. Everything runs directly between the user's computer and the phone.
2. **Reverse Proxying for Privacy:** External credentials (API keys, OAuth tokens, RTSP camera passwords) reside strictly on the desktop host inside `config.json` and are never forwarded to the mobile device.
3. **Resource Efficiency & Legacy Hardware Target:** The Android application is optimized to run smoothly on 10-year-old devices (Android 5.0, minSdk 21) using an embedded WebView wrapper, while the desktop agent runs as a self-contained single-file executable consuming minimal CPU and RAM.

---

## 2. Complete Tech Stack Mapping Across the Repository

The codebase is organized into four primary subsystems: the **Desktop Agent**, the **Android Application**, the **Web Panel / Settings UI**, and the **Build / Test Infrastructure**.

### 2.1 Component 1: Desktop Agent (`agent/PhoneBox.Agent/`)

| Technology / Tool | Version / Spec | Directory / Files | Purpose & Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **C# / .NET** | C# 13 / .NET 10.0 (`net10.0`, `net10.0-windows10.0.19041.0`) | `agent/PhoneBox.Agent/` | Primary backend language and runtime. Chosen for modern memory efficiency, zero-allocation Span/Memory APIs, native AOT/single-file publish capabilities, and native cross-platform support. |
| **ASP.NET Core / Kestrel** | Embedded in `Microsoft.NET.Sdk.Web` | `Program.cs`, `AgentEndpoint.cs` | Lightweight, high-performance HTTP/HTTPS and Server-Sent Events (SSE) server. Runs local endpoints for the phone panel, local webhooks, and the browser-based settings interface. |
| **Windows Forms** | .NET 10 WinForms | `Tray.cs`, `TrayStrings.*.resx` | Powers the system tray notification icon, context menu, and background lifecycle on Windows without requiring a persistent console window (`OutputType=WinExe`). |
| **NAudio** | 3.3.0 | `AudioControl.cs`, `SpectrumAnalyzer.cs` | Windows Core Audio (WASAPI) wrapper. Captures loopback audio for real-time 48-band FFT audio spectrum visualization and controls application-specific volume levels via Windows MMDevice APIs. |
| **QRCoder** | 1.8.1 | `Pairing.cs`, `Program.cs` | Generates pure C# offline QR codes displayed in the settings interface for zero-friction Wi-Fi pairing with the phone. |
| **WinRT APIs** | Windows 10 SDK (10.0.19041.0) | `MediaMonitor.cs` | Interacts with `GlobalSystemMediaTransportControlsSessionManager` to read metadata (track name, artist, album art) and control playback (play, pause, skip) across all modern Windows media players. |
| **Win32 P/Invoke Interop** | Win32 API | `KeySender.cs`, `JobObject.cs`, `AppConsole.cs`, `Shortcuts.cs` | Executes `SendInput` for macros, uses `JobObject` to guarantee child process cleanup (adb, ffmpeg), tracks foreground windows via `GetForegroundWindow`, and extracts icons via `ExtractIconEx`. |
| **Unix Native Bridges** | POSIX / macOS APIs | `agent/PhoneBox.Agent/Unix/` | Implements Apple SMC sensor reading for CPU/GPU temperatures, CoreAudio/AppleScript media control, MPRIS over D-Bus on Linux, and DRM `/proc/<pid>/fdinfo` for Intel GPU statistics. |
| **System.Text.Json** | Native .NET | `Config.cs`, `StateFrameCache.cs` | High-performance, zero-allocation JSON serialization and parsing with custom converters for configurations, state frames, and widgets. |
| **System.Security.Cryptography** | Native .NET | `Lan.cs`, `UsbLink.cs`, `SpotifyControl.cs` | Dynamically generates self-signed X.509 certificates with SHA-256 fingerprints for pinned TLS over USB and Wi-Fi, and executes OAuth2 PKCE S256 hashing. |

---

### 2.2 Component 2: Android Desk Panel App (`android/`)

| Technology / Tool | Version / Spec | Directory / Files | Purpose & Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Kotlin** | 2.0.21 | `android/app/src/main/java/` | Primary mobile development language. Chosen for null safety, concise syntax, and native coroutine support. |
| **Java** | OpenJDK 17 | `build.gradle.kts` | Compiler and toolchain runtime requirement for modern Android Gradle Plugin. |
| **Gradle** | 8.7.3 (Kotlin DSL) | `android/build.gradle.kts`, `settings.gradle.kts` | Build automation and dependency management toolchain. |
| **Android SDK** | Min SDK 21 (Android 5.0) · Target SDK 35 (Android 15) | `android/app/build.gradle.kts` | Enables repurposing old, retired smartphones (from 2014 onwards) while targeting modern security and display capabilities on newer devices. |
| **AndroidX WebKit** | 1.12.1 (`androidx.webkit:webkit`) | `MainActivity.kt`, `AgentLink.kt` | Provides `WebViewAssetLoader` to serve local assets via virtual `https://` schemas without file URI security restrictions or CORS issues, and supports custom request interception. |
| **Android Sensors API** | Native Android | `MainActivity.kt` | Listens to the Proximity Sensor (waking the screen upon hand approach) and Ambient Light Sensor (auto-brightness adjustment). |
| **Android Speech Recognizer** | Native Android | `Dictation.kt` | Interacts with the phone's native speech-to-text service (or Android 12+ on-device offline recognition) for voice dictation injected into the host PC. |
| **Custom TLS Certificate Pinning** | `javax.net.ssl` | `AgentLink.kt`, `UsbKey.kt` | Implements a custom `X509TrustManager` and `SSLSocketFactory` that only trusts the desktop agent's self-signed SHA-256 certificate fingerprint, preventing Man-in-the-Middle (MitM) attacks over USB adb reverse and LAN. |

---

### 2.3 Component 3: Desk Panel & Layout Editor Frontend (`android/app/src/main/assets/www/` & `settings.html`)

| Technology / Tool | Version / Spec | Directory / Files | Purpose & Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Vanilla JavaScript** | ES2022 / ES6 Modules | `assets/www/*.js`, `assets/www/widgets/*.js` | Core UI engine. Deliberately avoids heavy frameworks (React, Vue, Angular) to ensure near-zero memory footprint and instantaneous 60fps rendering on resource-constrained Android 5.0 hardware. |
| **Modern CSS3** | CSS Grid / Flexbox / Variables | `assets/www/style.css`, `appearance.css`, `controls.css` | Renders the responsive 12×6 desktop grid, customizable visual themes (AMOLED, Midnight, Warm, Graphite), glowing accent borders, and adaptive sizing. |
| **HTML5 Canvas** | Native Web API | `widgets/pc.js`, `widgets/spectrum.js`, `widgets/helpers.js` | Direct hardware-accelerated drawing for real-time CPU/GPU sparklines and 48-band audio spectrum bars. |
| **HTML5 Audio / HLS** | Native Web Audio / `<audio>` | `widgets/controls.js`, `ControlDeck.Hls.cs` | Streams internet radio and live media playback directly from the panel. |
| **Custom I18n Engine** | Custom JS | `assets/www/i18n.js`, `locale-*.js` | Zero-dependency internationalization supporting 24 distinct languages loaded dynamically on demand. |
| **Legacy Compat Layer** | Custom JS Polyfills | `assets/www/compat.js` | Polyfills modern JavaScript APIs (Promise, fetch, Array methods) for older Chromium WebView engines dating back to Chromium 37. |
| **Settings Web Editor** | Embedded Single-Page App | `agent/PhoneBox.Agent/settings.html` | Visual drag-and-drop page builder, template gallery, live phone preview, and configuration manager served directly from the desktop agent at `http://127.0.0.1:8765`. |

---

### 2.4 Component 4: Testing & Verification Infrastructure

| Technology / Tool | Version / Spec | Directory / Files | Purpose & Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **xUnit** | 2.9.3 | `agent/PhoneBox.Agent.Tests/` | .NET unit testing framework for validating configuration parsing, migrations, rule evaluation, radio directory lookups, and session tracking. |
| **Microsoft.NET.Test.Sdk** | 18.12.0 | `agent/PhoneBox.Agent.Tests.csproj` | Test runner integration for .NET CLI and IDEs. |
| **JUnit** | 4.13.2 | `android/app/src/test/` | JVM unit testing for Android wake packets and networking logic. |
| **Playwright** | 1.65.0 (`@playwright/test`) | `tools/ui-tests/` | End-to-end browser automation testing covering the panel UI, widget rendering, layout editor, and settings page against mock demo servers. |
| **Node.js** | 22.x | `tools/ui-tests/package.json` | Execution runtime for Playwright UI test suites. |
| **Python 3** | 3.10+ | `tools/*.py` | Build and regression test automation: `demo-server.py` (web preview), `test-agent-stream.py` (Kotlin stream lifecycle), `sync-locales.py` (i18n consistency), and `smoke-agent.py` (binary verification). |

---

### 2.5 Component 5: Packaging, CI/CD, and Installation Tools

| Technology / Tool | Version / Spec | Directory / Files | Purpose & Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **PowerShell** | 5.1 & Core 7+ | `setup.ps1`, `tools/*.ps1` | Automated Windows installer that downloads binaries, verifies SHA-256 checksums, configures ADB, creates Start Menu shortcuts, and manages autostart. |
| **POSIX Bash** | Bash 4+ | `setup.sh`, `tools/*.sh` | Automated Linux and macOS installer configuring systemd user services or macOS `launchd` LaunchAgents (`net.oxog.phonebox.agent.plist`). |
| **Scoop Manifest** | JSON format | `bucket/phonebox.json` | Package management manifest for automated Windows installation via the Scoop CLI. |
| **WinGet Manifests** | YAML v1.6.0 | `packaging/winget/manifests/` | Official Windows Package Manager (winget) submission manifests. |
| **GitHub Actions** | CI/CD YAML | `.github/workflows/` | Automated build matrices for Windows (`win-x64`), Linux (`linux-x64`, `linux-arm64`), and macOS (`osx-arm64`, `osx-x64`), plus APK compilation and artifact attestation. |
| **SignPath Foundation** | Cloud Signing Service | `.github/actions/sign-windows-agent/` | Authenticode code-signing integration for Windows executables (`PhoneBoxAgent.exe`) to prevent SmartScreen warnings. |
| **Apple Code Signing** | PKCS#12 (`.p12`) | `.github/actions/sign-macos-agent/` | Cryptographically signs macOS binaries with a persistent designated requirement to preserve macOS Accessibility permissions across updates. |

---

## 3. Detailed Feature-to-Technology Mapping

The table below maps every high-level user capability to the underlying classes, technologies, and system protocols:

| Feature / Capability | Core Source Files | Underlying Technologies & Protocols | How & Why It Works |
| :--- | :--- | :--- | :--- |
| **Hardware Stats (CPU, GPU, RAM, Disks)** | `SystemMonitor.cs`, `WindowsGpu.cs`, `DrmUsage.cs` | Win32 PerfCounters, NVML, LibreHardwareMonitor API (`127.0.0.1:8085`), Linux `sysfs`/`procfs`, Apple SMC | Probes OS metrics locally. Supports optional LibreHardwareMonitor web server for in-depth motherboard/GPU sensors. |
| **Audio Volume & Mixer Control** | `AudioControl.cs`, `ControlDeck.cs` | Windows WASAPI (via `NAudio`), Linux PulseAudio/PipeWire, macOS CoreAudio | Directly manipulates master and per-process audio session volumes from the phone screen. |
| **Audio Spectrum Visualizer** | `SpectrumAnalyzer.cs`, `spectrum.js` | WASAPI Loopback Capture, FFT Algorithm, HTML5 2D Canvas | Captures real-time output audio without virtual audio cables, runs FFT, sends 48 frequency bands to the phone canvas. |
| **AI Coding Sessions (Claude, Codex, Antigravity, Cursor, OpenCode)** | `SessionMonitor.cs`, `ClaudeHook.cs`, `CodexQuota.cs`, `OpenCodeApprovals.cs` | Local JSONL parsing, Stdio JSON-RPC, Local REST endpoints, Unix sockets | Monitors active coding sessions by inspecting local transcript files and local agent CLIs. Relays permission prompts (Allow/Deny) directly to the phone. |
| **AI Quota Tracking & Costs** | `QuotaMonitor.cs`, `CodexBar.cs`, `CodexQuota.cs` | Anthropic OAuth usage API, OpenAI CLI app-server, CodexBar CLI (`codexbar cost`) | Reads token limits, reset schedules, and monthly cost projections for AI models. |
| **Now Playing Media & Lyrics** | `MediaMonitor.cs`, `LyricsService.cs`, `lyrics.js` | WinRT Media Control, MPRIS D-Bus, LRCLIB REST API | Synchronizes real-time media title/artist and fetches timed `.lrc` lyrics from LRCLIB for karaoke-style auto-scrolling. |
| **USB Desk Panel Connection** | `AdbReverse.cs`, `UsbLink.cs`, `AgentLink.kt` | Android Debug Bridge (ADB), TLS Certificate Pinning, Localhost Port Reverse | Binds phone's local port to computer via `adb reverse tcp:8765 tcp:...`, secured with mutual ephemeral keys and pinned TLS certificates. |
| **Wi-Fi / Tailscale LAN Mode** | `Lan.cs`, `Pairing.cs`, `AgentLink.kt` | HTTPS, X.509 RSA/SHA-256 self-signed certificates, QR Code pairing | Allows wireless connectivity over local Wi-Fi or encrypted Tailscale mesh without USB cables. |
| **Automation Rules & Charge Guard** | `RuleEngine.cs`, `ChargeGuard.cs` | Event-driven state machine, Home Assistant REST, Smart switches | Automates page switching when specific conditions occur (e.g. mic turns on -> switch to meeting page). Cuts smart plug power to preserve phone battery between 50% and 80%. |
| **Smart Control Deck (Stream Deck alternative)** | `ControlDeck.cs`, `KeySender.cs`, `controls.js` | Win32 `SendInput`, macOS `CGEvent`, MIDI, OSC (Open Sound Control), Webhooks | Enables macro execution, application launch, virtual dials, and audio routing buttons. |

---

## 4. Comprehensive External Services & APIs Analysis

When copying or forking this repository to build your own version, understanding what external services are contacted, how they authenticate, and whether they are required is crucial.

> [!IMPORTANT]
> **Zero Required Cloud Backend:** PhoneBox has **NO mandatory external servers, accounts, or cloud subscriptions**. If you run PhoneBox with PC monitoring, touch shortcuts, and media controls, **it operates 100% locally and offline**.

Below is the exhaustive classification of all external services found in the codebase:

```mermaid
flowchart LR
    subgraph LocalOnly ["No Cloud Setup Required"]
        L1["PC Hardware & Audio Monitoring"]
        L2["Touch Shortcuts & Macros"]
        L3["Local AI Agents (Claude, Codex, Antigravity)"]
        L4["Local Docker, OBS & Media Sessions"]
    end

    subgraph FreeAPIs ["Free Public APIs (No API Keys Required)"]
        F1["Open-Meteo (Weather & Air Quality)"]
        F2["LRCLIB (Synced Lyrics)"]
        F3["Radio-Browser (Internet Radio)"]
        F4["Frankfurter (FX Rates)"]
        F5["CoinGecko (Crypto Rates)"]
        F6["Hacker News (Top Stories)"]
    end

    subgraph AuthAPIs ["Optional User Services (API Keys / OAuth Required)"]
        A1["Spotify Web API (Client ID + PKCE)"]
        A2["Discord Developer App (Client ID + Secret)"]
        A3["Steam Web API (SteamID64 + API Key)"]
        A4["GitHub API (gh CLI / PAT)"]
        A5["Home Assistant (Token + URL)"]
        A6["Immich Server (API Key + URL)"]
    end
```

---

### 4.1 Category 1: Free Public Web APIs (No Credentials Required)

These services are built into various widgets. They require **no API keys, accounts, or developer configurations**. They work out-of-the-box:

| Service | Target Endpoints | Code Location | What It Sends / Receives | Configuration Needed? |
| :--- | :--- | :--- | :--- | :--- |
| **Open-Meteo Weather** | `api.open-meteo.com/v1/forecast` | `main.js`, `sky.js` | Sends latitude/longitude coordinates; receives temperature, hourly weather forecasts, and wind data. | **None.** Default coordinates (Tallinn) can be adjusted in Settings. |
| **Open-Meteo Air Quality** | `air-quality-api.open-meteo.com/v1/air-quality` | `sky.js` | Sends latitude/longitude coordinates; receives European AQI and US AQI indices. | **None.** |
| **Open-Meteo Geocoding** | `geocoding-api.open-meteo.com/v1/search` | `settings.html` | Sends city search string; receives coordinates. | **None.** Used only when searching cities in settings. |
| **LRCLIB** | `https://lrclib.net/api/get` | `LyricsService.cs` | Sends track title, artist, album, and duration; receives synchronized `.lrc` lines. Rate-limited with backoff. | **None.** Free public lyrics database. |
| **Community Radio Browser** | `*.api.radio-browser.info/json/` | `ControlDeck.RadioDirectory.cs` | Queries country/tag station directories; receives radio stream URLs. | **None.** Community-operated directory mirrors. |
| **RadioTime (TuneIn OPML)** | `opml.radiotime.com/Search.ashx` | `ControlDeck.RadioLogos.cs` | Sends station query name; receives station logo image URLs. | **None.** |
| **Hacker News (Firebase)** | `hacker-news.firebaseio.com/v0/` | `widgets/web.js` | Fetches top story IDs and item details. | **None.** Official Firebase public read-only API. |
| **Frankfurter FX API** | `api.frankfurter.dev/v1/` | `widgets/web.js` | Sends currency symbols (e.g., EUR, USD); receives daily ECB currency exchange rates. | **None.** Free open-source currency API. |
| **CoinGecko API** | `api.coingecko.com/api/v3/coins/markets` | `widgets/web.js` | Sends crypto coins list; receives 24h prices and 7-day sparkline arrays. | **None.** Public tier (rate-limited to 5 min poll). |
| **Cloudflare DNS Ping** | `1.1.1.1` (ICMP Echo) | `PingMonitor.cs` | Sends standard ICMP echo packets every 5 seconds to calculate network latency. | **None.** Host can be changed in settings. |

---

### 4.2 Category 2: Optional User-Configured Services (Require Personal API Keys / Apps)

These integrations are strictly optional. They activate only if the user adds the corresponding widget and supplies their own credentials:

| Service / Feature | Target Endpoints & Protocols | Files | Credentials Required & Configuration Steps |
| :--- | :--- | :--- | :--- |
| **Spotify Control** | `accounts.spotify.com`, `api.spotify.com/v1/`, `i.scdn.co` | `SpotifyControl.cs` | **Spotify Developer App:**<br>1. Register app at [developer.spotify.com](https://developer.spotify.com).<br>2. Set Redirect URI to `http://127.0.0.1:8765/api/controls/spotify/callback`.<br>3. Enter **Client ID** in PhoneBox Settings › General.<br>4. Uses PKCE authorization code flow. Token saved locally in `config.json.spotify.json`. |
| **Discord Widget** | Named Pipe / Unix Socket (IPC), `discord.com/api/oauth2/token` | `DiscordMonitor.cs`, `docs/DISCORD.md` | **Discord Developer Application:**<br>1. Create app at [discord.com/developers](https://discord.com/developers/applications).<br>2. Add OAuth2 redirect `http://127.0.0.1`.<br>3. Enter **Client ID** and **Client Secret** in Settings.<br>4. Performs local RPC handshake to mute, deafen, and display voice participants. |
| **Steam Friends & Activity** | `api.steampowered.com/ISteamUser/`, `*.steamstatic.com` | `SteamMonitor.cs`, `docs/STEAM.md` | **Steam Web API Key:**<br>1. Obtain key at [steamcommunity.com/dev/apikey](https://steamcommunity.com/dev/apikey).<br>2. Enter **Steam Web API Key** and your **SteamID64** in Settings.<br>3. Steam profile friend list must be set to **Public**. |
| **GitHub Integration** | `api.github.com/graphql` | `GitHubMonitor.cs` | **GitHub CLI (`gh`):**<br>Uses the host PC's locally authenticated `gh` CLI (`gh auth login`). Requires no secret entry in PhoneBox. |
| **Home Assistant** | User's Home Assistant URL (`/api/states`, `/api/services/`) | `HomeAssistant.cs` | **Long-Lived Access Token:**<br>1. Generate token in Home Assistant (User Profile › Long-Lived Access Tokens).<br>2. Enter **Home Assistant URL** (e.g. `http://homeassistant.local:8123`) and **Token** in Settings. |
| **Immich Photo Frame** | User's Immich URL (`/api/albums`, `/api/search/metadata`) | `ImmichPhotos.cs` | **Immich API Key:**<br>1. Generate API key in Immich web interface (Account Settings › API Keys).<br>2. Enter **Immich Server URL** and **API Key** in Settings › Photo Frame. |
| **OBS Studio Control** | WebSocket (`ws://127.0.0.1:4455`) | `ObsMonitor.cs` | **OBS WebSocket 5 Password:**<br>Enable WebSocket server in OBS Studio (Tools › WebSocket Server Settings) and enter password in Settings. |
| **RTSP IP Cameras** | RTSP / RTSPS streams | `CameraFeeds.cs` | **IP Camera RTSP URLs & local `ffmpeg`:**<br>Requires `ffmpeg` installed on host PC and stream URLs entered in settings (e.g., `rtsp://user:pass@192.168.1.50:554/stream1`). |
| **iCalendar Feeds** | Private `.ics` / `webcal://` URLs | `CalendarMonitor.cs` | Secret iCal URLs from Google Calendar, Outlook, Apple Calendar, or Nextcloud. |
| **News RSS / Atom** | Custom RSS/Atom XML URLs | `FeedMonitor.cs` | Any valid RSS feed URL. |

---

### 4.3 Category 3: Local AI Coding Agents (Local Daemons & Files)

PhoneBox monitors AI coding agents by reading local files or talking to loopback daemons. **No API keys for Anthropic or OpenAI are configured inside PhoneBox itself**:

* **Claude Code (`ClaudeHook.cs`, `ClaudeTranscript.cs`):** Inspects local files in `~/.claude/sessions/` and reads usage via Claude Code's local CLI credentials stored in `~/.claude/.credentials.json`.
* **OpenAI Codex (`CodexQuota.cs`, `CodexHook.cs`):** Spawns the local `codex app-server` binary over stdio JSON-RPC. Codex uses its own local authentication (`~/.codex/auth.json`).
* **CodexBar (`CodexBar.cs`):** Runs the local CLI command `codexbar cost --days 30 --format json`.
* **Google Antigravity (`SessionMonitor.cs`):** Scans local transcript files in `~/.gemini/antigravity/brain` and `~/.gemini/antigravity-cli/brain`.
* **Cursor (`SessionMonitor.cs`):** Scans local transcript JSONL files in `~/.cursor/projects/*/agent-transcripts`.
* **OpenCode (`OpenCodeApprovals.cs`, `OpenCodeSessions.cs`):** Connects to the local OpenCode HTTP daemon (`127.0.0.1`) using the local password from `~/.local/state/opencode/service.json`.

---

### 4.4 Category 4: Hardcoded Repository URLs & Distribution Dependencies

If you clone or fork this repository to publish your own branded application, the following hardcoded identifiers and external publishing services **must be reconfigured**:

```
1. GitHub Repository References:
   - Config.cs (Line 244, 660): Defaults to "ersinkoc/phonebox" for update checks.
   - setup.ps1 (Line 51): $Repo = 'ersinkoc/phonebox'
   - setup.sh (Line 21): repo="ersinkoc/phonebox"
   - bucket/phonebox.json: URL links to "https://github.com/ersinkoc/phonebox"
   - UpdateService.cs: Calls "https://api.github.com/repos/ersinkoc/phonebox/releases/latest"

2. Android Package Name / Application ID:
   - android/app/build.gradle.kts: namespace = "net.oxog.phonebox.deskpanel", applicationId = "net.oxog.phonebox.deskpanel"
   - setup.ps1, setup.sh, UpdateService.cs: Reference "net.oxog.phonebox.deskpanel"

3. macOS LaunchAgent Bundle Identifier:
   - setup.sh: plist="$HOME/Library/LaunchAgents/net.oxog.phonebox.agent.plist"
   - UpdateService.cs: env("XPC_SERVICE_NAME") == "net.oxog.phonebox.agent"

4. Web Demo URL for QR Pairing Fallback:
   - Lan.cs / Pairing.cs / README.md: "https://ersinkoc.github.io/phonebox/"

5. Code Signing & CI Secrets (in .github/workflows/release.yml):
   - PHONEBOX_KEYSTORE_BASE64, PHONEBOX_STORE_PASSWORD, PHONEBOX_KEY_ALIAS, PHONEBOX_KEY_PASSWORD (Android APK release signing)
   - MACOS_SIGNING_P12_BASE64, MACOS_SIGNING_P12_PASSWORD (macOS binary signing)
   - SIGNPATH_API_TOKEN, SIGNPATH_SIGNING_POLICY (Windows Authenticode signing via SignPath)
```

---

## 5. Blueprint: Creating and Deploying Your Own Version

Follow this step-by-step checklist to fork or copy PhoneBox and run it under your own identity:

### Step 1: Rebrand and Update Repository References
1. In `agent/PhoneBox.Agent/Config.cs`, update:
   ```csharp
   public string Repo { get; set; } = "your-github-username/your-repo-name";
   ```
2. In `setup.ps1` and `setup.sh`, update:
   ```powershell
   $Repo = 'your-github-username/your-repo-name'
   ```
   ```bash
   repo="your-github-username/your-repo-name"
   ```
3. Update `bucket/phonebox.json` download URLs to point to your GitHub release assets.

### Step 2: Configure Android Application & Signing
1. If you wish to change the package name, update `android/app/build.gradle.kts`:
   ```kotlin
   namespace = "com.yourcompany.deskpanel"
   defaultConfig {
       applicationId = "com.yourcompany.deskpanel"
   }
   ```
   *(Also reflect this change in `setup.ps1`, `setup.sh`, and `UpdateService.cs`).*
2. Generate an Android release keystore:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias deskpanel -keyalg RSA -keysize 2048 -validity 10000
   ```
3. Base64-encode `release.keystore` and add it to your GitHub Repository Secrets as `PHONEBOX_KEYSTORE_BASE64`, along with passwords and alias.

### Step 3: Configure CI/CD Workflows
1. In `.github/workflows/release.yml`:
   * If you don't use SignPath Foundation for Windows signing, remove or disable the `Sign PhoneBoxAgent.exe (SignPath)` step.
   * If you don't have an Apple Developer certificate, generate a self-signed P12 using `tools/make-macos-signing-cert.sh` and populate `MACOS_SIGNING_P12_BASE64`.
2. Push your code to GitHub and create your first release tag (e.g., `v1.0.0`).

### Step 4: Local Build & Run (Without GitHub CI)
You can build and run the entire system locally without setting up any CI/CD or cloud services:

1. **Build and Run Desktop Agent:**
   ```bash
   cd agent/PhoneBox.Agent
   dotnet run
   ```
   The settings dashboard will launch at `http://127.0.0.1:8765`.

2. **Build and Install Android App:**
   ```bash
   cd android
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Connect the Phone:**
   Connect the phone via USB cable with **USB Debugging** enabled. The desktop agent will establish the reverse ADB tunnel automatically, wake the phone, and load your custom desk panel.

