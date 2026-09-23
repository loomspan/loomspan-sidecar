# PR 5: Deploy Sidecar with automatic HTTPS for internal and public use

## Outcome

Operators can deploy the existing Sidecar Compose stack behind Caddy with a
straightforward internal HTTPS setup and documented public certificate options.
Certificate issuance and renewal remain outside Sidecar's Java application. The
same application image remains usable as a localhost sidecar or a microservice,
providing a foundation for later cloud-specific deployment work.

## Requirements

- Add Caddy as the TLS termination point for the supported Compose deployment.
  Forward to Sidecar over the private Compose network; do not publish Sidecar's
  HTTP or management health ports on the host. The private HTTP hop is deliberate;
  end-to-end TLS between Caddy and Sidecar is outside this ticket.
- Support explicit certificate choices within this deployment:
  - Caddy-managed private certificates for internal installations without an
    existing CA. Provide the shortest complete setup path, including internal
    name resolution and installation of the public CA root in browser/OS and
    application trust stores. Do not imply that issuance automatically makes
    remote clients trust the certificates or recommend disabling verification.
  - Automatic publicly trusted certificates for registered domains. Support
    ordinary public endpoint validation and DNS validation for private endpoints
    that must not accept internet traffic. Planning selects one documented and
    verified DNS-provider integration as the reference; do not promise universal
    bundled DNS-provider support. Explain credentials, required connectivity,
    and how other Caddy DNS integrations can be configured without Java changes.
  - Operator-supplied certificate chain and key, including corporate PKI
    certificates. Document replacement and reload; this mode does not promise
    automatic issuance or renewal by Caddy. Specialized corporate CA enrollment
    integrations are outside scope.
- Make certificate selection and prerequisites clear. Recommend existing trusted
  corporate certificates when available; explain the client trust step for the
  built-in private CA and the DNS access requirements for public certificates.
  Persist and protect Caddy's certificate/CA state across container recreation,
  with backup/recovery guidance that distinguishes public trust roots from private
  keys. Never bake keys or DNS credentials into images or source control.
- Configure Caddy and Spring's Forwarded/X-Forwarded-* handling as one trusted
  proxy boundary. Client-supplied forwarded headers must not override the
  authoritative external scheme/host/port or influence trusted emailed origins.
  Preserve HTTPS redirects, Secure session cookies, explicit external HTTPS URL
  configuration for emailed links, and existing authentication/authorization.
- Preserve localhost operation and the ability to run the application with
  externally supplied TLS configuration or another trusted deployment proxy.
  Caddy is a deployment component, not a required application dependency.
- Preserve single-instance SQLite on storage with reliable local filesystem
  locking, persistent accounts/configuration, startup/readiness and bounded
  shutdown. Do not introduce replicas, database migration to a different engine,
  or network filesystem assumptions to enable this work.
- Supply this ticket's deployment tests and operator documentation against the
  developer-installed framework beta 5 snapshot, using supported framework APIs
  and standard Spring configuration. Verification must use controlled fixtures,
  not send real email or modify customer DNS. Clearly distinguish local
  certificate automation evidence from any unperformed live CA/provider checks.

## Acceptance criteria

- [ ] The documented Compose stack serves the console and execution API through
  Caddy HTTPS, with Sidecar HTTP and health unavailable through host-published
  ports; intended private callers can reach the HTTPS endpoint.
- [ ] Following the private CA instructions produces a verified TLS connection
  from a separately configured client; an untrusted client rejects it. Managed
  certificate renewal and container recreation preserve a working trust chain
  without manual leaf-certificate replacement or loss of persisted CA state.
- [ ] Public endpoint validation and the selected DNS-validation reference have
  reproducible configuration and controlled automation evidence. DNS validation
  requires no internet ingress to the application. Documentation identifies
  provider-specific prerequisites and the exact limits of verification evidence.
- [ ] An operator-supplied chain/key works with the same deployment, and the
  documented replacement/reload procedure serves the replacement certificate.
- [ ] Proxy-aware login, redirects, Secure cookies and emailed HTTPS links work;
  adversarial forwarded headers cannot change the trusted origin or bypass
  existing authentication/authorization.
- [ ] Certificate/CA state and secrets have documented persistence, permissions
  and recovery procedures; images and tracked files contain no operational keys
  or credentials. The guide explains which trust material clients must receive.
- [ ] Localhost operation and supplied application TLS remain usable without
  Caddy. Deployment verification demonstrates preserved SQLite state,
  readiness/startup and shutdown behavior using this ticket's own evidence.

## Context

External work-item label supplied by the developer: **PR 5**, to match their
GitHub ticket. No GitHub URL was supplied; this does not identify a verified
GitHub issue or pull-request number.

The developer wants both internal on-premises and public/cloud deployment
options without making a simple internal installation unnecessarily difficult.
The agreed starting point is Caddy in front of Spring Boot and correct forwarded
header handling. In separate Compose containers the upstream is the service's
network name, not localhost; localhost applies only to a shared network namespace.

This ticket extends the existing production Compose/TLS work. It does not add
Azure, AWS or Google Cloud infrastructure templates, Kubernetes support, service
mesh/mTLS, or a certificate issuance subsystem inside Sidecar. Those may build
on this foundation later. Existing repository framework-boundary and release
rules remain applicable; no framework rebuild, release publication, or live
cloud provisioning is authorized by this ticket.

Source hints for planning:
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
- [Caddy TLS configuration](https://caddyserver.com/docs/caddyfile/directives/tls)
- [Existing production deployment](../../../examples/production/README.md)
- [Design lens](../design-lens.md)

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change introduces a production proxy trust boundary and
  multiple certificate provisioning paths. Certificate persistence, renewal,
  client trust and deployment verification require research and design.
- **Reassessment triggers:** none; production security and deployment design
  require Full.
