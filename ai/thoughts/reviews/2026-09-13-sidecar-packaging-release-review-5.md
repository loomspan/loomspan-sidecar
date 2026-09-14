# Sidecar Packaging and Release Code Review — Cycle 5

## Scope and Repository State

Reviewed the complete ticket-scoped working tree independently from base commit
`d83902713bcf84375c3f54b8d33a66e048f0aa39` on `main`, including modified,
untracked, and ignored-build-output-sensitive paths. Scope included production
execution handoff, all changed tests and local fixtures, container and deployment
artifacts, documentation, Maven/release workflows, both preparation scripts, and
the supplied plans and release-readiness record. No prior review document was
read or used.

The matching framework checkout was at
`bfc2764bb661a6eccad9fd120cd687e6a911e99a`; its only worktree change was the
supplied readiness record. The installed starter JAR SHA-256 independently
matched `901769CACBAF7F0CD2B39845ED1D7AC3D63294D64F74CC9D411C16925620D2AA`.
No tag, publication, workflow dispatch, registry operation, or other live
external action was performed.

## Findings

No actionable findings remain after the fix described below.

## Findings Resolved in This Context

### [P2] Bound and clean up the invalid-image startup check
- Location: `scripts/verify-image.py:17`, `scripts/verify-image.py:124`
- Scenario: The negative startup check ran `docker run` attached with no timeout.
  If invalid route validation regressed and the container stayed running, the
  required verification would wait indefinitely instead of detecting failure;
  interrupting it could also leave the named container behind.
- Impact: Local preparation and hosted CI could hang for the job-wide timeout and
  fail to provide an actionable result for the invalid-configuration acceptance
  criterion.
- Evidence: The new verifier's generic command helper had no timeout parameter,
  and the negative `docker run` was the only operation whose success condition
  was process exit but had no independent deadline or guaranteed removal.
- Fix: Added an optional subprocess timeout, bounded the invalid startup run to
  30 seconds, and force-removes the exact named container in a `finally` block.
  A focused Python probe confirmed the helper raises `TimeoutExpired` at its
  deadline.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Non-root mounted image with documented JVM defaults and overrides | `Dockerfile`, `.dockerignore`, `README.md` | Docker build passed; image inspection executed before the environment-blocked end-to-end attempt | implemented |
| Startup validation and separate readiness/liveness probes | eager skill/route startup, `application.yml`, Compose and Kubernetes probes | focused management tests and full Maven suite passed; current image run was blocked by an unrelated host port owner | implemented |
| Snapshot pre-dispatch checks, YAML planner/REST JWT propagation, owner polling, and all diagnostic modes | controller/coordinator/REST handler plus common loopback fixture | 35-test focused suite and 61-test full suite passed | implemented |
| Prompt close, HTTP 503, close-or-handoff atomicity, direct waiting-work discard, reservation/reference cleanup | `ExecutionCoordinator` calls public `SkillInvocationHandoff` while holding the Sidecar gate and invokes the admitted handle after unlocking | deterministic coordinator races and real HTTP close/discard tests passed | implemented |
| Sidecar does not tear down accepted work early | framework-owned handle remains outside the queue; phase-0 REST clients outlive framework max-phase stop | real Sidecar coordinator/client/public-observer/normal trace-completion test passed | implemented |
| Framework-only listener ordering, blocked finalization, one deadline, and cutoff | matching framework lifecycle and handoff implementation | 30 matching framework lifecycle tests passed; not used as evidence for Sidecar admission, discard, wiring, or cleanup | implemented under revised ownership split |
| Management-context filtering and asynchronous multicasting | owner identity check and `supportsAsyncExecution=false` | standard Spring multicaster plus owner/foreign-context tests passed | implemented |
| Deterministic quick start, two REST leaves, local issuer/model/callback verification | `examples/quickstart/**`, `scripts/verify-image.py`, README commands | previously retained readiness evidence covers the complete flow; this review's full rerun could not reach it because host port 8080 was occupied | implemented; current rerun environment-limited |
| Configuration/security/diagnostics/restart guidance and Kubernetes example | `README.md`, `examples/kubernetes/deployment.yaml` | configuration reference and full Maven suite passed | implemented |
| CI/release preparation, exact released framework guard, image/JAR/checksums, no overwrite | Maven release profile, CI/release workflows, `prepare-release.py` | release-preparation test, validate-only command, and full Maven suite passed | implemented for the snapshot preparation stage |
| Exact snapshot/source evidence and honest external boundary | ticket and supplied framework readiness record | source/hash/dependency checks matched; post-publication gates remain pending | implemented for local stage |
| Published framework, hosted CI, Sidecar tag/assets/published-image quick start | intentionally deferred | not authorized or run | pending as required |

## Active Project Guardrails

- Simplicity: the handoff reuses the existing Sidecar gate and the review fix only
  adds a bounded subprocess option and exact-resource cleanup.
- Framework boundary: production and test dependencies remain on `ai.loomspan.api`
  plus standard Spring APIs; the architecture test passed. No framework bean or
  internal lifecycle surface is replaced or reflected into.
- Ownership: queue count/bytes and retained records remain Sidecar-owned until
  public handoff succeeds. Sidecar close drains waiting work directly; accepted
  handles remain framework-owned and are always released in `finally`.
- Identity: the verified JWT is installed only for handoff capture and the task's
  input/authentication references are cleared after handoff, rejection, or discard.
- Lifecycle: Sidecar adds no drain timer or listener-order coupling. Framework
  evidence is limited to its accepted-work listener/finalization/deadline/cutoff
  responsibilities under the revised ticket.
- Diagnostics: results and selected public events remain unchanged and are
  published together in the existing record.

## Open Questions and Assumptions

- Port 8080 was occupied by an unrelated Java process during this review. The
  image verifier documents and assumes free loopback ports, so that environmental
  conflict was not treated as a ticket defect and the process was not stopped.

## Verification Results

- PASS — `git diff --check` — no whitespace errors (line-ending conversion notices only).
- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ExecutionCoordinatorTest,ExecutionDiagnosticsTest,AuthenticatedExecutionApiIntegrationTest,ExecutionShutdownIntegrationTest,RestHandlerLifecycleIntegrationTest,ManagementEndpointIntegrationTest,ConfigurationReferenceTest,ReleasePreparationTest,SupportedLoomspanApiArchitectureTest' test` — 35 tests passed.
- PASS — `.\mvnw.cmd -B -ntp verify` — 61 tests passed and the executable JAR was repackaged.
- PASS — `C:\opendev\code\loomspan-framework\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test` — 30 framework-owned lifecycle tests passed.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` — nonpublishing validation passed.
- PASS — `.\mvnw.cmd -B -ntp dependency:tree '-Dincludes=ai.loomspan:loomspan-spring-boot-starter'` — resolved `1.0.0-beta.4-SNAPSHOT`.
- PASS — `Get-FileHash -Algorithm SHA256 C:/Users/mgiacomi/.m2/repository/ai/loomspan/loomspan-spring-boot-starter/1.0.0-beta.4-SNAPSHOT/loomspan-spring-boot-starter-1.0.0-beta.4-SNAPSHOT.jar` — matched the supplied hash.
- PASS — `docker build --tag loomspan-sidecar:sc5-local .` — built image `sha256:4debed3df78261c0983e3c9e2ba10dd510054d3aafd233dfbf6fb1abc562ee4c`.
- PASS — `python -m py_compile scripts/verify-image.py scripts/prepare-release.py` — both scripts compiled.
- PASS — `python -c "<load scripts/verify-image.py; call run() with a 100 ms timeout around a 2 s child>"` — the helper raised `subprocess.TimeoutExpired` and printed `timeout enforced`.
- FAIL — `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes` — the script reached the Compose flow but HTTP port 8080 was owned by unrelated Java process 47732, so the POST reached that Tomcat 9 service and returned 404. Cleanup completed; this is an environment conflict rather than an implementation failure.

## Residual Risks and Optional Developer Checks

- Rerun the complete image/Kubernetes verifier after host ports 8080, 8081, and
  9091 are free. This is needed to refresh complete image evidence after the
  verifier hardening, although production Java and the built image were unchanged
  by the fix.
- Post-framework-publication resolution, hosted CI, tag publication, release
  assets, and published-image checks remain intentionally pending and require
  separate authorization.

## Disposition

- `fixes-applied`
