# SDK build and external-suite SDK lane testing plan

## Change Summary

This is pipeline-mode Step 3, continuing directly from Step 2 in the same context, using the approved Full 5-Step Pipeline. Inputs read: ticket `ai/thoughts/tickets/2026-10-18-pr-8-1-1-sdk-build-and-suite-sdk-lane-foundation.md`, matching research artifact and implementation plan (same stem under `ai/thoughts/research/` and `ai/thoughts/plans/`). No implementation or tests have been run during planning.

The change adds independent Java 21 SDK JAR skeletons, the starter's optional Boot 4.1.0 dependencies, always-on dependency isolation and a separate Docker startup/readiness/shutdown lane. It does not add SDK invocation behavior. **Replace obsolete development contracts directly; no compatibility paths.** Apply [the design lens](../design-lens.md). No data reset is necessary.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Maven isolation | Parent configuration present but not inherited; transitive graph skipped; unexpected false positives | Clean control + eight actual failing Maven builds, both SDK modules |
| Version/build identity | Exported old source or stale JAR reported as current checkout | Exact checkout build args, HEAD/dirty, JAR hashes and embedded beta.8 metadata |
| Provider boundary | Shell credentials or network permit accidental provider access | Explicit container environment, internal-only network, no forwarding fixture, zero smoke requests |
| Lifecycle | Readiness timeout leaks containers; forced stop counted as graceful | Unit failure matrix + actual SIGTERM-compatible exit and graceful completion |
| Evidence | Failed preflight/empty recipe yields no report; diagnostic leaks | Initialize/finalize on failures, atomic write, secret/payload sentinel assertions |
| Preserved lanes | New tooling alters existing config/reference bytes or tests only new lane | Full local tests, existing real smoke/all mock replay, baseline hashes/diffs |

## Existing Coverage and Environment Constraints

Sidecar uses Maven Surefire, JUnit/AssertJ and ArchUnit. Existing `LoomspanSidecarApplicationTest`, `ConfigurationModeIntegrationTest`, `JwtStartupIntegrationTest`, `SupportedLoomspanApiArchitectureTest` and `ExecutionShutdownIntegrationTest` cover public-surface startup/configuration/security/lifecycle in process; they cannot prove the external runner or SDK dependency graph. The root `mvn clean verify` remains a required regression gate.

Suite `tests/test_runner.py` demonstrates pytest/monkeypatch/tmp_path conventions and restoration/report tests; `tests/test_replay.py` protects reference packs. Existing tooling is intentionally not reformatted or extended to own SDK scenarios. Add new isolated tests under `tests/test_sdk_runner.py`. Real Maven boundary tests live in Sidecar `sdks/java/tests/test_build_boundary.py`, use Python standard-library unittest and run explicitly rather than slowing every suite pytest invocation.

Prerequisites observed in research: Maven 3.9.6/Java 21 via `JAVA_HOME`; plain java absent from PATH; suite `.venv/Scripts/python.exe` has pytest/httpx/PyYAML/cryptography; Ruff is available; Docker is available. Framework beta.8 is installed locally. Existing runtime containers were stopped; start them with `scripts/start.py`. Do not run Framework install or change existing source pins. Public Maven downloads/Docker image pulls may be needed; no paid provider access is permitted. Use only temporary state for new SDK smoke. Existing smoke/replay adds isolated synthetic rows and preserves prior rows; no reset is authorized.

Before implementation, record suite maintained-file hashes and the existing `docs/project-agreement.md` bytes/diff. A final comparison to HEAD alone cannot prove preservation of that pre-existing dirty file.

## Failing Test First

- Name: `test_local_builds_actual_checkout_and_records_readiness_and_shutdown`.
- Type: pytest runner acceptance test with subprocess/runtime boundary doubles.
- Location: suite `tests/test_sdk_runner.py`.
- Arrange/Act/Assert: temporary Sidecar checkout/evidence destination; invoke `run_sdk.main` with local recipe; simulate successful Maven/package metadata and Docker lifecycle; assert root verify and SDK install commands target that checkout, no Framework command/override, readiness checked before stop, normal shutdown required, report contains recipe/revision/beta.8/SDK build identities.
- Expected pre-fix failure: runner import/capability absent. Capture this missing-capability failure once, then implement. Do not treat an unrelated import/dependency error as proof of a later behavioral defect.
- First build capability check: `mvn -B -ntp -f sdks/java/pom.xml clean install` initially lacks the reactor. Once added, boundary tests supply a genuinely forbidden graph and must fail specifically at the Enforcer rule.

## Tests to Add or Update

### 1. SDK independent build and dependency graph rejection

- Type: real Maven integration, standard-library unittest orchestration.
- Location: Sidecar `sdks/java/tests/test_build_boundary.py`.
- Proves: both children inherit normal-build enforcement; core/starter build independently from Sidecar; starter-to-core allowed; forbidden direct and transitive Framework/Sidecar coordinates rejected in either module.
- Inputs: copy SDK reactor to temporary directory, remove copied targets; add standalone synthetic forbidden-artifact modules and, for transitive cases, a benign other-group bridge depending on that artifact. All synthetic artifacts use test-only versions. Execute the temporary reactor through verify so dependency nodes have JARs before consuming modules resolve. Do not inject into checked-in POMs or overwrite real Framework/Sidecar artifacts in the local Maven cache.
- Cases: core/starter × Framework (`ai.loomspan:loomspan-spring-boot-starter`) / Sidecar (`ai.loomspan:loomspan-sidecar`) × direct/transitive = eight negatives. Positive normal reactor control; verify core has no Boot runtime dependencies and starter resolves Boot 4.1.0 metadata.
- Assertion: negative return code plus Enforcer banned-dependency diagnostic naming the forbidden artifact. Plugin download/compiler/parent-resolution failure is a failed test, not a successful rejection. Synthetic dependency nodes must not inherit the SDK parent ban themselves, or the wrong module might prove rejection.
- Doubles: none for Maven. Resolve tools/plugins using ordinary local development Maven cache. Keep fixture reactor version distinct from real released artifacts.

### 2. Recipe validation and evidence on every selected recipe

- Type: pytest, parameterized cases.
- Location: suite `tests/test_sdk_runner.py`.
- Proves: local selects exactly foundation smoke; standard/hardened are recognized but exit nonzero with `no scenarios` and failed per-run JSON. Unknown recipe is usage failure. Empty recipes do not trigger builds/runtime.
- Inputs: temporary output directory and supplied checkout; deterministic run ID/time where needed.
- Doubles: build/runtime calls fail the test if touched for an empty recipe.
- Edge cases: failed checkout preflight, unavailable Maven/Docker, failed Sidecar build, failed SDK install, missing/wrong embedded Framework version and missing SDK JAR each leave explicit failure/not-run stage status. Identity fields unavailable before build remain explicit null/unavailable.

### 3. Actual build provenance and default safety

- Type: pytest with temporary JAR ZIP metadata and subprocess capture.
- Location: suite `tests/test_sdk_runner.py`.
- Proves: actual supplied/local checkout is built; builds include tests; SDK locally installs; version comes from packaged Framework entry rather than hardcoded evidence; Git dirty state and artifact hashes recorded. Root Maven command includes no Framework override and no Framework source build.
- Inputs: temporary fake Sidecar/SDK JAR metadata; inject dirty checkout; environment with distinctive fake provider credential and Java option values.
- Doubles: external command execution only; use real metadata parsing and report serialization.
- Edge cases: wrong/multiple/missing Framework entry rejected; defaults do not silently reuse `.build/sidecar-beta.3` or old suite build record. Container definitions have explicit environment and no host environment forwarding, Docker socket or credential mounts. Compose networking is internal and readiness uses bounded container-local HTTP with no host publication.

### 4. Bounded readiness, graceful shutdown and cleanup matrix

- Type: pytest doubles for command, HTTP and clock boundaries; real local recipe for final proof.
- Location: suite `tests/test_sdk_runner.py`.
- Proves: wait tolerates initial unready response and succeeds only on `UP`; timeouts, early exit, malformed health and runtime failures fail smoke. Container gets normal stop; exit 0 or 143 plus in-memory Spring graceful completion and no OOM/forced kill is required. Cleanup happens after startup failure, readiness failure, interrupt and shutdown failure.
- Inputs: success plus each failure phase, including cleanup-only and primary+cleanup failures.
- Edge cases: forced kill/137 fails even if cleanup succeeds; failure is never overwritten by later cleanup PASS; interrupt yields failed evidence and nonzero exit; unique Compose project used throughout, no commands to retained project's volumes/data. Request polling timeout is explicit and bounded; test clock avoids real waits.

### 5. Deterministic fixture and diagnostic secrecy

- Type: pytest local HTTP tests and report-capture assertions.
- Location: suite `tests/test_sdk_runner.py`; fixture implementation `scripts/sdk_lane/fixture.py`.
- Proves: fixture emits fixed OpenAI-compatible completion and safe numeric request counters, has no forwarding path and logs no body/auth values. Smoke expects zero model requests. Fixture HTTP server uses ephemeral loopback during tests, not public services.
- Inputs: synthetic request/auth/private-key/payload sentinel strings, fake Maven stderr and fake container logs carrying those strings.
- Doubles: command failures/exceptions contain deliberately hostile diagnostics; HTTP server itself real.
- Edge cases: both report bytes and captured stdout/stderr exclude sentinels; raw exception/build/container output never persisted; expected report contains only stage/category/exit/status information. Only public verification key is serialized. Existing secrets/config files are never read by new lane.

### 6. Actual composed local recipe and preserved runtime regressions

- Type: provider-free Docker integration and maintained regression scripts.
- Location: new runner + existing suite commands below.
- Proves: installed SDK artifacts, fresh actual Sidecar JAR/image, mounted file model config, readiness UP, fixture count zero, scoped clean shutdown and durable PASS report. Then prove retained embedded beta.9/Python beta.3-source lanes still execute equipment and TypeSafe mocks with their original references.
- Inputs: temporary SDK state/public key; existing retained credentials/runtime used only by documented retained startup commands.
- Isolation: no new provider secrets, no published fixture/main Sidecar ports, internal Docker network, no business data mounting in SDK composition.
- Edge cases: run actual standard/hardened expected failures and inspect their reports. Confirm new lane cleanup leaves retained runtime intact and no SDK project containers/network remain.

## Safe Verification Commands

Commands are PowerShell. Run Sidecar commands from `C:/opendev/code/loomspan-sidecar`, suite commands from `C:/opendev/code/loomspan-sidecar-test-suite`. Maven resolves Java through existing `JAVA_HOME`; use `mvn.cmd`/full known path if shell resolution requires it. Separate commands below are separate invocations, not shell separator chains.

| Scope | Command | Required result |
| --- | --- | --- |
| SDK build | `mvn -B -ntp -f sdks/java/pom.xml clean install` | Both independent JAR modules installed, Boot 4.1.0 resolved |
| SDK boundaries | `C:/opendev/code/loomspan-sidecar-test-suite/.venv/Scripts/python.exe sdks/java/tests/test_build_boundary.py` | Positive control passes; all eight negatives reject intended artifact |
| Focused suite | `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q` | All new runner tests pass |
| Formatter | `ruff format scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` | Only new code formatted |
| Format gate | `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` | Pass |
| Lint gate | `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` | Pass |
| Real SDK lane | `.venv/Scripts/python.exe scripts/run_sdk.py local` | PASS report with identities/readiness/shutdown |
| Empty standard | `.venv/Scripts/python.exe scripts/run_sdk.py standard` | Expected nonzero; explicit empty-recipe failed report |
| Empty hardened | `.venv/Scripts/python.exe scripts/run_sdk.py hardened` | Expected nonzero; explicit empty-recipe failed report |
| Root regression | `mvn -B -ntp clean verify` | Existing Sidecar tests/package pass; no SDK modules in reactor |
| Full safe local suite | `.venv/Scripts/python.exe -m pytest tests -q` | All retained/new unit and local fixture tests pass |
| Retained startup | `.venv/Scripts/python.exe scripts/start.py` | Existing five-service stack ready; providers disabled |
| Retained smoke | `.venv/Scripts/python.exe scripts/smoke.py` | Both integrations pass, providers disabled/prior rows preserved |
| Retained all mocks | `.venv/Scripts/python.exe scripts/run_suite.py mock --suite all` | Equipment and TypeSafe deterministic replay pass |
| Text integrity | `git diff --check` in each checkout | No new whitespace defects |

Inspect actual new report JSON with a JSON parser; assert required fields and stage outcomes rather than assuming exit code alone proves evidence. For fixture preservation, compare pre/post SHA-256 maps for existing suite `config/`, `fixtures/`, app source and retained scripts; separately compare saved initial agreement bytes. Capture `git status --short` in both checkouts and verify root POM has no diff. SDK README additions are allowed; maintained lanes/packs are not.

The runtime regression is required and safe when executed through documented provider-disabled helpers. If a prerequisite is unavailable, report the exact blocker; do not claim acceptance from unit tests alone. Long command execution can be polled while keeping progress communication timely. No blanket reruns after sufficient unchanged verification are needed; rerun affected tests after fixes.

## Optional Developer Checks

None. No live-provider, paid, production or external configured-environment checks are needed.

## Exit Criteria

- [x] Missing-capability red test captured before implementation.
- [x] SDK independent build/install and real forbidden direct/transitive graph cases pass their intended assertions.
- [x] Java 21, Boot 4.1.0, root beta.8 pin and unchanged root scope verified.
- [x] New runner/fixture tests, formatter and lint pass.
- [x] Actual local recipe report proves fresh checkout/SDK build identity, readiness, zero model calls, graceful shutdown and scoped cleanup.
- [x] Actual standard and hardened invocations fail clearly with per-run evidence.
- [x] Failure-path reports and terminal captures contain no synthetic secret/private-key/payload sentinels or raw command output.
- [x] Root `clean verify`, full suite pytest, retained real smoke and all-suite mock replay pass.
- [x] Existing business fixtures/reference packs/config/source and pre-existing agreement change preserved; no data reset.
- [x] Every acceptance criterion maps to executable evidence, with actual commands/outcomes persisted in implementation notes.
- [x] Optional checks, if newly discovered, are labeled nonblocking and never represented as already performed.
- [ ] Fresh independent review completes after implementation; planning success alone does not complete this ticket.

## Execution adaptation

The actual lane now invokes root `clean verify` (including package/tests), providing the root regression gate as part of its real build. The initial runtime attempt found Windows Docker Compose plugin discovery needs `PROGRAMFILES`; preserve this nonsecret OS path in the child environment and check Compose version during preflight. The first failed report remains evidence; it is not counted as successful smoke.

The actual internal Docker network did not publish host mappings. Readiness is therefore checked by the existing image curl through `compose exec`, against the same HTTP health endpoint, with a two-second HTTP timeout and ten-second command timeout. Unit tests exercise unready/malformed/UP responses and the readiness deadline at that boundary. `/tmp` must be executable for sqlite-jdbc native extraction; the SDK tmpfs explicitly sets this and tests assert it. These are harness-only operational adaptations, with no production or retained-lane changes.

## Final verification receipt

Implementation-plan final executable results contain exact actual commands,
artifact hashes, root test counts and runtime evidence. Final local report is
suite `evidence/sdk/20261018T101613Z-a272c3d8addc/report.json`; all stages passed,
Framework beta.8, readiness UP, graceful exit 143, model requests zero, scoped
cleanup complete. Root clean verify (inside that run) reports 256 tests, zero
failures/errors and three skips. SDK install and real Maven control/eight banned
graphs pass. Final local pytest reports 142 passes; focused reports 32 passes.
Ruff format/lint and both Git whitespace checks pass. Actual empty recipe runs
fail clearly with retained JSON evidence. Retained runtime smoke passes and
all-suite replay passes all 232 executions (8 equipment and 224 TypeSafe), with
runtime restored. The 190-file baseline hash comparison preserves every original
file except the allowed README addition, including exact pre-existing agreement
bytes. No provider calls, Framework rebuild, data reset, commit or publication
occurred. The initial failed runtime attempt remains recorded rather than being
counted as successful evidence. Independent Step 5 remains the final gate.
