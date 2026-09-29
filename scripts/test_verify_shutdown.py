"""Regression checks for shutdown fixture environment, diagnostics, and cleanup."""

import argparse
import importlib.util
import os
import pathlib
import socket
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "shutdown_verifier", pathlib.Path(__file__).with_name("verify-shutdown.py"))
shutdown = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shutdown)

spec = importlib.util.spec_from_file_location(
    "production_verifier", pathlib.Path(__file__).with_name("verify-production.py"))
production = importlib.util.module_from_spec(spec)
# The production verifier changes localhost resolution for its HTTPS fixture.
with patch.object(socket, "getaddrinfo", socket.getaddrinfo):
    spec.loader.exec_module(production)


class ProductionHandoffTest(unittest.TestCase):
    def test_shutdown_subprocess_uses_fixture_environment(self):
        key = "LOOMSPAN_SIDECAR_CREDENTIAL_KEY"
        environment = {key: "fixture-only-key", "PATH": "fixture-path"}
        with patch.dict(os.environ, {key: "different-parent-key"}), \
                patch.object(production, "port", side_effect=[28080, 28081, 29091]), \
                patch.object(production.image, "run") as run:
            production.verify_shutdown("fixture", pathlib.Path("stopped-backup"), environment)
        command = run.call_args.args
        self.assertEqual(pathlib.Path(command[1]).name, "verify-shutdown.py")
        self.assertEqual(command[2:6], ("--image", "fixture", "--database-dir", "stopped-backup"))
        self.assertIs(run.call_args.kwargs.get("env"), environment)
        self.assertNotIn(environment[key], command)


class StartupFailureTest(unittest.TestCase):
    def test_startup_failure_retains_diagnostics_and_cleans_up(self):
        for cutoff, logs_timeout in ((False, False), (False, True), (True, False), (True, True)):
            with self.subTest(cutoff=cutoff, logs_timeout=logs_timeout), tempfile.TemporaryDirectory() as directory:
                pathlib.Path(directory, "sidecar.db").write_bytes(b"fixture")
                args = argparse.Namespace(image="fixture", database_dir=directory,
                                          api_port=28080, host_port=28081,
                                          management_port=29091, validate_only=False)
                calls = []
                key = "LOOMSPAN_SIDECAR_CREDENTIAL_KEY"

                def record(command, kwargs):
                    calls.append(command)
                    self.assertEqual(kwargs["env"][key], "fixture-only-key")
                    self.assertNotIn("fixture-only-key", command)

                def run(*command, **kwargs):
                    record(command, kwargs)
                    if "logs" in command and logs_timeout:
                        raise subprocess.TimeoutExpired(command, 15)

                failure = AssertionError("readiness failed before container lookup")
                with patch.dict(os.environ, {key: "fixture-only-key"}), \
                        patch.object(shutdown.image, "run", side_effect=run), \
                        patch.object(shutdown.image, "wait_for", side_effect=failure), \
                        patch.object(shutdown.image, "require_cleanup",
                                     side_effect=lambda *cmd, **kw: record(cmd, kw)):
                    with self.assertRaises(AssertionError) as raised:
                        shutdown.verify(args, cutoff=cutoff)
                self.assertIs(raised.exception, failure)
                self.assertIn("logs", calls[-2])
                self.assertEqual(calls[-1][-3:], ("down", "--volumes", "--remove-orphans"))
                self.assertEqual(pathlib.Path(directory, "sidecar.db").read_bytes(), b"fixture")


if __name__ == "__main__":
    unittest.main()
