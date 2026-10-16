# PR #8.1.4 — Integrate authenticated Spring callbacks with application security

## Development constraints

**Destructively replace superseded development contracts, code and schemas; no
compatibility shims, legacy adapters, parallel legacy APIs or migration machinery
solely to preserve obsolete behavior.** Apply the [design
lens](../design-lens.md#simplicity-and-technical-debt).
Choose the simplest complete solution and minimize technical debt; keep tests,
documentation and process proportional. Document required development-data resets
and impact; this policy does not authorize deleting deployed data.

## Outcome

Spring applications receive authorized Sidecar callbacks without replacing customer login or
accidentally authenticating callbacks using browser-session authority.

## Requirements

- Implement Boot 4.1.0 servlet integration for both explicitly selected arrangements: SDK-owned
  dedicated chain and SDK configurer in an application-owned chain. Preserve exact matchers/order,
  authorization hooks and overlap/fail-closed diagnostics. Do not imply WebFlux or unsupported
  asynchronous security-context propagation.
- Authenticate callbacks only with bearer credentials. Ignore cookies without loading, merging or
  mutating sessions, persisting callback identity or redirecting failures to login. Scope CSRF
  exceptions to designated callbacks; preserve neighboring browser routes and the independently
  configured optional JWKS endpoint.
- Independently validate receiver trust, signature/algorithm/time/claims, issuer, purpose/audience
  and sender constraints. Preserve verified issuer/subject, explicit roles and optional trusted
  resolver semantics. Valid TLS or a browser session cannot replace these checks; invalid tokens
  never reach the native-account resolver.
- Apply application URL, method and record authorization in both arrangements. Enforce callback peer
  restrictions for the selected transport. Forwarded bearer tokens remain reusable by approved
  recipients; they are not proof Sidecar authored a request body.
- Guarantee context cleanup/isolation on success, rejection, exceptions, sequential/ concurrent
  requests and supported servlet async paths. Unsupported composition fails clearly rather than
  silently loading sessions or weakening authentication.
- Supply runnable Spring examples for both arrangements on the existing application listener with
  unchanged customer login, minimal configuration and precise boundaries.

## Acceptance criteria

- [ ] Both arrangements pass every assigned CALLBACK variant, including bearer Alice/ session Bob,
  invalid/missing bearer with valid cookies, cookie-only and bearer-only.
- [ ] URL/method/record checks and default/native principals enforce pair/role/resolver semantics
  consistently; invalid-purpose/bound tokens fail independently of TLS.
- [ ] Browser login/session/CSRF remain intact; callback session access/mutation, persistence and
  login redirects are absent; JWKS and overlap diagnostics are correct.
- [ ] Sequential/concurrent/error/supported-async tests demonstrate cleanup and no authority
  leakage; unsupported configurations fail with meaningful diagnostics.
- [ ] Examples run with independent receiver trust and no SDK-created server; docs explain actual
  security composition, transport and async limits.

## Context

Created 2026-10-16 for roadmap GitHub PR #8. Depends on
[PR-8.1.1](PR-8.1.1-standalone-sdk-client.md), [PR-8.1.2](PR-8.1.2-credentials-and-signing.md),
[PR-8.1.3](PR-8.1.3-sidecar-trust-and-management.md).

**Matrix ownership:** Callback unit: all assigned Phase 1 identity, purpose, passthrough and context
variants. Every callback source scenario applies to both supported chain arrangements.

The [Phase 1 scope](../phases/2026-10-15-sdk-v1-phase-1-isolated-integration.md),
[API reference](../phases/2026-10-15-sdk-v1-api-reference.md),
[identity reference](../phases/2026-10-15-sdk-v1-identity-callback-reference.md),
[trust reference](../phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md),
[deployment recipes](../phases/2026-10-15-sdk-v1-deployment-recipes.md) and
[acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md) are normative.
Preserve all linked source assertions and variants, including both callback arrangements;
these summaries do not narrow them. Shared matrix ownership is cumulative: component
proof belongs in its component ticket, real composed/fault proof in PR-8.1.5.
Planned commands and Framework-only tests are not SDK/Sidecar evidence.

Focused SDK/Sidecar tests and documentation belong here; complex Compose/fault evidence
belongs in `C:/opendev/code/loomspan-sidecar-test-suite`, following its instructions and
updated agreement. Preserve existing embedded Java/Python examples and equipment-service
behavior. Record actual SDK/Sidecar/Framework versions or revisions, configuration,
command and scenario variant. Other languages, new Python functionality, management-
authoring SDK, code generation, streaming and server cancellation are outside SDK v1.

Use Java 21/Boot 4.1.0 and independent SDK `1.0.0-beta.1-SNAPSHOT` (planned first release
`1.0.0-beta.1`). Sidecar stays `1.0.0-beta.4-SNAPSHOT`, next release/tag
`1.0.0-beta.4`/`v1.0.0-beta.4`; Framework starter remains
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.8`. Consult local Framework checkout
`C:/opendev/code/loomspan-framework` at `v1.0.0-beta.8`, not stale installed docs or the
suite's distinct beta.9 lane. Use only closed `ai.loomspan.api` and authorized
`RestSkillHandler`; ArchUnit forbids internal/autoconfigure dependencies in application
and tests. No new SPI, bean replacement, internal reflection or Sidecar Java
`@SkillMethod` skills. Missing public capabilities require Framework planning, not a
bypass. Use documented `loomspan.*`, Sidecar-owned `loomspan-sidecar.*` and standard
Boot namespaces.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Authentication, application authorization, session isolation and async context.
- **Reassessment triggers:** Current evidence narrows the work to a bounded correction;
  settled Phase 0 design alone does not remove implementation assurance obligations.

## Pipeline notes

- Deliver this unit's implementation, tests and documentation; do not repeat Phase 0.
  Ticket creation does not start execution. Each ticket is one pipeline/review unit;
  internal work areas do not require separate tickets or process artifacts.
- Developer commits per ticket and syncs after all phases. No agent commit/push/release,
  hosted CI or external compatibility run is authorized. Required deterministic local
  security/provider testing remains in scope.
- Record intentional breakage, feasible checks, outstanding evidence and a named
  restoring unit. Do not add compatibility scaffolding or count unavailable tests as
  passes. Initial protected behavior cannot be deferred wholesale to Phase 3; all
  breakage closes before final readiness. Runtime limitations cannot justify weaker
  callback/session isolation or trust guarantees.
- Framework changes use the developer-installed aligned snapshot and affected retests,
  without normal Framework source rebuilds or snapshot repositories. Publish Framework
  before final Sidecar dependency pin/build/test/commit; hosted release checks follow
  publication/sync. Never overwrite a release.
