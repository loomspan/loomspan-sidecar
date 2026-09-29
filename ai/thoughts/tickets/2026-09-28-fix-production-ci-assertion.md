# Verify production CI against database-owned credentials

## Development constraints

**Sidecar is in development. Destructively replace superseded contracts, code
and schemas; no compatibility shims, legacy adapters, parallel legacy APIs or
migration machinery solely to preserve obsolete behavior.**

Apply the [design lens](../design-lens.md#simplicity-and-technical-debt). Use the
simplest complete solution with minimal technical debt and proportional tests
and documentation. No development-data reset is required; this policy does not
authorize deleting deployed data.

## Outcome

Production CI validates the current database-owned credential configuration
instead of failing on an obsolete environment-variable assertion.

## Requirements

- Replace the verifier's expectation of a process-level primary API key with
  an assertion that the obsolete variable is absent.
- Preserve the fixture's credential provisioning through the management API.
- Inspect related CI assumptions and verify the affected configuration using
  locally available tools; report any unrun container or hosted checks honestly.
- Do not restore obsolete environment configuration or change runtime contracts.

## Acceptance criteria

- [x] The production verifier rejects the obsolete API-key environment variable
  and accepts its absence in the current rendered Compose configuration.
- [x] Relevant verification passes, with full integration limitations recorded.

## Execution profile

- **Recommended:** Direct Implementation — No Independent Review
- **Confidence:** high
- **Rationale:** A localized assertion correction tests the existing production
  configuration; no deployment configuration or runtime behavior changes.
- **Reassessment triggers:** Required changes to runtime contracts or deployment.

## Execution notes

- User authorized fixing the CI pipeline after diagnosis. Selected direct
  execution; no independent review is performed. Initial working tree is clean.
- The shutdown timeout in the supplied output was an intentional mocked test;
  the actual failure was the production verifier's API-key dictionary lookup.
- Replaced only the stale assertion in `scripts/verify-production.py`; the
  source and destination fixtures still provision `provider.primary.key` through
  the management API. Both CI and release workflows already call this verifier,
  so no workflow or runtime changes are needed.
- Checked related references. `verify-production-tls.py` separately supplies
  environment settings to its direct TLS fixture; it is not called by these
  workflows and does not assert that production Compose supplies an API key.
  That separate fixture is outside this correction.
- PASS: `docker compose -f examples/production/compose.yaml config --format json`
  invoked from inline Python using the verifier's AST-extracted fixture values
  (image label `loomspan-sidecar:ci-fixture-fix`, origin
  `https://localhost:8443`, HTTP port 8080). Rendering starts no containers and
  does not depend on the image contents. Before editing, executing the exact
  API-key assertion against the rendered service reproduced
  `KeyError: LOOMSPAN_CONNECTIONS_PRIMARY_API_KEY`. After editing, executing all
  existing Compose preflight assertions passed. Executing the corrected
  assertion after injecting the obsolete key with `fixture-only`, then an empty
  value, raised `AssertionError` in both cases as required.
- PASS: `python -m unittest discover -s scripts -p 'test_*.py' -v`
  (7 tests, including the intentionally mocked shutdown-log timeout).
- PASS: `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`
  (11 tests).
- PASS: `git diff --check`; reviewed the complete ticket-scoped diff. No
  credentials, runtime changes, or unrelated edits were introduced.
- NOT RUN: `python scripts/verify-production.py --image loomspan-sidecar:ci`
  and the full hosted CI pipeline. Docker is available, but there is no current
  packaged JAR/image; the existing fixture image was built on September 25 and
  cannot establish integration of the current tree. Current-image container,
  browser and shutdown integration remain for CI. No framework rebuild or
  publication was performed. This is Direct Implementation with no independent
  review, supported by narrow executable verification of the changed assertion.
