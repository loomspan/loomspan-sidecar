# Execute HTTP skills through the supported SkillExecutor contract

## Development constraints

**Sidecar is in development with no implementations, deployments or customers. Destructively
replace superseded contracts, code and schemas. Do not add compatibility shims,
legacy adapters, parallel legacy APIs or migration machinery solely to preserve
obsolete development behavior.**

Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
Choose the simplest complete solution, minimize technical debt, and keep code,
tests and documentation proportional. Document any required development-data
reset and its impact; planning does not authorize deleting data.

Use only the closed supported `ai.loomspan.api` surface in application and test
code, with ArchUnit enforcement against `ai.loomspan.internal..` and
`ai.loomspan.autoconfigure..`. The accepted replacement is an annotated
`@SkillExecutor` bean method, not the removed REST SPI, a new SPI, bean replacement
or reflection into Framework internals. Sidecar hosts no Java `@SkillMethod`
skills. Standard Spring APIs are allowed. A concrete missing public capability
requires explicit Framework discussion/planning, not an internal dependency.

Develop against the locally installed
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.9-SNAPSHOT` and matching source
at `C:/opendev/code/loomspan-framework`. The developer installs Framework changes;
retest affected Sidecar behavior afterward. Do not rebuild Framework in Sidecar's
normal build/CI, introduce a snapshot repository, or add artifact/source-revision
verification machinery. The separately installed old documentation skill is stale.
Defer Framework beta.9 publication until the complete PR 9 integration effort is
proven locally. After publication, pin the released dependency for Sidecar's final
build/tests and final commit; hosted CI is deferred until publication. These
tickets do not authorize publishing releases. Sidecar remains independently
versioned at `1.0.0-beta.4-SNAPSHOT`, with planned release `1.0.0-beta.4`.

Each implementation unit owns its tests and documentation. Product documentation
and packaged skills describe current supported behavior, without migration recipes,
upgrade histories, PR tracking or future-delivery promises. Keep intentional-break
assessments and transitional limitations in internal work artifacts.

## Outcome

Replace the removed REST-handler architecture with a complete HTTP executor:
authors declare explicit request mappings in each skill, select shared targets,
and receive faithful bounded results or deliberate application-error feedback.
Deliver request construction, actual transport and resource ownership together;
durable management and browser authoring follow in PR 9.2.

## Requirements

### Request configuration and construction

- Define the Sidecar HTTP operation/target contract consumed from
  `SkillExecutorInvocation.skillConfig()` and `executorConfig()`. Skills select
  an HTTP executor and target; targets reside under the selected subtree of
  `loomspan.executor-config.<namespace>`. Framework treats these maps as opaque
  application data. Choose final annotation/configuration naming during planning;
  do not invent general namespace-management features.
- Use explicit operation fields for target, method, path, path/query mappings,
  optional body selection and recoverable status selection. Existing example
  field names are `target`, `method`, `path`, `pathParams`, `queryParams`,
  `body` and `recoverableStatuses`. Stabilize and document one coherent schema.
- Support GET, POST, PUT, PATCH and DELETE only. Reject a configured body on GET.
  Other supported methods allow an explicit body; omission means no body.
- All input references use nonempty JSON Pointers starting with `/`, including
  top-level fields. Support standard escaping, nested objects and array indexing;
  no slashless shorthand, wildcards, multiple matches or expression evaluation.
  Prefer an existing pointer implementation rather than a new language.
- Path mappings select non-null, nonempty string, number or boolean values.
  Missing/out-of-range values, objects and arrays fail before dispatch. Every
  placeholder has a mapping and every mapping has a placeholder. Placeholders
  occupy complete path segments; URL-encode original input values.
- Reject path values containing slash/backslash, dot or dot-dot segments and
  encoded separator/traversal forms that could bypass those checks. Configured
  paths also reject traversal. Ordinary spaces, Unicode and punctuation are
  encoded. Do not require callers to pre-encode values.
- The target owns HTTP/HTTPS scheme, host, port and optional base-path prefix.
  Append the skill's slash-prefixed path with one joining slash, preserving the
  base prefix. Skills cannot replace the host or provide absolute URLs. Neither
  base URL nor skill path contains query text or a fragment.
- Query mappings omit missing/out-of-range and null values, preserve empty strings
  as empty values, and preserve zero/false. Arrays of scalars use repeated names
  in array order; empty arrays omit the parameter. Reject objects, nested arrays
  and null array elements. No comma-separated or JSON query encoding alternatives.
  Required presence belongs in the skill's input schema.
- `body: {source: input}` serializes the entire input map without removing values
  also mapped to path/query. `body: {source: field, field: /something/changes}`
  serializes the selected value, preserving objects, arrays, scalars and explicit
  null. Missing selection fails. No body-field assembly, templates, merging,
  defaults, interpolation or transformations.
- Shared targets support fixed headers only; no per-skill or input-to-header
  mappings. Authentication remains separate from ordinary headers. Define
  credential/configuration representation coherently with existing Sidecar
  facilities; transport dispatch and credential use are included below.
- Target configuration includes finite positive connection/read timeouts and
  response-size limits, inherited without per-skill overrides. Choose documented
  defaults during planning; 5s/60s/1MB were examples, not required defaults.
- `recoverableStatuses` is optional and defaults to none; only 4xx statuses other
  than 401/403 may be designated. Actual error handling is included below.
- Provide reusable Sidecar-owned configuration and actual-input validation for
  startup, management and invocation consumers. Configuration errors do not
  require network calls; input-dependent errors are caught before dispatch.
  Do not duplicate Framework skill/schema/RBAC validation or add Framework hooks.
- Final header syntax/conflict rules, defaults and credential-reference spelling
  are planning details, not permission to add features. The earlier proposed
  reserved-header list was not accepted as a product requirement. Resolve routine
  validation from existing responsibilities; raise genuinely material new choices.

### Dispatch, authentication, responses and resource ownership

- Replace the generic REST-handler invocation path with a Spring bean method
  annotated `@SkillExecutor`, consuming the request/configuration contract above.
  Use captured `input()`, `skillConfig()` and `executorConfig()` directly.
  No skill-name route lookup, latest-config lookup or parallel legacy REST path.
- Target authentication has exactly three initial modes: none, fixed service
  credential headers, and original caller JWT passthrough. No automatic fallback.
  Skill input cannot select credentials or modify the mode.
- None sends no authentication credential. Fixed credentials use Sidecar's
  credential facilities and represent the configured service identity. No OAuth
  acquisition, refresh or exchange. Do not add a special credential channel to
  Framework invocation or assume Framework resolves opaque executor values.
- Passthrough reads the captured Spring Security JWT authentication, checks expiry
  immediately before dispatch, and sends the original bearer token. Missing or
  expired authentication fails. Do not mint, refresh, replace or infer caller
  identity from inputs. Preserve existing approved-recipient and authorization
  boundaries; the destination independently validates audience/trust/roles/records.
- Apply target fixed headers and authentication per request, not through mutable
  shared defaults. Keep infrastructure credentials out of business payloads,
  generated diagnostics and logs. Do not redact or rewrite application content.
- Support ordinary HTTP and HTTPS using built-in certificate and hostname
  validation. No validation bypass or custom executor TLS configuration system.
  Caddy owns mTLS; remove superseded executor client-certificate/SSL-bundle behavior,
  without removing unrelated standard Boot server TLS facilities.
- Make one HTTP attempt, with automatic retries, redirects and cookie
  storage/replay disabled. A timeout/lost response does not prove no remote effect.
- Enforce target connection/read timeouts and response-size limits defined above.
  Bound reading for successful and error bodies; an exceeded limit fails, never
  returns truncated success. Read timeout is not a new total-execution deadline.
- Prefer one reusable application-owned client/pool. Verify per-target captured
  timeouts can coexist under concurrency without shared-setting mutation.
  Separate clients are allowed only when evidence shows they are necessary.
  Framework owns generation capture/lifetime; do not recreate it in Sidecar.
- Keep required clients alive while Framework handles admitted work at shutdown,
  then close them without a second drain period. If resources require generation
  ownership, use the supported early-registered retirement listener, promptly
  offload cleanup, and also own shutdown cleanup because notifications may be absent.
  Do not conflate executor configuration generation, skill generation and durable IDs.
- All 2xx responses succeed, returning decoded body text unchanged; empty body is
  an empty string. No status/header envelope or JSON parsing/reserialization.
- Decode application/json and application/*+json as UTF-8; reject declared
  non-UTF-8 JSON charsets. Decode text/* with declared charset or UTF-8 default.
  Missing content type uses UTF-8 without sniffing. Other content types fail.
  Empty bodies return empty text regardless of type. Invalid bytes/unsupported
  charsets fail, not replacement characters. No trimming or newline normalization.
- Failures are terminal by default using `SkillException`. Connection, timeout,
  credential, invalid-config, response-limit and decoding failures remain terminal.
  Redirects, 5xx, 401 and 403 remain terminal.
- Only an operation's listed allowed 4xx `recoverableStatuses` produces
  `RecoverableSkillException`, and only when the response passes size/decoding
  checks. Omitted configuration means none. No special Loomspan response envelope.
- For terminal and recoverable HTTP errors with acceptable decoded content, use
  a short HTTP status line followed by the complete unchanged body. Empty body
  means status only. Do not append request headers, credentials or input bodies.
  Recoverable messages can reach the model; application content ownership remains
  with the destination. Do not promise recovery for direct calls or step planning:
  current Framework propagates direct failures and terminates step-plan tool failure;
  standard model tool calling can consume designated recoverable feedback.

## Acceptance criteria

- [ ] Representative GET, bodyless POST, whole-input POST and nested-field body
  cases produce the intended method, URL and JSON payload using production code.
- [ ] All five methods and the GET/body restriction are enforced; omitted body
  stays absent and explicit JSON null remains distinguishable from absence.
- [ ] Pointer escaping/indexing, missing selections and every path/query value
  rule above are covered, including ordered arrays, empty values, zero and false.
- [ ] Base-prefix joining and URL encoding preserve the intended destination;
  host replacement, traversal and malformed mappings fail before any dispatch.
- [ ] Invalid target/operation settings and recoverable-status declarations fail
  clearly without network access. Required-input schema ownership stays with
  Framework; Sidecar validates HTTP-specific use of actual values.
- [ ] The final schema/defaults are documented and reusable by PR 9.2. Tests
  and documentation for this unit are supplied rather than deferred to PR 9.3.
- [ ] No legacy route adapter, alternative mapping language or unsupported
  Framework dependency is introduced; any transitional blockers are recorded.

- [ ] Public-API Sidecar integration executes YAML HTTP skills through the annotated
  bean with the explicit mappings above and no legacy handler or route lookup.
- [ ] All three auth modes work; missing/expired caller credentials fail before
  dispatch, original token fidelity holds, and concurrent callers do not share
  credentials, fixed headers or cookie session state.
- [ ] HTTP/HTTPS calls honor target limits and server validation; retries,
  redirects and cookie replay are demonstrably disabled. Oversized bodies fail
  without returning partial success or unbounded cleanup reading.
- [ ] Success, empty-body, JSON/text/missing-type decoding and malformed/unsupported
  content follow the agreed fidelity rules, including error responses.
- [ ] Listed versus unlisted 4xx, forbidden auth statuses, redirects, 5xx and
  transport failures produce the agreed terminal/recoverable exceptions and
  complete status-plus-body messages without generated credential disclosure.
- [ ] Sidecar tests exercise direct invocation and standard model-tool feedback
  through public APIs with deterministic local fixtures, without paid model calls.
- [ ] Evidence supports the selected client ownership strategy, concurrent target
  timeout isolation, captured settings and Framework-aligned shutdown cleanup.
- [ ] Unit documentation/tests and affected runtime guidance are updated; retained
  downstream startup/management blockers are explicit, not hidden with shims.

## Context

- External work item: **PR 9**, slice **9.1**. Slice labels subdivide PR 9; they
  are not separate GitHub PR numbers.
- Sequence: **9.1 -> 9.2 -> 9.3**. Next:
  [configuration management and authoring](./2026-11-02-pr-9-2-http-configuration-management-and-authoring.md).
- Governing decisions: [HTTP executor design notes](../phases/http-skill-executor-design-notes.md).
  This ticket carries the accepted runtime behavior so a fresh implementation
  context does not need the chat.
- Existing GenericRestSkillHandler, RestRouteLoader, RestTargetClients and
  BoundedRestResponse are evidence, not mandated architecture. Remove their
  superseded behavior; old tests do not create compatibility requirements.
- PR 9.2 owns complete startup/publication/restoration integration, durable
  snapshot ownership, UI, export/import and consolidated authoring guidance.
  This ticket owns the reusable HTTP validation and actual client lifetime;
  document any temporary startup/management dependency instead of adapting the
  old routes format.
- The neighboring test suite is one client, not the primary design goal. Its
  flat lookup inputs and approval envelope justify whole-input body selection
  but do not constrain general HTTP behavior.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change spans HTTP contracts, authentication, Framework invocation, concurrent client ownership and shutdown.
- **Reassessment triggers:** Validate scope and dependencies against the checkout.
  Bring material product changes or missing public Framework capabilities back to
  the developer. Do not silently broaden the work into Framework development.
  This recommendation is advisory, not authorization to run the pipeline.

## Pipeline notes

- The developer will commit locally between tickets without syncing. Intermediate
  builds and application startup may be broken until the last ticket; this is
  explicitly accepted. Do not add shims, temporary Framework stubs, disabled tests
  or dual contracts merely to make an intermediate ticket green.
- Each ticket still supplies its own tests, documentation and independent review.
  Run meaningful checks available at that stage. Record every blocked check,
  exact cause and downstream owner in its verification artifact. A downstream
  build blocker is not evidence that a test passed.
- PR 9.3 must reconcile all deferred checks and leave the complete build and
  required local suites passing. Intermediate breakage does not permit reduced
  assurance at completion. These tickets do not instruct agents to commit, sync,
  push or publish; the developer owns those actions.
