# Authenticated Execution API Testing Plan

## Change Summary

The authenticated asynchronous execution API is already present in commits `2597238` and `fba8351`. This plan adds fresh, ticket-scoped assurance for the current implementation and requires the smallest correction only when executable evidence exposes a defect. The emphasis is on currently weak direct evidence at the raw HTTP boundary, custom JWT role configuration through framework validation/invocation, exact terminal data preservation, queue/retention cleanup transitions, and the close-to-dispatch gate.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| HTTP input and errors | Reader unit behavior does not alone prove HTTP statuses, problem content type, no-store, or absence of accepted state | Random-port requests with controlled UTF-8 bytes plus coordinator count invariants |
| JWT verification | Successful discovery/JWKS decoding can hide validator drift among key-source modes | Local discovery/JWKS/public-key positive and negative claim matrices |
| Role authorization | Converter/prefix unit checks may not prove framework validation-time and invocation-time RBAC agree | A custom-claim/custom-prefix application context using the real YAML/REST skill path |
| Ownership and identity | Queued authentication could expire, be replaced, or leak through a reused worker | Existing expiry/renewal/nested tests plus sequential worker identity assertions |
| Admission/accounting | Direct work may consume queue budget, or rejection/handoff/close may leak or double-release count/bytes/records | Latch-controlled coordinator tests around direct dispatch, saturation, rejection, handoff, expiry admission, and close |
| Terminal publication | Polling could expose partial outcome/history or truncate result/events | Immutable snapshot polling plus exact large result/event and ordered multi-view assertions |
| Retention/reference lifetime | Reads could extend TTL, active work could expire, or task-held input/JWT could survive exit/discard | Mutable-clock active/terminal/admission cases and deterministic cleanup/context invariants |
| Shutdown | Work dequeued near close could enter the facade; close could wait for or interrupt already active work | Direct begin-after-close boundary, blocked active/queued case, readiness/foreign-context assertions, and `503` mapping |
| Security coexistence and framework boundary | JWT security could capture Console routes, expose management endpoints, cache sensitive errors, or depend on unsupported APIs | Existing Console/management tests, expanded no-store assertions, ArchUnit, and full Maven verification |
| Documentation and delivery scope | README could overstate memory bounds or claim SC4/SC5 behavior | Source/default/README comparison and preservation of explicit later-phase exclusions |

## Existing Coverage and Environment Constraints

The repository uses JUnit 5, AssertJ, Mockito, Spring Boot test contexts, Java's `HttpClient`, loopback `HttpServer`, and ArchUnit under Maven Surefire. `AuthenticatedExecutionApiIntegrationTest` is the shared full application fixture with deterministic RSA tokens, mounted YAML REST skills, a test-only public `RestSkillHandler`, and a loopback OpenAI-compatible server. Coordinator tests use the public `SkillTemplate` facade as a Mockito boundary, latches, and a mutable clock. These are the conventions to reuse.

Current tests already cover the four central route flows, missing JWT, unfiltered catalog, request validation and default-role denial, accepted polling and exact Unicode result, renewed/foreign ownership, owned `FAILED` as `200`, queued token expiry, nested token visibility, count/byte/retained saturation, simultaneous admissions, byte equality/excess, terminal expiry, shutdown discard, diagnostics modes, primary failure classification, no-callback failure, decoder modes, Console separation, management exposure, defaults, and public-API architecture.

Step 1's retained Surefire reports show 33 passing tests but are not fresh execution evidence. Step 4 must run the commands below against Java 21 and the developer-installed `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`. Tests may bind only loopback and use checked-in or temporary deterministic fixtures. No provider account, production issuer, callback target, container runtime, or network service is required. The Sidecar build must not rebuild the framework or add a snapshot repository.

SC5 owns packaged image startup, relative framework/client resource lifetime, the published framework dependency, hosted CI, and release verification. Those checks are neither routine tests nor completion gates for this ticket.

## Failing Test First

- Name: `rejectsOversizeAndMalformedBodiesAtHttpBoundaryWithoutAdmission`
- Type: existing-feature acceptance/characterization integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Arrange/Act/Assert: record coordinator retained/queued counters; send authenticated POST bodies that are missing, malformed, non-object, exactly at the configured UTF-8 raw limit, and one byte over; assert `400` or `413` problem responses and `Cache-Control: no-store` for rejected cases, the exact-size body reaches normal framework validation/admission behavior, and rejected cases leave all counters unchanged.
- Expected pre-fix failure: no historical red run applies because the production feature predates this plan. The test should pass on a conforming checkout; if it fails, its status/body/counter assertion identifies the ticket-scoped transport or rollback defect to correct before proceeding.

## Tests to Add or Update

### 1. `rejectsOversizeAndMalformedBodiesAtHttpBoundaryWithoutAdmission`

- Type: random-port Spring Boot HTTP integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: raw byte enforcement occurs before parsing/validation, invalid bodies do not create records or reservations, exact equality is allowed, errors are RFC problem JSON and no-store.
- Inputs/fixture: authenticated requests with explicit byte-array body publishers; empty, `null`, array, scalar, malformed/trailing JSON, multibyte Unicode at equality, and one-byte excess. Configure a small test-only max input size or generate the exact default-size object without changing production defaults.
- Doubles or boundary isolation: existing local application, JWT, REST handler, and loopback model fixtures; inject the package-visible coordinator only for before/after invariant counts.
- Edge cases: malformed oversize data must be `413`, `{}` must reach framework validation and return issues, and declared versus streamed body lengths must follow the same cap.

### 2. `appliesCommonValidationToDiscoveryJwksAndPublicKeyModes`

- Type: focused decoder/configuration test
- Location: `src/test/java/ai/loomspan/sidecar/security/JwtSecurityConfigurationTest.java`
- Proves: every verification-key mode checks signature, issuer, audience, expiration/lifetime, nonblank issuer/subject, and `exp`; configured skew and invalid/ambiguous settings behave identically where applicable.
- Inputs/fixture: checked-in deterministic RSA pair, loopback discovery/JWKS metadata, tokens differing one claim at a time, and a tampered/wrong-key signature.
- Doubles or boundary isolation: local HTTP server only; no IdP or DNS dependency.
- Edge cases: discovery when neither explicit key source is set; both explicit sources fail; missing/blank issuer, audience, roles claim, null prefix, negative skew, blank subject, and missing expiration.

### 3. `usesCustomRoleClaimAndPrefixForValidationAndInvocation`

- Type: Spring Boot application/framework integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/CustomRoleExecutionIntegrationTest.java`
- Proves: authorities come from the configured trusted claim; the configured prefix matches `GrantedAuthorityDefaults`; the role-protected skill succeeds at request validation and again at worker invocation; wrong/default/missing claims remain denied without admission.
- Inputs/fixture: existing `echo-rest.yml`, public-key JWT configuration with `roles-claim=groups` and `role-prefix=APP_`, locally minted tokens containing `groups` or only `roles`, and a test-only public REST handler.
- Doubles or boundary isolation: reuse the deterministic local handler and token support; no production handler or Java skill.
- Edge cases: correct group, wrong group, empty/missing group, and a token whose old `roles` claim would have granted access under default settings.

### 4. `acceptedWorkOutlivesTheHttpResponseAndWorkerContextDoesNotLeak`

- Type: random-port application integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: after the client consumes/releases the `202` response, accepted blocked work remains present and completes; sequential work on the same single worker receives its own original JWT and no prior authentication.
- Inputs/fixture: existing max-concurrent-one blocking handler, two distinct locally signed owners/tokens, latches, and response-body disposal before release.
- Doubles or boundary isolation: real local HTTP and public `RestSkillHandler`; no raw half-socket timing.
- Edge cases: second token expires while queued and is renewed only for polling; handler-observed token order matches admission order.

### 5. `directHandoffDoesNotConsumeWaitingBudgetAndHandoffReleasesBeforeCompletion`

- Type: focused coordinator concurrency test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: direct worker dispatch leaves queued count/bytes zero; a waiting task reserves exactly once; its reservation is released when a worker takes it even while invocation remains blocked; worker capacity remains occupied until invocation exits.
- Inputs/fixture: one worker, public-facade Mockito answer with separate entered/release latches for successive calls, and known input byte sizes.
- Doubles or boundary isolation: no application context or network.
- Edge cases: exact count/byte limit; counters never become negative after cleanup.

### 6. `queueRejectionRollsBackRecordAndReservationExactlyOnce`

- Type: focused coordinator state-machine test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: the real saturated `ThreadPoolExecutor.execute` rejection path removes its provisional record, leaves existing queue reservations unchanged, and does not underflow during later dequeue or shutdown cleanup.
- Inputs/fixture: one blocked worker, one allowed queue entry, one rejected submission, retained-count and queued count/byte observations before and after release/close.
- Doubles or boundary isolation: public `SkillTemplate` mock only.
- Edge cases: rejection by count with byte room; rejection by bytes with count room; exact equality accepted.

### 7. `expiryReclaimsCapacityOnAdmissionWithoutExpiringActiveWork`

- Type: focused coordinator retention test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: completion starts TTL, reads do not extend it, equality at expiry removes the whole terminal record, a new admission synchronously reclaims its slot without waiting for the sweeper, and queued/running records are unaffected by elapsed TTL.
- Inputs/fixture: mutable clock, max-retained one where practical, immediate and blocked facade answers.
- Doubles or boundary isolation: no wall-clock sleep except bounded worker-completion polling.
- Edge cases: exact completion-plus-TTL instant, no intervening read, active record beyond the configured duration.

### 8. `preventsFacadeEntryWhenBeginLosesTheCloseRace`

- Type: focused coordinator close-gate test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: a task that reaches the coordinator begin boundary after owning-context close releases any reservation/removes its record and never invokes `SkillTemplate`; already active work is not cancelled by the close listener.
- Inputs/fixture: package-visible coordinator/task/record construction or the smallest existing state seam, owning-context `ContextClosedEvent`, invocation counter, and latches for the already-active case.
- Doubles or boundary isolation: no executor replacement, staging queue, arbitrary race loop, or framework internals.
- Edge cases: close before begin, active before close, queued discard, repeated cleanup with zero counters.

### 9. `preservesLargeResultAndOrderedSelectedEventsWithoutTruncation`

- Type: focused coordinator diagnostics test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java`
- Proves: result strings and all selected public events/details are exact; events from every synchronously supplied view keep delivery order; selection follows `NEVER`, `ONERROR`, and `ALWAYS` without a Sidecar size cutoff.
- Inputs/fixture: bounded large Unicode/multiline string, several public `SkillExecutionView` callbacks with sizable JSON-like detail values, and success/failure variants.
- Doubles or boundary isolation: public facade and event types only; no Console/internal history.
- Edge cases: empty callback list, multiple callbacks, child-failure event followed by a different primary facade exception, null frame/route fields.

### 10. `expiresOutcomeAndSelectedEventsAsOneRecord`

- Type: focused coordinator diagnostics/retention test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java`
- Proves: terminal result or failure and selected events become visible in one snapshot and become unavailable together at TTL; polling never observes terminal status without its selected data.
- Inputs/fixture: `ALWAYS`/`ONERROR`, mutable clock, one public event, and terminal success/failure.
- Doubles or boundary isolation: package-private clock injection; no separate history store.
- Edge cases: expiry equality and no observer callback.

### 11. `mapsClosedAdmissionToServiceUnavailableProblem`

- Type: MVC advice unit or deterministic standalone MVC test
- Location: `src/test/java/ai/loomspan/sidecar/api/ApiExceptionHandlerTest.java`
- Proves: `ExecutionUnavailableException` is represented as `503 Service Unavailable` problem detail; the global no-store behavior remains covered by application HTTP error cases.
- Inputs/fixture: direct advice invocation or standalone MVC around the existing exception owner.
- Doubles or boundary isolation: do not close a live server while issuing the assertion.
- Edge cases: title, status, detail, and `application/problem+json` when exercised through MVC.

### 12. `closeListenerIsPromptSynchronousAndOwnerScoped`

- Type: Spring lifecycle/coordinator integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java`
- Proves: the listener opts out of asynchronous event dispatch, ignores foreign/management contexts, publishes `REFUSING_TRAFFIC` for its owning context, discards queued work, and returns while active work remains blocked.
- Inputs/fixture: mocked context/readiness event plus a blocked public-facade call and bounded latch await.
- Doubles or boundary isolation: standard Spring events only; no listener-order assumptions or framework bean replacement.
- Edge cases: foreign close first, active task completes after listener return, queued accounting is zero.

### 13. `rejectsEveryInvalidExecutionLimitCategory`

- Type: focused configuration test
- Location: `src/test/java/ai/loomspan/sidecar/config/SidecarExecutionPropertiesTest.java`
- Proves: all size/count/TTL fields and diagnostics fail validation when null, zero, or negative as applicable; one-unit positive boundaries are accepted.
- Inputs/fixture: direct property object mutations matching the existing convention.
- Doubles or boundary isolation: none.
- Edge cases: null `DataSize`, null diagnostics, zero bytes, zero count, zero/negative TTL.

### 14. `preservesConsoleManagementAndSupportedApiBoundaries`

- Type: existing application integration and architecture regression tests
- Location: `src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java`, and `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`
- Proves: Console API keys and execution JWTs remain independent, only health/readiness is exposed on the separate management port, errors remain non-cacheable, all Sidecar/test code avoids internal/autoconfigure dependencies, and no Java skills are declared.
- Inputs/fixture: current opt-in Console and random management-port contexts; whole-package ArchUnit import.
- Doubles or boundary isolation: supported framework configuration and public surface only.
- Edge cases: API key on `/v1`, JWT on Console route, application-port health, unrelated management endpoint.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -B -ntp "-Dtest=ExecutionRequestBodyIntegrationTest,AuthenticatedExecutionApiIntegrationTest,JwtSecurityConfigurationTest,CustomRoleExecutionIntegrationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,ApiExceptionHandlerTest" test`
- Related suite: `.\mvnw.cmd -B -ntp "-Dtest=AuthenticatedExecutionApiIntegrationTest,CustomRoleExecutionIntegrationTest,ExecutionRequestBodyIntegrationTest,JwtSecurityConfigurationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,ConsoleSecurityIntegrationTest,ManagementEndpointIntegrationTest,SidecarDefaultsTest,SidecarExecutionPropertiesTest,SupportedLoomspanApiArchitectureTest" test`
- Full safe suite: `.\mvnw.cmd -B -ntp verify`
- Diff hygiene: `git diff --check -- README.md src/main src/test ai/thoughts/plans`

If Step 4 decides a proposed new class is unnecessary because an equivalent assertion fits an existing class, update the command to name the actual tests rather than passing a nonexistent `-Dtest` selector. All executed commands and outcomes must be reported exactly.

## Optional Developer Checks

- None for this ticket. SC5 separately owns image startup, packaged listener/client ordering, the snapshot-to-release dependency sequence, hosted CI, and release verification.

## Exit Criteria

- [x] No historical red test is claimed for code that predates this plan; the first new characterization test passes or exposes and then guards the intended ticket-scoped correction.
- [x] New and updated focused tests pass after any necessary correction.
- [x] The broadest safe relevant repository test suite, `.\mvnw.cmd -B -ntp verify`, passes against the locally installed beta.4 snapshot.
- [x] Every ticket acceptance criterion maps to executable Sidecar evidence or an explicitly justified same-path invariant; no criterion is inferred only from framework tests.
- [x] Raw request limits, decoder modes, custom roles, ownership, queued/nested identity, all capacity authorities, expiry, diagnostics, shutdown, Console/management coexistence, and the supported framework boundary are covered.
- [x] Result/event preservation and task/security cleanup tests are deterministic and do not rely on garbage collection, arbitrary race loops, or unbounded sleeps.
- [x] Routine automated tests use only local deterministic fixtures and perform no live, production, destructive, or externally authenticated operation.
- [x] README and shipped defaults match verified behavior and continue to identify SC4/SC5 exclusions accurately.
- [x] The unrelated untracked `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md` remains unchanged.
- [x] Optional SC5 checks remain nonblocking and are not represented as performed.
