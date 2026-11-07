from __future__ import annotations

from copy import deepcopy
from typing import TYPE_CHECKING
from uuid import UUID

from ._types import JsonObject

if TYPE_CHECKING:
    from ._models import ExecutionSnapshot


class SidecarError(Exception):
    """Base SDK error, with status or wait context when available."""

    def __init__(
        self,
        message: str,
        execution_id: UUID | None = None,
        last_snapshot: ExecutionSnapshot | None = None,
    ) -> None:
        super().__init__(message)
        self.execution_id = execution_id
        self.last_snapshot = last_snapshot


class LocalFailure(SidecarError):
    """The SDK rejected a call before dispatch."""


class ProtocolFailure(SidecarError):
    """A discovery or status read failed to return a usable response."""


class AcceptanceUnknown(SidecarError):
    """A submission was attempted, but its acceptance could not be established."""


class WaitTerminated(SidecarError):
    """The local wait reached its deadline."""


class HttpFailure(SidecarError):
    """The server or proxy returned a final non-success HTTP status."""

    def __init__(
        self,
        status_code: int,
        problem: JsonObject | None = None,
        execution_id: UUID | None = None,
        last_snapshot: ExecutionSnapshot | None = None,
    ) -> None:
        super().__init__(f"Sidecar returned HTTP {status_code}", execution_id, last_snapshot)
        self.status_code = status_code
        self._problem = deepcopy(problem)

    @property
    def problem(self) -> JsonObject | None:
        return deepcopy(self._problem)
