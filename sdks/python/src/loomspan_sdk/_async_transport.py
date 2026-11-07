from __future__ import annotations

import ssl
from dataclasses import dataclass, field
from threading import Lock
from time import monotonic

import anyio
import h11
from anyio.abc import ByteStream
from anyio.streams.tls import TLSAttribute, TLSStream

from ._credential import CredentialSnapshot
from ._errors import LocalFailure
from ._http import _HttpExchange, _READ_SIZE, _TLS_CONTEXT_ERROR, _remaining, _verified_context
from ._protocol import _CLIENT_CLOSED, _Origin, _Response


@dataclass(eq=False, slots=True)
class _Exchange:
    scope: anyio.CancelScope
    done: anyio.Event = field(default_factory=anyio.Event)
    raw_stream: ByteStream | None = None
    sending: bool = False


class _AsyncTransport:
    def __init__(
        self,
        origin: _Origin,
        connect_timeout: float,
        response_timeout: float,
        max_response_size: int,
        ssl_context: ssl.SSLContext | None,
    ) -> None:
        self._origin = origin
        self._connect_timeout = connect_timeout
        self._response_timeout = response_timeout
        self._limit = max_response_size
        self._ssl_context = ssl_context
        self._trust_lock = anyio.Lock()
        self._trust_worker_lock = Lock()
        self._closed = anyio.Event()
        self._active: set[_Exchange] = set()

    def ensure_open(self) -> None:
        if self._closed.is_set():
            raise LocalFailure(_CLIENT_CLOSED)

    async def aclose(self) -> None:
        self._closed.set()
        active = tuple(self._active)
        for exchange in active:
            exchange.scope.cancel()
        for exchange in active:
            await exchange.done.wait()

    async def poll_delay(self, seconds: float) -> bool:
        """Return whether the client closed during this polling interval."""
        deadline = monotonic() + seconds
        async with anyio.create_task_group() as group:
            group.start_soon(self._wait_for_close, deadline)
        return self._closed.is_set()

    async def _wait_for_close(self, deadline: float) -> None:
        with anyio.move_on_after(max(0, deadline - monotonic())):
            await self._closed.wait()

    async def exchange(
        self,
        method: str,
        path: str,
        body: bytes | None,
        credential: CredentialSnapshot,
        wait_deadline: float | None,
    ) -> _Response:
        if self._closed.is_set():
            return _Response(local_error=_CLIENT_CLOSED)
        now = monotonic()
        deadline = now + self._response_timeout
        if wait_deadline is not None:
            deadline = min(deadline, wait_deadline)
        connect_deadline = min(deadline, now + self._connect_timeout)
        # Close and SDK timers cancel only the child. Cancelling the caller task
        # concurrently still delivers its own native cancellation.
        async with anyio.create_task_group() as group:
            exchange = group.start_soon(
                self._exchange, method, path, body, credential, deadline, connect_deadline
            )
        return exchange.return_value

    async def _exchange(
        self,
        method: str,
        path: str,
        body: bytes | None,
        credential: CredentialSnapshot,
        deadline: float,
        connect_deadline: float,
    ) -> _Response:
        if self._closed.is_set():
            return _Response(local_error=_CLIENT_CLOSED)
        exchange = _Exchange(anyio.CancelScope())
        wire = _HttpExchange(self._origin, self._limit)
        self._active.add(exchange)
        try:
            with exchange.scope, anyio.move_on_after(_remaining(deadline)):
                stream = None
                with anyio.move_on_after(_remaining(connect_deadline)) as connect_scope:
                    stream = await self._connect(exchange, connect_deadline)
                    _remaining(connect_deadline)
                if self._closed.is_set():
                    return _Response(local_error=_CLIENT_CLOSED)
                if connect_scope.cancel_called:
                    return _Response()
                if stream is None:
                    return _Response(local_error=_TLS_CONTEXT_ERROR)
                for part in wire.request_parts(method, path, body, credential):
                    _remaining(deadline)
                    exchange.sending = True
                    await stream.send(part)
                while (response_body := wire.response_body(deadline)) is None:
                    try:
                        data = await stream.receive(_READ_SIZE)
                    except anyio.EndOfStream:
                        if self._closed.is_set():
                            return _Response(wire.status)
                        data = b""
                    wire.receive(data)
                return _Response(wire.status, response_body)
        except (
            OSError, ValueError, h11.ProtocolError, anyio.BrokenResourceError, anyio.ClosedResourceError
        ):
            pass
        finally:
            try:
                if exchange.raw_stream is not None:
                    # Close TCP without a peer-dependent TLS shutdown or draining.
                    await anyio.aclose_forcefully(exchange.raw_stream)
            except (OSError, anyio.BrokenResourceError, anyio.ClosedResourceError):
                pass
            finally:
                self._active.discard(exchange)
                exchange.done.set()
        if self._closed.is_set() and not exchange.sending:
            return _Response(local_error=_CLIENT_CLOSED)
        return _Response(wire.status)

    async def _connect(self, exchange: _Exchange, deadline: float) -> ByteStream | None:
        context = None
        if self._origin.scheme == "https":
            context = await self._https_context()
            if context is None:
                return None
        _remaining(deadline)
        raw_stream = await anyio.connect_tcp(self._origin.hostname, self._origin.port)
        exchange.raw_stream = raw_stream
        if context is None:
            return raw_stream
        _remaining(deadline)
        if not _verified_context(context):
            raise OSError
        tls = await TLSStream.wrap(
            raw_stream,
            hostname=self._origin.hostname.partition("%")[0],
            ssl_context=context,
            standard_compatible=True,
        )
        if tls.extra(TLSAttribute.alpn_protocol, None) not in (None, "http/1.1"):
            raise OSError
        return tls

    async def _https_context(self) -> ssl.SSLContext | None:
        if self._ssl_context is None:
            async with self._trust_lock:
                if self._ssl_context is None:
                    return await anyio.to_thread.run_sync(
                        self._load_default_context, abandon_on_cancel=True
                    )
        return self._ssl_context

    def _load_default_context(self) -> ssl.SSLContext | None:
        # An abandoned await releases the async lock before the worker stops.
        if not self._trust_worker_lock.acquire(blocking=False):
            raise OSError
        try:
            if self._ssl_context is None:
                try:
                    context = ssl.create_default_context()
                except (OSError, ValueError):
                    return None
                if _verified_context(context):
                    self._ssl_context = context
            return self._ssl_context
        finally:
            self._trust_worker_lock.release()
