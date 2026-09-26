---
name: loomspan-sidecar-authoring
description: Build application endpoints and matching Loomspan Sidecar REST skills through the shared management API. Use when working in an integrating application's repository to inspect its data access, author a complete Sidecar draft, validate it, hand off editing control, and optionally publish. Do not use for execution JWT issuance or management token administration.
metadata:
  loomspan-component: sidecar
  loomspan-version: "1.0.0-beta.1-SNAPSHOT"
  loomspan-framework-version: "1.0.0-beta.6-SNAPSHOT"
---

# Author application backed Sidecar skills

Work in the integrating application's checkout. Inspect its behavior, data model, existing authentication, per-record authorization, and tests before changing endpoints. Repository access comes from the agent's environment; Sidecar does not grant code access. Define a narrow endpoint that independently checks its caller and authorizes the requested data. Add application tests for allowed and denied access.

Read [client setup and commands](references/client-usage.md) for authoring, [execution configuration](references/execution-configuration.md) for publishable framework settings, [REST configuration](references/rest-configuration.md) for routes, and [integration](references/integration.md) for server setup, JWTs and direct execution API requests/polling. These resources and [record example](examples/remote-authoring/README.md) are bundled.

Use this bundle to access the existing Sidecar management Console API from a developer LLM. The separately installed `loomspan-docs` at exactly `1.0.0-beta.6-SNAPSHOT` is an explicit dependency for deeper framework syntax and semantics, as declared by `loomspan-framework-version`. Load only relevant guidance. If unavailable, explain the missing matching guidance rather than inventing semantics or assuming a framework checkout. `loomspan-install` selects exact sources; no live service is needed for installation.

Direct API integration requires no SDK. When the application uses an SDK, its independently versioned `loomspan-sdk-<language>` skill owns language setup, authentication integration, callbacks, request context, lifecycle and application changes. This skill owns Sidecar server configuration. Load the SDK specialist only when relevant and available; do not invent SDK protocols. Applications send execution requests to Sidecar; REST callbacks travel from Sidecar into the application and require independent application authentication and data authorization.

Use the bundled Python client and a user-provided management token from `LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN`. Keep the secret out of prompts, command arguments, files, logs, and output. A Read token inspects, an Edit token saves and validates, and a Publish token may publish; the live user role also applies. Never use the management token for execution or as an application data credential.

Author a complete candidate with `skillDocuments`, `restRoutesYaml`, and `executionConfigurationYaml`. Inspect the current publication and saved draft, acquire control, save, and correct structured server validation issues. Execution YAML can introduce connections and model aliases used by skills in the same draft and can set session limits and `loomspan.execution-trace.persistence`. Use `api-key-ref`, `gemini.credentials-ref`, or `header-refs` for credentials; an operator provisions those external Spring Environment properties outside the draft. Never put resolved credential values in authored YAML. Process settings remain deployment configuration and require restart. Server validation prepares candidate resources without calling a model or activating the draft. On a changed base, inspect both complete configurations and deliberately submit a complete reconciliation; do not merge or overwrite silently. Renew only at the user's direction. For a client change, explicitly hand off and read the current publication and saved draft under the new grant. A displaced holder must stop writing.

An Edit token can leave a saved validated draft for the same user to hand off and publish in the console. A Publish token can explicitly hand off, validate the exact new grant/revision/base, and publish remotely. Publication does not require an extra console approval. After publication, verify the published skill with a separate execution JWT and check both allowed and denied application data access. Treat conflicts, expired or revoked tokens, and ambiguous publication as reasons to inspect state before another mutation.

The local [record example](examples/remote-authoring/README.md) illustrates one route and its authorization boundary; adapt it to the application's real data and security contract.
