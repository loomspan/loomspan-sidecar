# Historical development-data reset notice

Preserved for provenance, not authorization to reset a deployed database.
See [upgrade guidance](../../docs/upgrades.md).

The pre-release V1 and V3 Flyway schemas now require source and encrypted
credential fields. Before
using this development build with an earlier local database, stop Sidecar and
preserve a complete backup, then reset only the development database and
recreate its accounts and drafts. Earlier format 1 bundles are rejected; create
new format 3 exports from compatible data or reauthor the content. This reset
is destructive to development data and is never a routine operation for a
deployed database.

