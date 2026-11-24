# Sidecar portable skill names testing plan

## Change Summary

Exercise explicit namespace and local name from authenticated discovery to ID-based polling. Prove the pair survives a real queue wait, uses the existing HTTP executor, and appears in the file-startup record. Retain Framework authority for portable names and the existing startup and archive path boundaries.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| HTTP and identity | Valid hyphenated names select a different target, or absent owners hide malformed pairs | Catalog-to-detail-to-submit reuse plus boundary and failure matrix |
| Authorization and release | Role denial or current trust checks move or weaken during replacement | Existing issuer/subject, exact role, queued expiry, trust withdrawal and shutdown tests |
| Execution correlation | A queued record loses an identity component or binds an older generation early | Held callback test with simultaneous queued and completed inspection plus publication/correlation suites |
| File and database activation | Inspection uses the old field or changes document storage or activation | Public catalog and durable startup inspection in file mode, managed publication in database mode |
| Path and diagnostic fidelity | Archive labels are interpreted as paths, or raw/model/consumer names are mixed | Mounted glob and real ZIP cases, exact diagnostic/result assertions, distinct structured and provider fixtures |
| Combined SDK and quickstart | Synthetic protocol checks miss a mismatch with the real controller or Framework worker | Native Sidecar package, shipped quickstart CLI and host, and both verified callbacks |

## Existing Coverage and Environment Constraints

- Use local Java 21, Maven and Framework `1.0.0-beta.10-SNAPSHOT`. The orchestrator already installed the snapshot from the sibling source using the prescribed Mockito agent. Its successful run is recorded in `target/framework-portable-install.log`; Sidecar verification is separate.
- `AuthenticatedExecutionApiIntegrationTest` boots Sidecar, installs database-mode fixtures, and drives JWT-bearing HTTP and real callback/Responses loopback servers. It already controls worker capacity and a callback latch. `ConfigurationModeIntegrationTest` uses real temporary files and SQLite. `ExecutionConfigurationCorrelationIntegrationTest` exercises captured provider and callback generations. `ConfigurationBundleV4Test` constructs complete ZIP streams with checked manifests.
- Use the existing local Playwright Chromium 1187 cache for browser tests. Use native filesystem/process permissions if the Windows sandbox blocks cached binaries, browser execution, SQLite files, or Java test cleanup. If necessary, run the same tests in a complete disposable copy under `target/` with source, resources, scripts and release/version inputs intact. Record actual failures and reruns.
- Omit the opt-in `sidecar.fixtureSuite` property. The separate consumer fixture repository has older declarations and filenames outside this ticket. Ordinary suite checks and checked-in examples must pass.
- Docker Desktop's Linux daemon was unavailable. Native loopback execution of the checked-in planner and host requires Java, Python, OpenSSL and ports 8081 and a free Sidecar port. Keep everything bound to loopback and use a new ignored data directory. Use the shipped synthetic model settings. The optional real-provider triage example is separate.

## Failing Test First

- **Name** `acceptsAndPollsExactOwnedResultWithWorkerJwt` with catalog-derived namespace and local name.
- **Type** Authenticated HTTP integration.
- **Location** `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`.
- **Arrange/Act/Assert** Use the database-mode REST callback fixture. Add the first assertions for the replacement list/detail routes and the exact pair before updating controller implementation. Reuse that pair for POST and assert the returned ID-based terminal identity and exact callback result.
- **Expected pre-fix failure** The authenticated namespace route is absent from the old controller. With the freshly installed snapshot, the pre-fix source may already fail at Java compilation because it calls superseded Framework signatures and descriptor accessors. Record this separately from an observed HTTP red. If it prevents reaching HTTP without an intermediate compatibility implementation, retain the test-first assertions and rerun after the coherent replacement.

## Tests to Add or Update

### 1. Catalog, accepted execution and terminal record

- **Type/location** `AuthenticatedExecutionApiIntegrationTest.java`, existing `acceptsAndPollsExactOwnedResultWithWorkerJwt` and `requiresJwtAndExposesCatalogWithoutRoleFiltering`.
- **Proof** Under the existing database-mode fixture, list `default` and select a real hyphenated name such as `echo-rest`. Capture `namespace` and `localName`, assert both in detail, reuse them to submit a `message` with Unicode and an escaped newline, and verify 202 with the matching poll URI. Assert terminal namespace, local name, exact output, configuration correlation, callback bearer and issuer/subject checks on subsequent reads. Assert the former top-level identity is absent from poll and catalog records. Verify unauthenticated discovery 401 and existing discovery for an authenticated user without the execution role.
- **Isolation** Existing locally seeded SQLite and mock HTTP services.

### 2. Invalid identity, absent identity and authorization

- **Type/location** `AuthenticatedExecutionApiIntegrationTest.java`, table-driven request coverage plus existing invalid body, forbidden role, and transport tests.
- **Proof** 400 for uppercase, prohibited provider substrings, malformed hyphen syntax, overlength namespace, and malformed local values such as an underscore or encoded colon. Check list for malformed namespaces, and detail and POST for malformed pairs. Include an absent but valid namespace with a malformed local name and a pair over the combined boundary, proving validation happens before the absent-owner result. For example, with `order-support` use locals of 49 and 50 lowercase ASCII characters: the first is valid and absent (404), the second is invalid (400). Check correct namespace plus unknown local, absent namespace plus the exact known local under `default`, and old `default-echo-rest` spelling as 404. Authenticated requests to each former route return 404. Keep existing 401/403 behavior and JSON/schema/capacity outcomes. A valid absent name is checked before body reading so a request with missing input on that target is still 404.
- **Isolation** Existing authenticated loopback Sidecar. Assertions on removed routes carry a JWT because the `/v1/**` authentication boundary remains.

### 3. Real queued handoff, trust and generation timing

- **Type/location** `AuthenticatedExecutionApiIntegrationTest.java`, `ExecutionCoordinatorTest.java`, `ExecutionDiagnosticsTest.java`, `ExecutionConfigurationCorrelationIntegrationTest.java`, `ExecutionOutputSchemaIntegrationTest.java`, and `HttpExecutorIntegrationTest.java` under their existing execution/HTTP directories.
- **Proof** Reuse the existing one-worker callback latch to hold the active execution while a second valid authenticated request waits. Inspect its QUEUED pair and null captured configuration ID, release the gate, then verify the same pair on COMPLETED and prove the expected second message and credential reached the callback. Separate expiry/withdrawal cases must fail before callback under the same ordering. Verify both pair arguments presented to a unit handoff and preserve current auth, release and generation correlation checks. Existing publication tests probe current catalog replacement, captured generations, and cleanup. Keep nested structured `namespace:local-name` model fixtures distinct from provider tool names such as `default--call`; assert real provider request tool names and raw result/failure evidence at the established test boundaries.
- **Isolation** Synthetic model and callback servers plus native Sidecar tests. The real admitted invocation and HTTP executor are exercised without network egress.

### 4. File inspection and physical path constraints

- **Type/location** `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java`, plus existing current/history management coverage.
- **Proof** In file mode load a lowercase mounted file from the temp directory, get its metadata using public `snapshot("default").skill(localName)` and read the recorded inspection via the actual store or management configuration output. Assert `namespace`, `localName` and `kind`, missing obsolete name field, startup UUID correlation, and exact lookup in the same snapshot. Read the retained inspection through `FileConfigurationStartupStore` so the stored shape is covered. For rejection, place a valid authored document at e.g. `skills/Upper/file-skill.yaml`, mount `file:/.../skills/**/*.yaml` in a fresh file-mode process/context, and assert startup fails with both offending relative path and component in its exception chain. A paired lowercase success isolates physical spelling from YAML or startup configuration. Existing database startup and authored publication tests still establish `default` and the activation distinction.
- **Isolation** Unique test workspaces and stores, public Framework catalog only.

### 5. Archive paths, labels and Sidecar contract material

- **Type/location** `src/test/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV4Test.java`, `src/test/java/ai/loomspan/sidecar/management/ManagementImportBrowserIntegrationTest.java`, `scripts/test_verify_deployment.py`, and authoring tests.
- **Proof** Feed the import decoder complete, valid format-4 ZIPs with correct digest and matching manifest entries. The baseline `skills/00000.yaml` succeeds and retains a source label like `Import/CustomerLookup.YAML` and exact YAML. A matched uppercase directory and an uppercase extension are each rejected at physical entry parsing. Run the existing management import tests to exercise the route to the same decoder. Verify deployment smoke uses the explicit `default` catalog. Check docs and retained diagnostic schema uses by context rather than removing all `skillName` fields.
- **Isolation** Generated ZIP bytes, browser and verifier unit doubles. No image operations required.

### 6. Native packaged Sidecar, quickstart and SDK seam

- **Type/location** Shipped `examples/quickstart/host/host.py`, its checked-in skills and `execution.yaml`, and `examples/quickstart/invoke.py`, against the JAR produced by Maven. Use the documented database-mode publish path from `docs/quickstart.md` if practical with native browser or the existing management tooling. The reproducible file-mode equivalent below uses all authored inputs, and the normal Sidecar suite separately covers real database-mode publication and execution.
- **Proof** Assert the fixture returns `quickstart complete`, poll by accepted UUID using a fresh token for the same fixture subject, and assert `default`, `quickstart-planner`, terminal status, exact result, and a correlation UUID. Read `/status` once complete and assert exactly the identity and detail callback paths with their respective `verify` and `detail` messages, issuer `loomspan-app`, subject `quickstart-user` and role `QUICKSTART_USER`.
- **SDK extension of the smoke** After the required CLI proof, if proportionate, invoke through the installed Python `SidecarClient` and `AsyncSidecarClient` using `list_skills`, `get_skill`, `submit` with `{"message": "Check the local callbacks"}`, and `wait_for_completion(accepted.id, credential)`. For Java, reuse the built SDK core and its existing Maven classpath to call `listSkills`, `getSkill`, `submit` with an `ObjectNode` carrying `message`, and `await` with the accepted ID. A disposable driver under `target/` suffices. Run clients sequentially and check the two new callbacks for each execution. Do not use CLI samples with a different business schema or introduce a new maintained cross-language harness. The SDK loopback suites from the preceding ticket cover each public surface and the real server is required for the checked-in quickstart.

## Safe Verification Commands

From the Sidecar root in PowerShell with Java 21, set `$agent = 'C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'`.

- **Focused red/post-fix** `mvn -o -B "-DargLine=-javaagent:$agent" "-Dtest=AuthenticatedExecutionApiIntegrationTest" test`.
- **Related name/data boundaries** `mvn -o -B "-DargLine=-javaagent:$agent" "-Dtest=ExecutionCoordinatorTest,ExecutionDiagnosticsTest,ConfigurationModeIntegrationTest,ExecutionConfigurationCorrelationIntegrationTest,ExecutionOutputSchemaIntegrationTest,ConfigurationBundleV4Test,ManagementImportBrowserIntegrationTest,HttpExecutorIntegrationTest,SupportedLoomspanApiArchitectureTest" test`.
- **Full safe suite and package** `mvn -o -B "-DargLine=-javaagent:$agent" verify`. Do not pass a release profile or external fixture property.
- **Python/contract checks** `python scripts/sidecar_version.py check`, `python -m unittest discover -s scripts -p "test_*.py" -v`, `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p "test_*.py" -v`, and `git diff --check a06d4d1`.

### Native, deterministic loopback procedure

From the repository root with the Maven package in `target/`, check that ports 8081 and 18080 are free or choose another unused Sidecar port and update the origin consistently. Use an existing OpenSSL executable on the PATH and isolated state. Start and clean up only the two processes from this run. Equivalent bounded orchestration in an ignored disposable script is acceptable.

```powershell
$quickRun = New-Item -ItemType Directory -Path ("target/portable-names-" + [guid]::NewGuid())
$quickOrigin = 'http://127.0.0.1:18080'
$quickHostOrigin = 'http://127.0.0.1:8081'
$quickJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
$quickPython = (Get-Command python).Source
$quickConfig = ([uri] (Resolve-Path 'examples/quickstart/sidecar/execution.yaml').Path).AbsoluteUri
$quickSkills = ([uri] (Resolve-Path 'examples/quickstart/sidecar/skills').Path).AbsoluteUri.TrimEnd('/') + '/*.yaml'
$quickJar = (Resolve-Path 'target/loomspan-sidecar-1.0.0-beta.5-SNAPSHOT.jar').Path
$previousQuickKeys = $env:QUICKSTART_KEY_DIRECTORY
$env:QUICKSTART_KEY_DIRECTORY = $quickRun.FullName
$quickHost = $null
$quickSidecar = $null
function Wait-PortableReadiness($uri) {
    $deadline = [datetime]::UtcNow.AddSeconds(45)
    while ([datetime]::UtcNow -lt $deadline) {
        try {
            if ((Invoke-RestMethod $uri -TimeoutSec 2).status -eq 'UP') { return }
        } catch {}
        Start-Sleep -Milliseconds 250
    }
    throw "Did not become ready at $uri"
}
try {
    $quickHost = Start-Process $quickPython -ArgumentList 'examples/quickstart/host/host.py' `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $quickRun.FullName 'host.out.log') `
        -RedirectStandardError (Join-Path $quickRun.FullName 'host.err.log')
    Wait-PortableReadiness "$quickHostOrigin/health"
    if ($quickHost.HasExited) { throw 'Fixture host failed to start' }
    $quickSidecar = Start-Process $quickJava -ArgumentList @(
        '-jar', $quickJar, '--server.address=127.0.0.1', '--server.port=18080',
        '--loomspan-sidecar.configuration.mode=file', '--loomspan-sidecar.trust.mode=file',
        "--loomspan-sidecar.trust.file.jwks=$($quickRun.FullName)/jwks.json",
        "--loomspan-sidecar.storage.database-path=$($quickRun.FullName)/sidecar.db",
        "--spring.config.additional-location=$quickConfig",
        "--loomspan.skills.locations=$quickSkills"
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $quickRun.FullName 'sidecar.out.log') `
      -RedirectStandardError (Join-Path $quickRun.FullName 'sidecar.err.log')
    Wait-PortableReadiness "$quickOrigin/actuator/health/readiness"
    if ($quickSidecar.HasExited) { throw 'Sidecar failed to start' }
    $quickOutput = '{"message":"Check the local callbacks"}' | & $quickPython examples/quickstart/invoke.py `
        default quickstart-planner --origin $quickOrigin --host $quickHostOrigin
    if ($LASTEXITCODE -ne 0 -or $quickOutput[-1] -ne 'quickstart complete' -or
        $quickOutput[0] -notmatch '^Accepted execution: ([0-9a-f-]{36})$') { throw 'Unexpected quickstart result' }
    $quickId = [guid] $Matches[1]
    $quickToken = (Invoke-RestMethod "$quickHostOrigin/token" -TimeoutSec 2).access_token
    $quickPoll = Invoke-RestMethod "$quickOrigin/v1/executions/$quickId" -TimeoutSec 2 `
        -Headers @{ Authorization = "Bearer $quickToken" }
    if ($quickPoll.namespace -ne 'default' -or $quickPoll.localName -ne 'quickstart-planner' -or
        $quickPoll.status -ne 'COMPLETED' -or $quickPoll.result -ne 'quickstart complete' -or
        -not $quickPoll.configurationSnapshotId) { throw 'Unexpected poll snapshot' }
    $quickCalls = (Invoke-RestMethod "$quickHostOrigin/status" -TimeoutSec 2).verified
    if ($quickCalls.Count -ne 2 -or $quickCalls[0].path -ne '/callbacks/identity' -or
        $quickCalls[0].message -ne 'verify' -or $quickCalls[1].path -ne '/callbacks/detail' -or
        $quickCalls[1].message -ne 'detail' -or
        ($quickCalls | Where-Object { $_.issuer -ne 'loomspan-app' -or
          $_.subject -ne 'quickstart-user' -or 'QUICKSTART_USER' -notin $_.roles })) {
        throw 'Unexpected verified callbacks'
    }
    $quickOutput
} finally {
    if ($quickSidecar -and -not $quickSidecar.HasExited) { Stop-Process -Id $quickSidecar.Id }
    if ($quickHost -and -not $quickHost.HasExited) { Stop-Process -Id $quickHost.Id }
    $env:QUICKSTART_KEY_DIRECTORY = $previousQuickKeys
}
```

For documented native database mode, use the same process isolation, follow local administrator setup with synthetic credentials, create and validate one draft with actual quickstart documents and the full settings file, authorize the two loopback targets and publish. Run the same CLI and evidence assertions after publication. Use a different fresh database path from any file-mode run. Record the actual mode, commands, and evidence without recording credentials.

## Optional Developer Checks

- Docker deployment/composition validation requires a working Docker daemon. Native loopback checks satisfy the portable naming seam locally. The optional real-provider triage example requires a separately configured account and is outside the required verification.

## Exit Criteria

- [x] Record the pre-fix integration result or exact compile obstruction from the freshly installed Framework snapshot.
- [x] Authenticated list/detail/submit/poll reuse and exact queued handoff pass, including invalid/absent names, old-route rejection and authorization.
- [x] Both configuration modes and generation correlation pass. File inspection emits both first-class fields, startup rejects uppercase physical paths, and archive import separates ordinal paths from exact labels.
- [x] Full safe Maven verification and relevant Python, architecture, documentation and diff checks pass.
- [x] Native quickstart completes against the real Sidecar package and real Framework loopback protocol. Both authorized callback records prove the renamed authored references resolve to the intended leaves.
- [x] Record checks actually run, disposition of any native setup limits, and optional or scoped-out checks without treating them as passing evidence.

## Step 4 verification on 2026-11-24

Sidecar used the pinned `1.0.0-beta.10-SNAPSHOT` installed from the matching
local Framework checkout. The install's Framework test results prepare the
dependency. The checks below use Sidecar's own API, package and fixtures.

| Check | Result |
| --- | --- |
| `mvn -o -B -ntp "-DargLine=-javaagent:$agent" "-Dtest=AuthenticatedExecutionApiIntegrationTest" test` | Expected pre-fix compilation failure on the old Framework `invoke`, `handoff` and descriptor API. The suite could not get to the old HTTP behavior on the installed snapshot. Log `target/portable-sidecar-red.log`. |
| `mvn -o -B -ntp "-DargLine=-javaagent:$agent" verify` | 311 tests, 309 passed and two opt-in skips, with zero failures or errors. Passed native Windows Chromium checks and built the executable JAR. Opt-in skips were the external fixture checkout and the Python-issued credential interoperability test. Log `target/portable-sidecar-verify-final.log`. |
| `mvn -o -B -ntp jar:jar spring-boot:repackage` | Built a packaged app for the first loopback smoke using the compiled Sidecar changes. |
| `target/portable-sdk-venv/Scripts/python.exe target/portable-quickstart-smoke.py` | The checked-in quickstart CLI, Java SDK, and synchronous and asynchronous Python SDKs completed `default:quickstart-planner` against the packaged app in file mode, using the three original YAML documents and the deterministic host. Eight callback records established exact callback paths, payload messages, issuer, subject and role. The CLI's ID poll returned the exact pair, result and file startup correlation. Final package logs in `target/portable-quickstart-7de9f85c2e4240dca7d41f6fb525fc4a`. |
| `target/portable-sdk-venv/Scripts/python.exe target/portable-quickstart-smoke.py --database` | The smoke created an administrator and used an authenticated management session to acquire a lease, save, validate and publish the same three documents and settings in a fresh database. All four clients then completed the planner. Both authenticated callbacks ran on each execution, and the CLI's ID poll identified the exact managed publication. Final package logs in `target/portable-quickstart-8f0d6780e73d4b9b96751661eba34fd1`. The two smoke files under `target/` are disposable drivers. |
| `python scripts/sidecar_version.py check` | Version metadata matched. |
| `python -m unittest discover -s scripts -p 'test_*.py' -v` | 22 passed, one symlink-capability skip. Covers the replacement catalog path in the deployment verifier as well as the quickstart client and packaged guidance. |
| `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` | 11 passed. |

The initial related tests found an outdated exact diagnostic message assertion
and could not launch Chromium in the default sandbox. The first native full suite
also identified stale managed skill fixtures and an obsolete empty classpath
location in the file mode console test. The fixture spellings and location now
match the current Framework contract, and the native environment uses the
existing Chromium installation.

The matching Framework documentation inventory was read from Git
`e7b051e:ai/thoughts/tickets/loomspan-pr-37.7-documentation-audit.md` after
that completed ticket moved out of the current Framework checkout.
Docker Compose, the optional real-provider example and the separate external
fixture suite were outside these local smoke runs.
