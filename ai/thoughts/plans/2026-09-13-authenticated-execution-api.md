# Authenticated Execution API Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md`
- Research: `ai/thoughts/research/2026-09-13-authenticated-execution-api.md`
- Outcome: expose the mounted public Loomspan catalog and an asynchronous, JWT-authenticated, owner-scoped execution API with bounded admission, retained outcomes, diagnostic selection, and prompt shutdown gates.

## Current State

`LoomspanSidecarApplication` is the only production Java type. The starter already provides an eager `SkillCatalog` and synchronous `SkillTemplate` through the supported `ai.loomspan.api` surface, while `application.yml` supplies only mounted-skill, reserved route-file, observability, and management defaults. The POM already includes MVC, Spring Security, OAuth2 resource-server/JOSE, Actuator, Java 21, and Boot 4.1.0; there is no controller, JWT decoder, execution store, executor, readiness gate, or Sidecar error mapping.

The existing tests prove startup skill registration, separate health-only management exposure, defaults, and the supported API boundary. They do not prove application HTTP behavior, authentication, framework execution, concurrency/accounting, retention, or close behavior. `SupportedLoomspanApiArchitectureTest` applies to test code as well as production code, so the new common fixture must use YAML/REST manifests plus the public `RestSkillHandler` SPI and must not declare Java `@SkillMethod` skills.

## Desired End State

All `/v1/**` requests require a verified bearer JWT. Authenticated callers can see all public catalog descriptors, validate and submit a JSON-object input asynchronously, and poll executions owned by the same explicit issuer/subject pair. Validation, authorization, and invocation remain framework-owned; Sidecar owns HTTP parsing, bounded admission, queued identity propagation, record retention, diagnostics selection, and transport errors.

Accepted work has one retained record and moves `QUEUED` to `RUNNING` to one atomically published terminal `COMPLETED` or `FAILED` snapshot. Capacity, queued count, and queued bytes are reserved and released by one coordinator. The worker executes with the originally verified `JwtAuthenticationToken`, allowing SC4's future caller-passthrough handler to read the original token, while task input and authentication references are released on exit.

Shutdown immediately publishes refusal of traffic, closes admission and the dequeue-to-invocation gate, removes queued records/tasks, and returns from the close listener. Already-dispatched calls remain alive for the framework's later lifecycle wait. SC5 can therefore add packaged listener-order/client-lifetime evidence without replacing this ticket's gates or adding a second timeout.

Acceptance criteria map as follows:

- HTTP status, problem-document, catalog, validation, and unchanged-result behavior belong to the API controllers and advice.
- JWT key-source validation, issuer/audience/lifetime/claims, role conversion, statelessness, no-store, Console coexistence, and ownership belong to the security configuration and owner extraction.
- concurrency, queue bytes/count, retained capacity, TTL, diagnostic selection, reference release, and dispatch races belong to one execution coordinator and its bounded executor queue.
- immediate readiness/admission/dispatch closure belongs to that coordinator's owning-context close listener; final executor destruction happens only in bean destruction after Spring lifecycle stop.

## Scope

### In scope

- Four JWT-authenticated `/v1` routes and RFC 9457 errors.
- Explicit JWT configuration for discovery, JWKS, and RSA public-key modes; common validation and authority conversion.
- In-memory execution lifecycle, ownership, failure classification, selected public events, TTL, and all admission limits.
- Original authentication propagation across queueing and nested framework work.
- Independent application-context close/readiness/dispatch gates and bounded resource cleanup.
- Local deterministic application, security, concurrency, retention, diagnostic, and shutdown tests.
- README configuration, API, security, limits, operations, and phase-boundary documentation.

### Out of scope

- SC4's production `RestSkillHandler`, route-file parser, outbound clients, and callback-host proof.
- SC5's image, packaged lifecycle matrix, quick start, release workflow, and switch to the published framework artifact.
- API-key execution authentication, token mint/refresh/exchange, X.509 identity mapping, synchronous waits, cancellation, streaming, retries, durable storage, hot reload, role-filtered catalog, and new framework SPIs.

## Active Project Guardrails

- Choose the smallest complete design; do not add speculative extension layers, a cache library, a scheduler per record, or duplicate DTO hierarchies.
- Depend on Loomspan only through `ai.loomspan.api`; do not replace framework beans, inspect internals, or host Java skills.
- The framework remains authoritative for skill lookup semantics, validation, authorization, invocation, nesting, and execution lifetime.
- One Sidecar coordinator owns retained records, executor queue admission, queued byte/count accounting, and terminal publication.
- Trusted identity is the verified JWT authentication, never an input field. Ownership is an immutable pair of issuer and subject.
- Skills and routes activate only at startup. Restart discards the in-memory execution store.
- Close dispatch immediately, discard waiting work, and leave already-dispatched work to the framework's single shutdown budget.
- Preserve unchanged result text, facade-primary failure classification, and every selected available public event without Sidecar truncation.

## Impact and Risk Analysis

The highest-risk boundary is the transition between HTTP admission and executor dispatch. A retained record must not exist without accepted work; direct dispatch must not consume waiting-queue count/bytes; queued reservations must be released once on dequeue or discard; and close must synchronize with dequeue so a task cannot start `SkillTemplate.invoke` after the gate closes. The solution needs one lock for these short state transitions but must never hold it during validation, invocation, observer delivery, or JSON serialization.

JWT modes can easily diverge if decoder convenience methods install different validators. Every decoder will therefore receive the same explicit validator chain after construction: timestamp with configured skew, trusted issuer, expected audience, and required nonblank `iss`/`sub` plus `exp`. Exactly one verification-key source is inferred: neither explicit setting means issuer discovery, while `jwk-set-uri` and `public-key-location` are mutually exclusive. `issuer-uri` and `audience` remain mandatory in every mode. The role claim and prefix feed both the JWT authority converter and `GrantedAuthorityDefaults`, preventing validation-time and invocation-time RBAC disagreement.

Execution records and events may contain business data. Record-count and TTL limits are not a total heap guarantee; queued bytes cover only the measured JSON map waiting in the executor and exclude running inputs, results, and diagnostics. No new output truncation or sanitization is introduced. Terminal state and selected diagnostics must be published as one immutable snapshot so polling never sees a terminal status without its outcome/history.

The close listener must not stop or interrupt active caller workers, because the framework waits later during lifecycle stop. It will only close gates and remove waiting tasks. A bean-destruction callback can then issue immediate bounded executor cleanup after lifecycle stop, without relying on the framework bean name, listener ordering, or numeric phase. SC5 will reuse these seams to prove packaged clients and either listener order.

## Implementation Approach

### Implementation observation (2026-09-13)

The implementation exposed one framework incompatibility that source review did
not predict:

- **Expected:** with separate-port Actuator health enabled, Loomspan Console's
  supported registrar activates under its reserved namespace and Sidecar passes
  that namespace to the framework API-key filter.
- **Found:** `ObservabilityRouteCollisionDetector` treats Boot 4.1's
  application-context `AdditionalHealthEndpointPathsWebMvcHandlerMapping` as an
  unknown handler mapping and reports a collision. The registrar disables
  Console before Sidecar security participates. The documented
  `management.endpoint.health.probes.add-additional-paths=false` leaves this
  empty mapping bean present and does not change the result.
- **Why it matters:** the ticket requires both separate-port readiness and
  opt-in Console coexistence. Removing readiness or replacing/filtering
  framework beans violates the governing boundary. The smallest correction is
  a framework fix recognizing this standard Boot mapping when it has no
  reserved-path collision, followed by the developer-managed snapshot install
  and rerunning Sidecar's related and full suites.

**Developer resolution:** PR 5.5
(`loomspan-pr-5.5-positive-observability-route-collisions.md`) completed the
narrow framework collision fix, and the developer ran `maven install` to refresh
the local `1.0.0-beta.4-SNAPSHOT`. Per repository policy, Sidecar relies on that
install workflow without separate provenance verification and reruns the affected
integration checks.

### Implementation adaptations (2026-09-13)

- The coordinator uses one `LinkedTransferQueue` with a guarded admission hook:
  `tryTransfer` identifies direct worker handoff, while the same coordinator
  lock bounds actual waiting entries by count and measured bytes. This preserves
  one executor queue and one capacity authority while making the required
  direct-versus-waiting distinction explicit; there is no staging queue.
- The immutable terminal state is represented by `ExecutionSnapshot`, and the
  small executor task is nested in `ExecutionCoordinator`, rather than adding
  separate `ExecutionOutcome` and `ExecutionTask` source files. This keeps the
  state transitions and reference cleanup under the single coordinator owner.
- The deterministic application fixture is kept local to
  `AuthenticatedExecutionApiIntegrationTest`, with the reusable JWT helper in
  `JwtTestTokens`. Its loopback model server drives a real YAML-to-REST nested
  invocation, so the original JWT reaches the public `RestSkillHandler` without
  a production callback handler or Java skill.

Add two validated property groups. `SidecarJwtProperties` uses `issuer-uri`, `audience`, optional `jwk-set-uri`, optional `public-key-location`, `clock-skew` (default `60s`), `roles-claim` (default `roles`), and `role-prefix` (default `ROLE_`). `SidecarExecutionProperties` uses the ticket's seven defaults. Bind them explicitly from `loomspan-sidecar.auth.jwt` and `loomspan-sidecar.executions`, validating positive sizes/counts/durations and rejecting ambiguous key sources at startup.

`JwtSecurityConfiguration` creates the selected standard decoder, applies one common validator chain, builds a `JwtAuthenticationConverter`, and publishes matching `GrantedAuthorityDefaults`. Its stateless filter chain permits the framework-reserved `/_loomspan/observability/v1/**` namespace so the framework's API-key filter remains authoritative, authenticates `/v1/**`, disables sessions/CSRF, and denies unrelated application routes. A small response filter applies `Cache-Control: no-store` to successes and errors. `ExecutionOwner` extracts and validates explicit issuer/subject claims from `JwtAuthenticationToken`.

Controllers return `SkillDescriptor` values directly and use small Sidecar response records only for accepted/execution/failure representations. `LimitedJsonObjectReader` reads at most `max-input-size + 1` bytes from the servlet stream before Jackson parsing, rejects missing/null/malformed/non-object bodies, and supplies the parsed `Map<String,Object>`. After framework validation, it serializes that map once to UTF-8 bytes only to obtain the queued-accounting size and does not retain those bytes.

`ExecutionCoordinator` owns a `ConcurrentHashMap<UUID, ExecutionRecord>`, one `ThreadPoolExecutor` with one bounded `ArrayBlockingQueue`, the short admission/dispatch lock, and queued count/byte totals. A coordinator-owned task is submitted directly to the executor; the executor's queue admission hook reserves count and bytes only when `execute` actually queues it. Record insertion, open-gate checks, expiry reclamation, and dispatch rollback are coordinated by the same owner. The task's run transition synchronizes with the dispatch gate, releases queued accounting before marking `RUNNING`, installs a fresh worker `SecurityContext` containing the captured authentication, invokes the public Map/observer overload, classifies only the thrown facade exception, atomically publishes the terminal snapshot plus selected copied public events, and clears/nulls input and authentication in `finally`.

An owning-context `ContextClosedEvent` listener overrides `supportsAsyncExecution()` to return false. Under the same dispatch lock it publishes `ReadinessState.REFUSING_TRAFFIC`, closes admission/dispatch, drains/removes queued tasks and records, releases accounting, and drops their input/authentication references, then returns without waiting or calling the framework. Bean destruction occurs after lifecycle stop and uses immediate executor shutdown with no unbounded close wait or second grace period.

Rejected alternatives are a separate semaphore plus queue counters (duplicate admission authorities), a cache/scheduler library (unneeded lifecycle and per-entry machinery), and `CompletableFuture` per request (obscures queue accounting and shutdown removal). A coordinator around the standard bounded executor is the smallest design that exposes the required atomic transitions.

## Phase 1: Bind execution and JWT policy

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/config/SidecarExecutionProperties.java` — bind the seven execution defaults, diagnostics enum, and startup validation for positive counts/sizes/TTL.
- [x] `src/main/java/ai/loomspan/sidecar/security/SidecarJwtProperties.java` — bind issuer, audience, optional JWKS/public-key resource, skew, role claim, and prefix; require issuer/audience/claim and reject both explicit key sources.
- [x] `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java` — construct discovery/JWKS/RSA-public-key decoders and attach the same issuer/audience/timestamp/required-claim validators; align `JwtAuthenticationConverter` with `GrantedAuthorityDefaults`.
- [x] `src/main/java/ai/loomspan/sidecar/security/ExecutionOwner.java` — represent and extract the nonblank issuer/subject pair without retaining the JWT.
- [x] `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java` — enable the two configuration-property classes without broad or duplicate scanning.
- [x] `src/main/resources/application.yml` — add the agreed execution defaults and documented JWT role/skew defaults while leaving issuer, audience, and key material deployment-supplied.
- [x] `src/test/java/ai/loomspan/sidecar/config/SidecarDefaultsTest.java` — assert every new shipped default.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp -Dtest=SidecarDefaultsTest,SidecarExecutionPropertiesTest,JwtSecurityConfigurationTest test` — all defaults, invalid execution settings, decoder modes, common validators, skew, and custom roles behave as configured.

### Optional developer checks

- [x] None.

## Phase 2: Add authenticated HTTP and owner-scoped representations

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java` — add the stateless application filter chain, `/v1/**` JWT requirement, observability pass-through, default denial, and framework-compatible authorities.
- [x] `src/main/java/ai/loomspan/sidecar/api/SkillCatalogController.java` — return the complete public descriptor list and exact descriptor lookup with `404` for unknown names.
- [x] `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java` — implement POST resolve/validate/measure/admit and owner-only GET polling, returning `202` plus absolute request-relative `Location` and `200` for known failed executions.
- [x] `src/main/java/ai/loomspan/sidecar/api/LimitedJsonObjectReader.java` — enforce the raw request byte cap before parsing and require a JSON object, permitting exact-size input.
- [x] `src/main/java/ai/loomspan/sidecar/api/ExecutionResponse.java` and `ExecutionFailure.java` — define only the agreed wire fields, leave result text unchanged, and omit stack traces.
- [x] `src/main/java/ai/loomspan/sidecar/api/ApiExceptionHandler.java` — map malformed/missing/non-object input and framework validation issues to `400`, access denial to `403`, oversize to `413`, capacity to `429`, shutdown admission to `503`, and unknown resources to `404` using Spring `ProblemDetail` with `application/problem+json`.
- [x] `src/main/java/ai/loomspan/sidecar/api/NoStoreResponseFilter.java` — set `Cache-Control: no-store` before delegating and order the filter ahead of bearer authentication so it applies even when Spring Security completes an authentication failure without reaching MVC.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp -Dtest=AuthenticatedExecutionApiIntegrationTest,ExecutionRequestBodyIntegrationTest test` — all routes, authentication/status mappings, catalog visibility, owner masking, problem media type, no-store, and input-size/body boundaries pass through the real application HTTP stack.

### Optional developer checks

- [x] None.

## Phase 3: Implement one bounded execution lifecycle

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionStatus.java`, `ExecutionRecord.java`, and `ExecutionSnapshot.java` — model queued/running state and one immutable terminal snapshot containing completion time, exact result or classified failure, and selected available events.
- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java` — own record retention, the standard bounded executor/queue, direct-versus-waiting admission, queued byte/count reservations, expiry sweep, owner lookup, rollback, and short dispatch lock.
- [x] the coordinator-owned `ExecutionTask` — retain the admitted Map and authentication only while active/queued, install and clear worker security context, call `SkillTemplate.invoke(name, map, observer)`, release queue reservations at handoff, and clear task references at worker exit/removal.
- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionFailureClassifier.java` — map the primary `SkillInputValidationException`, `AccessDeniedException`, and all other framework facade failures to the three agreed kinds without arbitrary cause unwrapping.
- [x] `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java` — serialize coordinator snapshots outside the admission lock, exposing events only according to `NEVER`, `ONERROR`, or `ALWAYS`.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp -Dtest=ExecutionCoordinatorTest,AuthenticatedExecutionApiIntegrationTest test` — direct work, queued transitions, simultaneous capacity bounds, UTF-8 equality/excess, rollback, expiry, failure classification, diagnostics, identity propagation, and reference release pass.

### Optional developer checks

- [x] None.

## Phase 4: Close dispatch promptly and preserve framework-owned active work

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java` — implement a synchronous owning-context close listener that lowers readiness, atomically closes admission/dispatch, drains waiting tasks without invocation, removes their records, releases reservations/references, and returns promptly.
- [x] the coordinator-owned `ExecutionTask` — synchronize the dequeue-to-invoke boundary with the coordinator gate so a close race cannot start another facade call.
- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java` — add post-lifecycle bean destruction that tears down worker resources without an unbounded wait, early interruption during the close event, or a second drain timer.
- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java` — cover readiness/admission closure, management-context filtering, asynchronous event multicasting, dequeue races, queue cleanup, and an already-dispatched invocation surviving the close listener.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp -Dtest=ExecutionShutdownIntegrationTest,ManagementEndpointIntegrationTest test` — the application gate closes synchronously while management health isolation remains intact and active facade callers are not stopped by the listener.

### Optional developer checks

- [x] SC5 later runs the packaged listener-order and client/resource-lifetime matrix; it is explicitly nonblocking for this unit.

## Phase 5: Complete deterministic application evidence and documentation

### Changes

- [x] the application integration fixture plus `src/test/java/ai/loomspan/sidecar/support/JwtTestTokens.java` — provide local JWT/JWKS/RSA helpers, a loopback deterministic model stub, blocking controls, and a test-only public `RestSkillHandler` that captures the current JWT without Java skills.
- [x] `src/test/resources/fixtures/execution-skills/*.yaml` — add public REST/YAML manifests for exact results, validation, RBAC, nested identity, failure, blocking, and observable-history scenarios.
- [x] `src/test/java/ai/loomspan/sidecar/security/JwtSecurityConfigurationTest.java` and `ConsoleSecurityIntegrationTest.java` — prove all key-source/claim/role cases and independent Console application security.
- [x] `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java` — prove asynchronous accepted work, polling, role checks at validation/invocation, queued/nested original-token propagation, expiry-after-admission, no leakage, failures, diagnostics, client disconnect, large exact results/events, and ownership.
- [x] `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java` — retain the existing whole-package internal/autoconfigure and Java-skill prohibitions over all new production and fixture code.
- [x] `README.md` — replace the SC1 deferral with route examples, JWT modes and roles, polling/failures, owner semantics, all limits, diagnostics/data exposure, no-store/statelessness, mTLS transport settings, token lifetime/audience and session-host guidance, Console coexistence, startup-only activation, shutdown/restart behavior, and explicit SC4/SC5 exclusions.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp verify` — the wrapper build, all application tests, and architecture checks pass against the locally installed beta.4 snapshot without external credentials or services.

### Optional developer checks

- [x] None for ticket acceptance; SC5 later verifies the same behavior from the packaged image and published framework dependency.

## Test Strategy

Start with an authenticated POST integration test that currently fails because `/v1/skills/{name}/executions` does not exist. Add focused property/security tests for fast feedback and coordinator tests with deterministic latches and an injected clock. Use full random-port application tests for the boundaries where filters, JSON body reading, framework public APIs, request authentication, worker context, and polling interact.

The common fixture uses only loopback servers and test-generated RSA JWTs. REST test manifests and a test-only `RestSkillHandler` exercise invocation and worker identity without implementing SC4. A deterministic local model stub is used only where a nested YAML-to-REST path is needed. Concurrency tests coordinate with barriers/latches rather than timing guesses; TTL tests use an injected clock; shutdown tests publish actual context events and observe readiness/gates. No routine test calls a live issuer, model, or callback service.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Four authenticated routes, complete catalog, status mappings, body/byte bounds | catalog/execution controllers, limited reader, problem advice | `AuthenticatedExecutionApiIntegrationTest`, `ExecutionRequestBodyIntegrationTest` |
| Three key modes and identical JWT validation/role semantics | JWT properties, decoder factory, validator chain, converter/default authority prefix | `JwtSecurityConfigurationTest` plus role cases in the application fixture |
| Asynchronous `202`, polling exact success/failure, disconnect independence | controller and coordinator/task | accepted/poll/failure/disconnect integration cases |
| Explicit issuer/subject ownership and queued/nested original identity without leakage | `ExecutionOwner`, task security-context handoff/finally cleanup | owner renewal/foreign issuer-subject and sequential worker identity cases |
| Worker/queue/byte/retained limits and exact cleanup | one coordinator, queue admission hook, retained map | deterministic saturation, multibyte equality/excess, simultaneous admission, rollback/dequeue tests |
| Valid configuration, completion TTL, no partial terminal publication, released references | validated properties, injected clock, immutable terminal outcome, task cleanup | context failures, clock-driven expiry, polling-race, internal cleanup assertions |
| Diagnostic modes, primary failures, missing callback, intact large outputs/history | observer capture and facade exception classifier | mode matrix, nested mixed failure, no-callback, large payload tests |
| Immediate owning-context shutdown/readiness and no new dispatch | coordinator close listener and synchronized task handoff | `ExecutionShutdownIntegrationTest` race/context/async-listener cases |
| Console and management coexistence; stateless no-store responses | security matcher ordering, response filter, existing management config | `ConsoleSecurityIntegrationTest`, management test, response-header assertions |
| Public API boundary, deterministic tests, and complete user documentation | supported imports only, fixture SPI, README | ArchUnit plus full `verify`; documentation review against bound properties |

## Risks and Rollback/Recovery

Because the store is intentionally in memory, process restart is the recovery mechanism: queued and retained records are lost and clients receive `404` afterward. A partial admission or executor rejection is recovered synchronously by removing the record, releasing any queue reservation, and clearing task references before returning `429`/`503`.

If implementation discovers that a required behavior cannot be achieved with the public beta.4 API, stop rather than import framework internals and raise a framework contract issue. If a concurrency test exposes an ambiguous ownership transition, keep the single coordinator and tighten its state machine/critical section rather than adding another semaphore or store. No release migration or durable rollback is required because this is an unreleased, in-memory API addition.

## References

- `ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md`
- `ai/thoughts/research/2026-09-13-authenticated-execution-api.md`
- `ai/thoughts/design-lens.md`
- `ai/thoughts/phases/phase-sc2.md`
- `ai/thoughts/phases/phase-sc3.md`
- `ai/thoughts/phases/phase-sc4.md`
- `ai/thoughts/phases/phase-sc5.md`
- `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java`
- `src/main/resources/application.yml`
- `pom.xml`
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`
