# Authenticated Execution API Testing Plan

## Change Summary

The change introduces the Sidecar's first application HTTP surface, verifies JWTs from three key-source modes, maps trusted identity/roles into framework validation and asynchronous invocation, and adds a bounded in-memory execution lifecycle with owner-only polling, selected diagnostics, TTL, and shutdown gates. Verification must cover focused configuration/state logic and the real application boundary; framework tests are useful design references but are not Sidecar evidence.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| HTTP/body handling | Oversize or invalid bodies reach Jackson/framework/admission; equality is rejected; wrong error representation | random-port raw-body tests with validation/handler counters and problem media/header assertions |
| JWT verification | discovery/JWKS/public-key modes apply inconsistent issuer, audience, expiration, or required-claim rules | context tests for each decoder mode and HTTP token matrix |
| Roles and ownership | custom prefixes disagree with framework RBAC; foreign callers learn record existence | validation/invocation role tests and issuer/subject owner matrix returning indistinguishable `404` |
| Worker security context | request identity disappears after queueing, expired admitted token is revalidated, or identity leaks to reused worker | blocking queue plus test `RestSkillHandler` token capture and sequential different-caller executions |
| Admission/accounting | races exceed count/byte/retained bounds or double-release reservations | latch/barrier-driven coordinator concurrency tests and state invariants after every transition |
| Terminal publication | polling sees terminal status before result/failure/events; observer absence blocks completion | concurrent polling and callback/no-callback diagnostic cases |
| Retention/reference lifetime | reads extend TTL, sweeps require polling, or completed tasks retain input/JWT | mutable clock tests, admission-triggered sweep, and package-visible task/reference-state assertions |
| Shutdown/lifecycle | a dequeue race starts invocation after close; wrong context closes gates; listener waits or interrupts active work | real context events, readiness observation, asynchronous multicaster, latches around handoff/invoke, prompt-return bound |
| Security coexistence | JWT chain captures Console routes or exposes Actuator/application routes; errors are cacheable | Console API-key, separate management-port, deny-by-default, and no-store response tests |
| Framework boundary | fixtures import internal/autoconfigure types or declare Java skills | existing whole-package ArchUnit rules in full `verify` |

## Existing Coverage and Environment Constraints

The repository currently uses JUnit 5, AssertJ, Spring Boot test support, Java's loopback `HttpClient`, and ArchUnit under Maven Surefire. `MountedSkillRegistrationIntegrationTest` demonstrates temporary mounted manifests and local model configuration; `ManagementEndpointIntegrationTest` demonstrates random application/management ports; `SupportedLoomspanApiArchitectureTest` imports all `ai.loomspan.sidecar` production and test classes.

There is no authenticated web fixture today. Add one shared Sidecar application fixture rather than duplicating startup/JWT/model plumbing. It will generate RSA keys/tokens locally, host discovery/JWKS and deterministic OpenAI-compatible responses on loopback, and register a test-only public `RestSkillHandler`. Tests must not contact a production issuer, model, callback, or provider and must not host Java `@SkillMethod` skills. SC4 callback-host verification and SC5 image/package checks remain later configured evidence, not missing routine tests here.

Inject `Clock` into expiry decisions and use latches/barriers around test handler invocation and task handoff. Avoid sleeps as assertions; bounded awaits are only failure timeouts. Reset fixture handler state and security contexts between cases so parallel or reused-worker behavior is deterministic.

## Failing Test First

- Name: `acceptsAuthorizedExecutionAndPollsExactResult`
- Type: random-port Spring Boot application integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Arrange/Act/Assert: start the common fixture with one role-protected REST manifest and valid local JWT; POST a valid JSON object; assert `202`, execution id, `Location`, and `no-store`; poll the owner URL until `COMPLETED`; assert the test handler's exact string and no events in default `NEVER` mode.
- Expected pre-fix failure: the application has no `/v1/skills/{name}/executions` mapping or resource-server configuration, so the POST cannot return the accepted representation.

Implementation receipt: the pre-fix red run was not captured before the route
and resource-server implementation were added, so that historical process check
remains unchecked. The final focused and full suites exercise the implemented
behavior; this omission does not substitute framework evidence or weaken a
ticket acceptance assertion.

## Tests to Add or Update

### 1. `bindsExecutionDefaultsAndRejectsInvalidLimits`

- Type: configuration-properties/context test
- Location: `src/test/java/ai/loomspan/sidecar/config/SidecarExecutionPropertiesTest.java`
- Proves: all seven defaults bind; positive counts/sizes/TTL are required; diagnostics accepts only `NEVER|ONERROR|ALWAYS`.
- Inputs/fixture: `ApplicationContextRunner` property overrides for zero/negative/malformed values and each enum.
- Doubles or boundary isolation: no web server or framework invocation.
- Edge cases: one-byte/count minimum and zero TTL.

### 2. `configuresDiscoveryJwksAndPublicKeyWithCommonValidation`

- Type: security configuration context test plus focused decoder authentication
- Location: `src/test/java/ai/loomspan/sidecar/security/JwtSecurityConfigurationTest.java`
- Proves: discovery, explicit `jwk-set-uri`, and RSA `public-key-location` construct a decoder; every mode verifies signature, issuer, audience, expiry, nonblank `iss`/`sub`, and `exp`; configured skew works; invalid/ambiguous settings fail startup.
- Inputs/fixture: generated RSA keys, temporary PEM, loopback discovery/JWKS server, tokens differing one claim at a time.
- Doubles or boundary isolation: all issuer metadata and keys are local; no external IdP.
- Edge cases: neither explicit key means discovery, both explicit keys fail, missing issuer/audience fail, wrong signing key, blank claims, missing expiration, just-inside/just-outside skew.

### 3. `mapsCustomRoleClaimAndPrefixConsistently`

- Type: application security/framework integration test
- Location: `src/test/java/ai/loomspan/sidecar/security/JwtSecurityConfigurationTest.java`
- Proves: roles originate only from the configured JWT claim; converter prefix and `GrantedAuthorityDefaults` agree at both `SkillTemplate.validate` and worker invocation.
- Inputs/fixture: role-protected REST manifest, tokens with default and custom claims/prefixes.
- Doubles or boundary isolation: test-only `RestSkillHandler` records invocation; no production handler.
- Edge cases: missing claim, empty roles, wrong role, custom prefix, no Sidecar assignment table.

### 4. `securesCatalogAndReturnsCompleteDescriptors`

- Type: random-port HTTP integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: both catalog routes require JWT, expose name/description/kind/input schema for all skills including role-restricted ones, and return problem `404` for unknown names.
- Inputs/fixture: common fixture with unrestricted and role-protected REST/YAML manifests.
- Doubles or boundary isolation: mounted test resources and local model stub.
- Edge cases: missing/invalid token, authenticated caller without the invocation role, exact name lookup.

### 5. `rejectsInvalidBodiesAndEnforcesRawByteLimitBeforeParsing`

- Type: raw HTTP integration test
- Location: `src/test/java/ai/loomspan/sidecar/api/ExecutionRequestBodyIntegrationTest.java`
- Proves: missing, empty, JSON `null`, malformed, scalar, and array bodies return `400`; raw UTF-8 input equal to the limit proceeds; one byte over returns `413` before parsing, validation, record creation, or handler execution.
- Inputs/fixture: Java `HttpClient` requests with controlled byte arrays and validation/handler counters.
- Doubles or boundary isolation: common local application fixture.
- Edge cases: multibyte Unicode split at the boundary, syntactically malformed oversize content, `{}` continuing into normal framework validation.

### 6. `mapsPreAdmissionFailuresWithoutRecords`

- Type: HTTP/framework integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: unknown skill is `404`, structured validation is problem `400` with issues, missing role is `403`, and none creates an execution or capacity reservation.
- Inputs/fixture: manifests with required inputs and `rbac_roles`; valid tokens with/without roles.
- Doubles or boundary isolation: coordinator exposes package-private invariant snapshot to tests rather than production diagnostics.
- Edge cases: empty object validation and repeat valid admission immediately after each rejection.

### 7. `acceptsAuthorizedExecutionAndPollsExactResult`

- Type: random-port application integration test (red test)
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: POST returns promptly with `202`, id, and `Location`; polling transitions to `COMPLETED` with unchanged result; disconnect does not cancel accepted work.
- Inputs/fixture: blocking public REST test handler and exact Unicode/multiline result.
- Doubles or boundary isolation: local handler controlled by latches.
- Edge cases: close response body/client before releasing handler, large result with no Sidecar truncation.

### 8. `returnsClassifiedOwnedFailuresWithHttp200`

- Type: application integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: invocation-time validation, access denial, and skill failure become `FAILED` records with the three kinds; polling a failed owned record returns `200`; primary facade message/issues survive and stack traces do not.
- Inputs/fixture: test handler and manifests that fail at distinct framework stages, including nested mixed failure.
- Doubles or boundary isolation: deterministic public framework paths only.
- Edge cases: primary classification disagrees with an event's child failure, `SkillException` exact message, no arbitrary cause unwrapping.

### 9. `enforcesOwnerByIssuerAndSubject`

- Type: HTTP integration test plus focused coordinator owner test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java` and `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: renewed JWT with same issuer/subject reads the record; either differing field returns the same `404` as unknown/expired.
- Inputs/fixture: locally signed renewed tokens varying subject/token id/expiration for HTTP; direct `ExecutionOwner` pairs varying issuer and subject at the coordinator boundary, because one running Sidecar trusts one configured issuer and a foreign issuer is rejected by JWT validation before HTTP ownership lookup.
- Doubles or boundary isolation: local issuers only.
- Edge cases: same subject/different issuer and same issuer/different subject; owner record contains no token.

### 10. `propagatesQueuedAndNestedJwtWithoutCrossRequestLeakage`

- Type: application/framework integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: a saturated request waits, then invocation and nested REST handler see the original `JwtAuthenticationToken`, authorities, and token value; post-admission expiry does not block local execution; a reused worker sees only the next caller.
- Inputs/fixture: max concurrency one, blocking first task, short-lived second token, YAML planner/model responses invoking the REST leaf, and a sequence of callers.
- Doubles or boundary isolation: local model stub and test handler capture `SecurityContextHolder`.
- Edge cases: token expires while queued, wrong role remains denied at invocation, context is empty after task and replaced for next task.

### 11. `boundsConcurrentQueueCountBytesAndRetainedRecords`

- Type: focused coordinator concurrency test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: running facade calls never exceed `max-concurrent`; saturation queues rather than rejects; count, canonical UTF-8 byte total, and retained capacity independently reject excess with no orphan records.
- Inputs/fixture: fake/blocking `SkillTemplate`, barriers for simultaneous admissions, measured ASCII and multibyte maps.
- Doubles or boundary isolation: inject public-interface fake; no Spring web context.
- Edge cases: exact byte equality accepted, one byte excess rejected, simultaneous last-slot submissions, direct dispatch excluded from queued budget.

### 12. `releasesReservationsExactlyOnceAcrossHandoffRemovalAndRejection`

- Type: focused coordinator state-machine test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: queue count/bytes release on worker handoff rather than completion, removal/dispatch failure rolls back record and reservations, and worker capacity remains occupied until worker exit.
- Inputs/fixture: controllable executor/queue hooks and task latches.
- Doubles or boundary isolation: package-private coordinator invariant snapshot.
- Edge cases: rejection after provisional record creation, concurrent close/dequeue, repeated cleanup calls cannot underflow.

### 13. `expiresWholeTerminalRecordsWithoutReadExtension`

- Type: focused coordinator retention test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: TTL begins at terminal publication; reads do not extend it; admission sweep reclaims expired capacity without polling; active records do not expire.
- Inputs/fixture: mutable injected clock and max-retained one.
- Doubles or boundary isolation: no wall-clock sleeps.
- Edge cases: equality at expiry instant, result/failure/events disappear together, unknown/expired both absent.

### 14. `publishesTerminalOutcomeAndSelectedEventsAtomically`

- Type: coordinator/application integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java`
- Proves: `NEVER`, `ONERROR`, and `ALWAYS` retain exactly the selected available observer events; polling never sees terminal status without its matching outcome/events; callback absence does not wait; large histories are intact.
- Inputs/fixture: public execution views produced by deterministic REST/YAML scenarios plus a pre-session/no-callback failure.
- Doubles or boundary isolation: no Console/internal trace API.
- Edge cases: success/failure matrix, nested mixed child failures, no callback, event order/details/frame/route fields, expiry with outcome.

### 15. `releasesTaskInputAndAuthenticationOnExitOrDiscard`

- Type: focused coordinator lifecycle test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: completed tasks and shutdown-discarded queued tasks null their input/authentication references while the record retains only owner/outcome metadata and owner reads still work.
- Inputs/fixture: package-visible task state and uniquely identifiable map/JWT values.
- Doubles or boundary isolation: deterministic state assertions avoid unreliable GC tests.
- Edge cases: successful, failed, executor-rejected, and queued-discard paths.

### 16. `closesAdmissionDispatchAndReadinessPromptly`

- Type: Spring application shutdown integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java`
- Proves: owning-context close immediately publishes refusing traffic, makes new POST return `503`, prevents a dequeued task from entering `invoke`, discards queue/accounting/references, and returns without waiting for active work.
- Inputs/fixture: latches before invoke/in active handler, readiness listener/probe, configured asynchronous application-event multicaster.
- Doubles or boundary isolation: public `SkillTemplate`/test handler behavior and standard Spring events only.
- Edge cases: close exactly during dequeue handoff, queued record becomes absent, active task completes after listener return, no executor `close()` wait.

### 17. `ignoresManagementContextCloseAndPreservesManagementExposure`

- Type: multi-context integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java` and `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java`
- Proves: a management child-context close does not close application gates; health/readiness stay on the separate port and unrelated endpoints remain unavailable.
- Inputs/fixture: normal random application and management ports.
- Doubles or boundary isolation: actual contexts and loopback HTTP.
- Edge cases: owning context compared by identity, application port Actuator remains unavailable.

### 18. `coexistsWithConsoleApiKeyAndAppliesNoStoreEverywhere`

- Type: security integration test
- Location: `src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java`
- Proves: enabling observability leaves its reserved path to the framework API-key filter while `/v1/**` remains JWT-only; API and error responses are stateless and `no-store`.
- Inputs/fixture: opt-in framework observability with local key, valid/invalid JWTs/API keys.
- Doubles or boundary isolation: framework's supported configuration and public HTTP surface; no internal filter imports.
- Edge cases: JWT on Console route is not a substitute for API key, API key on `/v1` is not authentication, 401/403/404/429/503 headers.

### 19. `sidecarUsesOnlySupportedFrameworkSurface`

- Type: architecture regression test
- Location: `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`
- Proves: all new production and test classes avoid internal/autoconfigure packages and no fixture declares a Java skill.
- Inputs/fixture: existing full package import.
- Doubles or boundary isolation: none.
- Edge cases: test-support packages are included.

### 20. `documentsBoundConfigurationAndDeliveryBoundary`

- Type: documentation/configuration consistency assertion plus review
- Location: `src/test/java/ai/loomspan/sidecar/config/SidecarDefaultsTest.java` and `README.md`
- Proves: documented property names/defaults match `application.yml` and bound properties; docs cover routes, polling, security/mTLS, token lifetime, diagnostics/data, memory/retention, restart/shutdown, Console, and SC4/SC5 exclusions.
- Inputs/fixture: production YAML property source and README snippets where stable assertions add value.
- Doubles or boundary isolation: no external services.
- Edge cases: do not claim production callback handler, image, packaged lifecycle proof, or released framework dependency.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -B -ntp -Dtest=SidecarExecutionPropertiesTest,JwtSecurityConfigurationTest,ExecutionCoordinatorTest test`
- Related suite: `.\mvnw.cmd -B -ntp -Dtest=AuthenticatedExecutionApiIntegrationTest,ExecutionRequestBodyIntegrationTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,ConsoleSecurityIntegrationTest,ManagementEndpointIntegrationTest test`
- Full safe suite: `.\mvnw.cmd -B -ntp verify`

All commands use the locally installed `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`. They must use only loopback fixtures and require no account, credential, container runtime, or network service.

## Optional Developer Checks

- None for this ticket. SC5 separately owns image startup, packaged listener-order/client lifetime, quick-start commands, snapshot-to-release dependency proof, hosted CI, and release verification.

## Exit Criteria

- [ ] The planned red test fails for the intended missing-route reason before implementation.
- [x] New and updated focused tests pass after implementation.
- [x] The full safe Maven `verify` suite passes against the locally installed beta.4 snapshot.
- [x] Every ticket acceptance criterion maps to executable Sidecar evidence rather than framework test inference.
- [x] Routine tests perform no live, destructive, or externally authenticated operations.
- [x] Security matrices cover every decoder mode, claims, roles, ownership, context propagation, and Console separation.
- [x] Deterministic concurrency tests cover every admission boundary, equality/excess, rollback/release path, and shutdown/dequeue race without timing-only assertions.
- [x] Terminal publication, diagnostics modes, expiry, exact untruncated result/events, and reference release are covered.
- [x] The architecture guard still covers production and test fixtures and passes.
- [x] README statements match the implemented properties and preserve the SC4/SC5 boundary.
- [x] Any SC5-only observation remains explicitly nonblocking and is not represented as performed.
