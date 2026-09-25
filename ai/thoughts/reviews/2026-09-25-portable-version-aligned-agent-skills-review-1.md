# Portable, Version-Aligned Agent Skills Code Review — Cycle 1

## Scope and Repository State

Independent Step 5 review on 2026-09-25, selected profile `full` (Full 5-Step Pipeline). Read the governing ticket, research, implementation and testing plans completely, both repositories' applicable AGENTS.md files, the Sidecar design lens, framework feature design lens, and shared review/automation instructions. No prior review was read.

Compared working trees to their current HEADs using status, tracked diffs and explicit untracked-file inspection. The supplied baseline was Sidecar HEAD `cda9d0c58c7c2d44f3dafdea92ccbf46a4cb04ae` with only the governing ticket untracked, and framework HEAD `558adbf18cc5166f681d3352d2e3dccc1d4795bb` clean. No staged or separate branch changes were supplied. Current changes are attributable to this ticket; no unrelated work was altered.

Scope includes framework router/installer manifests, renamed six-file Console bundle, documentation metadata/references, Python version/distribution/fixture checks and all synthetic project/source/scenario files; Console validator, archive/smoke/declaration/evaluation consumers, CI and release/onboarding docs; Sidecar skill metadata/resources/client navigation, canonical moved examples, integration/operations/README links, bundle tests, CI and the Java workflow's relocated example path; and pipeline evidence artifacts. Ordinary diff output alone would omit the new source/fixture folders, so those were inspected directly.

The complete independent defect review preceded disposition and plan comparison. No implementation artifacts were edited. This review document is the sole review-context change.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No code, tests, instructions, fixtures, plans or other implementation artifacts changed in this context.

## Acceptance-Criteria and Plan Conformance

Paths prefixed `framework:` resolve under `C:/opendev/code/loomspan-framework`; other paths are Sidecar-relative.

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| 1. Common entry point, focused specialists and replacement | Framework router, instruction-only installer, renamed Console folder and `RuntimeDebuggingSkillName`; active README/CI paths updated; old tracked contracts removed | Independent framework 13-test suite, version check, Console validator/archive/smoke/evaluation suites; actual R10 record compared with router | implemented |
| 2. No-POM direct API Sidecar selection | Installer selects target Sidecar revision, then its direct starter dependency, requiring exact source preflight | Read actual no-POM deployment and Sidecar/framework fixture files; independently compared R1 to final instructions and fixture host results | implemented |
| 3. Embedded Java exact selection and uncertainty | Installer accepts direct literal/single same-POM literal property and stops unresolved/ambiguous resolution | Independent fixture integrity tests; inspected literal/property/chain/inherited/BOM/duplicate/snapshot inputs and R2–R4 observations | implemented |
| 4. Independent SDK identity and compatibility facts | Installer source/version table and explicit supported/unknown/incompatible behavior; router/Sidecar ownership conventions | Inspected synthetic SDK package/manifest/table and scenario overrides; R5–R6 preserve independent 7.8.0/2.3.0/4.5.0 versions, unknown evidence and incompatibility stop | implemented |
| 5. Project default and explicit scope | Host-supported scope required; no silent global/manual-layout fallback | R1 and R7 compared against actual host scopes and requests | implemented |
| 6. Limited updates and truthful outcomes | Complete preflight, existing consent, collision handling and partial/unverifiable reporting; no dependency/runtime edits | R8–R9 matched actual requests, overrides and before/after project hashes; current diff leaves dependency/deployment/runtime sources unchanged | implemented |
| 7. Portable Sidecar direct API bundle | Canonical integration/examples/client resources are internal; explicit exact-version docs dependency | Independent five bundle tests, nine real CLI tests and two Java walkthrough tests pass; moved YAML unchanged from HEAD | implemented |
| 8. Routing and credential boundaries | Router separates evidence/framework/server/SDK ownership; Sidecar keeps management, execution and callback/data authority separate | Compared R10–R11 with actual guidance; independent CLI redirect/redaction and Java allowed/denied workflow pass | implemented |
| 9. Proportionate local evidence | Source/fixture tests plus actual instruction observations, no executable resolver/client matrix | All required relevant automated checks pass in this context; actual replay independently assessed against final digest and actual fixtures | implemented |
| Canonical ownership and no duplication | Console's five moved references and both Sidecar YAML assets compare unchanged to originals; operational guide moved with navigation/deployment wording adjustments | Direct UTF-8 source comparison; archive exact entries/bytes/determinism checks pass | implemented |
| Constant-based downstream consumers | Package, macOS package, smoke and evaluation digest paths derive from updated Console constant | Focused and full Console Go suites pass | safe deviation: unchanged consumers require no unnecessary edits |

### Independent instruction-evidence assessment

The implementation verification and replay records were inspected as evidence, not authority. Read actual `scenarios.json`, `expected.json`, every project/source fixture and the real installer/router/Sidecar guidance. Recomputed installer SHA256 with `Get-FileHash`: `5FD649D58DF049762D898459D1E625D4EA2D5FBBD60B4B0B0E249AFD638D0A1F`, matching the recorded candidate. No subsequent instruction delta makes that evidence stale.

R1–R11 decisions agree with the final instructions and supplied host outcomes. The record distinguishes minimal fixture source closure from real distribution closure, synthetic SDK facts from real compatibility, and proposed host calls from actual host integration. Source preflight precedes writes; old-release and snapshot mismatches stop rather than selecting main/latest. R8's authorized nonconflicting writes after a declined replacement are permitted by the plan and explicitly reported incomplete, not coherent success. R9 does not invent readback, rollback or later operations. R10 establishes routing rather than absent installed-version evidence; R11 establishes the actual guidance's credential boundaries rather than synthetic release runtime behavior. These disclosed boundaries are sufficient for this rewrite and do not leave an ambiguous required case needing replay. No real installation or service call was made for this assessment.

## Active Project Guardrails

- Development replacement policy and both design lenses: obsolete tracked bootstrap/Console contracts are replaced without aliases, adapters or migration machinery. Sources stay with owners; metadata is minimal; existing host management and packaging are reused. No data reset is required or performed.
- Framework supported surface and sole SPI: no production Java/API/SPI/configuration/persistence contract change. Sidecar's Java delta is only the canonical fixture path; public-surface restrictions remain intact. No internal framework imports or new Java skills were added.
- Identity and publication boundaries: relocated operational guidance preserves JWT ownership, original-token passthrough, independent callback authorization, live management authority, exact draft/lease/base checks and explicit publication. The local integration test still verifies both publication paths and allowed/denied callbacks.
- Lifecycle/diagnostics: runtime, concurrency, shutdown, retention and diagnostic semantics are preserved in the guide relocation. No new resources or lifecycle implementation were introduced. Test servers use local fixtures and close their resources.
- Console evidence/security: the renamed instruction body preserves read-only MCP behavior and treatment of returned content as untrusted. All five references are unchanged. Existing linked-file, exact-package and credential-content validation remains active; added component metadata validation rejects wrong/extra markers.
- Independent component/release policy: unchanged Sidecar beta.1-SNAPSHOT, framework dependency beta.5-SNAPSHOT and framework source beta.6-SNAPSHOT. No framework rebuild/install, dependency upgrade, tag, publication or deployment operation. Old release availability is not manufactured.
- Security/privacy inspection found no added real credentials, authorization expansion or new network execution path. Fixture tokens and compatibility facts remain explicitly synthetic. Performance risk is bounded to small local bundle/fixture scans; no new production workload or persistence state exists.
- Full profile remains appropriate for the author-facing cross-component distribution change. Required verification and independent review were retained.

## Open Questions and Assumptions

None affecting completion. Native host capabilities and future published exact sources are intentionally not inferred from local fixtures.

## Verification Results

All following commands were run independently in this review context on the current working tree. Go reported cached successful results; source-sensitive Python, Java and official skill checks ran successfully. No test failure attributable to the ticket occurred.

| Directory | Result and exact command | Evidence |
| --- | --- | --- |
| Sidecar | PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` | 5 tests; detached client with spaced path/unrelated cwd, resource/metadata negatives, actual symlink rejection with no skip, canonical examples |
| Sidecar | PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` | 9 loopback client tests |
| Sidecar | PASS — `.\mvnw.cmd -B -ntp test '-Dtest=RemoteAuthoringWorkflowIntegrationTest'` | 2 tests, zero failures/errors/skips, BUILD SUCCESS; 47.313 seconds total |
| Framework | PASS — `python -m unittest discover -s scripts/tests -v` | 13 tests; version mutations only in temporary repositories |
| Framework | PASS — `python scripts/loomspan_version.py check` | All four framework-owned markers consistent at unchanged beta.6-SNAPSHOT |
| Framework Console | PASS — `go test ./internal/agentskills ./internal/buildtool ./internal/agenteval` | Focused validator/distribution/evaluation coverage |
| Framework Console | PASS — `go test ./...` | Full related Go suite |
| Framework Console | PASS — `uv run --frozen --project skills-ref-validation skills-ref validate ./agent-skills/loomspan-console` | Official pinned validation |
| Framework Console | PASS — `uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan-docs` | Official pinned validation |
| Framework Console | PASS — `uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan` | Official pinned validation |
| Framework Console | PASS — `uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan-install` | Official pinned validation |
| Framework Console | PASS — `uv run --frozen --project skills-ref-validation skills-ref validate ../../loomspan-sidecar/agent-skills/loomspan-sidecar-authoring` | Official pinned validation |
| Both repositories | PASS — `git diff --check` | No whitespace errors |
| Framework | PASS — `Get-FileHash agent-skills/loomspan-install/SKILL.md -Algorithm SHA256` | Exact final candidate matches behavioral replay |

Additional read-only source comparison initially hit Windows default cp1252 decoding on a Unicode reference; rerun with explicit UTF-8 completed and established unchanged moved references/YAML and preserved operational semantics. This was a reviewer inspection-script encoding issue, not a product/test failure. Active-path searches found no obsolete distribution/reference path outside deliberate negative tests and historical material.

NOT RUN — full Maven runtime suites, Docker, native `go run ./internal/buildtool verify`, race certification, live installation and publication: outside the settled instruction/path-only scope. Focused real Sidecar workflow and complete related Go/Python checks cover the affected behavior. Hosted CI remains publication-dependent by repository policy.

## Residual Risks and Optional Developer Checks

- Current Sidecar framework beta.5-SNAPSHOT pin cannot be satisfied by beta.6-SNAPSHOT main, and old beta.5 sources lack the new family. Instructions/docs correctly fail exact-source preflight. This review does not establish a presently published installable combination.
- Once exact new-family released sources exist, a developer-selected real host can verify native discovery, scope and refresh behavior. This is nonblocking follow-up; simulated host outcomes do not certify a native host.
- Future SDK owners must provide real released source mappings and compatibility facts. Synthetic SDK fixtures establish selection behavior only.

## Disposition

`clean` — full independent review completed; no actionable findings, no implementation-artifact changes, and sufficient independent verification. Only this review artifact was written.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-09-25-portable-version-aligned-agent-skills-review-1.md
SUMMARY: Independently reviewed all ticket changes across Sidecar and framework, including untracked bundles and fixtures. Required automated checks pass and final-candidate behavioral evidence matches the actual sources. No actionable findings or implementation edits.
DECISIONS:
  - No additional replay required: source digest and supplied scenario outcomes match, with synthetic/native-host limitations preserved.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — all exact commands in Verification Results above.
OPTIONAL_DEVELOPER_CHECKS:
  - Verify native host discovery/scope/refresh after exact new-family released sources exist; real SDK compatibility remains SDK-owner work.
REVIEW_RESULT: clean
NEXT: Complete the pipeline report with these independent results and explicit availability/host limitations.
