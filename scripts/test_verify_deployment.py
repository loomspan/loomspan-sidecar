"""Failure-sensitive orchestration checks for the bounded deployment smoke."""

import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "deployment", Path(__file__).with_name("verify-deployment.py")
)
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)


class FakeRuntime:
    def __init__(self, phase=None, cleanup=False, diagnostics=False):
        self.phase = phase
        self.fail_cleanup = cleanup
        self.fail_diagnostics = diagnostics
        self.calls = []

    def action(self, name):
        self.calls.append(name)
        if self.phase == name:
            raise deployment.Failure("primary-failure")

    def inspect(self):
        self.action("inspect")

    def start(self):
        self.action("start")

    def assertions(self):
        self.action("assertions")

    def diagnostics(self):
        self.calls.append("diagnostics")
        if self.fail_diagnostics:
            raise RuntimeError("PRIVATE secret payload")
        return {"stateCollected": True}

    def cleanup(self):
        self.calls.append("cleanup")
        if self.fail_cleanup:
            raise deployment.Failure("cleanup-failed")


class OrchestrationTest(unittest.TestCase):
    def test_login_requires_a_secure_session_cookie(self):
        for header in (
            None,
            "JSESSIONID=fixture; HttpOnly",
            "OTHER=fixture; Secure",
            "JSESSIONID=fixture; Secure; HttpOnly",
        ):
            with self.subTest(header=header):
                runtime = deployment.Runtime.__new__(deployment.Runtime)
                runtime.token = "fixture-token"
                runtime.compose = ["docker", "compose"]
                runtime.port = 8443
                runtime.project = "unique"
                runtime.tls = Path("/temporary/tls")

                def request(path, token=None):
                    if path.startswith("/actuator/health/"):
                        return 200, b'{"status":"UP"}', None
                    if path == "/management/login":
                        return 200, b"", header
                    return (200, b"[]", None) if token else (401, b"", None)

                with patch.object(runtime, "request", side_effect=request), patch.object(
                    deployment, "command", return_value=""
                ):
                    if header == "JSESSIONID=fixture; Secure; HttpOnly":
                        runtime.assertions()
                    else:
                        with self.assertRaisesRegex(
                            deployment.Failure, "secure-session-cookie"
                        ):
                            runtime.assertions()

    def test_success_and_all_primary_failures_cleanup_even_without_diagnostics(self):
        for phase in (None, "inspect", "start", "assertions"):
            for diagnostics in (False, True):
                with self.subTest(phase=phase, diagnostics=diagnostics):
                    runtime = FakeRuntime(phase, diagnostics=diagnostics)
                    report = deployment.verify(runtime)
                    self.assertEqual(
                        report["status"], "passed" if phase is None else "failed"
                    )
                    self.assertEqual(report["cleanup"], "passed")
                    self.assertEqual(runtime.calls[-2:], ["diagnostics", "cleanup"])
                    self.assertNotIn("PRIVATE", json.dumps(report))

    def test_interruption_during_diagnostics_cannot_skip_cleanup(self):
        runtime = FakeRuntime()
        with patch.object(runtime, "diagnostics", side_effect=KeyboardInterrupt()):
            report = deployment.verify(runtime)
        self.assertEqual(runtime.calls[-1], "cleanup")
        self.assertEqual(report["status"], "failed")
        self.assertEqual(report["diagnostic"], "interrupted")

    def test_cleanup_failure_fails_success_preserves_primary(self):
        for phase in (None, "start"):
            report = deployment.verify(FakeRuntime(phase, cleanup=True))
            self.assertEqual(report["status"], "failed")
            self.assertEqual(
                report["diagnostic"], "primary-failure" if phase else "cleanup-failed"
            )

    def test_timeout_and_interrupt_cleanup(self):
        for failure in (deployment.Failure("command-timeout"), KeyboardInterrupt()):
            runtime = FakeRuntime()
            with patch.object(runtime, "start", side_effect=failure):
                report = deployment.verify(runtime)
            self.assertEqual(report["status"], "failed")
            self.assertEqual(runtime.calls[-1], "cleanup")

    def test_every_cleanup_operation_attempted(self):
        runtime = deployment.Runtime.__new__(deployment.Runtime)
        runtime.compose = ["docker", "compose"]
        runtime.project = "unique"
        calls = []

        def command(args, **kwargs):
            calls.append(args)
            if len(calls) == 1:
                raise deployment.Failure("command-timeout")
            return ""

        with patch.object(deployment, "command", side_effect=command):
            with self.assertRaisesRegex(deployment.Failure, "cleanup-failed"):
                runtime.cleanup()
        self.assertEqual(len(calls), 4)
        self.assertTrue(all("unique" in " ".join(call) for call in calls[1:]))

    def test_fallback_cleanup_continues_after_a_container_removal_failure(self):
        runtime = deployment.Runtime.__new__(deployment.Runtime)
        runtime.compose = ["docker", "compose"]
        runtime.project = "unique"
        calls = []

        def command(args, **kwargs):
            calls.append(args)
            if args[:3] == ["docker", "container", "ls"]:
                return "owned-container"
            if args[:3] == ["docker", "container", "rm"]:
                raise deployment.Failure("command-failed")
            return ""

        with patch.object(deployment, "command", side_effect=command):
            with self.assertRaisesRegex(deployment.Failure, "cleanup-failed"):
                runtime.cleanup()
        self.assertEqual(calls[-2][1], "network")
        self.assertEqual(calls[-1][1], "volume")
        self.assertIn(["docker", "container", "rm", "-f", "owned-container"], calls)

    def test_command_filters_environment_and_raw_diagnostics(self):
        for failure in ("timeout", "os", "exit"):

            def run(args, **kwargs):
                self.assertNotIn("API_KEY", kwargs["env"])
                self.assertNotIn("JAVA_TOOL_OPTIONS", kwargs["env"])
                self.assertEqual(kwargs["timeout"], 60)
                if failure == "timeout":
                    raise subprocess.TimeoutExpired(args, 60, stderr="PRIVATE payload")
                if failure == "os":
                    raise OSError("PRIVATE payload")
                return subprocess.CompletedProcess(
                    args, 1, "PRIVATE token", "PRIVATE key"
                )

            with (
                patch.dict(
                    deployment.os.environ,
                    {"API_KEY": "secret", "JAVA_TOOL_OPTIONS": "secret"},
                ),
                patch.object(deployment.subprocess, "run", side_effect=run),
            ):
                with self.assertRaises(deployment.Failure) as error:
                    deployment.command(["test"])
                self.assertNotIn("PRIVATE", str(error.exception))


if __name__ == "__main__":
    unittest.main()
