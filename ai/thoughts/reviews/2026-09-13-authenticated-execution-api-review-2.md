# Authenticated Execution API Code Review — Cycle 2

## Scope and Repository State

- Reviewed the ticket-scoped implementation and assurance changes independently from `e3c3f58` through current `HEAD` (`fba8351`) plus all staged, unstaged, and untracked workspace changes.
- Read the ticket, research, implementation plan, testing plan, repository guidance, design lens, production implementation, connected framework public contracts, tests, fixtures, shipped defaults, and README. No prior review document was read or relied on.
- The ticket implementation is committed; the current workspace adds ticket-scoped tests and pipeline artifacts. There are no staged changes.
- Preserved the unrelated untracked `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md` without reading or modifying it.
- Reassessed the selected profile as `full`: the reviewed change continues to cover supported HTTP/security contracts, authorization, concurrent admission, retention, diagnostics, identity propagation, and shutdown behavior.

## Findings

### [P2] Give the nested integration execution a sufficient bounded polling budget

- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:255`
- Scenario: During the full Maven suite, the nested execution performs three loopback model exchanges and public framework nesting while the test polls for at most 100 iterations with a 10 ms pause. Under full-suite startup and execution load, the valid execution remained nonterminal after that approximately one-second window.
- Impact: Required `mvn verify` failed nondeterministically even though the focused integration suite passed, so the ticket could not obtain reliable full-suite acceptance evidence.
- Evidence: The first `mvn verify` run failed only `nestedExecutionExposesTheOriginalJwtToTheRestHandler` at `poll`, while the preceding focused HTTP/custom-role command passed all eight selected tests and a focused nested-execution rerun passed.
- Fix: Keep polling bounded but increase the shared application-integration polling window from 100 to 500 ten-millisecond iterations (approximately five seconds), consistent with the fixture's existing five-second HTTP and latch bounds.

## Findings Resolved in This Context

- Resolved the P2 integration polling-budget defect by changing only the bounded attempt count in `AuthenticatedExecutionApiIntegrationTest.poll`. A targeted nested execution and the subsequent full 42-test Maven verification pass.
- Re-reviewed the complete ticket-scoped diff after the fix. No actionable findings remain.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Four JWT routes, unfiltered catalog, request validation, and `400/401/403/404/413` behavior | `SkillCatalogController`, `ExecutionController`, `LimitedJsonObjectReader`, `ApiExceptionHandler`, `NoStoreResponseFilter` | `AuthenticatedExecutionApiIntegrationTest`, `ExecutionRequestBodyIntegrationTest`, `ApiExceptionHandlerTest` | implemented |
| Discovery, JWKS, public key, common validators, clock skew, and configurable roles | `JwtSecurityConfiguration`, `SidecarJwtProperties` | `JwtSecurityConfigurationTest`, `CustomRoleExecutionIntegrationTest` | implemented |
| Prompt `202`, exact result/failure, problem documents, and accepted-work independence | `ExecutionController`, `ExecutionCoordinator`, `ExecutionFailureClassifier` | authenticated API success/failure, released-response blocked work, and diagnostics payload tests | implemented |
| Issuer/subject ownership and original queued/nested identity without leakage | `ExecutionOwner`, worker security-context install/clear | renewed/foreign owner, queued expiry, nested handler, and sequential worker-token tests | implemented |
| Worker, queue-count, queue-byte, and retained-record bounds with exact release/rollback | single `ExecutionCoordinator` gate, map, executor, and `AdmissionQueue` | saturation, simultaneous admission, equality/excess, handoff, rejection, close, and retained-capacity tests | implemented |
| Invalid configuration, completion TTL, whole-record expiry, and task reference cleanup | validated execution properties; task `clear`; terminal snapshots | invalid-category matrix, mutable-clock expiry/admission, active-record, discard, and reused-worker context tests | implemented |
| Diagnostic modes, atomic outcome/history, primary failure, no callback, and no truncation | `ExecutionCoordinator.complete`, immutable `ExecutionSnapshot`, classifier | diagnostics selection, primary/mixed failure, no-callback, ordered large result/events, and whole-record expiry tests | implemented |
| Immediate owner-scoped shutdown/readiness and no post-close dispatch | synchronous `ContextClosedEvent` listener, shared dispatch gate, direct queued discard | active/queued close, begin-after-close, readiness, async opt-out, and foreign-context tests | implemented |
| Console/management coexistence, stateless no-store API, supported framework surface | servlet security chain, management defaults, no-store filter | Console, management, API error, and ArchUnit tests | implemented |
| Operator documentation and SC4/SC5 boundary | `README.md`, `application.yml` | defaults tests, full wrapper verification, and source/document comparison | implemented |

## Active Project Guardrails

- Simplicity: the implementation retains one direct coordinator design; the review fix is a one-line bounded test adjustment and adds no production abstraction.
- Framework authority and boundary: application/tests use supported `ai.loomspan.api` types only; ArchUnit passes and no Java skill, internal import, reflection, bean replacement, or undocumented key was introduced.
- One admission owner/record: the coordinator remains sole owner of worker/queue capacity, queued bytes, retained records, expiry, terminal outcome, and selected events.
- Trusted identity outside inputs: owner remains separate issuer/subject fields; the admitted JWT is installed on the worker and cleared after execution without entering model input.
- Startup/shutdown: routes remain startup-only; the owner-scoped close listener closes admission/dispatch, discards queued work, and leaves admitted work to the framework shutdown budget.
- Diagnostic contract: exact result text and all selected public events remain untruncated and are published/expired with the terminal record.

## Open Questions and Assumptions

- None affecting correctness or review confidence. Packaged image/resource-lifecycle and published-release verification remain explicitly assigned to SC5.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp "-Dtest=AuthenticatedExecutionApiIntegrationTest,CustomRoleExecutionIntegrationTest" test` — 8 selected HTTP, ownership, nested identity, and custom-role tests passed before the review fix.
- FAIL — `.\mvnw.cmd -B -ntp verify` — 41 of 42 tests passed; the nested integration exceeded its approximately one-second polling budget, producing the resolved P2 finding.
- PASS — `.\mvnw.cmd -B -ntp "-Dtest=AuthenticatedExecutionApiIntegrationTest#nestedExecutionExposesTheOriginalJwtToTheRestHandler" "-Dsurefire.rerunFailingTestsCount=2" test` — the targeted nested public-API execution passed after the bounded polling adjustment.
- PASS — `.\mvnw.cmd -B -ntp verify` — all 42 tests passed; jar repackaging completed successfully against the locally installed beta.4 snapshot.
- PASS — `git diff --check -- README.md src/main src/test ai/thoughts/plans` — no whitespace errors; Git emitted only line-ending conversion notices.

## Residual Risks and Optional Developer Checks

- No ticket-specific optional developer check. SC5 still owns packaged lifecycle ordering, image startup, publication, hosted CI, and release verification.

## Disposition

- `fixes-applied`
