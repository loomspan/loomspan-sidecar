# Use portable skill names in SDKs and runnable examples

## Development constraints

**Sidecar is in development with no deployments or production instances. Destructively replace superseded naming contracts, code and schemas. Do not add compatibility shims, legacy adapters, parallel APIs or migration machinery.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that meets these requirements and minimize technical debt. Keep implementation, tests and documentation proportional. Write product documentation as the current contract, without migration guidance. Record any required development-data reset and its impact in the implementation plan. This ticket does not authorize deleting deployed data.

## Outcome

Java and synchronous and asynchronous Python callers can take a catalog entry, submit that exact skill and read its execution result. The quickstart teaches the same contract end to end.

## Requirements

- Consume the [Sidecar naming ticket](2026-11-24-sidecar-portable-skill-names.md) wire contract: `GET /v1/namespaces/{namespace}/skills`, `GET /v1/namespaces/{namespace}/skills/{localName}` and `POST /v1/namespaces/{namespace}/skills/{localName}/executions`. Listing takes an explicit namespace. Detail, submit and any convenience invocation methods take namespace and local name separately, including for `default`. Remove single-name overloads and implicit defaults.
- Expose both catalog and polled execution identity fields (`namespace`, `localName` on the wire) through idiomatic SDK models. Pass component spelling exactly. Preserve authentication, business input, execution-ID polling, returned results and diagnostic evidence.
- Update the runnable examples, SDK READMEs, quickstart and affected consumer and packaged authoring guidance. Convert declarations and references together, preserving the target of each child or executor reference. Keep business keys, REST paths, diagnostic labels and raw evidence intact. Review the documentation inventory in the [Framework handoff](../future/portable-skill-naming.md) for naming examples.

## Acceptance criteria

- [x] Java and both Python clients list an explicit namespace and use a returned pair with a hyphenated local name for lookup and submission. Requests use separate path segments, preserve the supplied input, and poll by execution ID. Parsed catalog and execution models expose the same pair.
- [x] Public SDK examples and naming-related tests exercise the replacement signatures, including convenience methods that accept a target. SDKs expose no obsolete single-name call or fallback.
- [x] The runnable quickstart submits the renamed planner in `default`; its references resolve to the intended renamed leaves and their HTTP executor. Consumer documentation and packaged examples show the current calls and declarations without migration instructions.

## Context

Sequence integration verification after the Sidecar naming ticket supplies the new routes and response fields. This ticket owns client and example adoption. It introduces no publication capability, SDK authentication change or Cloud work.

The [SDK review](../reviews/2026-11-24-sdk-and-example-portable-skill-names-review-2.md)
verified the client contract and installed examples. The [Sidecar testing
plan](../plans/2026-11-24-sidecar-portable-skill-names-testing.md#step-4-verification-on-2026-11-24)
records the combined quickstart and SDK verification against the actual routes.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Both language SDKs change supported public methods and serialized response handling across three client surfaces.
- **Reassessment triggers:** Reassess if SDK adoption already exists and the remaining change is confined to examples.
