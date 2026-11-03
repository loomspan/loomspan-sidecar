# Sidecar integration

Applications call the execution API with JWTs; management users author complete snapshots. For framework semantics use the matching installed `loomspan-docs` dependency declared in this skill.

Contents: [runtime](#runtime-configuration), [HTTP operations](#http-operations), [JWTs](#jwt-authentication), [execution](#execution-api), [authoring](#remote-configuration-authoring), [limits](#capacity-retention-and-diagnostics).

## Runtime configuration

Select file or database configuration at startup. See the authoritative
[configuration modes](configuration-modes.md) for file locations, complete
database authority, encryption-key provisioning and credential rotation.

Each model-backed manifest names a model. Author its connection and provider
model in the same complete Console draft; the manifest does not create them:

```yaml
loomspan:
  executor-config:
    default:
      http: {}
  connections:
    primary:
      driver: openai
      base-url: https://model.example.test/v1
      api-key-ref: provider.primary.key
  models:
    primary:
      connection: primary
      provider-model: example-model
```

Save `provider.primary.key` through the write-only draft credential control or API;
never put the resolved credential in authored YAML. Publish complete
[execution settings](execution-configuration.md) with skills. The
[management API](management-api.md) owns draft, validation and publication checks.

## HTTP operations

Use the [HTTP executor contract](rest-configuration.md) for skill-owned operations,
explicit JSON Pointers, body choices, target settings, service credentials and
caller passthrough. Publish skill documents with complete execution YAML containing
`loomspan.executor-config.default.http.targets`. Executor-backed skills are JAVA
in the Framework catalog and retain their authored YAML in managed configuration.

HTTP configuration and input are validated at invocation before dispatch.
Framework validation, readiness and management publication do not certify HTTP
validity or destination reachability. Direct HTTPS uses JVM trust and hostname
verification; Caddy owns outbound mTLS. Standard Boot inbound TLS is independent.

## JWT authentication

Every `/v1/**` request requires a profile bearer JWT. Trust always comes from
one current JWKS file in both application ConfigurationModes; it is never
managed database content. The shipped external configuration selects colocated:

```yaml
server:
  address: 127.0.0.1
loomspan-sidecar:
  trust:
    mode: colocated
    colocated:
      key-directory: /sidecar/keys
  auth:
    jwt:
      audience: loomspan-sidecar # default
```

The deployment creates that directory with app-write and Sidecar-read
permissions. Colocated reads `jwks.json` and requires Sidecar to bind loopback. For an operator-managed file instead select
`trust.mode: file` and `trust.file.jwks: /path/to/jwks.json`, omitting colocated
settings. Missing mode/path or other-mode settings fail startup; a missing or
unusable file starts waiting. See [execution trust](execution-trust.md)
for all file rules, one-second polling, globally bounded unknown-kid reload,
readiness, developer/Compose/Kubernetes permissions and failure/recovery guidance.

Require RS256, `typ: loomspan-execution+jwt`, nonblank `kid`,
`token_use: loomspan_execution`, nonblank string `iss`/`sub`, string-array `roles`,
numeric `exp` and the receiving audience. Extra audiences and fractional dates
are allowed; optional `iat`/`nbf` are numeric and `jti` is a string. Fixed receive
skew is 60 seconds. There is no configured issuer comparison: the trusted file
is the token-origin authority. Never derive trust from token URLs.

Role names match skill `rbac_roles` exactly, including any literal `ROLE_`.
Management accounts, sessions and personal tokens remain independent. The
removed `issuer-uri`, `jwk-set-uri`, `public-key-location`, `roles-claim`,
`role-prefix` and `clock-skew` properties have no aliases; replace process
configuration without resetting valid database publications or accounts.

Queue time consumes captured lifetime. Immediately before Framework handoff,
Sidecar revalidates current trust and strict expiry; expired work fails with
`execution credential expired before start`. A fresh owner token can poll by
exact signed issuer/subject but cannot replace captured bytes. Work already
admitted by Framework is not cancelled by trust changes. Each approved
passthrough send strictly checks `exp` and fails with `execution credential
expired` without sending at or after expiry, even within receive skew. Receivers
independently verify their own current trust, audience, purpose and lifetime.
Sidecar forwards the original token, never signs, refreshes or exchanges it,
and never follows redirects or automatically retries. Applications sharing a
Sidecar trust each other's asserted identities and roles; use separate Sidecars
for isolation. Browser sessions cannot authenticate execution or callbacks.

Standard Boot TLS configuration can require inbound mTLS, for example
`server.ssl.client-auth=need`. This protects the transport; Sidecar does not map
X.509 certificates to execution identities.

## Execution API

Authenticated clients can use:

```text
GET  /v1/skills
GET  /v1/skills/{name}
POST /v1/skills/{name}/executions
GET  /v1/executions/{id}
```

Catalog discovery exposes every descriptor, including role-restricted skills;
descriptors include nullable `outputSchema` metadata (as exposed by the public Framework descriptor).
authorization is enforced when POST validates the JSON-object input. Accepted
requests return `202`, an `id`, and a `Location` header without waiting. Poll the
location until `COMPLETED` or `FAILED`. Successful results are the framework's
unchanged text. A known owned failure is a normal `200` representation whose
kind is `INPUT_VALIDATION`, `ACCESS_DENIED`, or `SKILL_FAILURE`.

```powershell
$headers = @{ Authorization = "Bearer $env:SIDECAR_ACCESS_TOKEN" }
Invoke-RestMethod http://localhost:8080/v1/skills -Headers $headers
$accepted = Invoke-RestMethod http://localhost:8080/v1/skills/triageSupportRequest/executions `
  -Method Post -Headers $headers -ContentType application/json -Body '{"customerMessage":"Checkout times out after payment; retrying sometimes creates duplicate orders."}'
Invoke-RestMethod "http://localhost:8080/v1/executions/$($accepted.id)" -Headers $headers
```

Missing or invalid credentials return `401`; malformed/non-object or invalid
input returns `400` (with validation issues when available); denied roles return
`403`; unknown, expired or foreign records return `404`; raw bodies over the
configured limit return `413`; capacity exhaustion returns `429`; and admission
during shutdown returns `503`. Transport errors use `application/problem+json`.
Malformed execution IDs return `400`; unsupported HTTP methods return `405`.
All API responses carry `Cache-Control: no-store`. Closing a client connection
does not cancel accepted work.

Ownership compares the verified issuer and subject as separate fields. A renewed
token for the same pair can poll existing work; changing either field cannot.
Records are memory-only and disappear on restart.
The GET response includes nullable `configurationSnapshotId`. It is null while
queued or if capture fails; after worker handoff it is the durable UUID of the
captured framework generation and remains on the terminal record even after
that generation retires. Observer diagnostics do not determine this field.

## Remote configuration authoring

An external coding agent can use the portable
[Sidecar authoring skill](../SKILL.md) and its
[Python 3.9+ client](client-usage.md).
The agent works in the integrating application's repository to design an
endpoint and its authorization checks. The checked-in
[record example](../examples/remote-authoring/README.md) pairs an HTTP executor skill
with shared target settings; the local test fixture exercises both allowed and denied record
access. Install or load the complete skill through the host; no specific agent
product or access allowlist is required.

Use a scoped personal token for authoring and a separate execution JWT for
verification. See [management access](management-access.md) for token permissions,
revocation, and browser-session differences, and [management API](management-api.md)
for exact draft, lease, handoff, validation and publication contracts.
After an ambiguous update, inspect current runtime state before another mutation.
Validation does not prove endpoint connectivity or data authorization; test both
allowed and denied application access after publication.

## Capacity, retention, and diagnostics

```yaml
loomspan-sidecar:
  executions:
    max-input-size: 1MB
    max-retained: 1000
    completed-ttl: 15m
    max-concurrent: 32
    max-queued: 128
    max-queued-input-size: 64MB
    diagnostics: NEVER # NEVER, ONERROR, or ALWAYS
```

Limits and TTL must be valid at startup. `max-input-size` applies to raw request
bytes before JSON parsing. Queue bytes measure the validated map's UTF-8 JSON
representation; equality with either size limit is allowed. `max-retained`
counts queued, running and terminal records. Terminal TTL begins at completion,
reads do not extend it, and expiry removes the whole record. Waiting work is
discarded when shutdown closes admission and dispatch; active framework calls
remain governed by `loomspan.shutdown.timeout`.

`NEVER` returns no diagnostic events. `ONERROR` retains available events for
failed work, and `ALWAYS` for both terminal outcomes. Selected public events are
published with the outcome and expire with it. They are available completed
history, not a guaranteed complete trace, and may contain business data. Sidecar
does not sanitize or truncate arbitrary inputs, results, framework messages, or
selected events. Record count, TTL and queued-input bytes therefore do not bound
total heap: running inputs, results, diagnostics, and uncooperative framework
work may consume additional memory.

Complete validated snapshots can be published while work runs. Old admitted
work keeps its captured definitions, execution settings and provider resources.
HTTP requests use captured settings through the shared application-owned client.
Restart loses all
queued, active and retained execution records, shutdown discards waiting work,
and unfinished admitted work may be interrupted at the framework deadline.
Framework-invalid selected content or credential-record integrity failures prevent
startup and readiness until corrected through a stopped database restore.
HTTP-specific configuration errors first fail on invocation before dispatch.
The HTTP client stays available for framework-owned admitted work until normal completion or the single
`loomspan.shutdown.timeout` cutoff, then close without a second drain period. The framework default is 30 seconds; configure the deployment termination grace to cover that budget and cleanup.

