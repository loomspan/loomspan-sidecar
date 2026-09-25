# PR 6.1 — Share durable drafts and editing control across authoring clients

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

A user has one durable configuration draft and can explicitly move editing
control between their clients without losing work or allowing delayed writes
to overwrite newer changes. The console uses the same management contract that
the subsequent remote client will use, and can show saved changes while another
editing session holds the lease.

## Requirements

1. Replace browser-specific draft and tab ownership with a shared management
   contract and explicit editing sessions. Convert the console in this ticket;
   do not leave a second API or old browser flow in place. Reuse common services,
   payloads, structured errors and validation/publication rules. Authentication
   supplies trusted caller identity; client labels and input-carried identities
   cannot grant access. This ticket retains browser authentication; personal
   token authentication is PR 6.2, not a provisional implementation here.
2. Persist at most one saved draft per user, containing complete skill documents
   and REST route configuration. Saved work survives logout, credential expiry
   and application restart. Authorized sessions of the same user can read it;
   other users cannot, including an administrator taking over a lease. Editing
   sessions remain bound to the user and originating authenticated credential.
   No named drafts, branches or collaborative merging.
3. Retain one application-wide editing lease held by a specific editing session,
   not merely by a user or a UI/REST flag. Console/client names are display labels.
   Support explicit acquisition, renewal, release and same-user handoff. Handoff
   rotates the server-issued lease generation and invalidates the previous holder.
   The new holder reads current content/revision before saving. Every save checks
   current ownership and the expected revision; each saved change advances the
   revision and invalidates previous validation. Never automatically seize the
   lease back. Preserve administrator takeover without disclosing or deleting
   the displaced user's saved draft.
4. Bound editing access and lease lifetime. Background polling, saves, validation
   and model work must not silently renew authenticated access. Logout, expiry
   and relevant account changes invalidate associated editing access, not saved
   content. Restart preserves drafts but must not resurrect stale editing grants
   or validation authority. Concrete identifiers and timeouts belong in planning.
5. While another session of the same user edits, the console polls for revisions
   and shows the latest server-saved draft in read-only mode, with current holder
   information and an explicit take-control action. Distinguish unsaved local
   content, saved draft content and published configuration. Polling or loss of
   ownership must not silently overwrite unsaved local text or resubmit it.
6. When the runtime base changes, preserve other drafts and mark them stale.
   Require the user or client to explicitly reconcile with current configuration,
   submit against the current base and validate again. Do not automatically merge,
   silently replace content or accept a base-only relabel as reconciliation.
   The server enforces the explicit current-base submission and validation;
   it need not judge the semantic quality of the user's reconciliation.
7. Publish only the exact saved, successfully validated candidate against its
   current base. Recheck live authorization, editing ownership, lease generation,
   candidate and base after waiting for the publication gate. Successful
   publication clears the published draft and releases its lease, retaining its
   content in the published snapshot. Other users' saved drafts become stale.
   Failed publication preserves the draft. Keep import and rollback coherent
   with these rules; no alternate mutation path may bypass them.
8. Preserve browser CSRF protection and existing role boundaries. Management
   authentication grants no execution access. Use only supported framework APIs
   for skill validation and atomic configuration publication, preserving execution
   generations and shutdown behavior. Do not duplicate framework validation.
9. Supply this unit's Sidecar tests and console/API/operator documentation,
   including ownership, expiry, persistence, stale-base recovery and any destructive
   development reset. Do not defer this ticket's assurance to the later client ticket.

## Acceptance criteria

- [ ] The converted console uses one documented management contract for editing,
  validation and publication; obsolete tab-dependent contracts and legacy paths
  are removed. No personal-token scaffolding or duplicate agent API is required.
- [ ] Multiple authenticated sessions of one user observe the same single saved
  draft. Another user cannot read it or use its editing identifiers. The complete
  saved configuration survives logout, expiry and restart without restoring old
  editing or validation authority.
- [ ] Only the current lease holder can mutate the draft. Explicit same-user
  handoff succeeds; a delayed old-holder write, stale revision or foreign session
  is rejected without changing content. Administrator takeover preserves privacy
  and saved work. Background traffic does not extend access or lease deadlines.
- [ ] A read-only console view follows server-saved revisions from another session,
  identifies who holds control, and supports explicit handoff. Unsent local text
  is not silently lost or applied after control changes; saved and published states
  are visibly distinct.
- [ ] A changed published base retains other users' drafts as stale. Publication
  is blocked until explicit current-base reconciliation and renewed validation;
  conflicts never silently merge or replace content.
- [ ] Successful publication retains the published snapshot, clears the published
  draft and releases its lease. Failed publication retains the draft. Import and
  rollback respect the same ownership, validation and publication protections.
- [ ] Sidecar evidence covers delayed/concurrent writes, stale validation and
  authorization or ownership changes while waiting to publish. Browser CSRF,
  role checks, management/execution isolation and supported framework boundaries
  remain enforced.
- [ ] Required checks pass and documentation describes the new contract, recovery
  flows and reset implications; no compatibility machinery preserves obsolete
  behavior and no deployed data was deleted as routine verification.

## Context

- Parent work item: **GitHub PR 6**. This is ticket **PR 6.1**, not a separate PR.
- Roadmap: [Remote skill authoring](../phases/2026-09-24-remote-skill-authoring-roadmap.md),
  phase 1. No prerequisite ticket. PR 6.2 consumes this contract.
- Intended implementor: **GPT Sol 6**. This deliberately groups durable storage,
  ownership, API and console conversion into one complete outcome to avoid repeated
  pipeline overhead and intermediate incompatible clients. It is not a request
  to start implementation or a model override for the current ticket-writing task.
- Current context from the agreed roadmap: browser draft ownership is session/tab
  based; management accounts are local; complete runtime publication already exists.
  These are planning inputs, not verification evidence.
- Excludes personal token issuance, the authoring skill/client, OAuth, MCP, a built-in
  assistant, automatic merging and unpublished-draft execution. Do not impose
  environment-specific publication policy.
- Consult the matching local framework checkout and installed beta.5 snapshot.
  Follow repository release rules; Sidecar integration evidence cannot be replaced
  by framework tests or hosted CI requiring an unpublished dependency.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This unit changes persisted draft ownership, the supported
  management contract, authorization checks and concurrent editing/publication
  lifecycle across the console and backend. Research and planning are necessary.
- **Reassessment triggers:** None for a lighter profile; Full is warranted by
  the agreed scope. Newly unavailable public framework contracts require explicit
  planning rather than internal imports or a scope workaround.
