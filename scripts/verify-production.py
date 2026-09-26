#!/usr/bin/env python3
"""Exercise the production Compose service with disposable local fixtures."""

import argparse
import base64
import http.cookiejar
import importlib.util
import json
import io
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
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
# The disposable Compose fixture binds only 127.0.0.1. Keep the HTTPS URL and
# SNI as localhost while avoiding a stalled ::1 connection on some hosts.
_getaddrinfo = socket.getaddrinfo


def fixture_getaddrinfo(host, *args, **kwargs):
    return _getaddrinfo("127.0.0.1" if host == "localhost" else host, *args, **kwargs)


socket.getaddrinfo = fixture_getaddrinfo
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


def client():
    return urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl._create_unverified_context()),
                                       urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def multipart(opener, url, bundle, csrf, fields=None):
    boundary = "sidecar-" + uuid.uuid4().hex
    parts = []
    for key, value in (fields or {}).items():
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"\r\n\r\n{value}\r\n".encode())
    parts.extend([f"--{boundary}\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"config.zip\"\r\nContent-Type: application/zip\r\n\r\n".encode(), bundle,
                  f"\r\n--{boundary}--\r\n".encode()])
    request = urllib.request.Request(url, data=b"".join(parts), method="POST", headers={
        "Content-Type": "multipart/form-data; boundary=" + boundary, "X-CSRF-TOKEN": csrf})
    try:
        with opener.open(request, timeout=20) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode()


def get_bytes(opener, url):
    with opener.open(url, timeout=20) as response:
        assert response.status == 200 and response.headers.get_content_type() == "application/zip"
        return response.read()


def draft(opener, origin, csrf):
    grant = json.loads(need(*send(opener, origin + "/api/management/editing/lease", "POST",
                                 {"label": "Production verifier"}, csrf), 200))
    return candidate(grant, grant["draft"])


def candidate(lease, saved):
    return {key: lease[key] for key in ("editingSessionId", "generation")} | {
        key: saved[key] for key in ("draftId", "revision", "baseSnapshotId")}


def publish_draft(opener, origin, csrf, cap):
    checked = json.loads(need(*send(opener, origin + "/api/management/editing/draft/validate", "POST",
                                   cap, csrf), 200))
    assert checked["validation"]["successful"], checked
    return json.loads(need(*send(opener, origin + "/api/management/configuration/publish", "POST",
                                 candidate(cap, checked), csrf), 200))


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
    cap = draft(opener, origin, csrf)
    fixture = ROOT / "examples/quickstart/sidecar"
    skill_documents = []
    for filename in ("identity-leaf.yaml", "detail-leaf.yaml", "planner.yaml"):
        yaml = (fixture / "skills" / filename).read_text().replace("model: localplanner", "model: primary")
        skill_documents.append({"sourceName": filename, "yaml": yaml})
    routes = (fixture / "rest-routes.yaml").read_text().replace(
        "${QUICKSTART_HOST_URL:http://host:8081}", "${TARGET_URL}")
    execution = """loomspan:
  connections:
    primary:
      driver: openai
      base-url: http://host:8081/v1
      api-key-ref: provider.primary.key
  models:
    primary:
      connection: primary
      provider-model: deterministic-planner
"""
    saved = json.loads(need(*send(opener, origin + "/api/management/editing/draft", "PUT",
                                 {**cap,
                                  "skillDocuments": skill_documents, "restRoutesYaml": routes,
                                  "executionConfigurationYaml": execution}, csrf), 200))
    published = publish_draft(opener, origin, csrf, candidate(cap, saved))
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


def browser(origin, email, password, setup_link=None, recovery_link=None, editor_email=None, history_id=None):
    environment = dict(os.environ, PRODUCTION_TEST_ORIGIN=origin,
                       PRODUCTION_TEST_EMAIL=email, PRODUCTION_TEST_PASSWORD=password,
                       PRODUCTION_SETUP_LINK=setup_link or "",
                       PRODUCTION_RECOVERY_LINK=recovery_link or "",
                       PRODUCTION_EDITOR_EMAIL=editor_email or "",
                       PRODUCTION_HISTORY_ID=history_id or "")
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
    origin = f"https://localhost:{https_port}"
    email = "admin@example.test"
    password = "Long Password 123!"
    setup_result = image.run("docker", "run", "--rm", args.image, "admin", "generate-setup-token",
                             capture=True, timeout=30)
    setup = setup_result.stdout.strip()
    assert re.fullmatch(r"[A-Za-z0-9_-]{43}", setup)
    compose = ["docker", "compose", "--project-name", project,
               "-f", str(ROOT / "examples/production/compose.yaml")]
    with tempfile.TemporaryDirectory(prefix="sidecar-production-") as temporary:
        root = pathlib.Path(temporary)
        env_file = root / "fixture.env"
        values = {
            "SIDECAR_IMAGE": args.image, "HTTPS_BIND_ADDRESS": "127.0.0.1", "HTTPS_PORT": str(https_port),
            "HTTP_BIND_ADDRESS": "127.0.0.1", "HTTP_PORT": str(port()), "SITE_HOST": "localhost",
            "CADDY_CONFIG_FILE": str(ROOT / "examples/production/Caddyfile.internal"),
            "JWT_PUBLIC_KEY_FILE": str(ROOT / "examples/quickstart/host/public.pem"),
            "JWT_ISSUER_URI": "http://host:8081", "JWT_AUDIENCE": "loomspan-sidecar",
            "PROVIDER_PRIMARY_KEY": "fixture-only",
            "SETUP_TOKEN": "", "SMTP_FROM": "", "EXTERNAL_BASE_URL": origin,
            "SMTP_HOST": "", "SMTP_PORT": "2525", "SMTP_USERNAME": "", "SMTP_PASSWORD": "",
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
                     "networks": ["backend"],
                     "ports": [f"127.0.0.1:{host_port}:8081"]},
            "smtp": {"image": "python:3.13-slim", "entrypoint": ["python", "/fixture/smtp-capture.py"],
                     "networks": ["backend"],
                     "volumes": [{"type": "bind", "source": str(ROOT / "scripts/fixtures/smtp-capture.py"),
                                  "target": "/fixture/smtp-capture.py", "read_only": True}],
                     "ports": [f"127.0.0.1:{smtp_port}:8025"]}
        }}))
        command = [*compose, "-f", str(override), "--env-file", str(env_file)]
        volume = project + "_sidecar-data"
        opener = client()
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
            assert not service.get("ports"), service.get("ports")
            assert len(config["services"]["caddy"]["ports"]) == 2
            assert service["stop_grace_period"] in ("45s", 45000000000), service["stop_grace_period"]
            assert service["environment"]["LOOMSPAN_SIDECAR_SECURE_COOKIE"] == "true"
            mounts = {mount["target"]: mount for mount in service["volumes"]}
            assert mounts["/sidecar/keys/public.pem"]["read_only"]
            assert mounts["/sidecar/data"]["type"] == "volume"
            assert service["environment"]["SERVER_SSL_ENABLED"] == "false"
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
            assert ports.get("8080/tcp") is None, ports
            with socket.create_connection(("127.0.0.1", https_port), timeout=5) as raw:
                with ssl._create_unverified_context().wrap_socket(raw, server_hostname="localhost") as secure:
                    certificate = ssl.DER_cert_to_PEM_cert(secure.getpeercert(binary_form=True))
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
            need(*send(opener, origin + "/api/management/session", token=token), 401)
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
                       {"credential": setup, "email": email, "password": password,
                        "confirmation": password}, csrf_from(page)), 202)
            mail_url = f"http://127.0.0.1:{smtp_port}/messages"
            assert json.loads(send(plain, mail_url)[1]) == []
            browser(origin, email, password)
            print("PASS Chromium HTTPS setup, login, and Secure session cookie", flush=True)

            values["SMTP_FROM"] = "sidecar@example.test"
            values["SMTP_HOST"] = "smtp"
            write_env()
            image.run(*command, "up", "-d", "--force-recreate", "sidecar",
                      env=compose_environment, timeout=120)
            wait(lambda: send(opener, origin + "/management/login")[0] == 200, "mail-enabled login")

            print("CHECK management API login", flush=True)
            session_csrf = login(opener, origin, email, password)
            messages = json.loads(send(plain, mail_url)[1])
            mail_count = len(messages)
            users = {}
            for role in ("editor", "viewer"):
                address = role + "@example.test"
                account = json.loads(need(*send(opener, origin + "/api/management/accounts", "POST",
                                               {"email": address, "role": role}, session_csrf), 201))
                assert account["email"] == address and account["role"] == role
                messages = wait(lambda: (lambda mail: mail if len(mail) > mail_count else None)(
                    json.loads(send(plain, mail_url)[1])), role + " invitation")
                mail_count = len(messages)
                assert f"To: {address}" in messages[-1] and "evil.example.test" not in messages[-1]
                invited = re.search(re.escape(origin) + r"/management/password/set\?token=[A-Za-z0-9_-]{43}",
                                    messages[-1])
                assert invited, role + " invitation must use the trusted HTTPS origin"
                browser(origin, address, password, setup_link=invited.group(0))
                user = client()
                user_csrf = login(user, origin, address, password)
                assert json.loads(need(*send(user, origin + "/api/management/session"), 200))["role"] == role
                users[role] = (user, user_csrf)
            need(*send(users["viewer"][0], origin + "/api/management/editing/lease", "POST",
                       {"label": "Viewer"}, users["viewer"][1]), 403)
            need(*send(users["editor"][0], origin + "/api/management/accounts", "POST",
                       {"email": "forbidden@example.test", "role": "viewer"}, users["editor"][1]), 403)
            need(*send(opener, origin + "/api/management/accounts", "POST",
                       {"email": "csrf@example.test", "role": "viewer"}), 403)
            need(*send(opener, origin + "/v1/skills"), 401)
            print("PASS captured invitations, HTTPS links, role boundaries, CSRF and JWT separation", flush=True)
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
            browser(origin, email, password, editor_email="editor@example.test")
            assert json.loads(need(*send(opener, origin + "/api/management/configuration/current"), 200))[
                "published"]["localId"] != snapshot
            snapshot = json.loads(need(*send(opener, origin + "/api/management/configuration/current"), 200))[
                "published"]["localId"]
            print("PASS concurrent Chromium editing, private draft and stale grant invalidation", flush=True)

            need(*send(opener, origin + "/api/management/password/forgot", "POST", {"email": email},
                       session_csrf, extra_headers={"Forwarded": "host=evil.example.test;proto=http",
                                                    "X-Forwarded-Host": "evil.example.test",
                                                    "X-Forwarded-Proto": "http",
                                                    "X-Forwarded-Port": "1",
                                                    "X-Forwarded-Prefix": "/evil"}), 202)
            messages = wait(lambda: (lambda mail: mail if len(mail) > mail_count else None)(
                json.loads(send(plain, mail_url)[1])), "captured recovery email")
            assert all(re.search(r"(?m)^To: [^\n]+\.test$", message) for message in messages)
            assert "evil.example.test" not in messages[-1]
            recovery = re.search(re.escape(origin) + r"/management/password/reset\?token=[A-Za-z0-9_-]{43}",
                                 messages[-1])
            assert recovery, "recovery email did not use the trusted HTTPS origin"

            image.run(*command, "stop", "smtp", env=compose_environment, timeout=30)
            need(*send(opener, origin + "/api/management/accounts", "POST",
                       {"email": "outage@example.test", "role": "viewer"}, session_csrf), 503)
            need(*send(opener, origin + "/api/management/password/forgot", "POST",
                       {"email": "editor@example.test"}, session_csrf), 202)
            assert login(client(), origin, email, password)
            assert execute(opener, origin, token, "identityLeaf")["configurationSnapshotId"] == snapshot
            image.run(*command, "up", "-d", "smtp", env=compose_environment, timeout=60)
            wait(lambda: send(plain, mail_url)[0] == 200, "restarted SMTP capture")
            print("PASS mail outage leaves existing login and JWT execution available", flush=True)

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

            session_csrf = login(opener, origin, email, password)
            source_configuration = json.loads(need(*send(opener, origin +
                "/api/management/configuration/current"), 200))["published"]["configuration"]
            exported = get_bytes(opener, origin + "/api/management/configuration/export")
            with zipfile.ZipFile(io.BytesIO(exported)) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                assert manifest["sourceSnapshotId"] == snapshot
                assert "${TARGET_URL}" in json.loads(archive.read("rest.json"))["restRoutesYaml"]
                assert "provider.primary.key" in json.loads(archive.read("execution.json"))["executionConfigurationYaml"]
                assert not any("account" in name or "history" in name for name in archive.namelist())
                assert len([name for name in archive.namelist() if name.startswith("skills/")]) == 3

            second_project = project + "-destination"
            second_port, second_host_port, second_smtp_port = port(), port(), port()
            second_origin = f"https://localhost:{second_port}"
            second_setup = image.run("docker", "run", "--rm", args.image, "admin", "generate-setup-token",
                                     capture=True, timeout=30).stdout.strip()
            assert re.fullmatch(r"[A-Za-z0-9_-]{43}", second_setup) and second_setup != setup
            second_values = dict(values, HTTPS_PORT=str(second_port), HTTP_PORT=str(port()),
                                 EXTERNAL_BASE_URL=second_origin,
                                 TARGET_URL="http://fixture-destination:8081", URL_VARIABLES="",
                                 SETUP_TOKEN=second_setup)
            second_env = root / "destination.env"
            second_env.write_text("".join(f"{key}={value}\n" for key, value in second_values.items()))
            second_environment = dict(os.environ, **second_values)
            second_override = root / "destination.json"
            destination_services = json.loads(override.read_text())["services"]
            destination_services["host"]["ports"] = [f"127.0.0.1:{second_host_port}:8081"]
            destination_services["host"]["networks"] = {"backend": {"aliases": ["fixture-destination"]}}
            destination_services["smtp"]["ports"] = [f"127.0.0.1:{second_smtp_port}:8025"]
            second_override.write_text(json.dumps({"services": destination_services}))
            second_command = ["docker", "compose", "--project-name", second_project,
                              "-f", str(ROOT / "examples/production/compose.yaml"),
                              "-f", str(second_override), "--env-file", str(second_env)]
            second_volume = second_project + "_sidecar-data"
            second_client = client()
            try:
                image.run("docker", "volume", "create", second_volume, capture=True, timeout=20)
                image.run("docker", "run", "--rm", "--user", "0:0", "--volume",
                          f"{second_volume}:/sidecar/data", "--entrypoint", "sh", args.image,
                          "-c", "chown 10001:10001 /sidecar/data && chmod 700 /sidecar/data", timeout=30)
                image.run(*second_command, "up", "-d", "--build", env=second_environment, timeout=180)
                wait(lambda: send(second_client, second_origin + "/management/setup")[0] == 200,
                     "destination setup")
                page = need(*send(second_client, second_origin + "/management/setup"), 200)
                need(*send(second_client, second_origin + "/api/management/setup", "POST",
                           {"credential": second_setup, "email": "destination-admin@example.test",
                            "password": password, "confirmation": password}, csrf_from(page)), 202)
                browser(second_origin, "destination-admin@example.test", password)
                second_csrf = login(second_client, second_origin, "destination-admin@example.test", password)
                second_token = json.loads(need(*send(plain, f"http://127.0.0.1:{second_host_port}/token"), 200))[
                    "access_token"]
                second_current = json.loads(need(*send(second_client, second_origin +
                    "/api/management/configuration/current"), 200))["published"]["localId"]
                assert second_current != snapshot
                assert len(json.loads(need(*send(second_client, second_origin +
                    "/api/management/accounts"), 200))) == 1
                assert all(item["localId"] != snapshot for item in json.loads(need(*send(second_client,
                    second_origin + "/api/management/configuration/history"), 200)))

                held = draft(second_client, second_origin, second_csrf)
                before = json.loads(need(*send(second_client, second_origin +
                    "/api/management/editing/draft"), 200))
                bad_binding = json.loads(need(*multipart(second_client, second_origin +
                    "/api/management/configuration/import/review", exported, second_csrf), 200))
                assert not bad_binding["validation"]["successful"]
                assert json.loads(need(*send(second_client, second_origin +
                    "/api/management/editing/draft"), 200))["draftId"] == before["draftId"]
                assert json.loads(need(*send(second_client, second_origin +
                    "/api/management/configuration/current"), 200))["published"]["localId"] == second_current
                image.run(*second_command, "stop", "sidecar", env=second_environment, timeout=60)
                second_values["URL_VARIABLES"] = "TARGET_URL"
                second_env.write_text("".join(f"{key}={value}\n" for key, value in second_values.items()))
                second_environment.update(second_values)
                image.run(*second_command, "up", "-d", "--force-recreate", "sidecar",
                          env=second_environment, timeout=120)
                wait(lambda: send(second_client, second_origin + "/management/login")[0] == 200,
                     "destination with corrected binding")
                second_client = client()
                second_csrf = login(second_client, second_origin, "destination-admin@example.test", password)
                held = draft(second_client, second_origin, second_csrf)
                before = json.loads(need(*send(second_client, second_origin +
                    "/api/management/editing/draft"), 200))
                need(*multipart(second_client, second_origin + "/api/management/configuration/import/review",
                                b"not a ZIP", second_csrf), 400)
                assert json.loads(need(*send(second_client, second_origin +
                    "/api/management/editing/draft"), 200))["draftId"] == before["draftId"]
                assert json.loads(need(*send(second_client, second_origin +
                    "/api/management/configuration/current"), 200))["published"]["localId"] == second_current
                review = json.loads(need(*multipart(second_client, second_origin +
                    "/api/management/configuration/import/review", exported, second_csrf), 200))
                assert review["validation"]["successful"] and review["sourceSnapshotId"] == snapshot
                fields = held
                stale = dict(fields, baseSnapshotId=str(uuid.uuid4()))
                need(*multipart(second_client, second_origin + "/api/management/configuration/import/load",
                                exported, second_csrf, stale), 409)
                assert json.loads(need(*send(second_client, second_origin +
                    "/api/management/editing/draft"), 200))["draftId"] == before["draftId"]
                loaded = json.loads(need(*multipart(second_client, second_origin +
                    "/api/management/configuration/import/load", exported, second_csrf, fields), 200))
                assert loaded["revision"] > before["revision"] and loaded["sourceSnapshotId"] == snapshot
                assert json.loads(need(*send(second_client, second_origin +
                    "/api/management/configuration/current"), 200))["published"]["localId"] == second_current
                imported = publish_draft(second_client, second_origin, second_csrf, candidate(held, loaded))
                imported_id = imported["localId"]
                assert imported_id not in (snapshot, second_current) and imported["sourceId"] == snapshot
                assert all(item["localId"] != snapshot for item in json.loads(need(*send(second_client,
                    second_origin + "/api/management/configuration/history"), 200)))
                need(*send(second_client, second_origin + "/api/management/editing/draft"), 404)
                need(*send(second_client, second_origin + "/api/management/editing/lease/renew", "POST",
                           {key: held[key] for key in ("editingSessionId", "generation")}, second_csrf), 409)
                assert execute(second_client, second_origin, second_token,
                               "identityLeaf")["configurationSnapshotId"] == imported_id
                verified = json.loads(need(*send(plain, f"http://127.0.0.1:{second_host_port}/status"), 200))[
                    "verified"]
                assert verified and verified[-1]["path"] == "/callbacks/identity"
                assert verified[-1]["host"] == "fixture-destination:8081"
                imported_configuration = json.loads(need(*send(second_client, second_origin +
                    "/api/management/configuration/current"), 200))["published"]["configuration"]
                assert imported_configuration == source_configuration
                print("PASS independent destination import, authored ZIP, local ID, binding and draft cutover", flush=True)

                changed = publish_fixture(second_client, second_origin, second_csrf)
                rollback_held = draft(second_client, second_origin, second_csrf)
                rollback_review = json.loads(need(*send(second_client, second_origin +
                    f"/api/management/configuration/rollback/{imported_id}/review", "POST", {}, second_csrf), 200))
                assert rollback_review["validation"]["successful"]
                loaded = json.loads(need(*send(second_client, second_origin +
                    f"/api/management/configuration/rollback/{imported_id}/load", "POST",
                    rollback_held, second_csrf), 200))
                assert loaded["sourceSnapshotId"] == imported_id
                assert json.loads(need(*send(second_client, second_origin +
                    "/api/management/configuration/current"), 200))["published"]["localId"] == changed
                rolled = publish_draft(second_client, second_origin, second_csrf,
                                       candidate(rollback_held, loaded))
                assert rolled["localId"] not in (imported_id, changed, snapshot)
                assert rolled["sourceId"] == imported_id
                need(*send(second_client, second_origin + "/api/management/editing/draft"), 404)
                assert execute(second_client, second_origin, second_token,
                               "identityLeaf")["configurationSnapshotId"] == rolled["localId"]
                browser(second_origin, "destination-admin@example.test", password,
                        history_id=rolled["localId"])
                image.run(*second_command, "up", "-d", "--force-recreate", "sidecar",
                          env=second_environment, timeout=120)
                destination_container = image.run(*second_command, "ps", "-q", "sidecar",
                                                  env=second_environment, capture=True).stdout.strip()
                assert destination_container
                wait(lambda: "UP" in image.run("docker", "exec", destination_container, "curl", "--fail",
                                                 "--silent", "http://localhost:9091/actuator/health/readiness",
                                                 capture=True, timeout=10).stdout, "recreated destination readiness")
                assert execute(second_client, second_origin, second_token,
                               "identityLeaf")["configurationSnapshotId"] == rolled["localId"]
                print("PASS fresh local rollback and durable destination selection after recreation", flush=True)
            finally:
                image.require_cleanup(*second_command, "down", "--volumes", "--remove-orphans",
                                      env=second_environment)
                image.require_cleanup("docker", "volume", "rm", "--force", second_volume)

            image.run(*command, "stop", "sidecar", env=compose_environment, timeout=60)
            backup = root / "stopped-backup"
            backup.mkdir()
            image.run("docker", "run", "--rm", "--user", "0:0",
                      "--mount", f"type=volume,source={volume},target=/source,readonly",
                      "--mount", f"type=bind,source={backup},target=/backup",
                      "--entrypoint", "sh", args.image, "-c",
                      "cp -p /source/sidecar.db* /backup/ && test -f /backup/sidecar.db", timeout=30)
            source_set = set(image.run("docker", "run", "--rm", "--user", "0:0",
                                       "--mount", f"type=volume,source={volume},target=/source,readonly",
                                       "--entrypoint", "sh", args.image, "-c",
                                       "for f in /source/sidecar.db*; do basename \"$f\"; done",
                                       capture=True, timeout=30).stdout.splitlines())
            assert source_set == {item.name for item in backup.iterdir()} and "sidecar.db" in source_set
            assert (backup / "sidecar.db").stat().st_size > 0
            assert all(item.is_file() for item in backup.iterdir())

            image.run("docker", "run", "--rm", "--user", "10001:10001",
                      "--mount", f"type=volume,source={volume},target=/data",
                      "python:3.13-slim", "python", "-c",
                      "import sqlite3; db=sqlite3.connect('/data/sidecar.db'); "
                      "db.execute('UPDATE configuration_store_state SET current_snapshot_sequence=NULL WHERE singleton=1'); "
                      "db.commit(); db.close()", timeout=30)
            image.run(*command, "start", "sidecar", env=compose_environment, timeout=30)
            failed_container = image.run(*command, "ps", "-a", "-q", "sidecar",
                                         env=compose_environment, capture=True).stdout.strip()
            assert failed_container
            wait(lambda: image.run("docker", "inspect", "-f", "{{.State.Status}}", failed_container,
                                   capture=True).stdout.strip() == "exited", "invalid-selection startup failure")
            logs = image.run("docker", "logs", failed_container, capture=True, timeout=20).stdout
            assert "Cannot load selected configuration snapshot" in logs, logs[-1200:]
            try:
                status, _ = send(opener, origin + "/v1/skills", token=token)
                assert status in (502, 503), f"Invalid selected state admitted dispatch: {status}"
            except (OSError, urllib.error.URLError):
                pass
            diagnostic = root / "unusable-database"
            diagnostic.mkdir()
            image.run("docker", "run", "--rm", "--user", "0:0",
                      "--mount", f"type=volume,source={volume},target=/source,readonly",
                      "--mount", f"type=bind,source={diagnostic},target=/diagnostic",
                      "--entrypoint", "sh", args.image, "-c",
                      "cp -p /source/sidecar.db* /diagnostic/", timeout=30)
            assert (diagnostic / "sidecar.db").is_file()
            print("PASS unusable selected state fails startup and dispatch; diagnostic copied before restore", flush=True)

            image.run(*command, "rm", "-f", "sidecar", env=compose_environment, timeout=30)
            image.run("docker", "volume", "rm", volume, timeout=30)
            image.run("docker", "volume", "create", volume, capture=True, timeout=30)
            image.run("docker", "run", "--rm", "--user", "0:0",
                      "--mount", f"type=volume,source={volume},target=/sidecar/data",
                      "--entrypoint", "sh", args.image, "-c",
                      "printf stale > /sidecar/data/sidecar.db-wal && printf stale > /sidecar/data/sidecar.db-shm",
                      timeout=30)
            image.run("docker", "run", "--rm", "--user", "0:0",
                      "--mount", f"type=volume,source={volume},target=/sidecar/data",
                      "--mount", f"type=bind,source={backup},target=/backup,readonly",
                      "--entrypoint", "sh", args.image, "-c",
                      "rm -f /sidecar/data/sidecar.db /sidecar/data/sidecar.db-wal /sidecar/data/sidecar.db-shm && "
                      "cp -p /backup/sidecar.db* /sidecar/data/ && "
                      "chown -R 10001:10001 /sidecar/data && chmod 700 /sidecar/data && "
                      "for f in /sidecar/data/sidecar.db*; do test \"$(stat -c %u:%g \"$f\")\" = 10001:10001; done",
                      timeout=30)
            restored_set = set(image.run("docker", "run", "--rm", "--user", "0:0",
                                         "--mount", f"type=volume,source={volume},target=/data,readonly",
                                         "--entrypoint", "sh", args.image, "-c",
                                         "for f in /data/sidecar.db*; do basename \"$f\"; done",
                                         capture=True, timeout=30).stdout.splitlines())
            assert restored_set == source_set
            image.run(*command, "up", "-d", "sidecar", env=compose_environment, timeout=120)
            restored_container = image.run(*command, "ps", "-q", "sidecar",
                                           env=compose_environment, capture=True).stdout.strip()
            assert restored_container
            wait(lambda: "UP" in image.run("docker", "exec", restored_container, "curl", "--fail", "--silent",
                                            "http://localhost:9091/actuator/health/readiness",
                                            capture=True, timeout=10).stdout, "restored readiness")
            browser(origin, email, password)
            restored = client()
            login(restored, origin, email, password)
            current = json.loads(need(*send(restored, origin + "/api/management/configuration/current"), 200))
            assert current["published"]["localId"] == current["intendedId"] == snapshot
            history = json.loads(need(*send(restored, origin + "/api/management/configuration/history"), 200))
            assert any(item["localId"] == snapshot for item in history)
            assert any(item["email"] == email and item["role"] == "admin" for item in
                       json.loads(need(*send(restored, origin + "/api/management/accounts"), 200)))
            assert execute(opener, origin, token, "identityLeaf")["configurationSnapshotId"] == snapshot
            assert execute(opener, origin, token, "quickstartPlanner")["configurationSnapshotId"] == snapshot
            print("PASS stopped database-set restore with UID 10001 account/config activation", flush=True)

            busy_reset = image.run(*command, "run", "--rm", "--no-deps", "sidecar",
                                   "admin", "issue-password-reset", "--database", "/sidecar/data/sidecar.db",
                                   "--email", email, env=compose_environment, capture=True,
                                   check=False, timeout=60)
            assert busy_reset.returncode != 0 and not busy_reset.stdout
            image.run(*command, "stop", "sidecar", env=compose_environment, timeout=60)
            offline_reset = image.run(*command, "run", "--rm", "--no-deps", "sidecar",
                                      "admin", "issue-password-reset", "--database", "/sidecar/data/sidecar.db",
                                      "--email", email, env=compose_environment, capture=True, timeout=60)
            assert re.fullmatch(r"[A-Za-z0-9_-]{43}\n?", offline_reset.stdout)
            image.run(*command, "start", "sidecar", env=compose_environment, timeout=60)
            wait(lambda: send(client(), origin + "/management/password/reset")[0] == 200,
                 "manual reset page")
            recovery_client = client()
            manual_page = need(*send(recovery_client, origin + "/management/password/reset"), 200)
            assert "name='token'" in manual_page and "type='password'" in manual_page
            replacement = "Final Recovery Password 1!"
            need(*send(recovery_client, origin + "/api/management/password/reset", "POST",
                       {"token": offline_reset.stdout.strip(), "password": replacement},
                       csrf_from(manual_page)), 204)
            assert login(client(), origin, email, replacement)
            print("PASS production stopped-volume offline reset and manual redemption", flush=True)

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
