# Project Migration Checklist: PhoneBox to PhoneMon

**Source Repository Baseline:** `ersinkoc/phonebox`  
**Target Migration Repository:** `https://github.com/Sarveshsivasankaran/PhoneMon.git` (`Sarveshsivasankaran/PhoneMon`)  
**Date of Migration:** 2026-11-23  

---

## 1. Migration Tasks & Verification Checklist

### Phase 1: Core Desktop Agent Configuration
- [x] **Update Agent Release Polling Default:** Updated `UpdateConfig.Repo` in `agent/PhoneBox.Agent/Config.cs` to `"Sarveshsivasankaran/PhoneMon"`.
- [x] **Update Agent Release Normalization Fallback:** Updated fallback repository string in `agent/PhoneBox.Agent/Config.cs` (`Updates.Repo = "Sarveshsivasankaran/PhoneMon"`).
- [x] **Update User-Agent Header:** Updated `LyricsService.cs` User-Agent string to point to `+https://github.com/Sarveshsivasankaran/PhoneMon`.
- [x] **Update Direct APK Download Link:** Updated `Program.cs` release APK URL to point to `https://github.com/Sarveshsivasankaran/PhoneMon/releases/download/...`.
- [x] **Update QR Pairing URL:** Updated `PairingCodes.Link` in `agent/PhoneBox.Agent/Pairing.cs` to `"https://sarveshsivasankaran.github.io/PhoneMon/pair/#v=1"`.
- [x] **Update Settings Web UI:** Updated `settings.html` fallback update repository string and defaults to `Sarveshsivasankaran/PhoneMon`.
- [x] **Update .NET Agent Unit Tests:** Updated `agent/PhoneBox.Agent.Tests/PairingTests.cs` to assert `sarveshsivasankaran.github.io` and `/PhoneMon/pair/`.

---

### Phase 2: One-Step Installers & Package Managers
- [x] **Update Windows Setup Script (`setup.ps1`):**
  - Updated `$Repo = 'Sarveshsivasankaran/PhoneMon'`.
  - Updated one-liner PowerShell install commands (`https://raw.githubusercontent.com/Sarveshsivasankaran/PhoneMon/main/setup.ps1`).
- [x] **Update Linux/macOS Setup Script (`setup.sh`):**
  - Updated `repo="Sarveshsivasankaran/PhoneMon"`.
  - Updated curl one-liner install commands (`https://raw.githubusercontent.com/Sarveshsivasankaran/PhoneMon/main/setup.sh`).
- [x] **Update Scoop Bucket Manifest (`bucket/phonebox.json`):**
  - Updated `"homepage"` to `https://github.com/Sarveshsivasankaran/PhoneMon`.
  - Updated architecture 64-bit download URL and autoupdate URL to point to `Sarveshsivasankaran/PhoneMon/releases/download/...`.
- [x] **Update WinGet Manifest Generator (`packaging/winget/make-manifests.py`):**
  - Updated `PublisherUrl`, `PackageUrl`, `LicenseUrl`, and `ReleaseNotesUrl` to point to `Sarveshsivasankaran/PhoneMon`.
- [x] **Update Release Dispatch Script (`scripts/release.ps1`):**
  - Updated `gh workflow run release.yml --repo Sarveshsivasankaran/PhoneMon`.

---

### Phase 3: Web Demo, Documentation & GitHub Metadata
- [x] **Update Web Demo Engine (`tools/pages/demo.js`):**
  - Updated demo update repo to `Sarveshsivasankaran/PhoneMon`.
  - Updated demo pairing URL to `sarveshsivasankaran.github.io/PhoneMon/pair/`.
  - Updated demo release asset links to `Sarveshsivasankaran/PhoneMon/releases/latest`.
- [x] **Update Web Demo Landing Page (`tools/pages/index.html`):**
  - Updated GitHub project button, install scripts, and footer links.
  - Updated committer/reviewer team roles to `Sarveshsivasankaran`.
- [x] **Update Web Pairing Page (`tools/pages/pair.html`):**
  - Updated app download button and privacy policy links.
- [x] **Update GitHub Workflows (`.github/workflows/release.yml`):**
  - Updated release note footer URLs and Scoop autoupdate URLs.
- [x] **Update GitHub Issue Templates & Funding:**
  - Updated `.github/FUNDING.yml` to `github: Sarveshsivasankaran`.
  - Updated `.github/ISSUE_TEMPLATE/config.yml` security advisory and demo links.
- [x] **Update Main README (`README.md`):**
  - Updated live demo link, download button, workflow badges, one-liner install commands, Scoop instructions, and committer role.
- [x] **Update All Localized READMEs (`docs/README.*.md`):**
  - Replaced all raw script URLs, GitHub release URLs, and live demo links across 23 languages.
- [x] **Update Installation & Security Guides (`docs/SETUP.md`, `SECURITY.md`, `PRIVACY.md`):**
  - Updated setup guide links, private security advisory endpoint, and user agent notices.

---

### Phase 4: Verification & Git Remote Sync
- [x] **Release Metadata Validation:** Ran `python tools/check-release.py` -> Passed all widget counts, catalog schemas, and language consistency checks.
- [x] **Release Version Reset (v1.0.0):** Reset project version from legacy `0.9.27` to starting `1.0.0` across C# Agent, Android build (`versionCode = 1`, `versionName = "1.0.0"`), CHANGELOG, Scoop bucket, and WinGet manifest package (`Sarveshsivasankaran.PhoneMon`).
- [x] **WinGet Manifest Test Fix:** Updated `tools/test-winget-manifests.py` and `packaging/winget/make-manifests.py` to match the target repository and package identifier -> Passed in CI Release metadata job.
- [x] **Git Remote URL Configuration:** Set `origin` remote URL to `https://github.com/Sarveshsivasankaran/PhoneMon.git`.
- [x] **Initial Remote Reachability:** Verified remote target connectivity via `git ls-remote`.
- [x] **Commit and Push:** Stage all modifications and push branch to `origin main`.

---

## 2. CI/CD Release Signing Secrets (Generated & Ready)

The following GitHub Repository Secrets have been generated and validated. Add them in GitHub under **Settings › Secrets and variables › Actions › Repository secrets**:

1. **`PHONEBOX_KEYSTORE_BASE64`**: Base64 encoded string of `release.keystore` (2048-bit RSA, valid for 10,000 days).
2. **`PHONEBOX_STORE_PASSWORD`**: Store password for the keystore.
3. **`PHONEBOX_KEY_ALIAS`**: Keystore key alias (`phonebox`).
4. **`PHONEBOX_KEY_PASSWORD`**: Key password for the keystore.
5. **`MACOS_SIGNING_P12_BASE64`**: Base64 PKCS#12 bundle generated via `tools/make-macos-signing-cert.sh` (3072-bit RSA, valid for 30 years).
6. **`MACOS_SIGNING_P12_PASSWORD`**: Decryption password for the macOS P12 certificate.

