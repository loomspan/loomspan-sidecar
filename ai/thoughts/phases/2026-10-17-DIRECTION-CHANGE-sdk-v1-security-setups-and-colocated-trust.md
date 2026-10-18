# DIRECTION CHANGE — SDK v1 security setups and colocated trust (2026-10-17)

These are the developer's decisions from a 2026-10-17 review of the PR-8.1 work,
with network configuration and testing clarified on 2026-10-18.
This document is temporary. It gets folded into the original planning documents
and then deleted (section 9). Until then it overrides every conflicting roadmap,
phase, reference, scenario, matrix and recipe document.

**Development policy:**
- Destructive replacement; no shims. Apply the
  [design lens](../design-lens.md#simplicity-and-technical-debt).
- Removed features are deleted, not kept as experimental code.
- Untested network compositions are not removed features. The three reference
  recipes bound integration testing, not the configurations customers may use.

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

**V1 deployment and trust guideline — clarified 2026-10-17:**
- **If apps share a Sidecar, they trust each other in every way.** This applies
  to asserted user identities and roles, reusable tokens, business data sent
  between applications and Sidecar, and the network deployment boundary.
- V1 supports two arrangements: one application paired 1:1 with Sidecar over
  localhost, or a fully trusted group sharing Sidecar on one machine or across
  hosts in a microservices environment.
- One issuer per Sidecar. The trusted-group arrangement below shares signing
  identity, user identity semantics and role vocabulary. V1 has no arrangement
  for independent or partially trusted identity domains sharing a Sidecar.
- Applications requiring isolation use separate Sidecars. Do not add roadmap
  options, configuration or test combinations for mutually untrusted apps sharing
  one Sidecar.
- Mutual application trust does not disable JWT validation, application user and
  record authorization, or the selected transport's protections against outsiders.
  Local HTTP remains confined to the protected local boundary; shared trust does
  not permit plaintext traffic across hosts.

**Credential strategies:**
- Spring-issued and custom only. The app authenticates users any way it likes and
  issues a short-lived execution JWT.

**Tested network setups — agreed 2026-10-18:**
- Build and test three reference recipes: Local, Standard and Hardened. Each
  reference recipe uses the same transport level in both directions; callbacks
  may be omitted.
- These recipes define test coverage, not a runtime allowlist. Invocation and
  callback transport configuration remain independent. Do not reject a composition
  merely because it differs from these recipes, or label it unsupported or
  experimental solely because it is untested. For example, protected local HTTP
  invocation with Caddy-managed mTLS callbacks is permitted but not a composed
  integration-test requirement.
- Existing authentication, trust, destination and transport validation still
  applies to each hop. This does not relax the loopback requirement for colocated
  JWT trust or make unprotected remote HTTP a tested deployment.

| Setup | Invocation | Callback | Sidecar trust | SDK signing |
|---|---|---|---|---|
| Local | HTTP | HTTP / none | `colocated` (shipped samples) or `file` | `colocated` (shipped samples) or `provided` |
| Standard | Direct HTTPS | Direct HTTPS / none | `file` | `provided` |
| Hardened | Caddy-managed mTLS | Caddy-managed mTLS / none | `file` | `provided` |

- **Local:** direct HTTP over protected localhost communication; no proxy or TLS
  certificates required.
- **Standard:** the receiving application and Sidecar servers terminate HTTPS;
  callers verify server certificates. No client certificates or mandatory Caddy.
- **Hardened:** Caddy handles mTLS and trusted-group admission in both directions:

  ```text
  App + SDK -> local HTTP -> App Caddy
            -> mTLS -> Sidecar Caddy -> local HTTP -> Sidecar

  Sidecar -> local HTTP -> Sidecar Caddy
          -> mTLS -> App Caddy -> local HTTP -> App callback endpoint
  ```

  The SDK callback chain handles JWT authentication, not TLS handshakes. Caddy
  uses deliberately group-scoped certificate trust. Browser ingress uses a
  separate listener from mTLS callback ingress. No-callback deployments omit
  callback routes while retaining the invocation path.
- **Deployment boundary:** provide and test a reference Compose recipe in which
  each app shares a protected network namespace with its Caddy, and Sidecar shares
  another with its Caddy. Internal backend and outbound-proxy HTTP listeners bind
  to loopback; only intended Caddy TLS listeners are reachable externally.
  Outbound proxy routes have fixed destinations. Operators enforce equivalent
  protection in their own deployments. Loomspan does not add a network/firewall
  controller or SDK runtime discovery/proof of that boundary. Full mutual trust
  means no isolation machinery between an app and its own Caddy or against
  deliberate impersonation by another trusted group app.
- Native mTLS and mesh/custom compositions are outside the composed test matrix;
  do not block them merely for being untested. This does not require adding a
  separate SDK-native mTLS implementation or new adapters for every composition.
- **TLS certificate lifecycle — agreed 2026-10-18:** DevOps owns issuance,
  secure storage, delivery, renewal, replacement, CA rotation and revocation using
  standard Spring Boot configuration for direct HTTPS and Caddy facilities for
  proxy-managed mTLS. Reference recipes accept operator-provided material and link
  to [Spring Boot SSL documentation](https://docs.spring.io/spring-boot/reference/features/ssl.html)
  and [Caddy TLS documentation](https://caddyserver.com/docs/caddyfile/directives/tls).
  Loomspan supplies no certificate-management service, watcher, renewal scheduler,
  reload controller or prescribed restart/replacement procedure. No seamless
  rotation or fixed connection-retirement/expiry-cutoff guarantee is introduced.
  Supersede the old plans' product-specific TLS lifecycle and associated testing
  obligations. JWT signing-key rotation and JWKS file reload remain the separate
  token contracts below.

**SDK signing:**
- **`colocated`:**
  - RSA key generated in memory at startup; the private key never touches disk.
  - **Single-instance deployment clarification — agreed 2026-10-18:** one running
    application instance paired with one Sidecar; the app is the sole writer of
    its key directory. Independent pairs use separate directories. Multiple apps
    or replicas sharing a Sidecar use provided signing and file trust. Operators
    own this arrangement; Loomspan does not enforce instance counts or add writer
    locks, registration, key merging, replica coordination or associated tests.
    Atomic replacement does not prevent competing writers from replacing each
    other's public keys, and overlapping colocated instances do not provide
    signing continuity during restart.
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
  - **JWT signing-key certificates:** an expired or near-expiry certificate logs
    a warning, never a failure. This does not relax TLS certificate validation.
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
  - **File authority and recovery — clarified 2026-10-17:** the current file is
    authoritative. Validate the entire candidate before atomically replacing the
    in-memory key set; requests never observe a partially loaded set.
    - A valid nonempty JWKS supplies the current trusted keys.
    - A valid empty JWKS withdraws all keys: reject execution authentication and
      report not ready.
    - A missing, unreadable or malformed file stops execution authentication and
      reports not ready once detected. Do not continue accepting credentials with
      the last valid keys. A corrected valid nonempty file restores acceptance
      automatically through reload.
    - Writers use a temporary file followed by atomic replacement. Choose one
      documented reload interval in implementation planning; no instantaneous
      cross-process withdrawal guarantee is implied.
  - **Withdrawal boundary:** after reload, new requests cannot authenticate with
    a removed key. Before queued work enters the Framework, validate its captured
    token against current trust and expiry; unusable trust or an invalid token
    prevents handoff. Already admitted Framework work is not cancelled.
  - Each callback receiver validates new callbacks against its own current trust.
    Its configured signing public key and optional rotation-overlap JWKS remain
    separate from Sidecar's trust. Apply the same file failure/recovery rules to a
    configured overlap file; an unconfigured optional file is not an error.
    Updating Sidecar does not update receivers. Operators replace compromised
    signing keys and update trust at all participating applications independently.
  - Without trust history, restoring an old JWKS restores its keys' authority.
    Preventing such restoration is an operator responsibility, not a v1 ledger
    or revocation service.
  - Token checks: RS256, audience, `typ`, `token_use`, and expiry with 60 s
    skew. **Agreed 2026-10-18:** Sidecar does not check `iss` against a configured
    value and has no issuer setting; its trusted key file alone defines accepted
    origin. Callback receivers still check their configured issuer. There is no admission freshness requirement or fixed 300 s maximum.
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
- **Expiry alone — clarified 2026-10-17:** no maximum token age at admission.
  An otherwise valid, unexpired token may submit new work, discover skills or poll
  results regardless of how long ago it was issued. No admission-freshness window
  or corresponding configuration/tests are required. Queue time still consumes
  the captured token's lifetime, and callback/queue expiry checks below remain.
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

**Trusted group sharing Sidecar (`file` trust and `provided` signing):**
- The apps share one keystore, one issuer value and one callback audience.
  Sidecar and callbacks validate that shared signing identity. Multi-key JWKS
  supports rotation overlap within this one issuer, not independent app issuers.
- The group may run on one machine or across hosts. Local, Standard and Hardened
  are the tested recipes; other network compositions remain permitted subject to
  each hop's security requirements. JWT trust
  and transport validation are configured independently; neither substitutes for
  the other.
- Document the conditions:
  - full mutual trust for identity, roles, tokens, data and network participation;
    compromising one app or the shared signing key compromises the group
  - **one shared user directory**, because `(issuer, subject)` must mean the same
    person in every app
  - agreed role names across the group
  - coordinated key rotation
- Separate app issuers/keys, selective cross-app callback trust and per-key role
  restrictions are outside v1. No issuer-to-key mapping subsystem is needed.
- Execution ownership and application user/record authorization remain enforced;
  they do not provide isolation against another fully trusted group application.

**Trust visibility:**
- There is no management trust API, Console trust page or trust write path.
- Visibility is through the health endpoint plus one log line on each load or reload.
  The health endpoint reports mode, file path, loaded `kid`s, last load time and
  ready/waiting.
- Ticket detail: report only the status anonymously and put the details behind the
  existing management authentication, unless the ticket decides otherwise.
- A read-only Console card is a possible later addition.

**Management scope — agreed 2026-10-18:**
- Leave existing management accounts and management tokens unchanged. The SDK
  uses execution JWTs and the execution API; it does not need management-account
  conversion, new token presets or a management-authoring API.
- Remove the old plans' single-administrator conversion, management-token redesign
  and their new acceptance obligations from this SDK effort. No management trust
  API or indirect trust-publication machinery is needed for operator-owned JWKS
  files. Existing management/execution credential separation and ordinary regression
  coverage remain; this does not authorize weakening current management security.

**Roles:**
- **One allowlist at issuance:** `loomspan-sdk.issuance.roles: [ORDER_READER, …]`.
  Only listed authorities the user actually has go into the token, with names
  unchanged.
- **Sidecar uses token roles as-is.** There is no Sidecar role mapping.
- **Exact names — agreed 2026-10-18:** preserve each authority name exactly across
  the application, issuance allowlist, JWT `roles`, Sidecar execution authorities,
  skill `rbac_roles` and callback authorities. Do not add, strip or translate
  prefixes. Applications need not rename existing authorities.
  - For an application authority `ROLE_ORDER_EDITOR`, every location above uses
    `ROLE_ORDER_EDITOR`. With the application's default Spring `ROLE_` prefix,
    `@PreAuthorize("hasRole('ORDER_EDITOR')")` checks this authority and is
    equivalent to `@PreAuthorize("hasAuthority('ROLE_ORDER_EDITOR')")`.
    Only the `hasRole` argument omits the prefix.
  - For an application authority `ORDER_EDITOR`, every location uses
    `ORDER_EDITOR`, and `hasAuthority('ORDER_EDITOR')` matches it directly.
  - The SDK does not change the application's Spring role-prefix settings.
    Spring owns the interpretation of `hasRole`; Loomspan copies authority names.
  - Replace Sidecar's current JWT prefix-adding behavior and configure Framework
    role evaluation consistently for exact `rbac_roles` matching using supported
    public configuration. Remove the superseded execution role-prefix option;
    do not preserve alternate translation modes or modify management authorities.
- **Callbacks use token roles as-is.** The SDK installs verified identity and
  authorities in Spring's security context. Spring Security evaluates method
  annotations such as `@PreAuthorize("hasAuthority('ORDER_EDITOR')")`; the SDK
  does not inspect annotations or implement role-to-endpoint comparisons.
- **Callback identity is snapshot only** (the token's roles). Live mode and the
  native-account resolver are removed.
- Renaming at issuance is a possible later addition and would not break this
  configuration.

**Callback receiving (app side):**
- **One arrangement only: a dedicated chain.** The SDK registers its own stateless,
  bearer-only `SecurityFilterChain` at highest precedence. The app keeps its
  ordinary application chain and enables standard Spring method security for
  callback authorization. There are no manifests.
- **One prefix**, configurable, default `/loomspan/callbacks/**`, GET and POST only.
  Sidecar REST route paths point under it. **Agreed 2026-10-18:** routes outside
  the prefix are governed by the application's own security; the SDK cannot
  promise their protection.
- **Under the prefix: a valid token or reject.**
  - Missing, multiple or invalid `Authorization: Bearer` → 401 with
    `WWW-Authenticate: Bearer`.
  - The app's authorization rule denies → 403.
  - Query-parameter tokens are rejected.
  - Sessions are never read or created; cookies are ignored.
  - CSRF is off for the prefix.
  - No login redirect and no fallback.
  - Rejections are written directly, not forwarded to `/error`.
- **Authorization — clarified 2026-10-17:** the SDK handles authentication;
  the application handles authorization through Spring method annotations and
  business checks. Spring denies an annotated operation before the method executes
  when the verified caller lacks its required authority. Application business logic
  owns record-level checks.
  - URL authorization rules in the ordinary application chain are not inherited
    by the SDK callback chain. Applications express callback permissions through
    method annotations and business checks, not an SDK URL-policy hook or DSL.
  - A callback with no additional authorization check is accessible to any valid
    execution-token holder accepted by that application, within the allowed methods.
  - Callback identity provides the verified issuer/subject and token authorities;
    it does not recreate an application-specific native principal. Business checks
    use that documented identity.
- **Chain ordering — agreed 2026-10-18:** document the prefix and ordering
  requirements and test them in the reference app. No startup check or analysis
  of arbitrary application request matchers.
- **Ticket detail:** the response for malformed URLs that Spring's firewall rejects
  before the chain runs.

**Configuration (indicative names):**

```yaml
loomspan-sdk.signing:
  mode: colocated | provided
  colocated.key-directory: /srv/loomspan/keys # required; example path
  provided: { keystore, keystore-password-env, jwks-export }
loomspan-sidecar.trust:
  mode: colocated | file
  colocated.key-directory: /srv/loomspan/keys # required; example path
  file.jwks: /etc/sidecar/trust/jwks.json
```

- **Explicit modes — agreed 2026-10-18:** every credential strategy is explicit;
  both modes are required and a missing mode fails startup. Shipped
  operator-facing samples select `colocated` on both sides; configuration packaged
  in the Sidecar jar or SDK starter sets no mode. The colocated key directory is
  also required on both sides with no built-in value; operators choose a
  directory writable only by the application's account.
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
- The `application-chain` callback arrangement, callback security manifests,
  decorator post-processing, and multi-pattern callback routes
- Sidecar `roleMappings`, the SDK's callback `RoleMapping`, issuance renaming
  (`authority-map`), live callback identity, and the native-account resolver
- `jwk` / `pem-spki` upload formats (Sidecar reads JWKS only)
- Keycloak in production code, configuration or docs
- **Rejected designs:** unsigned JWTs (`alg: none`) and any built-in default key

## 4. Testing direction

- Unit and component tests for each option's rules.
- Build real Local HTTP, Standard HTTPS and Hardened Caddy-managed mTLS
  integration recipes/tests in `C:/opendev/code/loomspan-sidecar-test-suite`
  during Phase 1. Exercise invocation, callbacks, JWT/user authorization and
  relevant transport rejection/boundary behavior. Include focused no-callback
  coverage without multiplying all options into a deployment cross-product.
- Keep focused SDK/Sidecar tests in their owning builds, including colocated
  negatives: non-loopback refusal, missing file and wrong-mode settings.
- Other network compositions, including mixed transport levels, are untested,
  not prohibited or classified as unsupported/experimental. No composed matrix
  expansion is required for them; do not add same-level enforcement or a product
  allowlist of the three test recipes. These are planned tests, not completed
  integration evidence.
- TLS tests verify integration through the configured transport: valid invocation
  and callbacks, rejection of invalid/untrusted peers, retained JWT/application
  authorization, and prevention of Caddy bypass in the reference recipe. Retain
  the general no-automatic-submission/callback-replay contract on connection
  failure. Temporary certificates are fixtures, not a certificate-management
  feature. Do not build a TLS lifecycle suite for renewal, replacement, CA
  rollover, revocation propagation, live reload or timed connection retirement;
  those operations belong to DevOps and the underlying products. This does not
  remove JWT trust-file failure/recovery and signing-key rotation tests.
- No IdP in composed tests; test apps may use form login. If OIDC is ever tested,
  use at least two IdP token layouts.
- Code style for the harness and code:
  - small functions
  - no one-line validation chains
  - a short table selecting the three tested recipes, not a runtime allowlist
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
     - **Phase 2:** completion verification (the former Phase 4, reduced): build,
       integration, packaging and documentation checks plus independent security
       review. No release decisions or publication work.
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
   `TrustEstablishment`, `jwks-url`, `resource_access`, `azp`,
   `smallstep`, `pkce`. Rerun the check after implementation.
5. **Regenerate the 8.1 tickets** from the new phases, and run them from scratch in a
   fresh agent session.

**Release scope — agreed 2026-10-18:**
- Release timing, release versions, tags and publication are outside this effort.
  Remove those decisions and release-specific acceptance obligations from the
  reconciled SDK plans. The developer decides publication separately.
- Retain current build facts and the pinned Framework dependency. An initial SDK
  development version is a Maven implementation detail, not a commitment to a
  release version or to shipping with a particular Sidecar release.
- Completion requires build, integration, packaging and documentation checks and
  independent security review. Repository release policy still applies whenever
  a separate release is requested; it is not overridden by this scope decision.

**Design-lens reconciliation — approved and written 2026-10-18:**
- Reconsider requirements that introduce substantial complexity; include ongoing
  testing costs in scope decisions. Prefer representative tested recipes without
  restricting other network compositions solely because they are untested.
- Test tools never shape production contracts, names or configuration.
- Record full mutual trust, one issuer, exact authority names, snapshot roles,
  SDK callback authentication and application method/business authorization.
- Replace persistent signer/withdrawal-history assumptions with the agreed
  ephemeral/provided signing modes and current-file JWT trust behavior.
- Make TLS certificate lifecycle DevOps-owned and retain transport integration
  tests rather than certificate lifecycle obligations.
- **Ticket clarification — agreed 2026-10-18:** do not add a long-term design-lens
  rule for JWT trust versus application configuration mode. Relevant future trust,
  configuration and integration tickets must explicitly state that JWT verification
  keys come from the configured JWKS file in both file and database application
  configuration modes. Database mode does not move JWT trust into the database or
  introduce management trust APIs. This bounds this change; it is not a general
  exception to database authority over managed application configuration.

## 6. Open questions (to discuss one by one)

1. ~~Callback chain~~ — decided (section 2, "Callback receiving").
2. ~~Role mapping~~ — decided (section 2, "Roles").
3. ~~Management trust endpoints~~ — decided (section 2, "Trust visibility").
4. ~~Long executions~~ — decided (section 2, "Token lifetime and long
   executions").
5. ~~Phases~~ — decided (section 5, step 2).
5a. ~~Several apps per Sidecar~~ — decided (section 2, "Trusted group sharing
    Sidecar" and "Audience").
6. ~~Test CA~~ — decided. Generate a throwaway CA and certificates at test time,
   per run, in a temporary directory: valid, wrong-CA, wrong-peer and expired
   certificates for transport integration negatives. No certificate-reload or
   lifecycle test obligation remains. Use no CA service
   (no Smallstep) and no committed certificates. The ticket chooses between Java
   generation (test-scope, cross-platform) and `openssl`/`keytool`.
7. ~~Release versions~~ — outside this work (section 5, "Release scope").
8. ~~Design lens~~ — agreed changes written (section 5); the configuration-mode
   clarification belongs in relevant future tickets, not the long-term lens.

## 6a. Where the discussion stopped (resume here)

- **2026-10-18 management clarification:** existing management accounts/tokens
  remain unchanged; the old management redesign is outside this SDK effort.
  Exact role naming is also agreed in section 2, including Spring's `hasRole`
  shorthand. Release decisions are outside this work. Design-lens reconciliation
  is complete; keep the configuration-mode clarification in relevant tickets.
- **2026-10-18 network clarification:** the three tested recipes are Local HTTP,
  Standard direct HTTPS and Hardened Caddy-managed mTLS, implemented/tested in the
  external suite during Phase 1. Other compositions remain permitted and untested.
  TLS certificate lifecycle is DevOps-owned; no Loomspan replacement procedure
  or certificate-lifecycle test suite is required. JWT trust lifecycle remains
  separately in scope.
- Questions 1–6 and 5a, including exact role naming, have their decisions recorded.
  Question 7 is resolved by excluding release decisions. Question 8 (design-lens
  reconciliation) is resolved, with the configuration-mode note scoped to tickets.
- **Build baseline context, not release commitments:**
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
