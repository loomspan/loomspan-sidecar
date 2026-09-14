# Authenticated Execution API Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket-scoped authenticated execution API independently against
`main`/`origin/main` at `e3c3f58f1246643c9ac55005228a56bd80b4f57b` and the supplied research,
implementation plan, testing plan, SC2/SC3/SC5 phase constraints, repository
instructions, and design lens. Scope included committed, staged, unstaged, and
untracked state. There are no staged changes. The new API, configuration,
execution, security, tests, fixtures, ticket artifacts, and the relevant README,
application configuration, and existing-test adaptations belong to this ticket.

Pre-existing SC1 closeout and process edits in `AGENTS.md`, `ai/commands/**`,
`ai/thoughts/beta4-framework-alignment.md`, `ai/thoughts/beta4-handoff.md`,
`ai/thoughts/phases/phase-sc1.md`,
`ai/thoughts/tickets/2026-09-13-sidecar-scaffold.md`, and `.vscode/` were excluded
from the implementation judgment and preserved. The refreshed locally installed
beta.4 framework snapshot is the repository-authorized dependency workflow; no
separate provenance check was performed.

The Full 5-Step Pipeline remains the appropriate profile because the change owns
security, authorization, public HTTP, concurrency, retention, and lifecycle
boundaries.

## Findings

No actionable findings remain after the fix below and a complete re-review.

## Findings Resolved in This Context

### [P2] Reject trailing JSON tokens instead of admitting a malformed body

- Location: `src/main/java/ai/loomspan/sidecar/api/LimitedJsonObjectReader.java:37`
- Scenario: a caller sends two JSON root values, such as `{} {}`, within the raw
  byte limit. The original `readTree(byte[])` path validated only the first tree
  and did not establish that the request body was exhausted.
- Impact: a malformed request could proceed through skill validation and create
  an accepted execution, contrary to the required `400` handling for malformed
  bodies.
- Evidence: the original reader had no parser-exhaustion check. The focused
  regression now includes `{} {}` and the reader explicitly requires
  `parser.nextToken()` to return `null` after the object.
- Fix: parse through a scoped Jackson parser, retain the JSON-object requirement,
  and reject any trailing token as invalid JSON. Added the concatenated-root
  regression case at
  `src/test/java/ai/loomspan/sidecar/api/ExecutionRequestBodyIntegrationTest.java:18`.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Four authenticated routes, complete catalog, problem errors, and body/byte bounds | `SkillCatalogController`, `ExecutionController`, `LimitedJsonObjectReader`, `ApiExceptionHandler`, `NoStoreResponseFilter` | `AuthenticatedExecutionApiIntegrationTest`, `ExecutionRequestBodyIntegrationTest` | implemented |
| Discovery, JWKS, public-key JWT modes with common issuer/audience/lifetime/claim checks and aligned roles | `SidecarJwtProperties`, `JwtSecurityConfiguration`, `ExecutionOwner` | `JwtSecurityConfigurationTest`, application role-protected fixture | implemented |
| Asynchronous `202`, owner polling, exact result, and classified owned failure | `ExecutionController`, `ExecutionCoordinator`, `ExecutionFailureClassifier` | `AuthenticatedExecutionApiIntegrationTest`, `ExecutionDiagnosticsTest` | implemented |
| Issuer/subject ownership and original queued/nested JWT without cross-request replacement | `ExecutionOwner`, coordinator task security-context install/clear | owner matrix in `ExecutionCoordinatorTest`; renewed/foreign, queued, and nested cases in `AuthenticatedExecutionApiIntegrationTest` | implemented |
| One bounded executor/record owner with worker, queue-count, queue-byte, and retained limits | `ExecutionCoordinator` and its single `AdmissionQueue` | deterministic saturation, simultaneous admission, equality/excess, and cleanup cases in `ExecutionCoordinatorTest` | implemented |
| Completion TTL, whole-record expiry, terminal publication, and task-reference release | immutable `ExecutionSnapshot`; coordinator sweep and task `finally`/discard cleanup | mutable-clock expiry and terminal polling in `ExecutionCoordinatorTest`; diagnostic publication tests | implemented |
| Diagnostic selection, facade-primary failure classification, and no callback wait | `ExecutionCoordinator.complete`, `ExecutionFailureClassifier` | full diagnostics mode matrix, primary/nested classification, and no-callback case in `ExecutionDiagnosticsTest` | implemented |
| Owning-context shutdown closes admission/dispatch, lowers readiness, discards queued work, and preserves active work | synchronous owning-context listener and post-lifecycle destruction in `ExecutionCoordinator` | active/queued close case in `ExecutionCoordinatorTest`; context/readiness/async-opt-out cases in `ExecutionShutdownIntegrationTest` | implemented; SC5 packaged ordering/resource proof remains intentionally later |
| Console API-key coexistence, separate health-only management, stateless no-store API | security matcher ordering and global response filter; production management settings | `ConsoleSecurityIntegrationTest`, `ManagementEndpointIntegrationTest`, API header assertions | implemented |
| Supported framework surface, deterministic local fixtures, and user documentation | public `ai.loomspan.api` imports only; README and startup defaults | whole-package ArchUnit rules and full Maven verification | implemented |

The implementation intentionally deviates mechanically from the plan's early
`ArrayBlockingQueue` wording by using one `LinkedTransferQueue` admission queue.
This is a safe plan adaptation: it is still the executor's sole work queue and
lets direct worker handoff avoid waiting-count/byte reservations as required,
without adding another capacity owner or staging queue.

## Active Project Guardrails

- Simplicity: one coordinator, one executor queue, one retained map, one terminal
  snapshot, and no cache/scheduling subsystem beyond the single expiry sweep.
- Framework boundary: production and tests use only supported `ai.loomspan.api`
  types; the architecture test passed and no Java skill is hosted.
- Framework authority: catalog lookup, validation, RBAC, invocation, nesting,
  and observer behavior remain delegated to `SkillCatalog`/`SkillTemplate`.
- Admission/retention: `ExecutionCoordinator` owns record capacity, waiting count
  and bytes, dispatch transitions, expiry, and terminal publication.
- Trusted identity: the verified `JwtAuthenticationToken` crosses the queue;
  ownership retains only explicit issuer and subject fields.
- Shutdown: the synchronous owning-context listener closes gates and discards
  queued tasks without waiting; already-started facade calls are left to the
  framework lifecycle.
- Diagnostics: result text and selected public events are retained without a
  Sidecar truncation or alternate failure-precedence layer.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp clean "-Dtest=ExecutionRequestBodyIntegrationTest" test` — clean compilation and both request-body tests passed, including the trailing-token regression.
- PASS — `.\mvnw.cmd -B -ntp verify` — all 33 tests passed and the repackaged jar was built against the refreshed local beta.4 snapshot.
- PASS — `git diff --check` — no whitespace errors in tracked diffs.

## Residual Risks and Optional Developer Checks

- SC5 still owns packaged image startup, relative listener-order/client-resource
  lifecycle proof, published-framework verification, and release checks. Those
  are explicitly outside this ticket and are not required to accept SC2/SC3.
- The testing plan records that the historical pre-implementation red run was
  not captured. This is a process-evidence omission, not an unverified current
  behavior; the focused regression and full current suite pass.

## Disposition

- `fixes-applied`
