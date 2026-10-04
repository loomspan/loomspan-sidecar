# Management editing and publication

For access and sessions see [management access](management-access.md); for
startup authority and credentials see [configuration modes](configuration-modes.md).
The [Python client](client-usage.md) uses this same API.

## Browser editing

The edit page displays the runtime configuration to every management role.
Editors and admins can acquire the sole editing lease and change complete YAML
skill documents (with diagnostic source labels), complete REST targets/routes
YAML and framework execution YAML. Add or remove a skill document with the adjacent controls;
REST targets and routes remain one text document. URL placeholders such as
`${REST_BASE_URL}` are kept as authored text. Use the
[publishable execution fields](execution-configuration.md) and
[managed credential references](configuration-modes.md). Save secret values through
the separate write-only credential form and use their identifiers in YAML.
Process settings remain deployment-owned.

Edits save automatically to the user's durable draft after a short pause.
The draft status distinguishes unsent local text, a saved revision and the
published runtime. A failed save keeps the local text visible for explicit
retry. Execution YAML must be syntactically readable before saving so every
provider credential field can be checked; a syntax error preserves the last saved
draft. Framework semantic validation still happens on the saved revision.
Validation runs on the exact saved revision and shows labelled issues;
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
| `PUT /api/management/editing/draft` | Capability, `draftId`, `revision`, `baseSnapshotId`, complete `skillDocuments`, `restRoutesYaml`, `executionConfigurationYaml` | Replace the saved content at the current base; increment revision and clear validation. |
| `PUT /api/management/editing/draft/credentials` | Capability, draft ID/revision/base ID, credential `identifier` and write-only `value` | Encrypt a replacement in the private draft, increment revision and clear validation; the value is never returned. |
| `DELETE /api/management/editing/draft/credentials` | Capability, draft ID/revision/base ID and configured or required `identifier` | Remove a credential and its requirement from the private draft, increment revision and clear validation. |
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
YAML, execution YAML, provider source, and encrypted credentials. A new authorized client may read its owner's draft but must acquire a new lease and
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

Management viewers, editors and admins can inspect authored snapshot
content and masked provider-credential identifiers, including pending or failed
submissions. Provider secret values are never returned; ciphertext is returned
only by the explicit encrypted-export option requiring Edit authority. REST
YAML may contain literal sensitive values, so protect viewer accounts accordingly.
Every response is private and `Cache-Control:
no-store`; an execution JWT does not grant access. Browser mutations require a
management session and CSRF token; scoped bearer tokens use the same API.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET /api/management/configuration/current` | None | `published` full runtime snapshot, `intendedId`, `intendedStatus`, and `mutationFault`. |
| `GET /api/management/configuration/export` | None | Format 3 ZIP of the captured runtime-published configuration; authenticated viewer, editor, or admin. 413 when bundle size limits are exceeded; 503 when runtime state is unavailable. |
| `GET /api/management/configuration/history` | None | Retained full submitted snapshots in ascending `submissionSequence`. |
| `GET /api/management/configuration/history/{localId}` | None | Full retained snapshot or 404 after pruning. |
| `GET /api/management/configuration/file-startups` | None; Read or higher token or management session | Redacted retained file-startup metadata in file mode; empty list in database mode. |
| `POST /api/management/configuration/rollback/{localId}/review` | Empty body; Edit authority; browser sessions require CSRF | Read-only source summary and destination validation. |
| `POST /api/management/configuration/rollback/{localId}/load` | Capability, draft ID/revision, current base ID | Load the retained source into the caller's saved draft; no publication. |
| `POST /api/management/configuration/publish` | Capability, draft ID/revision/base ID | Publish only the exact successfully validated saved revision, then return the full snapshot. |

A full snapshot contains `localId`, nullable `sourceId`, `submissionSequence`,
`status`, and `configuration` with authored `skillDocuments`, `restRoutesYaml`
`executionConfigurationYaml`, and configured credential
identifiers. `effectiveExecutionYaml` contains references and the retained
provider settings, never resolved values.
The framework's validation returns `successful` and issues with `severity`
(`ERROR` or `WARNING`), `sourceLabel`, optional `skillName` and `location`, and
`message`. Warning-only results can be published; an error cannot. Every save, including identical content, advances the revision and clears validation.
Validation freezes the complete database candidate and credential versions. Publication
requires revalidation if its effective content differs, then prepares that same candidate. Neither validation
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
protected recovery-guidance link and the [stopped-instance recovery procedure](https://github.com/loomspan/loomspan-sidecar/blob/main/docs/operations.md#stopped-instance-backup-and-recovery) when
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
selection disagree. The browser authoring, validation, and retained
history and rollback workflows use these same contracts.
