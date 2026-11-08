import json
import logging
from collections.abc import Collection, Mapping
from dataclasses import dataclass
from decimal import Decimal
from time import time_ns
from typing import final
from uuid import uuid4

import simplejson  # type: ignore[import-untyped]
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicKey

from ._credential import CredentialSnapshot
from ._errors import LocalFailure
from ._protocol import _java_blank
from ._signing import ColocatedSigning, ProvidedSigning, _base64url, _initialize_signing, _sign_rs256

_EXECUTION_ALGORITHM = "RS256"
_EXECUTION_TYPE = "loomspan-execution+jwt"
_EXECUTION_PURPOSE = "loomspan_execution"
_NANOS_PER_SECOND = 1_000_000_000
_MIN_INSTANT_NS = -31_557_014_167_219_200 * _NANOS_PER_SECOND
_MAX_INSTANT_NS = 31_556_889_864_403_199 * _NANOS_PER_SECOND + 999_999_999
_LOG = logging.getLogger(__name__)


def _require_name(value: str, field: str) -> None:
    if type(value) is not str or _java_blank(value):
        raise LocalFailure(f"{field} must be a nonblank string")


def _names(values: Collection[str], field: str) -> tuple[str, ...]:
    if not isinstance(values, Collection) or isinstance(values, (str, bytes, bytearray, Mapping)):
        raise LocalFailure(f"{field} must be a collection of strings")
    names = tuple(values)
    for name in names:
        _require_name(name, field)
    return names


@final
@dataclass(frozen=True, slots=True, init=False)
class AuthenticatedIdentity:
    """Snapshot a subject and exact authorities from application authentication."""

    subject: str
    authorities: tuple[str, ...]

    def __init__(self, subject: str, authorities: Collection[str] = ()) -> None:
        _require_name(subject, "Subject")
        object.__setattr__(self, "subject", subject)
        object.__setattr__(self, "authorities", _names(authorities, "Authorities"))


@final
@dataclass(frozen=True, slots=True, init=False)
class IssuanceSettings:
    """Execution credential profile with an exact nanosecond lifetime."""

    issuer: str
    lifetime_ns: int
    additional_audiences: tuple[str, ...]
    roles: tuple[str, ...]

    def __init__(
        self,
        issuer: str = "loomspan-app",
        lifetime_ns: int = 900 * _NANOS_PER_SECOND,
        additional_audiences: Collection[str] = (),
        roles: Collection[str] = (),
    ) -> None:
        _require_name(issuer, "Issuer")
        if type(lifetime_ns) is not int or lifetime_ns <= 0:
            raise LocalFailure("Credential lifetime must be a positive integer number of nanoseconds")
        object.__setattr__(self, "issuer", issuer)
        object.__setattr__(self, "lifetime_ns", lifetime_ns)
        object.__setattr__(self, "additional_audiences", _names(additional_audiences, "Additional audiences"))
        object.__setattr__(self, "roles", _names(roles, "Roles"))


def _expiry(now: int, lifetime: int) -> int:
    expiry = now + lifetime
    if now < _MIN_INSTANT_NS or expiry > _MAX_INSTANT_NS:
        raise LocalFailure("Credential timestamps exceed the representable range")
    return expiry


def _numeric_date(timestamp: int) -> Decimal:
    return Decimal(f"{timestamp}e-9")


class ExecutionTokenIssuer:
    """Initialize one startup signing key and issue credentials for trusted identities."""

    __slots__ = ("__settings", "__audiences", "__roles", "__key", "__kid")

    def __init__(
        self, signing: ColocatedSigning | ProvidedSigning, settings: IssuanceSettings = IssuanceSettings()
    ) -> None:
        if type(signing) not in (ColocatedSigning, ProvidedSigning):
            raise LocalFailure("An explicit colocated or provided signing mode is required")
        if type(settings) is not IssuanceSettings:
            raise LocalFailure("Issuance settings are required")
        _expiry(time_ns(), settings.lifetime_ns)
        if settings.lifetime_ns > 3600 * _NANOS_PER_SECOND:
            _LOG.warning("Execution credential lifetime exceeds one hour. Roles remain snapshots until expiry.")
        self.__settings = settings
        self.__audiences = tuple(dict.fromkeys(("loomspan-sidecar", settings.issuer, *settings.additional_audiences)))
        self.__roles = frozenset(settings.roles)
        self.__key, self.__kid = _initialize_signing(signing)

    def __getstate__(self) -> None:
        raise TypeError("Execution token issuers cannot be serialized")

    def _callback_verification_material(self) -> tuple[str, str, RSAPublicKey]:
        return self.__settings.issuer, self.__kid, self.__key.public_key()

    def issue(self, identity: AuthenticatedIdentity) -> CredentialSnapshot:
        """Issue an opaque credential for one application-authenticated identity."""
        if type(identity) is not AuthenticatedIdentity:
            raise LocalFailure("An authenticated identity is required")
        now = time_ns()
        expires = _expiry(now, self.__settings.lifetime_ns)
        headers = {"alg": _EXECUTION_ALGORITHM, "typ": _EXECUTION_TYPE, "kid": self.__kid}
        claims = {
            "iss": self.__settings.issuer,
            "sub": identity.subject,
            "aud": self.__audiences,
            "iat": _numeric_date(now),
            "exp": _numeric_date(expires),
            "jti": str(uuid4()),
            "token_use": _EXECUTION_PURPOSE,
            "roles": list(dict.fromkeys(name for name in identity.authorities if name in self.__roles)),
        }
        try:
            header = _base64url(json.dumps(headers, separators=(",", ":")).encode("ascii"))
            payload = _base64url(simplejson.dumps(claims, separators=(",", ":"), use_decimal=True).encode("ascii"))
            content = f"{header}.{payload}"
            signature = _base64url(_sign_rs256(self.__key, content.encode("ascii")))
            return CredentialSnapshot(f"{content}.{signature}")
        except Exception:
            # Keep backend exceptions out of the public error and exception chain.
            pass
        raise LocalFailure("Execution credential issuance failed")
