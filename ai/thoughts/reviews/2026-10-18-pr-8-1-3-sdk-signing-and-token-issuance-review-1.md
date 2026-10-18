# SDK signing and token issuance Code Review — Cycle 1

## Scope and Repository State

Pipeline mode, selected profile `full`, developer approved. Independently reviewed
HEAD `05518e790e6e5db8ff0a3dc670beaeabfd0f3048` and the complete unstaged and
untracked ticket change. Baseline was clean according to the supplied scope;
Git inventory confirms production changes are confined to the independent Java
SDK. No staged change or unrelated developer modification was present.

Read the ticket, research, implementation plan, testing plan, command/protocol,
AGENTS.md and design lens. Reconstructed all new production and test files,
modified POMs, auto-configuration registration and README, including connected
credential/client transport and dependency-boundary implementation. No previous
review document was used. Completed the independent review before editing.

## Findings

### [P2] Reject public-export paths that replace provided signing inputs

- Location: `sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeys.java:30`
  (guard added here; original publication path was unguarded).
- Scenario: configure `provided.jwks-export` as the same file as
  `provided.key-location`. The loader reads the operator key successfully and
  then atomically replaces it with public JSON. Certificate and file-based
  password inputs are exposed to the same collision.
- Impact: startup appears successful but destroys operator material; a later
  restart cannot load that signing key. Public export must not own private input
  files. Normalized/aliased paths can produce the same collision.
- Evidence: the new `publicExportNeverOverwritesProvidedInputs` regression failed
  before the fix because initialization returned successfully with the JKS file
  selected as the export. Existing tests validated malformed material and
  export-I/O failure, but did not exercise valid material with overlapping paths.
- Fix: reject normalized or filesystem-identical paths for key, certificate and
  referenced password files before loading or publication. Document the rule and
  assert that original bytes remain unchanged.

No unresolved actionable findings remain after fixes and complete rereview.

## Findings Resolved in This Context

Added a package-private secret-file path lookup, provided-input destination
validation, preservation regression and SDK documentation. Validation does not
inspect or change permissions and adds no watcher, compatibility layer or public
extension contract. The regression passes after the fix. Review disposition must
be `fixes-applied`; another fresh review is required.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Explicit required mode, actionable startup failure, no packaged default | SigningSettings, ExecutionTokenAutoConfiguration; resources contain only auto-configuration imports | missingModeNamesBothOptionsAndQuickstart; inspected resource inventory | implemented |
| Reject settings belonging to other mode | SigningSettings and SigningProperties.settings | directoryErrorsAndMixedModesAreActionable; invalidAndMixedSettingsFailWithoutEchoingLiteralPasswords | implemented |
| Colocated existing/writable directory, RAM-only private key, atomic export, restart kid | SigningKeys initialization/publish and private LocalSource | colocatedExportsOnlyPublicKeyAndChangesKidOnRestart; directory errors; injected denied writes/moves and unsupported atomic replacement | implemented |
| Provided PKCS12/JKS/PEM, certificate x5c, reject invalid/weak/non-RSA, no fallback | SigningKeys provided branches and key proof | providedFormatsLoadTheSuppliedKeyAndExportCertificates; aliases/distinct passwords; invalid/weak/non-RSA/mismatched cases | implemented |
| Secret/environment references only; certificate warnings | SecretReference, explicit starter literal-property rejection, 30-day certificate check | secretReferencesAndSigningWarningsAreSafe; captured startup literal-password diagnostic checks | implemented |
| Signature, kid, typ, token purpose and complete claims | ExecutionTokenIssuer uses narrow signer and explicit claims | issuedTokenMatchesProfileAndExactAuthorityIntersection independently verifies RSA signature and claims | implemented |
| Defaults and additional audiences | IssuanceSettings defaults and issuer audience union | default/additional-audience tests; Boot default issuer independent of application name | implemented |
| Positive lifetime, 15m default, >1h warning | IssuanceSettings and issuer time arithmetic | fixed-clock durations, raw fractional NumericDate precision, overflow/zero/negative cases and captured warning boundary | implemented |
| Equivalent custom/Spring identity, deny invalid callers, business input separation | immutable AuthenticatedIdentity, Spring issueCurrent delegates once | real authenticated/anonymous/unauthenticated identities; capture counts; loopback Authorization versus forged business fields | implemented |
| Exact authority intersection, empty roles and snapshots | defensive copies and exact filter | prefixed/unprefixed authority assertions, ungranted exclusion, source mutation, empty roles and concurrent distinct identities | implemented |
| No private material/tokens in infrastructure diagnostics | sanitized key/signing errors, private source fields, existing CredentialSnapshot | synthetic secret/error/log assertions; source/snapshot serialization refusal or redaction; existing client secrecy regressions | implemented |
| Documentation and ownership consequences | SDK README | compared configuration and examples to implemented bindings and executable assertions | implemented |
| Preserve provided operator inputs | startup collision guard | new preservation regression, normalized path and link case where available | implemented |

The implementation follows the planned PKCS#8 scope, env/file references,
30-day certificate warning, startup-only provided key load and explicit snapshot
transport. The new input-protection rule is an in-scope operational correctness
fix. No required development-data reset exists.

## Active Project Guardrails

- Development policy: no obsolete-contract compatibility machinery; no deployed
  data action, migration or reset.
- Simplicity: one core claim path, one narrow signer, standard JCA/NIO and Spring
  authentication, no key hot reload or custom security chain.
- Identity and roles: trusted authentication captured once; exact allowlist
  intersection; business fields remain separate; no role-prefix mutation.
- Signing ownership: public-only atomic export, ephemeral colocated keys,
  operator-owned provided material, no fallback key generation or permission
  probing/mutation.
- SDK boundaries: no Framework/Sidecar Java imports or Boot runtime in core;
  Maven Enforcer runs in the actual build. Root Framework dependency/version and
  Sidecar receiver remain unchanged.
- Each implementation unit has its own SDK tests and documentation; no Framework
  test result is treated as Sidecar integration evidence.

## Open Questions and Assumptions

None affecting correctness. Operators own paths and the sole-writer deployment;
this does not claim protection from an adversarial concurrent filesystem writer.

## Verification Results

- PASS — `mvn -B -ntp -f sdks/java/pom.xml -pl core -Dtest=SigningKeysTest test`
  — original seven signing tests independently passed before review edits.
- FAIL (expected regression red) — `mvn -B -ntp -f sdks/java/pom.xml -pl core '-Dtest=SigningKeysTest#publicExportNeverOverwritesProvidedInputs' test`
  — before the guard, expected rejection did not occur for the JKS input.
- PASS — `mvn -B -ntp -f sdks/java/pom.xml -pl core '-Dtest=SigningKeysTest#publicExportNeverOverwritesProvidedInputs' test`
  — regression passed after the guard.
- PASS — `mvn -B -ntp -f sdks/java/pom.xml clean install`
  — 25 core and 12 starter tests; zero failures/errors/skips; both artifacts
  packaged and installed; dependency enforcement passed.
- PASS — `python sdks/java/tests/test_build_boundary.py` — all three test groups
  passed in 153.179 seconds (eleven disposable reactor builds), including clean
  control and direct/transitive Boot/Framework/Sidecar dependency violations.
- PASS — `git diff --check` — no whitespace errors.
- NOT RUN — root Sidecar Maven suite — no root production/build behavior changed;
  it does not exercise the independent SDK. Full SDK reactor is the relevant gate.

## Residual Risks and Optional Developer Checks

No optional developer check is required for this ticket. Actual app/Sidecar
restart, form-login, callback and provided deployment recipes remain later
tickets, as explicitly scoped. Symlink preservation assertion executes only
where the local OS permits test symlink creation; direct and normalized input
preservation assertions always execute. Standard existing SLF4J/deprecation
messages do not affect test outcomes. No live service or deployed file was used.

## Disposition

`fixes-applied` — all findings resolved and relevant verification passed; fresh
Step 5 context required.
