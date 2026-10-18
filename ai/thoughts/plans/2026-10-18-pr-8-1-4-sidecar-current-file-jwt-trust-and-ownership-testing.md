# Current-file JWT trust and ownership Testing Plan

## Change Summary

Step 3 of the approved Full pipeline, continuing in the Step 2 context. Governing inputs: [ticket](../tickets/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md), [research](../research/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md), and [implementation plan](2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md), all read completely. Apply the [design lens](../design-lens.md): destructively replace obsolete contracts, use public Framework APIs only, preserve one admission owner and captured credentials outside business data. No database reset or deployed data deletion is required or authorized.

The change replaces static/remote key selection with current-file snapshots, exact roles and reload readiness. It adds strict captured-token expiry before Framework handoff and before every callback, retaining current issuer/subject owner checks and management semantics.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Trust/configuration | Wrong source, mixed modes, wildcard second listener | Bound startup tests, both application modes, no packaged mode |
| File integrity | Partial valid file, stale fallback, ignored changes | Raw-key matrix, no-last-good state transitions, equal-metadata replacement |
| Concurrency | Partial snapshot, stale overwrite, unknown-kid flood | Atomic reader/load race and global limiter tests |
| JWT profile | Library coercion, wrong purpose, token URL key source | Signed raw-payload negatives, local HTTP trap counter |
| Identity | Prefix changes authorization, opaque issuer rejected | Real Framework exact-role invocation and plain issuer ownership |
| Lifetime | Queue/skew sends expired token or replaces captured credential | Mutable-clock/latch queue and zero-request callback tests |
| Health | Anonymous path/kid leaks or execution JWT gains details | Real HTTP management session/PAT/anonymous visibility matrix |
| Samples | Docker loopback NAT unreachable or readiness deadlock | Static composition assertions and disposable image/production verifiers |

## Existing Coverage and Environment Constraints

- `JwtSecurityConfigurationTest` and `JwtStartupIntegrationTest` currently assert obsolete PEM/discovery/configured issuer/prefix/skew behavior; replace those assertions, not their contracts with aliases.
- `ExecutionCoordinatorTest` already has clock/latch hooks, queue budgets, context capture, shutdown and generation evidence. Extend existing constructors/fixtures for credential validation rather than duplicate the whole coordinator suite.
- `AuthenticatedExecutionApiIntegrationTest` exercises public Framework validation, nested work, ownership, captured bytes and queue expiry; replace its expectation that expired queued work reaches a receiver's 401.
- `CustomRoleExecutionIntegrationTest` proves an intentionally removed groups/APP_ mode. Replace with exact-role integration against public Framework behavior.
- `GenericRestSkillHandlerTest` has passthrough, approved target and redirect fixtures. Add exp to synthetic Jwt fixtures, inject existing Clock, and use local request counts.
- `ConfigurationModeIntegrationTest` starts real file/database contexts with temporary SQLite. Existing management HTTP/session/PAT/locked-file suites prove credential boundaries and must continue under new trust setup. `SupportedLoomspanApiArchitectureTest` imports production and test classes and remains mandatory.
- Root Maven uses Java 21, Surefire 3.5.4, JUnit Jupiter/AssertJ/Mockito and Playwright. Root `verify` includes browser tests; CI's Chromium installation command is available if needed. No framework rebuild, SDK dependency, production account, live provider, real mail service, public CA or external trust endpoint is required.
- Tests use temporary filesystem directories and sanitized RSA fixtures, localhost JDK HTTP servers and deterministic model fixtures. Docker verifiers create disposable local containers/volumes with generated fixture material. If Docker is unavailable, record the missing sample verification explicitly; do not substitute a claim that samples work.
- Planning inspected production paths, root POM/CI, the listed tests, sample files and installed Spring Security sources. No tests were run during planning.

## Failing Test First

- Name: `currentFileWithdrawalRejectsPreviouslyAcceptedTokenAndRecovers`.
- Type: decoder/trust unit test.
- Location: `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java`.
- Arrange/Act/Assert: write valid fixture JWKS and authenticate a signed profile token; atomically replace it with `{"keys":[]}` and drive one ordinary reload; assert the same token fails and trust is waiting; replace with original valid bytes, reload and assert acceptance/ready returns. No live services or long sleeps.
- Expected pre-fix failure: current production has no file trust owner and no way to withdraw cached/static keys when the file becomes empty. Implement the new contract's test first; a missing new production type is an initial compile failure, followed by a behavioral failure while the initial API skeleton exists. Record the actual red command/result, not an invented baseline pass/fail.

## Tests to Add or Update

### 1. `wholeCandidateValidationAndStateTransitions`

- Type: parameterized unit tests.
- Location: `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java`.
- Proves: every file/key rule and fail-closed recovery, with no partial file or last-good acceptance.
- Inputs/fixture: top-level non-object/missing keys/non-array; malformed JSON; keys containing non-object; non-RSA; missing/wrong/empty n or e; sub-2048 modulus; missing/blank/non-string/duplicate kid; wrong or null use/alg when present; each private member present including null. Pair each invalid key with a valid key and assert zero published keys.
- Doubles or boundary isolation: actual temporary files for ordinary I/O; deterministic read failure (injected IOException at the file read boundary if required on Windows) for unreadable rather than relying on root/ACL-dependent chmod. No Framework doubles required.
- Edge cases: valid empty file; nonexistent file/directory; valid 2048+ keys; multiple keys; kid differs from thumbprint; invalid/expired-looking x5c and unknown values ignored; restoring identical old bytes after an error recovers; every detection replaces previous valid snapshot with no keys; repeated unchanged probes do not repeatedly log or parse.

### 2. `atomicReloadAndBoundedUnknownKidRefresh`

- Type: concurrency unit tests plus one bounded real-scheduler integration observation.
- Location: `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java` and `JwtStartupIntegrationTest.java`.
- Proves: readers observe whole old/new snapshots; scheduled file changes/recovery are not starved by unknown-kid traffic; unknown kid reloads are globally limited to one per second.
- Inputs/fixture: alternating two distinct complete multi-key files; same-length replacement with preserved mtime; missing-to-valid file; absent kid then atomic addition of that key.
- Doubles or boundary isolation: controllable monotonic ticker/read counter and latch around file load for deterministic races; real disk writes and real production scheduler for at least one HTTP 401/503 to 200/ready recovery. Close all schedulers/contexts.
- Edge cases: concurrent unknown kids cannot cause duplicate forced loads; retry only after one second; known kid causes no unknown-kid load; forced unknown load discovering malformed file immediately withdraws known keys; invalid tokens cannot create a per-kid cache. Reader assertions compare complete key maps, never require two separate decode calls to share a snapshot. An already-running decode may complete against its captured prior snapshot.
- Timing: poll with bounded deadlines; production recovery expected after the next one-second tick, allow small explicit CI scheduling tolerance and report it. Use latches/ticker for exact limiter assertions rather than sleeps.

### 3. `profileValidationRejectsMalformedRawClaims`

- Type: signed decoder unit tests.
- Location: `src/test/java/ai/loomspan/sidecar/security/JwtSecurityConfigurationTest.java`, shared signer in `src/test/java/ai/loomspan/sidecar/support/JwtTestTokens.java`.
- Proves: trusted RS256 signature and exact execution profile, raw shape checks before coercion, exact audiences/roles, fixed receive skew, no issuer comparison or token-directed key fetch.
- Inputs/fixture: helpers sign raw JSON maps and explicit JOSE headers, including deliberately malformed registered claims. Required claims: nonblank string iss/sub, numeric exp, aud string or string array containing own audience, exact token_use, roles string array. Header requires RS256/nonblank kid/exact typ.
- Doubles or boundary isolation: current-file trust with fixture public keys; local HTTP trap in jku/x5u/issuer claims, assert zero requests. Private keys remain only test fixtures/signing inputs and are never emitted in assertion/log output.
- Edge cases: bad/tampered signature, untrusted key with same kid, missing/unknown kid, wrong algorithm/typ/use/audience; null/numeric/array/object iss/sub; null/missing/string/mixed/numeric roles; numeric/null/mixed audience; exp string/boolean/null/object/out-of-range; present malformed iat/nbf/jti; valid fractional dates; expiry >60 seconds ago fails; just within receive skew succeeds; valid nbf skew/future rejection; extra audiences accepted; empty roles accepted; opaque `loomspan-app` and arbitrary alternate signed issuer accepted. No admission-age cap or requirement for omitted optional issuance claims is introduced.

### 4. `boundTrustModesListenersAndHealth`

- Type: application startup and real HTTP integration.
- Location: `src/test/java/ai/loomspan/sidecar/security/JwtStartupIntegrationTest.java`; related PAT route checks in `src/test/java/ai/loomspan/sidecar/management/ManagementBearerFilterTest.java`.
- Proves: explicit trust mode, mode-only settings, all Sidecar listener loopback rules, startup waiting versus configuration failure, automatic health/readiness and management-only details.
- Inputs/fixture: random ports, temporary database/JWKS and fixture management account/PAT/session. Start each application with only intended properties; do not hide a mode in a global test/default application.yml.
- Doubles or boundary isolation: real Spring contexts and localhost HTTP. Capture trust logger to assert a load/reload line contains mode/path/kids/time/state and omits token bytes/private members and raw parser exceptions. No provider discovery.
- Edge cases: missing mode error contains full property/options/quickstart; unsupported mode; no selected path; other-mode property set; colocated absent/wildcard/nonloopback main address; separate management wildcard/missing address rejected; separate loopback accepted; same-port and disabled management inherited/absent correctly. File mode permits nonloopback bind and applies same loader rules.
- HTTP matrix: anonymous health root/readiness/liveness shows status only; valid existing management session and read PAT show executionTrust fields; revoked/expired/disabled-account session/PAT has no details; execution JWT cannot grant management details; ordinary management credentials still fail `/v1/**`. Readiness goes DOWN/503 on unusable trust, UP/200 on valid recovery; liveness stays UP. Exercise health on the same application listener as management, following the developer single-listener correction.

### 5. `configuredJwksGovernsBothApplicationModes`

- Type: focused Spring wiring/integration.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java`.
- Proves: file and database ConfigurationMode instantiate the same process trust owner/path. Database publication cannot substitute trust or preserve a removed key.
- Inputs/fixture: parameterized application mode, one valid key/file and then withdrawn/replacement keys. Database mode may restore an ordinary skill publication; file mode uses ordinary file fixtures.
- Doubles or boundary isolation: temporary SQLite and files; public JwtDecoder authentication and managed publication inspection.
- Edge cases: publication activation does not change trust path; replacing the file changes accepted keys in each mode; no trust table/API or publication field added. Existing file-mode read-only and database restore assertions remain.

### 6. `exactAuthoritiesAndOwnerIdentity`

- Type: real HTTP + public Framework skill integration.
- Location: replace `src/test/java/ai/loomspan/sidecar/execution/CustomRoleExecutionIntegrationTest.java` with `ExactRoleExecutionIntegrationTest.java`; extend `AuthenticatedExecutionApiIntegrationTest.java`.
- Proves: JWT role literal matches the same skill `rbac_roles`; Framework consumes the supported empty-prefix bean; exact signed issuer/subject owns retained executions.
- Inputs/fixture: REST skills requiring `ROLE_ORDER_EDITOR` and `ORDER_EDITOR`; tokens with each literal, double-prefix variant and unrelated roles. Token issuer includes `loomspan-app`. Use fixture local receiver/model and current-file trust.
- Doubles or boundary isolation: no mocked Framework role evaluator or internal imports. Assert allowed submission actually completes to prove handoff evaluation, and denied variant never invokes receiver.
- Edge cases: discovery and submit authorization align; fresh token for same pair polls completed/queued work; different subject and same subject/different issuer return not-found; invalid/withdrawn polling token is rejected before owner lookup. Capture original callback bytes after fresh-token polling and assert they remain the submitted token. Management `MGT_*` authorities remain unchanged.

### 7. `queuedCredentialRevalidationAndAdmittedLifetime`

- Type: deterministic coordinator unit tests and focused authenticated integration.
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java` and `AuthenticatedExecutionApiIntegrationTest.java`.
- Proves: strict expiry/current-trust at the final Sidecar handoff boundary, no worker/reservation leak, captured authentication preserved, admitted work not cancelled.
- Inputs/fixture: one worker blocked after admission; second queued task with original profile token; advance mutable clock to exp exactly or withdraw key/file; release first worker and inspect terminal record/Framework handoff count.
- Doubles or boundary isolation: mock only supported `SkillInvocationHandoff`/`AdmittedSkillInvocation` in units; real public Framework in integration. Use latches, not token-duration sleeps.
- Edge cases: exp exactly now and slightly past while within 60-second receive skew fails with exact `execution credential expired before start`; untrusted key/empty/malformed trust fails without Framework handoff; valid queued token proceeds unchanged; queue count/bytes released and references cleared after failure; subsequent valid task runs. Withdraw trust after first public admission and let it complete without callbacks to demonstrate no cancellation. Poll with fresh owner token never renews queued credential. Preserve shutdown/generation/context-restoration tests.

### 8. `passthroughExpiryBeforeEveryExchange`

- Type: handler unit/local HTTP tests; one nested Framework integration regression.
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java` and `AuthenticatedExecutionApiIntegrationTest.java`.
- Proves: at strict expiry no HTTP request occurs; successful approved send uses exact captured bytes; each callback checks independently.
- Inputs/fixture: mutable Clock, captured JwtAuthenticationToken with exp in future/equal/past/missing, local counting receiver; two sequential callbacks with clock advanced between them.
- Doubles or boundary isolation: real local HTTP exchange for send/no-send counts; no real application or credentials. Expired failure is exact `execution credential expired` as a SkillException/skill failure, not a receiver 401.
- Edge cases: within-skew expiry still sends nothing; first callback succeeds then second fails without another request; caller-passthrough requires JWT; fixed/no-auth target behavior stays unchanged; redirect fixture destination receives zero requests and original token never reaches a redirected recipient. Existing approval/target-boundary tests continue.

### 9. `configurationAndSampleContractRegression`

- Type: existing reference/unit checks, full regressions and disposable local Docker verifiers.
- Location: `src/test/java/ai/loomspan/sidecar/config/ConfigurationReferenceTest.java`; existing management/architecture suites; `scripts/verify-image.py`, `verify-production.py`, `verify-production-tls.py`.
- Proves: final property reference/defaults and sample trust topology are coherent; old configuration has no binding/alias; management behavior and Framework boundary hold.
- Inputs/fixture: repository docs/compose/defaults, ephemeral exported JWKS and local fixture signer. Reference tests must enumerate nested trust leaf properties instead of documenting nested holder objects as settings.
- Doubles or boundary isolation: management context fixtures migrate to new trust while keeping their existing assertions; Docker fixtures are disposable and use generated material. Do not run the external PR 8.1.2 scenario as a required gate.
- Edge cases: packaged jar config has no mode; quickstart host and proxy share Sidecar namespace; only ingress ports published to host loopback; backend main/health addresses loopback; shared directory exists with app-writable/Sidecar-readable ownership and Sidecar read-only mount; no circular healthy dependency; profile token is accepted and callback bytes unchanged; startup/export ordering recovers within interval. Production Caddy still terminates existing TLS modes, cannot require direct bridge access to loopback backend, and restore fixtures create their own colocated key directory. Local and Kubernetes guidance and no-reset consequence are present; no new Kubernetes runtime test required.

## Safe Verification Commands

Run from repository root in PowerShell. Quote Maven `-Dtest` arguments as shown.

- Focused red and loader iterations: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest' test`
- Authentication/wiring: `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest,JwtSecurityConfigurationTest,JwtStartupIntegrationTest,ManagementBearerFilterTest' test`
- Related execution suite: `.\mvnw.cmd -B -ntp '-Dtest=ExactRoleExecutionIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ExecutionCoordinatorTest,GenericRestSkillHandlerTest,ConfigurationModeIntegrationTest,ManagementLockedExecutionIntegrationTest,SupportedLoomspanApiArchitectureTest,ConfigurationReferenceTest' test`
- Full safe suite: `.\mvnw.cmd -B -ntp verify`
- If Chromium absent, CI-equivalent setup: `.\mvnw.cmd -B -ntp test-compile org.codehaus.mojo:exec-maven-plugin:3.6.2:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.classpathScope=test' '-Dexec.args=install chromium'`; then rerun affected browser/full suite. No omission of management browser regressions is silently treated as success.
- Guidance/version/client: `python scripts/sidecar_version.py check`; `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v`; `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`; `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`. Run as separate commands.
- Shutdown verifier unit coverage: `python scripts/test_verify_shutdown.py`.
- Affected sample integration when Docker is available: `docker build --tag loomspan-sidecar:pr-8-1-4 .`, `python scripts/verify-image.py --image loomspan-sidecar:pr-8-1-4`, `python scripts/verify-production.py --image loomspan-sidecar:pr-8-1-4`. Each command runs separately and uses disposable local fixtures. Verify changed TLS script syntax/static assumptions without expanding into TLS certificate lifecycle testing.
- Inspect `git diff --check` and use `rg` over active source/docs/samples/scripts for the removed property names. Historical plans/tickets may name removals; active bindings/examples must not preserve them.

## Optional Developer Checks

- Operator-specific directory ACL/UID/fsGroup setup and real TLS material may be reviewed in the intended deployment. Kubernetes remains documentation only. These observations are not substituted for local executable trust/health/security tests and require no production action.
- No live deployment, real model/mail service, public CA or external composed suite is needed for routine verification.

## Exit Criteria

- [x] Planned red test records an actual pre-implementation failure for the missing behavior.
- [x] Every required trust/JWT rule and failure/recovery state has executable evidence, including unreadable I/O and no-last-good behavior.
- [x] Atomic publication, one-second ordinary detection and globally limited unknown-kid reload have deterministic evidence; real scheduler recovery is observed with bounded tolerance.
- [x] Both ConfigurationModes use the same configured file; all colocated listeners and no-mode startup behavior are proven.
- [x] Anonymous health leaks no details/components; valid existing management authentication alone reveals trust details; management/execution separation and existing regression suite pass.
- [x] Exact role names work through beta.8 public Framework behavior and ArchUnit passes.
- [x] Ownership handles an opaque default issuer and fresh polling does not replace captured bytes.
- [x] Strict queued expiry/trust denial prevents handoff and releases resources; admitted work continues.
- [x] Strict callback expiry produces exact failure with zero HTTP sends; valid token bytes and no-redirect approved-target controls hold.
- [x] New/updated tests and broadest safe relevant suite pass; all unavailable required environment checks are explicitly recorded with residual risk rather than reported as passed.
- [x] Samples, property reference and portable guidance match the implementation; affected local Docker verifiers run when available.
- [x] No application/test imports of Framework internal/autoconfigure types, no new migration/reset, and no unintended live/destructive operation.
- [x] Optional checks are identified as unperformed/nonblocking when applicable.

## Step-4 evidence and pending exit

See the implementation plan's Step-4 verification ledger for exact commands and actual counts. The planned missing-trust red was observed; focused loader/profile/ownership/lifetime/Framework/configuration/reference/architecture tests pass, as do Python guidance/client/version/shutdown checks and initial Docker image smoke. The full initial suite exposed stale default/unsigned-JWT fixtures, which were corrected and independently rerun green in the 78-test focused suite. A further 15-test authenticated integration run proves actual current-file queued withdrawal without cancelling admitted work; final boundary tests/package pass 24 tests.

Historical health-session discovery, superseded by developer correction: a valid read PAT exposes details on the separate Actuator listener; an existing application-listener browser session does not cross the servlet-context boundary. The developer requires one application listener; that correction and its evidence are persisted in the implementation plan. No shared-session store has been introduced. Single-listener focused verification passed 26 tests; final full verification and independent review remain required. Optional operator permissions/TLS observations are unperformed, nonblocking deployment checks.

### Developer correction

Serve health on the single application listener. Remove the shipped 9091 port, extra ingress mapping and separate-health verifier arguments. No shared-session subsystem is needed. Test valid management browser session/read PAT details plus anonymous, execution JWT, revoked/expired PAT, disabled account and idle-session denial on that same listener. Recreate all namespace participants when replacing/restarting the Sidecar namespace owner, and observe post-exit shutdown fixture state through the surviving host namespace rather than published owner ports.

Final full single-listener verification passed 264 tests (three expected production-browser environment skips); the final focused package passed 23 tests, and Docker image/production reruns pass, including real SIGTERM completion and cutoff. Exact commands and logs are in the implementation plan final ledger.

Final image and production verification both passed and cleaned up their disposable state. All testing-plan exits are backed by the implementation plan ledger; independent Step 5 review remains the final pipeline assurance stage.
