# PR-4 Administrator Setup and Recovery Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket-scoped staged, unstaged, and untracked change against `main` at `7706c5be5290ac99174a06ee85411a0a04841b08`, including the command dispatcher, SQLite lock and identity transitions, browser and JSON entry points, Compose files, verification scripts, and operator documentation. The ticket, research, implementation plan, testing plan, repository guidance, and design lens were read. No staged changes were present. The review included the call paths and tests beyond individual diff hunks.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. A candidate gap in coverage for the existing reserved administrator was ruled out by `ManagementMailIntegrationTest.reservedInitialAdministratorCompletesWithoutMail`, which exercises the old set token, reserved address, and direct completion. No implementation artifact changes remain from this context.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Capturable fresh setup credential without services | `ManagementAdminCommand.generate-setup-token`, `ManagementTokens.generate`, pre-Spring dispatch | `ManagementAdminCommandTest`, `scripts/verify-image.py` | Implemented |
| SMTP-free atomic first setup and validation | `ManagementIdentityService.setup`, form and JSON setup handlers, transaction and attempt limiter | `ManagementIdentityStoreTest`, `ManagementHttpIntegrationTest`, image smoke | Implemented |
| Durable closure and safe pending transition | Bootstrap state checks, reserved account ID/address guard, token consumption | `ManagementIdentityStoreTest`, `ManagementMailIntegrationTest.reservedInitialAdministratorCompletesWithoutMail` | Implemented |
| Offline recovery on eligible existing store with exclusion | `ManagementAdminCommand.issue`, `StorageLock`, pre-Spring dispatch | `ManagementAdminCommandTest`, `StorageLockTest`, image and production smoke | Implemented |
| Digest-only, expiring, single-use reset; session and editing invalidation | `ManagementTokens.issue`, `ManagementIdentityService.redeem`, credential version and editing state | Store, HTTP, command, and local SMTP tests | Implemented |
| Optional SMTP with retained invitations and email recovery | Production Compose defaults; unchanged mail service paths; console guidance | `ManagementMailIntegrationTest`, `ManagementMailHttpIntegrationTest`, production smoke | Implemented |
| README-linked local and production operator flows | `docs/admin-access.md`, `README.md`, operations and production guides | Reviewed against packaged command arguments and disposable image/Compose checks | Implemented |
| Sidecar-owned verification and framework boundary | Java tests and disposable scripts; public Loomspan architecture test | Maven `verify` and image/production scripts | Implemented |

## Active Project Guardrails

- The change uses Sidecar management and storage APIs, and the architecture test forbids application and test dependencies on `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`.
- Existing password hashing, token persistence, transaction boundaries, session versioning, and editing-state cleanup are reused. No new framework SPI, dependency, or migration was introduced.
- The implementation remains within the beta 5 snapshot workflow and does not publish or release artifacts.

## Open Questions and Assumptions

None affecting correctness or review confidence.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ManagementIdentityStoreTest,ManagementHttpIntegrationTest,ManagementAdminCommandTest,StorageLockTest' test` — 18 tests passed during review. One redundant test added during review was subsequently removed after locating equivalent existing coverage; its removal restores the pre-review implementation state.
- PASS — `.\mvnw.cmd -B -ntp verify` — build succeeded; 265 tests, zero failures/errors, three skipped. This run began before the redundant test was removed, so it includes that one passing test.
- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ManagementIdentityStoreTest,ManagementMailIntegrationTest' test` — nine tests passed after restoring the pre-review implementation state, including the existing reserved-account coverage.
- PASS — `docker compose --env-file examples/production/production.env.example -f examples/production/compose.yaml config --quiet` — optional SMTP fields interpolate successfully.
- PASS — `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --api-port 60783 --host-port 60785 --management-port 60786` — disposable packaged image generation, no-SMTP setup, live-store recovery rejection, stopped-volume issuance, and manual redemption passed; disposable Compose resources were removed. The initial invocation with default ports failed because host port 8081 was already allocated, then cleanup succeeded and the free-port rerun passed.
- PASS — `python scripts/verify-production.py --image loomspan-sidecar:sc5-local` — disposable production Caddy/Compose flows passed, including SMTP-free setup, configured-mail regressions, stopped-volume offline reset/manual redemption, and shutdown checks; disposable resources were removed.
- PASS — `git diff --check` — no whitespace errors; Git emitted only checkout line-ending notices.
- The full Maven run included every test from the unchanged implementation plus the one subsequently removed redundant test. The post-removal focused run confirms the actual current reserved-state suite. Step 4 separately reported a 264-test full `verify` against the current implementation files.

## Residual Risks and Optional Developer Checks

- On an actual deployment, verify volume permissions and HTTPS certificate trust/hostname before using the operator guide. This is an environment-specific observation, not a routine test gate.

## Disposition

- Clean. No actionable findings remain and this review left implementation artifacts unchanged.
