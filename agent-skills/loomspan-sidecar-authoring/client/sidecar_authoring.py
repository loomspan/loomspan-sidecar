#!/usr/bin/env python3
"""Small, stateless client for Sidecar's shared management API (Python 3.9+)."""

import argparse
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4


ROOT = "/api/management"
ROUTES = {
    "current": ("GET", "/configuration/current"),
    "history": ("GET", "/configuration/history"),
    "draft": ("GET", "/editing/draft"),
    "lease-status": ("GET", "/editing"),
    "acquire": ("POST", "/editing/lease"),
    "handoff": ("POST", "/editing/lease/handoff"),
    "renew": ("POST", "/editing/lease/renew"),
    "release": ("POST", "/editing/lease/release"),
    "save": ("PUT", "/editing/draft"),
    "reconcile": ("POST", "/editing/draft/reconcile"),
    "validate": ("POST", "/editing/draft/validate"),
    "publish": ("POST", "/configuration/publish"),
    "export": ("GET", "/configuration/export"),
    "import-review": ("POST", "/configuration/import/review"),
    "import-load": ("POST", "/configuration/import/load"),
}
JSON_BODY = {"renew", "release", "save", "reconcile", "validate", "publish"}
SAFE_CODE = re.compile(r"^[a-z][a-z0-9_]{0,63}$")
SAFE_UUID = re.compile(r"^[0-9a-fA-F-]{36}$")
MAX_BUNDLE_BYTES = 100 * 1_048_576


class ClientError(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def input_json(path):
    try:
        data = Path(path).read_text(encoding="utf-8") if path else sys.stdin.read()
        value = json.loads(data)
        if not isinstance(value, dict):
            raise ValueError("expected a JSON object")
        return value
    except (OSError, ValueError) as exc:
        raise ClientError("Provide a readable, complete JSON object via --file or stdin") from None


def multipart(bundle_path, fields):
    try:
        bundle = Path(bundle_path).read_bytes()
    except OSError:
        raise ClientError("Bundle file cannot be read") from None
    boundary = "sidecar-" + uuid4().hex
    chunks = []
    for name, value in fields.items():
        chunks.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n"
                       f"{value}\r\n").encode("utf-8"))
    chunks.extend([f"--{boundary}\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"bundle.zip\"\r\n"
                   "Content-Type: application/zip\r\n\r\n".encode(), bundle,
                   f"\r\n--{boundary}--\r\n".encode()])
    return b"".join(chunks), "multipart/form-data; boundary=" + boundary


def request(base, token, method, path, body=None, content_type="application/json"):
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json, application/zip"}
    if body is not None:
        headers["Content-Type"] = content_type
    req = Request(base + ROOT + path, data=body, headers=headers, method=method)
    try:
        with build_opener(NoRedirect).open(req, timeout=30) as response:
            if path == "/configuration/export":
                payload = response.read(MAX_BUNDLE_BYTES + 1)
                if len(payload) > MAX_BUNDLE_BYTES:
                    raise ClientError("Export exceeds the server bundle limit")
                return payload
            payload = response.read()
            try:
                return json.loads(payload) if payload else {}
            except ValueError:
                raise ClientError("Server returned invalid JSON; inspect Sidecar state before another mutation") from None
    except HTTPError as exc:
        try:
            problem = json.loads(exc.read(8192))
            if not isinstance(problem, dict):
                problem = {}
        except (ValueError, OSError):
            problem = {}
        code = problem.get("code", "http_error")
        code = code if isinstance(code, str) and SAFE_CODE.fullmatch(code) else "http_error"
        detail = f"HTTP {exc.code} {code}."
        if exc.code == 401:
            detail += " Token invalid, expired or revoked; obtain a new token and read current/draft."
        elif exc.code == 403:
            detail += " Token preset or live account role lacks permission."
        elif exc.code == 404:
            detail += " Requested resource is unavailable; read current/draft."
        elif exc.code == 409:
            detail += " Read current, draft and lease status; resolve ownership or revision/base explicitly."
        elif exc.code == 413:
            detail += " Bundle or request exceeds server limits."
        elif exc.code == 503:
            detail += " Inspect current and draft before any further mutation; publication may have activated."
            for key in ("publishedId", "intendedId"):
                value = problem.get(key)
                if isinstance(value, str) and SAFE_UUID.fullmatch(value):
                    detail += f" {key}={value}."
        elif 300 <= exc.code < 400:
            detail += " Redirect refused; verify the configured Sidecar origin."
        raise ClientError(detail) from None
    except (URLError, TimeoutError, OSError):
        raise ClientError("Connection failed; check Sidecar URL/TLS and inspect state before retrying a mutation") from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="Sidecar origin, HTTPS except loopback")
    sub = parser.add_subparsers(dest="command", required=True)
    for command in ROUTES:
        item = sub.add_parser(command)
        if command in ("acquire", "handoff"):
            item.add_argument("--label", required=True)
        if command in JSON_BODY or command == "import-load":
            item.add_argument("--file", help="JSON request file; default stdin")
        if command in ("import-review", "import-load"):
            item.add_argument("--bundle", required=True)
        if command == "export":
            item.add_argument("--output", required=True)
    args = parser.parse_args()
    token = os.environ.get("LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN", "")
    if not token:
        raise ClientError("Set LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN in the process environment")
    parsed = urlsplit(args.url)
    if (parsed.scheme not in ("http", "https") or not parsed.netloc or parsed.username or
            parsed.password or parsed.path not in ("", "/") or parsed.query or parsed.fragment or
            (parsed.scheme == "http" and parsed.hostname not in ("localhost", "127.0.0.1", "::1"))):
        raise ClientError("Use a bare HTTPS Sidecar origin (HTTP only for loopback)")
    method, path = ROUTES[args.command]
    body = None
    content_type = "application/json"
    if args.command in ("acquire", "handoff"):
        body = json.dumps({"label": args.label}).encode()
    elif args.command in JSON_BODY:
        body = json.dumps(input_json(args.file)).encode()
    elif args.command in ("import-review", "import-load"):
        fields = input_json(args.file) if args.command == "import-load" else {}
        if args.command == "import-load" and set(fields) != {"editingSessionId", "generation", "draftId", "revision", "baseSnapshotId"}:
            raise ClientError("Import load requires the exact current candidate fields")
        body, content_type = multipart(args.bundle, fields)
    result = request(args.url.rstrip("/"), token, method, path, body, content_type)
    if args.command == "handoff":
        result = {"grant": result,
                  "current": request(args.url.rstrip("/"), token, "GET", "/configuration/current"),
                  "draft": request(args.url.rstrip("/"), token, "GET", "/editing/draft")}
    if args.command == "export":
        try:
            Path(args.output).write_bytes(result)
        except OSError:
            raise ClientError("Export output file cannot be written") from None
        print(json.dumps({"bytes": len(result)}))
    else:
        print(json.dumps(result, indent=2, ensure_ascii=False).replace(token, "[redacted]"))


if __name__ == "__main__":
    try:
        main()
    except ClientError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
