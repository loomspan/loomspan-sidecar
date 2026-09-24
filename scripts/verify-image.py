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
import urllib.parse
import http.cookiejar
import re

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
    generated = [run("docker", "run", "--rm", args.image, "admin", "generate-setup-token",
                     capture=True, timeout=30) for _ in range(2)]
    assert all(re.fullmatch(r"[A-Za-z0-9_-]{43}\n?", item.stdout) for item in generated)
    assert generated[0].stdout != generated[1].stdout
    invalid = run("docker", "run", "--rm", args.image, "admin", "generate-setup-token", "extra",
                  capture=True, check=False, timeout=30)
    assert invalid.returncode != 0 and not invalid.stdout

    project = "loomspan-sc5-" + str(os.getpid())
    compose = ROOT / "examples/quickstart/compose.yaml"
    environment = dict(os.environ, SIDECAR_IMAGE=args.image,
                       LOOMSPAN_SIDECAR_SETUP_TOKEN=generated[0].stdout.strip(),
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

        browser = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        with browser.open(api_url + "/management/setup") as response:
            page = response.read().decode()
        match = re.search(r"name='_csrf' value='([^']+)'", page)
        assert match and "name='confirmation'" in page
        payload = json.dumps({"credential": generated[0].stdout.strip(),
                              "email": "admin@example.test", "password": "Long Password 123!",
                              "confirmation": "Long Password 123!"}).encode()
        with browser.open(urllib.request.Request(api_url + "/api/management/setup", data=payload,
                          headers={"Content-Type": "application/json", "X-CSRF-TOKEN": match.group(1)})) as response:
            assert response.status == 202
        assert "Management setup is complete" in browser.open(api_url + "/management/setup").read().decode()

        busy = run("docker", "compose", "-p", project, "-f", str(compose), "run", "--rm", "--no-deps",
                   "sidecar", "admin", "issue-password-reset", "--database", "/sidecar/data/sidecar.db",
                   "--email", "admin@example.test", env=environment, capture=True, check=False, timeout=60)
        assert busy.returncode != 0 and not busy.stdout

        started = time.monotonic()
        run("docker", "compose", "-p", project, "-f", str(compose), "stop", "-t", "15", "sidecar", env=environment)
        assert time.monotonic() - started < 20
        reset = run("docker", "compose", "-p", project, "-f", str(compose), "run", "--rm", "--no-deps",
                    "sidecar", "admin", "issue-password-reset", "--database", "/sidecar/data/sidecar.db",
                    "--email", "admin@example.test", env=environment, capture=True, timeout=60)
        assert re.fullmatch(r"[A-Za-z0-9_-]{43}\n?", reset.stdout), "reset output contract"
        environment["LOOMSPAN_SIDECAR_SETUP_TOKEN"] = ""
        run("docker", "compose", "-p", project, "-f", str(compose), "up", "-d", "--force-recreate",
            "sidecar", env=environment)
        wait_for(management_url + "/actuator/health/readiness")
        recovery_browser = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        reset_page = recovery_browser.open(api_url + "/management/password/reset").read().decode()
        assert "name='token'" in reset_page and "type='password'" in reset_page
        reset_csrf = re.search(r"name='_csrf' value='([^']+)'", reset_page).group(1)
        form = urllib.parse.urlencode({"token": reset.stdout.strip(),
                                       "password": "Another Long Password 1!", "_csrf": reset_csrf}).encode()
        with recovery_browser.open(urllib.request.Request(api_url + "/management/password/reset", data=form,
                                   headers={"Content-Type": "application/x-www-form-urlencoded"})) as response:
            assert "Password saved" in response.read().decode()
        login_page = recovery_browser.open(api_url + "/management/login").read().decode()
        login_csrf = re.search(r"name='_csrf' value='([^']+)'", login_page).group(1)
        login_form = urllib.parse.urlencode({"email": "admin@example.test", "password": "Another Long Password 1!",
                                             "_csrf": login_csrf}).encode()
        with recovery_browser.open(urllib.request.Request(api_url + "/management/login", data=login_form,
                                   headers={"Content-Type": "application/x-www-form-urlencoded"})) as response:
            assert response.url.endswith("/management/home")

    finally:
        require_cleanup("docker", "compose", "-p", project, "-f", str(compose), "down", "--volumes",
                        "--remove-orphans", env=environment)


if __name__ == "__main__":
    main()
