# PR 7.1 Code Review — Cycle 7

## Scope and Repository State

Independent Step 5, pipeline mode, approved Full 5-Step Pipeline, on 2026-09-27.
Comparison base is `87ffc54dd6cb0663f80144693dc7772653c14c4d` (`main`, also
`origin/main`). The index is empty. Reviewed the complete ticket-related working
change, including the deleted format-2 bundle implementation, all 63 changed
tracked files, and the 16 untracked source/resource/test files. Process artifacts
were inventoried separately. Prior review documents were not read.

Read the governing revised ticket, research, implementation and testing plans,
repository instructions, shared protocol and design lens. Reconstructed behavior
from current source and Git before evaluating plan conformance; implementation
reports and checked boxes were not used as verification evidence. Connected
framework behavior was checked in the matching local checkout, including the
public `SkillReloader` contract and documented complete-candidate semantics.

Reviewed mode binding/bootstrap, file startup and correlation, encrypted draft
and snapshot persistence, immutable candidate proofs, publication and recovery,
management authorization, archive parsing/export/import, browser controls,
remote authoring, deployment examples and operating instructions. Followed
resource cleanup, lock ordering, late authorization checks and failure paths
beyond changed hunks. No unrelated working changes were altered.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. The complete initial review preceded any artifact writing. This context
changed no implementation artifact. Its temporary verification POM changes only
the build output directory and is removed after verification.

Candidate checks included whether the Gemini guard rejects a supported API-key
configuration: the framework itself forbids a Gemini-options block in API-key
mode, so this was not a defect. Missing imported credential requirements cannot
be published merely by deleting a provider from YAML: the retained requirement
must be configured or explicitly removed. Import authentication precedes draft
replacement, and exact grants and live authority are checked again at mutation.

## Acceptance-Criteria and Plan Conformance

Paths below are relative to `src/main/java/ai/loomspan/sidecar` and
`src/test/java/ai/loomspan/sidecar` unless otherwise specified.

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1: Complete file authority; restart-only changes; empty/populated DB; no management mutation | `ConfigurationModeConfiguration`; `RuntimeConfigurationService.startFiles`; `FileConfigurationStartupStore`; shared `requireDatabase` gate | `ConfigurationModeIntegrationTest` executes model and REST work before/after file changes and restart, preserves the selected DB; `FileModeManagementHttpIntegrationTest` exercises mutation inventory and Chromium inspection | Implemented |
| AC2: Complete database Console/API/client with empty bootstrap and no external fallback | Binding advisor skips documented execution roots before conversion; empty skill source; `EffectiveExecutionConfiguration.assemble`; write-only credential endpoints and editor fields | Poisoned provider/numeric startup; real candidate preparation; editing HTTP/PAT and editor browser workflows; Python client suite | Implemented |
| AC3: Saves remain private draft edits; exact candidate, lease, revision/base and live authorization | `ManagementEditingService` captures and rechecks revisions; `Candidate.sameContent`; `RuntimeConfigurationService.publish`; transactional draft credential replacement | Editing service/HTTP/PAT and draft restart cases; same-value credential version inequality; runtime fault and remote authoring scenarios | Implemented |
| AC4: Encrypted durable secrets and safe reads/errors; explicit key failure | AES-256-GCM with random nonce and authenticated bounded identifier/version; separate ciphertext tables; direct-credential scan and safe failure responses | Cipher and exact SQL assertions; invalid-YAML HTTP rejection; controlled child-JVM production-key startup; browser value clearing and portability authentication | Implemented |
| AC5: Complete restart/history/rollback with one authority | Selected snapshot restored with retained encrypted map; rollback copies retained versions into normal draft workflow; file startup does not initialize/select DB content | Runtime integration/recovery and history browser tests, snapshot/draft persistence tests, same-key restart execution, file-to-database restart | Implemented |
| AC6: Optional encrypted portability and explicit replacement workflow | Format-3 strict inventory; capture under publication gate; Edit authorization; authenticated included import or explicit configuration-only path | Archive tests; same/different/missing-key and tampered-version import with real execution; encrypted export late role check; Read/Edit/Publish PAT matrix; browser/client options | Implemented |
| AC7: Captured roots, children and retries retain settings/credentials/resources and correlation; invalid preparation does not activate | Public three-argument prepare; staged generation mapping; existing retirement and transition ownership | Root/child/physical-503 retry correlation scenarios check old/new endpoint, model, key and quotas; existing REST generation, lifecycle and shutdown tests; invalid preparation/recovery tests | Implemented |
| AC8: Supported public API, local snapshot integration, usable guidance and intentional development replacement | No forbidden framework imports; retained ArchUnit rule; pinned beta.6-SNAPSHOT; operations, authoring and examples updated | Architecture test, full safe suite, Python guidance/client tests, verifier syntax and independent document review | Implemented |

Verification is composed across focused cases and existing integration tests;
not every illustrative testing-plan permutation is a distinct new test. The
actual tests exercise real Sidecar/framework startup and execution with temporary
SQLite and loopback fixtures. No framework test result substitutes for Sidecar
evidence. The public API keeps model/skill semantics authoritative. File startup
inspection is explicitly sanitized metadata, not a replayable backup.

## Active Project Guardrails

- Destructive development replacement is maintained: no provider-override path,
  format-2 compatibility adapter or obsolete schema migration was added. The
  ticket and lens prominently state the policy; operations documents backup and
  development reset needs. No data was reset during this review.
- Framework use remains on `ai.loomspan.api`; bootstrap isolation uses public
  Spring binding APIs and documented properties. ArchUnit covers application and
  test code and prohibits Java skills. Local framework source inspection did not
  add any internal dependency.
- One execution admission owner, trusted JWT identity, shared browser/PAT API,
  account-owned drafts and exact lease generations remain intact. Encrypted
  export checks live Edit authority after waiting; imports recheck authority at
  replacement; publication holds the transition gate used for invalidation.
- Old work retains framework generations and Sidecar resources until physical
  retirement; the existing shutdown owner/budget is preserved.
- Mode and encryption key remain deployment settings. Database providers use an
  explicit decrypted credential map; file mode ignores irrelevant encryption
  material. The existing REST YAML secret contract remains outside this change.
- Sidecar/framework versions remain independently pinned as instructed. No
  framework build, release/dependency switch, hosted CI or live service occurred.

## Open Questions and Assumptions

None affecting the disposition. The installed snapshot follows the developer's
installation workflow. The supported single Sidecar/local SQLite deployment and
externally provisioned encryption key remain the operating assumptions.

## Verification Results

- PASS — `.\mvnw.cmd -f .codex-review7-pom.xml -B -ntp '-Dtest=ConfigurationModeIntegrationTest,EffectiveExecutionConfigurationTest,ProviderCredentialCipherTest,CredentialPortabilityIntegrationTest' test` — 17 tests, zero failures/errors/skips. Log: `target/review7-focused.log`.
- PASS — `.\mvnw.cmd -f .codex-review7-pom.xml -B -ntp verify` — 255 tests, zero failures/errors, 3 intentional production Compose browser skips; JAR packaged. Finished 2026-09-27 17:17:30 PDT. Log: `target/review7-verify.log`; reports: `target/review7/surefire-reports/`. Local Chromium cases, architecture checks and execution lifecycle cases actually ran.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` — 11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_*.py'` — 7 tests. The shutdown fixture test prints its intentionally mocked log-collection timeout; the suite is successful and no Docker operation ran.
- PASS — `python -m py_compile scripts/verify-production.py`.
- PASS — `git -c core.safecrlf=false diff --check`.
- NOT RUN — `python scripts/verify-production.py` and its gated `ProductionComposeBrowserIntegrationTest` — disposable deployment is outside routine verification; three skips are not claimed as passes.

The review POM was copied from `pom.xml` with exactly one insertion:
`<directory>${project.basedir}/target/review7</directory>` inside `<build>`.
It was verified against that exact transformation and removed. This avoids the
reported IDE diagnostic classes in regular `target/classes` and
`target/test-classes` without changing compiler, dependencies, source roots or
test settings. The first focused command compiled into the new isolated output;
full verify then ran the whole suite against those classes. No code changes
occurred between these commands, and no further optional test expansion was
needed after success.

## Residual Risks and Optional Developer Checks

The three Docker production browser cases are explicitly gated and are not
routine local verification. Docker deployment, live provider behavior, framework
publication and hosted release CI remain unrun. They do not block local ticket
assurance; final release still requires the separately published framework and
release checks required by repository policy.

Optional: on a disposable installation, assess the file-mode/read-only and
wrong-key replacement wording, and rehearse restoring a consistent database
backup while provisioning the original encryption key separately. Early
provider-side revocation can invalidate old captured work, as accepted by the
ticket. No production operation is implied by this review.

## Disposition

`clean` — independent review completed with no actionable findings of any priority, no implementation-artifact changes, and sufficient executable verification.