#!/usr/bin/env python3
"""Bounded shipped production-recipe smoke; requires Docker Compose and OpenSSL."""

import argparse
import base64
import http.client
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import re
import secrets
import socket
import ssl
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]


class Failure(Exception):
    """Fixed diagnostic categories only; no command output or response payloads."""


def environment():
    allowed = {
        "PATH",
        "SYSTEMROOT",
        "SYSTEMDRIVE",
        "WINDIR",
        "COMSPEC",
        "PATHEXT",
        "HOME",
        "USERPROFILE",
        "APPDATA",
        "LOCALAPPDATA",
        "TEMP",
        "TMP",
        "DOCKER_CONFIG",
        "DOCKER_HOST",
        "DOCKER_CONTEXT",
        "PROGRAMFILES",
        "PROGRAMDATA",
    }
    return {key: value for key, value in os.environ.items() if key.upper() in allowed}


def command(args, timeout=60, data=None, binary=False):
    try:
        result = subprocess.run(
            list(map(str, args)),
            input=data,
            capture_output=True,
            text=not binary,
            timeout=timeout,
            env=environment(),
        )
    except subprocess.TimeoutExpired:
        raise Failure("command-timeout") from None
    except OSError:
        raise Failure("command-unavailable") from None
    if result.returncode:
        raise Failure("command-failed")
    return result.stdout


def require(condition, category):
    if not condition:
        raise Failure(category)


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def encode(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def fixtures(directory):
    tls = directory / "tls"
    keys = directory / "keys"
    tls.mkdir()
    keys.mkdir()
    command(
        [
            "openssl",
            "req",
            "-x509",
            "-newkey",
            "rsa:2048",
            "-nodes",
            "-days",
            "1",
            "-keyout",
            tls / "privkey.pem",
            "-out",
            tls / "fullchain.pem",
            "-subj",
            "/CN=localhost",
            "-addext",
            "subjectAltName=DNS:localhost,IP:127.0.0.1",
        ]
    )
    private = directory / "signer.pem"
    command(
        [
            "openssl",
            "genpkey",
            "-algorithm",
            "RSA",
            "-pkeyopt",
            "rsa_keygen_bits:2048",
            "-out",
            private,
        ]
    )
    modulus = (
        command(["openssl", "rsa", "-in", private, "-noout", "-modulus"])
        .strip()
        .split("=", 1)[1]
    )
    (keys / "jwks.json").write_text(
        json.dumps(
            {
                "keys": [
                    {
                        "kty": "RSA",
                        "kid": "deployment",
                        "alg": "RS256",
                        "use": "sig",
                        "n": encode(bytes.fromhex(modulus)).decode(),
                        "e": "AQAB",
                    }
                ]
            }
        ),
        encoding="utf-8",
    )
    now = int(time.time())
    header = encode(
        json.dumps(
            {"alg": "RS256", "typ": "loomspan-execution+jwt", "kid": "deployment"}
        ).encode()
    )
    claims = encode(
        json.dumps(
            {
                "iss": "https://deployment.invalid",
                "sub": "smoke",
                "aud": "loomspan-sidecar",
                "token_use": "loomspan_execution",
                "iat": now,
                "exp": now + 600,
                "roles": [],
            }
        ).encode()
    )
    signing = header + b"." + claims
    signature = command(
        ["openssl", "dgst", "-sha256", "-sign", private], data=signing, binary=True
    )
    return (signing + b"." + encode(signature)).decode()


class Runtime:
    def __init__(self, directory, image):
        self.project = "sidecar-smoke-" + uuid.uuid4().hex[:16]
        self.image = image
        self.token = fixtures(directory)
        self.tls = directory / "tls"
        self.port = free_port()
        http_port = free_port()
        while self.port == http_port:
            http_port = free_port()
        production = ROOT / "examples/production"
        values = {
            "SIDECAR_IMAGE": image,
            "SITE_HOST": "localhost",
            "HTTPS_PORT": str(self.port),
            "HTTP_PORT": str(http_port),
            "HTTPS_BIND_ADDRESS": "127.0.0.1",
            "HTTP_BIND_ADDRESS": "127.0.0.1",
            "CADDY_CONFIG_FILE": (production / "Caddyfile.supplied").as_posix(),
            "TLS_DIR": (directory / "tls").as_posix(),
            "JWT_KEY_DIRECTORY": (directory / "keys").as_posix(),
            "EXTERNAL_BASE_URL": f"https://localhost:{self.port}",
            "LOOMSPAN_SIDECAR_CREDENTIAL_KEY": base64.b64encode(
                secrets.token_bytes(32)
            ).decode(),
        }
        envfile = directory / "compose.env"
        envfile.write_text(
            "".join(f"{key}={value}\n" for key, value in values.items()),
            encoding="utf-8",
        )
        # Startup-only deterministic model settings. No execution/model request is made.
        override = directory / "smoke.json"
        override.write_text(
            json.dumps(
                {
                    "services": {
                        "sidecar": {
                            "environment": {
                                "LOOMSPAN_CONNECTIONS_PRIMARY_DRIVER": "openai",
                                "LOOMSPAN_CONNECTIONS_PRIMARY_BASE_URL": "http://127.0.0.1:9/v1",
                                "LOOMSPAN_CONNECTIONS_PRIMARY_API_KEY": "local-unused",
                                "LOOMSPAN_MODELS_PRIMARY_CONNECTION": "primary",
                                "LOOMSPAN_MODELS_PRIMARY_PROVIDER_MODEL": "unused",
                            }
                        }
                    }
                }
            ),
            encoding="utf-8",
        )
        self.compose = [
            "docker",
            "compose",
            "--project-directory",
            str(production),
            "--env-file",
            str(envfile),
            "-p",
            self.project,
            "-f",
            str(production / "compose.yaml"),
            "-f",
            str(production / "compose.supplied.yaml"),
            "-f",
            str(override),
        ]
        self.context = ssl.create_default_context(
            cafile=str(directory / "tls/fullchain.pem")
        )

    def inspect(self):
        (image,) = json.loads(command(["docker", "image", "inspect", self.image]))
        require(image["Config"]["User"] == "10001:10001", "image-user")
        require(
            any(
                value.startswith("JAVA_TOOL_OPTIONS=")
                and "MaxRAMPercentage" in value
                and "ExitOnOutOfMemoryError" in value
                for value in image["Config"]["Env"]
            ),
            "image-memory-options",
        )
        config = json.loads(command(self.compose + ["config", "--format", "json"]))
        services = config["services"]
        require(set(services) == {"sidecar", "caddy"}, "recipe-services")
        sidecar, caddy = services["sidecar"], services["caddy"]
        require(
            caddy["network_mode"] == "service:sidecar" and not caddy.get("ports"),
            "recipe-namespace",
        )
        require(
            sidecar["environment"]["SERVER_ADDRESS"] == "127.0.0.1"
            and sidecar["environment"]["SERVER_SSL_ENABLED"] == "false",
            "recipe-listener",
        )
        require(
            not any(
                key.startswith("MANAGEMENT_SERVER_") for key in sidecar["environment"]
            ),
            "recipe-health-listener",
        )
        require(
            len(sidecar["ports"]) == 2
            and {int(port["target"]) for port in sidecar["ports"]} == {80, self.port}
            and all(port["host_ip"] == "127.0.0.1" for port in sidecar["ports"]),
            "recipe-published-ingress",
        )
        require(
            sidecar["user"] == "10001:10001"
            and sidecar["stop_grace_period"] in ("45s", 45000000000),
            "recipe-user-grace",
        )
        mounts = {mount["target"]: mount for mount in sidecar["volumes"]}
        require(
            mounts["/sidecar/keys"]["read_only"]
            and mounts["/sidecar/data"]["type"] == "volume",
            "recipe-storage",
        )
        require(
            any(
                mount["target"] == "/etc/caddy/certs" and mount["read_only"]
                for mount in caddy["volumes"]
            ),
            "recipe-tls-mount",
        )
        require(
            sidecar["healthcheck"]["test"][-1]
            == "http://localhost:8080/actuator/health/readiness",
            "recipe-health",
        )

    def start(self):
        volume = self.project + "_sidecar-data"
        command(
            [
                "docker",
                "volume",
                "create",
                "--label",
                "com.docker.compose.project=" + self.project,
                volume,
            ]
        )
        command(
            [
                "docker",
                "run",
                "--rm",
                "--label",
                "com.docker.compose.project=" + self.project,
                "--user",
                "0:0",
                "--volume",
                volume + ":/sidecar/data",
                "--entrypoint",
                "sh",
                self.image,
                "-c",
                "chown 10001:10001 /sidecar/data && chmod 700 /sidecar/data",
            ]
        )
        command(self.compose + ["up", "-d"], timeout=180)

    def request(self, path, token=None):
        # Keep the fixed IPv4 endpoint and the shipped localhost TLS server name.
        with socket.create_connection(("127.0.0.1", self.port), timeout=3) as raw:
            with self.context.wrap_socket(raw, server_hostname="localhost") as stream:
                headers = f"GET {path} HTTP/1.1\r\nHost: localhost:{self.port}\r\nConnection: close\r\n"
                if token:
                    headers += "Authorization: Bearer " + token + "\r\n"
                stream.sendall((headers + "\r\n").encode())
                response = http.client.HTTPResponse(stream)
                response.begin()
                return (
                    response.status,
                    response.read(65536),
                    response.getheader("Set-Cookie"),
                )

    def assertions(self):
        deadline = time.monotonic() + 120
        while True:
            try:
                status, body, _ = self.request("/actuator/health/readiness")
                if status == 200 and json.loads(body) == {"status": "UP"}:
                    break
            except (OSError, ValueError, http.client.HTTPException):
                pass
            if time.monotonic() >= deadline:
                raise Failure("readiness-timeout")
            time.sleep(1)
        for group in ("readiness", "liveness"):
            status, body, _ = self.request("/actuator/health/" + group)
            require(
                status == 200 and json.loads(body) == {"status": "UP"},
                "anonymous-health",
            )
        status, body, _ = self.request("/v1/skills", self.token)
        require(status == 200 and json.loads(body) == [], "authenticated-catalog")
        require(self.request("/v1/skills")[0] == 401, "missing-jwt")
        status, _, header = self.request("/management/login")
        cookies = SimpleCookie()
        cookies.load(header or "")
        require(
            status == 200
            and "JSESSIONID" in cookies
            and cookies["JSESSIONID"]["secure"],
            "secure-session-cookie",
        )
        command(
            self.compose
            + [
                "exec",
                "-T",
                "sidecar",
                "sh",
                "-c",
                "test -s /sidecar/data/sidecar.db && test -w /sidecar/data && test $(stat -c %u /sidecar/data/sidecar.db) = 10001",
            ]
        )
        # Separate network namespace: ingress succeeds, backend listener refuses.
        code = """import socket,ssl,http.client,json
context=ssl.create_default_context(cafile='/tls/fullchain.pem')
with socket.create_connection(('sidecar',PORT),timeout=3) as raw:
    with context.wrap_socket(raw,server_hostname='localhost') as stream:
        stream.sendall(b'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost:PORT\\r\\nConnection: close\\r\\n\\r\\n')
        response=http.client.HTTPResponse(stream);response.begin()
        assert response.status == 200 and json.loads(response.read()) == {'status':'UP'}
try:
    with socket.create_connection(('sidecar',8080),timeout=3): pass
    raise AssertionError('backend bypass')
except ConnectionRefusedError: pass
""".replace("PORT", str(self.port))
        command(
            [
                "docker",
                "run",
                "--rm",
                "--label",
                "com.docker.compose.project=" + self.project,
                "--network",
                self.project + "_backend",
                "-v",
                str(self.tls) + ":/tls:ro",
                "python:3.13-slim",
                "python",
                "-c",
                code,
            ],
            timeout=30,
        )

    def diagnostics(self):
        states = command(self.compose + ["ps", "--all", "--format", "json"], timeout=15)
        # Fixed summary only. Raw state may contain untrusted data and stays captured.
        return {"stateCollected": bool(states)}

    def cleanup(self):
        failed = False
        try:
            command(
                self.compose
                + ["down", "--timeout", "20", "--volumes", "--remove-orphans"],
                timeout=90,
            )
        except Exception:
            failed = True
        # Also reclaim this run's labeled helper/partial-start resources. Continue
        # through every resource kind even if down, listing or removal fails.
        for resource in ("container", "network", "volume"):
            try:
                listing = [
                    "docker",
                    resource,
                    "ls",
                    "-q",
                    "--filter",
                    "label=com.docker.compose.project=" + self.project,
                ]
                ids = command(listing, timeout=15).split()
                if ids:
                    removal = (
                        ["docker", resource, "rm"]
                        + (["-f"] if resource == "container" else [])
                        + ids
                    )
                    command(removal, timeout=30)
                    require(
                        not command(listing, timeout=15).strip(), "cleanup-residual"
                    )
            except Exception:
                failed = True
        require(not failed, "cleanup-failed")


def verify(runtime):
    report = {"status": "failed", "diagnostic": None, "cleanup": "not-run"}
    started = time.monotonic()
    try:
        runtime.inspect()
        runtime.start()
        runtime.assertions()
        report["status"] = "passed"
    except KeyboardInterrupt:
        report["diagnostic"] = "interrupted"
    except Failure as failure:
        report["diagnostic"] = str(failure)
    except Exception:
        report["diagnostic"] = "assertion-failed"
    finally:
        try:
            report["state"] = runtime.diagnostics()
        except BaseException as failure:
            report["state"] = {"stateCollected": False}
            if isinstance(failure, KeyboardInterrupt):
                report["status"] = "failed"
                report["diagnostic"] = report["diagnostic"] or "interrupted"
        try:
            runtime.cleanup()
            report["cleanup"] = "passed"
        except BaseException:
            report["cleanup"] = "failed"
            report["status"] = "failed"
            report["diagnostic"] = report["diagnostic"] or "cleanup-failed"
    report["elapsedSeconds"] = round(time.monotonic() - started, 3)
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_./:@-]*", args.image):
        parser.error("invalid image reference")
    try:
        with tempfile.TemporaryDirectory(prefix="sidecar-deployment-") as temporary:
            report = verify(Runtime(Path(temporary), args.image))
    except Failure as failure:
        report = {"status": "failed", "diagnostic": str(failure)}
    except Exception:
        report = {"status": "failed", "diagnostic": "fixture-failed"}
    print(json.dumps(report))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
