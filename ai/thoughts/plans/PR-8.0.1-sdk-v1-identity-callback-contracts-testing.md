# SDK v1 identity and callback contracts Testing Plan

## Change Summary

This ticket delivers reference documentation and planned acceptance evidence only.
No implementation, Java test changes or provider/SDK integration execution belongs
to this step. Apply the [implementation plan](PR-8.0.1-sdk-v1-identity-callback-contracts.md)
and [design lens](../design-lens.md); replace obsolete development contracts directly
without shims. No reset or deployed-data deletion is required or authorized.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Strategy/provider | Unusable Exchange configuration, ID-token relay or hidden fallback | Concrete registration/Java-property equivalence review; STRATEGY/EXCHANGE scenarios |
| Token trust | Signed wrong-purpose/bound token accepted, issuer-selected remote key fetch | Separate admission/callback tables; TOKEN and ISSUER scenarios |
| Identity | Native-account linking, cross-issuer role escalation or ownership merge | IDENTITY scenarios and typed interface review |
| Callback chain | Browser context loaded/merged/mutated; URL authorization bypass or broad CSRF exception | CALLBACK scenarios for both arrangements and explicit filter/repository ordering |
| Async/lifecycle | Stale privilege promises, captured-token replacement, cross-user context leakage | FRESHNESS and CALLBACK async/concurrent/error scenarios |
| Passthrough | False anti-replay claim, unapproved recipient/redirect or infrastructure leakage | PASSTHROUGH scenarios with controlled reuse and fixture canaries |
| Evidence | Future API/settings confused with current support; lost roadmap obligation | Existing-versus-planned labels, full clause-to-scenario mapping, independent review |

## Existing Coverage and Environment Constraints

Verified test locations under `src/test/java/ai/loomspan/sidecar/`:
`security/JwtSecurityConfigurationTest.java`, `security/JwtStartupIntegrationTest.java`,
`execution/AuthenticatedExecutionApiIntegrationTest.java`,
`execution/CustomRoleExecutionIntegrationTest.java`, `rest/GenericRestSkillHandlerTest.java`,
`rest/RestTransportTlsIntegrationTest.java` and
`architecture/SupportedLoomspanApiArchitectureTest.java`.
These JUnit/AssertJ tests use signed local keys, local HTTP/TLS fixtures and Spring;
ArchUnit protects the public API boundary. They establish no SDK callback composition,
multi-issuer support, custom resolver, sender-constraint or provider matrix by themselves.
The root Maven wrapper and CI use `verify`; CI provisions Playwright Chromium first.
No Maven suite is needed to validate this documentation-only diff.

Focused SDK tests will belong to the independent `sdks/java/` build, Sidecar changes
to root tests and complex SDK/Sidecar/application/Keycloak/Caddy evidence to
`C:/opendev/code/loomspan-sidecar-test-suite`. Those future paths/commands must be
settled by their implementation units and PR-8.0.3; do not invent an existing test
runner or assert these scenarios have run. Local reference provider is Keycloak
26.4.0; image acquisition, Java/build dependencies, Docker and real TLS are future
integration prerequisites. No hosted identity provider, paid model or cloud account
is required for local release evidence. Use deterministic synthetic business fixtures.

## Failing Test First
- Name: `referenceContractAcceptanceCoverage` (documentation acceptance review).
- Type: document inspection, not a Java/runtime test.
- Location: new `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-reference.md` and `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md`.
- Arrange/Act/Assert: enumerate AC1–AC6 and the linked roadmap's detailed security clauses; require concrete decisions, interfaces/settings, named scenario rows and implementation owners for each.
- Expected pre-fix failure: both concrete reference artifacts are absent. No product red behavioral test applies because product behavior is unchanged.

## Tests to Add or Update

No production/test code is added. Checks 1–3 validate this ticket; checks 4–9 are
specifications of future evidence to record in the scenario matrix, all PLANNED.

### 1. `referenceLinksAndTraceability`
- Type: automated documentation structure and local-target checks plus semantic review.
- Location: both new reference documents; touched roadmap/Phase 0/lens/ticket.
- Proves: link targets exist; unique scenario IDs; required strategy/provider/validation/identity/callback/lifetime/owner sections; AC1–AC6 reach named planned evidence.
- Inputs/fixture: Markdown itself, no credentials.
- Doubles or boundary isolation: local filesystem only. Ignore external URLs and validate external fragment rules through source review rather than network-crawling every link.
- Edge cases: new document links, anchor-only links, parent-directory links and code-like pseudo-paths; do not mistake future code locations for files that should already exist.

### 2. `securityContractConsistency`
- Type: required independent security-focused document review.
- Location: normative reference and scenario matrix.
- Proves: no opposing authority defaults, unsupported compatibility promise, purpose ambiguity, trust expansion from mTLS membership, or unresolved material decision under signed-off text; every source-backed profile matches cited primary evidence.
- Inputs/fixture: research, ticket, normative roadmap and matching public Framework beta.8 APIs.
- Doubles or boundary isolation: no runtime service execution.
- Edge cases: overlapping ID-token audiences; null/unknown/malformed `cnf`; cookie Alice/Bob authority collisions; native resolver failure and aliases; async error/cancellation; exact token capture after renewal.

### 3. `documentationOnlyDiff`
- Type: automated diff/status/whitespace inspection.
- Location: repository working tree.
- Proves: product/build/version/test trees unchanged; original .1/.2/.3 heading edits preserved; no accidental compatibility scaffolding or reset.
- Inputs/fixture: `git diff`, `git status`, parent-supplied initial dirty state.
- Doubles or boundary isolation: no live side effects.
- Edge cases: new untracked Markdown must be checked separately because `git diff --check` does not cover it.

### 4. `strategyAndExchangeContract` — PLANNED
- Type: future SDK unit tests plus real Keycloak integration.
- Location: `sdks/java/` and external test-suite SDK v1 scenarios, exact files assigned by future tickets.
- Proves: four explicit strategies; shared issuance; access versus ID source selection; trusted custom sending/receiving adapters; equivalent Java/properties; errors never change strategy/login/identity.
- Inputs/fixture: same-realm Keycloak 26.4.0 registered confidential requester, subject/result access JWTs, bounded roles/audiences and protected secret.
- Doubles or boundary isolation: local protocol doubles for malformed/error/timeout results; real local server for provider acceptance, no mocked-success interoperability claim.
- Edge cases: unsupported server/flow/input/auth, missing requester audience, unavailable audience/scope, rejected exchange, wrong token type/purpose, opaque/bound output, unchanged subject identity and no automatic retry/fallback.

### 5. `independentTokenValidation` — PLANNED
- Type: future SDK and Sidecar validator unit/integration tests; real transport negatives.
- Location: `sdks/java/`, root `security/` tests and external test suite.
- Proves: independent acceptance at Sidecar and callbacks, not SDK preflight alone.
- Inputs/fixture: valid profiles; signed ID tokens with identical accepted audiences; wrong purpose/signature/algorithm/issuer/audience; missing/malformed claims; expiry/not-before/future-issued-time boundaries; `cnf` null/object/string/array/unknown/jkt/x5t forms; DPoP scheme.
- Doubles or boundary isolation: synthetic keys/clock for components; real HTTP/HTTPS/mTLS and both mTLS paths in applicable end-to-end evidence.
- Edge cases: valid workload cert never overrides JWT rejection; no token binding stripped; unknown issuer never causes unapproved discovery.

### 6. `issuerAwareIdentityAndFreshness` — PLANNED
- Type: future SDK mapping/resolver tests, Sidecar ownership tests and lifecycle integration.
- Location: `sdks/java/`, root `execution/` tests and external test suite.
- Proves: default verified pair/multiple mapped roles, native principal plus original pair, explicit issuer mappings, post-validation resolver only, fail-closed lookup, no linking/creation/fallback/authority union.
- Inputs/fixture: equal subjects across two approved issuers; same email/name; multiple aliases deliberately mapped to one account; unmapped/renamed roles; changed role with same pair; renewed versus changed-pair tokens; disabled/deleted accounts; resolver outage; logout; queued expiry.
- Doubles or boundary isolation: trusted deterministic resolver and synthetic clock; real cross-service ownership and queue/callback behavior.
- Edge cases: snapshot rights remain bounded by expiry; live policy has declared precedence; logout does not falsely promise JWT revocation; renewed polling never refreshes admitted JWT.

### 7. `callbackCompositionIsolation` — PLANNED
- Type: real Spring application integration for EACH selected arrangement.
- Location: SDK focused security tests plus external suite with existing browser login/session/CSRF/role routes.
- Proves: matcher/order/URL hooks, endpoint/method/record checks, narrow CSRF, 401/403 without redirects, no session load/merge/create/update/mutation and no cross-user context leaks.
- Inputs/fixture: bearer Alice plus session Bob; missing/invalid bearer with Bob; cookie-only/bearer-only; missing authority; forbidden record; neighboring browser POST without CSRF; existing login/session; distinct JWKS route.
- Doubles or boundary isolation: instrument session repository/access/creation counters and saved context; no token in business data.
- Edge cases: overlap/shadow/wrong order diagnostics, unsupported opaque chain configuration fails closed, sequential/concurrent success/rejection/exception, supported MVC async completion/error/timeout/cancel/executor cleanup; unsupported reactive or arbitrary tasks make no guarantee.

### 8. `passthroughTrustAndReuse` — PLANNED
- Type: real Sidecar/callback/proxy integration with synthetic approved recipients.
- Location: root REST-focused tests and external integration suite.
- Proves: byte-identical captured bearer, approved destinations, limited audience/roles, independently authorized callback records, redirect containment, explicit expired callback failure and controlled reuse residual limit.
- Inputs/fixture: two controlled approved recipients, unapproved redirect sink, valid/expired tokens, nonmember and non-Sidecar group certs, separate infrastructure-credential canaries.
- Doubles or boundary isolation: local synthetic operations only; capture app/proxy logs, diagnostics/model-bound inputs and outbound sink requests.
- Edge cases: approved member can reuse credential when user/audience/admission permit; Sidecar-only callback policy rejects other members; renewal does not replace old captured JWT; infrastructure canaries absent without demanding sanitization of application-supplied secrets.

### 9. `transportAndOwnerExpansion` — PLANNED
- Type: acceptance-matrix reconciliation for later Phase 0 consolidation.
- Location: named scenario matrix and PR-8.0.3 final matrix.
- Proves: all four strategies over protected HTTP/HTTP, HTTPS/HTTPS and both invocation mTLS paths with mTLS callbacks; isolated/group evidence; relevant mixed transport and outbound-only/resumed polling; transport guarantees reported separately.
- Inputs/fixture: named strategy/profile/chain arrangement/transport/client-path/phase/owner expansion, including applicable negatives from checks 4–8.
- Doubles or boundary isolation: component proofs can be shared with rationale; never infer a real composition from mocks or one mTLS path.
- Edge cases: HTTPS callback does not authenticate Sidecar workload; no-callback means no callback listener/TLS setup, JWKS route remains independent; PR-8.0.2 lifecycle/trust duties and PR-8.0.3 baseline duties remain explicit.

## Safe Verification Commands
- Focused: `git diff --check` and a local Python Markdown link/scenario checker run inline, with exact executed invocation recorded in the implementation report. Do not add permanent checker tooling solely for this document change.
- Related suite: `git diff --name-only`; `git status --short`; `git diff -- ai/thoughts/tickets/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md ai/thoughts/tickets/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md` — verify change scope and preserved initial edits.
- Full safe suite: document consistency/acceptance review across new reference/matrix and all touched normative files; no Maven/Docker suite planned because product/test/configuration/build artifacts are unchanged. If scope changes to Java/product, reassess and run the affected focused tests then `.\mvnw.cmd -B -ntp verify` with required local prerequisites.

The inline link checker must load an explicit file list, find Markdown link/image
targets outside fenced code, ignore URL/mailto and anchor-only targets, resolve
relative filesystem paths without the fragment, and fail if a local target does
not exist. Check unique matrix scenario IDs and each family's presence; manual
review verifies semantic completeness, because a heading or keyword cannot prove
a security rule. Inspect untracked files for trailing whitespace explicitly.

## Optional Developer Checks
- None for ticket completion. Later running the named provider/SDK/proxy scenarios is required future implementation evidence and must remain labelled PLANNED here.

## Exit Criteria
- [x] Pre-implementation absence of the concrete documents recorded; no invented red runtime result.
- [x] Both documents exist, links resolve and scenario IDs are unique; whitespace and diff-scope checks pass.
- [x] AC1–AC6 and every relevant detailed roadmap security acceptance obligation have a concrete contract and named planned scenario/phase/repository owner.
- [x] Both callback arrangements satisfy every cookie/CSRF/authorization/context requirement in their supported boundaries; unsupported combinations are explicit.
- [x] Primary source assertions, current Sidecar evidence and planned future support are distinguished.
- [ ] Independent review resolves actionable issues and unresolved material choices block affected sign-off.
- [x] No production/test/build/version changes, commits, pushes, live services or deletion; original developer heading edits remain intact.
- [x] No unrun integration test is represented as passing; full Phase 0 and PR-8.0.2/.3 dependencies remain outstanding.
