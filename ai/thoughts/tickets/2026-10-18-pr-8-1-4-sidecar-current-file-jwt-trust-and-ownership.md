# PR 8.1.4: Sidecar current-file JWT trust, exact roles, ownership and token lifetime

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Sidecar accepts execution JWTs only from keys in one current, fully validated
JWKS file. It fails closed when that file becomes unusable and recovers
automatically when the file is corrected. Authorities are matched exactly.
Executions belong to the signed (issuer, subject). Queued work and outbound
callbacks never run on expired or no-longer-trusted credentials. This replaces
Sidecar's previous execution-JWT key-source and role-prefix configuration
outright.

## Requirements

### Trust source and modes

- **JWT verification keys always come from the configured JWKS file. This holds
  in both file and database application ConfigurationMode.**
  - Database mode does not move JWT trust into the database.
  - There are no management trust APIs, trust history, initialization
    ceremony or Console trust page.
- `loomspan-sidecar.trust.mode` is required and has no built-in value. The
  values are `colocated` and `file`.
  - A missing mode fails startup with a message that names the property, both
    options and the colocated quickstart.
  - Configuration packaged inside the Sidecar jar must not set a mode.
  - Settings that belong to the other mode fail startup.
- `colocated`:
  - Reads `<colocated.key-directory>/jwks.json`. The key directory is required
    and has no built-in value; a missing setting fails startup.
  - Sidecar refuses to start unless its server bind address is loopback.
    Planning documents how any additional Sidecar listener is treated.
  - If `jwks.json` cannot be found, including because the two sides name
    different directories, Sidecar stays not ready and logs the path it is
    waiting on.
- `file`: reads an operator-supplied JWKS path from `file.jwks`.
- Both modes use one loader/validator and allow multiple keys for one issuer's
  rotation.
- Sidecar has no issuer setting and never compares `iss` with configuration. The
  trusted file is its only token-origin check.
- Final property names are set during planning (the names above are indicative).
  They stay under `loomspan-sidecar.*` with no aliases.

### File validation, reload and readiness

- A valid file is a JSON object with a `keys` array. Each key must meet all of
  these rules:
  - `kty` is `RSA`, with `n` and `e`, and the modulus is at least 2048 bits.
  - `kid` is present and unique within the file. It need not equal the
    thumbprint; signatures are verified against the key itself.
  - `use` and `alg`, when present, are `sig` and `RS256`.
  - No private members (`d`, `p`, `q`, `dp`, `dq`, `qi`).
  - `x5c` is allowed and ignored for trust (no chain or expiry checks). Other
    unknown members are ignored.
- Any invalid key makes the whole file malformed; keys are never loaded from a
  partly invalid file.
- Validate the whole candidate file, then publish one complete in-memory key
  set atomically. No request ever sees a partial set.

| Detected file state | Authentication and readiness |
| --- | --- |
| Valid non-empty JWKS | Use the current keys; ready |
| Valid empty JWKS | Withdraw all keys; reject execution authentication; not ready |
| Missing, unreadable or malformed | Reject execution authentication; not ready; no stale last-good acceptance |
| Corrected valid non-empty JWKS | Recover automatically on reload |

- Reload on file change and on an unknown `kid`. Unknown-`kid` reloads are rate
  limited.
- Planning selects one bounded, documented reload interval. There is no promise
  of instantaneous cross-process withdrawal.
- Restoring an old file restores its keys; that is the operator's
  responsibility.
- Health and one log line per load or reload report the mode, path, loaded kids,
  last load time and ready/waiting.
  - Anonymous health shows status only; details require existing management
    authentication.
  - Never log tokens or private material.

### Token validation

- Require:
  - an RS256 signature verified by a currently trusted key;
  - JOSE `typ` = `loomspan-execution+jwt` and `token_use` = `loomspan_execution`;
  - valid claim types, including a non-blank `sub` and a string-array `roles`;
  - an audience that includes Sidecar's own (default `loomspan-sidecar`, so no
    configuration is needed);
  - an unexpired token, with 60 seconds of skew.
- Extra audiences are allowed.
- Never derive a key source from URLs inside the token.
- Management and login credentials can never stand in for execution
  credentials, and execution JWTs grant no management access.

### Destructive configuration replacement

- Remove the old execution-JWT settings with no aliases: `issuer-uri`,
  `jwk-set-uri`, `public-key-location`, `roles-claim`, `role-prefix` and
  `clock-skew`.
  - The token profile now fixes the roles claim and the skew.
  - Management configuration, accounts, tokens and authorization stay unchanged.
- Planning checks whether any persisted development configuration, such as
  database-mode publications, contains removed settings. If so, it documents the
  required development-data reset and its impact. This ticket does not authorize
  deleting deployed data.

### Exact roles and ownership

- Remove Sidecar's JWT role-prefix addition. Configure Framework role
  evaluation through **supported public configuration** so that token roles
  match skill `rbac_roles` exactly (`ROLE_ORDER_EDITOR` stays
  `ROLE_ORDER_EDITOR`).
  - Do not change management authorities.
  - If the public Framework surface cannot express exact matching, stop and
    escalate as a Framework issue. Never import `ai.loomspan.internal..` or
    `ai.loomspan.autoconfigure..` types.
- Execution ownership is the signed (`iss`, `sub`).
  - A fresh, valid token for the same issuer and subject may read a retained,
    owned execution under current authorization, without changing its captured
    token.
  - A different subject cannot read it.

### Queue and callback token lifetime

- There is no admission-age limit: any valid, unexpired token may submit,
  discover or poll.
- Immediately before queued work is handed to the Framework, revalidate its
  captured token against current trust and expiry.
  - An invalid token or unusable trust prevents handoff.
  - Expired queued work fails with `execution credential expired before start`.
- Already admitted Framework work is never cancelled by a trust change.
- Before each outbound callback, check `exp`.
  - Once expired, fail the skill call with `execution credential expired` and do
    not send the token, even within the receiver's skew.
  - Forward the same captured token unchanged to approved recipients only, and
    never follow redirects to new recipients.
  - Sidecar never signs, refreshes or replaces credentials.

### Boundaries, samples and docs

- Keep the ArchUnit rule forbidding application and test dependencies on
  `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`.
- Shipped operator-facing samples select `colocated`: the quickstart, the sample
  external configuration and the Compose example.
- Document the following in Sidecar documentation:
  - the trust modes and file rules;
  - the failure/recovery table and the reload interval;
  - readiness and health details;
  - the queue and callback expiry consequences;
  - that JWT keys come from the JWKS file in both ConfigurationModes;
  - the removed settings;
  - the Local deployment guidance: a developer machine, Compose with
    `network_mode: "service:sidecar"` and a shared volume with suitable
    ownership, and a Kubernetes single pod with `emptyDir`/`fsGroup` (the
    Kubernetes part is documentation only);
  - that the deployment, not Loomspan, creates the key directory and sets its
    permissions (writable by the app, readable by Sidecar).

## Acceptance criteria

- [x] Focused wiring tests show trust keys sourced from the configured JWKS file
      in both file and database application ConfigurationMode. Neither mode
      reads trust from the database.
- [x] These all fail startup with clear messages:
      - a missing trust mode (message names the property, options and
        quickstart);
      - settings from another mode;
      - a missing colocated key directory;
      - a colocated non-loopback bind.
      Packaged Sidecar configuration sets no mode.
- [x] Each key rule violation (non-RSA, under 2048 bits, missing or duplicate
      `kid`, wrong `use`/`alg`, private members) rejects the whole file.
      `x5c` and unknown members are ignored.
- [x] Missing, unreadable, malformed and empty files each stop execution
      authentication and readiness with no last-good fallback. A corrected file
      recovers automatically within the documented interval.
- [x] Key-set publication is atomic. An unknown `kid` triggers a rate-limited
      reload.
- [x] Each invalid-JWT variant is rejected: signature, `typ`, `token_use`,
      audience, expiry and claim shape. A token signed by an untrusted key is
      rejected, and `iss` is not compared with configuration.
- [x] The removed settings no longer exist and no aliases remain. Management
      behavior and existing management regression tests are unchanged. Both
      directions of the management/execution credential separation hold.
- [x] Anonymous health shows status only. Authenticated details show the mode,
      path, kids, last load time and ready/waiting. One log line per load or
      reload, with no secrets.
- [x] An exact role grants access to a skill with the same `rbac_roles` name,
      and a prefixed or unprefixed variant does not. This works through
      supported Framework configuration, and the ArchUnit boundary passes.
- [x] The owner's fresh token can poll without changing the captured token, and
      a different subject is denied.
- [x] Queued work with an expired token fails with `execution credential expired
      before start`. Queued work under withdrawn trust is not handed off.
      Admitted work is not cancelled.
- [x] An expired captured token fails the callback skill call with `execution
      credential expired` and nothing is sent. Valid callbacks forward the
      unchanged token only to approved recipients, without following redirects.
- [x] Shipped samples select colocated, and the documentation covers the items
      listed above.

## Context

- Depends on PR 8.1.1. Test tokens may use the signing code from PR 8.1.3 or a
  test signer.
- Canonical sources:
  - [Signing and trust](../phases/2026-10-15-sdk-v1-signing-trust-reference.md), "Sidecar trust modes", "Rotation, queues and expiry" and "Configuration and visibility"
  - [Identity and callbacks](../phases/2026-10-15-sdk-v1-identity-callback-reference.md), "Execution-token profile", "Exact authority names" and "Execution identity and forwarding"
  - [Deployment recipes](../phases/2026-10-15-sdk-v1-deployment-recipes.md), "Local"
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md): "Sidecar trust", "Queue/callback token lifetime", "Sidecar ownership/roles", "Management/regression" and "Framework/build boundary" rows (focused)
- Composed evidence comes from PR 8.1.5 (Local), PR 8.1.6 (callback expiry) and
  PR 8.1.8 (rotation).
- Out of scope: TLS changes, management redesign and private-key handling.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This replaces Sidecar's execution authentication, authorization
  and configuration contract. It adds concurrency-sensitive reload and
  readiness behavior and changes queue and callback lifecycle checks.
- **Reassessment triggers:** If the public Framework API cannot express exact
  role matching, escalate before continuing.

## Pipeline notes

- Removing the old execution-JWT configuration intentionally breaks the
  PR 8.1.2 suite scenario until PR 8.1.5 moves it to the new trust contract. Do
  not keep the old settings working to avoid that breakage.

## Execution notes

- Implemented through the approved Full pipeline. Actual commands, tests, earlier failures/fixes and acceptance traceability are recorded in the [implementation plan](../plans/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership.md) and its testing companion.
- The developer clarified that a separate health listener was a planning mistake. Shipped configuration now serves health, management and execution on the single application listener, with existing management sessions/read PATs controlling health details. No shared-session subsystem was added.
- Operator process settings and obsolete demo PEM keys are replaced directly. No database/account/token/draft/publication reset is required or performed. Namespace-sharing app/proxy containers are recreated together when replacing/restarting the Sidecar owner.
- Full Maven verification and final image/production Docker verifiers pass. Independent Step 5 review remains required; no Git commit or release was requested or performed.
