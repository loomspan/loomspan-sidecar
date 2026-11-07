"""Client and models for the Loomspan Sidecar execution API."""

from ._async_client import AsyncSidecarClient
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
    "AsyncSidecarClient",
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
