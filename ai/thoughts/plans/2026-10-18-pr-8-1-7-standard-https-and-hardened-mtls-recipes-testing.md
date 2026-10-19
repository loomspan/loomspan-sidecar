# Standard HTTPS and Hardened mTLS Testing Plan

## Change Summary

Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md`.
Research: `ai/thoughts/research/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md`.
Implementation plan: `ai/thoughts/plans/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md`.

Full pipeline Steps 2 and 3 share this planning context. This step changes no production or test code. `suite/` denotes `C:/opendev/code/loomspan-sidecar-test-suite`; unprefixed paths denote `C:/opendev/code/loomspan-sidecar`.

Add composed evidence for direct Spring HTTPS and paired Caddy mTLS, provided signing with public-file JWKS delivery, real form login and authenticated callbacks, invalid-peer rejection, network isolation and no replay. Existing Java TLS facilities should suffice. Add Java tests only if an actual required wiring fix is needed.

**Replace superseded development contracts/fixtures directly; do not add legacy adapters or compatibility paths.** Apply [the design lens](../design-lens.md). No development-data reset, live provider call, business-runtime operation, commit or release is included.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Direct TLS | Client silently trusts the wrong CA or ignores hostname/expiry | Actual SDK invocation wrong-CA failure; actual Sidecar callback SAN/expiry failure; positive controls and independent verified TLS classification |
| mTLS | Client identity is not required or group trust is too broad | Valid mTLS pair; wrong-CA client rejection in both directions; absent client rejection and wrong-CA server rejection with a correct DNS SAN |
| Protected network | Backend/outbound proxy reachable from peer namespace; browser ingress bypasses callback mTLS | Actual outside TCP connect probes to both peers; separate browser HTTPS form login; callback prefix denied on browser ingress |
| Fixed upstream | Request authority selects destination or proxy retries | Authority/absolute-form probes, literal upstream inspection, one backend effect; response-loss attempts/forwards/effects remain one |
| JWT/user authorization | mTLS incorrectly substitutes for JWT or record/method checks | Verified accepted TLS/mTLS HTTP 401; callback exact issuer/subject/roles, literal-role method denial and Bob record denial |
| Signing/file trust | Test substitutes handcrafted tokens/trust for the app's export | SDK provided signer, exported JWKS public key matches provided signer, Sidecar reads public-only export; root ConfigurationMode test retains independent file/database proof |
| Harness/evidence | Missing results, refused sockets or cleanup failures appear passed; secrets escape | Strict result schemas, failed positive controls, false/missing/extra/wrong-type mutations, failure/interruption cleanup and report redaction tests |

## Existing Coverage and Environment Constraints

- Sidecar root uses Maven/JUnit, Bouncy Castle fixtures, Spring integration tests and ArchUnit. `RestTransportTlsIntegrationTest.usesTheSelectedSslBundleAndClientIdentityPerTarget` proves per-target bundle/mTLS selection. `GenericRestSkillHandlerTest` covers retry/redirect/timeout behavior. `ConfigurationModeIntegrationTest` already parameterizes authoritative JWKS-file trust in file/database mode.
- SDK core `SidecarClientTlsTest` and starter `SidecarClientAutoConfigurationTest` prove real TLS and hostname rejection through existing client/bundle wiring. Both clients disable automatic retries/redirects. Do not infer composition success from these tests.
- `suite/tests/test_sdk_runner.py` uses pytest/httpx, temporary fixtures and fake lanes for orchestration; real loopback TLS and response-loss fixtures verify the harness. New remote-focused tests belong in `suite/tests/test_sdk_remote.py` to keep additions readable.
- `suite/scripts/sdk_lane/local_probe.py` supplies working form login/CSRF, callback counters, exact roles/identity and token-equality patterns. Keep opaque tokens in process memory/app-local capture; report booleans/counters only.
- Full runner requires Java 21 (`JAVA_HOME` currently `C:/hamdev/jbrsdk21`), Maven, Docker daemon/Compose and suite `.venv` Python with cryptography/httpx/PyYAML. `java` may need `JAVA_HOME/bin` prepended to PATH in the invoking shell. Use existing child-environment allowlist; do not pass provider credentials or injected Java options.
- Pinned Caddy image is `caddy:2.10.2`; offline adaptation plus actual pinned-image startup must verify used syntax. No CA service/ACME, externally reachable service, custom plugin or credential is needed. Artifact/image downloads are possible.
- All runtime resources use unique SDK projects and isolated internal Docker networks. No host ports, business data/credentials or maintained reference packs are mounted. Temporary signing and TLS private material is fixture-only; no tracked material or run logs may expose it.
- Do not run the ordinary five-service runtime or paid mock/live business suites as a substitute. Required broad safe suite is Python `pytest tests`, plus the Java builds run by the SDK runner.

## Failing Test First

- Name: `test_remote_recipe_dispatches_and_requires_all_scenarios` (parameterized Standard/Hardened).
- Type: runner harness regression/first acceptance test for new behavior.
- Location: `suite/tests/test_sdk_runner.py`; replaces the existing `test_empty_recipe_fails_explicitly` expectation near the empty-recipe selector tests.
- Arrange/Act/Assert: fake build/runtime success using existing `fake_lane` conventions, call `runner.main([recipe, ...])`, require nonempty scenario selection, recipe-specific remote dispatch, passed typed observations and cleanup; then mutate one required result to absent/failed and require nonzero exit with failed evidence.
- Expected pre-fix failure: current selectors are empty and exit with `no-scenarios-for-recipe` before builds, so the success case fails. Record that intended red result before replacing dispatch.
- This harness red establishes the missing selector capability, not real transport correctness. The initial real remote attempt is also expected to expose missing runtime until implementation; final completion requires the actual composed gates below.

## Tests to Add or Update

### 1. `test_remote_material_is_temporary_and_has_selected_certificate_semantics`

- Type: unit plus real local TLS fixture test.
- Location: `suite/tests/test_sdk_remote.py`, exercising new `sdk_lane.remote_material` functions.
- Proves: per-invocation CA/key uniqueness, correct CA basic constraints, serverAuth/clientAuth EKUs, expected DNS SANs, wrong-CA signatures, wrong-host SAN mismatch, expired validity and PKCS#8 RSA provided signing material.
- Inputs/fixture: pytest temporary directory and cryptography-generated keys; no checked-in certificate bytes or private-key snapshots.
- Isolation: loopback TLS server/client sockets only; no Docker or network CA service.
- Edge cases: wrong CA yields verification failure; wrong hostname and expiry yield their actual verification errors; listener unavailable raises an unclassified infrastructure error and cannot satisfy a TLS-negative oracle. Inspect certificate metadata without printing PEM/private material.

### 2. `test_remote_composition_has_independent_trust_and_protected_listeners`

- Type: deterministic harness/configuration contract test.
- Location: `suite/tests/test_sdk_remote.py`, exercising `remote.configure`/`composition`.
- Proves: Standard has no Caddy or client key selection and uses two named trust bundles/HTTPS listeners; Hardened has exactly the two protected gateway namespaces, loopback backend/outbound addresses, scoped group CA, fixed verified upstreams and separate browser ingress.
- Inputs: shipped `examples/remote/**` fragments and generated runtime settings, not independent copied expected Caddy files.
- Isolation: parse settings and compare critical values; runtime Caddy adaptation/startup and outside socket tests remain required.
- Edge cases: no host port publication, no private signing mount into Sidecar, only public export shared, both localhost fixtures stay in their own namespace, browser callbacks denied, and no arbitrary request-controlled host/port. Standard model/control traffic remains namespace-local HTTP.

### 3. `test_remote_runtime_records_every_stage_and_cleans_its_project`

- Type: parameterized runner/runtime harness tests.
- Location: `suite/tests/test_sdk_runner.py` and `suite/tests/test_sdk_remote.py`.
- Proves: actual mandatory build path retained; correct recipe dispatch; all scenario results and cleanup required; source/artifact identities and durations preserved; report initialized before failure and finalized atomically.
- Inputs: fake command responses following existing lane tests; sanitized failure sentinels.
- Edge cases: startup failure before app export, invalid/empty JWKS, exited listener, readiness timeout, scenario error, KeyboardInterrupt, helper cleanup failure and public-volume removal failure. Primary error preserved, cleanup failure still fails run, unrelated resource names never removed. Report exposes only fixed diagnostics and typed observations, not exceptions, raw logs, token/key/certificate bodies or business payloads.

### 4. `<recipe>-provided-identity-and-callback`

- Type: actual composed acceptance scenario, parameterized once for Standard and Hardened.
- Location: `suite/scripts/sdk_lane/remote_probe.py` and `remote.Runtime` orchestration.
- Arrange: generated provided RSA signer mounted into app only; SDK exports public JWKS; file delivery mounted read-only into Sidecar; app and Sidecar live on the selected recipe. Readiness must be reached with valid verified transport.
- Act: perform form login/CSRF as Alice, submit fixed owned callback skill with real SDK, poll using fresh SDK credentials and check app-local captured credential equality.
- Assert: terminal COMPLETED; exact issuer `loomspan-app`, subject Alice and `ROLE_ORDER_EDITOR`; one callback body/effect; callback uses selected HTTPS/mTLS path. Bob reaches callback body but record check denies effect; literal `ORDER_EDITOR` is not accepted by `hasRole('ORDER_EDITOR')`. Retain CSRF/session protection on browser operations.
- Proves: both directions, provided signing, public file trust, authenticated identity and application-owned authorization. Do not create a test-only issuance endpoint or extract bearer credentials to host tooling.
- Edge cases: missing/invalid JWT reaches an HTTP 401 through accepted verified TLS/mTLS, whereas transport-negative probes never conflate 401 with handshake failure. Return exact boolean/counter result schemas and reject missing, false, extra or wrong-typed observations.

### 5. `standard-invalid-server-certificates`

- Type: actual composed TLS negative scenarios.
- Location: remote runtime startup variants and `remote_probe.py`; harness oracles in `suite/tests/test_sdk_remote.py`.
- Inputs: invocation server signed by unrelated CA; callback server with same-CA wrong hostname; callback expired server. This representative inventory covers both directions and all selected generated invalid server categories without a full matrix.
- Act: use the real authenticated form/SDK operation in each isolated fixed startup variant. For invocation, actual SDK cannot admit work; for callback, invocation succeeds but actual Sidecar callback must fail before entering app callback filters.
- Assert: expected SDK/terminal failure plus unchanged receiver admission/arrival/body/effect counters. Independent verified TLS probe confirms the specific certificate error, and valid material/control startup establishes the address/listener is alive. A refused connection, generic timeout or dead Java process is failure of the test, not proof of rejection.
- Isolation/recovery: independent process startup/teardown with fresh clients and pools; no certificate replacement/reload/rotation procedure. Final valid-control operation succeeds.

### 6. `hardened-invalid-client-and-server-peers`

- Type: actual composed mTLS negative scenarios in each direction.
- Location: remote runtime variants/`remote_probe.py`; negative-oracle harness tests in `suite/tests/test_sdk_remote.py`.
- Inputs: each outbound Caddy configured once with a wrong-CA client leaf, and each remote Caddy server configured separately with a wrong-CA leaf containing its correct expected DNS SAN. Also require a no-client-cert probe against both mTLS ingress listeners.
- Assert: actual outbound gateway rejects wrong server identity; ingress gateway rejects wrong-CA/missing client; backend counters unchanged; valid mTLS controls succeed. Observe fixed proxy/backend counts and classify verification failure using an independently verified probe with valid server/client counterparts.
- Identity limits: wrong-CA client establishes group CA membership enforcement, not DNS SAN verification of client identity. Standard callback SAN mismatch separately tests hostname identity. Hardened correct-SAN/wrong-CA server material permits normal Caddy certificate selection while proving outbound chain validation. Do not invent a client SAN allowlist or Caddy plugin contract.
- Edge cases: a stopped Caddy, upstream refused socket or malformed Caddy configuration must fail infrastructure setup and cannot count as successful negative evidence.

### 7. `hardened-no-bypass-and-fixed-destinations`

- Type: actual Docker namespace/network acceptance scenario.
- Location: `remote.Runtime` outsider probe and `remote_probe.py`; composition harness coverage in `suite/tests/test_sdk_remote.py`.
- Arrange: separate outsider container on the isolated network with no namespace sharing. Resolve app/Sidecar namespace owner IPs and establish the intended TLS/browser listeners work.
- Act/assert: TCP connection to each namespace's backend, local outbound proxy and local fixture port is refused; the appropriate mTLS endpoints accept only trusted peers. Browser 8444 uses form login and refuses callbacks, callback 8443 requires client auth and exposes only callback paths. Confirm namespace identities and loopback bindings as supporting evidence.
- Fixed-target probe: inside each namespace, send altered Host/absolute-form authority to the loopback outbound gateway; requests still go to its sole configured upstream or are rejected, never forwarded to a second fixture target. Counter assertions distinguish those outcomes.
- Proves: actual no-bypass boundaries and separate browser ingress. Static YAML/Caddy text assertions alone cannot pass this scenario.

### 8. `<recipe>-submission-and-callback-no-replay`

- Type: actual remote composed response-loss scenarios, with harness fixture tests.
- Location: fixed loss instrumentation in `suite/scripts/sdk_lane/fixture.py` or `remote_fault.py`, real operations in `remote_probe.py`, harness in `suite/tests/test_sdk_remote.py`; fixed reference-app operation only if needed in `ExecutionController`/`CallbackController`.
- Arrange: receiver-local fixed destination/path fault instrument. Standard test-only fault variant terminates trusted TLS and verifies TLS to actual Spring receiver; normal positive Standard remains direct Spring HTTPS. Hardened fault variant runs only behind actual Caddy mTLS on receiver-local loopback. It has no user-controlled destination and no automatic retry.
- Act: once-only forward accepted submission and drop its response; once-only forward authenticated callback, wait for its effect and drop response. Use the real Java SDK/Sidecar clients and real Caddy path where selected.
- Assert: independent incoming-attempt/forward/receiver-effect counts all one, including after a quiet interval longer than the configured response deadline (use at least 5 seconds when response timeout is 3 seconds). No status-read resubmission or compensating callback. SDK transport loss reports acceptance unknown if it receives no HTTP response; if Caddy converts loss into 502, assert actual HTTP failure and no replay instead of claiming unknown acceptance. Callback ends failed despite its single effect; preserve that ambiguity rather than converting to success.
- Edge cases: extra send/forward/effect must fail; closed listener before any effect cannot satisfy the post-effect fault oracle. Restore validity through fresh startup/independent action, never automatic replay of prior work.

### 9. `test_shipped_remote_examples_match_exercised_security_settings`

- Type: configuration parsing/validation and independent documentation review.
- Location: `suite/tests/test_sdk_remote.py`, shipped `examples/remote/**`, `docs/remote-recipes.md`.
- Proves: examples use real Boot/SDK/Sidecar keys and Caddy supplied TLS; fixed peers and listener bindings match runtime configuration. Runtime invokes pinned-image adaptation/validation of the actual Caddy files. Official documentation links, independent transport setup, no-plaintext-cross-host statement, DevOps lifecycle ownership and permitted untested compositions are present and coherent.
- Boundaries: no test that merely asserts lengthy prose strings. Use source/configuration correspondence and fresh review for operator accuracy. Do not replace required executable topology evidence with documentation checks.

## Safe Verification Commands

Run suite commands with working directory `C:/opendev/code/loomspan-sidecar-test-suite`:

```powershell
.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -k remote_recipe -q
.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q
.venv/Scripts/python.exe -m pytest tests -q
ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py
ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py
.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar
.venv/Scripts/python.exe scripts/run_sdk.py hardened --sidecar-checkout C:/opendev/code/loomspan-sidecar
git diff --check
```

The first command is the intended pre-implementation red test after adding its assertion. Adjust only its `-k` match if final test naming differs; do not report an empty selection as a passing red/green check.

Each real recipe command performs `mvn -B -ntp -f <sidecar>/pom.xml clean verify`, SDK `clean install`, both consumer packages and the actual Docker build through `build_checkout`. Root verify includes ConfigurationMode and architectural guardrail tests. If production TLS wiring changes, focused development commands from Sidecar are:

```powershell
mvn -B -ntp -Dtest=RestTransportTlsIntegrationTest test
mvn -B -ntp -f sdks/java/pom.xml '-Dtest=SidecarClientTlsTest,SidecarClientAutoConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

Run only the affected focused Java test during development, then let mandatory full builds supply broad evidence. No Framework rebuild or dependency override. Tests may download artifacts/images, but no live provider/service calls or business-data resets occur.

The existing Local runner is unaffected by dispatch design; its harness tests must remain green. Rerun the complete `scripts/run_sdk.py local` only if implementation changes Local runtime/probe/fixture behavior, or a regression makes it necessary; pure additive remote configuration plus shared harness regression does not require repeating unrelated lifecycle scenarios.

After each actual run, inspect that exact report's project identifiers with scoped `docker ps -a --filter label=com.docker.compose.project=<project>`, `docker network ls --filter label=com.docker.compose.project=<project>` and `docker volume ls --filter label=com.docker.compose.project=<project>`; require no run-owned persistent resources. Do not use prune/global cleanup. Review `git status --short` in both checkouts and tracked/untracked certificate/key extensions before delivery; generated material must remain outside the tracked changes.

## Optional Developer Checks

- In an explicitly configured non-production deployment, use operator-owned certificates/DNS and assess firewall/secret delivery policy. This observation is optional, is not performed by routine verification and is not evidence for portable customer deployment isolation.

## Exit Criteria

Final Step 4 evidence: Standard
`suite/evidence/sdk/20261019T015656Z-c2445d463665/report.json` (six scenarios),
Hardened `suite/evidence/sdk/20261019T014927Z-b5ab2b9224e2/report.json` (eight),
both complete with all required builds/stages/cleanup. The implementation plan's
verification ledger and suite SDK guide preserve failed attempts, corrections,
source/artifact identities and exact scoped-resource observations. Final focused
Python: 103; broad: 213; root: 261, SDK core: 25, starter: 30, all green.
No production wiring fix was necessary, so no new component TLS test was added.

- [x] New selector success test first fails because Standard/Hardened currently have no scenarios; no unrelated failure is counted as red.
- [x] Focused and broad safe Python suites pass; no false-positive TLS-negative, schema/redaction or cleanup test remains.
- [x] Existing Java root/SDK tests and consumer builds pass in the actual recipe build path. Any necessary production change has its own focused regression.
- [x] Both complete real remote runner commands pass every named scenario and required stage, including cleanup; exact report IDs and source/artifact hashes are recorded in SDK documentation.
- [x] Positive invocation/callback, accepted-transport JWT rejection and role/record authorization are shown in each recipe with provided signing and actual exported public file trust.
- [x] Both Standard directions reject an invalid server; selected SAN/expiry cases and real TLS classifications are recorded.
- [x] Both Hardened directions reject selected invalid client/server peers; actual outsider probes prove backend/outbound listener isolation, fixed destinations and separate browser ingress.
- [x] Post-effect submission and callback response loss produces one attempt/forward/effect through the bounded quiet interval in each recipe, without mislabeled HTTP/TLS failures or automatic replay.
- [x] Shipped operator configurations parse, Caddy configurations validate with the pinned image and required security settings correspond to exercised deployments.
- [x] No certificates/keys/credentials are committed or exposed in reports; no unrelated runtime, business data, maintained reference pack or deployed data is touched.
- [x] Acceptance criteria map to the executable evidence above; optional observations are labeled unperformed/nonblocking unless explicitly run.
- [ ] Full pipeline completion remains subject to fresh independent Step 5 review, which must execute sufficient final verification and return clean.
