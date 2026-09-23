#!/usr/bin/env python3
"""Verify Caddy certificate modes with disposable containers and local TLS clients.

No public CA, DNS API, customer service, or real mail is contacted.
"""

import argparse
import http.client
import pathlib
import socket
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "examples/production"


def run(*args, capture=False):
    result = subprocess.run(args, check=True, capture_output=capture, text=True)
    return result.stdout if capture else ""


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def handshake(port, context, headers=b"", host_port=None):
    with socket.create_connection(("127.0.0.1", port), timeout=5) as raw:
        with context.wrap_socket(raw, server_hostname="localhost") as stream:
            stream.sendall(b"GET / HTTP/1.1\r\nHost: localhost:" + str(host_port or port).encode()
                           + b"\r\nConnection: close\r\n" + headers + b"\r\n")
            chunks = []
            while chunk := stream.recv(2048):
                chunks.append(chunk)
            response = b"".join(chunks)
            assert b"200 OK" in response, response
            return stream.getpeercert(binary_form=True), response


def wait_handshake(port, context):
    deadline = time.monotonic() + 40
    last = None
    while time.monotonic() < deadline:
        try:
            return handshake(port, context)
        except (OSError, AssertionError) as failure:
            last = failure
            time.sleep(0.5)
    raise AssertionError(f"Caddy TLS not ready: {last}")


def cert(directory):
    run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
        "-keyout", str(directory / "privkey.pem"), "-out", str(directory / "fullchain.pem"),
        "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True, help="Existing Sidecar image; checked in production Compose configuration")
    parser.add_argument("--dns-image", default="loomspan-caddy-cloudflare:local")
    args = parser.parse_args()
    run("docker", "image", "inspect", args.image, capture=True)
    tag = "tls-fixture-" + uuid.uuid4().hex[:12]
    network, volume = tag + "-network", tag + "-data"
    backend, caddy = tag + "-sidecar", tag + "-caddy"
    direct, direct_volume = tag + "-direct", tag + "-direct-data"
    port = free_port()
    http_port = free_port()
    assert port != http_port
    with tempfile.TemporaryDirectory(prefix="sidecar-tls-") as directory:
        temp = pathlib.Path(directory)
        run("docker", "network", "create", network)
        run("docker", "volume", "create", volume)
        try:
            # Production Compose is the source for network, port, and persistent state assertions.
            env = CONFIG / "production.env.example"
            config = run("docker", "compose", "--env-file", str(env), "-f",
                         str(CONFIG / "compose.yaml"), "config", "--format", "json", capture=True)
            import json
            services = json.loads(config)["services"]
            assert not services["sidecar"].get("ports")
            assert {v["target"] for v in services["caddy"]["volumes"]} >= {"/data", "/config"}
            assert services["sidecar"]["environment"]["SERVER_SSL_ENABLED"] == "false"
            supplied_config = run("docker", "compose", "--env-file", str(env), "-f",
                                  str(CONFIG / "compose.yaml"), "-f",
                                  str(CONFIG / "compose.supplied.yaml"), "config", "--format", "json",
                                  capture=True)
            supplied_mounts = json.loads(supplied_config)["services"]["caddy"]["volumes"]
            assert any(v["target"] == "/etc/caddy/certs" and v["read_only"] for v in supplied_mounts)
            # Local stub occupies the same upstream name and port as Sidecar.
            echo = """from http.server import BaseHTTPRequestHandler, HTTPServer
import json
class H(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(dict(self.headers)).encode()
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
HTTPServer(('0.0.0.0', 8080), H).serve_forever()
"""
            run("docker", "run", "-d", "--name", backend, "--network", network,
                "--network-alias", "sidecar", "python:3.13-slim", "python", "-c", echo)

            def start(caddyfile, image, cert_dir=None):
                command = ["docker", "run", "-d", "--name", caddy, "--network", network,
                           "-p", f"127.0.0.1:{port}:{port}", "-p", f"127.0.0.1:{http_port}:80",
                           "-e", "SITE_HOST=localhost",
                           "-e", f"HTTPS_PORT={port}", "-v", f"{volume}:/data",
                           "-v", f"{caddyfile}:/etc/caddy/Caddyfile:ro"]
                if cert_dir:
                    command += ["-v", f"{cert_dir}:/etc/caddy/certs:ro"]
                run(*command, image)

            unverified = ssl._create_unverified_context()
            start(CONFIG / "Caddyfile.internal", "caddy:2.10.2")
            wait_handshake(port, unverified)
            connection = http.client.HTTPConnection("127.0.0.1", http_port, timeout=5)
            connection.request("GET", "/management/login", headers={"Host": "localhost:1",
                                                                    "Forwarded": "host=evil.example;proto=http",
                                                                    "X-Forwarded-Host": "evil.example"})
            redirect = connection.getresponse()
            assert redirect.status in (301, 302, 307, 308)
            assert redirect.getheader("Location") == f"https://localhost:{port}/management/login"
            connection.close()
            root = run("docker", "exec", caddy, "cat", "/data/caddy/pki/authorities/local/root.crt",
                       capture=True)
            root_key_mode = run("docker", "exec", caddy, "stat", "-c", "%a",
                                "/data/caddy/pki/authorities/local/root.key", capture=True).strip()
            assert int(root_key_mode, 8) & 0o077 == 0, root_key_mode
            root_file = temp / "root.crt"
            root_file.write_text(root)
            trusted = ssl.create_default_context(cafile=str(root_file))
            first_leaf, response = handshake(port, trusted, b"Forwarded: host=evil.example;proto=http\r\n"
                                                     b"X-Forwarded-Host: evil.example\r\n"
                                                     b"X-Forwarded-Proto: http\r\n"
                                                     b"X-Forwarded-Port: 1\r\n"
                                                     b"X-Forwarded-Prefix: /evil\r\n", host_port=1)
            headers = json.loads(response.split(b"\r\n\r\n", 1)[1])
            assert "Forwarded" not in headers and "X-Forwarded-Prefix" not in headers, headers
            assert headers["X-Forwarded-Proto"] == "https", headers
            assert headers["X-Forwarded-Host"] == f"localhost:{port}", headers
            assert headers["Host"] == f"localhost:{port}", headers
            assert "evil.example" not in str(headers) and "X-Forwarded-Port" not in headers, headers
            try:
                handshake(port, ssl.create_default_context())
                raise AssertionError("Untrusted client accepted private CA")
            except ssl.SSLCertVerificationError:
                pass
            run("docker", "rm", "-f", caddy)
            start(CONFIG / "Caddyfile.internal", "caddy:2.10.2")
            assert wait_handshake(port, trusted)[0] == first_leaf
            assert run("docker", "exec", caddy, "cat",
                       "/data/caddy/pki/authorities/local/root.crt", capture=True) == root
            run("docker", "rm", "-f", caddy)
            # Remove only this disposable fixture's managed leaf. Caddy must
            # issue a replacement from the persisted CA without operator PEMs.
            leaf_path = "/data/caddy/certificates/local/localhost/localhost"
            run("docker", "run", "--rm", "-v", f"{volume}:/data", "alpine:3.20", "sh", "-c",
                f"test -f {leaf_path}.crt && rm -f {leaf_path}.crt {leaf_path}.key {leaf_path}.json")
            start(CONFIG / "Caddyfile.internal", "caddy:2.10.2")
            renewed_leaf = wait_handshake(port, trusted)[0]
            assert renewed_leaf != first_leaf
            assert run("docker", "exec", caddy, "cat",
                       "/data/caddy/pki/authorities/local/root.crt", capture=True) == root
            print("PASS private CA trust, recreation and automatic leaf reissuance after fixture leaf loss")

            run("docker", "rm", "-f", caddy)
            cert_dir = temp / "certs"
            cert_dir.mkdir()
            cert(cert_dir)
            start(CONFIG / "Caddyfile.supplied", "caddy:2.10.2", cert_dir)
            supplied = ssl.create_default_context(cafile=str(cert_dir / "fullchain.pem"))
            old_leaf = wait_handshake(port, supplied)[0]
            cert(cert_dir)
            # Force reprovisioning when the Caddyfile text is unchanged.
            run("docker", "exec", caddy, "caddy", "reload", "--force",
                "--config", "/etc/caddy/Caddyfile")
            replaced = ssl.create_default_context(cafile=str(cert_dir / "fullchain.pem"))
            deadline = time.monotonic() + 15
            while True:
                try:
                    new_leaf = handshake(port, replaced)[0]
                    if new_leaf != old_leaf:
                        break
                except ssl.SSLCertVerificationError:
                    pass
                if time.monotonic() > deadline:
                    raise AssertionError("Caddy did not serve replacement certificate")
                time.sleep(0.5)
            print("PASS supplied certificate replacement after forced Caddy reload")

            # The application image still works directly, including standard
            # Spring PEM TLS, with no Caddy container in its request path.
            (cert_dir / "privkey.pem").chmod(0o644)  # Disposable fixture only.
            run("docker", "volume", "create", direct_volume)
            run("docker", "run", "--rm", "--user", "0:0", "-v",
                f"{direct_volume}:/sidecar/data", "--entrypoint", "sh", args.image,
                "-c", "chown 10001:10001 /sidecar/data && chmod 700 /sidecar/data")
            direct_port = free_port()
            direct_args = ["docker", "run", "-d", "--name", direct, "--user", "10001:10001",
                           "-p", f"127.0.0.1:{direct_port}:8080", "-v", f"{direct_volume}:/sidecar/data",
                           "-v", f"{cert_dir}:/sidecar/tls:ro",
                           "-v", f"{ROOT / 'examples/quickstart/host/public.pem'}:/sidecar/keys/public.pem:ro",
                           "-e", "MANAGEMENT_SERVER_SSL_ENABLED=false",
                           "-e", "LOOMSPAN_SIDECAR_AUTH_JWT_ISSUER_URI=https://issuer.example.test",
                           "-e", "LOOMSPAN_SIDECAR_AUTH_JWT_AUDIENCE=loomspan-sidecar",
                           "-e", "LOOMSPAN_SIDECAR_AUTH_JWT_PUBLIC_KEY_LOCATION=file:/sidecar/keys/public.pem",
                           "-e", "LOOMSPAN_CONNECTIONS_PRIMARY_DRIVER=openai",
                           "-e", "LOOMSPAN_CONNECTIONS_PRIMARY_BASE_URL=https://model.example.test/v1",
                           "-e", "LOOMSPAN_CONNECTIONS_PRIMARY_API_KEY=fixture-only",
                           "-e", "LOOMSPAN_MODELS_PRIMARY_CONNECTION=primary",
                           "-e", "LOOMSPAN_MODELS_PRIMARY_PROVIDER_MODEL=fixture",
                           "-e", "LOOMSPAN_SIDECAR_MAIL_FROM=sidecar@example.test",
                           "-e", f"LOOMSPAN_SIDECAR_EXTERNAL_BASE_URL=https://localhost:{direct_port}",
                           "-e", "LOOMSPAN_SIDECAR_SMTP_HOST=127.0.0.1", "-e", "LOOMSPAN_SIDECAR_SMTP_PORT=2525"]
            run(*direct_args, "-e", "SERVER_SSL_ENABLED=false", args.image)
            deadline = time.monotonic() + 45
            while True:
                try:
                    with urllib.request.urlopen(f"http://127.0.0.1:{direct_port}/management/login",
                                                timeout=2) as response:
                        assert response.status == 200
                    break
                except (OSError, urllib.error.URLError):
                    if time.monotonic() > deadline:
                        raise AssertionError("Direct localhost HTTP did not start")
                    time.sleep(0.5)
            run("docker", "rm", "-f", direct)
            run(*direct_args, "-e", "SERVER_SSL_ENABLED=true",
                "-e", "SERVER_SSL_CERTIFICATE=file:/sidecar/tls/fullchain.pem",
                "-e", "SERVER_SSL_CERTIFICATE_PRIVATE_KEY=file:/sidecar/tls/privkey.pem", args.image)
            direct_trust = ssl.create_default_context(cafile=str(cert_dir / "fullchain.pem"))
            deadline = time.monotonic() + 45
            while True:
                try:
                    with urllib.request.urlopen(f"https://localhost:{direct_port}/management/login",
                                                context=direct_trust, timeout=2) as response:
                        assert response.status == 200
                    break
                except (OSError, urllib.error.URLError):
                    if time.monotonic() > deadline:
                        raise AssertionError("Direct supplied application TLS did not start")
                    time.sleep(0.5)
            print("PASS direct localhost HTTP and supplied application TLS without Caddy")

            for filename, image in (("Caddyfile.public", "caddy:2.10.2"),
                                    ("Caddyfile.dns-cloudflare", args.dns_image)):
                adapted = run("docker", "run", "--rm", "-e", "SITE_HOST=example.com",
                              "-e", "HTTPS_PORT=443", "-e", "CF_API_TOKEN=fixture-only",
                              "-v", f"{CONFIG / filename}:/etc/caddy/Caddyfile:ro", image,
                              "caddy", "adapt", "--config", "/etc/caddy/Caddyfile", capture=True)
                parsed = json.loads(adapted)
                servers = parsed["apps"]["http"]["servers"]
                assert servers and '"example.com"' in adapted
                assert '"X-Forwarded-*"' in adapted and '"Forwarded"' in adapted
                assert '"X-Forwarded-Host"' in adapted and '"example.com:443"' in adapted
                assert '"X-Forwarded-Proto"' in adapted and '"https"' in adapted
                if filename.endswith("dns-cloudflare"):
                    issuer = parsed["apps"]["tls"]["automation"]["policies"][0]["issuers"][0]
                    assert issuer["module"] == "acme"
                    assert issuer["challenges"]["dns"]["provider"] == {
                        "name": "cloudflare", "api_token": "{env.CF_API_TOKEN}"}
                else:
                    assert "dns" not in parsed.get("apps", {}).get("tls", {})
            print("PASS public and Cloudflare DNS Caddyfile adaptation; no live CA/provider calls")
        finally:
            subprocess.run(["docker", "rm", "-f", caddy, backend, direct], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL)
            subprocess.run(["docker", "network", "rm", network], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL)
            subprocess.run(["docker", "volume", "rm", volume], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL)
            subprocess.run(["docker", "volume", "rm", direct_volume], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
