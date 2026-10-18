# Phase 0: reconciled reference contracts

Reconciled 2026-10-18. This planning rewrite is Phase 0; there is no Phase 0 ticket.
**Replace obsolete contracts directly; no shims or legacy paths.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
No development-data reset is required by this documentation change.

The [roadmap](2026-10-11-java-spring-sdk-roadmap.md) indexes the canonical contracts.
They supersede the former plan, including its testing obligations.

The reconciliation includes the subsequent 2026-10-18 decisions:
- Blocking client only; no custom futures or thread-context wrappers.
- Send each request once. Bounded waiting polls successful status responses and
  stops on read failure. Lost/unusable submission response means acceptance unknown.
- Simple request-attempt/effect counters; no business-operation ledger.
- Document callback prefix/order and test the reference app; do not analyze arbitrary
  application matchers or promise protection for routes outside the SDK chain.

Maintain one [acceptance matrix](2026-10-15-sdk-v1-acceptance-matrix.md), rather than
duplicate identity and trust scenario catalogs. Fold trusted-group and surviving
JWT lifecycle coverage into implementation. Remove the former separate trusted-group,
lifecycle and release phases.

Internal links and the search for obsolete feature names were checked on
2026-10-18. Relevant future tickets must carry the JWKS versus
file/database application-configuration clarification from the
[trust reference](2026-10-15-sdk-v1-signing-trust-reference.md#configuration-and-visibility).
No tickets, implementation, commits or release work are part of this reconciliation.