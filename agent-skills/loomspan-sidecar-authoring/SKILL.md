---
name: loomspan-sidecar-authoring
description: Build application endpoints and matching Loomspan Sidecar REST skills through the shared management API. Use when working in an integrating application's repository to inspect its data access, author a complete Sidecar draft, validate it, hand off editing control, and optionally publish. Do not use for execution JWT issuance or management token administration.
---

# Author application backed Sidecar skills

Work in the integrating application's checkout. Inspect its behavior, data model, existing authentication, per-record authorization, and tests before changing endpoints. Repository access comes from the agent's environment; Sidecar does not grant code access. Define a narrow endpoint that independently checks its caller and authorizes the requested data. Add application tests for allowed and denied access.

Read [client setup and commands](references/client-usage.md) and [the REST configuration guide](references/rest-configuration.md). These examples reflect the checked-out `1.0.0-beta.5-SNAPSHOT` framework and Sidecar route contract. For further syntax, consult the matching framework checkout's `agent-skills/loomspan-docs/references/skill-authoring/` directory and Sidecar's `docs/integration.md`. The separately installed `0.1.0-SNAPSHOT` documentation skill is stale.

Use the bundled Python client and a user-provided management token from `LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN`. Keep the secret out of prompts, command arguments, files, logs, and output. A Read token inspects, an Edit token saves and validates, and a Publish token may publish; the live user role also applies. Never use the management token for execution or as an application data credential.

Author a complete candidate with `skillDocuments` and `restRoutesYaml`, inspect the current publication and saved draft, acquire control, save, and correct structured server validation issues. Server validation does not call the endpoint. On a changed base, inspect both complete configurations and deliberately submit a complete reconciliation; do not merge or overwrite silently. Renew only at the user's direction. For a client change, explicitly hand off and read the current publication and saved draft under the new grant. A displaced holder must stop writing.

An Edit token can leave a saved validated draft for the same user to hand off and publish in the console. A Publish token can explicitly hand off, validate the exact new grant/revision/base, and publish remotely. Publication does not require an extra console approval. After publication, verify the published skill with a separate execution JWT and check both allowed and denied application data access. Treat conflicts, expired or revoked tokens, and ambiguous publication as reasons to inspect state before another mutation.

The local [record example](../../examples/remote-authoring/README.md) illustrates one route and its authorization boundary; adapt it to the application's real data and security contract.
