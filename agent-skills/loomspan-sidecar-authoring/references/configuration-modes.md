# Configuration modes and credential operations

Select `loomspan-sidecar.configuration.mode=file|database` at startup, or use
`LOOMSPAN_SIDECAR_CONFIGURATION_MODE`. The default is `database`; invalid values
fail startup. Mode never changes live, and load failures never select a fallback.

In **file** mode, framework `loomspan.skills.locations`, connections, models,
session settings and trace persistence come from deployment files. Sidecar reads
`loomspan-sidecar.rest-routes.location` once; its default is the bundled empty
routes resource. For example, set skill locations to `file:/sidecar/skills/*.yaml`
and routes location to `file:/sidecar/rest-routes.yaml`. Missing or invalid configured
resources fail startup. Deploy Git-managed changes, then restart; deploy earlier
files and restart to roll back. Literal provider credentials and external references
are supported. Prefer secret stores or environment provisioning for secrets.
The Console displays redacted startup inspection and retained startup metadata;
configuration mutations return `409 configuration_read_only`. Accounts, tokens and
execution retain their normal permissions. File startup does not initialize or
change the selected database publication, decrypt its credentials, or alter drafts.
Its separate durable startup UUID correlates executions; metadata retention uses
`loomspan-sidecar.snapshots.max-retained` and is not a recovery source.

In **database** mode, all authored skills, routes and execution settings and encrypted
provider credentials form one complete candidate. New databases start empty.
Deployment provider/model/session values do not seed or merge into that candidate,
including at framework bootstrap. No provider credential is resolved from deployment
files or the environment. Save or replace each referenced credential with
`PUT /api/management/editing/draft/credentials` under the current Edit grant, then
validate and publish. The Console shows identifiers only. Saving never activates;
credential replacement increments the draft revision and invalidates validation.
If an imported credential is no longer needed, remove its YAML references and its
required identifier from the draft. Required identifiers must be configured or
explicitly removed before validation can succeed.
Rotate a provider key by saving the replacement, validating, publishing, waiting for
old work to finish, then revoking the old provider key. Early provider-side revocation
can invalidate captured work. Rollback republishes complete retained settings and
credential versions under a new ID. Restart restores the database selection exclusively.

Database credential fields use `api-key-ref`, `gemini.credentials-json-ref`, or
`header-refs`. Save their values through the write-only control, never ordinary
YAML save bodies. Gemini Vertex requires managed `credentials-json-ref` material;
application-default credentials and credential file/URI references are prohibited
in database candidates. See [execution settings](execution-configuration.md) for
the publishable fields and a complete connection example.

Supply `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` through the process environment as a
base64-encoded cryptographically random 32-byte key. Generate it with
`openssl rand -base64 32` in a trusted operator environment and provision it through
normal deployment secret handling. It is never stored alongside ciphertext in SQLite
or bundles. Back it up separately and keep it stable across provider-key rotations.
Missing/wrong material fails explicitly without plaintext fallback or ciphertext loss.
Restore the original key and a consistent database backup; losing the key makes retained
credentials unrecoverable. File mode does not require or inspect this encryption key.
Encryption-key/environment changes require restart. There is no automatic key rotation.

Default exports carry configuration and required identifiers. Select **Include
encrypted credentials** or `?includeEncryptedCredentials=true` to capture retained
AES-256-GCM ciphertext and authenticated identifiers/versions atomically. This needs
current Edit authority, including the live account and PAT intersection. Users may
share encryption keys between environments; no distinct-key policy is imposed.
A destination with the same key can import the included credentials without reentry.
Wrong/missing keys or tampering fail without changing the draft or active publication.
For different keys explicitly choose **Configuration only** (`credentialMode=configuration-only`),
then provide destination replacements. The default `credentialMode=included` never
silently discards included ciphertext. Import is draft-only and still requires validation
and publication. Unrelated retained ciphertext remains intact.

For full-installation migration, make a consistent stopped-instance database backup
(including applicable WAL/SHM companions), restore it at the destination, and securely
provision the same encryption key separately. The complete database includes accounts,
drafts, history and encrypted credentials; a configuration bundle does not.
