# Package, verify and prepare Loomspan Sidecar for a framework-first beta 4 release Code Review — Cycle 1

## Scope and Repository State

Reviewed the complete ticket-scoped working tree at Sidecar base commit
`d83902713bcf84375c3f54b8d33a66e048f0aa39`, including tracked modifications,
all untracked image/example/script/test/plan files, and the external framework
readiness record. The matching framework source revision is
`e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f`; its only working-tree change is
the ticket-owned readiness record. There were no staged changes. The review
covered production behavior beyond diff hunks, tests and fixtures, packaging,
configuration, documentation, workflows, lifecycle/concurrency, JWT trust,
resource cleanup, operational failure paths, and the intentional publication
boundary. No tag, push, workflow dispatch, upload, publication, or other live
external action was performed.

The selected `full` profile remains required and appropriate because the change
crosses authorization, lifecycle/concurrency, container/deployment, and release
boundaries.

## Findings

No actionable findings remain after the fixes below.

## Findings Resolved in This Context

### [P1] Refuse to overwrite already published beta artifacts
- Location: `.github/workflows/release.yml:67`
- Scenario: The same version tag workflow is rerun or the Git tag is recreated
  after its GHCR image or GitHub release already exists.
- Impact: The original workflow pushed the same versioned image tag and updated
  the same GitHub release, violating the ticket's immutable-version and
  never-overwrite release requirements and making a released version's bytes
  mutable.
- Evidence: `docker/build-push-action` had `push: true` for the existing version
  tag and `softprops/action-gh-release` targeted the current tag without any
  existence guard or serialized release concurrency.
- Fix: Serialized runs by Git ref and added fail-closed prepublication checks
  that permit only confirmed absence, rejecting an existing GitHub release,
  existing GHCR manifest, or inability to establish either state. Extended
  `ReleasePreparationTest` to enforce these guards.

### [P2] Limit write-capable credentials to publication
- Location: `.github/workflows/release.yml:7`
- Scenario: Tag-triggered verification executes repository build, test, Docker,
  Compose, and fixture code before publication.
- Impact: Workflow-wide `contents: write` and `packages: write` permissions gave
  all verification steps a write-capable automatic token, expanding the blast
  radius of a compromised dependency or test beyond what verification needs.
- Evidence: The original top-level `permissions` block applied both write scopes
  to the `verify` and `publish` jobs even though only publication writes.
- Fix: Set the workflow default to `contents: read` and grant `contents: write`
  plus `packages: write` only to the `publish` job. Extended the release contract
  test to preserve this least-privilege split.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Non-root runtime image, JVM defaults, `/sidecar` mounts and overrides | `Dockerfile`, `.dockerignore`, `application.yml`, README | Image inspection, valid/invalid override runs | implemented |
| Eager startup validation and separate readiness/liveness probes | Existing eager skill/route registration, port 9091 defaults, Compose/Kubernetes probes | `ManagementEndpointIntegrationTest`, invalid image startup, image health checks | implemented |
| Snapshot pre-dispatch validation, planner/REST JWT propagation, ownership and diagnostics | Existing API/coordinator/REST handler plus shared loopback fixture | `AuthenticatedExecutionApiIntegrationTest`, deterministic image quick start | implemented |
| Prompt close refusal, queue/reference cleanup and atomic dequeue/dispatch decision | `ExecutionCoordinator` gate and handoff seam | `ExecutionCoordinatorTest`, `AuthenticatedExecutionApiIntegrationTest` close scenario | implemented |
| One framework shutdown budget with clients/callers/observers surviving to completion or cutoff | Existing lifecycle phase ownership and public APIs | `RestHandlerLifecycleIntegrationTest`, `ExecutionShutdownIntegrationTest`, matching framework lifecycle suite | implemented |
| Self-contained one-planner/two-leaf quick start with independently verified caller identity | `examples/quickstart/**`, README | `scripts/verify-image.py` | implemented |
| Kubernetes and complete Sidecar-owned configuration guidance | `examples/kubernetes/deployment.yaml`, README | `ConfigurationReferenceTest`, Kubernetes verifier | implemented |
| Closed supported framework boundary | Production/test imports remain on `ai.loomspan.api` and authorized REST SPI | `SupportedLoomspanApiArchitectureTest` | implemented |
| CI image checks and guarded tag release for image/JAR/checksums | CI/release workflows, release Maven profile, preparation script | `ReleasePreparationTest`, nonpublishing validation | implemented |
| Exact snapshot-stage evidence and honest framework-first handoff | Framework readiness record | Fresh status, dependency tree, Sidecar/image/framework commands | implemented |
| Released dependency, hosted CI, final Sidecar commit and publication evidence | Intentionally not changed before framework publication | Deferred commands remain unrun and visibly pending | implemented staged boundary |

## Active Project Guardrails

- Simplicity and technical debt: the image is a runtime-only Dockerfile over the
  existing Boot JAR; release hardening uses direct workflow checks and one
  contract test without a new runtime abstraction or dependency.
- Framework boundary: application and test architecture checks pass; no new
  internal/autoconfigure dependency, Java skill, SPI, reflection, or bean
  replacement was introduced.
- One admission owner and retained record: the existing coordinator remains the
  sole queue/accounting/record owner; close directly discards queued tasks.
- Trusted identity: JWT authentication remains captured outside model input and
  the original verified token is forwarded to callbacks; the only token issuer
  and private key are explicitly local example fixtures.
- Startup and shutdown: skills/routes remain startup snapshots; Sidecar closes
  dispatch immediately while framework-admitted work retains one framework
  budget and REST clients close afterward.
- Diagnostics: result text and selected public observer events remain unchanged;
  documentation accurately states the business-data and heap-bound caveats.

## Open Questions and Assumptions

- Framework publication, Maven Central resolution, hosted Sidecar CI, and actual
  Sidecar publication remain intentionally pending and require separate
  authorization. Prepared workflow structure and local checks are not treated as
  evidence that any of those external gates passed.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ExecutionCoordinatorTest,AuthenticatedExecutionApiIntegrationTest,ExecutionShutdownIntegrationTest,RestHandlerLifecycleIntegrationTest,ManagementEndpointIntegrationTest,ConfigurationReferenceTest,ReleasePreparationTest,SupportedLoomspanApiArchitectureTest' test` — 28 tests passed.
- PASS — `.\mvnw.cmd -B -ntp -Dtest=ReleasePreparationTest test` — final no-overwrite, serialization, and least-privilege contract passed.
- PASS — `.\mvnw.cmd -B -ntp verify` — 60 tests passed and the executable JAR was repackaged.
- PASS — `docker build --tag loomspan-sidecar:sc5-local .` — rebuilt image `sha256:cfb57bf9dccd687af714a3faf455f3ca3034b889ad3e154a49973d4500026f13`.
- PASS — `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes` — non-root/JVM/mount, probes, planner/two-leaf JWT callbacks, ownership, diagnostics, bounded SIGTERM, invalid startup, cleanup, and Kubernetes contract passed.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` — nonpublishing release validation passed.
- PASS — `.\mvnw.cmd -B -ntp dependency:tree '-Dincludes=ai.loomspan:loomspan-spring-boot-starter'` — resolved `1.0.0-beta.4-SNAPSHOT` from the local repository.
- PASS — `C:\opendev\code\loomspan-framework\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test` — 23 supplemental lifecycle tests passed.
- PASS — `git diff --check` — no tracked whitespace errors.
- NOT RUN — `.\mvnw.cmd -B -ntp -U verify` — released framework `1.0.0-beta.4` is not yet published; this is a required post-publication gate.
- NOT RUN — hosted CI, tag workflow, artifact publication, and published-image quick start — external actions are pending separate authorization and actual framework publication.

## Residual Risks and Optional Developer Checks

- The GHCR/GitHub absence checks are structurally tested locally; their live API
  behavior is exercised only by the separately authorized tag workflow.
- Optionally run the README quick start interactively and inspect the callback
  host's human-readable verified subject/roles output.
- Optionally review Kubernetes termination grace and resource sizing against the
  target cluster policy; no live cluster apply is required for this ticket.

## Disposition

- `fixes-applied`
