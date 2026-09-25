# REST skill and route authoring

The matching beta.5 framework REST manifest needs `name`, `description`, and `rest: true`. `input_schema` and `rbac_roles` are optional. REST manifests must omit model and prompt fields, including null or empty declarations. Sidecar's framework parser and validation remain authoritative; this guide is an example, not a second validator.

One complete snapshot has ordered `skillDocuments` entries (`sourceName`, raw `yaml`) and one raw `restRoutesYaml`. Each REST skill needs one exact case-sensitive route. A target has a literal or allowlisted environment-resolved base URL and an authentication mode of `none`, `static`, or `caller-passthrough`. See the matching installed `loomspan-docs` skill for framework REST syntax and the bundled [route contract](integration.md#rest-skill-routes) for Sidecar configuration.

For caller passthrough, the application must independently verify JWT signature, issuer, audience, expiry, subject and roles, then enforce data ownership. Sidecar's execution JWT is distinct from the management token. A static target header is a separate application credential and cannot stand in for per-user authorization unless the endpoint's own contract safely permits it. Never copy credentials into skill YAML, route YAML, or skill inputs.

The [record fixture](../examples/remote-authoring/README.md) contains matching YAML and route text. A malformed manifest can be saved; `validate` returns structured issues (`severity`, `sourceLabel`, `skillName`, `location`, `message`) to repair. Validation proves syntax and configuration compatibility, not network connectivity or record authorization. Publish the exact validated candidate, then execute using a separate JWT and test an unauthorized record.
