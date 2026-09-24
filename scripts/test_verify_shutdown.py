"""Regression checks for failure diagnostics in the shutdown verifier."""

import argparse
import importlib.util
import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "shutdown_verifier", pathlib.Path(__file__).with_name("verify-shutdown.py"))
shutdown = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shutdown)


class StartupFailureTest(unittest.TestCase):
    def test_startup_failure_retains_diagnostics_and_cleans_up(self):
        for logs_timeout in (False, True):
            with self.subTest(logs_timeout=logs_timeout), tempfile.TemporaryDirectory() as directory:
                pathlib.Path(directory, "sidecar.db").write_bytes(b"fixture")
                args = argparse.Namespace(image="fixture", database_dir=directory,
                                          api_port=28080, host_port=28081,
                                          management_port=29091, validate_only=False)
                calls = []

                def run(*command, **kwargs):
                    calls.append(command)
                    if "logs" in command and logs_timeout:
                        raise subprocess.TimeoutExpired(command, 15)

                failure = AssertionError("readiness failed before container lookup")
                with patch.object(shutdown.image, "run", side_effect=run), \
                        patch.object(shutdown.image, "wait_for", side_effect=failure), \
                        patch.object(shutdown.image, "require_cleanup",
                                     side_effect=lambda *cmd, **kw: calls.append(cmd)):
                    with self.assertRaises(AssertionError) as raised:
                        shutdown.verify(args, cutoff=False)
                self.assertIs(raised.exception, failure)
                self.assertIn("logs", calls[-2])
                self.assertEqual(calls[-1][-3:], ("down", "--volumes", "--remove-orphans"))
                self.assertEqual(pathlib.Path(directory, "sidecar.db").read_bytes(), b"fixture")


if __name__ == "__main__":
    unittest.main()
