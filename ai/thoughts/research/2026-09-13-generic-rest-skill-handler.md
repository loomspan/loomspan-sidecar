---
date: 2026-09-14
repository: loomspan-sidecar
branch: main
commit: d4471c0393051ee2ba0b8e3decfd48690bb5f404
ticket: ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md
tags: [sc4, rest-skills, startup, jwt, tls, transport]
---

# Generic REST Skill Handler Research

## Research Question

What production and test behavior exists in the current Sidecar checkout for
SC4, and what public framework, startup, security, transport, and lifecycle
contracts constrain the ticket?

The investigation covered the current production wiring, mounted-skill and
asynchronous execution paths, the existing test handler and fixtures, dependency
versions, relevant Git history, and the matching local framework checkout.

## Summary

SC4 is not implemented in production at the current commit. The default route
location is present in `application.yml`, and the README explicitly describes
the route file and production REST handler as future SC4 work, but there is no
route-file parser, route/target model, production `RestSkillHandler`, outbound
client, catalog correspondence validator, or REST-client lifecycle owner.

SC2/SC3 already provide the surrounding authenticated asynchronous path. The
request JWT is captured with admitted work, installed in `SecurityContextHolder`
on the Sidecar worker, and cleared afterward; the framework in turn makes that
authentication available on the actual REST-handler thread. Existing integration
tests prove queue and nested propagation using a test-only handler, not HTTP.

The public framework surface is sufficient to identify all registered REST
skills after registration: `SkillCatalog` is an eager immutable snapshot and
`SkillDescriptor.kind()` exposes `SkillKind.REST`. Framework registration looks
up exactly one handler only when REST manifests exist, calls it once with a
frozen `RestSkillInvocation`, preserves existing `SkillException` behavior, and
rejects a null result. Sidecar can only depend on those public API types; the
internal framework source below is implementation evidence, not an authorized
Sidecar dependency.

## Repository State

- Captured at `2026-09-14T00:16:37.5644943-07:00` on repository
  `loomspan-sidecar`, branch `main`, commit
  `d4471c0393051ee2ba0b8e3decfd48690bb5f404`.
- The working tree contained only the unrelated untracked SC5 paths
  `ai/thoughts/research/2026-09-13-sidecar-packaging-release.md` and
  `ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md` before this
  research artifact was added. They were not read or modified during this step.
- The matching framework checkout was clean on branch `main` at
  `e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f`. Its changes after the initial
  source pin `385729a254261de128df491505acd8898cc0a021` affect planning documents
  and observability collision behavior, not the REST SPI, public catalog, or
  shutdown source files used here.
- Maven resolves `loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`, Spring
  Boot `4.1.0`, Spring Web `7.0.8`, Jackson Databind `3.1.4`, and Apache
  HttpClient `5.6.1`. `httpclient5` is already a direct compile dependency at
  `pom.xml:68-72`.

## Current Behavior and Data Flow

### Startup and skill registration

1. `LoomspanSidecarApplication` enables only execution and JWT property classes;
   component scanning supplies the existing HTTP API, security, and execution
   coordinator (`src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java:9-10`).
2. Framework skill locations include only mounted `*.yaml` and `*.yml` patterns.
   The Sidecar-owned default `loomspan-sidecar.rest-routes-location` is declared
   separately as `file:/sidecar/rest-routes.yaml`
   (`src/main/resources/application.yml:1-10`).
3. No production class reads that property or route file. The README currently
   says the route file is reserved and not parsed, and says SC4 will add the
   production handler (`README.md:33-38`, `README.md:176-179`).
4. The framework completes YAML/REST registration while creating its public
   catalog. If REST declarations exist, its registrar resolves exactly one
   `RestSkillHandler`; with no REST declarations it does not perform handler
   cardinality validation
   (`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:37-44`,
   `:69-85`). The public catalog constructor forces registration completion
   before building its immutable descriptor snapshot
   (`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:20-34`).

### Authenticated asynchronous invocation

1. `ExecutionController` verifies that a catalog entry exists, reads and
   validates a JSON-object request with `SkillTemplate`, and admits the request
   with both the owner and the concrete `JwtAuthenticationToken`
   (`src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:36-46`).
2. `ExecutionCoordinator` retains that token in the queued task. Immediately
   before `SkillTemplate.invoke`, the worker creates a security context and
   installs the captured authentication; it clears the context and references
   in `finally` (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:269-293`).
3. Framework documentation states that the captured caller authentication is
   scoped through `SecurityContextHolder` on the actual handler thread. The
   handler receives only the exact skill name and an immutable map/list snapshot;
   null and non-container leaf identity, including a resolved Spring `Resource`,
   remain intact (`C:/opendev/code/loomspan-framework/README.md:445`).
4. Framework invokes the handler once. Existing `SkillException` instances keep
   their facade behavior, other runtime failures become safe `SkillException`
   failures, an empty string succeeds, and null fails
   (`C:/opendev/code/loomspan-framework/agent-skills/loomspan-docs/references/java-api/rest-skills.md:24-26`).
5. Sidecar catches the facade failure at the worker boundary and classifies every
   failure other than input validation or access denial as `SKILL_FAILURE`
   (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:284-289`,
   `src/main/java/ai/loomspan/sidecar/execution/ExecutionFailureClassifier.java:9-18`).
   The existing polling representation therefore already carries the framework's
   primary `SkillException` message as a normal failed execution.

### Shutdown

The Sidecar execution coordinator is an independent synchronous
`ContextClosedEvent` listener. It closes admission, marks readiness as refusing
traffic, and discards queued work without waiting; its `@PreDestroy` shuts down
its two executors (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:169-203`).
Framework documentation defines `loomspan.shutdown.timeout` as the one framework
budget and permits already-admitted roots, including nested work, to continue
until completion or cutoff (`C:/opendev/code/loomspan-framework/README.md:519`).
There is currently no Sidecar-owned REST transport resource or lifecycle hook.

## Key Components

- `src/main/resources/application.yml:1-10` — separates framework skill scan
  patterns from the reserved Sidecar route-file location.
- `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java:8-10` —
  application root and current configuration-property registration.
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:36-46` —
  authenticated HTTP admission through the public catalog/template.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:269-293`
  — queue-to-worker JWT propagation and invocation boundary.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:169-203`
  — current close gate and executor teardown.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionFailureClassifier.java:9-18`
  — stable execution failure-kind mapping.
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:38-60`
  — common application fixture with deterministic local model endpoint.
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:202-223`
  — nested planner-to-REST JWT propagation proof using the test handler.
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:291-318`
  — test-only handler that reads the worker JWT, blocks/fails on fixture values,
  and returns an in-memory string without outbound transport.
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:18-31`
  — executable ban on framework internal/autoconfigure dependencies and Java
  `@SkillMethod` declarations across production and tests.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillHandler.java:5-8`
  — sole supported REST SPI.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillInvocation.java:11-39`
  — immutable invocation handoff and recursive map/list freezing.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillCatalog.java:6-16`
  — eager immutable catalog contract.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillKind.java:4-9`
  — public `YAML`, `JAVA`, and `REST` classification.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Route configuration | Only the default location string exists. There is no parser, immutable target/route model, placeholder pass, duplicate/unknown-key detection, or local route validation. |
| Framework correspondence | Public catalog entries expose exact names and `REST` kind, but no Sidecar startup component currently compares them with routes. |
| Handler cardinality | Current production exposes no `RestSkillHandler`. REST integration tests import one test bean; adding an always-present production bean without changing that fixture would result in two handler beans for REST manifests. |
| Binding and URI construction | No production GET/POST binder or base-path-preserving URI builder exists. Current input validation is only the manifest/schema validation performed before admission. |
| Authentication | Inbound JWT verification and queue/nested propagation exist. No production outbound none/static/passthrough header behavior exists. |
| HTTP transport | Apache HttpClient is present, but there is no `RestClient`, per-target timeout/TLS configuration, redirect policy, response streaming cap, or media-type/charset mapping. |
| Failure propagation | Existing `SkillException` messages become `SKILL_FAILURE` polling results. No bounded HTTP diagnostic construction or response-body exclusion exists because there is no HTTP handler. |
| Lifecycle | Sidecar and framework shutdown gates exist; no REST-client owner currently participates in context close or destruction. |
| Documentation | README accurately describes SC2/SC3 and labels the route parser/handler as future work, so it does not document the ticket's requested runtime contract yet. |

## Existing Tests and Fixtures

- `MountedSkillRegistrationIntegrationTest` proves both extensions are scanned
  and the sibling route file is excluded from framework skill scanning
  (`src/test/java/ai/loomspan/sidecar/skill/MountedSkillRegistrationIntegrationTest.java:26-42`).
  It does not parse or validate that route file.
- `echo-rest.yml` declares the role-restricted `echoRest` REST leaf and
  `nested-rest.yml` declares a deterministic planner allowed to call it
  (`src/test/resources/fixtures/execution-skills/echo-rest.yml:1-11`,
  `src/test/resources/fixtures/execution-skills/nested-rest.yml:1-10`).
- `AuthenticatedExecutionApiIntegrationTest` proves inbound JWT enforcement,
  successful/failed polling, original-token availability after queueing, and a
  nested planner call. The REST behavior is supplied by its imported in-memory
  handler, so these tests do not prove URL binding, headers, TLS, response
  limits, callback-host verification, or production handler activation.
- `JwtTestTokens` already generates deterministic locally signed issuer,
  audience, subject, role, and short/expired-lifetime tokens
  (`src/test/java/ai/loomspan/sidecar/support/JwtTestTokens.java:21-65`).
- `ExecutionShutdownIntegrationTest` checks synchronous listener behavior and
  readiness publication but owns no HTTP client/resource assertion
  (`src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java:17-44`).
- The architecture test applies to test classes as well as production classes,
  so SC4 tests must also remain on the supported Loomspan surface.
- No current fixture or test covers the unified route document, duplicate YAML
  keys, unknown fields, placeholders, shared/multiple targets, GET/POST binding,
  traversal, redirects, streaming byte limits, media types/charsets, timeouts,
  mTLS, restart-only activation, production callback verification, or REST
  client lifecycle.
- No application-context test currently supplies an explicit empty routes file.
  Because the ticket makes the file mandatory even with no REST skills, the
  existing empty-skill context tests are directly affected. The two REST API
  integration classes also import the test handler and are directly affected by
  the ticket's exactly-one production handler requirement.
- No test suite was run during research. The Maven dependency-tree command was
  run only to record the resolved dependency versions above.

## Dependencies and Operational Constraints

- Java 21 and Boot 4.1.0 are selected in `pom.xml`; the Boot dependency set
  resolves Spring Web 7.0.8 and Apache HttpClient 5.6.1. Boot exposes its
  standard `SslBundles` API in the resolved `spring-boot-4.1.0.jar`.
- The framework checkout is the version-aligned source authority for ordinary
  development. The installed snapshot is developer-maintained to match it; the
  repository explicitly does not rebuild framework source as part of Sidecar.
- Application and test code may import Loomspan types only from the closed
  `ai.loomspan.api` package. `RestSkillHandler` is authorized, while registrar,
  capability registry, framework lifecycle, bean names, and phases are not.
- The framework public catalog supports exact-name and kind inspection but does
  not expose route declarations, handler ownership, or a client lifecycle SPI.
- The route file is outside the default framework scan, is expected to be read
  once at startup, and must not cause remote availability or credential probes.
- Current tests are loopback-only and require no provider account; the local
  model fixture uses JDK `HttpServer`. No current dependency provides WireMock
  or MockWebServer.
- SC5 packaging/release work and its two untracked artifacts are outside this
  ticket. This research makes no packaging or publication claim.

## Historical Context

- `e3c3f58` scaffolded the application and deliberately reserved the route-file
  property without parsing it.
- `2597238` implemented the authenticated asynchronous API and test-only REST
  handler used to prove JWT propagation.
- Despite its subject, `c709cab` did not add an SC4 production handler. It added
  this SC4 ticket plus SC2/SC3 test cleanup and historical pipeline artifacts.
  `d4471c0` immediately removed those historical artifacts. Neither commit
  changed `src/main`; the current production tree is still the SC2/SC3 state.
- `ai/thoughts/beta4-framework-alignment.md:44-46` records the framework
  alignment: construct the handler independently of the catalog, validate later
  against the eager public catalog, reject resolved non-JSON leaves, and use
  `SkillException` for intentionally preserved bounded diagnostics.

## Open Questions

- The current codebase contains no implemented media-type/charset policy for
  nonempty JSON and `text/*` responses; the ticket leaves its concrete client
  mapping to implementation planning.
- The current codebase contains no selected standard Spring lifecycle shape for
  keeping per-target clients alive through framework completion/cutoff and then
  releasing their resources; only the required observable ordering and one
  framework budget are settled.
- The existing JDK stub and JWT fixtures provide useful primitives, but there is
  no current local TLS/client-auth fixture or HTTP test harness that establishes
  how the requested mTLS and streaming assertions will be exercised.
