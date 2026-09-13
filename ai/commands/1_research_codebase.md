---
description: Research and document the current codebase for a ticket
---

# Research Codebase

Your only job in this step is to explain the codebase as it exists. Do not recommend a solution, judge the design, or edit production code. Diagnose causes only when the research question requires it, and keep diagnosis separate from recommendations owned by planning.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution mode, escalation, and the Step Report.

## Input

Use the supplied ticket or research question. In pipeline mode a ticket path is required; if it is missing, return `STATUS: needs-developer`. In standalone mode, ask for the question only when no useful input was supplied.

Read every supplied ticket, document, log, or patch completely before broader searching. For a very large file, read it in complete, ordered chunks and capture all relevant context.

## Research Process

1. **Decompose and track the research**
   - Break the ticket or question into concrete areas to locate, trace, and verify.
   - Track those areas in a checklist so important branches are not dropped when new evidence appears.
   - Investigate independent areas concurrently when supported, but verify their results against the source before using them.

2. **Establish repository state**
   - Record date/time, repository name, current commit, current branch, and working-tree status.
   - Preserve unrelated developer changes. Distinguish the ticket's likely diff from pre-existing modifications; do not assume every dirty file belongs to the ticket.

3. **Locate the feature**
   - Start from ticket terminology, identifiers, external system names, file formats, or error text.
   - Use `rg`/`rg --files`, Git history, and direct file reads.
   - Follow the complete path through the relevant layers, configuration, background work, user interface, persistence, and integrations as applicable.

4. **Document current behavior**
   - Identify entry points, data flow, validation, state transitions, side effects, outputs, and failure handling.
   - Identify external or environment-dependent boundaries and whether they perform live side effects.

5. **Map important interfaces and dependencies**
   - Identify affected behavior, data, integrations, configuration, deployment assumptions, and internal implementation as relevant to the ticket.
   - Record important identifiers, formats, interfaces, configuration, and other observable behavior.
   - Treat different forms of implementation and documentation evidence separately. Note disagreements instead of guessing which is authoritative.

6. **Find executable evidence**
   - Locate focused tests, sanitized fixtures, and adjacent test patterns.
   - Record which behavior is tested and which important paths are not.
   - Note tests that require local tools, credentials, services, network access, or other environment setup. Do not contact live services merely to research them.

7. **Use history carefully**
   - Search `ai/thoughts/` and relevant Git history for intent and prior decisions.
   - Historical artifacts supplement the checked-out code; they do not override it.

8. **Synthesize and resolve the key questions**
   - Explain current behavior with exact file and line anchors.
   - Connect findings across components and distinguish verified behavior from inference.
   - Identify ambiguity or missing evidence for planning, but do not propose how to implement the change.

## Output Artifact

Write to `ai/thoughts/research/<ticket-stem>.md`. When there is no ticket, use `ai/thoughts/research/YYYY-MM-DD-<short-description>.md`.

Use this structure:

```markdown
---
date: YYYY-MM-DD
repository: <repository name>
branch: <branch>
commit: <full commit>
ticket: <ticket path or none>
tags: [relevant, tags]
---

# <Topic> Research

## Research Question

## Summary

## Repository State

## Current Behavior and Data Flow

## Key Components
- `path/file.ext:line` — responsibility and relevant behavior

## Affected Areas
| Area | Current behavior and evidence |
| --- | --- |
| <Relevant area> | ... |

## Existing Tests and Fixtures

## Dependencies and Operational Constraints

## Historical Context

## Open Questions
```

Include only sections and table rows that add useful information.

End with the Step Report. `Open Questions` can contain matters for planning to investigate; use `needs-developer` only when research itself cannot be completed without a material answer.

## Standalone Follow-up

When the developer asks a follow-up about the same topic, update the existing research artifact instead of creating a competing document. Record the update date, append a clearly labeled follow-up section, and rerun the relevant investigation rather than answering from the old artifact alone.

## Hard Rules

- Describe what is, not what should be.
- Do not make recommendations or implementation plans.
- Do not expose secrets or sensitive data in the artifact.
- Do not run or trigger live external actions.
- Use fresh source evidence even when an older research document exists.
- Never write placeholder metadata.
