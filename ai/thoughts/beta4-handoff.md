# Beta 4 remaining work and release handoff

Updated 2026-09-16. This is the sole tracker for remaining Sidecar beta 4 work.
The scaffold, authenticated execution API, generic REST handler, and local
packaging are implemented. The completion-audit corrections are verified.
Completed tickets, phases, and the old framework alignment report have been
retired; standing constraints remain in [the design lens](design-lens.md).

## QA follow-ups

These are gaps in acceptance evidence, not newly established runtime defects.
Record results here as QA exercises them; remove resolved items. Existing unit,
application, and image checks do not establish the untested subcases below.

- [ ] **F4 — explicitly deferred to framework QA during use.** Exercise real
  installed-framework successes and failures under default/`NEVER`, `ONERROR`,
  and `ALWAYS`; nested access denial and handler failure; concurrent mixed-child
  failures preserving available history and the facade's primary failure.
  Verify terminal outcome/history publication together through GET, whole-record
  TTL, intact large results/history, and ordinary failure envelopes without stack
  traces. Failures with no observer callback must terminate without invented
  events. Current diagnostic matrix tests use synthetic events/test handoffs.
  The developer excluded this test expansion from the completed audit fixes;
  it is not claimed as passed and should arise through framework QA usage.
- [ ] **JWT startup wiring:** property-bound discovery/JWKS application startup
  and invalid key-policy configurations. Direct decoder tests and public-key /
  custom-role application contexts already exist.
- [ ] **REST binding edges:** missing/null path and query values, remaining query
  collections, all-path-consumed POST `{}`, nested Resource/file/stream values,
  and base path prefixes with both trailing-slash forms. Existing encoding,
  JSON binding, and prefix tests cover only part of this matrix.
- [ ] **Route startup matrix:** unreadable/malformed files, duplicate route keys,
  invalid size/timeout/auth/bundle settings, leading-authority/path rejection,
  and catalog mismatches in a real context. Duplicate targets and several eager
  startup failures already pass. Prove readiness stays down while registration
  and route validation are incomplete.
- [ ] **Transport failure integration:** verify timeout, oversize, and media-type
  failures through actual execution GET envelopes with `SKILL_FAILURE` and no
  response-body leakage. Handler-level tests cover these; real execution tests
  cover upstream 503 and expired-token 401. Streaming caps/early abort and the
  configured socket-connect timeout were verified in the audit fixes.
- [ ] **Lifecycle combinations:** actual application management-context/async
  multicaster shutdown with clients/callers, plus container SIGTERM during active
  work and at cutoff. Focused context tests and in-process normal/cutoff tests
  pass; the image verifier's SIGTERM check occurs after work completes. Do not
  reintroduce the superseded Sidecar-local trace requirement.
- [ ] **Documentation/dependency cleanup:** strengthen configuration-reference
  evidence beyond key/default token presence if needed (manual constraints review
  matched code); document the concrete rationale for the existing Guava and
  Error Prone version pins, or remove unnecessary pins with build verification.

Fix any framework defects in the framework repository, have the developer
reinstall its snapshot, and rerun affected Sidecar checks. Use only the supported
public API in Sidecar tests. This list does not authorize new extension contracts.

## Release sequence

- [ ] Complete and record applicable QA above, honoring the explicit F4 deferral.
- [ ] Finish final framework release checks on its final source state: version
  consistency, script tests, full build, release profile, and nonpublishing
  ConsoleRelease/MavenCentralRelease workflow validation. The
  [framework readiness record](../../../loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md)
  owns framework release work and its workflow URLs/results.
- [ ] After separate publication authorization, publish framework `1.0.0-beta.4`
  to Maven Central and verify it resolves. Never overwrite an existing release.
- [ ] Switch Sidecar to released framework `1.0.0-beta.4` and its intended release
  project version. Run final Maven/integration/image checks and hosted push/PR CI
  against the published dependency before the final Sidecar release commit/tag.
  Hosted CI is deferred until framework publication; local passes do not waive it.
- [ ] After separate Sidecar publication authorization, verify the tag workflow's
  GHCR image, JAR/ZIP, checksums, immutable versions, and quick start against the
  published image. Record actual artifact identities and workflow URLs here.

Release evidence must identify the tested Sidecar commit plus dirty changes,
framework revision, exact commands/results, image digest, and hosted workflow
URLs. Neither publication has been authorized or performed by this handoff.

## Current verification baseline

### Input and retention boundaries — 2026-09-16

The Request 3 evidence gap is closed by tests for real HTTP chunked requests
at 64 bytes and above the cap (202/413), multibyte UTF-8 HTTP input admitted
at the 32-byte serialized queue cap and rejected above it (202/429), and direct
checks that completed success and pre-handoff failure tasks clear input and
authentication references while retained results, failure details, and selected
diagnostics remain available. No production behavior or framework contract changed.

`.\mvnw.cmd -B -ntp "-Dtest=AuthenticatedExecutionApiIntegrationTest,ExecutionCoordinatorTest,ExecutionDiagnosticsTest" test`
passed: 28 tests, zero failures, errors, or skips. `.\mvnw.cmd -B -ntp verify`
passed: 69 tests, zero failures, errors, or skips; executable JAR packaging
passed (`input-retention-verify.log`). The public API architecture test passed.
Testing used Sidecar base `a62d516cde559fa6da38d1d33eb04a094e4f47b9`
plus uncommitted Request 3 tests in `AuthenticatedExecutionApiIntegrationTest`,
`ExecutionCoordinatorTest`, and `ExecutionDiagnosticsTest`, and the pre-existing
uncommitted REST fixture changes listed in the snapshot alignment baseline.
The developer-installed beta 4 snapshot was used; the matching local framework
checkout was `d7d0ef03aefe44c613ff1d8591bce2a30318a6bf`. This is local
snapshot evidence only; release verification remains open. No scoped gap remains.

### Snapshot API alignment — 2026-09-16

Recheck after the framework fix: `.\mvnw.cmd -B -ntp verify` **PASS** against
the developer-installed snapshot aligned with framework
`58336b92fb63ee02836749b07b3b986e32ed5b72`. All 67 tests passed with zero
failures, errors, or skips; executable JAR packaging passed
(`snapshot-recheck-verify.log`). Sidecar remained at base
`a62d516cde559fa6da38d1d33eb04a094e4f47b9` plus the four fixture changes
listed below and accompanying ticket/handoff documentation. The original
classpath fixtures in `AuthenticatedExecutionApiIntegrationTest` and
`CustomRoleExecutionIntegrationTest` passed unchanged, resolving the startup
regression. No additional code changes were needed for this recheck.

Initial check, before the framework fix:

Sidecar base `a62d516cde559fa6da38d1d33eb04a094e4f47b9` plus uncommitted
fixture changes in `GenericRestSkillHandlerTest`, `RestRouteRestartIntegrationTest`,
`RestRouteStartupIntegrationTest`, and `RestTransportTlsIntegrationTest` was
checked against the developer-installed snapshot aligned with framework
`33dcb6dcbc13659bd34d37844ac1ff138311b9bd`.

- `.\mvnw.cmd -B -ntp '-DskipTests' compile`: PASS; production needed no edits
  (`snapshot-compile.log`).
- `.\mvnw.cmd -B -ntp test-compile`: initially FAIL with 32 compilation errors
  (`snapshot-test-compile.log`). Updated 31 `RestSkillInvocation` constructions
  to supply generation IDs and one `SkillCatalog` stub to expose its generation.
  The same command then passed (`snapshot-test-compile-fixed.log`).
- `.\mvnw.cmd -B -ntp verify`: compilation PASS; 67 tests, 57 passed and 10
  startup errors from the framework classpath discovery regression
  (`snapshot-verify.log`). All four changed test classes and both public-surface
  architecture checks passed. Packaging was not reached.

The successful recheck is local snapshot evidence, not release verification. No image
or hosted CI run was performed; no new image digest or workflow URL exists.
The fixture diff was self-reviewed; no independent review ran. Logs are ignored
local artifacts. The completed alignment ticket has been retired; its verification
evidence is retained here.

### Prior passing baseline — 2026-09-14

Local checks on 2026-09-14 used Sidecar base
`96c04bde7966d74ad2b0bfff413373eef2978bd6` plus the uncommitted audit fixes in
`ApiExceptionHandler`, `GenericRestSkillHandler`, `RestTargetClients`, and their
API/handler tests. Framework source was
`bfc2764bb661a6eccad9fd120cd687e6a911e99a`, using the developer-installed
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`.
This is snapshot evidence, not verification of a released artifact.

| Passed command | Result / retained local log |
| --- | --- |
| `.\mvnw.cmd -B -ntp '-Dtest=GenericRestSkillHandlerTest,AuthenticatedExecutionApiIntegrationTest' test` | 18 tests; `fix-focused-tests.log`. Initial failures reproduced the defects in `fix-red-tests.log`. |
| `.\mvnw.cmd -B -ntp verify` | 67 tests, no failures/errors/skips; executable JAR; `fix-full-verify.log`. |
| `docker build --tag loomspan-sidecar:audit-fixes .` | `sha256:64d9210532bf1ee6986d1c2524efa643a25a1631eeec5030a5e18cf791b918ca`; `fix-image-build.log`. |
| `python scripts/verify-image.py --image loomspan-sidecar:audit-fixes --verify-kubernetes --api-port 28080 --host-port 28081 --management-port 29091` | Planner/two-leaf JWT callback, owner isolation, probes, idle SIGTERM, invalid startup, static Kubernetes checks; `fix-image-verify.log`. |
| `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` | Nonpublishing release-plan validation. |

The fixes disable shared cookies, preserve literal query plus signs and MVC
400/405 statuses, abort unread response streams, and avoid false interruption on
socket timeouts. Connect-timeout evidence injects an OS timeout at the socket
boundary; it does not measure a real network blackout. Logs are local ignored
artifacts. The diff was self-reviewed; no separate fresh-context review ran.

## Development reference

Use the snapshot installed by the developer, aligned with the local
`C:/opendev/code/loomspan-framework` checkout. Consult that checkout's README,
`agent-skills/loomspan-docs/references/java-api/README.md`, and skill-authoring
references. The separately installed `0.1.0-SNAPSHOT` documentation skill is stale.
Sidecar does not rebuild framework source or add a snapshot repository. Retest
applicable integration after framework changes; use the platform BOM.
