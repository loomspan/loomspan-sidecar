# Client setup and commands

Requires Python 3.9 or newer and only its standard library. Resolve the bundled [client](../client/sidecar_authoring.py) from the installed skill root supplied by the host; the customer's current directory remains their application project. There is no agent allowlist or proprietary dependency. Set `$skillRoot` below to that host-resolved absolute path, not a guessed agent directory.

```powershell
# Inject LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN into this process externally.
$client = Join-Path $skillRoot 'client/sidecar_authoring.py'
$url = 'https://sidecar.example.test'
python $client --url $url current
python $client --url $url draft
python $client --url $url lease-status
python $client --url $url acquire --label 'Application author'
```

Do not type a real token into an agent prompt, shell command, repository file, or transcript. Inject it into the client process environment through the user's secret handling mechanism. Use HTTPS outside loopback. The token secret is displayed once when issued in the console and cannot be retrieved later; replace expired or revoked tokens explicitly. The client does not refresh credentials.

`acquire` and `handoff` take only a descriptive label. The server binds ownership to the authenticated session or token. `handoff` makes one handoff request, then reads `current` and `draft` and prints the new grant and fresh state. There is no background renewal or retry. If work extends beyond the lease expiry, submit the current `editingSessionId` and `generation` to `renew` using JSON from stdin or `--file`.

Commands are `current`, `history`, `draft`, `lease-status`, `acquire`, `handoff`, `renew`, `release`, `save`, `reconcile`, `validate`, `publish`, `export --output FILE`, `import-review --bundle FILE`, and `import-load --bundle FILE`. Mutating JSON bodies are read from stdin or `--file FILE`, never from a command argument. Export writes the server ZIP unchanged. Import review sends only the `bundle` multipart field; import load sends that bundle and exactly the five current candidate fields in the JSON file. The client does not parse or validate skill YAML or bundle internals.

Sample save body (use actual IDs, revision and complete content from fresh reads):

```json
{
  "editingSessionId": "<grant editingSessionId>",
  "generation": "<grant generation>",
  "draftId": "<draft draftId>",
  "revision": 1,
  "baseSnapshotId": "<current snapshot ID>",
  "skillDocuments": [{"sourceName": "records.yaml", "yaml": "name: readRecord\ndescription: Read an owned record\nrest: true\n"}],
  "restRoutesYaml": "targets: {}\nroutes: {}\n"
}
```

The `validate` and `publish` bodies contain only the first five fields. Save and reconcile replace the **whole** candidate; neither merges changes. Import load also uses exactly those five fields. The submitted base for reconcile must be the current published base after reviewing both configurations. Validation proof is tied to the saved revision, base, and lease generation; after a handoff, validate again before publication.

An Edit token can save and validate, then the same user signs into the console, reads the saved draft, explicitly takes control, revalidates and publishes. A Publish token can run `handoff`, `validate`, and `publish` directly. The server enforces both paths; labels and IDs alone never grant authority.

For `401`, replace the token and read state; for `403`, check its preset and the live account role. For `409`, read current, draft and lease status, then decide whether to acquire, hand off or reconcile. Do not replay a denied write. For `503` after publish, inspect current publication and the retained draft first because activation may already have occurred. A saved draft survives token loss and Sidecar restart; the lease and validation proof do not. Successful publication removes the publishing user's draft and lease; failed publication retains the draft. Bundle export/import is optional transfer, not a required deployment policy.
