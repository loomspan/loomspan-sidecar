# SDK v1 API, deployment and evidence implementation plan

## Overview

- Ticket: `ai/thoughts/tickets/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md`
- Research: `ai/thoughts/research/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md`
- Outcome: close Phase 0 reference decisions with API/outcome contracts, versioned
  planned deployment recipes and scenario-level delivery/evidence ownership.
- Execution: pipeline mode, Full 5-Step Pipeline approved by developer. This ticket
  implements documentation, not the SDK, deployment tools or test-suite changes.

**Development policy:** destructively replace obsolete development contracts; no
compatibility shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No development-data
reset is needed; no deployed-data deletion is authorized.

## Current State

`sdks/java/.gitkeep` is the entire SDK. Root `pom.xml` selects Java 21, Boot 4.1.0,
Sidecar beta.4-SNAPSHOT and Framework beta.8. `ExecutionController.execute` already
accepts JSON objects and returns 202 plus UUID; `execution` returns retained snapshots,
including FAILED with HTTP 200. `ApiExceptionHandler` preserves ProblemDetail and
validation issues. Missing/nonowner/expired records share 404; restart loses records.
`RestTargetClients.build` disables retry/redirect/cookies but captures static TLS
context. Neither its configuration nor Framework tests prove new composed paths.

The preceding identity and trust references are normative planned contracts, including
servlet MVC callbacks, 300s JWT lifetime/60s skew, Keycloak 26.4.0, Smallstep bootstrap,
durable trust, queue withdrawal gates and measured TLS connection retirement.
`examples/production/compose.yaml` uses Caddy 2.10.2 and a shared backend; it does not
prove SDK mTLS or group isolation. The external suite intentionally uses Framework
beta.9-SNAPSHOT with pinned beta.3 Sidecar source; its embedded TypeSafe reference,
Python path and synthetic/frozen fixtures must remain preserved and separately
attributed. Its current runner is not an SDK v1 runner.

## Desired End State

Three new normative documents own API, deployment and complete acceptance mapping.
All API/configuration names, deployment commands, recipe assets, test locations and
owning units are explicitly PLANNED/NOT RUN until implemented and verified. Existing
references retain detailed assertions and stable scenario IDs. Phase documents link
the final contracts and ownership instead of implying that Phase 0 completed runtime
support. The seven acceptance criteria below are numbered AC1–AC7 in ticket order.

## Scope

### In scope

- Concrete eight-area SDK contract, submission/wait/conversion outcome tables.
- Isolated/group deployment recipes, independent transports, bootstrap/lifecycle
  owners, pinned local runtime/tool versions and exact planned command interfaces.
- Full preceding scenario inventories, roadmap expansion, phase/repository/unit/
  evidence mapping and explicit composition expansion/shared-evidence rules.
- Suite baseline reconciliation and future agreement update, deterministic tiers,
  cloud/provider manual procedures and honest status, related-plan/lens reconciliation.

### Out of scope

- Production/test implementation, executable SDK/Compose/runner assets, external-suite
  edits, new implementation tickets, live services, paid calls, cloud compatibility
  checks, commits/pushes/releases or hosted CI.
- New Python functionality, other language SDKs, management-authoring SDK, code
  generation, streaming or server cancellation.

## Active Project Guardrails

- Framework executes skills; Sidecar owns admission/retention, SDK owns HTTP/user
  integration and applications own business authorization/duplicate protection.
- Closed public `ai.loomspan.api` surface, authorized `RestSkillHandler`, existing
  internal/autoconfigure ArchUnit exclusions and no Sidecar Java `@SkillMethod`.
- `loomspan.*`, `loomspan-sidecar.*`, planned `loomspan-sdk.*` and standard Boot SSL
  namespaces remain distinct. No internal replacement or new SPI is authorized.
- No automatic submission/callback retries, including proxies and refresh/recovery.
  No cancellation, rollback, exactly-once, complete-history or lost-ID recovery claim.
- Trusted identity remains outside business/model data; preserve credentials and
  result/diagnostic content fidelity, recipient restrictions and server authorization.
- Use independent SDK build/versioning without changing the root build or beta.8 pin.
  Future Framework public-contract work belongs in Framework and must follow the
  publication/local-integration policy before final Sidecar release checks.

## Impact and Risk Analysis

The main risk is design text suggesting guarantees that current code does not prove.
Separate inspected source, normative planned behavior, future local evidence and
separately authorized external evidence in every artifact. Proxy errors cannot prove
rejection; ambiguous transport progress must remain acceptance unknown. Reliable IDs
must survive all later errors without enabling arbitrary Location redirects.

Shared Compose networks/unpublished ports cannot enforce isolation. The reference
must identify protected loopback/shared namespaces, exact memberships and egress
ownership, including privileged-host trust assumptions. Client Caddy must not expose
an outbound identity to other workloads. Group Sidecar Caddy remains required.

Matrix compression can silently erase preceding negative variants. Preserve every
source ID and its full assertion by direct trace, and explicitly expand compulsory
strategy/path/topology compositions. Component sharing needs a reason and cannot
replace actual end-to-end support evidence.

## Implementation Approach

Create these documents under `ai/thoughts/phases/`:

1. `2026-10-15-sdk-v1-api-reference.md` owns runtime, packaging, DTO/API, outcome,
   retry/wait, conversion and observability contracts.
2. `2026-10-15-sdk-v1-deployment-recipes.md` owns selected versions, per-hop topology,
   startup/material/route protection, future command interface and external checks.
3. `2026-10-15-sdk-v1-acceptance-matrix.md` owns the combined named scenario inventory,
   planned implementation-unit/evidence registry, tiers and release claim boundaries.

Link prior contracts instead of independently redefining identity/trust. Keep the
preceding scenario documents normative for detailed variants; the final matrix must
enumerate each individual ID with phase/repository/named unit/intended location and
source link. Do not use a family-only row as the complete mapping. No production
validator framework or permanent documentation lint tool is needed for this ticket;
read-only structural checks and full semantic review are proportionate.

Selected design direction:

- Java 21; optional Boot 4.1.0/Spring servlet MVC/Security integration. Standalone
  HTTP core requires neither Boot nor Framework. Independent Maven parent/modules
  under `sdks/java/`, explicit SDK version independent from Sidecar/Framework. Core
  exposes blocking and CompletableFuture APIs with equivalent envelopes; no reactive
  callback integration. Preserve the preceding explicit MVC async scope wrappers.
- Discovery list/by-name; JSON-object submit returns an accepted handle immediately;
  get-by-ID and bounded await are separate; terminal outcomes preserve unchanged text,
  failures/issues, configuration snapshot ID and optional available events. Conversion
  operates on an existing terminal snapshot, preserving it on failure without rerun.
- Definite pre-send requires transport proof; documented trusted Sidecar rejection
  differs from proxy/protocol uncertainty. Any reliable UUID survives unknown outcome.
  Conflicting IDs or unsafe foreign Location never silently select/follow a target.
  A valid 202/body UUID establishes acceptance despite foreign/unsafe Location: retain
  a warning and construct lookup locally, including client-Caddy's different origin.
  Damaged body without a trustworthy same-origin Location stays acceptance unknown.
- Safe reads use finite attempts, capped backoff and monotonic total deadline; no
  implicit POST retry. Wait timeout/interruption/async cancellation retains known ID,
  stops local waiting and never submits or cancels work. 404 remains ambiguous.
  `WaitFuture` exposes retained ID/last-snapshot observation after cancellation and
  client close, including cancellation/completion races. Rejection provenance defaults
  UNKNOWN; explicit verified direct-Sidecar routes alone permit definite API rejection.
  Proxied errors retain uncertainty without an unspecified origin-observation channel.
- HTTP/HTTP and direct HTTPS/HTTPS plus both mTLS paths; callback ingress independently
  protected HTTP, HTTPS or mTLS. No callback means no callback server/material. Optional
  JWKS hosting remains separate. Shared namespace loopback boundaries or equivalent
  OS-enforced protection prevent inbound bypass/outbound proxy borrowing.
- Planning pins: Docker Engine/CLI 28.5.1, Compose 2.40.2, Caddy 2.10.2 unchanged,
  step-ca 0.28.4, step CLI 0.28.7 and Keycloak 26.4.0. These are reproducible selected
  baselines, not verification or a claim of latest/security-certified versions.
  Caddy's standard leaf verifier/file loader enforces an exact approved public-leaf
  list plus CA verification; the protected controller validates member SAN/EKU and
  stages renewal leafs at receivers before sender activation. Real handshake and
  retirement evidence remains compulsory, including unlisted same-CA/same-SAN peers.
- Preserve the external suite's beta.9 TypeSafe baseline and add a deliberate SDK v1
  Java-to-Sidecar lane against explicitly recorded SDK/Sidecar/Framework revisions;
  do not equate evidence from different combinations. Update its project agreement
  only when implementation starts. SDK TypeSafe conversion cannot import Framework
  into core; any optional integration has independent explicitly verified dependency.
- Planned owning units are descriptive names, not fabricated tickets/IDs. Assign
  component tests in Sidecar/SDK and topology/fault evidence in the external suite.

Rejected alternatives: use of Framework internals, mutating existing suite fixtures,
an all-transport cross-product, a universal security-test cross-product, and deployed
recipe code in a documentation ticket would broaden scope without meeting a concrete
need. Explicit compulsory compositions plus justified component sharing are sufficient.

## Phase 1: Complete SDK reference contracts

### Changes

- [x] `ai/thoughts/phases/2026-10-15-sdk-v1-api-reference.md` — select module/package/
  version ownership, core and Spring boundaries, eight functionality areas, public
  operation signatures/DTO semantics, settings, ownership/close and safe diagnostics.
- [x] Same file — outcome table with trusted rejection, proven pre-send, unknown
  acceptance/lost ID, reliable-ID extraction, protocol/poll failures, total deadlines,
  interruption and HTTP-200 FAILED; distinguish optional conversion/TypeSafe failure.
- [x] Same file — explicit no-retry/no-rerun/no-rollback guidance and planned synthetic
  atomic stable-business-operation-ID fixture with concurrency/reconciliation cases.

### Automated verification

- [x] `git diff --check` — no whitespace defects.
- [x] Read-only PowerShell inventory/content assertions defined in testing plan —
  all eight functionality headings and outcome classes appear; no runtime edits.

### Optional developer checks

- None; runtime acceptance remains a future implementation obligation.

## Phase 2: Pin protected deployment and planned execution recipes

### Changes

- [x] `ai/thoughts/phases/2026-10-15-sdk-v1-deployment-recipes.md` — pinned prerequisites,
  isolated/group service/network/port/volume owners, both mTLS paths, independent
  callback ingress and no-callback/JWKS choices. Record exact SAN/EKU/chain/hostname/
  peer checks and per-hop guarantees; preserve management separation and no replay.
- [x] Same file — automatic NEW/ESTABLISHED bootstrap, generation/renewal/recovery gates,
  callback egress retirement owner and customer-supplied material alternatives.
- [x] Same file — exact future working directories/commands/flags/assets, fixtures,
  evidence schema and AWS/Azure/Google Cloud plus named hosted-provider manual checks.
  Every future command is labelled PLANNED/NOT RUN; external checks require separate
  authorization and limit claims without blocking deterministic local release gates.

### Automated verification

- [x] `git diff --check` and local Markdown link target check from testing plan.
- [x] PowerShell version/recipe presence assertions; manually inspect every hop and
  deny/bypass route. Do not run described future commands or live Compose recipes.

### Optional developer checks

- Future separately authorized cloud/provider checks only; no current external runs.

## Phase 3: Consolidate traceability and reconcile Phase 0

### Changes

- [x] `ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md` — full named inventories
  of both preceding scenario files, roadmap additions for API/outcomes/build/cloud,
  every-row source/phase/repository/unit/evidence locations and planned status.
- [x] Same file — primary HTTP/HTTPS/mTLS four-strategy compositions; isolated/group
  and both mTLS paths, focused mixed/no-callback/resumed-polling expansion, relevant
  negatives/lifecycle and explicit shared-component evidence justifications.
- [x] Same file — exact planned SDK/root/suite tiers, attempt counters, deterministic
  committed-effect/duplicate-protection fixtures, evidence fields and Phase 4 gates.
- [x] `ai/thoughts/phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md` and Phase 1–4
  files — link new normative contracts/owners and replace resolved pending language.
- [x] `ai/thoughts/phases/2026-10-11-java-spring-sdk-roadmap.md` — link resolved final
  reference contracts and remove conflicting pending scope decisions only.
- [x] Earlier reference/scenario documents — narrowly reconcile stale PR-8.0.3 handoff
  language with final matrix ownership; preserve their detailed normative assertions.
- [x] `ai/thoughts/design-lens.md` — verify consistency; edit only if a cross-ticket
  standing constraint truly changes. Record demonstrated Sidecar gaps (multi-issuer
  trust, callback/material lifecycle, no SDK) and absence of a proven missing public
  Framework contract; explicitly block affected future work if such a gap is found.

### Automated verification

- [x] `git diff --check`; scenario inventory equality, local-link checks and scoped
  changed-file inspection per testing plan. Each preceding scenario ID maps exactly
  once in the consolidated ownership table, preserving detailed variants by source.

### Optional developer checks

- None. Independent fresh Step 5 review is the current completion gate.

## Test Strategy

Documentation structure/inventory checks plus semantic acceptance review prove this
ticket. No Java tests change and Maven/Compose/provider runs add no proof of unimplemented
SDK design. Future test tiers are concrete design deliverables, not executed gates here.
Step 3 supplies AC checks, exact read-only commands and scenario comparison procedures.

## Acceptance-Criteria Traceability

| Criterion | Planned documentation evidence | Planned verification evidence |
| --- | --- | --- |
| AC1 runtime/API/packaging/eight areas | API reference, suite baseline section | Eight-area/API review; versions versus root/suite sources |
| AC2 outcomes/faults/attempts/duplicates | API outcome table and matrix named fault rows | Negative-outcome semantic audit; counter/fixture assertions in planned scenarios |
| AC3 recipes/transports/peers/lifecycle | Deployment recipes and topology rows | Pin/recipe inventory; per-hop/bypass review; no-callback independent settings |
| AC4 complete scenario traceability | Final matrix plus prior source assertions | Source-ID set comparison; required composition expansion/count audit |
| AC5 commands/tiers/suite preservation | Recipes, evidence registry and agreement handoff | Exact future-command/prerequisite/status review; external diff remains empty |
| AC6 external/manual/readiness | Named cloud/provider procedures and Phase 4 mapping | Status/authorization/claim audit; all local readiness obligations mapped |
| AC7 closure/owners/dependencies | Phase reconciliation, gap register and references | Cross-document review, local links, no unresolved material choices/no weakened boundary |

## Risks and Rollback/Recovery

No runtime, data or deployed configuration changes. Correct or revert only ticket-owned
documentation hunks if review finds an error; preserve research/plans and unrelated work.
The research artifact is the sole dirty file before planning. Do not reset the tree.
No compatibility support is claimed until future local evidence exists. No new technical
debt or temporary build breakage is planned.

## References

- Ticket, research, design lens and preceding identity/trust references and scenarios.
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java`,
  `ApiExceptionHandler.java`, `rest/RestTargetClients.java`; root `pom.xml`.
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`;
  `.github/workflows/ci.yml`; `examples/production/compose.yaml`.
- External suite `config/runtime-source.json`, `apps/java/pom.xml`,
  `docs/project-agreement.md` inspected read-only.
- [Docker Engine 28 release notes](https://docs.docker.com/engine/release-notes/28/),
  [Compose 2.40.2 release](https://github.com/docker/compose/releases/tag/v2.40.2),
  [Step CA 0.28.4](https://github.com/smallstep/certificates/releases/tag/v0.28.4),
  [Step CLI 0.28.7](https://github.com/smallstep/cli/releases/tag/v0.28.7).
  Official release pages were read during planning only; no binaries or images ran.
