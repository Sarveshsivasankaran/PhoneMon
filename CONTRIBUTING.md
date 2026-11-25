# Contributing to PhoneMon

Thank you for helping improve PhoneMon! Because PhoneMon runs persistently on a user's computer, launches configured actions, and communicates with an Android device over USB (`adb reverse`), Wi-Fi, or Tailscale, reviews focus carefully on security boundaries, privacy guarantees, and backward compatibility. Following the steps and rules below helps get your pull request reviewed and merged quickly.

## Before You Start

- **Small fixes** (bug fixes, typos, missing tests, documentation improvements): you can open a pull request directly.
- **New widgets, integrations, AI tools, or protocol changes**: open an issue first using the [feature request form](.github/ISSUE_TEMPLATE/feature_request.yml) so we can align on design, privacy, and UX before you invest time building something that may need major changes. For bugs, use the [bug report form](.github/ISSUE_TEMPLATE/bug_report.yml).
- **Keep each pull request focused:** one logical change per PR (aim for under ~800 changed lines excluding tests). Split large work into smaller, self-contained PRs that each build and pass tests on their own. Never mix unrelated refactors or dependency bumps with a feature or fix.

---

## Contribution Steps

1. **Fork and create a branch**
   - Fork [`Sarveshsivasankaran/PhoneMon`](https://github.com/Sarveshsivasankaran/PhoneMon) and clone your fork locally.
   - Branch off `main` with a descriptive name (for example `fix/wake-loop` or `feat/gpu-fan-tile`).
2. **Set up your development environment**
   - Install `.NET 10 SDK` (desktop agent), `JDK 17` + `Android SDK Platform 35` (Android app), `Python 3` (release checks & demo server), and `Node.js 22` (Playwright UI tests).
   - See the [Development guide](docs/DEVELOPMENT.md) for running `python tools/demo-server.py`, building debug APKs, or running an isolated development agent alongside an installed copy.
3. **Implement your change following the rules below**
   - Adhere to the Security, Privacy, Settings/Widget, JARVIS AI, and Android rules in the sections below.
   - Add or update unit/UI tests that fail without your fix (where practical). Keep all tests deterministic and independent of the local machine (no hardcoded ports, real user config directories, or host wall clocks).
   - If you change widget layouts or visual UI, capture updated screenshots (`tools/ui-tests/media.js` or at both small and large widget sizes).
4. **Run local verification checks**
   Run all suites that cover the areas you touched (matches CI in [build.yml](.github/workflows/build.yml)):

   ```sh
   # Metadata, catalog counts, locale placeholders, and WinGet manifests
   python tools/check-release.py
   python tools/test-winget-manifests.py

   # Desktop agent (.NET 10)
   dotnet test agent/PhoneBox.Agent.Tests --nologo

   # Browser panel & settings UI suite
   cd tools/ui-tests
   npm ci && npx playwright install chromium   # or chrome/msedge locally
   npx playwright test

   # Android app (run whenever android/ files change; requires JDK 17 + Android SDK 35)
   cd android
   ./gradlew lintDebug assembleDebug testDebugUnitTest --no-daemon
   python ../tools/test-agent-stream.py
   ```

   If you modify installer or packaging scripts (`setup.ps1`, `setup.sh`, `scripts/`), also run:
   - Windows: `./tools/test-setup-download.ps1`, `./tools/test-install-apk.ps1`, `./tools/test-build-staging.ps1`, and `./tools/test-windows-source-publish.ps1`
   - Linux/macOS: `python3 tools/test-unix-installer.py --bash /bin/bash`, `python3 tools/test-unix-autostart.py /bin/bash`, and `python3 tools/test-unix-uninstall.py`
5. **Commit and open a pull request**
   - Keep code, comments, UI copy, and commit messages in **English**. Write comments to explain *why*, not *what*.
   - **Do not** edit `VERSION` or add release entries to `CHANGELOG.md` in routine PRs (the maintainer bumps versions at release time; see [docs/RELEASING.md](docs/RELEASING.md)).
   - Fill out every section of the [pull request template](.github/pull_request_template.md): what changes for the user, why, linked issue (`Fixes #...`), test commands executed, and devices/OS versions tested by hand.
   - Address all reviewer feedback and resolve automated review threads (CodeRabbit) before merge.

---

## Project Rules & Review Standards

### 1. Security Model
- **Network boundaries:** The desktop agent binds local settings to `127.0.0.1`. Phone traffic travels either over `adb reverse` (`agent/PhoneBox.Agent/UsbLink.cs` with pinned TLS + app USB key) or over optional Wi-Fi/Tailscale (`agent/PhoneBox.Agent/Lan.cs` with pinned TLS + paired key, limited strictly to `AllowedOverLan`). **Adding any route to `Lan.AllowedOverLan` is a security decision** — justify why the phone needs it in the PR description.
- **ID-only actions from the phone:** Shortcuts, apps, custom commands, calendars, and JARVIS actions are configured on the computer or in vetted catalogs; the phone sends **IDs or enum action names**, never raw shell commands, file paths, or arbitrary URLs.
- **Per-widget opt-in gating:** Any feature that controls the host computer (killing processes, Docker actions, mouse/keyboard/ADB control, OBS actions) must check its per-widget/settings toggle via `Widgets(enabledPagesOnly: true)` so removing or disabling the widget/option immediately revokes access. New capabilities that launch programs, capture media, or send data must default to **off**.
- **Secrets isolation:** `AgentConfig.ToPublic()` defines what reaches the phone. Keep tokens, API keys, webhook keys, calendar URLs, and command strings out of `ToPublic()` (including inside Smart Stack child options), logs, and error responses returned to the phone. Write sensitive files owner-only (`AppPaths.WriteAllTextPrivate` / `CreatePrivate`). Keep external CLI tokens (such as Claude OAuth) read-only.
- **Process execution safety:** Launch subprocesses exclusively via `ProcessStartInfo.ArgumentList` — never invoke a shell (`cmd.exe`, `/bin/sh -c`) and never interpolate untrusted request strings. Enforce timeouts and kill child processes on cancellation.
- **DOM & native WebView bridge safety:** Build UI DOM elements using `textContent` or `h()`; never pass agent data, network responses, or widget options to `innerHTML`. Custom HTML widgets must stay sandboxed. Every `@JavascriptInterface` call exposed to WebView must validate the per-session key held in `main.js` so untrusted frames cannot invoke bridge methods.
- **JARVIS Local AI & voice routing:** Keep tool execution validated through `ToolValidator` and `PhoneMonAdapter` allowlists. Route transport overrides (`adb`/`usb`/`tailscale`/`wifi`) cleanly without bypassing pairing or allowlists.
- **Vulnerability disclosure:** Do **not** open public issues for security bugs; report them privately per [SECURITY.md](SECURITY.md).

### 2. Privacy & Telemetry
- PhoneMon collects **zero telemetry**, requires **zero cloud accounts**, and sends no analytics. Keep it that way.
- **Document every outbound request:** Any new external host contacted by the agent, panel, or setup script requires a row in [PRIVACY.md](PRIVACY.md) documenting the destination host, exact data sent, when it fires, and how to turn it off.
- Any feature sending user data (audio, transcripts, camera frames, location, or files) off-device must be explicit opt-in and clearly labeled where enabled.
- Never call an integration "local" if it can initiate internet requests.

### 3. Settings, Widgets, Panel UI & Compatibility
- **Adding a setting:** Define defaults and input normalization in `Config.cs`, add the matching key to all three default config files (`config.default.json`, `config.default.linux.json`, `config.default.macos.json`), and expose it in `agent/PhoneBox.Agent/settings.html` (preserving `X-Config-Version` optimistic locking). Keep computer-only secrets out of `ToPublic()`.
- **Adding a widget:** Register it in `agent/PhoneBox.Agent/widgets.json`, implement it in `android/app/src/main/assets/www/widgets/`, add its icon to `WIDGET_ICONS` in `main.js`, update widget/template counts in `README.md` (verified by `tools/check-release.py`), and add demo data in `tools/pages/demo.js`. Outside the native Android app, every widget must show a clean empty state rather than throwing.
- **Android & WebView compatibility:** The app supports Android 5.0+ (`minSdk = 21`). Any Android framework call above API 21 requires a `Build.VERSION.SDK_INT` guard (`./gradlew lintDebug` treats `NewApi` as fatal). The WebView UI maintains Chromium 83 compatibility via `compat.js`, with full container-query scaling from Chromium 105+. Test changes to `minSdk` or permissions carefully and list tested OS/WebView versions in your PR.
- **Backward compatibility:** Do not remove existing user options, rename config keys, or alter saved defaults without automatic config migration and a note in the PR.
- **Translations:** Use `t()`, `tc()`, and `tp()` from `i18n.js`; preserve placeholders (`{name}`, `%1$s`, `{0}`). When adding or updating locales, run `python tools/sync-locales.py` and verify with `python tools/check-release.py`.

### 4. AI-Assisted Contributions
Contributions created with AI coding assistants are welcome. You remain responsible for the entire diff: inspect every changed line, confirm comments/descriptions match actual code behavior, and verify all test suites pass locally before opening your pull request.

## License
By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
