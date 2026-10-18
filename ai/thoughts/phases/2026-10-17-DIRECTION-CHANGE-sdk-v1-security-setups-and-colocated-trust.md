# DIRECTION CHANGE — SDK v1 security setups and colocated trust (2026-10-17)

These are the developer's decisions from a 2026-10-17 review of the PR-8.1 work.
This document is temporary. It gets folded into the original planning documents
and then deleted (section 9). Until then it overrides every conflicting roadmap,
phase, reference, scenario, matrix and recipe document.

**Development policy:**
- Destructive replacement; no shims. Apply the
  [design lens](../design-lens.md#simplicity-and-technical-debt).
- Removed features are deleted, not kept as experimental code.

## 1. Rationale

- **Too many combinations.** Independent options produced about 2,300 nominal
  combinations, 133 scenarios and 40 Phase 1 deployments. That is disproportionate.
- **The simple path wasn't simple.** It still needed a signer store, a bootstrap
  guard, trust initialization and an upload.
- **The IdP strategies were Keycloak-shaped:**
  - `KEYCLOAK_ACCESS_V1`
  - the `resource_access.*.roles` role path
  - `typ: "Bearer"` and `azp` claims
  - Exchange accepted only `keycloak-26.4.0-standard-v2`
- **Customers:**
  - On-prem: Active Directory or database login, so no IdP JWTs.
  - Cloud: the provider's IdP (Entra and similar).
- **Keycloak is a test tool only.** Nothing in production code, configuration,
  names or customer docs may be designed for it.
- **Sidecar never signs.** On callback it forwards the caller's JWT
  (`GenericRestSkillHandler`, `CALLER_PASSTHROUGH`). Only the app needs a private
  key.

## 2. Decided v1 scope

**Credential strategies:**
- Spring-issued and custom only. The app authenticates users any way it likes and
  issues a short-lived execution JWT.

**Network setups:**
- Both directions use the same level. Callback "none" is allowed in every setup.
- Native mTLS only; `mtls-proxy` is removed. Mesh-terminated mTLS is documented as
  "Local behind your mesh".
- Mixed levels are unsupported and untested.

| Setup | Invocation | Callback | Sidecar trust | SDK signing |
|---|---|---|---|---|
| Local | HTTP | HTTP / none | `colocated` (default) or `file` | `colocated` (default) or `provided` |
| Standard | HTTPS | HTTPS / none | `file` | `provided` |
| Hardened | mTLS (native) | mTLS / none | `file` | `provided` |

**SDK signing:**
- **`colocated`:**
  - RSA key generated in memory at startup; the private key never touches disk.
  - Atomically writes only the public `<key-directory>/jwks.json` (canonical,
    public-only JWKS).
  - A restart creates a new key. Tokens issued before the restart stop validating,
    including executions still running; this is accepted.
- **`provided`:**
  - The customer's RSA key from PKCS#12, JKS or PEM (optional certificate).
  - Writes `jwks.json` to an export path at startup (optionally with `x5c`). An
    operator copies it to Sidecar.
  - Replicas share the keystore; the SDK does no coordination.
  - No key of their own: one documented `keytool`/`openssl` command (or a small SDK
    CLI command).
- **Both modes:**
  - **RSA/RS256 only.** Reject other key types at startup.
  - **`kid`** is the RFC 7638 thumbprint.
  - **Passwords** come from a secret or environment variable, never a properties
    literal.
  - **Certificates:** an expired or near-expiry certificate logs a warning, never a
    failure.
  - **Key reuse** (for example the TLS key) is the user's choice; don't block it.
  - **Cloud key vaults:** all signing goes through one narrow key-source interface:
    sign RS256 over bytes, plus provide the public key and `kid`. Nothing outside the
    key source needs private key bytes, so a Key Vault/KMS adapter can be added later
    without changing the signing, JWKS or validation paths.
  - **Callbacks** are validated with the app's own public key(s), plus an optional
    extra `jwks.json` for rotation overlap.

**Sidecar trust:**
- **`colocated`:**
  - Reads `<key-directory>/jwks.json`.
  - **Refuses to start unless bound to loopback.** Never relax this to private
    networks.
- **`file`:**
  - Reads an operator-supplied `jwks.json` path.
- **Both modes:**
  - One implementation.
  - Multi-key JWKS. Rotation: add the new key, switch the apps, remove the old key.
  - Reload when the file changes, and on an unknown `kid` (rate-limited).
  - Not-ready (health DOWN) until a valid key exists, logging that it is waiting.
  - Token checks: RS256, issuer, audience, `typ`, `token_use`, expiry with 60 s
    skew, and admission freshness (below). There is no fixed 300 s maximum.
  - No trust document, ledger, tombstones, kid-history, bootstrap marker or
    `trust initialize`.
  - `trust.mode` is independent of the configuration `ConfigurationMode`
    (file/database).

**Token lifetime and long executions:**
- Sidecar forwards the same caller token on every callback during an execution, and
  queue time counts against its lifetime.
- **The lifetime is configurable** on the SDK (`loomspan-sdk.issuance.lifetime`).
  The **default is 15 minutes**, and there is **no maximum**.
- **A lifetime over 1 hour logs a WARN** at SDK startup. Sidecar may also log a
  rate-limited WARN when it admits such tokens (ticket detail).
- **Admission freshness:** Sidecar rejects tokens whose `iat` is more than about 60 s
  old at admission (exact value in the ticket), so tokens can't be stockpiled.
- **Expiry handling in Sidecar:**
  - Don't start a queued execution whose token has expired; fail it with
    "execution credential expired before start".
  - Check `exp` before forwarding a callback; if it has passed, fail the skill call
    with "execution credential expired" and never send an expired token.
- With snapshot roles, a role revoked mid-execution remains effective until the token
  expires. Document this, and advise shorter lifetimes for sensitive roles.
- **Sidecar-issued callback tokens** remain a possible later design. They would
  bring back signing in Sidecar.

**Audience:**
- The SDK sets `aud` at issuance; each receiver checks only that its own name is
  present.
- **Defaults (no configuration needed):**
  - Sidecar's audience is `loomspan-sidecar`, the same default in the SDK and Sidecar.
  - The app's callback audience is the app's own issuer value.
  - The SDK puts both in every token.
- `loomspan-sdk.issuance.additional-audiences` adds other recipients (cross-app use).
- There is no workflow-audience allowlist. Sidecar does not reject tokens that carry
  extra audiences.

**Multiple apps per Sidecar (`file` mode; colocated is always one app):**
- **Trust boundary rule:** apps that share a Sidecar trust each other's role claims
  for Sidecar's own skills. Apps that must be isolated use separate Sidecars.
- **Default: separate keys.** Each app adds its public key to Sidecar's multi-key
  `jwks.json`. Sidecar accepts any listed key and forwards tokens unchanged. Each
  app's callbacks accept only its own tokens, so a cross-app callback is rejected by
  the receiving app (Sidecar reports "callback rejected by target"). Recommend
  distinct role names per app.
- **Shared group** (skills target several apps, and any app's user may reach any of
  them):
  - The apps share one keystore, one issuer value and one callback audience.
    Sidecar has one key.
  - This is the request-layer counterpart of a network-layer mTLS group. The two
    stay independent: mTLS membership never implies JWT trust, and vice versa.
  - Document the conditions:
    - full mutual trust; compromising one app or the shared key compromises all
    - **one shared user directory**, because `(issuer, subject)` must mean the same
      person in every app
    - agreed role names across the group
    - coordinated key rotation
- **Alternative with separate keys:** A adds B to `additional-audiences`, and B adds
  A's public key to its callback trust. Both sides must opt in.
- A per-key role restriction in Sidecar is a possible later addition.

**Trust visibility:**
- There is no management trust API, Console trust page or trust write path.
- Visibility is through the health endpoint plus one log line on each load or reload.
  The health endpoint reports mode, file path, loaded `kid`s, last load time and
  ready/waiting.
- Ticket detail: report only the status anonymously and put the details behind the
  existing management authentication, unless the ticket decides otherwise.
- A read-only Console card is a possible later addition.

**Roles:**
- **One allowlist at issuance:** `loomspan-sdk.issuance.roles: [ORDER_READER, …]`.
  Only listed authorities the user actually has go into the token, with names
  unchanged.
- **Sidecar uses token roles as-is.** There is no Sidecar role mapping.
- **Role prefix:** the ticket must settle how the `ROLE_` prefix lines up across all
  three sides, with one documented rule:
  - the app authority name (for example `ROLE_X` or `X`)
  - the token role
  - Sidecar's `rbac_roles`

  The current baseline sets `GrantedAuthorityDefaults` from
  `loomspan-sidecar.auth.jwt.role-prefix` (default `ROLE_`).
- **Callbacks use token roles as-is**, so the app's existing `hasAuthority` /
  `@PreAuthorize` rules work unchanged.
- **Callback identity is snapshot only** (the token's roles). Live mode and the
  native-account resolver are removed. Revisit together with open question 4.
- Renaming at issuance is a possible later addition and would not break this
  configuration.

**Callback receiving (app side):**
- **One arrangement only: a dedicated chain.** The SDK registers its own stateless,
  bearer-only `SecurityFilterChain` at highest precedence. The app does not modify
  its own security configuration, and there are no manifests.
- **One prefix**, configurable, default `/loomspan/callbacks/**`, GET and POST only.
  Sidecar REST route paths point under it. A mismatch fails safe: the request is
  rejected by the app's normal security.
- **Under the prefix: a valid token or reject.**
  - Missing, multiple or invalid `Authorization: Bearer` → 401 with
    `WWW-Authenticate: Bearer`.
  - The app's authorization rule denies → 403.
  - Query-parameter tokens are rejected.
  - Sessions are never read or created; cookies are ignored.
  - CSRF is off for the prefix.
  - No login redirect and no fallback.
  - Rejections are written directly, not forwarded to `/error`.
- **After a valid token,** the app's authorization rule and its own record-ownership
  checks still apply.
- **Startup fails** if another chain ordered at or above the SDK chain matches the
  prefix.
- **Ticket detail:** the response for malformed URLs that Spring's firewall rejects
  before the chain runs.

**Configuration (indicative names):**

```yaml
loomspan-sdk.signing:
  mode: colocated | provided
  colocated.key-directory: /var/run/loomspan/keys
  provided: { keystore, keystore-password-env, jwks-export }
loomspan-sidecar.trust:
  mode: colocated | file
  colocated.key-directory: /var/run/loomspan/keys
  file.jwks: /etc/sidecar/trust/jwks.json
```

- **Defaults:** `colocated` on both sides, with the same default path. This departs
  from the old "every strategy is explicit" rule; record the tradeoff.
- **Mixing modes:** a setting that belongs to another mode fails startup.

**Colocated recipes:**
- Developer machine: both use `127.0.0.1` and the same folder.
- Kubernetes: one pod, with an `emptyDir` volume in both containers.
- Docker Compose: `network_mode: "service:sidecar"` on the app, plus a named volume.
  Normal Compose networking means `provided` + `file`.

## 3. Removed from v1

- **Relay, Exchange**
  - Generic IdP-neutral Relay is a possible v1.x addition.
  - Exchange has no IdP-neutral standard.
- **URL JWKS trust**, separately hosted JWKS, the app-hosted JWKS endpoint
- `ManagedSigningState`, `BootstrapGuard`, protected signer store
- Sidecar trust history and initialization:
  - `TrustService`, `TrustEstablishment`, `TrustInitializationCommand`
  - the file/database trust stores, `JwksSourceClient`, trust schema
- Callback trust-store classes (`CallbackTrustStore`, `CallbackJwksSource`,
  `CallbackVerificationSources`), if not needed
- `mtls-proxy`, mixed transport levels
- The `application-chain` callback arrangement, callback security manifests,
  decorator post-processing, and multi-pattern callback routes
- Sidecar `roleMappings`, the SDK's callback `RoleMapping`, issuance renaming
  (`authority-map`), live callback identity, and the native-account resolver
- `jwk` / `pem-spki` upload formats (Sidecar reads JWKS only)
- Keycloak in production code, configuration or docs
- **Rejected designs:** unsigned JWTs (`alg: none`) and any built-in default key

## 4. Testing direction

- Unit and component tests for each option's rules.
- Composed deployments only for the named setups (about 4–5), plus colocated
  negative cases: non-loopback refusal, missing file, wrong-mode settings.
- No IdP in composed tests; test apps may use form login. If OIDC is ever tested,
  use at least two IdP token layouts.
- Code style for the harness and code:
  - small functions
  - no one-line validation chains
  - a short rule table for allowed combinations
  - a formatter

## 5. Recovery plan (decided)

Start over cleanly. Nothing is archived or referenced from the discarded work.

1. **Clean up — DONE 2026-10-17** (with developer approval):
   - **Sidecar:**
     - `main` reset to `a45ce54`, discarding the unpushed 8.1.1–8.1.4 commits and the
       8.1.5 work.
     - Untracked 8.1.5 files deleted.
     - All 21 `PR-8.0.*` / `PR-8.1.*` tickets, plans, research and reviews removed
       with `git rm` (staged, not committed).
   - **Test suite:**
     - Tracked edits reverted.
     - All SDK v1 scripts, harness, tests and evidence deleted.
   - **Docker:** all `sdkv1-*` containers, volumes and images removed.
   - **Gitignored 8.1 leftovers in Sidecar** deleted: SDK module build output,
     `target/`, and the 2026-10-16 root logs.
   - The framework repo is clean.
   - **Reset baseline:** at `a45ce54`, Sidecar admission is a plain resource-server
     JWT configuration (`SidecarJwtProperties`, `loomspan-sidecar.auth.jwt.*`):
     - `issuer-uri`, `audience`
     - `jwk-set-uri` or `public-key-location`
     - `roles-claim: roles`, `role-prefix: ROLE_`, `clock-skew`

     The new trust modes replace this. `public-key-location` is close to `file`
     mode; remove `jwk-set-uri`. Management trust endpoints and the Console trust
     page did not exist before 8.1.3.
2. **Rewrite the planning documents** in `ai/thoughts/phases`, in this conversation
   with the developer. This rewrite *is* Phase 0; there is no Phase 0 ticket.
   - the roadmap and security research
   - the phases, restructured as:
     - **Phase 0:** contracts, i.e. this rewrite
     - **Phase 1:** implementation and integration, including the surviving
       lifecycle cases as acceptance cases of the tickets that build each mechanism
     - **Phase 2:** release readiness (the former Phase 4, reduced), keeping the
       independent security review
   - the identity/callback and trust/bootstrap/lifecycle reference and scenario docs
   - the acceptance matrix and the deployment recipes

   The former trusted-group and lifecycle phases are dropped. Fold in this
   document's decisions, then delete this document.

   Also record the direction in the test suite's `docs/project-agreement.md`; it
   was reverted to its pre-8.1 state.
3. ~~Delete all PR-8 tickets, plans, research and reviews~~ — done in step 1.
   **Test-suite work stays inside Phase 1:**
   - one early harness-foundation ticket
   - each feature ticket adds its own composed scenario (a second repo in the same
     ticket, or a small paired ticket)
   - all ticket files live in the Sidecar repo's `ai/thoughts`
4. **Search check before generating tickets.** Planning docs, and later code,
   configuration and customer docs, must have no matches for: `keycloak`, `relay`,
   `exchange`, `ManagedSigningState`, `BootstrapGuard`, `TrustService`,
   `TrustEstablishment`, `jwks-url`, `resource_access`, `azp`, `mtls-proxy`,
   `smallstep`, `pkce`. Rerun the check after implementation.
5. **Regenerate the 8.1 tickets** from the new phases, and run them from scratch in a
   fresh agent session.

**Proposed design-lens additions** (developer to confirm):
- Prefer a few named setups over independent option axes.
- Test tools never shape production contracts, names or configuration.

## 6. Open questions (to discuss one by one)

1. ~~Callback chain~~ — decided (section 2, "Callback receiving").
2. ~~Role mapping~~ — decided (section 2, "Roles").
3. ~~Management trust endpoints~~ — decided (section 2, "Trust visibility").
4. ~~Long executions~~ — decided (section 2, "Token lifetime and long
   executions").
5. ~~Phases~~ — decided (section 5, step 2).
5a. ~~Several apps per Sidecar~~ — decided (section 2, "Multiple apps per
    Sidecar" and "Audience").
6. ~~Test CA~~ — decided. Generate a throwaway CA and certificates at test time,
   per run, in a temporary directory: valid, wrong-CA, wrong-peer and expired
   certificates, plus a second certificate for the reload case. Use no CA service
   (no Smallstep) and no committed certificates. The ticket chooses between Java
   generation (test-scope, cross-platform) and `openssl`/`keytool`.
7. **Versions:** is SDK v1 still part of Sidecar `1.0.0-beta.4`? Does the SDK stay
   `1.0.0-beta.1`, versioned independently?
8. **Design-lens additions** (section 5): adopt?

## 6a. Where the discussion stopped (resume here)

- Questions 1–6 and 5a are decided. **Next: question 7 (versions)**, then question
  8 (design-lens additions).
- **Facts for question 7:**
  - Sidecar POM is `1.0.0-beta.4-SNAPSHOT` with Framework `1.0.0-beta.8`.
  - After the reset no SDK code exists (`sdks/java/.gitkeep` only).
  - The test suite's maintained runtime lane uses Framework `1.0.0-beta.9-SNAPSHOT`
    and pinned Sidecar beta.3 source.
- **After the open questions:** rewrite the planning documents together in a fresh
  conversation (section 5, step 2), then run the search check (step 4).
- The Sidecar PR-8 file deletions are staged but not committed. Nothing has been
  committed.

## 7. Evidence status

- **Verified by code reading in the discarded 8.1 code** (no longer present):
  - the Keycloak-specific profile, claims and role path
  - the Exchange provider restriction
- **Present in the current baseline:**
  - the callback passthrough (`GenericRestSkillHandler`, `CALLER_PASSTHROUGH`)
  - `SidecarJwtProperties`
  - the `GrantedAuthorityDefaults` prefix
- **Not verified:** management trust endpoint details, the starter wiring, the
  sizes of the changes.
- **Not tested here:** other IdPs' token layouts are from general knowledge.

## 8. Instructions for the resuming agent

- Work in a fresh session.
- Do not use any discarded 8.1 code or harness.
- Execute section 5 only with the developer, step by step.
- No commit, push, release, paid call or provider access without separate
  authorization.
- Preserve the test suite's `fixtures/reference` pack and the beta.9 embedded and
  Python lane.

## 9. Lifetime

Delete this document once section 5 step 2 is complete and the search check passes.
