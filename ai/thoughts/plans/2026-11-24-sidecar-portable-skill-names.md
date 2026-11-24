# Sidecar portable skill names implementation plan

## Overview

- **Ticket** [Address Sidecar skills by namespace and local name](../tickets/2026-11-24-sidecar-portable-skill-names.md)
- **Research** [Sidecar portable skill naming](../research/2026-11-24-sidecar-portable-skill-names.md)
- **Outcome** An authenticated caller can discover a skill, use the returned namespace and local name to look it up and submit it, and poll the same identity and result by execution ID.
- **Baseline** SDK and example adoption is in local commit `a06d4d1`. The Sidecar ticket and shared naming handoff were untracked inputs at this baseline. Preserve them and leave this implementation uncommitted.

## Current State

- `SkillCatalogController` and `ExecutionController` look up a fixed namespace and guess a `default-` prefix from the presence of a hyphen. POST then passes the caller's original string to Framework validation and the coordinator. Polling serializes that string as `skillName`.
- `ExecutionCoordinator` captures the Framework generation at worker handoff after rechecking current trust and credential expiry. The queued record retains request identity and JWT through that boundary. Each state replacement in `ExecutionSnapshot` includes the original identity. Diagnostic events and output failures use distinct Framework-derived fields.
- `RuntimeConfigurationService.startFiles` records the Framework catalog's public metadata in a retained JSON inspection. Database startup, validation and publication already target `default`. `ConfigurationBundleV4` distinguishes fixed ordinal ZIP paths from diagnostic source labels and already checks actual entry names.
- The freshly installed pinned Framework snapshot accepts exact pairs on public validation and handoff calls. Its catalog and descriptor APIs expose exact local names. The two preceding authorities for malformed input and missing resources are already mapped by `ApiExceptionHandler`.

## Desired End State

- `GET /v1/namespaces/{namespace}/skills` lists the exact active namespace. `GET /v1/namespaces/{namespace}/skills/{localName}` and `POST /v1/namespaces/{namespace}/skills/{localName}/executions` resolve that exact pair. There is no inferred owner, normalization, combined-name parser or alias.
- A malformed component or pair yields 400. A syntactically valid absent namespace or local skill yields 404. Discovery still requires JWT authentication and remains unfiltered by per-skill roles. Submission still requires the roles and schema checked by Framework. Poll authorization remains based on issuer and subject.
- Accepted submissions return an ID and polling location. `GET /v1/executions/{id}` returns `namespace` and `localName` in queued, running and terminal responses. Catalog descriptors and current/retained file-startup skill inspection expose the components. Any human-readable qualified name uses `namespace:local-name`.
- Both configuration modes activate their authored documents in `default`. A physical definition path with uppercase characters is rejected within the Framework-controlled mount pattern. ZIP import rejects uppercase actual skill entries, while valid ordinal entries can retain mixed-case source labels.

## Scope

### In scope

- Execution HTTP mapping, exact Framework catalog precheck, request validation, queue identity, worker handoff, response metadata and file-startup skill inspection.
- Sidecar-owned test declarations, invocation calls, authored dependencies, structured planner/worker replies, provider wire fixtures and assertions. Update server-owned contract references and the deployment verifier's catalog path.
- Native verification against the locally installed Framework snapshot, including the already-committed quickstart, deterministic model and both authenticated HTTP callbacks.

### Out of scope

- Multi-namespace authoring or publication, Cloud, Framework code/dependencies, new extension contracts, live provider and deployment operations, release/CI publication, and external fixture-suite repository changes.
- SDK public API changes. The previous ticket provides the clients, docs and executable examples against the replacement contract.

## Active Project Guardrails

- Replace superseded development contracts directly. Apply the [design lens](../design-lens.md#simplicity-and-technical-debt) and keep implementation, tests and documentation proportional.
- Use supported `ai.loomspan.api` types only. Keep the existing `@SkillExecutor(name = "http", namespace = "default", config = "http")` bean method, the ArchUnit prohibition on internals and autoconfiguration, and the prohibition on Sidecar `@SkillMethod` skills.
- Preserve the boundary between authentication and business input. Keep one admission/record owner, trust rechecks before capture, one Framework shutdown budget and immutable terminal publication. Preserve raw diagnostic payloads, labels and execution result text. Product docs describe the current contract.

## Impact and Risk Analysis

- **Identity error precedence.** An absent owner does not provide a catalog on which to validate a pair. Validate the complete pair before lookup so an absent owner with a malformed local name or an overlength pair still returns 400. Snapshot's public `SkillReloadException` corresponds to an absent namespace in the pinned Framework implementation. Restrict the translation to snapshot itself.
- **Admission timing.** Discovery and submission precheck observe a catalog, request validation checks the current Framework state, and queued workers capture the available generation after trust rechecks. Keep these observations in their existing places. A complete publication can replace the available skills while work waits. Check the pair during worker handoff and preserve existing failure, correlation and release behavior when the selected generation or skill changes. The ordinary absent-at-lookup response remains 404.
- **Independent naming contexts.** Authored bare references are local, explicit authored and structured model references use colon pairs, provider tool names use the Framework wire mapping with two hyphens, and callback URL paths, business fields, roles and diagnostic source text have their own unchanged vocabulary. Inspect transformations by context.
- **Retained development data.** Previous execution records are in memory and disappear with restart. Old SQLite data can contain obsolete inspection JSON and obsolete authored names in selected, retained, or draft managed YAML. To verify the replacement contract or start a development instance with a clean history, use a fresh development database path and author/publish the current documents for database mode. An existing database whose old selected documents no longer validate cannot start that publication, and old inspections keep their historical JSON. A fresh database starts without the old accounts, sessions, drafts, credentials, publishes, and startup inspections. Preserve old files if needed and keep the external credential key separate. This ticket adds no migration or cleanup action and does not delete any existing database.

## Implementation Approach

Share the existing public catalog precheck through a small Sidecar API package lookup component used by both controllers. For namespace-only requests, `SkillReloader.snapshot(namespace)` provides component validation and lookup. For detail and POST, use the supported public `ValidatedSkill(namespace, localName, SkillKind.YAML)` value constructor for its pair preconditions before namespace lookup. Its kind argument has no lookup or execution role, and the temporary value is never returned as catalog metadata or accepted as proof of a publication. This serves malformed-pair checking even when the namespace is absent. Use `SkillCatalog.skill(localName)` to resolve the supplied local name and return the actual descriptor. Do not copy Framework's grammar, infer pair structure, or translate exception messages by matching them. Wrap only identity validation failures as `ApiInputException` with the Framework detail. Map missing namespace and missing skill through `ResourceNotFoundException`. Leave the body reader, authorization and Framework execution exceptions on their established paths.

Expose two string fields through `ExecutionRecord`, `ExecutionSnapshot` and `ExecutionCoordinator.admit`. Pass the actual descriptor's exact pair to `SkillTemplate.validate` and admission. The coordinator carries those components through all lifecycle states and into the existing worker-side `SkillInvocationHandoff.handoff(namespace, localName, input)`. No identity service or persistent admission token is needed.

## Phase 1: Replace HTTP and retained execution identity

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/api/SkillCatalogLookup.java` — share scoped namespace and exact descriptor lookup, Framework argument validation and the existing 400/404 exceptions.
- [x] `src/main/java/ai/loomspan/sidecar/api/SkillCatalogController.java` — bind both named path variables explicitly, list the requested snapshot and return the public descriptor for detail. Remove the single-name mappings.
- [x] `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java` — precheck the pair before reading the business body, validate the unchanged map using the actual descriptor's exact pair, admit it, and serialize `namespace` and `localName` on the existing poll path. Retain ID, status, location, dates, result, failure, configuration snapshot correlation, and event semantics.
- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java`, `ExecutionRecord.java`, `ExecutionSnapshot.java` in the same directory — replace the one-name field and pass both exact components across admission, handoff and snapshots. Preserve gate, owner, byte reservation, retention, timeouts, trust checks, generation registration and release.
- [x] `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java` — assert catalog-to-detail-to-submit-to-poll reuse for the hyphenated REST target, including during actual queue wait and for terminal outcomes. Check invalid versus absent identities, former routes and former prefix spelling, role enforcement, credential rechecks and snapshot correlation. Update `ExecutionCoordinatorTest.java`, `ExecutionDiagnosticsTest.java` and `TestSkillInvocationHandoff.java` in the same directory to use both handoff arguments and preserve capture observer evidence.

### Automated verification

- [x] `AuthenticatedExecutionApiIntegrationTest`, `ExecutionCoordinatorTest` and `ExecutionDiagnosticsTest` pass in the final full verification run against the installed pinned Framework.

### Optional developer checks

- None.

## Phase 2: Record file identity and adopt the contract in Sidecar-owned fixtures

### Changes

- [x] `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java` — `startFiles` records the descriptor's `namespace`, `localName` and `kind` for each skill. Retain the mode, safe inspection fields and startup correlation. Existing current and historical management outputs use the stored inspection. Keep database activation and file startup namespace explicitly `default`.
- [x] `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java` — exercise actual mounted startup with authorized file mode, verify persistent inspection identity and Framework descriptor, and reject an uppercase nested physical path under a lowercase glob root. Keep both provider and HTTP configuration capture coverage.
- [x] `src/test/java/ai/loomspan/sidecar/bundle/ConfigurationBundleV4Test.java` — exercise exact ZIP and manifest casing and preserved mixed-case label using the production import codec. Retain existing `ConfigurationBundleV4` entry policy. Running the management import checks verifies the codec is used at that boundary.
- [x] Convert declared names and their targets together in `src/test/resources/fixtures/execution-skills/echo-rest.yml`, `nested-rest.yml`, and `src/test/resources/fixtures/skills/mounted-yaml-skill.yaml` and `mounted-yml-skill.yml`. Audit inline YAML and directly invoked names in `ExecutionConfigurationCorrelationIntegrationTest`, `ExecutionOutputSchemaIntegrationTest`, `RuntimeConfigurationIntegrationTest`, `ConfigurationModeIntegrationTest`, `MountedSkillRegistrationIntegrationTest` and the management tests. Convert callable and executor references without modifying model aliases, URL paths, rbac roles or source labels.
- [x] `src/test/java/ai/loomspan/sidecar/http/HttpExecutorIntegrationTest.java` — update the authored dependency to the local name and assert the provider advertises and calls `default--call` where it previously used `default-call`. Update structured planner/worker fixture capability and tool names in `AuthenticatedExecutionApiIntegrationTest.java` and `ExecutionOutputSchemaIntegrationTest.java` to colon-qualified names. Retain raw exchange and producing skill diagnostics intact.
- [x] Update remaining Sidecar-owned test requests and public Framework calls in execution, HTTP, security and management tests. In `ExternalConfigurationFixturesIntegrationTest` update only the Sidecar-owned API vocabulary and current expectations. Its external fixture checkout still needs its own adoption and the opt-in property is excluded from this verification.

### Automated verification

- [x] `ConfigurationModeIntegrationTest`, `ExecutionConfigurationCorrelationIntegrationTest`, `ConfigurationBundleV4Test`, `ManagementImportBrowserIntegrationTest`, `HttpExecutorIntegrationTest`, `ExecutionOutputSchemaIntegrationTest` and `SupportedLoomspanApiArchitectureTest` pass in the final full verification run.

### Optional developer checks

- None.

## Phase 3: Update current contract and verify across the SDK seam

### Changes

- [x] `docs/operations.md` — describe the two polled identity fields separately from diagnostic producer identity. `agent-skills/loomspan-sidecar-authoring/references/integration.md` — confirm its examples and field shapes match the deployed HTTP controller. Describe exact grammar, component and pair limits, and 400/404 semantics if not already complete. `references/configuration-modes.md` — document file-startup inspection identity and physical path checks. `references/configuration-bundles.md` — document existing ordinal entry and diagnostic-label distinction.
- [x] Check `README.md`, `docs/integration.md`, `docs/http-executor.md`, `docs/quickstart.md`, the remaining setup/operations/architecture/Console/remote/production guidance and shipped examples against the handoff inventory. Update remaining server contract examples where stale. Keep `skillName` on diagnostic issue fields, the separate Framework observability URLs, endpoint paths inside authored HTTP configuration, and topology namespaces where applicable. The bundle fixture schemas describe archive manifests/configuration; update only if their server contract is affected.
- [x] `scripts/verify-deployment.py` — probe `/v1/namespaces/default/skills` in authenticated and unauthenticated assertions, preserving local verifier behavior. Add or update its existing Python test only where needed to establish the route.
- [x] Verify the native quickstart using the built Sidecar, checked-in planner/leaves/settings, shipped Python host, and shipped invoke client. Prefer the documented management publication workflow with local DB mode if it can be run using the existing tooling without a new harness. Native file mode using the same authored files is an acceptable seam check together with separate database publication and execution coverage. Verify both authorized callback records and ID-based polling. Where proportionate, exercise Java and both Python SDKs against the same real instance using their public operations.

### Automated verification

- [x] `mvn -o -B "-DargLine=-javaagent:$agent" verify` — broad Sidecar integration, architecture, browser checks and executable JAR packaging against the installed snapshot.
- [x] `python -m unittest discover -s scripts -p "test_*.py" -v` and `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p "test_*.py" -v` — verify deployment helper, authoring packaging and loopback client checks.
- [x] Native quickstart commands and assertions in the testing plan — one successful planner execution, both expected authenticated callbacks, exact identity on the ID poll, and the checked-in client result. Record the actual mode and commands.
- [x] `git diff --check a06d4d1` — check text changes and audit Sidecar-owned spelling references.

### Optional developer checks

- Docker Compose and the optional real-provider walkthrough, when a developer uses that setup. The native synthetic run and Sidecar integration suites are the local verification gate.

## Test Strategy

Use the existing authenticated Sidecar HTTP integration as the primary observable behavior test. Keep queue checks deterministic with its blocking callback, use public Framework types for all fixtures, and reuse configuration tests for startup, publication and data correlation. Cover archive path policy with real ZIP input and keep the native quickstart independent of external services. Framework tests from local installation prepare the dependency and do not establish Sidecar acceptance.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Discover and execute exact hyphenated identity, with poll and file inspection fields | Namespace routes, shared lookup, retention record, startup JSON | `AuthenticatedExecutionApiIntegrationTest`, `ConfigurationModeIntegrationTest`, native quickstart |
| Invalid 400, absent 404, existing authorization, former routes removed | Scoped translation and exact catalog lookup, unchanged JWT and role boundary | Authenticated malformed, missing, roleless and old-route assertions |
| Actual queued handoff and HTTP executor, file and database activation under `default` | Pair through existing coordinator/auth/capture, file and managed activation unchanged | Successful queued callback, trust and generation regression checks, configuration tests, native fixture |
| Uppercase physical paths rejected and exact diagnostic label preserved, documented routes and fields | Framework startup and existing Sidecar ZIP codec, updated contract docs and verifier | Actual file startup and archive read cases, deployment helper and documentation checks |

## Risks and Rollback/Recovery

The migration boundary is deliberate. New SDKs require the new server routes, and a dirty development database can contain old authored or inspection data. Use isolated state during verification. Reverting requires a matched previous server/client pair and its previously valid state. This run keeps the SDK commit local and leaves Sidecar edits visible for review.

## References

- [Portable naming handoff](../future/portable-skill-naming.md)
- [SDK and examples testing plan](2026-11-24-sdk-and-example-portable-skill-names-testing.md)
- [Sidecar API](../../../src/main/java/ai/loomspan/sidecar/api/)
- [Native quickstart fixture](../../../examples/quickstart/)
