# Operating Loomspan Sidecar

This guide covers local builds, production deployment, configuration publication, management, storage, recovery, and release status. Start with the [README](../README.md); see the [integration guide](integration.md) for application calls and skill routes.

## Build from source

Sidecar currently depends on `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.6-SNAPSHOT` installed in the local Maven repository from `C:/opendev/code/loomspan-framework`. Run `./mvnw` on POSIX or `.\mvnw.cmd` on Windows.

```powershell
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
```

## Deploy

The supported v1 production path is the [single-instance Compose guide](../examples/production/README.md). It covers HTTPS, SMTP, JWT trust, model connections, volume preparation, and first administrator setup. Kubernetes deployment support is deferred.

The runtime-only image never resolves Maven dependencies or checks out
framework source. It runs as UID/GID `10001:10001`, exposes application port
8080 and internal Actuator health port 9091, and starts Java with
`-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0
-XX:+ExitOnOutOfMemoryError`. Replace `JAVA_TOOL_OPTIONS` when deployment-specific
heap or GC settings are needed. Its exec-form Java entry point lets container
SIGTERM reach Spring directly.

## Local image and shutdown checks

The development Compose stack uses an example-only issuer, deterministic model
stub, and persistent SQLite volume. The image smoke check verifies empty
database selection, JWT protection, readiness/liveness probes, and bounded
process exit:

```powershell
python scripts/verify-image.py --image loomspan-sidecar:sc5-local
```

The Compose and verifier host ports default to `8080`, `8081`, and `9091`.
When those ports are in use, leave container ports unchanged and choose
isolated host ports:

```powershell
python scripts/verify-image.py --image loomspan-sidecar:sc5-local `
  --api-port 18080 --host-port 18081 --management-port 19091
```

With a stopped, isolated test database containing the authored quickstart
planner and routes, verify SIGTERM during nested work and at the framework
deadline:

```powershell
python scripts/verify-shutdown.py --image loomspan-sidecar:sc5-local --database-dir C:/path/to/stopped-test-data
```

The shutdown verifier mounts the source database directory read-only and copies
the database set into each isolated Compose project's disposable named volume,
with ownership `10001:10001` and owner-only read/write access. This also works
on Linux CI runners where host bind mounts retain host ownership. It prints
Compose logs before cleanup, including when startup readiness fails, and uses
ports `28080`, `28081`, and `29091` (overridable with the same port flags).
Its test callback gate holds admitted execution across SIGTERM. One case
releases it and checks completion; the other holds it past a three-second
framework budget and checks bounded exit without a forced kill.

## Durable configuration snapshots

`loomspan-sidecar.storage.database-path` defaults to `/sidecar/data/sidecar.db`.
Sidecar migrates and validates a file-backed SQLite database at startup, then
atomically creates one empty current snapshot on a new database, then activates
and records it as **published** before opening execution dispatch.
The empty content is no skill documents, the exact REST text
`targets: {}\nroutes: {}\n`, and execution YAML `loomspan: {}\n`, which uses
framework defaults. Reopening retains that snapshot; an inconsistent
initialized store or existing database without migration history fails startup.
On restart the committed current pointer is prepared and published as a new
process-local framework generation, regardless of its previous status. The
durable local UUID is preserved. Invalid content or required bookkeeping failure
aborts startup without falling back to empty or older content.

Each snapshot contains an ordered set of complete `SkillDocument` diagnostic
labels and original YAML text, plus original REST targets/routes YAML and
framework execution YAML. Labels are nonblank and exactly unique; the set may be empty. The
REST text is stored before parsing or placeholder resolution, so comments,
`${NAME}` references in `base-url`, and literal sensitive values remain exactly
as authored. Snapshots include the full framework-supported model-backed and
REST skill authoring surface. They do not include accounts, sessions, leases,
deployment URL-variable declarations or resolved environment values,
identity-provider settings, or arbitrary Spring settings. Publishable model
connections, aliases, session settings and trace persistence are included. Storage
does not sanitize or encrypt literal secrets; protect the database volume and
exported bundles accordingly.

A snapshot has a fresh stable local UUID, optional source UUID for provenance,
and increasing submission sequence for later oldest-first history pruning.
Content and identity do not change after insertion. Separate local status is
`pending`, `published`, or `failed`; `pending` means the publication outcome is
unknown. A source UUID never selects or overwrites a local snapshot. The
configuration bundle carries the same complete authored content with independent
integer `formatVersion: 2`, source identity, and informational producer
application and framework versions. The destination allocates a new local UUID;
imported status does not prove publication there. The archive layout, integrity
checks, and import/export operations are described in
[current configuration bundle](#current-configuration-bundle-format-2). Flyway
version and application version are not transfer compatibility rules.

### Durable private configuration drafts

Flyway V3 adds one saved draft per management account, with ordered complete
skill documents, REST YAML, execution YAML, base snapshot UUID, source provenance and a
monotonic revision. The saved content is in the same SQLite database and must
be included in stopped-instance backups. Leases and successful validation
proofs remain process-local, so restart requires reacquisition and revalidation.
Logout and credential changes revoke editing access without deleting content.
A successful publication deletes only its owner's saved draft after framework
activation. Other users' drafts remain stored and become stale by base UUID
comparison. Failure before activation leaves the publishing draft intact.

The pre-release V1 and V3 Flyway schemas now require execution YAML. Before
using this development build with an earlier local database, stop Sidecar and
preserve a complete backup, then reset only the development database and
recreate its accounts and drafts. Earlier format 1 bundles are rejected; create
new format 2 exports from compatible data or reauthor the content. This reset
is destructive to development data and is never a routine operation for a
deployed database.

The current database pointer records **intended production**, not the framework
generation that handled an execution or the source for an export. A publication
attempt stores complete B as pending and switches the pointer from expected A
to B in one transaction. A rejected attempt can atomically restore A and mark
B failed; B remains inspectable. A successful activation records B published
separately. On restart, Sidecar loads the committed pointer even if pending:
pending does not establish failure or success, and older pending attempts
remain unknown. An unreadable selection fails startup and requires a consistent
backup restore. An unreverted B becomes the startup selection even if A was
still running before the previous process stopped.

The internal runtime inspection reports the published UUID, intended UUID and
stored status, plus any mutation fault. A failed framework publication with a
successful revert leaves A running/intended and B failed. If revert fails, A
continues running while intended B stays pending and further mutations stop.
If B publishes but status recording fails, B continues running/intended while
its pending status remains outcome-unknown; further mutations stop. Existing
executions continue through either fault. Do not infer runtime state from the
database pointer alone until a stopped restart activates that pointer.

`loomspan-sidecar.snapshots.max-retained` is a positive snapshot count, default
`10`. Startup prunes oldest eligible snapshots after successful activation.
Pending and failed records count too. The selected snapshot and caller-supplied
IDs needed by publication or live generations are protected and count toward
the target; history may temporarily exceed it. An expired ID is absent on
lookup. The runtime service protects live generations and accepted updates,
then prunes after completed attempts. Retirement alone does not delete history;
retained execution IDs do not pin it forever.

The database directory, database file, and SQLite `-wal` and `-shm` auxiliary
files must be writable by UID/GID `10001:10001`. Use one Sidecar instance per
database on a persistent local filesystem with reliable locking. Do not share
one database across replicas or place it on a network filesystem. SQLite uses
foreign keys, WAL journaling, `synchronous=FULL`, and a 5-second busy timeout on
each connection. A competing writer can fail after that timeout; this is not a
distributed transaction service. Keep WAL files with the database. Copying only
the main database file while it is live is not a safe backup. The production
Compose guide prepares a single local volume with these permissions and locking.

### Stopped-instance backup and recovery

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
management account records, and literal sensitive values without redaction or
v1 encryption. Saved drafts are restored from this database backup; editing
leases, sessions and validation proofs do not survive a restart.
Startup activates the backup's committed pointer, including a pending selected
record. Sign in using a restored
management account and inspect **Current configuration** and **History** to
confirm the runtime snapshot and intended restart selection. The
configuration-only bundle described below excludes accounts and sessions;
the stopped-instance backup remains the full installation recovery artifact.
Protect backup access accordingly. The database
copy test verifies populated configuration and management identity recovery,
including stale destination companion removal. For an outcome-bookkeeping
fault, stop the sole instance, preserve a consistent full database-set copy,
correct the storage problem, and restart if the committed intended selection is
wanted. An unreverted A/B publication failure restarts onto B, not A. To recover
A, restore a known-good stopped full database backup and verify activation and
readiness. No online repair endpoint or live SQL procedure is provided.

## Management endpoints

The application port remains Boot's default `8080`. Health is exposed separately
on management port `9091` at `/actuator/health` and
`/actuator/health/readiness`. Other Actuator endpoints are not exposed.
Startup, readiness, and liveness probe groups are available at
`/actuator/health/readiness` and `/actuator/health/liveness`. Registration and
route validation are eager, so invalid configuration prevents startup from
becoming ready. Readiness changes to refusing traffic synchronously when the
owning application context closes; liveness remains up during ordinary long work.
Loomspan observability routes are disabled by default, so no Console operator
API is exposed. When Loomspan Console observability is enabled, its
reserved `/_loomspan/observability/v1/**` namespace remains protected by the
framework's independent API-key filter. Console keys do not authenticate `/v1`.

## Local management accounts

The browser management surface is on the application port (`/management/**` and
`/api/management/**`); port 9091 remains the health-only Actuator port. Local
accounts use server-side form-login sessions and CSRF protection. Their roles
are `viewer`, `editor`, and `admin`, with each higher role including lower-role
permissions. This release exposes account, session, private draft/lease and
configuration inspection, validation and publication APIs. A management session
cannot call `/v1/**`; an execution JWT or observability API key cannot log in to
management. Sessions are local to one process and do not survive restart.

Generate `LOOMSPAN_SIDECAR_SETUP_TOKEN` with the packaged `admin generate-setup-token`
command. The [administrator access walkthrough](admin-access.md) has copyable
PowerShell and POSIX commands. This one-time credential is distinct from the
administrator email login and chosen password. `/management/setup` accepts all
four fields and activates the account without email. Missing or invalid input
leaves setup unchanged; completed setup remains closed even if the environment
credential remains. Remove the secret and recreate the container afterward.

Configure SMTP with standard `spring.mail.*` values in deployment YAML or their
environment overrides. Set `loomspan-sidecar.management.mail-from` to a sender
address and `loomspan-sidecar.management.external-base-url` to the trusted public
HTTPS console URL; loopback HTTP is allowed for local development. Links are
built solely from that configured URL. SMTP connect, read and write waits default
to five seconds. Missing or failed delivery prevents invitations and emailed recovery;
existing logins and execution remain available. Test the deployment's SMTP and
HTTPS settings with a dedicated mailbox before relying on email actions. Never use a live
recipient in automated tests. The mail health probe is disabled so an SMTP outage
does not mark execution readiness down; email actions report their own failure.

Account usernames are email addresses. Sidecar trims surrounding whitespace and
lowercases the whole ASCII address, including the local part, for unique storage,
login and recovery. Addresses are immutable. To replace one, an admin invites
the new address, waits for its activation, then disables the old account. Pending
or disabled accounts cannot log in. Admins may invite, change roles, disable,
re-enable and resend an invitation, but cannot set someone else's password.
The last enabled, activated admin cannot be disabled or demoted. Passwords use
salted PBKDF2-HMAC-SHA256. They must have 15–128 Unicode code points (at most
512 UTF-8 bytes), with uppercase, lowercase, a digit and a non-whitespace
punctuation or symbol. Spaces are accepted but do not count as symbols. There
is no blocklist, breached-password lookup or periodic expiration.

The embedded console is served from the Sidecar JAR on the application port;
no frontend service is needed. Open `/management/login` to sign in. The landing
page explains an available, reserved, or locked first-administrator setup state;
`/management/setup` accepts the one-time credential and chosen password only until activation.
Authenticated users reach `/management/home`,
`/management/configuration/current`, `/management/configuration/edit`, and
`/management/password/change`.
Administrators also reach `/management/accounts` to invite accounts, change
roles, disable or re-enable accounts, and resend a pending password link. The
public `/management/forgot` and `/management/password/{set,reset}` forms support
emailed recovery, manually entered operator reset credentials, and invited-account password setup. Forms permit keyboard use,
password-manager autofill and paste. Forgotten-password requests
always give the same response for known and unknown addresses. Set links expire
after 24 hours and reset links after 30 minutes; each is single-use. A successful
password change or reset ends affected sessions and invalidates other links.
Sign in normally afterward. An operator with access to the stopped SQLite
volume can issue a reset credential for an existing enabled administrator with
`admin issue-password-reset`; see the [walkthrough](admin-access.md). The reset
page accepts that credential without a token-bearing URL. There is no security
question or admin-assigned replacement password.

The current-configuration page shows the complete authored skill YAML and REST
routes/targets from the **runtime-published** snapshot, including placeholders
and any literal sensitive values. Give viewer access only to people allowed to
read those values. The separately labeled intended ID/status are the database
selection for restart, not proof of what handles executions now. Authored text
is displayed as text, never interpreted as HTML. Refresh is explicit; passive
reads do not extend the idle login. The browser reports deliberate interaction
at most once every 30 seconds. If an invitation reports a delivery failure,
refresh the account list: it may contain a pending account. Correct SMTP and
resend the link. A used or expired set/reset credential requires a fresh
invitation, emailed reset link, or local operator reset credential as applicable.
Role changes, disables, resets and password changes invalidate affected sessions;
sign in again before continuing. Viewer/editor/admin checks and CSRF are enforced
by the server even if a stale browser page still shows an action.

The edit page displays the runtime configuration to every management role.
Editors and admins can acquire the sole editing lease and change complete YAML
skill documents (with diagnostic source labels), complete REST targets/routes
YAML and framework execution YAML. Add or remove a skill document with the adjacent controls;
REST targets and routes remain one text document. URL placeholders such as
`${REST_BASE_URL}` are kept as authored text. Execution YAML accepts
`loomspan.connections`, `models`, `session` and `execution-trace.persistence`.
Credential fields use external `api-key-ref`, `gemini.credentials-ref` or
`header-refs`; provision those Spring Environment properties outside the draft.
Java skills, process settings and deployment destination bindings remain outside this editor.

Edits save automatically to the user's durable draft after a short pause.
The draft status distinguishes unsent local text, a saved revision and the
published runtime. A failed save keeps the local text visible for explicit
retry. Validation runs on the exact saved revision and shows labelled issues;
warnings alone permit publication. Publish becomes available only for the
current saved, successfully validated revision. Successful publication changes
the runtime without restart, deletes only that user's draft and releases its
lease. Other users' saved drafts remain readable but stale.

The current editing session holds the lease. Another session of the same user
can explicitly take control; this rotates the server grant and first reads the
latest saved revision. Administrator takeover does not expose or delete the
previous holder's saved draft. A lost lease stops writes, while unsent local
text remains visible and is never automatically resubmitted. Logout, expiry,
account changes and restart revoke grants and validation, while saved content
remains in SQLite. After taking control again, compare retained local text with
the server-saved draft and choose **Resume editing unsaved local text** before
changing it; reacquisition itself does not make retained text writable.
A changed published base keeps a draft visible as stale;
review all fields, submit a complete current-base reconciliation, and validate
again. Typing, paste, clicks, scrolling and explicit Continue editing report
user activity at most once per 30 seconds. Background polling, save and
validation do not renew deadlines.

If Publish is rejected, inspect the displayed runtime, intended and fault
state before making another explicit decision. An activation failure may leave
the old runtime active; bookkeeping failure can occur after the new runtime
became active. A connection loss leaves the outcome unknown and never triggers
an automatic second Publish. The current page remains available to inspect
the runtime when configuration mutations are faulted.

JSON clients obtain a CSRF token from a session page or the authenticated
`GET /api/management/session` response and send it as `X-CSRF-TOKEN` on unsafe
requests. The login page and all other forms include a hidden CSRF value. The
session endpoint returns `id`, normalized `email`, `role`, effective
`permissions`, `csrfToken`, `sessionIdleTimeoutSeconds`, and
`editLeaseTimeoutSeconds`. `POST /api/management/session/activity` records
an intentional user action and extends the default 30-minute idle deadline only
when at least 30 seconds have passed since the previous accepted report;
polling the session endpoint does not. `POST /api/management/logout` ends the
session. `POST /api/management/password/change` takes `currentPassword` and
`newPassword`. Anonymous `POST /api/management/setup` takes `credential`, `email`,
`password`, and `confirmation`; `POST /api/management/password/forgot` takes `email`; and
`POST /api/management/password/{set,reset}` takes `token` and `password`.
Admin-only `GET/POST /api/management/accounts` lists accounts or invites one
with `email` and `role`; `PATCH /api/management/accounts/{id}` accepts `role`
and/or `enabled`, and `POST …/{id}/resend-invite` resends to a pending user.
Responses expose account id, email, role, enabled and activated state, never
password hashes or tokens. The session cookie is HttpOnly, SameSite=Lax and
Secure by default; use HTTPS in deployment. Set
`LOOMSPAN_SIDECAR_SECURE_COOKIE=false` only for a local HTTP test. Browser and
management API responses use `Cache-Control: no-store` and
`Referrer-Policy: no-referrer`.

### Shared durable editing API

The console and remote clients use this single management contract. All
calls require an authenticated management user; unsafe browser-session methods
require the browser CSRF token. Scoped personal tokens use bearer authentication
without browser cookies. Editors and administrators mutate; viewers may read their
own saved draft. The server derives the owner from authentication. Client
labels and request identifiers grant no access.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET /api/management/editing` | None | Application lease `held`, `mine`, `sameUser`, display-only `holderLabel`, `expiresAt`, and `editingSessionId` only for the holder's login session. Clients compare that ID with their own grant to detect a handoff between tabs sharing a login. |
| `GET /api/management/editing/draft` | None | Only the caller's saved draft, or 404. |
| `POST /api/management/editing/lease` | `{"label":"Console"}` | Acquire a free lease and create a draft from the published snapshot if none exists. |
| `POST /api/management/editing/lease/handoff` | `{"label":"Other client"}` | Explicitly take control from another session of the same user; rotate the grant. |
| `POST /api/management/editing/lease/takeover` | `{"label":"Administrator"}` | Admin only; displace a holder while preserving that user's private draft. |
| `POST /api/management/editing/lease/renew` | `editingSessionId`, `generation` | Explicitly renew a live lease. |
| `POST /api/management/editing/lease/release` | `editingSessionId`, `generation` | Release editing control and validation proof, retaining saved content. |
| `PUT /api/management/editing/draft` | Capability, `draftId`, `revision`, `baseSnapshotId`, complete `skillDocuments`, `restRoutesYaml` and `executionConfigurationYaml` | Replace the saved content at the current base; increment revision and clear validation. |
| `POST /api/management/editing/draft/reconcile` | Same complete body, with the current published `baseSnapshotId` | Explicitly submit a full configuration against the current base after it changes. |
| `POST /api/management/editing/draft/validate` | Capability, `draftId`, `revision`, `baseSnapshotId` | Validate the exact saved revision without publishing. |
| `DELETE /api/management/editing/draft` | Capability, `draftId`, `revision`, `baseSnapshotId` | Delete the caller's saved draft and release control. |

The capability is the server-issued `editingSessionId` and `generation`,
returned by acquisition or handoff with `expiresAt` and the current draft.
The draft has `draftId`, positive `revision`, `baseSnapshotId`, optional
`sourceSnapshotId`, complete `configuration`, `stale`, and an in-process
`validation` result or null. Identifiers are concurrency markers, never
bearer authority. Every mutation checks the live account and originating login or token, lease
holder, generation, draft ID, revision and base. A delayed old-holder write,
foreign user, stale revision, or obsolete base receives 409 without changing
saved content. Malformed requests receive 400, missing/invalid credentials 401,
and role, token-preset, or browser CSRF denial 403.

There is one saved draft per account in SQLite. It survives logout, credential
expiry, account changes and restart, including ordered skill documents, REST
YAML and execution YAML. A new authorized client may read its owner's draft but must acquire a new lease and
validate again. Validation and lease state are memory-only. The application
lease defaults to 15 minutes and the login idle limit to 30 minutes. Polling,
reads, saves, validation and model work do not renew either deadline. Only
explicit user activity reported through `/api/management/session/activity`
and explicit lease renewal extend them. The console checks saved revisions
while read-only; it keeps unsent local text separate and never submits it on
handoff or ownership loss.

When `stale` is true, compare the saved draft with the current published
configuration, edit the complete desired result, submit it to `/draft/reconcile`
with the current base, then validate its new revision and publish. The server
never merges or replaces stale content automatically. An unchanged saved
configuration cannot be submitted solely to relabel its base. It enforces a complete
current-base submission but cannot judge the semantic quality of the user's
reconciliation.

### Protected configuration API

Management viewers, editors and admins can inspect literal authored snapshot
content, including sensitive values in pending or failed submissions. Protect
viewer accounts accordingly. Every response is private and `Cache-Control:
no-store`; an execution JWT does not grant access. Browser mutations require a
management session and CSRF token; scoped bearer tokens use the same API.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET /api/management/configuration/current` | None | `published` full runtime snapshot, `intendedId`, `intendedStatus`, and `mutationFault`. |
| `GET /api/management/configuration/export` | None | Format 1 ZIP of the captured runtime-published configuration; authenticated viewer, editor, or admin. 413 when the v1 size limits are exceeded; 503 when runtime state is unavailable. |
| `GET /api/management/configuration/history` | None | Retained full submitted snapshots in ascending `submissionSequence`. |
| `GET /api/management/configuration/history/{localId}` | None | Full retained snapshot or 404 after pruning. |
| `POST /api/management/configuration/rollback/{localId}/review` | Empty body; editor/admin and CSRF | Read-only source summary and destination validation. |
| `POST /api/management/configuration/rollback/{localId}/load` | Capability, draft ID/revision, current base ID | Load the retained source into the caller's saved draft; no publication. |
| `POST /api/management/configuration/publish` | Capability, draft ID/revision/base ID | Publish only the exact successfully validated saved revision, then return the full snapshot. |

A full snapshot contains `localId`, nullable `sourceId`, `submissionSequence`,
`status`, and `configuration` with authored `skillDocuments`, `restRoutesYaml`
and `executionConfigurationYaml`.
The framework's validation returns `successful` and issues with `severity`
(`ERROR` or `WARNING`), `sourceLabel`, optional `skillName` and `location`, and
`message`. Warning-only results can be published; an error cannot. Every save, including identical content, advances the revision and clears validation.
Validation is advisory: Publish prepares and stages afresh. Neither validation
nor inspection renews login or lease inactivity deadlines.

### Current configuration bundle (format 2)

Use **Download current configuration** on the current-configuration page, or
`GET /api/management/configuration/export` with a management session or Read-or-higher token. The ZIP is
a configuration-only backup and promotion artifact. It carries the complete
authored skill YAML, source labels, REST routes/targets and execution YAML from the snapshot
running when export begins. An active empty configuration yields an empty skill
list, the authored empty REST document and `loomspan: {}\n`. If no runtime snapshot exists, export
is unavailable. The intended database pointer, retained history, private drafts,
accounts, sessions, leases, resolved environment values, URL allowlists, SMTP
settings, and SSL bundles are excluded. Editors and administrators can import
this bundle through **Import configuration** or the management API. For full
installation recovery, use the stopped-instance database backup procedure above.

Import loads the bundle's complete authored skills, routes, targets and execution settings into
the caller's saved draft. `POST /api/management/configuration/import/review`
accepts one `bundle` multipart field and returns producer metadata and
validation feedback without changing state. `POST
/api/management/configuration/import/load` accepts the same bundle plus
`editingSessionId`, `generation`, `draftId`, `revision`, and the current
`baseSnapshotId`. The caller must hold the lease; the load advances the saved
revision and preserves the bundle source UUID as provenance. The runtime is
unchanged until the caller validates and publishes through the shared editing
contract. A failed upload or load leaves the prior draft intact. The old direct
confirmation endpoint has been removed.

The destination must configure the REST URL variable allowlist, corresponding
process environment values, and any SSL bundles required by the authored
routes. Whole-value `${NAME}` base URLs resolve through this destination's exact
allowlist and environment; absent, undeclared, blank, or invalid bindings reject
before loading. Literal URLs are supported. Protect ZIPs as sensitive data.

The inclusive format limits are 100 MiB compressed, 512 MiB expanded, and
10,000 ZIP entries. Uploads spool to temporary files and are removed after each
request; no import job or durable uploaded bundle is retained. After a known
preparation, commit, activation, pointer-reversion, or bookkeeping failure,
inspect current runtime and local history before another attempt. A disconnected
browser has an unknown outcome; do not retry blindly. `PENDING` also means the
recorded outcome is unknown. A reversion failure stops configuration mutations
for operator recovery. A bookkeeping failure after activation does not undo the
running import. Restart follows the committed intended pointer, and admitted
execution continues with its original generation.

The ZIP contains exactly `manifest.json`, `rest.json`, `execution.json`, and one YAML file per
skill under `skills/`. All entry names are fixed or generated, never taken from
source labels. The old `src/test/resources/fixtures/bundles/v1/` fixture remains
only to prove that format 1 is rejected. JSON and YAML entry bytes are UTF-8. `rest.json` is
`{"restRoutesYaml":"<exact authored YAML>"}`; it retains comments, sensitive
literals and unresolved placeholders. `execution.json` is
`{"executionConfigurationYaml":"<exact authored YAML>"}` and contains reference
names, never resolved credential values. The manifest structure is:

```json
{
  "formatVersion": 2,
  "sourceSnapshotId": "<runtime snapshot local UUID>",
  "producer": {
    "sidecarVersion": "<informational version>",
    "frameworkVersion": "<informational version>"
  },
  "payloads": [
    {"path": "rest.json", "sha256": "<lowercase SHA-256 of exact entry bytes>"},
    {"path": "execution.json", "sha256": "<lowercase SHA-256 of exact entry bytes>"},
    {"path": "skills/00000.yaml", "sha256": "<lowercase SHA-256>", "sourceLabel": "<original label>"}
  ]
}
```

`payloads` inventories every entry except the manifest exactly once. Skill
paths use five decimal digits starting at zero with no gaps; the generated
ordinal defines the original skill order. The parser checks the supported format,
inventory, entry names, duplicate names/labels, SHA-256 digests, ZIP CRC and
sizes, UTF-8, JSON shape, and YAML mapping roots. Informational producer
versions do not determine compatibility. It performs no filesystem extraction,
framework validation, import, or activation. Integrity checks detect accidental
or deliberate changes to a bundle only when compared with a trusted digest;
they do not authenticate its sender. Protect downloaded ZIPs as sensitive data.

V1 limits are inclusive: 100 MiB compressed ZIP input, 512 MiB total expanded
entry bytes, and 10,000 ZIP entries (1 MiB = 1,048,576 bytes). The same bounds
apply to export. An oversize export fails rather than returning a partial ZIP.

### Console history and outcome inspection

After signing in, every viewer, editor, and administrator can open **History**
from the shared management navigation. The list follows the server's
authoritative oldest-first submission order and shows each durable local UUID,
optional source UUID, submission sequence, recorded status, and whether its
UUID matches the separately inspected runtime-published snapshot. Selecting a
row loads that exact UUID through the detail API before displaying complete
skill and REST YAML. Authored placeholders, markup-like text, and literal
sensitive values are preserved and rendered as text; protect all management
accounts accordingly.

Editors and administrators can select any retained snapshot, including a
failed, pending or current one, and review its content and destination
validation. The explicit Load action copies that complete retained content
into the current holder's own saved draft at an expected revision and current
base. Its local UUID becomes source provenance. The holder then validates and
publishes through the same endpoint as ordinary editing. Loading does not
activate the snapshot, bypass a lease, or erase another user's draft. A pruned
source returns 404. Retained history is visible to all authenticated management
users, while saved drafts are private to their owners, including against an
administrator who takes over the lease.

`PENDING` always means the recorded outcome is unknown, including when that
snapshot is currently running. `PUBLISHED` and `FAILED` are recorded bookkeeping
states; the console does not reconstruct an unstored failure stage. Runtime
identity and the intended SQLite restart selection are displayed separately.
Equality does not upgrade a pending record, while a mismatch means a restart
will follow the intended selection and does not itself prove an earlier
operation's outcome.

Retention can remove an ID between list and detail reads. The console then
clears any prior detail, reports that the snapshot expired, refreshes current
state and the retained list, and offers the Current configuration link. It does
not pin history or create a durable publication job. When a mutation fault is
present, acquisition, save, editing activity renewal, validation, and
publication are blocked; inspection, release or discard of private editing
state, and administrator account management remain available. Follow the
protected recovery-guidance link and the stopped-instance procedure above when
operator intervention is required. Import and rollback are available to editors
and administrators only while mutations are healthy. Retention defaults to ten
snapshots, including failed attempts and current production, with protection
for the selected snapshot and live generations. Older rollback sources can be
pruned; local history is not a guaranteed recovery archive.

The server checks the live account, session, editing grant, base snapshot and exact
validated saved revision after the publication lock becomes available. Conflicts
return 409 with `grant_conflict`, `revision_conflict`, `base_conflict`,
`validation_required`, `account_conflict`, `session_conflict`, `role_conflict`, or generic
`editing_conflict`. Authentication and role/CSRF failures
return 401 or 403. Malformed input returns 400. Missing or pruned history
returns 404 with `history_not_found`. A configuration fault or storage failure returns 503. A
publication failure after admission returns a `code`, nonsecret `error`, and
runtime/intended IDs, intended status and mutation fault where available.
Codes distinguish `preparation_failed`, `commit_failed`, `activation_failed`
(revert succeeded), `revert_failed`, `outcome_recording_failed` (activation
succeeded but bookkeeping is unknown), and `history_pruning_failed`.

An HTTP disconnect does not cancel an accepted operation, but completion is not
guaranteed across process crash. On reconnect, inspect current and history
before considering another update. `published` is the configuration currently
running, while `intendedId` is the SQLite restart selection. `PENDING` means
the recorded outcome is unknown; do not infer success or failure from it. If
`mutationFault` is present, configuration mutations stop while inspection,
private-state cleanup and account administration remain available. Preserve
the database and investigate the stage before retrying; the existing stopped
full-database-backup recovery procedure applies when runtime and intended
selection disagree. The browser authoring, automatic validation, and retained
history and rollback workflows use these same contracts.

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

<!-- configuration-reference:end -->

The route schema and `${NAME}` bindings are described in
[REST skill routes](../agent-skills/loomspan-sidecar-authoring/references/integration.md#rest-skill-routes). Framework startup discovery uses the
packaged empty source; framework shutdown remains
`loomspan.shutdown.timeout`; inbound server and outbound mTLS configuration
remains under standard `server.ssl.*` and `spring.ssl.bundle.*` namespaces. The
image honors those standard Boot environment and command-line overrides.

The production verifier uses labeled editing sessions and exact draft revisions.
Import and rollback checks review and load content into the private draft, assert
that the published snapshot is unchanged, then explicitly validate and publish.
It also checks stale-base rejection and lease release after publication.

## Phase 6 local acceptance (2026-09-19)

Local integration was checked against the developer-installed
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.6-SNAPSHOT` with these exact
commands from the repository root:

```powershell
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
python scripts/verify-production.py --image loomspan-sidecar:sc5-local
```

`verify` and `package` passed with zero test failures or errors. The package
run executed 260 tests; three Compose-only browser methods skipped in ordinary
Maven runs and ran through the deployment verifier instead. The image build
passed. The complete verifier passed with disposable HTTPS Compose projects,
test-only `.test` mail capture, and local JWT/model/REST fixtures. It checked
emailed setup, invitations, recovery, Secure cookies, role and CSRF isolation,
management lock and SMTP outage behavior, concurrent browser editing and stale
grant rejection, authored publication and REST/model execution, two independent
database stores with destination URL bindings, invalid import preservation,
fresh import/rollback identities, and selection after recreation. It also
showed startup failure with an invalid selected pointer, replacement from a
stopped full database file set with stale WAL/SHM removal and UID/GID 10001,
restored accounts/current/history and REST/model execution. The embedded
shutdown verifier reported admitted-work completion in 2.32 seconds and
framework cutoff in 4.46 seconds, both with SIGTERM exit 143.

The earlier deferred startup and publication boundary checks are covered by
Sidecar's `RuntimeConfigurationIntegrationTest` and
`RuntimeConfigurationRecoveryIntegrationTest`, including before-commit,
committed pending selection, reversion, status failure, and activation before
dispatch. `ManagementConfigurationHttpIntegrationTest` covers accepted updates
after client disconnect/logout. `ExecutionConfigurationCorrelationIntegrationTest`
and `RestGenerationIntegrationTest` cover captured snapshot IDs and retained
REST resources across publication; the Compose shutdown check covers the
existing completion/cutoff budget. These Sidecar tests ran in the Maven suite;
framework-only tests are not used as Sidecar acceptance evidence.

Use a stopped full database backup for installation recovery, a configuration
ZIP for authored-content transfer between instances, and retained-history
rollback to load a retained snapshot into a draft for later publication. ZIP and rollback do
not restore management accounts or repair an unusable database. Protect the
database backup and exported ZIP because authored values may contain secrets.

Local integration verification against the installed beta 6 snapshot precedes framework beta 6 publication.
After framework publication, switch the Sidecar dependency to released
`1.0.0-beta.6`, then run the final Sidecar build/tests and commit. Hosted CI must
resolve that published artifact and pass before the Sidecar release. None of
those release gates, publication steps, commits, or tags are claimed by this
local acceptance run.

## Dependency and release boundary

Production and test code may use Loomspan Java types only from `ai.loomspan.api`.

The Guava dependency override selects `33.4.0-jre` for Sidecar's Java 21 runtime.
Without it, the framework's Google GenAI / Google Auth dependency chain selects
`33.4.0-android`. The override changes the runtime flavor at the same version.
Error Prone annotations use the transitive dependency version; Sidecar does not
require a separate annotation-version override.

Push and pull-request CI is prepared to override the dependency with published
`1.0.0-beta.6` through job-level `MAVEN_ARGS`, including Maven subprocesses
launched by the production Compose browser verifier. Local verification keeps
the POM's snapshot default. Hosted verification is deferred until that artifact exists on
Maven Central. The delivery order is local Sidecar integration against the
snapshot, framework release checks and publication, then Sidecar verification
against the released artifact. This project does not build framework source in
its own build or CI.

Release tags are exactly `v<project-version>`. The guarded workflow requires a
non-SNAPSHOT Sidecar version and framework `1.0.0-beta.6`, reruns Maven and image
verification, then publishes an immutable GHCR version tag and a GitHub release
containing the executable JAR, a reproducible ZIP archive, and SHA-256 files.
Local preparation is intentionally nonpublishing:

```powershell
python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.1 --loomspan-version 1.0.0-beta.6 --tag v1.0.0-beta.1
```

Sidecar development is on `1.0.0-beta.1-SNAPSHOT` for the
[management console roadmap](../ai/thoughts/phases/2026-09-16-management-console-roadmap.md).
The framework dependency remains `1.0.0-beta.6-SNAPSHOT`. Final dependency changes,
release tags and publication follow the separate release verification workflow;
the framework must be published and resolvable before final Sidecar verification.
Sidecar and framework versions advance independently. The first planned Sidecar
release is `1.0.0-beta.1`, tagged `v1.0.0-beta.1`, bundling framework
`1.0.0-beta.6`. Historical acceptance evidence retains the versions tested then.

## Management personal tokens

Remote authoring uses the same `/api/management/**` configuration and editing
contract as the console. Require HTTPS for remote access and put the one-time
personal token in the `Authorization: Bearer` header. Never put it in a URL,
request body, command argument or repository file. The user must inject the
displayed secret into the client's environment; the stored digest cannot
supply it later. The console at `/management/personal-tokens` issues and
revokes only the current user's tokens. No OAuth server or separate management
API is involved.

Issuance is limited to five per account per hour and 20 active tokens. Failed
bearer authentication is limited to 20 attempts per source IP per 15 minutes,
with a bounded in-memory counter and fail-closed capacity. Headers over 128
characters are rejected. Tokens expire after seven days by default and at
most 30 days after issuance. They are never refreshed. Structured management
audit events identify action, actor account ID, known token ID and outcome;
configuration changes also record candidate/base/published IDs where
available. Audit events must never contain raw tokens or Authorization headers.
Management tokens cannot call `/v1/**`; execution JWTs cannot call management.
The remote authoring package adds no persistent schema or data migration; no
development-data reset is required for this ticket. Existing saved drafts
remain available to their owning accounts across token loss and restart.
