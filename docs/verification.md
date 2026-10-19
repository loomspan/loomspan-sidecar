# Maintainer verification

Sidecar component verification and the shipped deployment smoke are independent
of `../loomspan-sidecar-test-suite`. The suite separately builds the actual Sidecar
checkout once per invocation and owns composed SDK/application transport and
process-shutdown checks. Neither command invokes the other.

## Commands

Prerequisites: Java 21 (`JAVA_HOME`), Maven/wrapper, installed/resolvable Framework
`1.0.0-beta.8`, local Playwright Chromium, Docker Compose, Python and OpenSSL.
Container pulls and public dependency downloads can occur. No provider credentials
or live CA/DNS service are used. Run from the Sidecar root:

```powershell
./mvnw.cmd -B -ntp verify
python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v
docker build --tag loomspan-sidecar:deployment-smoke .
python scripts/verify-deployment.py --image loomspan-sidecar:deployment-smoke
```

On Linux use `./mvnw`. CI and release run these component checks and one deployment
smoke. No external-suite checkout is required. Release immutability checks remain;
this ticket performs no release operation.

From `C:/opendev/code/loomspan-sidecar-test-suite`:

```powershell
.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q
ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py
ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py
.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar
```

The SDK runner always builds root Sidecar components, the SDK reactor, its plain
Java driver and clean Boot servlet reference app, then builds the image. Reports record artifact hashes, source revision
and dirty state, stage/total durations, explicit scenario outcomes and cleanup.
No cached-artifact bypass or recursive deployment smoke is used.

## Ownership inventory

The table describes current owners, not equivalence of every container mechanic.
Component symbols are beneath `src/test/java/ai/loomspan/sidecar`. Composed baseline
scenarios live in the external suite's existing `scripts/run_sdk.py` runtime.

| Existing assertion family | Final owner / disposition |
| --- | --- |
| Image UID/JVM sizing, writable fresh database, production namespace/mounts/grace period, published ingress only | `verify-deployment.py`: image/config inspection and actual startup; no separate image path. |
| Readiness/liveness, anonymous status only, no second listener, TLS ingress, authenticated catalog, missing JWT | Small smoke plus retained `ManagementEndpointIntegrationTest`, `ConfigurationModeIntegrationTest`, `CurrentFileJwtTrustTest`, `JwtSecurityConfigurationTest`, `JwtStartupIntegrationTest` and `ExactRoleExecutionIntegrationTest`. |
| Execution/management credential separation, locked setup, admin token/reset CLI | Existing `ManagementAdminCommandTest`, `ManagementLockedExecutionIntegrationTest`, `ManagementHttpIntegrationTest`, personal-token tests. Remove repeated container CLI/setup/reset collection. |
| Secure session cookie through production HTTPS ingress | Small smoke requests the anonymous login page and requires a Secure `JSESSIONID`; no setup, authentication or browser collection is needed. The component browser tests use insecure cookies for their local HTTP fixture, so they do not replace this assertion. |
| Browser login, invitations, roles, CSRF, mail outage/origin, draft handoff/stale grant, import/history browser | Existing `Management*HttpIntegrationTest`, `ManagementMail*Test`, `ManagementEditorBrowserIntegrationTest`, `ManagementImportBrowserIntegrationTest`, `ManagementHistoryBrowserIntegrationTest`; remove composed browser class and SMTP capture. |
| Authored publication, model/REST execution, generation/snapshot correlation, preserved URL variables | `AuthenticatedExecutionApiIntegrationTest`, `ExecutionConfigurationCorrelationIntegrationTest`, `RestRouteStartupIntegrationTest`, `RuntimeConfigurationIntegrationTest`, plus restored suite [blocking-client](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json) against current trust. |
| Second deployment import/rebinding/credential replacement/rollback/restart | `ManagementConfigurationHttpIntegrationTest`, `CredentialPortabilityIntegrationTest`, `RuntimeConfigurationIntegrationTest`, `RestGenerationIntegrationTest`, `RestRouteRestartIntegrationTest`. Remove duplicate two-deployment choreography, not its component cases. |
| Stopped complete SQLite copy, corrupt selection refusal, restore accounts/history/configuration | `RuntimeConfigurationRecoveryIntegrationTest` and durable restart tests. Explicitly remove Docker-volume copy/chown/replace choreography as test-harness mechanics; smoke still proves fresh UID-owned storage. Operator backup guidance remains. |
| Container SIGTERM: admitted nested work completes with callback clients alive, or bounded Framework cutoff without later detail/finish effects | Suite [shutdown-completion](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json) / [shutdown-cutoff](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json) using existing lane fixture and temporary file configuration; retain Sidecar `RestHandlerLifecycleIntegrationTest` / `ExecutionShutdownIntegrationTest`. Fresh evidence is linked below. |
| Direct localhost HTTP and direct standard Spring PEM HTTPS image reachability | Existing suite [foundation-smoke](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json) plus [deployment-transport-baseline](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json) direct HTTPS variant; no form-login SDK claim. |
| Supplied Caddy TLS positive, untrusted CA rejection, redirect destination, fixed upstream, forwarded-header normalization | Suite [deployment-transport-baseline](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json) supplied-Caddy variant using per-run certs; smoke also uses shipped supplied recipe for auth/health/no-bypass. Fresh evidence is linked below. |
| Static production/supplied Compose topology | Smoke rendered configuration checks. |
| SDK form-login issuance, exact authorities and execution ownership; colocated app restart, current-trust correction, authenticated callbacks with role/record authorization and strict queue/callback expiry without dispatch | External Local group in `scripts/sdk_lane/local.py` and its clean Boot reference app, derived from the actual shipped quickstart Compose; detailed receiver rules belong to the SDK starter component tests. Current evidence is recorded below. |
| Static public/DNS Caddy adaptation | Explicitly remove external Caddy plugin/adaptation-only checks; no Sidecar runtime assertion is lost. Preserve shipped configs and operator guidance; no DNS/ACME execution. |
| Root-key permissions, same leaf/CA across restarts, regeneration after deletion, forced supplied replacement/reload | Delete as certificate lifecycle; no replacement anywhere. |
| Outbound TLS/mTLS selected bundle, wrong/missing client rejection | Retain `RestTransportTlsIntegrationTest`. Future full SDK peer/hostname/expiry and bidirectional no-bypass belong to 8.1.7, not claimed by stub/baseline evidence. |


The small smoke proves image UID/JVM settings, rendered production topology, fresh
writable SQLite, anonymous status-only application health, verified supplied-Caddy
HTTPS, authenticated empty catalog, missing-JWT rejection and external backend
refusal, plus a Secure session cookie on the anonymous login page. It starts once,
performs no authoring/browser/import/recovery/lifecycle or
in-flight shutdown collection, and cleans its uniquely labeled resources on success
or failure. Diagnostics never print credentials, payloads or raw logs; cleanup
failure fails the command. No development-data reset is required.

Certificate lifecycle tests have no replacement: issuance, persistence, regeneration,
forced replacement/reload and CA-state checks were test-tool responsibilities outside
Loomspan. Operator support/guidance for all standard Spring/Caddy options remains.
Temporary certificate rejection is transport validation and remains executable.
Detailed queue/callback expiry stays in `ExecutionCoordinatorTest` and
`GenericRestSkillHandlerTest`; `SupportedLoomspanApiArchitectureTest` continues to
forbid Framework internals/autoconfigure and Java skill dependencies in app/tests.
Framework tests are never substituted for Sidecar integration evidence.

## Evidence and limits

The fresh [Local SDK report](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json)
passes all five selected scenarios and cleanup against current checkout artifact
identities. It is the replacement gate, completed before legacy owners were deleted.
The suite's
`foundation-smoke` / `blocking-client` are plain Java SDK discovery/invocation,
opaque completion, primary REST failure and bounded lost-response/no-replay evidence.
`shutdown-completion` / `shutdown-cutoff` are real TERM baselines with fixture entry,
detail/finish effects and a single 3s Framework budget. `deployment-transport-baseline`
checks direct Spring HTTPS trust/hostname/expiry and supplied Caddy header/redirect/
fixed-upstream/no-bypass boundaries. Independent fixture counters detect effects.

The five retained baselines alone do not establish SDK Spring form-login issuance
or colocated signing/trust/queue behavior; the additional Local group owns those checks.
Those earlier reports do not establish callback authentication/method/business
security; the callback-specific Local evidence below owns that acceptance. Full
Standard/Hardened bidirectional invocation/callbacks, provided-key deployment,
trusted-group rotation and outbound-only coverage remain later-ticket obligations. Standard/Hardened selectors remain
empty and fail explicitly with `no-scenarios-for-recipe` until implemented.

## Cost observations

Historical CI paired `verify-image` with broad production verification after root
components. Source and the prior PR 8.1.4 log establish five Compose projects and
19 initial service starts before recreation, eight nested browser Maven invocations,
109.750 seconds of nested Maven time, and 2.48/4.53 seconds for shutdown observations.
Those are historical measurements, not fresh current results; no old total wall time
was measured. No speedup percentage can be inferred.

The replacement smoke uses one project/two initial services, one volume-preparation
helper and one external-namespace probe, no recreation and no nested Maven. The
separate Local SDK lane uses one project/two initial services, two shutdown Sidecar
recreations, then four transport service starts (eight total service starts), excluding
its Java driver and three ephemeral Python clients. Root component Maven still runs
once per SDK invocation; SDK and driver builds are additional Maven processes.
The Local run above took 453.482s: root components 306.614s, SDK reactor 29.542s,
driver 6.308s, image 9.200s, shutdown-completion stage 18.506s, cutoff stage 18.364s,
transport stage 20.661s and cleanup 21.573s. Within the TERM stages, process exits
were 2.420s (completion) and 3.912s (cutoff), both exit 143/no OOM with expected
fixture effects. All five scenario outcomes are explicitly passed in that report.

The initial shipped-recipe smoke passed in 18.897s. Docker-backed post-start
assertion failure was then injected through the runtime's testable boundary after
real assertions: `controlled-post-start-failure` stayed failed, cleanup passed in
18.063s, and independent project-label listings found no containers, networks or
volumes for `sidecar-smoke-f2a847ebff144790`. No public fault-injection flag exists.

A first complete SDK attempt is retained separately as failed evidence
[`20261018T210749Z-8af2a7427ca7`](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T210749Z-8af2a7427ca7/report.json):
its generated shutdown target omitted mandatory REST limits; old SDK cases and
cleanup passed. Targeted fixture debugging also caught Python 3.13 strict TLS
extension requirements, fixed with standard AKI/SKI/CA key usage. An initial smoke
attempt failed readiness (127.400s), cleaned up, and led to preserving localhost
SNI while connecting to fixed IPv4. These failures are not completion evidence.

Final post-deletion verification: `./mvnw.cmd -B -ntp clean verify` passed in
04:55 Maven-reported minutes with 61 classes / 261 tests and zero failures, errors
or skips, including all three retained Chromium classes, trust/lifetime and the
public Framework API-boundary tests. `clean` removed the deleted composed class's
stale bytecode. The focused `ReleasePreparationTest` also passed; the first focused
attempt caught Windows newline conversion in workflow edits, corrected by retaining
LF content. Existing release immutability checks remain.

The final `docker build --tag loomspan-sidecar:deployment-smoke .` produced image
`sha256:81b070449415babeb6174d824609af882244ec1588f9314dcadee0921e7d9111` from that
fresh JAR. `python scripts/verify-deployment.py --image loomspan-sidecar:deployment-smoke`
then passed every assertion and cleanup, reporting 20.568s. Smoke reported durations
measure inspect/start/assertions/diagnostics/cleanup; per-run certificate/JWT fixture
preparation precedes that timer, so these are not total CLI wall-time measurements.
The suite's 453.482s total includes its complete build/runtime orchestration.

`python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v` passed
seven safety cases; suite pytest passed 48 cases. Ruff checks, both diff checks,
active old-command/class and lifecycle-helper audits passed. Docker label checks
found no leftover formal-run or smoke resources. Hosted CI/release was not executed;
local equivalents above provide current evidence. No provider/live CA/DNS, reset,
operational-data or maintained-reference changes occurred.


## Local colocated recipe evidence

The [earlier Local report 20261018T222714Z-48aad3a2b736](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T222714Z-48aad3a2b736/report.json)
passes all ten selected scenarios and every build/runtime/cleanup stage in
518.105s. It retains the five baselines above and adds the clean Boot
form-login/CSRF consumer, exact authority 403/no dispatch, owner 404/fresh polling,
app-only restart with changed kid/old rejection before expiry/new acceptance,
current-JWKS readiness/auth withdrawal and recovery, and QUEUED original-credential
expiry with the exact failure and zero target effects. No Framework internal API,
provider, business runtime or operational data is used. Detailed parser/mode variants
remain component tests; no per-ConfigurationMode recipe is duplicated.

Source revisions/dirty flags, Sidecar/SDK/plain-Java/Boot hashes and actual shipped
quickstart Compose hash are recorded in that report. Root components passed 61
classes / 261 tests with zero failures/errors/skips. SDK install, both consumer builds,
66 focused harness tests, 176 safe Python tests, Ruff and both diff checks passed.
Independent exact project-label container/network/volume listings and the external
key-volume name were empty after cleanup. No development-data reset was required.

The measured build stages were 309.601s root,
30.38s SDK, 5.823s
plain Java and 6.285s Boot.
Additional Local startup/issuance/restart/trust/queue stages were
11.231/2.907/7.707/3.168/4.696s; Local teardown plus external-volume removal was
21.846s. These are stage durations, not production performance claims.

The initial full attempt [20261018T221808Z-eef7285741cc](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T221808Z-eef7285741cc/report.json)
remains failed at Local readiness: its already-imported initial fixture had an
unsupported connection key, corrected to beta.8 `request-timeout` before the fresh
run. All five baselines and cleanup passed; new Local cases remained not-run.
This is a fixture correction, not a shipped Local guidance defect. Callback
security, full Standard/Hardened bidirectional TLS/mTLS, provided signing and
trusted-group work remain outside this evidence.

## SDK callback authentication and completed Local recipe

The [current Local report 20261018T232143Z-36a99814fb53](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T232143Z-36a99814fb53/report.json)
passes all 12 scenarios and every build/runtime/cleanup stage in 538.779s.
The new callback scenario verifies exact forwarded credentials and authenticated
issuer/subject/authorities, method denial before invocation, application record
denial before effect, and ordinary form login/CSRF alongside SDK authentication.
The admitted planner expiry scenario observes RUNNING and model entry, holds the
captured two-second token past expiry, emits CALL_TOOL to a required-input REST
leaf, and observes exact `execution credential expired` plus zero callback HTTP
arrivals, method bodies and effects through terminal completion and a quiet interval.
It is distinct from the retained queue-expiry regression.

Root components passed 61 classes / 261 tests; the independent SDK passed 25 core
and 30 starter tests. Starter tests own the complete invalid-request/JWT matrix,
exact authorities, chain/session/CSRF isolation, prefix/method/firewall rules,
own-key acceptance, overlap withdrawal/recovery/removal/rate limit and shutdown.
Root `ConfigurationModeIntegrationTest` separately passed all 8 process-JWKS cases.
The final focused harness passed 79 tests and full safe Python suite 189; Ruff and
diff checks passed. Report artifact hashes identify actual rebuilt Sidecar, SDK
and consumers and the shipped quickstart sample. No provider calls, business
runtime, maintained reference data, commit or release was involved; no data reset.

The [first callback attempt](../../loomspan-sidecar-test-suite/evidence/sdk/20261018T230730Z-15907b2c1449/report.json)
is retained as failed: all builds and callback authorization passed, but the expiry
fixture expected a CALL_TOOL after beta.8 auto-dispatched its zero-input leaf.
A dedicated leaf requiring fixed input now forces that model turn; targeted
runtime proof and the fresh complete report passed. Both attempts cleaned their
projects and external key volumes. Full Standard/Hardened recipes and rotation
remain future work.
