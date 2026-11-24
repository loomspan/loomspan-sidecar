# Portable skill naming in Framework PR 37

Framework's completed contract is the basis for the next Sidecar planning context. Sidecar adoption requires its own implementation and public-entry-point tests. Use the [Sidecar design lens](../design-lens.md) and the closed supported `ai.loomspan.api` Java surface. Adopt the contract directly across the undeployed products, remove superseded overloads and fallbacks, and write current-state documentation without migration guidance.

## Identity and declaration contract

Namespace and local name each match the entire ASCII pattern `[a-z][a-z0-9]*(?:-[a-z0-9]+)*`, contain 1–64 characters and exclude either substring `anthropic` or `claude`. Validate supplied spelling exactly. Both callables and reference-only executors satisfy `namespace.length() + 2 + localName.length() <= 64`. Thus `order-support` leaves 49 local-name characters. `lookup-3pl` is valid.

Startup YAML belongs to `default`, including nested directories. Supplied YAML belongs to the namespace passed to `SkillReloader`. Java `@SkillMethod` and `@SkillExecutor` take their declared namespace, defaulting to `default`. An explicit nonempty `name` is used verbatim, otherwise the method name is used verbatim. A camelCase Java method such as `findOrder` must supply `name = "find-order"` or be renamed. Startup errors identify the offending method and naming remedy.

Callable registration is unique by pair across YAML and Java declarations. Executor registration has its own index and uniqueness rule. A callable and executor can share the same pair, and different owners can share a local name.

## Supported root invocation and retained admission

The six `SkillTemplate` operations are:

```java
void validate(String namespace, String localName, Object input);
void validate(String namespace, String localName, Map<String, Object> input);
String invoke(String namespace, String localName, Object input);
String invoke(String namespace, String localName, Map<String, Object> input);
String invoke(String namespace, String localName, Object input,
              Consumer<SkillExecutionView> observer);
String invoke(String namespace, String localName, Map<String, Object> input,
              Consumer<SkillExecutionView> observer);
```

The two `SkillInvocationHandoff` operations are:

```java
AdmittedSkillInvocation handoff(String namespace, String localName, Object input);
AdmittedSkillInvocation handoff(String namespace, String localName,
                                Map<String, Object> input);
```

Every operation validates both components and selects that exact pair under existing authorization. Missing owners/targets end lookup. The handoff captures reachable namespace generations before input conversion/validation, captures the current authentication and checks authorization again during execution. `AdmittedSkillInvocation.generationId()` exposes the entry's captured generation. Its `invoke()` and `invoke(Consumer<SkillExecutionView>)` are single-use. Hosts call idempotent `release()` when abandoning pending work. Retained work uses captured metadata across publication/removal and obeys Framework shutdown cutoff.

[Facade source](../../../../loomspan-framework/src/main/java/ai/loomspan/api/SkillTemplate.java), [handoff source](../../../../loomspan-framework/src/main/java/ai/loomspan/api/SkillInvocationHandoff.java), [admission source](../../../../loomspan-framework/src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java), [root contract tests](../../../../loomspan-framework/src/test/java/ai/loomspan/internal/skill/SkillReachableAdmissionTest.java).

## Catalog, validation and direct metadata reuse

`reloader.snapshot("orders")` selects one explicit namespace. `catalog.skill("lookup")` accepts a local name. `catalog.skill("orders:lookup")` fails validation. Missing local names return `Optional.empty()`. `ValidatedSkill` and `SkillDescriptor` both expose `namespace()`, `localName()` and derived `qualifiedName()`. For `orders:lookup`, those values are `orders`, `lookup` and `orders:lookup`.

The following uses only supported API. `documents` supplies a complete valid namespace proposal, `configuration` its complete `ExecutionConfiguration`, and `input` a valid map. Successful validation describes that proposal. Publication is required before invoking its declarations.

```java
import ai.loomspan.api.*;
import java.util.Map;

SkillValidationResult checked = reloader.validate("orders", documents, configuration);
if (checked.valid()) {
    ValidatedSkill validated = checked.skills().stream()
            .filter(skill -> skill.localName().equals("lookup"))
            .findFirst().orElseThrow();
    try (PreparedSkillUpdate update = reloader.prepare("orders", documents, configuration)) {
        reloader.publish(update);
    }
    String validatedResult = template.invoke(validated.namespace(), validated.localName(), input);
    SkillDescriptor descriptor = reloader.snapshot("orders").skill("lookup").orElseThrow();
    String catalogResult = template.invoke(descriptor.namespace(), descriptor.localName(), input);
    AdmittedSkillInvocation pending = handoffs.handoff(
            descriptor.namespace(), descriptor.localName(), input);
    try {
        String retainedResult = pending.invoke();
    } finally {
        pending.release();
    }
}
```

[Catalog API](../../../../loomspan-framework/src/main/java/ai/loomspan/api/SkillCatalog.java), [descriptor](../../../../loomspan-framework/src/main/java/ai/loomspan/api/SkillDescriptor.java), [validation record](../../../../loomspan-framework/src/main/java/ai/loomspan/api/ValidatedSkill.java), [metadata reuse integration](../../../../loomspan-framework/src/test/java/ai/loomspan/integration/PortableDeclarationIdentityIntegrationTest.java).

## Loading and complete namespace publication

`SkillReloader.validate/prepare` take an explicit namespace, complete supplied documents and an `ExecutionConfiguration`. Source names are exact diagnostic labels. Both `Orders import #42` and `Orders/lookup.yaml` are accepted labels. A loader checks file/archive paths before creating a document, while Framework validates the owner and declared skill identity. Namespace publication visibility defaults to Private on each replacement unless explicitly supplied Public.

For startup pattern `classpath:/skills/**/*.yaml`, Framework checks the actual path beneath the fixed prefix. Both `Orders/lookup.yaml` and `orders/lookup.YAML` fail with the relative path and offending component, including candidates a case-sensitive glob would omit. The caller's relative spelling is checked before lookup. A literal `classpath:/skills/Orders/lookup.yaml` checks only `lookup.yaml`; an uppercase literal filename fails. File/classpath/all-classpath and packaged JAR paths are covered. The Sidecar file/HTTP/archive loaders need their own corresponding checks.

[Reloader API](../../../../loomspan-framework/src/main/java/ai/loomspan/api/SkillReloader.java), [path tests](../../../../loomspan-framework/src/test/java/ai/loomspan/internal/skill/StartupSkillPathTest.java), [reload guidance](../../../../loomspan-framework/agent-skills/loomspan-docs/references/java-api/skill-reload.md).

## Authored resolution and model protocols

A bare local reference resolves in the containing skill's owner, and `namespace:local-name` selects the explicit pair. In `orders`, `warehouse-lookup` selects `orders:warehouse-lookup`, while `warehouse:lookup` selects the foreign target. Removing the local target makes the bare name fail even with the foreign target installed. Existing visibility and authorization apply. Allowed children, bindings, output forwarding and evidence select callables; `executor` selects only the executor index. Bare evidence `and` and `or` are operators. In owner `orders`, `orders:and or lookup` is exactly a disjunction of `orders:and` and `orders:lookup`; qualified `orders:or` is also a skill leaf.

Plans and worker prompts use the colon form. Structured `CALL_TOOL.toolName` must match the assigned qualified identity exactly. The provider adapter renders the validated pair with `--`, such as `order-support--find-order`, and binds that name to the resolved callback offered for the current invocation. Returned names select only exact offered callbacks. Colon-form names and otherwise valid unoffered provider names fail. Dispatch never parses a returned function name into an identity.

Raw provider request/response names, arguments and diagnostic evidence retain the exact exchanged content. The separate resolved `capabilityName` field uses `order-support:find-order`. Console catalog tuples expose `registeredName` with this colon spelling plus `namespace` and `localName`. Exact drill-downs and filters use recorded resolved identities. Historical raw evidence is retained verbatim.

[Authored ambiguity/index tests](../../../../loomspan-framework/src/test/java/ai/loomspan/internal/skill/NamespaceIdentityFoundationTest.java), [wire, worker and persisted-trace tests](../../../../loomspan-framework/src/test/java/ai/loomspan/integration/NamespaceExecutionAuthorityIntegrationTest.java), [Console tuple/drill-down tests](../../../../loomspan-framework/loomspan-console/internal/mcpadapter/skills_test.go).

## Verification evidence

Final Step 4 verification on 2026-11-23 used Framework Java 21/Maven 3.9.11 and Console Go 1.26.5, Node 24.18.0 and npm 12.0.2.

| Command, from its owning repository root | Actual result |
| --- | --- |
| Framework `mvnw.cmd clean verify` with the command-local Mockito agent recorded in the closing ticket | 2,569 tests, zero failures/errors, three skips. Produced `target/loomspan-spring-boot-starter-1.0.0-beta.10-SNAPSHOT.jar`. Includes the closed public-surface architecture test, all API/admission/declaration/authored/protocol tests and all shared Java corpus checks. |
| Console `go run ./internal/buildtool verify` and `go run ./internal/buildtool build` | Both passed. Each verification phase passed all 610 frontend tests, the full Go suite, type checking, runtime authoring-pack/replay checks and generated-asset verification. The build produced the versioned native executable. |
| Console `go test -race ./...` | Passed using the installed Windows C compiler and native workspace permissions. |
| Console `npm.cmd --prefix web run test:e2e` | All 40 browser workflows passed against the fresh native executable. Includes same-local owner navigation, trace acquisition, exact raw download, live state and failure evidence. |
| Console `go run ./internal/buildtool mcp-conformance` | All five official scenarios passed, totaling 17 scenario checks with zero failures/warnings. The caching scenario skips resources/read hints because that method is inapplicable. The existing runner handles the known Node/Windows post-result libuv closing assertion. |
| Framework `python scripts/loomspan_version.py check` and fidelity/metadata audit | Version consistency passed. All 82 canonical traces remain byte-identical to pre-ticket HEAD, including all 156 existing model request/response payloads. All 165 required LF files and current artifact lengths/evaluation checksums pass. Dated replay is unchanged. |

The three Maven skips are `StartupSkillPathTest#literalSymlinkKeepsItsLogicalFilename` because this host cannot create the link, plus the two `Pr18RecordedHandoffDiagnosticTest` methods gated by the absent opt-in `pr18.diagnostic` property. The normal loopback provider and worker protocol tests passed. The Step 4 starter JAR SHA-256 was `87fa70dead94b2818603476b36de0f2e0d2ad3a42ab0248639fb003ca56f638b`.

The [closing ticket](../../../../loomspan-framework/ai/thoughts/tickets/loomspan-pr-37.8-integration-and-sidecar-handoff.md) records exact commands, counts, log paths, capability skips and the disposition of all earlier deferred checks. The [testing plan](../../../../loomspan-framework/ai/thoughts/plans/2026-11-23-PR-37.8-integration-and-sidecar-handoff-testing.md) maps the final gates to behavior. These are Framework/Console results. Sidecar must establish its own integration evidence.

The verified starter artifact is in Framework `target/`. Before Sidecar work consumes this snapshot, follow its existing local dependency workflow from the Framework root:

```powershell
.\mvnw.cmd install '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

That installation is preparation for the subsequent Sidecar workstream. This Framework pipeline ran `clean verify` and kept the resulting artifact in `target/`.

## Pending Sidecar and Cloud work

Sidecar chooses its explicit two-component HTTP route/request representation and updates get/invoke/submit/admission, Java and sync/async Python clients, schemas, validation and catalog reuse together. Carry the same pair to Framework after access checks. The HTTP executor remains an annotated `@SkillExecutor` bean using only supported types. Sidecar hosts no Java `@SkillMethod` skills.

Use the [four-product documentation inventory](../../../../loomspan-framework/ai/thoughts/tickets/loomspan-pr-37.7-documentation-audit.md#read-only-sidecar-inventory-and-follow-ups) for concrete files and lines. Required reviewed sets include Sidecar `README.md`, `docs/quickstart.md`, `docs/integration.md`, `docs/http-executor.md`, both SDK READMEs, `examples/quickstart/`, `agent-skills/loomspan-sidecar-authoring/` references/examples, setup, architecture, remote recipes, operations, Console setup and production deployment guidance. Convert camelCase declarations and old encoded dependencies according to their original target. Keep business values, source labels and raw diagnostics exact. Bundle `sourceLabel`/`sourceName` are diagnostic labels. Validate physical definition paths at their loading boundary.

Cloud owns publication, promotion, export and plugin adoption separately. Its companion identity uses `order-support/SKILL.md` and frontmatter `name: order-support`. Destination promotion validates each complete namespace/skill pair. Plugin/exported companion pair limits are separate from runtime function limits. The [Cloud inventory](../../../../loomspan-framework/ai/thoughts/tickets/loomspan-pr-37.7-documentation-audit.md#read-only-cloud-inventory-and-follow-ups) records the seven reviewed planning documents and concrete follow-ups. The [portable naming proposal](../../../../loomspan-framework/ai/thoughts/future/portable-skill-naming.md) supplies the cross-product design context.
