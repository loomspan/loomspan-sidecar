# PR #8.0.2 — Settle SDK v1 trust administration, bootstrap and lifecycle contracts

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Produce concrete contracts for safely provisioning, administering, publishing and
withdrawing SDK v1 trust and credentials. Later implementation must have one clear
owner for each state transition and explicit enforcement timing, rather than
mistaking JWKS refresh or TLS configuration for a complete lifecycle mechanism.

## Requirements

- Define console/API uploaded public-key and administrator-configured JWKS URL trust
  sources, accepted formats/payloads, issuer/audience/authority association, persistence
  and publication under the preceding identity contract. Trust never comes from an
  incoming token's URL. Specify TLS/destination controls, retrieval, cache, refresh,
  outage and restart behavior; private signing material stays with its issuer.
- Define one console administrator and shared browser/automation management REST
  operations using cumulative opaque Read/Edit/Publish tokens. Specify exact operation
  mappings and token naming, expiry, revocation, hashing, creation-only disclosure and
  protected client storage. Effective permission intersects token scope with live
  account authority, checked per request and before committing after waits/locks.
- Require interactive administrator login for account administration, token creation
  and protected issuer/key trust changes, including audiences, issuer-authority
  mappings and emergency withdrawal/restoration. All token presets must be denied
  these operations, including indirect import/publication/rollback and stale-draft
  paths. Reject protected changes atomically, without partial apply or silently
  dropping fields, and recheck current trust at commit time.
- Preserve authorized Publish destination approval, including reusable-token
  passthrough recipients, without extra browser approval. Retain editing ownership,
  leases, revisions, validation and atomic publication; file-mode mutation restrictions
  apply to every client. Browser sessions retain CSRF; reject ambiguous management
  session/token combinations. Management and execution credentials grant no access
  to each other's APIs. Reconcile affected management plans and the design lens.
- Select one signing-state owner shared by provisioning and Spring/custom issuance.
  Specify persistent storage, permissions, initialization, replica coordination and
  alternative signer integration. Define publication-before-use, overlap, retirement,
  missing/corrupt-state recovery and operator responsibilities for uploads versus
  URL publication. JWKS refresh alone is not private-key rotation; do not silently
  reset identity, choose another store or regenerate missing established keys.
- Define the optional explicitly enabled JWKS endpoint in the existing application
  server, with no new server/port, clear failure without a supported web server,
  separate route/access policy and property/bean precedence. A supplied source replaces
  the default rather than creating competing signing state. Uploads/separately hosted
  JWKS support outbound-only applications; callback-validator trust is independent.
- Define property-driven public JWKS export after signing initialization and a Java
  API/Spring bean using the same publication snapshot as the endpoint. Require an
  explicit output path, public asymmetric material only, key IDs/metadata, publishable
  overlap and exclusion of withdrawn keys. Startup skips an existing file unchanged,
  including after key changes. Specify atomic new output and explicit replacement,
  clear unavailable-source/write errors and signing-store path safety. Export must
  never generate/reset/rotate keys or change signing state. No listener, customer
  startup hook, Maven export command/plugin or standalone CLI is required.
- Select an established provisioning mechanism for unique deployment-specific signing
  keys and separate TLS material for isolated Compose. The default needs no manual
  key/certificate exchange, certificate-tool commands or new identity server.
  Automatically generating JWT keys alone does not provision mTLS certificates.
  Define startup ordering, storage, delivery, public export and recovery. Sidecar
  never holds the application signing key. Keep customer-managed inputs usable with
  no permissive fallback and preserve explicit Relay/Exchange selection.
- Define operator bootstrap under file/database authority without runtime bypass of
  interactive trust administration. Repeated initialization must preserve established
  state and never restore withdrawn trust. Separate JWT and TLS ownership, persistence,
  provisioning, reload and renewal; define SDK-native and proxy client connection
  retirement, pooled/in-flight behavior, invalid updates and expiry recovery.
- Distinguish routine signing rollover from emergency key/issuer distrust. Define
  administrator procedure, cache invalidation, actual rejection timing/exposure
  windows, independent callback validators, outage/restart, queued/admitted work and
  explicit restoration. Import, rollback and stale refresh must not silently restore
  withdrawn trust. Withdrawal implies neither cancellation nor rollback of effects.
  Define workload membership removal for existing/new connections and coordinate
  live user/token authority enforcement with the identity contract. No recovery,
  refresh or reconnect may replay submissions or callbacks.
- Supply named planned acceptance scenarios and phase/repository ownership for these
  contracts. Separate initial enforcement in Phase 1, group extension in Phase 2
  and lifecycle mechanisms/stress evidence in Phase 3; do not defer all verification.

## Acceptance criteria

- [ ] The design specifies both verification sources, protected trust data and
  retrieval/cache/outage behavior, with concrete payload/state contracts and named
  source-validation and private-material rejection scenarios.
- [ ] An operation/authorization table covers cumulative token scopes, successful
  authoring/publication and passthrough destination changes, interactive-only actions,
  live checks after waits, token lifecycle, mixed credentials, browser CSRF, editing
  invariants and file mode. Direct and indirect protected mutations have atomic
  rejection and stale-draft acceptance scenarios for every token preset.
- [ ] One signing/publication owner, persistence and replica/alternative-signer policy
  are specified. Endpoint precedence, disabled/no-server/outbound-only behavior and
  both export interfaces have named scenarios covering public-only snapshots,
  overlap/withdrawal, existing-file skip, explicit atomic replacement, errors and
  no signing-state mutation, plus local/Compose retrieval and upload guidance.
- [ ] The automatic bootstrap contract covers unique independent deployments,
  protected private storage, separate signing/TLS delivery, file/database authority,
  restart persistence, repeated bootstrap, missing/corrupt state and customer-managed
  inputs without manual certificate work, fallback or restoration of withdrawn trust.
- [ ] Lifecycle tables state rollover and emergency-distrust timing separately for
  uploads/URLs and independent validators, including caches, outages, restarts,
  queued/admitted work and explicit restoration. Both mTLS client paths have planned
  reload/renewal/invalid-update/expiry and member-removal connection scenarios with
  no submission/callback replay.
- [ ] Contract dependencies, demonstrated Sidecar gaps, required public Framework
  changes and implementation/evidence owners are explicit; affected plans/lens are
  consistent and unresolved material decisions prevent affected contract sign-off.

## Context

Roadmap work item: GitHub PR #8. Ticket identifier: `PR-8.0.2`.
SDK v1 tickets use `PR-8.[phase].[ticket]` identifiers.

Depends on [identity/callback contracts](PR-8.0.1-sdk-v1-identity-callback-contracts.md).
Feeds [SDK/deployment/evidence contracts](PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md),
which selects final topologies, versions and commands using these provisioning and
lifecycle contracts. Do not duplicate ownership or leave conflicting reference designs.

Normative sources are [Phase 0](../phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md),
the [roadmap trust/export contracts](../phases/2026-10-11-java-spring-sdk-roadmap.md#agreed-verification-key-configuration-and-optional-jwks-endpoint),
[transport/bootstrap scope](../phases/2026-10-11-java-spring-sdk-roadmap.md#v1-transport-composition-and-deployment-patterns)
and [security research](../phases/2026-10-11-established-security-architectures.md).
Their detailed scenarios remain required. This ticket settles contracts, not production
features, deployable provisioning tooling or performed lifecycle evidence.

Keep the closed public Framework boundary and ArchUnit enforcement: only
`ai.loomspan.api`, including authorized `RestSkillHandler`; no internal/autoconfigure
dependencies, new SPI, internal-bean replacement or Sidecar Java `@SkillMethod` skills.
Consult local Framework tag `v1.0.0-beta.8` for the pinned starter `1.0.0-beta.8`.
Use documented `loomspan.*`, Sidecar-owned `loomspan-sidecar.*` and standard Boot
namespaces for their respective responsibilities; proposed SDK names remain design work.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Material authorization, persisted trust, bootstrap and lifecycle
  contracts require discovery and design even though this unit delivers documentation.
- **Reassessment triggers:** Reassess if approved concrete contracts already settle
  the complete scope; do not downgrade while lifecycle or trust decisions remain.

## Pipeline notes

- Deliver reviewed contracts and planned scenarios only. Record unresolved developer
  decisions; do not weaken requirements or create dependent implementation tickets
  until their material contracts are settled. Unrun scenarios are not evidence.
- No data deletion, product implementation, commit, push or release is authorized.
  The developer commits per ticket and syncs after all phases; hosted CI is not an
  intermediate gate. No code/build breakage is intended here. Later intentional
  breakage must name impact, feasible verification and a dependent restoration unit;
  acceptance remains outstanding until restoration and verification.
