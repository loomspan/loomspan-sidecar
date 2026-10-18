# Blocking SDK client Testing Plan

## Change Summary

Add the blocking core API, pooled no-replay HTTP transport, bounded waiting, safe
credentials/errors, equivalent Boot property wiring and a real isolated Java consumer.
Requirements are the ticket and accompanying implementation plan with the same stem.
This is Step 3 of the approved Full 5-Step Pipeline. Planned coverage below is now backed by the Step 4 execution evidence appended at the end.
Follow the [design lens](../design-lens.md); replace obsolete development contracts
directly, without compatibility shims. No data reset is required.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| HTTP outcomes | Hidden resend or fabricated known acceptance | Server-side request counters, malformed/lost acceptance fixtures |
| Wire envelope | Wrong ID, schema-as-object, inferred result status | Typed decoding negative tests and exact text comparisons |
| Wait/lifecycle | Slow drip exceeds total budget; lost interrupt; leaked pool | Latches/socket fixture, monotonic elapsed bound, follow-up calls and close |
| Security/config | Path escape, leaked JWT, weakened TLS | Zero-send validation, token/payload sentinels, real TLS handshake tests |
| Starter | Divergent defaults/SSL or resource leak | Boot context runner plus real loopback requests and bundle trust |
| Composition | Mock proof represented as SDK integration; provider or retained-data access | Real plain Java process against actual Sidecar, isolated network and report hashes/counts |

## Existing Coverage and Environment Constraints

SDK currently has no Java/test sources. Existing core boundary script creates
disposable Maven reactors and must inject into the new dependencies block.
Server `AuthenticatedExecutionApiIntegrationTest` is a wire reference, not SDK proof.
Use JDK HttpServer/raw ServerSocket for loopback failures and HttpsServer for TLS,
JUnit Jupiter with SDK reactor Surefire, Boot ApplicationContextRunner for starter.
TLS material is generated only in test temporary directories with JDK keytool or
test-scoped certificate tooling; no external server, private repo key or trust bypass.

External pytest currently covers SDK builds, runtime ordering, privacy, cleanup and
zero-model fixture smoke. Extend it; retain `standard`/`hardened` no-scenarios failure.
Host Java 21 must be available through JAVA_HOME (runner may resolve its executable
directly); Maven and Docker are required only for composed execution. External Python
environment includes pytest/httpx/PyYAML/cryptography. Public artifact/image downloads
may occur. No provider calls, business runtime startup, operational database reset,
live fixture forwarding or maintained reference-pack modification is permitted.

## Failing Test First
- Name: `SidecarClientTest.discoversSubmitsAndAwaitsOpaqueResult`.
- Type: focused loopback HTTP contract test.
- Location: `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTest.java`.
- Arrange/Act/Assert: serve catalog/skill strings, POST 202 with UUID and untrusted Location, then QUEUED/RUNNING/COMPLETED snapshots; call all operations and assert metadata, early acceptance ID, request paths/auth, final opaque Unicode string and exact per-path counts.
- Expected pre-fix failure: compile fails because SidecarClient/DTOs do not exist. Add test/build test dependencies first and record that intended missing-API red result; do not substitute an unrelated Maven/dependency failure.

## Tests to Add or Update

### 1. `discoversSubmitsAndAwaitsOpaqueResult`
- Type/location: focused HTTP, `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTest.java`.
- Proves: list/get preserve name/description/kind and string schemas including null outputSchema; submit returns ID immediately; status/await preserve timestamps/config ID/events/details and terminal status.
- Fixture: base path `/prefix`, one sequence of queued/running/completed envelopes; optional absent events and null pre-handoff config ID; nested event details with Unicode and arrays.
- Isolation: loopback only, one server-side attempt counter per endpoint.
- Edges: statuses return FAILED normally; await terminal first read sends once and sleeps zero times.

### 2. `preservesOpaqueResultsAndPrimaryFailure`
- Type/location: parameterized HTTP, same file.
- Proves: exact Java string equality for empty, whitespace, plain text, multiline Unicode/supplementary characters, `null`-looking text, valid JSON-looking and malformed JSON-looking text; actual null result remains null where permitted.
- Inputs: COMPLETED with each string; FAILED with primary kind/message/issues and available events; no business-schema parsing.
- Edges: FAILED never becomes COMPLETED because result looks successful; COMPLETED never becomes FAILED because text is malformed JSON.

### 3. `rejectsLocalFailuresWithoutSendingAndEncodesOneSegment`
- Type/location: parameterized HTTP, same file.
- Proves: null input/credential/name/ID/options and invalid duration/limit fail locally; no validation compiles business schemas; nested JSON object accepted. ObjectNode API cannot accept arrays/scalars at compile time; test null and non-object nodes if implementation accepts a wider node helper, without adding a wider public overload only for tests.
- Inputs: blank/controls, `/`, `\\`, `%2f`, `%252f`, `.`/`..` unsafe names; accepted Unicode/space/`?`/`#` names encoded once. Base URL with and without trailing slash/prefix.
- Assertions: invalid inputs leave count zero; safe path has exactly one encoded segment and preserves configured prefix; auth header exactly unchanged.

### 4. `distinguishesHttpProtocolAndUnknownAcceptanceWithoutReplay`
- Type/location: local HTTP/raw socket, same file.
- Proves every outcomes row: local no-send; valid 202; HTTP failure status/problem/extensions; lost/unusable acceptance; read error carrying requested ID/last snapshot; wait termination; terminal FAILED.
- Fixtures: HTTP 400/401/403/404/429/503 with RFC-style problem and issues; wrong 2xx status, empty/malformed/non-object/truncated/missing-ID/bad-UUID 202; socket close after consuming POST; status socket loss; malformed status JSON; 301/302/307/308 and WWW-Authenticate challenge.
- Assertions: known HTTP errors keep status and structured problem without asserting no work; malformed acceptance reports unknown and no guessed ID (ignore arbitrary Location/header IDs); oversized acceptance remains protocol failure; read protocol errors preserve requested ID. All requests counted once; redirect target and Location destination counters remain zero; 401 never causes a second authenticated or unauthenticated request.
- Privacy: fixed exception messages/toString/stack traces do not include sent business/JWT sentinel or raw response/parser exception. Explicit structured error data preserves server problem semantics but exact token echoes are removed.

### 5. `validatesExecutionEnvelopeAndKeepsRequestedId`
- Type/location: parameterized HTTP, same file.
- Inputs: mismatched valid UUID, missing ID/name/status/createdAt, incorrect scalar/object types, invalid UUID/time/status, non-string result, FAILED without failure, terminal without completion time; unknown extra fields accepted.
- Assertions: every invalid status envelope produces protocol failure with original requested ID. During await retain the previous valid snapshot even if the next body's ID differs. Never silently follow the supplied ID or infer an ID from a partial payload. Early QUEUED/RUNNING and omitted events remain valid.

### 6. `awaitHonorsOneTotalDeadlineAndStopsOnFirstReadFailure`
- Type/location: deterministic socket/latch HTTP, same file.
- Inputs: queued forever, slow response headers, stalled body, slow drip whose bytes arrive faster than response inactivity timeout; read HTTP 503/protocol error following one valid RUNNING; interval longer than remaining time.
- Assertions: e.g. 250ms total deadline completes within 1s measured monotonically despite configured 5s response timeout and slow drip; no additional poll after deadline/failure. Last valid snapshot and requested ID retained. Use latch to ensure the stalled stage is reached, avoid thread scheduling assumptions and long sleeps.
- Interruption: test interrupted poll sleep and in-flight network read on a dedicated thread; interrupt thread, join within bounded 1s tolerance, assert wait termination, interrupt flag restored, ID/last snapshot retained and no resubmission. Clear test thread flags at teardown only.

### 7. `boundsResponsesReleasesResourcesAndClosesIdempotently`
- Type/location: HTTP/raw socket, same file.
- Inputs: limit exactly reached and exceeded by one byte; Content-Length oversize, chunked oversize, malformed/truncated/error bodies; repeat enough sequential failures to expose pool starvation.
- Assertions: limit success and protocol overflow; bounded completion on overflow without draining endless body; follow-up valid request succeeds after each failure; response connections are released or discarded; close twice succeeds and every later operation fails locally/count zero. No timeout/close implies server cancellation.
- Configuration: assert 2s/10s/8MiB and WaitOptions 60s/250ms defaults through package-visible configuration or behavior, without adding diagnostic public getters solely for tests. Reject zero/negative/overflow durations and size, missing/relative/unsupported URL, absent host and URL userinfo/query/fragment.

### 8. `verifiesTlsTrustAndHostname`
- Type/location: real loopback TLS, `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTlsTest.java`.
- Fixture: temporary CA/server with localhost SAN plus separate mismatching-name certificate; optional SSLContext with that CA only.
- Assertions: trusted hostname succeeds; same server with default untrusted trust fails; trusted wrong-host fails. No trust-all manager, insecure flag or custom permissive verifier. Default TLS validation enabled even with custom SSLContext.

### 9. `credentialAndErrorsDoNotExposeTokens`
- Type/location: focused serialization/error/log capture in core test file.
- Inputs: unique token and input sentinels, blank/header-injection credential, server failure whose body echoes token.
- Assertions: ordinary getters/introspection expose no token; Jackson serialization either safe redacted/empty output or explicit safe refusal, never token; default toString, exceptions including causes/stack traces and captured default logging omit token/input. Non-Serializable holder cannot Java-serialize bytes. Assert unchanged bearer header on successful request, no client JWT validation. Do not enable third-party wire logging as a default.

### 10. `propertiesProduceEquivalentClientAndCloseWithContext`
- Type/location: Boot context + HTTP/TLS, `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SidecarClientAutoConfigurationTest.java`.
- Inputs: all five proposed SDK properties, core defaults, invalid/missing base URL, invalid durations, known/unknown Boot SSL bundle and a user-supplied client bean.
- Assertions: same request and overflow/timeout behavior as direct builder, conditional override preserved, autoconfiguration discovered, custom trust works while wrong hostname fails, missing configured bundle fails, context destruction closes created client. No core Boot runtime dependency appears.

### 11. `BuildBoundaryTest` regression
- Type/location: existing real disposable Maven reactors, `sdks/java/tests/test_build_boundary.py`.
- Assertions: control passes with new dependencies; eight direct/transitive Framework/Sidecar injection combinations fail due to the intended dependency boundary rather than duplicate dependencies XML or unrelated missing artifact. Core Boot prohibition remains active.

### 12. External runner/proxy regression and real Java flow
- Type/location: provider-free Python in `C:/opendev/code/loomspan-sidecar-test-suite/tests/test_sdk_runner.py`; driver `sdk/driver/src/main/java/ai/loomspan/sdk/example/BlockingClientDriver.java`.
- Local fixture tests: proxy forwards once to fixed local backend, counts one incoming and one forwarded submission, consumes backend response then drops/truncates frontend response; never redirects/retries. Effect endpoint increments independently and fixed failure returns 503. No auth/input sentinels logged.
- Runner doubles: verify real checkout/SDK/driver build command and ordered startup zero-model check, then feature driver, final counters, shutdown/cleanup. Report includes separate scenario outcomes, source/JAR/SDK hashes, and both counters; failure/interrupt/cleanup retain safe fixed diagnostics. Standard/hardened remain explicit no-scenario failures. Child environment and mounts exclude provider secrets/business state. Temporary token is driver-only, no private key serialized, no token on process command line/report.
- Composed actual run: Java process loads only SDK core plus its transitives; connects to real packaged Sidecar; list/get and submit/await success exact fixed model string; real REST failure primary SKILL_FAILURE/HTTP-503 text; response-loss proxy yields acceptance unknown with no known ID. Runner waits boundedly for synthetic effect and asserts submissionAttempts=1, forwardAttempts=1, syntheticEffects=1 and unchanged counts after driver termination; no resubmission or exactly-once production promise. Foundation startup count remains zero before calls; model success count is checked separately afterward. Runtime project always cleaned.

## Safe Verification Commands

From `C:/opendev/code/loomspan-sidecar`:
- Focused: `mvn -B -ntp -f sdks/java/pom.xml -pl core -am test`.
- Related suite: `mvn -B -ntp -f sdks/java/pom.xml clean install`.
- Boundary: `python sdks/java/tests/test_build_boundary.py`.
- Root regression: `mvn -B -ntp clean verify` (also executed by composed lane; avoid redundant repetition after an unchanged passing run).

From `C:/opendev/code/loomspan-sidecar-test-suite`:
- Focused: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`.
- Static: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- Static: `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- Full safe suite: `.venv/Scripts/python.exe -m pytest tests -q` (offline regression, no business runtime commands).
- Required composition: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`.

The composed runner owns driver Maven packaging/classpath copying; do not invoke
an independent manually assembled consumer against an arbitrary exported JAR.
Record exact executed commands/counts/report path and failures in implementation
evidence. A missing Docker daemon is a failed/unrun composed gate, not focused-test
evidence of real integration. Do not run paid or business runtime smoke/replay tools.

## Optional Developer Checks
- None. Inspecting the generated safe report is useful but does not replace assertions.

## Exit Criteria
- [x] First core acceptance test records the intended missing-API red result.
- [x] Focused core/starter tests and boundary control/negative cases pass.
- [x] Every outcomes row and acceptance criterion maps to the executable cases above.
- [x] Exact result/failure/event preservation, no replay, URL/TLS/credential privacy, deadline and response resource edge cases pass.
- [x] Full safe relevant root/SDK/Python suites and static checks pass.
- [x] Actual local composed report passes real COMPLETED, FAILED and response-loss flows, with independent one-attempt/one-effect counts and successful cleanup.
- [x] Tests perform no provider or unintended live/destructive operation; existing reference packs/data remain untouched.
- [x] SDK/lane docs match API/defaults/error semantics and accurately limit recipe/effect claims.
- [x] Optional checks are not represented as executed evidence; all unrun gates and residual risks are explicit.

## Step 4 execution evidence (2026-10-18)

The intended initial red command, `mvn -B -ntp -f sdks/java/pom.xml -pl core -am test`,
failed at testCompile because SidecarClient, CredentialSnapshot, ExecutionStatus and
WaitOptions did not yet exist. After implementation it passed. The final composed
runner's SDK clean install reran final core/starter sources: 11 core tests and 6 starter
tests passed. Protocol/deadline/resource cases are in `SidecarClientEdgeTest` rather
than adding all cases to the initial test file; this is a test-organization adaptation.

The build-boundary control/eight Framework/Sidecar variants passed through
`python sdks/java/tests/test_build_boundary.py`; two additional core Boot variants
passed via `python sdks/java/tests/test_build_boundary.py BuildBoundaryTest.test_core_rejects_direct_and_transitive_boot_runtime`.
External focused pytest passed 36 tests; full safe pytest passed 146 tests; both
specified ruff commands passed. Root clean verify passed 256 tests with zero
failures/errors and 3 existing configured-production-browser skips. These skips are
outside the local SDK lane and are not represented as SDK evidence.

The actual required local SDK lane passed all fourteen build/runtime stages, all five
driver outcome assertions and independent counts (one submission, one forward, one
synthetic effect, one model request after startup zero), with graceful shutdown and
cleanup. Report: `C:/opendev/code/loomspan-sidecar-test-suite/evidence/sdk/20261018T170928Z-ce1a23cab707/report.json`.
See `ai/thoughts/validation/2026-10-18-pr-8-1-2-blocking-sdk-client.md` for exact
commands, executable method mapping and artifact hashes. No required acceptance gate
is unrun; standard/hardened recipes and provider access remain outside this ticket.
