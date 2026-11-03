# Python SDK with Java SDK parity

Date: 2026-11-02. Status: design proposal for future planning; no Python SDK is
implemented or published by this document. API names and examples below are
proposed, not supported interfaces.

**Development policy: replace superseded contracts, code and schemas directly.
Do not introduce compatibility shims, legacy adapters, dual APIs or migration
machinery solely to preserve obsolete behavior.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). Choose the simplest
complete implementation and justify additional dependencies and abstractions by
their concrete benefit. No development-data reset is required by this proposal;
later adoption must preserve existing credentials, operational data and reference
packs. Planning does not authorize deleting them.

## Purpose and usability outcome

A Python application should be able to discover and invoke Sidecar skills, issue
execution credentials from its authenticated users, and receive authenticated
callbacks without implementing the Loomspan security protocol itself. Match the
Java core SDK and Spring starter's observable capabilities, using Python and
FastAPI conventions rather than reproducing Java class structure.

The developer should supply connection/signing settings, a trusted identity
adapter and ordinary business authorization. The SDK should own HTTP protocol
handling, credential construction, callback authentication and resource lifetime.
Adding an HTTP skill remains a YAML authoring task. Adding a callback should
require a protected route and business checks, not custom JWT decoding.

This is a client SDK, not a Python Framework implementation, management client,
skill executor plugin or replacement identity provider. It must have no runtime
dependency on Framework/Sidecar implementation code or repository checkouts.

## What the integration work established

The neighboring `loomspan-sidecar-test-suite` is a concrete consumer and a useful
acceptance environment, not the definition of the public product contract.

- Its old Python integration forwarded Keycloak login tokens. Current Sidecar
  requires dedicated execution credentials. Adapting that existing security
  contract required custom issuance and callback validation; executor YAML itself
  did not create the requirement.
- `apps/python/infrastructure/execution_identity.py` now implements signing,
  allowlisted roles, source-expiry bounding and callback verification. This is
  precisely the application-owned protocol code a supported SDK should replace.
- The nine equipment operations use whole-input request bodies: eight flat lookup
  inputs and one nested approval envelope. Owned SDK callbacks can be bodyless.
  Authentication must not read, wrap, consume or manufacture business bodies.
- Caller identity and original execution-token fidelity, role/record denial,
  expiry, trust changes and no automatic replay were exercised in local services.
  HTTPS fixtures needed trust configured separately for each actual TLS client.
- Qualified names such as `default-resolveEquipment` occur in actual public
  descriptors, planner identities and diagnostics. Clients must preserve server
  identities instead of stripping prefixes or teaching transport code business
  naming rules.
- The completed evidence includes 30 SDK scenarios and 8 equipment plus 224
  TypeSafe executions. Providers were disabled; model responses were deterministic
  fixtures. This establishes useful interoperability evidence, not live-model
  quality or proof that a future Python SDK is already correct.

The suite helper is deliberately narrower than the Java SDK: fixed issuer and
audience, a fixed key identifier, one key-file arrangement, integer timestamps,
and request-time JWKS reads. Do not promote it unchanged into a public library.
For example, Java issues a unique `jti`, uses an RSA thumbprint `kid`, supports
colocated/provided signing, and has an explicit callback overlap-trust lifecycle.

## Parity scope and ownership

| Capability | Python target | Application responsibility |
| --- | --- | --- |
| Discovery | List/get public skill descriptors and preserve schema strings | Select skills and interpret business contracts |
| Execution | Submit, read and wait; preserve results, failures and diagnostics | Decide what to do with business outcomes |
| Credentials | Opaque credential snapshot; explicit per-call credential | Supply trusted identity or an already-issued token |
| Issuance | Java-compatible execution JWT profile and exact role intersection | Authenticate users and select the authority allowlist |
| Signing | Explicit colocated/provided modes and public JWKS export | Provision files, directories, permissions and distribution |
| Callback authentication | FastAPI/ASGI protection, verified principal and trust updates | Authorize operations and records |
| TLS | Standard Python SSL context; verified certificates/hostnames | Provision trust and optional client identity at the correct hop |
| Lifecycle | Context-managed clients and application-managed security startup/shutdown | Integrate with application lifespan and deployment topology |

Recommend one independently versioned distribution under `sdks/python`, with a
core import surface and optional FastAPI integration extra. Core must import and
run without FastAPI, Starlette or an ASGI server. A sync client gives ordinary
Python users Java-equivalent blocking behavior; an async client is required for
usable FastAPI integration. Share protocol validation and result/error types;
do not run a private event loop inside the synchronous API.

The suite already uses HTTPX, PyJWT and cryptography. They are implementation
candidates, not decisions that this document establishes as compatible versions.
Implementation planning must verify supported Python/library versions, packaging,
transport retry behavior and cryptographic/keystore capabilities. Declare direct
dependencies explicitly rather than relying on transitive availability.

## Client contract

Proposed operations are `list_skills`, `get_skill`, `submit`, `get_execution` and
`wait_for_completion`. Offer sync/async variants with equivalent outcomes and
typed immutable response objects. Preserve public optional fields and tolerate
additive fields while rejecting malformed required envelopes.

Match the Java defaults: 2-second connection timeout, 10-second response timeout,
8 MiB response bound, and a per-wait 60-second total budget with 250 ms polling.
Validate positive finite limits. A response deadline covers body consumption;
waiting bounds each read by the remaining total budget. Bound streamed and
decompressed response data rather than trusting Content-Length.

Require an absolute HTTP(S) base URL without userinfo/query/fragment and preserve
its base path. Encode accepted skill names as one path segment; reject blank,
control, slash, backslash, percent and dot-segment names consistently with Java.
Build status URLs from the configured origin and accepted UUID, never `Location`.
Validate status IDs against the requested execution ID.

Submission accepts a JSON object and returns an acceptance UUID. It does not
validate business schemas or wait implicitly. Preserve result text exactly after
JSON envelope decoding: null, empty, Unicode, non-JSON and malformed-JSON-looking
strings remain distinct. A server `FAILED` status is a terminal snapshot with the
original failure, not automatically a client exception. Available events and
configuration identity are optional; do not invent missing diagnostics.

| Condition | Proposed Python outcome |
| --- | --- |
| Invalid input/credential or closed client before send | `LocalFailure`; no request |
| HTTP error response | `HttpFailure`, status and available public problem details |
| Lost/unusable submission response after possible send | `AcceptanceUnknown`; never infer an ID or resubmit |
| Malformed/mismatched/oversized status response | `ProtocolFailure` |
| Wait deadline ends | `WaitTerminated`, execution ID and last valid snapshot |
| Server execution fails | Normal terminal snapshot containing original failure |

Every HTTP operation gets one attempt. Disable transport retries, redirects,
authentication rechallenge replay and cookie persistence. Do not attach user
Authorization to shared client defaults. Concurrent users must have isolated
request credentials. HTTP failure does not prove that work had no effect.

Polling repeats only successful nonterminal reads and stops on the first failed
read. Neither stopping a wait nor closing the client cancels server work. For
async callers, preserve normal cancellation propagation; cancellation of submit
after sending can leave acceptance unknown. Document that explicitly and do not
swallow cancellation or automatically repeat the operation. Planning should
settle how a cancelled wait exposes its last snapshot without violating Python
cancellation conventions.

Use normal certificate and hostname validation; no insecure convenience switch.
Allow explicit standard SSL contexts for private CA trust and client identity
where the SDK is the TLS client. This does not add TLS client identity to the
Sidecar HTTP executor. Keep invocation and callback hops separately configurable.

## Execution credentials and issuance

`CredentialSnapshot` accepts an already-issued transport-safe credential without
claiming to verify or refresh it. Reject empty, whitespace/control or non-ASCII
transport values. Hide token bytes in repr, str, serialization, exceptions and
default logging. Avoid a normal public raw-token getter; internal transport access
must not expose credentials through business objects. Python cannot make a secret
inaccessible to application introspection; document that limitation honestly.

`AuthenticatedIdentity` represents identity already verified by application
authentication. Accept a nonblank stable subject and a copied collection of exact
authority names. Never instantiate it by deserializing model input or request JSON.
FastAPI has no universal current-principal convention: accept an explicit trusted
dependency/adapter instead of guessing claims from arbitrary headers or sessions.
Capture it once at operation entry and pass issued credentials explicitly across
tasks. Do not introduce mutable global user state or implicit refresh.

Match Java's issuance profile:

- RS256; RFC 7638 RSA public-key thumbprint `kid`;
  `typ=loomspan-execution+jwt`; `token_use=loomspan_execution`.
- Configured `iss`, stable `sub`, `iat`, `exp`, unique `jti` and string-array
  `roles`. Preserve valid NumericDate precision and reject timestamp overflow.
- Default issuer `loomspan-app`, lifetime 15 minutes, audiences containing
  `loomspan-sidecar` plus the issuer and deduplicated additional audiences.
- Roles are the exact intersection of verified authorities and the configured
  allowlist, deduplicated; an empty list is valid. Never add or strip `ROLE_`.
- Positive lifetime with a warning above one hour, matching Java's current
  behavior. Roles are snapshots, not live authorization-account lookups.

### Source-login expiry is an explicit design decision

The suite caps issuance to `min(login expiry, now + 600 seconds)`. Java's current
`AuthenticatedIdentity` contains only subject/authorities, and its issuer uses
configured lifetime; the Spring adapter does not impose a login-expiry cap.
Do not incorrectly describe the suite policy as existing Java parity.

Recommend an optional trusted `expires_at` on the Python identity adapter, with
issuance capped to that bound when supplied and expired identities rejected.
Without it, use Java-equivalent configured lifetime. This is a proposed extension
requiring an explicit planning decision and consideration of a matching Java API;
do not silently change Java or weaken the suite's existing cap. For session-based
authentication with no meaningful expiry, do not invent one.

## Signing keys and public trust

Require an explicit signing mode. No fallback-generated keys when provided
material is missing or invalid.

**Colocated:** generate a fresh RSA key of at least 2048 bits in process memory
at startup; atomically publish only its public JWKS into an existing directory.
Restart changes the signing identity. One process owns the export path. FastAPI
multiworker deployments cannot each run colocated signing against one file;
use provided shared identity for multiple workers/replicas. Do not silently turn
colocated mode into persistent private-key storage.

**Provided:** load operator-owned RSA material once at startup, validate strength,
format and certificate/key correspondence, and atomically export public JWKS.
Java supports PKCS12, JKS and unencrypted PKCS#8 PEM. Full format parity includes
all three; implementation planning must assess JKS support/dependency cost. If a
release defers JKS, call it a documented parity gap requiring an explicit scope
decision, not complete parity. Do not add custom keystore parsing casually.

Match Java's secret-reference discipline: passwords through `env:NAME` or
`file:/absolute/path`, never literal configuration values; remove only one terminal
newline from secret-file content. Reject inappropriate format-specific settings,
missing aliases and output paths overlapping key/certificate/secret inputs.
Export parents must already exist. Atomic export failure aborts startup; there
is no non-atomic fallback. Private keys never enter JWKS, diagnostics or bundles.
Provided-key changes take effect by restart. Certificate expiry warnings for
signing material must not imply that TLS certificate validation is disabled.

Callback trust is distinct from Sidecar's trust configuration. Match Java's own
startup public key plus optional local overlap-JWKS file, loaded at startup,
polled every second, and refreshed on unknown `kid` at most once per second.
Validate complete public RSA key sets before atomic replacement. A configured
missing/unreadable/malformed/empty overlap file withdraws all callback trust,
including the own key, until valid trust returns. No stale-key fallback or private
signer reload. Withdrawal affects new authentication, not already admitted work.

This is deliberately different from copying the suite's request-by-request file
read verbatim. Keep file I/O and cryptographic startup work off the async request
event loop, and manage background trust-refresh work through application lifespan.

## FastAPI callback security

Provide a small explicit installation API that protects an entire configured
callback prefix, plus a typed dependency exposing the verified principal to
handlers. Default prefix: `/loomspan/callbacks`, including its descendants but
not similarly named siblings. A bare per-handler dependency is insufficient as
the only guard: forgetting it on a new route must not expose the callback area.

Recommended shape: prefix-scoped ASGI authentication enforcement with an
`APIRouter` integration and a request-scoped principal dependency. Compose with
the application's existing lifespan; do not replace its startup/shutdown hooks.
Security initialization failure must fail application startup. Document middleware
ordering and mounted-app/root-path behavior and test the protected routing boundary.

Match Java's externally visible security rules:

- Require exactly one bearer Authorization header. Inspect raw ASGI headers so
  framework header merging cannot hide duplicates. Reject `access_token` query
  parameters, including encoded parameter names. Never read form bodies for auth.
- Cookies and login sessions cannot authenticate callbacks. Return 401 and
  `WWW-Authenticate: Bearer` for absent/invalid credentials, without login redirects.
- Verify algorithm, execution type/purpose, nonblank issuer/subject/key ID, valid
  roles and audience shapes, trusted signature and timestamps. Use the issuance
  issuer, with audience defaulting to that issuer and an explicit override.
- Match Java's 60-second callback timestamp skew; Sidecar still enforces strict
  credential expiry before forwarding. Source-expiry bounding is a separate policy.
- GET and POST are allowed after authentication. Authenticated other methods return
  403, including automatic HEAD/OPTIONS behavior unless an explicit future product
  decision changes both contracts. Without authentication they return 401.
- Reject ambiguous path forms so prefix matching cannot be bypassed by encoding,
  semicolon parameters or normalization differences. Document any ASGI-server
  syntax rejection that happens before middleware instead of promising identical
  servlet behavior. Test TRACE and malformed paths explicitly.
- Preserve request bodies and business headers. Bodyless POST stays bodyless;
  whole-input and selected-field HTTP operations need no security wrapper.

The principal exposes verified issuer, subject and exact authorities, not a bearer
token in its normal serialization. Application authorization produces 403 and
checks record ownership by issuer plus subject. No SDK rule can infer ownership
or substitute for business approval. Any roles helper should be a thin explicit
exact-membership check, not a new policy language.

Keep browser login, session handling and CSRF protection outside this callback
boundary under application control. Callback isolation must not disable CSRF or
change authentication behavior on unrelated routes. Clear request-local identity
after each request, including errors and cancellations.

## Proposed developer experience

Illustrative API sketch only; names and installation mechanics need implementation
planning. Settings and resources are initialized once by application lifespan.

```python
# public_user is supplied by the app's existing verified login dependency.
async def start_assessment(body, public_user, sdk):
    identity = AuthenticatedIdentity(
        subject=public_user.subject,
        authorities=public_user.authorities,
        expires_at=public_user.expires_at,  # proposed optional extension
    )
    credential = sdk.issuer.issue(identity)
    return await sdk.client.submit("assessEquipment", body, credential)

# callbacks belongs to the prefix protected when SDK security is installed.
@callbacks.post("/equipment/{asset_id}")
async def equipment_callback(
    asset_id: str,
    principal=Depends(sdk.callback_principal),
):
    require_authority(principal, "ASSESS_EQUIPMENT")
    equipment.require_owner(asset_id, principal.issuer, principal.subject)
    return equipment.lookup(asset_id)
```

The real quickstart must include imports, lifespan and configuration, a protected
router and a runnable local example. The SDK must not guess Keycloak claim names.
Provide a short application adapter example and explain exact role mapping at the
login boundary. Secrets must not appear in generated OpenAPI examples or errors.

Usability acceptance: a developer can replace the suite's custom JWT implementation
with SDK configuration and a small trusted identity adapter; ordinary submit/poll
code and business callbacks do not manipulate token claims, files or signatures.

## Adoption in the maintained suite

Replace `execution_identity.py` protocol logic with SDK issuance and the protected
FastAPI callback integration. Keep Keycloak authentication and equipment business
authorization in the application. Replace raw `httpx` Sidecar transport with the
async client while preserving public application response behavior deliberately.
Retain source-expiry policy through the explicitly chosen adapter contract.

Use provided signing to retain the suite's existing operator-owned test identity;
do not regenerate persisted credentials as an incidental SDK installation action.
Move key creation/export responsibilities only where ownership is explicit.
Do not hide issuer changes with record relabeling or compatibility adapters.

Preserve all equipment inputs, approval envelopes, model declarations, protected
original reference packs and accepted results. Reuse the established namespaced
contract unchanged unless a separately justified public-contract change requires
review. Add no client-specific Sidecar behavior and no new Framework extension.

## Verification and delivery outline

Create separate dated implementation tickets through Sidecar's pipeline after
settling the decisions below. Each unit owns its tests and documentation.

1. **Core transport and public models:** sync/async clients, credentials, errors,
   bounded reads, lifecycle and plain-Python examples. Test exact result fidelity,
   malformed envelopes, IDs/paths, concurrent users, timeout/cancellation and one
   attempt on lost submission responses. Core import tests exclude FastAPI.
2. **Signing and issuance:** profile/default parity, role intersection, key formats,
   atomic public exports and failures. Cross-language tests verify Python-issued
   tokens with actual Java/Sidecar validators and Java-issued tokens with Python;
   do not rely solely on a shared mock or one implementation verifying itself.
3. **FastAPI security:** prefix-wide protection and lifespan/trust integration.
   Test duplicate headers, query tokens, wrong type/issuer/audience/key, timestamps,
   malformed claim types (including Python bool versus NumericDate), methods,
   path normalization, browser separation, exact roles and request isolation.
   Exercise valid/invalid/removed/restored overlap trust and worker topology.
4. **Maintained consumer and composed proof:** replace the suite's custom protocol
   code and run representative plain Python/FastAPI, HTTPS and Caddy compositions.
   Cover all nine equipment bodies, bodyless callbacks, token fidelity, denial,
   expiry, shared-trust rotation and loss-response counters. Run deterministic
   replay against preserved inputs/results and verify installed-wheel examples.

Organize verification to avoid the repeated-build cost observed in 9.3. Prove
configuration and fixture contracts with focused tests first. Build unchanged
components once per source state where repository workflow allows, then reuse
those artifacts across isolated runtime scenarios. A changed runtime component
invalidates its affected evidence. Preserve full final required checks; do not
disable scenarios or weaken assertions for speed, and do not add a separate
Framework source/artifact provenance system.

Release acceptance requires documented parity for every matrix row, Python/Java
interoperability, a working FastAPI example, no tokens/private keys in output, and
a fresh independent review. Existing 9.3 passes are reference evidence, not passes
for this unimplemented SDK. Default verification uses local fixtures with provider
access disabled. Paid calls, publication and operational data changes need their
own explicit scope; this writeup authorizes none of them.

## Decisions to settle during implementation planning

- Package/import names, independent version and supported Python/FastAPI versions.
- Optional trusted source-expiry bound, including whether to add matching Java
  support; preserve the suite's current bound in any adoption.
- Complete JKS format parity versus an explicitly approved initial limitation.
- Exact sync/async cancellation surface and safe exposure of last wait state.
- FastAPI installation API and routing/middleware order, proven against mounted
  apps and actual server behavior rather than assumed from Spring semantics.

These are bounded design decisions. They do not justify a general plugin system,
identity-provider abstraction, automatic token refresh, certificate manager,
business authorization engine or compatibility layer.

## Source anchors

- [Java SDK contract and examples](../../../sdks/java/README.md).
- [Java issuer](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionTokenIssuer.java)
  and [trusted identity](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/AuthenticatedIdentity.java).
- [Spring identity adapter](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SpringExecutionTokenIssuer.java),
  [callback security](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackSecurityAutoConfiguration.java)
  and [JWT validation](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackJwtDecoder.java).
- [HTTP executor design](http-skill-executor-design-notes.md) and
  [9.3 integration evidence](../verification/2026-11-02-pr-9-3-http-executor-integration-and-completion.md).
- Neighboring suite: `apps/python/infrastructure/execution_identity.py`,
  `sidecar_client.py`, `scripts/prepare.py`, `scripts/sdk_lane/`,
  `tests/test_execution_identity.py`, and `tests/test_replay.py` under
  `C:/opendev/code/loomspan-sidecar-test-suite`.

Source assessment is based on the current local checkout and the completed local
integration work. Recheck Java behavior when planning implementation; this document
must not silently freeze an obsolete SDK contract.
