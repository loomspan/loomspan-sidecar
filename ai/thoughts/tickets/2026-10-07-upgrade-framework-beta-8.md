# Run Sidecar with Loomspan Framework beta.8

## Development constraints

**Sidecar is still in development. Destructively replace superseded development
contracts; do not introduce compatibility shims, legacy adapters, dual API paths,
or migration machinery solely to preserve obsolete behavior.**

Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). Choose
the simplest solution that fully satisfies the requirements and minimize technical
debt. Keep code, tests, documentation and process proportional. Document any
required development-data reset and its impact; do not delete deployed data.

## Outcome

Sidecar builds and runs against released Loomspan Framework `1.0.0-beta.8`, with
matching integration behavior, release validation and current user guidance.

## Requirements

- Replace the beta.7 framework dependency with beta.8 and reconcile affected
  integration code, tests, configuration, authoring guidance and release tooling.
- Consult `v1.0.0-beta.8` in `C:/opendev/code/loomspan-framework`; its development
  branch has advanced. Use only supported `ai.loomspan.api` contracts and documented
  framework settings. Preserve the ArchUnit boundary.
- Establish integration evidence from Sidecar's own checks. Do not rebuild the
  framework or introduce a snapshot repository.
- Keep Sidecar's independent version unchanged. Do not publish, tag, overwrite
  releases or rewrite historical implementation evidence as part of this upgrade.

## Acceptance criteria

- [x] Maven resolves beta.8 and Sidecar's required build and integration tests pass.
- [x] Affected public-contract integration and fixtures agree with beta.8;
  framework boundaries remain enforced.
- [x] Release validation and packaged authoring skill metadata select beta.8,
  and relevant script/metadata checks pass.
- [x] Current documentation and repository dependency guidance consistently
  describe beta.8; any required development-data reset is documented.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** Framework compatibility and configuration impacts require
  investigation before determining the complete upgrade scope.
- **Reassessment triggers:** Material public-contract, persisted configuration,
  lifecycle or protocol changes must be covered by research, planning and review.

## Execution notes

- The developer explicitly selected implementation in the current chat followed
  by an independent `ai/commands/5_code_review.md` review, overriding the proposed
  full pipeline. No research, implementation-plan or test-plan artifacts are required.
- Initial tracked checkout was clean. All current edits belong to this upgrade.
- Preserve Sidecar `1.0.0-beta.2`; no publication, tag or release overwrite is authorized.
- beta.8 extends `SkillDescriptor` with nullable `outputSchema`; update the direct
  constructor fixture and verify the HTTP metadata without a compatibility shim.
- No Sidecar schema changes or development-data reset are required by this upgrade.

## Implementation verification

- `./mvnw.cmd -B -ntp verify`: build success, 256 tests, zero failures/errors,
  three opt-in Docker deployment tests skipped. Java 21 from `C:/hamdev/jbrsdk21`.
- `python scripts/sidecar_version.py check`: Sidecar beta.2 / Framework beta.8.
- `python -m unittest discover -s scripts -p 'test_*.py'`: 15 passing tests.
- `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'`: 11 passing tests.
- `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.8 --tag v1.0.0-beta.2`: passed without publication.
- Inspected executable JAR: bundled starter and producer metadata use beta.8.
- Validated current beta.8 framework documentation target paths against the local tag.
