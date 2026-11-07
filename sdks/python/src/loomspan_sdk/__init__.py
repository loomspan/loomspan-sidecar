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
from ._issuance import AuthenticatedIdentity, ExecutionTokenIssuer, IssuanceSettings
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
from ._signing import ColocatedSigning, ProvidedSigning

__all__ = [
    "AcceptanceUnknown",
    "AcceptedExecution",
    "AsyncSidecarClient",
    "AuthenticatedIdentity",
    "ColocatedSigning",
    "CredentialSnapshot",
    "ExecutionEvent",
    "ExecutionFailure",
    "ExecutionIssue",
    "ExecutionSnapshot",
    "ExecutionStatus",
    "ExecutionTokenIssuer",
    "HttpFailure",
    "IssuanceSettings",
    "LocalFailure",
    "ProtocolFailure",
    "ProvidedSigning",
    "SidecarClient",
    "SidecarError",
    "SkillDescriptor",
    "WaitOptions",
    "WaitTerminated",
]
