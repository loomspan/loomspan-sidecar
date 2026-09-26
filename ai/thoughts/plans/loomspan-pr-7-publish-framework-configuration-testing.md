# Framework Configuration Publication Testing Plan

## Change Summary

Sidecar extends its complete authored value with framework beta 6 `ExecutionConfiguration` YAML and publishes it with skills and REST routes. The same draft/management path must preserve complete content, safe reference handling and generation-bound execution. The developer confirmed framework PR 11 is complete and `1.0.0-beta.6-SNAPSHOT` is installed; use that artifact and public API for all integration evidence. The older beta 5 research observation is historical.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Snapshot, draft and bundle | Missing execution YAML or wrong default; stale format accepted | Exact round trips, format 2 rejection cases and empty database startup |
| Validation/preparation | New alias rejected, unsupported settings accepted, missing reference leaks secret or changes active version | Public API integration with local fixture, field errors and no model-call counter |
| Publication | Pointer/framework/REST mix or leaked prepared resources | Fault hooks, selected pointer, runtime generation, draft retention and close assertions |
| Handoff/retirement | Old root or delayed child switches settings; old provider/REST client closes early | Deterministic latches and captured generation/durable ID assertions |
| Management/Console | Settings bypass lease, revision, proof or permission; autosave drops YAML | HTTP and Playwright workflow cases |
| Agent skill/client | LLM guidance or detached client omits complete settings | Python bundle/client tests and local HTTP fixture |
| Java boundary | Sidecar uses internal/autoconfigure types | ArchUnit |

## Existing Coverage and Environment Constraints

Maven Surefire runs JUnit/Spring and ArchUnit tests. Existing `RuntimeConfigurationIntegrationTest` covers startup, validation, fault paths and retention for skills/REST. `ExecutionConfigurationCorrelationIntegrationTest` covers handoff correlation. `ManagementEditingHttpIntegrationTest` and `ManagementEditorBrowserIntegrationTest` cover shared API/Console drafts, leases and permissions. `ConfigurationBundleV1Test` covers ZIP defenses. `scripts/test_agent_skills.py` validates the detached skill and `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py` covers its client. Browser tests require the repository's Playwright setup; production Compose checks need a configured non-production environment. Use installed beta 6 snapshot, local HTTP/provider fixtures and no live credentials or billable calls.

## Failing Test First

- Name: `publishesSkillWithCandidateModelAliasAndSettings`.
- Type: Spring integration test.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java`.
- Arrange/Act/Assert: Save a complete candidate with a new reference-backed connection, alias, skill using that alias, session quota and trace policy; validate and publish via the existing editing/runtime path. Assert no provider request during validation/preparation, complete selected content, and new generation after publication.
- Expected pre-fix failure: `ManagedConfiguration` has no execution YAML and Sidecar calls document-only `SkillReloader` methods.

## Tests to Add or Update

### 1. `completeConfigurationSurvivesSnapshotAndDraftRoundTrips`

- Type: JDBC store tests.
- Location: `src/test/java/ai/loomspan/sidecar/storage/ConfigurationSnapshotRepositoryTest.java`, `ConfigurationDraftStoreTest.java`, `ConfigurationSnapshotStoreTest.java`.
- Proves: Ordered skills, REST YAML and exact authored execution YAML survive insert/read, draft save/revision/restart and history. Fresh store seeds `loomspan: {}\n`.
- Inputs/fixture: Named connection with `api-key-ref`, alias, retry options, session quotas and `execution-trace.persistence`; no resolved value in stored row.
- Edge cases: Empty/default candidate, non-null field, draft conflict and snapshot immutability.

### 2. `bundleFormat2RoundTripsAndRejectsOldOrTamperedContent`

- Type: bundle unit test.
- Location: `src/test/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV2Test.java`.
- Proves: Export/import carries the complete authored value including reference names, never resolved values. Required `execution.json` participates in manifest inventory, checksum, size and path checks; format 1, missing/extra/duplicate/tampered payloads fail.
- Inputs/fixture: Sanitized candidate YAML and secret sentinel present only in mock environment, not authored YAML.
- Doubles or boundary isolation: In-memory/temp ZIP, no live server.

### 3. `validatesWholeCandidateWithoutActivationOrModelCall`

- Type: Spring integration.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java`.
- Proves: Same-draft new alias/skill validates; invalid model reference, unsupported process key, direct credential and bad session value give field feedback. Sidecar validation prepares and closes a temporary framework candidate to resolve references and verify resources, but does not activate settings or call a model. Missing/blank external references fail safely.
- Inputs/fixture: Local fixture provider URL and environment property; request counter at zero until actual invocation.
- Edge cases: Reference removed after successful advisory validation; publish re-prepares and preserves draft/active generation.

### 4. `failedPreparationCommitAndPublicationKeepOneActiveVersion`

- Type: Spring integration with existing fault hooks.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java`.
- Proves: REST/framework preparation, SQL commit and framework publication failures discard staged REST, close unpublished framework candidate, revert intended pointer when required, retain draft and report no partial active combination. Post-activation bookkeeping failure remains mutation fault.
- Inputs/fixture: Distinct A/B skill, REST and execution YAML; controlled failure hook and local resources.

### 5. `restartUsesSelectedCompleteConfiguration`

- Type: Spring/SQLite integration.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java`.
- Proves: Restart activates database-selected execution YAML and framework defaults; changed startup model settings do not override selected data; a new database can author and publish its first connection/skill through management.
- Doubles or boundary isolation: Temporary SQLite and local provider fixture.

### 6. `handoffRetainsCompleteGenerationUntilPhysicalCompletion`

- Type: concurrent Spring integration.
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionConfigurationCorrelationIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java`.
- Proves: Queued request uses version at handoff; captured root and delayed child/retry keep old alias/limits/trace policy, durable snapshot ID and REST/provider resource; later handoff gets new complete version. Retirement occurs after physical work, including shutdown path.
- Inputs/fixture: Deterministic latches and local fixture provider/REST resources; no timing-only or internal-framework assertions.

### 7. `settingsFollowSharedDraftPermissionsAndProof`

- Type: HTTP/Playwright integration.
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementEditingHttpIntegrationTest.java`, `ManagementEditorBrowserIntegrationTest.java`.
- Proves: Complete settings use existing private draft, lease, exact revision/base, stale reconciliation, validation proof and live Read/Edit/Publish checks for browser and token clients. Saving or validating alone does not activate. Console edits all settings in one YAML field and uses one publish button. API/preview/error output contains authored reference names only.
- Inputs/fixture: Two accounts, session/token credentials, sanitized candidate and secret sentinel only in external environment.

### 8. `agentGuidanceAndClientPreserveCompleteCandidate`

- Type: Python bundle/client and local HTTP fixture tests.
- Location: `scripts/test_agent_skills.py`, `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`.
- Proves: Skill metadata matches beta 6 pin, all resource links stay within detached bundle, docs describe the complete save/validate/publish path and process-setting boundary, and the generic client transmits `executionConfigurationYaml` unchanged in complete save/reconcile bodies while retaining exact lease/base/revision checks. It never prints resolved credentials. Import load remains the existing five-field bundle transfer request.
- Inputs/fixture: Isolated copied skill directory and local HTTP server; reference-only example YAML.

### 9. `sidecarDependsOnlyOnSupportedFrameworkPackages`

- Type: ArchUnit.
- Location: `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`.
- Proves: Application and test Java depend on framework only through `ai.loomspan.api`; no `ai.loomspan.internal..` or `ai.loomspan.autoconfigure..`.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -Dtest=RuntimeConfigurationIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest test`.
- Related suite: `.\mvnw.cmd -Dtest=ConfigurationSnapshotRepositoryTest,ConfigurationDraftStoreTest,ConfigurationSnapshotStoreTest,ConfigurationBundleV2Test,ManagementEditingHttpIntegrationTest,ManagementEditorBrowserIntegrationTest,RestHandlerLifecycleIntegrationTest,SupportedLoomspanApiArchitectureTest test`.
- Agent skill: `python scripts/test_agent_skills.py` and `python agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`.
- Full safe suite: `.\mvnw.cmd test` against locally installed `1.0.0-beta.6-SNAPSHOT`; report any Playwright environment limitation instead of treating an unrun browser check as passed.
- Release guard after published beta 6 artifact: `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.1 --loomspan-version 1.0.0-beta.6 --tag v1.0.0-beta.1`. Final CI/release verification waits for Maven Central publication.

## Optional Developer Checks

- In a configured non-production deployment, provision one external reference, publish a first model connection via Console and observe a local request using it. This is supplementary; automated local fixture tests are the gate.

## Exit Criteria

- [ ] The red test fails for missing complete-candidate support before Sidecar implementation.
- [x] Focused, related and full safe tests pass against the installed beta 6 snapshot; agent skill/client checks pass detached.
- [x] Every ticket acceptance criterion, including LLM authoring and safe reference handling, maps to executable evidence.
- [x] Fault tests demonstrate no partial active version or draft loss; concurrent tests demonstrate handoff and physical lifetime.
- [x] No routine test performs a live/billable provider call or exposes resolved credentials.
- [x] Process-setting rejection, first-publication defaults, reset impact and beta 6 release sequence are documented.
- [x] Optional configured checks are labeled separately and are not represented as already performed.

## Verification evidence (2026-09-25)

A separate pre-implementation red run was not captured; the first compile failure was from the planned destructive ManagedConfiguration contract change. The completed integration test now proves same-draft alias publication and restart, and a negative test proves missing external references and process settings cannot activate. Existing deterministic publication and resource-lifetime tests pass in the full suite. The optional configured deployment observation was not performed.
