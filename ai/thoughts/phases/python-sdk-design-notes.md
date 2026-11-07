# Python SDK with Java SDK parity

Date: 2026-11-02. Updated: 2026-11-07. Status: design baseline for implementation
planning; no Python SDK is implemented or published by this document. Python API
names and examples remain illustrative until implementation planning.

**Development policy: replace superseded contracts, code and schemas directly.
Do not introduce compatibility shims, legacy adapters, dual APIs or migration
machinery solely to preserve obsolete behavior.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). Choose the simplest
complete implementation and justify additional dependencies and abstractions by
their concrete benefit. No development-data reset is required by this proposal;
later adoption must preserve existing credentials, operational data and reference
packs. Planning does not authorize deleting them.

## Governing baseline and current handoff

**Use current Java SDK observable behavior and defaults as the Python baseline
unless a concrete reason justifies a difference.** This is the developer's
2026-11-07 direction. Use Python conventions for APIs and lifecycle; do not reopen
settled protocol, security or timeout choices merely because the language changes.
Any proposed behavioral divergence must identify the Java behavior, the concrete
reason, the consumer impact, and whether both SDKs should change. A dependency's
defaults are not justification for silently weakening the contract.

The [shared PEM design](sdk-pem-signing-design-notes.md) remains authoritative for
signing. Its earlier implementation-pending status and the old Python handoff are
historical: the current checkout includes Java PEM signing (`3571bb8`), submission
uncertainty (`487f26e`) and callback trust startup/diagnostics (`6a790f9`). These are
now the baseline, not three outstanding Java prerequisites.

This refresh inspected Java source, README and focused test assertions on branch
`main` at `6a790f91a10aaf5e50e41fbfe42dfb72eb98a067`. It is a source/test review,
not a fresh execution of tests or certification of Python interoperability. Existing
working-tree deletions of older tickets/research/plans/reviews were left untouched;
this note uses surviving source/test links rather than those removed artifacts.

### Settled scope

- The primary v1 audience is the general Python/FastAPI developer. Provide sync
  and async clients with equivalent observable outcomes; lead examples with async
  FastAPI. Core works without a web framework or a private signing key when given
  an already-issued credential. Java core already supports that separation.
- Retain Java's independent execution-credential lifetime and **15-minute default**,
  configurable to a positive representable duration, with a warning above one hour
  and no policy maximum. Under the parity direction, this replaces the earlier
  provisional-default question; it does not claim separate earlier approval of
  that number. No source-login expiry cap or mandatory lifetime setting is added.
  Applications must budget for queueing and later callbacks/status requests; no
  fixed default promises to cover arbitrary workflows.
- Retain explicit colocated/provided modes, ephemeral colocated keys and strict
  unencrypted PKCS#8 RSA provided keys. No JKS/PKCS12, encrypted PEM, signing
  certificates, automatic conversion, private-key persistence or rotation manager.
- Operators own rotation and public overlap trust. Retain own-key-plus-optional-
  overlap callback trust: invalid configured initial overlap fails startup;
  runtime invalidity withdraws all trust until recovery with safe transition logs.
- Authentication belongs to the application. Capture its trusted subject and exact
  authorities once; issuance intersects authorities with an allowlist. No original
  login token, provider-specific claims, account lookup or session reconstruction
  is needed. Callbacks authenticate independently; applications authorize records.
- Expiry governs new callback admission, not cancellation of admitted business code.
  The 60-second callback validation skew is separate from Sidecar's strict expiry
  check before forwarding. No automatic refresh, replay or execution-scoped grant.
- Colocated restarts may disrupt in-flight interaction. Do not add restart detection,
  recovery, draining or cancellation coordination. Sidecar work/effects may continue.
- Applications sharing a Sidecar remain a fully trusted group with one issuer and
  common identity/role semantics. Multiple keys or namespaces provide no new
  application isolation. The earlier topology question is closed for this scope.
- The maintained suite is an early customer, not the reference design. Its incidental
  source-expiry cap, fixed key IDs and request-time trust reads do not define parity.
  Java starter client-only activation remains deferred without a demonstrated need.

The FastAPI integration, cancellation/wait-context, and packaging/support decisions
were agreed on 2026-11-07 and are recorded below. No product-design question
currently blocks implementation tickets. Remaining feasibility work belongs in
the tickets' Full Pipeline research and planning, before committing implementation
details; see [Ticket readiness](#ticket-readiness). Do not build Python as
incidental follow-up work to the completed Java changes.

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

## V1 completeness and simplicity review (2026-11-07)

V1 retains all agreed Java-equivalent capabilities: discovery, submission/status/
waiting, sync and async clients, opaque credentials, issuance in both signing modes,
public export, callback authentication and reload, verified TLS, typed results and
safe errors. Simplicity does not mean dropping sync support, overlap trust, required
security checks, diagnostics fidelity or the agreed platform targets.

Keep the implementation proportional:

- Share protocol/model validation between sync and async clients. Use internal
  helpers where useful, not a public transport plugin system or extensible backend
  registry. Select dependencies through the bounded feasibility work below.
- Use ordinary typed Python configuration and response objects. Do not introduce
  SDK environment/YAML loaders, Spring-style configuration binding, generated
  business models or business-schema validation. Applications can use their own
  settings/Pydantic models. Unsupported constructor/configuration fields can fail
  through ordinary strict validation; Python needs no legacy-settings detector.
- Keep FastAPI integration to the agreed lifecycle, middleware and dependencies.
  Use normal application authorization checks; `require_authority` in the sketch
  is application code, not a promised SDK authorization subsystem. Reuse core
  issuance/client logic rather than maintaining a second FastAPI protocol path.
- No `WaitState`, execution handles, hidden background polling, automatic refresh,
  resubmission, restart recovery, key/certificate management or worker coordinator.
  Only explicitly owned client resources and callback trust refresh need lifecycle
  management. Do not add caches, health endpoints or telemetry frameworks without
  a demonstrated requirement.
- Preserve precision and strict input handling with standard/library facilities.
  If parity would require substantial custom time types, parsers or transport
  machinery, bring back the concrete tradeoff before building it. Do not silently
  weaken the contract or assume complexity is justified merely by literal Java
  implementation similarity.
- Test each required behavior at the smallest useful boundary, then prove the
  representative composed flows. Reuse synthetic fixtures; do not multiply every
  security case across every OS, dependency version, signing mode and deployment.
  Keep the agreed platform/version coverage and fresh cross-language evidence.

The remaining engineering risks are total HTTP deadlines, precise temporal/PEM
interoperability and ASGI boundary/lifecycle correctness. These belong in focused
research/planning within the four tickets, not new product subsystems. No capability
was dropped or new product decision opened by this review.

## What the integration work established

### Current Java source and test review (2026-11-07)

The following is the implementation baseline. Test references identify assertions
read during this review; they are not new test-run results. Python must supply its
own tests and composed Sidecar evidence.

| Area | Current Java behavior to preserve | Source and executable evidence |
| --- | --- | --- |
| Client and outcomes | List/get skills, submit, status and wait; 2 s connect, 10 s total response, 8 MiB body limit; 60 s wait with 250 ms polling. Exact result strings and normal terminal FAILED snapshots. | [SidecarClient](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/SidecarClient.java), [WaitOptions](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/WaitOptions.java), [client edge tests](../../../sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientEdgeTest.java) |
| Submission and wait errors | Pre-dispatch interruption is local failure; possible-dispatch loss, interruption or unusable success body is acceptance-unknown. Observed non-2xx status survives an unusable body. No inferred ID or replay. Status/wait failures carry requested ID and last valid snapshot where available. | `SidecarClient.request`, `responseFailure`, `await`; edge tests `oversizedSubmissionResponsesLeaveAcceptanceUnknown`, `interruptionBeforeSubmissionDispatchFailsLocally`, `interruptionAfterSubmissionDispatchLeavesAcceptanceUnknownAndReleasesConnections`, `httpErrorsKeepStatusWithoutUnsafeProblemDetails`, `statusOverflowPreservesWaitContext` |
| Identity and issuance | Explicit credential per call; application-selected subject; exact allowlisted roles; independent 15-minute default, fractional timestamps, unique jti, issuer/audience defaults. | [IssuanceSettings](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/IssuanceSettings.java), [issuer](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionTokenIssuer.java), [issuer tests](../../../sdks/java/core/src/test/java/ai/loomspan/sdk/ExecutionTokenIssuerTest.java), [Spring adapter](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SpringExecutionTokenIssuer.java) |
| Signing | Explicit modes; strict PEM-only provided signing; atomic public-only export, stable provided identity, ephemeral colocated identity, no fallback or input/output overlap. | [SigningKeys](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeys.java), [SigningSettings](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/SigningSettings.java), [signing tests](../../../sdks/java/core/src/test/java/ai/loomspan/sdk/SigningKeysTest.java), [configuration tests](../../../sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfigurationTest.java) |
| Callback trust lifecycle | Own key plus optional complete validated overlap; initial invalidity fails before polling starts; runtime loss withdraws own and overlap keys; valid replacement restores trust; category transitions log safely without repeated polling noise. | [CallbackJwtTrust](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackJwtTrust.java), [trust tests](../../../sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/CallbackJwtTrustTest.java), [security integration tests](../../../sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/CallbackSecurityAutoConfigurationTest.java) |
| Callback authentication | Whole-prefix stateless bearer protection, exact issuer/roles, receiver audience, 60 s timestamp skew, no session authentication or body consumption; application authorization remains separate. | [security configuration](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackSecurityAutoConfiguration.java), [decoder](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackJwtDecoder.java), [decoder tests](../../../sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/CallbackJwtDecoderTest.java) |

Important translation details from the source review:

- Java disables retries, redirects, cookie management, authentication rechallenge
  and automatic content compression. Its JSON reader rejects duplicate fields and
  trailing JSON tokens. Python must not accidentally enable these behaviors through
  transport/parser defaults. Preserve additive envelope fields and exact business
  result strings without treating a result string as another JSON document.
- Error problem details are optional: Java exposes only a complete bounded JSON
  object, redacting the submitted credential from keys and values. Invalid or
  oversized error bodies do not replace a known HTTP status or leak raw parser/body
  diagnostics. Business result/failure/event contents are not generally sanitized.
- Issuance and callback acceptance are different contracts. Java issues `iat` and
  unique `jti`; the callback decoder requires `exp` but accepts absent `iat`, `nbf`
  and `jti`. If present, time claims must be numeric and representable, and `jti`
  must be a string. Audience may be a matching string or a string array. Do not
  tighten Python validation simply by requiring every claim the issuer emits.
- Java preserves fractional NumericDate values up to nanosecond resolution and
  tests a one-nanosecond issuance lifetime. Python time/model representation must
  be checked against those fixtures; integer-second coercion is not parity.
- PEM parity includes complete input consumption, line endings, Base64 boundaries,
  valid optional PKCS#8 attributes and a matching optional embedded public key.
  Reuse the Java synthetic fixtures and rejection cases rather than assuming a
  successful permissive library load proves the full input contract.
- Java callback tests cover runtime trust withdrawal while an already-admitted
  request finishes with its original identity. This does not replace Python tests
  for that behavior or separately prove every expiry-during-callback scenario.
- Spring-specific bean activation, filter chains and thread interrupt flags are not
  Python APIs. Explicit FastAPI lifespan and normal Python cancellation are the
  established adaptations; preserve authentication boundaries and error context.
  Java's servlet firewall can reject malformed paths/TRACE before authentication.
  Document actual ASGI/server rejection behavior rather than promising identical
  container-specific status codes.

Earlier Java follow-ups for submission uncertainty and callback startup are
implemented in this checkout. Explicit-subject documentation also exists. No new
Java change was identified as necessary to proceed with Python planning.

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

Use one independently versioned distribution under `sdks/python`, with a
core import surface and optional FastAPI integration extra. Core must import and
run without FastAPI, Starlette or an ASGI server. A sync client gives ordinary
Python users Java-equivalent blocking behavior; an async client is required for
usable FastAPI integration. Share protocol validation and result/error types;
do not run a private event loop inside the synchronous API.

The suite already uses HTTPX, PyJWT and cryptography. They are implementation
candidates, not decisions that this document establishes as compatible versions.
Implementation planning must verify supported Python/library versions, packaging,
transport retry behavior and strict PEM/cryptographic capabilities. Declare direct
dependencies explicitly rather than relying on transitive availability.

## Client contract

Proposed operations are `list_skills`, `get_skill`, `submit`, `get_execution` and
`wait_for_completion`. Offer sync/async variants with equivalent outcomes and
typed immutable response objects. Preserve public optional fields and tolerate
additive fields while rejecting malformed required envelopes, duplicate JSON
fields and trailing JSON tokens.

Carry the complete Java public model: descriptor name/description/kind and schema
strings; execution ID, skill name, status, creation/completion timestamps, result,
primary failure with issues, optional configuration ID and optional events with
timestamp/level/type/details/frame ID/route. Preserve absent event history versus
an empty list. Terminal snapshots require completion time, and FAILED requires its
primary failure. Copy mutable nested data at boundaries so consumers cannot mutate
retained snapshots or problem details. This needs small typed models and defensive
copies, not a general immutable-JSON library.

Match the Java defaults: 2-second connection timeout, 10-second response timeout,
8 MiB response bound, and a per-wait 60-second total budget with 250 ms polling.
Validate positive finite limits. A response deadline covers body consumption;
waiting bounds each read by the remaining total budget. Bound streamed response
bytes rather than trusting Content-Length. Match Java by disabling automatic
compression negotiation/decompression; do not introduce
compressed-response support as an incidental transport-library behavior.

Require an absolute HTTP(S) base URL without userinfo/query/fragment and preserve
its base path. Encode accepted skill names as one path segment; reject blank,
control, slash, backslash, percent and dot-segment names consistently with Java.
Build status URLs from the configured origin and accepted UUID, never `Location`.
Validate status IDs against the requested execution ID.

Submission accepts a JSON object and returns an acceptance UUID from a complete,
valid HTTP 202 body. Reads require HTTP 200, matching Java. It does not
validate business schemas or wait implicitly. Preserve result text exactly after
JSON envelope decoding: null, empty, Unicode, non-JSON and malformed-JSON-looking
strings remain distinct. A server `FAILED` status is a terminal snapshot with the
original failure, not automatically a client exception. Available events and
configuration identity are optional; do not invent missing diagnostics.

| Condition | Proposed Python outcome |
| --- | --- |
| Invalid input/credential or closed client before send | `LocalFailure`; no request |
| HTTP error response | `HttpFailure`, observed status even with an oversized/unusable body; optional bounded, credential-redacted problem object |
| Lost/unusable submission response after possible send | `AcceptanceUnknown`; never infer an ID or resubmit |
| Malformed/mismatched/oversized successful discovery or status response | `ProtocolFailure`; retain requested execution ID for status reads |
| Wait deadline ends | `WaitTerminated`, execution ID and last valid snapshot |
| Server execution fails | Normal terminal snapshot containing original failure |

Every HTTP operation gets one attempt. Disable transport retries, redirects,
authentication rechallenge replay and cookie persistence. Do not attach user
Authorization to shared client defaults. Concurrent users must have isolated
request credentials. HTTP failure does not prove that work had no effect.

Clients own their connection resources. Sync `close` and async `aclose` (or their
context-manager exits) release resources idempotently and reject subsequent calls.
Release incomplete responses after failure/cancellation; do not drain an unbounded
body for connection reuse. Ordinary SDK exception messages and displayed exception
chains must not expose raw transport credentials, private material or parser input.
Structured business details remain available deliberately; arbitrary introspection
or third-party wire logging is not something the SDK can make secret-safe.

Polling repeats only successful nonterminal reads and stops on the first failed
read. Neither stopping a wait nor closing the client cancels server work.

### Agreed cancellation and wait context (2026-11-07)

Keep the Java-style context on SDK exceptions and let native Python cancellation
propagate normally. Do not introduce a `WaitState` object, cancellation-specific
state holder, background polling handle or recovery mechanism.

- SDK wait-budget expiry raises `WaitTerminated` with the execution ID and last
  valid snapshot, or no snapshot if no status read succeeded.
- SDK HTTP/protocol failures during waiting retain their error category, requested
  execution ID and last valid snapshot. A standalone status-read failure retains
  the requested ID; there is no earlier snapshot to invent.
- External async cancellation propagates as the runtime's native cancellation
  exception after local resource cleanup. Do not wrap it in an SDK exception,
  attach snapshot state to it, swallow it or translate it into an SDK wait timeout.
  An enclosing application timeout may apply its own normal exception semantics.
- Synchronous `KeyboardInterrupt` likewise propagates normally after cleanup;
  do not emulate Java thread interruption with a new Python cancellation API.
- Native cancellation/interruption carries no SDK guarantee of a last snapshot.
  Callers already holding an accepted execution ID may explicitly fetch current
  status or begin another wait. The prior snapshot on an SDK exception is only
  the last observed state and may be stale; it is not restored execution state.
- Cancellation/interruption of submission after possible dispatch can leave
  acceptance unknown without a reliable execution ID. Preserve native propagation,
  document possible server effects and never automatically resubmit or infer an ID.

This is the deliberate Python adaptation of Java interruption behavior. Preserve
Java's SDK error context without adding machinery solely to recover progress from
native cancellation. Test SDK timeout/error context, cancellation propagation and
cleanup during polling/sleep/body reads, and no replay or remote cancellation.

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
  allowlist, deduplicated; an empty list is valid. The allowlist defaults to empty,
  so no roles are granted by default. Reject blank/non-string authority names;
  never add or strip `ROLE_`.
- Positive representable lifetime with a warning above one hour and no policy
  maximum, matching Java's current behavior. Roles are snapshots, not live
  authorization-account lookups.

### Independent lifetime follows Java

The suite caps issuance to `min(login expiry, now + 600 seconds)`. Java's current
`AuthenticatedIdentity` contains only subject/authorities, and its issuer uses
configured lifetime; the Spring adapter does not impose a login-expiry cap.
Do not incorrectly describe the suite policy as existing Java parity.

The suite's cap is an incidental implementation choice, not an approved SDK
requirement. Removing it would be a behavioral change, not automatically a defect
or security regression. The baseline is Java-equivalent independent configured
lifetime with the 15-minute default, with no Python-only `expires_at` extension
unless a concrete requirement is established. If revisited, consider both SDKs together. Do not
invent a source-token expiry for LDAP, database or session authentication.
Neither independent expiry nor a source-expiry cap automatically propagates
logout, account revocation or authority changes to already-issued credentials.

## Signing keys and public trust

Require an explicit signing mode. No fallback-generated keys when provided
material is missing or invalid.

**Colocated:** generate a fresh RSA key of at least 2048 bits in process memory
at startup; atomically publish only its public JWKS into an existing directory.
Restart changes the signing identity. One process owns the export path. FastAPI
multiworker deployments cannot each run colocated signing against one file;
use provided shared identity for multiple workers/replicas, with one writer per
public export path in either mode. Do not silently turn colocated mode into
persistent private-key storage.

**Provided:** load operator-owned RSA material once at startup, validate strength
and format, and atomically export public JWKS.
Current Java implements PEM-only provided signing, retaining colocated generation.
Python implements that contract directly; do not add obsolete keystore settings,
custom parsers or compatibility paths. Accept unencrypted PKCS#8 only
and remove signing-certificate inputs/export, following the
[shared PEM signing design](sdk-pem-signing-design-notes.md).

Expose no signing format, alias, password or certificate settings. Reject
unsupported configuration clearly and reject output paths overlapping private input.
Export parents must already exist. Atomic export failure aborts startup; there
is no non-atomic fallback. Private keys never enter JWKS, diagnostics or bundles.
Provided-key changes take effect by restart. TLS certificate configuration remains
separate from signing material.

Callback trust is distinct from Sidecar's trust configuration. Match Java's own
startup public key plus optional local overlap-JWKS file, loaded at startup,
polled every second, and refreshed on unknown `kid` at most once per second.
Validate complete public RSA key sets before atomic replacement. A configured
missing/unreadable/malformed/empty overlap file fails security/application startup.
After successful startup, detecting such a file withdraws all callback trust,
including the own key, until valid trust returns. Log safe state transitions
without token/key/parser content or repeated polling noise; initial failure must
leave no refresh task. No stale-key fallback or private signer reload. Withdrawal
affects new authentication, not already admitted work.

Match Java's whole-set validation: valid UTF-8 JWKS with a nonempty key array,
public RSA keys of at least 2048 bits with valid modulus/exponent, unique nonblank
kids, no private members, and compatible `use`/`alg` when present. A conflicting kid
between own and overlap keys invalidates the set; an identical own key is allowed.
Do not silently skip invalid entries. No overlap file means no polling/file reads.
Unknown-kid refresh is throttled across requests for that trust instance, not per
attacker-supplied kid. Use the current Java validator and fixtures as the detailed
baseline; this is not remote JWKS discovery or a historical key cache.

This is deliberately different from copying the suite's request-by-request file
read verbatim. Keep file I/O and cryptographic startup work off the async request
event loop, and manage background trust-refresh work through application lifespan.

## FastAPI callback security

Provide a small explicit installation API that protects an entire configured
callback prefix, plus a typed dependency exposing the verified principal to
handlers. Default prefix: `/loomspan/callbacks`, including its descendants but
not similarly named siblings. Match Java's configurable literal, absolute non-root
prefix: normalize a trailing slash and reject wildcards, query/fragment, whitespace,
controls, encoding/parameter markers, backslash and empty/dot segments. Reject blank
explicit audience/overlap settings. There is one issuance issuer; no separate
callback issuer or dynamic per-route trust configuration is needed.

A bare per-handler dependency is insufficient as
the only guard: forgetting it on a new route must not expose the callback area.

Agreed shape: prefix-scoped ASGI authentication enforcement with an
`APIRouter` integration and a request-scoped principal dependency. Compose with
the application's existing lifespan; do not replace its startup/shutdown hooks.
Security initialization failure must fail application startup. Callback trust
validation belongs to security initialization, not the standalone client constructor.
Initialize mounted callback security through the owning application lifespan and
clean up partial initialization as well as normal shutdown. Document middleware
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
  credential expiry before forwarding. Issuance retains independent lifetime.
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

## Agreed FastAPI API and lifecycle approach (2026-11-07)

The developer accepted a small FastAPI integration object providing lifespan
management and typed dependencies, backed by independently usable core clients and
issuance classes. Use ordinary `FastAPI`, `APIRouter`, `Depends` and `async with`.
The architecture below is agreed; exact Python names remain illustrative and no
Python implementation or runnable quickstart is claimed yet.

### Explicit construction and application-owned lifetime

Constructing typed settings and the integration object may validate configuration
but performs no file access, key generation, network access or background work.
Entering its async lifespan initializes the signer, exports public JWKS, validates
callback trust, starts owned trust-refresh work and opens the reusable async client.
Initialization failure prevents startup and cleans up partially initialized
resources. Exiting cleans up owned resources without cancelling remote executions.
Compose this lifecycle explicitly with the application's existing lifespan; do not
replace hooks or depend on automatic lifespan execution for mounted applications.
Runtime resources belong to the application instance. There is no global current
user, shared per-user credential or implicit request identity propagation.

Illustrative setup (proposed SDK imports):

```python
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI

from loomspan_sdk import IssuanceSettings, ProvidedSigning
from loomspan_sdk.fastapi import Loomspan, CallbackMiddleware

loomspan = Loomspan(
    base_url="http://localhost:8080",
    signing=ProvidedSigning(
        key_location=Path("/run/secrets/signer.pem"),
        jwks_export=Path("/run/public/jwks.json"),
    ),
    issuance=IssuanceSettings(roles=["ASSESS_EQUIPMENT"]),
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with loomspan.lifespan(app):
        # Compose the application's other managed resources here.
        yield


app = FastAPI(lifespan=lifespan)
app.add_middleware(CallbackMiddleware, loomspan=loomspan)
```

Keep public export ownership explicit: one writer per export path even when
multiple provided-key workers share signing identity. The example is one process;
production worker configuration must respect that rule. A provided-key replica
recipe can use the same private identity with a separate export path/directory per
process. Operators choose/distribute the corresponding public trust. Do not add
leader election, locks or a shared export service to make multiple processes own
one export path. Verify the recipe in planning instead of presenting a generic
multiworker command as sufficient configuration.

### Typed dependencies and explicit issuance

Expose dependencies for client, issuer and callback principal (illustratively
`loomspan.client`, `loomspan.issuer`, `loomspan.principal`). Support ordinary FastAPI
dependency overrides for application tests. The integration resolves resources
from the initialized application; it must not lazily initialize them per request.

The application supplies its existing verified user dependency. It constructs
`AuthenticatedIdentity` explicitly and passes issued credentials per client call;
repeated mapping can be factored into an application dependency. No SDK identity-
provider registry, claim guessing or required login-token format is introduced.

```python
from typing import Annotated
from fastapi import APIRouter, Depends
from loomspan_sdk import (
    AsyncSidecarClient, AuthenticatedIdentity, ExecutionTokenIssuer,
)
from loomspan_sdk.fastapi import CallbackPrincipal

# AssessmentInput, User, current_user, require_authority and equipment are
# application-owned business models, authentication and authorization code.
@app.post("/assessments")
async def start_assessment(
    body: AssessmentInput,
    user: Annotated[User, Depends(current_user)],
    issuer: Annotated[ExecutionTokenIssuer, Depends(loomspan.issuer)],
    client: Annotated[AsyncSidecarClient, Depends(loomspan.client)],
):
    identity = AuthenticatedIdentity(
        subject=user.account_id,
        authorities=user.authorities,
    )
    credential = issuer.issue(identity)
    accepted = await client.submit(
        "assessEquipment", body.model_dump(), credential=credential,
    )
    return {"execution_id": str(accepted.id)}


callbacks = APIRouter(prefix=loomspan.callback_prefix)


@callbacks.get("/equipment/{asset_id}")
async def equipment_callback(
    asset_id: str,
    principal: Annotated[CallbackPrincipal, Depends(loomspan.principal)],
):
    require_authority(principal, "ASSESS_EQUIPMENT")
    return equipment.lookup_for_owner(
        asset_id, principal.issuer, principal.subject,
    )


app.include_router(callbacks)
```

### Prefix-wide enforcement with ordinary routers

One pure ASGI middleware protects the entire configured prefix, including routes
that omit the principal dependency. The dependency retrieves verified request-local
identity; it is not the authentication boundary. Do not require a custom router
class or callback decorator. Inspect raw headers and leave bodies untouched.

Document and test ordering with browser authentication/redirect middleware and
CORS handling so application middleware cannot bypass or change the callback
security contract. Installation without initialized security must fail closed;
it must never serve an unprotected callback. Keep unrelated route authentication
and CSRF behavior under application control. Test mounts/root paths, startup failure,
cleanup, omitted principal dependencies and concurrent-user isolation.

### Core remains independently usable

Client-only scripts/workers can use an already-issued credential without the
FastAPI integration, private signer or callback configuration, matching Java core:

```python
async with AsyncSidecarClient(base_url=url) as client:
    accepted = await client.submit(
        "assessEquipment", payload, credential=credential,
    )
```

The initial integration surface is one integration object, one middleware and
three dependencies, with ordinary routers. Exact naming, resource storage and
middleware mechanics are implementation details to prove with the complete
quickstart and tests, not new architecture decisions. Do not require a settings
framework, ASGI server or web dependencies in core.

The delivered quickstart must include actual imports, runnable local configuration,
a small trusted identity adapter and business authorization examples. Secrets must
not appear in generated OpenAPI examples or errors. Usability acceptance remains
replacement of the suite's custom JWT logic without business handlers manipulating
token claims, files or signatures.

References for the selected framework conventions:
[FastAPI lifespan](https://fastapi.tiangolo.com/advanced/events/) and
[Starlette pure ASGI middleware](https://starlette.dev/middleware/).

## Adoption in the maintained suite

Replace `execution_identity.py` protocol logic with SDK issuance and the protected
FastAPI callback integration. Keep Keycloak authentication and equipment business
authorization in the application. Replace raw `httpx` Sidecar transport with the
async client while preserving public application response behavior deliberately.
Choose issuance behavior from the agreed SDK contract; the suite's incidental
source-expiry cap does not constrain that contract.

Use provided signing to retain the suite's existing operator-owned test identity;
do not regenerate persisted credentials as an incidental SDK installation action.
Move key creation/export responsibilities only where ownership is explicit.
Do not hide issuer changes with record relabeling or compatibility adapters.

Preserve all equipment inputs, approval envelopes, model declarations, protected
original reference packs and accepted results. Reuse the established namespaced
contract unchanged unless a separately justified public-contract change requires
review. Add no client-specific Sidecar behavior and no new Framework extension.

## Verification and delivery outline

Create separate dated implementation tickets through Sidecar's pipeline using
the settled decisions in this note. Public API, security and lifecycle
implementation requires the Full 5-Step Pipeline; this documentation refresh does
not authorize production implementation. Each unit owns its tests and documentation.

1. **Package foundation, core transport and public models:** independent packaging,
   typed core imports without web dependencies, sync/async clients, credentials, errors,
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

## Agreed packaging and support commitments (2026-11-07)

The developer accepted the following packaging choices and initial release targets.
Support is earned through installation and SDK verification before release; these
are not claims of compatibility for an SDK that has not yet been implemented.

| Area | Decision |
| --- | --- |
| Distribution / import | `loomspan-sdk` / `loomspan_sdk` |
| Installation | `pip install loomspan-sdk` for core; `pip install "loomspan-sdk[fastapi]"` for integration |
| Layout | Independent project at `sdks/python`, with `src/loomspan_sdk/` and standard `pyproject.toml` |
| Artifacts | Wheel and source distribution, type annotations and packaged `py.typed` |
| Version | Start development at `0.1.0.dev0`, progressing toward `0.1.0`; version independently of Java SDK, Sidecar and Framework |
| Python | Standard CPython 3.12 minimum; initial tested support target 3.12Ã¢â‚¬â€œ3.15 |
| Metadata | `requires-python = ">=3.12"`; separately document versions actually tested |
| Platforms | Linux x86-64 for production/composed integration; Windows x86-64 for development/deployment; macOS ARM64 for development/local use |
| Other runtimes | No initial support commitment for PyPy or free-threaded CPython |

A lookup of the proposed PyPI project URL during the packaging discussion returned
404. This does not establish name availability or ownership. Confirm those before
publication; do not publish or reserve a name as part of implementation planning.
New Python releases become supported after verification, not automatically because
they satisfy the metadata minimum. Python 3.12 was selected for a longer support
runway than 3.11; the official lifecycle table lists October 2028 versus October
2027 end of support. Recheck timelines when planning later support changes.

### Dependencies and installation boundary

Core includes the selected HTTP transport and signing dependencies. Web integration
dependencies belong to the `fastapi` extra; core imports and runs without FastAPI,
Starlette or an ASGI server. Do not install Uvicorn, a FastAPI CLI, identity provider
or settings framework as a consumer requirement. There is no JKS extra.

Declare directly used libraries directly. Publish tested dependency ranges in
package metadata and keep pinned development/test environments. Verify both the
declared minimum dependencies and a current compatible set. Exact HTTP transport,
JWT, cryptography and FastAPI bounds follow the feasibility work below; candidate
libraries are not selected merely by being named in earlier research.

### Proportional verification and release support

Run the CPython 3.12Ã¢â‚¬â€œ3.15 matrix on Linux and the SDK suite on one selected supported
Python version on Windows and macOS. Cover installed-package behavior, PEM loading,
atomic exports, TLS and lifecycle on each target platform. Run expensive composed
Sidecar scenarios on one representative Linux configuration rather than every
Python/OS combination. Use representative minimum/current dependency environments
rather than a full dependency-by-platform Cartesian product. Test the installed
wheel and build/install the source
distribution; repository imports alone do not prove the package works.

The selected per-platform Python versions and dependency environments are test-plan
details. Do not describe untested combinations as independently certified. If a
release target cannot meet the contract, record the evidence and revisit the
commitment explicitly rather than silently dropping it or weakening behavior.

Maintain the current SDK release line initially; make no promise of backports
across multiple older lines. Document tested Sidecar versions independently of SDK
versions. Java/Python parity means equivalent capabilities and protocol behavior,
not synchronized release numbers. Publication remains separately scoped and must
not overwrite an existing release.

Sources consulted for the packaging decision:
[Python version lifecycle](https://devguide.python.org/versions/),
[pyproject.toml guidance](https://packaging.python.org/en/latest/guides/writing-pyproject-toml/),
and [src layout guidance](https://packaging.python.org/en/latest/discussions/src-layout-vs-flat-layout/).

## Ticket readiness

No product-design decisions remain open before creating implementation tickets.
Use the four delivery units above, with dated filenames and the development policy
and design-lens reference prominent in each ticket. Package foundation belongs with
core transport; FastAPI integration depends on client and signing/issuance contracts,
and maintained-suite adoption/composed verification follows those capabilities.
Each unit owns its documentation and verification; final integration is not a
substitute for unit-specific evidence.

The Full 5-Step Pipeline provides research, implementation planning, test planning,
implementation/verification and independent review. Tickets should preserve the
agreed outcomes, acceptance criteria and boundaries while leaving library selection,
exact API names and test mechanics to those stages. Do not require completed
implementation proofs merely to write the tickets. Research/planning must resolve
the feasibility items below before locking the affected implementation approach.

If evidence requires a material divergence from Java behavior, the accepted API
approach or support targets, bring back that specific tradeoff for a decision.
No new compatibility layer, weakened deadline, precision loss or reduced security
contract is implicitly authorized. PyPI name ownership and publication setup are
release prerequisites, not blockers to creating local implementation tickets.

### Engineering evidence needed before committing implementation details

These are bounded feasibility/verification tasks, not additional policy questions
for the developer. Select the simplest implementation that meets the baseline;
raise a cross-SDK design question only if evidence shows a necessary divergence.

- **Transport and total deadlines:** HTTPX remains a candidate, not a selected
  dependency. The earlier probe established that its default cookie persistence
  needs disabling; earlier research identified the need to prove a real synchronous
  total deadline. Demonstrate termination of underlying I/O during stalled and
  trickling bodies, resource release, remaining wait-budget enforcement, one attempt,
  no cookies/redirects/rechallenge and bounded body reads. A thread timeout leaving
  I/O running, or a hidden event loop in the sync API, does not satisfy the contract.
  Match Java's disabled automatic compression rather than adding decompression
  support incidentally. Prove async deadlines and cancellation independently.
- **Crypto and temporal representation:** PyJWT/cryptography remain candidates.
  Verify strict PEM handling against Java fixtures, public kid/JWKS equality,
  signature/profile interoperability, booleans/nonfinite numeric rejection,
  fractional precision and representable timestamp limits. Do not accept a
  library's integer-expiry coercion or silently reduce Java's accepted precision.
  If full precision/range parity requires a public representation tradeoff, document
  the concrete limitation and propose it explicitly for review.
- **FastAPI boundary:** prove raw duplicate-header inspection, untouched bodies,
  middleware order, mounts/root paths, malformed paths and unrelated browser/CSRF
  isolation with actual server behavior. Initialization and partial-failure cleanup
  belong to the composed application lifespan. File reads/key generation must not
  block request processing. Decide implementation mechanics from this evidence.
- **Deployment and dependencies:** verify selected versions and installation on the
  declared support matrix; declare directly used libraries directly. Prove atomic
  exports and equivalent-path protection on supported platforms. A single-worker
  colocated example and a provided-key example are sufficient starting recipes;
  each public export path has one writer even when provided identity is shared.

### Closed questions and superseded research

The earlier 2026-11-03/06 discussion evaluated JKS/PKCS12 dependencies, encrypted
PEM, signing certificates, a provided-only greenfield design and complete-file-only
callback trust. Those alternatives are superseded by the shared PEM contract and
current Java implementation; they are not pending decisions. Historical dependency
version observations and synthetic probes did not certify a supported stack.

The 15-minute default and independent lifetime now follow Java under the parity
instruction. No new source-expiry field, automatic refresh, issuer/namespace
isolation, rotation coordinator, general plugin system or business authorization
engine is proposed. Reopen a shared behavior only for a concrete consumer need or
verified correctness issue, considering both SDKs together.

## Source anchors

- [Java SDK contract and examples](../../../sdks/java/README.md).
- [Java issuer](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionTokenIssuer.java)
  and [trusted identity](../../../sdks/java/core/src/main/java/ai/loomspan/sdk/AuthenticatedIdentity.java).
- [Spring identity adapter](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SpringExecutionTokenIssuer.java),
  [callback security](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackSecurityAutoConfiguration.java)
  and [JWT validation](../../../sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackJwtDecoder.java).
- [HTTP executor design](http-skill-executor-design-notes.md). Earlier 9.3 suite
  results above are retained historical context; its verification report is absent
  from this checkout and was not revalidated in this refresh.
- Neighboring suite: `apps/python/infrastructure/execution_identity.py`,
  `sidecar_client.py`, `scripts/prepare.py`, `scripts/sdk_lane/`,
  `tests/test_execution_identity.py`, and `tests/test_replay.py` under
  `C:/opendev/code/loomspan-sidecar-test-suite`.

Source assessment was refreshed against the Java checkout identified at the top
on 2026-11-07. No Java tests, Python probes or composed integration runs were
performed in this refresh. Recheck changed Java behavior when planning implementation;
this document must not silently freeze an obsolete SDK contract.
