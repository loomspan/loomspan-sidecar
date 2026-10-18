# PR 8.1.2 Blocking SDK client Code Review — Cycle 1

## Scope and Repository State

Independent Step 5 under the approved Full 5-Step Pipeline. Reconstructed the
change from both working trees, including all untracked Java sources, rather than
using completion checkboxes as evidence. No previous review document was read.

- Sidecar: `main`, HEAD `a1ab2d80d9d575614039b01ffe13341bf9c2ea11`.
- External suite: `main`, HEAD `4ade9b8b1d051f70fc93446a9ce44fca78719941`.
- Ticket scope is the working-tree change relative to these foundation commits;
  there are no staged changes or ticket commits requiring a different merge base.
- Reviewed SDK parent/core/starter POMs, all 15 core production classes and three
  test classes, both starter classes, registration and starter tests, SDK README,
  boundary script, ticket, research and both plans.
- Reviewed external README/project agreement/lane documentation, runner, fixture
  and loss proxy, Compose configuration, complete runner tests and both untracked
  driver source/POM files. Build targets and ignored evidence were inspected as
  verification output, not source changes.
- Traced actual Sidecar controllers, execution records/coordinator/failure
  classifier, architecture guard and matching Framework beta.8 public event type.
  Checked outcome mapping, identity/credential handling, TLS, URI construction,
  deadlines/interruption, response cleanup/close, concurrency and fixture isolation.
  This ticket introduces no persisted schema or data migration.

The initial independent review completed before implementation edits. Only the
following actionable finding was confirmed; it has been resolved below.

## Findings

### [P2] Preserve nullable and blank primary failure messages
- Location: `sdks/java/core/src/main/java/ai/loomspan/sdk/SidecarClient.java:314`.
- Scenario: a FAILED execution has a primary exception whose `getMessage()` is
  null, empty or whitespace. Sidecar's `ExecutionFailureClassifier.classify`
  forwards this value unchanged and `ExecutionController.failure` serializes it.
- Impact: the initial SDK required a nonblank message and therefore threw
  `ProtocolException` for a valid terminal failure, losing the primary failure
  instead of returning the requested FAILED snapshot through getExecution/await.
- Evidence: source trace above and the new focused test reproduced the protocol
  exception before the decoder correction. Existing tests only used nonblank text.
- Fix: decode the message as nullable text while still requiring the failure
  object/kind and rejecting non-string non-null message values.

## Findings Resolved in This Context

Changed the decoder's message requirement from required to nullable. Added
`SidecarClientEdgeTest.preservesAbsentAndBlankPrimaryFailureMessages` covering
null/empty/whitespace preservation through both status and await, FAILED/kind
preservation, request counts and rejection of numeric message values. The test
failed for the intended reason before the one-line correction and passed after it.
No other implementation artifacts were edited. Internal re-review found no
remaining actionable findings.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Discovery, submit, status and await | SidecarClient five operations; independent SDK DTOs | SidecarClientTest operation sequence; real driver | implemented |
| Every outcomes row | Five error categories; reliable acceptance ID only; terminal FAILED return | EdgeTest HTTP/loss/read/wait cases; new nullable-message regression | implemented |
| Exact opaque result and primary failure/events | String result decoder; failure issues; defensive event JSON copies | Opaque string variants, supplementary Unicode, FAILED and nested-event assertions | implemented |
| Local shape/name rejection and one-segment encoding | ObjectNode signature, validation and UTF-8 segment encoder | Zero-send bad-name/input tests; exact raw path and bearer header | implemented |
| Base URL, no Location/redirect/rechallenge/retry | Fixed base-relative endpoints; HC5 automatic behaviors disabled | Redirect target zero-count, 401 challenge, consumed POST/GET connection loss | implemented |
| Expected ID and envelope validation | Strict UUID, scalar/type/time/status/failure decoding | Invalid/mismatched envelopes and last-snapshot tests | implemented |
| Total deadline, first-read failure, interruption | Monotonic budget, cancellation scheduler, bounded sleeps | Slow headers/body/drip, first failure and interrupted sleep/read tests | implemented |
| Limits, response resources, close lifecycle | Bounded read, finally abort/close, owned pool/timer, atomic close | Exact/over-limit known/chunked bodies, repeated failures and subsequent requests, all operations after close | implemented |
| Configuration defaults and TLS | Finite validated builder settings; standard SSLContext/TLS hostname checking | Default/invalid settings; real trusted/untrusted/wrong-host TLS | implemented |
| Credentials and safe diagnostics | Immutable holder without getter/Serializable; fixed exceptions; problem token redaction | Serialization/introspection/stack/log sentinels and unchanged header | implemented |
| Equivalent Boot wiring | Properties and conditional auto-configuration use core builder; standard SSL bundles | Six starter context/HTTP/TLS/lifecycle tests | implemented |
| Real Java completed/failed/lost response | Plain core-only driver, fixed loss proxy and independent effect counter | Required local SDK lane; independent report verification below | implemented |
| SDK documentation | SDK README and external lane docs explain operations, opaque results, outcomes, defaults, TLS and evidence limits | Read against implementation and driver | implemented |
| Framework/build boundary | Core contains no Framework/Sidecar/Boot dependency; existing server ArchUnit unchanged | Boundary control plus ten forbidden-dependency variants; root architecture tests in lane | implemented |

Routine test organization differs from the initial plan: edge tests use their own
class; TLS uses the same explicitly trusted localhost certificate with a second
hostname. Both preserve the intended observable assertions. Accepting nullable
failure messages corrects the decoder to the existing public wire contract and
ticket's primary-failure preservation requirement; it changes no product scope.

## Active Project Guardrails

- Destructive development replacement and simplicity: one final blocking client,
  ordinary immutable values and five error categories; no compatibility APIs,
  migration machinery, refresh/context managers, cancellation or retry subsystem.
- Framework remains execution authority. SDK contains no Framework dependency or
  internal imports; unchanged Sidecar public-API ArchUnit gate remains in the root
  build. No hosted Java skills, undocumented SPI or bean replacement was added.
- Credentials remain transport-only; no token issuance/verification in production
  core. Default errors avoid causes/payloads; explicit result/failure/event access
  preserves server business content. No default wire logger is installed.
- SDK settings use `loomspan-sdk.*`; TLS material uses standard Java/Boot APIs.
  Root Framework pin stays beta.8 and no Framework build/release is performed.
- Current execution-JWT fixture is the explicitly authorized temporary contract.
  Test signing private key remains in memory; token is driver-only. The internal
  Compose network has no provider forwarding, host ports or business-state mounts.
- Maintained reference packs, credentials, business runtimes and deployed data
  remain outside this work. No data reset, commit or push was performed.

## Open Questions and Assumptions

None requiring a developer decision. Full remains the appropriate approved profile
because of the supported API, protocol, security and resource-lifecycle boundaries.

## Verification Results

Commands run from Sidecar unless marked external:

- PASS — `mvn -B -ntp -f sdks/java/pom.xml test` — independent baseline: 11 core
  plus six starter tests, zero failures/errors/skips.
- FAIL (intended red) — `mvn -B -ntp -f sdks/java/core/pom.xml '-Dtest=SidecarClientEdgeTest#preservesAbsentAndBlankPrimaryFailureMessages' test`
  — before decoder fix, ProtocolException at the first null message.
- PASS — same focused command after the decoder fix — nullable/blank values
  preserved and non-string values still rejected.
- PASS — `python sdks/java/tests/test_build_boundary.py` — three tests including
  clean control, two Boot and eight Framework/Sidecar rejection variants.
- PASS — external `.venv/Scripts/python.exe -m pytest tests -q` — 146 tests.
- PASS — external `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` — four files unchanged.
- PASS — external `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- PASS — `git diff --check` in both working trees.
- FAIL — first external `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  — root clean verify (256 reported, zero failures/errors, three configured-browser
  skips), SDK clean install (12 core and six starter tests) and driver packaging
  passed. The runner finalized `unexpected-error` before image/runtime stages.
  Failed evidence is retained at external
  `evidence/sdk/20261018T172109Z-299307b6720b/report.json`. Safe isolated runtime
  initialization subsequently passed once and then ten consecutive times; raw
  exceptions, credentials and private material were not printed.
- PASS — second identical external `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  — all fourteen stages passed, including the full root and SDK rebuilds, real
  driver invocation, graceful shutdown and cleanup. Independently inspected final
  XML: root 256 reported, zero failures/errors, three existing browser skips;
  SDK 12 core and six starter tests, zero failures/errors/skips. Independently
  read the report at external
  `evidence/sdk/20261018T172812Z-d8a63bfd928f/report.json`: all five driver outcome
  assertions true; startup modelRequests=0; final modelRequests=1,
  syntheticEffects=1, submissionAttempts=1 and forwardAttempts=1; readiness UP;
  shutdown exit 143/graceful=true; cleanup passed. Independent SHA-256 checks
  match report core `6941d27f119ca3e4d9f3e17843c83e2f6f3a8b598adda7c51069901431c15601`
  and driver `109a6eb2ae55e84208fa649b4efac9b979ca1caa4317bc25c671a2a482b6a4e6`.
  `docker ps --all --filter 'name=sdk-9776180ac58044e2' --format '{{.Names}} {{.Status}}'`
  returned no remaining project containers after cleanup.

## Residual Risks and Optional Developer Checks

The local composition demonstrates one bounded synthetic effect, not durable
exactly-once business semantics. Standard/hardened recipes, callback security,
future trust-contract replacement and provider execution are outside this ticket;
their absence is documented rather than represented as passing evidence.
No optional developer check is required.

The first run's pre-runtime initialization error did not reproduce in eleven
isolated initialization checks or the complete repeated lane. Its exact cause was
not established; the failed report is preserved and the repeat is recorded
separately. All required gates now have passing executable evidence.

## Disposition

`fixes-applied`. Full initial review, safe in-scope fix, relevant verification and
internal re-review are complete, with no remaining actionable findings. Because
this context changed production and test sources, a fresh Step 5 context must
independently review the final ticket change before pipeline completion.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-18-pr-8-1-2-blocking-sdk-client-review-1.md
  - sdks/java/core/src/main/java/ai/loomspan/sdk/SidecarClient.java
  - sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientEdgeTest.java
SUMMARY: Reviewed the complete two-repository change independently and corrected rejection of valid nullable/blank primary failure messages. Focused red/green verification, boundary checks, 146 offline tests and the final real local SDK lane pass. No actionable findings remain in this context.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `mvn -B -ntp -f sdks/java/pom.xml test`
  - FAIL — focused nullable-message regression before the fix, as intended
  - PASS — `mvn -B -ntp -f sdks/java/core/pom.xml '-Dtest=SidecarClientEdgeTest#preservesAbsentAndBlankPrimaryFailureMessages' test`
  - PASS — `python sdks/java/tests/test_build_boundary.py`
  - PASS — external `.venv/Scripts/python.exe -m pytest tests -q`
  - PASS — external ruff format/check commands listed above
  - FAIL — first external local-lane command before runtime, preserved unexpected-error report
  - PASS — repeated external `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  - PASS — `git diff --check` in both repositories
OPTIONAL_DEVELOPER_CHECKS:
  - none
REVIEW_RESULT: fixes-applied
NEXT: Launch a fresh Step 5 review context.
