---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: a1ab2d80d9d575614039b01ffe13341bf9c2ea11
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-2-blocking-sdk-client.md
tags: [sdk, blocking-client, http, integration]
---

# Blocking SDK client Research

## Research Question

What code, wire contracts, build boundaries and external-suite fixtures currently
exist for the blocking client and composed no-replay evidence in PR 8.1.2?
This is Step 1 of the approved Full 5-Step Pipeline. It describes existing
behavior; API design and implementation choices belong to planning.

Investigation completed: SDK reactor/build boundary; actual discovery/admission/status
HTTP paths; ownership/JWT configuration; result/failure/event representations;
existing focused tests; external SDK lane runtime/evidence; framework beta.8 public
metadata and documentation; local tool prerequisites. No tests, Docker commands,
provider calls or production operations were run during research.

## Summary

The SDK reactor exists but contains no Java sources or public client API. The
real Sidecar already exposes authenticated discovery, asynchronous acceptance and
owner-scoped execution reads. Its metadata schemas are JSON **strings**, and its
execution result is a nullable opaque string. The external SDK lane currently
proves startup and shutdown only; it has neither a Java SDK consumer nor a
response-loss proxy. Its isolated deterministic fixture counts model requests,
which the startup smoke currently requires to be zero.

The ticket intentionally spans both repositories and intentionally uses today's
execution-JWT configuration. No production compatibility changes are authorized
by that temporary fixture contract. The destructive-replacement policy and
[design lens](../design-lens.md#simplicity-and-technical-debt) apply. No required
development-data reset was identified in the current source or ticket.

## Repository State

Recorded 2026-10-18 at approximately 09:56 America/Los_Angeles:

| Repository | Branch | HEAD | Initial working tree |
| --- | --- | --- | --- |
| `C:/opendev/code/loomspan-sidecar` | main | `a1ab2d80d9d575614039b01ffe13341bf9c2ea11` | clean |
| `C:/opendev/code/loomspan-sidecar-test-suite` | main | `4ade9b8b1d051f70fc93446a9ce44fca78719941` | clean |
| `C:/opendev/code/loomspan-framework` | main | `52f11dd90c12863b2ec440583cfe2396eda946f6` | not modified by research |

Framework main has advanced. The matching public types and docs were read with
`git show v1.0.0-beta.8:<path>`; that tag resolves to
`052e8b47edaf95c0d2013d9e6351284a98207d6b`.

## Current Behavior and Data Flow

### SDK packaging

`sdks/java/pom.xml:6` defines the independent `ai.loomspan:loomspan-sdk-parent`
reactor at `0.1.0-SNAPSHOT`, with `core` and `spring-boot-starter` modules and
Java 21. Its inherited Enforcer rule bans transitive `ai.loomspan:*` except the
two SDK artifacts (`:32`). Root `pom.xml:17` independently pins Framework beta.8;
the root application is not a module of the SDK reactor.

`sdks/java/core/pom.xml:14` has no dependencies and adds a transitive Boot ban.
`sdks/java/spring-boot-starter/pom.xml:14` imports Boot 4.1.0 dependency management
and depends on core, optional Boot autoconfigure and optional webmvc. Neither
module has sources, tests, property classes or auto-configuration registration.
`sdks/java/README.md:13` explicitly says the JARs contain no public API yet.

### HTTP discovery and submission

`src/main/java/ai/loomspan/sidecar/api/SkillCatalogController.java:12` exposes
`GET /v1/skills` as an array and `GET /v1/skills/{name}` as one descriptor from the
current `SkillReloader.snapshot()`. Unknown names become 404. Discovery is not
filtered by skill roles, though `/v1/**` requires authentication.

At framework tag beta.8, `src/main/java/ai/loomspan/api/SkillDescriptor.java:6`
contains `name`, `description`, `kind`, `inputSchema` and `outputSchema`.
`inputSchema` is a required String; `outputSchema` is a nullable String. Kinds
are `YAML`, `JAVA`, `REST`. Matching documentation
`agent-skills/loomspan-docs/references/java-api/catalog-and-validation.md:10`
explains exact input-schema text and effective nullable output metadata.
These public types explain wire serialization; SDK core has no Java dependency
on them.

`src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41` handles
`POST /v1/skills/{name}/executions`. It checks skill existence, reads one JSON
object, calls public Framework validation, and admits work with captured JWT
authentication/owner. A valid acceptance is HTTP 202 with body `{"id":"<uuid>"}`
and a server-generated Location. Acceptance is separate from completion.

`src/main/java/ai/loomspan/sidecar/api/LimitedJsonObjectReader.java:24` enforces
the raw byte cap, rejects empty/non-object/malformed/trailing JSON, then converts
the object to a map. The server performs business schema validation; no SDK
schema compiler exists. The current server has no SDK-specific path-name policy.

### Execution envelope and outcomes

`src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:54` handles
`GET /v1/executions/{id}`. Reads return only records owned by the JWT issuer and
subject; absent/wrong-owner records become 404. The envelope contains:

| Field | Existing server representation |
| --- | --- |
| id | UUID |
| skillName | String |
| status | QUEUED, RUNNING, COMPLETED, FAILED |
| createdAt | Instant |
| completedAt | nullable Instant; no startedAt field exists |
| result | nullable String |
| failure | null or object with kind/message and optional issues |
| configurationSnapshotId | nullable UUID |
| events | absent when diagnostic selection supplies null; otherwise array |

`execution/ExecutionRecord.java:20` creates QUEUED snapshots with null result,
failure, events, completedAt and configurationSnapshotId. In
`execution/ExecutionCoordinator.java:193`, RUNNING snapshots have a captured
configuration ID after handoff. `:202` publishes terminal outcome, completion
time and selected events together: successful result passes through unchanged;
failed result is null and the original primary failure is classified. Failures
before a successful handoff can retain null configurationSnapshotId.

`execution/ExecutionFailureClassifier.java:9` preserves input-validation issues,
distinguishes access denial, and otherwise reports SKILL_FAILURE with the primary
message. `ExecutionFailure.java:7` defines issue collections. Framework beta.8
`SkillInputValidationIssue.java:3` contains path/code/message.

Framework beta.8 `SkillExecutionEvent.java:12` exposes timestamp, level, type,
details, frameId and route. Details support nested JSON-like values. Sidecar
retains available public observer events, not an internally reconstructed trace.
`execution/ExecutionCoordinator.java:205` selects NEVER/ONERROR/ALWAYS history;
missing observation can yield an empty list. No SDK wait, retry or cancellation
API exists today.

`api/ApiExceptionHandler.java:15` preserves problem status/detail and includes
issues for validation errors: 400 input, 403 access, 404 missing, 413 size, 429
capacity, 503 unavailable. `security/JwtSecurityConfiguration.java:100` emits
safe application/problem+json authentication failures separately.

### Current authentication contract

`security/SidecarJwtProperties.java:8` uses `loomspan-sidecar.auth.jwt` with
required issuer-uri/audience, optional public-key-location or jwk-set-uri,
60-second default skew, roles claim `roles`, and default `ROLE_` prefix.
`security/JwtSecurityConfiguration.java:37` chooses the configured RSA public
key, JWKS endpoint, or issuer discovery; it checks timestamp/issuer/audience and
requires nonblank issuer/subject plus exp. It currently has no new execution
purpose/JWKS-file contract from future PR 8.1.4. Owner identity is the issuer and
subject pair (`security/ExecutionOwner.java:7`).

## Key Components and Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Core/client public API | No Java source tree exists under `sdks/java/core`; reactor boundary in `sdks/java/pom.xml:32` |
| Starter wiring | POM-only optional Boot dependencies, `sdks/java/spring-boot-starter/pom.xml:29` |
| Existing HTTP/TLS pattern | `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java:58` owns pooled Apache clients, optional Boot SSL bundles, finite connect/read settings, disabled redirects/retries/cookies and idempotent close; this is Sidecar outbound callback code, not SDK code |
| Suite lane selection | `C:/opendev/code/loomspan-sidecar-test-suite/scripts/run_sdk.py:24` maps local to foundation-smoke, standard/hardened to empty scenario sets |
| Suite build identity | `scripts/run_sdk.py:120` reads JAR SHA-256, SDK coordinates/hashes and embedded beta.8; `:347` runs real root clean verify and SDK clean install without rebuilding Framework |
| Temporary authentication fixture | `scripts/run_sdk.py:185` creates an RSA public key but discards the private half; no execution token issuer currently exists |
| Isolated runtime | `scripts/run_sdk.py:233`, `sdk/compose.yaml:1`: Python fixture and built Sidecar image on internal network, no published host ports, read-only configuration, tmpfs DB, no provider forwarding |
| Fixture model | `scripts/sdk_lane/fixture.py:8` counts model POSTs and returns fixed `fixture result`; no synthetic business effect counter, callback or loss proxy |
| Runtime evidence | `scripts/run_sdk.py:377` collects readiness, graceful shutdown and zero-model-request smoke, then tears down its unique Compose project |
| Reports/safety | `scripts/run_sdk.py:341` atomically writes report.json; fixed diagnostic categories and allowlisted child environment suppress arbitrary exceptions/logs/payloads |

Suite-relative paths in this table refer to the external suite checkout.

## Existing Tests and Fixtures

- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:111`
  covers authenticated unfiltered discovery/schema metadata and missing skill;
  `:132` verifies MVC problem statuses; `:148` validates shape/schema/role
  rejection before admission; `:252` accepts, polls and checks exact multiline
  Unicode result, owner restrictions and configuration ID; `:275` preserves a
  primary REST HTTP-503 failure through a terminal HTTP-200 status envelope.
- `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java:35`
  preserves large result/event fields; `:74` classifies only the primary failure;
  `:89` checks atomic failure/events; `:118` checks selection policies and missing
  observation. These prove server behavior, not SDK behavior.
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:17`
  imports application and test packages and forbids internal/autoconfigure
  dependencies; `:28` rejects hosted @SkillMethod methods.
- `sdks/java/tests/test_build_boundary.py:37` builds disposable reactors and
  checks direct/transitive forbidden dependencies. Its core fixture injection
  currently appends a dependencies element because the real core has none;
  starter injection locates its existing dependencies after dependencyManagement.
  Thus the current helper assumes today's POM layout. It copies the whole SDK
  tree excluding target/cache.
- External `tests/test_sdk_runner.py:109` checks actual checkout build commands,
  identity and smoke ordering; `:184` onward covers failure/cleanup evidence,
  safe diagnostics, startup/shutdown bounds and isolation; `:408` runs the fixture
  locally and confirms count/result/no input logging. There is no plain-Java
  consumer, SDK call test, TLS client test, no-replay proxy test or composed
  execution scenario in the present SDK lane.

## Dependencies and Operational Constraints

Both root and starter target Boot 4.1.0; existing Sidecar JSON uses
`tools.jackson` (Jackson 3). The SDK has not selected a JSON or HTTP dependency
version. Local Maven cache contains HttpClient 5.6.1 and Jackson databind 3.1.4
among older versions; this is availability evidence, not a version policy.

Local tools found: `mvn.cmd` at `c:/hamdev/maven/bin/mvn.cmd`, Docker CLI,
Python 3.13, ruff, `JAVA_HOME=c:/hamdev/jbrsdk21`, and external-suite
`.venv/Scripts/python.exe`. `java` itself was not resolved from PATH by the
read-only tool inventory. Docker daemon availability was not tested.

Documented verification entry points (not run during research):

```powershell
mvn -B -ntp -f sdks/java/pom.xml clean install
python sdks/java/tests/test_build_boundary.py
# External suite checkout:
.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q
ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py
ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py
.venv/Scripts/python.exe scripts/run_sdk.py local
```

The composed command runs root clean verify, SDK install, image build, isolated
Compose runtime and cleanup. Maven/container downloads can use network access;
model fixtures have no provider forwarding path. Existing business runtime helpers,
credentials, databases and maintained reference packs are outside this lane.
`docs/sdk-lane.md` records independent beta.8 SDK builds; the retained business
lane uses its separate beta.9 Framework snapshot and exported Sidecar beta.3.

## Historical Context

Sidecar HEAD is PR 8.1.1, establishing these reactor and lane foundations.
The canonical API reference and acceptance matrix dated 2026-10-15 were reconciled
on 2026-10-18 and explicitly label feature support as planned. Existing roadmap
and phase text are intent, not executed PR 8.1.2 evidence. The current ticket
specifically assigns the first real SDK execution, failed execution and
lost-response scenarios to this implementation unit, with at most one synthetic
effect and request attempts counted separately. Future issuance/callback/trust
contracts are outside this ticket.

## Open Questions

No developer answer is required to finish research. Planning still owns the public
DTO/error organization, exact unsafe-name rejection rules, base-path joining,
credential storage/access mechanism, finite whole-call deadline implementation,
library versions, starter SSL-bundle property, Java driver packaging and placement,
and synthetic effect/proxy fixture selection. Those choices have no pre-existing
SDK implementation to preserve. The ticket requires both path encoding and local
unsafe-name rejection but does not enumerate all name variants. The existing
server requires configurationSnapshotId to remain nullable before handoff, events
to remain optional, and schemas to remain strings.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-18-pr-8-1-2-blocking-sdk-client.md
SUMMARY: Traced the empty SDK foundation, actual Sidecar wire and authentication contracts, focused server evidence and isolated external SDK lane. No implementation or tests were run.
DECISIONS:
  - Used framework v1.0.0-beta.8 sources and documentation because main has advanced.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run Steps 2 and 3 in one fresh planning context.
