# HTTP Skill Executor Design Notes

## Purpose and scope

Record Sidecar decisions and open questions for replacing its REST skill handler with an HTTP executor using Framework's general SkillExecutor feature. The required outcome is calling the hosting application or another microservice. Existing REST behavior is evidence to evaluate, not a compatibility requirement.

Framework and Sidecar have no customers or deployments. Destructive replacements are permitted; do not add compatibility shims, legacy adapters, dual paths, or migration machinery solely to preserve obsolete behavior. Multiple tickets and commits may leave intermediate builds or applications broken. Prioritize the simplest final architecture and least technical debt over the smallest patch. Apply the [Sidecar design lens](../design-lens.md#simplicity-and-technical-debt).

These are design discussion notes, not an implementation roadmap or authorization to implement unresolved proposals. Work through the topics below one at a time. Only explicitly accepted decisions become requirements. HTTP request mapping and other HTTP-specific choices are deferred to the Sidecar executor effort; the current Framework discussion examines only concrete gaps in the general executor contract.

## Framework boundary

The general executor contract is designed in `loomspan-framework/ai/thoughts/phases/configured-java-skill-executors.md`. Sidecar consumes that contract; this document does not add Framework requirements. HTTP configuration, transport behavior, authentication modes, resource management, and Sidecar management workflows belong here.

If a concrete need cannot be met by the supported Framework API, discuss the gap explicitly before proposing a Framework change. Do not infer new metadata, lifecycle hooks, validation callbacks, configuration interpretation, or internal API access from Sidecar's current implementation.

Framework removes REST support as part of its executor work. Sidecar adaptation may follow separately after Framework changes are complete; it does not gate Framework removal. A later Sidecar roadmap can turn the settled decisions into implementation phases and end-to-end verification.

## Agreed decisions

### Develop against the Framework snapshot before release

Use `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.9-SNAPSHOT`
from the developer's local Maven repository and consult the matching local
Framework checkout. Defer the Framework beta.9 release until this Sidecar
executor effort is complete. Concrete REST needs discovered here may justify
explicitly planned Framework changes; retest affected Sidecar behavior after
the developer installs those changes. Publish the Framework release only after
local Sidecar integration is proven, then pin Sidecar to the published version
for its final build and tests.

### Put operation configuration in the skill

Remove the separate route table keyed by skill name. Each executor-backed YAML skill carries its HTTP operation configuration, delivered through `skillConfig()`. The existing general design already supplies what is needed through `skillConfig()` and `executorConfig()`; this decision requires no additional Framework feature or invocation metadata.

Illustrative syntax, with HTTP field names still to be designed:

```yaml
name: getCar
description: Get a car
executor: http
config:
  target: inventory
  method: GET
  path: /cars/{id}
```

The HTTP executor interprets these fields. Framework preserves and supplies the configuration without knowing its HTTP meaning. Removing the separate table eliminates skill-name route lookup and the need to synchronize route names with skill names. Shared target ownership is settled below; exact HTTP configuration field names remain open.

### Keep named shared HTTP targets

Keep named targets in the HTTP executor's configuration, supplied through `executorConfig()`. Individual skills select a target through `skillConfig()`. The HTTP executor resolves the selector and interprets target settings; Framework does not understand targets. This fits the existing SkillExecutor design and requires no Framework changes.

Shared target configuration provides a home for base URLs, authentication, TLS, timeouts, and response limits. Exact authentication modes, TLS behavior, and transport controls remain separate discussion topics below.

### HTTP methods and input references

Support GET, POST, PUT, PATCH and DELETE. Do not initially support HEAD, OPTIONS,
TRACE or CONNECT. GET rejects configured bodies. POST, PUT, PATCH and DELETE
allow an explicitly configured body; omission sends no body. The receiving
endpoint determines whether it requires a body.

Path and query mappings use JSON Pointer input references with a required leading
`/`, including top-level fields such as `/carId` and nested fields such as
`/options/simulate`. Do not support bare-name or slashless shorthand. Framework
input/output bindings provide precedent for this syntax, but their object-only
traversal does not imply identical traversal semantics: body selection below also
supports array indexing. Path and query pointers also support array indexing,
using the same traversal rules as body selection. Destination-specific value
rules still apply. An out-of-range index counts as missing: fail for a path
parameter and omit a query parameter. Do not support wildcards or multiple-match
selection.

### Path parameter values

Accept strings, numbers and booleans selected by path-parameter mappings. Reject
missing values, null, empty strings, objects and arrays before dispatch. Each
placeholder occupies one complete path segment and its value is URL-encoded.
Require a mapping for every placeholder and reject mappings without a matching
placeholder.

Reject path-parameter values containing `/` or `\`, values equal to `.` or `..`,
and encoded forms of path separators or traversal that could bypass these
checks. Configured paths also reject traversal segments. Authors supply original
values, not pre-encoded URL components; ordinary spaces, Unicode and punctuation
are URL-encoded. Identifiers containing slashes must use query parameters or the
body instead of path parameters.

### Target base URL and operation path

The shared target owns the scheme, host, port and optional base-path prefix.
The skill path starts with `/` and is appended to that prefix with one joining
slash. For example, base URL `https://inventory.example/api/v1` and skill path
`/cars/{id}` produce `https://inventory.example/api/v1/cars/car-123` when the
selected ID is `car-123`. Skill paths cannot supply a different host or an
absolute URL. Neither the base URL nor the skill path may contain a query string
or fragment; query parameters come from `queryParams`.

### Query parameter presence and scalar values

Omit query parameters whose selected input field is missing or explicitly null.
Send an empty string as an empty query value (for example, `color=`). Preserve
numeric and boolean values, including zero and false. Required input presence
belongs in the skill input schema.

Query arrays of scalars use repeated parameters in array order, for example
`colors: [blue, green]` mapped to `color` produces `color=blue&color=green`.
An empty array sends no parameter. Reject null array elements, nested arrays
and objects before dispatch. Do not initially support comma-separated or
JSON-encoded alternatives.

### Explicit JSON body selection

Support two body choices:

```yaml
body:
  source: input
```

This serializes the complete input map as JSON. Path and query mappings do not
consume or remove fields; fields used there remain in the complete input body.

```yaml
body:
  source: field
  field: /something/changes
```

This selects one input value using JSON Pointer and serializes that value as the
complete JSON body. Use the same pointer syntax for top-level selections such as
`/changes`. Support standard pointer escaping and array selection. Require a
nonempty pointer starting with `/`; whole-input selection uses `source: input`.
A missing path fails before dispatch. Existing objects, arrays, scalars and
explicit null retain their JSON types. Omitting `body` sends no body.

Keep this feature to selection: no expressions, templates, wildcards, defaults,
merging or recursive body construction. Do not add `body.fields` without a
concrete requirement. Prefer an existing JSON Pointer implementation where it
fits. Path and query mappings use the same pointer syntax as agreed above.

The neighboring test suite is supporting evidence, not the governing contract.
Its eight equipment lookup operations send flat `caseId`/`assetId` objects; its
service-request operation sends an `approval` envelope containing nested JSON.
Whole-input selection accommodates both without redundant field mappings. Its
owned SDK callback consumes authentication without requiring a body. These
POST examples do not establish GET or query-collection requirements. This was
source review, not executed integration evidence.

### Fixed target headers

Support fixed request headers on shared HTTP targets, applied to each request
using that target. Values are target configuration, not caller/model input.
Keep authentication configuration separate. Do not initially support per-skill
headers or input-to-header mappings. Header validation and conflicts with
authentication, body construction and transport-owned headers remain to be
settled. The proposed target syntax is a `headers` map, for example
`X-API-Version: "2026-01"`.

### Target authentication modes

Retain three authentication modes, selected per shared target with no automatic
fallback: none, fixed service credentials, and original caller JWT passthrough.
Skill inputs cannot select credentials or change the authentication mode.

None sends no authentication credential. Fixed service credentials use explicitly
configured credential headers managed through Sidecar's credential facilities;
the receiving service sees the configured service identity, not verified end-user
identity. Automatic OAuth acquisition, refresh and token exchange are out of scope.
Caller passthrough sends the captured original JWT as a bearer token, requires JWT
authentication and checks expiry immediately before dispatch. Missing or expired
caller credentials fail rather than falling back to service credentials. Sidecar
does not mint, refresh or replace the caller token. The receiving service validates
the token, including trust and audience, and owns role and record authorization.

### HTTPS and mTLS ownership

Ordinary HTTPS uses the HTTP client's built-in TLS, server-certificate validation
and hostname verification with its configured trust store. Support HTTP and HTTPS
target URLs without a separate executor HTTPS configuration system or a certificate
validation bypass. Private-CA trust configuration is a deployment concern to address
if needed. Caddy owns mTLS in this architecture; do not add executor client-certificate
configuration or mTLS lifecycle management. Existing Sidecar SSL-bundle/mTLS behavior
is not a requirement to preserve in the replacement.

### No automatic HTTP retries

The HTTP executor makes one attempt and reports its outcome; disable automatic
HTTP-client retries. Losing a response does not establish that the remote operation
failed or had no side effects. Applications own idempotency and deliberate recovery.
This transport policy is separate from the still-open policy for recoverable
application-error feedback to the model.

### No automatic redirects

Do not follow HTTP redirects automatically. Report redirect responses as
unsuccessful responses; authors configure the intended endpoint directly.
Keep credentials directed to the configured recipient and avoid implicit
follow-up requests with a potentially different destination or method.

### No automatic cookie storage or replay

Disable automatic cookie storage and replay. Responses may contain `Set-Cookie`,
but the executor does not retain those cookies or send them on subsequent calls.
Each call authenticates through the target's selected authentication mode; shared
clients must not carry cookie session state between users or executions.
Cookie-based login flows are out of scope.

### Target-level timeouts

Keep finite, positive connection and read timeouts in shared target configuration
(`connect-timeout` and `read-timeout`), with documented defaults to be selected
during planning. Skills select a target and inherit its limits; do not add
per-skill timeout overrides. Connection timeout bounds waiting to establish a
connection; read timeout bounds waiting for response data, not total execution
duration. These transport controls are separate from Framework execution
deadlines. A timeout does not prove that the receiver stopped processing.
The discussed `5s`/`60s` values were illustrative, not accepted defaults.

### Target-level response-size limits

Enforce a finite response-size limit in shared target configuration
(`max-response-size`) while reading both successful and error responses. Stop
reading and fail the call when the limit is exceeded; do not truncate and return
a successful result. Skills inherit the target limit with no per-skill override.
Select the documented default during planning; `1MB` was illustrative, not an
accepted default.

### Successful response handling

Treat HTTP 2xx responses as successful. Return the response body as text,
preserving its content; an absent body returns an empty string. Do not wrap the
result in a status/header envelope or parse and reserialize JSON.

### Response content types and decoding

Accept `application/json` and `application/*+json` as UTF-8 text without parsing
or reformatting; reject a declared non-UTF-8 JSON charset. Accept `text/*` using
its declared charset, defaulting to UTF-8 when omitted. When `Content-Type` is
missing, attempt UTF-8 decoding without content sniffing. Reject other content
types, including binary images and PDFs. An empty body returns an empty string
regardless of content type. Unsupported charsets and invalid byte sequences fail
explicitly rather than silently replacing characters. Preserve decoded text
without trimming or newline normalization. Binary resource handling is outside
this design. How error-response content is exposed remains a separate decision.

### HTTP failures and recoverable application rejections

Failures are terminal by default. Connection failures, timeouts, expired
credentials, invalid configuration, oversized responses and decoding failures
raise ordinary `SkillException`; they do not imply the model can repair them.
An HTTP status alone does not establish recoverability or prove that an operation
had no side effects. Automatic HTTP retries remain disabled.

Each skill's operation configuration may declare `recoverableStatuses`, for
example `[409, 422]`. Omission means none. Only explicitly listed 4xx statuses
qualify, excluding 401 and 403, which remain terminal. Transport failures,
redirects and 5xx responses remain terminal. Invalid, oversized or undecodable
responses remain terminal even when their status is listed. This is an explicit
per-operation endpoint contract, not a global classification or a special
Loomspan error envelope. Qualifying rejections raise `RecoverableSkillException`.
Current Framework standard model tool calling supplies that exception as feedback;
direct invocation propagates it and step planning still terminates on tool failure.

For recoverable and terminal HTTP failures with an acceptable decoded body,
construct the exception message from a short HTTP status line followed by the
complete decoded response body, for example:

```text
HTTP 409 Conflict
{"message":"Selected appointment is unavailable. Choose another time."}
```

Preserve body text without parsing, field extraction, rewriting or truncation.
Apply the agreed size and decoding rules. An empty body yields only the status.
Do not add request headers, credentials or request bodies to the generated
message. Error bodies can reach execution results and diagnostics; recoverable
bodies can reach the model. The receiving application owns returned content.
Terminal failures do not become model recovery feedback through this mechanism.

### HTTP configuration validation

HTTP-specific validation is invocation-time only, before HTTP dispatch, in both
file and database modes. The executor validates captured skill/target configuration
and actual input, including unknown targets, methods, pointers, body combinations,
HTTP credential usability and destination-specific value rules.

Startup, readiness, draft validation, publication, import, rollback and restoration
must not run HTTP domain validators or probe destinations. Framework still validates
its own declarations, executor references, selected-map structure, schemas and
authorization. Sidecar retains management authorization, storage/bundle integrity
and credential-record protection; these are not HTTP operational validation.
Management success does not certify executor configuration or destination usability.
A Framework-valid but HTTP-invalid configuration can start, be published/restored
and report ready; invocation fails clearly before sending its HTTP request.

This developer decision supersedes the earlier early-validation requirement.
Keep SkillExecutor invocation-only: no new public configuration accessor,
validation hook, internal access, independent Boot Config Data re-parsing or
parallel configuration contract is needed for this work. HTTP validators do not
contact destinations. Unit tests may exercise them directly, but production
startup/management paths must not call them.

### Management and durable configuration

Keep the file-mode/database-mode distinction while replacing the separate routes
document. HTTP operations live in skill YAML and shared targets live under
`loomspan.executor-config`. Management edits and publishes them together as one
complete configuration, validating Framework contracts and management invariants
only, not HTTP domain semantics. Database mode restores the selected publication
after restart without filling missing settings from startup files. File mode
continues to apply changes through restart.

Framework owns runtime generations; Sidecar owns durable storage, editing and
restoration. HTTP-client resources must no longer own durable snapshot identity.
Replace obsolete stored formats directly and document any required development-data
reset; do not add compatibility readers or migration machinery. This decision does
not authorize deleting data during planning.

### Original request token access

Sidecar must be able to access the token captured from the original request during HTTP executor invocation. Existing Spring Security authentication propagation can support this: Sidecar captures the request authentication, and its executor can read the JWT token from that authentication. Framework adds no token-specific field, storage, or forwarding behavior. Token expiry and forwarding policy remain Sidecar responsibilities. This requires no change to the general SkillExecutor contract.

### Prefer an application-owned shared HTTP client

For v1, prefer one reusable application-owned HTTP client/connection pool rather
than clients per target or configuration generation. The HTTP library owns pool
mechanics; Sidecar owns creation and shutdown. Build each request from the
invocation's captured configuration. Apply credentials and fixed headers per
request, never as mutable shared client state. Keep cookies, retries and redirects
disabled. Keep the client available through Framework shutdown handling for
admitted work, then close it without introducing another drain period.

During implementation planning, verify that the chosen client can honor each
target's captured connection/read timeouts without mutating settings shared by
concurrent requests. If separate clients are necessary, introduce only the resource
ownership needed to satisfy those requirements. This is a preferred approach,
not evidence that all timeout semantics have already been verified.

Framework owns versioned skill/configuration capture and retained execution
lifetime. Sidecar consumes `skillConfig()` and `executorConfig()` directly rather
than looking up latest settings or maintaining a parallel configuration-version
system. Do not preserve existing per-generation client bookkeeping solely because
the old implementation used it. Retirement notifications below are available
when resources actually require generation-specific ownership.

### Application owned resource retirement

The executor bean or a collaborating resource-management bean may inject the supported `SkillReloader` API and register `onGenerationRetired(...)`. Its generation ID can be matched against `executorConfigGenerationId()` for resource cleanup after retained work finishes. Register the listener early and arrange cleanup outside the callback; callbacks should return promptly. Notifications may be omitted during shutdown, so application beans also own shutdown cleanup.

This uses existing Framework facilities and needs no new executor lifecycle hooks or invocation metadata. Sidecar's exact client pooling, cache keys, staging, durable snapshot correlation, and cleanup implementation remain separate design work. Do not assume a generation ID alone distinguishes multiple executor configuration subtrees or targets in one namespace.

## Settled Framework contract for the later Sidecar effort

The Framework design document is authoritative; this summary records decisions relevant to Sidecar and does not expand their scope.

- An executor is a Spring bean method annotated with `@SkillExecutor(name, namespace, config)`, not an interface implementation. Name defaults to the method name, namespace to `default`, and `config` to the empty string (no reference). One method cannot also carry `@SkillMethod`.
- It accepts the public record `SkillExecutorInvocation`, containing `input()`, `skillConfig()`, and `executorConfig()` as `Map<String, Object>`, plus `skillName()` and `executorConfigGenerationId()`. Maps/lists are defensively copied and unmodifiable; non-container leaf values are preserved.
- Application configuration lives under `loomspan.executor-config.<namespace>.<relative-path>`. The annotation selects a dot-separated map path within its own namespace. Omission supplies an empty map; an explicit missing path or non-map subtree fails Framework binding validation. Null values inside maps remain allowed. Publication completely replaces configuration, with no startup/prior-value inheritance.
- Executor-backed YAML skills are direct-execution leaves. They own description, input schema, roles, and operation configuration. They reject model/planning/child-skill and output-schema settings. YAML `config:` is exclusive to executor-backed skills; omission or `{}` is empty, while explicit null, scalar, or list is invalid.
- Executors and SkillMethod share supported return types and conversion: null becomes empty text, strings remain unchanged, other values are JSON-serialized. There is no String-only executor restriction or Framework Java output-schema validation.
- Both use `RecoverableSkillException extends SkillException`, with message-only and message-plus-cause constructors. Messages must be non-null/nonblank and are preserved unchanged; the cause is diagnostic. Only known invocation wrappers are transparently unwrapped, not arbitrary application cause chains. Sidecar must deliberately decide what application failures merit this signal; Framework does not infer recoverability from HTTP status or error text.
- Direct invocation propagates exceptions. Standard model tool calling gives designated recoverable failures to the model as failed-call feedback, while terminal failures propagate. Step planning still terminates on tool failure in this enhancement. Broader planner recovery is a separate Framework follow-up, not a capability Sidecar can assume is implemented.
- YAML skill and executor RBAC both gate planner visibility and execution; applicable Spring method-security advice still applies. Existing scoped authentication supplies caller context, including Sidecar's captured JWT.
- Fixed executor registrations follow SkillMethod's namespace replacement/removal/recreation lifecycle; beans remain application-owned. Executing skills are classified as `SkillKind.JAVA`; executor registrations themselves are not callable catalog entries.
- Framework removes `RestSkillHandler`, `RestSkillInvocation`, REST manifest/wiring support, and `SkillKind.REST` in this enhancement. Sidecar may remain broken until its independent adaptation is complete; do not request legacy adapters or retain REST to avoid that interval.

## Handoff to implementation planning

The agreed decisions above settle the product direction for request construction,
authentication, TLS ownership, transport controls, response/failure handling,
validation and durable management. They are selected design, not shipped Sidecar
support. The local beta.9-SNAPSHOT executor API and documentation were reviewed;
implementation must prove Sidecar behavior through its own integration tests.

Resolve these remaining details from source evidence during ticket research and
implementation planning:

- Final configuration schema, defaults, credential references and header validation.
  Detailed conflicting-header restrictions were discussed but not accepted as
  requirements; use the agreed separation of responsibilities as the starting point.
- Verify shared-client support for captured per-target timeout settings and safe
  concurrent requests; design only the resource ownership actually needed.
- Trace startup, management validation/publication and restoration through the
  supported Framework APIs; identify any concrete public-contract gap explicitly.
- Separate durable snapshot identity from HTTP clients and determine the exact
  stored-format replacement and any required development-data reset.
- Identify coordinated changes to the management UI, exports/imports, fixtures,
  documentation, packaged guidance and representative test-suite integration.
- Define Sidecar-owned verification, including captured configuration across
  publication, caller authentication, HTTP failures and shutdown ordering.

Bring material new product choices back for discussion. Routine implementation
choices do not require a separate approval. Retain destructive replacement and
simplicity requirements prominently in the roadmap and dated tickets, with the
design-lens reference. Defer Framework beta.9 publication until this effort is
complete and local integration is proven.

## Current implementation evidence

- [GenericRestSkillHandler](../../../src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java): generation and skill-name lookup, request construction, caller forwarding, and dispatch.
- [RestRouteLoader](../../../src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java) and [RestRouteCatalogValidator](../../../src/main/java/ai/loomspan/sidecar/rest/RestRouteCatalogValidator.java): HTTP configuration parsing and skill-to-route validation.
- [RestTargetClients](../../../src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java) and [BoundedRestResponse](../../../src/main/java/ai/loomspan/sidecar/rest/BoundedRestResponse.java): transport settings and response behavior.
- [GenerationRestResources](../../../src/main/java/ai/loomspan/sidecar/rest/GenerationRestResources.java): client ownership, retirement, and durable snapshot correlation currently combined in one component.
- [RuntimeConfigurationService](../../../src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java): validation, staging, publication, and restoration.

Existing tests provide scenarios to evaluate, including retained admission across publication, shutdown, TLS, and HTTP failure handling. They do not establish a requirement to reproduce every old behavior.
