# Shared Authoring and Durable Drafts Testing Plan

## Change Summary

Replace session/tab-scoped transient drafts with one SQLite-backed complete draft per management account. Replace the editing grant with a server-issued, credential-bound editing session and explicit handoff; add revision compare-and-swap and current-base reconciliation; make import/rollback load the same draft rather than publish directly. Update the console to follow saved revisions without overwriting unsent local text.

The [design lens](../design-lens.md#simplicity-and-technical-debt) and repository policy require the simplest complete change and destructive replacement of obsolete development contracts, code and schemas, with no compatibility shims. Document any development-only reset; do not delete deployed data as verification. Tests use only public `ai.loomspan.api` framework types.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Durable storage | Missing/partial document or route content, duplicate per-user drafts, lost content after restart | SQLite store transaction/reopen tests and Spring restart integration |
| Privacy | Same-user read accidentally blocks, foreign user or admin takeover exposes content/IDs | HTTP tests with two accounts and two sessions of one account |
| Lease and revision | Delayed old-holder save, overlapping saves, expiry or takeover mutates content | Mutable-clock HTTP tests and latch-controlled service races |
| Authentication | Poll/save/validate silently renew session or lease; changed account continues after waiting | Clock-controlled HTTP and publication-gate tests |
| Validation/publication | Stale result or changed base publishes; failure deletes draft; success deletes others | Runtime hooks/latches and durable-state assertions |
| Import/rollback | Alternate direct cutover bypasses ownership/revision/validation | HTTP load-into-draft and obsolete-route tests |
| Browser | Poll or handoff overwrites unsent local text; stale draft disappears | Playwright two-context tests with local fixture server |
| Security/boundary | CSRF/role/JWT isolation changes; internal framework dependency | Existing security and ArchUnit suite plus focused negative HTTP cases |

## Existing Coverage and Environment Constraints

`ManagementEditingServiceTest`, `ManagementEditingHttpIntegrationTest`, `RuntimeConfigurationIntegrationTest`, `ManagementConfigurationHttpIntegrationTest`, and Playwright browser tests exercise the old tab/candidate and import/rollback confirmation behavior; update them to the replacement contract instead of retaining old behavior. `ConfigurationSnapshotStoreTest` shows the local SQLite/transaction/reopen pattern; use it for a new `ConfigurationDraftStoreTest`. HTTP fixtures use `@SpringBootTest(RANDOM_PORT)`, `@TempDir` SQLite, cookie-aware clients, synthetic accounts and a mutable `Clock`. Runtime tests have `Hooks` and latches for deterministic publication-gate races. Browser tests use local Playwright Chromium. Maven `verify` is the repository-standard safe suite. The local `1.0.0-beta.5-SNAPSHOT` starter is required; no framework build or external service is part of normal verification. If Chromium or the snapshot is absent, report the exact blocked command and residual risk rather than substituting hosted CI or live services.

## Failing Test First

- Name: `savedDraftSurvivesLogoutAndIsSharedOnlyWithSameAccount`
- Type: HTTP integration with file-backed SQLite.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementEditingHttpIntegrationTest.java`.
- Arrange/Act/Assert: Log in as account A in session 1, acquire, save multiple ordered skill documents and REST YAML, logout, log in as A in session 2 and B in session 3; A reads the same draft ID/content/revision while B receives 404 and neither session has an inherited lease/validation grant. Reopen the database in a fresh Spring context for the restart portion.
- Expected pre-fix failure: the old listener deletes A's draft on logout, `read` is session-keyed, and nothing is stored in SQLite.

## Tests to Add or Update

### 1. `oneCompleteDraftPerUserIsAtomicAndReopens`

- Type: SQLite store test.
- Location: `src/test/java/ai/loomspan/sidecar/storage/ConfigurationDraftStoreTest.java`.
- Proves: one account key, ordered documents plus full routes/source/base/revision persist; conditional updates change all content atomically; stale expected revision and forced SQL failure leave old content intact; discard affects only selected account; reopen retains content but no grant/validation.
- Inputs/fixture: temporary V3-migrated SQLite with two account rows and representative YAML/source labels.
- Doubles or boundary isolation: file database, `TransactionTemplate`, SQL trigger for forced failure as in snapshot tests.
- Edge cases: empty skill list, same-content save still increments revision, stale base UUID after history pruning.

### 2. `sharedReadHandoffAndTakeoverPreservePrivacy`

- Type: service and HTTP integration.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementEditingServiceTest.java` and `ManagementEditingHttpIntegrationTest.java`.
- Proves: two sessions of A read one draft; handoff rotates generation, new holder sees current revision before save, old grant and delayed write fail; B cannot read/use A identifiers; admin takeover gets only admin's own draft and A's saved work remains.
- Inputs/fixture: two A cookie sessions, B session, admin session, distinct complete configurations.
- Doubles or boundary isolation: local HTTP server/database, no remote calls.
- Edge cases: same session different editor tabs, double handoff, released/expired lease, forged user/account IDs in payload.

### 3. `revisionLeaseAndCredentialDeadlinesRejectLateWork`

- Type: mutable-clock HTTP and latch-controlled service integration.
- Location: `ManagementEditingHttpIntegrationTest.java` and `RuntimeConfigurationIntegrationTest.java`.
- Proves: concurrent expected-revision saves permit one winner; old holder, stale revision, foreign session and expired grant reject without content change; status/read/poll/save/validate do not extend either deadline; explicit renewal does not revive expired grant; explicit session activity follows the existing cadence.
- Inputs/fixture: `MutableClock`, two sessions and `CountDownLatch` before validation/admission.
- Doubles or boundary isolation: local clock/hooks, bounded waits with finally-release.
- Edge cases: session expiry, logout, password/role/enable changes during wait and after reacquisition, restart drops lease while keeping draft.

### 4. `staleDraftRequiresCompleteCurrentBaseSubmissionAndNewValidation`

- Type: HTTP and runtime integration.
- Location: `ManagementEditingHttpIntegrationTest.java` and `RuntimeConfigurationIntegrationTest.java`.
- Proves: publish by A makes B's draft stale but readable; ordinary save/validate/publish against old base conflict; explicit reconcile requires exact revision, current base and complete content, increments revision and clears validation; base-only relabel/partial payload cannot pass; fresh validation then permits publish.
- Inputs/fixture: two user drafts based on snapshot S1, publish A to S2, B reconciles to S2.
- Doubles or boundary isolation: local framework public reloader and database.
- Edge cases: base changes again between reconcile and publish, identical content still needs explicit submission, retained stale draft survives restart.

### 5. `publicationAdmitsExactValidatedDraftAndPreservesFailureState`

- Type: runtime integration with hooks/latches.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java` and `RuntimeConfigurationRecoveryIntegrationTest.java`.
- Proves: validation result cannot attach after revision, lease, account or base change; publication rechecks live credential/ownership/revision/base after waiting for gate; successful activation retains published snapshot, deletes only publishing draft and releases lease; preparation/commit/failed activation with successful revert retains it. Old admitted framework generation continues under existing coverage.
- Inputs/fixture: two accounts, exact valid skill/route candidate, `beforePreparation`, `beforeCommit`, `beforeFrameworkPublish` hooks and latches.
- Doubles or boundary isolation: local framework snapshot; inject store failure with existing hook/SQL patterns.
- Edge cases: outcome recording or draft cleanup fault after activation reports mutation fault and preserves honest runtime state; do not assert impossible rollback.

### 6. `importAndRollbackLoadDraftThenRequireValidation`

- Type: HTTP and browser integration.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/ManagementConfigurationHttpIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/management/ManagementImportBrowserIntegrationTest.java`, `ManagementHistoryBrowserIntegrationTest.java`.
- Proves: authorized current holder loads a valid bundle or retained snapshot into their own draft at expected revision; source ID retained; another holder/stale revision rejected; runtime unchanged until shared validate/publish; absent validation or changed base blocks publish; obsolete direct confirmation endpoints unavailable.
- Inputs/fixture: existing local format-1 bundle and retained snapshot fixtures.
- Doubles or boundary isolation: local multipart parser/SQLite, no live remote import.
- Edge cases: invalid/oversize bundle, missing/pruned rollback source, failed load retains previous draft, competing lease.

### 7. `readonlyPollingPreservesUnsentLocalTextAndRecoversAfterHandoff`

- Type: Playwright browser integration.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementEditorBrowserIntegrationTest.java`.
- Proves: same-user second session follows newer saved revision read-only with holder label; explicit take-control reads latest revision; unsent local text stays visible/separate and is not autosaved after ownership loss or handoff; saved and published states are distinct; stale draft remains visible and reconciliation requires conscious submit.
- Inputs/fixture: two isolated browser contexts, route interception to delay save/poll, local server.
- Doubles or boundary isolation: Playwright local fixture; no external browser account.
- Edge cases: failed save, late poll, changed published base while local text is dirty, second session handoff.

### 8. `editingSecurityAndFrameworkBoundaryRemainEnforced`

- Type: HTTP/security and ArchUnit regression.
- Location: `src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java`, `ManagementEditingHttpIntegrationTest.java`, existing framework boundary test.
- Proves: missing CSRF yields 403 for new mutations; viewer reads own saved draft but cannot edit/publish; editor cannot admin-takeover; management login cannot call `/v1/**`; execution JWT cannot access management; only supported `ai.loomspan.api` types appear in application/tests.
- Inputs/fixture: existing local JWT fixture, CSRF cookie sessions and account roles.
- Doubles or boundary isolation: test keys/local HTTP only.
- Edge cases: mixed/foreign identifiers reveal no private content; obsolete route has no active mutation handler.

## Safe Verification Commands

- Focused: `./mvnw.cmd -B -ntp -Dtest=ConfigurationDraftStoreTest,ManagementEditingServiceTest,ManagementEditingHttpIntegrationTest test`
- Related suite: `./mvnw.cmd -B -ntp -Dtest=RuntimeConfigurationIntegrationTest,RuntimeConfigurationRecoveryIntegrationTest,ManagementConfigurationHttpIntegrationTest,ManagementEditorBrowserIntegrationTest,ManagementImportBrowserIntegrationTest,ManagementHistoryBrowserIntegrationTest,ConsoleSecurityIntegrationTest test`
- Full safe suite: `./mvnw.cmd -B -ntp verify`
- Contract cleanup inspection: `rg -n 'tabId|import/confirm|rollback/confirm|clearAll\(' src/main docs` (review matches; zero obsolete active paths expected).

## Optional Developer Checks

- In a backed-up, non-production deployment only, observe cross-browser handoff and process restart against real operator configuration. This is nonblocking and is not routine automated verification.

## Exit Criteria

- [ ] The red draft-survival test fails for the intended reason before implementation. The test was implemented after the production change, so a pre-fix red run was not performed.
- [x] New and updated tests pass, and obsolete behavior assertions are removed rather than accommodated.
- [x] The full safe `verify` suite passes against the installed beta.5 snapshot; 197 tests, 0 failures/errors, 3 skipped on 2026-09-24.
- [x] Every ticket acceptance criterion has executable evidence, including races, authorization changes while waiting, import/rollback and console behavior.
- [x] Routine tests use temporary SQLite, local framework/test keys and browser fixtures, with no live or deployed-data effects.
- [x] The material privacy, stale-base, persistence, concurrency and failure-recovery cases above pass.
- [x] Optional configured-environment checks are reported separately and are never represented as performed unless actually run. The cross-browser deployed check was not run.
