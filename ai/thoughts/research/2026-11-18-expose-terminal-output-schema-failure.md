---
date: 2026-11-18
updated: 2026-11-18
repository: loomspan-sidecar
branch: main
commit: 9769f29dcfc764a7d284268ab7542e24057194b4
ticket: ai/thoughts/tickets/2026-11-18-expose-terminal-output-schema-failure.md
tags: [execution, diagnostics, output-schema, sdk]
---

# Terminal output-schema failure research

## Research Question

What supported Framework information reaches a Sidecar execution worker, and
does it identify the producing skill and structured schema issues for the
specific failure that terminated the requested execution?

## Summary

Framework PR 32 adds a supported terminal diagnosis to the public
`SkillException`. Its `OUTPUT_SCHEMA` stage supplies the producing skill
and the validator issues for the selected terminal cause. The diagnosis is
available at Sidecar's existing admitted-invocation catch boundary independently
of observer history. Sidecar still returns the generic parent failure today.
The original observer limitations below explain the prior scope escalation.

## Repository State

Observed at 19:36 PST on November 18, 2026. Sidecar is on `main` at the commit
above with no staged files. The ticket was untracked at entry. Existing
unstaged edits were `docs/setup.md`, `pom.xml`, `application.yml`,
`HttpSkillExecutor.java`, `LoomspanSidecarApplicationTest.java`,
`HttpExecutorTlsIntegrationTest.java`, `HttpFrameworkFixture.java`,
`HttpSkillExecutorTest.java`, and `HttpTestSupport.java`. These changes
include TypeSafe profile registration and adoption of the v1 Framework
executor invocation type. They predate this ticket run.

The initial local Framework checkout was clean at
`a65cdaa029c346b3301dd9506c7f1741c11f96cd`. The Sidecar POM pins Framework
`1.0.0-beta.10-SNAPSHOT`. Framework references below are paths relative to
`C:/opendev/code/loomspan-framework`.

## Current Behavior and Data Flow

1. `ExecutionController.submit` admits the requested skill name and business
   input, derives the owner from the JWT issuer and subject, and responds
   with 202 and the polling location. A read checks the same owner before
   serializing the retained snapshot. An unavailable or mismatched record
   produces 404.
2. `ExecutionCoordinator.ExecutionTask.run` calls the supported
   `AdmittedSkillInvocation.invoke(observer)` and collects available
   execution views in every diagnostics mode. The worker catches the
   primary runtime exception and passes it with observed events to
   `ExecutionCoordinator.complete`. The coordinator stores the terminal
   snapshot atomically, retaining the admitted request name and a null
   result on failure. `NEVER` omits events, `ONERROR` retains events only
   on failure, and `ALWAYS` retains them for both outcomes.
3. The Sidecar failure classifier tests the top-level
   `SkillInputValidationException` and Spring `AccessDeniedException` and
   otherwise uses `SKILL_FAILURE` with the exception message. The controller
   writes `kind`, `message`, and a nonempty `issues` list when present. The
   existing Java and Python SDKs decode this representation.
4. The Framework validates executor output after its output transform and
   before publishing the result. On schema violation it records an
   `EXHAUSTED` schema outcome and throws an internal
   `LoomspanOutputSchemaValidationException` carrying the canonical
   producing identity and structured issues. Failed model output also has
   an internal schema exception path after retries. The documented observer
   callback runs synchronously after execution with available finalized
   history. The follow-up below describes how the updated root facade also
   carries a public diagnosis.

### Observer attribution before Framework PR 32

The canonical root completion records a `terminalFailureId` linked to the
selected `ERROR_RECORDED` fact. Aborted and failed traces carry that link.
Distinct wrappers can link to a prior cause through `causedByFailureId`.
Classification indicates whether standard tool feedback is permitted.
Recoverable errors can also end an explicit-step execution. The Framework
trace documentation identifies completion linkage independently of classification.

`SkillExecutionView` exposes only `sessionId` and `events`. Its event
payload preserves structured schema outcomes and error facts. The journal
projector drops `TRACE_COMPLETED` and only copies status and optional
attachments when projecting a mission's closing frame into
`SKILL_FINISHED`. Although `ERROR` and `STEP_FAILURE` can share a
`failureId`, the view does not indicate the ID selected by root completion.
The structured output diagnosis itself contains a frame ID and route, but
no link from the public exception to either field. The supported documentation
allows incomplete history following finalization or mapping failure.

Source-level counterexample to an event-order rule. A planning parent with
concurrency enabled may admit two tasks in one parallel group. Workers
record failed attempts independently, and the join collects their outcomes
in admission order. If no primary cancellation won, the first nonrecoverable
failure in the joined outcomes propagates. Thus a schema failure observed
first can belong to the later-admitted sibling while the earlier-admitted
sibling supplies a different terminal failure. Reversing worker timing can
give an unrelated schema failure the last observed event instead. A primary
cancellation introduces a separate choice that supersedes the joined
outcomes. This follows from `StepLoopMissionExecutionEngine` source, not a
Sidecar reproduction.

Before PR 32 the public exception surface provided no failure ID, frame ID, canonical
child name, or issue list for a generic `SkillException`. Comparing a
cause's runtime class name or message to event diagnostics would inspect
an implementation detail and does not provide a documented identity match.
In particular the internal schema exception formats only its first issue
in its message, so two calls to the same child with a shared first issue
can carry different remaining issues and the same exception message. A
frame-level join between related events still needs the root completion
selection. The prior APIs exposed neither a terminal-linked structured
output failure nor an association between the exposed exception and the
selected schema event.

## Key Components

- Sidecar `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41`
  covers submit, owner-scoped read and failure serialization at line 71.
- Sidecar `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:152`
  covers retained record ownership, terminal snapshot construction at line
  227 and worker invocation at line 365.
- Sidecar `src/main/java/ai/loomspan/sidecar/execution/ExecutionFailureClassifier.java:9`
  maps the primary facade failure.
- Framework `src/main/java/ai/loomspan/api/SkillExecutionView.java:6` and
  `src/main/java/ai/loomspan/api/SkillExecutionEvent.java:12` define the public view.
- Framework `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:219`
  records the terminal failure and passes it to trace completion at line 291.
  Executor output validation and throwing begin at line 368.
- Framework `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:243`
  shows completion observer delivery and safe wrapping at the facade.
- Framework `src/main/java/ai/loomspan/internal/runtime/trace/ExecutionJournalProjector.java:75`
  projects selected record types. Line 114 projects skill lifecycle fields,
  and lines 298–301 project error links.
- Framework `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:424`
  selects parallel dispatch. Lines 660–709 record worker failure, and lines
  709–756 join and choose the propagated failure.
- Framework `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md:25`
  documents available-history delivery and the facade taxonomy. Its error
  discussion at line 175 distinguishes classification from completion linkage.
  `agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md:558`
  documents canonical terminal selection.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| HTTP and retention | The owner-visible `failure` object and diagnostics policy are applied when building a single in-memory snapshot. No persistent execution failure schema or reset is involved in the current flow. |
| Java SDK | `sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionFailure.java:4` defines the failure and issue records. `SidecarClient.java:301` validates the polling response and decodes these fields. |
| Python SDK | `sdks/python/src/loomspan_sdk/_models.py:65` defines the exported failure model. `_protocol.py:305` parses failure issues. Synchronous and asynchronous clients both use the common parser. |
| Consumer documentation | `docs/operations.md:344` records diagnostics policy. Both SDK READMEs describe failed executions as normal terminal snapshots. |

## Existing Tests and Fixtures

- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:248`
  exercises bearer-authenticated public submission and polling, including owner
  rejection. The failure case at line 274 uses a deterministic callback.
  The nested case at line 350 provides a provider fixture and a public
  polling example. The test fixture architecture supports local traffic.
- `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java:73`
  asserts primary failure classification. Its policy matrix at line 117
  covers successful and failed execution under all three diagnostics policies.
  The suite uses injected public Framework test doubles.
- The pre-existing TypeSafe profile smoke test in
  `src/test/java/ai/loomspan/sidecar/LoomspanSidecarApplicationTest.java:36`
  covers executor leaf registration. It does not dispatch the ticket's
  malformed repeat response. The sibling suite's checked-in
  `config/skills/typesafe-ai/tsTriageStructured.yaml` has the TypeSafe input
  transform but lacks the specific output transform/schema from the
  historical reproduction. The ticket supplies those missing behavioral
  requirements for a deterministic local scenario.
- The supplied sibling public snapshots under
  `evidence/public-sidecar-triage-failure/` confirm that `NEVER` omits
  events and all policies report the generic parent failure today. In the
  `ONERROR` and `ALWAYS` snapshots, the child `OUTPUT_SCHEMA` event
  includes `skillName` = `default-tsTriageStructured`, issue path
  `/repeatContactProbability`, code `type`, and a message that null was
  found where a number was expected. `ERROR` and `STEP_FAILURE` share a
  failure ID, and the parent's `SKILL_FINISHED` supplies no failure ID.
  These captures are historical reference observations.
- Java's `SidecarClientEdgeTest.java:68` and Python's
  `sdks/python/tests/test_protocol.py:210` exercise failed snapshots and
  issue fidelity with provider-free wire inputs. They cover the existing
  fields only. No current Sidecar test asserts terminal selection between
  multiple child schema events.

## Dependencies and Operational Constraints

Sidecar application and test code use the closed `ai.loomspan.api` surface.
`SupportedLoomspanApiArchitectureTest.java:24` enforces the internal and
autoconfiguration dependency ban. The canonical trace and internal exception
were read as research evidence. The public HTTP acceptance case requires
neither a live provider nor a trace lookup. No tests or live requests were
run in research. A Framework change would require a fresh local install of
the snapshot and Sidecar integration tests against that artifact before any
release steps.

## Historical Context

`ai/thoughts/tickets/2026-11-15-framework-beta10-snapshot-verification.md`
records the earlier snapshot transition and its then-current verification
failures. These results are historical. The active ticket approves use of a
deterministic local TypeSafe fixture and makes new supported Framework
capability a scope reassessment trigger.

## Open Questions

The HTTP field layout and SDK modeling remain Sidecar planning decisions.
Sidecar integration needs to exercise the real TypeSafe extension against the
deterministic local response and independently demonstrate terminal attribution.

## Follow-up on Framework PR 32

At 21:03 PST on November 18, 2026, the local Framework `main` was clean at
`8f73de81b38f30555814c91537a96d2af1fa588f`. The developer resolved the
Framework scope question by implementing PR 32 separately and asked to try it.
Sidecar remained at the repository state recorded above. The orchestrator is
installing/verifying the updated local Framework snapshot separately. This
follow-up inspected source and fixture definitions without running Maven.

### Supported contract and causal selection

- `src/main/java/ai/loomspan/api/SkillException.java:27` exposes
  `Optional<SkillFailureDiagnosis> getFailureDiagnosis()`.
  `SkillFailureDiagnosis.java:7` provides `skillName`, `stage`, `message`,
  and an immutable `issues` list. `SkillContractStage.java:4` distinguishes
  `OUTPUT_SCHEMA` from `INPUT_SCHEMA`, `INPUT_TRANSFORM`, and
  `OUTPUT_TRANSFORM`. Each `SkillContractIssue.java:6` issue carries JSON
  Pointer `path`, validator keyword `code`, and exact `message`. The empty
  string represents the root pointer.
- `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:374`
  validates the published executor output after transformation and throws a
  session-owned schema failure on rejection. Validator-derived public issues
  come from `internal/outputschema/OutputSchemaValidator.java:45`. The
  documented producing skill name is the canonical qualified name. Existing
  input-validation issues remain on `SkillInputValidationException`.
- `internal/core/LoomspanSession.java:155` indexes diagnoses by recorded
  throwable identity and carries forward the diagnosis of a recorded causal
  failure. Framework-owned failures must belong to the same session. Causes
  and suppressed failures are distinct. `ExecutionCoordinator.java:290`
  selects the root terminal failure or gives priority to primary cancellation,
  even when that cancellation has no failure ID. `LoomspanSession.java:602`
  freezes the diagnosis for the selected identifier before optional trace
  finalization. Sibling records and retry events cannot supply unrelated
  diagnoses.
- `internal/skillapi/DefaultSkillTemplate.java:243` reads the selected
  diagnosis before attempting observer delivery and exposes it on the facade
  exception. The public contract is documented in
  `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md:182`.
  Its scope includes direct invocation and admitted execution without an
  observer. It does not require Framework trace persistence or any Sidecar
  diagnostic-event policy. A generic terminal failure can have an empty
  optional despite schema evidence elsewhere in the trace.

The public contract now provides the terminal association identified as
missing in the first pass. The read path requires no inspection of an internal
exception, diagnostic event ordering, or privileged trace. Sidecar remains
responsible for recording the requested parent and limiting the HTTP response
to the authorized owner.

### Executable evidence and fixture boundaries

- Framework's `src/test/java/ai/loomspan/integration/ExecutorOutputSchemaIntegrationTest.java:149`
  describes an admitted planning parent with failed triage and a skipped
  dependent, with trace persistence disabled and enabled. It asserts a
  public `OUTPUT_SCHEMA` diagnosis for the child with the probability path
  and `type` code. It uses an executor test double and verifies the Framework
  contract. It does not exercise Sidecar HTTP, SDKs or TypeSafe transport.
- Framework's
  `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:3123`
  exercises selected, wrapped, and unrelated branch outcomes.
  `src/test/java/ai/loomspan/internal/core/LoomspanSessionTest.java:41`
  covers survival across diagnostic failures, cancellation precedence, and
  same-session causal selection. These are source anchors for construction of
  equivalent Sidecar evidence.
- Sidecar's `src/test/java/ai/loomspan/sidecar/support/SidecarApplicationFixture.java:82`
  already supplies a loopback OpenAI Responses endpoint with a response
  queue and request capture. `ExecutionConfigurationCorrelationIntegrationTest.java:38`
  exercises an explicit plan with that provider fixture.
  `AuthenticatedExecutionApiIntegrationTest.java:248` covers public ownership
  and polling. The uncommitted TypeSafe profile smoke already demonstrates
  file-mode activation with the Decisions extension.
- The local Decisions extension documents the loopback provider boundary in
  `C:/opendev/code/loomspan-extensions/extensions/decisions/docs/transport.md:26`.
  It posts to `/v1/systemone`, with a configured base URL and bearer token.
  Its native result retains the complete JSON response and does not impose
  provider answer-schema enforcement. Framework output transformation and
  output validation therefore remain the boundaries exercised by a response
  with `answers.repeat` equal to `{"type":"noul"}`. The original sibling
  snapshots remain reference evidence and the checked-in sibling triage
  YAML still lacks the exact ticket-specific output transform and schema.
