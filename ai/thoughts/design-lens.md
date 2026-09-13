# Design Lens

## Status

There are currently no active project-specific design guardrails recorded here.

## Purpose

This document preserves durable, non-obvious, cross-cutting decisions that future research, planning, implementation, and review must remember. It is intentionally a placeholder until this app develops such constraints.

An entry belongs here only when all of the following are true:

- it is specific to this app rather than general software-engineering advice;
- it applies across multiple tickets or feature areas;
- it is expected to remain valid long enough to guide future work;
- violating it could produce a plausible but incorrect implementation; and
- the rule cannot be reliably reconstructed from the checked-out code and tests alone.

Do not add repository architecture summaries, coding conventions visible in the source, ticket-specific requirements, temporary implementation notes, or generic reminders such as testing, authorization, validation, and error handling. Put those in the relevant code, tests, ticket, plan, or command instead.

Possible future subjects include AI decision authority, provenance and audit requirements for AI-derived values, model data boundaries, evaluation requirements for prompt or model changes, failure and fallback policy, and whether AI results may initiate external side effects. These examples are not current policy.

## Entry Format

```markdown
## <Guardrail name>

- **Decision:** <The rule future work must preserve.>
- **Why this is non-obvious:** <What a reasonable implementation might otherwise get wrong.>
- **Applies to:** <Features, data, integrations, or workflows covered by the rule.>
- **Exceptions:** <Explicit exceptions, or `None`.>
- **Established by:** <Ticket, decision, date, or other authoritative source.>
```
