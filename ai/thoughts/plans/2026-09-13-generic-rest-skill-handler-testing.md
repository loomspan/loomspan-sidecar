# Generic REST Skill Handler Testing Plan

## Change Summary

SC4 adds a required startup-loaded route document, exact route/catalog validation, one production `RestSkillHandler`, fixed GET/POST binding, target-specific authentication/TLS/timeouts, non-following/no-retry HTTP transport, streamed response limits and media handling, and client shutdown after framework completion/cutoff. Tests must replace the existing in-memory test handler with production transport where end-to-end behavior matters, while retaining cheaper focused tests for parser, binder, and response edge cases.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Route-file parsing | YAML duplicates or unknown fields are silently accepted; missing files/placeholders leak confusing or secret-bearing diagnostics | Strict loader tests over temporary/default/classpath resources and sanitized error assertions |
| Startup registration | Handler-to-catalog dependency creates a cycle, mappings are incomplete, or validation occurs after readiness | Real application-context success/failure tests using the public eager catalog and one production handler |
| URI and input binding | Leading route paths discard the base prefix; reserved/Unicode/dot/encoded values alter path structure; invalid values trigger I/O | Loopback request capture plus request counters for invalid path/query/non-JSON cases |
| Authentication | Static headers leak across targets; input controls headers; passthrough uses no/wrong identity | Captured headers per target and direct handler tests with/without `JwtAuthenticationToken` |
| HTTP behavior | Redirects or transient failures repeat side effects; connect/read timeout settings are ineffective | Redirect destination and request-count assertions plus deterministic connection/read failures |
| Response handling | Success/error bodies buffer without a cap; equality is rejected; media/charset rules are inconsistent; body content leaks in failures | Chunked exact/excess bodies, large non-2xx bodies, empty/204, JSON/text charset matrix, bounded diagnostic assertions |
| TLS isolation | The wrong trust/client identity succeeds, or one target's SSL configuration affects another | Local HTTPS servers requiring distinct in-process-generated test identities and Boot SSL bundles |
| Async identity/failure | Queue/nested execution loses the token; callback does not independently reject expiry; transport failures lose `SKILL_FAILURE` shape | Revised common application fixture with independently verifying JWT callback and polling assertions |
| Restart semantics | File edits mutate live routing/catalog or invalid edits are accepted on restart | Temporary mounted files exercised before and after a full context restart |
| Lifecycle/concurrency | Clients close before admitted nested work finishes, or cleanup adds an unbounded second drain | Latch-controlled context-close tests for normal completion and framework cutoff, plus existing admission/shutdown checks |
| Compatibility/public surface | Existing empty-skill contexts fail under the new required file or tests/code import framework internals | Explicit empty route fixture in all contexts, complete wrapper suite, and ArchUnit rule |
| Documentation/configuration | README diverges from actual binding, charset, token lifetime, or SC5 boundary | Review against executable examples, full build, and diff validation |

## Existing Coverage and Environment Constraints

The current suite uses JUnit 5, AssertJ, Mockito, `@SpringBootTest`, direct `SpringApplicationBuilder` contexts, `@TempDir`, and JDK loopback `HttpServer`. `AuthenticatedExecutionApiIntegrationTest` already supplies deterministic local model responses and `JwtTestTokens` supplies locally signed issuer/audience/subject/role/expiry variants. `ExecutionShutdownIntegrationTest` covers Sidecar's synchronous close listener/readiness gate, and `SupportedLoomspanApiArchitectureTest` scans both production and test packages for forbidden framework dependencies.

Current REST coverage stops at an imported in-memory `RestSkillHandler`; there is no route loader, outbound HTTP/TLS fixture, stream cap, restart, or REST-client lifecycle assertion. Adding the production handler would give the two REST application tests duplicate handlers unless their test configuration is removed. Context tests with no REST skills also currently omit a route file; they must use an explicit `targets: {}` / `routes: {}` fixture under the new mandatory contract.

The repository resolves Java 21, Boot 4.1.0, Spring Web 7.0.8, Jackson 3.1.4, and Apache HttpClient 5.6.1. `httpclient5` and Jackson YAML are already available; no WireMock/MockWebServer dependency exists. Prefer JDK `HttpServer`/`HttpsServer`, temporary files, counters, and latches. Generate test CA/server/client identities and PKCS#12 stores in-process with test-scoped Bouncy Castle dependencies, avoiding committed binary key material and calls to `keytool`; tests must not call external networks, providers, identity services, or production endpoints. Maven uses the developer-installed matching Loomspan `1.0.0-beta.4-SNAPSHOT` as directed by repository policy.

## Failing Test First

- Name: `startsWithRestSkillsUsingOneProductionHandlerAndCompletedCatalog`
- Type: Spring application-context integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestRouteStartupIntegrationTest.java`
- Arrange/Act/Assert: Mount the existing `echo-rest.yml`, supply a valid temporary route document targeting an unreachable loopback address, start `LoomspanSidecarApplication`, and assert the context contains exactly one `RestSkillHandler`, a completed REST descriptor and validated route without recording any outbound request.
- Expected pre-fix failure: The current checkout has no production `RestSkillHandler` or route loader, so framework REST registration fails startup with “found none” before the requested valid context can be obtained. After the smallest handler/configuration scaffold exists, the test continues to fail until route loading and late catalog validation are wired correctly.

## Tests to Add or Update

### 1. `loadsDefaultOverrideAndExplicitEmptyDocuments`

- Type: focused loader/configuration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestRouteLoaderTest.java`
- Proves: The property defaults to `file:/sidecar/rest-routes.yaml`; override resources are honored; a present document with exactly empty `targets` and `routes` freezes to empty maps.
- Inputs/fixture: `@TempDir` files and `src/test/resources/fixtures/rest-routes/empty.yaml`.
- Doubles or boundary isolation: `MockEnvironment`/standard resource loader and a stub `SslBundles`; no Spring context or network.
- Edge cases: Blank location, missing/unreadable resource, empty YAML stream, malformed root shape, missing one required map.

### 2. `rejectsDuplicateUnknownAndMalformedRouteConfigurationWithSafeDiagnostics`

- Type: parameterized focused loader test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestRouteLoaderTest.java`
- Proves: Duplicate target and route keys, unknown fields at every supported level, malformed YAML, unsupported methods, invalid path/base URL, nonpositive timeout/size, unknown target/bundle, and invalid auth/header combinations fail with resource/key/field context.
- Inputs/fixture: Inline temporary YAML cases with a sentinel static secret.
- Doubles or boundary isolation: Bundle lookup fake records requested names; loopback target is intentionally absent.
- Edge cases: GET/POST case normalization if supported by the model; PUT/PATCH/DELETE rejection; `//host`, scheme/query/fragment, static `.`/`..`, `%2e` traversal; HTTP(S)-only URLs; user-info; zero/negative duration/size; headers on `none`/`caller-passthrough` and missing static headers.

### 3. `resolvesEveryStringPlaceholderBeforeValidationWithoutLeakingSecrets`

- Type: focused loader test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestRouteLoaderTest.java`
- Proves: Spring environment placeholders resolve in target/header/route string fields before semantic checks; unresolved placeholders identify resource, field and placeholder; resolved secret values never appear in failures.
- Inputs/fixture: Environment properties for a base URL, bundle/name/path and static header; one absent placeholder; a sentinel resolved value that later causes validation failure.
- Doubles or boundary isolation: Standard configurable environment only.
- Edge cases: Multiple placeholders in one string, placeholder in a map value, unresolved placeholder nested under static headers.

### 4. `validatesExactRoutesAgainstTheCompletedPublicCatalog`

- Type: Spring application-context integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestRouteStartupIntegrationTest.java`
- Proves: One production handler starts with REST manifests without a dependency cycle; every REST descriptor has a route; every route names a REST descriptor and defined target; YAML/non-REST skills cannot be routed; all failures occur before a usable/readiness-up context; validation makes no remote request.
- Inputs/fixture: Existing REST/YAML manifests plus generated valid/missing/extra/wrong-kind/unknown-target route documents.
- Doubles or boundary isolation: Unreachable or request-counting loopback targets; context is closed after every case.
- Edge cases: No REST skills plus explicit empty maps, mixed YAML and REST catalog, shared target, multiple targets, exact case-sensitive names.

### 5. `bindsGetPathSegmentsAndRemainingScalarQueryValues`

- Type: focused production-handler integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: Whole-segment variables are consumed by exact name; remaining string/number/boolean values become query parameters; base paths survive trailing and nontrailing base slashes; Accept is `application/json, text/*`.
- Inputs/fixture: Local request-capture server and route `/expenses/{category}` under `/api`.
- Doubles or boundary isolation: Instantiate production loader/handler collaborators with only loopback clients.
- Edge cases: Unicode, spaces, slash, question/hash, percent, plus, ampersand, repeated query values across separate invocations, and deterministic input-map ordering where observable.

### 6. `rejectsUnsafeOrInvalidGetValuesBeforeAnyOutboundCall`

- Type: parameterized focused handler test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: Missing/null/list/map path fields and null/list/map remaining GET fields fail visibly; static or substituted dot/encoded traversal and attempted authority replacement never escape the configured host/base path; invalid inputs cause zero requests.
- Inputs/fixture: Counting server plus route/base/path/value matrices.
- Doubles or boundary isolation: Direct `RestSkillInvocation` calls avoid the inbound schema layer.
- Edge cases: `.`, `..`, `%2e%2e`, `../`, encoded slash/backslash, `//other-host`, and nested non-JSON leaves.

### 7. `serializesRemainingPostInputAsJsonObject`

- Type: focused production-handler integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: POST consumes path variables and sends the remaining object with JSON Content-Type; nulls, lists and nested string-keyed objects are retained; all-consumed input sends `{}`.
- Inputs/fixture: Capturing loopback server and Jackson 3 `ObjectMapper` assertions on the raw body.
- Doubles or boundary isolation: Direct invocation.
- Edge cases: Non-string nested map key, Spring `Resource`, stream/file/custom object, nonfinite floating number, and forbidden leaf nested in a list/map all fail before request.

### 8. `appliesAuthenticationIndependentlyPerTarget`

- Type: focused production-handler integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: `none` sends no configured credential, `static` sends only configured headers, passthrough sends exactly `Bearer <original-token>`, and one target's headers do not bleed to another; invocation input cannot become a header.
- Inputs/fixture: Three routes/targets and a capturing server; deterministic `JwtAuthenticationToken` installed in `SecurityContextHolder` for passthrough.
- Doubles or boundary isolation: Direct handler invocation and context cleanup in `finally`.
- Edge cases: No authentication, non-JWT authentication and cleared context in passthrough all produce visible `SkillException` before request; static `Authorization` remains allowed only as configured static data if header-name validation permits it.

### 9. `doesNotFollowRedirectsOrRetryFailures`

- Type: focused production-handler integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: Same-host and cross-host 3xx responses fail without reaching Location; non-2xx/transient/connection failures result in one attempt.
- Inputs/fixture: Source and destination request-counting servers, 301/302/307/308 cases, 500/503 cases, refused loopback port.
- Doubles or boundary isolation: Local Apache/Spring client stack with production configuration.
- Edge cases: Location omitted, same base path, cross-host destination, large redirect/error body.

### 10. `enforcesConnectAndReadTimeoutsPerTarget`

- Type: focused transport integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: Configured connect/read bounds take effect with the pinned client and one target's timeout does not mutate another.
- Inputs/fixture: A server that accepts then delays its body for read timeout; a refused loopback address for connection failure; a test Apache connection boundary that records the effective connect timeout; short and longer target settings.
- Doubles or boundary isolation: Loopback plus a package-visible client-factory seam whose test connection operator/socket strategy records the timeout Apache receives, with latches instead of arbitrary sleeps. The same production request configuration is used by the real-client read/refusal cases.
- Edge cases: Distinct targets deliver their own connect timeout to the Apache boundary, the delayed server enforces its own read timeout, timeout diagnostics are bounded/body-free, and interrupt status is preserved when applicable.

### 11. `streamsSuccessfulBodiesThroughTheConfiguredByteCap`

- Type: focused production-handler integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: Chunked body size equal to the limit succeeds; one byte over fails as soon as detected without full materialization; Content-Length is not the only enforcement mechanism.
- Inputs/fixture: Chunked UTF-8 JSON/text bodies written in multiple chunks with server-side progress counters/latches.
- Doubles or boundary isolation: Small byte caps and local server.
- Edge cases: Multibyte UTF-8 crossing chunk/cap boundaries, zero-byte body, misleading/absent Content-Length, and a producer that offers substantially more data after the first excess byte.

### 12. `returnsOnlySupportedSuccessfulMediaAsDocumentedText`

- Type: parameterized focused handler test
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java`
- Proves: Any bodyless 2xx including 204 without Content-Type returns `""`; nonempty `application/json`, structured `+json`, and `text/*` return decoded text unchanged; explicit charsets are honored and absent charsets use UTF-8; nonempty missing/unsupported/invalid charset fails.
- Inputs/fixture: Status/content-type/charset/body table including JSON whitespace and empty chunked responses.
- Doubles or boundary isolation: Local server responses only; handler does not parse JSON.
- Edge cases: 200 empty with unsupported/missing type, 204 with misleading headers, `application/problem+json`, `text/plain; charset=ISO-8859-1`, malformed encoded bytes, binary content.

### 13. `boundsNonSuccessDiagnosticsWithoutReadingOrLeakingTheBody`

- Type: focused handler and async execution tests
- Location: `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java` and `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: Non-2xx, timeout, oversize and unsupported content types throw deliberate bounded `SkillException` messages containing only permitted target/status/type facts; large/chunked error bodies are not buffered; polling exposes normal `FAILED`/`SKILL_FAILURE` with the same bounded primary message and no response-body text/stack trace.
- Inputs/fixture: Sentinel secret body much larger than the cap and each transport failure family.
- Doubles or boundary isolation: Direct handler checks establish exception type/message; real asynchronous API checks establish framework/Sidecar projection.
- Edge cases: Missing Content-Type/status context, very long configured target names/media parameters (validated or truncated to the chosen diagnostic bound), connection exception causes.

### 14. `usesTheSelectedSslBundleAndClientIdentityPerTarget`

- Type: local TLS integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestTransportTlsIntegrationTest.java`
- Proves: A server requiring client authentication accepts the target with the matching Boot bundle, rejects no/wrong bundle, and separate targets keep trust/key material isolated.
- Inputs/fixture: Test-scoped Bouncy Castle generation of separate CA/server/client identities and temporary PKCS#12 stores, exposed as standard Boot `SslBundle` instances without committed private keys.
- Doubles or boundary isolation: JDK `HttpsServer` on loopback with `needClientAuth`; no external CA or service.
- Edge cases: Trust-only bundle against client-auth server, wrong client identity, HTTP target unaffected by HTTPS settings, target A bundle cannot authorize target B.

### 15. `forwardsAndIndependentlyVerifiesTheOriginalCallerThroughQueueAndPlanner`

- Type: full application HTTP/JWT/queue/planner integration test
- Location: `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- Proves: JWT caller → async Sidecar POST → YAML planner → production REST leaf → callback host receives the identical token; callback independently verifies signature, issuer, audience and lifetime, maps subject/roles, and returns a result. A token that expires while queued is still accepted as captured local identity but is rejected by the callback and becomes `SKILL_FAILURE`; Sidecar neither refreshes nor adds a local handoff expiry check.
- Inputs/fixture: Existing `JwtTestTokens`, `echo-rest.yml`, `nested-rest.yml`, model-response queue and a callback server using Spring Security JWT decoder/validators and role conversion independently of the Sidecar request filter.
- Doubles or boundary isolation: All keys, model responses and callback hosts are local deterministic fixtures.
- Edge cases: Queued valid token, queued token expired before callback, same issuer/subject renewed token polling the retained failure, original role claim visible at host, no outbound body/token leakage in failure.

### 16. `keepsRouteAndCatalogSnapshotsUntilRestart`

- Type: application restart integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestRouteRestartIntegrationTest.java`
- Proves: Editing mounted skill and route files does not affect a running catalog/handler; full restart loads both valid edits; an invalid next version fails startup before readiness.
- Inputs/fixture: Temporary skill/route directories, two loopback response targets, and `SpringApplicationBuilder` contexts.
- Doubles or boundary isolation: Explicit close/recreate is the activation boundary; no watcher polling.
- Edge cases: Route-only mismatch, skill-only mismatch, invalid target after a previously valid running version.

### 17. `closesClientsOnlyAfterFrameworkCompletionOrCutoff`

- Type: application lifecycle/concurrency integration test
- Location: `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java`
- Proves: Context close immediately blocks/discards Sidecar admission as before; an admitted REST callback can finish using its client while the framework owns completion; the lower-phase client owner closes after completion. With a short `loomspan.shutdown.timeout`, blocked work is cut off and context/client cleanup finishes within a measured outer bound without another drain.
- Inputs/fixture: Latch-controlled callback, close on a separate thread/future, short deterministic framework budget, and a package-visible client-state probe or injectable client factory.
- Doubles or boundary isolation: Real Spring lifecycle and production client ownership; no internal framework bean names, types, phases or listener priority assertions.
- Edge cases: Normal completion just before deadline, cutoff while response is blocked, idempotent stop/destroy, no early close exception during admitted work.

### 18. `updatesExistingContextsForTheMandatorySingleRouteSource`

- Type: regression updates to existing application tests
- Location: `src/test/java/ai/loomspan/sidecar/LoomspanSidecarApplicationTest.java`, `src/test/java/ai/loomspan/sidecar/skill/MountedSkillRegistrationIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java`, `src/test/java/ai/loomspan/sidecar/execution/CustomRoleExecutionIntegrationTest.java`, and other contexts found by search
- Proves: Non-REST contexts explicitly provide the valid empty document, route files remain outside framework scans, and REST contexts use exactly the production handler while preserving existing JWT/management/role behavior.
- Inputs/fixture: `fixtures/rest-routes/empty.yaml` and test-specific production route files.
- Doubles or boundary isolation: Existing fixtures retained; only the obsolete handler bean is removed.
- Edge cases: Both `.yaml` and `.yml` skill patterns and route-file sibling scan exclusion.

### 19. `sidecarCodeAndTestsDependOnlyOnSupportedLoomspanPackages`

- Type: architecture regression test
- Location: `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java`
- Proves: New production/test classes retain the ban on `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`, declare no Java `@SkillMethod`, and use only the authorized `RestSkillHandler` plus public catalog/value types.
- Inputs/fixture: ArchUnit import of the complete Sidecar package.
- Doubles or boundary isolation: None.
- Edge cases: Test fixture imports are scanned as well as production code.

## Safe Verification Commands

- Focused: `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=RestRouteLoaderTest,RestRouteStartupIntegrationTest,GenericRestSkillHandlerTest,RestTransportTlsIntegrationTest' test`
- Related suite: `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=AuthenticatedExecutionApiIntegrationTest,CustomRoleExecutionIntegrationTest,RestRouteRestartIntegrationTest,RestHandlerLifecycleIntegrationTest,ExecutionShutdownIntegrationTest,MountedSkillRegistrationIntegrationTest,LoomspanSidecarApplicationTest,ManagementEndpointIntegrationTest,ConsoleSecurityIntegrationTest,SupportedLoomspanApiArchitectureTest' test`
- Full safe suite: `.\mvnw.cmd --batch-mode --no-transfer-progress verify`
- Documentation/diff hygiene: `git diff --check`

All commands are loopback/file-local. They use the repository wrapper and the developer-maintained local Loomspan snapshot; they do not rebuild framework source or contact a configured production service.

## Optional Developer Checks

- If the local host cannot deterministically produce a connect *timeout* distinct from immediate connection refusal, optionally exercise a non-production black-holed address with the documented short connect timeout and record the environment/result. The automated suite must still prove configured connection failure and read-timeout behavior; this optional observation must not call a production target.
- After automation passes, optionally run the documented mounted configuration against a developer-controlled non-production HTTPS/mTLS callback to confirm local certificate deployment. This is operational observation, not a substitute for the deterministic bundle/mTLS test.

## Exit Criteria

- [x] The planned red test fails for the intended reason before implementation, then starts with exactly one production handler and no network startup probe after implementation.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes through `.\mvnw.cmd --batch-mode --no-transfer-progress verify`.
- [x] Every ticket acceptance criterion maps to executable focused or application-level evidence in this plan and the implementation plan traceability table.
- [x] Invalid configuration/input, redirects, retries, response overflow, non-2xx bodies, TLS failures, and callback expiry are isolated to disposable local resources and cause no unintended live/destructive operation.
- [x] Exact response-cap equality, chunked excess, large error response handling, media/charset behavior, timeout behavior, target SSL isolation, and bounded body-free diagnostics are demonstrated with the pinned client versions.
- [x] The full JWT/queue/YAML-planner/production-REST/callback path proves independent host verification and visible `SKILL_FAILURE` on expiry before callback.
- [x] Restart and lifecycle tests prove immutable running state, startup rejection of invalid edits, no early client teardown, framework-owned completion/cutoff, and bounded resource release.
- [x] Existing admission, dispatch, polling, role configuration, management security, mounted-skill scanning, and architecture checks remain green.
- [x] README examples match the executable file/configuration, binding, auth, TLS, response/media, restart, token-lifetime and SC5 boundaries; `git diff --check` passes.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
