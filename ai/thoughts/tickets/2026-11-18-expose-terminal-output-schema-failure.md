# Identify the failing child and output-schema violation in a failed execution response

## Development constraints

**Sidecar is still in development. Destructively replace superseded development contracts and omit compatibility shims, legacy adapters, and parallel APIs.** Apply the [design lens](../design-lens.md). Choose the simplest solution that meets the public contract, minimize technical debt, and keep tests and documentation proportional. Document any required development-data reset. Use the supported Framework API boundary.

## Outcome

An authorized execution owner polling a failed planning parent can read the producing child's identity, the offending output field, and the violated schema constraint from the terminal `failure` object. This works with the default `loomspan-sidecar.executions.diagnostics=NEVER`. Today the response says only `Skill 'parent' execution failed.` The Framework already identifies the cause in the canonical trace, and Sidecar exposes it in diagnostic events when enabled.

## Requirements

- Expose machine-readable details for the output-schema violation that caused the terminal failure. Preserve the parent as the requested skill and distinguish a child output failure from a caller input error. HTTP callers and the Java and Python SDKs should be able to consume the details without parsing exception prose.
- Attribute details to the terminal cause across nested calls. An earlier or unrelated child's schema event must not be presented as the cause. Keep existing execution-owner access rules and diagnostic event selection. The default failure summary does not require publishing the event list, raw provider response, or exception chain.

## Acceptance criteria

- [x] Exercise the public HTTP API using the deterministic failing triage fixture. Submit business input to `POST /v1/skills/parent/executions`. The fixture planner schedules `tsTriageStructured` followed by a dependent child. The TypeSafe fixture returns a repeat answer of `{"type":"noul"}`, omitting its `noul` value. The triage transform projects that lookup into `repeatContactProbability`, which the child's output schema declares as a required number.
- [x] With diagnostics set to `NEVER`, submission returns 202. The owner polls the returned location and receives HTTP 200 with `skillName: parent`, `status: FAILED`, and `result: null`. The terminal `failure` identifies an output-schema violation on `default-tsTriageStructured`, at JSON Pointer `/repeatContactProbability`, with constraint code `type` and an explanation that null was found where a number was expected. The key exists, so reporting a missing required property would be incorrect. The `events` field is absent.
- [x] The fixture records one planner request and one TypeSafe request. The dependent child never runs. No additional execution or privileged trace lookup is necessary to obtain the failure details.
- [x] The same terminal details are available with `ONERROR` and `ALWAYS`, alongside their usual events. Both SDKs expose the documented details to callers. A different execution owner cannot retrieve the snapshot.
- [x] In a multi-child execution, only the schema violation responsible for termination is attributed to the terminal failure. A failure caused by something else carries no unrelated output-schema issue.

## Context

The sibling suite captured the [default public response](../../../../loomspan-sidecar-test-suite/evidence/public-sidecar-triage-failure/default-snapshot.json) and the [same failure with diagnostic events](../../../../loomspan-sidecar-test-suite/evidence/public-sidecar-triage-failure/ONERROR-snapshot.json). These are reference observations. Acceptance uses local fixtures and requires no paid provider access. The exact response-field layout is an implementation design decision.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change affects a serialized public failure contract and its SDK consumers. Correct attribution across nested calls matters even when diagnostic events are omitted.
- **Reassessment triggers:** Reassess scope if reliable attribution requires a new supported Framework contract.

## Execution notes

- The developer approved the Full 5-Step Pipeline and supplied Framework PR 32
  separately after initial research identified a missing public contract.
  Framework commit `8f73de8` adds `SkillException.getFailureDiagnosis()` at the
  admitted invocation boundary. Continue the Sidecar scope using the locally
  installed beta.10 snapshot.
- Preserve the TypeSafe and HTTP executor work already present at pipeline
  triage. That pre-existing work is baseline for the Sidecar ticket.
