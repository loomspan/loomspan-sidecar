# SDK v1 signing and trust reference

Reconciled 2026-10-18; implementation and tests are planned.
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
Replace superseded trust/configuration contracts without compatibility modes.

## Signing modes

| Mode | Private material | Public export | Restart consequence |
| --- | --- | --- | --- |
| `colocated` (default) | Fresh RSA key in memory, never written to disk | Atomically write public `<key-directory>/jwks.json` | New key; previous tokens fail as receivers adopt new trust |
| `provided` | Operator RSA key in PKCS12, JKS or PEM; optional certificate | Startup `jwks.json` export, optional x5c | Same provided key; no replacement on missing/invalid material |

Use RSA/RS256 only; reject other key types at startup.
The kid is the RFC 7638 thumbprint. Passwords come from secrets/environment
references, never literal configuration properties. Warn on expired or near-expiry
JWT signing certificates; this does not relax TLS certificate validation.
Key reuse, including use of an operator's TLS key, is the user's choice.

Provide one documented keytool/openssl key-generation command for users who need
a key; no additional SDK CLI is required. Provided replicas share the keystore
and operators coordinate changes; the SDK does not coordinate replicas.

Provided private signing keys are loaded only at application startup. Replacing
the key file or keystore does not change the running signer: operators must restart
each affected application to activate the replacement. No private-key watcher,
hot reload or uninterrupted-rotation guarantee is required. Public JWKS trust
continues to reload independently under the rules below.

All signing uses a narrow key-source interface: sign RS256 bytes and supply public
key and kid. Consumers do not require private key bytes. No cloud key adapter or
provider integration is required by v1.

Callbacks use the app's own signing public key(s), optionally augmented by a local
rotation-overlap JWKS. The callback key set is independent of Sidecar's file.
No public key-hosting endpoint is needed.

## Sidecar trust modes

`colocated` reads `<key-directory>/jwks.json` and refuses startup unless Sidecar
binds to loopback. `file` reads an operator-supplied JWKS path.
Both use one loader/validator and permit multiple keys for one issuer's rotation.
Both default to colocated with the same default directory.
Reject settings belonging to another selected mode at startup.

The current file is authoritative. Validate the entire candidate and publish one
complete in-memory key set atomically; no request sees a partially loaded set.

| Detected file state | Authentication / readiness |
| --- | --- |
| Valid nonempty JWKS | Use current keys; ready |
| Valid empty JWKS | Withdraw all keys; reject execution authentication; not ready |
| Missing, unreadable or malformed | Reject execution authentication; not ready; no stale last-good acceptance |
| Corrected valid nonempty JWKS | Recover automatically on reload |

Reload on file changes and on unknown kid, rate-limiting the latter.
Writers use a temporary file and atomic replacement. Implementation planning selects
one bounded documented reload interval; do not promise instantaneous cross-process
withdrawal. Startup waits not-ready for usable trust instead of generating a signing
key. Fail startup for invalid configuration such as colocated non-loopback binding.

The same failure/recovery rule applies to a configured callback overlap file.
An unconfigured optional file is not an error. If a configured overlap file becomes
unusable, affected callback authentication stops; own-key fallback must not conceal it.

No trust history, initialization ceremony, database, management write API or Console
trust page is introduced. Restoring an old file restores its keys' authority.
Operators are responsible for avoiding accidental restoration of withdrawn keys.

## Rotation, queues and expiry

Provided-key rotation is add the new public key to the receiving trust sets,
deploy the replacement private key and restart signing applications, then remove
the old public key when the intended overlap ends. Update Sidecar and callback
receivers independently. After a receiver reloads, removed keys cannot authenticate
new requests there. Restarting each signing application also replaces its own
configured signing public key; the optional overlap JWKS can retain the old public
key during the transition.

Immediately before queued work enters the Framework, revalidate its captured token
against current trust and expiry. Invalid tokens or unusable trust prevent handoff.
Expired queued work fails with `execution credential expired before start`.
Already admitted Framework work is not cancelled when trust changes.

Before each outbound callback, Sidecar checks exp. Once expired, fail the skill call
with `execution credential expired` and do not send that token, even within the
receiver's validation skew. Queue time consumes token lifetime. Sidecar never signs,
refreshes or replaces captured execution credentials. Each callback receiver checks
its own current trust; changing Sidecar's file does not update receivers.

Colocated app restart intentionally loses the old private key. Old executions may
continue inside the Framework, but their callbacks fail once the receiver no longer
trusts the captured token. Do not add persistent signing state or continuity machinery.

## Configuration and visibility

Indicative names, to finalize in implementation planning:

```yaml
loomspan-sdk.signing:
  mode: colocated # or provided
  colocated.key-directory: /var/run/loomspan/keys
loomspan-sidecar.trust:
  mode: colocated # or file
  colocated.key-directory: /var/run/loomspan/keys
  # file.jwks is used only in file mode
```

Replace old execution JWT key-source/prefix configuration with these contracts,
without aliases. Keep existing management configuration unchanged.

Health and one log line per load/reload report mode, path, loaded kids, last load
time and ready/waiting. Anonymous health exposes status only; details use existing
management authentication. Never log token/private material.

**Future-ticket clarification:** relevant trust, configuration and integration
tickets must explicitly state that verification keys come from the configured JWKS
file in both file and database application ConfigurationMode. Database mode does
not move JWT trust into the database or create management trust APIs. This note is
specific to this change, not an addition to the long-term design lens.

## TLS ownership

JWT signing-key/trust behavior above is separate from transport certificates.
DevOps owns TLS issuance, storage, delivery, renewal, replacement, CA rotation and
revocation using Spring Boot/Caddy facilities. No Loomspan watcher, scheduler,
reload controller, prescribed replacement/restart procedure, seamless rotation or
timed connection-retirement promise is introduced. Tests cover transport integration;
JWT file reload/rotation tests remain in scope.
