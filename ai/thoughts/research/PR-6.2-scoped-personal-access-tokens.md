---
date: 2026-09-24
repository: loomspan-sidecar
branch: main
commit: 89709033afcdeeab4ddbebb068d036c7cb025a10
ticket: ai/thoughts/tickets/PR-6.2-scoped-personal-access-tokens.md
tags: [management, authentication, editing, publication, security]
---

# Scoped Personal Access Tokens Research

## Research Question

How do current management identity, browser authentication, editing, publication, and execution boundaries work, and where would PR 6.2 intersect them?

## Summary

The management surface currently uses local accounts and browser form-login sessions. The management security chain has role rules and retains CSRF enforcement; there is no personal-token bearer authentication, token issuance UI/API, or token-specific permission model. The separate execution chain uses externally issued JWT bearer credentials for `/v1/**` and denies other requests. The PR 6.1 shared editing API persists one draft per account, but its active lease is bound to a servlet session ID. Its mutation paths check current account state and exact lease/candidate/base values at editing and publication gates.

## Repository State

- Observed 2026-09-24 20:20 PDT on `main` at `89709033afcdeeab4ddbebb068d036c7cb025a10` (`Cleanup PR 6.1`). `git status --short` returned no changes. The research document is the only new file from this step.
- `053e9dc` introduced PR 6.1, and the current HEAD is its cleanup. The PR 6.2 ticket and remote-authoring roadmap describe intended next behavior; they do not establish current implementation.

## Current Behavior and Data Flow

### Identity and browser authentication

- `ManagementSecurityConfiguration.java:33-91` installs the first Spring Security chain for `/management/**` and `/api/management/**`. It uses a database-backed `DaoAuthenticationProvider`, form login, role matchers, logout, API problem responses, a login-rate filter, and `ManagementSessionGuard`. It does not configure a bearer authenticator or disable CSRF. Current HTTP tests explicitly verify missing CSRF is rejected (`ManagementEditingHttpIntegrationTest.java:125-138`).
- `ManagementUserDetailsService.java:43-52` loads an active account and derives `MGT_VIEWER`, `MGT_EDITOR`, and `MGT_ADMIN` authorities from its role. The principal carries ID, email, role, account credential version, and authorities (`:17-39`).
- `ManagementSessionGuard.java:25-38` checks account existence, active status, credential version, session activity, and idle timeout on browser requests. `ManagementEditingService.java:213-233` independently checks live account status/version/role and live servlet-session activity before editing admission. `ManagementController.java:53-67` exposes session identity, authorities, CSRF token, and timeout values, and records deliberate activity. Logout invalidates the servlet session (`:69-73`).
- `ManagementAccountRepository.java:9-11,18-31,64-72` stores role, enabled status, password hash, and credential version. Password replacement increments the version; role or enabled changes also increment it. `ManagementIdentityService.java:140-183` changes passwords or accounts while holding the editing transition, then clears that account's active lease. Setup, invitation and password reset use a separate one-time `management_token` purpose table (`V2__management_identity.sql:18-25`), with generated 32-byte URL-safe values and SHA-256 digests (`ManagementTokens.java:11-36`). Those are email/setup credentials, not personal API tokens.

### Management operation map

| Area | Current operation and evidence |
| --- | --- |
| Anonymous identity | Setup and password forgot/set/reset are permitted by the management chain (`ManagementSecurityConfiguration.java:50-57`), with request handlers in `ManagementController.java:75-102`; public HTML forms also exist in `ManagementPagesController.java:36-153`. |
| Session and account | Session read/activity/logout/password change are in `ManagementController.java:53-107`; account list/invite/alter/resend are in `:109-125`. The chain protects account API paths with `MGT_ADMIN` (`ManagementSecurityConfiguration.java:62`). |
| Configuration inspection | Current snapshot, export, history list, and history item are GET operations in `ManagementConfigurationController.java:51-112`; the security chain's default is authenticated (`ManagementSecurityConfiguration.java:68`). |
| Editing inspection | Lease status and caller-owned draft are GET operations (`ManagementEditingController.java:24-33`); the chain permits authenticated users (`ManagementSecurityConfiguration.java:64`). |
| Editing mutations | Acquire, explicit same-user handoff, admin takeover, renew, release, save, reconcile, validate, and discard share `ManagementEditingService` (`ManagementEditingController.java:35-87`). The chain generally requires `MGT_EDITOR`, with `MGT_ADMIN` for takeover (`ManagementSecurityConfiguration.java:63-67`). |
| Import and rollback | Import review/load and rollback review/load are in `ManagementConfigurationController.java:116-127,141-158`. Import and rollback each validate/read source, then load into the same editing service (`ManagementConfigurationImportService.java:29-39`; `ManagementConfigurationRollbackService.java:22-34`). Chain matchers require `MGT_EDITOR` (`ManagementSecurityConfiguration.java:59-61`). |
| Publication | `POST /api/management/configuration/publish` calls `ManagementEditingService.publish` (`ManagementConfigurationController.java:135-139`). Its chain rule is `MGT_EDITOR` (`ManagementSecurityConfiguration.java:65-67`). There is currently no separate publication permission. |

### Editing and publication lifecycle

- `V3__management_drafts.sql:1-18` makes `account_id` the draft primary key and stores the complete configuration as ordered skill documents plus route YAML. `ConfigurationDraftStore.java:23-75` reads, creates, revision-replaces, and deletes drafts by account ID; content is not deleted by logout or account changes.
- `ManagementEditingState.java:7-43` holds one process-local lease and validation proof. A lease records account ID, servlet session ID, generated editing-session ID, generation, label, and expiry. `ManagementEditingSessionListener.java:19-23` clears the lease on session destruction.
- `ManagementEditingService.java:42-61` lets the current user see lease status and their own draft. Acquire and handoff rotate the lease generation; takeover checks admin role (`:64-78`). `ownLease` requires live session, live editor account, account ID, session ID, editing-session ID, and generation (`:193-201`). Each saved replacement checks expected draft revision and current base, increments revision, and clears validation (`:116-132`). Validation runs outside the editing lock, then attaches proof only after rechecking the same draft and lease (`:144-159`).
- Publication takes the runtime publication and transition locks, then evaluates the supplied admission (`RuntimeConfigurationService.java:147-164`). `ManagementEditingService.java:161-175` rechecks lease, live account, exact draft/revision/base, and successful validation proof within that admission. Runtime publication checks the published predecessor against the candidate base (`RuntimeConfigurationService.java:157-161`). On success, the publishing draft and lease are cleared (`:226-236`); failures are represented as publication faults and preserve the draft where activation did not complete (`:169-224`).
- `ManagementConfigurationImportService.java:29-39` and `ManagementConfigurationRollbackService.java:22-34` do review/validation and load a candidate into the same saved draft. They do not themselves activate it; activation is the separate shared publish endpoint.

### Execution credential boundary

- `JwtSecurityConfiguration.java:87-105` is the second ordered chain. It disables CSRF, is stateless, requires JWT authentication for `/v1/**`, permits the dedicated observability/health paths, and denies everything else. JWT decoding validates timestamp, issuer, audience, and required claims (`:36-69`). The management chain matches management paths first (`ManagementSecurityConfiguration.java:48`).
- The existing credential-separation integration test covers an observability API key versus an execution JWT (`ConsoleSecurityIntegrationTest.java:40-53`). `ManagementHttpIntegrationTest.java:158` starts a browser-management/CSRF versus execution case. There is no personal management bearer credential to exercise yet.

## Key Components

- `src/main/java/ai/loomspan/sidecar/management/ManagementSecurityConfiguration.java:33` — management filter chain, role routing, form login, CSRF path, and login limits.
- `src/main/java/ai/loomspan/sidecar/management/ManagementIdentityService.java:140` — credential and account changes, currently clearing editing state after changes.
- `src/main/java/ai/loomspan/sidecar/management/ManagementAccountRepository.java:9` — account persistence and existing one-time credential records.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:42` — shared draft/lease service and account checks.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingState.java:10` — session-bound in-memory lease and exact validation proof.
- `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java:151` — publication lock and admission boundary.
- `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:87` — separate execution JWT chain.
- `src/main/resources/static/management/assets/editor.js:13` and `console.js:50` — browser calls the management API with same-origin credentials and CSRF header; `import.js:19-20` does so for multipart import.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Token lifecycle | No personal-token table, API or console page exists. `V2__management_identity.sql:18-25` is limited to `set`/`reset` tokens; `ManagementPagesController.java:156-196,302-324` has home, account and password views. |
| Scope boundary | `ManagementSecurityConfiguration.java:50-68` uses account-derived role authorities and broad authenticated fallback; no token preset mapping exists. |
| Credential ownership | `ManagementEditingState.java:10-23` stores `sessionId`; `ManagementEditingService.java:219-233` requires a servlet session. An editing-session UUID by itself is insufficient today (`:193-201`). |
| Live mutation authority | Account, lease, validation, and base checks occur inside transition/publication gates (`ManagementEditingService.java:161-175,193-216`; `RuntimeConfigurationService.java:151-164`). No personal-token validity check exists at those gates. |
| Account lifecycle | Password/set/reset/change and disablement update the account credential version and clear its lease (`ManagementIdentityService.java:140-183`); they only consume the existing one-time reset/invite tokens (`ManagementAccountRepository.java:58-62`). |
| Abuse and audit | Login, forgot, and one-time-token attempts use `ManagementAttemptLimiter.java:10-43` and `ManagementController.java:81-89,127-130`; it is a bounded process-local rolling-window map. Runtime publication has fault/warning logs (`RuntimeConfigurationService.java:184-247`), but no structured actor/token/action/version audit path is present in the management code. |
| Transport/deployment | Production Caddy serves the application over HTTPS (`examples/production/README.md:4,47-53`); the local guide uses loopback HTTP (`docs/admin-access.md:55-60`). Management and execution share the application port (`docs/operations.md:255-258`). |

## Existing Tests and Fixtures

- `ManagementEditingHttpIntegrationTest.java:69-196` covers same-user handoff, private drafts, stale base, CSRF, lease expiry, admin takeover, session activity, viewer limits, and concurrent saves through real HTTP. `ManagementEditingServiceTest.java:27-68` covers delayed validation versus changed saved revision.
- `ManagementImportBrowserIntegrationTest.java:47` and `ManagementHistoryBrowserIntegrationTest.java:51` cover import and rollback load paths. `ManagementDraftRestartIntegrationTest.java:19` covers durable draft versus ephemeral editing authority. `ManagementLockedExecutionIntegrationTest.java:42` covers management locking versus JWT execution.
- `ManagementHttpIntegrationTest.java:158-262` covers browser/execution separation, account/session changes, password change, and login attempt bounds. `ConsoleSecurityIntegrationTest.java:40-53` covers observability API-key versus JWT isolation. `ManagementAttemptLimiterTest.java:17` covers bounded counters. These tests use local HTTP, temporary SQLite databases, fixture keys, and controllable clocks; no live identity service is needed.
- There are no existing tests for personal-token issuance/list/revoke, token bearer login, cumulative permission presets, token-bound leases, live token revocation at mutation gates, or token-secret-safe audit events because those capabilities do not exist yet.
- The repository's documented local build uses `./mvnw -B -ntp verify` or `mvnw.cmd` on Windows (`docs/operations.md:664-670`). `pom.xml:42-45` pins the installed framework `1.0.0-beta.5-SNAPSHOT`; repository policy requires Sidecar integration evidence against that local artifact, with hosted CI deferred until framework publication. Production Compose verification uses disposable local fixtures but starts configured containers (`docs/operations.md:678-681`), so it was not run during research.

## Dependencies and Operational Constraints

- SQLite and Flyway own persistent storage (`StorageConfiguration.java:135-138`; `V2__management_identity.sql`; `V3__management_drafts.sql`). Any new persistent token metadata intersects that schema and the single-node database/locking model.
- The framework integration uses supported `ai.loomspan.api` contracts; `RuntimeConfigurationService.java:3-5` imports public preparation/reload/validation types. The ticket and `AGENTS.md` prohibit internal framework dependencies and require local matching framework documentation during implementation.
- The current browser JavaScript uses same-origin cookies and CSRF headers (`console.js:50`, `editor.js:13`, `import.js:19-20`). `docs/operations.md:397-423` currently describes all management API calls as requiring an authenticated management user and CSRF on unsafe methods, and documents editing-session IDs as belonging to a login session; these descriptions are pre-token.
- The management API returns full configuration and retained snapshot content (`ManagementConfigurationController.java:51-112`), and saved drafts contain authored values (`ConfigurationDraftStore.java:23-40`); token Read access therefore reaches sensitive configuration content within its account/role boundary.

## Historical Context

- The PR 6.1 ticket expressly excluded personal-token authentication and established one shared management contract, durable per-user drafts, single lease, explicit handoff, and exact publication checks (`ai/thoughts/tickets/PR-6.1-shared-authoring-and-durable-drafts.md`, Requirements 1-8). The current code implements a browser-session origin for those grants.
- The remote-authoring roadmap's phase 2 defines cumulative Read/Edit/Publish presets, management/execution separation, live-role intersection, one-time secret presentation, and revocation on account changes (`ai/thoughts/phases/2026-09-24-remote-skill-authoring-roadmap.md:125-168`). The PR 6.2 ticket is the controlling current requirement.

## Open Questions

- The ticket leaves token lookup/hash format, permission identifiers, endpoint shapes, and issuance/authentication abuse limits for planning. The repository has examples of random one-time values, SHA-256 digests, generic failures, and bounded login counters, but no personal-token contract.
- The current publication authorization rule is editor role (`ManagementSecurityConfiguration.java:65-67`). PR 6.2 introduces a distinct Publish ceiling for bearer callers while retaining browser publication. The current security chain does not distinguish those request origins.
- Current account `credential_version` changes on any role/enable change (`ManagementAccountRepository.java:67-72`). Planning must distinguish the ticket's permanent token revocation on disablement/password changes from immediate live-role enforcement on role changes; neither token behavior exists today.
