# Loomspan Sidecar

Loomspan Sidecar runs Loomspan YAML skills as a separate service for applications
that want to call them over HTTP. An application sends a JWT-authenticated
request to start a skill and polls for its result. Operators use the included
browser console to validate and publish skill definitions and REST routes
without redeploying the calling application.

```text
Your application -- JWT + JSON --> Sidecar -- model or REST call --> Your services
Your application <-- execution result -- Sidecar
Operator -- browser console --> Sidecar (skill and route configuration)
```

Use Sidecar when your application needs a managed catalog of model-backed or
REST-backed skills, authenticated asynchronous execution, and a way to update
those skills while the service is running. Sidecar is a Java 21 / Spring Boot
4.1 service, not a library added to the calling application. It stores
configuration snapshots in SQLite; execution records are held in memory.

## Try the local service

For first administrator setup and SMTP-free recovery, follow the
[administrator access walkthrough](docs/admin-access.md). It covers the local
loopback HTTP console and the production Caddy HTTPS console.

Follow the [source-build prerequisites](docs/operations.md#build-from-source),
then build the JAR and image from the repository root. Docker Compose starts
Sidecar with a local JWT issuer and deterministic model fixture; no model
provider account is needed.

```powershell
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
$env:SIDECAR_IMAGE = "loomspan-sidecar:sc5-local"
docker compose -f examples/quickstart/compose.yaml up -d --build
$token = (Invoke-RestMethod http://localhost:8081/token).access_token
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod http://localhost:8080/v1/skills -Headers $headers
```

On a new volume, the response is `[]`: the service starts with no published
skills. This check verifies the running API and test token, not a completed
skill execution. The YAML files under `examples/quickstart/sidecar/` are
authoring examples; they are not automatically loaded into the database. Stop
the local stack with:

```powershell
docker compose -f examples/quickstart/compose.yaml down
```

On POSIX systems use `./mvnw` for the build. The local Compose file publishes
HTTP on loopback for development. For a deployed installation, follow the
[production Compose guide](examples/production/README.md), then sign in to the
console to add, validate, and publish skills.

## Connect an application

Configure Sidecar to trust your access-token issuer and audience. Your
application supplies a bearer access token and a JSON object as input:

```text
GET  /v1/skills
POST /v1/skills/{name}/executions  ->  202 Accepted + execution ID
GET  /v1/executions/{id}          ->  poll until COMPLETED or FAILED
```

The same verified issuer and subject can poll an execution. Sidecar does not
issue tokens. Execution records disappear on restart, so callers should handle
an unavailable or expired result. See the [integration guide](docs/integration.md)
for JWT configuration, skill and REST route authoring, request examples, results,
and limits.

## Install agent guidance

Read the official [loomspan-install instructions](https://github.com/loomspan/loomspan-framework/tree/main/agent-skills/loomspan-install) through your agent host. Select the exact Sidecar target from your deployment metadata; no application POM, Loomspan checkout or live server is required. The installer reads that release's POM to select matching framework guidance and defaults to the host's project scope. Updates change only selected skills, not dependencies or runtime targets.

The sibling skills are `loomspan` (orientation), `loomspan-docs` (framework semantics), `loomspan-console` (runtime evidence), and [loomspan-sidecar-authoring](agent-skills/loomspan-sidecar-authoring/SKILL.md) (server setup and direct API authoring). Install complete exact-revision folders through the host, including their resources and client.

Current source pins the locally installed framework beta.6-SNAPSHOT. The first Sidecar release waits for the tested framework beta.6 release on Maven Central, then pins that released artifact for final build and CI. Missing exact agent-skill sources stop preflight; published releases are never overwritten to add these skills.

Future `loomspan-sdk-<language>` skills live with their SDKs and use their own dependency versions and published compatibility facts. They own application lifecycle, security integration, request context and callbacks; Sidecar guidance owns server setup. Equal versions do not establish compatibility. Direct API users need no SDK.

## Planned SDKs

Client SDKs for Sidecar's public HTTP API will live in this repository under
`sdks/`, with independently built and published language packages. These are
placeholder directories only; no SDKs are implemented or published yet.

```text
sdks/
  java/     # Java; planned standalone Maven library
  go/       # Go
  node/     # Node.js (JavaScript)
  ruby/     # Ruby / Ruby on Rails
  python/   # Python
  dotnet/   # .NET
```

The Java SDK will have its own `sdks/java/pom.xml` when implemented. The root
`pom.xml` continues to build the Sidecar service.

## Configure and operate

The embedded console lets administrators manage accounts and lets editors
publish complete skill and REST route snapshots. A new database is empty.
Model connections, JWT trust, SMTP, and deployment URL values are configured
outside the console. The supported production deployment is one Sidecar instance
per persistent local SQLite volume, served through HTTPS by Caddy.

- [Integration guide](docs/integration.md): connect a caller, configure skills
  and routes, and use the execution API.
- [Operations guide](docs/operations.md): build, management console, snapshots,
  configuration reference, backup, recovery, and release status.
- [Production Compose guide](examples/production/README.md): deploy HTTPS,
  configure credentials, and set up the first administrator.
