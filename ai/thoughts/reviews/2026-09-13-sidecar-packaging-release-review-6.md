# Package, verify and prepare Loomspan Sidecar for a framework-first beta 4 release Code Review — Cycle 6

## Scope and Repository State

Reviewed independently in pipeline mode with the Full 5-Step Pipeline profile. Scope included the ticket, research, implementation plan, testing plan, design lens, framework readiness record, current Sidecar production/test/configuration/documentation/release artifacts, all tracked changes, and all untracked ticket files. Prior review documents were not read or relied upon.

The comparison base is current `main` commit `d83902713bcf84375c3f54b8d33a66e048f0aa39`; the complete SC5 implementation remains uncommitted. The matching framework checkout is `bfc2764bb661a6eccad9fd120cd687e6a911e99a`, with only its readiness record modified, and the installed starter snapshot SHA-256 is `901769CACBAF7F0CD2B39845ED1D7AC3D63294D64F74CC9D411C16925620D2AA`. Host port 8080 is occupied by the user's unrelated Tomcat process and was not stopped or altered.

The review traced the public `SkillInvocationHandoff` ownership transfer through Sidecar admission, dequeue, close, invocation, record publication, queue/reference cleanup, lifecycle teardown, probes, packaged quick start, release safeguards, and the framework-owned post-handoff lifecycle evidence. Security/privacy, concurrency, lifecycle, resource cleanup, external boundaries, release immutability, documentation, and test quality were considered before plan conformance.

## Findings

No actionable findings remain.

## Findings Resolved in This Context

### [P2] Allow image verification without taking over fixed host ports
- Location: `scripts/verify-image.py:63`
- Scenario: The verifier always published Compose services on host ports 8080, 8081, and 9091. A legitimate existing service on any one of those ports caused `docker compose up` to fail before the packaged acceptance flow could execute.
- Impact: The required complete local image gate was environment-fragile and could not be rerun on this developer host without disrupting unrelated user work.
- Evidence: The script constructed every request with literal ports and `examples/quickstart/compose.yaml` used literal host-to-container mappings. Port 8080 is currently owned by the user's long-running Tomcat process.
- Fix: Added distinct, range-validated `--api-port`, `--host-port`, and `--management-port` verifier options; passed them to parameterized Compose host mappings; based all verifier requests on those values; retained 8080/8081/9091 as defaults and all original container ports; documented an isolated-port invocation; and asserted the mappings in `ConfigurationReferenceTest`.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Non-root image, JVM defaults, mounted configuration and location overrides | `Dockerfile`, `.dockerignore`, Compose mount, production properties | Complete alternative-port image verifier and invalid-startup case | implemented |
| Separate readiness/liveness and eager startup validation | `application.yml`, eager skill/route registration, Compose/Kubernetes probes | Full Maven suite and image probe/invalid-config checks | implemented |
| JWT planner/REST callbacks, ownership and diagnostics | coordinator, security configuration, production REST handler, quick-start host | HTTP integration tests and packaged planner/two-leaf flow | implemented |
| Prompt Sidecar close, atomic close-or-handoff, direct discard and reference cleanup | `ExecutionCoordinator` acquires `SkillInvocationHandoff` under its existing gate and invokes outside it | deterministic coordinator race tests plus shutdown integration | implemented |
| Accepted work belongs exclusively to Loomspan | public `AdmittedSkillInvocation` boundary; Sidecar does not add a drain or timeout | Sidecar normal caller/client/observer completion plus 30 matching framework lifecycle tests | implemented |
| Public framework surface only | production/test imports remain in `ai.loomspan.api` | ArchUnit public-surface checks | implemented |
| Deterministic quick start and deployment example | `examples/quickstart/**`, `examples/kubernetes/deployment.yaml`, README | complete image/Kubernetes verifier on 18080/18081/19091 | implemented |
| Complete Sidecar configuration/security/diagnostic/update guidance | README reference and examples | `ConfigurationReferenceTest` and full Maven suite | implemented |
| CI/release preparation is nonpublishing and immutable | Maven release profile, CI/release workflows, preparation script | release contract tests and validate-only command | implemented |
| Exact snapshot/source evidence and honest publication boundary | framework readiness record and ticket execution note | dependency tree, snapshot hash, source status, local gates | implemented |
| Released dependency, hosted CI and publications | deliberately deferred until separately authorized publication | not run by this local snapshot review | partial (required external stage pending) |

## Active Project Guardrails

- Simplicity: the port correction is three optional CLI values and Compose substitutions; no port-discovery subsystem or new dependency was added.
- Framework boundary: Sidecar uses only the public catalog, handoff/admitted invocation, observer, and `RestSkillHandler` surface; ArchUnit guards production and tests.
- Admission/retention: one Sidecar gate, queue owner, reservation accounting path, and retained record remain.
- Identity: verified JWT authentication is captured at handoff and caller passthrough remains outside model input; the example-only issuer is clearly local test material.
- Shutdown: pre-handoff work is discarded by Sidecar; handed-off work remains under Loomspan's single budget; no listener-order convention or second timeout was introduced.
- Diagnostics: result text and selected public events remain unchanged and retained atomically.

## Open Questions and Assumptions

- Framework publication, released-dependency resolution, hosted CI, tagging, registry/release uploads, and published-image verification remain intentionally pending and unauthorized.
- A fresh Step 5 context is required because this review changed implementation artifacts.

## Verification Results

- PASS — `python scripts/verify-image.py --help; python scripts/verify-image.py --image unused --api-port 0 2>&1; if ($LASTEXITCODE -eq 2) { exit 0 } else { exit 1 }` — new arguments parse and invalid ports are rejected.
- PASS — `$env:SIDECAR_API_PORT='18080'; $env:QUICKSTART_HOST_PORT='18081'; $env:SIDECAR_MANAGEMENT_PORT='19091'; docker compose -f examples/quickstart/compose.yaml config; Remove-Item Env:SIDECAR_API_PORT,Env:QUICKSTART_HOST_PORT,Env:SIDECAR_MANAGEMENT_PORT` — alternative host ports resolve while container ports stay 8080/8081/9091.
- PASS — `git diff --check; .\mvnw.cmd -B -ntp '-Dtest=ConfigurationReferenceTest,ReleasePreparationTest,ExecutionCoordinatorTest,ExecutionShutdownIntegrationTest,RestHandlerLifecycleIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — no whitespace errors; 20 focused tests passed.
- PASS — `.\mvnw.cmd -B -ntp verify` — all 61 Sidecar tests passed and the executable JAR was repackaged.
- PASS — `docker build --tag loomspan-sidecar:sc5-local .` — image built as `sha256:4debed3df78261c0983e3c9e2ba10dd510054d3aafd233dfbf6fb1abc562ee4c`.
- PASS — `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes --api-port 18080 --host-port 18081 --management-port 19091` — non-root/JVM contract, quick start, JWT callbacks, ownership, diagnostics, probes, shutdown, invalid startup, cleanup, and Kubernetes contract passed without using host port 8080.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` — nonpublishing release preparation passed.
- PASS — `.\mvnw.cmd -B -ntp dependency:tree '-Dincludes=ai.loomspan:loomspan-spring-boot-starter'` — resolved `1.0.0-beta.4-SNAPSHOT`.
- PASS — `(Get-FileHash C:/Users/mgiacomi/.m2/repository/ai/loomspan/loomspan-spring-boot-starter/1.0.0-beta.4-SNAPSHOT/loomspan-spring-boot-starter-1.0.0-beta.4-SNAPSHOT.jar -Algorithm SHA256).Hash` — matched the supplied installed snapshot hash.
- PASS — `.\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test` in `C:/opendev/code/loomspan-framework` — all 30 framework-owned handoff/listener/finalization/budget/cutoff tests passed.
- NOT RUN — post-publication Maven/hosted CI/tag/published-image gates — framework and Sidecar publication remain separate, pending, and unauthorized.

## Residual Risks and Optional Developer Checks

- Optionally run the documented curl/PowerShell quick start interactively and inspect the host's human-readable verified identity output; the automated packaged check is the acceptance evidence.
- After separately authorized framework publication, complete the released-dependency, hosted-CI, final-commit, Sidecar release, checksum, and published-image gates exactly as retained in the ticket and readiness record.

## Disposition

- `fixes-applied`
