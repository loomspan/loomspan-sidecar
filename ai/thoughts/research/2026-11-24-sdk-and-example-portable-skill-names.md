---
date: 2026-11-24
repository: loomspan-sidecar
branch: main
commit: 4f4d68307d6000255d8fd48e224143c5561b98f0
ticket: ai/thoughts/tickets/2026-11-24-sdk-and-example-portable-skill-names.md
tags: [java-sdk, python-sdk, http, quickstart, skill-naming]
---

# SDK and example portable skill naming research

## Research Question

Where do Java, synchronous and asynchronous Python, the quickstart, and consumer
guidance depend on a single skill name? What can the current loopback fixtures
verify independently of Sidecar's server contract?

## Summary

The Java and Python SDKs construct skill URLs from one name and parse catalog
entries and retained executions from separate single-name fields. Polling already
uses the acceptance UUID independently of those fields. The runnable
existing-credential and FastAPI examples exercise SDK methods against loopback
test peers, so their client verification does not require the Sidecar process.

The quickstart contains a real-provider triage example and a separate
deterministic planner with two authenticated HTTP leaves. The latter's Compose
host still provides the model and callbacks, but the current repository has no
planner invocation script, no current verifier that publishes those files, and
no complete model configuration in its fixture YAML.

## Repository State

Research began on 2026-11-24 at 09:34 Pacific on `main` at the commit above.
There were no tracked or staged changes. The two naming tickets and
`ai/thoughts/future/portable-skill-naming.md` were already untracked user-supplied
inputs. The companion Sidecar server ticket and source are distinct from the SDK,
client examples and authoring changes. The requested order is SDK implementation,
SDK verification and local commit, followed by Sidecar implementation and
integrated verification, with no synchronization. The SDK ticket explicitly
sequences real integration verification after the server supplies the routes
and fields.

## Current Behavior and Data Flow

### Java SDK

- `sdks/java/core/src/main/java/ai/loomspan/sdk/SidecarClient.java:94`: list takes
  only a credential, detail takes one name, and submit takes one name plus an
  object. The requests target `/v1/skills` and its single-name descendants.
  Submit serializes the input object as the entire body and parses the returned
  UUID. `getExecution` and `await` call `/v1/executions/{id}` with the credential.
- `SidecarClient.java:285`: the skill segment helper rejects unsafe single
  segments and percent encodes other byte values. It currently accepts
  uppercase, non-ASCII and punctuation that the portable component grammar
  excludes. The configured URI prefix is separately retained by `base`.
- `SidecarClient.java:297`, `SkillDescriptor.java:3` and
  `ExecutionSnapshot.java:6`: descriptors read mandatory `name`; snapshots
  read mandatory `skillName`, status/timestamps, raw result text, optional
  failure and diagnostic events. `producingSkillName` is parsed separately
  as diagnostic text.
- `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java`
  constructs the same core client. Starter configuration and issuance tests
  invoke that client. No Java production convenience method accepts a skill
  target outside core's public catalog/submit methods.

### Python SDK and callers

- `sdks/python/src/loomspan_sdk/_client.py:80` and
  `sdks/python/src/loomspan_sdk/_async_client.py:66` expose the same name-only
  API. They share `_protocol.py:63`–82 for execution and skill URL building,
  `_protocol.py:141` for skill segment validation/encoding, and
  `_protocol.py:286`–357 for catalog, acceptance and snapshot parsing.
  Independent sync and async transports use the same explicit credential
  snapshot and exact input bytes. Wait methods poll by UUID.
- `_models.py:44` and `:106` define frozen public models with descriptor
  `name` and snapshot `skill_name` today. Failure's independent
  `producing_skill_name`, event details, and result preserve their own
  wire content.
- `sdks/python/examples/existing_credential.py:10` and
  `existing_credential_async.py:11` expose `run` and CLI arguments taking
  `skill_name`, list without a namespace, request that name and submit
  `skill.name`. `sdks/python/examples/fastapi_app.py:52` reads
  `LOOMSPAN_SKILL` with an implicit `echo` default and its application
  `/submit` handler passes the one name with `{"text": ...}` to the async
  client. The FastAPI SDK extension itself supplies reusable client, issuer and
  callback dependencies without exposing any additional target-taking method.

### Quickstart flows

- `docs/quickstart.md:3`–15 and `:59`–119 document the real OpenAI model flow.
  `examples/quickstart/triage-support-request.yaml:1` declares
  `triageSupportRequest`, takes `customerMessage` and has no child/executor.
  `examples/quickstart/invoke-triage.py:10`–37 gets an ephemeral token from
  localhost port 8081, posts that business report to the name-only route on
  port 8080, validates the poll host, then follows Location to a terminal result.
  The root README shows the same business report and skill contract.
- `examples/quickstart/sidecar/skills/planner.yaml:1` declares
  `quickstartPlanner` with model alias `localplanner`, expects `message`,
  and names `identityLeaf` and `detailLeaf` in `allowed_skills`.
  The corresponding already-lowercase physical files
  `identity-leaf.yaml` and `detail-leaf.yaml` use the same bare `http`
  executor, selecting `identity-host` and `detail-host` and their distinct
  `POST /callbacks/identity` and `POST /callbacks/detail` routes.
  Both require the exact `QUICKSTART_USER` role and preserve the `message`
  input contract.
- `examples/quickstart/host/host.py:95`–108 emits deterministic plan and
  CALL_TOOL structured text with the old planner and leaf labels, then
  `quickstart complete`. The Framework handoff identifies plan/capability
  names and structured CALL_TOOL names as exact colon-qualified identities.
  Provider tool function names use `--` where applicable. Business task IDs,
  tool arguments, callback records and endpoint paths are separate data.
- `host.py:124`–159 provides `/token`, `/v1/responses`, `/status` and the
  two callbacks. The callbacks independently verify the incoming token and
  store the path, subject, roles and message at `/status`. The Compose recipe
  binds ingress to host loopback and shares Sidecar's network namespace. Its
  database starts empty. The fixture `sidecar/execution.yaml` sets the two
  caller-passthrough HTTP targets but today has no `localplanner` connection
  or model. Framework local documentation for the OpenAI-compatible driver
  specifies the service root, such as `http://127.0.0.1:8081/v1`. Existing
  Sidecar loopback tests set the same style of Responses origin.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Product entry points | `README.md:65,83`, `docs/quickstart.md`, `examples/quickstart/README.md` teach the old triage declaration/name-only call. The quickstart Compose recipe currently does not publish its fixture documents. |
| SDK READMEs | `sdks/java/README.md:36–68,200` and `sdks/python/README.md:268,398–435,519` show old calls, descriptor properties, skill path encoding or a single-name FastAPI setting. |
| Authoring declarations | `docs/http-executor.md:16`, `agent-skills/loomspan-sidecar-authoring/references/rest-configuration.md:16`, the remote-authoring `record.yaml:1` and `references/client-usage.md:37` contain `updateOrder` or `readRecord` declarations. The record path, `recordId` and source label are business/configuration values. |
| Shared execution guidance | `agent-skills/loomspan-sidecar-authoring/references/integration.md:112–159` is the central reference linked by `docs/integration.md`. It contains old routes, the snapshot `skillName` and an obsolete illustrative resolved failure spelling. `docs/operations.md:352–353` names the old retained field. |
| Loader and management documentation | `references/configuration-modes.md:7–9`, `references/configuration-bundles.md:42,64` and `references/management-api.md:140` describe paths or diagnostic labels. These loading boundaries and server inspection rules are covered by the companion ticket. `docs/setup.md`, `docs/architecture.md`, `docs/remote-recipes.md`, `docs/console-setup.md` and `examples/production/README.md` mainly link to the same execution/authorship/setup references. |
| Server/runtime split | `src/main/java/ai/loomspan/sidecar/api/SkillCatalogController.java:13–24` and `ExecutionController.java:41–47` still map single-name URLs and infer the default owner. `scripts/verify-deployment.py:368–370` probes the old server catalog path. These points cannot provide new wire-contract evidence before the server changes. |

The complete named documentation inventory was reviewed in the matching
Framework checkout at
`ai/thoughts/tickets/loomspan-pr-37.7-documentation-audit.md:62–74`. Its
file pointers match this baseline. The handoff describes the intended
replacement metadata and naming rules, while the current code above records
what this checkout actually exposes.

## Existing Tests and Fixtures

- Java `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTest.java:13`
  exercises discovery, detail, one submit, repeated UUID polling and opaque
  result text against an in-process HTTP server. The fixture currently dispatches
  by suffix and reads/discards the request body. `SidecarClientEdgeTest.java`
  covers rejected path inputs, exact encoded paths, callback-free transport
  behavior, malformed successful responses and output-schema failure evidence
  with legacy identity field names. TLS and Spring SDK tests contain additional
  call sites and fixture paths.
- Python `tests/test_sync_client.py:38` and
  `tests/test_async_client.py:36` already assert ordered literal wire targets,
  prefix preservation, credentials, JSON submit body and poll IDs using
  `wire_fixture.py`. They currently exercise the accepted legacy
  `café ?` segment and a single-name descriptor response. Shared
  `tests/test_protocol.py:120,178,210,256,298` covers encoding,
  required fields and independent diagnostic/event fidelity. There are
  additional public client calls in FastAPI and transport behaviors.
- `sdks/python/tests/verify_distribution.py:110–131,153–205` builds and
  installs the package, launches the standalone CLI examples against a local
  peer and invokes the shipped FastAPI example via
  `verify_fastapi_example.py:47–86`. Both verifiers embed old paths and
  fields. `scripts/test_agent_skills.py:102` asserts the exact old packaged
  `readRecord` example declaration. The authoring client unit suite is also
  listed in `.github/workflows/ci.yml:25–26`.
- Current `scripts/verify-http-executor.ps1` constructs a narrowed Maven POM;
  it does not run or publish the Compose examples. Current
  `scripts/verify-deployment.py` is the production-recipe readiness/TLS
  smoke and does not use the quickstart planner. Sidecar's
  `AuthenticatedExecutionApiIntegrationTest.java:349` has an independent
  loopback plan/child/callback flow. The example definitions and fixture host
  are not loaded by that test.

## Dependencies and Operational Constraints

- SDK Java reactor `sdks/java/pom.xml` uses Java 21 and bans transitive
  Framework/Sidecar dependencies. The separate Maven validator is
  `sdks/java/tests/test_build_boundary.py`. SDK compilation does not require
  rebuilding Sidecar against the Framework snapshot.
- `sdks/python/pyproject.toml` requires Python 3.12+ and
  `requirements-dev.txt` supplies pytest, trustme and mypy. Python 3.13.13 and
  `uv` were found on this host; availability of every dependency/built
  distribution was not verified during research. On Windows, existing signing
  tests conditionally skip POSIX symlink traversal requirements.
- Framework-backed quickstart execution needs Sidecar's companion route/record
  work, the installed beta.10 snapshot, known fixture ingress, and an isolated
  database/key location if using Compose. The real-provider triage walkthrough
  makes live OpenAI requests and is outside routine verification. The planner
  stub requires no external provider, and `/status` gives evidence of actual
  HTTP leaf selection. The orchestrator found the Docker CLI but could not
  connect to its local Linux engine. A direct local JVM/Python-host verification
  can exercise the same checked-in YAML, protocol responses and callbacks when
  the Compose deployment is unavailable. The host script runs OpenSSL, which
  is present on this machine. No deployment/service or test was run in this step.

## Historical Context

Commit `939564a` consolidated deployment checks and removed the old
`scripts/verify-production.py`. Its previous `publish_fixture` read all
three quickstart documents, used a local OpenAI-compatible Responses connection
with `deterministic-planner` and a synthetic credential, and polled the
planner result. The old script's settings names and HTTP endpoints reflect that
historical version. The deterministic host/vetted local routing survived, while
the fixture publishing/invocation recipe did not.

## Open Questions

- The walkthrough's real-provider triage and the deterministic planner are
  distinct examples with different inputs today. The ticket names the
  deterministic planner and leaves as the required runnable path, and also
  requires preserving authored business data. Planning owns how to present both
  paths coherently.
- Post-server local integration must verify the published/loaded planner, the
  returned HTTP identity, ID polling and both authenticated leaf calls. SDK
  loopback tests and a quickstart protocol fixture can establish the client
  contract independently, with live server integration pending until the
  companion ticket.
