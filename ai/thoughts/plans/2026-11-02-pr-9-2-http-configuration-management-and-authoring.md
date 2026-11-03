# HTTP configuration management and authoring implementation plan

**Development policy: destructively replace superseded contracts, code and schemas;
do not add compatibility shims, adapters, dual APIs or obsolete-format readers.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
No development data deletion is authorized or performed by this plan.

## Overview

- Ticket: `ai/thoughts/tickets/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md`
- Research: `ai/thoughts/research/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md`
- Pipeline: full, developer approved on 2026-11-02.
- Outcome: one authored representation of skill YAML and complete execution YAML,
  validated, published and restored through the shared management workflow.
- Status: **Step 4 implemented and verified; independent Step 5 review pending**.
  The developer resolved the blocker by choosing invocation-only HTTP validation.
  Current source and tests were rechecked against that decision. No Framework
  change/install is required. Implementation and actual test results are recorded in the verification artifact.

## Current State

The research artifact describes the full call graph. Source rechecked during
planning confirms:

- `configuration/RuntimeConfigurationService.java` still injects removed
  `GenerationRestResources` and `RestRouteCatalogValidator`, stages route resources
  on validation/publication and obtains file targets from the obsolete route file.
- `configuration/EffectiveExecutionConfiguration.java` decrypts all managed records
  into the Framework provider map, rejects every ordinary `headers` field and
  restores a route-bearing `ManagedConfiguration`.
- `storage/ManagedConfiguration.java`, snapshot/draft SQL and
  `bundle/ConfigurationBundleV3.java` persist a separate route document.
- `execution/ExecutionCoordinator.java` resolves admitted generation to durable
  snapshot through the removed HTTP-resource owner.
- `static/management/assets/editor.js`, `console.js` and
  `management/ManagementPagesController.java` expose the route document; browser
  tests explicitly operate `#editor-rest`.
- PR 9.1's `HttpExecutorConfiguration` already supplies network-free settings and
  operation validation plus encrypted-record attachment. Its client is reusable,
  application owned, and has public-API lifecycle evidence.

All Java paths above are under `src/main/java/ai/loomspan/sidecar/` unless otherwise
qualified. No production/test edits or test executions occurred during planning.
Original planning entry contained only the research artifact against clean baseline
commit `736315dc0c67b179b1835f359604ffcf734d998e` on `main`. On resume, tracked edits
to the design lens, HTTP design notes and 9.1/9.2/9.3 tickets are developer planning
work and must be preserved. Research and plans are this pipeline's untracked
artifacts; no production/test changes exist yet.

## Resolved Validation Boundary — 2026-11-02

The developer chose invocation-only HTTP-specific validation, including management
publication. This supersedes the former startup/draft domain-validation requirement.
Framework checks its declarations, executor references, selected-map structure,
schemas and authorization. Sidecar retains management authorization, storage/bundle
integrity and credential-record protection. None of those checks certifies that an
HTTP operation/target is valid or reachable.

Do not call HTTP domain validators at startup/readiness, draft validation,
publication, import, rollback or restoration. The executor receives captured
skillConfig/executorConfig/input and performs HTTP validation before dispatch.
Framework-valid but HTTP-invalid configuration may start, publish, restore and
report ready; its invocation fails clearly without sending an HTTP request.

The earlier evidence remains factual context: Framework's internal YAML loader
preserves selected executor data in an internal representation and public startup
metadata has no executor-map accessor. That is no longer a gap for this requirement.
Do not introduce a public accessor, validation hook, internal import/reflection,
bean replacement, independent Config Data re-parsing or a second config contract.
No Framework change/install is needed to resume this work.

This finalized plan uses existing research and the installed snapshot. Continue
the approved pipeline into implementation, preserving developer planning edits.

## Desired End State and Scope

In scope are startup, selected snapshot restoration, credential assembly,
management validation/publication, durable correlation/history protection,
storage/bundles, browser authoring/inspection, remote payloads, examples/guidance
and their tests. Keep complete replacement and the existing management ownership,
authorization, credential rechecks, stale-base/revision/lease rules intact.

Out of scope are new Framework HTTP hooks/SPIs, namespace product expansion,
certificate lifecycle, independent credential infrastructure, framework builds
inside Sidecar, release publication, commits/pushes and deployed data deletion.
The former startup contract blocker is resolved, not deferred to PR 9.3.

## Active Project Guardrails

- Only `ai.loomspan.api` Framework types; retain ArchUnit prohibition of internal
  and autoconfigure dependencies and Java `@SkillMethod` declarations.
- Framework owns RBAC, preparation, generations and shutdown deadline. Sidecar
  consumes captured executor maps; no latest-target lookup or second drain budget.
- One management path serves browser and PAT clients. Retain CSRF and ambiguous
  credential rejection, token scopes, ownership/handoff, validation and rechecks.
- Database publications contain complete managed state and exact credential
  versions, with no startup/prior-publication inheritance. Process settings stay
  restart owned. File mode blocks all mutation, import and rollback paths.
- Product docs describe executable support without PR tracking or migration
  stories. Caddy owns mTLS; executor HTTPS uses ordinary JVM trust/hostname checks.

## Impact and Risk Analysis

Credential exposure, partial activation and admitted-work capture are the principal
risks. Keep diagnostics generic at parse/crypto boundaries, preserve authored
reference-only content, and never place plaintext HTTP credentials into effective
YAML or exports. Prepare before durable selection; retain existing rollback/fault
behavior and publish admission rechecks. Correlation must be staged before publish
and retained until Framework retirement, independently of client existence.

### Development reset decision

Replace `V1__configuration_snapshots.sql` and `V3__management_drafts.sql` definitions
directly to remove `rest_routes_yaml`; no added old-format migration/read path.
**Existing development databases require replacement/reset by the developer**:
Flyway checksums and stored contracts change. The affected store is the configured
SQLite database (default `/sidecar/data/sidecar.db`) and its associated WAL/SHM
state, handled only while the application is stopped. It also holds management
accounts, browser credentials, PATs, drafts/leases, credential versions, snapshots,
selection and startup history; replacing it loses all those records and requires
bootstrap/re-authoring. The external encryption key is separate and must not be
deleted. Preserve backups under developer control. This plan performs no reset.

Replace route-bearing portable bundle format 3 with format 4, rename implementation
and tests accordingly, remove `rest.json`, and reject format 3 explicitly. Keep
inventory/digests/size/CRC/ciphertext controls. Old development exports cannot be
imported; re-author/export current configuration. No automatic conversion.

## Implementation Approach for Invocation-Only HTTP Validation

Implementation phases below are complete. See
[Step 4 verification](../verification/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md)
for actual commands, acceptance evidence and precisely assigned release blockers.
Planned focused selectors below identify covered classes; where not invoked
separately, those classes ran in the broad suite. No Framework API work is pending.

### Phase 1: Replace authored/storage/bundle contracts

- [x] `storage/ManagedConfiguration.java`: only skill documents, execution YAML
  and required credential identifiers; remove route constructors/accessors.
- [x] `storage/ConfigurationSnapshotRepository.java`, `ConfigurationDraftStore.java`,
  `ConfigurationSnapshotStore.java`, related snapshot/draft records and migrations:
  remove route columns, reads/writes and empty route defaults.
- [x] Replace `bundle/ConfigurationBundleV3.java` with `ConfigurationBundleV4.java`,
  updating import/export consumers and tests; payloads are skill files, execution
  JSON and optional authenticated ciphertext only.
- [x] `management/ManagementEditingController.java`: Save/Reconcile use the same
  new complete representation. Reject obsolete fields rather than accept a dual API.
- [x] Update constructor/payload fixtures across tests without disabling coverage.
- [x] Remove `config/RestRoutesProperties.java`, its registration in
  `LoomspanSidecarApplication.java`, `src/main/resources/sidecar-empty-rest-routes.yaml`
  and obsolete `loomspan-sidecar.rest-routes`/`url-variables` configuration.
- [x] Verification: `./mvnw.cmd -B -ntp "-Dtest=ConfigurationSnapshotRepositoryTest,ConfigurationSnapshotStoreTest,ConfigurationDraftStoreTest,ConfigurationBundleV4Test" test`.

### Phase 2: Compose credentials without early HTTP domain validation

- [x] `configuration/EffectiveExecutionConfiguration.java`: parse strict authored
  execution YAML, collect provider references only from documented connection
  fields (`api-key-ref`, `header-refs` and managed Gemini JSON reference) and
  authenticate managed records as storage integrity, without HTTP usability checks.
  Supply only consumed provider credentials to Framework (unused entries fail
  there). Attach retained encrypted records with `withCredentials` only to an
  existing map-valued `default.http` subtree, without walking targets/auth or
  checking reference usability. Sharing provider/HTTP records works naturally.
  Missing HTTP references do not fail composition unless independently required
  by management credential inventory. Do not create/repair absent or non-map
  subtrees; leave selected-map validation to Framework. Preserve exact
  identities/versions and explicit replacement, with no environment lookup.
- [x] Keep authored YAML distinct from generated effective YAML. Restoration
  regenerates/checks the effective candidate from selected authored content and
  exact retained ciphertext rather than accepting authored generated credentials
  or injecting twice. Check regenerated effective content against selected captured
  content to detect storage inconsistency, not HTTP semantics. Candidate equality
  includes effective YAML, provider values and exact retained identifier/version/
  ciphertext records. Never serialize plaintext maps; expose authored YAML only.
- [x] Narrow the streaming secret scanner's structural exemptions to HTTP ordinary
  target headers and reference header names; preserve duplicate/multi-document/
  malformed YAML protections and provider plaintext rejection. Reject authored
  HTTP `credentials` inventories in management; file mode may load the documented
  encrypted inventory with the external key.
- [x] Do not add an authored HTTP-domain validator or walk skill documents to
  validate operations during startup/management. Keep PR 9.1 HTTP validators on
  the invocation path using captured maps. Credential reference collection and
  encrypted-record attachment may assemble managed content, but must not become
  early validation of HTTP targets, methods, pointers or credential usability.
  Preserve actual credential storage/security invariants and provider-specific
  Framework requirements. Document this boundary in candidate diagnostics.
- [x] `ConfigurationModeConfiguration.java`: exclude startup executor-config in
  database mode alongside other managed roots; bootstrap an empty catalog without
  startup executor data. Framework requires the annotated executor's selected `http`
  map even for an empty catalog (verified by composed startup). Bootstrap the fixed
  Sidecar resource `sidecar-database-bootstrap.yml` through Boot's installed public
  `PropertySourceLoader` interface, ahead of operator sources; do not inspect or
  reconstruct operator Config Data. The initial persisted selection also explicitly
  contains an empty `default.http` map. Add the same empty map to file defaults.
  Do not insert/repair settings in authored management publications. Test
  missing selected maps separately from present but HTTP-invalid maps according
  to Framework's own reference rules.
- [x] Verification: `./mvnw.cmd -B -ntp "-Dtest=EffectiveExecutionConfigurationTest,CredentialPortabilityIntegrationTest,ConfigurationModeIntegrationTest" test`.

### Phase 3: Publish/restore and correlate independently of HTTP clients

- [x] `RuntimeConfigurationService.java`: explicit `default` namespace calls,
  coherent Framework candidate preparation/validation, no route staging or client
  resource retirement. Do not run HTTP domain validation before publish/dispatch
  readiness. File startup uses the existing public catalog/lifecycle contracts
  for readiness and durable correlation, without inspecting selected HTTP settings.
  DB restore/publication passes complete executor data to Framework. HTTP-invalid
  but Framework-valid candidates remain admissible until invocation validates them.
  File inspection removes route/target fields rather than parsing Config Data;
  retain public catalog identity/kind and existing safe provider/process inspection.
  Public descriptors expose no executor identity or YAML provenance: do not invent
  metadata or inspect internal catalogs to fill the gap.
- [x] Introduce a small durable-correlation registry in `configuration/` mapping
  Framework opaque generation IDs to durable snapshot IDs. It owns mapping and
  history protection only, with synchronized stage/discard/retire/lookups.
  Register retirement before activation; stage durable mapping before publish,
  remove on failed activation, and keep captured admitted mapping until retirement.
  No parallel version allocation or client ownership.
- [x] `ExecutionCoordinator.java`: inject that registry; preserve captured admitted
  generation lookup and release-on-mapping-failure. Keep queue/trust/shutdown logic.
- [x] `RuntimeConfigurationService.prune`: protect retained registry IDs and active
  snapshot. Callbacks only update small in-memory state; no blocking SQL work.
- [x] Keep `HttpExecutorClient` application owned. Publication does not close it;
  shutdown continues to use Framework's single deadline and lifecycle ordering.
- [x] Align directly affected API/test namespace calls mechanically to the current
  public API when needed for this unit's tests, including
  `api/SkillCatalogController.java` (`snapshot("default")`) and Framework fixtures.
  This is mechanical integration, not namespace product expansion. Do not assign
  this ticket's own compiler failures to PR 9.3; record independent residual
  blockers precisely, with no compatibility fallback.
- [x] Verification: `./mvnw.cmd -B -ntp "-Dtest=RuntimeConfigurationIntegrationTest,RuntimeConfigurationRecoveryIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,HttpExecutorLifecycleIntegrationTest" test`.

### Phase 4: Browser, remote authoring and current guidance

- [x] `static/management/assets/editor.js`, `console.js`,
  `ManagementPagesController.java`: remove route state/editor/inspection, retain
  skill/execution YAML and ownership/local-unsaved-state behavior; show JAVA kind.
  Retain authored YAML/executor provenance only from actual available data.
  Validation/readiness messages must describe Framework/management acceptance,
  not promise HTTP validity or reachability. Explain that HTTP-specific errors
  are discovered on invocation; do not implement a separate HTTP preflight action.
- [x] Update browser editor/history/import tests, management HTTP/PAT/CSRF tests,
  and file-mode mutation/inspection tests to the new representation.
- [x] `README.md`, `docs/`, `examples/quickstart/`, `examples/remote/`, configuration
  templates, packaged `agent-skills/loomspan-sidecar-authoring/` and Python checks:
  remove routes files and obsolete classifications, supply executable operations
  and shared HTTP targets, credentials, pointers/body/error/transport/mode guidance.
- [x] Reconcile `AGENTS.md` and `ai/thoughts/design-lens.md` to the authorized
  annotated executor, maintaining the closed API boundary.
- [x] Verification: `./mvnw.cmd -B -ntp "-Dtest=ManagementEditorBrowserIntegrationTest,ManagementHistoryBrowserIntegrationTest,ManagementImportBrowserIntegrationTest,ManagementConfigurationHttpIntegrationTest,FileModeManagementHttpIntegrationTest" test` and the Python commands in the testing plan.

### Phase 5: Consolidated assurance

- [x] Run `./mvnw.cmd -B -ntp verify`, Python guidance/client checks and architecture
  enforcement against the installed snapshot. Record exact failures and downstream
  ownership for any ticket-permitted intermediate blocker; never call blocked
  checks passes or disable tests. No hosted CI before framework publication.
- [x] Supply implementation verification artifact mapping every criterion to real
  evidence, preserving the reset decision and any residual limitation.
- [ ] Fresh independent review owns final disposition; no commit/push/release.

## Test Strategy and Phase Success Criteria

Use strict candidate/scanner unit tests, fresh temporary SQLite/ZIP fixtures,
Boot plus loopback HTTP for startup/publication/restore/capture, and existing
Playwright browsers for authoring. Phase 1 succeeds when route-free exact-text
and credential round trips and old-format rejection pass. Phase 2 requires
credential separation and HTTP-invalid/Framework-valid candidate acceptance.
Phase 3 requires restored invocation, old/new capture, correlation races and
single-deadline lifecycle tests. Phase 4 requires browser/remote and guidance
checks. Phase 5 runs the full safe suite or precisely records ticket-permitted
independent intermediate blockers. The testing plan supplies detailed cases and
commands. Optional developer checks for every phase: none required. No live
destination or developer database reset is part of verification.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| File and DB startup; runtime HTTP validation; no inheritance | Runtime service, mode binding, invocation validator | HTTP-invalid/Framework-valid startup, publication and restore succeed; invocation fails before dispatch; omissions never inherit |
| Coherent Framework-validated publication | Effective candidate and public prepare/publish | Framework-invalid candidates rejected; HTTP-invalid candidates accepted; no probes; injected publish failure |
| Ownership, lease/revision, permissions, CSRF/scope and approval | Existing editing/admission paths with new payload | Management/PAT/browser/import/rollback negative and concurrency suites |
| Selected restore/credential semantics; client-independent ID | Snapshot store, effective candidate, correlation registry | Same/wrong key restart, exact replacement, model-only correlation |
| Captured old/new settings and shutdown | Captured Framework maps, existing shared client | Latch-controlled publication/handoff, old/new requests, queue discard and deadline |
| Same browser/remote representation; portable round trip | Shared Save payload and format 4 bundle | Browser publish/import, HTTP remote authoring, ZIP ciphertext/leak checks |
| Obsolete contracts removed; reset recorded | Direct schema/API/bundle replacement | Fresh schema tests, old bundle rejection, obsolete-token/source inventory |
| Current docs/guidance/constraints | Docs, examples, packaged guidance, AGENTS/lens | Python package/deployment contracts and example validation |
| Unit-owned verification and exact blockers | Verification artifact and unchanged enabled tests | Focused suites, full verify, fresh independent review |

## Implementation adaptations

- The registered executor requires an explicit selected `http` map for every
  Framework catalog, even empty/model-only ones. Bootstrap and complete examples
  therefore include that map; publication never repairs omitted authored maps.
- Key decoding is deferred until cipher encrypt/decrypt. File mode can start with
  unused malformed key material; fixed HTTP invocation rejects it before dispatch.
  Database restoration still authenticates every retained record and fails safely.
- Captured effective YAML includes ciphertext and is excluded from JSON inspection;
  authored YAML remains the management/export representation. Credential ordering
  is canonical by identifier. Restoration compares parsed complete effective content
  (including ciphertext), ignoring YAML mapping order, which can differ across JVM
  serialization of immutable maps. It never merges or repairs omitted values.

## Risks and Rollback/Recovery

No public capability is pending for HTTP startup validation. The accepted limitation
is that readiness/publication do not establish HTTP operational validity; precise
invocation failures and explicit UI/documentation wording communicate that boundary. Failed publication retains the
draft and previous active configuration; durable selection reversion/fault behavior
remains. Development reset recovery is from developer-controlled backups of the
whole stopped SQLite store with its matching external key, not schema migration.
Optional configured deployment checks remain nonblocking and require separate
authorization; no live services are needed for ordinary local tests.

## References

- Ticket, research and design lens above.
- `ai/thoughts/phases/http-skill-executor-design-notes.md`
- Existing matching Framework public APIs; prior loader findings explain why the
  superseded early-validation requirement was reconsidered, not a new API task.
- Testing plan: `ai/thoughts/plans/2026-11-02-pr-9-2-http-configuration-management-and-authoring-testing.md`
