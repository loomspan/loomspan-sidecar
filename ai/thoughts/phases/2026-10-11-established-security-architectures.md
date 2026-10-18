# SDK v1 security architecture

Reconciled 2026-10-18. This is the selected architecture, not a catalog of future
security products. Apply the [design lens](../design-lens.md#simplicity-and-technical-debt);
replace obsolete contracts directly, without compatibility machinery.

## Application trust

**If apps share a Sidecar, they trust each other in every way.** Shared applications
may assert identities and roles, reuse group tokens and handle each other's data.
They share a signing identity, issuer, callback audience, user directory and role
vocabulary. Compromise of a group member or signing key compromises the group.
Use separate Sidecars for applications needing isolation.

The alternatives are a 1:1 localhost pairing and this fully trusted group, on one
host or multiple hosts. Neither removes user/record authorization or protection
against outsiders. Execution ownership uses the verified issuer and subject.

## Security responsibilities

| Owner | Responsibility |
| --- | --- |
| Application login | Authenticate users using the application's existing mechanism |
| SDK issuer | Issue execution JWTs from authenticated identity and allowlisted exact authorities |
| Sidecar | Validate JWTs, enforce execution ownership, pass captured credentials unchanged |
| Framework | Skill authorization, validation and execution through supported public contracts |
| SDK callback chain | Independently authenticate bearer JWTs and install verified identity/authorities |
| Application callback | Authorize methods and records using annotations and business checks |
| Operators | Manage signing material, JWKS deployment and network/TLS configuration |

Sidecar does not issue or refresh tokens. Transport membership never substitutes
for JWT validation. JWTs are bearer credentials, not proof of Sidecar origin or of
a particular execution; applications must not treat request body claims as identity.
Management credentials and execution JWTs remain separate; existing management
accounts/tokens are unchanged.

The [identity contract](2026-10-15-sdk-v1-identity-callback-reference.md) defines
one execution-token profile, snapshot roles and the dedicated callback chain.
The [signing/trust contract](2026-10-15-sdk-v1-signing-trust-reference.md) defines
ephemeral/provided keys and current-file trust. No historical trust database,
account lookup on callbacks, role translation or per-application trust partition is
needed within the fully trusted group.

## Network and lifecycle

Test Local HTTP, Standard direct HTTPS and Hardened Caddy-managed mTLS through
the [three recipes](2026-10-15-sdk-v1-deployment-recipes.md).
Untested combinations remain allowed subject to each hop's security requirements.
Local plaintext belongs inside a protected local boundary, not across hosts.
Caddy protects both remote directions in the hardened recipe; the SDK does not
inspect or control the deployment network.

DevOps owns TLS issuance, storage, renewal, replacement and revocation using
Spring Boot/Caddy facilities. This work tests transport integration and boundary
enforcement, not certificate lifecycle automation. JWT signing-key rotation and
trust-file failure/recovery remain explicitly tested.

## Accepted tradeoffs

- Colocated defaults make the localhost path simple; restarting the signer
  invalidates old credentials as receivers load the new public key.
- Current-file authority fails closed when trust is unusable, then recovers
  automatically. Restoring an old file restores its keys; operators own that risk.
- Roles are snapshots until token expiry. Shorter lifetimes reduce that window,
  but queued and long-running executions need sufficient lifetime.
- Loss of a submission response can leave acceptance unknown. No automatic replay
  and no exactly-once or rollback promise is introduced.
- A small representative matrix replaces exhaustive option combinations.
  Requirements and test costs must be reconsidered together before adding scope.