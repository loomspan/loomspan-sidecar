# SDK v1 API and outcome reference

**Development policy:** Replace superseded development contracts directly; no
compatibility shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No data reset is needed.

All names and behavior below are **PLANNED, NOT RUN**, not current SDK support.
The [identity reference](2026-10-15-sdk-v1-identity-callback-reference.md) and
[trust reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md) remain
normative for security. [Deployment](2026-10-15-sdk-v1-deployment-recipes.md) and the
[acceptance matrix](2026-10-15-sdk-v1-acceptance-matrix.md) supply compositions and owners.

## Runtime, packaging and baseline attribution

Select Java 21 (compile release 21), Spring Boot 4.1.0 and its managed Spring MVC/
Security versions for optional servlet integration. No WebFlux callback integration
or reactive context propagation is supported. Core works in nonweb Java applications.
Use Apache HttpClient 5 (Boot 4.1.0 dependency-management version, imported as a BOM
without a Boot runtime requirement) and standard Java 21 TLS. Pin the resolved
HttpClient version in SDK dependency/evidence metadata. Its default client builder
disables redirects, automatic retries, cookie management and authentication-rechallenge
replay; the implementation must prove that redirects, implicit POST replay, authentication
rechallenge replay and reconnect replay are disabled. If its chosen HTTP engine
cannot prove this, replace the engine before sign-off; do not relax the contract.

Independent Maven reactor `sdks/java/pom.xml`, group `ai.loomspan`, initial development
version `1.0.0-beta.1-SNAPSHOT`, planned first release `1.0.0-beta.1`:

| Module / artifact | Package / responsibility | Dependencies and ownership |
| --- | --- | --- |
| `loomspan-sdk-core` | `ai.loomspan.sdk`: HTTP DTOs, blocking/async client, credential strategies, signing/public export primitives | Neither Spring Boot runtime nor embedded Framework; Apache HTTP, Jackson JSON and maintained Nimbus JWT/JOSE; SDK owns pools/executor by default |
| `loomspan-sdk-spring-boot-starter` | `ai.loomspan.sdk.spring`: property/bean wiring, MVC/Security callbacks, optional public JWKS route | Core and Boot 4.1.0 managed servlet/Security APIs; uses existing application server, never starts a callback server |

Keep root Sidecar Maven build independent; no root aggregation or version coupling.
Sidecar remains `1.0.0-beta.4-SNAPSHOT`, Framework starter `1.0.0-beta.8`. The SDK
uses Sidecar's public HTTP API; its modules have no Framework or Sidecar Java
dependency and do not reproduce skill execution or validation. Architecture and
dependency checks enforce that boundary. Sidecar's own Framework integration uses
only the closed supported `ai.loomspan.api` surface and authorized `RestSkillHandler`;
no internal/autoconfigure imports, new SPI, bean replacement, reflection or Sidecar
Java `@SkillMethod` are authorized.

The external suite's existing Java 21/Boot 4.1.0 embedded lane intentionally uses
Framework beta.9-SNAPSHOT and pinned beta.3 Sidecar source. Preserve it, its Python
path, equipment-service fixtures and synthetic/frozen TypeSafe replay packs. Add
a separate SDK v1 lane selecting this Sidecar revision and beta.8. Preserving those
fixtures creates no SDK TypeSafe integration or decoding requirement. SDK consumer
checks prove the HTTP contract and dependency isolation; they do not claim provider
capabilities for the selected server baseline.
Any snapshot lane records its distinct revisions; final release claims use tested
published artifacts under repository publication policy.

## SDK boundary: API envelopes and opaque skill results

The SDK decodes and validates Sidecar's HTTP response envelopes: execution IDs,
statuses, timestamps, problems, failure details and available diagnostics. It
preserves known execution context through polling and client failures. Typed DTOs
and typed SDK errors describe that API contract, not the business meaning of a result.

**Skill result content is opaque to the SDK.** The `result` field remains the exact
string supplied by Sidecar after JSON-envelope decoding. The SDK does not parse
that string as JSON, validate it against `outputSchema`, map it to application Java
types, interpret provider-specific values, sanitize it or infer execution success
from its contents. SDK v1 has no result-conversion helpers or TypeSafe adapter.
Applications own any parsing, schema checks and business interpretation after receipt.
Such application failures do not change the server-reported status or trigger SDK
submission, polling or retry behavior. This boundary does not remove validation of
API envelopes, credentials, transport configuration or callback authentication.

## 1. Discovery and execution

The following signatures specify public operations; implementation may organize
DTO files without changing the semantics. Every operation accepts an explicit
immutable `CredentialSnapshot` from the selected trusted strategy. Spring convenience
wiring captures it from the current supported scope once at operation start.

```java
interface SidecarClient extends AutoCloseable {
    List<SkillDescriptor> listSkills(CredentialSnapshot credential);
    SkillDescriptor getSkill(String name, CredentialSnapshot credential);
    AcceptedExecution submit(String name, ObjectNode input, CredentialSnapshot credential);
    ExecutionSnapshot getExecution(UUID id, CredentialSnapshot credential);
    ExecutionSnapshot await(UUID id, CredentialSnapshot credential, WaitOptions options);
    CompletableFuture<List<SkillDescriptor>> listSkillsAsync(CredentialSnapshot credential);
    CompletableFuture<SkillDescriptor> getSkillAsync(String name, CredentialSnapshot credential);
    SubmissionFuture submitAsync(String name, ObjectNode input, CredentialSnapshot credential);
    CompletableFuture<ExecutionSnapshot> getExecutionAsync(UUID id, CredentialSnapshot credential);
    WaitFuture awaitAsync(UUID id, CredentialSnapshot credential, WaitOptions options);
}
```

`SidecarClients.create(ClientSettings, CredentialStrategy)` constructs the owned
client. `Credentials.capture(strategy)` calls the selected strategy once, validates
its opaque `AccessCredential` under the approved profile and returns an immutable
`CredentialSnapshot` pinning the exact credential, verified pair, expiry and approved
policy reference. Only SDK capture creates snapshots; no business field can create
one. Capture errors are pre-send typed credential failures. Snapshots never expose
token bytes through getters/serialization/toString and cannot silently renew.

`SubmissionFuture extends CompletableFuture<AcceptedExecution>` adds
`SubmissionObservation observation()`. The immutable observation contains stage
`NOT_DISPATCHED|DISPATCH_POSSIBLE|ACCEPTED|REJECTED|UNKNOWN`, reliable ID if any and
safe problem/cause/timing. Cancelled futures retain access to this observation;
after dispatch is possible, cancellation records UNKNOWN until a valid response
establishes a stronger outcome, never not-submitted by assumption. Transport completion
may update the observation even after local future cancellation. This makes retained
acceptance context accessible without joining a cancelled future or rerunning work.

`WaitFuture extends CompletableFuture<ExecutionSnapshot>` adds `WaitObservation
observation()`. Each immutable observation contains the requested ID, last validated
snapshot (if any), read-attempt count and stage `WAITING|TERMINAL|TIMED_OUT|FAILED|
CANCELLED|CLOSED`, with safe cause/timing. Publish the latest observation before
completing/cancelling the future. Cancellation preserves it after `join/get` begins
throwing `CancellationException`; no cancelled completion erases the last snapshot.
The SDK owns observation updates, serializes cancellation/completion races and keeps
the terminal observation readable after client close. Cancellation stops scheduling
reads; a late read cannot reopen the wait or change its requested ID.

Names are encoded as a single URI path segment, never concatenated as arbitrary URLs.
`ObjectNode` must be an object; reject null, arrays and scalar input locally. Copy
input at capture to prevent caller mutation during async send. Do not coerce JSON
or silently drop fields. This is transport-shape validation only; the SDK does not
validate business inputs against a discovered schema. Sidecar/Framework remain
responsible for server-side skill validation and limits.
GET `/v1/skills` and `/v1/skills/{name}` return DTOs with `name, description, kind,
inputSchema, outputSchema`; schemas retain their wire string, optional output schema
may be absent. Schema strings are descriptive metadata for the caller; the SDK
does not compile or enforce them against skill inputs or results.

POST `/v1/skills/{name}/executions` returns 202 `{id: UUID}` plus Location.
`AcceptedExecution(id, statusUri)` means accepted, never completed; submit does not
poll. A structurally valid 202 plus a valid body UUID with no conflicting trustworthy
Location ID yields this handle. Resolve status URI locally under the configured SDK
base origin/path. Ignore unsafe/foreign Location with a safe protocol warning when
the complete body is valid; it is not needed to establish acceptance or route lookup.
Client-Caddy legitimately has a local HTTP SDK base URI while Sidecar emits its
external HTTPS Location. Do not follow Location, redirects or response authorities.

## 2. Waiting

GET `/v1/executions/{id}` is independent of submit; callers may persist known IDs and
later use a newly acquired same-pair credential subject to current read authorization.
`await` returns either terminal snapshot (COMPLETED or FAILED). It never translates
FAILED into success; `requireCompleted(snapshot)` is the explicit convenience that
throws `ExecutionFailed` with the complete snapshot. Missing record returns a typed
`StatusUnavailable` preserving ID and ProblemDetail, without diagnosing why.

`WaitOptions(totalTimeout=60s, pollInterval=250ms, maxReadAttempts=3,
initialBackoff=100ms, maxBackoff=1s)` are selected defaults. TotalTimeout must be
positive and finite; pollInterval at least 10ms; read attempts 1–5; all backoffs
nonnegative and capped at 5s. No unbounded wait setting. A monotonic total deadline
includes sleeps, reads, retries and credential acquisition. Each HTTP timeout and
backoff is clamped to remaining time; do not start a read when none remains.

Read retry applies only to GET discovery/status on transient connection/timeout,
trusted 429/502/503/504 (including proxy responses as read failures). Use at most
maxReadAttempts per logical read with exponential capped backoff and bounded
Retry-After; no parallel speculative request. 401/403/404, malformed response-envelope
JSON or an invalid API-envelope structure, conflicting IDs and other protocol failures
stop immediately. Skill result text is never parsed or schema-validated by this policy.
No automatic credential refresh. A nonterminal successful read resets that per-read counter, not the total
deadline. Await timeout preserves last snapshot/ID; exhausted read retries preserves
ID, cause and attempt count. A getExecution call also has finite HTTP/retry budgets.

Thread interruption stops local wait, restores interrupt flag and throws
`WaitInterrupted(id,lastSnapshot)`. Async cancellation stops future polls and attempts
local I/O cancellation, never a server cancellation. Already sent submit cancellation
may be acceptance unknown; a cancellation handle exposes its retained known ID/outcome
when available through `SubmissionObservation`, rather than fabricating rejection.
Closing a client stops waits with a typed local-closed error retaining IDs. No wait
termination, 404, expiry, reconnect or recovery submits again or sends cancellation.

## 3. Execution tracking and response preservation

`ExecutionSnapshot` contains `id, skillName, status, createdAt, completedAt, result,
failure, configurationSnapshotId, events`. Status is QUEUED/RUNNING/COMPLETED/FAILED;
intermediate transitions may never be observed. Reject incompatible/unknown required
state with protocol error retaining parsed ID/raw bounded response context. Do not
invent completion times/results/configuration IDs. These fields may be null before
handoff/completion; snapshot correlation remains opaque. COMPLETED retains exact
result text; FAILED retains failure and no fabricated result. Events distinguishes
omitted/unavailable from a selected empty list.

`ExecutionFailure(kind,message,issues)` preserves INPUT_VALIDATION, ACCESS_DENIED or
SKILL_FAILURE, primary message and available issues `(path,code,message)`. Preserve
additional problem/event JSON fields in immutable extension values. Do not suppress
an execution failure because diagnostics are unavailable. Event DTOs preserve public
timestamp/level/type/details/frameId/route without reconstructing internals.

Envelope validity and result content are separate. A COMPLETED snapshot whose
string result is plain text, empty, JSON-looking but malformed, or inconsistent
with a discovered output schema remains COMPLETED. Conversely, malformed envelope
JSON or a non-string value where the API requires a result string is a protocol
error. `requireCompleted` checks only the reported status. Applications can inspect
or parse the returned text themselves; their parsing failures leave the immutable
snapshot, ID and diagnostics available and cause no new SDK requests.

## 4. Client, protocol and execution errors

SDK errors describe local client/credential failures, HTTP/protocol failures and
server-reported execution outcomes. They do not classify application parsing errors
or infer an execution failure from business content, provider fields or message text.

All typed failures contain operation, reliable execution ID if any, optional last
snapshot/problem, safe cause, request/attempt timing and classification. Sensitive
raw payloads/headers are not included in default exception `toString` or logs.
Problem DTO preserves type/title/status/detail/instance/issues and extensions, with
bounded configured response size (default 8 MiB). Limit overflow is a protocol error,
never silent truncation of a successful result. Users can raise the limit explicitly.

| Observation | Public outcome | Retained context / permitted action |
| --- | --- | --- |
| Local invalid object/credential/configuration; transport proves no HTTP request bytes dispatched (e.g. pre-connect TLS failure) | `NotSubmitted` | Proof stage and cause; caller may fix and deliberately make a new submission |
| Complete documented pre-admission rejection from authenticated/otherwise protected Sidecar endpoint: 400/401/403/404/413/429/503 | `SubmissionRejected` | Status/problem/issues; no handle, no SDK retry. Origin must be established by recipe routing (proxy-generated errors excluded) |
| Send progress uncertain, reset/timeout/interruption after possible dispatch, lost response, proxy 4xx/5xx, truncated/malformed 202 or unexpected response | `AcceptanceUnknown` | Work may run; preserve reliable ID/details if obtained; no retry advice or recovery guarantee |
| Valid 202/body UUID and no conflicting trustworthy Location ID; foreign/unsafe Location is ignored with warning | `AcceptedExecution` | Known ID immediately; construct local status URI and poll separately |
| Known-ID status transport failure after bounded retries | `PollingFailed` | ID, last snapshot and cause; deliberate later status lookup is safe |
| Status invalid JSON/ID/state, oversized body or incompatible response | `ProtocolFailed` | Requested ID, any reliable response ID as diagnostic, no silent identity replacement |
| Status 401/403 | `StatusDenied` | ID and response; do not renew automatically |
| Status 404 | `StatusUnavailable` | ID; could be unknown, expired, nonowner or restart loss; no inferred rejection/cancellation |
| Total wait exhausted / interruption / local async cancellation | `WaitTimedOut` / `WaitInterrupted` / local cancellation observation | Known ID and last snapshot; work may continue; resume lookup deliberately |
| HTTP 200 status FAILED | Terminal snapshot, or explicit `ExecutionFailed` convenience | Complete primary failure/issues/history; effects may already have committed |

Reliable-ID rule: accept a UUID from a complete validated acceptance body or a
same-origin, exact configured status-path Location with one UUID and no query,
fragment, userinfo or path ambiguity. A damaged body with such a Location can retain
ID in AcceptanceUnknown but cannot claim successful acceptance. A valid 202/body ID
with unsafe/foreign Location remains accepted with a warning; the SDK discards that
URI and constructs lookup under its configured base URI. This accommodates a client
proxy's different local origin without trusting a response-selected destination.
Conflicting body and trustworthy same-origin Location IDs retain both as candidates
in safe diagnostics and select neither; no automatic poll. A partial UUID/string
substring, proxy request ID or arbitrary error-body `id` is never reliable. Receiving
a status ID cannot silently switch the caller's requested execution.

Trustworthy rejection requires a route that cannot substitute synthetic errors as
Sidecar origin evidence. `RejectionProvenance.UNKNOWN` is the default; an operator
may explicitly select `DIRECT_SIDECAR` only for a verified direct connection to
Sidecar's protected HTTP or Boot HTTPS listener, with no intermediary able to
synthesize HTTP responses. The recipe runner selects this only for those direct
paths. Client-Caddy and SDK-native mTLS both terminate at a proxy, so every error
response there remains acceptance unknown, even when counters later prove that
Sidecar rejected it. Test counters are evidence, never an SDK classification channel.
No header, response body shape or undeclared observation API proves proxy origin.
Customer proxies retain UNKNOWN unless a separately planned origin-proof contract
is approved. Preserve their status/problem in the unknown outcome without retry.
DNS/connect failures alone are not proof if a prior connection/request attempt may
already have sent bytes. No built-in POST retry, even after NotSubmitted or rejection.

## 5. User-security integration

Explicit Relay/Exchange/Spring-issued/Custom selection, Java/property equivalence,
trusted sending/receiving adapters, 300s maximum lifetime/60s skew, pair ownership,
independent callback verification and both callback-chain arrangements follow the
identity reference without fallback. `loomspan-sdk.*` belongs to SDK; documented
Framework `loomspan.*`, Sidecar `loomspan-sidecar.*` and Boot `spring.ssl.bundle.*`
retain ownership. No management-authoring SDK is included. Reuse existing customer
login, URL/method/record authorization and native account policy.

Async client APIs accept captured immutable identity; they do not propagate ambient
thread locals. Spring MVC Callable/DeferredResult require the explicit SDK executor/
capture wrappers and independent scope open/close from the identity contract. Raw
executors/common pool/detached work/reactive callbacks have no implicit guarantee.
No callback listener or TLS material is required for outbound-only invocation/polling;
optional JWKS hosting uses the application's independently elected existing server.

## 6. Transport lifecycle

`ClientSettings(baseUri, invocationTransport, connectTimeout=2s, responseTimeout=10s,
maxResponseBytes=8MiB, readPolicy, tlsMaterialSource, observationListener,
rejectionProvenance=UNKNOWN)` is immutable.
Require explicit protected-local boundary declaration for HTTP; remote HTTP fails
configuration. HTTPS always verifies certificate chain and hostname, no insecure
switch/downgrade. `invocationTransport` is direct HTTP/direct HTTPS/client-Caddy mTLS/
SDK-native mTLS; callback transport belongs to application/deployment independently.
Standard files/PKCS12/SSL bundles supply material; no automatic cloud SDK dependency.

Property initialization binds to the same validated `ClientSettings` builder as Java;
supplying both a client-settings bean and these properties is an error. Unknown fields,
missing base URI, inconsistent TLS/path choices and invalid limits fail startup.
The earlier illustrative `SdkClient` name denotes this `SidecarClient` builder/facade,
not an additional legacy API. Credential/endpoint/export properties keep the exact
contracts from the preceding references.

| Planned property | Java setting / default |
| --- | --- |
| `loomspan-sdk.sidecar.base-url` | `baseUri`, required exact origin and base path; no userinfo/query/fragment |
| `loomspan-sdk.transport.mode` | `invocationTransport`, required `http`, `https`, `mtls-proxy` or `mtls-native` |
| `loomspan-sdk.transport.local-boundary-ref` | Explicit protected boundary object, required for HTTP and proxy loopback ingress |
| `loomspan-sdk.transport.connect-timeout` | `connectTimeout=2s`, positive finite |
| `loomspan-sdk.transport.response-timeout` | `responseTimeout=10s`, positive finite |
| `loomspan-sdk.transport.max-response-bytes` | `maxResponseBytes=8388608`, positive finite; fail on overflow |
| `loomspan-sdk.transport.tls-material-ref` | `tlsMaterialSource`, standard material/source reference, required for native mTLS |
| `loomspan-sdk.transport.observer-bean` | `observationListener`, absent disables listener |
| `loomspan-sdk.transport.rejection-provenance` | `rejectionProvenance`, default `unknown`; `direct-sidecar` requires the verified direct route above and conflicts with either mTLS proxy path |
| `loomspan-sdk.read.max-attempts`, `.initial-backoff`, `.max-backoff` | Same bounded read policy as WaitOptions; defaults 3, 100ms, 1s |
| `loomspan-sdk.wait.total-timeout`, `.poll-interval` | Default WaitOptions 60s/250ms; per-call options replace complete defaults explicitly |

One client owns reusable pools and default bounded executor. Close is idempotent:
reject new operations, stop waits, close owned responses/pools/executor, preserve
accepted work context. Borrowed transport/executor is closed by its explicit owner;
builder requires ownership choice, never closes application server or shared resources.
Release response bodies on every success/failure/cancel path. No automatic submission
or callback retries in SDK, Sidecar clients or reference proxies; disable redirects,
cookie reuse and upstream failover replay. Customer proxies must prove the same with
attempt counters. Read retries are the separate bounded GET policy above.
Native and proxy material generations obey 5s load, 30s routine drain, immediate
expiry cutoff and 5s member-removal closure from the trust reference, with no replay.

## 7. Diagnostics

Default observability emits operation, opaque client correlation ID, reliable execution
ID, elapsed time, status/classification, read attempt count and selected transport/
generation identifier only. Observation listeners are opt-in and exceptions in them
cannot repeat work or replace execution failure. Never log request/result/event bodies,
Authorization, cookies, tokens, private material or internal identity context by default.
Available server history is neither complete nor durable; Sidecar's retention/visibility
policy applies. SDK/Sidecar/console add no business-content sanitization: deliberate
caller/callback text retains fidelity and applications own what reaches models/results.

## 8. Java ergonomics and application duplicate protection

Blocking/CompletableFuture methods have identical envelopes/classification. Do not
hide submit behind an auto-wait convenience that discards acceptance context. SDK
version/release is independent; clean Java and Boot consumers prove dependency scope.
Other languages, new Python features, code generation, streaming and server cancellation
are excluded. No compatibility adapters are introduced for superseded development APIs.

Planned `business-operation-ledger` fixture in the external suite has a caller-assigned
stable business operation ID in ordinary business input. An application transaction
atomically inserts `(operationId, normalizedRequestDigest, state, receipt)` under a
unique constraint and commits the synthetic effect plus receipt together. Concurrent
same-ID/same-digest calls return the committed receipt or explicit pending state;
different digest conflicts without effect. Crash-before-commit leaves no receipt/effect;
commit-then-drop-response leaves one durable receipt/effect, queryable by ID. Reconciliation
reads that ledger before an application deliberately chooses any new submission.

This is application policy, not SDK/server submission deduplication or exactly-once.
Deliberate repeated skill actions use distinct operation IDs when distinct effects
are intended. Count transport attempts separately from business invocations and
effects: a callback may commit before response loss or later execution failure.
No execution failure, wait termination, reconnect or distrust implies rollback.

## Owning gaps and sign-off

No SDK currently exists; Sidecar lacks finite managed trust/purpose/binding rules,
withdrawal queue gates and material-reload pools. Planned units in the matrix own
those gaps and tests. No new missing public Framework contract is demonstrated.
If public-only implementation cannot satisfy callback isolation, atomic publication
or connection cutoff, stop affected planning/sign-off and record
an owning-repository dependency. Never weaken the contracts or import internals.
This document closes API design choices only; all runtime acceptance remains planned.
