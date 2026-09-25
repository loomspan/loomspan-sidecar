# PR 6.2 Scoped Personal Access Tokens Code Review — Cycle 1

## Scope and Repository State

Independent pipeline review of the complete ticket-scoped working tree against the PR 6.2 ticket, research, implementation plan, testing plan, design lens, and repository instructions. Scope included staged, unstaged, and untracked files. The change adds PAT persistence and lifecycle, console UI, bearer authentication on the shared management API, credential-bound editing leases, gate rechecks, tests, and documentation. No unrelated staged changes were present. The new plan and research files are pipeline artifacts.

I traced browser and bearer requests through the management security chain, controller route map, identity/account reads, editing transition and publication gates, token persistence, and the separate JWT execution chain. I reviewed failure and cleanup paths, race behavior, secret exposure, abuse controls, and test assertions before checking plan conformance.

## Findings

No actionable findings remain after the fixes below.

## Findings Resolved in This Context

### [P2] Record the real validation and publication audit outcome
- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:176,205`
- Scenario: A candidate validation returns an unsuccessful result, or framework activation succeeds but draft cleanup or outcome recording then fails.
- Impact: The new audit log called validation `success` or publication `denied`, misrepresenting the state an operator must diagnose after a publication fault.
- Evidence: `RuntimeConfigurationService.publishCandidate` sets the published snapshot before the `draft_cleanup_failed`, `history_pruning_failed`, or `outcome_recording_failed` errors; validation can return a result with `successful() == false` and still attach it to the draft.
- Fix: Validation logs `valid` or `invalid` from the actual result. Publication logs `activated_fault` with the observed published ID for post-activation failures, `failed` for earlier publication failures, and `denied` for admission conflicts. A focused captured-log test exercises invalid validation and post-activation failure.

### [P3] Remove unused credential-origin helper
- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementCredential.java`
- Scenario: Origin formatting existed both in the editing service and in an uncalled credential helper.
- Impact: Duplicate rules could diverge when credential binding changes.
- Evidence: Repository-wide caller search found no use of `ManagementCredential.origin`.
- Fix: Removed the unused helper; the editing service remains the single lease-origin formatter.

### [P2] Exercise the expiry boundary in the HTTP suite
- Location: `src/test/java/ai/loomspan/sidecar/management/ManagementPersonalTokenHttpIntegrationTest.java`
- Scenario: The required future and 30-day expiry limits could regress without an HTTP assertion at the boundary.
- Impact: A malformed or overlong token lifetime might pass despite the ticket's explicit cap.
- Evidence: The existing test covered the seven-day default and an over-limit value but not now, past, or exactly 30 days.
- Fix: Added HTTP assertions rejecting now/past and accepting exactly 30 days while preserving the issuance-rate boundary check.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Own-token lifecycle, one-time secret, expiry bounds | `ManagementPersonalTokenController`, service, repository, V4 table, console page | `ManagementPersonalTokenHttpIntegrationTest` lifecycle, expiry, active count | implemented |
| Shared API, CSRF, mixed and invalid credentials | `ManagementBearerFilter`, `ManagementSecurityConfiguration`, existing editing/configuration controllers | HTTP mixed-credential and CSRF cases; `ManagementBearerFilterTest` closed map | implemented |
| Cumulative preset and live-role intersection | Bearer operation map; account lookup; `ManagementEditingService` gate checks | HTTP Read/Edit/Publish and viewer reduction cases; delayed validation/publication tests | implemented |
| Import/rollback and publication limits; exact candidate | Filter route map; shared import/rollback services; publication admission | Route-map test, HTTP exact-candidate publication, existing PR 6.1 suites | implemented |
| Revocation, expiry, credential changes, preserved drafts | Token service, identity revocation, lease expiry, durable draft store | HTTP lifecycle/account transitions and delayed-gate tests | implemented |
| Management/execution separation | Ordered management and JWT security chains | PAT-to-execution and existing JWT-to-management tests | implemented |
| Bounded abuse and safe audit | Issuance caps, attempt limiter, audit logger | HTTP count/expiry, filter limit test, captured-log tests | implemented |
| Installed snapshot integration and documentation | Pinned Maven dependency; admin, operations and integration docs | Focused tests and full `mvnw.cmd verify` | implemented |

## Active Project Guardrails

- The development contract is a direct V4 token table and one shared management API, without legacy adapters or a second authoring path. Existing development data needs no reset. The new helper removed during review reduced duplicate origin logic.
- Only supported `ai.loomspan.api` framework types appear in application and test code; the existing ArchUnit rule forbids internal and autoconfigure imports.
- JWT execution remains separate from local management PATs. Credential identity comes from the security context, while token validity and live account role are checked again at mutation admission.
- The single lease, user-owned durable draft, explicit handoff, exact validation proof, base check, and publication lock remain in their PR 6.1 services.

## Open Questions and Assumptions

None affecting correctness. The configured HTTPS deployment and manual clipboard behavior remain optional developer observations.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ManagementPersonalTokenHttpIntegrationTest,ManagementBearerFilterTest,ManagementEditingServiceTest' test` — 19 tests, zero failures/errors after fixes.
- PASS — `.\mvnw.cmd -B -ntp verify` — initial independent run before fixes: 215 tests, zero failures/errors, three optional production Compose browser skips.
- PASS — `.\mvnw.cmd -B -ntp verify` — final run after fixes: 216 tests, zero failures/errors, three optional production Compose browser skips.
- PASS — `git diff --check` — no whitespace errors.
- NOT RUN — configured HTTPS deployment/clipboard check — requires a configured environment or browser interaction; nonblocking.

## Residual Risks and Optional Developer Checks

In a configured non-production HTTPS deployment, an operator can inject a one-time PAT through the client environment and inspect resulting audit metadata. The one-time secret display and clipboard interaction can be inspected in a browser. Neither was represented as automated verification.

## Disposition

- `fixes-applied`; a fresh Step 5 context must review the changed implementation artifacts.
