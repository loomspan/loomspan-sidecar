# HTTP configuration management and authoring verification

Date: 2026-11-02 (America/Los_Angeles). Full pipeline, Step 4.

**Development policy: replace obsolete contracts and schemas directly; no legacy
readers, compatibility adapters, disabled tests or parallel configuration paths.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).

## Scope and repository state

Ticket: [PR 9.2](../tickets/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md).
Implementation/testing plans use the same stem under `../plans/`.
Baseline is `736315dc0c67b179b1835f359604ffcf734d998e` on `main`.
The developer's tracked edits to the lens, HTTP design notes and 9.1/9.2/9.3
tickets were preserved. This pipeline owns research/plans and the implementation
diff. No Framework source, dependency version, release tooling or hosted workflow
was changed. No commit, push, release or actual development-store reset occurred.

## Implemented behavior and decisions

- Authored configuration is skill documents plus complete execution YAML and
  credential identifiers. Snapshot/draft schemas, management payloads, browser
  state and inspection no longer contain a routes document.
- Portable bundles use format 4, with execution JSON, skill YAML and optional
  authenticated ciphertext. Format 3 is rejected directly. Existing inventory,
  size, CRC, digest, entry-name and UTF-8 controls remain.
- HTTP validation remains invocation-only using Framework-captured maps. Startup,
  draft validation, publication, restoration, import and rollback run no HTTP
  domain validator or destination probe. Framework declaration/reference/map checks
  and management authorization/integrity checks retain their normal boundaries.
- The annotated executor requires a selected `http` map even for an empty catalog.
  Initial stored/file defaults explicitly contain that map. Database bootstrap
  loads only Sidecar's fixed `sidecar-database-bootstrap.yml` through Boot's
  installed public `PropertySourceLoader` interface, ahead of deployment sources.
  It does not read/reconstruct operator Config Data or manipulate internal values.
  Published candidates receive no omitted-map repair or startup fallback.
- Only referenced provider credentials enter Framework's provider credential map.
  Retained encrypted records are attached to an existing HTTP map without walking
  targets/authentication. Managed ciphertext is authenticated as storage integrity;
  missing HTTP references alone do not become management domain validation.
- Effective YAML remains internal and is excluded from JSON inspection. Default
  export uses authored reference-only YAML; explicit encrypted export retains
  identities/versions/ciphertext and excludes the external key.
- Restoration reassembles selected authored content and exact retained records,
  then compares parsed complete effective content. YAML mapping order is ignored:
  immutable credential maps can serialize in different orders between JVMs.
  Different values/ciphertext are rejected; no merge or inheritance occurs.
- Key usability is checked when encrypt/decrypt is needed. Unused malformed file
  key material does not fail readiness; HTTP fixed invocation fails before I/O.
  Database retained-record authentication still fails on missing/wrong keys.
- `ConfigurationGenerationRegistry` owns only opaque Framework-generation to
  durable-snapshot correlation/history protection. Stage before publication,
  discard on failure, retire through the supported listener. HTTP client lifetime
  is independent: one application-owned pool, with Framework's shutdown deadline.
- Public catalog/reloader calls select `default` explicitly and use qualified
  catalog identities. Descriptors retain Framework's JAVA classification; no new
  metadata category or internal catalog access was introduced.
- Browser and remote authors share the existing save/validate/publish path, now
  with skill/execution YAML only. Old save fields are rejected. Ownership, CSRF,
  token scopes, revision/base, reconciliation, post-lock permission rechecks,
  import/rollback restrictions and draft cleanup remain in the same services.
- Product documentation, examples, configuration templates and packaged authoring
  guidance describe operations, pointers/body choices, captured shared targets,
  credentials, modes and invocation-time errors. Caddy owns mTLS; direct HTTPS
  uses ordinary JVM trust/hostname verification. Repository guidance authorizes
  the annotated executor within the closed public API boundary.

## Development data impact

V1/V3 definitions directly remove obsolete route columns. Existing development
SQLite stores require developer-owned replacement/reset while stopped, including
the appropriate WAL/SHM state. The whole store also contains accounts, browser
credentials, PATs, drafts, encrypted credential versions, history and selection;
those records would be lost and require bootstrap/re-authoring. Preserve backups
and the separately provisioned encryption key. No database was reset by this work.
Old format-3 development exports cannot be imported; no conversion reader exists.

## Test replacement and acceptance evidence

Obsolete route-loader/catalog/staging assertions were removed with their contracts.
`RestRouteStartup/Validation/RestartIntegrationTest` and `RestGenerationIntegrationTest`
are replaced by the following composed tests and retained execution tests.
`RestFailureEnvelopeIntegrationTest` moved to `http/HttpFailureEnvelopeIntegrationTest`
and asserts the current response contract. No test was marked disabled/skipped.

| Acceptance criterion | Executable evidence |
| --- | --- |
| File/DB startup, invocation-only HTTP rejection, no inheritance | `HttpConfigurationIntegrationTest`: invalid operation/target settings publish, restore and fail before any observed request; file mode accepts invalid settings; actual Boot import/profile selection; absent selected map still fails Framework validation; removed credentials/targets never inherit. `ConfigurationModeIntegrationTest`: DB ignores deployment settings, file changes require restart. |
| Coherent public publication without probes; invalid/stale/unauthorized rejection | Runtime validation/preparation/publication tests; HTTP invalid import and rollback review; `RuntimeConfigurationIntegrationTest` and recovery hooks preserve previous active selection and fault semantics. |
| Ownership, leases/revisions, permissions, CSRF/scopes and approval ownership | Management editing/service/HTTP/PAT/session suites, browser ownership/handoff tests and `RemoteAuthoringWorkflowIntegrationTest` exercise the same new payload and both browser/remote publication paths. |
| Selected restart and credential semantics; client-independent identity | `CredentialEnvironmentIntegrationTest` uses separate JVMs and production environment provisioning; portability tests use same/wrong keys and explicit replacement. `EffectiveExecutionConfigurationTest` covers HTTP-only credentials, missing refs, ciphertext comparison, JSON secrecy and reordered effective content. Model-only correlation tests remain. |
| Old/new captured work, failure and shutdown | New HTTP retained-admission target/credential test; authenticated execution old/new publication, nested callbacks, history protection; correlation handoff/publication race; full-Sidecar shutdown completion/cutoff, queue shutdown and HTTP lifecycle tests use supported APIs and one deadline. |
| Browser/remote representation and portable round trip | Browser editor/history/import, remote authoring and management export/import suites; bundle-v4 tests retain meaning/integrity, reject old version and prove no rest payload. |
| Obsolete contracts removed and reset documented | Direct schema/type/resource removal, unknown save-field rejection, bundle inventory test and production/docs/examples absence scan; reset consequences above. |
| Current guidance and closed Framework boundary | `SupportedLoomspanApiArchitectureTest`, HTTP architecture test, configuration reference test, packaged Python guidance/client tests; no new Framework dependency outside `ai.loomspan.api`. |
| Unit-owned backend/browser/lifecycle verification | Focused checks and broad results below. Independent Step 5 review remains a separate required stage. |

## Verification performed

- Initial red attempt: `./mvnw.cmd -B -ntp "-Dtest=EffectiveExecutionConfigurationTest" test`
  was blocked by baseline deleted REST types and namespace-less public calls.
  This was a compiler blocker, not the intended behavioral red assertion.
- PASS — `./mvnw.cmd -B -ntp -DskipTests compile` after production integration.
- The first broad `./mvnw.cmd -B -ntp verify` ran 296 tests, with 12 failures,
  4 errors and no skips. It located stale fixture expectations plus a release
  tooling mismatch. Ticket failures were corrected rather than disabled.
- The affected-class rerun (`./mvnw.cmd -B -ntp "-Dtest=ConfigurationReferenceTest,CredentialEnvironmentIntegrationTest,AuthenticatedExecutionApiIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,HttpFailureEnvelopeIntegrationTest,LoomspanSidecarApplicationTest,ManagementEditingHttpIntegrationTest,ManagementEditorBrowserIntegrationTest,RemoteAuthoringWorkflowIntegrationTest,MountedSkillRegistrationIntegrationTest,HttpConfigurationIntegrationTest,EffectiveExecutionConfigurationTest,ProviderCredentialCipherTest" test`)
  ran 62 tests: 61 passed; only cross-JVM effective-YAML ordering failed before
  its subsequent fix. Browser/remote execution and permission checks passed.
- PASS — `./mvnw.cmd -B -ntp "-Dtest=CredentialEnvironmentIntegrationTest,EffectiveExecutionConfigurationTest" test`:
  11 tests, no failures/errors/skips, verifying the cross-JVM ordering correction.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`:
  6 tests, including latest packaged guidance and canonical assets.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`:
  11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`:
  8 orchestration tests, no actual deployment started.
- PASS — `node --check src/main/resources/static/management/assets/editor.js`
  and `node --check src/main/resources/static/management/assets/console.js`.
- PASS — `./mvnw.cmd -B -ntp "-Dtest=SupportedLoomspanApiArchitectureTest,HttpExecutorArchitectureTest" test`: 3 tests, including the final added shutdown test class in the scanned output.
- PASS — `git diff --check`.
- Production/docs/examples/packaged-guidance `rg` scan found no `restRoutesYaml`,
  `rest_routes_yaml`, `RestSkillHandler`, `rest-routes` or `url-variables` contracts.

## Precisely assigned intermediate blockers

The ticket permits intermediate checks to remain blocked only with exact cause
and downstream ownership. These pre-existing release checks were not changed or
disabled by this unit:

1. `ReleasePreparationTest.releasePreparationRejectsSnapshotsAndProducesExpectedArtifactPlan`
   fails at its expected-success beta.8 plan. Reproduction:
   `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.8 --tag v1.0.0-beta.2`.
   Actual exit 1: `pom.xml does not use the beta 8 framework line`.
   `scripts/prepare-release.py` and the release profile remain beta.8-specific,
   while the required development POM is beta.9-SNAPSHOT.
   **Owner: PR 9.3 final integration/release reconciliation**, after Framework
   publication and tested released-dependency pinning as required by policy.
2. `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v`:
   7 tests, 1 error. `test_annotated_tag_and_duplicate_guard` copies the current
   snapshot Framework dependency into its temporary fixture and expects a release
   tag to succeed; the existing guard correctly rejects it with
   `Cannot release with a SNAPSHOT framework dependency`.
   **Owner: PR 9.3 final release/test-fixture reconciliation**. No real repository
   tag was created, and the guard was not weakened.

## Latest broad verification and residual scope

FAIL — Final full `./mvnw.cmd -B -ntp verify`: **298 tests, 297 passed, one
failure, zero errors/skips**. The sole failure is the unchanged
`ReleasePreparationTest` beta.8 guard detailed above; all functional tests passed.
The Maven lifecycle stopped in tests, so package/repackage/verify completion is
not claimed.

PASS — `./mvnw.cmd -B -ntp "-Dtest=HttpConfigurationShutdownIntegrationTest" test`:
**2 tests**, full Sidecar contexts with real Framework/HTTP client. Both queued
work discard and refusal of new admission are asserted through public Sidecar
methods. The completion case keeps the client open until the admitted request
finishes. The cutoff case closes within two seconds with a 300ms Framework budget
and a blocked 30s HTTP read, without another drain period. These tests were added
after the broad run and run separately; they are not included in its 298 count.

Step 4 implementation and ticket-owned verification are complete under the
explicitly permitted independent release blockers. Fresh Step 5 review remains
required before pipeline completion.
Hosted CI, Framework release/pinning and live Docker/Standard/Hardened deployments
were not run or represented as passing. No optional manual check is required.
