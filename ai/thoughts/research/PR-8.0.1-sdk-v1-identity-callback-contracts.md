---
date: 2026-10-15
repository: loomspan-sidecar
branch: main
commit: be693c9f65dcbda6e15cf93fbffe5f832bc0fb40
ticket: ai/thoughts/tickets/PR-8.0.1-sdk-v1-identity-callback-contracts.md
tags: [sdk-v1, identity, jwt, callbacks, security]
---

# SDK v1 identity and callback contracts research

## Research Question

What currently exists for identity, JWT admission, execution ownership, caller
propagation and REST callbacks, and which evidence constrains the documentation
contracts requested by PR-8.0.1?

Research checklist completed: ticket and normative phase/roadmap/security inputs;
repository state; admission and authority conversion; ownership and queue handoff;
outbound callbacks and transport; executable coverage; matching Framework public
contracts; primary security/provider/Spring sources; implementation-owner boundaries.
This artifact describes evidence, not the selected reference design.

## Summary

Sidecar implements single-issuer JWT admission, configurable role-claim/prefix
conversion, issuer/subject ownership, captured authentication through queue handoff
and unchanged bearer callback passthrough. It does not implement an SDK: `sdks/java/`
contains only `.gitkeep`. The four SDK strategies, native-account resolver and two
callback composition arrangements are roadmap obligations, not existing support.

Explicit access-token purpose rules, explicit sender-constraint rejection and an
issuer-specific trust/authority model are demonstrated contract gaps. Existing
Framework beta.8 public handoff and REST contracts already expose trusted caller
authentication on execution threads; research found no missing public Framework
contract needed merely to document the requested design.

## Repository State

- Inspected at 2026-10-15T21:42:30-07:00; branch `main`, commit above.
- Initial dirty state: exactly three ticket headings. The first lines of PR-8.0.1,
  PR-8.0.2 and PR-8.0.3 were prefixed with `PR #8.0.1 —`, `PR #8.0.2 —` and
  `PR #8.0.3 —`, respectively. These are pre-existing developer edits to preserve.
- Root `pom.xml:8` declares Sidecar `1.0.0-beta.4-SNAPSHOT`; `pom.xml:12`
  establishes Java 21, Boot 4.1.0 and Framework 1.0.0-beta.8. No production changes,
  commit, push, release, data reset or external service execution occurred here.
- Framework lookups used `git show v1.0.0-beta.8:...` in
  `C:/opendev/code/loomspan-framework`; tag commit
  `052e8b47edaf95c0d2013d9e6351284a98207d6b`. This avoids assuming its current
  development checkout matches Sidecar's dependency.

## Current Behavior and Data Flow

### Admission and identity

`security/JwtSecurityConfiguration.java:37` builds one Nimbus decoder from a public
RSA key, explicit JWKS URL or issuer discovery. It applies configured timestamp
skew, exact issuer and audience validators and requires nonblank issuer/subject and
an expiration (`:53`–`:75`). There is no application validator for a token-purpose
claim, `cnf`, future `iat`, maximum lifetime or issuer-specific authority allowlist.
Decoder-library defaults are separate from explicit application policy; this
research does not claim every otherwise valid JWT is accepted by every key mode.

`security/SidecarJwtProperties.java:8` binds `loomspan-sidecar.auth.jwt` with one
issuer, audience and verification-source selection, 60-second default skew, `roles`
claim and `ROLE_` prefix. Validation forbids simultaneous static public key and
JWKS URL, but has no collection of issuer trust profiles (`:34`).

`JwtSecurityConfiguration.java:80` converts the configured claim using Spring's
`JwtGrantedAuthoritiesConverter`; it does not resolve native accounts or select
different role maps for different issuers. Its order-2 servlet security chain
requires authentication on `/v1/**`, uses stateless sessions, denies unknown routes
and returns 401/403 problem responses (`:96`). It is the Sidecar API chain, not
the proposed SDK chain inside a customer's browser application.

`api/ExecutionController.java:41` resolves a skill, reads bounded JSON, calls public
Framework validation and admits work with both `ExecutionOwner.from(authentication)`
and the `JwtAuthenticationToken`. `security/ExecutionOwner.java:7` is the exact
issuer/subject record. Polling at controller `:55` derives that pair again;
`execution/ExecutionCoordinator.java:138` returns a record only for equal owners.
There is no username/email equivalence, native account mapping or cross-pair merge.

### Queued work and outbound callbacks

`ExecutionCoordinator.java:112` captures authentication with the queued input.
At `:163`, handoff installs a fresh Spring context carrying the captured token,
calls the public `SkillInvocationHandoff`, then restores the prior context in
`finally` (`:181`). Task references are cleared after handoff/execution/discard
(`:327`–`:364`). The queue does not re-decode or refresh the token when dispatching.

`rest/GenericRestSkillHandler.java:31` chooses the admitted generation's configured
route/target. For `CALLER_PASSTHROUGH`, `:49` requires a `JwtAuthenticationToken`
from the execution thread and attaches its exact token string as Bearer. It does
not mint, exchange, narrow, refresh or authenticate a callback recipient's user.
The recipient remains responsible for validation and business authorization.

`rest/RestTargetClients.java:58` builds per-target HTTP clients and named SSL-bundle
support. `:73` disables redirects, automatic retries and cookie management.
This is concrete containment for redirects/replays/cookie reuse; it does not prove
deployment egress admission, group membership, log hygiene across proxies, issuer
trust administration or automatic certificate rotation.

### Management is a separate boundary

`management/ManagementSecurityConfiguration.java:31` defines an order-1 servlet
chain restricted to `/management/**` and `/api/management/**`, with browser form
login and scoped bearer handling. Its existing CSRF and mixed-credential behavior
is not an SDK callback recipe. The roadmap deliberately gives management mixed
credentials and callback cookies different policies. No callback contract can be
inferred from the Sidecar management chain.

## Key Components

All Sidecar Java paths above are relative to `src/main/java/ai/loomspan/sidecar/`.

Matching Framework evidence, read from the beta.8 tag:

- `src/main/java/ai/loomspan/api/SkillInvocationHandoff.java:5` — captures caller
  authentication, validates/prepares input and transfers admission without execution.
- `src/main/java/ai/loomspan/api/RestSkillHandler.java:3` — supported generic REST SPI.
- `agent-skills/loomspan-docs/references/java-api/invocation.md`, “Atomic Application
  Handoff” — single-use handle, captured generation/authentication, execution-time
  authorization and prior worker-context restoration.
- `agent-skills/loomspan-docs/references/java-api/rest-skills.md`, “REST Handler SPI”
  — exactly one handler, captured caller available on actual handler thread, one
  handler invocation, no Framework token exchange or HTTP routing implementation.
- `agent-skills/loomspan-docs/references/skill-authoring/authorization.md`, “Role
  semantics shared with YAML” and “Local visibility and caller scope” — Spring
  role prefix/hierarchy semantics, captured trusted identity and restored contexts.

These are public-contract/source observations, not Sidecar test results. Importing
Framework internals or replacing beans is neither needed nor authorized by them.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Four strategies and shared issuance | Roadmap `:247`, `:587`, `:892`–`:1013` defines planned choices and illustrative settings; no SDK production implementation exists. |
| Validation profiles | Current single decoder does identity/audience/time/signature validation; roadmap `:400` requires independent admission/callback purpose and sender-constraint checks beyond that baseline. |
| Group trust | Roadmap `:288` and `:354` preserve issuer/subject identity but call for explicit issuer authority. Current single issuer cannot establish independently issuing applications by adding workload membership. |
| Native principals and authority freshness | Roadmap `:354` requires optional post-validation resolution and explicit token/live authority precedence; neither is current SDK behavior. |
| Callback composition | Roadmap `:301` requires both selected arrangements, bearer-only cookie isolation, URL/method/record checks, scoped CSRF and cleanup. Current Sidecar chains do not implement those customer-application contracts. |
| Passthrough | Roadmap `:471` preserves the reusable captured credential; existing handler/client code establishes unchanged forwarding and client redirect/retry/cookie containment, not execution-bound proof or replay prevention. |
| Data responsibility | Design lens “Preserve the available diagnostic contract” and roadmap `:535` forbid adding business-content sanitization while requiring infrastructure credential separation. |
| Trust lifecycle and baselines | PR-8.0.2 owns storage/admin/bootstrap/lifecycle; PR-8.0.3 owns final runtime/deployment/matrix consolidation. This ticket must expose dependencies rather than imply their implementation. |

## Existing Tests and Fixtures

Inspected, not executed in research:

- `security/JwtSecurityConfigurationTest.java:23` — signed fixture success,
  wrong issuer/audience, missing subject/expiration, tampered signature and skew;
  `:79` custom roles/prefix; `:93` discovery/explicit JWKS parity using a local
  HTTP server. No explicit purpose/cnf/profile matrix is present.
- `security/JwtStartupIntegrationTest.java:42` — property-bound remote key modes
  over real HTTP; `:108`/`:118` invalid bound policy and unusable static keys.
- `execution/AuthenticatedExecutionApiIntegrationTest.java:148` — invalid input
  and missing-role rejection before admission; `:252` owned polling, different
  subject 404, same-pair renewal and exact callback token; `:291` queue-expired
  callback fails 401 while renewed-token polling remains possible; `:320` nested
  REST sees the original issuer/subject/roles/token. This is a local callback
  fixture, not evidence of either SDK Spring composition option.
- `execution/CustomRoleExecutionIntegrationTest.java:58` — configured role claim
  reaches real Sidecar/Framework execution and wrong/missing roles fail.
- `rest/GenericRestSkillHandlerTest.java:139` — target cookies do not cross JWT
  callers; `:250` per-target authentication and exact original token; `:302`
  redirect, retry, response-bound and timeout containment.
- `rest/RestTransportTlsIntegrationTest.java:59` — real local TLS/client identity
  for selected target bundles and trust failures. Not a Caddy group or SDK test.
- `architecture/SupportedLoomspanApiArchitectureTest.java:20` — application and
  test imports forbid both `ai.loomspan.internal..` and
  `ai.loomspan.autoconfigure..`; also forbids Sidecar Java `@SkillMethod` methods.

Test paths are relative to `src/test/java/ai/loomspan/sidecar/`. Fixtures use local
test keys and local HTTP/TLS servers. The complex real SDK/Sidecar/application/
provider/Caddy matrix belongs to `C:/opendev/code/loomspan-sidecar-test-suite`
under Phase 0 ownership rules, with attributable local commands/revisions.
Existing tests cannot establish SDK strategies, session-Bob/bearer-Alice isolation,
native resolution, group issuer collisions or unsupported proof-of-possession rejection.

## Dependencies and Operational Constraints

Primary sources checked 2026-10-15 (documentation/source inspection only):

- [RFC 8725 §§3.1, 3.8–3.12](https://www.rfc-editor.org/rfc/rfc8725.html#section-3)
  requires algorithm verification, issuer/audience validation and distinct rules
  to prevent JWT substitution. Signature validity alone does not settle purpose.
- [RFC 9068 §4](https://www.rfc-editor.org/rfc/rfc9068.html#section-4)
  specifies the access-JWT profile's type/claim validation. It is one profile,
  not evidence every provider uses that profile.
- [RFC 8693 §2](https://www.rfc-editor.org/rfc/rfc8693.html#section-2)
  defines request/response token types; successful HTTP or an `access_token` field
  alone does not establish that an exchange result is the required access JWT.
- [Keycloak 26.4.0 exchange guide](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/docs/guides/securing-apps/token-exchange.adoc)
  documents standard V2 same-realm exchange, access-token subject input,
  confidential requester with exchange enabled, client authentication, repeated
  audiences and scope-derived audience filtering. Requested audiences do not add
  unavailable audiences; requester must be a subject-token audience. ID-token
  results are also possible, so output type is part of compatibility validation.
  These are candidate provider facts, not a selected or verified Loomspan profile.
- [Keycloak 26.4.0 TokenVerifier](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/core/src/main/java/org/keycloak/TokenVerifier.java)
  documents the payload `typ=Bearer` check. Its
  [TokenManager](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/services/src/main/java/org/keycloak/protocol/oidc/TokenManager.java)
  sets ID-token payload type separately to `ID`. This supports purpose distinction
  when audiences overlap; generic JOSE `typ=JWT` is not that distinction.
- [RFC 7800](https://www.rfc-editor.org/rfc/rfc7800.html#section-3) defines `cnf`;
  [RFC 9449](https://www.rfc-editor.org/rfc/rfc9449.html#section-7) defines DPoP
  token use. The roadmap's rejection of every `cnf` presence, including malformed
  or unknown forms, is a Loomspan v1 restriction rather than universal JWT policy.
- [Spring chain configuration](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html)
  documents ordered route-specific chains. This is evidence for explicit chain
  selection, not automatic inheritance of another chain's URL authorization.
- [Spring authentication persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)
  documents context repositories, including the null repository; supported session
  isolation still needs a composition contract and real application tests.
- [Spring concurrency support](https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html)
  documents explicit security-context transfer/cleanup wrappers. Ordinary executor
  tasks do not inherit request identity as an automatic SDK guarantee.

Current Spring documentation may cover newer versions. PR-8.0.3 must reconcile
the final supported baseline before compatibility claims; Keycloak evidence above
is deliberately tag-pinned rather than relying on its evolving online guide.

## Historical Context

`git log -3` shows `be693c9 Update roadmap, phases and tickets`, following
`de7cc77 Clean up old artifacts` and `59c044e Start 1.0.0-beta.4 development`.
The plans directory has no existing implementation plans. The current roadmap
and security research dated 2026-10-11, updated 2026-10-15, are planning history,
not completed integration reports. Their token lifetime and setting/API names
remain explicitly illustrative until this design work settles them.

The design lens already agrees on JWT-only execution identity, issuer/subject
ownership, unchanged captured credentials, public Framework boundaries and no
business sanitization. It does not claim current multi-issuer support. Phase 0
explicitly delegates the final runtime/build baseline and complete matrix to
PR-8.0.3 and trust lifecycle machinery to PR-8.0.2.

## Open Questions

Planning must settle the named local provider/version and complete registration,
accepted token profiles and failures; issuer-specific admission and authority model;
exact sending/receiving/resolver interfaces and Java/property equivalence; freshness,
logout and disabled-account semantics; bounded lifetime/queue/callback behavior;
callback matcher/order/authorization diagnostics, supported servlet/reactive and
async scope; and named acceptance scenarios with Phase 1/2/3 owners. These are the
ticket's design deliverables, not blockers to completing source research.

No developer decision is required to report the current behavior above. A material
choice that cannot be resolved from the ticket or evidence must remain explicit
in the governing design and be escalated before affected contract sign-off.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/PR-8.0.1-sdk-v1-identity-callback-contracts.md
SUMMARY: Recorded source-backed Sidecar identity/passthrough behavior, matching Framework public contracts, existing coverage and primary security/provider constraints. Distinguished unimplemented SDK and group/callback obligations from inspected tests.
DECISIONS:
  - Used Framework v1.0.0-beta.8 and Keycloak 26.4.0 tagged sources to avoid advanced-development/current-documentation drift; no provider or product design was selected by research.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run implementation planning and testing planning for the concrete reference contract.
