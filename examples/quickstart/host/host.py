#!/usr/bin/env python3
"""Local-only JWT issuer, OpenAI-compatible planner, and verifying callback host."""

import base64
import hashlib
import json
import os
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ISSUER = os.environ.get("QUICKSTART_ISSUER", "loomspan-app")
AUDIENCE = os.environ.get("QUICKSTART_AUDIENCE", "loomspan-sidecar")
KEY_DIRECTORY = os.environ.get("QUICKSTART_KEY_DIRECTORY", "/keys")
SIGNING_DIRECTORY = tempfile.TemporaryDirectory(prefix="quickstart-signing-")
PRIVATE_KEY = os.path.join(SIGNING_DIRECTORY.name, "private.pem")
PUBLIC_KEY = os.path.join(SIGNING_DIRECTORY.name, "public.pem")


def export_fresh_key():
    # Demo-only ephemeral signer; the deployment creates KEY_DIRECTORY and its permissions.
    subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                    "-out", PRIVATE_KEY], check=True, capture_output=True)
    subprocess.run(["openssl", "pkey", "-in", PRIVATE_KEY, "-pubout", "-out", PUBLIC_KEY], check=True, capture_output=True)
    modulus = subprocess.run(["openssl", "rsa", "-in", PRIVATE_KEY, "-noout", "-modulus"],
                             check=True, capture_output=True, text=True).stdout.strip().split("=", 1)[1]
    public = {"kty": "RSA", "kid": "quickstart", "alg": "RS256", "use": "sig",
              "n": b64url(bytes.fromhex(modulus)), "e": "AQAB"}
    with tempfile.NamedTemporaryFile(mode="w", dir=KEY_DIRECTORY, delete=False) as output:
        json.dump({"keys": [public]}, output); output.flush(); os.fsync(output.fileno())
        os.chmod(output.name, 0o644)
    os.replace(output.name, os.path.join(KEY_DIRECTORY, "jwks.json"))
RESPONSES = []
VERIFIED = []
LOCK = threading.Lock()


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def decode(part):
    return base64.urlsafe_b64decode(part + "=" * (-len(part) % 4))


def issue(subject="quickstart-user"):
    now = int(time.time())
    header = b64url(json.dumps({"alg": "RS256", "typ": "loomspan-execution+jwt", "kid": "quickstart"}, separators=(",", ":")).encode())
    claims = b64url(json.dumps({"iss": ISSUER, "sub": subject, "aud": AUDIENCE,
                                "iat": now, "exp": now + 600, "token_use": "loomspan_execution",
                                "roles": ["QUICKSTART_USER"]}, separators=(",", ":")).encode())
    signing_input = f"{header}.{claims}".encode()
    signature = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", PRIVATE_KEY],
        input=signing_input, check=True, capture_output=True).stdout
    return f"{header}.{claims}.{b64url(signature)}"


def verify(token):
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("malformed JWT")
    signing_input = f"{parts[0]}.{parts[1]}".encode()
    with tempfile.NamedTemporaryFile() as signature:
        signature.write(decode(parts[2])); signature.flush()
        checked = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", PUBLIC_KEY,
             "-signature", signature.name], input=signing_input, capture_output=True)
    if checked.returncode:
        raise ValueError("invalid signature")
    header = json.loads(decode(parts[0]))
    if header.get("alg") != "RS256" or header.get("typ") != "loomspan-execution+jwt" or header.get("kid") != "quickstart":
        raise ValueError("invalid header")
    claims = json.loads(decode(parts[1]))
    if (claims.get("token_use") != "loomspan_execution" or claims.get("iss") != ISSUER or AUDIENCE not in ([claims.get("aud")] if isinstance(claims.get("aud"), str)
                                                         else claims.get("aud", []))
            or not claims.get("sub") or int(claims.get("exp", 0)) <= int(time.time())
            or "QUICKSTART_USER" not in claims.get("roles", [])):
        raise ValueError("invalid claims")
    return claims


def completion(content):
    return {"id": "quickstart", "object": "chat.completion", "created": int(time.time()),
            "model": "deterministic-planner", "choices": [{"index": 0,
            "message": {"role": "assistant", "content": json.dumps(content, separators=(",", ":"))},
            "finish_reason": "stop"}], "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}


def reset_responses():
    global RESPONSES
    RESPONSES = [
        {"capabilityName": "quickstartPlanner", "createdAt": "2026-09-14T00:00:00Z", "status": "VALID",
         "tasks": [
             {"taskId": "identity", "title": "Verify identity", "status": "PENDING", "capabilityName": "identityLeaf",
              "intent": "Verify caller", "dependsOn": [], "expectedOutputs": ["identity"], "parallelGroup": None, "note": ""},
             {"taskId": "detail", "title": "Fetch detail", "status": "PENDING", "capabilityName": "detailLeaf",
              "intent": "Fetch detail", "dependsOn": ["identity"], "expectedOutputs": ["detail"], "parallelGroup": None, "note": ""}]},
        {"stepAction": "CALL_TOOL", "taskId": "identity", "toolName": "identityLeaf",
         "toolArguments": {"message": "verify"}},
        {"stepAction": "CALL_TOOL", "taskId": "detail", "toolName": "detailLeaf",
         "toolArguments": {"message": "detail"}},
        {"stepAction": "FINAL_RESPONSE", "finalResponse": "quickstart complete"},
    ]


reset_responses()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, pattern, *args):
        print(pattern % args, flush=True)

    def send_json(self, status, value):
        body = json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json(200, {"status": "UP"})
        elif parsed.path == "/token":
            subject = parse_qs(parsed.query).get("subject", ["quickstart-user"])[0]
            self.send_json(200, {"access_token": issue(subject), "token_type": "Bearer", "expires_in": 600})
        elif parsed.path == "/status":
            with LOCK: self.send_json(200, {"verified": list(VERIFIED)})
        else:
            self.send_json(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        if self.path == "/v1/chat/completions":
            with LOCK:
                if not RESPONSES: reset_responses()
                response = RESPONSES.pop(0)
            self.send_json(200, completion(response)); return
        if self.path in ("/callbacks/identity", "/callbacks/detail"):
            try:
                auth = self.headers.get("Authorization", "")
                claims = verify(auth[7:] if auth.startswith("Bearer ") else "")
                payload = json.loads(body or b"{}")
                if payload.get("message") == "block":
                    time.sleep(3)
                record = {"path": self.path, "host": self.headers.get("Host"),
                          "issuer": claims["iss"], "subject": claims["sub"],
                          "roles": claims["roles"], "message": payload.get("message")}
                with LOCK: VERIFIED.append(record)
                self.send_json(200, record)
            except Exception as failure:
                self.send_json(401, {"error": str(failure)})
            return
        self.send_json(404, {"error": "not found"})


if __name__ == "__main__":
    export_fresh_key()
    ThreadingHTTPServer(("127.0.0.1", 8081), Handler).serve_forever()
