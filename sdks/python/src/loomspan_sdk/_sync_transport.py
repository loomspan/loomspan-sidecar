from __future__ import annotations

import socket
import ssl
from dataclasses import dataclass
from threading import Lock
from time import monotonic

import h11

from ._credential import CredentialSnapshot
from ._errors import LocalFailure
from ._http import _HttpExchange, _READ_SIZE, _remaining, _verified_context
from ._protocol import _CLIENT_CLOSED, _Origin, _Response


@dataclass(eq=False, slots=True)
class _Exchange:
    sock: socket.socket | None = None
    attempted_connect: bool = False


class _Closed(OSError):
    pass


def _send(sock: socket.socket, data: bytes, deadline: float) -> None:
    view = memoryview(data)
    while view:
        sock.settimeout(_remaining(deadline))
        sent = sock.send(view)
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
                raise LocalFailure(_CLIENT_CLOSED)

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
        wire = _HttpExchange(self._origin, self._limit)
        now = monotonic()
        deadline = now + self._response_timeout
        if wait_deadline is not None:
            deadline = min(deadline, wait_deadline)
        connect_deadline = min(deadline, now + self._connect_timeout)
        try:
            with self._lock:
                if self._closed:
                    return _Response(local_error=_CLIENT_CLOSED)
                self._active.add(exchange)
            sock = self._connect(exchange, connect_deadline)
            for part in wire.request_parts(method, path, body, credential):
                _send(sock, part, deadline)
            while (response_body := wire.response_body(deadline)) is None:
                sock.settimeout(_remaining(deadline))
                data = sock.recv(_READ_SIZE)
                if not data and self._closed:
                    # A local shutdown cannot finish a close-delimited response.
                    raise _Closed
                wire.receive(data)
            return _Response(wire.status, response_body)
        except _Closed:
            return _Response(
                wire.status, local_error=None if exchange.attempted_connect else _CLIENT_CLOSED
            )
        except (OSError, ValueError, h11.ProtocolError):
            return _Response(wire.status)
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
