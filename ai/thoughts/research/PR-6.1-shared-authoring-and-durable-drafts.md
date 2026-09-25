---
date: 2026-09-24
repository: loomspan-sidecar
branch: main
commit: d146e4ce36bc6faa4b53924f7a18090130c4fbef
ticket: ai/thoughts/tickets/PR-6.1-shared-authoring-and-durable-drafts.md
tags: [management, drafts, leases, publication, console]
---

# Shared Authoring and Durable Drafts Research

## Research Question

How do the current console, management API, editing state, persistence, authentication, validation, and publication paths work for PR 6.1?

## Summary

The present draft and lease are process-local, session/tab-scoped state. Published configuration snapshots and management accounts are SQLite-backed. A management session can acquire the application-wide lease, save a complete configuration, validate it, and publish the exact in-memory candidate. The browser editor polls ownership and runtime identity, but does not follow a different session's saved draft; a changed runtime base causes its view to clear. Import and rollback have separate review/confirmation paths and can clear all private drafts during accepted cutover.

## Repository State

At 2026-09-24 17:12 PDT, `main` was at `d146e4ce36bc6faa4b53924f7a18090130c4fbef`, tracking `origin/main`, with a clean working tree. This research document is the only Step 1 change. Investigation checklist covered editing/API, persistence, console, security and lifetime, runtime publication/import/rollback, tests, documentation, and matching framework APIs.

## Current Behavior and Data Flow

1. The management Spring Security chain covers `/management/**` and `/api/management/**`, keeps form login and default CSRF, and assigns editor/admin authority to editing mutations and publication. Only admins may use lease takeover. The separate execution chain accepts JWTs for `/v1/**` and is stateless (`ManagementSecurityConfiguration.java:43-92`, `JwtSecurityConfiguration.java:88-108`). The management session endpoint exposes the authenticated account, role, CSRF token, and timeout values (`ManagementController.java:51-70`).
2. `ManagementEditingController.java:22-100` exposes status, draft read, lease acquire/takeover/activity/release, draft save/validate/discard under `/api/management/editing`. Requests carry a browser-generated `tabId`, server grant UUID, and expected candidate UUID. `ManagementConfigurationController.java:122-158` publishes through `/api/management/configuration/publish` and exposes separate import/rollback review and confirmation endpoints.
3. `ManagementEditingState.java:9-53` stores entries keyed by servlet session ID and one lease containing session ID, tab ID, grant ID, and expiry, all in memory. `ManagementEditingService.java:47-119` reads only the calling session's entry; acquisition either resumes that session's eligible entry or starts from the runtime snapshot. Admin takeover starts a new entry for the admin session. Save checks lease ownership and candidate UUID, replaces complete content, rotates candidate UUID, and clears validation via `ConfigurationDraft.java:31-35`.
4. Validation captures the candidate under the runtime transition gate, performs framework and REST checks outside it, then rechecks account, session, lease, candidate identity, and base before attaching results (`ManagementEditingService.java:149-166`, `RuntimeConfigurationService.java:132-150`). Publication waits for the publication lock, invokes admission to recheck editing authority, and requires the candidate's base to match the published snapshot (`RuntimeConfigurationService.java:153-167`). The exact validated candidate is represented by an in-memory `FrozenConfigurationCandidate` and `ConfigurationDraft.ValidatedCandidate` (`ConfigurationDraft.java:10-65`).
5. `RuntimeConfigurationService.java:226-304` prepares the complete framework replacement, stages REST resources, commits a database snapshot, activates the prepared generation, updates recorded status, and prunes retained history. On successful activation it clears all editing state, not only the publishing user's entry. A failed preparation leaves editing state; activation failure reverts the intended database selection but may leave a mutation fault if reversion/bookkeeping fails.
6. Import and rollback use session-held reviewed identifiers and observations, then recheck the live account/session and expected published snapshot and lease grant after acquiring publication and transition gates (`ManagementConfigurationImportService.java:57-147`, `ManagementConfigurationRollbackService.java:40-87`, `RuntimeConfigurationService.java:180-223`). Accepted import/rollback calls `publishCandidate` with import cutover; that path clears **all** drafts and the lease before database submission (`RuntimeConfigurationService.java:245-264`). The current operator documentation explicitly describes draft loss on accepted cutover, including commit or activation failure (`docs/operations.md:477-500`, `docs/operations.md:572-590`).
7. The editor creates a random tab ID in JavaScript, auto-saves full documents/routes, tracks local revisions separately from server candidate UUIDs, and polls runtime/lease status every ten seconds (`editor.js:6-12`, `editor.js:111-172`, `editor.js:179-207`, `editor.js:310-330`). Ownership loss retains local text on the page (`editor.js:174-177`). Acquiring control can resubmit retained unsaved local text through auto-save (`editor.js:209-223`). A changed runtime ID clears local content and invalidates the view (`editor.js:185-189`, `editor.js:239-246`). Initial load reads only the current session draft, otherwise renders the published snapshot (`editor.js:311-329`).

## Key Components

- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:17` — current editing policy, capability checks, account rechecks, lease expiry, candidate checks.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingState.java:9` — ephemeral per-session draft map and single lease.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingController.java:22` — tab-shaped editing HTTP contract and structured editing conflicts.
- `src/main/java/ai/loomspan/sidecar/storage/ConfigurationDraft.java:7` — in-memory exact candidate/validation association.
- `src/main/java/ai/loomspan/sidecar/storage/ManagedConfiguration.java:10` — complete authored skill documents and REST YAML, with unique source labels.
- `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java:30` — runtime gates, validation, snapshot commit, framework publication, import/rollback cutover.
- `src/main/java/ai/loomspan/sidecar/storage/StorageConfiguration.java:21` — file-backed SQLite, Flyway, WAL, full synchronous writes, foreign keys, process lock.
- `src/main/resources/db/migration/V1__configuration_snapshots.sql:1` and `V2__management_identity.sql:1` — current schema has snapshots, skill documents, management accounts/tokens; no draft table.
- `src/main/resources/static/management/assets/editor.js:1` — tab-scoped console editor and polling.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Draft lifetime and isolation | `ManagementEditingState.java:37-53` keys by session; session destruction, account password/role/enable changes, and successful publication remove content (`ManagementEditingSessionListener.java:17-26`, `ManagementIdentityService.java:140-184`, `RuntimeConfigurationService.java:270-277`). Restart recreates empty state. |
| Access and lease deadlines | `ManagementSessionGuard.java:25-70` rejects expired or changed accounts and only reports explicit activity at a 30-second cadence. `ManagementEditingService.java:92-103` renews the lease through `/lease/activity`; status/read/poll do not renew. Defaults are 30-minute session idle and 15-minute lease (`SidecarManagementProperties.java:8-9`, `application.yml:13-16`). Current editor sends activity after user events and polls separately (`editor.js:280-310`). |
| Base changes | `ManagementEditingService.java:183-197,234-241` clears a session's draft when its base differs from runtime. `editor.js:185-189` discards displayed text on runtime change. |
| Persistence | Snapshot rows and documents persist through `ConfigurationSnapshotStore.java:74-108` and repository; V1/V2 schema has no saved draft storage. `ConfigurationSnapshotStore.java:126-145` prunes published history subject to protected generations. |
| Framework boundary | Sidecar calls public `SkillReloader.validate(documents)`, `prepare(documents)`, and `publish(prepared)` (`RuntimeConfigurationService.java:132-150,263-276,363-376`). Matching framework `README.md:192-230` documents the closed public API and two-stage complete replacement. No live service was invoked in research. |
| Operator contract | `docs/operations.md:435-459` documents current tab/grant/candidate endpoint shapes; `docs/integration.md:49-56` describes session draft and publication gates. |

## Existing Tests and Fixtures

- `ManagementEditingServiceTest.java:41-180` checks session/tab-scoped lease/candidate lifecycle and late validation after edits or ownership loss.
- `ManagementEditingHttpIntegrationTest.java:77-287` checks current session privacy, stale tab/grant/candidate, takeover, expiry, activity cadence, logout, roles, and account change.
- `ManagementEditorBrowserIntegrationTest.java:73-480` uses Playwright for auto-save, validation/publication, separate tabs, failed/late save behavior, release/discard, takeover, activity and lease loss. It encodes today's tab/private-draft behavior.
- `RuntimeConfigurationIntegrationTest.java:206-274,421-585,754-787` covers exact candidate validation, publication gates, restart dropping editing state, accepted publication during lease loss, import/rollback cutover, and competing publication.
- `ManagementImportBrowserIntegrationTest.java:47-133`, `ManagementHistoryBrowserIntegrationTest.java`, `ManagementConfigurationHttpIntegrationTest.java`, and `ConsoleSecurityIntegrationTest.java:40` exercise adjacent management import/history, HTTP, CSRF, and management/execution separation.
- Existing tests do not establish durable one-draft-per-user behavior, same-user cross-session read/handoff, stale draft reconciliation, or preservation of other users' drafts because these are absent from current code. Standard local suite is `./mvnw.cmd -B -ntp verify` on Windows (`docs/operations.md:685`). Browser tests use local fixtures/Playwright; Compose-only checks are separate (`docs/operations.md:691-716`). No tests were run during this read-only research step.

## Dependencies and Operational Constraints

The matching framework checkout at `C:/opendev/code/loomspan-framework/README.md:192-230` documents `SkillReloader`, `SkillDocument`, `PreparedSkillUpdate`, and the process-local generation ID. In-memory `validate(documents)` checks authoring rules without activation or execution; preparation rechecks and freezes the complete replacement. Sidecar uses the local beta.5 snapshot dependency and does not rebuild framework source in its ordinary build (repository `AGENTS.md`, `pom.xml:24-27`). The existing database is file-backed with Flyway migration validation (`StorageConfiguration.java:21-103`); operators back up the stopped database set (`docs/operations.md:67-79`). Import/rollback and publication affect runtime generations and retained execution, so their gates and failure outcomes are part of the relevant path.

## Historical Context

The 2026-09-24 roadmap phase 1 and `design-lens.md` describe one saved draft per user, explicit session-bound editing control, same-user handoff, stale-base reconciliation, and shared console/remote management API. These are intended future behavior; checked-out code above remains the current evidence. The ticket requires destructive replacement of superseded development contracts and documentation of any development-data reset, without deleting deployed data during planning.

## Open Questions

- Planning must establish concrete editing-session identifiers, credential binding, revisions, timeouts, persistent validation association, and the exact current-base reconciliation request shape. The current code does not define them.
- Planning must reconcile import/rollback's separate review-confirm publication paths and existing pre-commit draft clearing with the ticket's durable-draft ownership and failure-retention requirements.
- The console currently clears local text when the published base changes and may auto-save retained text after control acquisition; the new required local/saved/published view needs a defined state transition for these cases.
