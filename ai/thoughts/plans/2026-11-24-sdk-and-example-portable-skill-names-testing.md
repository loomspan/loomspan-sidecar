# SDK and example portable skill names testing plan

## Change Summary

Verify Java, synchronous Python and asynchronous Python reuse the pair returned by the namespace catalog. Exercise the public, packaged and quickstart examples over the same routes and confirm UUID polling retains the selected pair and available result or diagnostic evidence. Verify actual planner dispatch through both authored leaves after the companion server supplies the wire contract.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Public client methods | Signature or caller still assumes one name or `default` | Core/starter compilation, both Python test suites, strict installed-example type checks, public signatures and call-site audit |
| HTTP protocol | Namespace and local name joined, split, normalized or lost at an origin prefix | Primary workflow fixtures assert literal separate segments for `order-support` and `lookup-3pl`, existing prefix, exact authorization, request body and UUID-only poll path |
| Transport safety | Either component can change path boundaries or bypass pre-dispatch validation | Shared segment tests for each argument, existing Java edge and Python invalid-call tests with zero dispatch |
| Serialized identity | SDK accepts legacy-only or incomplete identity or rewrites independent diagnostic text | Missing/wrong-type field cases and exact pair assertions, existing result, failure and diagnostic event regression checks |
| Authored names | Planner selects the wrong child, worker emission resolves incorrectly, or HTTP routing changes | Inspect authored local and qualified reference spellings, then run against the real local Sidecar and assert separately authenticated `/callbacks/identity` and `/callbacks/detail` |
| Documented usage | Published instructions use wrong CLI settings, omit example configuration or contain obsolete names | Installed CLI and FastAPI example harnesses, packaged authoring verifier, runnable quickstart client and current-contract inventory audit |

## Existing Coverage and Environment Constraints

- `SidecarClientTest` already has a Java loopback peer and `SidecarClientEdgeTest`, `SidecarClientTlsTest`, and starter tests cover accepted/failure/authentication behavior. The SDK reactor uses Java 21 and `mvn`; the wrapper can use the same reactor when standalone Maven is unavailable. The release repository is outside the build. Maven interop tests that depend on opt-in fixture environment variables may be skipped in the normal reactor and should be reported explicitly.
- Python's `WirePeer` records exact raw targets, request bodies and credentials; shared `test_protocol.py` owns descriptor and snapshot validation. Use an isolated Python 3.12+ environment installed from `sdks/python[fastapi]` and `sdks/python/requirements-dev.txt`. The host's Python 3.13 and `uv` are present, and the bare interpreter does not have SDK dependencies. The existing host uv cache at `C:/Users/mgiacomi/AppData/Local/uv/cache` contains cached compatible wheels including `hatchling`, Python 3.13 cryptography, FastAPI and test tools. If package index access fails, use this cache with `UV_OFFLINE=true` and `UV_CACHE_DIR` for the verification commands. If the cache is read-restricted for locks, copy cached artifacts into a disposable writable cache under ignored `target/` or the temporary directory, preserving effective wheel sources; do not change project dependency bounds.
- `verify_distribution.py` drives its own fresh external installation environments and cleans them up. Use the same uv settings for its child processes when needed. Python signing tests with platform assumptions report the explicit skips made by the test definitions.
- Before the SDK commit, local loopback peers intentionally implement the companion ticket's route contract. The SDK reactor does not need the Framework starter. The server remains at its old naming API until the next ticket. Actual quickstart planner and callback execution is a combined verification gate after that work, as the SDK ticket directs.
- The Docker CLI exists but the daemon could not be reached during triage. The combined gate may use native Sidecar JVM and checked-in Python host with an isolated directory for files, key export and database. The host needs OpenSSL and port 8081. Free the intended test ports or use existing test-side configuration, and keep model/JWT/callback HTTP on loopback. A Windows file-sharing issue opening the host fixture's temporary signature file in OpenSSL would be a fixture issue to confirm and fix within this scope if observed.

## Failing Test First

- **Name** `test_workflow_preserves_wire_contract`
- **Type** Synchronous Python HTTP client integration against `WirePeer`
- **Location** `sdks/python/tests/test_sync_client.py`
- **Arrange/Act/Assert** Serve a descriptor for `order-support` and `lookup-3pl`; list with the explicit namespace, call detail and submit using the returned components, and inspect the polled identity from `EXECUTION_ID`. Assert the exact request sequence and preserved business body. Give the acceptance response a different `Location` as the current fixture does.
- **Expected pre-fix failure** The old `list_skills` signature rejects the explicit namespace before any dispatch. After changing the method signature, the old route or model would fail the same workflow.

## Tests to Add or Update

### 1. `discoversSubmitsAndAwaitsOpaqueResult`

- **Type** Java public HTTP client loopback test
- **Location** `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTest.java`
- **Proves** Catalog namespace request, returned pair for detail/submit, both identities from the response models, one POST with full business data, ID-only polling and opaque result fidelity.
- **Inputs and isolation** In-process Java HTTP peer with prefix `/prefix/`, namespace `order-support`, local name `lookup-3pl`, synthetic bearer, nested object input and generated UUID. Capture requests on the peer and assert from the test runner, including `GET`/`POST` ordering and the untrusted `Location` being unused.
- **Edge cases** Retain bad requests and malformed successful responses in `SidecarClientEdgeTest.java`. Extend identity-required success-body cases and path validation for both components. Update `SidecarClientTlsTest.java`, `SidecarClientAutoConfigurationTest.java` and `SpringExecutionTokenIssuerTest.java` to exercise their actual SDK behavior through the replacement API.

### 2. `test_workflow_preserves_wire_contract` and `test_async_workflow_preserves_wire_contract`

- **Type** Python public sync and async HTTP client integration tests
- **Locations** `sdks/python/tests/test_sync_client.py` and `sdks/python/tests/test_async_client.py`
- **Proves** In both clients, expected ordered raw requests with the encoded origin prefix, explicit namespace, exact hyphenated local name and UUID poll path. Preserve exact bearer, JSON business object, status transition and opaque data. Assert the catalog, detail and terminal identities match.
- **Inputs and isolation** Existing `WirePeer`, `descriptor` and `snapshot` fixtures. Replace their legacy requested identity values while leaving independent event and failure data untouched. Keep native loopback peers and test timeout/cancellation harnesses.
- **Edge cases** Existing invalid-call, connection reuse, TLS, redirect, interruption and ambiguity tests exercise updated signatures. Verify local path-invalid input is stopped for both component positions. Update `sdks/python/tests/test_protocol.py` required-field and malformed-success coverage to exercise missing, null and wrong types for both identity values and legacy-only payloads. Preserve timestamp, output schema failure producer and event case coverage. Update actual SDK method usages in `sdks/python/tests/test_fastapi.py` while preserving callback tests.

### 3. Installed Python examples

- **Type** Installed package and public example verification
- **Locations** `sdks/python/tests/verify_distribution.py`, `sdks/python/tests/verify_fastapi_example.py`, and `sdks/python/examples/`
- **Proves** Both shipped CLI entry examples accept namespace and local name, discover only the requested catalog and reuse its returned metadata. The FastAPI example reads both explicit settings and invokes the same replacement target while retaining application and callback routes. Verify request body and accepted UUID polling, wheel and sdist exports and strict example type checks.
- **Inputs and isolation** Synthetic credential, explicit non-default namespace and `lookup-3pl` or a similar hyphenated skill. Use the existing loopback peer, temporary uv environments and FastAPI test harness. Do not introduce an implicit selection in test configuration.

### 4. Runnable quickstart and packaged authoring example

- **Type** Standard-library client boundary and packaged artifact tests before the SDK commit; live local integration after the server change.
- **Locations** `scripts/test_quickstart.py`, `scripts/test_agent_skills.py`, `examples/quickstart/invoke.py`, `examples/quickstart/sidecar/skills/`, and `examples/quickstart/host/host.py`.
- **Proves** The documented quickstart invocation accepts separate `default` and `quickstart-planner` arguments, lists and locates the catalog pair, submits unchanged `message` business input, and polls the accepted ID with the fixture bearer. Packaged authoring checks confirm `read-record` preserves its HTTP executor, path parameters and exact business schema key.
- **Inputs and isolation** Patch `urlopen` or use a local HTTP peer, seed a synthetic token/catalog record and accepted UUID, then serve a known terminal result. Check the actual runnable entry point, with no provider or shared credentials.
- **Combined runtime gate** Once the Sidecar ticket implements the routes, start an isolated Sidecar and the deterministic host, publish or start file mode with the checked-in configuration and all three skill YAML documents, and invoke through `python examples/quickstart/invoke.py default quickstart-planner` with the documented JSON on stdin. Confirm authenticated catalog/detail names, accepted-ID poll identity `default` plus `quickstart-planner`, result `quickstart complete`, and `/status` records for both `/callbacks/identity` with `verify` and `/callbacks/detail` with `detail`. Check JWT subject `quickstart-user`, exact `QUICKSTART_USER` role and original callback path arguments. The installed Sidecar performs the Framework and annotated HTTP executor work. Record topology, actual commands, and results under the companion pipeline. If using file mode for the example artifact, the server ticket still verifies database publication through its integration suite.
- **Documentation audit** Search current product and example paths for legacy SDK signatures, CamelCase authored declarations, implicit target settings, old endpoints, and legacy-only requested identity fields. Distinguish observer `producingSkillName`, provider data, source labels, business keys and old paths deliberately exercised for removal. Review the inventory targets named in the implementation plan.

## Safe Verification Commands

From the repository root, use the following commands. An isolated Python environment can be created with `uv venv target/sdk-portable-names-venv`; on Windows install into it with `uv pip install --python target/sdk-portable-names-venv/Scripts/python.exe -r sdks/python/requirements-dev.txt './sdks/python[fastapi]'`. Activate that environment or call its executable for `python` commands.

- **Focused Java** `mvn -B -ntp -f sdks/java/pom.xml -pl core -Dtest=SidecarClientTest,SidecarClientEdgeTest test` (quote the `-Dtest=...` argument if the shell treats the comma as syntax).
- **Focused Python** `python -m pytest sdks/python/tests/test_sync_client.py sdks/python/tests/test_async_client.py sdks/python/tests/test_protocol.py -k 'workflow_preserves_wire_contract or catalog_and_acceptance or known_fields or invalid_calls'`.
- **Full SDKs** `mvn -B -ntp -f sdks/java/pom.xml clean verify`; `python sdks/java/tests/test_build_boundary.py`; `python -m pytest sdks/python/tests`; `python sdks/python/tests/verify_distribution.py`.
- **Quickstart and authoring** `python -m unittest discover -s scripts -p 'test_quickstart.py' -v`; `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`; `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`.
- **Integrity** `git diff --check`, and inspect the complete ticket-scoped diff and the intentionally deferred server runtime files. No production service, provider, registry push, or destructive cleanup is part of the gate.

## Optional Developer Checks

- If deliberately trying the separate real-provider triage example, observe its output quality in a configured evaluation setup. It is not required for either portable-name gate.

## Exit Criteria

- [x] The first workflow shows the intended missing two-component contract before the implementation, or reports an exact environment reason why the red check could not run.
- [x] Each SDK's loopback workflow and tests pass with an explicit namespace, hyphenated local name, preserved input and diagnostic evidence, and exact UUID polling.
- [x] Standalone and packaged examples use the new APIs and separate arguments or required settings. Any skips are attributed to their actual command and reason.
- [x] Quickstart client and packaged authoring tests pass before the SDK commit. The actual combined planner run, including both authenticated callbacks, is scheduled in the follow-up as specified in the ticket and recorded when run.
- [x] The executable checks and source/doc review account for every acceptance criterion. No optional configured-provider observation is represented as run.

## Step 4 verification on 2026-11-24

| Command | Result |
| --- | --- |
| `mvn -o -B -ntp -f sdks/java/pom.xml -pl core -Dtest=SidecarClientTest test` | Expected pre-change failure at test compilation. The original client lacked namespace-scoped listing and the execution snapshot accessors. |
| `mvn -o -B -ntp -f sdks/java/pom.xml clean verify` | Passed. 78 tests counted, zero failures or errors. Four skips cover a host symlink assumption and three opt-in interoperability fixtures. |
| `python sdks/java/tests/test_build_boundary.py` | Passed, three tests. |
| `target/portable-sdk-venv/Scripts/python.exe -m pytest sdks/python/tests` | Passed with native filesystem permissions, 484 passed and two POSIX symlink cases skipped on Windows. The sandbox-only attempt could not create the unchanged synthetic hard-link test fixture. |
| `uv build --out-dir target/portable-sdk-dist-f891fcb7-1698-4643-8879-d1490880fc45 sdks/python` followed by `target/portable-sdk-venv/Scripts/python.exe sdks/python/tests/verify_distribution.py --dist-dir target/portable-sdk-dist-f891fcb7-1698-4643-8879-d1490880fc45` | Passed with the local uv cache and offline resolution. Both installed artifacts exercised the sync and async CLI examples, both FastAPI signing modes and strict type checks. Building into the workspace avoids treating the hash in the sandbox temporary directory as a URL fragment. |
| `python -m unittest discover -s scripts -p 'test_quickstart.py' -v` | Passed, one test. The actual CLI parsed the explicit pair and JSON business input, used the catalog identity, posted once and polled by the accepted ID despite the fixture's unrelated Location. |
| `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` | Passed, five passed and one Windows symlink capability skip. |
| `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` | Passed, 11 tests. |
| `git diff --check` | Passed. |

The example source review matched the renamed planner and leaves to their existing
HTTP paths, role, executor, task IDs and arguments. Framework-backed execution of
the checked-in quickstart against the replacement Sidecar routes is scheduled
after the companion server ticket, as the SDK ticket specifies. The local fixture
passed a synthetic OpenSSL issue/verify smoke under native execution permissions.
