# Loomspan Sidecar

Loomspan Sidecar is a Java 21 / Spring Boot 4.1 application that activates
database-selected Loomspan YAML skills and exposes an authenticated asynchronous
execution API. It provides verified JWT identity, owner-scoped polling,
bounded in-memory workers and retention, prompt shutdown gates, and a generic
bounded outbound REST handler for managed REST skills. The supported container
runs as a fixed non-root user and consumes environment configuration and a
writable `/sidecar/data` volume.

## Build locally

Local development uses
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.5-SNAPSHOT` installed from
the local checkout at `C:\opendev\code\loomspan-framework` (initial baseline
`385729a254261de128df491505acd8898cc0a021`). The developer runs a new framework
install after framework changes so the installed library matches source and
documentation lookups in that directory. Then rerun affected Sidecar checks:

```powershell
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd spring-boot:run
```

On POSIX systems use `./mvnw`. Tests use temporary local files and loopback-only
configuration; they do not need a model provider account.

## Container and deterministic quick start

Build the executable JAR first, then the runtime-only image. The Docker build
never resolves Maven dependencies or checks out framework source:

```powershell
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
```

The image uses UID/GID `10001:10001`, exposes application port 8080 and
management port 9091, and starts Java with
`-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0
-XX:+ExitOnOutOfMemoryError`. Replace `JAVA_TOOL_OPTIONS` when deployment-specific
heap or GC settings are needed. The Java entry point is exec-form so container
SIGTERM reaches Spring directly.

The Compose example runs an example-only issuer, a deterministic model stub,
and Sidecar with a persistent local SQLite volume. A new volume activates an
empty snapshot. The checked-in skill and route YAML are authored examples for
later management publication; they are not mounted as an alternate runtime
source. The checked-in private key is intentionally public test material and
must never be used outside this example.

```powershell
$env:SIDECAR_IMAGE = "loomspan-sidecar:sc5-local"
docker compose -f examples/quickstart/compose.yaml up -d --build
$token = (Invoke-RestMethod http://localhost:8081/token).access_token
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod http://localhost:8080/v1/skills -Headers $headers # [] on a new volume
docker compose -f examples/quickstart/compose.yaml down
```

The image smoke check verifies the empty database selection, JWT protection,
readiness/liveness probes, bounded process exit, and Kubernetes example structure:

```powershell
python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes
```

The Compose and verifier host ports default to `8080`, `8081`, and `9091`.
When those host ports are already in use, leave the container ports unchanged
and run the same verification on isolated alternatives, for example:

```powershell
python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes `
  --api-port 18080 --host-port 18081 --management-port 19091
```

With a stopped, isolated test database already containing the authored
quickstart planner and routes, verify SIGTERM during active nested work and at
the framework deadline with:

```powershell
python scripts/verify-shutdown.py --image loomspan-sidecar:sc5-local --database-dir C:/path/to/stopped-test-data
```

This copies the stopped test database into isolated Compose projects and uses
ports `28080`, `28081`, and `29091` (overridable with the same port flags).
A verification-only callback gate holds
an admitted execution across SIGTERM. One case releases it and checks that the
remaining callback and final model response finish; the other holds it past a
three-second framework budget and checks bounded exit without a forced kill.

## Durable configuration snapshots

`loomspan-sidecar.storage.database-path` defaults to `/sidecar/data/sidecar.db`.
Sidecar migrates and validates a file-backed SQLite database at startup, then
atomically creates one empty current snapshot on a new database, then activates
and records it as **published** before opening execution dispatch.
The empty content is no skill documents and the exact REST text
`targets: {}\nroutes: {}\n`. Reopening retains that snapshot; an inconsistent
initialized store or existing database without migration history fails startup.
On restart the committed current pointer is prepared and published as a new
process-local framework generation, regardless of its previous status. The
durable local UUID is preserved. Invalid content or required bookkeeping failure
aborts startup without falling back to empty or older content.

Each snapshot contains an ordered set of complete `SkillDocument` diagnostic
labels and original YAML text, plus one original REST targets/routes YAML
document. Labels are nonblank and exactly unique; the set may be empty. The
REST text is stored before parsing or placeholder resolution, so comments,
`${NAME}` references in `base-url`, and literal sensitive values remain exactly
as authored. Snapshots include the full framework-supported model-backed and
REST skill authoring surface. They do not include accounts, sessions, leases,
deployment URL-variable declarations or environment values, model-connection
settings, identity-provider settings, or arbitrary Spring settings. Storage
does not sanitize or encrypt literal secrets; protect the database volume and
any future exported bundle accordingly.

A snapshot has a fresh stable local UUID, optional source UUID for provenance,
and increasing submission sequence for later oldest-first history pruning.
Content and identity do not change after insertion. Separate local status is
`pending`, `published`, or `failed`; `pending` means the publication outcome is
unknown. A source UUID never selects or overwrites a local snapshot. A future
transfer bundle will carry the same complete authored content with independent
integer `formatVersion: 1`, source identity, and informational producer
application and framework versions. The destination will allocate a new local
UUID; imported status will not prove publication there. Phase 5 defines the
archive layout, integrity checks, and import/export operations. Flyway version
and application version are not transfer compatibility rules.

### In-memory configuration drafts

`ConfigurationDraft` starts from a runtime-published `ConfigurationSnapshot`
explicitly supplied by its caller. It retains that snapshot's local UUID as the
base and holds the complete authored skill documents and REST YAML. The model
does not consult the database's intended-production pointer. Each draft is an
independent, ephemeral copy; edits replace the complete content without parsing
YAML or resolving placeholders, so temporarily invalid text and literal values
are retained. A frozen candidate keeps that base identity and authored content
unchanged after later draft edits or loss of the draft reference.

An edit clears the draft's validation result, including when replacement text is
identical. A caller can attach a validation result only to the precise current
frozen candidate; late results for older candidates and results from another
draft are rejected. Validation issues retain severity, source label, skill name
and available field location. The runtime service checks the complete candidate
with the public framework validation API, checks REST routes and temporary
clients, then releases validation-only resources without allocating a generation.
Ordinary Publish requires that exact
successful result and serializes fresh preparation, staging, durable commit,
framework publication, and outcome recording. The management editing API now
stores one private draft per authenticated server session. It copies the complete
runtime-published snapshot, even while SQLite points at a pending selection.
Only the session holding the instance lease and its selected browser tab may
replace content. Successful framework publication clears prior drafts and grants,
including when later status bookkeeping fails. Rejected publication preserves
old-base editing state. Saving and validating do not activate a draft.

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
the main database file while it is live is not a safe backup. The Kubernetes sample expects a
PVC named `loomspan-sidecar-data` that supports these permissions and locking.

### Stopped-instance backup and recovery

The protected console links this procedure from **Configuration recovery
guidance**. That page is explanatory and read-only: it does not execute SQL,
retry or cancel publication, or provide import, export, or rollback controls.

Stop the sole Sidecar instance and wait for process exit before either copy.
For the quickstart use `docker compose -f examples/quickstart/compose.yaml stop sidecar`;
for the Kubernetes sample use `kubectl scale deployment/example-with-loomspan-sidecar --replicas=0`
and wait until its pod has terminated. On the host or maintenance container with
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
chown 10001:10001 "$DATA_DIR" "$DATA_DIR"/sidecar.db*
```

Restart with `docker compose -f examples/quickstart/compose.yaml start sidecar`
or `kubectl scale deployment/example-with-loomspan-sidecar --replicas=1`. Verify startup and
readiness. Restore rolls current selection, statuses, and history back to the
backup point. This full installation database backup includes authored YAML and
literal sensitive values without redaction or v1 encryption. Account/session
transfer is not introduced. Protect backup access accordingly. The database
copy test verifies both storage and runtime recovery. For an outcome-bookkeeping
fault, stop the sole instance, preserve a consistent full database-set copy,
correct the storage problem, and restart if the committed intended selection is
wanted. An unreverted A/B publication failure restarts onto B, not A. To recover
A, restore a known-good stopped full database backup and verify activation and
readiness. No online repair endpoint or live SQL procedure is provided.

## Runtime configuration

The database-selected snapshot is the only runtime skill and REST route source.
The framework's documented `loomspan.skills.locations` points to a packaged
empty directory so the initial catalog is empty. Sidecar checks that baseline
before publishing the selected snapshot. A new database starts with no skills.
Authored skill YAML and REST text remain unchanged in durable snapshots;
resolved deployment values are never written over references.

Each model-backed manifest names a model. Configure its connection and provider
model explicitly; the manifest does not create them:

```yaml
loomspan:
  connections:
    primary:
      driver: openai
      base-url: ${MODEL_BASE_URL}
      api-key: ${MODEL_API_KEY}
  models:
    primary:
      connection: primary
      provider-model: ${MODEL_NAME}
```

Keep credentials in environment variables. Model connection and URL allowlist
changes require restart; a complete snapshot publication changes skills and
REST routes without restart. Successful framework publication invalidates
prior-base drafts and leases under the runtime transition gate. Publish rechecks
live account, session, grant, candidate, validation and base after waiting for
the publication lock. Phase 5 import and rollback must reuse this
publication path and assign fresh local IDs with source provenance.

## REST skill routes

Exactly one production handler is always present. Each complete snapshot has
one authored route document. When no REST skills exist, use:

```yaml
targets: {}
routes: {}
```

Each REST skill must have one exact, case-sensitive route and every route must name
a registered REST skill and target. Invalid, unknown, duplicate, or incomplete
configuration fails validation or startup without contacting any target. A complete example is:

```yaml
targets:
  expenses:
    base-url: https://expenses.internal/api
    auth:
      mode: caller-passthrough
    ssl-bundle: expenses-mtls
    connect-timeout: 5s
    read-timeout: 30s
    max-response-size: 1MB
  customers:
    base-url: https://customers.internal/v1
    auth:
      mode: static
      headers:
        X-Service-Key: ${CUSTOMER_SERVICE_KEY}
    connect-timeout: 5s
    read-timeout: 30s
    max-response-size: 1MB
routes:
  expenseLookup:
    target: expenses
    method: GET
    path: /expenses/{category}
  customerLookup:
    target: customers
    method: POST
    path: /customers/{id}
```

`base-url` accepts a literal HTTP(S) URL or a whole-value `${NAME}` reference.
The name must appear in `loomspan-sidecar.url-variables`; only the process
environment supplies its value. Missing, blank, composed, defaulted, and invalid
URLs fail validation or startup. Unused allowlist entries require no value.
Other string fields and names retain required Spring `${NAME}` placeholder
behavior. Resolved secret values are omitted from diagnostics. Authentication modes are `none`,
`static`, and `caller-passthrough`; configured headers are accepted only for
`static`. Invocation input never becomes a header. Every request sends
`Accept: application/json, text/*`.

GET and POST are the only methods. `{name}` occupies a complete path segment and
consumes the same-named input. GET encodes remaining non-null string, finite-number,
and boolean values as query parameters. POST sends the remaining JSON-compatible
object, preserving nulls and nested arrays/objects and sending `{}` when all fields
were consumed. Resource, file, stream, non-string object key, nonfinite number, and
other non-JSON values fail before I/O. Segment encoding and base-path confinement
prevent input from replacing the configured authority or traversing above its base.

Each framework generation owns matching routes and isolated Apache HTTP clients.
Old generations retain them until public retirement establishes that admitted
and nested work has finished. Shutdown retains clients through the framework's
completion or cutoff and closes them without another drain period. Redirects, automatic retries, and
automatic cookie storage/replay are disabled; caller sessions are not shared across
executions. Literal plus signs in GET query names and values are percent-encoded.
Connect/read timeouts and the byte response cap apply per target; equality
with the cap succeeds and excess is stopped while streaming. Any bodyless 2xx returns
`""`. A nonempty successful response must be `application/json`, a structured
`+json` type, or `text/*`; its declared charset is honored and UTF-8 is used when
none is declared. JSON is returned as unchanged text, not parsed. Non-2xx and
transport/media/size failures become bounded, body-free skill diagnostics. Early
failures discard the connection before cleanup can drain the unread response.

Outbound TLS uses standard Boot SSL bundles, including client keys for mTLS; bundle
configuration remains under `spring.ssl.bundle.*`, for example:

```yaml
spring:
  ssl:
    bundle:
      jks:
        expenses-mtls:
          keystore:
            location: file:/sidecar/tls/client.p12
            password: ${CLIENT_STORE_PASSWORD}
            type: PKCS12
          truststore:
            location: file:/sidecar/tls/trust.p12
            password: ${TRUST_STORE_PASSWORD}
            type: PKCS12
```

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

## JWT authentication

Every `/v1/**` request requires a bearer access token. Sidecar validates its
signature, issuer, audience, expiration and lifetime and requires nonblank `iss`
and `sub` claims. It supports issuer discovery (the default), an explicit JWKS
URL, or an RSA public key; configure at most one explicit key source:

```yaml
loomspan-sidecar:
  auth:
    jwt:
      issuer-uri: https://identity.example.com
      audience: loomspan-sidecar
      # jwk-set-uri: https://identity.example.com/.well-known/jwks.json
      # public-key-location: file:/sidecar/keys/access-token.pem
      clock-skew: 60s
      roles-claim: roles
      role-prefix: ROLE_
```

Roles are read from the configured trusted claim. The prefix is also applied to
Loomspan's `rbac_roles` checks. Execution authentication has no local user/role table and Sidecar does not mint,
refresh or exchange tokens. A backend using browser sessions must supply a
backend-issued access token suitable for both Sidecar's and any callback target's
audience checks; cookies and OIDC ID tokens are not assumed to be API credentials.
Queue time consumes token lifetime. Once admitted, local work keeps the identity
and roles already verified even if the token expires. A `caller-passthrough` target
receives that original token unchanged and must independently verify its signature,
issuer, suitable audience, lifetime, subject, and roles. Sidecar does not recheck at
worker handoff and does not refresh, mint, or exchange the token, so size its lifetime
for queueing and callback time or expect the callback to reject it as a skill failure.

Standard Boot TLS configuration can require inbound mTLS, for example
`server.ssl.client-auth=need`. This protects the transport; Sidecar does not map
X.509 certificates to execution identities.

## Local management accounts

The browser management surface is on the application port (`/management/**` and
`/api/management/**`); port 9091 remains the health-only Actuator port. Local
accounts use server-side form-login sessions and CSRF protection. Their roles
are `viewer`, `editor`, and `admin`, with each higher role including lower-role
permissions. This release exposes account, session, private draft/lease and
configuration inspection, validation and publication APIs. A management session
cannot call `/v1/**`; an execution JWT or observability API key cannot log in to
management. Sessions are local to one process and do not survive restart.

Set `LOOMSPAN_SIDECAR_SETUP_TOKEN` to an **unpadded base64url encoding of exactly
32 cryptographically random bytes**. For example, generate a value with
`python -c "import secrets; print(secrets.token_urlsafe(32))"` and deliver it
through a deployment secret. The value is read only from the process environment;
it is never saved in SQLite. Visit `/management/setup`, supply that credential
and the first admin email, then follow the emailed password-set link. Missing,
malformed or wrong credentials leave setup locked; execution stays available.
The first successful request permanently reserves the chosen address before
email delivery. If delivery fails, repeat setup with the same credential and
address after fixing SMTP. Other addresses cannot replace it. After the admin
sets a password, setup remains closed even if the environment variable remains.

Configure SMTP with standard `spring.mail.*` values in deployment YAML or their
environment overrides. Set `loomspan-sidecar.management.mail-from` to a sender
address and `loomspan-sidecar.management.external-base-url` to the trusted public
HTTPS console URL; loopback HTTP is allowed for local development. Links are
built solely from that configured URL. SMTP connect, read and write waits default
to five seconds. Missing or failed delivery prevents setup/invitations/recovery;
existing logins and execution remain available. Test the deployment's SMTP and
HTTPS settings with a dedicated mailbox before initial setup. Never use a live
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
`/management/setup` accepts the one-time credential only until activation.
Authenticated users reach `/management/home`,
`/management/configuration/current`, `/management/configuration/edit`, and
`/management/password/change`.
Administrators also reach `/management/accounts` to invite accounts, change
roles, disable or re-enable accounts, and resend a pending password link. The
public `/management/forgot` and `/management/password/{set,reset}` forms support
email-only recovery and initial password setup. Forms permit keyboard use,
password-manager autofill and paste. Forgotten-password requests
always give the same response for known and unknown addresses. Set links expire
after 24 hours and reset links after 30 minutes; each is single-use. A successful
password change or reset ends affected sessions and invalidates other links.
Sign in normally afterward. Recovery is email only: there is no offline account
repair, security question, or admin-assigned replacement password. If email and
administrator access are both lost, restore a known-good **full database backup**
as an installation recovery operation, not as a password-reset bypass.

The current-configuration page shows the complete authored skill YAML and REST
routes/targets from the **runtime-published** snapshot, including placeholders
and any literal sensitive values. Give viewer access only to people allowed to
read those values. The separately labeled intended ID/status are the database
selection for restart, not proof of what handles executions now. Authored text
is displayed as text, never interpreted as HTML. Refresh is explicit; passive
reads do not extend the idle login. The browser reports deliberate interaction
at most once every 30 seconds. If an invitation reports a delivery failure,
refresh the account list: it may contain a pending account. Correct SMTP and
resend the link. A used or expired set/reset link requires a fresh email link.
Role changes, disables, resets and password changes invalidate affected sessions;
sign in again before continuing. Viewer/editor/admin checks and CSRF are enforced
by the server even if a stale browser page still shows an action.

The edit page displays the runtime configuration to every management role.
Editors and admins can acquire the sole editing lease and change complete YAML
skill documents (with diagnostic source labels) and the complete REST
targets/routes YAML. Add or remove a skill document with the adjacent controls;
REST targets and routes remain one text document. URL placeholders such as
`${REST_BASE_URL}` are kept as authored text. Java skills, deployment settings,
model connections and destination bindings are outside this editor.

Edits save automatically to a session-private draft after a short pause. The
lower-left status says Unsaved, Saving, Saved to private draft, or Save failed;
Saved means the server acknowledged the current text, **not** that it is live or
recoverable after logout. A failed save retains text on the open page. Retry
save explicitly, or make a new edit after the failure. Validation runs separately
after an acknowledged save. Valid, Validation errors and Out of date appear
without interrupting typing; expand Validation details to inspect labelled
issues. Warnings alone still permit publication. Publish becomes available only
for the current saved, successfully validated candidate. Publication switches
the runtime catalog without a restart and clears old-base drafts on success.

Only the tab that acquired the lease can write. Release preserves this session's
draft; reacquire to resume. Discard removes it. Admin takeover starts that admin
from runtime configuration and does not expose another session's draft. A lost
lease leaves still-valid local text in the open tab but stops writes. The page
stores no draft or grant in browser storage: logout, session expiry, account
changes, discard and a changed runtime base can invalidate edits. The page
shows the configured login and lease idle limits and warns two minutes before
lease expiry. Only typing, paste, clicks, scrolling and Continue editing send
an activity report, at most once per 30 seconds. Background save, validation,
status reads and mouse movement do not renew deadlines.

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
`newPassword`. Anonymous `POST /api/management/setup` takes `credential` and
`email`; `POST /api/management/password/forgot` takes `email`; and
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

### Private editing API

All routes below require a management login, and unsafe methods require
`X-CSRF-TOKEN`. Editors and admins may mutate drafts or leases; only admins may
take over. Viewers may inspect lease status and their own session draft, if any.
The server derives account and session identity from authentication, never from
request JSON. A browser tab creates a UUID `tabId` and keeps it only for that tab.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET /api/management/editing` | None | `held`, `mine`, `expiresAt`, and `mineTabId` only for this session's lease; no foreign draft or grant details. |
| `GET /api/management/editing/draft` | None | This session's draft or 404. |
| `POST /api/management/editing/lease` | `{"tabId":"<uuid>"}` | Acquires the sole lease and creates or resumes this session's draft. |
| `POST /api/management/editing/lease/takeover` | `{"tabId":"<uuid>"}` | Admin only; revokes the old grant and starts the admin's fresh draft from runtime production. |
| `POST /api/management/editing/lease/activity` | `{"tabId":"<uuid>","grantId":"<uuid>"}` | Reports meaningful activity; renews login and lease at most once per 30 seconds. |
| `POST /api/management/editing/lease/release` | `{"tabId":"<uuid>","grantId":"<uuid>"}` | Releases ownership while retaining the eligible private draft. |
| `PUT /api/management/editing/draft` | `tabId`, `grantId`, `expectedCandidateId`, complete `skillDocuments` and `restRoutesYaml` | Replaces exact authored content, rotates candidate ID, and clears validation. |
| `DELETE /api/management/editing/draft` | Current `tabId`/`grantId` when holding the lease; otherwise an empty body | Discards this session's draft and lease. |
| `POST /api/management/editing/draft/validate` | `tabId`, `grantId`, `expectedCandidateId` | Checks the complete frozen candidate and returns `draftId`, `candidateId`, `baseSnapshotId`, `applied: true` and `validation`; no activation or submission. |

Lease acquisition and activity responses include `grantId`, `expiresAt` and the
owning session's `draft`. A draft includes `draftId`, `candidateId`,
`baseSnapshotId`, `configuration` (`skillDocuments` with `sourceName`/`yaml`, and
`restRoutesYaml`), and its current `validation` or null. A second tab may read
its session draft but cannot write with another tab's grant. Acquisition while
held, stale grant/tab/candidate/base, and unavailable editing return 409 without
mutating content. Malformed input returns 400; missing login returns 401 and
insufficient role or CSRF returns 403. Private responses are not cached.

`loomspan-sidecar.management.edit-lease-timeout` is positive and defaults to
`15m`; the login idle timeout defaults to `30m`. Lease expiry or release keeps
the private draft while its session and runtime base are valid. Reacquisition
issues a fresh grant. Logout, login expiry, account version changes, session
destruction, successful publication and restart clear affected editing state.
Configuration fault state permits status, private inspection, release and
discard, while blocking acquisition, save and activity renewal. The browser
sends reports only for typing, paste, clicks, scrolling or explicit Continue
editing, no more than once per 30 seconds. Mouse movement, automatic
validation/status polling and an open idle tab do not report activity. The edit
page warns two minutes before lease expiry; server deadlines remain authoritative.

### Protected configuration API

Management viewers, editors and admins can inspect literal authored snapshot
content, including sensitive values in pending or failed submissions. Protect
viewer accounts accordingly. Every response is private and `Cache-Control:
no-store`; an execution JWT does not grant access. Mutations require a management
session and CSRF token.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET /api/management/configuration/current` | None | `published` full runtime snapshot, `intendedId`, `intendedStatus`, and `mutationFault`. |
| `GET /api/management/configuration/history` | None | Retained full submitted snapshots in ascending `submissionSequence`. |
| `GET /api/management/configuration/history/{localId}` | None | Full retained snapshot or 404 after pruning. |
| `POST /api/management/configuration/publish` | `{"tabId":"<uuid>","grantId":"<uuid>","expectedCandidateId":"<uuid>"}` | Publishes an exactly validated candidate, then returns its full snapshot. |

A full snapshot contains `localId`, nullable `sourceId`, `submissionSequence`,
`status`, and `configuration` with authored `skillDocuments` and `restRoutesYaml`.
The framework's validation returns `successful` and issues with `severity`
(`ERROR` or `WARNING`), `sourceLabel`, optional `skillName` and `location`, and
`message`. Warning-only results can be published; an error cannot. A save,
including identical content, rotates the candidate ID and clears its validation.
Validation is advisory: Publish prepares and stages afresh. Neither validation
nor inspection renews login or lease inactivity deadlines.

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

Submitted history is global to authenticated management users. A saved or
validated but unsubmitted draft remains private to its server session—even for
an administrator—and never appears in history. Logout, idle expiry, restart,
or another browser session may lose access to that private draft without losing
an accepted submission. After a disconnect or new login, inspect **Current
configuration** and **History**; inspection does not recover a draft, retry a
publication, or offer cancellation.

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
operator intervention is required. Transfer and rollback controls remain
outside this console workflow.

The server checks the live account, session, tab, grant, base snapshot and exact
validated candidate after the publication lock becomes available. Conflicts
return 409 with `grant_conflict`, `candidate_conflict`, `base_conflict`,
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
history workflow use these same contracts; Phase 5 adds transfer and rollback.

## Execution API

Authenticated clients can use:

```text
GET  /v1/skills
GET  /v1/skills/{name}
POST /v1/skills/{name}/executions
GET  /v1/executions/{id}
```

Catalog discovery exposes every descriptor, including role-restricted skills;
authorization is enforced when POST validates the JSON-object input. Accepted
requests return `202`, an `id`, and a `Location` header without waiting. Poll the
location until `COMPLETED` or `FAILED`. Successful results are the framework's
unchanged text. A known owned failure is a normal `200` representation whose
kind is `INPUT_VALIDATION`, `ACCESS_DENIED`, or `SKILL_FAILURE`.

```powershell
$headers = @{ Authorization = "Bearer $env:SIDECAR_ACCESS_TOKEN" }
Invoke-RestMethod http://localhost:8080/v1/skills -Headers $headers
$accepted = Invoke-RestMethod http://localhost:8080/v1/skills/example/executions `
  -Method Post -Headers $headers -ContentType application/json -Body '{"message":"hello"}'
Invoke-RestMethod "http://localhost:8080/v1/executions/$($accepted.id)" -Headers $headers
```

Missing or invalid credentials return `401`; malformed/non-object or invalid
input returns `400` (with validation issues when available); denied roles return
`403`; unknown, expired or foreign records return `404`; raw bodies over the
configured limit return `413`; capacity exhaustion returns `429`; and admission
during shutdown returns `503`. Transport errors use `application/problem+json`.
Malformed execution IDs return `400`; unsupported HTTP methods return `405`.
All API responses carry `Cache-Control: no-store`. Closing a client connection
does not cancel accepted work.

Ownership compares the verified issuer and subject as separate fields. A renewed
token for the same pair can poll existing work; changing either field cannot.
Records are memory-only and disappear on restart.
The GET response includes nullable `configurationSnapshotId`. It is null while
queued or if capture fails; after worker handoff it is the durable UUID of the
captured framework generation and remains on the terminal record even after
that generation retires. Observer diagnostics do not determine this field.

## Capacity, retention, and diagnostics

```yaml
loomspan-sidecar:
  executions:
    max-input-size: 1MB
    max-retained: 1000
    completed-ttl: 15m
    max-concurrent: 32
    max-queued: 128
    max-queued-input-size: 64MB
    diagnostics: NEVER # NEVER, ONERROR, or ALWAYS
```

Limits and TTL must be valid at startup. `max-input-size` applies to raw request
bytes before JSON parsing. Queue bytes measure the validated map's UTF-8 JSON
representation; equality with either size limit is allowed. `max-retained`
counts queued, running and terminal records. Terminal TTL begins at completion,
reads do not extend it, and expiry removes the whole record. Waiting work is
discarded when shutdown closes admission and dispatch; active framework calls
remain governed by `loomspan.shutdown.timeout`.

`NEVER` returns no diagnostic events. `ONERROR` retains available events for
failed work, and `ALWAYS` for both terminal outcomes. Selected public events are
published with the outcome and expire with it. They are available completed
history, not a guaranteed complete trace, and may contain business data. Sidecar
does not sanitize or truncate arbitrary inputs, results, framework messages, or
selected events. Record count, TTL and queued-input bytes therefore do not bound
total heap: running inputs, results, diagnostics, and uncooperative framework
work may consume additional memory.

Complete validated snapshots can be published while work runs. Old admitted
work keeps its captured definitions and REST resources. Restart loses all
queued, active and retained execution records, shutdown discards waiting work,
and unfinished admitted work may be interrupted at the framework deadline.
Invalid selected content prevents startup and readiness until corrected through
a stopped database restore. REST clients stay
available for framework-owned admitted work until normal completion or the single
`loomspan.shutdown.timeout` cutoff, then close without a second drain period. SC5
uses the framework's 30-second default; configure orchestrator termination grace
above that budget plus cleanup margin (the Kubernetes example uses 45 seconds).

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
[REST skill routes](#rest-skill-routes). Framework startup discovery uses the
packaged empty source; framework shutdown remains
`loomspan.shutdown.timeout`; inbound server and outbound mTLS configuration
remains under standard `server.ssl.*` and `spring.ssl.bundle.*` namespaces. The
image honors those standard Boot environment and command-line overrides.

The Kubernetes example at `examples/kubernetes/deployment.yaml` keeps the
application and Sidecar in one pod, expects a prepopulated persistent SQLite
volume, sources credentials from Secrets, uses port 9091 for all
management probes, and leaves resource requests/limits for the operator to size
from actual model concurrency and diagnostic retention.

## Dependency and release boundary

Production and test code may use Loomspan Java types only from `ai.loomspan.api`.

The Guava dependency override selects `33.4.0-jre` for Sidecar's Java 21 runtime.
Without it, the framework's Google GenAI / Google Auth dependency chain selects
`33.4.0-android`. The override changes the runtime flavor at the same version.
Error Prone annotations use the transitive dependency version; Sidecar does not
require a separate annotation-version override.

Push and pull-request CI is prepared to override the dependency with published
`1.0.0-beta.5`. Hosted verification is deferred until that artifact exists on
Maven Central. The delivery order is local Sidecar integration against the
snapshot, framework release checks and publication, then Sidecar verification
against the released artifact. This project does not build framework source in
its own build or CI.

Release tags are exactly `v<project-version>`. The guarded workflow requires a
non-SNAPSHOT Sidecar version and framework `1.0.0-beta.5`, reruns Maven and image
verification, then publishes an immutable GHCR version tag and a GitHub release
containing the executable JAR, a reproducible ZIP archive, and SHA-256 files.
Local preparation is intentionally nonpublishing:

```powershell
python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.5 --loomspan-version 1.0.0-beta.5
```

Sidecar development is on `1.0.0-beta.5-SNAPSHOT` for the
[management console roadmap](ai/thoughts/phases/2026-09-16-management-console-roadmap.md).
The framework dependency remains `1.0.0-beta.5-SNAPSHOT`. Final dependency changes,
release tags and publication follow the separate release verification workflow;
the framework must be published and resolvable before final Sidecar verification.
