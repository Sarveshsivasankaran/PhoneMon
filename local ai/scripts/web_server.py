#!/usr/bin/env python3
"""
Standalone local web server for JARVIS Local AI Model Management & Activeness.
Runs locally at http://127.0.0.1:8780.
Requires zero external dependencies (uses standard Python library).
"""
import http.server
import urllib.request
import json
import os
import sys
import time
import threading
from pathlib import Path

PORT = 8780
ROOT_DIR = Path(__file__).resolve().parents[1]
WEB_DIR = ROOT_DIR / "web"
MODELS_DIR = ROOT_DIR / "models"
if sys.platform == "win32":
    config_root = Path(os.environ["APPDATA"])
elif sys.platform == "darwin":
    config_root = Path.home() / "Library/Application Support"
else:
    config_root = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
profile_dir = config_root / "PhoneMon"
if not profile_dir.exists() and (config_root / "PhoneBox").exists():
    profile_dir = config_root / "PhoneBox"
PROFILE_PATH = profile_dir / "jarvis-profile.json"
PROFILE_LOCK = threading.Lock()
DEFAULT_PROFILE = {"assistantName": "JARVIS", "personality": "Be concise, helpful, and operate the PhoneMon desk panel using its available controls.", "model": "qwen2.5-0.5b"}


def load_profile():
    try:
        profile = json.loads(PROFILE_PATH.read_text(encoding="utf-8"))
        return validate_profile(profile) or DEFAULT_PROFILE.copy()
    except (OSError, json.JSONDecodeError):
        return DEFAULT_PROFILE.copy()


def validate_profile(profile):
    if not isinstance(profile, dict):
        return None
    name = profile.get("assistantName")
    personality = profile.get("personality")
    model = profile.get("model")
    if not isinstance(name, str) or not name.strip() or len(name) > 40:
        return None
    if not isinstance(personality, str) or len(personality) > 2000:
        return None
    if model not in {"qwen2.5-0.5b", "lfm2.5-vl-450m"}:
        return None
    return {"assistantName": name.strip(), "personality": personality.strip(), "model": model}


def save_profile(profile):
    with PROFILE_LOCK:
        PROFILE_PATH.parent.mkdir(parents=True, exist_ok=True)
        temporary = PROFILE_PATH.with_suffix(".tmp")
        temporary.write_text(json.dumps(profile, indent=2), encoding="utf-8")
        temporary.replace(PROFILE_PATH)


class JarvisHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_DIR), **kwargs)

    def do_GET(self):
        if self.path == "/api/profile":
            self.send_json_response(load_profile())
            return
        if self.path == "/api/status":
            try:
                with urllib.request.urlopen("http://127.0.0.1:8765/api/jarvis/session", timeout=1) as reply:
                    session = json.load(reply)
            except (OSError, ValueError):
                session = {"state": "OFFLINE"}
            self.send_json_response(session)
            return
        elif self.path == "/api/ping":
            self.send_json_response({"status": "ok", "timestamp": int(time.time() * 1000)})
        elif self.path == "/api/models":
            config_path = MODELS_DIR / "model-config.json"
            if config_path.exists():
                self.send_json_response(json.loads(config_path.read_text(encoding="utf-8")))
            else:
                self.send_json_response({"models": {}})
        else:
            super().do_GET()

    def do_POST(self):
        if self.headers.get("Host") not in {f"localhost:{PORT}", f"127.0.0.1:{PORT}"} or self.headers.get("Origin") not in {None, f"http://localhost:{PORT}", f"http://127.0.0.1:{PORT}"} or self.headers.get_content_type() != "application/json":
            self.send_error(403)
            return
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8") if content_len > 0 else "{}"
        try:
            data = json.loads(body)
        except Exception:
            data = {}

        if self.path == "/api/profile":
            profile = validate_profile(data)
            if profile is None:
                self.send_error(400, "Invalid profile")
                return
            save_profile(profile)
            self.send_json_response(profile)
        elif self.path in {"/api/swap", "/api/verify"}:
            self.send_error(503, "Model management runs in the paired phone app")

        elif self.path == "/api/inference":
            self.send_error(503, "Inference runs in the paired phone app")

        else:
            self.send_error(404, "Endpoint not found")

    def send_json_response(self, data: dict):
        response_bytes = json.dumps(data, indent=2).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(response_bytes)

    def log_message(self, format, *args):
        # Quiet standard logging to keep output clean
        pass


def main():
    server = http.server.HTTPServer(("127.0.0.1", PORT), JarvisHandler)
    print(f"==> JARVIS Local AI Console running at http://127.0.0.1:{PORT}")
    print("==> Serving model management & activeness dashboard from:", WEB_DIR)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n==> Stopping JARVIS Local AI Console.")
        server.server_close()


if __name__ == "__main__":
    main()

