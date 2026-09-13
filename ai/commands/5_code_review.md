---
description: Independently review, safely fix, and verify a change
---

# Code Review

Review the current ticket-scoped change as an independent engineer. Find concrete defects, not stylistic preferences. In pipeline mode, fix actionable findings within scope, re-review, and report whether a fresh review context is required.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution mode, the review/fix result, escalation, and the Step Report.
- Read `ai/thoughts/design-lens.md` and verify any active project-specific guardrails. Do not treat placeholder examples as policy.

## Core Review Principles

1. **Review the code, not the story.** Reconstruct the change from Git and the repository; do not trust summaries, checkmarks, or claimed test results without verification.
2. **Judge correctness before conformance.** First determine whether the implementation is sound, then compare it with the ticket and plans. A plan can be incomplete or wrong.
3. **Review by risk.** Select relevant lenses from the actual diff instead of forcing every category onto every change.
4. **Use concrete evidence.** A finding needs a reachable trigger, the affected path, and an observable impact.
5. **Be skeptical, not speculative.** Check callers, tests, dependencies, and existing safeguards before reporting a candidate.
6. **Passing tests are evidence, not proof.** Evaluate whether assertions and test boundaries can detect the defect under review.
7. **Complete the independent review before editing.** Pipeline fixes begin only after the initial review is complete.
8. **Always consider security and privacy.** Determine their applicability from the actual change, even when the ticket and plans do not mention them; requirement omissions can themselves expose defects.

## Inputs and Scope

Read every supplied ticket, research, implementation-plan, and testing-plan
artifact, plus repository instructions and relevant source, completely. A plan
and research artifact are helpful context, not prerequisites; never require
artifacts skipped by an approved fast-track route. Do not read prior review
documents in pipeline mode; every review cycle must form an independent
judgment from the current repository.

Establish scope using:

1. ticket outcome, requirements, acceptance criteria, and `Pipeline notes`;
2. implementation/testing plans and their recorded decisions;
3. committed, staged, unstaged, and untracked changes, plus the correct comparison base when reviewing branch work; and
4. unrelated pre-existing changes that must be excluded or treated carefully.

Do not assume the comparison branch name. Derive the base from the supplied target, upstream, or repository default and use the merge base when appropriate. Remember that ordinary Git diffs do not show untracked file contents.

Never assume every dirty file belongs to the ticket. Conversely, review all ticket-related changes even when they are unstaged or overlap earlier developer work. Inventory changed production code, tests, configuration, documentation, dependencies, generated files, and operational artifacts before evaluating the implementation.

## Review Process

### 1. Reconstruct intended and actual behavior

- Read changed files fully when practical and inspect connected callers, callees, configuration, and tests beyond the diff hunks.
- Trace the changed behavior through all relevant layers and boundaries, including failure and cleanup paths.
- Establish previous and new behavior independently before comparing the result with acceptance criteria or plan decisions.
- Identify the affected observable behavior, data, dependencies, configuration, and internal implementation.

### 2. Find correctness defects

Check normal, empty, malformed, boundary, failure, and recovery cases as relevant. Follow the actual data and control flow rather than relying on isolated diff hunks.

### 3. Review state, persistence, concurrency, and lifecycle when applicable

- Check stored-state compatibility, ownership, consistency, transaction boundaries, and deployment steps.
- Look for lost updates, duplicate processing, overlapping work, and unsafe check-then-act sequences.
- Confirm recovery or reconciliation is possible after partial work.
- Check cancellation, timeouts, startup/shutdown behavior, and whether files, streams, connections, executors, or other resources are scoped and closed correctly.

### 4. Review external boundaries

For affected external or environment-dependent boundaries:

- verify request/response mapping and identifiers;
- assess idempotency, retries, timeouts, rate limits, partial success, and duplicate side effects;
- ensure errors remain actionable without logging sensitive payloads; and
- ensure routine tests use safe doubles rather than live operations.

### 5. Review security and privacy

- Review the security and privacy consequences of the actual change, including trust boundaries, validation, authorization, and data exposure where relevant.
- Search the diff and relevant logging/error paths for secrets and sensitive data.
- Do not include sensitive values in the review artifact.

### 6. Review maintainability and repository fit

- Prefer the smallest coherent implementation consistent with the local feature area.
- Flag duplicated logic, dead new code, swallowed exceptions, unrelated migrations, misleading names, or abstractions that obscure a simpler implementation.
- Do not report broad legacy cleanup unrelated to the ticket unless the change makes an existing hazard materially worse.

### 7. Review performance, operations, and documentation when applicable

- Look for concrete unbounded work, resource growth, repeated expensive operations, or amplification on a realistic path.
- Verify failures are diagnosable without leaking sensitive data and that operational behavior remains understandable.
- Check user-facing documentation, examples, configuration guidance, and migration notes when the change requires users or operators to act differently.

### 8. Evaluate tests and verification

- Map each acceptance criterion and high-risk path to executable evidence.
- Determine whether each important test would fail without the implementation and asserts observable behavior rather than incidental internals.
- Inspect assertions, fixtures, mocks, negative paths, and whether a test could pass without exercising the changed code or a mock could hide an integration defect.
- Run focused repository-standard tests first, followed by the broadest safe relevant suite.
- Never trigger live external or production data operations as routine verification.
- Treat optional manual or configured-environment checks as residual observations, not automated passes.

### 9. Validate requirements and plan conformance

- Map every acceptance criterion and material plan decision to code, tests, documentation, or other evidence.
- Verify completed checkboxes against the repository rather than trusting them.
- Distinguish implemented, partial, missing, and safely deviated requirements.
- Treat plan conformance as a separate result: a conforming implementation can still have defects, and a harmless mechanical deviation is not automatically a finding.

## Candidate-Finding Discipline

Use searches and static clues to find candidates, then verify each one before reporting it:

1. Re-read the changed lines and surrounding implementation.
2. Trace the trigger through callers and dependencies.
3. Search for validation, safeguards, or compensating behavior.
4. Check relevant tests and library or framework semantics.
5. Confirm the issue is introduced, worsened, or made relevant by this change.
6. State the impact without exaggeration.

A finding must be caused by or materially exposed by the ticket-scoped change, reproducible from supported behavior, and specific enough to fix.

Do not report:

- style preferences with no concrete consequence;
- hypothetical misuse outside supported flows;
- pre-existing unrelated defects;
- missing comments or documentation that add no operational value; or
- a deliberate, narrowly scoped decision explicitly authorized by the ticket or `Pipeline notes`.

## Finding Priorities

- **P0 — Critical:** likely severe data corruption, secret exposure, broad security bypass, or destructive production behavior.
- **P1 — High:** material incorrect workflow, duplicate external action, data loss, security flaw, or failure of a primary acceptance criterion.
- **P2 — Medium:** real edge-case defect, incomplete failure handling, meaningful regression, or verification gap likely to escape into use.
- **P3 — Low:** small but concrete correctness or maintainability issue worth fixing in this scope.

Each finding must include priority, imperative title, exact file/line anchor, failing scenario, impact, evidence, and the smallest appropriate fix.

## Review/Fix Behavior

In standalone review-only mode, do not edit implementation files unless the developer explicitly asked for fixes. Write findings and report `clean` only when none are actionable.

In pipeline mode, apply the shared profile reassessment rule to the reviewed
change and before implementing fixes. Use the selected profile supplied by the
orchestrator. If a fast-track review discovers a full-profile trigger, return
`STATUS: needs-developer` with the evidence and proposed upgrade before
affected fixes or completion, even when the fix is clear and in scope.

For each review/fix cycle:

1. Complete the full review before editing.
2. Fix every safe, in-scope actionable finding, including tests, using Step 4's
   mismatch and verification discipline and preserving unrelated developer work.
3. Re-run relevant verification and review the complete diff again.
4. Repeat internally until no actionable findings remain, or return `needs-developer` for a material decision.
5. Return `REVIEW_RESULT: fixes-applied` if this context changed any implementation artifact. The orchestrator must launch another fresh step-5 context.
6. Return `REVIEW_RESULT: clean` only if this context made no implementation-artifact changes, found no actionable findings, and completed sufficient verification. Writing the review document alone does not count as a fix.

Do not return `REVIEW_RESULT: clean` when a verification failure is attributable to the ticket-scoped change or a required acceptance criterion lacks sufficient executable evidence. A `NOT RUN` check is compatible with `clean` only when the remaining evidence still establishes the affected requirements and risks; record the reason and residual risk explicitly.

Never expand the ticket materially to fix a finding. Escalate when the correct fix requires new product intent, destructive data action, unavailable authority, or materially broader scope.

## Output Artifact

Write to `ai/thoughts/reviews/<ticket-stem>-review-<N>.md` using the review number supplied by the orchestrator, or `1` for a standalone first review. When there is no ticket, prefix the short description with `YYYY-MM-DD-`.

```markdown
# <Ticket> Code Review — Cycle <N>

## Scope and Repository State

## Findings

### [P1] <Imperative title>
- Location: `path:line`
- Scenario:
- Impact:
- Evidence:
- Fix:

Use `No actionable findings.` when clean.

## Findings Resolved in This Context

## Acceptance-Criteria and Plan Conformance
| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |

## Active Project Guardrails
- <Conformance evidence for each applicable guardrail, or `None recorded`.>

## Open Questions and Assumptions
- <Only matters that affect correctness or review confidence, or `None`.>

## Verification Results
- PASS/FAIL/NOT RUN — `<exact command>` — <detail>

## Residual Risks and Optional Developer Checks

## Disposition
- `clean`, `fixes-applied`, `needs-developer`, or `failed`
```

End with the Step Report. Keep chat output a concise receipt; the review artifact
contains the evidence. A `clean` result requires no open findings of any
priority. For a stopped review, explain the decision or failure in the artifact
and omit `REVIEW_RESULT` from the Step Report until Step 5 is complete.

## Final Review Check

Before reporting, confirm that scope included staged, unstaged, and untracked work; behavior was traced beyond diff hunks; independent defect review preceded plan comparison; relevant correctness, security, lifecycle, persistence, integration, performance, operational, and documentation risks were considered; every finding was checked for safeguards and false positives; test quality was evaluated; reported commands were actually run; and the disposition matches the remaining findings and verification evidence.
