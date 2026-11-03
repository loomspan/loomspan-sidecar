# HTTP configuration management and authoring testing plan

## Change Summary

Ticket and implementation plan:
`2026-11-02-pr-9-2-http-configuration-management-and-authoring`.
This finalized Step 3 plan incorporates the developer's invocation-only HTTP
validation decision and rechecked current source/tests. No Framework change or
install is needed. No tests were changed or run during planning. Existing locations
are real test files; new method names describe planned observable assertions.
Preserve developer tracked design/ticket edits recorded in the implementation
plan. Only research and plans were created by this pipeline so far.

## Impacted Areas and Risks

| Area | Risk | Planned evidence |
| --- | --- | --- |
| Validation boundary | Early HTTP rejection or misleading readiness guarantee; environment inheritance | HTTP-invalid/Framework-valid startup/publication/restore accepted, invocation rejects; profile/import capture and DB omission tests |
| Credentials | Provider map rejects HTTP-only records; plaintext/ciphertext leakage | HTTP-only, shared, missing/wrong-key and bundle inspection cases |
| Publication | Partial active replacement or lost draft on failure | Failure hooks and exact draft/lease/content assertions |
| Correlation | Admission/publication race loses durable mapping | Existing after-handoff hook and model-only/HTTP cases |
| Retention/shutdown | Old admitted request sees new settings or client closes early | Latches and supported API lifecycle tests |
| Management/UI | Payload replacement weakens permission/recheck flows | Existing token/CSRF/lease suites plus browser shared representation |
| Persistence | New schema/bundle accidentally retains old routes | Fresh SQLite introspection, ZIP inventory and old-version rejection |

## Existing Coverage and Environment Constraints

Use JUnit/AssertJ, Boot integration fixtures, temporary SQLite databases, JDK
loopback HTTP servers and existing Playwright Chromium tests. The normal build is
the Maven wrapper targeting Java 21 and local beta.9-SNAPSHOT. Browser tests need
Playwright Chromium; install only if missing with the repository-standard CLI
command below. No external model, app, deployed service, secret or production
store is used. Existing HTTP fixtures already prove reusable client behavior,
but do not replace this ticket's composed startup/management evidence.

All Java test locations below are under
`src/test/java/ai/loomspan/sidecar/`.

## Failing Test First

- Name: `httpTargetHeadersAndFixedCredentialAreCapturedWithoutProviderEntries`
- Type/location: unit,
  `configuration/EffectiveExecutionConfigurationTest.java`.
- Arrange: complete execution YAML with `default.http`, ordinary Accept header,
  fixed Authorization reference and an encrypted credential, no model connection.
- Act/assert: assemble; the provider values map is empty, captured HTTP subtree
  contains only encrypted records, plaintext absent from YAML/toString, identity
  and version retained; composition performs no HTTP domain validation or request.
- Expected pre-fix failure: existing scanner rejects `headers` and assembly puts
  the HTTP-only credential into the provider map without attaching HTTP records.
- Current complete-build removed-type/API compilation failures must be recorded
  separately; a compiler failure is not the intended behavioral red test.

## Tests to Add or Update

### 1. Effective credentials and reference-only saving

Location: `configuration/EffectiveExecutionConfigurationTest.java`,
`CredentialPortabilityIntegrationTest.java`, `CredentialEnvironmentIntegrationTest.java`.
Cover HTTP-only and provider-only references, a shared record, missing required
record, duplicate IDs, explicit replacement/version change, wrong key/ciphertext,
authored HTTP inventory rejection, exact restored credentials and no startup
fallback. Test ordinary HTTP headers while direct provider secrets, duplicate
maps, malformed and multiple YAML documents still cannot hide stored secrets.
All encryption uses fixed test keys, not external process credentials.
Add `missingHttpReferenceDoesNotBecomeManagementValidation`: with an existing
`http` map, a fixed reference absent from managed records and an empty required-ID
inventory, assembly/Framework validation succeed and invocation fails before
dispatch. Contrast an explicitly required missing record, which fails management
inventory integrity. Attach ciphertext without requiring valid HTTP auth shapes,
target maps, headers or pointers. Assert candidate inequality for changed
ciphertext even with identical identity/version, and safe rejection of inconsistent
persisted effective content without exposing record values.

### 2. Invocation-only HTTP validation across startup and management

Locations: `configuration/RuntimeConfigurationIntegrationTest.java`,
`ConfigurationModeIntegrationTest.java`; replace obsolete scenarios in
`rest/RestRouteStartupIntegrationTest.java`, `RestRouteValidationIntegrationTest.java`
and `RestRouteRestartIntegrationTest.java` with HTTP-named tests.

Use Framework-valid declarations and a present map-valued selected HTTP subtree
whose HTTP semantics are invalid: unknown target, GET/body, malformed pointer or
invalid timeout/header. File startup/readiness, draft validation/publication and
selected DB restoration must succeed without HTTP domain validation or network
probes. Invoke the affected skill and assert a clear terminal configuration failure
before any request reaches the loopback destination. A missing/unusable HTTP
credential should likewise fail at invocation when it does not independently
violate managed credential-record integrity/security rules.

Separately verify that malformed Framework declarations, unresolved executor
references and invalid selected-map structure still fail Framework validation.
Management authorization, revision and credential-record integrity failures remain
rejected at their existing boundaries; do not relabel these as HTTP validation.

A valid unreachable target can publish without a probe, then fail on invocation
as a transport error. A valid reachable configuration executes a loopback HTTP
skill. DB startup ignores conflicting startup executor targets/providers. Restart
with omitted HTTP settings never inherits prior/startup values; distinguish
Framework-required selected-map presence from HTTP-required target semantics.

File-mode multi-document/profile/import cases prove selection through actual
invocation outcomes and observed destination requests, not a new captured-data
accessor or independent configuration parser. No Framework API change is needed.
Check imports and rollbacks obey the same no-HTTP-domain-validation boundary.

### 3. Durable contract and portable bundles

Locations: `storage/ConfigurationSnapshotRepositoryTest.java`,
`ConfigurationSnapshotStoreTest.java`, `ConfigurationDraftStoreTest.java`,
replacement `bundle/ConfigurationBundleV4Test.java`.
Fresh temporary schema has no route column. Snapshot/draft round trips preserve
exact skills/execution text and credential inventory. Format 4 round trip has no
`rest.json`/route fields, preserves meaning and exact source labels, checks
digests/CRC/bounds and encrypted versions, omits plaintext/key material, rejects
format 3. Included versus configuration-only imports retain explicit credentials
and correct/wrong-key behavior. No real development database reset in tests.

### 4. Atomic management/security workflow

Locations: `configuration/ManagementConfigurationHttpIntegrationTest.java`,
`management/ManagementEditingServiceTest.java`, `ManagementEditingHttpIntegrationTest.java`,
`ManagementPersonalTokenHttpIntegrationTest.java`,
`ManagementDraftRestartIntegrationTest.java`,
`configuration/FileModeManagementHttpIntegrationTest.java`.
Use new HTTP skill/executor payloads through normal endpoint path. Preserve
account/lease/revision/base ownership, handoff/reconciliation, expired/revoked
credentials and permission rechecks after locks/validation, cumulative scopes,
CSRF and ambiguous session/token rejection. Framework-invalid/stale candidate keeps old
configuration and draft; successful publish clears exact draft/lease. Import and
rollback have the same restrictions, including file mode. An authorized publisher
can approve passthrough targets through normal publication without a second ledger.

### 5. Captured work, correlation and lifetime

Locations: `execution/ExecutionConfigurationCorrelationIntegrationTest.java`,
`configuration/RuntimeConfigurationIntegrationTest.java`,
`http/HttpExecutorLifecycleIntegrationTest.java` and new
`configuration/HttpConfigurationShutdownIntegrationTest.java`; replace old
`rest/RestGenerationIntegrationTest.java` and obsolete response-envelope tests
with current HTTP contracts where they still own relevant scenarios.
Use supported Framework admission and loopback latches. Hold an old admission,
publish a different URL/header/credential version, then prove old execution uses
old capture and a new admission uses new capture. Interleave publication between
handoff and mapping using the existing hook; the durable ID remains old and
protected from prune. Include model-only execution to prove client independence.
Injected prepare/publish failure never changes active configuration or closes the
shared client. Composed shutdown stops dispatch, discards queued work, allows
admitted completion or Framework cutoff, then closes client without a second drain.
Use bounded deterministic waits, never sleeps against live services.

### 6. Browser, remote client and guidance

Locations: `management/ManagementEditorBrowserIntegrationTest.java`,
`ManagementHistoryBrowserIntegrationTest.java`, `ManagementImportBrowserIntegrationTest.java`.
Move route-editor ownership/unsaved-text scenarios to skill/execution fields;
assert no routes editor/card/payload exists. Author an HTTP skill and targets,
validate/publish, inspect JAVA classification and YAML provenance available from
actual authored state, export/import, then compare the same representation through
the HTTP API. Preserve read-only/second-tab/handoff/revision feedback. Assert
validation/readiness wording does not certify HTTP validity or reachability;
HTTP-invalid but Framework-valid drafts can publish and fail only on invocation.
Python `scripts/test_agent_skills.py` and packaged
`client/test_sidecar_authoring.py` use fake loopback boundaries and current payloads;
examples/guidance contain executable skill and executor YAML without routes.
Run architecture enforcement on application and tests, with no internal imports.

## Safe Verification Commands

Run focused commands listed in each implementation phase after its edits. Final
required local checks are:

```powershell
./mvnw.cmd -B -ntp verify
python -m unittest discover -s scripts -p 'test_agent_skills.py' -v
python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v
python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v
python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v
```

If Chromium is absent, repository-standard setup is:

```powershell
./mvnw.cmd -B -ntp test-compile org.codehaus.mojo:exec-maven-plugin:3.6.2:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test '-Dexec.args=install chromium'
```

`scripts/verify-http-executor.ps1` may provide supplemental isolated runtime
evidence but cannot establish management/UI acceptance or whole-app success.
Do not create stubs, disable tests or introduce compatibility to make compilation
green. Ticket-permitted intermediate blockers must have exact command/error and
downstream PR 9.3 ownership in the implementation artifact. The startup public
contract escalation is resolved by the runtime-only decision, not deferred.

## Optional Developer Checks

None required. Real deployment/mTLS operations need configured non-production
authorization and are outside routine verification. A manual browser observation
may supplement, never replace, the Playwright assertions.

## Exit Criteria

- [x] Both plans are finalized against invocation-only HTTP validation using the
  existing installed snapshot; no new startup-data API is required. This checkbox
  records planning completion only, not implementation verification.
- [ ] Planned behavioral red test fails for the intended reason when compilable.
  NOT RUN behaviorally: baseline deleted types/API calls blocked compilation;
  no stubs were introduced. The new assertion passes after integration.
- [x] New/updated unit, management, browser, restore and lifecycle tests pass.
- [x] Broadest safe relevant local suite passes, or every ticket-permitted
  intermediate blocker is precisely evidenced and assigned without claiming pass.
- [x] All acceptance criteria map to actual executable evidence in implementation
  results; no disabled tests, secret exposure, real store reset or live services.
- [x] Obsolete format rejection and development reset impact are recorded.
- [ ] Fresh independent review evaluates current code and actual verification.


## Step 4 verification handoff

See [verification and acceptance evidence](../verification/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md).
The final broad run passes all 297 functional tests and fails only the unchanged
release-preparation guard (298 total, zero skips). Two additional full-Sidecar
shutdown tests pass separately. PR 9.3 owns the exact release/tooling blockers
recorded there. No independent review is claimed by Step 4.
