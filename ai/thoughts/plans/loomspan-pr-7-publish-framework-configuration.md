# Framework Configuration Publication Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/loomspan-pr-7-publish-framework-configuration.md`
- Research: `ai/thoughts/research/loomspan-pr-7-publish-framework-configuration.md`
- Outcome: Publish one complete version of skills, REST routes and execution settings through the existing Console and management API.
- Resolved developer dependency (2026-09-25): Framework PR 11 is complete in `C:/opendev/code/loomspan-framework` at `56c750b`; `1.0.0-beta.6-SNAPSHOT` is installed locally. This supersedes the earlier beta 5 pin for this work. Sidecar must use beta 6 for development and plan its final dependency on the tested beta 6 release after framework publication.

## Current State

`ManagedConfiguration` holds skill documents and REST YAML. `ConfigurationSnapshotRepository`, `ConfigurationDraftStore`, `ConfigurationBundleV1`, `ManagementEditingController.Save`, and `static/management/assets/editor.js` carry those two fields. `RuntimeConfigurationService` validates/prepares skill documents with `SkillReloader`, stages REST resources, changes the intended database pointer and publishes. `ExecutionCoordinator` hands off queued work and correlates its captured generation with the durable snapshot via `GenerationRestResources`.

Framework beta 6 now publicly exposes `ExecutionConfiguration(String yaml)`, `SkillReloader.validate(documents, execution)`, `prepare(documents, execution)`, and closeable `PreparedSkillUpdate`. The strict candidate YAML accepts only `loomspan.connections`, `models`, `session`, and `execution-trace.persistence`. Credentials use `api-key-ref`, `gemini.credentials-ref`, and `header-refs` referencing Spring Environment properties. `validate` does not resolve references; `prepare` resolves them and freezes provider clients without invoking a model. Framework documentation identifies handoff as the selection boundary and framework retirement as the provider-resource lifetime boundary.

## Desired End State

One authored configuration containing ordered skills, REST YAML and reference-only execution YAML flows through draft, current, history, export/import, rollback and restart. A skill may use an alias introduced in the same draft. Preparation completes framework and REST staging before intended-pointer switch, then framework publication activates one complete generation. Captured work, including children/retries, keeps its generation; subsequent handoff gets the new one. A fresh database starts with empty skills/REST and `loomspan: {}\n`, inheriting framework defaults and allowing the first connection and skill to be authored.

## Scope

### In scope

- Complete authored execution settings in Sidecar persistence, bundle, shared management API and Console.
- Public beta 6 framework validation/preparation/publication, safe diagnostics, resource cleanup and restart authority.
- Developer LLM authoring skill, detached client, reference docs and examples for complete configuration.
- Dependency, CI/release and operations documentation updates for beta 6.

### Out of scope

- Process settings, secret-value editing/vault/rotation, REST YAML sensitive-data redesign, a separate settings publish API, and any Sidecar model-validation authority.

## Active Project Guardrails

Apply `ai/thoughts/design-lens.md`: framework owns skill semantics and lifetime, Sidecar owns transport/durable retention, and browser/token clients share one management workflow. The ticket supersedes the lens's restart-only model exception. **Destructively replace superseded development contracts, code and schemas; add no compatibility shims, legacy adapters, dual API paths or migration machinery solely for obsolete development behavior.** Document a required development-data reset and its impact; planning does not authorize deleting deployed data. Sidecar application and test Java may depend on framework types only under supported `ai.loomspan.api`.

## Impact and Risk Analysis

Snapshots, drafts and bundles change persisted/serialized pre-release contracts. Editing Flyway V1/V3 requires a documented development database reset after preserving needed data. Old bundles are rejected rather than silently interpreted. Only authored reference names enter database, ZIP, API and Console; resolved values must never enter error/log output. A reference can disappear after draft validation, so publish always re-prepares and can safely fail without discarding the draft. Unpublished `PreparedSkillUpdate` objects must close on validation, REST, SQL and publish failures. Framework owns provider retirement; `GenerationRestResources` owns REST retirement. Existing pointer revert and post-activation mutation-fault semantics remain.

## Implementation Approach

Add `executionConfigurationYaml` to `ManagedConfiguration` as exact authored text. Pass `new ExecutionConfiguration(yaml)` to the public `SkillReloader` overloads; do not duplicate framework parsing or default rules. Store the text as a required column in snapshot/draft headers. Replace the pre-release ZIP format with a version 2 contract containing an `execution.json` payload and preserving current ZIP inventory/limit/checksum defenses. The editor uses one labelled YAML textarea, which covers all publishable options while keeping the same save/validate/publish action. The agent skill and client use that same API and field, including complete-content save, validation proof and publish checks.

Use framework `validate` for detailed skill and field feedback, then `prepare` during Sidecar draft validation to check external references and candidate resources without executing skills or calling a model; close that temporary preparation. For publication, use a single candidate-ownership path in `RuntimeConfigurationService`: prepare framework, prepare/validate REST, stage both under the candidate generation, submit intended pointer, bind durable ID, publish, then retain published resources under framework/REST generation ownership. Close an unpublished candidate in every failure path, discard staged REST resources, revert intended selection on publication failure, and preserve the saved draft. Map framework `SkillValidationIssue` to the existing `ConfigurationValidationIssue`; field/reference errors must be actionable without including resolved values. Do not substitute startup model properties for selected database content.

## Phase 1: Complete persisted and exchanged configuration

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/storage/ManagedConfiguration.java` — add required authored `executionConfigurationYaml`; replace the two-argument constructor contract.
- [x] `src/main/resources/db/migration/V1__configuration_snapshots.sql`, `V3__management_drafts.sql`, `src/main/java/ai/loomspan/sidecar/storage/ConfigurationSnapshotRepository.java`, `ConfigurationDraftStore.java` — add and round-trip a non-null YAML column in all insert/read/create/replace paths without changing transaction, ordering or revision guarantees.
- [x] `src/main/java/ai/loomspan/sidecar/storage/ConfigurationSnapshotStore.java` — initialize empty content with `loomspan: {}\n`.
- [x] `src/main/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV1.java` — replace with `ConfigurationBundleV2`; add required `execution.json` to format 2 inventory/checksum/size validation and reconstruction, reject old/extra payloads, and update import/export callers and tests.
- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementEditingController.java` — require execution YAML in `Save`/`content`, retaining a single complete draft and shared browser/token endpoint. Existing current/history/rollback DTOs then carry the extended `ManagedConfiguration`.
- [x] Update `ConfigurationSnapshotRepositoryTest`, `ConfigurationDraftStoreTest`, `ConfigurationSnapshotStoreTest`, `ConfigurationBundleV2Test` and `ManagementEditingHttpIntegrationTest` for exact round trips, missing-field rejection and no resolved-secret serialization.

### Automated verification

- [x] `.\mvnw.cmd -Dtest=ConfigurationSnapshotRepositoryTest,ConfigurationDraftStoreTest,ConfigurationSnapshotStoreTest,ConfigurationBundleV2Test,ManagementEditingHttpIntegrationTest test` — complete values and transaction/ZIP rules pass.

### Optional developer checks

- [ ] None.

## Phase 2: Activate complete framework generations

### Changes

- [x] `pom.xml` — use installed `1.0.0-beta.6-SNAPSHOT` during development.
- [x] `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java` — call `SkillReloader.validate(documents, new ExecutionConfiguration(yaml))` for field feedback and `prepare(documents, execution)` in Sidecar validation, startup and publication; retain REST candidate validation, staged generation mapping, durable submission/revert and mutation faults. Close every unpublished `PreparedSkillUpdate` on validation or failure; release temporary REST validation resources. Report candidate field/reference problems safely.
- [x] `src/main/java/ai/loomspan/sidecar/rest/GenerationRestResources.java`, `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java` — preserve current generation ID mapping and handoff; change only if a test shows a specific retention/correlation gap.
- [x] `RuntimeConfigurationIntegrationTest`, `ExecutionConfigurationCorrelationIntegrationTest`, `RestHandlerLifecycleIntegrationTest`, `SupportedLoomspanApiArchitectureTest` — prove same-draft alias, failure atomicity, missing references, restart, old/new handoff, resource lifetime and public-package boundary.

### Automated verification

- [x] `.\mvnw.cmd -Dtest=RuntimeConfigurationIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,RestHandlerLifecycleIntegrationTest,SupportedLoomspanApiArchitectureTest test` — complete generation behavior against installed beta 6.

### Optional developer checks

- [ ] Optional configured non-production reference/provider smoke test; local fixtures provide required automated evidence.

## Phase 3: Console and developer LLM authoring

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java`, `src/main/resources/static/management/assets/editor.js` — add execution YAML editor and publishable/process-setting guidance; include YAML in `content()`, current/draft load, preview and reconcile; invalidate validation proof on every edit. Keep the existing single publish button and lease/read-only behavior.
- [x] `agent-skills/loomspan-sidecar-authoring/SKILL.md`, `references/integration.md`, `references/client-usage.md`, `examples/remote-authoring/README.md` — teach LLM clients to read current/draft, edit complete configuration including reference-only YAML, save with lease/base/revision, validate and publish through the existing API; explain process-setting limits and safe reference provisioning.
- [x] `agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py` and `client/test_sidecar_authoring.py` — retain the generic JSON pass-through for complete save/reconcile bodies; add or change client behavior only where the new field demonstrably needs it. Test that it transmits `executionConfigurationYaml` unchanged and never echoes secret values.
- [x] `src/test/java/ai/loomspan/sidecar/management/ManagementEditorBrowserIntegrationTest.java`, `ManagementEditingHttpIntegrationTest.java`, `scripts/test_agent_skills.py` — verify Console and detached client workflows, complete payloads, stale/permission checks, references-only guidance and bundle metadata.

### Automated verification

- [x] `.\mvnw.cmd -Dtest=ManagementEditorBrowserIntegrationTest,ManagementEditingHttpIntegrationTest test` — browser/API workflow passes.
- [x] `python scripts/test_agent_skills.py` and `python agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py` — detached skill/client remain self-contained and use complete configuration.

### Optional developer checks

- [ ] Inspect YAML guidance with a representative operator in a configured non-production Console.

## Phase 4: Documentation, version alignment and full verification

### Changes

- [x] `docs/operations.md`, `examples/production/README.md`, `examples/production/production.env.example`, `README.md`, `ai/thoughts/design-lens.md` — document first publication/defaults, canonical trace key, external references, restart authority, publishable/process boundary and development reset after backup.
- [x] `AGENTS.md`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `scripts/prepare-release.py` — align old beta 5 pin and release guards with the developer-specified beta 6 line; retain snapshot for development. Publish framework beta 6 to Maven Central before changing Sidecar to released beta 6 for final build/CI/release.

### Automated verification

- [x] `.\mvnw.cmd test` — full safe suite passes against installed beta 6 snapshot.
- [x] `python scripts/test_agent_skills.py` — version metadata, links, examples and detached package pass.
- [x] `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.1 --loomspan-version 1.0.0-beta.6 --tag v1.0.0-beta.1` — release guard recognizes beta 6 after the released artifact is available.

### Optional developer checks

- [ ] None beyond the configured non-production observations above.

## Test Strategy

Start with a runtime integration red test for a skill using an alias introduced in the same candidate. Use exact storage/bundle round trips and local fixture providers, then deterministic latches for handoff and retained resource lifetimes. Exercise shared API authorization and Console behavior. Do not contact a billable provider or use framework internal Java types. Detailed cases and exit criteria are in the companion testing plan.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| One Console workflow | `ManagedConfiguration`, editor, runtime | Browser and runtime integration |
| Complete draft/current/history/bundle/rollback/restart | Stores, bundle, management DTOs, startup | Storage, bundle, HTTP, restart |
| Handoff, child retention, durable ID | Existing handoff and generation mapping plus complete framework candidate | Correlation/lifecycle |
| Invalid settings/references/faults preserve active version and draft | Framework validation/preparation and Sidecar revert/close | Runtime fault tests |
| Permissions/lease/revision/base/proof | Existing editing service and shared controller | HTTP/browser security |
| No resolved credential disclosure | Reference-only authored YAML, safe errors | API/bundle/error sentinel assertions |
| UI/docs/defaults/reset | Editor guidance, operations, design lens | Browser and docs review |
| Supported beta 6 integration | Public `SkillReloader` overloads | ArchUnit and full suite |
| LLM authoring skill works with complete configuration | Agent skill docs/client | Python skill/client tests and HTTP fixture |

## Risks and Rollback/Recovery

Preserve a development database backup before its documented reset; no automatic deployed-data deletion. Old pre-release bundles fail under format 2, requiring new export from a compatible environment or deliberate authoring. On pre-activation failure, discard REST staging, close unpublished framework candidate, revert intended selection where needed and keep draft. A failed revert or post-activation bookkeeping fault remains an explicit mutation fault requiring operator inspection. Hosted CI and final release wait for published framework beta 6.

## References

- Ticket and research above; `ai/thoughts/design-lens.md`.
- `C:/opendev/code/loomspan-framework/src/main/java/ai/loomspan/api/ExecutionConfiguration.java`, `SkillReloader.java`, `PreparedSkillUpdate.java`; framework `README.md` publication section.

## Implementation evidence (2026-09-25)

The generic Python client already forwarded complete JSON objects, so no client implementation change was needed; its tests now send execution YAML. The focused runtime/storage/bundle and management/browser suites, detached Python tests, release validate-only guard and full Maven suite pass against installed beta 6 SNAPSHOT. The configured non-production provider smoke check and final released-artifact CI remain pending after framework beta 6 publication.
