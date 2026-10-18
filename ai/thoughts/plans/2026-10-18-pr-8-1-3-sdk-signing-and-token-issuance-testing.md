# SDK signing and execution-token issuance Testing Plan

## Change Summary

- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md`.
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md`.
- Implementation plan: `ai/thoughts/plans/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md`.
- Pipeline mode, selected profile `full`, approved. This step plans tests only; no tests or production source were changed or executed.

The SDK adds explicit signing modes, RSA startup loading and public-only atomic JWKS publication, then a single issuer for trusted custom and authenticated Spring identity. Existing `CredentialSnapshot` and client transport stay in use. Apply the [design lens](../design-lens.md#simplicity-and-technical-debt): replace obsolete contracts directly without compatibility shims or parallel legacy APIs. No development-data reset is required; deployed data must not be deleted.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Signing material | Wrong key, weak/non-RSA key, accidental generated fallback or private export | Real RSA signature verification, negative loader cases, unchanged prior export, public-only filesystem inventory |
| Atomic publication | Partial export, stale temporary file or non-atomic portability fallback | Real existing-target replacement, explicit atomic move flags, injected denied/unsupported move, cleanup and old-content assertions |
| Configuration | Hidden mode default, ignored mixed settings or secret leakage | Boot startup assertions, property/resource inspection, missing/invalid reference and cross-mode matrix |
| Profile | Missing purpose/audience, wrong thumbprint, incorrect expiry or reused identifier | Verify every JOSE/JWT field, independent public signature verification, fixed-clock lifetime cases and unique jti |
| Identity | Anonymous issuance, mutable identity or body-selected authority | Real Spring auth objects, single capture and custom equivalence, defensive-copy mutation and business-input separation |
| Diagnostics | Password, private bytes or token in errors/logs/serialization | Captured logger, complete exception chains and object serialization plus existing credential regression |
| Dependencies | Boot in core or Framework/Sidecar leaks | Maven Enforcer and existing disposable-reactor boundary suite |

## Existing Coverage and Environment Constraints

- JUnit 6 core tests use JDK loopback HTTP servers and `@TempDir`; Boot tests use `ApplicationContextRunner` and AssertJ. Core TLS and starter SSL tests already invoke JDK keytool from `java.home/bin`, using Windows `.exe` when available and a 30-second wait.
- `SidecarClientEdgeTest` covers current credential secrecy and transport errors. `SidecarClientAutoConfigurationTest` covers connection settings and client lifecycle. Retain these behavioral assertions rather than replacing them with issuer tests.
- `sdks/java/tests/test_build_boundary.py` runs a clean reactor and injected direct/transitive dependency violations in disposable copies, each with a 300-second Maven timeout. It is an intentional final gate, not a per-edit loop.
- Maven 3.9.6, Java 21.0.2 through `JAVA_HOME`, and Python 3.13 were observed by research. Direct `java` is not on this shell PATH, so fixture helpers use `java.home`; Maven is the entry point.
- No real Sidecar, database, Framework install, model provider, cloud secret manager, externally authenticated user, live HTTP endpoint or deployed trust file is needed. Maven may resolve declared dependencies through existing repository configuration.
- Use only synthetic per-run keys and known test secrets in isolated temporary directories. Do not change ACLs or infer effective write permission from `Files.isWritable`; elevated Windows processes make that unreliable. A small package-private file-operation boundary injects denied writes and atomic-move failure while normal behavior exercises real NIO.
- Core tests may use package-private `CredentialSnapshot.authorization()` from the same `ai.loomspan.sdk` package to inspect a signed test token. Starter tests should pass the snapshot through a loopback `SidecarClient` request and capture the Authorization header for parsing; do not add a production token getter or use reflection into credential state.
- The SDK reactor is independent of the root POM. A root Sidecar build would not exercise this work. Root receiver/trust changes and composed form-login/callback/provided deployments are later tickets and must not be claimed here.

## Failing Test First

- Name: `missingModeNamesBothOptionsAndQuickstart`.
- Type: Boot startup component test.
- Location: `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfigurationTest.java`.
- Arrange/Act/Assert: use existing Boot `ApplicationContextRunner` with current `SidecarClientAutoConfiguration`, a valid loopback base URL, and no mode. Assert startup fails and its sanitized diagnostic names `loomspan-sdk.signing.mode`, `colocated`, `provided` and the colocated quickstart. This initial test uses existing classes so it can run before signer types exist. Update runner imports to include the new auto-configuration after implementation.
- Expected pre-fix failure: current startup succeeds, so `hasFailed()` fails. Preserve this actual red result in step-4 evidence; compilation failure for missing new types is not the intended red proof.
- Focused red command: `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am -Dtest=ExecutionTokenAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`.

## Tests to Add or Update

### 1. `colocatedExportsOnlyPublicKeyAndChangesKidOnRestart`

- Type: core signing/filesystem component test.
- Location: `sdks/java/core/src/test/java/ai/loomspan/sdk/SigningKeysTest.java`.
- Proves: real 2048-bit RSA generation; one public `jwks.json`; `kid` equals independently recomputed RFC 7638 thumbprint; only RSA public fields plus `alg`, `use`, `kid`; source signs bytes verifiable with that public key; a second factory initialization in the same directory replaces the file and produces a different kid.
- Inputs/fixture: `@TempDir` existing directory, including a preexisting valid export to verify replacement. Enumerate all created files and assert no PEM/private encoding, private JWK members or key bytes appear.
- Doubles/isolation: none for happy path; test process owns its directory.
- Edge cases: missing directory value names its property/path placeholder; nonexistent path is named and remains absent; path is a regular file; no persistent signer state.

### 2. `atomicExportFailsClosedAndPreservesPriorFile`

- Type: core filesystem-boundary component test in `SigningKeysTest.java`.
- Proves: public temporary file is a sibling, closed before move; move requests `ATOMIC_MOVE` and `REPLACE_EXISTING`; no non-atomic fallback; publication failure aborts initialization, retains the prior complete destination and removes temporary files.
- Inputs/fixture: known prior export bytes and a temporary directory.
- Doubles/isolation: package-private file-operations seam records move options and throws `AccessDeniedException` for write/move and `AtomicMoveNotSupportedException` for move. Preserve real filesystem operations for remaining steps.
- Edge cases: nonexistent parent, destination directory, denied write, denied replacement, unsupported atomic move and successful existing-file replacement. Diagnostics name the actual export path and do not leak data.

### 3. `providedFormatsLoadTheSuppliedKeyAndExportCertificates`

- Type: core key-loading component table in `SigningKeysTest.java`.
- Fixture helper: `sdks/java/core/src/test/java/ai/loomspan/sdk/SigningTestMaterial.java`.
- Proves: PKCS12, JKS and unencrypted PKCS#8 PEM all use the provided key, produce stable kid across restart, export the correct public key, verify signatures, and include `x5c` whenever the alias/optional PEM certificate supplies a certificate. PEM without a certificate exports no `x5c` and derives RSA public values.
- Inputs: keytool-generated RSA 2048 keystores/certificates, conversion to PKCS#8 PEM through JCA, and an optional X.509 PEM/DER certificate. Keep secrets synthetic; reference them through temporary secret files or deterministic environment resolver injection.
- Edge cases: distinct JKS store/key passwords; default key password to store reference; missing file, wrong format, malformed PEM, public-only PEM, PKCS#1/encrypted PEM guidance, unknown/public-only alias, wrong password, mismatched certificate, EC material, RSA 1024. Each fails without writing/replacing JWKS or creating a new key.
- Avoid running a generation/format matrix per transport or identity source: this table supplies the focused acceptance proof.

### 4. `secretReferencesAndSigningWarningsAreSafe`

- Type: core configuration/diagnostic component cases in `SigningKeysTest.java`.
- Proves: `env:NAME` resolves only the selected process environment entry; `file:/absolute/path` loads the secret and strips only terminal newline; raw literal, absent/empty variable, unreadable/empty secret file, bad prefix and relative file reference fail. Preserve meaningful whitespace inside a password.
- Doubles/isolation: a package-private environment lookup seam supplies synthetic environment values without mutating the developer's environment. Real temporary files exercise file reads.
- Warnings: reuse a generated certificate, set the loader clock relative to its actual notAfter, and assert expired, exactly 30 days and just outside 30 days cases. Expired/near certificates warn and still sign; far certificates do not warn. No TLS behavior changes.
- Privacy: capture standard Java logger output and full error chains; assert known password marker, PEM/private encoding and private parameter strings never occur. Key-source/settings/reference `toString` and serialization cannot reveal secrets.

### 5. `issuedTokenMatchesProfileAndExactAuthorityIntersection`

- Type: core JWT component tests.
- Location: `sdks/java/core/src/test/java/ai/loomspan/sdk/ExecutionTokenIssuerTest.java`.
- Proves: verify RS256 signature with exported public JWK; typ `loomspan-execution+jwt`; exact thumbprint kid; token_use `loomspan_execution`; subject unchanged; default issuer `loomspan-app`; audiences include `loomspan-sidecar` and issuer; positive iat/exp relation; unique jti; roles is always a string array.
- Inputs: fixed clock, real core signer, subject `user-17`, granted `ROLE_ORDER_EDITOR`, `ORDER_EDITOR`, `EXTRA`, and allowlisted granted/ungranted names. Assert names remain distinct, no automatic prefix transformation and no ungranted allowlisted name appears. Include empty allowlist, empty authorities and no matching authorities.
- Edge cases: configured issuer/callback audience, additional audience additions/de-duplication; exactly 15m default; short positive/subsecond and multi-hour lifetimes; exactly one hour does not warn, greater than one hour warns; zero/negative lifetime and blank issuer/subject/audience/authority fail safely; duration arithmetic overflow has a clear validation error, no wrapped/truncated dates.
- Immutability: mutate source authority/allowlist/audience collections after construction and after issuance; decoded captured roles remain fixed. Multiple issuances have different jti. Shared issuer remains usable concurrently without cross-call identity contamination (small bounded two-identity test, not a stress suite).
- Privacy: the returned snapshot remains redacted under `toString`, attempted ordinary JSON serialization and errors; no ordinary token getter is added. Capture startup warning/issuance failure logging and scan known token/private markers.

### 6. `startupRequiresExplicitModeAndRejectsOtherModeSettings`

- Type: Boot auto-configuration component tests.
- Location: `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfigurationTest.java`.
- Proves: initial red case; registered new auto-configuration import; packaged resources do not set a signing mode; successful explicit colocated startup exports public file and creates source/core issuer/Spring adapter beans; provided startup delegates to real core loader. Missing directory/path errors include the configured path.
- Cases: missing/unknown mode; each selected mode with any nonempty settings of the other; missing provided format/key/export/alias; wrong-format options; literal password property names; unsupported reference; invalid lifetime/issuer. Use representative Boot failures and leave the full loader matrix in core.
- Defaults: 15m, issuer `loomspan-app` despite `spring.application.name=some-app`, required audiences and empty allowlist; additions bind. Capture startup WARN above one hour and certificate expiry through the actual wiring.
- Privacy: full startup error/log capture for a synthetic literal secret and malformed key; only property names/paths appear. Avoid a binder exception that prints forbidden password values.
- Update `SidecarClientAutoConfigurationTest.java` only as needed to preserve direct client wiring and add all-import behavior. A user-supplied client must not silently exempt signing startup requirements when the signing auto-configuration is loaded.

### 7. `springCapturesAuthenticatedIdentityOnceAndMatchesCustomIssuance`

- Type: Spring adapter component test plus loopback transport capture.
- Location: `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SpringExecutionTokenIssuerTest.java`.
- Proves: authenticated Spring user produces the same verified stable profile/subject/roles/audiences as trusted core identity (compare jti/iat appropriately rather than token strings). Spring reads the current Authentication once and captures name/authorities once; later context mutation cannot alter an issued snapshot. Adapter does not retain the last identity.
- Inputs: real Spring authenticated token, unauthenticated token, `AnonymousAuthenticationToken`, null authentication, and an authentication with blank name. Reject all invalid cases before signing. A recording authentication/source can count access/sign invocations without implementing authentication itself.
- Business-input separation: issue from authenticated `real-user` then submit a JSON object containing forged `sub`, `roles` and `token` fields to a local capturing server. Verify Authorization token still has the authenticated subject/allowlisted roles while the JSON body stays ordinary business data. No source accepts business fields as identity arguments.
- Isolation: clear `SecurityContextHolder` in `finally`/`@AfterEach`; no shared auth between tests, request context wrappers or application filter chain. Use JDK loopback server solely to observe an intentionally generated test token through the public snapshot/client API.

### 8. Existing regressions, boundaries and documentation

- Retain all core client, credential secrecy and transport/TLS tests and the starter SSL/client lifecycle coverage.
- Run unchanged Maven dependency rules and `sdks/java/tests/test_build_boundary.py` after new crypto/security dependencies; no Framework classes or Boot dependency enter core.
- Review `sdks/java/README.md` against tested names and examples: explicit modes, caller-supplied identity, reference forms, supported PEM scope, sole writer, operator ownership, both restart consequences, required export parent, one keytool command with `-storepass:env`, token profile/defaults/warning thresholds and exact role annotation example. No text promises later Sidecar trust/callback implementation as already delivered.

## Safe Verification Commands

Run from repository root in PowerShell. Single test selectors crossing both modules use `-Dsurefire.failIfNoSpecifiedTests=false` because the sibling module intentionally has no matching test; do not use flags that ignore actual failures.

- First red: `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am -Dtest=ExecutionTokenAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- Focused signing: `mvn -B -ntp -f sdks/java/pom.xml -pl core -Dtest=SigningKeysTest test`.
- Focused issuer: `mvn -B -ntp -f sdks/java/pom.xml -pl core -Dtest=ExecutionTokenIssuerTest test`.
- Focused Spring: `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am -Dtest=ExecutionTokenAutoConfigurationTest,SpringExecutionTokenIssuerTest -Dsurefire.failIfNoSpecifiedTests=false test` (quote the complete `-Dtest=...` argument in PowerShell if its comma is parsed as an argument list).
- Related suite: `mvn -B -ntp -f sdks/java/pom.xml test`.
- Full safe SDK suite/build: `mvn -B -ntp -f sdks/java/pom.xml clean install`.
- Dependency boundary gate: `python sdks/java/tests/test_build_boundary.py`.
- Patch hygiene: `git diff --check`.

No root Sidecar test rerun is planned while root production/build files remain unchanged: the complete independent SDK suite and boundary suite are the broadest relevant safe coverage. If implementation expands into shared/root behavior, reassess this boundary and run affected tests. Do not count downloaded library tests or framework tests as Sidecar SDK evidence.

## Optional Developer Checks

None required for this focused ticket. Later PRs supply actual app/Sidecar startup/restart, form-login, callback and provided-deployment recipes. They are not completion gates or completed evidence here.

## Exit Criteria

- [x] The initial missing-mode test fails before implementation because startup currently succeeds, and the red command/result is recorded.
- [x] All signing formats and public JWKS signature equivalence pass; unsupported/weak/invalid material never triggers fallback generation or damages a prior export.
- [x] Real replacement and deterministic access-denied/unsupported-atomic cases prove complete public-only export, path diagnostics and temporary cleanup without ACL changes or permission checks.
- [x] Every required header/claim, default, configured audience/lifetime and exact authority case has executable assertions, including unique jti and immutable snapshot behavior.
- [x] Real Spring authentication cases and trusted custom identity share one issuer path; invalid callers cannot sign; business inputs do not alter subject/roles.
- [x] Secrets, private material and tokens are absent from tested diagnostics/serialization; no public private-key/token accessor is introduced.
- [x] New/updated tests pass, complete SDK build passes, and disposable dependency boundary suite passes.
- [x] Acceptance criteria map to executable evidence through this plan and the implementation plan's traceability table; documentation is reviewed against actual tested configuration.
- [x] Tests use only local disposable keys/files/loopback servers and do not contact services or overwrite operator files.
- [x] Any failed/unrun command and its residual impact is reported accurately; optional future deployment checks are not represented as performed.

## Step Report: 2_create_plan + 3_testing_plan
STATUS: complete
ARTIFACTS:
  - ai/thoughts/plans/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md
  - ai/thoughts/plans/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance-testing.md
SUMMARY: Planned the minimal core signer/issuer and Spring startup/identity adapter with final configuration and full acceptance traceability. Specified a runnable missing-mode red test, real-key/public-signature evidence and deterministic filesystem failure tests; no implementation or tests were run.
DECISIONS:
  - Use env:/file: password references, explicit formats/aliases and unencrypted PKCS#8 PEM; warn within 30 days of signing-certificate expiry.
  - Expose Spring issueCurrent() at operation entry and delegate to one core issuer; retain explicit CredentialSnapshot transport.
  - Require atomic public-file replacement without fallback and prove platform-dependent denial through a package-private filesystem seam.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run step 4 implementation and verification using both completed plans.

## Step 4 evidence

See [implementation evidence](2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance-implementation.md)
for actual red/green commands, failures corrected during development, acceptance
mapping and final verification. The intended initial missing-mode red failure
was observed, not inferred from compilation. Full SDK build currently passes
36 tests. The boundary suite passed all three test groups (eleven disposable reactor builds); final patch hygiene passed.
