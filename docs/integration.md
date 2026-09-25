# Integrating an application with Loomspan Sidecar

Deploy Sidecar using the [production Compose guide](../examples/production/README.md), or follow the [README smoke check](../README.md). See [operations](operations.md) for deployment and administration.

The portable authoring skill owns the operational guide:

- [Runtime configuration](../agent-skills/loomspan-sidecar-authoring/references/integration.md#runtime-configuration)
- [REST routes](../agent-skills/loomspan-sidecar-authoring/references/integration.md#rest-skill-routes)
- [JWT authentication](../agent-skills/loomspan-sidecar-authoring/references/integration.md#jwt-authentication)
- [Execution requests, polling and errors](../agent-skills/loomspan-sidecar-authoring/references/integration.md#execution-api)
- [Remote configuration authoring](../agent-skills/loomspan-sidecar-authoring/references/integration.md#remote-configuration-authoring)
- [Retention and diagnostics](../agent-skills/loomspan-sidecar-authoring/references/integration.md#capacity-retention-and-diagnostics)
- [Complete record example](../agent-skills/loomspan-sidecar-authoring/examples/remote-authoring/README.md)

A new database has no skills. Sign in, author the complete configuration, validate and publish, then use an execution JWT to inspect the catalog, start work and poll its returned location. Copying example YAML does not activate it.
