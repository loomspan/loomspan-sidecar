# Sidecar design lens

Use these principles when planning, implementing and reviewing Sidecar changes.
Keep this document self-contained. Detailed contracts, ticket and phase references,
and implementation status belong in their own documents.

Sidecar is in development with no deployments or customers.

**Development policy: replace obsolete contracts, code and schemas directly.
Do not add compatibility shims, legacy adapters or parallel legacy APIs.**
Every new roadmap and ticket must state this policy prominently and reference
this lens. Document any required development-data reset and its impact.
This policy does not authorize deleting deployed data.

## Simplicity and technical debt

Choose the simplest solution that fully meets the current requirements. Reuse
existing capabilities where they fit. Add abstractions, dependencies, configuration
or process only when there is a concrete need. Keep code, tests, documentation and
process proportional to the work.

When meeting a requirement would add substantial complexity, reconsider the
requirement as well as the implementation. Include ongoing test-suite maintenance
and configuration combinations in the cost of scope; do not expand them without
a concrete benefit.

Prefer a few representative integration recipes over an exhaustive deployment
matrix. Untested network compositions are not automatically prohibited or
unsupported; retain each hop's security requirements without imposing a runtime
allowlist of tested recipes. Test tools must not dictate production contracts,
names or configuration.

Accept technical debt only when its benefit outweighs the expected maintenance
and future change costs. Record the tradeoff and any needed follow-up in the
existing ticket or plan. Review for unnecessary work as well as correctness;
simplicity does not excuse missing requirements or verification.

## Keep product documentation about the current system

The README, product documentation, and packaged skills are living descriptions
of the system available to their readers. Describe current behavior, supported
contracts, constraints, and usable examples directly. Keep them understandable
without access to internal tickets, pull requests, implementation plans, or
delivery history.

Do not add PR numbers, ticket identifiers, implementation-slice summaries,
completion receipts, or promises that behavior will arrive in a later PR. Keep
work tracking, sequencing, verification-run results, and handoff notes in tickets,
research, plans, and review artifacts under `ai/thoughts/`. Stable source and test
references may support a documented contract; they should not narrate the work
that produced it. Sidecar is in development with no deployments or customers.
Do not add upgrade or migration guidance, old-to-new recipes, or
compatibility-change histories to consumer documentation. Describe the current
supported contract directly; keep intentional-break assessments in internal
design and work artifacts.

When a feature is incomplete, state the actual current limitation and its effect
on the reader. Do not describe planned behavior as supported, hide a limitation
by removing its qualification, or substitute a future delivery reference for an
explanation. Review documentation changes against the current executable system
and remove obsolete recipes and tracking language as part of the same change.

## Framework remains the skill execution authority

The Framework owns skill validation, authorization, nested execution and execution
lifetime. Sidecar owns transport, admission and retained results. Use supported
public APIs and the authorized REST handler extension. Do not duplicate Framework
rules, replace internal beans or depend on internal types in application or test
code. A missing public capability requires explicit Framework planning.

## One admission owner and one retained record

Use one component to coordinate worker capacity, queue capacity and queued bytes.
Store the final outcome and selected diagnostic history together so readers cannot
see a partially completed result.

Limit record count, retention time and input size separately. These limits do not
guarantee a bound on total memory use.

## Trusted identity stays outside model inputs

**If apps share a Sidecar, they trust each other in every way:** asserted identity
and roles, reusable tokens, business data and network participation. Use separate
Sidecars when applications require isolation. V1 uses one issuer per Sidecar;
trusted groups share signing identity, user identity semantics and role vocabulary.
User/record authorization remains enforced, but provides no isolation against
another fully trusted group application.

Take credentials, caller identity and permissions from server authentication,
never from model inputs or skill instructions. Preserve authenticated identity
when work moves between threads or waits in a queue. Identify execution owners
by both issuer and subject.

Execution uses JWTs. Management credentials grant no execution access, and
execution JWTs grant no management access. Passthrough forwards the captured
execution credential; Sidecar does not mint, refresh or replace it. Forwarding
requires an approved recipient and remains subject to credential expiry and peer
access rules.

Configure verification keys deliberately; network membership does not substitute
for JWT validation. Each receiver validates the credential's
signature, intended audience, execution purpose and validity. Execution credentials
are bearer JWTs; transport mTLS does not bind them to a client certificate.
A browser session cannot substitute for callback authentication.

At issuance, include only allowlisted authorities the authenticated user actually
has. Preserve their exact names across tokens, Sidecar skill roles and callbacks;
do not add, strip or map role prefixes. Roles are snapshots until expiry, not live
account lookups. The SDK authenticates callbacks and establishes verified identity
and authorities; standard Spring method security and application business checks
authorize operations and records. The SDK does not interpret method annotations
or inherit URL rules from another security chain.

## One management API for browser and remote authoring

The console and remote clients share management endpoints, payloads, editing
services, validation and publication rules. Browser sessions retain CSRF protection.
Personal tokens grant limited access on behalf of an account. Reject requests
that ambiguously combine session and token credentials.

Check permissions on the server for every operation. A token can grant only actions
allowed by both its scope and the account's current permissions. Read, Edit and
Publish scopes are cumulative. Authorized Publish clients can publish without
extra browser approval, including approval of passthrough recipients.

Apply the same authorization rules to every way of changing configuration. Reject
unauthorized changes in full. Imports, rollbacks and client choice must not bypass
permission checks or file-mode restrictions.

## Editing ownership is explicit and credentials remain revocable

Saved drafts belong to an account and survive logout, credential expiry and
restart. Authorized clients of that account may read them, but only one editing
session may hold the write lease. Bind the session to the account and the login
or token that created it. Client labels and session identifiers grant no access
by themselves.

An explicit handoff invalidates the previous writer and requires the new writer
to read the current revision. Before a write or publication, check the lease,
draft revision and configuration version on which the draft was based. If the
published configuration has changed, require explicit reconciliation and fresh
validation. Do not merge or replace draft content silently.

Successful publication clears the published draft and releases its lease. Failed
publication preserves the draft. Recheck credentials and permissions after waiting
for locks, validation or external responses; an earlier check cannot authorize
work after expiry, revocation, logout or loss of account permissions.

## Trust and connection changes have explicit owners and timing

Assign clear ownership of signing keys, trusted verification keys and transport
credentials. Public exports use the signing owner's public keys without exposing
private material or independently changing signing state.

Colocated signing keys are intentionally ephemeral: application restart generates
a new key, and old tokens cease to validate as receivers adopt the replacement.
Provided signing keys are operator-owned; missing or invalid material must not
silently cause replacement key generation. Document the operational consequences
of each mode rather than adding persistent signer or bootstrap machinery.

The current operator-supplied or colocated JWKS file governs verification trust.
Publish complete validated key sets atomically. Missing, unreadable, malformed or
empty trust stops affected credential acceptance once detected; corrected valid
trust restores it through reload. Do not continue using last-valid keys after an
unusable file is detected. No withdrawal ledger or historical trust barrier is
maintained; restoring an old file restores its keys and is an operator responsibility.

State when each receiver adopts changed JWT trust, including queued work and
independent callback validators. Before Framework handoff, queued work must pass
current trust and expiry checks. Refreshing verification keys does not rotate
private signing keys or revoke trust everywhere at once.

DevOps owns TLS certificate issuance, storage, delivery, renewal, replacement and
revocation through standard Spring Boot or Caddy facilities. Reference deployments
accept operator-provided material. Test secure transport integration and deployment
boundaries, not a Loomspan certificate lifecycle system. Do not add renewal/reload
controllers or promise seamless rotation, timed connection retirement or exact
expiry cutoffs for established connections.

Withdrawal does not cancel work already admitted by the Framework or undo its
effects. Cancellation needs an explicit supported contract. Recovery and reconnects
must not automatically replay submissions or callbacks.

## Users choose their clients and deployment workflows

Apply permissions consistently regardless of client or environment labels. Do not
require a particular agent, promotion workflow or extra browser approval without
a concrete requirement. Export and import can support deployment workflows without
becoming the only way to publish.

## Atomic configuration publication and one framework shutdown budget

Select one complete configuration source at startup. File mode applies configuration
changes by restart and blocks management changes. Database mode restores the selected
publication without falling back to files or environment values for missing managed
settings or credentials. Process settings still require restart.

Validate and publish the complete skill, REST route and execution configuration
through the public Framework reload API. Immediately before committing, recheck
permissions, ownership and the exact validated candidate against the current
configuration. Keep the previous configuration and its resources available to
work already admitted under it. Draft validation must not activate or execute skills.

On shutdown, Sidecar stops dispatching and discards queued work. The Framework
owns the shutdown deadline for admitted work. Keep required clients alive until
that work finishes or reaches the deadline, then clean up without another drain
period. Closing clients early can break work the Framework is still completing.

Keep encryption keys outside the database and exported bundles. Moving encrypted
configuration must preserve credential identities and versions and require the
correct decryption key. Replacing credentials must be explicit.

## Preserve the available diagnostic contract

Return result text unchanged and retain selected public observer events without
Sidecar truncation. Preserve the original execution failure. Diagnostic history
contains the events that were available; it is not a guaranteed complete trace.
Do not wait indefinitely for missing observer events or inspect Framework internals
to reconstruct them. Respect the selected history policy and completion-based
retention limits.

Sidecar, its SDK and console do not detect, mask or remove sensitive content from
business inputs, prompts or callback responses. Applications are responsible for
what they put in that data; it may reach model context, results and diagnostics.
Infrastructure must not copy authentication credentials, private keys or internal
security context into business payloads, model inputs, logs or diagnostics.

Protect diagnostics through authentication, permissions and visibility rules.
Preserve the Framework's supported public output without adding Sidecar content
rewriting or promising durable history.
