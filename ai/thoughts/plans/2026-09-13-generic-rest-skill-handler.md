# Generic REST Skill Handler Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md`
- Research: `ai/thoughts/research/2026-09-13-generic-rest-skill-handler.md`
- Outcome: Load one immutable REST target/route document at startup and expose one production `RestSkillHandler` that performs bounded GET/POST calls with fixed binding, target-owned authentication/TLS, safe failures, and shutdown ordering that preserves admitted framework work.

## Current State

`src/main/resources/application.yml` reserves `loomspan-sidecar.rest-routes-location`, but no Sidecar code reads it. `LoomspanSidecarApplication` enables only execution and JWT properties, and production has no `RestSkillHandler`, route model, HTTP client, route/catalog validator, or REST transport lifecycle owner.

The existing authenticated path is otherwise ready for this unit. `ExecutionController` admits the concrete `JwtAuthenticationToken`; `ExecutionCoordinator.ExecutionTask` installs it in `SecurityContextHolder` while invoking `SkillTemplate`; and the framework propagates that authentication to its REST-handler thread. The public eager `SkillCatalog` exposes exact names and `SkillKind.REST`, so a Sidecar startup validator can compare the completed catalog with the already-loaded route map without using framework internals.

`AuthenticatedExecutionApiIntegrationTest` and `CustomRoleExecutionIntegrationTest` currently import a test-only handler. They must instead configure a route file and loopback host so the exactly-one production bean is exercised. Every existing application context must also supply an explicit empty route document because the file becomes mandatory even when the catalog contains no REST skills.

## Desired End State

At startup, Sidecar reads exactly one configured YAML resource, resolves `${...}` placeholders through the Spring environment, rejects duplicates and unknown fields, validates every local target/route setting and SSL-bundle reference, and freezes named target and skill-keyed route maps. A separate startup validator then compares those routes with the completed public catalog: each REST skill has exactly one route, each route names a REST skill and defined target, and non-REST skills cannot have routes. This validation performs no network call.

For a valid invocation, the production handler selects its immutable route and per-target `RestClient`. GET consumes whole-segment path variables and sends remaining scalar inputs as encoded query parameters. POST consumes path variables and serializes the remaining JSON-compatible input as an object, including `{}`. The configured base scheme, authority, and base-path prefix cannot be replaced or escaped. Targets independently apply `none`, static headers, or the original caller bearer token, plus the fixed Accept header.

The handler does not follow redirects or retry. It streams a response only up to the configured byte cap, accepts equality, returns an empty string for any bodyless 2xx, and accepts nonempty `application/json`, structured `+json`, or `text/*` using the declared charset or UTF-8 when absent. It bypasses default error-body buffering and emits bounded `SkillException` diagnostics without body content. Per-target clients remain available while the framework's higher-phase lifecycle completes or cuts off admitted work, then close promptly in the handler's ordinary lower lifecycle phase without a second drain budget.

These behaviors map to all ticket acceptance criteria: startup/configuration correspondence; GET/POST and JSON-value validation; URI confinement; auth modes; timeout/TLS/redirect isolation; response streaming/media rules; visible `SKILL_FAILURE`; production end-to-end JWT callback including queue expiry; restart-only activation and lifecycle; architecture, wrapper, and documentation conformance.

## Scope

### In scope

- Sidecar-owned property, strict YAML loading, placeholder resolution, immutable target/route models, local validation, and public-catalog correspondence.
- One always-present production `RestSkillHandler`, per-target Spring `RestClient`/Apache client construction, fixed GET/POST binding, JSON compatibility checks, URI confinement, auth modes, TLS bundles, timeouts, no redirect/retry, bounded response processing, and bounded failures.
- Standard Spring lifecycle ownership of the introduced clients, preserving framework completion/cutoff ordering.
- Deterministic loopback HTTP, HTTPS/mTLS, JWT callback, queue, restart, and lifecycle tests; conversion of affected existing tests to the production handler.
- README and configuration examples for the complete SC4 contract.

### Out of scope

- SC5 image, packaging matrix, deployment quick start, Maven Central/public release work, and exhaustive packaged shutdown proof.
- New inbound routes or authentication modes; token minting, refresh, or exchange; worker-side expiry revalidation.
- Hot reload, route merging/aliases, inline target maps, retries, arbitrary HTTP verbs or templates, upload/file transport, and response transformation.
- New Loomspan APIs/SPIs, framework bean replacement, internal imports/reflection, or a second skill/catalog authority.

## Active Project Guardrails

- Prefer the smallest complete implementation; add collaborators only where configuration, transport, and lifecycle need independently testable ownership.
- Use only the closed `ai.loomspan.api` surface. The framework remains the authority for registration, authorization, nested invocation, and execution lifetime.
- Keep trusted JWT identity in Spring Security context, never in model input, and forward only the captured original credential.
- Load skills/routes only at startup. Preserve the one framework shutdown budget: Sidecar closes dispatch immediately, framework completes/cuts off admitted roots, and REST clients close afterward without another wait period.
- Preserve result text and the existing primary failure/selected-diagnostics contract; the handler bounds only the HTTP diagnostics it deliberately constructs.

## Impact and Risk Analysis

The route document is a new startup contract. Lenient YAML, implicit map overwrites, automatic placeholder expansion assumptions, or catalog validation during handler construction could silently misroute calls or create a handler/catalog registration cycle. The loader therefore needs strict duplicate/unknown-field behavior and field-addressed validation, while catalog correspondence remains a distinct post-construction startup check.

URI construction and credential forwarding are security boundaries. Joining through ordinary URI resolution would allow a leading route slash to discard the base path; pre-expanded templates could allow reserved input to alter path structure. The implementation will validate and tokenize the configured base/route once, substitute only complete path-variable segments, percent-encode variable/query values as components, and reject static dot/traversal or authority/query/fragment constructs before any request. Static headers are configuration-only, and passthrough reads only a `JwtAuthenticationToken` from the current handler thread.

Default `RestClient` retrieval/error handling can buffer response bodies. The handler must use `exchange` to own status, headers, and the raw stream, skip non-2xx body materialization, and copy successful bodies into a capped buffer one chunk at a time. Media type and charset validation occurs after detecting whether a successful body is empty. Diagnostics contain only a bounded target identifier, status, media type, and fixed reason.

TLS and timeout values vary per target, so clients cannot share mutable request settings. Each target receives an isolated Apache client/request factory and `RestClient`; SSL context comes only from the referenced Boot `SslBundle`. Redirects are disabled at the Apache layer and no retry strategy is installed. Local deterministic tests must demonstrate the effective behavior of the pinned versions rather than rely only on configuration inspection.

Shutdown ordering cannot depend on framework bean names, listener order, or its internal phase number. The REST client owner will use standard `SmartLifecycle` with its normal phase. Spring stops the framework's admitted-work owner first because that owner advertises the highest phase; only then does the lower-phase Sidecar owner close clients idempotently and without waiting. An application test will prove usable transport during admitted close and bounded release after completion/cutoff.

## Implementation Approach

Add a small `ai.loomspan.sidecar.rest` package with three responsibilities. `RestRoutesProperties` binds only the route resource location. `RestRouteLoader` owns strict parsing, placeholder resolution, field validation, immutable route/target records, and prevalidated URI templates. `GenericRestSkillHandler` owns client construction, binding, request execution, bounded response decoding, auth, and client lifecycle. `RestRouteCatalogValidator` performs only the late public-catalog correspondence check.

Use a Jackson 3 `YAMLMapper` configured for unknown-property and strict duplicate detection, with package-private document records/POJOs that describe only the supported keys. Resolve each parsed string with `ConfigurableEnvironment.resolveRequiredPlaceholders` before semantic validation; wrap failures with the resource location, field path, and unresolved placeholder text, never the resolved value. Convert durations/data sizes using Boot value types or their standard parsers and require positive values. Resolve a named bundle through `SslBundles.getBundle`, translating a missing bundle to a target-keyed startup error.

Precompile route paths into static and whole-variable segments. Require `/`, reject `//`, scheme/authority/query/fragment syntax, malformed or repeated variable names, and static dot segments or encoded traversal. Validate base URLs as absolute HTTP(S) URIs with host and without user-info, query, or fragment; normalize only the joining slash. At invocation, recursively accept only null, string, boolean, finite number, string-keyed map, and list values. Require non-null scalar path/GET values; remove consumed path fields; encode each path value as one segment and remaining GET values as query components. POST serializes the remaining validated map with the Boot Jackson 3 `ObjectMapper`.

Build one Apache `CloseableHttpClient` and Spring `RestClient` per target. Configure connect/read timeouts, optional bundle-derived SSL context, disabled redirect handling, and no automatic retry. Use `RestClient.exchange` so non-2xx bodies are never converted by default handlers. On 2xx, stream at most `max + 1` bytes, allowing exact equality, then decode supported JSON/text using an explicit declared charset or UTF-8 default. Convert known handler/configuration/transport outcomes to intentionally bounded `SkillException`; preserve interrupts and do not unwrap arbitrary failures for their messages.

The material alternatives are less suitable: inline Boot map binding cannot detect all duplicate YAML keys or safely annotate placeholder failures in the separate file; one shared mutable client cannot isolate target SSL/timeouts; and a custom listener/second shutdown timer would duplicate framework lifecycle authority. The selected direct loader, per-target clients, and ordinary lifecycle phase satisfy the current requirements without a new framework contract or speculative reload layer.

## Phase 1: Strict route loading and startup correspondence

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/config/RestRoutesProperties.java` — bind only `loomspan-sidecar.rest-routes-location`, defaulting to `file:/sidecar/rest-routes.yaml`, and reject blank locations.
- [x] `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java` — enable `RestRoutesProperties` without changing framework-owned configuration.
- [x] `src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java` — load the configured resource once; strictly deserialize `targets`/`routes`; detect duplicate target/route keys and unknown fields; resolve every string placeholder with field-aware, secret-safe errors; validate names, URLs, positive limits/timeouts, auth/header combinations, bundle references, GET/POST methods, target references, and safe route/base paths; return immutable maps and precompiled route segments without contacting targets.
- [x] `src/main/java/ai/loomspan/sidecar/rest/RestRouteConfiguration.java` — define the immutable package-owned `Target`, `Auth`, `Route`, method/mode enums, and prevalidated path representation used by the loader and handler.
- [x] `src/main/java/ai/loomspan/sidecar/rest/RestRouteCatalogValidator.java` — inject the already-created handler configuration and public `SkillCatalog`, then use `SmartInitializingSingleton` to compare all exact REST names and kinds with routes; fail startup for missing/extra/non-REST mappings with location/key diagnostics.
- [x] `src/test/java/ai/loomspan/sidecar/rest/RestRouteLoaderTest.java` — cover default/override resources, explicit empty maps, shared/multiple targets, strict malformed/duplicate/unknown fields, placeholder success/failure without resolved-secret leakage, semantic target/auth/timeout/size/bundle/method/path failures, and no startup network traffic.
- [x] `src/test/java/ai/loomspan/sidecar/rest/RestRouteStartupIntegrationTest.java` — start real application contexts proving one production handler plus completed catalog without a cycle, valid exact correspondence, and failure before readiness for missing routes, extra/unknown/non-REST routes and targets.
- [x] `src/test/resources/fixtures/rest-routes/empty.yaml` and focused valid/invalid route fixtures — give every context an explicit source while keeping the route file outside skill scan patterns.

### Automated verification

- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=RestRouteLoaderTest,RestRouteStartupIntegrationTest,MountedSkillRegistrationIntegrationTest,LoomspanSidecarApplicationTest' test` — proves strict local loading, startup correspondence/order, scan separation, and the required empty-file case.

### Optional developer checks

- [x] None; startup/configuration behavior is deterministic and automated.

## Phase 2: Fixed binding, authentication, and bounded HTTP transport

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java` — register the single always-present production `RestSkillHandler`; select exact immutable routes; recursively reject non-JSON values before I/O; bind whole path segments and GET scalar queries or POST remaining JSON object; preserve configured scheme/authority/base path; attach fixed Accept and target auth headers; obtain passthrough only from a current `JwtAuthenticationToken`; execute once through the target client; create bounded `SkillException` outcomes; and implement ordinary lower-phase `SmartLifecycle` stop by delegating idempotent, nonwaiting client close after framework completion/cutoff.
- [x] `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java` — construct isolated Apache/`RestClient` pairs with per-target connect/read timeouts, optional Boot SSL bundle, redirects/retries disabled, and an idempotent nonwaiting close operation; expose only the immutable target/client lookup needed by the handler.
- [x] `src/main/java/ai/loomspan/sidecar/rest/BoundedRestResponse.java` — process `RestClient.exchange` responses without default error buffering; do not materialize non-2xx bodies; stream successful bodies through the target byte cap; allow equality; apply JSON/structured-JSON or `text/*` media checks and declared-charset-or-UTF-8 decoding; return empty text for all bodyless 2xx.
- [x] `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java` — use loopback servers and production collaborators to cover encoded Unicode/reserved path and query values, base prefixes with both slash forms, dot/encoded traversal and authority confinement, POST nested/null/empty bodies, early invalid/non-JSON rejection, Accept/static/none/passthrough headers, missing JWT, redirects/no retries, timeouts, connection failures, exact/excess streaming caps, bodyless 2xx, JSON/text charsets, missing/unsupported media types, and bounded body-free diagnostics.
- [x] `src/test/java/ai/loomspan/sidecar/rest/RestTransportTlsIntegrationTest.java` — run deterministic local HTTPS endpoints with test-only CA/server/client identities and PKCS#12 stores generated in-process through Bouncy Castle; prove required client auth succeeds only with the correct bundle and that target SSL settings do not bleed between clients. Runtime generation avoids committed binary key material while keeping the test local and self-contained.

### Automated verification

- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=GenericRestSkillHandlerTest,RestTransportTlsIntegrationTest' test` — proves the handler's binding, security, transport, streaming, media, timeout, redirect, retry, and TLS boundaries without external services.

### Optional developer checks

- [x] None; all transport checks use disposable loopback services and test credentials.

## Phase 3: Production execution, callback identity, restart, and lifecycle

### Changes

- [x] `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java` — remove the imported in-memory handler, configure the production route file and local callback host, retain current inbound/ownership/failure assertions, and prove direct and YAML-planner REST calls forward the identical JWT. The callback independently verifies signature, issuer, audience, lifetime and role mapping; queued-valid identity succeeds while expiry before callback becomes a visible `SKILL_FAILURE` without refresh/recheck.
- [x] `src/test/java/ai/loomspan/sidecar/execution/CustomRoleExecutionIntegrationTest.java` — use the production handler/loopback route while retaining custom inbound role-claim/prefix proof, so no second handler bean remains.
- [x] `src/test/java/ai/loomspan/sidecar/rest/RestRouteRestartIntegrationTest.java` — start against temporary skill/route files, mutate both while running and prove the current catalog/client route is unchanged, then restart to activate valid edits and reject invalid edits before readiness.
- [x] `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java` — close a real application context while an admitted callback is active; prove transport remains usable until framework completion, then is released. Repeat with a short framework cutoff and a blocked callback to prove context close and client cleanup stay bounded without a second drain or early client teardown, while existing admission/dispatch refusal remains intact.
- [x] `src/test/java/ai/loomspan/sidecar/LoomspanSidecarApplicationTest.java`, `src/test/java/ai/loomspan/sidecar/skill/MountedSkillRegistrationIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java`, and any other application-context fixture — point to `fixtures/rest-routes/empty.yaml` (or a test-specific valid route file) so the mandatory file contract is explicit.
- [x] `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java` — retain the production-and-test ban on `ai.loomspan.internal..`/`ai.loomspan.autoconfigure..` and Java `@SkillMethod`; the new tests and implementation must use only public Loomspan types.

### Automated verification

- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=AuthenticatedExecutionApiIntegrationTest,CustomRoleExecutionIntegrationTest,RestRouteRestartIntegrationTest,RestHandlerLifecycleIntegrationTest,ExecutionShutdownIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — proves the production end-to-end callback, queue/expiry outcomes, restart activation, lifecycle ordering, existing gates, and public surface.

### Optional developer checks

- [x] None; no configured provider, external identity service, or production host is required for SC4 proof.

## Phase 4: Documentation and repository-wide verification

### Changes

- [x] `README.md` — replace future-SC4 wording with the unified route-file schema, location override and required empty document; document target sharing, placeholders/environment secrets, GET/POST binding, JSON-only inputs, auth modes and fixed Accept header, Boot SSL-bundle/mTLS configuration, per-target timeouts and response caps, redirects/retries, media/charset rules, bounded failures, restart-only activation, independent callback verification/audience choice, queue-inclusive token lifetime and no refresh; retain SC5 packaging/release disclaimers.
- [x] `src/main/resources/application.yml` — retain the Sidecar route-location default and framework scan separation; add no inline compatibility maps or nonstandard SSL namespace.

### Automated verification

- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress verify` — compiles, runs the complete deterministic suite, architecture checks, and Spring Boot packaging through the repository wrapper.
- [x] `git diff --check` — proves edited code, tests, fixtures, plans, and documentation contain no whitespace errors.

### Optional developer checks

- [ ] Optionally run the documented application against a developer-managed mounted route file and real private service after implementation; this is nonblocking because availability and credentials are explicitly excluded from startup verification and the automated loopback suite proves the contract.

## Test Strategy

Start with a strict loader test that supplies a duplicate target key and expects a resource/key diagnostic; it demonstrates the missing capability before production code exists and protects the single-source routing contract. Keep most parser and binder cases as focused unit/component tests with temporary resources and loopback servers. Use application-context tests only for bean cardinality, catalog ordering/correspondence, readiness failure, restart behavior, lifecycle order, and the authenticated async/nested execution path.

Exercise byte caps with chunked responses so the assertion proves stream-time enforcement rather than Content-Length rejection alone. Use counters for invalid-input/no-retry/no-startup-call assertions, a redirect destination counter for redirect isolation, latches for deterministic read-timeout/lifecycle sequencing, and separate local TLS identities for per-target isolation. All fixtures are sanitized, local, deterministic, and require no provider account or network access.

The detailed test names, fixtures, commands, and exit conditions are defined in `ai/thoughts/plans/2026-09-13-generic-rest-skill-handler-testing.md`.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| One production handler, completed catalog, exact REST correspondence, valid local declarations, no startup calls | `RestRouteLoader`, `RestRouteCatalogValidator`, `GenericRestSkillHandler` | `RestRouteStartupIntegrationTest`, loader call counters |
| Default/override file, strict YAML, duplicates/unknowns, empty maps, target sharing, placeholders | `RestRoutesProperties`, `RestRouteLoader`, immutable `RestRouteConfiguration` | `RestRouteLoaderTest`, mounted/application context fixtures |
| GET/POST binding and JSON-only transport | `GenericRestSkillHandler` recursive value validator/binder | focused handler path/query/body and invalid-value tests |
| Base path, Unicode/reserved encoding, traversal/authority confinement | prevalidated path segments plus component encoders | focused loopback URI matrix with zero-call invalid assertions |
| None/static/passthrough auth and fixed Accept | target auth model and handler header builder | handler header capture plus defensive no-JWT test |
| Effective timeouts, isolated SSL/mTLS, no redirects/retries | `RestTargetClients` | timeout/redirect/counter tests and `RestTransportTlsIntegrationTest` |
| Streaming cap, bodyless success, media/charset rules, bounded error reads | `BoundedRestResponse` | chunked equality/excess, large errors, 204/empty, JSON/text/missing/unsupported media tests |
| Visible bounded `SKILL_FAILURE` without response-body leakage | handler `SkillException` diagnostics and existing failure classifier | production async failure polling assertions |
| JWT async planner callback, identity/roles, queued expiry | production handler plus existing security-context flow | revised `AuthenticatedExecutionApiIntegrationTest` with independently verifying callback |
| Restart-only activation and client lifetime through completion/cutoff | immutable startup state and lower-phase idempotent client lifecycle | `RestRouteRestartIntegrationTest`, `RestHandlerLifecycleIntegrationTest`, existing shutdown checks |
| Wrapper/public-surface checks and accurate documentation | architecture rule, README, standard namespaces | full wrapper `verify`, architecture test, `git diff --check` |

## Risks and Rollback/Recovery

The change is startup-scoped and holds no persistent data. A failed route edit prevents readiness and recovery is to restore the last valid route/skill files and restart. A target outage affects only invocations for that target and surfaces as `SKILL_FAILURE`; it does not mutate the catalog or retry side effects.

If pinned Boot/Apache behavior differs from an assumed timeout, SSL, redirect, or stream-close API, adapt `RestTargetClients`/`BoundedRestResponse` to the verified public client APIs rather than weaken tests or import framework internals. If lifecycle evidence exposes a framework contract gap, stop and resolve it in the framework; do not depend on internal bean names/phases or add a second drain timer.

Rollback is removal of the SC4 production package, property registration, route fixtures, and documentation updates as one unit. Because exactly-one-handler and mandatory-route-file behavior are coupled, do not partially roll back only the validator or client bounds while leaving the handler active.

## References

- `ai/thoughts/tickets/2026-09-13-generic-rest-skill-handler.md`
- `ai/thoughts/research/2026-09-13-generic-rest-skill-handler.md`
- `ai/thoughts/phases/phase-sc4.md`
- `ai/thoughts/design-lens.md`
- `ai/thoughts/beta4-framework-alignment.md`
- `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java`
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java`
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`
- `README.md`
- `pom.xml`
