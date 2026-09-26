# PR 7 — Publish framework configuration Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket, research, implementation and testing plans, repository guardrails, framework beta.6 public API and relevant implementation, callers, tests and documentation. Scope included the complete unstaged change, untracked V2 bundle and test, agent-skill reference, and planning artifacts on `main` at `66b1fc1`; no staged changes were present. The selected pipeline profile is `full` and remains appropriate for persisted configuration, publication and handoff semantics. No prior review artifact was used.

## Findings

No open actionable findings after fixes below.

## Findings Resolved in This Context

### [P2] Update the bundled integration guide to use the publication workflow
- Location: `agent-skills/loomspan-sidecar-authoring/references/integration.md:8`
- Scenario: A developer LLM follows the linked integration guide after reading the authoring skill. It was told to use `loomspan.connections.*.api-key` startup configuration and that model changes required restart.
- Impact: The bundle contradicted the new complete-draft contract and led its intended users away from Console publication and credential references.
- Evidence: `SKILL.md` links to the guide; the guide's old model YAML and restart instruction contradicted the new `executionConfigurationYaml` API and framework beta.6 candidate parser.
- Fix: Document database-selected execution settings, same-draft model authoring, `api-key-ref`, restart boundary and captured provider resources; add a guidance regression assertion.

### [P3] Correct obsolete bundle size errors
- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementConfigurationController.java:87`
- Scenario: A format 2 import or export exceeds a size limit.
- Impact: The API reports “format 1 limits” despite accepting/exporting format 2, misleading an operator diagnosing the failure.
- Evidence: The controller uses `ConfigurationBundleV2` but retained three format 1 error strings.
- Fix: Report format 2 consistently.

### [P2] Prove provider handoff across a live publication in Sidecar
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionConfigurationCorrelationIntegrationTest.java:32`
- Scenario: A model call remains physically active while a new execution configuration is published and another request is handed off.
- Impact: The previous Sidecar tests checked durable correlation after a completed model call and a REST handoff race, but could not detect an early provider retirement or incorrect new provider selection.
- Evidence: No Sidecar test exercised old and new model providers across an overlapping publication.
- Fix: Add a deterministic two-provider integration test with a held old request, a newly published connection, observable responses and durable snapshot assertions.

### [P3] Align the design lens with the new durable contract
- Location: `ai/thoughts/design-lens.md:94`
- Scenario: A later developer consults the design lens for draft/publication scope.
- Impact: It still described the saved draft and publication as skill/route only, despite the ticket explicitly superseding that guidance.
- Evidence: The specific stale descriptions remained after the new execution-setting exception was added.
- Fix: State complete skill, REST route and execution configuration in both decisions.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Complete candidate publication and restart authority | `ManagedConfiguration`, snapshot/draft SQL, `RuntimeConfigurationService`, framework beta.6 `ExecutionConfiguration` overload | `RuntimeConfigurationIntegrationTest` same-draft alias, validation, restart | implemented |
| Durable drafts, history, bundle, rollback and import | Snapshot/draft repositories; `ConfigurationBundleV2`; shared management controller/service | storage, bundle, import, editing and browser suites | implemented |
| Handoff, retained old resources and durable ID | `ExecutionCoordinator`, `GenerationRestResources`, public framework handoff/retirement | new two-provider overlapping call test, existing REST handoff and lifecycle tests | implemented for Sidecar boundary; nested framework work follows captured admission |
| Invalid settings/references and atomic publication | Public framework validation/preparation; staged REST; durable pointer and revert handling | invalid/process-setting and existing failure-injection tests | implemented |
| Shared permissions, revision/base/lease/proof | `ManagementEditingService`, single editor and token API | HTTP, browser and remote authoring tests | implemented |
| Reference-only credentials and no model call during validation | Authored YAML storage/bundle; framework prepare and sanitized Sidecar errors | missing reference and no-secret assertions; local fixture providers | implemented |
| Console, operations, first publication and development reset | editor UI, `docs/operations.md`, production example, agent-skill references | browser and agent-skill tests | implemented |
| Supported public API and beta.6 pin | `ai.loomspan.api` imports, `pom.xml`, CI/release guards | ArchUnit and full Maven suite | implemented |

## Active Project Guardrails

- Destructive pre-release V1/V3 and bundle replacement has no compatibility path; operations documentation describes the development database reset without performing deletion.
- Framework integration uses `ai.loomspan.api` and standard Spring only; no internal or autoconfigure Java dependency was added.
- Framework owns candidate parsing, validation and provider lifetime; Sidecar owns durable selection and REST lifetime. Browser and detached agent client share the same management API.
- The design lens and ticket record simplicity and the superseded restart-only rule.

## Open Questions and Assumptions

- None affecting this review cycle. The nested/parallel execution behavior is framework-owned under the captured public handoff; Sidecar's observable correlation and concurrent provider selection were verified through the public API.

## Verification Results

- PASS — `.\mvnw.cmd -q test` — full suite against the locally installed beta.6 snapshot, before the review's added focused test.
- PASS — `.\mvnw.cmd -q -Dtest=ExecutionConfigurationCorrelationIntegrationTest test` — three tests, zero failures/errors, including the new overlapping model-provider test.
- PASS — `python scripts/test_agent_skills.py` — bundled guidance and detached skill checks.
- PASS — `python agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py` — client request/response checks.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.1 --loomspan-version 1.0.0-beta.6 --tag v1.0.0-beta.1` — nonpublishing release guard.
- PASS — `git diff --check` — no whitespace errors; Git reported only line-ending conversion notices.
- NOT RUN — `python scripts/verify-production.py` — requires a configured Docker/production-like environment; local HTTP, browser and provider fixtures cover the changed paths.

## Residual Risks and Optional Developer Checks

- Framework beta.6 release publication, final dependency switch and hosted CI remain release-sequence work under `AGENTS.md`.
- A configured non-production deployment can provision an external reference and publish a first model-backed skill through the Console as an optional operational observation.

## Disposition

- `fixes-applied` — this review changed documentation, API error text and tests. Launch a fresh Step 5 context to certify the resulting diff.
