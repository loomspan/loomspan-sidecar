# Java SDK

The Java 21 blocking SDK discovers skills, submits executions, reads status and
waits for completion. Core runs without Spring Boot, Sidecar or Framework classes.
Its HTTP and JSON dependencies are Apache HttpClient 5 and Jackson 3.

The [remote recipes](../../docs/remote-recipes.md) configure direct Boot HTTPS or
paired Caddy mTLS independently for SDK invocation and Sidecar callbacks, with
provided signing and file-delivered public JWKS.

Build and locally install the independent reactor from the Sidecar checkout:

```powershell
mvn -B -ntp -f sdks/java/pom.xml clean install
python sdks/java/tests/test_build_boundary.py
```

Use `ai.loomspan:loomspan-sdk-core:0.1.0-SNAPSHOT` in plain Java, or
`ai.loomspan:loomspan-sdk-spring-boot-starter:0.1.0-SNAPSHOT` with Spring Boot
4.1.0. SDK development versions advance independently from Sidecar.
The build rejects direct and transitive Framework/Sidecar dependencies and keeps
Boot out of core. Boundary tests use disposable synthetic artifacts.

```java
import ai.loomspan.sdk.CredentialSnapshot;
import ai.loomspan.sdk.SidecarClient;
import ai.loomspan.sdk.WaitOptions;
import java.net.URI;
import tools.jackson.databind.json.JsonMapper;

// Load an already-issued execution JWT from the application's credential source.
var credential = CredentialSnapshot.of(executionJwt);
try (var client = SidecarClient.builder()
        .baseUrl(URI.create("https://sidecar.example.com"))
        .build()) {
    var catalog = client.listSkills(credential);
    var skill = client.getSkill("summarize", credential);
    var input = JsonMapper.builder().build().createObjectNode().put("text", "Hello");
    var accepted = client.submit("summarize", input, credential);
    var current = client.getExecution(accepted.id(), credential);
    var terminal = client.await(accepted.id(), credential, WaitOptions.defaults());
    // Inspect terminal.status() and terminal.failure(); result() is opaque text.
}
```

`submit` requires a JSON object and returns a valid acceptance UUID immediately.
It does not compile business schemas or wait for execution. Discovery preserves
public metadata and schema strings. Status returns ID, skill, status, timestamps,
result, primary failure, configuration snapshot ID and available events. Early
snapshots can lack a configuration ID; event history can be absent.

Results remain the exact decoded server string, including empty, non-JSON,
malformed JSON-looking and Unicode text. A null result remains null. Only the
server's `COMPLETED` or `FAILED` status determines the outcome; a failed execution
returns a normal terminal snapshot with its primary failure. The SDK does not
parse or sanitize results or business failure/event details.

Each HTTP request is attempted once. The client disables retries, redirects and
authentication rechallenge replay, and never follows `Location`. Status URLs
are built under the configured base URL and must return the requested ID.
Accepted skill names are encoded as one UTF-8 path segment; blank names, controls,
slashes, backslashes, percent signs and the names `.` and `..` fail locally.

| Observation | Outcome |
| --- | --- |
| Invalid local input, credential or closed client | `LocalFailureException`; no request sent |
| Server/proxy HTTP error | `HttpFailureException` with status and available problem details; work may have occurred |
| Unusable or lost submission response after possible send | `AcceptanceUnknownException`; work may be running; no resubmission |
| Invalid status envelope, mismatched ID or response size overflow | `ProtocolException`; no silent truncation |
| Wait timeout or interruption | `WaitTerminatedException`; local waiting ends; server work may continue |
| Server status `FAILED` | Terminal snapshot with original primary failure; no inferred rollback |

Exceptions for status reads retain the requested execution ID. During `await`,
errors also retain the last valid snapshot through `executionId()` and
`lastSnapshot()`. An acceptance ID is available only after a complete reliable
acceptance response; partial bodies and arbitrary headers are never ID sources.
HTTP errors do not prove rejection or absence of effects. Do not automatically
resubmit after an uncertain outcome.

`WaitOptions.defaults()` sets a 60-second total timeout and 250-millisecond poll
interval. Use `new WaitOptions(timeout, pollInterval)` to change them. The client
polls successful `QUEUED`/`RUNNING` reads within the remaining total deadline and
stops on the first read failure. Thread interruption restores the interrupt flag.
Wait expiration, HTTP 401/403/404, interruption and client close never cancel
server work. Closing releases the owned connection pool, is idempotent and rejects
new operations. Use try-with-resources or let Spring manage the bean lifecycle.

The builder requires an absolute HTTP(S) base URL with a host and no userinfo,
query or fragment. An optional base path is preserved: `https://host/prefix/`
produces requests under `/prefix/v1/...`.

| Builder setting | Boot property | Default |
| --- | --- | --- |
| `baseUrl(URI)` | `loomspan-sdk.base-url` | Required |
| `connectTimeout(Duration)` | `loomspan-sdk.connect-timeout` | 2 seconds |
| `responseTimeout(Duration)` | `loomspan-sdk.response-timeout` | 10 seconds |
| `maxResponseSize(long)` | `loomspan-sdk.max-response-size` | 8 MiB (8388608 bytes) |
| `sslContext(SSLContext)` | `loomspan-sdk.ssl-bundle` | Standard Java trust |

Timeouts and response size limits must be positive and finite. Each response is
bounded by the response timeout, including body reads. `await` further bounds each
call by its remaining total budget. Over-limit responses fail rather than truncate.

TLS always verifies certificates and hostnames. Core accepts standard Java
`SSLContext` trust/key material; the starter resolves a named standard Spring Boot
SSL bundle. No insecure setting exists. Use HTTPS when credentials leave a trusted
local boundary. A missing configured bundle fails startup. Standard trust settings
are configured through Boot, for example:

```yaml
loomspan-sdk:
  base-url: https://sidecar.example.com
  connect-timeout: 2s
  response-timeout: 10s
  max-response-size: 8388608
  ssl-bundle: sidecar
spring:
  ssl:
    bundle:
      pem:
        sidecar:
          truststore:
            certificate: file:/run/trust/sidecar-ca.pem
```

Inject `SidecarClient` in the application. A user-defined client bean overrides
autoconfiguration. The starter uses the core builder, so validation, defaults and
request semantics match plain Java. Wait options stay per-call.

`CredentialSnapshot` accepts an already-issued execution JWT, does no client-side
JWT verification or refresh, and sends it unchanged. It has no ordinary token
getter and keeps token bytes out of serialization, `toString` and SDK errors or
default logs. Creation rejects empty, whitespace, control and non-ASCII transport
values without parsing the JWT. Applications control credential storage and must avoid explicitly
enabling third-party HTTP wire/header logging that could expose bearer tokens or
business payloads. Business inputs, results and diagnostics may contain sensitive
content supplied by the application.

The neighboring `loomspan-sidecar-test-suite` documents its separate SDK lane in
`docs/sdk-lane.md`: a plain Java core consumer against real Sidecar with
provider-free completion/failure and lost-response no-replay evidence. Independent
request and synthetic effect counts describe those fixtures, without promising
exactly-once business execution. Standard and hardened recipes remain separate.

## Execution-token issuance

The SDK issues execution credentials for users authenticated by your application.
Capture identity at the beginning of the operation, before processing business
inputs. Spring applications inject `SpringExecutionTokenIssuer` and call
`issueCurrent()`, then pass the returned `CredentialSnapshot` explicitly to the
client. Null, anonymous and unauthenticated Spring identities are rejected.
The adapter reads the current authentication, subject and authorities once;
there is no async context wrapper, principal reconstruction or account lookup.

### Colocated quickstart

Create a directory owned by the application account and set deployment-appropriate
permissions before startup. The SDK neither creates it nor inspects or changes its
permissions. Configure both required values explicitly:

```yaml
loomspan-sdk:
  base-url: http://localhost:8080
  signing:
    mode: colocated
    colocated:
      key-directory: /run/loomspan-keys
  issuance:
    roles: [ROLE_ORDER_EDITOR]
```

No signing mode is packaged in the starter. Missing mode fails startup with this
quickstart reference. Settings belonging to the other mode are rejected.
Colocated signing creates a fresh 2048-bit RSA private key only in memory and
atomically replaces `/run/loomspan-keys/jwks.json` with its public key. Each app
restart changes the kid: old tokens stop validating as receivers adopt the new
file. Use one app instance per key directory (the sole writer); replicas or
multiple apps use provided signing. Directory ownership and permissions are
deployment responsibilities. Unsupported atomic replacement fails startup.

In plain Java, initialize the signer once at startup and construct identity only
from trusted authentication code:

```java
var source = SigningKeys.initialize(SigningSettings.colocated(Path.of("/run/loomspan-keys")));
var settings = new IssuanceSettings("loomspan-app", Duration.ofMinutes(15),
        List.of(), List.of("ROLE_ORDER_EDITOR"));
var issuer = new ExecutionTokenIssuer(source, settings);
// authenticatedUser comes from your server's authentication, never request JSON.
var credential = issuer.issue(new AuthenticatedIdentity(
        authenticatedUser.stableSubject(), authenticatedUser.authorities()));
client.submit("summarize", input, credential);
```

The core API uses `java.nio.file.Path`, `java.time.Duration`, `java.util.List`
and classes in `ai.loomspan.sdk`. Treat `AuthenticatedIdentity` as a trusted
authentication snapshot; never deserialize business input into it. Subject and
authorities are nonblank and defensively copied.

### Provided signing

Operators provide RSA keys of at least 2048 bits in `PKCS12`, `JKS`, or unencrypted
PKCS#8 PEM (`-----BEGIN PRIVATE KEY-----`). PKCS#1 and encrypted PEM are rejected;
convert them or use an encrypted keystore. Optional PEM certificates may be PEM
or DER X.509 and must match the private key. Keystores use their alias certificate
chain. Public JWKS includes `x5c` when a certificate exists.

Generate a keystore using a password already supplied in the environment:

```sh
keytool -genkeypair -alias signer -keyalg RSA -keysize 2048 -validity 365 -dname "CN=Loomspan signer" -storetype PKCS12 -keystore signing.p12 -storepass:env SIGNING_STORE_PASSWORD
```

```yaml
loomspan-sdk:
  signing:
    mode: provided
    provided:
      format: PKCS12
      key-location: /run/secrets/signing.p12
      key-alias: signer
      keystore-password-reference: env:SIGNING_STORE_PASSWORD
      jwks-export: /run/loomspan-public/jwks.json
```

For JKS choose `format: JKS`; an optional `key-password-reference` can differ
from the store password and otherwise defaults to the store reference. Passwords
accept only `env:VARIABLE_NAME` or `file:/absolute/path` references. Secret files
are UTF-8; one terminal LF or CRLF is removed, with other characters preserved.
Missing or empty secret targets fail startup. Literal `keystore-password` and
`key-password` settings are forbidden. Do not put passwords in reference values.

For PEM configure `format`, `key-location`, `jwks-export` and optionally
`certificate-location`; omit aliases and password references. For keystores,
`format`, `key-location`, `key-alias`, `keystore-password-reference` and
`jwks-export` are required, and `certificate-location` is forbidden. Export parents
must exist; the SDK never creates directories. The export must be separate from
the provided key, certificate and password files; overlapping paths fail startup
without replacing those inputs. In core construct
`new SigningSettings("provided", null, new SigningSettings.Provided(format,
keyPath, alias, storeReference, keyReference, certificatePath, exportPath))`
with `SecretReference` objects and null for absent options.

Provided keys load at startup only: changing private material requires a restart.
Missing/invalid, mismatched, weak or non-RSA material fails startup and never
generates a fallback. The SDK warns when the signing certificate has expired or
expires within 30 days; it continues signing. This warning does not affect TLS
validation. Neither private material nor tokens are logged by SDK issuance.

The export is a complete public-only JWKS written to a sibling temporary file
and atomically replaced. Filesystem publication failure aborts startup; there is
no ordinary-move fallback. The exported content is the trust file consumed by
the Sidecar file-based JWT trust work in PR 8.1.4, in either application
ConfigurationMode. Callback validation uses its own application-side trust below.

### Token profile and exact authorities

All issuance settings use the `loomspan-sdk.issuance` prefix:

| Setting | Default / behavior |
| --- | --- |
| `issuer` | `loomspan-app`; independent of `spring.application.name` |
| `lifetime` | `15m`; any positive duration, WARN above one hour, no policy maximum |
| `additional-audiences` | Empty; added to `loomspan-sidecar` and the issuer, de-duplicated |
| `roles` | Empty exact authority allowlist |

Tokens use RS256, RFC 7638 RSA public-key thumbprint `kid`, JOSE
`typ=loomspan-execution+jwt`, and `token_use=loomspan_execution`. Claims are
`iss` (issuer), `sub` (stable authenticated subject), `aud` (the audiences above),
`iat` and `exp` (including fractional seconds), unique `jti`, and a string-array
`roles` that may be empty. Timestamp range overflow fails clearly. Core
`IssuanceSettings.defaults()` uses these same defaults.

Only allowlisted authorities the authenticated user actually holds are included.
No prefix is added, stripped or mapped; application role-prefix settings remain
unchanged. Under Spring's standard role prefix,
`hasRole('ORDER_EDITOR')` checks `ROLE_ORDER_EDITOR`, as does
`hasAuthority('ROLE_ORDER_EDITOR')`. Allowlist `ROLE_ORDER_EDITOR` to carry that
exact authority. `ORDER_EDITOR` is a distinct name. Roles remain snapshots until
expiry, and business input or model output cannot choose the subject or roles.

### Receiving callbacks in Spring

The servlet starter installs one stateless bearer-JWT `SecurityFilterChain` at
`Ordered.HIGHEST_PRECEDENCE` on the application's existing server. Keep callback
controllers under `/loomspan/callbacks` (including its descendants) and avoid
competing chains at that precedence. Routes outside this prefix remain the
application's responsibility. Ordinary form login and CSRF rules belong to your
own lower-precedence application chain; its URL authorization is not inherited
by callbacks.

```java
@Configuration
@EnableMethodSecurity
class ApplicationSecurity {
    @Bean
    SecurityFilterChain browser(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(r -> r.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults()).build();
    }
}

@RestController
class OrdersCallback {
    @PostMapping("/loomspan/callbacks/orders/{id}")
    @PreAuthorize("hasRole('ORDER_EDITOR')")
    void update(@PathVariable String id, JwtAuthenticationToken authentication) {
        // Check the record owner using BOTH verified issuer and subject.
        orders.requireOwner(id, authentication.getToken().getClaimAsString("iss"),
                authentication.getToken().getSubject());
        orders.update(id);
    }
}
```

Enable standard Spring method security explicitly. The SDK authenticates; your
annotations and business checks authorize operations and records. Token `roles`
become authorities exactly: `ROLE_ORDER_EDITOR` satisfies standard
`hasRole('ORDER_EDITOR')`; literal `ORDER_EDITOR` does not. The principal is a
verified Spring `Jwt`, available through `JwtAuthenticationToken`, and the context
is cleared after each request.

Only GET and POST are allowed. Callbacks require a single Authorization bearer
header; browser cookies and sessions never authenticate them. Missing, duplicate,
malformed or invalid credentials and any `access_token` query parameter return
401 with `WWW-Authenticate: Bearer`, without redirect or `/error` forwarding.
CSRF is disabled only in this chain, and no session is read or created.
Application denials return 403. Authenticated PUT/PATCH/DELETE/HEAD/OPTIONS return
403; without a bearer they return 401. The default Spring firewall rejects TRACE,
semicolon path parameters and unnormalized double-slash paths with 400 before
authentication. Earlier servlet connector syntax rejection follows the container.

| `loomspan-sdk.callbacks` setting | Default / behavior |
| --- | --- |
| `path-prefix` | `/loomspan/callbacks`; one literal absolute non-root prefix, trailing slash normalized; no wildcards, query, fragment, backslash or empty/dot segments |
| `audience` | Issuance issuer; optional nonblank receiver audience override |
| `overlap-jwks` | Absent; optional local public RSA JWKS file for rotation overlap |

JWTs must have an RS256 trusted signature, `typ=loomspan-execution+jwt`,
`token_use=loomspan_execution`, valid claim shapes, the exact configured
`loomspan-sdk.issuance.issuer`, the receiver audience and valid timestamps with
60 seconds of skew. There is no callback issuer setting. If overriding the
audience, include it in `issuance.additional-audiences` for tokens intended for
that receiver. Sidecar still checks strict credential expiry before sending.

The callback validator always owns the startup signer's public key in colocated
or provided mode, independently of Sidecar's configured JWKS. With no overlap
file, that own key is sufficient. A configured overlap file is loaded immediately,
polled every second and reloaded on unknown `kid` at most once per second.
Every complete public RSA key set is validated and published atomically; removed
keys cease to authenticate new requests. Missing, unreadable, malformed or empty
configured overlap withdraws ALL callback trust, including the own key, until a
valid file is restored. There is no stale fallback, private signer reload or
public key-hosting endpoint. Trust updates do not cancel already admitted work.
