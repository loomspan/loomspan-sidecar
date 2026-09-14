# Authenticated Execution API Code Review — Cycle 1

## Scope and Repository State

- Reviewed in pipeline mode as Step 5 of the Full 5-Step Pipeline against ticket `ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md`, its research artifact, implementation plan, testing plan, and the current repository state.
- Reconstructed the ticket change from committed production work in `2597238`/`fba8351`, the unstaged test changes, and ticket-related untracked plans, research, and new tests. No staged changes were present.
- Read the relevant HTTP, JWT, ownership, coordinator, retention, diagnostics, shutdown, configuration, architecture, Console/management, fixture, and README paths beyond diff hunks. Consulted the matching local framework public contract and lifecycle source only to verify boundary and shutdown semantics.
- Preserved the unrelated untracked `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md` without reading or modifying it.
- The selected `full` profile remains appropriate because the reviewed behavior changes authentication/authorization, HTTP contracts, concurrency, retention, and lifecycle boundaries.

## Findings

### [P3] Quote comma-separated Maven test selectors for PowerShell

- Location: `ai/thoughts/plans/2026-09-13-authenticated-execution-api-testing.md:170`
- Scenario: A developer copies either documented focused command into the repository's PowerShell environment.
- Impact: PowerShell parses the commas as argument-list separators and raises `Missing argument in parameter list` before Maven starts, so the prescribed verification command is not runnable as written.
- Evidence: Running `.\mvnw.cmd -B -ntp -Dtest=ExecutionRequestBodyIntegrationTest,AuthenticatedExecutionApiIntegrationTest,JwtSecurityConfigurationTest,CustomRoleExecutionIntegrationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,ApiExceptionHandlerTest test` reproduced the parser error. Quoting the complete `-Dtest=...` argument ran all 32 selected tests successfully.
- Fix: Quote every comma-separated `-Dtest=...` argument in both implementation and testing plans.

No actionable implementation findings remained after the fix and internal re-review.

## Findings Resolved in This Context

- Updated all five comma-separated Maven selector examples in `ai/thoughts/plans/2026-09-13-authenticated-execution-api.md` and `ai/thoughts/plans/2026-09-13-authenticated-execution-api-testing.md` to pass the selector as one quoted PowerShell argument.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Four JWT routes, unfiltered catalog, strict bounded object bodies, and request error statuses | `SkillCatalogController`, `ExecutionController`, `LimitedJsonObjectReader`, `ApiExceptionHandler`, `NoStoreResponseFilter` | `AuthenticatedExecutionApiIntegrationTest`, `ExecutionRequestBodyIntegrationTest` | implemented |
| Discovery, JWKS, public-key signature/claim policy, skew, and configurable role mapping | `JwtSecurityConfiguration`, `SidecarJwtProperties` | `JwtSecurityConfigurationTest`, `CustomRoleExecutionIntegrationTest` | implemented |
| Prompt `202`, exact result/failure polling, problem responses, and accepted-work independence | `ExecutionController`, `ExecutionFailureClassifier`, coordinator task | application integration and diagnostics tests | implemented |
| Issuer/subject ownership and admitted JWT propagation without worker leakage | `ExecutionOwner`, `ExecutionCoordinator.ExecutionTask` | renewed/foreign/expiry/nested application cases and sequential coordinator token assertions | implemented |
| One owner for workers, queue count/bytes, retained records, rollback, and handoff | `ExecutionCoordinator`, `AdmissionQueue` | deterministic saturation, equality/excess, rejection, handoff, simultaneous admission, and shutdown cases | implemented |
| Completion TTL, whole-record expiry, and task-held reference cleanup | coordinator expiration and task `clear` paths | mutable-clock expiry/admission tests plus worker/discard state-path assertions | implemented |
| Diagnostic selection, atomic terminal publication, primary failure, and no truncation | `ExecutionSnapshot`, coordinator `complete`, `ExecutionFailureClassifier` | selection matrix, no-callback, ordered large payload, primary failure, and whole-record expiry tests | implemented |
| Immediate owner-scoped shutdown/readiness gate with no post-close facade entry | coordinator close listener and shared `gate`/`begin` boundary | active/queued shutdown, foreign context, synchronous listener, close-to-begin, and `503` advice tests | implemented |
| Console/management coexistence, defaults, supported Loomspan API, and documentation | security chain, `application.yml`, README, architecture rule | Console, management, defaults, properties, ArchUnit, and full Maven suite | implemented |

## Active Project Guardrails

- The implementation keeps the public `ai.loomspan.api` surface as the framework boundary; the whole-package ArchUnit test passes and no Sidecar Java skill is declared.
- Framework validation, authorization, invocation, nesting, observation, and admitted lifetime remain framework-owned; Sidecar does not introduce another extension contract or inspect internals.
- `ExecutionCoordinator` remains the single admission, queue-accounting, record, terminal-publication, retention, and close-gate owner.
- Trusted identity remains outside model input, ownership remains an issuer/subject value, and worker authentication/input references are cleared on exit or discard.
- Close handling discards queued work and preserves already-dispatched work for the framework lifecycle budget; matching framework lifecycle evidence confirms its synchronous close gate and SmartLifecycle wait/cutoff precede bean destruction.
- Results and selected public events remain unchanged and untruncated; documentation correctly avoids claiming heap bounds or SC4/SC5 delivery.
- The review fix was limited to making existing verification commands runnable and added no abstraction or production complexity.

## Open Questions and Assumptions

- None.

## Verification Results

- FAIL — `.\mvnw.cmd -B -ntp -Dtest=ExecutionRequestBodyIntegrationTest,AuthenticatedExecutionApiIntegrationTest,JwtSecurityConfigurationTest,CustomRoleExecutionIntegrationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,ApiExceptionHandlerTest test` — PowerShell rejected the unquoted comma-separated argument before Maven started; this directly exposed the resolved plan defect.
- PASS — `.\mvnw.cmd -B -ntp "-Dtest=ExecutionRequestBodyIntegrationTest,AuthenticatedExecutionApiIntegrationTest,JwtSecurityConfigurationTest,CustomRoleExecutionIntegrationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ExecutionShutdownIntegrationTest,ApiExceptionHandlerTest" test` — 32 tests passed with no failures, errors, or skips.
- PASS — `.\mvnw.cmd -B -ntp verify` — all 42 tests passed and the executable jar was repackaged successfully against the locally installed beta.4 snapshot.
- PASS — `git diff --check -- README.md src/main src/test ai/thoughts/plans` — no whitespace errors; Git emitted only line-ending conversion warnings for existing working-tree test files.

## Residual Risks and Optional Developer Checks

- Packaged client/resource ordering, container startup, the released framework dependency, hosted CI, and release verification remain explicitly deferred to SC5.
- No optional developer check is required for this ticket.

## Disposition

- `fixes-applied`
