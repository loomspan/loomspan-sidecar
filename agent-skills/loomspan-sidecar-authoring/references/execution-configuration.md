# Publishable execution settings

The Console and management API accept one complete `executionConfigurationYaml` value, `skillDocuments` in one draft. Save or reconcile all content together, validate the saved revision, then publish it with the same lease and base checks. Saving and validation do not activate the candidate.

The initial database has no skills and empty `http` and `typesafe` maps under
`loomspan.executor-config.default`. Keep an `http` map in every publication.
When the `typesafe` process profile is active, also include a `typesafe` map.
A first draft can introduce a connection and model alias alongside a skill that
uses it:

```yaml
loomspan:
  executor-config:
    default:
      http: {}
  connections:
    primary:
      driver: openai
      api-key: { $var: provider.primary.key }
  models:
    editor:
      connection: primary
      provider-model: gpt-4.1-mini
  session:
    max-depth: 8
    quotas:
      max-provider-attempts: 24
  execution-trace:
    persistence: ONERROR
```

For file versus database authority, managed values, encryption
keys, rotation and cross-environment credential transfer, see
[configuration modes](configuration-modes.md). The [bundle contract](configuration-bundles.md)
owns archive format and import/export behavior; the [management API](management-api.md)
owns candidate fields, revisions, grants, and publication checks.

Only `loomspan.connections`, `loomspan.models`, `loomspan.executor-config`, `loomspan.session`, and
`loomspan.execution-trace.persistence` are publishable. Framework process settings,
Sidecar settings, Spring Boot settings, and secret provisioning remain deployment
configuration and may require a restart. Preparation makes no model request.
Variable references match a single-key mapping with the exact key `$var`.
Framework resolves them below each connection, each executor namespace's settings,
and the top-level `config` of executor-backed skills. Names are case-sensitive.
For example, use `headers: { X-API-Key: { $var: service.key } }` or
`gemini: { credentials-json: { $var: vertex.service-account } }`. The same
fields also accept literals. Ordinary text such as `${region}` stays literal.
Variable value or mapping-key collision errors prevent activation and preserve the draft.
HTTP target, mapping and credential-usability errors are checked only on invocation
before dispatch. Management validation/readiness do not certify HTTP validity. Remove unneeded configured names with
`remove-credential` after removing their YAML references.
