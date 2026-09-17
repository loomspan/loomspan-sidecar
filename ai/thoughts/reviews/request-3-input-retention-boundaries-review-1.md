# Request 3 Input Retention Boundaries Code Review — Cycle 1

## Scope and Repository State

Reviewed Sidecar `a62d516cde559fa6da38d1d33eb04a094e4f47b9` plus the unstaged Request 3 changes in `AuthenticatedExecutionApiIntegrationTest`, `ExecutionCoordinatorTest`, `ExecutionDiagnosticsTest`, and the input/retention section of `beta4-handoff.md`. The ticket is untracked. There are no staged changes. The pre-existing REST fixture changes in four REST test files, the snapshot alignment handoff section, and unrelated untracked phase/ticket files were inventoried and preserved. The local framework checkout is `d7d0ef03aefe44c613ff1d8591bce2a30318a6bf`; the Maven dependency remains `1.0.0-beta.4-SNAPSHOT`.

The HTTP path reads the servlet stream with a byte counter even when content length is unknown, converts a valid JSON object, measures its serialized UTF-8 bytes, and passes that count to `ExecutionCoordinator`. Its admission queue compares the count with the configured queued-byte budget. The worker clears input and authentication after successful handoff and in its final cleanup path after a handoff failure; the retained record stores the terminal snapshot separately. The ticket changes exercise these existing paths without changing production behavior.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation artifact was changed during review.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Unknown-length requests obey the 64-byte raw cap | `LimitedJsonObjectReader.read` counts stream bytes independently of content length and rejects totals above the cap | `chunkedRequestsEnforceTheRawUtf8CapWithoutContentLength` uses a publisher with length `-1`, asserts 64-byte acceptance and 66-byte rejection with no new retained record | implemented |
| HTTP multibyte input obeys serialized queue bytes and the 32-byte limit | `LimitedJsonObjectReader.measure` serializes the parsed map to bytes; `ExecutionCoordinator.AdmissionQueue.offer` reserves and bounds those bytes | `httpUtf8SerializationUsesBytesForQueuedCapacity` holds the worker, admits padded input serialized to 32 bytes, observes `queuedBytes == 32`, then rejects 34 serialized bytes with 429 | implemented |
| Completed work releases input and authentication while retaining selected outcomes | `ExecutionTask.clear` nulls both references; `ExecutionRecord` retains the snapshot independently | Success test retains the full result and selected ordered events while asserting cleared references; pre-handoff failure test retains failure details and asserts cleared references | implemented |
| Supported API and local snapshot verification; handoff updated | No production or framework contract edits; architecture guard forbids internal and autoconfigure dependencies | Focused tests and full Maven verification pass, including the architecture test; handoff records tested revisions, commands, results, and release evidence boundary | implemented |

No research, implementation plan, or testing plan artifacts exist on this approved fast-track route.

## Active Project Guardrails

- The change is limited to proportional test and handoff evidence; it adds no admission owner, retained store, policy, configuration, or extension contract.
- Test code uses only supported `ai.loomspan.api` types; the public API ArchUnit checks passed. No Sidecar Java `@SkillMethod` was added.
- Identity remains in `JwtAuthenticationToken`, outside model input. The tests check its release from completed tasks without changing ownership or passthrough behavior.
- The handoff remains the sole QA/release tracker and distinguishes local snapshot verification from release verification. Existing REST fixture and snapshot alignment work was preserved.

## Open Questions and Assumptions

None affecting this review. The developer-installed snapshot is assumed aligned with the matching local framework checkout under the repository's stated development workflow.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp "-Dtest=AuthenticatedExecutionApiIntegrationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest" test` — 28 tests, zero failures/errors/skips.
- PASS — `.\mvnw.cmd -B -ntp verify` — 69 tests, zero failures/errors/skips; public API architecture checks and executable JAR packaging passed.
- PASS — `git diff --check` — no whitespace errors.
- PASS — `git status --porcelain=v2` — confirmed staged, unstaged, and untracked scope; no staged changes.

## Residual Risks and Optional Developer Checks

Local snapshot verification does not establish final release artifact or hosted CI behavior. Those checks remain in the delivery handoff's release sequence and are outside Request 3.

## Disposition

`clean`
