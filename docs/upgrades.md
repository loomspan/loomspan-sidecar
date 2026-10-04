# Upgrading Sidecar

Sidecar is in beta; configuration, HTTP contracts, skill semantics, and stored
formats may change between betas. Pin an exact target and read its release notes
before upgrading. Sidecar and Framework versions advance independently.

1. Record your Sidecar version, image/source revision, configuration mode, and
   bundled Framework version from that exact revision's POM. Choose an exact target.
2. Review the target's release and migration notes for API, configuration,
   database, bundle, and Framework skill changes. An absent migration ledger is
   unknown coverage, not a compatibility promise.
3. Take a [stopped full-installation backup](operations.md#stopped-instance-backup-and-recovery)
   and preserve the encryption key and deployment secrets separately. A configuration
   ZIP excludes accounts and drafts and does not replace this backup.
4. Test the target in an isolated environment using representative application
   invocations, polling, allowed and denied callbacks, publication, restart, and
   recovery. Verify file-mode startup or database-mode restoration as applicable.
5. Update the runtime deliberately. Follow the [version-aware installer](https://github.com/loomspan/loomspan-framework/blob/v1.0.0-beta.7/agent-skills/loomspan-install/SKILL.md)
   to select guidance for the new Sidecar and its bundled Framework. Update Console
   to the coordinated Framework version and verify MCP connectivity separately.

## Earlier development data

Earlier development revisions changed the V1/V3 schema definitions and replaced
format 1 configuration bundles with format 3. An earlier local development
database may therefore require a reset rather than an in-place migration. Stop
Sidecar and preserve a complete backup before deliberately recreating only that
development database, its accounts, and drafts. Reauthor incompatible content or
produce format 3 exports from compatible data. This is destructive and is not a
routine deployed-database upgrade procedure.

The former notice did not identify exact source/target versions. The audit keeps
its [original wording](../ai/throughts/development-data-reset-extract.md) for review;
do not infer a safe migration path for a deployed database from it. Resolve that
path against the selected release before changing deployed data.

Rollback of authored configuration loads retained content into a draft for a new
publication; it is not a runtime binary downgrade or database schema rollback.
See [configuration lifecycle](../agent-skills/loomspan-sidecar-authoring/references/configuration-lifecycle.md)
and [bundle compatibility](../agent-skills/loomspan-sidecar-authoring/references/configuration-bundles.md).
