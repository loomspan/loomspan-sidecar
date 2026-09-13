---
description: Design focused regression and verification coverage for a change
---

# Create Testing Plan

Design tests and exit criteria; do not edit test or production code in this step.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution mode, escalation, and the Step Report.
- Cover any active `ai/thoughts/design-lens.md` guardrails carried into the completed implementation plan.

## Input

Read the implementation plan, ticket, research, and any supplied failure output completely. In pipeline mode this command runs in the same context immediately after step 2 and returns a combined report. If no usable input exists, ask in standalone mode or return `needs-developer` in pipeline mode.

## Process

1. **Verify the actual test landscape**
   - Inspect the affected production path, build configuration, existing tests, adjacent test conventions, and relevant fixtures.
   - Identify the test levels and tools already used by the repository.
   - Note dependencies on local tools, configuration, credentials, services, networks, or other environment setup.

2. **Define scope and risks**
   - List the observable and internal behaviors that change.
   - Identify the highest-risk regressions and edge cases from the actual implementation path.
   - Include integration, persistence, configuration, security, concurrency, or operational risks only when relevant.

3. **Plan the red test**
   - For a bug, name the smallest deterministic test that fails before the fix and explain the expected failure.
   - For new behavior, identify the first acceptance-criteria test that demonstrates the missing capability.
   - For a pure refactor, state why no red behavioral test applies and identify regression coverage.

4. **Specify coverage**
   - Use the cheapest test boundary that proves the behavior.
   - Reuse repository conventions and representative sanitized fixtures.
   - Isolate live or destructive side effects unless an existing safe test environment is explicitly intended for them.
   - Cover negative and recovery paths when the change makes them material.

5. **Define commands and exit criteria**
   - Prefer a focused repository-standard test during development, then the broadest safe relevant suite.
   - Use the build tool and environment conventions discovered in the repository; do not invent commands or profiles.
   - Separate automated gates from optional manual or configured-environment observations.

## Output Artifact

Write to `ai/thoughts/plans/<ticket-stem>-testing.md`. When there is no ticket, use `ai/thoughts/plans/YYYY-MM-DD-<short-description>-testing.md`.

```markdown
# <Feature or Fix> Testing Plan

## Change Summary

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| <Affected area> | ... | ... |

## Existing Coverage and Environment Constraints

## Failing Test First
- Name:
- Type:
- Location:
- Arrange/Act/Assert:
- Expected pre-fix failure:

## Tests to Add or Update

### 1. `<testName>`
- Type: <repository-appropriate test level>
- Location: `exact/path`
- Proves:
- Inputs/fixture:
- Doubles or boundary isolation:
- Edge cases:

## Safe Verification Commands
- Focused: `<command>`
- Related suite: `<command>`
- Full safe suite: `<command, or reason not planned>`

## Optional Developer Checks
- <Manual or explicitly configured non-production observation, or none.>

## Exit Criteria
- [ ] The planned red test fails for the intended reason before implementation, when applicable.
- [ ] New and updated tests pass after implementation.
- [ ] The broadest safe relevant repository test suite passes.
- [ ] Acceptance criteria map to executable evidence.
- [ ] Routine automated tests do not perform unintended live or destructive operations.
- [ ] Material risks and edge cases identified above are covered.
- [ ] Any optional check is reported as nonblocking and is not represented as already performed.
```

End with the Step Report. In pipeline mode, emit one combined `2_create_plan + 3_testing_plan` report listing both artifacts.
