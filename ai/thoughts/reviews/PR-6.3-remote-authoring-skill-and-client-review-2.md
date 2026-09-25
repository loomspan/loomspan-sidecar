# PR 6.3 Remote Authoring Skill and Client Code Review — Cycle 2

## Scope and Repository State

Reviewed the ticket-scoped staged, unstaged, and untracked state on `main`: the portable skill and Python client, examples, joined Java/browser/application fixture, integration and operations guidance, and verification record. There were no staged changes. Traced the client through the shared management controllers, server lease and candidate behavior, bundle limits, console handoff, and published execution boundary before comparing it with the ticket and plans. The selected pipeline profile remains `full`.

## Findings

### [P2] Accept exports allowed by the server

- Location: `agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py:84`
- Scenario: The original response reader rejected every response above 16 MiB, including `GET /configuration/export`, although `ConfigurationBundleV1.MAX_ZIP_BYTES` permits a valid ZIP as large as 100 MiB.
- Impact: A user could publish a configuration and export it through the shared API, yet the shipped client could not transfer its valid bundle.
- Evidence: The original `response.read(16 * 1024 * 1024 + 1)` applied to all routes. `src/main/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV1.java:41,95` establishes the 100 MiB server limit. The new 17 MiB response test passes only after the client correction.
- Fix: Apply the server's 100 MiB bound to ZIP export and remove the unrelated 16 MiB cutoff from JSON reads.

## Findings Resolved in This Context

- Resolved the export limit mismatch in the Python client and added a large-bundle regression case in `test_sidecar_authoring.py`. Re-reviewed the complete ticket change after the edit; no other actionable findings remain in this context. A fresh reviewer must assess this implementation change.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Portable skill and aligned endpoint/security guidance | `SKILL.md`, two references, record example; Python 3.9+ standard library client | Python subprocess invocation; joined fixture consumes example assets | implemented |
| Shared API operations and bundle transfer | Client route table and multipart encoder call existing controllers | Python route/body/multipart/export tests, including large export | implemented |
| Validation repair, console observation, both publish paths | Joined workflow drives CLI, Playwright console, and shared publication API | `RemoteAuthoringWorkflowIntegrationTest` passes | implemented |
| Fresh handoff, exact ownership, actionable errors | Handoff rereads current/draft; no retry loop; server generation and candidate checks | Python conflict/redirect tests; joined old-holder denial | implemented |
| Persistence, staleness, revocation, role change, competition, cleanup | Existing editing/token services; skill recovery guidance | Joined lifecycle test plus broad predecessor and restart suite in `mvn test -q` | implemented |
| Separate execution identity and application access denial | Record callback verifies JWT and owner; examples use caller passthrough | Joined successful and denied published executions; PAT rejected by execution API | implemented |
| Environment-only management credential | CLI reads PAT from environment and redacts output; docs separate credentials | Python and joined secret assertions; existing server token tests | implemented |
| Local verification and accurate release boundary | Ticket record and integration/operations docs retain snapshot and deferred release gates | Python suite, focused workflow, full Maven suite, patch check pass | implemented |

## Active Project Guardrails

- No new management API, framework SPI, agent allowlist, schema, compatibility shim, or migration path was added. The client uses the existing API and delegates validation, authorization, lease ownership, and publication to Sidecar. The Java fixture uses supported framework types and the existing ArchUnit suite passed.
- Management PATs remain separate from execution JWTs and callback authorization. The package has no secret values or secret logging. The fixture is loopback and uses disposable data; this ticket requires no development-data reset.
- The framework dependency stays at the locally installed beta.5 snapshot. Release publication, released-dependency verification, and hosted CI are separate gates.

## Open Questions and Assumptions

- None affecting this review. Installation in a particular downstream coding agent remains an optional compatibility check, not a claimed universal guarantee.

## Verification Results

- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` — 9 tests, including the 17 MiB export regression.
- PASS — `mvn '-Dtest=RemoteAuthoringWorkflowIntegrationTest' test -q` — 2 joined workflow tests.
- PASS — `mvn test -q` — 223 tests, zero failures or errors, three existing skips, against the installed beta.5 snapshot.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- A downstream agent product may present skill instructions differently; install and inspect there if desired. No configured external Sidecar or application was contacted. Final framework publication, released dependency tests, and hosted CI remain pending under repository policy.

## Disposition

- `fixes-applied` — one in-scope client defect and its regression test were fixed. This context cannot certify its own implementation change; launch a fresh Step 5 cycle.
