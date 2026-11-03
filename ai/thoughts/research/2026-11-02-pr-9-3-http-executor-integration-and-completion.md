---
date: 2026-11-02
repository: loomspan-sidecar
branch: main
commit: 27108f1b46ad037bfc54054897282c67d82c3626
ticket: ai/thoughts/tickets/2026-11-02-pr-9-3-http-executor-integration-and-completion.md
tags: [http-executor, integration, clients, lifecycle, security]
---

# HTTP executor integration and completion research

## Research Question

What is already implemented and tested by 9.1/9.2, which maintained consumers
still use removed contracts, and what executable boundaries and deferred checks
remain for the assembled HTTP executor architecture?

This is Step 1 in pipeline mode, profile `full`, approved by the developer after
Step 0. Research describes the checkout; it does not establish a passing build or
replace implementation planning.

**Development policy:** superseded contracts are replaced directly, without
compatibility shims, adapters, parallel legacy APIs or migration machinery.
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
No operational data, credentials or maintained reference pack was changed.

## Summary

Sidecar already contains the annotated HTTP executor, captured target/credential
handling, invocation-only HTTP validation, complete managed publication and
restoration, durable-generation correlation, and Framework-aligned client shutdown.
Its tests directly exercise supported public Framework APIs, including standard
model-tool recoverable feedback. Those tests are current source evidence, not
newly executed evidence from this research step.

The neighboring suite remains substantially on the removed architecture. Its
equipment generator emits nine `rest: true` leaves and a separate route catalog;
its suite assembler and Compose mounts consume those files. SDK foundation,
Local, shutdown, remote and outbound-only generators also consume route files.
SDK preflight and artifact checks require beta.8, while the business lane exports
Sidecar beta.3 and requires a historical Framework source revision. These are
active consumers, not merely historical documentation references.

The two precisely recorded remaining Sidecar build blockers are a beta.8-specific
release-preparation check and a release-tag test fixture that copies a snapshot
dependency and then expects tagging to succeed. Publication, dependency pinning
to the eventual release, hosted CI and actual tags remain separate release gates.

## Repository State

Observed 2026-11-02, approximately 21:39–21:45 America/Los_Angeles:

| Repository | Branch / commit | Initial state |
| --- | --- | --- |
| `C:/opendev/code/loomspan-sidecar` | `main`, `27108f1b46ad037bfc54054897282c67d82c3626` | Clean |
| `C:/opendev/code/loomspan-sidecar-test-suite` | `main`, `1a79ba7c5ad106665bddfec363d5737a2df8ebe4` | Clean |

Paths below are relative to Sidecar unless prefixed `suite:` or `framework:`.
Those prefixes mean the sibling test-suite and Framework checkouts respectively.
Only this research artifact is written by Step 1. No implementation edit, test,
Framework build, service startup, commit, push, tag or release was performed.

Read applicable root `AGENTS.md` files, the Sidecar design lens, controlling
ticket and HTTP design notes, and suite README/project agreement/foundation/runtime
guidance. Suite AGENTS requires preserving reference assets and operational data;
the current ticket supersedes historical beta.3/beta.8 and provenance assumptions.

## Current Behavior and Data Flow

### Runtime invocation and transport

`http/HttpSkillExecutor.java:23` declares
`@SkillExecutor(name="http", namespace="default", config="http")`. Invocation
consumes only the captured `SkillExecutorInvocation` maps. It validates settings,
validates the operation, constructs the mapped request, and creates a fresh header
map before dispatch. Fixed authentication decrypts the retained credential version;
passthrough reads the captured Spring `JwtAuthenticationToken`, requires authenticated
state and a future expiry, and forwards its original token bytes. No token minting
or refresh occurs (`HttpSkillExecutor.java:25`).

`http/HttpExecutorConfiguration.java:27` validates named targets and retained
credential records. Target fields are `base-url`, `headers`, `auth`,
`connect-timeout`, `read-timeout`, and `max-response-size`. Defaults in source are
5s, 60s and 1MB. Operation fields at line 62 are `target`, `method`, `path`,
`pathParams`, `queryParams`, `body`, and `recoverableStatuses`. Supported methods
are GET/POST/PUT/PATCH/DELETE; GET rejects configured bodies. The configured body
is whole input or a selected JSON Pointer field; omission sends no entity.
Only listed 4xx statuses other than 401/403 can be recoverable. The public mapper
performs input-dependent pointer, scalar and destination checks before dispatch.

`http/HttpExecutorClient.java:24` owns one Apache pool and disables retries,
redirect handling and cookie management. Request-local connect/pool-wait/read
limits are built from the captured target (`:29`). Headers and JSON entities
belong to each request. Response failure closes immediately, avoiding graceful
draining of an unbounded remainder (`:45`). Phase-zero lifecycle cleanup and
`@PreDestroy` close the same pool idempotently (`:59`).

`http/HttpExecutorResponse.java:13` bounds successful and unsuccessful response
reads. Accepted text is preserved rather than parsed/reserialized. Nonempty JSON
and `+json` must be UTF-8; `text/*` honors its charset; missing type defaults to
UTF-8; unsupported types, charsets and malformed bytes fail. Error messages are
`HTTP <numeric-status>` plus newline and the complete decoded body when present.
For example the implemented contract is `HTTP 503\n{"error":...}`, with no old
target/content-type wrapper. Recoverable/terminal exception selection occurs only
after bounded decoding succeeds.

### Startup, publication, restoration and identity

`configuration/RuntimeConfigurationService.java:90` selects file startup or the
selected database publication. Database activation restores complete effective
execution YAML and retained encrypted records, prepares through public
`SkillReloader`, stages generation-to-snapshot correlation, publishes and opens
execution admission. File startup uses the Framework-loaded default catalog and
records a file-startup identity (`:165`).

Validation assembles a candidate and invokes public `validate("default", ...)`
and detached preparation without invoking the HTTP executor (`:225`). Publication
rechecks the candidate/base after acquiring publication locks (`:269`), prepares,
stores the durable candidate, stages correlation before Framework publication,
and preserves/reverts selection on failure (`:291`). Management authorization,
lease, revision, permission and encrypted-storage checks are separate from HTTP
operational validation.

`configuration/EffectiveExecutionConfiguration.java:77` attaches retained encrypted
credentials only when an HTTP map exists; its authoring checks protect credential
material, not target semantics. Restoration is at line 138. Missing targets or
HTTP credential references are not repaired from prior or startup values.
`ConfigurationGenerationRegistry.java:10` holds only an opaque Framework generation
ID to durable UUID map. It protects historical snapshots and retires correlations;
it neither owns HTTP clients nor equates generation IDs with durable IDs.

`execution/ExecutionCoordinator.java:177` revalidates queued JWT trust before
Framework handoff, installs the original captured authentication, captures the
admitted generation and resolves its durable snapshot identity. Existing queue,
ownership and trust tests remain separate from executor tests. The full-Sidecar
shutdown test proves the queued entry is discarded, new admission is refused,
the pool stays open for admitted work and closes after completion/cutoff.

Catalog APIs now explicitly select `default`.
`api/SkillCatalogController.java:21` exposes Framework descriptors and resolves
unqualified route arguments to `default-<name>` on lookup. Descriptor names remain
qualified; executor leaves are `SkillKind.JAVA`, and the executor registration
itself is not a callable entry. This differs from SDK driver's old expectations.

### Equipment-service client

`suite:scripts/suites/equipment_service/author_workflow.py:359` emits eight lookup
leaves into `config/rest-skills/equipment-service`, plus `createServiceRequest`,
all with `rest: true`. It emits target and name-keyed route fragments at line 368.
Checked-in YAML has the same obsolete shape.

`suite:config/routes/equipment-service.yaml:1` selects a caller-passthrough target
at `http://python:8080`, 5s connect, 240s read and 1MB response. All nine routes are
POST `/skills/<name>` with no explicit body contract in that old file.
`suite:scripts/suite_config.py:8` checks identities across model and REST leaf
directories, merges named route fragments, validates route/target references and
writes `.runtime/suite-routes.yaml`. `environment.compose():35` always invokes
that assembler before constructing Compose commands.

`suite:config/sidecar.yaml:4` loads the separate route file and lines 79–80 load
model/REST leaves. `suite:compose.yaml:44` builds a beta.3 export, mounts those
trees/catalog and uses retained `sidecar-data`; `compose.runtime.yaml` repeats the
old build path. Normal offline overlays blank provider credentials.

The receiver business contract is independent of those declarations:

- `suite:apps/python/suites/equipment_service/skill_routes.py:9` authenticates the
  callback and validates the eight lookup operations as closed flat
  `{caseId, assetId}` objects.
- The same handler validates `createServiceRequest` as a closed `{approval: ...}`
  envelope, then calls business authorization (`:11`). The nested approval schema
  is in `contracts.py:15`. The public service-request API wraps approval at
  `api.py:20`.
- `business.py:78` checks explicit approval, role/record authority, immutable
  assessment/quote correspondence, expiry and idempotency. Assessment replay
  does not create service commitments.

The generator owns both model skill YAML and these leaves. Maintained reference
checks hash only the model-skill directory and independent business source JSON
(`suite:scripts/replay_reference.py:26`); leaf transport edits alone do not alter
those hashes. This observation does not establish that the new Framework request
scaffolding still matches every frozen request. A material request-contract
change is an explicit escalation boundary in the ticket.

### SDK clients and transport recipes

`suite:scripts/run_sdk.py:168` checks packaged Framework beta.8 and line 219 checks
the POM against that version. `write_config():242` emits `sdkFailure`/`sdkEffect`
REST leaves, a separate routes file and a `rest-routes` setting. Shutdown
composition at line 440 generates nested planner leaves/routes the same way.
`build_checkout():707` runs one root `clean verify`, SDK reactor `clean install`,
then driver and reference-app packages. It does not rebuild Framework.

`suite:sdk/driver/.../BlockingClientDriver.java:28` expects unqualified catalog
descriptor names and at line 39 expects the old REST-specific 503 message.
The maintained SDK should receive the actual current descriptor and failure
contract rather than a Sidecar client-specific response.

`suite:scripts/sdk_lane/local.py:130` generates model and callback leaves; line
157 adds `rest: true`, line 162 writes routes, and line 206 selects the old file.
Owned callbacks are POST without a configured body. The receiver
`suite:sdk/reference-app/.../CallbackController.java:42` takes authenticated JWT
identity without a request body, checks method and record authorization and
compares the exact original token through its fixture. Arrival/body/effect
counters support expiry and no-replay evidence.

`suite:scripts/sdk_lane/remote.py:45` overlays shipped remote examples on Local
configuration and reads removed `examples/remote/<recipe>/rest-routes.yaml` at
line 60. Current Sidecar examples already put targets inside execution YAML.
Remote variants cover Standard HTTPS server/hostname/expiry rejection, Hardened
Caddy peer rejection/isolation, submission/callback response loss, shared-group
rotation and outbound-only operation. `remote.py:146` composes temporary services
and TLS material; the Caddy service owns client identity in Hardened.
`outbound.py:10` currently removes callbacks by writing empty old targets/routes.

The runner's `local`, `standard` and `hardened` selectors execute the existing
bounded inventory, not individual scenario selectors (`run_sdk.py:948`). They
use unique project names and temporary configs/tmpfs data. Cleanup targets those
projects. They do not mount retained equipment databases or credentials.

## Key Components and Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Runtime HTTP executor | `src/main/java/ai/loomspan/sidecar/http/*`; annotated bean, captured maps and one application pool |
| Managed configuration | `RuntimeConfigurationService`, `EffectiveExecutionConfiguration`, `ConfigurationGenerationRegistry`; no HTTP preflight |
| Equipment declarations/generator | Suite `config/rest-skills`, `config/routes`, `author_workflow.py:359` actively emit/use removed contracts |
| Suite configuration/Compose | `suite_config.py`, `environment.py`, `config/sidecar.yaml`, `compose*.yaml`; separate catalog and old source export |
| SDK generators/consumers | `run_sdk.py`, `sdk_lane/{local,remote,outbound}.py`, Java driver; old declarations, beta.8 and error/catalog assertions |
| Runtime source/build packaging | `suite:prepare.py:10`, `record_build.py:13`, `package_runtime.py:23`, `install_runtime.py:27`; beta.3 paths, historical provenance and hard-coded beta.8 extraction |
| Release tooling | `scripts/prepare-release.py:12`, POM release profile, release/CI workflows and `ReleasePreparationTest`; beta.8-only checks |
| Current consumer guidance | Suite runtime/foundation/SDK/suite docs describe old layouts and version/build assumptions |

## Existing Tests and Fixtures

Source-reviewed Sidecar coverage (not executed by Step 1):

| Requirement boundary | Existing executable evidence |
| --- | --- |
| Direct public invocation, retained settings/credentials, schema/RBAC | `http/HttpExecutorIntegrationTest.java:14`, public `SkillTemplate`, `SkillReloader`, `SkillInvocationHandoff` |
| Standard tool feedback and terminal propagation | Same test `:41`, local OpenAI-compatible fixture, exact recoverable body, caller token, observer classification, terminal status matrix |
| Mapping and protocol/content limits | `HttpRequestMapperTest`, `HttpExecutorConfigurationTest`, `HttpSkillExecutorTest` cover all methods/body selections, pointers/URI safety, status/media matrices, decompressed/exact limits and response abandonment |
| Authentication and isolation | `HttpSkillExecutorTest:27` fixed captured records; `:47` expiry and concurrent caller/cookie isolation |
| No replay/redirect | `HttpSkillExecutorTest:111` dropped response/redirect attempts; `:120` connect timeout; `:100` concurrent reads |
| HTTPS | `HttpExecutorTlsIntegrationTest:24` generates certs and checks trusted success, untrusted CA and hostname failure |
| File/DB invocation-only validation | `configuration/HttpConfigurationIntegrationTest:26` publishes/restores HTTP-invalid fixtures without requests, tests import/rollback review; `:121` file startup; `:168` missing map/credential/target behavior |
| Captured publication/restart | Same test `:73`, old/new paths and encrypted versions, failed publication and restored durable identity |
| Full Sidecar shutdown | `HttpConfigurationShutdownIntegrationTest:28`, queued discard, no new admission, completed active work or Framework cutoff without another 30s read drain |
| Lower-level lifecycle | `HttpExecutorLifecycleIntegrationTest`, synchronous/asynchronous event paths and cutoff |
| Public boundary | `architecture/SupportedLoomspanApiArchitectureTest:23` scans production and tests for internal/autoconfigure dependencies; `:33` forbids Java `@SkillMethod` |

Suite tests include `tests/test_suites.py:71` old route assembly/collision fixtures,
`test_sdk_runner.py` generated config, guarded artifact version, no-provider
environment, shutdown fixtures, callback expiry and no-replay tests, and
`test_sdk_remote.py` remote configuration/security oracles. They currently encode
the old shape and are part of the active consumer work.

`suite:fixtures/reference` contains the accepted four-case, sixty-response
equipment replay pack. `replay_reference.py` verifies checksums, exact business
sources, model-skill contracts, six Sol/two Luna assignments and reasoning before
use; `fixtures/replay.py` checks actual model request semantics and consumes each
response once. `tests/test_replay.py:52` exercises integrity and mismatch handling.
The separate TypeSafe protocol/accepted-response packs are also maintained assets.
`run_suite.py mock --suite all` selects the existing grouped deterministic suites.
`mock` defaults to replacing `evidence/latest`; explicit `--output` requires a new
directory. Reference packs are never output locations.

## Dependencies and Operational Constraints

- Sidecar POM is `1.0.0-beta.4-SNAPSHOT` with Framework
  `1.0.0-beta.9-SNAPSHOT`. Suite embedded Java also selects this Framework version.
  Framework public executor documentation is
  `framework:docs/quickstart-executors.md`, with detailed contracts in
  `framework:agent-skills/loomspan-docs/references/skill-authoring/executor-skills.md`
  and `references/java-api/skill-reload.md`. Public source
  `framework:src/main/java/ai/loomspan/api/SkillExecutorInvocation.java:13` confirms
  map capture and the executor-generation identifier; `SkillReloader.java:10`
  confirms namespace-explicit validate/prepare/snapshot and retirement listener.
- No separate artifact/source revision proof or Framework rebuild is required.
  The suite currently does enforce both in `record_build.py:30` and documents a
  Framework `clean install` prerequisite. Those historical requirements conflict
  with the current ticket's installed-snapshot workflow.
- Read-only environment checks succeeded: Maven 3.9.6 using Java 21.0.2,
  Docker server 29.8.1, Compose v5.5.1, and suite `.venv` Python 3.13.13.
  `docker ps` listed only unrelated `azurite-qa`; no Loomspan stack was started.
  Browser binaries, certificate CLI prerequisites and image/cache availability
  were not established by these checks.
- Sidecar required local commands are in `docs/verification.md:5`:
  `./mvnw.cmd -B -ntp verify`, script unittest discovery, and packaged authoring
  client unittest discovery. Browser tests use Playwright Chromium. SDK reactor
  and maintained suite lanes have their own build/test commands.
- Suite startup helpers use Compose overlays, including provider-disabled
  settings. Bare Compose bypasses those controls. `configure_isolated.py:15`
  refuses to reconfigure an existing `.runtime`; fresh workspace isolation is
  distinct from overwriting retained credentials or data. Suite replay/smoke
  add synthetic rows; service-request fixtures can create synthetic receipts.
  The ticket does not authorize operational store resets or live commitments.
- 9.2 destructively changed development schema definitions and bundle format.
  Its internal verification documents that existing stores need developer-owned
  replacement/reset and lists loss of accounts/tokens/drafts/history/credentials.
  No existing store was reset by research. Old suite Sidecar volumes cannot be
  assumed suitable for the current schema merely because file mode is selected.
- SDK scenarios use temporary TLS material and normal JVM trust for Standard;
  Caddy owns mTLS in Hardened. No executor client-certificate feature is present
  or required. No public Framework gap was established by this source research.

## Historical Context and Deferred Checks

9.1 verification recorded compiler blockers in coordinator/runtime management and
namespace-less controller calls. Current source contains their replacements:
generation registry, complete default-namespace reload calls and aligned catalog
controller. 9.2's last independent record reports 300 Java tests, 299 passing and
one release-preparation failure, with zero skipped tests. These are historical
results, not new execution evidence for 9.3.

Two explicit 9.3-owned deferred checks remain represented in current source:

1. `release/ReleasePreparationTest.java:36` expects successful beta.8 release-plan
   validation. `scripts/prepare-release.py:37` rejects the current beta.9 snapshot
   POM with `pom.xml does not use the beta 8 framework line`. POM release enforcer
   at line 173, `.github/workflows/ci.yml:14` and release workflow similarly pin
   beta.8. The build failure does not prove any functional test failed, but also
   does not establish package/repackage/verify completion.
2. `scripts/test_sidecar_version.py:59` copies the checkout metadata into a
   disposable repository and expects successful release tagging without replacing
   the snapshot Framework dependency. The real guard correctly rejects it.
   `test_snapshot_framework_blocks_release` separately exercises that prohibition.

The design notes' final "Current implementation evidence" section still links
removed REST classes. It is historical internal context; current executable paths
are the HTTP classes described above. Product and client guidance have a different
obligation: they describe current supported behavior without delivery history.

Snapshot integration completion does not satisfy release-artifact/hosted-CI gates.
The remaining release sequence is explicit: developer publishes Framework beta.9
to Maven Central first; Sidecar is then pinned to that released artifact and its
final build/tests/final commit are repeated; hosted CI is checked after publication
and before Sidecar `1.0.0-beta.4` / `v1.0.0-beta.4` release. Releases are never
overwritten. No publication or tag is authorized by this ticket.

## Open Questions

- Actual current full build and maintained suite results are unexecuted in this
  step. Earlier pass counts do not certify the current assembled application.
- Which representative composed scenarios provide the remaining ticket-owned
  evidence, given the existing focused coverage and the SDK runner's whole-recipe
  selectors? This is a planning/verification question, not a new product choice.
- Frozen request matching across the changed Framework scaffold has not been
  executed. Any material replay-contract change remains a developer decision;
  research does not authorize editing accepted responses or weakening matching.
- The precise safe fresh-workspace invocation for business replay remains for
  planning. Current configuration has retained-data mounts and old-source paths;
  SDK lanes already use isolated temporary projects. No operational reset is
  implied by availability of Docker or by the historical development-reset note.
- Release-tooling reconciliation must keep future release safety meaningful
  while distinguishing local snapshot verification from unpublished release
  checks. The snapshot dependency must remain unchanged until publication.
