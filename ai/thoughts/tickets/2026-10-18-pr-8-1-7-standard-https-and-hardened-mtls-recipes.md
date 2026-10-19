# PR 8.1.7: Standard direct HTTPS and Hardened Caddy mTLS recipes

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

The external suite proves two remote recipes with provided signing and file
trust:
- **Standard:** direct HTTPS in both directions.
- **Hardened:** Caddy-managed mTLS in both directions, with no bypass of the
  proxies.

JWT authentication and user authorization must still apply over accepted TLS.
Operators get usable reference configuration. These recipes bound testing; they
do not restrict customer configuration.

## Requirements

### Shared

- **JWT verification keys come from the configured JWKS file in both file and
  database application ConfigurationMode.** These recipes use provided signing
  and file trust: the app's exported JWKS is delivered to Sidecar's file trust
  path. Do not duplicate the recipes per ConfigurationMode.
- Generate temporary certificates on every run: valid, wrong-CA, wrong-peer and
  expired material, as the negatives require.
  - Never commit certificates.
  - Do not run a certificate authority service.
- TLS uses standard Spring Boot SSL configuration and Caddy TLS configuration
  with operator-supplied material.
  - No Loomspan certificate watcher, reload controller, renewal or rotation
    procedure, or timed connection retirement.
  - No renewal, rollover, revocation-propagation or live-reload tests.
- Each scenario covers invocation (SDK to Sidecar) and callback (Sidecar to app)
  with the reference app and form login.
- Show at least one JWT rejection over an accepted TLS or mTLS connection.
- Reference proxies and clients never replay submissions or callbacks on
  connection failure.
- The provided-key deployment here is the "one provided-key deployment"
  composed evidence for SDK signing.
- Deterministic fixtures, no provider calls, and evidence recorded in the SDK
  lane format with no secrets.
- Add no SDK transport-mode enum, runtime recipe allowlist or network-proof
  controller. Mixed or other compositions stay permitted and untested; do not
  label them unsupported.
- If a recipe needs a Sidecar or SDK change to accept standard TLS
  configuration, make it in `loomspan-sidecar` with focused tests. For example,
  Sidecar's outbound callback client may need standard Spring Boot SSL bundle
  configuration. Keep the change within standard Spring Boot facilities and
  configuration namespaces.

### Standard recipe

- The SDK calls Sidecar's Spring HTTPS listener. Sidecar calls the reference
  app's Spring HTTPS callback listener.
- Both callers verify certificate chains and hostnames.
- No client certificates and no Caddy.
- Negatives: an invalid server certificate (wrong CA, wrong hostname/peer or
  expired) is rejected in each direction.

### Hardened recipe

- Topology:

  ```text
  App + SDK -> local HTTP -> App Caddy -> mTLS -> Sidecar Caddy -> local HTTP -> Sidecar
  Sidecar -> local HTTP -> Sidecar Caddy -> mTLS -> App Caddy -> local HTTP -> app callback
  ```

- The app shares a protected network namespace with its own Caddy, and Sidecar
  does the same with its Caddy.
- Backend and outbound-proxy HTTP listeners bind to loopback. Only the intended
  Caddy TLS listeners are reachable externally.
- Outbound proxy destinations are fixed; never an open forward proxy.
- Browser login ingress uses a listener separate from the mTLS callback
  listener.
- Caddy certificate trust is deliberately scoped to the group.
- Negatives:
  - peer rejection in both directions (wrong-CA, wrong-peer or expired client
    and server material);
  - no bypass: the backend HTTP listeners and the outbound proxy listeners
    cannot be reached from outside their namespace.
- Loomspan does not inspect, control or prove the network at runtime.

### Documentation (loomspan-sidecar)

- Add operator reference documentation for both recipes, linking to Spring
  Boot SSL and Caddy TLS documentation.
- State that DevOps owns certificate issuance, storage, delivery, renewal,
  replacement, CA rotation and revocation.
- State that plaintext HTTP never crosses hosts.
- State that invocation and callback transports are configured independently.

## Acceptance criteria

- [x] The runner's `standard` and `hardened` recipes run end to end with
      per-run temporary certificates. No certificates are committed.
- [x] Standard:
      - invocation and callback succeed over verified HTTPS;
      - an invalid server certificate is rejected in each direction;
      - a JWT rejection over accepted TLS is shown.
- [x] Hardened:
      - invocation and callback succeed through Caddy mTLS;
      - invalid peers are rejected in both directions;
      - backend and outbound-proxy listeners cannot be reached from outside;
      - outbound destinations are fixed;
      - browser login uses a separate ingress;
      - a JWT rejection over accepted mTLS is shown.
- [x] Both recipes use provided signing with Sidecar file trust loaded from the
      exported JWKS.
- [x] No proxy or client replays a submission or callback after a connection
      failure.
- [x] Any required Sidecar or SDK TLS wiring uses standard Spring Boot
      configuration and has focused tests. No insecure switch or transport
      enum is added.
- [x] The operator documentation for both recipes states DevOps TLS ownership
      and the boundaries listed above.

## Verification ownership reconciliation

The [deployment simplification ticket](2026-10-18-simplify-deployment-and-composed-test-ownership.md)
uses the existing SDK lane for current plain-Java invocation and deployment
transport/real-TERM baselines. See [current ownership](../../../docs/verification.md).
Those baselines do not complete this ticket's future reference-app, callback,
recipe or trust obligations. No TLS certificate-lifecycle testing is transferred.

## Context

- Depends on PR 8.1.6, which provides the complete Local recipe, the reference
  app and callbacks.
- Canonical sources:
  - [Deployment recipes](../phases/2026-10-15-sdk-v1-deployment-recipes.md), "Standard", "Hardened" and "Operator instructions and test fixtures"
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md), "Three real network recipes"
  - [Security architecture](../phases/2026-10-11-established-security-architectures.md), "Network and lifecycle"
- PR 8.1.8 adds the trusted group, rotation and outbound-only coverage on these
  recipes.
- Out of scope: native mTLS, custom meshes, mixed-transport composed tests, a
  TLS lifecycle suite and an exhaustive transport cross-product.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** This verifies transport security boundaries across two new
  network topologies. It may need changes to Sidecar's outbound TLS
  configuration, and how network isolation is achieved in the fixtures still
  needs design.
- **Reassessment triggers:** none

## Pipeline notes

- Most of this work is in `loomspan-sidecar-test-suite`. Operator
  documentation, and any required standard TLS wiring, belongs in
  `loomspan-sidecar`. This is intentional.

## Execution notes

Full pipeline implementation is complete pending independent Step 5 review.
No production TLS wiring change or development-data reset was required. The
[implementation plan](../plans/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md)
records fixture decisions, failed attempts and final verification. The complete
external SDK reports are `20261019T015656Z-c2445d463665` (Standard, six scenarios)
and `20261019T014927Z-b5ab2b9224e2` (Hardened, eight), under the suite's
`evidence/sdk/`; all required builds, scenarios and cleanup pass. All fourteen
final project-label container/network/volume listings are empty. The initial
failed Standard report `20261019T014109Z-545d2153ac89` is retained and explained
in the suite SDK guide. No provider, business runtime/data, maintained reference
pack, commit or release work was performed.
