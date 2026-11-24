# Address Sidecar skills by namespace and local name

## Development constraints

**Sidecar is in development with no deployments or production instances. Destructively replace superseded naming contracts, code and schemas. Do not add compatibility shims, legacy adapters, parallel APIs or migration machinery.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that meets these requirements and minimize technical debt. Keep implementation, tests and documentation proportional. Write product documentation as the current contract, without migration guidance. Record any required development-data reset and its impact in the implementation plan. This ticket does not authorize deleting deployed data.

## Outcome

Callers can discover a skill and submit it using the exact identity returned by the catalog. A hyphen in a local name never changes which skill Sidecar selects.

## Requirements

- Replace the existing catalog and submission routes with `GET /v1/namespaces/{namespace}/skills`, `GET /v1/namespaces/{namespace}/skills/{localName}` and `POST /v1/namespaces/{namespace}/skills/{localName}/executions`. The POST body remains the business input. Polling continues at `GET /v1/executions/{id}`.
- Keep file startup and managed publication in `default`. Require explicit namespace selection even for that owner. Apply Framework's exact component and pair rules at the HTTP boundary. Remove implicit owners, prefix guessing and alternate-name lookups. Keep Framework authoritative through its supported public API.
- Expose `namespace` and `localName` in catalog descriptors, retained execution responses and file-startup skill inspection. Use `namespace:local-name` for qualified identity displays. Preserve Framework diagnostics, source labels and business data verbatim.
- Carry the same pair through submission validation, the queued record and worker handoff. Preserve authorization, capacity, trust rechecks and the timing of generation capture. Keep the existing annotated `default:http` executor.
- Update server-owned fixtures, authored references and HTTP contract documentation affected by naming. For physical definition paths, exercise the existing startup and archive loading boundaries. Supplied document labels are diagnostic text.

## Acceptance criteria

- [x] Through authenticated HTTP, a caller can list `default`, get a skill such as `lookup-3pl` using its returned pair, submit it, and poll the accepted ID. The expected skill executes and the polled identity and file-startup inspection expose both components.
- [x] Malformed identity components or an overlength pair return 400 on applicable routes. A well-formed absent namespace or skill returns 404 even when the local name exists under a different owner. Existing authorization outcomes hold. The old single-name routes and fallback spellings are gone.
- [x] Sidecar integration coverage exercises the exact pair through queued handoff and the existing HTTP executor against the locally installed Framework snapshot. File and database configurations still activate under `default`.
- [x] Sidecar startup rejects uppercase physical skill paths and bundle import rejects invalid uppercase skill entries. A valid ordinal archive path with a mixed-case diagnostic label retains that label. Server contract examples and schemas describe the new names and routes directly.

## Context

Use the [portable naming handoff](../future/portable-skill-naming.md) and the repository-pinned Framework snapshot. This ticket establishes the wire contract for the [SDK and examples ticket](2026-11-24-sdk-and-example-portable-skill-names.md). Multi-namespace publication, Cloud adoption and changes to queue or publication policy are outside scope.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change replaces public HTTP and serialized metadata contracts and carries identity across admission and execution.
- **Reassessment triggers:** Reassess if the current checkout already implements these contracts and only verification or documentation remains.
