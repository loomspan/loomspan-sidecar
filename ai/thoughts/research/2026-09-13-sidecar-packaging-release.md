---
date: 2026-09-14
repository: loomspan-sidecar
branch: main
commit: d83902713bcf84375c3f54b8d33a66e048f0aa39
ticket: ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md
tags: [sc5, packaging, container, lifecycle, readiness, release]
---

# Sidecar Packaging and Release Research

## Research Question

What packaging, readiness, shutdown/resource-lifecycle, deterministic quick-start,
configuration-documentation, integration-test, and release-preparation behavior
exists in the current Sidecar checkout, and which matching beta 4 framework
contracts and release gates constrain SC5?

## Summary

The current checkout is a repackageable Spring Boot JAR, not a containerized
application. It has no Dockerfile or build-image configuration, no image or tag
release workflow, no Kubernetes example, no runnable image quick-start fixture,
and no Sidecar-local SC5 evidence record. Its sole workflow runs Maven
verification against an override to the future released framework version.

The checked-out production application does contain the completed SC1-SC4
runtime: startup-only mounted YAML skills and REST routes, a production
`RestSkillHandler`, target-specific bounded HTTP clients, JWT authentication,
owner-scoped asynchronous execution, bounded admission/retention, diagnostic
selection, management-port health, and independent framework/Sidecar shutdown
ownership. The broad HTTP integration already uses the production REST handler
and a loopback callback which verifies the original token after a YAML planner
invokes a REST leaf. These are in-process tests, not packaged-image evidence.

The remaining lifecycle evidence is narrower than the ticket's packaged matrix.
The coordinator closes admission/readiness and drains queued tasks under its gate,
but a successful `begin` releases that gate before calling `SkillTemplate.invoke`,
leaving the dequeue/begin-to-framework-entry interval untested. Existing real
context-close tests prove REST clients outlive direct framework calls through
completion or cutoff, but do not combine actual Sidecar worker/caller wiring,
observer delivery, management-context events, or asynchronous event
multicasting. Listener ordering, blocked trace finalization, the single shutdown
budget, and cutoff are framework-owned behaviors rather than Sidecar-local proof
obligations.

The matching framework checkout is clean at
`e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f`. Since the repository's initial pin,
its only production-source change is an observability route-collision check; the
inspected public REST SPI, `SkillTemplate` contract, and lifecycle implementation
retain the beta 4 shape. Repository policy treats the developer-managed install
as aligned with that checkout; SC5 evidence still has to name the exact tested
framework and Sidecar states. Publication and post-publication gates remain
explicitly pending and unauthorized.

## Repository State

- Observed at `2026-09-14T08:45:53-07:00` in `loomspan-sidecar`.
- Branch `main`, commit `d83902713bcf84375c3f54b8d33a66e048f0aa39`.
- The Sidecar worktree was clean before this research artifact was updated. The
  pre-existing artifact described commit `d4471c0` and was stale relative to the
  subsequently committed SC4 implementation.
- The framework checkout was clean on branch `main` at
  `e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f`.
- Ignored `target/` output was not treated as current SC5 verification evidence.
  No live external service or publication action was invoked.

## Current Behavior and Data Flow

### Startup, routes, and readiness

`LoomspanSidecarApplication` binds execution, JWT, and route-location properties
(`src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java:10`). The
production YAML defaults skill discovery to `/sidecar/skills/**/*.yaml` and
`/**/*.yml`, routes to `/sidecar/rest-routes.yaml`, disables Loomspan
observability, and exposes only health on management port 9091
(`src/main/resources/application.yml:1`). Both skill and route locations are
startup overrides.

`RestRouteLoader` eagerly reads one required route document during bean creation,
resolves required Spring placeholders, and validates the closed target/route
schema without contacting targets (`src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java:46`).
It requires `targets` and `routes` maps; validates unique names, HTTP(S) base URLs,
auth mode and headers, SSL-bundle references, positive connect/read timeouts and
response caps, GET/POST, safe path variables, and target references
(`src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java:82`).

Framework registration eagerly completes all YAML definitions and requires
exactly one public `RestSkillHandler` when REST skills exist
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:34`).
The Sidecar always supplies one production handler. `RestRouteCatalogValidator`
then compares every configured route with the completed public catalog and
requires a one-to-one route for every REST skill
(`src/main/java/ai/loomspan/sidecar/rest/RestRouteCatalogValidator.java:20`).
Both registration and route validation complete before application startup can
finish, so invalid skills/routes prevent a ready running context.

Boot health probes are exposed on the separate management server
(`src/main/resources/application.yml:25`). A valid running test context reports
health and readiness `UP`, while the application port does not expose Actuator
(`src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java:39`).
The coordinator publishes `REFUSING_TRAFFIC` for its owning close event
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:168`). No
current test observes readiness over HTTP during slow startup, invalid route
startup, active long work, or shutdown, and no liveness group is directly tested.

### Authenticated asynchronous execution and callbacks

Every `/v1/**` request is stateless JWT-authenticated. JWT construction supports
issuer discovery, an explicit JWKS URI, or an RSA public key, with issuer,
audience, timestamp, nonblank subject/issuer, signature, and configurable role
validation (`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:35`).
Health and framework observability namespaces are separately permitted; all
other application paths are denied (`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:88`).

For a POST, `ExecutionController` resolves the public catalog entry, reads a
bounded JSON object, calls public `SkillTemplate.validate` before admission, then
captures `(issuer, subject)` ownership and the verified JWT authentication in
the coordinator (`src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41`).
The framework contract states that validation does not reserve framework root
admission (`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:13`).
Accepted work returns `202` and a poll location. Polling discloses a record only
when both owner fields match (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:100`).

`ExecutionCoordinator` owns one record map, fixed worker pool, transfer queue,
queued count/bytes, expiry sweeper, and gate lock
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:35`). A
worker installs the captured authentication, invokes public `SkillTemplate`,
collects available public observer events, clears the security context, and
clears task input/authentication references
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:260`).
Terminal publication retains no events for `NEVER`, failed-work events for
`ONERROR`, and all available terminal events for `ALWAYS`; result text and
selected events are not Sidecar-truncated
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:141`).

`GenericRestSkillHandler` maps a public `RestSkillInvocation` to the startup
route snapshot, validates JSON-compatible input, binds safe path/query/body data,
adds static headers or the captured original bearer token, invokes a target's
isolated client, and converts bounded transport failures to `SkillException`
(`src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:36`).
`RestTargetClients` creates one redirect/retry-disabled Apache client per target,
applies connect/read limits and optional Boot SSL bundles, bounds the response
stream, and closes all clients idempotently
(`src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java:33`).

`AuthenticatedExecutionApiIntegrationTest` uses loopback model and callback
servers plus the production route file/handler. It proves HTTP pre-dispatch
role/input rejection, async admission and owner polling, callback-time expiry,
worker-context isolation, and a YAML planner invoking a REST leaf while the host
verifies the original token's signature, issuer, audience, expiry, subject, and
roles (`src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:76`,
`:142`, `:180`, `:207`). It is not yet a shared packaged application fixture and
does not run the image or the documented quick-start commands.

### Shutdown and resource lifetime

For its owning close event, `ExecutionCoordinator` acquires its gate, closes
Sidecar admission, publishes readiness refusal, drains waiting runnables directly,
removes their records, releases queue reservations, and clears input/security
references. It ignores other contexts and opts out of asynchronous listener
execution (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:168`).
Admission, queue accounting, dequeue release, task `begin`, and close use the same
gate. If `begin` observes closure, it prevents framework invocation. Once `begin`
returns true, however, it releases the gate before `run` enters
`SkillTemplate.invoke` (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:125`,
`:277`). The focused race test covers close winning before `begin`, not close
occurring after successful `begin` and before facade entry
(`src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java:346`).

At destruction the coordinator immediately stops its sweeper and calls
`shutdownNow` on worker threads, directly clearing any waiting tasks; it does not
wait or establish another timeout
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:190`).
The generic REST handler is a lower-phase (`0`) `SmartLifecycle`; its stop closes
target clients (`src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:77`).
Current real-context lifecycle tests show a direct framework REST call and its
client survive until normal completion, and that a blocked call is cut off and
the context/client close within a bounded interval without a second Sidecar wait
(`src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java:25`,
`:86`).

The framework independently closes root admission in its synchronous,
owner-scoped close listener, establishes its single deadline, waits during its
highest-phase lifecycle stop, publishes cutoff if roots/executor remain, and has
a nonwaiting destruction fallback
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:73`,
`:101`, `:181`). Framework tests cover both close-listener orders, foreign
contexts, asynchronous standard event multicasting, observer lifetime, blocked
writers, interruption, shorter Spring phase timeouts, and nonwaiting fallback.
Those tests are framework evidence and do not instantiate the complete Sidecar
caller/client/observer wiring required by SC5.

### Build, image, examples, and release

The Maven project uses Java 21, Boot 4.1.0, and the locally installed
`loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` (`pom.xml:12`). The Boot Maven
plugin only configures `repackage`, producing an executable JAR (`pom.xml:91`).
There is no OCI image goal, Dockerfile, container metadata, non-root declaration,
sidecar JVM defaults, archive assembly, or checksum generation.

The repository has no Compose/Kubernetes manifest or runnable sample `/sidecar`
tree with one planner and two leaves. It has no bundled standalone model stub or
JWT issuer/callback host. The README documents local Maven/JAR startup, route
schema, TLS/JWT/security, API polling, bounds, diagnostics, and restart-only
activation, but has no image run/JWT/curl/poll quick start
(`README.md:9`, `:30`, `:150`, `:194`).

`.github/workflows/ci.yml` has one Java 21 push/PR verification job. It overrides
the framework dependency with `1.0.0-beta.4`, so it cannot succeed until that
artifact is published, and it does not build or test an image
(`.github/workflows/ci.yml:10`). There is no Sidecar `v<version>` release workflow,
release-version/SNAPSHOT guard, JAR archive/checksum packaging, image publication,
or local nonpublishing workflow validation path.

The framework readiness record still marks Sidecar SC5 snapshot integration,
integration remediation, final release-commit checks, and both manual
nonpublishing validations pending
(`C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md:10`,
`:91`). Framework workflows already separate manual validation from tag-triggered
publication. Neither repository is authorized for tagging or publication by this
ticket.

## Key Components

- `pom.xml:12` — Java, Boot, snapshot dependency, and executable-JAR build.
- `src/main/resources/application.yml:1` — mount, execution, and management defaults.
- `src/main/java/ai/loomspan/sidecar/config/RestRoutesProperties.java:5` — bound route location and default.
- `src/main/java/ai/loomspan/sidecar/security/SidecarJwtProperties.java:8` — bound JWT fields and constraints.
- `src/main/java/ai/loomspan/sidecar/config/SidecarExecutionProperties.java:8` — bound execution fields, defaults, and constraints.
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41` — pre-dispatch validation, admission, and polling.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:35` — queue/store, identity handoff, diagnostics, close gate, and worker destruction.
- `src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java:33` — eager unified target/route document parsing and validation.
- `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:25` — authorized REST SPI implementation and lower-phase client lifecycle.
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:38` — current full HTTP/JWT/model/production-callback fixture.
- `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java:22` — current real-context REST resource lifecycle proof.
- `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java:17` — separate management-port evidence.
- `.github/workflows/ci.yml:1` — released-dependency Maven verification only.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillHandler.java:3` — authorized application REST SPI.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:20` — framework one-budget lifecycle, source evidence only.
- `C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md:91` — authoritative pending SC5 evidence gate.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Production REST path | Present and startup-bound: route loader/catalog validator, isolated target clients, production public-SPI handler, caller token forwarding, TLS bundles, response bounds, and lifecycle tests. |
| Container packaging | Executable Boot JAR only; no image mechanism, non-root/JVM metadata, mounted example, or image test exists. |
| Probes | Health/readiness are exposed on port 9091 and close publishes readiness refusal; startup timing, long-work liveness, and shutdown HTTP transitions are not exercised. |
| Admission and dispatch | One gate bounds queue/record transitions and rejects close-before-begin; the successful-begin-to-facade-entry close interval remains open in code and untested. |
| Resource lifecycle | Direct real framework REST calls preserve clients through completion/cutoff; the full Sidecar worker/client/observer/listener/management wiring matrix is absent. |
| Quick start | README has configuration and API fragments, but no self-contained image fixture, local issuer, callback host, model stub, planner/two leaves, or verified commands. |
| Configuration reference | README covers current property groups and unified route format; defaults have a focused assertion, but no test asserts reference completeness against all bound property fields. |
| CI/release | Push/PR Maven verification targets the released framework override; image CI and tag release/archive/checksum/SNAPSHOT safeguards are absent. |
| Release evidence | Framework readiness remains pending SC5 and needs exact Sidecar/framework states plus actual commands/results. No publication is authorized. |

## Existing Tests and Fixtures

- `SidecarDefaultsTest` asserts the exact production YAML defaults, including
  mounts, JWT role/skew settings, execution bounds, and management port/exposure
  (`src/test/java/ai/loomspan/sidecar/config/SidecarDefaultsTest.java:11`).
- `SidecarExecutionPropertiesTest` and `JwtSecurityConfigurationTest` cover
  execution/JWT constraints. JWT tests use local keys and loopback discovery/JWKS;
  no external IdP is required.
- `MountedSkillRegistrationIntegrationTest` proves YAML/YML discovery, location
  overrides, separation from the route file, and startup failure for a missing
  model (`src/test/java/ai/loomspan/sidecar/skill/MountedSkillRegistrationIntegrationTest.java:25`).
- `RestRouteLoaderTest`, `RestRouteStartupIntegrationTest`, and
  `RestRouteRestartIntegrationTest` cover route parsing, placeholders and safe
  diagnostics, catalog agreement, one production handler, overrides, explicit
  empty configuration, and restart-only snapshots.
- `GenericRestSkillHandlerTest` covers GET/POST binding, all auth modes, input
  rejection before I/O, response/media/error bounds, redirects/retries, and read
  timeout (`src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java:45`).
  `RestTransportTlsIntegrationTest` covers per-target SSL bundle and client identity.
- `AuthenticatedExecutionApiIntegrationTest` is the broadest application fixture:
  it uses local keys plus loopback model and verifying callback servers through
  the production handler. It is a single test class rather than shared support.
- `ExecutionCoordinatorTest` covers workers/queue/count/byte/record bounds,
  concurrency, rollback, expiry, queue discard, identity handoff, and close before
  `begin`. `ExecutionDiagnosticsTest` covers all three selection modes, exact
  result/event retention, primary failure classification, missing observer
  callback, and whole-record expiry.
- `ExecutionShutdownIntegrationTest` only invokes the coordinator listener with
  mocks to prove async opt-out, foreign-context filtering, and readiness event
  publication (`src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java:18`).
- `RestHandlerLifecycleIntegrationTest` closes a real non-web Sidecar context with
  an active direct framework REST invocation, but does not exercise HTTP admission,
  coordinator callers, observers, management contexts, or async multicasting.
  Trace blocking, actual framework listener order, budget, and cutoff are
  framework-owned proof obligations.
- `SupportedLoomspanApiArchitectureTest` forbids main/test dependencies on
  `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..` and forbids Sidecar
  Java `@SkillMethod` declarations.
- No checked-in test builds/runs an image, inspects its Unix user/JVM defaults,
  executes the quick start, validates Kubernetes files, or checks a Sidecar
  release workflow. No test suite was run in this research step; a read-only
  Maven dependency-tree command freshly confirmed resolution of the snapshot.

## Dependencies and Operational Constraints

- Sidecar uses Java 21, Boot 4.1.0 with Jackson 3, Apache HttpClient 5, and
  `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`. Maven resolves
  that snapshot from the local repository; the build declares no snapshot
  repository and does not build framework source.
- Application and test Loomspan types must remain inside `ai.loomspan.api`.
  Framework lifecycle internals cited here are source evidence, not a supported
  Sidecar dependency. No public framework shutdown API exists for Sidecar use.
- The framework lifecycle default is `30s`; admitted roots can continue nested
  work until completion or the shared cutoff. Sidecar currently uses lifecycle
  phase ordering through standard Spring `SmartLifecycle` only.
- Docker client and daemon are available locally (29.5.3), as are `curl.exe` and
  Maven 3.9.11 using JetBrains Java 21.0.2. Image build behavior, network access,
  credentials, registry access, Maven Central resolution, and hosted CI were not
  exercised.
- Local deterministic tests can use checked-in keys, temporary files, and
  loopback servers. The current production callback integration does not require
  external model or identity-provider accounts.
- Updating the framework readiness record with local SC5 evidence is in ticket
  scope. Tags, pushes, workflow dispatches, registry/Maven uploads, and either
  project publication require separate authorization.
- Post-publication Sidecar criteria cannot execute during the snapshot stage;
  they remain required pending gates and are not local-preparation successes.

## Historical Context

The SC5 phase owns the image/probes, packaged lifecycle proof, runnable quick
start, configuration guidance, and release workflow
(`ai/thoughts/phases/phase-sc5.md:13`). The design lens keeps framework execution
authority separate from Sidecar transport/admission/retention, keeps trusted
identity outside model input, makes skill/route activation startup-only, and
assigns already-admitted work one framework shutdown budget
(`ai/thoughts/design-lens.md:25`, `:56`).

Git history records the scaffold at `e3c3f58`, authenticated execution at
`2597238` plus cleanup `fba8351`, and production REST implementation at `f71d2a4`
plus cleanup `d839027`. The earlier version of this research artifact was created
at `d4471c0`, before `f71d2a4`; its claim that SC4 was absent is superseded by
the checked-out source.

The framework's original alignment pin is `385729a...`; its current clean state
is `e62b776...`. The only production-source change between those revisions is
`ObservabilityRouteCollisionDetector` at commit `fffd844`; current public REST
and lifecycle contracts inspected above are otherwise unchanged. The framework
readiness record names the earlier handoff pin and intentionally leaves SC5 and
final release gates pending.

## Review Cycle 3 Remediation Update

Independent review exposed that Sidecar could not make its close-or-dispatch
decision atomic using `SkillTemplate.invoke`: the framework admitted a root only
inside that long-running call. The approved framework remediation adds the public
`SkillInvocationHandoff` and `AdmittedSkillInvocation` contracts at framework
checkout `bfc2764bb661a6eccad9fd120cd687e6a911e99a` (implementation commit
`5d19c7a`). The locally installed beta.4 snapshot starter has SHA-256
`901769CACBAF7F0CD2B39845ED1D7AC3D63294D64F74CC9D411C16925620D2AA` and contains
both API classes.

Sidecar now transfers ownership through that public handoff while its existing
dispatch gate is held, then invokes the admitted handle outside the gate. Its
deterministic tests cover Sidecar-close-first, framework-admission-first, and a
framework-close-first failure without invoking the root. The developer clarified
that successful handoff is the sole ownership boundary: Sidecar proves discard
and cleanup before that boundary plus its actual caller/client/observer wiring
and normal accepted-work completion. The matching framework lifecycle tests own
listener ordering, blocked trace finalization, the single shutdown budget, and
cutoff. They are not substituted for Sidecar-owned behavior, and no new trace
SPI, framework internals, or replacement beans were introduced in Sidecar.

## Open Questions

- Which exact uncommitted Sidecar state will be present when each retained SC5
  command runs? The readiness record requires the tested commit plus changes, so
  this is evidence captured at execution time rather than inferable now.
- If packaged integration exposes a framework defect, the resulting framework
  revision and developer reinstall cannot be known during research; affected
  Sidecar evidence becomes current only after that reinstall and retest.
- Framework publication, released-dependency resolution, hosted Sidecar CI, and
  published-image quick-start results remain intentionally unknowable in the
  local snapshot stage and must remain visibly pending until separately run.
