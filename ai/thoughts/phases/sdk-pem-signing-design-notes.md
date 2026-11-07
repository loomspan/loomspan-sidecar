# Shared SDK PEM signing design

Date: 2026-11-06. Status updated: 2026-11-07. Agreed signing contract;
Java implementation is present in the current checkout; Python implementation is
pending. Applies to Java, Python and future Go/.NET SDKs. This is the authoritative
design record for signing-material simplification. The status refresh is based on
source inspection, not a fresh build or integration verification run.

**Development policy: replace superseded contracts, code and schemas directly.
Do not add compatibility shims, legacy adapters, dual APIs or migration machinery
solely to preserve obsolete behavior.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). Keep dependencies,
configuration and verification proportional to concrete requirements.

No development-data reset is required by this design. Preserve existing
credentials, identities, operational data and reference packs. If fixtures need
different key packaging, preserve the key deliberately rather than regenerate it
incidentally. Planning does not authorize deleting data or modifying deployed keys.

## Problem and outcome

Before this change, Java's provided signer accepted JKS, PKCS12 and PEM, with
aliases, password references and optional signing certificates. Reproducing these options across
Python, Go and .NET adds dependency and interoperability work without improving
the execution-token protocol. Python research exposed JKS installation costs and
PKCS12 alias-selection limitations in candidate libraries.

Standardize provided signing on one unencrypted PKCS#8 PEM private key and keep
the existing ephemeral colocated mode. Remove signing certificates. Both modes
produce the same execution-token profile and public JWKS, usable across SDKs.

## Agreed decisions

- Retain explicit `colocated` and `provided` signing modes.
- Colocated mode generates a fresh RSA key in memory at application startup and
  atomically exports only public JWKS. It never persists the private key. One
  process owns the export path; multiple workers/replicas use provided identity.
- Provided mode loads operator-owned unencrypted PKCS#8 PEM at startup. Private
  material changes require restart. Missing or invalid input fails startup;
  there is no fallback-generated key.
- Remove JKS/PKCS12 signing support, alias selection and signing-password settings.
  Encrypted PEM is out of scope for now. Operator-managed secret delivery, storage
  and access controls protect the private file.
- Remove optional signing-certificate inputs, certificate-chain handling,
  certificate expiry warnings and signing-certificate `x5c` export. No current
  external consumer requiring certificate-bearing signing JWKS has been identified.
- Retain RSA minimum strength of 2048 bits, RS256, RFC 7638 public-key thumbprint
  `kid`, and existing execution-token claims, role intersection and audience rules.
- Export public RSA JWK material directly from the signing key. Keep private
  material out of logs, errors, business objects and public exports.
- Keep operator-owned rotation and overlapping public trust. PEM limits neither
  the number of public verification keys nor multiple signers in a trust set.
  Operators assemble aggregate JWKS; separate SDK exports must not overwrite it.
- Keep ordinary TLS/mTLS certificate and keystore configuration under the existing
  platform facilities. This change concerns execution-token signing only.

Current validators trust configured public RSA JWK fields, not X.509 chain
validation. Certificate removal therefore preserves the existing authentication
decision. Colocated signing already operates without a signing certificate.

## Agreed configuration and input rules

Adopted by the developer on 2026-11-06. These are implementation requirements,
not open design alternatives.

Keep the existing Java property structure with fewer fields:

```yaml
loomspan-sdk:
  signing:
    mode: provided
    provided:
      key-location: /run/secrets/loomspan-signer.pem
      jwks-export: /run/loomspan-public/jwks.json
```

Colocated mode retains `colocated.key-directory`. Export parents must already
exist. Reject destinations overlapping the provided private key, including
equivalent existing-file paths. Atomic publication failure aborts initialization;
there is no non-atomic fallback. The SDK does not create directories or manage
their permissions.

Remove `format`, `key-alias`, `keystore-password-reference`,
`key-password-reference`, and `certificate-location` from supported signing
configuration. Detect obsolete signing options with actionable startup errors
rather than silently ignoring operator intent. Such rejection is not a legacy
adapter. Existing forbidden literal password options must not become silently
accepted. Exact binding/error mechanics and language-specific names remain
implementation details; Python need not copy Spring's property syntax.

Required strict input contract: exactly one `PRIVATE KEY` PEM block containing a
valid RSA PKCS#8 private key, allowing ordinary surrounding whitespace and standard
line endings. Reject encrypted blocks, PKCS#1, certificate bundles, multiple key
blocks, malformed/trailing non-whitespace content, non-RSA and weak keys. Validate
with established runtime/library primitives rather than writing cryptography.
Prove equivalent handling with fixtures. Require an explicit mode and its required
paths, and reject settings belonging to the other mode. Exact diagnostic wording
and language-specific configuration object names remain implementation details.

## Sidecar impact assessment

Source inspection on 2026-11-06 found no required Sidecar production-code or
execution-protocol change for PEM-only SDK signing. Sidecar consumes public JWKS;
it does not load the application's private PEM/JKS/PKCS12 signing file.
`SidecarTrustProperties` resolves a colocated directory's jwks.json or an explicit
JWKS file. `CurrentFileJwtTrust` constructs RSA verification keys from public
JWK fields. Operations documentation and a trust test explicitly establish that
x5c is ignored, so removing SDK x5c export does not change validation.

Retain Sidecar's trust configuration, reload, JWT/RBAC and callback forwarding
contracts. Do not remove unrelated PKCS12/JKS usage in TLS tests or Spring SSL
bundles. Sidecar-side verification must prove acceptance of the revised SDK's
certificate-free JWKS and signed tokens; recipes/fixtures may need updates if
they configure the SDK's old signing options. Full inventory remains planning work.

The separately agreed invalid-initial-overlap startup fix belongs to the SDK's
callback security integration, not Sidecar's own trust-file startup behavior.
Do not accidentally make Sidecar fail startup while waiting for a colocated
application's first public-key export. Submission error classification is also
a separate Java client correction, not a PEM protocol change.

## Trust and lifecycle boundaries

Keep the existing callback own-key plus optional overlap-file model. The earlier
greenfield proposal for a single complete callback trust file was exploratory and
is not part of this change. Retain the implemented Java behavior: invalid configured initial
overlap fails security/application startup; runtime invalidity withdraws all trust
until recovery, with safe state-transition diagnostics.

Restart-driven colocated key changes may disrupt in-flight callbacks. No restart
cancellation/recovery coordinator is introduced. Credential expiry governs callback
admission, not cancellation of already-admitted work. Independent credential
lifetime is separate from signing format. Python now retains Java's independent
15-minute default under the agreed parity baseline; see the Python design notes.

No namespace isolation or new multi-issuer authorization contract is introduced.
Applications sharing a Sidecar remain within its existing trusted-group model.

## Delivery and verification

Create Python implementation tickets through Sidecar's existing workflow. Java
has removed obsolete signing contracts; Python implements this contract directly.
Use current code and tests as evidence rather than the original pending-work status.
Future Go/.NET SDKs use it as their input contract, without requiring those SDKs
to be built as part of this change.

Inventory the maintained suite and deployment fixtures for JKS/PKCS12/password/
certificate dependencies. Any fixture preparation must be explicit and preserve
existing key identity where required. No automatic runtime conversion or reset.
Update the design lens only where a shared constraint actually changes; do not
publish unsupported behavior in product documentation ahead of implementation.

Each implementation unit owns its tests and documentation. Cover:

- Valid PEM load, key strength/type checks and safe failures for the rejected forms.
- Same-key thumbprint/public JWKS equivalence and Java/Python token verification.
- Public-only exports, atomic publication failure, input/output overlap and
  restart behavior in both modes.
- Rejection of obsolete settings without exposing secrets.
- Maintained-suite use of the new configuration with existing business behavior.

Reuse fixed synthetic public test vectors and protected test-only private fixtures;
never include operator keys. Go/.NET verification is required when implementing
those SDKs, not claimed from Java/Python evidence.

## Current status and remaining work

The current checkout contains Java PEM signing (`3571bb8`), submission uncertainty
(`487f26e`) and callback trust startup/diagnostics (`6a790f9`). Earlier ticket links
and intermediate-build sequencing are historical and no longer prerequisites for
Python. Current source/test anchors below remain available; this update does not
claim a fresh test run or re-certify maintained-suite integration.

Python implementation and its own Java/Sidecar interoperability evidence remain.
Follow the [Python design notes](python-sdk-design-notes.md) for accepted API,
lifecycle, cancellation, packaging and support scope. Library selection and
platform-specific atomic-file behavior require implementation evidence. No new
signing option, legacy adapter, multiworker export coordinator or future-language
SDK is required for Python v1.

## Related evidence

- [Python SDK discussion and dependency research](python-sdk-design-notes.md).
- [Current Java SDK README](../../../sdks/java/README.md).
- [Current signer loader](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeys.java).
- [Current callback trust](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackJwtTrust.java).
- [Current Sidecar trust](../../../src/main/java/ai/loomspan/sidecar/security/CurrentFileJwtTrust.java).
