# PR 7.1 Complete Execution Configuration Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket, research, both plans, design lens, repository instructions, matching framework public contract/parser, and the ticket-scoped tracked and untracked code, tests, schema, Console, client, and documentation. Branch `main` at `87ffc54`; no staged changes. The ticket and all PR 7.1 files are working-tree changes. Review proceeded from actual data flow before comparing plan checkboxes. The selected profile remains `full` because this change crosses credential, persistence, lifecycle, and publication boundaries.

## Findings

### [P1] Re-read supported deployment provider files during explicit preparation
- Location: `src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java:122`
- Scenario: Start Sidecar with a provider in an external `application.yml`, validate a file-source draft, edit the file, then publish or revalidate without restarting. `fileProviders` reads the already-loaded Spring `Environment` property sources; neither validation nor publication reopens the changed file.
- Impact: The new publication can silently use the former provider setting, and the promised file-drift `validation_required` check cannot detect an on-disk edit. This blocks the default-source acceptance criterion for current file content.
- Evidence: `fileProviders` only enumerates `environment.getPropertySources()` and calls `environment.getProperty`; `RuntimeConfigurationIntegrationTest.fileProviderDriftRequiresRevalidationAndRestartRetainsPublishedEffectiveContent` mutates an in-memory property source, not a file. `docs/operations.md` says current deployment connections/models are read during explicit preparation and a file change between validation and publication requires revalidation. Spring's environment does not reload application configuration files by itself.
- Fix: Define which local deployment file formats and precedence are supported for live preparation, load those files at each explicit preparation, and add a real temporary-file drift test. Preserve environment-reference resolution and the selected database snapshot as restart authority.

## Findings Resolved in This Context

- [P2] Indexed `loomspan.models.<alias>.thinking-levels[0]` entries were emitted as literal YAML keys, which the framework parser rejects. `EffectiveExecutionConfiguration` now assembles a YAML list, and the focused test covers two levels.
- [P2] A deployment header with a dotted name was split into nested YAML maps. The assembler now preserves the complete header name under `header-refs`; the same test covers it.
- [P3] The Console import page still asked for a format-1 bundle after V3 replacement. It now says format-3. The export warning now distinguishes excluded provider credentials from sensitive values that an operator may have authored in REST YAML.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Default file provider source and exact candidate | `EffectiveExecutionConfiguration.fileProviders`, `RuntimeConfigurationService.publish` | In-memory drift integration; no real file-edit test | Partial: open P1 |
| Strict Console override and credential replacement | Candidate assembler, write-only editing route, cipher, Console controls and authoring client | Managed override, editing HTTP and browser tests | Implemented for tested paths |
| Edits inert, exact proof and live authorization | `ManagementEditingService` revision/lease/proof admission and runtime gate | Editing HTTP and runtime integration | Implemented for tested paths |
| Ciphertext retention and secret-free ordinary provider surfaces | Separate credential tables, AES-GCM cipher, DTOs and V3 bundle | Cipher, SQLite, HTTP and bundle tests | Implemented for tested paths |
| Restart, history, rollback and import | Effective snapshot, retained credentials and source intent | Runtime recovery and management/bundle tests | Implemented for tested paths; live file-preparation issue remains |
| Captured work and no billable invalid preparation | Framework handoff and generation resources | Correlation and fake-provider integration | Implemented for tested paths |
| Public framework API and guidance | Closed `ai.loomspan.api` usage, operations and authoring docs | ArchUnit and full Maven suite | Implemented for tested paths; operations currently overstates live file reading |

## Active Project Guardrails

- Superseded V2 bundle/schema contracts are replaced rather than adapted; operations document development-data reset without deleting deployed data.
- Sidecar imports only supported `ai.loomspan.api` framework types; the ArchUnit test passed in the full suite.
- Shared browser/authoring API and framework preparation authority are retained. No new framework SPI or internal framework import was introduced.
- The implementation adds no alternate secret read endpoint; credential values remain write-only in the management contract.

## Open Questions and Assumptions

- Material scope decision: Which deployment file forms must live preparation reread? The ticket clearly requires current provider files, but Spring ConfigData can include local YAML, properties, config trees, profiles, and remote sources. Recommend local YAML/properties and config-tree files that Sidecar documents and can deterministically reload, with startup Environment still supplying external references and process settings. Unsupported dynamic origins should fail explicitly rather than silently use a stale value.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp verify` — pre-review-fix baseline: 233 tests, 0 failures, 3 skipped, against installed beta 6 snapshot.
- FAIL — `.\mvnw.cmd -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,RuntimeConfigurationIntegrationTest,ManagementEditorBrowserIntegrationTest' test` — new unit assertion expected unquoted YAML scalars; production output had quoted scalars. Assertion corrected; this was not a production failure.
- PASS — `.\mvnw.cmd -B -ntp '-Dtest=EffectiveExecutionConfigurationTest' test` — 3 tests, including indexed levels and dotted header.
- PASS — `python -m py_compile agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py`.
- PASS — `git diff --check` — whitespace clean; Git emitted only line-ending notices.
- NOT RUN — post-fix full `verify` — the open P1 needs a scope decision and corrective integration test first.

## Residual Risks and Optional Developer Checks

- Real on-disk deployment edits are not reflected by explicit preparation. Do not treat the in-memory drift test as proof of this acceptance criterion.
- Inspect Console layout visually in a configured local browser if desired; automated browser tests passed in the pre-fix full suite.
- After framework beta 6 publication, pin the release and repeat final Sidecar verification before release, per repository policy.

## Disposition

- `needs-developer`: the supported live deployment-file sources and precedence require one material scope decision before a safe fix. The bounded findings above were fixed; a fresh Step 5 review is required after any implementation changes.
