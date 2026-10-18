# Java/Spring SDK v1 roadmap

Reconciled 2026-10-18 from the agreed direction change and scope discussion.
This replaces the earlier roadmap; it does not preserve its accumulated obligations.

**Development policy: replace superseded contracts, code and schemas directly.
No compatibility shims, legacy adapters or parallel obsolete APIs.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). This planning rewrite
requires no data reset and authorizes no deletion of deployed data.

## Goal and scope

Provide a small Java HTTP SDK for discovery, submission, status and bounded waiting,
with optional Spring integration for execution-token issuance and callback authentication.
Reuse the application's existing login and business authorization.

**If apps share a Sidecar, they trust each other in every way:** identities, roles,
tokens, business data and network participation. Use either a 1:1 localhost pairing
or a fully trusted group with one signing identity, issuer, user directory and role
vocabulary. Isolation requires separate Sidecars; there is no roadmap for mutually
untrusted applications sharing one Sidecar.

V1 has a blocking client only. There are no async client APIs, custom futures,
thread-context wrappers, automatic request retries or business-operation ledger.
Submit returns on acceptance; bounded wait polls status without resubmitting.
Simple attempt/effect counters test the no-replay contract.

SDK issuance uses one implementation with Spring-authenticated or trusted custom
identity. Signing is ephemeral colocated or operator-provided RSA; Sidecar reads
one current JWKS file for one issuer. SDK authenticates callbacks; application
method annotations and business checks authorize them. Management accounts and
tokens remain unchanged.

Three composed recipes bound testing: Local HTTP, Standard direct HTTPS and
Hardened Caddy-managed mTLS. Other compositions remain permitted and untested;
there is no product allowlist or requirement to match transport levels.
DevOps owns TLS certificate lifecycle. JWT trust-file reload and signing-key
rotation are part of this work.

## Canonical documents

Each detail has one owner; phase documents organize delivery without adding scope.

| Document | Owns |
| --- | --- |
| [Security architecture](2026-10-11-established-security-architectures.md) | Trust boundaries and rationale |
| [API reference](2026-10-15-sdk-v1-api-reference.md) | Blocking operations, outcomes, packaging |
| [Identity and callbacks](2026-10-15-sdk-v1-identity-callback-reference.md) | Issuance, claims, roles, callback security |
| [Signing and trust](2026-10-15-sdk-v1-signing-trust-reference.md) | Keys, current-file authority, expiry and recovery |
| [Deployment recipes](2026-10-15-sdk-v1-deployment-recipes.md) | Three tested network configurations |
| [Acceptance matrix](2026-10-15-sdk-v1-acceptance-matrix.md) | Bounded tests and owning components |

## Delivery

1. [Phase 0: reconcile contracts](2026-10-15-sdk-v1-phase-0-reference-contracts.md).
   This document rewrite is Phase 0; no ticket is needed.
2. [Phase 1: implementation and integration](2026-10-15-sdk-v1-phase-1-implementation-integration.md).
   Establish the harness early. Each feature supplies its focused tests and necessary
   composed coverage, including trusted-group and JWT lifecycle cases. Reuse or
   extend existing scenarios across tickets rather than requiring a new one per ticket.
3. [Phase 2: completion verification](2026-10-15-sdk-v1-phase-2-completion-verification.md).
   Check builds, consumer packaging, integration, documentation and independent
   security review. This is not a deferred feature or test phase.

All implementation/testing is **planned, not completed evidence**. No tickets are
created by this reconciliation. When requested later, use the repository's
`ai/commands` workflow and dated tickets under Sidecar's `ai/thoughts`.
Every ticket states the development policy and design-lens reference above.
Use Full/Fast/Direct eligibility from those commands; security/API implementation
must not be treated as a documentation-only shortcut.

## Build and repository boundaries

Sidecar is currently `1.0.0-beta.4-SNAPSHOT`, pinned to Framework
`1.0.0-beta.8`; no SDK implementation remains after the reset.
Java 21 and Spring Boot 4.1.0 are the SDK build baseline.
The SDK has an independent Maven build and no Framework or Sidecar Java dependency.
Sidecar uses only supported `ai.loomspan.api` types and the authorized
`RestSkillHandler` SPI; retain the ArchUnit boundary against internal/autoconfigure
dependencies. Resolve missing public contracts in the owning repository.

The external test suite gets a separate SDK lane using the actual Sidecar revision
and its pinned Framework dependency. Preserve its existing beta.9 snapshot embedded
and beta.3-based Python/Sidecar lane, business examples and reference fixtures.
Evidence from that lane does not prove SDK integration.

Release versions, timing, tags and publication are outside this effort. Do not
restore discarded implementation or add speculative future features to this plan.
If implementation reveals a requirement that makes scope substantially more complex,
discuss the requirement before expanding the design or test matrix.
