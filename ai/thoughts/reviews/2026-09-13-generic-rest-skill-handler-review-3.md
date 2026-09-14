# Generic REST Skill Handler Code Review — Cycle 3

## Scope and Repository State

Reviewed the complete SC4 change against the ticket, research, implementation
plan, testing plan, repository instructions, design lens, current `main` at
`d4471c0`, and the staged, unstaged, and untracked inventory. The review traced
startup loading and catalog validation, input/URI binding, authentication,
per-target HTTP/TLS configuration, bounded response handling, asynchronous
failure projection, restart behavior, and shutdown ordering beyond the diff
hunks. Prior review documents were excluded from this independent review.

The untracked SC5 ticket and research artifact are unrelated paused work and
were neither read nor modified. The untracked SC4 research, plans, production
package, tests, fixture, and this review document are ticket-scoped.

## Findings

No actionable findings remain.

## Findings Resolved in This Context

### [P2] Reject syntactically invalid route paths during startup

- Location: `src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java:254`
- Scenario: A configured route such as `/bad[path` passed loader validation,
  then `GenericRestSkillHandler.bindUri` failed at the first invocation when it
  constructed the final `URI`.
- Impact: Invalid route configuration could pass readiness and turn a required
  startup diagnostic into a runtime skill failure.
- Evidence: The loader validated traversal and percent escapes but did not parse
  the compiled raw path as URI syntax; URI construction existed only in the
  invocation path.
- Fix: Validate the configured path as a URI after replacing whole-segment
  variables with a safe sentinel, and cover the case in
  `RestRouteLoaderTest.java:44`.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One production handler and exact completed-catalog correspondence | `GenericRestSkillHandler`, `RestRouteCatalogValidator` | `RestRouteStartupIntegrationTest`, full suite | implemented |
| Required strict route file, override, empty maps, placeholders, local diagnostics | `RestRoutesProperties`, `RestRouteLoader` | `RestRouteLoaderTest`, application contexts | implemented |
| GET/POST binding and JSON-only values before I/O | `GenericRestSkillHandler.bindUri` and recursive validator | `GenericRestSkillHandlerTest` | implemented |
| Base path and component encoding cannot escape target | loader path compilation and handler component encoding | handler traversal/reserved/Unicode cases; new invalid-URI startup case | implemented |
| None/static/passthrough authentication and fixed Accept | handler header construction from target config/security context | handler auth capture and async callback tests | implemented |
| Per-target timeouts, SSL/mTLS, no redirects or retries | `RestTargetClients` isolated clients | handler timeout/redirect counters and `RestTransportTlsIntegrationTest` | implemented |
| Streaming cap, bodyless success, media and charset rules | `BoundedRestResponse`, bounded Apache entity stream | focused response matrix | implemented |
| Bounded body-free failures project as `SKILL_FAILURE` | handler `SkillException` construction and existing classifier | focused diagnostics and authenticated API polling | implemented |
| Original JWT survives queue/planner and callback verifies it | existing security propagation plus passthrough handler | `AuthenticatedExecutionApiIntegrationTest` | implemented |
| Restart-only activation and client lifetime through framework cutoff | immutable configuration and lower-phase lifecycle | restart and lifecycle integration tests | implemented |
| Public framework surface and documentation | `ai.loomspan.api` imports, README, standard namespaces | ArchUnit in full suite and `git diff --check` | implemented |

## Active Project Guardrails

- The implementation uses only the supported `ai.loomspan.api` surface and the
  authorized `RestSkillHandler`; the architecture test covers production and
  test code.
- Framework registration, validation, invocation, security propagation, and
  execution lifetime remain framework-owned. Sidecar owns only routing,
  transport, admission integration, and introduced-client cleanup.
- Trusted identity is read from `JwtAuthenticationToken` on the handler thread
  and is not placed in model inputs or minted/refreshed/exchanged.
- Routes and clients are immutable startup snapshots. The handler's lower
  lifecycle phase preserves the single framework shutdown budget.
- The design is direct and proportional: one strict loader, immutable model,
  catalog validator, handler, client owner, and bounded response helper.

## Open Questions and Assumptions

- None affecting correctness or review confidence.

## Verification Results

- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=RestRouteLoaderTest,RestRouteStartupIntegrationTest,GenericRestSkillHandlerTest,RestTransportTlsIntegrationTest' test` — 10 focused tests passed after the fix.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress verify` — complete 55-test suite and repackaging passed.
- PASS — `git diff --check` — no whitespace errors; Git emitted only existing LF-to-CRLF conversion warnings.

## Residual Risks and Optional Developer Checks

- A real developer-managed non-production HTTPS/mTLS callback remains an
  optional deployment observation; deterministic local mTLS coverage passed.
- A black-holed non-production address may optionally confirm wall-clock connect
  timeout behavior on a particular host. The implementation sets the pinned
  Apache connect/lease timeouts, and automated tests cover connection refusal and
  effective read timeout without contacting external services.

## Disposition

- `fixes-applied`
