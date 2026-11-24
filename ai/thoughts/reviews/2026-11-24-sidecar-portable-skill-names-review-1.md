# Sidecar portable skill names code review — Cycle 1

## Scope and Repository State

Reviewed the tracked Sidecar changes from local SDK commit `a06d4d1` and the
untracked `SkillCatalogLookup.java`, Sidecar ticket, research, and plans.
There were no staged changes. The untracked naming handoff and Sidecar ticket
were present before implementation. The tracked SDK ticket update records the
combined runtime check. No SDK source changes were part of this review.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This context wrote only the review artifact.

## Acceptance-Criteria and Plan Conformance

| Criterion | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Discover, look up, submit and poll the same exact hyphenated skill pair | `SkillCatalogController` returns Framework descriptors. `ExecutionController` takes the pair from the descriptor, validates the same input map and returns it in ID-based polling. | `AuthenticatedExecutionApiIntegrationTest` reuses the catalog pair, asserts the accepted location and exact result containing Unicode and a newline, and checks the replacement fields. The packaged quickstarts exercise the CLI and three SDK clients. | Implemented |
| Distinguish malformed names from valid missing identities and preserve authorization | `SkillCatalogLookup` validates the full pair using the public `ValidatedSkill` constructor before looking up the namespace. The public snapshot selects one exact owner and the catalog selects the exact local name. The `/v1/**` JWT chain and Framework role validation remain in place. | Authenticated coverage distinguishes malformed from missing identities, including malformed local names under absent owners, the pair length boundary, and rejected former routes and spellings. Role denial, unauthenticated requests and issuer/subject ownership are exercised. | Implemented |
| Retain and hand off the exact pair through the queue, using the existing HTTP executor and both configuration modes | `ExecutionRecord` stores both components as final values and the coordinator passes them to Framework after existing trust and expiry checks. It records the captured generation after handoff. `HttpSkillExecutor` is still registered as `default:http`. | The blocked-worker HTTP test observes the pair in a queued snapshot and then the successful callback result. Trust, publication correlation and shutdown tests passed. Native file and managed database runs independently exercised the checked-in planner and both HTTP callbacks. | Implemented |
| Reject uppercase physical paths, preserve diagnostic labels, and document current server shapes | File startup uses the Framework path loader and persists inspection records with both components. `ConfigurationBundleV4` checks actual ordinal paths before constructing documents with exact source labels. The operations, file-mode, bundle and execution references describe those boundaries. | Temporary mounted startup rejected `Orders/lookup.yaml` with the uppercase-component diagnostic. The ZIP test rejected uppercase directory and extension entries and preserved the exact label on a valid path. The management browser import and contract helper tests passed. | Implemented |

## Active Project Guardrails

- The old single-name API and prefix guessing are gone. The implementation plan
  records the effect of using fresh development data. Tests used fresh local
  state without changing existing databases.
- Framework remains authoritative for pair validation, selection, role
  checks, generation capture and execution. The ArchUnit suite checks direct
  dependencies in production and test classes and forbids Java skill methods.
- Capacity, credential rechecks, owner equality and terminal snapshot
  publication still use the existing paths. No input or output-name
  transformation was added at these points. The exact failure message,
  producing skill identity and observer details remain Framework-owned.
- Product references describe the replacement API directly. The separate
  observability routes and diagnostic metadata fields retain their purpose.
  The relevant packaging and authoring checks passed.

## Open Questions and Assumptions

None.

## Verification Results

Maven used Java 21 and the pinned Framework snapshot in the local repository.
For both Maven commands, `$agent` was
`C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar`.
Native Windows permissions allowed the installed Chromium and loopback fixtures
to run.

- PASS — `mvn -o -B -ntp "-DargLine=-javaagent:$agent" '-Dtest=AuthenticatedExecutionApiIntegrationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ConfigurationModeIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,ExecutionOutputSchemaIntegrationTest,ConfigurationBundleV4Test,ManagementImportBrowserIntegrationTest,HttpExecutorIntegrationTest,SupportedLoomspanApiArchitectureTest' test`. All 75 passed. Log `target/portable-sidecar-review1-focused.log`.
- PASS — `mvn -o -B -ntp "-DargLine=-javaagent:$agent" verify`. 309 passed, two opt-in skips, zero failures or errors. Produced the executable JAR. Log `target/portable-sidecar-review1-verify.log`.
- PASS — `target/portable-sdk-venv/Scripts/python.exe target/portable-quickstart-smoke.py`. File mode loaded the checked-in YAML. The CLI, Java SDK and both Python clients reused the catalog identity, polled a completed execution by ID and verified two authenticated callbacks each. Evidence directory `target/portable-quickstart-c27b0f8fc184437cb5360cf3b91a3992`.
- PASS — `target/portable-sdk-venv/Scripts/python.exe target/portable-quickstart-smoke.py --database`. The managed publication used the unchanged checked-in YAML and settings. All four clients completed executions with eight verified callback records. The CLI's polled record carried the new publication UUID. Evidence directory `target/portable-quickstart-1649a77fc6ee4a1f864a0439221ddafe`.
- PASS — `python scripts/sidecar_version.py check`. Sidecar, Framework pin and packaged skill metadata matched.
- PASS — `python -m unittest discover -s scripts -p 'test_*.py' -v`. 22 passed and one Windows symlink-capability skip.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`. All 11 passed.
- PASS — `git -c core.safecrlf=false diff --check a06d4d1`.

## Residual Risks and Optional Developer Checks

Docker Compose was unavailable in this environment. The combined tests ran
against the native package on loopback. The real-provider walkthrough and the
external fixture checkout were outside this run. The two Maven skips were
`ExternalConfigurationFixturesIntegrationTest` and `PythonIssuanceInteropTest`.
The combined smoke exercised both Python clients through the real Sidecar
routes with a fixture-issued credential.

## Disposition

`clean`. The independent full suite, file-mode smoke and managed database smoke
passed. Both tickets' combined runtime checks are complete.
