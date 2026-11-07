from __future__ import annotations

import ipaddress
import json
import re
from calendar import monthrange
from collections.abc import Callable
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from math import isfinite
from time import monotonic
from typing import cast
from urllib.parse import quote, urlsplit
from uuid import UUID

from ._credential import CredentialSnapshot
from ._errors import AcceptanceUnknown, HttpFailure, LocalFailure, ProtocolFailure, WaitTerminated
from ._models import (
    AcceptedExecution,
    ExecutionEvent,
    ExecutionFailure,
    ExecutionIssue,
    ExecutionSnapshot,
    ExecutionStatus,
    SkillDescriptor,
)
from ._types import JsonObject, JsonValue

_AUTHORITY = re.compile(
    r"(?P<host>\[[0-9A-Fa-f:.]+(?:%[A-Za-z0-9._~-]+)?\]|[A-Za-z0-9.-]+)"
    r"(?::(?P<port>[0-9]+))?"
)
_LABEL = re.compile(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
_PATH = re.compile(r"(?:[A-Za-z0-9:/@!$&'()*+,;=._~-]|%[0-9A-Fa-f]{2}|[^\x00-\x9f])*")
_UUID = re.compile(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
_INSTANT = re.compile(
    r"([0-9]{4}|-[0-9]{4,10}|\+[0-9]{5,10})-([0-9]{2})-([0-9]{2})[Tt]"
    r"([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\.([0-9]{0,9}))?"
    r"([Zz]|[+-][0-9]{2}:[0-9]{2}(?::[0-9]{2})?)"
)


@dataclass(frozen=True, slots=True)
class _Origin:
    scheme: str
    hostname: str
    port: int
    authority: str
    base_path: str


@dataclass(frozen=True, slots=True)
class _Response:
    status: int | None = None
    body: bytes | None = field(default=None, repr=False)
    closed_before_dispatch: bool = False


def _control(ch: str) -> bool:
    return ord(ch) < 32 or 127 <= ord(ch) <= 159


def _java_blank(value: str) -> bool:
    return not value or all(
        ch.isspace() and ch not in "\u0085\u00a0\u2007\u202f" for ch in value
    )


def parse_origin(url: str) -> _Origin:
    if (
        type(url) is not str
        or any(ch in url for ch in "?#\\")
        or any(ch.isspace() or _control(ch) for ch in url)
    ):
        raise LocalFailure("Invalid Sidecar base URL")
    try:
        parsed = urlsplit(url)
        match = _AUTHORITY.fullmatch(parsed.netloc)
        if (
            parsed.scheme not in ("http", "https")
            or match is None
            or not _PATH.fullmatch(parsed.path)
        ):
            raise ValueError
        host = match["host"]
        if host.startswith("["):
            # Python passes the unbracketed IPv6 literal to the resolver and TLS.
            host = str(ipaddress.IPv6Address(host[1:-1]))
            address, encoded_zone, zone = host.partition("%25")
            if encoded_zone and zone:
                host = f"{address}%{zone}"
        else:
            host = host.lower()
            domain = host.removesuffix(".")
            if len(domain) > 253 or not all(_LABEL.fullmatch(label) for label in domain.split(".")):
                raise ValueError
            if "." in domain and all(ch in "0123456789." for ch in domain):
                ipaddress.IPv4Address(domain)
        port_text = match["port"]
        port = int(port_text) if port_text is not None else (443 if parsed.scheme == "https" else 80)
        if not 1 <= port <= 65535:
            raise ValueError
        path = quote(parsed.path.rstrip("/"), safe="/%:@!$&'()*+,;=-._~")
        return _Origin(parsed.scheme, host, port, parsed.netloc, path)
    except ValueError:
        pass
    raise LocalFailure("Invalid Sidecar base URL")


def skill_segment(name: str) -> str:
    if (
        type(name) is not str
        or _java_blank(name)
        or name in (".", "..")
        or any(_control(ch) or ch in "/\\%" for ch in name)
    ):
        raise LocalFailure("Invalid skill name")
    try:
        return quote(name, safe="")
    except UnicodeError:
        pass
    raise LocalFailure("Invalid skill name")


def _validate_json(value: object) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if not isinstance(key, str):
                raise ValueError
            _validate_json(child)
    elif isinstance(value, list):
        for child in value:
            _validate_json(child)
    elif value is not None and not isinstance(value, (bool, int, float, str)):
        raise ValueError


def encode_input(value: object) -> bytes:
    if not isinstance(value, dict):
        raise LocalFailure("Expected a JSON object input")
    try:
        _validate_json(value)
        return json.dumps(value, allow_nan=False, ensure_ascii=True, separators=(",", ":")).encode("ascii")
    except (TypeError, ValueError, RecursionError, OverflowError):
        pass
    raise LocalFailure("Invalid JSON object input")


def _object_pairs(pairs: list[tuple[str, JsonValue]]) -> JsonObject:
    result: JsonObject = {}
    for key, value in pairs:
        if key in result:
            raise ValueError
        result[key] = value
    return result


def _reject_constant(_: str) -> JsonValue:
    raise ValueError


def _parse_float(value: str) -> float | Decimal:
    result = float(value)
    if isfinite(result):
        return result
    # A finite JSON number can overflow a float without being malformed.
    try:
        number = Decimal(value)
    except InvalidOperation:
        raise ValueError from None
    if not number.is_finite():
        raise ValueError
    return number


def decode_json(body: bytes) -> JsonValue:
    return cast(
        JsonValue,
        json.loads(
            body.decode("utf-8"),
            object_pairs_hook=_object_pairs,
            parse_constant=_reject_constant,
            parse_float=_parse_float,
        ),
    )


def _object(value: JsonValue) -> JsonObject:
    if not isinstance(value, dict):
        raise ValueError
    return value


def _array(value: JsonValue) -> list[JsonValue]:
    if not isinstance(value, list):
        raise ValueError
    return value


def _optional_text(obj: JsonObject, key: str) -> str | None:
    value = obj.get(key)
    if value is not None and not isinstance(value, str):
        raise ValueError
    return value


def _text(obj: JsonObject, key: str) -> str:
    value = _optional_text(obj, key)
    if value is None or _java_blank(value):
        raise ValueError
    return value


def _uuid(value: str) -> UUID:
    if not _UUID.fullmatch(value):
        raise ValueError
    return UUID(value)


def _instant(value: str) -> str:
    match = _INSTANT.fullmatch(value)
    if match is None:
        raise ValueError
    year, month, day, hour, minute, second = map(int, match.groups()[:6])
    fraction, zone = match.groups()[6:]
    if (
        not -1_000_000_000 <= year <= 1_000_000_000
        or (year == 0 and match[1].startswith("-"))
        or not 1 <= month <= 12
        or not 1 <= day <= monthrange(year, month)[1]
        or minute >= 60
    ):
        raise ValueError
    if hour == 24 and minute == second == 0 and not (fraction or "").strip("0"):
        pass
    elif not (hour < 24 and (second < 60 or (hour == 23 and minute == 59 and second == 60))):
        raise ValueError
    offset = 0
    if zone not in ("Z", "z"):
        hours, minutes, *seconds = map(int, zone[1:].split(":"))
        offset_second = seconds[0] if seconds else 0
        offset = hours * 3600 + minutes * 60 + offset_second
        if minutes >= 60 or offset_second >= 60 or offset > 18 * 3600:
            raise ValueError
        if zone.startswith("-"):
            offset = -offset
    utc_second = hour * 3600 + minute * 60 + min(second, 59) - offset
    if (year, month, day) == (-1_000_000_000, 1, 1) and utc_second < 0:
        raise ValueError
    if (year, month, day) == (1_000_000_000, 12, 31) and utc_second >= 86400:
        raise ValueError
    return value


def parse_skill(value: JsonValue) -> SkillDescriptor:
    obj = _object(value)
    return SkillDescriptor(
        _text(obj, "name"),
        _optional_text(obj, "description"),
        _text(obj, "kind"),
        _text(obj, "inputSchema"),
        _optional_text(obj, "outputSchema"),
    )


def parse_catalog(value: JsonValue) -> tuple[SkillDescriptor, ...]:
    return tuple(parse_skill(item) for item in _array(value))


def parse_acceptance(value: JsonValue) -> AcceptedExecution:
    return AcceptedExecution(_uuid(_text(_object(value), "id")))


def _issue(value: JsonValue) -> ExecutionIssue:
    obj = _object(value)
    return ExecutionIssue(
        _optional_text(obj, "path"), _optional_text(obj, "code"), _optional_text(obj, "message")
    )


def _failure(value: JsonValue) -> ExecutionFailure:
    obj = _object(value)
    issues = obj.get("issues")
    return ExecutionFailure(
        _text(obj, "kind"),
        _optional_text(obj, "message"),
        () if issues is None else tuple(_issue(issue) for issue in _array(issues)),
    )


def _event(value: JsonValue) -> ExecutionEvent:
    obj = _object(value)
    return ExecutionEvent(
        _instant(_text(obj, "timestamp")),
        _text(obj, "level"),
        _text(obj, "type"),
        _object(obj.get("details")),
        _optional_text(obj, "frameId"),
        _optional_text(obj, "route"),
    )


def parse_snapshot(value: JsonValue, expected: UUID) -> ExecutionSnapshot:
    obj = _object(value)
    execution_id = _uuid(_text(obj, "id"))
    if execution_id != expected:
        raise ValueError
    status = ExecutionStatus(_text(obj, "status"))
    completed = _optional_text(obj, "completedAt")
    failure = obj.get("failure")
    if (status.terminal and completed is None) or (status is ExecutionStatus.FAILED and failure is None):
        raise ValueError
    events = obj.get("events")
    configuration = _optional_text(obj, "configurationSnapshotId")
    return ExecutionSnapshot(
        execution_id,
        _text(obj, "skillName"),
        status,
        _instant(_text(obj, "createdAt")),
        None if completed is None else _instant(completed),
        _optional_text(obj, "result"),
        None if failure is None else _failure(failure),
        None if events is None else tuple(_event(event) for event in _array(events)),
        None if configuration is None else _uuid(configuration),
    )


def _redact(value: JsonValue, credential: CredentialSnapshot) -> JsonValue:
    if isinstance(value, str):
        return credential._redact(value)
    if isinstance(value, list):
        return [_redact(item, credential) for item in value]
    if isinstance(value, dict):
        return _object_pairs([
            (credential._redact(key), _redact(item, credential)) for key, item in value.items()
        ])
    return value


def decode_response[T](
    response: _Response,
    parser: Callable[[JsonValue], T],
    credential: CredentialSnapshot,
    *,
    submission: bool = False,
    execution_id: UUID | None = None,
    last_snapshot: ExecutionSnapshot | None = None,
    wait_deadline: float | None = None,
) -> T:
    if response.closed_before_dispatch:
        raise LocalFailure("Client is closed", execution_id, last_snapshot)
    status = response.status
    if status is not None and not 200 <= status < 300:
        error = HttpFailure(status, execution_id=execution_id, last_snapshot=last_snapshot)
        if response.body is not None:
            try:
                problem = _object(_redact(_object(decode_json(response.body)), credential))
                error = HttpFailure(status, problem, execution_id, last_snapshot)
            except (ValueError, RecursionError, OverflowError):
                pass
        raise error
    if response.body is not None and status == (202 if submission else 200):
        try:
            result = parser(decode_json(response.body))
        except (ValueError, RecursionError, OverflowError):
            pass
        else:
            if wait_deadline is None or monotonic() < wait_deadline:
                return result
    if wait_deadline is not None and monotonic() >= wait_deadline:
        raise WaitTerminated("Wait deadline expired", execution_id, last_snapshot)
    if submission:
        raise AcceptanceUnknown("Could not establish submission acceptance")
    raise ProtocolFailure("Sidecar read did not return a usable response", execution_id, last_snapshot)
