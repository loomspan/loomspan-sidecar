# SDK v1 identity and callback reference contract

**Development policy:** Destructively replace superseded development contracts;
no compatibility shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No development-data
reset is required by this documentation change; no deployed-data deletion is authorized.

This is the normative design output of [PR-8.0.1](../tickets/PR-8.0.1-sdk-v1-identity-callback-contracts.md).
Every SDK API and `loomspan-sdk.*` setting below is **PLANNED**, not installed or
runtime-verified. It refines the [roadmap](2026-10-11-java-spring-sdk-roadmap.md)
without reducing its acceptance obligations. The companion
[scenario matrix](2026-10-15-sdk-v1-identity-callback-scenarios.md) records future evidence.

## Credential strategies and configuration

Select exactly one strategy at initialization. Missing, ambiguous or contradictory
configuration is a startup error. Runtime credential failure stops that operation;
there is no strategy switch, replacement login, service-account substitution,
automatic exchange retry or implicit token refresh. Customer login remains external.
An explicitly renewed customer credential may be used by a later operation.

The SDK retrieves credentials, constructs/signs execution tokens where selected,
attaches one Authorization header and supplies callback validation. Customers supply
trusted identity/permission integration and normal business authorization, not JWT
construction, signing, attachment or authentication filters. No session, password,
arbitrary security object or raw incoming business field is serialized into a token.

### Concrete SDK contracts

The following signatures define inputs and outputs; imports and package spelling are
left to SDK implementation. They are SDK extension points, **not Framework SPIs**.
Collections are immutable defensive copies; identities are nonblank exact strings.

```java
record VerifiedIdentity(String issuer, String subject) {}
record VerifiedCaller(VerifiedIdentity identity, Set<String> mappedAuthorities) {}
record TrustedIdentity(String subject, Set<String> authorities) {}
record ResolvedAccount(Object principal, boolean enabled, Set<String> authorities) {}
record CallbackPrincipal(VerifiedIdentity identity, Object nativePrincipal) {}
enum AuthorityPolicy { TOKEN_SNAPSHOT, LIVE_ACCOUNT }

interface AccessTokenSource { AccessCredential currentAccessToken(); }
// AccessCredential: SDK-owned opaque handle; token text is never in toString/logs.
interface CustomIdentitySource { TrustedIdentity currentIdentity(); }
interface SpringIdentityMapper { TrustedIdentity map(Authentication authenticated); }
interface NativeAccountResolver { ResolvedAccount resolve(VerifiedCaller caller); }
interface ReceivingScope extends AutoCloseable { void close(); }
interface CustomReceivingAdapter {
    ReceivingScope open(VerifiedCaller caller, CallbackPrincipal principal,
                        Set<String> effectiveAuthorities);
}
interface ExecutionTokenIssuer {
    AccessCredential issue(TrustedIdentity identity); // SDK implementation only
}
interface CredentialStrategy { AccessCredential currentCredential(); }
interface CallbackAuthorization {
    boolean permits(HttpServletRequest request, Authentication bearerCaller);
}
interface CallbackSecurityConfigurer {
    void configure(HttpSecurity http, CallbackPolicy policy);
}
enum TokenProfile { APPLICATION_EXECUTION_V1, KEYCLOAK_ACCESS_V1 }
record RoleMapping(String claimPath, Map<String, Set<String>> approvedValues) {}
record IssuerPolicy(String issuer, String verificationSourceRef,
                    TokenProfile profile, Set<String> authorizedParties,
                    String receiverAudience, Set<String> workflowAudiences,
                    List<RoleMapping> roleMappings) {}
interface CallbackTokenValidator {
    VerifiedCaller validate(AccessCredential credential);
}
```

`currentIdentity()` reads established server authentication; null, anonymous,
unauthenticated, missing subject or adapter failure means `IdentityUnavailable` and
no HTTP submission. The Spring mapper similarly rejects unauthenticated/anonymous
Spring authentication. The built-in mapper uses `Authentication.getName()` only
when the application explicitly selects it as a stable subject, and copies only the
listed authorities. A custom mapper may derive a stable account ID from a trusted
principal. Neither mapper promotes request fields to authenticated identity.

`AccessTokenSource` has built-in Spring OAuth2 authorized-client and authenticated
bearer implementations. The former retrieves **OAuth2AccessToken**, never OIDC ID
token, for the selected existing registration and current authenticated user; the
latter reads the authenticated JWT bearer context. Neither uses arbitrary headers
without authentication. A session without an authorized access token fails.
The SDK checks profile compatibility before attachment; servers still validate
independently. Only the SDK transport can unwrap `AccessCredential` to a header.

The issuer is the same maintained Spring/Nimbus-backed implementation for both
issuance strategies. `IssuerSettings` contains exact issuer, audience set, lifetime,
approved input-authority-to-token-role map and a reference to one signing-state owner.
It issues the application-execution-v1 profile below, with UUID `jti`, current `iat`,
`exp=iat+lifetime`, fresh `kid` selection from active signing state, and selected roles.
No arbitrary claim passthrough. Signing lifecycle/provisioning follows the
[trust/bootstrap/lifecycle reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md);
a missing/unusable signer fails, with no ephemeral replacement key.

All credential failures are typed SDK errors (`IdentityUnavailable`,
`IncompatibleCredential`, `ExchangeRejected`, `ExchangeUnavailable`,
`SigningUnavailable`); safe messages contain a reason code, never token/secret/body.
A failure before submission has no Sidecar admission; submission uncertainty after
sending belongs to PR-8.0.3. Network errors never trigger a different strategy.

### Equivalent property and Java initialization

All properties below bind to the exact Java constructors shown. Common invocation
`loomspan-sdk.sidecar.base-url` maps to `SdkClient(baseUrl, strategy, transport)`;
transport settings remain PR-8.0.3. `ref`/`*-bean` values name explicit application
objects, not secret values. A named bean must exist with the stated type; duplicate
property and Java strategy definitions fail startup rather than override each other.
Unknown strategy fields fail binding. This contract requires no particular DI container.
`profiles.require(name)` resolves an application-registered `IssuerPolicy` with the
fixed RS256/time/binding rules below; `Validators.callback(List<IssuerPolicy>)`
constructs `CallbackTokenValidator`. `validator-ref` names that validator object;
`identity-policy-ref` names the `IdentityPolicy` object below. These are explicit
bean/Java wiring, not hidden automatic trust discovery. `verificationSourceRef`
names an approved public-key/JWKS source whose lifecycle is defined by PR-8.0.2.
Authorized-party set is required/nonempty for Keycloak and empty for application
profile; the receiver audience must belong to workflow audiences. Claim paths are
literal approved paths (not expressions), approvedValues maps each exact role value
to one or more canonical authorities, and conflicting duplicate issuer entries or
empty/malformed policies fail initialization. SDK credential preflight supplies the
selected policy; callback construction takes a finite set with unique issuer keys.

```yaml
# Merge common settings with exactly ONE strategy fragment.
loomspan-sdk:
  sidecar:
    base-url: ${SIDECAR_URL}
  credentials:
    strategy: relay
    relay:
      source: spring-oauth2-client
      registration-id: existing-login
      profile-ref: approved-keycloak
```

Equivalent: `Strategies.relay(AccessTokens.springAuthorizedClient("existing-login"),
profiles.require("approved-keycloak"))`. Alternative `source: spring-bearer`
maps to `AccessTokens.springBearer()` and forbids `registration-id`.
`profile-ref` names a finite trusted issuer/profile policy, not unverified discovery.

```yaml
loomspan-sdk:
  credentials:
    strategy: exchange
    exchange:
      provider: keycloak-26.4.0-standard-v2
      token-endpoint: ${KEYCLOAK_TOKEN_ENDPOINT}
      client-id: sdk-requester
      client-secret-ref: exchangeSecret
      subject-source: spring-oauth2-client
      subject-registration-id: existing-login
      input-profile-ref: keycloak-subject
      output-profile-ref: approved-keycloak
      audiences: [sidecar-api, orders-callback]
      scopes: [loomspan-workflow]
```

Equivalent: `Strategies.exchange(new ExchangeSettings("keycloak-26.4.0-standard-v2",
tokenEndpoint, "sdk-requester", secrets.require("exchangeSecret"),
AccessTokens.springAuthorizedClient("existing-login"), profiles.require("keycloak-subject"),
profiles.require("approved-keycloak"), Set.of("sidecar-api", "orders-callback"),
Set.of("loomspan-workflow")))`. Secret reference resolves to a protected secret supplier;
no literal credentials in configuration. Subject-source also supports `spring-bearer`
with the same prohibition on registration ID. A custom authenticated access-token
source is Java wiring of `AccessTokenSource`, never an untrusted token parameter.

```yaml
loomspan-sdk:
  credentials:
    strategy: spring-issued
    issuance:
      issuer: ${APPLICATION_ISSUER}
      audiences: [sidecar-api, orders-callback]
      lifetime: 5m
      signing-state-ref: applicationSigner
      authority-map: { ROLE_ORDER_READER: order.read, ROLE_ORDER_EDITOR: order.edit }
    spring-identity:
      subject: authentication-name
      allowed-authorities: [ROLE_ORDER_READER, ROLE_ORDER_EDITOR]
```

Equivalent: `issuer = Issuers.executionV1(new IssuerSettings(applicationIssuer,
Set.of("sidecar-api", "orders-callback"), Duration.ofMinutes(5),
signers.require("applicationSigner"), Map.of("ROLE_ORDER_READER", "order.read",
"ROLE_ORDER_EDITOR", "order.edit"))); strategy = Strategies.springIssued(
SpringIdentities.authenticationName(Set.of("ROLE_ORDER_READER", "ROLE_ORDER_EDITOR")), issuer);`
For a custom Spring mapper use `spring-identity.mapper-bean` instead of `subject`
and `allowed-authorities`, equivalent to passing that `SpringIdentityMapper` object.

```yaml
loomspan-sdk:
  credentials:
    strategy: custom
    custom:
      source-bean: authenticatedIdentitySource
    issuance:
      issuer: ${APPLICATION_ISSUER}
      audiences: [sidecar-api, orders-callback]
      lifetime: 5m
      signing-state-ref: applicationSigner
      authority-map: { ORDER_READER: order.read, ORDER_EDITOR: order.edit }
```

Equivalent: build the same `IssuerSettings` with the custom input authority map,
then `Strategies.custom(authenticatedIdentitySource, issuer)`.
`authenticatedIdentitySource` implements `CustomIdentitySource`, e.g. returns the
trusted existing account's immutable ID and current selected permissions. It never
returns JWTs or signing code. Issuer construction validates that lifetime is positive
and at most five minutes, audiences are nonempty and mappings have nonblank values.

## Local Exchange profile

Selected provider: **Keycloak 26.4.0, standard V2, same realm**, image
`quay.io/keycloak/keycloak:26.4.0`. No legacy exchange or foreign-provider exchange.
The [tagged exchange guide](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/docs/guides/securing-apps/token-exchange.adoc)
supports confidential requesters, access-token subjects, repeated audience filtering
and scope-based audience availability. This is a selected test design, not a running
provider or a claim about other OAuth servers.

The local fixture registration recipe is deliberately narrow:

1. Create realm `loomspan-sdk-v1`; use its externally stable issuer URL consistently
   for SDK, Sidecar and callback trust. Set RS256 and access-token lifespan 300s.
2. Register `initial-login` for existing authorization-code login with exact redirect
   URIs and PKCE S256; no wildcard redirects or password grant. Fixture user Alice
   authenticates through that login; the SDK introduces no login server/flow.
3. Register confidential `sdk-requester`: client authentication on, client-secret
   method, Standard token exchange on, service accounts/direct grants off and no
   refresh tokens for exchange. Deliver its generated secret via `exchangeSecret`.
4. Register resource clients `sidecar-api` and `orders-callback`, with interactive
   grants/service accounts off. Define their client roles `execute` and `read`/`edit`
   respectively. Grant Alice only the fixture's required roles; disable Full Scope
   Allowed on requester and explicitly scope-map those roles.
5. Add an audience mapper to initial-login's dedicated scope with included client
   audience `sdk-requester`, access token on, ID token off. Create optional client
   scope `loomspan-workflow` on requester, scope-map the resource roles and attach
   the standard roles/audience-resolve mappers so available audiences derive from
   those roles. Remove unnecessary default scopes/mappers and unrelated role grants.
   Evaluate tokens to confirm exactly the requested resource audiences and roles;
   an unavailable audience is a fixture error, not reason to weaken validators.
6. Approve the realm JWKS source and exact issuer separately at admission and
   callbacks; configure expected `azp=sdk-requester` for exchanged outputs and the
   respective resource-role maps below. Initial access-token preflight instead
   requires `azp=initial-login` and `sdk-requester` audience.

Request: one form POST to the configured realm token endpoint with HTTP Basic
client-secret authentication; `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`,
`subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
`requested_token_type=urn:ietf:params:oauth:token-type:access_token`, subject credential,
`scope=loomspan-workflow` and repeated `audience=sidecar-api` and
`audience=orders-callback`. No actor, impersonation, resource, refresh-token or ID-token
request. Input is a validated ordinary same-realm bearer access JWT without `cnf`;
subject/output `(iss, sub)` must match. Bound, opaque, ID and foreign subjects fail
before exchange, even if some other provider could support them.

Require HTTP success plus `token_type=Bearer`, matching `issued_token_type`, a
compatible signed keycloak-access-v1 JWT, required audience set and permitted roles.
Check actual JWT expiry rather than trusting `expires_in`; reject extra audiences
outside the approved workflow set. Ignore no wrong-purpose result. Provider denial,
invalid client, missing requester audience, unavailable scope/audience, consent failure,
malformed response, timeout or incompatible output produce typed failure and no
Sidecar request. There is one attempt and no fallback. Requests and responses are
never logged with secrets. Remote endpoint use requires TLS with hostname validation;
cleartext is allowed only inside the enforced local test boundary, with no public
token endpoint or host-published cleartext credential traffic.

## Admission validation

These are required **future server rules**, not descriptions of current Sidecar.
Validate before Framework invocation, queue admission or owner creation. Profiles
are finite operator-approved entries; selecting an entry by untrusted `iss` is only
a lookup, never authority or permission to fetch a token-supplied URL. Pin algorithms
per entry; reject `jku`/`x5u` key-location headers rather than following them. JOSE `crit` extensions
and encrypted/noncompact/unsigned tokens are unsupported.

| Rule | Sidecar admission requirement and rejection |
| --- | --- |
| Credential | Exactly one Authorization Bearer credential; reject missing, ambiguous/duplicate headers, query/body tokens, DPoP scheme or DPoP proof header. No management/session fallback. |
| Signature | Trusted issuer public RSA key, RS256 only, RSA at least 2048 bits; matching approved `kid` for key sets, valid signature. Reject `none`, HMAC/key confusion, wrong key/algorithm, unknown key or unsupported JOSE header. Static single-key configuration may omit `kid`; an included `kid` must match. |
| Claims | `iss`/`sub` nonblank strings, exact approved issuer, `aud` nonempty string or array of distinct nonblank strings, integer NumericDate `iat`/`exp`, optional integer `nbf`; reject malformed/duplicate JSON members. No coercion of arrays/numbers into identity. |
| Time | Shared reference skew 60s. Accept only `iat <= now+60`, absent `nbf` or `nbf <= now+60`, `now < exp+60`, `iat < exp`, `exp-iat <= 300s`, and present `nbf <= exp`. Boundaries are explicit; SDK refuses already-expired tokens for a new outbound operation even during verifier skew. |
| Audience | Must contain this Sidecar's exact audience (`sidecar-api` in fixture); every audience must be in the issuer's approved workflow audience set. No wildcard/string-prefix match. Callback audience alone fails admission. |
| Purpose | Exactly one configured accepted profile below; trusted signature, roles or generic `typ=JWT` alone never establish purpose. No automatic profile guessing/fallback. |
| Sender constraint | Reject any top-level `cnf` member, including null, empty, string, array, `jkt`, `x5t#S256` and unknown methods. Reject all other configured profile proof requirements; never strip/convert bindings. Valid mTLS workload credentials do not override this. |
| Authority | Only explicitly mapped issuer claims/values become authorities; require actual catalog/skill permission through supported Framework authorization. Unknown well-formed roles grant nothing; malformed selected role claim rejects authentication. |

Invalid authentication returns 401 problem response and Bearer challenge without token
details/login redirect; valid identity lacking required authority returns 403. Existing
nonowner polling remains non-disclosing 404 after authentication and current read checks.

### Accepted profiles

| Profile | Distinct access purpose and claim rules |
| --- | --- |
| `application-execution-v1` | JOSE `typ=loomspan-execution+jwt`, payload `token_use=loomspan_execution`, `jti` nonblank unique issuance ID, `roles` array of strings (empty allowed). Both purpose markers required. Issuer signing state must reserve them for execution issuance, never login. `aud`, times and signature rules above apply. This is a Loomspan profile, no RFC 9068 conformance claim. |
| `keycloak-access-v1` | Keycloak 26.4.0 realm access JWT, JOSE `typ=JWT` and payload `typ=Bearer`, nonblank `jti`, approved string `azp`, issuer-specific `resource_access.<resource>.roles` arrays. Payload `typ=ID` fails even with an accepted audience; generic JOSE type is insufficient. Reject other provider versions/profiles until explicitly planned and tested. |

Keycloak purpose distinction is grounded in tagged
[TokenVerifier](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/core/src/main/java/org/keycloak/TokenVerifier.java)
and [TokenManager](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/services/src/main/java/org/keycloak/protocol/oidc/TokenManager.java).
Per-issuer Relay registration may approve `initial-login` rather than requester `azp`,
but must produce the same narrow workflow audiences and role semantics. Do not relay
a general-purpose login token merely because it has roles. A provider unable to
distinguish ID/access purpose is unsupported.

## Callback validation

Callback validators are configured independently of Sidecar. No trust is inferred
from Sidecar's prior acceptance, workload membership, model/body identity or cookies.

| Rule | Receiving SDK requirement and rejection |
| --- | --- |
| Credential/signature/claims/time/purpose/binding | Independently repeat every admission authentication rule, including RS256, exact issuer, 300s maximum lifetime/60s skew, duplicate/invalid claims, both supported purpose profiles, every `cnf` presence and DPoP. No cookie fallback. |
| Audience | Require the receiving service's own exact audience (`orders-callback`); all audiences remain within that issuer's approved workflow set. Sidecar-only audience fails callback. |
| Keys/trust | Receiver's explicitly approved issuer/profile/key entry; never use raw token URLs or assume admission key configuration installed receiver trust. Key retrieval/withdrawal mechanisms belong to PR-8.0.2. |
| Authority/principal | Map receiver-specific issuer role paths after validation; optionally resolve a native account. Reject resolution failures. Install only the resultant bearer caller for the request. |
| Business checks | Require configured callback URL authorization and existing endpoint/method/record checks. JWT does not establish that the request body belongs to an execution or prove Sidecar origin. |
| Outcome | Missing/invalid credential: 401 and Bearer challenge, no redirect; authenticated insufficient permission/disabled or unresolved account: 403. Resolver infrastructure unavailable: 503 safe problem response, no fallback. Business exceptions use normal safe application error handling after cleanup. |

These explicit substitution rules follow [RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html#section-3);
the deliberate v1 exclusion of all confirmation claims is stricter than
[RFC 7800](https://www.rfc-editor.org/rfc/rfc7800.html#section-3).
[DPoP](https://www.rfc-editor.org/rfc/rfc9449.html#section-7) is unsupported even
when presented as Bearer or alongside a valid workload certificate. Supporting
proof of possession requires separately planned delegation contracts.

## Identity and authorities

Default Spring `Authentication.getPrincipal()` is `CallbackPrincipal(identity,
identity)`; `getName()` is a diagnostic representation of the pair, never a username
lookup key. Application code uses `identity()` for ownership. With resolution,
`nativePrincipal` is the resolver's object and the original pair remains in the
wrapper. Tokens/credentials are not exposed through principal details. Framework
handoff continues to use the verified JWT authentication at Sidecar, not native
callback account objects.

`NativeAccountResolver` is called once per callback request, only after all token
validation; it receives verified identity and mapped token permissions, not raw JWTs.
Missing/deleted/disabled account returns rejection, exception/outage rejects without
default-principal fallback. No implicit username/email linking, account creation or
lookup heuristic. Trusted application mapping may deliberately map several pairs to
one account; ownership and token authority remain per original pair, without merging.

| Explicit authority policy | Effective permissions and freshness |
| --- | --- |
| `TOKEN_SNAPSHOT` (default) | Exactly issuer-approved mapped token authorities. Optional resolver supplies native principal and enabled status only; its permissions are ignored. Disabled/deleted accounts reject when resolved; without a resolver, external disable/logout cannot be detected. Token permissions stay snapshots until expiry. |
| `LIVE_ACCOUNT` | Requires resolver. Effective authorities = intersection of mapped token authorities and resolver's live authorities in the same canonical namespace. Never union or allow live roles to expand the credential's maximum grant. Enabled status and permissions read each callback; failure rejects. |

Role maps are explicit per issuer, source claim path and value. For application
issuer A, `roles:order.read -> ROLE_ORDER_READER` and `order.edit -> ROLE_ORDER_EDITOR`;
for the Keycloak realm, admission maps `resource_access.sidecar-api.roles:execute ->
ROLE_EXECUTOR`, receiver maps `resource_access.orders-callback.roles:read/edit ->
ROLE_ORDER_READER/ROLE_ORDER_EDITOR`. Each source can map multiple approved values;
no automatic prefixing of arbitrary roles, cross-issuer map reuse or authority union.
An independently issuing issuer B's `admin` grants nothing unless B has its own
approved map. Authority changes never change `(iss, sub)` ownership.

Spring wiring `loomspan-sdk.callbacks.identity.authority-policy: token-snapshot`
maps to `AuthorityPolicy.TOKEN_SNAPSHOT`; `resolver-bean: nativeAccounts` maps to a
`NativeAccountResolver` instance. `live-account` requires that bean.
`receiving-adapter-bean` optionally supplies `CustomReceivingAdapter`, otherwise
SDK-managed Spring context installation is used. Equivalent Java:
`IdentityPolicy(policy, Optional.ofNullable(nativeAccounts), Optional.ofNullable(adapter))`.
Custom receiving scope is opened only after resolution and permission selection;
SDK always closes it in `finally`, even when authorization/business processing throws.
It must restore its prior application context, must not grant permissions or alter
validated identity, and must not touch browser sessions. Scope-open failure installs
no identity; adapters must unwind partial installation before throwing. Scope-close
failure still triggers SDK Spring cleanup and safe failure handling. SDK never lets
an adapter select keys or bypass validation. Async propagation requires the explicit
scoped mechanism below; an arbitrary adapter thread-local is not magically propagated.

## Issuer trust model

Current Sidecar is **single issuer** (`SidecarJwtProperties` and
`JwtSecurityConfiguration`); configuration examples here do not implement multi-issuer
support. Future trust entries are finite tuples: exact issuer, key-source reference,
RS256, accepted profile, approved authorized-party values where applicable, required
receiver audience, allowed workflow audience set and issuer-specific role mappings.
Only deliberately trusted issuers may assert users/permissions. A group certificate
admits a workload; it never creates an issuer entry or rights to claim another issuer.
No discovery from arbitrary incoming `iss`, `kid`, `jku` or `x5u`.

Single-issuer deployments use one such policy without group extension. Independently
issuing applications in a group require future Sidecar multi-issuer dispatch and
per-issuer mappings plus independent receiver trust; sharing one approved provider
issuer is also possible. Equal `sub` across issuers remains different ownership.
The [trust/bootstrap/lifecycle reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md)
defines trust storage/admin/bootstrap/key lifecycle, atomic publication and withdrawal
timing. These policy fields constrain that schema; mTLS membership never permits
trust administration. Its companion scenario matrix is planned evidence only.

## Callback composition

Scope: servlet Spring Security/MVC integration first. Reactive/WebFlux callback
security is unsupported by this reference. The final API reference selects the
servlet Java 21/Boot 4.1.0 baseline; runtime evidence is required before compatibility
claims. Expansion requires equally concrete design
and evidence. Standalone HTTP clients and trusted custom identity/receiving adapters
remain supported design scope without requiring Spring login.

`CallbackPolicy(routes, validator, identityPolicy, authorization)` requires a finite
`CallbackRoutes` manifest of normalized absolute path patterns and HTTP methods, with
a reserved namespace, e.g. `/internal/loomspan/**` and GET/POST. The reserved namespace
is bearer-only for **all methods**, including unsupported methods, so a PATCH cannot
fall into browser/session authentication. Allowed method/URL permissions are separate
authorization rules; undeclared operations deny. Reject ambiguous encoded traversal,
servlet-prefix mismatch or inconsistent proxy rewriting; forwarded routes must be
normalized by trusted infrastructure. Customer-supplied opaque matchers cannot prove
isolation and are unsupported in v1. Routes are not inferred from controller annotations.

`CallbackAuthorization` is mandatory and evaluated for authenticated callers in the
selected chain, with deny as default. Use normal Spring `AuthorizationManager` via
the SDK adapter, e.g. `CallbackAuthorizations.spring(urlRules)` where `urlRules`
requires reader for GET and editor for POST, denies everything else in the namespace.
Application endpoint/method/record security executes afterward and can deny further.
Enable the application's normal method security; record access remains business logic
(e.g. Alice cannot edit Bob's order). Neither recipe copies URL rules from other chains.

### SDK-owned dedicated chain

Planned Spring configuration:

```yaml
loomspan-sdk:
  callbacks:
    arrangement: dedicated-chain
    routes: { path: /internal/loomspan/**, methods: [GET, POST] }
    order: 1
    validator-ref: ordersValidator
    identity-policy-ref: ordersIdentity
    authorization-bean: callbackRules
```

Equivalent Java: `CallbackChains.dedicated(http, 1, new CallbackPolicy(
CallbackRoutes.reserved("/internal/loomspan/**", Set.of("GET", "POST")),
ordersValidator, ordersIdentity, callbackRules))`, returning a `SecurityFilterChain`
bean at order 1. Property mode registers that bean; Java mode registers it explicitly,
never both. Browser chain is explicitly order 2 (or later); an earlier chain matching
callbacks is a startup error. SDK reserves only the callback namespace. The chain
uses a null security-context repository, no request cache, no session authentication,
no remember-me/form login/logout/session-management filters that mutate sessions,
bearer-only validation and callback 401/403 handlers. CSRF exclusion is exactly the
reserved callback namespace; no browser request is excluded. There is no session
creation, reading, saving, invalidation, cookie rotation or Set-Cookie on these requests.

### Application-owned chain

```yaml
loomspan-sdk:
  callbacks:
    arrangement: application-chain
    chain-bean: applicationSecurity
    routes: { path: /internal/loomspan/**, methods: [GET, POST] }
    validator-ref: ordersValidator
    identity-policy-ref: ordersIdentity
    authorization-bean: callbackRules
```

Equivalent Java inside `applicationSecurity(HttpSecurity http)`:
`callbackConfigurer.configure(http, new CallbackPolicy(routes, ordersValidator,
ordersIdentity, callbackRules)); return http.build();`. The application registers
the resulting chain, its order/matcher and ordinary browser login/URL/CSRF rules.
The configurer contributes the callback rule at the reserved matcher before browser
catch-all rules, so URL rules are actually applied in this chain. All other application
authorization remains owned by the application. No SDK-owned competing chain is created.

The configurer must build request-aware composition **before context loading**:

1. Select null security-context repository for callbacks; preserve the application's
   original repository on noncallbacks. No callback `loadDeferredContext` or save
   delegates to a session repository. Install fresh empty callback context before
   any identity-reading filter and restore/clear it on every exit.
2. Route callback bearer processing only through SDK validator/mapper. Gate browser
   authentication, remember-me, login/logout, session-management/concurrent-session,
   request-cache and session-writing mechanisms to noncallbacks. Use a callback-null
   request cache; never save a failed callback for login replay. A single `STATELESS`
   flag or a filter added after session loading is insufficient.
3. Select callback 401/403 handlers without redirects; retain browser handlers on
   noncallbacks. CSRF ignore matcher covers exactly designated callbacks. Keep
   browser CSRF token repository and session behavior for every other request.
4. Install callback URL authorization and preserve method/record checks. A globally
   disabled CSRF policy, unguarded session-reading custom filter, global context
   persistence override or competing bearer filter makes this composition unsupported.

The SDK supplies these components; application developers do not implement filters.
Initially supported browser mechanisms are standard form login or OAuth2 login,
standard session context repository and CSRF support whose registrations the
configurer controls through documented Spring APIs. Unguarded custom filters and
opaque context repositories are rejected at startup. If safe composition cannot
be established through those APIs on the selected baseline, this arrangement is an
implementation blocker requiring public Spring-compatible design revision, never
permission to load Bob's session and replace it later. A session-access sentinel in
integration tests must prove zero session access/mutation on callbacks.

### Diagnostics, contexts and asynchronous boundaries

At startup validate exactly one arrangement per reserved namespace, unique chain
order, no earlier shadowing, no overlapping route manifests, required authorization
hook, method policy, CSRF and repository/filter composition, existing chain bean and
validator/resolver wiring. Diagnostics name safe chain/route/component identifiers,
never credentials. Applications register the finite matcher manifests for all chains;
unknown/opaque earlier chain matchers or session-reading filters fail validation,
rather than claiming automatic analysis of arbitrary user configuration. Requests
outside declared callback namespaces retain normal browser behavior. Reject insecure
dynamic overrides after startup; rebuilding security configuration requires restart.

Security contexts are fresh per callback. `finally` closes receiving scope and
clears callback identity on success, rejection, resolver/authorization/business error.
Restore a previous worker context only within explicit nested capture scopes; servlet
entry/exit starts/ends empty, preventing reuse of a stale worker caller.
Never use inheritable/global thread-local security contexts.

Supported servlet async paths are MVC `Callable` and `DeferredResult` using an
SDK-configured context-aware executor/capture wrapper. At task submission the SDK
captures immutable verified caller, effective authorities and optional native principal;
each task opens/closes its own receiving scope and fresh Spring context in `finally`.
Completion, exception, timeout, cancellation and redispatch clear retained references
and restore worker state; denied/error redispatch never reloads sessions or duplicates
business execution. A task already running at cancellation still closes its scope
when it exits; cancellation cannot roll back business effects. Receiver permission
policy is evaluated on request authentication and again on explicitly dispatched
new callback requests, not continuously inside a running task.

Outbound async calls use SDK `CredentialContexts.capture()` on the authenticated
thread, then `captured.wrap(Runnable/Callable)` or an SDK-supplied executor; the
scope binds the selected credential/identity source for that operation and restores
prior state. Captured identity grants no new login or fresh permission lookup.
An expired captured credential fails instead of renewing silently. Arbitrary raw
executors, `CompletableFuture` common pool, arbitrary reactive pipelines and detached
application tasks have no automatic propagation guarantee; missing scope fails
`IdentityUnavailable`. Servlet async execution must use the configured wrappers;
unsupported adapters unable to open independent scopes cannot enable that mode.

Optional JWKS publication has its own path and access policy in the trust reference, disjoint
from the callback namespace; reject collision at startup. A public key route never
permits unauthenticated callback business requests. No callbacks means no callback
chain/listener/TLS configuration; optional JWKS hosting is a separate opt-in.

Ordered chains, context repositories and explicit worker propagation use standard
[Spring chain selection](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html),
[persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)
and [concurrency](https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html)
concepts. Those sources explain mechanisms, not evidence that this SDK configurer
exists or composes correctly on a particular runtime.

## Lifetime and unchanged passthrough

Reference issuance lifetime is five minutes, maximum accepted token lifetime 300s,
verifier skew 60s; no minimum queue-completion guarantee. SDK fails new operations
with already-expired credentials. Admission validates at request time and captures
the exact JWT. Queue handoff retains that credential and its role snapshot; it does
not refresh/revalidate expiry automatically. Framework retains supported execution
authorization. A token expiring in the queue can therefore lead to a callback 401;
that failure is reported without replay, renewal, exchange or substitution. Long
workflows must fit expiry or tolerate explicit callback failure; Sidecar does not
mint longer tokens or require automatic retries. The trust reference separately defines emergency
distrust timing for queued/admitted work; this reference promises no immediate kill.

Same-pair renewal for later authorized polling retains ownership eligibility; changed
issuer or subject cannot inherit the original record, even through the same native
account. Later SDK renewal never replaces the captured JWT of admitted work. Local
logout prevents further session-based credential acquisition; previously issued
bearer JWTs remain usable within validity/skew and trust policy. No immediate
stateless revocation is claimed. LIVE_ACCOUNT rejects new callbacks after disabled
account or lost live rights; TOKEN_SNAPSHOT without resolver cannot detect them.
Already running effects are not rolled back, and no policy promises cancellation.

Only publish-approved, explicitly configured targets receive unchanged tokens.
Publish authority delegates this recipient choice subject to ordinary publication
checks and deployment egress/peer policy; it grants no protected trust administration.
Targets fix scheme/host/port and base path; model inputs cannot select destinations.
Disable redirects, automatic callback retries and cookie reuse; do not carry credentials
to redirect sinks. Require TLS for remote hops or documented enforced local boundaries.
Token audiences/permissions must cover only Sidecar and required trusted recipients;
no wildcard audiences or general-purpose excessive grants. Sidecar cannot narrow the
captured token, and an application unable to issue/obtain a suitably limited token
cannot use Relay for that workflow.

Approved recipients receive reusable bearer credentials: they may reuse one against
Sidecar or another approved audience while identity, network/workload admission and
authorization permit. Tokens are not single-use, execution-bound proof or evidence
that Sidecar sent a body. Group membership does not stop approved-member reuse.
When Sidecar origin is required, callback mTLS ingress must admit Sidecar's specific
workload identity, not all members. HTTPS alone authenticates the server to Sidecar,
not Sidecar to the receiver. This model does not isolate mutually untrusted recipients.

SDK, Sidecar and proxies must keep tokens, signing/TLS keys and internal security
context out of business payloads, prompts, logs and diagnostics. Fixtures test
infrastructure credential canaries separately from deliberately supplied business
data. No content sanitization/redaction is added; preserve business text and supported
Framework diagnostics unchanged, with normal authorization/visibility protections.

## Implementation ownership and dependencies

| Work | Owner and phase | Evidence boundary |
| --- | --- | --- |
| Four strategies, shared issuer, trusted adapters, Spring callbacks, explicit async capture | Independent `sdks/java/` build, Phase 1 | Focused component tests plus real application/provider/Sidecar integration in external test suite |
| Explicit purpose/time/algorithm/binding validation | Sidecar root security tests and SDK validators, Phase 1 | Both validators tested independently; valid SDK preflight is insufficient |
| Finite multi-issuer policies and per-issuer authority conversion | Sidecar root security/execution tests, Phase 2 | Current single-issuer gap; group integration in external test suite |
| Pair ownership and captured token handoff | Sidecar root execution/rest tests, Phase 1; renewal/expiry Phase 3 | Existing behavior source/tests inform design, do not prove new SDK support |
| Trust storage/admin/bootstrap/signing/JWKS/withdrawal and peer lifecycle | PR-8.0.2 design; Phases 1/2/3 implementation | Must preserve policy fields and authority boundaries here; mechanisms/evidence remain outstanding |
| Runtime/build/transport baselines, deployment recipes and complete matrix | PR-8.0.3 design; Phases 1/2/3 integration | External `C:/opendev/code/loomspan-sidecar-test-suite`; independently attributable local evidence |
| Framework execution authorization/lifecycle and REST handoff | Existing beta.8 supported public contracts | Only `ai.loomspan.api`, including planned supported `RestSkillHandler`; no new SPI, bean replacement, reflection or Java `@SkillMethod` skills |

Demonstrated gaps: Sidecar currently lacks explicit purpose/`cnf`/future-iat/lifetime
rules and finite per-issuer authority policy; no SDK strategy/resolver/callback-chain
implementation exists. Existing clients already disable redirect/retry/cookie reuse
and forward the exact captured JWT. [Research](../research/PR-8.0.1-sdk-v1-identity-callback-contracts.md)
identifies the matching beta.8 public contracts and inspected test locations; none
were executed as new integration evidence in this ticket. Retain ArchUnit bans on
`ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`. Missing public Framework
contracts must be planned there; no missing new Framework SPI is identified here.

Current Sidecar baseline remains Java 21, Boot 4.1.0, Framework 1.0.0-beta.8 and
Sidecar 1.0.0-beta.4-SNAPSHOT. The [API reference](2026-10-15-sdk-v1-api-reference.md)
selects the same Java/Boot baseline, independent modules and blocking/async core,
with servlet-only callbacks. Filter/repository composition still requires its own
implementation evidence; no reactive compatibility is inferred.
Framework behavior uses documented `loomspan.*`; Sidecar owns `loomspan-sidecar.*`;
these future SDK settings use `loomspan-sdk.*`; Boot SSL bundles stay standard.

## Decisions and sign-off

Selected: narrow Keycloak reference, two explicit access profiles, shared SDK issuance,
60s skew/300s lifetime, pair-preserving principal, snapshot default/live intersection,
servlet-first bounded async, both callback arrangements with strict supported-chain
restrictions, finite issuer authority and unchanged reusable passthrough. These
choices minimize new protocols and preserve customer login/business authorization.
No unresolved material choice within this ticket is knowingly deferred. An inability
to implement safe mixed-chain isolation on the chosen runtime blocks that arrangement
and dependent implementation sign-off; it does not authorize a weaker guarantee.

This contract requires independent Step 5 security review before ticket completion.
The [deployment reference](2026-10-15-sdk-v1-deployment-recipes.md) and
[consolidated matrix](2026-10-15-sdk-v1-acceptance-matrix.md) complete the remaining
Phase 0 design/ownership decisions. Future implementation tickets must read those
contracts and must not claim runtime support established by document verification.
