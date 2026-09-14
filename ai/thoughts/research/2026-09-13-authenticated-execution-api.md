---
date: 2026-09-13
repository: loomspan-sidecar
branch: main
commit: e3c3f58f1246643c9ac55005228a56bd80b4f57b
ticket: ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md
tags: [sc2, sc3, execution-api, jwt, concurrency, shutdown]
---

# Authenticated Execution API Research

## Research Question

What does the completed SC1 Sidecar currently provide, and which existing
Sidecar and framework contracts, tests, configuration, lifecycle behavior, and
operational boundaries govern the combined SC2/SC3 authenticated asynchronous
execution API ticket?

## Summary

The checked-out Sidecar is the completed SC1 scaffold. It starts one Spring Boot
application, loads mounted Loomspan YAML skills into the framework's eager public
catalog, exposes only health on a separate management port, and enforces the
public-Loomspan-package boundary in an architecture test. It has no application
controllers, JWT decoder or security chain, Sidecar configuration binding,
execution records, admission queue, ownership model, diagnostic selection, or
Sidecar shutdown gate.

The pinned framework checkout is at the required commit and supplies the closed
public surface needed by this ticket: `SkillCatalog`, `SkillTemplate`, descriptor
and diagnostic value types, the three public skill exception types, and the
test-only `RestSkillHandler` SPI. Catalog discovery is eager and unfiltered;
`SkillTemplate.validate(name, Map)` performs input validation and root
authorization without creating a session or reserving framework admission;
`invoke(name, Map, observer)` repeats those checks, captures the current Spring
authentication, returns text, and synchronously offers at most one available
completed view. Sidecar must use those contracts without importing the framework
implementations inspected here as source evidence.

The ticket combines SC2 and SC3 because request-thread JWT identity drives root
validation, immutable issuer/subject ownership, queued worker security context,
nested authorization, and the later SC4 callback credential. SC5 depends on the
shutdown gate implemented here but owns packaged resource-lifecycle proof and
release work.

## Repository State

- Research recorded at `2026-09-13T17:39:29-07:00` in
  `C:/opendev/code/loomspan-sidecar`.
- Branch `main` and `HEAD`/`origin/main` are
  `e3c3f58f1246643c9ac55005228a56bd80b4f57b` (`Scaffold a buildable Loomspan
  Sidecar against the beta 4 framework`).
- Before this research artifact was created, the working tree already contained
  modified `AGENTS.md`, `README.md`, `ai/thoughts/beta4-framework-alignment.md`,
  `ai/thoughts/beta4-handoff.md`, `ai/thoughts/phases/phase-sc1.md`, and
  `ai/thoughts/tickets/2026-09-13-sidecar-scaffold.md`, plus untracked `.vscode/`
  and the current ticket. The documentation diffs record SC1 closeout and the
  developer-managed installed-snapshot workflow; they are not an existing
  SC2/SC3 implementation and must be preserved.
- The matching framework checkout is
  `C:/opendev/code/loomspan-framework` at
  `385729a254261de128df491505acd8898cc0a021`. It has separate modified planning
  and release-readiness documents but no framework production-source changes.
  Repository policy says the developer keeps the locally installed snapshot
  aligned with this checkout, so ordinary Sidecar development requires no
  additional artifact provenance check.

## Current Behavior and Data Flow

### Startup and discovery

`LoomspanSidecarApplication` is only a conventional `@SpringBootApplication`
entry point. Production configuration sends the framework two startup-only skill
patterns under `loomspan.skills.locations`, disables Loomspan observability, and
reserves a separate `loomspan-sidecar.rest-routes-location` that Sidecar does not
yet read (`src/main/resources/application.yml:1-10`). The existing registration
integration test proves that `.yaml` and `.yml` manifests beneath a supplied
mount are registered and that a sibling route file is not scanned as a skill
(`MountedSkillRegistrationIntegrationTest.java:25-41`).

At framework startup, the public `SkillCatalog` is backed by an exact-name,
sorted, immutable snapshot. Its construction completes YAML registration, reads
all registered capability kinds, and does not consult caller authorization
(`DefaultSkillCatalog.java:20-35` in the matching framework checkout). Its public
interface exposes `skills()` and exact `skill(name)` lookup
(`SkillCatalog.java:6-15`), and each descriptor consists of name, description,
kind, and the exact input-schema string (`SkillDescriptor.java:5-14`). This is
why the current catalog contract includes restricted skills and why discovery is
not an authorization decision.

No `/v1/**` mappings exist in the Sidecar. The README explicitly describes SC1
as having no execution or catalog API (`README.md:65-71`).

### Validation, invocation, and observation

The public `SkillTemplate` has Map overloads for both pre-check and execution
(`SkillTemplate.java:11-25` in the framework checkout). The matching framework
implementation currently behaves as follows:

1. Map preparation performs exact skill resolution, contract validation, and
   structured issue mapping (`DefaultSkillTemplate.java:133-157`).
2. `validate` then reads the current `SecurityContext` authentication and checks
   the root skill's access policy (`DefaultSkillTemplate.java:159-174`). It
   returns `void`; no prepared or normalized value crosses the public boundary.
3. `invoke` repeats preparation, captures the authentication currently installed
   on its calling thread, creates a fresh framework session, and returns
   `String.valueOf(result)` (`DefaultSkillTemplate.java:176-209`). Nested
   execution remains framework-owned.
4. A supplied observer is called synchronously during completion with a mapped
   `SkillExecutionView` when a public completed view is available
   (`DefaultSkillTemplate.java:181-187`). Framework documentation states that
   the callback is at most once, follows binding restoration, can accompany
   success or a post-session failure, and is absent for pre-session rejection or
   some finalization/mapping failures.

The public completed view contains a session ID and immutable event list
(`SkillExecutionView.java:6-16`). Each public event exposes timestamp, level,
type, JSON-like immutable details, and optional frame/route identifiers
(`SkillExecutionEvent.java:12-27`). The public mapper projects the finalized
journal in its existing order and does not promise history when finalization is
unavailable (`SkillExecutionViewMapper.java:32-56`). These events can contain
business data.

Public failures are already distinguishable at the boundary:
`SkillInputValidationException` carries immutable structured issues
(`SkillInputValidationException.java:5-18`), `AccessDeniedException` propagates
unchanged through the facade (`DefaultSkillTemplate.java:149-151,193-195`), and
other facade failures use `SkillException`. The framework preserves an existing
`SkillException` and wraps other runtime failures with a safe skill-level message;
it does not catch JVM `Error`. The current Sidecar has no HTTP error mapper or
execution-failure representation.

### Authentication and ownership

The POM already declares Boot MVC, Spring Security, OAuth2 resource server, and
OAuth2 JOSE dependencies (`pom.xml:49-67`), but the repository has no
`SecurityFilterChain`, `JwtDecoder`, JWT converter, JWT configuration properties,
or owner value. Consequently there is no Sidecar code that binds the ticket's
custom `loomspan-sidecar.auth.jwt` namespace to signature, issuer, audience,
lifetime, required-claim, key-source, skew, or role validation.

Framework role enforcement reads the current Spring `Authentication`. The
matching implementation prefixes each manifest role with `ROLE_` unless an
application `GrantedAuthorityDefaults` supplies another prefix, and applies any
configured `RoleHierarchy` (`SkillRoleEvaluator.java:12-28`). The public
integration fixture demonstrates request-thread authentication reaching a REST
handler and both validation- and invocation-time access denial
(`SupportedSurfaceIntegrationTest.java:118-185`). Sidecar currently has no
executor boundary and therefore no queued-context or cross-request leakage test.

### Execution admission, state, and retention

There are no Sidecar execution DTOs, records, maps, executors, counters, locks,
expiration sweeps, or diagnostics settings. There is therefore no current
accepted-execution lifecycle (`QUEUED` to `RUNNING` to a terminal state), no
retained-capacity or queue-byte accounting, no TTL behavior, and no cleanup of
input/security references. The only production Java file is the 13-line
application entry point.

The framework itself owns a separate root-admission and mission-execution
lifecycle. `SkillTemplate.validate` does not reserve it. `invoke` can still fail
after Sidecar pre-check because invocation repeats enforcement and enters the
framework lifecycle only at that point. Framework documentation also states that
its mission executor can retain work until mission/shutdown cutoff; the ticket's
Sidecar worker limit therefore concerns concurrent facade calls rather than a
total bound on nested or lingering framework work.

### Management, Console coexistence, and caching

The production application exposes health and readiness on management port 9091
and limits management web exposure to `health`
(`src/main/resources/application.yml:12-22`). The current management integration
test proves the ports differ, both health URLs return 200, `/actuator/info` is not
exposed, application-port health is not exposed, and `WebEndpointsSupplier`
contains exactly `health` (`ManagementEndpointIntegrationTest.java:35-53`).

Loomspan observability is disabled by default. When enabled later, the framework
owns `/_loomspan/observability/v1/**` and its independent
`X-loomspan-Api-Key` filter; its documented successful responses already carry
`Cache-Control: no-store` (framework `README.md:527-553`). Sidecar has no current
security rule that explicitly permits that namespace, and no global no-store
response policy for application routes or errors.

### Shutdown

Sidecar has no close listener, admission/dispatch flag, availability-state
publisher, or worker cleanup lifecycle. Its current readiness behavior is the
ordinary Actuator startup state; no test exercises close.

The pinned framework independently closes root admission on its owning
`ContextClosedEvent`, ignores other contexts, opts out of asynchronous listener
execution, and returns from that event promptly; it waits for admitted roots and
its executor later in `SmartLifecycle.stop` under one configured deadline
(`FrameworkExecutionLifecycle.java:73-119`). This inspected implementation is
not a supported Sidecar dependency: repository rules prohibit importing it,
depending on its bean name, or coordinating through its numeric lifecycle phase.
SC5 records that Sidecar resources must remain usable during the framework's
later lifecycle wait and that packaged tests must prove relative listener-order
independence (`phase-sc5.md:41-64`).

## Key Components

- `pom.xml:12-18` — Java 21, Boot 4.1.0, and beta.4 snapshot version alignment.
- `pom.xml:43-83` — framework, MVC, Security resource-server/JOSE, Actuator, test,
  and ArchUnit dependencies already present.
- `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java:6-12` — sole
  production class and component-scan root.
- `src/main/resources/application.yml:1-22` — current mounted-skill,
  observability, reserved route-file, and management defaults.
- `README.md:3-7` — current delivery boundary explicitly defers API,
  authentication, workers, REST handling, and packaging.
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:13-32`
  — forbids production and test dependencies on `ai.loomspan.internal..` and
  `ai.loomspan.autoconfigure..`, and forbids Sidecar-hosted Java skills.
- `ai/thoughts/phases/phase-sc2.md:16-118` — authoritative HTTP, validation,
  execution-state, queue/accounting, ownership, failure, and diagnostics behavior.
- `ai/thoughts/phases/phase-sc3.md:22-83` — authoritative JWT, identity, roles,
  stateless transport, Console coexistence, and queued-token behavior.
- `ai/thoughts/phases/phase-sc5.md:30-64` — downstream shutdown/resource-lifecycle
  boundary that constrains this ticket without moving packaging into it.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillCatalog.java:6-15`
  — supported, eager, unfiltered discovery contract.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:11-25`
  — supported Map validation and invocation/observer overloads.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillExecutionEvent.java:12-69`
  — supported diagnostic representation and JSON-like detail constraints.
- `C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:101-185`
  — framework executable example for catalog, validation, invocation,
  observation, authorization, and REST handler authentication.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| HTTP surface | No Sidecar controllers or `/v1/**` routes exist; README says the execution/catalog API is absent (`README.md:65-71`). |
| Request parsing and bounds | MVC and Jackson 3 arrive through the Boot platform, but Sidecar has no request-body reader or `max-input-size` setting. |
| Framework discovery | Mounted manifests feed the framework's eager, exact, immutable, unfiltered public catalog; existing tests currently assert only skill name/kind registration. |
| Framework pre-check | The Map validation overload performs exact lookup, contract validation, and root access evaluation without session/admission; no current HTTP mapping consumes its exceptions. |
| Async execution | No Sidecar worker pool, accepted record, queue, or polling representation exists. Framework invocation itself is synchronous to its caller. |
| JWT verification | Required dependencies exist, but no Sidecar decoder, validators, converter, custom property binding, or filter chain exists. |
| Roles | Framework authorization consumes Spring authorities and `GrantedAuthorityDefaults`; Sidecar does not currently construct JWT authorities or configure that prefix. |
| Ownership | No issuer/subject owner type or record access check exists. |
| Diagnostics | Public observer events are available and immutable; Sidecar has no `NEVER`/`ONERROR`/`ALWAYS` selection or storage. |
| Admission/retention | No `max-retained`, TTL, worker, queue-count, queued-byte, or expiration implementation exists. |
| Shutdown/readiness | Only ordinary Actuator readiness exists; Sidecar has no owning-context close listener or dispatch/admission cleanup. |
| Console coexistence | Framework observability is disabled by default; Sidecar has no application security policy for its reserved namespace when enabled. |
| Documentation | README documents only SC1 mounts, model setup, probes, dependency/release sequence, and explicit deferral of this feature. |

## Existing Tests and Fixtures

The existing Sidecar suite has eight tests across five classes. Existing
Surefire reports in `target/surefire-reports` record zero failures/errors/skips,
but this research step did not rerun them; the dirty documentation also records
an SC1 `clean verify` and clean-source verification as historical evidence.

- `LoomspanSidecarApplicationTest` starts a non-web context with an empty skill
  pattern and asserts an empty public catalog (`lines 18-28`).
- `MountedSkillRegistrationIntegrationTest` covers both YAML suffixes, custom
  locations, route-file scan exclusion, and startup failure for an unconfigured
  model (`lines 25-75`). Its model URL is loopback port 9 and no invocation is
  made, so it has no provider-side effect.
- `ManagementEndpointIntegrationTest` uses random loopback ports and Java's HTTP
  client to verify the separate health-only management surface (`lines 17-60`).
- `SidecarDefaultsTest` loads only production configuration data and asserts all
  current SC1 defaults (`lines 10-34`).
- `SupportedLoomspanApiArchitectureTest` imports the full Sidecar package,
  including tests, and enforces the framework boundary and no-Java-skill rule
  (`lines 13-32`).
- Current resource fixtures are two simple model-backed YAML manifests and one
  invalid-as-a-skill route-file decoy. There is no shared authenticated Sidecar
  web fixture, REST skill manifest/handler, local JWT issuer/JWKS/key material,
  blocking/concurrency fixture, or shutdown fixture.

The matching framework's `SupportedSurfaceIntegrationTest` is useful as an
executable public-contract pattern, but its success is not Sidecar application
evidence. It configures a local OpenAI-compatible mock server, a YAML planner, a
Java leaf, and a `RestSkillHandler`, then tests catalog shape, Map/object
pre-checks, exact string results, observer history, handler authentication, and
access denial (`lines 42-190`). Sidecar tests may not copy its Java-skill pattern
because this repository explicitly forbids hosting Java `@SkillMethod` skills.

## Dependencies and Operational Constraints

- The Boot BOM controls Java platform dependencies: Boot 4.1.0, Spring Framework
  7.0.8, Spring Security 7.1.0, and Boot's Jackson 3 `tools.jackson` APIs. The POM
  already has the resource-server and JOSE modules; there is no separate version
  to choose for JWT support.
- Sidecar consumes the developer-installed
  `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`; it does not
  rebuild the framework or declare a snapshot repository. Hosted CI deliberately
  overrides this with the not-yet-published `1.0.0-beta.4`
  (`.github/workflows/ci.yml:10-23`).
- Application and test code may use Loomspan Java types only from the closed
  `ai.loomspan.api` surface. `RestSkillHandler` is the only supported SPI.
  Framework source inspection above establishes behavior but does not authorize
  importing `internal` or `autoconfigure` types, reflection, bean replacement,
  or undocumented settings.
- Framework keys stay under documented `loomspan.*`; Sidecar-owned settings use
  `loomspan-sidecar.*`; standard server SSL/mTLS remains under Boot's namespace.
  Skills and routes are startup-only and restarting loses any in-memory store.
- JWT is the only inbound execution/catalog credential. Console's API key is
  separate operator authentication. Inbound mTLS is transport protection, not
  an X.509 identity source.
- Identity and roles accepted at admission are intended to survive token expiry
  for local queued execution. A future SC4 passthrough target will independently
  validate the original token, so queue time consumes callback token lifetime.
- Input size, queued bytes, retained record count, and completion TTL are
  different boundaries. None limits total heap, result size, diagnostic size,
  fetched attachment content, or framework-owned references from uncooperative
  work.
- The shutdown listener in this ticket must be independent and prompt. Already
  dispatched facade calls stay alive for framework mission timeouts and the one
  `loomspan.shutdown.timeout`; SC5 later proves packaged resource ordering.
- Tests and examples for this unit must remain local and deterministic. Research
  contacted no live service and triggered no external action.

## Historical Context

Commit `e3c3f58` introduced the application, Maven wrapper/POM, mounted-skill
defaults, health-only management surface, CI skeleton, public-surface guard, and
all eight current tests. The pre-existing working-tree documentation updates
mark SC1 locally complete and name authenticated execution (SC2+SC3) as the next
delivery unit.

The authoritative handoff groups work into scaffold, authenticated execution
API, generic REST handler, and packaging/release units
(`ai/thoughts/beta4-handoff.md:26-39`). The design lens assigns skill validation,
authorization, nesting, and execution lifetime to the framework while Sidecar
owns transport, admission, retention, owner-scoped access, identity propagation,
diagnostic selection, and the immediate dispatch gate. It also requires one
admission authority and one retained record rather than duplicate queues,
semaphores, stores, or shutdown budgets.

The alignment review records that no new framework contract was required for
SC2/SC3 at the pinned revision. It specifically confirms that Map validation
returns no normalized request or admission reservation, the observer can be
absent on some failures, the facade's primary exception remains authoritative,
and `GrantedAuthorityDefaults` controls framework role-prefix semantics.

## Open Questions

- Planning still needs to choose the Sidecar-internal DTO/package layout and the
  simplest executor/admission-accounting structure. The ticket explicitly leaves
  those implementation details open while fixing their observable behavior and
  single-authority invariants.
- Planning needs to spell out the exact custom JWT property fields for discovery,
  explicit JWKS, public-key material, and clock skew, then map each key-source
  mode to standard Spring Security decoders and the same validator set. The
  issuer, audience, required claims, expiry, and role semantics are already
  settled and require no product decision.
- The existing tests do not yet provide the required common authenticated web
  fixture. Planning must identify how focused configuration-context tests and
  full application HTTP/concurrency/shutdown tests share fixtures without
  introducing Sidecar Java skills or relying on framework internals.
- SC5's packaged lifecycle suite will need the resource ordering established by
  this ticket. This unit must make that later proof possible, but image, callback
  host, release, and packaged quick-start work remain outside the current scope.
