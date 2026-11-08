"""Application-owned Sidecar resources and prefix-wide FastAPI callback authentication."""

from __future__ import annotations

import re
import ssl
from collections.abc import AsyncIterator
from contextlib import AsyncExitStack, asynccontextmanager
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote, unquote_to_bytes

import anyio
from fastapi import FastAPI, HTTPException, Request
from starlette.responses import Response
from starlette.types import ASGIApp, Receive, Scope, Send

from ._async_client import AsyncSidecarClient
from ._callback import CallbackPrincipal, CallbackVerifier, _validate_callback_options
from ._errors import InvalidCallbackCredential, LocalFailure
from ._http import _CONNECT_TIMEOUT, _MAX_RESPONSE_SIZE, _RESPONSE_TIMEOUT, _client_options
from ._issuance import ExecutionTokenIssuer, IssuanceSettings
from ._protocol import _control, _java_blank
from ._signing import ColocatedSigning, ProvidedSigning

__all__ = ["CallbackMiddleware", "CallbackPrincipal", "Loomspan"]

_DEFAULT_PREFIX = "/loomspan/callbacks"
_RESOURCE = "_loomspan_sdk_resources"
_PRINCIPAL = "loomspan_sdk.callback_principal"
_BEARER = re.compile(rb"Bearer ([A-Za-z0-9\-._~+/]+=*)", re.IGNORECASE)
_CHALLENGE = {"WWW-Authenticate": "Bearer"}
_BAD_ESCAPE = re.compile(rb"%(?![a-fA-F0-9]{2})")
_ENCODED_SYNTAX = re.compile(rb"%(?:23|25|2e|2f|3b|3f|5c)", re.IGNORECASE)
_NESTED_PERCENT = re.compile(r"%(?:25)+(?=[a-fA-F0-9]{2})")
_INVALID_PATH_TEXT = re.compile(r"[\x00-\x1f\x7f-\x9f\u2028\u2029\ud800-\udfff]")
_PATH_SYNTAX = re.compile(r"//|(?:^|/)\.{1,2}(?:/|$)|[;\\%?#]")


def _validate_prefix(prefix: str) -> str:
    if isinstance(prefix, str) and prefix.startswith("/") and not any(
        _java_blank(ch) or _control(ch) or "\ud800" <= ch <= "\udfff" or ch in "?#*{}\\%;" for ch in prefix
    ):
        prefix = prefix.removesuffix("/")
        if all(segment not in ("", ".", "..") for segment in prefix[1:].split("/")):
            return prefix
    raise LocalFailure("Callback prefix must be a literal absolute non-root path")


async def _start_issuer(
    signing: ColocatedSigning | ProvidedSigning, issuance: IssuanceSettings
) -> ExecutionTokenIssuer:
    async def initialize() -> ExecutionTokenIssuer | Exception:
        try:
            return await anyio.to_thread.run_sync(ExecutionTokenIssuer, signing, issuance)
        except Exception as failure:
            return failure

    # A cancelled lifespan must keep its writer reservation until the worker
    # finishes. The task group waits for the thread even on native cancellation.
    async with anyio.create_task_group() as tasks:
        starting = tasks.start_soon(initialize)
    result = starting.return_value
    if isinstance(result, Exception):
        raise result
    return result


@dataclass(frozen=True, slots=True)
class _Resources:
    integration: Loomspan
    client: AsyncSidecarClient
    issuer: ExecutionTokenIssuer
    verifier: CallbackVerifier


@dataclass(slots=True)
class _PrincipalBinding:
    integration: Loomspan
    principal: CallbackPrincipal | None


class Loomspan:
    """Validate settings now and initialize SDK resources in the owning app's lifespan."""

    def __init__(
        self,
        base_url: str,
        signing: ColocatedSigning | ProvidedSigning,
        issuance: IssuanceSettings = IssuanceSettings(),
        *,
        callback_prefix: str = _DEFAULT_PREFIX,
        callback_audience: str | None = None,
        overlap_jwks: Path | None = None,
        connect_timeout: float = _CONNECT_TIMEOUT,
        response_timeout: float = _RESPONSE_TIMEOUT,
        max_response_size: int = _MAX_RESPONSE_SIZE,
        ssl_context: ssl.SSLContext | None = None,
    ) -> None:
        if type(signing) not in (ColocatedSigning, ProvidedSigning):
            raise LocalFailure("An explicit colocated or provided signing mode is required")
        if type(issuance) is not IssuanceSettings:
            raise LocalFailure("Issuance settings are required")
        _, connect_timeout, response_timeout = _client_options(
            base_url, connect_timeout, response_timeout, max_response_size, ssl_context
        )
        _validate_callback_options(callback_audience, overlap_jwks)
        self._prefix = _validate_prefix(callback_prefix)
        self._signing = signing
        self._issuance = issuance
        self._audience = callback_audience
        self._overlap_jwks = overlap_jwks
        self._base_url = base_url
        self._connect_timeout = connect_timeout
        self._response_timeout = response_timeout
        self._max_response_size = max_response_size
        self._ssl_context = ssl_context

    @property
    def callback_prefix(self) -> str:
        """The normalized literal path relative to the app owning the guard."""
        return self._prefix

    @asynccontextmanager
    async def lifespan(self, app: FastAPI) -> AsyncIterator[None]:
        """Initialize signing, trust and the client for one application lifespan."""
        if hasattr(app.state, _RESOURCE):
            raise LocalFailure("Loomspan lifespan is already active for this app")
        middleware = app.user_middleware
        outermost: object = middleware[0].cls if middleware else None
        if (
            outermost is not CallbackMiddleware
            or middleware[0].kwargs.get("loomspan") is not self
        ):
            raise LocalFailure("Install the Loomspan callback middleware last on its owning app")

        setattr(app.state, _RESOURCE, self)
        try:
            issuer = await _start_issuer(self._signing, self._issuance)
            async with CallbackVerifier.open(
                issuer, audience=self._audience, overlap_jwks=self._overlap_jwks
            ) as verifier:
                async with AsyncExitStack() as client_lifespan:
                    client = await client_lifespan.enter_async_context(AsyncSidecarClient(
                        self._base_url,
                        connect_timeout=self._connect_timeout,
                        response_timeout=self._response_timeout,
                        max_response_size=self._max_response_size,
                        ssl_context=self._ssl_context,
                    ))
                    setattr(app.state, _RESOURCE, _Resources(self, client, issuer, verifier))
                    try:
                        yield
                    finally:
                        setattr(app.state, _RESOURCE, self)
                        with anyio.CancelScope(shield=True):
                            await client_lifespan.aclose()
        finally:
            delattr(app.state, _RESOURCE)

    def _resources(self, app: FastAPI) -> _Resources | None:
        resources = getattr(app.state, _RESOURCE, None)
        if isinstance(resources, _Resources) and resources.integration is self:
            return resources
        return None

    async def client(self, request: Request) -> AsyncSidecarClient:
        """Inject the reusable client of the current owning app."""
        resources = self._resources(request.app)
        if resources is None:
            raise HTTPException(status_code=503, detail="Loomspan resources unavailable")
        return resources.client

    async def issuer(self, request: Request) -> ExecutionTokenIssuer:
        """Inject the execution credential issuer of the current owning app."""
        resources = self._resources(request.app)
        if resources is None:
            raise HTTPException(status_code=503, detail="Loomspan resources unavailable")
        return resources.issuer

    async def principal(self, request: Request) -> CallbackPrincipal:
        """Inject the verified callback principal for this request."""
        binding = request.scope.get(_PRINCIPAL)
        if isinstance(binding, _PrincipalBinding) and binding.integration is self and binding.principal is not None:
            return binding.principal
        raise HTTPException(status_code=401, detail="Callback authentication required", headers=_CHALLENGE)


def _relative_path(path: str, root: str) -> str:
    # Use the same segment boundary as Starlette's router and Mount.
    if root and (path == root or path.startswith(root + "/")):
        return path[len(root):]
    return path


def _under_prefix(path: str, prefix: str) -> bool:
    return path == prefix or path.startswith(prefix + "/")


def _path_aliases(path: str) -> tuple[str, str]:
    """Find aliases for rejection only. Never use these paths to dispatch."""
    while "%" in path:
        decoded = unquote(_NESTED_PERCENT.sub("%", path))
        if decoded == path:
            break
        path = decoded
    path = _INVALID_PATH_TEXT.sub("", path).replace("\ufffd", "").replace("\\", "/")
    path = path.split("?", 1)[0].split("#", 1)[0]
    segments = [segment.partition(";")[0] for segment in path.split("/")]
    segments = [segment for segment in segments if segment]
    resolved: list[str] = []
    for segment in segments:
        if segment == "..":
            if resolved:
                resolved.pop()
        elif segment != ".":
            resolved.append(segment)
    return "/" + "/".join(segments), "/" + "/".join(resolved)


def _callback_path(scope: Scope, prefix: str) -> tuple[bool, bool]:
    path: str = scope["path"]
    root: str = scope.get("root_path", "")
    raw: bytes | None = scope.get("raw_path")
    relative = _relative_path(path, root)
    ambiguous = bool(
        _PATH_SYNTAX.search(path) or _PATH_SYNTAX.search(root)
        or _INVALID_PATH_TEXT.search(path) or _INVALID_PATH_TEXT.search(root)
    )
    paths = [path]
    if raw is None:
        ambiguous = ambiguous or "\ufffd" in path
    else:
        try:
            raw_decoded = unquote_to_bytes(raw).decode("utf-8")
        except UnicodeDecodeError:
            raw_decoded = unquote(raw)
            ambiguous = True
        if _relative_path(raw_decoded, root) != relative:
            ambiguous = True
            paths.append(raw_decoded)
        ambiguous = ambiguous or bool(_BAD_ESCAPE.search(raw) or _ENCODED_SYNTAX.search(raw))

    if not ambiguous:
        return _under_prefix(relative, prefix), False

    roots = (root, _path_aliases(root)[1].rstrip("/"))
    for candidate in paths:
        for alias in (candidate, *_path_aliases(candidate)):
            if any(_under_prefix(_relative_path(alias, base), prefix) for base in roots):
                return True, True
    return False, False


def _bearer(headers: list[tuple[bytes, bytes]], query: bytes) -> str | None:
    for parameter in query.split(b"&"):
        name = parameter.partition(b"=")[0]
        if _BAD_ESCAPE.search(name) or unquote_to_bytes(name.replace(b"+", b" ")) == b"access_token":
            return None
    values = [value for name, value in headers if name.lower() == b"authorization"]
    match = _BEARER.fullmatch(values[0]) if len(values) == 1 else None
    return match[1].decode("ascii") if match is not None else None


class CallbackMiddleware:
    """Authenticate the configured callback area before dispatch or browser middleware."""

    def __init__(self, app: ASGIApp, *, loomspan: Loomspan) -> None:
        self._app = app
        self._loomspan = loomspan

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] not in ("http", "websocket"):
            await self._app(scope, receive, send)
            return
        protected, malformed = _callback_path(scope, self._loomspan.callback_prefix)
        if not protected:
            await self._app(scope, receive, send)
            return
        if scope["type"] == "websocket":
            await send({"type": "websocket.close", "code": 1008})
            return
        if malformed:
            await Response(status_code=400)(scope, receive, send)
            return

        owner = scope.get("app")
        resources = self._loomspan._resources(owner) if isinstance(owner, FastAPI) else None
        # ASGI permits one-shot header iterables. Preserve every pair and its
        # order so authentication can inspect them without consuming downstream headers.
        headers = [(name, value) for name, value in scope["headers"]]
        token = _bearer(headers, scope.get("query_string", b""))
        principal = None
        if resources is not None and token is not None:
            try:
                principal = await resources.verifier.verify(token)
            except InvalidCallbackCredential:
                pass
        if principal is None:
            await Response(status_code=401, headers=_CHALLENGE)(scope, receive, send)
            return
        if scope["method"] not in ("GET", "POST"):
            await Response(status_code=403)(scope, receive, send)
            return

        binding = _PrincipalBinding(self._loomspan, principal)
        downstream = {**scope, "headers": headers, _PRINCIPAL: binding}
        try:
            await self._app(downstream, receive, send)
        finally:
            # A downstream middleware can copy the scope. Clear the identity
            # in every such copy through the shared request-local binding.
            binding.principal = None
