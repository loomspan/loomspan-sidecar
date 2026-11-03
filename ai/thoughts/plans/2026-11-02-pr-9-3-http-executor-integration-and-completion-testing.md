# HTTP executor integration and completion testing plan

**Development policy: replace superseded contracts directly without compatibility
shims, legacy adapters or parallel APIs.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt).

## Change Summary

Pipeline Steps 2/3, profile `full`, approved 2026-11-02. This plan accompanies
`2026-11-02-pr-9-3-http-executor-integration-and-completion.md` in this directory.
The controlling ticket and research have the same stem under `tickets`/`research`.
The change replaces the neighboring suite's HTTP client declarations/generators,
current runtime build assumptions and stale release fixtures. Existing Sidecar
HTTP/publication/lifecycle behavior is verified rather than reimplemented.
`suite:` paths mean `C:/opendev/code/loomspan-sidecar-test-suite`.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Client generation | Checked-in leaves pass but regenerated fixtures retain REST | Regeneration into temporary root; nine executor leaves and no route artifacts |
| Request body | Lookup wrapper or stripped approval reaches receiver | All eight actual flat requests; nested approval parsing/rejection; SDK bodyless counters |
| Identity | Caller token/role/expiry or cross-user state changes | Existing HTTP isolation tests + Local callback role/owner/expiry and exact-token checks |
| Transport | Replay, redirect, cookie retention, lost response or decoding corrupts outcome | Existing HTTP attempt/content/size tests + independent SDK response-loss effect counters |
| Configuration/lifetime | Early HTTP preflight or premature client closure | File/DB invalid fixture zero-network assertions, retained generation/restoration, real shutdown |
| HTTPS/mTLS | Generator reads obsolete examples or weakens peer verification | Standard/Hardened actual recipes and their negative/no-bypass assertions |
| Build/release | Green tests require old Framework or weaken snapshot guard | Beta.9 snapshot checks, disposable release fixtures, full required local checks |
| Maintained state | Replay overwrites accepted references or operational stores | Fresh worktree/project/ports/stores, reference SHA-256 before/after, explicit new outputs |

## Existing Coverage and Environment Constraints

Sidecar `src/test/java/ai/loomspan/sidecar/http` already contains mapping,
configuration, executor, TLS, public invocation/tool feedback and lifecycle tests.
`configuration/HttpConfigurationIntegrationTest` proves file/DB startup,
management publication/import/rollback/restoration accept HTTP-invalid but
Framework-valid settings without requests; other fixtures reject Framework-invalid
declarations. `HttpConfigurationShutdownIntegrationTest` runs full Sidecar with
queued and admitted work. `architecture/SupportedLoomspanApiArchitectureTest`
scans both production and test code. Run these as Sidecar evidence; Framework
tests are not a substitute.

Java 21, Maven, Docker/Compose and suite `.venv` Python are present per research.
Playwright Chromium/image cache availability is not yet proven. Install missing
test browsers through the existing Playwright CLI if needed. Container image pulls
and Maven dependency downloads are permitted; provider requests are not. Existing
SDK runner sanitizes child environments and creates temporary projects/tmpfs stores.
Preserve those controls. Never use bare business `docker compose up`.

## Failing Test First

- Name: `ReleasePreparationTest.releasePreparationRejectsSnapshotsAndProducesExpectedArtifactPlan`.
- Type/location: existing JUnit test,
  `src/test/java/ai/loomspan/sidecar/release/ReleasePreparationTest.java`.
- Arrange/Act/Assert: run against current beta.9-SNAPSHOT POM; its obsolete beta.8
  successful release-plan invocation returns nonzero. Capture this known blocker
  before reconciling the release line, then prove the intended prospective beta.9
  plan and invalid versions/tags separately.
- Command: `./mvnw.cmd -B -ntp '-Dtest=ReleasePreparationTest' test`.
- Expected pre-fix failure: successful-plan assertion fails because the script
  rejects the current POM's Framework line, not because the executor malfunctioned.
- Additional first acceptance test: a temporary equipment generator fixture asserts
  all nine leaves contain `executor: http`, per-leaf POST/path/`body: {source: input}`, and
  no `rest`/route artifacts. It fails against the old generator before conversion.
- The existing Python successful-tag fixture also fails with a snapshot Framework;
  record its failure, fix only its disposable released-dependency setup, and keep
  snapshot-rejection coverage. Test commits/tags occur only inside its temp repo.

## Tests to Add or Update

### 1. Generated equipment declarations preserve the business request contract

- Type/location: pytest in `suite:tests/test_suites.py`, with relevant existing
  equipment tests in `tests/test_offer_context.py`/`tests/test_replay.py`.
- Generate into a temporary root by redirecting the generator's output root/save
  boundary; assert the eight lookup schemas and creation schema are unchanged.
- Assert whole-input bodies, correct paths/roles/targets, model skill bytes unchanged,
  and no generated legacy tree/routes. Assert duplicate names across model/executor
  trees fail. Do not recreate invocation-time HTTP validation in the assembler.
- Check the checked-in files match generator output without changing accepted
  model prompts or maintained response/source assets.

### 2. Runtime build and distribution use the current installed dependency

- Type/location: new `suite:tests/test_runtime_build.py` using temp roots and
  synthetic executable JARs; no Maven invocation or neighboring Framework Git.
- Exercise `record_build` artifact selection/staging, actual beta.9 snapshot
  coordinate recognition, missing/multiple/incorrect artifact rejection,
  per-host hashes and configuration records.
- Exercise `install_runtime.verify_inputs` with the manifest's actual Framework
  coordinate; changed JAR/source/configuration or missing files fail. No hard-coded
  beta.8 embedded filename, Framework revision requirement or source-target hash
  comparison remains. Retain useful full-package integrity checks.
- Verify credential generation preserves existing credentials and supplied install
  still refuses existing `.runtime`; never read retained secret values into logs.

### 3. Equipment composed receiver proof

- Type/location: extend `suite:scripts/smoke.py` with focused test support in
  `tests/test_offer_context.py` or an adjacent test module.
- In the isolated real five-service composition invoke all eight deterministic
  lookup skills with flat `{caseId, assetId}`, authenticate through maintained
  login, and assert successful receiver/business output and zero model requests.
- Submit creation with an authenticated authorized fixture user and a deliberately
  invalid immutable assessment/quote within the nested `{approval: ...}` body.
  Assert the expected business rejection after schema/envelope parsing, not a
  missing body or schema wrapper error, and no created request row. Preserve the
  separate unauthenticated/Maya-denied tests and prior-row preservation checks.
- If existing maintained deterministic business fixtures already supply stronger
  equivalent proof, reuse them; do not add a second business workflow framework.

The public app remains authenticated by Keycloak; its Sidecar transport must use
the supported custom-app execution JWT rather than forward an ordinary access
token. Add `suite:tests/test_execution_identity.py` with temporary RSA key material:
verified subject and allowlisted exact roles are retained, unverified/model inputs
cannot grant identity, execution type/purpose/audience/key ID and issuer are exact,
expiry is bounded by source identity expiry, and callbacks reject wrong trust,
ordinary login JWTs, wrong audience/purpose and expired credentials. Assert only
public JWKS reaches Sidecar; the original minted execution token reaches the
receiver unchanged and callback role/record authorization still applies. Generated
keys belong only to the new isolated workspace; no existing key is rotated/reset.

### 4. SDK generated configuration and actual callbacks

- Type/location: `suite:tests/test_sdk_runner.py`, `tests/test_sdk_remote.py`.
- Assert `run_sdk.write_config`, shutdown, Local, Standard, Hardened and outbound
  generation use operation-local config and target maps; legacy route files/keys
  absent. Standard/Hardened overlay the shipped execution target configuration.
- Assert owned callbacks omit `body`, exact role names remain, required input is
  retained for the expiry scenario, and no private token mount reaches Sidecar.
- Preserve real Local identity/owner/expiry/restart/trust/queue and shutdown tests.
- Actual callback receiver asserts original caller token and no request entity;
  driver checks qualified catalog names and exact terminal 503 text.
- Lost submission/callback response requires exactly one independent attempt/effect
  and no later activity; never loosen counters to tolerate replay.

### 5. Existing Sidecar protocol/configuration/lifetime regression

- Type/location: existing JUnit tests under `http`, `configuration`, `execution`,
  `management`, `architecture`.
- Run public direct and standard tool-call success/recoverable/terminal cases;
  only listed permitted 4xx recover. Preserve exact multilingual/whitespace/error
  content, bounded decompressed success/error reads, malformed decoding failures,
  expiry/fixed credential capture/concurrent caller/cookie isolation and no retry.
- File/DB HTTP-invalid fixtures produce zero outbound requests through readiness,
  validation/publication/restore; invalid invocation fails before dispatch.
- Retained admission uses captured settings/credentials/durable identity; new
  admissions/restart use new settings. Shutdown leaves clients alive until admitted
  completion or the single Framework deadline, discarding queued work.
- Add a test only if client integration exposes an uncovered reachable failure.
  No internal Framework type, Java skill or mocked Framework lifecycle substitute.

## Safe Verification Commands

### Authorized namespace contract verification

The developer authorized a separately preserved namespace-only contract update.
`suite:fixtures/reference-namespaced/namespace-review.json` lists all 316 explicit
identity changes and source/current hashes. The original pack remains byte-identical.
`suite:tests/test_replay.py::test_namespace_contract_changes_only_reviewed_framework_identity_positions`
independently compares the complete case structures, embedded reply characters,
business values and ledger. No runtime adapter or relaxed semantic matcher is used.
Owned trace selectors and canned SDK/TypeSafe planner fixtures use exact canonical
names. A fresh grouped mock must pass all equipment and TypeSafe cases on both hosts.

Commands are executed from the named repository; use PowerShell and the maintained
Python environment. All report directories below must be new. Capture precise
expanded commands/paths and actual results in the verification artifact.

### Sidecar

```powershell
./mvnw.cmd -B -ntp '-Dtest=ReleasePreparationTest' test
python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v
./mvnw.cmd -B -ntp '-Dtest=Http*Test,SupportedLoomspanApiArchitectureTest' test
./mvnw.cmd -B -ntp verify
python -m unittest discover -s scripts -p 'test_*.py' -v
python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v
git diff --check
```

If Chromium is missing, use the repository's existing setup command before full
verification; this installs a local browser, without invoking product providers:

```powershell
./mvnw.cmd -B -ntp test-compile org.codehaus.mojo:exec-maven-plugin:3.6.2:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.classpathScope=test' '-Dexec.args=install chromium'
```

### Suite source and isolated SDK lanes

```powershell
.venv/Scripts/python.exe -m pytest tests/test_suites.py tests/test_offer_context.py tests/test_replay.py tests/test_runtime_build.py -q
.venv/Scripts/python.exe -m pytest tests/test_execution_identity.py -q
.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q
.venv/Scripts/python.exe -m pytest -q
mvn -B -ntp -f apps/java/pom.xml verify
git diff --check
```

Use unique outputs for each actual maintained recipe:

```powershell
$runSuffix = [guid]::NewGuid().ToString('N')
.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar --output "evidence/sdk/pr9-3-local-$runSuffix"
.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar --output "evidence/sdk/pr9-3-standard-$runSuffix"
.venv/Scripts/python.exe scripts/run_sdk.py hardened --sidecar-checkout C:/opendev/code/loomspan-sidecar --output "evidence/sdk/pr9-3-hardened-$runSuffix"
```

Each existing command owns its mandatory root/SDK/consumer builds and bounded
scenario inventory. Do not add skip-build/skip-test flags merely to accelerate
acceptance. Failures require diagnosis; do not disable a maintained scenario.

### Fresh business workspace

From the maintained suite, choose a new sibling path and detached worktree:

```powershell
$validationSuffix = [guid]::NewGuid().ToString('N')
$validationRoot = Join-Path C:/opendev/code "loomspan-suite-pr9-3-$validationSuffix"
$suitePython = 'C:/opendev/code/loomspan-sidecar-test-suite/.venv/Scripts/python.exe'
git worktree add --detach $validationRoot HEAD
```

Copy only current ticket source modifications/untracked source additions from the
maintained suite into that worktree, including removals of superseded tracked files.
Do not copy `.runtime`, `.build`, `.venv`, old outputs or credentials. Preserve the
reference pack byte-for-byte, and record hashes of every maintained reference asset
before and after. This is source synchronization for the isolated test, not a commit.
Choose an unused nonzero port offset (10000 if available), unique project name,
and verify no existing `.runtime` before these commands in the fresh workspace:

```powershell
& $suitePython scripts/configure_isolated.py --project "pr9-3-$validationSuffix" --port-offset 10000
& $suitePython scripts/prepare.py
mvn -B -ntp -f apps/java/pom.xml clean package
& $suitePython scripts/record_build.py --sidecar-checkout C:/opendev/code/loomspan-sidecar
& $suitePython scripts/start.py --build
& $suitePython scripts/smoke.py
& $suitePython scripts/run_suite.py mock --suite all --output "evidence/pr9-3-$validationSuffix"
```

The current Sidecar JAR must already be built successfully against the installed
snapshot before recording. No Framework build command runs. The fresh suite uses
its own new named volume/project and fresh synthetic SQLite stores. Both Java and
Python/Sidecar replay paths are required. TypeSafe `mock --suite all` remains
provider-free and guards shared harness regressions; no new TypeSafe feature work.

After verification use the repository helper in that fresh workspace only:

```powershell
& $suitePython -c "import sys; sys.path.insert(0, 'scripts'); import environment, subprocess; subprocess.run(environment.compose() + ['down'], check=True)"
```

Do not pass `--volumes`, remove retained data or clean unrelated Docker resources.
Keep the isolated evidence/worktree until its paths/results are recorded. No
automatic worktree/database cleanup is necessary to prove acceptance.

## Optional Developer Checks

Only configured external nonproduction deployment observations, if desired after
local completion. No external environment is contacted here. Hosted CI and final
published-dependency verification are explicitly deferred release gates: Framework
publication, released Sidecar pin, repeated final build/tests/final commit, CI, then
Sidecar beta.4 release. They cannot be reported as passed from snapshot evidence.

## Exit Criteria

- [x] Known release red tests fail for the intended reason before fixes; generator
  acceptance also fails against frozen original source (retrospective red).
- [x] All new/updated tests and full required Sidecar Maven/Python checks pass.
- [x] Suite pytest and embedded Java build pass; Local/Standard/Hardened maintained
  SDK lanes pass with complete scenario results and cleanup receipts.
- [x] All eight lookup bodies and nested creation envelope are observed through
  composed calls; SDK caller-token/bodyless POST evidence passes.
- [x] Accepted equipment business outputs pass both hosts using the approved
  separately preserved namespace contract; shared deterministic
  suite lanes pass. Maintained reference outputs and model contracts are unmodified.
- [x] HTTP/domain-invalid startup/publication/restoration and Framework-invalid
  rejection are separately proved; zero-network and lifecycle assertions pass.
- [x] Terminal/recoverable fidelity, authentication, no replay/redirect/cookie state,
  response bounds, captured ownership/settings and normal TLS/Caddy boundaries
  map to actual passing Sidecar/client tests.
- [x] Every 9.1/9.2 deferred check has a concrete reconciled result, not an inherited
  pass claim. No failing tests/scenarios are disabled and no compatibility path added.
- [x] Active-source/guidance searches show no dependencies on removed REST contracts;
  ArchUnit passes for public-only Framework usage in application/test code.
- [x] Provider access remained disabled; no existing credentials/data/evidence
  were overwritten, no paid calls/live commitments/commits/push/releases occurred.
- [x] Verification artifact records exact commands/results/limits, preserved pack
  integrity, isolation identity, any Framework gaps and pending release gates.
- [ ] A fresh independent Step 5 review returns clean with sufficient verification.
  This item is Step 5-owned; Step 4 does not claim independent review.

A material replay-contract mismatch or missing supported Framework capability is a
developer escalation. An unavailable required environment check is a blocker with
its exact cause, never an optional pass. Ordinary client-adapter bugs stay in scope.

Completed per-file selections above are also covered by the final full 268-case
pytest run; exact actually invoked commands/results are in the verification artifact.
