# PR 6.3 — Deliver and verify the complete remote skill-authoring workflow

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

An external coding agent working in an integrating application's repository can
inspect its behavior and security, develop endpoints and matching Sidecar skills,
and author, validate and optionally publish through the shared API. Users can
watch saved edits in the console and explicitly move control between clients.
Ship an agent-neutral authoring skill with a small client and verified local
integration guidance, completing roadmap phases 3 and 4 in one ticket.

## Requirements

1. Package a reusable authoring skill containing the instructions, examples and
   references needed to inspect an application's behavior, data model and access
   controls; propose and implement integration endpoints; author skill YAML/REST
   routes; and iterate from structured server validation. Use documentation aligned
   with the tested framework dependency, not the stale installed documentation skill.
   Follow the applicable skill-creation guidance during implementation. Repository
   access belongs to the agent's environment, not a new Sidecar code-access service.
2. Provide a small client for the existing management API, not an independent
   authoring API, validator or business-rule engine. Support configuration/draft
   reading, editing session/lease management, explicit handoff, saving, stale-base
   reconciliation submission, validation and publication. Handle relevant export/
   import operations through that same API for a user choosing bundle transfer.
   Reuse server structures and errors rather than reproducing framework parsing.
3. Keep the skill/client agent-neutral and document its runtime/setup requirements.
   Do not add an agent allowlist, proprietary-agent dependency or claim universal
   compatibility. Select a minimal packaging/runtime approach during planning.
   Verify using a representative client invocation without restricting other agents.
4. Supply the token from the user's environment, outside skill content and model
   inputs. Sidecar holds only hashes; never attempt to retrieve a token secret from
   its database. Keep credentials out of command arguments, repositories, normal
   output, error output and logs. No credential vault or automatic refresh. Separate
   management tokens from execution JWTs and application data-access credentials.
5. Respect one durable draft per user and one lease held by a specific editing
   session. Client names are descriptive labels. Explicit same-user handoff must
   read the current draft/revision and use the new lease generation. Never silently
   seize back control, retry denied writes indefinitely, overwrite a stale candidate
   or treat session identifiers as authorization. Make expiry, lost ownership,
   insufficient permission and base/revision conflicts actionable to the caller.
   Background activity must not silently renew authenticated access.
6. Support both permission-controlled workflows: an Edit token prepares and
   validates a draft, then the same user takes control and publishes in the console;
   a Publish token completes remote publication without mandatory UI review. In
   either case, authorization and exact candidate/base validation remain server-side.
   A read-only console view shows server-saved agent changes. No separate draft
   transfer format is needed for same-user handoff.
7. Explain and exercise draft persistence across credential loss/restart, explicit
   reconciliation after the runtime base changes, successful publication cleanup
   and preservation after failure. Prompt instructions are guidance, not enforcement.
   Treat Read/Edit/Publish as ceilings intersected with live user permissions.
8. Do not prescribe dev/QA/production usage or restrict publication by environment.
   Export/import promotion is an example only. No built-in assistant, MCP, OAuth,
   additional production approval policy, automatic merging or draft-execution
   sandbox. Testing a skill uses a published configuration and separate execution
   authentication; validation alone does not prove endpoint connectivity or execution.
9. Complete a representative local integration demonstrating application endpoint
   development, skill/route authoring, correction of a validation error, console
   observation, explicit handoff and remote publication. Verify both successful
   execution and denied application data access with separate runtime credentials.
   Use a controlled fixture/sample, not live third-party or production operations.
10. Supply focused client/skill verification and integrated Sidecar evidence for
    revocation during work, live role changes, stale candidates, competing clients,
    restart and recovery after expiry. Build on predecessor tests rather than
    needlessly duplicating their entire matrices. Fix integration defects within
    agreed behavior; do not invent new security or API contracts as client workarounds.
11. Deliver installation/use documentation, environment-based credential setup,
    permission selection, lease renewal/handoff, reconciliation, publication,
    troubleshooting and development reset implications. Use structured logs already
    provided by Sidecar; do not add an audit UI. Record actual local verification and
    limitations, preserving the repository's separate final release/CI gates.

## Acceptance criteria

- [x] The shipped skill and client can be installed and invoked using documented
  runtime requirements without a particular agent product or access allowlist.
  Guidance covers application endpoint design, data authorization and matching
  Sidecar configuration using documentation aligned with the framework version.
- [x] Client operations use the shared API for reading, editing, validation,
  publication and relevant bundle transfer, with no duplicate framework validator,
  compatibility endpoint or browser-session emulation.
- [x] A local representative workflow creates/updates an application endpoint and
  matching Sidecar draft, fixes a server-reported validation error, and shows saved
  agent changes in the console. It verifies both Edit-token console publication
  after explicit handoff and Publish-token remote publication without UI approval.
- [x] Handoff forces a current read and rejects delayed old-holder writes. Permission,
  expiry, revocation, lease and stale-base/revision failures produce actionable
  results without secret leakage, silent retries, automatic takeover or overwrites.
- [x] Integrated evidence covers retained drafts after expiry/restart, reconciliation
  after another publication, live role changes/revocation during work, competing
  clients, successful publication cleanup and failed-publication retention.
- [x] Published skills execute against the representative application using separate
  execution credentials; unauthorized application data access is denied. The
  walkthrough distinguishes validation from actual execution and presents bundle
  promotion as optional rather than a required environment policy.
- [x] Environment-based credential setup works without secrets in skill files,
  repositories, command arguments, client output/errors or audit logs. Token
  secrets cannot be recovered from Sidecar; expired tokens are replaced explicitly.
- [x] Focused automated checks and the local end-to-end walkthrough pass against
  the installed framework snapshot. Setup, permissions, handoff, recovery and
  troubleshooting documentation matches verified behavior; release/hosted CI
  checks deferred by repository policy are not represented as completed.

## Context

- Parent work item: **GitHub PR 6**. This is ticket **PR 6.3**, not a separate PR.
- Roadmap: [Remote skill authoring](../phases/2026-09-24-remote-skill-authoring-roadmap.md),
  phases 3 and 4.
- Depends on completed [PR 6.1](PR-6.1-shared-authoring-and-durable-drafts.md) and
  [PR 6.2](PR-6.2-scoped-personal-access-tokens.md). Consume their documented shared
  contract and actual evidence; do not infer completion from the tickets themselves.
- Intended implementor: **GPT Sol 6**. Client, skill, examples and end-to-end readiness
  form one deliverable to avoid a separate process-only verification ticket. Earlier
  tickets still require their own complete tests and documentation.
- This ticket's representative application work is a local example/fixture, not
  authorization to modify an unrelated user's repository or production service.
- Follow supported framework APIs and the existing dependency/release policy. Use
  the locally installed beta.5 snapshot without rebuilding the framework. No release,
  deployment, framework publication or final release dependency switch is requested.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** A new credential-bearing remote client coordinates editing and
  publication lifecycles, and its packaging/interface and integration evidence
  require design. It is more than documentation of settled behavior.
- **Reassessment triggers:** None for a lighter profile under this scope. A need
  for a new framework extension, alternate API, automatic token delegation or a
  draft execution environment exceeds the agreed contract and needs a decision.

## Local implementation verification (2026-09-24)

- The shipped package is `agent-skills/loomspan-sidecar-authoring/`; the client uses Python 3.9+ standard library and the existing management API. The local walkthrough uses a disposable SQLite database, loopback callback, synthetic PATs and execution JWTs, and Playwright Chromium. No live application or production service was contacted.
- `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` passed (9 tests). It covers environment-only credentials, shared routes and multipart transfer, handoff reads, actionable conflicts, no write retry, and redirect refusal.
- `mvn -Dtest=RemoteAuthoringWorkflowIntegrationTest test -q` passed (2 tests) against the installed `1.0.0-beta.5-SNAPSHOT`: malformed server validation and repair, saved-console observation, explicit console handoff, both publication paths, published execution and denied foreign-record access, competing clients, stale-base reconciliation, lease expiry, revocation, and live-role downgrade.
- `mvn '-Dtest=ManagementEditingHttpIntegrationTest,ManagementPersonalTokenHttpIntegrationTest,ManagementDraftRestartIntegrationTest,ManagementEditorBrowserIntegrationTest,ManagementImportBrowserIntegrationTest,ConfigurationBundleV1Test,AuthenticatedExecutionApiIntegrationTest' test -q` passed. These predecessor suites cover restart retention, publication failure retention and bundle/server security edges beyond the focused client-path tests.
- `mvn test -q` passed (223 tests, 0 failures/errors, 3 skips reported by Surefire) against the installed snapshot. This full run preceded final client and joined-test refinements; the Python suite and joined Java workflow were rerun afterward and passed. `skill-creator`'s `quick_validate.py` reported `Skill is valid!` after PyYAML was supplied in a temporary directory.
- This ticket adds no schema or persistent contract change, so no development-data reset is required. Release publication, the final framework release dependency switch, and hosted CI remain separate gates under `AGENTS.md`; none is claimed here.
