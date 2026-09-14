# Sidecar Packaging and Release Code Review — Cycle 3

## Scope and Repository State

Reviewed the complete local snapshot-stage SC5 change independently from the
ticket, research, implementation plan, testing plan, current committed/staged/
unstaged/untracked repository state, connected production code and tests, and
the matching framework readiness record. Prior review documents were not read.

The Sidecar base is `d83902713bcf84375c3f54b8d33a66e048f0aa39` on
`main`. The worktree contains the ticket-scoped production, test, documentation,
container, example, script, workflow, and pipeline-artifact changes listed by
`git status --short`; there are no staged changes. Following the developer's
approved remediation, the matching framework source is clean at
`bfc2764bb661a6eccad9fd120cd687e6a911e99a` before its supplied readiness record
update. Its newly installed starter JAR SHA-256 is
`901769CACBAF7F0CD2B39845ED1D7AC3D63294D64F74CC9D411C16925620D2AA` and contains
both new public API classes. No tag, publication, workflow dispatch, registry
operation, or other live external action was performed.

The selected Full 5-Step Pipeline remains required: the change affects security
handoff, concurrency/lifecycle behavior, deployment packaging, and release
publication safeguards.

## Findings

### [P1] Make the close-or-framework-entry transition genuinely atomic
- Location: `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:287`
- Scenario: A worker returns successfully from `begin()` at line 288, then the
  owning `ContextClosedEvent` closes Sidecar dispatch before the worker reaches
  `SkillTemplate.invoke()` at line 294. If the Sidecar listener runs before the
  framework close listener, the worker can enter a new framework root during
  that interval even though Sidecar has already refused traffic and declared
  dispatch closed.
- Impact: The primary shutdown acceptance criterion is not implemented: a
  dequeued request can begin a new framework invocation after Sidecar dispatch
  closes. Listener ordering can therefore change whether that request starts,
  contrary to the required independent shutdown gates.
- Evidence: The only production delta in this path is a `dispatchHandoff` test
  callback before the pre-existing `begin()` call; `begin()` still releases the
  gate before security-context setup and `SkillTemplate.invoke()`. The new test
  blocks in that callback (`ExecutionCoordinatorTest.java:383-405`), so close
  occurs before `begin()` and exercises behavior the old gate already provided.
  It never pauses after a successful `begin()` as required by the testing plan,
  and would pass without a production concurrency fix.
- Fix: First add a deterministic red test whose pause is after the successful
  Sidecar transition and before real framework admission. Then provide an atomic,
  nonblocking framework-admission handoff that lets Sidecar distinguish work
  already owned by Loomspan from work it must discard. Holding the Sidecar gate
  for the blocking duration of `SkillTemplate.invoke()` is not acceptable because
  it would make the close listener wait and create a second shutdown owner.

### [P2] Exercise the required real lifecycle participants instead of stand-ins
- Location: `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java:53`
- Scenario: Shutdown behavior changes with the relative order of the actual
  Sidecar and framework close listeners, or a trace write/finalization blocks
  while a Sidecar worker and REST client are active. The current order test uses
  a mocked context and an unrelated dummy asynchronous listener, and the real
  lifecycle test only sets `execution-trace.persistence=ALWAYS` before asserting
  retained public events; it neither observes trace finalization nor blocks a
  trace write.
- Impact: Regressions in the exact listener/client/caller/observer/trace wiring
  required by SC5 can pass all current Sidecar tests. The framework's focused
  lifecycle tests are useful supplemental evidence but do not instantiate the
  Sidecar coordinator, caller worker, REST clients, and observer path together.
- Evidence: `ExecutionShutdownIntegrationTest.java:60-75` orders the coordinator
  only against a locally declared `asyncListener`. In
  `RestHandlerLifecycleIntegrationTest.java:71-94`, no trace artifact, trace
  finalization signal, or blocked writer is asserted. The focused and full
  Sidecar suites and the 23-test framework lifecycle suite all pass despite
  these scenarios remaining unexercised.
- Fix: Add real-context Sidecar integration variants that cover both relative
  Sidecar/framework listener orders and expose an observable public/standard-
  Spring trace-finalization boundary, including a blocked-write cutoff case,
  without importing framework internals or replacing framework beans. If the
  needed test/control boundary is not public, resolve it in the framework under
  the repository's explicit extension-contract planning rule.

## Findings Resolved in This Context

- **P1 resolved:** The approved framework change adds public
  `SkillInvocationHandoff` and `AdmittedSkillInvocation`. `ExecutionCoordinator`
  releases the queue reservation, checks the close gate, installs captured
  authentication, and acquires framework ownership under the existing gate;
  it then clears task references and invokes/releases the admitted handle
  outside the gate. Deterministic tests prove Sidecar-close-first,
  admission-first, and framework-close-first behavior.
- **P2 resolved:** Real Sidecar integration now exercises the public handoff and
  proves HTTP caller completion, selected public observer events after configured
  trace persistence, and REST-client lifetime. Sidecar's standard-Spring async
  event test remains correctly scoped to its synchronous close listener, while
  the matching framework suite supplies 30 passing supplemental tests for both
  actual listener orders and blocked trace-finalization cutoff. No internal API,
  reflection, bean replacement, or listener-priority coupling was added.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Snapshot non-root image, mounts/env/overrides, JVM defaults | `Dockerfile`, `.dockerignore`, Compose fixture | Docker build and `verify-image.py` pass | implemented |
| Separate probes, valid/invalid startup, long-work liveness | `application.yml`, Compose/Kubernetes probes | management tests and image verification pass | implemented |
| JWT planner/REST callback, ownership, diagnostics | coordinator, security, production REST handler, quick-start host | focused Spring suite and image quick start pass | implemented |
| Prompt refusal, `503`, queued discard/accounting/reference cleanup | existing coordinator close gate | focused shutdown/API tests pass | implemented |
| Atomic dequeue/dispatch close race | public `SkillInvocationHandoff` acquired under the coordinator gate; admitted invocation runs outside it | deterministic close-first, admission-first, and framework-close-first tests | implemented |
| Actual Sidecar/framework listener-order and observer/trace lifecycle matrix | Sidecar uses only public framework and standard Spring surfaces | real Sidecar caller/observer/trace/client tests plus 30 matching framework actual-order/blocked-trace tests | implemented |
| Runnable planner/two-leaf quick start and Kubernetes example | `examples/quickstart/**`, `examples/kubernetes/deployment.yaml` | image/Kubernetes verification passes | implemented |
| Complete Sidecar configuration/security/diagnostic/update guidance | `README.md` and bound property classes | configuration-reference test and full suite pass | implemented |
| Public Loomspan boundary | application and test dependencies | ArchUnit test passes; source search found no forbidden dependency | implemented |
| CI/tag workflow, release pin, no-overwrite guards, JAR/ZIP/checksums | `pom.xml`, CI/release workflows, preparation script | release contract test and validate-only command pass | implemented for nonpublishing preparation |
| Honest framework-first evidence handoff | framework readiness record | dependency tree and source-state checks | implemented for local snapshot stage |
| Released dependency, hosted CI, tags/publication, published-image quick start | intentionally deferred | not run and not authorized | partial by staged design |

## Active Project Guardrails

- Simplicity: container, examples, and release preparation use direct repository-
  local mechanisms; no unnecessary production authority was introduced.
- Framework authority/public boundary: production and tests use the closed
  `ai.loomspan.api` surface, including the approved minimal public admission
  handoff; no internal dependency or Sidecar-owned replacement contract exists.
- One admission owner/one record: retained by the coordinator.
- Trusted identity outside model input: retained; the original verified JWT is
  propagated through the worker and production REST handler.
- Startup-only activation/one framework shutdown budget: packaging, docs, and
  the atomic handoff conform; admitted work remains framework-owned.
- Diagnostic contract: result text and selected events remain untruncated.

## Open Questions and Assumptions

None. The developer authorized the recommended framework remediation and
explicitly retained the SC5 atomic-dispatch requirement.

## Verification Results

- EXPECTED INTERMEDIATE FAILURE — focused coordinator/diagnostics run — two
  authentication assertions exposed that the initial public-handoff test adapter
  did not restore its captured security context; the adapter was corrected and
  the same 16-test selection passed on rerun.
- EXPECTED INTERMEDIATE FAILURE — final robustness/lifecycle tightening first
  failed compilation because the new non-null handoff guard lacked its import;
  adding the import corrected it.
- EXPECTED INTERMEDIATE FAILURE — the first direct finalization assertion showed
  that public selected events contain `SKILL_STARTED`/`SKILL_FINISHED`, not the
  persistence-only `TRACE_COMPLETED` record. The test was corrected to observe
  the newly written trace file through the filesystem boundary and verify its
  terminal record; the focused 15-test rerun passed.
- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,RestHandlerLifecycleIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ManagementEndpointIntegrationTest,ConfigurationReferenceTest,ReleasePreparationTest,SupportedLoomspanApiArchitectureTest' test` — 35 tests passed.
- PASS — `.\mvnw.cmd -B -ntp verify` — 61 tests passed and the executable JAR was repackaged.
- PASS — `docker build --tag loomspan-sidecar:sc5-local .` — final image rebuilt as `sha256:a1f8f996d6ade53134bf15388fdaff2513898f966b936adc21db22a7790d1742`.
- PASS — `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes` — deterministic image, quick-start, probe, invalid-startup, shutdown, and Kubernetes checks passed.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` — nonpublishing validation passed.
- PASS — `.\mvnw.cmd -B -ntp dependency:tree '-Dincludes=ai.loomspan:loomspan-spring-boot-starter'` — resolved `1.0.0-beta.4-SNAPSHOT`.
- PASS — installed starter hash/class inspection — SHA-256 matched the developer-provided value and the JAR contains `AdmittedSkillInvocation.class` and `SkillInvocationHandoff.class`.
- PASS — `.\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test` (framework checkout) — 30 tests passed at `bfc2764bb661a6eccad9fd120cd687e6a911e99a`.
- PASS — `git diff --check` and `git diff --check --cached` — no whitespace errors; line-ending conversion warnings only.
- NOT RUN — released-dependency `-U` verification, hosted CI, tags, publication, uploads, and published-image checks — intentionally pending separate framework/Sidecar release authorization.

## Residual Risks and Optional Developer Checks

- The local image check stops the container after its long request has already
  completed; in-process Sidecar and matching framework lifecycle tests supply
  the active-work and blocked-finalization evidence. An active-work packaged
  SIGTERM repetition remains an optional final-release confidence check.
- Optionally run the README quick start interactively and review Kubernetes
  termination/resource settings against the intended cluster policy.
- Publication-boundary checks remain pending exactly as recorded in the ticket
  and framework readiness record.

## Disposition

- `fixes-applied`
