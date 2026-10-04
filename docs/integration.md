# Developing an application with Sidecar

Use the [quickstart](quickstart.md) for a complete first invocation or
[setup](setup.md) for deployment choices. Application work belongs in your
application repository; it does not require modifying Sidecar's Java code.

The [Sidecar authoring Agent Skill](../agent-skills/loomspan-sidecar-authoring/SKILL.md)
guides endpoint design, authorization, complete drafts, validation, and publication.
Its references are readable without an assistant:

- [Execution API, JWTs and polling](../agent-skills/loomspan-sidecar-authoring/references/integration.md#execution-api)
- [REST routes and transport](../agent-skills/loomspan-sidecar-authoring/references/integration.md#rest-skill-routes)
- [REST skill design and application authorization](../agent-skills/loomspan-sidecar-authoring/references/rest-configuration.md)
- [Publishable execution settings](../agent-skills/loomspan-sidecar-authoring/references/execution-configuration.md)
- [Management API and exact publication checks](../agent-skills/loomspan-sidecar-authoring/references/management-api.md)
- [Python authoring client](../agent-skills/loomspan-sidecar-authoring/references/client-usage.md)
- [Record-access example](../agent-skills/loomspan-sidecar-authoring/examples/remote-authoring/README.md)
- [Retention and diagnostics](../agent-skills/loomspan-sidecar-authoring/references/integration.md#capacity-retention-and-diagnostics)

A new database has no skills. Publish a complete configuration before invoking
it; copying YAML into the checkout does not activate it. Management credentials
author configuration, while separate execution JWTs invoke skills. Validate the
configuration, then verify real application access, including denied records.
Configuration validation alone cannot prove endpoint connectivity or authorization.
