# SDK callback authentication Implementation Plan

## Overview

**Development policy: replace superseded development contracts directly; do not
add compatibility shims, legacy adapters, dual APIs or migration machinery.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
No development-data reset is required; deployed or maintained data is not disposable.

- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-6-sdk-callback-authentication.md`
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-6-sdk-callback-authentication.md`
- Profile: `full`, developer approved proceeding with Full 5-Step Pipeline.
- Outcome: independently authenticate application callbacks, demonstrate standard
  method and business authorization, and complete the provider-free Local recipe.
- External paths below use `suite/` for `C:/opendev/code/loomspan-sidecar-test-suite/`.

## Current State

Verified current sources after research: `ExecutionTokenAutoConfiguration` creates
the startup-owned `SigningKeySource` and issuance helpers, but no callback receiver.
`SigningKeySource.publicKey()` and `kid()` expose the exact public identity needed.
The starter POM lacks resource-server and security-config dependencies. Its
autoconfiguration imports list only client and issuance configurations.

`CurrentFileJwtTrust` and `ExecutionJwtDecoder` demonstrate whole-file atomic trust,
one-second reload/rate limit, raw claim validation and 60-second skew, but belong to
the Sidecar application. SDK dependency boundaries prohibit depending on them.
`GenericRestSkillHandler.handle` already forwards captured credentials unchanged
and checks strict expiry immediately before sending. No sender change is planned.

The suite reference app has a catch-all form-login chain, three fixed submission
skills, a two-second issuer used for queue expiry, and no callback controller or
method security. `local.configure`, `local_probe.queue`, `LocalHandler` and runner
stages exercise issuance/trust/queue behavior. The existing shutdown fixture already
uses deterministic planner JSON (`CALL_TOOL`, `FINAL_RESPONSE`); reuse that protocol
for admitted-work expiry rather than introduce Java skills or test-only Framework SPIs.

## Desired End State

The servlet starter installs a first, dedicated callback chain. Applications keep
routes under its prefix and explicitly enable standard method security. Valid
callbacks expose verified issuer, subject and the exact `roles` authorities.
Ordinary browser routes continue through the application's own form-login chain.
Configured overlap trust failures reject all callbacks until correction; absent
overlap configuration accepts the startup signer's public key alone.

## Scope

### In scope

- SDK callback properties, trust owner, decoder, bearer resolution and servlet chain.
- Starter component security/lifecycle tests and documentation.
- Suite reference-app callbacks, role/ownership examples, Local recipe scenarios,
  bounded fixtures, sanitized report assertions and current coverage documentation.

### Out of scope

- Framework internals or changes, new SPIs, private signer reload, public key endpoints,
  Sidecar trust redesign, Standard/Hardened recipe completion or rotation scenario PR 8.1.8.
- Business suite changes, paid providers, reference-pack edits, commits or release work.

## Active Project Guardrails

- SDK remains independently buildable: no Sidecar/Framework dependency or imports.
  Small equivalent SDK validation code is justified by that boundary; avoid a shared
  cross-reactor security library and its maintenance cost.
- Trusted identity comes from verified authentication, never model inputs. Exact
  authority names survive issuance, forwarding and callback authentication.
- SDK authenticates; application method security and record checks authorize.
- One complete current-file trust result; detected failure withdraws trust and
  correction recovers without restart or history ledgers.
- Sidecar reads only its configured JWKS in file and database ConfigurationMode;
  existing component evidence remains a regression gate, not proof of SDK callbacks.
- Use only supported Framework public contracts; Sidecar hosts no Java `@SkillMethod`.
- Preserve separate suite business runtimes, operational data and maintained fixtures.

## Impact and Risk Analysis

The new servlet chain deliberately changes callback authentication and ordering.
No catch-all SDK chain, shared decoder override or global authority-prefix bean may
alter application security. Keep decoder/converter owned by this chain, with named
beans or direct construction so unrelated resource-server beans cannot override it.
Use the existing application security infrastructure and a distinct HttpSecurity
chain; do not analyze or rewrite arbitrary application matchers.

The highest risks are browser-session fallback, ambiguous bearers, claim conversion
accepting malformed input, overlap failures hidden by own keys, and expired queued
work mistaken for callback expiry. Tests isolate all five. A callback's 60-second
receiver skew does not change the sender's strict pre-send expiry rule.

## Implementation Approach

Use `CallbackProperties` under `loomspan-sdk.callbacks`:

- `path-prefix`: default `/loomspan/callbacks`; one absolute literal prefix.
  Normalize a trailing slash once; reject blank/root, query/fragment, wildcards,
  backslashes and ambiguous dot/empty path segments at startup. Match the literal
  prefix and descendants with a segment boundary, never `/loomspan/callbacks-other`.
- `audience`: optional nonblank override; default to the validated issuance issuer.
  Overrides must also be included in issuance additional audiences when issuing
  credentials intended for that receiver. There is no callback issuer setting.
- `overlap-jwks`: optional local filesystem path. A blank explicit value is invalid;
  unconfigured means own key only. No remote resolver or JWKS HTTP endpoint.

`CallbackJwtTrust` owns one immutable volatile merged snapshot. Start with the
`SigningKeySource` public key/kid. If overlap is configured, synchronously load it
and then poll every second on a daemon scheduler; rate-limit unknown-kid reloads
globally to one per second using monotonic time. Read bytes and compare content,
not mtime, and validate the entire candidate before one atomic assignment. Apply
Sidecar's file validation: public RSA only, >=2048 bits, valid unsigned n/e,
nonblank unique kids, optional use=sig/alg=RS256, no private members, valid UTF-8
JSON and nonempty keys. Duplicate own kid with identical public key is harmless;
the same kid with a different key invalidates the candidate. Invalid/unreadable/
missing/empty overlap publishes no effective keys, including no own-key fallback.
Retain neither stale keys nor a withdrawal ledger. Recovery reloads normally.
Removed overlap keys disappear immediately after publication. Close stops the
scheduler. Own keys remain startup-owned; no key export re-read or private reload.

`CallbackJwtDecoder` validates raw JOSE/claims before Spring conversion: RS256,
exact typ, nonblank kid, trusted signature, exact token_use, exact configured
issuance issuer, nonblank subject, audience string or nonempty list of strings
containing expected audience, roles list of strings, numeric finite/range-valid exp
and optional iat/nbf, optional string jti. Use Spring timestamp validation with
60-second skew and injectable Clock for deterministic tests. Never echo raw tokens,
claims or parser exceptions. Reuse Spring's Jwt principal and
JwtAuthenticationToken; map roles to SimpleGrantedAuthority without prefix mapping.

`CallbackSecurityAutoConfiguration` runs after issuance and relevant servlet
security autoconfiguration, is servlet-only, imports properties and registers one
`@Order(Ordered.HIGHEST_PRECEDENCE)` chain. It must register alongside an application
chain, never back off merely because an application chain exists. Use stateless
sessions, NullSecurityContextRepository, NullRequestCache, no form/basic/logout
authentication, and CSRF disabled only on this chain. Preserve Spring's request
context cleanup. Disable URL/form token authentication. A small bearer resolver
rejects duplicate Authorization headers, combined/multiple bearers and any
`access_token` query parameter (including when a valid header is also supplied).
Missing or invalid credentials get a direct 401 with exactly a Bearer challenge;
write status/response directly, never sendError, login redirect or `/error` forward.
Application denials are direct 403. No endpoint annotation inspection.

GET and POST within the prefix require authentication; other methods are denyAll.
Concrete normal Spring policy: authenticated PUT/PATCH/DELETE/HEAD/OPTIONS get 403;
missing credentials normally get 401; default StrictHttpFirewall rejects TRACE
and malformed/unnormalized paths with 400 before chain authentication. Do not
replace or relax the global firewall. Invalid HTTP syntax rejected earlier by the
servlet container follows container handling. Pin reproducible firewall examples
in tests; do not promise application-chain control over connector parsing.

Reference app enables `@EnableMethodSecurity`, keeps its ordinary chain below SDK
precedence, and adds a separate `CallbackController` under the default prefix.
Use `@PreAuthorize("hasRole('ORDER_EDITOR')")`, then compare verified issuer/subject
with a fixed synthetic order's owner; return 403 for business denial. A callback
interceptor counts arrivals independently of method-body/effect counters, so method
denial proves zero invocation and expiry proves zero HTTP request. Expose only
fixed authenticated fixture counter/reset routes outside the prefix. Avoid the
existing ExecutionController catch-all exception handler turning callback denials
into 500 by keeping callback controller/error handling separate.

Local routes use explicit approved `caller-passthrough` targets at loopback app
port 8081. Give only the method-negative skill a permissive skill role allowing
the literal-role user to reach callback authentication; otherwise Framework would
deny before the method-security boundary. Preserve existing role-denial scenario.
Use fixed owned/other-owner record routes or bounded fixture inputs, never caller
identity from those inputs. Compare verified authentication against the initiating
user, and optionally compare credentials in-memory at the fixed capture boundary;
report only equality booleans, statuses and counters.

For callback expiry, add one short-lived planner skill with an allowed REST leaf.
The deterministic model enters a bounded barrier after admission, signals entry,
and only after the captured two-second token expires returns a CALL_TOOL step.
The leaf uses caller-passthrough to the app callback. Assert strict sender failure
`execution credential expired`, zero callback arrivals, and no business effects.
If the planner handles child failure, return a deterministic final result that
preserves the exact observed child failure; do not replace the required failure
with a mere generic parent failure. Never add sender timing hooks or sleeps in
production. Poll fresh app credentials and release barriers in finally blocks.

Implementation evidence refined this fixture: beta.8 auto-dispatches an assigned
zero-input leaf without asking for CALL_TOOL. Use a separate expiry-only leaf
requiring a fixed `message` input; the deterministic step model supplies that
input after release. This preserves the planned CALL_TOOL/admission oracle and
does not change the sender or use Framework internal contracts in application code.

## Phase 1: SDK callback trust and validation

### Changes

- [x] `sdks/java/spring-boot-starter/pom.xml` — add Boot-managed security config,
  web and OAuth2 resource-server/JOSE dependencies and security-test as needed.
- [x] `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackProperties.java`
  — bind and validate the three callback properties above.
- [x] Same directory `CallbackJwtTrust.java`, `CallbackJwtDecoder.java` — implement
  atomic fail-closed effective trust and strict independent validation.
- [x] `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/CallbackJwtTrustTest.java`
  and `CallbackJwtDecoderTest.java` — own keys, overlap state transitions, lifecycle,
  claim-shape/signature/issuer/audience/skew tests using generated keys and temp files.

### Automated verification

- [x] From Sidecar: `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am '-Dtest=CallbackJwtTrustTest,CallbackJwtDecoderTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  — all trust and decoder cases pass; no framework dependency introduced.

### Optional developer checks

None.

## Phase 2: Isolated servlet callback chain

### Changes

- [x] Starter source directory `CallbackSecurityAutoConfiguration.java` — chain,
  resolver, direct rejection handlers and exact-role authentication conversion.
- [x] `sdks/java/spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — discover callback configuration without changing issuance-only non-web contexts.
- [x] Starter tests `CallbackSecurityAutoConfigurationTest.java` — complete callback
  matrix with real FilterChainProxy, ordinary form/CSRF chain and method security.
- [x] Existing `ExecutionTokenAutoConfigurationTest.java` — discovery and startup
  behavior remain coherent with the new servlet-only configuration.
- [x] `sdks/java/README.md` — complete wiring/properties/precedence/audience/overlap
  examples, exact roles, methods/firewall responses and browser-session exclusion.

### Automated verification

- [x] From Sidecar: `mvn -B -ntp -f sdks/java/pom.xml clean install`
- [x] From Sidecar: `python sdks/java/tests/test_build_boundary.py`
  — complete SDK reactor/security tests and independent dependency boundaries pass.

### Optional developer checks

None.

## Phase 3: Real Local callbacks and composed authorization

### Changes

- [x] `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/SecurityConfiguration.java`
  — enable method security and explicit ordinary-chain lower precedence.
- [x] Same directory `CallbackController.java` — separate authenticated callbacks,
  verified owner checks and independent arrival/method/effect counters.
- [x] Same directory `ExecutionController.java` — fixed callback/planner submission
  allowlist and short-issuer selection; authenticated fixture observations.
- [x] `suite/scripts/sdk_lane/local.py` — fixed callback targets/REST leaves,
  planner skill/model configuration, SCENARIOS and strict RESULTS schemas.
- [x] `suite/scripts/sdk_lane/fixture.py` — bounded deterministic admitted planner
  barrier, in-memory credential handling and sanitized observations.
- [x] `suite/scripts/sdk_lane/local_probe.py` — authorized forwarding, missing role,
  business ownership and admitted callback-expiry assertions; ordinary login regression.
- [x] `suite/scripts/run_sdk.py` and `suite/tests/test_sdk_runner.py` — report stages,
  scenario completeness, failure sanitization, bounded barrier and cleanup regression.
- [x] `suite/docs/sdk-lane.md`, `suite/README.md`, `docs/verification.md` — current
  coverage and evidence limits; update canonical acceptance links if needed without
  marking unrun recipes or rotation work complete.

### Automated verification

- [x] From suite: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`
- [x] From suite: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
- [x] From suite: `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
- [x] From suite: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  — real consumer packaging, root clean verify once, complete Local scenarios,
  all baseline scenarios and unique-project cleanup pass. Preserve report path/hash.
  Do not separately repeat root clean verify after a passing unmodified composed run.

### Optional developer checks

None. Docker execution is required automated acceptance, not an optional check.

## Test Strategy

The companion testing plan specifies the complete component matrix and scenario
oracles. Use fixed clocks for time boundaries, controllable reader/ticker/scheduler
seams for trust races, temporary RSA/JWKS fixtures and real Spring security filter
execution. Keep invalid token permutations out of composed coverage. Run the
provider-free isolated Local recipe against actual sources; no business data mounts.

## Acceptance-Criteria Traceability

| Acceptance criterion | Implementation evidence | Executable test evidence |
| --- | --- | --- |
| Request table, multiple bearers/query/cookies; direct Bearer 401 | Callback chain/resolver/handlers | Complete servlet negative matrix; response/dispatch counters |
| Signature, iss, aud, typ, purpose, expiry and claim shapes | CallbackJwtDecoder | Parameterized raw JWT variants and fixed skew boundaries |
| No sessions; CSRF scoped; no fallback; context cleared | Dedicated stateless chain | Existing-session spy, no Set-Cookie, ordinary CSRF, consecutive requests |
| Configurable prefix; GET/POST; documented other methods | CallbackProperties and method rules | Override/boundary/startup cases, PUT/HEAD/OPTIONS 403, firewall 400 |
| Exact verified identity/authorities | JwtAuthenticationToken converter | Endpoint principal/issuer/subject/authority assertions |
| Own key without overlap | CallbackJwtTrust | Colocated and provided public-key acceptance |
| Configured unusable overlap withdraws and recovers | Atomic trust snapshot | Missing/malformed/empty/unreadable transitions reject own and old key; recovery |
| Removed overlap keys rejected | Complete replacement on reload | Removed old kid rejected with current own key still valid |
| Authorized forwarded callback | Reference callback and passthrough route | Local authenticated identity/exact credential equality and effect assertions |
| Method denial before invocation | Method security + negative skill admission | 403, arrival increment, method/effect counters unchanged |
| Business denial | Application owner check | Valid authorized callback reaches body then 403; no effect |
| Ordinary login/routes continue | Ordinary chain | Real form login, normal GET, CSRF-required POST alongside callbacks |
| Callback expiry without HTTP request | Existing sender + admitted planner barrier | Exact child failure, admission observation, zero arrival delta |
| Real Local invocation and callback/JWT/user permissions | Composed isolated runtime | Complete runner report from actual rebuilt sources |
| Sidecar JWKS-only trust both ConfigurationModes | Existing production file-trust ownership | Root ConfigurationModeIntegrationTest in composed build |
| SDK wiring/limits documented | SDK README + verification inventories | Review docs against component behavior and actual report |

## Risks and Rollback/Recovery

Changing the configured prefix requires moving application routes together; there
is one supported prefix and no legacy route adapter. Missing/invalid overlap stops
callback authentication until a complete valid file is restored; removal of the
overlap setting requires restart and is an operator decision, not automatic fallback.
Trust updates affect new authentication only, never cancel admitted work or replay
callbacks. On implementation failure preserve artifacts and report failed checks;
do not reset existing data or erase the failure. Disposable per-run SDK volumes
are removed only through the existing labeled-project cleanup.

## Implementation verification

All phase checkboxes are backed by the
[verification record](2026-10-18-pr-8-1-6-sdk-callback-authentication-verification.md)
and final twelve-scenario Local report, including actual rebuilt artifacts,
strict callback admission/expiry observations and independently empty cleanup
queries. Independent Step 5 review remains; no optional developer checks.

## References

- Ticket, research and design lens linked above.
- `sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeySource.java`
- `src/main/java/ai/loomspan/sidecar/security/CurrentFileJwtTrust.java`
- `src/main/java/ai/loomspan/sidecar/security/ExecutionJwtDecoder.java`
- `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java`
- `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-reference.md`
- `ai/thoughts/phases/2026-10-15-sdk-v1-signing-trust-reference.md`
- `ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md`
- `suite/AGENTS.md`, `suite/docs/project-agreement.md`, `suite/docs/sdk-lane.md`
