# SDK v1 Phase 1 — Isolated integration

**Development policy:** Destructively replace superseded contracts, code and
schemas. No compatibility shims, legacy adapters, dual legacy APIs or migration
machinery solely to preserve obsolete development behavior. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt); choose the simplest
complete solution and keep technical debt and process proportional. Document
required development-data resets and impact; planning authorizes no data deletion.

## Delivery and evidence rules

This document defines a phase, not an implementation ticket or completed evidence.
The [roadmap](2026-10-11-java-spring-sdk-roadmap.md) and
[security research](2026-10-11-established-security-architectures.md) retain their
detailed agreed requirements and acceptance scenarios. These phase documents
organize that scope; they do not replace or reduce it.

Intermediate tickets and phases may deliberately break code, builds or tests that
a later unit restores. Do not add compatibility scaffolding to keep every
intermediate commit green. Each affected ticket must record the known breakage,
its impact, verification still possible, and the named dependent unit that restores
it. Report expected failures honestly, distinguishing them from unexpected defects.
A phase can hand off documented temporary breakage; its affected acceptance evidence
remains outstanding until restored and verified. No deferred breakage may remain at
final release readiness.

The developer commits after each ticket and will not sync to the remote until all
phases are complete. Agents do not commit, push or release without separate
authorization. Local validation must support this workflow; hosted CI is not an
intermediate-phase gate. After sync, verify required hosted checks before release,
following the repository's Framework publication/dependency policy.

Tests live with the responsibility they exercise:
- This repository owns Sidecar tests and the independently built SDK's focused
  tests in `sdks/java/`. Small Docker Compose smoke/contract checks may live here
  when they provide focused local feedback without duplicating the system matrix.
- `C:/opendev/code/loomspan-sidecar-test-suite` owns the complex Docker Compose
  scenarios: real SDK, Sidecar, application, local identity provider and proxies,
  transport combinations and cross-service lifecycle/fault injection.
- Framework tests alone never establish Sidecar integration. Evidence recorded in
  the test-suite repository must identify the tested SDK/Sidecar/Framework versions
  or local revisions, configuration, command and result. This is reproducible test
  attribution, not an extra verification of locally installed Framework artifacts.
- Each implementation ticket owns necessary tests and documentation even when its
  integration evidence belongs in the other repository. Assign cross-repository
  dependencies explicitly; do not postpone all testing to the final phase.
- Routine acceptance is local and deterministic, with synthetic business operations
  and no paid model calls or live hosted-service dependencies. Use real authentication,
  TLS and service boundaries. Cloud/hosted-provider checks remain separately
  authorized manual compatibility evidence, outside local release gates.

For roadmap GitHub PR #8, name tickets `PR-8.[phase].[ticket]-<slug>.md` (ticket numbering starts at 1 within each phase). Create tickets using [write_ticket](../../commands/write_ticket.md) after
the relevant phase is sufficiently specified. Apply Full/Fast/Direct eligibility
to each ticket; security, contract and lifecycle changes generally require Full.

## Outcome

Deliver the independently built Java SDK and demonstrate an isolated application's
authenticated invocation and authorized callbacks with minimal customer plumbing.

## Scope

- Implement the Phase 0 HTTP core, discovery/submission/handle/status/wait APIs,
  result fidelity and typed conversion, actionable errors and resource ownership.
- Implement all four credential strategies and shared Spring/custom issuance;
  preserve customer login and application authorization.
- Implement both callback-chain arrangements, issuer-aware principals/roles and
  optional account resolution with session/CSRF isolation and context cleanup.
- Implement necessary planned Sidecar admission/trust-management changes, optional
  JWKS endpoint, public file export and isolated signing bootstrap. Use only
  supported public Framework APIs; a missing API is an explicit owning-repository
  dependency, never permission to use internals.
- Implement the planned console trust-source UI and shared management REST behavior,
  including opaque Read/Edit/Publish tokens owned by the single administrator.
  Tokens are named, expiring, revocable, hashed and shown only at creation; protect
  client storage. Enforce live authority, interactive-only account/token-creation/
  trust changes, atomic import/publication/rollback restrictions, stale-draft checks,
  management mixed-credential rejection, browser CSRF and file-mode restrictions.
  Successful authorized Publish may change passthrough recipients without separate
  browser approval. Phase 3 adds lifecycle stress evidence, not initial enforcement.
- Support protected direct HTTP, direct HTTPS and both mTLS invocation paths with
  independently configured callbacks. No callback listener is needed for invocation
  and polling alone; client-Caddy invocation still retains its local proxy.
- Preserve token-purpose/sender-constraint rejection, unchanged credential
  passthrough, destination controls and separate management/execution access.
- Demonstrate acceptance-unknown, no submission/callback replay, retained IDs and
  resumed polling, terminal failure with completed callback effects and conversion
  failure without rerunning work.
- Preserve unchanged result text, typed envelopes, snapshot IDs, validation/problem
  details and available diagnostics, including TypeSafe conversion. Distinguish
  explicit rejection, demonstrable pre-send failure, acceptance unknown, protocol/
  polling failure, timeout/interruption and HTTP-200 execution failure. Preserve 404
  ambiguity; a stopped wait neither cancels nor resubmits work. No blind-retry advice,
  exactly-once guarantee or promised lost-ID recovery. Provide safe timing/identifier
  observability without default payload/credential logging or a complete/durable trace.

## Test ownership and completion

Focused SDK/Sidecar tests and small local smoke checks live here. The test suite
owns full isolated Compose configurations across the four strategies, both mTLS
paths and applicable direct/mixed/no-callback cases. Include a real local exchange
server and deterministic model fixtures, authorization failures, concurrent-user
isolation and existing browser-security preservation.

The [Phase 0 coverage table](2026-10-15-sdk-v1-phase-0-reference-contracts.md#roadmap-coverage-and-delivery-owners)
assigns the full roadmap scenarios. Ticket acceptance must explicitly include:

- HTTP/HTTP and HTTPS/HTTPS with all four strategies, plus both isolated mTLS paths
  for all four. Test local-boundary enforcement, server chain/hostname rejection and
  no downgrade. Focused independent settings include mTLS invocation with protected
  HTTP callbacks. No-callback/resumed polling retains all four strategies and both
  mTLS paths, omitting client Caddy only on the native path. Optional JWKS hosting
  is separate from callback ingress. Share justified component evidence rather than
  expanding every test into an exhaustive transport cross-product.
- Both callback chains with bearer Alice/session Bob, missing/invalid bearer plus
  valid session, cookie-only and bearer-only requests; no authority merging, session
  persistence/mutation or login redirects. Prove URL/method/record rules, neighboring
  browser CSRF/login behavior, JWKS separation, overlap/order diagnostics and
  sequential/concurrent/error/supported-async context cleanup.
- Token-purpose enforcement at admission and callbacks, including signed ID tokens
  with overlapping audiences where applicable, incompatible Relay/Exchange credentials,
  DPoP authentication and malformed/unknown `cnf` bindings even with valid mTLS.
  Exercise default and resolved principals, failed/unresolved lookup, invalid JWTs
  never reaching the resolver, no implicit name/email linking, multiple mapped roles
  and same-pair renewal versus changed-pair ownership. Phase 2 supplies cross-issuer
  integration where it depends on group trust support.
- Both uploaded keys and JWKS URLs, application-hosted and separately hosted JWKS,
  endpoint disabled/outbound-only operation, no-server configuration error and
  unchanged application routes. Test property-driven export and Java API/bean,
  explicit enablement, public-only snapshots, no-listener export, existing-file skip,
  write/replacement failures and no signing-state mutation. No export CLI, Maven
  plugin or customer startup hook is required.
- Positive scoped management authoring/publication, including changed passthrough
  recipients, and denials of protected trust changes for every token preset through
  direct and indirect paths. Retain lease/revision/validation checks and atomicity.
- Controlled approved-recipient reuse, wrong audience/operation/record rejection,
  blocked credential redirects and no infrastructure-introduced credential leakage
  in application/proxy logs, diagnostics or model inputs. Keep authentication
  fixtures separate from business data; do not add sanitization assertions for
  caller/callback-supplied sensitive content.
- Lost/malformed acceptance responses, explicit rejection and proven pre-send failure;
  submission and callback attempt counts through relevant clients/proxies after
  response loss; committed callback effects despite execution failure; interruption/
  failed polling/deadline expiry with resumed lookup and bounded read retries.
  Demonstrate application-owned stable operation IDs and atomic duplicate handling
  separately from transport retries and repeated skill actions. Conversion errors
  retain execution/result context without re-execution.

Complete implementation and setup documentation with evidence for the isolated
matrix and applicable Phase 0 negative scenarios. Any intentionally deferred
lifecycle behavior must name its Phase 3 owner and retain outstanding evidence;
it is not completed support. Preserve the existing test-suite embedded reference
while adding a deliberate Java-to-Sidecar SDK path.

## Dependencies and intended ticket boundaries

Depends on Phase 0 contracts. Split implementation into the HTTP core, credential
strategies/issuance, callback integration, Sidecar trust changes, isolated bootstrap
and test-suite scenarios as justified by the design. Each unit includes its own
verification/documentation and explicit cross-repository prerequisites. Phase 2
extends the integration to trusted groups; Phase 3 completes lifecycle assurance.

