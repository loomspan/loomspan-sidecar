# Publish, restore and author complete HTTP executor configuration

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

Operators can author, validate, publish and restore HTTP executor skills and shared
targets through the existing Sidecar management workflows. File mode remains a
complete startup source; database mode restores the selected complete publication.
The obsolete routes document disappears from runtime, storage, API and browser UI.

## Requirements

### Complete configuration and validation

- Consume the request/target/authentication contract delivered by PR 9.1. Operation
  configuration lives in each executor-backed skill's YAML; named shared targets
  live in the selected `loomspan.executor-config` subtree. Remove the separate
  route table and `restRoutesYaml`-style fields rather than preserve both formats.
- HTTP-specific configuration and input validation runs only inside executor
  invocation, before dispatch, using captured configuration. Do not run HTTP domain
  validators during startup/readiness, draft validation, publication, import,
  rollback or restoration. Unknown targets, invalid HTTP methods/body combinations,
  pointers, target settings and HTTP credential usability fail on invocation.
  Framework-valid but HTTP-invalid configuration may start, publish, restore and
  report ready. Preserve Framework validation and management authorization,
  storage/bundle and credential-record integrity checks; none certifies HTTP
  operational correctness. No new Framework accessor/hook, internal dependency,
  startup config re-parsing or second configuration contract is required.
- Framework owns skill validation, RBAC, prepared publication and captured runtime
  configurations. Use its public APIs to publish one complete coherent skill and
  execution configuration, including executor data. Do not add HTTP-specific
  Framework callbacks or reinterpret opaque executor data as Framework credentials.
- File mode applies changes through restart and blocks management mutations.
  Database mode restores the selected publication with no fallback to startup
  files/environment for omitted managed settings or credentials. Process settings
  remain restart-owned. No prior-publication merge or implicit inheritance.
- Preserve one management API and validation/publication path for browser and
  remote authoring. Preserve read/edit/publish permissions, token scopes, CSRF,
  credential rechecks, draft ownership/leases/revisions, stale-base rejection,
  explicit reconciliation and passthrough-recipient approval ownership.
  Successful publication clears the published draft/lease; failure preserves it.
  Imports and rollbacks must not bypass these rules or file-mode restrictions.
- Integrate fixed service credential references with existing credential storage
  without creating an unrelated secret-management feature. Preserve encryption-key
  separation, credential identities/versions and explicit credential replacement.
  Do not leak credentials through inspection/export/validation diagnostics.
  Caller identity still comes from captured authentication, never skill inputs.

### Persistence, captured execution and shutdown

- Sidecar owns durable snapshots, selection, editing and restoration; Framework
  owns runtime generations. Remove durable snapshot identity/protection from
  HTTP-client resources. Keep any necessary durable execution correlation in the
  component that actually owns it, without a parallel Framework-version system.
- Retained Framework work uses captured skill and executor settings across
  publication; new admissions use new settings. Do not replace captured
  configuration with a latest-target lookup. Existing queue/trust checks and
  execution admission ownership remain intact.
- Integrate PR 9.1's client lifetime with publication and shutdown. Prefer the
  application-owned reusable client if verified; do not rebuild generation caches
  merely to reproduce old architecture. If clients require generation ownership,
  use the executor configuration generation rather than assume the entry/skill
  generation identifies it.
- Sidecar stops dispatching/discards queued work at shutdown. Keep clients needed
  by admitted Framework work alive until completion or the Framework deadline,
  then clean up without a second drain budget. Verify through supported APIs.
- Replace obsolete persisted/serialized formats directly. Planning must identify
  whether a development-data reset is required, affected stores and consequences.
  Record that internally; do not add old-format readers, schema migration machinery
  solely for obsolete behavior, or perform a destructive reset as part of planning.

### Browser authoring, exports and guidance

- Update browser editors, inspection, server payloads and export/import to use the
  same skill plus executor-configuration representation. Remove the routes editor,
  route synchronization assumptions and obsolete REST classifications.
- Preserve Framework's classification of executor-backed skills as JAVA while
  retaining YAML provenance and executor identity where the supported public
  metadata exposes it. Do not invent new Framework catalog categories.
- Update Sidecar examples, configuration templates, deployment recipes and packaged
  authoring guidance to the executable contract. Explain explicit path/query
  pointers, body choices, target settings, authentication, response/error behavior,
  file/database modes and validation without teaching obsolete forms.
- Caddy owns mTLS; direct HTTPS uses ordinary client trust/hostname validation.
  Remove claims that the new executor manages client certificates or mTLS.
  Preserve unrelated standard Boot/server TLS behavior and existing security
  boundaries. Do not introduce a certificate lifecycle system.
- Reconcile repository guidance/design-lens wording that still authorizes
  RestSkillHandler or describes publishing REST routes with the accepted
  SkillExecutor architecture. Do not broaden the public Framework boundary.
- This unit supplies its management/UI tests and current documentation. PR 9.3
  proves the composed integration, not substitutes for these checks.

## Acceptance criteria

- [ ] File startup, management publication and database restoration accept
  Framework-valid but HTTP-invalid configuration without domain validation or
  network probes. Invocation rejects it clearly before HTTP dispatch. Framework
  structural/reference failures still reject configuration at their normal boundary.
  Missing managed settings never inherit startup or prior-publication values.
- [ ] A valid management candidate is validated and published coherently through
  public Framework APIs without network probes or HTTP domain validation.
  Framework-invalid, stale or unauthorized
  candidates fail without partially replacing the active configuration.
- [ ] Existing draft ownership, lease/revision, permission, CSRF, scope and
  recipient-approval behavior holds across editing, publication, import and rollback.
- [ ] Restart restores the selected publication and its credential semantics;
  durable snapshot identity no longer depends on HTTP-client existence/lifetime.
- [ ] Retained admitted work uses captured old configuration while new admission
  uses the new publication; publication failure and shutdown do not close needed
  clients early or introduce another drain period.
- [ ] Browser and remote clients can inspect/edit/validate/publish the same new
  representation; export/import round trips retain its meaning and credential
  protection, with no routes document or compatibility path.
- [ ] Obsolete storage/API/UI/configuration contracts are removed; the exact
  development-reset decision and impact are recorded without deleting data during
  planning or adding compatibility machinery.
- [ ] Documentation, packaged guidance and repository constraints describe the
  actual new architecture and limits without migration/upgrade/delivery narratives.
  Validation/readiness success must not claim HTTP configuration is operationally
  valid; HTTP-specific errors may first appear when a skill is invoked.
- [ ] This ticket's relevant backend, browser and lifecycle tests pass, or any
  permitted intermediate blocker is precisely assigned for final reconciliation.
  No tests are disabled merely because they expose unfinished replacement work.

## Context

- External work item: **PR 9**, slice **9.2**, not a separate GitHub PR.
- Depends on [HTTP executor runtime, PR 9.1](./2026-11-02-pr-9-1-http-skill-executor.md).
  Followed by [composed integration, PR 9.3](./2026-11-02-pr-9-3-http-executor-integration-and-completion.md).
- [Design notes](../phases/http-skill-executor-design-notes.md) record the accepted
  direction. Final schema spelling/defaults and verified client strategy come from
  PR 9.1; do not reopen settled behavior merely to reproduce old code.
- RuntimeConfigurationService, GenerationRestResources and management editor/
  inspection code are known affected areas, not prescribed implementation structure.
- Scope includes management, persistence, authoring surfaces and their integrated
  tests so this is one substantive implementation unit, not a docs-only follow-up.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This replaces persisted and management API contracts, changes publication/resource ownership, and spans authorization-sensitive backend and browser workflows.
- **Reassessment triggers:** Validate scope and dependencies against the checkout;
  escalate material new product choices or public Framework gaps. Do not treat a
  small final edit as reducing the assurance needed for the complete ticket.
  This is advisory; pipeline selection remains with the repository workflow.

## Pipeline notes

### Resolved validation blocker — 2026-11-02

The developer approved the Full 5-Step Pipeline. Research is complete; Steps 2–3
produced drafts under the former early HTTP-validation requirement. Implementation
and independent review have not started; no tests or development-data resets have
been performed by those stages or this decision update.

**Decision: HTTP-specific validation is invocation-time only, including for
management publication. The startup-accessor blocker is resolved by changing the
requirement; no Framework change or new snapshot installation is required for it.**

Framework continues to validate its own declarations, executor references,
selected-map structure, schemas and authorization. Sidecar retains management
permissions and storage/bundle/credential-record integrity. Startup/readiness,
draft validation, publication, import, rollback and restoration do not run HTTP
domain validators or probe destinations. The HTTP executor validates its captured
configuration and input before dispatch. Framework-valid HTTP-invalid settings
may be published/restored and report ready; their invocation fails before sending
a request. Management success is not certification of executor operational validity.

The former blocker arose because selected executor configuration is available
through invocation but not public startup metadata. Do not add the previously
suggested accessor, a validator hook, internal access, independent Config Data
re-parsing or a second configuration contract to work around that intentional
boundary. Preserve research as historical evidence, not an outstanding API request.

**Resume Steps 2–3 using the existing installed snapshot.** Recheck the current
source, finalize both updated drafts against this decision, then continue the
already-approved pipeline into implementation and independent review. Do not
restart Step 1 or ask for renewed profile approval solely because this blocker
was resolved. Do not treat the updated drafts as executed or verified plans.

Handoff artifacts:

- [Research, with decision addendum](../research/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md)
- [Implementation draft ready to resume](../plans/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md)
- [Testing draft ready to resume](../plans/2026-11-02-pr-9-2-http-configuration-management-and-authoring-testing.md)

The development-reset impact remains separate: replacement of obsolete
snapshot/draft schemas requires developer-owned replacement of existing SQLite
stores, losing colocated accounts, tokens, drafts and snapshots. No reset is
performed or authorized by this update.

### Existing execution constraints

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


### Step 4 implementation handoff — 2026-11-02

Steps 2–3 were finalized and Step 4 implemented the new contract, credentials,
publication/restoration, independent durable correlation, browser/remote authoring
and current guidance. See [verification and acceptance evidence](../verification/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md).
Final broad verification ran 298 tests: 297 passed; only the unchanged beta.8
release-preparation guard failed, with zero errors/skips. Two additional full
Sidecar shutdown completion/cutoff tests pass separately. The exact release
preparation and version-tag fixture blockers are assigned to PR 9.3 there,
without disabling tests or changing the required snapshot dependency.
Fresh independent Step 5 review remains required. No commit, release or actual
development-data reset was performed.
