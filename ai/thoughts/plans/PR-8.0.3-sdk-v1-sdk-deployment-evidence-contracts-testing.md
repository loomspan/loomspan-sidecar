# SDK v1 API, deployment and evidence testing plan

## Change Summary

Implement Phase 0 documentation contracts and planned evidence, not SDK/product/test
code. The implementation plan selects three new reference artifacts and narrowly
reconciles roadmap/phase/preceding references. All described future SDK/Sidecar/suite
tests and deployment runs remain **PLANNED, NOT RUN**.

Apply the [design lens](../design-lens.md#simplicity-and-technical-debt): replace obsolete
development contracts directly, introduce no legacy adapters/shims, and keep checks
proportional. No data reset, runtime breakage, commit, push or release is needed.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| API/outcomes | Proxy error misclassified as rejection; IDs lost after wait/conversion errors | AC1/AC2 semantic outcome-table review |
| Protected topology | Unpublished/shared network mistaken for isolation; proxy identity borrowed | AC3 hop/port/namespace/member/egress review |
| Matrix completeness | Prior negative variants or required strategy/path/topology cases disappear | AC4 source-ID comparison plus composition expansion audit |
| Baseline/evidence | Suite beta.9 evidence reported as beta.8 compatibility; plans called passes | AC5 exact revision/status/command audit |
| Cloud/release | Local key load mistaken for hosted renewal/compatibility; release gate expanded | AC6 manual-status/readiness audit |
| Scope/ownership | New internal SPI, external edits or unowned implementation gap | AC7 owning-unit and changed-file audit |

## Existing Coverage and Environment Constraints

Root Maven/JUnit/Spring/ArchUnit coverage already exercises admission, owner concealment,
JSON object limits, diagnostics, REST TLS, shutdown and supported public Framework use.
It does not prove unimplemented SDK or group/bootstrap behavior. `sdks/java/` has no
tests/build yet. The external suite's existing pytest/mock runner targets its embedded
Java and Python/Sidecar paths; a future SDK v1 runner must be explicitly labelled new.

Current verification requires PowerShell, Git and local Markdown files only. Official
release pages justify selected tool versions; acquisition is setup, not an offline
installation promise. No Java build, Docker, certificate issuance, JWT provider,
cloud credentials or paid model is needed to verify a documentation-only change.

## Failing Test First

- Name: `finalReferenceArtifactsAndPriorScenarioMapping`
- Type: documentation acceptance inspection, not a new product test.
- Location: three planned `ai/thoughts/phases/2026-10-15-sdk-v1-{api-reference,deployment-recipes,acceptance-matrix}.md` files.
- Arrange/Act/Assert: inspect files and compare every prior named scenario ID with
  the final ownership matrix; assert the final API/deployment/matrix contracts exist.
- Expected pre-fix failure: all three final reference files are absent, so complete
  ownership/composition/outcome mapping cannot yet be established. This observed gap
  is the intended documentation red state; do not invent a passing runtime test.

## Tests to Add or Update

No production or permanent automated test files are added. Execute these focused
read-only checks during implementation and independent review. Keep outputs in the
step verification record; future runtime acceptance remains in the planned matrix.

### 1. `referenceArtifactPresence`

- Type: structural documentation check.
- Location: `ai/thoughts/phases/` three final references.
- Proves: deliverables are present and substantive.
- Command (PowerShell, repository root):

```powershell
$sdkV1Docs = @('2026-10-15-sdk-v1-api-reference.md','2026-10-15-sdk-v1-deployment-recipes.md','2026-10-15-sdk-v1-acceptance-matrix.md')
foreach ($sdkV1Doc in $sdkV1Docs) { $sdkV1Path = Join-Path 'ai/thoughts/phases' $sdkV1Doc; if (!(Test-Path -LiteralPath $sdkV1Path)) { throw "Missing $sdkV1Path" }; if ((Get-Content -LiteralPath $sdkV1Path).Count -lt 50) { throw "Not substantive: $sdkV1Path" } }
```

### 2. `priorScenarioInventoryPreserved`

- Type: deterministic source-ID comparison.
- Location: both prior scenario files and final acceptance matrix.
- Proves: no named prior scenario disappears from consolidated mapping.
- Inputs: all table-row IDs matched from the two normative source inventories.
- Edge cases: mapping references must resolve to individual rows or explicit lists;
  a family heading/range alone does not prove per-ID ownership. Detailed variants
  remain normative at the linked source, independently reviewed for preservation.
- Command:

```powershell
$sdkV1Sources = @('ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md','ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md')
$sdkV1Ids = @($sdkV1Sources | ForEach-Object { [regex]::Matches((Get-Content -Raw -LiteralPath $_), '(?m)^\| ([A-Z]+-\d{2})\b') | ForEach-Object { $_.Groups[1].Value } }) | Sort-Object -Unique
$sdkV1Matrix = Get-Content -Raw -LiteralPath 'ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md'
$sdkV1Missing = @($sdkV1Ids | Where-Object { $sdkV1Matrix -notmatch ('(?<![A-Z0-9-])' + [regex]::Escape($_) + '(?![A-Z0-9-])') })
if ($sdkV1Missing.Count) { throw ('Missing scenario IDs: ' + ($sdkV1Missing -join ', ')) }
Write-Output ('Mapped prior scenario IDs: ' + $sdkV1Ids.Count)
```

### 3. `markdownLocalTargetsResolve`

- Type: read-only local file-link validation.
- Location: new final references, touched roadmap/phase/reference/scenario docs and plans.
- Proves: local Markdown paths point to actual documents. Future test/recipe assets
  must be code-form planned paths, not broken links pretending the assets exist.
- Command:

```powershell
$sdkV1LinkFiles = @('ai/thoughts/phases/2026-10-11-java-spring-sdk-roadmap.md','ai/thoughts/design-lens.md') + @(Get-ChildItem -LiteralPath 'ai/thoughts/phases' -Filter '2026-10-15-sdk-v1-*.md' | ForEach-Object { $_.FullName }) + @(Get-ChildItem -LiteralPath 'ai/thoughts/plans' -Filter 'PR-8.0.3*.md' | ForEach-Object { $_.FullName })
$sdkV1BadLinks = @()
foreach ($sdkV1File in $sdkV1LinkFiles) {
  $sdkV1Base = Split-Path -Parent (Resolve-Path -LiteralPath $sdkV1File).Path
  foreach ($sdkV1Match in [regex]::Matches((Get-Content -Raw -LiteralPath $sdkV1File), '\]\(([^)]+)\)')) {
    $sdkV1Target = $sdkV1Match.Groups[1].Value.Trim('<','>')
    if ($sdkV1Target -match '^(https?://|#|mailto:)') { continue }
    $sdkV1Target = ($sdkV1Target -split '#',2)[0]
    if ($sdkV1Target -and !(Test-Path -LiteralPath (Join-Path $sdkV1Base $sdkV1Target))) { $sdkV1BadLinks += "$sdkV1File -> $sdkV1Target" }
  }
}
if ($sdkV1BadLinks.Count) { throw ($sdkV1BadLinks -join "`n") }
```

### 4. `contractAndCompositionAcceptanceReview`

- Type: semantic review against all seven ticket criteria, complete roadmap obligations
  and both preceding normative scenario inventories; keyword matching is insufficient.
- Location: three final documents and affected phase/roadmap/reference sections.
- Proves AC1: eight areas have concrete operations/envelopes/settings, versions,
  standalone/core/Spring/servlet/async/TypeSafe boundaries and independent builds.
- Proves AC2: rejection/proven pre-send/unknown/retained-ID/404/HTTP-200 failure/conversion
  distinctions, trustworthy ID handling, bounded read retries/deadline and no replay.
  Fault fixtures count each configured layer, record committed effects and demonstrate
  atomic stable-business-ID duplicate protection/reconciliation without SDK guarantees.
- Proves AC3: HTTP/HTTP and HTTPS/HTTPS four-strategy cases plus both mTLS paths ×
  isolated/group × four strategies. Explicit focused mixed and no-callback/resume cases
  cover all four strategies/both mTLS paths where required. Verify callback ingress is
  independently chosen, Caddy retained/omitted correctly, JWKS separate, full hop and
  membership policy, management separation, no bypass/proxy borrowing and TLS lifecycle.
- Proves AC4: every matrix row carries source, phase, repository, descriptive planned
  unit and intended evidence location; required negatives/auth/lifecycle/export/admin
  variants survive. Shared evidence rationale does not replace composed integration.
- Proves AC5: exact future commands/flags/working directories/assets and prerequisites
  are clearly planned; real local JWT/JWKS/Exchange/TLS/application/proxy boundaries,
  synthetic fixtures and attempt counters. Existing suite baseline/assets unchanged;
  its agreement update is assigned to the implementation owner.
- Proves AC6: AWS/Azure/Google Cloud and named hosted providers have manual procedures,
  recorded unverified status and separate authorization; non-exportable signer unmet
  requirements block affected integration. Phase 4 includes clean consumers/full local
  matrix/temporary-breakage closure/independent security review/authorized release checks.
- Proves AC7: related docs agree on owners, versions and no hidden public Framework gap.
  No weakened preceding trust/lifecycle/callback policy or unresolved material choice.

### 5. `ticketChangeBoundary`

- Type: scope inspection.
- Commands: `git status --short`, `git diff --name-only`, `git diff --stat`,
  `git diff --check`; inspect untracked deliverables explicitly because Git diff omits them.
- Proves: only planned documentation changed; root build, production/tests, external
  suite, its agreement/runtime/fixtures and deployed material were not modified.
- Doubles/isolation: no live external operations; no commits, pushes or releases.

## Safe Verification Commands

- Focused: presence and source-ID checks above; `git diff --check`.
- Related suite: local Markdown target check plus full semantic AC/composition review.
- Full safe suite: combined documentation checks and scope audit above. Root Maven,
  browser/image/Compose/provider tests are not planned current gates because no runtime
  source/configuration/tests change. Running them cannot prove the future SDK contracts.
- Future commands embedded in recipes/matrix are design deliverables only. Report them
  NOT RUN, with prerequisites and owning implementation unit, never as current passes.

## Optional Developer Checks

Future AWS/Azure/Google Cloud/hosted-provider procedures require separate authorization.
They are not current-ticket or deterministic-local release gates; unrun checks limit
external compatibility claims. No optional live check is requested for this ticket.

## Exit Criteria

- [x] Missing final-document red state is recorded honestly; no fictitious runtime red test.
- [x] All three documents exist and whitespace/local-target checks pass.
- [x] Every prior scenario ID is mapped and detailed normative variants survive.
- [x] All seven ACs pass semantic review; primary/focused compositions and relevant
  negative/lifecycle obligations have explicit planned owners/evidence.
- [x] Future command interfaces, setup, selected versions and result-attribution schema
  are concrete; current evidence is distinguished from planned/unrun checks.
- [x] No product/external-suite/data modifications or unauthorized operations occur.
- [x] No unresolved material contract remains hidden; affected future blockers are explicit.
- [ ] Fresh independent Step 5 review supplies current completion assurance.

## Step Report

The combined pipeline Step Report is returned to the orchestrator after both plan
artifacts are written. This plan does not claim implementation verification.
