# PR 6.1 Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket-scoped staged, unstaged, and untracked work in the current checkout: the SQLite draft schema/store, editing lease and security flow, runtime publication, import/rollback, console scripts, tests, and operator documentation. The comparison base is the current `HEAD`; no unrelated changes were identified. I traced request admission through account/session checks, revision storage, validation, publication locks, cleanup, and browser polling before comparing with the plans.

## Findings

No actionable findings remain after the fixes below.

## Findings Resolved in This Context

### [P1] Detect handoff between tabs sharing one login session
- Location: `src/main/resources/static/management/assets/editor.js:138`, `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:22`
- Scenario: Two tabs share one HTTP session. The second tab explicitly takes control. The first tab's poll saw `mine: true` because that flag checked only the HTTP session ID, so it left the old grant and editor active until a mutation failed.
- Impact: The console did not reflect the current holder and allowed the displaced tab to continue editing local text, contrary to the required read-only handoff behavior.
- Evidence: The lease rotates `editingSessionId` and generation on handoff, while the original status response exposed only session-level `mine`. The new same-login-session browser test exercises this path.
- Fix: Include the current editing session ID in status only for the holder's login session, and compare it with the tab's grant during polling.

### [P2] Require explicit resumption of retained local text
- Location: `src/main/resources/static/management/assets/editor.js:157`
- Scenario: After losing control with unsaved local text, the user takes control again. The old flow immediately made that retained text editable; the next keystroke triggered automatic save of the entire retained document against the newly returned saved revision.
- Impact: Retained unsent text could replace newer server-saved content without an explicit decision to reuse it.
- Evidence: `acquire()` retained local content with `setDraft(..., false)` but set `editing = true`; `changed()` scheduled the whole-content save. The browser test now covers reacquisition and explicit resume.
- Fix: Keep retained text read-only after reacquisition and provide **Resume editing unsaved local text** as a separate user action.

### [P2] Repair browser assertions that cannot match their own element
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementEditorBrowserIntegrationTest.java:67` and companion history/import browser tests.
- Scenario: Tests used `locator("#text-element").getByText(...)` on elements containing only direct text; Playwright searched descendants and timed out. The initial full suite showed all three browser timeouts despite the page actions proceeding.
- Impact: Required browser assurance failed and could hide console regressions.
- Evidence: The initial full `verify` reported three browser timeouts at these locators. Replacing them with waits on the element's own `textContent` made all four focused browser cases pass.
- Fix: Wait on the actual text element in the editor, history, and import browser tests.

### [P2] Prepare both test publication candidates before pausing the first commit
- Location: `src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java:376`, `src/test/java/ai/loomspan/sidecar/support/RuntimePublicationFixture.java:27`
- Scenario: In the competing-publication test, the second worker created its fixture account and draft while the first publication was paused immediately before SQLite submission.
- Impact: Concurrent fixture writes could make the first commit fail from database contention, so the test failed before exercising its intended publication-gate behavior.
- Evidence: A full `verify` run failed in `competingPublicationRechecksBaseAndUsesAcceptedFrozenContent` with `commit_failed` for the first worker; the fixture performed account/draft writes before acquiring the publication gate.
- Fix: Build both immutable publication admissions before starting either worker. The workers now exercise only the serialized publication path.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One shared contract; old paths removed | Editing/configuration controllers and console scripts | HTTP/browser tests; obsolete-path search | Implemented |
| Private durable complete draft | V3 and `ConfigurationDraftStore` | Store, HTTP, and restart tests | Implemented |
| Lease, handoff, expiry, revision races | `ManagementEditingService`, session/account invalidation | HTTP/service and same-session browser tests | Implemented |
| Read-only polling and retained local text | `editor.js`, editor page | Editor browser tests | Implemented |
| Explicit stale-base reconciliation | Draft replace and exact validation admission | HTTP stale-base test | Implemented |
| Exact publication; import/rollback same path | Runtime service and load services | Runtime/configuration HTTP and browser tests | Implemented |
| CSRF, roles, execution isolation, public framework boundary | Security chain and public API calls | HTTP security and ArchUnit suite | Implemented |
| Documentation and safe reset | `docs/operations.md`, `docs/integration.md` | Document and migration inspection | Implemented |

## Active Project Guardrails

- Superseded tab/candidate and direct confirmation contracts are replaced without production compatibility routes. The documented development-only reset does not authorize deployed-data deletion.
- Application and tests use the supported `ai.loomspan.api` framework surface; ArchUnit checks the boundary. No new framework SPI is introduced.
- Verification uses the installed beta.5 snapshot and local SQLite/browser fixtures, without a framework rebuild or live service operation.

## Open Questions and Assumptions

None.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ManagementEditorBrowserIntegrationTest,ManagementHistoryBrowserIntegrationTest,ManagementImportBrowserIntegrationTest,AuthenticatedExecutionApiIntegrationTest' test` — 18 tests, zero failures/errors after browser assertion fixes.
- PASS — `node --check src/main/resources/static/management/assets/editor.js; node --check src/main/resources/static/management/assets/console.js; node --check src/main/resources/static/management/assets/import.js` — JavaScript syntax accepted.
- PASS — `rg -n 'tabId|import/confirm|rollback/confirm|clearAll\(' src/main docs` — no obsolete contract matches.
- PASS — `git diff --check` — no whitespace errors (Git printed line-ending conversion notices).
- FAIL — `.\mvnw.cmd -B -ntp verify` — 198 tests, zero assertion failures, one error, three skipped. All groups except `competingPublicationRechecksBaseAndUsesAcceptedFrozenContent` passed; its fixture race was fixed afterward.
- PASS — `.\mvnw.cmd -B -ntp '-Dtest=RuntimeConfigurationIntegrationTest' test` — all 12 runtime integration tests, including the competing-publication case, passed after the fixture fix.

## Residual Risks and Optional Developer Checks

Optional backed-up, non-production cross-browser and restart observation from the testing plan was not run. The initial full suite had three execution failures under load; all 14 execution tests passed on focused rerun and the next full run. The fresh review context should run full verification against all fixes together.

## Disposition

`fixes-applied`
