# Production Compose deployment

Run one Sidecar instance per SQLite volume on a local filesystem with reliable
file locking. The application port serves both the management console and JWT
execution API over HTTPS. Actuator health is HTTP on container port 9091 only;
Compose does not publish it. The image runs as UID/GID 10001:10001 and contains
no SMTP capture server, model fixture, or private signing key.

From the repository root, build against the installed Loomspan beta 5 snapshot:

```powershell
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
Copy-Item examples/production/production.env.example examples/production/production.env
```

On POSIX use `./mvnw`. Edit `production.env` and restrict it to the operator:
it contains model and SMTP credentials. Its example hosts and credentials are
placeholders and do not work. Supply the HTTPS DNS origin in `EXTERNAL_BASE_URL`
using the same publicly reachable port as `HTTPS_PORT`, and change
`HTTPS_BIND_ADDRESS` from its loopback example to the intended host interface
when clients connect remotely. Provision a PEM full
certificate chain and matching unencrypted private key in
`examples/production/secrets/tls/fullchain.pem` and `privkey.pem`. Mounts are
read-only; both files and their parent directories must be readable/traversable
by UID 10001. Put the issuer's RSA JWT public key in
`examples/production/secrets/jwt-public.pem` with the same readability.
Keep private keys and the env file outside source control. Renew the certificate
and key together, then recreate the container to load the new files.

Set `JWT_ISSUER_URI` and `JWT_AUDIENCE` to the real token issuer and audience.
Configure `MODEL_DRIVER`, `MODEL_BASE_URL`, `MODEL_API_KEY`, and `MODEL_NAME` for
the model named `primary` in authored skills. For the first administrator setup,
set `SETUP_TOKEN` to unpadded base64url encoding of exactly 32 random bytes;
generate one with `python -c "import secrets; print(secrets.token_urlsafe(32))"`.
Set `SMTP_FROM`, `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, and `SMTP_STARTTLS` for the
customer's SMTP provider. When `SMTP_STARTTLS=true`, the connection fails if
the provider does not support STARTTLS, so SMTP credentials are not sent over
plaintext. Set `SMTP_STARTTLS=false` and `SMTP_AUTH=false` only for a trusted
plaintext relay without SMTP credentials. Email delivery and a correct trusted
HTTPS origin are
required for initial setup, invitations, and password recovery. Automated tests
use `.test` recipients and an isolated capture fixture; production uses the
customer SMTP service. To use a REST `base-url: ${TARGET_URL}`, list `TARGET_URL`
in `URL_VARIABLES` and supply its absolute HTTP(S) value as `TARGET_URL`.
Only declared names resolve from the process environment, and changes require
a restart. Remove unused names and values.

Create and prepare the named volume before starting. Run the maintenance
container only on a trusted operator host; these commands affect the volume
created by this Compose project:

```powershell
docker compose --env-file examples/production/production.env -f examples/production/compose.yaml config --quiet
docker volume create production_sidecar-data
docker run --rm --user 0:0 -v production_sidecar-data:/sidecar/data alpine:3.20 sh -c 'chown 10001:10001 /sidecar/data && chmod 700 /sidecar/data'
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml up -d
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml ps
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml exec sidecar curl --fail --silent http://localhost:9091/actuator/health/readiness
```

Use the HTTPS origin in a browser, open `/management/setup`, enter the one-time
credential and administrator address, follow the emailed password link, then
sign in at `/management/login`. Verify the browser receives a Secure session
cookie and the certificate chains to the intended CA. After setup, clear
`SETUP_TOKEN` in `production.env` and recreate the service. Existing accounts
remain in the database; a fresh volume without the token keeps management
setup locked while authenticated JWT execution remains available.

```powershell
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml up -d --force-recreate
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml stop sidecar
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml start sidecar
docker volume inspect production_sidecar-data
```

Stop the sole instance and wait for exit before backup or recovery. The volume
mountpoint from `docker volume inspect` contains `sidecar.db` and any existing
`sidecar.db-wal` and `sidecar.db-shm`. Copy the whole existing set to a new
protected directory while stopped. For restore, preserve the damaged set,
remove its stale WAL/SHM companions, copy the stopped backup set in, and set
directory/file ownership to 10001:10001 before restarting. The exact copy and
restore procedure is in [stopped-instance backup and recovery](../../README.md#stopped-instance-backup-and-recovery).
After restart, check the private readiness endpoint, sign in with a restored
account, and compare **Current configuration** with **History** and the intended
restart ID. Execute a known REST and model-backed skill with a valid JWT before
returning the instance to clients. If startup fails on an invalid selected
snapshot, keep a protected copy of the unusable stopped file set and restore a
known-good stopped backup; do not edit the live database or assume that a
configuration ZIP repairs the installation.
The database includes management accounts and authored configuration; a
configuration ZIP does not restore accounts or deployment settings. Keep the
operator env, PEM files, and JWT trust material separately protected and backed
up. Do not place multiple Sidecar instances or network filesystem replicas on
one database. Kubernetes deployment support is deferred pending customer
requirements; the container is not restricted from other environments.

Configuration ZIP export/import transfers authored YAML and route text between
installations, with destination-specific allowlisted URL values supplied by
each process. It creates a new local snapshot and does not transfer accounts or
source history. Retained-history rollback validates and publishes a new local
snapshot from a source still present in the same installation. Review the
destination's URL bindings and the documented draft-loss confirmation before
either cutover. Neither operation replaces the full stopped database recovery
procedure.

For local verification, run `python scripts/verify-production.py --image
loomspan-sidecar:sc5-local` from the repository root. It creates only disposable
Compose projects, volumes, certificates, fixture issuer/model and SMTP capture;
it does not use `production.env` or contact real mail recipients.
