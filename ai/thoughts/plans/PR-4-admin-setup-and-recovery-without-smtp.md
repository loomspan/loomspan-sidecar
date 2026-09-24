# Administrator Setup and Recovery Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/PR-4-admin-setup-and-recovery-without-smtp.md`
- Research: `ai/thoughts/research/PR-4-admin-setup-and-recovery-without-smtp.md`
- Outcome: An operator can generate a setup credential, activate the first administrator, and issue a local password reset from the packaged image without SMTP, Python, or a running Sidecar.

## Current State

`ManagementIdentityService.setup` currently reserves an administrator and emails a set-password token; `ManagementPagesController.setup` and `ManagementController.Setup` accept only credential and email. `ManagementAccountRepository` owns the SQLite bootstrap singleton and token rows. `redeem` already hashes a password, consumes tokens, increments `credential_version`, and clears editing state. `LoomspanSidecarApplication.main` always starts Spring, while `StorageConfiguration.dataSource` creates a missing database. Production Compose requires SMTP interpolation. The exact current flow, test fixtures, and documentation are recorded in the research artifact.

## Desired End State

The setup page and JSON setup endpoint accept credential, email, password, and confirmation. With a valid configured credential, a valid password, and matching confirmation, one transaction creates or safely completes the reserved administrator, writes the hash, consumes any old set tokens, and activates bootstrap. Invalid input leaves the database unchanged. Activated bootstrap never reopens. Existing pending setup is completable only for its reserved address with the valid credential; existing activated accounts remain intact. Mail invitations and mail reset remain available when configured.

The JAR dispatches `admin generate-setup-token` before Spring startup and `admin issue-password-reset --database <path> --email <address>` through a narrow offline path. Successful stdout is one raw credential and a newline; failure prints a generic diagnostic to stderr and exits nonzero. Recovery opens only a preexisting compatible store, rejects ineligible targets, acquires the same store lock as the server, and commits a digest-only 30-minute reset token before printing it. The browser reset form permits manual token entry at a URL without a token query parameter. Production Compose accepts absent SMTP settings. README-linked instructions give copyable Windows PowerShell and POSIX paths for both deployments.

## Scope

### In scope

- Initial administrator setup, pending-reservation transition, offline credential generation and recovery issuance, manual reset entry, optional SMTP deployment and UI guidance, and Sidecar-owned tests/documentation.

### Out of scope

- CLI invitations or broad account administration, SSO, JWT execution authentication, framework code/dependencies, publishing, and automated setup-secret removal.

## Active Project Guardrails

- Choose the smallest complete solution and replace obsolete development behavior directly; no compatibility shims for the old emailed initial setup path.
- Use only the supported `ai.loomspan.api` framework surface. Sidecar owns management identity and storage; no framework internals or new SPI.
- Preserve Sidecar runtime transition and editing-state coordination on password redemption.
- Keep the locally installed beta 5 snapshot dependency; no release or framework rebuild in this ticket.

## Impact and Risk Analysis

- **Bootstrap atomicity and takeover:** The existing `unreserved → reserved → activated` schema can be reused. Setup must validate all fields before a transaction and guard reserved account ID/email/password state inside it. SQLite serializes writes; the conditional `reserve` operation ensures one winner. Check the return of `activateBootstrap` so a failed activation rolls back password/account writes. A reserved account may have an old emailed set token; direct completion must consume its outstanding tokens.
- **Recovery authority and store selection:** A command that calls normal `StorageConfiguration.dataSource` without checking the path could create an unrelated empty database. Require an explicit database path, validate an existing nonempty regular file, Flyway history/schema compatibility, bootstrap/account state, and target role/enabled/password inside the offline path. The store path is authority, not a public HTTP endpoint.
- **Concurrent maintenance:** Use a Sidecar-owned lock file adjacent to the database, acquired exclusively at server startup and held through context shutdown, and acquired by the recovery command before inspecting or writing. If unavailable, fail without issuing. The server datasource must depend on the lock so normal services do not start against a locked store. Document stop/maintenance/restart; SQLite transactions remain the atomicity boundary for token writes.
- **Secret handling:** Generate 32 secure random bytes in one shared token utility; canonical unpadded base64url output. Store only SHA-256 digests. Avoid raw token in exceptions, logs, request object `toString`, and status messages. Print only after committed issuance. Keep the reset form token in a password input (or equivalent concealed manual field) and do not add token-bearing URLs for operator recovery.
- **Existing mail behavior:** Invitations, resend, and email reset continue through `ManagementMailService`. Replace only the initial setup mail path. Email reset links retain their existing behavior for SMTP-configured environments; operator recovery uses manual redemption.
- **Deployment:** The production Compose interpolation currently blocks no-SMTP startup. Optional values must align with application defaults. Removing setup token requires a container recreate, not merely restarting the current container environment. The quickstart console is direct loopback HTTP; Caddy is production HTTPS.

## Implementation Approach

Keep the existing schema and identity service. Make setup a direct transactional state transition and reuse its password encoder/policy. A reserved pending account is completed in place, preserving its ID and email; a different email is rejected. Activated state rejects all subsequent setup attempts, even while the environment token remains set. Old set tokens are consumed at completion, so an old emailed link cannot alter the chosen password. A reserved initial account's old `set` link is rejected even before completion because the valid setup credential is required for that transition; invited accounts retain normal `set` redemption.

Dispatch CLI arguments in `LoomspanSidecarApplication.main` before `SpringApplication.run`. Generation needs only JDK cryptography. Recovery uses the existing JDBC repository and token issuance rules without constructing the web application or mail service. Extract just the random-token/digest/expiry issuance operation used by both email reset and offline recovery, rather than duplicating token semantics or building a second authentication system. Use an exclusive file lock tied to the normalized database path; hold it for the server lifecycle and the offline command's short transaction. Do not migrate in the offline command: validate a preexisting migrated store. This makes a stopped-server maintenance operation explicit and prevents silent database initialization or simultaneous Sidecar access. The packaged recovery command suppresses Flyway logging during offline issuance so stdout remains the committed raw credential alone; it restores the logger level and reports failures generically on stderr.

## Phase 1: Direct, atomic administrator setup

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementIdentityService.java` — change `setup` to accept password and confirmation, validate setup credential/email/password/confirmation before writes, and transactionally create or complete the reserved account. Hash with the existing encoder, consume outstanding tokens, and activate bootstrap; reject another email, active password, or activated state. Use the transaction and existing SQL conflict translation; no mail call.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementAccountRepository.java` — make bootstrap activation report whether its guarded update succeeded so transaction failure is detectable. Retain the existing schema unless implementation finds a concrete need for migration.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java` and `ManagementController.java` — add password and confirmation to setup form/JSON, retain CSRF and attempt limiter, show first-run success/sign-in and accurate error text, update reserved/locked messages. Keep JSON request redaction.
- [x] `src/test/java/ai/loomspan/sidecar/management/ManagementIdentityStoreTest.java`, `ManagementHttpIntegrationTest.java`, and mail integration tests — replace old first-admin email assumptions; test no-SMTP setup, validation, concurrent requests, restart, reserved transition, and subsequent rejection. Keep invitation/email reset regression through a directly activated admin fixture.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp -Dtest=ManagementIdentityStoreTest,ManagementHttpIntegrationTest,ManagementMailIntegrationTest,ManagementMailHttpIntegrationTest test` — setup/security and retained SMTP flows pass.

### Optional developer checks

- [ ] None; local HTTP and SMTP-fixture cases are automatable.

## Phase 2: Offline commands and storage exclusion

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java` — recognize exactly the two `admin` commands before Spring startup, reject malformed arguments and unknown commands with nonzero status; generation must not touch configuration, storage, mail, or application context.
- [x] `src/main/java/ai/loomspan/sidecar/management/` — add a small token helper shared by `ManagementIdentityService` and offline issuance for 32-byte random unpadded base64url credentials, digest persistence, supersession, and 30-minute expiry. Keep raw values out of object `toString` and diagnostics.
- [x] `src/main/java/ai/loomspan/sidecar/management/` — add a focused offline command implementation that requires `--database` and `--email`, normalizes email, verifies a preexisting compatible store without migration, selects only an enabled active `admin`, and inserts a reset token in one transaction. Print raw token only after commit; avoid Spring web/model/skill startup.
- [x] `src/main/java/ai/loomspan/sidecar/storage/StorageConfiguration.java` and a small lock owner in `src/main/java/ai/loomspan/sidecar/storage/` — acquire one exclusive lock file for the database before server datasource initialization, hold until Spring context close, and use the same lock in recovery; fail clearly if busy. Ensure recover path does not create a database or run Flyway migrations.
- [x] `src/test/java/ai/loomspan/sidecar/management/` and `src/test/java/ai/loomspan/sidecar/storage/` — process-level CLI, target/store rejection, lock contention, expiry/supersession/digest, and no-server-start coverage. Include a second process or equivalent OS-lock check, since same-process `FileLock` overlap is not sufficient evidence for server exclusion.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp -Dtest=ManagementIdentityStoreTest,ManagementAdminCommandTest,StorageLockTest test` — focused command and storage tests pass (create the named tests).
- [x] `.\mvnw.cmd -B -ntp package` — boot JAR exists and packaged invocation succeeds.

### Optional developer checks

- [ ] None; command paths should be tested in a disposable store and packaged image.

## Phase 3: Redemption UI, deployment, and guide

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java` — allow a reset credential to be pasted into `/management/password/reset` without a query parameter, keep query-token support for existing emailed reset links, and explain SMTP-disabled forgot/invite behavior without account enumeration. Update account-page copy and `src/main/resources/static/management/assets/console.js` action feedback as needed.
- [x] `examples/production/compose.yaml` and example environment/documentation — make SMTP variables optional while preserving valid configured SMTP behavior. Keep setup token optional after activation.
- [x] `README.md`, `docs/operations.md`, `examples/production/README.md`, and any quickstart guide linked from README — give complete PowerShell and POSIX commands for generation/capture, Compose start, local HTTP or Caddy HTTPS setup/sign-in, secret removal with recreate, stop/run one-off recovery on the same persistent volume/restart, manual reset, 30-minute expiry, retries, UID/volume privileges, and lack of mail actions when SMTP is absent. Do not use Python for setup credential generation or suggest a default password.
- [x] `scripts/verify-image.py`, `scripts/verify-production.py`, and Compose browser fixtures — exercise packaged image command generation/recovery and SMTP-free setup in disposable Compose projects while retaining an SMTP-configured invitation/reset branch. Keep verification local and nonproduction.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp verify` — all safe Java tests pass, including security and session regressions.
- [x] `python scripts/verify-image.py --image loomspan-sidecar:sc5-local` — disposable quickstart image flow covers SMTP-free setup and commands.
- [x] `python scripts/verify-production.py --image loomspan-sidecar:sc5-local` — disposable production Compose/browser flow proves optional SMTP and retained SMTP flows. Python remains acceptable for verification scripts; operator token generation uses the JAR.
- [x] `docker compose --env-file examples/production/production.env.example -f examples/production/compose.yaml config --quiet` — validates example interpolation with SMTP omitted when using a sanitized local env fixture.

### Optional developer checks

- [ ] Operator may inspect an actual deployment's volume permissions and HTTPS trust/hostname before using the guide; this is not routine test execution.

## Test Strategy

Step 3 specifies deterministic store and HTTP tests first, followed by packaged JAR/image tests in disposable storage. Reuse local SMTP fixtures for retained email paths. Assert database state after failures, token digest-only storage, session version invalidation, and absence of a token query parameter in manual recovery. Broad Maven and Docker verification are required when the local tooling is available; a missing Docker daemon must be reported, not treated as a pass.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Fresh, capturable generated credential; no service dependencies | Pre-Spring `admin generate-setup-token` dispatcher and token helper | Packaged JAR/image repeated-generation and stdout/stderr tests |
| No-SMTP setup and invalid/concurrent failure safety | Transactional `ManagementIdentityService.setup`, form/JSON fields | Store and HTTP CSRF/validation/race tests |
| Durable closure and safe pending transition | Guarded bootstrap update and reserved-ID/email checks | Reopen-store, prior-token, reserved and activated tests |
| Offline recovery, intended store, exclusion, no services | Explicit path checks, Flyway validation, lock, narrow JDBC command | Command-process invalid-store/target/lock tests; image one-off run |
| Expiry, replay, supersession, session and editing invalidation | Shared issuance and existing `redeem`/version/editing operations | Clock-controlled token and HTTP session/edit tests |
| SMTP-free Compose and retained email | Optional Compose interpolation; unchanged mail service flows | Compose config/start plus local SMTP fixture integration |
| README-linked full workflow | README, operations and production guides | Documentation command review against packaged commands and disposable Compose |
| Sidecar-owned regression/integration evidence | Tests and verification scripts above | Focused Maven, full `verify`, image and production scripts |

## Risks and Rollback/Recovery

File locking must work on the supported local/container volume and fail closed if unavailable. The recovery command must avoid printing a token before commit, since a failed write would yield an unusable credential. The existing database schema allows direct activation without migration; if implementation uncovers a schema change, add a migration and migration tests before proceeding. On a failed maintenance command, retain the stopped store and restart only after diagnosing the generic error; use the documented stopped-volume backup/restore process for database faults. No release rollback or framework publish is part of this ticket.

## References

- `ai/thoughts/tickets/PR-4-admin-setup-and-recovery-without-smtp.md`
- `ai/thoughts/research/PR-4-admin-setup-and-recovery-without-smtp.md`
- `ai/thoughts/design-lens.md`
- `src/main/java/ai/loomspan/sidecar/management/ManagementIdentityService.java`
- `src/main/java/ai/loomspan/sidecar/storage/StorageConfiguration.java`
- `src/main/resources/db/migration/V2__management_identity.sql`
