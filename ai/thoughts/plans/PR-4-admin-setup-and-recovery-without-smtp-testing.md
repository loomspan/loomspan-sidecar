# Administrator Setup and Recovery Testing Plan

## Change Summary

The first administrator is activated directly with a chosen password and no SMTP. The packaged image adds pre-Spring setup-token generation and an offline, store-bound reset issuer. Reset credentials can be entered on the browser page without appearing in a URL. Production Compose permits no-SMTP startup while existing SMTP invitations and reset links remain supported.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Bootstrap authorization | Missing/wrong/malformed credential, invalid password, confirmation mismatch, or a competing request creates a partial or second admin | Store-state and HTTP tests with before/after account and bootstrap assertions, plus two-connection race |
| Pending migration state | Old `reserved` account is hijacked, duplicated, or modified by an old set link after direct completion | Seed reserved account and set token; reject other email; finish same ID; reject old token |
| Closure across restart | Configured setup token reopens setup | Reopen SQLite and attempt setup with same token; HTTP page shows completion |
| Command isolation | CLI initializes a missing store or starts HTTP/framework services | Process invocation with absent/malformed DB; assert no DB created, no listening port or application banner, clean exit |
| Concurrent maintenance | Recovery overlaps server on the same SQLite volume | Hold server/store lock in another process; recovery returns nonzero and does not add token |
| Reset security | Raw token persisted or logged, reused, issued to disabled/nonadmin/pending account, or password changed at issuance | Inspect token table, captured stdout/stderr, target matrix; clock-controlled expiry/replay/supersession |
| Session and editing state | Redemption leaves old session or lease usable | Existing HTTP session and editing fixture extended to redeem offline-issued token; assert version rejection and editing cleared |
| SMTP/configuration | Production Compose requires SMTP or mail flows regress | Sanitized `docker compose config`, disposable no-SMTP image startup, local SMTP fixture invitation and email recovery |
| Operator usability | Documentation points to wrong URL/volume or shell capture includes noise | Run documented commands against packaged image/disposable Compose and inspect stdout contract |

## Existing Coverage and Environment Constraints

`ManagementIdentityStoreTest` uses a temporary SQLite database, mutable clock, and mocked mail service; it currently asserts the old emailed first-admin path. `ManagementHttpIntegrationTest` has real Spring HTTP/CSRF/session coverage but expects SMTP-free setup to return 503. `ManagementMailIntegrationTest` and `ManagementMailHttpIntegrationTest` use a local SMTP fixture and currently couple initial setup to mail. Keep their invitation and forgot/reset coverage but activate the initial admin directly. `ManagementSessionGuardTest` already checks version invalidation, and editing-state tests exist around `ManagementEditingState`/runtime transitions. `scripts/verify-image.py` and `scripts/verify-production.py` operate on disposable Docker Compose projects and volumes. Maven wrapper is the repository-standard build path; Java 21 and the locally installed beta 5 framework snapshot are required. Docker checks need a local daemon and image; production browser checks also need the script's Playwright/Chromium setup. No live SMTP, remote model, production service, or external database is needed.

## Failing Test First

Implementation note: the pre-fix red run was not captured; the setup code was
changed before the named regression test was added. The post-fix store, HTTP,
and image checks provide behavior evidence, but the first exit criterion below
remains unchecked rather than being represented as a red test run.

- Name: `setupWithoutSmtpActivatesOneAdministratorAtomically`
- Type: SQLite-backed service regression test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementIdentityStoreTest.java`
- Arrange/Act/Assert: Create a fresh migrated temporary database with a valid setup credential and `mail.configured()` false. Submit credential, normalized email, valid password, and matching confirmation; assert one enabled admin, non-null PBKDF2 hash matching the chosen password, `activated` bootstrap, no token row, and no mail call. Submit wrong credential, weak password, or mismatched confirmation on separate fresh fixtures and assert `unreserved` plus zero accounts.
- Expected pre-fix failure: The current `setup` has no password/confirmation arguments and rejects valid no-SMTP setup with `ManagementMailService.Unavailable`. The test should fail at compile or execution for that precise missing capability.

## Tests to Add or Update

### 1. `setupWithoutSmtpActivatesOneAdministratorAtomically`
- Type: SQLite-backed service test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementIdentityStoreTest.java`
- Proves: Valid no-SMTP setup, policy/hash use, durable closure, no partial state on failures.
- Inputs/fixture: Existing `@TempDir` store and valid 43-character credential, password policy fixtures.
- Doubles or boundary isolation: Mock mail service configured false; no external services.
- Edge cases: Missing/malformed/wrong setup credential; missing/invalid password; confirmation mismatch; normalized email; attempted repeat with token still configured.

### 2. `reservedAdministratorCanCompleteOnlySameIdentity`
- Type: SQLite-backed state transition test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementIdentityStoreTest.java`
- Proves: Existing pending reservation remains bound to the original ID/email, old set tokens are consumed on direct activation, and activated accounts do not change.
- Inputs/fixture: Seed a `reserved` bootstrap row, an inactive admin, and an outstanding set token through repository/SQL fixtures; reopen store after completion.
- Doubles or boundary isolation: Temporary SQLite only.
- Edge cases: Other email, wrong setup credential, already populated password, old set-token replay, and post-restart setup attempt.

### 3. `concurrentSetupCannotCreateCompetingAdministrators`
- Type: Two SQLite datasource concurrency test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementIdentityStoreTest.java`
- Proves: At most one successful direct activation and exactly one admin after simultaneous distinct-address requests.
- Inputs/fixture: Existing latch/two-service pattern with valid password/confirmation.
- Doubles or boundary isolation: Separate connections to the same temporary database.
- Edge cases: Winner may be either request; assert one success, one rejection, `activated`, one account, no partial loser.

### 4. `setupAndManualResetKeepCsrfAndAttemptLimits`
- Type: Real HTTP integration test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementHttpIntegrationTest.java`
- Proves: Setup form/JSON field contract, CSRF protection, attempt limit, no-SMTP sign-in, manual reset form at `/management/password/reset` with no query token, and generic rejection text without secret echo.
- Inputs/fixture: Existing browser helper and `@SpringBootTest` temporary store; use a dedicated new test class if shared seeded state would interfere.
- Doubles or boundary isolation: Loopback server and temporary DB only.
- Edge cases: No CSRF, missing/incorrect credential, invalid/mismatched password, repeated token attempts, success login with chosen email/password, reset page without URL token.

### 5. `offlineIssuerRequiresExistingAdminAndCompatibleStore`
- Type: Forked packaged JAR process and temporary SQLite integration test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementAdminCommandTest.java`
- Proves: Explicit `--database` and email selection, enabled active admin restriction, no store creation, no migrations, no ordinary app startup, nonzero failures with no secret stdout.
- Inputs/fixture: Migrated temporary stores with admin/viewer/disabled/pending accounts, absent/empty/foreign/unmigrated DB paths; invoke `java -jar target/loomspan-sidecar-1.0.0-beta.1-SNAPSHOT.jar` after package, or use a classpath process boundary for regular Surefire and reserve packaged JAR assertion for script. Avoid a test that assumes the JAR exists during plain `test`.
- Doubles or boundary isolation: Disposable files and subprocess; no server, SMTP, or model service.
- Edge cases: Unknown command/options, invalid email, missing target, viewer, disabled admin, pending admin, missing file, incompatible Flyway history, stdout empty on every failure.

### 6. `generatedCredentialsAreFreshCanonicalAndCaptureSafe`
- Type: Packaged JAR/image subprocess test.
- Location: `scripts/verify-image.py` plus a narrow Java command test if useful.
- Proves: Two runs yield distinct 43-character canonical unpadded base64url values decoding to 32 bytes; stdout contains only credential and newline; no DB/model/SMTP/HTTP prerequisites; errors are stderr plus nonzero status.
- Inputs/fixture: Built image, no mounted database and minimal environment.
- Doubles or boundary isolation: Disposable `docker run --rm` invocations.
- Edge cases: Unrecognized flags and argument count; redact output from failure diagnostics. Test capture in PowerShell/POSIX documentation with equivalent simple shell checks where feasible.

### 7. `offlineResetExpiresSupersedesAndInvalidatesOnRedemption`
- Type: SQLite service/command test plus HTTP session test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementIdentityStoreTest.java`, `ManagementAdminCommandTest.java`, and `ManagementHttpIntegrationTest.java`.
- Proves: Issuance stores SHA-256 digest only, leaves hash/role/bootstrap/version unchanged, second issuance consumes first, expiry after 30 minutes, replay rejection, successful password change/version increment, old session rejection, and editing state clear.
- Inputs/fixture: Mutable clock for deterministic expiry; existing session guard/browser and editing fixtures.
- Doubles or boundary isolation: Local SQLite and loopback HTTP. Capture raw CLI token only inside test process memory; never print it to test logs.
- Edge cases: Expired exactly at boundary, invalid purpose, duplicate redemption, prior email reset token superseded, wrong password policy, session and lease after redemption.

### 8. `serverAndRecoveryExcludeSimultaneousStoreAccess`
- Type: Process-level storage integration test.
- Location: `src/test/java/ai/loomspan/sidecar/storage/StorageLockTest.java` and/or `ManagementAdminCommandTest.java`.
- Proves: A second process cannot issue against the same path while the server or another maintenance process holds the lock; startup also fails against a held lock.
- Inputs/fixture: Temporary store and child process holding the lock through a synchronization marker; bounded timeouts and cleanup.
- Doubles or boundary isolation: Disposable local processes. A same-JVM overlapping lock test alone is inadequate.
- Edge cases: Lock release permits subsequent recovery; busy failure creates no token row and leaks no raw token.

### 9. `smtpConfiguredInvitationsAndRecoveryStillWork`
- Type: Local SMTP fixture integration test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementMailIntegrationTest.java` and `ManagementMailHttpIntegrationTest.java`.
- Proves: Initial setup no longer sends mail; invitation and forgot/reset still send trusted-origin links; known/unknown forgot responses remain equivalent; disabled mail actions show clear UI guidance.
- Inputs/fixture: Existing `LocalSmtp` fixture and direct initial setup.
- Doubles or boundary isolation: Test-only SMTP socket; no external recipient.
- Edge cases: SMTP delivery failure/retry on invitation or forgot, token purpose and session invalidation, no raw token in page/status.

### 10. `smtpFreeComposeFirstRunAndRecovery`
- Type: Disposable Docker Compose smoke/browser verification.
- Location: `scripts/verify-image.py`, `scripts/verify-production.py`, and `src/test/java/ai/loomspan/sidecar/management/ProductionComposeBrowserIntegrationTest.java` if the browser fixture is used.
- Proves: Built image token generation, no-SMTP startup, first admin setup/login, stop/one-off recovery on same named volume, manual redemption and login with new password, production Compose config with absent SMTP, retained SMTP branch.
- Inputs/fixture: Unique Compose project/volume names, local fixture model/JWT host and SMTP capture only for the configured-mail branch.
- Doubles or boundary isolation: Disposable containers/volumes; script's cleanup must run in `finally`.
- Edge cases: Run recovery while Sidecar is live must fail; stop/retry succeeds; token not echoed in script logs; Caddy HTTPS path checked by existing production browser tooling.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -B -ntp -Dtest=ManagementIdentityStoreTest,ManagementHttpIntegrationTest,ManagementAdminCommandTest,StorageLockTest test`
- Related suite: `.\mvnw.cmd -B -ntp -Dtest=ManagementMailIntegrationTest,ManagementMailHttpIntegrationTest,ManagementSessionGuardTest test`
- Full safe suite: `.\mvnw.cmd -B -ntp verify`
- Package gate: `.\mvnw.cmd -B -ntp package`
- Disposable quickstart/image gate: `python scripts/verify-image.py --image loomspan-sidecar:sc5-local`
- Disposable production gate: `python scripts/verify-production.py --image loomspan-sidecar:sc5-local`
- Compose interpolation gate: `docker compose --env-file examples/production/production.env -f examples/production/compose.yaml config --quiet` with a sanitized local environment file that omits SMTP but supplies the unrelated required production settings. The implementation scripts should automate this fixture rather than depend on operator secrets.

The Docker commands are safe only against the scripts' disposable projects and unique volumes. They require a local Docker daemon; report `NOT RUN` with the specific missing prerequisite if unavailable. The existing Python verification harness is allowed; the shipped operator credential command must not require Python.

## Optional Developer Checks

- On an actual deployment, the operator may verify volume ownership and HTTPS certificate trust before following the documented recovery steps. This is a configured-environment observation, not a completion gate or routine test.

## Exit Criteria

- [ ] The no-SMTP red test fails for the intended missing setup capability before implementation.
- [x] New and updated tests pass after implementation, including negative paths, process-level lock exclusion, and configured SMTP regressions.
- [x] The full Maven `verify` suite passes against the locally installed beta 5 snapshot.
- [x] Packaged image generation and disposable quickstart/production Compose verification pass, or exact environmental blockers and residual risks are reported.
- [x] Every ticket acceptance criterion has executable evidence from the tests or scripts above; documentation commands match the actual packaged command syntax.
- [x] No routine test contacts live services or production storage; test credentials are sanitized and raw reset credentials are not printed by verification logs.
- [x] Optional deployment observations remain labeled nonblocking and are not represented as performed.
