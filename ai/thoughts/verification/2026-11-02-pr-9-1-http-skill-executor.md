# HTTP executor implementation and verification

Date: 2026-11-02 (America/Los_Angeles). Pipeline Step 4, profile `full`.
Ticket: `ai/thoughts/tickets/2026-11-02-pr-9-1-http-skill-executor.md`.
Plans: `ai/thoughts/plans/2026-11-02-pr-9-1-http-skill-executor.md` and its
`-testing.md` companion. Starting commit: `b136e0ac9f24795fbac9bb0e935aa9d26581b819`.
Only prior research/planning artifacts were present when implementation began.

**Development replacement policy:** removed the obsolete runtime and its
superseded tests directly; no adapters, stubs, dual routes, disabled tests,
Framework source edits or migration machinery. Applied the
[design lens](../design-lens.md#simplicity-and-technical-debt). No development data
was reset or deleted. No commit, sync, push, release or live service call occurred.

## Implementation

- `http/HttpExecutorConfiguration`: public network-free target/operation validation,
  final schema/defaults and captured encrypted credential assembly.
- `http/HttpRequestMapper`: public actual-input validation/construction, Jackson
  JSON Pointers, safe URI components, repeated queries and optional JSON body.
- `http/HttpSkillExecutor`: supported annotated `http` bean consuming captured
  invocation maps; fixed encrypted service headers or captured original caller JWT.
- `http/HttpExecutorClient`: one application-owned Apache pool, immutable
  request-local limits, no retries/redirects/cookies, phase-zero immediate cleanup.
- `http/HttpExecutorResponse`: bounded success/error/decompressed reading, strict
  media/charset decoding, faithful text and explicit recoverable classification.
- Existing external credential key is available in file and database modes.
- Deleted all seven obsolete `rest` runtime types and replaced their four runtime
  test files; downstream management/startup/generation tests remain present.
- Runtime guidance and packaged HTTP reference document current behavior and the
  activation limitation; the authoring metadata matches the installed snapshot.

Paths above are relative to `src/main/java/ai/loomspan/sidecar` unless stated.

## Commands and actual results

1. `./mvnw.cmd -q -DskipTests compile`: initially returned success against old
   incremental output; this was not accepted as fresh compilation evidence.
2. `./mvnw.cmd -q clean compile`: FAIL at the baseline, confirming missing
   RestSkillHandler/RestSkillInvocation, SkillKind.REST and namespace-less reload
   and snapshot calls. Raw output was captured in `http-baseline-compile.log`.
3. `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-http-executor.ps1`:
   intended RED after adding the first wire test and before adding runtime classes;
   test compilation failed specifically for missing HttpExecutorClient and
   HttpSkillExecutor. An earlier generated-POM syntax error was corrected before
   obtaining this result. Raw output: `http-red.log`.
4. The same isolated command: PASS, 61 tests, zero failures/errors/skips. Reports
   are under `target/http-executor-check/target/surefire-reports` (untracked build
   output). Counts: configuration 27, mapper 12, transport 14, public Framework
   integration 2, lifecycle 4, TLS 1, architecture 1. Raw output: `http-check.log`.
5. `./mvnw.cmd -q test`: FAIL before tests at retained downstream compilation
   boundaries described below. Raw output: `http-full-build.log`. This is not a
   passing whole-application build or a test result.
6. `python scripts/test_agent_skills.py`: PASS, 6 tests. The metadata-negative test
   originally hard-coded beta.8, so its mutation stopped changing the updated
   metadata; replaced that mutation with a generic metadata-line replacement,
   retaining its rejection assertion. No coverage was disabled.
7. `git diff --check`: PASS (Git reports only ordinary LF/CRLF conversion advice).
8. `rg -n '^import ai\.loomspan\.(internal|autoconfigure)|@SkillMethod' src/main/java/ai/loomspan/sidecar/http src/test/java/ai/loomspan/sidecar/http`:
   no matches. The executed ArchUnit rule additionally checks bytecode dependencies
   in the isolated HTTP/test packages and reused credential packages. The existing
   whole-project architecture test remains unchanged and blocked by compilation.
9. `./mvnw.cmd -q -f target/http-executor-check/pom.xml "-Dtest=HttpExecutorTlsIntegrationTest" test`:
   PASS, 1 test, zero failures/errors/skips, after the final TLS-fixture change
   reinitialized the standard JVM default from trust-store properties and restored
   it afterward. Other production/test source is unchanged from the passing 61-test
   run. Raw output: `http-tls-final.log`.

Intermediate fixture failures were fixed, not classified as passes: unauthenticated
single-argument test JWT constructor; missing startup executor subtree; publishable
provider credential reference syntax; qualified catalog lookup; ambiguous overloaded
test method reference; duplicate CLI timeout values; and a TLS fixture that changed
a trust-store property after default SSLContext initialization. The TLS fixture
uses standard TrustManagerFactory/JVM trust-store properties and restores its
previous global context/properties; no trust-all or hostname bypass exists.

## Isolated verification scope

The script derives dependencies/properties from the real root POM and creates a
temporary POM with absolute real source paths. It cleans its own Maven output.
Production includes are `ai/loomspan/sidecar/http/*.java`,
`ai/loomspan/sidecar/configuration/ProviderCredentialCipher.java` and
`ai/loomspan/sidecar/storage/EncryptedCredential.java`. Test includes are every
`ai/loomspan/sidecar/http/*.java`. Compiler/Surefire versions match the root build.
No generated compatibility source, Framework source compilation or default-build
exclusion is used. Test JVMs are independent to isolate global TLS fixtures.

The Boot fixture enables normal automatic configuration and imports actual Sidecar
HTTP beans. Its only beans are application Clock/cipher, not Framework substitutes.
It exercises installed snapshot/public API behavior independently of the unfinished
Sidecar management graph. The isolated check is temporary development evidence;
9.3 owns removing it when the normal complete build can replace it.

## Acceptance-criteria evidence

New test paths are relative to `src/test/java/ai/loomspan/sidecar/http`.

| Ticket criterion | Production and executable evidence |
| --- | --- |
| 1: representative requests | HttpSkillExecutorTest maps explicit nested pointers, whole input, GET, bodyless POST and field body onto actual loopback wire requests. |
| 2: methods/body/null | Configuration method/GET-body validation; allMethodsAndBodySelectionsReachWire covers five methods and absent versus JSON null entities. Mapper tests cover selected objects/arrays/scalars/null. |
| 3: pointer/path/query rules | Configuration and mapper matrices cover RFC escapes, arrays, missing, null, empty, ordered repeated values, zero/false, disallowed shapes and literal star property. |
| 4: destination/encoding | Validated scheme/authority/base paths and segment placeholders; raw URI assertions cover base prefix, Unicode/punctuation and traversal forms; invalid mapped input dispatch count stays zero. |
| 5: network-free validation | HttpExecutorConfigurationTest creates no client/server; malformed maps/methods/limits/auth/statuses fail safely. HttpRequestMapper performs actual-input validation before dispatch. |
| 6: reusable schema/docs | Public validators, mapper and withCredentials helper; documented defaults 5s/60s/1MB and schema in docs/http-executor.md plus self-contained packaged reference. |
| 7: no legacy/internal API | Seven old runtime files removed, one annotated entry, no route/name lookup. Executed ArchUnit tests enforce public boundary and no Java SkillMethod declarations. |
| 8: public YAML integration | HttpExecutorIntegrationTest publishes YAML through public SkillReloader and invokes actual bean through SkillTemplate, including schema and RBAC checks. |
| 9: authentication/isolation | Real cipher fixed-mode tests, missing-key/ref/control failures, strict expiry/no dispatch, concurrent distinct original JWT/header values and no cookie replay; public handoff retains original authentication and model-tool fixture observes original token. |
| 10: HTTP/HTTPS/limits/attempts | Actual loopback transport, generated-cert HTTPS trusted success/untrusted CA/hostname rejection, socket-boundary connect values, concurrent read limits, exact/decompressed limits, redirects/dropped connection single attempt, slow oversize success/error abandonment. |
| 11: faithful decoding | Success/error matrix for JSON/+json/text/no type, UTF-8 and declared Latin-1, malformed bytes, unsupported charset/type/non-UTF-8 JSON, whitespace/newlines and empty 204. |
| 12: errors | Exact complete HTTP status-plus-body matrix; listed/unlisted 4xx, auth errors, redirects and 5xx; oversized or undecodable listed responses stay terminal; generated failures have safe messages with no raw diagnostic causes. |
| 13: direct/model feedback | Public direct recoverable exception propagates; deterministic local OpenAI-compatible fixture observes exact complete recoverable message/correction feedback, public RECOVERABLE event, and no continuation for terminal HTTP statuses. No paid model calls. |
| 14: ownership/capture/lifetime | One reusable production pool with request-local config; same-host concurrent limits, captured encrypted versions/path through retained admission across publication; public normal/cutoff close plus async and child-context scenarios. No generation client/durable-ID coupling. |
| 15: docs/tests/blockers | Seven new test classes plus helpers and reproducible isolated command; runtime guidance updated, downstream failures preserved below. |

## Retained downstream blockers and owners

`./mvnw.cmd -q test` fails compiling:

- ExecutionCoordinator imports/constructor references to deleted
  GenerationRestResources (lines 26/63): 9.2 owns durable generation/snapshot
  correlation replacement and queue integration.
- RuntimeConfigurationService imports/fields/constructor and stage/publish/prune
  uses of GenerationRestResources and RestRouteCatalogValidator (lines 9/10,
  53/54/75, 116/179/273/320/504/505): 9.2 owns complete candidate validation,
  startup/publication/restoration and encrypted credential attachment.
- RuntimeConfigurationService namespace-less snapshot (103/178), validate (261)
  and prepare (476): 9.2 owns new complete namespace configuration flow.
- ExecutionController (44) and SkillCatalogController (20/24) namespace-less
  snapshot calls: 9.3 composed API/catalog alignment and final-suite reconciliation.

These are the ticket's expressly permitted intermediate integration boundaries.
The remaining old startup/restart/validation/generation/management fixtures are
not silently removed, disabled or adapted. They require 9.2/9.3 contract updates.
Full application startup, ExecutionCoordinator shutdown, managed persistence and
full-project architecture tests are NOT RUN because compilation stops first.
Runtime public admission/shutdown tests pass separately; they do not establish
full Sidecar startup/queue behavior. Existing management's broad header scanner
also cannot yet accept the new HTTP target schema; 9.2 owns that integration.

## Decisions and limits

The selected encrypted captured-record approach follows the plan, keeping
configuration generations independent of live credential lookup. No data reset
is required in this runtime slice. Actual-input construction is public for reuse
by management/startup consumers without dispatch. Library source confirms that
Apache 5.6.1 uses request-local connect timeouts at socket connect and TLS handshake,
and request-local response timeouts at the endpoint. Sidecar's concurrency/socket
and lifecycle tests, rather than Framework tests, establish the selected strategy.

There are no required optional manual/live checks. Hosted CI and release operations
are not part of this slice. Fresh independent Step 5 review is still required.
