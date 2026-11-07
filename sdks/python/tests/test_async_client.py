import asyncio
import gzip
import json
import select
import ssl
from threading import Event, get_ident
from time import monotonic
from uuid import uuid4

import anyio
import pytest
import trustme

from loomspan_sdk import (
    AcceptanceUnknown,
    AsyncSidecarClient,
    CredentialSnapshot,
    ExecutionStatus,
    HttpFailure,
    LocalFailure,
    ProtocolFailure,
    WaitOptions,
    WaitTerminated,
)
from loomspan_sdk._async_transport import _AsyncTransport
from loomspan_sdk._http import _HttpExchange

from test_protocol import CONFIG_ID, CREATED, EXECUTION_ID, descriptor, snapshot
from wire_fixture import WirePeer, read_until_peer_closed, reply, reply_json, verified_tls_contexts

TOKEN = "synthetic-async-bearer"
CREDENTIAL = CredentialSnapshot(TOKEN)
BUDGET = 0.30


def test_async_workflow_preserves_wire_contract() -> None:
    result = " \n{malformed 日本語"

    def respond(conn, request, index):
        if index == 0:
            reply_json(conn, 200, [descriptor()])
        elif index == 1:
            reply_json(conn, 200, descriptor())
        elif index == 2:
            reply_json(conn, 202, {"id": str(EXECUTION_ID)}, headers=(("Location", "/wrong"),))
        elif index in (3, 4):
            reply_json(conn, 200, snapshot("QUEUED" if index == 3 else "RUNNING") | {
                "configurationSnapshotId": str(CONFIG_ID),
            })
        else:
            reply_json(conn, 200, snapshot("FAILED") | {"result": result, "events": []})

    async def exercise(url):
        async with AsyncSidecarClient(url + "/prefix/%2f/") as client:
            catalog = await client.list_skills(CREDENTIAL)
            assert catalog[0].name == "default-echo"
            assert catalog[0].input_schema == descriptor()["inputSchema"]
            assert (await client.get_skill("café ?", CREDENTIAL)).name == "default-echo"
            accepted = await client.submit("café ?", {"text": "日本語", "arr": [1, None]}, CREDENTIAL)
            assert accepted.id == EXECUTION_ID
            current = await client.get_execution(accepted.id, CREDENTIAL)
            assert current.status is ExecutionStatus.QUEUED
            assert current.events is None and current.created_at == CREATED
            assert current.configuration_snapshot_id == CONFIG_ID
            failed = await client.wait_for_completion(accepted.id, CREDENTIAL, WaitOptions(1.0, 0.01))
            assert failed.status is ExecutionStatus.FAILED
            assert failed.result == result and failed.events == ()
            assert failed.failure.kind == "VALIDATION"

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    status_path = f"/prefix/%2f/v1/executions/{EXECUTION_ID}"
    assert [(r.method, r.target) for r in peer.requests] == [
        ("GET", "/prefix/%2f/v1/skills"),
        ("GET", "/prefix/%2f/v1/skills/caf%C3%A9%20%3F"),
        ("POST", "/prefix/%2f/v1/skills/caf%C3%A9%20%3F/executions"),
        ("GET", status_path), ("GET", status_path), ("GET", status_path),
    ]
    for request in peer.requests:
        assert request.headers["authorization"] == f"Bearer {TOKEN}"
        assert request.headers["accept"] == "application/json"
        assert "accept-encoding" not in request.headers and "cookie" not in request.headers
    assert peer.requests[2].headers["content-type"] == "application/json"
    assert json.loads(peer.requests[2].body) == {"text": "日本語", "arr": [1, None]}


def test_invalid_calls_do_not_dispatch_and_keep_context() -> None:
    async def exercise(url):
        async with AsyncSidecarClient(url) as client:
            with pytest.raises(LocalFailure):
                await client.get_skill("a/b", CREDENTIAL)
            with pytest.raises(LocalFailure):
                await client.submit("echo", {"bad": float("nan")}, CREDENTIAL)
            with pytest.raises(LocalFailure):
                await client.get_execution(str(EXECUTION_ID), CREDENTIAL)
            with pytest.raises(LocalFailure) as error:
                await client.get_execution(EXECUTION_ID, None)
            assert error.value.execution_id == EXECUTION_ID
            with pytest.raises(LocalFailure) as error:
                await client.wait_for_completion(EXECUTION_ID, CREDENTIAL, None)
            assert error.value.execution_id == EXECUTION_ID

    with WirePeer(lambda *_: pytest.fail("Unexpected dispatch")) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == 0


def test_concurrent_credentials_and_ignored_cookies() -> None:
    resume = Event()

    def respond(conn, request, _):
        if request.target.endswith("/first"):
            loop.call_soon_threadsafe(first_arrived.set)
            assert resume.wait(2)
        reply_json(conn, 200, descriptor(), headers=(("Set-Cookie", "session=fixture"),))

    async def exercise(url):
        nonlocal loop, first_arrived
        loop = asyncio.get_running_loop()
        first_arrived = asyncio.Event()
        async with AsyncSidecarClient(url) as client:
            first = asyncio.create_task(client.get_skill("first", CREDENTIAL))
            try:
                await asyncio.wait_for(first_arrived.wait(), 1)
                second = await asyncio.wait_for(
                    client.get_skill("second", CredentialSnapshot("synthetic-second")), 1
                )
                assert not first.done()
            finally:
                resume.set()
            assert second == await first
            assert await client.get_skill("later", CREDENTIAL) == second

    loop = first_arrived = None
    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert [(r.target, r.headers["authorization"]) for r in peer.requests] == [
        ("/v1/skills/first", f"Bearer {TOKEN}"),
        ("/v1/skills/second", "Bearer synthetic-second"),
        ("/v1/skills/later", f"Bearer {TOKEN}"),
    ]
    assert all("cookie" not in r.headers and "accept-encoding" not in r.headers for r in peer.requests)
    assert peer.connections == 3


@pytest.mark.parametrize(("method", "status", "headers"), [
    ("GET", 302, (("Location", "http://127.0.0.1:1/other"),)),
    ("POST", 307, (("Location", "/other"),)),
    ("GET", 401, (("WWW-Authenticate", 'Bearer realm="fixture"'),)),
    ("POST", 407, (("Proxy-Authenticate", 'Basic realm="fixture"'),)),
])
def test_redirects_and_challenges_are_single_attempts(method, status, headers) -> None:
    def respond(conn, *_):
        reply_json(conn, status, {"title": f"Rejected {TOKEN}"}, headers=headers)

    async def exercise(url):
        async with AsyncSidecarClient(url) as client:
            with pytest.raises(HttpFailure) as error:
                if method == "GET":
                    await client.list_skills(CREDENTIAL)
                else:
                    await client.submit("echo", {}, CREDENTIAL)
            assert error.value.status_code == status
            assert TOKEN not in repr(error.value.problem) + str(error.value)
            assert error.value.__cause__ is error.value.__context__ is None

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == 1
    assert [request.method for request in peer.requests] == [method]


@pytest.mark.parametrize("compressed", [True, False])
def test_compression_and_missing_response_do_not_establish_acceptance(compressed) -> None:
    def respond(conn, *_):
        if compressed:
            body = json.dumps({"id": str(EXECUTION_ID)}).encode()
            reply(conn, 202, gzip.compress(body), headers=(("Content-Encoding", "gzip"),))

    async def exercise(url):
        async with AsyncSidecarClient(url) as client:
            with pytest.raises(AcceptanceUnknown) as error:
                await client.submit("echo", {}, CREDENTIAL)
            assert error.value.execution_id is None

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == len(peer.requests) == 1


@pytest.mark.parametrize("mode", ["headers", "body", "trickle"])
def test_total_response_budget_bounds_stalls_and_trickling_body(mode) -> None:
    released = Event()

    def respond(conn, *_):
        if mode != "headers":
            conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Length: 1024\r\n\r\n[")
        if mode == "trickle":
            sent = 0
            for _ in range(30):
                if select.select([conn], [], [], 0.03)[0]:
                    break
                try:
                    conn.sendall(b" ")
                except OSError:
                    break
                sent += 1
            assert 1 < sent < 30
        assert read_until_peer_closed(conn) == 0
        released.set()

    async def exercise(url):
        async with AsyncSidecarClient(url, response_timeout=BUDGET) as client:
            start = monotonic()
            with pytest.raises(ProtocolFailure):
                await client.list_skills(CREDENTIAL)
            assert monotonic() - start < 1.2
            assert await asyncio.to_thread(released.wait, 1)

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == len(peer.requests) == 1


@pytest.mark.parametrize("framing", ["length", "chunked", "close", "incomplete"])
@pytest.mark.parametrize("status", [202, 503])
def test_unusable_response_preserves_observed_submission_status(framing, status) -> None:
    body = json.dumps({"id": str(EXECUTION_ID)}).encode()
    released = Event()

    def respond(conn, *_):
        conn.sendall(b"HTTP/1.1 100 Continue\r\n\r\n" + f"HTTP/1.1 {status} Test\r\n".encode())
        if framing == "length":
            conn.sendall(b"Content-Length: 120\r\n\r\n")
        elif framing == "chunked":
            conn.sendall(b"Transfer-Encoding: chunked\r\n\r\n60\r\n" + body * 2 + b"\r\n")
        elif framing == "close":
            conn.sendall(b"Connection: close\r\n\r\n" + body * 2)
        else:
            conn.sendall(b"Content-Length: 64\r\n\r\n" + body)
            return
        # The SDK must close at the limit, without waiting for an EOF or final chunk.
        assert read_until_peer_closed(conn) == 0
        released.set()

    async def exercise(url):
        async with AsyncSidecarClient(url, max_response_size=64) as client:
            with pytest.raises(AcceptanceUnknown if status == 202 else HttpFailure) as error:
                await client.submit("echo", {}, CREDENTIAL)
            assert error.value.execution_id is None
            if status == 503:
                assert error.value.status_code == 503 and error.value.problem is None
            if framing != "incomplete":
                assert await asyncio.to_thread(released.wait, 1)

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == len(peer.requests) == 1


def test_exact_body_limit_and_chunked_informational_response() -> None:
    def respond(conn, *_):
        conn.sendall(
            b"HTTP/1.1 103 Early Hints\r\n\r\nHTTP/1.1 200 OK\r\n"
            b"Transfer-Encoding: chunked\r\n\r\n1\r\n[\r\n1\r\n]\r\n0\r\n\r\n"
        )
        assert read_until_peer_closed(conn) == 0

    async def exercise(url):
        async with AsyncSidecarClient(url, max_response_size=2) as client:
            assert await client.list_skills(CREDENTIAL) == ()

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == len(peer.requests) == 1


def test_connect_deadline_cancels_resolution_before_dispatch(monkeypatch) -> None:
    async def exercise(port):
        loop = asyncio.get_running_loop()
        resolve = loop.getaddrinfo
        cancelled = asyncio.Event()
        queries = 0

        async def lookup(host, *args, **kwargs):
            nonlocal queries
            assert host == b"localhost" or host == "localhost"
            queries += 1
            if queries == 1:
                try:
                    await asyncio.Event().wait()
                finally:
                    cancelled.set()
            return await resolve("127.0.0.1", *args, **kwargs)

        monkeypatch.setattr(loop, "getaddrinfo", lookup)
        async with AsyncSidecarClient(
            f"http://localhost:{port}", connect_timeout=BUDGET, response_timeout=3
        ) as client:
            with pytest.raises(ProtocolFailure):
                await asyncio.wait_for(client.list_skills(CREDENTIAL), 1.2)
            assert cancelled.is_set()
            assert await client.list_skills(CREDENTIAL) == ()
            assert queries == 2

    with WirePeer(lambda conn, *_: reply_json(conn, 200, [])) as peer:
        asyncio.run(exercise(peer.port))
    assert peer.connections == len(peer.requests) == 1


def test_upload_is_chunked_in_memory_and_bounded_under_backpressure(monkeypatch) -> None:
    class StalledStream:
        def __init__(self):
            self.sizes = []
            self.closed = False

        async def send(self, data):
            self.sizes.append(len(data))
            if len(self.sizes) == 2:
                await asyncio.Event().wait()

        async def receive(self, _):
            pytest.fail("Response read began before the request upload finished")

        async def aclose(self):
            self.closed = True

    stream = StalledStream()
    connections = 0

    async def connect(*_):
        nonlocal connections
        connections += 1
        return stream

    monkeypatch.setattr(anyio, "connect_tcp", connect)

    async def exercise():
        async with AsyncSidecarClient("http://localhost", response_timeout=BUDGET) as client:
            with pytest.raises(AcceptanceUnknown):
                await asyncio.wait_for(client.submit("echo", {"text": "x" * 200_000}, CREDENTIAL), 1)
            assert stream.closed

    asyncio.run(exercise())
    assert connections == 1
    assert 0 < stream.sizes[0] < 64 * 1024 == stream.sizes[1]


@pytest.mark.parametrize(("mode", "expected"), [
    ("mismatch", ProtocolFailure),
    ("malformed", ProtocolFailure),
    ("http", HttpFailure),
    ("stall", WaitTerminated),
    ("http-stall", HttpFailure),
    ("response-deadline", ProtocolFailure),
])
def test_wait_keeps_last_valid_snapshot_and_stops_on_first_error(mode, expected) -> None:
    released = Event()

    def respond(conn, _, index):
        if index == 0:
            reply_json(conn, 200, snapshot("RUNNING") | {"events": [{
                "timestamp": CREATED, "level": "INFO", "type": "started", "details": {"attempt": 1},
            }]})
        elif mode == "mismatch":
            reply_json(conn, 200, snapshot() | {"id": str(uuid4())})
        elif mode == "malformed":
            reply(conn, 200, b"not JSON")
        elif mode == "http":
            reply_json(conn, 403, {"title": "Forbidden"})
        else:
            status = 503 if mode == "http-stall" else 200
            conn.sendall(f"HTTP/1.1 {status} Test\r\nContent-Length: 1000\r\n\r\n{{".encode())
            assert read_until_peer_closed(conn) == 0
            released.set()

    async def exercise(url):
        short_response = mode == "response-deadline"
        async with AsyncSidecarClient(url, response_timeout=BUDGET if short_response else 5) as client:
            options = WaitOptions(3 if short_response else BUDGET, 0.02)
            with pytest.raises(expected) as raised:
                await asyncio.wait_for(client.wait_for_completion(EXECUTION_ID, CREDENTIAL, options), 1.3)
            error = raised.value
            assert error.execution_id == EXECUTION_ID
            assert error.last_snapshot.id == EXECUTION_ID
            assert error.last_snapshot.status is ExecutionStatus.RUNNING
            assert error.last_snapshot.events[0].details == {"attempt": 1}
            if mode == "http":
                assert error.status_code == 403 and error.problem == {"title": "Forbidden"}
            if mode == "http-stall":
                assert error.status_code == 503 and error.problem is None
            if mode.endswith("stall") or short_response:
                assert await asyncio.to_thread(released.wait, 1)

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert len(peer.requests) == 2 and all(r.method == "GET" for r in peer.requests)


@pytest.mark.parametrize("stall", [False, True])
def test_wait_expires_during_first_read_or_poll_delay(stall) -> None:
    def respond(conn, *_):
        if stall:
            assert read_until_peer_closed(conn) == 0
        else:
            reply_json(conn, 200, snapshot())

    async def exercise(url):
        async with AsyncSidecarClient(url) as client:
            with pytest.raises(WaitTerminated) as raised:
                await asyncio.wait_for(
                    client.wait_for_completion(EXECUTION_ID, CREDENTIAL, WaitOptions(BUDGET, 10)), 1.3
                )
            assert raised.value.execution_id == EXECUTION_ID
            if stall:
                assert raised.value.last_snapshot is None
            else:
                assert raised.value.last_snapshot.status is ExecutionStatus.QUEUED

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == len(peer.requests) == 1


async def cancel_task(task) -> None:
    assert task.cancel("caller cancellation")
    with pytest.raises(asyncio.CancelledError) as raised:
        await asyncio.wait_for(task, 1)
    assert type(raised.value) is asyncio.CancelledError
    assert raised.value.args == ("caller cancellation",)
    assert vars(raised.value) == {}
    assert raised.value.__cause__ is None


@pytest.mark.parametrize("mode", ["header", "body", "outer-timeout", "submission"])
def test_native_cancellation_releases_network_without_replay(monkeypatch, mode) -> None:
    released = Event()
    loop = reached_read = None

    def respond(conn, _, index):
        if index:
            reply_json(conn, 200, snapshot("COMPLETED") | {"result": ""})
            return
        if mode == "submission":
            conn.sendall(b"HTTP/1.1 202 Accepted\r\nContent-Length: 512\r\n\r\n"
                         + json.dumps({"id": str(EXECUTION_ID)}).encode())
        elif mode in ("body", "outer-timeout"):
            conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Length: 512\r\n\r\n{")
        else:
            loop.call_soon_threadsafe(reached_read.set)
        assert read_until_peer_closed(conn) == 0
        released.set()

    parse = _HttpExchange.response_body

    def observe_body_read(wire, deadline):
        body = parse(wire, deadline)
        if body is None and wire.status is not None:
            reached_read.set()
        return body

    monkeypatch.setattr(_HttpExchange, "response_body", observe_body_read)

    async def exercise(url):
        nonlocal loop, reached_read
        loop = asyncio.get_running_loop()
        reached_read = asyncio.Event()
        async with AsyncSidecarClient(url) as client:
            if mode == "outer-timeout":
                async with asyncio.timeout(None) as outer:
                    task = asyncio.create_task(client.wait_for_completion(EXECUTION_ID, CREDENTIAL))
                    await asyncio.wait_for(reached_read.wait(), 1)
                    outer.reschedule(loop.time() + 0.02)
                    # An outer asyncio timeout cancels its awaited task as well.
                    await task
                pytest.fail("Outer application timeout did not fire")
            if mode == "submission":
                task = asyncio.create_task(client.submit("echo", {}, CREDENTIAL))
            else:
                task = asyncio.create_task(client.wait_for_completion(EXECUTION_ID, CREDENTIAL))
            await asyncio.wait_for(reached_read.wait(), 1)
            await cancel_task(task)
            assert await asyncio.to_thread(released.wait, 1)
            if mode != "submission":
                later = await client.get_execution(EXECUTION_ID, CREDENTIAL)
                assert later.status.terminal and later.result == ""

    with WirePeer(respond) as peer:
        if mode == "outer-timeout":
            with pytest.raises(TimeoutError) as raised:
                asyncio.run(exercise(peer.url))
            assert type(raised.value) is TimeoutError and released.wait(1)
        else:
            asyncio.run(exercise(peer.url))
    expected = ["POST"] if mode == "submission" else ["GET", "GET"]
    assert [r.method for r in peer.requests] == (["GET"] if mode == "outer-timeout" else expected)


def test_caller_cancellation_keeps_its_message_when_client_closes() -> None:
    loop = arrived = None
    released = Event()

    def respond(conn, *_):
        loop.call_soon_threadsafe(arrived.set)
        assert read_until_peer_closed(conn) == 0
        released.set()

    async def exercise(url):
        nonlocal loop, arrived
        loop = asyncio.get_running_loop()
        arrived = asyncio.Event()
        async with AsyncSidecarClient(url) as client:
            task = asyncio.create_task(client.get_execution(EXECUTION_ID, CREDENTIAL))
            await asyncio.wait_for(arrived.wait(), 1)
            # Queue native cancellation before close wakes the exchange task.
            loop.call_soon(task.cancel, "caller cancellation")
            await client.aclose()
            with pytest.raises(asyncio.CancelledError) as error:
                await asyncio.wait_for(task, 1)
            assert error.value.args == ("caller cancellation",)
            assert vars(error.value) == {}
            assert await asyncio.to_thread(released.wait, 1)

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert peer.connections == len(peer.requests) == 1


@pytest.mark.parametrize("closing", [False, True])
def test_cancel_or_close_during_poll_sleep_stops_until_explicit_read(monkeypatch, closing) -> None:
    delay = _AsyncTransport.poll_delay
    reached_sleep = None

    async def observe_sleep(transport, seconds):
        reached_sleep.set()
        return await delay(transport, seconds)

    monkeypatch.setattr(_AsyncTransport, "poll_delay", observe_sleep)

    def respond(conn, _, index):
        reply_json(conn, 200, snapshot("COMPLETED" if index else "RUNNING"))

    async def exercise(url):
        nonlocal reached_sleep
        reached_sleep = asyncio.Event()
        async with AsyncSidecarClient(url) as client:
            waiting = asyncio.create_task(client.wait_for_completion(
                EXECUTION_ID, CREDENTIAL, WaitOptions(5, 4)
            ))
            await asyncio.wait_for(reached_sleep.wait(), 1)
            if closing:
                await client.aclose()
                with pytest.raises(LocalFailure) as error:
                    await asyncio.wait_for(waiting, 1)
                assert error.value.execution_id == EXECUTION_ID
                assert error.value.last_snapshot.status is ExecutionStatus.RUNNING
                with pytest.raises(LocalFailure):
                    await client.get_execution(EXECUTION_ID, CREDENTIAL)
            else:
                await cancel_task(waiting)
            assert len(peer.requests) == 1
        async with AsyncSidecarClient(url) as later:
            assert (await later.get_execution(EXECUTION_ID, CREDENTIAL)).status.terminal

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert len(peer.requests) == 2 and all(r.method == "GET" for r in peer.requests)


@pytest.mark.parametrize("mode", ["timeout", "exchange-timeout", "cancel", "close"])
def test_pending_tls_handshake_releases_socket_without_dispatch(mode) -> None:
    released = Event()
    loop = hello = None
    _, context = verified_tls_contexts()

    def respond(conn):
        assert conn.recv(8192)  # ClientHello is sent before the peer stalls.
        loop.call_soon_threadsafe(hello.set)
        read_until_peer_closed(conn)
        released.set()

    async def exercise(url):
        nonlocal loop, hello
        loop = asyncio.get_running_loop()
        hello = asyncio.Event()
        async with AsyncSidecarClient(
            url, ssl_context=context,
            connect_timeout=BUDGET if mode == "timeout" else 3,
            response_timeout=BUDGET if mode == "exchange-timeout" else 5,
        ) as client:
            task = asyncio.create_task(client.get_execution(EXECUTION_ID, CREDENTIAL))
            await asyncio.wait_for(hello.wait(), 1)
            if mode == "cancel":
                await cancel_task(task)
            else:
                if mode == "close":
                    await asyncio.wait_for(asyncio.gather(client.aclose(), client.aclose()), 1)
                with pytest.raises(LocalFailure if mode == "close" else ProtocolFailure) as error:
                    await asyncio.wait_for(task, 1)
                assert error.value.execution_id == EXECUTION_ID
                assert error.value.last_snapshot is None
            assert await asyncio.to_thread(released.wait, 1)

    with WirePeer(raw_handle=respond) as peer:
        asyncio.run(exercise(f"https://127.0.0.1:{peer.port}"))
    assert peer.connections == 1 and not peer.requests


@pytest.mark.parametrize("submission", [False, True])
def test_close_aborts_active_body_and_rejects_new_calls(monkeypatch, submission) -> None:
    released = Event()
    reached_body = None
    parse = _HttpExchange.response_body

    def observe_body_read(wire, deadline):
        result = parse(wire, deadline)
        if result is None and wire.status is not None:
            reached_body.set()
        return result

    monkeypatch.setattr(_HttpExchange, "response_body", observe_body_read)

    def respond(conn, *_):
        if submission:
            # A complete JSON object cannot establish acceptance until framing ends.
            body = json.dumps({"id": str(EXECUTION_ID)}).encode()
            conn.sendall(b"HTTP/1.1 202 Accepted\r\nConnection: close\r\n\r\n" + body)
        else:
            conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Length: 512\r\n\r\n{")
        assert read_until_peer_closed(conn) == 0
        released.set()

    async def exercise(url):
        nonlocal reached_body
        reached_body = asyncio.Event()
        client = AsyncSidecarClient(url)
        async with client:
            if submission:
                task = asyncio.create_task(client.submit("echo", {}, CREDENTIAL))
            else:
                task = asyncio.create_task(client.get_execution(EXECUTION_ID, CREDENTIAL))
            await asyncio.wait_for(reached_body.wait(), 1)
            if submission:
                async def interrupted_close():
                    # Cancel the caller at its first suspension while joining cleanup.
                    asyncio.current_task().cancel("shutdown owner stopped")
                    await client.aclose()

                with pytest.raises(asyncio.CancelledError, match="shutdown owner stopped"):
                    await asyncio.create_task(interrupted_close())
            await asyncio.wait_for(asyncio.gather(client.aclose(), client.aclose()), 1)
            with pytest.raises(AcceptanceUnknown if submission else ProtocolFailure) as error:
                await asyncio.wait_for(task, 1)
            assert error.value.execution_id == (None if submission else EXECUTION_ID)
            assert await asyncio.to_thread(released.wait, 1)
            with pytest.raises(LocalFailure) as error:
                await client.get_execution(EXECUTION_ID, CREDENTIAL)
            assert error.value.execution_id == EXECUTION_ID
        with pytest.raises(LocalFailure):
            async with client:
                pytest.fail("A closed client was reentered")

    with WirePeer(respond) as peer:
        asyncio.run(exercise(peer.url))
    assert [r.method for r in peer.requests] == ["POST" if submission else "GET"]


def test_context_exit_releases_active_connection() -> None:
    loop = arrived = None
    released = Event()

    def respond(conn, *_):
        loop.call_soon_threadsafe(arrived.set)
        assert read_until_peer_closed(conn) == 0
        released.set()

    with WirePeer(respond) as peer:
        client = AsyncSidecarClient(peer.url)  # A lifespan owner may configure it before the loop.

        async def exercise():
            nonlocal loop, arrived
            loop = asyncio.get_running_loop()
            arrived = asyncio.Event()
            async with client:
                task = asyncio.create_task(client.list_skills(CREDENTIAL))
                await asyncio.wait_for(arrived.wait(), 1)
            with pytest.raises(ProtocolFailure):
                await asyncio.wait_for(task, 1)
            assert await asyncio.to_thread(released.wait, 1)

        asyncio.run(exercise())
    assert len(peer.requests) == 1


@pytest.mark.parametrize(("status", "length_framed", "notify"), [
    (202, True, False), (202, False, True), (202, False, False), (503, False, False),
])
def test_verified_tls_requires_authenticated_eof_for_close_delimited_body(
    status, length_framed, notify
) -> None:
    server, context = verified_tls_contexts()
    body = json.dumps({"id": str(EXECUTION_ID)} if status == 202 else {"title": "Retry"}).encode()

    def respond(conn, *_):
        if length_framed:
            reply(conn, status, body)
            return
        conn.sendall(f"HTTP/1.1 {status} Test\r\nConnection: close\r\n\r\n".encode() + body)
        if notify:
            try:
                conn.unwrap().close()
            except OSError:
                pass

    async def exercise(url):
        async with AsyncSidecarClient(url, ssl_context=context) as client:
            if status == 503:
                with pytest.raises(HttpFailure) as error:
                    await client.submit("echo", {}, CREDENTIAL)
                assert error.value.status_code == 503 and error.value.problem is None
            elif length_framed or notify:
                assert (await client.submit("echo", {}, CREDENTIAL)).id == EXECUTION_ID
            else:
                with pytest.raises(AcceptanceUnknown):
                    await client.submit("echo", {}, CREDENTIAL)

    with WirePeer(respond, tls=server) as peer:
        asyncio.run(exercise(peer.url))
    assert len(peer.requests) == 1


def test_tls_verifies_private_trust_client_identity_hostname_and_alpn() -> None:
    ca = trustme.CA()
    context = ssl.create_default_context()
    ca.configure_trust(context)
    ca.issue_cert("sdk-client").configure_cert(context)
    context.set_alpn_protocols(["h2", "http/1.1"])

    def respond(conn, *_):
        assert conn.getpeercert()
        reply_json(conn, 200, [])

    def server_context(hostname, alpn):
        server = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ca.issue_cert(hostname).configure_cert(server)
        ca.configure_trust(server)
        server.verify_mode = ssl.CERT_REQUIRED
        server.set_alpn_protocols([alpn])
        return server

    async def exercise(url, trust, accepted):
        async with AsyncSidecarClient(url, ssl_context=trust) as client:
            if accepted:
                assert await client.list_skills(CREDENTIAL) == ()
            else:
                with pytest.raises(ProtocolFailure) as error:
                    await client.list_skills(CREDENTIAL)
                assert error.value.__cause__ is error.value.__context__ is None

    trusted_server = server_context("127.0.0.1", "http/1.1")
    with WirePeer(respond, tls=trusted_server) as peer:
        asyncio.run(exercise(peer.url, context, True))
    assert peer.connections == len(peer.requests) == 1
    for server, trust in (
        (trusted_server, ssl.create_default_context()),
        (server_context("localhost", "http/1.1"), context),
        (server_context("127.0.0.1", "h2"), context),
    ):
        def reject(conn):
            try:
                with server.wrap_socket(conn, server_side=True) as tls:
                    assert tls.recv(8192) == b""
            except (ssl.SSLError, BrokenPipeError, ConnectionAbortedError, ConnectionResetError):
                # The TLS stack may see a handshake alert or the client's immediate TCP abort.
                pass

        with WirePeer(raw_handle=reject) as peer:
            asyncio.run(exercise(f"https://127.0.0.1:{peer.port}", trust, False))
        assert peer.connections == 1 and not peer.requests


@pytest.mark.parametrize("mode", ["success", "failure", "timeout"])
def test_system_trust_loads_off_loop_and_shares_connect_budget(monkeypatch, mode) -> None:
    server, context = verified_tls_contexts()
    resume, finished, cached = Event(), Event(), Event()
    loop = entered = loop_thread = None
    loads = 0

    def load_context():
        nonlocal loads
        loads += 1
        assert get_ident() != loop_thread
        loop.call_soon_threadsafe(entered.set)
        try:
            assert resume.wait(2)
            if mode == "failure":
                raise OSError("Synthetic trust failure")
            return context
        finally:
            finished.set()

    monkeypatch.setattr(ssl, "create_default_context", load_context)

    if mode == "timeout":
        load_default_context = _AsyncTransport._load_default_context

        def observe_load(transport):
            loaded = load_default_context(transport)
            if loaded is not None:
                cached.set()
            return loaded

        monkeypatch.setattr(_AsyncTransport, "_load_default_context", observe_load)

    def respond(conn, *_):
        reply_json(conn, 200, snapshot("COMPLETED"))

    async def exercise(url):
        nonlocal loop, entered, loop_thread
        loop = asyncio.get_running_loop()
        entered = asyncio.Event()
        loop_thread = get_ident()
        async with AsyncSidecarClient(url, connect_timeout=BUDGET if mode == "timeout" else 2) as client:
            first = asyncio.create_task(client.get_execution(EXECUTION_ID, CREDENTIAL))
            await asyncio.wait_for(entered.wait(), 1)
            try:
                assert not first.done() and not finished.is_set()
                if mode == "timeout":
                    with pytest.raises(ProtocolFailure):
                        await asyncio.wait_for(first, 1)
                    assert not finished.is_set()
                    with pytest.raises(ProtocolFailure):
                        await asyncio.wait_for(client.get_execution(EXECUTION_ID, CREDENTIAL), 1)
                    assert loads == 1
                    resume.set()
                    assert await asyncio.to_thread(cached.wait, 1)
                    assert (await client.get_execution(EXECUTION_ID, CREDENTIAL)).status.terminal
                elif mode == "failure":
                    resume.set()
                    with pytest.raises(LocalFailure) as error:
                        await asyncio.wait_for(first, 1)
                    assert error.value.execution_id == EXECUTION_ID
                    assert "Synthetic trust failure" not in str(error.value)
                    assert error.value.__cause__ is error.value.__context__ is None
                else:
                    second = asyncio.create_task(client.get_execution(EXECUTION_ID, CREDENTIAL))
                    await asyncio.sleep(0)
                    assert not second.done() and loads == 1
                    resume.set()
                    for result in await asyncio.wait_for(asyncio.gather(first, second), 1):
                        assert result.status.terminal
            finally:
                resume.set()
                assert await asyncio.to_thread(finished.wait, 1)

    with WirePeer(respond, tls=server) as peer:
        asyncio.run(exercise(peer.url))
    assert loads == 1
    assert peer.connections == len(peer.requests) == {"success": 2, "failure": 0, "timeout": 1}[mode]
