# Integrating an application with Loomspan Sidecar

Sidecar is a separate HTTP service. Applications call its JWT-protected execution API; operators publish complete skill and REST route snapshots through the management console. See the [README](../README.md) for the local smoke check and the [operations guide](operations.md) for deployment and administration.

## From installation to first execution

1. Deploy Sidecar with a persistent SQLite volume and configure JWT issuer,
   audience, model connections if needed, SMTP, and the console URL. The
   [production Compose guide](../examples/production/README.md) walks through
   this setup and first administrator sign-in.
2. Sign in to `/management/login`. On **Edit configuration**, add skill YAML
   and any REST targets/routes it uses, then validate and publish the complete
   configuration. A new database has no skills. The
   [quickstart skill files](../examples/quickstart/sidecar/skills/planner.yaml)
   illustrate the manifest format; copying those files alone does not activate
   them.
3. Obtain an access token from your configured issuer with the intended
   audience and call `GET /v1/skills` to confirm the published skill name.
4. Start an execution and poll its returned `Location` until its status is
   `COMPLETED` or `FAILED`. The [API example](#execution-api) shows the calls.

The browser management session and the execution bearer token are separate
credentials. Sidecar verifies execution tokens but does not issue them.

## Runtime configuration

The database-selected snapshot is the only runtime skill and REST route source.
The framework's documented `loomspan.skills.locations` points to a packaged
empty directory so the initial catalog is empty. Sidecar checks that baseline
before publishing the selected snapshot. A new database starts with no skills.
Authored skill YAML and REST text remain unchanged in durable snapshots;
resolved deployment values are never written over references.

Each model-backed manifest names a model. Configure its connection and provider
model explicitly; the manifest does not create them:

```yaml
loomspan:
  connections:
    primary:
      driver: openai
      base-url: ${MODEL_BASE_URL}
      api-key: ${MODEL_API_KEY}
  models:
    primary:
      connection: primary
      provider-model: ${MODEL_NAME}
```

Keep credentials in environment variables. Model connection and URL allowlist
changes require restart; a complete snapshot publication changes skills and
REST routes without restart. Successful framework publication clears only the
publishing user's saved draft and lease; other users' durable drafts become
stale and require explicit current-base reconciliation and fresh validation.
Publish rechecks live account, session, editing generation, saved revision,
validation and base after waiting for the publication lock. Import and rollback
load into the same draft workflow before validation and publication, assigning
fresh local IDs with source provenance when published.

## REST skill routes

Exactly one production handler is always present. Each complete snapshot has
one authored route document. When no REST skills exist, use:

```yaml
targets: {}
routes: {}
```

Each REST skill must have one exact, case-sensitive route and every route must name
a registered REST skill and target. Invalid, unknown, duplicate, or incomplete
configuration fails validation or startup without contacting any target. A complete example is:

```yaml
targets:
  expenses:
    base-url: https://expenses.internal/api
    auth:
      mode: caller-passthrough
    ssl-bundle: expenses-mtls
    connect-timeout: 5s
    read-timeout: 30s
    max-response-size: 1MB
  customers:
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
    target: expenses
    method: GET
    path: /expenses/{category}
  customerLookup:
    target: customers
    method: POST
    path: /customers/{id}
```

`base-url` accepts a literal HTTP(S) URL or a whole-value `${NAME}` reference.
The name must appear in `loomspan-sidecar.url-variables`; only the process
environment supplies its value. Missing, blank, composed, defaulted, and invalid
URLs fail validation or startup. Unused allowlist entries require no value.
Other string fields and names retain required Spring `${NAME}` placeholder
behavior. Resolved secret values are omitted from diagnostics. Authentication modes are `none`,
`static`, and `caller-passthrough`; configured headers are accepted only for
`static`. Invocation input never becomes a header. Every request sends
`Accept: application/json, text/*`.

GET and POST are the only methods. `{name}` occupies a complete path segment and
consumes the same-named input. GET encodes remaining non-null string, finite-number,
and boolean values as query parameters. POST sends the remaining JSON-compatible
object, preserving nulls and nested arrays/objects and sending `{}` when all fields
were consumed. Resource, file, stream, non-string object key, nonfinite number, and
other non-JSON values fail before I/O. Segment encoding and base-path confinement
prevent input from replacing the configured authority or traversing above its base.

Each framework generation owns matching routes and isolated Apache HTTP clients.
Old generations retain them until public retirement establishes that admitted
and nested work has finished. Shutdown retains clients through the framework's
completion or cutoff and closes them without another drain period. Redirects, automatic retries, and
automatic cookie storage/replay are disabled; caller sessions are not shared across
executions. Literal plus signs in GET query names and values are percent-encoded.
Connect/read timeouts and the byte response cap apply per target; equality
with the cap succeeds and excess is stopped while streaming. Any bodyless 2xx returns
`""`. A nonempty successful response must be `application/json`, a structured
`+json` type, or `text/*`; its declared charset is honored and UTF-8 is used when
none is declared. JSON is returned as unchanged text, not parsed. Non-2xx and
transport/media/size failures become bounded, body-free skill diagnostics. Early
failures discard the connection before cleanup can drain the unread response.

Outbound TLS uses standard Boot SSL bundles, including client keys for mTLS; bundle
configuration remains under `spring.ssl.bundle.*`, for example:

```yaml
spring:
  ssl:
    bundle:
      jks:
        expenses-mtls:
          keystore:
            location: file:/sidecar/tls/client.p12
            password: ${CLIENT_STORE_PASSWORD}
            type: PKCS12
          truststore:
            location: file:/sidecar/tls/trust.p12
            password: ${TRUST_STORE_PASSWORD}
            type: PKCS12
```

## JWT authentication

Every `/v1/**` request requires a bearer access token. Sidecar validates its
signature, issuer, audience, expiration and lifetime and requires nonblank `iss`
and `sub` claims. It supports issuer discovery (the default), an explicit JWKS
URL, or an RSA public key; configure at most one explicit key source:

```yaml
loomspan-sidecar:
  auth:
    jwt:
      issuer-uri: https://identity.example.com
      audience: loomspan-sidecar
      # jwk-set-uri: https://identity.example.com/.well-known/jwks.json
      # public-key-location: file:/sidecar/keys/access-token.pem
      clock-skew: 60s
      roles-claim: roles
      role-prefix: ROLE_
```

Roles are read from the configured trusted claim. The prefix is also applied to
Loomspan's `rbac_roles` checks. Execution authentication has no local user/role table and Sidecar does not mint,
refresh or exchange tokens. A backend using browser sessions must supply a
backend-issued access token suitable for both Sidecar's and any callback target's
audience checks; cookies and OIDC ID tokens are not assumed to be API credentials.
Queue time consumes token lifetime. Once admitted, local work keeps the identity
and roles already verified even if the token expires. A `caller-passthrough` target
receives that original token unchanged and must independently verify its signature,
issuer, suitable audience, lifetime, subject, and roles. Sidecar does not recheck at
worker handoff and does not refresh, mint, or exchange the token, so size its lifetime
for queueing and callback time or expect the callback to reject it as a skill failure.

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
authorization is enforced when POST validates the JSON-object input. Accepted
requests return `202`, an `id`, and a `Location` header without waiting. Poll the
location until `COMPLETED` or `FAILED`. Successful results are the framework's
unchanged text. A known owned failure is a normal `200` representation whose
kind is `INPUT_VALIDATION`, `ACCESS_DENIED`, or `SKILL_FAILURE`.

```powershell
$headers = @{ Authorization = "Bearer $env:SIDECAR_ACCESS_TOKEN" }
Invoke-RestMethod http://localhost:8080/v1/skills -Headers $headers
$accepted = Invoke-RestMethod http://localhost:8080/v1/skills/example/executions `
  -Method Post -Headers $headers -ContentType application/json -Body '{"message":"hello"}'
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
work keeps its captured definitions and REST resources. Restart loses all
queued, active and retained execution records, shutdown discards waiting work,
and unfinished admitted work may be interrupted at the framework deadline.
Invalid selected content prevents startup and readiness until corrected through
a stopped database restore. REST clients stay
available for framework-owned admitted work until normal completion or the single
`loomspan.shutdown.timeout` cutoff, then close without a second drain period. SC5
uses the framework's 30-second default; the production Compose file gives the
process a 45-second termination grace for that budget and cleanup.

