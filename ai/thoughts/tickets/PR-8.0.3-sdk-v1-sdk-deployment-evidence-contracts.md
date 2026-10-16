# PR #8.0.3 — Complete SDK v1 API, reference deployment and acceptance contracts

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Finish Phase 0 with an internally consistent reference design, concrete SDK outcome
contracts, reproducible planned deployment/test recipes and a traceable acceptance
matrix. Implementation owners must know what to build and prove without treating
unrun tests, proposed settings or Framework-only evidence as completed SDK support.

## Requirements

- Select actual Java, Spring and runtime versions, blocking/async APIs and
  servlet/reactive scope consistent with the preceding identity/trust contracts.
  Preserve a standalone HTTP core without embedded Loomspan or required Spring Boot,
  SDK ownership in `sdks/java/`, independent Maven build/version/release and the root
  Sidecar build. Resolve test-suite runtime differences before compatibility claims.
- Define discovery/descriptors, JSON-object submission, immediate accepted execution
  handles, status by ID, bounded polling/wait, resource ownership/cleanup and reusable
  connections. Specify typed envelopes, unchanged result text, snapshot IDs,
  validation/problem details and available diagnostic history. Optional JSON/typed
  and TypeSafe conversion failures retain execution/result context without reruns.
  Define safe timing/identifier observability without default payload/credential
  logging or complete/durable-trace promises.
- Define explicit API rejection, demonstrable pre-send failure, acceptance unknown,
  transport/protocol/polling errors, timeout/interruption and terminal execution
  failure even with HTTP 200. Lost/unusable acceptance responses may leave work
  running without a known ID; proxy errors alone do not establish rejection.
  Preserve reliable IDs/details. Specify retryable safe status reads, bounded backoff
  and total wait deadlines separately from HTTP timeouts. Wait termination retains
  IDs for resumed lookup and neither cancels nor resubmits. Preserve status-404 ambiguity.
- Prohibit automatic submission and callback retries in SDK/Sidecar clients and
  reference proxies, including refresh/recovery paths; document equivalent external
  proxy obligations. No blind-retry guidance, server submission deduplication,
  exactly-once guarantee or guaranteed lost-ID recovery. A callback effect can commit
  before response loss or a later failure; execution failure implies no rollback.
  Specify a planned synthetic fixture demonstrating application-owned stable business
  operation IDs, atomic duplicate handling and reconciliation, separate from transport
  retry prevention and deliberate repeated skill actions.
- Select concrete isolated and trusted-group Compose recipes using the established
  bootstrap/lifecycle contract. Pin Docker/Compose, Caddy and local provider versions
  and prerequisites, starting from roadmap Caddy 2.10.2; explain any baseline change.
  Include protected direct HTTP, direct HTTPS and both client-Caddy and SDK-native
  mTLS invocation paths with independently selected callback ingress. No callback
  listener/TLS is needed for invocation/polling alone; retain client Caddy on its
  invocation path and omit it on the native path. Optional JWKS hosting is separate.
- State per-hop transport guarantees, verified chain/hostname checks, no downgrade,
  explicit workload membership and Sidecar-specific callback peer policy where
  required. Broad CA trust, co-location or unpublished ports alone do not establish
  membership/isolation. Prevent inbound bypass and borrowing outbound proxy identity;
  separate management/browser ingress without execution bypass. Keep proxy peer
  certificate interpretation outside Sidecar Java and callback server TLS in the
  application ingress/server, not a new SDK server. Group mTLS retains Sidecar Caddy.
- Preserve all four credential strategies, existing customer authentication and
  authorization, unchanged-token reusable-credential limits, restricted recipients,
  redirect controls and infrastructure secret protections in each recipe. No new
  business-data sanitization; caller/callback content and existing public diagnostics
  retain fidelity. Generated signing keys do not substitute for TLS provisioning.
- Expand every roadmap acceptance obligation into named scenarios, not one happy-path
  row per family. Map each to its source, phase, owning repository, named owning unit
  and intended test/recipe/evidence location. Identify units as planned until their
  dated implementation tickets exist; do not invent completed tickets or tracker IDs.
  Consolidate the two preceding tickets' scenarios and identify cross-repository
  dependencies without creating implementation tickets for unresolved contracts.
- Primary evidence families are HTTP/HTTP, HTTPS/HTTPS and mTLS/mTLS across Relay,
  Exchange, Spring-issued and Custom identity. Both mTLS client paths cover isolated
  and group deployments and all strategies. Add focused mixed transport, including
  mTLS invocation/protected HTTP callback, and no-callback/resumed-polling across
  all four strategies and both mTLS paths. Map all applicable negative, authorization,
  retry/uncertainty, lifecycle, export and management scenarios; justify shared
  component evidence without replacing real integration for promised compositions.
- Define exact planned local commands/tiers, prerequisites and deterministic fixtures:
  no paid model calls or hosted-service dependencies, but real JWT/JWKS, local Exchange
  server, TLS/mTLS and SDK/Sidecar/application/proxy boundaries. Dependency/image
  acquisition is setup, not an offline-installation promise. Focused SDK/Sidecar tests
  belong here; complex Compose/topology/fault-injection evidence belongs in
  `C:/opendev/code/loomspan-sidecar-test-suite`. Record versions/revisions,
  configuration, command and result for evidence; Framework tests alone do not prove
  Sidecar behavior. Each later implementation unit owns tests and documentation.
- Define test-suite extension boundaries and the required project-agreement update
  when implementation starts. Preserve existing embedded Java and Python/Sidecar
  examples, fixtures and business behavior while adding a deliberate Java-to-Sidecar
  path. Name new work SDK v1, distinct from historical Phase 1/2/3 labels. Other
  languages, new Python functionality, management-authoring SDK, code generation,
  streaming and server cancellation are not included v1 deliverables.
- Identify AWS, Azure, Google Cloud and named hosted-provider recipes with explicit
  separately authorized manual verification procedures/status. Prefer standard
  files/keystores/SSL bundles; cloud-specific SDK code needs a demonstrated unmet
  requirement, and non-exportable signers need an explicit integration contract.
  Local material tests do not establish cloud issuance/delivery/renewal or commercial
  provider compatibility. Unrun external checks limit claims, not local release gates.
- Reconcile reference contracts, design lens and related plans; identify demonstrated
  Sidecar gaps and necessary public Framework changes in their owning repositories.
  Retain `ai.loomspan.api`-only use, authorized `RestSkillHandler`, ArchUnit exclusions
  for internal/autoconfigure packages, no new SPI or internal-bean replacement, and
  no Sidecar Java `@SkillMethod` skills. Preserve documented namespace ownership.

## Acceptance criteria

- [x] A consistent reference design selects runtime/API/web scope and independent
  SDK packaging, reconciles sample-suite baseline differences and defines all eight
  roadmap SDK-functionality areas with concrete API/configuration/error contracts.
- [x] Outcome tables and named fault scenarios distinguish acceptance uncertainty,
  explicit rejection, proven pre-send failure, retained-ID polling/wait outcomes,
  HTTP-200 execution failure and conversion failure. Planned attempt-count evidence
  covers response loss through clients/proxies without replay; bounded reads and
  the application duplicate-protection fixture preserve the stated limits.
- [x] Versioned isolated/group recipes specify both mTLS paths, direct and mixed
  transports, no-callback operation, independent ingress, protected local boundaries,
  explicit peers and lifecycle responsibilities without default manual key/certificate
  work or weakened trust. Per-hop guarantees and passthrough/data limits are explicit.
- [x] A scenario-level matrix traces every roadmap acceptance obligation, including
  preceding security/trust scenarios, to phase, repository, named owning unit and
  intended evidence location. All primary families/strategies, both isolated/group
  mTLS paths and focused mixed/no-callback cases are represented; omissions or shared
  evidence have explicit justification and no required scenario is silently dropped.
- [x] Planned commands/tiers/prerequisites and evidence attribution are concrete;
  local deterministic release gates use real security boundaries. Test-suite
  extension/project-agreement requirements preserve existing examples and explicitly
  separate SDK v1 work from historical phase labels and excluded SDK features.
- [x] Cloud/provider recipes have named manual checks and honest unverified status;
  local evidence cannot be misread as external compatibility. Phase 4 obligations
  include clean-consumer packaging, complete local matrix, closure of temporary
  breakage and independent security review, followed by authorized release checks.
- [x] Phase 0 closes only with resolved material contracts, consistent ownership,
  versions and dependencies. Lens/related plans are reconciled; missing public
  Framework contracts and required developer decisions are explicit blockers to
  affected implementation planning rather than permission to weaken requirements.

## Context

Roadmap work item: GitHub PR #8. Ticket identifier: `PR-8.0.3`.
SDK v1 tickets use `PR-8.[phase].[ticket]` identifiers.

Depends on completed [identity/callback](PR-8.0.1-sdk-v1-identity-callback-contracts.md)
and [trust/bootstrap/lifecycle](PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md)
contracts. Integrate their decisions rather than independently redesign them.
Normative inputs are [Phase 0 and its ownership table](../phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md),
the [complete roadmap](../phases/2026-10-11-java-spring-sdk-roadmap.md) and
[security research](../phases/2026-10-11-established-security-architectures.md).
Use [Phase 1](../phases/2026-10-15-sdk-v1-phase-1-isolated-integration.md),
[Phase 2](../phases/2026-10-15-sdk-v1-phase-2-trusted-group.md),
[Phase 3](../phases/2026-10-15-sdk-v1-phase-3-lifecycle-deployment.md) and
[Phase 4](../phases/2026-10-15-sdk-v1-phase-4-release-readiness.md) for delivery ownership.
Their full detailed acceptance scenarios remain normative.

Sidecar development is `1.0.0-beta.4-SNAPSHOT`, next planned release/tag
`1.0.0-beta.4`/`v1.0.0-beta.4`; SDK and Framework versions are independent.
Keep `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.8` pinned to the tested
release and consult local Framework tag `v1.0.0-beta.8`, not stale installed docs.
Future Framework snapshot work uses the developer-installed artifact aligned with
the local checkout, without extra artifact/source verification or normal Sidecar
Framework rebuilds. Prove affected integration locally; a required new Framework
release must reach Maven Central before final Sidecar dependency/build/test/commit
checks. Hosted checks follow publication/sync before release; never overwrite releases.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** SDK API/outcome, transport and cross-repository evidence design has
  material unresolved choices; the final Phase 0 contract needs discovery and review.
- **Reassessment triggers:** Reassess if approved reference contracts and a complete
  traceable matrix already exist; formatting an existing matrix alone is narrower work.

## Pipeline notes

- Deliver reference documentation and planned recipes/scenarios, not production
  code or the later test-suite implementation. Review completeness/consistency;
  label unrun commands and scenarios planned, never passing support evidence.
  Record unresolved developer decisions and do not create dependent implementation
  tickets until their material contracts are settled.
- The developer commits after each ticket and syncs only after all phases. No commit,
  push, release or external compatibility run is authorized here; hosted CI is not
  an intermediate gate. No code/build breakage is intended by this design ticket.
  Later tickets permitting intermediate breakage must record impact, feasible checks
  and a named restoring unit; all deferred breakage must close before final readiness.

## Execution notes

### Approved route and implementation boundary

The developer approved the **Full 5-Step Pipeline**. Step 4 implements reference
documentation only. The research and two plan files already present at Step 4 entry
are this pipeline's artifacts; no unrelated developer changes were found. No product
source/test/build/configuration, external-suite agreement/runtime/fixture, deployment
data or installed dependency changed. No commit, push, release, hosted CI, container,
certificate issuance, paid-model or external compatibility operation ran.

Created the API reference, deployment recipes and consolidated matrix; reconciled
the roadmap, Phase 0–4 and prior reference/scenario handoffs. The design lens was
reviewed and already agrees, so it is unchanged. No development-data reset is needed.

Material decisions: independent SDK beta.1-SNAPSHOT modules on Java 21/Boot 4.1.0;
core has no Boot runtime/embedded Framework requirement; blocking and async operations
share typed outcomes and retained context. Apache HTTP transport explicitly disables
replay mechanisms; only finite GET retries are permitted. A same-origin safe Location
can retain a reliable ID after a damaged acceptance without establishing successful
acceptance. UID/cgroup firewall rules protect both local proxy ownership and plaintext
backends; sharing loopback does not authorize mTLS bypass. Callback ingress and JWKS
hosting stay independent. Suite beta.9 embedded evidence remains distinct from the
new beta.8 SDK v1 lane. All new runtime APIs/settings/assets/tests remain planned.

### Documentation verification results and exact commands

Working directory for all following scripts: `C:/opendev/code/loomspan-sidecar`.
These checks execute documentation inspection only; no future command embedded in
the deployment/matrix was executed.

Red-state inspection before creating the final references returned `exists=False`
for all three paths (expected missing-document state, not a claimed runtime test):

```powershell
$sdkV1Docs = @('2026-10-15-sdk-v1-api-reference.md','2026-10-15-sdk-v1-deployment-recipes.md','2026-10-15-sdk-v1-acceptance-matrix.md')
foreach ($sdkV1Doc in $sdkV1Docs) { $sdkV1Path = Join-Path 'ai/thoughts/phases' $sdkV1Doc; Write-Output "$sdkV1Path exists=$(Test-Path -LiteralPath $sdkV1Path)" }
```

PASS, exit 0 — structural/content/source-preservation check below. Actual output:
`PRIMARY=24`, `MIXED=32`, `OUTBOUND=16`, then `PASS: 3 substantive documents,
108 uniquely mapped unchanged prior assertions, 72 compositions, 8 functionality
areas, 11 outcomes, pinned recipe inventory`. All 57 identity/callback and 51
trust/lifecycle full source assertion rows match HEAD byte-for-line text; each maps
exactly once. This does not claim their future runtime assertions pass.

```powershell
$sdkDocs=@('2026-10-15-sdk-v1-api-reference.md','2026-10-15-sdk-v1-deployment-recipes.md','2026-10-15-sdk-v1-acceptance-matrix.md')
foreach($sdkDoc in $sdkDocs){$sdkPath=Join-Path 'ai/thoughts/phases' $sdkDoc;if(!(Test-Path $sdkPath) -or (Get-Content $sdkPath).Count -lt 50){throw "Missing/substantive document $sdkPath"};if([regex]::IsMatch((Get-Content -Raw $sdkPath),'(?m)[\t ]+$')){throw "Trailing whitespace $sdkPath"}}
$sdkSources=@('ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md','ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md')
$sdkIds=@($sdkSources | ForEach-Object {[regex]::Matches((Get-Content -Raw $_),'(?m)^\| ([A-Z]+-\d{2})\b') | ForEach-Object {$_.Groups[1].Value}})
$sdkMatrix=Get-Content -Raw ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md
foreach($sdkId in $sdkIds){if([regex]::Matches($sdkMatrix,'(?m)^\| '+[regex]::Escape($sdkId)+' \|').Count -ne 1){throw "Not uniquely mapped $sdkId"}}
foreach($sdkSource in $sdkSources){$sdkBefore=@((git show "HEAD:$sdkSource") | Where-Object {$_ -match '^\| [A-Z]+-\d{2}\b'});$sdkAfter=@(Get-Content $sdkSource | Where-Object {$_ -match '^\| [A-Z]+-\d{2}\b'});if(Compare-Object $sdkBefore $sdkAfter){throw "Changed normative assertion $sdkSource"}}
foreach($sdkPair in @(@('PRIMARY',24),@('MIXED',32),@('OUTBOUND',16))){$sdkCount=[regex]::Matches($sdkMatrix,'(?m)^\| '+$sdkPair[0]+'-[A-Z-]+ \|').Count;if($sdkCount -ne $sdkPair[1]){throw "Wrong composition count $($sdkPair[0])"};Write-Output "$($sdkPair[0])=$sdkCount"}
$sdkApi=Get-Content -Raw ai/thoughts/phases/2026-10-15-sdk-v1-api-reference.md
foreach($sdkArea in 1..8){if($sdkApi -notmatch ('(?m)^## '+$sdkArea+'\. ')){throw "Missing functionality $sdkArea"}}
foreach($sdkOutcome in @('NotSubmitted','SubmissionRejected','AcceptanceUnknown','PollingFailed','ProtocolFailed','StatusDenied','StatusUnavailable','WaitTimedOut','WaitInterrupted','ExecutionFailed','ResultConversionFailed')){if(!$sdkApi.Contains($sdkOutcome)){throw "Missing outcome $sdkOutcome"}}
$sdkRecipes=Get-Content -Raw ai/thoughts/phases/2026-10-15-sdk-v1-deployment-recipes.md
foreach($sdkToken in @('28.5.1','2.40.2','2.10.2','0.28.4','0.28.7','26.4.0','21.0.8+9','3.9.11','3.12.10','mtls-proxy','mtls-native','AWS','Azure','Google Cloud','Auth0','Okta','Entra','PLANNED, NOT RUN','UNVERIFIED')){if(!$sdkRecipes.Contains($sdkToken)){throw "Missing recipe contract $sdkToken"}}
Write-Output "PASS: 3 substantive documents, $($sdkIds.Count) uniquely mapped unchanged prior assertions, 72 compositions, 8 functionality areas, 11 outcomes, pinned recipe inventory"
git -c core.autocrlf=false diff --check
if($LASTEXITCODE -ne 0){throw 'Whitespace failure'}
```

PASS, exit 0 — exact local-link check below reported
`PASS markdownLocalTargetsResolve: 16 documents`. Future assets are code-form
planned paths rather than broken links. This checks file targets, not heading anchors.

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
Write-Output "PASS markdownLocalTargetsResolve: $($sdkV1LinkFiles.Count) documents"
```

PASS, exit 0 — exact scope check below reported `PASS ticketChangeBoundary: only
ai/thoughts documentation; external suite clean`. Tracked diff inspection was
supplemented by explicit review of all three untracked new documents and the
pre-existing pipeline plans/research. `git diff --check` and
`git -c core.autocrlf=false diff --check` both passed; Git's LF/CRLF advisory is
not a whitespace defect. No private keys, credentials or sensitive data entered
the diff; fixtures/configuration in these documents are symbolic planned values.

```powershell
git status --short
git -c core.autocrlf=false diff --name-only
git -c core.autocrlf=false diff --stat
$sdkChanged=@(git diff --name-only) + @(git ls-files --others --exclude-standard)
foreach($sdkPath in $sdkChanged){if($sdkPath -notlike 'ai/thoughts/*'){throw "Unexpected non-document change $sdkPath"}}
$sdkSuiteStatus=@(git -C C:/opendev/code/loomspan-sidecar-test-suite status --short)
if($sdkSuiteStatus.Count){throw ('External suite is dirty: '+($sdkSuiteStatus -join ', '))}
Write-Output 'PASS ticketChangeBoundary: only ai/thoughts documentation; external suite clean'
```

Verification-harness correction: an exploratory
`git -c core.autocrlf=false diff --no-index --check -- NUL ai/thoughts/phases/2026-10-15-sdk-v1-api-reference.md`
returned exit 1 without whitespace output because no-index compares different files.
It was not treated as a pass or a document defect. The explicit untracked-file
trailing-whitespace check above plus tracked diff check replaced that unsuitable
exit-code assertion and passed. A final diff check caught an extra blank EOF line
introduced while appending these notes; it was removed and the final check rerun.

### Acceptance review mapping

Step 4 reviewed the complete seven criteria semantically against the normative
roadmap/prior scenarios, in addition to the structural checks:

| Criterion | Documentation and concrete reviewed evidence |
| --- | --- |
| AC1 | API runtime/module table and eight numbered areas: independent builds, Java/Boot/servlet scope, explicit blocking/async/credential capture signatures, response DTOs, configuration mapping, optional TypeSafe and separately attributed suite baseline |
| AC2 | API outcome table and reliable-ID rules; OUTCOME-01–06 and EFFECT-01–02 retain rejection/pre-send/unknown/proxy/read/404/deadline/HTTP-200 failure/conversion distinctions; planned per-layer counters and transactional business ledger include response loss, concurrency, conflicting digest and reconciliation |
| AC3 | Deployment service/port/volume/hop tables, per-UID backend/proxy protection, explicit SAN/EKU/chain/member rules, management deny routing, bootstrap/material gates; 24 primary + 32 mixed + 16 outbound compositions retain all four strategies, both mTLS paths, both topologies and independent callbacks |
| AC4 | Every prior ID has source/phase/repository/named planned unit/location; full source assertion rows unchanged. New API/effect/build/manual/readiness rows and every composition have source/phase/owner/location. Both callback arrangements and source negative/lifecycle variants remain mandatory; component sharing rationale never replaces composed proof |
| AC5 | Deployment commands specify cwd/assets/flags/setup/tier/evidence fields and deterministic real JWT/JWKS/Keycloak/TLS boundaries; matrix unit registry assigns focused versus complex-suite evidence. Suite agreement update is a future implementation prerequisite and existing assets remain untouched |
| AC6 | Named AWS/Azure/Google Cloud/Auth0/Okta/Entra manual procedures are explicitly UNVERIFIED/NOT RUN and separately authorized; local material evidence does not imply hosted compatibility. Phase 4/readiness rows require clean consumers, complete local matrix, restored breakage, independent security review and separately authorized release checks |
| AC7 | Roadmap/Phase 0–4/preceding handoffs link consistent final contracts; static trust/queue/TLS/no-SDK gaps have owning units. No demonstrated missing new Framework public contract; an unavailable required capability blocks affected work in Framework, never authorizes internals, new SPI, bean replacement or weaker security. Design lens unchanged |

No unresolved material design choice is knowingly hidden. Future implementation must
prove the selected runtime's callback isolation, public-only TypeSafe feasibility,
atomic state/publication and connection cutoffs. A demonstrated unavailable public
capability blocks affected planning/sign-off and requires a recorded owning-repository
contract correction. These are explicit implementation gates, not existing support.

NOT RUN: `mvn -f sdks/java/pom.xml verify`, `mvn verify`, all planned
`scripts/sdk_v1.py`/Compose/clean-consumer commands and cloud/provider procedures.
No runtime source changes, SDK/runner assets or live authorization exist for those
checks in this ticket; they cannot establish current implementation evidence.
Separately authorized future manual external checks limit those compatibility
claims and are not deterministic local release gates.

Step 4 implementation verification is complete. Fresh independent Step 5 review
is still required before final pipeline completion.
