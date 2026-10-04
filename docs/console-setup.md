# Console and MCP setup

Sidecar includes a **management UI** for accounts and configuration. **Loomspan
Console** is a separate application for runtime investigation, with read-only
MCP tools for development assistants. Its version is coordinated with the bundled
Framework, not Sidecar's version number: this checkout pairs Framework beta.7
with Console beta.7.

## Enable Sidecar observability

Observability is disabled by default. Configure the process with:

```yaml
loomspan:
  observability:
    enabled: true
    auth:
      api-key: ${LOOMSPAN_OBSERVABILITY_API_KEY}
```

Generate at least 32 random bytes and encode them as unpadded base64url; supply
the resulting key through protected deployment configuration. Restart Sidecar
after changing these process settings. They do not belong in database-mode
`executionConfigurationYaml` drafts. Sidecar already permits the reserved
`/_loomspan/observability/v1/**` namespace to reach Framework's independent key
filter. No Java security configuration changes are needed.

Point Console at the application listener (8080 locally), not the health port
9091. Use HTTPS beyond a trusted local boundary. The observability key cannot
authenticate execution or management APIs. For the exact beta.7 adapter settings
and diagnostic behavior, consult the matching
[Framework observability setup](https://github.com/loomspan/loomspan-framework/blob/v1.0.0-beta.7/README.md#opt-in-console-observability-rest-api).

## Connect Console and the assistant

Follow the matching [Console runtime package guide](https://github.com/loomspan/loomspan-framework/blob/v1.0.0-beta.7/loomspan-console/release/README.md)
to obtain and start Console. Open its printed loopback pairing URL and configure
the Sidecar target address and observability key. In the paired browser's
Settings, enable MCP. Configure your assistant host with the Console loopback
MCP endpoint and its separate bearer credential through protected settings.

Install the matching `loomspan-console` Agent Skill via [assistant setup](setup.md#equip-your-development-assistant).
The host must be able to reach the local Console listener. Installing the skill
alone does not connect it, and enabling MCP does not publish Sidecar skills.

For ongoing investigation, follow the [Console skill](https://github.com/loomspan/loomspan-framework/blob/v1.0.0-beta.7/loomspan-console/agent-skills/loomspan-console/SKILL.md).
Start with a reproducible application invocation and its execution ID; distinguish
Sidecar's retained HTTP result from Framework's available observability evidence.
Neither promises permanent or complete history. If the adapter is unavailable,
check process configuration, startup diagnostics, target address, TLS, and the
appropriate credential before investigating the model itself.
