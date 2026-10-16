# SDK v1 Phase 2 — Two-way mTLS trusted group

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

Prove an explicitly authorized group of workloads can invoke Sidecar and receive
callbacks with workload authentication and peer policy in both directions while
retaining independent user and record authorization.

## Scope

The [trust/bootstrap/lifecycle reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md)
and [planned scenarios](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md)
extend Phase 1's same finite policy/schema to group issuers and distinct workload
membership. Issuer trust and workload membership remain independent; Phase 3 adds
renewal/removal-under-load mechanisms and evidence with the stated connection gates.

- Implement Phase 0 group recipes with distinct workload credentials, explicit
  admission policy and approved callback routes. Broad CA trust alone is not group
  membership; prevent inbound bypass and borrowing outbound proxy identities.
- Support both client-Caddy and SDK-native mTLS invocation across all four credential
  strategies, with the planned issuer/audience model for independently issuing apps.
- Keep peer certificate interpretation in deployment proxies, outside Sidecar Java.
  Separate management/browser ingress without opening an execution bypass.
- Prove Sidecar-specific callback peer policy where required, user authorization
  and the reusable passthrough-token boundary. An approved member can reuse a valid
  token when admission/audience/permissions allow; do not promise origin-only
  callbacks or isolation between mutually untrusted applications.
- Preserve independently selected transports. Describe mixed configurations by
  their actual per-hop guarantees, not as two-way mTLS. No-callback workloads retain
  invocation requirements without callback ingress.
- Establish baseline membership changes and connection behavior; assign full
  renewal, invalid-update and removal-under-load evidence explicitly to Phase 3.

## Test ownership and completion

The test suite owns multi-application/group Compose topologies, both mTLS paths,
four-strategy scenarios, nonmember rejection, callback peer restrictions, bypass
attempts and focused mixed/no-callback checks. Keep narrow policy/configuration
regressions near their owning code here where useful; do not clone the topology matrix.

Carry forward the [assigned roadmap scenarios](2026-10-15-sdk-v1-phase-0-reference-contracts.md#roadmap-coverage-and-delivery-owners),
not only successful group requests. Prove identical subjects from different issuers
remain distinct, issuer-specific roles do not transfer authority implicitly and
local-account resolution cannot merge execution ownership. Validate token-purpose
and sender-constraint rejection even for admitted workloads with valid certificates.
Cover controlled authorized token reuse, credential redirect/leakage protections and
no automatic submission/callback replay through each group reference-proxy path.
Count attempts after response loss rather than infer retry behavior from configuration.

Both mTLS paths and all four strategies apply to group no-callback invocation and
resumed polling. Mixed transport includes independently selected protected HTTP
callbacks; it does not inherit two-way mTLS guarantees. Reuse justified Phase 1
component evidence, but retain real group integration for promised compositions.

Completion requires real SDK/Caddy/Sidecar/application evidence, documented
membership and route policy, independent user/record authorization and per-hop
guarantees. Configuration files or a single happy-path request are insufficient.
Record deferred Phase 3 lifecycle evidence without claiming completed assurance.

## Dependencies and intended ticket boundaries

Depends on settled Phase 0 group contracts and the relevant Phase 1 SDK/admission
capabilities. Candidate units are proxy/group deployment, issuer-trust integration
and test-suite group/rejection scenarios. Coordinate any necessary Sidecar changes
with their focused tests. Phase 3 consumes both isolated and group deployments.

