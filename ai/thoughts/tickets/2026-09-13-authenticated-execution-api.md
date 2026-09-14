# Execute skills asynchronously through an authenticated, owner-scoped HTTP API

## Outcome

Any HTTP client can discover mounted skills, submit an execution using a verified
bearer JWT, and poll its own execution for the result or failure. Preserve the
caller's trusted identity through queued and nested work, bound admission and
retention, and stop dispatch promptly at shutdown. Deliver SC2 and SC3 together
on the completed SC1 scaffold, with application tests and user documentation.

## Requirements

### HTTP and framework execution

- Provide these JWT-authenticated routes:

  ```text
  GET  /v1/skills
  GET  /v1/skills/{name}
  POST /v1/skills/{name}/executions
  GET  /v1/executions/{id}
  ```

- Catalog responses use public `SkillCatalog` descriptors, exposing name,
  description, kind and input schema for every registered skill to every
  authenticated caller. Do not filter catalog visibility by role. Unknown
  skill names return `404`; execution authorization occurs through validation.
- POST requires a JSON object parsed as `Map<String,Object>`. Missing, null,
  malformed and non-object bodies return `400`; an empty object undergoes normal
  skill validation. Enforce `max-input-size` while reading, before parsing or
  framework validation: exceeding it returns `413`, equality is allowed.
- On the request thread, resolve the skill, call the Map overload of
  `SkillTemplate.validate`, measure the validated input map, then reserve
  capacity/create the record and dispatch or queue it. Validation issues return
  `400` with issues; access denial returns `403`; capacity rejection returns
  `429`. Rejections leave no accepted record or reservation. Validation returns
  void; it neither normalizes input nor reserves framework admission.
- Accepted POSTs return `202` with execution id and `Location` without waiting
  for completion. No synchronous mode or `wait` parameter. Client disconnect
  does not cancel accepted work. Workers call the public Map overload of
  `SkillTemplate.invoke(name, input, observer)` with invocation-time checks intact.
- Execution representation: `id`, `skillName`, `status`
  (`QUEUED|RUNNING|COMPLETED|FAILED`), `createdAt`, `completedAt`, `result`,
  `failure`, and selected `events`. Result is the framework's unchanged string.
  Failure includes `kind` (`INPUT_VALIDATION`, `ACCESS_DENIED`, `SKILL_FAILURE`),
  message and validation issues when applicable. Classify the primary exception
  thrown by the facade, including nested failures; do not invent precedence from
  diagnostic events or unwrap arbitrary causes to recover another message.
- GET returns `200` for any known, owned execution, including `FAILED`.
  Unknown, expired or foreign executions return `404`. HTTP `500` describes
  failure to handle the HTTP request, not a successfully retrieved failed skill.
  HTTP errors use RFC 9457 `application/problem+json`; execution failures use
  the execution representation. Preserve framework `SkillException` messages;
  ordinary failure envelopes omit stack traces. Do not add sanitization of
  arbitrary inputs, results, messages or diagnostic history.

### Authentication and ownership

- Use Spring Security OAuth2 resource-server verification producing
  `JwtAuthenticationToken`. JWT is the only inbound execution/catalog credential.
  Missing or invalid bearer credentials return `401`. No temporary production
  authentication path, inbound API keys, sessions or CSRF. Responses carry
  `Cache-Control: no-store`.
- Bind Sidecar JWT configuration explicitly to the decoder and validators.
  Support issuer discovery, explicit `jwk-set-uri`, and public-key verification.
  Every mode validates signature, trusted issuer, expected audience and lifetime,
  requiring nonblank `iss` and `sub` plus expiration. Explicit keys do not bypass
  issuer validation. Invalid or ambiguous key-source configuration fails startup;
  clock skew is configurable. Exact binding details belong to implementation
  planning, not a new authentication-policy decision.
- Under `loomspan-sidecar.auth.jwt`, configure `issuer-uri`, `audience`,
  `roles-claim` (default `roles`) and `role-prefix` (default `ROLE_`). Roles are
  asserted by the trusted issuer; Sidecar reads the configured claim and applies
  the authority prefix. It does not assign users roles or introduce arbitrary
  role-name translation. Align the converter prefix with Spring's
  `GrantedAuthorityDefaults` so framework `rbac_roles` checks agree at validation
  and invocation. Preserve the framework's existing role semantics.
- Capture one immutable owner value with explicit issuer and subject fields at
  admission and compare both on reads. Do not join them into a string or retain
  a token as the owner. A renewed token for the same pair retains ownership;
  a different pair does not. Use this owner representation from the outset.
- Propagate captured authentication onto the invoking worker through standard
  Spring Security facilities or equivalent, including after queueing. Nested
  invocation must expose the original JWT on the thread executing a test
  `RestSkillHandler`. Never put trusted identity or credentials into model inputs.
  Release the worker's captured context when it exits without leaking identity
  into subsequent work.
- Identity and roles accepted at admission remain valid for local execution even
  if the token subsequently expires. No expiry recheck at worker handoff, token
  refresh, exchange or minting. Framework access checks still apply. SC4 will
  forward the same token; each target independently checks it and may reject an
  expired token. Document queue time, audience and lifetime implications, and
  backend-issued access tokens for session-based hosts; cookies and OIDC ID
  tokens are not assumed to be suitable API credentials.
- Preserve separate-port Actuator health/readiness exemption without exposing
  unrelated management endpoints. All `/v1/**` routes require JWT. If Console
  observability is enabled, permit its reserved namespace through application
  security to the framework's own API-key filter. Keep operator authentication
  independent. Document inbound mTLS using standard `server.ssl.client-auth`;
  it is transport protection, not X.509 identity mapping.

### Admission, retention and diagnostics

- Use these defaults under `loomspan-sidecar.executions`; invalid limits/TTL
  fail startup:

  ```yaml
  max-input-size: 1MB
  max-retained: 1000
  completed-ttl: 15m
  max-concurrent: 32
  max-queued: 128
  max-queued-input-size: 64MB
  diagnostics: NEVER
  ```

- One admission/accounting owner coordinates one ordinary bounded executor work
  queue and one bounded `ConcurrentHashMap` record store. No staging queue,
  duplicate capacity authorities, separate result/history store, or custom
  scheduling/cache subsystem. Keep admission locks out of invocation, observer
  delivery and response serialization.
- Worker saturation queues work while waiting capacity remains. Waiting records
  are `QUEUED`, becoming `RUNNING` when taken by a worker. Reserve queue count
  and queued bytes together; exceeding either returns `429`. Direct dispatch
  consumes no waiting-queue budget. Measure the JSON map's UTF-8 byte size once
  after validation, storing its size without retaining an extra serialized copy
  solely for accounting. Allow a queued-byte sum equal to the limit.
- Release queue count/bytes exactly once on handoff or removal without execution.
  Roll back record and reservations on dispatch failure. Worker capacity is
  released on worker exit, not just when a timeout is reported. `max-concurrent`
  bounds Sidecar facade calls, not nested or lingering framework operations.
- `max-retained` counts all accepted records, including active and terminal work;
  full retained capacity returns `429`. Completion starts TTL. Use one simple
  expiration sweep, enforce TTL on reads, and reclaim expired capacity during
  admission. Reads never extend TTL. Expiry removes the entire record even if
  nobody polls it; restart loses the store. No durable storage or recovery.
- Queued/running tasks own input and authentication. Completed records retain
  only owner, status/timestamps, result or failure and selected diagnostics;
  release task-held input/context on worker exit. Selected events may themselves
  contain business input data. These limits do not bound total heap, result size,
  diagnostic size or references retained by uncooperative framework work.
- `diagnostics` supports `NEVER` (default: retain/return no events), `ONERROR`
  (failed executions only), and `ALWAYS` (both terminal outcomes), independently
  of framework trace persistence. Publish terminal outcome and selected available
  observer events atomically in the same record after `invoke` returns or throws.
  They are retrieved and expire together; no separate history endpoint or state.
- Public observer delivery is synchronous within invocation and at most once.
  Some failures have no callback; finish them with their primary failure without
  waiting or reconstructing history from internal/Console APIs. Preserve all
  selected available events, including mixed child failures, without promising a
  complete raw trace. Pre-dispatch errors have no execution history. Never parse,
  transform or truncate result text, or omit/truncate selected events due to size.

### Shutdown and delivery boundary

- Implement the independent admission/dispatch shutdown gates in this ticket.
  At owning-context close, immediately lower readiness, reject new execution
  admission with `503`, prevent new facade dispatch, and discard queued work
  directly, releasing reservations and input/security references. Synchronize
  with dequeue so no new invocation begins after the dispatch gate closes.
- Use an independent `ContextClosedEvent` listener that returns promptly, opts
  out of asynchronous event execution and ignores other contexts' close events.
  It must not wait, call the framework, rely on framework listener order, drain
  the queue through Loomspan, or introduce another shutdown timer.
- Already-dispatched work belongs to the framework's mission timeouts and
  `loomspan.shutdown.timeout` (default `30s`). Keep waiting caller workers alive
  through framework completion/cutoff, then tear down resources with bounded
  cleanup. No early interruption or unbounded executor `close()`. Do not depend
  on internal bean names, numeric lifecycle phases or replacement beans. SC5
  adds packaged client/caller lifecycle proof; this ticket supplies application
  proof of its own gates, accounting and caller behavior.
- Reuse one Sidecar application fixture and test-only REST handlers with local
  deterministic JWT/model fixtures. Prove Sidecar integration using only public
  Loomspan APIs; do not infer completion from framework tests. Update README
  with routes, JWT/key-source/role configuration, limits, polling/failures,
  ownership, diagnostics/data exposure, shutdown/restart behavior, Console
  coexistence and phase limits. Examples require no external provider account.

## Acceptance criteria

- [x] All four routes work with verified JWTs. Catalog exposes all registered
  descriptors, including role-restricted skills; unknown names return `404`.
  Missing/invalid credentials return `401`, invalid input `400` with issues where
  applicable, denied roles `403`, and oversize input `413` before parsing or
  validation. Invalid bodies never create records; byte-limit equality passes.
- [x] Context tests exercise discovery, explicit JWKS and public-key binding;
  signature, issuer, audience, required claims and lifetime checks apply in each
  mode. Invalid/ambiguous settings fail startup; configured clock skew and custom
  role claim/prefix work at validation and invocation.
- [x] Accepted POST returns `202`, id and `Location` without waiting. Polling
  reaches the exact result or classified failure, with `200` for owned failures.
  HTTP errors are problem documents; framework messages survive without ordinary
  stack traces. Client disconnect does not cancel accepted work.
- [x] Unknown/expired/foreign execution reads return `404`. Renewed JWTs with
  the same issuer/subject retain access; differences in either field do not.
  Queued and nested execution preserve identity, roles and the original token
  in a test handler, with no cross-request context leakage. Expiry after admission
  does not block local work or bypass framework authorization.
- [x] Worker saturation yields `QUEUED` then `RUNNING`. Concurrent submissions
  cannot exceed worker, queue-count, queued-byte or retained-record limits.
  Independent capacity boundaries return `429` without orphan records; UTF-8
  multibyte accounting and equality/excess behave correctly. Handoff, removal
  and dispatch failure release reservations exactly once; worker capacity remains
  occupied until exit.
- [x] Invalid execution configuration fails startup. Completion-based expiry
  removes whole records and releases capacity without reads; reads do not extend
  retention or expose partial terminal data. Completed records and exited tasks
  release their invocation input/security references while ownership still works.
- [x] All diagnostic modes behave as specified for success and failure. Selected
  available history and terminal outcome publish together and expire together.
  Mixed nested failures preserve the facade's primary classification and selected
  history; no-callback failures finish without waiting or invented events. Large
  results and selected events remain intact without Sidecar size truncation.
- [x] Application shutdown immediately closes admission/dispatch and readiness,
  including dequeue races, and releases discarded queue accounting/references.
  Management-context close events do not close application gates; asynchronous
  event multicasting does not delay the owning-context gate. No listener-order
  dependency, queue drain, second grace timer, early caller interruption or
  unbounded worker teardown is introduced.
- [x] JWT security coexists with opt-in Console API-key security and separate-port
  health/readiness; unrelated management endpoints remain unexposed. Responses,
  including errors, carry `Cache-Control: no-store`; the API remains stateless.
- [x] Sidecar's wrapper verification and public-API architecture checks pass.
  Tests use no Java skills or framework internals. Documentation covers the
  implemented configuration/behavior and local examples, including JWT/mTLS,
  token lifetime, data exposure, memory limits and restart-only activation;
  it does not claim the SC4 handler or SC5 packaging is delivered.

## Context

[SC2](../phases/phase-sc2.md) and [SC3](../phases/phase-sc3.md) own this combined
delivery unit, with the [handoff](../beta4-handoff.md) and
[design lens](../design-lens.md) supplying cross-ticket constraints.
[SC1](2026-09-13-sidecar-scaffold.md) is locally complete at `e3c3f58`.
The shutdown gate boundary is shared with [SC5](../phases/phase-sc5.md): this
ticket implements it; SC5 proves the packaged resource lifecycle.

Use only supported `ai.loomspan.api` Java types, including the named
`RestSkillHandler` SPI, plus standard Spring APIs. No new framework contracts,
internal imports, reflection bypasses or undocumented configuration. Framework
gaps must be resolved there. Sidecar hosts no Java `@SkillMethod` skills.
Use Boot's Jackson 3 and existing Java 21 / Boot 4.1 platform. Framework keys
stay under documented `loomspan.*`, Sidecar keys under `loomspan-sidecar.*`,
and standard Boot settings in their standard namespaces; secrets use environment
variables. Keep skills/routes startup-only with restart activation.

Consume the locally installed
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` alongside source
and documentation in `C:/opendev/code/loomspan-framework`. The developer installs
after framework changes so the artifact and source match; no separate provenance
verification is required for ordinary development. Rerun affected Sidecar checks
after changes. Do not rebuild framework source in Sidecar builds/CI or add a
snapshot repository. SC5 snapshot integration precedes framework publication;
Sidecar's released dependency switch, final hosted CI and release remain later
gates, not prerequisites for this ticket's local acceptance.

Excluded: production outbound REST handler/route parsing and callback-host proof
(SC4); image, packaged lifecycle suite and release (SC5); API keys for execution,
token issuance/refresh/exchange, synchronous execution, cancellation API,
streaming, retries, durable storage, hot reload, catalog role filtering and new
framework SPIs. Exact decoder binding, DTO organization and executor wiring are
implementation choices within these constraints, not reasons to reopen the
agreed product behavior. Choose the simplest solution satisfying the requirements.

## Execution profile

- **Recommended:** full
- **Confidence:** high
- **Rationale:** This unit introduces authentication/authorization boundaries,
  an HTTP representation, concurrent admission and retention, identity propagation
  and shutdown behavior. It requires research, planning and independent review
  despite settled product requirements.
- **Reassessment triggers:** Reassess if equivalent implementation has already
  landed, scope changes, or framework contracts change. Security, concurrency,
  lifecycle, public-surface and verification requirements remain binding.
