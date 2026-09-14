# Generic REST Skill Handler Code Review — Cycle 1

## Scope and Repository State

- Reviewed in pipeline mode under the selected Full 5-Step Pipeline profile.
- Reconstructed the ticket-scoped implementation from `main` at
  `d4471c0393051ee2ba0b8e3decfd48690bb5f404`, the unstaged tracked changes,
  and the untracked SC4 production, test, fixture, research, and plan files.
- There were no staged changes. The untracked SC5 ticket and research artifact
  were treated as unrelated paused work and were neither read nor modified.
- Review covered startup parsing and catalog ordering, URI/input binding,
  credential forwarding, target isolation, HTTP/TLS behavior, streamed response
  handling, failure projection, restart semantics, lifecycle ordering,
  architecture boundaries, documentation, and the quality of their tests.

## Findings

No actionable findings remain after the fixes listed below and a complete
re-review of the resulting change.

## Findings Resolved in This Context

### [P1] Reject encoded traversal before constructing an outbound URI

- Location: `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:103`
- Scenario: A path input such as `%2e%2e`, `%252e%252e`, `../child`, or an
  encoded slash/backslash passed the original exact `.`/`..` check and could be
  decoded again by a callback stack into traversal syntax.
- Impact: A supported path-variable invocation could escape the intended
  endpoint path after downstream decoding, violating the configured base-path
  security boundary.
- Evidence: The original guard checked only exact literal dot segments; the
  testing plan explicitly required encoded traversal and separator cases to be
  rejected before I/O.
- Fix: Normalize nested percent markers, reject encoded separators and any
  literal/decoded dot segment before URI construction, and add zero-request
  regression cases in `GenericRestSkillHandlerTest`.

### [P1] Keep resolved secrets out of nested startup exceptions

- Location: `src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java:141`
- Scenario: A placeholder-backed URL, duration, size, or SSL-bundle name could
  fail semantic parsing and remain in the preserved cause even though the
  top-level message omitted the resolved value.
- Impact: Spring's startup stack trace could disclose a resolved secret in logs.
- Evidence: `URISyntaxException` and bundle lookup failures can embed the input
  value in their messages; the existing test asserted only the outer message.
- Fix: Retain field-addressed sanitized failures without value-bearing semantic
  causes and assert the complete cause-message chain excludes a resolved
  sentinel value.

### [P2] Validate effective timeout, port, and static-header boundaries

- Location: `src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java:187`
- Scenario: Case-variant duplicate headers could create ambiguous HTTP fields,
  port `65536` survived URI parsing, and a positive sub-millisecond duration
  truncated to Apache's zero-millisecond infinite timeout.
- Impact: Invalid configuration could pass startup and produce ambiguous
  authentication headers, invocation-time URI failure, or an unbounded wait.
- Evidence: Header names are case-insensitive, `URI#getPort` accepts values over
  65535, and Apache documents zero timeout as infinite.
- Fix: Detect headers case-insensitively, reject out-of-range ports, require at
  least one effective millisecond, and add loader regressions for all three.

### [P2] Enforce the documented JSON media-type contract

- Location: `src/main/java/ai/loomspan/sidecar/rest/BoundedRestResponse.java:45`
- Scenario: The original subtype-only check accepted `image/json` as a JSON
  response even though the contract permits `application/json`, structured
  `+json`, and `text/*`.
- Impact: Unsupported response content could be reported as a successful skill
  result.
- Evidence: A loopback `image/json` response succeeded under the prior predicate.
- Fix: Require the `application` top-level type for bare `json`, retain
  structured `+json` and `text/*`, sanitize invalid charset failures, and add a
  regression assertion.

### [P2] Complete identity and restart-only executable evidence

- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:230`
- Scenario: The callback test asserted the token and a role but not the restored
  issuer/subject, while the restart test changed only the route file and did not
  prove mounted skill metadata also remained immutable until restart.
- Impact: Two primary acceptance-criterion claims could regress without the
  planned tests detecting it.
- Evidence: The ticket requires original issuer/subject/roles at the callback
  and startup-only activation for both skill and route edits.
- Fix: Require and retain callback subject/issuer and assert both, then mutate
  the mounted skill description and route target together and prove old/new
  snapshots on either side of restart.

### [P3] Remove the unused handler mapper dependency

- Location: `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:31`
- Scenario: The handler stored an injected `ObjectMapper` that was never read.
- Impact: The dead dependency obscured actual request serialization ownership
  and added needless construction/test coupling.
- Evidence: All POST bodies are written by the configured `RestClient` converter.
- Fix: Remove the field, constructor parameter, imports, and test arguments.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One production handler; completed catalog correspondence; no startup probe | `GenericRestSkillHandler`, `RestRouteCatalogValidator` | `RestRouteStartupIntegrationTest` | implemented |
| Required strict route file, overrides, empty maps, placeholders, target sharing | `RestRoutesProperties`, `RestRouteLoader`, immutable `RestRouteConfiguration` | `RestRouteLoaderTest`, empty-route fixture, application contexts | implemented |
| GET/POST binding and JSON-only values before I/O | `GenericRestSkillHandler` validation and binder | `GenericRestSkillHandlerTest` request capture and zero-call failures | implemented |
| Base-path preservation and encoded traversal/authority confinement | Prevalidated route/base segments and encoded invocation components | Reserved/Unicode path capture and traversal regressions | implemented |
| None/static/passthrough auth and fixed Accept | Target auth model and handler header construction | Header capture, missing-JWT, production callback tests | implemented |
| Per-target timeout/TLS, no redirect/retry | `RestTargetClients` isolated Apache clients | timeout/redirect counters and `RestTransportTlsIntegrationTest` | implemented |
| Streaming cap, bodyless success, media/charset contract | `AbortableBoundedInputStream`, `BoundedRestResponse` | exact/excess, empty, JSON/text/unsupported-media tests | implemented |
| Bounded body-free failures projected as `SKILL_FAILURE` | `BoundedRestResponse` messages and existing classifier | direct failures and async polling assertions | implemented |
| JWT queue/planner/callback identity and expiry | Existing security-context propagation plus caller passthrough | `AuthenticatedExecutionApiIntegrationTest` | implemented |
| Restart snapshots and framework-owned shutdown budget | Immutable configuration, phase-zero client lifecycle | `RestRouteRestartIntegrationTest`, `RestHandlerLifecycleIntegrationTest` | implemented |
| Public API boundary, wrapper verification, and accurate docs | Public `ai.loomspan.api` imports, README, standard namespaces | ArchUnit rule, full Maven verification, diff check | implemented |

## Active Project Guardrails

- Simplicity and technical debt: the implementation keeps one loader, one
  immutable configuration, per-target clients, one handler, and one late
  validator; the unused mapper dependency was removed.
- Framework execution authority: registration, validation, authorization,
  nesting, and execution lifetime remain framework-owned through the supported
  public catalog/template/SPI surface.
- One admission owner and retained record: execution admission, storage, and
  diagnostics code were not duplicated or replaced.
- Trusted identity outside model inputs: passthrough reads only the current
  `JwtAuthenticationToken`; invocation input cannot set headers.
- Startup activation and one shutdown budget: routes and skills remain startup
  snapshots; the phase-zero client owner closes only after the framework's
  higher-phase completion/cutoff and adds no wait budget.
- Diagnostic contract: successful text is returned unchanged; deliberate HTTP
  messages are bounded/body-free and existing observer selection is untouched.
- Architecture: production and test imports remain outside
  `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`; no Java
  `@SkillMethod` was added.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=RestRouteLoaderTest,RestRouteStartupIntegrationTest,GenericRestSkillHandlerTest,RestTransportTlsIntegrationTest' test` — 10 tests passed after the production fixes.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=AuthenticatedExecutionApiIntegrationTest,CustomRoleExecutionIntegrationTest,RestRouteRestartIntegrationTest,RestHandlerLifecycleIntegrationTest,ExecutionShutdownIntegrationTest,MountedSkillRegistrationIntegrationTest,LoomspanSidecarApplicationTest,ManagementEndpointIntegrationTest,ConsoleSecurityIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — 21 related application, lifecycle, security, and architecture tests passed.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=AuthenticatedExecutionApiIntegrationTest,RestRouteRestartIntegrationTest' test` — 8 tests passed after completing the identity and restart assertions.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress verify` — final full wrapper build and all 55 tests passed.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- No blocking residual risk. As an optional operational observation, a developer
  may run the documented configuration against a developer-controlled
  non-production HTTPS/mTLS callback; deterministic local mTLS coverage already
  satisfies this review.
- A non-production black-holed address can optionally supplement the deterministic
  configured connection-failure/read-timeout evidence on hosts where a distinct
  connect-timeout observation is useful.

## Disposition

- `fixes-applied`
