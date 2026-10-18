# Local colocated recipe integration Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md`
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md`
- Outcome: the external SDK runner proves form-login issuance, literal roles, ownership, colocated restart, current-file trust recovery and strict queued expiry using the shipped Local topology.

**Replace superseded development contracts directly; no compatibility shims, legacy adapters or migration machinery.** Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). No development-data reset is required; all new runtime state is temporary and belongs to a unique SDK project.

## Current State

Sidecar owns the SDK under `sdks/java`, pinned Framework beta.8 integration, and the shipped `examples/quickstart/compose.yaml`. That sample supplies the actual colocated trust environment, loopback binding, UID 10001, read-only Sidecar key mount and shared network namespace. Its Python host and initially empty database are not a Boot SDK reference app or published test skill configuration.

The external repository `C:/opendev/code/loomspan-sidecar-test-suite` owns `scripts/run_sdk.py`, `scripts/sdk_lane/fixture.py`, `sdk/compose.yaml`, `sdk/driver` and SDK reports. `run_sdk.Runtime` currently generates file-trust fixtures and drives five established plain-Java/deployment scenarios. `build_checkout` builds Sidecar once, installs SDK once and packages the plain-Java driver. `stage`, `LaneFailure`, `child_environment` and `write_report` already provide bounded, sanitized evidence and cleanup conventions.

`SpringExecutionTokenIssuer.issueCurrent` obtains subject and exact authorities from authenticated server context. `CredentialSnapshot` has no transport getter. `SidecarClient` sends snapshots through supported HTTP operations, preserves HTTP failure status, and never automatically replays submissions. `ExecutionController.execution` returns 404 to a different issuer/subject. `ExecutionCoordinator` checks captured expiry strictly before handoff, while HTTP JWT reception allows 60 seconds of clock skew. `CurrentFileJwtTrust` reloads every second and publishes current trust without last-good fallback.

Initial Sidecar HEAD is `939564ac3a79e083b7cd141a8d795a33b7a2a600`; the sole current dirty input is this pipeline's new research artifact. External HEAD is `1d32501b16dde6736981e44fe82fa1abd28f2389`, initially clean. Framework source is read-only and version-specific lookups use `v1.0.0-beta.8`.

## Desired End State

The `local` selector retains the five existing scenarios and adds one small composed Local app scenario group, with individual observable subresults for issuance/roles, ownership, restart, trust correction and queue expiry. It builds and runs one clean Boot servlet consumer of the locally installed SDK starter. App and Sidecar use direct loopback HTTP and the same pre-created public-key volume; only the app normally publishes signing keys. No provider is reachable and no operational database or retained business fixture is mounted.

Each required scenario has an executable assertion and a sanitized durable result. Reports identify both repository revisions/dirty state, Sidecar/SDK/reference-app build hashes and the shipped Compose source hash. A failure or interruption leaves accurate stage/scenario outcomes and cleans new containers/networks/volumes, including the explicitly external key volume.

## Scope
### In scope
- External Boot reference app, deterministic SDK-lane fixtures, Local composition/orchestration, focused harness tests, build/evidence integration and external SDK-lane documentation.
- Sidecar pipeline artifacts, ticket completion evidence and the existing maintainer verification inventory's current evidence/limits. This is evidence ownership documentation, not a shipped Local guidance correction.
- A Sidecar sample/runtime correction only if an actual defect is reproduced; record such a discovery and adjust its focused verification before editing.

### Out of scope
- Callback JWT or method/business authorization, TLS recipe completion, second app/shared signing, per-ConfigurationMode recipe duplication, provider access, framework rebuilds, commits, pushes and releases.
- Business apps, beta.9 lane, credentials, `.runtime` state and all maintained reference packs in the external suite.

## Active Project Guardrails
- Framework remains execution/role authority; app/test code uses supported public APIs only. Do not add framework internals, reflection, bean replacement, Java skill methods or new SPIs.
- Trusted identity comes from Spring authentication, never request inputs. Exact authorities survive issuance unchanged; allowlisting intersects actual authenticated authorities.
- App restart intentionally replaces ephemeral signing identity; reload is eventually observed with bounded polling, not assumed synchronous.
- Unusable current JWKS stops acceptance/readiness; recovery does not replay work. Fresh owner authentication does not renew captured queued credentials.
- Keep one representative Local recipe and reuse the existing plain-Java/TERM/transport baselines. No compatibility machinery, certificate lifecycle or configuration cross-product.
- External SDK agreement authorizes this provider-free lane while preserving business runtime/data. Keep subprocess environment allowlisting and fixed diagnostics.

## Impact and Risk Analysis

The main implementation risk is accidentally testing reconstructed file trust instead of the shipped colocated settings. Load `examples/quickstart/compose.yaml` itself and derive a per-run Compose document with narrowly documented test substitutions; assert inherited security properties in harness tests and record its hash. Another risk is a readiness deadlock: app starts after Sidecar is started, never after Sidecar is healthy, because app exports initial JWKS.

Restart evidence must retain an old real SDK-issued credential without exposing opaque transport state. Use a fixed fixture capture endpoint compatible with the SDK `listSkills` response. An authenticated app test operation issues through `SpringExecutionTokenIssuer`, then uses a separately scoped SDK client to that fixed fixture destination; the fixture keeps the Authorization value only in process memory. It probes only fixed Sidecar GET paths and returns status/booleans, never headers or credentials. The primary app client still calls Sidecar directly. This avoids adding SDK getters, reflection, private-key persistence or a general proxy. The fixture outlives only the app restart.

Queue expiry must prove non-dispatch, not simply eventual failure. Hold a first execution at an explicit deterministic model barrier with one worker, observe the second execution QUEUED, wait beyond its captured short lifetime, release the first, and inspect both the exact failure message and a distinct queued-skill effect counter remaining zero. Fresh poll credentials are issued independently. Every wait, latch and HTTP call is bounded and cleanup releases barriers.

## Implementation Approach

Create a compact reference application at external `sdk/reference-app`. Use Boot 4.1.0, Java 21, explicit webmvc/security dependencies and the locally installed `loomspan-sdk-spring-boot-starter` version passed by the existing build recorder. Do not embed Framework. Standard form login with in-memory users supplies `alice` and `bob` with `ROLE_ORDER_EDITOR`, and a negative user with `ORDER_EDITOR`. Allowlist both exact spellings so a negative result proves the missing exact authority rather than a hidden mapping. Keep CSRF enabled and exercise the actual login form/session plus CSRF-protected POSTs.

Use a normal authenticated controller to submit fixed fixture skills and read a UUID execution, obtaining a fresh `issueCurrent()` snapshot per operation. Return only test execution IDs, statuses, fixed result/failure fields and sanitized HTTP status. Never expose arbitrary destination URLs or raw exception/problem bodies. Use a normal long issuance lifetime (at least 120s) for restart and trust probes. For queue submission only, construct a private fixture `SpringExecutionTokenIssuer` around the same public `SigningKeySource` with a two-second `IssuanceSettings` lifetime; it still captures current authenticated identity. Do not replace starter beans or change global issuance during a scenario.

Add external `scripts/sdk_lane/local.py` as a focused composition/scenario owner using the existing runtime call/failure/evidence helpers. Derive the new composition from the shipped quickstart document: replace its Python host with the Boot JAR service, omit ingress/host port publishing because the probe lives inside the shared namespace, add the deterministic loopback fixture, use the unique built Sidecar image, add temporary file-mode skill/model configuration and tmpfs database, and replace external volume name with a unique per-run name. Retain original Sidecar colocated/loopback settings and read-only key mount; app runs UID 10001 with a writable mount. Remove irrelevant sample authoring/mail placeholders; pass no host secrets. Keep the runtime network internal. Prepare/chown/chmod the named key directory with the existing image before app startup, using the sample's 10001/0700 convention.

Use a disposable operator helper mounting that one unique key volume for unusable-file injection/restoration, never a second signing app. Save only public JWKS in temporary memory and restore it atomically under UID 10001. Restart only the app with `docker compose restart host`; Sidecar/fixture and their namespace remain running. Verify changed public kid, old credential 401 and new credential 200 after adoption. For correction, await readiness 503 and captured/current credential rejection, restore valid public file, then await readiness 200 and acceptance recovery. Readiness output must remain exactly status-only. Do not use `/headers` or any existing header-echo fixture to capture secrets.

Rejected alternatives: native host-only processes would reconstruct the shipped sample instead of exercising its namespace/mount boundary; replacing the existing baseline runtime would combine unrelated TERM/TLS fixtures with this small recipe; extracting SDK token/private state would expand contracts solely for testing. A separate small Local runtime reuses builds/image and gives this ticket its own assertions without duplicating old scenarios.

## Phase 1: Boot consumer and fixed fixture boundaries
### Changes
- [x] External `sdk/reference-app/pom.xml` — Boot 4.1.0 executable JAR, Java 21, starter coordinate property, webmvc/security dependencies; test dependencies only where focused app tests need them.
- [x] External `sdk/reference-app/src/main/java/ai/loomspan/sdk/example/ReferenceApplication.java`, `SecurityConfiguration.java`, `ExecutionController.java` — startup, exact-authority test users/form login/CSRF, fixed skill submission, fresh owner polling, short queue issuer and fixed capture client operation.
- [x] External `sdk/reference-app/src/main/resources/application.yaml` — loopback 8081, colocated `/keys`, fixed issuer/roles and loopback Sidecar client; disable noisy access/security debug logging.
- [x] External `scripts/sdk_lane/fixture.py` — separate Local fixture handler/server for deterministic model completion, entered/release barrier, effect counters, in-memory credential capture and fixed old-credential probe; bounded bodies/waits, no header/body echo or access logs.
- [x] External `tests/test_sdk_runner.py` — actual loopback fixture tests for fixed-route restrictions, capture secrecy and barrier/counter behavior.

### Automated verification
- [x] From external root: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q` — fixture behavior/secrecy passes, existing runner remains covered.
- [x] From external root after SDK install: `mvn -B -ntp -f sdk/reference-app/pom.xml -Dsdk.version=0.1.0-SNAPSHOT clean package` — clean Boot consumer packages against the current SDK (runner obtains actual version dynamically).

### Optional developer checks
None.

## Phase 2: Shipped colocated composition and composed scenarios
### Changes
- [x] External `scripts/sdk_lane/local.py` — sample derivation, unique-volume preparation, startup with started dependencies, bounded login/CSRF flow and direct SDK scenarios, app restart, trust-file correction, queue barrier/expiry and cleanup.
- [x] External `scripts/run_sdk.py` — extend `RECIPES`, `STAGES`, `build_checkout` and `run_smoke` to package/hash the reference app once and run Local scenarios with explicit pass/fail ownership; retain the existing five baseline scenarios and component build ordering. Record suite revision/dirty state and sample hash; propagate failures without raw outputs.
- [x] External `tests/test_sdk_runner.py` — assert actual sample security settings survive test substitutions, startup ordering, exact scenario outcome validation, build identity, failure/interruption and external-volume cleanup.

### Automated verification
- [x] From external root: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q` — orchestration and failure paths pass.
- [x] From external root: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` — existing formatting/lint gates pass.
- [x] From external root: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar` — root component verify, SDK install, both consumer builds and all retained/new scenarios pass, including cleanup.

### Optional developer checks
None. Login and all contract observations are automated HTTP/container checks.

## Phase 3: Evidence, ownership and ticket closure
### Changes
- [x] External `docs/sdk-lane.md` and `README.md` — document current Local reference app topology, commands, scenario semantics, secret-safe evidence and remaining callback/TLS/group limits; link fresh retained report.
- [x] Sidecar `docs/verification.md` — update the existing owner inventory/evidence limits to the fresh Local report without representing it as callback/TLS completion or changing shipped guidance absent a defect.
- [x] Sidecar ticket — mark only freshly proved remaining criteria complete and link the external report; record actual files/build identities, any discovered deviation and temporary-state/no-reset boundary.

### Automated verification
- [x] From external root: `.venv/Scripts/python.exe -m pytest tests -q` — broadest safe provider-free harness regression suite passes.
- [x] From both repository roots: `git diff --check` — no whitespace errors; inspect status/diff for scope and confirm no credentials, token/key/payload output or business data changes.
- [x] Independently inspect the report — all selected scenario results/stages and cleanup pass; revisions, app/SDK/Sidecar hashes and sample identity are populated. No second full runtime invocation is needed unless implementation changes invalidate evidence.

### Optional developer checks
None.

## Test Strategy

Use actual loopback HTTP for fixture boundaries and fake-command tests only for orchestration/failure cleanup; actual Spring/SDK/Sidecar behavior is proved by the composed run. Keep detailed JWT/parser/role variants in existing SDK/Sidecar tests, covered by runner builds. The testing plan specifies the initial red acceptance assertion, scenarios, deadlines and secrecy checks.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Local uses shipped colocated sample/provider-free | `local.py` derives sample and runs app/Sidecar namespace | Composition assertions plus real Local report/sample hash |
| Form login reaches skill through SDK | App form chain and `issueCurrent` controller | Real login/CSRF, accepted and completed fixed skill |
| Exact-role positive and negative | Exact users and issuer allowlist; required `ROLE_ORDER_EDITOR` skill | Alice succeeds, `ORDER_EDITOR` user gets 403 and no effect |
| Different owner denied; fresh owner polls | Fresh per-request issuance and UUID polling | Bob gets 404; Alice fresh request returns original execution |
| App restart invalidates old/adopts new | App-only restart, external in-memory capture | Changed kid; bounded old 401/new 200 adoption observation |
| Bad JWKS stops/readiness and correction recovers | Temporary operator injection and atomic restore | Readiness 503/auth 401 followed by readiness 200/auth 200 |
| Expired queued token fails/no dispatch | Private short issuer, one worker, barrier/counter | QUEUED observed, exact FAILED message, zero queued effect |
| Existing completed/failed/lost-response scenarios retained | Existing runner methods unchanged | Existing blocking-client result/counters pass |
| Evidence recipe/revisions/builds without secrets | Runner report fields and fixed result schema | Report identity/secrecy/failure tests plus actual retained report |

## Risks and Rollback/Recovery

Bound all startup/reload/login/terminal waits (startup up to 120s; reload/recovery up to 10s; requests approximately 3s; barrier timeout long enough for the measured two-second expiry with a finite 30s ceiling). Never infer adoption from sleep alone. Use admission-time wait margin for queue expiry and count effects after worker recovery. If the fixture times out, fail with a fixed diagnostic and clean up rather than claim a trust/expiry failure.

Cleanup must attempt both runtime teardown and explicit external key-volume removal, preserving a primary failure and failing otherwise successful runs on cleanup errors. Cleanup targets exact unique resource names only. Recovery is rerunning the isolated recipe; no persistent data migration/reset or production action is involved. Record material implementation adjustments in this plan/ticket before review.

## References
- Ticket and research above; [testing plan](2026-10-18-pr-8-1-5-local-colocated-recipe-integration-testing.md).
- `examples/quickstart/compose.yaml`, `examples/quickstart/README.md`, `docs/operations.md`, `docs/verification.md`.
- `sdks/java/core/src/main/java/ai/loomspan/sdk/{CredentialSnapshot,ExecutionTokenIssuer,SidecarClient}.java` and Spring issuer/auto-configuration.
- External `AGENTS.md`, `docs/project-agreement.md`, `docs/sdk-lane.md`, `scripts/run_sdk.py`, `scripts/sdk_lane/fixture.py`, `tests/test_sdk_runner.py`.

## Implementation observations

- Scenario HTTP/session assertions live in a separate small `scripts/sdk_lane/local_probe.py`
  mounted read-only into the shared namespace; `local.py` owns composition and lifecycle.
  This keeps the Docker orchestration readable and retains cookie/CSRF state only in memory.
- The public beta.8 connection budget is `request-timeout`; the Local fixture uses 30s.
  Sidecar queue capacity uses its documented `max-queued` property (four), with one worker.
- Both disposable preparation/operator helpers carry the unique project and helper labels.
  Cleanup explicitly removes any interrupted helper before Compose teardown and key-volume
  removal. A primary scenario failure/interruption remains primary when cleanup also fails.
- The app normal issuance lifetime is five minutes. Restart rejection must finish within
  120 seconds, making ordinary credential expiry insufficient to explain old-token rejection.
- Initial red command: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -k local_recipe_builds -q`
  failed at the missing reference-app Maven build assertion (one failure, 48 deselected).
  After implementation, focused harness verification passed 64 tests. Boot packaging passed
  with `mvn -B -ntp -f sdk/reference-app/pom.xml '-Dsdk.version=0.1.0-SNAPSHOT' clean package`;
  PowerShell requires quotes around the `-D` argument. An initial unquoted manual command
  failed Maven argument parsing before any build; the runner uses structured argv.

- First integrated attempt `20261018T221808Z-eef7285741cc` failed at Local readiness
  after all five retained scenarios passed; root components/builds and cleanup passed.
  Its already-imported initial fixture used unsupported connection `read-timeout`.
  The source was corrected to documented `request-timeout` before a fresh full run.
  Independent label inspection found no container/network/volume leftovers for either
  the baseline or Local project. Preserve this report as failed evidence.
- A provider-free real-container focused check against the first built image and final
  Local harness passed startup, form/CSRF/exact roles/ownership, app restart/new kid/old
  rejection, trust withdrawal/restoration, QUEUED expiry/zero effects and cleanup.
  It is diagnostic executable evidence; the fresh complete runner report remains the gate.
- Final focused harness passed 66 tests; the full safe suite passed 176 tests in 26.25s.
  Ruff format and lint checks passed. No production/sample defect was exposed.

- Fresh complete gate passed: [Local report 20261018T222714Z-48aad3a2b736](../../../../loomspan-sidecar-test-suite/evidence/sdk/20261018T222714Z-48aad3a2b736/report.json),
  518.105s, all ten scenarios and every build/runtime/cleanup stage passed.
  Root components ran 61 classes / 261 tests, zero failures/errors/skips. SDK reactor,
  plain Java consumer and Boot consumer all built; no Framework source rebuild occurred.
- Final report/hash/secrecy assertions verified exact scenario schemas, stage outcomes,
  Sidecar/plain-Java/Boot hashes against current artifacts and sample hash against the
  shipped input. Independent Docker listings by both exact project labels, plus the
  exact external-volume name, were empty. Focused diagnostic resources were also absent.
- Sidecar changes are evidence ownership/closure documentation and this pipeline's
  artifacts only. External changes are `sdk/reference-app`, `scripts/sdk_lane/local.py`,
  `local_probe.py`, the fixed fixture, runner, focused tests and SDK-lane/README docs.
  Business runtimes, credentials, operational data and maintained packs were preserved;
  no development-data reset, provider call, commit, push or release was performed.
