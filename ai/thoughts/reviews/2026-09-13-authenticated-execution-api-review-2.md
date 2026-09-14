# Authenticated Execution API Code Review — Cycle 2

## Scope and Repository State

Reviewed the complete ticket-scoped production, test, configuration, and README
change against `main`/`origin/main` at
`e3c3f58f1246643c9ac55005228a56bd80b4f57b`, including staged, unstaged, and
untracked files. The implementation is entirely in the working tree, so untracked
source and tests were read directly rather than inferred from Git diff output.
The pre-existing command/protocol edits, SC1/handoff documentation edits, and
`.vscode/` files were treated as unrelated and preserved. The ticket's research,
implementation plan, and testing plan were reviewed; no prior review document
was read or used as an input.

The review traced HTTP parsing and error mapping, public Loomspan catalog and
facade calls, JWT decoder/authority construction, owner extraction, queue and
retention accounting, worker security-context lifetime, diagnostic publication,
expiry, shutdown gating, management/Console coexistence, and documentation. The
selected Full profile remains required and sufficient because the change affects
security, public HTTP contracts, concurrency, and lifecycle behavior.

## Findings

### [P2] Make the queued-token test cross the configured expiry boundary

- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:136`
- Scenario: The test issued a token with a one-second lifetime, slept for 1.2
  seconds, and then used the same token to poll, while the application retained
  the production default 60-second JWT clock skew.
- Impact: The test passed without the token becoming invalid at the HTTP
  boundary, so it did not establish the acceptance criterion that already
  admitted queued work still starts after its captured bearer token expires.
- Evidence: `JwtTimestampValidator` receives the configured clock skew, and the
  test application had not overridden `loomspan-sidecar.auth.jwt.clock-skew`.
  The old post-sleep poll succeeding with the original token was evidence that
  the token remained accepted, not that queued local execution survived expiry.
- Fix: Set clock skew to zero for the application fixture, wait until the
  original token receives `401`, release the blocked worker, and poll the result
  with a renewed token having the same issuer/subject while asserting the
  handler observed the original queued token.

No actionable findings remain after the fix and complete re-review.

## Findings Resolved in This Context

- Resolved the P2 queued-token verification defect in
  `AuthenticatedExecutionApiIntegrationTest`. The revised test now proves both
  sides of the contract: the expired credential is rejected for a new HTTP
  request, while the already admitted task executes with that original token and
  remains readable by a renewed token for the same owner.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Authenticated catalog and execution routes with bounded JSON-object input and problem responses | `SkillCatalogController`, `ExecutionController`, `LimitedJsonObjectReader`, `ApiExceptionHandler`, `NoStoreResponseFilter` | `AuthenticatedExecutionApiIntegrationTest`, `ExecutionRequestBodyIntegrationTest` | implemented |
| Common JWT validation for discovery, JWKS, and public-key modes; trusted role mapping | `SidecarJwtProperties`, `JwtSecurityConfiguration` | `JwtSecurityConfigurationTest`, `ConsoleSecurityIntegrationTest` | implemented |
| Explicit issuer/subject ownership and preserved queued/nested JWT identity | `ExecutionOwner`, `ExecutionCoordinator.ExecutionTask` | `AuthenticatedExecutionApiIntegrationTest`, including the corrected post-expiry case; `ExecutionCoordinatorTest` | implemented |
| One admission/accounting owner bounds workers, queue count/bytes, and retained records | `ExecutionCoordinator`, `AdmissionQueue` | `ExecutionCoordinatorTest` concurrent, equality/excess, retained, and shutdown cases | implemented |
| Completion TTL, immutable terminal outcome, primary failure classification, and diagnostic selection | `ExecutionCoordinator`, `ExecutionSnapshot`, `ExecutionFailureClassifier` | `ExecutionCoordinatorTest`, `ExecutionDiagnosticsTest` | implemented |
| Owning-context close lowers readiness, closes admission/dispatch, and discards waiting work without waiting | `ExecutionCoordinator.onApplicationEvent` and post-lifecycle `destroy` | `ExecutionShutdownIntegrationTest`, `ExecutionCoordinatorTest` | implemented |
| JWT application security coexists with Console API-key security and separate health-only management | `JwtSecurityConfiguration`, production management configuration | `ConsoleSecurityIntegrationTest`, `ManagementEndpointIntegrationTest` | implemented |
| Only the supported Loomspan API/SPI is used and no Java skill fixture is hosted | all production/test imports and YAML/REST fixtures | `SupportedLoomspanApiArchitectureTest` | implemented |
| User documentation covers routes, auth, ownership, limits, diagnostics, shutdown/restart, and SC4/SC5 boundaries | `README.md` | `SidecarDefaultsTest` plus review | implemented |

The implementation safely deviates from the plan's mention of an
`ArrayBlockingQueue`: the coordinator's single `LinkedTransferQueue` admission
hook is still bounded by the same locked count/byte authority and permits true
direct worker handoff without charging waiting-queue capacity. It does not add a
staging queue, duplicate capacity owner, or observable contract change.

## Active Project Guardrails

- Simplicity and technical debt: the change uses standard Spring/JDK facilities,
  one coordinator, one record map, and small transport/configuration types; no
  speculative persistence, cache, or scheduling subsystem was added.
- Framework execution authority: validation, authorization, invocation, nested
  work, and public diagnostic views remain behind `SkillCatalog`,
  `SkillTemplate`, and `RestSkillHandler`; ArchUnit rejects internal and
  autoconfiguration dependencies.
- One admission owner and one retained record: `ExecutionCoordinator` holds the
  only gate, queue accounting, and record map, and publishes terminal outcome
  plus selected events in one snapshot.
- Trusted identity outside model inputs: the verified `JwtAuthenticationToken`
  is captured separately, installed only around worker invocation, and cleared
  in `finally`; ownership retains only issuer and subject.
- Startup/shutdown boundary: startup remains manifest-driven and restart-only;
  the close listener promptly closes Sidecar admission/dispatch while framework
  lifecycle stop retains its single admitted-work budget.
- Diagnostic contract: result text and selected public events are copied without
  Sidecar truncation, and classification follows the facade's primary exception.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp verify` — pre-fix full suite against the refreshed
  locally installed beta.4 snapshot: 33 tests, zero failures/errors/skips.
- PASS — `.\mvnw.cmd -B -ntp -Dtest=AuthenticatedExecutionApiIntegrationTest test`
  — corrected expiry/renewal integration case and the other authenticated API
  cases: 6 tests, zero failures/errors/skips.
- PASS — `.\mvnw.cmd -B -ntp verify` — post-fix full suite and packaging: 33
  tests, zero failures/errors/skips; build successful.

## Residual Risks and Optional Developer Checks

- SC5 still owns packaged image/client lifecycle ordering, framework release
  pinning, and hosted CI evidence. Those explicitly deferred checks do not weaken
  this ticket's application-level gate and are not optional checks for this unit.

## Disposition

- `fixes-applied`
