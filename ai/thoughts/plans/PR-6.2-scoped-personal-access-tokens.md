# Scoped Personal Access Tokens Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/PR-6.2-scoped-personal-access-tokens.md`
- Research: `ai/thoughts/research/PR-6.2-scoped-personal-access-tokens.md`
- Outcome: A user can issue a short-lived Read, Edit, or Publish token in the console and use it on the existing management API, subject to live account authority and PR 6.1 editing/publication gates.

**Development contract:** Sidecar is in development. Destructively replace superseded development contracts; add no compatibility shim, parallel API, or migration machinery solely for obsolete behavior. Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). Existing development databases can be retained through a new Flyway migration; no data reset is required by this plan. Do not delete deployed data.

## Current State

`ManagementSecurityConfiguration` authenticates form-login sessions and authorizes management routes with account role authorities. `ManagementSessionGuard` and `ManagementEditingService` require an active servlet session. `ManagementEditingState.Lease` binds a lease to that session ID. `ManagementConfigurationController` and `ManagementEditingController` expose the single shared draft, import, rollback, validation, and publication contract. `RuntimeConfigurationService.publish` invokes the editing admission after taking its publication lock. `ManagementIdentityService` changes passwords/accounts under the editing transition and clears active leases. `JwtSecurityConfiguration` has the separate execution JWT chain. The research artifact records the corresponding source and test locations.

## Desired End State

The console creates, lists, and revokes only its signed-in user's personal tokens. A 32-byte random secret appears once; persistence holds only an identifier, SHA-256 digest, owner, preset, timestamps, and revocation. A token defaults to seven days and must expire after issuance and within 30 days. Browser and token clients call identical authoring endpoints and payloads. Read, Edit, and Publish are cumulative ceilings intersected with the live role. Personal tokens cannot enter account, token-management, session-management, or takeover routes. The bearer origin is bound to an editing lease; a leaked editing-session ID is insufficient. Revocation, expiry, password changes, and disablement stop subsequent access, including work waiting at a mutation gate; drafts remain saved. Publication still requires the exact validated candidate and current base. Execution JWTs and management credentials remain separate.

## Scope
### In scope
- Token table/repository/service, browser-only lifecycle API and console UI.
- Central bearer authentication, explicit operation map, CSRF/credential separation, lease-origin binding, and live gate checks.
- Bounded issuance/authentication attempts, structured safe audit events, tests, and operator/client documentation.

### Out of scope
- OAuth, JWT issuance/exchange, MCP, agent allowlists, environment policy, approval toggle, audit-history UI, vault, automatic refresh, and PR 6.3 client packaging.
- Changes to execution authority, runtime credential forwarding, or framework internals.

## Active Project Guardrails

- Apply [simplicity and technical debt](../design-lens.md#simplicity-and-technical-debt): one lifecycle service and one management contract, with no legacy adapters.
- Management and execution identities stay separate; never derive credential authority from request bodies, labels, or model inputs.
- Preserve one user-owned durable draft, one process-local lease, explicit same-user handoff, exact validation/base admission, and publication under the existing lock.
- Use only supported `ai.loomspan.api` framework types; no `ai.loomspan.internal..` or `ai.loomspan.autoconfigure..` imports in application or tests. Consult matching local framework documentation as needed. Build against the locally installed beta.5 snapshot.

## Impact and Risk Analysis

The new token is a stored security credential. A random 256-bit secret makes deterministic SHA-256 lookup safe against offline guessing; a public random identifier enables indexed lookup, while constant-time digest comparison and uniform 401 responses avoid disclosing validity. Keep the one-time secret out of entity `toString`, logs, list responses, and errors. A separate table prevents reset/invitation credentials in `management_token` from being accepted as PATs. Persisted token metadata needs a forward Flyway migration; no old PAT data exists to migrate.

The high-risk path is mixed cookie/bearer requests: reject bearer plus `JSESSIONID` before authentication, even if the bearer is invalid. Do not bypass CSRF for a request that could use a browser session. Bearer-only management API requests can be CSRF exempt after that rejection; cookie mutations continue to require CSRF. Never accept a bearer on browser pages or token-lifecycle endpoints. A PAT cannot create a servlet session or use a session activity endpoint. The execution chain remains JWT-only.

Role changes increment `credential_version` today. PAT validity must not use an issuance-time version: role reduction should reduce effective authority live, while disablement and password replacement permanently revoke PAT rows. Recheck both row validity and the current account at editing mutation and publication admission, after lock waits and validation work. The lease records the credential origin; expired/revoked PAT access fails even if the process-local lease has not yet been swept. Explicit revocation and account changes clear a matching lease under the editing transition without deleting a draft.

## Implementation Approach

Use a personal-token format `lspat_<random-id>.<43-character-random-secret>` (or an equivalently strict, documented unambiguous format), with a random public identifier and separate 32-byte secret. Store its SHA-256 digest, compare digests in constant time, and reject malformed or oversized headers before lookup. A new `management_personal_token` table contains identifier, account ID, digest, `read|edit|publish`, creation, expiry, and revocation times. Keep issuance and revocation in one service, with database transactions and a bounded per-user issuance rate (for example five per hour) plus a bounded active-token count (for example 20). Use `ManagementAttemptLimiter` for per-IP bearer failures (for example 20 per 15 minutes, bounded by its existing capacity); cap header size and count malformed attempts. State the exact selected limits in docs and tests. No new configurable-lifetime subsystem is needed.

A management bearer filter owns credential parsing and authentication in the existing management chain. It rejects mixed credentials, invalid Bearer headers, and PAT use on non-authoring/lifecycle routes. It resolves the current account for every request and creates a request-local authentication carrying the same account principal plus token identifier/preset as trusted origin metadata. Assign only effective operation authorities after intersecting token preset and live role. Keep browser authorities unchanged. A single explicit HTTP method/path rule map covers every current management operation: Read for current/export/history/editing status and own draft; Edit for lease acquire/handoff/renew/release, save/reconcile/validate/discard, import/rollback review and load; Publish for publish. The latter still requires editor/admin live role. All other PAT routes deny by default, including setup/reset, session/activity/logout/password, accounts, PAT administration, pages, and takeover. Recheck permissions in the editing service gates so request-start authorization cannot outlive revocation or role change. Security mapping and service admission should share a small policy enum/helper rather than duplicate independent lists of powers.

Replace the lease's servlet-session-only field with a credential-origin value (`browser:<session-id>` or `pat:<token-id>`). Browser destruction clears only its origin. Controller methods obtain the trusted origin from authentication/request; do not accept it in request payloads. Same-user Edit/Publish handoff rotates generation and makes the previous holder invalid. PAT expiry/revocation invalidates the old origin; saved draft remains. Preserve exact candidate, validation and base checks in `ManagementEditingService`/`RuntimeConfigurationService`.

Implementation note (Step 4): the existing controller signatures remain unchanged; the editing service reads the trusted, request-local Spring Security authentication to derive the origin at each gate. This keeps the shared payloads and callers intact. The integration scenarios were consolidated in `ManagementPersonalTokenHttpIntegrationTest`, and deterministic delayed-validation and delayed-publication gate scenarios were added to `ManagementEditingServiceTest`. Limits selected: five issuances per account per hour, 20 active tokens, 20 failed bearer authentications per IP per 15 minutes, and 128 Authorization-header characters.

Rejected alternative: extending the existing password-reset `management_token` table would mix short-lived, one-use identity credentials with reusable scoped PATs and complicate revocation/account rules. A separate authoring API would duplicate PR 6.1's rules. Neither is justified.

## Phase 1: Persist and administer PATs

### Changes
- [x] `src/main/resources/db/migration/V4__management_personal_tokens.sql` — create constrained token metadata table with unique indexed identifier, owner foreign key, allowed preset, created/expiry/revoked timestamps, and owner index. No raw token column.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementPersonalTokenRepository.java` and `ManagementPersonalTokenService.java` — issue opaque secrets, validate seven-day default/30-day maximum and bounded active count/issuance rate, authenticate via indexed lookup and constant-time digest comparison, list only safe metadata by owner, and revoke by owner plus ID. Use a no-secret `toString` for any issuance response type.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementPersonalTokenController.java` — `GET/POST /api/management/personal-tokens` and `DELETE /api/management/personal-tokens/{id}` available only to browser sessions with CSRF on mutations. Creation alone returns the secret with `Cache-Control: no-store`; list/revoke never return it.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java` and `src/main/resources/static/management/assets/console.js` (plus `console.css` if needed) — own-token console page/navigation, preset and expiry choices, one-time copy/display warning, list and revoke; never persist the secret in browser storage or URL.
- [x] Lifecycle, ownership, hashes, expiry bounds/default, limits, and one-time response behavior in `ManagementPersonalTokenHttpIntegrationTest` and `ManagementBearerFilterTest`.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp -Dtest=ManagementPersonalTokenHttpIntegrationTest test` — consolidated table and browser lifecycle tests pass against local SQLite and installed snapshot.

### Optional developer checks
- [ ] Inspect one-time secret display and clipboard behavior in a browser; automated HTTP/UI assertions remain the release gate.

## Phase 2: Authenticate and authorize on the shared API

### Changes
- [x] `ManagementSecurityConfiguration.java`, `ManagementSessionGuard.java`, `ManagementCredential.java`, and `ManagementBearerFilter.java` — insert bearer authentication into the management chain, reject bearer-plus-cookie and malformed/invalid Bearer before session fallback, keep cookie CSRF, avoid PAT sessions, and implement explicit method/path preset map with deny-by-default PAT behavior. Preserve browser rules. Allow bearer-only CSRF exemption solely for eligible management API routes after mixed-credential rejection.
- [x] `ManagementEditingService.java` and `ManagementEditingState.java`, using the existing shared controllers/import/rollback services and session listener — derive the trusted request-local origin, bind the lease to it, recheck live token/account and Edit/Publish authority at mutation gates after validation and publication waits, keep takeover browser-only and exact publication checks.
- [x] `ManagementIdentityService.java` — revoke all PATs transactionally on password change/set/reset/recovery and disablement while holding the editing transition; role changes retain PAT rows but live role narrows authority. Clear affected active lease; do not delete draft.
- [x] `ManagementPersonalTokenService.java` and relevant management mutation/publication services — emit structured audit events with actor account ID, PAT identifier where present, action, candidate/base/published version when relevant, and outcome; never log secret or Authorization header.
- [x] `ManagementPersonalTokenHttpIntegrationTest.java`, `ManagementBearerFilterTest.java`, and `ManagementEditingServiceTest.java` — live role/preset intersection, mixed auth and CSRF, same-user handoff, closed operation map including import/rollback routes, exact publication, account transitions, deterministic revoked-token checks after validation/publication waits, and execution separation.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp '-Dtest=ManagementBearerFilterTest,ManagementPersonalTokenHttpIntegrationTest,ManagementEditingServiceTest' test` — consolidated PAT operation and delayed-gate matrix passes.
- [x] `.\mvnw.cmd -B -ntp verify` — existing browser, persistence, handoff, CSRF, and execution boundaries remain intact within the full suite.

### Optional developer checks
- [ ] None.

## Phase 3: Documentation and full verification

### Changes
- [x] `docs/admin-access.md`, `docs/operations.md`, and `docs/integration.md` — document own-token creation/revocation, exact preset/role behavior, endpoint and Authorization header use, HTTPS for remote connections, limits/expiry, publication prerequisites, audit fields, and management/execution separation. Explain that stored hashes cannot supply a secret: the user injects the displayed one-time value into the client's environment, without command-line secret arguments, repository files, or a new vault.
- [x] `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java` — retained unchanged; the full suite verifies the public framework boundary.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp verify` — complete safe Sidecar suite against locally installed `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.5-SNAPSHOT` passes: 216 tests, zero failures/errors, three optional production Compose browser skips. No framework rebuild.

### Optional developer checks
- [ ] In a configured non-production HTTPS deployment, exercise a client call with an environment-injected PAT and inspect emitted audit metadata. This is nonblocking and must be reported as unrun unless explicitly performed.

## Test Strategy

Start with issuance/list secrecy and a bearer Read HTTP test that fails before implementation. Prefer local SQLite/HTTP integration tests and controllable clocks already used by management tests. Exercise all current route families and negative paths, including malformed/invalid bearer, mixed credentials, cookie CSRF, token role intersection, origin-bound lease, concurrent/waiting publication, and account transitions. Keep execution tests on fixture JWTs and no external services. The testing plan specifies individual scenarios and commands.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Own-token lifecycle, secrecy, expiry | V4 table, token service/controller, console | Lifecycle integration including DB digest inspection and one-time response |
| Shared API, CSRF, mixed auth | Bearer filter and security chain, unchanged controllers/payloads | HTTP cookie/bearer equivalence, CSRF and mixed/invalid credential tests |
| Cumulative presets, live roles, private state | Explicit policy, live principal/account resolution, owner-bound draft/lease | Method matrix, viewer/high-scope, role reduction, cross-user tests |
| Edit/Publish and no PAT admin/takeover | Route map and service publication gate | Import/rollback and publish tests, admin-owned PAT negative tests |
| Revocation/account lifecycle/delayed work | Token rows, identity transitions, origin and gate rechecks | Clock/revocation/password/disable/re-enable and latch-controlled gate tests |
| Credential separation/environment neutrality | Existing execution chain, no label-based policy | PAT-to-execution/JWT-to-management and label-neutral tests |
| Abuse and safe audit | Attempt limiter, count cap, audit event calls | Limit and captured-log no-secret assertions |
| Installed-snapshot integration and docs | Documentation updates and unchanged framework boundary | Full `verify` plus relevant HTTP integration coverage |

## Risks and Rollback/Recovery

If a PAT is exposed, revoke it in the console; issuing a replacement yields a new secret. Revocation does not erase the saved draft. A failed Flyway migration prevents startup rather than silently omitting credential controls; correct the migration in development and reset only disposable development data if necessary, never deployed data as a planning shortcut. Keep application and migration changes together; reverting the feature after real issuance needs an explicit data/operational decision rather than a compatibility adapter. Tests must prove lock ordering has no deadlock and that no stale authority survives a wait.

## References
- [Ticket](../tickets/PR-6.2-scoped-personal-access-tokens.md), [research](../research/PR-6.2-scoped-personal-access-tokens.md), [design lens](../design-lens.md)
- `src/main/java/ai/loomspan/sidecar/management/ManagementSecurityConfiguration.java`, `ManagementEditingService.java`, `ManagementIdentityService.java`, `ManagementConfigurationController.java`, `ManagementEditingController.java`
- `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java`, `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java`
