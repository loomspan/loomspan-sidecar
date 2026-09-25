# Scoped Personal Access Tokens Testing Plan

## Change Summary

Add browser-only PAT issuance/list/revocation, central PAT bearer authentication on the existing management API, cumulative Read/Edit/Publish ceilings intersected with live account roles, origin-bound editing leases, and revocation at mutation gates. Preserve browser CSRF, PR 6.1 candidate/lease/base protections, and JWT-only execution. **Development contract:** destructively replace superseded development behavior, without compatibility shims or migrations solely for obsolete contracts; apply the [design lens](../design-lens.md#simplicity-and-technical-debt). No development-data reset is expected for the additive V4 table; never delete deployed data as a planning step.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Persistence and secret handling | Secret persisted or exposed after creation; other-user token access | SQLite row inspection, response and list assertions, owner-scoped revoke tests |
| Authentication/CSRF | Browser cookie bypasses CSRF; invalid bearer falls back to cookie | Real HTTP matrix for bearer, cookie, both, bad bearer, missing CSRF |
| Permission/role policy | Broad authenticated fallback or stale role grants action | Every route family and viewer/editor/admin by Read/Edit/Publish matrix |
| Editing ownership | Lease ID acts as authority, revoked token keeps editing | Cross-user, same-user handoff, old-origin rejection and clock/revocation tests |
| Publication | Edit bypasses Publish, stale candidate or base activates | Real candidate lifecycle with validate/publish, import/rollback, stale/mismatched values |
| Delayed work | Revocation/role reduction races with mutation lock or validation | Latch-controlled waits before service admission, assert no mutation and draft preserved |
| Account lifecycle | Password/reset/disable fail to revoke; re-enable resurrects token | HTTP/account transition tests with persisted token row checks |
| Execution boundary | PAT accepted as JWT or JWT accepted as management | Fixture JWT and PAT cross-surface HTTP tests |
| Abuse/audit | Unlimited attempts/issuance or token leaked to logs | Bounded limiter/count tests and captured structured audit assertions |

## Existing Coverage and Environment Constraints

`ManagementEditingHttpIntegrationTest` already exercises cookie editing, handoff, private drafts, stale bases, CSRF, viewers, takeover and concurrent saves. `ManagementEditingServiceTest` covers delayed validation; `ManagementDraftRestartIntegrationTest` covers persistent drafts versus ephemeral leases; `ManagementImportBrowserIntegrationTest` and `ManagementHistoryBrowserIntegrationTest` cover import/rollback; `ManagementHttpIntegrationTest` covers account/password flows and execution separation; `ManagementAttemptLimiterTest` covers bounded counters. `ConsoleSecurityIntegrationTest` and execution HTTP tests use fixture JWTs. Tests use local HTTP, temporary SQLite, controllable clocks, and fixture keys. The local Maven wrapper and installed beta.5 framework snapshot are required; no live OAuth, SMTP, external identity service, production deployment, or framework build is part of routine verification. Browser/Playwright checks already present can be extended only for one-time UI behavior that HTTP assertions cannot prove.

## Failing Test First
- Name: `readTokenUsesSharedManagementApiAndCannotAcquireLease`
- Type: local HTTP integration test.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementPersonalTokenHttpIntegrationTest.java`.
- Arrange/Act/Assert: create an editor account and issue a Read PAT through a CSRF-protected browser session; call `GET /api/management/configuration/current` and `GET /api/management/editing/draft` with `Authorization: Bearer ...`, then call `POST /api/management/editing/lease` with the same PAT. The reads succeed using existing response contracts; lease acquisition is 403 and no lease is created.
- Expected pre-fix failure: no PAT issuance route or bearer authentication exists, so the initial issuance or authenticated read fails.

## Tests to Add or Update

### 1. `personalTokenLifecycleIsOwnerScopedAndSecretIsOneTime`
- Type: SQLite-backed HTTP integration.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementPersonalTokenIntegrationTest.java`.
- Proves: browser with CSRF can issue/list/revoke only its own tokens; create response alone contains the secret and is `no-store`; DB stores digest only, not secret; list and revoke response contain safe metadata; wrong owner cannot revoke or enumerate.
- Inputs/fixture: two users, known clocks, Read/Edit/Publish presets, temporary database.
- Doubles or boundary isolation: local HTTP and database only.
- Edge cases: default seven days; custom future expiry; now/past/over-30-day and malformed expiry rejected; expiration rejects authentication and no refresh/replacement bearer route exists; repeated revoke is safe and never returns a secret.

### 2. `personalTokenIssuanceAndAuthenticationAreBounded`
- Type: service/HTTP integration.
- Location: `ManagementPersonalTokenIntegrationTest.java` and `src/test/java/ai/loomspan/sidecar/management/ManagementAttemptLimiterTest.java` if limiter behavior needs extension.
- Proves: selected issuance rate and active-count caps, bounded per-IP failed bearer attempts, fail-closed limiter-capacity behavior, maximum Authorization header size, and recovery after window expiry. Exact limits must match plan implementation and docs.
- Inputs/fixture: controllable clock, many distinct invalid secrets/IP values, temp DB.
- Doubles or boundary isolation: no external service; isolate process-local limiter per fixture.
- Edge cases: malformed token, unknown ID, wrong secret, revoked/expired ID, too-long header, repeated valid token after failures, limit boundary and post-window recovery. Failure bodies must be generic.

### 3. `managementCredentialMatrixKeepsCookieCsrfAndRejectsMixedAuth`
- Type: local HTTP integration.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementPersonalTokenHttpIntegrationTest.java`.
- Proves: cookie browser requests still need CSRF on unsafe operations; bearer-only authoring calls work without CSRF; bearer plus session cookie is rejected whether bearer is valid or invalid; invalid/malformed bearer never falls back to cookie or anonymous access; PAT requests do not create a servlet session.
- Inputs/fixture: editor browser session, three PAT presets, invalid and malformed Authorization headers.
- Doubles or boundary isolation: real security filter chain and HTTP client cookie handling.
- Edge cases: bearer on HTML page, lifecycle API, login/setup/password, session activity/logout, account admin and takeover; credential combination with and without CSRF. Assert deny by default for unmatched PAT routes.

### 4. `presetAndLiveRoleMatrixCoversEveryManagementOperation`
- Type: parameterized local HTTP integration.
- Location: `ManagementPersonalTokenHttpIntegrationTest.java`.
- Proves: Read allows current/export/history/status/own draft only; Edit additionally allows acquire/handoff/renew/release/save/reconcile/validate/discard and import/rollback review/load; Publish additionally allows publish. Browser editor/admin flow remains available. Viewer with Publish PAT reads only; editor/admin with Read PAT cannot mutate; role reduction immediately narrows authority on the next request without revoking the PAT row.
- Inputs/fixture: viewer/editor/admin accounts; each preset; minimal valid payloads and fixtures for every route family.
- Doubles or boundary isolation: local HTTP, fixture bundle and temporary DB.
- Edge cases: wrong method, private other-user draft/session/token ID; admin-owned PAT cannot administer accounts, issue PATs, or take over. Labels and environment names do not alter permissions.

### 5. `tokenOriginOwnsLeaseAndExplicitHandoffRotatesGeneration`
- Type: local HTTP integration.
- Location: `ManagementPersonalTokenHttpIntegrationTest.java`.
- Proves: an Edit/Pub PAT acquires a lease; same user in browser or another PAT sees saved draft and can explicitly hand off; old origin plus old editing ID/generation fails; another user cannot read draft or hand off. Revoked/expired originating PAT cannot use the lease, and no draft is deleted.
- Inputs/fixture: two users, two PATs for one user, browser session, controllable clock.
- Doubles or boundary isolation: real editing service and temp SQLite.
- Edge cases: forged editing-session ID, wrong generation, session listener clears only matching browser origin, PAT expiry while holding lease, lease timeout, same-user handoff without admin privilege.

### 6. `publishRequiresPublishPresetAndExactlyValidatedCandidate`
- Type: local HTTP integration.
- Location: `ManagementPersonalTokenHttpIntegrationTest.java` or `ManagementPersonalTokenGateIntegrationTest.java`.
- Proves: Edit PAT can save/import/rollback-load and validate but receives 403 on publish; Publish PAT owned by editor/admin can activate the exact validated candidate through the existing endpoint with no UI review toggle; no import/rollback path activates directly.
- Inputs/fixture: fixture configuration/bundle and retained history snapshot.
- Doubles or boundary isolation: public framework snapshot installed locally, no live downstream service.
- Edge cases: changed draft revision/generation/base, failed validation, stale base, conflicting lease, concurrent replacement. Verify failed publication retains the draft and PR 6.1 history behavior.

### 7. `revocationAndRoleChangeWinAtMutationGate`
- Type: deterministic latch-controlled integration.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementPersonalTokenGateIntegrationTest.java`.
- Proves: when a token-backed save/validate/import-load/publish request waits before the editing or publication admission, revocation/expiry or editor-to-viewer reduction makes the gate reject it; runtime and saved draft are unchanged except for the already saved pre-wait content. Same-user handoff also invalidates the old lease after the wait.
- Inputs/fixture: controllable clock and test latch around existing runtime editing/publication transition, exact candidate IDs.
- Doubles or boundary isolation: local in-process HTTP/service fixture; never sleep for race timing.
- Edge cases: role reduction does not erase token metadata; expiry/revocation prevents stale lease reuse; no deadlock between identity transition and publication locks; validation result cannot attach after loss of authority.

### 8. `accountCredentialTransitionsPermanentlyRevokeTokens`
- Type: SQLite-backed HTTP integration.
- Location: `ManagementPersonalTokenIntegrationTest.java` and/or `ManagementPersonalTokenGateIntegrationTest.java`.
- Proves: password change, setup/set/reset/recovery where relevant to an account, and disablement revoke all its PATs transactionally; re-enable does not reactivate them; saved draft survives. Role reduction alone changes effective permission live.
- Inputs/fixture: active users, browser sessions, local reset credential fixture, temporary DB.
- Doubles or boundary isolation: existing fake/local mail fixture; no SMTP.
- Edge cases: password changes while token holds lease; multiple PATs; re-enable and old token reuse; reset token is never a PAT.

### 9. `managementAndExecutionCredentialsStaySeparate`
- Type: local HTTP integration.
- Location: `ManagementPersonalTokenHttpIntegrationTest.java`, extending `ManagementHttpIntegrationTest.java` only where shared fixtures make that smaller.
- Proves: PAT and browser cookie cannot invoke `/v1/**`; execution JWT cannot read or mutate `/api/management/**`; management rejects a valid execution JWT as a PAT; no management token is forwarded to execution.
- Inputs/fixture: local fixture JWT keys, one issued PAT.
- Doubles or boundary isolation: real ordered security chains, local execution fixture.
- Edge cases: same `Authorization: Bearer` scheme on different chains; cookie plus execution JWT.

### 10. `auditEventsIdentifyActionWithoutSecret`
- Type: captured-log integration.
- Location: `ManagementPersonalTokenIntegrationTest.java` and `ManagementPersonalTokenHttpIntegrationTest.java`.
- Proves: issuance, revocation, denied authentication/authorization, draft mutation/validation, and publish success/failure emit structured actor ID, token ID when known, action, outcome, and relevant candidate/base/published version. No event, exception or normal response contains raw secret or Authorization header.
- Inputs/fixture: known PAT and candidate ID, in-memory logging appender.
- Doubles or boundary isolation: local logs only.
- Edge cases: unknown token has no actor/token ID; failure body and `toString` do not leak secret.

### 11. `consoleShowsSecretOnlyAtCreation`
- Type: existing browser integration convention, or focused DOM/HTTP assertion where sufficient.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementEditorBrowserIntegrationTest.java` or a dedicated adjacent browser test.
- Proves: user can create/list/revoke from console, copy the one-time secret, and cannot retrieve it after navigation/refresh; another user never sees the record.
- Inputs/fixture: local browser and temporary DB.
- Doubles or boundary isolation: disposable local server; browser test may be gated by repository's existing Playwright availability convention.
- Edge cases: expiry validation message and pending request failures without secret persistence.

## Safe Verification Commands
- Focused: `./mvnw.cmd -B -ntp -Dtest=ManagementPersonalTokenIntegrationTest,ManagementPersonalTokenHttpIntegrationTest,ManagementPersonalTokenGateIntegrationTest test`
- Related suite: `./mvnw.cmd -B -ntp -Dtest=ManagementEditingHttpIntegrationTest,ManagementEditingServiceTest,ManagementDraftRestartIntegrationTest,ManagementImportBrowserIntegrationTest,ManagementHistoryBrowserIntegrationTest,ManagementHttpIntegrationTest,ConsoleSecurityIntegrationTest,ManagementAttemptLimiterTest test`
- Full safe suite: `./mvnw.cmd -B -ntp verify` (uses locally installed beta.5 snapshot; no production services). Run browser tests under the repository's existing availability convention and report any skipped fixture/browser as such.

## Optional Developer Checks
- In a configured non-production HTTPS deployment, inject the one-time PAT through the client's environment and confirm a read/publish call and audit metadata. Do not put secrets in command arguments, URLs, repository files, or request payloads. This observation is nonblocking and is not represented as already performed.

## Exit Criteria
- [ ] The planned red test fails for the intended missing capability before implementation.
- [x] New and updated tests pass after implementation, including one-time secret and owner isolation assertions.
- [x] The related suites and full `verify` pass against the installed framework snapshot: 216 tests, zero failures/errors, three optional production Compose browser skips.
- [x] Every ticket acceptance criterion maps to executable evidence in the implementation plan and actual results.
- [x] Routine automated tests use only disposable local data and no unintended live or destructive operation.
- [x] Delayed-gate, credential separation, CSRF, live-role, and exact-candidate edge cases pass deterministically.
- [x] Optional configured-environment checks are reported separately and do not masquerade as completed verification.

## Step 4 execution note

The HTTP scenarios were consolidated in `ManagementPersonalTokenHttpIntegrationTest`; the bounded bearer-filter scenarios are in `ManagementBearerFilterTest`. Deterministic delayed validation and publication admission scenarios are in `ManagementEditingServiceTest`. The planned pre-fix red test was not run before implementation; it must not be treated as observed evidence. The configured HTTPS deployment check and manual clipboard inspection were not run. Local HTTP tests use disposable SQLite databases and fixture credentials.
