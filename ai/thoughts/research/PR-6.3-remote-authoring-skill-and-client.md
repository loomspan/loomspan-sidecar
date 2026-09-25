---
date: 2026-09-24
repository: loomspan-sidecar
branch: main
commit: 9496e3b02fced7dda63f47f161ecbefa5e6f30a4
ticket: ai/thoughts/tickets/PR-6.3-remote-authoring-skill-and-client.md
tags: [remote-authoring, management-api, personal-tokens, leases, skill-authoring]
---

# Remote Authoring Skill and Client Research

## Research Question

What checked-out Sidecar API, console behavior, framework guidance, fixtures, and tests exist for the ticket's external skill-authoring workflow?

## Summary

The shared management API and personal tokens from PR 6.1 and 6.2 are implemented. It exposes complete published configuration, a user-owned durable draft, one editing lease, explicit same-user handoff, save/reconcile/validate/publish, and bundle export/import. The console already reads server-saved revisions while another client holds control. This checkout has no packaged remote authoring skill, remote client, or representative client-to-console-to-execution fixture yet. These are source observations, not conclusions drawn from predecessor tickets.

## Repository State

- At 2026-09-24 21:38 PDT, `main` was at `9496e3b02fced7dda63f47f161ecbefa5e6f30a4`; `git status --short --branch` showed `## main...origin/main` and no changes. This research file is the only research-stage edit.
- POM declares Java 21 and `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.5-SNAPSHOT` (`pom.xml:13-17, 40-44`). Repository policy uses the locally installed snapshot and keeps framework release and hosted CI gates separate (`AGENTS.md`).

## Current Behavior and Data Flow

1. A signed-in management user creates a Read, Edit, or Publish token. The issue response contains the secret once; the service stores a SHA-256 digest and returns metadata thereafter (`ManagementPersonalTokenController.java:21-34`; `ManagementPersonalTokenService.java:43-64,76-112`). The server accepts bearer credentials only on a closed route map and intersects token preset with the live account role (`ManagementBearerFilter.java:27-56,68-82`). It rejects mixed bearer/browser credentials, invalid/revoked/expired tokens, and unauthorized routes. Browser mutations use CSRF; bearer-only requests are exempt (`ManagementSecurityConfiguration.java:50-70`). Management PATs do not authenticate `/v1/**` (`ManagementPersonalTokenHttpIntegrationTest.java:163-170`).
2. `GET /api/management/configuration/current` returns the published snapshot and restart-selection state; `GET /api/management/editing` returns lease visibility; `GET /api/management/editing/draft` returns only the caller's draft or 404 (`ManagementConfigurationController.java:51-52`; `ManagementEditingController.java:24-33`). A draft contains ID, positive revision, base/source IDs, complete `configuration`, `stale`, and optional validation; a grant adds editing session ID, generation, and expiry (`ManagementEditingService.java:25-29,300-320`). Configuration is ordered `skillDocuments` of `sourceName`/`yaml` plus one `restRoutesYaml` (`ManagedConfiguration.java:11-23`; matching framework `SkillDocument.java:3-6`).
3. `POST /editing/lease` acquires a free lease, creating a draft from current publication if absent. `POST /editing/lease/handoff` requires an existing lease owned by the same user and rotates the grant; `renew` and `release` require the exact current editing session ID and generation (`ManagementEditingController.java:35-60`; `ManagementEditingService.java:68-100,235-250`). The holder is bound to the originating browser session or PAT, not a supplied label/identifier. Lease expiry is 15 minutes by default (`application.yml:15`; `ManagementEditingService.java:78-81`). Reads, saves, and validation do not renew it (`ManagementEditingHttpIntegrationTest.java:125-139`).
4. `PUT /editing/draft` sends a complete configuration and expected draft ID, revision, and base with the current grant. `POST /editing/draft/reconcile` sends the same complete body against the current published base after staleness; neither operation merges content (`ManagementEditingController.java:63-75`; `ManagementEditingService.java:130-146`). An expected-revision mismatch returns `revision_conflict`, a base mismatch `base_conflict`, and a displaced/expired lease `grant_conflict` (`ManagementEditingService.java:135-143,220-250`). Saving advances the revision and clears validation. `POST /editing/draft/validate` validates the exact saved revision and retains only in-memory proof for its grant/base (`ManagementEditingService.java:159-178,300-309`). The validation result supplies `successful` and structured issues with severity, source label, skill name, location, and message (`RuntimeConfigurationService.java:126-145`; `ConfigurationValidationIssue.java:4-5`).
5. `POST /configuration/publish` requires the same exact validated candidate and a Publish credential if bearer. The server rechecks account, grant, revision, generation, and base during publication admission (`ManagementConfigurationController.java:135-139`; `ManagementEditingService.java:181-217`). Publication re-prepares and stages the candidate, submits a snapshot, activates it, then deletes the publishing user's draft and clears the lease (`RuntimeConfigurationService.java:151-163,166-252`). Failure before activation leaves the draft; an after-activation cleanup/bookkeeping fault returns a detailed 503 with runtime/intended IDs and mutation fault (`ManagementConfigurationController.java:189-206`). The status/health inspection matters after an ambiguous 503.
6. `GET /configuration/export` streams a format-1 ZIP of the runtime-published configuration. Import review validates an uploaded ZIP without mutation; import load requires the exact grant/candidate and replaces the caller's saved draft for later validation/publication (`ManagementConfigurationController.java:54-95,141-180`; `ManagementConfigurationImportService.java:29-61`). Multipart fields are `bundle` alone for review and `bundle`, `editingSessionId`, `generation`, `draftId`, `revision`, `baseSnapshotId` for load (`ManagementConfigurationController.java:141-168`).
7. The console editor polls current publication, lease status, and saved draft; it displays server-saved content even while read-only and keeps local unsent text separate (`editor.js:76-86,133-155`). Its handoff path reads current state and obtains a fresh grant; prior tabs lose editing control (`editor.js:157-167`; `ManagementEditorBrowserIntegrationTest.java:60-117`). It has explicit reconcile, validate, publish, release, and lease renewal tied to user activity (`editor.js:170-212`). The console's current-configuration and history views are separate (`console.js:137-163,319-340`).

## Key Components

- `src/main/java/ai/loomspan/sidecar/management/ManagementBearerFilter.java:68` — route-to-preset authorization map for remote calls; admin takeover and token administration are absent.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingController.java:16` — request DTOs and shared editing HTTP routes, including `code`-bearing 409 responses.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:68` — lease admission, draft replacement, exact validation/publication proof, and conflict codes.
- `src/main/java/ai/loomspan/sidecar/management/ManagementConfigurationController.java:34` — current/history/export/import/publish routes and publication problem shape.
- `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java:126` — framework skill validation plus Sidecar route validation; publication lifecycle.
- `src/main/java/ai/loomspan/sidecar/management/ManagementPersonalTokenService.java:43` — one-time PAT issuance, digest storage, expiry, revocation, preset ceilings.
- `src/main/resources/static/management/assets/editor.js:134` — console polling and read-only saved-draft observation.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Remote client packaging | `rg --files` finds no `agent-skills/` package or remote management client in Sidecar; only quickstart host and operational Python scripts exist. |
| Skill guidance | Local framework checkout has `agent-skills/loomspan-docs/references/skill-authoring/` covering REST skills, input/output, RBAC, and validation; its REST guide says a REST manifest uses `rest: true`, optional `input_schema` and `rbac_roles`, and forbids model fields (`rest-skills.md:14-44`). Sidecar documents its own route/target YAML and auth modes (`docs/integration.md:60-134`). |
| Credential boundary | PAT secret is one-time and digest-only; remote docs already instruct environment injection and HTTPS (`docs/integration.md:234-254`; `docs/operations.md:753-772`). Execution uses issuer-signed JWTs and the application callback may verify caller passthrough independently (`docs/integration.md:155-184,191-232`). |
| Persistence and recovery | One draft per account persists across logout/credential loss/restart; lease and validation are memory-only (`docs/operations.md:429-446`; `ManagementDraftRestartIntegrationTest.java:19-43`). |
| Bundle transfer | Server owns format-1 ZIP parsing, size limits, review, and load (`ManagementConfigurationController.java:54-95,141-180`; `docs/operations.md:474-498`). |
| Documentation consistency | `docs/operations.md:397-400,453-454` still says browser login/session-only, while its later PAT section and checked-out bearer filter expose remote access. This is a documentation disagreement to carry into planning. |

## Existing Tests and Fixtures

- `ManagementEditingHttpIntegrationTest.java:69-197` tests private saved draft, same-user handoff and delayed-write denial, stale-base reconciliation, lease expiry without implicit renewal, administrator takeover, role downgrade, and concurrent revision saves.
- `ManagementPersonalTokenHttpIntegrationTest.java:66-235` tests preset access, one-time secret, mixed credentials, live roles, exact publish candidate, revocation/password recovery, expiry with draft retention, and audit messages without raw token. Its HTTP helper uses `Authorization: Bearer` (`:266-267`).
- `ManagementDraftRestartIntegrationTest.java:19-43` tests persistence across application restart and loss of lease/validation proof. `ManagementEditorBrowserIntegrationTest.java:60-117` tests read-only saved-revision observation and handoff using Playwright.
- `RuntimeConfigurationIntegrationTest.java:124-163` exercises structured validation issues and no publication on validation alone. `ManagementConfigurationExportControllerTest.java:19-43`, `ConfigurationBundleV1Test.java:30-265`, and `ManagementImportBrowserIntegrationTest.java:47-61` cover bundle boundaries and console import. Runtime tests cover publication/activation failure paths; they do not constitute remote-client integration evidence.
- `AuthenticatedExecutionApiIntegrationTest.java:97-170,232-269,300-327` covers separate execution JWT admission, role/input denial, result polling, and REST handler token handoff. `RestFailureEnvelopeIntegrationTest.java:32-101` covers bounded REST failures through execution polling. Existing quickstart `examples/quickstart/host/host.py` and `examples/quickstart/sidecar/{skills,rest-routes.yaml}` are local examples, but no test currently drives a remote authoring client through saved drafts, console observation, handoff, publication, and application authorization together.

## Dependencies and Operational Constraints

- The matching local framework source checkout is `C:/opendev/code/loomspan-framework` (observed commit `cec786399dd82ed61b51008caf271516c7990172`). Its bundled skill-authoring reference describes supplied `SkillDocument` labels and whole-set validation (`agent-skills/loomspan-docs/references/skill-authoring/validation-workflow.md:14-24`) and REST manifest rules (`.../rest-skills.md:14-44`). `AGENTS.md` directs use of this checkout rather than the separately installed stale 0.1.0 documentation skill.
- Framework validation is advisory: it does not invoke a skill, test endpoint connectivity, or authorize publication (`validation-workflow.md:22-24`). Sidecar execution requires a published configuration and separate JWT (`docs/integration.md:191-224`).
- `docs/operations.md:104-119` documents the V3 development draft schema and states that resetting a development database loses local accounts/drafts; no reset is performed in this research stage. The ticket excludes deployed-data deletion, release, deployment, and framework publication.
- Research only: no tests were run and no external services were contacted. Playwright browser tests and local HTTP integration tests have environment/tooling dependencies visible in their test classes; exact verification commands belong to test planning.

## Historical Context

- The roadmap's phases 1 and 2 define the shared draft/lease and PAT contract, while phases 3 and 4 identify the authoring skill/client and integrated readiness as the remaining work (`ai/thoughts/phases/2026-09-24-remote-skill-authoring-roadmap.md:99-215`). The ticket identifies PR 6.1 and 6.2 as predecessors; the implementation and tests cited above are the checked-out evidence of their behavior.

## Open Questions

- Planning needs to settle the skill/client package location and minimal runtime/interface. No equivalent package exists in the current Sidecar tree.
- The representative application fixture must demonstrate endpoint authorization under its own credentials, in addition to Sidecar execution authorization; the current quickstart and tests provide separate pieces but no joined remote-authoring walkthrough.
- The exact command set and local fixture startup sequence need test planning. No live or production endpoint is needed by the research evidence.
