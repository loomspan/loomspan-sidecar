# Trusted group, rotation and outbound-only Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-8-trusted-group-rotation-and-outbound-only.md`
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-8-trusted-group-rotation-and-outbound-only.md`
- Outcome: executable composed evidence for one fully trusted two-app group, independent same-issuer JWT trust rotation, continued admitted work, and callback-free invocation in all three recipes, with operator instructions.
- Execution: pipeline mode, approved Full 5-Step Pipeline. This artifact and the testing plan share one planning context. No implementation or tests were run during planning.

**Development policy: destructively replace superseded development contracts; do not add compatibility shims, legacy adapters, dual API paths or migration machinery.** Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). No development-data reset is required or authorized.

Paths prefixed `suite/` below mean the separate repository `C:/opendev/code/loomspan-sidecar-test-suite/`. Other paths are relative to this Sidecar repository. Both repositories remain in ticket scope; the suite's business runtime, credentials and maintained reference packs remain outside it.

## Current State

- `suite/scripts/sdk_lane/remote.py::{configure,composition,Runtime}` consumes the shipped Standard/Hardened fragments, generates temporary provided RSA PEM material and builds one app plus one Sidecar. Its variants have no trusted group, rotation or outbound-only case.
- `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/{ExecutionController,CallbackController,SecurityConfiguration}.java` provides authenticated issuance, fixed SDK operations, an Alice/Bob/literal user directory, role checks and record checks. Callback credential comparison currently assumes the capturing app and receiver share one fixture's memory.
- `suite/scripts/sdk_lane/fixture.py::LocalServer` retains one opaque credential in memory and has a bounded model barrier. `local.py::configure` always installs REST skills/routes, and remote composition always installs callback-facing infrastructure.
- `sdks/java/core/.../SigningKeys.java::initialize` loads one startup signer and derives its public kid. `sdks/java/spring-boot-starter/.../CallbackJwtTrust.java::reload` combines the receiver's startup own key with its independently configured overlap file. `src/main/java/ai/loomspan/sidecar/security/CurrentFileJwtTrust.java` trusts only its current configured file. Both poll every second; unknown-kid requests can trigger rate-limited reads.
- `ExecutionCoordinator` rechecks trust before Framework handoff, not continuously after admission. A model-only barrier can prove continued admitted work without requiring a callback that must later reject the old key.
- `ConfigurationModeIntegrationTest::processJwksIsTheOnlyTrustInBothConfigurationModes` already proves file authority in file/database application modes. The new composition is not duplicated by mode.
- `suite/scripts/run_sdk.py` always builds the actual Sidecar, SDK, consumers and image before running selected scenarios. Its exact schemas and failure/cleanup reporting must remain strict.

## Desired End State

Standard carries a single new `trusted-group-rotation` variant. Two independently running reference app instances share the same initial provided signing store, issuer `loomspan-app`, callback audience, in-memory directory definition and exact role vocabulary. App B submits through the real SDK to the shared Sidecar; callbacks go to app A. Both apps can read the same owner's results; Bob cannot read Alice's records, and literal/Bob callback failures still have zero unauthorized effects.

One staged rotation adds a replacement public key at each independent receiver, restarts both signing apps on replacement private material, preserves old credentials during overlap, then removes the old public key independently. Old credentials remain unexpired during rejection checks. Already admitted model work completes after old trust is withdrawn.

Local, Standard and Hardened each get one focused `outbound-only` scenario using a non-web SDK application, a model-only skill and no REST routes or callback infrastructure. Invocation retains each recipe's signing and outbound transport: Local colocated loopback, Standard verified Spring HTTPS, Hardened app-side outbound Caddy mTLS to Sidecar ingress. There is no browser or callback listener in the non-web app.

## Scope

### In scope

- Separate-suite fixture/composition, safe credential probes, exact report observations and harness regression tests.
- Real provider-free composed runs of all three recipes against the current pinned beta.8 Sidecar checkout.
- Sidecar operations/remote recipe/verification documentation and suite SDK lane evidence inventory.

### Out of scope

- Production Sidecar/SDK trust implementation changes unless actual failing evidence establishes a defect and the plan is updated accordingly.
- New Framework SPI, internal imports, private-key reload, uninterrupted rotation, TLS certificate lifecycle, multi-issuer trust, replica coordination, group isolation or a group deployment matrix.
- Repeating security negative cases for each outbound-only variant, duplicating compositions by ConfigurationMode, paid/provider calls, existing business lanes, commits, pushes or releases.

## Active Project Guardrails

- Reuse public Framework `ai.loomspan.api` and supported REST handler SPI only; preserve the existing ArchUnit boundary test. Consult the matching local Framework `v1.0.0-beta.8` tag when needed. Preserve dependency/version pins.
- Trusted identity comes from authentication. The trusted group has no cross-app isolation; applications can assert each other's identity/roles and access group data.
- Each receiver owns its current-file trust and independent adoption; startup signing is immutable. The callback receiver's own startup key is not revoked by removing it from overlap, so it must restart on replacement signing material before old-key retirement.
- Publish whole valid public-only JWKS atomically. No database or management trust path.
- Framework remains execution/lifetime authority. Do not rebuild its policy in the harness or infer Sidecar behavior from Framework tests.
- Keep credentials out of reports, CLI arguments, model inputs, logs and business output. Fixed fixture probes return only statuses, booleans and counters; credentials survive app restart only in isolated fixture process memory.
- Keep tests proportional: one Standard group/rotation composition and three small outbound-only cases. Preserve mandatory build, secret-free failure reporting and unique-project cleanup.

## Impact and Risk Analysis

The main risk is a misleading rotation oracle. An issuer restart rewrites its exported JWKS, and the callback startup own key remains implicitly trusted. Therefore operator-controlled Sidecar trust and callback overlap must be distinct from per-app SDK exports; restarting apps must not overwrite those trust sets. Add/remove one receiver's file at a time and probe the other unchanged receiver before its update. Use HTTP validation through the real receivers rather than direct calls to trust reload methods.

Keep old/new probe credentials in separately named fixed memory slots; normal callback comparison cannot replace the saved old token. Validate candidate signatures through HTTP acceptance, and compare only public header kid/expected signer kid and signed identity in the fixture. Do not treat an unverified decoded header alone as proof of key adoption.

Every reload observation has a monotonic deadline of five seconds from completed atomic publication: the documented one-second polling interval plus a bounded scheduling/network allowance. Report polling and observation limits separately; do not promise an exact one-second HTTP cutoff. Unknown-kid probes may accelerate adoption, so observed latency proves eventual bounded adoption, not the scheduler's exact phase. Existing component tests own scheduled-loader details.

The model barrier's existing 30-second request budget cannot span two Spring restarts reliably. Admit the old-key blocker after the app restarts, while old-key trust still overlaps, immediately before retirement; withdraw trust and release within its bounded model deadline. Fresh replacement-key owner polls then prove completion.

## Implementation Approach

Reuse existing remote configuration/material/runtime helpers, adding bounded branches for the two new variants. Use a second instance of the existing tiny reference application rather than a separately maintained app codebase. Run the two independent JVM services in a stable app namespace (owned by a minimal sleep-only `app-network` container with DNS alias `app`) on distinct HTTPS ports (A on 8443, B on 8445); both retain the existing app certificate and verified Sidecar HTTPS client. The probe reaches B as `https://app:8445`, covered by the existing app certificate SAN. This keeps credential capture/comparison on the existing shared loopback fixture, avoids new fixture transport and still exercises B-to-Sidecar-to-A across the actual Standard HTTPS boundary. Give each app a unique public export path, sharing the provided private signing store and directory configuration; SDK exports never serve as mutable receiver overlap stores in this scenario.

For pre-restart new-key acceptance probes, prepare an opaque candidate execution credential using public SDK signing/issuance APIs in a disposable app-owned helper inside the app signing boundary. It loads candidate provided material explicitly once, emits only the public candidate JWKS to a separate export, and captures its credential through a fixed fixture HTTP endpoint. It does not change the running application's signer or trust beans. Keep the helper narrow (a new `RotationProbe` Java fixture entry point in the reference-app package is suitable); it must never print a credential. Authenticate fixture issuance identity explicitly and use the same issuer/audience/role settings. This enables rejection before add, acceptance after add and preservation of the ordered add/restart/remove procedure without inventing private-key hot reload.

Extend the credential fixture with fixed old/candidate/current slots and fixed Sidecar discovery, old-key blocker submission and callback-owned probes. Targets come only from generated recipe configuration, never request URLs. Both app JVMs capture/compare against the existing `127.0.0.1:8090` fixture in their shared app namespace. No credential is relayed through a new external fixture listener. Keep raw credentials inside the private test composition.

For outbound-only, add a fixture startup runner using `ExecutionTokenIssuer` with a fixed authenticated fixture identity and `SidecarClient`, selected only for the non-web fixture configuration. Gate servlet-specific app controllers/security with Spring's standard servlet condition. Start signing/export before Sidecar readiness, then bounded discovery readiness, one model submission and owner polling. Do not retry submissions. Emit one exact secret-free completion record and exit cleanly. No production starter callback-disable option is needed: its servlet callback auto-configuration does not run in a non-web application.

### Implementation adaptation: stable namespace across signer restarts

Compose restart of the namespace-owning app also restarts namespace-dependent
services, losing the fixture's saved old credential. The Standard group therefore
uses a sleep-only namespace owner with DNS alias `app`; both app JVMs and the
memory-only fixture join that stable namespace. Restarting the signing apps
leaves the fixture process and its saved old/candidate slots intact. This adds no
listener or trust path and remains one representative Standard composition.

### Implementation adaptation: remote model barrier

The model fixture runs in Sidecar's namespace, while credential capture and the
rotation driver run in the app namespace. Their loopback `/status` endpoints
therefore observe different processes. For this one group scenario, bridge the
existing bounded model barrier with fixed `entered`/`release` marker files in a
temporary directory mounted only in the two fixture containers. No credential
crosses this directory, no external fixture listener is added, and the model still
waits at most 30 seconds. Host failure cleanup always publishes release. This is a
fixture adaptation; real Framework RUNNING and exact terminal output remain the
admission/completion oracles.

## Phase 1: Standard trusted group and independent rotation

### Changes

- [x] `suite/scripts/sdk_lane/remote_material.py::generate` — generate initial/replacement temporary provided signing material and public-only key sets; preserve existing TLS fixtures and private mount isolation. Retain PEM fixture storage from PR 8.1.7 rather than adding a new keystore format matrix.
- [x] `suite/scripts/sdk_lane/remote.py::{VARIANTS,configure,composition,Runtime}` — add Standard `trusted-group-rotation`; independent app B/config/export; distinct operator-owned trust/overlap paths mounted by directory for atomic replacement; narrow methods for public file publication, signing-material selection/recreation and independent restart/readiness. Cleanup covers every new helper/service/volume after partial startup and failure.
- [x] `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/ExecutionController.java` and new adjacent `RotationProbe.java` — fixed probe issuance/capture and public kid comparisons with public SDK APIs, shared loopback capture destination, normal authenticated caller identity and unchanged role checks. Replacement app configuration selects only replacement provided material at restart.
- [x] `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/CallbackController.java` — retain fixed loopback comparison, issuer/subject, method authorization and effect counters; the two-app composition requires no new callback contract.
- [x] `suite/scripts/sdk_lane/fixture.py::{LocalServer,LocalHandler}` — old/candidate/current in-memory credential slots, fixed validation probes and saved-old blocker admission. Retain no arbitrary forwarding, access logs or token output.
- [x] New `suite/scripts/sdk_lane/rotation_probe.py` (or a focused adjacent module) — ordered group and rotation assertions using real HTTP receivers and bounded waits. Mount it into the isolated driver in `remote.py`.
- [x] `suite/scripts/sdk_lane/remote_contract.py::observations` and `suite/scripts/run_sdk.py::{RECIPES,run_remote}` — exact typed observations for cross-app success, role/record/owner denial, independent add/remove adoption, restart kid changes, overlap validity, unexpired old-key rejection and admitted-work completion; separate named stages for material changes/restarts/probes where practical.
- [x] `suite/tests/test_sdk_remote.py` and `suite/tests/test_sdk_runner.py` — focused composition, sequencing, credential-safety, schema and failure-cleanup regression checks.

### Automated verification

- [x] `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q` in suite — all harness tests pass, including intended preimplementation failing group composition/schema test.
- [x] `.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar` in suite — fresh build, all existing six cases and new group/rotation pass with exact observations and cleanup.

### Optional developer checks

None. Real production rollout/key delivery is not needed for acceptance.

### Ordered composed rotation oracle

1. Both apps start on old shared signing material; capture old credential and candidate credential separately. B issues Alice execution; Sidecar accepts; A receives callback with exact issuer/subject/roles and captured credential equality. Run literal/Bob callback denials and Bob result 404. Both apps' public exports identify the same initial kid.
2. Confirm old credentials accepted at both receivers, and candidate credentials rejected at both before addition. Callback success probes include fixed credential comparison setup; count deltas account for deliberate probe effects.
3. Atomically add candidate public key to Sidecar trust only. Within five seconds candidate discovery succeeds, while unchanged callback receiver still rejects candidate. Old probes stay accepted.
4. Atomically add candidate to callback overlap. Within five seconds candidate callback succeeds, Sidecar remains accepting, old works at both. No signer has switched.
5. Deploy replacement private signing material and restart both app instances independently. Reauthenticate browser sessions; verify both SDK public exports and freshly captured/accepted tokens use the replacement kid and same issuer/audience. Old probes remain accepted under the two-key overlap.
6. Submit old-key model-only work using the saved credential, wait for model barrier entry and actual RUNNING. Only now begin retirement.
7. Atomically remove old key from Sidecar trust; require old discovery 401 within five seconds and old callback still successful at unchanged receiver. Replacement key remains usable. Work remains RUNNING.
8. Atomically remove old key from callback overlap; require old callback 401 within five seconds, with no body/effect for the rejected request, and Sidecar still rejecting. Verify replacement callback acceptance. Check old token has not expired; fail on expiry or excessive total scenario duration.
9. Release blocker; poll with fresh replacement-key Alice credentials and require exact COMPLETED model result. This proves old trust withdrawal did not cancel already admitted Framework work. Always release barriers in failure cleanup.

## Phase 2: Focused outbound-only configurations

### Changes

- [x] New `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/OutboundOnlyRunner.java` — non-web fixture runner with public SDK issuer/client; fixed fixture identity, bounded readiness, one `localModel` submit, exact result and clean close. No callback beans/server are active.
- [x] `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/{SecurityConfiguration,ExecutionController,CallbackController}.java` — apply servlet conditions so the same jar supports a genuinely non-web startup without changing servlet behavior.
- [x] `suite/scripts/sdk_lane/local.py::{configure,composition,Runtime}` — dedicated model-only outbound-only composition deriving quickstart, empty routes (`targets: {}`, `routes: {}`) or shipped default empty route resource, one model skill, colocated signer/public trust and no callback fixture/controller listener. Keep the existing Local composition/scenarios.
- [x] `suite/scripts/sdk_lane/remote.py::{VARIANTS,configure,composition,Runtime}` — one outbound-only variant per remote recipe; same model-only manifest/empty routes. Standard retains verified HTTPS client settings but app has no inbound server. Hardened retains app outbound :9080 and Sidecar mTLS ingress, omits app callback :8443/browser :8444 and Sidecar outbound callback :9080 blocks. Validate generated Caddy configs before startup.
- [x] `suite/scripts/run_sdk.py::{RECIPES,run_smoke,run_remote}`, `suite/scripts/sdk_lane/{local_probe,remote_probe,remote_contract}.py` — exact scenario result, process success and executable listener absence checks from app namespace/outsider as appropriate. Require invocation plus absent listeners and empty callback configuration; a model result alone is insufficient.
- [x] `suite/tests/test_sdk_{runner,remote}.py` — verify no REST manifests/routes/targets, no callback ingress or reverse outbound block, preserved invocation TLS/mTLS topology and strict observation/cleanup handling.

### Automated verification

- [x] Focused pytest above; Ruff formatting/lint over affected runner/modules/tests.
- [x] All three `scripts/run_sdk.py` commands — each outbound-only case completes using its recipe configuration with no callback listener; existing cases still pass. Final runs can follow Phase 3 documentation changes so reports describe final implementation inputs.

### Optional developer checks

None.

## Phase 3: Operator instructions and final evidence

### Changes

- [x] `docs/operations.md` — trusted group prerequisites, full mutual-trust/isolation statement; one-second independent current-file polling; add/restart/remove sequence; old own-key caveat; no database/management trust path in either ConfigurationMode; admitted work is not cancelled.
- [x] `docs/remote-recipes.md` — concrete group deployment adaptations, distinct app exports versus operator trust/overlap, provided signer restarts, independent receiver updates and bounded adoption caveat; callback-free Local/Standard/Hardened adaptations including exact Hardened blocks to omit and invocation blocks to retain. Explain non-web SDK use and omit callback routes/REST skills when no callbacks are needed.
- [x] `docs/verification.md` and `suite/docs/sdk-lane.md` — actual fresh reports, source revisions/dirty flags and artifact hashes, new scenarios/results, failures and limits, zero paid calls and cleanup evidence. Do not copy old framework reports as this ticket's evidence.
- [x] Ticket acceptance checkboxes — mark only after executable proof exists; preserve separate evidence ownership and no-feature Phase 2 boundary.

### Automated verification

- [x] Full safe suite Python tests, Ruff and both repository `git diff --check` pass.
- [x] Fresh Local/Standard/Hardened reports contain all selected passed scenarios and stages, build identities, new strict observations and successful cleanup; independent exact project-label container/network/volume absence checks pass.
- [x] Scan changed files/reports for bearer/JWT/private key material without printing matching content; only sanitized diagnostics retained. No private fixture material is tracked.

### Optional developer checks

None. Production-specific rollout is outside verification scope.

## Test Strategy

Cheap harness tests establish composition, sequencing, exact schemas, redaction and failure cleanup. Real isolated Docker recipes establish composed authentication, authorization, independent trust and lifecycle behavior; no mock result can satisfy these criteria. Existing root configuration-mode, current-file trust, architecture and SDK callback tests remain regression gates in mandatory builds. Add no broad duplicate production component tests unless implementation changes those paths. The accompanying testing plan specifies commands and exit criteria.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Two apps, shared issuer/key, shared Sidecar and accepted callbacks | Standard group composition, shared provided store, separate SDK exports, B issuance to A callback | Group execution completes, callback identity/audience/credential checks, same public kid |
| Unauthorized group users denied | Existing app method/record checks plus group driver | Literal method 403, Bob callback record 403 with no effects, Bob result 404 |
| Independent add/restart/overlap/remove rotation | Distinct current JWKS/overlap, candidate helper, explicit restarts and monotonic probes | Candidate rejected before add, staged acceptance, new kid after restarts, old overlap acceptance, staged unexpired old 401 within five seconds |
| Admitted work survives rotation | Existing model barrier and saved old credential | Old-key work RUNNING before retirement, COMPLETED exact model result afterward |
| Only JWKS trust paths in both modes | Current file-authority component test; no management/database calls in composed probe | Mandatory root test for both modes plus harness fixed file-only sequencing checks |
| No-callback invocation in all recipes | Non-web SDK runner, model-only manifest, empty routes, omitted callback infra | Three actual recipe passes plus executable listener absence and strict configuration checks |
| Operator guidance | Operations and remote recipe docs | Review against executable setup and public configuration keys |
| Secret-free versioned scenario evidence | Runner stages/contracts/identities and evidence docs | Strict schemas, redaction regressions, fresh reports with revision/hash/result and cleanup |

## Risks and Rollback/Recovery

Fixture changes are reversible through normal source edits; no persistent/deployed data is modified. Always tear down only each fresh unique SDK Compose project and its explicitly owned external volumes/helpers. Retain failed evidence reports, and fix then rerun affected acceptance rather than editing failed results. If a real public-contract defect emerges, record evidence and update plans before production changes; do not access Framework internals. A malformed operator file withdraws trust and correction restores it under the documented existing behavior; this ticket need not repeat PR 8.1.5 recovery cases.

The planned helper and composition details are routine fixture choices, not new production extension contracts. Keep their APIs fixed and narrow; avoid a generic deployment/key-rotation orchestration layer.

## References

- Ticket and research listed above; `ai/thoughts/design-lens.md`.
- `ai/thoughts/phases/2026-10-15-sdk-v1-signing-trust-reference.md`, deployment recipes and acceptance matrix.
- Public SDK `SigningKeys`, `SigningSettings`, `ExecutionTokenIssuer`, `SpringExecutionTokenIssuer`, `SidecarClient`; current-file loaders and `ExecutionCoordinator`.
- Suite `scripts/run_sdk.py`, `scripts/sdk_lane/{local,remote,remote_material,remote_contract,fixture,local_probe,remote_probe}.py`, reference app and SDK harness tests.
- Shipped `examples/quickstart/compose.yaml` and `examples/remote/{standard,hardened}`; `docs/{operations,remote-recipes,verification}.md`.


## Step 4 verification handoff

All implementation and automated verification checkboxes above are backed by
[implementation evidence](../validation/2026-10-18-pr-8-1-8-trusted-group-rotation-and-outbound-only.md)
and its adjacent exact-command cleanup audit. Final full Local/Standard/Hardened
reports pass 13/8/9 cases. The initial failed Standard attempt remains retained.
No development-data reset was required. Independent Step 5 review is pending.
