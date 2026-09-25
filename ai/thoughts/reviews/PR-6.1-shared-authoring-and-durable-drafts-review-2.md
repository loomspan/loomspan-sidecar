# PR 6.1 Shared Authoring and Durable Drafts Code Review — Cycle 2

## Scope and Repository State

Independent review of the ticket-scoped working tree on `main`, including staged, unstaged, and untracked production code, migration, console scripts, tests, and documentation. No staged changes were present. I reconstructed draft ownership, lease and credential admission, validation, publication, import/rollback, and browser polling from the current code before comparing the ticket and plans. I did not use an earlier review document.

## Findings

### [P1] Reject base-only stale-draft reconciliation
- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:126`
- Scenario: After another user publishes a new base, the holder posts the old saved configuration unchanged to `/editing/draft/reconcile` while supplying the new base ID.
- Impact: The server advances the draft revision and makes it publishable after validation without any actual current-base content submission. This violates the ticket's explicit prohibition on base-only relabeling.
- Evidence: The original `replace` path accepted any non-null complete configuration and, when `rebase` was true, changed the stored base without comparing content. The existing stale-base test only submitted modified content.
- Fix: Reject unchanged content when reconciliation changes the base; preserve import/rollback loading as distinct operations. Add an HTTP assertion that the old draft remains stale after rejection and document the rule.

## Findings Resolved in This Context

- Added a content equality check for stale-base reconciliation in `ManagementEditingService`, a regression assertion in `ManagementEditingHttpIntegrationTest`, and the corresponding operator guidance. Re-reviewed these changes and the complete affected flow; no further actionable finding remained in this context.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One shared management contract; obsolete paths removed | Editing/configuration controllers and console scripts; no old active route matches | HTTP and browser integration tests | Implemented |
| One private durable draft per user; restart drops grants | V3 draft tables, `ConfigurationDraftStore`, transient `ManagementEditingState` | Store, HTTP privacy, and restart integration tests | Implemented |
| Lease holder, generation, revision, expiry, and takeover protections | `ManagementEditingService` admission and conditional draft replacement | HTTP race, expiry, handoff, takeover tests | Implemented |
| Read-only polling preserves unsent text | `editor.js` polling, saved preview, take-control flow | Two-session and same-login browser tests | Implemented |
| Stale base requires explicit substantive submission and validation | Reconcile content check, base checks, validation proof | Updated stale-base HTTP test | Implemented |
| Exact publication; success/failure cleanup; import/rollback share path | Runtime publication admission and draft load services | Runtime and management configuration integration tests | Implemented |
| CSRF, roles, management/execution separation, framework boundary | Security chain, session guard, supported framework API usage | Security and ArchUnit tests in full verify | Implemented |
| Documentation and no compatibility machinery | Operations/integration guides; obsolete-path search | Full verify and contract search | Implemented |

## Active Project Guardrails

- Superseded browser/tab and direct-confirmation contracts were removed without adapters. The V3 migration adds durable drafts to the development schema; documentation describes a backed-up development-only reset if an experimental schema conflicts.
- Draft storage is account-owned; lease/validation grants remain transient. Authentication, authorization, and execution identity remain separate. Framework validation and publication use supported API types; no internal or autoconfigure dependencies were introduced.
- The implementation uses the existing runtime publication gate and one saved draft per account. No duplicate publication route or automatic merge was found.

## Open Questions and Assumptions

- None affecting correctness. A configured deployment observation remains optional.

## Verification Results

- PASS — `./mvnw.cmd -B -ntp '-Dtest=ConfigurationDraftStoreTest,ManagementEditingServiceTest,ManagementEditingHttpIntegrationTest' test` — 9 tests, 0 failures/errors.
- PASS — `./mvnw.cmd -B -ntp '-Dtest=ManagementEditingHttpIntegrationTest' test` — 7 tests, including the added base-only reconciliation assertion.
- PASS — `./mvnw.cmd -B -ntp verify` — 198 tests, 0 failures/errors, 3 skipped; packaged jar built.
- PASS — `rg -n 'tabId|import/confirm|rollback/confirm|clearAll\(' src/main docs` — no matches.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- The three skipped tests are existing conditional tests; the full relevant local suite otherwise passed. In a backed-up, non-production deployment, an operator may optionally observe cross-browser handoff and process restart with real configuration. No deployed data or live external service was touched for verification.

## Disposition

- `fixes-applied`. A fresh independent Step 5 context is required to certify these implementation changes.
