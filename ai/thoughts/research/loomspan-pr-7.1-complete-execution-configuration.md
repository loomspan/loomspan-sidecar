---
date: 2026-09-27
repository: loomspan-sidecar
branch: main
commit: 87ffc54dd6cb0663f80144693dc7772653c14c4d
ticket: ai/thoughts/tickets/loomspan-pr-7.1-complete-execution-configuration.md
tags: [execution-configuration, configuration-modes, publication, credentials, management, framework]
---

# PR 7.1 File or Database Configuration Research

## Research Question

What does the preserved working tree implement, and what current source evidence bears on the revised deployment-selected file/database modes, database bootstrap independence, encrypted credential portability, authorization, UI/client behavior and publication guarantees?

## Follow-up update — 2026-09-27

This document replaces the September 26 current-state account after fresh source inspection. That earlier account described committed PR 7 before the substantial uncommitted PR 7.1 implementation. Its claims that credential storage, provider controls and effective candidates were absent are no longer current. The developer's September 27 ticket decisions supersede the mixed provider-source requirements: one startup-selected mode; file changes require restart; database content includes encrypted provider credentials exclusively; optional ciphertext exports can travel between environments sharing the encryption key. There is no outstanding file-reread decision. Previous review documents were not read or used as implementation evidence.

The development replacement policy and [design lens](../design-lens.md#simplicity-and-technical-debt) remain controlling. Existing mixed-source code is preserved research input, not an approved final contract. This research makes no production edits or implementation recommendations.

During this research, the developer also approved `loomspan-sidecar.configuration.mode=file|database`, defaulting to `database`, with environment form `LOOMSPAN_SIDECAR_CONFIGURATION_MODE`. Selection is startup-only, invalid values fail, and database contents never infer a mode or trigger fallback. The orchestrator persisted this decision in the ticket. This is an approved future contract, not behavior already present in the inspected source.

## Summary

The working tree already adds encrypted credential versions to private drafts and immutable snapshots, a frozen effective candidate to validation proofs, credential mutation endpoints and Console controls, and format 3 configuration-only bundles. It still uses a per-publication `providerOverride` boolean: skills/routes/session settings come from the database while providers can come from Spring deployment properties. There is no deployment mode selector, file-managed Sidecar startup, REST file-location setting, mutation mode gate, or encrypted-credential bundle option.

Database publication uses the supported framework three-argument `prepare` with an explicit credential map. Framework bootstrap precedes that publication and still eagerly constructs deployment-configured provider clients. Empty skill locations alone do not isolate bootstrap from deployment provider configuration. Current source therefore establishes the later explicit-candidate boundary, but does not establish the revised whole-process database independence requirement.

## Repository State

- Inspected September 27, 2026, approximately 15:00 PDT; branch `main`, HEAD `87ffc54dd6cb0663f80144693dc7772653c14c4d`.
- `git status --short` shows 46 tracked changed/deleted files plus untracked research/plans/reviews/ticket and new source/tests. No staged changes were shown. The tracked diff reports 1,060 insertions and 763 deletions; this excludes untracked files.
- Existing work spans README, operations/authoring guidance, design lens, runtime publication, management controllers/services/pages, draft/snapshot storage, V1/V3 schemas, browser JavaScript/CSS, Python client and tests. `ConfigurationBundleV2` and its test are deleted; untracked replacements include `ConfigurationBundleV3`, `EffectiveExecutionConfiguration`, `ProviderCredentialCipher`, `EncryptedCredential`, and corresponding tests. These names and inspected contents correspond to the supplied prior PR 7.1 work; research did not revert or reattribute other developer changes.
- `pom.xml` retains Sidecar `1.0.0-beta.1-SNAPSHOT` and framework `1.0.0-beta.6-SNAPSHOT`. The developer installed the matching updated framework snapshot. No framework rebuild or artifact/source hash verification was performed or required.

## Investigation Checklist

- [x] Startup authority, modes, file skills and REST resources.
- [x] Framework bootstrap versus explicit database preparation.
- [x] Draft/proof/publication/restart/rollback flow.
- [x] Credential encryption, identifiers, retained versions and portability.
- [x] API authorization, Console, authoring client and documentation.
- [x] Sidecar tests, fixtures and gaps for revised requirements.

## Current Behavior and Data Flow

### Startup and file resources

`src/main/resources/application.yml:1` configures framework skills at `classpath:/sidecar-empty-skills/*.yaml`. It contains no Sidecar mode setting. `RuntimeConfigurationService.java:84` runs after Spring startup, rejects any nonempty framework startup catalog, registers retirement handling, restores the database-selected snapshot with its retained ciphertext, stages REST clients, publishes a generation, updates its durable status and then opens execution dispatch. Failure leaves activation closed and reports a safe phase-specific startup error; encryption failures preserve actionable key guidance (`:111`).

`ConfigurationSnapshotStore.java:36` initializes a new database with empty skills, empty REST routes and `loomspan: {}`. `StorageConfiguration.java:98` initializes this store unconditionally. A populated database selection is always runtime startup authority in the inspected Sidecar code. There is no branch that makes file configuration authoritative while ignoring retained database publication content.

`RestRoutesProperties.java:5` currently contains only the URL-variable allowlist. `RestRouteLoader.java:62` parses supplied YAML text; `GenerationRestResources.java:56` always feeds it the supplied string under the diagnostic label `rest-routes.yaml`. No current production resource reader loads a routes file. The loader resolves REST placeholders using the existing Spring/process environment rules and validates SSL bundles; provider-credential changes do not replace this distinct REST contract. Framework file skill discovery remains supported by `loomspan.skills.locations`, but the Sidecar startup nonempty-catalog rejection currently prevents using it as the authoritative Sidecar execution configuration.

`ExecutionCoordinator.java:183` requires a non-null durable configuration UUID for every captured framework generation. `GenerationRestResources.java:63` stages that mapping, `:77` looks it up, and `:98` retires it asynchronously only after framework retirement. Thus file startup also intersects execution correlation and resource ownership; simply allowing a nonempty framework catalog would not by itself supply these existing application invariants.

### Framework bootstrap and complete candidates

All framework paths below are under `C:/opendev/code/loomspan-framework/` and are source evidence only, not permitted Sidecar imports.

- `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:72` binds deployment `LoomspanProperties`. At `:171` it creates the generation manager with the bound properties and a runtime factory that builds `NamedAiConnectionRegistry` from all effective connections.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:92` initializes its initial generation through `activate(prepare())`; `:110` checks configured YAML, and `:163` returns startup properties before an active runtime exists. At `:328`, preparation invokes the runtime factory even for an empty skill set.
- `src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java:18` iterates configured connections and constructs each client. Invalid or unresolved deployment provider settings can therefore fail bootstrap before Sidecar's runner restores the database candidate. This is a source-derived consequence; no new boot experiment was run here.
- `README.md:263` explicitly states that the framework has no startup loading switch or deferred activation mode. Its documented restart pattern starts normally and then prepares saved documents before the application opens traffic.
- `src/main/java/ai/loomspan/api/SkillReloader.java:25` provides explicit execution configuration preparation and at `:32` the credential-map overload with no Spring Environment fallback. `README.md:198` defines publishable connections, models, session and trace persistence, reference-only candidate credential fields, missing/blank/unused-map rejection, and preparation without model requests. Ordinary file startup permits the connection fields, including literal credential values.

The public candidate API therefore provides database preparation without environmental credential fallback after bootstrap; it does not itself change the initial deployment property binding or connection construction. Sidecar currently has no startup environment-isolation component or additional documented framework bootstrap selector. The ticket allows standard Spring APIs and documented configuration, while forbidding framework bean replacement, reflection and internal imports. Whether the final startup behavior is demonstrated through those allowed boundaries remains planning/implementation evidence to establish, not a request for a developer choice during research.

### Existing mixed-source implementation

`ManagedConfiguration.java:11` now carries skills, REST YAML, execution YAML, `providerOverride`, and credential identifiers. `EffectiveExecutionConfiguration.java:64` parses authored YAML and rejects direct provider secret fields. With override off, it removes authored connections/models and inserts providers collected from enumerable Spring property sources (`:113`), retaining authored session/trace settings. With override on, it decrypts the supplied managed versions and does not merge deployment providers.

The file-provider path distinguishes raw `${NAME}` references from literals, synthesizes identifiers for literals and encrypts those values (`:137`). It reads Spring's already-loaded property sources; it contains no disk file rereader. Restore (`:96`) uses stored effective YAML plus retained ciphertext, but missing references in override-off snapshots are resolved again from the Environment. These behaviors implement the superseded mixed source design, including an environmental secret dependency after restart.

### Draft validation, atomic publication and recovery

`ManagementEditingService.java:176` replaces credentials under exact account/draft/revision/base and lease checks, assigns a new version, encrypts before persistence, revises the draft and invalidates validation. Removal follows the same mutation path (`:197`). Ordinary draft views expose configured identifier lists, not encrypted values (`:365`).

Validation captures exact draft state, prepares a candidate and rechecks the capture before attaching the candidate to an in-memory proof (`ManagementEditingService.java:214`). Publication rechecks live authorization, lease, revision/base and proof under the publication gate (`:240`). `RuntimeConfigurationService.java:215` reassembles effective content and compares it with that proof; changes cause `validation_required`. Its candidate comparison includes YAML, decrypted values and managed version identities (`EffectiveExecutionConfiguration.java:50`). File-literal randomized versions are deliberately excluded from version comparison.

`RuntimeConfigurationService.java:225` prepares the validated candidate using the public map overload, stages REST resources, durably submits effective YAML and encrypted retained values (`:246`), binds the snapshot identity and publishes atomically. Existing failure/reversion paths remain present. Successful admission clears the published draft; failed publication preserves it. The runtime's `candidateFor` (`:383`) handles explicit retained-source rollback separately from ordinary edits.

`V1__configuration_snapshots.sql:1` stores provider selection and effective YAML; `:12` stores immutable credential identifier/version/ciphertext rows. `V3__management_drafts.sql:14` stores draft ciphertext and `:22` separately records required identifiers, allowing absent imported credentials to remain visible. `ConfigurationDraftStore.java:54` copies current managed versions into a newly opened draft; `:121` reads ciphertext internally. `ManagementConfigurationRollbackService.java:22` validates retained content and `:29` loads retained encrypted versions into the normal draft workflow. No plaintext credential is part of the configuration record or snapshot response model.

### Cipher and portable bundles

`ProviderCredentialCipher.java:20` accepts a base64 32-byte key; `StorageConfiguration.java:35` obtains it directly from `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` via `System.getenv`. Missing material is tolerated until encryption/decryption is required; malformed supplied material fails construction. AES-GCM uses a fresh 12-byte nonce, a 128-bit authentication tag, and additional authenticated data made from identifier, NUL separator and version (`ProviderCredentialCipher.java:34`, `:74`). Encoded ciphertext contains nonce and sealed bytes. It is not bound to an environment or snapshot UUID. Therefore the same key plus unchanged identifier/version/ciphertext is sufficient cryptographic input at another destination; this is inferred directly from the cipher, not existing bundle support. Wrong keys, altered metadata and tampering produce safe decryption failures.

`ConfigurationBundleV3.java:39` explicitly excludes both values and ciphertext. Its `Bundle` record (`:51`) contains configuration only; `write` (`:65`) places authored YAML, providerOverride and identifier requirements in `execution.json`, alongside REST, skills and checked manifest inventory. The parser retains existing strict format, duplicate, integrity and size validation. `ManagementConfigurationController.java:54` has no encrypted-export option and captures only the published snapshot. `ManagementConfigurationImportService.java:29` reviews configuration and `:35` loads it into a draft without encrypted versions. The Python client and Console similarly have no ciphertext inclusion or explicit ciphertext-discard/replacement import option. Configuration-only imports already have persisted required identifiers and separate write-only replacement operations.

Existing ciphertext serialization has no explicit cipher-format field beyond the fixed implementation. Bundle inclusion metadata, key mismatch reporting at import, ciphertext-preserving failure and explicit configuration-only selection for credential-bearing input are not current contracts. The encryption key is not serialized by existing storage/bundle code.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Authorization | `ManagementSecurityConfiguration.java:49` retains session CSRF and bearer-only exemption; `:61` requires editor permission for import/rollback/editing. `ManagementEditingService.java:310` rechecks live account/token edit and publish authority. Mode restriction is absent. |
| Console | `ManagementPagesController.java:291` renders a provider override checkbox and masked credential form. `:301` adds a YAML builder for provider/model/session/trace options, while retaining full YAML editing. `editor.js:227` writes secrets through separate credential endpoints and clears the password input. There is no deployment-mode display or read-only file configuration view. |
| Export/import UI | `ManagementPagesController.java:223` says bundles exclude managed credentials. Current export links and import workflow have no inclusion/discard options. |
| Remote authoring | `agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py:27` adds replace/remove credential commands; `:32` retains fixed export/import routes. CLI parsing at `:137` supports bundle files and ordinary JSON input but no encrypted-export flag or ciphertext import policy. |
| Operations and lens | `docs/operations.md:350` describes per-publication provider override; `:363` calls that provider choice file mode while session/trace remain authored. The design lens's atomic publication section still describes explicit file-provider preparation and database restart authority. These conflict with the revised deployment-wide modes. |
| Development schema | Existing changes replace V1/V3 directly. `docs/operations.md:124` already documents backup and development reset for the altered schema and superseded bundles. No database reset was performed during research. |

## Existing Tests and Fixtures

These are source-located test capabilities, not claims that a current suite passed. No tests or live provider services were run in this research step.

- `configuration/ProviderCredentialCipherTest.java:12` tests randomized ciphertext bound to identifier/version; `:22` covers missing/wrong/tampered keys. `configuration/EffectiveExecutionConfigurationTest.java:17` covers encrypted file literals and retained restart values; `:45` covers external references and managed override ignoring file providers; `:80` checks thinking levels and dotted headers. The latter tests assert superseded source semantics.
- `storage/ConfigurationDraftStoreTest.java:56` checks encrypted replacement/revision, `:83` missing import credential requirements, and `:111` retained rollback source behavior. `storage/ConfigurationSnapshotRepositoryTest.java:114` checks ciphertext-only persistence and identifiers in configuration-only bundles.
- `management/ManagementEditingHttpIntegrationTest.java:94` covers encrypted, write-only replacement and invalidated validation. `management/ManagementEditorBrowserIntegrationTest.java:126` exercises the source checkbox and credential form. Existing management tests cover private drafts, lease handoff, CSRF, revoked/live permissions, token authoring and stale revisions; `RemoteAuthoringWorkflowIntegrationTest` is the shared token workflow fixture.
- `configuration/RuntimeConfigurationIntegrationTest.java:39` tests empty startup, `:101` mixed file-provider drift/restart retention, `:161` closed startup gate, `:206` pending restart selection, `:234` invalidated proof, `:421` export capture, `:462` bundle transfer, `:494` competing publication, and `:531` generation-aware pruning. `RuntimeConfigurationRecoveryIntegrationTest` covers selected/reverted/faulted states, backup restore and failed activation.
- `execution/ExecutionConfigurationCorrelationIntegrationTest.java:34` uses a local fake provider to overlap old/new connections and credentials; `:114` and `:163` cover durable correlation and the handoff/mapping race. `rest/RestHandlerLifecycleIntegrationTest`, `rest/RestGenerationIntegrationTest`, and `execution/ExecutionShutdownIntegrationTest` cover retained resources and shutdown. Existing overlap evidence does not by itself establish the complete revised mode matrix or delayed-child/retry behavior for full encrypted publications.
- `bundle/ConfigurationBundleV3Test.java:38` roundtrips exact configuration and empty content; remaining cases cover format, integrity, duplicate entries, bounds and malformed archives. There is no ciphertext-bearing bundle path to test. Its encrypted ZIP flag rejection at `:236` concerns ZIP encryption, not provider ciphertext payloads.
- `architecture/SupportedLoomspanApiArchitectureTest.java:17` checks application and test dependencies against forbidden internal/autoconfigure packages and `:28` rejects Java skills. Framework source inspection above does not change this boundary.

All test paths in this section are below `src/test/java/ai/loomspan/sidecar/`. Local verification uses SQLite, fake loopback HTTP providers, the installed Maven snapshot and, for browser tests, Playwright/browser setup. Python authoring tests are in `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`. Revised-mode acceptance paths currently lack implementation and corresponding tests: file startup with empty/populated database, restart-only file changes, mutation denial, database bootstrap despite invalid deployment providers, and same-key/wrong-key/tampered credential-bearing import/export.

## Dependencies and Operational Constraints

The revised ticket keeps mode, ports, storage, authentication and encryption key deployment-owned. Database provider secrets must never fall back to environment or files; file provider literals remain supported. Full-installation migration requires a consistent database backup plus the same key provisioned separately. Provider-side revocation can break old captured work; this is accepted scope. Existing REST YAML secret handling remains distinct.

Framework snapshot installation and local source lookup follow AGENTS.md. Hosted release CI waits for the published framework beta 6 artifact; Sidecar versioning remains independent. Tests must establish Sidecar behavior through the public API, including resource and shutdown integration; framework tests are not Sidecar completion evidence.

## Historical Context

Committed PR 7 established database-selected atomic publication. The preserved PR 7.1 work added mixed provider source selection, encrypted managed/literal-file credentials and complete candidates. The developer's September 27 ticket revision replaces that model with deployment-wide modes and optional encrypted portability. Earlier plans and mixed-source documentation therefore carry historical implementation context, not authority over the revised ticket. The prior review remains audit-only.

## Open Questions

No developer answer is needed to finish research. The mode property and default are settled above. Planning owns the allowed startup isolation mechanism, file inspection/correlation representation, and encrypted bundle/import representation. Evidence still to obtain during implementation includes whole-process database independence before framework bootstrap and end-to-end mode, portability, retained-credential recovery, delayed child/retry and authorization behavior. These are engineering and verification questions within the settled requirements.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/loomspan-pr-7.1-complete-execution-configuration.md
SUMMARY: Refreshed research from current Sidecar and matching framework source for the revised modes and encrypted portability requirements. Documented preserved implementation, bootstrap dependency, security/storage/UI/client paths, existing test coverage and missing revised acceptance evidence. No production changes, tests or live external operations were performed.
DECISIONS:
  - Replaced obsolete current-state claims with fresh evidence; retained prior review as audit-only and recorded approved mode property/default.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run Steps 2 and 3 against the revised ticket and refreshed research.
