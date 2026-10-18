# SDK v1 API reference

Reconciled 2026-10-18. Planned behavior, not implemented support.
**Replace superseded contracts directly; no compatibility shims or parallel APIs.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).

## Build and packaging

Use Java 21 and Spring Boot 4.1.0 for optional servlet integration.
An independent Maven reactor under `sdks/java` contains:
- `ai.loomspan:loomspan-sdk-core`: blocking HTTP client, DTOs, credentials and signing.
  No Boot runtime, Framework or Sidecar Java dependency.
- `ai.loomspan:loomspan-sdk-spring-boot-starter`: core plus property wiring,
  Spring-authenticated issuance and callback SecurityFilterChain using the existing
  application server. No separate callback server.

Keep the root Sidecar build independent. Its current dependency remains Framework
`1.0.0-beta.8`. SDK development version is an implementation detail, not a release
commitment. Use maintained HTTP/JSON/JWT libraries; the prior Apache HttpClient 5,
Jackson and Nimbus choices are suitable implementation starting points, not reasons
to add an executor, transport adapter framework or public observer API.

## Blocking operations

```java
interface SidecarClient extends AutoCloseable {
    List<SkillDescriptor> listSkills(CredentialSnapshot credential);
    SkillDescriptor getSkill(String name, CredentialSnapshot credential);
    AcceptedExecution submit(String name, ObjectNode input, CredentialSnapshot credential);
    ExecutionSnapshot getExecution(UUID id, CredentialSnapshot credential);
    ExecutionSnapshot await(UUID id, CredentialSnapshot credential, WaitOptions options);
}
```

These operation names describe the intended small surface; implementation may choose
ordinary DTO organization. No async variants, custom futures, SDK thread-context
wrappers, reactive support, streaming or server-cancellation API.

The illustrative `CredentialSnapshot` type is only a small immutable credential
value holding the execution JWT issued by the application SDK through the
[identity contract](2026-10-15-sdk-v1-identity-callback-reference.md).
Spring convenience wiring reads the authenticated identity once at entry.
No capture manager, thread-local scopes, context propagation, additional client-side
JWT verification pipeline or automatic credential refresh. Keep token bytes out of
ordinary getters, serialization, diagnostics and toString. Sidecar and callback
receivers still independently validate the token; Sidecar forwards it unchanged.

Discovery returns public skill metadata and schemas. Submit requires a JSON object;
reject invalid transport shape locally without compiling business schemas.
Encode names as single path segments. A valid acceptance response supplies a UUID;
submit returns that ID immediately and does not wait for completion.
Construct status requests under the configured base URL. Do not follow response
Location URLs or redirects to another destination.

Status returns the public execution envelope: ID, skill, status, timestamps,
opaque result string, failure, configuration snapshot ID and available events.
Preserve available failure details and event fields without reconstructing internals.
Validate the API envelope and expected execution ID; do not silently switch IDs.

**Results are opaque text.** Preserve the exact decoded result string, including
empty, non-JSON or malformed JSON-looking text. Do not parse it into business types,
validate it against output schemas, sanitize it or infer success from its contents.
Server COMPLETED/FAILED determines status. Application parsing is outside SDK scope.

## Waiting and errors

Each HTTP request is attempted once. Disable automatic retries, redirect replay,
authentication-rechallenge replay and callback/submission replay in the relevant
client/proxy configuration. No read-backoff or rejection-origin subsystem.

`await` polls successful QUEUED/RUNNING status responses until COMPLETED/FAILED or
a finite deadline. Use a simple configurable timeout and polling interval; retain
60 seconds and 250 milliseconds as initial defaults. All network calls must fit
within the remaining total deadline. A failed read stops the wait immediately.

| Observation | SDK behavior |
| --- | --- |
| Local validation/credential failure before send | Return a local failure; no request sent |
| Valid accepted response | Return execution ID; completion is separate |
| HTTP error from server or proxy | Preserve status/problem; do not assert that no work occurred |
| Submission response lost, incomplete or unusable after possible send | Acceptance unknown; work may be running; never resubmit |
| Status read error or invalid envelope | Preserve requested ID, safe error and last valid snapshot if waiting |
| Wait deadline or thread interruption | Stop local waiting, preserve ID/last snapshot; server work may continue |
| Status FAILED | Return terminal failed snapshot with primary failure; no inferred rollback |

Restore the interrupt flag on interruption. A timeout/404/401/403 or client close
does not cancel server work. A caller may deliberately read a known ID later with
an appropriate credential. A new token for the same owner does not renew an
execution's captured token.

Error types distinguish local failure, HTTP failure, protocol failure, acceptance
unknown and wait termination; avoid public state machines for every transport stage.
Retain a reliable parsed execution ID when available, never guess one from partial
bodies or arbitrary headers. Do not build Location/body candidate reconciliation.
Errors and default logging must not expose credentials or business payloads.

## Client configuration and resources

Require a configured base URL without userinfo/query/fragment and finite connection
and response timeouts. Preserve the initial 2-second connect, 10-second response
and 8-MiB response-size defaults as implementation starting points; expose only
settings with concrete use. Overflow is a protocol error, not silent result truncation.

Reuse a client-owned HTTP connection pool; close it idempotently and reject new
operations after close. Always release response resources. No public executor
ownership framework or custom observability listener is required.

Use standard TLS configuration with certificate/hostname verification. Do not add
an insecure switch. Caddy routes use protected local HTTP from SDK to its local
proxy. No SDK transport-mode enum that restricts users to the three test recipes,
network proof controller or separate native-mTLS feature is needed.
Invocation and callback transports are independently configured.

Use `loomspan-sdk.*` for SDK-owned settings, `loomspan-sidecar.*` for Sidecar
settings, documented `loomspan.*` for Framework and standard Boot namespaces for
Boot TLS. Java configuration and Spring wiring must implement the same behavior,
without a matrix of interchangeable transport/identity adapters.

## Testing boundary

Use [one acceptance matrix](2026-10-15-sdk-v1-acceptance-matrix.md).
Simple request-attempt and synthetic-effect counters demonstrate no automatic
replay after response loss. Do not implement a business-operation ledger,
deduplication service, transactional crash-recovery fixture or exactly-once promise.
Applications remain responsible for their own business semantics.
