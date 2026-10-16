---
date: 2026-10-11
updated: 2026-10-15
repository: loomspan-sidecar
branch: main
commit: de7cc77b01bdae380e2c84398e77daa47e766553
ticket: none
tags: [sdk, security, research]
---

# Security evidence for isolated Sidecar and two-way mTLS trusted group

## Scope and evidence status

The [SDK roadmap](2026-10-11-java-spring-sdk-roadmap.md) now specifies protected local
HTTP, HTTPS and mTLS, independently selected for invocation and callbacks, following
the 2026-10-14 scope clarification. This supersedes the earlier restriction to two
fixed security options. Sources below were checked on 2026-10-11/12; this is
research evidence, not completed Loomspan interoperability or security validation.
Customers retain their existing login infrastructure and authorization policies.
**Loomspan never handles customer application login or supplies, hosts or replaces
the customer's login/authorization server.** End users retain their existing login
flow. Application-issued execution JWTs represent already-established authentication;
they introduce no login service. Sidecar management login is a separate concern.
The agreed SDK scope includes all four credential strategies: Relay, Exchange,
Spring-issued and Custom identity. These operate across supported transport compositions;
isolated and two-way mTLS trusted-group deployments are reference patterns, not SDK
security modes restricting transport selection. SDK responsibilities below are planned
direction, not completed integration evidence.

Apply the [design lens](../design-lens.md). Replace superseded development contracts
directly; no compatibility shims or migration machinery solely for obsolete behavior.
No data reset, deployed-data deletion or production change occurred.

## Independent transport selection — agreed v1 scope

Support SDK-to-Sidecar and Sidecar-to-application HTTP for protected same-host or
same-container communication, ordinary HTTPS for trusted-network deployments, and
mTLS where independent workload membership is required. Select each direction
independently, with proxy placement and JWT strategy also independent. Plain HTTP
requires an enforced local boundary; sharing a host alone does not prove isolation.
HTTPS retains server-chain/hostname checks. Every transport retains JWT validation,
ownership and user/business authorization. No failure may trigger a weaker fallback.

Direct HTTP requires no Caddy. HTTPS may use existing application/server TLS, ingress
or Caddy. Both previously agreed mTLS invocation paths remain required in v1. Native
outbound TLS does not implement callback ingress; configure the receiving server or
proxy separately when callbacks exist. No-callback execution needs no callback
listener; electing to host a JWKS endpoint requires an existing application web listener.
Uploaded keys or separately hosted JWKS allow outbound-only applications. The roadmap's
transport-composition diagram is the general model; the
proxy/mTLS diagrams below it are specific reference arrangements.

These are requirements, not newly verified interoperability claims. Prove the direct
and proxy arrangements and mixed-direction combinations through real integration
tests, including rejected credentials, invalid server identities, local isolation,
independent callback configuration and no downgrade. The customer still owns login.

## 1. Isolated Sidecar

A dedicated Sidecar accepts only its logical application's work and calls only its
approved callback targets. The deployment enforces that relationship through trusted
identities and restricted access. Loopback/network isolation can narrow exposure but
is not proof of the identity of a particular local process.

[NIST SP 800-204A](https://csrc.nist.gov/pubs/sp/800/204/a/final) describes service-mesh
security architecture and separation of security enforcement responsibilities.
[Istio security](https://istio.io/latest/docs/concepts/security/) documents workload
identity, mTLS and authorization. Applicability to a dedicated pair is an architectural
inference: restrict identities and permitted communication to the application's
boundary. This does not require a service mesh in every installation, nor does citing
NIST establish that a future Loomspan configuration is certified or secure.

The agreed setup goal is automatic trust/credential management without manual
certificate commands. A concrete recipe must establish initial trust, protect private
material, preserve identity across restarts, renew credentials and verify denied access.
The 2026-10-15 decision selects **automatic deployment-specific JWT keys as the default
for isolated Docker Compose with Spring-issued or Custom identity**. Deployment tooling
generates a unique asymmetric signing-key pair at first initialization, delivers private
material only to the issuing application's protected storage, and configures Sidecar's
public verification trust and approved issuer/audience settings. Configure receiving
callback trust separately where needed. Preserve keys and issuer identity across restarts
and use one signing-state owner with the SDK, not competing startup generators. Sidecar
remains a validator, not a token issuer or holder of the application's signing key.

Retain public-key upload and administrator-configured JWKS URLs as alternatives. Relay
and Exchange retain their provider-owned keys and trust with no fallback to local issuance.
Use no shared default private key or authentication-disabled mode. JWT provisioning does
not provision TLS/mTLS certificates or replace network isolation and peer policy.

Phase 0 must define the concrete mechanism, protected storage, startup ordering, export,
recovery and installation of bootstrap trust under file/database authority. Operator-
selected deployment initialization is distinct from runtime management tokens; subsequent
management trust changes require interactive admin login. Repeated bootstrap must not
overwrite trust or restore withdrawn keys. Test unique keys per deployment, initial setup,
restart persistence, callback trust and private-key isolation, plus missing/corrupt-state
failure without silent reset or weaker validation. Document explicit reset impact. The
default is agreed, but no concrete bootstrap implementation is yet validated.

## 2. Two-way mTLS trusted group — reference deployment

Each workload has its own certificate and private key. Both directions authenticate
peers, and policy permits only the intended group identities. Sidecar's deployment
restricts admission to members and callbacks to approved targets; target proxies
authenticate Sidecar's workload and rely on its deployment's group enforcement.
Existing user/record authorization still applies.

The 2026-10-15 clarification reserves this two-way guarantee for configurations with
mTLS and explicit peer policy on both invocation and callback connections. Independent
transport selection remains supported. Describe mixed configurations by their actual
transports, such as **mTLS invocation with HTTPS callbacks**, without assigning them
the two-way guarantee or introducing another SDK security mode.

In that mixed example, invocation authenticates the Sidecar server and the calling
workload, with membership enforced at Sidecar's proxy and separate JWT validation in
Sidecar. HTTPS callbacks authenticate the callback server to Sidecar; the receiving
application validates the user JWT and business permissions, but neither ordinary
HTTPS nor the passed-through JWT proves that Sidecar is the calling workload.
Requiring that identity calls for callback mTLS with peer policy or another explicitly
planned and supported mechanism. Protected local HTTP retains its documented local
boundary. Workloads without callbacks require only invocation protection and do not
need a callback listener or claim two-way mTLS protection.

[Istio authorization policy](https://istio.io/latest/docs/reference/config/security/authorization-policy/)
provides a documented way to authorize authenticated workload identities. Strict mTLS
and explicit access rules are separate requirements. Trusting a general corporate CA
alone is not a policy limiting access to the selected group.

[SPIFFE/SPIRE concepts](https://spiffe.io/docs/latest/spire-about/spire-concepts/)
describe workload identity and attestation as existing infrastructure that can support
this model. They do not grant blanket authorization to all identities in a trust domain.
Neither Istio nor SPIRE is a mandatory dependency selected by this research.

This option intentionally accepts otherwise-authorized work initiated by any permitted
member. It offers no origin-only callback guarantee or separation of mutually untrusted
applications within the group. A protected proxy/mesh deployment must also prevent
bypass and secure its backend connection. User identity remains separate from the
immediate peer identity established by mTLS.

## Agreed direction: two v1 mTLS client paths

On 2026-10-12, the developer selected proxy-managed mTLS and group enforcement as
the direction to pursue, with Caddy handling mTLS in the reference deployment. Sidecar
Java code should not need to handle peer certificates or know group membership.
This is an architectural direction, not a validated deployment or a third option.

The 2026-10-14 scope decision requires both `SDK -> client Caddy -> Sidecar Caddy ->
Sidecar` and `SDK -> Sidecar Caddy -> Sidecar` in v1. The first uses protected local
HTTP to client Caddy and mTLS between proxies. The second uses SDK-native outbound
mTLS directly to Sidecar Caddy. Both apply to isolated and trusted-group deployments
and all four credential strategies. They are transport arrangements, not new user
credential strategies or deferred extensions. Select explicitly without weaker fallback.

In the native path, the SDK loads provisioned workload credentials, verifies the server
chain/hostname and manages credential reload and connection retirement. Infrastructure
owns enrollment, renewal and material delivery; Sidecar Caddy enforces membership.
These SDK lifecycle capabilities are requirements, not demonstrated support. Renewal,
key rotation and CA trust rotation need separate tests, including invalid updates,
expiry recovery and removal of members with established connections.

[Caddy TLS documentation](https://caddyserver.com/docs/caddyfile/directives/tls)
documents `client_auth` with `require_and_verify` and configured trust authorities
for inbound client authentication. Its
[reverse-proxy transport](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#transports)
supports `tls_client_auth` to present a client certificate and `tls_trust_pool` to
configure upstream trust. These capabilities support the proposed composition;
exact syntax and lifecycle behavior must be verified against the repository's
pinned Caddy 2.10.2, since current online documentation can describe newer releases.

For the client-Caddy arrangement, the proposed paths are application -> local proxy -> mTLS -> Sidecar proxy -> Sidecar,
and Sidecar -> local proxy -> mTLS -> application proxy -> application. Adding mTLS
only to Sidecar's inbound listener does not cover callbacks. Proxy placement and
routes remain design work. Each workload's proxy holds its distinct identity;
protected local HTTP hops must prevent both direct backend access and unauthorized
use of that proxy's outbound identity. Unpublished host ports alone do not establish
isolation from other containers with access to the same network.

SDK-native outbound mTLS creates no callback listener. A workload without REST callbacks
needs no callback ingress or callback TLS setup, and obtains results through outbound
API calls. Client-Caddy invocation still requires client Caddy and its protected local
proxy listener, even without callbacks. SDK-native mTLS requires no client Caddy for
invocation; both mTLS paths retain Sidecar Caddy. No-callback operation removes callback
ingress requirements, not the selected invocation path's proxies or security controls.
An optional application-hosted JWKS endpoint independently requires a web listener.
Where callbacks are required, independently configure and test receiving mTLS through
client Caddy, existing ingress or application-server TLS. Both client paths and their
documented callback recipes need end-to-end evidence before release.

Membership requires restricted certificate issuance/trust or explicit member policy.
Outbound routes must restrict approved targets rather than expose an arbitrary proxy.
Sidecar retains JWT validation, issuer/subject ownership, unchanged user credential
passthrough and configured REST routing; receiving applications retain business
authorization. No forwarded peer-identity header is required for Sidecar to enforce
membership, because the proxy enforces that boundary before forwarding.

Provisioning, renewal, trust changes and member removal belong to the deployment
layer. Define and test removal for already-established connections as well as new
handshakes. Management/browser ingress must not create a bypass around execution
membership policy. Automatic workload enrollment and Spring user-identity propagation
remain unresolved; ordinary HTTPS certificate automation does not establish either.

### Existing Caddy foundation

- [Production Compose](../../../examples/production/compose.yaml) pins Caddy 2.10.2
  by default, disables Sidecar server TLS, persists Caddy data/configuration and
  leaves Sidecar application and health ports unpublished.
- [Production guidance](../../../examples/production/README.md) documents the private
  HTTP hop and internal CA, public ACME, Cloudflare DNS and supplied certificate modes.
  Internal CA clients still install the public trust root; supplied certificate renewal
  remains the operator's responsibility.
- [TLS verification script](../../../scripts/verify-production-tls.py) contains checks
  for trust rejection, forwarded-header handling, certificate persistence across
  recreation, reissuance after fixture leaf loss and supplied certificate replacement.
  Public/DNS checks adapt configuration without live CA/provider calls. The script
  was inspected, not run for this update; fixture reissuance is not proof of scheduled
  renewal or complete group lifecycle behavior.

The existing configuration is server HTTPS, not full group mTLS. It is a useful
starting point for both selected options and should be reused where the validated
recipe permits, rather than duplicating certificate management inside Java.

## Agreed direction: SDK-managed JWT and user security

The SDK should own as much JWT and user-security integration as possible across
all four agreed strategies. Customers select a strategy and configure trust, using
their existing login and authorization. They should not need to build JWT plumbing
or callback authentication filters for supported integrations.

| Strategy | Planned SDK behavior |
| --- | --- |
| Relay | Retrieve a compatible existing access JWT and attach it to execution requests. |
| Exchange | Obtain an appropriate execution JWT from a configured authorization server on behalf of the user. |
| Spring-issued | Read authenticated Spring identity and selected authorities and sign a short-lived execution JWT with configured application keys. |
| Custom identity | Read identity and authorities through trusted application code and use the same issuance machinery as Spring-issued. |

All four are planned v1 work. Select explicitly through configuration or Java
initialization; never fall back between strategies on failure. Exchange depends on
server support, authorization and compatible resulting claims; existing OAuth login
alone does not establish those capabilities. Supported server/library versions and
exchange behavior require implementation research and integration evidence.

Spring-issued and Custom identity share maintained JWT machinery for claims, signing,
validation and lifetime. Prefer asymmetric signing with private keys held by the
issuing application and trusted public verification material supplied to validators.
The SDK integrates with provisioned signing keys and rotation; deployment establishes
trust and protects keys. Caddy manages workload mTLS, not JWT issuance or issuer trust.

All callback paths validate tokens before establishing native Spring authentication
or invoking a custom receiving hook. Define signature/algorithm, issuer, audience,
time and required-claim validation. SDK mapping and optional principal lookup hooks
preserve existing endpoint, method and record authorization without serializing
passwords, sessions or arbitrary security objects. Identity comes from trusted
server authentication/provider code, never model input or unverified request data.
The SDK handles credential attachment, supported asynchronous propagation, concurrent
user isolation, callback context cleanup and actionable failures.

Permission freshness, disabled accounts, logout, queue/callback expiry and signing-key
rotation remain explicit contract decisions. Capture them as SDK behavior/configuration
and narrow policy hooks so each application need not invent its own security mechanics.
Issuing a fresh JWT for later requests does not refresh the credential already captured
by an admitted execution under current Sidecar passthrough semantics.

First prove these strategies using existing Sidecar contracts. One configured issuer,
issuer/subject ownership and unchanged passthrough may already suffice for some
deployments. Independently issuing applications in a group expose a possible issuer-trust
gap: define who may assert which users/authorities and audiences acceptable to Sidecar
and callbacks. Membership in the mTLS group does not establish JWT issuer authority.
Any demonstrated Sidecar gap requires explicit planning; Sidecar remains a validator
and propagator, not a token mint, refresh or exchange service. No standing design-lens
contract is changed by this direction update.

### Agreed callback security integration — 2026-10-15

The [roadmap's callback composition contract](2026-10-11-java-spring-sdk-roadmap.md#agreed-callback-security-composition--2026-10-15)
supports an opt-in SDK-owned security chain for reserved callback routes and an SDK
configurer contributing authentication to an application-owned chain. Both use the same
JWT and identity/authority components. Select one arrangement per callback route; scope
SDK behavior to callbacks even in a chain also serving ordinary application requests.
Spring's first-matching-chain behavior means separate chains do not inherit each other's
authorization rules. Required callback URL rules must live in the selected chain;
preserve application endpoint, method and record checks rather than replacing them
with a valid-token check. Exact ordering, matchers, hooks and diagnostics need Phase 0
design and tested recipes, not a promise to combine arbitrary security configurations.

Callback authentication is bearer-only and ignores session cookies. Do not load or
merge session identities/authorities, fall back to a valid session for a missing/invalid
token, or persist callback identity to a browser session. A token for Alice with Bob's
session cookie authenticates only Alice without changing Bob's session. Authentication
failure rejects the callback rather than redirecting to browser login. Preserve existing
login/session behavior and CSRF protection outside callbacks; any CSRF exception applies
only to explicitly designated bearer-only callback requests, not a mixed-use chain or
the entire application. Isolate and clean up security context on success, rejection,
exceptions and supported async paths. Keep the optional JWKS endpoint's key-publication
access policy separate from callback business-route authentication.

For both arrangements, test an application with existing login, sessions, role-restricted
routes and CSRF protection. Cover chain precedence, URL/method/record authorization,
valid bearer with a different user's session, missing/invalid bearer with a valid
session, cookie-only requests and bearer-only requests. Prove no session fallback,
authority merging, session identity persistence or login redirects on callback failures.
Verify unchanged browser protections, callback-scoped CSRF exceptions, concurrent/error/
supported async context cleanup, JWKS route separation and supported overlap/order
diagnostics. These are agreed requirements; integration support remains to be proven.

### Agreed issuer-aware identity mapping — 2026-10-15

The [roadmap's identity and authority contract](2026-10-11-java-spring-sdk-roadmap.md#agreed-issuer-aware-identity-and-authority-mapping--2026-10-15)
preserves verified issuer and subject (`iss`, `sub`) as user identity, consistent with
Sidecar execution ownership. Usernames/display names and roles are separate claims.
Multiple roles map to Spring authorities through explicit issuer-aware claim/value
mappings; the same role name from another issuer has no implied equivalent authority.
Role changes do not change the identity pair. Do not automatically combine token roles
and local permissions or bypass application endpoint/method/record authorization.

An optional trusted application bean resolves verified issuer/subject to a native
local principal after token validation. Retain the original pair alongside that
principal. Without the bean, use the pair and explicitly mapped authorities without
requiring local accounts. When resolution is configured, missing accounts and lookup
failures reject access without default-principal fallback, implicit account creation
or username/email matching. Explicit account equivalence belongs to application code
or configuration, not a new SDK account-linking subsystem, and never merges Sidecar
execution ownership across distinct pairs.

Phase 0 must settle exact bean/principal contracts and the explicit choice/precedence
of token-authority mappings versus live permission lookup, including freshness and
disabled-account/logout rules. Renewed same-pair tokens preserve ownership eligibility
subject to other authorization checks; a different issuer or subject does not grant
access to the original pair's executions, even for a shared local account.

Required evidence covers issuer/subject collisions, multiple roles, issuer-specific
role mappings, role changes, default identity, successful and failed local resolution,
preservation of the original pair and no resolver invocation for invalid JWTs. Verify
no implicit linking by matching username/email and ownership behavior after renewal or
identity changes. Multi-issuer evidence depends on an explicitly planned issuer-trust
contract, not an assumption that current Sidecar supports multiple issuers. These are
agreed implementation requirements, not completed integration evidence.

### Agreed v1 bearer execution-token boundary — 2026-10-15

The [roadmap's execution-token contract](2026-10-11-java-spring-sdk-roadmap.md#agreed-v1-execution-token-compatibility--2026-10-15)
supports ordinary bearer access JWTs over all supported transports, including mTLS.
Workload mTLS and JWT validation remain independent. V1 explicitly rejects
sender-constrained execution tokens, including certificate-bound and DPoP-bound tokens;
transport mTLS support does not imply support for token-to-certificate binding.

ID tokens and other wrong-purpose JWTs must also be rejected as execution credentials,
even with a valid signature from a trusted issuer. Define each supported provider's
audience and token-purpose rules, using its documented type headers/claims where
applicable. Do not require a universal `at+jwt` marker, accept generic `JWT` as proof
of purpose, infer purpose from roles or relax audience validation. Unresolved token-
purpose ambiguity prevents claiming provider compatibility. Relay selects access tokens,
not login ID tokens; Exchange results must satisfy the execution-token profile, and
Spring-issued/Custom identity issue a documented distinguishable execution profile.
These rules apply at both Sidecar admission and SDK callbacks, not only client selection.

Test validly signed ID tokens, other wrong-purpose tokens and wrong-audience access
tokens against both validators. For supported profiles with overlapping audiences, test
the additional purpose checks explicitly. Include correct Relay selection, incompatible
Exchange results, accepted execution tokens and rejection without relabeling/fallback.
The [JWT security guidance on distinct validation rules](https://www.rfc-editor.org/rfc/rfc8725.html#section-3.12)
supports this separation. Exact provider profiles remain Phase 0 work, not a claim of
completed implementation or interoperability verification.

For all four credential strategies, Sidecar admission and SDK callback validation must
reject unsupported confirmation (`cnf`) claims, including malformed/unknown methods,
and unsupported DPoP execution authentication. A valid signature or admitted workload
must not turn a constrained token into an ordinary bearer credential. SDK compatibility
checks supplement fail-closed server enforcement. Exact token-profile/error contracts
remain Phase 0 work; never ignore or strip bindings, share the originating client's
private key, or silently fall back to another credential.

Relay needs an ordinary bearer access JWT; Exchange must return one through an explicit
provider-authorized flow; Spring-issued and Custom identity issue bearer execution
JWTs. Authentication to an issuer using mTLS is distinct from receiving a
certificate-bound token. Support for such issuer authentication or sender-constrained
exchange inputs requires separate provider planning and is not implied here.

Customers requiring sender-constrained execution credentials end to end cannot use
v1 under that policy. Customers using them elsewhere need an explicitly permitted
separate bearer execution credential. Sidecar cannot prove possession of the original
application's private key when forwarding its token to a callback. Future support
requires planned delegation/callback-credential contracts, not just mTLS configuration.

Required evidence covers successful bearer-token invocation/callbacks over both mTLS
client paths and rejection of certificate-bound/DPoP-bound JWTs even when presented as
Bearer, DPoP execution authentication, and malformed/unknown confirmation claims.
Exercise both admission and callback validators, incompatible Relay/Exchange results,
valid workload certificates, and failure without fallback. Setup and compatibility
guidance must state the limitation. These are agreed requirements, not verified current
implementation behavior.

### Agreed unchanged-token passthrough trust requirements — 2026-10-15

Retain unchanged execution-JWT passthrough. The
[roadmap's trust boundary and acceptance scenarios](2026-10-11-java-spring-sdk-roadmap.md#agreed-unchanged-token-passthrough-trust-boundary--2026-10-15)
make callback destinations trusted recipients of reusable user credentials. An approved
recipient, or an attacker controlling it, may reuse a valid token wherever workload
admission, audience and authorization allow. Signatures prevent modifying claims; they
do not make tokens single-use or prove Sidecar originated a request or approved its body.
This model does not isolate mutually untrusted applications or support arbitrary
third-party callback recipients safely.

[RFC 6750's bearer-token threat model](https://www.rfc-editor.org/rfc/rfc6750.html#section-5)
describes disclosure and replay risks. The
[OAuth security best current practice](https://www.rfc-editor.org/rfc/rfc9700.html#section-2.3)
supports audience and privilege restriction. Applied here, use narrowly scoped execution
credentials intended for Sidecar and necessary callbacks; unchanged passthrough cannot
reduce a token's authority at each hop. Every recipient/intermediary adds exposure, and
a token may grant more authority than one callback requires. mTLS limits which workloads
can present it, but does not prevent reuse by an otherwise authorized group member.

Publish authority includes configuring and approving REST callback destinations,
including services receiving reusable execution JWTs through caller-passthrough.
Authorized publication constitutes destination approval without a separate interactive
approval step; deployment network/peer restrictions still apply.
Forward only to approved configured targets, prevent credential forwarding to other
redirect destinations, prevent infrastructure-introduced credential disclosure into
business payloads/logs/diagnostics/model inputs, and
protect remote hops with TLS or local HTTP with the agreed isolation boundary. Callback
applications must validate tokens and enforce operation/record permissions. If the
caller must be Sidecar, use callback mTLS with Sidecar-specific peer policy rather than
trusting every group member. Choose bounded lifetimes accounting for queued work;
expired callback tokens fail explicitly without refresh, exchange or service-identity
fallback. Later SDK credential renewal does not replace an admitted execution's token.

Required local acceptance evidence includes rejected nonmembers and, where configured,
non-Sidecar callback callers; wrong audiences; unauthorized operations/records; expired
queued/callback credentials; redirect containment; and no infrastructure-introduced
fixture credential leakage into captured logs, diagnostics and model-bound inputs.
Keep authentication fixtures separate from business content in leakage tests; these
checks do not require sanitizing caller-supplied or callback-returned secrets. Also demonstrate the residual
limit with controlled reuse by an otherwise authorized recipient. Do not claim replay
prevention or execution-bound authorization from passthrough. Requirements forbidding
callback recipients from holding a Sidecar-usable credential would require separate
contract planning, not an implicit change to passthrough. These are agreed protections
and tests to implement, not a claim of completed integration verification.

### V1 data responsibility: no business-data sanitization

The [Sidecar design lens](../design-lens.md#preserve-the-available-diagnostic-contract)
preserves business content and adds no sanitization contract. SDK, Sidecar and console
do not classify, mask, redact or sanitize caller inputs/prompts or callback responses.
Applications remain responsible for credentials or sensitive data they place in those
payloads, which may flow into model context, results and selected diagnostics. V1 does
not promise to detect/remove them. Authentication, authorization and visibility controls
protect access without rewriting business content.

Infrastructure must still avoid introducing credentials, private keys or internal
security context from authentication plumbing into business data, logs, diagnostics or
model inputs. Supported authentication transport and verified identity integration
remain separate. Leakage tests use synthetic credentials to verify that boundary, not
blanket redaction of arbitrary input/output data. Preserve existing framework public
diagnostic/projection behavior without removing or extending its narrow existing rules;
this decision adds no sanitizer or new framework behavior.

### Proposed component flowcharts

The roadmap's [component and request flowcharts](2026-10-11-java-spring-sdk-roadmap.md#proposed-component-and-request-flows)
record the responsibility boundaries agreed on 2026-10-14: customer-owned login,
four SDK credential strategies, Caddy workload authentication, Sidecar execution
admission/ownership, Framework skill execution, and application business authorization.
They include invocation transport and conditional REST callbacks with validated identity
mapping and context cleanup. These are proposed designs, not implementation evidence;
ordinary responses and status polling do not constitute REST-skill callbacks.

### Proposed configuration and customer responsibilities

The roadmap contains the canonical
[proposed YAML examples for all four strategies](2026-10-11-java-spring-sdk-roadmap.md#proposed-customer-configuration-examples),
including common callback settings. All `loomspan-sdk.*` keys, adapter names and
trust-file formats shown there are design proposals, not implemented settings or
verified Spring configuration. The 2026-10-13 clarification records the intended
customer experience; it is not new interoperability evidence or a revalidation of
the external sources checked on 2026-10-11/12.

| Strategy | Customer supplies | SDK/deployment automation goal |
| --- | --- | --- |
| Relay | Existing compatible access JWT source, issuer/audience trust and any required provider configuration. | Retrieve/attach tokens and validate/map callbacks; consume the existing provider's public verification keys. |
| Exchange | Existing server with an authorized exchange flow, usable subject credential and exchange client registration with protected client credentials. | Perform exchange and subsequent request/callback handling without taking over the provider or its signing keys. |
| Spring-issued | Existing Spring authentication, stable subject/authority choices, approved trust and durable protected signing storage or signer. | Manage supported signing-key lifecycle, optionally expose public JWKS, issue execution JWTs and establish validated callback authentication. |
| Custom identity | Trusted server-side identity adapter, the same issuance prerequisites, and a receiving adapter if needed. | Reuse Spring-issued JWT/key machinery and deliver verified identity to the custom integration. |

Deployment tooling should generate stable issuer, audience and trust configuration and
provision protected storage where possible, based on customer-approved trust decisions.
Callback trust and Sidecar admission trust must both be configured. Discovery must not
turn an incoming token's claimed issuer into an automatically trusted issuer. Signing
state must survive restart, and replica coordination and rotation require verification.
Caddy owns proxy workload mTLS; the direct SDK path owns client TLS integration.
Neither transport choice issues
user JWTs nor decides which issuer may assert a user's permissions.

Customers retain business authorization and permission-freshness decisions, but should
not implement JWT signing, validation filters or security-context cleanup. The SDK
must integrate only with designated callback routes and preserve the application's
existing login routes. Automatic provisioning is a goal, not a proven bootstrap recipe.

### Agreed console trust sources and optional SDK endpoint — 2026-10-15

Automatic deployment-specific key provisioning is the default for isolated Compose with
Spring-issued or Custom identity. Console uploads and JWKS URLs remain explicit customer-
managed alternatives, not required manual steps for that default. Invalid supplied trust
must not silently fall back to generated keys. These are setup/distribution choices,
not additional execution credential strategies.

The [roadmap's verification-key decision](2026-10-11-java-spring-sdk-roadmap.md#agreed-verification-key-configuration-and-optional-jwks-endpoint)
requires console configuration of either uploaded public verification keys (including
public JWKS) or a trusted JWKS URL, with explicit issuer and audience settings. A URL
may belong to the application, deployment infrastructure or an existing provider.
Private signing keys remain with the issuer. Incoming tokens cannot select a trusted
key URL. Exact formats, management contracts and publication/persistence behavior
require planning within the existing configuration-authority rules.

V1 uses one console administrator plus administrator-created opaque management tokens
for AI/automation access to the same management REST API. Cumulative Read, Edit and
Publish presets permit inspection, draft authoring/validation and publication respectively,
subject to live account authority and existing editing/validation/publication rules.
Publish needs no mandatory browser approval. Tokens do not create additional human roles
or a separate AI API; richer human delegation remains post-v1 work if needed.

Publish tokens may configure REST callback destinations, including approving new recipients
of unchanged execution JWTs. Granting Publish therefore delegates the choice of services
receiving user credentials as well as ordinary authoring/publication. No separate interactive-admin approval
is required for destination changes. This does not grant issuer/key trust administration or
bypass deployment egress, transport, peer policy, callback authorization or publication checks.

Account administration, token creation and issuer/key trust changes require the admin's
interactive login; no token preset grants them. Protect uploaded keys, JWKS sources,
issuer/audience acceptance, issuer-to-authority mappings and emergency trust withdrawal/
restoration. Token-authorized import, publication and rollback must preserve current
protected trust; reject changes atomically, including stale-draft changes detected under
the commit gate. Do not silently omit protected fields or allow indirect privilege bypass.

Tokens are individually named, expiring, revocable, shown once and stored as hashes.
Clients store credentials outside prompts, skills and logs. Check effective token/live-
account authority per request and before committing after waits. Preserve browser CSRF
and reject ambiguous mixed session/token management credentials; callback-cookie ignoring
does not apply here. Management tokens grant no execution access and execution JWTs no
management access. Retain file-mode restrictions for browser and automation alike.

Test all presets, successful AI publication including changed caller-passthrough callback
destinations, denial of interactive-only operations and
indirect trust changes, expiry/revocation/account changes, mixed credentials, browser CSRF
and normal lease/revision/publication checks. Reconcile existing management plans and
design-lens guidance before implementation; these are requirements, not current-code claims.

For Spring-issued and Custom identity, an optional SDK-provided JWKS endpoint will
use the application's existing web server, with properties or Java beans configuring
the endpoint and key source. It starts no separate listener or port and exposes only
public keys. It is disabled unless explicitly enabled; enabling it without a supported
web server must fail clearly. Define property/bean precedence and preserve existing
application routes. Applications using uploads or separately hosted JWKS need no SDK
endpoint. Callback validators require their own trust configuration independently of
what is configured in Sidecar's console.

Use maintained Spring/Nimbus components for signing, validation and key serialization.
URL retrieval/cache refresh does not generate or rotate private signing keys. Uploaded
keys require coordinated operator updates; each publication workflow needs explicit
rollover overlap, persistence and retirement behavior. Automated rotation and replica
coordination remain design work. Phase 0 must define these contracts; integration and
lifecycle evidence must prove both trust sources, endpoint configuration, outbound-only
operation, public-only output, key rollover and retrieval/cache failure behavior.
These are agreed requirements, not implemented features or new interoperability evidence.

Distinguish routine rollover overlap from emergency withdrawal of a compromised key or
issuer. Phase 0 must define an administrator procedure, cache invalidation and rejection
timing in Sidecar and callback validators, retrieval-outage/restart behavior and treatment
of queued/admitted work. JWKS removal is not an instantaneous revocation guarantee, and
withdrawal neither rolls back completed effects nor implicitly cancels executions.
Import, rollback and stale refresh must not silently restore withdrawn trust; restoration
requires an explicit administrator decision. Test graceful rotation separately from
emergency distrust for both uploaded/JWKS sources, including cached keys, outages,
restart, queued work, callbacks and restoration attempts. Test interactive-administrator
enforcement on trust mutations, including denial of Publish tokens, and document the
actual exposure window across validators.

### Agreed public JWKS file export — 2026-10-15

The [roadmap's export contract](2026-10-11-java-spring-sdk-roadmap.md#agreed-public-jwks-file-export--2026-10-15)
provides opt-in property-driven file export after signing-key initialization and a Java
API/Spring bean for custom control. No customer startup hook, HTTP listener or Maven
command is required. The operator obtains the public JWKS file, optionally through a
Compose-mounted host directory, and uploads it using the interactive-admin console flow.
Automatic Compose trust provisioning does not require this manual step.

Use the same public publication source as the optional JWKS endpoint, with a consistent
snapshot of public keys, key IDs and verification metadata. Include publishable overlap
keys and exclude withdrawn keys; never export private parameters or symmetric secrets.
Export itself does not generate, rotate or reset signing keys or select an alternate
store. Enable only with an explicit output path. Property-driven startup export skips an
existing output file without failing startup or modifying it, even after signing-key
changes; the file retains its previous snapshot until explicitly replaced. When writing
an export, fail clearly for unavailable material or write errors. Phase 0 defines atomic
output and explicit replacement behavior without
silent overwrites or damage to the signing store. Files are point-in-time snapshots;
key changes require explicit re-export/upload or the separately selected JWKS URL option.

Retain the Java API for custom integration and Maven dependency distribution of the SDK,
but no Maven export command/plugin or standalone CLI is required for v1. Test both export
interfaces, disabled export, no-listener operation, agreement with endpoint publication,
public-only output, rollover/withdrawal snapshots, repeated startup with an existing file
before and after signing-key changes, and write/replacement failures without
signing-state mutation. Property/API names remain design proposals; document local and
Compose retrieval/upload steps before claiming support.

## Certificate lifecycle evidence supporting both options

[Smallstep production guidance](https://smallstep.com/docs/step-ca/certificate-authority-server-production/)
documents trust-root distribution through deployment infrastructure.
[Smallstep renewal](https://smallstep.com/docs/step-ca/renewal/) describes automated
certificate renewal. These are implementation references for certificate management,
not additional Loomspan security options or a decision to bundle a CA.

[Docker Swarm PKI](https://docs.docker.com/engine/swarm/how-swarm-mode-works/pki/)
provides a product precedent for simple enrollment followed by managed certificates.
It demonstrates a setup experience, not permission to invent an enrollment protocol.
An installer/orchestrator can supply trusted bootstrap material automatically; no
reviewed mechanism removes the need for initial trust altogether.

TLS certificate lifecycle and JWT signing-key lifecycle are distinct. Certificate
renewal does not supply user roles or define which issuer may assert them. Private
keys, expiry, trust changes and application reload must be covered by the chosen recipe.

## Customer-managed cloud infrastructure

V1 release gates use locally testable evidence only. AWS, Azure and Google Cloud recipes
require separate, explicitly authorized manual validation; those checks do not gate
release. Record outstanding validation per recipe and claim verified cloud compatibility
only after the corresponding manual checks pass.

Certificate management does not add a third security option. Both options can use
enterprise-provided certificate/key/trust inputs.

| Platform | Documented capability | Loomspan evidence still needed |
| --- | --- | --- |
| [AWS Private CA connector](https://docs.aws.amazon.com/privateca/latest/userguide/PcaKubernetes.html) | cert-manager issuance, delivery and renewal | Material loading, reload and peer authorization in the selected recipe. |
| [Google CA Service](https://cloud.google.com/security/products/certificate-authority-service) | Managed private CA and workload integrations | Validated certificate delivery and lifecycle integration. |
| [Azure Key Vault certificates](https://learn.microsoft.com/en-us/azure/key-vault/certificates/about-certificates) | Certificate storage/lifecycle and issuer integration | Validated material delivery; Key Vault is not itself a general private CA. |

Standard Spring SSL bundles and deployment-mounted material may avoid cloud-specific
SDK code. This is a feasible integration boundary, not verified support. Non-exportable
keys require explicit integration and cannot be treated as private-key files.

## Agreed submission ambiguity and callback effects — 2026-10-15

The [roadmap's SDK outcome contract](2026-10-11-java-spring-sdk-roadmap.md#agreed-submission-uncertainty-and-side-effect-semantics--2026-10-15)
distinguishes explicit API rejection, acceptance with an execution ID, acceptance
unknown, polling/wait failure and terminal execution failure. A lost or unusable
submission response may leave work running without an ID known to the SDK. Only claim
definite non-submission with sufficient transport evidence; a proxy error is not itself
proof of API rejection. Preserve reliable IDs and details, and report uncertainty without
recommending blind retry. Exact SDK outcome/error types remain planning work.

V1 performs no automatic execution-submission retries, including SDK HTTP-client and
reference-proxy behavior; document the same requirement for external proxies. Connection
recovery, timeouts and credential/TLS refresh must not replay submissions. No server
deduplication, exactly-once submission or guaranteed lost-ID recovery is introduced.
Safe status reads can use bounded retries within the wait deadline. Once known, execution
IDs remain available through polling errors, timeouts and interruption for resumed lookup;
these events do not resubmit or cancel work. Preserve the existing ambiguity of status 404.

Callback business effects may commit before a response is lost, or earlier callbacks may
complete before a later failure. A failed execution does not imply no side effects or
rollback. Preserve failure details without automatic execution reruns. Prohibit automatic
callback retries by Sidecar's outbound HTTP client and callback-path proxies, including
on timeout, connection recovery or credential/TLS refresh. Configure reference proxies
and document this requirement for customer-managed proxies. Conversion errors also must
not cause re-execution. Safe SDK status-read retries remain a separate policy.

Transport retry prevention does not stop separate repeated skill actions or user
submissions. Applications own duplicate protection for consequential business operations,
using stable operation IDs across relevant repetitions, atomic duplicate detection/result
handling and reconciliation. IDs generated afresh per HTTP attempt do not establish that
protection. Sidecar/SDK supply no invented business identifiers or exactly-once guarantee.

Use deterministic local acceptance fixtures to lose a response after submission acceptance
and prove acceptance unknown without duplicate submissions across client/proxy layers.
Cover malformed acceptance responses, explicit rejections and demonstrable pre-send
failures. Verify ID retention and resumed lookup after polling failures/interruption/wait
expiry, bounded read retry deadlines and no cancellation or resubmission. Record a callback
side effect before losing its response to prove execution failure can coexist with a
completed operation and that SDK reporting/rerun behavior preserves that distinction.
Count received callbacks through the Sidecar client and each reference-proxy path to
prove no automatic replay after response loss; document equivalent customer-proxy checks.
Demonstrate application-owned duplicate protection in a fixture with repeated operation
IDs, distinguishing separate skill actions from transport retries and application guarantees
from SDK guarantees.
Include conversion failures without re-execution. These are planned integration tests,
not new evidence of completed behavior or authorization for real business operations.

## Existing Sidecar baseline

Source baseline: `main`, commit in metadata. These are observations from the research,
not a claim that either complete SDK option is already implemented.

| Component | Observed behavior |
| --- | --- |
| `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:36` | JWT validation through a configured issuer and public verification source, including audience/time/identity checks. |
| `src/main/java/ai/loomspan/sidecar/security/ExecutionOwner.java:7` | Execution owner is issuer plus subject. |
| `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:138` | Result lookup checks that recorded owner. |
| `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:48` | Callback passthrough forwards the original worker JWT. |
| `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java:58` | Outbound clients consume named SSL bundles; automatic renewal/reload is not proven by this construction. |
| `agent-skills/loomspan-sidecar-authoring/references/integration.md` | Documents inbound Boot mTLS and outbound per-target bundles; certificates do not map to execution user identity. |

Existing `JwtSecurityConfigurationTest`, `AuthenticatedExecutionApiIntegrationTest`
and `RestTransportTlsIntegrationTest` cover portions of JWT admission, ownership and
TLS transport. They were inspected, not executed for this documentation work. They
are not evidence of automated pairing, full group enforcement or cloud integration.

The current design lens specifies issuer/subject ownership and unchanged credential
passthrough. Any necessary contract changes require explicit planning and lens updates.
No new Framework SPI or dependency on Framework internals is authorized.

## Agreed local integration test boundary — 2026-10-15

The [roadmap's local test boundary](2026-10-11-java-spring-sdk-roadmap.md#agreed-local-integration-test-boundary--2026-10-15)
defines "provider-free" as **local, deterministic integration tests with no live
external-service dependency**, not the absence of a local identity provider. Routine
tests require no paid model calls, hosted identity services or cloud credentials;
local processes and containers are allowed. Dependency/image acquisition and runtime
prerequisites remain documented setup requirements, not an offline-installation promise.

Use deterministic model fixtures or recorded replay with synthetic business operations,
but real signed JWTs/JWKS, validation, HTTP exchange, TLS/mTLS, and SDK/Sidecar/application/
Caddy integration where applicable. Lightweight protocol fixtures support focused cases;
at least one named, locally runnable authorization server must supply end-to-end Exchange
evidence. Phase 0 selects its version, registration and exchange profile, pins runtime
prerequisites, and defines test commands/tiers. Not every integration test belongs in
every unit-test run; required local release evidence still must pass.

Hosted-provider and cloud verification are separate, explicitly authorized manual checks
outside the local-only release gates.
Claim compatibility only for named provider/version/configuration combinations with
matching evidence. Local protocol fixtures do not prove commercial-provider compatibility,
and mounted local keys do not prove cloud issuance, delivery or renewal. Phase 0 identifies
v1 provider/cloud claims and required manual checks; unrun external checks remain
outstanding for those compatibility claims, not blockers to release.

## Agreed primary transport scenarios — 2026-10-15

The [roadmap's evidence matrix](2026-10-11-java-spring-sdk-roadmap.md#agreed-primary-transport-evidence-matrix--2026-10-15)
uses three primary end-to-end scenario families, each with Relay, Exchange, Spring-issued
and Custom identity:

| Scenario | Invocation | Callback | Coverage |
| --- | --- | --- | --- |
| Protected local HTTP | HTTP | HTTP | Real direct integration inside an enforced local boundary. |
| HTTPS | HTTPS | HTTPS | Server verification in both directions; direct integration and selected reference ingress/proxy coverage where applicable. |
| Two-way mTLS | mTLS | mTLS | Both client-Caddy and SDK-native invocation paths for every strategy, with peer policy in both directions. |

Retain isolated/group mTLS coverage and relevant negative and lifecycle tests; these
families are not merely three successful requests. Mixed transports remain supported
without exhaustive end-to-end testing of every permutation. Focused integration checks
must prove independently selected invocation/callback settings, including mTLS invocation
with protected local HTTP callbacks. Focused no-callback checks must prove invocation
and resumed polling without a callback listener or callback TLS, retaining the agreed
four-strategy/both-mTLS-path coverage. Keep client Caddy and its protected local listener
in the client-proxy case, omitting callback ingress only; prove SDK-native invocation
without client Caddy. Optional JWKS hosting remains a separate choice.

Phase 0 maps primary/focused scenarios to tests and recipes with configuration and evidence
locations. Share justified component evidence, but require real integration evidence for
the promised compositions. Completion requires passing required scenarios and applicable
rejection/lifecycle checks with matching documentation; unrun checks remain outstanding.
These release obligations use local evidence; cloud verification remains separate manual
work outside release gates. This decision does not settle runtime/provider versions or
claim any newly completed interoperability evidence.

## Remaining evidence needed

- Local authorization-server Exchange integration under the agreed test boundary,
  documented runtime prerequisites/test tiers, and separate evidence for each claimed
  hosted-provider or cloud configuration.
- The three primary transport families across all four credential strategies, both
  mTLS invocation paths, and focused mixed-transport/no-callback scenarios defined above.

- Submission ambiguity, retry containment, polling ID retention and callback-side-effect
  scenarios above, including response loss after acceptance and after a committed effect.

- Both callback security integration arrangements and their bearer-only/session-cookie
  isolation, authorization preservation, scoped CSRF and context-cleanup scenarios above.

- The issuer-aware identity/authority and optional local-account resolution scenarios
  above, including multiple roles, issuer collisions and unchanged ownership semantics.

- The v1 bearer-token compatibility scenarios above, including bearer JWTs over mTLS
  and explicit rejection of unsupported sender constraints at admission and callbacks.

- The unchanged-token passthrough protections and acceptance scenarios above, including
  controlled authorized reuse to establish the residual trust boundary explicitly.

- One documented isolated deployment with minimal setup and enforced admission.
- Both required v1 client paths across the two deployment options and four credential
  strategies, including no-callback execution, SDK-native certificate reload and
  protected proxy routes. Verify restart, renewal, expiry recovery, invalid updates
  and connection retirement without replaying ambiguous execution submissions.
- One complete proxy-managed group deployment proving membership and callback
  restrictions in both directions, with distinct workload identities and no inbound
  or outbound proxy bypass; validate the pinned Caddy version.
- SDK invocation and validated callback evidence for all four credential strategies
  under both deployment options, including exchange failure without fallback and
  shared Spring/custom issuance with trusted identity mapping.
- A documented issuer/audience model, especially for independently issuing group
  applications, and signing-key provisioning/rotation separate from Caddy certificates.
- Positive and negative authorization tests, concurrent-user isolation, expiry,
  restart, certificate renewal/reload and membership removal, including existing
  connections and separation of browser/management ingress from workload admission.
  Include invalid JWT signatures/issuers/audiences/claims, queued/callback token expiry,
  permission changes, disabled accounts, logout and callback context cleanup.
- Exact supported software versions, cloud recipes and independent security review.
- Per-direction guarantees for each transport composition. Reserve two-way mTLS
  evidence for authenticated and authorized peers in both directions; mixed HTTPS
  callback evidence must not imply Sidecar workload authentication. No-callback
  scenarios require invocation evidence without a callback listener.

The scope includes independently selected local HTTP, HTTPS and mTLS for invocation
and callbacks, both mTLS client paths and all four SDK credential strategies. Caddy
handles proxy TLS where selected; direct transport does not require a client proxy.
Detailed implementation and compatibility claims remain subject to evidence.
