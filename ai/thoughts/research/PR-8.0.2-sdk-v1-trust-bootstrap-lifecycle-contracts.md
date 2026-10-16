---
date: 2026-10-15
repository: loomspan-sidecar
branch: main
commit: ce1e6c59487c04ebb6ce92483679fa56899a5583
ticket: ai/thoughts/tickets/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md
tags: [sdk-v1, trust, management, bootstrap, lifecycle, contracts]
---

# SDK v1 trust, bootstrap and lifecycle research

## Research Question

What exists today for verification trust, management authorization, signing/publication,
bootstrap and credential/connection lifecycle, and which requirements remain design
work for PR-8.0.2? This research describes the checkout; it does not select future
mechanisms or claim performed integration evidence.

Research checklist completed:

- [x] Establish Sidecar state and preceding identity contract.
- [x] Trace current JWT sources and source validation.
- [x] Trace management tokens, operations, editing and publication gates.
- [x] Locate persisted schemas and bootstrap/deployment material.
- [x] Inspect transport construction and queued/admitted execution boundaries.
- [x] Inspect matching Framework beta.8 public source and documentation.
- [x] Map tests, gaps and implementation/evidence boundaries.

## Summary

Current Sidecar verifies one process-configured issuer using a static RSA public
key, configured JWKS URL, or issuer discovery. Console trust administration,
persisted issuer policies, withdrawal state, SDK signing state, public JWKS
publication/export and automated isolated mTLS provisioning are absent from the
inspected production tree. Existing management authoring is substantially implemented:
cumulative bearer grants, hashed secrets, expiry/revocation, mixed-credential
rejection, CSRF, leases, exact revisions and publication gates already exist.

The new one-administrator design differs from today's viewer/editor/admin account
model. Tokens currently have IDs but no human names. Protected issuer trust is
currently outside managed configuration, so current import/publication tests cannot
prove future protected-trust invariants. Source inspection identifies these gaps;
no runtime tests were run in this research step.

## Repository State

- Captured at `2026-10-15T22:04:06.4717182-07:00` (developer timezone).
- Repository `C:/opendev/code/loomspan-sidecar`, branch `main`, HEAD
  `ce1e6c59487c04ebb6ce92483679fa56899a5583`.
- Initial `git status --short` was empty. This research artifact is ticket work;
  no pre-existing developer edits were observed.
- Latest commit: `ce1e6c5 PR #8.0.1 — Settle SDK v1 identity and callback security contracts`.
- Scope is contracts and planned scenarios only; no production changes, data reset,
  dependent implementation tickets, commit, push, release or live service actions.

## Current Behavior and Data Flow

### Verification trust

`SidecarJwtProperties.java:8` binds `loomspan-sidecar.auth.jwt`: issuer, one audience,
optional JWKS URL or public-key resource, 60s skew, roles claim and prefix.
`validate()` rejects missing policy and simultaneous URL/file sources, but does not
define a destination allowlist, URL refresh cadence or persisted source policy.
`JwtSecurityConfiguration.java:36` chooses RSA X.509 conversion, Nimbus URL decoder,
or `JwtDecoders.fromIssuerLocation` respectively. It validates issuer, audience,
timestamps and nonblank issuer/subject plus expiry. Nimbus/Spring supplies URL
retrieval/cache behavior; Sidecar code exposes no durable cache or withdrawal API.
Incoming tokens do not supply the configured decoder URL.

The converter at `JwtSecurityConfiguration.java:73` uses one roles claim/prefix;
there is no finite issuer-specific authority table. Execution routes are stateless
JWT-authenticated at `JwtSecurityConfiguration.java:90`. This is distinct from
management form-login authentication and its server-side session.

### Management credentials and operations

`ManagementBearerFilter.java:35` rejects multiple Authorization headers and a
bearer request with `JSESSIONID` (via `ManagementCredential.java:13`), verifies the
opaque token and active owner, checks a closed route/preset map, then loads the
live account. `required()` at line 82 maps configuration/draft reads to Read;
lease/draft/import/rollback preparation to Edit; publish to Publish. Unknown paths,
account administration, token creation and takeover are not token-authorized.

`ManagementPersonalTokenService.java:19` defines metadata without a display name.
`issue()` at line 43 generates a 16-byte random ID and 32-byte random secret,
returns `lspat_<id>.<secret>` once, and stores SHA-256 of the high-entropy secret.
Default expiry is 7 days and maximum 30 days, with active-count and hourly issuance
limits. Issuance checks active account and credential version inside transition/
editing/transaction gates. Authentication and `valid()` at lines 76 and 86 reject
expired/revoked tokens and inactive accounts; mutation permission intersects
preset with the current account role. Revocation clears that token's lease origin.

`ManagementPersonalTokenController.java:24` takes `{preset, expiresAt}`;
metadata/list and issuance responses use no-store. `V4__management_personal_tokens.sql:1`
persists owner, hash, preset and timestamps, without a name. Account records at
`ManagementAccountRepository.java:9` contain role/enabled/credential version;
`ManagementIdentityService.java:108` supports invitations and line 221 recognizes
viewer/editor/admin. That is current behavior, not the new single-administrator
contract in the roadmap.

`ManagementSecurityConfiguration.java:47` selects only management paths and
retains CSRF except pure bearer requests; form login marks session state. The
execution filter chain is separate. Documentation of current role-based behavior
also exists in `agent-skills/loomspan-sidecar-authoring/references/management-access.md:3`.

### Editing and publication

`ManagementEditingService.java:247` publishes through `RuntimeConfigurationService`
with lease origin/generation, current account, token Publish grant, exact draft
revision/base and matching validation proof. Lines 304–337 recheck lease, account
and token under transition gates. Validation captures state, performs work, then
rechecks the exact candidate (`ManagementEditingService.java:222`).
`RuntimeConfigurationService.java:297` serializes publication and admission under
publication/transition locks. The persisted selection is restart authority; public
Framework prepare/publish produces the process-local runtime generation.

`ManagedConfiguration.java:11` contains skill documents, REST YAML, execution YAML
and credential identifiers. No JWT issuer trust is included. Import and rollback
load candidates through editing services; `ManagementConfigurationImportService.java:30`
and `ManagementConfigurationRollbackService.java:23` require database mode.
`ConfigurationMode.java:7` rejects file-mode configuration mutations. Existing
scope/revision enforcement is evidence of those mechanisms, not proof that
future protected trust cannot enter a draft or be restored indirectly.

### Transport and work lifetime

`RestTargetClients.java:58` builds per-target Apache pooled clients. Selected Boot
SSL bundles become SSL contexts during construction. The source does not register
bundle-update listeners or define renewing connections after trust/key changes.
Lines 76–78 disable redirects, automatic retries and cookie reuse. Close immediately
closes clients (`RestTargetClients.java:107`). `GenericRestSkillHandler.java:50`
requires JWT authentication for passthrough and forwards the captured token.

`ExecutionCoordinator.java:113` retains the JWT authentication with queued work.
At line 163 it restores that authentication for public Framework handoff, then
restores the prior thread context. The inspected handoff path does not re-decode
the token or consult an issuer-withdrawal registry. Already admitted work belongs
to Framework; Sidecar records the captured generation and terminal result.
Current implementation therefore supplies no evidence of emergency distrust of
queued/admitted trees or of cancelling effects.

### Bootstrap and publication assets

`examples/production/compose.yaml:1` runs Caddy 2.10.2 and Sidecar, persists Caddy
and Sidecar volumes, and mounts an operator-supplied JWT public key. The application
private signing key is not mounted into Sidecar. `Caddyfile.internal:1` uses
`tls internal` for server HTTPS and reverse proxies to Sidecar; it contains no
client-authentication/member policy. This deployment is not an SDK bootstrap/mTLS
recipe. No `sdks/java/` implementation or signing store/exporter/endpoint was found
in the repository file inventory.

## Key Components

- `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:36` — decoder construction.
- `src/main/java/ai/loomspan/sidecar/security/SidecarJwtProperties.java:8` — process trust settings.
- `src/main/java/ai/loomspan/sidecar/management/ManagementBearerFilter.java:68` — current closed token operation map.
- `src/main/java/ai/loomspan/sidecar/management/ManagementPersonalTokenService.java:43` — issuance/lifecycle.
- `src/main/java/ai/loomspan/sidecar/management/ManagementEditingService.java:247` — publication authorization.
- `src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java:297` — serialized durable/runtime publication.
- `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java:58` — captured TLS contexts and clients.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:163` — queue-to-Framework handoff.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Prior identity contracts | `2026-10-15-sdk-v1-identity-callback-reference.md` selects RS256, explicit profiles, 300s maximum lifetime, 60s skew, exact `(iss,sub)`, finite issuer policy and independent callback validators. Trust mechanisms are explicitly PR-8.0.2 work. |
| Standing management plans | Design lens sections “One management API”, “Editing ownership” and “Users choose their clients” preserve cumulative grants, live checks and destination delegation; roadmap adds interactive-only trust changes. No separate current management implementation plan is present in `ai/thoughts/plans/`; the only plans there are PR-8.0.1. |
| Source/export contract | Roadmap lines 745–906 and security research lines 497–624 require both sources, endpoint, export, bootstrap alternatives and independent distrust. They explicitly leave concrete mechanisms to Phase 0. |
| Phase assignment | Phase 0 assigns initial trust/token/export/bootstrap enforcement to Phase 1, group extension to Phase 2 and lifecycle mechanisms/stress evidence to Phase 3; Phase 3 explicitly owns implementation, not only tests. |
| Deployment baseline | Current production Caddy configuration supplies server TLS, not both-path mTLS/member lifecycle or automatic app signing provisioning. |

## Existing Tests and Fixtures

Located, not executed during research:

- `ManagementPersonalTokenHttpIntegrationTest.java:67` covers Read shared API,
  lifecycle/one-time secret (83), mixed credentials/CSRF (135), live role restriction
  (144), expiry (159), exact publication candidate (176), account-route/execution
  denial (206), disable/re-enable (227), recovery (237), expired lease (249) and
  audit secret exclusion (263). These are existing multi-account tests.
- `JwtStartupIntegrationTest.java:42` uses a local server for configured JWKS and
  discovery; line 108 covers invalid bound policy and line 118 unusable public keys.
  It does not establish administrative retrieval policy, persistent cache or withdrawal.
- `RestTransportTlsIntegrationTest.java:59` supplies real test certificates and
  verifies per-target SSL bundles/client identities. It does not establish automatic
  renewal/reload or pooled connection retirement.
- Editing, file-mode, import/rollback/restart, publication recovery and shutdown tests
  exist in `src/test/java/ai/loomspan/sidecar/{management,configuration,execution}/`.
- `SupportedLoomspanApiArchitectureTest.java:19` checks production and test classes
  against internal/autoconfigure package bans; line 30 rejects Sidecar Java skills.
- Test JWT private/public PEMs are fixtures, not deployment credentials.

## Dependencies and Operational Constraints

Framework checkout HEAD is `52f11dd90c12863b2ec440583cfe2396eda946f6` and differs
from the matching tag. All Framework lookups here use `git show`/`git grep` at
`v1.0.0-beta.8`, tag object `052e8b47edaf95c0d2013d9e6351284a98207d6b`.
No Framework rebuild/artifact revision verification was performed or required.

At that tag:

- `src/main/java/ai/loomspan/api/SkillInvocationHandoff.java:5` states current
  authentication is captured at handoff and authorization is enforced during execution.
- `src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java:5` defines single-use
  admission, explicit pending release and framework cutoff; it exposes no kill/revoke API.
- `src/main/java/ai/loomspan/api/RestSkillHandler.java:5` is the sole supported SPI.
- `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`
  lists the closed 23-type public surface and rejects bean replacement/internal types.
- `agent-skills/loomspan-docs/references/java-api/skill-reload.md` describes captured
  generation/resource lifetime and excludes process Spring/server settings from reload.

No public SDK signing/trust/member-lifecycle service exists in this allowlist. The
research does not establish a required new Framework SPI. A later design requiring
in-flight cancellation or new execution-time policy hooks cannot infer those
capabilities from current admission/reload contracts.

Sidecar/SDK focused evidence belongs here; complex real Compose/provider/proxy
lifecycle evidence belongs in `C:/opendev/code/loomspan-sidecar-test-suite`.
Framework tests are not Sidecar integration evidence. Client/repository versions,
configuration, command and result remain required attribution for future evidence.

## Historical Context

PR-8.0.1 is committed at current HEAD and supplies the preceding contract and 57
planned scenarios. Its documented 300s/60s token window and callback/live-account
policies constrain rollover, queue expiry and independent-validator treatment.
`be693c9` updated roadmap/phases/tickets; `de7cc77` removed old artifacts. Current
checked-out code and normative design outputs take precedence over removed plans.
The roadmap and security research explicitly label automatic provisioning, SDK
publication and lifecycle recipes as unverified design work.

## Open Questions

These are contract decisions for planning, not research blockers: accepted public
upload/state schemas; finite retrieval/cache/outage timings; protected trust publication
and tombstone ownership; single-administrator replacement scope; signing-store and
replica/alternative-signer policy; maintained automatic signing/TLS provisioning
mechanism; endpoint/source precedence; export atomicity/path safety; independent
validator withdrawal coordination; and precise queued/in-flight/connection enforcement.
PR-8.0.3 owns final runtime/deployment versions/commands and consolidated evidence
matrix, so this ticket's concrete lifecycle mechanisms must remain a coherent input
to that work. No developer decision was needed to complete current-state research.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md
SUMMARY: Traced current trust, management, publication, transport and execution boundaries against the preceding contract and matching Framework tag. Recorded existing tests separately from planned SDK/lifecycle support; no runtime evidence was claimed.
DECISIONS:
  - Used Framework v1.0.0-beta.8 because local HEAD has advanced; scope remains documentation and planned scenarios only.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run implementation and test planning using the current-state gaps and normative roadmap obligations.
