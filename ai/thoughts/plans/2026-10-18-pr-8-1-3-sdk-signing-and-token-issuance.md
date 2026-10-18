# SDK signing and execution-token issuance Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md`
- Research: `ai/thoughts/research/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md`
- Outcome: an explicit startup signer exports public trust and one core issuer returns `CredentialSnapshot` for authenticated application identity; Spring captures its existing authentication at issuance entry.
- Pipeline mode, selected profile `full`, approved by the developer. This plan makes ordinary implementation decisions; no product decision remains open.

**Development policy: destructively replace superseded contracts; no compatibility shims, legacy adapters, parallel legacy APIs or migration machinery.** Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). No existing signing state needs a development-data reset. Do not delete deployed data.

## Current State

- `sdks/java/core/src/main/java/ai/loomspan/sdk/CredentialSnapshot.java` owns immutable credential transport and redacted diagnostics. `SidecarClient` accepts it explicitly on every operation; its HTTP and error behavior need no change.
- `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java` creates the blocking client; `SidecarClientProperties` owns connection settings. Neither contains signing or identity code. The imports resource currently lists only that auto-configuration.
- `sdks/java/pom.xml` is an independent Java 21 two-module reactor. Core has JUnit 6 and no Spring dependencies. The starter uses Boot 4.1.0 with Boot test support. Maven Enforcer bans Framework/Sidecar artifacts, and bans Boot in core.
- Existing core client tests exercise loopback HTTP/TLS and credential secrecy. `SidecarClientAutoConfigurationTest` uses `ApplicationContextRunner` and JDK keytool with temporary directories. There are no signing fixtures or issuance tests.
- Root Sidecar JWT receiver behavior is superseded by later PR 8.1.4; it is not changed or used as evidence for the new token profile here.

## Desired End State

Boot startup requires `loomspan-sdk.signing.mode=colocated|provided`, rejects cross-mode settings, validates all issuance configuration, loads/generates exactly one RSA signer, and successfully exports its public JWKS before exposing the issuer. Core offers the same initialization and issuance without Spring. The private key is not exposed by the key-source interface. Spring issues only for authenticated, nonanonymous identity captured once, while trusted non-Spring code explicitly supplies an authenticated identity snapshot. Every result uses the existing redacted `CredentialSnapshot`.

Tokens use RS256, RFC 7638 public thumbprint kid, execution typ/purpose, fixed issuer default, Sidecar plus issuer audiences, positive configured lifetime, unique jti and exact allowlisted authority intersection. Startup warnings cover long lifetime and expired/near-expiry signing certificates. Documentation describes actual supported configuration and operational consequences.

## Scope

### In scope

- Core RSA key-source interface, colocated/provided loading, public-only atomic export, core identity/issuer API, startup validation, sanitized diagnostics and focused tests.
- Starter properties, startup wiring, Spring authentication capture and tests; update existing Boot tests for the newly required mode.
- SDK README for both core and Spring use, password references, formats, token profile and deployment responsibilities.

### Out of scope

- Sidecar trust, callback authentication/security chains, sample deployments and composed form-login flows assigned to PRs 8.1.4–8.1.7.
- Client overload wrappers, async/context propagation, key watchers, replica coordination, permission inspection or mutation, cloud adapters, key hosting and credential refresh.
- Framework changes, root dependency/version changes and release work.

## Active Project Guardrails

Apply the design lens's simplicity, trusted identity, exact authority and explicit signing ownership rules. Core remains Spring/Boot/Framework/Sidecar independent; the existing Maven boundary is retained. Application authentication supplies identity outside business inputs. Signing files and callbacks are separate from TLS ownership. Colocated private keys are ephemeral; provided failure never generates a key. Operators own directory permissions and the single colocated writer. Existing management and transport semantics remain unchanged.

## Impact and Risk Analysis

- A key mismatch, private JWK serialization or accidental non-atomic fallback changes the trust boundary. Build the public JWK explicitly, verify supplied public/private correspondence, and require atomic replacement without a fallback.
- Cross-mode and password configuration mistakes must fail clearly without exposing values. Validate settings before key loading; report property names and configured paths only. Do not retain third-party parse exceptions whose messages might echo secrets or PEM content in the public exception chain.
- Writable checks vary across Windows/Linux and elevated users. Attempt the actual temporary write and atomic move, report failure with the destination path, and test denied/unsupported operations at a small package-private filesystem seam rather than probing permissions or changing ACLs.
- Spring Security must supply identity without enabling or replacing application security chains. Depend only on `spring-security-core` and use `SecurityContextHolder` once inside the Spring adapter; core has no security context dependency.
- JWT NumericDate has finite representation. Validate configured positive durations and reject representational overflow clearly rather than silently truncating lifetime or inventing a policy maximum. Subsecond positive lifetimes must retain precision in the selected JWT API/serialization.

## Implementation Approach

Use Nimbus JOSE + JWT in core for JWK thumbprints, JOSE/JWT serialization and signing integration; explicitly pin a resolved maintained version in `core/pom.xml` (10.10 is available in the local Maven cache). Use JCA for RSA generation, signature and standard keystore/PKCS#8/X.509 parsing; no general crypto-provider dependency is needed for the chosen PEM scope. The issuer adapts the narrow source to Nimbus's RS256 signing interface instead of making consumers depend on private keys.

Planned core types live directly under `sdks/java/core/src/main/java/ai/loomspan/sdk/` to follow the existing small package: `SigningKeySource`, `SigningKeys`, `SigningSettings`, `SecretReference`, `AuthenticatedIdentity`, `IssuanceSettings`, `ExecutionTokenIssuer`. Related private helpers may be nested/package-private rather than multiplying public extension points. `SigningKeySource` exposes only RS256 byte signing, RSA public key and kid; optional certificate export belongs to loader metadata, not a private-key accessor. The core factory returns a fully initialized source only after export succeeds. Core factory startup warnings use a standard Java logger, captured by focused tests; no logging framework is required in core.

Final properties (no earlier aliases):

| Property | Contract |
| --- | --- |
| `loomspan-sdk.signing.mode` | Required `colocated` or `provided`, no default |
| `loomspan-sdk.signing.colocated.key-directory` | Existing directory; required only for colocated |
| `loomspan-sdk.signing.provided.format` | Required `PKCS12`, `JKS` or `PEM` |
| `loomspan-sdk.signing.provided.key-location` | Required filesystem path |
| `loomspan-sdk.signing.provided.key-alias` | Required for PKCS12/JKS, forbidden for PEM |
| `loomspan-sdk.signing.provided.keystore-password-reference` | Keystore password reference for PKCS12/JKS, forbidden for PEM |
| `loomspan-sdk.signing.provided.key-password-reference` | Optional key password reference; defaults to store reference for keystores; forbidden for unencrypted PEM |
| `loomspan-sdk.signing.provided.certificate-location` | Optional X.509 PEM/DER certificate for PEM; forbidden for keystores, whose certificate chain is loaded with the alias |
| `loomspan-sdk.signing.provided.jwks-export` | Required destination file; its parent must exist |
| `loomspan-sdk.issuance.issuer` | Nonblank exact value, default `loomspan-app`, independent of application name |
| `loomspan-sdk.issuance.lifetime` | Positive duration, default 15 minutes; WARN above one hour |
| `loomspan-sdk.issuance.additional-audiences` | Nonblank strings added to `loomspan-sidecar` and issuer, de-duplicated |
| `loomspan-sdk.issuance.roles` | Exact nonblank allowlist entries; default empty |

Password references have only `env:VARIABLE_NAME` and `file:/absolute/path` forms. The former resolves from the process environment, the latter reads an operator-mounted UTF-8 secret file and strips a terminal newline (including CRLF), preserving other password characters. Missing/blank reference targets fail without echoing names' resolved values. Core accepts references, not raw password config; the starter explicitly rejects literal password settings (including `keystore-password`/`key-password`) without binding/logging their values. Password arrays are cleared after load where feasible. No custom vault integrations or Spring placeholder-based literal password property is introduced.

PEM support is explicitly unencrypted PKCS#8 `PRIVATE KEY`, with optional matching X.509 certificate. An RSA CRT key supplies its public modulus/exponent without a certificate. PKCS#1 and encrypted PEM fail with a format guidance message; users can choose an encrypted PKCS12/JKS instead. This is an initial documented encoding contract, not a legacy compatibility path. Keystore alias must identify a private key and usable certificate/public key. Reject missing aliases, public-only entries, invalid passwords, unsupported keys, weak RSA and certificate/key mismatch. Warn when signing certificate expiry is at or before `now + 30 days`, including already expired certificates; expiry does not block signing or relax TLS.

`AuthenticatedIdentity` is an immutable subject and defensive-copy authority snapshot constructed by trusted application authentication code; it is not a request/body deserialization contract and has no business-input parameters. `ExecutionTokenIssuer.issue(identity)` is the sole claim-construction path. `SpringExecutionTokenIssuer.issueCurrent()` obtains the current `Authentication` once, rejects null, unauthenticated and anonymous authentication (using Spring's standard trust resolver), captures name and authorities once, then delegates. It does not issue from unverified claims or take a caller-selected subject. The returned credential is passed explicitly to `SidecarClient` as today. Documentation places `issueCurrent()` at the beginning of the application operation, before business processing; no client wrapper or thread-context machinery is needed.

## Phase 1: Core signing and public export

### Changes

- [x] `sdks/java/core/pom.xml` — pin Nimbus dependency; retain existing dependency bans.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeySource.java` — narrow `signRs256`, `publicKey`, `kid` contract; no private material accessor.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/SigningSettings.java` and `SecretReference.java` — immutable mode settings and safe secret-reference resolution; reject cross-mode/format options before side effects.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeys.java` — factory validates settings, generates 2048-bit colocated RSA or loads provided material once, validates size/type/correspondence, computes thumbprint and certificate warnings. Export explicit public RSA JWK (`use=sig`, `alg=RS256`, optional `x5c`) in one-key JWKS. Write a sibling temporary public file, close it, then `Files.move` with `ATOMIC_MOVE` and `REPLACE_EXISTING`; remove temporary file on failure. Do not create parent directories, inspect permissions, persist private state or fall back to ordinary move.
- [x] `sdks/java/core/src/test/java/ai/loomspan/sdk/SigningKeysTest.java` and `SigningTestMaterial.java` — JUnit/keytool temporary fixtures and deterministic write/move failure coverage. Failures preserve existing destination; success exports no private members or private bytes.

### Automated verification

- [x] `mvn -B -ntp -f sdks/java/pom.xml -pl core -Dtest=SigningKeysTest test` — formats, rejection, new/reused kids, atomic options/failure cleanup, warnings and secret references pass.

### Optional developer checks

- None. ACL variations are deterministic component coverage; no manual gate.

## Phase 2: Core issuance and identity snapshots

### Changes

- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/AuthenticatedIdentity.java` — nonblank subject, immutable exact authorities, no token or business object fields.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/IssuanceSettings.java` — defaults, exact allowlist and audience validation, positive duration validation and long-lifetime warning at issuer startup.
- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionTokenIssuer.java` — one issuance method, fixed clock seam for tests, unique jti, profile claims and unchanged allowlist intersection; return `CredentialSnapshot` without adding token getters. No logging token/signature/private state.
- [x] `sdks/java/core/src/test/java/ai/loomspan/sdk/ExecutionTokenIssuerTest.java` — decode/verify using public JWKS, inspect headers and claims via package-private credential transport available to same-package tests, fixed-clock lifetime and identity mutation checks.

### Automated verification

- [x] `mvn -B -ntp -f sdks/java/pom.xml -pl core test` — issuance/signing plus all client, credential and TLS regressions pass.

### Optional developer checks

- None.

## Phase 3: Starter startup and Spring identity

### Changes

- [x] `sdks/java/spring-boot-starter/pom.xml` — add Boot-managed `spring-security-core`; do not add a security starter or application filter chain.
- [x] `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SigningProperties.java` and `IssuanceProperties.java` — separate prefix bindings for the final properties; no built-in mode or secret-value diagnostics.
- [x] `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfiguration.java` — validate configuration at startup, initialize source/issuer through core factories, and wire Spring adapter. Missing mode error names `loomspan-sdk.signing.mode`, `colocated`, `provided`, and the README colocated quickstart. Validation/export failure aborts startup.
- [x] `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SpringExecutionTokenIssuer.java` — `issueCurrent()` captures authentication once, uses standard anonymous detection, delegates to the same core issuer, and holds no authentication between calls.
- [x] `sdks/java/spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — register new configuration; packaged resources set no mode.
- [x] `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfigurationTest.java` and `SpringExecutionTokenIssuerTest.java` — startup configuration and identity trust boundaries, with current context cleared after each test.
- [x] `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SidecarClientAutoConfigurationTest.java` — preserve isolated client wiring tests; add representative import-discovery/full auto-configuration evidence with explicit signing config where all imports are exercised. Existing direct-import client-only tests need no artificial signer if the new configuration is independently registered.

### Automated verification

- [x] `mvn -B -ntp -f sdks/java/pom.xml test` — both modules pass; full auto-configuration fails without a mode and succeeds with explicit valid config.

### Optional developer checks

- None. Real form-login to skill flow is a later ticket's composed evidence.

## Phase 4: Documentation and complete verification

### Changes

- [x] `sdks/java/README.md` — core and Spring issuance examples and final property table; explicit colocated quickstart with operator-created private directory; one writer, restart invalidation, provided restart requirements, file trust relationship and no hidden mode; one keytool command using environment password reference (`-storepass:env`) rather than a literal secret; PEM encoding guidance; defaults/token profile, role intersection and `hasRole('ORDER_EDITOR')` / `hasAuthority('ROLE_ORDER_EDITOR')` exact-name example; warning thresholds and reference resolution.
- [x] Record ticket-scoped implementation evidence in the pipeline's required implementation report/ticket as prescribed by step 4. Do not claim the later deployment scenarios or framework tests as implemented evidence.

### Automated verification

- [x] `mvn -B -ntp -f sdks/java/pom.xml clean install` — complete SDK compilation, tests, packaging and dependency enforcement.
- [x] `python sdks/java/tests/test_build_boundary.py` — clean control and direct/transitive forbidden-dependency variants pass in disposable reactors.
- [x] `git diff --check` — no whitespace defects.

### Optional developer checks

- None required; real deployment/key rotation recipes remain assigned to later tickets.

## Test Strategy

Use component tests for the format/invalid-config matrix, a real local filesystem for normal exports and keytool-generated per-run keystore/certificate material, and a tiny package-private file-operation seam only for deterministic denied/unsupported atomic moves. No permission mutation or brittle platform-specific writable assertions. Use Nimbus public signature verification and parsed claims for issuance rather than comparing random token strings. Use Boot context runner for configuration and Spring Security's real authentication objects for identity capture. Preserve existing client secrecy/transport and boundary tests. Detailed cases, safe commands and exit criteria are in the companion testing plan.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Missing mode actionable; packaged starter no mode | `ExecutionTokenAutoConfiguration`, imports, README | `ExecutionTokenAutoConfigurationTest.missingModeNamesBothOptionsAndQuickstart`, packaged-resource assertion |
| Other-mode settings fail | `SigningSettings`, property mapping | parameterized core and Boot mixed-mode cases |
| Colocated directory required/existing/writable; private key RAM-only; atomic export; restart kid | `SigningKeys` colocated/export paths | real temporary export, missing path, injected denied-write/atomic failure, directory inventory/private members, two initializations |
| Provided PKCS12/JKS/PEM, x5c, invalid/weak/non-RSA rejection, no generation | `SigningKeys` provided loaders | all three formats verified against exported public key; negative table and unchanged destination |
| Secret/environment references only; certificate warnings | `SecretReference`, loader warning | env/file success, literal/missing-ref failure and safe diagnostics; fixed-clock expired/near/far cert cases |
| Required profile and cryptographic signature | `ExecutionTokenIssuer` | Nimbus signature verification, thumbprint and every header/claim assertion |
| Issuer/audience defaults plus additions | `IssuanceSettings` | default/configured issuer, application-name independence, additions/de-duplication |
| 15m default, any positive lifetime, >1h warning | `IssuanceSettings`, issuer | fixed clock, short/subsecond/long lifetimes, zero/negative rejection and warning boundary |
| Equivalent Spring/custom identity; deny anonymous/unauthenticated; business input cannot select identity | Spring adapter delegates once to core | public-claim equivalence excluding iat/jti randomness, invalid auth cases, capture once and hostile body separation |
| Exact allowlisted authority intersection, empty allowed | immutable identity/settings and issuer | prefixed/unprefixed distinct, granted/ungranted allowlist, empty and mutation tests |
| No private material/tokens in diagnostics | source internals, snapshot, sanitized failures | captured logger/error stack and object serialization assertions plus existing credential tests |
| SDK documentation complete | README | review final examples/property names against tested settings and token assertions |

## Risks and Rollback/Recovery

No schema/data migration or development reset is required. On invalid configuration or I/O the application fails startup and operators correct configuration/material/permissions; the SDK creates no replacement provided key. Unsupported atomic replacement is a startup failure, not a portability fallback. Operators restart provided applications to pick up key changes; colocated restart deliberately replaces trust and invalidates old tokens as receivers adopt it. A preexisting export remains complete on failed publication. Rollback reverts this ticket's source/config documentation; it must not delete operator key material or rewrite deployed trust. Record any implementation-driven deviation in this plan and its tests before handoff.

## References

- Ticket and research paths above; `AGENTS.md`; `ai/commands/shared/automation-protocol.md`.
- `ai/thoughts/phases/2026-10-15-sdk-v1-signing-trust-reference.md`.
- `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-reference.md`.
- `ai/thoughts/phases/2026-10-15-sdk-v1-acceptance-matrix.md`.
- Existing SDK POMs, README, `CredentialSnapshot`, `SidecarClient`, Boot auto-configuration/tests, and `sdks/java/tests/test_build_boundary.py`.

## Step 4 implementation notes

Implemented the planned core and starter APIs and SDK documentation. Evidence and
acceptance mapping are in
[implementation evidence](2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance-implementation.md).
The complete reactor includes the planned core-only regression invocation;
`mvn -B -ntp -f sdks/java/pom.xml clean install` passed 36 tests (24 core,
12 starter). Existing isolated client auto-configuration tests needed no edits;
new combined startup/import tests supply signing evidence. Decimal NumericDate
claims preserve subsecond lifetimes. JCA file-based keystore detection validates
the configured format rather than silently accepting JKS/PKCS12 autodetection.
These are routine implementation adaptations, with no change to scope or policy.
The boundary gate passed all three test groups (eleven disposable reactor builds); final patch hygiene also passed.
