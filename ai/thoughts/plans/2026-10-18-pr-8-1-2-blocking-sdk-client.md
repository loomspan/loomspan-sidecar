# Blocking SDK client Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-2-blocking-sdk-client.md`
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-2-blocking-sdk-client.md`
- Outcome: one plain Java blocking client, equivalent optional Boot wiring, focused contract tests and real provider-free execution/no-replay evidence in the neighboring suite.
- Execution: approved Full 5-Step Pipeline; implementation and verification are complete for Step 4; fresh independent review remains.

**Replace superseded development contracts directly; no compatibility shims,
legacy adapters or parallel legacy APIs.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No development-data
reset is required. Existing deployed or business-lane data must not be deleted.

## Current State

`sdks/java/pom.xml` owns an independent Java 21 reactor. `core/pom.xml` has no
dependencies or sources; the starter is also POM-only and imports Boot 4.1.0.
`tests/test_build_boundary.py::BuildBoundaryTest.build` currently appends a new
core dependencies element and must change when core acquires real dependencies.

`SkillCatalogController` serves `/v1/skills` and `/v1/skills/{name}`. Schemas are
JSON strings, not nested objects. `ExecutionController.execute` returns 202 with
body UUID and Location. `execution` returns an owner-scoped snapshot with no
startedAt field. `ExecutionRecord` permits a null configuration snapshot ID before
handoff. Events may be absent. `ExecutionFailureClassifier` preserves the primary
failure; a terminal failure still arrives through HTTP 200.

External paths below are relative to `C:/opendev/code/loomspan-sidecar-test-suite`.
`scripts/run_sdk.py` builds the actual checkout and SDK, uses temporary configuration
and an internal Compose network, then checks startup/graceful shutdown and zero
model calls. `scripts/sdk_lane/fixture.py` already provides deterministic model
responses. `sdk/compose.yaml` publishes no ports. There is no SDK consumer or proxy.
Suite AGENTS.md and README/project-agreement/foundation/runtime/sdk-lane were read;
the separate beta.9 business runtime and reference packs are outside this work.

## Desired End State

Callers discover metadata, submit an object and immediately receive its ID, read
the complete public execution envelope, and wait with a finite total deadline.
Terminal result text passes through exactly; status alone defines success/failure.
Each operation sends each request once. Errors distinguish known HTTP responses,
invalid protocol, unknown submission acceptance and local wait termination.
The external plain Java driver proves real completion, primary failure and a
forwarded submission whose response is lost, with independent attempt/effect counts.
The acceptance table below maps every ticket criterion.

## Scope
### In scope
- SDK core API/transport/DTOs/error semantics, starter properties and SSL bundle wiring.
- Focused HTTP/TLS/deadline/resource/security tests and build-boundary adaptation.
- Plain Java composed driver, temporary JWT fixture, one response-loss proxy and evidence.
- SDK README and external lane documentation.

### Out of scope
- JWT issuance/callback authentication, trust-contract replacement, business-schema compilation.
- Async APIs, cancellation, replay/deduplication ledgers, application executors/listeners.
- Standard/hardened recipe implementation, provider calls, business fixture changes.
- Framework changes/builds, publication, dependency release changes, commits or pushes.

## Active Project Guardrails

- Framework remains execution authority; core has no Framework, Sidecar or Boot dependency.
- Sidecar application/test references remain on `ai.loomspan.api`; preserve existing ArchUnit gate.
- Credentials remain outside payloads/diagnostics; no JWT verification or refresh in core.
- Preserve opaque result strings, primary failures and available public events.
- No-replay extends to redirects and authentication challenges; closing does not cancel server work.
- Use `loomspan-sdk.*` for SDK settings, standard Boot SSL bundles for trust material.
- Root Framework dependency stays beta.8; source lookups use matching framework tag.
- External SDK lane uses the current JWT configuration intentionally until future tickets replace it.

## Impact and Risk Analysis

The highest risks are accidental POST replay, an unjustified claim of rejection after
response loss, deadline overrun through slow streaming responses, URL path traversal,
credential disclosure through exceptions/wire logging and overly strict rejection of
legitimate early snapshots. Avoid raw request/response bodies and underlying parser or
HTTP exception causes in default diagnostics. Preserve structured server problem fields
as data while keeping exception messages/toString safe; never copy the input payload
into errors. Remove exact credential occurrences if a malicious server echoes the token
in error data. Do not sanitize result or execution failure business content.

Pool reuse requires explicit abort on malformed/oversized/stalled bodies rather than
unbounded draining. Timing tests need controlled local fixtures and generous fixed
tolerance, while still distinguishing a total deadline from inactivity timeouts.
The composed build needs Docker/Java/Maven and may download public dependencies, but
has no provider forwarding path, persistent business mounts or published host ports.

## Implementation Approach

Use package `ai.loomspan.sdk` for a public final `SidecarClient` implementing
`AutoCloseable`, with a builder; one implementation needs no interface/adapter layer.
Expose the five ticket operations and ordinary immutable DTOs. Use Apache HC5 5.6.1
and Jackson 3.1.4, matching versions in the installed Boot 4.1.0 BOM, without
importing Boot into core. Add JUnit Jupiter and Surefire to the independent reactor.

Public values: `CredentialSnapshot`, `SkillDescriptor`, `AcceptedExecution`,
`ExecutionSnapshot`, `ExecutionStatus`, `ExecutionFailure`, `ExecutionEvent`,
`WaitOptions`; issue values may be nested in failure. Keep schemas as text,
configurationSnapshotId nullable and events nullable/absent. Preserve event
timestamp/level/type/details/frameId/route through JSON trees for flexible nested
details. Do not import Framework DTOs. Explicit decoding validates JSON types,
UUIDs, timestamps, required names/status, expected execution ID, terminal completion
time and failure shape; tolerate unknown fields. QUEUED/RUNNING may have no result,
failure, completion time or configuration ID. COMPLETED permits null or any string
result; FAILED needs its primary failure. Never parse result text.

`CredentialSnapshot` is a final immutable non-record holder with private token,
no public token getter, no Serializable implementation, no Jackson-visible token
and a constant redacted toString. Creation rejects blank/control/whitespace token
transport values, without parsing/verifying JWTs. A package-private header accessor
is used only by transport. Do not install a default logger for requests/bodies.

Use a safe `SidecarException` base and distinguish local, HTTP, protocol,
acceptance-unknown and wait-termination subclasses. Known-ID errors carry requested
ID and optional last valid snapshot. HTTP errors retain status and parsed problem
fields, including extension issues, without assuming rejection. Malformed/empty/
truncated 202 or transport loss on submit is acceptance unknown without an invented
ID. A complete reliable acceptance UUID is the only submission ID source. Overflow
is always protocol failure (including on submission), as expressly required by the
ticket; never expose a partial acceptance ID. Read failures stop await immediately
with their category and its last snapshot. Deadline/interruption use wait termination.

Validate absolute HTTP(S) base URLs with a host and no userinfo/query/fragment.
Preserve an optional base path and append `/v1/...` with one separating slash.
Reject empty/blank skill names, controls, slash, backslash, `%`, `.` and `..`
locally. Encode every other accepted name using UTF-8 percent encoding as a single
segment (spaces are `%20`, never form `+`). No double decoding or URI normalization
of skill names. Validate positive, representable finite durations and byte limits.
Defaults: connect 2s, response 10s, response cap 8 MiB, wait 60s, interval 250ms.

Own an HC5 pool with retries, redirects, cookie storage and authentication handling
disabled. Do not use supplied Location or install credentials providers. Standard
hostname/certificate verification remains enabled with optional Java SSLContext.
Bound pool lease, connection and response waits. Use monotonic total-call deadlines
and an internal client-owned daemon scheduler to abort a request at its deadline,
including body reads/slow drip; no public executor API. Clamp each await request and
poll sleep to remaining total budget. Cancel scheduled aborts and close response
resources on every exit. Discard/abort incomplete responses rather than draining
unbounded input. Close pool/scheduler idempotently and reject subsequent operations.
Interruption during both sleep and network waits must restore the flag and retain ID
and last snapshot; never retry or resubmit.

## Phase 1: Core operations and focused contract proof

### Changes
- [x] `sdks/java/pom.xml`, `core/pom.xml` — pin HC5/Jackson/test dependencies and Surefire; preserve Enforcer rules and independent Java 21 build.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/SidecarClient.java` — builder, five operations, exact status/outcome handling, safe validation, pool lifecycle and absolute deadlines described above. Package-private transport/codec helpers may reside in this same package, without new public extension points.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/` — add named DTOs, credential holder and exception categories; preserve server strings/metadata/events and safe error diagnostics.
- [x] `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTest.java` — local HTTP and socket fixtures for operations, ID/envelope validation, opaque results, errors, no replay, limits, deadlines/interruption and close.
- [x] `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTlsTest.java` — temporary local TLS certificates, trusted success, untrusted and wrong-host rejection.
- [x] `sdks/java/tests/test_build_boundary.py::BuildBoundaryTest.build` — inject forbidden dependencies into the existing core dependencies block; continue proving direct/transitive bans in both modules.

### Automated verification
- [x] `mvn -B -ntp -f sdks/java/pom.xml -pl core -am test` — focused contract tests pass, with first missing-API red test recorded before implementation.
- [x] `mvn -B -ntp -f sdks/java/pom.xml clean install` — SDK reactor builds and installs with all focused tests and dependency enforcement.
- [x] `python sdks/java/tests/test_build_boundary.py` — clean control passes; all forbidden dependency variants fail specifically at Enforcer.

### Optional developer checks
- None.

## Phase 2: Boot wiring and user documentation

### Changes
- [x] `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientProperties.java` — bind `loomspan-sdk.base-url`, `connect-timeout`, `response-timeout`, `max-response-size`, `ssl-bundle`; use core defaults and builder validation. Wait defaults remain `WaitOptions` call options rather than redundant properties.
- [x] `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java` — conditional missing client bean, property binding and optional named `SslBundles` SSLContext. Missing configured bundle fails startup; bean destruction closes the client.
- [x] `sdks/java/spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — register autoconfiguration.
- [x] `sdks/java/spring-boot-starter/pom.xml` — add Boot test support only for tests; no unrelated security or callback machinery.
- [x] `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SidecarClientAutoConfigurationTest.java` — ApplicationContextRunner binding, override, invalid config, SSL bundle and real local HTTP behavior equivalent to builder.
- [x] `sdks/java/README.md` — replace empty-API statements with compilable Java and Boot usage, operations/outcomes, defaults/path joining/TLS, limits, wait interruption, no replay/no server cancellation and credential privacy caveat for explicit third-party wire logging.

### Automated verification
- [x] `mvn -B -ntp -f sdks/java/pom.xml clean install` — starter tests and same core behavior pass; context closes resources.

### Optional developer checks
- None.

## Phase 3: Real provider-free composed SDK evidence

### Changes
- [x] External `sdk/driver/pom.xml`, `sdk/driver/src/main/java/ai/loomspan/sdk/example/BlockingClientDriver.java` — plain Java 21 consumer with only SDK core as direct runtime dependency; package its runtime classpath using Maven dependency copying. Consume a temporary token file, never command-line token bytes, environment credentials or Framework types. Discover, submit/await model completion and compare the exact known fixture string; submit/await REST failure and check kind/message; submit the synthetic-effect skill through loss proxy and require acceptance unknown. Emit only fixed booleans/counts/outcomes, not credential/payload/stack traces.
- [x] External `scripts/run_sdk.py::write_config` — retain temporary RSA private key in memory long enough to sign a short-lived issuer/subject/audience/exp token using existing cryptography; serialize only public PEM to Sidecar configuration and temporary token to a separate driver-only read-only directory. Current `loomspan-sidecar.auth.jwt` properties stay unchanged in production. Add YAML model success and REST failure/effect declarations with documented `loomspan-sidecar.rest-routes.location`; auth none on synthetic fixture callbacks inside isolated network.
- [x] External `scripts/sdk_lane/fixture.py` — retain fixed model response; add `/fail` (fixed 503), `/effect` (increment syntheticEffects once per invocation), and independent loss-proxy submissionAttempts/forwardAttempts counters. Reference proxy on another local fixture port forwards once to fixed `sidecar:8080`, reads acceptance, then drops/truncates it; no generic forwarding target, retry, credential logging or provider access. Counters are synchronized and public only inside isolated test network.
- [x] External `sdk/compose.yaml` — add a Java 21 driver service using built driver/dependencies and driver-only token mount, on existing internal network; no exposed ports or business mounts. Build downloads occur on host before isolated execution. Proxy can run as a fixture listener without a new dependency/service framework.
- [x] External `scripts/run_sdk.py` — add driver build/execute stages and `blocking-client` scenario after readiness and before graceful stop. Check startup zero-model count before executing scenarios (preserves foundation-smoke meaning); collect final counts separately. Bounded wait for effect completion, then assert loss submissionAttempts=1, forwardAttempts=1 and syntheticEffects=1 (stronger than at most one) and no driver resubmission. Preserve primary failures/cleanup and fixed safe diagnostics; include scenario evidence in atomically written report.
- [x] External `tests/test_sdk_runner.py` — update scenario/stage/order/temporary fixture expectations; add local proxy integration counters/drop tests, driver command/dependency isolation, safe report failure cases and preserved foundation zero-model check before feature execution.
- [x] External `docs/sdk-lane.md`, `README.md` SDK lane description — explain real completed/failed/lost-response evidence, temporary current-JWT fixture, count meanings/limits and unchanged recipe coverage.

### Automated verification
- [x] In external checkout: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q` — fixture/proxy/runner focused checks pass without Docker/provider calls.
- [x] In external checkout: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` — formatting/static checks pass.
- [x] In external checkout: `.venv/Scripts/python.exe -m pytest tests -q` — safe offline suite regression passes without touching business runtime.
- [x] In external checkout: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar` — actual root clean verify, SDK clean install, driver packaging, isolated runtime and cleanup all pass; report contains real COMPLETED/FAILED/acceptance-unknown evidence and independent counters. This is a required composed gate, not an optional check. If prerequisites fail, report that gate NOT RUN/FAIL honestly and preserve its report for follow-up.

### Optional developer checks
- None. Standard and hardened remain explicitly unimplemented, not claimed verified.

## Test Strategy

Use JDK loopback HTTP/socket/TLS fixtures for SDK contracts, JUnit for API and
Boot context tests, existing disposable Maven boundary tests, and Python pytest
for runner/proxy safety. Reserve Docker for the one real local composition.
Step 3 specifies focused cases and measurable timing/count assertions.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Discovery/submit/status/await | SidecarClient operations/DTO codec | Core operation fixture sequence |
| Every outcomes-table row | Exception categories and terminal snapshot return | Local/HTTP/loss/read/wait/FAILED focused cases |
| Exact opaque results/status authority | ExecutionSnapshot result decoder | Empty/plain/malformed-looking/Unicode/null strings plus FAILED |
| Local shape/name rejection, path encoding, no Location/redirect | Validation and URI builder, disabled redirects | Zero-attempt bad inputs; encoded URI; second-destination count zero |
| Expected ID/envelope checks | Status codec/error ID | Mismatched ID/type/timestamp/status/terminal failure cases |
| Total await deadline, failed-read stop, interruption | Monotonic budget/request abort/interrupt handling | Stalled headers/body/slow drip and interrupted sleep/network |
| Exactly one attempt per HTTP request | Disabled retries/redirect/auth handling | POST/GET dropped connection, redirects and 401 challenge counters |
| Response limit/resources/idempotent close | Bounded read/abort/close | Known/chunked oversize, reuse after error, post-close zero sends |
| URL/timeouts/defaults/TLS | Builder plus SSLContext | Invalid config/default checks, trusted/untrusted/wrong-host TLS |
| Credential privacy | Credential holder and safe diagnostics | Serialization/toString/error/log sentinel assertions |
| Starter equivalence | Properties/autoconfig/core builder | Context/HTTP/SSL-bundle/close/override tests |
| Real completed/failed/lost response | External driver/proxy/counters | Required local SDK lane report with attempt and effect evidence |
| SDK documentation | SDK README | Build example used by actual driver; review API/default/outcome text |

## Risks and Rollback/Recovery

No persistence/schema change or data reset. Revert ticket changes in both checkouts
to recover the previous empty SDK foundation if needed; no compatibility code is
needed. Temporary configuration/token files and only the unique SDK Compose project
are cleaned on exit. Do not remove maintained reference fixtures, local credentials,
business databases or unrelated dirty files. Do not claim exactly-once business
semantics from a one-attempt/one-effect synthetic test. Changes discovered during
implementation must update this plan/testing plan when material and be independently
reviewed under Full.

## References
- Ticket and research paths above.
- `ai/thoughts/phases/2026-10-15-sdk-v1-api-reference.md`
- `ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md`
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java`
- `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java`
- External `AGENTS.md`, `docs/project-agreement.md`, `docs/sdk-lane.md`, `scripts/run_sdk.py`, `sdk/compose.yaml`.

## Step 4 execution notes (2026-10-18)

Implementation and executable acceptance mapping are recorded in
`ai/thoughts/validation/2026-10-18-pr-8-1-2-blocking-sdk-client.md`.
The core tests are grouped into the original `SidecarClientTest`, a separate
`SidecarClientEdgeTest` for protocol/deadline/resource cases, and `SidecarClientTlsTest`.
JDK HttpServer/HttpsServer fixtures provide exact response closure, declared/chunked
length and slow-body behavior; no external HTTP server is required. TLS uses a temporary
localhost certificate trusted explicitly by the test and rejects the same certificate
under default trust or a mismatched hostname.

Routine refinements: credentials allow only visible ASCII transport characters
(JWTs are ASCII) so invalid Unicode cannot be silently re-encoded; this does not parse
or verify JWTs. Duplicate JSON fields and trailing documents are rejected so an
ambiguous acceptance ID is unusable. A known non-success HTTP status survives even
when its body is truncated, with unavailable problem data left null. Core Boot bans
now have explicit direct/transitive synthetic-dependency tests in addition to the
existing eight Framework/Sidecar cases. No compatibility layer, production configuration
change, Framework rebuild or development-data reset was introduced.

The required local composed run passed in the external checkout at
`evidence/sdk/20261018T170928Z-ce1a23cab707/report.json`: all fourteen stages,
real completed/failed/unknown-acceptance outcomes, one submission/one forward/one
synthetic effect, startup zero-model check, graceful shutdown and cleanup.
Standard/hardened remain unimplemented; no claims are made for those recipes.
