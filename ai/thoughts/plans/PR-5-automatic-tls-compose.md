# Automatic TLS Compose Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/PR-5-automatic-tls-compose.md`
- Research: `ai/thoughts/research/PR-5-automatic-tls-compose.md`
- Outcome: Caddy fronts the one-instance Sidecar stack with internal, public, DNS, or supplied certificate configuration.

## Current State

`examples/production/compose.yaml` makes Sidecar the HTTPS server and publishes its application port. `application.yml` has no forwarded header strategy. `verify-production.py` assumes a direct PEM mount.

## Desired End State

Caddy alone publishes HTTP(S), forwards to private Sidecar HTTP, sanitizes forwarded origin headers, and persists certificate state. Explicit external HTTPS URL remains the authority for mail. Localhost and direct application TLS remain supported outside this Compose deployment.

## Scope

### In scope

- Compose, Caddy configurations, deployment tests, and operator guide.
- Standard Spring forwarded header handling at the trusted Caddy boundary.

### Out of scope

- Java certificate issuance, cloud templates, replicas, database migration, or framework changes.

## Active Project Guardrails

Use the smallest complete deployment change; retain supported framework APIs, one SQLite instance, and one shutdown budget.

## Impact and Risk Analysis

Forwarded header spoofing could alter redirects and origin handling. A missing Caddy data volume could rotate the internal trust root. Public ACME tests must not issue real certificates or edit customer DNS. Mount and filesystem permissions must protect keys.

## Implementation Approach

Use a single Compose bridge network with only Caddy publishing host ports; Sidecar has no published ports but retains outbound access for model and mail calls. Select one checked-in Caddyfile per certificate mode with an env path. Use a supplied-cert overlay solely for the certificate mount. Build a Cloudflare module image for the reference DNS mode; its token comes from an operator env file. Caddy strips client `Forwarded` and all `X-Forwarded-*` headers, then writes authoritative values for Spring. Caddy's site matcher restricts the external host. Keep the explicit external URL for email.

## Phase 1: Compose and proxy boundary

### Changes

- [x] `examples/production/compose.yaml` — add Caddy, private upstream, persistent Caddy state, and remove Sidecar TLS/published port.
- [x] `examples/production/Caddyfile.*` — certificate modes and sanitized forwarded origin.
- [x] `examples/production/compose.yaml` — enable standard Spring forwarded handling only behind this trusted Caddy boundary.
- [x] `scripts/verify-production.py` — use the Caddy path and assert private Sidecar ports, login, cookies, email, auth, state, readiness, shutdown.
- [x] `src/test/java/ai/loomspan/sidecar/config/ConfigurationReferenceTest.java` — update deployment configuration assertions for the Caddy boundary.

### Automated verification

- [x] `python scripts/verify-production.py --image loomspan-sidecar:sc5-local` — fixture deployment passes.

### Optional developer checks

- [ ] Confirm actual enterprise trust-store distribution for client fleets.

## Phase 2: Certificate choices and guidance

### Changes

- [x] `examples/production/README.md` and `production.env.example` — complete prerequisites, deployment, trust, persistence, renewal/reload, and recovery instructions.
- [x] `examples/production/compose.supplied.yaml`, DNS provider image/config — supplied and DNS reference modes.
- [x] `scripts/verify-production-tls.py` — controlled internal trust/recreation, HTTP redirect, supplied replacement, and public/DNS configuration tests.

### Automated verification

- [x] `python scripts/verify-production-tls.py --image loomspan-sidecar:sc5-local` — local certificate automation and replacement pass.

### Optional developer checks

- [ ] Live public ACME and Cloudflare API issuance in a configured installation; no customer DNS change in routine verification.

## Test Strategy

Prefer disposable Compose projects and local certificates. Use real TLS handshakes for trust and replacement, and local configuration adaptation for public/DNS modes. Re-run Java tests for proxy-aware behavior and the full safe Maven suite.

## Acceptance-Criteria Traceability

| Criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| HTTPS ingress/private ports | Compose/Caddy | Deployment script port and API checks |
| Private CA trust/renewal | Internal Caddyfile/data volume | Trusted/untrusted TLS and recreation |
| Public and DNS | Public/DNS Caddyfiles and Cloudflare build | Config adaptation and controlled fixture |
| Supplied replacement | Overlay/Caddyfile | Replacement TLS handshake |
| Proxy/security/email | Header config, Spring, external URL | Adversarial headers, browser/API/mail fixture |
| State/secrets | Volumes and guide | Recreation, secret scan, backup guidance |
| Localhost/TLS/SQLite/lifecycle | Optional Compose boundary, existing app config | Java TLS and production fixture suite |

## Risks and Rollback/Recovery

Stop the instance and back up the SQLite file set and Caddy data separately. Restore the private CA data before restarting internal-mode clients; replacing it changes their trust anchor. Revert Compose/config deployment files to return to the prior supplied-cert layout if needed.

## References

Ticket, research, `examples/production`, `scripts/verify-production.py`, `src/main/resources/application.yml`.
