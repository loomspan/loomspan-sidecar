# PR #8.1.2 — Deliver SDK credentials and durable signing identity

## Development constraints

**Destructively replace superseded development contracts, code and schemas; no
compatibility shims, legacy adapters, parallel legacy APIs or migration machinery
solely to preserve obsolete behavior.** Apply the [design
lens](../design-lens.md#simplicity-and-technical-debt).
Choose the simplest complete solution and minimize technical debt; keep tests,
documentation and process proportional. Document required development-data resets
and impact; this policy does not authorize deleting deployed data.

## Outcome

Applications can use all four credential strategies with one durable signing identity, safe
public-key distribution and preserved customer login. Deliver signing, issuance and credential
capture together as working behavior.

## Requirements

- Implement the managed signer and explicit replacing-source contracts shared by provisioning,
  Spring-issued and Custom issuance within this ticket. Protect persistent state, explicit
  initialization and replica coordination. Missing/corrupt established state fails closed; never
  silently regenerate identity, switch stores or disclose private keys.
- Implement publication-before-use and initial revision/barrier semantics from the trust reference.
  Do not create separate private-key owners for export and issuance.
- Provide the explicitly enabled optional Spring JWKS endpoint on the application's existing server,
  independent of callbacks. Serve the same public snapshot without signing-state mutation. Preserve
  disabled/no-server/collision behavior and existing routes; never start a callback server just to
  serve keys.
- Provide configuration-driven and Java API/bean public JWKS export, including no-listener
  operation, existing-file skip/replacement, atomic writes and actionable errors. No CLI, Maven
  plugin or customer startup hook is required.
- Document initialization/recovery and upload versus URL distribution responsibilities. Phase 3 owns
  lifecycle stress assurance; initial persistent identity, publication safety and replica barriers
  cannot be deferred to it.
- Implement equivalent property/bean and Java initialization and the trusted adapter contracts in
  the identity reference. Spring-issued and Custom share this ticket's signing owner. Never fall
  back between strategies, replace login or substitute service identity.
- Capture the trusted credential once per operation into the immutable snapshot. Validate agreed
  profiles, preserve exact captured bytes and keep them out of public getters/serialization/logs.
  Relay selects compatible access tokens, not ID tokens. Reject DPoP and unsupported/malformed
  `cnf`; never strip bindings or silently reissue.
- Implement Keycloak 26.4.0 standard V2 same-realm exchange with the exact registration, client
  authentication, input/output profiles and errors. Prove against the real local provider; do not
  claim generic OAuth-provider compatibility.
- Implement shared verified issuer/subject principal, explicit issuer-specific multiple roles and
  optional trusted native-account resolver contracts. Invalid tokens never invoke the resolver;
  failed lookup rejects without implicit linking/account creation/fallback. Retain the verified pair
  with a native principal. PR-8.1.3 and PR-8.1.4 independently enforce these semantics at admission
  and callbacks.
- Implement explicit snapshot versus live-account-intersection permissions and supported
  servlet/async capture. Preserve disabled-account/logout/expiry semantics. Renewal cannot update
  captured work; same-pair renewal differs from changed-pair ownership.
- Keep signing/attachment/context plumbing in the SDK. Business inputs cannot choose trusted
  identity or permissions. Provide plain Java and Spring usage examples.

## Acceptance criteria

- [ ] Restart preserves identity; concurrent initialization and missing/corrupt-state cases reject
  safely without substitute keys or private material disclosure.
- [ ] Provisioning, issuance access and public publication share one owner; initial
  ordering/barriers prevent premature activation.
- [ ] Optional endpoint passes enabled/disabled/no-server and route-isolation cases independently of
  callback configuration.
- [ ] Both export interfaces work without a listener and pass skip/replacement/write
  failure/public-only snapshot tests without changing signing state.
- [ ] Operator setup/recovery documentation matches tests and labels later evidence honestly.
- [ ] All four strategies work through both initialization forms; configuration/provider failures
  reject without an alternate strategy or service identity.
- [ ] Real local exchange and Relay tests accept supported profiles and reject wrong-purpose, ID,
  bound and expired credentials without infrastructure credential leakage.
- [ ] Principal/role/resolver tests prove verified pair retention, invalid-token resolver exclusion,
  rejection on failed lookup and no name/email linking.
- [ ] Concurrent and supported async operations retain isolated captured credentials; snapshot/live
  policy, renewal, expiry, logout and disabled-account variants pass.
- [ ] Runnable examples require no customer JWT construction/signing/attachment and document actual
  provider and context-propagation limits.

## Context

Created 2026-10-16 for roadmap GitHub PR #8. Depends on
[PR-8.1.1](PR-8.1.1-standalone-sdk-client.md).

**Matrix ownership:** Signing and Credential units: all assigned Phase 1
strategy/exchange/token/identity/freshness/context and
initialization/persistence/public-export/JWKS-endpoint source variants.

The [Phase 1 scope](../phases/2026-10-15-sdk-v1-phase-1-isolated-integration.md),
[API reference](../phases/2026-10-15-sdk-v1-api-reference.md),
[identity reference](../phases/2026-10-15-sdk-v1-identity-callback-reference.md),
[trust reference](../phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md),
[deployment recipes](../phases/2026-10-15-sdk-v1-deployment-recipes.md) and
[acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md) are normative.
Preserve all linked source assertions and variants, including both callback arrangements;
these summaries do not narrow them. Shared matrix ownership is cumulative: component
proof belongs in its component ticket, real composed/fault proof in PR-8.1.5.
Planned commands and Framework-only tests are not SDK/Sidecar evidence.

Focused SDK/Sidecar tests and documentation belong here; complex Compose/fault evidence
belongs in `C:/opendev/code/loomspan-sidecar-test-suite`, following its instructions and
updated agreement. Preserve existing embedded Java/Python examples and equipment-service
behavior. Record actual SDK/Sidecar/Framework versions or revisions, configuration,
command and scenario variant. Other languages, new Python functionality, management-
authoring SDK, code generation, streaming and server cancellation are outside SDK v1.

Use Java 21/Boot 4.1.0 and independent SDK `1.0.0-beta.1-SNAPSHOT` (planned first release
`1.0.0-beta.1`). Sidecar stays `1.0.0-beta.4-SNAPSHOT`, next release/tag
`1.0.0-beta.4`/`v1.0.0-beta.4`; Framework starter remains
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.8`. Consult local Framework checkout
`C:/opendev/code/loomspan-framework` at `v1.0.0-beta.8`, not stale installed docs or the
suite's distinct beta.9 lane. Use only closed `ai.loomspan.api` and authorized
`RestSkillHandler`; ArchUnit forbids internal/autoconfigure dependencies in application
and tests. No new SPI, bean replacement, internal reflection or Sidecar Java
`@SkillMethod` skills. Missing public capabilities require Framework planning, not a
bypass. Use documented `loomspan.*`, Sidecar-owned `loomspan-sidecar.*` and standard
Boot namespaces.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Credential protocols, durable signing state and identity/permission boundaries.
- **Reassessment triggers:** Current evidence narrows the work to a bounded correction;
  settled Phase 0 design alone does not remove implementation assurance obligations.

## Pipeline notes

- Deliver this unit's implementation, tests and documentation; do not repeat Phase 0.
  Ticket creation does not start execution. Each ticket is one pipeline/review unit;
  internal work areas do not require separate tickets or process artifacts.
- Developer commits per ticket and syncs after all phases. No agent commit/push/release,
  hosted CI or external compatibility run is authorized. Required deterministic local
  security/provider testing remains in scope.
- Record intentional breakage, feasible checks, outstanding evidence and a named
  restoring unit. Do not add compatibility scaffolding or count unavailable tests as
  passes. Initial protected behavior cannot be deferred wholesale to Phase 3; all
  breakage closes before final readiness. Runtime limitations cannot justify weaker
  callback/session isolation or trust guarantees.
- Framework changes use the developer-installed aligned snapshot and affected retests,
  without normal Framework source rebuilds or snapshot repositories. Publish Framework
  before final Sidecar dependency pin/build/test/commit; hosted release checks follow
  publication/sync. Never overwrite a release.
