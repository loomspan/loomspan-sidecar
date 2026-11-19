# Terminal output-schema failure testing plan

**Sidecar development policy.** Replace superseded development contracts directly.
Omit compatibility shims, legacy adapters, and parallel APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No development
data reset is needed.

## Change Summary

Verify that a failed owned snapshot includes the Framework-selected
`OUTPUT_SCHEMA` failure, canonical `producingSkillName`, and exact issues
independently of event policy. The requested name remains the parent. Java
and Python clients expose the producer and issue data using their normal
failed snapshot path. See the [implementation plan](2026-11-18-expose-terminal-output-schema-failure.md)
and the [ticket](../tickets/2026-11-18-expose-terminal-output-schema-failure.md).

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| HTTP and ownership | Diagnostic policy hides the diagnosis, exposes events under `NEVER`, or allows retrieval by another owner. | Real server, all three policies, signed owner and foreign tokens. |
| Framework boundary | A generic failure or a different branch's schema event is mistaken for the terminal cause. | Real accepted parallel plan with two failed children and retained observer facts, plus focused top-level exception mapping. |
| Issue fidelity | Null output becomes a missing field, path format changes, or issue prose is substituted. | Concrete TypeSafe response and exact pointer/code assertions, plus SDK and mapping tests for exact strings. |
| SDK protocol | New data is ignored or invalid producer values are silently accepted. | Failure wire envelopes for Java and Python, current generic envelopes, and invalid producer values. |

## Existing Coverage and Environment Constraints

- Use JDK 21 or later, the local Framework beta.10 snapshot at
  `8f73de8`, and the existing installed Decisions snapshot. The
  orchestrator installed the Framework snapshot, verified 58 focused
  Framework checks, and verified 23 Sidecar baseline checks. These
  checks establish readiness for the Sidecar tests.
- `SidecarApplicationFixture` supplies a local OpenAI Responses HTTP stub,
  queued responses, and request capture. `JwtTestTokens` and the fixture
  JWKS supply signed execution tokens. `AuthenticatedExecutionApiIntegrationTest`
  shows real HTTP polling and one specific 404 response for a foreign
  owner. `LoomspanSidecarApplicationTest.typesafeProfileRegistersFileModeExecutorLeaf`
  shows how to enable the real extension in a temporary file-mode context.
- Use the locally available Playwright Chromium cache for the overall
  Sidecar suite. Test configurations and database locations must use the
  checkout's fixtures or JUnit temporary directories.
- Python requires an SDK development environment with Python 3.12+,
  the SDK with `fastapi` extra, and `sdks/python/requirements-dev.txt`.
  The default global Python has pytest but lacks the SDK and its runtime
  dependencies. Prefer an existing suitable isolated environment or
  provision a venv under the ignored `target` directory. Set its
  `PYTHONPATH` to the checkout's `sdks/python/src` when verifying the
  current change against dependencies already present in that environment.
- Providers and callback work stay on loopback. No paid accounts, live
  services, privileged trace endpoints, or deployed configuration are needed.

## Failing Test First

- Name `exposesSelectedTriageOutputFailure` (`NEVER` first).
- Type. Full public HTTP integration using the real Framework and Decisions extension.
- Location `src/test/java/ai/loomspan/sidecar/execution/ExecutionOutputSchemaIntegrationTest.java`.
- Arrange a valid two-task plan and one malformed TypeSafe output as
  specified below. Submit once as the owner and poll the returned Location.
  Confirm HTTP 202, HTTP 200, terminal `FAILED`, and absence of events
  before asserting `failure.kind == OUTPUT_SCHEMA`.
- Expected pre-fix failure. The final assertion sees `SKILL_FAILURE` with
  no producing skill or schema issue. A setup error, extra upstream request,
  or unrelated failure is fixture work to resolve before recording red.

## Tests to Add or Update

### 1. Owned TypeSafe failure through the public API

- Location `src/test/java/ai/loomspan/sidecar/execution/ExecutionOutputSchemaIntegrationTest.java`.
- Create one temporary file-mode startup for each diagnostics policy,
  enabling `typesafe`, the local JWKS, a temporary database and a random
  servlet port. Omit the diagnostics property for the `NEVER` row to
  verify the default. Configure documented Framework trace persistence as
  `never` in the fixture and use the public polling response for assertions.
- Write a `parent` YAML skill with `planning_mode: true`, `max_steps: 2`,
  `output_from` referencing the dependent, and deterministic required child
  entries. Bind the business contact message from the parent's
  `/customerMessage` into the same path on the triage child. The triage
  child's closed input object has that single required string. Its JSLT
  input transform creates a native TypeSafe request with model
  `jev-1.13.0`, contact state containing the exact business message, and
  one `noul` repeat question. Use `executor: typesafe`, a local
  `base_url` and an inert fixture `api_key` in the namespaced executor
  configuration.
- Serve JSON `{"answers":{"repeat":{"type":"noul"}}}` from a loopback
  `POST /v1/systemone` endpoint. Capture the request and verify the message
  and question reached the native request. Use output transform
  `{"repeatContactProbability": .answers.repeat.noul}` and a closed
  output schema declaring the property required and numeric. The Framework
  preserves the present null for validation.
- The planner fixture returns one valid planning document in an OpenAI
  Responses envelope from `SidecarApplicationFixture.completion`. Supply
  the exact canonical parent and child names, full required task fields,
  the triage task first, and a second task with a dependency on triage.
  Bind the dependent's required `/probability` from triage's published
  `/repeatContactProbability` and use a test executor counter to detect
  any dispatch. Both children have closed input contracts with all
  properties wholly bound, allowing Framework-assigned argument dispatch.
  Designated-child forwarding needs no final model call.
- Submit real JSON business input to `POST /v1/skills/parent/executions`
  and check 202 and matching location/ID. Poll the returned location
  with a bounded deadline. Require HTTP 200 and `skillName == parent`,
  `status == FAILED`, an explicit JSON null result, and the expected
  primary failure kind and canonical producer. The single issue has
  `path == /repeatContactProbability`, `code == type`, and an explanation
  that null was found where a number was expected. Check the null and
  number terms without cementing incidental validator punctuation.
- Under `NEVER` require the `events` key to be absent. Under `ONERROR`
  and `ALWAYS` require the same failure object and available diagnostic
  history, including the triage schema event. Assert only the documented
  failure fields in the default summary. Check a different subject
  receives 404 and no available failure for the admitted ID. Require one
  planner request, one TypeSafe request, and no dependent executor calls
  after terminal polling. No secondary execution or trace lookup is involved.

### 2. Competing child outcomes through the public API

- Location `src/test/java/ai/loomspan/sidecar/execution/ExecutionOutputSchemaIntegrationTest.java`.
- Under `ONERROR`, accept a plan with two independent tasks in the same
  parallel group and require both children to execute. Use closed zero-input
  YAML executor leaves and a Sidecar test bean with a supported
  `@SkillExecutor` method. Both leaves can use the same annotated
  method and distinguish runs by `SkillExecutorInvocation.skillName()`.
  Configure a designated output child and two step slots so one planner
  response is enough. Write separate schema property names for the two leaves.
- In the schema case, both branches return schema-invalid JSON. Assert both
  output-schema events are present and the terminal response contains only
  the first accepted branch's producing name and exact issue. In the generic
  case, the first accepted branch throws a test `IllegalStateException` and
  the other still produces its schema-invalid result. Assert that the
  unrelated schema event is present and the failed snapshot has
  `SKILL_FAILURE` and no producer or schema issues.
- Use the documented accepted-order result of a normal parallel join.
  The public regression does not depend on worker timing or use an
  internal trace listener to reorder events. The generic case in
  particular rejects selecting an arbitrary schema event regardless of
  when it arrived.

### 3. Primary facade classification

- Location `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java`.
- Extend the existing classification proof to map a top-level
  `SkillException` with a public `SkillFailureDiagnosis` at
  `OUTPUT_SCHEMA`. Pass an exact issue including an empty root pointer
  or escaped pointer segments. Assert the original facade message and
  exact values survive. A non-output diagnosis and an output diagnosis
  attached only to a nested or suppressed throwable remain generic.
- Retain input validation and access denial cases. Compare the mapped
  input issue fields, including the existing input path. Preserve current
  tests for atomic publication, retention, and unchanged event policy.

### 4. SDK wire access

- Locations `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientEdgeTest.java`
  and `sdks/python/tests/test_protocol.py`.
- Java uses the existing local fixture for a normal get/await response.
  Python tests the same parser used by sync and async polling. Use the
  representative wire payload and assert the requested skill stays
  `parent`, failure kind stays `OUTPUT_SCHEMA`, the canonical producer
  uses the corresponding SDK attribute, and all issue fields are
  available without parsing prose. Include an exact empty root pointer
  or escaped segment for fidelity.
- Verify an omitted producing field still gives null/None on the
  existing generic response and a nonstring value triggers the
  normal malformed-protocol behavior. Keep existing tests for HTTP
  errors and normal failed snapshots.

## Safe Verification Commands

- Red at the Sidecar root before the classifier change.
  `./mvnw.cmd -B -ntp -o '-Dtest=ExecutionOutputSchemaIntegrationTest#exposesSelectedTriageOutputFailure' test`.
- Focused Sidecar acceptance and boundary.
  `./mvnw.cmd -B -ntp -o '-Dtest=ExecutionOutputSchemaIntegrationTest,ExecutionDiagnosticsTest,AuthenticatedExecutionApiIntegrationTest,SupportedLoomspanApiArchitectureTest' test`.
- Broad Sidecar verification.
  `./mvnw.cmd -B -ntp -o verify` with the local browser cache.
- Java SDK verification. `./mvnw.cmd -B -ntp -o -f sdks/java/pom.xml verify`.
- Python SDK verification using the environment described above.
  `python -m pytest -q sdks/python/tests`, where `python` resolves to the
  SDK test environment.
- Review whitespace with `git diff --check` and inspect the new tests and
  docs against the ticket and baseline changes. No hosted release workflow
  is part of verification.

## Optional Developer Checks

None. All ticket acceptance observations are executable locally.

## Exit Criteria

- [x] The red test sees the generic failure after confirming fixture admission
  and correct termination.
- [x] All three diagnostics modes, owner isolation, the one-request
  counts, and the two competing-child scenarios pass.
- [x] Both SDKs consume the documented producer and exact issues.
- [x] Focused and broad tests pass, or the implementation report names
  any concrete environmental limitation and the affected evidence.
- [x] Acceptance criteria map to the tests above. Tests stay on loopback
  and use isolated configuration and storage.
- [x] The HTTP, operations, and SDK docs agree with the implemented fields
  and diagnostics behavior.

## Step 4 evidence

- Expected red, `./mvnw.cmd -B -ntp -o '-Dtest=ExecutionOutputSchemaIntegrationTest#exposesSelectedTriageOutputFailure' test`.
  Against the pre-fix classifier, the `NEVER` case reached HTTP 200 with
  the requested parent, failed status, null result, and omitted events.
  The fixture counted one planner call, one TypeSafe call, zero dependent
  calls, and a 404 for the foreign owner. The failure-kind assertion
  saw `SKILL_FAILURE` instead of `OUTPUT_SCHEMA`. In that red run the
  two other policy rows also identified an event field typo in the new
  fixture, corrected to inspect `details` before focused verification.
  Local log `target/pr32-red.log`.
- Pass, `./mvnw.cmd -B -ntp -o '-Dtest=ExecutionOutputSchemaIntegrationTest,ExecutionDiagnosticsTest,AuthenticatedExecutionApiIntegrationTest,SupportedLoomspanApiArchitectureTest' test`.
  All 29 passed, including the three TypeSafe policy rows and both parallel
  terminal-cause cases. Local log `target/pr32-sidecar-focused.log`.
- Pass, `./mvnw.cmd -B -ntp -o verify`.
  Sidecar passed 307 and skipped one opt-in Python issuance fixture check.
  The local browser tests and application packaging completed. Local log
  `target/pr32-sidecar-verify.log`.
- Pass, `./mvnw.cmd -B -ntp -o -f sdks/java/pom.xml verify`.
  The core module passed 36 with two opt-in issuance fixture checks skipped.
  The Spring starter passed 38 with one opt-in Python issuance fixture
  check skipped. Local log `target/pr32-java-sdk.log`.
- Pass, `./mvnw.cmd -B -ntp -o -f sdks/java/pom.xml '-Dtest=SidecarClientEdgeTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`.
  All 16 passed after making the HTTP fixture's changing response body
  thread-safe. Local log `target/pr32-java-sdk-focused.log`.
- Pass, `./target/pr32-python/Scripts/python.exe -m pytest -q sdks/python/tests`.
  Both client variants use the tested parser. The suite passed 480 with
  two POSIX-only symlink checks skipped on Windows. Local log
  `target/pr32-python-sdk.log`.
- Pass, `git diff --check`.
