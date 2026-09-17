# Sidecar management console and configuration snapshots roadmap

Date: 2026-09-16

Last handoff update: 2026-09-16, including framework tickets and restart bootstrap.

Status: proposed phase structure grounded in the agreed product scope. Ready for
roadmap review; not a set of implementation tickets or authorization to implement.

## Outcome

An embedded console manages its own Sidecar instance. Users create, update and
remove skills and REST configuration in session-owned drafts, validate the complete
candidate, and publish it without restarting Sidecar. Published configuration can
be backed up, restored, and moved between development, QA and production as a unit.

Define phases now and refine this roadmap before creating tickets. Keep one
roadmap rather than separate phase documents that duplicate decisions. Split out
phase detail only when it becomes useful. Unresolved product decisions below
block affected tickets, not this roadmap.

## Resume here in a new context

The work is at product/architecture refinement, not Sidecar implementation. The
developer requested two framework dependency tickets, created locally for manual
transfer to loomspan-framework. No pipeline, framework change, Sidecar production
change, commit or publication was performed for this roadmap work.

Read Agreed scope first, then Blocking loomspan-framework issues and the proposal
sections below. Do not reopen settled session ownership, snapshot invalidation,
destructive import, or management-only bootstrap locking. In particular, the old
one-shared-draft proposal was rejected: users must never inherit another user's
unpublished changes or responsibility for them.

The latest discussion favors application-supplied skill content, including initial
activation after restart. The framework ticket now requests that capability; this
does not select SQLite as Sidecar's persistence engine. Next, confirm the Sidecar
storage choice and beta deployment scope, then define persistent publication and
crash recovery. Continue refining this document before creating Sidecar tickets.
Target URL portability remains deliberately deferred until ticket preparation.

When the developer returns, inspect whether the framework tickets have moved or
been completed and recheck the public contracts against the matching local checkout.
Do not assume an API described as requested here exists. Framework completion
requires the developer's snapshot reinstall and affected Sidecar integration checks.
Existing beta 4 QA/release state remains in the handoff, not in this document.

## Agreed scope

- Support all framework-supported model-backed and REST YAML skill types and
  all aspects of their declarations. Exclude Java skill authoring and hosting.
- The editor may start as a complete YAML text editor; a section-based editor
  with explanations is an alternative or later enhancement. The choice is not
  settled, and the UI must not restrict the supported authoring surface.
- Run the console inside Sidecar and manage only that instance. Central management
  of multiple instances is outside this feature set.
- Maintain session-owned edits with one active editing lease for the instance.
  Users start from the current published snapshot, never another user's edits.
  Logout or changing browsers loses the edits; cross-session recovery is deferred.
  Lease expiry alone preserves edits within the session. Resumption requires an
  unchanged base snapshot ID and reacquisition of the lease. A changed snapshot
  invalidates and clears old edits. No cross-user draft revision is required.
- Protect editing with an exclusive lease with expiry. Automatically
  attempt renewal while the user is still working. Enforce ownership server-side.
- Validate before publication using the framework's prepare/publish API. Skill
  and REST configuration activation must not require a restart.
- Successful publication releases the lease. Validation/publication errors are
  shown to the user and preserve their edits and lease, subject to normal expiry.
- Initial import/restore is destructive: break leases, delete drafts and notify
  users that they must start over. Whether invalid imports leave editing sessions
  untouched until validation succeeds is a recommendation still to settle.
- Keep one fixed RestSkillHandler bean. Select the matching route configuration
  for each captured invocation generation; do not replace the bean.
- Managed configuration currently means skill declarations and the REST routes
  and target configuration needed by that handler. Arbitrary Spring settings,
  model-connection reload, and identity-provider configuration are not included.
- Skill definitions and REST route mappings travel together in snapshots.
- Target URL portability is explicitly TBD. Revisit during ticket preparation;
  do not silently choose whether imports replace or preserve destination URLs.
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

The matching local framework provides SkillReloader.prepare(), publish() and
snapshot(). Preparation freezes and validates a complete candidate; publication
does not reread files. The application must stabilize source files during prepare
and stage external configuration before publication. The injected SkillCatalog
remains a startup snapshot. Framework-admitted invocation trees retain their
captured generation; REST invocations carry its ID.

Preparation validates configuration, not future model outcomes or remote service
availability. Publication can reject stale candidates or shutdown-time updates.
The framework supplies neither a multi-file storage transaction nor a signal that
old application-owned routes and clients are safe to delete. Its generation IDs
are process-local, not durable exported snapshot identities.

Use only supported ai.loomspan.api contracts. Missing capabilities must be resolved
through deliberate framework planning, never internal imports or bean replacement.
This roadmap plans the evolution of the design lens's startup-only rule; it does
not claim reload is already implemented. Preserve the existing shutdown budget,
trusted execution identity, and framework execution authority.

The [beta 4 handoff](../beta4-handoff.md) remains the sole tracker for existing QA
and release work. This roadmap neither copies those obligations nor marks them
complete. The target release and its relationship to beta 4 remain to be decided.

## Phase 1: Configuration storage and snapshot foundation

**Outcome:** Establish a durable representation of the active configuration,
session-owned edits, and complete snapshots that can support authoring and portability.

Candidate ticket groups:

- Define the managed configuration boundary, stable snapshot identity and format
  version, and separation between durable snapshot IDs and runtime generation IDs.
- Implement the chosen persistent store and draft lifecycle, with a defined
  migration/bootstrap path from existing mounted skills and routes.
- Define and implement recovery of the last published configuration after process
  restart, including interrupted writes and publication bookkeeping.

**Exit evidence:** Draft edits do not change active execution; complete snapshots
can be recovered without mixing skill and route versions; restart and interrupted
write tests demonstrate the agreed recovery behavior. Storage and deployment
requirements are documented in this phase.

**Decisions before affected tickets:** File versus database storage; session expiry
and restart behavior; backup history retention; initial mount compatibility; the portable
configuration boundary, including target URLs where it affects the data contract.

## Phase 2: Validated runtime publication and REST generation lifecycle

**Outcome:** Activate a complete validated skill and REST configuration snapshot
without restarting, while protecting work using the previous generation.

Candidate ticket groups:

- Coordinate immutable candidate content, framework preparation, candidate route
  validation and HTTP client preparation, and publication. An edit after validation
  must require validation of the new candidate before it can be published.
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

**Decisions before affected tickets:** Safe retirement policy and bounds; exact
publication/recovery semantics. Keep these consistent with the single admission owner.

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

**Decisions before affected tickets:** Local account/session details, permissions
and account recovery; lease and inactivity timings; what counts as activity;
save/release versus discard; administrator takeover; multiple tabs in one session.
Those action details were proposed in discussion but have not all been approved.

## Phase 4: Embedded authoring and publication console

**Outcome:** Users can complete the supported authoring workflow in the browser.

Candidate ticket groups:

- Build the embedded console shell, initial setup/login flow and current published
  configuration views. Choose frontend tooling during implementation planning.
- Deliver complete skill editing and REST route/target editing with useful
  explanations and validation feedback. Preserve the full supported YAML surface.
- Display draft ownership, renewal/loss of lease, changed configuration, validation
  state and publication results. Preserve unsaved editor content on lease loss
  according to the agreed UX, without allowing stale writes.

**Exit evidence:** Browser-level workflows cover creating, modifying and removing
skills and routes, validation failures, successful publication without restart,
and two users contending for the draft. The published catalog reflects the update.

**Decisions before affected tickets:** Full-text editor versus structured sections
for the first release; validation/review presentation; unsaved-change handling.

## Phase 5: Backup, restore and environment promotion

**Outcome:** Export and recover complete managed configuration, and move it between
instances through a deliberate, validated workflow.

Candidate ticket groups:

- Export complete snapshots with format/version metadata and integrity checks;
  validate imported package structure and compatibility.
- Implement destructive import/restore: replace managed configuration, break
  leases, delete session drafts and notify users. Validate destination requirements
  and define the failure boundary before destructive cutover.
- Restore an earlier configuration through the same validated activation path;
  define backup scope, retention and supported recovery procedures.

**Exit evidence:** Round-trip and two-instance tests preserve skill definitions
and route mappings together; malformed/incompatible bundles cannot partially alter
active configuration; restore and destination validation work without restarting
for supported managed configuration. Target URL behavior matches its eventual
explicit decision.

**Decisions before affected tickets:** Target URLs (explicitly deferred); secret
references versus exported values; destination bindings; compatibility policy;
destructive cutover timing; whether backup includes only managed configuration
or also accounts for full instance recovery. Drafts are session-bound.

Restoring as a new activation, keeping secrets local, and excluding
accounts/sessions/locks from promotion bundles are recommended designs, not yet
agreed product requirements. Portability shapes Phase 1 even though its complete
user workflow ships here.

## Phase 6: Deployment and end-to-end readiness

**Outcome:** Operate the complete feature with durable storage, clear recovery
procedures and verified interaction with Sidecar's existing execution service.

Candidate ticket groups:

- Finish container/deployment examples for writable persistent state, setup
  credentials and console access, preserving non-root operation.
- Exercise publication, concurrent editing, backup/restore, process interruption
  and shutdown together through the real Sidecar application.
- Complete operator guidance and phase-specific acceptance evidence; integrate
  with the existing release handoff when the target release is chosen.

**Exit evidence:** Deployment exercises demonstrate persistent state, console-lock
isolation, recovery and the complete author/export/import/publish workflow. Existing
execution and shutdown contracts remain covered. Release checks follow the existing
handoff and published-framework dependency policy.

Tests and documentation belong to every implementation ticket. This phase covers
cross-feature verification and packaging, not deferred correctness or documentation.

## Blocking loomspan-framework issues

This is the working list of framework dependencies for this feature. Both items
are open; local tickets were created for manual transfer to loomspan-framework.
They have not been executed and are not external issue-tracker IDs.
Resolve them through supported public contracts before dependent Sidecar work;
do not compensate with internal imports or framework bean replacement.

| Issue | Required outcome | Blocks |
| --- | --- | --- |
| Safe retirement notification | A supported way to know that an old generation can no longer be used by admitted/running work, so Sidecar can retire its matching REST configuration and HTTP clients. Define ordering, concurrency and shutdown guarantees. Framework generation cleanup alone does not retire application-owned resources. | Phase 2 resource retirement |
| Application-supplied skills at startup and reload | Prepare a complete collection of named YAML content without replacing the active skill tree. Support application-controlled initial activation and restart loading as well as reload; a directory-only prepare argument is insufficient for database-backed content. Preserve existing directory-based behavior for other applications. | Phase 1 storage integration and Phase 2 preparation/bootstrap |

Tickets (self-contained; filenames remain identifiers after manual relocation):

- [2026-09-16-framework-generation-retirement-notification.md](../tickets/2026-09-16-framework-generation-retirement-notification.md)
- [2026-09-16-framework-application-supplied-skill-lifecycle.md](../tickets/2026-09-16-framework-application-supplied-skill-lifecycle.md)

Both recommend the Full 5-Step Pipeline. After moving them, update these links or
locate the same filenames under loomspan-framework/ai/thoughts/tickets/. They contain
framework outcomes and acceptance criteria, not instructions to implement Sidecar.

### Storage discussion — open, not a selected design

The developer proposed SQLite for skills and REST handler configuration, with a
possible external redundant database option. Evaluate transactional storage of
complete configuration snapshots and passing their YAML content into the framework.
The framework should not need to own Sidecar's schema or database connections.
An in-memory filesystem is another possibility, but does not itself provide durable
storage or a supported framework source API. The latest recommendation is to accept
named content directly; a directory overload can be a convenience, not a second
mandatory implementation. Spring byte-backed resources demonstrate that a physical
filesystem is not intrinsically required, but current Loomspan APIs do not expose
this new preparation path. No custom filesystem or internal-bean workaround is agreed.

SQLite would be embedded local persistence; supporting an external database is a
separate scope decision. Database transactions do not atomically commit the JVM's
active framework generation, so activation/restart recovery still needs a contract.
Database backup and portable configuration export are distinct: the latter must
select intended configuration and preserve compatibility and environment rules.
Target URL portability remains TBD. No database requirement is approved yet.

The assistant recommends SQLite for the local beta, with external database support
deferred. SQLite remains local file-backed storage with permission/locking needs;
it is not itself a redundant remote database. A supported external engine such as
PostgreSQL would need explicit scope, migrations and verification, not merely an
arbitrary JDBC URL. Keep storage operations contained without speculative multi-engine
infrastructure. Store original YAML content so the authoring surface stays complete.

For promotion, the assistant recommends a versioned configuration bundle containing
snapshot metadata, YAML and REST configuration instead of executing SQL dumps.
Database backup can serve installation recovery, but should not accidentally promote
accounts, sessions or environment secrets. Export format and account/secret treatment
still require decisions. A database transaction simplifies consistent storage; it
does not eliminate the runtime-publication recovery problem.

### Proposed restart/bootstrap flow

This is the requested framework integration direction, not existing behavior:

1. Sidecar opens its durable store and selects the recorded active snapshot.
2. Sidecar supplies that snapshot's complete named YAML content to Loomspan while
   initial execution activation is pending.
3. Loomspan validates/freezes the initial candidate and assigns a fresh generation ID.
4. Sidecar validates matching REST configuration and stages clients under that ID.
5. Sidecar publishes the generation and opens execution traffic.

Durable snapshot identity survives restart; runtime generation identity does not.
An explicit application-controlled startup path is needed so eager directory loading
does not activate skills before Sidecar stages the matching resources. Default
directory-based startup remains supported for other framework applications.

Proposed Sidecar policies, still to confirm: a new installation explicitly activates
an empty configuration and exposes initial console setup; invalid stored configuration
leaves execution unavailable while management remains available for recovery; session
drafts and leases do not survive a process restart. Do not silently replace invalid
stored configuration with an empty set. The framework ticket enables this workflow
without making Sidecar's persistence or recovery policy a framework responsibility.

### Lease and authentication proposals still to confirm

- Proposed lease defaults: 15 minutes of inactivity, activity updates throttled to
  at most once per 30 seconds, and a warning with a Continue editing action before
  expiry. Meaningful editor activity includes typing, paste, clicks and scrolling;
  mouse movement and background polling do not renew ownership. Server time and
  lease ownership govern writes and publication. These numbers and activity rules
  are recommendations, not accepted requirements.
- Decide whether normal reading requires explicit renewal and how multiple tabs
  share or contend for a session's editing lease. Administrative takeover and
  explicit release/discard actions also remain open. No per-user durable draft or
  cross-user draft revision scheme is requested.
- Proposed beta authentication: Spring Security local username/password form login,
  server-side sessions, hashed persistent passwords, CSRF protection for browser
  operations, and separate existing JWT execution authentication. An embedded JDBC
  account store and administrator/editor roles were recommended, not selected.
- Account creation/disable/reset, recovery, session timeout, and management permissions
  still need scope. LDAP and OAuth/OIDC are future possibilities, not beta requirements.
  The setup environment variable's name and validation policy are not yet chosen.
- Proposed import safety: validate the package and candidate before breaking leases
  and deleting drafts; serialize cutover with ordinary publication. Destructive import
  is agreed, but that failure boundary and notification mechanism are not yet settled.

### Remaining decisions before Sidecar tickets

Confirm storage and supported deployment first. Then settle recovery across the
database/file commit and framework publication boundary, initial configuration
adoption, backup-history retention, and the proposed first-start/error behavior.
Framework safe retirement will govern runtime REST resources; it does not decide
how long user-restorable backups are kept. Resolve lease/session/account details,
the first editor UI, import cutover, bundle compatibility and environment bindings
before their affected tickets. Do not convert these recommendations into requirements
just because they have been recorded in this handoff.

### Sources consulted during refinement

- Matching local framework public SkillReloader and PreparedSkillUpdate APIs and
  skill-reload.md; current prepare() has no source argument. Sidecar queues work
  before SkillInvocationHandoff, so capture remains at existing worker handoff.
- [Spring resource abstraction](https://docs.spring.io/spring-framework/reference/core/resources.html)
- [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
- [Spring Security JDBC account management](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/jdbc.html)
- [SQLite deployment guidance](https://www.sqlite.org/whentouse.html)
- [SQLite backup API](https://www.sqlite.org/backup.html)

## Sequence and ticket readiness

Storage precedes durable publication; publication and protected management APIs
precede the complete editor workflow; portability uses the same validation and
activation path with destructive draft invalidation. Authentication design can
proceed once the identity storage boundary is known.
These are dependency boundaries, not a requirement to finish every detail of one
phase before discussing the next.

Before writing tickets:

1. Review the phase boundaries and target release together.
2. Resolve the material choices affecting the next phase, starting with persistence,
   snapshot/draft lifecycle and publication recovery. Resolve target URLs near ticket
   creation as requested, early enough to avoid baking an assumption into storage.
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
- [Beta 4 QA and release handoff](../beta4-handoff.md)
- [Sidecar application and deployment reference](../../../README.md)
- [Matching local framework reload contract](../../../../loomspan-framework/agent-skills/loomspan-docs/references/java-api/skill-reload.md)

Framework source/documentation reference uses the developer-maintained local
checkout and installed snapshot workflow. Implementation must recheck affected
public contracts and supply its own Sidecar integration evidence.
