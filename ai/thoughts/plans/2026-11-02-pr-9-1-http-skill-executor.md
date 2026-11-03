# HTTP skill executor implementation plan

## Overview

**Development policy: destructively replace superseded contracts, code and schemas.
Do not add compatibility shims, legacy adapters, parallel APIs or migration machinery.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).

- Ticket: `ai/thoughts/tickets/2026-11-02-pr-9-1-http-skill-executor.md`
- Research: `ai/thoughts/research/2026-11-02-pr-9-1-http-skill-executor.md`
- Profile: Full 5-Step Pipeline, approved by the developer on 2026-11-02.
- Outcome: a complete, reusable HTTP executor runtime with explicit input mapping,
  captured target settings, three authentication modes, bounded faithful responses,
  and application-owned transport lifetime, proved in Sidecar tests.
- Starting state: `main`, `b136e0ac9f24795fbac9bb0e935aa9d26581b819`;
  research is the only pre-existing untracked change. Preserve unrelated changes
  appearing after this plan.

## Current state

`rest/GenericRestSkillHandler.java` imports the removed REST SPI and resolves
routes by skill name and generation. `RestRouteLoader` implements separate route
YAML, two methods, implicit remaining-input mapping and executor SSL bundles.
`RestTargetClients` owns a pool per target and generation. `BoundedRestResponse`
does not read HTTP error bodies and has different media/charset behavior.
`GenerationRestResources` also owns durable snapshot identity.

`configuration/ProviderCredentialCipher` and `storage/EncryptedCredential` already
provide AES-GCM encryption with identifier/version binding and external key
ownership. `EffectiveExecutionConfiguration.Candidate` retains these encrypted
records, but Framework's provider credential map does not resolve opaque executor
references. `StorageConfiguration.providerCredentialCipher` currently suppresses
the key in file mode. `EffectiveExecutionConfiguration`'s broad authored-header
scanner and startup/publication assembly still implement the obsolete schema.

The public Framework supplies annotated executors, immutable captured maps,
namespace-aware reload, admitted invocation, and generation retirement. Sidecar's
startup, management and catalog callers still use removed APIs. Research contains
the exact source anchors and public API evidence. No normal build has been run by
planning, and no Framework test is counted as Sidecar evidence.

## Desired end state and scope

In scope: HTTP-specific configuration/input validation, request mapping, transport,
credential use, response/error decoding, shared-client ownership, runtime docs and
the tests proving these behaviors through production code and public Framework APIs.

Out of scope: complete startup/management/storage/UI/bundle replacement, durable
generation correlation, final deployment fixture reconciliation, Framework changes,
release publication, commit, sync and push. These remain with tickets 9.2/9.3.
Retain their broken callers explicitly rather than invent temporary adapters.

Remove the obsolete runtime classes and replace their runtime-specific tests.
Do not delete downstream management/startup integration tests merely because they
are blocked. Removing tests whose entire contract was superseded is replacement,
not permission to disable relevant coverage.

## Active project guardrails

- Depend only on supported `ai.loomspan.api` types; retain the whole-project
  ArchUnit prohibition on internal/autoconfigure imports and Java `@SkillMethod`.
- Use the installed beta.9-SNAPSHOT and matching local checkout; do not rebuild
  Framework, add repositories or verify source/artifact revision metadata.
- Framework owns schema/RBAC/skill validation, captured generations and admitted
  lifetime. HTTP validation belongs in Sidecar and performs no network probes.
- Identity comes from Spring Security, never input. Preserve original token and
  expiration checks; configuration authorization owns passthrough recipient approval.
- Return application content unchanged. Infrastructure must not copy credentials,
  request bodies or raw secret-bearing configuration into diagnostics.
- Keep one Framework shutdown budget. Caddy owns mTLS; standard Boot server TLS
  remains supported independently of outbound executor transport.
- Product documentation states current behavior and limitations, without ticket
  tracking or promised future delivery. Internal artifacts record downstream owners.

## Implementation approach

### Stable public configuration

Use `@SkillExecutor(name = "http", namespace = "default", config = "http")`.
Skills select `executor: http` in the default namespace; shared configuration is
`loomspan.executor-config.default.http`. No general namespace management is added.
Use a new `ai.loomspan.sidecar.http` package so new code has no dependency on the
removed REST resource graph.

Operation keys are exactly `target`, `method`, `path`, `pathParams`, `queryParams`,
`body`, `recoverableStatuses`. Required nonblank target/method/path; optional maps
default empty; optional statuses default empty. Reject unknown keys and explicit
null in place of a declared map/body/list. Supported uppercase methods are GET,
POST, PUT, PATCH, DELETE. GET rejects any body declaration. Body is either
`{source: input}` or `{source: field, field: /pointer}` with no other fields.

The selected executor map has `targets` and optional `credentials`. Each named
target has `base-url`, optional `headers`, `auth`, `connect-timeout`, `read-timeout`,
`max-response-size`. Auth omission means `{mode: none}`; allowed mode names are
`none`, `fixed`, `caller-passthrough`. Unknown fields/modes fail. Non-fixed modes
reject credential-header references. Targets own limits; operation overrides fail.

Choose defaults of `5s` connect, `60s` read and `1MB` (1,048,576 bytes) response cap:
finite conservative values that avoid mandatory boilerplate. Parse durations and
sizes with standard Spring utilities. Require at least 1ms and at most
`Integer.MAX_VALUE` milliseconds, and 1 through `Integer.MAX_VALUE` bytes; reject
overflow, fractional-millisecond zero, nonpositive and nonfinite values.

### Captured fixed credentials

Fixed auth uses `auth: {mode: fixed, header-refs: {Authorization: serviceToken}}`.
References are case-sensitive existing credential identifiers, not expressions.
The captured executor map includes:

```yaml
credentials:
  serviceToken:
    version: version-1
    ciphertext: <existing EncryptedCredential ciphertext>
```

`HttpExecutorConfiguration.withCredentials(executorMap, encryptedRecords)` is a
small assembly helper which attaches copies of existing encrypted records to the
runtime map; it neither persists them nor reads a latest publication. It rejects
duplicates and malformed metadata. This runtime map is passed in complete YAML to
the public Framework preparation API. Sidecar interprets the references and uses
`ProviderCredentialCipher` to decrypt the captured envelope immediately before use.
No new encryption format, secret store, credential acquisition protocol or special
Framework channel is introduced. The envelope preserves captured identity/version
across publication without a secret cache, latest-secret lookup or plaintext
prepared YAML. Keep decrypted values request-local and redact `toString` for any
credential-bearing helper types. Catch cipher/header failures with safe messages.

Use the existing external `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` for this mode, including
file configuration: remove `StorageConfiguration`'s file-mode key suppression.
A missing key is allowed when no encrypted credential is used, and fails fixed
credential use. No environment fallback fills a missing captured reference.
The helper is testable independently; 9.2 owns injecting retained encrypted records
at startup/publication/restoration and redacted inspection/export. Runtime documents
explain the runtime schema and current activation limitation without claiming that
the existing management editor provisions it. This slice performs no data reset.

### Validation and mapping ownership

Add public reusable `HttpExecutorConfiguration` APIs to parse/validate selected
executor settings, validate an operation against those settings, and attach
encrypted records. Keep immutable typed results, with no captured map retained in
error causes. Invocation uses the same validators. Startup/management can call
these APIs on extracted maps without activating a skill, duplicating Framework
validation or calling the destination. Actual-value mapping belongs in
`HttpRequestMapper`; it returns a URI, method and optional serialized JSON bytes
with absence distinct from the four bytes `null`.

Use Jackson's existing JsonPointer/JsonNode traversal for all references, plus
strict RFC escaping validation (only `~0`/`~1`) and a required nonempty `/` prefix.
No expressions or wildcard expansion. Array indexing follows JSON Pointer rules;
out-of-range is missing. A literal property named `*` may be selected as a literal
key; it must never select multiple matches. Convert the captured JSON-compatible
input once, preserve explicit null, and reject unsupported/nonfinite leaves safely.
Framework remains responsible for required input schema validation.

Path placeholders occupy whole segments; every distinct placeholder has exactly
one mapping and every mapping is used (a repeated placeholder can reuse its
mapping). Keep the existing identifier spelling convention for placeholder names.
Reject missing/null/empty/object/array path selections, literal separators, `.`/
`..`, and recursively percent-encoded separators/traversal. Apply corresponding
checks to target base paths and static operation segments; reject malformed
percent escapes, user-info, non-HTTP(S), query/fragment, absolute skill URLs and
authority-form `//...` paths. Encode original selected values, never interpolate
an untrusted value into URI authority or parse it as a URL. Preserve validated
base-path prefixes and join with exactly one boundary slash. Static skill path
segments are authored text and encoded once as path segments after validation;
base URI raw path retains validated URL escaping. Document this distinction.

Query maps preserve authored order; each scalar array produces repeated names in
array order. Missing/null/empty array omits; empty string/zero/false remain. Reject
objects, nested arrays, null array elements and nonfinite numbers before transport.
Encode names and values as URI components (including literal `+`, `&`, `=` and `%`).
Body selection serializes the complete input or selected node without consuming
path/query values. Missing selection fails; explicit null remains JSON null.

### Header responsibilities

Use a single case-insensitive name/conflict validator. Names use HTTP token syntax;
values reject CR/LF/NUL and other invalid control characters. Reject differently
cased duplicate names and ordinary/auth collisions. Framing/authority headers
`Host`, `Content-Length`, `Transfer-Encoding` belong to the HTTP library and are
not configurable. Authentication-bearing `Authorization`, `Proxy-Authorization`
and `Cookie` do not belong in ordinary fixed headers; cookie login/session flows
are excluded. Fixed authentication header references can supply ordinary service
credential names including Authorization, but not framing, payload metadata or
cookie headers. Passthrough alone supplies its Authorization value.

Do not revive the old broad reserved-header list: authors may set Accept and
other ordinary metadata. A request with a JSON body defaults Content-Type to
application/json; an explicitly fixed Content-Type must be application/json or
application/*+json with UTF-8 (or no charset), otherwise reject that operation
before dispatch. Bodyless requests may keep an explicitly configured Content-Type.
Do not append multiple Content-Type/Authorization values. All headers are built
per request, with no mutable shared defaults. Semantically secret custom headers
belong in `header-refs`, even when their names cannot be detected automatically.

### Shared transport and response fidelity

`HttpExecutorClient` owns one application-wide Apache CloseableHttpClient/pool.
Use direct Apache classic request/response APIs to avoid Spring message conversion
or error-handler rewriting. Set connection-request, connect and response timeout
on each request/context from its captured target. The available deprecated
request-local connect setter is justified by actual library source support;
do not mutate pool/default settings or introduce per-target clients prematurely.
Keep ordinary default TLS/server/hostname validation and JVM trust facilities.
Disable retries, redirects and cookie management. Never apply client certificates
or executor SSL-bundle settings.

Read at most cap plus one decoded-entity byte for every response status. Abort or
discard the endpoint before closing an incomplete response so Apache cannot drain
unbounded unread content for reuse; the existing discard interceptor is evidence
for this mechanism. A fully consumed response may reuse its connection. Do not
trust Content-Length as the sole cap. Compression, if enabled by the library,
must remain bounded after decompression. Exceeding the cap is terminal.

`HttpExecutorResponse` decodes bytes with REPORT for malformed/unmappable input.
Empty bodies bypass media checks and return empty text. Missing type means UTF-8;
application/json and application/*+json require UTF-8; text/* uses declared charset
or UTF-8. Other/malformed types, unsupported charset and invalid bytes fail safely.
Do not trim, normalize, parse or reserialize returned content.

All 2xx succeed. Other status messages are `HTTP <numeric status>` followed by
`\n` plus complete unchanged body when nonempty. The numeric line avoids copying
untrusted reason text and satisfies the short-status-line requirement. Only listed
400..499 excluding 401/403 throw RecoverableSkillException after successful
bounded reading/decoding; everything else throws ordinary SkillException. Validate
status declarations as integral numbers in that range; reject duplicates and
out-of-range/forbidden declarations. No implicit retries or recovery promises.

Generated config/input/credential/transport errors never include raw values,
headers, tokens, body or entire maps. Do not attach an arbitrary transport/cipher
exception as a diagnostic cause if it could disclose infrastructure values; use
safe error categories. Preserve interruption where appropriate. Application error
bodies intentionally remain unchanged even if their owner includes sensitive text.

### Lifetime

Use application-owned `SmartLifecycle` phase 0 and an idempotent immediate close
fallback on bean destruction. Framework's higher phase drains/cuts off admitted
work first; the client does not handle ContextClosedEvent, start another drain,
listen for generations or own durable IDs. Verify ordering through public
admission/template/reload APIs and actual context close, including cutoff,
parent/child contexts and asynchronous event multicast. Do not import Framework
lifecycle classes to force ordering in tests. If public integration exposes a real
gap, escalate it rather than emulate Framework lifetime.

## Phase 1: Define the replacement contract and mapper

### Changes

- [x] Add `src/main/java/ai/loomspan/sidecar/http/HttpExecutorConfiguration.java`:
  immutable validated target/operation/auth/body settings, defaults, safe errors,
  `withCredentials` helper, and reusable network-free validation.
- [x] Add `src/main/java/ai/loomspan/sidecar/http/HttpRequestMapper.java`: strict
  pointers, path safety and encoding, repeated query values and explicit JSON body.
- [x] Add `src/test/java/ai/loomspan/sidecar/http/HttpExecutorConfigurationTest.java`
  and `HttpRequestMapperTest.java` with the contract and value matrix in the test plan.
- [x] Add `scripts/verify-http-executor.ps1` only if needed for reproducible isolated
  verification: derive dependencies from root pom; generate a temporary POM under
  target with absolute source/test paths and explicit includes of the HTTP runtime,
  the two reused credential classes and the HTTP tests. Use Maven compiler/Surefire;
  no root build profile, source stubs, skipped/disabled tests or Framework source
  compilation. Clearly label output as an isolated runtime check, not a full build.

### Automated verification

- [x] `./mvnw.cmd -q -DskipTests compile` records actual baseline/downstream errors.
- [x] `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-http-executor.ps1`
  (when isolation is required) compiles actual selected production source and runs
  its tests against installed dependencies, using clean isolated output directories.
  The first new test fails for missing capability before production implementation.
- [x] If ordinary compilation is already available, use normal Maven tests instead
  of adding isolation machinery. Do not broaden production scope to obtain green.

### Optional developer checks

None.

## Phase 2: Deliver dispatch, credentials and bounded responses

### Changes

- [x] Add `src/main/java/ai/loomspan/sidecar/http/HttpSkillExecutor.java`: annotated
  bean method consuming captured maps, current Spring JWT and request-local auth.
- [x] Add `HttpExecutorClient.java` and `HttpExecutorResponse.java` in that directory:
  reusable pool, per-request limits, bounded cleanup, strict decoding and exception
  classification. Client owns lifecycle and immediate idempotent cleanup.
- [x] Update `src/main/java/ai/loomspan/sidecar/storage/StorageConfiguration.java`
  only to make the existing external credential key available in both modes.
- [x] Delete all superseded runtime classes under `src/main/java/ai/loomspan/sidecar/rest/`:
  GenericRestSkillHandler, RestRouteLoader, RestRouteConfiguration,
  RestRouteCatalogValidator, RestTargetClients, BoundedRestResponse and
  GenerationRestResources. Do not supply replacement types for their old callers.
- [x] Replace `GenericRestSkillHandlerTest`, `RestRouteLoaderTest` and
  `RestTransportTlsIntegrationTest` with the new configuration/mapping/transport
  tests. Replace the HTTP runtime lifecycle scenarios in
  `RestHandlerLifecycleIntegrationTest` with `http/HttpExecutorLifecycleIntegrationTest`.
  Keep downstream startup/restart/management/generation tests present for 9.2/9.3.
- [x] Add `src/test/java/ai/loomspan/sidecar/http/HttpSkillExecutorTest.java`,
  `HttpExecutorTlsIntegrationTest.java` and package-local deterministic loopback
  helpers. Reuse the actual cipher and encrypted records in fixed-auth tests.

### Automated verification

- [x] Isolated verification command above runs the full new HTTP package test set
  with real Apache transport, no external services or paid model calls.
- [x] Concurrent same-host requests show separate short/long target limits and
  distinct headers/JWTs; mock socket boundary verifies captured connect values
  and single-attempt behavior.
- [x] Local HTTPS fixture proves trusted success plus untrusted certificate and
  hostname mismatch rejection using standard JVM trust, without permissive managers.
- [x] Slow oversized success/error streams fail promptly before fixture release.

### Optional developer checks

None; application-specific live destination/idempotency checks are not routine gates.

## Phase 3: Prove public integration, document and record downstream blockers

### Changes

- [x] Add `src/test/java/ai/loomspan/sidecar/http/HttpExecutorIntegrationTest.java`
  and `HttpExecutorLifecycleIntegrationTest.java`: minimal standard Boot test
  application imports actual Sidecar HTTP beans and enables normal automatic
  configuration, without Sidecar's unfinished management/startup graph. Publish
  YAML with public SkillReloader/ExecutionConfiguration, invoke through public
  SkillTemplate and SkillInvocationHandoff; no autoconfigure/internal imports or
  Framework bean replacement. This proves runtime integration, not full Sidecar
  application startup.
- [x] Add `HttpExecutorArchitectureTest.java` in the HTTP test package for the
  isolated runtime's production/test classes, forbidding unsupported Framework
  dependencies and Java SkillMethod declarations. Retain the existing broader
  `architecture/SupportedLoomspanApiArchitectureTest.java` unchanged for final suite.
- [x] Add `docs/http-executor.md` covering exact schema, defaults, request rules,
  captured credential helper, auth ownership, results/error fidelity, one-attempt
  ambiguity, TLS and current activation limitations.
- [x] Update HTTP runtime claims/links in `docs/architecture.md`, `docs/operations.md`,
  `docs/remote-recipes.md` and README as needed; remove executor SSL-bundle/client
  certificate claims while preserving Boot inbound TLS. Replace the core obsolete
  runtime contract in `agent-skills/loomspan-sidecar-authoring/references/rest-configuration.md`
  with current HTTP executor guidance or a direct reference to the new contract.
  Do not pretend current startup/management/deployment examples activate it.
  Consolidated editor/export/deployment example replacement belongs to 9.2; mark
  unsupported workflows' present limitation clearly rather than keeping a false recipe.
- [x] Write `ai/thoughts/verification/2026-11-02-pr-9-1-http-skill-executor.md` with
  exact commands/results, all acceptance mappings and blocked checks/owners.
  Update plan/testing checkboxes only when actually backed by evidence.

### Automated verification

- [x] `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-http-executor.ps1`
  runs all new runtime unit/integration/TLS/lifecycle/architecture tests (if needed).
- [x] `./mvnw.cmd -q test` records the broadest safe normal-build result; retain
  exact first failures and affected suites rather than declaring them passing.
- [x] `git diff --check` passes; secret/import scans inspect changed production,
  tests and docs. Relevant documentation check scripts run when their scope changed;
  document failures due to still-obsolete downstream fixtures rather than disabling.

### Optional developer checks

None required. Hosted CI is deferred until Framework publication under release policy.

## Test strategy

Pure configuration/mapping tests cover broad value matrices cheaply. Real local
HTTP fixtures prove the complete production dispatch/response path. Public-API
Framework integration proves annotation discovery, YAML binding, standard tool
feedback, captured auth/settings and lifecycle. A local model protocol fixture
returns deterministic tool calls; no model mocks or paid services are needed.
TLS uses generated temporary certificates and standard trust configuration.
Use latches and bounded waits, not fragile elapsed-time races, for concurrency and
shutdown. The companion testing plan specifies cases and commands.

## Acceptance-criteria traceability

| Ticket criterion (in ticket order) | Planned code evidence | Planned executable evidence |
| --- | --- | --- |
| 1: GET/bodyless POST/whole input/nested body | HttpRequestMapper, HttpSkillExecutor | Mapper matrix and real wire assertions |
| 2: five methods, GET/body, null vs absence | Operation/body validation, optional request entity | Method matrix and server-observed absent versus null bytes |
| 3: pointers and all path/query values | Jackson pointer traversal, destination checks | Escapes/index/missing/scalar/array matrices |
| 4: prefix/encoding/host/traversal | Validated base/path and component encoding | Wire raw URI plus zero dispatch on invalid cases |
| 5: config/status validation without network | HttpExecutorConfiguration reusable methods | No-client pure tests, invalid invocation request count zero |
| 6: schema/defaults reusable by management | Public validators/withCredentials and docs | Default/strict-schema tests, documented production examples |
| 7: no legacy adapter/language/internal API | Remove old runtime; annotated bean | Source scan and isolated ArchUnit; whole-project ArchUnit retained but may block |
| 8: YAML public API integration | Actual annotated Sidecar bean | Public SkillReloader publication and SkillTemplate invocation |
| 9: auth modes, expiry/fidelity/isolation | Captured encrypted records and per-request JWT/headers | Actual cipher, zero-dispatch missing/expired cases, concurrent callers/cookies |
| 10: HTTP/HTTPS/limits/no retries/redirect/cookies | Shared Apache client and bounded abort | Loopback HTTP/TLS/socket/single-attempt/slow-stream tests |
| 11: success/media/charset fidelity | HttpExecutorResponse | Success and error decode matrix; exact whitespace/newlines |
| 12: exceptions and safe complete errors | Response classification and safe generated messages | Status matrix, oversize/invalid listed errors remain terminal, secret marker diagnostics |
| 13: direct and standard model feedback | Actual runtime through public Framework | Local provider fixture verifies exact feedback and terminal no-continuation |
| 14: ownership/concurrency/capture/shutdown | One client, request config, phase 0 close | Retained admission across publish, concurrent limits, normal/cutoff/context-close tests |
| 15: unit docs/tests and explicit blockers | Runtime docs and verification artifact | New suite plus normal build attempt with exact downstream ownership |

## Risks, downstream blockers and recovery

Full application compilation/startup remains blocked when old callers reference
deleted runtime types, `SkillKind.REST`, namespace-less reload methods and old
durable route fields. 9.2 owns startup/management/storage/bundle/UI/correlation
replacement and its tests; 9.3 owns composed fixture/catalog/API reconciliation
and all final suites. Record actual failures, not just this predicted list.
The isolated test command is scoped evidence only and must not become a green
release/CI substitute. 9.3 should remove temporary isolation machinery once normal
verification works; no release is authorized here.

Existing startup/publication cannot yet call the reusable HTTP validators or attach
captured credentials until 9.2 is implemented. This is the ticket's expressly
permitted integration boundary. Public runtime integration tests must still pass;
do not classify missing runtime requirements as downstream blockers. A missing
public Framework capability or inability to establish ticket behavior requires
escalation, not a substitute internal API.

No persisted schema changes or development-data reset are needed for this runtime
slice; obsolete data is neither read through an adapter nor deleted. 9.2 owns the
reset decision for its persisted-format replacement. Recover by reverting this
ticket's source changes through the developer's normal workflow; no migration or
data rollback machinery is introduced. HTTP side effects are never rolled back
by Sidecar; timeout/lost response remains ambiguous.

## References

- Ticket, research and `ai/thoughts/phases/http-skill-executor-design-notes.md`.
- `ai/thoughts/tickets/2026-11-02-pr-9-2-http-configuration-management-and-authoring.md`.
- Matching local Framework public executor/reload documentation and API records.
- Local Apache 5.6.1 request config/execution source cited in research.

## Implementation receipt

Step 4 evidence is recorded in `ai/thoughts/verification/2026-11-02-pr-9-1-http-skill-executor.md`.
Checked command items include recorded blocked/conditional branches, not claims that
normal compilation or the complete suite passed. The isolated runtime suite is the
executable evidence for this slice. Full application startup, managed activation,
queue shutdown and full-project architecture checks remain blocked with explicit
9.2/9.3 ownership. No optional live checks were performed.
