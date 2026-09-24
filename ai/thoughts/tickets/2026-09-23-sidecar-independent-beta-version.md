# Start Sidecar releases at beta 1

## Outcome

Give Sidecar its own release sequence starting at 1.0.0-beta.1, independently
of the framework's beta 5 sequence.

## Requirements

- Set Sidecar development to 1.0.0-beta.1-SNAPSHOT and current release examples
  to 1.0.0-beta.1 / v1.0.0-beta.1.
- Preserve the framework beta 5 dependency and framework-first publication gate.
- Preserve historical tickets and acceptance evidence. Do not publish or tag.

## Acceptance criteria

- Maven resolves the Sidecar project version to 1.0.0-beta.1-SNAPSHOT.
- Release preparation accepts Sidecar beta 1 with framework beta 5 and continues
  rejecting snapshots, unsupported framework versions, and mismatched tags.
- Current documentation states independent versioning and the first release tag.

## Execution profile

- Recommended: Direct Implementation — No Independent Review
- Confidence: high
- Rationale: Mechanical unpublished project metadata and example updates; no
  runtime, dependency, release validation logic, or deployment behavior changes.
- Reassessment triggers: Changes to release behavior or published artifacts.

## Execution notes

- User authorized this change after agreeing on the independent version values.
- Initial working tree was clean; local Git tag listing was empty.
- Direct implementation selected; no independent review. Existing local image
  labels such as sc5-local and historical evidence are not release versions and
  remain unchanged.
- Updated the existing release preparation test inputs to exercise the intended
  Sidecar/framework version pairing without changing validation logic.
- Verification: `./mvnw.cmd -B -ntp -Dtest=ReleasePreparationTest clean test`
  passed (one test); Maven reported project 1.0.0-beta.1-SNAPSHOT. The initial
  run without clean failed to load the test class from existing build output;
  rebuilding cleanly resolved it.
- Verification: `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.1 --loomspan-version 1.0.0-beta.5 --tag v1.0.0-beta.1`
  passed. `git diff --check` passed. All acceptance criteria are satisfied.
