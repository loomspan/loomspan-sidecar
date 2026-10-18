## Execution JWT trust

JWT verification keys come from one current filesystem JWKS in **both** file and
database application ConfigurationModes. Publications, imports, SQLite restores
and management APIs cannot supply or preserve execution trust. Set
`loomspan-sidecar.trust.mode` explicitly; the jar has no mode. Choose `colocated`
with `trust.colocated.key-directory`, or `file` with `trust.file.jwks`. Paths are
filesystem paths, never remote JWKS/discovery URLs. A setting from the other
mode fails startup. Missing mode diagnostics point to the colocated quickstart.

Colocated requires explicit loopback `server.address`. Health and management
share this application listener; shipped configuration has no separate health
port. File mode
allows remote networking protected by your deployment's TLS and access policy.
The configured file is the only token-origin check: no expected issuer is
configured. Use separate Sidecars when applications require isolation; applications
sharing trust can assert each other's identities, roles and reusable tokens.

The JWKS must be an object containing a `keys` array. Every item needs `kty: RSA`,
a nonblank unique string `kid`, valid base64url `n` and `e`, and a usable RSA
public key with a modulus of at least 2048 bits. Optional `use` must be `sig` and
optional `alg` must be `RS256`. Private members `d`, `p`, `q`, `dp`, `dq`, `qi`
(and multi-prime `oth`) are forbidden even when null. One bad key rejects the
entire file. `kid` need not be the thumbprint. `x5c` and unknown members are
ignored; certificate chain/expiry cannot establish trust. Multiple keys support
rotation under the shared issuer/identity vocabulary.

| Detected current file | Execution authentication and readiness |
| --- | --- |
| Valid nonempty JWKS | Current keys accepted; ready |
| Valid empty JWKS | All keys withdrawn; waiting, not ready |
| Missing, unreadable or malformed | All keys withdrawn; waiting, no last-good fallback |
| Corrected valid nonempty JWKS | Automatic recovery |

Sidecar reads the current bytes every **one second**, even when file size and
mtime match. It validates the whole candidate and publishes an immutable key set
atomically. Unknown nonblank kids can force an additional read at most once per
second globally per process; this limiter does not defer scheduled checks.
Replace files atomically in the same directory. A request already validating a
captured snapshot may finish; withdrawal governs subsequent checks. Restoring
an old file restores its keys. No withdrawal ledger or historical barrier exists.

`executionTrust` contributes to readiness (DOWN/503 while waiting), independently
of liveness. Health reads the in-memory snapshot, without file I/O. Anonymous
health exposes status and standard probe-group names, with no component details.
Existing management browser sessions and read PATs on that same listener
control detailed mode, path, kids,
lastLoadTime and ready/waiting state. Load/reload logs contain the same safe
metadata and a reason category, never tokens, raw file text or parser exceptions.

Execution tokens require RS256, `typ: loomspan-execution+jwt`, a nonblank `kid`,
`token_use: loomspan_execution`, nonblank string `iss` and `sub`, a string-array
`roles` (empty allowed), numeric `exp`, and an `aud` string or string array
including Sidecar's audience. Present `iat`/`nbf` must be NumericDate values and
present `jti` a string. Fractional NumericDate values are accepted; numeric
strings and null/mixed claim shapes are rejected. Receive validation has fixed
60-second skew. URLs in the token never select a key source. Roles match skill
`rbac_roles` literally: `ROLE_ORDER_EDITOR` and `ORDER_EDITOR` are different.
Management `MGT_*` authorities remain separate from execution roles.

There is no admission-age cap. Immediately before Framework handoff Sidecar
strictly requires current trust and `now < exp` for the original captured token.
Expired queued work fails with `execution credential expired before start`;
withdrawn/invalid credentials fail before handoff. Already admitted Framework
work is not cancelled by trust withdrawal. Before **each** passthrough callback,
Sidecar strictly checks expiry and fails with `execution credential expired`
without a network send at or after `exp`, even within receive skew. Callback
receivers independently validate their trust. The original bytes go only to
approved targets; redirects and retries remain disabled. A fresh owner token
can poll using the same signed issuer/subject pair but never renews or replaces
the queued/admitted credential. Sidecar never signs or refreshes credentials.

The old execution JWT properties `issuer-uri`, `jwk-set-uri`,
`public-key-location`, `roles-claim`, `role-prefix` and `clock-skew` were removed
without aliases. Replace operator process settings and the former static sample
keys. No database, account, personal-token, draft or publication reset is needed;
valid managed publications never contained those process settings.

### Local deployment

The **deployment** creates the shared key directory and sets its permissions:
writable by the sole application signer/exporter and readable by Sidecar. Sidecar
never creates signing keys or the directory. On a developer machine use the same
absolute directory for the app and Sidecar, bind Sidecar to `127.0.0.1`,
and grant the app write and Sidecar read permissions using your OS ACLs.

Compose colocated services share the Sidecar network namespace with
`network_mode: "service:sidecar"`. The quickstart app and proxy depend on Sidecar
being **started**, because Sidecar readiness waits for the app's first export.
A shared volume is writable by the app (demo UID/GID `10001:10001`) and mounted
read-only by Sidecar. Loopback-bound backends cannot be reached through Docker
port NAT, so the shipped ingress proxy shares the namespace and receives the
published host-loopback ports. See the Sidecar repository quickstart preparation commands below.
Recreate namespace-sharing app/proxy containers together when replacing Sidecar.
The demo app generates a new ephemeral signing key on restart and atomically
replaces the export; old tokens stop validating when receivers adopt it.
Operator-provided signing keys must never be silently regenerated.

Kubernetes Local guidance is a **single pod**, with shared loopback networking
and a deployment-created `emptyDir`. For example, use pod
`securityContext: {runAsUser: 10001, runAsGroup: 10001, fsGroup: 10001}` and
`volumes: [{name: execution-keys, emptyDir: {}}]`; mount it at `/keys` writable
in the application and read-only at `/sidecar/keys` in Sidecar. An init container
owned by the deployment prepares directory ownership/mode when the platform's
fsGroup handling is insufficient. Set Sidecar
`server.address` to `127.0.0.1`, select colocated and point at `/sidecar/keys`. Keep one application
writer; unrelated pods/applications use operator-managed file mode and suitable
secure networking. This is deployment guidance, not a shipped Kubernetes runtime
test or a signing/key lifecycle controller.

### Prepare the demo key directory

Run from the Sidecar repository root after building `loomspan-sidecar:sc5-local`:

```sh
docker volume create loomspan-quickstart-keys
docker run --rm --user 0:0 -v loomspan-quickstart-keys:/keys --entrypoint sh loomspan-sidecar:sc5-local -c 'chown 10001:10001 /keys && chmod 700 /keys'
```

Use the same `SIDECAR_KEYS_VOLUME` name in Compose when overriding the default.
These are disposable demo commands; choose operator-owned paths and ACLs for
application deployments. No live deployment or data reset is authorized by this
guidance.
