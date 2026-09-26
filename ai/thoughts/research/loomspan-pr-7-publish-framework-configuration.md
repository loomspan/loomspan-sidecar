---
date: 2026-09-25
repository: loomspan-sidecar
branch: main
commit: 66b1fc160253bc70497627b52795dfafb985b64e
ticket: ai/thoughts/tickets/loomspan-pr-7-publish-framework-configuration.md
tags: [configuration, publication, framework, console, persistence]
---

# Framework Configuration Publication Research

## Research Question

How do Sidecar's current managed configuration, publication, Console, persistence, import/export, execution correlation, and framework API work, and which contract is available for the proposed complete execution configuration?

## Summary

Sidecar's managed content currently consists only of ordered skill YAML documents and REST routes YAML. It validates and publishes that content through framework `SkillReloader`, with a durable SQLite selection and generation-bound REST resources. The framework checkout currently has no supported `ai.loomspan.api` contract for candidate model connections, aliases, session settings, or trace persistence; framework PR 11 is still a ticket in that checkout. Its existing startup properties bind models/connections and session settings, and trace persistence currently binds the top-level `execution-trace` key. This is a material dependency for planning and cannot be inferred from existing Sidecar or framework tests.

## Repository State

- Observed 2026-09-25 19:55 PDT; Sidecar `main` at `66b1fc160253bc70497627b52795dfafb985b64e`.
- Before research, the only Sidecar working-tree change was the untracked ticket `ai/thoughts/tickets/loomspan-pr-7-publish-framework-configuration.md`.
- Framework checkout `C:/opendev/code/loomspan-framework` was `main` at `ca46017ec06eec7e5a8f4b4ea1f4ca9aaad74915`, with its PR 11 ticket untracked. Research did not alter it.

## Current Behavior and Data Flow

`ManagedConfiguration` is the authored value carried through draft, snapshot, management JSON, and bundle paths. It holds only `skillDocuments` and `restRoutesYaml`, requiring distinct nonblank source labels (`src/main/java/ai/loomspan/sidecar/storage/ManagedConfiguration.java:11`). A fresh database receives an empty skill set and `targets: {}\nroutes: {}\n`, selected transactionally (`ConfigurationSnapshotStore.java:11`, `:43`). The SQL schema stores snapshot header, ordered skill documents, status, and selected pointer; draft tables store the same authored content under an account (`src/main/resources/db/migration/V1__configuration_snapshots.sql:1`, `V3__management_drafts.sql:1`). There is no framework execution-settings column or document. Snapshot submit switches the intended database pointer in one transaction; revert marks a rejected candidate failed while restoring the predecessor (`ConfigurationSnapshotStore.java:83`, `:100`).

At startup, `RuntimeConfigurationService.run` requires an empty framework skill catalog, loads the selected database snapshot, prepares skill YAML and REST resources, stages their generation mapping, publishes the prepared framework update, records status, prunes history, then opens execution admission (`RuntimeConfigurationService.java:78`). Validation calls `SkillReloader.validate(skillDocuments)`, then prepares and checks REST resources and releases the temporary resources; it does not submit or activate (`:132`). Publication serializes the operation, rechecks the candidate's base, prepares both parts, stages REST resources, commits the intended pointer, binds its durable ID, publishes framework skills, and then clears the successful draft and records status (`:161`, `:178`). A failure before framework publication leaves the active runtime unchanged; a publication failure attempts durable revert and records a mutation fault if it cannot; faults after activation are reported as mutation faults (`:190`, `:232`). The method's framework preparation is only `reloader.prepare(configuration.skillDocuments())` (`:329`). No candidate execution settings are passed to the framework.

The shared `/api/management/editing` service stores account-private drafts, uses editing leases and exact draft revision/base checks, validates a saved revision, and requires a matching successful validation proof at publish admission (`ManagementEditingService.java:18`, `:105`, `:159`, `:199`, `:231`). The `/api/management/configuration` controller exposes current/history/export/import/rollback/publish through that workflow (`ManagementConfigurationController.java:32`, `:51`, `:104`, `:135`). The browser editor currently constructs content from skill documents and REST YAML, autosaves it, validates the saved draft, then calls the same publish endpoint (`src/main/resources/static/management/assets/editor.js:31`, `:111`, `:169`, `:179`). Its page renders only skill-document and REST text editors (`ManagementPagesController.java:287`).

Exports use bundle format 1 with a manifest, REST YAML and ordered skill files. Import reconstructs `ManagedConfiguration` from those two content classes (`ConfigurationBundleV1.java:65`, `:199`). Import and rollback load content into the caller's draft, rather than activating it (`ManagementConfigurationImportService.java:29`, `ManagementConfigurationRollbackService.java:29`). Current/history and draft JSON likewise expose only the current record fields.

Execution workers call public `SkillInvocationHandoff.handoff` when dequeuing a request. The returned admission provides a generation ID, which Sidecar maps to a durable snapshot ID held by `GenerationRestResources`; `ExecutionSnapshot` records that ID (`ExecutionCoordinator.java:163`, `:178`, `:186`, `:193`). Queued work has not yet crossed handoff. Framework retirement callbacks remove the matching REST generation; `GenerationRestResources` protects its durable ID from history pruning while live (`RuntimeConfigurationService.java:85`, `GenerationRestResources.java:81`, `:96`). REST clients close when the generation retires or the Sidecar lifecycle stops (`GenerationRestResources.java:39`, `:116`). These paths currently correlate skill/REST generations, without candidate model/provider resources.

## Key Components

- `src/main/java/ai/loomspan/sidecar/storage/ManagedConfiguration.java:11` — current two-field authored contract.
- `src/main/java/ai/loomspan/sidecar/storage/ConfigurationDraftStore.java:20` — account-owned, revisioned drafts; SQL stores skill documents and REST YAML.
- `src/main/java/ai/loomspan/sidecar/storage/ConfigurationSnapshotRepository.java:28` — snapshot serialization and reconstruction from those fields.
- `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java:78` — recovery, validation, publication, intended-pointer failure handling and pruning.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:231` — live authorization, lease, base/revision, and validation-proof checks at publication admission.
- `src/main/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV1.java:65` — export/import archive format.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:163` — handoff and durable generation correlation.
- `src/main/java/ai/loomspan/sidecar/rest/GenerationRestResources.java:18` — REST resource ownership and durable snapshot mapping per generation.
- `src/main/resources/static/management/assets/editor.js:31` — browser authoring content and save/validate/publish actions.
- `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java:287` — current authoring page fields.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Framework publication | Public `SkillReloader` accepts skill documents only (`C:/opendev/code/loomspan-framework/src/main/java/ai/loomspan/api/SkillReloader.java:7`). No public complete execution candidate was found under `ai.loomspan.api`. |
| Startup execution settings | Framework `LoomspanProperties` holds session, named connections and aliases (`C:/opendev/code/loomspan-framework/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:33`, `:45`, `:48`). Sidecar's `application.yml` supplies only empty skill location and process configuration (`src/main/resources/application.yml:1`). Production example supplies model environment settings (`examples/production/production.env.example:19`). |
| Trace key | Framework `ExecutionTraceProperties` currently binds `execution-trace` with `ONERROR` default (`C:/opendev/code/loomspan-framework/src/main/java/ai/loomspan/autoconfigure/ExecutionTraceProperties.java:9`). PR 11 requires `loomspan.execution-trace.persistence`. |
| Persistence | SQLite snapshot/draft schemas contain skills/REST content and status/ownership metadata (`src/main/resources/db/migration/V1__configuration_snapshots.sql:1`, `V3__management_drafts.sql:1`). |
| Management security | Browser and token clients use shared editing and configuration controllers; publish admission checks the live account, lease, credential, exact base/revision, and validation proof (`ManagementEditingService.java:231`). |
| Export/rollback | Bundle format 1 and rollback review/load carry current `ManagedConfiguration` content (`ConfigurationBundleV1.java:65`, `ManagementConfigurationRollbackService.java:22`). |
| Documentation | Design lens explicitly says model connections require restart and snapshots contain skills/routes (`ai/thoughts/design-lens.md:129`, `:142`); operations guide describes that boundary (`docs/operations.md:79`, `:339`). Ticket pipeline notes supersede it. |

## Existing Tests and Fixtures

- `RuntimeConfigurationIntegrationTest.java:39`, `:97`, `:124`, `:193`, `:339`, `:371`, `:408` covers empty database activation, restart, validation, staging failures, bundle transfer, competing publication and retention of admitted generations for existing skill/REST content.
- `ExecutionConfigurationCorrelationIntegrationTest.java:33`, `:75` covers durable ID correlation across publication and a handoff/publication race for current content.
- `ManagementEditingHttpIntegrationTest.java:69`, `:92`, `:170`, `:186` covers private drafts, stale reconciliation, permissions and revision conflicts. Browser workflow tests are in `ManagementEditorBrowserIntegrationTest.java:60`, `ManagementImportBrowserIntegrationTest.java`, and `ManagementHistoryBrowserIntegrationTest.java`.
- `RestHandlerLifecycleIntegrationTest.java:36`, `:125` covers lifecycle and shutdown with the public framework handoff. `SupportedLoomspanApiArchitectureTest.java` checks the framework package boundary.
- No located Sidecar test publishes model connections, aliases, session settings or trace persistence as authored candidate fields, because those fields are absent. Existing tests use local fixture models/REST endpoints; browser tests use Playwright and production Compose tests need their documented environment.

## Dependencies and Operational Constraints

The companion framework ticket is present at `C:/opendev/code/loomspan-framework/ai/thoughts/tickets/loomspan-pr-11-publish-execution-configuration.md`, but its proposed public contract is not implemented in the checked-out `ai.loomspan.api` source. `SkillReloader` prepares/publishes skill sets only, and `SkillInvocationHandoff` provides the admission boundary (`.../api/SkillReloader.java:7`, `.../api/SkillInvocationHandoff.java:11`). Framework model/session properties are currently autoconfigure Java types; repository guidance prohibits Sidecar application and test dependencies on that package. The framework ticket also identifies provider resource freezing, reference resolution and trace-key correction as work to complete. Sidecar's normal Maven build consumes the developer-installed `1.0.0-beta.5-SNAPSHOT`; research did not rebuild framework or contact providers. The release policy requires local Sidecar integration evidence against the updated installed artifact before final release steps.

## Historical Context

The Sidecar design lens established atomic skill/REST publication and the current restart-only model boundary (`ai/thoughts/design-lens.md:129`). The current ticket deliberately supersedes that boundary and the two-field snapshot description. Framework PR 11 explicitly sequences its supported capability before Sidecar integration (`C:/opendev/code/loomspan-framework/ai/thoughts/tickets/loomspan-pr-11-publish-execution-configuration.md:85`). Historical intentions are distinct from present source behavior.

## Open Questions

- What exact supported public candidate, validation, preparation, publication, defaulting and credential-reference contract will framework PR 11 deliver? This is unresolved in the current checkout and materially determines Sidecar's integration shape.
- What exact schema/version shape will represent the new authored settings in Sidecar's pre-release SQLite and bundle formats? The ticket permits coherent replacement and requires documenting development-data reset impact; no existing representation answers this.
