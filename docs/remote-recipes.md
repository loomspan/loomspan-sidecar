# Standard HTTPS and Hardened Caddy mTLS

These are representative remote deployments. Invocation (SDK to Sidecar) and
callback (Sidecar to application) transports are configured independently.
Mixed and other compositions remain permitted and untested. Loomspan has no
transport-mode selector or runtime network verifier.

DevOps owns certificate issuance, storage, delivery, renewal, replacement, CA
rotation and revocation. Supply certificates and keys using standard
[Spring Boot server SSL](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.configure-ssl),
[Boot SSL bundles](https://docs.spring.io/spring-boot/reference/features/ssl.html)
and [Caddy TLS](https://caddyserver.com/docs/caddyfile/directives/tls).
No Loomspan certificate watcher, renewal service, reload controller or timed
connection retirement is provided. Plan certificate replacement with the
deployment's own restart and connection policies; these recipes do not promise
seamless rollover or revocation propagation on established connections.

Plaintext HTTP never crosses hosts in either recipe. JWT authentication,
exact authority names and application method/record authorization still apply
after transport authentication. mTLS authenticates group participation; it does
not bind bearer JWTs to a certificate or replace user authorization. Applications
sharing a Sidecar must trust each other in every way.

## Signing and trust delivery

Both examples use the SDK's provided signer and Sidecar file trust. Deliver the
private signing key only to the application, provision its writable public-export
directory, and deliver the SDK's exported `/public/jwks.json` to the exact
Sidecar file path. Sidecar gets only public verification material. The example
uses unencrypted PKCS#8 RSA PEM; use protected secret delivery or the documented
SDK keystore/password-reference facilities. Missing/invalid provided material
fails startup and never generates replacement keys.
Remove colocated signing settings when selecting provided mode; the modes are
mutually exclusive.

The JWKS file is authoritative in both file and database application
ConfigurationMode. Configure Sidecar trust as a process setting and configure
REST targets, routes, skill roles and approved passthrough recipients through
the selected application configuration source. The fragments below are merged
into complete application configuration; supply your skill/model/application
values, issuance role allowlist and security rules. Models and other external
services need their own independently secure transport configuration.

## Standard: direct Spring HTTPS

```text
App + SDK -- verified HTTPS --> Sidecar Spring listener
Sidecar  -- verified HTTPS --> App Spring callback listener
```

Merge [app.yaml](../examples/remote/standard/app.yaml) and
[sidecar.yaml](../examples/remote/standard/sidecar.yaml) into their respective
application settings. Configure the callback target from
[rest-routes.yaml](../examples/remote/standard/rest-routes.yaml).
Each Spring server listens on 8443 with its supplied certificate/private key.
The SDK selects the `sidecar` Boot trust bundle; the Sidecar REST target selects
the `app` bundle. Use DNS names covered by server certificate SANs. Both clients
verify the certificate chain and hostname. There are no client certificates or
Caddy proxies in this recipe.

Configure readiness probes for the HTTPS listener and its trust root. The image's
default localhost HTTP healthcheck must be overridden for this listener change;
the composed harness uses its own verified HTTPS readiness gate.

Replace example DNS names and certificate paths consistently. The sample uses
one group CA file for convenience; invocation and callback may use independent
trust roots. Bind/listen and firewall according to your deployment, and expose
only intended paths. A public browser ingress must use verified HTTPS too.

## Hardened: paired protected namespaces

```text
App + SDK -> local HTTP -> App Caddy -> mTLS -> Sidecar Caddy -> local HTTP -> Sidecar
Sidecar -> local HTTP -> Sidecar Caddy -> mTLS -> App Caddy -> local HTTP -> app callback
Browser -> HTTPS :8444 -> App Caddy -> local HTTP -> app form login
```

Use [app.yaml](../examples/remote/hardened/app.yaml),
[sidecar.yaml](../examples/remote/hardened/sidecar.yaml),
[rest-routes.yaml](../examples/remote/hardened/rest-routes.yaml),
[Caddyfile.app](../examples/remote/hardened/Caddyfile.app) and
[Caddyfile.sidecar](../examples/remote/hardened/Caddyfile.sidecar).
These supplied configurations are exercised with `caddy:2.10.2`. Review
[reverse proxy transport options](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)
when adapting them to your own deployment.

The app and its Caddy must share one protected network namespace, and Sidecar
and its Caddy another. For Docker Compose this means the gateway owns the
network and the backend uses `network_mode: service:<gateway>`. Configuration
fragments alone do not establish that boundary. Backends bind loopback 8081/8080
and each outbound proxy binds loopback 9080. Do not publish these HTTP ports,
place backends in separate namespaces, or route plaintext between hosts.
Only intended Caddy TLS listeners may be externally reachable.

Each outbound listener has one literal upstream and a fixed TLS server name;
it is not a forward proxy. The app gateway supplies its group client certificate
when calling Sidecar; the Sidecar gateway supplies its own when calling the app.
Receivers use `require_and_verify` against the scoped group CA. Client certificates
need clientAuth EKU; server certificates need serverAuth EKU and the expected DNS
SAN. Caddy verifies client chain/group membership, without a client SAN allowlist.
Scope the group CA and private key delivery deliberately; possession of a valid
group client identity grants transport participation in the group.

The mTLS app listener on 8443 exposes only `/loomspan/callbacks/*`. Browser login
uses the separate server-TLS listener on 8444, which rejects callback paths.
The Sidecar mTLS ingress allows execution/readiness paths. Caddy admin and
automatic HTTPS are disabled because operators deliver the material.

The Java clients disable automatic retries and redirects. The examples specify
`lb_try_duration 0` and one upstream. Keep this policy when adding infrastructure:
never replay a submission or callback after a connection failure. An accepted
operation can have effects even when its response is lost. A proxy may return
HTTP 502; a direct SDK transport loss may report acceptance unknown. Neither is
permission to resubmit. A callback can have one effect while execution reports
failure; recovery needs explicit application policy.

## Evidence boundary

The external SDK lane runs fresh temporary certificate/signing fixtures for
these recipes, actual form-login SDK invocation and authenticated callbacks,
invalid-peer cases, accepted-transport JWT rejection, namespace isolation and
post-effect response loss. See [verification ownership](verification.md) and
the neighboring suite's `docs/sdk-lane.md` for exact reports and limitations.
Certificates are never committed, and no CA service runs. Certificate renewal,
rollover, revocation propagation and live reload are outside this evidence.
