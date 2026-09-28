# PR 7.1 File or Database Configuration Testing Plan

## Change Summary

**Development policy: replace superseded contracts, schemas and tests directly; no compatibility shims or obsolete source-selection paths.** Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). Development resets are documentation, not permission to delete deployed data.

This Step 3 plan develops the revised September 27 implementation plan in the same Full 5-Step Pipeline context. Test startup-selected `file|database` authority, encrypted database-only providers, frozen complete candidates, read-only file management, retained execution lifetime and optional authenticated ciphertext portability. The prior provider-override/file-drift tests describe obsolete behavior and must be replaced. No tests were run in planning; all evidence below is planned.

Inputs:

- `ai/thoughts/tickets/loomspan-pr-7.1-complete-execution-configuration.md`
- `ai/thoughts/research/loomspan-pr-7.1-complete-execution-configuration.md`
- `ai/thoughts/plans/loomspan-pr-7.1-complete-execution-configuration.md`

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Bootstrap | Framework eagerly reads invalid deployment providers before DB restore | Actual Spring application startup with poisoned YAML/CLI/environment properties; no calls or file credential reads |
| Mode authority | Populated DB overrides file content or failure triggers fallback | Empty/populated/wrong-key DB file startups; selected-pointer assertions; mode-switch restart tests |
| File lifetime | REST resource or correlation missing at handoff; files reread live | Real public handoff, non-null stored startup UUID, edit files after start, unchanged results until restart |
| Database preparation | External provider secrets or Gemini credential files leak into candidate | Explicit encrypted values versus conflicting environment; missing-map and file-URI rejection |
| Proof/concurrency | Save or key replacement activates, or stale proof publishes different content | Active ID/counters unchanged on saves; revision/base/lease and credential-version revalidation |
| Cipher/security | Plaintext in SQLite, views, bundles, errors, logs or UI | Cipher tampering tests, exact SQL assertions, decompressed ZIP scans, captured output/DOM checks |
| Portability | Imported metadata changes AAD; wrong key silently loses ciphertext | Two temporary databases with same/different keys, metadata/ciphertext tamper, explicit configuration-only replacement |
| Authorization | Ciphertext export/import bypasses edit or late revocation | Session and cumulative Read/Edit/Publish PAT matrix, lock-delayed revocation, CSRF |
| Recovery/lifecycle | Restart adopts changed files; old children/retries use new settings | Real selected restore, complete rollback, blocked roots/children/retries and physical retirement |
| Framework boundary | Isolation hook imports internals or tests fake framework behavior | ArchUnit plus actual app startup/public execution APIs against installed snapshot |

## Existing Coverage and Environment Constraints

`pom.xml` runs JUnit 5/AssertJ under Surefire, uses JDK 21, Spring Boot 4.1.0, SQLite and Playwright 1.55.0. The Maven wrapper is checked in. Use the developer-installed `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.6-SNAPSHOT`; do not rebuild framework or verify artifact/source hashes. Hosted release CI is deferred until published beta 6 exists. Local verification must not inherit a `MAVEN_ARGS` release-version override from a CI example.

Existing tests already exercise private drafts, leases, live account/PAT checks, frozen candidate proofs, encrypted SQL rows, strict archives, recovery faults, generation mapping, shutdown and browser editing. The source-reviewed fixtures include `SidecarApplicationFixture` (loopback model/REST endpoints), `RuntimePublicationFixture`, `@TempDir` databases and test-only Sidecar cipher beans. Keep framework collaborators real; do not replace framework beans or import internal types. Sidecar-owned test hooks and injected cipher/clock fixtures are allowed.

All provider endpoints, issuer keys and callback services must be fixture-owned. Use randomly bound loopback ports, synthetic credentials and temp directories; do not use a developer database or real cloud endpoints. Count loopback requests to prove no-call preparation. Do not log synthetic secret markers as part of an assertion failure that is subsequently treated as a production leakage test. Capture only the application channel being asserted. Use bounded latches/timeouts, release blocked work in `finally`, and close contexts/providers.

Most encrypted tests can inject a Sidecar cipher with a test key. At least one real startup/restart test must exercise the production `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` environment boundary using a child JVM with a controlled environment and temporary database; do not mutate the host environment or pass the key on a command line. Similarly use `SystemEnvironmentPropertySource` or a controlled child process for the mode environment equivalent, not just a dotted-property surrogate. A test-only child entrypoint may live under `src/test/java/.../support` and must use only public APIs.

Playwright Chromium must be available for browser tests. Use the repository CI's installation command adapted to Windows if absent, then run the actual browser suite; do not label missing-browser skips a pass. Python client tests use local fakes and the standard library. Guidance tests in `scripts/test_agent_skills.py` also apply to the edited bundled skill.

## Failing Test First

- Name: `databaseModeBootstrapsDespiteInvalidDeploymentProviders`.
- Type: application integration, real installed framework.
- Location: new `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java`.
- Arrange/Act/Assert: start a new temporary database using the default mode, a deployment connection with an invalid driver/unresolved secret and a deployment skill location containing a valid file skill; assert startup succeeds, current mode is database, public catalog is empty, database content is empty, dispatch opens only after activation and no provider request occurs. Use a malformed numeric session value in a separate parameterized case to prove binding is skipped before conversion.
- Expected pre-fix failure: framework provider/session bootstrap fails or external file skills cause the current runner's nonempty-catalog rejection. The failure must be at the intended behavior, not from unavailable tools/credentials.
- Run this red test before implementing the mode isolation mechanism. After it is green, add file-mode cases; retain the original command/output summary in the implementation report.

## Tests to Add or Update

### 1. `ConfigurationModeIntegrationTest` — authority and bootstrap

- Type: real application startup/restart with temporary SQLite and file resources.
- Location: `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java`.
- Proves: default database selection, explicit file/database, environment-equivalent selection, invalid value failure, startup-only selection, no source fallback, process settings preserved.
- Inputs/fixture: actual external application YAML plus command-line and environment-style overrides; invalid provider driver, unresolved API-key placeholder, missing Gemini file URI, invalid session field, invalid model definitions and nonempty deployment skill locations. Database startup ignores those execution inputs. Verify documented process settings remain applied through observable existing shutdown/security/observability behavior or their public Sidecar configuration surface.
- Doubles: loopback endpoints and fixture JWTs only; real framework autoconfiguration and `SkillReloader`.
- Edge cases: poisoned settings appear at different precedence levels; new empty DB and populated valid DB both start; retained invalid ciphertext fails explicitly in database mode and never selects file fallback; invalid mode fails without opening dispatch. Mutating the process Environment's mode after startup cannot change runtime/service authority.

### 2. `fileModeUsesFilesAndPersistsOnlySafeStartupIdentity`

- Type: application integration and public execution handoff.
- Location: same `ConfigurationModeIntegrationTest.java`, with reusable fixture inputs under `src/test/resources/fixtures/`.
- Proves: complete file skill/model/REST/session/trace configuration works with empty and populated DB; retained DB selection/drafts/ciphertext stay unchanged. File mode works without a key and with irrelevant malformed/wrong DB encryption material.
- Inputs/fixture: temp skill files, application YAML and route resource; literal provider key and external reference variants, loopback model/callback. Change files after activation; old execution/settings/routes remain until context restart, then new file content takes effect.
- Doubles: existing local provider fixture; actual framework catalog and `ExecutionCoordinator` admission. Inspect SQL for startup UUID and redacted metadata, and correlate both model-only and REST execution IDs with that UUID.
- Edge cases: unreadable/missing/invalid explicitly configured routes, skill failure, route/catalog mismatch, durable startup-record write failure and staging failure keep dispatch closed and release owned resources. A file error never restores DB configuration. Startup metadata uses bounded retention and protects the active identity. File-to-database restart restores the unchanged selected DB publication.

### 3. `databaseCandidatesNeverResolveDeploymentCredentials`

- Type: candidate unit tests plus actual preparation integration.
- Locations: `src/test/java/ai/loomspan/sidecar/configuration/EffectiveExecutionConfigurationTest.java` and `RuntimeConfigurationIntegrationTest.java`.
- Proves: only complete authored YAML and encrypted versions determine candidate content; providerOverride/file-literal special cases are gone. Framework remains model/skill validator.
- Inputs/fixture: multiple providers, aliases, API-key/header/Vertex JSON references, session limits/quotas and trace persistence; conflicting environment secrets and providers. Missing credentials fail even when matching external values exist. Omitted provider/model fields stay omitted; no fallback merge.
- Doubles: cipher unit fixture and loopback providers; framework prepare is real in integration cases.
- Edge cases: blank/missing/unused map values, invalid alias/driver, direct secret fields, process keys inside candidate, unsupported model options, credentials-ref URI attempt rejected before any file access, complete Vertex JSON material supported. Assert zero model calls for valid preparation and invalid preparation alike, unchanged active UUID and preserved draft on failures.

### 4. `credentialEncryptionAndRecoveryPreserveCiphertext`

- Type: cipher/store unit and database restart integration.
- Locations: `src/test/java/ai/loomspan/sidecar/configuration/ProviderCredentialCipherTest.java`, `storage/ConfigurationDraftStoreTest.java`, `storage/ConfigurationSnapshotRepositoryTest.java`, `configuration/RuntimeConfigurationRecoveryIntegrationTest.java`.
- Proves: randomized authenticated encryption; durable drafts/current/history credentials are encrypted; missing/wrong external keys cannot destroy or bypass stored secrets.
- Inputs/fixture: same plaintext encrypted twice, identifier/version variants, key absent/malformed/wrong, changed nonce/body/tag, truncated/invalid base64, invalid/NUL metadata. Assert successful decryption only with exact original metadata/key.
- Doubles: synthetic key in ordinary tests; controlled environment child startup for the production environment contract.
- Edge cases: scan SQL text/blob columns and main/WAL files for distinctive plaintext/key markers while also asserting exact encrypted rows (raw byte absence alone is insufficient); ordinary snapshot/draft/history responses remain secret-free. Failed restart preserves selected pointer and every ciphertext record; original key plus consistent backup restores successfully. Empty database can start without a key. File mode does not need or evaluate database key material.

### 5. `draftCredentialsObeyExactProofAndAuthorization`

- Type: service and session/PAT HTTP integration.
- Locations: `src/test/java/ai/loomspan/sidecar/management/ManagementEditingServiceTest.java`, `ManagementEditingHttpIntegrationTest.java`, `ManagementDraftRestartIntegrationTest.java`, `RemoteAuthoringWorkflowIntegrationTest.java`.
- Proves: credential save/remove stays draft-only; every changed version invalidates proof; private drafts, lease, revision/base and live authority apply to all added content.
- Inputs/fixture: Read/Edit/Publish cumulative PATs and browser roles; two users; stale lease generation, stale revision/base, handoff, logout, token revocation/expiry, account disable/role downgrade. Save the same plaintext again and assert the changed version still requires validation.
- Doubles: existing test clocks/Sidecar hooks; real storage and HTTP security.
- Edge cases: block publication/import mutation at an existing gate, revoke permission, release and assert no mutation; restore draft after restart with encrypted credentials but no surviving validation proof. Failed validation/publication preserves draft/revision/ciphertext according to existing failure semantics. Save and credential operations cannot change the active generation or make provider requests.

### 6. `fileModeRejectsEveryConfigurationMutation`

- Type: shared service plus authenticated HTTP/browser integration.
- Locations: `src/test/java/ai/loomspan/sidecar/configuration/ManagementConfigurationHttpIntegrationTest.java`, `management/ManagementEditingHttpIntegrationTest.java`, `ManagementEditorBrowserIntegrationTest.java`.
- Proves: file mode reports mode/read-only status and effective redacted configuration; server restrictions cover direct API calls, not just disabled UI.
- Inputs/fixture: table-driven operation inventory from controllers: acquire/renew/handoff, save/reconcile/discard, credential replace/remove, import load, rollback load, validate and publish. Assert authorized attempts return 409 `configuration_read_only` and leave DB draft/selection/credential rows unchanged. Read requests must not create drafts.
- Doubles: fixture accounts/PATs and loopback services.
- Edge cases: anonymous/insufficient privilege remains denied by existing auth; CSRF still applies to session mutations; PAT credentials do not bypass mode. Account/PAT administration and execution remain usable under their existing permissions. DOM displays no secret/override checkbox/edit action; ordinary GET inspection stays available and stable after file edits.

### 7. `ConfigurationBundleV3Test` — strict optional ciphertext format

- Type: pure archive parser/writer tests.
- Location: `src/test/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV3Test.java`.
- Proves: configuration-only default and explicit encrypted option, exact metadata preservation, strict bounded inventoried optional payload and key/plaintext exclusion.
- Inputs/fixture: complete multi-provider configuration with several credential versions; decompress entries and assert default has identifiers but no ciphertext payload, opt-in contains exact original identifier/version/ciphertext and fixed cipher format. Inspect all expanded entries for plaintext and encryption key.
- Doubles: none beyond synthetic records.
- Edge cases: retain duplicate/unknown entry, path, size, CRC/inventory, UTF-8, malformed ZIP and trailing/duplicate JSON tests. Add unknown cipher format, duplicate credential identifier, invalid metadata/encoding, mismatched identifiers, missing records, extra unreferenced records and dishonest inclusion metadata. Old providerOverride format content is rejected under destructive development replacement. Format parsing does not decrypt, invoke providers or expose metadata values in unsafe errors.

### 8. `CredentialPortabilityIntegrationTest` — same/different-key workflows

- Type: two independent app/database contexts through the shared management API.
- Location: new `src/test/java/ai/loomspan/sidecar/configuration/CredentialPortabilityIntegrationTest.java`.
- Proves: same-key credential-bearing import can validate/publish/run without reentry; different-key explicit configuration-only/replacement path succeeds only after destination credentials; all loads remain draft-only.
- Inputs/fixture: source publication has API key, extra header and representative JSON secret; destination has the same key, a different key, or no key. Export a captured publication, import/review, acquire exact grant, load, validate, publish and invoke a loopback skill. Compare actual received authorization/model/route outputs and durable IDs. Restart destination and rollback after later credential replacement to prove retained versions survive.
- Doubles: local fake provider; public framework API; Sidecar-owned key fixture.
- Edge cases: wrong/missing key or tampered ciphertext/identifier/version returns actionable safe error and changes neither existing draft nor active state. Recompute archive hashes after tampering so AES authentication, rather than only ZIP integrity, is exercised. Explicit configuration-only import ignores included credentials by operator choice, preserves required IDs and matching destination-managed values, and requires missing replacements. Original source archive/ciphertext and unrelated retained rows remain intact. Race export capture against publication/pruning; every archive contains one complete version. Missing retained credentials produce a failed export/rollback, not a partial archive or partial activation.

### 9. `encryptedTransferRechecksLiveAuthorityAndNeverEchoesSecrets`

- Type: HTTP security and output-capture integration.
- Locations: `CredentialPortabilityIntegrationTest.java`, `configuration/ManagementConfigurationHttpIntegrationTest.java`, `management/RemoteAuthoringWorkflowIntegrationTest.java`.
- Proves: default read exports retain read permission; explicit ciphertext export needs current Edit permission; import load needs current edit grant and publication needs Publish. Browser sessions and PAT intersection share rules.
- Inputs/fixture: read-only user/token, editor, publisher, revoked/expired token, disabled user and mixed session/bearer credentials. Pause export capture/import load on a deterministic Sidecar hook, revoke/downgrade, release and assert no sensitive transfer/mutation.
- Doubles: test clock/hook; actual authorization chain.
- Edge cases: session CSRF on modifying routes, private-draft isolation, no-store responses; key/secret markers absent from errors, logs, review payloads, ordinary reads and denied responses. Explicit encrypted export contains ciphertext only; its permission test must not accidentally decrypt into HTTP output.

### 10. `completeConfigurationControlsAndPortableAuthoring`

- Type: Playwright and Python client tests, plus existing remote API integration.
- Locations: `src/test/java/ai/loomspan/sidecar/management/ManagementEditorBrowserIntegrationTest.java`, `ManagementImportBrowserIntegrationTest.java`, `ManagementHistoryBrowserIntegrationTest.java`; `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`; `scripts/test_agent_skills.py`.
- Proves: usable provider/model/session/trace controls, write-only credentials, explicit encrypted-export option and clear included/configuration-only import choice work through the same services as remote clients.
- Inputs/fixture: browser creates complete provider-backed config, replaces key, sees draft still inactive, validates/publishes, exports both forms, imports with same key and handles wrong-key replacement. Assert actual runtime outputs, not only DOM labels.
- Doubles: loopback provider, fixture users/keys, mocked HTTP transport for Python argument/payload/error-output tests.
- Edge cases: password never prefilled or echoed, input cleared on completion/failure, obsolete override control absent, reload/private draft state preserved, file mode is visibly read-only. Python adds explicit flags/parts, never prints credential input or key, supports mode errors, and does not automate source fallback or bypass exact revisions.

### 11. `capturedRootsChildrenRetriesKeepFullPublication`

- Type: real framework/Sidecar concurrent execution with loopback provider and REST fixtures.
- Locations: `src/test/java/ai/loomspan/sidecar/execution/ExecutionConfigurationCorrelationIntegrationTest.java`, `rest/RestGenerationIntegrationTest.java`, `rest/RestHandlerLifecycleIntegrationTest.java`, `execution/ExecutionShutdownIntegrationTest.java`.
- Proves: old captured roots and delayed nested/parallel children/retries retain old key, endpoint/model alias, session/quota and trace selection plus REST resources; new handoffs use the new complete publication.
- Inputs/fixture: A and B publications differ in credentials, model names/endpoints, limits, trace persistence and routes. Block A after handoff but before spawning child/retry, publish B, run B, release A, observe provider headers/model request bodies/REST callback and retained diagnostics/limit outcomes. Use deterministic fake HTTP retry responses and explicit latches; do not rely on sleeps or inspect framework internal fields.
- Doubles: loopback scripted providers and Sidecar hooks only. Record generation retirement through the public callback and Sidecar resource state.
- Edge cases: root result/cancellation returns before blocked physical work; resources remain until physical completion, then close. Queue a root before publication and hand it off after publication to prove handoff is the boundary. Every result maps to its captured durable publication/startup UUID, including model-only work and the existing mapping race. Pruning must retain active resources/versions. Shutdown tests preserve framework's one budget and file-startup resource cleanup. Invalid preparation makes zero billable/model requests and preserves active state.

### 12. `SupportedLoomspanApiArchitectureTest` and recovery regressions

- Type: architecture and existing broad integration suite.
- Locations: `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`, `configuration/RuntimeConfigurationRecoveryIntegrationTest.java`, `storage/ConfigurationSnapshotStoreTest.java`.
- Proves: no forbidden internal/autoconfigure dependencies or Java skills; bootstrap uses Spring APIs; exact durable pending/published/failed state behavior, backups, rollback and mutation-fault behavior survive the mode changes.
- Inputs/fixture: retain existing controlled fault hooks and SQLite failure fixtures; update only obsolete fields/fixtures. Explicitly test restart with changed/poisoned deployment files and no external credentials, and full complete-configuration rollback using encrypted retained versions as a new ID.
- Doubles: existing Sidecar fault hooks; real framework snapshot integration.
- Edge cases: fault after activation must not claim rollback; restore original key/backup explicitly; no automatic source fallback. Existing admission/auth/REST/diagnostic regression suites continue to pass.

## Acceptance Traceability

| Ticket acceptance | Tests above |
| --- | --- |
| AC1 File startup/complete files/read-only/restart | 1, 2, 6, 10 |
| AC2 Complete database Console/API/client with empty bootstrap and no fallback | 1, 3, 5, 10 |
| AC3 Draft-only exact candidate/proof/authorization | 3, 5, 6, 9 |
| AC4 Encryption, no disclosure, actionable key recovery | 4, 7, 8, 9, 10 |
| AC5 Restart/history/rollback and selected authority | 1, 2, 4, 8, 12 |
| AC6 Configuration-only/encrypted portability and explicit replacements | 7, 8, 9, 10 |
| AC7 Handoff/children/retries/resources/correlation and no-call invalid preparation | 2, 3, 11, 12 |
| AC8 Supported framework boundary, installed snapshot, guidance and lens | 10, 12, full safe suites and implementation documentation review |

## Safe Verification Commands

Run from `C:/opendev/code/loomspan-sidecar` in PowerShell. Commands use only test fixtures and the installed snapshot. Quote Maven property arguments as shown.

- First red/focused: `.\mvnw.cmd -B -ntp '-Dtest=ConfigurationModeIntegrationTest#databaseModeBootstrapsDespiteInvalidDeploymentProviders' test`
- Modes/boundary: `.\mvnw.cmd -B -ntp '-Dtest=ConfigurationModeIntegrationTest,SupportedLoomspanApiArchitectureTest' test`
- Candidate/storage: `.\mvnw.cmd -B -ntp '-Dtest=EffectiveExecutionConfigurationTest,ProviderCredentialCipherTest,ConfigurationDraftStoreTest,ConfigurationSnapshotRepositoryTest,ConfigurationSnapshotStoreTest,RuntimeConfigurationIntegrationTest,RuntimeConfigurationRecoveryIntegrationTest' test`
- Management/portability: `.\mvnw.cmd -B -ntp '-Dtest=ManagementEditingServiceTest,ManagementEditingHttpIntegrationTest,ManagementConfigurationHttpIntegrationTest,CredentialPortabilityIntegrationTest,ManagementDraftRestartIntegrationTest,RemoteAuthoringWorkflowIntegrationTest,ConfigurationBundleV3Test' test`
- Browser: `.\mvnw.cmd -B -ntp '-Dtest=ManagementEditorBrowserIntegrationTest,ManagementImportBrowserIntegrationTest,ManagementHistoryBrowserIntegrationTest' test`
- Lifecycle: `.\mvnw.cmd -B -ntp '-Dtest=ExecutionConfigurationCorrelationIntegrationTest,RestGenerationIntegrationTest,RestHandlerLifecycleIntegrationTest,ExecutionShutdownIntegrationTest' test`
- Python client: `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`
- Bundled guidance: `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`
- Full safe Maven suite: `.\mvnw.cmd -B -ntp verify`
- Browser setup, only if missing: `.\mvnw.cmd -B -ntp test-compile org.codehaus.mojo:exec-maven-plugin:3.6.2:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.classpathScope=test' '-Dexec.args=install chromium'`

The setup command downloads local tooling, not a product deployment. Do not run production verification scripts, live provider experiments, framework rebuilds, data resets or release-profile publication as routine test steps. Focused suites support iteration; after meaningful checks pass, run full verify and both applicable Python suites once, repeating only to resolve changed code or concrete failures.

## Optional Developer Checks

On a disposable installation, assess whether the read-only mode view and wrong-key/configuration-only instructions are clear, and follow the documented separate key plus database-backup provisioning workflow. These are optional usability observations, not substitute proof or completion gates. No live model request is needed.

## Exit Criteria

- [x] First red startup test failed for the intended pre-fix reason; implementation report records the actual command/outcome.
- [x] All new/revised tests pass, and obsolete mixed-source assumptions are removed from helpers and assertions.
- [x] Broad safe Maven suite and applicable client/guidance Python suites pass; browser cases execute with installed Chromium.
- [x] Every acceptance criterion above has executable Sidecar evidence against the installed framework snapshot.
- [x] Actual startup isolation includes malformed deployment providers and environment-form configuration, not only candidate-unit mocks.
- [x] File mode proves complete execution, preserved DB authority, durable correlation, restart-only changes and shared mutation denial.
- [x] Wrong-key/tampered transfer preserves ciphertext, active publication and saved draft; same-key and explicit replacement paths execute successfully.
- [x] Roots/children/retries prove settings, credentials, resources and handoff correlation through the public API.
- [x] No routine test contacts live providers or production infrastructure, changes a developer database or exposes secrets.
- [x] Any optional check or environmental limitation is reported accurately; unrun tests are not passes.

## Step 4 measured outcome

The final clean isolated build `.\mvnw.cmd -f .codex-verification-pom.xml -B -ntp clean verify`
passed 246 tests with zero failures/errors and three explicitly gated
`ProductionComposeBrowserIntegrationTest` skips; packaging succeeded. Local Chromium
acceptance cases ran. The temporary POM changes only the output directory to avoid
editor-generated diagnostic class files; it is removed after verification. Exact
commands, iteration failures and fixes, acceptance-by-acceptance mapping, coverage
composition refinements and optional checks are preserved in the implementation
plan's **Final measured verification** section. A final ConfigurationReferenceTest
run passed 2 tests after example/docs edits. Client unittest suite passed 11 tests;
scripts suite passed 7. Docker production/browser checks, live provider checks and
release/hosted CI are NOT RUN under this plan's safe boundary. No required acceptance
criterion remains without executable Sidecar evidence; fresh Step 5 remains pending.
