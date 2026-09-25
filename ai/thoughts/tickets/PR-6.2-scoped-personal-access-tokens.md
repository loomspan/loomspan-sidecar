# PR 6.2 — Author and publish through scoped personal access tokens

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Users issue revocable, expiring personal tokens for external clients to read,
edit or publish through the same management API as the console. Users can choose
manual console publication or autonomous remote publication without an OAuth
server, a second API or additional privileges beyond their current role.

## Requirements

1. Add console creation, listing and revocation of the authenticated user's own
   opaque personal access tokens. Show a cryptographically random secret once;
   store only its secure hash and non-secret identifier, owner, permission, expiry
   and revocation metadata in the database. Do not expose another user's tokens.
   Token expiry defaults to 7 days, cannot exceed 30 days and must be in the future.
   No automatic refresh, JWT issuance or configurable-lifetime subsystem is required.
2. Accept tokens as bearer credentials on the existing shared management contract.
   Authenticate centrally and use the same editing/publication services as browser
   requests. Bind editing sessions to the authenticated user and originating token;
   session identifiers alone do not authorize access. Retain browser cookie/session
   authentication with CSRF protection. Reject ambiguous mixed credentials; adding
   bearer authentication must not create a cookie-authenticated CSRF bypass.
3. Provide cumulative permission presets: **Read** inspects permitted configuration
   and the user's saved draft; **Edit** adds editing control, explicit same-user
   handoff, draft changes and validation; **Publish** adds activation of an exactly
   validated candidate. Every request must satisfy both the token's permission and
   the current account's role/status. A preset is a ceiling, not a role or grant
   of elevated authority. Check live roles rather than relying on issuance-time roles.
4. Map all management operations to these boundaries, denying ungranted operations
   by default. Read tokens cannot mutate or acquire editing control. Importing or
   editing a candidate requires Edit; activation requires Publish, including import
   and rollback activation. Personal tokens cannot administer accounts, issue tokens,
   or use administrator takeover even when owned by an admin. Same-user explicit
   handoff remains available to Edit/Publish. Keep personal-token lifecycle
   administration in the authenticated console for this scope.
5. Allow Publish tokens to complete remote publication without mandatory console
   review or a separate LLM toggle. Enforce all exact candidate, validation, base
   and lease checks from PR 6.1. Do not impose development/QA/production restrictions,
   import-only promotion or an agent allowlist. Recheck current token validity and
   effective user authority at the mutation/publication gate after waits, so queued
   or delayed work cannot use revoked or reduced authority.
6. Revocation and expiry invalidate associated editing access without deleting the
   user's draft. Password changes, password reset/recovery and account disablement
   revoke the user's tokens; re-enabling the account does not resurrect them. Role
   reductions take effect on subsequent operations, including operations awaiting
   a mutation gate. Retain PR 6.1 persistence, single-lease, handoff and stale-base rules.
7. Preserve credential boundaries: execution JWTs cannot access management and
   personal tokens/browser sessions cannot access execution. Do not exchange or
   forward management tokens as runtime credentials. No OAuth infrastructure,
   MCP server, execution token issuer or framework internal dependency.
8. Use HTTPS for remote access and authorization headers rather than URLs or
   request payloads for tokens. Bound token issuance and authentication abuse
   using a proportionate policy chosen in planning. Keep secrets out of logs,
   errors, URLs and subsequent token-list responses. Emit structured audit logs
   identifying the user, token identifier when applicable, action and affected
   configuration version/outcome; no audit-history UI is required.
9. Supply this ticket's tests and documentation for issuance, permission limits,
   expiry, revocation and client setup. Explain that Sidecar's stored hash cannot
   supply the secret to a client: the user must provide the one-time secret to
   their environment. Document environment injection without command-line secret
   arguments, repository storage or a new credential vault. PR 6.3 packages the client.

## Acceptance criteria

- [ ] The console can create/list/revoke only the current user's tokens. The secret
  appears only on creation, stored records contain no recoverable token secret,
  expiry defaults to 7 days, and invalid/past or over-30-day expiry is rejected.
  Expired tokens cannot refresh or issue replacement credentials.
- [ ] Browser and token clients use the same editing and publication endpoints and
  payloads. Cookie-authenticated mutations retain CSRF enforcement; ambiguous
  mixed authentication and invalid bearer credentials are rejected.
- [ ] A permission/role matrix proves cumulative Read/Edit/Publish behavior and
  live account enforcement, including viewers with high-permission tokens and
  role reductions. Another user's draft, token records and editing sessions remain
  inaccessible. Explicit same-user handoff works without administrator privileges.
- [ ] Edit tokens can author and validate but cannot activate directly or via import
  or rollback. Publish tokens with an authorized user can publish the exact valid
  candidate without UI approval. Administrative/token-management/takeover endpoints
  are unavailable to personal tokens, including admin-owned tokens.
- [ ] Expiry, explicit revocation, password change/reset/recovery and disablement
  stop token-backed access and stale editing work while preserving saved drafts.
  Re-enabling an account does not reactivate revoked tokens. Mutation-gate tests
  cover revocation or reduced permission while a request waits.
- [ ] Management tokens cannot invoke execution and execution JWTs cannot manage
  configuration. No environment label or agent identity creates extra permissions
  or publication restrictions; no OAuth or JWT issuance machinery is introduced.
- [ ] Abuse controls are bounded and verified; normal failures and audit events
  expose no token secrets. Structured logs identify the actor, action and relevant
  configuration version/outcome without requiring an audit UI.
- [ ] Sidecar checks and integration tests pass against the installed framework
  snapshot, preserving PR 6.1 handoff/persistence/publication protections. Console,
  API and credential-setup documentation describes the actual verified behavior.

## Context

- Parent work item: **GitHub PR 6**. This is ticket **PR 6.2**, not a separate PR.
- Roadmap: [Remote skill authoring](../phases/2026-09-24-remote-skill-authoring-roadmap.md),
  phase 2.
- Depends on [PR 6.1](PR-6.1-shared-authoring-and-durable-drafts.md). It supplies
  durable user-owned drafts and the shared API. PR 6.3 consumes this token contract.
- Intended implementor: **GPT Sol 6**. Token storage, lifecycle UI, authentication,
  scope enforcement and their security evidence form one implementation unit;
  avoid fragmenting them into small tickets that leave incomplete access controls.
- Exact hashing/lookup implementation, scope identifiers, abuse limits and HTTP
  details belong in research/planning, within these settled product constraints.
- Follow the repository's supported framework boundary and release policy. Use
  Sidecar evidence against the local framework snapshot; do not rebuild the framework
  as part of normal Sidecar work or substitute framework tests for integration tests.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Introduces a credential lifecycle, persisted security data and a
  new authentication method across existing management and publication boundaries.
- **Reassessment triggers:** None for a lighter profile; Full is required by the
  agreed scope. A design needing execution-token exchange or another identity
  system would exceed this ticket and requires a developer decision.
