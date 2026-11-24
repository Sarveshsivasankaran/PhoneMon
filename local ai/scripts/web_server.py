#!/usr/bin/env python3
"""Redirect the retired JARVIS dashboard to the desktop agent's settings."""

from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlsplit


class JarvisHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        routes = {
            "/": "/#ai",
            "/index.html": "/#ai",
            "/api/status": "/api/jarvis/session",
            "/api/profile": "/api/jarvis/profile",
        }
        target = routes.get(urlsplit(self.path).path)
        if target is None:
            self.send_error(404)
            return
        self.send_response(302)
        self.send_header("Location", "http://127.0.0.1:8765" + target)
        self.send_header("Content-Length", "0")
        self.end_headers()


if __name__ == "__main__":
    with HTTPServer(("127.0.0.1", 8780), JarvisHandler) as server:
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
