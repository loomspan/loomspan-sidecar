#!/usr/bin/env python3
"""Verify nested work across SIGTERM using a stopped, prepopulated test database."""

import argparse
import importlib.util
import json
import os
import pathlib
import shutil
import tempfile
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("image_verifier", ROOT / "scripts/verify-image.py")
image = importlib.util.module_from_spec(spec)
spec.loader.exec_module(image)


def wait_state(url, predicate, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        state = json.loads(image.request(url + "/verification/status")[1])
        if predicate(state):
            return state
        time.sleep(.05)
    raise AssertionError(f"Callback state did not reach expected condition: {state}")


def verify(args, cutoff):
    mode = "cutoff" if cutoff else "completion"
    project = f"loomspan-shutdown-{os.getpid()}-{mode}"
    environment = dict(os.environ, SIDECAR_IMAGE=args.image,
                       SIDECAR_API_PORT=str(args.api_port),
                       QUICKSTART_HOST_PORT=str(args.host_port),
                       SIDECAR_MANAGEMENT_PORT=str(args.management_port))
    host_url = f"http://127.0.0.1:{args.host_port}"
    api_url = f"http://127.0.0.1:{args.api_port}"
    management_url = f"http://127.0.0.1:{args.management_port}"
    with tempfile.TemporaryDirectory(prefix="loomspan-shutdown-") as directory:
        root = pathlib.Path(directory)
        data = root / "data"
        data.mkdir()
        if not args.validate_only:
            source = pathlib.Path(args.database_dir)
            assert (source / "sidecar.db").is_file(), "stopped test database is missing sidecar.db"
            for suffix in ("", "-wal", "-shm"):
                part = source / ("sidecar.db" + suffix)
                if part.exists(): shutil.copy2(part, data / part.name)
        override = root / "override.json"
        override.write_text(json.dumps({"services": {
            "host": {"entrypoint": ["python", "/host/verification.py"], "volumes": [
                {"type": "bind", "source": str(ROOT / "scripts/fixtures/shutdown-host.py"),
                 "target": "/host/verification.py", "read_only": True}]},
            "sidecar": {"environment": {"LOOMSPAN_SHUTDOWN_TIMEOUT": "3s"}, "volumes": [
                {"type": "bind", "source": str(data), "target": "/sidecar/data"}]}
        }}))
        compose = ["docker", "compose", "-p", project, "-f", str(ROOT / "examples/quickstart/compose.yaml"),
                   "-f", str(override)]
        if args.validate_only:
            image.run(*compose, "config", "--quiet", env=environment, timeout=15)
            return
        container = None
        try:
            image.run(*compose, "up", "-d", "--build", env=environment)
            image.wait_for(management_url + "/actuator/health/readiness")
            token = json.loads(image.request(host_url + "/token")[1])["access_token"]
            status, accepted = image.request(api_url + "/v1/skills/quickstartPlanner/executions",
                                             "POST", token, {"message": "shutdown"})
            assert status == 202, accepted
            wait_state(host_url, lambda state: state["entered"])
            container = image.run(*compose, "ps", "-q", "sidecar", env=environment, capture=True).stdout.strip()
            assert container
            started = time.monotonic()
            image.run("docker", "kill", "--signal=TERM", container, timeout=10)
            time.sleep(.3)
            state = json.loads(image.run("docker", "inspect", container, capture=True).stdout)[0]["State"]
            assert state["Running"], "Sidecar exited before admitted work could finish or reach cutoff"
            if not cutoff:
                assert image.request(host_url + "/verification/release", "POST", body={})[0] == 200
                wait_state(host_url, lambda state: state["detail"] and state["finished"], seconds=5)
            image.run("docker", "wait", container, capture=True, timeout=15)
            elapsed = time.monotonic() - started
            state = json.loads(image.run("docker", "inspect", container, capture=True).stdout)[0]["State"]
            assert state["Status"] == "exited" and not state["OOMKilled"], state
            assert state["ExitCode"] in (0, 143), state
            assert elapsed < 12, f"Shutdown took {elapsed:.2f}s for a 3s framework budget"
            progress = json.loads(image.request(host_url + "/verification/status")[1])
            if cutoff:
                assert elapsed >= 2.5, f"Work was torn down before the 3s cutoff: {elapsed:.2f}s"
                assert not progress["detail"] and not progress["finished"], progress
            else:
                assert progress["detail"] and progress["finished"], progress
            print(f"PASS {mode}: SIGTERM exit {state['ExitCode']} in {elapsed:.2f}s; {progress}", flush=True)
        finally:
            try:
                if container:
                    image.run("docker", "logs", container, check=False, timeout=15)
            finally:
                image.require_cleanup(*compose, "down", "--volumes", "--remove-orphans", env=environment)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True)
    parser.add_argument("--validate-only", action="store_true",
                        help="Validate Compose configuration without starting containers")
    parser.add_argument("--database-dir", help="Directory containing a stopped test database with quickstartPlanner")
    parser.add_argument("--api-port", type=int, default=28080)
    parser.add_argument("--host-port", type=int, default=28081)
    parser.add_argument("--management-port", type=int, default=29091)
    args = parser.parse_args()
    ports = (args.api_port, args.host_port, args.management_port)
    if any(port < 1 or port > 65535 for port in ports) or len(set(ports)) != 3:
        parser.error("host ports must be distinct values from 1 through 65535")
    if not args.validate_only and not args.database_dir:
        parser.error("--database-dir is required for active shutdown verification")
    for cutoff in (False, True):
        verify(args, cutoff)


if __name__ == "__main__":
    main()
