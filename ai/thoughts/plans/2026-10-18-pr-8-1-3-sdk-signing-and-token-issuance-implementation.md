# SDK signing and execution-token issuance implementation evidence

Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md`.
Execution: Step 4, Full 5-Step Pipeline, developer approved, 2026-10-18.

**Development policy: replace superseded contracts directly; no compatibility
shims, legacy adapters or parallel legacy APIs.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt).
No development-data reset, deployed data changes or release changes are needed.

## Implementation and material details

Core now owns explicit `SigningSettings`, reference-only `SecretReference`, the
narrow `SigningKeySource`, startup `SigningKeys`, immutable
`AuthenticatedIdentity`/`IssuanceSettings` and one `ExecutionTokenIssuer`.
Nimbus 10.10 handles public JWK thumbprints and JOSE serialization/signing
integration; JCA owns RSA, PKCS#8, certificates and keystores. Fractional NumericDate
claims use decimal seconds directly because Date-based claim construction would
truncate positive subsecond lifetimes.

The loader validates key type, minimum size and public/private correspondence
before publishing an explicit public JWK. It detects the actual keystore type
and rejects a declared format mismatch, avoiding JCA's automatic JKS/PKCS12
compatibility loading. Provided material never falls back to generation.
Password arrays are cleared and underlying parser/provider exception chains are
discarded to prevent their diagnostics echoing private material or passwords.
The supported reference/PEM/certificate warning contracts match the plan.

Public export uses a sibling temporary file, closes the write before moving,
requests ATOMIC_MOVE plus REPLACE_EXISTING, and removes temporary files after
failure. Denied write, denied replacement and unsupported atomic move are
deterministic package-private NIO seam tests. Real temporary directories exercise
normal creation/replacement. No permission probe or permission mutation exists.

The new independently registered Boot configuration requires signing even when
the application provides its own client. Existing direct-import client tests
remain unchanged because they deliberately test client wiring in isolation.
The new startup tests exercise both configurations together and inspect import
discovery. Packaged starter resources contain only the imports registration and
set no mode. Spring reads authentication/name/authorities once at operation entry
and delegates to the shared issuer without installing an application security chain.

The SDK README documents actual property names, core/Spring usage, both modes,
operator ownership, sole writer, restart effects, formats/references, generation,
warning thresholds, JWT profile and exact role naming. Root Sidecar receiver,
callback and deployment scenarios remain the later tickets' scope.

## Acceptance evidence

| Criterion | Executable evidence / source |
| --- | --- |
| Missing explicit mode, actionable message, no packaged default | `ExecutionTokenAutoConfigurationTest.missingModeNamesBothOptionsAndQuickstart`; starter imports/resource inspection; `SigningSettings` |
| Reject other-mode settings | `SigningKeysTest.directoryErrorsAndMixedModesAreActionable`; `ExecutionTokenAutoConfigurationTest.invalidAndMixedSettingsFailWithoutEchoingLiteralPasswords`; nonnull provided settings are rejected as a whole in colocated mode |
| Required/existing/writable colocated directory, RAM-only private key, atomic export, new restart kid | `colocatedExportsOnlyPublicKeyAndChangesKidOnRestart`, `directoryErrorsAndMixedModesAreActionable`, `atomicExportFailsClosedAndPreservesPriorFile`; public-only JWK fields and directory inventory |
| PKCS12/JKS/PEM, matching public key/certificate export, invalid/weak/non-RSA rejection | `providedFormatsLoadTheSuppliedKeyAndExportCertificates`, `invalidWeakNonRsaAndMismatchedMaterialNeverReplacesExport`, `keystoreAliasesMissingFilesDistinctPasswordsAndFormatOptionsFailClosed`; startup PEM delegation test |
| Reference-only passwords and certificate warnings | `secretReferencesAndSigningWarningsAreSafe`; distinct JKS passwords and default reference tests; startup literal-property/log rejection |
| All JWT fields and RS256/thumbprint | `issuedTokenMatchesProfileAndExactAuthorityIntersection`; independent public RS256 verification and parsed JOSE/JWT assertions |
| Default issuer/audiences and additions | `defaultsAdditionalAudiencesAndEmptyRoles`; Boot `explicitModeBindsDefaultsAndDiscoversConfiguration` proves application-name independence |
| 15m default, positive configured lifetimes, WARN above 1h | core profile/default tests and `positiveDurationsKeepPrecisionAndWarnOnlyAboveOneHour`; checks 1ns/500ms/1s/1h/2h and overflow |
| Same Spring/custom path, reject invalid identity, business inputs cannot alter identity | `springCapturesAuthenticatedIdentityOnceAndMatchesCustomIssuance`, `rejectsUnauthenticatedAnonymousNullAndBlankIdentity`; local HTTP capture of credential beside hostile business JSON |
| Exact authority intersection, empty roles, immutable snapshots | core profile/default tests, mutable source collection checks and bounded two-identity concurrency test |
| Private material/token diagnostic safety | `privateSourceAndSigningFailuresRemainRedacted`, core credential serialization/profile tests, secret reference/warning checks, startup captured-output/error-stack checks and retained `SidecarClientEdgeTest.credentialAndErrorsDoNotExposeTokens` |
| Required SDK documentation | `sdks/java/README.md` reviewed against tested settings and token assertions |

## Verification ledger

All commands ran from the repository root in PowerShell. Maven -D arguments with
dots/commas are quoted to preserve them through this host's native invocation.

- Initial unquoted planned red command failed before tests because PowerShell
  split `-Dsurefire.failIfNoSpecifiedTests=false` into an unknown lifecycle phase.
  Exact command: `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am -Dtest=ExecutionTokenAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- Actual red: `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am "-Dtest=ExecutionTokenAutoConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  failed as intended: one test failure, `hasFailed()` because the original context
  started successfully. This test initially imported only the existing client
  auto-configuration, and was updated to include new signing wiring afterward.
  The identical command passed after implementation.
- `mvn -B -ntp -f sdks/java/pom.xml -pl core -DskipTests compile` initially failed
  on an ambiguous JCA/Nimbus `RSAKey` import; fixed with an explicit Nimbus import.
  Subsequent focused/full builds compile successfully.
- PASS: `mvn -B -ntp -f sdks/java/pom.xml -pl core "-Dtest=SigningKeysTest" test`
  (initial six signing tests); final expanded signing class passes seven tests in
  the complete build.
- PASS: `mvn -B -ntp -f sdks/java/pom.xml -pl core "-Dtest=ExecutionTokenIssuerTest" test`
  (initial four issuer tests); final expanded class passes five tests in the
  complete build.
- `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am "-Dtest=ExecutionTokenAutoConfigurationTest,SpringExecutionTokenIssuerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  initially failed compiling an unavailable AssertJ helper; replaced with explicit
  full-stack-string assertions. The identical command then passed six tests.
- PASS: `mvn -B -ntp -f sdks/java/pom.xml test` (34 tests at that point).
- PASS: `mvn -B -ntp -f sdks/java/pom.xml clean install` (first expanded build 35
  tests; final build 36 tests: 24 core and 12 starter, zero failures/errors/skips).
  This also runs all planned core-only regressions, so a separate `-pl core test`
  invocation was superseded by the complete reactor.
- PASS: `python sdks/java/tests/test_build_boundary.py` — all three unittest groups passed in 141.671 seconds, covering the clean control plus ten direct/transitive forbidden-dependency reactor variants.
- PASS: `git diff --check` at implementation review and final handoff; no whitespace defects.

Only disposable synthetic keys, files and loopback HTTP/TLS servers were used.
No operator keys/trust files, deployed data, services, model providers or framework
source builds were touched. The only preexisting changes were the three prior
pipeline artifacts; they are preserved. No commit was made.

## Optional developer checks

None. Actual composed app/Sidecar startup/restart, login, callback and provided
deployment evidence remains PRs 8.1.4â€“8.1.7. Root Sidecar tests were not run because
this independent SDK-only change does not modify root production/build behavior.

Independent Step 5 review is still required for final pipeline completion.
