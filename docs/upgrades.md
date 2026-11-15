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
5. Update the runtime deliberately. Follow the version-aware installer at
   `agent-skills/loomspan-install/SKILL.md` in the matching Framework checkout
   described by [assistant setup](setup.md#equip-your-development-assistant)
   to select guidance for the new Sidecar and its bundled Framework. Update Console
   to the coordinated Framework version and verify MCP connectivity separately.

## Current development snapshot

This checkout uses the locally installed Framework `1.0.0-beta.10-SNAPSHOT`.
Its `openai` driver uses the Responses API: a service root ending in `/v1`
receives requests at `/v1/responses`. Chat Completions-only services are no longer
supported. Check the matching Framework connection documentation before using
an OpenAI-compatible provider. The native `ollama` driver is also removed; use
`openai` with a Responses-compatible Ollama endpoint and the documented credentials.

Sidecar's model test fixtures and local quickstart host use Responses without
calling paid providers. Release preparation requires a published Framework
dependency; the local development snapshot is not a release artifact.

## Configuration and recovery

Use complete file startup configuration or the selected database publication.
Retained-history rollback loads complete content into a draft for validation and
new publication; it does not restore accounts or change runtime binaries.
Configuration bundles use format 4 and transfer authored skill/execution content,
with optional authenticated encrypted credentials. See
[configuration lifecycle](../agent-skills/loomspan-sidecar-authoring/references/configuration-lifecycle.md)
and [bundles](../agent-skills/loomspan-sidecar-authoring/references/configuration-bundles.md).
