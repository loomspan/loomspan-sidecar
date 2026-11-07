"""Client and models for the Loomspan Sidecar execution API."""

from ._client import SidecarClient
from ._credential import CredentialSnapshot
from ._errors import (
    AcceptanceUnknown,
    HttpFailure,
    LocalFailure,
    ProtocolFailure,
    SidecarError,
    WaitTerminated,
)
from ._models import (
    AcceptedExecution,
    ExecutionEvent,
    ExecutionFailure,
    ExecutionIssue,
    ExecutionSnapshot,
    ExecutionStatus,
    SkillDescriptor,
    WaitOptions,
)

__all__ = [
    "AcceptanceUnknown",
    "AcceptedExecution",
    "CredentialSnapshot",
    "ExecutionEvent",
    "ExecutionFailure",
    "ExecutionIssue",
    "ExecutionSnapshot",
    "ExecutionStatus",
    "HttpFailure",
    "LocalFailure",
    "ProtocolFailure",
    "SidecarClient",
    "SidecarError",
    "SkillDescriptor",
    "WaitOptions",
    "WaitTerminated",
]
