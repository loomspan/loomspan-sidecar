# Terminal output-schema failure code review — cycle 1

## Scope and Repository State

Reviewed the Full 5-Step Pipeline implementation against the ticket, research,
and both plans. Sidecar `main` and `origin/main` were at
`9769f29dcfc764a7d284268ab7542e24057194b4`. There were no staged changes.
The review included unstaged implementation, API and SDK documentation, and
the untracked HTTP integration fixture and pipeline artifacts.

The existing TypeSafe and HTTP executor changes in `docs/setup.md`,
`pom.xml`, `application.yml`, `HttpSkillExecutor.java`,
`LoomspanSidecarApplicationTest.java`, `HttpExecutorTlsIntegrationTest.java`,
`HttpFrameworkFixture.java`, `HttpSkillExecutorTest.java`, and
`HttpTestSupport.java` form the working baseline. The ticket fixture uses
the already configured TypeSafe profile. Verification ran against the local
Framework beta.10 snapshot containing PR 32.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. The implementation, tests, documentation, configuration and plans were
left as reviewed.

## Acceptance-Criteria and Plan Conformance

| Criterion or decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Submit the deterministic failed TypeSafe plan with business input | The normal submission endpoint validates business input, submits the requested skill and returns its polling location in `ExecutionController.java:41`. | `ExecutionOutputSchemaIntegrationTest.java:62` creates the real TypeSafe executor, a two-task planner response, a dependent input binding and the malformed repeat answer. Its HTTP helper checks 202 and the location before polling. | Implemented |
| Default owned snapshot returns 200, the requested parent, null result and the terminal output issue, omitting events | `ExecutionCoordinator.java:227` stores one terminal snapshot with the original requested name and diagnostics chosen by policy. `ExecutionFailureClassifier.java:23` reads the public top-level diagnosis and maps the exact issue records. `ExecutionController.java:55` serializes the retained snapshot and optional producer. | `ExecutionOutputSchemaIntegrationTest.java:105` polls under the default and explicit modes, checking the exact canonical child, pointer, validator code, null-versus-number explanation and absence of `events` under `NEVER`. `ExecutionDiagnosticsTest.java:94` verifies the exact root pointer and issue message without rewriting. | Implemented |
| One planner request, one TypeSafe request and no dependent dispatch | The normal worker invokes the admitted skill once through the public Framework API in `ExecutionCoordinator.java:369`. | `ExecutionOutputSchemaIntegrationTest.java:113` counts both requests, verifies the actual TypeSafe request uses the business input, and checks that the dependent executor was not called. No trace or extra execution is used. | Implemented |
| Same terminal diagnosis alongside normal events and owner isolation | `ExecutionController.java:55` uses the issuer-and-subject owner through `ExecutionCoordinator.java:152` before constructing the response. Event selection in `ExecutionCoordinator.java:227` is independent of diagnosis. | `ExecutionOutputSchemaIntegrationTest.java:62` covers `ONERROR` and `ALWAYS` and checks a foreign subject receives 404 with no failure details. `ExecutionDiagnosticsTest.java:112` checks atomic publication. | Implemented |
| Java and Python SDKs expose supported wire fields | `SidecarClient.java:324` and `_protocol.py:312` use the existing failure parsing path and preserve the optional producer and exact issue strings. Sync and async Python clients share that parser. The HTTP reference, operations guide and SDK READMEs document the contract. | `SidecarClientEdgeTest.java:85` exercises actual HTTP reads. `test_protocol.py:256` covers the shared parser. Tests include a root pointer, absence on generic failures, and malformed producer types. | Implemented |
| Only the selected terminal cause supplies output details | `ExecutionFailureClassifier.java:13` preserves input-validation and access-denied precedence and reads only the primary public `OUTPUT_SCHEMA` diagnosis. The coordinator makes no event-based attribution. | `ExecutionOutputSchemaIntegrationTest.java:133` checks two schema failures with only the selected producer returned, then checks an ordinary selected failure with an unrelated retained schema event and no schema details. `ExecutionDiagnosticsTest.java:94` separately checks non-output and nested or suppressed diagnoses. | Implemented |

## Active Project Guardrails

- Framework chooses the cause. Sidecar consumes `ai.loomspan.api` values and
  the fixture uses the supported annotated executor. The Sidecar architecture
  test ran against the updated snapshot.
- The retained result, diagnosis and selected events use the existing
  owner-checked record. The failure mapping exposes the validator issues and
  preserves the primary message. Raw payloads and exception chains are absent
  from the serialized failure model. The policy tests check its field set.
- Both SDKs use their existing parsers and issue records. The development
  contract is updated directly with no alternate paths. Execution retention
  is in memory and requires no data reset.
- Consumer documentation describes the current HTTP and SDK fields and
  diagnostics policy without release or work-tracking history.

## Open Questions and Assumptions

None. The local TypeSafe extension and Framework snapshot are the tested
dependencies. Framework publication and release dependency pinning follow
the separate repository release process.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp -o '-Dtest=ExecutionOutputSchemaIntegrationTest,ExecutionDiagnosticsTest,AuthenticatedExecutionApiIntegrationTest,SupportedLoomspanApiArchitectureTest' test`. All 29 checks passed, including the five new HTTP cases. Log `target/pr32-review1-sidecar.log`.
- PASS — `.\mvnw.cmd -B -ntp -o verify`. Sidecar passed 307 and skipped one opt-in issuance interop test. Browser checks and packaging passed. Log `target/pr32-review1-sidecar-full.log`.
- FAIL on initial run — `.\mvnw.cmd -B -ntp -o -f sdks/java/pom.xml verify`. Java core passed. One unchanged Spring callback test failed during local signing-key setup before it could assert the overlap JWKS diagnostic. Log `target/pr32-review1-java.log`.
- PASS on isolated rerun — `.\mvnw.cmd -B -ntp -o -f sdks/java/pom.xml verify`. Both modules passed, totaling 74 passes and three opt-in issuance interop skips. The callback setup failure did not recur. Log `target/pr32-review1-java-retry.log`.
- PASS — `.\target\pr32-python\Scripts\python.exe -m pytest -q sdks/python/tests`. Python passed 480 and skipped two POSIX-only symlink cases on Windows. Log `target/pr32-review1-python.log`.
- PASS — `git diff --check`. No whitespace errors.

## Residual Risks and Optional Developer Checks

No ticket acceptance checks remain. The opt-in issuance checks and POSIX-only
symlink cases cover different behavior. The initial signing-key setup failure
was isolated to an unchanged test and did not recur. Provider verification used
local substitutes and no hosted release check was run.

## Disposition

`clean`. The required acceptance criteria passed independent verification.
