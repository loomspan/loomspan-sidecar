# First administrator and local recovery

The setup credential authorizes creation of the first administrator. The email
address is the login username. The password is chosen by that administrator;
there is no default password. Entering an email address during setup does not
prove mailbox ownership. Keep terminal output and shell history containing
credentials private.

Build the image from the repository root (`./mvnw` on POSIX):

```powershell
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
```

```sh
./mvnw -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
```

The image generates a fresh 32-byte, unpadded base64url setup credential on
each invocation. This command needs no database, server, SMTP, or model settings.
Its only successful stdout is the credential and newline.

## Local quickstart (loopback HTTP)

PowerShell, from the repository root:

```powershell
$env:SIDECAR_IMAGE = "loomspan-sidecar:sc5-local"
$env:LOOMSPAN_SIDECAR_SETUP_TOKEN = docker run --rm loomspan-sidecar:sc5-local admin generate-setup-token
docker compose -f examples/quickstart/compose.yaml up -d
```

POSIX shell:

```sh
export SIDECAR_IMAGE=loomspan-sidecar:sc5-local
export LOOMSPAN_SIDECAR_SETUP_TOKEN="$(docker run --rm loomspan-sidecar:sc5-local admin generate-setup-token)"
docker compose -f examples/quickstart/compose.yaml up -d
```

The assignment captures the credential without displaying it. Display it when
you are ready to enter it in the management console (keep this terminal output
private):

```powershell
$env:LOOMSPAN_SIDECAR_SETUP_TOKEN
```

```sh
printf '%s\n' "$LOOMSPAN_SIDECAR_SETUP_TOKEN"
```

Open <http://localhost:8080/management/setup>. Copy the displayed value into
the **setup credential** field, then enter the administrator email, chosen
password, and confirmation. The setup credential is not the login password.
Sign in at <http://localhost:8080/management/login> with the email and chosen
password.
The management console is on port 8080; port 9091 is health only. Setup closes
permanently in this database once activated, even while the setup credential
remains configured. Remove the secret from the environment and recreate the
container (a restart keeps its old environment):

```powershell
Remove-Item Env:LOOMSPAN_SIDECAR_SETUP_TOKEN
docker compose -f examples/quickstart/compose.yaml up -d --force-recreate sidecar
```

```sh
unset LOOMSPAN_SIDECAR_SETUP_TOKEN
docker compose -f examples/quickstart/compose.yaml up -d --force-recreate sidecar
```

Quickstart stores SQLite in the persistent `quickstart_sidecar-data` Compose
volume when using the default project name. Keep the volume to retain accounts.
Do not use `down -v` unless deliberately discarding the installation.

## Production (Caddy HTTPS)

Follow the [production Compose guide](../examples/production/README.md) for
JWT, model, Caddy hostname, certificate, and volume preparation. Set
`SIDECAR_IMAGE` in `examples/production/production.env`. SMTP settings may be
left empty. Generate the setup credential into the shell before `up`; the
shell variable overrides `SETUP_TOKEN` in the env file:

```powershell
$env:SETUP_TOKEN = docker run --rm loomspan-sidecar:sc5-local admin generate-setup-token
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml up -d
```

```sh
export SETUP_TOKEN="$(docker run --rm loomspan-sidecar:sc5-local admin generate-setup-token)"
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml up -d
```

Open `https://<SITE_HOST>:<HTTPS_PORT>/management/setup` using the trusted
Caddy hostname and port from the production env file. Supply the same four
fields, then sign in at `/management/login` with email and chosen password.
Clear `SETUP_TOKEN` from the shell and env file, then recreate Sidecar:

```powershell
Remove-Item Env:SETUP_TOKEN
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml up -d --force-recreate sidecar
```

```sh
unset SETUP_TOKEN
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml up -d --force-recreate sidecar
```

## Recover an existing administrator without SMTP

The operator command needs write access to the existing Sidecar SQLite volume.
It accepts only an enabled administrator who already has a password. The
database path is mandatory. Stop the sole Sidecar instance first; the command
also takes a file lock and fails if Sidecar still holds it. A maintenance
container uses the image's UID/GID 10001 and must be able to write the volume.
For production, prepare ownership as described in the production guide. The
command neither starts the server nor creates a missing database or account.

Quickstart PowerShell:

```powershell
docker compose -f examples/quickstart/compose.yaml stop sidecar
$reset = docker compose -f examples/quickstart/compose.yaml run --rm --no-deps sidecar admin issue-password-reset --database /sidecar/data/sidecar.db --email admin@example.com
docker compose -f examples/quickstart/compose.yaml start sidecar
```

Quickstart POSIX:

```sh
docker compose -f examples/quickstart/compose.yaml stop sidecar
reset="$(docker compose -f examples/quickstart/compose.yaml run --rm --no-deps sidecar admin issue-password-reset --database /sidecar/data/sidecar.db --email admin@example.com)"
docker compose -f examples/quickstart/compose.yaml start sidecar
```

Production PowerShell:

```powershell
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml stop sidecar
$reset = docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml run --rm --no-deps sidecar admin issue-password-reset --database /sidecar/data/sidecar.db --email admin@example.com
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml start sidecar
```

Production POSIX:

```sh
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml stop sidecar
reset="$(docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml run --rm --no-deps sidecar admin issue-password-reset --database /sidecar/data/sidecar.db --email admin@example.com)"
docker compose --project-name production --env-file examples/production/production.env -f examples/production/compose.yaml start sidecar
```

Replace `admin@example.com` with the existing administrator login email. If
the command fails, stdout contains no credential; keep Sidecar stopped while
checking the path, volume permission, lock, migration compatibility, and target
eligibility, then retry. Each successful issuance supersedes previous reset
credentials for that account. The credential expires after 30 minutes and can
be used once. Issuance leaves the current password unchanged. Deliberately
pass `$reset` or `reset` to the administrator through a protected channel and
clear the shell variable afterward.

The administrator opens `/management/password/reset` on the same local HTTP
or production HTTPS console, pastes the reset credential into the form, and
chooses a new password. The credential is entered in the page, not in the URL.
Successful redemption invalidates old management sessions and editing state.
SMTP, when configured, remains available for invitations and emailed password
recovery. Without SMTP, those email actions cannot deliver links; initial
setup and this operator recovery still work.

## Personal access tokens

Sign in to the console and open **Personal tokens**. Any active account can create,
list and revoke its own tokens. Select Read, Edit, or Publish and optionally set a
future expiry no more than 30 days away. The default is seven days. Creation
shows the opaque secret once; later lists contain only its identifier, preset,
timestamps and revocation state. Copy it immediately and inject it into the
client's environment. Sidecar stores a SHA-256 digest, so it cannot recover a
lost secret. Revoke a lost or exposed token and issue a new one. At most five
tokens may be issued per account per hour and 20 may be active at once.

Read can inspect current configuration, exports, history, editing status and
the owner's saved draft. Edit adds lease control, same-user handoff, draft
changes, import and rollback loading, and validation. Publish adds activation
of the exact validated candidate. The account's current role is always an
additional limit: viewers can only read even with a Publish token. Personal
tokens cannot manage accounts, tokens, sessions or administrator takeover.
Password changes, password reset/recovery and account disablement revoke all
tokens for that account. Re-enabling does not revive them. Role changes narrow
effective permissions immediately. Revocation leaves saved drafts intact.
