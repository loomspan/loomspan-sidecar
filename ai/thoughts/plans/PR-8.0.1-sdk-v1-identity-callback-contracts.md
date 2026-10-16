# SDK v1 identity and callback contracts Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/PR-8.0.1-sdk-v1-identity-callback-contracts.md`
- Research: `ai/thoughts/research/PR-8.0.1-sdk-v1-identity-callback-contracts.md`
- Outcome: reviewed normative reference contracts and named planned scenarios; no SDK or Sidecar product implementation and no claims of passing future integrations.
- Selected execution profile: Full 5-Step Pipeline, explicitly approved.

**Development policy:** Destructively replace superseded development contracts;
no compatibility shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). This documentation
unit requires no data reset and authorizes no deployed-data deletion.

## Current State

`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java`
(`sidecarJwtDecoder`, `requiredClaims`, `sidecarJwtAuthenticationConverter`) and
`SidecarJwtProperties.java` implement a single issuer, timestamp/issuer/audience
validation and one role claim/prefix. They do not explicitly enforce access-token
purpose, all sender constraints or per-issuer authority allowlists. These are
demonstrated gaps, not permission to introduce an SDK through framework internals.

`security/ExecutionOwner.java` and `execution/ExecutionCoordinator.java`
(`admit`, `find` and the handoff execution path) retain issuer/subject ownership and captured JWTs.
`rest/GenericRestSkillHandler.java` (`handle`) forwards the exact captured token;
`rest/RestTargetClients.java` disables redirects, retries and cookies. No SDK exists
in `sdks/java/` beyond `.gitkeep`. Existing management/browser chains do not prove
SDK callback chain composition. Framework beta.8 public `SkillInvocationHandoff`
and `RestSkillHandler` support the existing handoff; no new public SPI is presently
needed to specify these contracts.

Verified build baseline: `pom.xml` selects Java 21, Boot 4.1.0, Sidecar
1.0.0-beta.4-SNAPSHOT and Framework 1.0.0-beta.8. Final SDK runtime compatibility
is PR-8.0.3's decision, subject to this unit's security constraints.

## Desired End State

One dated reference defines configuration and equivalent Java contracts, independent
validators, identity and authority policies, supported callback composition and
unchanged passthrough. One adjacent matrix assigns every relevant detailed roadmap
obligation to named planned evidence, phase and repository. Roadmap/lens references
identify this as the settled refinement rather than leaving competing illustrative
names and rules. Unsupported configurations fail closed; unresolved material choices
block affected sign-off instead of becoming implicit implementation assumptions.

## Scope
### In scope
- `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-reference.md` — normative contracts, decisions, source-backed rationale, Sidecar gaps and handoffs.
- `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md` — named, explicitly planned positive/negative scenarios and owners.
- Existing roadmap, Phase 0, lens and PR-8.0.1 ticket — narrow links/reconciliation, acceptance status and evidence limitations.

### Out of scope
- Product/configuration/schema changes, SDK implementation, runtime tests for unimplemented contracts, external services, commits, pushes, release and hosted CI.
- Trust storage/admin/bootstrap/key lifecycle mechanisms (PR-8.0.2), final build/runtime/deployment/full-matrix consolidation (PR-8.0.3).
- Creating future implementation tickets before Phase 0 material contracts settle.

## Active Project Guardrails
- Preserve JWT-only user identity outside model inputs, exact `(iss, sub)` ownership and unchanged captured credentials; Sidecar never mints/exchanges/refreshes them.
- Framework owns execution authorization and lifecycle. Use only closed `ai.loomspan.api` and the supported REST SPI, with existing ArchUnit prohibitions intact.
- Maintain management/execution separation, browser CSRF/session behavior, endpoint/method/record checks and no business-data sanitization.
- Use documented Framework `loomspan.*` only for Framework behavior, Sidecar `loomspan-sidecar.*` for Sidecar behavior and a distinct SDK namespace for proposed SDK settings. Standard Boot SSL namespace remains standard.
- One publication/generation owner and the existing shutdown budget remain intact; no parallel authority or queue drain policy is introduced.
- Preserve the developer's existing PR-8.0.1/.2/.3 heading edits. No version/dependency or data changes.

## Impact and Risk Analysis

The primary risk is documenting a security guarantee that current implementation
cannot prove. Separate current source observations, normative future contracts and
planned evidence. Provider documentation is not interoperability evidence. State
exact supported token purposes/algorithms rather than trusting a signature, roles,
generic JWT header or workload certificate. Define startup failures and request
rejections for unprovable callback-chain/session isolation; do not promise arbitrary
Spring chains or silently reduce the two required arrangements to one.

Issuer trust must be finite and explicitly configured: never discover arbitrary
issuers/keys from an unverified token. Trust/member lifecycle remains PR-8.0.2's
responsibility. Native identity resolution must retain the verified pair and never
merge ownership or privileges. Ordinary short-lived bearer credentials necessarily
remain reusable; logout/permission rules must not promise immediate revocation
under a token snapshot policy.

## Implementation Approach

Select Keycloak **26.4.0 standard V2 same-realm token exchange** as the local Exchange
reference, using its tag-pinned guide/source identified in research. Define a
confidential requester with standard exchange enabled, client-secret authentication
over protected transport, compatible ordinary bearer access-token input, explicit
requested access-token type and a resulting audience/role profile. Require requester
audience on the subject token and preconfigured scope/audience availability; reject
unsupported providers, inputs, client auth and incompatible results without fallback.
The reference must include reproducible realm/client/mapping registration details,
not only a token-endpoint URL. Local cleartext provider traffic is allowed only
inside the documented protected test boundary; remote token exchange requires TLS.

Settle two explicitly named accepted token profiles: application-issued execution
access JWTs using a dedicated purpose/header contract and Keycloak access JWTs using
documented payload `typ=Bearer` rather than generic JOSE `typ=JWT`. Independently
specify issuer/key/algorithm/time/required claims, audiences and purpose at Sidecar
and callbacks. Reject every `cnf` presence (including null, malformed and unknown),
DPoP authentication and unsupported proof requirements, even with valid mTLS. Do not
assert RFC 9068 compatibility unless its full claim rules are deliberately selected.

Keep Spring-issued and Custom identity on one maintained issuance component;
customers supply trusted identity/permission integration, never token signing or
filters. Define typed immutable verified-pair/default principal/native-resolution
contracts and equivalent Java/property examples. Token-mapped snapshot authority
is the explicit default; live account permissions require an explicit resolver
policy with unambiguous precedence (no automatic union). Document disabled-account,
logout and expiry limits separately for each policy and for already-admitted work.

Use the following planned SDK concepts in the reference (not new Framework SPIs):
`VerifiedIdentity(issuer, subject)`, `VerifiedCaller(identity, mappedAuthorities)`,
`CustomIdentitySource.currentIdentity()` returning a trusted subject plus selected
authorities, `NativeAccountResolver.resolve(VerifiedCaller)` returning a native
principal/account status/live authorities or a failure, and a custom receiving
adapter that consumes only validated caller/native-resolution output. Define how
the receiving scope is closed in `finally`, including exceptions. An application
receiving adapter must not select keys, accept raw unverified identity, add arbitrary
token roles or bypass SDK validation. The SDK's shared `ExecutionTokenIssuer`
is selected by both `spring-issued` and `custom`; it is not customer JWT code.

Document `loomspan-sdk.credentials.strategy` values `relay`, `exchange`,
`spring-issued`, `custom`, and equivalent explicit Java strategy construction.
For Relay include the authenticated access-token source and accepted profile;
for Exchange the provider/token endpoint/requester/client-secret source/subject
source/requested audiences/scopes/profile; for both issuance strategies the shared
issuer/signing-state reference/lifetime/audiences/role mapping and the distinct
Spring-authentication or custom-source adapter. No raw credential literals belong
in examples. Define `CallbackSecurityConfigurer` and a dedicated-chain factory
over the same callback policy; both take explicit route matcher, validator,
identity/authority policy and application authorization hook. Final package/type
spelling can follow ordinary SDK naming conventions, but every field, input,
output, failure and Java/property equivalence must be settled in the reference.

Plan a finite per-issuer trust/profile/role map for independently issuing group
applications, as a deliberate future Sidecar gap; mTLS membership does not create
issuer authority. PR-8.0.2 owns its storage/admin/lifecycle. No multi-issuer support
is claimed from proposed settings. A single approved issuer remains usable without
the group extension; all four strategies must remain covered by later group evidence.

Specify servlet callback support first, grounded in this repository's MVC baseline;
explicitly bound async propagation/cleanup with supported context capture wrappers
and MVC lifecycle paths. Reactive callback security is unsupported by this reference,
not silently inferred from servlet APIs; PR-8.0.3 must reconcile final scope and may
expand it only with an equally concrete contract and evidence. Preserve a standalone
HTTP core and explicitly selected async use without inheritable global contexts.

Both dedicated and application-owned callback arrangements use the same validator/
mapper and request-scoped bearer isolation. The mixed-chain recipe must select
session/context repositories, authentication filters, entry points and CSRF rules
per callback matcher before any session identity is loaded; simple filter addition
after browser session loading cannot satisfy the requirement. List supported
configuration restrictions, startup diagnostics and integration obligations for
opaque/custom matchers that cannot be proven safe automatically.

## Phase 1: Write the reference security contracts

### Changes
- [x] `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-reference.md` — add `Credential strategies and configuration`, `Local Exchange profile`, `Admission validation`, `Callback validation`, `Identity and authorities`, `Issuer trust model`, `Callback composition`, `Lifetime and unchanged passthrough`, `Implementation ownership and dependencies` and `Decisions and sign-off` sections.
- [x] Specify concrete Java signatures for credential source/shared issuer/custom sending adapter/verified identity/native resolver/custom receiving integration and callback configurer/authorization hooks. Explain trusted inputs, error outcomes, no fallback and the native-principal/pair relationship.
- [x] Include equivalent explicit Java and Spring setup for each strategy and both callback arrangements; mark APIs/settings as planned, not installed Framework APIs. All setting choices must map to Java initialization or clearly identify application bean wiring.
- [x] Include independent validation tables and full local provider registration/flow/output/failure profile. Include exact audience membership, allowed algorithms, time/skew/lifetime and purpose rules without contradictory defaults.
- [x] Define per-issuer authority mapping and live/snapshot precedence, disabled/logout/renewal/queue semantics and receiving URL/method/record checks. Specify exact supported asynchronous boundaries and reject unsupported chain combinations.

### Automated verification
- [x] `git diff --check` — no whitespace errors in tracked documentation edits.
- [x] Execute the testing plan's Markdown-link/path check over both new documents and touched normative documents — all relative local targets and files exist.

### Optional developer checks
- None; independent content review is a required pipeline gate, not an optional check.

## Phase 2: Map acceptance and reconcile standing guidance

### Changes
- [x] `ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md` — add named scenarios with concrete inputs/outcomes, evidence boundaries, owners and phase. Every row starts planned; no component test establishes unrun cross-service support.
- [x] `ai/thoughts/phases/2026-10-11-java-spring-sdk-roadmap.md` — link settled identity/callback refinements and mark corresponding illustrative configuration/names and remaining decisions as superseded by the reference; preserve every acceptance obligation.
- [x] `ai/thoughts/phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md` — link the scoped output and outstanding PR-8.0.2/.3 handoffs, without marking all Phase 0 complete.
- [x] `ai/thoughts/design-lens.md` — reconcile the trusted identity guardrail with explicitly planned issuer profiles/authority policies and unchanged passthrough. Distinguish future group trust from current single-issuer support; do not invent a current-support claim.
- [x] `ai/thoughts/tickets/PR-8.0.1-sdk-v1-identity-callback-contracts.md` — record artifact links, scoped decisions and acceptance completion only after documentation review/verification; preserve its heading. No edits to .2/.3 tickets are necessary.

### Automated verification
- [x] `git diff --check` and testing-plan link/scenario-ID checks — no dangling links, duplicate IDs or missing required scenario families.
- [x] `git diff --name-only` plus `git status --short` — only authorized documentation changes beyond preserved original ticket-heading edits; `pom.xml`, production/test trees and build settings unchanged.

### Optional developer checks
- None. Running Keycloak, SDK or group transport integrations belongs to implementation phases, not this unit.

## Test Strategy

Documentation structure/link/traceability checks plus an independent security-focused
semantic review are the appropriate boundaries. No red runtime test or new Java test
is appropriate for a documentation-only design. The test plan specifies future
deterministic component and real integration scenario obligations, separately labelled
planned. Existing Sidecar tests may substantiate existing behavior only; they cannot
prove SDK contracts. No full Maven/Docker/hosted suite is needed for an unchanged
product tree; broaden verification only if implementation unexpectedly changes it.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| AC1: Four strategies/shared issuance/Java and Spring/trusted adapters/named local Exchange/no fallback | Reference strategy, interface and provider sections; no code changes | STRATEGY and EXCHANGE families; equivalent-config review |
| AC2: Independent validation/profile/rejection tables, purpose/ID/sender constraints across transports | Reference admission/callback tables and issuer profiles | TOKEN family, including overlapping ID audiences and every cnf shape, transport expansion |
| AC3: Identity/roles/resolution/pair ownership/freshness/logout/disabled/expiry | Reference identity and lifetime policies | IDENTITY and FRESHNESS families |
| AC4: Both chain arrangements, matchers/order/hooks/diagnostics/session isolation/authorization/CSRF/JWKS/cleanup | Reference callback recipes and supported boundaries | CALLBACK family parameterized over both arrangements, sequential/concurrent/error/async |
| AC5: Approved reuse/limited audiences/peer restrictions/redirect-leakage/unchanged captured credentials | Reference passthrough and destination policies | PASSTHROUGH family with controlled reuse and credential canaries |
| AC6: Roadmap coverage/owners/Sidecar gaps/Framework dependencies/reconciliation/no unresolved material choices | Reference ownership/decisions and linked scenario matrix; narrow roadmap/lens edits | Every relevant roadmap clause mapped to named planned evidence and phase/repository; independent review |

## Risks and Rollback/Recovery

No production behavior, persisted state or release changes; no development-data reset
needed here. If review rejects a design detail, revise the normative reference and
linked plans/matrix together before sign-off. Withdraw only this unit's new documents
and reconciliation edits if abandoned, preserving pre-existing developer edits. Any
required later schema/config reset must be specified by its implementation/trust
ticket; planning does not authorize deleting deployed data. No unresolved material
developer decision is known; source/provider contradictions discovered during writing
must be resolved or escalated, not hidden in future work.

## References
- Ticket and research linked above.
- [Phase 0](../phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md).
- [Roadmap security contracts](../phases/2026-10-11-java-spring-sdk-roadmap.md#sdk-managed-jwt-and-user-security).
- [Security research](../phases/2026-10-11-established-security-architectures.md).
- `pom.xml`; `.github/workflows/ci.yml`; matching Framework beta.8 tagged public APIs/references identified in research.
