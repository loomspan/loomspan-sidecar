#!/usr/bin/env python3
"""Exercise the production Compose service with disposable local fixtures."""

import argparse
import base64
import http.cookiejar
import importlib.util
import json
import os
import pathlib
import re
import secrets
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("image_verifier", ROOT / "scripts/verify-image.py")
image = importlib.util.module_from_spec(spec)
spec.loader.exec_module(image)


def port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def send(opener, url, method="GET", body=None, csrf=None, token=None, extra_headers=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if csrf:
        headers["X-CSRF-TOKEN"] = csrf
    if token:
        headers["Authorization"] = "Bearer " + token
    if extra_headers:
        headers.update(extra_headers)
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(request, timeout=10) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode()


def wait(action, description, seconds=90):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        try:
            result = action()
            if result:
                return result
        except (OSError, urllib.error.URLError, ValueError, subprocess.CalledProcessError) as failure:
            last = failure
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for {description}: {last}")


def need(status, body, expected):
    assert status == expected, f"Expected HTTP {expected}, got {status}: {body[:600]}"
    return body


def csrf_from(html):
    match = re.search(r"name='_csrf' value='([^']+)'", html)
    assert match, "CSRF form field missing"
    return match.group(1)


def login(opener, origin, email, password):
    page = need(*send(opener, origin + "/management/login"), 200)
    fields = urllib.parse.urlencode({"email": email, "password": password, "_csrf": csrf_from(page)}).encode()
    request = urllib.request.Request(origin + "/management/login", data=fields,
                                     headers={"Content-Type": "application/x-www-form-urlencoded"})
    with opener.open(request, timeout=10) as response:
        assert response.url.endswith("/management/home"), response.url
    return json.loads(need(*send(opener, origin + "/api/management/session"), 200))["csrfToken"]


def publish_fixture(opener, origin, csrf):
    tab = str(uuid.uuid4())
    grant = json.loads(need(*send(opener, origin + "/api/management/editing/lease", "POST",
                                 {"tabId": tab}, csrf), 200))
    name = grant["grantId"]
    candidate = grant["draft"]["candidateId"]
    fixture = ROOT / "examples/quickstart/sidecar"
    skill_documents = []
    for filename in ("identity-leaf.yaml", "detail-leaf.yaml", "planner.yaml"):
        yaml = (fixture / "skills" / filename).read_text().replace("model: localplanner", "model: primary")
        skill_documents.append({"sourceName": filename, "yaml": yaml})
    routes = (fixture / "rest-routes.yaml").read_text().replace(
        "${QUICKSTART_HOST_URL:http://host:8081}", "${TARGET_URL}")
    saved = json.loads(need(*send(opener, origin + "/api/management/editing/draft", "PUT",
                                 {"tabId": tab, "grantId": name, "expectedCandidateId": candidate,
                                  "skillDocuments": skill_documents, "restRoutesYaml": routes}, csrf), 200))
    cap = {"tabId": tab, "grantId": name, "expectedCandidateId": saved["candidateId"]}
    checked = json.loads(need(*send(opener, origin + "/api/management/editing/draft/validate", "POST",
                                   cap, csrf), 200))
    assert checked["validation"]["successful"], checked
    published = json.loads(need(*send(opener, origin + "/api/management/configuration/publish", "POST",
                                     cap, csrf), 200))
    assert published["localId"]
    return published["localId"]


def execute(opener, origin, token, skill):
    accepted = json.loads(need(*send(opener, origin + f"/v1/skills/{skill}/executions", "POST",
                                     {"message": "production fixture"}, token=token), 202))
    identifier = accepted["id"]
    result = wait(lambda: (lambda item: item if item.get("status") in ("COMPLETED", "FAILED") else None)(
        json.loads(need(*send(opener, origin + "/v1/executions/" + identifier, token=token), 200))),
        skill + " execution", seconds=30)
    assert result["status"] == "COMPLETED", result
    return result


def browser(origin, email, password, setup_link=None, recovery_link=None):
    environment = dict(os.environ, PRODUCTION_TEST_ORIGIN=origin,
                       PRODUCTION_TEST_EMAIL=email, PRODUCTION_TEST_PASSWORD=password,
                       PRODUCTION_SETUP_LINK=setup_link or "",
                       PRODUCTION_RECOVERY_LINK=recovery_link or "")
    wrapper = "mvnw.cmd" if os.name == "nt" else "./mvnw"
    image.run(str(ROOT / wrapper), "-B", "-ntp", "-Dtest=ProductionComposeBrowserIntegrationTest", "test",
              env=environment, timeout=180)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True)
    args = parser.parse_args()
    project = "sidecar-prod-test-" + uuid.uuid4().hex[:8]
    https_port, host_port, smtp_port = port(), port(), port()
    assert len({https_port, host_port, smtp_port}) == 3
    origin = f"https://127.0.0.1:{https_port}"
    email = "admin@example.test"
    password = "Long Password 123!"
    setup = base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip("=")
    compose = ["docker", "compose", "--project-name", project,
               "-f", str(ROOT / "examples/production/compose.yaml")]
    with tempfile.TemporaryDirectory(prefix="sidecar-production-") as temporary:
        root = pathlib.Path(temporary)
        tls = root / "tls"
        tls.mkdir()
        image.run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                  "-keyout", str(tls / "privkey.pem"), "-out", str(tls / "fullchain.pem"),
                  "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1",
                  timeout=20, capture=True)
        # OpenSSL creates the key as 0600 on Linux. The disposable fixture
        # container runs as UID 10001, while the temporary parent stays 0700.
        (tls / "privkey.pem").chmod(0o644)
        env_file = root / "fixture.env"
        values = {
            "SIDECAR_IMAGE": args.image, "HTTPS_BIND_ADDRESS": "127.0.0.1", "HTTPS_PORT": str(https_port),
            "TLS_DIR": str(tls),
            "JWT_PUBLIC_KEY_FILE": str(ROOT / "examples/quickstart/host/public.pem"),
            "JWT_ISSUER_URI": "http://host:8081", "JWT_AUDIENCE": "loomspan-sidecar",
            "MODEL_DRIVER": "openai", "MODEL_BASE_URL": "http://host:8081/v1",
            "MODEL_API_KEY": "fixture-only", "MODEL_NAME": "deterministic-planner",
            "SETUP_TOKEN": "", "SMTP_FROM": "sidecar@example.test", "EXTERNAL_BASE_URL": origin,
            "SMTP_HOST": "smtp", "SMTP_PORT": "2525", "SMTP_USERNAME": "", "SMTP_PASSWORD": "",
            "SMTP_AUTH": "false", "SMTP_STARTTLS": "false", "URL_VARIABLES": "TARGET_URL",
            "TARGET_URL": "http://host:8081"}
        compose_environment = dict(os.environ)

        def write_env():
            env_file.write_text("".join(f"{key}={value}\n" for key, value in values.items()))
            # Compose gives process variables precedence over --env-file.
            compose_environment.update(values)

        write_env()
        override = root / "fixture.json"
        override.write_text(json.dumps({"services": {
            "host": {"build": {"context": str(ROOT / "examples/quickstart/host")},
                     "environment": {"QUICKSTART_ISSUER": "http://host:8081",
                                     "QUICKSTART_AUDIENCE": "loomspan-sidecar"},
                     "ports": [f"127.0.0.1:{host_port}:8081"]},
            "smtp": {"image": "python:3.13-slim", "entrypoint": ["python", "/fixture/smtp-capture.py"],
                     "volumes": [{"type": "bind", "source": str(ROOT / "scripts/fixtures/smtp-capture.py"),
                                  "target": "/fixture/smtp-capture.py", "read_only": True}],
                     "ports": [f"127.0.0.1:{smtp_port}:8025"]}
        }}))
        command = [*compose, "-f", str(override), "--env-file", str(env_file)]
        volume = project + "_sidecar-data"
        opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl._create_unverified_context()),
                                             urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        plain = urllib.request.build_opener()
        try:
            image.run(*command, "config", "--quiet", env=compose_environment, timeout=20)
            rendered = image.run(*command, "config", "--format", "json", env=compose_environment,
                                 capture=True, timeout=20).stdout
            config = json.loads(rendered)
            service = config["services"]["sidecar"]
            assert service["environment"]["LOOMSPAN_SIDECAR_SMTP_PASSWORD"] == ""
            assert service["environment"]["LOOMSPAN_CONNECTIONS_PRIMARY_API_KEY"] == "fixture-only"
            assert len([key for key in config["services"] if key == "sidecar"]) == 1
            assert service["user"] == "10001:10001"
            assert all("9091" not in str(item) for item in service["ports"]), service["ports"]
            assert service["stop_grace_period"] in ("45s", 45000000000), service["stop_grace_period"]
            assert service["environment"]["LOOMSPAN_SIDECAR_SECURE_COOKIE"] == "true"
            mounts = {mount["target"]: mount for mount in service["volumes"]}
            assert mounts["/sidecar/tls"]["read_only"]
            assert mounts["/sidecar/keys/public.pem"]["read_only"]
            assert mounts["/sidecar/data"]["type"] == "volume"
            image.run("docker", "volume", "create", volume, capture=True, timeout=20)
            image.run("docker", "run", "--rm", "--user", "0:0", "--volume", f"{volume}:/sidecar/data",
                      "--entrypoint", "sh", args.image, "-c", "chown 10001:10001 /sidecar/data && chmod 700 /sidecar/data",
                      timeout=30)
            image.run(*command, "up", "-d", "--build", env=compose_environment, timeout=180)
            sidecar = image.run(*command, "ps", "-q", "sidecar", env=compose_environment,
                                capture=True).stdout.strip()
            assert sidecar
            wait(lambda: "UP" in image.run("docker", "exec", sidecar, "curl", "--fail", "--silent",
                                            "http://localhost:9091/actuator/health/readiness", capture=True,
                                            timeout=10).stdout, "readiness")
            assert "UP" in image.run("docker", "exec", sidecar, "curl", "--fail", "--silent",
                                      "http://localhost:9091/actuator/health/liveness", capture=True).stdout
            assert image.run("docker", "exec", sidecar, "id", "-u", capture=True).stdout.strip() == "10001"
            image.run("docker", "exec", sidecar, "sh", "-c",
                      "test ! -e /fixture/smtp-capture.py && test ! -e /host/host.py")
            assert image.run("docker", "exec", sidecar, "stat", "-c", "%u:%g", "/sidecar/data/sidecar.db",
                             capture=True).stdout.strip() == "10001:10001"
            ports = json.loads(image.run("docker", "inspect", sidecar, capture=True).stdout)[0]["NetworkSettings"]["Ports"]
            assert ports.get("9091/tcp") is None, ports
            certificate = ssl.get_server_certificate(("127.0.0.1", https_port))
            assert "BEGIN CERTIFICATE" in certificate
            with socket.create_connection(("127.0.0.1", https_port), timeout=2) as cleartext:
                cleartext.settimeout(2)
                cleartext.sendall(b"GET /management/login HTTP/1.1\r\nHost: localhost\r\n\r\n")
                try:
                    response = cleartext.recv(100)
                except OSError:
                    response = b""
                assert not response.startswith(b"HTTP/1.1 200"), response
            token = json.loads(need(*send(plain, f"http://127.0.0.1:{host_port}/token"), 200))["access_token"]
            need(*send(opener, origin + "/v1/skills"), 401)
            assert json.loads(need(*send(opener, origin + "/v1/skills", token=token), 200)) == []
            locked = need(*send(opener, origin + "/management/setup"), 200)
            assert "not configured" in locked.lower() or "credential" not in locked.lower(), locked[:400]
            print("PASS fresh volume: readiness, JWT execution, management lock, UID, TLS, and exposure", flush=True)

            values["SETUP_TOKEN"] = setup
            write_env()
            image.run(*command, "up", "-d", "--force-recreate", "sidecar",
                      env=compose_environment, timeout=120)
            wait(lambda: send(opener, origin + "/management/setup")[0] == 200, "setup page")
            page = need(*send(opener, origin + "/management/setup"), 200)
            need(*send(opener, origin + "/api/management/setup", "POST",
                       {"credential": setup, "email": email}, csrf_from(page)), 202)
            mail_url = f"http://127.0.0.1:{smtp_port}/messages"
            messages = wait(lambda: json.loads(send(plain, mail_url)[1]) or None, "captured setup email")
            assert len(messages) == 1 and "To: admin@example.test" in messages[0]
            assert "From: sidecar@example.test" in messages[0]
            match = re.search(re.escape(origin) + r"/management/password/set\?token=[A-Za-z0-9_-]{43}", messages[0])
            assert match, "setup email did not use the trusted HTTPS origin"
            browser(origin, email, password, match.group(0))
            print("PASS Chromium HTTPS setup, login, and Secure session cookie", flush=True)

            print("CHECK management API login", flush=True)
            session_csrf = login(opener, origin, email, password)
            print("CHECK authored fixture publication", flush=True)
            snapshot = publish_fixture(opener, origin, session_csrf)
            print("CHECK JWT REST/model execution", flush=True)
            skills = json.loads(need(*send(opener, origin + "/v1/skills", token=token), 200))
            assert any("identityLeaf" in str(item) for item in skills), skills
            assert execute(opener, origin, token, "identityLeaf")["configurationSnapshotId"] == snapshot
            assert execute(opener, origin, token, "quickstartPlanner")["configurationSnapshotId"] == snapshot
            assert "${TARGET_URL}" in json.loads(need(*send(opener, origin +
                "/api/management/configuration/current"), 200))["published"]["configuration"]["restRoutesYaml"]
            print("PASS published REST URL variable and model-backed execution", flush=True)

            need(*send(opener, origin + "/api/management/password/forgot", "POST", {"email": email},
                       session_csrf, extra_headers={"X-Forwarded-Host": "evil.example.test"}), 202)
            messages = wait(lambda: (lambda mail: mail if len(mail) >= 2 else None)(
                json.loads(send(plain, mail_url)[1])), "captured recovery email")
            assert "evil.example.test" not in messages[-1]
            recovery = re.search(re.escape(origin) + r"/management/password/reset\?token=[A-Za-z0-9_-]{43}",
                                 messages[-1])
            assert recovery, "recovery email did not use the trusted HTTPS origin"

            values["SETUP_TOKEN"] = ""
            write_env()
            image.run(*command, "up", "-d", "--force-recreate", "sidecar",
                      env=compose_environment, timeout=120)
            wait(lambda: send(opener, origin + "/management/login")[0] == 200, "recreated login")
            browser(origin, email, password, recovery_link=recovery.group(0))
            assert any("identityLeaf" in str(item) for item in json.loads(
                need(*send(opener, origin + "/v1/skills", token=token), 200)))
            assert "Management setup is complete" in need(*send(opener, origin + "/management/setup"), 200)
            assert execute(opener, origin, token, "identityLeaf")["configurationSnapshotId"] == snapshot
            print("PASS account and authored database selection after recreation without setup credential", flush=True)

            image.run(*command, "stop", "sidecar", env=compose_environment, timeout=60)
            backup = root / "stopped-backup"
            backup.mkdir()
            image.run("docker", "run", "--rm", "--user", "0:0",
                      "--mount", f"type=volume,source={volume},target=/source,readonly",
                      "--mount", f"type=bind,source={backup},target=/backup",
                      "--entrypoint", "sh", args.image, "-c",
                      "cp -p /source/sidecar.db* /backup/ && test -f /backup/sidecar.db", timeout=30)
            assert (backup / "sidecar.db").is_file()
            image.run(*command, "rm", "-f", "sidecar", env=compose_environment, timeout=30)
            image.run("docker", "volume", "rm", volume, timeout=30)
            image.run("docker", "volume", "create", volume, capture=True, timeout=30)
            image.run("docker", "run", "--rm", "--user", "0:0",
                      "--mount", f"type=volume,source={volume},target=/sidecar/data",
                      "--mount", f"type=bind,source={backup},target=/backup,readonly",
                      "--entrypoint", "sh", args.image, "-c",
                      "cp -p /backup/sidecar.db* /sidecar/data/ && chown -R 10001:10001 /sidecar/data && chmod 700 /sidecar/data",
                      timeout=30)
            image.run(*command, "up", "-d", "sidecar", env=compose_environment, timeout=120)
            wait(lambda: send(opener, origin + "/management/login")[0] == 200, "restored login")
            browser(origin, email, password)
            assert execute(opener, origin, token, "identityLeaf")["configurationSnapshotId"] == snapshot
            print("PASS stopped database-set restore with UID 10001 account/config activation", flush=True)

            image.run(sys.executable, str(ROOT / "scripts/verify-shutdown.py"), "--image", args.image,
                      "--database-dir", str(backup), "--api-port", str(port()),
                      "--host-port", str(port()), "--management-port", str(port()), timeout=240)
            print("PASS admitted-work completion and framework cutoff across SIGTERM", flush=True)
        finally:
            image.require_cleanup(*command, "down", "--volumes", "--remove-orphans",
                                  env=compose_environment)
            image.require_cleanup("docker", "volume", "rm", "--force", volume)


if __name__ == "__main__":
    main()
