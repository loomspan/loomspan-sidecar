# Publishable execution settings

The Console and management API accept one complete `executionConfigurationYaml` value, `skillDocuments`, and `restRoutesYaml` in one draft. Save or reconcile all content together, validate the saved revision, then publish it with the same lease and base checks. Saving and validation do not activate the candidate.

The initial database value is `loomspan: {}\n`, which uses framework defaults. A first draft can introduce a connection and model alias alongside a skill that uses it:

```yaml
loomspan:
  connections:
    primary:
      driver: openai
      api-key-ref: provider.primary.key
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

For file versus database authority, managed credential restrictions, encryption
keys, rotation and cross-environment credential transfer, see
[configuration modes](configuration-modes.md). The [bundle contract](configuration-bundles.md)
owns archive format and import/export behavior; the [management API](management-api.md)
owns candidate fields, revisions, grants, and publication checks.

Only `loomspan.connections`, `loomspan.models`, `loomspan.session`, and
`loomspan.execution-trace.persistence` are publishable. Framework process settings,
Sidecar settings, Spring Boot settings, and secret provisioning remain deployment
configuration and may require a restart. Preparation makes no model request.
Missing, blank or unused credential references prevent activation and preserve
the draft. Remove unneeded configured or required identifiers with
`remove-credential` after removing their YAML references.
