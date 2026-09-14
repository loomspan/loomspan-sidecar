# Generic REST Skill Handler Code Review — Cycle 4

## Scope and Repository State

- Reviewed the ticket-scoped working-tree change against merge base `d4471c0` (`origin/main`), including modified, staged, unstaged, and untracked production code, tests, configuration, documentation, dependencies, and pipeline artifacts.
- Read the ticket, research, implementation plan, testing plan, repository guidance, and design lens. Reconstructed behavior from the current code and connected execution/security/lifecycle paths before comparing it with the plans.
- The untracked prior review documents were inventoried but not read, preserving an independent review context.
- Excluded and did not read or modify the unrelated paused SC5 files `ai/thoughts/research/2026-09-13-sidecar-packaging-release.md` and `ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md`.
- The selected Full 5-Step Pipeline remains appropriate because the change affects an outbound protocol, credential forwarding, TLS, startup activation, and client lifecycle. No profile reassessment is required.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This context changed no implementation artifact.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One production handler; route/catalog correspondence after registration; no construction cycle or startup target probe | `GenericRestSkillHandler`, `RestRouteCatalogValidator`, and catalog-independent `RestRouteLoader`/`RestTargetClients` construction | `RestRouteStartupIntegrationTest`, full application contexts | implemented |
| Required default/override route file; strict YAML; placeholders; immutable target/route maps; local validation | `RestRoutesProperties`, `RestRouteLoader`, `RestRouteConfiguration` | `RestRouteLoaderTest`, startup and restart integration tests | implemented |
| Fixed GET/POST binding and JSON-only transport | `GenericRestSkillHandler.bindUri`, recursive JSON validation, and POST body construction | `GenericRestSkillHandlerTest` path/query/body and pre-I/O rejection assertions | implemented |
| Base-path preservation and URI confinement for reserved, Unicode, dot, encoded-separator, and authority-like input | Prevalidated static route/base segments plus component encoding and dynamic path safety checks | `GenericRestSkillHandlerTest`, `RestRouteLoaderTest`, `RestRouteRestartIntegrationTest` | implemented |
| Independent none/static/caller-passthrough authentication and fixed Accept header | Target-owned immutable authentication and handler-thread `JwtAuthenticationToken` lookup | `GenericRestSkillHandlerTest`, async execution integrations | implemented |
| Per-target connect/read bounds, isolated SSL bundles/mTLS, and disabled redirects/retries | Isolated Apache clients in `RestTargetClients` with target request/socket settings, bundle SSL context, and disabled redirect/retry behavior | `GenericRestSkillHandlerTest`, `RestTransportTlsIntegrationTest` | implemented |
| Stream-time response cap, bodyless success, supported JSON/text media, charset rules, and body-free non-success handling | Apache response-stream wrapper and `BoundedRestResponse` | `GenericRestSkillHandlerTest` equality/excess, media, charset, non-2xx, redirect, and timeout cases | implemented |
| Bounded handler diagnostics surface as the existing `SKILL_FAILURE` representation | Deliberate `SkillException` messages plus unchanged `ExecutionFailureClassifier`/record projection | `AuthenticatedExecutionApiIntegrationTest`, focused handler failures | implemented |
| Original JWT reaches an independently verifying callback through async and planner execution; queued expiry fails without refresh/recheck | Existing security-context propagation plus caller-passthrough handler | `AuthenticatedExecutionApiIntegrationTest` direct, queued-expiry, and nested-planner cases | implemented |
| Startup snapshots and client lifetime preserve restart-only activation and the one framework shutdown budget | Immutable configuration, framework-higher/handler-lower `SmartLifecycle` ordering, idempotent client close | `RestRouteRestartIntegrationTest`, `RestHandlerLifecycleIntegrationTest`, existing shutdown tests | implemented |
| Public Loomspan surface, existing contexts, documentation, and wrapper build remain conformant | No internal/autoconfigure imports or Java skills; explicit empty-route fixtures; README contract; snapshot dependency retained | `SupportedLoomspanApiArchitectureTest`, complete Maven verification, diff hygiene | implemented |

## Active Project Guardrails

- Simplicity and technical debt: responsibilities are kept to a small loader/model, catalog validator, handler, client owner, and bounded response reader; no reload, compatibility aliases, second routing authority, or speculative extension contract was added.
- Framework execution authority: production and test code use only the supported `ai.loomspan.api` surface, with registration, authorization, nesting, and execution lifetime left to Loomspan.
- One admission owner and retained record: the existing Sidecar coordinator/store remain unchanged; the REST handler introduces no queue, admission semaphore, or diagnostic store.
- Trusted identity outside model inputs: passthrough reads only the framework-propagated `JwtAuthenticationToken`; invocation input cannot create headers.
- Startup activation and one shutdown budget: routes and clients are immutable startup state, and lower-phase client close occurs after framework completion/cutoff without another drain.
- Diagnostic contract: successful text is returned unchanged, deliberate HTTP messages are bounded/body-free, and the existing failure and observer-event projection remains intact.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=RestRouteLoaderTest,RestRouteStartupIntegrationTest,GenericRestSkillHandlerTest,RestTransportTlsIntegrationTest' test` — 10 focused loader, startup, handler, and TLS tests passed.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress verify` — all 55 tests passed and the executable Spring Boot jar was repackaged successfully.
- PASS — `git diff --check` — no whitespace errors; Git emitted only line-ending conversion warnings.

## Residual Risks and Optional Developer Checks

- The deterministic suite proves connection failure/read timeout and local mTLS. A black-holed-address connect-timeout observation and a developer-controlled non-production HTTPS/mTLS deployment check remain optional environment observations, not completion gates.
- SC5 still owns packaged image/resource-lifecycle and release evidence.

## Disposition

- `clean`
