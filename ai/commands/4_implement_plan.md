---
description: Implement an approved plan or ticket-led fast/direct change and verify it safely
---

# Implement Plan

Implement the supplied plan and testing plan completely, or execute an
explicitly selected ticket-led fast-track/direct change, while preserving
unrelated developer work.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution mode, escalation, and the Step Report.
- Read and preserve active `ai/thoughts/design-lens.md` guardrails. A supplied
  plan may identify the applicable entries; ticket-led execution must assess
  them directly.

## Getting Started

1. Read every supplied ticket, research, implementation-plan, and testing-plan artifact completely.
2. Inspect `git status` and the relevant diff before editing, including staged
   and untracked changes. Existing changes belong to the developer unless the
   artifacts clearly identify them as this ticket's work. Preserve unrelated
   changes, including overlapping hunks; ask or escalate when ownership cannot
   be established safely.
3. Reread every production and test file named by the supplied artifacts plus directly affected callers.
4. Create a working checklist from the plan, or from the ticket in an explicitly selected ticket-led profile, and begin when the intended behavior is clear.

When the orchestrator explicitly invokes **fast-track** or **direct** pipeline
execution without plan artifacts:

- Read the ticket, repository instructions, working-tree state, and directly
  relevant files completely.
- Perform bounded, targeted reconnaissance sufficient to validate the ticket's
  assumptions and locate the implementation and tests. Do not create research,
  implementation-plan, or testing-plan artifacts merely to recreate skipped
  stages.
- Create an internal working checklist mapping every acceptance criterion to
  implementation and proportionate verification.
- Apply the shared profile eligibility and reassessment rules as evidence
  develops. A mismatch returns `STATUS: needs-developer` with the proposed
  upgrade; the orchestrator changes the route before affected work resumes.
- Preserve material context in the ticket's `Execution notes` under the shared
  handoff rules; the internal checklist and Step Report do not replace that
  durable handoff. Under the direct profile, state clearly that no independent
  review is part of the selected route.

If neither a plan nor an explicit ticket-led profile is provided, ask for a
plan. Do not infer that an arbitrary plan is intended, and do not infer
fast-track or direct mode yourself.

## Implementation Rules

- Follow the plan or ticket's outcome and decisions while adapting routine details to the current code.
- Implement one coherent phase at a time, including its tests and documentation/configuration changes.
- Prefer existing repository conventions in the affected area.
- Keep unrelated modernization and cleanup out of scope.
- Use the repository's standard build and test tools.
- Use `apply_patch` or another reviewable edit mechanism; do not overwrite unrelated changes.

Do not perform live or destructive operations as routine verification. Use the safe boundaries, test environments, and operational constraints established by the governing plan or ticket and repository.

## Plan Mismatches

Routine adaptation includes a renamed helper, a moved line, or an additional caller clearly covered by the governing plan or ticket. Persist material decisions in the governing artifact, echo them in `DECISIONS`, and continue.

A genuine mismatch exists when:

- an assumption in the governing plan or ticket is false;
- observable behavior or scope must change;
- an affected consumer, dependency, or operational constraint needs treatment the governing artifact did not address;
- the implementation changes the risk profile materially; or
- safe verification cannot establish a required acceptance criterion.

For a genuine mismatch, stop only the affected work and finish independent safe work. State `Expected`, `Found`, and `Why it matters`, then propose the smallest governing-artifact correction. Ask the developer in standalone mode or return `STATUS: needs-developer` in pipeline mode. Profile mismatches follow the shared upgrade rule instead. Never hide the mismatch behind a fallback or broaden scope silently.

## Verification Loop

For each phase:

1. Add or update the planned test first when the testing plan calls for a red test.
2. Confirm the intended pre-fix failure when practical and record the exact command/result.
3. Implement the change.
4. Run the focused test, then the broadest safe relevant suite.
5. Inspect the diff for correctness, secret or data leakage, accidental live-service use, and unrelated edits.
6. Check completed boxes in implementation and testing plans, when present,
   only after the work and verification are actually complete. Otherwise update
   only the ticket when its own acceptance checkboxes are backed by evidence.

Do not claim a test passed if it was not run. Attribute failures before fixing
them; preserve unrelated developer changes. When a test is blocked by a
pre-existing failure or missing environment dependency, capture the exact
failure, determine whether a narrower safe test still proves the change, and
report residual risk accurately.

Do not return `STATUS: complete` while verification is failing because of the ticket-scoped change or while a required acceptance criterion lacks sufficient executable evidence. Fix the failure, return `needs-developer` when resolving it requires a material decision, or return `failed` when an unrecoverable tool or environment failure prevents completion.

## Completion

Before reporting completion:

- map every acceptance criterion to code and test evidence;
- confirm all required code, tests, configuration, documentation, and supporting artifacts changed coherently;
- verify no secrets or sensitive data entered the diff or logs;
- confirm routine tests did not perform unintended live or destructive operations; and
- review the full ticket-scoped diff, not only the last phase.

End with the Step Report. Under `VERIFICATION`, list exact commands and actual
results; label unrun checks NOT RUN. Under `OPTIONAL_DEVELOPER_CHECKS`, carry
forward nonblocking manual or configured-environment observations from the
governing plan or ticket. Persist new observations in that artifact first;
ticket-led work uses `Execution notes`. For full and fast-track, the independent
Step 5 review determines final completion. Direct has no independent review
and must state that reduced assurance explicitly.

## Resuming Work

When plan checkboxes are already complete, verify the corresponding code exists and continue from the first incomplete item. Re-run prior verification only when the current diff, failure, or dependency makes it necessary. Never use destructive Git commands to recreate a clean state.
