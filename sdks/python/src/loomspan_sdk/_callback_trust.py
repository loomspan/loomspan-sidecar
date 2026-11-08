import base64
import logging
import os
import re
import stat
from collections.abc import Mapping
from decimal import InvalidOperation
from pathlib import Path
from time import monotonic
from types import MappingProxyType

import anyio
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicKey, RSAPublicNumbers

from ._errors import LocalFailure
from ._issuance import _EXECUTION_ALGORITHM
from ._protocol import _array, _object, _text, decode_json
from ._types import JsonValue

_LOG = logging.getLogger(__name__)
_INTERVAL = 1.0
_UNAVAILABLE = "Callback overlap JWKS is unavailable. Provide a readable public JWKS file."
_INVALID = "Callback overlap JWKS is invalid. Provide a valid nonempty public RSA JWKS file."
_PRIVATE_MEMBERS = {"d", "p", "q", "dp", "dq", "qi", "oth"}
_BASE64URL = re.compile(r"[A-Za-z0-9_-]+")


def _decode_base64url(value: JsonValue) -> bytes:
    if not isinstance(value, str) or not _BASE64URL.fullmatch(value):
        raise ValueError
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def _read_overlap(path: Path) -> bytes:
    # A nonblocking open on POSIX also covers a FIFO swapped in after the path check.
    if not path.is_file():
        raise OSError
    flags = os.O_RDONLY | getattr(os, "O_NONBLOCK", 0) | getattr(os, "O_BINARY", 0)
    with os.fdopen(os.open(path, flags), "rb") as file:
        if not stat.S_ISREG(os.fstat(file.fileno()).st_mode):
            raise OSError
        return file.read()


def _candidate(
    path: Path, own: Mapping[str, RSAPublicKey],
) -> tuple[Mapping[str, RSAPublicKey], str | None]:
    try:
        content = _read_overlap(path)
    except (OSError, ValueError):
        return MappingProxyType({}), _UNAVAILABLE

    try:
        candidates = _array(_object(decode_json(content, exact_numbers=True)).get("keys"))
        if not candidates:
            raise ValueError
        overlap = {}
        for candidate in candidates:
            jwk = _object(candidate)
            kid = _text(jwk, "kid")
            if (
                jwk.get("kty") != "RSA" or kid in overlap or not _PRIVATE_MEMBERS.isdisjoint(jwk)
                or jwk.get("use", "sig") != "sig" or jwk.get("alg", _EXECUTION_ALGORITHM) != _EXECUTION_ALGORITHM
            ):
                raise ValueError
            n = int.from_bytes(_decode_base64url(jwk.get("n")))
            e = int.from_bytes(_decode_base64url(jwk.get("e")))
            if n.bit_length() < 2048 or n % 2 != 1 or not 3 <= e < n or e % 2 != 1:
                raise ValueError
            numbers = RSAPublicNumbers(e, n)
            if kid in own and own[kid].public_numbers() != numbers:
                raise ValueError
            overlap[kid] = numbers.public_key()
        return MappingProxyType({**overlap, **own}), None
    except (ValueError, RecursionError, OverflowError, InvalidOperation):
        return MappingProxyType({}), _INVALID


class _CallbackTrust:
    # The owning event loop coordinates publication and waiters. Workers only build candidates.
    def __init__(self, kid: str, public: RSAPublicKey, path: Path | None) -> None:
        self._own = MappingProxyType({kid: public})
        self._path = path
        self._keys: Mapping[str, RSAPublicKey] = self._own
        self._failure: str | None = None
        self._wake = anyio.Event() if path is not None else None
        self._completion: anyio.Event | None = None
        self._last_miss = float("-inf")
        self._closed = False

    async def initialize(self) -> None:
        if self._path is None:
            return
        path = None
        try:
            path = self._path.absolute()
        except OSError:
            pass
        if path is None:
            raise LocalFailure(_UNAVAILABLE)
        keys, failure = await anyio.to_thread.run_sync(_candidate, path, self._own)
        if failure is not None:
            raise LocalFailure(failure)
        self._path = path
        self._keys = keys

    async def key(self, kid: str) -> RSAPublicKey | None:
        key = self._keys.get(kid)
        if key is not None or self._closed or self._wake is None:
            return key
        completion = self._completion
        if completion is None:
            if monotonic() - self._last_miss < _INTERVAL:
                return None
            self._completion = completion = anyio.Event()
            self._wake.set()
        await completion.wait()
        return self._keys.get(kid)

    async def refresh(self) -> None:
        assert self._path is not None and self._wake is not None
        while not self._closed:
            with anyio.move_on_after(_INTERVAL):
                await self._wake.wait()
            self._wake = anyio.Event()
            # Only a miss can have queued a completion before the read begins.
            if self._completion is None:
                self._completion = anyio.Event()
            else:
                self._last_miss = monotonic()
            keys, failure = await anyio.to_thread.run_sync(_candidate, self._path, self._own)
            if self._closed:
                return
            self._keys = keys
            if failure != self._failure:
                self._failure = failure
                if failure is None:
                    _LOG.info("Callback trust restored.")
                else:
                    _LOG.warning("Callback trust withdrawn. %s", failure)
            self._completion.set()
            self._completion = None

    def close(self) -> None:
        self._closed = True
        self._keys = MappingProxyType({})
        if self._completion is not None:
            self._completion.set()
