---
date: 2026-11-02
repository: loomspan-sidecar
branch: main
commit: b136e0ac9f24795fbac9bb0e935aa9d26581b819
ticket: ai/thoughts/tickets/2026-11-02-pr-9-1-http-skill-executor.md
tags: [http, executor, authentication, lifecycle]
---

# HTTP skill executor research

## Research Question

What exists today across request mapping, credentials, HTTP transport, responses,
Framework capture, and shutdown, and which boundaries affect implementing ticket
9.1 against the installed beta.9 snapshot?

Research was conducted in pipeline mode, profile `full`, after the developer
approved the Full 5-Step Pipeline. This document describes source evidence, not
an implementation recommendation or proof that the replacement already works.

## Summary

Sidecar still implements the removed `RestSkillHandler` contract. It resolves a
skill name against a separately loaded route table and a generation-owned client
map. GET forwards all remaining scalar inputs as query parameters; POST sends
remaining input as JSON after removing path fields. There are no explicit pointer
mappings, optional body selector, additional methods, or recoverable HTTP statuses.

The matching Framework checkout exposes `@SkillExecutor` and the immutable
`SkillExecutorInvocation` maps required by the ticket. It treats executor data as
opaque: neither placeholders nor credential references are resolved. Its current
reload APIs require an explicit namespace. Sidecar startup, management and catalog
controllers still call old namespace-less overloads, independently of the removed
REST contracts. These are concrete downstream integration mismatches, not evidence
of missing public Framework functionality.

Apache HttpClient 5.6.1 is already available through Boot dependency management.
Its source shows request-local connection and response timeouts flowing to the
connection/socket boundary. Existing Sidecar uses one client per target per
generation, disables retry/redirect/cookies, and prevents unbounded response cleanup
with an endpoint-discard interceptor. Shared-client concurrency and replacement
shutdown behavior have not yet been proven in Sidecar.

## Repository State

- Checkout: `C:/opendev/code/loomspan-sidecar`, branch `main`, commit above.
- Research began at `2026-11-02T17:57:41-08:00` (America/Los_Angeles).
- `git status --short` was empty before research. No unrelated developer edits
  were present. This research artifact is the only change made by this step.
- No build, tests, service requests, framework install, commit, sync or publication
  were performed. Compile mismatches below are source observations, not executed
  build results.
- Research areas checked: current handler and validation; credential facilities;
  transport and decoding; capture/publication/shutdown; tests and runtime guidance;
  local Framework public contracts; local HTTP client source.

## Current Behavior and Data Flow

### Configuration and invocation

`rest/RestRouteLoader.java:63` parses a separate YAML document with `targets` and
`routes`, using strict duplicate-key detection. `RestRouteConfiguration.java:8`
retains target and route maps. Only GET and POST exist (`:15`). Every target
requires explicit positive timeouts and response size; limits are bounded by
`Integer.MAX_VALUE` (`RestRouteLoader.java:325`). There are no default values.
The target schema includes `ssl-bundle`, but no ordinary fixed-header map.

The loader interpolates Spring placeholders for most strings. Base URLs instead
allow only whole-value `${NAME}` references to allowlisted process environment
variables (`RestRouteLoader.java:173`). It rejects non-HTTP(S), user-info, query,
fragment, unsafe static path segments and invalid path variables (`:250`, `:269`).
The old reserved header set includes Host, Content-Length, Transfer-Encoding,
Accept and Content-Type (`:34`). This is existing implementation evidence, not
an accepted new product requirement.

`GenericRestSkillHandler.java:40` gets generation resources, then looks up the
route by `invocation.skillName()`. It recursively rejects non-JSON input leaves,
builds the URL, and uses Spring RestClient for dispatch. Path values use bare
field names and are removed from a mutable input copy (`:88`). GET serializes
the remaining scalars in sorted name order; POST always serializes the remaining
map (`:73`). There is no bodyless POST, whole-input preservation, nested-pointer
selection, repeated query array, PUT/PATCH/DELETE or operation recoverability.

The path encoder preserves the target base-path prefix. Existing unsafe-value
logic repeatedly decodes percent markers for traversal checks (`:137`); it does
not directly reject every literal slash in a path input. The ticket's complete
path-value rules are therefore not established by retaining that helper.

### Authentication and credential ownership

`RestRouteLoader.java:131` recognizes none, static and caller-passthrough. Static
headers are resolved from the authored routes and Spring Environment. There is no
separate executor credential resolver. The handler applies headers per request,
reads Spring Security's JWT authentication, and checks strict expiry immediately
before `exchange` (`GenericRestSkillHandler.java:53`). Missing authentication or
missing/expired expiry fails. Input cannot choose the authentication mode.

`ExecutionCoordinator.java:177` captures the queued caller authentication,
revalidates its JWT before handoff, installs it in an isolated SecurityContext,
obtains `AdmittedSkillInvocation`, and restores the prior context (`:193`). It
maps the admitted entry generation to a durable snapshot (`:211`). This is the
existing authentication and admission boundary; the executor's context must be
proven through public Framework invocation, not assumed from direct unit calls.

Managed provider credentials already use encrypted, versioned
`EncryptedCredential` records and `ProviderCredentialCipher.java:36` under an
external encryption key. `EffectiveExecutionConfiguration.java:51` decrypts these
to an in-memory candidate map and passes that map to Framework provider
preparation. It does not resolve opaque executor references. The current broad
credential scanner rejects any authored `headers` field outside its special
provider-reference exceptions (`:74`); that conflicts with the new ordinary
target-header contract when full management is connected. These management/storage
changes are part of the downstream 9.2 boundary.

Recipient approval currently means authorized file/database configuration of
targets: there is no separate runtime recipient allowlist or `approved` target
field in the loader/handler. `docs/operations.md:106` and
`docs/remote-recipes.md:38` discuss approved callback recipients. Management
publication authorization remains in `management/ManagementEditingService.java:329`;
the HTTP replacement does not establish a new approval workflow by itself.

### Transport, responses and cleanup

`RestTargetClients.java:58` creates an Apache pooling client per target. Pool
socket settings and default RequestConfig embed the target timeouts. Optional
Boot SSL bundles replace its SSL context. Retry, redirect and cookie management
are explicitly disabled (`:77`). Request settings do not mutate during invocation
today because every target has its own client.

`RestTargetClients.java:80` wraps response entities so early stream/entity close
discards the endpoint before Apache/Spring can drain unread data for connection
reuse. This matters to the bounded-response and prompt-failure requirements.
Closing uses `CloseMode.IMMEDIATE` (`:107`). Existing code does not provide evidence
that naive try-with-resources response cleanup is bounded.

`BoundedRestResponse.java:17` rejects every non-2xx response before reading its
body. It reads success bodies through a byte cap, with one-byte-over-limit
detection. Empty bodies return empty text. Current content handling rejects a
missing Content-Type, allows declared non-UTF-8 JSON, and accepts `+json` subtypes
even outside `application`. Charset decoding uses REPORT for malformed/unmappable
bytes, and returns text without trimming. Error bodies are absent from current
messages; recoverable exceptions are not used. All of these differences matter
when assessing the replacement against the ticket rather than old tests.

`GenericRestSkillHandler.java:170` preserves interrupt state for interrupted
transport causes while avoiding treating socket timeouts as thread interruption.
Generated transport messages omit input and credential values, but preserve
diagnostic causes. Endpoint/config values and causes need assessment under the
ticket's infrastructure credential disclosure requirement.

### Resource generations and durable configuration

`GenerationRestResources.java:17` combines HTTP client lifetime, parsed routes,
generation lookup, durable snapshot IDs and protected retention IDs. Prepare
constructs clients; stage/bind attaches snapshot identity. Retirement removes the
generation and offloads close to a single cleanup executor (`:95`). Shutdown closes
all owned resources and stops cleanup at SmartLifecycle phase zero (`:111`).

`RuntimeConfigurationService.java:97` registers retirement before initial staging.
Database startup restores selected authored content and encrypted credentials,
prepares Framework, stages routes/clients, binds durable identity, publishes and
records outcome. File startup separately loads routes (`:177`). Validation calls
Framework validation then prepares and checks HTTP routes (`:246`). Publication
rechecks candidate credentials, stages resources, records durable state, binds
snapshot identity and publishes (`:316`). Pruning uses resources' protected IDs
(`:524`). This is why removing old client generations also exposes downstream
snapshot/startup management work rather than merely replacing a handler class.

`storage/ManagedConfiguration.java:11`, draft/snapshot repositories, bundle V3,
management editor payloads and UI all persist or expose `restRoutesYaml` separately
from `executionConfigurationYaml`. No development-data reset was performed or
authorized by this research. The ticket explicitly leaves complete durable-format
replacement and restoration to 9.2.

## Key Components and Public Framework Evidence

Framework paths below are relative to `C:/opendev/code/loomspan-framework` and are
source lookups only. Sidecar application/tests remain restricted to supported API.

- `src/main/java/ai/loomspan/api/SkillExecutor.java:15`: annotation on one Spring
  bean method; name, namespace (default `default`) and optional config path.
- `src/main/java/ai/loomspan/api/SkillExecutorInvocation.java:12`: public record
  with input, skillConfig, executorConfig, skillName and executorConfigGenerationId;
  defensive immutable map/list copies, including null values.
- `src/main/java/ai/loomspan/api/SkillReloader.java:9`: namespace-required
  validate/prepare/snapshot/remove; `:58` retirement registration with prompt,
  concurrent callbacks and no shutdown-delivery guarantee.
- `agent-skills/loomspan-docs/references/skill-authoring/executor-skills.md:23`:
  executor leaves reject REST syntax and model/planning/output-schema fields.
  `:33` describes namespace-indexed configuration; `${...}` and credential-like
  fields are literal opaque data. `:47` documents annotation subtree selection.
- `agent-skills/loomspan-docs/references/java-api/skill-reload.md:39`: credential
  maps resolve Framework provider references only; executor data does not consume
  these references. Complete replacement has no startup/prior-value inheritance.
- `agent-skills/loomspan-docs/references/java-api/java-skills.md:106`: recoverable
  exception exact message contract and standard tool feedback; direct invocation
  propagates failures, and step planning still terminates on them.
- `agent-skills/loomspan-docs/references/skill-authoring/execution-limits.md:73`:
  one Framework shutdown budget includes admitted work, observer delivery,
  cutoff and Framework executor cleanup. External side effects are not rolled back.
- `src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:157`:
  corroborating source lookup only: synchronous own-context close admission,
  SmartLifecycle stop waits for roots under one budget, phase Integer.MAX_VALUE
  (`:288`). This is not a type Sidecar may import or substitute in tests.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Production HTTP entry | Removed REST SPI and skill-name routing, `GenericRestSkillHandler.java:27`. |
| HTTP configuration | Separate routes document, old method/auth/TLS schema, `RestRouteLoader.java:30`. |
| HTTP client ownership | Per-generation, per-target client map plus durable IDs, `GenerationRestResources.java:17`. |
| Startup/publication | Old namespace-less `snapshot`, `validate`, `prepare`, `RuntimeConfigurationService.java:103`, `:261`, `:476`; downstream 9.2 ownership. |
| Catalog/API | Namespace-less snapshots in `api/SkillCatalogController.java:20` and `api/ExecutionController.java:44`; downstream catalog alignment must be tracked. |
| Persistence/editor/export | Separate routes member, `storage/ManagedConfiguration.java:11`; management DTO, bundles, UI and retained data remain old contract. |
| Framework boundary | `architecture/SupportedLoomspanApiArchitectureTest.java:22` scans production and tests and forbids both internal/autoconfigure dependencies; `:33` forbids Java SkillMethod declarations. |
| Runtime guidance | README, docs/architecture.md, docs/operations.md, docs/remote-recipes.md, examples and packaged authoring references still describe REST routes and executor SSL bundles. |

## Existing Tests and Fixtures

Tests below are source-reviewed scenarios, not tests executed by this step.

- `rest/GenericRestSkillHandlerTest.java:48`: local HttpServer fixtures verify old
  scalar/path/body behavior. `:139` checks cookie isolation and original bearer
  values. `:160` deterministically mocks `java.net.Socket` construction to inspect
  connect timeout and attempt count without unreliable external blackholes.
  `:211` checks prompt abandonment of oversized/error streams; `:302` covers
  redirect/retry/read timeout and malformed body/media cases; `:370` strict expiry.
  Several expectations intentionally conflict with the new ticket (remaining-input
  mapping, missing Content-Type rejection, non-UTF-8 JSON acceptance/error suppression).
- `rest/RestRouteLoaderTest.java:31`: strict duplicate/unknown/malformed settings,
  safe diagnostics, placeholder and allowlisted URL behavior. These prove old YAML
  schema validation only, not JSON Pointer/operation validation.
- `rest/RestHandlerLifecycleIntegrationTest.java:36` and `:119`: normal close,
  blocked remote work at cutoff, parent versus child context, servlet/non-web,
  and asynchronous application event multicast. Uses public Framework APIs through
  Sidecar application; fixture still seeds old REST manifests/routes.
- `rest/RestGenerationIntegrationTest.java:137`: pending admission, publication,
  original config and retirement; currently coupled to old durable management.
- `rest/RestTransportTlsIntegrationTest.java:59`: local HTTPS certificate generation
  with Bouncy Castle and old per-target SSL/client certificates. This does not prove
  replacement ordinary HTTPS default trust/hostname verification.
- RestRouteStartup/Restart/ValidationIntegrationTest and RestFailureEnvelopeIntegrationTest
  cover full startup, persisted selection and public polling for old contracts.
- `support/SidecarApplicationFixture.java`, fixtures/execution-skills/*.yml and
  fixtures/rest-routes/empty.yaml use the old format. New executor tests cannot
  treat them as already compatible.
- Framework `integration/ExplicitJavaFailureHandlingIntegrationTest.java:30` uses
  a deterministic local OpenAI-compatible server and asserts exact tool feedback.
  Its source also imports Framework internals, so it is scenario evidence only;
  Sidecar cannot copy that test's internal fixtures/bean setup.
- Framework `integration/ExecutorApplicationIntegrationTest.java` and
  `ExecutorGenerationExecutionIntegrationTest.java` illustrate executor declarations,
  captured config and retained-generation scenarios. Framework coverage is not
  Sidecar integration evidence.

## Dependencies and Operational Constraints

`pom.xml:10` uses Sidecar beta.4-SNAPSHOT, Java 21, Boot 4.1.0 and Framework
beta.9-SNAPSHOT. Apache client5 is already declared (`:99`); Jackson 3 YAML/tree
APIs are already used. Maven wrapper is 3.9.11. No new snapshot repository or
Framework build is part of Sidecar development.

The local Boot 4.1.0 dependency POM sets HttpClient to 5.6.1 (`:77`). The installed
source archive is
`C:/Users/mgiacomi/.m2/repository/org/apache/httpcomponents/client5/httpclient5/5.6.1/httpclient5-5.6.1-sources.jar`.
Source inspection found:

- `InternalExecRuntime.java:100` obtains per-context RequestConfig for pool lease;
  `:158` passes its connect timeout to manager.connect; `:225` applies its response
  timeout to the leased endpoint before execution.
- `PoolingHttpClientConnectionManager.java:542` uses the provided connection timeout
  in preference to default connection configuration and passes it to the operator.
- `RequestConfig.java:520` still exposes request-local setConnectTimeout, but marks
  it deprecated in favor of ConnectionConfig. This is actual available behavior,
  not proof of concurrency or TLS-handshake timing under the replacement.

No network/service calls are needed for configuration validation. Existing tests
use loopback servers and temporary SQLite files; model feedback can use a local
protocol fixture rather than paid calls. TLS fixture trust must retain production
certificate/hostname validation. Whole-project compilation currently faces the
removed REST types and old reloader signatures identified above. Exact commands,
blocked checks and any isolated verification strategy belong in the test plan and
implementation evidence; no passing result is claimed here.

## Historical Context

The accepted design notes and current ticket deliberately replace the REST table
and client-certificate architecture. They authorize interim build/startup breakage
between slices, forbid compatibility shims/stubs/disabled tests, and leave complete
management/restoration to 9.2 with final reconciliation in 9.3. The ticket owns
runtime transport, reusable HTTP-specific validation, auth, decoding, resources,
tests and documentation in this slice.

Repository AGENTS.md and parts of the design lens still name the old authorized
RestSkillHandler extension. The ticket explicitly selects the supported annotated
executor instead; this disagreement is stale guidance text, not authorization to
introduce any other SPI. Development replacement/no-shims policy and public API
constraints remain applicable throughout. Product documentation must describe
actual support/limitations without internal ticket histories or future promises.

## Open Questions

These are planning decisions, not research blockers requiring developer input.

- Final selected namespace/config path, target/auth/credential-reference spelling,
  header conflict rules, validation API and timeout/response defaults remain open
  as explicitly delegated by the ticket.
- Credential facilities currently cover provider preparation and old route
  interpolation, not captured opaque executor references. Planning must state the
  runtime credential ownership and precise downstream management limitation.
- Shared-client request-local timeouts have source support, but Sidecar concurrency,
  same-destination reuse, TLS and shutdown need their own executable evidence.
- Whole-project old API dependencies remain broader than the new executor. The
  scope boundary and exact checks possible before downstream replacement need
  explicit tracking; old build errors cannot be called passing integration tests.
- New HTTP validation must be reusable without network effects, but existing
  Framework ValidatedSkill exposes only name/kind, not opaque operation config.
  Current authored SkillDocument/configuration maps are available to Sidecar;
  nothing in the researched public API promises an HTTP validation hook.
