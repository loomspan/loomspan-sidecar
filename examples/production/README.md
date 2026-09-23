# Production Compose deployment

Run one Sidecar instance per SQLite volume on a local filesystem with reliable
file locking. Caddy serves the management console and JWT execution API over
HTTPS, forwarding to Sidecar HTTP on the Compose network. Neither Sidecar's
application port nor its HTTP health port is published on the host. The image
runs as UID/GID 10001:10001 and contains no SMTP capture server, model fixture,
or private signing key. The private HTTP hop is deliberate; this stack does not
provide end-to-end TLS to Sidecar.

From the repository root, build against the installed Loomspan beta 5 snapshot:

```powershell
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
Copy-Item examples/production/production.env.example examples/production/production.env
```

On POSIX use `./mvnw`. Edit `production.env` and restrict it to the operator:
it contains model and SMTP credentials and may contain a DNS API token. The
example values are placeholders. Set `SITE_HOST` to the one name clients use,
and `EXTERNAL_BASE_URL` to `https://SITE_HOST` with the actual HTTPS port
(omit `:443`). This explicit URL is the trusted origin for emailed links. Bind
`HTTPS_BIND_ADDRESS` to a reachable interface for remote clients. Bind
`HTTP_BIND_ADDRESS` remotely only when clients need redirects or public ACME
HTTP validation; DNS validation can keep it on loopback. The HTTP listener
redirects to HTTPS. Never publish Sidecar port 8080
or management port 9091. Put the issuer's RSA JWT public key in
`examples/production/secrets/jwt-public.pem`, readable by UID 10001.

## Choose a certificate mode

Use an existing trusted corporate certificate when one is available. Otherwise,
the built-in private CA is the shortest complete path for an internal name.
For a registered public name, use public endpoint validation if the CA can
reach ports 80 and 443, or DNS validation when application ingress must stay
private. All modes use the same Sidecar image. Set `CADDY_CONFIG_FILE` in the
protected env file to the selected checked-in Caddyfile:

| Mode | Caddyfile | Required action |
| --- | --- | --- |
| Internal private CA | `./Caddyfile.internal` | Resolve `SITE_HOST` to the Caddy host for every client; install Caddy's root in each client trust store. |
| Public endpoint | `./Caddyfile.public` | Register the domain, publish correct A/AAAA records, and allow CA ingress on 80 and 443. Use host ports 80 and 443. |
| Public DNS challenge | `./Caddyfile.dns-cloudflare` | Build the Cloudflare module image, set `CADDY_IMAGE` and a scoped `CF_API_TOKEN`; allow outbound DNS, provider API, and ACME CA access. No inbound internet path is required for issuance. |
| Supplied chain/key | `./Caddyfile.supplied` | Provide the full chain and matching unencrypted key; add `compose.supplied.yaml` to Compose commands. Caddy does not issue or renew these files. |

For internal mode, choose an internal DNS name such as `sidecar.home.arpa` and
make it resolve to the Caddy host using your DNS or a client hosts entry. Start
the stack, then export the public root from the persistent Caddy volume:

```powershell
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml cp caddy:/data/caddy/pki/authorities/local/root.crt examples/production/secrets/caddy-local-root.crt
curl.exe --cacert examples/production/secrets/caddy-local-root.crt --resolve sidecar.home.arpa:8443:127.0.0.1 https://sidecar.home.arpa:8443/management/login
```

Adjust name, port and IP in the verification command. Install the exported
**public root certificate** in each browser/OS and application trust store that
calls Sidecar; Java callers can import it with `keytool -importcert` into their
trust store. Other runtimes have their own trust-store procedures. An untrusted
client must reject the TLS chain. Caddy cannot automatically trust remote
clients. Never disable client certificate verification. Caddy renews managed
leaf and intermediate certificates using `/data`; recreation with the same
`caddy-data` volume retains the CA and client trust chain. The exported root
certificate is public; `/data` also contains the **private CA key** and must
never be distributed to clients.

For public endpoint mode, Caddy automatically obtains and renews a publicly
trusted certificate. The domain's public DNS must resolve to Caddy, and the CA
must reach the Caddy HTTP port 80 or TLS port 443. For private endpoints that
must not accept public ingress, use the Cloudflare DNS reference: build it with
`docker build -f examples/production/Dockerfile.caddy-dns -t loomspan-caddy-cloudflare:local examples/production`,
set that tag as `CADDY_IMAGE`, and set `CF_API_TOKEN` in `production.env` to a
scoped token with `Zone.Zone:Read` and `Zone.DNS:Edit` on the intended zone.
The domain must be registered and publicly resolvable for ACME TXT checks;
private client DNS may resolve the application name to an internal address.
The Caddy host needs outbound access to its DNS provider API, public DNS
resolvers, and the ACME CA. Other Caddy DNS providers require a compatible
custom Caddy build and provider-specific Caddyfile syntax; they do not require
Java changes. The stock Caddy image does not bundle every DNS provider.
Restrict access to the Docker daemon and rendered Compose configuration:
container administrators can inspect the DNS token in Caddy's environment.

For supplied mode, set `TLS_DIR=./secrets/tls` in `production.env`, place
`fullchain.pem` and `privkey.pem` there, and append
`-f examples/production/compose.supplied.yaml` to each Compose command. The
chain must contain the leaf and needed intermediates, with the key readable by
Caddy but restricted from other users. The same route supports corporate PKI
certificates; clients must trust that corporate CA. Replace both files together
as one maintenance operation and run `docker compose ... exec -T caddy caddy
reload --force --config /etc/caddy/Caddyfile` to load them. The `--force` flag
reprovisions the loaded certificates even though the Caddyfile is unchanged.
Verify the new certificate with a trusted
client. Caddy does not renew operator-supplied certificates; corporate CA
enrollment integrations are outside this deployment.

Protect `production.env`, DNS tokens, JWT material, and all private keys from
source control and image builds. The `caddy-data` and `caddy-config` volumes
persist across recreation. Back up both volumes to protected storage alongside
the stopped SQLite backup. A lost private CA key cannot be recovered from the
distributed public root; restoring a different or newly generated CA requires
redistributing its public root. Public ACME data also belongs in the backup,
but public roots are already trusted by clients; never distribute Caddy's ACME
account or certificate private keys. Restrict access to the volumes and backups.
For recovery, stop Caddy, restore the complete `production_caddy-data` and
`production_caddy-config` volume contents with their ownership and permissions,
then start Caddy and verify with a client that already trusts the exported root.
Do not restore only `root.crt`: it cannot sign a replacement leaf without the
private CA key. Back up the SQLite volume while Sidecar is stopped as described
below; restore it independently of the certificate volumes.

## Application use without this proxy

Caddy is only part of this Compose deployment. The same Sidecar image can run
on localhost HTTP or behind another trusted proxy. For direct application TLS,
provide standard Spring Boot `SERVER_SSL_CERTIFICATE` and
`SERVER_SSL_CERTIFICATE_PRIVATE_KEY` file locations and a private key readable
by the application UID; do not set `SERVER_SSL_ENABLED=false`. Configure
`SERVER_FORWARD_HEADERS_STRATEGY=framework` only when an ingress proxy strips
untrusted forwarded headers and supplies the authoritative external origin.
Keep `LOOMSPAN_SIDECAR_EXTERNAL_BASE_URL` set to the trusted HTTPS URL used in
email. The local TLS fixture verifies direct HTTP and supplied application TLS
with no Caddy request path.

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

For local verification, run `python scripts/verify-production-tls.py --image
loomspan-sidecar:sc5-local` and `python scripts/verify-production.py --image
loomspan-sidecar:sc5-local` from the repository root. They create only
disposable containers, Compose projects, volumes, certificates, fixture
issuer/model and SMTP capture; they do not use `production.env`, contact real
mail recipients, request public certificates, or modify DNS. The TLS fixture
tests local private-CA automation, trust and recreation, supplied replacement,
and Caddyfile adaptation for public and Cloudflare DNS modes. Actual public CA
issuance and Cloudflare DNS updates require a separately authorized,
configured-environment check and are not established by the local tests.

Caddy reference: [automatic HTTPS](https://caddyserver.com/docs/automatic-https),
[TLS configuration](https://caddyserver.com/docs/caddyfile/directives/tls),
[proxy headers](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy),
and [Cloudflare DNS module](https://github.com/caddy-dns/cloudflare).
Spring's [forwarded header security guidance](https://docs.spring.io/spring/reference/web/webmvc/filters.html)
explains why Caddy removes client-supplied forwarded headers before Sidecar
uses its proxy-provided origin.
