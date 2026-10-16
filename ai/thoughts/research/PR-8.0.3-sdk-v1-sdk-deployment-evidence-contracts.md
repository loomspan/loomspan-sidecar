---
date: 2026-10-15
repository: loomspan-sidecar
branch: main
commit: a64446841ee38cdce87772ba9fcfab8c4e5c77e5
ticket: ai/thoughts/tickets/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md
tags: [sdk, contracts, deployment, evidence]
---

# SDK v1 API, deployment and evidence research

## Research Question

What current Sidecar contracts, settled Phase 0 decisions, local Framework public
APIs and sample-suite boundaries constrain the final API/deployment/evidence design?
This step documents existing behavior and evidence; it does not choose implementation.

## Summary

The Java SDK directory contains only `.gitkeep`. Sidecar already exposes discovery,
202 execution acceptance, asynchronous retained snapshots, unchanged text results,
configuration correlation, selected public diagnostics and structured failures.
There is no implemented SDK outcome taxonomy, wait/retry policy, converter, bootstrap
or complete SDK v1 deployment matrix. The preceding two contracts settle identity,
callback, trust, signing and lifecycle requirements, all explicitly PLANNED.

Java 21 and Boot 4.1.0 agree across Sidecar and the sample Java app, but Framework
versions differ: this repository pins beta.8; the suite intentionally evaluates
beta.9-SNAPSHOT with beta.3 Sidecar source. That suite combination is not an upstream
release compatibility claim. Its TypeSafe fixtures and embedded reference are maintained
assets, not permission to downgrade or alter them during this documentation ticket.

## Repository State

- Observed 2026-10-15T23:19:35-07:00 (America/Los_Angeles).
- Sidecar branch `main`, commit `a64446841ee38cdce87772ba9fcfab8c4e5c77e5`.
- Working tree clean before research; this artifact is the only research mutation.
- Suite inspected at `14233ef4eca50bd7f0051f954a65812324142632`.
- Framework matching tag `v1.0.0-beta.8` resolves to
  `052e8b47edaf95c0d2013d9e6351284a98207d6b`; public source was read using `git show`.
- No service, provider, Maven build, Compose recipe, external compatibility check,
  commit, push or release was run. Tests mentioned here were inspected only.

Research coverage:

- [x] Ticket and normative roadmap/phase/security inputs.
- [x] Prior identity/callback and trust/bootstrap/lifecycle reference/scenario contracts.
- [x] Current discovery/submission/status/data/diagnostics and transport ownership.
- [x] Existing focused tests, proxy recipe and public Framework surfaces.
- [x] External suite baseline, extension agreement and evidence boundaries.
- [x] Remaining material design and evidence questions for planning.

## Current Behavior and Data Flow

### Discovery, submission and retained status

`SkillCatalogController.java:13` exposes `/v1/skills`; GET returns the public
`SkillDescriptor` list and `/{name}` returns a descriptor or 404. At beta.8,
`ai.loomspan.api.SkillDescriptor` contains `name,description,kind,inputSchema,outputSchema`;
schemas are strings and output schema may be absent. An SDK has no need to embed
Framework simply to model these HTTP fields.

`ExecutionController.java:41` accepts POST `/v1/skills/{name}/executions`, checks
existence, reads a JSON object, validates through public `SkillTemplate`, admits work
and returns 202 `{id: UUID}` with Location. The response is not a completion response.
`LimitedJsonObjectReader.java:26` rejects missing/null/nonobject/malformed/trailing
JSON and limits raw UTF-8 bytes. Default maximum input is 1 MiB.

`ExecutionCoordinator.java:112` owns admission under a gate: closed admission is 503;
retained/queue limits produce capacity errors. It creates one random UUID, stores
one in-memory record, and removes it if executor admission fails. Queued work later
captures a public Framework admitted invocation at `ExecutionCoordinator.java:158`.
The request's authenticated context is installed for handoff then restored.

GET `/v1/executions/{id}` returns HTTP 200 for every found snapshot, including FAILED.
`ExecutionController.java:59` emits ID, skillName, status, createdAt, completedAt,
result, failure and configurationSnapshotId; events is omitted when unselected.
`ExecutionRecord.java:23` starts QUEUED with nullable completion/result/failure/events/
configuration correlation. Handoff sets RUNNING and captured configuration ID;
terminal completion atomically supplies result or primary failure and selected events.
A fast completion may be observed without seeing intermediate states.

`ExecutionCoordinator.java:138` returns empty for missing or nonowner records.
`ExecutionCoordinator.java:149` expires completed records at completedAt + TTL.
The same status 404 therefore conceals nonownership and missing/expired state;
process restart also loses these in-memory records. It does not disclose why.
Defaults in `SidecarExecutionProperties.java:12`: 1000 retained, 15-minute completed
TTL, 32 concurrent, 128 queued and 64 MiB queued inputs; diagnostics NEVER.
Shutdown stops new dispatch, removes queued records and leaves Framework responsible
for admitted-work lifetime (`ExecutionCoordinator.java:231`). No durable trace,
ID recovery, execution deduplication or server cancellation API exists here.

### Errors, fidelity and available diagnostics

`ApiExceptionHandler.java:15` emits Spring ProblemDetail: 400 input/validation (issues
preserved), 403 denied, 404 missing, 413 oversized, 429 capacity and 503 unavailable.
Framework validation issues are `path,code,message` at beta.8. Security exceptions
and container/proxy failures are separate origins; an arbitrary proxy 5xx is not
this documented pre-admission rejection evidence.

`ExecutionFailure.java:6` carries INPUT_VALIDATION, ACCESS_DENIED or SKILL_FAILURE,
message and optional validation issues. `ExecutionCoordinator.java:204` publishes
COMPLETED/FAILED and selected events atomically; result is unchanged String on success
and null on failure. HTTP 200 does not turn FAILED into successful execution.
`SkillExecutionEvent` public fields are timestamp, level, type, details, frameId and
route. The SDK can preserve these available JSON values without importing Framework.
Diagnostics NEVER/ONERROR/ALWAYS control selection; missing observer events are not
reconstructed. Conversion is not performed by this HTTP controller.

### Outbound callbacks and connections

`RestTargetClients.java:58` builds one reusable pooled Apache client per target,
with explicit connect/pool/response timeouts and optional standard Boot SSL bundle.
Lines 76–78 disable redirects, automatic retries and cookie management. Client close
is idempotent and closes owned pools immediately. Response-cleanup interception
at line 79 discards endpoints on early body cleanup, avoiding unbounded draining.
These mechanisms are source evidence, not attempt-count evidence through new proxies.
The captured SSL context is constructed once; dynamic TLS reload and membership
connection retirement are planned by the prior reference, not implemented here.

`GenericRestSkillHandler.java:48` handles the supported public REST SPI; configured
caller passthrough sends the original worker JWT. The design lens preserves exact
credential/content fidelity, recipient approval, infrastructure secret separation and
application business authorization. No business-data sanitizer is present or required.

## Settled Reference Contracts and Remaining Boundaries

| Area | Current normative contract/evidence |
| --- | --- |
| Identity and token profiles | `phases/2026-10-15-sdk-v1-identity-callback-reference.md:15`: explicit four strategies, equivalent Java/property wiring, no fallback or implicit refresh; application-execution-v1 and Keycloak access profiles, pair ownership, 300s max lifetime/60s skew, purpose and all cnf rejection |
| Local Exchange | Same reference `:203`: Keycloak 26.4.0 standard V2 same-realm, confidential requester and narrow scopes/audiences; one attempt, no fallback. Registration/flow already selected, not open for independent redesign |
| Callback scope | Same reference `:395`: servlet MVC/Security first, two explicit arrangements, mandatory URL hook plus method/record authorization; no WebFlux support. Application-chain isolation must occur before session/context loading |
| Async | Same reference `:515`: Callable/DeferredResult with explicit SDK wrappers; outgoing immutable credential capture; no raw executor/common-pool/reactive propagation promise |
| Trust/admin | `phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md:19`: finite approved issuer/source entries, protected durable trust/tombstones; single administrator, cumulative R/E/P authoring, interactive-only trust/security administration and atomic indirect-path denials |
| Key publication | Same reference `:253`: one signer owner, protected persistent state and replica barrier. Endpoint/export share public snapshots; startup export skips existing safe file and explicit replacement is Java-only |
| Bootstrap | Same reference `:427`: Smallstep step-ca/step chosen; guarded lock/NEW→ESTABLISHED stages; public trust only to Sidecar, callback trust separately, no silent established-state reset. Exact versions/topologies/commands remain this ticket's work |
| Lifecycle | Same reference `:494`: upload/URL rollover and independent emergency commits, queue withdrawal gate without new Framework kill SPI; 65s normal URL removal detection and 300s outage-age bound |
| TLS | Same reference `:532`: versioned complete material, reload within 5s, routine active drain ≤30s, expiry cutoff and member removal ≤5s after local delivery; whole-generation stop if selective closure unavailable. Both paths/no replay mandatory |
| Existing production deployment | `examples/production/compose.yaml:3` pins Caddy 2.10.2; server HTTPS only. Backend application/health ports unpublished, one shared backend network. This is not evidence of exact peer membership, protected outbound identity or complete SDK/group bootstrap |
| Commands and scenarios | Both preceding scenario files explicitly PLANNED/NOT RUN. They preserve named negative cases and parameter variants, but do not assign final concrete units/locations/runner commands for this ticket's complete matrix |

## Key Components

- `pom.xml:13` — Java 21, Boot 4.1.0, Framework beta.8, independent root Sidecar build.
- `sdks/java/.gitkeep:1` — reserved SDK location, no Java SDK POM or implementation.
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41` — acceptance and status HTTP contract.
- `src/main/java/ai/loomspan/sidecar/api/SkillCatalogController.java:13` — descriptor discovery.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:112` — admission, ownership, retention and handoff.
- `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java:58` — reusable callback clients, no retry/redirect/cookie and static SSL context.
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:18` — ArchUnit production/test exclusion of internal/autoconfigure dependencies; line 29 bans Sidecar Java SkillMethod declarations.
- `ai/thoughts/design-lens.md:30` — Framework execution authority, admission/ownership, lifecycle, management and diagnostic invariants.

## Existing Tests and Fixtures

Focused source evidence includes ExecutionRequestBodyIntegrationTest (JSON object/raw
byte limit), AuthenticatedExecutionApiIntegrationTest and CustomRoleExecutionIntegrationTest
(admission/ownership), ExecutionCoordinatorTest (capacity/state/retention),
ExecutionConfigurationCorrelationIntegrationTest (captured snapshot),
ExecutionDiagnosticsTest (large text, selected public event fidelity, primary failure,
atomic terminal visibility, absent-observer handling, joint expiry),
ExecutionShutdownIntegrationTest (public-only integration), RestTransportTlsIntegrationTest
and GenericRestSkillHandlerTest (transport/callback), RestFailureEnvelopeIntegrationTest,
RestHandlerLifecycleIntegrationTest and RestGenerationIntegrationTest.

ProductionComposeBrowserIntegrationTest and `scripts/verify-production-tls.py` are
adjacent deployment patterns; no new SDK matrix can inherit completed support from
their presence. JWT fixtures are synthetic and local. No SDK-focused tests or
complete SDK/application/proxy attempt-count tests exist in the reserved SDK directory.

The preceding identity scenario IDs include STRATEGY, EXCHANGE, TOKEN, ISSUER,
IDENTITY, FRESHNESS, CALLBACK, PASSTHROUGH and TRANSPORT. The trust matrix includes
TRUST, MGMT, SIGN, ENDPOINT, EXPORT, BOOT, LIFE and TLS. Each family has explicit
negative variants; CALLBACK rows apply to both arrangements and malformed tokens
are tested independently at admission and callback. Their full row assertions remain
normative when consolidating ownership/evidence; one family-level happy path is insufficient.

## Dependencies and Operational Constraints

Framework beta.8 public `SkillTemplate`, `SkillInvocationHandoff`,
`AdmittedSkillInvocation`, `SkillReloader`, descriptors/events/issues and supported
`RestSkillHandler` supply existing execution/handoff/diagnostics. No newly required
Framework SPI is identified by current source inspection. TypeSafe-specific snapshot
sample behavior is not established by beta.8 evidence. Missing public capability is
an owning-Framework dependency, never permission for internals, bean replacement,
reflection or Sidecar Java SkillMethod skills. Namespace ownership remains Framework
`loomspan.*`, Sidecar `loomspan-sidecar.*`, future SDK `loomspan-sdk.*`, Boot SSL standard.

The root Dockerfile pins a Temurin Java 21 runtime digest. Docker/Compose and
Smallstep exact versions are not yet selected in the SDK reference contracts.
Caddy remains 2.10.2 baseline; Keycloak remains 26.4.0. Hosted/cloud verification
is not authorized; exact AWS/Azure/Google Cloud and named hosted-provider manual
procedures/status are still absent from the final SDK design.

The external suite has no root Maven POM. `apps/java/pom.xml:2` uses Boot 4.1.0,
Java 21 and Framework beta.9-SNAPSHOT. `config/runtime-source.json:2` pins Framework
commit 3265354b6c4975c8ae82ac9f111eff0ffcf77b9d and beta.3 Sidecar source
3eff6befd95f73b7d4091ed0da32498e0f64c488, rebuilt with that snapshot override.
`docs/project-agreement.md:15` explicitly records that direction. Foundation's final
baseline paragraph still says beta.8, a documentation disagreement superseded by
runtime-source/POM and the agreement's later TypeSafe section.

Suite README commands include `.venv/Scripts/python.exe -m pytest tests -q` and
`scripts/run_suite.py mock --suite all`; those target current embedded Java and
Python/Sidecar paths, not a Java SDK v1 path. Foundation documents authenticated
synthetic business operations and stable idempotent service-request receipts.
The agreement preserves equipment-service fixtures and separate TypeSafe synthetic/
accepted replay packs. New SDK v1 work requires an explicit agreement update when
implementation starts and clear separation from historical Phase 1/2/3. This ticket
changes neither the external agreement nor any fixtures/runtime.

Complex topology/fault evidence belongs in the external suite; SDK focused tests
belong in independent `sdks/java/`, Sidecar tests in the root. Each future unit owns
tests/documentation and attributable versions/revisions/configuration/command/result.
Acquisition/setup is not an offline-install promise; deterministic routine tests
use real JWT/JWKS, local Exchange and TLS boundaries without paid or hosted calls.

## Historical Context

`ce1e6c5` completed PR-8.0.1 documentation; `a644468` completed PR-8.0.2 documentation.
Their references/scenarios are design evidence only. The roadmap at lines 1057–1155
defines eight SDK functionality areas and acceptance uncertainty/no-replay/side-effect
limits; lines 1242–1330 define deterministic local tests and all-strategy primary,
both-mTLS isolated/group, mixed and no-callback evidence. Phase 4 independently requires
clean-consumer packaging, complete local matrix, closure of deferred breakage and
security-focused review before separately authorized release checks.

## Open Questions

These are planning questions rather than a research-blocking developer decision:

- Exact independently versioned SDK module/API shape, blocking/async methods and
  runtime/servlet support declaration consistent with existing callback contracts.
- Concrete rejection/pre-send/unknown/retained-ID error envelopes, trustworthy ID
  extraction from damaged acceptance responses, wait deadlines and bounded read policy.
- Exact pinned Docker/Compose/Smallstep/runtime prerequisites and protected networks,
  loopback/shared namespaces, management routing, Caddy peer policy and reload control.
- Explicit handling of suite beta.9 TypeSafe baseline while Sidecar stays beta.8,
  including which integration evidence can be claimed at each selected revision.
- Named planned owning units/evidence paths and exact future commands for every
  existing/new scenario, including application duplicate protection and attempt counts.
- Named hosted/cloud material recipes and separately authorized manual procedures;
  local key loading does not prove cloud issuance/delivery/renewal or provider support.

No missing public Framework contract has been demonstrated for the existing HTTP
API requirements. Any inability to satisfy the preceding callback isolation,
atomic-publication or connection-cutoff contracts remains an affected-implementation
blocker; it does not authorize weakening those contracts in this design ticket.
