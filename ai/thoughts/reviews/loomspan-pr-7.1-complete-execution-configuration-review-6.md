# PR 7.1 Code Review — Cycle 6

## Scope and Repository State

Pipeline Step 5, approved Full profile, reviewed independently on 2026-09-27.
Read AGENTS.md, automation/review commands, design lens, revised ticket, research,
implementation plan and testing plan. Prior review documents were not read.

HEAD and origin/main share merge base `87ffc54dd6cb0663f80144693dc7772653c14c4d`.
The ticket is the current working-tree change: 63 tracked changed/deleted files
plus new mode, credential, bundle, startup-store and integration-test files.
There were no staged changes. Review included untracked implementation contents,
configuration, schemas, browser assets, Python client, examples and operations
guidance. Existing audit artifacts were excluded from implementation evidence.

Traced startup binding through real framework initialization; file inspection and
generation mapping; encrypted draft/snapshot transactions; exact validation and
publication; retained resources and handoff; import/export authorization and
ciphertext inventory; Console/PAT/client flows; and recovery/documentation.
Completed the initial review before editing implementation artifacts.

## Findings

No remaining actionable findings after the fix and internal re-review below.

### [P1] Require managed Vertex credentials before database preparation — resolved

- Location: `src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java:122`.
- Scenario: Author a database connection with `driver: gemini`, `vertex-ai: true`,
  project and location, but omit `credentials-json-ref`. The prior candidate
  assembler accepted an empty credential map. Framework provider construction
  passes no explicit Google credential in this case, allowing application-default
  credentials to be used from the deployment.
- Impact: Database publications can depend on ambient provider credentials that
  are neither encrypted in their retained configuration nor frozen by the
  publication. Restart and transfer can adopt another deployment identity. This
  violates the explicit database-only credential authority requirement.
- Evidence: Matching local framework `SpringAiProviderIntegration.gemini`
  supplies credentials only when URI/JSON is present; `LoomspanProperties` permits
  Vertex mode without either. The Google SDK's application-default credential
  path was inspected locally; the installed 1.58.0 class contains that path.
  The new assembly regression failed before the fix with “Expecting code to
  raise a throwable.” No Google credential lookup or live request was executed.
- Fix: Require `credentials-json-ref` for the database Gemini options block
  before framework preparation. Existing reference resolution requires a
  corresponding encrypted managed value. Keep incomplete drafts saveable and
  leave JSON/provider semantic validation to the framework.

## Findings Resolved in This Context

Added the narrow credential policy check in `EffectiveExecutionConfiguration`.
Added `vertexCandidatesRequireExplicitManagedCredentialsBeforeProviderPreparation`
to prove missing references are rejected before provider creation and an explicit
encrypted value reaches the candidate map. Updated operations and bundled
authoring execution guidance to state the Vertex requirement.

The same assembly path covers validation, publication, restart and retained
rollback. File startup does not use this database assembler. Re-reviewed the
complete ticket change and the fix for source fallback, secret disclosure,
proof invalidation, concurrency, resource ownership and unsupported framework
access. No additional actionable finding remained.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1 Complete file authority, startup-only changes and read-only management | Immutable mode, startup branch, separate startup store, shared mutation gate | ConfigurationModeIntegrationTest; FileModeManagementHttpIntegrationTest including Chromium | Implemented |
| AC2 Complete database configuration independent of deployment providers | Binding advisor; explicit candidate map; managed credential endpoints; Vertex guard; Console builder/client | Startup poison tests; candidate regression; editing HTTP/PAT/browser tests; Python client | Implemented |
| AC3 Draft-only changes and exact frozen candidate | Draft revision/lease checks, candidate equality, publication and editing gates | Editing service/HTTP, draft restart, remote authoring and runtime tests | Implemented |
| AC4 Encrypted durable values and safe failures/readback | AES-GCM with authenticated identifier/version; ciphertext tables; reference-only writes and redacted file views | Cipher, repository/draft, HTTP, environment-child and browser tests | Implemented |
| AC5 Complete restart/history/rollback and no automatic mode fallback | Selected DB restore; retained credentials; rollback draft loading; file metadata separate from selection | Runtime recovery, mode switching, credential environment, portability restart and draft-store tests | Implemented |
| AC6 Explicit configuration-only and encrypted portability | Format 3 inventory, captured export and live Edit check, authenticated import before draft replacement | Portability same/wrong/missing key and tamper tests; bundle tests; HTTP/PAT/browser/client paths | Implemented |
| AC7 Captured configuration and resource lifetime | Public complete prepare, generation mapping, retained snapshot protection and framework retirement callback | Root/child/retry correlation tests; REST lifecycle/generation and shutdown suites | Implemented |
| AC8 Supported API, installed snapshot and coherent guidance | Public API/Spring integration, pinned beta.6-SNAPSHOT, updated docs/lens/authoring | Architecture test, full Maven verify, Python guidance/client tests and source inspection | Implemented |
| Proportionate verification | Layered assertions compose the acceptance evidence; the new policy guard is tested without ambient credential discovery | Broad initial suite plus focused post-fix suite | Safe adaptation of illustrative test matrix |

## Active Project Guardrails

- Destructive development replacement retained: no provider override, compatibility
  adapter, old bundle path or migration machinery added. Reset/backup instructions
  remain documentation; no database reset performed.
- Framework remains skill/model validation and execution authority. The fix is
  a database credential-source policy check, using Sidecar types only. ArchUnit
  passed; no Java skills, framework bean replacement or internal imports added.
- One admission owner and durable handoff correlation remain intact; captured
  work retains generation resources through physical completion. Existing public
  shutdown/lifecycle tests passed in the broad suite.
- JWT execution identity, management/PAT separation, private drafts, exact grants,
  live permission checks and user-selected deployment workflow remain intact.
- Sidecar/framework versions are unchanged. Used the developer-installed snapshot
  and matching local documentation. No framework rebuild, release or hosted CI.
- Selected Full profile remains appropriate for the persisted/security/lifecycle
  scope. The fix satisfies the settled no-fallback requirement without a new
  extension contract or product decision.

## Open Questions and Assumptions

None requiring a developer decision. Normal snapshot-installation workflow is
the dependency authority; no artifact/source-revision verification was required.

## Verification Results

All commands ran from `C:/opendev/code/loomspan-sidecar`. To isolate editor-written
diagnostic classes, `.codex-review6-pom.xml` copied `pom.xml` and added only
`<directory>${project.basedir}/target/review6</directory>` under `<build>`.
It was removed after verification. Logs remain under ignored `target/`.

- PASS — `.\mvnw.cmd -f .codex-review6-pom.xml -B -ntp '-Dtest=ConfigurationModeIntegrationTest,EffectiveExecutionConfigurationTest,CredentialPortabilityIntegrationTest,SupportedLoomspanApiArchitectureTest' test`
  — initial focused run: 15 tests, no failures/errors/skips; `target/review6-focused.log`.
- PASS — `.\mvnw.cmd -f .codex-review6-pom.xml -B -ntp verify`
  — initial reviewed implementation: 254 tests, zero failures/errors, 3 gated
  production Compose skips; packaging passed; `target/review6-verify.log`.
- FAIL (expected red) — `.\mvnw.cmd -f .codex-review6-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest#vertexCandidatesRequireExplicitManagedCredentialsBeforeProviderPreparation' test`
  — 1 test failed because missing managed Vertex credentials were accepted;
  `target/review6-vertex-red.log`.
- PASS — `.\mvnw.cmd -f .codex-review6-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,RuntimeConfigurationIntegrationTest,RuntimeConfigurationRecoveryIntegrationTest,ConfigurationModeIntegrationTest,CredentialPortabilityIntegrationTest,SupportedLoomspanApiArchitectureTest' test`
  — final fix: 37 tests, zero failures/errors/skips; `target/review6-postfix.log`.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'`
  — 11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_*.py'`
  — 7 tests; the mocked shutdown timeout diagnostic is intentional fixture output.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py'`
  — 6 tests after the authoring guidance edit.
- PASS — `python -m py_compile scripts/verify-production.py`.
- PASS — `git -c core.safecrlf=false diff --check`.
- NOT RUN — production Compose verifier, live providers, deployed-data operations,
  framework release/rebuild and hosted CI: excluded from routine safe verification.

The full suite preceded the narrow Vertex fix. Post-fix checks cover its affected
candidate, startup, recovery, portability and architecture paths; unchanged
browser/client/lifecycle checks were not repeated merely for a second full run.

## Residual Risks and Optional Developer Checks

Three production Compose browser cases remain explicitly gated and unrun. Local
Chromium authoring/read-only/import tests executed. On a disposable installation,
optionally assess file-mode inspection and wrong-key replacement wording and
exercise consistent database backup plus separately provisioned key recovery.
These observations do not substitute for automated tests. A fresh reviewer must
review the changes made here before pipeline completion.

## Disposition

`fixes-applied`: one confirmed P1 credential-source defect fixed; no remaining
actionable findings in this context. A fresh Step 5 context is required.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/loomspan-pr-7.1-complete-execution-configuration-review-6.md
  - src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java
  - src/test/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfigurationTest.java
  - docs/operations.md
  - agent-skills/loomspan-sidecar-authoring/references/execution-configuration.md
SUMMARY: Completed independent review and fixed database Vertex application-default credential fallback. The regression failed before the fix and passed afterward. No remaining actionable finding was identified in the internal re-review.
DECISIONS:
  - Enforce the existing complete managed-credential requirement before provider preparation; retain framework semantic validation and the file-mode behavior.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — initial full Maven verify: 254 tests, zero failures/errors, 3 gated skips.
  - FAIL — expected pre-fix Vertex regression: 1 assertion failure.
  - PASS — final focused Maven suite: 37 tests, zero failures/errors/skips.
  - PASS — Python client 11, scripts 7, final guidance 6; Python syntax and diff checks.
OPTIONAL_DEVELOPER_CHECKS:
  - Disposable mode/import wording and separately provisioned key plus database-backup recovery checks.
REVIEW_RESULT: fixes-applied
NEXT: Launch a fresh Step 5 review context with review number 7.
