# Management accounts and credentials

The browser management surface is on the application port (`/management/**` and
`/api/management/**`); port 9091 remains the health-only Actuator port. Local
accounts use server-side form-login sessions and CSRF protection. Their roles
are `viewer`, `editor`, and `admin`, with each higher role including lower-role
permissions. This release exposes account, session, private draft/lease and
configuration inspection, validation and publication APIs. A management session
cannot call `/v1/**`; an execution JWT or observability API key cannot log in to
management. Sessions are local to one process and do not survive restart.

Generate `LOOMSPAN_SIDECAR_SETUP_TOKEN` with the packaged `admin generate-setup-token`
command. The [administrator access walkthrough](https://github.com/loomspan/loomspan-sidecar/blob/main/docs/admin-access.md) has copyable
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
`admin issue-password-reset`; see the [walkthrough](https://github.com/loomspan/loomspan-sidecar/blob/main/docs/admin-access.md). The reset
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

## Session and account HTTP API

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
and/or `enabled`, and `POST /api/management/accounts/{id}/resend-invite` resends to a pending user.
Responses expose account id, email, role, enabled and activated state, never
password hashes or tokens. The session cookie is HttpOnly, SameSite=Lax and
Secure by default; use HTTPS in deployment. Set
`LOOMSPAN_SIDECAR_SECURE_COOKIE=false` only for a local HTTP test. Browser and
management API responses use `Cache-Control: no-store` and
`Referrer-Policy: no-referrer`.

## Management personal tokens

Remote authoring uses the same `/api/management/**` configuration and editing
contract as the console. Require HTTPS for remote access and put the one-time
personal token in the `Authorization: Bearer` header. Never put it in a URL,
request body, command argument or repository file. The user must inject the
displayed secret into the client's environment; the stored digest cannot
supply it later. The console at `/management/personal-tokens` issues and
revokes only the current user's tokens. No OAuth server or separate management
API is involved.

Any active account can create, list and revoke its own tokens. Creation shows the
secret once; later lists expose only its identifier, preset, timestamps and
revocation state. Sidecar stores a SHA-256 digest and cannot recover the secret.
Never mix bearer authentication and browser session cookies in one request.

Issuance is limited to five per account per hour and 20 active tokens. Failed
bearer authentication is limited to 20 attempts per source IP per 15 minutes,
with a bounded in-memory counter and fail-closed capacity. Headers over 128
characters are rejected. Tokens expire after seven days by default and at
most 30 days after issuance. They are never refreshed. Structured management
audit events identify action, actor account ID, known token ID and outcome;
configuration changes also record candidate/base/published IDs where
available. Audit events must never contain raw tokens or Authorization headers.
Management tokens cannot call `/v1/**`; execution JWTs cannot call management.

## Personal token permissions and revocation

Read can inspect current configuration, exports, history, editing status and
the owner's saved draft. Edit adds lease control, same-user handoff, draft
changes, import and rollback loading, and validation. Publish adds activation
of the exact validated candidate without requiring additional console approval.
The account's current role is always an
additional limit: viewers can only read even with a Publish token. Personal
tokens cannot manage accounts, tokens, sessions or administrator takeover.
Password changes, password reset/recovery and account disablement revoke all
tokens for that account. Re-enabling does not revive them. Role changes narrow
effective permissions immediately. Revocation leaves saved drafts intact.
