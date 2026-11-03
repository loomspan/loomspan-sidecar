# HTTP skill executor testing plan

## Change summary

Replace separate REST routes and per-generation clients with the actual annotated
HTTP executor, strict explicit JSON Pointer mapping, shared captured targets,
encrypted fixed credentials, caller JWT forwarding, bounded faithful response
handling and one application-owned client.

**Destructively replace superseded contracts; add no compatibility adapters,
parallel APIs, stubs, disabled tests or migrations.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt).
This is step 3 of the developer-approved Full 5-Step Pipeline, continuing step 2.
Read the companion implementation plan for final schema/default decisions.

## Impacted areas and risks

| Area | Risk | Planned evidence |
| --- | --- | --- |
| Mapping | Wrong host/path/query/body or null/absence conflation | Pure matrices plus wire-observed representative requests |
| Authentication | Wrong identity, expired JWT, credential/cookie contamination | Real cipher/header tests and concurrent public invocations |
| Protocol | Retried actions, redirected credentials, malformed decoding, unbounded cleanup | Local HTTP/TLS/socket fixtures and slow response latches |
| Capture | New publication changes retained invocation settings/credentials | Public retained admission across complete replacement |
| Lifetime | Client closes before Framework drain or adds second drain | Actual context close with normal and cutoff work |
| Feedback | Ordinary failures reach model or response body is rewritten | Local provider tool protocol and public observer assertions |
| Framework boundary | Integration tests hide unsupported dependencies | Isolated ArchUnit plus retained whole-project architecture test |

## Existing coverage and environment constraints

Java 21, Maven wrapper 3.9.11, Boot 4.1.0, JUnit 5/AssertJ/Mockito, Jackson 3,
Apache client5, Bouncy Castle and ArchUnit already exist in root pom. Use installed
Framework beta.9-SNAPSHOT only. `com.sun.net.httpserver.HttpServer` is available for
loopback fixtures. Reuse scenario ideas from old REST tests, not their superseded
expectations or Framework internal fixtures. Do not add a model dependency or
contact live services. Temporary DB/files may be created only under test temp dirs.

Normal compilation is expected to fail on removed Framework APIs and deleted REST
runtime dependencies in downstream startup/management/catalog code. First run the
ordinary compile command and record actual errors. If necessary, the implementation
may add `scripts/verify-http-executor.ps1`: a transparent targeted Maven check which
derives dependencies from root pom, generates a temporary POM under target, names
actual HTTP source/tests plus `ProviderCredentialCipher`/`EncryptedCredential`, and
runs ordinary compiler/Surefire in a clean isolated output directory. It must not
change default Maven behavior, disable tests, generate compatibility sources,
compile Framework source or claim whole-application success. Use exact include
lists and include all new HTTP unit/integration/TLS/lifecycle/architecture tests.
The isolated Boot fixture loads real Framework via normal Boot auto-configuration
without importing its autoconfigure classes or replacing Framework beans.

The whole-project ArchUnit test remains in place. An isolated HTTP architecture
test proves only the new runtime/test package boundary. Full application startup,
management, shutdown through ExecutionCoordinator and full-suite results remain
explicitly blocked until their actual downstream callers/fixtures are reconciled.
Public runtime admission/shutdown tests are nevertheless required in this slice.

## Failing test first

- Name: `mapsExplicitPointersAndPreservesWholeInputBody`.
- Type: HTTP runtime unit test with actual loopback dispatch.
- Location: `src/test/java/ai/loomspan/sidecar/http/HttpSkillExecutorTest.java`.
- Arrange: target base `/api/v1`, POST `/cars/{id}`, `pathParams.id: /car/id`,
  repeated query mapping `/options/colors`, `body: {source: input}`; input includes
  nested ID, ordered colors, zero and false.
- Act: invoke production `HttpSkillExecutor` with public SkillExecutorInvocation.
- Assert: server receives encoded prefix/path, explicit repeated query names and
  the entire original JSON input including path/query fields.
- Expected pre-fix failure: new runtime symbols do not exist; record the focused
  compiler/test failure before implementing them. Existing obsolete project-wide
  compile errors alone are not the behavioral red test. A subsequent mapper-only
  test can isolate the missing construction behavior if needed.

## Tests to add or replace

All new paths below are under `src/test/java/ai/loomspan/sidecar/http/`.

### 1. `HttpExecutorConfigurationTest`

- Type: pure unit tests, no HTTP client/server construction.
- Proves: reusable executor and operation validators; all unknown/null/type errors,
  target selection, finite positive limits/defaults/overflow, five methods and
  GET/body prohibition, auth modes/reference rules, strict recoverable statuses.
- Inputs: selected subtree maps, operation maps, actual existing encrypted records.
- Cases: defaults 5s/60s/1MB; valid explicit bounds; zero/negative/submillisecond/
  overflow/infinite malformed limits; wrong roots/maps/lists; empty target names;
  bad schemes/host/user-info/query/fragment; host replacement and slash-prefixed
  operation paths; whole-segment placeholders; missing/unused/repeated mappings;
  strict `/` pointer prefix and `~0`/`~1`, malformed `~` escapes; body source
  combinations; duplicate/fractional/string/401/403/non-4xx statuses.
- Header cases: token names, controls/CRLF/NUL, case duplicates/conflicts, framing
  ownership, separate auth, allowed ordinary Accept, JSON Content-Type compatibility.
- Credential cases: helper copies records, duplicate/missing identifiers, metadata
  validation and no captured plaintext. No network is needed for all these cases.

### 2. `HttpRequestMapperTest`

- Type: pure mapping tests using production mapper/Jackson.
- Proves: explicit JSON Pointers and URI/body construction without dispatch.
- Cases: nested object/array/escaped `/` and `~` keys; literal `*` key is one key,
  no expansion; out-of-range/missing/non-container traversal; path strings/numbers/
  booleans and rejection of null/empty/object/array/missing/nonfinite values.
- Safety matrix: `/`, `\\`, `.`, `..`, `%2f`, `%5c`, `%2e%2e`, mixed casing,
  nested `%25` encoding, encoded traversal segments, malformed escapes; base and
  static path counterparts; absolute and authority-form skill paths.
- Encoding matrix: Unicode, spaces, `+`, `%`, `?`, `#`, `&`, `=`, quotes and braces
  in selected original values; preserve intended destination/prefix and exactly
  one joining slash. Assert raw URI rather than normalized server convenience APIs.
- Queries: missing/null/out-of-range and empty array omit; empty string produces
  empty value; zero/false preserved; scalar arrays repeat names in order; reject
  objects/nested arrays/null elements. Distinct mapped names select the same input
  without consuming it.
- Bodies: absent entity, whole input, selected object/array/string/number/boolean/
  null; absent selector fails; mapped input remains in whole body; all supported
  body methods. Assert bytes/JSON types without relying on property order.

### 3. `HttpSkillExecutorTest`

- Type: actual production runtime through a local HttpServer; no mocked RestClient.
- Fixture: package-local helper records raw URI, method, request bytes and selected
  headers; bounds every wait; responds deterministically; closes server/executors.
- Proves representative GET, bodyless POST, whole-input POST, nested-field PATCH,
  PUT and DELETE; no dispatch for config/input/auth failures.
- Auth: none sends no credential; fixed decrypts existing cipher-encrypted record;
  two versions produce their own values; missing reference/key, corrupt ciphertext,
  unsafe decrypted header and decryption failure are terminal with zero dispatch.
  Passthrough sends original token byte-for-byte and rejects missing/non-JWT/
  unauthenticated/missing-expiry/expired/exact-expiry authentication. A fixed Clock
  removes expiry timing races. Input cannot choose mode or credential.
- Concurrency: one shared client, two callers and two targets to the same destination,
  unique fixed headers/JWTs, Set-Cookie responses and repeated calls; assert no
  cross-call headers or cookie replay and no ambient context pollution.
- Timeouts: latch-held slow response with short and long captured read settings,
  both active concurrently, short times out while long succeeds after release.
  Follow with reused connections to detect timeout persistence. Mockito socket
  construction fixture checks two distinct connect limits at the socket boundary
  and one attempt per call, with no external blackhole.
- Attempts: redirect fixture records no redirected request; dropped connection and
  5xx fixtures record a single request, no automatic retry. Never infer no remote
  effect from the timeout assertion.
- Limits: exact cap success, cap+1 terminal for success and listed/unlisted errors,
  fixed/chunked and decompressed body when compression is enabled. Slow unbounded
  remainder uses latch; failure must occur before releasing remainder. Check both
  success and error paths and cleanup, not just a small already-buffered response.
- Media matrix for successes and HTTP errors: JSON/+json UTF-8 unchanged including
  whitespace/newlines; text charset/default; no type UTF-8; non-UTF-8 JSON rejected;
  application binary and non-application +json rejected; invalid bytes, unknown
  charset and malformed type terminal; empty/no body accepted regardless of type.
- Status matrix: representative/all 2xx including 204, listed/unlisted 400/409/422,
  401/403, redirect and 500; message exactly `HTTP <code>\n<body>` or status-only.
  Listed status with oversized/undecodable body remains ordinary SkillException.
- Diagnostics: secret markers in configured/decrypted credentials must not appear
  in generated exception messages, attached causes or captured logs. Destination-
  owned response markers remain unchanged; do not test for content redaction.
- Thread interruption: interrupted transport preserves interrupt status where
  applicable; ordinary socket timeout does not spuriously mark the thread interrupted.

### 4. `HttpExecutorTlsIntegrationTest`

- Type: actual local HTTPS with generated Bouncy Castle fixtures.
- Proves normal HTTPS server trust and hostname verification, not a custom executor
  trust API. Use standard JVM trust store configuration with temporary PKCS12 data;
  restore properties and isolate a JVM if cached default TLS context would make
  in-process ordering unreliable.
- Cases: trusted matching SAN succeeds, untrusted chain fails, trusted mismatched
  SAN fails. No trust-all manager, hostname bypass, executor SSL bundle or client
  certificate. Loopback HTTP remains independently supported. Verify finite target
  timeout behavior and no follow-up connection after the failure.

### 5. `HttpExecutorIntegrationTest`

- Type: Sidecar production executor plus minimal Boot context loading the real
  installed Framework through normal auto-configuration. Use only public APIs.
- Proves annotation discovery and actual YAML operations through SkillTemplate;
  framework schema/RBAC still gates invocation independently of HTTP-specific input
  checks. Skill classification remains JAVA and executor itself is not a skill.
- Capture test: publish A, admit and retain a public invocation, publish B, invoke
  retained A then newly admitted B; server observes each base/path/header/timeout/
  encrypted credential version. Use same application client; do not recreate its
  generation resources or derive durable IDs from runtime generation IDs.
- JWT test: install known Spring authentication at handoff, retain/invoke on the
  appropriate supported path and assert captured original caller token. Include
  standard model-tool child dispatch to prove propagation across Framework work.
- Feedback fixture: local OpenAI-compatible HTTP server returns one deterministic
  tool call and then text. Assert its second request contains complete exact
  recoverable HTTP message, and public observer events classify failed call.
  Unlisted/terminal/invalid/oversize response must propagate and issue no model
  continuation; direct invocation propagates the recoverable exception rather than
  claiming a recovered direct result. No paid model endpoint or Framework internals.
- Security diagnostics: public execution views/events must not contain configured
  infrastructure secret markers; expected application body remains intact.

### 6. `HttpExecutorLifecycleIntegrationTest`

- Type: actual context close and public Framework admitted invocation/template.
- Normal drain: latch blocks response; start close, assert client still usable/open
  during admitted work, release, assert result completes and close finishes; client
  closes exactly once after Framework handling. No second drain.
- Cutoff: blocked response exceeds short `loomspan.shutdown.timeout`; context close
  finishes under a generous bounded test deadline without waiting another client
  read timeout. Client cleanup is immediate/idempotent after Framework cutoff.
- Context boundaries: child close does not close parent-owned client; repeat with
  async application event multicasting and at least a non-web normal Boot context.
  Include servlet context when practical using standard Boot test infrastructure.
- Do not import/mock/replace internal Framework lifecycle beans, inspect internal
  worker maps or assume observer completion beyond the supported public contract.
- Full Sidecar queue/coordinator shutdown integration stays present but is blocked
  by 9.2 startup/correlation work; state this separately from runtime lifecycle pass.

### 7. `HttpExecutorArchitectureTest` and current whole-project rule

- Type: ArchUnit on new HTTP production/test packages and reused credential types.
- Proves no dependencies on `ai.loomspan.internal..` or
  `ai.loomspan.autoconfigure..`, no Java SkillMethod declarations, real Sidecar
  executor is included. Retain the full-project existing architecture test.
- Add source/import/legacy scans as complementary checks; do not present a textual
  scan as equivalent to executing the whole-project architecture rule.

## Safe verification commands

Run from `C:/opendev/code/loomspan-sidecar` using the local snapshot. Commands below
are planned, not already executed results.

- Baseline compile: `./mvnw.cmd -q -DskipTests compile`.
- Focused normal check when compilation permits:
  `./mvnw.cmd -q "-Dtest=ai.loomspan.sidecar.http.HttpExecutorConfigurationTest,ai.loomspan.sidecar.http.HttpRequestMapperTest,ai.loomspan.sidecar.http.HttpSkillExecutorTest" test`.
- All new runtime tests when normal compilation permits:
  `./mvnw.cmd -q "-Dtest=ai.loomspan.sidecar.http.*Test" test`.
- If the documented downstream blockers prevent those commands:
  `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-http-executor.ps1`.
  Record the generated POM's actual source/test includes and Surefire totals in
  verification evidence. Fresh isolated output prevents old compiled classes
  masking missing sources. This is a targeted check, never whole-project success.
- Broad safe attempt: `./mvnw.cmd -q test`. Record blocked normal test commands and
  exact compiler failure names; do not alter unrelated callers just to obtain green.
- Diff whitespace: `git diff --check`.
- Supplementary import scan:
  `rg -n 'ai\.loomspan\.(internal|autoconfigure)|@SkillMethod' src/main/java/ai/loomspan/sidecar/http src/test/java/ai/loomspan/sidecar/http`.
  No matches is expected; distinguish an intentional assertion string from an import.
- Run applicable existing documentation checks such as
  `python scripts/test_agent_skills.py` when packaged guidance is changed; inspect
  script usage first and record any obsolete-fixture blocker rather than weakening it.

Do not run production operations, remote probes, release profiles, Framework builds,
CI publication or destructive data resets as verification.

## Optional developer checks

None required. Live hosting-application authorization/idempotency tests may be run
separately in an explicitly configured environment but are not needed for local
runtime proof and must not be claimed as executed.

## Exit criteria

- [x] New capability has a recorded intended red test/compiler result before runtime implementation.
- [x] All new configuration, mapping, real transport, TLS, public integration,
  model feedback, capture, concurrency, lifecycle and architecture tests pass.
- [x] Every ticket acceptance criterion maps to executable runtime evidence and
  current documentation; checks are marked complete only after execution.
- [x] Exact normal-build and full-suite attempts are recorded. Permitted downstream
  startup/management/fixture blockers name the failure, consequence and 9.2/9.3 owner.
- [x] No runtime acceptance gap is concealed as an intermediate downstream blocker.
- [x] No obsolete compatibility runtime, disabled test, Framework internal import,
  paid model call, live external action, secret-bearing diagnostic or data reset.
- [x] Full-project architecture and full application/queue/management/startup checks
  are reported blocked when not executed; isolated passing checks are not substitutes
  for final composed verification in 9.3.
- [x] Runtime documentation states actual activation/management limitations and
  matches schema/defaults/errors/TLS behavior; optional checks are not counted as passes.
- [x] Independent fresh review can reproduce the targeted suite using the actual
  checked-out source and installed dependencies.

## Implementation receipt

Step 4 evidence is recorded in `ai/thoughts/verification/2026-11-02-pr-9-1-http-skill-executor.md`.
Checked command items include recorded blocked/conditional branches, not claims that
normal compilation or the complete suite passed. The isolated runtime suite is the
executable evidence for this slice. Full application startup, managed activation,
queue shutdown and full-project architecture checks remain blocked with explicit
9.2/9.3 ownership. No optional live checks were performed.
