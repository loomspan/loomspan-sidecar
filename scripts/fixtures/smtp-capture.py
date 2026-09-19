#!/usr/bin/env python3
"""Isolated SMTP sink for the production Compose verifier only."""

import json
import socketserver
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAIL = []
LOCK = threading.Lock()


class Smtp(socketserver.StreamRequestHandler):
    def handle(self):
        self.wfile.write(b"220 fixture ESMTP\r\n")
        data = False
        lines = []
        while line := self.rfile.readline():
            value = line.decode(errors="replace").rstrip("\r\n")
            command = value.upper()
            if data:
                if value == ".":
                    with LOCK:
                        MAIL.append("\n".join(lines))
                    data = False
                    lines = []
                    self.wfile.write(b"250 queued\r\n")
                else:
                    lines.append(value[1:] if value.startswith("..") else value)
            elif command.startswith("EHLO"):
                self.wfile.write(b"250-fixture\r\n250 8BITMIME\r\n")
            elif command.startswith("HELO") or command.startswith("MAIL FROM:") or command.startswith("RCPT TO:"):
                self.wfile.write(b"250 ok\r\n")
            elif command == "DATA":
                data = True
                self.wfile.write(b"354 end with dot\r\n")
            elif command == "QUIT":
                self.wfile.write(b"221 bye\r\n")
                return
            else:
                self.wfile.write(b"250 ok\r\n")
            self.wfile.flush()


class Http(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/messages":
            self.send_error(404)
            return
        with LOCK:
            body = json.dumps(MAIL).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    threading.Thread(target=lambda: socketserver.ThreadingTCPServer(("0.0.0.0", 2525), Smtp).serve_forever(),
                     daemon=True).start()
    ThreadingHTTPServer(("0.0.0.0", 8025), Http).serve_forever()
