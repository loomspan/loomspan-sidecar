# Invoke configured REST services from mounted skills with verified caller identity

## Outcome

Sidecar users can declare REST skills in mounted YAML and map them to HTTP
services without writing Java. One production `RestSkillHandler` executes those
calls with fixed input binding, configured authentication, TLS and bounded
transport behavior. The existing authenticated asynchronous API can execute a
YAML planner whose REST leaf calls a host that verifies the original caller's
JWT and restores its identity and roles.

Deliver SC4 as one complete handler unit, including startup validation, all
authentication modes, transport bounds, integration proof and documentation.
Do not defer security or transport correctness to a later hardening ticket.

## Requirements

### Route file and startup activation

- Register exactly one production `RestSkillHandler` bean, always present even
  when no REST skills are registered. Reuse the supported framework SPI and
  public catalog; Sidecar hosts no Java `@SkillMethod` skills.
- Load `loomspan-sidecar.rest-routes-location`, default
  `file:/sidecar/rest-routes.yaml`, once at startup into immutable named target
  and skill-name-keyed route maps. Support location overrides. Keep this file
  outside the framework's default `/sidecar/skills/` scan; do not add scanner
  exclusions or parse route declarations as skill manifests.
- The file is the single source of target and route declarations. Use this
  contract; the names and endpoints below are illustrative:

  ```yaml
  targets:
    expense-service:
      base-url: https://expenses.internal/api
      auth:
        mode: caller-passthrough
      ssl-bundle: expenses-mtls
      connect-timeout: 5s
      read-timeout: 30s
      max-response-size: 1MB
    customer-service:
      base-url: https://customers.internal/v1
      auth:
        mode: static
        headers:
          X-Service-Key: ${CUSTOMER_SERVICE_KEY}
      connect-timeout: 5s
      read-timeout: 30s
      max-response-size: 1MB
  routes:
    expenseLookup:
      target: expense-service
      method: GET
      path: /expenses/{category}
    customerLookup:
      target: customer-service
      method: POST
      path: /customers/{id}
  ```

- Multiple routes may share a target, and routes may use different targets.
  Targets own base URL, authentication, optional SSL bundle reference, connect
  and read timeouts, and response-size limit. SSL bundle definitions remain in
  standard Spring Boot configuration. No inline `loomspan-sidecar.rest-targets`
  or `rest-skills` maps, merge/precedence behavior or compatibility aliases.
- A missing/unreadable file, malformed YAML, duplicate target or route key,
  unknown field or invalid configuration fails startup with useful file/key
  diagnostics. An explicit document with `targets: {}` and `routes: {}` is valid
  when no REST skills exist; the file is still required in that case.
- Resolve environment-backed `${NAME}` placeholders in parsed strings using
  Spring's existing placeholder facilities before validation. An unresolved
  required placeholder fails startup naming file, field and placeholder without
  printing resolved secrets. No expression language or custom secret provider.
- Validate HTTP(S) target URLs, positive timeouts/response limits, supported
  authentication-mode/header combinations and referenced SSL bundles locally.
  Only GET and POST routes are supported. Paths start with `/`, contain no scheme
  or authority, and cannot start with `//`. Reject invalid declarations before
  readiness; startup validation does not contact target services to check
  availability, credentials or endpoint existence.
- Every registered REST skill must have a route; every route must name a
  registered REST skill and a defined target. Construct the handler from the
  file without depending on `SkillCatalog`, then validate correspondence against
  the completed public catalog through standard Spring startup lifecycle. Avoid
  handler/catalog/registration construction cycles, internal registrar calls,
  readiness polling, a second catalog snapshot or a custom lifecycle system.
- Skills and routes activate only on startup. Disk edits leave the running
  catalog/routing unchanged; restart applies both. Invalid edits fail the next
  startup before readiness. No reload API, file watcher or catalog versioning.

### Fixed input binding and outbound authentication

- Bind `{name}` path variables from same-named inputs. Path values and remaining
  GET query values must be non-null strings, numbers or booleans. Missing path
  inputs, nulls, arrays and objects fail visibly before any outbound call.
  Encode substitutions as individual path segments and query values correctly.
- Remaining POST inputs form a JSON object body, including an empty object when
  all inputs were consumed by path variables. Preserve schema-permitted nulls
  and nested JSON. No implicit omission, flattening or general templating.
- Use URI facilities to join base URL and route while preserving the base path
  with or without trailing slash: base `https://expenses.internal/api` plus
  `/expenses/{category}` must remain under `/api/expenses/` on that host.
  Normalize the joining slash; inputs cannot replace scheme/authority or escape
  the configured base path. Handle Unicode, reserved characters, dot segments
  and encoded traversal according to that invariant.
- Transport only JSON-compatible input values. If framework reference resolution
  supplies a Spring `Resource`, file, stream or other non-JSON value, fail before
  the outbound call, including when nested in a POST body. Do not serialize
  handle fields/content or introduce multipart, base64 conversion or uploads.
- Send `Accept: application/json, text/*`. Support target auth modes `none`,
  `static` and `caller-passthrough`; configured auth headers belong to static
  mode only. Inputs never become headers. Outbound none/static modes do not
  alter the JWT-only inbound API.
- Passthrough sends `Authorization: Bearer <original-token>` obtained from
  `JwtAuthenticationToken` in `SecurityContextHolder` on the handler thread.
  Absence of JWT is a visible skill failure, including defensive direct-handler
  tests. Never inject credentials into model inputs or mint/refresh/exchange
  tokens. Preserve the existing queued/nested authentication propagation.
- The callback host independently verifies the same JWT, including audience and
  lifetime, and establishes the original issuer, subject and roles. Local work
  accepted before token expiry continues with its captured identity; a token
  expiring during queueing/execution can fail at the callback host and surface
  as a REST skill failure. Do not add a worker-handoff expiry check.

### Transport, results and lifecycle

- Use a Spring `RestClient` per target with that target's timeouts and optional
  Boot SSL bundle, including mTLS. Verify effective timeout, TLS and redirect
  behavior with the project's dependency versions. No retries; do not follow
  redirects, including same-host or cross-host redirects.
- Enforce the target response-byte cap while reading the stream, before full
  materialization. Equality is allowed; excess fails. Avoid default error-body
  buffering that could consume an unbounded non-2xx body.
- Any bodyless 2xx, including `204` without Content-Type, returns an empty string.
  A nonempty 2xx requires a JSON or `text/*` content type and returns its decoded
  text without parsing or transforming JSON. Specify and document concrete
  media-type/charset handling using the client facilities during implementation.
  Nonempty missing or unsupported Content-Type is a failure.
- Non-2xx, connection failure, timeout, oversize body or unsupported nonempty
  content type yields a skill failure. Deliberately constructed HTTP diagnostics
  are bounded and use target name, status code and content type when available;
  never include the response body in the exception message. Use supported
  `SkillException` where those diagnostics must survive to the execution API.
  Do not unwrap arbitrary causes to bypass framework failure handling.
- Preserve SC2's failure contract: polling an owned failed execution returns
  its normal representation with `FAILED` and `failure.kind=SKILL_FAILURE` for
  handler transport failures. The handler's bounded messages do not imply that
  arbitrary framework messages/results/diagnostics are sanitized. Preserve
  selected available observer events through the existing diagnostics policy.
- Integrate client ownership with existing shutdown gates: keep handler clients
  usable through framework completion/cutoff, then release resources with bounded
  teardown. No early client close, second drain period or unbounded executor wait.
  Use standard Spring lifecycle and public contracts without dependencies on
  framework internal bean names, numeric phases or listener priorities. SC5 owns
  exhaustive packaged resource-lifecycle proof; SC4 must supply a correct client
  lifecycle and application evidence for the resources it introduces.
- Reuse the common Sidecar application fixture for HTTP/JWT/queue/route proof.
  Focused local stub-server tests own binding/transport boundaries; deterministic
  local JWT and model fixtures support planner integration without external
  provider accounts. WireMock or MockWebServer are suggestions, not requirements.
- Update README/configuration examples with the unified file, location override,
  required empty-file case, environment secrets, supported methods/binding,
  authentication, TLS, response limits/media types and restart-only activation.
  Explain host token verification, suitable audiences, queue-inclusive lifetime
  and absence of refresh. Keep configuration ownership and existing API behavior
  intact; do not claim packaging or release is complete.

## Acceptance criteria

- [x] Application starts with registered REST skills, one production handler
  and the completed public catalog without a dependency cycle. Every missing or
  mismatched mapping, unknown skill/target, route naming a non-REST skill, invalid
  method/path/target setting or missing SSL bundle fails before readiness. No
  remote target call is made during startup validation.
- [x] Default and overridden route-file locations load correctly; missing,
  unreadable or malformed files, duplicate target/route keys and unknown fields
  fail with file/key diagnostics. Explicit empty maps work without REST skills.
  Shared-target and multiple-target routes work. Environment substitutions resolve
  before validation; unresolved required placeholders fail without secret leakage.
- [x] GET binds encoded path segments and remaining scalar query inputs; POST
  sends remaining JSON, preserving permitted nulls/nested values and `{}`. Missing
  or invalid path/query values and non-JSON transport values, including nested
  handles, fail before any outbound call.
- [x] Base-path preservation works with both trailing-slash forms, Unicode and
  reserved characters. Dot segments, encoded traversal and attempted authority
  replacement cannot escape the configured target/base path. Invalid inputs
  make no outbound call.
- [x] None/static/passthrough authentication behaves independently per target;
  static headers and the agreed Accept header reach the stub. Inputs cannot
  supply headers. Passthrough sends the original JWT and fails without one.
- [x] Per-target connect/read timeouts and SSL settings take effect. A host
  requiring client authentication succeeds with the correct bundle and fails
  without it; target TLS settings remain isolated. Redirects are not followed
  and transient errors do not cause retries.
- [x] Streaming response-cap equality succeeds and excess fails without full
  buffering, including error-response handling. Bodyless 2xx returns empty text;
  nonempty JSON/text returns decoded text according to documented charset rules;
  nonempty missing/unsupported content type fails.
- [x] Non-2xx, connection failure, timeout, response oversize and unsupported
  content type produce visible `SKILL_FAILURE` outcomes with bounded diagnostics
  and no response-body leakage in the message. Existing failure polling and
  diagnostic selection remain intact.
- [x] End to end, JWT caller -> asynchronous Sidecar POST -> YAML planner -> REST
  leaf -> local host verifies the same JWT and restores identity/roles, including
  work that waited in the queue. Expiry before callback is rejected by the host
  and surfaces as a skill failure without Sidecar token refresh or local expiry
  recheck. This uses the production handler, not just a test handler.
- [x] Editing a skill and its route does not change a running instance; restart
  activates both, while invalid edits prevent readiness. Client lifecycle tests
  show no early teardown during admitted work and bounded resource release after
  framework completion/cutoff; existing admission/dispatch gates still work.
- [x] Sidecar wrapper verification and public-surface architecture checks pass
  with deterministic local fixtures. Documentation/examples match the implemented
  file/configuration and transport behavior, including restart and token-lifetime
  implications, without claiming SC5 packaging or publication.

## Context

[SC4](../phases/phase-sc4.md), the [delivery handoff](../beta4-handoff.md) and
[design lens](../design-lens.md) define this unit. The developer confirmed the
[authenticated execution API](2026-09-13-authenticated-execution-api.md) complete;
current Sidecar history includes implementation `2597238` and cleanup `fba8351`.
That unit supplies JWT verification, ownership, asynchronous queue/store,
diagnostics and shutdown gates. SC4 adds production REST routing/transport and
verified host callback proof. Historical pending/next-unit wording in the handoff
does not override the developer's completion confirmation.

Use the existing Java 21 / Boot 4.1 platform, Boot's Jackson 3, standard Spring
APIs and only the supported `ai.loomspan.api` surface. The named `RestSkillHandler`
SPI is authorized; new extension contracts, internal imports, replacement beans,
reflection bypasses and undocumented settings are not. Framework gaps are fixed
there, never bypassed in Sidecar. Keep framework settings under documented
`loomspan.*`, Sidecar settings under `loomspan-sidecar.*`, and SSL bundles under
standard Boot configuration. Prefer the simplest solution meeting this contract.

Consume `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` from the
developer's local Maven repository and use source/documentation in
`C:/opendev/code/loomspan-framework`. The developer installs after framework
changes to keep artifact and source aligned; no separate artifact/source-revision
verification is required for ordinary development. Retest affected Sidecar
behavior after changes. Sidecar builds/CI do not rebuild framework source or
introduce a snapshot repository.

Excluded: SC5 image, packaged lifecycle matrix, full deployment quick start and
release workflow/publication; new HTTP execution routes/authentication modes;
hot reload, durable recovery, retries, token issuance/refresh/exchange, arbitrary
HTTP methods, templating, file/upload transport and new framework APIs. The
framework-first release sequence and SC5 release evidence remain unchanged.
Exact parser/client plumbing, media-type/charset details, fixture layout and
resource wiring belong to implementation planning within the settled constraints.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This unit introduces outbound protocols, credential forwarding,
  TLS, a configuration contract, startup ordering and client resource lifecycle.
  Those boundaries require research, planning, verification and independent review
  even though product scope is settled.
- **Reassessment triggers:** Equivalent implementation already present, changed
  scope or changed framework contracts. Security, transport, lifecycle and
  public-surface requirements remain binding under any selected profile.
