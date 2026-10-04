# Configuration lifecycle

`loomspan-sidecar.storage.database-path` defaults to `/sidecar/data/sidecar.db`.
Sidecar migrates and validates a file-backed SQLite database at startup. In
`database` mode it atomically creates one empty current snapshot on a new database, then activates
and records it as **published** before opening execution dispatch.
The empty content is no skill documents, the exact REST text
`targets: {}\nroutes: {}\n`, and execution YAML `loomspan: {}\n`, which uses
framework defaults. Reopening retains that snapshot; an inconsistent
initialized store or existing database without migration history fails startup.
On restart the committed current pointer is prepared and published as a new
process-local framework generation, regardless of its previous status. The
durable local UUID is preserved. Invalid content or required bookkeeping failure
aborts startup without falling back to empty or older content.

Each snapshot contains an ordered set of complete `SkillDocument` diagnostic
labels and original YAML text, plus original REST targets/routes YAML and
framework execution YAML. Labels are nonblank and exactly unique; the set may be empty. The
REST text is stored before parsing or placeholder resolution, so comments and
`${NAME}` references in `base-url` remain exactly as authored. REST YAML secret
handling is separate from the provider credential feature. Snapshots include the full framework-supported model-backed and
REST skill authoring surface. They do not include accounts, sessions, leases,
deployment URL-variable declarations or resolved environment values,
identity-provider settings, or arbitrary Spring settings. Publishable model
connections, aliases, session settings and trace persistence are included. Each
snapshot also retains complete effective execution YAML. Provider credential
values retained for database publications and drafts are encrypted in separate
SQLite tables. Ordinary snapshot reads and default exports contain credential
identifiers, never values or ciphertext. An explicit authorized export can
include authenticated ciphertext, but never plaintext values or the encryption key. Protect the database volume and exported bundles; REST YAML may
still contain literal sensitive values under its existing contract.

A snapshot has a fresh stable local UUID, optional source UUID for provenance,
and increasing submission sequence for later oldest-first history pruning.
Content and identity do not change after insertion. Separate local status is
`pending`, `published`, or `failed`; `pending` means the publication outcome is
unknown. A source UUID never selects or overwrites a local snapshot. The
configuration bundle carries the same complete authored content with independent
integer `formatVersion: 3`, source identity, and informational producer
application and framework versions. The destination allocates a new local UUID;
imported status does not prove publication there. The archive layout, integrity
checks, and import/export operations are described in
[configuration bundles](configuration-bundles.md). Flyway
version and application version are not transfer compatibility rules.

## Durable private configuration drafts

Sidecar stores one saved draft per management account, with ordered complete
skill documents, REST YAML, execution YAML, encrypted
provider credential versions, base snapshot UUID, source provenance and a
monotonic revision. The saved content is in the same SQLite database and must
be included in stopped-instance backups. Leases and successful validation
proofs remain process-local, so restart requires reacquisition and revalidation.
Logout and credential changes revoke editing access without deleting content.
A successful publication deletes only its owner's saved draft after framework
activation. Other users' drafts remain stored and become stale by base UUID
comparison. Failure before activation leaves the publishing draft intact.

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

Current-configuration inspection reports the published UUID, intended UUID and
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
the main database file while it is live is not a safe backup. The production
Compose guide prepares a single local volume with these permissions and locking.
