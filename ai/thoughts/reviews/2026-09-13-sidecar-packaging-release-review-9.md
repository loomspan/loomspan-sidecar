# Sidecar Packaging and Release Code Review — Cycle 9

## Scope and Repository State

- Reviewed independently in pipeline mode under the Full 5-Step Pipeline profile. No prior review document was read or used as evidence.
- Review base is `main` at `d83902713bcf84375c3f54b8d33a66e048f0aa39`. Scope included every tracked modification and every untracked ticket artifact reported by `git status --short` and `git ls-files --others --exclude-standard`, including production code, tests, Docker/Compose/Kubernetes packaging, scripts, workflows, documentation, and planning artifacts.
- The matching framework checkout is `bfc2764bb661a6eccad9fd120cd687e6a911e99a`; its only worktree change is the ticket-authorized readiness record. The installed starter JAR SHA-256 is `901769CACBAF7F0CD2B39845ED1D7AC3D63294D64F74CC9D411C16925620D2AA`.
- The review stopped at the local snapshot boundary. It did not tag, publish, dispatch workflows, contact live external services, or alter the user's Tomcat on host port 8080. Docker verification used isolated host ports 18080, 18081, and 19091 and cleaned up its containers and network.
- The selected full profile remains appropriate because the ticket changes a supported framework handoff boundary, lifecycle/concurrency behavior, container packaging, and release automation.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This context changed no implementation artifact; only this required review record was added.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Atomic public ownership handoff | `ExecutionCoordinator.handoff` releases the queue reservation, checks the open/record state, installs the captured authentication, and calls public `SkillInvocationHandoff.handoff` while holding the Sidecar gate; `AdmittedSkillInvocation.invoke` and idempotent `release` occur outside it | Deterministic close-first, handoff-first/active-work, and framework-admission-closed cases in `ExecutionCoordinatorTest`; real snapshot use in application/lifecycle tests | implemented |
| Pre-handoff discard and reference cleanup | Owner close marks admission closed, drains only waiting Sidecar tasks, removes their records, releases count/bytes, and clears input/authentication; a dequeued close-loser is rejected by the same gate | `ExecutionCoordinatorTest`, `AuthenticatedExecutionApiIntegrationTest#ownerCloseImmediatelyRefusesTrafficAndDiscardsWaitingWork`, full suite | implemented |
| Accepted-work Sidecar resource lifetime | Framework-owned invocation runs on the Sidecar worker after handoff; REST clients remain open through normal completion and selected public observer events are retained before later teardown | `RestHandlerLifecycleIntegrationTest#sidecarWorkersClientsAndObserversSurviveNormalClose`; authenticated HTTP/JWT integration | implemented |
| Sidecar event boundary | Listener ignores foreign contexts, opts out of asynchronous delivery, publishes refusing traffic synchronously, and does not wait or invoke the framework | `ExecutionShutdownIntegrationTest`, `ManagementEndpointIntegrationTest` | implemented |
| Framework-owned post-handoff lifecycle | The revised ticket assigns actual framework listener ordering, blocked finalization, the single deadline, and cutoff to the framework; Sidecar contains no trace/storage coupling or internal lifecycle dependency | 30 matching-source framework lifecycle tests passed; Sidecar architecture test passed | implemented |
| Authenticated planner/REST and diagnostics | Existing controller/coordinator/REST handler are exercised with local JWT signature/issuer/audience/expiry/subject/role verification, nested planner callbacks, ownership, and `NEVER`/`ONERROR`/`ALWAYS` retention | Focused application/diagnostics tests and complete container quick start | implemented |
| Image, probes, examples, and isolated verifier ports | Pinned Java 21 runtime, fixed non-root UID/GID, exec-form entrypoint, `/sidecar` mount, separate management probes, Compose host, Kubernetes manifest, configurable verifier host ports and bounded cleanup | Uncached Docker build, exact JAR hash comparison, image/Kubernetes verifier on 18080/18081/19091, invalid-startup check | implemented |
| Configuration and operational documentation | README covers every Sidecar-bound key, route separation, security, diagnostics/data exposure, restart-only activation, state loss, shutdown budget, ports, and overrides | `ConfigurationReferenceTest`, full suite, image/Kubernetes verifier | implemented |
| CI and release guards | CI verifies released beta 4 and image without publishing; tag workflow checks project/tag/framework versions, scopes permissions, refuses overwrites, and produces JAR/ZIP/checksums | `ReleasePreparationTest`; nonpublishing validation command | implemented |
| Snapshot evidence and staged release boundary | Ticket and framework readiness record identify the tested Sidecar state, framework revision/hash, passed local gates, and pending external release gates | Fresh dependency tree/hash/status checks | implemented |
| Post-publication verification/publication | Deliberately deferred until separately authorized framework publication | Not run by design | safe deviation (staged/pending) |

## Active Project Guardrails

- Simplicity and technical debt: the implementation reuses the existing coordinator gate, retained record, worker pool, REST handler/client lifecycle, and standard Spring availability/lifecycle facilities; it adds no second queue, drain authority, timeout, or speculative framework SPI.
- Framework boundary: production and test code use Loomspan only through `ai.loomspan.api`; the ArchUnit guard imports both main and test classes and passed. No `ai.loomspan.internal..`, `ai.loomspan.autoconfigure..`, reflection, bean replacement, internal phase/name, or undocumented trace/storage configuration is used.
- Execution authority: Sidecar owns HTTP admission, pre-handoff discard, queue accounting, retention, and identity transport; the successful public handoff is the sole transfer point and Loomspan owns accepted execution/nesting/lifetime.
- Identity: verified JWT authentication is captured at handoff and restored by the framework; identity is not inserted into model input, and production Sidecar does not mint, refresh, or exchange tokens.
- Shutdown: Sidecar refusal/discard is immediate and nonwaiting; framework tests own the one accepted-work budget/cutoff; Sidecar client and worker teardown adds no drain period or unbounded close wait.
- Diagnostics: result text and selected public events are retained without new truncation/sanitization; docs identify business-data and heap-limit implications.

## Open Questions and Assumptions

- None affecting correctness or local snapshot review confidence.
- Framework publication, released-dependency resolution, hosted CI, release workflow execution, and published-image verification remain intentionally pending and require separate authorization.

## Verification Results

- PASS — `git diff --check` — no whitespace errors; only expected Git line-ending warnings were emitted.
- PASS — `rg -n "ai\\.loomspan\\.(internal|autoconfigure)|execution-trace|TRACE_COMPLETED|FrameworkExecutionLifecycle|FrameworkShutdown" src README.md examples scripts .github pom.xml` — the only match was the architecture rule's forbidden-package declaration; no implementation/test coupling was found.
- PASS — `.\\mvnw.cmd -B -ntp dependency:tree '-Dincludes=ai.loomspan:loomspan-spring-boot-starter'` — resolved `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`.
- PASS — `Get-FileHash "$env:USERPROFILE\\.m2\\repository\\ai\\loomspan\\loomspan-spring-boot-starter\\1.0.0-beta.4-SNAPSHOT\\loomspan-spring-boot-starter-1.0.0-beta.4-SNAPSHOT.jar" -Algorithm SHA256` — matched `901769CACBAF7F0CD2B39845ED1D7AC3D63294D64F74CC9D411C16925620D2AA`.
- PASS — `.\\mvnw.cmd -B -ntp '-Dtest=ExecutionCoordinatorTest,ExecutionDiagnosticsTest,AuthenticatedExecutionApiIntegrationTest,ExecutionShutdownIntegrationTest,RestHandlerLifecycleIntegrationTest,ManagementEndpointIntegrationTest,ConfigurationReferenceTest,ReleasePreparationTest,SupportedLoomspanApiArchitectureTest' test` — 35 tests passed.
- PASS — `.\\mvnw.cmd -B -ntp verify` — all 61 Sidecar tests passed and the executable JAR was repackaged.
- PASS — `C:\\opendev\\code\\loomspan-framework\\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test` — 30 framework-owned lifecycle tests passed at the recorded source revision.
- PASS — `docker build --tag loomspan-sidecar:sc5-review9 .` — image built successfully.
- PASS — `docker build --no-cache --tag loomspan-sidecar:sc5-review9-uncached .` — a fresh uncached builder path succeeded, disproving the reviewed cache/context concern.
- PASS — `docker run --rm --entrypoint sha256sum loomspan-sidecar:sc5-review9-uncached /app/loomspan-sidecar.jar` plus local `Get-FileHash` — image JAR and freshly built local JAR both matched SHA-256 `bb91cc27478bad0663b5e0fd8ae28295411da597f8a538af545dbac81ea8182f`.
- PASS — `python scripts/verify-image.py --image loomspan-sidecar:sc5-review9-uncached --verify-kubernetes --api-port 18080 --host-port 18081 --management-port 19091` — non-root/JVM/mount contract, readiness/liveness, long work, planner/two-leaf callbacks, JWT identity/roles, diagnostics, foreign-owner isolation, bounded SIGTERM, invalid startup, Kubernetes contract, and cleanup passed without using host port 8080.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` — nonpublishing release validation passed.
- PASS — `docker ps -a --filter "name=loomspan-sc5-43044" --format "{{.Names}} {{.Status}}"` — no verifier containers remained.

## Residual Risks and Optional Developer Checks

- The framework artifact is still a local snapshot and the Sidecar work is intentionally uncommitted. Final framework release-commit checks/publication, Sidecar switch to released `1.0.0-beta.4`, hosted CI, final release commit/tag, artifact publication, and published-image quick start remain mandatory staged gates.
- Optional: run the README PowerShell quick start interactively and inspect `/status`; the automated image verification already exercises the same functional contract.
- Optional: review Kubernetes resource sizing and 45-second termination grace against the intended cluster policy; no live cluster apply is required for local readiness.

## Disposition

- `clean`
