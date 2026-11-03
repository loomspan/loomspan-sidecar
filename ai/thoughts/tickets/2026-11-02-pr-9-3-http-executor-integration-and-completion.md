# Prove the complete HTTP executor architecture with Sidecar clients

## Development constraints

**Sidecar is in development with no implementations, deployments or customers. Destructively
replace superseded contracts, code and schemas. Do not add compatibility shims,
legacy adapters, parallel legacy APIs or migration machinery solely to preserve
obsolete development behavior.**

Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
Choose the simplest complete solution, minimize technical debt, and keep code,
tests and documentation proportional. Document any required development-data
reset and its impact; planning does not authorize deleting data.

Use only the closed supported `ai.loomspan.api` surface in application and test
code, with ArchUnit enforcement against `ai.loomspan.internal..` and
`ai.loomspan.autoconfigure..`. The accepted replacement is an annotated
`@SkillExecutor` bean method, not the removed REST SPI, a new SPI, bean replacement
or reflection into Framework internals. Sidecar hosts no Java `@SkillMethod`
skills. Standard Spring APIs are allowed. A concrete missing public capability
requires explicit Framework discussion/planning, not an internal dependency.

Develop against the locally installed
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.9-SNAPSHOT` and matching source
at `C:/opendev/code/loomspan-framework`. The developer installs Framework changes;
retest affected Sidecar behavior afterward. Do not rebuild Framework in Sidecar's
normal build/CI, introduce a snapshot repository, or add artifact/source-revision
verification machinery. The separately installed old documentation skill is stale.
Defer Framework beta.9 publication until the complete PR 9 integration effort is
proven locally. After publication, pin the released dependency for Sidecar's final
build/tests and final commit; hosted CI is deferred until publication. These
tickets do not authorize publishing releases. Sidecar remains independently
versioned at `1.0.0-beta.4-SNAPSHOT`, with planned release `1.0.0-beta.4`.

Each implementation unit owns its tests and documentation. Product documentation
and packaged skills describe current supported behavior, without migration recipes,
upgrade histories, PR tracking or future-delivery promises. Keep intentional-break
assessments and transitional limitations in internal work artifacts.

## Outcome

The assembled Sidecar HTTP executor works through real supported Framework
integration and representative clients, including the neighboring test suite.
All permitted intermediate breakage is resolved. Local evidence establishes
whether Framework beta.9 needs further changes before its release.

## Requirements

### Adapt representative clients

- Complete [PR 9.1](./2026-11-02-pr-9-1-http-skill-executor.md) and
  [PR 9.2](./2026-11-02-pr-9-2-http-configuration-management-and-authoring.md) first. Their tests and documentation remain owned by
  those units; this ticket updates composed clients and closes cross-cutting gaps.
- Adapt the maintained HTTP integration in
  `C:/opendev/code/loomspan-sidecar-test-suite` to executor-backed YAML skills,
  per-skill operations and named targets in executor configuration. Update
  generators, harness configuration, fixtures and assertions that still depend
  on REST declarations/routes, not only checked-in YAML.
- Treat the suite as one client, not the primary product contract. Preserve its
  business purpose and Java/Python equivalence; change obsolete integration
  assumptions directly rather than add Sidecar special cases or adapters.
  Read that repository's current instructions before modifying it.
- Cover its eight equipment lookup operations with flat caseId/assetId bodies,
  createServiceRequest with its nested approval envelope, and representative SDK
  owned callbacks including caller identity and bodyless POST where applicable.
  Use agreed whole-input/selected-field body behavior, not artificial mappings.
- Keep provider access disabled and use maintained deterministic/local fixtures.
  No paid model calls, live business side effects, credential/data resets, commits,
  pushes or releases are authorized by this ticket. Preserve operational data,
  local credentials and maintained reference packs; do not overwrite them with
  new run outputs. If a maintained replay contract needs a material change,
  surface it rather than fabricate a passing reference.
- Align consumers with the developer-installed beta.9 snapshot and matching local
  Framework source without a separate artifact/source provenance system. Update
  stale suite version assumptions relevant to this work; do not revive obsolete
  revision-verification requirements merely because the suite once used them.

### Composed behavior and final verification

- Prove invocation-only HTTP domain validation in file and database modes:
  Framework-valid but HTTP-invalid settings can start, publish and restore without
  HTTP domain validation or network probes, then fail on invocation before dispatch.
  Framework structural/reference validation and management authorization/integrity
  checks remain active. No Framework configuration accessor or validation hook is
  required; do not recreate the superseded early-validation requirement.

- Prove real Sidecar invocation of executor YAML through public Framework APIs,
  including direct calls and standard model tool calls with deterministic model
  fixtures. Do not use Framework's own passing tests as Sidecar evidence.
- Prove terminal/recoverable HTTP behavior and content fidelity: only explicitly
  listed permitted 4xx rejections provide recoverable feedback; transport,
  authentication, redirects, 5xx, size/decoding failures remain terminal.
  Direct calls propagate exceptions; do not claim current step planning recovers.
- Prove no automatic replay after timeout/lost response, redirects or cookie reuse,
  original caller-token fidelity and expiry enforcement, and no cross-user header/
  credential state. Receiving services retain their own authorization checks.
- Verify retained admitted execution across publication, new-admission settings,
  restart restoration and Framework-aligned shutdown with required clients alive.
  Preserve queue/trust and management security boundaries; do not equate entry,
  executor generation and durable identities.
- Use a few representative HTTP/HTTPS/Caddy compositions rather than an exhaustive
  deployment matrix. Prove standard HTTPS validation and the agreed Caddy-owned
  mTLS boundary without adding executor client-certificate support or treating
  untested network layouts as prohibited product configurations.
- Resolve every deferred build/test blocker from 9.1/9.2. Run the repository's
  complete required local build/checks and affected maintained suite lanes against
  the snapshot. Do not disable tests or use compatibility shims to obtain green.
  Framework source is not rebuilt as part of the Sidecar build.
- Ensure no active code, tests, templates, management payloads, client generators or
  consumer guidance still depends on removed REST contracts. Historical internal
  records may retain accurate historical references.
- Any concrete unsupported Framework need is a separate explicit planning
  discussion. Do not import internals or expand Framework scope silently. After
  developer-installed Framework fixes, rerun affected Sidecar verification.
- Record local evidence, remaining limitations and final disposition in internal
  work artifacts. Product documentation describes actual behavior without release
  history, migration recipes, ticket tracking or promises of future support.

### Release boundary

- Completion here proves the snapshot integration locally; it does not publish
  Framework or Sidecar. Keep Framework beta.9 unpublished until this evidence is
  complete and any discovered contract gaps are resolved.
- Record the remaining release sequence explicitly: developer publishes Framework
  to Maven Central first; then pin Sidecar to the published Framework version and
  repeat its final build/tests and final commit, with hosted CI checked after
  publication and before Sidecar release. Never overwrite a release.
- Do not claim the release-artifact/hosted-CI gate passed while only snapshot
  evidence exists. Sidecar's next version/tag remains 1.0.0-beta.4/v1.0.0-beta.4.

## Acceptance criteria

- [ ] File readiness, management validation/publication and restart restoration
  do not certify HTTP domain correctness. A Framework-valid HTTP-invalid fixture
  reaches those boundaries and fails only when invoked, with no outbound request;
  separate Framework-invalid fixtures still fail Framework validation.

- [ ] The equipment-service and relevant SDK harness/generator paths use the new
  executor architecture; representative local composed calls pass without paid
  providers, altered reference outputs, compatibility adapters or client-specific
  Sidecar behavior.
- [ ] Sidecar-owned public-API evidence demonstrates direct and standard tool-call
  success/failure behavior, complete text/error fidelity, authentication/expiry,
  no automatic replay/redirect/cookie state and bounded responses.
- [ ] Real publication/restoration/shutdown scenarios confirm captured settings,
  durable ownership and resource lifetime; Framework tests are not substituted
  for Sidecar integration evidence.
- [ ] Representative HTTPS and Caddy-owned mTLS scenarios preserve trust boundaries
  without adding executor TLS identity management or an exhaustive network matrix.
- [ ] Every intermediate blocker from earlier tickets is explicitly reconciled;
  the complete Sidecar build/required local checks and affected maintained suite
  lanes pass against the installed snapshot, with no disabled failing tests.
- [ ] Active consumers and current guidance contain no obsolete REST contract
  dependencies; application and test Framework dependencies remain public-only.
- [ ] Internal evidence identifies what was executed, its results and limits, any
  Framework gaps and their resolution, and the still-pending published-artifact/
  hosted-CI release gate. No release/publication is performed by this ticket.

## Context

- External work item: **PR 9**, slice **9.3**, the final implementation ticket.
  Sequence: 9.1 -> 9.2 -> 9.3. The developer commits locally between tickets
  without syncing; all builds/checks must be reconciled by this ticket's completion.
- [Design notes](../phases/http-skill-executor-design-notes.md) contain the agreed
  behavior. Existing suite observations were source review, not evidence that the
  new architecture already works.
- This is substantive cross-repository harness/client adaptation and integration
  work, not a review-only or documentation-only ticket. Keep changes confined to
  the new architecture and its proof; unrelated model tuning and business redesign
  are out of scope.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This changes maintained cross-repository client/harness contracts and proves security, lifecycle and protocol behavior across the assembled system.
- **Reassessment triggers:** Validate scope and dependencies against the checkout;
  escalate material new product choices or public Framework gaps. Do not treat a
  small final edit as reducing the assurance needed for the complete ticket.
  This is advisory; pipeline selection remains with the repository workflow.

## Pipeline notes

- The developer will commit locally between tickets without syncing. Intermediate
  builds and application startup may be broken until the last ticket; this is
  explicitly accepted. Do not add shims, temporary Framework stubs, disabled tests
  or dual contracts merely to make an intermediate ticket green.
- Each ticket still supplies its own tests, documentation and independent review.
  Run meaningful checks available at that stage. Record every blocked check,
  exact cause and downstream owner in its verification artifact. A downstream
  build blocker is not evidence that a test passed.
- PR 9.3 must reconcile all deferred checks and leave the complete build and
  required local suites passing. Intermediate breakage does not permit reduced
  assurance at completion. These tickets do not instruct agents to commit, sync,
  push or publish; the developer owns those actions.
