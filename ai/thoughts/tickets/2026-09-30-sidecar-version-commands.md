# Manage Sidecar versions consistently

**Development policy: destructively replace superseded contracts, code and
schemas; no compatibility shims, legacy adapters, or parallel legacy APIs.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
No development-data reset is required.

## Context and outcome

The developer authorized a Sidecar equivalent of the framework's `check`,
`set`, and `tag` commands before preparing the first release. Sidecar's version
must remain independent of its framework dependency. Existing release packaging
and publication remain in `prepare-release.py` and the release workflow.

## Acceptance criteria

- Check root POM and authoring skill Sidecar/framework version consistency.
- Set only the POM project version and skill Sidecar metadata from a clean tree.
- Create a local annotated tag only for a matching committed release version,
  with a released framework dependency; reject dirty trees and existing tags.
- Do not push, publish, or update the actual project version in this task.
- Document usage, exercise guards in disposable repositories, and run consistency
  checks in CI and release verification.

## Execution profile

Direct Implementation — No Independent Review. The change is confined to local
developer tooling and mechanical metadata checks; no application, persisted
data, or deployment contract changes. Narrow Python verification is sufficient.

## Execution notes

Implemented `scripts/sidecar_version.py`, targeted Git fixture tests, operations
documentation, and workflow checks. Removed the current snapshot literal from
the agent skill version-mismatch test so it remains valid after version changes.
The earlier README edit is preserved as separate authorized work.

Verification: version check passed; seven version-command tests and six existing
agent-skill tests passed; `git diff --check` passed. No independent review was
performed. No project version, commit, tag, or publication was created.
