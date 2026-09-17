# Verify request input limits and completed-task cleanup

## Outcome

Establish Sidecar integration evidence that request limits hold without a
declared content length, queued input accounting measures actual UTF-8 bytes,
and completed tasks release input and security references. These checks close
the input and retention evidence gap without changing the agreed API behavior.

## Requirements

- Verify raw request size enforcement for unknown-length/chunked requests,
  including the configured limit and an oversized request.
- Verify multibyte UTF-8 input through HTTP into queued-byte accounting. Distinguish
  byte size from character count and preserve the existing accounting contract.
- Directly assert that completed tasks release their retained input and security
  references. Retained results and selected diagnostics must continue to follow
  the existing retention policy; completion must not simply erase the record.
- Treat this as an acceptance-evidence gap, not a confirmed runtime defect.
  Correct demonstrated violations of the existing requirements with regression
  coverage; do not introduce new limit, retention, or authorization policies.
- Use only the supported public framework API in application and test code.
  Verify against the developer-installed beta 4 snapshot. Framework defects
  belong in the framework repository and require a developer snapshot reinstall
  before affected Sidecar checks are repeated.
- Record commands, results, tested revisions and dirty changes, and any remaining
  gaps in the delivery handoff. Update the scoped QA item only as evidence permits.

## Acceptance criteria

- [x] Unknown-length/chunked requests at the configured raw request cap are
  accepted when otherwise valid; requests exceeding it are rejected according
  to the existing API contract.
- [x] HTTP requests containing multibyte UTF-8 input demonstrate correct
  queued-byte accounting and enforcement of the existing queue byte limit.
- [x] Direct assertions establish that completed tasks no longer retain their
  input/security references while required results and diagnostics remain available.
- [x] Relevant existing tests and new regression checks pass using only supported
  framework contracts, with no unintended API, authorization, or retention change.
- [x] The delivery handoff contains the local verification evidence and accurately
  reflects whether all three scoped gaps have been resolved.

## Context

User work-item reference: **Request 3**.

The [delivery handoff](../beta4-handoff.md) remains the sole tracker of remaining
Sidecar QA and release work. Its input and retention item reports existing
coverage for fixed-length cap equality/excess, numeric queue accounting, and
discarded-task cleanup. Those checks do not prove the three cases above.

Apply the [design lens](../design-lens.md), including one admission owner and
one retained record, trusted identity outside model inputs, and proportional
implementation complexity. Other handoff QA items and release/publication work
are outside this ticket. Local snapshot evidence does not establish release
verification.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** low
- **Rationale:** The intended outcomes are settled, but the conversation does not
  establish the integration seams or verification approach needed for these
  boundary and reference-release assertions. Discovery and test planning remain.
  Testing existing behavior alone does not change its contract.
- **Reassessment triggers:** Current-checkout triage may support fast-track if
  existing test facilities make the work bounded and the implementation direction
  clear. Any required changes to lifecycle, concurrency, security boundaries, or
  external behavior retain the full profile.

## Execution notes

- Selected profile: Fast-Track 2-Step Pipeline — Implementation & Review, confirmed
  by the developer after Step 0 triage. The change is test and handoff evidence
  only; no production behavior or framework API changed.
- Pre-existing dirty REST fixture tests, the snapshot alignment handoff section,
  and unrelated untracked phase/ticket files were preserved. This ticket owns
  the three execution test changes and the input/retention handoff update.
- Local verification and tested revisions are recorded in the delivery handoff.
