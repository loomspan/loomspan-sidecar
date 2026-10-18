# PR 8.1.6: SDK callback authentication and reference-app callback authorization

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Applications receive Sidecar callbacks on their existing Spring server through
one dedicated, stateless bearer-JWT chain. The SDK independently authenticates
each callback against the application's own trust. The application then
authorizes it with standard Spring method security and its own business checks.
The reference app demonstrates the required wiring, and the Local recipe
becomes complete end to end.

## Requirements

### Callback chain (loomspan-sdk-spring-boot-starter)

- Register one dedicated stateless bearer `SecurityFilterChain` at highest
  precedence.
  - The default path prefix is `/loomspan/callbacks/**`, configurable as one
    prefix.
  - Allow GET and POST.
  - No separate server, security manifest, URL-policy DSL, application-chain
    integration or analysis of arbitrary request matchers.

| Request | Behavior |
| --- | --- |
| Missing, multiple or invalid bearer credentials | 401 with `WWW-Authenticate: Bearer` |
| Query-parameter token | Reject; never authenticate it |
| Valid JWT, denied application authorization | 403 before the denied method executes |
| Cookie or session without a bearer token | No authentication fallback |
| Valid JWT without additional method or business checks | Accessible within allowed methods |

- Inside the chain:
  - Never read or create sessions, and ignore cookies.
  - Disable CSRF only within the prefix.
  - Never redirect to login and never fall through to another chain.
  - Write authentication rejections directly instead of forwarding to `/error`.
  - Disallowed methods and malformed or firewall-rejected requests use normal
    Spring handling. Planning documents the concrete responses.
- Validation is independent of Sidecar and requires:
  - an RS256 signature from the callback key set;
  - JOSE `typ` = `loomspan-execution+jwt` and `token_use` = `loomspan_execution`;
  - `iss` equal to the application's own `loomspan-sdk.issuance.issuer` (there
    is no separate callback issuer setting);
  - an audience that includes the callback audience (default: the issuer);
  - valid claim types;
  - an unexpired token, with 60 seconds of skew.
- On success, install the verified issuer, subject and exact token authorities,
  with no prefix mapping. The context is request-scoped and cleared after use.
- The SDK does not inspect annotations or compare endpoint permissions. Spring
  method security and application code authorize. URL rules from the ordinary
  application chain are not inherited.

### Callback trust

- The callback key set has two parts:
  - the application's own signing public key(s): the current colocated key or
    the configured provided key;
  - an optional rotation-overlap JWKS file.
- It is independent of Sidecar's trust file.
- The overlap file uses Sidecar's file rules:
  - whole-file validation and atomic publication;
  - reload on change and on an unknown `kid`, with the unknown-`kid` reload rate
    limited;
  - fail closed with no stale fallback;
  - automatic recovery.
- An unconfigured overlap file is not an error. If a configured overlap file
  becomes unusable, callback authentication stops, and the app's own key must
  not conceal the failure.
- Keys removed from the overlap file are rejected after reload.
- No public key-hosting endpoint.

### Reference app and composed coverage (loomspan-sidecar-test-suite)

- Extend the PR 8.1.5 reference app:
  - put callback routes under the SDK prefix;
  - have no competing chain intercepting the prefix;
  - enable standard Spring method security (for example
    `@PreAuthorize("hasRole('ORDER_EDITOR')")`);
  - perform a business record-ownership check in application code.
- Composed scenarios on the Local recipe:
  - An authorized callback: a skill's call reaches the reference-app callback
    carrying the user's forwarded token and succeeds.
  - Method denial: a token without the required authority gets a 403, and the
    method body is not invoked.
  - Business denial: a record the user does not own is denied by application
    code.
  - Ordinary form login and the application's normal routes keep working
    alongside the callback chain.
  - Callback expiry: a captured token that expires before the callback fails the
    skill call with `execution credential expired` and sends no request.
- **Sidecar reads its JWT verification keys only from its configured JWKS file,
  in both file and database application ConfigurationMode.** The callback key
  set is configured separately on the application side.
- Keep the detailed invalid-request and token variants in starter component
  tests, not in composed scenarios.

### Documentation

- Document in SDK documentation:
  - the callback prefix and chain precedence;
  - the requirement to keep callback routes under the prefix and to avoid
    competing chains;
  - that routes outside the prefix are not protected by the SDK;
  - that method security must be enabled;
  - the responsibility for business checks;
  - the overlap file and its failure behavior;
  - that browser sessions never authenticate callbacks.

## Acceptance criteria

- [ ] Component tests cover every row of the request table, including multiple
      bearers, query tokens and cookie-only requests. 401 responses include
      `WWW-Authenticate: Bearer` and never redirect or forward to `/error`.
- [ ] Component tests reject each JWT variant: signature, issuer, audience,
      `typ`/`token_use`, expiry and claim shape.
- [ ] The chain creates no sessions, disables CSRF only within the prefix, never
      falls through, and clears its security context after each request.
- [ ] The prefix is configurable. GET and POST work, and disallowed methods use
      normal Spring handling, as documented.
- [ ] Verified issuer, subject and exact authorities are installed without
      prefix changes.
- [ ] Callbacks validate against the app's own key without an overlap file.
- [ ] A configured overlap file that becomes unusable stops callback
      authentication, and correcting it recovers.
- [ ] Keys removed from the overlap file are rejected after reload.
- [ ] Composed Local recipe scenarios pass:
      - an authorized callback with the forwarded token;
      - a method denial before invocation;
      - a business record denial;
      - ordinary login still working;
      - callback expiry failing without a request being sent.
- [ ] The Local recipe now covers real invocation and callback with JWT and
      user authorization.
- [ ] SDK documentation covers the callback wiring and its limits.

## Verification ownership reconciliation

The [deployment simplification ticket](2026-10-18-simplify-deployment-and-composed-test-ownership.md)
uses the existing SDK lane for current plain-Java invocation and deployment
transport/real-TERM baselines. See [current ownership](../../../docs/verification.md).
Those baselines do not complete this ticket's future reference-app, callback,
recipe or trust obligations. No TLS certificate-lifecycle testing is transferred.

## Context

- Depends on PR 8.1.5 (reference app and Local recipe). It also builds on
  PR 8.1.3 (signing keys and issuer) and PR 8.1.4 (Sidecar forwarding and the
  callback expiry check).
- Canonical sources:
  - [Identity and callbacks](../phases/2026-10-15-sdk-v1-identity-callback-reference.md), "Callback receiving"
  - [Signing and trust](../phases/2026-10-15-sdk-v1-signing-trust-reference.md), callback key set and overlap file rules
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md): "SDK callback chain" and "Callback trust" (focused and composed), the callback part of "Queue/callback token lifetime", and the "SDK issuance" composed authorized callback
- PR 8.1.8 covers callback-receiver rotation.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This adds a new authentication boundary and Spring Security
  chain contract, a trust-reload lifecycle and cross-repo composed authorization
  coverage.
- **Reassessment triggers:** none

## Pipeline notes

- The work spans the starter in `loomspan-sidecar` and the reference app and
  composed scenarios in `loomspan-sidecar-test-suite`. This is intentional.
