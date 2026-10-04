# Setup and integration choices

Choose Sidecar when your application should invoke Loomspan over HTTP. For an
embedded Java service, follow the [Framework project](https://github.com/loomspan/loomspan-framework)
instead. Direct Sidecar callers need an HTTP client, not an SDK. The `sdks/`
directories in this checkout are placeholders; inspect an SDK owner's published
package and compatibility evidence before adopting one.

## Obtain a runtime

Select an exact Sidecar release and use that revision's documentation and POM.
The POM in this checkout declares Sidecar `1.0.0-beta.2` and Framework
`1.0.0-beta.7`. This is source metadata, not verification that a corresponding
image or release download is published. Check the
[Sidecar releases](https://github.com/loomspan/loomspan-sidecar/releases) for
available artifacts and checksums. Do not infer compatibility from equal numbers.

For a source evaluation, use Java 21, the included Maven wrapper, Docker with
Compose, and Python 3.9+ for the quickstart's portable commands. Maven resolves
the pinned Framework artifact; Sidecar does not build Framework source.
From the Sidecar repository root:

```powershell
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
```

On POSIX, use `./mvnw -B -ntp package` and the same Docker command. The image name
is a local tag. Building this image to evaluate Sidecar does not mean modifying
its runtime. Follow the [quickstart](quickstart.md) for a complete first execution.

## Choose configuration authority

| Mode | Where application configuration lives | Apply a change |
| --- | --- | --- |
| Database (default) | Complete skills, routes, execution settings and encrypted provider credentials in SQLite | Save a private draft, validate, publish through management UI/API |
| File | Mounted skill/route files and deployment execution configuration | Deploy the complete files and restart |

A new database is empty. Environment model settings do not seed or supplement a
database publication. The mode is selected at startup, with no fallback on load
failure. File management inspection is read-only; accounts and execution retain
their normal access rules. See [configuration modes and credentials](../agent-skills/loomspan-sidecar-authoring/references/configuration-modes.md)
for exact file locations, key provisioning, and rotation. The process-key table
lives in [operations](operations.md#sidecar-configuration-reference).

Framework supports OpenAI, Anthropic, Gemini and Ollama connections; use its
matching [connection reference](https://github.com/loomspan/loomspan-framework/blob/v1.0.0-beta.7/agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md)
for driver-specific settings. For real model access, choose a connection and a model your
account can use. Model aliases referenced by skills belong in the same complete
configuration. Database mode uses write-only credential controls and an external
encryption key. See [execution configuration](../agent-skills/loomspan-sidecar-authoring/references/execution-configuration.md).

## Establish access

- **Application execution:** configure trusted JWT issuer, audience, and key
  source; supply bearer access tokens on `/v1/**`. See [JWT authentication](../agent-skills/loomspan-sidecar-authoring/references/integration.md#jwt-authentication).
- **Configuration management:** initialize a local administrator, then use browser
  sessions or scoped personal tokens. See [administrator access](admin-access.md).
- **Runtime investigation:** enable Framework observability and connect the
  separate Console and its MCP endpoint. See [Console setup](console-setup.md).

The local quickstart's issuer and signing key are test fixtures. For deployment,
use your own identity provider and the [HTTPS Compose guide](../examples/production/README.md).
Protect and back up SQLite and the external credential key separately.

## Equip your development assistant

Follow Framework's [loomspan-install workflow](https://github.com/loomspan/loomspan-framework/blob/v1.0.0-beta.7/agent-skills/loomspan-install/SKILL.md)
through your assistant host. It resolves the exact Sidecar revision, reads its
Framework dependency, verifies complete source bundles, and uses the host's
supported skill manager. It does not require your application to have a POM or a
running Sidecar instance. Project scope is the default where supported.

For this checkout, Sidecar authoring guidance is beta.2; `loomspan`,
`loomspan-docs`, and `loomspan-console` guidance match Framework beta.7. An older
release may lack a required skill; report that limitation instead of substituting
latest guidance. Install complete folders, including references and the bundled
client. Guidance installation does not change runtime dependencies or configure MCP.

Then ask, for example:

```text
Use loomspan-sidecar-authoring to design an order lookup skill in this
application. Inspect our existing authentication and record authorization,
define the narrow endpoint, and prepare and validate a Sidecar draft.
```

The [Sidecar skill](../agent-skills/loomspan-sidecar-authoring/SKILL.md) guides
application work; Framework owns deeper skill syntax and semantics, and Console
owns runtime investigation. [Upgrades](upgrades.md) explains how to keep the set aligned.
