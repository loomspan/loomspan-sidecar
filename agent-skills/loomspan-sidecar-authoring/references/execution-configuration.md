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

Read `current.mode` before authoring. In `file` mode the configuration is read-only:
deploy complete framework skill/configuration files and the Sidecar REST route file,
then restart. Literal file credentials and external references are supported; prefer
secret-store provisioning. File mode never changes the selected database publication.

In `database` mode authored providers and settings are complete and never inherit
file fields or external credentials. `api-key-ref`, `gemini.credentials-json-ref`
and `header-refs` identify values saved through write-only `replace-credential`.
Gemini Vertex requires `credentials-json-ref`; application-default credentials and
credential file/URI references are prohibited in database candidates. An Edit
token/session needs the current lease, revision and base. Send values once from a
trusted secret source; saved values cannot be read back. Replacement advances revision
and requires validation. Never put provider plaintext into ordinary save payloads,
prompts, logs or exports. Preparation makes no model request. Missing, blank or unused
references prevent activation and preserve the draft.
After removing an imported provider from the YAML, use `remove-credential` to
remove its configured or still-required identifier before validation.

Default exports require destination credentials. `export --include-encrypted-credentials`
adds authenticated ciphertext, requiring Edit authority. With the same externally
provisioned `LOOMSPAN_SIDECAR_CREDENTIAL_KEY`, a destination can use all included values.
Users may share keys; distinct environment keys are not required. Wrong/missing keys
or tampering fail without mutation. Explicitly choose `import-load --credential-mode
configuration-only` for different-key promotion, then supply destination replacements.
Imports are drafts; validate and publish normally. Local rollback retains encrypted
versions. Full-installation migration uses a consistent database backup and separately
provisioning its original key. Never put that key in database or export files.

Only `loomspan.connections`, `loomspan.models`, `loomspan.session`, and `loomspan.execution-trace.persistence` are publishable. Framework process settings, Sidecar settings, Spring Boot settings, and secret provisioning remain deployment configuration and may require a restart. The database-selected complete configuration is restored at Sidecar restart.
