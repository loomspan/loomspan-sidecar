# Phase 1: implementation and integration

**Development policy: destructively replace superseded contracts, code and schemas;
no compatibility shims, legacy adapters or parallel obsolete APIs.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
No data reset is currently identified; document any necessary development reset in
its owning implementation plan. This is not permission to delete deployed data.

Follow the [roadmap](2026-10-11-java-spring-sdk-roadmap.md) and its canonical references.
All implementation and evidence remain planned. These are delivery groups, not
created tickets or a new feature catalog.

## Delivery order and ownership

1. Establish the independent SDK core/starter build and a small external-suite
   harness foundation early. Select the actual Sidecar revision and pinned Framework
   beta.8; preserve the suite's existing beta.9 embedded/Python lane and fixtures.
2. Implement the blocking HTTP client and simple outcomes/waiting. Test the real
   API path, opaque results and no replay using attempt/effect counters.
3. Implement shared issuance and colocated/provided signing. Add current-file
   Sidecar trust, exact authorities, execution ownership and queue revalidation.
   Replace old execution key-source/prefix configuration directly.
4. Add dedicated callback JWT authentication and reference application
   method/business authorization. Test independent receiver trust, captured-token
   expiry and the configured callback chain boundary.
5. Complete Local HTTP, Standard direct HTTPS and Hardened Caddy-managed mTLS
   recipes in the external suite. Include the trusted group, JWT rotation/recovery
   and focused outbound-only cases assigned by the acceptance matrix.

Each group includes its own documentation and tests when implemented.
Tickets may reuse or extend composed scenarios shared with other tickets; identify
the relevant coverage instead of duplicating it. Keep detailed rule variants in
component tests. Add a composed scenario only for behavior not already demonstrated.
Do not postpone feature acceptance until Phase 2. Harness work should remain a
small foundation plus scenarios added by the feature that needs them, not a generic
deployment platform.

When the user requests tickets later, use repository commands and dated filenames.
All ticket artifacts live under Sidecar's ai/thoughts, including external-suite work.
A feature may touch both repositories or use a small paired integration ticket.
State explicitly in relevant trust/configuration/integration tickets that JWT keys
come from the configured JWKS file in both application configuration modes.

## Scope and completion

Use only the [bounded acceptance matrix](2026-10-15-sdk-v1-acceptance-matrix.md).
No product allowlist of network recipes, TLS lifecycle controller/testing,
management redesign, async client, retry subsystem or business ledger.
Operators own TLS configuration and lifecycle.

Retain supported public Framework boundaries in application and test code; if
missing public capability blocks the agreed contract, resolve it in the owning
repository instead of importing internals or expanding scope silently.

Complete when the agreed features and their focused/composed tests pass, with
clear evidence tied to actual builds, documented limitations and usable examples.
No paid calls, commit, push or release is authorized by this phase document.
