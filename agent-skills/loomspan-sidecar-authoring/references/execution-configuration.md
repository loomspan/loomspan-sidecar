# Publishable execution settings

The Console and management API accept one complete `executionConfigurationYaml` value beside `skillDocuments` and `restRoutesYaml`. Save or reconcile all three together, validate the saved revision, then publish it with the same lease and base checks. Saving and validation do not activate the candidate.

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

`api-key-ref`, `gemini.credentials-ref`, and `header-refs` name Spring Environment properties supplied by the operator. Do not write credential values into the draft, client input, examples, or prompts. Validation checks syntax and skill references; preparation checks that external references exist and constructs provider resources without making a model request. A missing or blank reference blocks publication and leaves the saved draft intact.

Only `loomspan.connections`, `loomspan.models`, `loomspan.session`, and `loomspan.execution-trace.persistence` are publishable. Framework process settings, Sidecar settings, Spring Boot settings, and secret provisioning remain deployment configuration and may require a restart. The database-selected complete configuration is restored at Sidecar restart.
