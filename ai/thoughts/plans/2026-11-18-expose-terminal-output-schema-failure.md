# Terminal output-schema failure implementation plan

**Sidecar development policy.** Replace superseded development contracts directly.
Omit compatibility shims, legacy adapters, and parallel APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). This change
requires no development-data reset because retained executions are in memory.

## Overview

- Ticket [2026-11-18-expose-terminal-output-schema-failure](../tickets/2026-11-18-expose-terminal-output-schema-failure.md).
- Research [Terminal output-schema failure](../research/2026-11-18-expose-terminal-output-schema-failure.md).
- Outcome. An authorized owner can identify the skill and exact output validator
  issues selected as the terminal cause of a failed invocation from one poll,
  including under the default diagnostics policy.

## Current State

- `ExecutionController.execute` validates the requested skill and input before
  admission. `ExecutionController.execution` checks ownership through
  `ExecutionCoordinator.find` and serializes one retained snapshot.
- `ExecutionCoordinator.ExecutionTask.run` passes the top-level admitted invocation
  failure to `complete`. The classifier handles input validation and access
  denial explicitly and classifies other exceptions as `SKILL_FAILURE`.
  The completion path applies diagnostic event selection independently.
- Framework commit `8f73de8`, installed locally as the pinned beta.10
  snapshot, exposes `SkillException.getFailureDiagnosis()`. The supported
  diagnosis identifies the canonical emitting skill, stage and exact
  validator issues for the Framework-selected terminal failure. The Framework
  selects that cause before observer delivery.
- `SidecarClient.snapshot` and Python `_protocol._failure` already parse
  failure kind, message and an issue list. Both SDKs return a failed snapshot
  as a normal result of polling.
- The TypeSafe profile, v1 executor imports, and related build and startup
  edits already present at triage are baseline work. Preserve those changes
  and use the actual TypeSafe extension in the principal acceptance fixture.

## Desired End State

The owner receives HTTP 200 with this terminal snapshot shape, in addition to
the existing ID and timestamps. The issue explanation shown is illustrative.

```json
{
  "skillName": "parent",
  "status": "FAILED",
  "result": null,
  "failure": {
    "kind": "OUTPUT_SCHEMA",
    "message": "Skill 'parent' execution failed.",
    "producingSkillName": "default-tsTriageStructured",
    "issues": [
      {
        "path": "/repeatContactProbability",
        "code": "type",
        "message": "null found, number expected"
      }
    ]
  }
}
```

`OUTPUT_SCHEMA` names the selected failure kind. `producingSkillName` is
present for this kind and contains the canonical Framework name. Preserve
the primary failure message and copy each output issue's JSON Pointer,
code, and explanation verbatim. The empty string is a valid root pointer.
Existing classifications and input-issue representation keep their existing
contract. Generic, access-denied, and input failures omit the producing name.
Preserve existing empty-issue omission in the response and the SDKs' empty
collection representation. Sidecar returns the same failure under all three
diagnostics policies, with `events` still governed by that policy. A foreign
owner continues to receive HTTP 404.

## Scope

### In scope

- The terminal failure model, primary classification, and owner-facing JSON.
- Java and Python SDK failure models and parsers.
- Deterministic public HTTP, worker-boundary and SDK regression evidence and
  current consumer documentation.

### Out of scope

- Exposing other Framework diagnosis stages in the response, new Framework
  work, replay, trace lookup, or alterations to event selection.
- Provider behavior changes, unrelated TypeSafe profile work, release
  publication and changes to the pinned artifact version.

## Active Project Guardrails

- Keep validation and terminal cause selection in the Framework. Consume only
  its closed public surface. Use the Framework observer and annotated executor
  contract where needed in tests.
- Retain the failure together with the result and selected events under the
  existing owner-scoped execution record and completion lifecycle.
- Preserve primary failure text, exact validated issues and available events.
  Avoid adding the raw provider response or exception chain to the failure.
- Document the current contract in operator, SDK, and packaged authoring
  references. Keep implementation evidence in the pipeline artifacts.

## Impact and Risk Analysis

- A nested branch class or an observer event can refer to a losing attempt.
  Reading only the top-level public terminal diagnosis preserves Framework
  causal selection even with parallel execution or absent observer history.
- Existing input issues and output issues both have path, code, and message.
  The issue path conventions differ. Keep exact text from the corresponding
  source record, including an empty output root pointer or an input path such
  as `$.message`.
- HTTP consumers can switch on the new failure kind, and both SDKs expose
  the optional canonical producer. Existing polling snapshots without the
  field still decode using the same parser. The SDKs do not require a
  Framework dependency.
- The new detail follows the existing owner check and retention window. No
  storage schema, settings, admission, or request validation changes follow
  from this contract.

## Implementation Approach

Extend the existing flat failure object with `OUTPUT_SCHEMA` and
`producingSkillName`, reusing `issues`. One classification and issue list is
enough for a single terminal cause. A separately nested diagnosis would
duplicate the kind or the issue envelope. Keep the retained model independent
of Framework value types by using one Sidecar `Issue` record for current input
issues and new output issues. Translate both Framework records directly in
the classifier. The HTTP controller adds the producer field when present.

## Phase 1. Sidecar worker and public HTTP

### Changes

- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionOutputSchemaIntegrationTest.java`
  exercises the exact real TypeSafe failure through HTTP on loopback. Run
  the `NEVER` case against the current implementation to observe the
  generic failure, then complete the policy and parallel attribution cases
  from the testing plan.
- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionFailure.java` adds
  the kind, nullable producing name and immutable list of local
  `Issue(path, code, message)` records.
- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionFailureClassifier.java`
  preserves input-validation and access-denied precedence. For a top-level
  `SkillException`, inspect only `getFailureDiagnosis()`. Map an
  `OUTPUT_SCHEMA` diagnosis to the new kind, canonical producing skill and
  issue list. Preserve the original facade message and use the existing
  generic classification for all remaining cases.
- [x] `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java`
  serializes `producingSkillName` only when present. The coordinator and
  existing ownership, record lifecycle and diagnostic policies already
  provide the required boundary.
- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionDiagnosticsTest.java`
  updates the current input issue assertion for the local record and
  verifies public-stage filtering and facade-only selection.

### Automated verification

- [x] `./mvnw.cmd -B -ntp -o '-Dtest=ExecutionOutputSchemaIntegrationTest,ExecutionDiagnosticsTest,AuthenticatedExecutionApiIntegrationTest,SupportedLoomspanApiArchitectureTest' test`
  proves owner-scoped HTTP behavior, Framework boundary, stage filtering and
  unchanged execution admission and observer behavior.

### Optional developer checks

None.

## Phase 2. SDKs and consumer documentation

### Changes

- [x] `sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionFailure.java`
  adds a nullable `producingSkillName()` component to the existing record.
  `SidecarClient.snapshot` reads the optional string with existing field
  validation and preserves exact issue strings. Keep the normal terminal
  snapshot path and use the new record directly.
- [x] `sdks/python/src/loomspan_sdk/_models.py` adds
  `producing_skill_name: str | None` with an absent default to the frozen
  `ExecutionFailure` dataclass. `_protocol._failure` parses the optional
  `producingSkillName` string through the common sync/async parser.
- [x] `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientEdgeTest.java`
  and `sdks/python/tests/test_protocol.py` cover the wire contract,
  exact issue text, absent producer, and nonstring producer rejection.
- [x] Update `agent-skills/loomspan-sidecar-authoring/references/integration.md`
  with the new kind and HTTP shape, `docs/operations.md` with the relation
  to diagnostics policy, and both SDK READMEs with attribute names and
  typed issue access. Explain parent versus canonical producer, JSON
  Pointer, and terminal-only attribution.

### Automated verification

- [x] `./mvnw.cmd -B -ntp -o -f sdks/java/pom.xml verify`
  passes both Java SDK modules, including failure polling and the dependency
  boundary.
- [x] `python -m pytest -q sdks/python/tests` in the SDK development
  environment passes the shared parser and execution client suites.

### Optional developer checks

None.

## Test Strategy

Run the offline HTTP acceptance flow for all policy values using one
fixture shape, and one parameterized competing-branch scenario with
different terminal categories. Unit checks protect the exact public API
mapping without repeating Framework selection rules. SDKs read representative
HTTP envelopes over their existing provider-free test boundaries. The
[testing plan](2026-11-18-expose-terminal-output-schema-failure-testing.md)
defines the fixtures, full local checks, and exit criteria.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Public submit with business input and malformed TypeSafe repeat answer | Existing normal submit and admitted invocation | HTTP integration submits to `parent`, with real TypeSafe extension and accepted two-task fixture plan |
| Default owned 200 exposes exact output issue and omits events | Classifier and controller serialize canonical producer and structured issue | `NEVER` case checks status, result, kind, producing name, exact pointer/code, null vs. number explanation and missing event field |
| Exactly one planner request and one TypeSafe request; dependent is skipped | Existing admission and Framework dependency path | Fixture counts both providers and checks the dependent executor was not called |
| Details under `ONERROR` and `ALWAYS`, SDK model access, owner isolation | Existing event selection and owner check, SDK parser/model fields | Policy cases check identical diagnosis and included events; foreign token gets 404; both SDKs parse the contract |
| Only the selected terminal cause supplies output details | Only the top-level public output diagnosis is mapped | Parallel group contains competing observed failures; selected schema variant checks the correct producer and non-schema variant checks absent producer and issues |

## Risks and Rollback/Recovery

Address red or failing tests at the fixture, mapping, or dependency boundary
that caused them. If the installed Framework snapshot lacks the new public
method, standard development recovery is to install the matching local
Framework checkout and rerun affected Sidecar checks. Rollback is the Sidecar
code/tests/docs diff only. No development or deployed data migration or reset
is required. Release checks and dependency pinning follow Framework publication.

## References

- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java`
- `src/main/java/ai/loomspan/sidecar/api/ExecutionController.java`
- `C:/opendev/code/loomspan-framework/agent-skills/loomspan-docs/references/java-api/observation-and-errors.md`
- `C:/opendev/code/loomspan-framework/agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md`
- `C:/opendev/code/loomspan-framework/agent-skills/loomspan-docs/references/skill-authoring/planning-concurrency.md`
