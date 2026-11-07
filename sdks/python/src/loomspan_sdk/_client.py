from __future__ import annotations

import ssl
from collections.abc import Callable
from time import monotonic, sleep
from types import TracebackType
from typing import Self
from uuid import UUID

from ._credential import CredentialSnapshot
from ._errors import LocalFailure, WaitTerminated
from ._http import (
    _CONNECT_TIMEOUT,
    _MAX_RESPONSE_SIZE,
    _RESPONSE_TIMEOUT,
    _TLS_CONTEXT_ERROR,
    _client_options,
    _verified_context,
)
from ._models import AcceptedExecution, ExecutionSnapshot, SkillDescriptor, WaitOptions
from ._protocol import (
    _check_credential,
    _execution_path,
    _skill_path,
    _wait_deadline,
    decode_response,
    encode_input,
    parse_acceptance,
    parse_catalog,
    parse_skill,
    parse_snapshot,
)
from ._sync_transport import _SyncTransport
from ._types import JsonValue


class SidecarClient:
    """Blocking, single-attempt client for the Sidecar execution API."""

    def __init__(
        self,
        base_url: str,
        *,
        connect_timeout: float = _CONNECT_TIMEOUT,
        response_timeout: float = _RESPONSE_TIMEOUT,
        max_response_size: int = _MAX_RESPONSE_SIZE,
        ssl_context: ssl.SSLContext | None = None,
    ) -> None:
        origin, connect_timeout, response_timeout = _client_options(
            base_url, connect_timeout, response_timeout, max_response_size, ssl_context
        )
        if origin.scheme == "https" and ssl_context is None:
            try:
                ssl_context = ssl.create_default_context()
            except OSError:
                pass
            if ssl_context is None or not _verified_context(ssl_context):
                raise LocalFailure(_TLS_CONTEXT_ERROR)
        self._transport = _SyncTransport(
            origin, connect_timeout, response_timeout, max_response_size,
            ssl_context if origin.scheme == "https" else None,
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
        return self._request(_skill_path(name), credential, parse_skill)

    def submit[T](
        self, name: str, input: dict[str, T], credential: CredentialSnapshot
    ) -> AcceptedExecution:
        path = _skill_path(name) + "/executions"
        return self._request(path, credential, parse_acceptance, body=encode_input(input))

    def get_execution(self, execution_id: UUID, credential: CredentialSnapshot) -> ExecutionSnapshot:
        return self._read(execution_id, credential)

    def wait_for_completion(
        self,
        execution_id: UUID,
        credential: CredentialSnapshot,
        options: WaitOptions = WaitOptions(),
    ) -> ExecutionSnapshot:
        deadline = _wait_deadline(execution_id, options)
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
        return self._request(
            _execution_path(execution_id),
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
        _check_credential(credential, execution_id, last_snapshot)
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
