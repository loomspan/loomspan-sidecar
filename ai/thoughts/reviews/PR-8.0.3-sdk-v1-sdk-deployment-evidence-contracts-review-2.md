# PR-8.0.3 Code Review — Cycle 2

## Scope and Repository State

Pipeline Step 5, Full 5-Step Pipeline, fresh independent context. Reconstructed
the change from current staged, unstaged and untracked scope against HEAD
`a64446841ee38cdce87772ba9fcfab8c4e5c77e5`. No staged change exists. Scope is the
three new final reference documents, roadmap/Phase 0–4 and earlier reference/scenario
reconciliation, ticket execution record, research and both plans. No unrelated
developer change was identified; the external suite is clean. Prior review
documents and their conclusions were not read.

Read the ticket, research, implementation/testing plans, repository instructions,
design lens and connected normative requirements. Reviewed the API, deployment
and matrix artifacts beyond diff hunks, including negative/source variants and
the distinction between design completion and runtime evidence. Inspected current
Sidecar acceptance/status/problem/REST transport and architecture sources, root
versions, matching Framework beta.8 public boundary and suite agreement/baseline.

The ticket authorizes reference documents and planned recipes, so no runtime suite,
external service, deployment or release operation is appropriate current evidence.
Only this review artifact was written in this context.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation artifact was changed.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Documentation evidence | Executed verification / semantic evidence | Result |
| --- | --- | --- | --- |
| AC1 runtime/API/build/eight areas | API reference runtime/module table and sections 1–8; explicit standalone core, optional servlet/TypeSafe modules and independent versions | Eight-area check; root Java 21/Boot 4.1.0/beta.8 compared with suite's separately attributed snapshot lane; signatures, DTOs, credential capture and ownership reviewed | implemented (reference contract) |
| AC2 uncertainty/rejection/IDs/wait/effects | API outcome/provenance/Location rules, SubmissionFuture and WaitFuture observations; OUTCOME-01–06, EFFECT-01–02 | Reviewed direct versus proxy-origin rejection, valid-body/foreign-Location handling, conflicting reliable IDs, cancelled-future context, monotonic deadline/read retry, 404 ambiguity, HTTP-200 FAILED and conversion preservation; attempt/effect counters and atomic ledger assertions are concrete planned evidence | implemented (reference contract) |
| AC3 protected transport/deployment | Deployment service/hop/namespace/UID/material and group-leaf policy; primary/mixed/outbound inventory | Exact required composition set verified independently; checked backend/proxy UID denial, management closed routes, native/proxy placement, independent callbacks/JWKS, automatic bootstrap and per-generation cutoffs. Caddy tagged standard modules verified from official source | implemented (planned recipes) |
| AC4 complete source and composition mapping | 108 individually mapped prior IDs with full normative source assertions; new API/outcome/effect/build/material/manual/readiness rows | All 108 original full assertion rows unchanged against HEAD and mapped once; exact 24 primary + 32 mixed + 16 outbound tuples checked. Both callback arrangements, token negatives at both receivers, R/E/P indirect paths, export/state and trust lifecycle variants remain mandatory | implemented (planned matrix) |
| AC5 deterministic commands/tiers/attribution/suite boundary | Deployment exact commands, fixture assets, evidence schema, lane flags and agreement prerequisite; matrix unit/test registry | Local paths resolve; new assets remain expressly planned; source/scope inspection confirms no product/external-suite edits. Reviewed real JWT/JWKS/Keycloak/TLS/fault boundary requirements and no hosted/paid routine dependency | implemented (planned evidence contract) |
| AC6 manual external claims/readiness | Named AWS/Azure/Google Cloud/Auth0/Okta/Entra procedures; READY-01/02; Phase 4 | Explicit UNVERIFIED/NOT RUN and separate authority retained; local material does not prove hosted support; clean consumers, complete local variants, restored breakage and independent security review precede authorized publication | implemented (reference contract) |
| AC7 closure/owners/public boundary | Roadmap/phase/prior-reference handoffs and owning-gap register | Reviewed consistency with design lens, closed beta.8 public surface, documented namespaces and independent release policy; callback/TypeSafe/atomic-publication/connection-cutoff implementation gates cannot weaken requirements | implemented (reference contract) |

Required runtime proof remains planned throughout these results. No row above
asserts a passing SDK, Compose, Framework integration or external compatibility run.
Sharing component permutations is explicitly justified while each promised real
composition retains its own integration assertions; counts do not substitute for
those source variants.

## Active Project Guardrails

- Direct replacement policy and design-lens reference retained; no shim, obsolete
  parallel API, migration, data reset or deployed-data operation introduced.
- Framework execution authority retained; SDK core does not embed Framework or
  require Boot runtime. Optional integration remains public `ai.loomspan.api` only.
- Existing ArchUnit exclusions cover internal/autoconfigure packages and Sidecar
  Java SkillMethod declarations; no new SPI, internal-bean replacement or reflection.
- Framework/Sidecar/SDK/Boot namespaces are distinct. Root build and beta.8 dependency
  remain unchanged; suite snapshot evidence and SDK/Sidecar versions stay independent.
- Identity/credentials remain trusted infrastructure; callback authorization and
  issuer/subject ownership remain independent of workload membership. Exact result
  and business-data fidelity coexist with safe default logging and secret protection.
- Submission/callback replay, origin assumptions, implicit refresh, rollback,
  single-use bearer, cancellation and durable/complete-history guarantees are absent.
- Complex evidence belongs in the suite, focused tests in component repositories;
  the future suite agreement update precedes implementation and preserves old lanes.
- No commit/push/release, hosted CI, live provider or external compatibility operation.
  Full profile remains appropriate to the contract/security/deployment scope.

## Open Questions and Assumptions

None requiring a developer decision for this reference-design ticket. Real callback
isolation, public-only conversion and material/connection lifecycle implementation
must prove the selected contracts; a demonstrated unavailable public capability
blocks affected work and requires owning-repository planning.

## Verification Results

All commands below ran at the Sidecar repository root. LF/CRLF advisories were
observed; they are not whitespace failures.

- PASS — `git status --short`, `git -c core.autocrlf=false diff -- ai/thoughts/phases`,
  `git diff --cached --stat` — tracked/untracked scope reconstructed; no staged edits.
- PASS — reran every PowerShell documentation-check block recorded in the ticket
  using the exact command below. Presence now reports true; structural/source checks
  report 108 unchanged uniquely mapped prior assertions, 72 compositions, eight
  functionality areas, eleven named outcomes; 16 local-link documents resolve;
  changed-file boundary is documentation only and external suite remains clean.

```powershell
$reviewTicket=Get-Content -Raw ai/thoughts/tickets/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md; $reviewBlocks=[regex]::Matches($reviewTicket,'(?s)```powershell\r?\n(.*?)```'); foreach($reviewBlock in $reviewBlocks){ & ([scriptblock]::Create($reviewBlock.Groups[1].Value)); if($LASTEXITCODE -and $LASTEXITCODE -ne 0){throw 'Review documentation check failed'} }; git diff --cached --stat; git diff --check
```

- PASS — independent `exactRequiredCompositionSet` and
  `untrackedAndTrackedWhitespace` PowerShell checks below. Required IDs are
  enumerated by strategy, topology, path and callback, not merely counted.

```powershell
$reviewMatrix=Get-Content ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md; $reviewCompositions=@($reviewMatrix | Where-Object {$_ -match '^\| (PRIMARY|MIXED|OUTBOUND)-'}); $reviewKeys=@{}; foreach($reviewRow in $reviewCompositions){$reviewFields=$reviewRow.Split('|') | ForEach-Object {$_.Trim()}; $reviewKey=$reviewFields[1]; if($reviewKeys.ContainsKey($reviewKey)){throw "Duplicate $reviewKey"}; $reviewKeys[$reviewKey]=$reviewFields }; foreach($reviewStrategy in @('R','E','S','C')){foreach($reviewDirect in @('H','T')){if(!$reviewKeys.ContainsKey("PRIMARY-I-$reviewDirect-$reviewStrategy")){throw 'Missing direct'}};foreach($reviewTopology in @('I','G')){foreach($reviewPath in @('P','N')){foreach($reviewKind in @('PRIMARY','OUTBOUND')){if(!$reviewKeys.ContainsKey("$reviewKind-$reviewTopology-$reviewPath-$reviewStrategy")){throw 'Missing mandatory composition'}};foreach($reviewCallback in @('H','T')){if(!$reviewKeys.ContainsKey("MIXED-$reviewTopology-$reviewPath-$reviewCallback-$reviewStrategy")){throw 'Missing mixed'}}}}}; if($reviewKeys.Count -ne 72){throw 'Unexpected composition'}; Write-Output 'PASS exactRequiredCompositionSet: all required tuples present exactly once'; $reviewChanged=@(git diff --name-only)+@(git ls-files --others --exclude-standard); foreach($reviewFile in $reviewChanged){if($reviewFile -like '*/reviews/*'){continue}; if([regex]::IsMatch((Get-Content -Raw $reviewFile),'(?m)[\t ]+$')){throw "Whitespace $reviewFile"}}; Write-Output 'PASS untrackedAndTrackedWhitespace'
```

- PASS — `git diff --check` — tracked whitespace check.
- PASS — source inspection of official tagged
  [Caddy leaf verifier](https://github.com/caddyserver/caddy/blob/v2.10.2/modules/caddytls/connpolicy.go#L888)
  and [file loader](https://github.com/caddyserver/caddy/blob/v2.10.2/modules/caddytls/leaffileloader.go).
  Confirms standard module availability and exact leaf equality in addition to
  configured CA verification; it is not real-handshake evidence.
- NOT RUN — `mvn -f sdks/java/pom.xml verify`, root `mvn verify`, clean consumers,
  all future `scripts/sdk_v1.py`/Compose commands and external manual procedures.
  No runtime change exists; new SDK/runner assets are absent, and deployments/live
  external checks are outside this ticket's authority. These cannot establish
  present runtime support and are not documentation completion gates.

## Residual Risks and Optional Developer Checks

No runtime compatibility claim is established. Future units must supply the real
matrix, attempts/effects, callback isolation, clean consumers and lifecycle timing
evidence at their named locations. Cloud/provider recipes remain unverified until
separately authorized manual runs; they limit those claims without blocking local
release gates. No optional live check is requested for this ticket.

## Disposition

`clean` — independent review complete, no actionable findings, adequate executed
documentation verification, and no implementation-artifact modifications.
