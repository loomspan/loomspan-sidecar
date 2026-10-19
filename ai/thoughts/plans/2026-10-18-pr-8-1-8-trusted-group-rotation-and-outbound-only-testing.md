# Trusted group, rotation and outbound-only Testing Plan

## Change Summary

Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-8-trusted-group-rotation-and-outbound-only.md`.
Research and implementation plan: the matching ticket-stem files under `ai/thoughts/research/` and `ai/thoughts/plans/`.
Execution is the approved Full 5-Step Pipeline, Steps 2+3 in one context. No test/production code was edited or verification run during these planning steps.

**Development policy: replace superseded development contracts directly; add no compatibility shims, legacy adapters, parallel APIs or migration machinery.** Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). No data reset is required.

`suite/` paths mean `C:/opendev/code/loomspan-sidecar-test-suite/`. New evidence belongs to that repository's SDK lane, against this Sidecar's beta.8 pin; business beta.9 lanes and maintained reference assets remain untouched.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Group identity | Two services accidentally prove only same-app behavior | Two JVMs/HTTPS ports, B submission with A callback, exact issuer/subject/roles and opaque credential equality |
| Authorization | Shared signing bypasses method/record checks | Literal role denial, Bob record denial, zero unauthorized effects, Bob result 404 |
| Trust adoption | Shared file or signer export makes both receivers appear independent | Distinct trust/overlap/export paths; staged file-only updates; opposite receiver's unchanged behavior |
| Private signing lifecycle | Hidden hot reload or old startup own key prevents revocation | Initial/candidate probe slots; both actual app restarts; fresh replacement kid; old acceptance until retirement then unexpired old 401 |
| Timing | Token expiry or arbitrary sleep mistaken for trust removal | Five-second publication-to-observation deadline; explicit old expiry check; accepted replacement credential in parallel |
| Framework lifetime | Work queued or waiting on callback rather than admitted | Old-key blocker reaches actual model barrier/RUNNING before withdrawal; release and exact completed result afterward |
| Outbound-only | Existing controller/listener remains and model-only success hides it | Non-web process, no REST skills/routes/targets, actual refused app ports; Hardened absent callback ingress/egress plus successful mTLS invocation |
| Reporting | Canned expected observations conceal missing assertions or leak credentials | Every result backed by live assertions; strict malformed-observation tests; secret sentinel failures; sanitized formal reports |
| Cleanup | Partial startup/restart leaves resources | Failure injection at each new stage; unique project helpers/volumes always removed; independent exact-label resource absence |

## Existing Coverage and Environment Constraints

- Root `ConfigurationModeIntegrationTest::processJwksIsTheOnlyTrustInBothConfigurationModes` covers both modes; `CurrentFileJwtTrustTest` covers complete-file validation and reload; `SupportedLoomspanApiArchitectureTest` forbids Framework internal/autoconfiguration dependencies. SDK core and callback trust tests cover issuance, own-key merge, validation and reload. These run in mandatory root/SDK builds; no duplicate recipe mode matrix is needed.
- Suite `tests/test_sdk_remote.py` uses pytest/temp paths, generated local certificates, fake runtime calls and real local TLS listeners for cheap harness checks. It already checks shipped fragments, export/private separation, exact schema and cleanup failure. `tests/test_sdk_runner.py` owns Local sequencing, fixture memory, barriers, reporting and cleanup.
- Provider-free Docker evidence uses Java 21 through `JAVA_HOME`, Maven/Docker Compose on PATH, `.venv` Python with cryptography/httpx/PyYAML/pytest and Ruff. Root tests require the existing Chromium prerequisite. The runner filters provider/Java option environment variables and uses internal networks/temporary resources. Dependency downloads/image pulls are allowed; external model services are not.
- All official `scripts/run_sdk.py` invocations rebuild root `clean verify`, SDK `clean install`, plain Java/reference consumers and the actual image. Do not introduce a cached-build bypass. A complete Local run historically takes roughly nine minutes, Standard thirteen and Hardened fifteen before these additions; these are historical costs, not deadlines.
- Use bounded local fixture barriers and monotonic checks. One-second loader polling is the public interval; five seconds is a test observation allowance per atomic mutation, not an exact public cutoff. Unknown-kid reads can accelerate adoption and must not be misreported as proof of scheduler phase.
- The root/suite working trees were clean before research; only research and plans existed at planning handoff. No credential, production operation, paid call, commit or release is authorized.

## Failing Test First

- Name: `test_standard_group_rotation_has_two_apps_and_independent_trust_files`.
- Type: pytest composition regression.
- Location: `suite/tests/test_sdk_remote.py`.
- Arrange/Act/Assert: generate material/configuration for Standard `trusted-group-rotation`, build composition, require app A plus a second independently launched JVM on 8445 sharing the app namespace; same signing input/issuer/audience/role settings; distinct per-app exports, Sidecar authoritative JWKS and receiver overlap; public-only Sidecar mounts and no external ports. Require the variant in `remote.VARIANTS['standard']` and absent from Hardened.
- Expected preimplementation failure: current composition has one app and exports one public file directly to Sidecar; no second service, overlap or new variant. Record the failure for that missing capability before implementing. Avoid making a missing import or renamed helper the sole red assertion.

## Tests to Add or Update

### 1. `test_standard_group_rotation_has_two_apps_and_independent_trust_files`

- Type: pytest configuration/composition test at existing harness boundary.
- Location: `suite/tests/test_sdk_remote.py`.
- Proves: topology above, shared signing/directory semantics, verified HTTPS remains active and signer exports cannot overwrite operator trust/overlap.
- Inputs/fixture: generated disposable old/replacement signing material and current shipped Standard fragments.
- Doubles/boundary: no Docker; inspect generated effective YAML, mounts and service commands.
- Edge cases: Sidecar cannot receive private mount; unique exports; no external host ports; both apps select replacement material only in restart configuration; reject accidental same path for export/trust/overlap.

### 2. `test_group_rotation_orders_independent_updates_restarts_and_retirement`

- Type: pytest orchestration test, not acceptance evidence for JWT validation.
- Location: `suite/tests/test_sdk_remote.py`.
- Proves: add Sidecar then callback overlap, pre-switch candidate probes, restart both apps, old overlap checks, old-key admission barrier, remove Sidecar then callback, terminal completion and cleanup ordering. No database/management trust operation occurs.
- Inputs/fixture: fake command/probe functions recording sanitized operations and a fake monotonic clock.
- Doubles/boundary: command and receiver observations faked only to verify runner sequencing/deadlines.
- Edge cases: opposite receiver still old at first add/removal; timeout at five seconds fails without silent retry of submission; expired old credential fails oracle; failed restart cannot proceed to retirement; barrier always released; app session recreated after restart.

### 3. `test_rotation_credentials_stay_in_fixed_memory_slots`

- Type: pytest real local fixture HTTP test using existing fixture conventions.
- Location: `suite/tests/test_sdk_runner.py` (or `test_sdk_remote.py` when testing new remote probe helpers).
- Proves: separately saved old/candidate/current opaque tokens, public kid-only output, fixed discovery/callback/submission paths, comparison equality and correct slot preservation when current capture changes.
- Inputs/fixture: nonsecret sentinel credential strings and a local fixed mock receiver; fixture events/counters.
- Doubles/boundary: receiver returns fixed status; no production trust acceptance is claimed.
- Edge cases: unknown slot/path rejected, arbitrary destination not accepted, old slot survives app restart simulation, credentials cleared on fixture shutdown, no token/Authorization/private content in returned JSON, logged output or raised failure categories.

### 4. `test_new_scenarios_reject_missing_false_extra_and_wrong_typed_observations`

- Type: pytest strict result-contract regression, extending existing parametrization.
- Location: `suite/tests/test_sdk_remote.py` and `suite/tests/test_sdk_runner.py` for Local schema.
- Proves: group/rotation and all outbound-only observations are mandatory and exactly typed. Boolean `1`, false, missing evidence and extra fields cannot pass.
- Inputs/fixture: each expected contract mutated one field at a time.
- Doubles/boundary: command result only; require driver assertions are separately exercised.
- Edge cases: missing restart/independent update/expiry/admission proof; no reporting `passed` solely from an expected-schema factory.

### 5. `test_outbound_only_omits_callbacks_and_preserves_recipe_invocation`

- Type: pytest composition/configuration regression, parameterized Local/Standard/Hardened.
- Location: `suite/tests/test_sdk_runner.py` for Local and `suite/tests/test_sdk_remote.py` for remotes.
- Proves: non-web app startup, only model skills, genuinely empty routes/targets or default empty resource, no callback service/controller listener; matching signer mode and actual recipe client transport retained.
- Inputs/fixture: existing shipped quickstart/remote fragments plus generated temporary config.
- Doubles/boundary: Docker execution deferred to composed runs.
- Edge cases: Local app stays in Sidecar namespace with colocated keys; Standard client uses verified TLS bundle; Hardened keeps app loopback outbound :9080, group client identity and Sidecar :8443 mTLS ingress but omits app :8443/:8444 and Sidecar callback :9080. No hidden REST manifest or synthetic callback effect route remains.

### 6. `test_new_scenario_failure_always_cleans_owned_resources`

- Type: pytest fault-injection regression extending cleanup tests.
- Location: `suite/tests/test_sdk_remote.py`, Local equivalent in `suite/tests/test_sdk_runner.py`.
- Proves: failures during second-app startup, material publication, app restart, trust timeout, outbound-only application exit and cleanup cannot produce a passed report, and cleanup attempts every newly owned resource.
- Inputs/fixture: fixed `LaneFailure` and interruption injections; unique project identity.
- Doubles/boundary: subprocess calls; inspect called argv only for fixed nonsecret inputs.
- Edge cases: publication failure, partial helper startup, volume removal after Compose-down failure, preserved primary failure, no global prune/unrelated resource operation.

### 7. `standard-trusted-group-rotation`

- Type: real provider-free composed acceptance scenario in `suite/scripts/sdk_lane/rotation_probe.py`, wired by `remote.Runtime` and `run_sdk.run_remote`.
- Location: `suite/scripts/sdk_lane/{remote,remote_contract,rotation_probe,fixture}.py`; public SDK fixture helper beside `ExecutionController.java`.
- Proves: every step of the implementation plan's ordered rotation oracle. Authenticate B/A through real form login. B's SDK submit produces an A callback with exactly `loomspan-app`/Alice/`ROLE_ORDER_EDITOR`, expected callback audience and same captured bearer value. Literal method/role failure, Bob callback ownership denial and Bob result 404 preserve user/record authorization.
- Inputs/fixture: shared old provided store, replacement store; one issuer and callback audience; Alice/Bob/literal directory; distinct mutable public trust sets; fixed old/candidate/current memory slots. Candidate credential generated once through public SDK APIs in the app signing boundary before switching the two normal app signers.
- Doubles/boundary: only model responses are deterministic; actual Sidecar/SDK validators, transport and Framework execution run.
- Edge cases: before add new rejects at both; after Sidecar-only add candidate accepted there/401 at unchanged receiver; after receiver add both accept; both actual app restarts change fresh token kid; old tokens work throughout overlap; old-key model is RUNNING before retirement; Sidecar-only removal rejects old there while callback accepts; callback removal rejects old there with unchanged body/effects; replacement still accepted; five-second mutation deadlines and unexpired old token checks; release yields exact `fixture result` completion with fresh replacement-key owner polls.
- Timing: start old-key blocker after restarts, immediately before removal, to remain within existing 30-second model deadline. Record observation limit/interval, bounded adoption booleans and distinct stage outcomes. Do not add flaky minimum-latency assertions.

### 8. `local-outbound-only`, `standard-outbound-only`, `hardened-outbound-only`

- Type: three real focused composed acceptance cases using `OutboundOnlyRunner`.
- Location: `suite/scripts/sdk_lane/{local,remote,local_probe,remote_probe,remote_contract}.py`, `suite/scripts/run_sdk.py`, reference-app `OutboundOnlyRunner.java`.
- Proves: SDK discovery, exactly one model submission, terminal COMPLETED/exact result, no callback listener and no callback routes under each configuration.
- Inputs/fixture: same model-only skill/empty route data, fixed authenticated fixture identity; Local colocated keys, remote provided keys and shipped invocation transports.
- Doubles/boundary: deterministic local model only. Use real Java non-web application/client, actual Sidecar and real remote TLS/Caddy path.
- Edge cases: check relevant app ports refuse connections during process lifetime rather than only after it exits; app process alive during listener check and later clean exit; Hardened outsider cannot reach app ingress ports, app namespace lacks callback backend and Sidecar namespace lacks callback egress; retained invocation path still succeeds. No automatic resubmission if response is lost. A successful model execution alone does not satisfy absence evidence.

## Safe Verification Commands

Run suite commands from `C:/opendev/code/loomspan-sidecar-test-suite`. Commands below are planned, not claimed executed.

```powershell
# First red capability test, then focused SDK harness regression.
.venv/Scripts/python.exe -m pytest tests/test_sdk_remote.py -k standard_group_rotation_has_two_apps_and_independent_trust_files -q
.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q

# Formatting/lint include the new modules via their parent directory.
ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py
ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py

# Broadest safe Python regression; no provider/business service invocation.
.venv/Scripts/python.exe -m pytest tests -q

# Formal recipe runs; each performs all mandatory builds and its full selected cases.
.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar
.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar
.venv/Scripts/python.exe scripts/run_sdk.py hardened --sidecar-checkout C:/opendev/code/loomspan-sidecar

git diff --check
git -C C:/opendev/code/loomspan-sidecar diff --check
```

For a production component failure requiring focused diagnosis, run `mvn -B -ntp -f C:/opendev/code/loomspan-sidecar/pom.xml '-Dtest=ConfigurationModeIntegrationTest,CurrentFileJwtTrustTest,SupportedLoomspanApiArchitectureTest' test`. This is diagnostic/focused verification; it does not replace the mandatory root `clean verify` embedded in formal runs. SDK focused command if its code changes: `mvn -B -ntp -f C:/opendev/code/loomspan-sidecar/sdks/java/pom.xml test`. Do not build Framework source or invoke the business runtime.

After each formal run, read its new `evidence/sdk/<run-id>/report.json`; enumerate actual project names from the report's runtime identities. For each exact project string, use these read-only commands (replace `ACTUAL_PROJECT_FROM_REPORT` with that exact value):

```powershell
docker ps -aq --filter 'label=com.docker.compose.project=ACTUAL_PROJECT_FROM_REPORT'
docker network ls -q --filter 'label=com.docker.compose.project=ACTUAL_PROJECT_FROM_REPORT'
docker volume ls -q --filter 'label=com.docker.compose.project=ACTUAL_PROJECT_FROM_REPORT'
docker ps -aq --filter 'label=loomspan.sdk.helper=ACTUAL_PROJECT_FROM_REPORT'
```

All listings must be empty; any explicitly external named volume must also be absent. Never use global prune. Inspect report/config source with an automated secret detector or assertion that reports matching file/category only, never matching lines. Known temporary fixture material remains only under the runner's temporary directory and is removed normally. Preserve failed reports and publish actual failure categories in evidence docs without raw diagnostics.

## Optional Developer Checks

None. Live deployment/key ceremonies, production trust and TLS certificate lifecycle are not required to prove this ticket.

## Exit Criteria

- [x] The new group composition red test fails for missing group/trust capability before implementation, with the intended cause recorded.
- [x] All added/updated focused harness tests and broad safe pytest suite pass; Ruff formatting/lint and both diff checks pass.
- [x] Fresh formal Local, Standard and Hardened commands pass every selected scenario and mandatory build/startup/cleanup stage. No cached-artifact bypass or provider call is used.
- [x] Standard group proves B-to-Sidecar-to-A callback identity plus method, record and result-owner denials.
- [x] Candidate/old/replacement credentials and public exports establish the actual add/restart/overlap/remove order; each receiver's unchanged opposite boundary is observed during staged mutation.
- [x] Per-receiver old-key rejection occurs before token expiry within five seconds of its own publication. Replacement tokens continue working, preventing an indiscriminate receiver failure from passing.
- [x] Already admitted old-key model work completes after old trust removal, with RUNNING/barrier and exact final result evidence.
- [x] Three outbound-only cases prove actual recipe invocation and absent callback listener/routes, including runtime checks while the app is alive and Hardened callback infra omission.
- [x] Existing both-mode trust authority and public Framework API boundary tests pass through the mandatory root build; no mode scenario matrix is introduced.
- [x] Every criterion has a named executable observation or operator-doc change; reports retain source revisions/dirty flags, artifact hashes, sanitized results and independently verified cleanup.
- [x] No credential/private key/JWT is present in tracked changes or evidence; maintained reference packs, operational credentials/data and business runtime are preserved.
- [x] Any failed formal run remains recorded and is followed by actual successful reruns for affected final acceptance; no failed report is edited into a pass.
- [ ] Fresh independent Step 5 review validates sufficient final evidence and returns clean before pipeline completion.


Step 4 evidence is recorded in the matching `ai/thoughts/validation/` artifact.
All implementation/automated exit criteria pass; only the independent review
criterion remains pending for the pipeline's next stage.
