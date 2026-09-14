# Sidecar Packaging and Release Code Review — Cycle 2

## Scope and Repository State

Reviewed the Full 5-Step Pipeline change independently from the SC5 ticket,
research, implementation plan, testing plan, current committed/staged/unstaged/
untracked repository state, connected production code, and the framework beta 4
readiness record. The comparison base is `main` at
`d83902713bcf84375c3f54b8d33a66e048f0aa39`; there are no staged changes. The
ticket-scoped worktree includes the coordinator/test changes, packaging,
examples, documentation, Maven/workflow release preparation, and pipeline
artifacts. The matching framework checkout is
`e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f` with only its readiness record
modified.

The selected `full` profile remains required because the reviewed change affects
security-token propagation, lifecycle/concurrency behavior, container and
deployment behavior, and release automation. No tag, publication, workflow
dispatch, registry operation, or other live external action was performed.

## Findings

No actionable findings remain.

## Findings Resolved in This Context

### [P2] Exercise an actual queued request in the dequeue/close race test
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java:367`
- Scenario: The test named `dequeuedTaskDoesNotEnterFacadeWhenCloseWinsDispatchHandoff`
  submitted one request to an idle single-worker executor. `LinkedTransferQueue`
  handed that request directly to the waiting worker, so the test never created a
  queue reservation or dequeued waiting work.
- Impact: A regression in the required queued-count/queued-byte release path, or
  in the transition from a queued request to the guarded dispatch handoff, could
  escape while the acceptance criterion appeared covered.
- Evidence: The original test admitted only one task and asserted queue totals
  only after close. The coordinator queue deliberately uses `tryTransfer` before
  reserving queued capacity, so this scenario could not traverse the waiting
  queue.
- Fix: The test now blocks an active first invocation, admits and asserts a
  genuinely queued second request, releases the worker, pauses the second request
  after dequeue, delivers close, and proves only the first invocation entered the
  facade while the second record, reservation, input, and authentication are
  cleared. The framework readiness record was updated with the corrected test and
  fresh verification evidence.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Non-root runtime image, mounted `/sidecar`, JVM defaults, and overrides | `Dockerfile`, `.dockerignore`, `application.yml`, Compose fixture | `scripts/verify-image.py` inspection, valid/invalid startup, and override run | implemented |
| Separate management probes and startup validation | eager skill/route registration plus Boot probe configuration | management HTTP integration and image valid/invalid startup checks | implemented |
| JWT identity through asynchronous planner and production REST callbacks; owned polling and diagnostics | execution controller/coordinator, public `SkillTemplate`, production `RestSkillHandler` | authenticated HTTP integration, diagnostics suite, and deterministic image quick start | implemented |
| Immediate Sidecar close gate, direct queued discard, and dequeue-race cleanup | `ExecutionCoordinator` gate, queue accounting, synchronous owner listener | corrected two-task dequeue/close test, shutdown integration, and image SIGTERM check | implemented |
| Framework-owned admitted work and bounded resource teardown | framework public invocation plus Sidecar worker and lower-phase REST client lifecycle | normal completion/cutoff Sidecar lifecycle tests and image shutdown check | implemented |
| Self-contained planner/two-leaf local quick start | `examples/quickstart/**` | image verifier checks exact result, both verified callback paths, ownership isolation, and liveness | implemented |
| Kubernetes and complete configuration/security/diagnostic/update guidance | Kubernetes manifest and README reference | configuration-reference test and Kubernetes verifier | implemented |
| Closed public Loomspan dependency surface | application/test imports remain under `ai.loomspan.api` | ArchUnit supported-surface guard in the full suite | implemented |
| CI image verification and guarded tag release for image/JAR/checksums | CI/release workflows, release Maven profile, preparation script | release contract test and nonpublishing validation | implemented |
| Exact snapshot-stage evidence and honest publication boundary | framework readiness record and ticket execution note | source/dependency inventory plus rerun results | implemented |
| Published framework dependency, hosted CI, and final Sidecar release | deliberately deferred until separate authorization and actual publication | not runnable in the snapshot stage | partial |

## Active Project Guardrails

- Simplicity and technical debt: the implementation reuses the existing queue,
  record, lifecycle, Boot probe, and public framework surfaces; the review fix is
  confined to making an existing test traverse its claimed path.
- Framework remains the skill execution authority: production and tests use only
  the supported `ai.loomspan.api` Java surface and the authorized
  `RestSkillHandler`; the ArchUnit guard passes.
- One admission owner and one retained record: `ExecutionCoordinator` remains the
  sole Sidecar queue/byte/record owner and directly discards queued tasks.
- Trusted identity stays outside model inputs: the verified JWT authentication is
  captured separately, restored on the worker, and independently verified by the
  callback fixtures.
- Startup activation and one framework shutdown budget: route/skill changes are
  startup-only; Sidecar closes dispatch without a drain timer while framework
  lifecycle owns admitted work and its one timeout.
- Preserve the diagnostic contract: results remain unchanged and selected public
  events are retained without added Sidecar truncation or sanitization.

## Open Questions and Assumptions

- The guarded `begin` transition is the Sidecar dispatch linearization point. A
  task that wins that gate is already framework-owned; a task for which close wins
  is removed before `SkillTemplate.invoke`. The corrected test now proves the
  actual queued/dequeued path at this boundary.
- Framework publication, released-dependency resolution, hosted CI, tagging, and
  published-image verification remain pending by design and require separate
  authorization. They are not local-stage failures.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp -Dtest=ExecutionCoordinatorTest test` — 10 tests passed after the review fix, including a real queued/dequeued close race.
- PASS — `.\mvnw.cmd -B -ntp verify` — all 60 Sidecar tests passed and the executable JAR was repackaged.
- PASS — `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes` — unchanged image `sha256:cfb57bf9dccd687af714a3faf455f3ca3034b889ad3e154a49973d4500026f13` passed quick-start, identity, ownership, probes, shutdown, invalid-startup, and Kubernetes checks.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` — nonpublishing release inputs validated.
- PASS — `.\mvnw.cmd -B -ntp dependency:tree "-Dincludes=ai.loomspan:loomspan-spring-boot-starter"` — resolved the installed `1.0.0-beta.4-SNAPSHOT` starter.
- PASS — `git diff --check` — no whitespace errors.
- NOT RUN — `C:\opendev\code\loomspan-framework\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter -Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest test` — no framework implementation changed in this cycle; the required Sidecar full and packaged integration gates were rerun against the installed matching snapshot.
- NOT RUN — released-dependency, hosted-CI, tag, registry, GitHub-release, and published-image checks — publication is pending and these actions are explicitly unauthorized in the local snapshot stage.

## Residual Risks and Optional Developer Checks

- Optionally run the README PowerShell quick start interactively and inspect the
  callback host's human-readable verified subject/roles output.
- Optionally review the Kubernetes termination grace and resource guidance against
  the target cluster policy.
- After separately authorized publication, perform the recorded released-
  dependency, hosted-CI, artifact, checksum, and published-image gates; none are
  represented as already passed.

## Disposition

- `fixes-applied`
