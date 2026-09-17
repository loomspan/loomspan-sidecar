# Notify applications when a skill generation is safe to retire

## Outcome

An application using one fixed RestSkillHandler can safely retire the route
configuration and HTTP clients associated with an obsolete skill generation,
without tracking framework execution trees itself or accessing framework internals.

## Requirements

- Add a supported public framework notification contract identifying the retired
  generation by the same process-local ID exposed to REST invocations and catalogs.
- A successful publication alone must not signal safe retirement. A superseded
  generation remains usable by previously admitted roots, including pending
  admissions and all nested/parallel descendants, until their ownership is released.
- Notification must mean that no existing or future framework invocation can use
  that generation's external resources. A quiet interval between REST calls is
  insufficient; an old root may call another REST child later.
- An application that subscribes through the documented initialization sequence
  must not miss retirement of a generation it staged before publication, including
  an immediately superseded generation with no executions. Document delivery,
  subscription, threading and ordering guarantees so applications can implement
  idempotent resource cleanup without a check/subscribe race.
- Define retirement behavior for normal completion, failed execution, abandoned
  pending admission, overlapping publication, and shutdown. Do not describe a
  deadline or cancellation request as proof that uncooperative code stopped using
  resources. Distinguish safe retirement from any forced shutdown cleanup policy.
- Consumer cleanup failures must not change an invocation result, undo publication,
  prevent other eligible generations from retiring, or create another shutdown
  drain budget. Document notification error handling.
- Keep ownership of application resource creation and destruction in the application.
  Do not add a REST-specific resource manager or depend on Sidecar types.
- Preserve existing generation capture, authorization and single framework shutdown
  budget. Stay within the deliberately extended supported ai.loomspan.api surface.
- Document how never-published/rejected candidates differ from retired published
  generations; applications must be able to clean up unused staged resources.

## Acceptance criteria

- [ ] Public-surface integration evidence demonstrates that publishing B while A
  has a pending admission or running nested/parallel work does not retire A early.
- [ ] After A is superseded and its final ownership is released, the application
  receives a notification identifying A and can close its resources safely.
- [ ] Rapid successive publications, including unused generations, do not lose
  retirement notifications through the documented subscription sequence.
- [ ] Failures, pending-admission release and shutdown have tested behavior matching
  the documented guarantees, including the distinction between deadline expiry and
  actual safe retirement.
- [ ] A throwing notification consumer does not corrupt publication, execution
  outcomes or subsequent retirement processing.
- [ ] An application example uses one fixed REST handler with generation-keyed
  resources and notification-based cleanup, without internal framework imports.
- [ ] Public documentation describes registration timing, delivery/error semantics,
  shutdown behavior and caller ownership of rejected candidate cleanup.

## Context

Destination repository: loomspan-framework. This ticket was authored in Sidecar
for the developer to move into the framework repository; it must stand alone there.

Sidecar is planning an embedded management console that validates and publishes
skills and matching REST configuration without restarting. The framework currently
exposes SkillReloader, PreparedSkillUpdate, generation IDs, and a fixed
RestSkillHandler receiving the captured ID. Its reload documentation explicitly
does not provide an external-artifact retirement signal. Internal generation
reclamation does not tell the application when its routes and clients can close.

Callback versus event implementation and exact API names belong to framework
planning. This ticket requests the observable safety contract, not a particular
listener mechanism. It does not request backup-history retention or Sidecar code.
Coordinate with the companion application-supplied skill lifecycle ticket so the
initial application-controlled generation has the same retirement semantics.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Extends the supported public contract and changes generation
  lifecycle, concurrency and shutdown behavior.
- **Reassessment triggers:** None that justify reducing rigor; re-evaluate scope
  if the current framework already supplies part of this contract.
