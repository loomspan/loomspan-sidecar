# Package, verify and prepare Loomspan Sidecar for a framework-first beta 4 release

## Outcome

Users can run Sidecar next to their application as a non-root container, mount
skills and REST routes, authenticate with a JWT and follow a working end-to-end
quick start. The packaged application proves readiness, asynchronous callback
identity, diagnostics and bounded shutdown with real resource wiring. Release
automation produces an image and JAR archive with checksums, pinned to published
framework `1.0.0-beta.4`.

SC5 is staged: complete local snapshot integration before the framework is
tagged/published, then complete Sidecar's released-dependency verification and
release gates. Starting this ticket does not authorize publication.

## Requirements

### Delivery and release sequence

- Start with the locally installed
  `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`. Complete local
  packaging, integration, documentation and nonpublishing release preparation
  before asking for framework publication. Do not require a published framework
  artifact or successful hosted Sidecar CI to begin or accept this local stage.
- Prove pre-dispatch checks, asynchronous identity propagation through a YAML
  planner and production REST handler, all three diagnostic modes, and packaged
  shutdown/resource behavior against the snapshot before the framework tag.
  Resolve any discovered framework defects there, have the developer reinstall,
  and rerun affected Sidecar integration before treating evidence as current.
- Retain exact commands and results, tested Sidecar commit plus uncommitted
  changes, and the installed framework source revision in the existing framework
  readiness record at
  `C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md`.
  Keep Sidecar implementation/test evidence local to this repository and link
  it from that record rather than copying framework reports as Sidecar proof.
  The developer's install workflow keeps local source and artifact aligned;
  record that tested state without introducing a separate artifact-provenance
  verification system. SC5 release evidence still identifies the tested revisions.
- After local SC5 integration passes, hand off for final framework release
  checks on its final release commit: version/script checks, full build,
  release-profile checks and nonpublishing release-workflow validation under
  the framework's own release process. Then tag/publish framework beta.4 to
  Maven Central through a separately authorized release action. Never overwrite
  a release; integration defects must be resolved before publication.
- After the framework artifact is published and resolvable, switch Sidecar to
  `1.0.0-beta.4`, run final build/tests and hosted CI against that dependency,
  retain evidence identifying the tested revision, and make the final Sidecar
  commit before Sidecar tagging/publication. Intermediate development commits
  are permitted; they are not the final release sign-off.
- Sidecar builds and CI consume Maven artifacts; they do not check out or build
  framework source, and no snapshot repository is introduced. Prepare ordinary
  CI to build/test the image when the released dependency is available. Do not
  report prepared workflows, local results or deferred hosted checks as CI success.
- Prepare a `v<version>` tag workflow that builds/tests and publishes the image
  and JAR archive with checksums. Require framework dependency `1.0.0-beta.4`;
  reject SNAPSHOT dependencies for release. Validate preparation without publishing.
  Actual framework and Sidecar tags, publishing workflow dispatches and uploads
  require separate release authorization; finish all independent local work first.

### Packaging, probes and shutdown

- Choose and document one image build mechanism: Spring Boot build-image or a
  Dockerfile. Run as non-root with JVM defaults suited to a sidecar. The image
  starts from environment configuration and mounted `/sidecar`, with `skills/`
  and the separate `rest-routes.yaml`; supply secrets through environment
  variables. Preserve supported location overrides and standard Boot settings.
- Provide management-port health/readiness probes for orchestration. Readiness
  remains down until skill registration and route validation complete, and goes
  down immediately at shutdown. Liveness remains up during a long execution.
- Preserve and prove the existing independent shutdown gates in the packaged
  application. At owning-context close, reject new execution admission with
  `503`, prevent new dispatch into `SkillInvocationHandoff`, and discard waiting
  requests directly, releasing queue count/bytes and input/security references.
  Synchronize dequeue/dispatch so a dequeued request cannot transfer to framework
  ownership after dispatch closes. A successful handoff is the atomic ownership
  boundary: Sidecar discards work that has not crossed it, while Loomspan owns
  handed-off work. Do not drain queued requests through the framework.
- The independent `ContextClosedEvent` listener returns promptly, opts out of
  asynchronous execution and ignores other contexts' close events. It does not
  wait, invoke the framework, depend on listener order or start a Sidecar drain
  timer. Framework admission independently rejects new top-level work once its
  shutdown begins; existing nested work remains framework-owned.
- Work already handed to Loomspan may finish under mission timeouts and
  `loomspan.shutdown.timeout` (default `30s`). Keep waiting caller workers and
  REST clients alive through framework completion/cutoff during Spring lifecycle
  stop. At cutoff the framework cancels remaining work; subsequent Sidecar
  teardown must be bounded, without early interruption, another grace period or
  unbounded executor `close()` waits. A shorter Spring lifecycle-phase timeout
  must not shorten the framework budget.
- Prove the Sidecar-owned boundary with both deterministic close-or-handoff race
  outcomes, active skill and observer delivery through the actual client/caller
  wiring, management-context close events, and asynchronous standard event
  multicasting. Sidecar integration proves normal accepted-work completion and
  that its resources are not torn down early; matching framework lifecycle tests
  own internal listener ordering, blocked trace finalization, the single shutdown
  budget, and cutoff after successful handoff. Do not treat framework tests as
  evidence for Sidecar-owned admission, discard, resource wiring, or cleanup.
  Use supported public APIs and standard Spring facilities only. No internal bean
  overrides, internal phase/name dependencies, new Sidecar shutdown API,
  reflection bypasses or cross-repository priority conventions.
- Reuse the common Sidecar application fixture for HTTP/JWT/queue/route
  integration. Focused tests retain exhaustive binder/authentication coverage;
  packaged checks prove image startup, quick start and resource lifecycle.
  Do not duplicate all boundary tests in the container or substitute framework
  test results for actual Sidecar integration. Fix integration defects within
  the existing ownership boundaries rather than introducing another queue/store
  or lifecycle authority.

### Quick start and configuration guidance

- Supply a runnable sample `/sidecar` directory containing one YAML planner,
  two REST leaves, and `rest-routes.yaml` with both targets and routes. Bundle a
  stub REST host and a documented local JWT test issuer/host backend. Supply
  explicit planner model/connection configuration and a documented local model
  stub so the example requires no external model or IdP account or client SDK.
- README commands run the image, obtain a local test JWT, submit with `curl`
  and poll the execution. Demonstrate the callback host verifying the forwarded
  original token and restoring the caller's identity and roles. Verify these
  commands against the local image during preparation and against the published
  image after the separately authorized Sidecar release.
- Include a Kubernetes sidecar example with mounted configuration, environment
  secrets and management probes. Document the image's JVM defaults and shutdown
  configuration so deployment termination allows the framework's budget and
  bounded cleanup to operate as described.
- Provide a configuration reference covering every bound `loomspan-sidecar.*`
  key, its implemented defaults/constraints, and the unified target/route format,
  including location overrides and environment placeholders. Generate or assert
  agreement with bound properties in tests. Keep documented `loomspan.*` behavior
  and standard Boot SSL configuration in their own namespaces.
- Document HTTPS/mTLS, issuer/key verification, audiences, token lifetime including
  queue time and no refresh/exchange. Explain existing access-token forwarding
  and backend-issued tokens for session-based applications. Test-token issuance
  belongs to the example host, never production Sidecar. Optional Console
  pairing through `loomspan.observability` may be included while preserving its
  independent operator authentication.
- Explain diagnostic modes, available-history limits and business-data exposure;
  preserve unchanged result text and selected public events without new Sidecar
  truncation/sanitization. Count/TTL/queued-byte bounds are not total-heap limits.
- Document the update procedure: finish active work and retrieve needed results,
  edit skills/routes, then restart. Disk edits do not affect a running instance.
  Restart loses retained records; shutdown discards queued work, and unfinished
  admitted work may be interrupted at the framework deadline. Invalid skills or
  routes prevent startup/readiness until corrected. No hot reload or durable
  recovery is added.

## Acceptance criteria

### Local snapshot preparation, before framework publication

- [x] Local image build succeeds against the installed snapshot. The image runs
  non-root with documented JVM defaults and starts using environment variables
  and the mounted skills/route configuration. Location overrides remain usable;
  no framework source build or snapshot repository is added to Sidecar builds.
- [x] Packaged readiness remains down until skill registration and route
  validation finish; invalid configuration prevents readiness. Management probes
  operate on their separate port and liveness stays up during long work.
- [x] Snapshot integration proves pre-dispatch rejection, asynchronous JWT
  propagation through planner/REST callbacks to a verifying host, owner-scoped
  polling and `NEVER`, `ONERROR`, `ALWAYS` diagnostics with intact selected
  events/outcomes. Local deterministic fixtures require no external account.
- [x] Shutdown promptly lowers readiness, rejects new admission with `503` and
  closes dispatch, including dequeue races. Waiting work is discarded directly;
  reservations and input/security references are released without queue draining.
- [x] Actual Sidecar shutdown wiring proves both close-or-handoff ownership
  outcomes, active skills/observers, management-context events, asynchronous
  multicasting, normal accepted-work completion and no early Sidecar resource
  teardown through public contracts. Matching framework lifecycle tests prove
  internal listener ordering, blocked trace finalization, the single shutdown
  budget and cutoff after handoff. Framework evidence is not substituted for
  Sidecar-owned admission, discard, client/caller wiring or cleanup. Subsequent
  cleanup introduces no unbounded wait or second drain period.
- [x] Quick-start commands succeed as written against the local image with one
  planner, two REST leaves, local model configuration/stub, JWT issuer and host
  that verifies the callback token. The Kubernetes example reflects the same
  configuration/probes and documented shutdown behavior.
- [x] Configuration reference is tested against bound properties, and examples
  use the separate route file. Security, diagnostics/data limits, restart-only
  activation and state loss are accurately documented. Public-surface architecture
  checks and the required Sidecar build/integration suites pass.
- [x] CI/release preparation includes image build/tests and a tag workflow for
  image/JAR/checksums, enforcing released framework `1.0.0-beta.4` and refusing
  SNAPSHOT release dependencies. Nonpublishing validation succeeds; no publishing
  operation is performed by local preparation.
- [x] Retained local evidence identifies exact commands/results and both tested
  source states in the existing framework readiness record. Any discovered
  framework defects have been fixed, reinstalled by the developer and retested.
  The handoff states local readiness and all remaining publication/CI gates
  without claiming full SC5 completion prematurely.

### After separately authorized framework publication

- [ ] Framework beta.4 is published/resolvable following its final release
  checks. Sidecar pins that released dependency and passes final build/tests,
  integration and hosted CI, including image build, with the tested revision
  recorded before the final Sidecar release commit/tag.
- [ ] After separately authorized Sidecar publication, the tag workflow produces
  the image and JAR archive with checksums, and the quick start succeeds as
  written against the published image. Release evidence identifies the artifacts
  and source state; no release is overwritten. These checks stay pending until
  they actually run, even if local implementation/review is complete.

## Local snapshot execution note — 2026-09-14

The initial independently executable local snapshot-stage checks passed against
the installed `1.0.0-beta.4-SNAPSHOT`, with exact source states, commands,
results, and image identity retained in the framework beta 4 readiness record.
Independent review cycle 3 subsequently found that the Sidecar transition ended
before actual framework admission and that the required trace-finalization
lifecycle evidence lacked a supported boundary. The developer authorized the
recommended framework remediation rather than weakening the atomic-dispatch
requirement.

Framework commit `5d19c7a` added the supported `SkillInvocationHandoff` and
`AdmittedSkillInvocation` API; cleanup is committed at framework revision
`bfc2764bb661a6eccad9fd120cd687e6a911e99a`. On 2026-09-14 the developer
confirmed that a new `1.0.0-beta.4-SNAPSHOT` was installed. The local starter
JAR contains both public API classes and has SHA-256
`901769CACBAF7F0CD2B39845ED1D7AC3D63294D64F74CC9D411C16925620D2AA`.
Sidecar must now adopt the handoff, resolve the remaining lifecycle-evidence
finding through supported contracts, and rerun affected integration and release
gates before local SC5 readiness is current again.

Review cycle 4 confirmed that Sidecar correctly adopts the public handoff and
that the remaining blocker was solely the ticket's demand for a Sidecar-local
blocked trace-finalization boundary. The developer then clarified the intended
ownership model: Sidecar directly discards all work that has not successfully
crossed `SkillInvocationHandoff`; Loomspan exclusively owns accepted work,
including blocked trace finalization, its listener ordering, deadline and cutoff.
Accordingly, this ticket no longer requires a public trace-writer SPI or a
Sidecar test that blocks framework-internal finalization. Sidecar still must
prove its own handoff race outcomes, discard/reference cleanup, real resource
wiring, and normal accepted-work completion without internal dependencies.

No tag, publication, publishing-workflow dispatch, hosted-CI claim, or released-
dependency claim was made. The two post-publication acceptance criteria remain
pending and require separate authorization and actual external evidence.

## Context

[SC5](../phases/phase-sc5.md), the [handoff](../beta4-handoff.md) and
[design lens](../design-lens.md) own this final delivery unit. The developer
confirmed SC1, SC2+SC3 and SC4 complete and committed. Current history includes
[REST handler](2026-09-13-generic-rest-skill-handler.md) implementation `c709cab`
and cleanup `d4471c0`; [authenticated execution](2026-09-13-authenticated-execution-api.md)
already supplies ownership, queue/store, diagnostics and shutdown gates.
Historical pending-phase wording does not override those completion confirmations.
SC5 proves their packaged integration and finishes deployment/release support.

Use the existing Java 21 / Boot 4.1 platform and Boot's Jackson 3. Application and
test Loomspan dependencies stay within the closed supported `ai.loomspan.api`
surface; the existing `RestSkillHandler` is the authorized SPI. No Java skills,
new framework extension contracts, internal dependencies or undocumented settings.
Consult matching framework source/documentation in
`C:/opendev/code/loomspan-framework`; the developer reinstalls after changes.

Excluded: new execution/transport features, alternate authentication, token
refresh/exchange, durable storage, hot reload, independent shutdown budgets and
framework release implementation inside Sidecar's build. Image mechanism,
example organization and workflow plumbing are implementation choices within the
existing repository/release conventions. Keep process and code proportional.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Packaged lifecycle/concurrency, deployment configuration and
  cross-repository release gates require research, planning, integration testing
  and independent review. Earlier component tests do not establish these outcomes.
- **Reassessment triggers:** Equivalent packaging already landed, changed
  framework lifecycle contracts or changed release scope. Publication status
  changes which gates can execute, not the required evidence or authorization.

## Pipeline notes

- This ticket has an intentional external release dependency. Complete and
  review all local snapshot work before handing off for framework release;
  do not request publication as a prerequisite to starting. At the publication
  boundary, retain results and report the local stage complete with later gates
  pending, using the repository workflow's handoff mechanism. Resume released-
  dependency verification when framework publication is confirmed. Ticket
  execution alone does not authorize either project's tag/publication, and
  deferred gates must not be marked passed or waived.
- Developer decision on 2026-09-14: a successful public invocation handoff is
  the sole Sidecar-to-framework ownership boundary. Sidecar dumps all work that
  has not crossed it; framework tests own blocked trace finalization and cutoff
  after it. Do not add a public trace-finalization API solely to duplicate that
  framework-owned proof in Sidecar.
