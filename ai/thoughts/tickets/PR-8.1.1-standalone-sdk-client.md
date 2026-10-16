# PR #8.1.1 — Deliver the standalone Java SDK client

## Development constraints

**Destructively replace superseded development contracts, code and schemas; no
compatibility shims, legacy adapters, parallel legacy APIs or migration machinery
solely to preserve obsolete behavior.** Apply the [design
lens](../design-lens.md#simplicity-and-technical-debt).
Choose the simplest complete solution and minimize technical debt; keep tests,
documentation and process proportional. Document required development-data resets
and impact; this policy does not authorize deleting deployed data.

## Outcome

Java applications can discover skills, submit work, inspect retained executions and wait for results
through an independently built SDK with accurate failure and uncertainty outcomes. Establish only
the test infrastructure needed to exercise this client and support later integrations.

## Requirements

- Implement the independent `sdks/java/` Maven reactor and API reference on Java 21, SDK
  beta.1-SNAPSHOT and Boot 4.1.0 managed dependencies. Core has no Boot runtime, embedded Framework
  or Sidecar class dependency; root Sidecar build stays separate.
- Implement blocking/async discovery, submission, snapshots, waiting, DTOs and typed outcomes.
  Establish the immutable credential snapshot/capture interfaces consumed by PR-8.1.2; no insecure
  default or fake production strategy. Controlled test credentials can exercise transport; concrete
  signing and all four production credential strategies belong to PR-8.1.2.
- Preserve pre-send failure versus proven direct rejection versus acceptance unknown,
  protocol/polling errors, status denial/unavailability, wait timeout/interruption, execution
  failure and local client closure. These classify client/API behavior and server-reported
  outcomes, not skill result semantics or application parsing. Preserve IDs/snapshots/async observations through
  cancellation/close. Stopping a wait neither cancels nor resubmits work; status-404 remains
  ambiguous. Apply exact safe Location/ID rules, never follow a response-supplied URL or promise
  lost-ID recovery.
- Configure the reference Apache transport to disable redirects, cookies, automatic POST retries,
  authentication-rechallenge and reconnect replay. Prove no implicit replay; replace an unsuitable
  engine rather than weaken this contract. Only the specified finite GET retries are allowed within
  the monotonic total deadline.
- Preserve exact result text, configuration IDs, validation problems, primary failure and available
  history from Sidecar's public HTTP API. **Skill result content is opaque to the SDK.**
  Decode and validate API envelopes; preserve the result string without parsing, schema validation,
  Java-type conversion, sanitization or provider-specific interpretation. Discovered skill schemas
  are metadata for callers, not SDK validation rules. Applications own result interpretation;
  their parsing failures do not change the reported execution status or cause SDK requests.
  SDK v1 includes no result-conversion helpers, TypeSafe adapter or Framework Java dependency.
- Implement owned/borrowed pools/executors, response cleanup, concurrency, repeat close and
  in-flight close. Diagnostics default to safe timing/IDs, not credentials or payloads; listener
  failures cannot replace failures or rerun work.
- Supply runnable plain Java examples and focused HTTP tests. Credential strategies, callback
  security and full deployed scenarios remain their dependent units.
- In `C:/opendev/code/loomspan-sidecar-test-suite`, update `docs/project-agreement.md` before
  implementing the new lane. Follow that repository's instructions. Preserve embedded Java,
  Python/Sidecar, equipment-service behavior and synthetic/frozen TypeSafe fixtures. Name new work
  SDK v1, distinct from historical phase labels.
- Implement the runner, configuration, evidence recording and deterministic model/business fixtures
  needed to exercise the standalone client now. Use real JWT/JWKS, local Keycloak and TLS boundaries
  whenever exercising those capabilities; no paid model or hosted-service dependency. Missing
  required assets fail or report unavailable, never pass. PR-8.1.5 completes the deployment-specific
  fixtures and full composed/fault harness.
- Keep the existing embedded beta.9-SNAPSHOT lane separate from SDK v1 against Sidecar
  beta.4-SNAPSHOT and Framework beta.8. Record scenario ID/variant, actual SDK/Sidecar/Framework
  versions or revisions, configuration, command and result. Framework tests and beta.9 TypeSafe
  tests do not establish SDK beta.8 support.
- Provide prerequisite checks, reproducible startup/cleanup and failure reporting for this client
  foundation. Subsequent component tickets add their own tests; PR-8.1.5 completes real
  composed/fault evidence. Do not defer all testing to the final integration ticket.

## Acceptance criteria

- [ ] An independent core consumer works on the selected baseline; dependency/architecture checks
  prove no SDK dependency on Framework/Sidecar Java classes or Boot runtime. Sidecar's own
  Framework integration continues to enforce its public-only boundary.
- [ ] Blocking/async API and outcome variants pass, including lost/malformed acceptance,
  foreign/conflicting Location, cancel/close observations, bounded polling and 404 ambiguity.
- [ ] Attempt counters show no automatic submission replay. Plain text, empty, malformed-JSON-looking
  and business-schema-mismatched result strings remain unchanged in valid COMPLETED snapshots.
  Invalid API envelopes fail distinctly; application parsing failure preserves the returned
  snapshot/result/ID/history and causes zero new SDK requests.
- [ ] Fidelity, safe diagnostics and resource ownership/cleanup pass focused concurrent and
  error-path tests; no leaked resources remain.
- [ ] Consumer documentation explains supported APIs, uncertainty, retries and ownership without
  exactly-once, server cancellation or complete/durable history claims. It distinguishes typed API
  envelopes from opaque business results and assigns interpretation to the application.
- [ ] Updated agreement identifies the new lane and preserved baselines; relevant regression checks
  demonstrate unchanged existing examples and fixture behavior.
- [ ] Runner executes meaningful deterministic fixture checks and fails on failed assertions or
  missing required prerequisites without false passing/skipped gates.
- [ ] Results identify exact versions/revisions, configuration, command and variant; unimplemented
  SDK scenarios and the embedded baseline cannot appear as SDK proof.
- [ ] Local setup/cleanup instructions work without paid models or hosted services.

## Context

Created 2026-10-16 for roadmap GitHub PR #8. Depends on completed Phase 0 contracts; no Phase 1
prerequisite.

**Matrix ownership:** HTTP unit (including opaque-result preservation), Harness foundations, Phase 1 focused
API-01–05, OUTCOME-01–06 and BUILD-01/02. Initial harness work supports the client now; complete
deployment fixtures and composed/fault evidence belong to PR-8.1.5.

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
- **Rationale:** Public client APIs, transport outcomes, concurrency and initial evidence
  infrastructure.
- **Reassessment triggers:** Current evidence narrows the work to a bounded correction;
  settled Phase 0 design alone does not remove implementation assurance obligations.

## Pipeline notes

- Developer clarification, 2026-10-16: the SDK consumes only Sidecar's public HTTP contract;
  skill result content is opaque. Remove the TypeSafe adapter and all SDK result-conversion
  requirements. Preserve API-envelope validation, execution tracking, reported errors and
  diagnostics; application interpretation remains outside the SDK. This supersedes the prior
  TypeSafe feasibility gate without changing the pinned Framework baseline. No data reset is needed.
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
