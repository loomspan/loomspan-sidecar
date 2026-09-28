# PR 7.1 Code Review — Cycle 5

## Scope and Repository State

Full-profile pipeline Step 5 on 2026-09-27. Base: `main` / `origin/main`,
`87ffc54dd6cb0663f80144693dc7772653c14c4d`; no staged changes or branch-only commits.
Reviewed the ticket-scoped tracked and untracked implementation, tests, schema,
configuration, Console assets, Python client, deployment examples and documentation.
This includes the new mode/candidate/cipher/file-startup classes and format-3 bundle,
not just the tracked diff. Read the governing ticket, refreshed research, both plans,
AGENTS.md, automation protocol and design lens. Prior review documents were not read.
No unrelated working files were reset or removed.

Traced startup binding through activation/readiness, encrypted draft and snapshot
storage, exact validation/publication checks, import/export/rollback, live authority,
file inspection, browser/client flows and resource retirement. Consulted the matching
local framework README and public `SkillReloader` contract. The initial independent
review preceded implementation edits; subsequent re-review included both corrections
and their connected callers. Implementation reports were context, not test evidence.

## Findings

### [P1] Reject execution YAML that cannot be inspected safely before storage

- Location: `src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java:94`.
- Scenario: an authorized editor saves a document with a syntax error before a
  later flow mapping containing a quoted provider credential field. The streaming
  parser stops at the earlier error; the former fallback matched only unquoted
  fields at line starts. YAML escaping further defeats lexical field matching.
- Impact: the ordinary draft save accepts and persists plaintext provider material
  and returns it on draft reads, violating the encrypted/write-only boundary.
- Evidence: a standalone probe accepted the malformed document. Added unit and real
  HTTP regressions both failed before the fix; the HTTP endpoint returned 200 where
  rejection was expected. The test uses synthetic data and checks revision, readback
  and SQLite content after rejection.
- Fix: remove the regex fallback. Reject parser failures using a fixed safe message.
  Readable YAML still uses parsed field names and context, including quoted/escaped
  keys, alias names and referenced header names. Framework semantic validation is
  unchanged. The Console retains unsent text and the prior saved draft on failure.

### [P2] Allow Read tokens to inspect file-startup metadata

- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementBearerFilter.java:72`.
- Scenario: a remote client calls the new `GET /api/management/configuration/file-startups`
  endpoint using an otherwise valid Read, Edit or Publish token.
- Impact: the closed token route map omitted this endpoint, returning 403 even
  though the same read was available to authenticated browser sessions.
- Evidence: traced the controller endpoint through `ManagementBearerFilter.required`,
  whose unmatched route returns null and is denied before the controller. Added a
  real Read-token HTTP assertion for the endpoint and its array response.
- Fix: classify this exact GET route as Read and document its mode-dependent result.

## Findings Resolved in This Context

Both findings are resolved. The final security correction replaces the initial
lexical patch with rejection of unreadable input, avoiding successive quoting and
escaping exceptions. Tests cover malformed prefixes, quoted and escaped credential
keys, preserved HTTP/SQLite draft state, valid alias/header identifiers, semantic
validation ownership, browser local-text preservation and the token route mapping.
The implementation plan records the syntax-save refinement; operations documents
both that behavior and the metadata endpoint. No actionable findings remain after
internal re-review.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1 Complete file authority and read-only restart workflow | Immutable mode, binding advisor, `startFiles`, separate startup store, mutation gates | `ConfigurationModeIntegrationTest`, `FileModeManagementHttpIntegrationTest` | Implemented |
| AC2 Complete database editing without deployment fallback | Explicit candidate and credential-map preparation, empty bootstrap, shared controls/client | Startup poison cases, editing HTTP/browser/PAT, client suite | Implemented |
| AC3 Draft-only edits, exact candidate and live grants | Candidate/version equality, revision/base/lease proof and transition gates | Editing service/HTTP, private-draft restart and remote workflow | Implemented |
| AC4 Encrypted durable secrets and safe key failure/readback | AES-GCM with authenticated metadata, encrypted tables, write-only boundary | Cipher/store tests, real environment-key child process, new malformed-input regressions | Implemented |
| AC5 Restart, history and retained rollback | Database restore and retained versions; file startup leaves database selection intact | Runtime recovery, file-to-database restart, retained-source/draft and environment tests | Implemented |
| AC6 Explicit encrypted portability and replacement | Strict format 3, atomic export capture, authenticated included import and explicit configuration-only load | Bundle, portability, encrypted export authorization, browser/client options | Implemented |
| AC7 Handoff and resource isolation | Public complete-candidate prepare, generation mappings and retirement ownership | Root/child/retry correlation, REST generations, lifecycle and shutdown integration | Implemented |
| AC8 Supported public API and coherent guidance | Public Loomspan types, standard Spring binding hook; documented modes/key/recovery | ArchUnit, Maven integration, Python guidance/client, configuration reference checks | Implemented |
| Malformed execution YAML saving | Reject unreadable syntax before storage; retain readable semantic errors for framework validation | Unit/HTTP tests plus browser preservation assertion | Safe refinement recorded in governing implementation plan |

## Active Project Guardrails

- Superseded source selection, schemas and bundle contract are replaced directly;
  no compatibility shim or migration machinery was added. Development reset and
  separate key/backup recovery remain documented; no database reset was performed.
- Sidecar and tests retain the supported `ai.loomspan.api` boundary. Bootstrap uses
  public Spring binding APIs; no framework bean replacement or new SPI was added.
- One framework publication authority, one admission owner, trusted JWT execution
  identity, private drafts, live account/token grants and physical retirement remain
  intact. The file metadata fix grants only the existing Read capability.
- The final input correction reduces complexity by removing a lexical fallback.
  It does not introduce a competing model/skill validator.
- Used the installed beta.6-SNAPSHOT. No framework rebuild, dependency/release
  switch, hosted CI, live provider or production operation occurred.

## Open Questions and Assumptions

None requiring a developer decision. File inspection is sanitized startup metadata,
not a replayable database publication. REST YAML retains its separately authorized
existing secret-handling contract.

## Verification Results

Verification uses a temporary copy of `pom.xml` with only
`<directory>${project.basedir}/target/review5</directory>` added under `<build>`.
This avoids editor-generated diagnostic classes in normal `target`; dependencies,
source roots and test configuration are unchanged.

Exact setup:

```powershell
$reviewPom = (Get-Content pom.xml -Raw).Replace('<build>', '<build><directory>${project.basedir}/target/review5</directory>')
Set-Content -LiteralPath .codex-review5-pom.xml -Value $reviewPom
```

- PASS — `.\mvnw.cmd -f .codex-review5-pom.xml -B -ntp '-Dtest=ConfigurationModeIntegrationTest,EffectiveExecutionConfigurationTest,CredentialPortabilityIntegrationTest,ManagementEditingHttpIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — 26 tests, no failures/errors/skips. Log: `target/review5-focused.log`.
- Expected FAIL before correction — `.\mvnw.cmd -f .codex-review5-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest#rejectsQuotedAndInlineSecretsEvenInInvalidDrafts,ManagementEditingHttpIntegrationTest#invalidYamlCannotPersistOrEchoInlineProviderSecrets' test` — 2 tests, 2 assertion failures proving the defect; HTTP returned 200 instead of 400. Log: `target/review5-red.log`.
- PASS — `.\mvnw.cmd -f .codex-review5-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,ManagementEditingHttpIntegrationTest' test` — 16 tests after the initial correction. Log: `target/review5-green.log`.
- PASS — `.\mvnw.cmd -f .codex-review5-pom.xml -B -ntp verify` — 254 tests, 0 failures/errors, 3 gated production Compose skips; packaging succeeded. This run compiled before the final parser-failure simplification and token allowlist addition; the final affected suite below verifies those final changes. Log: `target/review5-verify.log`.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — 11 tests. Log: `target/review5-client.log`.
- PASS — `python -m unittest discover -s scripts -p 'test_*.py' -v` — 7 tests. Log: `target/review5-python.log`.
- PASS — `python -m py_compile scripts/verify-production.py`.
- PASS — `git -c core.safecrlf=false diff --check`.
- Probe note: bare `java` was unavailable on PATH. The same source probe ran using
  the JDK path from the freshly generated Surefire report and returned `ACCEPTED`.
  The unit/HTTP red tests above provide the durable executable reproduction.
- PASS — `.\mvnw.cmd -f .codex-review5-pom.xml -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,ManagementEditingHttpIntegrationTest,ManagementPersonalTokenHttpIntegrationTest,ManagementEditorBrowserIntegrationTest,ConfigurationBundleV3Test,ConfigurationReferenceTest,SupportedLoomspanApiArchitectureTest,FileModeManagementHttpIntegrationTest' test` — final code: 50 tests, 0 failures/errors/skips. This reruns every affected parser/controller/token/browser/bundle boundary and architecture/reference check. Real Chromium cases ran. Log: `target/review5-final.log`.
- PASS — `Remove-Item -LiteralPath .codex-review5-pom.xml`; `Test-Path .codex-review5-pom.xml` returned false. Final `git -c core.safecrlf=false diff --check` passed.
- NOT RUN — `python scripts/verify-production.py`: explicitly gated disposable production Compose verification; no Docker deployment or live service operations were authorized as routine review checks. The three production Compose browser skips are not counted as executed tests.

## Residual Risks and Optional Developer Checks

The final changes were verified by the 50-test affected suite after the broader
254-test run; the packaged JAR from that broader run is not a final release artifact.
No optional repeat of the complete suite was needed for these bounded corrections.
A fresh independent review is required because this context changed implementation
artifacts. On a disposable installation, optionally assess the mode/key/import
wording and separate database-backup/key-provisioning workflow. These observations
remain nonblocking; no live provider or deployed-data check was performed.

## Disposition

`fixes-applied`. Both findings are fixed and final affected verification passes.
No remaining actionable findings or unresolved developer questions were identified.
This context cannot certify its own edits as a clean independent review.
