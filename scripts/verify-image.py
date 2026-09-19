#!/usr/bin/env python3
"""Docker/Compose smoke verification for database-first Sidecar startup."""

import argparse
import json
import os
import pathlib
import subprocess
import sys
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True)
    parser.add_argument("--api-port", type=int, default=8080)
    parser.add_argument("--host-port", type=int, default=8081)
    parser.add_argument("--management-port", type=int, default=9091)
    args = parser.parse_args()
    ports = (args.api_port, args.host_port, args.management_port)
    if any(port < 1 or port > 65535 for port in ports) or len(set(ports)) != len(ports):
        parser.error("host ports must be distinct values from 1 through 65535")

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
        rendered = json.loads(run("docker", "compose", "-p", project, "-f", str(compose),
                                  "config", "--format", "json", env=environment, capture=True).stdout)
        for service in rendered["services"].values():
            assert all(binding.get("host_ip") == "127.0.0.1" for binding in service.get("ports", [])), \
                "Development HTTP ports must bind only to loopback"
        run("docker", "compose", "-p", project, "-f", str(compose), "up", "-d", "--build", env=environment)
        wait_for(management_url + "/actuator/health/readiness")
        liveness = wait_for(management_url + "/actuator/health/liveness")
        assert '"status":"UP"' in liveness
        token = json.loads(wait_for(host_url + "/token"))["access_token"]
        assert request(api_url + "/v1/skills")[0] == 401
        status, skills = request(api_url + "/v1/skills", token=token)
        assert status == 200 and json.loads(skills) == []
        assert request(api_url + "/v1/skills/unknown/executions", "POST", token, {})[0] == 404
        assert '"status":"UP"' in request(management_url + "/actuator/health/liveness")[1]

        started = time.monotonic()
        run("docker", "compose", "-p", project, "-f", str(compose), "stop", "-t", "15", "sidecar", env=environment)
        assert time.monotonic() - started < 20

    finally:
        require_cleanup("docker", "compose", "-p", project, "-f", str(compose), "down", "--volumes",
                        "--remove-orphans", env=environment)


if __name__ == "__main__":
    main()
