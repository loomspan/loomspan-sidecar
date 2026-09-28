# PR 7.1 — File or database execution management and encrypted credential portability

## Development constraints

**Sidecar is still in development. Destructively replace superseded contracts,
code and schemas; do not add compatibility shims, legacy adapters, parallel
legacy APIs or migration machinery solely to preserve obsolete behavior.**

Apply the [design lens](../design-lens.md). Choose the simplest complete solution,
minimize technical debt, and keep implementation, tests and documentation
proportional. Document required development-data resets and their impact; this
does not authorize deleting deployed data.

## Outcome

Each Sidecar instance has one deployment-selected configuration mode: file or
database. File mode loads the complete execution configuration, skills, REST
routes and credentials from deployment files at startup; operators apply changes
by restarting. Database mode manages the complete configuration and encrypted
provider credentials through the Console/shared API and publishes them as one
version without restart. Executions never mix settings or credentials from
different publications. Operators can optionally include encrypted credentials
in portable exports and reuse them at a destination with the same encryption key.

## Requirements

- Add deployment-owned `loomspan-sidecar.configuration.mode` (`file` or `database`,
  default `database`; environment equivalent `LOOMSPAN_SIDECAR_CONFIGURATION_MODE`),
  effective at startup. Invalid values fail startup. File
  mode uses files for all publishable execution configuration, skills, REST routes
  and provider credentials; file changes take effect on restart. Database mode
  uses the database for all of that content. Do not retain the superseded
  provider-only override, per-field source selection, cross-source merging,
  file rereading during database publication, or automatic source fallback.
- In file mode, support framework file-based skill/configuration loading and
  Sidecar file-based REST routes. Disable Console/API configuration mutations
  and show the selected mode and effective configuration without disclosing
  credentials. Git-managed file deployment and rollback are valid workflows.
- In database mode, extend PR 7's existing UI and shared management API to cover the complete
  execution configuration supported by framework PR 11.1. Include usable UI
  controls for the new configuration fields and provider credential management;
  a backend-only capability is insufficient. Keep one draft/validation/publication
  workflow for skills, REST routes and execution configuration.
- In database mode, provider credentials must be encrypted in the database;
  do not resolve provider credentials from environment variables or deployment
  provider files. File mode may use literal credentials or external references;
  documentation should encourage secret stores without requiring them.
- Treat database-mode configuration and provider edits as draft content. Saving
  keys or other draft content does not itself activate anything.
  Prepare one complete effective execution configuration before atomic publication.
  Freeze the candidate used by validation/preparation; if publication would use
  different effective content, require revalidation instead of silently substituting
  it. Preserve existing exact revision/base, lease and validation-proof rules.
- Support entering and replacing Console-managed provider credentials, storing
  them encrypted in the database. Supply the encryption key externally through
  `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` environment variable, never alongside ciphertext
  in the database or exports. Use a cryptographically random encryption key. Missing
  or incorrect encryption material must produce an actionable failure without
  plaintext fallback, loss of ciphertext or silent provider-source fallback.
- Restrict credential mutations through the management authorization model and
  preserve live authorization rechecks. Do not return saved secret values through
  ordinary reads or reveal plaintext values in UI, diagnostics, errors, logs,
  snapshots or exports. An explicitly requested credential-bearing export may
  contain encrypted credentials. The UI may identify a configured credential without revealing it.
  Durable secret material, including any retained versions needed by publication
  or recovery, must be encrypted; transient preparation may pass decrypted values
  through the supported framework contract.
- Persist enough complete database publication state for restart recovery, history
  and rollback. In database mode the selected publication remains restart
  authority and never adopts file configuration or external provider credentials.
  In file mode the files are startup authority, regardless of retained database
  configuration. Mode selection does not switch live and load failures never
  trigger source fallback.
- Support configuration exports with an explicit option to include encrypted
  provider credentials. Always exclude plaintext credentials and the encryption
  key. Preserve credential identifiers and the metadata necessary to authenticate
  and decrypt included ciphertext at a destination using the same key. Operators
  may share encryption keys between environments; do not impose an environment
  promotion policy or require distinct keys.
- Configuration-only imports require destination credentials. Credential-bearing
  imports can use included credentials with the same encryption key; a different
  or missing key must be reported explicitly and must not activate an incomplete
  configuration. Permit importing the configuration and supplying replacement
  destination credentials through an explicit workflow. Keep import as a draft
  operation subject to authorization, validation and atomic publication.
- Database rollback republishes a complete retained configuration and encrypted
  credential versions as a new version, without mutating captured executions.
  Make any unavailable retained credentials explicit. File rollback is deployment
  of earlier file content followed by restart.
- Pass a complete candidate through the public framework API; do not introduce a
  competing model/skill validation authority, internal imports, reflection, bean
  replacement or undocumented configuration. Preserve the ArchUnit API boundary.
- New work captures the published version at framework handoff; already-captured
  roots and their nested/parallel work and retries retain old settings, credentials
  and resources until physical completion. Preserve durable execution correlation,
  atomic selection, failure handling and failed drafts from the existing workflow.
- Process infrastructure remains deployment-owned in both modes: mode selection,
  ports, storage, authentication and the encryption key are deployment settings.
  Document file provisioning, both modes, encrypted-key provisioning and recovery, environment restart
  requirements and normal provider-key rotation. Provider-side revocation may
  invalidate old work; this is accepted. No automatic file watcher, live environment
  refresh, general-purpose vault or machinery to prevent every operator-created
  mismatch is required. Preserve the core publication guarantees.
- Update the bundled authoring skill/client and deployment documentation so remote
  authoring uses the same mode restrictions, credential-handling, optional encrypted
  export/import and complete publication semantics as the browser. Keep plaintext
  secrets out of ordinary configuration payloads and exports rather than requiring
  agents to read them back. Document full-installation migration using a consistent
  database backup plus securely provisioning the same encryption key separately.

## Acceptance criteria

- [x] File mode starts and runs complete file-managed skills, routes, execution
  settings and credentials with either an empty or previously populated database;
  file changes require restart, and management configuration mutations are disabled.
- [x] In database mode, an authorized operator configures providers and replaces
  encrypted credentials through the Console without restart. Preparation succeeds
  independently of deployment provider files or external provider credentials and
  never merges fallback fields. A new database begins with empty publication content.
  The shared management API and bundled authoring client support the same workflow.
- [x] Saving edits does not activate them; validation and publication concern the
  same effective candidate. Existing permissions, private drafts, lease/revision/
  base checks, revalidation and failure preservation apply to the added content.
- [x] Database inspection finds encrypted secret material, not plaintext keys;
  ordinary read responses, errors, diagnostics and exports do not disclose plaintext keys.
  Missing or wrong external encryption keys fail explicitly without fallback or
  destructive recovery. Documentation explains provisioning and recovery needs.
- [x] Database-mode restart restores the selected complete publication and retained
  encrypted credentials without adopting changed files. History and rollback retain
  complete configuration. Mode selection is deployment-owned with no silent fallback.
- [x] Configuration-only export/import and optional encrypted-credential export/import
  work through Console and authoring API/client. Same-key destinations can use all
  included credentials; different-key destinations have an explicit configuration-only
  import/replacement path. Missing, wrong or tampered encryption material prevents
  activation without disclosing plaintext, silently discarding retained ciphertext,
  or changing the active configuration. No export contains the encryption key.
- [x] Overlapping work demonstrates full configuration and credential isolation at
  handoff, including delayed children and retries, with accurate durable snapshot
  correlation and correct resource retirement. Invalid preparation leaves the
  active configuration intact and makes no billable model request.
- [x] Sidecar integration tests use only the supported framework API and the locally
  installed updated snapshot. UI, operations, authoring guidance and design lens
  describe the completed execution scope and intentional development changes.

## Context

Developer-assigned work item: **PR 7.1**. Extends
[PR 7](loomspan-pr-7-publish-framework-configuration.md); depends on
[framework PR 11.1](../../../../loomspan-framework/ai/thoughts/tickets/loomspan-pr-11.1-complete-execution-configuration.md).

Implement the framework capability first. Use the developer-installed
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.6-SNAPSHOT` and matching local
framework documentation, then prove Sidecar integration before final framework
release checks. Publish framework beta 6 to Maven Central before switching
Sidecar to that release for final build/tests and final commit; hosted CI is
deferred until publication. Sidecar remains independently versioned at
`1.0.0-beta.1-SNAPSHOT`, targeting `1.0.0-beta.1`. Never overwrite releases.

The existing framework `ExecutionConfiguration` and Sidecar complete publication
workflow are starting points to extend. This is the Sidecar management Console,
not the separate Go observability Console. The new provider credential capability
does not expand scope into redesigning existing REST YAML secret handling.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Changes credential security, UI/API behavior, durable configuration,
  restart/rollback semantics and concurrent cross-repository publication behavior.
- **Reassessment triggers:** Missing supported framework capabilities must be
  resolved in framework planning, not bypassed inside Sidecar.

## Pipeline notes

- **Developer decisions, 2026-09-27:** Replace mixed provider-source management
  with an explicit file/database mode. File mode applies changes at restart;
  database mode stores provider credentials encrypted as well as configuration.
  Deployment infrastructure and the externally supplied encryption key remain
  outside the database-managed execution configuration.
- The developer approved `loomspan-sidecar.configuration.mode=file|database`,
  defaulting to `database`, and its environment equivalent
  `LOOMSPAN_SIDECAR_CONFIGURATION_MODE`. Changing mode requires restart; neither
  database contents nor load failures select the mode automatically.
- Literal file credentials are supported; secret-store/environment provisioning
  is encouraged but not required. Users choose whether environments share encryption
  keys. Exports may optionally carry encrypted provider credentials so a destination
  with the same key can move the complete configuration without reentering credentials.
- These decisions supersede the original provider-override/file-reread requirements
  and conflicting prior research/plan conclusions. The earlier Step 5 file-reread
  escalation is superseded; no live file-reload source choice is pending.
- The Full 5-Step Pipeline was already selected by the developer. Refresh research
  and implementation/test planning against the preserved current changes for this
  material scope revision, then implement and obtain a fresh independent review.
  The developer installed the updated framework PR 11.1 snapshot; continue to rely
  on that installation workflow. Preserve the previous review as an audit record.

## Step 4 execution notes

Implementation replaces provider-override/mixed-source behavior with immutable startup
mode and complete encrypted database candidates. The public Spring bind advisor
skips only documented execution roots in database mode; its strict-binding finish
handler suppresses only intentionally skipped properties. File startup records safe
correlation metadata separately and never becomes database recovery authority.
Cipher exports require live Edit authority and explicit inclusion; imports authenticate
metadata/ciphertext before atomic draft replacement, with explicit configuration-only
replacement for different keys. See the implementation plan's measured verification
section for commands and acceptance evidence. Existing prior uncommitted PR 7.1 work
was retained where applicable; prior review records remain untouched and were not used
as handoff evidence. No framework rebuild, release switch, data reset or deployment
was performed. Production Compose/verifier instructions now use the external encryption
key and write-only draft credentials; Docker deployment checks remain optional and unrun.

Step 4 verification completed on 2026-09-27: clean isolated Maven verify passed
246 tests (0 failures/errors; 3 intentionally gated production Compose browser skips)
and packaged the JAR. Client tests: 11 passed; scripts/guidance tests: 7 passed.
The implementation plan records exact commands, resolved iteration failures,
all eight acceptance mappings and optional checks. Checked criteria indicate
implementation evidence; the approved Full Pipeline still requires fresh Step 5 review.
