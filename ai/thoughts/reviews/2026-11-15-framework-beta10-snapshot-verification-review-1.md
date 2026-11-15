# Framework beta.10 snapshot compatibility review — Cycle 1

## Scope and Repository State

Reviewed the uncommitted development dependency switch from beta.9 to
`1.0.0-beta.10-SNAPSHOT`, current-checkout documentation, authoring metadata,
repository guidance, and the dated verification ticket. Sidecar remains
`1.0.0-beta.5-SNAPSHOT`. Release-only beta.9 gates deliberately remain intact.
No application or test implementation was changed by this reviewer. The scope
is compatibility assessment, not a repair of all newly exposed incompatibilities.

## Findings

### [P1] Replace the quickstart host's obsolete model protocol before claiming it works

- Location: `examples/quickstart/host/host.py:137` and response builder at line 86.
- Scenario: The snapshot's `openai` driver sends a request to `/v1/responses`.
- Impact: The bundled local quickstart model host returns 404; it cannot complete
  its planner workflow with this dependency.
- Evidence: An independent ephemeral loopback server using the actual `Handler`
  returned 404 for POST `/v1/responses` and 200 for POST `/v1/chat/completions`.
  Matching Framework documentation, `agent-skills/loomspan-docs/references/
  skill-authoring/model-selection-and-connections.md:64`, specifies stateless
  Responses and excludes Chat Completions-only services.
- Follow-up: Directly replace the route and payload with Responses, then exercise
  the complete local quickstart. Do not add a legacy protocol fallback.

### [P2] Update provider fixtures to exercise the Responses contract

- Location: `src/test/java/ai/loomspan/sidecar/support/SidecarApplicationFixture.java:86`
  and `src/test/java/ai/loomspan/sidecar/http/HttpExecutorIntegrationTest.java:51`.
- Scenario: Integration tests invoke model skills through the new framework.
- Impact: Model-backed configuration, credential, authenticated execution, and
  captured-generation tests fail before proving their intended behavior.
- Evidence: The shared fixture only serves Chat Completions and returns `choices`.
  The HTTP tool fixture also reads `messages` and returns `choices`; its snapshot
  report contains `OpenAIInvalidDataException: output is not set` from the Responses
  model. Other observed failures include model execution remaining incomplete or
  returning FAILED and fixture blocking latches never being entered.
- Follow-up: Replace request/response and tool-call fixture contracts with
  Responses, retain the behavioral assertions, then rerun the affected and broad
  suites. These failures alone do not establish a Sidecar production-adapter defect.

### [P2] Replace the removed Ollama driver in mounted-skill fixtures

- Location: `src/test/java/ai/loomspan/sidecar/skill/MountedSkillRegistrationIntegrationTest.java:129`.
- Scenario: The two positive database activation cases load `driver: ollama`.
- Impact: Both fail startup during skill preparation under the snapshot.
- Evidence: The full suite records two activation errors in this class. The
  matching Framework connection documentation lists `openai`, `anthropic`,
  `gemini`, and `typesafe` and specifies Ollama through `openai`, with no native
  Ollama driver. The fixture's deployment properties also use the removed driver.
- Follow-up: Use the supported model connection contract in both fixture sources
  without weakening startup validation.

### [P2] Separate release-input unit fixtures from the current development pin

- Location: `scripts/prepare-release.py:41` and
  `src/test/java/ai/loomspan/sidecar/release/ReleasePreparationTest.java:36`.
- Scenario: The ordinary test suite asks the script to validate a beta.9 release
  plan while the actual development POM is beta.10-SNAPSHOT.
- Impact: The expected-success assertion fails independently of runtime behavior.
- Evidence: Direct invocation of the same validate-only command prints
  `pom.xml does not use the beta 9 framework line` and fails. The script explicitly
  accepts only beta.9 or beta.9-SNAPSHOT POM text.
- Follow-up: Isolate the release-validation test's POM fixture or update release
  preparation deliberately after publication. Preserve rejection of snapshot
  releases; do not relax release gates to make the suite green.

## Findings Resolved in This Context

None. Findings were sent to the parent before any proposed implementation changes.
The authorized assessment preserves these incompatibilities for explicit follow-up.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Development pin and guidance identify beta.10-SNAPSHOT | POM, AGENTS, README, setup/Console docs, authoring metadata | Version consistency command passes | Implemented |
| Record local compatibility results and limits | Ticket plus this review | Full suite log and preserved reports; independent host probe | Implemented as assessment; compatibility fails |
| Keep Sidecar version and release safety | Sidecar version unchanged; release gates unchanged | Release test exposes hardcoded development-line assumption | Safe intentional scope decision |
| Public framework boundary; no shims | No application imports or behavior changed | Compilation completed; existing boundary checks remain | Implemented |

## Active Project Guardrails

- Applied the design lens and destructive replacement policy; no adapters,
  migration machinery, or new extension contracts were introduced.
- Used matching local Framework documentation for protocol interpretation.
- Runtime evidence is from Sidecar tests, not Framework test claims.
- No development-data reset, deployment, live provider call, or publication occurred.
- The dependency/metadata edits are internally coherent and contain no secrets.

## Open Questions and Assumptions

The exact production compatibility of model invocation remains unproven until
provider fixtures use the supported protocol. The parent owns any beta.9 focused
comparison and records its results separately; no baseline result is presumed here.

## Verification Results

- PASS — `python scripts/sidecar_version.py check` (independently run): POM and
  authoring metadata agree on Sidecar beta.5-SNAPSHOT and Framework beta.10-SNAPSHOT.
- PASS — `git diff --check` (independently run).
- CONFIRMED INCOMPATIBILITY — Python loopback HTTP probe importing the quickstart
  host, starting its `Handler` on an ephemeral port, and POSTing `{}` to both
  model routes: Responses 404; Chat Completions 200. No signer export was invoked.
- FAIL — `python scripts/prepare-release.py --validate-only --project-version
  1.0.0-beta.4 --loomspan-version 1.0.0-beta.9 --tag v1.0.0-beta.4`
  (independently run): current POM rejected as outside beta.9 line.
- FAIL — `.\mvnw.cmd -B -ntp -o clean verify` (parent-run, final log and reports
  independently inspected): 301 tests, 9 failures, 3 errors, 1 skipped. Compilation
  succeeded. Packaging was not reached because tests failed. Snapshot reports
  were preserved by the parent under `target/beta10-surefire-reports`.
- NOT RUN — concurrent Maven execution by reviewer, to avoid modifying the
  parent's build outputs. No duplicate full suite was needed to confirm failures.
- NOT RUN — Docker deployment, real providers, hosted CI, or release publication;
  these are outside the local assessment and cannot be claimed as passing.

## Residual Risks and Optional Developer Checks

Adapt the provider fixtures and quickstart directly to supported Responses
contracts, replace removed driver fixtures, and resolve release-test isolation
before claiming the snapshot is fully compatible. Rerun Sidecar verification
after that work. Actual hosted model/provider behavior remains untested.

## Disposition

Compatibility verification failed; the dependency-switch assessment is complete.
This is not a clean review or a release-readiness approval. No implementation
fixes were applied in this review context.
