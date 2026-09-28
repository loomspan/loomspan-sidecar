# PR 7.1 Code Review — Cycle 2

## Scope and Repository State

Fresh pipeline Step 5 under the approved Full 5-Step Pipeline, reviewed September 27, 2026. Read AGENTS.md, the shared protocol, review command, design lens, revised ticket, refreshed research and both plans. Prior review documents were not read. Implementation reports and checked boxes were treated as context, not verification evidence.

The branch is `main`, tracking `origin/main`; their merge base and HEAD are `87ffc54dd6cb0663f80144693dc7772653c14c4d`. There were no staged changes. Scope includes the entire unstaged PR 7.1 diff and untracked production/test files: complete startup modes, bootstrap binding, file inspection and correlation, effective candidates, cipher/storage/schema, bundle format replacement, management services/controllers/pages, browser assets, authoring client, deployment examples, documentation and tests. Existing pipeline/audit artifacts were preserved. No framework source, release version, deployed data, or unrelated developer work was changed.

Independently traced startup through framework handoff, draft/proof/publication and failure paths, retained credential recovery/rollback, encrypted export capture/import admission, live management authority, file-mode mutation denial, and browser/client inputs. Initial defect review was completed before fixes. Afterwards the affected paths and complete ticket diff were reconsidered against requirements. Public local framework README and `SkillReloader` establish the supported explicit-map preparation and retirement contracts; Sidecar tests establish integration evidence.

## Findings

All findings below were resolved in this context. No actionable finding remains in the final internal re-review.

### [P1] Reject direct provider secrets before invalid YAML can be saved

- Location: `src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java:75` (`assertNoDirectAuthoredCredentials`).
- Scenario: Submit inline/quoted `api-key` YAML with a duplicate root mapping or incomplete closing mapping through ordinary draft save. The line-start regex misses that field; strict tree parsing fails, and its exception was swallowed. A second YAML document could also evade the single-tree inspection.
- Impact: The ordinary save path accepts and returns the plaintext provider key and writes it into `management_draft.execution_configuration_yaml`, violating the write-only/encrypted credential boundary even though eventual publication validation would reject the configuration.
- Evidence: The new `rejectsQuotedAndInlineSecretsEvenInInvalidDrafts` regression failed before the fix because no exception was raised. `ManagementEditingController.content` invokes this guard before saving, and `ConfigurationDraftStore.replace/read` otherwise persists/returns the exact submitted text.
- Fix: Scan YAML property tokens across documents and duplicate mappings, independently of strict framework candidate validation. Retain support for incomplete nonsecret drafts. Add unit coverage for quoted, inline, duplicate and multiple-document cases, plus an HTTP regression asserting rejection, unchanged revision, secret-free response/readback and absent plaintext in the SQL column.

### [P2] Remove the unsupported Gemini URI credential control

- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java:339` and `src/main/resources/static/management/assets/editor.js:285`.
- Scenario: An operator fills the offered Gemini credentials URI reference and generates execution YAML. The builder emits `gemini.credentials-ref`, which the database write boundary deliberately rejects.
- Impact: A visible authoring option cannot be saved or published; it also contradicts the documented JSON-only database credential workflow.
- Evidence: Traced the offered HTML field through the generator to `assertNoDirectAuthoredCredentials` and the approved prohibition on database credential-file/URI references.
- Fix: Remove the URI input and generator branch. Keep the managed JSON-reference input. Existing real Chromium generation/publication coverage now also asserts that the unsupported input is absent and the JSON input is present.

### [P2] Forward the documented mode into the production container

- Location: `examples/production/compose.yaml:33`.
- Scenario: Follow the updated production guide and select `LOOMSPAN_SIDECAR_CONFIGURATION_MODE=file` in the deployment environment. Compose previously forwarded only its explicitly listed settings and omitted mode.
- Impact: The container silently remains in default database mode instead of using the requested file authority.
- Evidence: Compared the guide's new file-mode instructions with the production service's explicit environment mapping. There is no `env_file` or other mode forwarding path.
- Fix: Forward the mode with a `database` default, expose it in `production.env.example`, and verify the mapping in the existing configuration-reference test. File mode still requires the guide's explicit configuration/skill/route mounts.

## Findings Resolved in This Context

Resolved the three findings above with changes limited to the direct-secret guard, Console field/generator, production environment mapping and focused regressions. No compatibility path, new framework SPI, migration machinery, or alternate publication workflow was added. Full profile remains appropriate because this ticket changes security and durable/lifecycle contracts; no new developer decision was required.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1 File startup authority, restart-only changes, empty/populated DB, read-only management | Immutable `ConfigurationModeConfiguration`; `startFiles`; separate `FileConfigurationStartupStore`; shared database mode gates | `ConfigurationModeIntegrationTest`, `FileModeManagementHttpIntegrationTest`, real model/REST execution and browser inspection | Implemented |
| AC2 Complete database providers/settings and Console/API/client parity without deployment fallback | Public binding advisor isolates documented roots; effective candidate uses explicit decrypted map; Console and client write-only credential commands | Poisoned startup cases, runtime configuration tests, editing HTTP/Chromium workflow, client suite | Implemented; unsupported URI control removed |
| AC3 Saves remain drafts; exact validated candidate, revisions/base/lease/live authority | Candidate equality includes YAML, values and version identities; publication/transition gates; existing editing admission checks | Editing service/HTTP/PAT, restart/remote authoring, runtime fault/concurrency tests | Implemented |
| AC4 Encrypted durability, safe reads/errors, missing/wrong key recovery | AES-GCM with metadata AAD; separate encrypted rows; production environment key; redacted file inspection; repaired write guard | Cipher/store/HTTP tests; child-JVM key boundary; new direct-secret regression | Implemented after fix |
| AC5 Selected complete restart/history/rollback authority | Stored effective YAML and credential versions; source-aware retained drafts; file starts preserve DB selection | Runtime/recovery, draft/storage, mode-switch and environment-key tests | Implemented |
| AC6 Default and optional encrypted portability; explicit different-key replacement | Format 3 strict inventory; locked capture; included authentication before draft replacement; explicit configuration-only choice; browser/client options | Bundle suite, same/missing/different-key/tampered portability executions and restart, session/PAT export checks, Chromium and Python | Implemented |
| AC7 Full generation isolation at handoff, delayed children/retries, correlation/resources | Explicit complete framework candidate; generation-to-durable-ID mapping; retirement ownership unchanged | Correlation root/child/retry scenarios, REST generation/lifecycle and shutdown tests; preparation failure tests | Implemented; composed public integration evidence rather than framework internal assertions |
| AC8 Supported framework boundary, installed snapshot, usable guidance and intentional replacement | Only supported Loomspan API imports; V1/V3 and format replacement; both-mode/key/backup docs and examples | ArchUnit; independent full Maven run; Python guidance/client tests; configuration reference checks | Implemented; production mode forwarding corrected |

The implementation follows the settled design. Standard Spring binding customization is consistent with the approved public boundary. File startup metadata is a safe correlation record, not a second recovery authority. Retained source selection and credential preparation preserve the existing publication/failure model. Optional deployment checks remain distinct from executable local acceptance evidence.

## Active Project Guardrails

- Simplicity/development replacement: obsolete mixed source and format-2 contracts are replaced, with documented backup/reset implications. No reset was performed.
- Framework authority: public `SkillReloader`/`ExecutionConfiguration`/catalog/retirement APIs remain the execution and validation boundary. The token scan is a secret write-boundary check, not model/skill validation. ArchUnit covers application and test classes; no Java skills or internal/autoconfiguration imports were introduced.
- Configuration namespaces/process ownership: only documented framework execution roots are isolated; mode, storage, JWT/authentication, ports and encryption material remain deployment-owned.
- Admission/identity/lifecycle: existing JWT ownership, single admission owner, physical retirement and one framework shutdown budget remain intact and covered by Sidecar integration tests.
- Shared management/drafts: one browser/PAT workflow retains account/token checks, private saved drafts, exact lease/revision/base proofs and no live mode switch. User-chosen shared-key transfer and explicit replacement remain supported.
- Release workflow: used the installed beta.6-SNAPSHOT without rebuilding the framework or switching versions. Sidecar remains independently versioned. Hosted/release checks remain deferred until framework publication.

## Open Questions and Assumptions

None requiring developer input. The installed framework snapshot is trusted under the documented developer installation workflow. The chosen startup modes and shared-key export policy are settled scope.

## Verification Results

An isolated Maven output directory avoids the reported IDE writes to ordinary `target/classes`. Created `.codex-review-2-pom.xml` from the actual `pom.xml` with only `<directory>${project.basedir}/target/review-2-verification</directory>` inserted immediately after `<build>`. Source roots, dependencies and test/plugin settings were unchanged. Logs are retained under `target/review-2-*.log`; the temporary POM is removed after verification.

Exact temporary-POM creation:

```powershell
$reviewPom = (Get-Content -Raw pom.xml).Replace('<build>', '<build><directory>${project.basedir}/target/review-2-verification</directory>')
[System.IO.File]::WriteAllText((Join-Path (Get-Location) '.codex-review-2-pom.xml'), $reviewPom)
```

Cleanup completed with `Remove-Item -LiteralPath .codex-review-2-pom.xml`. No output-directory deletion or developer data cleanup was performed.

- PASS — `.\mvnw.cmd -f .codex-review-2-pom.xml -B -ntp verify` — independent initial complete-diff baseline: 246 tests, zero failures/errors, 3 intentionally gated production Compose browser skips; JAR packaged. This run began before review fixes and is not claimed as a post-fix full build.
- FAIL (expected red regression) — `.\mvnw.cmd -f .codex-review-2-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest' test` — 3 tests, 1 failure: the newly added secret test expected rejection but no exception occurred. `target/review-2-secret-red.log`.
- PASS — `.\mvnw.cmd -f .codex-review-2-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,ManagementEditingHttpIntegrationTest,ManagementEditorBrowserIntegrationTest,ConfigurationReferenceTest' test` — 18 tests, zero failures/errors/skips after the fixes. Includes actual HTTP persistence/readback assertions and Chromium generation/publication. `target/review-2-focused.log`.
- PASS — `.\mvnw.cmd -f .codex-review-2-pom.xml -B -ntp '-Dtest=ConfigurationBundleV3Test,CredentialPortabilityIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — 14 tests, zero failures/errors/skips; repaired guard remains compatible with archives/imports and supported framework integration. `target/review-2-transfer.log`.
- PASS — `.\mvnw.cmd -f .codex-review-2-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest' test` — final 3 tests, zero failures/errors/skips after extracting the token scanner's immutable field-name set to a static constant. `target/review-2-secret-final.log`.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` — 11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_*.py'` — 7 tests; the printed simulated Docker log timeout belongs to a passing mocked script-error test, not a live Docker invocation.
- PASS — `python -m py_compile scripts/verify-production.py`.
- PASS — `git -c core.safecrlf=false diff --check`.
- NOT RUN — live provider calls, Docker production verification, data reset, release/deployment, framework rebuild and hosted CI. They are outside routine verification and were not needed to test these fixes.

## Residual Risks and Optional Developer Checks

The three gated production Compose browser tests were not executed; normal local Chromium acceptance tests executed successfully. Production mode forwarding is checked in the repository configuration-reference test; no container deployment was performed. Optional disposable-installation checks remain: assess clarity of file inspection and wrong-key replacement wording, and rehearse separate database/key backup provisioning. Existing REST YAML literal-sensitive-value behavior is intentionally outside this provider-secret redesign.

## Disposition

`fixes-applied`. The independent review found and repaired three actionable defects; its internal re-review has no remaining actionable issue. The complete initial suite plus focused post-fix HTTP/browser/security/transfer checks provide sufficient evidence for this review/fix cycle. A fresh Step 5 context must independently review the changed implementation before the pipeline can finish.
