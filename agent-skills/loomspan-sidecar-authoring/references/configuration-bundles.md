# Configuration bundles (format 3)

Use **Download current configuration** on the current-configuration page, or
`GET /api/management/configuration/export` with a management session or Read-or-higher token. The ZIP is
a configuration-only transfer by default, with optional encrypted credentials. It carries the complete
authored skill YAML, source labels, REST routes/targets and execution YAML from the snapshot
running when export begins. An active empty configuration yields an empty skill
list, the authored empty REST document and `loomspan: {}\n`. If no runtime snapshot exists, export
is unavailable. The intended database pointer, retained history, private drafts,
accounts, sessions, leases, resolved environment values, URL allowlists, SMTP
settings, and SSL bundles are excluded. Editors and administrators can import
this bundle through **Import configuration** or the management API. For full
installation recovery, use the [stopped-instance backup procedure](https://github.com/loomspan/loomspan-sidecar/blob/main/docs/operations.md#stopped-instance-backup-and-recovery).

Import loads the bundle's complete authored skills, routes, targets and execution settings into
the caller's saved draft. `POST /api/management/configuration/import/review`
accepts one `bundle` multipart field and returns producer metadata and
validation feedback without changing state. `POST
/api/management/configuration/import/load` accepts the same bundle plus
`editingSessionId`, `generation`, `draftId`, `revision`, and the current
`baseSnapshotId`, plus optional `credentialMode` (`included` or `configuration-only`). The caller must hold the lease; the load advances the saved
revision and preserves the bundle source UUID as provenance. The runtime is
unchanged until the caller validates and publishes through the shared editing
contract. A failed upload or load leaves the prior draft intact.

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

The ZIP contains `manifest.json`, `rest.json`, `execution.json`, optional `credentials.json`, and one YAML file per
skill under `skills/`. All entry names are fixed or generated, never taken from
source labels. JSON and YAML entry bytes are UTF-8. `rest.json` is
`{"restRoutesYaml":"<exact authored YAML>"}`; it retains comments, sensitive
literals and unresolved placeholders. `execution.json` is
`{"executionConfigurationYaml":"<exact authored YAML>","credentialIdentifiers":[]}`
and contains identifier inventory, never resolved credential values or the encryption key.
Optional `credentials.json` contains `cipherFormat: "AES-256-GCM-v1"` and a
`credentials` array of exact `identifier`, `version`, `ciphertext` records. It is
inventoried and bounded like other payloads; identifiers and versions authenticate
the ciphertext and must not be renamed. The manifest structure is:

```json
{
  "formatVersion": 3,
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

The same inclusive bounds apply to export (1 MiB = 1,048,576 bytes). An oversize export fails rather than returning a partial ZIP.
