# SDK v1 Phase 3 — Lifecycle and deployment

**Development policy:** Destructively replace superseded contracts, code and
schemas. No compatibility shims, legacy adapters, dual legacy APIs or migration
machinery solely to preserve obsolete development behavior. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt); choose the simplest
complete solution and keep technical debt and process proportional. Document
required development-data resets and impact; planning authorizes no data deletion.

## Delivery and evidence rules

This document defines a phase, not an implementation ticket or completed evidence.
The [roadmap](2026-10-11-java-spring-sdk-roadmap.md) and
[security research](2026-10-11-established-security-architectures.md) retain their
detailed agreed requirements and acceptance scenarios. These phase documents
organize that scope; they do not replace or reduce it.

Intermediate tickets and phases may deliberately break code, builds or tests that
a later unit restores. Do not add compatibility scaffolding to keep every
intermediate commit green. Each affected ticket must record the known breakage,
its impact, verification still possible, and the named dependent unit that restores
it. Report expected failures honestly, distinguishing them from unexpected defects.
A phase can hand off documented temporary breakage; its affected acceptance evidence
remains outstanding until restored and verified. No deferred breakage may remain at
final release readiness.

The developer commits after each ticket and will not sync to the remote until all
phases are complete. Agents do not commit, push or release without separate
authorization. Local validation must support this workflow; hosted CI is not an
intermediate-phase gate. After sync, verify required hosted checks before release,
following the repository's Framework publication/dependency policy.

Tests live with the responsibility they exercise:
- This repository owns Sidecar tests and the independently built SDK's focused
  tests in `sdks/java/`. Small Docker Compose smoke/contract checks may live here
  when they provide focused local feedback without duplicating the system matrix.
- `C:/opendev/code/loomspan-sidecar-test-suite` owns the complex Docker Compose
  scenarios: real SDK, Sidecar, application, local identity provider and proxies,
  transport combinations and cross-service lifecycle/fault injection.
- Framework tests alone never establish Sidecar integration. Evidence recorded in
  the test-suite repository must identify the tested SDK/Sidecar/Framework versions
  or local revisions, configuration, command and result. This is reproducible test
  attribution, not an extra verification of locally installed Framework artifacts.
- Each implementation ticket owns necessary tests and documentation even when its
  integration evidence belongs in the other repository. Assign cross-repository
  dependencies explicitly; do not postpone all testing to the final phase.
- Routine acceptance is local and deterministic, with synthetic business operations
  and no paid model calls or live hosted-service dependencies. Use real authentication,
  TLS and service boundaries. Cloud/hosted-provider checks remain separately
  authorized manual compatibility evidence, outside local release gates.

For roadmap GitHub PR #8, name tickets `PR-8.[phase].[ticket]-<slug>.md` (ticket numbering starts at 1 within each phase). Create tickets using [write_ticket](../../commands/write_ticket.md) after
the relevant phase is sufficiently specified. Apply Full/Fast/Direct eligibility
to each ticket; security, contract and lifecycle changes generally require Full.

## Outcome

Show that the supported configurations remain secure and predictable across
restart, expiry, rotation, trust withdrawal and changing user/workload authority.

## Scope

The [trust/bootstrap/lifecycle reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md)
and [planned scenarios](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md)
are normative for signer publication barriers, durable withdrawal, independent
validator clocks and TLS generation/connection retirement. Phase 1 supplies initial
security enforcement and Phase 2 group extension; this phase implements the lifecycle
mechanisms and stress/fault evidence, rather than treating configuration as proof.

Implement any lifecycle mechanisms assigned here by Phase 0 or deferred explicitly
by Phase 1/2 tickets, then verify them. This phase owns production changes needed
for renewal/reload, signing rotation, emergency distrust and recovery; it is not a
test-only phase that assumes those mechanisms already exist. Preserve the
[roadmap coverage assignments](2026-10-15-sdk-v1-phase-0-reference-contracts.md#roadmap-coverage-and-delivery-owners).

- Verify first-start unique signing keys, protected storage, bootstrap authority,
  restart persistence and safe missing/corrupt-state recovery. No silent identity
  reset or bootstrap restoration of withdrawn trust.
  Include independent-deployment uniqueness, private-key access boundaries and
  callback trust. Automated setup must satisfy the no-manual-key/certificate-exchange
  default; customer-managed keys/trust remain supported alternatives.
- Complete certificate renewal, private-key and CA-trust rotation separately from
  JWT signing-key rollover. Exercise both mTLS paths, native reload and proxy
  material delivery with pooled/in-flight connections, invalid updates and expiry
  recovery; no refresh/recovery may replay submissions or callbacks.
- Verify membership removal on existing and new connections according to the
  Phase 0 timing contract, including separate management ingress.
- Verify public-key publication before signing-key use, overlap/retirement,
  uploaded and URL-based trust, restart/cache/retrieval failures and independent
  callback-validator trust. Cover optional endpoint/export consistency, existing
  export skip after key change and explicit replacement without signing-state mutation.
  Exercise both property and Java export interfaces against publishable overlap and
  withdrawn-key snapshots, and the selected signing-state/replica coordination policy.
- Prove emergency withdrawal separately from routine rotation: actual rejection
  timing/exposure window, cached keys, outages, restart, queued/admitted work and
  rejected restoration via stale refresh/import/rollback. Restoration requires
  explicit interactive administration.
- Verify queue/callback token expiry, unchanged captured credentials despite SDK
  renewal, logout, permission changes, disabled accounts, revocation and authority
  rechecks after waits. Preserve issuer/subject execution ownership and context cleanup.
- Test management token scope/lifecycle and indirect trust-mutation denials, including
  stale drafts, without weakening authorized Publish destination changes.
- Verify customer-managed material locally. Cloud/provider-specific delivery,
  issuance and renewal claims require separate manual evidence; local mounts do not
  prove them. Do not run external services or paid evaluations under this phase.
  Document the AWS, Azure, Google Cloud and selected hosted-provider recipes and
  their manual verification procedures/status. Prefer standard files/keystores or
  SSL bundles; cloud-specific code requires a demonstrated unmet requirement.

## Test ownership and completion

Keep focused state/error/authorization regressions in this repository and SDK.
The test suite owns Compose restart/rotation/outage/member-removal scenarios and
cross-service fault injection, with synthetic clocks/fixtures where compatible
with proving real validation and transport behavior.

Completion requires passing applicable lifecycle and failure scenarios for the
agreed isolated/group paths, reproducible commands and operational recovery
documentation. Close prior tickets' deferred lifecycle breakage/evidence; identify
any remaining release dependency explicitly. Claims state measured enforcement
timing and limitations rather than instantaneous revocation or exactly-once effects.

## Dependencies and intended ticket boundaries

Depends on Phase 1/2 implementations and Phase 0 lifecycle contracts. Candidate
units separate TLS/connection lifecycle, signing/trust lifecycle, identity and
management revocation, and integrated deployment recovery. Each unit includes
tests where behavior is owned and cross-service evidence where required.

