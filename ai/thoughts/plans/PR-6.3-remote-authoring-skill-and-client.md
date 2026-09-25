# Remote Authoring Skill and Client Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/PR-6.3-remote-authoring-skill-and-client.md`
- Research: `ai/thoughts/research/PR-6.3-remote-authoring-skill-and-client.md`
- Outcome: Ship an agent-neutral skill and a small remote client for the existing management API, with a controlled endpoint-to-publication-to-execution walkthrough and local evidence against the installed beta.5 snapshot.

## Current State

`ManagementEditingController` and `ManagementEditingService` already expose one durable user draft, one lease, explicit same-user handoff, exact save/reconcile/validate, and publication admission through `ManagementConfigurationController`. The bearer filter permits scoped PATs on this shared API and intersects them with current account authority. `editor.js` polls saved state while read-only. The repository has no remote authoring package. `docs/operations.md` still contains browser-only wording that predates PATs. The test tree already has HTTP, browser, restart, bundle, and execution fixtures, but no one workflow drives an external client into console observation and application data authorization.

## Desired End State

An agent in an integrating application's checkout can install the skill, read the tested authoring references, build an application endpoint with its own authorization, and submit a complete Sidecar draft through a credential-safe CLI. The CLI surfaces server responses and conflicts without merging, renewal loops, lease takeover, browser impersonation, or local validation rules. The user can publish an Edit-token draft in the console after handoff, or use a Publish token to publish remotely after its own handoff and fresh validation. A runnable local fixture demonstrates both paths, server validation repair, separate execution JWTs, allowed and denied application access, retention/reconciliation/recovery, and bundle operations. No new Sidecar API, framework SPI, schema, or agent allowlist is required.

## Scope

### In scope

- `agent-skills/loomspan-sidecar-authoring/` skill package, client, examples/references, and setup guide.
- Narrow corrections to `docs/integration.md` and `docs/operations.md` for the implemented remote workflow.
- Focused Python client tests and Java local integration evidence using an application fixture and the real Sidecar server/console/execution APIs.
- A recorded local verification report in the existing ticket or documentation, with actual commands/results and deferred release gates named accurately.

### Out of scope

- New authorization rules, independent API/validator, automatic merge or renewal, token issuance/refresh/vault, MCP/OAuth, browser session emulation, draft execution, policy by environment, framework release/deployment, and modifying an unrelated application repository.
- Rebuilding or changing the framework and changing Sidecar's snapshot dependency for this ticket.

## Active Project Guardrails

- Apply `ai/thoughts/design-lens.md`: choose the smallest complete implementation, use the public framework surface, keep trusted identity outside model inputs, use the one shared management API, explicit ownership/reconciliation, independent management and execution credentials, and user-selected client/deployment flow.
- Sidecar is in development: destructively replace superseded contracts and wording; add no compatibility shims, legacy adapters, dual paths, or migration machinery. This ticket needs no development-data reset or schema change. If testing requires a fresh fixture database, use disposable test data only; do not delete deployed data.
- Java production and tests depend on the closed supported `ai.loomspan.api` surface; the existing ArchUnit boundary remains in force. Consult the matching local `C:/opendev/code/loomspan-framework` docs for beta.5 source guidance rather than the stale installed skill.

## Impact and Risk Analysis

- **Credential disclosure:** the CLI reads only `LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN` from the process environment; no token option, prompt, repository file, stdin request payload, URL query, debug dump, or error echo. Only the CLI HTTP layer creates the Authorization header. Management tokens never call `/v1/**`; JWTs and application data credentials remain separate.
- **Loss of ownership and stale content:** server grant/session/generation, draft ID/revision, and base are explicit input/output data. The CLI does not cache grant state, infer authority from labels, retry writes, automatically hand off, merge stale drafts, or renew in the background. After handoff it rereads current publication and the saved draft; after any conflict it prints actionable code/status and directs a fresh read. Reconcile is a deliberate complete-content submission at the current base.
- **Ambiguous publication:** a 503 may follow activation. The client must display the server problem code and current/intended IDs if present, then direct the caller to inspect current status and draft before any new mutation. It must not retry automatically.
- **Transport and output:** document HTTPS for non-loopback use; keep normal output bounded to requested server data and structured issues. Never echo environment values, exception request headers, or token-shaped input. A locally published fixture may use HTTP loopback only.
- **Integration:** server validation is advisory and does not contact the endpoint. The fixture must prove real published execution with an execution JWT and separately verify that the application rejects access to another user's data.

## Implementation Approach

Use a Python 3 standard-library CLI in `agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py`. Python is already used by the repository quickstart, is present locally, and avoids a package manager or client-side schema dependency. Document direct invocation with `python` (or the platform's equivalent). Commands cover current/draft/lease status, acquire/handoff/renew/release, save/reconcile/validate/publish, export, import review/load, and optionally history for recovery inspection. JSON mutating bodies come from stdin or a named local JSON file, never from arguments; the CLI forwards complete server shapes and returns server JSON or a bounded error with status/code. ZIP import/export uses named files and the server's multipart/ZIP behavior. Paths may name authored content, but management token secrets are env-only. The client does not parse YAML or attempt to validate bundle content.

Use `SKILL.md` with concise workflow instructions plus references for the tested framework REST manifest syntax, Sidecar route YAML, permissions and exact draft protocol, recovery, and a sanitized local example. During implementation, read and follow the available `skill-creator` guidance and keep the package portable so an agent with repository access can copy or load it. Treat prompt content as guidance; the server remains enforcement authority.

For integrated evidence, build a controlled application callback fixture under `src/test/java/.../support/` with a small record endpoint and independent JWT verification/record authorization. Drive the real Python client through a random-port Sidecar instance using `ProcessBuilder` environment injection, and observe saved revisions and handoff in a real console browser test. Reuse predecessor tests for broad server security/restart matrices; add only client-specific lifecycle and the joined workflow. Keep the fixture's endpoint example and matching skill/route YAML in the skill package or `examples/remote-authoring/`, with tests consuming those same files so documentation stays executable.

## Phase 1: Agent-neutral skill and API client

### Changes

- [x] `agent-skills/loomspan-sidecar-authoring/SKILL.md` — guide endpoint/security inspection, matching REST manifest and routes, complete-draft editing, server validation repair, explicit handoff, optional publication, and real execution verification; cite matching framework checkout references.
- [x] `agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py` — implement subcommands above using `urllib.request`, `json`, and standard multipart encoding; read token only from the environment; preserve server JSON/ZIP responses; map HTTP 400/401/403/404/409/413/503 and transport errors to concise, secret-free diagnostics and nonzero exit status. Handoff must reread both `/configuration/current` and `/editing/draft` after the new grant; no implicit retry or renewal.
- [x] `agent-skills/loomspan-sidecar-authoring/references/` and `examples/remote-authoring/` — tested operation examples, complete request shapes, REST YAML and route YAML, permission selection, and local application endpoint sample. Any example secret is a placeholder, never a real PAT.
- [x] `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py` — fake HTTP server and subprocess tests for invocation, endpoint/payload mapping, handoff reread, structured conflicts, no secret disclosure, and no hidden retry/renewal.

### Automated verification

- [x] `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` — commands, payloads, and error behavior pass without live Sidecar or third-party access.

### Optional developer checks

- [ ] Load the package in another agent environment and inspect its instructions for fit; this is nonblocking and does not imply universal compatibility.

## Phase 2: Local joined authoring and execution fixture

### Changes

- [x] `src/test/java/ai/loomspan/sidecar/support/RemoteAuthoringApplicationFixture.java` — loopback record endpoint, independent verification of fixture JWT issuer/audience/signature/expiry, owner/role authorization, and deterministic allowed/denied outcomes; never log credential values.
- [x] `src/test/java/ai/loomspan/sidecar/management/RemoteAuthoringWorkflowIntegrationTest.java` — start Sidecar on a random port with disposable storage; issue Edit and Publish tokens through the existing browser-only token UI/API fixture; invoke the real Python CLI with PAT injected in `ProcessBuilder` environment; save malformed authored YAML, receive/fix structured validation issue, observe saved draft in read-only console, hand off to console and publish, then hand off a second draft to a Publish token for remote publication without UI approval. Execute each published REST skill with separately minted JWTs and assert fixture data access success/denial. Verify stale old-holder write, competing client, role change/revocation, lease expiry, retained draft, re-read/reconciliation, and cleanup/failure retention as focused client-path cases; reference predecessor tests for exhaustive server cases rather than copying them.
- [x] `examples/remote-authoring/` — ensure example route/manifest and callback behavior are the same assets the integration test reads, or document where the test fixture supplies dynamic loopback URLs.

### Automated verification

- [x] `mvn -Dtest=RemoteAuthoringWorkflowIntegrationTest test` — the local walkthrough passes against the installed beta.5 snapshot; no external service or deployed data is contacted.
- [x] `mvn -Dtest=ManagementEditingHttpIntegrationTest,ManagementPersonalTokenHttpIntegrationTest,ManagementDraftRestartIntegrationTest,ManagementEditorBrowserIntegrationTest,AuthenticatedExecutionApiIntegrationTest test` — relevant predecessor behavior remains intact, including browser and restart boundaries.

### Optional developer checks

- [ ] Observe the console UI manually while running the local fixture if desired; Playwright assertions are the required executable evidence.

## Phase 3: Documentation and verification record

### Changes

- [x] `docs/integration.md` — link installation, env credential injection, permissions, client commands, endpoint/route example, execution JWT separation, optional export/import promotion, and validation versus execution.
- [x] `docs/operations.md` — replace superseded browser-only statements in the shared editing/configuration sections; document explicit renewal/handoff, stale reconciliation, expiry/revocation/restart recovery, 503 outcome inspection, no token recovery/refresh, and no development-data reset for this ticket.
- [x] `ai/thoughts/tickets/PR-6.3-remote-authoring-skill-and-client.md` — add concise actual local verification evidence and limitations after running it, retaining snapshot/release/hosted CI separation.

### Automated verification

- [x] `mvn test` — broad safe local Sidecar suite passes with the installed snapshot (Playwright tooling is a local dependency; record any concrete inability rather than claiming a pass).
- [x] `git diff --check` — whitespace and patch integrity pass.

### Optional developer checks

- [ ] Installation in a specific downstream agent product and configured non-production Sidecar remains an optional user-specific observation.

## Test Strategy

The first red test should assert that a subprocess CLI can read `/configuration/current` using only an environment PAT; it currently fails because the client does not exist. Unit tests use a fake local HTTP server to inspect paths, methods, bodies, headers, and error formatting. The joined Java test uses the actual Sidecar server, browser console, PATs, published configuration, and separate execution JWTs against a loopback application. Existing predecessor tests remain the broad security/restart regression gate. No routine verification uses third-party or production services.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Installable agent-neutral skill/client and aligned guidance | `SKILL.md`, client, references, setup | Python subprocess invocation; reviewed references |
| Shared API for all operations and bundle transfer | CLI route table and multipart handling | Fake HTTP route/payload tests; joined API workflow |
| Endpoint, validation repair, console observation, both publish paths | Example, fixture, client, existing console | `RemoteAuthoringWorkflowIntegrationTest` |
| Fresh handoff and actionable conflicts | CLI handoff rereads and error map | Python handoff/conflict tests; old-holder integration assertion |
| Retention, reconciliation, roles/revocation, competition, failure cleanup | CLI recovery guidance; existing server behavior | Joined focused cases plus predecessor HTTP/restart tests |
| Published execution and application denial | REST example and callback fixture | JWT execution and callback assertions in joined test |
| Environment-only PAT and no leaks | CLI credential loading/error discipline, docs | Process/fake-server secret leak assertions; existing PAT audit test |
| Focused and full local verification, accurate documentation | Tests, docs, ticket verification record | Python suite, focused Maven suites, `mvn test`, diff check |

## Risks and Rollback/Recovery

An invalid or stale candidate remains a saved draft; inspect current publication and saved draft, reacquire or explicitly hand off, submit a reviewed complete reconciliation if base changed, then revalidate. Revoked or expired PATs require a newly issued secret; Sidecar cannot recover the old one. After an ambiguous publication fault, inspect current/intended state and draft before any retry. The implementation adds no persistent schema or data migration; removing the skill/client package does not erase existing drafts or publications. A test fixture database is disposable. No deployed-data deletion is authorized.

## References

- `ai/thoughts/tickets/PR-6.3-remote-authoring-skill-and-client.md`
- `ai/thoughts/research/PR-6.3-remote-authoring-skill-and-client.md`
- `ai/thoughts/design-lens.md`
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingController.java`
- `src/main/java/ai/loomspan/sidecar/management/ManagementConfigurationController.java`
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java`
- `docs/integration.md`; `docs/operations.md`
- `C:/opendev/code/loomspan-framework/agent-skills/loomspan-docs/references/skill-authoring/`
