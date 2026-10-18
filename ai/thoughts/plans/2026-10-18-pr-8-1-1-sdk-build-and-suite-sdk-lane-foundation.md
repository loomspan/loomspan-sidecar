# SDK build and external-suite SDK lane implementation plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-1-sdk-build-and-suite-sdk-lane-foundation.md`
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-1-sdk-build-and-suite-sdk-lane-foundation.md`
- Outcome: independent Java SDK skeletons and a separate provider-free packaged-Sidecar startup/readiness/shutdown lane with durable evidence.
- Pipeline: approved Full 5-Step Pipeline; developer answered “proceed”. Steps 2 and 3 share this planning context. No approval remains outstanding.

**Development policy: replace superseded development contracts directly; add no compatibility shims or legacy paths.** Apply [the design lens](../design-lens.md). No development-data reset is needed or authorized.

## Current State

`pom.xml` defines the single Sidecar application, Java 21, Boot 4.1.0 and Framework beta.8; it has no reactor modules. `Dockerfile` packages that executable JAR and uses a direct Java entrypoint, a nonroot user and management readiness on 9091. `ConfigurationModeIntegrationTest.fileModelCredentialsChangeOnlyAtRestartAndPreserveDatabaseAuthority` demonstrates file configuration with a deterministic OpenAI fixture. `JwtSecurityConfiguration.jwtDecoder` accepts a local RSA public key, avoiding issuer discovery.

The external suite at `C:/opendev/code/loomspan-sidecar-test-suite` has existing five-service Compose helpers, Python pytest coverage and frozen business fixtures. Its `config/runtime-source.json` pins beta.9-SNAPSHOT for the embedded host and exported beta.3 Sidecar source. `scripts/start.py`, `scripts/smoke.py` and `scripts/run_suite.py mock --suite all` are its retained regression entrypoints. They must remain separate from the new lane.

At planning time, Sidecar has only the new research artifact; the suite has the pre-existing 72-line `docs/project-agreement.md` SDK direction addition. Preserve that file byte-for-byte during this ticket; do not attribute it to implementation.

## Desired End State

`mvn -f sdks/java/pom.xml clean install` builds core and starter without building Sidecar or Framework. SDK dependency enforcement runs during normal builds and rejects forbidden direct/transitive artifacts. The separate suite runner accepts all three recipe names; `local` runs the foundation smoke, while `standard` and `hardened` produce explicit failed evidence because they have no scenarios yet. Successful smoke proves the actual built revision reaches readiness and terminates gracefully. It makes no SDK/client-behavior claim.

## Scope

### In scope

- SDK parent and two minimal JAR modules, dependency boundary checks and build documentation.
- Separate suite runner, isolated Docker composition, deterministic model fixture, runner tests and safe report.
- Existing-lane regression execution and preservation checks.

### Out of scope

- Client APIs, signing, callbacks, reference app, TLS recipes, public placeholder types, publication, CI, commit/push/release, Framework changes/builds and restored retired infrastructure.

## Active Project Guardrails

- Keep code, tests and process proportional; existing business lanes are not abstractions to generalize for this work.
- Use only supported Framework APIs/configuration; no application or test imports of internals and no Java skills.
- Framework owns execution/lifecycle; the lane observes readiness and shutdown rather than replacing lifecycle behavior.
- Infrastructure must not copy credentials, private keys or payloads into reports/logs. Temporary fixture state is isolated from retained data.
- Preserve installed Framework dependency workflow and beta.8 root pin; source documentation is from matching beta.8 tag.

## Impact and Risk Analysis

Maven inheritance must actually execute dependency bans in both SDK children, including resolved transitive graphs. Test actual failing Maven builds, not merely POM strings. Core must not acquire Boot runtime dependencies; starter may depend on core and optional Boot servlet support.

On Windows, terminating a native subprocess is insufficient proof of graceful JVM shutdown. Use Docker's normal SIGTERM/timeout path and verify container exit state before cleanup. An internal-only Docker network and explicit container environments prevent inherited provider configuration and external provider access. Container-local HTTP readiness requires no host ports and cannot collide with retained ports. Containers get only temporary config/state; no local credential directory or Docker socket is mounted.

Evidence must identify dirty checkouts honestly: record HEAD, dirty state and packaged JAR hash, plus SDK module coordinates/hashes. Do not imply HEAD alone identifies uncommitted tested bytes. Raw process output and exception text can contain sensitive values, so persist fixed diagnostic categories and numeric/status metadata only. Build/runtime output is captured, never copied wholesale into reports or terminal diagnostics.

## Implementation Approach

Use `sdks/java/pom.xml` as an independent parent/aggregator with version `0.1.0-SNAPSHOT` (development only), Java release 21, UTF-8 and pinned compiler/Enforcer plugins. `core/pom.xml` and `spring-boot-starter/pom.xml` define the required artifact IDs. Import Boot 4.1.0 dependency management in the starter, keep its core dependency ordinary, and its Boot autoconfigure/servlet integration dependencies optional. Empty source trees are acceptable; do not create public skeleton APIs merely to have compilation units.

Inherit an always-active Enforcer banned-dependencies rule with transitive search enabled. Ban `ai.loomspan:*` except exact SDK core/starter coordinates; this rejects all current Framework and Sidecar artifacts without a brittle enumerated Framework artifact list. Add core-only Boot dependency exclusion. Verify the parent rule is inherited during ordinary validate/verify, not only a release profile. No root POM changes.

Use `scripts/run_sdk.py` and a small `scripts/sdk_lane/` package in the external suite. Keep orchestration, fixture behavior and report serialization easy to read; do not introduce a general harness framework. A separate `sdk/compose.yaml` owns just Sidecar and a Python standard-library fixture service on an `internal: true` network. Use a unique project per run. The Sidecar image builds with the actual checkout's existing Dockerfile after Maven package; the fixture image needs only standard Python. Explicit `-f`/project arguments are contained in the SDK helper; do not call or change the retained five-service environment helper. This is a separate documented SDK composition, not bare startup of the retained stack.

Generate temporary YAML/public verification material, use file configuration and a minimal model skill pointing only at `http://fixtures:8080/v1`, dummy nonsecret model credentials, observability off and short documented lifecycle timeouts. Generate an ephemeral public key in memory without serializing private key material. Run the database on a fresh writable container tmpfs with the existing nonroot UID. Mount fixture/config code read-only. Readiness is polled over container-local HTTP through the image's existing curl; no ports are published. The fixture serves a fixed OpenAI response and request counts, never forwards requests, and suppresses HTTP access logs/body capture. Startup smoke expects zero model requests; no extra execution is needed for this ticket.

The runner performs `clean verify` on the actual Sidecar root and `clean install` on its SDK reactor, with no Framework build/override or SDK shortcut. Read the Framework version from the packaged `BOOT-INF/lib` entry and assert exact beta.8; read SDK coordinates from Maven metadata/POMs and hash the installed/built JARs. Do not implement separate Framework source/artifact auditing. Explicitly record build attempted/succeeded/failed/not-run statuses, including for empty recipes.

Every valid recipe invocation writes an individual `evidence/sdk/<run-id>/report.json`, initialized before preflight and finalized in `finally`, even after build/startup/readiness/shutdown failure or interruption. Report recipe, scenario, source HEAD/dirty flag, artifact identities, resolved Framework version, each stage's concise result, readiness, shutdown and cleanup. Missing identity after preflight failure is explicitly unavailable, not invented. Use atomic report replacement. Do not overwrite maintained references or unrelated evidence files. Standard/hardened fail before builds with `no scenarios for recipe`; they remain recognized selections. CLI supports `--sidecar-checkout` (default neighboring checkout) and `--output` (a new run directory).

Poll readiness with a bounded deadline and validate `UP`. Stop Sidecar with bounded `docker compose stop`, inspect exit code/OOM/error and require a normal SIGTERM-compatible exit (0 or 143), no force-kill/timeout, and a Spring graceful-shutdown completion marker checked in memory. Never persist raw container logs. If only forced cleanup succeeds, smoke remains failed. Always tear down only this run's project/temp state; preserve original failure when cleanup also fails.

Native process execution was rejected because graceful termination needs additional Windows-specific machinery. Reusing the retained five-service stack was rejected because it embeds the wrong Sidecar/Framework identity and shares business state. A large deployment abstraction is unnecessary for one smoke scenario.

## Phase 1: Independent SDK reactor and executable isolation proof

### Changes

- [x] `sdks/java/pom.xml`, `sdks/java/core/pom.xml`, `sdks/java/spring-boot-starter/pom.xml` — independent coordinates, Java 21, optional Boot 4.1.0 support and inherited dependency bans.
- [x] `sdks/java/tests/test_build_boundary.py` — standard-library unittest driver that creates disposable copies/reactors and synthetic forbidden dependencies; direct/transitive Framework and Sidecar negatives for each SDK child. Synthetic bridge uses another group so transitive inspection is essential. Run real Maven and assert banned-dependency failure names the forbidden coordinate, not another build failure. Keep test artifacts out of the developer repository; no changes to real SDK POMs during negatives.
- [x] `sdks/java/README.md` — build/install/check commands, artifact boundary, development version and intentional empty modules.

### Automated verification

- [x] Sidecar root: `mvn -B -ntp -f sdks/java/pom.xml clean install` — both SDK JARs installed; independent root remains untouched.
- [x] Sidecar root: `C:/opendev/code/loomspan-sidecar-test-suite/.venv/Scripts/python.exe sdks/java/tests/test_build_boundary.py` — normal control builds and all eight forbidden graph cases fail for the expected rule. Temporary fixture reactors may use normal Maven cache resolution; never overwrite real released dependencies.

### Optional developer checks

None.

## Phase 2: Separate deterministic SDK lane and safe evidence

### Changes

- [x] Suite `scripts/run_sdk.py`, `scripts/sdk_lane/__init__.py`, `scripts/sdk_lane/fixture.py` — small runner helpers for recipe validation, explicit build commands, metadata, scoped Compose lifecycle, safe result writing and deterministic fixture.
- [x] Suite `sdk/compose.yaml` — two-service internal network, explicit environment, temporary state, container-local HTTP readiness and bounded graceful stop. Generate a tiny fixture Dockerfile only if image creation needs it; use Python standard library rather than suite service dependencies.
- [x] Suite `tests/test_sdk_runner.py` — recipe handling, build provenance, lifecycle failures, cleanup/report completion, provider isolation, safe diagnostics and fixture protocol tests.
- [x] Suite `docs/sdk-lane.md` and `README.md` — separate invocation/prerequisites/evidence/failure semantics; state startup smoke scope and future recipe status. Preserve all existing docs content and the pre-existing agreement addition.

### Automated verification

- [x] Suite: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q` — simulated failures and deterministic fixture tested without Docker/provider calls.
- [x] Suite: `ruff format scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`; `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`; `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` — format/lint only new harness files.
- [x] Suite: `.venv/Scripts/python.exe scripts/run_sdk.py local` — real current-revision builds, readiness, zero provider traffic and graceful shutdown, report PASS.
- [x] Suite: `.venv/Scripts/python.exe scripts/run_sdk.py standard`; `.venv/Scripts/python.exe scripts/run_sdk.py hardened` — expected nonzero with explicit failed reports; distinguish expected negatives from unexplained failures.

### Optional developer checks

None.

## Phase 3: Regression and preservation verification

### Changes

- [x] Update this plan's checkboxes and append implementation results, actual commands and artifact locations as work completes; no separate implementation report is required unless useful.
- [x] Record maintained fixture/config/reference preservation using before/after hashes and scoped Git diffs. Do not modify existing lane code, pins, fixtures or reference packs to accommodate the SDK lane.

### Automated verification

- [x] Sidecar: `mvn -B -ntp clean verify` — existing application/tests and packaging still pass with beta.8 and no SDK reactor inclusion.
- [x] Suite: `.venv/Scripts/python.exe -m pytest tests -q` — all local suites pass.
- [x] Suite: `.venv/Scripts/python.exe scripts/start.py`; `.venv/Scripts/python.exe scripts/smoke.py`; `.venv/Scripts/python.exe scripts/run_suite.py mock --suite all` — retained embedded and Python/Sidecar lanes still run provider-free. Use documented existing runtime, credentials and installed artifacts; do not rebuild Framework. If startup prerequisites fail, diagnose the actual existing prerequisite and report it; never substitute unit tests for this gate or reset existing data.
- [x] `git diff --check` in each repository; scoped `git diff --exit-code`/hash comparison for retained source/config/fixtures and suite agreement baseline — no unintended tracked changes. New README SDK link is an allowed documentation addition.

### Optional developer checks

None. All ticket acceptance checks are executable locally with installed prerequisites; no paid/live environment check is proposed.

## Test Strategy

Use real Maven negative graphs for build isolation, cheap pytest subprocess/clock/HTTP doubles for failure transitions, standard-library HTTP fixture protocol tests, one real Docker foundation smoke and the retained provider-free regression commands. The testing plan defines cases/exit criteria. Keep detailed future client/security scenarios for their feature tickets.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Independent Java 21 SDK; Boot 4.1.0 starter | Three `sdks/java` POMs | SDK clean install, Maven dependency metadata and installed artifacts |
| Root scope and Framework beta.8 unchanged | Root POM left untouched | Root clean verify, scoped diff, packaged Framework entry |
| Forbidden direct/transitive deps rejected in both modules | Inherited Enforcer rule | Eight actual negative graphs plus clean control |
| Actual checkout/SDK builds; deterministic smoke | SDK runner, isolated composition, file fixture config | Real local recipe report, packaged hashes, readiness UP and normal shutdown |
| Three recipes; empty selection fails | Explicit recipe-to-scenarios table | Unit tests and actual standard/hardened nonzero reports |
| Per-run identity/build/result evidence; no secrets/payloads | Allowlisted report fields, suppressed raw diagnostics | Failure-injection/sentinel tests and actual report inspection |
| Existing lanes and maintained content preserved | No existing lane/config/fixture changes | Full pytest, existing smoke + all mock replay, before/after hashes and diffs |

## Risks and Rollback/Recovery

Docker images may need pulling/building; Maven may download public dependencies. These are build prerequisites, not model-provider access. Record unavailable prerequisites honestly and retain failed evidence. New containers/network/tmpfs are scoped by unique project and removed in `finally`; failed cleanup is reported without masking a prior error. No database reset, retained-volume deletion or global Docker cleanup is allowed. Rollback is removal of the new SDK/lane files and SDK docs additions only; installed SDK snapshots can simply be replaced by a later development install. Do not remove unrelated local Maven artifacts.

## References

- Ticket/research paths above; `ai/thoughts/design-lens.md`.
- `pom.xml`, `Dockerfile`, `src/main/resources/application.yml`, `ConfigurationModeIntegrationTest`, `ExecutionShutdownIntegrationTest`.
- Suite `AGENTS.md`, `docs/project-agreement.md`, `docs/runtime.md`, `scripts/start.py`, `scripts/run_suite.py`, `tests/test_runner.py`.
- Canonical SDK Phase 1, API reference and acceptance matrix linked in the ticket.

## Implementation evidence (2026-10-18, Step 4 complete)

- Baseline: suite `.runtime/sdk-preservation-before.json` records SHA-256 for all
  190 initially tracked files, including the pre-existing agreement bytes. The
  first comparison permits only the documented README link addition; all other
  original bytes match. No framework source, existing lane, business fixture or
  reference pack was edited and no data reset occurred.
- Missing-capability red test: `.venv/Scripts/python.exe -m pytest
  tests/test_sdk_runner.py -q` failed with `ModuleNotFoundError: run_sdk` before
  runner implementation. The same named test now verifies actual-checkout build
  commands, artifact hashes, dirty revision, readiness and shutdown evidence.
- PASS: `mvn -B -ntp -f sdks/java/pom.xml clean install`; independent parent,
  core and starter built/installed. `dependency:tree
  '-Dincludes=org.springframework.boot:*,ai.loomspan:*'` confirms no core runtime
  dependencies and starter Boot 4.1.0 plus SDK core.
- PASS: `C:/opendev/code/loomspan-sidecar-test-suite/.venv/Scripts/python.exe
  sdks/java/tests/test_build_boundary.py`; clean control plus all eight negative
  graphs rejected the intended artifact in the intended SDK module (42.114s on
  the final run). Synthetic artifacts were verified, never installed.
- PASS: focused SDK pytest (32 tests); full suite pytest (142 tests, 17.14s).
  Ruff format/check and lint pass for all new harness code. No retained code was
  reformatted.
- Expected negative actual CLI runs: `scripts/run_sdk.py standard` and
  `scripts/run_sdk.py hardened` each exited 1 with `no-scenarios-for-recipe`.
  Reports are suite `evidence/sdk/20261018T100546Z-e6a355565a48/report.json` and
  `evidence/sdk/20261018T100547Z-c4c5dc2f2b2c/report.json`; parsed assertions
  confirmed the failed status/category rather than trusting exit code alone.
- PASS: retained `.venv/Scripts/python.exe scripts/start.py`; five services ready,
  providers disabled. PASS: retained `.venv/Scripts/python.exe scripts/smoke.py`;
  both integrations, deterministic quotes, identity/permission checks, prior rows
  preserved and zero model calls; evidence `.runtime/smoke.json`.
- Routine adaptations: fixture uses the standard Python 3.13 image directly with
  one read-only script mount, so no fixture Dockerfile is needed. Child commands
  use an explicit OS/tool environment allowlist, excluding provider variables and
  Java/Maven option injection. Each SDK image has a unique tag and remains as a
  local build artifact after run-scoped container/network/temp-state cleanup;
  `docs/sdk-lane.md` states this explicitly. These add no API or supported recipe.
- Actual local SDK smoke, root `clean verify` and retained all-suite replay remain
  in progress/unrun at this interim update; completion requires their results.

- Actual local run `20261018T100248Z-d83c83ac15d3` retained FAILED evidence: build, SDK install, metadata and image build passed; startup/cleanup failed before creating containers because Docker could not discover Compose under the projected Windows environment. `PROGRAMFILES` is required for Docker plugin discovery and is now allowlisted, and preflight checks Compose version. A direct Compose configuration check now passes. No retained containers/data were affected.
- Routine verification adaptation: the runner now uses root `clean verify` rather than `clean package`. Verify includes package and the same tests, and supplies the required root regression gate within the actual lane invocation instead of duplicating the expensive unchanged application suite afterward.

- Linux runtime prerequisite: sqlite-jdbc extracts its native library under `/tmp`; Docker tmpfs defaults prevented loading it. The SDK-only tmpfs now explicitly uses `/tmp:exec,mode=1777`, and a unit assertion protects that prerequisite. Sidecar then reached Docker health `UP`.
- This daemon does not publish host port mappings on the internal-only network (`NetworkSettings.Ports` was empty). Readiness now polls the same actual HTTP endpoint with bounded `compose exec sidecar curl` inside the container. Host publication was unnecessary for startup smoke and has been removed; this preserves provider isolation and the exact `UP` requirement without changing ticket scope.

- Windows browser/platform helpers also need `SYSTEMDRIVE` and standard ProgramData/ProgramFiles paths. These nonsecret OS paths are now allowlisted alongside tool/user paths, while provider variables and Java/Maven injected options remain excluded. The initial limited environment generated a literal `%SystemDrive%` Windows cache directory inside the checkout; it is task-created cache only and was preserved outside the checkout after the active build finished.
- Runtime-only diagnostic after these harness fixes passed all six actual phases: image build, startup, HTTP readiness UP, graceful shutdown (exit 143, no OOM, Spring completion marker), zero model requests and cleanup. This diagnoses the smoke path but does not replace the in-progress full fresh-build CLI evidence gate.


### Final executable results

- PASS: suite `.venv/Scripts/python.exe scripts/run_sdk.py local`, final run
  `20261018T101613Z-a272c3d8addc`. Its source is
  `b747fd9e9f57e5ae1cf8972d79801df8c9c6fa38`, honestly marked dirty.
  `evidence/sdk/20261018T101613Z-a272c3d8addc/report.json` was parsed and every
  stage asserted passed: source/preflight, root verification, SDK install,
  artifact identity, image, startup, readiness, shutdown, fixture and cleanup.
- That invocation actually executed `mvn -B -ntp -f
  C:/opendev/code/loomspan-sidecar/pom.xml clean verify`: 61 Surefire suites,
  256 tests reported, zero failures/errors, three skips. Root POM, source,
  Dockerfile and build scope remain unchanged. It then executed
  `mvn -B -ntp -f C:/opendev/code/loomspan-sidecar/sdks/java/pom.xml clean install`.
  No Framework source build or version override occurred.
- The final built Sidecar JAR SHA-256 is
  `5b5d10b2d27d10016d7924c00e7495a28d27604a4a41c06f7d169a9a637d553d`;
  this matches the packaged bytes after verification. The report's resolved
  Framework version is `1.0.0-beta.8`. SDK core/starter coordinates are
  `ai.loomspan:loomspan-sdk-core:0.1.0-SNAPSHOT` and
  `ai.loomspan:loomspan-sdk-spring-boot-starter:0.1.0-SNAPSHOT`, with SHA-256
  `42263b9a41e3f3a77b2a3b7b7ee720d7c540998bafa9bae30bcea44ba0dcdc69` and
  `0e2746dcd5b23f357d2afdb70debc5182955e05156c5f47f1cce967cfd09ac8c`.
- Actual lifecycle assertions: readiness `UP`, graceful shutdown marker present
  in memory, exit 143, no OOM, fixture `modelRequests=0`, successful scoped
  cleanup. Docker inspection afterward found no `sdk-` containers or networks.
  Unique local image tags remain as documented build artifacts.
- Expected negative final CLI runs: `scripts/run_sdk.py standard` and
  `scripts/run_sdk.py hardened`, exit 1 with explicit failed/not-run evidence
  under `evidence/sdk/20261018T101739Z-7bc8e74b6c70/report.json` and
  `evidence/sdk/20261018T101740Z-d4cd92b24779/report.json`. JSON assertions
  verify empty selection and unattempted builds/runtime.
- PASS: final full suite `.venv/Scripts/python.exe -m pytest tests -q`:
  142 passed in 18.91s. Focused tests after the final OS-environment correction:
  32 passed in 2.18s. Final Ruff formatting and lint checks pass.
- PASS: `.venv/Scripts/python.exe scripts/run_suite.py mock --suite all`, exit 0.
  Parsed retained reports show eight equipment cases `REPLAY_PASS` and 224
  TypeSafe executions `PROTOCOL_PASS` (232 executions total). All 240 equipment
  checks and 3,242 TypeSafe checks are true, with
  `runtimeRestored=true` for both suites. Evidence remains under existing
  `evidence/latest/equipment-service/report.json` and
  `evidence/latest/typesafe-ai/report.json`. Existing startup/smoke also passed,
  with provider access disabled, zero model calls and prior rows preserved.
- Final preservation comparison: all 190 original tracked suite files retain
  their initial bytes except the permitted README link; the pre-existing
  agreement addition is byte-identical. Summary is
  `.runtime/sdk-preservation-after.json`, backed by the initial hash map.
  `git diff --check` passes in both repositories; `git diff --exit-code --
  pom.xml Dockerfile .dockerignore src` confirms unchanged application files.
- The limited first-run Windows environment generated four OS cache files under
  literal `%SystemDrive%` in the checkout. Automatic approval review rejected
  recursive deletion; the safer operation preserved that task-created cache at
  `C:/Users/mgiacomi/AppData/Local/Temp/loomspan-sdk-generated-windows-cache-20261018-1024`.
  No cache or other unintended file remains in the checkout, and the corrected
  environment retains normal OS paths. No user data was deleted.
- Acceptance mapping is complete: SDK build/Java/Boot and boundary proof are
  covered by Phase 1 commands; real checkout/Framework/SDK identities and
  lifecycle are covered by the final actual report; recipe/evidence failures
  and secrecy by focused tests and actual empty runs; preserved lanes/content
  by full tests, real retained smoke/replay and hash/diff checks. No blocking or
  optional manual verification remains. Fresh Step 5 review is still required
  before pipeline completion.
