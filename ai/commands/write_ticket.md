---
description: Write a self-contained ticket with an advisory execution profile
---

# Write Ticket

A ticket is the input to `0_run_pipeline.md`. Capture the desired business outcome and decisions the pipeline cannot discover from code. Do not duplicate research, implementation planning, test design, implementation, or review that later steps own.

## Conversational Handoff

Use the full conversation as source material. The pipeline runs in fresh contexts, so the ticket must stand alone:

- explain why the outcome matters;
- preserve agreed behavior, scope boundaries, security or correctness constraints, dependencies, and sequencing;
- include a rejected alternative only when reopening it would materially change the outcome; and
- replace phrases such as "as discussed" with the actual decision.

Distinguish required implementation constraints from suggestions or source hints that planning may reconsider using repository evidence. Do not silently turn a suggestion into a requirement or allow repository evidence to override agreed product intent.

Do not ask the developer to repeat a decision already settled in the conversation. Before writing, identify any unresolved question that could materially change observable behavior, scope, data handling, external side effects, authorization, compatibility, or acceptance criteria. If one remains, ask one focused question and wait. Otherwise create the ticket immediately.

## File Name and External Work Item

Write tickets under `ai/thoughts/tickets/`.

- When the developer supplies an external work-item URL or ID, use `<work-item-id>-<short-kebab-case-slug>.md` when the ID is suitable for a filename.
- Otherwise use `YYYY-MM-DD-<short-kebab-case-slug>.md`.
- Use an outcome-oriented H1 title. Include the external work-item reference in `Context` when one was supplied.
- Never invent a tracker ID, issue, or pull-request number.
- Confirm the target path does not already exist before writing.

The pipeline should use the ticket filename stem when naming its research, plan, testing-plan, and review artifacts.

## Required Content

A pipeline-ready ticket answers:

1. **Outcome** — What should a user or system observe when the work is complete, and why?
2. **Requirements** — Which behavior, edge cases, constraints, or deliberate design decisions cannot safely be inferred from the repository?
3. **Acceptance criteria** — Which concise observable results prove completion?

Add `Context` when links, source examples, dependencies, scope exclusions, or background help a fresh reader. Add `Pipeline notes` only for an intentional exception or constraint that a later step could not safely infer.

Interpret `Pipeline notes` narrowly: they authorize only the stated exception, never broader scope, reduced correctness, weaker security, or inadequate verification. Omit the section when there is no genuine exception.

## Execution Profile Recommendation

Record an advisory execution profile using the context already developed in the
conversation. Preserve the ticket author's understanding of intent and risk
without turning ticket writing into repository research:

Read the `Execution Profiles` eligibility table in
`ai/commands/shared/automation-protocol.md` and apply it only to the
conversational knowledge already available. Reading that process definition
does not call for source inspection. Full retains research and planning;
fast-track uses ticket-led implementation plus independent review; direct
omits independent review and is eligible only when every direct condition
is supported by the known intent and risk.

Add an `Execution profile` section containing `Recommended`, `Confidence`, a
short intent-based `Rationale`, and any `Reassessment triggers`. Use confidence
`high`, `medium`, or `low`. This is advisory: `0_run_pipeline.md` validates the
recommendation against the current checkout before expensive work begins. Do
not perform new codebase research only to increase confidence; when the
conversation does not establish enough, recommend `full` with low confidence.
The recommendation controls process only; it never waives ticket requirements,
correctness, verification, or a review explicitly required by the ticket.

## What Later Steps Discover

The full route assigns the responsibilities below. Light routes move necessary
targeted investigation and verification into Step 4 without requiring skipped
artifacts; fast-track retains Step 5.

- Step 1 finds current code, data flow, tests, fixtures, integrations, configuration, and history.
- Step 2 selects implementation details, assesses impacts and risks, and writes the plan.
- Step 3 designs regression and verification coverage.
- Step 4 implements and verifies the plan.
- Step 5 independently reviews, fixes actionable defects, and repeats in fresh contexts until a clean review is obtained.

Source paths, line numbers, test names, and commands belong in a ticket only when they preserve meaningful context or impose a real constraint.

## Template

```markdown
# <Short outcome-oriented title>

## Outcome

<Observable result and why it is needed.>

## Requirements

- <Required behavior or constraint.>
- <Important edge case or settled decision.>

## Acceptance criteria

- [ ] <Observable result that review can map to evidence.>
- [ ] <Another observable result.>

## Context

<External work-item reference, background, dependencies, scope exclusions, or helpful nonbinding hints. Omit when unnecessary.>

## Execution profile

- **Recommended:** full | fast-track | direct
- **Confidence:** high | medium | low
- **Rationale:** <Why the known intent and risk support this profile.>
- **Reassessment triggers:** <Current-checkout discoveries that should change
  the profile, or `none`.>

## Pipeline notes

- <Intentional exception likely to look accidental. Omit when none.>
```

## Readiness Check

Before saving, confirm:

- the ticket is self-contained and has no placeholders, unresolved questions, or references that depend on this conversation;
- materially different observable outcomes cannot all satisfy it;
- required behavior, deliberate constraints, nonbinding suggestions, and scope exclusions are distinguishable;
- every requirement is represented by at least one observable acceptance criterion;
- vague phrases such as "as appropriate," "where possible," and "handle gracefully" are resolved when different interpretations would change behavior;
- consequential behavior and unusual risks are explicit;
- important data, security, compatibility, and deployment expectations are stated when material;
- known dependencies and sequencing constraints are recorded;
- the ticket contains an advisory execution profile, confidence, rationale, and
  reassessment triggers without claiming unverified repository state;
- no later stage must recover unique intent or background from chat;
- acceptance criteria describe outcomes rather than implementation steps; and
- no research or coding is prescribed unless it captures an agreed constraint.

If the check passes, report the exact ticket path, recommended execution
profile, and that it is pipeline-ready. Create only the ticket; do not start the
pipeline unless the developer also asked for it.
