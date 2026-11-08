import base64
import json
import logging
import os
from pathlib import Path
from threading import Event

import anyio
import pytest
from loomspan_sdk import (
    AuthenticatedIdentity,
    CallbackVerifier,
    ColocatedSigning,
    ExecutionTokenIssuer,
    InvalidCallbackCredential,
    LocalFailure,
    _callback,
    _callback_trust,
    _issuance,
)

NOW = 1792324800_123_456_789
UNAVAILABLE = "Callback overlap JWKS is unavailable. Provide a readable public JWKS file."
INVALID = "Callback overlap JWKS is invalid. Provide a valid nonempty public RSA JWKS file."


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def document(*keys):
    return json.dumps({"keys": list(keys)}).encode("utf-8")


def token_for_unknown_kid(token, kid):
    protected, payload, signature = token.split(".")
    header = json.loads(base64.urlsafe_b64decode(protected + "=" * (-len(protected) % 4)))
    return ".".join((b64(json.dumps(dict(header, kid=kid)).encode()), payload, signature))


@pytest.fixture
def keys(tmp_path, monkeypatch):
    monkeypatch.setattr(_issuance, "time_ns", lambda: NOW)
    monkeypatch.setattr(_callback, "time_ns", lambda: NOW)

    def build(label):
        directory = tmp_path / label
        directory.mkdir()
        issuer = ExecutionTokenIssuer(ColocatedSigning(directory))
        [jwk] = json.loads((directory / "jwks.json").read_bytes())["keys"]
        token = issuer.issue(AuthenticatedIdentity(label))._authorization().removeprefix(b"Bearer ").decode("ascii")
        return issuer, jwk, token

    return build


async def reject(verifier, token):
    with pytest.raises(InvalidCallbackCredential, match="^Invalid callback execution credential$"):
        await verifier.verify(token)


def test_no_overlap_does_not_read_export_or_start_refresh(tmp_path, monkeypatch, keys):
    issuer, _, token = keys("own")
    (tmp_path / "own/jwks.json").unlink()

    def unexpected(*args):
        pytest.fail("own-key-only mode performed overlap work")

    monkeypatch.setattr(_callback_trust, "_read_overlap", unexpected)
    monkeypatch.setattr(_callback_trust._CallbackTrust, "refresh", unexpected)

    async def verify():
        async with CallbackVerifier.open(issuer) as verifier:
            assert (await verifier.verify(token)).subject == "own"
            await reject(verifier, token_for_unknown_kid(token, "unknown"))
        await reject(verifier, token)

    anyio.run(verify)


@pytest.mark.parametrize(("content", "message"), [
    (None, UNAVAILABLE), (b"unreadable", UNAVAILABLE), (b"directory", UNAVAILABLE),
    (b"\xff", INVALID), (b"{private-test-marker", INVALID), (b"[]", INVALID), (b"{}", INVALID),
    (b'{"keys":[],"keys":[]}', INVALID), (b'{"keys":[]}', INVALID), (b'{"keys":"bad"}', INVALID),
])
def test_bad_initial_overlap_starts_no_refresh(tmp_path, monkeypatch, caplog, keys, content, message):
    issuer, _, _ = keys("own")
    overlap = tmp_path / "private-test-marker.json"
    if content == b"directory":
        overlap.mkdir()
    elif content is not None:
        overlap.write_bytes(content)
    if content == b"unreadable":
        def unreadable(path):
            raise PermissionError("private-test-marker")

        monkeypatch.setattr(_callback_trust, "_read_overlap", unreadable)

    def unexpected(*args):
        pytest.fail("initial invalid trust started refresh")

    monkeypatch.setattr(_callback_trust._CallbackTrust, "refresh", unexpected)

    async def verify():
        with pytest.raises(LocalFailure) as caught:
            async with CallbackVerifier.open(issuer, overlap_jwks=overlap):
                pytest.fail("bad initial overlap was accepted")
        assert str(caught.value) == message
        assert caught.value.__cause__ is None and caught.value.__context__ is None

    anyio.run(verify)
    assert not caplog.records


@pytest.mark.parametrize(("field", "value"), [
    ("kty", "EC"), ("kid", "same"), ("kid", "\u2003"), ("kid", None),
    ("n", "AQAB"), ("n", b64((2**2047).to_bytes(256))), ("n", "n+"), ("n", "AA=="), ("n", "A"),
    ("e", "AQ"), ("e", "BA"), ("e", "modulus"), ("e", 65537),
    ("use", "enc"), ("use", None), ("alg", "RS512"), ("alg", None),
    *((member, None) for member in ("d", "p", "q", "dp", "dq", "qi", "oth")),
])
def test_bad_later_candidate_rejects_complete_document(tmp_path, keys, field, value):
    issuer, own, _ = keys("own")
    first = dict(own, kid="same")
    second = dict(own, kid="second")
    second[field] = second["n"] if value == "modulus" else value
    overlap = tmp_path / "overlap.json"
    overlap.write_bytes(document(first, second))

    async def verify():
        with pytest.raises(LocalFailure) as caught:
            async with CallbackVerifier.open(issuer, overlap_jwks=overlap):
                pytest.fail("invalid later candidate did not reject the whole set")
        assert str(caught.value) == INVALID

    anyio.run(verify)


def test_identical_own_key_and_optional_metadata(tmp_path, keys):
    issuer, own, own_token = keys("own")
    _, other, other_token = keys("other")
    overlap = tmp_path / "overlap.json"
    overlap.write_bytes(document(own, {k: v for k, v in other.items() if k not in {"use", "alg"}}))

    async def verify():
        async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
            assert (await verifier.verify(own_token)).subject == "own"
            assert (await verifier.verify(other_token)).subject == "other"
        overlap.write_bytes(document(dict(other, kid=own["kid"])))
        with pytest.raises(LocalFailure) as caught:
            async with CallbackVerifier.open(issuer, overlap_jwks=overlap):
                pytest.fail("conflicting own key was accepted")
        assert str(caught.value) == INVALID

    anyio.run(verify)


def test_relative_overlap_keeps_startup_directory(tmp_path, monkeypatch, keys):
    issuer, _, _ = keys("own")
    _, first, first_token = keys("first")
    _, other, other_token = keys("other")
    _, replacement, replacement_token = keys("replacement")
    initial_directory = tmp_path / "first"
    other_directory = tmp_path / "other"
    overlap = Path("overlap.json")
    (initial_directory / overlap).write_bytes(document(first))
    (other_directory / overlap).write_bytes(document(other))
    tick = 0
    monkeypatch.setattr(_callback_trust, "monotonic", lambda: tick)
    monkeypatch.chdir(initial_directory)

    async def verify():
        nonlocal tick
        async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
            monkeypatch.chdir(other_directory)
            await reject(verifier, other_token)
            assert (await verifier.verify(first_token)).subject == "first"
            (initial_directory / overlap).write_bytes(document(replacement))
            tick += 1
            assert (await verifier.verify(replacement_token)).subject == "replacement"
            await reject(verifier, first_token)

    anyio.run(verify)


def test_runtime_withdrawal_and_safe_state_transitions(tmp_path, monkeypatch, caplog, keys):
    issuer, _, own_token = keys("own")
    _, old, old_token = keys("old")
    _, new, new_token = keys("new")
    overlap = tmp_path / "private-test-marker.json"
    overlap.write_bytes(document(old))
    tick = 0
    monkeypatch.setattr(_callback_trust, "monotonic", lambda: tick)
    caplog.set_level(logging.INFO, logger=_callback_trust.__name__)

    async def verify():
        nonlocal tick
        async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
            admitted = await verifier.verify(old_token)
            assert (await verifier.verify(own_token)).subject == "own"
            overlap.unlink()
            await reject(verifier, new_token)  # First miss observes removal.
            for _ in range(2):
                tick += 1
                await reject(verifier, own_token)
                await reject(verifier, old_token)

            overlap.write_bytes(document(new, dict(old, d="private-test-marker")))
            for _ in range(2):
                tick += 1
                await reject(verifier, new_token)
                await reject(verifier, own_token)

            overlap.write_bytes(document(new))
            tick += 1
            assert (await verifier.verify(new_token)).subject == "new"
            assert (await verifier.verify(own_token)).subject == "own"
            await reject(verifier, old_token)
            overlap.write_bytes(document(old))
            tick += 1
            assert (await verifier.verify(old_token)).subject == "old"
            await reject(verifier, new_token)
            monkeypatch.setattr(_callback, "time_ns", lambda: NOW + 1000_000_000_000)
            await reject(verifier, old_token)
            assert (admitted.issuer, admitted.subject, admitted.authorities) == ("loomspan-app", "old", ())

    anyio.run(verify)
    assert caplog.messages == [
        f"Callback trust withdrawn. {UNAVAILABLE}",
        f"Callback trust withdrawn. {INVALID}",
        "Callback trust restored.",
    ]
    assert "private-test-marker" not in caplog.text and all(record.exc_info is None for record in caplog.records)


def test_poll_withdraws_and_restores_without_request_misses(tmp_path, caplog, keys):
    issuer, own, token = keys("own")
    overlap = tmp_path / "overlap.json"
    overlap.write_bytes(document(own))
    caplog.set_level(logging.INFO, logger=_callback_trust.__name__)

    async def verify():
        async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
            overlap.unlink()
            with anyio.fail_after(5):
                while not caplog.messages:
                    await anyio.sleep(0.01)
            await reject(verifier, token)
            overlap.write_bytes(document(own))
            with anyio.fail_after(5):
                while "Callback trust restored." not in caplog.messages:
                    await anyio.sleep(0.01)
            assert (await verifier.verify(token)).subject == "own"

    anyio.run(verify)


def test_global_miss_throttle_offload_and_complete_snapshot(tmp_path, monkeypatch, keys):
    issuer, _, own_token = keys("own")
    _, old, old_token = keys("old")
    _, new, new_token = keys("new")
    overlap = tmp_path / "overlap.json"
    overlap.write_bytes(document(old))
    before = overlap.stat()
    tick = 0.0
    read_overlap = _callback_trust._read_overlap
    reads = 0
    release = Event()
    monkeypatch.setattr(_callback_trust, "monotonic", lambda: tick)

    async def verify():
        nonlocal tick
        started = anyio.Event()

        def read(path):
            nonlocal reads
            reads += 1
            if reads in (2, 3):
                anyio.from_thread.run_sync(started.set)
                assert release.wait(5)
            return read_overlap(path)

        monkeypatch.setattr(_callback_trust, "_read_overlap", read)
        with anyio.fail_after(5):
            async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
                try:
                    async with anyio.create_task_group() as requests:
                        scopes = []

                        async def cancelled_waiter():
                            with anyio.CancelScope() as scope:
                                scopes.append(scope)
                                await reject(verifier, new_token)
                            assert scope.cancelled_caught

                        requests.start_soon(cancelled_waiter)
                        await started.wait()
                        for index in range(40):
                            requests.start_soon(reject, verifier, token_for_unknown_kid(own_token, f"unknown-{index}"))
                        await anyio.wait_all_tasks_blocked()
                        assert (await verifier.verify(old_token)).subject == "old"
                        assert (await verifier.verify(own_token)).subject == "own"
                        assert reads == 2
                        scopes[0].cancel()
                        await anyio.wait_all_tasks_blocked()
                        release.set()
                finally:
                    release.set()
                assert reads == 2
                overlap.write_bytes(document(new))
                os.utime(overlap, ns=(before.st_atime_ns, before.st_mtime_ns))
                assert (overlap.stat().st_size, overlap.stat().st_mtime_ns) == (before.st_size, before.st_mtime_ns)
                tick = 0.999
                await reject(verifier, new_token)
                assert (await verifier.verify(old_token)).subject == "old"
                assert reads == 2
                tick = 1.0
                started = anyio.Event()
                release.clear()

                async def accept_new():
                    assert (await verifier.verify(new_token)).subject == "new"

                async with anyio.create_task_group() as requests:
                    requests.start_soon(accept_new)
                    try:
                        await started.wait()
                        assert (await verifier.verify(old_token)).subject == "old"
                        assert (await verifier.verify(own_token)).subject == "own"
                    finally:
                        release.set()
                assert (await verifier.verify(own_token)).subject == "own"
                await reject(verifier, old_token)
                assert reads == 3
        await reject(verifier, own_token)

    anyio.run(verify)


def test_close_releases_waiter_before_inflight_read_finishes(tmp_path, monkeypatch, keys):
    issuer, own, token = keys("own")
    _, new, new_token = keys("new")
    overlap = tmp_path / "overlap.json"
    overlap.write_bytes(document(own))
    read_overlap = _callback_trust._read_overlap
    reads = 0
    release = Event()

    async def verify():
        started = anyio.Event()

        def read(path):
            nonlocal reads
            reads += 1
            if reads == 2:
                anyio.from_thread.run_sync(started.set)
                assert release.wait(5)
            return read_overlap(path)

        monkeypatch.setattr(_callback_trust, "_read_overlap", read)
        with anyio.fail_after(5):
            async with anyio.create_task_group() as requests:
                async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
                    overlap.write_bytes(document(new))

                    async def waiter():
                        try:
                            await reject(verifier, new_token)
                        finally:
                            release.set()

                    requests.start_soon(waiter)
                    await started.wait()
                assert release.is_set()
                await reject(verifier, new_token)
                await reject(verifier, token)
                assert reads == 2

    anyio.run(verify)


@pytest.mark.parametrize("cancelled", [False, True])
def test_scope_failure_stops_refresh_and_preserves_exit(tmp_path, keys, cancelled):
    issuer, own, token = keys("own")
    overlap = tmp_path / "overlap.json"
    overlap.write_bytes(document(own))

    async def verify():
        if cancelled:
            with anyio.CancelScope() as scope:
                async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
                    scope.cancel()
                    await anyio.sleep_forever()
            assert scope.cancelled_caught
        else:
            with pytest.raises(RuntimeError, match="^caller failed$"):
                async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
                    raise RuntimeError("caller failed")
        await reject(verifier, token)

    anyio.run(verify)
