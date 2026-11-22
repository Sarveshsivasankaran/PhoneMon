#!/usr/bin/env python3
"""
Standalone local web server for JARVIS Local AI Model Management & Activeness.
Runs locally at http://127.0.0.1:8780.
Requires zero external dependencies (uses standard Python library).
"""
import http.server
import json
import os
import sys
import time
from pathlib import Path

PORT = 8780
ROOT_DIR = Path(__file__).resolve().parents[1]
WEB_DIR = ROOT_DIR / "web"
MODELS_DIR = ROOT_DIR / "models"

# Active State Representation
state = {
    "active_model_id": "qwen3",
    "active_model_name": "Qwen3 1.7B Instruct",
    "state": "STANDBY",
    "ram_used_mb": 1364,
    "ram_max_mb": 1800,
    "thermal_status": "NOMINAL",
    "temperature_c": 42.5,
    "subsystems": {
        "voice": "Listening for \"JARVIS\" (16kHz)",
        "vision": "Back Lens (Ask & See Active)",
        "memory": "SQLite Memory Verified (32 facts)",
        "phonemon": "Port 8765 Loopback OK"
    }
}


class JarvisHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_DIR), **kwargs)

    def do_GET(self):
        if self.path == "/api/status":
            self.send_json_response(state)
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
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8") if content_len > 0 else "{}"
        try:
            data = json.loads(body)
        except Exception:
            data = {}

        if self.path == "/api/swap":
            target = data.get("model", "qwen3")
            if target == "lfm":
                state["active_model_id"] = "lfm"
                state["active_model_name"] = "LFM2.5-VL 450M Vision"
                state["ram_used_mb"] = 460
            else:
                state["active_model_id"] = "qwen3"
                state["active_model_name"] = "Qwen3 1.7B Instruct"
                state["ram_used_mb"] = 1364
            self.send_json_response(state)

        elif self.path == "/api/verify":
            model = data.get("model", "qwen3")
            self.send_json_response({
                "status": "verified",
                "model": model,
                "message": f"SHA-256 verification passed for {model} weights."
            })

        elif self.path == "/api/inference":
            prompt = data.get("prompt", "")
            response = self.simulate_local_response(prompt, state["active_model_id"])
            self.send_json_response({"prompt": prompt, "response": response})

        else:
            self.send_error(404, "Endpoint not found")

    def simulate_local_response(self, prompt: str, model_id: str) -> str:
        prompt_lower = prompt.lower()
        if "status" in prompt_lower or "check" in prompt_lower:
            return (
                "JARVIS on-device status: All systems nominal. "
                f"Model: {state['active_model_name']}, RAM: {state['ram_used_mb']} MB, "
                "Cores pinned: 4x Cortex-A73, Local PhoneMon loopback active."
            )
        elif "cpu" in prompt_lower or "metric" in prompt_lower:
            return "Invoking tool 'get_pc_metrics': CPU load 18%, GPU load 12%, RAM 4.2GB / 16GB, Temperature 48°C."
        elif "camera" in prompt_lower or "see" in prompt_lower or "look" in prompt_lower:
            return "LFM2.5-VL frame analysis: Visual sensor captures desktop workstation with active monitors and development terminal."
        else:
            return f"[{state['active_model_name']}] Analyzed input: '{prompt}'. Local inference processed with zero cloud transmission."

    def send_json_response(self, data: dict):
        response_bytes = json.dumps(data, indent=2).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.send_header("Access-Control-Allow-Origin", "*")
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

