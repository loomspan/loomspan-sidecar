from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from decimal import ROUND_FLOOR, Context, Decimal, InvalidOperation, localcontext
from pathlib import Path
from time import time_ns

import anyio
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding

from ._callback_trust import _CallbackTrust, _decode_base64url
from ._errors import InvalidCallbackCredential, LocalFailure
from ._issuance import (
    _EXECUTION_ALGORITHM,
    _EXECUTION_PURPOSE,
    _EXECUTION_TYPE,
    _MAX_INSTANT_NS,
    _MIN_INSTANT_NS,
    _NANOS_PER_SECOND,
    ExecutionTokenIssuer,
)
from ._protocol import _array, _java_blank, _object, _text, decode_json
from ._types import JsonValue

_SKEW_NS = 60 * _NANOS_PER_SECOND
_NANOSECOND = Decimal("1e-9")
_MIN_INSTANT_SECONDS = _MIN_INSTANT_NS // _NANOS_PER_SECOND
_AFTER_MAX_INSTANT_SECONDS = _MAX_INSTANT_NS // _NANOS_PER_SECOND + 1
_DATE_CONTEXT = Context(prec=26, rounding=ROUND_FLOOR, Emin=-26, Emax=26, traps=[InvalidOperation])


def _nonblank(value: JsonValue) -> str:
    if not isinstance(value, str) or _java_blank(value):
        raise ValueError
    return value


def _instant_ns(value: JsonValue) -> int:
    if isinstance(value, bool) or not isinstance(value, (int, Decimal)):
        raise ValueError
    number = Decimal(value)
    if not number.is_finite() or not _MIN_INSTANT_SECONDS <= number < _AFTER_MAX_INSTANT_SECONDS:
        raise ValueError
    with localcontext(_DATE_CONTEXT):
        return int(number.quantize(_NANOSECOND).scaleb(9))


def _validate_callback_options(audience: str | None, overlap_jwks: Path | None) -> None:
    if audience is not None and (not isinstance(audience, str) or _java_blank(audience)):
        raise LocalFailure("Callback audience must be a nonblank string")
    if overlap_jwks is not None and (
        not isinstance(overlap_jwks, Path) or _java_blank(overlap_jwks.name) or "\0" in str(overlap_jwks)
    ):
        raise LocalFailure("Callback overlap JWKS must be a file path")


@dataclass(frozen=True, slots=True)
class CallbackPrincipal:
    """Authenticated callback identity and its exact authority snapshot."""

    issuer: str
    subject: str
    authorities: tuple[str, ...]


class CallbackVerifier:
    """Verify execution callbacks within an explicitly owned async context."""

    def __init__(self, trust: _CallbackTrust, issuer: str, audience: str) -> None:
        self._trust = trust
        self._issuer = issuer
        self._audience = audience

    @classmethod
    @asynccontextmanager
    async def open(
        cls,
        issuer: ExecutionTokenIssuer,
        *,
        audience: str | None = None,
        overlap_jwks: Path | None = None,
    ) -> AsyncIterator["CallbackVerifier"]:
        """Initialize current trust, then own any refresh work until context exit."""
        if not isinstance(issuer, ExecutionTokenIssuer):
            raise LocalFailure("An initialized execution token issuer is required")
        _validate_callback_options(audience, overlap_jwks)

        issuer_name, kid, public = issuer._callback_verification_material()
        trust = _CallbackTrust(kid, public, overlap_jwks)
        await trust.initialize()
        verifier = cls(trust, issuer_name, issuer_name if audience is None else audience)
        if overlap_jwks is None:
            try:
                yield verifier
            finally:
                trust.close()
            return

        body_error: BaseException | None = None
        async with anyio.create_task_group() as tasks:
            tasks.start_soon(trust.refresh)
            try:
                yield verifier
            except BaseException as error:
                # Preserve the caller's error after the owned task has stopped.
                body_error = error
            finally:
                trust.close()
                tasks.cancel_scope.cancel()
        if body_error is not None:
            raise body_error

    async def verify(self, token: str) -> CallbackPrincipal:
        """Authenticate a compact execution JWT or raise InvalidCallbackCredential."""
        try:
            return await self._verify(token)
        except (ValueError, RecursionError, OverflowError, InvalidOperation, InvalidSignature):
            pass
        raise InvalidCallbackCredential()

    async def _verify(self, token: str) -> CallbackPrincipal:
        if not isinstance(token, str):
            raise ValueError
        protected, payload, encoded_signature = token.split(".", 3)
        headers = _object(decode_json(_decode_base64url(protected), exact_numbers=True))
        if (
            headers.get("alg") != _EXECUTION_ALGORITHM or headers.get("typ") != _EXECUTION_TYPE
            or headers.get("b64", True) is not True
            or ("crit" in headers and (headers["crit"] != ["b64"] or "b64" not in headers))
        ):
            raise ValueError
        kid = _text(headers, "kid")
        claims = _object(decode_json(_decode_base64url(payload), exact_numbers=True))
        signature = _decode_base64url(encoded_signature)
        if claims.get("iss") != self._issuer or claims.get("token_use") != _EXECUTION_PURPOSE:
            raise ValueError
        subject = _text(claims, "sub")
        authorities = tuple(_nonblank(role) for role in _array(claims.get("roles")))
        audience = claims.get("aud")
        audiences = [audience] if isinstance(audience, str) else _array(audience)
        if self._audience not in audiences or not all(isinstance(item, str) for item in audiences):
            raise ValueError
        if "jti" in claims and not isinstance(claims["jti"], str):
            raise ValueError
        expires = _instant_ns(claims.get("exp"))
        issued = _instant_ns(claims["iat"]) if "iat" in claims else None
        not_before = _instant_ns(claims["nbf"]) if "nbf" in claims else None
        key = await self._trust.key(kid)
        if key is None:
            raise ValueError
        key.verify(signature, f"{protected}.{payload}".encode("ascii"), padding.PKCS1v15(), hashes.SHA256())
        now = time_ns()
        if (
            expires < now - _SKEW_NS or (issued is not None and expires <= issued)
            or (not_before is not None and not_before > now + _SKEW_NS)
        ):
            raise ValueError
        return CallbackPrincipal(self._issuer, subject, authorities)
