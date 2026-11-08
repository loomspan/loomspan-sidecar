"""Single-worker local example. Run with uvicorn --factory fastapi_app:create_app."""

import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from hmac import compare_digest
from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request
from pydantic import BaseModel

from loomspan_sdk import (
    AsyncSidecarClient, AuthenticatedIdentity, ColocatedSigning,
    ExecutionTokenIssuer, IssuanceSettings, ProvidedSigning,
)
from loomspan_sdk.fastapi import CallbackMiddleware, CallbackPrincipal, Loomspan

ISSUER = "orders-app"
SUBJECT = "local-operator"
EDITOR = "ORDER_EDITOR"
RECORD_OWNERS = {
    "order-1": (ISSUER, SUBJECT),
    "order-2": (ISSUER, "another-operator"),
    "order-3": ("another-issuer", SUBJECT),
}


class Submission(BaseModel):
    text: str


class OrderNote(BaseModel):
    note: str


def create_app() -> FastAPI:
    mode = os.environ["LOOMSPAN_SIGNING_MODE"]
    signing: ColocatedSigning | ProvidedSigning
    if mode == "colocated":
        signing = ColocatedSigning(Path(os.environ["LOOMSPAN_KEY_DIRECTORY"]))
    elif mode == "provided":
        signing = ProvidedSigning(
            Path(os.environ["LOOMSPAN_SIGNING_KEY"]), Path(os.environ["LOOMSPAN_JWKS_EXPORT"]),
        )
    else:
        raise ValueError("Choose the colocated or provided signing mode")

    demo_password = os.environ["LOOMSPAN_DEMO_PASSWORD"].encode("utf-8")
    if not demo_password:
        raise ValueError("A local demo password is required")
    skill = os.environ.get("LOOMSPAN_SKILL", "echo")
    loomspan = Loomspan(
        os.environ["LOOMSPAN_SIDECAR_URL"], signing, IssuanceSettings(issuer=ISSUER, roles=[EDITOR]),
    )

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        async with loomspan.lifespan(app):
            # Start other application resources here when they need the SDK.
            yield

    app = FastAPI(lifespan=lifespan)
    app.add_middleware(CallbackMiddleware, loomspan=loomspan)

    async def current_user(request: Request) -> AuthenticatedIdentity:
        # Local demonstration only. Production supplies its existing verified user.
        supplied = request.headers.get("x-demo-password", "").encode("utf-8")
        if not compare_digest(supplied, demo_password):
            raise HTTPException(status_code=401, detail="Application authentication required")
        return AuthenticatedIdentity(SUBJECT, [EDITOR])

    @app.post("/submit", status_code=202)
    async def submit(
        body: Submission,
        user: Annotated[AuthenticatedIdentity, Depends(current_user)],
        client: Annotated[AsyncSidecarClient, Depends(loomspan.client)],
        issuer: Annotated[ExecutionTokenIssuer, Depends(loomspan.issuer)],
    ) -> dict[str, str]:
        credential = issuer.issue(user)
        accepted = await client.submit(skill, {"text": body.text}, credential)
        return {"id": str(accepted.id)}

    callbacks = APIRouter(prefix=loomspan.callback_prefix)

    @callbacks.post("/orders/{order_id}")
    async def order_note(
        order_id: str,
        body: OrderNote,
        principal: Annotated[CallbackPrincipal, Depends(loomspan.principal)],
    ) -> dict[str, str]:
        owner = (principal.issuer, principal.subject)
        if EDITOR not in principal.authorities or RECORD_OWNERS.get(order_id) != owner:
            raise HTTPException(status_code=403, detail="Forbidden")
        return {"order": order_id, "note": body.note}

    app.include_router(callbacks)
    return app
