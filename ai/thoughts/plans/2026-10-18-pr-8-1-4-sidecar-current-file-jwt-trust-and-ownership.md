# Current-file JWT trust and ownership Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md`

- Research: `ai/thoughts/research/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md`

- Outcome: one authoritative current JWKS file, exact execution authorities, issuer/subject ownership, and strict credential lifetime at Framework handoff and callback send.

- Execution: pipeline mode, approved `full`; this document and its testing companion are Steps 2 and 3.

**Replace superseded development contracts directly; no aliases, compatibility adapters, parallel legacy APIs or migration machinery. Apply the [design lens](../design-lens.md).** No database reset is required: valid managed publications cannot contain the removed process settings. Replace operator process configuration and obsolete sample keys; do not delete accounts, publications, tokens, drafts or deployed data.

## Current State

- `security/SidecarJwtProperties` and `security/JwtSecurityConfiguration` select PEM, remote JWKS or discovery, require a configured issuer, and add a configurable role prefix. There is no reload owner or trust health contributor.

- `execution/ExecutionCoordinator.handoff` installs the captured authentication immediately before public `SkillInvocationHandoff.handoff`, without revalidation. The admission gate already owns queue reservations and retained records.

- `rest/GenericRestSkillHandler.handle` forwards the captured token for approved caller-passthrough routes. `RestTargetClients` already disables redirects and retries.

- `security/ExecutionOwner.from` uses `Jwt.getIssuer()`. Inspection of installed Spring Security 7.1.0 `JwtClaimAccessor` shows that accessor converts to URL. The SDK defaults to the plain string `loomspan-app`; ownership must use the validated exact string claim instead.

- `management/ManagementSecurityConfiguration` authenticates only management paths. `ManagementBearerFilter.required` is a closed PAT route map; all management principals have `MGT_VIEWER`. Public health currently goes through the stateless execution chain, so merely setting health detail visibility is insufficient.

- `application.yml` has a second listener on 9091 and no listener addresses. Quickstart uses separate namespaces and PEM files; production Caddy reaches `sidecar:8080` over a bridge network. Both must change to use colocated loopback correctly.

- Root build is Java 21, Spring Boot 4.1.0, Framework beta.8. Tests use JUnit/AssertJ/Mockito, local HTTP fixtures and temporary SQLite. Matching beta.8 public authorization documentation supports `GrantedAuthorityDefaults("")` for exact YAML REST skill roles.

Paths above are relative to `src/main/java/ai/loomspan/sidecar/` unless otherwise stated.

## Desired End State

Execution authentication uses only a complete validated current file snapshot. A detected unusable file immediately withdraws acceptance; recovery requires a usable current file, with no historical barrier. Execution roles remain literal strings. Polling uses current valid request authentication but never changes the execution's captured credential. Pre-handoff checks protect queued work; admitted work keeps its Framework lifetime. Each passthrough send refuses an expired captured token, independently of receiver skew.

Management retains its existing authority names, account/PAT/session semantics and CSRF protections. Health is publicly readable as status only; existing management credentials reveal trust details. Trust remains process/file configuration in both application ConfigurationModes.

## Scope

### In scope

- Trust property replacement, mode/listener validation, whole-file validation/reload, execution JWT validation, health visibility, exact roles and lifetime boundaries.

- Focused Sidecar evidence, affected fixtures and regression configuration, shipped sample topology, operational and portable authoring guidance.

### Out of scope

- SDK signing implementation, callback receiver redesign, remote key sources, management trust APIs/storage/UI, trust history and issuer registration.

- Framework changes or builds, TLS lifecycle changes, deployment data deletion, release publication and the composed PR 8.1.5/8.1.6/8.1.8 suite work.

- Preserving PR 8.1.2's obsolete external scenario (explicit ticket exception).

## Active Project Guardrails

- Supported `ai.loomspan.api` types only; retain the ArchUnit production-and-test boundary and no Java `@SkillMethod` skills. `RestSkillHandler` remains the only authorized outbound SPI.

- Framework owns skill authorization, nested execution, admitted lifetime and shutdown. Use standard Spring configuration for exact roles; do not reimplement Framework authorization.

- Keep one admission coordinator, immutable terminal record publication, captured identity outside business/model data, approved callback targets, redirects/retries disabled.

- Process trust is independent of database/file application ConfigurationMode. No signing, token refresh, trust ledger, stale fallback or replay.

- Prefer small immutable snapshots, one loader and existing Spring health/security facilities over a second configuration/lifecycle subsystem.

## Impact and Risk Analysis

- Library coercion can turn malformed numeric/string claim shapes into apparently valid claims. Validate decoded JSON shapes before Nimbus registered-claim conversion; never authenticate from this preliminary unverified data. Then use maintained Nimbus RS256 verification and standard timestamp/audience validation.

- Concurrent loading must never publish partial keys or let an older load overwrite a newer result. Serialize load attempts; readers take one immutable snapshot. An authentication overlapping withdrawal may finish using the snapshot it obtained; withdrawal becomes effective for subsequent checks, without cancellation.

- Poll-only metadata checks can miss replacement files with equal size/timestamps. Read current bytes every interval; compare content and state to avoid repeated parsing/logging when unchanged. Missing/unreadable checks must not be skipped merely because prior metadata matches.

- Colocated must cover the former packaged additional listener, removed by developer correction; Docker NAT cannot reach a loopback-only backend. Namespace sharing plus an explicit local ingress proxy preserves sample usability without weakening the Sidecar bind requirement.

- Health on the application listener must not accept execution JWTs as management identity, or expose paths/kids through anonymous component inclusion. Explicit management authority controls details and components.

## Implementation Approach

### Fixed configuration contract

- Replace `SidecarJwtProperties` with `security/SidecarTrustProperties` bound at `loomspan-sidecar.trust`: required `mode`, nested `colocated.key-directory`, nested `file.jwks`. Keep only the existing audience setting `loomspan-sidecar.auth.jwt.audience`, now defaulting to `loomspan-sidecar`, in a reduced `SidecarJwtProperties`.

- `mode` accepts exactly `colocated` and `file`. Missing mode error names the property, both choices and `examples/quickstart`. Selected mode requires its nonblank path; any populated other-mode setting fails startup. Paths are filesystem paths, never remote/resource URL key sources. Configuration validation fails startup; missing/bad file content starts waiting.

- Remove the six old properties, methods, bindings and default rows outright. They confer no behavior and have no aliases. No new obsolete-property compatibility scanner is needed.

- In colocated mode require explicit effective `server.address` resolving exclusively to loopback; reject absent/wildcard/non-loopback values. For a separately enabled management port (including random port 0), require explicit loopback `management.server.address`; same-port management inherits the main listener; `management.server.port=-1` has no listener to validate. Validate before serving requests using normal Boot configuration/lifecycle facilities. No custom connector discovery or runtime deployment allowlist.

### One trust owner

- Add `security/CurrentFileJwtTrust` (or equivalent single component) owning the resolved path, scheduler, serialized loader and volatile immutable snapshot: ready/waiting, key map, last actual load time. Load at construction/startup, close the daemon scheduler on context destruction.

- Fixed one-second polling interval; no new timing knobs. At each tick read the whole file, detecting replacement, permission/read failures and recovery. Unchanged usable bytes need no reparse/publication/log. Changed bytes or a state transition cause one complete validation attempt and one log line. Repeated identical waiting states need no repeated line; initial waiting line includes the path.

- Unknown nonblank `kid` requests may force one reload at most once per second globally per Sidecar, using monotonic elapsed time and the same load lock. No per-kid map, unbounded attacker-controlled cache or remote resolution. Re-read the current snapshot after the attempt. Scheduled checks remain independent so failed/unknown credentials cannot delay ordinary detection/recovery.

- Parse raw JSON object/keys array. Each item must be an object with exact RSA `kty`, nonempty unique string `kid`, valid nonempty base64url `n`/`e`, usable public RSA key and modulus >=2048 bits. Optional `use`/`alg` must have the required string values. Reject presence of every listed private member even if null. Ignore `x5c` before constructing Nimbus keys, so invalid/expired certificate text cannot influence trust. Ignore other unknown members.

- Construct the full immutable map only after all candidates pass. Empty/malformed/missing/unreadable snapshots contain no keys. Never retain a last-good decoder/cache that can continue authenticating after waiting is published.

- Log only mode, path, loaded kids, load time and ready/waiting with a safe reason category. Do not log raw file content, token text or exception messages that may echo private material. Unknown kid text itself is not logged as trusted metadata.

### JWT and lifetime validation

- `JwtSecurityConfiguration.sidecarJwtDecoder` delegates to the current trust owner. Validate raw JSON header/claims before registered-claim coercion: `alg=RS256`, required nonblank string `kid`, exact `typ`, exact string `token_use`, nonblank string `iss` and `sub`, `roles` JSON array of strings (empty allowed), `aud` string or nonempty string array including the configured audience, numeric representable `exp`. Present `iat`/`nbf` must be numeric representable timestamps and present `jti` must be a string. Do not add required issuance-age/lifetime/jti restrictions beyond ticket validation requirements. Accept fractional NumericDate values; never accept numeric strings, null required claims or coerced role values.

- Verify RS256 with the key selected from one current snapshot, ignoring token URLs/embedded key hints. Fixed 60-second receive skew through standard timestamp validation; no configured issuer comparison. An opaque nonblank issuer such as `loomspan-app` is valid. Preserve exact claim strings and original token bytes in the resulting Jwt.

- Authorities use fixed `roles` claim, empty prefix; expose `GrantedAuthorityDefaults("")` through standard Spring. Existing management `.hasAuthority("MGT_...")` rules remain unchanged.

- Add a small execution credential validation operation using the same decoder and injected `Clock`: require `now < captured exp` strictly, then re-decode captured bytes against current trust. `ExecutionCoordinator.handoff` invokes this immediately before public Framework handoff, after queue reservation release, while preserving its admission/shutdown gate. Use the captured authentication for invocation; do not replace it from poll requests or mint anything. Expiry throws an access-denied failure with exactly `execution credential expired before start`; other invalid/untrusted failures use a stable sanitized credential-invalid message. Existing completion machinery retains the failed record and releases task references/reservations.

- `GenericRestSkillHandler.handle` receives existing Clock bean and checks captured `exp` strictly immediately before passthrough request exchange. Missing exp fails closed. At equality or later throw `SkillException("execution credential expired")`; no network exchange occurs. Each send repeats the check, with unchanged approved-route and no-redirect policy. Trust withdrawal does not cancel admitted work or add Sidecar trust checks to callbacks; callback receivers validate their own trust.

- `ExecutionOwner.from` reads the already validated string `iss` rather than URL-converting accessor. Preserve exact pair equality and current not-found behavior for other owners.

### Health and management authentication

- Add `security/CurrentFileTrustHealthIndicator` with component name `executionTrust` and snapshot-only health; never perform filesystem I/O on health reads. Include mode/path/kids/lastLoadTime/state details. DOWN when waiting, UP when ready.

- In packaged `application.yml` include `readinessState,executionTrust` in readiness group; keep liveness independent. Configure health details/components `when_authorized` with `MGT_VIEWER` authority (using Boot's documented role handling), verifying real HTTP behavior rather than assuming a prefix. Do not set a packaged trust mode or listener address.

- Extend existing management chain matcher to health paths, permit their anonymous reads, and retain its account/session guard and PAT authenticator. Add GET health/readiness/liveness paths to `ManagementBearerFilter.required` as `read`; this reuses existing read PAT capability. Health credentials cannot fall into the execution JWT chain. Verify session validity/revocation and PAT scope/account checks apply. Avoid a parallel login or HTTP Basic mechanism.

## Phase 1: Current-file trust and authenticated health

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/security/SidecarTrustProperties.java`, reduced `SidecarJwtProperties.java`, `CurrentFileJwtTrust.java`, `CurrentFileTrustHealthIndicator.java` — implement the contract above, listener checks and one-second reload lifecycle.

- [x] `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java` — register trust properties.

- [x] `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java` — replace all legacy key sources/issuer/prefix/skew configuration with current-file decoder and literal authorities.

- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementSecurityConfiguration.java`, `ManagementBearerFilter.java`, `src/main/resources/application.yml` — management-authenticated health with public status and readiness contribution.

- [x] `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java`, `JwtSecurityConfigurationTest.java`, `JwtStartupIntegrationTest.java` — loader, token and real wiring tests.

- [x] `src/test/java/ai/loomspan/sidecar/support/JwtTestTokens.java` and all affected test context configuration — emit profile JWTs and write temporary JWKS, eliminating old settings. Keep tests independent of SDK build.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,JwtSecurityConfigurationTest,JwtStartupIntegrationTest,ManagementBearerFilterTest' test` — startup diagnostics, atomic/current trust, token negatives, health identity boundary pass.

### Optional developer checks

- None; all required trust behavior has local executable evidence.

## Phase 2: Exact ownership and lifetime boundaries

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/security/ExecutionOwner.java` — raw validated issuer string.

- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java` — current-file token revalidation and strict expiry before handoff, integrated with existing failure/cleanup flow.

- [x] `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java` — strict expiry before each passthrough exchange.

- [x] Replace `src/test/java/ai/loomspan/sidecar/execution/CustomRoleExecutionIntegrationTest.java` with `ExactRoleExecutionIntegrationTest.java`; update `AuthenticatedExecutionApiIntegrationTest.java`, `ExecutionCoordinatorTest.java`, `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java` — exact prefixed/unprefixed roles, ownership and captured-byte/lifetime evidence.

- [x] `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java` — same configured file governs trust in both application modes and is not a managed publication.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp '-Dtest=ExactRoleExecutionIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ExecutionCoordinatorTest,GenericRestSkillHandlerTest,ConfigurationModeIntegrationTest,ManagementLockedExecutionIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — no handoff/send at strict expiry, no cancellation of admitted work, ownership and public Framework role behavior pass.

### Optional developer checks

- None.

## Phase 3: Coherent samples, guidance and full regression

### Changes

- [x] `docs/operations.md`, `docs/setup.md`, `agent-skills/loomspan-sidecar-authoring/references/integration.md` — exact properties/defaults, file rules/failure table, one-second detection and unknown-kid cap, authenticated health, lifetime consequences, removed settings and no database reset. Include developer machine and documented Kubernetes emptyDir/fsGroup examples, deployment-created permissions, sole writer/restart implications.

- [x] `examples/quickstart/compose.yaml`, new `examples/quickstart/Caddyfile`, `examples/quickstart/host/host.py`, `host/Dockerfile`, `sidecar/rest-routes.yaml`, quickstart README/invocation configuration — select colocated; host uses `network_mode: "service:sidecar"`, shared pre-created named key volume, compatible account IDs and read-only Sidecar mount. Host writes a profile-compatible public JWKS atomically from a fresh test/demo signing key and binds callbacks/planner to localhost. Remove checked-in sample private/public PEM files. Keep demo private material in ephemeral fixture storage only; this is no new signing product.

- [x] Quickstart ingress Caddy shares Sidecar namespace and proxies ingress `18080` to `127.0.0.1:8080` for execution, management and health, and `18081` to `127.0.0.1:8081`. Namespace owner `sidecar` publishes host-loopback configurable API/host ports to those ingress ports; do not map Docker ports directly to loopback-bound backend ports. Use UID/GID `10001:10001` for the demo app and Sidecar; document deployment commands to pre-create the key volume and set its directory ownership/mode (app writable, Sidecar read-only mount). Host/proxy depend on Sidecar being started, never healthy, because trust readiness waits for host export; avoid circular health dependencies. Preserve documented browser/login/invoke workflow through ingress. Proxy configuration contains no execution credential rewriting.

- [x] `examples/production/compose.yaml`, all `examples/production/Caddyfile.*`, env example and README — shipped default selects colocated and mounts operator-created shared key directory read-only. Caddy joins Sidecar namespace, reaches `127.0.0.1:8080`, and publishes its ingress ports via the namespace owner; explicitly loopback-bind the Sidecar application listener. Document the sole local application writer and namespace requirement; remote/multiple applications use documented file mode instead of weakening loopback. Keep existing TLS modes and operator certificate ownership.

- [x] `scripts/verify-image.py`, `verify-production.py`, `verify-production-tls.py` and affected script fixtures — adapt property names, profile tokens, runtime JWKS generation/mounts, localhost targets, namespace/port assertions and temporary key-directory ownership. Keep disposable fixtures local; do not retain PEM aliases. Production recovery fixtures must create their own JWKS directories and app namespaces rather than rely on removed checked-in keys.

- [x] `src/test/java/ai/loomspan/sidecar/config/ConfigurationReferenceTest.java` — recurse nested trust properties, assert no packaged mode/obsolete defaults, exact docs rows and loopback/shared namespace samples.

### Automated verification

- [x] `python scripts/sidecar_version.py check`

- [x] `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v`

- [x] `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`

- [x] `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`

- [x] `.\mvnw.cmd -B -ntp verify` — full root suite including management/browser/architecture regression; install local Playwright Chromium using CI's command if absent.

- [x] `python scripts/test_verify_shutdown.py`

- [x] With Docker available: `docker build --tag loomspan-sidecar:pr-8-1-4 .`, `python scripts/verify-image.py --image loomspan-sidecar:pr-8-1-4`, `python scripts/verify-production.py --image loomspan-sidecar:pr-8-1-4`. These are affected sample verification with disposable local services; absence of Docker must be reported explicitly, not as a pass.

### Optional developer checks

- Real deployment file permissions and operator TLS material are environment-specific observations; no live mail/model/public CA calls are required. Kubernetes is documentation only. Existing exhaustive TLS lifecycle checks are not expanded by this ticket.

## Test Strategy

Use deterministic loader/decoder units for the key/claim matrix, atomic snapshot and reload limiter; real Sidecar HTTP for mode wiring, health and management separation; public Framework integration for exact roles and queue/ownership behavior. Use mutable clocks and latches for strict boundaries instead of long token sleeps. Ensure at least one production-scheduler recovery assertion completes within the documented one-second interval plus bounded test scheduling tolerance. Full details and exits live in the testing companion.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |

| --- | --- | --- |

| File trust in both ConfigurationModes | Trust bean independent of managed publication | ConfigurationModeIntegrationTest file replacement in each mode |

| Missing/wrong mode, path and bind startup failures; no packaged mode | SidecarTrustProperties/listener validation; application.yml | JwtStartupIntegrationTest, ConfigurationReferenceTest |

| Every key rule, whole-file rejection, ignored x5c | CurrentFileJwtTrust raw validator | CurrentFileJwtTrustTest parameterized key matrix |

| Missing/unreadable/malformed/empty withdraw; automatic recovery | Complete empty waiting snapshots and polling | Loader state tests and HTTP readiness/recovery |

| Atomic publication, limited unknown-kid reload | Serialized loads, immutable volatile snapshot, monotonic limiter | Concurrent readers/key swaps and forced reload count |

| Signature/profile/audience/claim rejection; no expected issuer | Profile decoder/current keys | JwtSecurityConfigurationTest raw payload matrix and SDK default issuer |

| Removed configuration; management separation/regression | Reduced properties, unchanged management semantics | ConfigurationReferenceTest, full management suite, execution/PAT/session separation |

| Health visibility and safe load logging | executionTrust health, management health matcher | Same-listener anonymous/session/PAT/expired credential HTTP checks; captured safe logs |

| Exact roles and public boundary | Empty prefix Spring bean and roles converter | ExactRoleExecutionIntegrationTest and ArchUnit |

| Fresh owner poll, different owner denial | Exact string ExecutionOwner pair | AuthenticatedExecutionApiIntegrationTest |

| Expired/withdrawn queue denied; admitted continues | Handoff credential validation | Coordinator latch/clock tests and public integration |

| Expired callback sends nothing; unchanged valid token/no redirects | Handler strict exp plus existing target/client policy | GenericRestSkillHandlerTest and authenticated integration |

| Samples/docs meet colocated/local guidance | Shared namespace/volume/ingress, references | ConfigurationReferenceTest, Python guidance checks, Docker sample verifiers |

## Risks and Rollback/Recovery

Correct a waiting JWKS atomically to recover automatically. Restoring old bytes deliberately restores those keys; no withdrawal ledger exists. Validating snapshots does not promise instantaneous cross-process withdrawal or revoke already admitted Framework work. Queue time consumes captured lifetime and fresh polling cannot extend it. For a process upgrade, replace obsolete external settings and prepare the new directory/topology; a binary rollback also requires its matching external configuration. No schema or development-data reset is required and no deployed data deletion is authorized.

## References

- Ticket and research above; [design lens](../design-lens.md).

- `ai/thoughts/phases/2026-10-15-sdk-v1-signing-trust-reference.md`, `2026-10-15-sdk-v1-identity-callback-reference.md`, `2026-10-15-sdk-v1-deployment-recipes.md` and focused acceptance matrix.

- `C:/opendev/code/loomspan-framework` tag `v1.0.0-beta.8`, `agent-skills/loomspan-docs/references/skill-authoring/authorization.md`: documented standard Spring empty prefix.

- Installed Spring Security 7.1.0 sources: `JwtClaimAccessor.getIssuer`, `MappedJwtClaimSetConverter.withDefaults`; source evidence for raw claims and opaque issuer handling.

## Implementation decisions and verification

- The planned red command `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest' test` failed test compilation because `CurrentFileJwtTrust` was absent (recorded in `target/pr814-red.log`). An initial test-fixture compile mistake was corrected before recording that isolated missing-type result.

- Spring Security 7.1 `JwtAuthenticationConverter` unconditionally adds `FACTOR_BEARER`. Use its supported `Converter<Jwt, AbstractAuthenticationToken>` extension with `JwtGrantedAuthoritiesConverter` and `JwtAuthenticationToken` to preserve only the signed role authorities. No Framework extension is involved.

### Developer correction: one application listener

The developer resolved the escalation on 2026-10-18: "There is not supposed to be separate health endpoint. that must have been a mistake in the plans." The orchestrator verified that current packaged application.yml explicitly sets management.server.port: 9091. Remove that separate listener from shipped configuration and samples; serve existing health endpoints on the application listener. Correct the plan, tests, documentation and verifiers accordingly, prove existing management browser-session and read-PAT health details on that listener, and rerun affected checks plus full verification. Do not introduce shared-session infrastructure or preserve the separate-listener sample topology. This supersedes the pending proposal below; retain earlier observations only as historical verification evidence.

### Superseded health-session plan correction

Expected: matching the existing management security chain on health would make an existing application-listener browser session available on a separate Actuator listener. Found: a real read PAT reveals `executionTrust` details on the separate management port, but the same valid browser session returns anonymous status there (`ManagementPersonalTokenHttpIntegrationTest.trustHealthDetailsRequireCurrentManagementCredentials`, first Phase-2 run, line 80). Boot creates a separate servlet context/session manager for that listener. The application-listener session is valid for normal management requests.

Why it matters: sharing the security filter chain does not share servlet sessions. Making existing cookies resolve across listeners needs an explicit shared-session design (for example Spring Session), which adds a session subsystem beyond the approved plan. No such subsystem has been introduced.

Superseded proposal (not selected; see developer correction above): retain separate-listener public status and read-PAT details; document that browser-session health details require the same application listener and prove that same-port configuration. Preserve packaged port 9091 and current management account/session/PAT semantics. Alternative: explicitly plan and test shared-session infrastructure if cross-listener browser sessions are mandatory. Independent loader/JWT/lifetime/sample work continues safely.

### Earlier Step-4 verification ledger (before developer single-listener correction)

- EXPECTED RED: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest' test` — missing production trust type, isolated after fixing the initial test fixture compile mistake (`target/pr814-red.log`).

- PASS: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,JwtSecurityConfigurationTest,JwtStartupIntegrationTest,ManagementBearerFilterTest' test` — 14 tests, zero failures/errors (`target/pr814-phase1.log`). Both trust modes recovered through the real production scheduler, allowing four seconds of bounded scheduling tolerance; ordinary interval is one second.

- INITIAL FAIL: `.\mvnw.cmd -B -ntp '-Dtest=ExactRoleExecutionIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ExecutionCoordinatorTest,GenericRestSkillHandlerTest,ConfigurationModeIntegrationTest,ManagementLockedExecutionIntegrationTest,SupportedLoomspanApiArchitectureTest,ManagementPersonalTokenHttpIntegrationTest' test` — 70 tests, one cross-listener session failure and three fixture errors (`target/pr814-phase2.log`). Duplicate command-line JWKS path and missing synthetic expiry were corrected. The session decision is intentionally unresolved.

- INITIAL FAIL: `.\mvnw.cmd -B -ntp verify` — 261 tests, 12 failures, one error, three normal environment-gated production-browser skips (`target/pr814-verify.log`). Eleven failures and the error were stale default assertions and synthetic unsigned coordinator credentials; all those areas were corrected and passed in the focused rerun below. The remaining health-session assertion is pending developer decision. Full verify must run again after resolving that decision.

- PASS: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,JwtSecurityConfigurationTest,ExactRoleExecutionIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ExecutionCoordinatorTest,GenericRestSkillHandlerTest,ConfigurationModeIntegrationTest,CredentialPortabilityIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,RestHandlerLifecycleIntegrationTest,SidecarDefaultsTest,ConfigurationReferenceTest,SupportedLoomspanApiArchitectureTest' test` — 78 tests, zero failures/errors/skips (`target/pr814-focused-final.log`).

- PASS: `.\mvnw.cmd -B -ntp '-Dtest=AuthenticatedExecutionApiIntegrationTest' test` — 15 tests including newly added real current-file withdrawal of queued work while already admitted work completes, fresh owner polling, and unchanged callback bytes (`target/pr814-current-queue.log`).

- PASS: `.\mvnw.cmd -B -ntp '-Dtest=ExecutionCoordinatorTest' test` — 15 tests; includes expiry during decoding, with a second strict clock check immediately before public Framework handoff (`target/pr814-strict-handoff.log`).

- PASS: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,JwtSecurityConfigurationTest,ExecutionCoordinatorTest' test` — 24 tests (`target/pr814-final-boundaries.log`).

- PASS: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,JwtSecurityConfigurationTest,ExecutionCoordinatorTest' package` — 24 tests plus final jar packaging (`target/pr814-final-package.log`). Whole-file validation also rejects malformed UTF-8 rather than silently replacing invalid bytes.

- PASS: `python scripts/sidecar_version.py check`; `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v` (7 tests); `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` (6 tests, rerun after fixing an escaping documentation link by adding self-contained `references/execution-trust.md`); `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` (11 tests); `python scripts/test_verify_shutdown.py` (2 tests, rerun after namespace/volume fixture updates).

- PASS: `python -m py_compile scripts/verify-image.py scripts/verify-production.py scripts/verify-production-tls.py scripts/verify-shutdown.py examples/quickstart/host/host.py scripts/fixtures/shutdown-host.py`; `git diff --check`.

- PASS: `docker info --format '{{.ServerVersion}}'` — daemon 29.8.1; `docker compose -f examples/quickstart/compose.yaml config --quiet`; `docker compose --env-file examples/production/production.env.example -f examples/production/compose.yaml config --quiet`; `docker compose -f examples/quickstart/compose.yaml build host`.

- PASS: `.\mvnw.cmd -B -ntp '-DskipTests' package` and `docker build --tag loomspan-sidecar:pr-8-1-4 .` — rebuilt again after final boundary hardening (`target/pr814-final-image-build.log`).

- PASS: `python scripts/verify-image.py --image loomspan-sidecar:pr-8-1-4` — initial colocated image/ingress/export/readiness/JWT/setup/recovery run with cleanup (`target/pr814-verify-image.log`); final-image repetition passed (`target/pr814-verify-image-final.log`).

- FAIL (historical topology): `python scripts/verify-production.py --image loomspan-sidecar:pr-8-1-4` — completed startup/browser/management/execution/import/rollback/restore checks, then failed owner-only restart before manual reset; see the single-listener/lifecycle correction below (`target/pr814-verify-production.log`).

- NOT RUN: full `python scripts/verify-production-tls.py --image loomspan-sidecar:pr-8-1-4`; the plan requires syntax/static coherence, not expanding this ticket into TLS certificate lifecycle testing.

- NOT RUN: external PR 8.1.2 composed scenario, intentionally excluded by ticket Pipeline notes; no live providers, mail, CA, release or deployed-data operations.

Production verifier fixtures now create independent public-key directories, share the app/Sidecar namespace, reacquire tokens after ephemeral signer restarts, and preserve destination URL-binding checks through a distinct localhost URL. Shutdown fixtures similarly prepare/clean a unique public-key volume and export a fresh key. Only disposable local fixture state is created or removed. Operator ACL/UID/fsGroup and real TLS observations remain optional/unperformed. No schema reset, Framework build, release publication or Git commit was performed.

Remaining work after the developer single-listener correction: finish updated full verification and Docker reruns, then perform independent Step 5 review. The earlier health-session proposal is superseded; no developer question remains.

### Single-listener implementation update

The developer correction is implemented: packaged configuration and Docker expose only application port 8080; health remains at `/actuator/health` and probe paths on that listener. Quickstart ingress has API/management/health on 18080 and fixture host on 18081; the old 19091 mapping and verifier management-port options are removed. Production and direct-TLS guidance use the application listener. Existing management session/PAT validation controls detailed health without new session storage. Standard Boot operator overrides still retain colocated loopback validation for any explicitly configured additional listener, but no additional listener is shipped.

` .\mvnw.cmd -B -ntp '-Dtest=JwtStartupIntegrationTest,ManagementPersonalTokenHttpIntegrationTest,ManagementEndpointIntegrationTest,ConfigurationReferenceTest,SidecarDefaultsTest' test` passed 26 tests (`target/pr814-one-listener.log`). Full verification will include additional expired-session/PAT health assertions.

The first production Docker run completed all startup, browser, management, REST/model, independent-destination import, rollback and stopped database restore checks, then failed its manual-reset page wait after restarting only Sidecar (SSL EOF; `target/pr814-verify-production.log`). Cleanup completed. The cause was a stale shared network namespace in app/proxy containers. The remaining owner-only restart was replaced by recreation of Sidecar and every namespace participant. Operator backup/restart guidance and shutdown fixtures follow the same lifecycle. Shutdown post-exit observations use the host container's loopback directly because published owner-container ingress can disappear on exit. These are sample-topology corrections within the approved colocated scope. Final Docker reruns remain required.

### Final single-listener verification

- PASS: `.\mvnw.cmd -B -ntp verify` — 264 tests, zero failures/errors, three normal environment-gated production-browser skips (`target/pr814-verify-one-listener.log`). Browser-gated scenarios are exercised explicitly by the Docker production verifier. This includes expired read-PAT, disabled-account and idle-session health denial on the application listener.
- PASS: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,GenericRestSkillHandlerTest,RestHandlerLifecycleIntegrationTest' package` — 23 tests and final jar (`target/pr814-single-listener-package.log`). Final targeted adjustments reject blank/null unknown kids without forced reload, prove passthrough redirects never send to a new recipient, and preserve the child-context shutdown-ownership regression using an ordinary Spring child context rather than an obsolete health web child. An initial focused attempt exposed that old test assumption; the corrected rerun passes.
- PASS: `docker build --tag loomspan-sidecar:pr-8-1-4 .` — final single-listener image (`target/pr814-single-listener-image-build.log`).
- PASS: `python scripts/verify-shutdown.py --image loomspan-sidecar:pr-8-1-4 --validate-only`; `python scripts/test_verify_shutdown.py` (2 tests); `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` (6 tests); both Compose config checks, version metadata and Python syntax checks after the listener correction.
- PASS: `python scripts/verify-image.py --image loomspan-sidecar:pr-8-1-4` (`target/pr814-verify-image-one-listener.log`) and `python scripts/verify-production.py --image loomspan-sidecar:pr-8-1-4` (`target/pr814-verify-production-one-listener.log`) in independent disposable projects. The production run completed HTTPS/browser/management/REST/model/destination import/rollback/restore/offline-reset cases and both real SIGTERM completion (2.43 seconds) and Framework cutoff (4.57 seconds); cleanup completed.

No developer question remains. Required Docker reruns are complete; independent Step 5 review follows Step 4.

The final scheduler test now waits for snapshot-only readiness before submitting authenticated requests after file recovery. This proves ordinary one-second polling recovers without an unknown-kid-triggered reload masking the observation. The loader unit also signs a token for a newly added kid and proves a globally permitted forced read immediately adopts that key. These strengthen the planned reload evidence without changing production behavior.

All ticket acceptance criteria have implementation and executable evidence through the traceability table, focused/full Maven results and final Docker verifiers above. Active configuration has no obsolete execution-JWT binding/alias or shipped separate health listener. No secrets or live/deployed data entered the diff; only disposable local verification fixtures were created or deleted. Optional operator ACL/UID/fsGroup and real TLS-material observations remain unperformed/nonblocking.

- PASS: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,JwtStartupIntegrationTest' test` — 9 tests, zero failures/errors/skips (`target/pr814-scheduler-final.log`), including snapshot-only scheduler recovery and newly added unknown-kid adoption.
- PASS: final `git diff --check`; no disposable production/shutdown containers remain.
