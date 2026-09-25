# Remote skill authoring through one management API

**Status: agreed direction and implementation roadmap; no implementation is
claimed by this document.**

**Parent work item: GitHub PR 6. All work in this roadmap belongs to PR 6;
implementation tickets are numbered PR 6.1, PR 6.2, ... PR 6.x.**

## Mandatory development constraints

**Sidecar is still in development. All changes must destructively replace
superseded behavior. No compatibility shims, legacy adapters, parallel legacy
APIs, or migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md) throughout planning, implementation
and review. Choose the simplest solution that fully meets current requirements
and minimize technical debt. Reuse existing capabilities; introduce abstractions
only for demonstrated needs. Keep code, tests, documentation and process
proportional. Document any required development-data reset and its impact;
this roadmap does not authorize deleting deployed data.

Every ticket created from this roadmap, and every other new Sidecar ticket,
must repeat these constraints prominently and link to the design lens. Use
[write_ticket](../../commands/write_ticket.md) and the PR 6 ticket naming rule
below. Create implementation tickets when their scope is ready; this roadmap is not a set of
pipeline-ready tickets or permission to start implementation.

## User outcome

A user works with an external coding agent in the repository of the application
being integrated with Sidecar. The agent can inspect that application's behavior,
data model and security, implement appropriate application endpoints, and author
matching Sidecar skills and REST routes. It reads and edits Sidecar configuration
through an authenticated management API and uses server validation to refine it.

The agent's repository access comes from its development environment, not from
Sidecar. Application endpoints remain responsible for their own authorization
and data-access controls. Authoring privileges do not imply runtime data access.

## Current foundation and required change

- The console already uses management endpoints for private drafts, leases,
  validation and publication. Existing ownership depends on browser sessions
  and tabs; accepting a new credential alone is insufficient.
- Management uses local accounts with viewer, editor and admin roles, session
  authentication and CSRF protection. Execution uses externally issued JWTs.
  Preserve that separation.
- Complete configuration publication already uses the public framework reload
  contract. Reuse this authority and the existing publication safeguards.
- These are local source/documentation observations, not new test evidence.
  See [operations](../../../docs/operations.md),
  [integration](../../../docs/integration.md) and the matching local framework
  documentation when creating implementation tickets.

## Agreed architecture

The browser and external client call the same management endpoints, with the
same payloads, structured errors and editing rules. Browser sessions use CSRF
protection; external clients use opaque, scoped personal access tokens tied to
existing management accounts. Both reach the same Sidecar editing services.

Authenticate centrally and pass trusted caller identity and effective permissions
to the services. Avoid client-type branches throughout controllers and services.
Reject requests with ambiguous mixed authentication. Do not disable CSRF for
cookie-authenticated requests when adding bearer authentication.

Each user has at most one saved draft containing the complete skill/route
configuration. Named drafts and branches are outside this scope. Drafts belong
to users and persist across logout, credential expiry and restart.
The same user can read their saved draft from the console or an authorized client.
Editing sessions remain bound to the user and originating credential; one specific
editing session holds the single editing lease. Session IDs confer no authority
by themselves. Both clients use the same editing and publication workflow.

Use Console/agent names only as display labels, not ownership or security flags.
Explicit same-user handoff rotates a server-issued lease generation and invalidates
the old holder. The new holder must read the current draft revision before saving;
every save checks the lease generation and expected revision. Delayed agent work
cannot overwrite newer changes. Clients must not automatically seize control back.
Retain administrator takeover for another user's lease without exposing that user's
private draft or granting this administrative operation to personal tokens.

The console shows the latest server-saved draft while another client edits it,
initially by polling for revision changes in read-only mode. Distinguish saved
draft content from published configuration; unsent agent work is not visible.
When a draft's published base changes, preserve its content and mark it stale.
The user or agent must explicitly reconcile it with current configuration and
submit against the new base, then validate again. Do not automatically merge,
silently replace content or merely relabel the base to bypass reconciliation.
Successful publication clears the published draft and releases its lease; the
published snapshot retains the content. Other users' drafts remain saved but
become stale. Failed publication preserves the draft.

The authoring skill supplies instructions, examples and workflow guidance. A
small client handles HTTP, credentials and editing identifiers. The server owns
authorization, validation and activation. Skill instructions are not a security
boundary. Keep tokens outside prompts, source control, URLs and diagnostic logs.

## Phase 1 — Shared authoring contract and console conversion

Replace browser-specific draft ownership with durable user-owned drafts and
explicit editing sessions, and
convert the console to the resulting API in the same implementation unit.
Remove superseded request fields and flows rather than preserving aliases.

Preserve private drafts, single-lease arbitration, exact candidate/base checks,
validation invalidation after edits, and publication authorization rechecks.
Define explicit renewal and bounded expiry; polling and background model work
must not silently renew access. Logout, credential expiry/revocation and account
changes invalidate applicable editing access without deleting saved work.
Implement explicit same-user handoff and read-only console viewing of agent edits.
Inventory import, rollback and other management mutations so they cannot bypass
the shared ownership and publication rules.

**Completion evidence:** console editing works through the shared contract;
the same user's authorized clients can read their draft, other users cannot,
and no client can use a foreign editing session;
stale candidates, expired sessions, lease conflicts and concurrent publication
are rejected. Handoff rejects delayed old-holder writes. Drafts survive logout,
token expiry and restart; a changed base requires reconciliation and validation.
Verify the one-draft-per-user limit, successful publication cleanup and lease
release, preservation of other users' stale drafts, and retention after failure.
Existing import/rollback and publication behavior remains coherent.

## Phase 2 — Scoped personal access tokens

Allow authenticated users to create, list and revoke their own personal access
tokens through the console. Show the random secret once and store only a secure
hash with non-secret metadata, owner, scope, expiry and revocation state. Require
expiry with a 7-day default and 30-day maximum, with no automatic refresh. These
are the initial product limits, not a requirement for configurable limits.
Use HTTPS for remote access and bearer headers for API requests.

Check current account status and permissions on every operation, intersected
with token scopes. Role reductions take effect without waiting for token expiry.
Revoke tokens on password change, reset/recovery and account disablement. Re-enabling
an account does not resurrect revoked tokens. Invalidate associated editing access
on revocation or expiry while retaining the user's draft.
Record user, token identifier, action and affected configuration version without
logging secrets. Apply bounded credential issuance and authentication abuse controls.

Provide three cumulative token permission presets:

| Preset | Allowed operations, subject to the user's current role |
| --- | --- |
| Read | Inspect permitted configuration and the user's saved drafts. |
| Edit | Read plus editing control, explicit same-user handoff, draft updates and validation. |
| Publish | Edit plus publication of an exactly validated candidate. |

Users choose Edit for manual console publication or Publish for an autonomous
client workflow. No separate LLM publication switch or mandatory UI approval is
required. Account administration, token issuance using a token, and administrator
takeover remain excluded. Deny ungranted operations by default, including indirect
publication through import or rollback. Map every management operation to these
permissions during planning. Editing an imported candidate requires Edit;
activating it requires Publish. The token permission is a maximum authority,
not a user role: every action must also be allowed by the user's current role.
Execution JWT handling remains unchanged; do not
introduce JWT issuance or OAuth infrastructure.

**Completion evidence:** browser and token requests exercise the same authoring
contract; scope and live-role denial, token expiry/revocation, ownership isolation,
CSRF retention and mixed-authentication rejection are tested. Tokens cannot reach
execution or administrative paths; only Publish tokens with a currently authorized
user can publish, including through indirect mutation paths. Credential exposure
is checked. Structured server logs suffice initially; no audit-history UI is required.
Verify the default and maximum token lifetimes and that expired tokens cannot
refresh themselves or regain access through another management operation.

## Phase 3 — Authoring skill and small REST client

Package repository-aware authoring guidance and a client for the shared API.
Teach the agent to inspect application behavior and data permissions, propose
integration endpoints and input/output contracts, author skill YAML and routes,
and iterate using structured server validation results. Use the matching
framework documentation, not stale installed documentation skills.

The client supplies credentials outside model inputs, manages editing identifiers,
and explains permission, lease, expiry and version conflicts. It must not silently
overwrite changes, take over another client's lease or repeatedly retry denied
operations. Avoid duplicating framework validation in the client.

Support both console review/publication and full remote publication according to
token permissions. Shared user-owned drafts and explicit lease handoff remove the
need for a separate candidate export/import workflow between the user's clients.

Keep the skill and client contract agent-neutral. No supported-agent allowlist or
restriction on which agent may attempt the workflow. Verify with a representative
client without making it a product requirement.

Sidecar stores only token hashes and metadata in its database. The external client
still needs the actual secret, shown once on creation. Initially document environment
injection without placing secrets in command arguments, repositories, skill files
or logs; do not build a credential vault. Runtime execution credentials remain separate.

**Completion evidence:** a representative integrating application can be inspected
and extended, its Sidecar draft created and corrected through the client, and
the exact proposed content viewed in the console while the client edits. Verify
explicit handoff for console publication with an Edit token, and full client
publication with a Publish token. Both paths validate the exact current candidate.
Exercise data authorization at the application's endpoint as well as Sidecar's
management boundary. Validation alone is not evidence of successful execution.

## Phase 4 — End-to-end readiness

Complete an integrated browser/client walkthrough covering configuration changes,
revocation during work, live role changes, stale candidates, competing clients
and recovery after expiry. Document installation, credential storage, renewal,
review/publication and development reset requirements. Each earlier phase still
owns its own tests and documentation; this phase does not defer necessary checks.

Use Sidecar integration evidence against the locally installed framework snapshot
and the repository's existing release policy. Do not substitute framework tests
for Sidecar behavior or claim hosted CI before the published dependency exists.

## Boundaries and later decisions

- No built-in assistant, OAuth authorization server or MCP server is required.
  Consider MCP later only for a concrete client integration need.
- No automatic execution-token delegation, runtime credential forwarding from
  management tokens, or elevation of application data privileges.
- No collaborative merging, multiple simultaneous writers or compatibility APIs.
- Do not prescribe how users divide work across development, QA and production.
  Export/import promotion is one supported example, not a required operating
  convention or an environment-specific restriction. Authorized publication is
  available regardless of environment; do not add production token exclusions,
  import-only activation or mandatory human review without a concrete requirement.
- No unpublished-draft execution environment is promised. Plan that separately
  if needed; validation does not test external connectivity or skill execution.
- Exact endpoint shapes, scope identifiers and token storage
  implementation belong in research/planning before the relevant
  ticket is finalized. These details must not silently expand product scope.

## Ticket sequencing and process

Assign sequential ticket identifiers **PR 6.1, PR 6.2, ... PR 6.x** across the
entire roadmap; do not restart numbering for each phase. These are ticket
identifiers under the single parent GitHub PR 6, not separate GitHub pull requests.
Include the identifier in each ticket's title and record GitHub PR 6 in its
Context. Use filenames `PR-6.1-<short-kebab-case-slug>.md`,
`PR-6.2-<short-kebab-case-slug>.md`, and so on under `ai/thoughts/tickets/`,
following the ticket command's supplied-work-item naming rule. This explicit
roadmap convention replaces the default dated ticket filename for this work.

Create bounded tickets in phase order, carrying forward settled requirements
and resolving observable lifecycle decisions before declaring a ticket ready.
Shared API, authentication and lifecycle changes warrant the Full 5-Step Pipeline
under the repository's eligibility rules. Assess client/documentation work on
its actual scope; do not force a lighter profile or create implementation reports
as part of this roadmap. Every ticket must include its own acceptance criteria,
tests/documentation expectations, dependencies, advisory execution profile and
the mandatory development constraints above.
