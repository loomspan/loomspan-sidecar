---
description: Create an implementation plan grounded in the current codebase and ticket
---

# Create Implementation Plan

Produce a complete, executable plan for the supplied ticket. Planning may choose routine implementation details from repository evidence, but it must not invent product behavior.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution mode, escalation, and the Step Report.
- Read `ai/thoughts/design-lens.md` and apply any active project-specific guardrails. If it records none, proceed from the ticket and repository evidence without inventing any.

## Input

In pipeline mode, read the ticket and step-1 research document completely. In standalone mode, use supplied ticket/research paths or ask for the task when none is available.

Also inspect the current working tree and reread the relevant source. Research is a handoff, not a substitute for verifying code that may have changed.

If the developer corrects a factual assumption, inspect the source or other authoritative evidence affected by the correction before relying on it. Treat the developer's product intent as authoritative, but do not leave a corrected codebase fact unverified when it can be checked.

Treat ticket details according to how they are stated: agreed behavior, constraints, and deliberate implementation decisions are binding; suggestions and source hints may be reconsidered using repository evidence. If that distinction is material but unclear, resolve it before finalizing the plan.

## Planning Process

1. **Restate the outcome**
   - Summarize the requested observable behavior and scope boundaries.
   - Map each acceptance criterion to the current flow identified in research.
   - Verify the understanding against the source and call out discrepancies rather than carrying a mistaken ticket assumption into the plan.

2. **Resolve implementation details from evidence**
   - Trace the affected call paths, data flow, configuration, user interface, persistence, and integrations as applicable.
   - Reuse existing local abstractions and patterns when they already own the concept.
   - Keep unrelated modernization and cleanup out of scope unless the ticket requires them.
   - Search for all callers, duplicated rules, shared identifiers or formats, configuration, fixtures, and documentation that must change together.

3. **Assess constraints and risks**
   - Record any active project-specific guardrails from the design lens, or state that there are none.
   - Identify the behavior, data, integrations, operational assumptions, and internal implementation relevant to this ticket.
   - Analyze only the compatibility, security, data, side-effect, concurrency, deployment, and recovery concerns that the change actually raises.

4. **Choose a coherent approach**
   - Make ownership of changed behavior clear and prefer the smallest complete change.
   - Base compatibility decisions on the ticket, verified consumers, and repository evidence rather than a default policy.
   - Separate required automated verification from optional manual or configured-environment checks.
   - When more than one materially different design remains viable, compare the realistic options and record why the selected one best fits the requirements and repository.

5. **Ask only material questions**
   - In standalone mode, present the evidence-backed understanding and recommendation, then ask a focused question when materially different outcomes remain plausible.
   - In pipeline mode, first exhaust the ticket, research, source, tests, and history. If the choice still materially affects behavior, scope, risk, or acceptance criteria, return `needs-developer` with evidence and a recommended answer.
   - Do not escalate naming, helper placement, or another routine choice with a clearly supported answer.

6. **Settle the plan structure**
   - Choose phases with clear outcomes, ordering, and verification boundaries before writing detailed tasks.
   - For materially complex standalone work, align with the developer on the phase outline before finalizing the detailed plan.
   - Do not finalize a plan with unresolved material questions or placeholder decisions.

## Output Artifact

Write to `ai/thoughts/plans/<ticket-stem>.md`. When there is no ticket, use `ai/thoughts/plans/YYYY-MM-DD-<short-description>.md`.

Use this structure:

```markdown
# <Feature or Fix> Implementation Plan

## Overview
- Ticket: `<path>`
- Research: `<path>`
- Outcome: <concise result>

## Current State
<Only the current details needed to understand the plan, with source anchors.>

## Desired End State
<Observable behavior, unchanged behavior, and acceptance-criteria mapping.>

## Scope
### In scope
- ...

### Out of scope
- ...

## Active Project Guardrails

<List applicable decisions from `ai/thoughts/design-lens.md`, or `None recorded`.>

## Impact and Risk Analysis

<Cover only risks and constraints material to this ticket. Do not add empty categories as ceremony.>

## Implementation Approach
<Explain the selected design, where the changed behavior belongs, any material rejected alternative, and why it fits existing code.>

## Phase 1: <Descriptive name>

### Changes
- [ ] `exact/path` — concrete symbols and logic to add, change, or remove.
- [ ] `exact/test/path` — behavior to cover.

### Automated verification
- [ ] `<exact repository-standard build or verification command>` — expected proof.

### Optional developer checks
- [ ] <Focused manual or configured-environment observation, or none.>

## Phase 2: <Descriptive name>
...

## Test Strategy
<The appropriate test levels, regression coverage, fixtures, and boundary checks to develop in step 3.>

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| ... | ... | ... |

## Risks and Rollback/Recovery

## References
- Ticket, research, and important source paths
```

Every phase must name real paths and symbols, explain the logic rather than saying only "add validation," and include objective success criteria. Do not place unverified line numbers in a future-state plan; prefer symbol names because edits move lines.

Success criteria must be measurable and tied to the phase's behavior. Prefer repository-standard build, test, lint, or validation commands. Keep non-automatable observations in `Optional developer checks`; do not use them as a substitute for executable evidence when an acceptance criterion can reasonably be automated.

## Pipeline Handoff

In pipeline mode, continue immediately in the same context with `3_testing_plan.md`, using the ticket, research, and completed implementation plan. Return one combined Step Report listing both plan artifacts.

In standalone mode, report the draft plan location, invite review of scope, sequencing, technical details, and verification, and update the same artifact until the developer is satisfied. Then stop: this command plans the work and does not implement it without a separate explicit request.
