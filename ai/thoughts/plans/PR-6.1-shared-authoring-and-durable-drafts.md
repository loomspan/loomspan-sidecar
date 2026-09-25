# Shared Authoring and Durable Drafts Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/PR-6.1-shared-authoring-and-durable-drafts.md`
- Research: `ai/thoughts/research/PR-6.1-shared-authoring-and-durable-drafts.md`
- Outcome: One durable, private, complete configuration draft per management user; an explicit session-owned application lease; one management API and console flow for save, reconciliation, validation, and publication.

## Current State

`ManagementEditingState` stores drafts by HTTP session and a tab-shaped lease in memory. `ManagementEditingService` clears a draft when its base changes; `ManagementEditingSessionListener` and account changes delete it, and `RuntimeConfigurationService.publishCandidate` clears every draft. `ManagementConfigurationImportService` and `ManagementConfigurationRollbackService` have separate direct publication paths. `editor.js` polls lease/runtime but does not poll another session's saved revision and clears its view on base change. SQLite `V1`/`V2` persists snapshots/accounts, but no draft. The matching local framework checkout documents public `SkillReloader.validate/prepare/publish`, which the current runtime service already uses.

## Desired End State

An authenticated user reads only that user's saved draft across sessions and restarts. The saved document list and REST YAML remain intact after logout, lease loss, account changes, and a changed published base. Only a live, current editing session may change or publish it. Every saved change increments a revision and removes prior validation. A different same-user session can explicitly take control and must read the current revision before saving. A stale draft requires an explicit complete current-base submission and fresh validation; the server neither merges content nor accepts a base-label-only request. Publishing an exact validated revision clears only its owner's draft on success; failures before activation retain it. Import and rollback load content into this same draft workflow. The console separately identifies local unsent text, saved draft, and published configuration.

## Scope

### In scope

- SQLite draft persistence, account isolation, revision checks, lifecycle, lease, editing HTTP contract, console, import/rollback conversion, tests and operator/API documentation.
- Replace obsolete tab/candidate and direct import/rollback confirmation contracts and tests. Document any reset needed for development databases; never delete deployed data as routine work.

### Out of scope

- Personal tokens, separate agent API, OAuth, MCP, named drafts, merge engine, unpublished execution, publication policy by environment, framework internals or new SPIs.

## Active Project Guardrails

The [design lens](../design-lens.md#simplicity-and-technical-debt) and repository `AGENTS.md` require destructive replacement of superseded development contracts and schemas, without compatibility shims, adapters, or migration machinery solely to preserve obsolete behavior. A required development-data reset must be documented; planning does not authorize deployed-data deletion. Use the closed public `ai.loomspan.api` surface, framework validation/publication authority, one management API for console and later clients, JWT-only execution identity, exact candidate/base checks and one framework shutdown budget.

## Impact and Risk Analysis

- **Privacy:** user ID comes from live server authentication. Draft ID, revision and lease IDs are concurrency markers, never authority. No other user's content appears in status, conflict responses, takeover, history or logs. Admin takeover sees their own draft and holder identity only.
- **Concurrency:** keep the existing transition/publication lock order; serialize draft repository mutations and lease state under the transition gate. Recheck account, HTTP session activity, holder session, generation, draft ID, revision and base after slow validation and after waiting for publication. Use monotonic revisions and conditional SQL updates so delayed writes cannot win.
- **Persistence:** store ordered documents and full routes with a one-row-per-account unique key. Draft base UUID must remain usable even if its historical snapshot is pruned; stale means `baseSnapshotId != published.localId`. No validation proof or lease is persisted, so restart forces validation and reacquisition.
- **Publication:** existing snapshot submission precedes framework activation. Leave draft intact through prepare, staging, commit and failed activation/revert. Delete only the published owner's draft after activation, and mark a mutation fault if durable cleanup fails. Other drafts become stale by base comparison; never clear them globally. Keep published snapshot as durable outcome and existing generation retention.
- **Credential lifetime:** session ID plus server-issued editing session ID/generation bind the grant to the login session; account version is checked live. Neither polling, saving, validating nor framework work extends session or lease deadlines. Explicit user-activity reports and lease renewal alone do so.

## Implementation Approach

Use `ConfigurationDraftRepository`/`ConfigurationDraftStore` alongside existing snapshot storage and transactions. Persist draft ID, account ID, base snapshot ID, positive revision, complete configuration and optional source snapshot ID; keep successful validation only in `ManagementEditingState` keyed to exact draft ID/revision/base and lease generation. Persisting validation would require a durable proof across restart, contrary to the ticket's invalidation rule. A single in-memory lease remains adequate because stale grants must die at restart. The lease records authenticated HTTP session ID, user ID, server-generated editing session ID, generation UUID and deadline. The server issues a fresh editing session ID on acquisition/handoff; no browser `tabId` is accepted. Acquire is available when lease is free; same-user handoff explicitly rotates generation, returns current draft/revision, and old holder loses authority. Admin takeover rotates generation but never reads or deletes another user's draft.

Keep the existing management paths for status/read/save/validate/publish where useful, but replace their payloads and remove tab-based fields. Define the following coherent contract in `docs/operations.md` and implement it exactly: `GET /editing` returns holder display/expiry and whether the caller holds control; `GET /editing/draft` returns only the caller's saved draft, `stale` and validation state; `POST /editing/lease` acquires a free lease; `POST /editing/lease/handoff` takes over a lease held by another session of the same user; `POST /editing/lease/takeover` remains admin-only; `POST /editing/lease/renew` and `/release` require the current editing session ID and generation. Grant responses include the current user's draft and revision. `PUT /editing/draft` carries editing session ID, generation, draft ID, expected revision, full content and expected base ID. Ordinary save requires the draft's stored base to equal the runtime base. An explicit `POST /editing/draft/reconcile` carries the same holder/revision checks, current published base ID and **complete** content, increments revision and changes base in the same durable write. It cannot merely relabel a base because it requires a newly submitted complete configuration; the server does not judge semantic reconciliation. `POST /editing/draft/validate` and `/configuration/publish` carry editing session ID, generation, draft ID, revision and base ID. A create-on-first-acquire draft starts from the published snapshot. `DELETE /editing/draft` requires ownership when a live lease exists; no cross-user discard. Conflict codes distinguish grant, revision, stale base and validation problems without exposing content.

Convert import and rollback into **load into draft** actions requiring the current lease, expected revision and current base; use the existing bundle parser and retained snapshot lookup. Their response is the new saved draft revision and optional source ID. The user then validates and publishes through the ordinary path. Remove obsolete direct confirmation and session-held review publication authority. Preview/review may remain read-only but cannot publish. Preserve source ID in the draft for resulting snapshot provenance, rather than letting a separate API call `publishCandidate`. This is simpler than maintaining two more gate and validation protocols.

## Implementation decisions

- The repository and transactional store are one `ConfigurationDraftStore` class. The
  SQL is small and local, and the class keeps whole-draft writes in one
  transaction without a second abstraction.
- Publication holds the runtime transition gate from admission through
  activation. This prevents a save from replacing the exact admitted saved
  revision before activation. Account and lease state are still checked after
  waiting for the publication gate.
- The former in-memory `ConfigurationDraft` and frozen candidate helpers are
  test fixtures only. Production validation and publication use the durable
  draft revision and process-local exact validation proof.
- V3 is additive to V1/V2. Existing development databases using those
  migrations need no reset. A conflicting experimental local schema requires
  a stopped, backed-up, development-only reset as documented in operations.

## Phase 1: Durable private draft and lease model

### Changes

- [x] `src/main/resources/db/migration/V3__management_drafts.sql` — add unique account-owned draft header, ordered skill document rows, revision and base ID constraints; cascade child rows on draft deletion. Existing V1/V2 deployed rows remain. If local experimental schemas conflict with V3, document a **development-only** reset after backup instead of introducing compatibility machinery.
- [x] `src/main/java/ai/loomspan/sidecar/storage/ConfigurationDraftStore.java`, `StorageConfiguration.java` — add transactional load/create, conditional whole-content replacement/reconciliation, delete-by-account-and-exact-revision and serialization checks; store only saved content, no lease/validation.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementEditingState.java`, `ManagementEditingService.java`, `ManagementEditingSessionListener.java`, `ManagementIdentityService.java` — replace session-keyed drafts with transient lease and validation association; invalidate grants on session destruction/account change without deleting content; explicit acquisition, renewal, same-user handoff, admin takeover and release. Use 30-minute management idle and 15-minute edit lease defaults already configured; only explicit activity endpoints extend either deadline. Live account/credential version and session activity checks remain admission requirements.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementEditingController.java`, `ManagementConfigurationController.java`, `ManagementSecurityConfiguration.java` — replace tab-shaped request/response records, enforce same role/CSRF boundaries, expose explicit reconciliation and handoff. Reject obsolete payloads rather than retaining dual paths.

### Automated verification

- [x] `./mvnw.cmd -B -ntp -Dtest=ConfigurationDraftStoreTest,ManagementEditingServiceTest,ManagementEditingHttpIntegrationTest test` — one draft per user survives reopening; foreign user isolation; grant and revision races; account/session/lease expiry and no implicit renewal.

### Optional developer checks

- [ ] None.

## Phase 2: Exact validation and one publication path

### Changes

- [x] `ManagementEditingState.java`, `ManagementEditingService.java` — retain only an exact in-process successful validation result for draft ID/revision/base and generation; clear it on any save, reconcile, handoff, loss of lease, account/session invalidation or restart. Capture validation input under transition gate, call existing `runtime.validate` outside, and attach only after full live recheck.
- [x] `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java`, `ConfigurationSnapshotStore.java` — admit publish under publication lock with full live checks, use existing public framework prepare/publish, delete only the publishing user's draft after confirmed activation, preserve drafts on pre-activation failures and retain others as stale. Treat cleanup failure after activation as mutation fault, never claim rollback of a published generation.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementConfigurationImportService.java`, `ManagementConfigurationRollbackService.java`, `ManagementConfigurationController.java` — remove direct confirmation publication methods; import and retained-history restore save complete content through the lease/revision workflow, preserving source provenance. Keep parsing/preview and size protections, but require subsequent exact validation and normal publication.

### Automated verification

- [x] `./mvnw.cmd -B -ntp -Dtest=RuntimeConfigurationIntegrationTest,RuntimeConfigurationRecoveryIntegrationTest,ManagementConfigurationHttpIntegrationTest test` — exact candidate/base and post-wait admission, failures preserve draft, successful publication isolates cleanup, import/rollback cannot bypass the path.

### Optional developer checks

- [ ] None.

## Phase 3: Console and documentation

### Changes

- [x] `src/main/resources/static/management/assets/editor.js`, relevant management HTML/CSS and import/history scripts — poll saved draft revision while read-only; display holder, local unsent, saved and published states; explicit same-user take-control. When a poll reveals a newer saved revision or ownership loss, preserve unsent local text separately and never auto-resubmit it. On base change, keep stale draft visible and require user-guided complete reconciliation/validation before publish. Import and rollback load into the draft, then use shared controls.
- [x] `docs/operations.md`, `docs/integration.md`, any console help text — document one API contract, expiry/handoff, stale-base recovery, import/rollback steps, durable storage and backup, and any development-only reset. Remove obsolete tab/session draft and cutover deletion guidance.
- [x] Update old tests that encode tab/candidate/direct-confirm semantics; add browser coverage in `ManagementEditorBrowserIntegrationTest.java`, `ManagementImportBrowserIntegrationTest.java`, `ManagementHistoryBrowserIntegrationTest.java`.

### Automated verification

- [x] `./mvnw.cmd -B -ntp verify` — full safe local suite, including browser fixtures, CSRF and framework-boundary tests, using the installed beta.5 snapshot. 197 tests, 0 failures/errors, 3 skipped on 2026-09-24.
- [x] `rg -n 'tabId|import/confirm|rollback/confirm|clearAll\(' src/main docs` — no obsolete production/documentation contract remains (inspect any legitimate test-only references separately).

### Optional developer checks

- [ ] If a configured non-production deployment is available, observe two browser sessions and a restart against a backed-up development database; report separately from automated gates. No live service or deployed-data mutation is a routine check.

## Test Strategy

First establish a red HTTP integration test for one user's saved draft surviving logout and a second login. Test SQL durability and revision compare-and-swap below HTTP; test credential/CSRF/role and cross-session races at HTTP; test framework publication waits and failure hooks at runtime integration level; test local-text behavior with existing Playwright fixtures. Replace obsolete assumptions rather than adding compatibility assertions. See the companion testing plan for named cases and safe commands.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| One shared management contract, obsolete paths removed | Editing/configuration controllers and scripts | HTTP contract and browser flows; obsolete-route checks |
| Durable, private one-per-user complete draft | V3, draft store, editing read | Store restart and two-session HTTP privacy tests |
| Current lease holder only; handoff/takeover/races | Lease state, service live checks and revision CAS | Service and HTTP delayed write, expiry and takeover tests |
| Read-only console follows saved state without losing local text | `editor.js` state/poll changes | Playwright two-session and unsent-text tests |
| Stale base preserved; explicit complete reconcile and validate | Store reconciliation and service admission | Runtime/HTTP stale-base tests |
| Success clears only owner; failure retains; import/rollback same path | Runtime publication and draft-loading actions | Runtime failure hooks, import/rollback HTTP/browser tests |
| CSRF, role, auth, public framework boundary | Security chain, live checks, existing public API calls | Security/ArchUnit and publication-wait tests |
| Checks and docs; no compatibility or deployed deletion | Operations/integration docs, obsolete removal | `verify`, contract search, docs inspection |

## Risks and Rollback/Recovery

Back up the stopped SQLite database set before upgrading a deployment. V3 is additive to existing deployed snapshot/account data, while the HTTP contract is intentionally replaced. A pre-activation publication failure leaves the owner draft and current published selection recoverable. An activation/bookkeeping or post-activation draft-cleanup fault requires operator inspection of runtime/intended state and backup recovery using existing operations guidance; do not silently retry publication or delete drafts. Restart drops lease and validation, reloads saved drafts, and requires reacquisition/revalidation. Record any development-only database reset precisely and never run it against deployed data.

## References

- [Ticket](../tickets/PR-6.1-shared-authoring-and-durable-drafts.md), [research](../research/PR-6.1-shared-authoring-and-durable-drafts.md), [design lens](../design-lens.md).
- `ManagementEditingService`, `ManagementEditingState`, `RuntimeConfigurationService`, `ConfigurationSnapshotStore`, `ManagementConfigurationImportService`, `ManagementConfigurationRollbackService`, `editor.js`, `docs/operations.md`.
