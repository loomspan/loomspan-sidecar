# Operating Loomspan Sidecar

For application deployment and recovery. Start with [setup](setup.md) and the
[production Compose guide](../examples/production/README.md). Developing Sidecar
itself is a separate activity; historical maintainer material is parked in
[ai/throughts](../ai/throughts/readme-maintainer-material.md) for review.

## Deploy

The supported production path is the [single-instance Compose guide](../examples/production/README.md). It covers HTTPS, SMTP, JWT trust, model connections, volume preparation, and first administrator setup. Kubernetes deployment support is deferred.

The runtime-only image never resolves Maven dependencies or checks out
framework source. It runs as UID/GID `10001:10001`, exposes application port
8080, including Actuator health, and starts Java with
`-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0
-XX:+ExitOnOutOfMemoryError`. Replace `JAVA_TOOL_OPTIONS` when deployment-specific
heap or GC settings are needed. Its exec-form Java entry point lets container
SIGTERM reach Spring directly.

## HTTP executor activation

The [HTTP executor](http-executor.md) uses captured target configuration, one
application-owned client, ordinary JVM HTTPS trust and hostname verification.
There are no outbound executor SSL bundles or client certificates; Caddy owns mTLS.
File mode loads operations and shared executor settings from startup files.
Database mode publishes/restores complete skill and execution configuration.
HTTP domain checks occur on invocation before dispatch; readiness/publication
do not establish HTTP validity or reachability.
Standard Boot inbound server TLS facilities remain independent.

## Configuration and management

Choose [file or database authority](../agent-skills/loomspan-sidecar-authoring/references/configuration-modes.md)
at startup. Database mode starts empty and restores complete publications. File
mode loads deployment files and requires restart for changes. Both need writable
local SQLite storage for management.

- [First administrator and recovery](admin-access.md)
- [Accounts, sessions and token security](../agent-skills/loomspan-sidecar-authoring/references/management-access.md)
- [Browser editing and management API](../agent-skills/loomspan-sidecar-authoring/references/management-api.md)
- [Configuration lifecycle and publication faults](../agent-skills/loomspan-sidecar-authoring/references/configuration-lifecycle.md)
- [Bundle transfer](../agent-skills/loomspan-sidecar-authoring/references/configuration-bundles.md)

Protect the persistent database and its WAL/SHM companions. Use one instance per
local volume with reliable locking, never a shared network filesystem. Preserve
the external credential encryption key separately; a database restore cannot
recover encrypted credentials without it. See [key operations](../agent-skills/loomspan-sidecar-authoring/references/configuration-modes.md).

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
Sidecar strictly checks expiry and fails with `HTTP caller credential missing or expired`
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
published host-loopback ports. See [quickstart preparation](../examples/quickstart/README.md).
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

### Fully trusted application groups

**Apps sharing a Sidecar trust each other in every way. Use separate Sidecars
when applications require isolation.** Group members can assert each other's
users and roles, reuse bearer tokens and participate in the same business and
network trust boundary. User and record checks still apply to authenticated
requests; they do not isolate a group member from another trusted application.

Use one issuer, one provided signing key, the same callback audience, and shared
user-directory semantics and exact role vocabulary across the group. Give each
app a distinct SDK public export path. Configure Sidecar with an operator-owned
public JWKS and callback receivers with their own
`loomspan-sdk.callbacks.overlap-jwks` files; keep these separate from SDK exports.
Each app can issue for the group, invoke the shared Sidecar and receive the
original credential on approved callbacks. Result ownership is issuer plus
subject, so another group app acting for the same user can read that user's
execution; another subject cannot.

### Provided-key rotation

Provided private signing material loads once at app startup. Deploy replacement
material and **restart every signing app**; changing a file never hot-reloads the
private key. Retain secure delivery, backups and rollback policy under operator
control. Publish complete public-only JWKS atomically in their mounted directory;
mount the directory rather than a single inode when using atomic replacement.
No database operation or management publication rotates JWT trust in either
application ConfigurationMode.

1. Generate replacement signing material securely and obtain its public JWK and
   `kid`. Add that public key beside the old key in Sidecar's current JWKS.
   Independently add it to every callback receiver's overlap JWKS. Verify new-key
   acceptance at each receiver before changing normal signing.
2. Deploy the replacement private material to the signing apps and restart them.
   Verify their exported public `kid` and freshly issued tokens use the replacement
   key, with the same issuer, callback audience and role semantics. Old tokens
   remain accepted while both keys overlap.
3. After the chosen overlap period, remove the old public key from Sidecar's file
   and independently from each callback overlap file. Verify old-key rejection
   and replacement-key acceptance at each receiver. A callback receiver always
   includes its **own startup signing key** in valid trust: it must first restart
   on replacement signing material for removal of the old overlap key to revoke it.

Sidecar and each configured SDK overlap receiver poll their files independently
every **one second**. Adoption is eventual; allow scheduling and request latency
when observing it. The composed test permits five seconds per publication, which
is a test observation allowance, not an exact HTTP cutoff guarantee. Unknown-kid
reads may accelerate adoption. Updating Sidecar never updates callback trust,
and updating a callback file never updates Sidecar. Missing, malformed, empty or
unreadable configured trust withdraws acceptance until corrected; a receiver
with no configured overlap file uses only its startup signer.

Withdrawal does not cancel already admitted Framework work or undo effects.
Queued work still needs current trust before handoff; subsequent callbacks need
the receiving app's current trust and the original credential must remain
unexpired. Fresh owner credentials can poll retained results. Rotation does not
promise uninterrupted app availability or private-key hot reload.

### Outbound-only applications

Applications that only invoke model skills need no callback listener. Configure
only the required model skills and omit HTTP operations. The HTTP executor
subtree may contain `targets: {}`. Keep execution JWT
signing and Sidecar verification trust. A non-web Spring application can use
`spring.main.web-application-type=none`; authenticate users in its own trusted
application boundary before issuing credentials. The SDK callback servlet chain
requires a servlet application and creates no listener in a non-web process.

Retain Local's loopback/colocated arrangement, Standard's verified outbound
HTTPS, or Hardened's app-side outbound Caddy and Sidecar mTLS ingress. The
[remote recipe adaptations](remote-recipes.md#outbound-only-adaptations) identify
the callback infrastructure to omit. This requires no data reset.

## Stopped-instance backup and recovery

The protected console links this procedure from **Configuration recovery
guidance**. That page is explanatory and read-only: it does not execute SQL,
retry or cancel publication, or provide import, export, or rollback controls.
Local retained-history rollback loads one snapshot still held by this server
into a saved draft for later validation and publication; it cannot recover
accounts, settings, or a damaged database. Configuration
ZIP import also cannot bypass a mutation fault.

Stop the sole Sidecar instance and wait for process exit before either copy.
For production use `docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml stop sidecar`; for the development quickstart
use `docker compose -f examples/quickstart/compose.yaml stop sidecar`.
On the host or maintenance container with
the local persistent volume mounted, set `DATA_DIR` to the directory containing
`sidecar.db` and `BACKUP_DIR` to a separate protected directory. Copy the main
database and whichever WAL/SHM files exist while no process has it open:

```sh
set -eu
DATA_DIR=/path/to/mounted/sidecar-data
BACKUP_DIR=/path/to/protected/backup
mkdir "$BACKUP_DIR" # use a new empty directory for each backup
test -f "$DATA_DIR/sidecar.db"
for suffix in '' -wal -shm; do
  if [ -e "$DATA_DIR/sidecar.db$suffix" ]; then
    cp -p "$DATA_DIR/sidecar.db$suffix" "$BACKUP_DIR/"
  fi
done
```

To recover an unreadable or invalid selected state, stop Sidecar again, choose
a backup created while stopped, and replace the whole database set. Keep a
separate copy of damaged files for investigation. Remove stale WAL/SHM companions
before replacing and ensure the directory and restored files are writable by
UID/GID `10001:10001`:

```sh
set -eu
DATA_DIR=/path/to/mounted/sidecar-data
BACKUP_DIR=/path/to/protected/backup
test -f "$BACKUP_DIR/sidecar.db" # verify the backup before removing the current database
mkdir -p "$DATA_DIR"
rm -f "$DATA_DIR/sidecar.db" "$DATA_DIR/sidecar.db-wal" "$DATA_DIR/sidecar.db-shm"
for suffix in '' -wal -shm; do
  if [ -e "$BACKUP_DIR/sidecar.db$suffix" ]; then
    cp -p "$BACKUP_DIR/sidecar.db$suffix" "$DATA_DIR/"
  fi
done
chown 10001:10001 "$DATA_DIR" "$DATA_DIR/sidecar.db"
for suffix in -wal -shm; do
  if [ -e "$DATA_DIR/sidecar.db$suffix" ]; then
    chown 10001:10001 "$DATA_DIR/sidecar.db$suffix"
  fi
done
```

Restart namespace participants together: `docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml up -d --force-recreate sidecar caddy`, including your application service in the same command. For the quickstart use `docker compose -f examples/quickstart/compose.yaml up -d --force-recreate sidecar host ingress`. Verify startup and
readiness. Restore rolls current selection, statuses, and history back to the
backup point. This full installation database backup includes authored YAML,
management account records, and literal sensitive values without whole-database encryption; managed provider credentials remain encrypted. Saved drafts are restored from this database backup; editing
leases, sessions and validation proofs do not survive a restart.
Startup activates the backup's committed pointer, including a pending selected
record. Sign in using a restored
management account and inspect **Current configuration** and **History** to
confirm the runtime snapshot and intended restart selection. The
[configuration bundle](../agent-skills/loomspan-sidecar-authoring/references/configuration-bundles.md) excludes accounts and sessions;
the stopped-instance backup remains the full installation recovery artifact.
Protect backup access accordingly. For an outcome-bookkeeping
fault, stop the sole instance, preserve a consistent full database-set copy,
correct the storage problem, and restart if the committed intended selection is
wanted. If a failed update leaves a new intended selection, restart loads that selection
even if the previous configuration was still running. To recover the previous state, restore a known-good stopped full database backup and verify activation and
readiness. No online repair endpoint or live SQL procedure is provided.

## Management endpoints

The application listener remains Boot's default `8080`. Health shares that
listener at `/actuator/health` and
`/actuator/health/readiness`. Other Actuator endpoints are not exposed.
Readiness and liveness probe groups are available at
`/actuator/health/readiness` and `/actuator/health/liveness`. Framework registration
and structural/reference validation run before readiness. HTTP-specific settings
are checked only on invocation before dispatch; HTTP-invalid but Framework-valid
configuration may report ready. Readiness changes to refusing traffic synchronously when the
owning application context closes; liveness remains up during ordinary long work.
Loomspan observability routes are disabled by default, so no Console operator
API is exposed. When Loomspan Console observability is enabled, its
reserved `/_loomspan/observability/v1/**` namespace remains protected by the
framework's independent API-key filter. Console keys do not authenticate `/v1`.

## Sidecar configuration reference

<!-- configuration-reference:start -->

| Key | Default / requirement |
| --- | --- |
| `loomspan-sidecar.storage.database-path` | `/sidecar/data/sidecar.db`; writable persistent local database path and parent directory required at startup. |
| `loomspan-sidecar.snapshots.max-retained` | `10`; positive count of stored snapshots, including pending and failed history; protected snapshots may exceed it. |
| `loomspan-sidecar.management.session-idle-timeout` | `30m`; positive idle deadline extended only by intentional session activity. |
| `loomspan-sidecar.management.edit-lease-timeout` | `15m`; positive editing lease deadline extended only by accepted activity reports. |
| `loomspan-sidecar.management.mail-from` | Required nonblank sender address for email-dependent management actions. |
| `loomspan-sidecar.management.external-base-url` | Required trusted HTTPS console URL for email links; loopback HTTP is allowed in development. |
| `loomspan-sidecar.trust.mode` | No default; required `colocated` or `file`; fixed at startup. |
| `loomspan-sidecar.trust.colocated.key-directory` | No default; required in colocated mode; reads `jwks.json` in this deployment-created directory; forbidden in file mode. |
| `loomspan-sidecar.trust.file.jwks` | No default; required filesystem JWKS path in file mode; forbidden in colocated mode. |
| `loomspan-sidecar.auth.jwt.audience` | `loomspan-sidecar`; nonblank receiving audience, extra token audiences allowed. |
| `loomspan-sidecar.executions.max-input-size` | `1MB`; positive raw HTTP body limit. |
| `loomspan-sidecar.executions.max-retained` | `1000`; positive count of queued, running, and terminal records. |
| `loomspan-sidecar.executions.completed-ttl` | `15m`; positive terminal-record lifetime. |
| `loomspan-sidecar.executions.max-concurrent` | `32`; positive worker count. |
| `loomspan-sidecar.executions.max-queued` | `128`; positive waiting-record count. |
| `loomspan-sidecar.executions.max-queued-input-size` | `64MB`; positive total serialized input bytes reserved by waiting work. |
| `loomspan-sidecar.executions.diagnostics` | `NEVER`; allowed values are `NEVER`, `ONERROR`, and `ALWAYS`. |
| `loomspan-sidecar.configuration.mode` | `database` (default) or `file`; startup-only authority, invalid values fail. |
<!-- configuration-reference:end -->

The explicit operation and target schema is described in
[HTTP executor](http-executor.md). Framework shutdown remains
`loomspan.shutdown.timeout`; inbound server TLS configuration
remains under standard `server.ssl.*` and `spring.ssl.bundle.*` namespaces. Caddy
owns outbound mTLS. The
image honors those standard Boot environment and command-line overrides.

The image's default Docker health probe uses HTTP on application port 8080, as
the shipped Caddy topology does. A deployment enabling direct application TLS
must override its container/orchestrator health probe to use that same TLS
listener with the certificate's hostname and trusted CA. There is no separate
plaintext health listener. Do not disable certificate verification.
