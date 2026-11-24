# SDK and example portable skill names code review, cycle 2

## Scope and Repository State

Reviewed the Full 5-Step Pipeline's SDK ticket against `4f4d683`. No changes were staged at the time of review. Scope covered the Java core and Spring Boot starter, sync and async Python clients, wire and distribution fixtures, both SDK READMEs, the quickstart script and fixture topology, and the packaged authoring examples. The untracked quickstart script and test were included. The user-supplied Framework handoff and companion ticket were inputs. The Sidecar runtime, server-side tests, and server-only contract documentation were deferred to the companion ticket.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This review changed only this artifact.

## Acceptance-Criteria and Plan Conformance

| Criterion or decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| All three SDK surfaces discover and reuse an explicit hyphenated identity | `SidecarClient` and Python `_protocol`, `_client`, `_async_client` build independent path segments. Both SDKs require response `namespace` and `localName`. The submit body stays a business object, and polls use the accepted UUID beneath the configured API origin. | Java `discoversSubmitsAndAwaitsOpaqueResult`, Python sync and async workflow tests, and response parser edge tests assert raw paths, credential and payload fidelity, returned identity, status and opaque result. | Implemented |
| Replace public and example signatures | Core and starter callers compile against the new methods. Python CLI entry points accept explicit components and FastAPI requires both configuration settings. The primary examples select a matching pair from the namespace catalog and reuse the returned identity. Source audit found no compatibility overload or old response-field fallback. | Full SDK tests and fresh wheel and source archive installs exercise the packaged examples and strict type checks. Invalid old-only and incomplete known fields fail parsing. | Implemented |
| Run the renamed planner and preserve both HTTP targets | `invoke.py` lists `default`, finds `quickstart-planner`, reads detail, submits once and polls by ID. The YAML planner references the two renamed leaves in its owner. The configured model stub returns qualified planner/worker identities while the leaves keep their HTTP paths, roles and input keys. The packaged `read-record` and optional triage examples preserve `recordId` and `customerMessage`. | Quickstart wire tests verify returned pair selection, exact UTF-8 body, bearer handling, redirect refusal, and accepted-ID polling. Packaged authoring tests pass. Actual planner and callback execution against Sidecar is sequenced after the server routes by the ticket. | Implemented for the SDK commit gate |

## Active Project Guardrails

- The SDKs and examples replace the old contract directly, and the design needs no compatibility surfaces, migration machinery, or development-data reset.
- Sidecar remains authoritative for portable naming validation. Both SDKs keep their existing single-segment transport guards and preserve spelling for transmitted inputs. Java retains its Framework and Sidecar dependency boundary.
- Authentication and dispatch remain separate from business input. The SDKs keep transport behavior, and the quickstart does not follow credentialed redirects or a response-provided polling location. Provider, callback and authoring examples use synthetic values and preserve the intended claims, paths and payload keys.
- Result and separate diagnostic fields remain opaque to the identity replacement. The docs and authoring examples describe the replacement client API directly.

## Open Questions and Assumptions

None for this commit gate. The installed Framework-backed server is verified with the companion ticket, where the replacement routes and metadata become available.

## Verification Results

- PASS — `mvn -o -B -ntp -f sdks/java/pom.xml -pl core '-Dtest=SidecarClientTest,SidecarClientEdgeTest' test` — 18 passed.
- PASS — `mvn -o -B -ntp -f sdks/java/pom.xml clean verify` — 75 passed and three opt-in interoperability checks skipped under native Windows permissions. The earlier sandbox clean could not delete the generated SDK jar.
- PASS — `python sdks/java/tests/test_build_boundary.py` — three passed, including the disposable reactor checks for direct and transitive forbidden dependencies.
- PASS — `target/portable-sdk-venv/Scripts/python.exe -m pytest sdks/python/tests/test_sync_client.py sdks/python/tests/test_async_client.py sdks/python/tests/test_protocol.py -k 'workflow_preserves_wire_contract or catalog_and_acceptance or known_fields or invalid_calls'` — 43 passed.
- PASS — `target/portable-sdk-venv/Scripts/python.exe -m pytest sdks/python/tests` — native retry passed with 484 passed and two POSIX-specific skips. The initial sandbox run could not create the existing synthetic hard link and had one transient failure in an unchanged wait-deadline test. Both passed on the native retry.
- PASS — `uv build --out-dir target/portable-sdk-review2-dist sdks/python` and `target/portable-sdk-venv/Scripts/python.exe sdks/python/tests/verify_distribution.py --dist-dir target/portable-sdk-review2-dist` — offline cache. Fresh wheel and source archive verified with sync and async CLI, FastAPI in both signing modes and strict typing. The initial launch with an in-repo `TMP` hit the harness's external-installation guard. The successful invocation used the shell's external temporary directory.
- PASS — `python -m unittest discover -s scripts -p 'test_quickstart.py' -v` — two passed.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` — five passed and one host symlink capability skip.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — 11 passed.
- PASS — `git -c core.safecrlf=false diff --check 4f4d683` — no whitespace errors. Untracked ticket-scope code, tests and governance artifacts were reviewed directly.

## Residual Risks and Optional Developer Checks

The SDK commit targets the forthcoming server wire contract. Run the actual deterministic planner and both callbacks against Sidecar during the companion ticket before treating the combined work as verified. The optional real-provider triage walkthrough was outside verifier scope.

## Disposition

`clean`. Independent verification found no actionable SDK or example issue at the first commit gate. The companion pipeline owns the combined runtime check.
