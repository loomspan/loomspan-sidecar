# PR #8.1.5 — Prove isolated SDK deployments and failure semantics

## Development constraints

**Destructively replace superseded development contracts, code and schemas; no
compatibility shims, legacy adapters, parallel legacy APIs or migration machinery
solely to preserve obsolete behavior.** Apply the [design
lens](../design-lens.md#simplicity-and-technical-debt).
Choose the simplest complete solution and minimize technical debt; keep tests,
documentation and process proportional. Document required development-data resets
and impact; this policy does not authorize deleting deployed data.

## Outcome

Operators can start isolated SDK deployments with automatic provisioning, use every required Phase 1
transport composition and rely on demonstrated failure semantics without silent business-operation
replay. Complete all Phase 1 integration evidence.

## Requirements

- Implement the pinned recipes in Sidecar and the external suite with automatic guarded Smallstep
  provisioning, separate SDK JWT initialization, persistent deployment identity and explicit
  recovery. Customers start the recipe without manual key/certificate assembly.
- Implement protected direct HTTP, direct HTTPS, client-Caddy mTLS and SDK-native mTLS invocation.
  Enforce all defined hops: exact peer membership, SAN/EKU/chain validation, Sidecar-only callback
  peer and the specified UID/cgroup local backend/proxy ownership protections. Shared loopback alone
  does not authorize bypass or identity borrowing.
- Configure callback ingress/transport independently. Exercise mixed protected-HTTP/ HTTPS callbacks
  and outbound-only operation across all four credential strategies. No-callback operation needs no
  callback listener/material; native invocation omits client Caddy, proxied invocation retains it;
  optional JWKS remains independent.
- Execute every isolated composition with real SDK, Sidecar, application, local provider and
  transport boundaries. Prove exact opaque-result/API-envelope preservation, ownership, independent token-purpose/ binding
  validation, invalid peer/server identity, downgrade/bypass denial and credential redirect/leakage
  protection. Valid TLS never rescues invalid user credentials.
- Complete composed Phase 1 source variants allocated to Harness/Bootstrap, including both callback
  arrangements, uploads/URLs and application/separate JWKS hosting. Shared component evidence needs
  the matrix's rationale and cannot replace a required composed case. PR-8.1.1 through PR-8.1.4
  retain their own focused tests and documentation.
- Preserve existing suite lanes and document prerequisites/start/use/stop and per-hop guarantees.
  Local delivery tests do not imply AWS/Azure/Google Cloud or hosted identity compatibility; those
  procedures remain unverified without separately authorized runs.
- Complete shared response-loss/effects variants within this ticket alongside the deployments; no
  separate fault-ticket handoff remains. Do not reduce the matrix or defer initial protected
  boundaries to Phase 3 lifecycle assurance.
- Inject real submission/callback response loss, resets/timeouts, malformed/oversized acceptance,
  proxy errors, redirects and authentication/reconnect challenges through required isolated
  native/proxy paths. Count each hop's sends, admissions and business effects; prove no automatic
  submission or callback replay.
- Prove direct rejection versus proxied/unknown acceptance, reliable-ID/Location rules, bounded
  polling/resume, status-404 ambiguity and cancellation/close races. Preserve
  observations/IDs/context after failed waits without rerunning or cancelling server work.
- Implement the application-owned stable operation-ID ledger fixture with atomic same-ID/same-digest
  receipts, conflicting-digest rejection, concurrency, before-commit crash versus after-commit
  response loss and pending/query/reconcile behavior. A new operation ID permits an intentional new
  effect. Do not add Sidecar exactly-once, bearer single-use or transport retry guarantees.
- Demonstrate committed callback effects despite HTTP-200 terminal execution failure, original
  failure/history preservation and application-side parsing failure without new SDK requests.
  The SDK validates API envelopes and treats skill result strings as opaque; it neither
  interprets provider output nor validates results against business schemas. Test safe
  diagnostics/infrastructure credential canaries separately from business content that must remain
  unchanged; no content sanitization assertions.
- Complete all remaining Phase 1 matrix variants, including shared Harness/component assertions and
  isolated deployment fault cases. Use the matrix's Phase 1 ticket ownership registry; repair
  missing component implementation/evidence in its owning repository rather than treating
  documentation or Framework tests as proof.
- Keep Phase 2 group, Phase 3 lifecycle stress/material and Phase 4 release work assigned to their
  existing phases. Multi-phase rows require their Phase 1 initial enforcement now. Record any
  breakage, available checks and concrete later restoration owner; never declare affected acceptance
  passed. All deferred breakage closes before release.
- Provide evidence and guidance for uncertainty, duplicate protection, safe resume and actual
  compatibility. Do not execute hosted/paid provider checks or releases.

## Acceptance criteria

- [ ] All isolated primary/mixed/outbound configurations run with the four strategies, both mTLS
  paths and required direct paths using automatic bootstrap.
- [ ] Real tests reject wrong peer/chain/hostname/SAN/EKU, local bypass, proxy identity borrowing
  and downgrade; redirect sinks receive no forwarded credentials.
- [ ] Assigned composed authentication/authorization, API/results, both callback chains, upload/URL
  and independent JWKS/no-listener variants pass.
- [ ] Existing embedded/Python examples retain behavior; each variant has actual versions/revisions,
  configuration, command and result evidence.
- [ ] Setup instructions reproduce the deployments and all Phase 1 shared fault variants pass within
  this ticket; later lifecycle/external claims remain explicitly outstanding.
- [ ] Per-hop counters/effects prove one automatic send per action across required Phase 1 failure
  variants, without promising single-use tokens or exactly-once operations.
- [ ] Outcome/wait/opaque-result tests preserve correct context and never resubmit or cancel server
  work on behalf of a failed local wait or application-side result parsing. Result content never
  overrides the server-reported execution status; no SDK result converter or provider adapter.
- [ ] Atomic ledger tests distinguish duplicates, conflicts, crash windows, reconciliation and
  intentional new operations with correct receipts and committed effects.
- [ ] Diagnostics preserve business text but exclude infrastructure credentials, keys and security
  context, including proxy/application logs and model inputs.
- [ ] Every Phase 1 matrix row/variant has attributable passing evidence or an explicit blocker;
  Phase 1 is reported complete only when its required evidence passes.
- [ ] Existing suite regressions pass; later-phase/external claims stay outstanding and developer
  guidance agrees with demonstrated behavior.

## Context

Created 2026-10-16 for roadmap GitHub PR #8. Depends on
[PR-8.1.1](PR-8.1.1-standalone-sdk-client.md), [PR-8.1.2](PR-8.1.2-credentials-and-signing.md),
[PR-8.1.3](PR-8.1.3-sidecar-trust-and-management.md),
[PR-8.1.4](PR-8.1.4-spring-callback-security.md).

**Matrix ownership:** Bootstrap and Fault units, remaining Harness integration: all PRIMARY-I-*,
MIXED-I-* and OUTBOUND-I-* rows and full variants; Phase 1 TOPOLOGY-01, real API-01–05,
OUTCOME-01–06 and EFFECT-01/02 evidence; final reconciliation of every Phase 1 row.

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
- **Rationale:** Cross-service provisioning, real security boundaries, transactional fixtures and
  fault evidence.
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
