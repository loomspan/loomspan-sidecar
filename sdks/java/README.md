# Java SDK

The Java 21 blocking SDK discovers skills, submits executions, reads status and
waits for completion. Core runs without Spring Boot, Sidecar or Framework classes.
Its HTTP and JSON dependencies are Apache HttpClient 5 and Jackson 3.

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
