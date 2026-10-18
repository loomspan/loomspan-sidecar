# PR 8.1.3: SDK signing modes and execution-token issuance

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

An application using the SDK can issue execution JWTs for its own authenticated
users, signed with either an ephemeral colocated key or an operator-provided RSA
key. The SDK also publishes the matching public JWKS for Sidecar to trust.
Tokens carry only the exact allowlisted authorities the user actually has.
Identity never comes from business input. This lets applications reuse their
existing login without Sidecar issuing tokens.

## Requirements

### Signing modes (loomspan-sdk-core, wired by the starter)

- `loomspan-sdk.signing.mode` is required and has no built-in value. The values
  are `colocated` and `provided`.
  - A missing mode fails startup with a message that names the property, both
    options and the colocated quickstart.
  - Configuration packaged in the SDK starter must not set a mode.
- Settings that belong to the other mode fail startup.
- `colocated`:
  - Generates a fresh 2048-bit RSA key in memory at startup. The private key is
    never written to disk.
  - Writes only the public `<key-directory>/jwks.json`, atomically (temporary
    file plus atomic replacement).
  - `colocated.key-directory` is required and has no built-in value.
  - The directory must already exist and be writable. A missing directory
    setting, a missing directory or an unwritable directory fails startup with
    a message naming the path.
  - The SDK neither creates the directory nor changes permissions, and adds no
    permission checks.
  - Each restart generates a new key, so previously issued tokens stop
    validating as receivers adopt the new public key. No persistent signer
    state.
- `provided`:
  - Loads an operator RSA key from PKCS12, JKS or PEM, with an optional
    certificate.
  - Keystore and key passwords come from secret or environment references,
    never literal configuration values.
  - Missing or invalid material fails startup and never triggers replacement key
    generation.
  - Exports the public `jwks.json` at startup to the configured export path,
    including `x5c` when a certificate is present.
  - Warns on expired or near-expiry signing certificates. This does not affect
    TLS validation.
  - The private key loads at startup only. There is no watcher or hot reload;
    replacing the key requires an application restart.
- Both modes:
  - RSA with RS256 only. Reject other key types and keys under 2048 bits at
    startup.
  - `kid` is the public key's RFC 7638 thumbprint.
  - Signing goes through one narrow key-source interface: sign RS256 bytes and
    supply the public key and kid. Consumers never need private key bytes. No
    cloud or provider key adapters.
- Never log private material or tokens.
- The exported JWKS is what Sidecar's trust file contains. In PR 8.1.4, Sidecar
  reads JWT verification keys only from its configured JWKS file. This holds in
  both file and database application ConfigurationMode; database mode does not
  move JWT trust into the database.

### Issuance

- One issuance implementation with two identity sources:
  - Spring: reads an authenticated, non-anonymous principal's stable subject and
    authorities once, at operation entry.
  - Trusted custom identity: the non-Spring application supplies an
    authenticated subject and authorities.
- Never derive identity or roles from skill inputs, model output, request
  business fields or unverified token claims.
- The result is the immutable credential value introduced by PR 8.1.2.
- Token profile:

  | Field | Contract |
  | --- | --- |
  | Signature | RS256; `kid` is the RFC 7638 thumbprint |
  | JOSE `typ` | `loomspan-execution+jwt` |
  | `token_use` | `loomspan_execution` |
  | `iss` | `loomspan-sdk.issuance.issuer`, default `loomspan-app`; never derived from `spring.application.name` or another per-application name |
  | `sub` | Non-blank stable subject |
  | `aud` | `loomspan-sidecar`, plus the callback audience (the issuer value), plus `loomspan-sdk.issuance.additional-audiences` |
  | `iat`, `exp` | Lifetime from `loomspan-sdk.issuance.lifetime`, default 15 minutes |
  | `jti` | Unique per issuance |
  | `roles` | String array, possibly empty |

- Lifetime accepts any positive duration and logs a startup WARN above one hour.
  There is no maximum and no freshness rule.
- `loomspan-sdk.issuance.roles` is the single allowlist. Include only allowlisted
  authorities the user actually has, with names unchanged.
  - No prefix is added, stripped or mapped.
  - The application's role-prefix settings are not changed.
  - Roles are snapshots: no live account lookup, role translation or principal
    reconstruction.
- No async or thread-context wrappers.
- Final property names may be adjusted in planning (the names above are
  indicative). They must stay under `loomspan-sdk.*` and must not include
  aliases for any earlier names.

### Documentation

Document the following in SDK documentation:
- both signing modes and their restart consequences;
- the colocated sole-writer assumption: one app instance per key directory, and
  replicas or multiple apps use provided signing;
- key-directory ownership and permissions as deployment responsibilities;
- one keytool/openssl key-generation command;
- the token profile and the defaults for issuer, audiences and lifetime;
- exact authority naming, including the `hasRole('ORDER_EDITOR')` /
  `hasAuthority('ROLE_ORDER_EDITOR')` example.

## Acceptance criteria

- [ ] A missing signing mode fails startup with a message naming the property,
      both options and the colocated quickstart. The packaged starter
      configuration sets no mode.
- [ ] Settings from the other mode fail startup.
- [ ] Colocated mode:
      - A missing key-directory setting, a missing directory or an unwritable
        directory fails startup with the path named.
      - The private key is never written.
      - `jwks.json` is replaced atomically.
      - A restart produces a new kid.
- [ ] Provided mode loads PKCS12, JKS and PEM keys and exports the JWKS (with
      `x5c` when a certificate exists). Missing or invalid material, non-RSA
      keys and keys under 2048 bits fail startup without key generation.
- [ ] Passwords are accepted only through secret or environment references.
      Expired or near-expiry signing certificates log a warning.
- [ ] Issued tokens match the profile: RS256 with thumbprint `kid`, `typ`,
      `token_use`, `iss`, `sub`, `aud`, `iat`, `exp`, `jti` and `roles`.
- [ ] Issuer and audience defaults need no configuration. Additional audiences
      are added.
- [ ] The default lifetime is 15 minutes. A configured positive lifetime is
      honored, and a lifetime above one hour logs a startup WARN.
- [ ] Spring and trusted custom identity produce equivalent tokens. Anonymous or
      unauthenticated callers cannot obtain one. Business input cannot change
      the subject or roles.
- [ ] Only allowlisted authorities the user holds appear, with exact names, and
      an empty roles array is allowed.
- [ ] No private material or tokens appear in logs or diagnostics.
- [ ] SDK documentation covers the signing modes, their consequences, the
      key-generation command, the token profile and exact authority naming.

## Context

- Depends on PR 8.1.1. Uses the credential value from PR 8.1.2.
- Canonical sources:
  - [Signing and trust](../phases/2026-10-15-sdk-v1-signing-trust-reference.md), "Signing modes" and "Configuration and visibility"
  - [Identity and callbacks](../phases/2026-10-15-sdk-v1-identity-callback-reference.md), "Issuance", "Execution-token profile" and "Exact authority names"
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md): "SDK issuance" and "SDK signing" rows (focused)
- Composed evidence for these rows comes later:
  - PR 8.1.5: Local colocated start/restart and a form-authenticated caller
    reaching a skill.
  - PR 8.1.6: the authorized callback.
  - PR 8.1.7: the provided-key deployment.
- Nonbinding hint: Nimbus JOSE + JWT is a suitable starting point.
- Out of scope: callback validation (PR 8.1.6), Sidecar trust (PR 8.1.4) and
  private-key hot reload.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This defines security-critical key handling, a token-format
  contract and an identity boundary, together with new public configuration.
- **Reassessment triggers:** none
