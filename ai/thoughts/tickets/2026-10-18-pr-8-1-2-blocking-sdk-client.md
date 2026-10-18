# PR 8.1.2: Blocking SDK client with composed no-replay evidence

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Java applications can discover skills, submit executions, read status and wait
for completion against a real Sidecar through one small blocking client. Every
HTTP request is sent once. Uncertain outcomes are reported honestly, and server
results are returned exactly as the server provides them. Composed evidence in
the external suite shows a real execution flow and no automatic replay after a
lost response.

## Requirements

### Client surface (loomspan-sdk-core)

- Provide one blocking client with these operations. The names describe the
  intended small surface; DTO organization is free.

  ```java
  interface SidecarClient extends AutoCloseable {
      List<SkillDescriptor> listSkills(CredentialSnapshot credential);
      SkillDescriptor getSkill(String name, CredentialSnapshot credential);
      AcceptedExecution submit(String name, ObjectNode input, CredentialSnapshot credential);
      ExecutionSnapshot getExecution(UUID id, CredentialSnapshot credential);
      ExecutionSnapshot await(UUID id, CredentialSnapshot credential, WaitOptions options);
  }
  ```

- Not in scope: async variants, custom futures, reactive support, streaming,
  server cancellation, SDK thread-context wrappers, capture managers,
  thread-local scopes and automatic credential refresh.
- The credential value is a small immutable holder of an execution JWT.
  - Token bytes stay out of ordinary getters, serialization, diagnostics and
    `toString`.
  - The client does no client-side JWT verification. It sends the token
    unchanged.
  - Issuing the token belongs to PR 8.1.3. This ticket accepts an
    already-issued token.
- Discovery returns public skill metadata and schemas.
- Submit:
  - Requires a JSON object input.
  - Rejects an invalid transport shape locally, without sending a request and
    without compiling business schemas.
  - Encodes the skill name as a single path segment.
  - A valid acceptance response returns its UUID immediately, without waiting
    for completion.
- Status:
  - Builds status requests under the configured base URL. Never follows
    `Location` URLs or redirects to another destination.
  - Returns the public execution envelope: ID, skill, status, timestamps, result
    string, failure, configuration snapshot ID and available events.
  - Preserves the available failure details and event fields without
    reconstructing internals.
  - Validates the envelope and the expected execution ID, and never silently
    switches IDs.
- **Results are opaque text.** Preserve the exact decoded result string,
  including empty, non-JSON and malformed JSON-looking text. Never parse,
  schema-validate or sanitize it, and never infer success from it. Only the
  server's COMPLETED/FAILED status determines the outcome.
- `await`:
  - Polls successful QUEUED/RUNNING responses until COMPLETED or FAILED, or
    until a finite deadline, at a configurable polling interval. Defaults:
    60-second timeout and 250-millisecond interval.
  - Every network call fits within the remaining total deadline.
  - A failed read stops the wait immediately.
  - It never resubmits.

### Outcomes and errors

| Observation | Required behavior |
| --- | --- |
| Local validation or credential failure before send | Local failure; no request sent |
| Valid accepted response | Return the execution ID; completion is separate |
| HTTP error from server or proxy | Preserve status and problem details; do not assert that no work occurred |
| Submission response lost, incomplete or unusable after a possible send | Acceptance unknown; work may be running; never resubmit |
| Status read error or invalid envelope | Preserve the requested ID, a safe error and, when waiting, the last valid snapshot |
| Wait deadline or thread interruption | Stop waiting locally; preserve the ID and last snapshot; server work may continue |
| Status FAILED | Return the terminal failed snapshot with its primary failure; no inferred rollback |

- Error types distinguish local failure, HTTP failure, protocol failure,
  acceptance unknown and wait termination. Do not build a public state machine
  for transport stages.
- Restore the interrupt flag on interruption.
- Timeouts, 404/401/403 responses and client close never cancel server work.
- Keep an execution ID only when it was reliably parsed. Never guess one from
  partial bodies or arbitrary headers, and do not reconcile `Location` against
  body candidates.
- Errors and default logging never expose credentials or business payloads.

### Transport, configuration and resources

- Attempt each HTTP request exactly once. Disable automatic retries, redirect
  replay and authentication-rechallenge replay. There is no read-backoff or
  rejection-origin subsystem.
- Configuration:
  - A base URL is required and must not contain userinfo, a query or a fragment.
  - Connect and response timeouts must be finite.
  - Starting defaults: 2-second connect timeout, 10-second response timeout,
    8-MiB response size limit.
  - Exceeding the size limit is a protocol error, never silent truncation.
  - Expose only settings with a concrete use.
- Use standard TLS with certificate and hostname verification. Callers can
  supply standard trust material: a Java `SSLContext` in core, and Spring Boot
  SSL configuration in the starter. There is no insecure switch and no
  transport-mode enum.
- The client owns its connection pool. It always releases response resources,
  closes idempotently and rejects new operations after close. There is no
  public executor ownership or observability listener API.
- The starter wires the client from `loomspan-sdk.*` properties and behaves
  exactly like direct Java configuration.
- Use maintained HTTP and JSON libraries. Apache HttpClient 5 and Jackson are
  suitable starting points.

### Composed evidence (loomspan-sidecar-test-suite, SDK lane)

- A plain-Java driver that depends only on locally built `loomspan-sdk-core`
  runs against the real Sidecar from the SDK lane:
  - one real submit-to-terminal COMPLETED flow, with the result string preserved
    exactly;
  - one FAILED execution, with its primary failure preserved;
  - one lost-response case. A small reference proxy forwards the submission and
    drops or truncates the response. The SDK reports acceptance unknown.
    Separate counters show one request attempt and the synthetic effect
    happening at most once, with no resubmission.
- The reference proxy itself never replays requests.
- Authenticate these scenarios with a token signed by a test key, using
  Sidecar's **current** execution-JWT configuration. PR 8.1.4 removes that
  configuration and PR 8.1.5 moves these scenarios to the new trust contract.
  This is test-fixture wiring only. Add no production code to support it.
- Uses deterministic model fixtures and no provider calls. Records evidence in
  the lane's format.

### Documentation

- Document the client operations, outcomes and error semantics, opaque results,
  waiting, no-replay behavior, configuration defaults and the TLS expectation in
  the SDK's documentation.

## Acceptance criteria

- [x] Discovery, submit, status and `await` work against focused HTTP fixtures.
- [x] Each row of the outcomes table is shown by a focused test.
- [x] Results are returned byte-for-byte, including empty, non-JSON and
      malformed JSON-looking strings. Only server status decides
      COMPLETED/FAILED.
- [x] Invalid input shapes and path-unsafe names never send a request. Names
      are encoded as one path segment. Redirects and `Location` URLs are never
      followed.
- [x] A mismatched execution ID or invalid envelope is a protocol error that
      preserves the requested ID.
- [x] `await` respects the total deadline, including network calls, and stops
      on the first failed read. Interruption restores the interrupt flag and
      preserves the ID and last snapshot.
- [x] Every request is attempted exactly once: no retries and no redirect or
      re-authentication replay.
- [x] Oversized responses are protocol errors. Response resources are released.
      Close is idempotent and later operations are rejected.
- [x] Base-URL and timeout validation and the stated defaults are enforced.
      TLS verifies certificates and hostnames and has no insecure option.
- [x] Credential token bytes never appear in `toString`, serialization, errors
      or default logs.
- [x] Starter property wiring produces the same client behavior as Java
      configuration.
- [x] The plain-Java suite driver shows a real COMPLETED flow, a real FAILED
      flow and a lost-response flow. The lost-response flow reports acceptance
      unknown with one attempt, at most one effect and no resubmission.
- [x] SDK documentation covers the operations, outcomes, waiting, opaque
      results, no-replay and configuration.

## Context

- Depends on PR 8.1.1 (SDK reactor and SDK lane).
- Canonical sources:
  - [API reference](../phases/2026-10-15-sdk-v1-api-reference.md)
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md): "SDK core" row (focused and composed) and the plain-Java consumer under "Framework/build boundary"
  - [Phase 1](../phases/2026-10-15-sdk-v1-phase-1-implementation-integration.md), delivery group 2
- Nonbinding hint: planning picks the synthetic effect counter. Examples are a
  test endpoint the deterministic skill reaches, or the number of executions
  Sidecar reports. It must count effects separately from request attempts.
- Out of scope: business-operation ledgers, deduplication, exactly-once
  promises, crash-recovery fixtures, async or cancellation-race suites, and
  retry or backoff matrices.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This creates a new supported public API with external HTTP
  protocol, error and resource-lifecycle contracts. It also adds a cross-repo
  composed scenario.
- **Reassessment triggers:** none

## Pipeline notes

- Using Sidecar's current execution-JWT configuration in the suite scenario is
  intentional and temporary. PR 8.1.4 removes that configuration, which breaks
  this scenario, and PR 8.1.5 restores it on the new trust contract. Do not add
  production compatibility code to keep it working.
- The work spans the SDK in `loomspan-sidecar` and the composed scenario in
  `loomspan-sidecar-test-suite`. This is intentional.

## Execution notes (2026-10-18)

Step 4 implementation and verification cover all acceptance criteria; the approved
Full 5-Step Pipeline still requires fresh independent review. See the matching
implementation/testing plans and `ai/thoughts/validation/2026-10-18-pr-8-1-2-blocking-sdk-client.md`
for executable mapping and actual local composed evidence. The core API is one final
blocking client with immutable values, five error categories and no replay/cancellation
API. Visible-ASCII credential transport validation and duplicate-field JSON rejection
keep invalid transport values and ambiguous acceptance IDs local/unknown as appropriate.
No production or Framework behavior, release pin, compatibility path or persisted data
was changed. The external lane retains current JWT fixture wiring intentionally.
