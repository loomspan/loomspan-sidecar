# SDK v1 Phase 0 — Reference contracts

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

Settle contracts and acceptance gates before implementation tickets depend on them.
Produce a concrete reference design and matrix; do not implement product behavior
or mistake unrun scenarios for completed support.

## Scope and decisions to settle

- Select Java/Spring/runtime baselines, blocking/async APIs and servlet/reactive
  scope. Preserve the standalone HTTP core and independent SDK Maven build/release.
- Define Relay, Exchange, Spring-issued and Custom identity configuration, failure
  behavior and shared issuance. Select a named local exchange server/version,
  registration and flow. No strategy fallback or replacement customer login service.
- Settle issuer/audience/token-purpose profiles, group issuer trust against the
  single-issuer baseline, rejection of ID/wrong-purpose and sender-constrained
  tokens, issuer/subject identity, multiple roles, optional native-account resolver,
  authority precedence, permission freshness, logout and expiry.
- Specify both callback security arrangements, route matchers/ordering/hooks and
  diagnostics. Preserve bearer-only callbacks that ignore session cookies, existing
  application authorization and narrowly scoped CSRF behavior with context cleanup.
- Define protected trust administration and shared management API operations:
  one administrator, cumulative Read/Edit/Publish tokens, interactive-only account,
  token-creation and trust administration, atomic indirect-path enforcement and
  live authority checks. Publish may approve passthrough destinations. Preserve
  editing leases, ownership, revisions and file-mode mutation restrictions.
- Define uploads/JWKS URL trust, retrieval/cache/outage behavior, one signing-state
  owner, optional endpoint and property/bean precedence. Specify public-only file
  export, existing-file skip, atomic explicit replacement and signing-store safety.
  Separate ordinary rollover from emergency distrust/restoration and its actual
  rejection timing, including queued work and independently operated validators.
- Select concrete isolated Compose bootstrap and trusted-group recipes, both mTLS
  client paths, independent callback ingress, explicit peer membership and protected
  local boundaries. Specify file/database bootstrap authority without bypassing
  later interactive trust administration. Define signing/TLS material ownership,
  persistence, provisioning/reload and existing-connection removal behavior.
- Specify SDK submission/polling/result/error contracts, acceptance uncertainty,
  retained IDs, deadlines and safe status-read retry policy. Prohibit automatic
  submission/callback retries and preserve business side-effect uncertainty.
- Preserve unchanged-token passthrough and its reusable-credential limitations,
  restricted recipients/audiences, redirect controls and infrastructure credential
  protections without adding business-data sanitization.
- Map every roadmap acceptance obligation to its phase, owning repository and
  intended scenario/evidence location. Primary families are HTTP/HTTP, HTTPS/HTTPS
  and mTLS/mTLS across all four strategies; both mTLS paths cover isolated/group
  deployments. Include focused mixed transport and no-callback/resumed polling.
- Define test-suite extension boundaries and update its project agreement when
  implementing that work. Preserve existing embedded Java and Python/Sidecar
  examples, fixtures and business behavior. Name the new work SDK v1 so it is not
  confused with that repository's historical Phase 1/2/3 labels. Resolve runtime
  baseline differences before compatibility claims.
- Identify demonstrated Sidecar gaps and any required public Framework contracts
  in their owning repositories. Reconcile the design lens and related plans.

### Design boundaries that tickets must preserve

Choose the simplest setup using maintained security implementations; do not invent
a security protocol. The default isolated recipe must require no manual key/certificate
exchange, certificate-tool commands or new identity server. Automatically generated
JWT signing keys alone do not satisfy certificate provisioning for an mTLS recipe.
Existing customer certificates/trust must remain usable without permissive fallback.
Keep signing and TLS keys separate; Sidecar never holds the application's signing key.

Specify signature/allowed-algorithm/time/required-claim checks as well as issuer,
audience and purpose, independently at admission and callbacks. Define trusted
custom sending and receiving adapters and equivalent Java initialization, not only
Spring bean names. Identity must never come from model input or arbitrary request
fields, and tokens must not serialize sessions/passwords/arbitrary security objects.

Select actual Java, Spring, Docker, Caddy and local-provider versions and local test
commands/tiers, starting from the roadmap's Caddy 2.10.2 baseline. Proposed settings,
adapter names, `skills.execute` and the example five-minute lifetime are not mandates.
Settle signing-store/replica coordination and alternative signer integration explicitly;
JWKS refresh alone is not automated private-key rotation. Identify the AWS, Azure,
Google Cloud and hosted-provider recipes and their separate manual verification needs.
Prefer standard material delivery over cloud-specific SDK code absent a proven need.

Management authoring APIs in Sidecar are in scope; a management-authoring SDK,
code generation, streaming and server cancellation are not assumed v1 SDK features.
Other languages and new Python-example functionality remain outside this work.
Retain the root Sidecar build and public Framework boundary, including ArchUnit
enforcement; no new SPI or internal-bean replacement follows from SDK planning.

### Roadmap coverage and delivery owners

This table assigns the roadmap's detailed obligations; linked sections remain
normative, including every acceptance scenario within them. Phase 0 designs all
rows. Phases 1–3 implement and supply evidence; Phase 4 reconciles complete coverage.
The concrete test matrix produced by Phase 0 must expand these families into named
scenarios and owning tickets, not treat a row as a single happy-path test.

| Roadmap requirement family | Implementation and evidence owner |
| --- | --- |
| [Transport composition and isolated setup](2026-10-11-java-spring-sdk-roadmap.md#v1-transport-composition-and-deployment-patterns) | Phase 1: direct HTTP/HTTPS and isolated mTLS, automatic bootstrap and customer-managed inputs. Phase 3: restart/recovery and lifecycle. |
| [Both mTLS client paths](2026-10-11-java-spring-sdk-roadmap.md#agreed-direction-two-v1-mtls-client-paths) | Phase 1: isolated; Phase 2: group; Phase 3: material/connection lifecycle. Retain both paths across all four strategies and no-callback cases. |
| [Shared security and credential integration](2026-10-11-java-spring-sdk-roadmap.md#sdk-managed-jwt-and-user-security) | Phase 1: all four strategies, custom adapters and async context. Phase 2: group issuer trust; Phase 3: freshness/expiry. |
| [Both callback security arrangements](2026-10-11-java-spring-sdk-roadmap.md#agreed-callback-security-composition--2026-10-15) | Phase 1: route/chain composition, authorization, bearer/session isolation, scoped CSRF, errors and context cleanup. |
| [Identity and authority mapping](2026-10-11-java-spring-sdk-roadmap.md#agreed-issuer-aware-identity-and-authority-mapping--2026-10-15) | Phase 1: default/resolved identity and ownership; Phase 2: issuer collisions under the planned group trust contract; Phase 3: live changes. |
| [Execution-token compatibility](2026-10-11-java-spring-sdk-roadmap.md#agreed-v1-execution-token-compatibility--2026-10-15) | Phase 1: purpose and sender-constraint validation at both boundaries; Phase 2: enforcement under group/mTLS configurations. |
| [Passthrough trust boundary](2026-10-11-java-spring-sdk-roadmap.md#agreed-unchanged-token-passthrough-trust-boundary--2026-10-15) | Phases 1/2: isolated/group and relevant mixed-path positive and negative evidence, including controlled reuse and leakage/redirect checks; Phase 3: captured-token expiry. |
| [Business-data responsibility](2026-10-11-java-spring-sdk-roadmap.md#v1-business-data-responsibility-and-no-sanitization) | Phase 1: content/diagnostic fidelity and infrastructure protections; Phase 2: proxy-path checks; Phase 4: accurate user guidance. |
| [Console trust, management access and optional JWKS](2026-10-11-java-spring-sdk-roadmap.md#agreed-verification-key-configuration-and-optional-jwks-endpoint) | Phase 1: console/API trust sources, management tokens and authorization, shared signing/publication and optional endpoint. Phase 3: rotation, cache/outage, revocation and emergency distrust. |
| [Public JWKS file export](2026-10-11-java-spring-sdk-roadmap.md#agreed-public-jwks-file-export--2026-10-15) | Phase 1: both interfaces, disabled/no-listener behavior, safe writes and errors; Phase 3: key-change/withdrawal snapshots and repeated startup. |
| [SDK functionality](2026-10-11-java-spring-sdk-roadmap.md#sdk-functionality) | Phase 1: all eight areas, including diagnostics/TypeSafe conversion and resource ownership; Phase 3: reload/lifecycle; Phase 4: clean-consumer ergonomics. |
| [Submission uncertainty and side effects](2026-10-11-java-spring-sdk-roadmap.md#agreed-submission-uncertainty-and-side-effect-semantics--2026-10-15) | Phase 1: outcome distinctions, attempt counts, retained IDs and application duplicate-protection fixture; Phase 2: group proxy paths; Phase 3: no replay on lifecycle recovery. |
| [Local test boundary](2026-10-11-java-spring-sdk-roadmap.md#agreed-local-integration-test-boundary--2026-10-15) and [primary/focused matrix](2026-10-11-java-spring-sdk-roadmap.md#agreed-primary-transport-evidence-matrix--2026-10-15) | Phase 0: exact matrix, versions and commands. Phases 1/2: isolated/group scenarios; Phase 3: lifecycle; Phase 4: all required local evidence. |
| [Enterprise/cloud recipes](2026-10-11-java-spring-sdk-roadmap.md#enterprise-and-cloud-integration) | Phase 0: recipe/manual-check scope; Phase 3: local material integration and documented external checks; Phase 4: honest compatibility status, without a cloud release gate. |
| [Ownership, builds and sample baseline](2026-10-11-java-spring-sdk-roadmap.md#ownership-and-current-evidence) | Phase 0: baseline/dependencies; Phase 1: independent SDK build and Java-to-Sidecar sample; Phase 4: packaging, compatibility and independent security review. |

## Dependencies and completion

PR-8.0.1 supplies the scoped
[identity/callback reference](2026-10-15-sdk-v1-identity-callback-reference.md) and
[named planned scenarios](2026-10-15-sdk-v1-identity-callback-scenarios.md).
These settle credential/identity/callback policy, subject to independent review;
they do not prove runtime support or complete this phase. PR-8.0.2 supplies the
[trust/bootstrap/lifecycle reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md)
and [named planned scenarios](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md)
against those policy fields; these settle contracts without proving runtime support.
PR-8.0.3 supplies the [API/runtime contract](2026-10-15-sdk-v1-api-reference.md),
[versioned deployment recipes](2026-10-15-sdk-v1-deployment-recipes.md) and
[complete scenario ownership matrix](2026-10-15-sdk-v1-acceptance-matrix.md).
They select Java 21/Boot 4.1.0, standalone blocking/async HTTP core and servlet-only
callbacks, independent SDK packaging, concrete outcome/read policies and local tiers.
All APIs, future commands, scenarios and descriptive owning units remain planned;
documentation completion establishes no runtime support. Units become implementation
tickets only after their material public-contract dependencies are resolved.

Inputs are the roadmap and its security research. Output is a reference design
with resolved material choices, concrete contracts, supported configurations and
an acceptance matrix. Record any developer decision still needed rather than
quietly selecting a weaker requirement. Do not create dependent implementation
tickets while their material contracts remain unresolved.

Completion requires internally consistent contracts, explicit component ownership,
selected runtime/provider versions and local test tiers/prerequisites, and a
traceable scenario matrix. Claims about current support need evidence; planned
commands and tests remain labelled planned.

## Intended ticket boundaries

Derive bounded contract-design tickets after reviewing this phase: identity and
callback composition; trust/bootstrap/lifecycle; SDK outcomes and reference
deployment/evidence contracts. Consolidate where a split would duplicate decisions.
These are candidate boundaries, not tickets or authorization to start a pipeline.

