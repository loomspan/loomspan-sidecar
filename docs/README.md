# Sidecar documentation

Start with the [README](../README.md). These documents serve developers evaluating,
integrating, and operating Sidecar in their applications.

| Decision or task | Authoritative home |
| --- | --- |
| Architecture and adoption tradeoffs | [Architecture](architecture.md) |
| Environment, runtime choice, assistant installation | [Setup](setup.md) |
| First complete application skill | [Quickstart](quickstart.md) |
| Console and MCP connection | [Console setup](console-setup.md) |
| Version changes and development-data compatibility | [Upgrades](upgrades.md) |
| Deployment, backup and process configuration keys | [Operations](operations.md) |
| First administrator and operator password recovery | [Administrator access](admin-access.md) |
| HTTPS Compose deployment | [Production guide](../examples/production/README.md) |

## Ongoing application development

The portable [Sidecar authoring Agent Skill](../agent-skills/loomspan-sidecar-authoring/SKILL.md)
guides ongoing application work. Its focused references are ordinary Markdown,
usable without an assistant:

| Contract or workflow | Home |
| --- | --- |
| Execution HTTP API, JWTs, REST transport and retention | [Integration reference](../agent-skills/loomspan-sidecar-authoring/references/integration.md) |
| REST skill design and application authorization | [REST authoring](../agent-skills/loomspan-sidecar-authoring/references/rest-configuration.md) |
| Publishable model and execution settings | [Execution configuration](../agent-skills/loomspan-sidecar-authoring/references/execution-configuration.md) |
| File/database authority and encryption-key operations | [Configuration modes](../agent-skills/loomspan-sidecar-authoring/references/configuration-modes.md) |
| Draft, lease, validation and publication API | [Management API](../agent-skills/loomspan-sidecar-authoring/references/management-api.md) |
| Accounts, sessions and personal-token security | [Management access](../agent-skills/loomspan-sidecar-authoring/references/management-access.md) |
| Snapshot identity, restart selection and fault semantics | [Configuration lifecycle](../agent-skills/loomspan-sidecar-authoring/references/configuration-lifecycle.md) |
| ZIP transfer format and import/export | [Configuration bundles](../agent-skills/loomspan-sidecar-authoring/references/configuration-bundles.md) |
| Bundled Python authoring client | [Client workflow](../agent-skills/loomspan-sidecar-authoring/references/client-usage.md) |

Docs explain architecture, tradeoffs, environment setup, and upgrades. Agent Skills
guide tasks and carry their detailed contracts. Installation and upgrade workflows
may use skills while the background lives here. Link to the owning reference
rather than maintaining parallel specifications. Use the revision matching your
runtime; `main` is not compatibility evidence for an older release.

Framework owns skill syntax and execution semantics; Console owns its runtime
package and MCP tools. [Setup](setup.md) links those projects and explains version
selection. Sidecar contributor process and historical release evidence belong
outside this application map; see [repository guidance](../AGENTS.md) and the
[audit](../ai/throughts/readme-audit.md).
