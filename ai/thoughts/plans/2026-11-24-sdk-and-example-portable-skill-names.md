# SDK and example portable skill names implementation plan

## Overview

- Ticket `ai/thoughts/tickets/2026-11-24-sdk-and-example-portable-skill-names.md`
- Research `ai/thoughts/research/2026-11-24-sdk-and-example-portable-skill-names.md`
- Profile Full 5-Step Pipeline, preapproved by the developer. Implement, verify and independently review the SDK change before its local commit. Start the companion server ticket after that commit. Run combined Sidecar integration when that ticket provides the routes and fields. No synchronization.

## Current State

`SidecarClient` in Java and both Python clients construct `/v1/skills` URLs, use a single target string, and parse descriptor `name` and snapshot `skillName`. Path segment escaping, credential dispatch, ID polling and raw diagnostic parsing already have reusable implementations. The Java starter returns the core client. The Python CLI examples have target-taking `run` functions and the FastAPI example reads a single optional target from the environment. The SDK library clients have no further target-taking invocation convenience methods.

`examples/quickstart/invoke-triage.py` runs the model-only example in `triage-support-request.yaml`. The separate `sidecar/skills/` fixture tree already declares a planner and two distinct HTTP leaves. `host/host.py` has deterministic plan and worker responses plus JWT and HTTP callback verification. `sidecar/execution.yaml` currently has the HTTP targets and needs the local model configuration. Neither the published example command nor the current deployment verifier runs those planner files today.

## Desired End State

- Java and the synchronous and asynchronous Python clients list an explicit namespace, read a hyphenated local name from the catalog, use the returned pair for detail and submit, and get that pair back in the UUID-polled snapshot. Credentials, JSON input, opaque results and diagnostic fields keep their existing contracts.
- SDK examples and other callers supply the pair explicitly. The quickstart walkthrough publishes and runs `default:quickstart-planner`, calls the existing two authenticated leaves via `default:http`, and shows the terminal result and callback evidence. The standalone triage example retains its original support-report input and model behavior under its portable name.

## Scope

### In scope

- Both SDK libraries, their public snippets, direct consumers, test fixtures and packaged example verification.
- Quickstart walkthrough, runnable client, authored skills, local model fixture configuration and deterministic provider responses.
- Consumer and packaged authoring examples from the Framework inventory. Update the execution route examples and retained identity documentation in the shared integration reference as the consumer contract used by these examples.

### Out of scope

- Sidecar controllers, queue, management models, loader validation, server test fixtures, deployment verifiers and server-only documentation about inspected skills and physical loading boundaries. These belong to `2026-11-24-sidecar-portable-skill-names.md`.
- New publication capability, authentication changes, Framework changes and Cloud work.
- The two tickets and `ai/thoughts/future/portable-skill-naming.md` were untracked user-supplied inputs at `4f4d683`, with no tracked or staged diffs. Preserve those inputs and any unrelated work. Keep the companion server ticket out of the SDK implementation changes.

## Active Project Guardrails

**Destructively replace superseded naming methods, fields and examples.** Apply the [design lens](../design-lens.md#simplicity-and-technical-debt), including current-contract documentation, Framework authority, trusted identity and diagnostic fidelity. The SDK reactor keeps its Framework and Sidecar dependency boundary. No development-data reset is required for the SDK change. Quickstart verification uses a fresh isolated configuration and synthetic values. It does not delete or reset an existing volume, database or signing directory.

## Impact and Risk Analysis

The route and model replacement is intentionally breaking for the undeployed versions. Successful responses with missing or nontext identity fields must fail protocol parsing. A local hyphen does not delimit an owner. Separately escape each segment using the existing transport protection, preserve the caller's spelling, and let Sidecar apply authoritative portable grammar and bounds. In particular, no trim, lowercase conversion, prefix removal or fallback belongs in the clients. Keep the existing caller/credential lifecycle and submit exactly once. No compatibility gate depends on a live provider or production deployment.

The current real-provider guide cannot prove planner leaf selection. Make the deterministic planner the primary runnable tutorial because its declarations, local stub and authenticated callbacks already exist and require no provider account. Keep the real-provider triage example as an optional separate configuration with its existing payload, declaration logic and variable-reference instructions.

## Implementation Approach

Use a namespace catalog path and a local-name path constructed from it. Parse namespace and local name directly as required strings on SDK records. Reuse catalog or detail components at all SDK/example call sites that obtain metadata. Update the deterministic host's structured plan and worker fields to qualified colon names. Keep local child references bare inside a YAML document owned by `default`, and keep bare `executor: http` with its existing owner configuration, target settings, paths, claims, arguments and output. The Framework owns provider function-call names and their double-hyphen rendering.

## Phase 1: Replace client APIs and response identity

### Changes

- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/SidecarClient.java` changes `listSkills(namespace, credential)`, `getSkill(namespace, localName, credential)` and `submit(namespace, localName, input, credential)`. Share the catalog path and escape both supplied components separately. `descriptor` and `snapshot` read `namespace` and `localName` as required text.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/SkillDescriptor.java` and `ExecutionSnapshot.java` expose `namespace()` and `localName()`. Retain independent `ExecutionFailure.producingSkillName()` diagnostic text.
- [x] `sdks/python/src/loomspan_sdk/_client.py`, `_async_client.py`, `_protocol.py` and `_models.py` make the symmetric public replacement using `list_skills(namespace, credential)`, `get_skill(namespace, local_name, credential)` and `submit(namespace, local_name, input, credential)`. Both use the shared catalog/detail path builders and parsers. Public models expose `namespace` and `local_name`. Keep independent `producing_skill_name`, poll paths and exception context.
- [x] `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTest.java`, `SidecarClientEdgeTest.java`, `SidecarClientTlsTest.java`, `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SidecarClientAutoConfigurationTest.java` and `SpringExecutionTokenIssuerTest.java` use the new calls/fixtures. Assert literal wire methods, paths, UUID, headers, business body and both identities.
- [x] `sdks/python/tests/test_sync_client.py`, `test_async_client.py`, `test_protocol.py` and `test_fastapi.py` use new calls and wire fixtures while retaining the existing transport, failure and credential assertions. The primary workflows reuse the pair from catalog metadata through submit and terminal polling.

### Automated verification

- [x] `mvn -B -ntp -f sdks/java/pom.xml clean verify` and `python sdks/java/tests/test_build_boundary.py` pass with the core and starter target and identity replacement.
- [x] `python -m pytest sdks/python/tests` from an SDK-enabled environment passes sync, async, parsing and callback tests.

### Optional developer checks

- None.

## Phase 2: Adopt the API in SDK examples and installed package checks

### Changes

- [x] Update `sdks/java/README.md` and `sdks/python/README.md` to use an explicit namespace for listing and the catalog or detail pair for later operations. Explain the pair, separate paths, retained ID polling and existing diagnostic properties. The configured issuer and credential calls stay as written.
- [x] `sdks/python/examples/existing_credential.py` and `existing_credential_async.py` take namespace and local name in `run` and as separate CLI arguments. List that namespace and use the selected descriptor's pair for submission. `fastapi_app.py` requires `LOOMSPAN_SKILL_NAMESPACE` and `LOOMSPAN_SKILL_LOCAL_NAME`; preserve `/submit`, callback routes and exact JSON body keys. Update related commands, required settings and snippets in `sdks/python/README.md`.
- [x] `sdks/python/tests/verify_distribution.py` and `verify_fastapi_example.py` run each shipped example from installed wheel/sdist using explicit namespace/local pairs, a hyphenated local name and unchanged synthetic business data. Assert the exact request targets and UUID polling.

### Automated verification

- [x] `python sdks/python/tests/verify_distribution.py` builds and installs both artifacts and verifies the standalone and FastAPI examples with local peers. Strict example type-checks continue to pass.

### Optional developer checks

- None.

## Phase 3: Make the quickstart and authoring examples executable against the same contract

### Changes

- [x] In `examples/quickstart/sidecar/skills/planner.yaml`, `identity-leaf.yaml` and `detail-leaf.yaml`, use `quickstart-planner`, `identity-leaf` and `detail-leaf`; update `allowed_skills` to the exact renamed local leaves. Preserve `message`, task count/order, targets, role, `executor: http` and callback routes. Convert `examples/quickstart/host/host.py` plan and structured worker identity values to `default:quickstart-planner`, `default:identity-leaf` and `default:detail-leaf`; preserve task IDs, exact arguments, callbacks and the terminal result.
- [x] Complete `examples/quickstart/sidecar/execution.yaml` with the existing `localplanner` alias, an OpenAI-compatible connection to `http://127.0.0.1:8081/v1`, provider model `deterministic-planner` and synthetic fixture-only key. Keep the existing HTTP settings. Use the authored configuration in the walkthrough and check the publisher approves the two loopback passthrough targets.
- [x] Replace the one-example request script `examples/quickstart/invoke-triage.py` with `examples/quickstart/invoke.py`, a small standard-library client taking namespace and local name separately and a JSON business object via stdin. Acquire the same fixture token, list the namespace, use the selected catalog pair for detail and submission, then poll the accepted UUID under the configured local origin. Preserve the one-submit behavior, deadlines, opaque result and failure reporting. The explicit CLI arguments are needed for both the primary and optional example, so either can use the same script without an implicit default.
- [x] In `docs/quickstart.md` and `examples/quickstart/README.md`, document the existing Compose setup, publication of the three fixture documents and execution YAML, `default quickstart-planner` invocation, expected `quickstart complete` and inspection of the two verified callbacks. Put the real-provider instructions behind an optional triage section. Keep the original `customerMessage` business sample in `README.md` and that triage instructions path, convert its declaration in `triage-support-request.yaml` to `triage-support-request`, and use the explicit route in the root README.
- [x] In `docs/http-executor.md`, `agent-skills/loomspan-sidecar-authoring/references/rest-configuration.md`, `references/client-usage.md`, `examples/remote-authoring/record.yaml` and `scripts/test_agent_skills.py`, update the `update-order` and `read-record` declarations/references. Keep `recordId`, REST paths, headings and source labels. Update `references/integration.md` consumer example routes, the requested pair and resolved `producingSkillName` example. Keep the meaning and contents of separate issue and diagnostic fields. Check the linked inventory documents, leaving only server-owned inspection/loader details and deployment verification to the companion work.
- [x] Cover the runnable quickstart client using a local HTTP double or patched `urlopen` in `scripts/test_quickstart.py`. Assert explicit catalog namespace, returned pair reuse, business payload, and ID poll sequence. Verify authored references and actual HTTP callbacks through the combined Sidecar integration gate after the companion server becomes available.

### Automated verification

- [x] `python -m unittest discover -s scripts -p 'test_quickstart.py' -v` passes the runnable planner client path against local synthetic responses.
- [x] `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` and `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` pass the packaged example and authoring client checks.
- [ ] After the companion routes are implemented, execute the real local planner in `default` using the installed beta.10 Sidecar dependency, checked-in example YAML and deterministic host. Verify its catalog and retained pair, exact callbacks and terminal output. Use native local processes if Docker remains unavailable. Record the evidence with companion verification.

### Optional developer checks

- Observe triage output against the reader's configured external provider only if deliberately running the optional triage example. Routine verification uses no live provider and this is not a completion gate.

## Test Strategy

Exercise each SDK over a loopback HTTP peer and extend shared parser negative fixtures. Exercise the examples installed as distributions and the packaged authoring bundle through their maintained harnesses. Run the quickstart client against a local double before commit and verify the same runnable planner against the installed Sidecar and deterministic model/callback host after the server change. See the [testing plan](2026-11-24-sdk-and-example-portable-skill-names-testing.md).

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Java and both Python clients reuse a hyphenated identity and preserve input and UUID polling | Java `SidecarClient` and records, Python shared protocol/models and both client classes | Java `SidecarClientTest`; Python sync/async workflow tests specify exact catalog/detail/submit paths, parsed pairs, business body and polling targets |
| Public snippets and convenience methods adopt the replacement signatures | Java/Python READMEs, both `run` signatures, FastAPI explicit settings; removed SDK declarations and absent fallback in parsers | Compile SDK and test callers, installed wheel/sdist example checks, shared parser cases for incomplete/legacy identity, source/call-site audit |
| Quickstart planner invokes intended HTTP leaves with portable naming, current consumer and authoring examples | `invoke.py`, walkthrough, planner/leaf manifests, deterministic host, HTTP reference and packaged record example | Quickstart wire test and authoring bundle test before SDK commit; real Sidecar planner completion and both distinct authenticated callbacks after server implementation |

## Risks and Rollback/Recovery

The SDK commit is deliberately one side of the coordinated contract change. Until the follow-up server work lands, the old Sidecar endpoints cannot serve these clients. Independent SDK verification uses the scheduled replacement contract. The final combined check uses isolated keys/configuration and stops temporary processes without changing existing installations. If a defect is found, fix the affected current-contract change and repeat verification. No restoration of obsolete overloads, routes or schemas is needed.

## References

- [SDK and example ticket](../tickets/2026-11-24-sdk-and-example-portable-skill-names.md), [companion server ticket](../tickets/2026-11-24-sidecar-portable-skill-names.md), [research](../research/2026-11-24-sdk-and-example-portable-skill-names.md), [Framework handoff](../future/portable-skill-naming.md)
- `sdks/java/pom.xml`, `sdks/python/pyproject.toml`, `examples/quickstart/compose.yaml`, `agent-skills/loomspan-sidecar-authoring/references/integration.md`
