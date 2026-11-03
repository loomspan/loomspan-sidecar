# Standard HTTPS and Hardened Caddy mTLS

HTTP operations live in skill YAML; shared targets live in complete execution
YAML. Load [HTTP configuration](http-executor.md) through file-mode Boot Config
Data or the management editor/API in database mode. Validation/readiness do not
certify HTTP operational validity; HTTP-specific checks run at invocation.

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
HTTP targets, operation mappings, skill roles and approved passthrough recipients through
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
[execution.yaml](../examples/remote/standard/execution.yaml).
Each Spring server listens on 8443 with its supplied certificate/private key.
Provision the application CA in the JVM trust store used by Sidecar (for example
with standard `javax.net.ssl.trustStore` process settings); the executor does not
select a Boot SSL bundle. The SDK selects its `sidecar` Boot trust bundle.
Outbound HTTP executor HTTPS uses
standard JVM trust, without a target SSL bundle. Use DNS names covered by SANs. Both clients
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
[execution.yaml](../examples/remote/hardened/execution.yaml),
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

## Trusted group and independent JWT rotation

Use the [group and rotation procedure](operations.md#fully-trusted-application-groups)
with either remote recipe. The representative composed group uses Standard:
two separate app JVMs on HTTPS 8443 and 8445 share one provided signer, issuer
`loomspan-app`, callback audience, user directory and exact role vocabulary;
Sidecar callbacks target the first app. Each app gets its own public export, for
example `/public/app-a.json` and `/public/app-b.json`. The shared Sidecar reads
an independently managed `/trust/sidecar.json`, and each receiver configures
`loomspan-sdk.callbacks.overlap-jwks: /trust/callback.json`. These illustrative
paths are deployment choices, not new configuration keys.

Deliver private signing material only to signing apps. Mount public trust
directories read-only in receivers and grant the operator write access for
atomic replacement. Never point receiver overlap at an app's single-key export:
a restart would overwrite the intended overlap. Add the new public key to each
receiver separately, verify acceptance, replace provided private material and
restart both apps, then retire the old public key at each receiver separately.
The callback receiver must restart with the replacement signer before its old
startup own key can be retired. Polling is independently one second per receiver;
there is no cross-receiver notification or database/management trust path.

Apps sharing a Sidecar trust each other in every way; use separate Sidecars for
isolation. Shared group signing preserves user/record authorization, not app
isolation. Keep the same issuer/subject semantics when fresh credentials poll
old executions. Trust changes do not cancel already admitted Framework work.

## Outbound-only adaptations

Use only model skills and empty HTTP targets. Keep provided signing and
public JWKS delivery, but omit callback controllers and the app callback listener.
A Spring SDK caller may run with `spring.main.web-application-type=none`; it still
uses public SDK discovery/submission/result polling and trusted authentication.

- **Local:** retain shared loopback networking and the writable app/read-only
  Sidecar colocated public-key directory. Omit the application callback listener
  and callback routes.
- **Standard:** retain `loomspan-sdk.base-url: https://sidecar:8443`, its `sidecar`
  SSL bundle and the Sidecar HTTPS listener. Omit app inbound server settings
  when no browser/API is needed, its server certificate delivery and the Sidecar
  HTTP target settings when unused.
- **Hardened:** retain only the global block and `http://:9080` outbound block in
  `Caddyfile.app`; keep its loopback bind, fixed Sidecar upstream and app client
  identity. Retain Sidecar's `https://:8443` mTLS execution/readiness ingress and
  loopback backend. Remove Sidecar's `http://:9080` callback egress block and app
  `https://:8443` callback ingress. A non-web app also omits `https://:8444` browser
  ingress and its loopback 8081 backend. Keep the two protected namespaces and
  never expose outbound/backend HTTP ports. Omit unused callback server/client
  certificate delivery.

Invocation still requires execution JWT validation and the recipe's outbound
transport authentication. Omission of callback infrastructure does not disable
those checks. These adaptations require no database or development-data reset.

## Evidence boundary

The external SDK lane runs fresh temporary certificate/signing fixtures for
these recipes, actual form-login SDK invocation and authenticated callbacks,
invalid-peer cases, accepted-transport JWT rejection, namespace isolation and
post-effect response loss. See [verification ownership](verification.md) and
the neighboring suite's `docs/sdk-lane.md` for exact reports and limitations.
Certificates are never committed, and no CA service runs. Certificate renewal,
rollover, revocation propagation and live reload are outside this evidence.
