# Standard HTTPS and Hardened mTLS Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md`
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md`
- Execution: pipeline mode, Full 5-Step Pipeline; developer approved proceeding.
- Outcome: executable Standard and Hardened reference-app recipes in the external SDK lane, with operator reference configurations in Sidecar.

**Development policy: replace superseded contracts and fixtures directly; add no compatibility shims, legacy adapters or parallel legacy APIs.** Apply the [design lens](../design-lens.md), especially simplicity and technical debt. No development-data reset is required; no deployed or business data may be deleted.

Paths prefixed `suite/` below are relative to `C:/opendev/code/loomspan-sidecar-test-suite`. All other paths are relative to `C:/opendev/code/loomspan-sidecar`. Plans do not authorize commits, publishing, paid provider calls or changes to the maintained business suites.

## Current State

- `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java`, `build`, already applies the selected Boot bundle SSLContext and retains normal hostname verification; retries and redirects are disabled. `RestRouteLoader` validates named bundles.
- `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java`, `sidecarClient`, resolves `loomspan-sdk.ssl-bundle`; `SidecarClient` disables retries and redirects.
- `suite/scripts/run_sdk.py`, `RECIPES`, has empty Standard/Hardened selectors. `build_checkout` already builds real root/SDK artifacts and both consumers and records hashes. Existing Local execution must remain selected independently.
- `suite/scripts/sdk_lane/local.py`, `composition`/`configure`/`Runtime`, demonstrates scoped projects, temporary settings, public-key-volume ownership, startup sequencing and cleanup. Its shared namespace and colocated signer are specific to Local.
- `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/ExecutionController.java` and `CallbackController.java` already provide form-session invocation, real SDK issuance, callback method and record checks, counters and fixed localhost token capture/comparison. Those localhost operations can remain local by running a capture fixture inside the app namespace.
- `ConfigurationModeIntegrationTest` already proves configured JWKS-file authority in file/database modes. Existing Sidecar `RestTransportTlsIntegrationTest`, SDK `SidecarClientTlsTest` and `SidecarClientAutoConfigurationTest` cover the component TLS facilities; whole remote compositions are missing.
- Only the research artifact was dirty at planning entry; the external suite was clean. Preserve later unrelated changes.

## Desired End State

`scripts/run_sdk.py standard` and `hardened` each build the actual checkout, create new temporary certificates/signing material, run a form-login reference app with invocation and callback, produce strict secret-free SDK evidence and remove their own resources. They retain JWT, exact role and ownership enforcement over accepted transport. Runtime configuration does not recognize recipe names.

The supported configuration primitives remain independently selectable: Boot HTTPS listeners, named Boot outbound SSL bundles, provided signing, Sidecar file JWKS trust and optional fixed Caddy gateways. The recipes are representative tested deployments; mixed or other configurations remain permitted and untested.

## Scope

### In scope

- External runner dispatch, offline certificate/signing fixtures, two remote compositions, probes, loss instrumentation, evidence, cleanup and harness tests.
- Operator examples for both recipes and links from existing Sidecar/SDK documentation.
- A focused Sidecar/SDK production fix only if executable evidence reveals standard TLS wiring is broken, with a regression test at the affected public boundary.

### Out of scope

- Framework changes, dependency/version updates, business suites and reference packs, native Java mTLS recipes, custom meshes, an exhaustive certificate/configuration matrix.
- Certificate services, certificate lifecycle/rotation/reload tests or controllers, transport enums, runtime recipe allowlists, network-proof controllers and insecure verification switches.

## Active Project Guardrails

- Framework remains execution authority; Java production/tests use only the closed `ai.loomspan.api` surface and the authorized `RestSkillHandler` SPI. Preserve the existing architecture test.
- Reuse standard Boot namespaces for SSL; `loomspan-sdk.ssl-bundle` and REST-target `ssl-bundle` select existing facilities. Do not add an alternative transport client.
- Identity comes from form authentication and issuance, never probe business payloads. JWT authentication remains independent of TLS authentication.
- DevOps owns TLS lifecycle; test supplied material and deployment boundaries without lifecycle machinery.
- Keep security material out of logs/reports and prevent automatic replays. No live providers; deterministic fixtures stay local to a protected namespace.
- Preserve the external beta.9 business lane and maintained reference packs. This lane remains on the actual Sidecar beta.8 dependency.

## Impact and Risk Analysis

The principal risks are false TLS evidence (a dead listener can look rejected), bypassable Caddy deployments, plaintext fixture traffic between namespaces, credentials in evidence and unintended retries after an accepted request loses its response. Address these with positive readiness controls, verified TLS probes, in-namespace application counters, external socket probes, explicit fixed upstreams and post-effect response loss.

Provided signing must not silently fall back to colocated signing. Mount a generated PKCS#8 RSA key only into the reference app. Give its SDK an existing writable public-export directory; mount only that public export into Sidecar, configured with `trust.mode=file` and its exact JWKS path. Public delivery through an isolated shared volume models DevOps file delivery, not a network trust service. Sidecar must never receive the signing private key. Sequence readiness after the app has exported a nonempty public JWKS.

Caddy client authentication validates membership in an explicitly scoped group CA and clientAuth EKU; it does not perform server-style DNS-name verification on client certificates. Standard wrong-peer server tests verify SAN matching; Hardened server negatives use an unrelated CA with the correct SAN so Caddy certificate selection does not masquerade as caller verification. Wrong-CA client tests prove group membership rejection; do not claim arbitrary client SAN allowlisting or add a custom verifier.

## Implementation Approach

Use a small shared remote-fixture module plus recipe-specific configuration, rather than copying Local's lifecycle scenarios or introducing a generic deployment framework. New proposed modules are `suite/scripts/sdk_lane/remote.py` (configuration, runtime, supplied Caddy files), `remote_probe.py` (bounded fixed behavior) and `remote_material.py` (offline certificates and provided signer). File splitting may follow size, but preserve these explicit responsibilities.

### Standard topology

- Sidecar Spring HTTPS on `sidecar:8443`; app Spring HTTPS on `app:8443`, each in its own Docker network namespace on the isolated internal network. No Caddy and no client certificates.
- App's SDK trusts the Sidecar CA through a Boot PEM bundle; Sidecar's app REST target trusts the app CA through its own Boot PEM bundle. URLs use certificate DNS SANs and neither client disables hostname verification.
- Sidecar model fixture binds only `127.0.0.1:8090` in the Sidecar namespace. App capture/comparison fixture binds the same port in the separate app namespace. All cross-namespace requests, including browser login, use HTTPS.

### Hardened topology

- `app-proxy` and `sidecar-proxy` are the two network namespace owners, each running pinned `caddy:2.10.2`. App and its capture fixture use `network_mode: service:app-proxy`; Sidecar and its model fixture use `network_mode: service:sidecar-proxy`. No backend publishes a host port.
- App and Sidecar backends bind `127.0.0.1:8081` and `127.0.0.1:8080` respectively. Each Caddy's outbound HTTP listener binds `127.0.0.1:9080` and has one literal remote TLS upstream; request authority/absolute URI cannot select a destination.
- App SDK calls local `http://127.0.0.1:9080`; App Caddy presents its client certificate to `https://sidecar-proxy:8443`. Sidecar Caddy requires and verifies it against the group CA before forwarding locally to Sidecar.
- Sidecar callback target calls its local `http://127.0.0.1:9080`; Sidecar Caddy presents its client certificate to `https://app-proxy:8443`. App Caddy requires and verifies it before forwarding locally to the callback path.
- Browser ingress is separate `https://app-proxy:8444`, with ordinary verified server TLS and form login. It rejects the callback prefix. The mTLS callback ingress permits only the callback prefix. Sidecar's TLS ingress exposes the required execution paths and readiness, not a new management gateway.
- Disable Caddy admin/automatic HTTPS and use supplied cert/key files. Use `client_auth { mode require_and_verify; trust_pool file ... }`, verified upstream transport with supplied CA/client certificate and explicit expected server name. Set `lb_try_duration 0` explicitly; no fallback upstream, redirect or retry mechanism. Validate syntax against the pinned image before starting.

Offline fixtures create a valid group CA plus unrelated CA, valid app/Sidecar server and client leaves, representative wrong-host/expired server leaves and wrong-CA client leaves. All material exists only in a per-run temporary directory/volume; no certificates or private keys are committed. No authority service runs.

## Phase 1: Remote material, dispatch and protected compositions

### Changes

- [x] `suite/scripts/sdk_lane/remote_material.py` — generate per-run PEM CA/leaf material with appropriate EKUs/SANs and a separate RSA provided signing key; keep filenames fixed and secret values out of return/report structures.
- [x] `suite/scripts/sdk_lane/remote.py` — add `SCENARIOS`, `configure`, `composition` and `Runtime` with recipe-specific topology above; use the existing app controllers and fixed `localOwned`/`localLiteralOwned` skills rather than renaming production behavior after recipes. Use Local helpers for stable skill fixture data where practical without modifying Local defaults.
- [x] `suite/scripts/run_sdk.py` — populate `RECIPES`, add remote stages and dispatch after the existing mandatory build path. Build the Docker image for each selected remote run; do not run Local scenarios automatically for remote selectors. Record strict per-scenario results and cleanup; a missing/failed remote result cannot become overall pass.
- [x] Remote `Runtime.start` — prepare public-export directory ownership, start app/capture fixture, verify exported public JWKS, then start Sidecar/model fixture and verify transport-aware readiness. `Runtime.cleanup` must remove only the unique project, helpers and public volume, including startup/interruption failures.
- [x] `suite/tests/test_sdk_runner.py` — replace empty-recipe failure expectations with dispatch/report tests and extend scoped-cleanup checks. Add focused remote harness tests in `suite/tests/test_sdk_remote.py`.

### Automated verification

- [x] From suite: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q` — verifies nonempty dispatch, configuration separation, material contents, startup/error accounting, strict schemas and cleanup.
- [x] Pinned Caddy `adapt`/`validate` invoked by remote runtime — both supplied gateway configurations parse and load their files without automation or insecure TLS.

### Optional developer checks

None. Runtime verification of actual networking is required in phase 2.

## Phase 2: Bidirectional security and no-replay evidence

### Changes

- [x] `suite/scripts/sdk_lane/remote_probe.py` — real form login/CSRF, SDK submission/polling, callback identity/exact roles/credential equality and method/record denial; independent typed result schema, bounded deadlines and no raw token/result dumping. Generalize only browser base-URL plumbing from `local_probe.py` if it avoids copying login logic while preserving Local behavior.
- [x] Remote positive scenario in each recipe — submit `localOwned` as Alice and poll a completed callback result; assert exact issuer/subject/roles, one callback effect and opaque token equality through app-local capture. Bob's owner mismatch and literal authority mismatch remain denied. Show missing/invalid execution JWT rejected with HTTP 401 over a verified accepted TLS/mTLS connection, distinguishing that HTTP response from handshake failure.
- [x] Standard negative scenarios — invocation wrong-CA server; callback wrong-host server; callback expired server. These representative cases prove both actual callers reject invalid servers and exercise generated CA, SAN and expiry material without a full cross-product. Run isolated startup variants with fresh connections, never live certificate replacement. Assert SDK invocation fails before any Sidecar admission/effect, or admitted execution fails before any callback arrival/body/effect. Positive controls prove listeners are running and rejection is attributable to certificate validation, not refusal/timeout.
- [x] Hardened negative scenarios — for each direction, configure its outbound Caddy with a wrong-CA client certificate, and separately configure the remote server with a wrong-CA leaf carrying its correct expected DNS SAN. The corresponding form-originated operation must fail and the protected backend counters must remain unchanged. Use verified independent probes to classify the server/client TLS failure and valid-identity controls to establish the target listener works. No listener-down result passes as certificate rejection.
- [x] Hardened isolation scenario — from a separate outsider service on the internal network, verify allowed TLS/browser ingress, reject unauthenticated mTLS, and prove TCP connection refusal to both peers' backend, outbound-proxy and local-fixture ports. Test an absolute-form URL/host override through each local fixed proxy and verify the sole backend counter, so the fixture cannot act as an open forward proxy. Inspect namespace identities/listener binding as additional evidence, not a replacement for outside probes.
- [x] `suite/scripts/sdk_lane/fixture.py` or new fixed `remote_fault.py` — loss instrumentation has one literal destination and a small path allowlist, forwards once, waits for receiver acceptance/effect, consumes the response, then drops the caller connection. For Standard the fault fixture uses verified TLS toward the real Spring receiver and a trusted TLS listener toward the real Java caller; for Hardened it sits on receiver-local HTTP behind the actual mTLS Caddy ingress. Normal successful Standard traffic remains direct Spring-to-Spring. Instrumentation is a test-only fault variant, never an operator proxy or TLS bypass.
- [x] `suite/sdk/reference-app/src/main/java/ai/loomspan/sdk/example/ExecutionController.java` — only if needed, add fixed fault-fixture operations which use the real configured SDK/issuer and report bounded acceptance-unknown/transport-failure booleans, without token extraction, user-selected destinations or production SDK changes. `CallbackController` may add a fixed synthetic response-loss route/counter if required by the receiver-local loss fixture.
- [x] Submission and callback loss scenarios in each recipe — independent request/forward/effect counters equal one after response loss and remain unchanged through a bounded quiet interval exceeding configured response timeout. If no HTTP response reaches the SDK, submission reports acceptance unknown; a Caddy-generated 502 is correctly recorded as HTTP failure instead of being mislabeled acceptance unknown. Neither outcome causes resubmission. Callback terminal failure does not cause another callback. Polling/status reads do not replay submission. Fresh successful operation is allowed only as a separately identified test action.
- [x] `suite/tests/test_sdk_remote.py` — verify exact oracles, negative false-positive prevention and fixture loss behavior, including unavailable service, extra request, false/missing/wrong-typed result and cleanup failure.

### Automated verification

- [x] From suite: `.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar` — all Standard scenario results, required stages and cleanup pass using real built artifacts.
- [x] From suite: `.venv/Scripts/python.exe scripts/run_sdk.py hardened --sidecar-checkout C:/opendev/code/loomspan-sidecar` — all Hardened scenario results, required stages, outsider isolation and cleanup pass.
- [x] Each complete run necessarily runs root `clean verify`, SDK `clean install` and both consumer packages through existing `build_checkout`. Existing component configuration-mode and TLS tests must pass without duplicating recipes by ConfigurationMode. If actual TLS wiring needs a production fix, first add the smallest affected component regression and rerun the relevant recipe after the fix.
- [x] From suite: `.venv/Scripts/python.exe -m pytest tests -q` — broad safe provider-free harness; inspect any environment-dependent failures rather than relabeling as pass.

### Optional developer checks

Deploy the examples using operator-owned DNS/certificates in an explicitly configured non-production environment to assess local firewall/secret delivery policy. This is optional and does not substitute for the required isolated Docker evidence.

## Phase 3: Operator configuration and recorded evidence

### Changes

- [x] `docs/remote-recipes.md` — explain independent invocation/callback configuration, provided signer/JWKS delivery, both topologies, exact listener boundaries and no plaintext cross-host traffic. Link official Boot SSL and Caddy TLS/reverse-proxy docs. State DevOps ownership of issuance, storage, delivery, renewal, replacement, CA rotation and revocation; do not prescribe lifecycle implementation or promise hot reload.
- [x] `examples/remote/standard/app.yaml`, `sidecar.yaml`, `rest-routes.yaml` — minimal usable fragments with Boot PEM listener and trust bundles, provided SDK signing/export and Sidecar `trust.mode=file`. Explain application/skill/model values which the operator supplies.
- [x] `examples/remote/hardened/app.yaml`, `sidecar.yaml`, `rest-routes.yaml`, `Caddyfile.app`, `Caddyfile.sidecar` — loopback backends/outbound listeners, group-scoped mTLS, fixed verified upstreams and separate browser ingress as above. Reference protected shared namespace/deployment requirements; configuration fragments alone do not create network isolation.
- [x] `docs/README.md`, `docs/integration.md`, `docs/verification.md`, `examples/production/README.md` and `sdks/java/README.md` — concise links and accurate component/composed ownership; retain existing generic production ingress examples and do not imply those are the new Hardened topology.
- [x] `suite/docs/sdk-lane.md` and `suite/README.md` — replace empty-selector claims, list actual scenarios/commands and bounded results with actual report paths; record any attempted failure and final successful artifacts honestly. No copied Framework completion evidence.
- [x] Ticket acceptance checkboxes may be marked only after matching actual evidence. No development-data reset.

### Automated verification

- [x] Both remote compositions consume/validate the shipped recipe fragments where practical; otherwise harness parses fragments and compares security-critical configured listener/upstream/bundle/signing settings with the exercised composition. Do not maintain untested alternate Caddy examples.
- [x] From suite: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py` and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py`.
- [x] `git diff --check` in both repositories; inspect tracked/untracked files for certificate/key material and verify no run-owned containers/networks/public volumes remain after successful and failed runs.

### Optional developer checks

None beyond phase 2's explicitly configured deployment observation.

## Test Strategy

Use existing Java component tests for existing TLS/JWKS contracts. Add focused Java coverage only for an exposed wiring defect. Use Python harness tests for fixture correctness, report strictness and cleanup, with real local TLS handshakes for certificate semantics. Use full Docker SDK-lane runs for actual Java caller, Caddy and namespace behavior. Do not treat mocked Compose, static Caddy text checks or framework tests as composed evidence.

The paired recipe scenario as a whole covers both invocation and callback; a negative necessarily interrupts its particular direction, with the other hop established by a valid control. Avoid cloning Local restart, trust reload, queue expiry or certificate-lifecycle scenarios.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Both runner recipes, per-run temporary certs | `RECIPES`, remote material/runtime | Full Standard/Hardened reports; certificate generator and cleanup harness tests; no committed secrets |
| Standard verified HTTPS, both negatives, accepted-TLS JWT rejection | Boot listener/bundles, remote positive and negative probes | Real SDK invocation/callback success; invocation wrong CA; callback SAN and expiry; verified connection returning 401 |
| Hardened mTLS, peer rejection, isolation, fixed upstream, separate browser, accepted-mTLS JWT rejection | Supplied paired Caddy files and protected namespaces | Both Caddy client/server paths, wrong-CA clients and wrong-CA servers in both directions, outsider TCP probes, fixed-target oracle and HTTP 401 through mTLS |
| Provided signing and exported file JWKS | App-only PEM mount, SDK provided settings, public-only file delivery | Inspect exported public key and absence of private mount; authentic issuance/callback success; existing ConfigurationMode integration test in root verify |
| No submission/callback replay | Existing Java settings, explicit Caddy zero retry, fixed fault fixture | Post-acceptance response-loss attempt/forward/effect counters and quiet interval in both recipes |
| Standard TLS wiring and focused tests; no insecure switch/enum | Existing Boot client wiring retained unless proven defective | Existing Java TLS tests/root and SDK builds; focused regression for any actual production fix |
| Operator references and ownership/boundaries | `docs/remote-recipes.md`, `examples/remote/**`, links | Parse/validate shipped settings, exercised configuration correspondence, final independent documentation review |

## Risks and Rollback/Recovery

## Implementation decisions and verification ledger

- Standard invalid callback server variants use the same Spring HTTPS listener
  for browser control and callbacks. The isolated Python control driver pins
  that variant's exact generated certificate to bootstrap form login; only this
  negative-test control uses that treatment. The actual SDK invocation and Sidecar
  callback clients retain normal configured chain/hostname/expiry verification.
  Positive Standard browser, invocation and callback traffic all verify normally.
- The first selector regression was run before implementation:
  `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -k remote_recipe -q`
  failed both parameter cases because the selectors were empty (2 failed,
  77 deselected). This is harness red evidence, not transport evidence.
- Existing runner harness passed 79 tests. The new remote harness initially
  passed 12; adding real loopback TLS exposed missing AKI/SKI extensions, which
  were corrected in disposable fixture certificates. Pinned `caddy:2.10.2`
  validated both actual shipped Caddy files. Broad Python passed 201 before the
  additional loopback TLS test; final verification remains required.
- Early harness development failures were corrected before final gates: a fake
  runtime used the wrong positional variant index; Python 3.13 strict TLS exposed
  missing fixture AKI/SKI; the Windows unavailable-listener case needed to accept
  infrastructure timeout as well as refusal, while neither may count as a TLS
  rejection. The attempted `.venv/Scripts/ruff.exe` path was unavailable; the
  installed `ruff` command performs formatting/lint verification successfully.
- No production TLS wiring change or development-data reset is required.
- The paired Caddy outbound blocks explicitly replace HTTP Host with their
  literal remote TLS authority, matching fixed SNI under Caddy's client-auth
  strict host checks. A targeted real Hardened positive run exposed the missing
  header; after the configuration correction, positive and invocation invalid
  client/server variants passed. Caddy destinations remain request-independent.
- Caddy readiness has its own literal loopback backend route. A targeted
  Hardened submission-loss startup initially routed readiness through the
  not-yet-started loss instrument and failed; the instrument now affects only
  the execution route. Fresh loss-only runs passed both Hardened submission and
  callback cases, and all fourteen Standard/Hardened targeted variants passed.
  The fault fixtures remain receiver-local; no lifecycle or retry contract changed.
- The first full Standard report `20261019T014109Z-545d2153ac89` passed root,
  SDK, both consumer and image builds, then failed app readiness because the
  provided-mode fixture inherited the packaged Local colocated key-directory.
  Remote app startup now selects its complete fixture configuration source.
  Independent label-scoped container/network/volume listings confirmed cleanup
  for `sdk-bd3a0263c12c47d1-positive`. All six fresh targeted Standard runtime
  variants subsequently passed; full final runner gates remain required.
- The complete Hardened command passed all eight scenario/startup/cleanup results:
  `suite/evidence/sdk/20261019T014927Z-b5ab2b9224e2/report.json`, 927.972 seconds.
  Exact project-label `docker ps -a`, `docker network ls` and `docker volume ls`
  checks found no resources for all eight report-listed projects. Full source
  revisions and artifact hashes are recorded in that report and suite SDK guide.
- The complete Standard repeat passed all six scenario/startup/cleanup results:
  `suite/evidence/sdk/20261019T015656Z-c2445d463665/report.json`, 777.899 seconds.
  Exact scenario schemas and required build stages pass in both reports. All six
  Standard projects independently have no labeled containers/networks/volumes.
- Final verification: focused external Python `tests/test_sdk_runner.py
  tests/test_sdk_remote.py` passes 103; broad `tests` passes 213; Ruff formatting
  and lint pass. Actual root Surefire reports contain 61 classes / 261 tests;
  SDK core 25 and starter 30; all have zero failures/errors/skips. Both repository
  diff checks pass. Changed/untracked file-extension scans found no certificates
  or keys; all three formal reports contain no PEM/bearer/JWT values. No provider,
  business runtime, maintained reference pack, data reset, commit or release work
  occurred. Full pipeline completion remains subject to fresh Step 5 review.

No persistent business schema changes or reset. Rollback consists of reverting ticket changes and removing only uniquely labeled ephemeral SDK-run resources. Cleanup failures fail the run, preserve the primary diagnostic and identify the unique project for bounded recovery; never use Docker-wide prune or delete another project's volume. Generated private material is temporary and must be removed even after failure/interruption. Keep actual report identifiers and residual limitations in the SDK guide for fresh review.

If Docker or a required build tool is unavailable, complete safe harness work but report the remote acceptance gates unrun; those gates cannot become optional merely because the environment fails. No production change is planned on the assumption that a negative test fails.

## References

- Ticket and research above; `ai/thoughts/design-lens.md`.
- `ai/thoughts/phases/2026-10-15-sdk-v1-deployment-recipes.md`; `docs/verification.md`.
- `suite/AGENTS.md`, `README.md`, `docs/project-agreement.md`, `docs/sdk-lane.md`.
- [Spring Boot SSL bundles](https://docs.spring.io/spring-boot/reference/features/ssl.html), [embedded server SSL](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.configure-ssl).
- [Caddy TLS](https://caddyserver.com/docs/caddyfile/directives/tls), [reverse proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy); current official syntax inspected in planning, pinned image validation remains required.
