from __future__ import annotations

import socket
import ssl
from dataclasses import dataclass
from threading import Lock
from time import monotonic

import h11

from ._credential import CredentialSnapshot
from ._errors import LocalFailure
from ._protocol import _Origin, _Response


@dataclass(eq=False, slots=True)
class _Exchange:
    sock: socket.socket | None = None
    status: int | None = None
    attempted_connect: bool = False


class _Closed(OSError):
    pass


def _verified_context(context: ssl.SSLContext) -> bool:
    return (
        context.protocol != ssl.PROTOCOL_TLS_SERVER
        and context.verify_mode == ssl.CERT_REQUIRED
        and context.check_hostname
        and not context.options & getattr(ssl, "OP_IGNORE_UNEXPECTED_EOF", 0)
    )


def _remaining(deadline: float) -> float:
    remaining = deadline - monotonic()
    if remaining <= 0:
        raise TimeoutError
    return remaining


def _send(sock: socket.socket, data: bytes, deadline: float) -> None:
    view = memoryview(data)
    while view:
        sock.settimeout(_remaining(deadline))
        sent = sock.send(view[:64 * 1024])
        if sent == 0:
            raise OSError
        view = view[sent:]


class _SyncTransport:
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
        self._lock = Lock()
        self._closed = False
        self._active: set[_Exchange] = set()

    def ensure_open(self) -> None:
        with self._lock:
            if self._closed:
                raise LocalFailure("Client is closed")

    def close(self) -> None:
        with self._lock:
            if self._closed:
                return
            self._closed = True
            for exchange in self._active:
                sock = exchange.sock
                if sock is None:
                    continue
                try:
                    # Unblock I/O without clearing the owner's active TLS state.
                    socket.socket.shutdown(sock, socket.SHUT_RDWR)
                except OSError:
                    # An unconnected socket can still be blocked in connect().
                    if not isinstance(sock, ssl.SSLSocket):
                        sock.close()

    def exchange(
        self,
        method: str,
        path: str,
        body: bytes | None,
        credential: CredentialSnapshot,
        wait_deadline: float | None,
    ) -> _Response:
        exchange = _Exchange()
        now = monotonic()
        deadline = now + self._response_timeout
        if wait_deadline is not None:
            deadline = min(deadline, wait_deadline)
        connect_deadline = min(deadline, now + self._connect_timeout)
        try:
            with self._lock:
                if self._closed:
                    return _Response(closed_before_dispatch=True)
                self._active.add(exchange)
            sock = self._connect(exchange, connect_deadline)
            wire = h11.Connection(h11.CLIENT)
            self._write(wire, sock, method, path, body, credential, deadline)
            response_body = self._read(wire, sock, exchange, deadline)
            return _Response(exchange.status, response_body)
        except _Closed:
            return _Response(exchange.status, closed_before_dispatch=not exchange.attempted_connect)
        except (OSError, ValueError, h11.ProtocolError):
            return _Response(exchange.status)
        finally:
            with self._lock:
                self._active.discard(exchange)
                if exchange.sock is not None:
                    exchange.sock.close()

    def _connect(self, exchange: _Exchange, deadline: float) -> socket.socket:
        _remaining(deadline)
        addresses = socket.getaddrinfo(
            self._origin.hostname, self._origin.port, type=socket.SOCK_STREAM
        )
        for family, socktype, protocol, _, address in addresses:
            with self._lock:
                if self._closed:
                    raise _Closed
                _remaining(deadline)
                exchange.sock = socket.socket(family, socktype, protocol)
                exchange.attempted_connect = True
            try:
                exchange.sock.settimeout(_remaining(deadline))
                exchange.sock.connect(address)
            except OSError:
                with self._lock:
                    exchange.sock.close()
                    exchange.sock = None
                continue
            return self._wrap_tls(exchange, deadline)
        raise OSError

    def _wrap_tls(self, exchange: _Exchange, deadline: float) -> socket.socket:
        with self._lock:
            if self._closed:
                raise _Closed
            sock = exchange.sock
            assert sock is not None
            context = self._ssl_context
            if context is None:
                return sock
            _remaining(deadline)
            if not _verified_context(context):
                raise OSError
            exchange.sock = context.wrap_socket(
                sock,
                server_hostname=self._origin.hostname.partition("%")[0],
                do_handshake_on_connect=False,
                suppress_ragged_eofs=False,
            )
            tls = exchange.sock
        tls.settimeout(_remaining(deadline))
        tls.do_handshake()
        if tls.selected_alpn_protocol() not in (None, "http/1.1"):
            raise OSError
        return tls

    def _write(
        self,
        wire: h11.Connection,
        sock: socket.socket,
        method: str,
        path: str,
        body: bytes | None,
        credential: CredentialSnapshot,
        deadline: float,
    ) -> None:
        headers = [
            (b"Host", self._origin.authority.encode("ascii")),
            (b"Accept", b"application/json"),
            (b"Authorization", credential._authorization()),
            (b"Connection", b"close"),
        ]
        if body is not None:
            headers += [
                (b"Content-Type", b"application/json"),
                (b"Content-Length", str(len(body)).encode("ascii")),
            ]
        start = wire.send(h11.Request(
            method=method, target=self._origin.base_path + path, headers=headers
        ))
        assert start is not None
        _send(sock, start, deadline)
        if body is not None:
            data = wire.send(h11.Data(data=body))
            assert data is not None
            _send(sock, data, deadline)
        end = wire.send(h11.EndOfMessage())
        if end:
            _send(sock, end, deadline)

    def _read(
        self,
        wire: h11.Connection,
        sock: socket.socket,
        exchange: _Exchange,
        deadline: float,
    ) -> bytes:
        body = bytearray()
        while True:
            event = wire.next_event()
            if isinstance(event, h11.Response):
                exchange.status = event.status_code
                for name, value in event.headers:
                    if name == b"content-length" and int(value) > self._limit:
                        raise ValueError
            _remaining(deadline)
            if event is h11.NEED_DATA:
                sock.settimeout(_remaining(deadline))
                data = sock.recv(8192)
                if not data and self._closed:
                    # A local shutdown cannot finish a close-delimited response.
                    raise _Closed
                wire.receive_data(data)
            elif isinstance(event, h11.Data):
                if len(body) + len(event.data) > self._limit:
                    raise ValueError
                body.extend(event.data)
            elif isinstance(event, h11.EndOfMessage):
                return bytes(body)
            elif not isinstance(event, (h11.Response, h11.InformationalResponse)):
                raise h11.RemoteProtocolError("Incomplete HTTP response")
