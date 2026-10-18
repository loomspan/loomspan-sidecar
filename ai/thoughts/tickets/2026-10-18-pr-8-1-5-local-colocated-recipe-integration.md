# PR 8.1.5: Local colocated recipe with reference app, issuance and trust integration

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

The external suite proves that the real pieces work together in the Local HTTP
recipe:
- a form-login Spring Boot reference app;
- SDK colocated signing and issuance;
- Sidecar colocated trust from the shipped sample configuration;
- exact roles and execution ownership;
- trust recovery and queue expiry.

The deployment simplification ticket takes the PR 8.1.2 plain-Java fixture
repair; this ticket retains the actual Spring/colocated recipe obligations.

## Requirements

- **JWT verification keys come from the configured JWKS file in both file and
  database application ConfigurationMode.** This scenario runs one mode.
  PR 8.1.4's focused wiring tests cover both modes; do not duplicate this
  recipe per mode.
- The reference app lives in `C:/opendev/code/loomspan-sidecar-test-suite`.
  - It is a Spring Boot servlet application that consumes the locally built
    `loomspan-sdk-spring-boot-starter`, which makes it the clean Boot consumer.
  - It uses ordinary form login with test users and exact authority names.
  - It uses deterministic model fixtures, no provider calls and temporary state.
  - Later tickets extend it with callbacks (PR 8.1.6), TLS recipes (PR 8.1.7)
    and a second app (PR 8.1.8). Keep it small and readable.
- Local recipe topology:
  - One app instance and one Sidecar.
  - Both bound to loopback and configured with the same pre-created key
    directory, set up the way the deployment recipe describes (writable by the
    app, readable by Sidecar).
  - Sidecar configuration comes from the shipped colocated sample configuration
    produced by PR 8.1.4, plus only the test-specific values the suite must
    supply.
  - The harness sets up the directory; the SDK and Sidecar do not create it.
- Composed scenarios to add:
  - A form-authenticated user's request in the reference app issues a token
    through the SDK and reaches a skill.
  - Exact roles: a user with the exact skill authority succeeds, and a user
    without it is denied.
  - A different subject cannot read another user's execution. The owner's fresh
    token can poll it.
  - Colocated restart: restarting the app produces a new key. Tokens from before
    the restart stop working once Sidecar adopts the new `jwks.json`. New tokens
    work.
  - Trust-file correction: making `jwks.json` unusable stops acceptance and
    readiness, and correcting it recovers.
  - Queue expiry: queued work whose token expires before handoff fails with
    `execution credential expired before start` and is never dispatched.
- Reuse the PR 8.1.2 plain-Java scenarios repaired by the deployment simplification
  ticket (completed, failed, lost response), using public JWKS/current execution
  JWT claims. Do not duplicate that fixture repair. It is not SDK Spring issuance
  or colocated reference-app evidence.
- Record evidence in the SDK lane format. Never log tokens, keys or payloads.
- Make Sidecar-side documentation or sample corrections in `loomspan-sidecar`
  only when this integration exposes a real defect in the shipped Local
  guidance.

## Acceptance criteria

- [ ] The suite runner's `local` recipe runs the reference app and Sidecar from
      the shipped colocated sample configuration with no provider calls.
- [ ] A form-logged-in user reaches a skill through SDK issuance.
- [ ] The exact-role positive and negative cases both pass.
- [ ] A different user is denied another user's execution, and the owner's fresh
      token can poll it.
- [ ] Tokens issued before a colocated app restart are rejected after Sidecar
      reloads, and new tokens are accepted.
- [ ] An unusable `jwks.json` stops acceptance and readiness, and correction
      recovers both.
- [ ] Queue-expired work fails with `execution credential expired before start`
      and is never dispatched.
- [x] The PR 8.1.2 completed, failed and lost-response scenarios pass on the new
      trust contract, and the old-configuration fixture is gone. Delivered by the
      deployment simplification ticket; its current report is linked from the
      maintainer inventory. The remaining Spring/colocated criteria stay pending.
- [ ] The evidence records the recipe, revisions and builds, and results, with
      no secrets.

## Verification ownership reconciliation

The [deployment simplification ticket](2026-10-18-simplify-deployment-and-composed-test-ownership.md)
uses the existing SDK lane for current plain-Java invocation and deployment
transport/real-TERM baselines. See [current ownership](../../../docs/verification.md).
Those baselines do not complete this ticket's future reference-app, callback,
recipe or trust obligations. No TLS certificate-lifecycle testing is transferred.

## Context

- Depends on:
  - PR 8.1.2 (client and scenarios)
  - PR 8.1.3 (signing and issuance)
  - PR 8.1.4 (Sidecar trust, ownership and expiry)
- Canonical sources:
  - [Deployment recipes](../phases/2026-10-15-sdk-v1-deployment-recipes.md), "Local"
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md): composed evidence for the "SDK signing" (colocated start/restart), "SDK issuance", "Sidecar trust" (file correction), "Sidecar ownership/roles" and "Queue/callback token lifetime" (queue expiry) rows, plus the Boot consumer
- PR 8.1.6 completes the Local recipe's callback leg.
- Out of scope: multi-writer coordination tests, login-provider deployments and
  a per-ConfigurationMode duplicate of this recipe.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** This is the first composed test of the new security contracts
  across the SDK, Sidecar and a new reference app. Fixture topology and the
  timing of restart and reload checks still need design.
- **Reassessment triggers:** none
