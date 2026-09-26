# PR 7 — Publish framework configuration Code Review — Cycle 2

## Scope and Repository State

Independent pipeline review of the current ticket-scoped working tree on `main` at `66b1fc1`. I inspected staged, unstaged, and untracked paths; there are no staged changes. Scope includes the three-field managed configuration, edited V1/V3 development schemas, format 2 bundles, framework preparation/publication, Console and management API, authoring skill/client, dependency and release configuration, documentation, and integration tests. The untracked ticket, research, plans, bundle V2 implementation/tests, and agent-skill execution reference were included. No prior review document was read. The selected profile remains `full` because durable contracts and concurrent runtime publication are in scope.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This review made no implementation-artifact changes.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One Console workflow publishes skills, routes, connections, aliases, limits, and tracing | `ManagedConfiguration`, `ManagementEditingController`, `editor.js`, `RuntimeConfigurationService` | `RuntimeConfigurationIntegrationTest.publishesSkillWithCandidateModelAliasAndSettings`, `ManagementEditorBrowserIntegrationTest` | implemented |
| Complete draft, current, history, export/import, rollback, and restart | Draft/snapshot stores and SQL, `ConfigurationBundleV2`, management services, startup activation | Store and bundle tests, HTTP/browser tests, runtime restart tests | implemented |
| Handoff selects a complete generation and retains captured work/resources | Public `SkillInvocationHandoff` in `ExecutionCoordinator`; generation-bound REST resources and framework provider ownership | `ExecutionConfigurationCorrelationIntegrationTest`, `AuthenticatedExecutionApiIntegrationTest.nestedRestCallAfterPublicationKeepsItsAdmittedGeneration`, `RestHandlerLifecycleIntegrationTest` | implemented |
| Invalid settings, missing references, and publication failures do not partially activate or discard a failed draft | Framework `validate`/`prepare`, staged REST cleanup, durable pointer revert, mutation fault handling | Runtime validation and fault tests, management publication tests | implemented |
| Permissions, lease, revision/base, stale reconciliation, and proof apply to execution YAML | Shared editing controller/service with complete save body | `ManagementEditingHttpIntegrationTest`, browser and remote authoring workflow tests | implemented |
| Resolved credentials stay outside stored/exported/UI content and error details | Reference-only authored YAML; safe preparation issue mapping; bundle serializes authored text | Runtime missing-reference and disclosure checks, bundle and management tests | implemented |
| UI/docs define defaults, process boundary, and development reset | Editor label/help, operations guide, production example, design lens | Browser test, reference and release tests; documentation inspection | implemented |
| Supported framework API and beta 6 snapshot | `ExecutionConfiguration` and `SkillReloader` public overloads, beta 6 dependency | ArchUnit and full Maven suite | implemented |
| Developer LLM skill/client use the same Console API with complete content | `loomspan-sidecar-authoring` skill and references; existing JSON pass-through client | Detached skill and client Python suites, remote authoring HTTP workflow | implemented |

## Active Project Guardrails

- The implementation uses only `ai.loomspan.api` Java types from the framework; the architecture test covers both application and test code. No Sidecar Java skills or new framework SPI were introduced.
- The pre-release schemas and bundle were replaced coherently. Operations documentation requires backup and a development-data reset; it does not direct deletion of deployed data. Old format 1 bundles are rejected.
- The design lens now describes complete publication and keeps the browser/token shared management workflow. Sidecar remains on installed `1.0.0-beta.6-SNAPSHOT` for development and defers final released-artifact verification until framework publication.

## Open Questions and Assumptions

- None affecting correctness. The configured deployment smoke check and published beta 6 CI/release gates are explicitly deferred by the repository workflow.

## Verification Results

- PASS — `.\mvnw.cmd -q '-Dtest=RuntimeConfigurationIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,ConfigurationBundleV2Test,ManagementEditingHttpIntegrationTest,ManagementEditorBrowserIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — focused runtime, persistence, API, browser, and architecture coverage.
- PASS — `.\mvnw.cmd -q test` — full safe test suite against the installed beta 6 snapshot.
- PASS — `python scripts/test_agent_skills.py` — detached authoring skill checks.
- PASS — `python agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py` — detached client HTTP checks.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.1 --loomspan-version 1.0.0-beta.6 --tag v1.0.0-beta.1` — nonpublishing release guard.
- PASS — `git diff --check` — no whitespace errors.
- FAIL — `.\mvnw.cmd -q -Dtest=RuntimeConfigurationIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,ConfigurationBundleV2Test,ManagementEditingHttpIntegrationTest,ManagementEditorBrowserIntegrationTest,SupportedLoomspanApiArchitectureTest test` — PowerShell parsed the unquoted comma-separated property; the quoted rerun above passed.

## Residual Risks and Optional Developer Checks

- A configured non-production deployment can verify first publication with an externally provisioned reference and a local provider. Automated fixture tests cover this path without a billable request.
- Hosted CI and the final Sidecar released-artifact test await Maven Central publication of framework `1.0.0-beta.6`, as required by the repository release policy.

## Disposition

- `clean`
