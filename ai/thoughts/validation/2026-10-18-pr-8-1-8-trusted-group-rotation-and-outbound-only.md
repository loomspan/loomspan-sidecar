# PR 8.1.8 implementation verification

Approved Full 5-Step Pipeline, Step 4. Independent Step 5 remains required.
Apply the [design lens](../design-lens.md): replace obsolete development contracts directly;
no compatibility machinery or data reset is needed. No production trust implementation changed.

## Implementation decisions

- Standard adds a second JVM in the app network namespace on verified HTTPS 8445;
  B issues/submits while A receives the unchanged owned callback. Both share provided
  signing, issuer, callback audience and the same directory/roles implementation.
- Operator Sidecar trust and callback overlap are distinct public files mounted by
  directory, independent of each app's export. A disposable public-SDK Java helper
  issues one replacement candidate within the app signing boundary. Old/candidate/
  current credentials stay only in fixture memory across app restarts.
- Addition probes and retirement probes each run as one bounded in-namespace driver;
  the host restarts each app independently between these phases. Per-file adoption
  is observed within five seconds of atomic replacement, independently of the
  unchanged opposite receiver. A saved old-key model blocker starts only after
  both restarts and completes after both removals. Fixed nonsecret marker files
  bridge the model barrier between the app/Sidecar fixture namespaces; the model
  deadline remains 30 seconds and no extra network listener is added.
- Outbound-only reuses the jar as a non-web Spring application. Servlet beans are
  conditional; one ApplicationRunner issues, discovers, submits once, polls exact
  completion and exits. The process awaits a fixture release so live listener
  absence is established before invocation. Hardened omits both callback Caddy
  blocks and browser ingress while retaining outbound invocation and mTLS ingress.
- Only temporary SDK compositions change. Business runtime, operational data,
  maintained reference fixtures, credentials, commits and releases are untouched.

## Commands and results

Suite working directory: `C:/opendev/code/loomspan-sidecar-test-suite`.

- EXPECTED RED: `.venv/Scripts/python.exe -m pytest tests/test_sdk_remote.py -k standard_group_rotation_has_two_apps_and_independent_trust_files -q`  -  1 failed, 24 deselected; the asserted Standard variant was absent (not a missing import).
- PASS after composition implementation: same focused command  -  1 passed, 24 deselected.
- PASS: `mvn -B -ntp -f sdk/reference-app/pom.xml clean package`  -  six Java sources compiled, packaged Boot jar; no Java test sources in this fixture.
- PASS: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q`  -  136 passed (52.48s final rerun).
- PASS: `ruff format scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py` and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py`  -  formatting applied, lint clean.
- PASS: `git diff --check` in both repositories after final documentation/checklist updates.
- PASS: `.venv/Scripts/python.exe -m pytest tests -q`  -  246 passed (64.70s final rerun after namespace/mount regression changes).
- FAIL retained: `.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar`  -  report `evidence/sdk/20261019T025810Z-daadbfb4e9bf/report.json`, 847.813s, `rotation-add-failed`. All mandatory builds, six existing variants and scoped cleanup passed; group failed and outbound-only remained not-run. This process loaded pre-fix module mounts/topology and is not final acceptance.
- Scoped provider-free diagnostics reused that attempt's freshly built image only to locate fixture issues; these do not replace mandatory full-run evidence. Group diagnostics found the missing remote-probe import-name mount and namespace-owner restart of credential memory; both corrected with regressions. Final scoped group and Local/Standard outbound-only diagnostics passed actual assertions, with cleanup. Hardened outbound diagnostic also passed; every diagnostic cleaned successfully.
- Root component results from the formal build: 61 classes / 261 tests, zero failures/errors/skips. SDK core 25 and starter 30, zero failures/errors/skips.

## Formal Standard acceptance

PASS: `.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar`  -  report `evidence/sdk/20261019T031355Z-aa841eadf316/report.json`, 884.328s. All eight selected scenarios, mandatory builds and each startup/cleanup passed; no failed stages.

The group result proves shared signer/issuer, B invocation to A callback, exact identity/roles/credential equality, method/record/owner denial, pre-add rejection, independent add and removal, actual restart of both signers, accepted replacement and unexpired old-key rejection, RUNNING old-key admission and exact completion after retirement. Every observation is required by the strict result schema. Standard outbound-only proves actual no-listener invocation and clean process exit.

Source identities: Sidecar `f0ea254e0c41e09ab900f58c1773e407575df986`, suite `6e65846ee833ee91f308e07ecb8662fe56839a16`, both dirty. Framework `1.0.0-beta.8`. Sidecar JAR SHA-256 `e9faded6623775d149923f8e7c36e54af41d4951f159d8faea6f1e9fca12f194`; reference app `3cd68c8ccb329762e806c57eeaa29d72958c52bda3596935513a223ce952bac2`; remaining SDK/driver identities are in the report.

## Formal Local acceptance

PASS: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`  -  report `evidence/sdk/20261019T032901Z-8d4833bcc6c8/report.json`, 611.772s. All 13 scenarios and required build/runtime/cleanup stages passed, including actual non-web outbound-only invocation with one model request and clean exit. No failed stages. Sidecar SHA-256 `4416a27516faa306339b42539f04721100bee4cda3257e920e71820ddd4a22cf`; reference app `3ec82b6843f50119c24109e96cde206d1f44db823e0b19809f24fb0e02d43248`. SDK/driver/source identities are retained in the report. Exact cleanup audit adds the observed base project `sdk-eea384b57cea4bcc` and the two report-identified Local projects/explicit key volumes.

## Formal Hardened acceptance

PASS: `.venv/Scripts/python.exe scripts/run_sdk.py hardened --sidecar-checkout C:/opendev/code/loomspan-sidecar`  -  report `evidence/sdk/20261019T033931Z-5bb5875da104/report.json`, 851.853s. All nine cases, mandatory builds and startup/cleanup stages passed; no failed stages. Outbound-only retained mTLS invocation with absent callback/backend/browser listeners and Sidecar callback egress, one model request and clean app exit. Sidecar SHA-256 `6120f1eb1572d3bef2fa3652d5559f7598485a38c28945225620e1554a7a89b1`; reference app `14acd3d7754a3a740b8952570119f27dd47b2154ffdeabb279bdcab1166b83c9`. Remaining identities are in the report.

All three fresh commands pass 30 selected scenarios total. Root and SDK test counts remain 261 / 25 / 30 with zero failures/errors/skips. Independent Step 5 review is still pending.

Final low-impact cleanup removed an unused fixture environment marker and corrected a fixture docstring. Group composition test rerun: 1 passed, 46 deselected; Ruff formatting/lint remained clean. No Java or executed behavior changed. No formal result is claimed from mocks or historical reports.

## Cleanup and secret checks

The adjacent cleanup JSON records each exact read-only Docker command and absence result. Initial audit: 14 failed-attempt/diagnostic projects, 70 commands; containers, networks, project volumes, helpers and explicit external volumes all absent. Final Standard, Local and Hardened projects add 99 checks; the complete audit has 34 projects / 169 checks, all clean.

Final changed-source/report secret-pattern scan: 32 files checked, no private PEM material or JWT/Bearer values found. All four new formal reports were included; final report identities, required stages and exact scenario results also passed automated consistency checks. Only matching file/category would be printed on failure.
No formal result is claimed from harness mocks or historical reports.
