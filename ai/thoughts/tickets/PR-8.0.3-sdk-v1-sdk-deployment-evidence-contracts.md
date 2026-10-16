# Complete SDK v1 API, reference deployment and acceptance contracts

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Finish Phase 0 with an internally consistent reference design, concrete SDK outcome
contracts, reproducible planned deployment/test recipes and a traceable acceptance
matrix. Implementation owners must know what to build and prove without treating
unrun tests, proposed settings or Framework-only evidence as completed SDK support.

## Requirements

- Select actual Java, Spring and runtime versions, blocking/async APIs and
  servlet/reactive scope consistent with the preceding identity/trust contracts.
  Preserve a standalone HTTP core without embedded Loomspan or required Spring Boot,
  SDK ownership in `sdks/java/`, independent Maven build/version/release and the root
  Sidecar build. Resolve test-suite runtime differences before compatibility claims.
- Define discovery/descriptors, JSON-object submission, immediate accepted execution
  handles, status by ID, bounded polling/wait, resource ownership/cleanup and reusable
  connections. Specify typed envelopes, unchanged result text, snapshot IDs,
  validation/problem details and available diagnostic history. Optional JSON/typed
  and TypeSafe conversion failures retain execution/result context without reruns.
  Define safe timing/identifier observability without default payload/credential
  logging or complete/durable-trace promises.
- Define explicit API rejection, demonstrable pre-send failure, acceptance unknown,
  transport/protocol/polling errors, timeout/interruption and terminal execution
  failure even with HTTP 200. Lost/unusable acceptance responses may leave work
  running without a known ID; proxy errors alone do not establish rejection.
  Preserve reliable IDs/details. Specify retryable safe status reads, bounded backoff
  and total wait deadlines separately from HTTP timeouts. Wait termination retains
  IDs for resumed lookup and neither cancels nor resubmits. Preserve status-404 ambiguity.
- Prohibit automatic submission and callback retries in SDK/Sidecar clients and
  reference proxies, including refresh/recovery paths; document equivalent external
  proxy obligations. No blind-retry guidance, server submission deduplication,
  exactly-once guarantee or guaranteed lost-ID recovery. A callback effect can commit
  before response loss or a later failure; execution failure implies no rollback.
  Specify a planned synthetic fixture demonstrating application-owned stable business
  operation IDs, atomic duplicate handling and reconciliation, separate from transport
  retry prevention and deliberate repeated skill actions.
- Select concrete isolated and trusted-group Compose recipes using the established
  bootstrap/lifecycle contract. Pin Docker/Compose, Caddy and local provider versions
  and prerequisites, starting from roadmap Caddy 2.10.2; explain any baseline change.
  Include protected direct HTTP, direct HTTPS and both client-Caddy and SDK-native
  mTLS invocation paths with independently selected callback ingress. No callback
  listener/TLS is needed for invocation/polling alone; retain client Caddy on its
  invocation path and omit it on the native path. Optional JWKS hosting is separate.
- State per-hop transport guarantees, verified chain/hostname checks, no downgrade,
  explicit workload membership and Sidecar-specific callback peer policy where
  required. Broad CA trust, co-location or unpublished ports alone do not establish
  membership/isolation. Prevent inbound bypass and borrowing outbound proxy identity;
  separate management/browser ingress without execution bypass. Keep proxy peer
  certificate interpretation outside Sidecar Java and callback server TLS in the
  application ingress/server, not a new SDK server. Group mTLS retains Sidecar Caddy.
- Preserve all four credential strategies, existing customer authentication and
  authorization, unchanged-token reusable-credential limits, restricted recipients,
  redirect controls and infrastructure secret protections in each recipe. No new
  business-data sanitization; caller/callback content and existing public diagnostics
  retain fidelity. Generated signing keys do not substitute for TLS provisioning.
- Expand every roadmap acceptance obligation into named scenarios, not one happy-path
  row per family. Map each to its source, phase, owning repository, named owning unit
  and intended test/recipe/evidence location. Identify units as planned until their
  dated implementation tickets exist; do not invent completed tickets or tracker IDs.
  Consolidate the two preceding tickets' scenarios and identify cross-repository
  dependencies without creating implementation tickets for unresolved contracts.
- Primary evidence families are HTTP/HTTP, HTTPS/HTTPS and mTLS/mTLS across Relay,
  Exchange, Spring-issued and Custom identity. Both mTLS client paths cover isolated
  and group deployments and all strategies. Add focused mixed transport, including
  mTLS invocation/protected HTTP callback, and no-callback/resumed-polling across
  all four strategies and both mTLS paths. Map all applicable negative, authorization,
  retry/uncertainty, lifecycle, export and management scenarios; justify shared
  component evidence without replacing real integration for promised compositions.
- Define exact planned local commands/tiers, prerequisites and deterministic fixtures:
  no paid model calls or hosted-service dependencies, but real JWT/JWKS, local Exchange
  server, TLS/mTLS and SDK/Sidecar/application/proxy boundaries. Dependency/image
  acquisition is setup, not an offline-installation promise. Focused SDK/Sidecar tests
  belong here; complex Compose/topology/fault-injection evidence belongs in
  `C:/opendev/code/loomspan-sidecar-test-suite`. Record versions/revisions,
  configuration, command and result for evidence; Framework tests alone do not prove
  Sidecar behavior. Each later implementation unit owns tests and documentation.
- Define test-suite extension boundaries and the required project-agreement update
  when implementation starts. Preserve existing embedded Java and Python/Sidecar
  examples, fixtures and business behavior while adding a deliberate Java-to-Sidecar
  path. Name new work SDK v1, distinct from historical Phase 1/2/3 labels. Other
  languages, new Python functionality, management-authoring SDK, code generation,
  streaming and server cancellation are not included v1 deliverables.
- Identify AWS, Azure, Google Cloud and named hosted-provider recipes with explicit
  separately authorized manual verification procedures/status. Prefer standard
  files/keystores/SSL bundles; cloud-specific SDK code needs a demonstrated unmet
  requirement, and non-exportable signers need an explicit integration contract.
  Local material tests do not establish cloud issuance/delivery/renewal or commercial
  provider compatibility. Unrun external checks limit claims, not local release gates.
- Reconcile reference contracts, design lens and related plans; identify demonstrated
  Sidecar gaps and necessary public Framework changes in their owning repositories.
  Retain `ai.loomspan.api`-only use, authorized `RestSkillHandler`, ArchUnit exclusions
  for internal/autoconfigure packages, no new SPI or internal-bean replacement, and
  no Sidecar Java `@SkillMethod` skills. Preserve documented namespace ownership.

## Acceptance criteria

- [ ] A consistent reference design selects runtime/API/web scope and independent
  SDK packaging, reconciles sample-suite baseline differences and defines all eight
  roadmap SDK-functionality areas with concrete API/configuration/error contracts.
- [ ] Outcome tables and named fault scenarios distinguish acceptance uncertainty,
  explicit rejection, proven pre-send failure, retained-ID polling/wait outcomes,
  HTTP-200 execution failure and conversion failure. Planned attempt-count evidence
  covers response loss through clients/proxies without replay; bounded reads and
  the application duplicate-protection fixture preserve the stated limits.
- [ ] Versioned isolated/group recipes specify both mTLS paths, direct and mixed
  transports, no-callback operation, independent ingress, protected local boundaries,
  explicit peers and lifecycle responsibilities without default manual key/certificate
  work or weakened trust. Per-hop guarantees and passthrough/data limits are explicit.
- [ ] A scenario-level matrix traces every roadmap acceptance obligation, including
  preceding security/trust scenarios, to phase, repository, named owning unit and
  intended evidence location. All primary families/strategies, both isolated/group
  mTLS paths and focused mixed/no-callback cases are represented; omissions or shared
  evidence have explicit justification and no required scenario is silently dropped.
- [ ] Planned commands/tiers/prerequisites and evidence attribution are concrete;
  local deterministic release gates use real security boundaries. Test-suite
  extension/project-agreement requirements preserve existing examples and explicitly
  separate SDK v1 work from historical phase labels and excluded SDK features.
- [ ] Cloud/provider recipes have named manual checks and honest unverified status;
  local evidence cannot be misread as external compatibility. Phase 4 obligations
  include clean-consumer packaging, complete local matrix, closure of temporary
  breakage and independent security review, followed by authorized release checks.
- [ ] Phase 0 closes only with resolved material contracts, consistent ownership,
  versions and dependencies. Lens/related plans are reconciled; missing public
  Framework contracts and required developer decisions are explicit blockers to
  affected implementation planning rather than permission to weaken requirements.

## Context

Roadmap work item: GitHub PR #8. Ticket identifier: `PR-8.0.3`.
SDK v1 tickets use `PR-8.[phase].[ticket]` identifiers.

Depends on completed [identity/callback](PR-8.0.1-sdk-v1-identity-callback-contracts.md)
and [trust/bootstrap/lifecycle](PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md)
contracts. Integrate their decisions rather than independently redesign them.
Normative inputs are [Phase 0 and its ownership table](../phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md),
the [complete roadmap](../phases/2026-10-11-java-spring-sdk-roadmap.md) and
[security research](../phases/2026-10-11-established-security-architectures.md).
Use [Phase 1](../phases/2026-10-15-sdk-v1-phase-1-isolated-integration.md),
[Phase 2](../phases/2026-10-15-sdk-v1-phase-2-trusted-group.md),
[Phase 3](../phases/2026-10-15-sdk-v1-phase-3-lifecycle-deployment.md) and
[Phase 4](../phases/2026-10-15-sdk-v1-phase-4-release-readiness.md) for delivery ownership.
Their full detailed acceptance scenarios remain normative.

Sidecar development is `1.0.0-beta.4-SNAPSHOT`, next planned release/tag
`1.0.0-beta.4`/`v1.0.0-beta.4`; SDK and Framework versions are independent.
Keep `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.8` pinned to the tested
release and consult local Framework tag `v1.0.0-beta.8`, not stale installed docs.
Future Framework snapshot work uses the developer-installed artifact aligned with
the local checkout, without extra artifact/source verification or normal Sidecar
Framework rebuilds. Prove affected integration locally; a required new Framework
release must reach Maven Central before final Sidecar dependency/build/test/commit
checks. Hosted checks follow publication/sync before release; never overwrite releases.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** SDK API/outcome, transport and cross-repository evidence design has
  material unresolved choices; the final Phase 0 contract needs discovery and review.
- **Reassessment triggers:** Reassess if approved reference contracts and a complete
  traceable matrix already exist; formatting an existing matrix alone is narrower work.

## Pipeline notes

- Deliver reference documentation and planned recipes/scenarios, not production
  code or the later test-suite implementation. Review completeness/consistency;
  label unrun commands and scenarios planned, never passing support evidence.
  Record unresolved developer decisions and do not create dependent implementation
  tickets until their material contracts are settled.
- The developer commits after each ticket and syncs only after all phases. No commit,
  push, release or external compatibility run is authorized here; hosted CI is not
  an intermediate gate. No code/build breakage is intended by this design ticket.
  Later tickets permitting intermediate breakage must record impact, feasible checks
  and a named restoring unit; all deferred breakage must close before final readiness.
