# Deployment verification and composed test ownership testing plan

## Change Summary

Use the sibling implementation plan and supplied ticket/research. Replace obsolete development tools directly, with no legacy wrappers; apply the [design lens](../design-lens.md). No application behavior, dependency, listener or data change is intended. No development-data reset is required or authorized.

The change replaces two broad Sidecar image/production paths with one production-recipe smoke, removes certificate-lifecycle coverage, and restores/extends the existing external SDK lane only enough to retain current unique transport and real-process shutdown assurance. This plan defines required checks; checked exit criteria and the linked execution record distinguish completed results.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Coverage ownership | Unique integration check deleted behind a future ticket | Inventory plus fresh suite baseline report before legacy deletion |
| Trust fixture | Old JWT/configuration fails current public contract | Verify generated JWKS/profile/signature; real SDK invocation on port 8080 |
| Transport negative | Unready service mistaken for peer rejection | Positive readiness/trusted request first; specific TLS failure and zero application effect |
| Shutdown | Process exits before admitted work or doubles budget | Actual TERM completion/cutoff scenarios and retained focused public-API tests |
| Smoke isolation | Backend exposed, health leaks detail, fixtures call providers | Namespace peer refusal, parsed status-only health, deterministic local configuration |
| Cleanup | Partial startup/diagnostic failure leaves state or passes | Inject failures at orchestration boundary, require all cleanup attempts and nonzero result |
| Reports | Credentials/payloads/raw diagnostics leak | Existing sentinel-based tests and safe fixed diagnostics |
| Pipeline | New recursive suite dependency or duplicate smoke | Workflow/release assertions and suite build call assertions |

## Existing Coverage and Environment Constraints

Sidecar uses JUnit/AssertJ/Spring tests, ArchUnit, Playwright Chromium and Maven Surefire. Keep `Management*HttpIntegrationTest`, editor/import/history browser tests, `RuntimeConfigurationRecoveryIntegrationTest`, credential portability/restart tests, authenticated execution/correlation, trust/roles/expiry tests, `RestTransportTlsIntegrationTest`, lifecycle/shutdown and `SupportedLoomspanApiArchitectureTest`. Remove only the environment-driven composed browser class after its ownership is recorded.

The suite uses pytest, cryptography, PyYAML, httpx, Ruff and `scripts/run_sdk.py`; use its `.venv/Scripts/python.exe`. Required runtime tools: Java 21/JAVA_HOME, Maven, Docker/Compose, Python, OpenSSL for the new Sidecar smoke, installed/resolvable Framework beta.8, local Playwright Chromium. Container pulls and Maven downloads may occur; provider/live CA/DNS calls must not. Use unique Compose projects and temporary files; never use business `scripts/start.py`, `.runtime`, credentials or reference packs for this ticket. Do not run or rebuild the Framework checkout.

Root Maven tests include browser tests; root build must never call suite scripts. Suite continues to build the actual Sidecar checkout and SDK once per invocation; avoid a new cached-artifact bypass. Phase 1 runtime evidence must precede removal of current unique checks. Source inspection or mocked orchestration is insufficient replacement evidence by itself.

## Failing Test First

- Name: `test_write_config_uses_current_jwks_execution_contract` (new/updated suite case).
- Type/location: pytest infrastructure contract in suite `tests/test_sdk_runner.py`.
- Arrange/Act/Assert: create temporary config/driver credential directories; call `write_config`; parse YAML and public JWKS; decode and verify JWT against JWKS; assert file trust and configured audience, nonblank JWT issuer/subject, nonempty matching `kid`, `typ=loomspan-execution+jwt`, `token_use=loomspan_execution`, bounded expiry, no private JWK/PEM output or retired keys. Current Sidecar config has audience only, with no issuer property.
- Expected pre-fix failure: current `write_config` emits PEM configuration, old issuer-uri/public-key-location, `typ=JWT`, and lacks `kid/token_use`.
- No red production-behavior test applies to a test-ownership refactor. Existing product regression tests must remain green; cleanup/error-path tests below prove new infrastructure failure handling.

## Tests to Add or Update

### 1. Current trust, readiness and recipe evidence

- Type/location: pytest in suite `tests/test_sdk_runner.py`, updating existing fixture-signature and fake-lane tests.
- Proves: current generated JWT verifies with public-only JWKS; retired settings absent; readiness uses application port 8080/status-only health. Local lists actual baseline scenarios; Standard/Hardened remain explicit `no-scenarios-for-recipe` failures while full SDK recipes are pending.
- Fixtures: temporary RSA material, current fake checkout and fake Runtime; no private key persisted by JWT generator.
- Edge cases: failed/not-run scenario remains distinguishable from passed; unknown fields/partial success do not pass. Reports retain source/build hashes and elapsed durations for passed and failed stages.

### 2. Runner cleanup, failure preservation and safe diagnostics

- Type/location: pytest in suite `tests/test_sdk_runner.py`; stdlib unittest in Sidecar `scripts/test_verify_deployment.py`.
- Proves: success cleanup; partial startup cleanup; assertion/command timeout cleanup; diagnostic collection failure cannot suppress cleanup; primary failure survives; cleanup failure alone gives nonzero result; every owned cleanup operation is attempted even if an earlier operation fails.
- Doubles: monkeypatch subprocess/network/runtime boundaries, not real product services. Use existing sentinel credential/private-key/payload strings to assert none enter stdout/report; environment filtering stays enforced.
- Edge cases: KeyboardInterrupt where supported by existing main/finally structure; command-unavailable; cleanup timeout; cleanup return code nonzero. Do not retain source-string tests that merely mirror helper implementation.

### 3. Existing Local SDK behavior on repaired fixtures

- Type/location: Docker end-to-end suite `scripts/run_sdk.py local`, existing `sdk/driver/.../BlockingClientDriver.java` and `scripts/sdk_lane/fixture.py`.
- Proves: real discovery/submission/polling, exact opaque completed result, REST primary failure, acceptance unknown with one attempt/forward/effect, no extra replay after driver exit; no model activity before invocation. Existing behavior is retained, not renamed into new SDK Spring evidence.
- Inputs/isolation: temporary file-mode skills/routes/JWKS/token and existing deterministic OpenAI/REST fixture; internal network, no host credential or operational-data mounts.

### 4. Migrated deployment transport baseline

- Type/location: Docker scenarios through suite `Runtime`/`run_smoke` and `sdk/compose.yaml` overrides; temporary certificate utility in `scripts/sdk_lane/transport.py` if needed.
- Proves: actual image direct standard Spring HTTPS trusted reachability; wrong CA/hostname/expired server material rejected. Supplied Caddy trusted ingress succeeds, wrong trust fails, forwarded headers normalized, redirect host fixed and backend cannot be reached by external namespace peer.
- Fixtures: one per-run CA/server/negative certificate set; deterministic fixture header observation and fixed upstream, existing SDK lane runtime and safe reports. No live CA service, certificate replacement, reload, persistence or regeneration assertions.
- Edge cases: establish trusted positive and service readiness before negatives; TLS verification failures differ from generic refused/unavailable network. Assert rejected calls have no corresponding fixture effect. Keep absent/wrong-client outbound mTLS regression in `RestTransportTlsIntegrationTest`.
- Limit: this baseline is not full Standard/Hardened invocation and callback coverage, SDK Spring method/business security, or provided signing deployment evidence. Those remain 8.1.5–8.1.8 obligations.

### 5. Migrated real SIGTERM completion and cutoff

- Type/location: suite Docker scenarios `shutdown-completion` / `shutdown-cutoff`, extending `scripts/sdk_lane/fixture.py` and temporary generated skills in `scripts/run_sdk.py`.
- Arrange/Act/Assert: fresh file configuration admits deterministic nested execution; wait on synchronized fixture entered state; send actual TERM; observe process remains alive briefly. Completion releases blocked work and asserts detail/finished effects before normal exit. Cutoff withholds release and asserts configured Framework budget is reached, exit is bounded, no detail/finished effects, no OOM and normal code 0/143.
- Preserve the prior meaningful bounds (3s Framework budget, cutoff at least 2.5s and overall below 12s) unless measured environment evidence requires a documented adjustment; do not assert exact scheduling time. Fixture entry is the synchronization gate, not fixed pre-TERM sleeps.
- Fixtures/isolation: temporary file config and shared lane fixture; independent fixture survives Sidecar exit. Never copy or corrupt operational DB; no repeated authoring/browser setup. Latch fixture tests cover entered/release/finished sequencing and bounded waits.
- Retain root `RestHandlerLifecycleIntegrationTest` and `ExecutionShutdownIntegrationTest`, using only supported Framework APIs.

### 6. Single shipped-recipe deployment smoke

- Type/location: Docker via `scripts/verify-deployment.py`, actual production Compose + supplied overlay and built image.
- Proves: nonroot image/expected JVM sizing, fresh writable storage, correct namespace/published ingress, readiness/liveness status only over verified TLS, authenticated catalog 200, unauthenticated catalog 401, Secure session cookie on the anonymous login page, no direct backend bypass from external peer; cleanup completes. Cookie negative cases reject absent, insecure and wrong-name cookies without adding browser/setup choreography.
- Inputs: per-run TLS/JWKS/current-profile JWT; local-only deterministic startup configuration, empty catalog. No browser or management setup needed.
- Negative verification: unit-tested orchestration failure after startup plus Docker-backed observation if practical without exposing a new public fault-injection CLI. Inspect only this run's project labels for residual containers/networks/volumes after success and failure. Unrelated Docker resources are untouched.

### 7. Workflow and ownership maintenance

- Type/location: update `ReleasePreparationTest`; source/document audits and `git diff --check`.
- Proves: one deployment smoke in CI/release, no suite checkout requirement, all obsolete script callers removed, all broad assertion families assigned to focused component/smoke/suite/explicit removal. Retain release immutability checks; no release is executed.
- Audit active code in both repositories for lifecycle operations (`reload --force`, forced leaf deletion, leaf/CA persistence/replacement/revocation assertions). Do not flag per-run certificate generation or business certificate fields as lifecycle testing. Historical thought documents remain historical.

## Safe Verification Commands

Run from Sidecar unless noted. Use PowerShell literal arguments for Maven test selectors if commas are present.

1. Suite focused: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q` from suite.
2. Suite style: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` from suite.
3. Suite deletion gate: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar` from suite. Save generated new `evidence/sdk/<run-id>/report.json`; do not overwrite old evidence.
4. Smoke helper: `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`.
5. Workflow focused: `./mvnw.cmd -B -ntp -Dtest=ReleasePreparationTest test`.
6. Full safe relevant component suite: `./mvnw.cmd -B -ntp verify`. Include configured Chromium tests; do not report absent-browser skips as equivalent proof.
7. Image: `docker build --tag loomspan-sidecar:deployment-smoke .`.
8. Smoke: `python scripts/verify-deployment.py --image loomspan-sidecar:deployment-smoke`.
9. Audit: `rg -n 'verify-image\.py|verify-production\.py|verify-production-tls\.py|verify-shutdown\.py|ProductionComposeBrowserIntegrationTest' .github scripts src/test README.md docs examples`, then `git diff --check` in each checkout.

Linux CI uses `./mvnw` instead of `./mvnw.cmd`. Do not run release profile on the development snapshot or a release workflow; it intentionally rejects snapshots. Existing unrelated business replay/runtime suites are outside changed code and would touch operational state; do not invoke them. Further root reruns are justified only by changed inputs or an unresolved failure.

## Optional Developer Checks

None. Future full SDK recipes are required by their own tickets, not manual substitutes or completion claims here. Hosted CI is not run in this task; local equivalents provide current evidence.

## Exit Criteria

- [x] Current trust fixture test fails before its repair for the intended retired-contract reason and passes afterward.
- [x] New/updated infrastructure tests and style checks pass; failure/timeout/cleanup safety is covered.
- [x] Fresh suite Local report passes current invocation, migrated transport baseline and both actual TERM scenarios before old unique check owners are deleted.
- [x] Root Maven verify remains green including management/browser/trust/lifetime/API-boundary tests; no required skip is hidden.
- [x] One actual built-image smoke passes through shipped production Caddy config and cleans up. Failure cleanup is proved by injection tests and runtime observation where practical.
- [x] Inventory maps all old families, names duplicate/removal rationale and links fresh executable evidence. Future Standard/Hardened/Spring callback scenarios remain pending explicitly.
- [x] Neither repository has active lifecycle-only tests/helpers; transport negatives and operator TLS support remain.
- [x] Normal Sidecar CI is independent of suite; suite has no recursion and uses its existing lane.
- [x] Counts and durations distinguish historical baseline (five projects / 19 initial services / eight nested browser Maven runs; 109.750s historical nested Maven) from actual new observations. No fabricated before-total or speedup.
- [x] No provider/live CA/DNS/operational-data/reference-pack activity occurs, no reset is needed, and cleanup failures fail evidence.
- [x] Both working trees pass diff checks; implementation report names exact commands/results and any unmet gate honestly.


## Execution record

See the sibling implementation plan's execution notes and
[maintainer verification](../../../docs/verification.md) for report links, failed
attempts, exact standard commands, counts and duration limits. Final root clean
verification passed 261 tests with no failure/error/skip; suite pytest passed 48,
smoke safety unittest passed seven, final image smoke reported 20.568s with cleanup.
The normal SDK Local gate passed all five scenarios before old unique owners were
deleted. Full future SDK recipes remain explicitly pending.

Docker failure cleanup was observed with `python -` and the following inline test
of the existing boundary (the runtime chose its own unique project; no public
injection flag was added):

```python
import importlib.util, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location('deployment', Path('scripts/verify-deployment.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
with tempfile.TemporaryDirectory(prefix='deployment-failure-') as temporary:
    runtime = m.Runtime(Path(temporary), 'loomspan-sidecar:deployment-smoke')
    original = runtime.assertions
    def post_start_failure():
        original()
        raise m.Failure('controlled-post-start-failure')
    runtime.assertions = post_start_failure
    report = m.verify(runtime)
    assert report['status'] == 'failed'
    assert report['diagnostic'] == 'controlled-post-start-failure'
    assert report['cleanup'] == 'passed'
    for resource in ('container', 'network', 'volume'):
        assert not m.command(['docker', resource, 'ls', '-q', '--filter',
            'label=com.docker.compose.project=' + runtime.project]).strip()
```

Observed: fixed primary failure retained, cleanup passed, 18.063s reported
orchestration duration and no project resources remained. Final root verification
used `clean verify` to remove stale bytecode after deleting the old test class;
this was the only additional broad root run after the replacement gate.
