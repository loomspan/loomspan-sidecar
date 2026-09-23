---
date: 2026-09-22
repository: loomspan-sidecar
branch: main
commit: 04a17695f6379acbe0df5f057171c824aa348a10
ticket: ai/thoughts/tickets/PR-5-automatic-tls-compose.md
tags: [deployment, tls, compose]
---

# Automatic TLS Compose Research

## Research Question

How does the current production stack handle TLS, proxy headers, persistence, and deployment verification?

## Summary

The current Compose stack terminates TLS inside Sidecar, publishes port 8080 through a host HTTPS port, and keeps management health private. Sidecar email links use an explicit external base URL. No forwarded header strategy is configured. The production verifier exercises TLS, setup, login, JWT execution, SMTP capture, SQLite recreation and recovery, and shutdown using disposable fixtures.

## Repository State

At research start, `main` was at `04a17695f6379acbe0df5f057171c824aa348a10`; only the PR-5 ticket was untracked.

## Current Behavior and Data Flow

`examples/production/compose.yaml` mounts a PEM chain/key into Sidecar and publishes its application port. The health port stays within the container. `src/main/resources/application.yml` sets Secure cookies by default and an explicit management external URL. `scripts/verify-production.py` starts a disposable Compose project with fixture mail, issuer, model, and TLS certificate, then drives browser and API flows.

## Key Components

- `examples/production/compose.yaml:1` — one Sidecar service, local SQLite volume, direct TLS and published application port.
- `examples/production/production.env.example:1` — TLS path, external URL, and application settings.
- `examples/production/README.md:1` — current supplied-certificate procedure and state recovery.
- `src/main/resources/application.yml:15` — explicit external URL and Secure session cookie.
- `scripts/verify-production.py:1` — disposable production integration fixture.
- `src/test/java/ai/loomspan/sidecar/management/ManagementMailHttpIntegrationTest.java:29` — trusted emailed origin behavior.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| TLS ingress | Sidecar listens with PEM material; Compose publishes 8080 (`compose.yaml`). |
| Email links | Explicit `external-base-url` config supplies HTTPS origin (`application.yml`, mail test). |
| Storage | One `sidecar-data` volume persists SQLite (`compose.yaml`, production verifier). |
| Readiness/shutdown | Private 9091 healthcheck and 45-second stop grace (`compose.yaml`). |

## Existing Tests and Fixtures

`scripts/verify-production.py` is the broad deployment test and uses disposable fixtures. Java tests cover management login, mail origins, authentication and application TLS. Docker, OpenSSL, Python, and the local beta 5 snapshot are needed for full verification.

## Dependencies and Operational Constraints

The existing application image remains usable without Compose. The production verifier's certificate fixture currently trusts a self-signed certificate by disabling validation; the new TLS-specific verification will need a trusted and an untrusted client. Caddy's data directory must persist managed certificates and its private CA. Caddy documentation says DNS challenge needs a provider plugin and DNS API credentials; the stock image does not bundle all providers.

## Historical Context

The PR-5 ticket extends the existing production deployment and preserves the beta 5 framework boundary and single-instance SQLite constraints.

## Open Questions

No material product decision remains for planning. Public CA issuance and a real DNS-provider API cannot be exercised by local fixtures.
