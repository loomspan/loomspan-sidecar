---
date: 2026-09-13
repository: loomspan-sidecar
branch: main
commit: fba8351a97f80f6a5352ca6cd7b5e1bfdc0ef57b
ticket: ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md
tags: [sc2, sc3, execution-api, jwt, concurrency, retention, diagnostics, shutdown]
---

# Authenticated Execution API Research

## Research Question

What behavior is present in the checked-out SC2/SC3 implementation, how does it
flow through HTTP, JWT security, framework validation/invocation, admission,
retention, diagnostics, and shutdown, and what executable evidence currently
supports the authenticated-execution ticket?

## Summary

The current checkout already contains the authenticated asynchronous execution
API introduced by `2597238` and retained unchanged by the cleanup commit at
`HEAD`. Authenticated callers can discover the framework's unfiltered public
catalog, submit a bounded JSON-object input, and poll an issuer/subject-owned
in-memory record. Admission validates on the request thread, then one coordinator
owns the record map, executor queue, queued count/bytes, expiration, diagnostics
selection, security-context handoff, and shutdown gate.

JWT verification is explicitly bound for issuer discovery, JWKS, or RSA public
key modes, with common issuer, audience, timestamp, and required-claim
validators. The worker installs the admitted `JwtAuthenticationToken` before
calling the public Map `SkillTemplate.invoke` overload and clears both the
thread-local context and task-held input/token references on exit. Terminal
result/failure and selected events are published in one immutable snapshot.

The repository has focused and application-level tests for the central flow,
JWT modes, ownership, queued/nested identity, capacity, TTL, diagnostics,
Console coexistence, and basic shutdown behavior. Existing Surefire XML reports
show all 33 tests passing, but they predate this research and were not rerun in
Step 1. Several ticket details have no direct dedicated assertion, including an
HTTP-level `413`, client disconnect, large result/event preservation, task-field
reference release, an explicit dispatch/dequeue race, HTTP shutdown `503`, and
packaged lifecycle ordering (the latter remains SC5 scope).

## Repository State

- Research recorded at `2026-09-13T22:53:24-07:00` in
  `C:/opendev/code/loomspan-sidecar`.
- Branch `main`; `HEAD` and `origin/main` are
  `fba8351a97f80f6a5352ca6cd7b5e1bfdc0ef57b` (`Cleanup Async Skill Exec`).
- Before this artifact was created, the only working-tree entry was untracked
  `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md`. It is a later
  SC4 ticket, unrelated to this research, and was not read or modified.
- Relative to the completed SC1 commit `e3c3f58`, commit `2597238` added the
  current controllers, JWT configuration, coordinator/state model, tests,
  fixtures, defaults, documentation, and the authenticated-execution ticket.
  Commit `fba8351` deleted the prior research, implementation/test plans, and
  three review records only; it did not change the production or test files for
  this feature.
- The configured framework dependency remains
  `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`
  (`pom.xml:16-18,43-48`). The clean local framework checkout currently reports
  `e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f`; repository policy says the
  developer keeps that checkout and installed snapshot aligned and ordinary
  Sidecar work does not perform separate artifact provenance verification.

## Current Behavior and Data Flow

### Authentication and routing

`JwtSecurityConfiguration` creates one resource-server decoder from an RSA
public key, explicit JWKS URL, or issuer discovery, in that order
(`JwtSecurityConfiguration.java:34-47`). Every mode receives the same timestamp
validator with configured skew, issuer validator, audience validator, and a
custom validator requiring nonblank `iss` and `sub` plus `exp` (`lines 49-68`).
`SidecarJwtProperties.validate` requires issuer, audience, roles claim, non-null
role prefix, and nonnegative skew, and rejects simultaneous JWKS and public-key
configuration (`SidecarJwtProperties.java:33-40`).

The JWT converter reads the configured roles claim and applies the configured
authority prefix; a `GrantedAuthorityDefaults` bean exposes that same prefix to
framework role evaluation (`JwtSecurityConfiguration.java:71-84`). The servlet
chain is stateless, disables CSRF, permits only the reserved Console namespace
and health namespace, requires authentication for `/v1/**`, denies other
requests, and emits problem JSON for authentication/authorization failures
(`lines 86-113`). The highest-precedence response filter sets
`Cache-Control: no-store` before the rest of the chain, so it also covers
security and error responses (`NoStoreResponseFilter.java:14-22`).

`SkillCatalogController` returns `SkillCatalog.skills()` unchanged and performs
an exact catalog lookup for one descriptor, mapping absence to `404`
(`SkillCatalogController.java:12-25`). The framework public contract describes
that catalog as an eager, immutable, exact-name-sorted, authorization-unfiltered
snapshot; `SkillDescriptor` contains name, description, kind, and input schema
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillCatalog.java:6-15` and
`.../SkillDescriptor.java:5-14`).

### POST admission

`ExecutionController.execute` follows this request-thread sequence
(`ExecutionController.java:41-51`):

1. Resolve the named skill through `SkillCatalog`; absence raises `404`.
2. Read the raw body through `LimitedJsonObjectReader`.
3. Call public `SkillTemplate.validate(name, input)` using the Map overload.
4. Serialize the validated map once to measure its JSON byte length.
5. Capture `ExecutionOwner(issuer, subject)` and the full
   `JwtAuthenticationToken`, then ask the coordinator to admit the task.
6. Return `202` with `{ "id": ... }` and a polling `Location`.

The reader rejects a declared or observed raw length above `max-input-size`
before parsing, allows equality, rejects empty, null, non-object, malformed, or
trailing JSON, and converts a JSON object to `Map<String,Object>` through Boot's
Jackson 3 mapper (`LimitedJsonObjectReader.java:24-56`). Validation exceptions,
access denial, missing resources, oversize input, capacity rejection, and closed
admission map to `400`, `403`, `404`, `413`, `429`, and `503` problem details,
respectively (`ApiExceptionHandler.java:12-42`). Structured validation issues
are added to the `400` problem document (`lines 17-21`).

### Admission, queue accounting, and execution

`ExecutionCoordinator` owns one `ConcurrentHashMap`, one lock, a fixed-size
`ThreadPoolExecutor`, a custom ordinary work queue, queued count/byte counters,
and one daemon expiration sweeper (`ExecutionCoordinator.java:35-71`). Under
the lock, admission rejects a closed gate, removes expired terminal records,
checks the all-status retained-record limit, creates one queued record and task,
and submits it (`lines 74-98`). A rejected executor submission removes that
record, clears the task, releases any reservation, and surfaces queue capacity
failure.

The executor queue first tries a direct worker handoff. Only waiting work
reserves count and bytes; its locked check allows equality and rejects a count
or byte excess (`ExecutionCoordinator.java:209-227`). Queue removal paths release
the reservation exactly once through the task's `reserved` flag
(`lines 229-257,299-301`). When a task begins, the coordinator releases any
queue reservation under the same lock, refuses dispatch if shutdown closed the
gate or the record disappeared, otherwise replaces the initial `QUEUED`
snapshot with `RUNNING` (`lines 125-139`).

The worker creates and installs a fresh Spring `SecurityContext` containing the
captured JWT authentication, invokes public
`SkillTemplate.invoke(name, input, observer)`, and then publishes success or the
primary thrown runtime failure (`ExecutionCoordinator.java:277-292`). The
framework public API states that Map validation does not reserve admission and
that the Map invocation overload returns text and may synchronously supply an
available completed view
(`C:/opendev/code/loomspan-framework/loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:11-25`).
The worker flattens every received public view's events in delivery order and
does not inspect internal/Console history (`ExecutionCoordinator.java:283-297`).
It clears the thread-local security context, input map, and authentication
reference in `finally` (`lines 289-301`).

### Completion, diagnostics, ownership, and retention

The coordinator classifies only the exception directly thrown by the facade:
input validation and access denial receive their named kinds, and all other
failures become `SKILL_FAILURE` with the original message
(`ExecutionFailureClassifier.java:9-17`). It does not traverse causes. Under
the coordinator lock, completion chooses events according to `NEVER`, `ONERROR`,
or `ALWAYS` and replaces the record's volatile snapshot once with status,
completion time, unchanged result or classified failure, and selected events
(`ExecutionCoordinator.java:141-157`). `ExecutionSnapshot` copies a selected
event list, and the framework event value recursively holds immutable standard
JSON-like details (`ExecutionSnapshot.java:9-14` and framework
`SkillExecutionEvent.java:12-69`).

Polling compares an immutable owner containing separate issuer and subject
fields and exposes no distinction among unknown, expired, or foreign records
(`ExecutionOwner.java:7-18`; `ExecutionCoordinator.java:100-109`). The controller
always emits id, skill name, status, timestamps, result, and failure, adds issues
only when present, and omits the `events` property when diagnostics selection is
`null` (`ExecutionController.java:54-76`). An owned `FAILED` snapshot therefore
remains an ordinary successful GET response.

Terminal TTL starts at `completedAt`. Both reads and admissions synchronously
remove expired records, and the scheduled sweep runs at the smaller of the TTL
or 60 seconds (`ExecutionCoordinator.java:69-71,79-81,100-123`). Reads do not
alter completion time. The entire record—including selected events and result or
failure—is removed together; queued and running records do not expire.

### Shutdown and management behavior

The coordinator listens independently for its owning context's close event,
ignores other contexts, and opts out of asynchronous listener execution
(`ExecutionCoordinator.java:168-188`). Under the shared gate it closes admission
and dispatch, publishes `REFUSING_TRAFFIC`, drains waiting tasks directly from
the executor queue, removes their records, releases their reservations, and
clears task input/authentication (`lines 169-184`). Running tasks are not
removed or interrupted by this event and can still publish terminal state.

Bean destruction stops the expiration sweeper and calls `shutdownNow` on the
worker executor, then removes and clears any returned waiting tasks
(`ExecutionCoordinator.java:190-203`). There is no Sidecar drain loop or second
timeout in this method. Production defaults expose only health (including
readiness) on management port 9091; Loomspan observability is disabled by
default (`application.yml:25-35,6-7`).

## Key Components

- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41` — POST
  validation/admission and owner-scoped polling representation.
- `src/main/java/ai/loomspan/sidecar/api/LimitedJsonObjectReader.java:24` — raw
  body byte bound and strict JSON-object parsing.
- `src/main/java/ai/loomspan/sidecar/api/ApiExceptionHandler.java:14` — known
  request failures mapped to RFC-style problem details.
- `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:34`
  — decoder/key-source selection, validators, roles, and servlet security.
- `src/main/java/ai/loomspan/sidecar/security/ExecutionOwner.java:7` — immutable
  issuer/subject ownership value.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:35` —
  single admission, queue, retained-store, worker, expiry, and shutdown owner.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionRecord.java:8` — stable
  owner/identity metadata and one volatile current snapshot.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionFailureClassifier.java:9`
  — primary facade-exception classification without cause traversal.
- `src/main/java/ai/loomspan/sidecar/config/SidecarExecutionProperties.java:8`
  — Sidecar execution defaults and positive-limit/TTL validation.
- `src/main/resources/application.yml:9` — JWT, execution, management, and
  startup-only mount defaults.
- `README.md:73` — operator-facing JWT, API, ownership, capacity, diagnostics,
  data exposure, shutdown/restart, mTLS, and delivery-boundary documentation.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| HTTP contract | Four authenticated mappings are split between catalog and execution controllers; known transport failures use problem documents, accepted execution uses `202` plus `Location`, and owned terminal failure remains `200` (`SkillCatalogController.java:19-24`; `ExecutionController.java:41-67`). |
| Raw input | Request bytes are bounded before Jackson parsing; only one complete JSON object is accepted and map serialization supplies queued-byte accounting (`LimitedJsonObjectReader.java:24-56`). |
| Framework boundary | Production code uses public `SkillCatalog`, `SkillTemplate`, descriptors/events, and standard Spring APIs; the ArchUnit test forbids `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..` and forbids Sidecar Java skills (`SupportedLoomspanApiArchitectureTest.java:13-32`). |
| JWT and roles | All key-source modes share issuer/audience/timestamp/claim validation; custom role claim/prefix is aligned with `GrantedAuthorityDefaults` (`JwtSecurityConfiguration.java:34-84`). |
| Ownership | Admission stores issuer and subject separately; lookup compares the pair and returns absence for foreign records (`ExecutionOwner.java:7-18`; `ExecutionCoordinator.java:100-105`). |
| Concurrency and capacity | One fixed worker pool and one admission queue enforce direct handoff, waiting count/bytes, and all-status retained records under one gate (`ExecutionCoordinator.java:62-98,209-257`). |
| Diagnostics | Available observer events are flattened and terminal outcome plus selected events replace one snapshot atomically (`ExecutionCoordinator.java:141-157,283-297`). |
| Retention | Completion starts TTL; reads, admission, and one periodic sweep remove the whole terminal record without extending expiry (`ExecutionCoordinator.java:69-71,100-123`). |
| Shutdown/readiness | Owning-context close synchronously lowers readiness, closes admission/dispatch, discards waiting work, and leaves already-running work untouched by the event (`ExecutionCoordinator.java:168-188`). |
| Console and management | The application chain permits Console's reserved namespace for its own framework filter, while API keys cannot authenticate `/v1`; only health is exposed on the separate management port (`JwtSecurityConfiguration.java:92-96`; `application.yml:25-35`). |
| Documentation | README describes routes, JWT sources/roles/lifetime, mTLS, limits, polling/failure semantics, ownership, diagnostics/data exposure, memory caveats, shutdown/restart, Console coexistence, and the SC4/SC5 boundary (`README.md:69-182`). |

## Existing Tests and Fixtures

Existing XML reports under `target/surefire-reports` record 33 tests, zero
failures, zero errors, and zero skips. Their directory timestamp is earlier than
this research, so this is retained build evidence rather than a fresh Step 1
verification run.

- `AuthenticatedExecutionApiIntegrationTest.java:37-181` starts the full web
  application on random loopback ports with public-key JWT verification, two
  YAML REST skills, a test-only public `RestSkillHandler`, and a local deterministic
  OpenAI-compatible server. Its six tests cover unauthenticated/no-store,
  unfiltered catalog and unknown skill, request-thread validation/role denial,
  `202`/Location/exact result, renewed/foreign owner access, owned failure as
  `200` without stack trace, queued token expiry and context isolation, and
  nested handler visibility of the original JWT.
- `JwtSecurityConfigurationTest.java:20-115` covers RSA public-key signature,
  issuer, audience, expiration, required claims, configurable clock skew,
  invalid and ambiguous properties, custom role claim/prefix bean alignment,
  issuer discovery, and explicit JWKS against a loopback HTTP server.
- `ExecutionCoordinatorTest.java:37-219` covers direct worker plus queued state,
  worker token propagation, owner issuer/subject comparisons, queue count/bytes,
  retained limit, simultaneous admission, byte-limit equality/excess without an
  orphan, completion-based expiry, and shutdown discard/closed admission while
  active work completes.
- `ExecutionDiagnosticsTest.java:29-140` covers the three failure kinds,
  no arbitrary cause unwrapping, atomic failure/events visibility, all three
  selection modes for success/failure, and a failure with no callback.
- `ExecutionRequestBodyIntegrationTest.java:14-43` directly exercises the
  reader's malformed/missing/null/non-object handling and exact/excess UTF-8 raw
  limit. It does not send these cases through the HTTP controller.
- `ExecutionShutdownIntegrationTest.java:17-46` asserts synchronous close-event
  participation, foreign-context isolation, and owning-context readiness
  publication. `ManagementEndpointIntegrationTest.java:17-64` proves the
  separate management port exposes health/readiness but not info or
  application-port health.
- `ConsoleSecurityIntegrationTest.java:16-51` proves Console API-key access and
  `/v1` JWT access remain independent. `SidecarDefaultsTest.java:10-43` and
  `SidecarExecutionPropertiesTest.java:9-22` cover exact default values and
  direct invalid limit/TTL validation.
- `SupportedLoomspanApiArchitectureTest.java:13-32` guards the supported
  framework boundary. Existing mount/startup tests continue to exercise YAML
  registration and the route-file exclusion.
- `src/test/resources/fixtures/execution-skills/echo-rest.yml` and
  `nested-rest.yml` are deterministic public REST-skill fixtures. The local RSA
  key pair in `src/test/resources/fixtures/jwt-*.pem` is test-only. Test HTTP
  services bind loopback; the suite requires Java 21, the Maven wrapper, and the
  locally installed framework snapshot but no external provider account.

Important ticket paths without a dedicated current assertion are: HTTP-level
oversize `413` and invalid-body record absence; client-disconnect independence;
large result/event non-truncation; weak-reference or equivalent proof that
completed task input/security references are released; selected diagnostics and
outcome expiring together; custom role prefix exercised through both framework
validation and invocation; explicit dispatch-failure and shutdown/dequeue race
accounting; HTTP `503` after close; asynchronous event-multicaster close timing;
and bounded packaged cleanup relative to framework shutdown. Some follow from
shared code paths, while packaged lifecycle proof is explicitly deferred to SC5.

## Dependencies and Operational Constraints

- The Maven build targets Java 21 and Boot 4.1.0 and uses Boot's Jackson 3 APIs;
  resource-server, JOSE, MVC, Security, Actuator, and ArchUnit dependencies are
  already declared (`pom.xml:12-18,43-83`).
- Sidecar relies on the developer-installed beta.4 framework snapshot and does
  not rebuild the framework or declare a snapshot repository. Hosted CI remains
  dependent on later publication of `1.0.0-beta.4`.
- Application and test code are restricted to the closed `ai.loomspan.api`
  surface. `RestSkillHandler` is the authorized test SPI; no internal imports,
  bean replacement, reflection bypass, or Sidecar-hosted Java skills are
  present.
- JWT is the sole inbound `/v1` credential. Console API-key authentication is
  separate. Standard `server.ssl.client-auth` can add transport mTLS but does
  not create execution identity.
- Identity/roles accepted at admission are deliberately reused after local JWT
  expiry. Queue time still consumes the credential's useful lifetime for future
  SC4 passthrough because each remote target validates independently.
- Input-byte, queued-byte, retained-count, and TTL bounds do not limit total
  process heap, result/event size, or framework-owned lingering operations.
  Records and work are memory-only and restart loses them.
- The close-event gate is prompt and independent; already-dispatched work is
  left to framework mission timeouts and `loomspan.shutdown.timeout`. SC5 owns
  the packaged relative-lifecycle proof, image, and release sequence.
- README examples and current tests use environment-supplied secrets or local
  deterministic fixtures. This research contacted no live external service.

## Historical Context

The authoritative handoff groups SC2 and SC3 as one delivery unit because JWT
identity simultaneously controls request validation, issuer/subject ownership,
queued and nested authorization, and future callback propagation
(`ai/thoughts/beta4-handoff.md:26-39`). The design lens assigns skill validation,
authorization, nesting, and execution lifetime to the framework while Sidecar
owns HTTP transport, admission, retention, identity handoff, diagnostic
selection, and the immediate shutdown gate
(`ai/thoughts/design-lens.md:19-67`).

Commit `2597238` implemented that unit and also created research, implementation
planning, test planning, and three review documents. Commit `fba8351` immediately
removed those six pipeline artifacts as cleanup while preserving the feature
implementation, ticket, tests, and README. Accordingly, this research uses the
checked-out source and tests as current evidence rather than treating deleted
reports as an active assurance record.

The ticket's acceptance boxes are checked in the current file, and README
describes SC2/SC3 as delivered, but those documentation states are separate from
fresh verification. The local phase files still contain unchecked acceptance
boxes and pre-implementation wording; they remain useful statements of behavior
and boundary but disagree with the ticket/README about delivery status.

## Open Questions

- Planning needs to determine which unasserted ticket details require new tests
  versus sufficient verification of existing shared paths. The checked ticket
  and existing code settle product behavior; this is an assurance-scope question,
  not a request to redesign the API.
- The local SC2/SC3 phase files still say Sidecar implementation evidence is
  pending and retain unchecked acceptance lists, while the ticket and README say
  the unit is delivered. The pipeline should preserve this disagreement unless
  a later documentation step intentionally reconciles authoritative status.
- Packaged shutdown/resource ordering remains explicitly owned by SC5. Current
  unit tests cover the Sidecar gate and active-vs-queued behavior but do not
  provide packaged evidence of cleanup after the framework cutoff.
