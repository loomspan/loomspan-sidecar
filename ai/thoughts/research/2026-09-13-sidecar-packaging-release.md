---
date: 2026-09-14
repository: loomspan-sidecar
branch: main
commit: d4471c0393051ee2ba0b8e3decfd48690bb5f404
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
release workflow, no Kubernetes or runnable quick-start fixture, and no Sidecar
release evidence record. Its sole workflow runs Maven verification against an
override to the future published framework version.

Existing production code supplies JWT authentication, owner-scoped asynchronous
execution, bounded in-memory admission/retention, diagnostic selection, a
close-event gate, and separate-port Actuator health. The current tests cover
those components mostly in-process. They do not exercise the SC5 packaged
lifecycle matrix, image startup, a production callback client, or a full local
quick start.

The ticket's assertion that the generic production REST handler is already
implemented does not match the checked-out tree. There is no production
`RestSkillHandler`, route configuration class, route loader, binding, or HTTP
client. Commit `c709cab` is titled as the REST implementation but its tree diff
adds the generic-handler ticket and changes authenticated-execution plans/tests;
commit `d4471c0` removes those plans. The README still explicitly calls the
outbound REST handler future SC4 work. The existing nested REST test supplies a
test-only handler, so it proves security-context propagation to an injected
callback, not the production route/transport path required by SC5.

The local framework checkout is clean at
`e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f`, later than the initial source pin
`385729a254261de128df491505acd8898cc0a021`. Changes after the pin include a
production observability route-collision update, but the lifecycle and supported
REST SPI inspected for this research retain the documented beta 4 shape. Per
repository policy, the developer-managed install workflow is the authority that
keeps this checkout and the locally installed
`1.0.0-beta.4-SNAPSHOT` artifact aligned; SC5 still has to record the exact
tested framework revision.

## Repository State

- Observed at `2026-09-13T23:56:42-07:00` in `loomspan-sidecar`.
- Branch `main`, commit `d4471c0393051ee2ba0b8e3decfd48690bb5f404`.
- Before this research artifact was created, the only working-tree entry was the
  untracked ticket
  `ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md`; it is developer
  input and must be preserved.
- Ignored local build/log material already exists under `target/` and in six
  `sc1-*.log` files. It was not treated as current SC5 evidence.
- The framework checkout was clean on branch `main` at
  `e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f`.

## Current Behavior and Data Flow

### Startup, configuration, and readiness

`LoomspanSidecarApplication` enables only `SidecarExecutionProperties` and
`SidecarJwtProperties` (`src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java:9`).
The production YAML supplies two `/sidecar/skills` resource patterns, disables
framework observability, reserves `file:/sidecar/rest-routes.yaml`, configures
execution defaults, and exposes health on management port 9091
(`src/main/resources/application.yml:1`). The route-location value is currently
an environment property only: no production Sidecar class binds or consumes it.

Framework YAML registration is eager. The framework registrar reads all skill
definitions during singleton completion and requires exactly one
`RestSkillHandler` only when REST definitions exist
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:34`).
The public catalog constructor explicitly completes registration before it
snapshots descriptors
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:20`).
Consequently invalid model-backed skill registration prevents application
startup, while the current Sidecar does not validate `rest-routes.yaml`; mounted
REST manifests cannot start without some externally supplied handler bean.

Actuator health and readiness endpoints are enabled and exposed only on the
management server (`src/main/resources/application.yml:25`). There is no custom
startup readiness component. A running valid test application reports health
and readiness `UP` on the management port
(`src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java:38`).
The coordinator explicitly publishes `REFUSING_TRAFFIC` when its owning context
closes (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:168`).
No current test observes the management readiness endpoint during slow startup,
invalid route validation, long execution, or shutdown.

### Authenticated asynchronous execution

Every `/v1/**` request is stateless JWT-authenticated; health and framework
observability namespaces are separately permitted and all other application
routes are denied (`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:86`).
JWT construction supports issuer discovery, an explicit JWKS URI, or an RSA
public key. Validators require signature, issuer, audience, timestamp,
nonblank issuer/subject, and expiration (`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:34`).
Roles come from configurable `roles-claim` and `role-prefix`, with the same
prefix exposed through Spring's `GrantedAuthorityDefaults`
(`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:71`).

For a POST, `ExecutionController` verifies the catalog name, reads a bounded
JSON object, calls the public `SkillTemplate.validate` pre-dispatch, then admits
the validated map with its verified authentication and `(issuer, subject)`
owner (`src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41`). The
public framework contract states that validation does not reserve framework
execution admission
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:11`).
Accepted work returns `202` and a polling location. Polling returns a snapshot
only when both owner fields match (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:100`).

`ExecutionCoordinator` owns the record map, fixed worker pool, transfer queue,
queued count/bytes, expiry sweeper, and one lock used for admission and dispatch
state (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:35`).
The worker restores the captured JWT authentication before calling public
`SkillTemplate.invoke`, gathers available public observer events, clears the
security context, and clears task input/authentication references on exit
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:260`).
Terminal publication selects no events for `NEVER`, failed-work events for
`ONERROR`, and all available terminal events for `ALWAYS`; result text and
events are stored without Sidecar truncation
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:141`).

The existing HTTP integration uses a deterministic loopback OpenAI-compatible
model stub and a test-only `RestSkillHandler`. It proves rejection before
admission, accepted polling/ownership, queue-time token expiry at the HTTP
boundary, worker token retention, and a nested YAML plan exposing the original
JWT to that test bean
(`src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:59`,
`src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:140`,
`src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:201`,
`src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:290`).
It does not make a production outbound HTTP call or validate a callback host.

### Shutdown and resource lifetime

For its owning `ContextClosedEvent`, `ExecutionCoordinator` sets its `open` gate
false, publishes readiness refusal, drains waiting runnables directly, removes
their records, releases reservations, and clears input/authentication references.
The listener ignores other contexts and opts out of asynchronous listener
execution (`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:168`).
Admission, queue operations, task `begin`, and close use the same lock. A task
whose `begin` observes the closed gate cannot call the framework
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:125`).

The actual call to `SkillTemplate.invoke` occurs after `begin` releases the gate
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:277`).
Current tests prove that a synthetic task beginning after close does not invoke,
and that queued work is discarded while a directly mocked active call can
finish (`src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java:130`,
`src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java:346`).
They do not exercise the exact scheduling interval between a successful `begin`
and facade entry, nor close a real application with active framework skill and
observer work.

At bean destruction, the coordinator immediately stops the sweeper and calls
`shutdownNow` on its worker executor, clearing any returned waiting tasks; it
does not wait or establish another timeout
(`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:190`).
There is currently no production REST HTTP client resource whose close order can
be tested. The focused Sidecar shutdown integration has only two tests: async
listener opt-out and owning-versus-foreign context/readiness publication
(`src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java:17`).

The framework independently closes root admission on its owning close event and
does not wait in that listener. Its `SmartLifecycle.stop` then waits for roots
within the single configured deadline, stops its executor, publishes cutoff if
needed, and provides a nonwaiting destruction fallback
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:73`,
`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:101`,
`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:181`).
Its internal phase is `Integer.MAX_VALUE`; repository guidance explicitly makes
that source evidence, not a supported Sidecar dependency. Framework tests cover
owning contexts, synchronous closure under an asynchronous multicaster, either
independent listener registration order, shorter Spring phase timeout, observer
lifetime, blocked writers, interruption, and nonwaiting fallback, but those are
framework-only tests rather than Sidecar wiring proof.

### Build, image, examples, and release

The Maven project is Java 21 / Boot 4.1.0 and currently uses
`loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`
(`pom.xml:12`). The Spring Boot Maven plugin runs `repackage`, producing an
executable JAR (`pom.xml:86`). There is no configured OCI image goal, container
metadata, non-root user declaration, sidecar JVM defaults, or archive/checksum
assembly.

The repository has no Dockerfile, Compose file, Kubernetes manifest, bundled
quick-start directory, local issuer/callback host, or standalone model stub.
The README documents local Maven/JAR startup and configuration fragments, but
its mounted-configuration section says route parsing is future SC4 work
(`README.md:26`), and it has no runnable image/JWT/curl/poll sequence.

`.github/workflows/ci.yml` contains one push/PR job. It runs `verify` on Java 21
with `-Dloomspan.version=1.0.0-beta.4`, so it is prepared for the future
published dependency but does not build or run an image
(`.github/workflows/ci.yml:10`). No `v<version>` workflow exists, and current
automation does not reject a SNAPSHOT release, package a JAR archive, generate
checksums, publish an image, or provide a nonpublishing validation mode.

The framework readiness record still marks Sidecar SC5 snapshot integration and
all final release-commit/manual validation gates pending
(`C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md:10`,
`C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md:91`).
Framework tag workflows already separate manual nonpublishing validation from
tag-triggered publication; actual tag/publish actions remain outside this
ticket's authorization.

## Key Components

- `pom.xml:12` — Java/Boot/framework versions and executable-JAR build.
- `src/main/resources/application.yml:1` — current mount, execution, and
  management defaults; route location is declared but unconsumed.
- `src/main/java/ai/loomspan/sidecar/security/SidecarJwtProperties.java:8` — all
  bound Sidecar JWT fields and validation constraints.
- `src/main/java/ai/loomspan/sidecar/config/SidecarExecutionProperties.java:8`
  — all bound execution fields, defaults, and positive-value validation.
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41` — public
  pre-dispatch validation, admission, and owner-scoped polling entry points.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:35` —
  queue/store ownership, security-context handoff, diagnostics, close gates,
  and worker destruction.
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:38`
  — current HTTP/JWT/model fixture; REST behavior is supplied by a test bean.
- `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java:17`
  — current limited application-listener shutdown coverage.
- `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java:17`
  — separate management-port exposure evidence.
- `.github/workflows/ci.yml:1` — released-dependency Maven verification only.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillHandler.java:3`
  — authorized application-provided REST leaf SPI.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:20`
  — framework's current one-budget lifecycle implementation, inspected as source
  evidence only.
- `C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md:91`
  — authoritative pending SC5 evidence gate.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Production REST path | Absent. Only test code declares a `RestSkillHandler`; no route loader, binder, transport client, TLS/bundle integration, or startup route validation exists. |
| Container packaging | Executable Boot JAR only; no image mechanism or non-root/JVM configuration exists. |
| Probes | Boot health/readiness are exposed on port 9091; startup/route-validation timing, long-work liveness, and shutdown HTTP transitions are not exercised. |
| Admission and dispatch | One coordinator lock bounds record/queue state and prevents `begin` after close; exact dequeue-to-invoke shutdown wiring remains unproved. |
| Resource lifecycle | Coordinator listener is synchronous and owner-scoped; destruction uses nonwaiting `shutdownNow`. There is no production REST client to order around framework lifecycle stop. |
| Quick start | README contains fragments and PowerShell API examples, but no self-contained image, test issuer, callback host, model stub, or sample planner/two leaves. |
| Configuration reference | README documents many current keys, but there is no complete generated/asserted reference and the route key has no bound implementation. |
| CI/release | Push/PR Maven verification targets the released framework override; image build/test and tag release automation are absent. |
| Release evidence | Framework record is pending SC5 and still needs exact Sidecar/framework states plus actual commands/results. No publication is authorized. |

## Existing Tests and Fixtures

- `SidecarDefaultsTest` asserts exact values currently present in
  `application.yml`, including the unbound route-location key and management
  settings (`src/test/java/ai/loomspan/sidecar/config/SidecarDefaultsTest.java:10`).
- `SidecarExecutionPropertiesTest` and `JwtSecurityConfigurationTest` cover the
  current execution/JWT property constraints. The JWT tests use local keys and a
  loopback discovery/JWKS server; no external IdP is required.
- `MountedSkillRegistrationIntegrationTest` copies YAML/YML skills to temporary
  directories, demonstrates custom locations, and proves missing model
  configuration fails startup. Its `rest-routes.yaml` fixture is intentionally
  invalid as a skill and only proves the route file is outside skill globs
  (`src/test/java/ai/loomspan/sidecar/skill/MountedSkillRegistrationIntegrationTest.java:25`).
- `AuthenticatedExecutionApiIntegrationTest` is the broadest current application
  fixture. It uses a loopback model server and test handler, but it is not shared
  as a reusable support fixture with other integration suites.
- `ExecutionCoordinatorTest` covers direct handoff, count/byte/retention bounds,
  simultaneous admission, rollback, expiry, queue discard, identity handoff,
  and a close-before-begin case. It mocks the framework facade.
- `ExecutionDiagnosticsTest` covers `NEVER`, `ONERROR`, `ALWAYS`, exact large
  result/event retention, primary failure classification, missing observer
  callback, and whole-record expiry
  (`src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java:33`).
- `ExecutionShutdownIntegrationTest` does not start a Spring application or
  framework execution; it directly invokes the coordinator listener with mocks.
- `SupportedLoomspanApiArchitectureTest` forbids dependencies on
  `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..` in both main and test
  classes and forbids Java `@SkillMethod` declarations
  (`src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:13`).
- No checked-in test builds an image, asserts its Unix user/JVM defaults/mount
  behavior, runs quick-start commands, validates Kubernetes configuration,
  checks release-workflow gating, or records retained SC5 results.
- No tests were run during this read-only research step; descriptions above are
  source inspection, not fresh execution results.

## Dependencies and Operational Constraints

- Sidecar uses Java 21, Boot 4.1.0, Boot's Jackson 3 mapper, Apache HttpClient 5,
  and the locally installed framework snapshot (`pom.xml:12`, `pom.xml:43`).
  Apache HttpClient is declared but unused by current production source.
- Application and test dependencies must remain inside `ai.loomspan.api`; the
  framework lifecycle internals cited here cannot become Sidecar dependencies.
- Framework source currently exposes `SkillTemplate`, catalog/value types, and
  the deliberately authorized `RestSkillHandler` SPI. It does not expose a
  public shutdown API for Sidecar coordination.
- The framework default shutdown timeout is documented as 30 seconds and must be
  positive. Already admitted roots may continue nested work until completion or
  the shared cutoff; Sidecar must not create another framework budget.
- Docker CLI and `curl.exe` are installed on this research host. Daemon state,
  image-build capability, network access, credentials, registry access, Maven
  Central availability, and hosted CI were not exercised.
- Local deterministic tests can use loopback servers and checked-in test keys;
  external model/IdP accounts are not needed by the existing fixture.
- The ticket authorizes updating the framework readiness record with local SC5
  evidence, but it does not authorize tags, pushes, workflow dispatches,
  registry/Maven uploads, or either project publication.
- The post-publication acceptance criteria cannot execute while framework
  `1.0.0-beta.4` is unpublished. They remain required deferred gates, not local
  preparation failures.

## Historical Context

The authoritative SC5 phase assigns image/probes, resource lifecycle, quick
start, configuration guidance, and release workflow to this unit
(`ai/thoughts/phases/phase-sc5.md:13`). The design lens keeps execution authority
in the framework, transport/admission/retention in Sidecar, trusted identity out
of model input, startup-only activation, and one framework shutdown budget
(`ai/thoughts/design-lens.md`). The framework readiness record already contains
framework-only lifecycle test results but explicitly rejects them as a
substitute for packaged Sidecar integration.

Git history records SC1 at `e3c3f58`, authenticated execution at `2597238`, its
cleanup at `fba8351`, then `c709cab` and `d4471c0`. Contrary to the SC5 ticket's
context sentence, inspection of `c709cab` shows no production REST-handler files
or modifications. This mismatch is checked-out evidence, not a conclusion about
developer intent.

The framework's original alignment pin was `385729a...`; the current clean
framework checkout is `e62b776...`. Its intervening production change is in the
observability collision detector rather than the inspected execution lifecycle
or REST SPI. The readiness record currently still names the earlier reviewed
pin and marks SC5 pending.

## Open Questions

- Was the missing SC4 production implementation intentionally omitted from the
  current branch, or must the SC5 implementation plan absorb/restore the
  generic-handler ticket before packaged callback and route-validation criteria
  can be met? The current tree cannot execute the required production REST path.
- Which exact current framework snapshot revision is installed locally for the
  first SC5 verification pass? Repository policy says the developer-managed
  artifact is aligned with the checkout; the retained SC5 evidence must record
  the tested value, which is currently `e62b776...` in source.
- The framework release and all post-publication Sidecar gates are intentionally
  deferred and separately authorized. Planning needs a durable local-stage
  handoff boundary that leaves those checks visibly pending without treating
  them as passed.
