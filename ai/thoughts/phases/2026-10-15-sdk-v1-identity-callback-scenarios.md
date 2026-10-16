# SDK v1 identity and callback planned acceptance scenarios

**Development policy:** Destructively replace superseded development contracts;
no shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No data reset or
deployed-data deletion is required/authorized by this document.

Normative contract: [identity and callback reference](2026-10-15-sdk-v1-identity-callback-reference.md).
Every scenario below is **PLANNED, NOT RUN**. This is a scoped Phase 0 matrix,
not evidence of working SDK/provider/Sidecar integration or complete Phase 0.
The [consolidated matrix](2026-10-15-sdk-v1-acceptance-matrix.md) individually maps
every ID here with PR-8.0.2's scenarios to final phase/repository/planned owning
unit/evidence locations. It links selected runtime/deployment versions and exact
future commands; no implementation tickets or completed runtime evidence are invented.

## Owners and evidence rules

- **SDK**: focused tests in this repository's independent `sdks/java/` build.
- **Sidecar**: root `src/test/java/ai/loomspan/sidecar/` security, execution, REST
  and architecture tests using only supported public Framework API.
- **System**: complex real SDK/application/Sidecar/Keycloak/Caddy integration in
  `C:/opendev/code/loomspan-sidecar-test-suite`, named SDK v1 to avoid its historical
  Phase 1/2/3 labels. Local synthetic business fixtures; no paid models/hosted services.
- **Trust**: PR-8.0.2 defines protected trust/signing/JWKS/peer-lifecycle mechanisms;
  **Deployment**: PR-8.0.3 defines runtime/build/deployment/evidence consolidation.

P1 = isolated/core implementation; P2 = group/issuer expansion; P3 = lifecycle/fault
evidence. A row naming multiple owners requires component and composed evidence;
one cannot substitute for the other. Exact future files/commands are selected by
the consolidated matrix and deployment reference; implementation tickets must create
and verify them. They are not existing test runners.
Evidence records SDK/Sidecar/Framework versions or local revisions, provider/proxy
version, configuration, command, actual result and each hop's guarantee. All rows
start planned, including rows related to currently inspected tests.

## Strategy and Exchange scenarios

| ID / name | Concrete input and expected outcome | Owner / phase |
| --- | --- | --- |
| STRATEGY-01 explicitStrategySelection | Initialize each of relay/exchange/spring-issued/custom through Java and properties with equivalent objects; same identity/profile/audiences. Missing/duplicate strategy, unknown fields or conflicting Java/property definitions fail startup. | SDK P1; System P1 |
| STRATEGY-02 relayAccessOnly | Existing OAuth2 login has access and ID tokens; SDK selects OAuth2AccessToken and never ID token. Authenticated bearer source also succeeds with compatible access JWT. Session without access token fails, no request/issuance. | SDK P1; System P1 |
| STRATEGY-03 sharedIssuance | Spring stable subject plus approved authorities and trusted custom source for the same subject produce the same application-execution-v1 claims/profile through the same issuer; no password/session/security object in claims. | SDK P1; System P1 |
| STRATEGY-04 trustedSendingAdapter | Anonymous/missing authentication, untrusted body username, missing subject and throwing source/mapper fail before submission. Multiple allowed authorities are selected, arbitrary roles omitted. No signing code required from customer. | SDK P1 |
| STRATEGY-05 noFallback | Make each source/signer/exchange fail; assert zero alternative-strategy calls, no service user, no weaker transport/login change, no automatic retry. Distinct safe typed error, no token/secret logging. | SDK P1; System P1 |
| STRATEGY-06 receivingAdapterScope | Custom receiving adapter sees only validated pair/native principal/effective authorities; close runs on success/denial/business failure. Partial open failure unwinds; close failure still clears Spring state. Invalid JWT never opens scope. | SDK P1; System P1 |
| EXCHANGE-01 localProviderFlow | Keycloak 26.4.0 realm/client/scope/role recipe, Alice login access token with requester audience, authenticated one-shot V2 exchange; output preserves pair, azp requester, exact available workflow audiences, bounded roles/lifetime and Bearer purpose. Real execution and callback succeed. | SDK P1; System P1 |
| EXCHANGE-02 registrationAndInputFailures | Exchange disabled, public client, bad secret/client auth, missing requester audience, unavailable audience/scope, missing consent, foreign/ID/opaque/bound/expired subject each fails; no Sidecar submission/fallback. | SDK P1; System P1 |
| EXCHANGE-03 responseCompatibility | Protocol fixture returns HTTP success with opaque, ID, wrong issued_token_type/token_type, changed pair, wrong issuer/audience/azp, overlong/expired/bound JWT or malformed JSON; SDK rejects before attachment. Valid response field names alone do not suffice. | SDK P1; Sidecar P1; System P1 |
| EXCHANGE-04 endpointFailure | Timeout, OAuth denial and non-success/invalid response: one request, safe typed failure, secret and subject not logged. Remote wrong TLS identity rejected; protected local cleartext only within documented boundary. | SDK P1; System P1 |

## Independent token validation scenarios

Run each applicable TOKEN row independently at **Sidecar admission and callback
validator**, bypassing SDK preflight in negative server fixtures. Expand over
transports as specified below; a valid workload certificate never bypasses a JWT
negative. Fixture clocks/keys are synthetic; actual transport composition is real.

| ID / name | Concrete input and expected outcome | Owner / phase |
| --- | --- | --- |
| TOKEN-01 acceptedProfiles | Application-execution-v1 and keycloak-access-v1 valid signed tokens authenticate at both receivers with receiver-specific audience and mapped roles; all four strategy paths reach them as applicable. | SDK + Sidecar + System P1 |
| TOKEN-02 signatureAlgorithms | Tamper signature; use unknown key, HS256/RSA confusion, none, wrong alg, RSA below 2048 bits, missing/unmatched required kid, unsupported crit, JWE/noncompact JWT: 401 before execution/resolver. Approved single static key may omit kid only as specified. | SDK + Sidecar P1; System P1/P2 |
| TOKEN-03 requiredClaimShapes | Missing/blank/wrong-type iss/sub/aud/iat/exp; duplicate JSON claims; fractional dates; empty/duplicate audience entries; malformed selected roles; wrong jti/purpose type: 401, no coercion. | SDK + Sidecar P1 |
| TOKEN-04 timeBoundaries | iat/nbf at now+60 accepted, beyond rejected; now=exp+60 rejected; exp<=iat, nbf>exp and lifetime>300 rejected. SDK refuses new already-expired credential despite server skew. | SDK + Sidecar P1; System P3 |
| TOKEN-05 issuerAudienceSeparation | Unknown issuer, callback-only token at Sidecar, Sidecar-only token at callback, wildcard/prefix/superset unapproved audiences rejected. Unknown iss/kid/jku/x5u never triggers unapproved key fetch. | SDK + Sidecar P1; System P2 |
| TOKEN-06 overlappingIdAudiences | Legitimately signed Keycloak ID JWT with same accepted aud and roles still fails payload typ=ID; application login JWT with overlapping aud fails missing/wrong dedicated header and purpose claim. Generic typ=JWT and roles alone never suffice. | SDK + Sidecar + System P1/P2 |
| TOKEN-07 allConfirmationShapes | Present cnf=null, empty object, string, array, known jkt, x5t#S256 and unknown method all rejected as Bearer, including valid signature and valid mTLS peer. Never strip binding/reissue. | SDK + Sidecar P1; System P1/P2 |
| TOKEN-08 dpopAndAmbiguousCredentials | DPoP scheme, DPoP proof header with Bearer, duplicate Authorization, query/body-only token and absent bearer return 401; cookies or workload certificates cannot rescue. | SDK + Sidecar P1; System P1/P2 |
| TOKEN-09 authorizationAfterValidation | Valid JWT lacking catalog/skill/URL role returns 403, no unauthorized business effect; authorized role still subject to method/record checks. Invalid JWT remains 401 even if role text looks privileged. | SDK + Sidecar + System P1 |

## Issuer, identity and freshness scenarios

| ID / name | Concrete input and expected outcome | Owner / phase |
| --- | --- | --- |
| ISSUER-01 finiteApprovedTrust | Single approved issuer works before group extension. Two independent issuers work only after deliberately implemented finite Sidecar trust policy; unknown issuer rejected without discovery. | Sidecar + System P1/P2; Trust design |
| ISSUER-02 membershipIsNotIssuerAuthority | Valid group cert plus unapproved issuer or signature purporting to be another issuer fails; no certificate-to-JWT trust creation. Same role name from issuer B grants no A authority without explicit B mapping. | Sidecar + SDK + System P2 |
| ISSUER-03 independentCallbackTrust | Sidecar accepts issuer A but callback lacks A policy/key/profile or required audience: callback rejects independently. Adding member or Sidecar trust does not install receiver trust. | SDK + System P2; Trust design |
| IDENTITY-01 defaultAndMultipleRoles | No account DB: default CallbackPrincipal retains exact pair and multiple explicitly mapped reader/editor roles; unknown well-formed roles add nothing. Role change preserves pair/ownership. | SDK + Sidecar + System P1 |
| IDENTITY-02 nativeResolution | Resolver after valid token returns enabled native principal; wrapper retains verified pair and effective authorities. Application uses native principal for business checks and pair for owner identity. | SDK + System P1 |
| IDENTITY-03 resolverExclusionAndFailures | Each TOKEN failure never calls resolver. Missing/deleted/disabled account gives 403; outage gives 503; no fallback principal/account creation. Successful resolution cannot fix invalid token. | SDK + System P1/P3 |
| IDENTITY-04 noImplicitLinking | Same username/email or same sub from different iss stays distinct; resolver receives pair only as identity, no SDK lookup heuristic. Two explicit aliases may resolve to one native account but cannot read each other's Sidecar executions. | SDK + Sidecar P1; System P2 |
| IDENTITY-05 authorityPrecedence | Snapshot with resolver ignores resolver role additions; live requires resolver and intersects token/live canonical roles. No automatic union, unknown issuer role copying or local privilege escalation. | SDK + System P1/P3 |
| IDENTITY-06 renewedOwnership | Renew same pair with changed roles: ownership eligibility remains subject to current read checks. Change iss or sub, even resolving same native account: original execution remains inaccessible (nonowner 404). | Sidecar + SDK + System P1/P2/P3 |
| FRESHNESS-01 snapshotLogoutAndDisable | Without resolver, logout stops session-based acquisition but issued bearer remains valid until expiry/trust change; external disable not falsely detected. With snapshot resolver, disabled/deleted account rejects new callback but live role additions do not enlarge snapshot. | SDK + System P3 |
| FRESHNESS-02 livePermissionChange | Remove role/disable account before next callback: live lookup rejects; resolver outage fails closed. Already running callback is not retroactively cancelled or effects rolled back. | SDK + System P3 |
| FRESHNESS-03 queuedExpiryAndRenewal | Admit valid JWT, expire it while queued, then callback fails 401; later renewed polling can read same-pair result but captured callback header is original, no renewal/exchange/replay. Expiry does not imply queue cancellation. | Sidecar + SDK + System P3 |
| FRESHNESS-04 asyncCaptureExpiry | Capture identity/credential for supported async call, expire before use: fail typed credential error without fresh login/implicit renewal. Running wrapper always restores prior worker context on success/error/cancel. | SDK + System P1/P3 |

## Callback composition scenarios

**Every CALLBACK row runs for both `dedicated-chain` and `application-chain`.**
Use a real application with existing form/OAuth2 login, Bob's session, browser CSRF,
URL roles, method security and record ownership; session instrumentation counts
reads/creates/saves/invalidations/cookie writes. A final response body alone cannot
prove that Bob's session was never loaded.

| ID / name | Concrete input and expected outcome | Owner / phase |
| --- | --- | --- |
| CALLBACK-01 matcherAndUrlRules | Correct normalized callback path selects declared arrangement/chain and callback URL manager; GET reader/POST editor accepted, missing role/undeclared method denied. Ordinary browser matcher/rules preserved; chains never merge. | SDK + System P1 |
| CALLBACK-02 bearerAliceSessionBob | Alice Bearer plus Bob cookie authenticates Alice/pair/roles only; zero session read/write/create/invalidate or Set-Cookie, unchanged Bob session afterward. No union even when Bob has stronger rights. | SDK + System P1 |
| CALLBACK-03 cookieFallbackRejected | Missing, malformed, expired and wrong-purpose Bearer with valid Bob cookie, plus cookie-only, all return 401/Bearer challenge without Location/login redirect/request cache save; no session access or mutation. | SDK + System P1 |
| CALLBACK-04 bearerOnly | Valid Alice Bearer without cookies works under URL/method/record policy, creates no session and no authentication cookie. | SDK + System P1 |
| CALLBACK-05 methodAndRecordChecks | Valid URL role but method denial or Bob-owned record returns 403/no effect; reader cannot POST editor action. No SDK permitAll/authenticated-only rule bypasses stricter customer checks. | SDK + System P1 |
| CALLBACK-06 browserAndCsrfPreserved | Normal login/session and permitted browser routes work; browser POST without CSRF rejected including neighboring namespace. Callback bearer POST works without CSRF token only on reserved route; unsupported callback method never falls into browser chain. | SDK + System P1 |
| CALLBACK-07 overlapDiagnostics | Competing arrangements, overlapping namespaces, earlier shadowing chain, duplicate/wrong order, unknown chain, opaque matcher or proxy-prefix mismatch fail clear safe startup validation. Malformed/ambiguous encoded paths rejected. | SDK + System P1 |
| CALLBACK-08 unsupportedComposition | Mixed chain has unguarded custom session filter/repository, global disable CSRF, persistence override or competing bearer filter: startup rejects. Supported browser filters never load sessions for callbacks; no replace-after-load shortcut. | SDK + System P1 |
| CALLBACK-09 sequentialConcurrentCleanup | Alice/Bob callbacks sequentially/concurrently, token rejection and ordinary browser request on reused worker threads: each sees only own context, no native/custom scope or Spring authentication leakage after completion. | SDK + System P1 |
| CALLBACK-10 exceptionCleanup | Resolver, authorization hook, business handler and receiving open/close exceptions: correct safe failure, no session mutation, no retained callback identity; worker's nested capture scope restored. | SDK + System P1 |
| CALLBACK-11 supportedMvcAsync | Callable and DeferredResult on SDK executor/capture wrapper, success/error/timeout/cancel/redispatch: same verified pair/permissions, separate scopes, no session load and eventual finally cleanup even for running cancelled task. No duplicate effect on error redispatch. | SDK + System P1/P3 |
| CALLBACK-12 unsupportedAsync | Raw executor/common pool, detached task, reactive/WebFlux integration or adapter unable to open independent scopes: no implicit context guarantee, missing outbound scope fails; unsupported callback mode/configuration rejected. | SDK + System P1; Deployment reconciliation |
| CALLBACK-13 jwksSeparation | Explicit public JWKS route outside callback namespace permits keys only; unauthenticated callback remains rejected; overlapping JWKS path fails startup; disabling callbacks creates no callback chain/listener. | SDK + System P1; Trust design |

## Passthrough scenarios

| ID / name | Concrete input and expected outcome | Owner / phase |
| --- | --- | --- |
| PASSTHROUGH-01 exactCapturedToken | Admit JWT A, renew client credential B, invoke nested/queued callback: header remains byte-identical A, never B, no issuer/refresh/exchange calls in Sidecar. | Sidecar + SDK + System P1/P3 |
| PASSTHROUGH-02 limitedAudiencePermissions | Narrow Sidecar plus necessary callback audiences/roles accepted; missing own audience/extra unapproved audience/role shortage rejected. General-purpose Relay token never causes weakened validation. | SDK + Sidecar + System P1/P2 |
| PASSTHROUGH-03 controlledApprovedReuse | Approved recipient deliberately reuses valid captured credential for a synthetic authorized operation: succeeds when user/audience/network/workload policy allows. Nonmember/wrong-audience/forbidden-record attempt fails. Demonstrates residual reuse rather than a single-use claim. | Sidecar + SDK + System P1/P2 |
| PASSTHROUGH-04 sidecarSpecificPeer | Group callback ingress with Sidecar-only peer policy accepts Sidecar, rejects another valid group member holding same JWT. General group policy permits member reuse as documented. HTTPS-only callback is never reported as Sidecar workload authentication. | System P2; Trust design |
| PASSTHROUGH-05 destinationAndRedirect | Publish-approved fixed destination works; model/body cannot choose host/port. Unapproved target/egress rejects. Approved target redirect to sink is not followed and sink gets no credential. Zero automatic callback retry/cookie reuse. | Sidecar + System P1/P2 |
| PASSTHROUGH-06 infrastructureCanaries | Synthetic bearer/secret/key/context canaries absent from SDK/Sidecar/proxy logs, prompts, diagnostics and body where only infrastructure held them; deliberate business-data secret/text preserved per content contract. No sanitizer/blanket secret-absence assertion. | SDK + Sidecar + System P1/P2 |
| PASSTHROUGH-07 capturedExpiry | Original token expires before callback; 401 propagates as explicit callback failure without mint/refresh/exchange or automatic retry. Same-pair renewed polling remains independently authorized. | Sidecar + SDK + System P3 |

## Transport and roadmap expansion

| ID / name | Required expansion and evidence | Owner / phase |
| --- | --- | --- |
| TRANSPORT-01 primaryFamilies | For EACH of four strategies: direct protected HTTP/HTTP, HTTPS/HTTPS with actual ingress where selected, and mTLS/mTLS through BOTH client-Caddy and SDK-native invocation paths. mTLS includes isolated P1 and group P2. Run real success/callback/ownership and relevant TOKEN/authorization negatives, no downgrade. Record boundary/server/workload/user checks separately for each hop. | System P1/P2; Deployment exact recipes |
| TRANSPORT-02 focusedMixed | Independently selected invocation/callback settings: mTLS invocation with protected local HTTP callback and representative HTTPS mixed callback. Prove each actual hop/boundary; ordinary HTTPS callback establishes no Sidecar peer identity. Include wrong JWT/destination negatives. | System P1/P2; Deployment |
| TRANSPORT-03 outboundOnlyResumedPolling | All four strategies and both mTLS invocation paths, no callback listener or callback TLS material; submit, retain ID, later resume status/result polling. Client-Caddy retains local invocation proxy; native path uses no client Caddy. JWKS listener opt-in independent; uploaded/separately hosted keys permit no app listener. | SDK + System P1/P3; Deployment |
| TRANSPORT-04 trustAndLifecycleDependencies | Reconcile each applicable primary/focused case with PR-8.0.2 key retrieval/rotation/outage/emergency distrust, TLS renewal/member removal and queued work rules. No claim that removing public JWKS key instantaneously revokes all validators or cancels work. | Trust + System P3; Deployment consolidation |
| TRANSPORT-05 publicFrameworkBoundary | Root ArchUnit forbids internal/autoconfigure imports and Java SkillMethod; real Sidecar handoff/authorization/shutdown evidence uses public beta.8 APIs. Framework tests alone never establish Sidecar or SDK integration. | Sidecar P1/P3 |

Shared component evidence (e.g. all malformed cnf shapes) may be referenced by
transport cases with rationale, but each primary composition needs real SDK, Sidecar
and receiving application authentication/authorization evidence. At least a signed
wrong-purpose token and a bound token must fail over each actual transport/mTLS
client path; a successful certificate is never sufficient. Do not manufacture a
full security-case cross-product when it adds no proof; retain every required
composition and its applicable authorization/lifecycle negatives.

## Acceptance and detailed obligation traceability

| Normative obligation / ticket AC | Named planned scenario coverage | Design/evidence owner |
| --- | --- | --- |
| Four selected strategies, shared issuance, equivalent Java/Spring, trusted adapters, no customer JWT/filter work; AC1 | STRATEGY-01 through STRATEGY-06 | SDK/System P1 |
| Named locally runnable exchange, registration/input/client auth/output/failures/no fallback; AC1 | EXCHANGE-01 through EXCHANGE-04 | SDK/System P1 |
| Independent signature/algorithms/time/required claims, purpose and overlap-ID substitution; AC2 | TOKEN-01 through TOKEN-06 | SDK/Sidecar P1; System P1/P2 |
| All sender constraints, cnf shapes/DPoP/no stripping/fallback despite mTLS; AC2 | TOKEN-07, TOKEN-08, STRATEGY-05, TRANSPORT-01 | SDK/Sidecar/System P1/P2 |
| Issuer authority distinct from membership, explicit group model/single-issuer gap; AC3/AC6 | ISSUER-01 through ISSUER-03, IDENTITY-04 | Sidecar/SDK/System P2; Trust |
| Default/native pair, multiple roles, post-validation resolution/failure/no linking or ownership merging; AC3 | IDENTITY-01 through IDENTITY-06 | SDK/Sidecar/System P1/P2 |
| Token/live precedence, role changes, disabled/logout, renewal, queued/callback expiry; AC3 | IDENTITY-05, IDENTITY-06, FRESHNESS-01 through FRESHNESS-04 | SDK/Sidecar/System P3 |
| Both chain arrangements/match/order/hooks/diagnostics/URL/method/record rules; AC4 | CALLBACK-01, CALLBACK-05, CALLBACK-07, CALLBACK-08 | SDK/System P1 |
| Alice Bearer/Bob session, no fallback/merge/save/mutation/redirect; AC4 | CALLBACK-02 through CALLBACK-04, CALLBACK-10 | SDK/System P1 |
| Browser security, scoped CSRF, JWKS isolation; AC4 | CALLBACK-06, CALLBACK-13 | SDK/System P1; Trust |
| Sequential/concurrent/error/async cleanup and explicit unsupported scope; AC4 | CALLBACK-09 through CALLBACK-12, FRESHNESS-04 | SDK/System P1/P3; Deployment baseline |
| Reusable unchanged token, restricted recipients/audiences/rights, Sidecar peer policy, redirect/leakage; AC5 | PASSTHROUGH-01 through PASSTHROUGH-07 | SDK/Sidecar/System P1/P2/P3 |
| No business-content sanitization, infrastructure credential separation and diagnostic fidelity; AC5/AC6 | PASSTHROUGH-06 | SDK/Sidecar/System P1/P2 |
| Every strategy/primary/mixed/no-callback/both-mTLS-path evidence, public Framework boundary; AC6 | TRANSPORT-01 through TRANSPORT-05 | Sidecar/System P1/P2/P3; Deployment |

Roadmap families outside this ticket's security scope retain their full obligations:
trust administration/import/rollback/JWKS/export/bootstrap/rotation belong to
PR-8.0.2; submission uncertainty, complete SDK functionality/resource ownership,
cloud/manual recipes, build/sample compatibility and final matrix belong to PR-8.0.3.
These are named dependency owners, not waived tests or claims those tickets are done.
No dependent implementation ticket should be created until its material Phase 0
contracts settle. Document verification here proves only links/scope/traceability;
all integration and implementation evidence above remains outstanding.
