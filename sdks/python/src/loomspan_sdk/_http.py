from __future__ import annotations

import ssl
from collections.abc import Iterator
from time import monotonic

import h11

from ._credential import CredentialSnapshot
from ._errors import LocalFailure
from ._models import _duration
from ._protocol import _Origin, parse_origin

_CONNECT_TIMEOUT = 2.0
_RESPONSE_TIMEOUT = 10.0
_MAX_RESPONSE_SIZE = 8 * 1024 * 1024
_READ_SIZE = 8192
_WRITE_SIZE = 64 * 1024
_TLS_CONTEXT_ERROR = (
    "HTTPS requires a client SSL context with certificate, hostname, and TLS closure validation"
)


def _client_options(
    base_url: str,
    connect_timeout: float,
    response_timeout: float,
    max_response_size: int,
    ssl_context: ssl.SSLContext | None,
) -> tuple[_Origin, float, float]:
    origin = parse_origin(base_url)
    connect_timeout = _duration(connect_timeout)
    response_timeout = _duration(response_timeout)
    if type(max_response_size) is not int or not 0 < max_response_size <= 2**31 - 9:
        raise LocalFailure("Invalid response size limit")
    if ssl_context is not None:
        if not isinstance(ssl_context, ssl.SSLContext):
            raise LocalFailure("Invalid SSL context")
        if origin.scheme == "https" and not _verified_context(ssl_context):
            raise LocalFailure(_TLS_CONTEXT_ERROR)
    return origin, connect_timeout, response_timeout


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


class _HttpExchange:
    """HTTP framing and response size accounting for one connection."""

    def __init__(self, origin: _Origin, limit: int) -> None:
        self._origin = origin
        self._limit = limit
        self._wire = h11.Connection(h11.CLIENT)
        self._body = bytearray()
        self.status: int | None = None

    def request_parts(
        self, method: str, path: str, body: bytes | None, credential: CredentialSnapshot
    ) -> Iterator[bytes]:
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
        start = self._wire.send(h11.Request(
            method=method, target=self._origin.base_path + path, headers=headers
        ))
        assert start is not None
        for offset in range(0, len(start), _WRITE_SIZE):
            yield start[offset:offset + _WRITE_SIZE]
        if body is not None:
            for offset in range(0, len(body), _WRITE_SIZE):
                data = self._wire.send(h11.Data(data=body[offset:offset + _WRITE_SIZE]))
                assert data is not None
                yield data
        end = self._wire.send(h11.EndOfMessage())
        if end:
            yield end

    def receive(self, data: bytes) -> None:
        self._wire.receive_data(data)

    def response_body(self, deadline: float) -> bytes | None:
        """Return a complete body, or None when the transport must read again."""
        while True:
            event = self._wire.next_event()
            if isinstance(event, h11.Response):
                self.status = event.status_code
                for name, value in event.headers:
                    if name == b"content-length" and int(value) > self._limit:
                        raise ValueError
            _remaining(deadline)
            if event is h11.NEED_DATA:
                return None
            if isinstance(event, h11.Data):
                if len(self._body) + len(event.data) > self._limit:
                    raise ValueError
                self._body.extend(event.data)
            elif isinstance(event, h11.EndOfMessage):
                return bytes(self._body)
            elif not isinstance(event, (h11.Response, h11.InformationalResponse)):
                raise h11.RemoteProtocolError("Incomplete HTTP response")
