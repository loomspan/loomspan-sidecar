# SDK v1 identity and callback reference

Reconciled 2026-10-18; all new behavior is planned. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt).
Replace superseded contracts directly; no alternate legacy modes.

## Issuance and identity

The app authenticates its users using its existing login. The SDK offers
Spring-issued and trusted custom identity sources using the same issuer.
Spring captures an authenticated, nonanonymous principal's stable subject and
authorities. A trusted custom source supplies equivalent authenticated identity
for non-Spring applications. Never derive identity or permissions from skill inputs,
model output, request business fields or unverified token claims.

The SDK inside the application reads the authenticated identity and issues the
execution JWT. Core calls receive that JWT in a small immutable credential value.
Spring convenience wiring reads the authenticated user once at operation entry;
the trusted custom source supplies equivalent identity outside Spring.
The value does not expose token bytes through ordinary getters, serialization,
diagnostics or toString and never refreshes itself. No capture manager, thread-local
scopes, context-propagation machinery or additional client-side JWT verification
pipeline is required. There are no async APIs or SDK thread-context wrappers.

Sidecar does not issue another JWT: it validates the incoming execution JWT and
forwards that same token unchanged to callbacks. Callback receivers independently
validate it. Simplifying the client credential value does not change either
receiver's validation responsibilities.

There is one issuer per Sidecar. A trusted group shares a keystore, issuer, callback
audience, user directory and role vocabulary. Multi-key trust permits rotation
within that issuer, not separate app identity domains.

## Execution-token profile

| Field | Contract |
| --- | --- |
| Signature | RSA/RS256 only; required `kid` is the public key's RFC 7638 thumbprint |
| JOSE `typ` | `loomspan-execution+jwt` |
| `token_use` | `loomspan_execution` |
| `iss`, `sub` | Configured exact issuer and nonblank stable subject |
| `aud` | Includes the receiving service's exact audience |
| `iat`, `exp` | Issuance and expiry timestamps; positive issuance lifetime |
| `jti` | Unique issuance identifier |
| `roles` | String array, empty allowed; exact allowlisted user authorities |

Validators require valid signatures, claim types, issuer, own audience, execution
purpose and expiry, using a 60-second validation skew. Use maintained JWT libraries;
do not infer a key source from token-provided URLs. Management/login credentials
cannot substitute for execution credentials.

The SDK defaults Sidecar's audience to `loomspan-sidecar` and the callback audience
to the application's issuer; tokens include both. `loomspan-sdk.issuance.additional-audiences`
adds recipients. Each receiver checks only its own audience; extra audiences are
not rejected by a global audience allowlist.

Lifetime defaults to 15 minutes, accepts any positive configured duration and logs
a startup WARN above one hour. There is no maximum accepted lifetime or admission
freshness rule. Any otherwise valid unexpired token may submit, discover or poll.
See [signing/trust](2026-10-15-sdk-v1-signing-trust-reference.md) for queue/callback
expiry and key-withdrawal boundaries.

## Exact authority names

`loomspan-sdk.issuance.roles` is one allowlist. Include only names the authenticated
user actually has. Preserve names unchanged in application authorities, the
allowlist, JWT roles, Sidecar authorities, skill `rbac_roles` and callback authorities.

For authority `ROLE_ORDER_EDITOR`, every location uses `ROLE_ORDER_EDITOR`.
With Spring's default role prefix:

```java
@PreAuthorize("hasRole('ORDER_EDITOR')")
// Equivalent:
@PreAuthorize("hasAuthority('ROLE_ORDER_EDITOR')")
```

Only the hasRole argument omits the prefix. For an authority named `ORDER_EDITOR`,
use that exact name everywhere and `hasAuthority('ORDER_EDITOR')`.
The SDK does not change application role-prefix settings.

Replace Sidecar's JWT prefix addition and configure Framework role evaluation
through supported public configuration for exact matching. Remove the obsolete
execution prefix option without changing management authorities.

Roles are snapshots. Revocation or account changes do not change a captured token;
shorter issuance lifetimes reduce the exposure window. No live account resolver,
native-principal reconstruction or role translation is part of v1.

## Callback receiving

The optional starter registers one dedicated stateless bearer SecurityFilterChain
at highest precedence, default prefix `/loomspan/callbacks/**`, configurable as one
prefix. Allow GET and POST. No separate server, security manifest, URL-policy DSL
or application-chain integration is provided.

The application must put callback routes under that prefix, avoid competing chains
that intercept it, and enable standard Spring method security. Document and test
this wiring in the reference app. Do not build general analysis of arbitrary Spring
request matchers. Routes outside the prefix are governed by the application's own
security configuration; the SDK cannot promise their protection.

| Request | Behavior |
| --- | --- |
| Missing, multiple or invalid bearer credentials | 401 with WWW-Authenticate: Bearer |
| Query-parameter token | Reject; never authenticate it |
| Valid JWT, denied application authorization | 403 before the denied method executes |
| Cookie/session without bearer token | No authentication fallback |
| Valid JWT without additional method/business checks | Accessible within allowed methods |

The chain never reads/creates sessions, ignores cookies, disables CSRF only within
its prefix, never redirects to login and never falls through to another chain.
Write authentication rejections directly rather than forwarding to `/error`.
Use normal Spring handling for disallowed methods and malformed/firewall-rejected
requests; implementation planning documents the concrete responses.

The SDK independently validates callbacks using the application's signing public
key(s) plus an optional rotation-overlap JWKS. It installs verified issuer/subject
and exact token authorities. Spring evaluates method annotations; business code
owns record-level checks. The SDK does not inspect annotations or compare endpoint
permissions itself. URL rules on the ordinary application chain are not inherited.
Security context is request-scoped and cleared after use.

Caddy handles the hardened recipe's TLS handshakes. The callback chain authenticates
JWTs regardless of transport; network trust does not replace user authorization.

## Execution identity and forwarding

Sidecar captures the token once, associates ownership with `(issuer, subject)`,
and forwards that same token to configured, approved callback recipients.
No token or internal security context enters model/business payloads or ordinary logs.
Do not follow redirects to new recipients.

A fresh token for the same issuer/subject may read a retained owned execution under
current authorization. It does not renew the captured execution token.
A different subject cannot read the owner's execution. Trusted apps can impersonate
within their shared identity domain; this is not isolation between group members.

Existing management accounts, tokens and authorization remain unchanged.
The SDK has no management-authoring API. Keep ordinary credential-separation
regression coverage without adding a management redesign.
