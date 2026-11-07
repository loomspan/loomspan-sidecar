from copy import deepcopy
from dataclasses import dataclass, field
from enum import StrEnum
from math import isfinite
from threading import TIMEOUT_MAX
from uuid import UUID

from ._errors import LocalFailure
from ._types import JsonObject


def _duration(seconds: float) -> float:
    if (
        type(seconds) not in (int, float)
        or not 0 < seconds <= min(TIMEOUT_MAX, (2**62 - 1) / 1e9)
        or not isfinite(seconds)
    ):
        raise LocalFailure("Invalid finite positive duration")
    return float(seconds)


@dataclass(frozen=True, slots=True)
class WaitOptions:
    timeout: float = 60.0
    poll_interval: float = 0.25

    def __post_init__(self) -> None:
        object.__setattr__(self, "timeout", _duration(self.timeout))
        object.__setattr__(self, "poll_interval", _duration(self.poll_interval))


class ExecutionStatus(StrEnum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"

    @property
    def terminal(self) -> bool:
        return self in (ExecutionStatus.COMPLETED, ExecutionStatus.FAILED)


@dataclass(frozen=True, slots=True)
class SkillDescriptor:
    name: str
    description: str | None
    kind: str
    input_schema: str
    output_schema: str | None


@dataclass(frozen=True, slots=True)
class AcceptedExecution:
    id: UUID


@dataclass(frozen=True, slots=True)
class ExecutionIssue:
    path: str | None
    code: str | None
    message: str | None


@dataclass(frozen=True, slots=True)
class ExecutionFailure:
    kind: str
    message: str | None
    issues: tuple[ExecutionIssue, ...] = ()

    def __post_init__(self) -> None:
        object.__setattr__(self, "issues", tuple(self.issues))


@dataclass(frozen=True, slots=True, init=False)
class ExecutionEvent:
    timestamp: str
    level: str
    type: str
    _details: JsonObject = field(repr=False, hash=False)
    frame_id: str | None
    route: str | None

    def __init__(
        self,
        timestamp: str,
        level: str,
        type: str,
        details: JsonObject,
        frame_id: str | None = None,
        route: str | None = None,
    ) -> None:
        object.__setattr__(self, "timestamp", timestamp)
        object.__setattr__(self, "level", level)
        object.__setattr__(self, "type", type)
        object.__setattr__(self, "_details", deepcopy(details))
        object.__setattr__(self, "frame_id", frame_id)
        object.__setattr__(self, "route", route)

    @property
    def details(self) -> JsonObject:
        return deepcopy(self._details)


@dataclass(frozen=True, slots=True)
class ExecutionSnapshot:
    id: UUID
    skill_name: str
    status: ExecutionStatus
    created_at: str
    completed_at: str | None
    result: str | None
    failure: ExecutionFailure | None
    events: tuple[ExecutionEvent, ...] | None
    configuration_snapshot_id: UUID | None

    def __post_init__(self) -> None:
        if self.events is not None:
            object.__setattr__(self, "events", tuple(self.events))
