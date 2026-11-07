import gzip
import json
import os
import select
import signal
import socket
import ssl
import sys
import traceback
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, Event, current_thread, main_thread
from time import monotonic, sleep
from uuid import uuid4

import pytest
import trustme

from loomspan_sdk import (
    AcceptanceUnknown,
    CredentialSnapshot,
    ExecutionStatus,
    HttpFailure,
    LocalFailure,
    ProtocolFailure,
    SidecarClient,
    WaitOptions,
    WaitTerminated,
)

from test_protocol import EXECUTION_ID, descriptor, snapshot
from wire_fixture import WirePeer, read_until_peer_closed, reply, reply_json

TOKEN = "distinct-synthetic-bearer"
CREDENTIAL = CredentialSnapshot(TOKEN)
BUDGET = 0.30


def test_workflow_preserves_wire_contract() -> None:
    result = " \n{malformed 日本語"

    def respond(conn, request, index):
        if index == 0:
            reply_json(conn, 200, [descriptor()])
        elif index == 1:
            reply_json(conn, 200, descriptor())
        elif index == 2:
            reply_json(
                conn, 202, {"id": str(EXECUTION_ID)},
                headers=(("Location", "http://127.0.0.1:1/wrong"),),
            )
        elif index in (3, 4):
            reply_json(conn, 200, snapshot("QUEUED" if index == 3 else "RUNNING"))
        else:
            reply_json(conn, 200, snapshot("FAILED") | {"result": result, "events": []})

    with WirePeer(respond) as peer, SidecarClient(peer.url + "/prefix/%2f/") as client:
        assert client.list_skills(CREDENTIAL)[0].name == "default-echo"
        assert client.get_skill("café ?", CREDENTIAL).name == "default-echo"
        accepted = client.submit("café ?", {"text": "日本語", "arr": [1, None]}, CREDENTIAL)
        assert accepted.id == EXECUTION_ID
        assert client.get_execution(accepted.id, CREDENTIAL).status is ExecutionStatus.QUEUED
        failed = client.wait_for_completion(accepted.id, CREDENTIAL, WaitOptions(1.0, 0.01))
        assert failed.status is ExecutionStatus.FAILED
        assert failed.result == result and failed.events == () and failed.failure.kind == "VALIDATION"
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
    post = peer.requests[2]
    assert post.headers["content-type"] == "application/json"
    assert json.loads(post.body) == {"text": "日本語", "arr": [1, None]}


def test_invalid_calls_do_not_dispatch_and_keep_status_context() -> None:
    with WirePeer(lambda *_: pytest.fail("Unexpected dispatch")) as peer:
        with SidecarClient(peer.url) as client:
            with pytest.raises(LocalFailure):
                client.submit("echo", {"bad": {1: "bad key"}}, CREDENTIAL)
            with pytest.raises(LocalFailure):
                client.submit("echo", {"bad": float("nan")}, CREDENTIAL)
            with pytest.raises(LocalFailure):
                client.get_skill("dot/segment", CREDENTIAL)
            with pytest.raises(LocalFailure) as raised:
                client.get_execution(EXECUTION_ID, None)
            assert raised.value.execution_id == EXECUTION_ID
            with pytest.raises(LocalFailure) as raised:
                client.wait_for_completion(EXECUTION_ID, CREDENTIAL, None)
            assert raised.value.execution_id == EXECUTION_ID
    assert peer.connections == 0


@pytest.mark.parametrize(("status", "body"), [
    (202, b"{}"),
    (202, b'{"id":"invalid"}'),
    (202, b'{"id":null}'),
    (202, b'{"id":"a","id":"b"}'),
    (202, ('{"id":"%s"} false' % EXECUTION_ID).encode()),
    (200, ('{"id":"%s"}' % EXECUTION_ID).encode()),
    (204, b""),
])
def test_invalid_acceptance_is_not_replayed(status, body) -> None:
    with WirePeer(lambda conn, *_: reply(conn, status, body)) as peer:
        with SidecarClient(peer.url) as client:
            with pytest.raises(AcceptanceUnknown) as error:
                client.submit("echo", {}, CREDENTIAL)
            assert error.value.execution_id is None
            assert error.value.__context__ is None and error.value.__cause__ is None
    assert len(peer.requests) == 1 and peer.requests[0].method == "POST"


def test_complete_json_in_incomplete_acceptance_frame_is_uncertain() -> None:
    body = json.dumps({"id": str(EXECUTION_ID)}).encode()

    def respond(conn, *_):
        conn.sendall(b"HTTP/1.1 202 Accepted\r\nContent-Length: 512\r\n\r\n" + body)

    with WirePeer(respond) as peer, SidecarClient(peer.url) as client:
        with pytest.raises(AcceptanceUnknown) as error:
            client.submit("echo", {}, CREDENTIAL)
        assert error.value.execution_id is None
    assert len(peer.requests) == 1


def test_read_requires_200_and_recovers_after_protocol_failure() -> None:
    def respond(conn, request, index):
        if index == 0:
            reply_json(conn, 202, snapshot("COMPLETED"))
        elif index == 1:
            reply_json(conn, 200, snapshot("COMPLETED") | {"id": str(uuid4())})
        else:
            reply_json(conn, 200, snapshot("COMPLETED") | {"result": ""})

    with WirePeer(respond) as peer, SidecarClient(peer.url) as client:
        for _ in range(2):
            with pytest.raises(ProtocolFailure) as error:
                client.get_execution(EXECUTION_ID, CREDENTIAL)
            assert error.value.execution_id == EXECUTION_ID
            assert error.value.last_snapshot is None
        terminal = client.get_execution(EXECUTION_ID, CREDENTIAL)
        assert terminal.status is ExecutionStatus.COMPLETED and terminal.result == ""
    assert len(peer.requests) == 3


def test_concurrent_credentials_are_isolated_and_cookies_are_ignored() -> None:
    meeting = Barrier(2, timeout=2)

    def respond(conn, request, _):
        if request.target.endswith(("/first", "/second")):
            meeting.wait()
        reply_json(conn, 200, descriptor(), headers=(("Set-Cookie", "session=fixture"),))

    with WirePeer(respond) as peer, SidecarClient(peer.url) as client:
        with ThreadPoolExecutor(2) as callers:
            first = callers.submit(client.get_skill, "first", CredentialSnapshot("user-one"))
            second = callers.submit(client.get_skill, "second", CredentialSnapshot("user-two"))
            assert first.result(3).name == second.result(3).name == "default-echo"
        client.get_skill("third", CREDENTIAL)
    assert {r.target: r.headers["authorization"] for r in peer.requests} == {
        "/v1/skills/first": "Bearer user-one",
        "/v1/skills/second": "Bearer user-two",
        "/v1/skills/third": f"Bearer {TOKEN}",
    }
    assert all("cookie" not in r.headers for r in peer.requests)


@pytest.mark.parametrize("status", [302, 307, 401, 407])
@pytest.mark.parametrize("submission", [False, True])
def test_redirect_and_auth_challenge_are_not_replayed(status, submission) -> None:
    def respond(conn, _, index):
        assert index == 0
        reply_json(conn, status, {"title": "Try again"}, headers=(
            ("Location", peer.url + "/replay"),
            ("WWW-Authenticate", 'Bearer realm="fixture"'),
            ("Proxy-Authenticate", 'Basic realm="fixture"'),
        ))

    with WirePeer(respond) as peer, SidecarClient(peer.url) as client:
        with pytest.raises(HttpFailure) as error:
            if submission:
                client.submit("echo", {}, CREDENTIAL)
            else:
                client.list_skills(CREDENTIAL)
        assert error.value.status_code == status and error.value.problem == {"title": "Try again"}
    assert len(peer.requests) == 1


def test_content_is_not_decompressed() -> None:
    compressed = gzip.compress(json.dumps({"id": str(EXECUTION_ID)}).encode())

    def respond(conn, request, _):
        assert "accept-encoding" not in request.headers
        reply(conn, 202, compressed, headers=(("Content-Encoding", "gzip"),))

    with WirePeer(respond) as peer, SidecarClient(peer.url) as client:
        with pytest.raises(AcceptanceUnknown):
            client.submit("echo", {}, CREDENTIAL)
    assert len(peer.requests) == 1


@pytest.mark.parametrize(("partial", "trickle", "error_type"), [
    (b"HTTP/1.1 200 OK\r\nX-Partial:", False, ProtocolFailure),
    (b"HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n[", False, ProtocolFailure),
    (b"HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n[", True, ProtocolFailure),
    (b"HTTP/1.1 503 Unavailable\r\nContent-Length: 1000\r\n\r\n{", False, HttpFailure),
])
def test_absolute_deadline_closes_incomplete_peer_io(partial, trickle, error_type) -> None:
    released = Event()

    def respond(conn, request, index):
        if index:
            reply_json(conn, 200, [])
            return
        conn.sendall(partial)
        if trickle:
            guard = monotonic() + 1.4
            while monotonic() < guard:
                try:
                    if select.select([conn], [], [], 0.02)[0]:
                        break
                    conn.sendall(b" ")
                except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError):
                    released.set()
                    return
            else:
                raise AssertionError("Trickling socket was not closed by the client")
        assert read_until_peer_closed(conn) == 0
        released.set()

    with WirePeer(respond) as peer, SidecarClient(peer.url, response_timeout=BUDGET) as client:
        start = monotonic()
        with pytest.raises(error_type) as error:
            client.list_skills(CREDENTIAL)
        assert 0.18 <= monotonic() - start < 1.3
        if error_type is HttpFailure:
            assert error.value.status_code == 503 and error.value.problem is None
        assert released.wait(1.0)
        assert client.list_skills(CREDENTIAL) == ()
    assert len(peer.requests) == 2


@pytest.mark.parametrize(("framing", "status", "complete"), [
    (b"Content-Length: 4\r\n\r\n[]  ", 200, True),
    (b"Content-Length: 5\r\n\r\n[]   ", 200, False),
    (b"Content-Length: 90\r\n\r\n", 200, False),
    (b"Content-Length: 4\r\n\r\n[]", 200, False),
    (b"Transfer-Encoding: chunked\r\n\r\n2\r\n[]\r\n2\r\n  \r\n0\r\n\r\n", 200, True),
    (b"Transfer-Encoding: chunked\r\n\r\n3\r\n[] \r\n2\r\n  \r\n0\r\n\r\n", 200, False),
    (b"Connection: close\r\n\r\n[]  ", 200, True),
    (b"Connection: close\r\n\r\n[]   ", 200, False),
    (b"Transfer-Encoding: chunked\r\n\r\nnot-hex\r\n", 503, False),
    (b"Content-Length: 6\r\n\r\n{}", 503, False),
])
def test_bounded_response_framing(framing, status, complete) -> None:
    def respond(conn, *_):
        conn.sendall(f"HTTP/1.1 {status} Fixture\r\n".encode() + framing)

    with WirePeer(respond) as peer, SidecarClient(peer.url, max_response_size=4) as client:
        if complete:
            assert client.list_skills(CREDENTIAL) == ()
        elif status == 503:
            with pytest.raises(HttpFailure) as error:
                client.list_skills(CREDENTIAL)
            assert error.value.status_code == 503 and error.value.problem is None
        else:
            with pytest.raises(ProtocolFailure):
                client.list_skills(CREDENTIAL)
    assert len(peer.requests) == 1


@pytest.mark.parametrize("status", [202, 503])
def test_oversized_submission_response_closes_without_draining(status) -> None:
    released = Event()

    def respond(conn, *_):
        conn.sendall(f"HTTP/1.1 {status} Fixture\r\nContent-Length: 512\r\n\r\n".encode())
        assert read_until_peer_closed(conn) == 0
        released.set()

    with WirePeer(respond) as peer, SidecarClient(peer.url, max_response_size=48) as client:
        with pytest.raises(AcceptanceUnknown if status == 202 else HttpFailure) as error:
            client.submit("echo", {}, CREDENTIAL)
        if status == 503:
            assert error.value.status_code == 503 and error.value.problem is None
        assert released.wait(1)
    assert len(peer.requests) == 1


def test_informational_status_does_not_replace_final_response() -> None:
    def respond(conn, *_):
        conn.sendall(b"HTTP/1.1 103 Early Hints\r\nLink: </hint>\r\n\r\n")
        reply_json(conn, 200, [])

    with WirePeer(respond) as peer, SidecarClient(peer.url) as client:
        assert client.list_skills(CREDENTIAL) == ()


def test_connect_budget_covers_tls_handshake_and_releases_socket() -> None:
    released = Event()

    def respond(conn):
        assert read_until_peer_closed(conn) > 0  # The client sent its ClientHello.
        released.set()

    with WirePeer(raw_handle=respond) as peer:
        with SidecarClient(f"https://127.0.0.1:{peer.port}", connect_timeout=BUDGET) as client:
            start = monotonic()
            with pytest.raises(ProtocolFailure):
                client.list_skills(CREDENTIAL)
            assert 0.18 <= monotonic() - start < 1.3
            assert released.wait(1)
    assert peer.connections == 1 and peer.requests == []


def test_response_budget_stops_stalled_upload(monkeypatch) -> None:
    released = Event()
    payload_size = 16 * 1024 * 1024
    connect = socket.socket.connect

    def bounded_send_buffer(sock, address):
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 32 * 1024)
        return connect(sock, address)

    # Make backpressure deterministic on loopback, including Windows.
    monkeypatch.setattr(socket.socket, "connect", bounded_send_buffer)

    def respond(conn):
        sleep(0.55)
        assert 0 < read_until_peer_closed(conn) < payload_size
        released.set()

    with WirePeer(raw_handle=respond, receive_buffer=64 * 1024) as peer:
        with SidecarClient(peer.url, response_timeout=BUDGET) as client:
            start = monotonic()
            with pytest.raises(AcceptanceUnknown):
                client.submit("echo", {"text": "x" * payload_size}, CREDENTIAL)
            assert 0.18 <= monotonic() - start < 1.5
            assert released.wait(1.5)
    assert peer.connections == 1


def test_resolver_elapsed_time_is_charged_before_dispatch(monkeypatch) -> None:
    real_lookup = socket.getaddrinfo
    lookups = 0

    def slow_lookup(*args, **kwargs):
        nonlocal lookups
        lookups += 1
        if lookups == 1:
            sleep(0.10)
        return real_lookup(*args, **kwargs)

    monkeypatch.setattr(socket, "getaddrinfo", slow_lookup)
    with WirePeer(lambda conn, *_: reply_json(conn, 200, [])) as peer:
        with SidecarClient(peer.url, connect_timeout=0.07) as client:
            with pytest.raises(ProtocolFailure):
                client.list_skills(CREDENTIAL)
            assert client.list_skills(CREDENTIAL) == ()
    assert lookups == 2 and len(peer.requests) == 1 and peer.connections == 1


@pytest.mark.parametrize(("mode", "expected"), [
    ("mismatch", ProtocolFailure),
    ("malformed", ProtocolFailure),
    ("http", HttpFailure),
    ("stall", WaitTerminated),
    ("http-stall", HttpFailure),
])
def test_wait_stops_on_first_read_failure_with_context(mode, expected) -> None:
    released = Event()

    def respond(conn, _, index):
        if index == 0:
            reply_json(conn, 200, snapshot("RUNNING"))
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

    with WirePeer(respond) as peer, SidecarClient(peer.url, response_timeout=5) as client:
        start = monotonic()
        with pytest.raises(expected) as raised:
            client.wait_for_completion(EXECUTION_ID, CREDENTIAL, WaitOptions(BUDGET, 0.02))
        assert monotonic() - start < 1.3
        error = raised.value
        assert error.execution_id == EXECUTION_ID
        assert error.last_snapshot.id == EXECUTION_ID
        assert error.last_snapshot.status is ExecutionStatus.RUNNING
        if mode.endswith("stall"):
            assert released.wait(1)
        if mode == "http-stall":
            assert error.status_code == 503 and error.problem is None
    assert len(peer.requests) == 2
    assert all(r.method == "GET" for r in peer.requests)


@pytest.mark.parametrize("stall", [False, True])
def test_wait_expiry_during_first_read_or_poll_sleep(stall) -> None:
    def respond(conn, *_):
        if stall:
            assert read_until_peer_closed(conn) == 0
        else:
            reply_json(conn, 200, snapshot())

    with WirePeer(respond) as peer, SidecarClient(peer.url, response_timeout=5) as client:
        with pytest.raises(WaitTerminated) as raised:
            client.wait_for_completion(EXECUTION_ID, CREDENTIAL, WaitOptions(BUDGET, 10))
        assert raised.value.execution_id == EXECUTION_ID
        if stall:
            assert raised.value.last_snapshot is None
        else:
            assert raised.value.last_snapshot.status is ExecutionStatus.QUEUED
    assert len(peer.requests) == 1


def test_close_aborts_active_read_and_rejects_new_calls() -> None:
    started = Event()
    released = Event()

    def respond(conn, *_):
        conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n{")
        started.set()
        assert read_until_peer_closed(conn) == 0
        released.set()

    with WirePeer(respond) as peer:
        with SidecarClient(peer.url) as client, ThreadPoolExecutor(1) as callers:
            reading = callers.submit(client.get_execution, EXECUTION_ID, CREDENTIAL)
            assert started.wait(1.5)
            client.close()
            client.close()
            assert released.wait(1)
            with pytest.raises(ProtocolFailure) as error:
                reading.result(1)
            assert error.value.execution_id == EXECUTION_ID
            with pytest.raises(LocalFailure) as error:
                client.get_execution(EXECUTION_ID, CREDENTIAL)
            assert error.value.execution_id == EXECUTION_ID
        with pytest.raises(LocalFailure):
            with client:
                pytest.fail("A closed client was reentered")
    assert len(peer.requests) == 1 and peer.requests[0].method == "GET"


def _verified_tls_contexts() -> tuple[ssl.SSLContext, ssl.SSLContext]:
    ca = trustme.CA()
    server = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ca.issue_cert("127.0.0.1").configure_cert(server)
    client = ssl.create_default_context()
    ca.configure_trust(client)
    return server, client


@pytest.mark.parametrize("tls", [False, True])
def test_close_aborts_pending_message_instead_of_delimiting_it(monkeypatch, tls) -> None:
    ready, resume, released = Event(), Event(), Event()
    response = b"HTTP/1.1 202 Accepted\r\nConnection: close\r\n\r\n"
    response += json.dumps({"id": str(EXECUTION_ID)}).encode()
    server_context, client_context = _verified_tls_contexts() if tls else (None, None)
    socket_type = ssl.SSLSocket if tls else socket.socket
    original_recv = socket_type.recv
    read = 0
    caller = None

    def receive(sock, *args, **kwargs):
        nonlocal read
        if current_thread() is caller and read == len(response):
            ready.set()
            assert resume.wait(3)
            if tls:
                # The request thread must retain ownership of the TLS socket.
                assert sock.fileno() != -1
        data = original_recv(sock, *args, **kwargs)
        if current_thread() is caller:
            read += len(data)
        return data

    def respond(conn, *_):
        conn.sendall(response)
        assert read_until_peer_closed(conn) == 0
        released.set()

    def submit():
        nonlocal caller
        caller = current_thread()
        return client.submit("echo", {}, CREDENTIAL)

    monkeypatch.setattr(socket_type, "recv", receive)
    with WirePeer(respond, tls=server_context) as peer:
        with SidecarClient(peer.url, ssl_context=client_context) as client, ThreadPoolExecutor(1) as callers:
            pending = callers.submit(submit)
            try:
                assert ready.wait(2)
                client.close()
                assert released.wait(1)
            finally:
                resume.set()
            with pytest.raises(AcceptanceUnknown):
                pending.result(1)
    assert len(peer.requests) == 1


@pytest.mark.parametrize(("status", "length_framed", "notify"), [
    (202, True, False), (202, False, True), (202, False, False), (503, False, False),
])
def test_https_requires_authenticated_eof_for_close_delimited_body(status, length_framed, notify) -> None:
    server_context, client_context = _verified_tls_contexts()
    body = json.dumps({"id": str(EXECUTION_ID)} if status == 202 else {"title": "Try again"}).encode()

    def respond(conn, *_):
        if length_framed:
            reply(conn, status, body)
            return
        conn.sendall(f"HTTP/1.1 {status} Test\r\nConnection: close\r\n\r\n".encode() + body)
        if notify:
            # unwrap sends close_notify before waiting for a reciprocal alert.
            try:
                conn.unwrap().close()
            except OSError:
                pass

    with WirePeer(respond, tls=server_context) as peer:
        with SidecarClient(peer.url, ssl_context=client_context) as client:
            if status == 503:
                with pytest.raises(HttpFailure) as error:
                    client.submit("echo", {}, CREDENTIAL)
                assert error.value.status_code == 503 and error.value.problem is None
            elif length_framed or notify:
                assert client.submit("echo", {}, CREDENTIAL).id == EXECUTION_ID
            else:
                with pytest.raises(AcceptanceUnknown):
                    client.submit("echo", {}, CREDENTIAL)
    assert len(peer.requests) == 1


def test_native_keyboard_interrupt_closes_an_incomplete_submission(monkeypatch) -> None:
    started = Event()
    released = Event()
    real_recv = socket.socket.recv

    def respond(conn, request, index):
        if index:
            reply_json(conn, 200, snapshot("COMPLETED"))
            return
        conn.sendall(b"HTTP/1.1 202 Accepted\r\nContent-Length: 512\r\n\r\n{")
        started.set()
        if sys.platform != "win32":
            sleep(0.06)
            os.kill(os.getpid(), signal.SIGINT)
        assert read_until_peer_closed(conn) == 0
        released.set()

    interrupted = False
    client_receives = 0

    def interrupt_recv(sock, *args, **kwargs):
        nonlocal client_receives, interrupted
        if current_thread() is main_thread() and not interrupted:
            client_receives += 1
            if client_receives == 2:
                assert started.wait(1)
                interrupted = True
                raise KeyboardInterrupt
        return real_recv(sock, *args, **kwargs)

    if sys.platform == "win32":
        monkeypatch.setattr(socket.socket, "recv", interrupt_recv)
    with WirePeer(respond) as peer, SidecarClient(peer.url) as client:
        with pytest.raises(KeyboardInterrupt) as raised:
            client.submit("echo", {}, CREDENTIAL)
        assert not hasattr(raised.value, "last_snapshot")
        assert released.wait(1)
        assert client.get_execution(EXECUTION_ID, CREDENTIAL).status.terminal
    assert [request.method for request in peer.requests] == ["POST", "GET"]


def test_keyboard_interrupt_during_poll_sleep_stops_without_adapter(monkeypatch) -> None:
    def interrupt(_):
        raise KeyboardInterrupt

    monkeypatch.setattr("loomspan_sdk._client.sleep", interrupt)
    with WirePeer(lambda conn, *_: reply_json(conn, 200, snapshot())) as peer:
        with SidecarClient(peer.url) as client:
            with pytest.raises(KeyboardInterrupt):
                client.wait_for_completion(EXECUTION_ID, CREDENTIAL)
    assert len(peer.requests) == 1 and peer.requests[0].method == "GET"


def test_server_role_ssl_context_is_rejected_before_dispatch() -> None:
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.verify_mode = ssl.CERT_REQUIRED
    context.check_hostname = True

    with WirePeer(lambda *_: pytest.fail("Unexpected dispatch")) as peer:
        with pytest.raises(LocalFailure) as error:
            SidecarClient(f"https://127.0.0.1:{peer.port}", ssl_context=context)
        assert error.value.__context__ is None
    assert peer.connections == 0


def test_verified_private_ca_and_client_identity() -> None:
    ca = trustme.CA()
    identity = ca.issue_cert("sdk-client")

    def server_context(name: str) -> ssl.SSLContext:
        server = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ca.issue_cert(name).configure_cert(server)
        ca.configure_trust(server)
        server.verify_mode = ssl.CERT_REQUIRED
        return server

    client_context = ssl.create_default_context()
    ca.configure_trust(client_context)
    identity.configure_cert(client_context)
    missing_identity = ssl.create_default_context()
    ca.configure_trust(missing_identity)

    def respond(conn, *_):
        assert conn.getpeercert()
        reply_json(conn, 200, [])

    with WirePeer(respond, tls=server_context("127.0.0.1")) as peer:
        with SidecarClient(peer.url, ssl_context=client_context) as client:
            assert client.list_skills(CREDENTIAL) == ()
        for context in (None, missing_identity):
            with SidecarClient(peer.url, ssl_context=context) as client:
                with pytest.raises(ProtocolFailure) as error:
                    client.list_skills(CREDENTIAL)
                assert error.value.__cause__ is None and error.value.__context__ is None
                assert TOKEN not in "".join(traceback.format_exception(error.value))
        insecure = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        insecure.check_hostname = False
        insecure.verify_mode = ssl.CERT_NONE
        with pytest.raises(LocalFailure):
            SidecarClient(peer.url, ssl_context=insecure)
        if hasattr(ssl, "OP_IGNORE_UNEXPECTED_EOF"):
            client_context.options |= ssl.OP_IGNORE_UNEXPECTED_EOF
            try:
                with pytest.raises(LocalFailure):
                    SidecarClient(peer.url, ssl_context=client_context)
            finally:
                client_context.options &= ~ssl.OP_IGNORE_UNEXPECTED_EOF
    assert len(peer.requests) == 1 and peer.connections == 3

    with WirePeer(respond, tls=server_context("localhost")) as wrong_host:
        with SidecarClient(wrong_host.url, ssl_context=client_context) as client:
            with pytest.raises(ProtocolFailure):
                client.list_skills(CREDENTIAL)
    assert wrong_host.connections == 1 and wrong_host.requests == []
