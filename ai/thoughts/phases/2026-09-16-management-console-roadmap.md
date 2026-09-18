# Sidecar management console and configuration snapshots roadmap

Date: 2026-09-16

Last handoff update: 2026-09-18. Phases 1 through 3 are complete, with PR 1.3.3
completion reported by the developer. Phase 4 is next; its quiet automatic
validation, automatic session-draft saving and console scope are selected below.
Phase 3 selects email-address usernames and email-only forgotten-password
recovery for SQLite-backed Spring Security management accounts. Execution JWT
authentication stays separate. Passwords require uppercase and lowercase letters,
at least one number and at least one special character; no common-password or
compromised-password checking is included. Public framework `validate()` is delivered
and checked against the updated local snapshot. The candidate metadata follow-up
is also delivered and checked; the framework contract now covers Sidecar's REST
route-validation needs without preparation during editor checks.

Earlier handoff updates included delivered framework contracts, revised
restart bootstrap, the agreed SQLite/Flyway/Spring JDBC storage stack, and v1
database-first publication, startup, history, accounts, editing defaults, bundle
compatibility and the beta 5 release scope. Review clarifications record editor
import safeguards, unsanitized v1 exports, draft invalidation after successful
publication, allowlisted environment variables for REST base URLs, unreverted-publication fault behavior, and
the unreleased development policy. The admitted-invocation generation accessor
was subsequently delivered and checked in the installed beta 5 snapshot.
The latest review also settled non-cancellable updates, refreshed destructive
confirmation, failed snapshot status, fresh local import/rollback identities, and
separate validation and publication preparation.

Status: Phases 1 through 3 complete; Phase 4 ready for ticket preparation.
This document is not a set of implementation tickets or authorization to run them.

## Outcome

An embedded console manages its own Sidecar instance. Users create, update and
remove skills and REST configuration in session-owned drafts, validate the complete
candidate, and publish it without restarting Sidecar. Published configuration can
be backed up, restored, and transferred as a unit. V1 transfers authored
configuration as-is, preserving URL placeholders that resolve against each
destination's explicitly allowlisted environment variables.

Keep one roadmap rather than separate phase documents that duplicate decisions. Split out
phase detail only when it becomes useful. Unresolved product decisions below
block affected tickets, not this roadmap.

## Development policy: break and rebuild

Sidecar is in development with no releases or deployed-version compatibility
obligations. The beta labels in this document are development milestones, not
evidence of shipped versions. Destructive changes are welcome when they simplify
the intended design. Replace obsolete code, configuration and schemas directly;
do not add compatibility shims, deprecated paths, legacy readers, conversion tools
or migration/adoption workflows to preserve the current development implementation.
Development databases and configuration may be discarded and recreated.

All six phases will be completed before a working Sidecar deployment is expected.
Intermediate phase commits may break existing functionality, application startup
or even the build while later phases complete the replacement. Do not retain old
implementations or add temporary bridges solely to keep those commits deployable.
Tickets still own their tests and documentation; identify checks blocked by later
phases and complete them when those dependencies land. The integrated result must
pass its required checks before release. Flyway and bundle versioning serve the
new design; they do not create compatibility obligations for discarded development
states. Apply the design lens's simplicity and technical-debt rule throughout.

## Resume here in a new context

Current handoff, 2026-09-18: the developer reports PR 1.3.3 complete, finishing
Phase 3. Next prepare Phase 4 tickets under PR 1.4.x for the embedded console,
account screens, authoring/lease workflow and publication history. The developer
selected low-key automatic validation after an editing pause and automatic saving
to the session draft; see Phase 4 and the agreed editor behavior below. Frontend
tooling, editor component, precise layouts and timing remain implementation-planning
choices. This roadmap update does not rerun Phase 3 verification or start a pipeline.

Historical pre-Phase-3 handoff: framework validation and the candidate-metadata follow-up
are delivered and available in the installed snapshot. The earlier 21 targeted
Sidecar regressions passed against the initial validation API. A subsequent direct
public-API smoke check passed against the metadata extension, including immutable
candidate names/kinds, invalid/empty results and unchanged active state. The framework
dependency is now sufficient for Phase 3 planning. Sidecar still uses prepare-based
draft validation; adapting it and its route checker remains implementation work.
Earlier Phase 2 descriptions record the prior implementation. See the validation
handoff below for details.

Phase 3 decisions were settled for ticket preparation. The developer accepted emailed
initial-password links, immutable email usernames, deployment-configured SMTP and
15-character minimum passwords with the selected composition rules. SMTP belongs
in application YAML. The following sequential Full-profile tickets are now complete
per the developer's handoff:

1. [PR 1.3.1 — Management accounts and email recovery](../tickets/2026-09-18-pr-1.3.1-management-accounts-and-email-recovery.md).
2. [PR 1.3.2 — Session drafts and editing leases](../tickets/2026-09-18-pr-1.3.2-session-drafts-and-editing-leases.md).
3. [PR 1.3.3 — Protected configuration management](../tickets/2026-09-18-pr-1.3.3-protected-configuration-management.md).

First read the [Sidecar design lens](../design-lens.md). Keep its principles in
context throughout our discussion, ticket preparation, planning, implementation
and review: choose the simplest solution that covers the requirements, avoid
technical debt, and welcome destructive development changes without compatibility
shims. Apply these principles when refining this roadmap as well as implementing it.

The work is at product/architecture refinement, not Sidecar implementation. On
2026-09-17 the developer reported both framework enhancements complete. The matching
local checkout now exposes `prepare(Collection<SkillDocument>)` and
`onGenerationRetired(Consumer<String>)`. These contracts were checked against the
public API and framework reload documentation; no Sidecar integration evidence is
claimed. The Sidecar development version is now `1.0.0-beta.5-SNAPSHOT`;
the framework dependency is the developer-installed beta 5 snapshot, confirmed by
the developer on 2026-09-17. No feature
implementation, pipeline execution or publication is claimed by this roadmap update.

The framework subsequently delivered `AdmittedSkillInvocation.generationId()`.
Its implementation, public documentation and installed snapshot API were checked
on 2026-09-17. Sidecar can use the captured ID directly for snapshot correlation;
no additional handoff/publication synchronization is needed solely to discover it.
This establishes the framework contract, not implemented Sidecar correlation.

After the design lens, read Agreed scope, then Delivered loomspan-framework contracts and the proposal
sections below. Do not reopen settled session ownership, snapshot invalidation,
destructive import, or management-only bootstrap locking. In particular, the old
one-shared-draft proposal was rejected: users must never inherit another user's
unpublished changes or responsibility for them.

The framework accepts application-supplied skill content, including resubmission
after restart, but does not defer its ordinary configured startup activation.
Sidecar must gate execution during startup restore and staging. SQLite is now selected as
the embedded default for production-capable single-instance storage, with Flyway
migrations and `NamedParameterJdbcTemplate` repositories using Spring transactions.
Publication commits the intended production snapshot in SQLite before framework
publication; restart follows that committed pointer. A failed publication attempts
to revert the pointer; failure of that revert requires operator intervention in v1.
Retain snapshot history separately from runtime generation retirement, defaulting
to ten snapshots including current production. Framework startup must be empty;
Sidecar loads/publishes the database-selected configuration before opening dispatch,
and fails startup on any load/activation error. All six phases target beta 5.
Next, prepare settled phase tickets using `PR 1.<phase>.<sequence>` identifiers.
V1 supports allowlisted environment variables for complete REST base URLs;
automatic URL rewriting and console-managed destination bindings remain outside v1.

Use the developer-maintained installed snapshot and matching local checkout workflow.
Dependent implementation must supply affected Sidecar integration checks, including
restart gating and retirement.

## Agreed scope

- Ship SQLite as the embedded default for durable managed configuration, suitable
  for single-instance production use without a separate database process. Provide
  writable persistent storage and verified recovery/backup procedures. External
  database support remains a separate scope decision.
- Use Flyway for schema creation, versioned DDL, required reference data and
  upgrade-related data transformations. Use Spring JDBC's
  `NamedParameterJdbcTemplate` with small repository classes and Spring transactions
  for runtime persistence. Hibernate/JPA is not part of the selected stack.
- Support all framework-supported model-backed and REST YAML skill types and
  all aspects of their declarations. Exclude Java skill authoring and hosting.
- Use a full YAML text editor for v1, preserving the complete supported authoring
  surface. Structured skill editing is deferred.
- Start the framework with no skills, then load, prepare and publish the complete
  current database snapshot with matching REST resources before opening the queue
  for execution requests. Log errors and fail startup if loading or activation fails.
- Use SQLite-backed Spring Security local accounts with hierarchical management
  roles `viewer`, `editor` and `admin`, as detailed below. Keep execution JWT
  authentication separate.
- Run the console inside Sidecar and manage only that instance. Central management
  of multiple instances is outside this feature set.
- Maintain session-owned edits with one active editing lease for the instance.
  Users start from the current published snapshot, never another user's edits.
  Logout or changing browsers loses the edits; cross-session recovery is deferred.
  Lease expiry alone preserves edits within the session. Resumption requires an
  unchanged runtime-published base snapshot ID and reacquisition of the lease. A changed runtime snapshot
  invalidates and clears old edits. No cross-user draft revision is required.
- Protect editing with an exclusive lease with expiry. Automatically
  attempt renewal while the user is still working. Enforce ownership server-side.
- Validate before publication using the framework's prepare/publish API. Skill
  and REST configuration activation must not require a restart.
- Commit the intended production snapshot pointer in SQLite immediately before
  framework publication, after complete validation and REST resource staging.
  Restart activates the database-selected snapshot even if publication did not
  finish before a crash. On publication failure, attempt to restore the prior
  pointer; if that also fails, require operator recovery in v1.
- Retain immutable configuration history for rollback and historical debugging.
  Database history cleanup is separate from framework generation retirement;
  default to the latest ten stored snapshots, including failed attempts and current
  production, configurable under `loomspan-sidecar`. Correlate Sidecar diagnostics
  with durable snapshot IDs.
- After framework publication succeeds, invalidate drafts based on the prior
  runtime-published snapshot and cancel editing leases. Committing or reverting the
  intended SQLite pointer alone does not invalidate drafts or cancel leases.
  Failed ordinary publication preserves edits and leases subject to their normal
  lifecycle; destructive import/rollback cutover remains the exception. Show
  validation/publication errors to the user.
- Once an authorized update starts with frozen content, run it to completion of
  its success or failure handling. No user, session, lease or subsequent management
  action can cancel it. This does not promise success despite operational failure
  or process interruption; the existing shutdown and recovery policies still apply.
- Import/restore validates the complete package and candidate before destructive
  cutover: break leases, delete drafts and notify users that they must start over.
  Validation failure leaves editing sessions untouched. Once cutover begins, drafts
  remain discarded even if activation subsequently fails.
- Editors may import or roll back even when doing so breaks another user's lease. Before
  destructive cutover, the UI must clearly warn that drafts will be discarded,
  identify the current lease owner when present, and require explicit confirmation.
  If the specific editing lease grant (or absence of a lease) or runtime-published
  snapshot changes before update acceptance, reject
  the stale confirmation and require refreshed confirmation before starting the update.
  Every replacement lease requires fresh confirmation, including one granted to
  the same user, session or tab; renewal of the existing grant is not replacement.
  This is an intentional exception to admin-only explicit lease takeover.
- Keep one fixed RestSkillHandler bean. Select the matching route configuration
  for each captured invocation generation; do not replace the bean.
- Managed configuration currently means skill declarations and the REST routes
  and target configuration needed by that handler. Arbitrary Spring settings,
  model-connection reload, and identity-provider configuration are not included.
- Skill definitions and REST route mappings travel together in snapshots.
- V1 exports/backups preserve authored configuration without secret detection,
  redaction or encryption. Sensitive values entered into configuration are included.
  Backup encryption is a possible future feature, outside v1.
- Support multiple deployment-declared URL variables through
  `loomspan-sidecar.url-variables`. REST `base-url` may be a literal URL or a whole-value
  `${NAME}` reference to an allowlisted, nonblank environment variable whose value
  passes REST URL validation. Export/import preserves authored placeholders and
  resolves them against the destination environment before activation. The allowlist
  and environment values are deployment configuration, outside managed snapshots
  and configuration bundles. No automatic URL rewriting, destination-URL preservation
  or console-managed destination bindings are included in v1.
- Require an environment-supplied, unique one-time setup credential for initial
  administrator creation. On an uninitialized instance with no usable credential,
  lock management access and explain the condition in logs and the console landing
  page without exposing the credential. Consume bootstrap access after initial
  administrator creation; an initialized installation must not require the variable
  for normal login or reopen bootstrap just because the variable remains set.
- A management lock must not disable the existing authenticated execution API.

## Current foundation and constraints

Sidecar currently loads skills, routes and HTTP clients at startup. Its catalog
controller holds the injected startup SkillCatalog, and its REST handler uses a
single immutable startup route configuration. The container uses a read-only
configuration mount. Browser login, authoring storage, management APIs and an
embedded authoring UI still need implementation.

The matching local framework provides `SkillReloader.prepare()`,
`prepare(Collection<SkillDocument>)`, `publish()`, `snapshot()` and
`onGenerationRetired(Consumer<String>)`. Preparation freezes and validates a
complete candidate; publication does not reread files. The application must
stabilize source files during directory-based prepare, or supply complete named YAML
content, and stage external configuration before publication. The injected SkillCatalog
remains a startup snapshot. Framework-admitted invocation trees retain their
captured generation; REST invocations carry its ID.

Preparation validates configuration, not future model outcomes or remote service
availability. Publication can reject stale candidates or shutdown-time updates.
The framework supplies retirement notification for superseded published generations
after captured preparation, pending admission and physical invocation work release
them. Sidecar owns cleanup of matching routes and clients. It supplies no storage
transaction or durable notification delivery. Its generation IDs are process-local,
not durable exported snapshot identities.

Use only supported ai.loomspan.api contracts. Missing capabilities must be resolved
through deliberate framework planning, never internal imports or bean replacement.
This roadmap plans the evolution of the design lens's startup-only rule; it does
not claim reload is already implemented. Preserve the existing shutdown budget,
trusted execution identity, and framework execution authority.

All six management-console phases target Sidecar
`1.0.0-beta.5-SNAPSHOT`; beta 4 release evidence is not implied by this scope change.

## Phase 1: Configuration storage and snapshot foundation

**Outcome:** Establish a durable representation of the active configuration,
session-owned edits, and complete snapshots that support authoring and backup/restore.

Candidate ticket groups:

- Define the managed configuration boundary, stable snapshot identity and format
  version, and separation between durable snapshot IDs and runtime generation IDs.
- Implement SQLite persistence through Spring JDBC repositories, Flyway schema
  migrations and the draft lifecycle. Start new databases empty. Do not adopt or
  supply a migration path for the old mounted-skills/routes development setup.
- Implement restart loading of the database-selected intended production snapshot
  and retained immutable history, using the agreed database-first publication policy.

**Exit evidence:** Draft edits do not change active execution; complete snapshots
can be recovered without mixing skill and route versions; restart and interrupted
write tests demonstrate the agreed recovery behavior. Migration and repository
tests use real SQLite, covering fresh database creation and any explicitly supported
schema upgrades. Discarded development schemas need no upgrade path. Storage and deployment
requirements are documented in this phase.

**Ticket preparation:** Specify SQLite deployment details and the snapshot/bundle
data contract. Preserve authored URL placeholders; deployment URL-variable declarations
remain outside the snapshot/bundle schema.
Apply the startup, retention and session policies below.

## Phase 2: Validated runtime publication and REST generation lifecycle

**Outcome:** Activate a complete validated skill and REST configuration snapshot
without restarting, while protecting work using the previous generation.

Candidate ticket groups:

- Coordinate immutable candidate content, framework preparation, candidate route
  validation and HTTP client preparation, and publication. An edit after validation
  must require validation of the new candidate before it can be published.
  Validate records a result for immutable authored content and releases temporary
  resources. Explicit Publish prepares and stages that content again; it does not
  retain a prepared generation or clients while the user considers publication.
  A prior validation success does not guarantee publication success.
  The server must reject ordinary Publish unless the exact frozen draft content
  has a successful validation result; this is not merely a disabled UI button.
  Import/rollback follows its own required validation-before-cutover flow.
- Validate literal REST base URLs and allowlisted whole-URL environment references
  during startup and candidate staging, using the URL-variable policy below.
- Use current catalog snapshots for discovery and generation-specific configuration
  in the single REST handler. Stage initial configuration before admitting work.
- Coordinate execution handoff, old configuration/client retention and retirement,
  failed candidate cleanup, and shutdown through the supported public contracts.

**Exit evidence:** Real Sidecar integration tests demonstrate additions, updates,
deletions, invalid candidates, route-only changes, and old/new nested REST work
using matching configurations. Rejected updates preserve the active configuration;
recovery tests cover agreed failure points between storage and runtime publication.
No success claim is inferred solely from framework tests.

Retain framework generation capture at Sidecar's existing worker handoff; no new
Sidecar policy to pin a generation at HTTP acceptance is requested.

Serialize update attempts from authorization/base checks and content freezing
through preparation, staging, publication and success/failure handling. An update
starts only after these admission checks succeed. A waiting request has not started
and must be checked when its turn arrives. Once started, lease expiry, takeover,
logout, session expiry, account disable, role change, browser disconnect and later
edits cannot cancel or change its frozen content. Do not provide a cancel operation.
Operational errors still follow the failure policy; do not add an extra shutdown
drain budget or promise completion across a process crash.

Other sessions may edit A while B is being prepared. Draft bases always refer to
the runtime-published snapshot, never the intended SQLite pointer. Successful
publication of B invalidates A-based drafts and cancels leases; failed publication
does not. Coordinate draft/lease operations with the short publish-and-invalidate
section so no stale draft or lease escapes invalidation. This does not pause
execution dispatch. No transaction spanning SQLite and the framework, cross-user
draft revision scheme or lease-lifetime extension is required.

**Ticket preparation:** Specify runtime resource cleanup bounds and the operator
recovery procedure. Apply the agreed database-first publication and fault policies
below, keeping dispatch behavior consistent with the single admission owner.

## Phase 3: Management identity, APIs and exclusive draft editing

**Outcome:** Authorized users can manage the instance through protected APIs,
with first-administrator setup and exclusive ownership of active editing.

Candidate ticket groups:

- Implement environment-controlled first-user setup, locked landing state,
  persistent initialization state, login/logout and agreed account administration.
- Expose protected management operations for draft authoring, validation,
  publication and snapshot inspection, separate from execution authorization.
- Implement lease acquisition, renewal during user activity, expiry and stale-write
  rejection across users and browser tabs. Define release and recovery actions.

**Exit evidence:** Missing setup credentials lock management only; bootstrap cannot
be reused after initialization; unauthorized management calls fail. Concurrent-user
tests prove exclusivity, expiry, renewal and rejection of writes from a former owner.

**Ticket preparation:** Apply the account, permission, session and editing defaults
below. Specify email-based forgotten-password recovery, deployment email delivery,
setup-token validation and public management API contracts without adding external
identity providers in v1. No offline administrator account recovery is included.

## Phase 4: Embedded authoring and publication console

**Outcome:** Users can complete the supported authoring workflow in the browser.

Candidate ticket groups:

- Build the embedded console shell, initial setup/login flow and current published
  configuration views. Choose frontend tooling during implementation planning.
- Include logout, emailed initial-password setup, forgotten-password recovery,
  password change and account administration screens using the Phase 3 contracts.
- Deliver complete skill editing and REST route/target editing with useful
  explanations and validation feedback. Preserve the full supported YAML surface.
- Automatically save changes to the owning session's draft, with subtle lower-left
  status-bar feedback such as "Saving..." and "Saved". Saving does not publish or
  provide cross-session recovery; failures must not be presented as saved changes.
- Automatically validate the complete draft after a pause in editing. Above the
  editor, show a compact green "Valid" or red "Validation errors" indicator with
  expandable details. Keep automatic feedback unobtrusive: no interrupting dialogs
  or automatically expanded error lists. Use text as well as color and quiet
  checking/out-of-date states so old results never imply that new edits are valid.
- Display draft ownership, renewal/loss of lease, changed configuration, validation
  state and publication results. Preserve unsaved editor content on lease loss
  according to the agreed UX, without allowing stale writes.
- Provide a publication-history screen showing retained submitted snapshots and
  their recorded status, with the current runtime-published snapshot identified.
  Users unsure whether publication completed can inspect this history after
  reconnecting or logging in again. Pending status remains outcome-unknown;
  no separate operation-status service or durable job system is required in v1.

**Exit evidence:** Browser-level workflows cover creating, modifying and removing
skills and routes, validation failures, successful publication without restart,
and two users contending for the draft. The published catalog reflects the update.

**Ticket preparation:** Use the full YAML editor and the editing policy below.
Present source-labelled validation errors and a clear publication result; frontend
tooling and precise layouts remain implementation choices.
The developer accepted this console scope and its grouping into shell/account
flows, authoring/lease/publication workflow and history. Refine these into
independently verifiable PR 1.4.x tickets rather than treating the groups as fixed
ticket counts. Export/import and rollback remain Phase 5 work.

## Phase 5: Backup, restore and configuration transfer

**Outcome:** Export and recover complete managed configuration, and move it between
instances as-is through a deliberate, validated workflow. Authored URL placeholders
resolve using the destination's allowlist and environment values.

Candidate ticket groups:

- Export complete snapshots with format/version metadata and integrity checks;
  validate imported package structure and compatibility.
- Implement destructive import/restore: replace managed configuration, break
  leases, delete session drafts and notify users. Validate destination requirements
  and define the failure boundary before destructive cutover.
  The confirmation UI must show the disruption and current lease owner's identity,
  including when an editor imports; refresh that information before confirmation.
  Reject stale confirmation if the specific lease grant (or absence of a lease)
  or the runtime-published snapshot
  changed before the update starts; refresh and reconfirm without discarding drafts.
- Restore an earlier configuration through the same validated activation path;
  define backup scope, retention and supported recovery procedures.

**Exit evidence:** Round-trip and two-instance tests preserve skill definitions
and route mappings together; malformed/incompatible bundles cannot partially alter
active configuration; restore and destination validation work without restarting
for supported managed configuration. Imported target configuration matches the
bundle as authored. Two-instance tests prove that the same placeholder resolves to
each destination's URL, and missing, undeclared, blank or invalid URL bindings fail
before destructive cutover.

**Ticket preparation:** Apply the versioned bundle, authored-content and destructive
cutover and URL-variable policies below. Reuse the Phase 1 snapshot contract;
automatic URL rewriting and console-managed destination bindings remain outside v1.

## Phase 6: Deployment and end-to-end readiness

**Outcome:** Operate the complete feature with durable storage, clear recovery
procedures and verified interaction with Sidecar's existing execution service.

Candidate ticket groups:

- Finish container/deployment examples for writable persistent state, setup
  credentials, URL-variable allowlists and environment values, and console access,
  preserving non-root operation.
- Exercise publication, concurrent editing, backup/restore, process interruption
  and shutdown together through the real Sidecar application.
- Complete operator guidance and phase-specific acceptance evidence for the beta 5 target.

**Exit evidence:** Deployment exercises demonstrate persistent state, console-lock
isolation, recovery and the complete author/export/import/publish workflow. Existing
execution and shutdown contracts remain covered. Release checks follow the
published-framework dependency policy.

Tests and documentation belong to every implementation ticket. This phase covers
cross-feature verification and packaging, not deferred correctness or documentation.

## Delivered loomspan-framework contracts

The reload enhancements and subsequent admitted-invocation generation accessor
were reported complete by the developer on 2026-09-17 and their public contracts
were verified in the matching local checkout. This establishes
framework capability, not completed Sidecar behavior. Use only these supported
contracts; do not compensate with internal imports or framework bean replacement.

| Capability | Delivered contract | Sidecar integration required |
| --- | --- | --- |
| Safe retirement notification | `onGenerationRetired(Consumer<String>)` reports safe retirement of a superseded published runtime generation. Sidecar still owns resource cleanup. | Phase 2 resource retirement and shutdown |
| Application-supplied skills | `prepare(Collection<SkillDocument>)` accepts a complete named YAML replacement set. Configured startup activation still occurs; restore uses prepare/stage/publish behind a Sidecar execution gate. | Phase 1 storage integration and Phase 2 preparation/bootstrap |
| Captured invocation generation | `AdmittedSkillInvocation.generationId()` returns the immutable process-local generation ID captured during preparation, before input conversion/validation. Reads do not retain ownership; the ID remains available after execution, release or cutoff. | Phase 2 generation-to-snapshot correlation at worker handoff |

Original ticket identifiers (historical references; these files were not found in
either checkout's `ai/thoughts` tree on 2026-09-17):

- `2026-09-16-framework-generation-retirement-notification.md`
- `2026-09-16-framework-application-supplied-skill-lifecycle.md`

Integration constraints from the delivered contract:

- `SkillDocument(sourceName, yaml)` uses a diagnostic label, not a file path or
  callable skill name. Labels must be nonblank and exactly unique; documents and
  YAML must be non-null. Preparation copies/freezes the supplied content. Empty
  content removes all YAML skills. No-argument preparation still uses configured
  locations; Sidecar's stored-snapshot path must deliberately use supplied content.
- Register retirement before staging initial resources. A callback can occur before
  `publish` returns, can run concurrently, and must return promptly. Schedule slow
  cleanup on a Sidecar-owned executor. Notification has no replay, ordering, retry
  or durable delivery guarantee; it does not retire a durable backup snapshot.
- Never-published candidates require explicit cleanup. A rejected repeated publish
  does not authorize deleting resources for an already published generation.
- Shutdown may omit notifications, including for the active generation, and does
  not wait for cleanup. Preserve the existing framework shutdown budget and keep
  application resources alive until completion/cutoff, then clean up remaining
  resources. Closing registration does not wait for already selected callbacks.

### Storage decision — embedded SQLite

On 2026-09-17 the developer favored an out-of-the-box production-capable database
without a separate running process; use embedded SQLite for skills and REST handler
configuration. Its engine runs within Sidecar through JDBC. This is the production
storage direction, not a temporary development-only database. The framework owns
neither Sidecar's schema nor its database connections; supply stored YAML through
the delivered `SkillDocument` API.

SQLite is embedded local persistence; supporting an external database is a
separate scope decision. Database transactions do not atomically commit the JVM's
active framework generation; the database-first policy below defines that boundary.
Database backup and configuration export are distinct: the latter selects the
runtime-published snapshot and preserves its authored content and bundle contract. V1 resolves
authored URL placeholders against the destination deployment without rewriting them.

The initial supported deployment is one Sidecar instance owning its database on a
writable persistent volume with reliable local filesystem locking. Do not share
the database file among replicas over a network filesystem. Production readiness
must verify transactional writes, contention handling, restart/crash recovery,
explicitly supported schema upgrades and consistent backup/restore on the supported
deployment. Exact
journal/durability settings and migration scripts belong in implementation planning.
SQLite remains local file-backed storage; it is not itself a redundant remote
database. A supported external engine such as
PostgreSQL would need explicit scope, migrations and verification, not merely an
arbitrary JDBC URL. Keep storage operations contained without speculative multi-engine
infrastructure. Store original YAML content so the authoring surface stays complete.

On 2026-09-17 the developer agreed to Flyway and Spring JDBC:

- Flyway owns initial schema creation, versioned schema changes, required reference
  data and upgrade-related data transformations, including migration history and
  validation. Complete migrations before loading stored configuration or accepting
  management writes. Use one migration runner for the instance; Flyway's SQLite
  support does not allow concurrent migration runners.
- Application services own user-created skills, snapshots, publication state and
  first-administrator setup. These runtime operations are not migration seed data.
- Small repository classes use `NamedParameterJdbcTemplate` and Spring transactions
  with explicit SQL and transaction boundaries. The expected persistence operations
  do not justify Hibernate entity tracking, cascading or lazy loading; SQLite also
  requires Hibernate's separately maintained community dialect.
- Verify migrations and repositories against real SQLite, including fresh installs
  and any explicitly supported upgrades preserving existing data. No preservation
  of discarded development databases is required. Neither Flyway nor Spring JDBC
  makes database commits atomic with framework publication; use the database-first
  policy below rather than introducing a distributed transaction.

For configuration transfer, use a versioned bundle containing snapshot metadata,
YAML and REST configuration, not SQL dumps. Exclude accounts, sessions and leases.
Preserve authored configuration, including any literal sensitive values; v1 does
not determine what is secret, require secret-reference conversion, or sanitize
exports. Existing references remain references rather than being replaced with
resolved values in stored/exported content; validate destination bindings as needed.
Full database backup serves installation recovery and is a separate operator
procedure, not a configuration-import feature. V1 adds no backup encryption. Possible
future encryption does not justify additional v1 machinery.

### Agreed REST URL-variable policy

Deployment application YAML declares the environment variables permitted for REST
base-URL substitution. Multiple declarations are supported:

```yaml
loomspan-sidecar:
  url-variables:
    - CUSTOMER_API_URL
    - EXPENSE_API_URL
```

REST configuration uses the same variable name as a whole base URL, for example
`base-url: ${CUSTOMER_API_URL}`. The deployment supplies
`CUSTOMER_API_URL=https://customers.example.com`. Literal base URLs remain supported.
V1 URL references use only the whole-value `${NAME}` form; URL fragments, composed
templates and placeholder defaults are not part of this feature.

For each referenced URL variable, require an exact allowlist entry, an existing
nonblank process environment value, and a resolved value satisfying the normal REST
base-URL rules. Resolve URL variables from the process environment rather than
arbitrary Spring property sources. An absent or empty allowlist permits literal
URLs only. Unused declarations do not require environment values. This restriction
applies to `base-url`; existing placeholder handling in other REST fields is unchanged.

Validate these requirements during startup and candidate staging, including import
and rollback, before changing production or performing destructive cutover. Preserve
the authored placeholder in storage and exports; never replace it with the resolved
URL. The destination supplies its own allowlist and environment values. Neither is
included in managed snapshots or configuration exports, and the console does not
edit deployment bindings.

Changing deployment allowlists or process environment values requires restart.
Editing a REST target to select another already-declared and available variable uses
normal validation/publication without restart. Document the planned
`loomspan-sidecar.url-variables` setting and verify allowed references, undeclared
references, missing/blank values, invalid URLs, literal URLs and export/import
preservation. This setting is not yet implemented.

### Bundle compatibility and rollback

Use an independent integer `formatVersion`, initially `1`, plus informational
producer application/framework versions. Do not derive bundle compatibility from
the application minor version or Flyway schema version. Increment the format version
when the bundle contract becomes incompatible. Each release documents and tests its
supported format versions; v1 supports format 1 and rejects unsupported versions
before changing state. No generic format-conversion framework is required in v1.
This governs the new bundle contract, not compatibility with discarded development
formats or the old mounted configuration layout.

A supported format does not guarantee that the destination has the needed model
connections, secret bindings or framework skill features. Validate the full candidate
through the destination framework and Sidecar before cutover. This avoids promising
that arbitrary newer skill declarations run on an older release merely because the
archive format is unchanged. Include representative export/import fixtures in release
checks. Exact archive structure is a ticket-level choice.

Export captures the runtime-published snapshot when the export starts and exports
that immutable content, even if another snapshot is published while export runs.
An intended SQLite snapshot that has not been activated is not the published snapshot.

Rollback copies a retained snapshot into a new candidate and uses the same validation
and activation path. Every import and rollback receives a fresh local snapshot ID,
including repeated imports of the same bundle. A source snapshot ID is provenance
only and never replaces or overwrites a local identity. Editors may roll back
without owning the current editing lease,
using the same destructive confirmation as import. Validate import and rollback
before breaking leases and deleting drafts, serialize
cutover with ordinary publication, and notify invalidated sessions through their next
management response or normal status refresh; no push-notification subsystem is needed.
Editors are deliberately authorized to perform disruptive imports and rollbacks without an admin
takeover. The UI must identify the current lease owner and explain the loss of
drafts before the user explicitly confirms destructive cutover.
Bind confirmation to the specific editing lease grant (or absence of a lease)
behind the displayed ownership and to the runtime-published snapshot. A replacement
grant requires refreshed confirmation even for the same user, session or tab;
renewing the existing grant does not. Treat all new lease grants alike.
Recheck both under serialization before starting the update. If either changed,
return a conflict and require refreshed confirmation; leave drafts untouched.
Once accepted, the update is non-cancellable. For import/rollback, defer competing
lease and draft operations from the confirmation check through completion so no
new editing lease can be granted before the update finishes. Normal session/lease
expiry does not cancel an accepted update. Finish preparation/staging before
discarding drafts; validation failure leaves editing state untouched, subject to
normal session/lease expiry. Execution dispatch continues throughout.

### Agreed v1 publication and history policy

Favor the simplest implementation that meets current requirements. Do not add an
automatic reconciliation service, recovery journal or speculative multi-engine
abstractions for exceptional failures. This is an explicit v1 scope choice; revisit
it only if operational experience establishes a concrete need.

1. Serialize the complete update attempt, including restore/import activation.
   Check authorization, draft ownership and the runtime-published base snapshot,
   or import/rollback confirmation, before accepting the operation. Freeze the
   content and run the accepted update without cancellation. Prepare it afresh
   through the framework, validate matching REST configuration and stage runtime
   resources. Earlier editor validation is not a retained publishable candidate.
2. After successful preparation/staging and any destructive import cutover, in a
   SQLite transaction, persist the candidate and change one production-snapshot
   pointer from A to B, retaining A as history. If this transaction fails, do not
   publish B. Committing B alone does not invalidate drafts or cancel leases.
3. Publish the prepared B generation. After success, invalidate A-based drafts and
   cancel editing leases as part of the coordinated management transition. Record
   B as published. Report publication success only after the framework call succeeds;
   the durable pointer represents intended production, not proof of activation.
4. If publication fails, attempt to restore the pointer to A and clean up resources
   for the never-published candidate. Keep the stored B snapshot marked failed,
   rather than deleting it. Reversion does not invalidate drafts or cancel leases;
   ordinary publication failure preserves them subject to their normal lifecycle.
   Drafts already discarded by destructive import/rollback cutover remain discarded.
5. If the revert also fails, surface the failure and require operator intervention.
   Reject further managed-configuration mutations, including draft changes,
   publication, import and rollback. Clearly report that runtime execution remains
   on A while SQLite selects B. Keep configuration/status inspection available and
   continue execution on the still-active A until operator recovery. Do not report
   publication success or add automatic recovery machinery in v1. Document the
   operator recovery procedure; restart still follows the committed SQLite pointer
   and the normal fail-startup policy.

Snapshot content and identity are immutable; publication status is bookkeeping.
Persist a pending status with B, then published or failed when the outcome is known.
Pending means the outcome was not durably recorded, not proof that publication
failed. A database error can prevent recording the outcome. Surface that error;
do not undo a successful runtime publication or delete its resources because a
later status write failed. In that case execution remains on B, the pointer remains
B, and management configuration mutations stop for operator recovery. No additional
recovery journal or automatic reconciliation service is required.

On restart, load whichever snapshot the committed SQLite pointer selects. A crash
after committing B but before publication or reversion therefore selects B. This
is intentional: B was validated and selected as the intended production state.
Startup still validates and stages that configuration through the supported API.
Successful startup activation records the selected snapshot as published. Historical
pending records may remain outcome-unknown after a crash; do not infer past success
or failure. Status never overrides the committed pointer's restart authority.
No routine pause of execution dispatch is required solely to make the database
commit and publication simultaneous; existing work retains its captured generation.

Retirement callbacks release generation-specific runtime routes and resources when
safe. They do not delete stored configuration history. A separate database cleanup
policy retains the latest ten stored snapshots by default, including current
production and failed attempts. Failed snapshots remain distinguishable from
published snapshots and count toward the same limit; no separate failed-history
quota or cleanup service is needed. Failures before a candidate is persisted need
not create a history record. Submission for publication makes persisted candidate
content part of inspectable history; private session drafts remain private.
Configure the positive count with `loomspan-sidecar.snapshots.max-retained` (default
`10`). Always protect the current pointer and any snapshot still needed by an in-flight
publication or live generation. Protected snapshots count toward the retention
target; prune the oldest eligible snapshots first. Protected snapshots can exceed
the target, and excess history remains until the next pruning opportunity even
after its runtime generation retires. Run simple database pruning at startup and after completed update attempts
when the database is usable; retirement callbacks do not delete database records. No separate scheduling service
is required for v1.

Sidecar execution diagnostics/log correlation must identify the durable snapshot
actually used through the runtime-generation-to-snapshot mapping, never by reading
the current production pointer after execution. Exact placement in existing diagnostic
output belongs in the affected ticket and must use public contracts. When history
has expired, report its absence; keeping traces does not pin snapshots indefinitely.
Retained configuration enables inspection, not guaranteed replay of past model or
remote-service results.

Use the delivered `AdmittedSkillInvocation.generationId()` immediately after a
successful worker handoff to resolve and record the Sidecar snapshot ID before
invocation. The returned ID identifies the captured generation even if publication
changes the current catalog before handoff returns. It covers model-only executions
and does not depend on a completed observer callback; no `SkillExecutionView`
change is required. Durable snapshot IDs and their mapping remain Sidecar-owned.
Stage the mapping before publication and retain it with the generation's resources.
The accessor does not retain ownership or guarantee that the mapping still exists
after cutoff/release. Handle an unavailable mapping explicitly; never substitute
the current snapshot. Release an admission abandoned during lookup or recording.
Sidecar integration tests must prove correlation across publication and cleanup.

Verify the normal sequence, database-commit failure, publication failure with a
successful revert, restart between commit and publication, and visible failure when
the revert fails. In that fault state, verify that configuration mutations are
rejected, the A/B mismatch is visible, and execution continues on A. Verify draft
invalidation and lease cancellation only after successful framework publication;
ordinary publication failure and pointer reversion preserve valid drafts/leases.
Destructive import cutover remains irreversible for discarded drafts. Cover failed
status and shared retention, outcome-recording failure after successful publication,
fresh local import/rollback IDs, stale destructive confirmation, and the absence of
cancellation after update acceptance. Keep tests and operator
documentation proportional to this policy.

### Agreed restart/bootstrap flow

This is planned Sidecar behavior using the delivered public contracts; Sidecar
has not implemented it:

1. Sidecar keeps its execution queue closed from startup. Configure Loomspan's ordinary
   startup discovery to find no skills; it has no deferred activation mode. Use the
   documented `loomspan.skills.locations` setting with a controlled empty source.
   An empty list alone falls back to default scanning in the current framework;
   integration tests must prove the configured baseline is empty. No Java skills
   are hosted, and mounted skills/routes are not automatically adopted.
2. Sidecar registers retirement notification, opens its durable store, completes
   Flyway migrations and selects the recorded active snapshot. Management writes
   remain unavailable until migrations complete.
3. Sidecar calls `prepare(completeDocuments)` to validate/freeze that snapshot's
   named YAML content and obtain a fresh runtime generation ID.
4. Sidecar validates matching REST configuration and stages clients under that ID.
5. Sidecar publishes the prepared generation and only then opens execution traffic.
   The gate must protect actual dispatch, not only a readiness status endpoint.

Durable snapshot identity survives restart; runtime generation identity does not.
A genuinely new database receives an empty current configuration and exposes initial
administrator setup after successful startup. An initialized database with missing or
invalid current configuration is an error, not a new installation. On any database
load, preparation, REST staging or publication error, log actionable errors without
secrets and fail application startup. Do not open the queue, silently substitute an
empty configuration, or keep a management-only recovery server running. Recovery is
an operator procedure. Session drafts and leases do not survive process restart.

### Agreed management accounts and editing defaults

Use Spring Security form login, SQLite-backed local accounts, hashed passwords,
server-side sessions and CSRF protection. Management roles form this hierarchy:

| Role | Permissions |
| --- | --- |
| `viewer` | Read current published configuration and all retained submitted snapshots, including pending and failed content; export authored configuration as stored, including any embedded sensitive values. No draft mutation or publication. |
| `editor` | Viewer permissions plus author/validate/publish skills and REST handler configuration, import and rollback. |
| `admin` | Editor permissions plus user administration, lease takeover and administrative features beyond skill-runtime management. |

Unpublished draft content and its validation results are visible only to the owning
session, including for administrators. Other sessions may see lease ownership and
retained submitted snapshot history, but not another session's private draft or
its validation results. Once a publication candidate is persisted, that immutable
submitted content is inspectable history even if publication fails or its outcome
remains pending; the owning session's draft remains private.

These roles do not grant execution API access or override its JWT/skill authorization.
Support admin-created accounts, role changes and disable/re-enable; users can change
their own password. The username is the account's email address, used for both
management login and password-recovery email. No separate username or public
self-registration is included. Account recovery uses a forgot-password email flow
only, replacing the earlier offline recovery requirement and exclusion of email
recovery. Do not add an offline reset or an administrator-set replacement password
as an alternative recovery path. Invalidate affected sessions on password reset,
disable or role change. Prevent removal/demotion/disable of the last enabled
administrator. This decision concerns account access, not the separate full-database
backup/restore procedure for configuration/storage failures. Keep the existing
one-time environment-credential setup rule; use
`LOOMSPAN_SIDECAR_SETUP_TOKEN`, require a nonblank high-entropy operator-supplied value,
and never log or store the plaintext token. Exact validation is a ticket detail.

On 2026-09-18 the developer explicitly selected password composition rules:
require uppercase and lowercase letters, at least one number and at least one
special character. Do not implement common-password or compromised-password
checking: no bundled blocklist, breach lookup service or background checking.
This replaces the earlier recommendation to use a blocklist without composition
rules. Retain the proposed minimum of 15 characters, long-password support
(maximum at least 64 characters), spaces and password-manager paste/autofill.
No routine periodic password changes are required. Use salted adaptive password
hashing through Spring Security and login throttling. Exact hashing parameters,
length cap and character classification belong in planning.

The email recovery design must use unpredictable, expiring, single-use reset tokens,
protected token storage, rate limits and generic request responses that do not
reveal account existence. Successful reset invalidates affected sessions and does
not automatically log the user in. Follow the
[OWASP forgotten-password guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
The developer accepted the following email/account decisions on 2026-09-18:

- Admin-created accounts receive a single-use emailed link to set their own initial
  password. Reuse the password-reset mechanism; users cannot log in before completing
  password setup. The first administrator follows the same email-link flow after
  authorization with the one-time setup token, confirming mailbox control before login.
- Account email addresses are immutable in v1. Create a replacement account and
  disable the old account to change an address, preserving the last-admin safeguard.
- SMTP configuration belongs in deployment application YAML under standard
  `spring.mail.*` settings. Sender and trusted external console base URL also belong
  in application YAML, under Sidecar-owned management settings where no standard
  property applies. These are outside managed snapshots and the console editor.
- Existing login and execution remain available when email delivery is missing or
  unavailable. Email-dependent setup/recovery remains unavailable until delivery is
  restored, with safe retries and no alternative password-recovery mechanism.

Use the configured external URL for email links rather than a caller-supplied host.
Exact property names for Sidecar-owned settings, token expiry/throttling defaults,
email normalization and API representations are implementation-planning details.

The developer delegated reasonable editing defaults on 2026-09-17:

- Lease inactivity timeout: `loomspan-sidecar.management.edit-lease-timeout: 15m`.
  Renew at most every 30 seconds while meaningful editor activity occurs (typing,
  paste, clicks, scrolling, or explicit Continue editing). Mouse movement, background
  polling and merely leaving a tab open do not renew. Warn two minutes before expiry.
- Login idle timeout: `loomspan-sidecar.management.session-idle-timeout: 30m`.
  Background polling must not indefinitely keep an otherwise idle login alive.
  Logout/session expiry clears drafts and releases ownership; lease expiry alone
  retains the draft within its still-valid session.
- One writable editor tab per session. A second tab may read but cannot silently
  take over the active editor; enforce session and tab ownership server-side.
- Release editing preserves the session draft, subject to unchanged base and later
  lease reacquisition. Discard edits clears it and releases the lease. Successful
  framework publication invalidates drafts based on the prior runtime snapshot and
  cancels leases. SQLite pointer changes or reversion alone do neither. Failed
  ordinary publication preserves drafts/leases subject to their normal lifecycle;
  destructive import/rollback cutover still discards them. None of these editing
  actions cancels an already accepted update.
- Admin takeover explicitly revokes the old lease and starts from production, never
  inherits another session's edits. The former owner retains its own draft only while
  its session and base snapshot remain valid. A changed runtime-published snapshot clears
  stale drafts. Preserve local editor text on lease loss until these invalidation
  rules apply, but reject stale writes.
- Show validation errors with source labels and available locations. Require explicit
  publication after validation; edits invalidate validation. No structured editor,
  cross-session draft recovery or collaborative editing in v1.

The two management timing properties and snapshot retention count are planned YAML
settings, not yet implemented configuration. Keep renewal cadence and warning lead
time as simple UI defaults rather than exposing every timing detail as configuration.

### Remaining decisions before Sidecar tickets

The product decisions above are selected for v1, including allowlisted environment
variables for REST base URLs. Automatic URL rewriting and console-managed destination
bindings remain outside v1. Specify concrete
storage/deployment details, trace-correlation fields, the operator recovery steps,
setup-token validation and the bundle structure in their affected tickets/plans.
Do not reopen the settled publication policy to add automated recovery machinery.

Phase 3 discussion on 2026-09-18 also confirmed separate draft-save, validation and
publication effects, rejection of stale lease/draft writes, accurate distinction
between activation failure and post-activation bookkeeping failure, and activity
tracking that does not count background polling as login or editing activity.

The developer selected a separate public framework `validate()` method on
2026-09-18 and has now delivered it with the framework developer. The existing
`prepare()` leaves active skills unchanged but
advances generation identity, constructs a publishable candidate and may initialize
cached dependencies. It is not a side-effect-free validation utility; retain its
prepare/publish purpose rather than merely renaming it.

Delivered in framework commit `236cf3c`, both `SkillReloader.validate()` and
`validate(Collection<SkillDocument>)` return an immutable `SkillValidationResult`
with `valid()` and structured issues: severity, source name, optional skill name,
optional field path and message. Validation and preparation share checking logic;
validation avoids generation allocation, handler construction and publication state.
Public documentation and focused framework tests cover those guarantees. The saved
framework review reports 1,177 passing tests; this handoff did not rerun that suite
or rebuild the framework.

Local Sidecar verification against the installed snapshot passed 21 tests covering
runtime publication/recovery, REST generations, execution correlation and the public
boundary. A temporary public-API smoke check also passed valid, invalid and empty
validation, repeatable/source-labelled feedback, unchanged active state and
publication of a candidate prepared before a later validation call. These checks
do not establish integrated editor validation or Phase 3 workflow completion.

Candidate metadata follow-up reviewed on 2026-09-18: `SkillValidationResult.skills()`
now returns immutable `ValidatedSkill(name, SkillKind)` entries sorted by exact name.
A valid result includes the complete proposed YAML/REST set and fixed Java skills;
warnings preserve that metadata. Errors produce an empty list, so consumers must
check `valid()` first. Both overloads share these semantics. The implementation
derives metadata from the shared checked definitions without preparing a generation;
the public type is included in the supported-surface allowlist and documentation.
Source tests cover mixed kinds, fixed Java retention, ordering, warning-only results,
invalid results and state isolation. A direct installed-snapshot smoke check passed
candidate REST name/kind, immutable/detached metadata, invalid/empty results,
unchanged active state and publication of a previously prepared candidate.

This resolves the previously identified framework contract gap. Sidecar can adapt
`RestRouteCatalogValidator` to consume candidate names/kinds for missing, unknown and
non-REST route checks without parsing framework YAML or calling `prepare()` during
editor validation. Sidecar still owns route/target/URL validation and resource
staging; explicit publication still prepares and stages afresh. No Sidecar production
integration or full editor workflow is claimed complete by this contract review.

Agreed editor behavior, selected 2026-09-18: automatically save changes to the
session draft and validate the complete draft after a pause in editing. Coalesce
requests and apply results only to the exact candidate checked. Show a quiet,
expandable indicator above the editor: green "Valid" or red "Validation errors",
with checking/out-of-date states as needed. Details stay collapsed until requested;
automatic validation must not interrupt typing or repeatedly push errors at users.
Use subtle lower-left status-bar "Saving..." / "Saved" feedback for automatic
saves, and accurately indicate unsaved changes or save failures. A saved draft
remains session-owned and is lost on logout/session expiry under the agreed policy.
Saving, validation and publication are separate effects; publication stays explicit
and prepares/stages afresh. Automatic requests do not themselves count as login or
lease activity. Phase 3 owns API integration and Phase 4 owns automatic editor
scheduling and presentation. Exact save/validation timing belongs in implementation
planning. The preceding framework handoff checks are historical evidence, not a
replacement for the completed Phase 3 ticket's own integration evidence.

### Release and ticket sequence

All six phases ship in Sidecar `1.0.0-beta.5-SNAPSHOT` development, leading to the
beta 5 release after verification. The Sidecar version changes independently of the
framework dependency. The developer confirmed framework beta 5 was installed, so use
`1.0.0-beta.5-SNAPSHOT` until the framework release workflow authorizes its published
`1.0.0-beta.5` replacement. Hosted CI remains deferred until that release is available.

Use the developer's planning identifiers `PR 1.<phase>.<sequence>`, beginning with
`PR 1.1.1`: phase 1 uses `PR 1.1.x`, phase 2 `PR 1.2.x`, through phase 6 `PR 1.6.x`.
Keep dated ticket filenames and include the planning identifier in each ticket.
These are planning labels, not invented GitHub pull-request numbers. Create tickets
incrementally for settled outcomes; do not run their pipelines merely by recording
this sequence. The developer explicitly deferred tagging until beta 5 is release-ready.
Release tags remain immutable `v<non-SNAPSHOT-version>` and must not label unfinished
snapshot development as a completed release.

### Sources consulted during refinement

- Matching local framework public SkillReloader, SkillDocument and PreparedSkillUpdate
  APIs and skill-reload.md, rechecked 2026-09-17. Sidecar queues work
  before SkillInvocationHandoff, so capture remains at existing worker handoff.
- [Spring resource abstraction](https://docs.spring.io/spring-framework/reference/core/resources.html)
- [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
- [Spring Security JDBC account management](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/jdbc.html)
- [SQLite deployment guidance](https://www.sqlite.org/whentouse.html)
- [SQLite backup API](https://www.sqlite.org/backup.html)
- [Flyway SQLite support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/sqlite)
- [Spring JDBC approaches](https://docs.spring.io/spring-framework/reference/data-access/jdbc/choose-style.html)
- [Hibernate dialect support](https://docs.hibernate.org/stable/orm/dialect/)

## Sequence and ticket readiness

Storage precedes durable publication; publication and protected management APIs
precede the complete editor workflow; configuration transfer uses the same validation and
activation path with destructive draft invalidation. Authentication design can
proceed once the identity storage boundary is known.
These are dependency boundaries, not a requirement to finish every detail of one
phase before discussing the next or keep intermediate phases buildable/deployable.
Apply the development policy above; complete integration and deferred checks before
the first working deployment and release.

Before writing tickets:

1. Use the agreed six-phase beta 5 scope and phase-based ticket sequence.
2. Specify the material details affecting the next phase. Apply the URL-variable
   policy and transfer authored configuration as-is, preserving placeholders.
3. Refine candidate ticket groups into independently verifiable outcomes. They are
   not ticket IDs, fixed ticket counts or implementation plans.
4. Use [write_ticket](../../commands/write_ticket.md) and dated ticket filenames.
   No implementation ticket may retain material TBDs. Apply Full/Fast/Direct
   eligibility to each ticket; persisted contracts, authorization, publication and
   lease lifecycle work are expected to require the Full 5-Step Pipeline.

Continue refining this roadmap after writing it. Create tickets for settled phases
incrementally rather than freezing the whole backlog now. No new implementation,
pipeline execution or publication is authorized by this document.

## References

- [Sidecar design lens](../design-lens.md)
- [Sidecar application and deployment reference](../../../README.md)
- [Matching local framework reload contract](../../../../loomspan-framework/agent-skills/loomspan-docs/references/java-api/skill-reload.md)

Framework source/documentation reference uses the developer-maintained local
checkout and installed snapshot workflow. Implementation must recheck affected
public contracts and supply its own Sidecar integration evidence.
