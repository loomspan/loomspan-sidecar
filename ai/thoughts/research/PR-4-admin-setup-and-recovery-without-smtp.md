---
date: 2026-09-24
repository: loomspan-sidecar
branch: main
commit: 7706c5be5290ac99174a06ee85411a0a04841b08
ticket: ai/thoughts/tickets/PR-4-admin-setup-and-recovery-without-smtp.md
tags: [management, bootstrap, recovery, smtp, sqlite, deployment]
---

# Administrator Setup and Recovery Research

## Research Question

How do the checked-out Sidecar's first administrator setup, management credentials, password recovery, local storage, packaged runtime, Compose deployment, documentation, and tests currently work relative to PR-4?

## Summary

First setup validates a 32-byte base64url environment credential, reserves one administrator email in SQLite, then emails a password-set link. It cannot complete without configured SMTP. Password-set redemption activates the reserved account and closes bootstrap; reset redemption changes the password and invalidates sessions through a credential version increment. There is no bundled operator command or offline reset issuance path. Production Compose currently requires SMTP sender and host values at interpolation time. The quickstart allows empty SMTP values but README's first-run sequence only verifies the execution API.

## Repository State

At 2026-09-24 09:06 PDT, `main` was at `7706c5be5290ac99174a06ee85411a0a04841b08`. `git status --short` showed only the untracked PR-4 ticket. No production, test, or documentation changes were present at the start of research. This research artifact is the only file added by this step.

## Current Behavior and Data Flow

### Setup and sign-in

`ManagementIdentityService` obtains `LOOMSPAN_SIDECAR_SETUP_TOKEN` from an environment-backed supplier and checks canonical unpadded base64url encoding of exactly 32 bytes; supplied and configured values are compared with `MessageDigest.isEqual` (`src/main/java/ai/loomspan/sidecar/management/ManagementIdentityService.java:51-59,181-195`). Both browser form and JSON API record token attempts by IP before setup (`ManagementPagesController.java:53-57,204-208`; `ManagementController.java:75-79,127-130`). Spring Security permits these anonymous routes while retaining its CSRF handling (`ManagementSecurityConfiguration.java:46-58,59-78`).

The current setup service normalizes email, requires `mail.configured()`, and executes account insert, bootstrap reservation, and set-token issuance inside one database transaction (`ManagementIdentityService.java:73-95`). `management_bootstrap` starts `unreserved`; setup writes `reserved` with an account ID and an inactive account whose password hash is null (`src/main/resources/db/migration/V2__management_identity.sql:1-24`; `ManagementAccountRepository.java:32-48`). A retry in `reserved` state is accepted only for the same email and an account with no password. Other states reject setup. Mail is sent after transaction commit, so failed delivery leaves the reservation and a token row, and same-address retry issues another token (`ManagementIdentityService.java:79-95,164-171`).

The setup page asks only for credential and email and promises an emailed password link (`ManagementPagesController.java:38-57`). The API setup body likewise has only `credential` and `email` (`ManagementController.java:23-25,75-79`). Login uses email and password (`ManagementPagesController.java:20-36`; `ManagementSecurityConfiguration.java:79-93`). `ManagementPolicy.email` trims and lowercases addresses; `ManagementPolicy.password` enforces 15–128 code points, at most 512 UTF-8 bytes, uppercase, lowercase, digit, and a non-whitespace punctuation/symbol (`ManagementPolicy.java:11-42`). The service stores a prefixed PBKDF2 hash, and the user-details service admits only enabled accounts with a password (`ManagementIdentityService.java:128-148,160-163`; `ManagementUserDetailsService.java:49-62`).

### Token redemption and recovery

The token table stores SHA-256 digests, account IDs, purpose (`set` or `reset`), expiry, and consumption time (`V2__management_identity.sql:17-24`). Issuance uses 32 secure random bytes encoded as unpadded base64url and consumes earlier outstanding tokens for that account; `set` lasts 24 hours and `reset` lasts 30 minutes (`ManagementIdentityService.java:95-124,164-180`). Email `forgot` is public and uses non-enumerating responses and per-email/IP limits; it issues a reset token only for an active account when mail is configured (`ManagementController.java:81-89`; `ManagementPagesController.java:59-71`; `ManagementIdentityService.java:111-124`). `ManagementMailService` requires host, sender, a trusted configured external origin, and a mail sender; it constructs a token-bearing URL in email (`ManagementMailService.java:27-64`). There is no local command to issue a reset credential.

`redeem` checks purpose, unused status, expiry, enabled account, and password state within a transaction, then consumes the presented token, writes a new password hash, consumes account tokens, and activates bootstrap for `set` (`ManagementIdentityService.java:126-146`; `ManagementAccountRepository.java:50-69`). Account password writes increment `credential_version` (`ManagementAccountRepository.java:67-70`); the session guard rejects a session whose principal's version differs from the current account (`ManagementSessionGuard.java:27-43`). Redemption and password change clear the account's in-memory editing state under the runtime transition gate (`ManagementIdentityService.java:42-49,126-162`; `ManagementEditingState.java:39-47`). The browser reset page accepts a token in the query string, inserts it into a hidden field, and accepts a new password; the JSON reset endpoint accepts token and password (`ManagementPagesController.java:73-89`; `ManagementController.java:91-101`). No current page has a visible token entry field for a manually delivered credential.

### Storage and application lifecycle

`LoomspanSidecarApplication.main` always calls `SpringApplication.run` (`src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java:14-19`). `StorageConfiguration` creates a SQLite datasource under `loomspan-sidecar.storage.database-path` (default `/sidecar/data/sidecar.db`), checks existing migration history, sets foreign keys, 5-second busy timeout, WAL, and FULL synchronous mode, then runs Flyway migrations and initializes the snapshot store (`StorageConfiguration.java:26-72,75-137`; `src/main/resources/application.yml:8-10`). A missing path is created by this normal startup path. There is no separate command dispatch, maintenance mode, or process-level exclusion path in the checked-out code. The image ENTRYPOINT always runs the packaged JAR as UID/GID 10001 and includes Java 21 JRE and curl (`Dockerfile:1-19`).

## Key Components

- `src/main/java/ai/loomspan/sidecar/management/ManagementIdentityService.java:73-146` — setup, invitations, email recovery, redemption, password changes, and transactional state transitions.
- `src/main/java/ai/loomspan/sidecar/management/ManagementAccountRepository.java:28-73` — account, bootstrap, and token SQL operations.
- `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java:20-105` — setup, login, forgotten password, reset forms, and account page.
- `src/main/java/ai/loomspan/sidecar/management/ManagementController.java:75-146` — management JSON setup/reset endpoints and error mapping.
- `src/main/java/ai/loomspan/sidecar/management/ManagementSecurityConfiguration.java:46-103` — route authorization, CSRF-enabled form login, and login attempt limits.
- `src/main/java/ai/loomspan/sidecar/management/ManagementMailService.java:27-64` — mail availability and trusted-origin link creation.
- `src/main/java/ai/loomspan/sidecar/storage/StorageConfiguration.java:26-137` — datasource creation, migration, and SQLite durability setup.
- `src/main/resources/db/migration/V2__management_identity.sql:1-24` — persisted identity schema and bootstrap singleton.
- `src/main/resources/static/management/assets/console.js:351-425` — account invitations and resend interactions assume emailed links.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Setup credential | Environment supplier and strict canonical 43-character decoding; no generation command (`ManagementIdentityService.java:53-59,181-195`; `LoomspanSidecarApplication.java:14-19`). |
| Bootstrap identity | `unreserved → reserved → activated` database state, reserved address bound to ID; activation happens on emailed set-token redemption (`V2__management_identity.sql:9-15`; `ManagementIdentityService.java:79-95,126-146`). |
| Recovery | Email-only reset issuance; digest persistence, 30-minute expiry, transactional redemption (`ManagementIdentityService.java:111-146,164-180`). |
| Session and edit state | Password write bumps version; guard rejects old session; redemption clears drafts and lease for account (`ManagementAccountRepository.java:67-70`; `ManagementSessionGuard.java:27-43`; `ManagementEditingState.java:39-47`). |
| Email actions | Invitations and resend check mail configuration; browser accounts UI says links are emailed (`ManagementIdentityService.java:97-110`; `ManagementPagesController.java:97-105`; `console.js:378-425`). |
| Quickstart | Optional empty SMTP variables, loopback HTTP management port 9091, persistent named volume (`examples/quickstart/compose.yaml:31-47`); README starts API fixture and has no console first-admin walkthrough (`README.md:23-53`). |
| Production | SMTP sender and host are required Compose substitutions; setup and recovery guide assumes email (`examples/production/compose.yaml:37-46`; `examples/production/README.md:124-161`). Caddy fronts the production console (`examples/production/compose.yaml:1-20`). |
| Operations docs | Describes Python setup-token generation, email-only recovery, and setup API body without password (`docs/operations.md:274-325,389-395`). |

## Existing Tests and Fixtures

- `ManagementIdentityStoreTest.java:59-93,139-183` covers bootstrap persistence, wrong credential, reserved-address restriction, concurrent reservation, token purpose, replay, expiry, activation, and reset hash change using real SQLite and a mocked mail service. These cases exercise the current emailed pending-account path.
- `ManagementMailIntegrationTest.java:55-79,94-136` covers SMTP failure and retry for the reserved address, trusted emailed links, invitations, and email recovery with a local SMTP fixture.
- `ManagementMailHttpIntegrationTest.java:60-110` covers CSRF-protected HTTP setup, emailed link redemption, login, invitation, forgot response equivalence, reset, and session invalidation against a local SMTP fixture.
- `ManagementHttpIntegrationTest.java` covers management form/JSON CSRF, roles, login limits, password changes, and session behavior. Its SMTP-free setup expectation is currently HTTP 503 for a valid credential.
- `ManagementSessionGuardTest.java` covers version-driven invalidation; `ConfigurationSnapshotStoreTest.java:338-391` covers migration rejection and SQLite pragma/lock behavior.
- `scripts/verify-image.py:68-115` builds a disposable quickstart Compose project and checks image/runtime behavior. `scripts/verify-production.py:186-398` uses a disposable production Compose stack with SMTP capture and Chromium to exercise setup and recovery; `ProductionComposeBrowserIntegrationTest.java:28-53` consumes test links. Current production verification uses Python to generate its setup credential and assumes email links.

No current test or fixture covers an offline packaged command, SMTP-free first-admin activation, manually entered reset credential, or maintenance exclusion while a server uses the same database. Docker-based scripts require a local Docker/Compose daemon and the image; Chromium browser tests require Playwright browser availability. SMTP fixtures are local test services and do not contact real recipients.

## Dependencies and Operational Constraints

The application uses Java 21, Spring Boot 4.1, SQLite JDBC, Flyway, and the locally installed Loomspan `1.0.0-beta.5-SNAPSHOT` starter (`pom.xml:12-89`). Repository policy restricts Loomspan Java dependencies to the supported public `ai.loomspan.api` surface and leaves framework installation to the developer (`AGENTS.md`). The production guide requires one Sidecar per local SQLite volume and stopped-volume backup/restore (`examples/production/README.md:3-10,143-185`). The image runs as UID 10001; volume preparation gives that UID write access (`Dockerfile:5-17`; `examples/production/README.md:143-153`). A maintenance command that uses current normal `StorageConfiguration.dataSource` would create a missing database path before validating it, based on the code at `StorageConfiguration.java:41-66`.

## Historical Context

Earlier PR 1.3.1 and PR 1.4.1 tickets explicitly established emailed first-password setup and email-only recovery (`ai/thoughts/tickets/2026-09-18-pr-1.3.1-management-accounts-and-email-recovery.md:32-73`; `ai/thoughts/tickets/2026-09-18-pr-1.4.1-embedded-console-and-account-flows.md:16-35`). PR-4 changes those outcomes for initial setup and local operator recovery while retaining invitations and email flows when configured. The design lens permits replacing obsolete development behavior directly without compatibility shims (`ai/thoughts/design-lens.md:5-27`). The matching local framework checkout is the prescribed reference; the older installed documentation skill is stale per `AGENTS.md`.

## Open Questions

- Planning must determine the pending-reservation transition semantics for existing databases. The current reserved state owns an email with no password and expects a set token (`ManagementIdentityService.java:83-92,137-143`).
- Planning must determine how an offline command identifies and validates the intended preexisting database and enforces exclusion from a live Sidecar process. Current datasource startup creates a missing path and uses WAL (`StorageConfiguration.java:41-72`).
- Verification must distinguish the production Compose interpolation requirement from actual mail service configuration. Compose currently rejects absent SMTP sender/host values before startup (`examples/production/compose.yaml:38-40`).
