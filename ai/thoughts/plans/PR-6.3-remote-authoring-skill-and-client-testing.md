# Remote Authoring Skill and Client Testing Plan

## Change Summary

Add an agent-neutral Python standard-library client and skill instructions for the existing management API, then verify a local application endpoint, saved draft, console handoff, publication, and separate authenticated execution. Server authority and persisted schema stay unchanged. The design lens requires one shared API, explicit lease ownership, trusted credentials outside model input, and no compatibility layer.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| CLI transport and credentials | Wrong route/method/body, token in args/output/error, mixed browser cookies | Fake HTTP server request inspection and subprocess output assertions |
| Lease and revision | Handoff uses stale state, displaced writer silently retries, renews automatically | Handoff response followed by current/draft GETs; 409 and request-count tests; joined old-holder denial |
| Permission and revocation | Read/Edit/Publish confusion or live role change permits action | Real PAT requests in joined test; existing `ManagementPersonalTokenHttpIntegrationTest` matrix |
| Stale base and recovery | Complete draft overwritten or auto-merged after another publication | Explicit reconcile input and no-retry tests; joined stale draft and predecessor reconciliation test |
| Validation and publication | Client treats validation as execution, publishes wrong revision, loses draft on failure | Real malformed/fixed YAML validation, exact candidate publication, failure retention and cleanup assertions |
| Bundle transfer | Client invents ZIP logic or malformed multipart fields | Fake HTTP exact multipart fields/bytes; server bundle/import predecessor tests |
| Application access | Published REST route works but target leaks another user's data | Loopback fixture verifies execution JWT independently and denies foreign record; published execution assertions |
| Console observation | Agent edits remain invisible or handoff misses saved revision | Playwright read-only saved draft and explicit handoff assertions; existing browser test |
| Restart | Lease/proof incorrectly survive or saved draft disappears | `ManagementDraftRestartIntegrationTest` plus focused client recovery read after restarted server if feasible |

## Existing Coverage and Environment Constraints

The repository uses JUnit 5/Spring Boot random-port tests, Java `HttpClient`, disposable SQLite under `@TempDir`, and Playwright browser tests. Existing `ManagementEditingHttpIntegrationTest`, `ManagementPersonalTokenHttpIntegrationTest`, `ManagementDraftRestartIntegrationTest`, `ManagementEditorBrowserIntegrationTest`, `ConfigurationBundleV1Test`, `ManagementImportBrowserIntegrationTest`, and `AuthenticatedExecutionApiIntegrationTest` protect the underlying server contracts. New tests should exercise the client boundary and one joined workflow, rather than duplicating all predecessor combinations.

Python 3.13, Maven 3.9.6, and Java 21 are present locally. The CLI itself should require only a documented Python 3 minimum and standard library; implementation must choose a supported minimum based on syntax used. The joined test calls Python via `ProcessBuilder` with a PAT in the child environment and captures stdout/stderr without printing secrets. Playwright Chromium is a local test dependency already used by repository tests. The framework beta.5 snapshot is installed locally per `AGENTS.md`; do not rebuild framework or use external services. A non-loopback management endpoint in real use must use HTTPS, but fixture HTTP loopback is safe for the test.

## Failing Test First

- Name: `readsCurrentWithEnvironmentTokenAndNoArgumentSecret`
- Type: Python `unittest` subprocess plus fake local HTTP server.
- Location: `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`
- Arrange/Act/Assert: start a loopback fake server, set `LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN` only in subprocess environment, invoke `python sidecar_authoring.py current` with base URL, assert `GET /api/management/configuration/current`, one Authorization header, valid JSON result, and no token in command arguments/stdout/stderr.
- Expected pre-fix failure: client script is absent, so the process cannot perform the request.

## Tests to Add or Update

### 1. `routesCommandsThroughSharedApi`
- Type: Python unit/subprocess test.
- Location: `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`
- Proves: current/draft/lease status, acquire, renew, release, save, reconcile, validate, publish, export, import review/load use exact existing management routes and methods; mutation bodies remain complete server structures.
- Inputs/fixture: representative grant, draft, configuration, ZIP bytes, and structured server JSON; token only in environment.
- Doubles or boundary isolation: loopback fake HTTP server records requests and returns canned responses; no Sidecar or third party.
- Edge cases: draft 404, malformed local JSON, missing environment token, ZIP output failure, multipart field names and binary payload.

### 2. `handoffReadsFreshStateAndDoesNotRetryConflicts`
- Type: Python unit/subprocess test.
- Location: `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`
- Proves: handoff is explicit, then performs current and draft GETs and returns the rotated grant/current revision; a 409 save, renew, or publish exits nonzero with server code and a fresh-read direction; there is no hidden renewal, takeover, or second write.
- Inputs/fixture: generation rotation, revision change, `grant_conflict`, `revision_conflict`, `base_conflict`, `validation_required`.
- Doubles or boundary isolation: request-counting fake HTTP server.
- Edge cases: 401 expiry/revocation, 403 permission, 503 ambiguous publication with returned state fields.

### 3. `neverEmitsManagementSecret`
- Type: Python unit/subprocess test.
- Location: `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`
- Proves: token appears only as the request Authorization header; no raw header or secret appears in normal/error output or traceback; token is not accepted as CLI argument.
- Inputs/fixture: distinctive synthetic PAT and server/transport failures.
- Doubles or boundary isolation: fake local server and closed loopback port.
- Edge cases: hostile server error body echoing a token-like string should be redacted or not printed wholesale.

### 4. `remoteAuthoringFromEndpointThroughBothPublicationPaths`
- Type: Java Spring random-port + `ProcessBuilder` Python + Playwright + loopback callback integration.
- Location: `src/test/java/ai/loomspan/sidecar/management/RemoteAuthoringWorkflowIntegrationTest.java` with `src/test/java/ai/loomspan/sidecar/support/RemoteAuthoringApplicationFixture.java`.
- Proves: a committed representative application endpoint and matching example YAML/route can be authored into a draft; structured validation error is repaired; another console session sees server-saved text read-only; console handoff rotates the lease and publishes an Edit-token draft; a Publish token obtains a new grant, validates and publishes a second draft without UI approval; success clears the publishing draft and lease.
- Inputs/fixture: seeded management user/PATs, controlled record callback, malformed then valid skill document, dynamic literal loopback target URL, separate fixture JWTs.
- Doubles or boundary isolation: fixture HTTP endpoint only, disposable database, local browser; no production/network service.
- Edge cases: old holder delayed save denied; current revision checked after handoff; validation does not execute; server draft remains after validation failure.

### 5. `publishedSkillHonorsApplicationRecordAccess`
- Type: Java HTTP integration as part of the joined workflow.
- Location: `src/test/java/ai/loomspan/sidecar/management/RemoteAuthoringWorkflowIntegrationTest.java`.
- Proves: a separately minted execution JWT can invoke a published REST skill to read its own record; a foreign record is rejected by the application fixture; management PAT cannot invoke `/v1/**`.
- Inputs/fixture: two subjects, separate records, fixture issuer key, RBAC roles.
- Doubles or boundary isolation: callback verifies JWT itself and records only safe identifiers; no credential logging.
- Edge cases: denied access result must be observed at the execution API and callback, not inferred from validation.

### 6. `clientRecoveryAcrossExpiryConflictAndRestart`
- Type: Java joined focused integration plus existing predecessor suites.
- Location: `RemoteAuthoringWorkflowIntegrationTest.java`; existing `ManagementPersonalTokenHttpIntegrationTest.java`, `ManagementEditingHttpIntegrationTest.java`, and `ManagementDraftRestartIntegrationTest.java`.
- Proves: credential revocation/role downgrade and competing holder stop client writes; expired lease permits a later explicit reacquire while retaining draft; another publication makes draft stale until deliberate complete reconciliation; failed publication keeps the draft; restart keeps draft but loses lease/validation proof.
- Inputs/fixture: existing mutable clock and restart mechanisms where practical; avoid duplicating every server-side matrix in the new class.
- Doubles or boundary isolation: disposable storage and controlled fault injection only; no live deployments.
- Edge cases: return code and conflict code must remain actionable to CLI caller, and no automatic retry should occur.

### 7. `existingServerAndBrowserRegressions`
- Type: existing JUnit suites.
- Location: `src/test/java/ai/loomspan/sidecar/management/`, `src/test/java/ai/loomspan/sidecar/execution/`, `src/test/java/ai/loomspan/sidecar/bundle/`.
- Proves: shared API permission ceiling, same-user handoff, restart, bundle format/import, browser saved-view behavior, JWT and callback semantics remain intact.
- Inputs/fixture: established test fixtures.
- Doubles or boundary isolation: local services/temporary databases only.
- Edge cases: if a failure reveals an agreed integration defect, fix it without inventing a second API or security contract.

## Safe Verification Commands

- Focused: `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'`
- Focused joined: `mvn -Dtest=RemoteAuthoringWorkflowIntegrationTest test`
- Related suite: `mvn -Dtest=ManagementEditingHttpIntegrationTest,ManagementPersonalTokenHttpIntegrationTest,ManagementDraftRestartIntegrationTest,ManagementEditorBrowserIntegrationTest,ManagementImportBrowserIntegrationTest,ConfigurationBundleV1Test,AuthenticatedExecutionApiIntegrationTest test`
- Full safe suite: `mvn test`
- Patch check: `git diff --check`

## Optional Developer Checks

- Install/load the skill in a particular downstream coding agent and inspect its presentation. This is agent-specific and nonblocking; the local subprocess invocation is the required executable portability evidence.
- Run the walkthrough against a separately configured non-production Sidecar/application only if the developer later requests it. This is not part of routine verification.

## Exit Criteria

- [x] The planned red test fails for the intended missing-client reason before implementation.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes, or each environmental failure is reported accurately with residual risk.
- [x] Acceptance criteria map to executable client and joined Sidecar evidence, including real published execution and denied application access.
- [x] Routine automated tests use only loopback fixtures, disposable data, and synthetic credentials.
- [x] Secret, stale revision, lost lease, permission, expiry, revocation, restart, ambiguous publish, and bundle edges above are covered by focused and predecessor tests.
- [x] Optional checks are reported as nonblocking and not represented as already performed.
