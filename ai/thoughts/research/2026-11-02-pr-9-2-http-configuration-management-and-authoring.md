---
date: 2026-11-02
repository: loomspan-sidecar
branch: main
commit: 736315dc0c67b179b1835f359604ffcf734d998e
ticket: ai/thoughts/tickets/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md
tags: [http-executor, management, persistence, authoring, lifecycle]
---

# HTTP configuration management and authoring research

## Research Question

How does the current checkout load, validate, publish, retain, persist, restore,
inspect and author configuration, and where does its remaining separate-route
contract meet the HTTP executor introduced by PR 9.1?

This is Step 1 of the approved Full 5-Step Pipeline. It describes checked-out
behavior and supporting evidence, not an implementation plan. The ticket's
destructive replacement policy and [design lens](../design-lens.md) apply:
obsolete development contracts receive no compatibility obligation. No data has
been reset, and no production source has been edited during research.

## Decision addendum — 2026-11-02

After this research and the blocked planning drafts, the developer chose HTTP
validation only during executor invocation, including for managed configurations.
The earlier startup/draft HTTP-domain-validation requirement is superseded.
Framework validation and management authorization/storage/credential integrity
remain; HTTP-invalid but Framework-valid settings may start, publish and restore,
then fail on invocation before HTTP dispatch. No new Framework accessor, hook or
snapshot installation is required to resolve the former blocker. Resume Steps 2–3
from the revised ticket/plans. The source findings below remain historical evidence;
references to early domain validation are not current implementation requirements.

## Summary

The HTTP runtime exists under `sidecar/http`, including reusable network-free
target/operation validators, captured encrypted service credentials, one
application-owned Apache client and public-Framework integration/lifecycle tests.
Management, startup and persistence still use `ManagedConfiguration` containing
`restRoutesYaml`, and `RuntimeConfigurationService` and `ExecutionCoordinator`
still import the removed `GenerationRestResources`. There is consequently no
currently composed application path proving the new runtime with management.

The local Framework's public API requires explicit namespaces for reload and
catalog operations. Its execution configuration carries namespace-indexed opaque
executor data without interpreting credential references. Sidecar's existing
provider credential assembly currently decrypts every managed credential into
the Framework credential map, and its authored-content scanner rejects every
`headers` field outside narrowly exempted provider paths. Those paths do not yet
consume the HTTP runtime's encrypted credential attachment helper.

Durable snapshots, drafts, portable bundles, browser state and packaged examples
all retain the route document. Snapshot-to-execution correlation is currently
requested from the removed HTTP-resource component, including for model-only
executions. The public catalog descriptor carries `JAVA` classification but no
executor identity or YAML provenance; Framework diagnostic documentation exposes
those additional facts separately.

## Repository State

- Research timestamp: 2026-11-02T19:07:11-08:00, America/Los_Angeles.
- Branch `main`, commit `736315dc0c67b179b1835f359604ffcf734d998e`.
- Working tree was clean at research entry. The only research output is this file.
- HEAD is `PR 9.1 Execute HTTP skills through the supported SkillExecutor contract`;
  its parent contains ticket creation, following the accepted design notes.
- No builds, tests, live services, framework installation, release, commit, push,
  reset, or deletion were performed in this step. Test descriptions below report
  source coverage, not execution results.

## Current Behavior and Data Flow

### HTTP runtime and final schema

`src/main/java/ai/loomspan/sidecar/http/HttpSkillExecutor.java:20` declares
`@SkillExecutor(name="http", namespace="default", config="http")`. Invocation
uses only `invocation.executorConfig()`, `skillConfig()` and `input()`, then obtains
the captured Spring JWT for caller passthrough. It performs no latest-target
lookup and maintains no generation/client cache.

`http/HttpExecutorConfiguration.java:33` validates a selected HTTP map containing
`targets` and optional `credentials`. Shared targets accept `base-url`, `headers`,
`auth`, `connect-timeout`, `read-timeout` and `max-response-size`. Defaults are
5s, 60s and 1MB. Authentication modes are `none`, `fixed` and
`caller-passthrough`; fixed auth has `header-refs`. Credential records are keyed
by identifier and contain `version` and `ciphertext`, using the existing
`EncryptedCredential`/`ProviderCredentialCipher` contract. The validator rejects
missing references, unsupported settings, malformed shapes, conflicting header
names, transport-owned/authentication header misuse and invalid target URLs.

`http/HttpExecutorConfiguration.java:62` validates operation maps containing
`target`, `method`, `path`, optional `pathParams`, `queryParams`, `body` and
`recoverableStatuses`. It checks targets, GET/body combinations, uppercase
GET/POST/PUT/PATCH/DELETE, placeholder/mapping correspondence, JSON Pointer syntax,
paths, body selection and recoverable statuses without dispatching. Runtime
`HttpRequestMapper` adds actual-input traversal and value checks.

`http/HttpExecutorConfiguration.java:98` supplies
`withCredentials(selectedMap, records)`: an immutable copy with encrypted records
attached. It rejects an already-authored `credentials` source and duplicate
identifiers. Current management has no caller of this helper.

`http/HttpSkillExecutor.java:27` decrypts fixed headers from those captured
encrypted records per invocation. Passthrough requires an authenticated JWT with
a future expiry and forwards its original value. Configuration or mapped input
cannot supply caller identity. Failures use safe messages rather than raw secret
values or transport exception causes.

`http/HttpExecutorClient.java:22` owns one reusable pool. Request-local connect,
pool-acquisition and response timeouts come from the captured target. Automatic
retries, redirects and cookies are disabled. Responses are immediately closed,
including error paths; `HttpExecutorResponse` bounds decoded/decompressed reads
and preserves supported response text. The pool is a phase-zero Spring
`SmartLifecycle`, and close is immediate and idempotent (`:53`).

### Startup modes and validation/publication

`configuration/ConfigurationModeConfiguration.java:20` chooses database mode by
default. Database bootstrap replaces `loomspan.skills.locations` with an empty
resource location. Its bind advisor excludes deployment connections, models,
session and execution-trace persistence (`:66`), but currently does not list
`loomspan.executor-config`. File mode leaves normal Framework startup binding.

`configuration/RuntimeConfigurationService.java:97` registers resource retirement
before activation. Database startup expects an empty Framework catalog, loads the
selected durable snapshot, restores its effective candidate and credentials,
prepares it, stages old REST resources, publishes, records status/prunes history,
then opens execution dispatch. It records safe failed status/validation metadata
on activation failure; it does not select an earlier snapshot as fallback.

`RuntimeConfigurationService.java:176` handles file startup after Framework
startup catalog creation. It reads `RestRoutesProperties`' resource, constructs
and validates route/client resources, records safe file-startup inspection and
binds the Framework generation to that durable startup ID. The current
inspection contains skill name/kind, route and target names, redacted provider
settings, model/session settings and defaults. Exceptions abort startup with a
generic route-oriented message. Its HTTP/domain checks currently refer to
deleted types, rather than the new validator.

`RuntimeConfigurationService.java:246` assembles a candidate, calls Framework
validation, maps its issues, temporarily prepares/closes provider resources,
then stages and validates old routes. No candidate is activated by validation.
The HTTP runtime's validator is not yet part of this flow. Validation results
contain source name, skill name, field path, severity and a safe message.

`RuntimeConfigurationService.java:297` serializes publication with fair
publication/transition locks. It obtains authorization/admission after locking,
rejects a stale base, assembles fresh credentials and checks `Candidate.sameContent`
against the previously validated candidate. `publishCandidate` prepares resources,
commits a pending durable selection, binds its generation, then calls Framework
publication (`:316`). Pre-publication failure discards candidate resources and
reverts the durable pointer. A failed revert freezes mutations with a fault.
Once activation succeeds it clears the exact draft and lease, records published
status and prunes history; cleanup/status errors are reported as activated faults,
not false rollback claims (`:381`).

### Credential assembly and protection

`configuration/EffectiveExecutionConfiguration.java:49` parses authored execution
YAML, rejects direct provider secrets, decrypts every supplied managed record into
one provider value map, retains encrypted records, and checks explicitly required
credential identifiers. It currently returns the exact authored YAML rather than
injecting executor credentials. Its `sameContent` compares YAML, plaintext map and
identifier/version inventory. Restoration uses the snapshot's effective YAML and
retained credential versions (`:103`); a matching retained rollback/import source
may reuse its exact captured configuration (`RuntimeConfigurationService.java:479`).

`EffectiveExecutionConfiguration.java:76` scans every YAML document and duplicate
mapping before ordinary save so syntactically invalid drafts cannot hide a secret.
The scanner rejects `api-key`, `headers`, `credentials-json`, `credentials-uri`
and `credentials-ref` almost everywhere. Exemptions cover provider alias names
and provider `header-refs` header names. A legitimate HTTP target's ordinary
`headers` is currently rejected. `rejectAuthoredValues` additionally prohibits
provider direct credentials and requires explicit managed Gemini JSON references.

`configuration/ProviderCredentialCipher.java:20` obtains a base64 32-byte external
key. AES-GCM uses a fresh nonce and identifier/version as authenticated metadata.
Missing/wrong keys fail with safe errors. `storage/StorageConfiguration.java:29`
constructs this cipher from `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` in both modes.
Credential replacement/removal already uses the draft's exact account, lease,
revision and base checks; save bodies contain references rather than secret
values. `ConfigurationDraftStore.java:136` persists encrypted replacements and
increments revision. No separate HTTP secret store exists.

### Management authorization, imports and rollback

`management/ManagementEditingController.java:18` defines the shared Save payload:
capability IDs, draft ID/revision/base, `skillDocuments`, `restRoutesYaml` and
`executionConfigurationYaml`. Content validation requires all three content
parts and invokes the authored credential scanner (`:111`).

`management/ManagementEditingService.java:221` captures an owned exact draft,
validates outside the editing transition, then reacquires it and rechecks
account/credential/session/lease/revision/base before recording validation. Publish
rechecks the lease and Publish token scope and exact validation record under the
runtime publication gate (`:247`). `ownLease` verifies origin, account, session ID,
generation and expiry (`:302`). `checkEditor`, `checkPublish` and `checkAccount`
consult current account state and token validity (`:320`). Browser and PAT paths
share these services; `ManagementSecurityConfiguration` owns URL authorization,
CSRF and ambiguous credential protections.

`management/ManagementConfigurationImportService.java:28` requires database mode,
parses/stages a bounded ZIP and reviews it using runtime validation. Load accepts
explicit `included` versus `configuration-only` credential mode; included encrypted
records are authenticated before loading through the editing service (`:36`).
Imports populate a draft rather than activate a publication.
`ManagementConfigurationRollbackService.java:23` likewise reviews retained content
through runtime validation and loads it through `editing.loadRetained`. All
configuration mutation paths ultimately call `runtime.requireDatabase()`.

No standalone recipient approval ledger or separate browser approval capability
was found in current production source. The new runtime recognizes
`caller-passthrough` but has no `approved` target field. Current HTTP documentation
states that the authorized configuration publisher approves targets; the shared
Publish authorization path is the current approval owner. This is separate from
the destination's JWT audience/trust/authorization responsibilities.

### Durable storage, bundle format and reset-relevant facts

`storage/ManagedConfiguration.java:11` is the exact authored representation with
skill documents, `restRoutesYaml`, execution YAML and required credential IDs.
Snapshot SQL persists `rest_routes_yaml` and both authored/effective execution
YAML (`src/main/resources/db/migration/V1__configuration_snapshots.sql:1`). Draft
SQL separately persists `rest_routes_yaml`
(`db/migration/V3__management_drafts.sql:1`). Their repository/store constructors,
header records and SQL reads/writes all use that field. Snapshot initialization
uses empty targets/routes plus `loomspan: {}` (`ConfigurationSnapshotStore.java:15`).

The same SQLite database contains snapshots/selection, encrypted snapshot
credentials, file-startup inspection, management accounts/credentials, drafts,
draft credential requirements and PATs. The default path is
`/sidecar/data/sidecar.db` (`config/SidecarStorageProperties.java:8`). SQLite uses
WAL, foreign keys, FULL synchronous mode and a busy timeout. Existing files must
have Flyway history; startup runs migrations and checksum validation
(`storage/StorageConfiguration.java:107`, `:170`). Changing existing migrations
therefore affects initialized databases, including identity/token/draft content
held beside snapshots. The concrete reset decision belongs to planning; no reset
or data deletion was performed here.

`bundle/ConfigurationBundleV3.java:39` implements format-3 ZIP with strict
inventory, CRC/digest/size/entry bounds and producer/source metadata. Write emits
`rest.json` containing `restRoutesYaml`, `execution.json` containing authored
execution YAML and credential IDs, skill YAML files and optional authenticated
ciphertext (`:82`). Read requires that route payload (`:201`). Encryption keys
and plaintext credentials are not bundle entries. This is a serialized contract
independent of the database schema.

### Execution correlation, retention and shutdown

`execution/ExecutionCoordinator.java:61` currently injects removed
`GenerationRestResources` and retains `resources::snapshotId`. After current
credential trust/expiry checks, it obtains a supported Framework admitted
invocation, invokes a race-test hook and resolves the admitted generation's durable
snapshot ID (`:177`, `:208`). Mapping failure releases the admitted invocation.
The ID is retained in the execution result even after completion (`:230`).
This correlation exists for every execution, including model-only skills.

History protection currently comes from `resources.protectedIds()` plus the
active snapshot (`RuntimeConfigurationService.java:523`), and retirement calls
`resources.retire`. These responsibilities remain references to removed code;
there is no replacement live source implementation hidden in the HTTP package.
Framework retained work captures its own skill/executor configuration; the new
HTTP executor uses those invocation maps directly.

On its own context's close event, the execution coordinator synchronously closes
admission, changes readiness and drains/discards queued tasks; it explicitly
rejects asynchronous event handling (`ExecutionCoordinator.java:255`, `:273`).
Its PreDestroy shuts down the expiration sweeper and worker executor (`:277`).
The HTTP client's lower lifecycle phase is separately responsible for keeping
the client alive until Framework work finishes or reaches its deadline.

### Browser and remote authoring surfaces

`src/main/resources/static/management/assets/editor.js:7` keeps separate skill,
route and execution state. Save sends `restRoutesYaml` (`:33`), saved-draft preview
includes a REST section (`:96`), file/base loading copies routes (`:166`) and a
dedicated route editor updates state (`:217`). `console.js:133` renders a separate
REST routes/targets inspection card. Browser history/import/editor tests exercise
these same management endpoints rather than a second publication service.

The Python packaged client is largely an endpoint/payload transport; its save and
reconcile tests still send `restRoutesYaml`
(`agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py:146`).
The packaged skill's main workflow explicitly authors three content parts
(`agent-skills/loomspan-sidecar-authoring/SKILL.md:40`). Its canonical remote
authoring example has a separate route file; `scripts/test_agent_skills.py:99`
asserts that old file's contents.

## Key Components and Affected Areas

Paths without a prefix below are relative to `src/main/java/ai/loomspan/sidecar/`.

| Area | Current behavior and evidence |
| --- | --- |
| Startup/publication | `configuration/RuntimeConfigurationService.java:97` — selected snapshot authority, prepared publication, REST resource staging, safe failures, draft cleanup and pruning. |
| File/database boundary | `configuration/ConfigurationModeConfiguration.java:20` — mode capture, empty DB startup declarations and binding exclusions. |
| Credential composition | `configuration/EffectiveExecutionConfiguration.java:49` — reference-only provider candidate, credential inventory, exact restoration and scanner. |
| HTTP validation/runtime | `http/HttpExecutorConfiguration.java:33`, `http/HttpSkillExecutor.java:20` — reusable validators and captured invocation execution. |
| Client lifecycle | `http/HttpExecutorClient.java:22` — one pool, request-local limits and phase-zero cleanup. |
| Durable representation | `storage/ManagedConfiguration.java:11`, `ConfigurationSnapshotRepository.java:27`, `ConfigurationDraftStore.java:52` — route-bearing authored representation and storage. |
| Management authorization | `management/ManagementEditingService.java:221` — capture/recheck validation, publication admission, lease and live permission checks. |
| Portable payload | `bundle/ConfigurationBundleV3.java:82` — route-bearing ZIP write/read contract and optional encrypted credential inventory. |
| Durable execution identity | `execution/ExecutionCoordinator.java:208` — captured generation lookup through removed client-resource owner. |
| Browser and guidance | `src/main/resources/static/management/assets/editor.js:33`, `console.js:133`, packaged authoring `SKILL.md:40` — three-part form, route inspection and examples. |
| Public catalog entry | `api/SkillCatalogController.java:20` — forwards public descriptor metadata using an obsolete namespace-less snapshot call. |

## Existing Tests and Fixtures

Tests were located and inspected; none were executed in this research step.

- `src/test/java/ai/loomspan/sidecar/http/HttpExecutorConfigurationTest.java:13`
  covers target defaults, methods, malformed durations/sizes/URLs/pointers,
  operation shapes/statuses and header/auth ownership without network access.
  `HttpRequestMapperTest` covers pointer traversal and destination-specific input.
- `http/HttpSkillExecutorTest`, `HttpExecutorTlsIntegrationTest` and
  `HttpExecutorIntegrationTest.java:14` exercise actual loopback wire behavior,
  ordinary trust/hostname verification, fixed and caller credentials, retained
  capture and model recoverable feedback. `HttpFrameworkFixture.java:48` uses the
  installed Framework's `prepare("default", ...)` with real Sidecar HTTP beans.
- `http/HttpExecutorLifecycleIntegrationTest.java:16` covers admitted completion
  before client close and deadline cutoff without another read-timeout drain,
  both with synchronous/asynchronous multicasting and child-context close. This
  fixture excludes the full Sidecar queue/management graph.
- `configuration/RuntimeConfigurationIntegrationTest.java:39` covers empty DB
  activation, complete model settings, missing references, dispatch gating,
  restart, stale validation/base, preparation/publication failures, warnings,
  bundle transfer and protected history. Several scenarios still assert old
  route resources (notably `:301`, `:375`, `:529`).
- `configuration/RuntimeConfigurationRecoveryIntegrationTest.java:32` covers
  pending/reverted/faulted selection restart, stopped backups, failed selected
  status writes and safe startup errors.
- `configuration/ConfigurationModeIntegrationTest.java:18` covers database
  rejection of deployment-provider inheritance, file restart semantics and
  identity/trust. `:124` still uses separate routes. `FileModeManagementHttpIntegrationTest.java:45`
  covers mutation/import/rollback restrictions and inspection-only pages.
- `configuration/ManagementConfigurationHttpIntegrationTest.java:70` covers draft
  import/rollback, rechecks after publication/export locks, viewer limits,
  failed-publication preservation and ownership races.
- `configuration/CredentialPortabilityIntegrationTest.java:29`,
  `CredentialEnvironmentIntegrationTest.java:15`, `EffectiveExecutionConfigurationTest.java:11`
  and `ProviderCredentialCipherTest.java:12` cover explicit credential inventory,
  same/wrong keys, reference-only saving, hidden secrets in invalid YAML and
  authenticated version/identifier encryption. They are currently provider-shaped.
- `execution/ExecutionConfigurationCorrelationIntegrationTest.java:35`, `:143`
  and `:191` cover captured old/new provider settings, model-only durable IDs and
  publication between handoff and durable mapping. Old `rest/RestGenerationIntegrationTest.java:137`
  covers retained HTTP admission/publication but imports deleted resources;
  `RestRouteStartup/Restart/ValidationIntegrationTest` retain old YAML contracts.
- `management/ManagementEditingServiceTest`, `ManagementEditingHttpIntegrationTest`,
  `ManagementPersonalTokenHttpIntegrationTest`, `ManagementDraftRestartIntegrationTest`
  cover leases, revision, ownership, reconciliation, token scopes/CSRF and draft
  persistence. Browser editor/history/import tests use Playwright Chromium with
  a locally started random-port application and temporary SQLite databases.
- `storage/ConfigurationSnapshotRepositoryTest`, `ConfigurationSnapshotStoreTest`,
  `ConfigurationDraftStoreTest`, `bundle/ConfigurationBundleV3Test` verify exact
  persistence, selection/retention/integrity and bundle structure/security.
- `architecture/SupportedLoomspanApiArchitectureTest.java:23` forbids internal and
  autoconfigure dependencies in application and tests and Java `@SkillMethod`
  declarations. The HTTP-specific architecture test enforces the same boundary
  on its isolated subset.
- Portable Python checks live in `scripts/test_agent_skills.py` and packaged
  `client/test_sidecar_authoring.py`; they exercise self-contained guidance,
  metadata, examples, payload transport and fake local HTTP boundaries.

No current test establishes composed new HTTP startup, management validation,
published-target credential partitioning, restored HTTP publications or browser
round-trips, because those production paths still contain the removed route
contract. Existing tests are source scenarios, not evidence of completed new
behavior.

## Dependencies and Operational Constraints

- `pom.xml:16` targets Java 21, Boot 4.1.0 and locally installed Framework
  `1.0.0-beta.9-SNAPSHOT`; Apache HTTP client, SQLite/Flyway, ArchUnit and
  Playwright are existing dependencies. No snapshot repository is configured.
- Matching local public source is under
  `C:/opendev/code/loomspan-framework/src/main/java/ai/loomspan/api/`.
  `SkillReloader.java:10` requires a namespace for validate/prepare/snapshot.
  Existing Sidecar namespace-less calls in runtime/configuration and API
  controllers are absent from that supported contract.
- `SkillReloader.prepare(namespace, documents, configuration, credentialValues)`
  copies an explicit provider credential map with no environment fallback.
  Matching documentation states missing, blank and unused provider entries fail
  (`agent-skills/loomspan-docs/references/java-api/skill-reload.md`, Complete
  configuration and credentials). Executor content is opaque: its `${...}` and
  `api-key-ref` values do not resolve or consume Framework credentials.
- Publishable roots are connections/models/session/executor-config and trace
  persistence. The executor map is namespace-indexed; complete replacement may
  supply only its own namespace, and omission never inherits old/startup content.
  Startup/preparation check selected map structure without invoking application
  executor/domain validation.
- `PreparedSkillUpdate.java:8` exposes candidate generation and public catalog;
  no public accessor returns the candidate's executor map or authored operation
  data. `SkillDescriptor.java:10` exposes name, description, kind and schemas,
  not executor identity/YAML provenance. The matching executor documentation
  classifies callable executor-backed leaves as `JAVA` and describes richer
  diagnostic catalogs with `declaration: YAML`, source/yaml and executor name.
- `SkillReloader.onGenerationRetired` notifies superseded generations after
  retained/physical work releases ownership; callbacks may be concurrent and
  omitted on shutdown. They carry no durable ID. Public invocation exposes the
  captured executor configuration generation separately from entry generation.
- `scripts/verify-http-executor.ps1:1` currently generates an isolated Maven POM
  for HTTP runtime plus cipher/encrypted-record sources and all HTTP tests. It
  derives dependencies from the real POM, cleans isolated output and does not
  compile Framework sources or inject stubs. It is explicitly not a whole-app
  build. Its presence is historical development infrastructure, not management
  verification evidence.
- Full Maven compilation currently has statically identifiable removed-type and
  namespace-less call boundaries; research did not execute the compiler. HTTP
  fixture tests use loopback only. Browser tests need installed Playwright
  Chromium. No paid model provider, deployed service or release is needed for
  their local fixtures. Live deployment recipes are not ordinary research checks.
- Inbound Boot TLS and Caddy transport settings are process/deployment owned.
  The new client supports ordinary HTTPS trust/hostname validation and no client
  certificate configuration. Existing standard/hardened route files and related
  recipes still describe the previous outbound bundle contract.

## Historical Context and Documentation Disagreements

- The accepted design notes settle skill-owned HTTP operations, shared executor
  targets, complete managed publication, publisher-owned recipient approval and
  client-independent durable identity. Their final “Current implementation
  evidence” links point to now-deleted REST classes; those links are historical,
  not live source evidence.
- HEAD removed the seven old runtime types but retained downstream management,
  correlation and authoring work. PR 9.1 verification records those blockers and
  assigns runtime configuration/correlation to 9.2 and remaining API/catalog
  namespace alignment/final composed-suite reconciliation to 9.3. This research
  independently located those current source references; prior claimed test
  counts are not reused as this ticket's verification.
- `docs/http-executor.md:7` accurately states the current activation limitation.
  It and the packaged HTTP reference document the actual PR 9.1 schema. Other
  consumers still teach separate routes: packaged main workflow/client tests,
  remote authoring/quickstart/standard/hardened examples, remote recipes and parts
  of `docs/operations.md`. `scripts/test_agent_skills.py:103` enforces an old example.
- Root `AGENTS.md`'s Framework boundary still authorizes `RestSkillHandler`, and
  `ai/thoughts/design-lens.md` still mentions the REST handler/complete REST-route
  publication. The ticket explicitly identifies this wording for reconciliation;
  its accepted supported replacement is the annotated executor, with no broader
  Framework extension permission.

## Open Questions for Planning

These are integration questions to resolve against the located contracts, not
developer escalations or recommendations:

1. Resolved by the developer: HTTP domain validation is invocation-only, using
   captured maps already supplied to the executor. Do not obtain those maps for
   startup/draft validation or add a public accessor for this purpose.
2. How will the existing provider credential inventory distinguish HTTP-only and
   shared credential uses while keeping executor ciphertext captured, ordinary
   headers authorable, direct secrets blocked, and inspections/exports safe?
3. What is the exact database/bundle format replacement and development-reset
   impact, accounting for accounts/PATs/drafts sharing the same SQLite file and
   Flyway validating existing migration checksums?
4. Where does durable generation correlation/history protection live after
   removal of its old client-resource owner, and how are handoff/publication and
   retirement races covered for HTTP and model-only work?
5. Which remaining composed API/fixture namespace blockers are part of this
   implementation versus the explicitly accepted PR 9.3 reconciliation, and what
   executable assurance remains available if any intermediate blocker persists?

Research requires no new product decision. Planning owns the concrete choices
and acceptance-criterion/test mapping.
