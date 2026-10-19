# SDK callback authentication Testing Plan

## Change Summary

**Replace superseded development contracts directly; no compatibility shims,
legacy adapters or parallel APIs.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No data reset.

Pipeline Steps 2 and 3 use profile `full`. Inputs read completely: the matching
ticket, research and implementation plan under `ai/thoughts/`. This plan adds no
production or test implementation. `suite/` means
`C:/opendev/code/loomspan-sidecar-test-suite/`; starter test paths below share
`sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/`.

The starter gains independent callback JWT authentication, an isolated servlet
chain and fail-closed optional overlap trust. The reference app demonstrates
method and record authorization and the Local harness proves real forwarding and
expiry after Framework admission. Existing Sidecar sender expiry and process-file
trust behavior remain regression coverage.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Authentication boundary | Session/browser identity authenticates callbacks | Existing authenticated session plus absent/invalid bearer fails 401; session never read |
| Ambiguous credentials | Multiple bearer/header/query tokens authenticate | Resolver matrix, valid header plus query rejected |
| JWT interpretation | Wrong issuer/purpose or coerced claim shape accepted | Raw signed JSON claim matrix and independent wrong-key signature |
| Authorization | SDK maps roles or bypasses application checks | Exact principal authorities, method denial before body, business denial before effect |
| Chain isolation | Callback falls through or ordinary form/CSRF breaks | Two real chains, normal login/CSRF regression, error-dispatch counter |
| Trust lifecycle | Own key masks invalid overlap; stale/removed keys survive | Effective-snapshot withdrawal/recovery/removal assertions, concurrent reads |
| Timing | Queue expiry incorrectly substitutes for callback expiry | Separate admitted planner barrier and exact pre-send child failure; zero arrivals |
| Operational scope | Harness touches business runtime or exposes credentials | Existing unique-project composition/cleanup and sanitized-report tests |

## Existing Coverage and Environment Constraints

The starter uses JUnit 5, AssertJ, Boot ApplicationContextRunner and generated RSA
fixtures. Add Spring security-test and servlet MockMvc/Boot test dependencies as
needed using the existing Boot 4.1.0 dependency management. Exercise actual filter
chains with a servlet context and FilterChainProxy, not only mocked decoder calls.
Include a real embedded servlet HTTP smoke if MockMvc cannot prove firewall/header
or dispatch behavior; exact duplicate headers must reach the resolver intact.

Sidecar's `CurrentFileJwtTrustTest`, `JwtSecurityConfigurationTest`,
`GenericRestSkillHandlerTest` and `ConfigurationModeIntegrationTest` are useful
regression evidence but cannot satisfy starter component obligations. Reproduce
relevant behavior through independently owned SDK tests. No imports from Sidecar
or Framework security internals. Core remains independent of Spring Boot runtime.

The suite has pytest runner/fixture tests, ruff and a real Docker Local lane. The
lane packages the Boot consumer, installs SDK, builds root once and builds the
image from actual sources. Requires Java 21, Maven, Python suite .venv, Docker,
images and resolvable Framework beta.8. No provider calls or credentials needed.
The separate beta.9 business runtime and maintained reference packs are untouched.
No verification commands have been run by planning; listed expectations are future
gates, not reported passes. If Docker is unavailable, component success does not
complete composed acceptance; record exact environmental blocker.

## Failing Test First

- Name: `callbackRequiresBearerEvenWithAuthenticatedBrowserSession`.
- Type: servlet security component test.
- Location: starter `CallbackSecurityAutoConfigurationTest.java`.
- Arrange/Act/Assert: start a normal application form chain with an authenticated
  browser session and a callback GET under the default prefix. Install SDK
  autoconfiguration through its discovery path. Request callback using only the
  session; assert 401, `WWW-Authenticate: Bearer`, no Location, no body invocation,
  no `/error` forward, no session access/create or new session cookie.
- Expected pre-fix failure: the existing app chain accepts session authentication
  (or redirects an anonymous request), because no dedicated callback chain exists.
- Run the new test before implementation and preserve the failure reason; a compile
  failure from referring to nonexistent implementation is not the intended red proof.
  Build the fixture using discovery/string properties rather than missing symbols.

## Tests to Add or Update

### 1. `callbackRequestMatrixAndDirectRejections`

- Type: servlet security component, starter `CallbackSecurityAutoConfigurationTest.java`.
- Proves: every request-table row and direct rejection semantics.
- Inputs: generated valid SDK execution token; missing/empty/malformed Authorization,
  Basic only, two Authorization headers (same and different bearers), comma-combined
  bearers, repeated Bearer in one header, query access_token only, repeated query
  values, valid header plus access_token, cookie-only and existing-session-only.
- Assert all credential rejections are 401 with Bearer challenge; no Location,
  redirect, forward/error dispatch, protected method invocation or fallback-chain
  invocation. Token values never appear in response/error capture.
- Valid GET and POST succeed without CSRF token; authenticated method denial and
  application record denial return 403 with distinct method/effect counter oracles.
- Boundary isolation: real security chain with fixed in-process controller and keys,
  no Sidecar or model. Record session calls using a wrapped request/session trap.

### 2. `callbackChainIsStatelessAndIsolated`

- Type/location: same servlet component class.
- Proves: NullSecurityContextRepository/NullRequestCache behavior, context lifecycle,
  SDK highest precedence and ordinary application's independent form/CSRF chain.
- Inputs: two successive requests on same test thread, first valid JWT then no JWT;
  a deliberately populated session/context cookie; an ordinary session login.
- Assert no request.getSession calls on callbacks, no Set-Cookie/session creation,
  no successful context after chain returns and next unauthenticated request fails.
  Capture callback issuer, subject, principal and exact role set (ROLE_ORDER_EDITOR,
  ORDER_EDITOR and unrelated authority); no SCOPE_/ROLE_ prefix conversion.
- Ordinary GET/login works; ordinary POST lacking CSRF fails and with CSRF succeeds.
  Callback rejection never reaches ordinary controller/filter-chain marker.
- SDK configuration must coexist with another JwtDecoder/converter without adopting
  it; non-web issuance context still has no callback chain or scheduler.

### 3. `prefixMethodsAndFirewallFollowDocumentedPolicy`

- Type/location: same servlet component class plus context startup cases.
- Inputs: default and `/custom/hooks` prefix, exact root/descendant, sibling prefix,
  trailing slash normalization, empty/root/wildcard/query/fragment/backslash/dot
  configuration. Two application routes make outside-prefix behavior observable.
- Assert invalid prefix settings fail startup with safe property-name diagnostics;
  override protects only override, outside/sibling routes use application policy.
  Valid bearer GET/POST work; PUT/PATCH/DELETE/HEAD/OPTIONS return 403 under denyAll;
  absent bearer on disallowed method follows 401 entry point. Default firewall
  TRACE, semicolon/path-parameter and non-normalized double-slash request cases
  return 400 before authentication; pin only requests the actual Spring firewall
  recognizes and document concrete examples. No global firewall replacement.
- Servlet connector malformed wire syntax follows container handling and is not
  used as proof of bearer rejection or chain matching.

### 4. `strictExecutionJwtValidation`

- Type: decoder unit tests, starter `CallbackJwtDecoderTest.java`, with component
  cases ensuring decoder failures become direct 401 Bearer responses.
- Fixtures: generated RSA keypair and a second key; sign raw JSON for malformed
  registered claims so a library builder does not normalize invalid inputs.
- Reject: signature corruption/untrusted same-kid key/unknown kid, unsigned token,
  HS256/RS512, missing or wrong typ, missing or wrong purpose, different signed
  issuer, blank/missing/nonstring issuer and subject; missing/wrong/empty/nonstring
  audience and mixed list; missing/non-list/mixed roles; nonnumeric/missing/out-of-
  range exp and malformed optional iat/nbf/jti. Invalid kid/header shapes also fail.
- Accept audience as expected single string or included among valid strings;
  preserve exact roles, issuer and subject. Extra untrusted claims do not grant roles.
  Verify override audience and issuer-derived default; there is no callback issuer knob.
- Fixed Clock proves expired beyond 60-second skew fails, within skew accepts,
  future nbf beyond skew fails and within skew accepts. Choose one second on either
  side of cutoff to avoid floating/nanosecond ambiguity; test exact cutoff if
  documented Spring validator semantics require it. Sender strict expiry is separate.
- Error text is constant and contains no JWT, claims or key material.

### 5. `ownKeyAndOverlapTrustTransitions`

- Type: trust unit and decoder integration tests, starter `CallbackJwtTrustTest.java`.
- Fixtures: temporary JWKS, startup public key from colocated and provided signing
  modes, old overlap key, replacement overlap key, injected monotonic ticker/reader.
- Unconfigured overlap: both signing modes authenticate own token without any file
  read, no scheduler required solely for immutable own trust.
- Valid overlap: own and overlap authenticate; equal own-kid/equal-key merge works;
  same kid/different key fails all effective trust. Remove old overlap key, reload,
  reject old token and continue accepting own/current keys. Removed old keys cannot
  be restored from a cache by an unknown-kid lookup.
- Missing/unreadable/malformed/empty configured overlap rejects both own and old
  tokens. Correct the file then reload and accept again without replacing signer.
  Initial unusable configured file starts with no effective keys rather than
  throwing away the app's normal login availability.
- Cover whole-file invalid member among good keys; non-RSA, blank/duplicate kids,
  private fields, wrong use/alg, small/even/invalid RSA n/e and invalid UTF-8/JSON.
  No subset publication. Unchanged content avoids needless publication; changed
  content with unchanged file metadata reloads. File disappearance also withdraws.
- Unknown kid triggers immediate read once and floods/concurrent unknown kids are
  limited to one per second globally; advancing fake ticker permits another read.
  Concurrent snapshot readers observe full old or full new/empty result, never mix.
- Polling integration uses bounded eventual wait (not sleeps as a correctness
  oracle) to prove change detection and automatic recovery; close/context shutdown
  stops scheduler and future reads. No live filesystem permission assumptions on
  Windows: use reader seam to simulate unreadable I/O deterministically.

### 6. `autoconfigurationAndDependencyBoundaries`

- Type: context/startup and existing independent build-boundary suite.
- Location: starter `ExecutionTokenAutoConfigurationTest.java` and
  `CallbackSecurityAutoConfigurationTest.java`; `sdks/java/tests/test_build_boundary.py`.
- Assert imports include new callback configuration, servlet chain is present with
  ordinary application chain, issuance issuer validation still governs callbacks,
  invalid callback properties fail safely and plain-Java/non-web SDK use is intact.
- Run existing reactor transitive dependency and core-runtime exclusion tests.
  Do not add a shared Sidecar dependency to avoid duplicate validation code.

### 7. `localCallbackAuthorization`

- Type: real composed Local recipe.
- Location: `suite/scripts/sdk_lane/local_probe.py`, `local.py`, `fixture.py`,
  `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/CallbackController.java`.
- Authorized Alice skill calls fixed owned-record callback using Sidecar
  caller-passthrough. Assert verified issuer/subject/roles match initiating user,
  exact forwarded credential equality observed only in memory, one arrival/body/
  effect and completed skill. Do not expose bearer values in reports or model inputs.
- Literal-role user enters an intentionally admitted REST skill then receives
  callback 403 from standard hasRole method check. Assert arrival +1, body/effect
  unchanged, skill failure retains HTTP 403. Existing skill-role denial remains a
  separate earlier-boundary case.
- Bob has required role but targets Alice-owned record. Assert authenticated method
  body executes, ownership yields 403 and effect remains unchanged.
- Ordinary form login, authenticated routes and CSRF POST behavior still pass in
  the same composition after callback scenarios. Counter/reset paths require app
  authentication and are not SDK callback routes.
- Repeat detailed invalid token/header permutations only in component tests.

### 8. `localCallbackExpiryAfterAdmission`

- Type/location: same real Local lane, dedicated scenario and sanitized result schema.
- Use app-issued two-second execution JWT and planner with allowed callback REST
  leaf. Wait for deterministic model barrier entry and RUNNING execution before
  expiry; this proves Framework admission (not merely QUEUED state).
- Capture baseline callback arrival/body/effect counters. Hold the bounded barrier
  beyond expiry, then release a deterministic CALL_TOOL response. The original
  captured credential is used; polling uses fresh normal credentials.
- Assert exact leaf failure `execution credential expired`, no callback HTTP arrival,
  no body/effect, and final parent outcome that exposes the required child failure.
  A generic final model string without observed child error is insufficient.
  Use actual existing planner response protocol, as shutdown fixture does, rather
  than speculative OpenAI tool-call format. Keep credential out of planner payload.
- All waits have deadlines; barrier release occurs in finally; zero arrivals is
  checked through terminal completion plus bounded quiet interval. Preserve queue
  expiry's distinct `execution credential expired before start` regression.

### 9. `localRunnerEvidenceAndCleanup`

- Type: pytest fixture/runner tests, `suite/tests/test_sdk_runner.py`.
- Update fake runtime/scenario stages and strict RESULTS schemas for callback
  authorization/expiry; every scenario must be passed before success can be emitted.
- Assert one root build, actual reference-app package hash, shipped compose hash,
  no business mounts/provider access/host ports, unique temporary key volume, fixed
  callback target and caller-passthrough configuration.
- Exercise barrier boundedness, separate counters, failed scenario/report failure,
  cleanup on success/failure/interruption and no token/private-key/raw exception in
  stdout or report. Fixed status/equality/counter observations suffice.
- Existing root ConfigurationModeIntegrationTest remains responsible for JWKS-only
  process trust in both file/database modes and runs in the composed root build.

## Safe Verification Commands

From `C:/opendev/code/loomspan-sidecar`:

```powershell
mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am '-Dtest=CallbackSecurityAutoConfigurationTest#callbackRequiresBearerEvenWithAuthenticatedBrowserSession' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am '-Dtest=CallbackJwtTrustTest,CallbackJwtDecoderTest,CallbackSecurityAutoConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -f sdks/java/pom.xml clean install
python sdks/java/tests/test_build_boundary.py
```

The first command is the intended red test; it must fail before production edits
for missing authentication behavior, then pass after implementation. Match actual
test names if equivalent cases are consolidated without dropping obligations.

From `C:/opendev/code/loomspan-sidecar-test-suite`:

```powershell
.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q
ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py
ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py
.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar
```

The last command is the broadest safe relevant suite and includes root
`./mvnw.cmd -B -ntp clean verify`, SDK build, real packaging, root component
regressions, existing Local/baseline tests and cleanup. Do not invoke the business
runtime start/smoke/mock or paid runners. Do not nest the external runner in
Sidecar Maven/CI. Avoid repeating clean builds once the unchanged final source
passed; rerun affected checks after meaningful fixes.

If the composed runner fails before its root build, a standalone root
`./mvnw.cmd -B -ntp clean verify` can establish regression status, but does not
replace the required composed evidence. Record unrun/failed gates accurately.

## Optional Developer Checks

None. All ticket acceptance behavior has planned executable evidence. Independent
review also checks SDK examples/property names and current evidence links against
the final code and reports; this is part of required review, not a manual gate.

## Exit Criteria

- [x] Intended red test fails for missing callback boundary before implementation.
- [x] New/updated component and runner tests pass after implementation.
- [x] Complete SDK reactor and dependency boundary checks pass.
- [x] Broadest safe relevant Local composed run passes and cleanup succeeds.
- [x] Every acceptance row maps to the implementation-plan traceability table and
  executable evidence; prior reports/framework tests do not stand in for new proof.
- [x] Required callback expiry observes admitted work, exact child failure and zero
  callback HTTP arrivals; queue expiry alone is insufficient.
- [x] Configured overlap withdraws own-key acceptance and recovers, including removed
  key rejection, whole-file validation, rate limiting and shutdown coverage.
- [x] Callback context/session/CSRF/firewall/authority semantics agree with SDK docs.
- [x] No unintended live/destructive operation, business data mutation, provider
  send, credential disclosure, commit or release occurred.
- [x] Verification report distinguishes actual passes/failures and blocked/unrun gates;
  optional observations are never represented as performed acceptance tests.

## Step Report: 2_create_plan + 3_testing_plan
STATUS: complete
ARTIFACTS:
  - ai/thoughts/plans/2026-10-18-pr-8-1-6-sdk-callback-authentication.md
  - ai/thoughts/plans/2026-10-18-pr-8-1-6-sdk-callback-authentication-testing.md
SUMMARY: Verified current starter, trust, reference-app and Local harness sources. Planned independent callback authentication and fail-closed overlap lifecycle plus complete component and composed coverage. No code, tests or runtime services were changed or run.
DECISIONS:
  - Keep security ownership inside the independent starter, using own signer public key plus optional atomic overlap trust and the existing issuance issuer.
  - Document normal authenticated disallowed-method 403 and default firewall 400 handling, with no global firewall customization.
  - Prove callback expiry using a bounded deterministic planner barrier after Framework admission and independent callback arrival counters.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run Step 4 implementation and verification with both plans under profile full.
