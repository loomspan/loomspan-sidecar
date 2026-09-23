# Make Sidecar documentation useful to application developers

## Outcome

A developer evaluating Sidecar can understand its purpose, try the local
service, find the application integration contract, and reach detailed
deployment and operations guidance without reading an implementation reference
from top to bottom.

## Requirements

- Keep the README focused on what Sidecar is, why an application would use it,
  a local starting point, and links to details. The POM and operations guide
  carry dependency versions and current source-build prerequisites.
- Preserve useful technical reference material in an integration guide and an
  operations guide; retain the existing production Compose procedure.
- Describe the empty initial catalog accurately. Do not present the local
  smoke check as a completed execution.
- Keep existing documentation links and the configuration-reference test valid.

## Acceptance criteria

- [x] The README gives a clear application-developer entry point and links to
  the deeper guides.
- [x] The integration and operations guides retain the relevant technical
  details and point readers to a first execution workflow.
- [x] Existing documentation backlinks and the configuration-reference test
  point to the moved content.
- [x] Local Markdown links and configuration-reference tests pass.

## Context

The previous README mixed a brief deployment start with detailed architecture,
management API, recovery, and release material across more than 1,000 lines.
The existing `examples/production/README.md` remains the production procedure.

## Execution profile

- **Recommended:** Direct Implementation — No Independent Review
- **Confidence:** high
- **Rationale:** This is a bounded documentation reorganization with no change
  to runtime behavior or external contracts; link and reference checks provide
  narrow verification.
- **Reassessment triggers:** New behavior or deployment changes beyond moving
  and clarifying existing guidance.

## Execution notes

The direct route has no independent review. The first local walkthrough is
explicitly a smoke check because an empty database has no published skills.
