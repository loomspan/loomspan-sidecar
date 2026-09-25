# PR 6.2 Scoped Personal Access Tokens Code Review — Cycle 2

## Scope and Repository State

Independently reviewed the ticket-scoped unstaged and untracked changes against `main` (the current branch and comparison base). No staged changes or ticket commits exist. Scope includes the V4 token table; token repository, service, controller, bearer filter and credential record; management security, identity, editing and session changes; console UI; documentation; and new and changed tests. Research and both plans informed conformance checks only. I traced the security chains, route mappings, token persistence and account transitions, lease admission, validation, publication locks, and execution JWT boundary beyond diff hunks. No unrelated developer change required editing.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation artifact changed in this review context.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Own-token lifecycle, one-time secret, expiry | `ManagementPersonalTokenService`, `ManagementPersonalTokenRepository`, V4 table, lifecycle controller and console | `ManagementPersonalTokenHttpIntegrationTest` lifecycle, limits and expiry cases | Implemented |
| Shared API, bearer/cookie separation, CSRF | `ManagementBearerFilter.required`, `ManagementCredential.bearerOnly`, management chain; shared controllers unchanged | PAT HTTP and bearer filter tests; existing browser CSRF tests | Implemented |
| Preset and live-role limits, private draft/lease | Explicit route map, current-account resolution, `ManagementEditingService` origin and gate checks | PAT HTTP role/handoff tests and editing integration suite | Implemented |
| Edit versus Publish, exact candidate, no admin PAT | Publish admission rechecks token, lease, candidate, validation and base; route map denies lifecycle/admin/takeover | PAT publish/handoff tests and existing PR 6.1 publication tests | Implemented |
| Revocation, expiry, account transition, delayed work | Token revocation and account change under editing transition; `ownLease` and publish gate checks | PAT HTTP account/expiry tests and latch-controlled editing service tests | Implemented |
| Management/execution credential boundary | Separate management and JWT security chains; PAT route map | PAT HTTP execution denial plus existing execution/management tests | Implemented |
| Bounded abuse and safe audit | Active/issuance caps, bounded attempt limiter, generic failures, structured ID logs | Bearer filter limit test, PAT HTTP limit and captured-log tests | Implemented |
| Framework and documentation | Public framework dependency remains pinned to local beta.5 snapshot; admin, integration and operations guides updated | Full `verify`, including architecture and integration tests | Implemented |

## Active Project Guardrails

- Simplicity/design lens: one shared management API and a small token service/filter; no compatibility adapter, OAuth infrastructure or second authoring path. V4 adds new metadata without a development-data reset.
- Framework boundary: no new dependency on `ai.loomspan.internal..` or `ai.loomspan.autoconfigure..`; full architecture test passes against the installed beta.5 snapshot.
- Identity and editing: credentials derive from Spring Security authentication, never payload labels; one saved draft and one origin-bound lease remain; handoff rotates generation. Publication retains exact validation and base admission under existing locks.
- Execution boundary: management PATs are never accepted or forwarded as execution JWTs.

## Open Questions and Assumptions

None affecting correctness or review confidence.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ManagementPersonalTokenHttpIntegrationTest,ManagementBearerFilterTest,ManagementEditingServiceTest' test` — 19 tests, zero failures/errors/skips.
- PASS — `.\mvnw.cmd -B -ntp verify` — 216 tests, zero failures/errors, three optional production Compose browser skips; build successful against the installed framework snapshot.
- PASS — `git diff --check` — no whitespace errors (Git printed line-ending conversion notices only).

## Residual Risks and Optional Developer Checks

The configured HTTPS client call with an environment-injected PAT and manual clipboard/UI inspection were not run; they are optional deployment/browser observations. Automated HTTP and persistence tests cover the release behavior without live external services.

## Disposition

`clean`
