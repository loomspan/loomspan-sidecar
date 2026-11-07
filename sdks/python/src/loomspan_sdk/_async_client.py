from __future__ import annotations

import ssl
from collections.abc import Callable
from time import monotonic
from types import TracebackType
from typing import Self
from uuid import UUID

from ._async_transport import _AsyncTransport
from ._credential import CredentialSnapshot
from ._errors import LocalFailure, WaitTerminated
from ._http import _CONNECT_TIMEOUT, _MAX_RESPONSE_SIZE, _RESPONSE_TIMEOUT, _client_options
from ._models import AcceptedExecution, ExecutionSnapshot, SkillDescriptor, WaitOptions
from ._protocol import (
    _CLIENT_CLOSED,
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
from ._types import JsonValue


class AsyncSidecarClient:
    """Async, single-attempt client shared by tasks within one async runtime."""

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
        self._transport = _AsyncTransport(
            origin, connect_timeout, response_timeout, max_response_size, ssl_context
        )

    async def __aenter__(self) -> Self:
        self._transport.ensure_open()
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        await self.aclose()

    async def aclose(self) -> None:
        """Release local connections and prevent new calls."""
        await self._transport.aclose()

    async def list_skills(self, credential: CredentialSnapshot) -> tuple[SkillDescriptor, ...]:
        return await self._request("/v1/skills", credential, parse_catalog)

    async def get_skill(self, name: str, credential: CredentialSnapshot) -> SkillDescriptor:
        return await self._request(_skill_path(name), credential, parse_skill)

    async def submit[T](
        self, name: str, input: dict[str, T], credential: CredentialSnapshot
    ) -> AcceptedExecution:
        path = _skill_path(name) + "/executions"
        return await self._request(path, credential, parse_acceptance, body=encode_input(input))

    async def get_execution(
        self, execution_id: UUID, credential: CredentialSnapshot
    ) -> ExecutionSnapshot:
        return await self._read(execution_id, credential)

    async def wait_for_completion(
        self,
        execution_id: UUID,
        credential: CredentialSnapshot,
        options: WaitOptions = WaitOptions(),
    ) -> ExecutionSnapshot:
        deadline = _wait_deadline(execution_id, options)
        last = None
        while True:
            last = await self._read(execution_id, credential, last, deadline)
            if last.status.terminal:
                return last
            remaining = deadline - monotonic()
            if remaining <= 0:
                raise WaitTerminated("Wait deadline expired", execution_id, last)
            if await self._transport.poll_delay(min(options.poll_interval, remaining)):
                raise LocalFailure(_CLIENT_CLOSED, execution_id, last)

    async def _read(
        self,
        execution_id: UUID,
        credential: CredentialSnapshot,
        last: ExecutionSnapshot | None = None,
        deadline: float | None = None,
    ) -> ExecutionSnapshot:
        return await self._request(
            _execution_path(execution_id),
            credential,
            lambda value: parse_snapshot(value, execution_id),
            execution_id=execution_id,
            last_snapshot=last,
            deadline=deadline,
        )

    async def _request[T](
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
        response = await self._transport.exchange(
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
