"""Exercise the copied FastAPI example against the installed optional SDK."""

import importlib.util
import json
import os
import sys
from pathlib import Path

import anyio

import loomspan_sdk.fastapi as integration
from fastapi_app import create_app
from wire_fixture import WirePeer, reply_json

EXECUTION_ID = "6a0c950c-d172-4151-b629-6a5c15c7ae37"
PASSWORD = "synthetic-local-password"
CALLBACK = "/loomspan/callbacks/orders/"


async def request(app, path, headers=(), body=None):
    messages = []
    sent = False

    async def receive():
        nonlocal sent
        assert not sent and body is not None
        sent = True
        return {"type": "http.request", "body": json.dumps(body).encode(), "more_body": False}

    async def send(message):
        messages.append(message)

    await app({
        "type": "http", "asgi": {"version": "3.0"}, "http_version": "1.1", "scheme": "http",
        "method": "POST", "root_path": "", "path": path, "raw_path": path.encode(),
        "headers": [(b"content-type", b"application/json"), *headers], "query_string": b"",
    }, receive, send)
    return messages[0]["status"], messages[1]["body"]


def public_key(export):
    [key] = json.loads(export.read_bytes())["keys"]
    assert set(key) == {"kty", "n", "e", "kid", "alg", "use"}
    return key


async def exercise(mode, runtime):
    runtime.mkdir()
    export = runtime / "jwks.json"
    pem = Path("secrets/loomspan-signing.pem")

    def sidecar(conn, incoming, index):
        assert index == 0
        assert (incoming.method, incoming.target) == ("POST", "/v1/skills/echo/executions")
        assert json.loads(incoming.body) == {"text": "hello"}
        assert incoming.headers["authorization"].startswith("Bearer ")
        reply_json(conn, 202, {"id": EXECUTION_ID})

    with WirePeer(sidecar) as peer:
        os.environ.update(
            LOOMSPAN_SIGNING_MODE=mode, LOOMSPAN_KEY_DIRECTORY=str(runtime),
            LOOMSPAN_SIGNING_KEY=str(pem), LOOMSPAN_JWKS_EXPORT=str(export),
            LOOMSPAN_SIDECAR_URL=peer.url, LOOMSPAN_DEMO_PASSWORD=PASSWORD, LOOMSPAN_SKILL="echo",
        )
        app = create_app()
        assert not export.exists()
        async with app.router.lifespan_context(app):
            original_key = public_key(export)
            denied = await request(app, "/submit", body={"text": "hello"})
            assert denied[0] == 401 and not peer.requests
            accepted = await request(app, "/submit", [(b"x-demo-password", PASSWORD.encode())], {"text": "hello"})
            assert accepted[0] == 202 and json.loads(accepted[1]) == {"id": EXECUTION_ID}
            [incoming] = peer.requests
            auth = incoming.headers["authorization"].encode()
            assert auth != b"Bearer " + PASSWORD.encode()
            headers = [(b"authorization", auth)]
            note = {"note": "done"}
            denied_callback = await request(app, CALLBACK + "order-1")
            malformed_callback = await request(app, CALLBACK + "order-1", [(b"authorization", b"Bearer invalid")])
            assert denied_callback[0] == malformed_callback[0] == 401
            for order in ("order-2", "order-3"):
                assert (await request(app, CALLBACK + order, headers, note))[0] == 403
            allowed = await request(app, CALLBACK + "order-1", headers, note)
            assert allowed[0] == 200 and json.loads(allowed[1]) == {"order": "order-1", "note": "done"}
            schema = json.dumps(app.openapi()).encode()
            assert b"/submit" in schema and b"/loomspan/callbacks/orders/{order_id}" in schema
            for output in (schema, denied[1], denied_callback[1], malformed_callback[1], allowed[1]):
                assert PASSWORD.encode() not in output and auth[7:] not in output and b"PRIVATE KEY" not in output
            assert b"x-demo-password" not in schema and b"Authorization" not in schema

        if mode == "colocated":
            async with app.router.lifespan_context(app):
                assert public_key(export)["kid"] != original_key["kid"]
                assert (await request(app, CALLBACK + "order-1", headers))[0] == 401
        else:
            assert original_key == public_key(Path("secrets/public-jwks.json"))
            replica_export = runtime / "replica-jwks.json"
            os.environ["LOOMSPAN_JWKS_EXPORT"] = str(replica_export)
            replica = create_app()
            async with replica.router.lifespan_context(replica):
                assert public_key(replica_export) == public_key(export) == original_key
                assert (await request(replica, CALLBACK + "order-1", headers, note))[0] == 200


if __name__ == "__main__":
    assert Path(integration.__file__).resolve().is_relative_to(Path(sys.prefix).resolve())
    for package in ("uvicorn", "fastapi_cli", "fastapi_cloud_cli"):
        assert importlib.util.find_spec(package) is None
    for signing_mode in ("colocated", "provided"):
        anyio.run(exercise, signing_mode, Path(sys.argv[1]) / signing_mode)
