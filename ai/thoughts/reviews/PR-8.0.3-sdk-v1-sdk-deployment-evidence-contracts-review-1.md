# PR-8.0.3 Code Review — Cycle 1

## Scope and Repository State

Pipeline mode, profile `full`; independent initial review completed before editing.
Reviewed current staged/unstaged/untracked ticket work against HEAD
`a64446841ee38cdce87772ba9fcfab8c4e5c77e5`. Initial pipeline tree was clean; current
changes are documentation under `ai/thoughts/`. There are no staged changes.
Scope includes the three new API/deployment/matrix documents, ticket, research,
implementation/testing plans, roadmap, Phase 0–4 and both earlier references/scenario
inventories. No previous review document was read.

Reconstructed actual acceptance/status/problem behavior from ExecutionController,
ApiExceptionHandler, SkillCatalogController and repository transport/deployment sources.
Compared the complete normative roadmap, security research, design lens and original
scenario variants, not merely table counts. Consulted Framework beta.8 public API
and external-suite Java baseline read-only. Caddy feasibility was checked against
tagged 2.10.2 sources rather than assuming a configurable SAN matcher exists.

## Findings

No remaining actionable findings after the fixes and internal re-review below.

## Findings Resolved in This Context

### [P2] Retain async wait context after cancellation

- Location: `ai/thoughts/phases/2026-10-15-sdk-v1-api-reference.md:73`.
- Scenario: `awaitAsync` returned an ordinary CompletableFuture; after cancel,
  get/join exposes CancellationException rather than the promised last snapshot/ID
  context. The submission-specific observation cannot serve this wait.
- Impact: the concrete async API did not fulfill its wait-termination envelope.
- Evidence: public signature and cancellation/wait outcome table had no retained
  wait observation despite requiring last-snapshot retention.
- Fix: select WaitFuture/WaitObservation, define publication and race semantics,
  preserve context after cancel and client close, and extend OUTCOME-05 accordingly.

### [P2] Define SDK-visible rejection provenance conservatively

- Location: `ai/thoughts/phases/2026-10-15-sdk-v1-api-reference.md:213`.
- Scenario: a proxy returns a plausible rejection after dispatch. The design referred
  to an origin observation channel without defining an SDK input or protocol for it.
- Impact: owners could invent an unsound origin heuristic or implement incompatible
  classification while Phase 0 claimed complete outcome contracts.
- Evidence: ClientSettings had no provenance field; planned proxy counters were
  out-of-band evidence rather than an authenticated per-response SDK contract.
- Fix: UNKNOWN by default; operator-declared verified DIRECT_SIDECAR route alone
  permits definite rejection. Both mTLS paths contain proxies and keep error
  responses unknown. Explicit property/Java mapping and OUTCOME-01 negatives agree.
  No new origin-signing protocol, trusted header or proxy plugin is introduced.

### [P2] Select an available Caddy peer enforcement mechanism

- Location: `ai/thoughts/phases/2026-10-15-sdk-v1-deployment-recipes.md:172`.
- Scenario: the reference required unspecified leaf-identity matchers and deferred
  whether pinned standard Caddy could enforce them until implementation.
- Impact: a material topology mechanism remained unresolved at Phase 0 closure.
- Evidence: tagged standard Caddy provides exact leaf verification and a file loader:
  [verifier implementation](https://github.com/caddyserver/caddy/blob/v2.10.2/modules/caddytls/connpolicy.go#L888),
  [loader implementation](https://github.com/caddyserver/caddy/blob/v2.10.2/modules/caddytls/leaffileloader.go).
  This establishes module availability, not completed integration evidence.
- Fix: require the built-in leaf verifier/file loader alongside normal CA verification;
  protected controller validates approved SAN/EKU/chain before supplying exact public
  leafs. Define receiver acknowledgement before sender renewal and removal of every
  member leaf. Add TOPOLOGY-01 for same-CA/same-SAN unlisted-peer rejection and
  renewal/removal. Runtime handshakes and cutoff remain future gates.

### [P2] Accept valid body IDs across the client proxy's different origin

- Location: `ai/thoughts/phases/2026-10-15-sdk-v1-api-reference.md:115`.
- Scenario: SDK uses local HTTP client Caddy; Sidecar emits its external HTTPS
  Location with a valid 202/body UUID. The previous foreign-Location rule classified
  this normal reference composition as acceptance unknown.
- Impact: client-proxy primary compositions could not produce the promised immediate
  accepted handle, despite a complete valid acceptance body.
- Evidence: deployment explicitly selects loopback 9080 for SDK-to-client-Caddy,
  while ExecutionController constructs Location from the Sidecar request origin;
  production proxy configuration forwards its external origin to Boot.
- Fix: valid 202/body UUID establishes acceptance unless a trustworthy same-origin
  Location conflicts. Discard foreign/unsafe Location with warning and construct
  status URI locally. Damaged body plus foreign Location remains unknown without a
  reliable ID. OUTCOME-04 includes both cases. No response-selected URL is followed.

The implementation plan records these contract decisions. Other implementation
artifacts changed here: API reference, deployment recipes and acceptance matrix.
The original 108 normative source assertion rows remain unchanged.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| AC1 runtime/eight SDK areas/builds | API reference eight areas, module/version/runtime selection and independently attributed suite baseline | Eight headings, outcome presence and source baseline inspection; BUILD-01/02 and API scenarios planned | implemented as documentation |
| AC2 outcomes/uncertainty/retention/effects | API taxonomy, WaitFuture, direct-only provenance, reliable IDs/local URI, bounded reads, transactional ledger | OUTCOME-01–06/EFFECT-01–02; semantic failure/race review; runtime attempts explicitly unrun | implemented as documentation |
| AC3 recipes/transport/peer/lifecycle | Deployment per-hop/namespace/UID ownership, management closed routing, independent callback ingress, concrete Caddy leaf mechanism | Exact 24 primary/32 mixed/16 outbound composition set; TOPOLOGY-01 and prior TLS/BOOT assertions | implemented as documentation |
| AC4 every roadmap/prior obligation | Complete registry and per-ID source/phase/repository/unit/location, source variants normative | 108 source rows preserved and uniquely mapped; all 72 expected composition IDs compared as a set; semantic roadmap-family review | implemented as documentation |
| AC5 planned commands/tiers/fixtures/suite preservation | Explicit cwd, flags/assets, pinned tools, real Keycloak/JWT/TLS boundary, future agreement owner | Planned runner/fixture/counter evidence; current boundary audit finds no external-suite changes | implemented as documentation |
| AC6 external procedures/readiness | Six named manual rows, unverified status, local gate separation, P4 clean consumers/restoration/security/publication | MANUAL-01–06 and READY-01/02 have owners/evidence; no external checks misreported as passes | implemented as documentation |
| AC7 coherent ownership/dependencies | Related references/phases, public-only boundary, demonstrated Sidecar gap register and affected-public-contract blocker | Semantic reconciliation, local targets, scope and whitespace checks | implemented as documentation |

All scenario/test paths above remain planned. No Java/runtime implementation or
runtime passing support is claimed by this documentation review. The review fixes
refine the plan within the approved scope and preserve its security/outcome intent.

## Active Project Guardrails

- Design lens and direct replacement policy retained; no compatibility shim or
  development-data reset is needed. No deployed data was touched.
- Public `ai.loomspan.api` boundary and authorized RestSkillHandler remain explicit;
  no internal/autoconfigure import, new SPI, bean replacement or Sidecar SkillMethod.
- Namespace ownership, beta.8 pin, independent versions/builds and publication policy
  remain unchanged. No SDK/runtime artifact was installed or rebuilt.
- Trust remains independent per receiver; management authority, protected-local
  enforcement and callback Sidecar-specific peer policy are not weakened.
- No automatic submission/callback retry or inferred cancellation/rollback. Content
  fidelity and infrastructure credential protections remain distinct.
- Local deterministic future evidence, existing suite preservation and separately
  authorized external compatibility remain explicit. No commits/pushes/releases ran.

## Open Questions and Assumptions

None requiring a developer decision for this documentation scope. As expressly
planned, public-only TypeSafe feasibility, supported Spring mixed-chain composition,
atomic publication and enforced connection cutoffs require implementation evidence;
a demonstrated unavailable public capability blocks its affected unit. Material
policy choices cannot be weakened by treating those gates as optional.

## Verification Results

PASS — `git -c core.autocrlf=false diff --check` after fixes.

PASS — all three exact PowerShell scripts embedded in the testing plan, plus the
ticket's combined structure/source-preservation script, executed independently by:

```powershell
$reviewPlan = Get-Content -Raw ai/thoughts/plans/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts-testing.md
$reviewChecks = [regex]::Matches($reviewPlan, '(?s)```powershell\r?\n(.*?)```')
if ($reviewChecks.Count -ne 3) { throw 'Unexpected documentation check count' }
foreach ($reviewCheck in $reviewChecks) { & ([scriptblock]::Create($reviewCheck.Groups[1].Value)) }
$reviewTicket = Get-Content -Raw ai/thoughts/tickets/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md
$reviewStructural = @([regex]::Matches($reviewTicket, '(?s)```powershell\r?\n(.*?)```') | Where-Object { $_.Groups[1].Value.Contains('$sdkDocs=') })
if ($reviewStructural.Count -ne 1) { throw 'Missing structural verification block' }
& ([scriptblock]::Create($reviewStructural[0].Groups[1].Value))
```

Observed: 108 mapped/unchanged source assertions; 24/32/16 composition counts;
three substantive final documents; eight functionality areas; 11 outcomes; pins
and local Markdown targets pass. New wait/provenance/leaf/proxy-origin contract
and matching matrix strings were also checked after editing; semantic review is
the substantive assurance, rather than keyword presence alone.

PASS — exact composition set and scope script (repository root):

```powershell
$reviewMatrix = Get-Content -Raw ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md
$reviewActual = @([regex]::Matches($reviewMatrix, '(?m)^\| ((?:PRIMARY|MIXED|OUTBOUND)-[A-Z-]+) \|') | ForEach-Object { $_.Groups[1].Value })
$reviewExpected = @()
foreach ($strategy in @('R','E','S','C')) {
  foreach ($direct in @('H','T')) { $reviewExpected += "PRIMARY-I-$direct-$strategy" }
  foreach ($topology in @('I','G')) { foreach ($path in @('P','N')) {
    $reviewExpected += "PRIMARY-$topology-$path-$strategy"
    $reviewExpected += "OUTBOUND-$topology-$path-$strategy"
    foreach ($callback in @('H','T')) { $reviewExpected += "MIXED-$topology-$path-$callback-$strategy" }
  } }
}
if (@($reviewActual | Sort-Object -Unique).Count -ne 72 -or (Compare-Object ($reviewExpected | Sort-Object) ($reviewActual | Sort-Object))) { throw 'Composition set mismatch' }
$reviewChanged = @(git diff --name-only) + @(git ls-files --others --exclude-standard)
foreach ($reviewPath in $reviewChanged) { if ($reviewPath -notlike 'ai/thoughts/*') { throw "Unexpected non-document edit $reviewPath" } }
$reviewExternal = @(git -C C:/opendev/code/loomspan-sidecar-test-suite status --short)
if ($reviewExternal.Count) { throw 'External suite changed' }
```

PASS — `git status --short`, `git diff --stat` and explicit untracked-document
inspection: documentation only; external suite clean. No secret material found in
the scoped text/diff; references are symbolic or public certificate/module metadata.

NOT RUN — `mvn verify`, `mvn -f sdks/java/pom.xml verify`, planned consumer/runner/
Compose commands, actual TLS/JWT/provider flows and cloud manual procedures. This
ticket changes design documents only; SDK/runner assets are not implemented. Such
runs cannot validate this ticket as present runtime support and remain future units'
acceptance obligations. No required current documentation check is left failing.

## Residual Risks and Optional Developer Checks

No optional live check is requested now. Future topology tests must prove UID owner
filtering, exact leaf admission, renewal acknowledgements and all cutoff windows with
real connections. Source inspection does not establish those runtime facts. Manual
cloud/provider checks require separate authorization and limit only their claims.

## Disposition

`fixes-applied`. Four in-scope documentation defects resolved and internally
re-reviewed without remaining actionable findings. A fresh Step 5 context must
review the resulting implementation artifacts before pipeline completion.
