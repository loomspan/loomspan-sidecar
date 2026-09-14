# Authenticated Execution API Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md`
- Research: `ai/thoughts/research/2026-09-13-authenticated-execution-api.md`
- Outcome: establish fresh Sidecar evidence that the existing SC2/SC3 implementation satisfies the authenticated asynchronous execution contract, correcting only ticket-scoped defects and closing material test or documentation gaps found during verification.

## Current State

Commit `2597238` already added the feature and commit `fba8351` retained its production code, tests, fixtures, configuration, and README while deleting its earlier pipeline artifacts. The current flow is therefore implemented rather than greenfield:

- `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java` and `SidecarJwtProperties.java` select issuer discovery, explicit JWKS, or RSA public-key verification; apply common issuer, audience, timestamp, and required-claim validation; map the configured role claim/prefix; and keep `/v1/**` stateless and JWT-only.
- `src/main/java/ai/loomspan/sidecar/api/SkillCatalogController.java`, `ExecutionController.java`, `LimitedJsonObjectReader.java`, `ApiExceptionHandler.java`, and `NoStoreResponseFilter.java` expose the four routes, bound raw input before parsing, validate through the public `SkillTemplate` Map overload, return accepted/polling representations, and map request failures to problem documents.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java` owns the only record map, executor work queue, queued count/byte accounting, expiry sweep, task security-context handoff, terminal snapshot publication, and owning-context close gate. `ExecutionOwner` retains issuer and subject separately.
- The current test suite covers the central HTTP flow, decoder modes, ownership, queued/nested token propagation, basic count/byte/retained bounds, expiry, diagnostic modes, Console coexistence, management exposure, and the supported framework boundary. Step 1 identified weaker direct evidence for HTTP-level body limits and record absence, custom roles through both framework validation and invocation, exact preservation of large results/events, task/reference cleanup, the close-to-dispatch boundary, and shutdown `503` mapping.
- Existing Surefire XML reports show 33 passing tests but predate this pipeline. They are historical evidence only; Step 4 must run fresh verification against the developer-installed `1.0.0-beta.4-SNAPSHOT`.

The checked ticket marks its acceptance criteria complete and the README describes SC2/SC3 as delivered. The local SC2/SC3 phase files still contain older pending language. Those status documents are not implementation evidence and will not be rewritten in this ticket unless a concrete factual error in user-facing documentation is found.

## Desired End State

The checked-in implementation remains the smallest complete design unless new executable evidence exposes a defect. Fresh tests and verification demonstrate that:

- authenticated callers receive the complete public catalog, can submit a strictly bounded JSON object, and receive `202` plus a polling location without coupling accepted work to the request connection;
- all key-source modes enforce the same signature, issuer, audience, lifetime, and required-claim policy, and custom role claim/prefix settings agree with framework validation and invocation;
- records are readable only by the same issuer/subject pair, while the admitted JWT and authorities cross queueing and nested work without leaking to another task;
- one coordinator enforces worker, queued-count, queued-byte, and retained-record limits; releases reservations/references on every handoff, rejection, discard, and exit path; and publishes one complete terminal result/failure plus selected events;
- terminal TTL, diagnostics, and shutdown behavior remain bounded and owner-scoped, with no dispatch after the close gate and no Sidecar truncation of selected data;
- Console API-key security, separate-port health/readiness, no-store responses, supported Loomspan API use, configuration defaults, and README guidance continue to conform.

Packaged client/resource ordering, the container image, the released framework dependency, hosted CI, and release proof remain SC5 work. Application-level evidence in this plan must not claim those later gates.

## Scope

### In scope

- Re-read and verify the existing HTTP, JWT, ownership, execution, retention, diagnostics, and shutdown paths against every ticket criterion.
- Add focused deterministic assertions where current coverage does not directly prove a material criterion, prioritizing HTTP request-boundary behavior, custom role integration, exact terminal data, coordinator cleanup/accounting, and the close-to-dispatch gate.
- Apply the smallest production correction if a new or existing test exposes a ticket-scoped defect.
- Keep README and shipped defaults aligned with actual behavior and the SC4/SC5 boundary.
- Run focused and full Maven verification against the locally installed beta.4 snapshot.

### Out of scope

- SC4 production outbound REST handling, route parsing, or callback-host proof.
- SC5 image, packaged lifecycle/client ordering, framework publication, release dependency switch, hosted CI, or release actions.
- New authentication modes, token issuance/refresh/exchange, synchronous execution, cancellation, streaming, retries, durable storage, hot reload, catalog role filtering, sanitization/truncation policy, or framework SPIs.
- Reconciliation of stale SC2/SC3 phase completion checkboxes solely as process bookkeeping.
- The unrelated untracked `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md`.

## Active Project Guardrails

- Choose the simplest complete correction and proportionate evidence; do not add speculative abstractions, a cache, a staging queue, duplicate DTO systems, or test-only production machinery without a concrete need.
- Application and test code may depend on Loomspan only through the supported `ai.loomspan.api` surface. `RestSkillHandler` is the authorized test SPI; do not use internals, autoconfiguration types, reflection bypasses, bean replacement, or Java `@SkillMethod` fixtures.
- The framework remains authoritative for catalog semantics, validation, authorization, invocation, nesting, observer delivery, and admitted execution lifetime. Sidecar owns transport, admission, owner lookup, retention, selected history, and its immediate close gate.
- `ExecutionCoordinator` remains the single owner of the executor queue, queued count/bytes, retained map, and terminal snapshot. Locks must stay out of validation, invocation, observer delivery, and response serialization.
- Trusted identity stays outside model input. Ownership is the immutable issuer/subject pair; the captured JWT authentication exists only for queued/running work and is cleared with the worker context.
- Skills/routes remain startup-only. On close, Sidecar stops admission and dispatch, discards queued work, and leaves already-dispatched work to the framework's one shutdown budget.
- Preserve result text and all selected available public events unchanged. Retention bounds do not claim to bound total heap.

## Impact and Risk Analysis

The most consequential residual risk is false assurance at boundaries whose implementation is shared across several outcomes. Direct reader tests prove byte handling, for example, but not the HTTP `413` problem response or that rejection leaves coordinator capacity untouched. A focused application HTTP assertion should cover this seam without creating a second fixture. Similarly, the unit role-converter assertion proves prefix construction but not agreement with the framework's request-thread and invocation-time RBAC checks; a small custom-role application context is stronger evidence than additional converter internals.

Concurrency tests must remain deterministic. The shared coordinator lock already serializes admission, queue reservation, begin, completion, expiry, and close transitions. Tests should exercise state immediately around controllable latches or package-visible state and should not add arbitrary timing races. Queue-full rejection is the real `ThreadPoolExecutor.execute` rollback path and can prove provisional-record and reservation cleanup. A direct `begin`-after-close boundary assertion can prove that a task already removed from the queue still cannot enter the facade after the dispatch gate closes, without introducing production scheduling hooks.

Reference-release evidence should avoid unreliable garbage-collection assertions. Where existing control flow makes clearing obvious, inspect deterministic task state only if that can be done without broadening production API or retaining a task solely for testing. Otherwise combine rejection/discard/worker-exit state invariants, reused-worker security-context assertions, and code review; do not add a public diagnostic endpoint or reflection into framework internals.

Large results and selected events are intentionally unbounded. Tests should use representative payloads large enough to catch accidental Sidecar truncation but small enough to keep the suite fast. Client disconnect independence follows from acceptance completing before polling and the absence of request-owned cancellation; use a completed `202` response whose client body/connection is released before unblocking work rather than a timing-sensitive half-written socket test.

The local snapshot and loopback fixture services are required. No routine test may contact a production issuer, model, callback, provider, or other external service. The Full 5-Step Pipeline remains the correct profile because fresh assurance still spans security, protocol, concurrency, retention, and lifecycle boundaries.

## Implementation Approach

Preserve the existing architecture and treat current behavior as the implementation under verification. First add the cheapest missing acceptance assertions at the boundary that owns each rule. Run those focused tests; if one fails, trace the failure through the existing owner and make the smallest local correction rather than redesigning settled behavior. Then run the related integration set and full `verify`, update README only for a demonstrated mismatch, and retain exact verification results for the pipeline handoff.

The selected approach deliberately does not recreate every aspirational case from the deleted historical testing plan. Existing shared paths plus focused additions can prove the contract without test-only schedulers, executor injection, weak-reference polling, a second full application fixture per edge case, or SC5 package tests. Material gaps get executable evidence; structural invariants that are already directly exercised through the same path remain review evidence.

## Phase 1: Strengthen transport and security boundary evidence

### Changes

- [x] `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java` — extend the existing random-port fixture to send controlled raw UTF-8 bodies and assert HTTP-level missing/malformed/non-object handling, exact `max-input-size` acceptance, one-byte excess `413`, problem media type, `Cache-Control: no-store`, and unchanged retained/queued accounting after every rejected request. Keep `{}` on the framework validation path and verify it returns issues rather than being rejected by the reader.
- [x] `src/test/java/ai/loomspan/sidecar/security/JwtSecurityConfigurationTest.java` — complete the per-mode negative matrix where the current discovery/JWKS coverage only demonstrates successful decode, so discovery, JWKS, and public-key modes all have direct issuer/audience/lifetime/required-claim evidence using only loopback metadata and deterministic local keys.
- [x] `src/test/java/ai/loomspan/sidecar/execution/CustomRoleExecutionIntegrationTest.java` (or the smallest equivalent reuse of the current application fixture) — configure a nondefault roles claim and prefix; submit with and without the asserted role; prove both request-thread `SkillTemplate.validate` and worker `SkillTemplate.invoke` accept the correctly prefixed role and reject the wrong/missing claim. Extend `src/test/java/ai/loomspan/sidecar/support/JwtTestTokens.java` only as needed to mint the configured claim locally.
- [x] `src/main/java/ai/loomspan/sidecar/api/**` and `src/main/java/ai/loomspan/sidecar/security/**` — make no planned redesign; change only the owning reader/controller/advice/decoder/converter symbol if the new boundary test exposes nonconformance.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp "-Dtest=ExecutionRequestBodyIntegrationTest,AuthenticatedExecutionApiIntegrationTest,JwtSecurityConfigurationTest,CustomRoleExecutionIntegrationTest" test` — all raw-body/status, key-source, claims, and custom-role assertions pass through their intended boundaries with no external calls.

### Optional developer checks

- [x] None.

## Phase 2: Close execution lifecycle and diagnostics assurance gaps

### Changes

- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java` — extend deterministic latch/state cases to prove direct handoff does not charge queued bytes/count, queue-full rejection removes the provisional record without counter underflow, queue reservation releases at handoff rather than completion, retained capacity is reclaimed by expiry on admission, active records do not expire, and a task reaching `begin` after owning-context close cannot invoke the facade. Preserve the existing single coordinator/executor design.
- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java` and `AuthenticatedExecutionApiIntegrationTest.java` — strengthen worker cleanup evidence by proving sequential tasks on the same worker see only their own authentication and by asserting discard/rejection/exit invariants without GC timing or new public diagnostics. Demonstrate accepted work continues after the HTTP response is released, which is the deterministic client-disconnect boundary available to the application.
- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java` — add exact large-result and large-event assertions, observer delivery ordering across available views, mixed child-event versus primary-facade failure classification, and whole-record expiry so result/failure and selected events disappear together. Keep fixture payloads bounded for test speed and do not use Console/internal history.
- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java` — retain the synchronous-listener contract test and add a prompt close assertion around blocked active work, discarded queued accounting, and readiness publication where that evidence is not already cheapest in `ExecutionCoordinatorTest`; verify foreign-context close remains inert.
- [x] `src/test/java/ai/loomspan/sidecar/api/ApiExceptionHandlerTest.java` (if HTTP-close testing through a live closing context is not deterministic) — assert `ExecutionUnavailableException` maps to a `503` problem document. Do not shut down the server underneath an in-flight assertion merely to obtain an HTTP socket response.
- [x] `src/main/java/ai/loomspan/sidecar/execution/**` — change only the existing coordinator/task/snapshot/failure owner if a focused lifecycle test exposes a defect; do not introduce a second admission or history owner.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp "-Dtest=ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ApiExceptionHandlerTest" test` — lifecycle transitions, cleanup, diagnostics, retention, accepted-work independence, and shutdown mapping pass deterministically.

### Optional developer checks

- [x] None. SC5 owns packaged listener ordering and client-resource lifetime after framework cutoff.

## Phase 3: Reconcile documentation and complete fresh verification

### Changes

- [x] `README.md` and `src/main/resources/application.yml` — compare documented routes, key sources, role mapping, limits, polling/failures, ownership, diagnostics/data exposure, shutdown/restart, Console coexistence, and SC4/SC5 exclusions with the verified implementation; edit only demonstrated mismatches and keep secrets/environment guidance intact.
- [x] `src/test/java/ai/loomspan/sidecar/config/SidecarDefaultsTest.java` and `SidecarExecutionPropertiesTest.java` — ensure every shipped execution default and every invalid size/count/TTL category remain covered without duplicating Spring binding behavior unnecessarily.
- [x] `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java` — retain the whole-package checks so all new test support continues to avoid framework internals/autoconfiguration and Java skills.
- [x] `ai/thoughts/phases/phase-sc2.md`, `ai/thoughts/phases/phase-sc3.md`, and `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md` — preserve unchanged; they are not ticket-scoped implementation evidence for this pass.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp "-Dtest=SidecarDefaultsTest,SidecarExecutionPropertiesTest,SupportedLoomspanApiArchitectureTest" test` — defaults, validation, and framework-boundary checks pass.
- [x] `.\mvnw.cmd -B -ntp verify` — the complete Sidecar test suite and repackaged jar pass against the locally installed `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`.
- [x] `git diff --check -- README.md src/main src/test ai/thoughts/plans` — ticket-scoped edits and the new planning artifacts have no whitespace errors.

### Optional developer checks

- [x] None for SC2/SC3. Image startup, packaged resource ordering, published dependency, hosted CI, and release verification remain SC5 gates.

## Test Strategy

Use JUnit 5, AssertJ, Spring Boot random-port tests, Java's loopback `HttpClient`, Mockito at the public `SkillTemplate` boundary, deterministic local JWT keys/servers, latches, and the existing package-visible coordinator state. Favor focused unit/state-machine tests for accounting and expiry, then application integration tests for servlet security, framework RBAC, mounted YAML/REST invocation, and response contracts. Do not infer Sidecar behavior from framework tests and do not create live or destructive fixtures. Step 3 provides the individual test specifications and exit criteria.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Four JWT routes; complete catalog; `400/401/403/404/413`; raw equality; no rejected records | Existing catalog/controller/reader/advice/security filter; smallest correction only if exposed | Extended `AuthenticatedExecutionApiIntegrationTest`, `ExecutionRequestBodyIntegrationTest`, retained/counter invariants |
| Discovery, JWKS, public key, common validators, skew, custom roles | Existing `JwtSecurityConfiguration`, `SidecarJwtProperties`, `GrantedAuthorityDefaults` | Completed decoder-mode matrix plus `CustomRoleExecutionIntegrationTest` through validate and invoke |
| Prompt `202`, exact result/failure, problem documents, no stack trace, accepted work independent of client | Existing `ExecutionController`, coordinator task, failure classifier | Existing polling/failure cases plus blocked work released after response/client disposal and large exact result |
| Issuer/subject ownership and queued/nested original identity without leakage | Existing `ExecutionOwner` and worker context install/clear | Renewed/foreign/expired/nested cases plus sequential reused-worker authentication assertions |
| Worker, queue count/bytes, retained capacity, equality/excess, exact release/rollback | Existing `ExecutionCoordinator`, `AdmissionQueue`, task reservation flag | Extended deterministic coordinator saturation, direct handoff, rejection, handoff, close, and admission-reclaim cases |
| Invalid configuration, completion TTL, whole-record removal, released task references | Existing validated properties, expiry sweep, record/task separation | Complete invalid-property matrix; mutable-clock active/terminal/admission expiry; cleanup/context invariants |
| Diagnostics modes, atomic terminal state, facade-primary failure, no callback, no truncation | Existing immutable `ExecutionSnapshot`, `complete`, and classifier | Diagnostics matrix plus ordered multi-view, mixed-failure, large payload, and outcome/events expiry assertions |
| Prompt owning-context shutdown, readiness, no post-close dispatch, queued discard, active preservation | Existing synchronous `ContextClosedEvent` listener and shared gate | Close/begin boundary, blocked active/queued case, readiness/foreign-context tests, and `503` advice mapping |
| Console/management coexistence, no-store, statelessness, supported API, docs | Existing filter chain, management config, filter, architecture rule, README | Existing Console/management integration, expanded error no-store checks, defaults/ArchUnit tests, full `verify` |

## Risks and Rollback/Recovery

Most planned edits are tests and documentation. If a test is flaky, replace timing assumptions with an existing latch, mutable clock, or state transition rather than increasing sleeps. If a production correction is necessary, keep it isolated to the current owner and rerun its focused suite before the full build. Revert only ticket-scoped new edits if the approach proves invalid; do not reset or overwrite the unrelated untracked SC4 ticket.

If the installed framework snapshot is missing or behavior disagrees with its matching public source, stop and report the dependency/environment evidence rather than importing an internal type or rebuilding framework as part of Sidecar. If a required public contract is absent, treat it as a framework issue requiring developer coordination. Packaged lifecycle or release-only uncertainty is recorded as SC5 scope, not worked around here.

## References

- `ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md`
- `ai/thoughts/research/2026-09-13-authenticated-execution-api.md`
- `ai/thoughts/design-lens.md`
- `ai/thoughts/beta4-handoff.md`
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java`
- `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java`
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java`
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java`
- `README.md`
