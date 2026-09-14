# Loomspan Sidecar

Loomspan Sidecar is a Java 21 / Spring Boot 4.1 application that loads mounted,
model-backed Loomspan YAML skills and exposes an authenticated asynchronous
execution API. SC2 and SC3 add verified JWT identity, owner-scoped polling,
bounded in-memory workers and retention, prompt shutdown gates, and a generic
bounded outbound REST handler for mounted REST skills. Container packaging remains
SC5 work.

## Build locally

Local development uses
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` installed from
the local checkout at `C:\opendev\code\loomspan-framework` (initial baseline
`385729a254261de128df491505acd8898cc0a021`). The developer runs a new framework
install after framework changes so the installed library matches source and
documentation lookups in that directory. Then rerun affected Sidecar checks:

```powershell
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd spring-boot:run
```

On POSIX systems use `./mvnw`. Tests use temporary local files and loopback-only
configuration; they do not need a model provider account.

## Mounted configuration

The default mount layout is:

```text
/sidecar/skills/**/*.yaml
/sidecar/skills/**/*.yml
/sidecar/rest-routes.yaml
```

`loomspan.skills.locations` contains only the two skills patterns. The sibling
route file is loaded separately from `loomspan-sidecar.rest-routes-location` and
is never parsed as a skill manifest. Override either location at startup, for example:

```powershell
java -jar target/loomspan-sidecar-1.0.0-beta.4-SNAPSHOT.jar `
  "--loomspan.skills.locations=file:C:/mounted/skills/**/*.yaml,file:C:/mounted/skills/**/*.yml" `
  "--loomspan-sidecar.rest-routes-location=file:C:/mounted/rest-routes.yaml"
```

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

Keep credentials in environment variables. Skills and REST routes are immutable
startup snapshots; restart the process after changing either file.

## REST skill routes

Exactly one production handler is always present. Its required route document is
the single source of REST targets and skill mappings. When no REST skills exist,
mount an explicit empty document:

```yaml
targets: {}
routes: {}
```

Each REST skill must have one exact, case-sensitive route and every route must name
a registered REST skill and target. Invalid, unknown, duplicate, or incomplete
configuration fails startup without contacting any target. A complete example is:

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

All string fields and names support required Spring `${NAME}` placeholders.
Unresolved placeholders fail startup with their file and field, while resolved
secret values are omitted from diagnostics. Authentication modes are `none`,
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

Targets receive isolated Apache HTTP clients. Redirects and automatic retries are
disabled. Connect/read timeouts and the byte response cap apply per target; equality
with the cap succeeds and excess is stopped while streaming. Any bodyless 2xx returns
`""`. A nonempty successful response must be `application/json`, a structured
`+json` type, or `text/*`; its declared charset is honored and UTF-8 is used when
none is declared. JSON is returned as unchanged text, not parsed. Non-2xx and
transport/media/size failures become bounded, body-free skill diagnostics.

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

## Management endpoints

The application port remains Boot's default `8080`. Health is exposed separately
on management port `9091` at `/actuator/health` and
`/actuator/health/readiness`. Other Actuator endpoints are not exposed.
Loomspan observability routes are disabled by default, so no Console operator
API is exposed. When Loomspan Console observability is enabled, its
reserved `/_loomspan/observability/v1/**` namespace remains protected by the
framework's independent API-key filter. Console keys do not authenticate `/v1`.

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
Loomspan's `rbac_roles` checks. Sidecar has no user/role table and does not mint,
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
All API responses carry `Cache-Control: no-store`. Closing a client connection
does not cancel accepted work.

Ownership compares the verified issuer and subject as separate fields. A renewed
token for the same pair can poll existing work; changing either field cannot.
Records are memory-only and disappear on restart.

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

Skills and REST routes remain startup-only. Restart to activate either change;
doing so loses all queued, active and retained execution records. REST clients stay
available for framework-owned admitted work until normal completion or the single
`loomspan.shutdown.timeout` cutoff, then close without a second drain period. SC5
will add the image and exhaustive packaged resource-lifecycle/release proof; this
repository documentation does not claim that packaging or publication is complete.

## Dependency and release boundary

Production and test code may use Loomspan Java types only from `ai.loomspan.api`.

Push and pull-request CI is prepared to override the dependency with published
`1.0.0-beta.4`. Hosted verification is deferred until that artifact exists on
Maven Central. The delivery order is local Sidecar integration against the
snapshot, framework release checks and publication, then Sidecar verification
against the released artifact. This project does not build framework source in
its own build or CI.

See the [delivery handoff](ai/thoughts/beta4-handoff.md),
[SC2 phase](ai/thoughts/phases/phase-sc2.md), and
[SC3 phase](ai/thoughts/phases/phase-sc3.md).
