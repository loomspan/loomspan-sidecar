# Configuration modes and credential operations

Select `loomspan-sidecar.configuration.mode=file|database` at startup, or use
`LOOMSPAN_SIDECAR_CONFIGURATION_MODE`. The default is `database`; invalid values
fail startup. Mode never changes live, and load failures never select a fallback.

In **file** mode, Framework `loomspan.skills.locations`, connections, models,
executor configuration, session settings and trace persistence come from deployment
files. Set skill locations, for example, to `file:/sidecar/skills/*.yaml`, and load
execution YAML through ordinary Spring Boot Config Data. HTTP settings live under
`loomspan.executor-config.default.http`; operations belong in each skill YAML.
Framework structural/reference errors fail startup; HTTP domain errors first fail
on invocation before dispatch. Deploy changes and restart to apply them. Literal
provider credentials and external references follow Framework's file-mode contract.
Skill files activate in `default`. Use lowercase physical path components
within the configured skill pattern, including the filename extension.

The Console displays the startup skill catalog, model aliases, session and trace
settings, and retained startup metadata. Startup inspection identifies each skill
by its `namespace`, `localName` and `kind`. Connection settings stay in the deployment
files. Configuration mutations return `409 configuration_read_only`. Accounts, tokens and
execution retain their normal permissions. File startup does not initialize or
change the selected database publication, decrypt its credentials, or alter drafts.
Its separate durable startup UUID correlates executions; metadata retention uses
`loomspan-sidecar.snapshots.max-retained` and is not a recovery source.

In **database** mode, authored skill and execution YAML and separately supplied
encrypted variable values belong to one installation. Authored skills publish in
`default`. Deployment provider, model,
and session properties do not contribute to a managed candidate. Its exact
reference map comes from the selected documents and the installation's values.

Use a literal or a whole-node marker such as `{ $var: service.key }` at a supported
setting. For example, `api-key`, items in `headers`, and
`gemini.credentials-json` accept values. In an HTTP executor skill, a marker can
supply its operation's `config.path`. Authorized management reads and
configuration-only exports show the authored YAML, including any literal values
and markers. Supply separately managed values using
`PUT /api/management/editing/draft/credentials` under the current Edit grant.
These values stay encrypted in storage and are write-only through management.

Saving YAML or editing a value changes the draft revision. Validate checks authored
structure first, then reads the values needed to prepare that revision without
activation. Only publication changes the current installation values. If a
reference is missing or its value is blank, supply a value or edit the documents,
then validate again. Removing the last reference leaves the encrypted value in
the installation. To delete it, remove the value explicitly and publish.

Rotate a provider key by saving the replacement, validating, publishing, waiting
for old work to finish, then revoking the old key. Rollback selects historical
authored documents and the currently published values. If a historical reference
was removed from the value inventory, supply its replacement in the rollback
draft before publishing. Restart restores the published documents and values.
See [execution settings](execution-configuration.md) for the publishable fields.

Supply `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` through the process environment as a
base64-encoded cryptographically random 32-byte key. Generate it with
`openssl rand -base64 32` in a trusted operator environment and provision it through
normal deployment secret handling. It is never stored alongside ciphertext in SQLite
or bundles. Back it up separately and keep it stable across provider-key rotations.
Missing/wrong material fails explicitly without plaintext fallback or ciphertext loss.
Restore the original key and a consistent database backup; losing the key makes retained
credentials unrecoverable. Candidate preparation decrypts only referenced values.
Encryption-key/environment changes require restart. There is no automatic key rotation.

Default exports carry authored configuration and configured names. Select **Include
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
