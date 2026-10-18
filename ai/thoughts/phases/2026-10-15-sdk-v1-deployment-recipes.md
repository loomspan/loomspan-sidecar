# SDK v1 tested deployment recipes

Reconciled 2026-10-18. These are planned integration recipes, not completed evidence.
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt);
replace superseded contracts without compatibility machinery.

**Three recipes bound testing, not customer configuration.** Invocation and callback
transport remain independent. Mixed and other compositions are permitted, merely
untested; do not label them unsupported/experimental or enforce matching levels.
Existing per-hop authentication, approved destinations and transport checks remain.

| Recipe | Invocation and callback | Signing / Sidecar trust |
| --- | --- | --- |
| Local | Direct protected localhost HTTP | Default colocated/colocated; provided/file also valid |
| Standard | Direct HTTPS, receiving Spring servers terminate TLS | provided/file |
| Hardened | Caddy-managed mTLS in both directions | provided/file |

Each recipe may omit callbacks. Use focused no-callback coverage, not a second full
matrix. Shared apps always trust each other completely; use one issuer and shared
identity semantics. Remote plaintext is not a supported boundary for these recipes.

## Local

On a developer machine, app and Sidecar bind to 127.0.0.1 and share a public-key
directory. Colocated signing writes only public JWKS; Sidecar refuses non-loopback
binding. A Compose example puts the app in Sidecar's network namespace with
`network_mode: "service:sidecar"` and mounts a shared key volume.
A Kubernetes pod can use a shared emptyDir and localhost; this is documentation,
not an additional required integration deployment.

Ordinary separate Compose networking does not meet colocated loopback trust.
Use provided/file there with an appropriately protected transport.
Default colocated restart intentionally invalidates previous credentials as
receivers adopt new keys.

## Standard

SDK calls Sidecar's Spring HTTPS listener; Sidecar calls the app's Spring HTTPS
callback listener. Both callers verify server certificate chains and hostnames.
No client certificates or mandatory Caddy. Use standard Spring Boot/server/client
TLS configuration, with operator-supplied material.

## Hardened

```text
App + SDK -> local HTTP -> App Caddy
          -> mTLS -> Sidecar Caddy -> local HTTP -> Sidecar

Sidecar -> local HTTP -> Sidecar Caddy
        -> mTLS -> App Caddy -> local HTTP -> app callback
```

Caddy authenticates transport peers using deliberately group-scoped certificate
trust. The SDK callback chain independently authenticates JWTs.

The reference Compose recipe gives each app a protected network namespace shared
with its own Caddy, and Sidecar another shared with its Caddy. Backend and outbound
proxy HTTP listeners bind to loopback. Only intended Caddy TLS listeners are
reachable externally. Outbound proxy destinations are fixed; do not create an open
forward proxy. Browser login ingress uses a separate listener from mTLS callbacks.
No-callback deployments omit callback routes and keep invocation.

Operators enforce equivalent boundaries in their own deployment. Loomspan neither
controls firewalls nor discovers/proves the network at runtime. Do not add isolation
between app and its own proxy or against deliberate impersonation by trusted apps.

## Operator instructions and test fixtures

For a trusted group, deploy one shared signing keystore, issuer, callback audience,
user directory and role vocabulary. Coordinate JWT key rotation using the
[signing/trust contract](2026-10-15-sdk-v1-signing-trust-reference.md).
Provided private signing keys load at startup only: restart affected applications
after replacing them. Public JWKS trust reloads independently; no private-key
hot reload or uninterrupted rotation is promised.
Configure callback paths under the SDK prefix and method/business authorization.

DevOps owns TLS certificate issuance, storage, delivery, renewal, replacement, CA
rotation and revocation. Use [Spring Boot SSL configuration](https://docs.spring.io/spring-boot/reference/features/ssl.html)
and [Caddy TLS configuration](https://caddyserver.com/docs/caddyfile/directives/tls).
No Loomspan certificate replacement procedure or lifecycle suite is required.

Tests generate temporary certificates per run: valid, wrong-CA, wrong-peer and
expired material as needed for transport rejection. Do not commit certificates or
run a certificate authority service. Verify valid traffic in both directions,
relevant invalid-peer rejection and inability to bypass Caddy through backend or
outbound HTTP listeners. No renewal, rollover, revocation propagation, live-reload
or timed connection-cutoff tests.

Build these three recipes in `C:/opendev/code/loomspan-sidecar-test-suite` during
Phase 1, with deterministic model fixtures and no provider calls.
Native mTLS/custom mesh/mixed transports require no additional composed tests or
new SDK adapters. No login-provider deployment is needed; reference apps can use
form login.
