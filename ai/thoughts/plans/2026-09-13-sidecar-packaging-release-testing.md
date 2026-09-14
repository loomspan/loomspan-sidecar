# Sidecar Packaging and Release Testing Plan

## Change Summary

SC5 closes one concurrency gap by acquiring a supported public framework admission handle under the Sidecar dispatch gate, expands the existing real application tests into a public-surface shutdown/resource matrix, packages the prebuilt Boot JAR as a non-root Docker image, adds a deterministic local planner/JWT/callback quick start and Kubernetes example, asserts configuration documentation against bound properties, and prepares CI/release safeguards. Verification is deliberately split into the local installed-snapshot stage and deferred post-framework-publication/release stages.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Dispatch concurrency | Close can race a dequeued worker unless framework ownership transfer occurs under the same Sidecar gate | Deterministic latch-controlled `ExecutionCoordinatorTest` for Sidecar-close-first, handoff-first, and framework-close-first outcomes |
| Queue/security cleanup | Discard may leak count/bytes, input, or JWT references, or release a reservation twice | Coordinator state assertions and weak/reference-visible fixture assertions after close |
| Sidecar lifecycle | Async multicasting or management child events could delay gate closure, or Sidecar caller/client/observer wiring could tear down before normal accepted-work completion | Real-context `ExecutionShutdownIntegrationTest` and normal-completion `RestHandlerLifecycleIntegrationTest` variants |
| Framework-owned completion | Internal listener ordering, blocked trace finalization, the single shutdown budget, or cutoff could regress after handoff | Matching framework lifecycle suite; Sidecar black-box cutoff coverage is an integration smoke check only |
| Probes | Readiness may advertise before startup validation, stay up after close, or liveness may fail during long work | Management HTTP tests plus valid/invalid/long-running container checks |
| JWT/planner/REST path | Worker handoff may lose the token/roles or callback may merely trust rather than verify it | Common local-key application fixture and quick-start host verification status |
| Container | Image may run as root, ignore mounts/env/overrides, mishandle SIGTERM, or depend on framework source | Image inspection and portable Docker verification script |
| Documentation/configuration | README may omit or misstate a bound key/default/constraint or merge routes into skill discovery | Reflection/explicit-prefix configuration reference test and startup fixture tests |
| Examples | Compose/Kubernetes manifests may drift from actual ports, mounts, environment, probes, and shutdown needs | Image script parse/behavior checks and configuration-reference assertions |
| Release safety | Tag/version may disagree, SNAPSHOT may publish, image/archive/checksum may be absent, or local validation may publish | Maven release profile, `ReleasePreparationTest`, and nonpublishing preparation script |
| External staging | Snapshot success could be misreported as released-dependency/CI/publication success | Readiness record with explicit PENDING gates and fresh post-publication commands |

## Existing Coverage and Environment Constraints

The repository uses JUnit 5, AssertJ, Mockito, Spring Boot application tests, local `HttpServer` fixtures, temporary files, Maven Surefire, and ArchUnit. `ExecutionCoordinatorTest` already covers capacity, queue accounting, identity handoff, discard, and close-before-`begin`; `ExecutionDiagnosticsTest` exhaustively owns selection and retained-result/event semantics. `AuthenticatedExecutionApiIntegrationTest` already exercises real HTTP, local JWT keys, a YAML planner, loopback model, production REST callback, owner polling, pre-dispatch rejection, and token verification, but its fixture is not shared. `RestHandlerLifecycleIntegrationTest` currently proves direct facade calls retain clients through completion/cutoff. `ManagementEndpointIntegrationTest` proves separate-port health/readiness for a valid running context. Focused route/JWT/TLS tests already own exhaustive parsing and authentication edges and should not be duplicated in Docker.

Maven must run on Java 21 with the developer-installed `1.0.0-beta.4-SNAPSHOT`; Sidecar declares no snapshot repository. Docker 29.5.3 and Python are available locally, but image checks remain a distinct gate from the default Maven suite and require free loopback ports. Fixtures use only local keys and loopback/container networking; no external IdP, model, registry, Kubernetes cluster, or credentials are required. The matching framework checkout is `C:/opendev/code/loomspan-framework`; its lifecycle tests are framework-owned evidence for accepted-work internals and are never substituted for Sidecar-owned behavior. Publication, hosted CI, and Maven Central resolution are impossible/unauthorized in the snapshot stage and remain pending.

## Failing Test First
- Name: `dequeuedTaskDoesNotEnterFacadeWhenCloseWinsDispatchHandoff`
- Type: deterministic concurrency unit test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Arrange/Act/Assert: occupy the single worker, enqueue a second task, release the worker and pause it immediately before the guarded public handoff; deliver the owning `ContextClosedEvent`, release the hook, and assert the second task never reaches `SkillInvocationHandoff`, its record is gone, queue count/bytes are zero, admission now throws `ExecutionUnavailableException`, and task input/authentication references are cleared.
- Expected pre-fix failure: the previous `begin()` released the gate before `SkillTemplate.invoke`, allowing close to win that interval and a worker to invoke afterward; pausing before the old `begin()` did not expose that post-`begin()` gap, which review cycle 3 corrected by requiring the public admission contract.

## Tests to Add or Update

### 1. `dequeuedTaskDoesNotEnterFacadeWhenCloseWinsDispatchHandoff`
- Type: concurrency unit test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: the close-or-dispatch boundary is atomic and queue/accounting/security cleanup occurs exactly once.
- Inputs/fixture: one active and one queued task, distinct inputs/tokens, latches at the package-private handoff seam.
- Doubles or boundary isolation: public `SkillInvocationHandoff` test adapter backed by a mocked public `SkillTemplate`; no Spring context startup.
- Edge cases: close wins after dequeue; repeat close; equality queue bytes; inverse winner remains active.

### 2. `frameworkAdmissionWinsThenCompletesAfterDispatchCloses`
- Type: concurrency unit test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java`
- Proves: the fix does not hold the coordinator gate for the duration of framework execution and does not interrupt already-dispatched work.
- Inputs/fixture: admission and completion latches, valid JWT authentication.
- Doubles or boundary isolation: public handoff/admitted-invocation handles backed by a mocked public `SkillTemplate`.
- Edge cases: concurrent `find`, close, and completion; zero residual queue accounting.

### 3. `authenticatedPlannerRestCallbackPreservesVerifiedIdentityForEveryDiagnosticMode`
- Type: real Spring HTTP integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: pre-dispatch validation, asynchronous identity/roles, YAML planner, both production REST routes, owner-scoped polling, exact result, and `NEVER`/`ONERROR`/`ALWAYS` selected public-event behavior against the snapshot.
- Inputs/fixture: extracted `SidecarApplicationFixture`, local RSA keys, local model responses, verifying callback server, success/failure inputs.
- Doubles or boundary isolation: loopback servers only; callback independently validates signature, issuer, audience, expiry, subject, and roles.
- Edge cases: foreign owner 404, expired callback-time token failure, failure diagnostics without result mutation.

### 4. `ownerCloseImmediatelyRefusesTrafficAndDiscardsWaitingWork`
- Type: real web application integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java`
- Proves: owning close event promptly changes readiness, new POST receives `503`, waiting records disappear, reservations clear, and active framework work is not stopped by the Sidecar gate.
- Inputs/fixture: common application fixture with one active and one queued request, real JWT HTTP requests.
- Doubles or boundary isolation: local model/callback blocking only.
- Edge cases: event delivered twice; close while queue at byte equality; caller disconnect does not affect admitted work.

### 5. `foreignManagementCloseDoesNotCloseApplicationAdmission`
- Type: Spring context integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java`
- Proves: management/child-context events do not close the owning Sidecar gate, while the owner event does.
- Inputs/fixture: running application with separate management port and valid JWT.
- Doubles or boundary isolation: standard Spring contexts only.
- Edge cases: management close before/after owner event.

### 6. `asynchronousMulticasterKeepsCoordinatorCloseSynchronousRegardlessRegistrationPosition`
- Type: parameterized Spring lifecycle integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java`
- Proves: Sidecar's `supportsAsyncExecution=false` makes refusal/dispatch closure synchronous with a standard async multicaster regardless of its registration position; coordinator unit tests cover both semantic ownership winners. Matching framework tests separately own actual application/framework listener ordering.
- Inputs/fixture: two fixture context configurations with reversed registration and a `SimpleApplicationEventMulticaster` task executor.
- Doubles or boundary isolation: only standard Spring facilities; no framework bean names, phases, internals, or reflection.
- Edge cases: event executor deliberately occupied; listener returns promptly without waiting for active work.

### 7. `sidecarWorkersClientsAndObserversSurviveNormalClose`
- Type: real application lifecycle integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java`
- Proves: HTTP-admitted worker invokes the production REST handler, selected public observer events and caller polling complete, and target clients close only afterward during normal accepted-work completion.
- Inputs/fixture: real REST callback completion latch and Sidecar diagnostics `ALWAYS`.
- Doubles or boundary isolation: local model/callback servers; assertions use the Sidecar API output and `RestTargetClients.isClosed`, never framework internals or undocumented configuration.
- Edge cases: close during REST response and observer delivery active at close. Actual framework listener ordering is framework-owned and covered in the matching framework suite.

### 8. `frameworkCutoffBoundsBlockedWorkWithoutEarlyOrSecondSidecarTeardown`
- Type: real application lifecycle integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java`
- Proves: as a black-box Sidecar integration smoke check, an uncooperative callback is cut off and target clients subsequently close within a broad bound. The matching framework suite, not this test, proves the single budget, cutoff, and blocked trace-finalization semantics.
- Inputs/fixture: blocked local callback, framework timeout longer than configured Spring phase timeout, elapsed-time bands with generous CI tolerance.
- Doubles or boundary isolation: public application APIs and standard `DefaultLifecycleProcessor` timeout configuration only; matching framework `FrameworkExecutionLifecycleTest` supplies the framework-owned blocked-writer and cutoff evidence.
- Edge cases: phase timeout shorter than framework timeout; interrupted blocked request; cleanup after cutoff.

### 9. `managementProbesTrackStartupLongWorkAndShutdown`
- Type: management HTTP integration test
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java`
- Proves: management port is separate, readiness is not available/up before registration and validation, valid context reports ready, liveness remains up during long execution, and close refusal is observable.
- Inputs/fixture: valid delayed startup/long-work fixture and invalid route/skill startup fixtures.
- Doubles or boundary isolation: loopback HTTP only.
- Edge cases: application port never exposes actuator; invalid config never reports ready.

### 10. `imageRunsNonRootWithMountedQuickStartAndCorrectProbes`
- Type: Docker system integration script
- Location: `scripts/verify-image.py`
- Proves: Dockerfile build, non-root UID/GID, Java defaults, `/sidecar` and override locations, environment secrets, separate probes, valid/invalid startup, SIGTERM handling, and bounded shutdown.
- Inputs/fixture: `examples/quickstart/**`, locally tagged image, isolated Compose project name.
- Doubles or boundary isolation: bundled host container; no external network/account.
- Edge cases: invalid route mount, long callback, readiness transition, container cleanup on failure.

### 11. `documentedCurlQuickStartCompletesAndHostVerifiesCaller`
- Type: Docker end-to-end script
- Location: `scripts/verify-image.py`
- Proves: README token, submit, and polling commands work with one planner/two leaves; host records verified original subject/roles; results and selected diagnostics are intact.
- Inputs/fixture: example-only RSA key, token endpoint, deterministic OpenAI-compatible model responses, both callbacks.
- Doubles or boundary isolation: all services local to Compose.
- Edge cases: callback rejects altered/expired token; poll as a different subject returns 404.

### 12. `configurationReferenceMatchesBoundPropertiesAndExamples`
- Type: unit/document contract test
- Location: `src/test/java/ai/loomspan/sidecar/config/ConfigurationReferenceTest.java`
- Proves: every getter-backed key under `RestRoutesProperties`, `SidecarJwtProperties`, and `SidecarExecutionProperties` is represented exactly once with actual default/required semantics; examples keep route YAML outside skill patterns and use placeholders for secrets.
- Inputs/fixture: production property classes, `application.yml`, README, Compose, Kubernetes manifest.
- Doubles or boundary isolation: local files only.
- Edge cases: null/required JWT source choices, zero clock skew, all diagnostics enum values, location overrides.

### 13. `releasePreparationRejectsSnapshotsAndProducesExpectedArtifactPlan`
- Type: unit/script contract test
- Location: `src/test/java/ai/loomspan/sidecar/release/ReleasePreparationTest.java`
- Proves: only `v<version>` tag publishing is allowed, tag matches a non-SNAPSHOT project version, framework is exactly `1.0.0-beta.4`, CI builds/tests the image without publishing, and release creates image/JAR archive/SHA-256 assets.
- Inputs/fixture: `pom.xml`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, temporary copied metadata for negative cases.
- Doubles or boundary isolation: no GitHub API, registry, credentials, tag, or upload.
- Edge cases: Sidecar SNAPSHOT, framework SNAPSHOT/wrong beta, mismatched tag, missing checksum/upload or publish guard.

### 14. `kubernetesExampleMatchesRuntimeContract`
- Type: local manifest contract check
- Location: `scripts/verify-image.py`
- Proves: example has separate app/Sidecar containers, read-only `/sidecar/skills` and `rest-routes.yaml`, Secret environment references, 9091 startup/readiness/liveness probes, and termination grace exceeding the configured shutdown budget.
- Inputs/fixture: `examples/kubernetes/deployment.yaml` and documented defaults.
- Doubles or boundary isolation: parse locally; no cluster apply.
- Edge cases: missing route subPath, probes accidentally on 8080, embedded secret, too-short termination grace.

## Safe Verification Commands
- Focused: `.\mvnw.cmd -B -ntp -Dtest=ExecutionCoordinatorTest,AuthenticatedExecutionApiIntegrationTest,ExecutionShutdownIntegrationTest,RestHandlerLifecycleIntegrationTest,ManagementEndpointIntegrationTest,ConfigurationReferenceTest,ReleasePreparationTest,SupportedLoomspanApiArchitectureTest test`
- Related suite: `.\mvnw.cmd -B -ntp verify`
- Image/package suite: `.\mvnw.cmd -B -ntp package`; `docker build --tag loomspan-sidecar:sc5-local .`; `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes`
- Release preparation: `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4`
- Matching framework-owned lifecycle: `C:\opendev\code\loomspan-framework\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter -Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest test`
- Snapshot identity: `.\mvnw.cmd -B -ntp dependency:tree -Dincludes=ai.loomspan:loomspan-spring-boot-starter`; `git status --short`; `git -C C:/opendev/code/loomspan-framework status --short`
- Deferred post-publication: `.\mvnw.cmd -B -ntp -U verify`; `.\mvnw.cmd -B -ntp -U dependency:tree -Dincludes=ai.loomspan:loomspan-spring-boot-starter`; hosted CI on the recorded final revision; authorized tag workflow and published-image quick start.

## Optional Developer Checks
- Run the README quick start interactively and inspect the callback host's verified identity/roles output.
- Review Kubernetes termination grace/resource recommendations against the target cluster policy; a live apply is not required.
- After separately authorized publication, inspect Maven Central, GHCR, and GitHub release discoverability. Do not substitute this inspection for executable checksums and published-image verification.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant Sidecar Maven suite passes against the installed snapshot.
- [x] The local Docker image builds, runs non-root, and passes the deterministic image/quick-start/probe/shutdown script.
- [x] The matching framework lifecycle suite passes as framework-owned evidence, and any discovered framework fix is reinstalled and affected Sidecar gates rerun.
- [x] Acceptance criteria map to executable evidence in the implementation plan and readiness record.
- [x] Routine automated tests use only local fixtures and perform no tag, push, workflow dispatch, registry upload, or other live/destructive operation.
- [x] Configuration, security, lifecycle, concurrency, deployment, and release edge cases identified above are covered.
- [x] Exact Sidecar/framework source states and command results are retained before the publication boundary.
- [x] Post-framework-publication, hosted-CI, Sidecar-tag/publication, and published-image checks remain visibly pending until actually performed.
- [x] Optional checks are reported as nonblocking and are not represented as already performed.
