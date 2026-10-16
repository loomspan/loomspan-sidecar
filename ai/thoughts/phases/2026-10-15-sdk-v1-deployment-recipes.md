# SDK v1 planned deployment and execution recipes

**Development policy:** Replace superseded development contracts directly; no shims,
legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No data reset is required.

Everything here is **PLANNED, NOT RUN**. Commands define future runner interfaces;
the assets do not exist yet. This documentation ticket ran no container, cloud or
provider. The [API reference](2026-10-15-sdk-v1-api-reference.md),
[identity reference](2026-10-15-sdk-v1-identity-callback-reference.md),
[trust reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md) and
[acceptance matrix](2026-10-15-sdk-v1-acceptance-matrix.md) are normative companions.

## Pinned local prerequisites

| Dependency | Selected baseline | Purpose / qualification |
| --- | --- | --- |
| JDK | Eclipse Temurin 21.0.8+9, release 21 | SDK, Sidecar and suite Java application; resolve platform image digest in implementation evidence |
| Spring Boot | 4.1.0 (managed MVC/Security dependencies) | Optional servlet integration; no independently overridden Spring versions |
| Maven | 3.9.11 | Independent SDK/root builds and clean consumers |
| Docker Engine / CLI | 28.5.1 | Linux containers; Docker Desktop is optional hosting, not a separate promised runtime |
| Compose plugin | 2.40.2 | Compose v2 CLI, not deprecated docker-compose v1 |
| Caddy | 2.10.2 | Roadmap baseline retained unchanged; official standard distribution, no custom authentication plugin |
| Smallstep step-ca | 0.28.4 | Local TLS CA and automated enrollment/renewal |
| Smallstep step CLI | 0.28.7 | Bootstrap/enrollment sidecar, never a customer manual command requirement |
| Keycloak | 26.4.0 | Exchange standard V2 same-realm profile from identity reference |
| Python | CPython 3.12.10 | New external-suite runner/pytest harness, independent of existing environments |

These are selected reproducibility versions, not a latest-version or vulnerability
certification claim. Resolve exact image digests per architecture, package lockfiles
and dependencies before recording evidence; mutable tags alone cannot identify a run.
Host needs Git, PowerShell 7, Linux Docker with network-namespace sharing and enforced
nftables rules (rootful daemon), at least 8 GiB Docker memory and 10 GiB scratch disk.
macOS/Windows may host that Linux VM; no native Windows-container topology is claimed.
Acquiring Maven dependencies/images/Python wheels is setup, not an offline-installation
promise. After setup routine tests have no hosted/paid dependency. No service starts
with live provider keys. Existing suite lanes retain their own locked prerequisites.

## Recipe input contract and fixture assets

Planned suite root: `C:/opendev/code/loomspan-sidecar-test-suite`.
Suite unit `SDK v1 deterministic harness and agreement` owns
`scripts/sdk_v1.py`, `sdk-v1/compose/compose.yaml`, overlays
`isolated.yaml`, `group.yaml`, `transport-{http,https,mtls-proxy,mtls-native}.yaml`,
`callback-{http,https,mtls,none}.yaml`, `sdk-v1/config/`, `sdk-v1/fixtures/` and
`evidence/sdk-v1/`. These are intended paths, not existing executable evidence.

Runner accepts explicit `--topology isolated|group`,
`--strategy relay|exchange|spring-issued|custom`,
`--invocation http|https|mtls-proxy|mtls-native`,
`--callback http|https|mtls|none`, `--jwks upload|separate|application`,
`--authority file|database`, `--lane beta8|suite-snapshot`, `--run-id` and
`--evidence-dir`. Unknown/conflicting settings fail before starting services.
Default authority is database and default JWKS is upload; file authority and URL
variants are explicit required scenarios. Group requires mTLS invocation in primary
group recipes; independent callback transport may be protected HTTP or HTTPS in mixed
cases. `--callback none` removes callback listener/certificates and route entirely.
`--jwks application` separately requires the application's existing servlet server;
upload/separate allows a nonweb application. No SDK callback server is introduced.

Fixtures include local deterministic model JSON/replay, Alice/Bob accounts and roles,
pair-collision issuers, RFC-invalid JWT vectors, independent callback validator keysets,
guarded JWT signer volumes, real local TLS PKI, controllable time/protocol/response-loss
servers, attempt counters and the API reference's transactional business-operation-ledger.
The real Keycloak flow is mandatory for Exchange; a protocol fixture only adds faults.
Relay obtains the customer's local fixture login access token, never an ID token.
Spring-issued/Custom use the shared SDK signer and trusted identity hooks; they do
not start Keycloak or another login service in default isolated bootstrap. Browser
login, URL/method/record ownership and both callback-chain arrangements are retained.

## Isolated topology and enforced local boundaries

Each deployment ID owns a dedicated Linux network namespace anchor `boundary-D`.
Application, Sidecar and elected local proxies share only that deployment namespace
via `network_mode: service:boundary-D`. Every plaintext service binds loopback;
only explicit TLS ingress listeners bind the namespace network interface. Shared
namespace loopback is the documented privileged trust boundary: it contains only
the collaborating application/Sidecar and its owned proxies. Untrusted containers
cannot join it. Host/root/Docker administrators are trusted and can inspect material;
unpublished ports/co-location alone are not the protection. Namespace-local nftables
owner/cgroup rules additionally restrict Sidecar's proxy backend to Sidecar Caddy
and the management proxy, and callback proxy backends to callback Caddy only.
Application UID cannot call Sidecar's plaintext backend on either mTLS path, and
Sidecar UID cannot call a proxy-protected callback backend directly. Only an explicitly
selected protected-HTTP path permits its named caller UID; direct HTTPS has no
plaintext execution backend. Test these rules from every resident UID as well as
from outside the namespace. A management proxy UID's execution routing is denied
by its closed route configuration; its configuration/control interface is protected.

For HTTP/HTTP, SDK→Sidecar `127.0.0.1:8080` and Sidecar→application
`127.0.0.1:8081` stay inside that boundary; no proxy is mandatory. For HTTPS direct,
Sidecar's standard Boot TLS listener `sidecar-D.test:8443` and application servlet
TLS `app-D.test:9443` verify chains and exact DNS SAN. SDK connects directly without
client Caddy; Sidecar calls application directly using a standard SSL bundle. Bind
only elected TLS listeners externally; never expose corresponding HTTP backends.

| Service / port | Owner / ingress | Volume access |
| --- | --- | --- |
| `boundary-D` | Deployment network controller; no application listener | Public peer policy, no private keys |
| `sidecar-D` / loopback 8080 | Sidecar HTTP backend in proxy mTLS path, or protected HTTP; not host-published | Sidecar database/trust/tombstones; public app JWKS only; Sidecar-owned callback TLS key if native egress |
| `app-D` / loopback 8081 | Existing callback server for HTTP/proxy callback only; absent for no-callback | Application signer private store only for Spring/Custom; independent callback public trust |
| `sidecar-caddy-D` / 8443 | Required mTLS admission proxy on both mTLS client paths | Its server key/chain, public client CA/peer policy; no application signing key |
| `client-caddy-D` / loopback 9080 | Proxy invocation path only; fixed Sidecar destination | Its distinct client TLS key, public Sidecar CA; no JWT private signer |
| `callback-caddy-D` / 9443 | Independently elected HTTPS/mTLS callback ingress | App server key/chain, Sidecar-only callback peer policy for mTLS |
| `management-caddy-D` / host 127.0.0.1:9444 | Browser/management HTTPS only; exact /console, /api and static management routes | Separate management server key; no execution client identity |
| `jwks-D` / 9445 | Optional separate public-key host; protected retriever route | Read-only public export, no private stores |
| `step-ca-D` / 9000 | Internal enrollment network, only scoped enroller identities | CA private state and issuer password only here; never mounted into app/Sidecar |
| `keycloak-D` / 9446 | Relay/Exchange local provider on separately verified HTTPS ingress; internal HTTP only behind its owned TLS ingress, never published | Realm/client test state and exchange secret only to named owners; no application signer |
| `bootstrap-D`, `renewer-D`, `policy-controller-D` | Init/renewal/control plane; not public business ingress | Least-privilege per-recipient material/control mounts; signer init runs as application owner |

Exact management route list is generated from Sidecar's declared management
controllers; deny everything else, including `/v1`, encoded/normalized variants,
alternate host routing, websocket/CONNECT and catch-all forwarding. Test this deny
list against actual container routes; failure blocks the recipe. Management requests
still require Sidecar session/CSRF or proper named management token; ingress provides
no privilege. Health uses a separately protected loopback probe, never a backend
port exposure. Trust administration remains interactive and file mode read-only.

## mTLS paths and outbound identity protection

| Path | Invocation hops and guarantee | Callback selected independently |
| --- | --- | --- |
| `mtls-proxy` | SDK→client Caddy loopback HTTP inside boundary; client Caddy→Sidecar Caddy mTLS with exact member identity; Sidecar Caddy→Sidecar loopback HTTP | Sidecar→callback Caddy mTLS→app loopback HTTP for primary; independent native HTTPS or protected HTTP for mixed |
| `mtls-native` | SDK-owned TLS pool→Sidecar Caddy mTLS with exact member identity; Sidecar Caddy→Sidecar loopback HTTP; no client Caddy exists | Same independently selected callback choices; callback listener omitted for none |

Outbound-only client Caddy listens only on loopback 9080, accepts only the application's
Linux uid/cgroup through namespace-local nftables owner rules, strips proxy control
headers, fixes upstream scheme/host/port/base path and rejects CONNECT/arbitrary
absolute URLs. Sidecar and other local processes cannot borrow this identity even
though they share a namespace. Distinct service UIDs, dropped capabilities,
read-only filesystems and only the network controller with NET_ADMIN enforce this;
deny root/elevated workloads access to the application namespace by deployment policy.
Use firewall acceptance/rejection probes from Sidecar and a sibling container, not
assumptions about unpublished ports. Outbound identities are not exposed on group
networks. If platform owner filtering is unavailable, stop recipe setup; do not open
the listener. Equivalent customer proxies must document an OS-enforced exclusive
caller boundary. Native SDK path mounts its client key only into the application.

The reference callback egress is Sidecar native managed REST TLS pools, not a shared
outbound proxy. Their private key is Sidecar-owned. Phase 3 implements complete
material generation loading and pool retirement, including callbacks. An optional
customer egress proxy must obey the same exclusive-caller/fixed-target/no-replay
and connection-cutoff requirements and needs its own attributable evidence.

For all strategies, peer mTLS supplements user JWT admission. Neither certificate
nor management credential replaces execution JWT purpose/audience/roles. Application
callback validates the unchanged captured JWT independently and applies existing
URL/method/record rules. Publish-approved fixed recipients only; redirects/cookies/
automatic callback retry disabled. An approved receiver can reuse a still-valid
credential where user/audience/network policy permits; tokens are not single-use.
Keep authentication/private-key/context canaries out of infrastructure payloads/logs;
do not sanitize deliberate business data or supported public diagnostics.

## Trusted-group topology and certificate policy

Group overlay creates distinct `boundary-A`, `boundary-B` and `boundary-sidecar`
namespaces on a dedicated group bridge. No plaintext loopback backend joins another
namespace or is published. Sidecar Caddy remains mandatory for both client paths.
Each application has a distinct member ID/client certificate; the Sidecar callback
client has its own member ID. An attacker namespace on the bridge proves direct
backend and local proxy denial. External application callback plaintext is forbidden;
the focused protected-HTTP callback uses a separate explicitly approved application
callback worker within Sidecar's protected namespace, not HTTP across the bridge.
This protected worker's business authorization remains independent. Ordinary remote
callbacks use HTTPS/mTLS. The group cannot claim isolation between mutually untrusted
approved recipients of a reusable credential.

Issue TLS leafs with exact DNS SANs `sidecar-D.test`, `app-D.test` (serverAuth) and
`member-app-A.group.test`, `member-app-B.group.test`, `member-sidecar.group.test`
(clientAuth); no wildcard identity matching. Separate server/client certificates
by role. Check chain to configured CA, validity, exact expected SAN, EKU and key
match. A chain under a broad customer CA does not grant membership. Select Caddy
2.10.2's built-in `tls.client_auth.verifier.leaf` with `tls.leaf_cert_loader.file`,
in addition to `require_and_verify` against configured client CA roots. The loader's
`files` list contains only the public leaf certificates of explicitly approved peers;
the verifier compares the presented leaf certificate exactly. The protected policy
controller validates each candidate's exact single member DNS SAN, explicit
clientAuth EKU, chain, validity and approved member record before producing this
allowlist. Thus a newly issued same-CA or same-SAN certificate is not admitted until
its complete leaf is approved; Caddy does not need an unprovided SAN matcher plugin.
Callback allowlists contain only Sidecar's approved client leafs; the deliberate
general-group variant lists its broader approved peer set explicitly.

The tagged [leaf verifier](https://github.com/caddyserver/caddy/blob/v2.10.2/modules/caddytls/connpolicy.go#L888)
and [file loader](https://github.com/caddyserver/caddy/blob/v2.10.2/modules/caddytls/leaffileloader.go)
establish available standard modules, not performed handshake evidence. Renewal
stages the validated new public leaf at every receiving allowlist and obtains policy
acknowledgements before the sender switches its private material generation. Remove
old leafs under the routine drain gate; emergency removal deletes every approved
leaf for that member and uses the existing 5s whole-generation termination rule.
No forwarded certificate header is authoritative. Sidecar Java never interprets
peer certificates. Implementation must prove actual standard-module configuration,
wrong SAN/EKU/chain/leaf rejection, staged renewal and cutoff with real handshakes;
a module-load or enforcement failure blocks the recipe without a weaker fallback.

Sidecar ingress admits only configured application members. Callback primary ingress
admits only Sidecar's specific client identity, rejecting another valid group member
even with the same JWT. A separately tested general-group callback policy deliberately
admits reusable-member credentials and documents that residual risk. HTTPS callback
only authenticates the server; it cannot claim Sidecar workload authentication.
All TLS hops verify chain and hostname; no insecure mode/downgrade/fallback. Management
ingress exposes no alternate execution route. Network policy denies nonapproved
destinations and metadata/link-local addresses; approved membership never creates
JWT issuer trust. Finite issuer/source/role entries are independently administered.

## Bootstrap, renewal and recovery

Default isolated Spring-issued/Custom `up` runs all provisioning automatically:
guarded lock; persist NEW deployment identity; initialize application-owned signing
store; start private Smallstep CA; issue separate TLS role leafs and public roots;
export only public JWT snapshot; install Sidecar initial upload and independently
install callback trust; verify acknowledgements/digests; mark ESTABLISHED and ready.
Neither customer key exchange nor manual certificate CLI nor a new login service is
required. Relay/Exchange preserve provider JWT ownership, never generate local JWT
signers. Enrollment tokens are scoped, short-lived, private and consumed by owned
bootstrap/renewers, never printed or included in evidence bundles. File/database
authority obeys the trust reference's startup-only seed and authoritative guard;
later trust changes use interactive administration and cannot be replayed by bootstrap.

Named volumes separate app signer, app TLS, Sidecar TLS, CA private state, database,
public snapshots and protected deployment ledger/tombstones. Mount only recipient
material, read-only to consumers, private modes/UIDs; public host export cannot alias
signer state. Never mount Docker socket into workload/control containers. Controller
executes through a narrow host-owned process with only target-generation stop/start
permissions, not an open Caddy admin/Docker API reachable from workloads.

ESTABLISHED restarts validate identity, digest and authority ledger; missing/corrupt
private state fails closed and requires explicit protected-backup recovery with
current withdrawal reconciliation. Missing marker plus nonempty authority is not
NEW. Concurrent bootstrap losers stop. Partial NEW stage resumes only after validating
its recorded outputs; never overwrite established identity or restore withdrawn trust.
Customer-supplied signer/certificates/trust replace generated material explicitly;
invalid or incomplete delivery fails without generating/permissive fallback.

Renew at two-thirds validity, scheduler interval ≤60s. Deliver atomic complete
generation directory with manifest, key, leaf/chain, roots and peer policy. Native
SDK and Sidecar pools and proxy controllers validate/load within 5s; immediately
close idle old pools, gate new requests to new generation, drain active ≤30s. At
leaf notAfter close even active old connections and fail readiness without grace.
Invalid/lower/partial material leaves the last still-valid generation degraded,
never mixes files. Member removal gates new requests, invalidates session tickets
and closes affected old connections ≤5s after local commit; terminate whole proxy
generation when selective closure is unavailable. Missing acknowledgement stops that
route. Measure dissemination delay separately; no instantaneous global revocation.
Signer rollover/last-issuance+360s retirement, uploads/URLs, 65s normal URL removal
and 300s cache outage bounds retain independent callback receiver commits. No
reconnect/recovery reruns submission/callback or rolls back committed effects.

## Exact future local commands and evidence tiers

Every command in this section is **PLANNED, NOT RUN**; the harness/SDK/assets must
first be implemented by named matrix units. Run from the specified working directory.
`setup` only acquires/builds local artifacts after agreement update, verifies all
pinned versions/digests and writes a manifest; it must not modify existing suite lanes.
`run` owns isolated per-run namespaces/volumes, no implicit deletion of prior data.
`down` stops only named run resources; no `down -v`/global prune/recovery reset.

```powershell
# C:/opendev/code/loomspan-sidecar: independent focused SDK tier
mvn -f sdks/java/pom.xml verify
# C:/opendev/code/loomspan-sidecar: focused Sidecar/public-boundary tier
mvn verify
# C:/opendev/code/loomspan-sidecar: P4 clean-consumer packaging in a dedicated local repository
mvn -f sdks/java/pom.xml -Dmaven.repo.local=target/sdk-v1-consumer-repository clean install
mvn -f sdks/java/consumers/plain/pom.xml -Dmaven.repo.local=target/sdk-v1-consumer-repository verify
mvn -f sdks/java/consumers/boot/pom.xml -Dmaven.repo.local=target/sdk-v1-consumer-repository verify
# C:/opendev/code/loomspan-sidecar-test-suite: planned dedicated SDK v1 environment
py -3.12 -m venv .venv-sdk-v1
.venv-sdk-v1/Scripts/python.exe -m pip install -r sdk-v1/requirements.lock
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py setup --lane beta8 --manifest sdk-v1/config/beta8.json
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py run --tier focused --scenario API-01 --run-id api-local --evidence-dir evidence/sdk-v1/api-local
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py run --tier composition --topology isolated --strategy spring-issued --invocation mtls-native --callback mtls --jwks upload --authority database --lane beta8 --run-id native-local --evidence-dir evidence/sdk-v1/native-local
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py run --tier composition --topology group --strategy exchange --invocation mtls-proxy --callback http --jwks separate --authority database --lane beta8 --run-id mixed-local --evidence-dir evidence/sdk-v1/mixed-local
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py run --tier composition --topology isolated --strategy custom --invocation mtls-proxy --callback none --jwks upload --authority file --lane beta8 --run-id outbound-local --evidence-dir evidence/sdk-v1/outbound-local
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py run --tier lifecycle --matrix sdk-v1/config/required-matrix.json --lane beta8 --run-id lifecycle-local --evidence-dir evidence/sdk-v1/lifecycle-local
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py run --tier release --matrix sdk-v1/config/required-matrix.json --lane beta8 --run-id release-local --evidence-dir evidence/sdk-v1/release-local
.venv-sdk-v1/Scripts/python.exe scripts/sdk_v1.py down --run-id native-local
```

Runner composes only the fixed overlays selected by those flags, executes `docker
compose --project-name sdkv1-<run-id> -f sdk-v1/compose/compose.yaml` with those overlays,
`up --build --wait`, probes readiness, runs matching pytest cases, records result,
then stops that project's containers. It never executes free-form commands from input.
Release tier expands every matrix row/variant, requires completed local prerequisites
and clean-consumer packaging; it does not invoke cloud/provider manual procedures.
Attempt observation records SDK POST send count, client-Caddy ingress/upstream count,
Sidecar ingress/admission/Framework handoff, callback client sends, callback ingress/
handler count, ledger commits/receipts and GET read attempts separately. Inject
response loss at each real hop after dispatch/commit; no-retry configuration alone
is insufficient. Zero counts require a proven pre-send point. Counters contain no
tokens/business payloads; correlate with synthetic run IDs and safe execution IDs.

Each evidence JSON has scenario/variant/status (PLANNED/PASS/FAIL/NOT_RUN), UTC start/
end and measured monotonic durations, exact command/cwd/config digest, all repository
revisions/dirty-diff digests, SDK/Sidecar/Framework artifact versions, JDK/Boot/Maven/
Python/OS/engine/Compose/provider/proxy/CA versions and image digests, topology,
strategy/path/authority/JWKS/callback arrangement, public fingerprints, per-hop
guarantees, safe attempt/effect counters, expected/actual result and residual limits.
Never include private stores, raw credentials or secret-bearing environment dumps.

## External material and provider manual procedures

All rows below are **UNVERIFIED / NOT RUN, separate authorization required**.
Phase 3 unit `SDK v1 material delivery and external recipe documentation` owns local
material adapters and `sdk-v1/manual/<name>.md` in the suite; Phase 4 reconciles claims.
Run only in an authorized disposable tenant/account, synthetic user/data, documented
cost boundary and cleanup plan. Record exact provider/API/config versions, region,
material revision, commands, safe outputs, renewal timings and revocation observations.

| Recipe | Material/identity selection | Explicit manual verification procedure |
| --- | --- | --- |
| AWS | Secrets Manager delivery of exportable PKCS12/PEM to protected files; optional ACM Private CA issuance via deployment agent | Authorize test account/region; provision distinct client/server certificates with exact SAN/EKU; deliver atomic generation; invoke both paths and callback; rotate/renew/revoke and measure reload/closure; test IAM denial/delivery outage/expiry; remove test resources. ACM non-exportable material is not assumed usable |
| Azure | Key Vault exportable certificate secret delivered by deployment identity | Authorize test subscription/vault; least-privilege fetch to protected files; exercise direct HTTPS/both mTLS/callback; new version/renewal, access withdrawal and outage/expiry; verify no secret in logs and cleanup. Managed identity is delivery authority, never user identity |
| Google Cloud | Secret Manager versioned exportable keys/chain, optionally Certificate Authority Service issuance | Authorize project/region; issue exact-role leafs, deliver through deployment agent; both mTLS paths/callback; rotate/disable version and verify 5s/expiry gates; denied service account/outage checks; cleanup. Workload identity cannot substitute for user JWT |
| Auth0 | Existing customer's OAuth access JWT for Relay, finite issuer/JWKS/profile and exact workflow audiences | Authorize disposable tenant/application/API; login synthetic Alice, record actual access token purpose/claims privately; configure explicit receiver trust; execute/poll/callback and signed ID-token substitution/expired/wrong-audience negatives; rotate provider key and observe retrieval. Incompatible token cannot be weakened into support; no Auth0 Exchange claim |
| Okta | Existing customer's access JWT for Relay from explicitly selected custom authorization server | Authorize test org/server/client; select access policy/audiences/roles; login synthetic user, verify both receivers and ID/bound/wrong-purpose negatives; key change/outage observations and cleanup. No inferred compatibility from OIDC discovery or local Keycloak |
| Microsoft Entra ID | Existing tenant access JWT for Relay only if selected purpose/audience/lifetime constraints are satisfiable | Authorize test tenant/app registration/API scopes; obtain access token and configure explicit profile, run real admission/callback and ID/role/audience negatives; document incompatibilities without relaxing maximum lifetime/purpose. No token-exchange or opaque-token support implied |

Non-exportable AWS KMS/Azure Key Vault/Google Cloud KMS signing is **not a delivered
v1 integration**. First demonstrate why standard material/keystore/SSL bundle delivery
cannot meet the actual requirement, then explicitly plan public `SigningStateSource`
integration (public snapshot/kid/algorithm/issuance/barrier/failure/replica ownership),
provider signing adapter and security evidence. Missing integration is a blocker for
that recipe, not permission to add cloud SDK code or misrepresent local key tests.
No cloud/provider procedure gates deterministic local release. Unrun checks restrict
compatibility claims; clean local fixtures never prove hosted issuance/delivery/renewal.
Hosted-provider recipes are feasibility/compatibility evaluations against the approved
token contracts, not permission to invent an additional accepted profile at runtime.
If Auth0/Okta/Entra requires a new purpose profile or cannot meet the 300s limit,
record unsupported/incompatible and require explicit public contract planning before
claiming support. Neither the SDK nor a manual procedure may reinterpret an ID token,
strip binding or relax existing validation to make a provider appear compatible.

## Suite agreement and implementation dependencies

Before adding this lane, its harness owner updates external `docs/project-agreement.md`
with SDK v1 naming, deliberate Java-to-Sidecar path, preserved embedded Java/Python/
equipment business behavior and TypeSafe synthetic/accepted replay fixtures. Record
the existing beta.9 lane separately from beta.8 SDK v1; do not rewrite those sources
or claim compatibility across revisions. Current commands in that repository do not
already run this design. Each component unit supplies focused tests/documentation,
then coordinates this suite's real security/fault evidence; Framework tests alone
are insufficient. No external repository was edited by this documentation ticket.
