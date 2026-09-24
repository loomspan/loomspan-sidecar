# Use the CI framework version in browser verification

## Outcome

Production Compose browser verification must resolve the same published
framework version as the preceding CI build.

## Context

The supplied CI log shows a successful Docker build and initial production
checks, followed by Maven dependency resolution failure in `browser()`.
The workflow passes the release override to its direct Maven commands, but
the Python verifier launches Maven using the POM's local snapshot default.

## Requirements

- Apply the existing beta 5 release override to every Maven invocation in CI.
- Preserve local snapshot development and the guarded release workflow.
- Do not add a snapshot repository or build framework source.

## Acceptance criteria

- Maven invoked through the browser verifier inherits the CI override and
  evaluates `loomspan.version` as `1.0.0-beta.5`.
- Without the CI environment, Maven evaluates the snapshot default.
- Operations documentation explains the override's scope.

## Execution profile

- Recommended: Direct Implementation — No Independent Review
- Confidence: high
- Rationale: Localized CI argument propagation with no application changes;
  narrow executable verification is sufficient.
- Reassessment triggers: Changes to runtime behavior or release policy.

## Execution notes

- User requested a fix for the supplied CI failure; working tree was clean.
- Direct implementation; no independent review performed.
- Moved the duplicate command-line override to job-level `MAVEN_ARGS`.
  Maven wrapper uses Maven 3.9.11, whose launcher supports this variable.
  The browser verifier already copies the parent environment.
- PASS: inline Python verification imported `scripts/verify-production.py`,
  read `MAVEN_ARGS` from the workflow, and invoked `browser()` with its command
  runner intercepted. Using the environment passed by that function, the real
  `mvnw.cmd -q -DforceStdout help:evaluate -Dexpression=loomspan.version`
  returned `1.0.0-beta.5`. Without `MAVEN_ARGS`, it returned
  `1.0.0-beta.5-SNAPSHOT`. The check also asserted a single workflow override
  and the expected browser-test selector.
- Full Docker/browser integration was not rerun locally; rerun hosted CI to
  verify the complete sequence. The targeted check proves argument propagation
  and version selection, not the remaining integration assertions.
