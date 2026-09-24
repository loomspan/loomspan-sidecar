# Make shutdown verification storage writable on Linux CI

## Outcome

The production verifier must run the completion and cutoff shutdown cases on
Linux CI with Sidecar running as UID/GID 10001:10001.

## Context

GitHub CI passed production database restore, then the shutdown fixture failed
its 60-second readiness wait before sending SIGTERM. The fixture copied the
database into a host-owned bind mount without correcting ownership. Container
startup logs were skipped because its container ID was assigned only after
readiness and execution admission. The log establishes the readiness failure;
it does not contain the underlying application exception.

## Requirements

- Copy the stopped database and optional WAL/SHM files without modifying the source.
- Use an isolated disposable Compose volume, writable by the image's non-root UID.
- Keep the existing readiness and shutdown assertions and timeouts.
- Collect startup logs before cleanup even when readiness fails. Diagnostic
  collection failure must not mask the original error or prevent cleanup.
- Leave production application behavior and CI service identity unchanged.

## Acceptance criteria

- Both completion and cutoff Compose configurations validate.
- Full local production verification reaches and passes both shutdown cases.
- A Linux filesystem check proves that the initialized copy is writable by UID
  10001 while the source remains unchanged.
- Startup failure prints fixture logs and still attempts Compose cleanup.

## Execution profile

Recommended: Direct Implementation — No Independent Review
Confidence: high
Rationale: Localized test-fixture storage and diagnostic repair using the
existing production restore ownership convention; no runtime lifecycle change.
Reassessment triggers: A required application or framework behavior change.

## Execution notes

- Worktree was clean at the start. Implementation is limited to the verifier,
  its regression check and CI invocation, operational documentation, and this record.
- Direct implementation within the requested CI fix; no independent review.

## Verification

- PASS: `python scripts/verify-shutdown.py --image loomspan-sidecar:sc5-local --validate-only`
  validated completion and cutoff Compose configurations.
- PASS: `python scripts/test_verify_shutdown.py` checked startup failure diagnostics,
  cleanup, and preservation of the original error when log collection times out.
  CI now runs this regression check before building the image.
- PASS: `python scripts/verify-production.py --image loomspan-sidecar:sc5-local`
  exited 0, including production restore and both shutdown cases. Completion
  exited 143 in 2.34s with nested work finished; cutoff exited 143 in 4.48s with
  nested work unfinished. Log: `target/verify-production-shutdown-fix.log`.
- PASS: An isolated Linux container check executed the initializer command
  captured from the verifier. It first demonstrated that UID 10001 could not
  write a directory owned by UID 12345, then verified the initialized database,
  WAL and SHM were readable/writable by UID 10001 and a journal could be created.
  SHA-256 checks and ownership/mode checks confirmed the root-owned source files
  remained unchanged at mode 400. The test container was removed automatically.
- PASS: `python -m py_compile scripts/verify-shutdown.py scripts/test_verify_shutdown.py`
  and `git diff --check`.
- GitHub-hosted CI has not been rerun; full production verification ran locally
  against the existing local image. No application or framework source changed.
