# Operating Loomspan Sidecar

For application deployment and recovery. Start with [setup](setup.md) and the
[production Compose guide](../examples/production/README.md). Developing Sidecar
itself is a separate activity; historical maintainer material is parked in
[ai/throughts](../ai/throughts/readme-maintainer-material.md) for review.

## Deploy

The supported production path is the [single-instance Compose guide](../examples/production/README.md). It covers HTTPS, SMTP, JWT trust, model connections, volume preparation, and first administrator setup. Kubernetes deployment support is deferred.

The runtime-only image never resolves Maven dependencies or checks out
framework source. It runs as UID/GID `10001:10001`, exposes application port
8080 and internal Actuator health port 9091, and starts Java with
`-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0
-XX:+ExitOnOutOfMemoryError`. Replace `JAVA_TOOL_OPTIONS` when deployment-specific
heap or GC settings are needed. Its exec-form Java entry point lets container
SIGTERM reach Spring directly.

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

Restart with `docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml start sidecar` (or the quickstart Compose
file for a development volume). Verify startup and
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

The application port remains Boot's default `8080`. Health is exposed separately
on management port `9091` at `/actuator/health` and
`/actuator/health/readiness`. Other Actuator endpoints are not exposed.
Readiness and liveness probe groups are available at
`/actuator/health/readiness` and `/actuator/health/liveness`. Registration and
route validation are eager, so invalid configuration prevents startup from
becoming ready. Readiness changes to refusing traffic synchronously when the
owning application context closes; liveness remains up during ordinary long work.
Loomspan observability routes are disabled by default, so no Console operator
API is exposed. When Loomspan Console observability is enabled, its
reserved `/_loomspan/observability/v1/**` namespace remains protected by the
framework's independent API-key filter. Console keys do not authenticate `/v1`.

## Sidecar configuration reference

<!-- configuration-reference:start -->

| Key | Default / requirement |
| --- | --- |
| `loomspan-sidecar.url-variables` | Empty by default; exact allowed process-environment names for REST `base-url`; changes require restart. |
| `loomspan-sidecar.storage.database-path` | `/sidecar/data/sidecar.db`; writable persistent local database path and parent directory required at startup. |
| `loomspan-sidecar.snapshots.max-retained` | `10`; positive count of stored snapshots, including pending and failed history; protected snapshots may exceed it. |
| `loomspan-sidecar.management.session-idle-timeout` | `30m`; positive idle deadline extended only by intentional session activity. |
| `loomspan-sidecar.management.edit-lease-timeout` | `15m`; positive editing lease deadline extended only by accepted activity reports. |
| `loomspan-sidecar.management.mail-from` | Required nonblank sender address for email-dependent management actions. |
| `loomspan-sidecar.management.external-base-url` | Required trusted HTTPS console URL for email links; loopback HTTP is allowed in development. |
| `loomspan-sidecar.auth.jwt.issuer-uri` | Required nonblank issuer; also used with an explicit local key or JWKS URL. |
| `loomspan-sidecar.auth.jwt.audience` | Required nonblank audience. |
| `loomspan-sidecar.auth.jwt.jwk-set-uri` | Optional explicit JWKS URL; mutually exclusive with the public-key location. |
| `loomspan-sidecar.auth.jwt.public-key-location` | Optional RSA PEM resource; mutually exclusive with the JWKS URL. |
| `loomspan-sidecar.auth.jwt.clock-skew` | `60s`; zero is allowed, negative values are rejected. |
| `loomspan-sidecar.auth.jwt.roles-claim` | `roles`; required nonblank trusted claim name. |
| `loomspan-sidecar.auth.jwt.role-prefix` | `ROLE_`; required but may be empty. |
| `loomspan-sidecar.executions.max-input-size` | `1MB`; positive raw HTTP body limit. |
| `loomspan-sidecar.executions.max-retained` | `1000`; positive count of queued, running, and terminal records. |
| `loomspan-sidecar.executions.completed-ttl` | `15m`; positive terminal-record lifetime. |
| `loomspan-sidecar.executions.max-concurrent` | `32`; positive worker count. |
| `loomspan-sidecar.executions.max-queued` | `128`; positive waiting-record count. |
| `loomspan-sidecar.executions.max-queued-input-size` | `64MB`; positive total serialized input bytes reserved by waiting work. |
| `loomspan-sidecar.executions.diagnostics` | `NEVER`; allowed values are `NEVER`, `ONERROR`, and `ALWAYS`. |
| `loomspan-sidecar.rest-routes` | File-mode REST route resource settings; `location` defaults to `classpath:/sidecar-empty-rest-routes.yaml`; changes require restart. |
| `loomspan-sidecar.configuration.mode` | `database` (default) or `file`; startup-only authority, invalid values fail. |
| `loomspan-sidecar.rest-routes.location` | `classpath:/sidecar-empty-rest-routes.yaml`; explicit missing/invalid resources fail file startup; restart applies changes. |
<!-- configuration-reference:end -->

The route schema and `${NAME}` bindings are described in
[REST skill routes](../agent-skills/loomspan-sidecar-authoring/references/integration.md#rest-skill-routes). Framework shutdown remains
`loomspan.shutdown.timeout`; inbound server and outbound mTLS configuration
remains under standard `server.ssl.*` and `spring.ssl.bundle.*` namespaces. The
image honors those standard Boot environment and command-line overrides.
