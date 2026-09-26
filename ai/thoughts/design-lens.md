# Sidecar design lens

Standing design constraints for Sidecar planning, implementation and review.

**Development policy: destructively replace superseded contracts, code and
schemas. No compatibility shims, legacy adapters or parallel legacy APIs.**
Every new roadmap and ticket must state this policy prominently and reference
this lens. Document required development-data resets and their impact; this
policy does not authorize deleting deployed data during planning.

## Simplicity and technical debt

Standing guidance for our work together:

> Remember that we are choosing the simplest solution that still covers the requirements.
> We are working to avoid technical debt where possible. Also we are in development
> so destructive changes are welcome. No compatibility shims.

- **Decision:** Choose the simplest solution that fully satisfies the current
  requirements. Prefer existing capabilities and direct implementations. Add
  abstractions, configuration, dependencies or workflow artifacts only for a
  concrete need; speculative future needs are insufficient justification.
  Keep code, tests, documentation and process proportional to the work.
  Replace obsolete development code, configuration and schemas directly rather
  than preserving them with compatibility shims or migration machinery solely
  to support obsolete development contracts.
- **Why this matters:** Complexity consumes implementation, review and
  maintenance effort. Minimize technical debt; accept it only when its concrete
  benefit outweighs its expected maintenance and future change costs. For
  deliberate debt, briefly record the tradeoff and any necessary follow-up in
  the existing ticket or plan rather than creating another tracking artifact.
- **Applies to:** Planning, implementation, testing, documentation and review.
  Review should look for unnecessary work and opportunities to simplify as
  well as correctness. Simplicity does not excuse unmet requirements or omitted
  necessary verification.
- **Exceptions:** More complex solutions are warranted when required for
  correctness or another current requirement; explain the reason briefly.

## Framework remains the skill execution authority

- **Decision:** Use the public catalog, validation, invocation, observer and named
  REST SPI. The framework owns skill validation, authorization, nesting and
  execution lifetime; Sidecar owns transport, admission and retention.
- **Why this is non-obvious:** Re-parsing manifests or replacing internal beans
  can appear to simplify startup or HTTP error handling while creating a second
  authority and an unsupported dependency.
- **Applies to:** All integration, production and test code.
- **Exceptions:** New framework contracts require deliberate developer planning.

## One admission owner and one retained record

- **Decision:** Coordinate worker/queue capacity and queued bytes with one owner;
  publish terminal outcome and selected history together in the same record.
  Count/TTL bounds and input byte limits are distinct; neither bounds total heap.
- **Why this is non-obvious:** Separate semaphores, staging queues and history
  stores can duplicate authority and publish partial outcomes.
- **Applies to:** Execution admission, diagnostics and shutdown cleanup.
- **Exceptions:** None in beta 4.

## Trusted identity stays outside model inputs

- **Decision:** Execution API identity remains JWT-only and is propagated through
  queued execution via Spring Security. Execution ownership uses issuer and
  subject. Passthrough forwards the captured execution credential; Sidecar never
  mints, refreshes or exchanges execution JWTs. Management credentials do not
  grant execution access, and execution JWTs do not grant management access.
  Planned opaque personal access tokens belong to local management users; they
  are not execution JWTs. Credentials, caller identity and permissions come from
  trusted server authentication, never model inputs or a skill's instructions.
- **Why this is non-obvious:** Request-thread authentication is lost at ordinary
  executor boundaries; input-carried identity is not a trusted substitute.
- **Applies to:** Execution and management APIs, authorization, ownership,
  authoring clients and REST callbacks.
- **Exceptions:** Test issuers may mint local fixture tokens.

## One management API for browser and remote authoring

- **Decision:** Planned remote authoring and the console share management
  endpoints, payloads, editing services, validation and publication rules.
  Browser sessions retain CSRF protection; personal tokens add scoped bearer
  authentication. Effective token authority is the intersection of the live
  account's permissions and the token's allowed operations. Reject ambiguous
  mixed credentials. Enforce these rules server-side on every operation.
  Planned token presets are cumulative Read, Edit and Publish; Publish permits
  remote publication without mandatory UI approval, subject to live user authority
  and all validation/publication checks. Tokens grant no account administration.
- **Why this matters:** A separate agent API or browser-session emulation would
  duplicate behavior and security rules. REST is the initial interface; OAuth,
  MCP and a built-in assistant are not prerequisites.
- **Applies to:** Planned shared management contract, console and authoring client.
- **Exceptions:** Authentication and credential-management operations may have
  different access policies; that does not require duplicate authoring APIs.

## Editing ownership is explicit and credentials remain revocable

- **Decision:** Each user has at most one saved draft containing the complete
  skill, REST route, and execution configuration. It survives logout, credential expiry and restart.
  The same user's authorized clients may read the saved draft. Editing
  sessions bind to the user and originating login or personal token; one session
  holds the single lease. UI/agent labels are descriptive only. Explicit same-user
  handoff rotates the lease generation, invalidates the old holder and requires
  the new holder to read the current revision. Identifiers alone confer no
  authority. Retain explicit renewal and exact lease/candidate/base checks.
  A changed runtime base makes retained drafts stale and requires explicit
  reconciliation against current configuration and validation, without automatic
  merging or silent content replacement. Successful publication clears the
  published draft and releases its lease; other users' drafts remain saved but
  stale. Failed publication preserves the draft. Credential expiry,
  revocation, logout and account changes must prevent further unauthorized
  operations, including after waiting on locks or model responses.
- **Why this matters:** Sharing an API must not expose another client's draft,
  allow stale writes, or turn background work into indefinite authenticated access.
- **Applies to:** Planned remote authoring redesign and corresponding console updates.
- **Exceptions:** No automatic merging or simultaneous writers. Same-user read
  access and explicit handoff do not grant access to another user's private draft.

## Users choose their clients and deployment workflows

- **Decision:** Keep the authoring skill and API agent-neutral. Read/Edit/Publish
  permissions govern actions regardless of client or environment labels. Do not
  impose a production promotion convention, import-only activation, mandatory UI
  approval or an agent allowlist. Export/import promotion is an example workflow.
- **Why this matters:** Unnecessary usage restrictions add policy and maintenance
  costs without satisfying an agreed requirement.
- **Applies to:** Authoring guidance, management APIs, tokens and environment examples.
- **Exceptions:** Add restrictions only for a concrete requirement through explicit
  planning; ordinary authorization, validation and execution boundaries still apply.

## Atomic configuration publication and one framework shutdown budget

- **Decision:** Restore the database-selected snapshot at startup. Publish complete
  validated skill, REST route, and execution snapshots through the public framework reload contract;
  activation does not require restart. Recheck live authorization, ownership and
  exact candidate/base under the publication gate. Preserve old generations for
  already-admitted work. Sidecar immediately stops dispatch and discards queued
  work on close; framework
  shutdown owns already-admitted work. Keep clients/callers alive until its
  completion/cutoff, then clean up without another drain period.
- **Why this is non-obvious:** Queue draining, ordered listeners or early client
  teardown can defeat the framework's existing deadline and nested work.
- **Applies to:** Configuration, workers, HTTP clients, readiness and packaging.
- **Exceptions:** External credential provisioning and process settings still require
  restart; model connections, aliases, session limits and trace persistence are
  published with skills and REST routes. Draft validation neither activates nor executes skills.

## Preserve the available diagnostic contract

- **Decision:** Return text unchanged and retain all selected public observer
  events without Sidecar size truncation. Preserve the facade's primary failure.
  History is available completed diagnostics, not a guaranteed complete trace.
- **Why this is non-obvious:** An observer may never run after pre-session or
  mapping/finalization failures. Waiting for it or parsing internal traces creates
  unsupported behavior. Diagnostic events may contain business data.
- **Applies to:** Failure envelopes, diagnostics, retention and client guidance.
- **Exceptions:** NEVER/ONERROR selection and whole-record completion TTL remain
  the agreed policies; no new sanitization or durable-history contract.
