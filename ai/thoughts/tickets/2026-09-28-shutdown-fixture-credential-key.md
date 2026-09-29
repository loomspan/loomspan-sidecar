# Restore encrypted credentials in the shutdown verification fixture

## Development constraints

Apply the [design lens](../design-lens.md#simplicity-and-technical-debt): choose the simplest sufficient solution and minimize technical debt. Destructively replace superseded development contracts, code and schemas; add no compatibility shims, legacy adapters or parallel obsolete behavior. No development-data reset is required, and no deployed data may be deleted.

## Outcome

The production verifier's shutdown subprocess must restore its copied fixture database with the same encryption key used by the production fixture, allowing completion and cutoff checks to reach execution.

## Context

The supplied CI log fails readiness because the restored selected configuration needs `LOOMSPAN_SIDECAR_CREDENTIAL_KEY`. The production verifier configures this key in its fixture environment but omits that environment when launching `verify-shutdown.py`. The earlier log collection timeout is expected regression-test output, not the integration failure.

## Requirements

- Pass the production fixture environment to the shutdown subprocess without exposing the key in command arguments or logs.
- Preserve file mode's behavior: it neither requires nor inspects this key. Database mode requires it when encrypting or decrypting provider credentials, not merely because database mode is selected.
- Keep this change confined to verification scripts, regression tests, and operator documentation. Preserve the stopped source database and isolated fixture cleanup.

## Acceptance criteria

- Executable regression coverage detects omission of the fixture environment at the production-to-shutdown handoff and verifies propagation to shutdown Compose commands.
- Existing shutdown diagnostic/cleanup regression checks pass.
- Operations documentation states the matching-key requirement for encrypted database fixtures and the file-mode distinction.

## Execution profile

- Recommended: Direct Implementation — No Independent Review.
- Confidence: high.
- Rationale: a localized, mechanically clear test-harness environment correction with narrow regression verification; no application contract changes.
- Reassessment triggers: any required application behavior, encryption contract, lifecycle, or deployment-policy change.

## Execution notes

- User explicitly approved direct implementation after diagnosis and clarification of file/database key requirements.
- Initial working tree was clean. No independent review is part of this route.
- Docker is available. A full production fixture rerun may be used if the local image/build prerequisites are available; narrow executable regression coverage is required.
- Implemented the production shutdown launch as a small helper with a required environment argument, called with `compose_environment`. The helper forwards that environment to the subprocess, retaining the key outside command arguments. No application or shutdown lifecycle behavior changed.
- Acceptance evidence: `ProductionHandoffTest` exercises that launch with a fixture key different from the parent environment; `StartupFailureTest` verifies the inherited key reaches Compose initialization, startup, diagnostics, and cleanup for both completion/cutoff modes, including log timeouts. Source database bytes remain unchanged. Operations guidance now documents the matching-key requirement, empty-database startup, and file-mode distinction.
- Red/green evidence: `python scripts/test_verify_shutdown.py` first failed the new handoff assertion (`env` was `None`), then passed both tests after environment forwarding. `python -m unittest discover -s scripts -p 'test_*.py'` passed all 8 tests. Expected simulated log-timeout messages remain part of passing regression output.
- `python scripts/verify-shutdown.py --image loomspan-sidecar:ci-fixture-fix --validate-only` passed actual Docker Compose validation for both modes. `git diff --check` passed. These checks do not start Sidecar or prove admitted-work completion against a running image.
- Optional follow-up: run `python scripts/verify-production.py --image <current-built-image>` to repeat the complete Docker/browser/shutdown integration against a current build. NOT RUN here: the available `loomspan-sidecar:ci-fixture-fix` image was created September 25 and carries no application source-revision label, and no packaged current JAR is present. Rebuilding/rerunning the broader Java/browser suite is unnecessary for the localized script handoff; lifecycle behavior has not been re-proven in this change.
