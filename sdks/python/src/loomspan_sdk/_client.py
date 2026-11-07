from __future__ import annotations

import ssl
from collections.abc import Callable
from time import monotonic, sleep
from types import TracebackType
from typing import Self
from uuid import UUID

from ._credential import CredentialSnapshot
from ._errors import LocalFailure, WaitTerminated
from ._models import AcceptedExecution, ExecutionSnapshot, SkillDescriptor, WaitOptions, _duration
from ._protocol import (
    decode_response,
    encode_input,
    parse_acceptance,
    parse_catalog,
    parse_origin,
    parse_skill,
    parse_snapshot,
    skill_segment,
)
from ._sync_transport import _SyncTransport, _verified_context
from ._types import JsonValue


class SidecarClient:
    """Blocking, single-attempt client for the Sidecar execution API."""

    def __init__(
        self,
        base_url: str,
        *,
        connect_timeout: float = 2.0,
        response_timeout: float = 10.0,
        max_response_size: int = 8 * 1024 * 1024,
        ssl_context: ssl.SSLContext | None = None,
    ) -> None:
        origin = parse_origin(base_url)
        connect_timeout = _duration(connect_timeout)
        response_timeout = _duration(response_timeout)
        if type(max_response_size) is not int or not 0 < max_response_size <= 2**31 - 9:
            raise LocalFailure("Invalid response size limit")
        if ssl_context is not None and not isinstance(ssl_context, ssl.SSLContext):
            raise LocalFailure("Invalid SSL context")
        if origin.scheme == "https":
            if ssl_context is None:
                try:
                    ssl_context = ssl.create_default_context()
                except OSError:
                    pass
            if ssl_context is None or not _verified_context(ssl_context):
                raise LocalFailure(
                    "HTTPS requires a client SSL context with certificate, hostname, and TLS closure validation"
                )
        else:
            ssl_context = None
        self._transport = _SyncTransport(
            origin, connect_timeout, response_timeout, max_response_size, ssl_context
        )

    def __enter__(self) -> Self:
        self._transport.ensure_open()
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        self.close()

    def close(self) -> None:
        """Release local connections and prevent new calls."""
        self._transport.close()

    def list_skills(self, credential: CredentialSnapshot) -> tuple[SkillDescriptor, ...]:
        return self._request("/v1/skills", credential, parse_catalog)

    def get_skill(self, name: str, credential: CredentialSnapshot) -> SkillDescriptor:
        return self._request("/v1/skills/" + skill_segment(name), credential, parse_skill)

    def submit[T](
        self, name: str, input: dict[str, T], credential: CredentialSnapshot
    ) -> AcceptedExecution:
        path = "/v1/skills/" + skill_segment(name) + "/executions"
        return self._request(path, credential, parse_acceptance, body=encode_input(input))

    def get_execution(self, execution_id: UUID, credential: CredentialSnapshot) -> ExecutionSnapshot:
        return self._read(execution_id, credential)

    def wait_for_completion(
        self,
        execution_id: UUID,
        credential: CredentialSnapshot,
        options: WaitOptions = WaitOptions(),
    ) -> ExecutionSnapshot:
        if not isinstance(execution_id, UUID):
            raise LocalFailure("Invalid execution ID")
        if not isinstance(options, WaitOptions):
            raise LocalFailure("Invalid wait options", execution_id)
        deadline = monotonic() + options.timeout
        last = None
        while True:
            last = self._read(execution_id, credential, last, deadline)
            if last.status.terminal:
                return last
            remaining = deadline - monotonic()
            if remaining <= 0:
                raise WaitTerminated("Wait deadline expired", execution_id, last)
            sleep(min(options.poll_interval, remaining))

    def _read(
        self,
        execution_id: UUID,
        credential: CredentialSnapshot,
        last: ExecutionSnapshot | None = None,
        deadline: float | None = None,
    ) -> ExecutionSnapshot:
        if not isinstance(execution_id, UUID):
            raise LocalFailure("Invalid execution ID")
        return self._request(
            f"/v1/executions/{execution_id}",
            credential,
            lambda value: parse_snapshot(value, execution_id),
            execution_id=execution_id,
            last_snapshot=last,
            deadline=deadline,
        )

    def _request[T](
        self,
        path: str,
        credential: CredentialSnapshot,
        parser: Callable[[JsonValue], T],
        *,
        body: bytes | None = None,
        execution_id: UUID | None = None,
        last_snapshot: ExecutionSnapshot | None = None,
        deadline: float | None = None,
    ) -> T:
        if type(credential) is not CredentialSnapshot:
            raise LocalFailure("Missing or invalid execution credential", execution_id, last_snapshot)
        submission = body is not None
        response = self._transport.exchange(
            "POST" if submission else "GET", path, body, credential, deadline
        )
        return decode_response(
            response,
            parser,
            credential,
            submission=submission,
            execution_id=execution_id,
            last_snapshot=last_snapshot,
            wait_deadline=deadline,
        )
