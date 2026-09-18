# Sidecar design lens

Standing design constraints for Sidecar planning, implementation and review.

## Simplicity and technical debt

Standing guidance for our work together:

> Remember that we are choosing the simplest solution that still covers the requirements.
> We are working to avoid technical debt where possible. Also we are in development
> so destructive changes are welcome. No compatibility shims.

- **Decision:** Choose the simplest solution that fully satisfies the current
  requirements. Prefer existing capabilities and direct implementations. Add
  abstractions, configuration, dependencies or workflow artifacts only for a
  concrete need; speculative future needs are insufficient justification.
  Keep code, tests, documentation and process proportional to the work.
  Replace obsolete development code, configuration and schemas directly rather
  than preserving them with compatibility shims.
- **Why this matters:** Complexity consumes implementation, review and
  maintenance effort. Minimize technical debt; accept it only when its concrete
  benefit outweighs its expected maintenance and future change costs. For
  deliberate debt, briefly record the tradeoff and any necessary follow-up in
  the existing ticket or plan rather than creating another tracking artifact.
- **Applies to:** Planning, implementation, testing, documentation and review.
  Review should look for unnecessary work and opportunities to simplify as
  well as correctness. Simplicity does not excuse unmet requirements or omitted
  necessary verification.
- **Exceptions:** More complex solutions are warranted when required for
  correctness or another current requirement; explain the reason briefly.

## Framework remains the skill execution authority

- **Decision:** Use the public catalog, validation, invocation, observer and named
  REST SPI. The framework owns skill validation, authorization, nesting and
  execution lifetime; Sidecar owns transport, admission and retention.
- **Why this is non-obvious:** Re-parsing manifests or replacing internal beans
  can appear to simplify startup or HTTP error handling while creating a second
  authority and an unsupported dependency.
- **Applies to:** All integration, production and test code.
- **Exceptions:** New framework contracts require deliberate developer planning.

## One admission owner and one retained record

- **Decision:** Coordinate worker/queue capacity and queued bytes with one owner;
  publish terminal outcome and selected history together in the same record.
  Count/TTL bounds and input byte limits are distinct; neither bounds total heap.
- **Why this is non-obvious:** Separate semaphores, staging queues and history
  stores can duplicate authority and publish partial outcomes.
- **Applies to:** Execution admission, diagnostics and shutdown cleanup.
- **Exceptions:** None in beta 4.

## Trusted identity stays outside model inputs

- **Decision:** JWT-only inbound identity is propagated through queued execution
  via Spring Security. Ownership uses issuer and subject. Passthrough forwards
  the captured credential; Sidecar never mints, refreshes or exchanges tokens.
- **Why this is non-obvious:** Request-thread authentication is lost at ordinary
  executor boundaries; input-carried identity is not a trusted substitute.
- **Applies to:** Execution API, authorization, ownership and REST callbacks.
- **Exceptions:** Test issuers may mint local fixture tokens.

## Startup activation and one framework shutdown budget

- **Decision:** Skills/routes load at startup and change on restart. Sidecar
  immediately stops dispatch and discards queued work on close; framework
  shutdown owns already-admitted work. Keep clients/callers alive until its
  completion/cutoff, then clean up without another drain period.
- **Why this is non-obvious:** Queue draining, ordered listeners or early client
  teardown can defeat the framework's existing deadline and nested work.
- **Applies to:** Configuration, workers, HTTP clients, readiness and packaging.
- **Exceptions:** None in beta 4; reload requires new planning.

## Preserve the available diagnostic contract

- **Decision:** Return text unchanged and retain all selected public observer
  events without Sidecar size truncation. Preserve the facade's primary failure.
  History is available completed diagnostics, not a guaranteed complete trace.
- **Why this is non-obvious:** An observer may never run after pre-session or
  mapping/finalization failures. Waiting for it or parsing internal traces creates
  unsupported behavior. Diagnostic events may contain business data.
- **Applies to:** Failure envelopes, diagnostics, retention and client guidance.
- **Exceptions:** NEVER/ONERROR selection and whole-record completion TTL remain
  the agreed policies; no new sanitization or durable-history contract.
