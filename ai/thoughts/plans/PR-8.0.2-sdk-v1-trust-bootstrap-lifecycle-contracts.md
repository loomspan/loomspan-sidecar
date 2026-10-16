# SDK v1 trust, bootstrap and lifecycle contracts Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md`
- Research: `ai/thoughts/research/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md`
- Outcome: reviewed normative contracts and named planned acceptance scenarios; no production implementation or runtime support claim.
- Selected profile: Full 5-Step Pipeline, developer approved.

**Development policy:** Destructively replace superseded development contracts;
no compatibility shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No data reset is
required by this documentation unit; no deployed-data deletion is authorized.

## Current State

`security/JwtSecurityConfiguration.java` (`sidecarJwtDecoder`) selects one static
RSA key, configured URL or issuer discovery. `security/SidecarJwtProperties.java`
binds process configuration; neither owns persisted administrative trust or emergency
withdrawal. Paths here are relative to `src/main/java/ai/loomspan/sidecar/`.

`management/ManagementBearerFilter.java` (`required`) already closes bearer access
to specific management authoring routes. `ManagementPersonalTokenService.issue`
hashes high-entropy opaque secrets and limits expiry, but lacks human token names.
`ManagementIdentityService` currently has multiple users/roles; the agreed SDK v1
single administrator replaces that model. `ManagementEditingService.publish` and
`configuration/RuntimeConfigurationService` already gate exact candidate/base,
lease, validation and live authority. Trust is absent from `ManagedConfiguration`.

`rest/RestTargetClients.build` builds pooled TLS clients with captured SSL contexts;
renewal and membership removal are absent. `execution/ExecutionCoordinator` retains
captured JWTs across queue handoff. Framework beta.8 admission exposes no execution
kill API. `examples/production/compose.yaml` supplies Caddy server TLS and an uploaded
public key, not automatic SDK signing/mTLS provisioning. No implemented SDK exists.

Verified build/test landscape: Java 21, Boot 4.1.0, JUnit 5/AssertJ, Spring random-port
HTTP integration, Nimbus, BouncyCastle TLS fixtures and ArchUnit; Framework remains
`1.0.0-beta.8`. Existing tests inform future ownership but prove none of this new design.

## Desired End State

A dated reference and adjacent scenario matrix settle public trust payloads and
protected state, shared management authorization, one signing/publication owner,
endpoint/export, automated isolated bootstrap and separate JWT/TLS lifecycle.
Each acceptance criterion maps to observable planned evidence with phase and repository
ownership. Narrow reconciliations prevent standing roadmap/lens guidance from
contradicting the reference. No unresolved material choice may pass affected sign-off.

## Scope
### In scope
- `ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md`.
- `ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md`.
- Narrow updates to the roadmap, Phase 0, affected Phase 1/2/3 plans, design lens,
  preceding identity reference if needed, and this ticket's acceptance/evidence status.
### Out of scope
- Product Java/config/schema changes, provisioning tooling, real data changes, SDK
  code, new Framework SPI, future implementation tickets and live deployments.
- Final runtime versions, executable deployment commands and consolidated matrix,
  owned by PR-8.0.3; commit, push, release and hosted CI.

## Active Project Guardrails

Keep public `ai.loomspan.api` only, including supported `RestSkillHandler`; retain
ArchUnit bans on internal/autoconfigure imports and Java skills. Keep Framework
execution authority and captured generations. Sidecar owns trust/admission/transport;
the SDK owns issuance and callback validation, independently configured. Preserve
shared authoring, cumulative scopes, destination delegation, exact leases/revisions,
atomic publication, file-mode restrictions, browser CSRF and credential-domain isolation.
Preserve unchanged-token semantics, 300s lifetime/60s skew and no automatic replay.
Use documented `loomspan.*`, Sidecar `loomspan-sidecar.*`, planned SDK
`loomspan-sdk.*` and standard Boot SSL namespaces. Do not claim Framework tests as
Sidecar evidence or installed SDK documentation as matching beta.8 authority.

## Impact and Risk Analysis

Protected trust can be restored indirectly by import, rollback, stale drafts, cache
refresh or repeated bootstrap; design durable policy revision and withdrawal tombstones
outside ordinary authoring and recheck under the commit gate. URL retrieval can cause
SSRF and unbounded stale acceptance; specify destination/TLS controls, bounded fetches,
refresh/cold-start/outage limits and observable rejection clocks. Independent validators
cannot be revoked centrally by changing Sidecar alone. Signing replicas and exporters
can overwrite identity; define one writer and explicit replacement boundaries. TLS
renewal leaves old pooled connections usable unless deliberately retired. A terminated
connection or withdrawal cannot promise rollback, cancellation or replay safety.

## Implementation Approach

Use a single normative reference rather than repeating contracts in every phase.
Select public RSA JWK/JWKS and PEM upload contracts compatible with PR-8.0.1's
RS256 minimum-2048-bit profile; reject private/symmetric material atomically. Define
issuer-policy fields, source identity, revisions and persistent withdrawal barriers.
Use finite explicit URL retrieval settings with a durable last-success snapshot and
bounded stale use; token-controlled URLs/discovery never enlarge trust.

Keep ordinary authoring separate from protected trust administration, while checking
any protected section supplied through indirect paths instead of silently dropping it.
A single interactive administrator owns tokens; live disabled/credential-version state
still bounds cumulative Read/Edit/Publish grants. Preserve current operation paths
where possible and explicitly name planned trust/admin routes and every token denial.

Choose one SDK-managed persistent signing-state source, initialized only for a marked
new deployment, with exclusive writer ownership and explicit publication-before-use.
Read-only replicas consume coordinated snapshots; an application-supplied signer/source
replaces the default entirely. Do not invent distributed key-store coordination.
Endpoint and both export interfaces consume the same immutable public snapshot.
File output is explicit, atomic, skip-existing on startup, and never a signing action.

Select maintained Smallstep `step-ca`/`step` tooling for automated Compose TLS
provisioning; it is a private CA, not a new login/identity server. Deployment init
and renewal services invoke tooling automatically; customers do not run certificate
commands or exchange files. SDK signing initialization separately creates the app's
JWT key in its protected store. Bootstrap supplies only public trust to Sidecar,
with first-initialization guards under file/database authority and no later runtime
trust bypass. Explicit customer material replaces automation without fallback.
Primary sources: [Docker CA bootstrap](https://smallstep.com/docs/tutorials/docker-tls-certificate-authority/),
[production ownership](https://smallstep.com/docs/step-ca/certificate-authority-server-production/),
[renewal](https://smallstep.com/docs/step-ca/renewal/). These support mechanism choice;
they do not prove the proposed Compose recipe, its member policy or application reload.
PR-8.0.3 selects pinned versions and exact commands.

Define routine overlap and emergency withdrawal separately. Sidecar rejects new
admissions after local trust commit and checks queued work at handoff; admitted
Framework work is not killed. Independently operated callbacks enforce their own
committed policy on new callbacks; clearly quantify any refresh/coordination window.
Define both native and proxy TLS material generations, invalid-update behavior,
expiry and removal of existing/new member connections without retries.

## Phase 1: Specify protected trust and shared administration
### Changes
- [x] Reference: concrete upload/URL payloads, validation, policy/storage revisions,
  durable tombstones, initial/restart/outage rules and bounded retrieval timing.
- [x] Reference: operation table for all three presets and interactive admin;
  named token metadata/lifecycle and protected direct/indirect atomic rejection,
  after-wait rechecks, file authority, CSRF and credential-domain isolation.
### Automated verification
- [x] `git diff --check` — no whitespace/conflict defects in tracked edits.
- [x] `rg -n 'Read|Edit|Publish|interactive|tombstone|stale|CSRF|file' ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md` — locate required clauses for review; matches alone do not establish correctness.
### Optional developer checks
- None; independent security review is required by the pipeline.

## Phase 2: Specify shared signing, publication and bootstrap
### Changes
- [x] Same reference: persistent signing schema/states, permissions, one-writer/
  replica/source precedence, rollover, corrupt/missing recovery and public snapshots.
- [x] Same reference: exact proposed Java/property endpoint/export interfaces,
  disabled/no-server/outbound-only behavior, existing-file skip and safe atomic
  explicit replacement, public-only/private rejection and store-path protection.
- [x] Same reference: ordered automatic Compose initialization and separate CA/JWT
  ownership, mounts, public delivery, persistence, guarded bootstrap and customer inputs.
### Automated verification
- [x] `git diff --check`.
- [x] `rg -n 'signing|replica|export|endpoint|bootstrap|Smallstep|recovery' ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md` — locate concrete contracts and their failure paths for review.
### Optional developer checks
- None; no deployment is part of this contract ticket.

## Phase 3: Lifecycle timing, evidence ownership and reconciliation
### Changes
- [x] Same reference: upload/URL routine and emergency timing tables; independent
  callback enforcement, queue/admitted work and live-user/token checks; native/proxy
  TLS renewal, pools, membership removal and no-replay rules.
- [x] Scenario matrix: stable scenario IDs, parameterized presets/source/client-path
  variants, arrange/action/assert and phase/repository owners; mark every runtime row planned.
- [x] `ai/thoughts/phases/2026-10-11-java-spring-sdk-roadmap.md`, Phase 0/1/2/3
  documents, `ai/thoughts/design-lens.md` and affected identity reference: add narrow
  normative links, reconcile obsolete single/multi-user and trust-mechanism wording.
- [x] Ticket: record acceptance completion as contract delivery, remaining runtime
  evidence as planned, and any later reset impact without deleting data.
### Automated verification
- [x] `git diff --check`; `git diff --name-only`; `git status --short` — review scope and new artifacts.
- [x] Local relative Markdown links in changed/new documents resolve; use the exact
  PowerShell check in the testing plan. Review scenario IDs for duplicates/coverage.
### Optional developer checks
- None. Unimplemented lifecycle scenarios cannot be verified manually in this unit.

## Test Strategy

No behavioral red test applies to a contract-only diff. The missing deliverable is a
complete, internally consistent reference/matrix, not a Java behavior under change.
Use diff/link/scope verification and independent semantic/security review. Future
runtime cases must be concrete and assigned, including negative and recovery paths;
no new Java tests or unimplemented feature assertions are added now. Root Maven,
SDK and Compose runtime suites remain future implementation gates, not ticket evidence.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned contract evidence | Planned verification/scenario evidence |
| --- | --- | --- |
| AC1 sources/protected data/cache | Reference trust schemas, retrieval and durable state | TRUST upload/URL/private/SSRF/outage/restart scenarios |
| AC2 operation authorization | Reference operation table and commit gates | MGMT preset/action matrix, stale/indirect/atomic and destination-positive scenarios |
| AC3 signing/endpoint/export | Reference one owner, source precedence and export algorithms | SIGN/ENDPOINT/EXPORT snapshots/errors/path/no-state-change scenarios |
| AC4 automatic bootstrap | Reference ordered isolated provisioning and file/database authority | BOOT independent deployment/persistence/corrupt/customer/withdrawal scenarios |
| AC5 lifecycle | Reference clocks and two TLS paths | LIFE rollover/emergency/queue/callback and TLS renewal/member/no-replay scenarios |
| AC6 dependencies/owners/reconciliation | Reference ownership/gap table and linked standing plans | Named matrix phase/repository columns; independent review and local documentation checks |

## Risks and Rollback/Recovery

No runtime or data change is authorized. Revise rejected contracts and all affected
references together; do not preserve superseded variants as compatibility paths.
Missing provider capability or an unimplementable security guarantee blocks affected
contract sign-off and requires a focused recorded developer decision. No new Framework
contract is needed for explicit admission/queue/callback enforcement without execution
cancellation. Any later requirement to revoke arbitrary Framework work must be planned
in Framework rather than accessing internals. Future schema resets belong to their
implementation units; this plan authorizes none.

## References
- Ticket, research and preceding identity reference/matrix.
- Roadmap, Phase 0 and security research linked from the ticket.
- Current classes/tests and Framework beta.8 tagged public APIs listed in research.
- Primary Smallstep documentation linked in the selected approach above.

## Implementation and verification evidence

Implementation delivered the dated reference and 51-family planned scenario matrix,
plus narrow roadmap/Phase 0–3/identity-reference/design-lens reconciliation. The matrix's
acceptance table maps every ticket criterion to concrete reference clauses and row IDs;
all runtime rows are explicitly PLANNED. No production/test code or data changed.
No behavioral red test applies: the missing pre-change artifacts were supplied.
Independent Step 5 semantic/security review remains the final completion gate.

Selected ordinary details: immutable revisioned trust with key-kid/thumbprint and issuer
tombstones; 60s URL refresh, 5s fetch and 300s absolute last-success cutoff; exclusive
signer plus stopped replica installation barrier; property export uses create-if-absent,
Java explicit replacement, fail-closed filesystem safety; Smallstep TLS generation is
separate from SDK signing. TLS complete delivery loads within 5s, routine active
connections drain at most 30s, expiry closes immediately, emergency member removal
closes old connections within 5s of local gate. Operator delivery/independent-validator
coordination is separately measured and not claimed bounded globally.

Verification commands and actual results (documentation acceptance only):

- PASS — `git diff --check`: no whitespace errors in tracked edits (only repository
  LF/CRLF conversion warnings).
- PASS — full-file PowerShell check below: all 13 changed/new Markdown files have
  no trailing whitespace or conflict markers. A preliminary no-index loop incorrectly
  treated Git's new-file-diff exit 1 as a whitespace failure and stopped; its output
  contained no whitespace diagnostics. Replaced that faulty harness with this direct
  check, which passed; no document fix was needed.
- PASS — `git diff --name-only` and `git status --short`: ticket scope remains Markdown
  artifacts only; inspected tracked reconciliation and untracked deliverables.
- PASS — exact PowerShell relative-link check in the testing plan's
  [Safe Verification Commands](PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts-testing.md#safe-verification-commands):
  all changed/untracked document file links resolve (fragments also reviewed semantically).
- PASS — scenario ID check below: 51 unique explicitly planned families; coverage
  reviewed against AC1–AC6 and both sources/presets/validators/TLS paths, with positive,
  denial, race and recovery cases and initial Phase 1 enforcement ownership.
- PASS — both planned `rg -n` clause-location commands: source/admin and signing/
  bootstrap clauses present; semantic review performed separately, not inferred from matches.
- NOT RUN — `.\mvnw.cmd -B -ntp "-Dtest=ManagementPersonalTokenHttpIntegrationTest,JwtStartupIntegrationTest,RestTransportTlsIntegrationTest,SupportedLoomspanApiArchitectureTest" test`
  and `.\mvnw.cmd -B -ntp verify`: unrelated runtime checks for a prose-only unit.
  SDK/Compose lifecycle commands remain future owning-ticket work, not current evidence.

Exact scenario check:

```powershell
$matrix = Get-Content ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md -Raw
$scenarioRows = [regex]::Matches($matrix, '(?m)^\| ((?:TRUST|MGMT|SIGN|ENDPOINT|EXPORT|BOOT|LIFE|TLS)-\d+) / PLANNED \|')
$scenarioIds = @($scenarioRows | ForEach-Object { $_.Groups[1].Value })
if ($scenarioIds.Count -ne 51 -or @($scenarioIds | Group-Object | Where-Object Count -gt 1).Count) { throw 'Missing or duplicate scenario IDs' }
Write-Output "PASS: $($scenarioIds.Count) unique planned scenario families."
```

Exact full-file whitespace/conflict check (includes untracked documents):

```powershell
$docFiles = @((git diff --name-only -- '*.md'), (git ls-files --others --exclude-standard -- '*.md')) | ForEach-Object { $_ } | Where-Object { $_ }
foreach ($docFile in $docFiles) {
    $docText = Get-Content -LiteralPath $docFile -Raw
    if ($docText -match '(?m)[\t ]+\r?$|^(<<<<<<< |=======\r?$|>>>>>>> )') { throw "Whitespace or conflict marker in $docFile" }
}
Write-Output "PASS: all $($docFiles.Count) Markdown files have no trailing whitespace or conflict markers."
```

No optional manual developer check is needed for this contract unit. No secrets,
real deployment keys, data deletion or live-service operations entered the diff/checks.

