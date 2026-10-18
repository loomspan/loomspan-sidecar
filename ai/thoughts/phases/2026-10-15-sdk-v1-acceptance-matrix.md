# SDK v1 bounded acceptance matrix

Reconciled 2026-10-18. **All entries are planned, not run.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
Replace obsolete contracts/tests directly; do not preserve the former scenario matrix.

This is the single test inventory. Each implementation unit supplies its own
focused tests and necessary real integration coverage during Phase 1.
The rows are behavior groups, not instructions to multiply every case by every
transport, signing mode, identity source or application configuration mode.

Component tests prove individual rules and detailed invalid-input/configuration
variants. Composed integration tests prove that the real pieces work together.
Tickets may extend or reuse existing composed scenarios and identify the coverage
they supply; one scenario may satisfy several tickets' acceptance requirements.
Add a new composed scenario only when it proves behavior existing scenarios cannot.
There is no one-ticket/one-integration-scenario requirement. Retain the three recipes
and meaningful failure cases, including trust recovery and Caddy bypass prevention.

The Local colocated fixture uses one application instance and one Sidecar with
one app-owned key directory. This is an operator-owned deployment assumption,
not a runtime enforcement feature; no multi-writer coordination tests are required.

## Feature coverage

| Area / owner | Focused acceptance | Composed evidence |
| --- | --- | --- |
| SDK core | Blocking discovery/submit/status/wait; envelope validation; opaque results; HTTP/protocol/unknown-acceptance errors; timeout/interruption and ID retention; resource close | One real submit-to-terminal flow, failed execution and lost-response case |
| SDK issuance | Spring and trusted custom identity; exact role allowlist; no body-derived identity; one profile; own-audience validation; default/configured lifetime and warning | Form-authenticated caller reaches skill and authorized callback |
| SDK signing | Colocated RAM-only private key/public atomic export; restart new key; provided formats; RS256/kid; 2048-bit minimum; wrong/missing material and mode settings; missing mode or colocated key directory setting fails startup; missing/unwritable directory fails startup; password references | Local colocated start/restart; one provided-key deployment |
| Sidecar trust | Complete atomic JWKS load; key rules (RSA, size, unique kid, no private members) reject the whole file; missing/malformed/unreadable/empty stop acceptance; recovery; rate-limited unknown-kid reload; loopback refusal; missing mode or colocated key directory fails startup | File correction and same-issuer add/switch/remove rotation with bounded eventual adoption |
| Queue/callback token lifetime | No admission-age limit; queue current-trust recheck; expiry before handoff and callback prevents send; already admitted work not cancelled | Queue-expiry/no-dispatch plus representative callback-expiry failure |
| Sidecar ownership/roles | Exact roles through supported Framework configuration; issuer/subject ownership; fresh owner token can poll without changing captured execution token | Different user denied; exact role positive/negative across Sidecar and app |
| SDK callback chain | Independent signature/issuer/audience/purpose/expiry checks; invalid/missing/multiple bearer and query tokens; no sessions, redirect or error forwarding; context cleanup | Reference app route/order configuration; method denial before invocation; business record denial; ordinary app login still works |
| Callback trust | Own public key plus optional overlap file; configured file failure/recovery; removed keys rejected after reload | One rotation involving Sidecar and callback receiver independently |
| Management/regression | Existing management accounts/tokens unchanged; management/execution separation; protected health details | Reuse relevant existing regression coverage; no management feature matrix |
| Framework/build boundary | SDK has no Framework/Sidecar Java dependency; Sidecar application/test code respects public API ArchUnit rule | Clean plain Java and Boot consumers; actual Sidecar build and SDK lane |

Use a small table of JWT-invalid variants at component level (signature, issuer
for callback receivers only, audience, purpose, expiry, claim shape), not a separate
deployed environment per variant. Sidecar does not check issuer; a token signed by
an untrusted key is its origin negative. Provided key formats can be component
tests. Exercise trust-file sourcing with both file/database application
configuration using focused wiring tests; do not duplicate all three recipes for
both modes.

Provided signing-key rotation uses application restart to load the replacement
private key. Cover this in the existing rotation scenario; do not add private-key
hot-reload or uninterrupted-rotation tests. Public JWKS trust reload remains tested.

## Three real network recipes

| Recipe | Required integration |
| --- | --- |
| Local HTTP | Colocated app/Sidecar from the shipped sample configuration, real invocation and callback, JWT/user authorization |
| Standard direct HTTPS | provided/file, invocation and callback, server verification and invalid-server rejection |
| Hardened Caddy mTLS | provided/file, invocation and callback, peer rejection in both directions, no backend/outbound-listener bypass, separate browser ingress |

Use the provided-key recipe fixtures to exercise the fully trusted group with one
issuer, shared user identity/roles and callback audience. A second small app in one
recipe proves cross-app use; do not add a separate deployment matrix.
Add focused outbound-only coverage without a callback listener; omission must work
with each recipe's configuration, but does not require rerunning every security case.

Use temporary per-run certificates for relevant wrong-CA, wrong-peer and expired
transport negatives. JWT rejection must still work over accepted TLS/mTLS.
Reference proxies and clients must not replay submissions/callbacks on connection
failure. Count request attempts separately from synthetic effects; a lost response
may follow an effect. No automatic retry, rollback or exactly-once claim.

## Harness and evidence

The external suite owns composed fixtures and a small runner selecting three
recipes. Keep focused SDK/Sidecar tests in their owning builds.
Use deterministic model fixtures, form login, no provider access, and temporary
test state. Keep functions small and validation readable (no one-line validation
chains), and apply a formatter.

Record the selected recipe, tested revisions/builds and concise pass/fail evidence
with safe diagnostics. No credential/private-key/business-payload logging.
Do not restore discarded test infrastructure or overwrite maintained reference packs.
The existing beta.9 embedded and Python lane remains separate from the SDK lane
against Sidecar's pinned beta.8 dependency.

## Explicit scope limits

No async/cancellation-race suite, retry/backoff matrix, proxy-origin proof system,
business ledger/crash-recovery fixture, arbitrary Spring matcher analyzer,
TLS certificate lifecycle suite, separate login-provider deployment, untrusted
shared-Sidecar isolation or exhaustive transport cross-product.
No release/version/publication acceptance gates.

Phase 2 reviews this bounded evidence and packaging/documentation completeness;
it must not silently recreate the old matrix. New complexity requires discussion
of the requirement before new obligations are added.
