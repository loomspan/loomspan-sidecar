# PR 8.1.8: Trusted group, JWT rotation and recovery, and outbound-only coverage

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

The composed evidence of Phase 1 is complete. It shows:
- a fully trusted group of two apps sharing one Sidecar;
- same-issuer provided-key rotation adopted independently by Sidecar and the
  callback receiver;
- deployments without a callback listener.

Operators get instructions for the trusted group and for rotation.

## Requirements

- **JWT verification keys come from the configured JWKS file in both file and
  database application ConfigurationMode.**
  - Rotation is performed by changing JWKS files, never through the database or
    management APIs.
  - Do not duplicate scenarios per ConfigurationMode.

### Trusted group

- Use the provided-key recipe fixtures from PR 8.1.7 in one recipe. Planning
  chooses which.
- Add a second small app sharing:
  - the signing keystore;
  - one issuer;
  - the callback audience;
  - the user directory and the role vocabulary.
- Demonstrate cross-app use: a token issued by one app is accepted by Sidecar,
  and that execution's callbacks are accepted by the group's callback receiver
  under the shared issuer and audience.
- User and record authorization still apply. Group members are not isolated
  from one another, and this is documented as such.
- No separate deployment matrix for the group.

### Rotation and recovery

- One provided-key, same-issuer rotation scenario:
  1. Add the new public key to Sidecar's trust file and to the callback
     receiver's overlap JWKS. Each receiver adopts it independently.
  2. Deploy the replacement private key and restart the signing app(s). The
     old key is never hot-reloaded.
  3. New tokens use the new `kid` and are accepted by both receivers. Tokens
     with the old key keep working while the overlap lasts.
  4. Remove the old public key. After each receiver reloads, old-key tokens are
     rejected there.
- Adoption is eventual and bounded by the documented reload interval. Changing
  Sidecar's file does not update the callback receiver, and the reverse is also
  true.
- Already admitted Framework work is not cancelled by the trust change.
- No private-key hot-reload or uninterrupted-rotation tests. Colocated restart
  and file correction are already covered by PR 8.1.5.

### Outbound-only

- Add focused no-callback coverage: invocation works with no callback listener
  and no callback routes configured, under each recipe's configuration (Local,
  Standard and Hardened).
- Hardened no-callback deployments omit the callback routes and keep
  invocation.
- This does not rerun every security case per recipe.

### Documentation (loomspan-sidecar)

Add operator instructions for:
- the trusted group, including the statement that apps sharing a Sidecar trust
  each other in every way and that separate Sidecars are needed for isolation;
- the rotation procedure, including the restart requirement for provided keys
  and the independent receiver updates;
- no-callback deployments.

## Acceptance criteria

- [x] Two apps share one issuer and signing key on one Sidecar. A token from one
      app is accepted, and the group's callbacks are accepted under the shared
      issuer and audience.
- [x] User and record authorization still deny unauthorized users in the group
      scenario.
- [x] The rotation scenario works as follows:
      - after the add step, both receivers accept the new `kid`;
      - after the restart, new tokens use the new key;
      - during the overlap, old tokens remain valid;
      - after removal and reload, each receiver rejects old-key tokens
        independently, within the documented interval.
- [x] Admitted work is not cancelled by the rotation.
- [x] Rotation changes only JWKS files. No database or management trust path is
      used.
- [x] Invocation without a callback listener succeeds under the Local, Standard
      and Hardened configurations.
- [x] Operator documentation covers the trusted group, rotation and no-callback
      deployments.
- [x] Evidence records each scenario, its revisions and its results, with no
      secrets.

## Verification ownership reconciliation

The [deployment simplification ticket](2026-10-18-simplify-deployment-and-composed-test-ownership.md)
uses the existing SDK lane for current plain-Java invocation and deployment
transport/real-TERM baselines. See [current ownership](../../../docs/verification.md).
Those baselines do not complete this ticket's future reference-app, callback,
recipe or trust obligations. No TLS certificate-lifecycle testing is transferred.

## Context

- Depends on PR 8.1.7 (Standard and Hardened recipes with provided keys) and on
  the PR 8.1.6 callback overlap file.
- Canonical sources:
  - [Signing and trust](../phases/2026-10-15-sdk-v1-signing-trust-reference.md), "Rotation, queues and expiry"
  - [Deployment recipes](../phases/2026-10-15-sdk-v1-deployment-recipes.md), "Operator instructions and test fixtures"
  - [Security architecture](../phases/2026-10-11-established-security-architectures.md), "Application trust"
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md): composed rotation evidence for the "Sidecar trust" and "Callback trust" rows, the trusted group and outbound-only coverage
- This is the last Phase 1 ticket. Phase 2 verifies the evidence and adds no
  features.
- Out of scope: isolation between untrusted apps sharing a Sidecar, multi-issuer
  trust, and replica coordination.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** This is security-boundary and trust-lifecycle evidence across
  several receivers and recipes. The timing of the rotation scenario and the
  topology of the second app still need design.
- **Reassessment triggers:** none

## Pipeline notes

- Most of this work is in `loomspan-sidecar-test-suite`, and the operator
  documentation is in `loomspan-sidecar`. This is intentional.


## Execution notes

Implemented through the approved Full 5-Step Pipeline. See the matching
[implementation verification](../validation/2026-10-18-pr-8-1-8-trusted-group-rotation-and-outbound-only.md)
for actual fresh reports, failures/corrections, source/artifact identities and
independent cleanup commands. Local/Standard/Hardened pass 13/8/9 scenarios;
operator instructions are in `docs/operations.md` and `docs/remote-recipes.md`.
No data reset, provider call, business-lane change, commit or release occurred.
Independent Step 5 review remains pending before final pipeline completion.
