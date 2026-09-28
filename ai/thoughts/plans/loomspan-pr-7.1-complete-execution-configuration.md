# PR 7.1 File or Database Configuration Implementation Plan

## Overview

**Development policy: destructively replace superseded contracts, code and schemas; no compatibility shims, legacy adapters, dual APIs or migrations solely for obsolete development behavior.** Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). Document required development-data resets; do not delete deployed data.

- Ticket: `ai/thoughts/tickets/loomspan-pr-7.1-complete-execution-configuration.md`
- Research: `ai/thoughts/research/loomspan-pr-7.1-complete-execution-configuration.md`
- Profile: Full 5-Step Pipeline, Steps 2 and 3; revised September 27, 2026.
- Outcome: startup selects file or database authority for the entire execution configuration. Database publication includes encrypted credentials and supports optional portable ciphertext. File changes apply only at restart.
- This plan replaces the former mixed-source plan. Preserve and adapt substantial existing PR 7.1 work; earlier review documents are audit records, not implementation evidence.

## Current State

`RuntimeConfigurationService.run` rejects nonempty framework startup catalogs and always restores `ConfigurationSnapshotStore.current`. `StorageConfiguration.configurationSnapshotStore` initializes that selection unconditionally. `EffectiveExecutionConfiguration.assemble/restore` and `ManagedConfiguration.providerOverride` implement the superseded provider-only choice and can read deployment providers or external credential references. Drafts, retained ciphertext, exact candidate proofs, management credential endpoints and browser controls already exist. They must be adapted, not rebuilt from the old plan's inaccurate assertion that they are absent.

Framework startup binds deployment properties and eagerly builds provider clients before the Sidecar runner. An empty skill location alone does not prevent that dependency. Local framework `SkillReloader` supports explicit complete candidates plus a credential map without environment fallback, `snapshot().generationId()`, and physical-retirement callbacks. Its README documents no deferred startup switch. `GenerationRestResources` and `ExecutionCoordinator.handoff` require a durable UUID mapping before dispatch. `RestRouteLoader` parses text; there is no file reader yet.

`ConfigurationBundleV3` currently exports configuration only. The cipher already authenticates identifier/version with AES-GCM and carries the nonce in ciphertext; it is not environment-bound. The current browser and Python client expose neither optional encrypted export nor an explicit different-key import path. `pom.xml` uses the developer-installed beta 6 snapshot and JDK 21; tests use JUnit, SQLite, loopback providers and Playwright.

## Desired End State

- `loomspan-sidecar.configuration.mode=file|database`, default `database`, and `LOOMSPAN_SIDECAR_CONFIGURATION_MODE` select authority once at startup. Invalid values fail before activation. Files, database contents and load failures never select or switch modes.
- File mode uses normal framework file startup for skills, providers, aliases, session and trace settings. Sidecar reads a REST route resource once at startup. A populated database does not override file content or require decryption of retained database publications. File changes need restart; Console/shared API configuration mutations are unavailable.
- Database mode starts with an isolated empty framework bootstrap and restores its selected complete publication. New databases have empty publication content. Provider values come only from retained encrypted database credentials. No file-provider merge, external secret resolution, or file reread occurs during validation, publication or recovery.
- Database saves revise private drafts only. A proof freezes YAML, credential values and version identities. Publication rechecks live authority, lease, exact revision/base and candidate equality, then activates that same candidate atomically.
- Ordinary reads expose configuration, mode and configured credential identifiers only. Ciphertext is included only in explicitly requested encrypted exports. No provider plaintext or encryption key enters durable records, ordinary responses, logs or exports.
- Database import is always draft-only. Default configuration-only exports require destination secrets. Credential-bearing imports authenticate all included ciphertext with the destination key, or the operator explicitly chooses configuration-only import and supplies destination replacements. Failed imports never replace a saved draft or active publication.
- Old captured roots, descendants, retries and physical work retain their generation's credentials/settings/resources. Database rollback uses retained encrypted versions in a new publication. File rollback deploys earlier files and restarts.

## Scope

### In scope

Mode selection and bootstrap isolation; file startup/read-only inspection/correlation; database-only candidate cleanup; authorization and mutation gates; encrypted bundle transport; browser/client parity; schema replacement, fixtures, integration tests and documentation.

### Out of scope

Live mode switching or file watching; provider environment fallback in database mode; a vault/key-rotation service; new framework SPIs; framework bean replacement; redesign of REST secret handling; framework installation or release publication. Management accounts, PATs, authentication, execution admission and process infrastructure retain their existing ownership.

## Active Project Guardrails

The framework remains validation and execution authority. Application and tests import only `ai.loomspan.api` from Loomspan; Spring public APIs are allowed. Retain ArchUnit enforcement and no Java skills. Preserve one admission owner, trusted JWT identity, one management API, private draft and explicit lease ownership, live authorization rechecks, user-chosen deployment workflows, physical retirement and the framework's single shutdown budget. Update the lens's obsolete mixed-source paragraph as part of implementation. No compatibility obligations justify keeping `providerOverride` or format variants.

## Impact and Risk Analysis

- Bootstrap is earlier than `ApplicationRunner`; isolation must operate before framework configuration binding. Checking only the public preparation overload would miss the startup failure.
- File startup must not decrypt or switch the database selection, and must map the actual initial framework generation before opening execution traffic. Failure staging routes or writing correlation metadata keeps dispatch closed.
- Credential import changes a persisted/security boundary. Retain original authenticated identifiers and versions, enforce bounded metadata, and recheck authorization after work/locks. Wrong keys must not cause ciphertext deletion.
- File inspection must not serialize raw Spring property maps containing literal keys, headers or Gemini material. Capture a redacted view once; no request-time file rereading. File metadata is not recovery authority.
- Source removal affects many fixture helpers: defaults that formerly inherited deployment providers now need explicit complete database candidates and encrypted fixture secrets.

## Implementation Approach

### A. Allowed bootstrap mechanism

Add a small Sidecar-owned configuration class, `configuration/ConfigurationModeConfiguration.java`, and immutable `configuration/ConfigurationMode.java`. Read the mode through Spring Boot `Binder` against the completed Environment; use one immutable result throughout startup and management. Do not depend on a configuration-properties bean when constructing the binding advisor, to avoid an early binding cycle.

Register a **static Spring Boot `ConfigurationPropertiesBindHandlerAdvisor` bean** using `AbstractBindHandler`. In database mode, `onStart` returns null for the documented publishable roots `loomspan.connections`, `loomspan.models`, `loomspan.session` and `loomspan.execution-trace.persistence` (and their descendants); delegate all other names. This skips their binding before value conversion or placeholder resolution, leaving empty provider/model maps and framework defaults for the initial empty generation. Do not name or reference framework property classes. File mode delegates every binding unchanged.

Before that advisor can be used, add a highest-precedence Sidecar `MapPropertySource` setting the documented `loomspan.skills.locations` to `classpath:/sidecar-empty-skills/*.yaml` in database mode. This is a list replacement, not an attempted empty-map override. Remove the unconditional override from `application.yml` so file mode uses documented normal file locations. Keep process-level framework properties (shutdown, observability and trace infrastructure) available. The advisor is a standard Spring binding hook; it neither replaces a framework bean nor adds a framework SPI.

Evidence: local Spring Boot 4.1.0 source JAR documents `ConfigurationPropertiesBindHandlerAdvisor` for configuration-properties binding and nullable `BindHandler.onStart`; the framework binds its documented properties through that mechanism. Tests must prove actual startup with malformed deployment providers/session values, environment-form keys, custom skill locations and working process settings. If that boundary fails for reasons the public Spring APIs cannot handle, stop and report the concrete missing contract rather than adding internal access or an elaborate property-source emulation layer.

### B. File startup and durable correlation

Add `loomspan-sidecar.rest-routes.location` to `config/RestRoutesProperties.java`, defaulting to a bundled empty routes resource. Use Spring `ResourceLoader` and the existing `RestRouteLoader`; one specified missing/unreadable/invalid resource fails startup. No automatic fallback or watcher. Framework `loomspan.skills.locations` and documented `loomspan.*` execution keys remain file provisioning inputs.

Branch `RuntimeConfigurationService.run` after registration of retirement handling. File mode uses the already-created `SkillReloader.snapshot()` without calling a reload/prepare path again. Prepare REST resources from the one captured resource, validate them against that public catalog, persist a sanitized file-startup record with a fresh UUID, and stage resources against the catalog's actual `generationId`. Open dispatch only after all steps succeed. Use existing resource retirement and shutdown ownership; close staged resources on every failed path.

Introduce a small `storage/FileConfigurationStartupStore.java` and a `configuration_file_startup` table in the replaced development schema. Store UUID, startup time and redacted inspection metadata (mode, public skill descriptors, configured execution settings/credential-presence indicators and safe route view). This record gives the existing `configurationSnapshotId` a durable file-startup identity; it is explicitly not a replayable configuration snapshot. Do not store file provider literals or ciphertext, and do not require `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` to run file mode. Preserve the database publication pointer, retained credentials and saved drafts across file-mode runs. Skip database selection initialization/loading/pruning in this branch. Bound startup-record retention using the existing retention setting and protect the current identity; explain that historical metadata follows retention, as database history does.

Capture inspection from documented property groups through Spring's binder into Sidecar-owned data, redact all provider secret fields/header values before storage or rendering, and use public catalog descriptors for effective skill inspection. Show omitted execution settings as framework defaults rather than claiming that an unavailable framework execution-properties getter was used. Inspection never activates anything or validates skills independently. A mode-aware current response identifies the file startup and read-only state; database responses continue to identify the published/intended snapshots. File history is labelled startup metadata. Bundle publication/import/rollback is the database authoring workflow; file deployment/rollback remains file provisioning plus restart, not reactivation of a retained database snapshot.

### C. Complete database candidates and storage

Remove `providerOverride`, file literal synthesis and external-reference fallback everywhere: `ManagedConfiguration`, `EffectiveExecutionConfiguration`, SQL V1/V3, repositories, draft copies, payloads, UI, bundle and client. `assemble` decrypts only the candidate's stored credential versions and passes its exact authored complete YAML and explicit map to public framework validation/preparation. `restore` uses retained complete YAML/ciphertext exclusively. Keep Sidecar's direct-secret write-boundary check without creating a competing model/skill validator. Do not accept Gemini credential-file/URI references in database mode: require managed credential JSON material via the public JSON reference field, so a stored URI cannot silently resolve a deployment file.

Retain AES-256-GCM with random 12-byte nonces and authenticated identifier/version metadata. Require base64 32-byte externally provisioned keys when database encryption/decryption is required; empty configurations may start without a key. File mode must not parse or require irrelevant encryption material. Reject NUL and invalid/oversized metadata used for authenticated identifiers/versions. Never log candidate maps or include decryption causes containing content. Publication, rollback and startup failure preserve ciphertext and the prior authority.

Keep the current exact proof/revision/base/live-authorization machinery. Candidate equality includes the complete YAML and every retained credential version/value, with no former `@sidecar.file:` exemption. Credential replacement always invalidates validation, including replacement with the same plaintext. Complete retained credential versions remain protected while old generations exist.

### D. Mode gate and encrypted portability

Write-boundary refinement (2026-09-27): reject syntactically unreadable execution
YAML before saving, with a fixed error that contains no submitted content. A parser
failure can hide later quoted or escaped literal credential fields; a lexical
fallback cannot safely classify that remainder. This replaces permissive malformed
YAML saving. Syntactically readable but semantically invalid drafts remain supported
for framework validation. The Console retains unsent text and the last saved draft
on rejection. No separate semantic validator or compatibility path is introduced.

Enforce database mode in the shared service boundary for every configuration mutation, including lease acquisition/renewal/handoff, draft creation/save/reconcile/discard, credential replace/remove, import load, rollback load, validation proof creation and publication. Gate direct runtime mutation entry points as well as controllers. Return HTTP 409 `configuration_read_only` for authorized attempts in file mode; ordinary authentication/authorization checks remain in force. A file-mode page read must not create a draft as a side effect. Account/PAT administration and execution remain available according to existing permissions.

Destructively extend the in-development format 3 bundle. Add an optional `credentials.json` payload, inventoried and bounded by the existing strict ZIP manifest rules, containing a fixed cipher-format marker and identifier/version/ciphertext records. Keep the key and plaintext absent. Preserve exact identifiers/versions on transport because they are authenticated data. Reject duplicate/unknown identifiers, malformed encodings, unsupported cipher formats and missing required records on a credential-bearing export. Default `includeEncryptedCredentials=false`; explicit true captures the published snapshot and its retained credential rows together under the publication/retention gate so pruning cannot produce a partial export. No ordinary read returns ciphertext.

Keep configuration-only export available with current read authority. Encrypted export requires current management Edit authority (the existing authority to replace managed credentials), with session/PAT intersection and live recheck after capture; do not add a new permission system. Document that this explicit export is sensitive. The import `credentialMode` is `included` by default for a credential-bearing bundle, or explicit `configuration-only`; configuration-only bundles naturally use destination credentials. `included` authenticates all records before replacing a draft. Missing/wrong keys or tampering returns a safe actionable credential error and leaves both draft and active state intact. Never silently downgrade to configuration-only. The explicit configuration-only choice keeps required identifiers and available matching destination-managed versions; missing identifiers require write-only replacement before validation. It must not delete ciphertext retained in unrelated snapshots/drafts. Both paths use the normal exact grant/revision/base admission and final live authorization checks.

## Phase 1: Mode, bootstrap and file activation

### Changes

- [x] Add `src/main/java/ai/loomspan/sidecar/configuration/ConfigurationMode.java` and `ConfigurationModeConfiguration.java` with immutable selection and the public Spring binding mechanism above.
- [x] Update `src/main/resources/application.yml`, `src/main/java/ai/loomspan/sidecar/config/RestRoutesProperties.java` and add `src/main/resources/sidecar-empty-rest-routes.yaml`.
- [x] Update `RuntimeConfigurationService.run/current/inspect/history`, `StorageConfiguration.configurationSnapshotStore`, and `GenerationRestResources` only as needed for initial generation mapping. Add `FileConfigurationStartupStore` and its sanitized record/table; preserve the database pointer.
- [x] Add `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java` for startup isolation, file authority, correlation, no reload and failure cleanup.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp '-Dtest=ConfigurationModeIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — real app startup proves both modes using only supported framework API.

### Optional developer checks

None.

## Phase 2: Remove mixed sources and enforce database mutation ownership

### Changes

- [x] Simplify `src/main/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfiguration.java`, `ProviderCredentialCipher.java` and runtime prepare/restore/candidate comparison.
- [x] Remove superseded fields from `storage/ManagedConfiguration.java`, `ConfigurationSnapshotRepository.java`, `ConfigurationDraftStore.java`, `ConfigurationSnapshotStore.java`, `src/main/resources/db/migration/V1__configuration_snapshots.sql` and `V3__management_drafts.sql`.
- [x] Gate shared editing/import/rollback/runtime entry points in `management/ManagementEditingService.java`, `ManagementConfigurationImportService.java`, `ManagementConfigurationRollbackService.java` and controllers; expose mode/read-only status without secret values.
- [x] Update `src/test/java/ai/loomspan/sidecar/support/SidecarApplicationFixture.java` and `RuntimePublicationFixture.java` to seed complete database candidates and encrypted fixture secrets explicitly. Replace mixed-source assertions across existing integration tests.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,ProviderCredentialCipherTest,ConfigurationDraftStoreTest,ConfigurationSnapshotRepositoryTest,ConfigurationSnapshotStoreTest,ManagementEditingServiceTest,ManagementEditingHttpIntegrationTest,RuntimeConfigurationIntegrationTest,RuntimeConfigurationRecoveryIntegrationTest' test` — no fallback, plaintext persistence or mutation bypass; existing proof/recovery gates still hold.

### Optional developer checks

None.

## Phase 3: Authenticated encrypted bundles

### Changes

- [x] Extend `src/main/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV3.java` and `management/ManagementConfigurationController.java` with bounded optional ciphertext export and explicit import policy.
- [x] Update `ManagementConfigurationImportService` and the existing draft replacement path so import is atomic after integrity, cipher authentication and current grant checks; review responses expose only statuses/identifiers.
- [x] Extend `src/test/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV3Test.java` and `configuration/ManagementConfigurationHttpIntegrationTest.java`; add `configuration/CredentialPortabilityIntegrationTest.java` for two-database same/different-key workflows and denied late authorization.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp '-Dtest=ConfigurationBundleV3Test,ManagementConfigurationHttpIntegrationTest,CredentialPortabilityIntegrationTest' test` — configuration-only and encrypted archives roundtrip, altered metadata/ciphertext cannot activate, failed imports preserve state.

### Optional developer checks

None.

## Phase 4: Console, authoring client and operator guidance

### Changes

- [x] Update `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java`, `src/main/resources/static/management/assets/editor.js` and `console.css`: visible mode, read-only file inspection, remove provider override control, retain usable provider/model/session/trace editors and write-only credential controls.
- [x] Add explicit encrypted-export checkbox and included/configuration-only import choice with same-key/different-key guidance; no password prefill or echoed secret. Clear entered keys after submission and failures without putting values in diagnostics.
- [x] Update `agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py`, its tests, `SKILL.md`, `references/client-usage.md` and `references/execution-configuration.md` to use the same endpoints and export/import options. Secret input remains separate from ordinary configuration/readback; never print it.
- [x] Update `README.md`, `docs/operations.md` and `ai/thoughts/design-lens.md`: both complete modes, environment selection/restart, file locations/literal-or-external secret examples, encrypted provider-key rotation, cryptographically random key provisioning, missing/wrong-key recovery, consistent database backup plus separately provisioned shared key, bundle transport choices and independent release sequencing. Explain deliberate V1/V3 schema/format replacement and required backup/development reset; perform no reset.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp '-Dtest=ManagementEditorBrowserIntegrationTest,ManagementImportBrowserIntegrationTest,ManagementHistoryBrowserIntegrationTest,RemoteAuthoringWorkflowIntegrationTest' test` — browser/PAT complete workflows and file-mode read-only controls.
- [x] `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` — shared payloads/options, failure handling and secret-free client output.

### Optional developer checks

Use a disposable installation to assess clarity of mode/key/import wording. This is nonblocking; browser tests establish functional acceptance.

## Phase 5: Capture lifetime and complete safe verification

### Changes

- [x] Extend `src/test/java/ai/loomspan/sidecar/execution/ExecutionConfigurationCorrelationIntegrationTest.java` to publish complete database settings and credential versions while old work, delayed descendants and retries are blocked on loopback fixtures.
- [x] Reuse `rest/RestGenerationIntegrationTest.java`, `rest/RestHandlerLifecycleIntegrationTest.java` and `execution/ExecutionShutdownIntegrationTest.java` for public lifecycle/retirement coverage alongside new file startup/execution/close tests. Only source-contract fixture changes were needed in existing lifecycle coverage.
- [x] Finish acceptance mapping with measured outcomes; no framework-test substitution. Preserve failures/fault semantics and saved drafts under invalid preparation.

### Automated verification

- [x] `.\mvnw.cmd -B -ntp '-Dtest=ExecutionConfigurationCorrelationIntegrationTest,RestGenerationIntegrationTest,RestHandlerLifecycleIntegrationTest,ExecutionShutdownIntegrationTest' test` — old/new isolation, physical retirement, durable mapping and one shutdown budget.
- [x] `.\mvnw.cmd -B -ntp verify` plus the Python suite above — broad safe repository verification. Browser binaries are prerequisites, not grounds to skip acceptance silently.

### Optional developer checks

None beyond Phase 4. No billable provider, production database, Docker deployment or framework release operation is a routine test gate.

## Test Strategy

Use pure cipher/bundle/candidate tests for structural security, real temporary SQLite/app contexts for startup and recovery, HTTP session/PAT tests for service authorization, Playwright for usable controls and loopback fake providers for execution evidence. Run the first red startup test before implementing the binding guard. Tests use synthetic credentials and isolated temporary databases; the separate testing plan specifies exact scenarios and safe commands.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| AC1 File authority, restart-only changes, empty/populated database, no mutations | Mode guard; file branch; file-startup store; shared mutation gate | ConfigurationModeIntegrationTest; file-mode HTTP/browser cases |
| AC2 Complete Console/API database editing independent of deployment, empty new database | Simplified EffectiveExecutionConfiguration; bootstrap advisor; full controls/client | Startup poison inputs; ManagementEditingHttpIntegrationTest; browser/PAT workflow |
| AC3 Draft-only edits, same candidate, existing ownership/proof guarantees | Draft replacement; immutable proof; publication gate | Exact revision/base/lease and credential-version invalidation tests |
| AC4 Encrypted storage, secret-free reads/errors/exports, key recovery | Cipher; schema/repositories; redacted views; operations | Cipher/SQL leakage scans; missing/wrong-key restart; HTTP/browser/client output tests |
| AC5 Database restart/history/rollback and explicit authority | Mode branches; retained candidate restore/rollback; pointer preservation | Recovery integration; file-to-database restart; complete rollback tests |
| AC6 Optional encrypted portability and explicit different-key path | Strict bundle extension; gated capture/import policy; UI/client | Bundle negatives; CredentialPortabilityIntegrationTest; browser/client options |
| AC7 Old/new settings/credentials/resources and no-call invalid preparation | Explicit map prepare; generation mapping/retirement | Blocked roots, children, retries; fake-provider counters; shutdown tests |
| AC8 Public API, snapshot integration, guidance/lens | ArchUnit; installed dependency; updated docs/authoring | SupportedLoomspanApiArchitectureTest; full verify; Python suite; document review |

## Risks and Rollback/Recovery

Do not reset existing working files or discard the substantial implementation. Replace obsolete development fields/schema directly and document that old development databases/bundles require backup and recreation; no migration scaffolding or automated deletion. File-mode startup writes only sanitized correlation metadata and never changes selected database content, so switching back on restart restores the previous database authority. Startup/preparation/import/key failures keep traffic closed or the current generation intact as appropriate and preserve ciphertext. In database mode, a consistent full database backup and the original externally stored key are recovery inputs; in file mode use previous file content plus restart. Provider revocation can invalidate old executions and is accepted. Keep beta 6 SNAPSHOT installed locally for integration; hosted release CI waits for the published release, with no snapshot repository added.

## References

- Governing ticket, refreshed research and design lens linked above.
- `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java`
- `src/main/java/ai/loomspan/sidecar/rest/GenerationRestResources.java`
- Local framework `README.md`, `src/main/java/ai/loomspan/api/SkillReloader.java` and `SkillCatalog.java`.
- Local Spring Boot 4.1.0 source JAR: public `ConfigurationPropertiesBindHandlerAdvisor`, `AbstractBindHandler`, `BindHandler`.

## Step 4 execution evidence

- 2026-09-27: `.\mvnw.cmd -B -ntp '-Dtest=ConfigurationModeIntegrationTest' test` initially failed (1 error) at conversion of the deliberately invalid deployment driver, establishing the intended red startup boundary. Output: `target/mode-red.log`.
- The first binding advisor correctly skipped conversion, but Spring strict binding reported deliberately skipped inputs as unbound. Routine refinement: the advisor's public `onFinish` tolerates `UnboundConfigurationPropertiesException` only when every reported property belongs to the four excluded execution roots. All other unbound settings and validation failures still propagate. No framework types, reflection, bean replacement, or property-source emulation are involved.
- The same focused startup command then passed (1 test, zero failures/errors/skips), output `target/mode-guard.log`. This initial focused result was subsequently superseded by the complete clean verification below.
- Existing uncommitted PR 7.1 implementation is ticket work per refreshed research. No unrelated changes, releases, developer databases, or framework installation were modified.

### Implementation refinements and verification environment

- The database bootstrap guard uses only Spring's public binding advisor and the documented empty skill location. Its strict-binding exception filter tolerates only deliberately excluded execution roots; unknown process keys still fail. File mode delegates normal framework bootstrap unchanged. No framework rebuild or dependency switch was performed.
- File startup correlation lives in a separate bounded `configuration_file_startup` table. It stores safe metadata only and is never a restore candidate. File-mode startup leaves the selected database pointer, private drafts and encrypted rows intact.
- Ciphertext metadata is restricted to bounded identifiers/versions before AAD construction, preventing delimiter ambiguity. Optional transfer uses the existing strict format-3 archive inventory; authenticated ciphertext is loaded atomically only after explicit policy and current grant checks. Configuration-only loading keeps matching local draft credential identifiers and leaves unrelated retained snapshots untouched.
- Production Compose and its disposable verifier needed the same destructive source-contract update: provision `LOOMSPAN_SIDECAR_CREDENTIAL_KEY`, then write provider credentials through the draft API. The configured verifier is updated but is not a routine execution gate and was NOT RUN.
- The running editor writes diagnostic class files into normal `target/classes` and `target/test-classes`. An ordinary Maven incremental run consumed those class files; `clean test-compile` exposed the discrepancy. Verification therefore uses a temporary `.codex-verification-pom.xml` copied from `pom.xml`, with only `<directory>${project.basedir}/target/verification</directory>` added to its build. All later Maven commands use `-f .codex-verification-pom.xml`; dependencies, compiler/test configuration and source roots are identical. The temporary POM is removed after verification. No editor process was stopped. The initial red/green mode log files were removed by the intervening clean; their command/outcome is recorded above.
- Overlap proof uses three real loopback scenarios: blocked root, delayed model-backed child and a physical HTTP 503 provider retry. Existing route-generation, physical retirement and shutdown tests remain complementary evidence rather than being duplicated.

### Final measured verification (2026-09-27)

All phase verification checkboxes above are satisfied by the final clean full-suite
run, which is a superset of the listed planned focused selectors. They do not claim
each illustrative selector command was separately executed.

| Actual command | Result |
| --- | --- |
| `.\mvnw.cmd -B -ntp '-Dtest=ConfigurationModeIntegrationTest' test` | Initial intentional RED: 1 error from invalid deployment driver conversion. Subsequent initial guard run GREEN: 1 test. |
| `.\mvnw.cmd -f .codex-verification-pom.xml -B -ntp '-Dtest=CredentialEnvironmentIntegrationTest,CredentialPortabilityIntegrationTest,ManagementConfigurationHttpIntegrationTest' test` | PASS: 8 tests; actual environment-key startup, credential transfer/tamper, capture authorization. |
| `.\mvnw.cmd -f .codex-verification-pom.xml -B -ntp verify` (first full iteration) | FAIL: 243 tests, 2 failures, 0 errors, 3 gated skips. Fixed obsolete mounted-skill expectation and new test's incorrect management URL. |
| `.\mvnw.cmd -f .codex-verification-pom.xml -B -ntp verify` (second full iteration) | FAIL: 246 tests, 1 failure, 0 errors, 3 gated skips. Fixed production-env reference assertion after replacing external provider secret with encryption key. |
| `.\mvnw.cmd -f .codex-verification-pom.xml -B -ntp clean verify` | **PASS: 246 tests, 0 failures, 0 errors, 3 gated skips; JAR packaged.** Final code including strengthened portability runtime calls, model/quota/credential overlap, file-mode browser, Console publication and PAT export matrix. `target/clean-final.log`; finished 15:58:11 local. |
| `.\mvnw.cmd -f .codex-verification-pom.xml -B -ntp '-Dtest=ConfigurationReferenceTest' test` | PASS: 2 tests after final quickstart/docs edits. `target/reference-final.log`. |
| `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` | PASS: 11 tests including explicit encrypted export and configuration-only import options. `target/client-final.log`. |
| `python -m unittest discover -s scripts -p 'test_*.py'` | PASS: 7 tests, including guidance and mocked shutdown-script failure/cleanup cases. `target/python-final.log`. |
| `python -m py_compile scripts/verify-production.py` | PASS: updated disposable-production script syntax. |
| `git -c core.safecrlf=false diff --check` | PASS. |

The three skipped tests are `ProductionComposeBrowserIntegrationTest`, intentionally
activated by the disposable Docker production verifier. They are **NOT RUN**, not
missing-Chromium passes. Local editor, history, import/export and file-mode Chromium
tests actually executed. Docker production verification, live providers, deployment,
framework rebuild/release, release dependency switch and hosted CI were NOT RUN.
The testing plan excludes those operations from routine Step 4 verification.

### Measured acceptance mapping

| Criterion | Implementation and executable Sidecar evidence |
| --- | --- |
| AC1 | Immutable `ConfigurationModeConfiguration`, file branch and separate startup store. Six `ConfigurationModeIntegrationTest` cases cover invalid/poisoned settings, environment selection, empty/populated DB, real file model and REST calls, safe UUID correlation and restart-only changes; `FileModeManagementHttpIntegrationTest` rejects all 16 authenticated mutation routes and runs Chromium read-only inspection. Environment-key test proves irrelevant malformed key is ignored in file mode. |
| AC2 | `EffectiveExecutionConfiguration` consumes only explicit decrypted map plus complete authored YAML. Startup guard, RuntimeConfiguration integration, Console credential save/validate/publish, editing HTTP/PAT and client tests prove empty startup, no deployment fallback and no restart for publication. |
| AC3 | Private draft store, immutable candidate/version proof and shared live authorization remain enforced. Editing service/HTTP/PAT, draft restart, runtime fault and remote authoring tests cover revision/base/grant changes, draft-only edits, credential replacement and failure preservation. |
| AC4 | AES-GCM cipher and bounded authenticated metadata; snapshot/draft credential tables; redacted views/write-only controls. Cipher, draft/repository SQL assertions, environment-key child JVM, portability tamper, browser secret-clearing and safe-error tests prove encryption and actionable failure without state loss. |
| AC5 | Database restore uses retained complete effective YAML and encrypted rows. RuntimeConfiguration integration/recovery, credential environment/portability restart, draft retention/rollback and file-to-database restart prove selected authority and preserved local IDs/history. |
| AC6 | Format-3 optional credentials inventory, atomically captured authorized export, explicit import policy. Strict archive tests; source/same-key/different-key/missing-key/tampered imports; real loopback execution after import/restart/replacement; encrypted-download/import-choice Chromium; Read/Edit/Publish PAT matrix; client options all pass. Encryption key is never a payload field. |
| AC7 | Explicit public complete-candidate prepare and existing generation mapping. Five correlation cases include blocked roots, delayed model children and physical 503 retries retaining old endpoint/model/key and multi-attempt quota while new work uses new values. Existing nested REST/retirement/handler lifecycle/shutdown tests prove captured routes, physical cleanup and durable IDs. Invalid preparation is tested against loopback or unreachable fixture endpoints without model calls and preserves active state. |
| AC8 | SupportedLoomspanApiArchitectureTest passes against the installed beta.6-SNAPSHOT; no forbidden framework dependency or rebuild. Full clean verify, Python guidance/client suites and updated operations/design lens/production and quickstart examples complete repository evidence. |

Coverage composition refinement: generation-lifetime scenarios demonstrate endpoint,
provider model, credential and quota isolation with real calls; complete trace/session
YAML preparation/persistence/restore is covered by RuntimeConfiguration integration.
Existing public resource/shutdown tests are reused instead of duplicating framework
internals or inventing lifecycle hooks. All fixtures use synthetic values and temporary
SQLite/loopback services. No developer data or unrelated working changes were removed.

Optional developer observations: assess file-mode inspection and wrong-key replacement
instructions on a disposable installation, and exercise separate key/database backup
provisioning. These remain nonblocking usability/operations checks. Fresh independent
Step 5 review is the next pipeline gate.
