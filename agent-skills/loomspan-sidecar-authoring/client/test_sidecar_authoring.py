"""Black-box checks for the portable management client."""

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest


SCRIPT = Path(__file__).with_name("sidecar_authoring.py")
TOKEN = "fixture-secret-management-token-123"


class Handler(BaseHTTPRequestHandler):
    requests = []
    replies = []

    def do_GET(self):
        self.reply()

    def do_POST(self):
        self.reply()

    def do_PUT(self):
        self.reply()

    def reply(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.requests.append((self.command, self.path, dict(self.headers), body))
        status, payload = self.replies.pop(0) if self.replies else (200, {})
        encoded = payload if isinstance(payload, bytes) else json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/zip" if isinstance(payload, bytes) else "application/json")
        if isinstance(payload, dict) and "_location" in payload:
            self.send_header("Location", payload["_location"])
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *_args):
        pass


class ClientTest(unittest.TestCase):
    def setUp(self):
        Handler.requests = []
        Handler.replies = []
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def run_client(self, *args, input=None, token=TOKEN):
        env = dict(os.environ)
        if token is None:
            env.pop("LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN", None)
        else:
            env["LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN"] = token
        result = subprocess.run([sys.executable, str(SCRIPT), "--url", self.url, *args],
                                input=input, text=True, capture_output=True, env=env)
        self.assertNotIn(TOKEN, result.stdout + result.stderr)
        return result

    def test_readsCurrentWithEnvironmentTokenAndNoArgumentSecret(self):
        Handler.replies = [(200, {"publishedId": "p1"})]
        result = self.run_client("current")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["publishedId"], "p1")
        self.assertEqual([(x[0], x[1]) for x in Handler.requests],
                         [("GET", "/api/management/configuration/current")])
        self.assertEqual(Handler.requests[0][2]["Authorization"], "Bearer " + TOKEN)

    def test_handoff_rereads_current_and_draft(self):
        Handler.replies = [(200, {"generation": "new"}), (200, {"publishedId": "p2"}),
                           (200, {"revision": 5})]
        result = self.run_client("handoff", "--label", "fixture agent")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([x[:2] for x in Handler.requests], [
            ("POST", "/api/management/editing/lease/handoff"),
            ("GET", "/api/management/configuration/current"),
            ("GET", "/api/management/editing/draft")])
        self.assertEqual(json.loads(result.stdout)["draft"]["revision"], 5)

    def test_conflicts_are_actionable_and_not_retried(self):
        Handler.replies = [(409, {"code": "revision_conflict", "error": TOKEN})]
        result = self.run_client("save", input='{"revision":1}')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("revision_conflict", result.stderr)
        self.assertIn("current", result.stderr)
        self.assertEqual(len(Handler.requests), 1)

    def test_missing_token_and_secret_echo(self):
        self.assertNotEqual(self.run_client("current", token=None).returncode, 0)
        self.assertEqual(Handler.requests, [])
        Handler.replies = [(503, {"code": "publication_failure", "error": TOKEN,
                                  "publishedId": "00000000-0000-0000-0000-000000000001"})]
        result = self.run_client("publish", input="{}")
        self.assertIn("inspect", result.stderr.lower())
        self.assertEqual(len(Handler.requests), 1)

    def test_bundle_multipart_and_export(self):
        with tempfile.TemporaryDirectory() as folder:
            bundle = Path(folder) / "bundle.zip"
            bundle.write_bytes(b"PK\x03\x04fixture")
            Handler.replies = [(200, {"successful": True})]
            result = self.run_client("import-review", "--bundle", str(bundle))
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(b'name="bundle"', Handler.requests[0][3])
            self.assertIn(bundle.read_bytes(), Handler.requests[0][3])
            Handler.replies = [(200, b"PK\x03\x04returned")]
            target = Path(folder) / "export.zip"
            result = self.run_client("export", "--output", str(target))
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(target.read_bytes(), b"PK\x03\x04returned")
            large_bundle = b"PK\x03\x04" + b"x" * (17 * 1_048_576)
            Handler.replies = [(200, large_bundle)]
            result = self.run_client("export", "--output", str(target))
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(target.read_bytes(), large_bundle)

    def test_shared_routes_and_complete_bodies(self):
        cases = [
            ("history", "GET", "/configuration/history", None),
            ("draft", "GET", "/editing/draft", None),
            ("lease-status", "GET", "/editing", None),
            ("acquire", "POST", "/editing/lease", None),
            ("renew", "POST", "/editing/lease/renew", '{"editingSessionId":"one","generation":"two"}'),
            ("release", "POST", "/editing/lease/release", '{"editingSessionId":"one","generation":"two"}'),
            ("save", "PUT", "/editing/draft", '{"revision":3,"skillDocuments":[],"restRoutesYaml":"targets: {}"}'),
            ("reconcile", "POST", "/editing/draft/reconcile", '{"revision":3,"skillDocuments":[],"restRoutesYaml":"targets: {}"}'),
            ("validate", "POST", "/editing/draft/validate", '{"revision":3}'),
            ("publish", "POST", "/configuration/publish", '{"revision":3}'),
        ]
        for name, method, path, payload in cases:
            with self.subTest(name=name):
                Handler.requests = []
                Handler.replies = [(200, {"ok": True})]
                options = ("--label", "Agent") if name == "acquire" else ()
                result = self.run_client(name, *options, input=payload)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(Handler.requests[0][:2], (method, "/api/management" + path))
                if payload is not None:
                    self.assertEqual(json.loads(Handler.requests[0][3]), json.loads(payload))
                self.assertEqual(len(Handler.requests), 1)

    def test_import_load_fields_and_permissions(self):
        fields = {"editingSessionId": "s", "generation": "g", "draftId": "d",
                  "revision": 4, "baseSnapshotId": "b"}
        with tempfile.TemporaryDirectory() as folder:
            bundle = Path(folder) / "bundle.zip"
            bundle.write_bytes(b"PK\x03\x04fixture")
            Handler.replies = [(200, {"revision": 5})]
            result = self.run_client("import-load", "--bundle", str(bundle), input=json.dumps(fields))
            self.assertEqual(result.returncode, 0, result.stderr)
            body = Handler.requests[0][3]
            for name in fields:
                self.assertIn(f'name="{name}"'.encode(), body)
            self.assertEqual(len(Handler.requests), 1)
            Handler.replies = [(403, {"code": "role_conflict"})]
            result = self.run_client("acquire", "--label", "Agent")
            self.assertIn("permission", result.stderr.lower())
            self.assertEqual(len(Handler.requests), 2)

    def test_rejects_non_loopback_http_and_malformed_json(self):
        env = dict(os.environ)
        env["LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN"] = TOKEN
        result = subprocess.run([sys.executable, str(SCRIPT), "--url", "http://sidecar.example.test", "current"],
                                text=True, capture_output=True, env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(TOKEN, result.stderr)
        result = self.run_client("save", input="not json")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(Handler.requests, [])

    def test_redirect_never_forwards_management_token(self):
        Handler.replies = [(302, {"_location": self.url + "/unexpected"})]
        result = self.run_client("current")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Redirect refused", result.stderr)
        self.assertEqual(len(Handler.requests), 1)


if __name__ == "__main__":
    unittest.main()
