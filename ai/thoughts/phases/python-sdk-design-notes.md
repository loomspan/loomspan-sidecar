# Python SDK with Java SDK parity

Date: 2026-11-02. Status: design proposal for future planning; no Python SDK is
implemented or published by this document. API names and examples below are
proposed, not supported interfaces.

Discussion updated: 2026-11-06. Keep questions, findings and decisions here as
the SDK design conversation proceeds; distinguish proposals from agreed scope.

## Design discussion: scope and baseline

### Context handoff (2026-11-06)

The primary v1 audience is the general Python/FastAPI developer. Build toward
the revised common SDK contract, not literal parity with Java's obsolete key
formats. The [shared PEM design](sdk-pem-signing-design-notes.md) is authoritative
for signing; historical research below explains rejected alternatives only.

Three Java tickets are ready; none has been implemented or run through the
pipeline in this discussion:

1. [PEM signing](../tickets/PR-11.1-java-sdk-pem-signing.md).
2. [Submission uncertainty](../tickets/PR-11.2-java-sdk-submission-uncertainty.md).
3. [Callback trust startup](../tickets/PR-11.3-java-sdk-callback-trust-startup.md).

Keep these as three cohesive units. PEM is the largest and spans core, starter,
documentation and maintained consumers; split into smaller work items only if
pipeline research uncovers a materially larger scope, not merely along file/module
boundaries. Recommended execution order is PEM, submission correction, then
callback startup, with shared checks against the final combined state. This is
sequencing guidance, not a hard functional dependency.

The developer explicitly permits intermediate broken builds between tickets and
will sync only after the series. Do not add compatibility code to keep intermediate
states green. Record each temporary break and the ticket that resolves it; do not
claim blocked checks passed. Final combined build, affected SDK/Sidecar/suite
verification and required independent reviews must pass before the series is
reported complete/ready to sync. Unrelated behavior remains outside scope.

Completed in this chat: design/document updates, Java README explicit-subject
example, source/test review, dependency research and limited synthetic Python
probes. No production implementation, commit, publication, live provider calls or
full SDK integration tests were performed. Local documentation/tickets remain
workspace changes; inspect current git status on resumption rather than assuming
they were committed. Model intended for implementation: Sol 6.1; ticket sizing
is based on cohesive outcomes, not an assumed model capacity.

Next Python work: review a concrete FastAPI quickstart/API sketch (explicit
lifespan, async client, trusted identity dependency and prefix-wide callbacks),
then settle package/import names, supported Python/library ranges, sync total
deadline implementation and cancellation/last-snapshot ergonomics. HTTPX, PyJWT,
cryptography and AnyIO are candidates, not a tested final dependency selection.
JKS/PKCS12 feasibility and encrypted PEM are no longer active work. The 15-minute
credential default remains provisional; do not reopen settled authentication,
expiry-at-admission, rotation or namespace policy without new evidence. The test
suite is a customer and must not dictate SDK contracts. Do not build Python as an
incidental part of the Java tickets.

### Current decisions

- **Agreed scope, revised 2026-11-06:** align both SDKs on PEM-only provided
  signing while retaining ephemeral colocated generation. This supersedes the
  earlier requirement to reproduce Java's JKS/PKCS12 support in Python. Java must
  change; Python implements the revised contract. Go and .NET are future consumers
  of the same contract. Provided keys will be unencrypted PKCS#8 PEM for now;
  encrypted PEM is out of scope. Signing certificates are removed from the target
  contract. The [shared PEM signing design](sdk-pem-signing-design-notes.md) owns
  these cross-SDK requirements;
  implementation is not authorized merely by recording the design direction.
  Minimal configuration and strict PEM input rules were adopted on 2026-11-06
  in that shared document. Sidecar needs verification against the resulting
  public JWKS/tokens, with no production protocol change identified; SDK callback
  startup changes must not be applied to Sidecar's colocated trust startup.
- **Agreed signing scope:** retain both colocated and provided signing modes.
  **Agreed colocated behavior:** retain ephemeral keys regenerated on SDK startup;
  Sidecar's public-trust reload makes independent restarts workable. Source inspection
  confirms Java currently generates a fresh in-memory private key on every SDK
  startup and replaces only the public `jwks.json`; it does not load or persist a
  private key. The developer expected possible generate-only-if-missing behavior
  and raised independent-restart continuity as a concern. Sidecar reloads the
  public trust file, so it can adopt the new key without restarting, but old
  credentials lose trust as the old public key disappears. SDK callback trust
  also starts with the new signer key unless explicit overlap trust is supplied.
  Restarting only Sidecar does not rotate the application's signing key.
  Provided signing reuses operator-owned private material across restarts.
  The developer accepts disruption to in-flight realtime work when the SDK
  application restarts; persistent generated private-key storage is not needed.
  This preserves the existing design-lens policy. Clarification: restart/key
  replacement is not cancellation, rollback or guaranteed disposal of Sidecar
  work. Already-admitted execution may continue and effects may already have
  occurred; subsequent callbacks using the old credential can fail. Do not
  describe in-flight work as guaranteed dead or automatically replay it.
  **Agreed scope boundary:** from the restarting application's perspective,
  losing its in-flight realtime interaction is acceptable. Do not add restart
  detection, coordinated cancellation, draining, recovery or execution-tracking
  machinery to manage Sidecar work across SDK-host restarts. The benefit does
  not justify the complexity for this use case. This does not change ordinary
  resource cleanup or the existing execution/shutdown contracts.
- **Java is reviewable:** existing Java decisions are open to improvement. Both
  SDKs should target the deliberately agreed contract rather than reproduce
  behavior that we already expect to replace.
- **Agreed rotation ownership:** retain the option for planned signing-key
  rotation with overlapping public trust. Operators provision and protect private
  keys, assemble and distribute overlap trust, coordinate signer restarts, and
  remove old verification keys when the transition is complete. The SDK loads
  and validates its private signer at startup, atomically exports its public
  JWKS, and observes configured callback overlap trust. Sidecar independently
  observes its configured public trust. Private-key changes require application
  restart; missing or invalid provided material fails startup without generating
  a replacement. Do not add an automatic rotation manager or automatic overlap
  retirement. Public export contains the startup signer's public material; it
  does not automatically assemble an operator's old/new overlap set.
- **Agreed: retain fail-closed callback trust.** With
  no overlap file configured, trust the startup signer's public key. If an overlap
  file is explicitly configured but becomes missing, unreadable, malformed or
  empty, withdraw all callback trust, including the own key, until a valid file
  is observed. This treats configured trust as one required security configuration
  rather than introducing stale-key grace periods or partial fallback behavior.
  The availability cost is explicit: a file delivery error blocks new callbacks
  even from the own signer. Operators should replace complete files atomically,
  keep a valid nonempty trust set at the configured path, and restore valid content
  to recover. Removing an overlap key means publishing a valid set without that
  key, not deleting/emptying the configured file. Already-admitted callbacks may
  finish. Clear safe diagnostics and documented recovery are needed; no new
  rotation controller or historical trust state is proposed. Own-key-only fallback
  is a possible alternative, not inherently insecure, but is not recommended here.
- **The suite is an early customer, not a reference design.** Its Python
  integration was created opportunistically by an LLM without a specific mandate
  or a deliberate design discussion. Its choices have no normative authority.
  Use it for adoption and interoperability evidence, not to derive requirements.
- **Identity intent:** leave authentication to the application (including LDAP,
  database authentication and Keycloak). An original login token is not required.
  Java captures Spring's authenticated name and authorities, issues a separate
  application-signed execution JWT with allowlisted authorities, and establishes
  a new Spring JWT authentication on callbacks. It does not wrap the original
  credential or restore its session, custom principal or provider-specific claims.
- **Current recommendation, not a new implementation decision:** retain an
  independent execution-credential lifetime. Source-login expiry is not a
  universal authentication concept, and an expiry cap does not itself implement
  logout, account revocation or live permission checks. The suite's cap is not
  an approved policy that SDK adoption must preserve.
- **Open question: default lifetime.** Java's 15-minute default originated as an
  assistant suggestion, not an established workload requirement. There is no
  evidence yet that it is wrong or that another number would be better. Recommend
  retaining it provisionally while checking the required interval from issuance
  through queueing and the last authenticated callback/status request. This is a
  credential validity window, not an execution timeout or cancellation promise.
  Longer validity extends bearer-token exposure and authority-snapshot staleness;
  shorter validity can interrupt later authenticated interactions. No default
  change has been agreed. Review long-running work expectations before finalizing.
- **Finding: workflow duration cannot be predicted by the SDK.** Users choose
  model latency/thinking settings and workflow depth; valid executions may be
  very short or very long. No fixed default can promise coverage of every
  workflow. Lifetime therefore remains an application-owned configuration choice,
  not a duration inferred by the SDK from models or skill trees.
  The current recommendation remains a provisional 15-minute default, with clear
  expiry consequences and guidance to budget for queueing and later authenticated
  interactions. This is not user approval of that default. Do not add automatic
  refresh or unbounded validity to hide the limitation. Still to settle: whether
  applications must explicitly configure lifetime or may accept a documented
  default.
- **Agreed recommendation: expiry governs callback admission, not cancellation.**
  Check credential validity when authenticating a new callback. A callback already
  authenticated and admitted may finish after token expiry; expiry itself must
  not interrupt its business code. This does not override ordinary request
  deadlines, shutdown or application failures. Beginning an execution before
  expiry does not authorize new callbacks indefinitely: Sidecar still requires
  an unexpired credential before forwarding, and callback receivers independently
  validate it. The existing documented 60-second receiver timestamp skew is a
  separate validation tolerance, not an execution-lifetime extension.
  Applications must choose a credential lifetime suitable for their workflows,
  including queueing and later callbacks. Unpredictably long executions can reach
  a callback after their credential expires; document that limitation explicitly.
  Do not accept expired bearer credentials merely because work may have started
  earlier, or add automatic refresh. A future execution-scoped grant would need
  its own explicit design for verifiable execution association and termination;
  it is not part of this recommendation. Verify admission/finish behavior in each
  SDK's own tests rather than treating this decision as implementation evidence.

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

### Agreed SDK requirements and Java follow-up

Python is not yet implemented: agreed behavior is a Python implementation
requirement, not a Python fix. Track corrections to existing Java separately.
Create dated implementation tickets through the repository workflow after this
design review; this section is the decision record, not implementation evidence.

**Agreed requirement: preserve submission uncertainty.** If a submission may
have been sent and no reliable acceptance response is available, expose acceptance
uncertainty rather than implying that execution did not start. Oversized success
responses are included. Failures detected before sending remain distinguishable;
known HTTP error responses retain their status without implying absence of effects.
Never automatically resubmit. Python must preserve normal async cancellation and
document possible acceptance when cancellation occurs after sending.

**Required Java fix — agreed, not implemented:** correct oversized submission
response classification and interruption after possible submission in
`SidecarClient.request`. Preserve the interrupt flag and the distinction between
pre-send interruption and uncertain submission. Retain observed HTTP error status
when its body exceeds the response bound. Update the test that currently expects
`ProtocolException` for an oversized 202 response. Verify fixed-length and streamed
overflow, interruption before/after possible send, retained HTTP status, and one
request attempt with no inferred execution ID. Status-read failures must retain
their existing requested-ID/last-snapshot context.

**Completed Java documentation:** the README now explains the existing explicit
subject option; no identity API change is required.

**Deferred Java consideration:** client-only Spring starter activation awaits a
demonstrated consumer need. It is not an agreed implementation task.

**Agreed requirement: initialize callback trust before serving requests.** If an
explicitly configured overlap-JWKS file is missing, unreadable, malformed or empty
at security initialization, fail initialization with a clear, safe diagnostic.
Without an overlap file configured, use the startup signer's public key; no
overlap file is required. After successful startup, an unusable configured file
withdraws all callback trust, including the own key, until valid trust returns.
Keep the application running and let already-admitted callbacks finish. Report
trust-state transitions with safe unavailable/invalid/restored diagnostics; do
not log token/key contents, raw parser exceptions or repeated polling failures.

**Required Java fix — agreed, not implemented:** make invalid configured initial
overlap trust fail Spring startup, rather than starting with an empty trust set.
Add safe state-transition diagnostics to callback trust reload. Preserve runtime
withdrawal and automatic recovery. Verify startup failures for missing,
unreadable, malformed and empty files; successful own-key-only startup when no
file is configured; runtime withdrawal/recovery; and non-repetitive diagnostics
without sensitive content. Ensure failed initialization leaves no polling task.

**FastAPI implementation requirement:** initialize signing and callback security
through an explicit managed lifecycle composed with the application's existing
FastAPI lifespan. Propagate initialization failure to fail application startup
before serving requests. Own trust-refresh work and cleanup through that lifecycle,
including partial initialization failure. Do not attach overlap-file validation
to the standalone HTTP client constructor: the client transports credentials,
while callback security owns verification trust. Protect the configured callback
prefix as a whole. Exact installation API remains to be designed; avoid implicit
Spring-style autoconfiguration or replacing application lifespan hooks.

### Java baseline review for Python design (2026-11-03)

Discussion approach agreed by the developer: review Java for concrete weaknesses
and discuss exceptions, rather than repeat the original design questionnaire.
Retain settled behavior unless evidence warrants reopening it. The following is
a source-and-test review, not a new runtime verification pass or authorization to
change Java. No production code was changed. Decision status is recorded in the
section above and in each item's discussion notes.

1. **Separate client use from issuance/security activation.** The Spring starter's
   `ExecutionTokenAutoConfiguration` unconditionally creates signing and issuance
   beans, and the servlet integration installs callback security. Its tests
   explicitly require startup failure when signing mode is absent, even with a
   supplied client bean. An application using already-issued credentials therefore
   cannot use the ordinary starter configuration as a client-only integration
   without exclusions or bypassing it. Java core already supports this use case.
   Recommend independently usable client and explicitly activated security setup;
   keep the complete security setup convenient, and fail on invalid configuration
   once activated. Do not require private signing keys merely to call Sidecar.
   Python should not inherit this coupling. Decide whether to simplify Java's
   starter activation in parallel; no general plugin system is needed.
   **Discussion decision:** defer Java starter activation changes until actual
   client needs justify them. Potential beneficiaries are workers receiving
   already-issued credentials or apps using a separate issuer, not the main
   authenticate/issue/callback application flow. Java core already supports
   credential-only client use. This is a convenience tradeoff, not a demonstrated
   defect requiring immediate change.
2. **Make stable subject selection explicit.** `SpringExecutionTokenIssuer`
   uses `Authentication.getName()` as `sub`. That is an application-defined name,
   not a guarantee of immutable account identity. For applications whose names
   can be changed or reassigned, issuer-plus-subject ownership can split one
   person's identity or conflate successive owners of a name. This is conditional
   on the application's identity semantics, not a universal Spring defect.
   Recommend a clear contract and example for selecting a stable, non-reassigned
   application account ID. Java's existing core `AuthenticatedIdentity` already
   provides the escape hatch; determine whether documentation suffices before
   adding any adapter API. Python's explicit trusted adapter should select the
   subject deliberately. This requires no LDAP/DB/provider lookup by the SDK.
   **Discussion clarification:** the SDK cannot determine whether an application's
   authentication name is stable or unique in its issuer namespace. A username
   rename can break continuity; reassignment can make a new account appear to own
   records associated with the former account's issuer/subject. Neither occurs
   merely because `getName()` is used: applications guaranteeing suitable names
   can keep the existing adapter. Prefer an immutable account identifier when
   names are mutable/reusable; it need not be a UUID. The callback authentication
   name becomes that chosen subject, so applications expecting a display/login
   name must account for that deliberately. First assess whether existing Spring
   principal configuration or direct core issuance is sufficient before adding
   SDK customization machinery. No identity API change is agreed yet.
   **Agreed direction and scope correction:** improve subject-contract guidance,
   retain the existing adapter, and show direct `AuthenticatedIdentity` use where
   needed. The developer correctly notes that in-flight identity is a snapshot
   in ordinary Spring applications too. A rename during execution is not a new
   Sidecar-specific identity defect, and the SDK should not reconcile live account
   changes. Sidecar does use issuer/subject for execution ownership, in addition
   to forwarding the credential, but this does not justify a new identity model.
   The earlier recycled-name example concerns applications using names as durable
   ownership identifiers; it is not evidence of a flaw in the intended round-trip
   flow. Treat this as modest documentation clarification, not an SDK redesign
   or a requirement to replace otherwise suitable application usernames.
   **Documentation completed:** added a short example to `sdks/java/README.md`
   explaining explicit core issuance with an application-selected subject and
   its callback authentication-name consequence. No Java API change was needed.
3. **Correct submission error classification before using it as parity evidence.**
   `SidecarClient.request` throws `ProtocolException` directly on body-size overflow
   even after a 202 submission response. `SidecarClientEdgeTest` explicitly expects
   this behavior. The operation may have been accepted but has no reliable ID;
   it belongs to the documented acceptance-unknown outcome. Interruption after
   possible submission instead takes the generic `WaitTerminatedException` branch,
   even though no wait operation was requested. Recommend preserving uncertainty
   consistently for Java submission failures and distinguishing pre-send failure
   from possible-send outcomes. Also retain a known HTTP error status when an
   error body exceeds the limit. Python cancellation must still propagate normally;
   document possible acceptance rather than replacing cancellation with a Java-style
   exception or replaying the request. These are corrections/translation work,
   not reasons to reconsider the single-attempt execution contract.
4. **Make fail-closed trust failures diagnosable.** `CallbackJwtTrust.reload`
   silently replaces trust with an empty map on unreadable/invalid configured
   overlap. Its snapshot exposes keys but no failure reason, and the normal callback
   path returns generic invalid-credential responses. A bad overlap file at startup
   also leaves the application running with empty trust rather than failing startup.
   This implements denial correctly but makes operator recovery harder than needed.
   Recommend safe state-transition diagnostics (unavailable/invalid/restored, no
   token/key contents, no once-per-second log flood). Decide explicitly whether
   invalid initial overlap fails startup or starts with callbacks unavailable;
   the Python notes currently promise startup failure while Java does the latter.
   **Decision:** initial invalid overlap now must fail security/application startup,
   with Java fixes and FastAPI requirements recorded above. Keep the runtime
   fail-closed/recovery rule; do not introduce stale-key fallback
   or an elaborate health/rotation subsystem to solve an observability gap.

Source anchors: `SidecarClient.java` body bounds and exception handling;
`SidecarClientEdgeTest.boundsResponsesReleasesResourcesAndClosesIdempotently`;
`ExecutionTokenAutoConfiguration` and its missing-mode tests;
`SpringExecutionTokenIssuer.issueCurrent`; `CallbackJwtTrust.reload` and its
withdrawal/recovery tests, all under `sdks/java`.

No concrete reason was found in this review to reopen the agreed key modes,
operator-managed rotation, exact role intersection, result fidelity, no automatic
replay, or application-owned authentication. JKS support was subsequently removed
from the target by the shared PEM decision. Python sync/async
lifecycle and cancellation are language adaptations, not flaws in Java's blocking
API. The 15-minute default remains provisional, not a demonstrated defect.

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

The suite's cap is an incidental implementation choice, not an approved SDK
requirement. Removing it would be a behavioral change, not automatically a defect
or security regression. The current recommendation is Java-equivalent independent
configured lifetime, with no Python-only `expires_at` extension unless a concrete
requirement is established. If revisited, consider both SDKs together. Do not
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
use provided shared identity for multiple workers/replicas. Do not silently turn
colocated mode into persistent private-key storage.

**Provided:** load operator-owned RSA material once at startup, validate strength
and format, and atomically export public JWKS.
Java currently supports PKCS12, JKS and unencrypted PKCS#8 PEM. The revised
cross-SDK target is PEM-only provided signing, retaining colocated generation.
Remove obsolete keystore-specific contracts directly in the planned Java change;
do not add custom parsers or compatibility paths. Accept unencrypted PKCS#8 only
and remove signing-certificate inputs/export, following the
[shared PEM signing design](sdk-pem-signing-design-notes.md).

Remove obsolete signing format, alias, password and certificate settings. Reject
obsolete configuration clearly and reject output paths overlapping private input.
Export parents must already exist. Atomic export failure aborts startup; there
is no non-atomic fallback. Private keys never enter JWKS, diagnostics or bundles.
Provided-key changes take effect by restart. TLS certificate configuration remains
separate from signing material.

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

### Greenfield security alternative for discussion

**Current decision, 2026-11-06:** proceed with the PEM-only provided-signing
direction for both SDKs, keeping colocated generation. The earlier alternatives
and dependency findings below explain the decision but do not override it.
The multi-application namespace example introduces no new isolation requirement.
The [shared PEM signing design](sdk-pem-signing-design-notes.md) now owns the
cross-SDK decisions and remaining implementation details. The discussion below
preserves research context; it does not override that design's agreed scope.

Remaining decisions for that note:
- Encryption is settled: unencrypted PKCS#8 only for now. Remove signing-key
  password settings with obsolete keystore settings; encrypted PEM must fail
  startup clearly. Operator-managed secret delivery, storage and access controls
  protect provided private keys. No automatic decryption or conversion is added.
- One RSA private key per provided file, minimum 2048 bits and RS256 retained;
  reject unsupported/ambiguous material with safe startup diagnostics.
- Certificates are settled: remove signing-certificate input, chain handling,
  expiry warnings and x5c export. TLS/mTLS certificates are unaffected.
- Final reduced provided-signing settings and direct rejection/removal of obsolete
  format, alias and store-password options. No automatic keystore conversion.
- Inventory Java/suite fixtures, recipes and documentation affected by removal;
  preserve existing key identity where needed through deliberate operator/test
  preparation. Do not regenerate persisted credentials or reset data incidentally.
  Define cross-language fixtures for loading, kid/JWKS equality and token verification.

**Encrypted PEM assessment (2026-11-06; decision: defer encrypted support):** its concrete benefit
is protecting a stolen/copied private-key file when the attacker does not also
obtain the decryption password, subject to password strength and key-derivation
cost. It can support operator policies requiring encrypted key files. It does not
strengthen JWT signatures, RBAC or TLS and does not protect a compromised running
signer, which can use the decrypted key. Unattended startup still requires access
to a password, so separation of password delivery from key-file storage matters.

Python cryptography already accepts a password in its PEM loader; modern .NET
RSA has ImportFromEncryptedPem. Java 21 provides EncryptedPrivateKeyInfo and JCA
decryption primitives, but mapping supported PBES2 parameters/provider behavior
needs a concrete interoperability proof, not an assumption of a one-call loader.
Go's standard crypto/x509 PKCS8 parser explicitly handles unencrypted keys;
encrypted PKCS8 needs an additional vetted package (youmark/pkcs8 is a candidate,
not a selected/audited dependency) or additional implementation. Do not use legacy
encrypted PEM support as a substitute for encrypted PKCS8.

Estimated incremental complexity: low in Python/.NET loading code; moderate in
Java and Go; moderate ongoing cross-SDK configuration/testing cost. This is a
qualitative estimate from API research, not a four-runtime execution result.
Common work includes env/file password references, wrong/missing/unexpected
password handling, safe startup diagnostics, encoding rules, and a bounded
interoperable encryption profile. PBES2 with PBKDF2-HMAC-SHA256 and AES-256-CBC is
a candidate to test, not an approved profile or iteration-count recommendation.
Avoid promising arbitrary encrypted PEM algorithms. Decryption occurs at startup,
not per token; colocated in-memory generation is unaffected.

Recommendation: support unencrypted PKCS8 first unless encrypted-file provisioning
is an actual target requirement. This is an operator-storage capability rather
than a prerequisite for secure token transport. If encrypted support is desired,
scope it to one tested profile and retain existing secret-reference discipline;
do not implement bespoke cryptography. The developer chose unencrypted PEM for
now; the optional encrypted-profile discussion above is deferred research, not
an implementation requirement.
Sources: [Python loader](https://cryptography.io/en/50.0.2/hazmat/primitives/asymmetric/serialization/),
[.NET loader](https://learn.microsoft.com/en-us/dotnet/api/system.security.cryptography.rsa.importfromencryptedpem),
[Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/EncryptedPrivateKeyInfo.html),
[Go parser](https://go.dev/src/crypto/x509/pkcs8.go),
[Go candidate](https://github.com/youmark/pkcs8), and
[OpenSSL PKCS8 options](https://docs.openssl.org/3.5/man1/openssl-pkcs8/).

**Signing certificates: removal agreed 2026-11-06.** Current Java optionally
loads a certificate alongside PEM, checks that its public key matches, warns on
expiry/near expiry, and exports certificate data as JWKS x5c. Sidecar and SDK
callback validators construct trusted RSA keys from configured JWKS n/e/kid;
they do not establish trust by validating x5c certificate chains or expiry.
Colocated signing already works without certificates. Recommendation: remove
optional signing-certificate configuration/export in the simplified contract,
unless an external consumer concretely requires x5c. Export the public RSA JWK
directly from the signer. This removes certificate loading, correspondence checks,
chain handling and expiry warnings without changing the current explicit-JWKS
trust model. TLS/mTLS certificates remain separately supported through normal
server/client TLS configuration. Implement removal in Java and omit this capability
from Python, following the shared signing design. No production code is changed yet.

Retain agreed token profile, public JWKS format, trust reload/overlap behavior,
operator-owned rotation and independent TLS configuration. PEM format selection
does not require reopening those decisions.

**2026-11-06: cross-language and multi-application requirements.** The developer
expects future Go and .NET SDKs as well as Java/Python, motivating a common PEM
provided-key format. They ask about two apps using different namespaces on one
Sidecar, each calling back only to its source app. PEM does not limit verification
to one key: each app can hold its own private PEM, and an operator-managed Sidecar
JWKS can contain both public keys with distinct kids. Each JWT has one signing
key; multi-key trust enables multiple signers and rotation. Individual SDK public
exports must not overwrite a shared aggregate file; operators own aggregation.

This is separate from issuer/namespace authorization. The current design lens
specifies one issuer per Sidecar and full mutual trust among sharing applications,
with common identity semantics and role vocabulary. Multiple keys in the validator
do not establish support for isolated application issuers or cryptographically
bind keys to namespaces. Namespace-specific callback configuration can route to
the appropriate app but is not proof that other callers cannot invoke its skills.
Each app may validate its own callback signer/audience, while Sidecar-wide app
isolation would require an explicitly planned authorization contract. Confirm
whether the desired topology is a fully trusted group or isolated applications
before representing it as supported end-to-end. No new multi-tenant security
contract or key-format removal is agreed by this question.

**Follow-up under consideration: PEM-only provided signing plus existing colocated
generation in both SDKs.** These choices are independent: colocated keys are
generated in memory and need no keystore loader. Both modes could retain the
same JWT profile, public JWKS export, RBAC, callback identity, TLS and trust reload.
This would remove JKS/PKCS12 format and alias/password combinations and the Python
JKS dependency burden, while preserving the accepted easy-start colocated mode.
It does not require changing own-key/overlap trust composition.

Actual losses are direct consumption of existing keystores, selection by alias
from a multi-key bundle, and bundled key/certificate-chain handling. Operators
would provision one chosen signing key as PEM instead. Java's current PEM loader
accepts only unencrypted PKCS#8 and optionally one certificate; simply retaining
it also removes password-encrypted provided signing material and full keystore
chain export. PEM itself can be encrypted, but adding encrypted PKCS#8 support
would be a separate scoped implementation decision and retain password-reference
handling. Explicitly settle that distinction rather than describing PEM-only as
automatically security-equivalent for storage. Protected secret delivery and
file permissions remain operator duties; some organizations' key-export/storage
policies may rule out unencrypted PEM. Conversion can preserve the same key and
therefore the RFC 7638 kid, but must be deliberate operator work, not automatic
startup conversion or incidental regeneration. No SDK keystore change removes
Spring Boot's separate TLS keystore/SSL-bundle facilities.

Decision: adopt this simplification for both SDKs with unencrypted PKCS#8 and
without signing certificates. This supersedes full-format parity. Implementation
remains pending under the shared design and Java ticket. Sources:
[Java keytool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html)
and [OpenSSL PKCS12 conversion](https://docs.openssl.org/3.5/man1/openssl-pkcs12/).

The developer asks whether, without the Java SDK or an established Sidecar
security model, a simpler design could meet RBAC and authenticated callback needs
for FastAPI and Spring Boot. This question reopens evaluation, not implementation
authorization by itself. The subsequent shared PEM decision supersedes the earlier
full-format-parity requirement.

Recommendation: retain the essential application-issued, short-lived signed
execution JWT, containing issuer/subject, exact allowed roles, intended audiences
and execution purpose. The application authenticates users by its existing means;
Sidecar verifies assertions and applies RBAC; callbacks independently verify the
credential and expose a request-scoped principal for application authorization.
TLS protects transport. Neither original passwords nor provider-specific login
tokens are needed. The existing protocol is already close to this minimal design.

Potential simplification is operational surface, not removing authentication:
one operator-provisioned RSA signing key in PKCS#8 PEM, and one explicit public
JWKS trust file per receiver, supporting multiple keys for operator-managed
rotation. In this alternative, callback trust is exactly the configured complete
set rather than an implicit own-key-plus-optional-overlap merge. Startup validity,
atomic runtime reload, fail-closed behavior and safe diagnostics remain necessary.
Do not include JKS/PKCS12, SDK key generation or automatic rotation in a greenfield
minimum unless actual consumers require them. This would reduce format handling,
initialization modes and trust composition cases, but transfer initial key
provisioning to operators and change the convenience/compatibility scope. It is
historical context only: retain both signing modes and own-key/overlap trust;
remove JKS/PKCS12 and signing certificates per the shared PEM design.

Rejected shortcuts for these requirements: a shared API key alone authenticates
the calling service, not its claimed user/roles; untrusted user/role headers do
not establish identity. A symmetric signing secret shared with Sidecar would let
verifiers mint credentials, unlike public-key verification. A central issuer or
token-introspection service adds an availability/deployment dependency. Mutual
TLS can authenticate services but does not by itself carry delegated user RBAC.
Execution-scoped callback credentials could narrow authority but add issuance and
verification machinery and are not the simplest baseline. Existing bearer-token
replay exposure and shared-trust-group limitations still apply to the proposed
minimal asymmetric design; it is not a claim of universal isolation.

### Dependency and FastAPI developer-experience research (2026-11-03)

**User direction:** target the general Python/FastAPI application developer for
v1. Keep full agreed Java capability parity; lead documentation and examples with
async FastAPI usage rather than Java-shaped configuration. Findings below are
recommendations and feasibility evidence, not dependency selections or reduced
scope. No Python SDK code or project dependency files were created.

Historical research status: JKS extras and PKCS12 parity investigations below were
superseded by the shared PEM decision. They explain dependency costs and are not
remaining requirements. The protocol/transport findings still apply.

**Proposed packaging:** one independently versioned distribution under
`sdks/python`, provisionally `loomspan-sdk` / import `loomspan_sdk` (names not
reserved or verified available). A `fastapi` extra installs the web integration;
core imports without FastAPI/Starlette. A `jks` extra is worth considering to
isolate keystore-specific installation cost; it would still be fully supported
v1 functionality, not a parity deferral. Standard `pyproject.toml`, wheel/sdist,
and `py.typed`; tested dependency ranges for consumers and reproducible development
constraints. Do not force Uvicorn, a FastAPI CLI, an identity provider or a settings
framework on applications. Use typed core objects and let callers use their own
Pydantic settings/business models. See the
[packaging guide](https://packaging.python.org/en/latest/guides/writing-pyproject-toml/).

**Runtime baseline recommendation:** Python 3.11 minimum is a reasonable candidate
for reach without older-version compatibility code; Python 3.12+ would provide a
longer support runway. The official
[version table](https://devguide.python.org/versions/) lists security support
through October 2027 for 3.11 and October 2028 for 3.12. Final supported upper
versions/platforms must follow actual installation and integration checks, not
unbounded assumptions about new Python releases. No support matrix is settled.

**Candidate stack and observed package versions:** PyPI metadata retrieved during
this research reported HTTPX 0.28.1, PyJWT 2.15.1, cryptography 50.0.2, PyJKS 20.0.0,
FastAPI 0.162.0, Starlette 1.10.0 and AnyIO 4.16.0. These are a dated research
snapshot, not a recommended lockfile or compatibility certification. If directly
using AnyIO or Starlette APIs, declare them directly rather than relying on
transitive installation. Verify a bounded FastAPI/Starlette version combination
and older supported baseline before publishing dependency ranges.

**FastAPI integration fits the agreed design.** Use an explicit async context
manager in the application's lifespan, an async client reused for that lifespan,
an application-provided identity dependency, and a typed callback principal
dependency. Use prefix-wide pure ASGI enforcement internally: raw headers must
remain available for duplicate detection and request bodies must pass untouched.
Avoid `BaseHTTPMiddleware` context propagation limitations. Mounting a callback
sub-application does not automatically run its lifespan; initialize security from
the main application's lifespan. Startup key/file work must not block request
processing, and polling belongs to owned background lifecycle work. Sources:
[FastAPI lifespan](https://fastapi.tiangolo.com/advanced/events/) and
[Starlette middleware](https://starlette.dev/middleware/).

Multiple workers are an ordinary FastAPI deployment option, so the quickstart
must make the single-process scope of ephemeral colocated signing conspicuous.
Workers sharing an export path cannot independently generate different keys.
Recommend a single-worker colocated example and a provided-key production example
using shared signing identity, without a new coordinator. See
[FastAPI workers](https://fastapi.tiangolo.com/deployment/server-workers/).

**HTTPX is a good transport candidate, not a complete policy implementation.** It
has native sync and async clients and standard SSL-context support. Its read
timeout is per chunk, not a total response deadline. Async deadlines can use an
AnyIO cancel scope around the whole exchange; synchronous total-deadline
enforcement needs a focused proof before selecting the implementation. A thread
timeout alone does not stop underlying I/O. Keep synchronous support in scope;
do not hide a private event loop in it. Disable redirects/retries and cookie
persistence deliberately, keep credentials per request, bound decoded/streamed
bodies, and preserve cancellation. Sources:
[HTTPX timeouts](https://www.python-httpx.org/advanced/timeouts/),
[async clients](https://www.python-httpx.org/async/),
[transports](https://www.python-httpx.org/advanced/transports/) and
[AnyIO cancellation](https://anyio.readthedocs.io/en/stable/cancellation.html).

**PyJWT plus cryptography can handle signing, but defaults are not the contract.**
Pin allowed verification to RS256 and enforce Loomspan type/purpose, issuer,
audience, role shapes and required fields explicitly. Local inspection of PyJWT
2.15.1 found `int(payload["exp"])` in expiry validation: copying its defaults
would discard fractional expiry precision. Use public signature verification
facilities with explicit profile/time validation where needed, not custom RSA.
Strict numeric handling must reject bool/nonfinite values and preserve the chosen
precision; prove Java/Python interoperation. Sources:
[PyJWT API](https://pyjwt.readthedocs.io/en/stable/api.html) and
[versioned implementation](https://raw.githubusercontent.com/jpadilla/pyjwt/2.15.1/jwt/api_jwt.py).

**JKS is feasible but carries packaging cost.** PyJKS supports aliases, certificate
chains and separate store/key passwords without a JVM. Its published 20.0.0 release
dates to April 2020. Installed metadata includes javaobj-py3, pyasn1,
pyasn1-modules, pycryptodomex and twofish; this is broader than the JKS-only need.
An isolated `uv run` on this Windows host with Python 3.13.13 installed/imported
PyJKS 20.0.0 and built twofish 0.3.0 locally. An in-memory synthetic RSA JKS
roundtrip passed with alias selection and separate store/key passwords. This is
not Java interoperability or proof of compiler-free installation on users'
machines. The earlier recommendation to keep JKS was superseded; no PyJKS
dependency or JKS installation matrix is now required. Do not copy unpublished
upstream code or create a bespoke parser casually. Sources:
[PyJKS release](https://pypi.org/project/pyjks/) and
[keystore API](https://pyjks.readthedocs.io/en/latest/jks.html).

**Unexpected parity issue: PKCS12 needs more research than the format name suggests.**
Java selects a private key by alias from a keystore. cryptography supports one
private key and associated certificates in its high-level PKCS12 parser; its
loader takes data/password, not alias or distinct entry password. A certificate
friendly name does not provide general multi-entry selection. Its additional
certificates also should not be assumed to be precisely Java's selected alias
chain. Therefore single-key PKCS12 loading alone would not have been full Java parity.
The surveyed oscrypto API likewise returns one key/certificate plus extras; it
does not establish an alias-selection solution. This investigation is closed by
the agreed removal of PKCS12 signing support from both SDKs; no further
multi-entry/password/chain feasibility work is required. Sources:
[cryptography PKCS12](https://cryptography.io/en/50.0.2/hazmat/primitives/asymmetric/serialization/#pkcs12)
and [oscrypto implementation](https://github.com/wbond/oscrypto/blob/master/oscrypto/_openssl/asymmetric.py).

**Evidence limits and next steps:** probes used only synthetic in-memory keys and
a mock HTTP transport; no user credentials, live Sidecar/model calls or publication.
HTTPX mock requests confirmed response cookies are persisted and sent on the next
request by default. No cross-language or full OS/Python matrix was run. Prioritize
total-deadline feasibility work and review one concrete
FastAPI installation/usage example. Main open choices include packaging and the
supported Python floor; do not
reopen settled authentication policy merely to fit library defaults.

- Package/import names, independent version and supported Python/FastAPI versions.
- Execution-credential lifetime policy and whether the provisional 15-minute
  default fits expected work; any source-expiry extension needs independent
  justification and consideration for both SDKs.
- Implement the agreed PEM signing contract; no JKS/PKCS12 dependency assessment remains.
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
