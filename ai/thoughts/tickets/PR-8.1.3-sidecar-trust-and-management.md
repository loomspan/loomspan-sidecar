# PR #8.1.3 — Deliver administered Sidecar trust and protected management

## Development constraints

**Destructively replace superseded development contracts, code and schemas; no
compatibility shims, legacy adapters, parallel legacy APIs or migration machinery
solely to preserve obsolete behavior.** Apply the [design
lens](../design-lens.md#simplicity-and-technical-debt).
Choose the simplest complete solution and minimize technical debt; keep tests,
documentation and process proportional. Document required development-data resets
and impact; this policy does not authorize deleting deployed data.

## Outcome

An administrator can configure uploaded public keys or an approved JWKS URL through the console/API
and immediately use that trust for SDK execution admission. Automation retains authorized management
operations without gaining protected trust privileges.

## Requirements

- Implement the single-administrator shared browser/API contract and named opaque Read/Edit/Publish
  tokens. Scopes are cumulative, intersect live account authority, expire and remain revocable. Hash
  tokens at rest, disclose only at creation and protect client storage; map every operation to the
  specified permissions.
- Require interactive administrator login for account administration, token creation, issuer/key
  trust changes, audiences, authority mappings and withdrawal/restoration. Deny all token presets
  through direct routes and indirect import/publication/rollback or stale-draft paths. Reject
  unauthorized changes atomically, without dropping fields.
- Preserve browser CSRF, reject ambiguous session-plus-token management authentication, separate
  execution/management credentials and enforce file-mode mutation restrictions through every client
  and publication path.
- Preserve account-owned drafts, leases/handoff, revisions, validation and atomic publication.
  Recheck credentials, live permissions, ownership/revisions and protected trust immediately before
  commit after waits/locks/external calls. Authorized Publish can approve passthrough destinations
  without an additional browser approval.
- Provide console/API trust editing against the exact reference schema with meaningful errors, fully
  integrated with this ticket's actual trust source validation, storage and admission. Test
  authorization and source publication together; no placeholder or deferred integration may report
  successful trust publication.
- Document token operation mappings and lifecycle. Complete real trust integration in this ticket;
  initial authorization cannot be deferred to Phase 3 stress testing.
- Implement the reference single-issuer policy: exact issuer, profile, audience, authorized parties
  and explicit roles. Accept only specified public JWKS/JWK/PEM-SPKI uploads, not private keys or
  client certificate chains. Never derive trust or fetch destinations from token-provided URLs.
  Multi-issuer group extension belongs to Phase 2.
- Implement bounded JWKS retrieval, approved destination/TLS/redirect/DNS controls, finite
  refresh/cache age and fail-closed outages. Persist revisions, snapshots and withdrawal protection;
  restart cannot reset cache age or restore withdrawn trust.
- Integrate real validated trust sources with this ticket's console/API atomic publication. Enforce
  interactive-only protected mutations and file-mode authority everywhere; never activate a
  partially valid upload/candidate or bypass indirect mutation checks.
- Validate signature/algorithm/time/required claims/purpose/audience, reject ID tokens, DPoP and
  malformed/unknown `cnf` independently of workload TLS. Preserve issuer/subject ownership and
  agreed permissions at admission and queue dispatch. Framework remains execution authority; no
  duplication of its skill authorization/lifetime machinery.
- Forward captured JWT bytes unchanged only to approved callback recipients, with
  redirect/credential-leakage protection and exact expiry/withdrawal enforcement. Sidecar never
  mints, refreshes or exchanges. Withdrawal cannot cancel Framework- admitted work or roll back its
  effects; later SDK renewal does not update it.
- Preserve owner visibility, indistinguishable unknown/expired/nonowner/restart-loss status-404,
  joint result/history retention and original failure. Test public-only shutdown boundaries; missing
  APIs require Framework planning, not internal access.
- Document persistent state, source diagnostics, initialization/recovery and necessary development
  resets. Initial durability, cache expiry and authorization are required now; Phase 3 adds full
  lifecycle stress assurance.

## Acceptance criteria

- [ ] Browser and scoped-token clients share authoring rules; authorized publication including
  changed passthrough recipients succeeds.
- [ ] Every token preset fails protected direct/indirect mutations atomically, including stale
  drafts and authority/revocation changes during waits.
- [ ] Token lifecycle/storage/disclosure, CSRF, mixed credentials, file-mode restrictions and
  management/execution separation pass focused tests.
- [ ] Lease/revision/validation tests reject stale ownership/configuration, preserve drafts on
  failed publication and recheck authorization at commit.
- [ ] Console/API trust editing, source validation/storage/admission and guidance work together; no
  pending integration or placeholder success remains within this feature.
- [ ] Console/API uploaded and URL sources work; malformed/private material and unsafe
  retrieval/destinations reject without partial activation.
- [ ] Cache-age, persistence/restart, outage and withdrawal tests cannot revive trust or extend
  stale-key acceptance beyond the specified bounds.
- [ ] Valid SDK credentials pass while all assigned signature/claim/purpose/binding/ permission
  negatives fail even with valid TLS.
- [ ] Queue and callback passthrough preserve captured identity/bytes and enforce expiry/recipient
  rules; owner/status/result/history variants pass.
- [ ] Integrated management tests deny protected direct/indirect token mutations and prove
  console-to-trust-to-admission behavior; architecture checks enforce public APIs.
- [ ] Focused tests and operator guidance cover all assigned Phase 1 variants, clearly separating
  later group/lifecycle evidence from current support.

## Context

Created 2026-10-16 for roadmap GitHub PR #8. Depends on
[PR-8.1.1](PR-8.1.1-standalone-sdk-client.md), [PR-8.1.2](PR-8.1.2-credentials-and-signing.md).

**Matrix ownership:** Management and Admission units: all assigned Phase 1
management/protected-mutation/trust/source/state/queue/ownership variants and shared API-01/02,
OUTCOME-01/06. Console, trust storage and admission integration must finish within this ticket.

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
- **Rationale:** Management authorization, persistent trust, publication and execution admission.
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
