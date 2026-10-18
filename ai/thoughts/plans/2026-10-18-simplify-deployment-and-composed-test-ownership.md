# Deployment verification and composed test ownership implementation plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-10-18-simplify-deployment-and-composed-test-ownership.md`
- Research: `ai/thoughts/research/2026-10-18-simplify-deployment-and-composed-test-ownership.md`
- Outcome: one bounded Sidecar shipped-recipe smoke; existing SDK lane owns retained composed checks; no certificate-lifecycle tests.
- Approved profile: Full 5-Step Pipeline. Steps 2 and 3 established this plan; checked items and Step 4 execution notes below record implementation evidence.

**Replace obsolete development contracts and tools directly; no compatibility shims, old-command wrappers or parallel legacy paths.** Apply the [design lens](../design-lens.md), especially simplicity and technical debt. No data reset is required or authorized. Preserve both repositories' credentials, operational data and maintained reference packs.

## Current State

Sidecar CI/release run root Maven tests, then `scripts/verify-image.py` and `scripts/verify-production.py`. The first starts quickstart; the second starts two production projects, invokes an environment-driven browser class eight times, performs recovery choreography, and invokes two more shutdown projects. `verify-production-tls.py` separately combines transport checks with certificate lifecycle tests. Shared imports couple production/shutdown to the image script. `ReleasePreparationTest` asserts these names and helper source strings.

The production Compose recipe binds Sidecar to namespace loopback and publishes only Caddy HTTPS/redirect listeners. `compose.supplied.yaml` supplies operator PEM material; health remains on application port 8080. No listener or product configuration changes are needed.

The external suite (`C:/opendev/code/loomspan-sidecar-test-suite`, abbreviated **suite** below) has `scripts/run_sdk.py`, `Runtime`, `sdk/compose.yaml`, a plain Java SDK driver and deterministic Python fixture. Local scenarios exist; Standard/Hardened have empty selectors which fail explicitly. Local's old PEM/issuer configuration, token profile and 9091 readiness URL are stale. The lane already has safe reports, isolated child environments and failure-sensitive cleanup. Root Sidecar Maven has no dependency on the suite.

Focused Sidecar management/browser/configuration/trust/shutdown/TLS tests already cover the repeated behavior; see the ownership table below. Planning verified working trees: only the supplied ticket and Step 1 research are untracked; suite is clean. No runtime checks were performed during planning.

## Desired End State

Component verification is `./mvnw.cmd -B -ntp verify` on Windows (`./mvnw` on Linux). Building the image and running `python scripts/verify-deployment.py --image <tag>` is the single shipped-recipe smoke path. The smoke uses actual production Compose/Caddy with supplied per-run TLS material; it has no browser, authoring, import, restore, lifecycle or in-flight shutdown collection.

Composed checks run separately through suite `scripts/run_sdk.py local`. Explicit scenario names distinguish restored blocking-client evidence and migrated deployment transport/shutdown baselines from future full SDK recipes. Standard/Hardened remain explicitly pending until their reference application and callback contracts exist. No test-only recipe enum or allowlist is added to production.

## Scope

### In scope

- Consolidate image/deployment checks, remove duplicate collections and certificate lifecycle helpers, update CI/release and release-preparation assertions.
- Repair existing plain Java SDK lane trust/readiness fixtures and add the minimum retained composed transport and real-process shutdown evidence there before deletion.
- Add ownership/evidence documentation and reconcile active 8.1.5–8.1.8 references.
- Record observed runtime costs without a benchmark system or arbitrary speed threshold.

### Out of scope

- SDK Spring form-login reference app, SDK callback security chain, full Standard/Hardened bidirectional recipes, trusted-group/JWKS rotation work (8.1.5–8.1.8).
- Application behavior, API, trust rules, listener topology, dependency/version/release changes or Framework implementation.
- Business suite runtime, `.runtime`, credentials, maintained fixtures, paid providers, live CA/DNS, commit/push/release or deployed data.

## Active Project Guardrails

- One Framework shutdown budget; Sidecar clients survive admitted work; test only public `ai.loomspan.api`, preserve ArchUnit boundary checks.
- JWT identity, exact roles, file trust and expiry remain enforced independently of TLS and management sessions. JWT rotation/withdrawal are not TLS lifecycle.
- Standard Spring Boot SSL and Caddy own transport. DevOps owns certificate lifecycle. No renewal/reload controller or lifecycle tests.
- Representative recipes, proportional fixtures and no network-configuration cross-product. Keep production and SDK lane verification separate and nonrecursive.
- Preserve unmodified operator deployment options and guidance. History remains history; no rewriting old verification reports as current evidence.

## Impact and Risk Analysis

Deleting broad checks can hide unique deployment assurance. The deletion gate below requires fresh executable suite replacement for direct Spring HTTPS, Caddy transport normalization/rejection and real SIGTERM behavior. Mapping component duplicates does not claim they prove every Docker mechanic.

Smoke cleanup and diagnostics are infrastructure behavior worth focused tests: cleanup must run after partial startup and diagnostic failure; all owned resources must be attempted; a cleanup failure must fail the command even after otherwise successful assertions. Never print command environments, token bodies, private keys, response payloads or raw container logs. Use fixed diagnostic categories and safe status summaries.

Suite build and runtime identity must describe actual checkout artifacts; retaining its existing build path avoids a new stale-artifact cache contract. Add per-stage elapsed duration to existing reports; do not introduce build reuse or a benchmark harness in this ticket. Suite continues to build Sidecar once per invocation; it never calls Sidecar's Docker smoke, and Sidecar never calls the suite.

## Implementation Approach

Use the existing SDK lane and `Runtime` for retained composed baseline checks, with small Compose overrides and temporary configuration where modes differ. Do not copy the production verifier or import Sidecar verification scripts into the suite. Add focused fixture functions/modules only where the existing file would otherwise become unwieldy.

Keep current nonredundant checks before removing their Sidecar owners. First restore Local invocation and add deployment transport and shutdown baselines; execute them against the current Sidecar build. Then consolidate the Sidecar smoke and delete the old collections. The current ticket takes the stale fixture repair formerly assigned to 8.1.5; 8.1.5 still owns the actual Spring/colocated recipe. Baselines are not evidence for absent SDK Spring callback authentication or full Standard/Hardened acceptance.

## Ownership inventory and deletion gate

Create `docs/verification.md` as the current maintainer guide and durable detailed inventory; use this mapping as its minimum content. Each retained suite row must link an actual scenario name and fresh `report.json` before old script deletion is accepted.

| Existing assertion family | Final owner / disposition |
| --- | --- |
| Image UID/JVM sizing, writable fresh database, production namespace/mounts/grace period, published ingress only | `verify-deployment.py`: image/config inspection and actual startup; no separate image path. |
| Readiness/liveness, anonymous status only, no second listener, TLS ingress, authenticated catalog, missing JWT | Small smoke plus retained `ManagementEndpointIntegrationTest`, `ConfigurationModeIntegrationTest`, JWT tests. |
| Execution/management credential separation, locked setup, admin token/reset CLI | Existing `ManagementAdminCommandTest`, `ManagementLockedExecutionIntegrationTest`, `ManagementHttpIntegrationTest`, personal-token tests. Remove repeated container CLI/setup/reset collection. |
| Secure session cookie through production HTTPS ingress | Small smoke requests the anonymous login page and requires Secure `JSESSIONID`; retained HTTP component fixtures explicitly disable secure cookies, so they cannot replace this assertion. |
| Browser login, invitations, roles, CSRF, mail outage/origin, draft handoff/stale grant, import/history browser | Existing `Management*HttpIntegrationTest`, `ManagementMail*Test`, `ManagementEditorBrowserIntegrationTest`, `ManagementImportBrowserIntegrationTest`, `ManagementHistoryBrowserIntegrationTest`; remove composed browser class and SMTP capture. |
| Authored publication, model/REST execution, generation/snapshot correlation, preserved URL variables | Existing execution/configuration/REST tests plus restored suite `blocking-client` against current trust. |
| Second deployment import/rebinding/credential replacement/rollback/restart | `ManagementConfigurationHttpIntegrationTest`, `CredentialPortabilityIntegrationTest`, `RuntimeConfigurationIntegrationTest`, `RestGenerationIntegrationTest`, `RestRouteRestartIntegrationTest`. Remove duplicate two-deployment choreography, not its component cases. |
| Stopped complete SQLite copy, corrupt selection refusal, restore accounts/history/configuration | `RuntimeConfigurationRecoveryIntegrationTest` and durable restart tests. Explicitly remove Docker-volume copy/chown/replace choreography as test-harness mechanics; smoke still proves fresh UID-owned storage. Operator backup guidance remains. |
| Container SIGTERM: admitted nested work completes with callback clients alive, or bounded Framework cutoff without later detail/finish effects | Suite `shutdown-completion` / `shutdown-cutoff` using existing lane fixture and temporary file configuration; retain Sidecar `RestHandlerLifecycleIntegrationTest` / `ExecutionShutdownIntegrationTest`. Fresh runtime proof required before deleting `verify-shutdown.py`. |
| Direct localhost HTTP and direct standard Spring PEM HTTPS image reachability | Existing suite Local startup plus `deployment-transport-baseline` direct HTTPS variant; no form-login SDK claim. |
| Supplied Caddy TLS positive, untrusted CA rejection, redirect destination, fixed upstream, forwarded-header normalization | Suite `deployment-transport-baseline` supplied-Caddy variant using per-run certs; smoke also uses shipped supplied recipe for auth/health/no-bypass. Fresh suite proof required. |
| Static production/supplied Compose topology | Smoke rendered configuration checks. |
| Static public/DNS Caddy adaptation | Explicitly remove external Caddy plugin/adaptation-only checks; no Sidecar runtime assertion is lost. Preserve shipped configs and operator guidance; no DNS/ACME execution. |
| Root-key permissions, same leaf/CA across restarts, regeneration after deletion, forced supplied replacement/reload | Delete as certificate lifecycle; no replacement anywhere. |
| Outbound TLS/mTLS selected bundle, wrong/missing client rejection | Retain `RestTransportTlsIntegrationTest`. Future full SDK peer/hostname/expiry and bidirectional no-bypass belong to 8.1.7, not claimed by stub/baseline evidence. |

## Phase 1: Restore and establish executable suite replacement evidence

### Changes

- [x] Suite `scripts/run_sdk.py::write_config` — replace retired PEM/issuer-uri keys with `trust.mode=file`, `trust.file.jwks`, current `auth.jwt.audience`; emit public-only RSA JWKS with nonempty `kid`. Sign temporary token with `typ=loomspan-execution+jwt`, matching `kid`, `token_use=loomspan_execution`, nonblank issuer and subject, audience and expiry. Issuer is a verified identity claim, not a new Sidecar issuer configuration property. Keep private key in memory and token only in driver mount.
- [x] Suite `Runtime.readiness` — poll port 8080 and require status-only UP; preserve no external application port for Local. Keep existing blocking-client/no-replay scenarios unchanged in meaning.
- [x] Suite `sdk/compose.yaml`, `scripts/run_sdk.py::Runtime/run_smoke`, `scripts/sdk_lane/fixture.py` — extend the same runtime with `deployment-transport-baseline`, `shutdown-completion`, `shutdown-cutoff`; use generated temporary Compose overrides/configuration, fixture controls/counters and common cleanup/reporting. Add a focused `scripts/sdk_lane/transport.py` module if needed for temporary cert generation and assertions; no independent runner.
- [x] Transport baseline: boot actual image with standard Spring supplied PEM HTTPS; prove trusted reachability, wrong CA, wrong hostname and expired server rejection using temporary material. Boot Caddy supplied configuration against the lane fixture/Sidecar; prove verified TLS positive, wrong trust rejection, normalized forwarded headers, redirect host, fixed upstream and external backend refusal. Assertions must positively establish readiness before interpreting a negative connection result. Per-run cert creation is fixture preparation, not issuance-service testing. Do not modify/reload/replace live certs.
- [x] Shutdown scenarios: generate deterministic planner/nested REST declarations directly in temporary file mode, rather than copying a management-created database. Extend existing fixture with synchronized entered/release/detail/finished state and deterministic planner output. Admit work, wait until entered, send actual TERM, verify process still running briefly, then release for completion or leave blocked for cutoff. Assert effects, exit/no OOM and bounded Framework budget as in the prior check. Use independent observer fixture state after Sidecar exit; no operational mounts or raw logs.
- [x] Suite `stage` — add elapsed seconds for passed/failed stages and total run duration to existing sanitized report; report scenario results explicitly. Keep Standard/Hardened empty selectors fail-closed and state full recipes pending.
- [x] Suite `tests/test_sdk_runner.py` — update trust/profile/readiness assertions and meaningful success/failure/cleanup/environment/report tests; add fixture latch and transport-negative tests where they catch false-positive rejection.

### Automated verification

- [x] In suite: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`.
- [x] In suite: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- [x] In suite: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar` — all old and new baseline scenarios pass with artifact identities, explicit scenario outcomes, safe duration evidence and passing cleanup. This is the deletion gate.

### Optional developer checks

None. Full future recipes are separate ticket acceptance, not optional substitutes for current gates.

## Phase 2: Implement the bounded shipped-recipe smoke and retire duplication

### Changes

- [x] Add `scripts/verify-deployment.py` — single entry point, required `--image`, ephemeral loopback ports, UUID project and temporary directory, bounded subprocess/network calls, validated inputs and explicit report/status summaries. Use `examples/production/compose.yaml` plus shipped `compose.supplied.yaml`/`Caddyfile.supplied`, generated per-run TLS PEM and public JWKS mounted read-only; keep production listeners unchanged. Python stdlib plus OpenSSL are sufficient (pattern exists in quickstart host); document OpenSSL prerequisite rather than adding a new Python dependency.
- [x] Generate a short-lived execution JWT with matching public trust using OpenSSL, `kid`, current `typ/token_use`, issuer/audience and expiry; no signing/credential output. Use deterministic local model configuration only to satisfy startup, never invoke a provider. Database starts empty and UID-owned. No setup/login or publication needed to authenticate an empty catalog.
- [x] Assert image UID and memory options; rendered two-service namespace/port/health topology; start once; anonymously check health bodies contain status only; trusted HTTPS catalog returns 200 for valid JWT and 401 without it; require a Secure session cookie from the anonymous login page; prove application port 8080 is unreachable from an external peer while ingress is reachable. A disposable probe container may join only this run's backend network; do not mount operational data. Health remains on application listener.
- [x] Cleanup all owned Compose resources/volumes on success and every failure; preserve primary diagnostic, record cleanup failure and nonzero exit. Collect only safe container state summaries. Do not print Compose env/config, token-bearing HTTP commands or raw logs.
- [x] Add `scripts/test_verify_deployment.py` for cleanup/safety/error paths, including partial startup, timeout, diagnostic failure and one cleanup failure followed by remaining cleanup attempts.
- [x] After Phase 1 gate passes, delete `scripts/verify-image.py`, `verify-production.py`, `verify-production-tls.py`, `verify-shutdown.py`, `test_verify_shutdown.py`, unused `scripts/fixtures/shutdown-host.py` / `smtp-capture.py`, and `src/test/java/ai/loomspan/sidecar/deployment/ProductionComposeBrowserIntegrationTest.java`. Search actual callers before deleting any further fixture. Preserve shipped quickstart host/skills.
- [x] `.github/workflows/ci.yml` and `release.yml` — one smoke invocation after existing component verification/image build; replace helper unittest command; remove stale nested-Maven comment. Neither workflow references suite checkout.
- [x] `src/test/java/ai/loomspan/sidecar/release/ReleasePreparationTest.java` — assert current one-smoke workflow and independence; remove old helper implementation-string assertions. Retain release safety tests.

### Automated verification

- [x] `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`.
- [x] `./mvnw.cmd -B -ntp -Dtest=ReleasePreparationTest test`, then `./mvnw.cmd -B -ntp verify` (includes retained browser, trust/lifetime/API boundary checks).
- [x] `docker build --tag loomspan-sidecar:deployment-smoke .`, then `python scripts/verify-deployment.py --image loomspan-sidecar:deployment-smoke` — one successful project and no residual resources.
- [x] Exercise controlled post-start assertion failure via the script's testable orchestration boundary and verify unconditional cleanup; use Docker-backed failure observation where practical without adding a public fault-injection CLI. Unit tests must cover cleanup even if startup fails partially.

### Optional developer checks

None.

## Phase 3: Publish ownership, reconcile future work, and record reduced cost

### Changes

- [x] `docs/verification.md` — inventory above with concrete symbols, fresh command/results/report links, complete active commands/prerequisites and separate component/smoke/suite scopes. Explicitly identify removed lifecycle and Docker harness mechanics. Record failed attempts honestly and future contracts as pending.
- [x] `README.md`, `examples/production/README.md` — short link to current maintainer verification; retain all operator certificate ownership/support guidance.
- [x] Suite `docs/sdk-lane.md`, `README.md`, SDK section of `docs/project-agreement.md` — current trust/readiness contract, executable baseline names and evidence limits; preserve business-lane instructions. State component build cost remains once per SDK run and no recursive smoke.
- [x] Sidecar tickets `2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md`, `...8-1-6-sdk-callback-authentication.md`, `...8-1-7-standard-https-and-hardened-mtls-recipes.md`, `...8-1-8-trusted-group-rotation-and-outbound-only.md` — link this ownership change, mark stale plain-Java fixture repair as taken here, and preserve actual unimplemented acceptance obligations. Update active roadmap/matrix references only where necessary; do not rewrite historical evidence or parked maintainer history.
- [x] Record before commands and research historical baseline explicitly: five CI-pair Compose projects / 19 initial service starts before recreation, eight nested browser Maven runs, historical browser Maven 109.750 seconds and shutdown 2.48/4.53 seconds. Record new actual project/service/Maven counts and wall/stage durations from execution. Do not claim an old total wall duration or speedup percentage that was never observed; no broad legacy rerun solely for benchmarking.

### Automated verification

- [x] `rg -n 'verify-image\.py|verify-production\.py|verify-production-tls\.py|verify-shutdown\.py|ProductionComposeBrowserIntegrationTest' .github scripts src/test README.md docs examples` — no active old command/caller; historical thoughts are intentionally excluded.
- [x] Inspect both repositories' active scripts/tests for removed lifecycle behavior and helper-only fixtures; distinguish certificate validation and business warranty fields.
- [x] `git diff --check` in both repositories; final inventory reconciles every old assertion family with component/smoke/suite/removal evidence and no unexplained skip.

### Optional developer checks

None. Hosted CI/release execution is not performed by this ticket; local equivalent checks are required.

## Test Strategy

Keep all focused production regressions. Add cheap tests only for changed runner safety, trust fixture and executable assertions; Docker proves actual image/namespace/signal boundaries. Reuse suite's environment filtering, failure categories and report identity. Detailed cases and exit criteria are in the sibling testing plan. No red product-behavior test applies to this ownership refactor; stale fixture/profile and cleanup-contract tests provide useful infrastructure red tests.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Single bounded smoke, startup/auth/health/no bypass and cleanup | New verifier, one workflow command | Python safety tests; fresh built-image smoke and cleanup observation |
| Focused regressions remain green, topology unchanged | No application source/recipe topology changes; retained tests | Root Maven verify incl browser/trust/lifetime/ArchUnit |
| All assertions have one owner, unique checks executable | `docs/verification.md` inventory | Fresh suite Local report links for every migrated baseline |
| SDK lane owns composition; future recipes remain explicit | Existing runner extensions and 8.1.5–8.1.8 reconciliation | Local baselines pass; empty future selectors still explicit failures |
| No active TLS lifecycle tests/helpers; validation/guidance retained | Legacy TLS script removed; temporary transport fixture | Transport negatives and TLS component tests, source audit |
| Independent Sidecar CI and nonrecursive suite | Workflow/release test; unchanged Maven boundary | Maven root and isolated SDK runner command trace/tests |
| Reduced duplicate work measured honestly | Current guide and stage elapsed report | Actual counts/durations plus labeled historical observations |
| Disposable, no provider/live CA/DNS/data operations; cleanup fails closed | Temporary configs/projects and safe reports | Environment/cleanup failure tests, runtime cleanup and status checks |

## Risks and Rollback/Recovery

If replacement checks fail, fix them before deleting old owners; future ticket links cannot waive this gate. Component overlaps are validated with fresh root execution. Test fixture failure must not be mistaken for successful TLS rejection. No rollback of production or data is involved. Recover tooling through version control if necessary; do not keep deprecated entry points as rollback machinery. If a required runtime tool is unavailable, report exact unrun gate and residual risk rather than marking the ticket complete.

## References

- Ticket/research linked in Overview; design lens linked above.
- `scripts/verify-image.py`, `verify-production.py`, `verify-production-tls.py`, `verify-shutdown.py` (pre-change source).
- `examples/production/compose.yaml`, `compose.supplied.yaml`, `Caddyfile.supplied`.
- `src/test/java/ai/loomspan/sidecar/{management,configuration,execution,security,rest,architecture}`.
- Suite `AGENTS.md`, `README.md`, `docs/project-agreement.md`, `docs/foundation.md`, `docs/runtime.md`, `docs/sdk-lane.md`, `scripts/run_sdk.py`, `tests/test_sdk_runner.py`, `sdk/compose.yaml`.

## Implementation execution notes

- Initial dirty state was only the supplied untracked ticket; research/plans are this pipeline's artifacts. The external suite was clean. No operational data, credentials, maintained reference pack, dependency/version, release, commit or push is changed.
- Red contract check: suite `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q -k test_write_config_uses_current_jwks_execution_contract` failed with `KeyError: trust` against the retired fixture, then passed after replacing PEM/issuer settings with public JWKS/current JWT claims.
- First full Local attempt: `evidence/sdk/20261018T210749Z-8af2a7427ca7/report.json` in the suite. Root components passed (264 tests, zero failures/errors, three old environment-composed browser skips); the retained component Chromium tests did not skip. Real SDK completion, primary REST failure and acceptance-unknown/no-replay checks passed. Shutdown fixture startup failed because its generated REST target omitted mandatory `connect-timeout` and `max-response-size`; cleanup passed. This is failed attempt evidence, not the deletion gate. Runtime 399.331s included 297.734s root component build, 31.387s SDK reactor and 5.954s driver build.
- Routine fixture correction: the generated shutdown target supplies all required route limits, as required by `RestRouteLoader`. Targeted disposable runtime checks may reuse the already-built image to debug this fixture; final SDK evidence still uses the unchanged normal actual-checkout build path, with no public cached-artifact bypass.
- Targeted real-process fixture checks after route repair passed completion in 2.320s and cutoff in 3.914s (exit 143/no OOM, expected effects). The first transport attempt failed because Python 3.13 strict TLS requires certificate AKI/SKI and CA key usage; generated per-run certificates now include those standard extensions, verified by real local TLS handshakes for valid/wrong-CA/hostname/expired material. No TLS lifecycle operation was added.
- First bounded smoke attempt failed readiness (127.400s) and passed cleanup: fixed IPv4 connection used IP SNI instead of the shipped `localhost` site. Requests now connect to IPv4 with verified `localhost` SNI, with no global resolver change. The subsequent actual built-image smoke passed in 18.897s with cleanup. A targeted transport rerun passed all direct HTTPS and supplied Caddy assertions. These targeted observations do not replace the normal complete SDK-run deletion gate.

- Docker-backed post-start failure observation used the new runtime's testable `assertions` boundary: after real assertions completed, a fixed `controlled-post-start-failure` was raised. The resulting report stayed failed, cleanup passed (18.063s), and independent project-label listings found no containers/networks/volumes. It used only its unique `sidecar-smoke-f2a847ebff144790` project. No public fault-injection CLI was added.
- The new guide accounts for all old families, including explicit removal of Docker-volume shell choreography and certificate-lifecycle/adaptation-only checks. Sidecar and suite guides preserve future SDK Spring/Standard/Hardened obligations. Workflow and release-preparation changes are applied together after the first replacement run's old compiled test has finished.
- Routine ownership adaptation: real TERM admission uses a disposable Python client in the existing internal SDK network with a read-only temporary token mount. The driver remains the only resident service with that mount; neither Sidecar nor the resident fixture receives credentials. The token is read inside the short-lived client and never appears in command arguments/output. This avoids adding a second Java test driver just to submit the deterministic shutdown execution; signing private material remains in memory. Current suite documentation states this exact boundary.

- Deletion gate PASSED before removal: suite `evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json`. All five selected scenarios and every stage/cleanup passed; total 453.482s, root build 306.614s, SDK reactor 29.542s, driver 6.308s, image 9.200s. TERM completion 2.420s/cutoff 3.912s, exit 143/no OOM, expected detail/finished effects. Direct Spring trusted/wrong-CA/hostname/expired and supplied-Caddy normalization/fixed-upstream/redirect/no-bypass checks passed. Actual runner counts are one project, eight service starts including recreations/additions, one Java driver and three disposable Python clients; three Maven builds, with root components once and no nested browser invocation. Fresh report is linked from the ownership inventory.

- Final post-deletion `./mvnw.cmd -B -ntp clean verify` PASSED in 04:55 Maven-reported minutes: 61 classes / 261 tests, zero failures/errors/skips. The clean removes stale bytecode for the deleted composed browser class. `./mvnw.cmd -B -ntp -Dtest=ReleasePreparationTest test` passed after preserving LF workflow content (first focused attempt exposed newline conversion). No release profile/workflow was executed on the development snapshot.
- Final rebuilt image `sha256:81b070449415babeb6174d824609af882244ec1588f9314dcadee0921e7d9111` passed `python scripts/verify-deployment.py --image loomspan-sidecar:deployment-smoke`, with cleanup and 20.568s reported orchestration duration. Smoke timer excludes per-run TLS/JWT preparation, so no total smoke CLI wall-time claim is made. Normal SDK total covers its full build/runtime orchestration.
- Final infrastructure verification: Sidecar seven unittest safety cases, suite 48 pytest cases, Ruff format/lint, both diff checks and active command/lifecycle-helper audits passed. Independent Docker label checks found no resources for formal Local or smoke projects. No application/recipe topology, dependency/version, operational/reference data, provider/live CA/DNS, reset, release, commit or push change occurred. Step 5 independent review remains required.
