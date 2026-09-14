#!/usr/bin/env python3
"""Nonpublishing Docker/Compose verification for the Sidecar image and examples."""

import argparse
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_COMMAND_TIMEOUT_SECONDS = 300
CLEANUP_TIMEOUT_SECONDS = 30


def run(*command, cwd=ROOT, env=None, capture=False, check=True,
        timeout=DEFAULT_COMMAND_TIMEOUT_SECONDS):
    print("+", " ".join(map(str, command)), flush=True)
    return subprocess.run(command, cwd=cwd, env=env, check=check, text=True,
                          capture_output=capture, timeout=timeout)


def cleanup(*command, cwd=ROOT, env=None):
    try:
        result = run(*command, cwd=cwd, env=env, check=False, timeout=CLEANUP_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        print("Cleanup command timed out:", " ".join(map(str, command)), flush=True)
        return False
    if result.returncode:
        print("Cleanup command failed:", " ".join(map(str, command)), flush=True)
        return False
    return True


def require_cleanup(*command, cwd=ROOT, env=None):
    succeeded = cleanup(*command, cwd=cwd, env=env)
    if not succeeded and sys.exc_info()[0] is None:
        raise RuntimeError("Docker cleanup did not complete")


def request(url, method="GET", token=None, body=None):
    headers = {}
    if token: headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    if data is not None: headers["Content-Type"] = "application/json"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=5) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode()


def wait_for(url, expected=200, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            status, body = request(url)
            if status == expected: return body
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    raise AssertionError(f"{url} did not return {expected} within {seconds}s")


def verify_kubernetes():
    text = (ROOT / "examples/kubernetes/deployment.yaml").read_text()
    required = ["name: application", "name: loomspan-sidecar", "readOnly: true",
                "/sidecar/skills/", "/sidecar/rest-routes.yaml", "subPath: rest-routes.yaml",
                "secretKeyRef:", "port: management", "containerPort: 9091",
                "startupProbe:", "readinessProbe:", "livenessProbe:"]
    for value in required:
        assert value in text, f"Kubernetes example is missing {value!r}"
    grace = int(text.split("terminationGracePeriodSeconds:", 1)[1].splitlines()[0].strip())
    assert grace > 30, "termination grace must exceed the default framework shutdown budget"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True)
    parser.add_argument("--verify-kubernetes", action="store_true")
    parser.add_argument("--api-port", type=int, default=8080)
    parser.add_argument("--host-port", type=int, default=8081)
    parser.add_argument("--management-port", type=int, default=9091)
    args = parser.parse_args()
    ports = (args.api_port, args.host_port, args.management_port)
    if any(port < 1 or port > 65535 for port in ports) or len(set(ports)) != len(ports):
        parser.error("host ports must be distinct values from 1 through 65535")
    if args.verify_kubernetes: verify_kubernetes()

    inspect = json.loads(run("docker", "image", "inspect", args.image, capture=True).stdout)[0]
    assert inspect["Config"]["User"] == "10001:10001"
    assert any(value.startswith("JAVA_TOOL_OPTIONS=") and "MaxRAMPercentage" in value
               for value in inspect["Config"]["Env"])

    project = "loomspan-sc5-" + str(os.getpid())
    compose = ROOT / "examples/quickstart/compose.yaml"
    environment = dict(os.environ, SIDECAR_IMAGE=args.image,
                       SIDECAR_API_PORT=str(args.api_port),
                       QUICKSTART_HOST_PORT=str(args.host_port),
                       SIDECAR_MANAGEMENT_PORT=str(args.management_port))
    api_url = f"http://127.0.0.1:{args.api_port}"
    host_url = f"http://127.0.0.1:{args.host_port}"
    management_url = f"http://127.0.0.1:{args.management_port}"
    try:
        run("docker", "compose", "-p", project, "-f", str(compose), "up", "-d", "--build", env=environment)
        wait_for(management_url + "/actuator/health/readiness")
        liveness = wait_for(management_url + "/actuator/health/liveness")
        assert '"status":"UP"' in liveness
        token = json.loads(wait_for(host_url + "/token"))["access_token"]
        status, blocked = request(api_url + "/v1/skills/identityLeaf/executions",
                                  "POST", token, {"message": "block"})
        assert status == 202, blocked
        blocked_id = json.loads(blocked)["id"]
        time.sleep(.25)
        assert '"status":"UP"' in request(management_url + "/actuator/health/liveness")[1]
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            blocked_result = json.loads(request(f"{api_url}/v1/executions/{blocked_id}", token=token)[1])
            if blocked_result.get("status") == "COMPLETED": break
            time.sleep(.25)
        assert blocked_result.get("status") == "COMPLETED", blocked_result

        status, accepted = request(api_url + "/v1/skills/quickstartPlanner/executions",
                                   "POST", token, {"message": "hello"})
        assert status == 202, accepted
        execution_id = json.loads(accepted)["id"]
        deadline = time.monotonic() + 45
        result = None
        while time.monotonic() < deadline:
            status, body = request(f"{api_url}/v1/executions/{execution_id}", token=token)
            result = json.loads(body)
            if result.get("status") in ("COMPLETED", "FAILED"): break
            time.sleep(.25)
        assert result and result.get("status") == "COMPLETED", result
        assert result.get("result") == "quickstart complete", result
        assert "events" in result and result["events"], result
        foreign = json.loads(request(host_url + "/token?subject=other-user")[1])["access_token"]
        assert request(f"{api_url}/v1/executions/{execution_id}", token=foreign)[0] == 404
        verified = json.loads(request(host_url + "/status")[1])["verified"]
        assert {entry["path"] for entry in verified} >= {"/callbacks/identity", "/callbacks/detail"}
        assert all(entry["subject"] == "quickstart-user" and "QUICKSTART_USER" in entry["roles"] for entry in verified)

        started = time.monotonic()
        run("docker", "compose", "-p", project, "-f", str(compose), "stop", "-t", "15", "sidecar", env=environment)
        assert time.monotonic() - started < 20

        with tempfile.TemporaryDirectory(prefix="loomspan-invalid-") as directory:
            root = pathlib.Path(directory)
            (root / "skills").mkdir()
            (root / "skills" / "leaf.yaml").write_text((ROOT / "examples/quickstart/sidecar/skills/identity-leaf.yaml").read_text())
            (root / "rest-routes.yaml").write_text("targets: {}\nroutes:\n  identityLeaf: {target: missing, method: GET, path: /}\n")
            (root / "public.pem").write_text((ROOT / "examples/quickstart/sidecar/keys/public.pem").read_text())
            name = project + "-invalid"
            try:
                run("docker", "run", "--name", name, "-v", f"{root.resolve()}:/config:ro",
                    args.image,
                    "--loomspan.skills.locations=file:/config/skills/*.yaml",
                    "--loomspan-sidecar.rest-routes-location=file:/config/rest-routes.yaml",
                    "--loomspan-sidecar.auth.jwt.issuer-uri=https://invalid.example",
                    "--loomspan-sidecar.auth.jwt.audience=sidecar",
                    "--loomspan-sidecar.auth.jwt.public-key-location=file:/config/public.pem",
                    check=False, timeout=30)
                state = json.loads(run("docker", "inspect", name, capture=True).stdout)[0]["State"]
                assert state["Status"] == "exited" and state["ExitCode"] != 0
            finally:
                require_cleanup("docker", "rm", "--force", name)
    finally:
        require_cleanup("docker", "compose", "-p", project, "-f", str(compose), "down", "--volumes",
                        "--remove-orphans", env=environment)


if __name__ == "__main__":
    main()
