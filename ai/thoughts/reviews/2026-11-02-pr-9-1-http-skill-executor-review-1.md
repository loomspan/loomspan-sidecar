# HTTP skill executor code review — Cycle 1

## Scope and repository state

Reviewed in pipeline mode, profile `full`, on 2026-11-02 (America/Los_Angeles).
Comparison base is the clean starting commit
`b136e0ac9f24795fbac9bb0e935aa9d26581b819` on `main`, supplied by the orchestrator
and consistent with the research state. Included staged, unstaged and untracked
ticket work: five HTTP runtime classes, seven HTTP test classes and two fixture
helpers, removal of seven REST runtime classes and four superseded test classes,
the credential-key bean change, isolated verification script, documentation and
packaged guidance, metadata-test adjustment, research, plans and verification
artifact. No unrelated developer changes were identified. There were no staged
changes. No previous review documents were read.

Reconstructed configuration parsing, pointer mapping, authentication, HTTP
dispatch and response consumption, error classification, shared-client lifetime
and public Framework invocation before comparing them with the ticket/plans.
Reviewed connected credential types, application callers and the explicit
startup/management boundary. No implementation artifacts were edited in this
review; this review record is the only change made by this context.

## Findings

No actionable findings.

## Findings resolved in this context

None; no fixes were required.

## Acceptance-criteria and plan conformance

Paths in the following table are under the production/test
`ai/loomspan/sidecar/http` packages unless otherwise stated. Evidence was checked
against source and independently executed tests, rather than plan checkmarks.

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| 1. Representative GET, bodyless/whole-input POST and nested body | `HttpRequestMapper.map`, optional entity in `HttpExecutorClient.execute` | `mapsExplicitPointersAndPreservesWholeInputBody`, `allMethodsAndBodySelectionsReachWire`, mapper body matrix | Implemented |
| 2. Five methods, GET restriction, body absence/null | `validateOperation` method/body validation and nullable request bytes | Configuration method checks, wire method/null matrix, `preservesBodySelectionTypesAndAbsence` | Implemented |
| 3. JSON Pointers and path/query values | Strict pointer escape check plus Jackson traversal; scalar path/query validation | Escaped pointer/array/omission cases, traversal matrix, invalid-shape and scalar cases | Implemented |
| 4. Prefix, encoding and destination safety | Validated base URI, whole-segment mappings, repeated security-marker decoding and component encoder | Raw URI assertions, invalid base and placeholder matrices, no-dispatch invalid path case | Implemented |
| 5. Network-free reusable validation | Public `HttpExecutorConfiguration.validate`/`validateOperation` and `HttpRequestMapper.map` | Pure settings/mapping tests and wire dispatch counter | Implemented |
| 6. Reusable documented schema/defaults | Shared validators and encrypted-record assembly helper; `docs/http-executor.md` | Defaults, schema/status/header validation and actual fixed-auth use | Implemented |
| 7. No legacy adapter or unsupported dependency | Removed obsolete runtime; one annotated entry consumes captured maps | Executed `HttpExecutorArchitectureTest`; import/legacy inspection; original broad architecture rule retained | Implemented within slice |
| 8. Public YAML execution | `HttpSkillExecutor.execute` annotated for default/http | `yamlExecutorUsesCapturedSettingsCredentialsAndAuthentication` publishes real YAML through public APIs and invokes actual production beans | Implemented |
| 9. Authentication, expiry and isolation | Request-local headers, existing cipher, authenticated JWT with strict expiry | Fixed encrypted versions and failures; missing/expired JWT no-dispatch; concurrent distinct original bearer/header values and no cookies; public handoff/tool JWT | Implemented |
| 10. Transport, TLS, finite limits, one attempt | One Apache pool, request-local timeouts, retries/redirect/cookies disabled, immediate response/client close | Socket-boundary timeout/attempt assertions, concurrent read isolation, redirect/drop checks, real trust/SAN tests, exact/decompressed/slow-oversize tests | Implemented |
| 11. Faithful success/error decoding | Bounded bytes and strict REPORT decoder; explicit media/charset selection | Success/error media matrix, malformed bytes/charset/type, empty body and exact whitespace | Implemented |
| 12. Terminal/recoverable errors and safe messages | Status classification after size/decoding; generated failures omit raw values/causes | Exact status/body assertions, listed/unlisted/auth/redirect/server errors, oversized listed errors remain terminal, credential/transport safe-message checks | Implemented |
| 13. Direct and model feedback through public API | Production executor with supported exceptions | Direct recoverable propagation; local OpenAI-compatible fixture receives exact correction message; public RECOVERABLE event; terminal statuses stop continuation | Implemented |
| 14. Capture/concurrency/client lifetime | Captured invocation maps; request-local timeout settings; phase-zero SmartLifecycle/immediate idempotent close | Retained admission across publish with distinct credential versions/path; concurrent timeouts; normal/cutoff shutdown, async events and child context | Implemented |
| 15. Unit documentation/tests and explicit downstream blockers | Updated runtime/operator/packaged guidance; verification artifact identifies owners | All new tests executed; packaged checks executed; normal-build blocker independently reproduced | Implemented within slice |

The final schema, defaults, captured encrypted records and application-owned pool
follow the plan. The isolated fixture uses normal Boot automatic configuration
with the actual Sidecar HTTP beans, public Framework publication/admission APIs
and no replacement Framework beans. It proves runtime integration, not complete
Sidecar startup or durable management. The script compiles actual selected source
with dependencies derived from the root POM; it introduces no compatibility
sources, disabled tests or normal-build exclusions.

## Active project guardrails

- Destructive replacement and the design lens are applied: obsolete runtime and
  superseded tests are removed, with no adapters, dual path, migration machinery
  or data reset. Retained downstream tests are not disabled.
- Runtime and test dependencies remain within the supported `ai.loomspan.api`
  surface. The executed scoped ArchUnit test forbids internal/autoconfigure
  dependencies and Java `@SkillMethod`; the original whole-project rule remains.
- Tests use the installed beta.9 snapshot and local loopback fixtures. No
  Framework rebuild/source modification, paid model call, live external service,
  commit, push, publication or deployed-data operation occurred.
- Authentication stays outside inputs. Fixed secrets are decrypted per request
  from captured encrypted records; caller authentication is propagated by public
  Framework paths. Generated error paths do not append secret values or unsafe
  causes; destination-owned response content remains unchanged.
- One application-owned client follows Framework shutdown handling without a
  second drain or generation/durable-ID resource layer. Default TLS validation
  remains enabled; no executor SSL bundle or certificate bypass was introduced.
- Product documentation describes actual executor behavior and prominently states
  the current startup/management activation limitation. Downstream ownership and
  verification receipts stay in internal artifacts.

## Open questions and assumptions

None requiring a developer decision. The ticket explicitly authorizes the
intermediate startup/management build break and assigns full composition to the
following slices; this is not a runtime acceptance exception.

## Verification results

- PASS — `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-http-executor.ps1`
  independently rebuilt the selected runtime and ran 61 tests, zero failures,
  errors or skips: architecture 1, configuration 27, public integration 2,
  lifecycle 4, TLS 1, mapper 12, transport 14. Reports are in
  `target/http-executor-check/target/surefire-reports`; review execution output is
  `target/http-review-1-tests.log`.
- PASS — `python scripts/test_agent_skills.py`: 6 tests. The generic metadata
  mutation preserves the negative test's original assertion.
- PASS — `git diff --check`: no whitespace errors; ordinary LF/CRLF advice only.
- PASS — `rg -n 'ai\.loomspan\.(internal|autoconfigure)|@SkillMethod|logger|\.log\(' src/main/java/ai/loomspan/sidecar/http src/test/java/ai/loomspan/sidecar/http`:
  only the intended forbidden-package assertion strings match; no unsupported
  imports, Java skill declarations or runtime logging paths were found.
- FAIL — `./mvnw.cmd -q test`: compilation stops before tests, independently
  reproduced in `target/http-review-1-normal.log`. `ExecutionCoordinator` still
  references removed `GenerationRestResources`; `RuntimeConfigurationService`
  references removed resources/catalog validator and namespace-less
  snapshot/validate/prepare calls; `ExecutionController` and
  `SkillCatalogController` retain namespace-less snapshot calls. These match the
  explicitly retained downstream boundaries, not defects in the replacement
  runtime. Management/startup/correlation replacement is owned by 9.2;
  composed API/catalog/fixture and complete-suite reconciliation is owned by 9.3.
- NOT RUN — full application startup, queue/management/persistence integration
  and the whole-project architecture suite: normal compilation stops first.
  Scoped passing runtime evidence does not establish those composed behaviors.

## Residual risks and optional developer checks

The checkout cannot yet build or activate this executor through the full Sidecar
startup/management graph. The retained downstream callers/tests must be replaced
and the normal full suite, startup, queue shutdown and whole-project architecture
checks must pass in the owning slices. The isolated script is development evidence
and must not substitute for final release/CI verification. No additional manual
or configured live-environment check is required for this runtime slice.

## Disposition

`clean`: independent review found no actionable issues, made no implementation
changes and completed sufficient executable verification for this ticket's runtime
scope. The explicitly authorized downstream composition limitations remain visible.
