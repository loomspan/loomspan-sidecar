import contextlib
import io
import json
import runpy
import sys
import unittest
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "examples/quickstart/invoke.py"
EXECUTION_ID = "6a0c950c-d172-4151-b629-6a5c15c7ae37"


class QuickstartTest(unittest.TestCase):
    def test_invokes_the_default_planner_and_polls_its_accepted_id(self):
        catalog_path = "http://localhost:8080/v1/namespaces/default/skills"
        skill_path = catalog_path + "/quickstart-planner"
        poll_path = "http://localhost:8080/v1/executions/" + EXECUTION_ID
        planner = {"namespace": "default", "localName": "quickstart-planner"}
        business_input = '{ "message": "Check the local callbacks — 雪" }\n'.encode()
        responses = [
            ("GET", "http://localhost:8081/token", 200, {"access_token": "fixture-token"}),
            ("GET", catalog_path, 200, [
                {"namespace": "another-owner", "localName": "quickstart-planner"},
                {"namespace": "default", "localName": "identity-leaf"},
                planner,
            ]),
            ("GET", skill_path, 200, planner),
            ("POST", skill_path + "/executions", 202, {"id": EXECUTION_ID}),
            ("GET", poll_path, 200, planner | {"status": "RUNNING"}),
            ("GET", poll_path, 200, planner | {"status": "COMPLETED", "result": "quickstart complete"}),
        ]
        calls = iter(responses)

        def open_fixture(request, *, timeout):
            method, url, status, body = next(calls)
            if isinstance(request, str):
                self.assertEqual((method, request), ("GET", url))
            else:
                self.assertEqual((request.get_method(), request.full_url), (method, url))
                self.assertEqual(request.get_header("Authorization"), "Bearer fixture-token")
                if method == "POST":
                    self.assertEqual(request.data, business_input)
            response = io.BytesIO(json.dumps(body).encode())
            response.status = status
            response.headers = {"Location": "http://localhost:1/ignored"}
            return response

        output = io.StringIO()
        with (
            patch("urllib.request.OpenerDirector.open", side_effect=open_fixture) as wire,
            patch("time.sleep"),
            patch.object(sys, "argv", [str(SCRIPT), "default", "quickstart-planner"]),
            patch.object(sys, "stdin", io.TextIOWrapper(io.BytesIO(business_input), encoding="cp1252")),
            contextlib.redirect_stdout(output),
        ):
            runpy.run_path(str(SCRIPT), run_name="__main__")

        self.assertEqual(wire.call_count, len(responses))
        self.assertEqual(output.getvalue().splitlines(), [
            "Accepted execution: " + EXECUTION_ID, "quickstart complete",
        ])

    def test_discovery_does_not_follow_a_redirect_with_the_fixture_token(self):
        requests = []

        class Fixture(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append((self.path, self.headers.get("Authorization")))
                if self.path == "/token":
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b'{"access_token":"fixture-token"}')
                elif self.path == "/v1/namespaces/default/skills":
                    self.send_response(302)
                    self.send_header("Location", f"http://localhost:{self.server.server_port}/redirect")
                    self.end_headers()
                else:
                    self.send_response(404)
                    self.end_headers()

            def log_message(self, *_):
                pass

        run = runpy.run_path(str(SCRIPT))["run"]
        with ThreadingHTTPServer(("127.0.0.1", 0), Fixture) as peer:
            origin = f"http://127.0.0.1:{peer.server_port}"
            serving = Thread(target=peer.serve_forever)
            serving.start()
            try:
                with self.assertRaises(urllib.error.HTTPError) as raised:
                    run("default", "quickstart-planner", b"{}", origin, origin)
            finally:
                peer.shutdown()
                serving.join()

        self.assertEqual(raised.exception.code, 302)
        self.assertEqual(requests, [
            ("/token", None), ("/v1/namespaces/default/skills", "Bearer fixture-token"),
        ])
