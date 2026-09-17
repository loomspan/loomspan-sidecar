"""Verification-only gated version of the quickstart callback/model host."""

import json
import threading
from http.server import ThreadingHTTPServer

import host

ENTERED = threading.Event()
RELEASE = threading.Event()
FINISHED = threading.Event()
DETAIL = threading.Event()


class Handler(host.Handler):
    def do_GET(self):
        if self.path == "/verification/status":
            self.send_json(200, {"entered": ENTERED.is_set(), "detail": DETAIL.is_set(),
                                 "finished": FINISHED.is_set()})
        else:
            super().do_GET()

    def do_POST(self):
        if self.path == "/verification/release":
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            RELEASE.set()
            self.send_json(200, {})
            return
        if self.path == "/callbacks/identity":
            ENTERED.set()
            if not RELEASE.wait(60):
                self.send_json(504, {"error": "verification gate timed out"})
                return
        super().do_POST()

    def send_json(self, status, value):
        super().send_json(status, value)
        if status == 200 and self.path == "/callbacks/detail":
            DETAIL.set()
        if status == 200 and self.path == "/v1/chat/completions":
            content = json.loads(value["choices"][0]["message"]["content"])
            if content.get("stepAction") == "FINAL_RESPONSE":
                FINISHED.set()


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8081), Handler).serve_forever()
