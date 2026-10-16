# SDK v1 Phase 4 — Release readiness

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

Make the SDK usable by a clean consumer and establish complete local acceptance
evidence before the developer syncs the completed work and performs release checks.

## Scope

- Verify independent Maven packaging and clean-consumer usage, SDK/Sidecar/Framework
  compatibility facts and the standalone HTTP core. Keep package versions independent.
- Complete setup, callback, trust, export, credential strategy and operational guidance
  for direct HTTP/HTTPS and both mTLS paths, including mixed/no-callback applications.
  Cover local/Compose export retrieval and interactive upload, customer-managed
  trust, actual withdrawal timing, and customer-proxy verification of no automatic
  submission/callback retries. Document application-owned duplicate protection,
  diagnostic limits and business-data responsibility without promising sanitization.
- Align examples with tested behavior and preserve the test suite's existing business
  contracts, embedded reference and deterministic fixtures.
- Reconcile the acceptance matrix with actual evidence across all four strategies,
  primary transport families, isolated/group mTLS paths, mixed/no-callback checks and
  required rejection/lifecycle cases. Close every documented intermediate breakage.
  Use the [roadmap coverage assignments](2026-10-15-sdk-v1-phase-0-reference-contracts.md#roadmap-coverage-and-delivery-owners)
  to check for unowned obligations, including management authorization, both callback
  chains, both verification-key sources and both export interfaces. Full-matrix
  evidence belongs in the test suite; focused checks here must not substitute for it.
- Obtain independent security-focused review of implemented boundaries and actual
  evidence; planning documents and Framework tests do not substitute for that review.
- Document unsupported sender-constrained execution credentials, reusable passthrough
  limits, callback side effects, acceptance uncertainty, local prerequisites and any
  unverified hosted-provider/cloud recipes. Do not market unrun compatibility claims.
- Respect the pinned Framework dependency and repository release policy: prove
  affected integration locally, publish Framework before final Sidecar release
  dependency/build checks when a new Framework release is required, and verify hosted
  checks after publication/sync before release. Never overwrite a release.

## Test ownership and completion

This repository supplies focused tests, builds, packaging and SDK consumer checks.
The test suite supplies the full system acceptance and deployment/lifecycle evidence
against the intended artifacts. Record versions/revisions, commands, configuration
and results so evidence from another repository remains attributable.

Before final sync, all required local checks pass, all agreed v1 capabilities have
their required evidence, and temporary failures from earlier commits are resolved.
Unrun required local checks block readiness; separate manual cloud/provider checks
limit their compatibility claims rather than block the local release gate.

Completion of local readiness is not publication. The developer controls commits
and sync; release and any external validation need their own authorization. Required
hosted release checks remain explicitly outstanding until they can run.

## Dependencies and intended ticket boundaries

Depends on Phases 0–3 and all deferred restoration work. Candidate units cover
clean-consumer/package validation, sample/documentation completion, acceptance
evidence reconciliation and independent security review/fixes. Use actual remaining
work to form tickets rather than creating empty process tickets.

