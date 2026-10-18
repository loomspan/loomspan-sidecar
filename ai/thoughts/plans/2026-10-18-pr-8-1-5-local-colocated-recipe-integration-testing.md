# Local colocated recipe integration Testing Plan

## Change Summary

Add a clean Spring Boot servlet SDK consumer and composed Local colocated scenarios to the external SDK lane. Retain completed/failed/lost-response, real TERM and deployment-transport baselines. Production SDK/Sidecar contract changes are not planned.

Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md`. Research: `ai/thoughts/research/2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md`. Implementation plan: `ai/thoughts/plans/2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md`.

**Replace superseded development contracts directly, without compatibility machinery; apply the [design lens](../design-lens.md).** No development-data reset is required. Tests create only unique temporary SDK resources and preserve external business apps, credentials, `.runtime` and maintained reference packs.

All `sdk/`, `scripts/`, `tests/` and SDK-lane documentation paths below belong to `C:/opendev/code/loomspan-sidecar-test-suite` unless explicitly labeled Sidecar. Both pipeline plans and ticket/evidence ownership updates belong to Sidecar. Framework source remains read-only, referenced at beta.8 when necessary.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Shipped topology | Recreated test config could accidentally use file trust/bridge networking | Load real quickstart Compose; assert inherited loopback/colocated/mount/UID settings and run actual shared namespace |
| Authentication | Fixture might bypass form login, CSRF or authenticated identity | Real session/form/CSRF flow and unauthenticated/CSRF-negative requests; no subject/role request inputs |
| Literal roles/ownership | Prefix mapping or application filtering could hide Sidecar decisions | Exact allowed/mismatched authority users; Sidecar 403 and 404 propagated as sanitized HTTP statuses |
| Restart/trust | Expiry or readiness races could masquerade as trust adoption | Long-lived captured credential initially accepted; changed kid, old 401/new 200; bounded readiness/auth polling |
| Queue lifetime | Fresh polling could replace captured credentials or failed work might dispatch | Explicit QUEUED state, short original lifetime, fresh polls, exact failure and separate zero effect counter |
| Fixture confidentiality | Captured credentials or failures might reach reports/logs | In-memory capture; real local handler sentinel tests; fixed reports and no raw command output |
| Runtime lifecycle | Startup deadlock/leaked external key volume | Started dependency, entry/release events, failure/interruption cleanup tests and real project-label cleanup inspection |
| Existing lane | New setup could break plain Java or beta.9 business fixtures | Retained five scenarios, full Python test suite, no business runtime mounts or source edits |

## Existing Coverage and Environment Constraints

Sidecar `CurrentFileJwtTrustTest`, `JwtStartupIntegrationTest`, `ExactRoleExecutionIntegrationTest`, `ExecutionCoordinatorTest`, `ConfigurationModeIntegrationTest` and supported-public-API architecture tests already cover detailed receiver rules. SDK issuer/signing/Spring/client tests run through the SDK reactor. Their passes are component coverage, not substitutes for the new composed assertions.

External `tests/test_sdk_runner.py` uses pytest, fake command boundaries, actual Python loopback servers, httpx, counters and threading events. Existing `fake_lane`, `checkout`, report/secrecy tests and fixture tests should be extended proportionally. Keep `standard` and `hardened` empty-selector failures until their own tickets. Do not duplicate detailed JWT parser variants or introduce browser automation for a form/CSRF flow that httpx can exercise directly.

Required tools: suite `.venv`, pytest/httpx/PyYAML/cryptography, Ruff, Java 21 via `JAVA_HOME`, Maven, Docker/Compose, installed/resolvable Framework `1.0.0-beta.8`, and Chromium used by Sidecar's root component tests. Public dependency/image downloads may occur. Provider credentials are unnecessary; child tools retain existing environment allowlisting and the Local runtime has an internal network. The runner builds Sidecar once, installs SDK once, builds both consumers and reuses one image; no cached-build bypass or Framework rebuild is added.

## Failing Test First
- Name: `test_local_recipe_builds_boot_consumer_and_records_colocated_scenarios`.
- Type: focused orchestration/acceptance registration test.
- Location: external `tests/test_sdk_runner.py`.
- Arrange/Act/Assert: extend the existing fake-lane setup with a Boot artifact and Local result boundary; invoke `runner.main(local)`; require the reference-app Maven build with the actual SDK version, its SHA-256, and explicit new Local results for authenticated issuance, exact roles, ownership, restart, trust recovery and queue expiry. Existing five baseline results must remain present.
- Expected pre-fix failure: current runner never builds the Boot consumer or selects/runs/records these scenarios. Require the test to fail on the missing capability before orchestration implementation; an invalid fake setup/import failure is not evidence.
- The first real runtime acceptance attempt must also fail until the Boot consumer/topology/scenarios exist. Do not spend a full build solely to rediscover a missing fixture. The focused red test is the initial executable missing-capability proof.

## Tests to Add or Update

### 1. `test_local_recipe_builds_boot_consumer_and_records_colocated_scenarios`
- Type: pytest orchestration regression.
- Location: `tests/test_sdk_runner.py`.
- Proves: retained baseline selection/order, one component/SDK build, dynamic SDK version propagation into clean Boot consumer packaging, app artifact identity and explicit composed outcomes. Suite source identity and shipped sample hash are included.
- Inputs/fixture: extend existing `checkout` and `fake_lane` with sample Compose input and Boot artifact; keep fake identity values synthetic.
- Doubles or boundary isolation: existing `command`, `find_jar` and runtime interfaces; no Docker/Maven needed.
- Edge cases: missing/ambiguous app JAR, incomplete result schema, one false or failed subresult, unavailable source identity, empty future selectors. Missing or partial outcomes must fail rather than be filled as passed.

### 2. `test_local_composition_preserves_shipped_colocated_boundary`
- Type: pytest configuration integration.
- Location: `tests/test_sdk_runner.py`.
- Proves: `local.py` reads the supplied checkout's actual quickstart Compose, preserves source colocated key-directory/loopback settings and Sidecar read-only volume, runs app UID 10001 with its writable key mount, keeps service-started startup and shared namespace, and substitutes only documented fixture values. Source identity changes if the sample changes.
- Inputs/fixture: actual shipped sample for the normal test, plus a temporary mutated sample to prove source consumption rather than a duplicated constant.
- Doubles or boundary isolation: capture generated Compose/commands without starting containers.
- Edge cases: missing or malformed sample fails clearly; fixture composition has no host ports/provider variables/operational mounts/docker socket, no dependency on Sidecar healthy before app starts, and explicit unique external volume initialization/removal.

### 3. `test_local_capture_and_probe_keep_credentials_in_memory`
- Type: actual loopback HTTP fixture boundary test.
- Location: `tests/test_sdk_runner.py`; production fixture symbols in `scripts/sdk_lane/fixture.py`.
- Proves: fixed SDK-shaped capture route accepts a bounded Bearer credential and returns a valid empty discovery response, holds it only in memory, and a separate fixed probe sends it once to the specified fixture Sidecar destination. Probe output contains only status/booleans. Primary application client does not require a proxy or token getter.
- Inputs/fixture: sentinel token/header/body values and a local test HTTP server at an ephemeral loopback port as the fixed Sidecar boundary.
- Doubles or boundary isolation: local HTTP server only; never an arbitrary user-supplied URL, business payload or external service.
- Edge cases: missing capture, unknown path, arbitrary forwarding attempts, oversized body, failure/timeouts, reset/shutdown. Assert response/captured stdout/stderr/reports contain none of the sentinels. Do not call a header-echo endpoint or inspect private SDK fields.

### 4. `test_local_queue_fixture_has_bounded_barrier_and_independent_effect_count`
- Type: actual loopback HTTP/concurrency fixture test.
- Location: `tests/test_sdk_runner.py`; `scripts/sdk_lane/fixture.py`.
- Proves: the blocker records entry before waiting, cannot complete before release, release permits completion, and queued-skill effects use an independent counter. Unknown endpoints cannot affect counts.
- Inputs/fixture: local fixture server, explicit entered/release threading events and fixed request bodies that are discarded.
- Doubles or boundary isolation: no model/provider; deterministic fixed model reply.
- Edge cases: bounded barrier timeout and reset between scenarios; shutdown/release on test cleanup; no sensitive request logging. Avoid wall-clock sleeps as the latch assertion.

### 5. `test_local_failures_and_interruptions_clean_unique_resources_without_secrets`
- Type: parameterized pytest orchestration/fault tests.
- Location: `tests/test_sdk_runner.py`.
- Proves: each meaningful new runtime boundary reports its own failure, leaves later scenarios not-run, attempts teardown and removes its exact external key volume. Cleanup failure fails a previously successful run and never hides a primary failure/interruption.
- Inputs/fixture: existing failure sentinel strings; fake startup/login/restart/trust/queue/cleanup boundaries.
- Doubles or boundary isolation: command boundary only; no real operational resources.
- Edge cases: volume created but startup fails; app restart fails; corrupt file correction fails; queue barrier blocked; cleanup and primary failure both fail. Raw subprocess/HTTP/exception bodies never enter durable report or console. Inspect actual generated report structure rather than only helper return values.

### 6. `local` composed scenario group: form authentication, SDK issuance, exact roles and ownership
- Type: real Boot/SDK/Sidecar integration through `scripts/run_sdk.py local`.
- Location: `scripts/sdk_lane/local.py`; consumer `sdk/reference-app`.
- Proves: actual starter auto-configuration, servlet dependencies, colocated startup export, Spring form authentication, SDK issuance, direct loopback SDK client and real Framework skill authorization.
- Inputs/fixture: one temporary file-configuration Sidecar; public fixture skill requiring `ROLE_ORDER_EDITOR`; Alice/Bob have exact required authority and negative user has `ORDER_EDITOR`; issuer allows both literals. App allows only fixed skill choices and standard execution UUID reads.
- Actions/assertions: anonymous request requires login; obtain real login-page CSRF token and submit credentials; reacquire post-login CSRF because authentication changes it; CSRF-free POST denied; Alice's CSRF-protected submission accepted and COMPLETED with expected fixed result; negative user receives Sidecar 403 and no added dispatch/effect; Bob gets Sidecar 404 on Alice's execution; a new Alice request issues a fresh token and reads that execution successfully. Assert ownership denial at Sidecar, not an app owner table.
- Doubles or boundary isolation: deterministic model/REST fixture only; actual app security, starter, signing, SDK transport, Sidecar and Framework are real.
- Edge cases: all HTTP/session state remains in memory, no credentials in CLI/report. Login failures fail a fixed category, not a raw page dump. Detailed prefix/parser variants remain in components.

### 7. `local` composed scenario group: app restart and current trust correction
- Type: real process/filesystem/trust integration.
- Location: `scripts/sdk_lane/local.py`, fixed credential capture/probe fixture.
- Proves: SDK startup publishes a new public kid atomically on app-only restart; Sidecar adopts new current keys without retaining old; broken trust withdraws readiness and authentication and correction recovers both.
- Inputs/fixture: a captured SDK-issued Alice credential with at least 120s normal lifetime, fixed identity issuer, unique prepared public-key volume; no persisted private key or bearer file.
- Actions/assertions: prove captured old credential accepted before restart; record only kid-change boolean/public hash; restart host alone, re-login, wait for new startup export; bounded poll until old probe returns 401 and new authenticated app request returns successful discovery/read. Then capture a current credential, inject malformed/empty JWKS using a disposable operator helper, await readiness 503 and credential 401, atomically restore valid public file and await readiness 200 and credential 200. Assert status-only readiness bodies.
- Doubles or boundary isolation: process restart is real, trust file is real, SDK/Sidecar signing/verification is real; fixture only retains/probes the old credential and operator helper changes public data.
- Edge cases: fixture/Sidecar are not restarted; readiness is polled independently of authentication. Use bounded 10s reload/recovery windows with approximately 3s requests, not a fixed sleep or assumption of simultaneous adoption. Reject unexpected HTTP statuses/timeouts as fixture failures. Old rejection must occur before normal token expiry can explain it.

### 8. `local` composed scenario group: original queued credential expires before dispatch
- Type: real queue/SDK-issued lifetime integration.
- Location: `scripts/sdk_lane/local.py`, reference-app short-lifetime operation and Local deterministic fixture.
- Proves: fresh poll tokens do not renew queued work and Sidecar's strict handoff expiry prevents any queued target effect.
- Inputs/fixture: one worker with sufficient queue capacity; first fixed model skill held at fixture entry/release; second authorized fixed REST effect skill, short SDK-issued token lifetime two seconds and independent dispatch/effect counter zero. Short issuer uses same startup `SigningKeySource`, authenticated Spring identity and exact allowlist.
- Actions/assertions: submit blocker, await entered; submit short-lived second execution, read and require QUEUED with a fresh normal owner token; wait at least two seconds plus margin after successful submission (therefore after issuance), continuing fresh poll authentication if needed; release blocker; await first terminal success and second terminal FAILED. Assert exact message `execution credential expired before start`; require queued effect counter zero before and after recovery and no later increment across a bounded quiet observation.
- Doubles or boundary isolation: no coordinator injection/mock clocks/reflection and no Framework internals. Real handoff path; deterministic HTTP fixture supplies only barrier and observable effects.
- Edge cases: fail explicitly if second work is not observed QUEUED; do not rely on receive-JWT expiry because reception permits 60s skew; no resubmission on errors. Barrier timeout has a 30s ceiling and cleanup always releases it. Long normal tokens permit fresh polling through the entire scenario.

## Safe Verification Commands

Run from `C:/opendev/code/loomspan-sidecar-test-suite`:

- Focused red/green: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -k local -q`.
- Related suite: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`.
- Formatting: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- Lint: `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- Consumer packaging during development, after SDK install: `mvn -B -ntp -f sdk/reference-app/pom.xml -Dsdk.version=0.1.0-SNAPSHOT clean package`. The integrated runner must derive the real current SDK version; this explicit value matches current source.
- Full safe Python suite: `.venv/Scripts/python.exe -m pytest tests -q`.
- Required composed gate: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`. This includes root `clean verify`, SDK `clean install`, retained consumer and new Boot builds and all retained/new runtime scenarios; no redundant separate root build is required.
- From both repository roots: `git diff --check` and inspect `git status --short` / scoped diffs.

Inspect the resulting unique report's exact selected scenario outcomes, per-stage durations/statuses, source identities, Sidecar/SDK/plain-Java/Boot hashes, sample hash and cleanup. Independently list Docker resources by the recorded unique SDK project label and verify that its explicitly external key volume is absent. No runtime inspection command may print raw credentials or payloads. Preserve failed reports as failed evidence with their actual diagnostic; retain the final successful report in the existing evidence format.

If actual integration exposes a production/sample defect, add the smallest meaningful focused regression test in its owning repository, record the plan change, rerun that affected check and regenerate any invalidated composed evidence. Otherwise avoid adding duplicate Sidecar tests for behavior unchanged by this ticket.

## Optional Developer Checks

None. All ticket criteria have executable evidence; no interactive browser, real login provider, paid provider, TLS lifecycle, release or hosted CI observation is required.

## Exit Criteria
- [x] The planned initial test fails before implementation for missing Boot/Local capability, not broken test setup.
- [x] New/updated focused fixture and orchestration tests pass, including secrecy and failure cleanup.
- [x] Boot app packages using the locally built starter and explicit servlet/security application dependencies; no Framework embedding/internal imports are introduced.
- [x] Full safe Python suite and Ruff checks pass.
- [x] One fresh integrated Local report passes all existing and new scenarios, components/builds and cleanup with recorded artifact identities.
- [x] Actual login/CSRF, literal role denial, different-owner 404/fresh-owner success, old/new key adoption, trust withdrawal/correction and exact queued expiry/non-dispatch assertions all pass.
- [x] Scope/resource inspections confirm no provider access, operational data changes, retained fixture replacement or leftover runtime/unique key volume.
- [x] Ticket/maintainer/external SDK-lane documents link actual evidence and preserve callback/TLS/group limitations.
- [x] Required acceptance criteria map to executed evidence; component source presence and old reports are not represented as new passes.
- [x] Any optional/nonexecuted observation is reported accurately; none is needed for completion here.


## Executed evidence

- Initial focused red failed at missing reference-app build capability (one failure,
  48 deselected), before orchestration implementation. Final focused harness: 66 passed.
- `.venv/Scripts/python.exe -m pytest tests -q`: 176 passed in 26.25s.
  Both specified Ruff checks and both repositories' `git diff --check` passed.
- `mvn -B -ntp -f sdk/reference-app/pom.xml '-Dsdk.version=0.1.0-SNAPSHOT' clean package`
  passed; quotes are required for the manual PowerShell `-D` argument.
- `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  passed with [report 20261018T222714Z-48aad3a2b736](../../../../loomspan-sidecar-test-suite/evidence/sdk/20261018T222714Z-48aad3a2b736/report.json).
  All ten selected scenarios and every stage passed in 518.105s.
- Real Local assertions: form login/CSRF, exact authority success/403, no denied dispatch,
  different-owner 404/fresh owner read, changed kid/old rejection before expiry/new
  acceptance, status-only readiness and auth withdrawal/restoration, observed QUEUED,
  exact expired-before-start failure and zero queued effects including quiet observation.
- Independent report/hash/secrecy assertions and exact project-label resource listings
  passed; the external key volume was independently absent. The initial failed full
  report `20261018T221808Z-eef7285741cc` remains failed, correctly attributed in the plan.
  No optional developer check or unexecuted acceptance gate remains.
