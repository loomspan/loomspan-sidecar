# Pin framework beta 7 for Sidecar beta 2

## Development constraints

**Sidecar is still in development. Destructively replace superseded contracts,
code and schemas; no compatibility shims, legacy adapters, dual API paths or
migration machinery solely to preserve obsolete behavior.** Apply the
[design lens](../design-lens.md), keeping changes and verification proportional.
No development-data reset is required by this dependency-pin update; deployed
data deletion is not authorized.

## Outcome

Sidecar uses released framework `1.0.0-beta.7` consistently and documents how
to publish Sidecar `1.0.0-beta.2` without publishing during implementation.

## Requirements and acceptance criteria

- [x] Pin the POM, CI, release guards, packaging helper and authoring metadata
  to framework beta 7; verify metadata and release-input acceptance/rejection.
- [x] Retest Sidecar integration against beta 7 with the Maven verification
  suite; use no framework-only results as Sidecar evidence.
- [x] Update current repository guidance and publishing instructions while
  preserving dated historical beta 6 acceptance evidence.
- [x] Keep Sidecar at `1.0.0-beta.2-SNAPSHOT`; no commit, tag, push or publication
  is part of this implementation.

## Execution profile

- **Recommended:** Direct Implementation — No Independent Review
- **Confidence:** high
- **Rationale:** Mechanically replace an exact dependency pin and its existing
  guards/metadata; no application contracts or implementation changes are
  planned. Existing integration verification checks the dependency update.
- **Reassessment triggers:** Framework incompatibility requiring application
  contract, lifecycle, security or stored-data changes.

## Execution notes

The worktree was clean at entry. Use the beta 7 tag's documentation from the
local framework checkout, whose development branch has advanced to beta 8.
The user authorized the upgrade and requested release instructions only.
No independent review is performed; reduced assurance.

## Verification (2026-10-01)

- PASS: `python scripts/sidecar_version.py check` — Sidecar beta 2 SNAPSHOT,
  framework beta 7, matching authoring metadata.
- PASS: `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.7 --tag v1.0.0-beta.2`.
  The same invocation with framework beta 6 was rejected with exit 1.
- PASS: `python -m unittest discover -s scripts -p 'test_*.py' -v` — 15 tests.
- PASS: `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — 11 tests.
- PASS: `.\mvnw.cmd -B -ntp verify` — 255 tests, zero failures/errors,
  three Compose-only methods skipped; executable JAR packaged successfully.
- PASS: `docker build --tag loomspan-sidecar:beta7-local .`.
- PASS: `python scripts/verify-image.py --image loomspan-sidecar:beta7-local`.
- PASS: `python scripts/verify-production.py --image loomspan-sidecar:beta7-local` —
  HTTPS browser checks (including the ordinary Maven run's skipped methods),
  configuration transfer/rollback, backup restoration and shutdown checks.
  Shutdown completion took 2.31 seconds; framework cutoff took 4.50 seconds;
  both exited 143. Disposable fixture resources were cleaned up.
- PASS: `git diff --check`.
- Framework beta 7 POM returned HTTP 200 from Maven Central. The read-only
  `git ls-remote --tags origin refs/tags/v1.0.0-beta.2` check found no tag.
  Repeat remote availability checks immediately before publication.

No application Java changes were required. Historical beta 6 acceptance
records remain unchanged. No commit, tag, push or publication was performed.
The release-profile build against a non-SNAPSHOT Sidecar version and hosted
CI/publication remain release-time checks in the operations guide. No
independent review was performed; reduced assurance.
