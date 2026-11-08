import asyncio
import json
from contextlib import asynccontextmanager
from pathlib import Path
from threading import Event
from typing import Annotated
from urllib.parse import unquote

import anyio
import h11
import pytest
from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request
from starlette.middleware.cors import CORSMiddleware
from starlette.responses import RedirectResponse, Response
from starlette.types import ASGIApp, Message, Scope

from loomspan_sdk import (
    AsyncSidecarClient, AuthenticatedIdentity, CallbackPrincipal, CallbackVerifier,
    ColocatedSigning, ExecutionTokenIssuer, InvalidCallbackCredential, IssuanceSettings,
    LocalFailure, ProvidedSigning,
)
from loomspan_sdk import _callback, _callback_trust, _issuance
from loomspan_sdk import fastapi as integration
from loomspan_sdk.fastapi import CallbackMiddleware, Loomspan

PREFIX = "/loomspan/callbacks"
BASE_URL = "http://127.0.0.1:1"
ISSUANCE = IssuanceSettings(issuer="trusted-app", roles=["ROLE_EDITOR", "reader"])
SYNTHETIC_JWKS = Path(__file__).resolve().parents[2] / "java/core/src/test/resources/signing/public-jwks.json"


def scope_for(path: str, **overrides) -> Scope:
    return {
        "type": "http", "asgi": {"version": "3.0"}, "http_version": "1.1",
        "scheme": "http", "method": "GET", "root_path": "", "path": path,
        "headers": [], "query_string": b"", **overrides,
    }


async def call(app: ASGIApp, scope: Scope, receive=None) -> list[Message]:
    messages: list[Message] = []

    async def send(message: Message) -> None:
        messages.append(message)

    async def unread() -> Message:
        raise AssertionError("the app must not read this body")

    await app(scope, unread if receive is None else receive, send)
    return messages


def test_uninitialized_middleware_denies_callback_without_dependency(tmp_path):
    loomspan = Loomspan(BASE_URL, ColocatedSigning(tmp_path))
    app = FastAPI()
    app.add_middleware(CallbackMiddleware, loomspan=loomspan)

    @app.get(PREFIX)
    async def protected():
        raise AssertionError("uninitialized callback reached the handler")

    @app.get(PREFIX + "-sibling")
    async def sibling():
        return {"ok": True}

    async def exercise():
        denied = await call(app, scope_for(PREFIX, headers=[(b"authorization", b"Bearer fake")]))
        assert denied[0]["status"] == 401
        assert (b"www-authenticate", b"Bearer") in denied[0]["headers"]
        assert (await call(app, scope_for(PREFIX + "-sibling")))[0]["status"] == 200

    anyio.run(exercise)


def setup(tmp_path, **options):
    loomspan = Loomspan(BASE_URL, ColocatedSigning(tmp_path), ISSUANCE, **options)
    app = FastAPI(lifespan=loomspan.lifespan)
    app.add_middleware(CallbackMiddleware, loomspan=loomspan)
    return loomspan, app


def auth(issuer, subject="alice", roles=("ROLE_EDITOR", "unlisted")):
    credential = issuer.issue(AuthenticatedIdentity(subject, roles))
    return [(b"authorization", credential._authorization())]


async def response(app, path=PREFIX, **scope):
    return (await call(app, scope_for(path, **scope)))[0]["status"]


@pytest.mark.parametrize("prefix", [
    "", "/", "/a//", "relative", "//a", "/a/./b", "/a/..", "/a b", "/a\u001f",
    "/a\u0085", "/a\t", "/a*", "/a{b}", "/a#b", "/a?b", "/a\\b", "/a%2fb", "/a;b",
])
def test_invalid_callback_prefix(tmp_path, prefix):
    with pytest.raises(LocalFailure, match="literal absolute non-root path"):
        setup(tmp_path, callback_prefix=prefix)


def test_construction_is_validation_only(tmp_path, monkeypatch):
    def forbidden(*args, **kwargs):
        pytest.fail("configuration performed work")

    with monkeypatch.context() as patches:
        patches.setattr(Path, "open", forbidden)
        patches.setattr(Path, "stat", forbidden)
        patches.setattr(_issuance, "_initialize_signing", forbidden)
        patches.setattr(_callback_trust, "_read_overlap", forbidden)
        patches.setattr(anyio, "connect_tcp", forbidden)
        patches.setattr(anyio, "create_task_group", forbidden)
        loomspan, _ = setup(tmp_path, callback_prefix="/nested/hooks/", overlap_jwks=tmp_path / "missing")
        assert loomspan.callback_prefix == "/nested/hooks"
        for options in (
            {"callback_audience": " "}, {"overlap_jwks": ""},
            {"overlap_jwks": Path("bad\0name")}, {"connect_timeout": 0}, {"max_response_size": True},
        ):
            with pytest.raises(LocalFailure):
                setup(tmp_path, **options)
        with pytest.raises(LocalFailure):
            Loomspan("https://user:password@invalid", ColocatedSigning(tmp_path))
        with pytest.raises(LocalFailure):
            Loomspan(BASE_URL, None)
        with pytest.raises(LocalFailure):
            Loomspan(BASE_URL, ColocatedSigning(tmp_path), {})
    assert not (tmp_path / "jwks.json").exists()


def test_lifespan_resources_and_fastapi_overrides(tmp_path):
    loomspan, _ = setup(tmp_path)
    stages = []

    @asynccontextmanager
    async def lifespan(app):
        async with loomspan.lifespan(app):
            stages.append("application started")
            yield
            stages.append("application stopped")
            assert await loomspan.client(Request({"type": "http", "app": app})) is client

    app = FastAPI(lifespan=lifespan)
    app.add_middleware(CallbackMiddleware, loomspan=loomspan)
    client = issuer = None

    @app.get("/resources")
    async def resources(
        actual_client: Annotated[AsyncSidecarClient, Depends(loomspan.client)],
        actual_issuer: Annotated[ExecutionTokenIssuer, Depends(loomspan.issuer)],
    ):
        return [id(actual_client), id(actual_issuer)]

    @app.get(PREFIX)
    async def protected(principal: Annotated[CallbackPrincipal, Depends(loomspan.principal)]):
        return principal.subject

    async def exercise():
        nonlocal client, issuer
        request = Request({"type": "http", "app": app})
        assert await response(app, "/resources") == 503
        async with app.router.lifespan_context(app):
            client = await loomspan.client(request)
            issuer = await loomspan.issuer(request)
            assert (tmp_path / "jwks.json").is_file()
            for _ in range(2):
                result = await call(app, scope_for("/resources"))
                assert json.loads(result[1]["body"]) == [id(client), id(issuer)]
            app.dependency_overrides[loomspan.client] = lambda: client_override
            app.dependency_overrides[loomspan.issuer] = lambda: issuer_override
            app.dependency_overrides[loomspan.principal] = lambda: CallbackPrincipal("override", "overridden", ())
            client_override, issuer_override = object(), object()
            result = await call(app, scope_for("/resources"))
            assert json.loads(result[1]["body"]) == [id(client_override), id(issuer_override)]
            assert await response(app) == 401
            result = await call(app, scope_for(PREFIX, headers=auth(issuer)))
            assert json.loads(result[1]["body"]) == "overridden"
            with pytest.raises(LocalFailure, match="already active"):
                async with loomspan.lifespan(app):
                    pytest.fail("second lifespan entered")
            assert stages == ["application started"]
            app.dependency_overrides.clear()
        assert stages == ["application started", "application stopped"]
        assert await response(app, "/resources") == 503
        assert await response(app, headers=auth(issuer)) == 401
        with pytest.raises(LocalFailure, match="closed"):
            await client.list_skills(issuer.issue(AuthenticatedIdentity("alice")))

        other = FastAPI()
        other.add_middleware(CallbackMiddleware, loomspan=loomspan)
        async with loomspan.lifespan(other):
            assert await loomspan.client(Request({"type": "http", "app": other})) is not client
            assert await response(app, "/resources") == 503

    anyio.run(exercise)


@pytest.mark.parametrize("failure", ["signer", "overlap", "client", "composed"])
def test_startup_failure_and_partial_cleanup(tmp_path, monkeypatch, failure):
    signing = (
        ProvidedSigning(tmp_path / "missing.pem", tmp_path / "provided.json")
        if failure == "signer" else ColocatedSigning(tmp_path)
    )
    overlap = tmp_path / "overlap.json"
    if failure != "overlap":
        overlap.write_bytes(SYNTHETIC_JWKS.read_bytes())
    loomspan = Loomspan(BASE_URL, signing, ISSUANCE, overlap_jwks=overlap)
    original = RuntimeError("application startup failed")
    app = FastAPI()
    app.add_middleware(CallbackMiddleware, loomspan=loomspan)
    captured = []
    clients = []
    open_verifier = CallbackVerifier.open

    @asynccontextmanager
    async def observe(issuer, **options):
        async with open_verifier(issuer, **options) as verifier:
            captured.append((issuer, verifier))
            yield verifier

    @asynccontextmanager
    async def lifespan(app):
        async with loomspan.lifespan(app):
            assert failure == "composed"
            clients.append(await loomspan.client(Request({"type": "http", "app": app})))
            raise original
            yield

    app.router.lifespan_context = lifespan
    monkeypatch.setattr(CallbackVerifier, "open", observe)
    if failure == "client":
        class BrokenClient(AsyncSidecarClient):
            async def __aenter__(self):
                raise original

        monkeypatch.setattr(integration, "AsyncSidecarClient", BrokenClient)

    async def exercise():
        messages = []

        async def receive():
            return {"type": "lifespan.startup"}

        async def send(message):
            messages.append(message)

        with pytest.raises(RuntimeError if failure in ("client", "composed") else LocalFailure) as caught:
            await app({"type": "lifespan", "asgi": {"version": "3.0"}, "state": {}}, receive, send)
        assert messages[0]["type"] == "lifespan.startup.failed"
        if failure == "signer":
            with pytest.raises(LocalFailure, match=r"key_location.*missing.*Provision") as standalone:
                ExecutionTokenIssuer(signing, ISSUANCE)
            assert str(caught.value) == str(standalone.value)
            assert str(caught.value) in messages[0]["message"]
            assert "missing.pem" not in messages[0]["message"]
            assert not signing.jwks_export.exists()
        assert await response(app) == 401
        assert not hasattr(app.state, integration._RESOURCE)
        assert (tmp_path / "jwks.json").exists() == (failure != "signer")
        if failure in ("client", "composed"):
            assert caught.value is original
            [(issuer, verifier)] = captured
            with pytest.raises(InvalidCallbackCredential):
                await verifier.verify(auth(issuer)[0][1].removeprefix(b"Bearer ").decode())
            if failure == "composed":
                [client] = clients
                with pytest.raises(LocalFailure, match="closed"):
                    await client.list_skills(issuer.issue(AuthenticatedIdentity("alice")))
        else:
            assert not captured

    anyio.run(exercise)


def test_concurrent_startup_reserves_owner_before_signing(tmp_path, monkeypatch):
    loomspan, app = setup(tmp_path)
    create = anyio.to_thread.run_sync

    async def exercise():
        starting, release = anyio.Event(), anyio.Event()

        async def delayed(function, *args, **kwargs):
            if function is ExecutionTokenIssuer:
                starting.set()
                await release.wait()
            return await create(function, *args, **kwargs)

        async def first():
            async with loomspan.lifespan(app):
                assert await response(app) == 401

        monkeypatch.setattr(anyio.to_thread, "run_sync", delayed)
        with anyio.fail_after(3):
            async with anyio.create_task_group() as group:
                group.start_soon(first)
                await starting.wait()
                with pytest.raises(LocalFailure, match="already active"):
                    async with loomspan.lifespan(app):
                        pytest.fail("concurrent startup entered")
                assert await response(app) == 401
                release.set()
        assert not hasattr(app.state, integration._RESOURCE)

    anyio.run(exercise)


def test_cancelled_startup_waits_for_signing_writer(tmp_path, monkeypatch):
    loomspan, app = setup(tmp_path)
    initialize = _issuance._initialize_signing

    async def exercise():
        started = anyio.Event()
        release = Event()

        def blocked(signing):
            anyio.from_thread.run_sync(started.set)
            assert release.wait(5)
            return initialize(signing)

        async def start():
            async with loomspan.lifespan(app):
                pytest.fail("cancelled startup entered")

        monkeypatch.setattr(_issuance, "_initialize_signing", blocked)
        with anyio.fail_after(4):
            task = asyncio.create_task(start())
            try:
                await started.wait()
                task.cancel()
                await anyio.wait_all_tasks_blocked()
                assert not task.done()
                with pytest.raises(LocalFailure, match="already active"):
                    async with loomspan.lifespan(app):
                        pytest.fail("second signer started")
            finally:
                release.set()
                await asyncio.gather(task, return_exceptions=True)
            assert task.cancelled()
            assert not hasattr(app.state, integration._RESOURCE)
            assert (tmp_path / "jwks.json").is_file()
            assert await response(app) == 401

    anyio.run(exercise)


def test_callback_boundary_credentials_methods_paths_and_body(tmp_path):
    loomspan, app = setup(tmp_path)
    reads_allowed = False
    received = []

    @app.get(PREFIX)
    @app.get(PREFIX + "/")
    @app.get(PREFIX + "/entry")
    @app.get(PREFIX + "-sibling")
    async def plain():
        return "handler"

    @app.post(PREFIX + "/entry")
    async def entry(request: Request):
        nonlocal reads_allowed
        reads_allowed = True
        received.append((request.headers.getlist("x-business"), [frame async for frame in request.stream()]))

    @app.websocket(PREFIX + "/entry")
    async def upgraded():
        pytest.fail("protected WebSocket dispatched")

    async def exercise():
        nonlocal reads_allowed
        async with loomspan.lifespan(app):
            issuer = await loomspan.issuer(Request({"type": "http", "app": app}))
            headers = auth(issuer)
            for path in (PREFIX, PREFIX + "/", PREFIX + "/entry"):
                assert await response(app, path, headers=headers) == 200
                assert await response(app, path) == 401
            assert await response(app, PREFIX + "-sibling") == 200
            assert await response(app, PREFIX, headers=[(b"AUTHORIZATION", b"bEaReR" + headers[0][1][6:])]) == 200
            for invalid in (
                headers + [(b"Authorization", b"Bearer other")],
                [(b"authorization", headers[0][1] + b", Bearer other")],
                [(b"authorization", b"Bearer  abc")],
                [(b"authorization", b"Bearer abc\n")],
                [(b"authorization", b"Bearer bad@token")],
                [(b"cookie", b"session=authenticated")],
            ):
                messages = await call(app, scope_for(PREFIX, headers=invalid, session={"subject": "alice"}))
                assert messages[0]["status"] == 401
                assert (b"www-authenticate", b"Bearer") in messages[0]["headers"]
                assert (b"location", b"/login") not in messages[0]["headers"]
                assert messages[1]["body"] == b""
            for query in (b"access_token", b"x=1&access_token=", b"%61ccess%5Ftoken=other", b"weird%ZZ=1"):
                assert await response(app, headers=headers, query_string=query) == 401
            assert await response(app, headers=headers, query_string=b"parameter=access_token%ZZ") == 200
            for method in ("HEAD", "OPTIONS", "PUT", "PATCH", "DELETE", "TRACE"):
                assert await response(app, method=method, headers=headers) == 403
                assert await response(app, method=method) == 401
            assert await call(app, scope_for(PREFIX + "/entry", type="websocket", headers=headers)) == [
                {"type": "websocket.close", "code": 1008}
            ]

            for raw in (
                b"//loomspan/callbacks", b"/loomspan//callbacks/entry", b"/loomspan/callbacks;sid/entry",
                b"/loomspan;sid/callbacks", b"/loomspan/callbacks;sid/../../outside",
                b"/loomspan\\callbacks", b"/loomspan%2fcallbacks", b"/loomspan/callbacks/%2e/entry",
                b"/loomspan/callbacks/%ZZ", b"/loomspan/callbacks/%ff", b"/loomspan/callbacks/%00",
                b"/loomspan/callbacks/%E2%80%A8",
                b"/loomspan%25252fcallbacks", b"/safe/../loomspan/callbacks",
                b"/loomspan/callbacks/../../outside", b"/loomspan/callbacks%3fother",
            ):
                assert await response(app, unquote(raw), raw_path=raw, headers=headers) == 400, raw
            assert await response(app, "/loomspan//callbacks") == 400
            assert await response(app, "/loomspan/callbacks-sibling", raw_path=b"/loomspan/callbacks-sibling") == 200

            for chunks, content_type in [
                ([], b"application/octet-stream"),
                ([b"access_token=business-data"], b"application/x-www-form-urlencoded"),
                ([b'{"text":', b'"untouched"}'], b"application/json"),
            ]:
                reads_allowed = False
                pending = list(chunks or [b""])

                async def receive():
                    assert reads_allowed
                    body = pending.pop(0)
                    return {"type": "http.request", "body": body, "more_body": bool(pending)}

                raw_headers = headers + [
                    (b"x-business", b"first"), (b"x-business", b"second"), (b"content-type", content_type),
                ]
                scope = scope_for(PREFIX + "/entry", method="POST", headers=(iter(pair) for pair in raw_headers))
                assert (await call(app, scope, receive))[0]["status"] == 200
                assert received[-1] == (["first", "second"], chunks + [b""])

    anyio.run(exercise)

class BrowserPolicy:
    def __init__(self, app, *, seen):
        self.app = app
        self.seen = seen

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        self.seen.append(scope["path"])
        if scope["path"].startswith("/edge/browser"):
            headers = dict(scope["headers"])
            if headers.get(b"cookie") != b"session=existing":
                await RedirectResponse("/login")(scope, receive, send)
                return
            if scope["method"] == "POST" and headers.get(b"x-csrf") != b"expected":
                await Response(status_code=403)(scope, receive, send)
                return
        await self.app(scope, receive, send)


def test_callback_mount_root_and_browser_middleware(tmp_path):
    origin = (b"origin", b"https://browser.test")
    preflight = [origin, (b"access-control-request-method", b"POST")]
    loomspan = Loomspan(BASE_URL, ColocatedSigning(tmp_path), ISSUANCE, callback_prefix="/mounted/callbacks/")
    app = FastAPI(lifespan=loomspan.lifespan)
    seen = []
    app.add_middleware(BrowserPolicy, seen=seen)
    app.add_middleware(CORSMiddleware, allow_origins=["https://browser.test"], allow_methods=["GET", "POST"])
    app.add_middleware(CallbackMiddleware, loomspan=loomspan)
    child = FastAPI()
    router = APIRouter()

    @router.get("/callbacks")
    async def protected(principal: Annotated[CallbackPrincipal, Depends(loomspan.principal)]):
        return principal

    @router.get("/callbacks-sibling")
    async def sibling():
        return "ordinary child route"

    child.include_router(router)
    app.mount("/mounted", child)

    @app.get("/edgeish/mounted/callbacks")
    @app.get("/browser")
    @app.post("/browser")
    async def browser():
        return "ordinary app route"

    async def dispatch(scope, receive, send):
        scope["root_path"] = "/edge"
        await app(scope, receive, send)

    async def exercise():
        async with loomspan.lifespan(app):
            issuer = await loomspan.issuer(Request({"type": "http", "app": app}))
            headers = auth(issuer)
            path = "/edge/mounted/callbacks"
            messages = await call(dispatch, scope_for(path, headers=headers + [origin]))
            assert json.loads(messages[1]["body"]) == {
                "issuer": "trusted-app", "subject": "alice", "authorities": ["ROLE_EDITOR"],
            }
            assert (b"access-control-allow-origin", origin[1]) in messages[0]["headers"]
            assert await response(dispatch, path, raw_path=b"/mounted/%63allbacks", headers=headers) == 200
            assert seen == [path, path]
            assert await response(dispatch, path, headers=preflight, method="OPTIONS") == 401
            assert await response(dispatch, path, headers=headers + preflight, method="OPTIONS") == 403
            assert await response(dispatch, path, headers=[(b"cookie", b"session=existing")]) == 401
            assert await response(dispatch, "/mounted/callbacks") == 401
            assert seen == [path, path]
            assert await response(dispatch, "/edgeish/mounted/callbacks") == 200
            assert await response(dispatch, "/edge/mounted/callbacks-sibling") == 200
            for raw in (b"/edge%2fmounted/callbacks", b"/edge//mounted/callbacks", b"/edge;sid/mounted/callbacks"):
                assert await response(dispatch, unquote(raw), raw_path=raw, headers=headers) == 400
            assert await response(dispatch, "/edge/browser") == 307
            cookies = [(b"cookie", b"session=existing")]
            assert await response(dispatch, "/edge/browser", headers=cookies) == 200
            assert await response(dispatch, "/edge/browser", headers=cookies, method="POST") == 403
            assert await response(dispatch, "/edge/browser", headers=cookies + [(b"x-csrf", b"expected")], method="POST") == 200
            assert await response(dispatch, "/edge/browser", headers=preflight, method="OPTIONS") == 200

    anyio.run(exercise)


def test_h11_rejection_precedes_callback_scope_creation():
    def parse(method, target):
        connection = h11.Connection(h11.SERVER)
        connection.receive_data(method + b" " + target + b" HTTP/1.1\r\nHost: example.test\r\n\r\n")
        return connection.next_event()

    for target in (b"/loomspan/callbacks", b"/loomspan%2fcallbacks"):
        request = parse(b"TRACE", target)
        assert isinstance(request, h11.Request)
        assert (request.method, request.target) == (b"TRACE", target)
    for target in (b"/loomspan/callbacks/with space", b"/loomspan/callbacks/\x00"):
        with pytest.raises(h11.RemoteProtocolError):
            parse(b"GET", target)


@pytest.mark.parametrize("initialized", [True, False])
def test_mounted_child_lifespan_is_explicit(tmp_path, initialized):
    loomspan, child = setup(tmp_path)

    @child.get(PREFIX)
    async def callback(principal: Annotated[CallbackPrincipal, Depends(loomspan.principal)]):
        return principal.subject

    @asynccontextmanager
    async def lifespan(app):
        if initialized:
            async with loomspan.lifespan(child):
                yield
        else:
            yield

    app = FastAPI(lifespan=lifespan)
    app.mount("/mounted", child)

    async def exercise():
        path = "/edge/mounted" + PREFIX
        async with app.router.lifespan_context(app):
            if initialized:
                issuer = await loomspan.issuer(Request({"type": "http", "app": child}))
                assert await response(app, path, headers=auth(issuer), root_path="/edge") == 200
            else:
                assert await response(app, path, headers=[(b"authorization", b"Bearer fake")], root_path="/edge") == 401
            assert (tmp_path / "jwks.json").is_file() is initialized
        assert await response(app, path, root_path="/edge") == 401

    anyio.run(exercise)


@pytest.mark.parametrize("misplaced", [False, True])
def test_startup_rejects_missing_or_inner_callback_middleware(tmp_path, misplaced):
    loomspan = Loomspan(BASE_URL, ColocatedSigning(tmp_path), ISSUANCE)
    app = FastAPI()
    if misplaced:
        app.add_middleware(CallbackMiddleware, loomspan=loomspan)
        app.add_middleware(CORSMiddleware)

    async def exercise():
        with pytest.raises(LocalFailure, match="middleware last"):
            async with loomspan.lifespan(app):
                pytest.fail("unsafe app started")
        assert not (tmp_path / "jwks.json").exists()

    anyio.run(exercise)


def test_concurrent_callback_identity_error_and_cancellation(tmp_path):
    loomspan = Loomspan(BASE_URL, ColocatedSigning(tmp_path), ISSUANCE)
    app = FastAPI(lifespan=loomspan.lifespan)
    gates = {}
    requests = {}
    original = RuntimeError("callback failed")

    class CopyScope:
        def __init__(self, app):
            self.app = app

        async def __call__(self, scope, receive, send):
            await self.app(scope.copy(), receive, send)

    app.add_middleware(CopyScope)
    app.add_middleware(CallbackMiddleware, loomspan=loomspan)

    @app.get("/unprotected")
    @app.get(PREFIX + "/{operation}")
    async def protected(
        request: Request, principal: Annotated[CallbackPrincipal, Depends(loomspan.principal)], operation: str = "",
    ):
        requests[operation] = request
        if operation == "business" and (
            "ROLE_EDITOR" not in principal.authorities or (principal.issuer, principal.subject) != ("trusted-app", "alice")
        ):
            raise HTTPException(status_code=403, detail="Forbidden")
        if operation in gates:
            started, release = gates[operation]
            started.set()
            await release.wait()
        if operation == "error":
            raise original
        return principal

    async def exercise():
        async with loomspan.lifespan(app):
            issuer = await loomspan.issuer(Request({"type": "http", "app": app}))
            alice = auth(issuer)
            bob = auth(issuer, "bob", ("reader",))
            with anyio.fail_after(4):
                gates["error"] = anyio.Event(), anyio.Event()
                gates["cancel"] = anyio.Event(), anyio.Event()
                failing = scope_for(PREFIX + "/error", headers=alice)
                cancelled = scope_for(PREFIX + "/cancel", headers=bob)
                error_task = asyncio.create_task(call(app, failing))
                cancel_task = asyncio.create_task(call(app, cancelled))
                try:
                    await gates["error"][0].wait()
                    await gates["cancel"][0].wait()
                    assert (await loomspan.principal(requests["error"])) == CallbackPrincipal(
                        "trusted-app", "alice", ("ROLE_EDITOR",),
                    )
                    assert (await loomspan.principal(requests["cancel"])) == CallbackPrincipal(
                        "trusted-app", "bob", ("reader",),
                    )
                    assert await response(app, "/unprotected", headers=alice) == 401
                    assert await response(app, PREFIX + "/business", headers=bob) == 403
                    assert await response(app, PREFIX + "/business", headers=auth(issuer, "bob")) == 403
                    assert await response(app, PREFIX + "/business", headers=alice) == 200
                    gates["error"][1].set()
                    with pytest.raises(RuntimeError) as caught:
                        await error_task
                    assert caught.value is original
                    cancel_task.cancel()
                    with pytest.raises(asyncio.CancelledError):
                        await cancel_task
                finally:
                    for task in (error_task, cancel_task):
                        if not task.done():
                            task.cancel()
                    await asyncio.gather(error_task, cancel_task, return_exceptions=True)
                for scope in (failing, cancelled):
                    assert integration._PRINCIPAL not in scope
                successful = scope_for(PREFIX + "/business", headers=alice)
                assert (await call(app, successful))[0]["status"] == 200
                assert integration._PRINCIPAL not in successful
                for operation in ("business", "error", "cancel"):
                    with pytest.raises(HTTPException) as failure:
                        await loomspan.principal(requests[operation])
                    assert failure.value.status_code == 401

    anyio.run(exercise)


def test_trust_and_expiry_only_gate_new_admission(tmp_path, monkeypatch, caplog):
    overlap = tmp_path / "overlap.json"
    valid = SYNTHETIC_JWKS.read_bytes()
    overlap.write_bytes(valid)
    clock = 1_800_000_000_000_000_000
    monkeypatch.setattr(_issuance, "time_ns", lambda: clock)
    monkeypatch.setattr(_callback, "time_ns", lambda: clock)
    caplog.set_level("INFO", logger="loomspan_sdk._callback_trust")
    loomspan, app = setup(tmp_path, overlap_jwks=overlap)
    entered = release = None

    @app.get(PREFIX + "/hold")
    async def hold(principal: Annotated[CallbackPrincipal, Depends(loomspan.principal)], request: Request):
        entered.set()
        await release.wait()
        assert await loomspan.principal(request) is principal
        return principal

    @app.get(PREFIX)
    async def probe():
        return "authenticated"

    async def observe_log(text):
        while text not in caplog.text:
            await anyio.sleep(0.02)

    async def exercise():
        nonlocal entered, release, clock
        async with loomspan.lifespan(app):
            issuer = await loomspan.issuer(Request({"type": "http", "app": app}))
            headers = auth(issuer)
            for transition in ("withdrawal", "expiry"):
                entered, release = anyio.Event(), anyio.Event()
                scope = scope_for(PREFIX + "/hold", headers=headers)
                with anyio.fail_after(5):
                    task = asyncio.create_task(call(app, scope))
                    try:
                        await entered.wait()
                        if transition == "withdrawal":
                            overlap.write_text("invalid JWKS", encoding="utf-8")
                            await observe_log("Callback trust withdrawn.")
                        else:
                            clock += 961 * 1_000_000_000
                        assert await response(app, headers=headers) == 401
                        release.set()
                        result = await task
                    finally:
                        if not task.done():
                            task.cancel()
                            await asyncio.gather(task, return_exceptions=True)
                    assert json.loads(result[1]["body"]) == {
                        "issuer": "trusted-app", "subject": "alice", "authorities": ["ROLE_EDITOR"],
                    }
                    assert integration._PRINCIPAL not in scope
                    if transition == "withdrawal":
                        overlap.write_bytes(valid)
                        await observe_log("Callback trust restored.")
                        assert await response(app, headers=headers) == 200
            assert headers[0][1].decode() not in caplog.text

    anyio.run(exercise)
