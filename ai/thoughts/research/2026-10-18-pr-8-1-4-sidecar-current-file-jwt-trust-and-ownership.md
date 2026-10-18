---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: ab96258c17fb49e60ea52e51ceb4048e89569983
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md
tags: [jwt, trust, authorization, ownership, lifecycle]
---

# Current-file JWT trust and ownership Research

## Research Question

Locate current execution authentication, role evaluation, ownership, dispatch,
callback, health, configuration persistence and samples; identify verified public
Framework support and the evidence needed by Full pipeline planning. This is Step
1 in pipeline mode, selected profile `full`, approved by the developer. The
ticket's destructive replacement policy and [design lens](../design-lens.md)
apply; this artifact documents existing behavior, not an implementation design.

Research checklist completed: authentication and claims; public exact-role
configuration; listeners and health; queue/callback lifetime; ownership;
persistence/reset implications; tests, samples and build boundaries.

## Summary

Current Sidecar uses a startup-created Nimbus decoder with either PEM, remote
JWKS or issuer discovery. It requires a configured issuer/audience, adds a
configurable authority prefix, and has no current-file trust service or trust
health indicator. Both Framework and Spring already support an empty
`GrantedAuthorityDefaults` prefix through documented standard Spring configuration;
there is no missing public contract blocking exact-role matching.

Ownership already uses signed issuer plus subject. Dispatch captures the original
JWT authentication but does not revalidate it before Framework handoff, and
passthrough callbacks send it without a strict expiry check. Redirects and
automatic HTTP retries are already disabled. JWT process configuration is outside
managed publications and the persistence schema; no reset of valid development
database publications is implied by removing those process settings.

## Repository State

- Research recorded at 2026-10-18 12:16 America/Los_Angeles.
- Branch `main`; HEAD `ab96258c17fb49e60ea52e51ceb4048e89569983`.
- Initial `git status --short` was empty. This research artifact is the only
  Step-1 edit; no production changes or tests were performed.
- `pom.xml:15` targets Java 21 and `pom.xml:19` pins Framework
  `1.0.0-beta.8`; Sidecar is `1.0.0-beta.4-SNAPSHOT`.
- Local Framework checkout is on `main`, described as
  `v1.0.0-beta.8-12-g52f11dd`. All Framework evidence below was read with
  `git show`/`git grep` against **`v1.0.0-beta.8`**, not its advanced branch.

## Current Behavior and Data Flow

### Execution authentication and exact role support

`security/SidecarJwtProperties.java:8` binds
`loomspan-sidecar.auth.jwt`. Issuer and audience have no defaults. Roles claim
defaults to `roles`, authority prefix to `ROLE_`, and skew to 60 seconds.
`JwtSecurityConfiguration.java:36` validates these properties before creating
one decoder: explicit PEM first, then explicit remote JWKS, otherwise issuer
discovery. PEM failure prevents startup. Remote modes can perform network I/O;
research did not contact them.

The decoder installs timestamp, configured issuer, configured audience and
required-claim validators (`JwtSecurityConfiguration.java:50`). Required claims
currently cover nonblank issuer/subject and present expiry, not the execution
purpose, execution-specific JOSE type or strict roles shape. The authority
converter (`:73`) uses the configured claim and prefix.
`grantedAuthorityDefaults` (`:83`) gives that same prefix to Spring/Framework.
There is no trust watcher, whole-file key validator, atomic trust snapshot,
unknown-kid reload limiter or automatic unusable-file recovery in current Sidecar.

The matching public Framework documentation explicitly states that
`GrantedAuthorityDefaults` configures a custom **or empty** prefix for all skill
kinds, including YAML REST skills:
`C:/opendev/code/loomspan-framework`, tag `v1.0.0-beta.8`,
`agent-skills/loomspan-docs/references/skill-authoring/authorization.md:52`.
It also explicitly explains that YAML `ROLE_FINANCE` under the default prefix
requires `ROLE_ROLE_FINANCE`, because prefixes are not stripped. The tag's
`src/main/java/ai/loomspan/internal/security/SkillRoleEvaluator.java:17` confirms
the documented standard Spring bean is consumed and prepended at evaluation.
This internal source is research evidence only; application/test imports remain
forbidden. No undocumented `loomspan.*` option or new SPI is needed to express
the requested exact-role contract.

### Ownership, admission, dispatch and callback

`api/ExecutionController.java:45` validates a submit through public
`SkillTemplate.validate`, then passes `ExecutionOwner.from(authentication)` and
the original `JwtAuthenticationToken` to the coordinator. The owner record
(`security/ExecutionOwner.java:7`) requires both nonblank issuer and subject.
`execution/ExecutionCoordinator.java:112` stores that owner in an in-memory record
and captures authentication in the task. `find` (`:138`) compares owners, not
token identity. Polling with another valid JWT for the same pair therefore does
not replace task authentication. A different pair yields not-found, avoiding
disclosure of other owners' records. Retained execution records are not persisted.

The coordinator's gate coordinates retained capacity, workers, queued count and
queued input bytes. `handoff` (`:163`) releases reservations, checks admission is
open, installs captured authentication in a fresh security context, and calls
public `SkillInvocationHandoff.handoff`. It does **not** currently re-decode the
credential or inspect expiry. The previous context is restored in `finally`.
Task invocation and completion preserve the captured generation and immutable
outcome; runtime exceptions become failure records. The classifier
(`ExecutionFailureClassifier.java:11`) maps validation/access-denied specially,
with other exceptions treated as skill failure.

`rest/GenericRestSkillHandler.java:32` resolves the invocation's captured
generation and fixed configured target/route, binds URI inputs, then builds the
request. For `CALLER_PASSTHROUGH` (`:48`) it requires JWT authentication and adds
the unchanged token as an Authorization header. There is no expiry check and
no mint/refresh path. `rest/RestTargetClients.java:76` disables redirect following
and automatic retries, so a redirect cannot transfer that bearer token elsewhere.
Existing route publication and management authorization govern approved
passthrough targets; the ticket does not replace those controls.

Shutdown closes admission and discards waiting tasks
(`ExecutionCoordinator.java:230`); already handed-off work follows Framework's
public lifetime and generation resources. Current trust changes cannot cancel
work because current-file trust does not yet exist.

### Listeners, health and management separation

Packaged `application.yml:65` starts a separate Actuator listener on port 9091;
application port normally defaults to 8080. No bind address is configured for
either listener in packaged configuration. `application.yml:67` enables probes
and exposes only health. No trust contributor is present. The execution security
chain permits health anonymously (`JwtSecurityConfiguration.java:96`), requires
authentication on `/v1/**`, uses stateless sessions and denies other requests.
The repository does not currently configure health details exposure.

Management's order-1 chain matches only `/management/**` and
`/api/management/**` (`management/ManagementSecurityConfiguration.java:50`),
uses accounts, sessions, personal bearer tokens and `MGT_*` authorities, and
preserves CSRF for session users. Its authentication filters are scoped to that
chain. Health currently falls outside it. Thus existing management authentication
does not automatically supply authenticated health detail context merely because
health is public in the second chain. This is a concrete wiring area for planning.

The additional 9091 listener is material to colocated loopback policy: it must be
addressed explicitly, including inherited versus explicit management bind and
disabled/same-port configurations. Current source contains no other custom
embedded listener registration. Framework observability is disabled by default
and uses its own key filter on the application listener when enabled; its reserved
namespace is permitted by Sidecar to reach that filter.

### Configuration persistence and reset implications

`configuration/ConfigurationModeConfiguration.java:19` selects application mode
(`database` default, or `file`). Its database bind exclusions (`:54`) apply only
to Framework connections/models/session/trace settings, not Sidecar JWT settings.
The JWT bean is created in both modes with the same process properties.

`storage/ManagedConfiguration.java:11` stores skill documents, REST YAML,
execution YAML and credential identifiers. The schema
(`src/main/resources/db/migration/V1__configuration_snapshots.sql:1`) stores
authored/effective execution YAML and provider credential ciphertext, but has no
JWT settings or JWT trust table. `RuntimeConfigurationService.java:261` validates
execution YAML with public Framework `ExecutionConfiguration`; restore/prepare
passes that same contract. The beta.8 Framework parser
`src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:25`
allows only top-level `loomspan`, with publishable children connections, models,
session and execution-trace. Valid publications cannot contain
`loomspan-sidecar.auth.jwt.*` or the future trust settings.

File startup history records redacted Framework execution settings and route/skill
inspection (`RuntimeConfigurationService.java:187`), not JWT settings. Drafts may
contain invalid authored YAML, but it cannot make those fields runtime JWT trust.
**No repository-owned reset of valid database publications, accounts, tokens or
draft storage is required by this replacement.** Operator process files/env and
obsolete sample key material require replacement under the new contract; this
finding does not authorize deleting databases or deployed data.

## Key Components

- `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:36`
  — decoder, authority converter and execution/health security chain.
- `src/main/java/ai/loomspan/sidecar/security/SidecarJwtProperties.java:8`
  — superseded key source/prefix/skew configuration.
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:163`
  — last Sidecar boundary before public Framework admission.
- `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:48`
  — original credential passthrough before each outbound request.
- `src/main/java/ai/loomspan/sidecar/management/ManagementSecurityConfiguration.java:37`
  — existing management credential/session handling.
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:20`
  — imports both production and test classes and rejects internal/autoconfigure
  dependencies; also forbids Java `@SkillMethod` declarations.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Operator docs | `docs/operations.md:142` documents all six removed properties; `agent-skills/loomspan-sidecar-authoring/references/integration.md:139` contains the old external configuration example. `docs/setup.md:56` links to that example. |
| Quickstart | `examples/quickstart/compose.yaml:1` uses separate host and Sidecar network namespaces and static PEM mounts. `host/host.py:34` issues `typ=JWT`, no `kid`, no `token_use`, using a checked-in development private key. It listens on `0.0.0.0:8081`. This topology currently cannot meet colocated loopback by renaming environment variables alone. |
| Production Compose | `examples/production/compose.yaml:39` uses old issuer/public-PEM settings and a separate Caddy backend network. It is a protected remote/proxy recipe; its current topology is separate from the canonical Local colocated recipe. Production env and verifier scripts reference the same PEM contract. |
| Tests/fixtures | Many context tests configure the old issuer/public PEM properties; a shared fixture and tests have manually assembled non-profile JWTs. Configuration reference tests assert old defaults and sample topology. |
| SDK | PR 8.1.3 already added `sdks/java/core/.../ExecutionTokenIssuer.java` and `SigningKeys.java` with focused issuance/signing tests. SDK is an independent Maven build and is not a Sidecar Framework API dependency. |

## Existing Tests and Fixtures

- `security/JwtSecurityConfigurationTest` covers the **old** issuer/PEM/remote
  JWKS/discovery, skew and custom claim/prefix contracts. Its remote provider is
  a local JDK HTTP server. `JwtStartupIntegrationTest` proves bound old policy
  at real HTTP/startup boundaries. Neither proves current-file trust rules.
- `execution/CustomRoleExecutionIntegrationTest` currently proves custom
  `groups` + `APP_`, an intentionally superseded behavior.
- `execution/AuthenticatedExecutionApiIntegrationTest` exercises auth, role
  validation, owner polling, queueing, unchanged passthrough, nested invocation,
  generations and shutdown against real Sidecar. Its queued expired token test
  (`:300`) currently expects a receiver HTTP 401 after dispatch, so that assertion
  conflicts with the new pre-handoff failure contract.
- `rest/GenericRestSkillHandlerTest:267` exercises passthrough and unchanged
  token forwarding; `:321` covers redirects. Synthetic JWT builders here do not
  consistently provide expiry. `support/JwtTestTokens.java:64` signs RS256
  tokens with `new JWSHeader(RS256)` and no purpose/type/kid profile fields.
- `security/ConsoleSecurityIntegrationTest`, management HTTP/personal-token/session
  tests and `management/ManagementLockedExecutionIntegrationTest` provide adjacent
  credential separation/regression coverage. They need new execution trust setup
  without changing their management assertions.
- `configuration/ConfigurationModeIntegrationTest` and file/runtime-publication
  fixtures establish both application modes. They currently do not prove both
  modes source execution keys from the same configured JWKS path.
- No current tests prove whole-file rejection, no last-good acceptance, automatic
  recovery/readiness, atomic key publication or rate-limited unknown-kid reload.

## Dependencies and Operational Constraints

The root Maven wrapper build uses Java 21 and Surefire 3.5.4. CI
(`.github/workflows/ci.yml:20`) runs Python version/guidance/client checks,
installs Chromium via Playwright, runs `./mvnw -B -ntp verify`, then builds and
verifies Docker images/production recipes. Focused Java tests can use
`.\\mvnw.cmd -B -ntp -Dtest=<classes> test` in this PowerShell workspace. Browser
checks require Chromium; deployment checks require Docker and corresponding
tools. Root tests use temporary SQLite state and local deterministic callback/model
fixtures; research did not execute tests or contact live providers.

The ticket explicitly permits the external PR 8.1.2 scenario to break until
PR 8.1.5 replaces its old configuration. It does not grant aliases or waive
Sidecar-owned focused evidence. No Framework rebuild, new snapshot repository,
release/publication gate or SDK dependency is required for this ticket.

## Historical Context

HEAD is PR 8.1.3 signing/issuance, preceded by PR 8.1.2 blocking SDK and PR 8.1.1
independent SDK build. The ticket and four canonical phase references were read
in full; each describes the revised behavior as planned. They assign composed
Local, callback-expiry and rotation evidence to later PRs 8.1.5, 8.1.6 and 8.1.8.
Their described outcomes are requirements, not evidence Sidecar already implements
them. Research uses current Sidecar source and matching beta.8 Framework source.

## Open Questions

These are planning choices, not blockers to completing research:

- Final property names and the bounded reload interval/rate-limit policy.
- Concrete additional-listener loopback policy and health management-auth wiring.
- Exact interpretation/testing of malformed registered claim types before library
  coercion, with fixed 60-second skew versus strict queued/callback expiry.
- How shipped Local colocated samples and the existing remote production Caddy
  recipe are expressed coherently under their different trust/deployment needs.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md
SUMMARY: Traced current JWT, ownership, dispatch, callbacks, health, persistence and samples. Matching beta.8 public documentation supports exact roles through empty GrantedAuthorityDefaults. No valid database publication reset is implied by removing process-only JWT properties.
DECISIONS:
  - Used the matching v1.0.0-beta.8 Framework tag because its development branch has advanced.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run implementation and test planning for the selected Full 5-Step Pipeline.
