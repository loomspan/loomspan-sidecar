# Automatic TLS Compose Testing Plan

## Change Summary

Move production TLS termination to Caddy with three managed/supplied certificate paths while keeping Sidecar's existing security and persistence.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Proxy boundary | Spoofed origin or exposed upstream | Adversarial forwarded headers and port inspection |
| Trust | Untrusted client accepted or CA lost | Real TLS verification and recreation |
| Certificates | Supplied rotation fails | Certificate fingerprint change after reload |
| Lifecycle | SQLite or shutdown regresses | Existing production fixture and shutdown test |

## Existing Coverage and Environment Constraints

`scripts/verify-production.py` exercises the current direct TLS Compose flow. `ManagementMailHttpIntegrationTest`, `ConsoleSecurityIntegrationTest`, and `RestTransportTlsIntegrationTest` cover related application behavior. Docker/OpenSSL/Chromium and the developer-installed beta 5 snapshot are required. Public CA and provider API calls are excluded from routine tests.

## Failing Test First

- Name: private Sidecar port and trusted Caddy handshake
- Type: Compose integration
- Location: `scripts/verify-production-tls.py`
- Arrange/Act/Assert: start disposable stack, inspect published ports, connect with and without exported private root.
- Expected pre-fix failure: current Compose has no Caddy/private root and publishes Sidecar directly.

## Tests to Add or Update

### 1. `verify-production-tls.py`
- Type: disposable container integration plus Compose configuration check
- Location: `scripts/verify-production-tls.py`
- Proves: private CA trust, leaf reissuance, recreation, supplied certificate replacement, direct application HTTP/TLS, public/DNS Caddyfile validity, and private upstream.
- Inputs/fixture: temporary local certificates, network, volumes, and the production Compose files.
- Doubles or boundary isolation: no public CA, DNS API, or real email.
- Edge cases: untrusted client rejection and adversarial forwarded headers.

### 2. `verify-production.py`
- Type: deployment integration
- Location: `scripts/verify-production.py`
- Proves: setup, login, JWT authorization, email HTTPS origin, SQLite persistence, readiness and shutdown through the new proxy layout.
- Inputs/fixture: existing fixture mail/issuer/model.
- Doubles or boundary isolation: disposable Compose project.
- Edge cases: spoofed forwarded headers and no published health/app port.

## Safe Verification Commands

- Focused: `python scripts/verify-production-tls.py --image loomspan-sidecar:sc5-local`
- Related suite: `python scripts/verify-production.py --image loomspan-sidecar:sc5-local`
- Full safe suite: `.\mvnw.cmd -B -ntp test`

## Optional Developer Checks

- Live public ACME and Cloudflare DNS validation with authorized domain/credentials.

## Exit Criteria

- [ ] The new fixture fails before the change for the intended reason. Not run before implementation; the baseline Compose has no Caddy service.
- [x] New and updated tests pass.
- [x] Maven safe suite passes.
- [x] Every criterion has executable evidence or a clearly identified live boundary.
- [x] Tests use no customer DNS or real mail.
