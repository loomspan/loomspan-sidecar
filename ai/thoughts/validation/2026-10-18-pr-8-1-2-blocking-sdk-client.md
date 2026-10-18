# PR 8.1.2 implementation and verification

Date: 2026-10-18. Approved profile: Full 5-Step Pipeline, Step 4.
Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-2-blocking-sdk-client.md`.
The implementation is complete; a fresh Step 5 review still determines pipeline completion.
The [design lens](../design-lens.md) applies. No compatibility code, data reset,
Framework rebuild, publication, commit or push was performed.

## Implementation

The independent SDK reactor now supplies a plain Java blocking `SidecarClient`,
public execution/catalog values and credential holder, and distinct local/HTTP/
protocol/unknown-acceptance/wait-termination exceptions. Apache HC5 5.6.1 owns a pool
with retries, redirects, cookies and authentication replay disabled. Jackson 3.1.4
decodes strictly typed envelopes without parsing result text; duplicate fields and
trailing documents are rejected. A client-owned daemon scheduler cancels individual
transport requests at their absolute deadlines or on interruption. Cancellation
ends local transport activity and makes no server-cancellation claim.

Builder validation, finite defaults, response limits, safe diagnostics, credential
redaction in structured HTTP problems and TLS verification are shared by the Boot
starter. Standard Boot SSL bundles supply optional trust material. Core remains
independent of Boot, Sidecar and Framework. SDK README documents the public surface
and outcome/uncertainty rules.

The neighboring suite supplies a plain Java consumer, temporary current-JWT fixture,
fixed response-loss proxy, independent synchronized attempt/effect counters and
provider-free local composition. Business/reference fixtures and deployed state
were left untouched. Standard/hardened recipes remain unimplemented.

## Verification commands and actual results

Commands below ran from the Sidecar checkout unless an external checkout is named.

| Result | Command | Evidence |
| --- | --- | --- |
| Expected red | `mvn -B -ntp -f sdks/java/pom.xml -pl core -am test` | Initial testCompile failed specifically on missing SidecarClient, CredentialSnapshot, ExecutionStatus and WaitOptions before their implementation. |
| Pass | `mvn -B -ntp -f sdks/java/pom.xml -pl core -am test` | 11 core tests, zero failures/errors, including deadline/interruption and real TLS. Final sources also reran in the clean install below. |
| Pass | `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am test` | Starter's 6 tests passed, including actual HTTP, close and standard SSL bundle trust/hostname checks. |
| Pass | `mvn -B -ntp -f sdks/java/pom.xml -pl core -am '-Dtest=SidecarClientTlsTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | Focused trusted/untrusted/wrong-host TLS proof passed. |
| Pass | `python sdks/java/tests/test_build_boundary.py` | Clean disposable reactor and eight direct/transitive Framework/Sidecar negative variants passed (2 unittest methods). |
| Pass | `python sdks/java/tests/test_build_boundary.py BuildBoundaryTest.test_core_rejects_direct_and_transitive_boot_runtime` | Two additional synthetic Boot variants failed at the intended core Enforcer ban (1 unittest method). |
| Pass | External: `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q` | 36 tests passed, including real loopback loss proxy, JWT signature/mount isolation, failure privacy and count bounds. |
| Pass | External: `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` | Formatting passed. |
| Pass | External: `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py` | Static checks passed. |
| Pass | External: `.venv/Scripts/python.exe -m pytest tests -q` | 146 safe offline tests passed. |
| Pass | External: `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar` | All 14 stages passed: actual root/SDK/driver builds, isolated runtime, three SDK flows, counters, graceful stop and cleanup. |
| Pass | `git diff --check` in both checkouts | No whitespace errors. |

The composed runner executed these real build commands with the resolved Maven
executable, and retained their pass/fail status in its report:

- `mvn -B -ntp -f C:/opendev/code/loomspan-sidecar/pom.xml clean verify`:
  256 tests, zero failures/errors, 3 pre-existing configured production-browser skips.
- `mvn -B -ntp -f C:/opendev/code/loomspan-sidecar/sdks/java/pom.xml clean install`:
  11 core and 6 starter tests passed against final SDK sources, and artifacts installed.
- `mvn -B -ntp -f C:/opendev/code/loomspan-sidecar-test-suite/sdk/driver/pom.xml -Dsdk.version=0.1.0-SNAPSHOT clean package`:
  actual plain Java consumer and runtime classpath packaged successfully.

The three root skips belong to `ProductionComposeBrowserIntegrationTest`
(`productionHistoryAndImportInspection`, `productionHttpsPasswordLinkAndLogin`,
`productionHttpsInvitationAndConcurrentEditing`); they are outside this ticket's
required local SDK composition. No required SDK acceptance gate is unrun.

## Acceptance mapping

Core test prefix below is `sdks/java/core/src/test/java/ai/loomspan/sdk/`.

| Criterion / outcomes row | Executable evidence |
| --- | --- |
| Discovery, submit, status, await; immediate known acceptance | `SidecarClientTest.discoversSubmitsAndAwaitsOpaqueResult`: prefixed paths, metadata/schema strings, nested object input, one POST and QUEUED/RUNNING/COMPLETED reads. |
| Opaque result and server-authoritative terminal failure | `SidecarClientEdgeTest.preservesOpaqueResultsAndPrimaryFailure`: empty/whitespace/plain/multiline/supplementary Unicode/null-looking/valid-looking/malformed-looking strings and actual null; FAILED returns original primary failure/issues despite success-looking text. |
| Local no-send failures, single encoded segment | `rejectsLocalFailuresWithoutSendingAndEncodesOneSegment`: null arguments and unsafe names count zero; Unicode/space/question/hash encoded once under the configured prefix, with unchanged bearer header. ObjectNode type excludes non-object shapes at compile time. |
| Known HTTP error, never assert rejection; no redirects/auth replay | `distinguishesHttpProtocolAndUnknownAcceptanceWithoutReplay`: 301/302/307/308/400/401/403/404/429/503, structured problems, truncated error body, one attempt per call, destination counter zero and challenge handling disabled. Initial operation test ignores an untrusted Location. |
| Unknown submission acceptance, reliable ID only | Same method: closed-after-consumed POST, empty/malformed/non-object/truncated/missing/wrong-type/bad-UUID/duplicate-field acceptance and wrong success status yield unknown with no ID and no retry. |
| Invalid status envelope retains expected ID and last snapshot | `validatesExecutionEnvelopeAndKeepsRequestedId`: missing/type-invalid/UUID/time/status/terminal-failure envelopes, mismatched ID during await, optional early configuration/events and unknown-field tolerance. |
| Total deadline, failed-read stop | `awaitHonorsOneTotalDeadlineAndStopsOnFirstReadFailure`: 250ms total wait bounded below 1s for queued, stalled headers, stalled body and slow drip despite 5s response setting; first HTTP/protocol read error stops at two reads and retains RUNNING snapshot. |
| Interruption preserves ID/snapshot and restores flag | `interruptionRestoresFlagDuringSleepAndNetworkRead`: dedicated caller thread joins within 1s for interrupted sleep and in-flight body read, no additional POST or poll. |
| Response overflow/resources/close | `boundsResponsesReleasesResourcesAndClosesIdempotently`: exact cap success, declared/chunked overflow, malformed/truncated responses, repeated error-to-success reuse and oversized acceptance protocol failure. `validatesConfigurationDefaultsAndClose` proves idempotent close and all subsequent operations count zero sends. |
| Base URL, finite durations, defaults, standard TLS | `validatesConfigurationDefaultsAndClose` and `SidecarClientTlsTest.verifiesCertificatesAndHostnamesWithStandardTrustMaterial`: required/restricted URL, invalid duration/size, documented defaults, trusted success and untrusted/wrong-host failure before HTTP. |
| Credential privacy and safe errors/logs | `credentialAndErrorsDoNotExposeTokens`: token/input sentinels absent from exception stack/cause/default stderr, no ordinary getter or Java serialization, safe Jackson refusal/redaction, HTTP problem token echoes removed recursively. |
| Equivalent Boot behavior | `spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SidecarClientAutoConfigurationTest`: six tests for discovered registration, defaults/all properties, override, invalid config, response limits/timeouts, context close and Boot SSL bundles plus hostname rejection. |
| Real completed/failed/lost response and independent counters | Actual local SDK report below; driver uses only core plus runtime transitives and compares exact model string and primary REST HTTP-503 failure. Loss proxy and synthetic effect counts are separate. |
| Documentation | SDK README, external SDK lane/README/project-agreement scope and actual compiling Java consumer. |

## Actual composed evidence

Report:
`C:/opendev/code/loomspan-sidecar-test-suite/evidence/sdk/20261018T170928Z-ce1a23cab707/report.json`.

All 14 stages passed; `completed`, `opaqueResultPreserved`, `failed`,
`primaryFailurePreserved`, `acceptanceUnknown` are true. Startup model requests: 0.
Final counters: modelRequests=1, submissionAttempts=1, forwardAttempts=1,
syntheticEffects=1, checked again after driver completion. Graceful Sidecar exit:
143; OOM=false; cleanup passed and no SDK containers remain.

Source revision: `a1ab2d80d9d575614039b01ffe13341bf9c2ea11`, dirty=true.
Framework dependency: `1.0.0-beta.8`. SHA-256:

- Sidecar JAR: `c6f93339ce23c35630c811538ffa6a651920149888c4ac538f939d52de79eff7`
- SDK core: `20f38e7167e1ad3fda97005b7cf6cd9b39a40ad90fa33208cb464d5b5a740b54`
- SDK starter: `bb1a478168a8e219020ff547945a3ebd61ddccc91bce80aca27e802a1f8b7ea9`
- Driver: `84969f220117adc0bf7e767a0a86c980285fbf2cc60f5bb2f819d1c6397a2590`

These counts prove this fixture's single-attempt/no-replay behavior, not exactly-once
business semantics. No provider call, business-runtime startup, retained-data reset,
paid operation or deployed configuration change was performed. Temporary credentials
were driver-only; the signing private key remained in memory. Optional developer checks: none.
