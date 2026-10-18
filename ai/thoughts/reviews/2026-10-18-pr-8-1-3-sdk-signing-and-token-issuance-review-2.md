# PR 8.1.3 Code Review — Cycle 2

## Scope and Repository State

Fresh independent Step 5 context in pipeline mode, selected profile `full`, approved by the developer. Read the ticket, research, implementation plan, testing plan, repository instructions, design lens and review/protocol commands. Did not read previous review artifacts or rely on their findings or reported verification.

Reconstructed the current change from `git status --short`, `git diff --stat`, `git diff -- sdks/java`, `git diff --cached --stat` and `git ls-files --others --exclude-standard sdks/java`. Branch is `main`, HEAD `05518e7`; the supplied clean initial baseline and current Git inventory establish the uncommitted ticket scope. No staged changes exist. Scope includes four tracked SDK modifications and sixteen untracked SDK Java production/test files, plus the supplied planning artifacts. Root Sidecar production/configuration, its framework dependency and release versions are unchanged.

Read all new production and test files and the modified SDK README/POMs/imports, then followed credential consumption and existing client/security/SSL tests beyond the diff. Reviewed correctness before checking plan conformance. Considered authentication, secret handling, public/private key correspondence, persisted file publication, startup/restart, concurrency, diagnostics, dependencies and operator guidance. Completed the independent review before any artifact writing; no implementation artifact was edited in this context.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. Only this review document was written.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Explicit mode; actionable missing-mode error; no packaged default | `SigningSettings` rejects absent/unknown mode; starter resource contains only auto-configuration imports | `ExecutionTokenAutoConfigurationTest.missingModeNamesBothOptionsAndQuickstart`; source/resource inspection | implemented |
| Reject other-mode settings | `SigningProperties.settings` preserves presence; `SigningSettings` rejects cross-mode/format options | Core mixed-mode/configuration cases; starter invalid/mixed-settings matrix | implemented |
| Colocated existing directory, ephemeral RSA 2048, public-only atomic export and new restart kid | `SigningKeys.initialize` generates only in colocated branch; explicit public RSA JWK; sibling temporary write followed by atomic replacement; no permissions probing/mutation or directory creation | `colocatedExportsOnlyPublicKeyAndChangesKidOnRestart`, `directoryErrorsAndMixedModesAreActionable`, `atomicExportFailsClosedAndPreservesPriorFile` | implemented |
| Provided PKCS12/JKS/PEM and optional certificate/x5c; reject missing/invalid/weak/non-RSA/mismatch without fallback | Explicit loader branches, format validation, modulus checks and challenge signature verification precede export | `providedFormatsLoadTheSuppliedKeyAndExportCertificates`, alias/password/format cases, `invalidWeakNonRsaAndMismatchedMaterialNeverReplacesExport`, starter PEM integration | implemented |
| Protect operator signing input from export overlap | `validateExportDestination` rejects normalized same-path and existing same-file aliases for key/certificate/secret files | `publicExportNeverOverwritesProvidedInputs` preserves original bytes | implemented |
| Password references only and certificate warnings | `SecretReference` resolves env/absolute secret file; starter forbids literal password properties; loader warns at expiry or within 30 days | `secretReferencesAndSigningWarningsAreSafe`; distinct-password keystore case; starter captured literal-password diagnostic test | implemented |
| Complete token profile and valid RS256/thumbprint kid | One `ExecutionTokenIssuer.issue` builds headers/claims and delegates signing through narrow source | `issuedTokenMatchesProfileAndExactAuthorityIntersection` verifies signature and every required field; signing test equates exported public key | implemented |
| Default issuer/audiences; audience additions; issuer independent of app name | Immutable `IssuanceSettings`, fixed starter defaults; issuer de-duplicates mandatory/added audiences | `defaultsAdditionalAudiencesAndEmptyRoles`; starter defaults/configuration binding with `spring.application.name` | implemented |
| 15m default, positive lifetimes including subsecond, warn above 1h, clear representational overflow | Decimal NumericDate construction; duration validation; expiry overflow rejection; startup warning | Fixed-clock profile assertion; `positiveDurationsKeepPrecisionAndWarnOnlyAboveOneHour`; starter invalid lifetime and configured 2h startup | implemented |
| Spring/custom equivalence and deny invalid callers; capture identity once outside business input | `SpringExecutionTokenIssuer.issueCurrent` reads existing authentication/name/authorities once, rejects anonymous/unauthenticated and delegates to core | Real Spring authentication cases; access counts; verified loopback transport token versus hostile JSON fields and trusted custom identity | implemented |
| Exact held/allowlisted authorities and empty roles; snapshot immutability | Defensive immutable identity/settings collections; exact intersection without prefix manipulation | Prefixed/unprefixed and ungranted role assertions; empty allowlist/authorities; collection mutation; bounded concurrent distinct identities | implemented |
| No private material/tokens in infrastructure logs/diagnostics | Private source owns private key with no accessor; explicit public JWK; sanitized exceptions without provider causes; credential remains redacted | Source/credential serialization and toString tests, signing-error cause suppression, captured certificate/startup diagnostics, existing credential transport secrecy regression | implemented |
| Complete operational SDK documentation | README includes both modes, sole writer/operator permissions, restart consequences, env-backed keytool command, references/PEM scope, profile/defaults and exact `hasRole`/`hasAuthority` example | Compared README examples/property names against production settings and passing component tests | implemented |
| Independent SDK dependency boundary and retained client/TLS behavior | Core adds Nimbus only; starter adds Spring Security core without a security chain; existing enforcer rules retained | Complete SDK build and disposable boundary suite; unchanged client/TLS and Boot SSL/lifecycle tests | implemented |

Executable evidence establishes every acceptance criterion. Representative component cases cover the high-risk paths without introducing a deployment matrix. Existing client-only auto-configuration tests remain isolated; new combined startup/import cases establish signer startup behavior. Public key export and provided input validation stay startup-only. There is no new Framework extension, compatibility shim, async wrapper, signer watcher or data migration.

## Active Project Guardrails

- Applied the design lens: narrow signer/issuer APIs, one claim-construction implementation, public-only atomic export and explicit operator ownership. No unnecessary persistence or compatibility layer.
- Identity comes from trusted authentication, never business JSON; exact authority names remain unchanged and snapshots are immutable. Spring Security supplies authentication without SDK filter-chain replacement.
- SDK has no Framework/Sidecar dependency or unsupported internal import; inherited Maven enforcement remains active. Root Framework pin and release state are untouched. No Framework tests are treated as SDK implementation evidence.
- Both signing modes preserve their intended restart semantics. Provided failures do not generate replacements; failed publication preserves the previous file; overlapping provided input/export paths fail before replacement.
- All verification uses disposable local material and loopback servers. No deployed data or operator key files were modified. No data reset is necessary.

## Open Questions and Assumptions

None affecting correctness. As documented, the application supplies a stable authenticated subject and operators enforce one colocated writer per key directory.

## Verification Results

- PASS — `mvn -B -ntp -f sdks/java/pom.xml clean install` — complete reactor, compilation, packaging, installation and Maven boundary enforcement; 37 tests (25 core, 12 starter), zero failures/errors/skips. This includes focused signing, issuance and Spring tests plus retained client/TLS regressions.
- PASS — `python sdks/java/tests/test_build_boundary.py` — three groups in 148.413 seconds, covering the clean control and ten direct/transitive forbidden-dependency variants (eleven disposable reactor builds).
- PASS — `git diff --check` — no whitespace defects; ordinary checkout LF/CRLF notices only.
- PASS — `rg -n -g '*.txt' 'Tests run:' sdks/java/core/target/surefire-reports sdks/java/spring-boot-starter/target/surefire-reports` — independently confirmed per-class counts and zero failures/errors/skips.
- A report-inspection attempt, `rg -n 'Tests run:' sdks/java/core/target/surefire-reports/*.txt sdks/java/spring-boot-starter/target/surefire-reports/*.txt`, failed because Windows rg does not expand path wildcards. Corrected with the passing `-g` command above; no implementation/test failure was hidden.

## Residual Risks and Optional Developer Checks

Actual form-login/composed Sidecar startup, receiver reload, callbacks and provided deployment recipes belong to later PRs 8.1.4–8.1.7. They are not claimed as performed here. The current focused scope is covered by local component/integration tests. ACL denial and unsupported atomic moves use the intentional package-private NIO seam; normal atomic replacement uses the actual local filesystem. Symbolic-link same-file checks run only where the OS permits link creation; normalized-path protection is unconditional. No optional developer completion gate.

## Disposition

`clean` — no actionable findings, no implementation-artifact edits and sufficient independent executable verification. Completed scope reconstruction includes staged, unstaged and untracked changes; traced connected behavior; reviewed security, lifecycle, persistence, concurrency, integration, operations, documentation and test quality; verified every acceptance criterion without relying on past review evidence.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance-review-2.md
SUMMARY: Fresh independent review found no actionable findings. The complete SDK build passed 37 tests and all disposable dependency-boundary checks passed; this context changed only its review document.
DECISIONS:
  - Retain the approved full profile; current security and token contracts require its assurance.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `mvn -B -ntp -f sdks/java/pom.xml clean install`
  - PASS — `python sdks/java/tests/test_build_boundary.py`
  - PASS — `git diff --check`
OPTIONAL_DEVELOPER_CHECKS:
  - none
REVIEW_RESULT: clean
NEXT: Complete the pipeline and report the reviewed implementation and verification evidence.
