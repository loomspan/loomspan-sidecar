# Align the authoring skill with the released framework dependency

## Development constraints

**Sidecar is still in development. Destructively replace superseded contracts,
code and schemas; no compatibility shims, legacy adapters, parallel legacy APIs
or migration machinery solely to preserve obsolete behavior.**

Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). Choose
the simplest complete solution, minimize technical debt, and keep tests and
documentation proportional. No development-data reset is required; this policy
does not authorize deleting deployed data.

## Outcome

The authoring bundle passes its version-alignment check against the existing
released framework dependency, `1.0.0-beta.6`.

## Requirements

- Update the authoring skill's framework metadata and matching documentation
  dependency prose from the snapshot to the release.
- Update the negative test's framework-version replacement so it still injects
  an invalid version after the metadata change.
- Keep the existing Maven dependency and Sidecar version unchanged.

## Acceptance criteria

- [x] Skill metadata and documentation dependency both declare `1.0.0-beta.6`.
- [x] All six tests in `scripts/test_agent_skills.py` pass, including rejection
  of an invalid framework version.

## Execution profile

- **Recommended:** Direct Implementation — No Independent Review
- **Confidence:** high
- **Rationale:** Three mechanically clear string updates; no runtime behavior
  or contract changes, and existing bundle tests provide narrow verification.
- **Reassessment triggers:** Any required runtime or broader release changes.

## Execution notes

- User authorized the three diagnosed updates with “proceed.” Selected direct
  execution for this bounded fix; no independent review is performed.
- Initial working tree was clean. Maven publication is not checked by this
  Python test, which compares local POM and bundle metadata.
- Updated the two framework-version strings in the authoring skill and the
  negative test's replacement target. Maven dependency and Sidecar version are
  unchanged. The design lens calls for these direct replacements with no
  compatibility code or broader release changes.
- Verification: `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`
  reproduced the reported metadata mismatch before editing (six tests, one
  failure); after editing all six tests passed, including invalid framework
  metadata rejection and linked-asset rejection, with no skips.
- Inspected the scoped diff: only the three intended string updates and this
  ticket are included. No live services, sensitive data, or destructive
  operations were involved. Maven build/release verification is outside this
  metadata-only fix; no independent review was performed under the direct route.
