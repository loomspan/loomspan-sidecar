# PR 7.1 Code Review — Cycle 3

## Scope and Repository State

Reviewed independently in pipeline mode, Step 5, Full 5-Step Pipeline, on 2026-09-27.
Read AGENTS.md, the shared protocol, design lens, revised ticket, refreshed research,
implementation plan and testing plan. No prior review documents or findings were read.

The comparison base is HEAD `87ffc54dd6cb0663f80144693dc7772653c14c4d`, on `main`
tracking `origin/main`; the remote default is also `main`. There are no staged
changes or additional ticket commits beyond that base. Scope includes the complete
tracked working-tree diff and untracked ticket source/tests, including the V2-to-V3
bundle replacement, startup mode/bootstrap, complete candidates and cipher, SQL
draft/snapshot storage, file startup metadata, runtime/publication/lifecycle callers,
management controllers/services/pages/assets, Python authoring client, deployment
examples/verifier and operator guidance. Supplied research/plans are context; prior
review files are excluded audit records. Existing ticket work was preserved.

Completed the initial correctness, security, persistence, concurrency/lifecycle,
integration, operational and test review before adding regressions or editing
implementation. Both findings below were reproduced, fixed and internally reviewed.

## Findings

### [P2] Preserve valid alias and referenced-header names in the credential guard — resolved

- Location: `src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java:78`.
- Scenario: Save execution YAML containing `header-refs: {api-key: provider.key}`,
  a connection named `headers`, or a model alias named `credentials-json`.
- Impact: The former global field-name scan rejected these reference-only candidates
  with HTTP 400, preventing supported complete configuration authoring and import.
- Evidence: The framework's current public preparation contract accepts these named
  entries; its matching local parser distinguishes names from provider fields.
  The new `credentialFieldNamesAreAllowedAsAliasesAndReferencedHeaderNames` regression
  failed before the fix. The real HTTP save/validate regression now passes using an
  encrypted synthetic value and an unreachable loopback endpoint without model calls.
- Fix: Keep streaming inspection across duplicate mappings/documents, exempt only
  connection/model alias keys and `header-refs` names, and retain conservative
  rejection of literal credential fields in invalid drafts. Quoted/inline secret
  regressions remain green, including an invalid extra root containing a literal key.

### [P2] Allow explicit removal of unresolved imported credential requirements — resolved

- Locations: `src/main/java/ai/loomspan/sidecar/storage/ConfigurationDraftStore.java:160`
  and `src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java:66`.
- Scenario: Load a configuration-only bundle, remove an unnecessary provider and
  its YAML reference, then remove its still-required credential identifier.
- Impact: Removal previously required an existing ciphertext row and rejected the
  unresolved requirement. Validation could then approve the edited YAML with no
  supplied values while retaining that identifier in the publication metadata;
  immediate encrypted export failed its inventory check, and restart reconstructed
  different identifier metadata from the stored credential rows.
- Evidence: The new candidate regression demonstrated that unresolved requirements
  were accepted; the store regression failed with `Credential identifier is not
  configured`. The complete import/edit/remove/validate/publish/encrypted-export
  integration now succeeds and verifies an empty final credential inventory.
- Fix: Under the existing exact revision transaction, remove a configured value,
  its pending requirement, or both; reject a wholly unknown identifier without a
  revision change. Preparation requires every retained requirement to be configured
  until explicitly removed. Console wording and operator/authoring guidance describe
  this workflow. Existing permission, lease and proof invalidation rules still apply.

## Findings Resolved in This Context

Both findings are resolved. Changes are limited to the credential guard and
requirement check, transactional identifier removal, one Console message, relevant
operations/authoring guidance, focused unit/HTTP/import regressions, and the runtime
test fixture. The fixture now uses ordinary credential removal/replacement operations
when replacing its credential set, keeping its required-ID metadata consistent.

The initial broad run exposed that fixture assumption in two execution tests and an
incomplete new OpenAI test candidate lacking its API key reference. These were
corrected; the affected 41-test suite and final complete suite pass. The final internal
review found no remaining actionable issue of any priority. A fresh reviewer must
still review this context's changes.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1: immutable file authority, restart-only changes, empty/populated DB, read-only management | `ConfigurationModeConfiguration`, `RuntimeConfigurationService.startFiles`, `FileConfigurationStartupStore`, shared mode gates | `ConfigurationModeIntegrationTest`, `FileModeManagementHttpIntegrationTest`, environment-key file probe | Implemented |
| AC2: complete database authoring independent of deployment providers | Spring binding advisor, `EffectiveExecutionConfiguration`, editor controls, credential endpoint/client | Poisoned bootstrap, runtime integration, editor Chromium, editing HTTP, remote authoring and Python client suites; new alias/header regression | Implemented |
| AC3: draft-only saves and exact candidate/grant/base validation | `ManagementEditingService`, candidate content comparison, publication/transition gates | Private draft, token/session/live authority, stale revision/base, validation and failure-preservation tests | Implemented |
| AC4: encrypted durable values and safe key failures/readback | AES-GCM with authenticated metadata, encrypted draft/snapshot rows, write-only responses and redaction | Cipher/store tests, real environment-key child JVM, HTTP invalid-secret tests, browser clearing, tamper/recovery tests | Implemented |
| AC5: selected DB restart/history/rollback; file starts preserve DB authority | Explicit restore candidate, retained ciphertext, separate file metadata, snapshot pointer/failure handling | Runtime/recovery, key environment, portability restart, draft/store and file-to-database restart tests | Implemented |
| AC6: explicit optional encrypted portability and replacement workflow | Bounded format 3, locked export capture/live Edit check, authenticated included import and explicit configuration-only choice | Bundle tests, same/different/missing-key and authenticated-metadata tamper transfer, PAT export matrix, browser transfer controls, new unresolved-ID workflow | Implemented |
| AC7: complete generation capture, delayed children/retries and physical resource lifetime | Public three-argument preparation, generation mapping, existing admission/resource ownership | Root/child/503 retry overlap, durable correlation, REST generation/lifecycle, shutdown and invalid-preparation suites | Implemented |
| AC8: supported API, installed snapshot, coherent guidance and development replacement | No internal framework dependencies; matching local framework source used for review; replaced schemas/bundle contracts and updated guidance | ArchUnit, complete Maven verify, Python client/guidance/script tests and documentation review | Implemented |

The implementation's documented composition of complete session/trace persistence
tests with endpoint/model/key/quota overlap and existing physical-retirement tests is
sufficient; it does not claim framework tests as Sidecar evidence. No obsolete source
fallback or provider-override path was retained. Full-profile eligibility remains
appropriate for the security, persistence and lifecycle scope.

## Active Project Guardrails

- Development replacement and simplicity: superseded contracts/schemas are replaced;
  the required development reset is documented and no data was reset. Fixes use the
  existing parser, store transaction and API rather than adding an extension or service.
- Framework boundary: application/tests remain on `ai.loomspan.api`; ArchUnit forbids
  internal/autoconfigure imports and Java skills. Bootstrap uses standard Spring APIs.
- Execution authority/admission/diagnostics: framework validation and lifetime,
  one Sidecar admission owner, retained correlation and diagnostic behavior remain
  intact; the complete integration and shutdown suites pass.
- Trusted identity/shared management: execution JWT and management credentials remain
  separate. Private drafts, explicit lease/handoff, live account/PAT checks and
  browser CSRF still guard credential changes and complete publication.
- Deployment autonomy/atomicity: startup mode is explicit, file changes require
  restart, database content has no provider fallback, and included/configuration-only
  import choices remain explicit. Old captured work retains its generation.
- Release policy: used the developer-installed beta.6-SNAPSHOT and local matching
  framework source. No framework rebuild, release dependency switch, publication,
  production deployment or hosted CI operation occurred.

## Open Questions and Assumptions

None affecting implementation or review confidence. The established developer
snapshot-installation workflow is relied upon as instructed. Synthetic credentials,
temporary SQLite databases and loopback providers are the verification boundary.

## Verification Results

The IDE writes diagnostic classes into normal `target`, so all Maven runs used a
temporary copy of `pom.xml` with only the build output directory changed. Exact
PowerShell setup (from the repository root):

```powershell
$reviewPom = Get-Content pom.xml -Raw
$reviewPom = $reviewPom.Replace('<build>', '<build><directory>${project.basedir}/target/review-3</directory>')
Set-Content -LiteralPath .codex-review-3-pom.xml -Value $reviewPom
```

- PASS — `.\mvnw.cmd -f .codex-review-3-pom.xml -B -ntp '-Dtest=ConfigurationModeIntegrationTest,CredentialEnvironmentIntegrationTest,CredentialPortabilityIntegrationTest,FileModeManagementHttpIntegrationTest,EffectiveExecutionConfigurationTest,ProviderCredentialCipherTest,ConfigurationBundleV3Test' test` — 26 tests, no failures/errors/skips; `target/review-3-focused.log`.
- FAIL (intentional reproduction) — `.\mvnw.cmd -f .codex-review-3-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,ConfigurationDraftStoreTest' test` — 9 tests, 2 failures/1 error establishing the findings; `target/review-3-red.log`.
- PASS — same focused candidate/store command after fixes — 9 tests, no failures/errors/skips; `target/review-3-fixes.log`.
- FAIL (resolved iteration) — `.\mvnw.cmd -f .codex-review-3-pom.xml -B -ntp verify` — 252 tests, 1 failure/2 errors/3 gated skips. Two runtime fixture metadata assumptions and the incomplete new HTTP provider fixture were corrected; `target/review-3-verify.log`.
- PASS — `.\mvnw.cmd -f .codex-review-3-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,ConfigurationDraftStoreTest,CredentialPortabilityIntegrationTest,ManagementEditingHttpIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest' test` — 41 tests, no failures/errors/skips; `target/review-3-regressions.log`.
- PASS — final `.\mvnw.cmd -f .codex-review-3-pom.xml -B -ntp verify` — **252 tests, 0 failures, 0 errors, 3 gated skips; packaged executable JAR**. Final unchanged source, completed 2026-09-27 16:28:45 PDT; `target/review-3-final.log`.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — 11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_*.py' -v` — 7 tests. The shutdown-script failure/cleanup case mocks Docker; its diagnostic is expected.
- PASS — `python -m py_compile scripts/verify-production.py`.
- PASS — `git -c core.safecrlf=false diff --check`.
- PASS — cleanup `Remove-Item -LiteralPath .codex-review-3-pom.xml`; temporary POM removed, repository POM unchanged.
- NOT RUN — gated `ProductionComposeBrowserIntegrationTest` (3 tests), disposable Docker production/shutdown verification, live providers, release/hosted CI. These are excluded from routine safe verification; actual local Chromium editor/import/history/file-mode tests ran.

## Residual Risks and Optional Developer Checks

No unresolved implementation finding remains. Optional checks carried forward from
the plan: assess the file-mode inspection and different-key import wording on a
disposable installation, and exercise consistent database backup plus separately
provisioned key recovery. Configured Docker/production checks and eventual published
framework release verification remain separate and unrun. Provider-side revocation
can invalidate captured old credentials as explicitly accepted by the ticket.

## Disposition

`fixes-applied`. This context changed implementation artifacts and therefore cannot
certify its own fixes as a clean fresh review. Launch independent Step 5 cycle 4
against the entire current ticket diff, without passing this review's findings.
