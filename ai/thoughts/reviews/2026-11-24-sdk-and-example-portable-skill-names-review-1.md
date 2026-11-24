# SDK and example portable skill names code review — Cycle 1

## Scope and Repository State

Reviewed the working tree against `4f4d683` on 2026-11-24. The Java core and starter, synchronous and asynchronous Python clients, distribution examples, quickstart, and packaged authoring guidance are in scope. The working tree had no staged changes. Inspected the untracked quickstart entry point and its test, the ticket and planning artifacts, and the Framework documentation inventory. The pre-existing sibling ticket and Framework handoff remain developer inputs. Sidecar HTTP routes, deployment smoke and file/archive loading belong to the sibling ticket.

## Findings

No actionable findings remain after the fixes below.

### [P2] Keep the quickstart bearer on the configured request

- Location: `examples/quickstart/invoke.py:13` (the request opener introduced here; prior discovery used `urlopen`).
- Scenario: A redirect answers one of the new authenticated catalog or detail GETs.
- Impact: The standard-library default redirect handler copies the bearer header and issues a new request to the redirect target. The fixture credential carries execution permission.
- Evidence: The new loopback test first observed a 404 from the redirect destination instead of the original 302. Python's handler copies request headers other than the entity headers.
- Fix: Use one explicit no-redirect opener for the fixture token and all Sidecar requests. The loopback regression verifies the 302 and the complete request sequence, including the absence of any request to the alternate origin.

### [P2] Preserve UTF-8 business input from Windows pipes

- Location: `examples/quickstart/invoke.py:63`.
- Scenario: A caller pipes a UTF-8 JSON report through a Windows locale whose default text codec differs from UTF-8.
- Impact: The newly generic client could silently change non-ASCII business data before submission. The prior client used a literal message and did not decode arbitrary input from a pipe.
- Evidence: With a synthetic cp1252 stream around UTF-8 bytes, the workflow test captured different bytes in the POST body before the fix.
- Fix: Read the raw stdin bytes, decode strictly as UTF-8 for JSON-object validation, and transmit the original bytes. State the input encoding in the walkthrough.

## Findings Resolved in This Context

Both quickstart findings had failing executable regressions before the fixes. The same two regressions pass with the corrected client. Review fixes touched the entry point, its test and the quickstart wording. The SDK libraries and installed distribution examples were unchanged in this context.

## Acceptance-Criteria and Plan Conformance

| Criterion or decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| List an explicit namespace, look up and submit a catalog pair with a hyphen, then poll by accepted ID | Java `SidecarClient`, Python clients and `_protocol.py` build two independent escaped segments. Both models require the two wire fields and retain independent failure and event evidence. | Main Java, Python sync and Python async loopback tests assert exact paths, business data, credential, catalog/detail identities, and same-ID snapshots. Parser and transport tests cover malformed identity and path-boundary rejection. | Implemented |
| SDK examples use the pair without obsolete single-name calls or defaults | Java and Python README snippets, both Python CLI `run` methods, and explicit FastAPI identity environment variables. | Full Java and Python runs, real dependency-boundary reactors, installed CLI/FastAPI examples and strict type checks. Searches for obsolete names in SDK code and examples found no live method or fallback. | Implemented |
| Runnable quickstart invokes the renamed planner and preserves leaf/executor references | `invoke.py`, `examples/quickstart/sidecar/skills/`, execution YAML and structured responses in fixture host. Both leaves retain the exact HTTP targets and paths, and the optional report retains `customerMessage`. | Quickstart regressions exercise discovery, exact body, one submission, polling and redirect refusal. The host independently signed and verified a native OpenSSL fixture JWT. Actual planner execution against Sidecar follows the explicitly ordered companion ticket. | Implemented at SDK/fixture boundary |
| Consumer and packaged authoring guidance describes the current calls and declarations | Root README, walkthrough, SDK READMEs, integration and HTTP references, remote authoring `record.yaml` and client-usage reference. | Pack verifier and isolated authoring client tests, plus source and documentation audit against the Framework inventory. | Implemented |

## Active Project Guardrails

- The old SDK signatures and requested identity fields were replaced directly. No compatibility paths, additional dependencies or client fallback were introduced.
- SDK transport retains caller spelling. Sidecar owns transport-level naming validation through its companion work and Framework owns execution. Project build files and Framework API dependencies were unchanged.
- Credential capture and application paths are unchanged in the SDKs. The quickstart sends a fixture credential solely on its configured requests and preserves the raw business body.
- Resolved names in the synthetic planner responses follow the new identity contract. HTTP callback paths, roles and business values stay intact. Retained output and diagnostic contents are not normalized.

## Open Questions and Assumptions

The independent SDK/fixture review does not answer runtime integration questions about the new Sidecar endpoints. The ticket explicitly defers that combined check until the companion routes exist. Existing server uses of the prior naming API and the deployment verifier's catalog request were excluded from this review and remain companion scope.

## Verification Results

- PASS — `mvn -o -B -ntp -f sdks/java/pom.xml -pl core '-Dtest=SidecarClientTest,SidecarClientEdgeTest' test` — 18 passed. An initial unquoted PowerShell command failed at shell parsing; the quoted command ran the tests.
- PASS — `mvn -o -B -ntp -f sdks/java/pom.xml clean verify` — 74 passed and four skipped for platform/opt-in fixtures.
- PASS — `python sdks/java/tests/test_build_boundary.py` — three passed, including direct and transitive forbidden dependencies.
- PASS — `target/portable-sdk-venv/Scripts/python.exe -m pytest sdks/python/tests/test_sync_client.py sdks/python/tests/test_async_client.py sdks/python/tests/test_protocol.py` — 252 passed.
- PASS — `target/portable-sdk-venv/Scripts/python.exe -m pytest sdks/python/tests` — 484 passed, two POSIX-platform skips. Native permissions allow the existing hard-link case.
- PASS — `uv build --out-dir target/portable-sdk-review1-dist sdks/python` and `target/portable-sdk-venv/Scripts/python.exe sdks/python/tests/verify_distribution.py --dist-dir target/portable-sdk-review1-dist` — fresh wheel and source archive built and verified offline using the disposable cache. Both CLI examples, both FastAPI signing modes and all strict example type checks passed.
- FAIL (expected red) and PASS after repair — `python -m unittest discover -s scripts -p 'test_quickstart.py' -v` — both tests reproduced their respective findings, then passed after the fixes.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` — five passed and one Windows symlink skip.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — 11 passed.
- PASS — `python -c "import runpy; h=runpy.run_path('examples/quickstart/host/host.py'); h['export_fresh_key'](); token=h['issue'](); assert h['verify'](token)['sub'] == 'quickstart-user'"` — native OpenSSL with a new ignored build directory for `QUICKSTART_KEY_DIRECTORY`.
- PASS — `git -c core.safecrlf=false diff --check 4f4d683` — no whitespace errors.
- NOT RUN — Real Sidecar quickstart planner, two authenticated HTTP callbacks and polled identity. This is the combined companion verification gate.

## Residual Risks and Optional Developer Checks

The SDK commit's contract is useful against the companion routes. The cross-ticket verification must run the authored planner with the local deterministic host and validate both verified callbacks and the terminal result. The optional paid-provider triage walkthrough was not exercised.

## Disposition

`fixes-applied`. A fresh independent review must verify this working tree before the SDK commit.
