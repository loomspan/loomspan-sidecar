# Loomspan Sidecar

Loomspan Sidecar brings AI reasoning into your application through an HTTP
service. Define reusable **application skills** that reason with a model or call
your application's REST endpoints. Compose them into a hierarchy so a model can
break down a task within the capabilities you provide.

For example, turn a customer's checkout failure report into an engineering
triage brief, then add authorized order and payment lookups when the workflow
needs evidence from your systems.

## Why Sidecar?

- **Integrate from any language.** Send JSON over an authenticated HTTP API,
  then poll for the result. No Loomspan SDK is required.
- **Keep application operations under your control.** Expose narrow REST
  endpoints with their own authentication and data-access checks.
- **Evolve skills independently of the caller.** Publish validated configuration
  through Sidecar's management UI/API, or deploy files and restart.
- **Investigate with your assistant.** Use version-matched Agent Skills for
  development and Loomspan Console MCP for runtime evidence.

Choose Sidecar when a separate service fits your application. Choose
[embedded Loomspan Framework](https://github.com/loomspan/loomspan-framework)
when you want in-process Java integration and Java application skills. Sidecar
hosts YAML model and REST skills; it does not host Java `@SkillMethod` skills.
Read [architecture and tradeoffs](docs/architecture.md) before choosing the boundary.

## Work with your AI assistant

Loomspan is designed for AI-assisted integration. **Application skills** execute
inside Loomspan; **Agent Skills** guide your development assistant. Installing
guidance and connecting Console MCP are separate setup steps. Humans can read
the same documentation and references directly.

Paste this discovery prompt into your assistant:

```text
Read https://github.com/loomspan/loomspan-sidecar/blob/main/README.md
and follow its architecture and setup links. Explain what Sidecar would add
to my application, its tradeoffs, and whether HTTP Sidecar or embedded Java
fits my needs. Review the linked Loomspan Agent Skill installation guidance.
Determine the exact Sidecar version I intend to use and its bundled Framework
version before selecting guidance. Explain how Console MCP supports runtime
troubleshooting and how it differs from Sidecar's management UI and API.
Distinguish verified capabilities from assumptions. Start with an assessment
before changing my project.
```

When ready, follow [assistant setup](docs/setup.md#equip-your-development-assistant)
to install matching guidance, then [Console setup](docs/console-setup.md).

## From customer report to engineering triage

Start with a skill that assesses a support report and identifies what an engineer
should investigate:

```yaml
name: triageSupportRequest
description: Turn a customer issue into an actionable engineering triage brief.
model: assistant
prompt: >
  Assess customer impact and urgency, distinguish reported facts from hypotheses,
  and propose the next investigation steps. Identify missing information.
  Do not claim to have checked systems or taken action.
input_schema:
  type: object
  properties:
    customerMessage: { type: string }
  required: [customerMessage]
  additionalProperties: false
```

After publishing it with an `assistant` model connection, your application sends:

```http
POST /v1/skills/triageSupportRequest/executions
Authorization: Bearer <execution-access-token>
Content-Type: application/json

{"customerMessage":"Since this morning's deployment, checkout times out after payment. Three customers were charged without an order confirmation. Retrying sometimes creates two orders."}
```

Sidecar returns `202 Accepted` with an execution ID and `Location`. Poll that
location with a token for the same issuer and subject until `COMPLETED` or
`FAILED`. The result is text: an assessment of the reported impact, possible
causes, and next checks. This skill reasons over the supplied report; it does
not inspect payments or orders, and its conclusions need review.

The [complete quickstart](docs/quickstart.md) provides the build, local JWT
fixture, administrator setup, model credentials, publication, invocation, and
polling steps. It makes a real model request. To extend it, add application REST
capabilities using the [authoring guide](agent-skills/loomspan-sidecar-authoring/SKILL.md).

## Before deploying

Sidecar is in beta. Pin an exact runtime and review [upgrade guidance](docs/upgrades.md).
This checkout's POM and authoring bundle identify Sidecar `1.0.0-beta.2` with
Framework `1.0.0-beta.7`; their version numbers advance independently.

The supported deployment uses one Sidecar instance per persistent local SQLite
volume. Configuration and accounts persist; execution records are memory-only
and expire or disappear on restart. Your application supplies execution JWTs;
Sidecar does not issue them. See [setup](docs/setup.md) for authentication and
configuration choices, and [operations](docs/operations.md) for recovery.

| Goal | Start here |
| --- | --- |
| Evaluate architecture and tradeoffs | [Architecture](docs/architecture.md) |
| Run a complete first skill | [Quickstart](docs/quickstart.md) |
| Set up deployment and assistant guidance | [Setup](docs/setup.md) |
| Author skills and integrate HTTP calls | [Application integration](docs/integration.md) |
| Investigate execution with Console MCP | [Console setup](docs/console-setup.md) |
| Deploy HTTPS and manage recovery | [Production Compose](examples/production/README.md), [operations](docs/operations.md) |
| Find the authoritative reference | [Documentation map](docs/README.md) |

Developing an application **with** Sidecar is different from developing Sidecar
itself. Repository contributors should begin with [AGENTS.md](AGENTS.md);
maintainer audit material is parked in [ai/throughts](ai/throughts/readme-maintainer-material.md)
for review, outside the application learning path.
