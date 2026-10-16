# SDK v1 trust, bootstrap and lifecycle reference contract

**Development policy:** Destructively replace superseded development contracts;
no compatibility shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). This documentation
unit requires no development-data reset and authorizes no deployed-data deletion.

Normative output of [PR-8.0.2](../tickets/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md),
extending the [identity/callback reference](2026-10-15-sdk-v1-identity-callback-reference.md).
Every new API, schema, setting and lifecycle mechanism here is **PLANNED**.
The [named scenario matrix](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md)
is future evidence, not performed runtime verification. These decisions supersede
corresponding illustrative names and open choices in the
[roadmap](2026-10-11-java-spring-sdk-roadmap.md), preserving its acceptance obligations.
The final [API reference](2026-10-15-sdk-v1-api-reference.md),
[deployment recipes](2026-10-15-sdk-v1-deployment-recipes.md) and
[consolidated matrix](2026-10-15-sdk-v1-acceptance-matrix.md) select runtime versions,
future commands/topologies and individual evidence owners without weakening these
boundaries. Every runtime scenario remains planned.

## Verification sources and protected state

Both Sidecar admission and SDK callback validators use the finite `IssuerPolicy`
from the preceding reference: exact issuer, approved source reference, RS256,
profile, authorized parties, receiver/workflow audiences and explicit role maps.
No token `iss`, `kid`, `jku`, `x5u` or discovery address creates trust. Never fetch a
token-supplied URL. Phase 1 permits one issuer; Phase 2 extends this same schema
to a finite uniquely keyed issuer set. A workload certificate grants no issuer power.

### Payloads and validation

The proposed management base is `/api/management`; trust routes below use `/trust`.
All trust mutations require
an interactive administrator and database mode. `GET /trust` returns the current
public policy, source diagnostics, revision and withdrawal metadata; no raw token,
private key or credential. Requests reject unknown fields rather than silently drop
protected data. JSON examples below use symbolic generated public modulus values;
they are schemas, not importable test keys.

`PUT /trust/issuers/{id}` creates/replaces one complete entry with `expectedRevision`
(zero for a new trust store) and exactly one source:

```json
{
  "expectedRevision": 12,
  "policy": {
    "issuer": "https://orders.example/issuer",
    "verificationSourceRef": "orders-keys",
    "profile": "application-execution-v1",
    "authorizedParties": [],
    "receiverAudience": "sidecar-api",
    "workflowAudiences": ["sidecar-api", "orders-callback"],
    "roleMappings": [{"claimPath": "roles", "approvedValues": {"order.read": ["ORDER_READ"]}}]
  },
  "source": {
    "id": "orders-keys", "type": "upload", "format": "jwks",
    "content": "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"orders-1\",\"alg\":\"RS256\",\"use\":\"sig\",\"key_ops\":[\"verify\"],\"n\":\"PUBLIC_BASE64URL_MODULUS\",\"e\":\"AQAB\"}]}"
  }
}
```

Upload `format` is `jwks` (object with nonempty `keys` array), `jwk` (one public
JWK) or `pem-spki` (one `BEGIN PUBLIC KEY` RSA SubjectPublicKeyInfo PEM).
PEM requires a sibling nonblank `kid`; JSON keys require their own unique nonblank
`kid` (1–128 printable ASCII characters). Maximum decoded upload/response is 256 KiB,
maximum 32 keys, no duplicate JSON members or duplicate kids. RSA modulus is at least
2048 bits; algorithm is fixed RS256, optional input `alg` must match. Optional `use`
must be `sig`; optional `key_ops` must be exactly `["verify"]`. Output normalizes
these metadata fields. Reject private parameters (`d,p,q,dp,dq,qi,oth`), symmetric
keys/secrets, non-RSA keys, encrypted/private PEM, certificate chains/PKCS#1 PEM,
malformed/empty material, unsupported algorithms and external key references. Only
`kty,kid,n,e,alg,use,key_ops` are accepted JWK fields; no `x5u`/`jku` fetch. Reject the
entire candidate if any key fails. Never log rejected content.

Alternative source on the same policy:

```json
{
  "id": "orders-keys", "type": "jwks-url",
  "url": "https://orders.internal:8443/keys/jwks.json",
  "allowedDestinations": [{"host": "orders.internal", "port": 8443, "cidrs": ["10.44.0.0/24"]}],
  "tlsTrustRef": "ordersJwksTrust"
}
```

`tlsTrustRef` explicitly selects normal system trust or an operator-configured TLS
trust bundle; never trust-all. HTTPS is mandatory except explicit protected-local
HTTP with an operator-owned local-boundary declaration in file/bootstrap authority.
The exception is limited to a fixed loopback/isolated Compose destination and is
visible in inspection; a management token cannot create it. URL has no userinfo,
fragment or query credentials; no cookies, Authorization forwarding or client secret.
Restrict exact scheme/host/port/path; no redirects, proxy environment discovery or
issuer discovery. Resolve every connection, require **all** answers within approved
CIDRs, pin the validated address for that connection, and preserve hostname/SNI
verification. Reject metadata/link-local/multicast/unspecified addresses; private
network addresses require the explicit narrow allowlist. Block DNS rebinding and
address-family bypass. Deployment egress policy must enforce the same destination
boundary. URL response must be a successful JSON JWKS of accepted public keys;
untrusted cache headers never extend local timing. Source change requires a new
validated snapshot; never borrow the previous URL's cache.

### Persistence, publication and withdrawal barriers

Protected trust is separate from ordinary skill/REST/execution authoring. Persist
one `TrustState{schemaVersion:1, deploymentId, revision, issuers, tombstones}` under
the selected authority. Each issuer has stable `id`, the complete `policy`, one
`source`, `sourceRevision`, `enabled`, public snapshot `{keys, sha256, fetchedAt,
expiresAt}`, and last retrieval status. `sourceRevision` changes with source/policy;
trust `revision` advances for every administrative commit. Retrieval replaces the
public snapshot only, under a compare-and-swap of issuer/source revision and current
tombstones. It cannot change policy, re-enable an issuer or erase distrust.

Tombstones contain `{issuerId, issuer, scope:issuer|key, kid?, publicThumbprint?,
withdrawnAt, revision, reason}`. A key withdrawal records its kid **and** SHA-256
public-key thumbprint; block either match, including the same key under a new kid.
Reusing a kid for different material within an issuer is rejected; rotate to a fresh
kid. Tombstones survive issuer deletion, source replacement and restart. Deleting
an issuer means disabled plus issuer tombstone, never forgetting its history.
Ordinary import/rollback/bootstrap/refresh cannot clear them. No timer restores trust.

Interactive `POST /trust/withdraw` takes `{expectedRevision,issuerId,scope,kid?,reason}`;
key scope must identify an existing known key, otherwise reject. Issuer scope blocks
all keys including future URL additions. `POST /trust/restore` takes exact tombstone
revision(s), expected current revision, reason and a freshly validated complete
candidate entry. This deliberately authorizes the specified key/issuer again; no
automatic restoration of other tombstones. Return new revision and safe fingerprint
summary. `DELETE /trust/issuers/{id}` requires `{expectedRevision,reason}` and uses
the issuer-withdrawal transition, retaining the tombstone and last public state.
Revision mismatch is 409; unauthorized token is 403 before mutation;
invalid policy/material is 400; unavailable required source is 503. Successful
mutations return only after durable commit and local validator publication.

Administrative update retrieves/validates outside the commit lock, then rechecks
session/account/credential version, authority, mode, expected revision and source
digest at commit. Serialise commit with admission and queue handoff; durable state
is restart authority. If durable commit succeeds but local activation cannot complete,
close admission/readiness and recover that revision; never serve older trust after
acknowledging success. Every request validates against one immutable policy/key
snapshot and withdrawal generation at its authentication gate. No new request may
cross that gate using pre-commit trust after a completed local withdrawal.

File mode reads a complete operator-owned trust document plus durable withdrawal
ledger from its protected state directory. Every management configuration/trust
mutation is forbidden in file mode, including interactive changes. The operator
stops/restarts with an explicit revised file and ledger to withdraw/restore; older
file revisions fail startup rather than erase tombstones. Database mode owns trust
and ledger in the database, outside ordinary history/rollback. It does not reread
bootstrap files after establishment. Neither mode silently falls back to the other.

### Retrieval clock and outages

Fixed v1 defaults below apply independently in Sidecar and each callback validator;
these bounds are not HTTP cache headers. Implementation may expose stricter limits,
but increasing these maxima needs a contract change. Persist UTC instants and reject
future/invalid timestamps on restart; process age checks use monotonic elapsed time.

| Event/bound | Required behavior |
| --- | --- |
| Fetch | 2s connect, 3s response, 5s whole attempt including DNS; 256 KiB decompressed cap; one attempt, no redirects/retry |
| Normal refresh | Start at least every 60s; one in-flight fetch per source. Valid response becomes visible after revision/tombstone recheck, within the 5s fetch bound |
| Unknown `kid` | At most one coalesced fetch per source per 10s; wait at most 5s, then reject unless validated key exists. Known-key validation need not wait |
| Outage/invalid response | Preserve last valid snapshot, report degraded retrieval; accept known unwithdrawn keys only while age is strictly less than 300s since last successful full validation |
| Age reaches 300s | Reject that URL source's tokens, including previously known kids; liveness may remain but readiness signals unavailable trust |
| Cold start | Fetch required if no valid durable snapshot. No source means not ready; never accept an empty/permissive policy |
| Restart | Load policy/tombstones first; use persisted snapshot only within the same 300s absolute age and source revision, and immediately refresh. Restart never resets age |
| Upload | No network refresh or cache age expiry; administrative revisions and token lifetime govern acceptance. Explicit upload required for changed material |

Unknown-key traffic cannot postpone normal refresh or produce unbounded requests.
304 is allowed only with a previously validated same-revision representation and
counts as full success after validation of stored material; otherwise reject it.
Source retrieval failures never remove tombstones, reset keys or select another source.
URL deletion alone has up to 65s normal detection (60s scheduling plus 5s fetch),
and up to 300s last-success exposure during outage. Use explicit local withdrawal
for emergency enforcement; do not call URL deletion immediate revocation.

## One management API and one administrator

SDK v1 replaces the current multiple viewer/editor/admin users with one administrator
account and its clients. No invitation, secondary human roles or parallel legacy
API survives that replacement. Keep enabled and credential-version state and live
operation authority as real revocation boundaries. Each request intersects live
authority with token preset; enabled administrator ordinarily has all operations.
Disabled or version-invalid credentials grant none. A restricted/read-only operating
state can reduce live authority, never expand token scope.

All paths in this table are under `/api/management`; A means interactive session,
R/E/P mean Read/Edit/Publish tokens. The map is closed: undeclared routes deny tokens.
Authentication/token metadata is deliberately interactive-only for v1 simplicity.

| Operations (method and route) | R | E | P | A |
| --- | --- | --- | --- | --- |
| GET `/configuration/current`, `/configuration/export`, `/configuration/history`, `/configuration/history/{id}`, `/configuration/file-startups`, `/editing`, `/editing/draft`, `/trust` | yes | yes | yes | yes |
| POST `/editing/lease`, `/editing/lease/handoff`, `/editing/lease/renew`, `/editing/lease/release` | no | yes | yes | yes |
| PUT/DELETE `/editing/draft`, `/editing/draft/credentials` | no | yes | yes | yes |
| POST `/editing/draft/reconcile`, `/editing/draft/validate`, `/configuration/import/review`, `/configuration/import/load`, `/configuration/rollback/{id}/review`, `/configuration/rollback/{id}/load` | no | yes | yes | yes |
| POST `/configuration/publish`, including new caller-passthrough destinations | no | no | yes | yes |
| PUT `/trust/issuers/{id}`, DELETE `/trust/issuers/{id}`, POST `/trust/withdraw`, `/trust/restore` | no | no | no | yes |
| GET/POST `/personal-tokens`, DELETE `/personal-tokens/{id}`; all account/password/recovery operations; forced lease takeover | no | no | no | yes |

Read/E/P entries are still subject to live authority and file mode. File mode allows
inspection and account/token security lifecycle, but forbids lease/draft/import/
rollback/publish/trust mutation for everyone. Account/token security state is not
application configuration. Existing authentication/login/recovery routes are separate
from the bearer authoring allowlist; a token cannot use recovery to create a session.

One administrator has one saved ordinary-authoring draft shared for reading by
authorized clients, one active editing lease bound to originating session or token,
and exact draft/runtime-base/trust-base revisions. Same-account explicit handoff
rotates lease generation and requires reread; descriptive client names confer no
authority. Renewals are explicit. Successful publication clears that draft and
releases lease; failure preserves it byte-for-byte. No automatic merge or takeover.

Ordinary drafts need not embed protected trust. Envelopes that include `protectedTrust`
(exports/imports/historical candidates) must supply the current complete canonical
public state and `trustRevision`. Token requests supplying an altered section, unknown
protected field, outdated revision or tombstone fail the **whole** operation, including
review/load/validate/reconcile/publish; identical current sections may proceed.
An omitted section means preserve current trust, but a stale `trustBaseRevision`
still blocks commit until explicit reconciliation against current state. Never silently
strip protected fields, partially save a draft, resurrect a snapshot or apply an
interactive protected edit via ordinary publication. Interactive users also use the
dedicated trust transitions for changes; ordinary rollback never rolls trust back.

Before draft save/load and publication after any wait/model validation/lock, recheck
account enabled/version/authority, token expiry/revocation/preset, lease origin and
generation/expiry, exact candidate revision/base, trust revision and validation proof
under the transition gate. A trust commit invalidates prior validation proofs. A
failed gate changes neither draft, active publication, credentials nor trust. Publish
may approve a new token-passthrough recipient through this same gate without another
browser approval; normal fixed-target validation, egress and peer policy still apply.

`POST /personal-tokens` takes `{name,preset,expiresAt?}`: trimmed 1–80 character name
without control characters, unique among active tokens; preset `Read|Edit|Publish`.
Default expiry is 7 days, positive and no more than 30 days. Return 201 metadata
`{id,name,preset,createdAt,expiresAt,revokedAt}` plus secret **once**, `Cache-Control:
no-store`. List/revoke return metadata only; revocation is durable and idempotent.
Use current high-entropy opaque `lspat_<random-id>.<256-bit-secret>` shape and SHA-256
secret hash with constant-time verification, never plaintext storage, recoverable
encryption or logs. Retain bounded issuance/attempt limits. Reset credentials/recovery
revokes prior tokens and sessions through credential version; re-enable does not revive
them. Expiry/revoke stops new requests and post-wait commits, releases originating
lease, preserves saved draft. It cannot roll back a completed publication.

Browser writes require valid session plus CSRF, including trust and token creation;
CSRF is exempt only for pure management bearer requests. Reject bearer plus management
session cookie (even stale), duplicate Authorization or multiple credentials; never
choose the more powerful credential. Execution JWTs fail management, management tokens
fail execution. AI/automation stores token in protected credential configuration or OS
secret storage, never prompts, skills, URLs, source files or logs. Safe audit records
contain operation, actor/token ID, revision and outcome, never secret/material bodies.

## One SDK signing and public publication owner

Spring-issued and Custom identity resolve `signing-state-ref: applicationSigner`
to exactly one `SigningStateSource`; both use it through the SDK's common issuer.
Relay/Exchange do not initialize application signing state. The default local store
is explicitly configured, not discovered in working directories or temporary homes:

```yaml
loomspan-sdk:
  signing:
    store: /var/lib/loomspan-sdk/signing/state.json
    mode: writer
    deployment-id: orders-deployment
  jwks:
    endpoint:
      enabled: false
      path: /internal/loomspan-keys/jwks.json
      access: public-read
    export:
      path: /exports/loomspan-public-jwks.json
```

`signing` binds the SDK-managed `applicationSigner`; `mode` is `writer|replica`.
`deployment-id` must match established state. `store` has no default path; missing
selection for issuance fails `SigningUnavailable`. Initialization occurs through the
guarded deployment process below, never as a side effect of issue/endpoint/export.
No `initialize-if-missing` runtime escape hatch. A supplied application
`SigningStateSource` bean named `applicationSigner` replaces the SDK-managed owner
entirely; any simultaneous `signing.store/mode/deployment-id` configuration is an
error. The supplied source must provide signing and public snapshot from the same
generation, fail closed and meet publication/withdrawal guarantees. SDK never creates
a backup signer. V1 does not ship cloud/HSM-specific signer implementations.

Managed state schema is `SigningState{schemaVersion:1,deploymentId,revision,issuer,
activeKid,keys,withdrawn}`. Key records hold unique `kid`, public fingerprint,
protected RSA private JWK, `state:staged|active|overlap|retired|withdrawn`, `createdAt`,
`publishedAt?`, `activatedAt?`, `lastIssuedAt?`, `retireAfter?`. RSA generation uses
maintained JCA/Nimbus components, secure randomness, 3072-bit default (minimum 2048).
Directory is owner-only, store/lock/private backups mode 0600 on Unix or equivalent
owner-only Windows ACL, no private material in image/source/env/Sidecar/export volume.
Persist through write/fsync/atomic replace with directory durability; unsuitable
filesystem fails startup. Do not log, serialize into diagnostics or expose private state.

One writer holds an exclusive store lock for its lifetime, including provisioning;
a second writer fails. No shared-write network filesystem or ad-hoc leader election.
Read-only replicas receive complete immutable revisions over operator-controlled
protected distribution, including private material only for authorized issuers.
An activation barrier stops issuance on all replicas, installs/acknowledges a complete
revision, then resumes. A lagging/unreachable replica remains stopped. Emergency
signer withdrawal uses the same stop/barrier before resumption; rolling unsynchronised
writers are unsupported. Drain or invalidate outstanding signing leases before
acknowledging the barrier; no withdrawn signature may be released afterward.
Public endpoint replicas must serve at least the approved
publication revision before activation. No distributed-coordination service is added.

Staged keys are publishable before use; active and overlap keys are publishable;
retired/withdrawn keys never are. `PublicJwksSnapshot` contains immutable canonical
UTF-8 JWKS bytes plus state revision/digest, generated through public-only Nimbus
serialization. Include `kid,kty,alg:RS256,use:sig,key_ops:[verify],n,e`; no private
parameters. Validate every supplied source's output; private/symmetric or inconsistent
material rejects the entire snapshot. Consumers never filter a bad source into success.

Only the signing-state owner generates/stages/activates/retires/withdraws; the exporter
and endpoint are read-only consumers. `publishedAt` means operator acknowledgement of
the required **validator** publication revision, not merely a successful file write or
HTTP availability. For uploads every active validator must acknowledge the import;
for URLs confirm each validator has fetched the new revision before use (do not just
sleep 60s). Operator then explicitly activates the staged key under the replica barrier.
This is coordinated rollover; no unattended rotation claim follows from JWKS refresh.

Missing/corrupt established store, wrong deployment ID/issuer, bad permissions, mixed
revision or unavailable supplied source stops issuance and readiness. Preserve the
evidence; restore a protected consistent backup and reconcile all validator/tombstone
state before resuming. If recovery is impossible, explicitly replace deployment identity
and trust through interactive administration, retiring old credentials. Never reset,
generate alternate keys, select a different store or restore known withdrawn trust.
Any destructive development reset belongs to a later implementation unit documenting
affected local volumes/tokens/trust; it is not authorized by this contract.

## Endpoint and public file export

Exact proposed Java contracts (SDK API, never a Framework SPI):

```java
interface SigningStateSource {
    SigningLease acquireForIssue();
    PublicJwksSnapshot publicationSnapshot();
}
interface SigningLease extends AutoCloseable {
    String kid();
    long revision();
    byte[] signRs256(byte[] signingInput);
    void close();
}
// Lease pins one active generation; no accessor returns private key bytes.
record PublicJwksSnapshot(long revision, String sha256, byte[] jwksUtf8) {}
enum ExportMode { CREATE_IF_ABSENT, REPLACE_EXISTING }
enum ExportOutcome { CREATED, SKIPPED_EXISTING, REPLACED }
record ExportResult(ExportOutcome outcome, Path path, Long exportedRevision) {}
interface PublicJwksExporter {
    ExportResult export(Path output, ExportMode mode);
}
record JwksEndpointSettings(boolean enabled, String path, JwksAccess access) {}
enum JwksAccess { PUBLIC_READ, APPLICATION_POLICY }
```

`SigningLease` is used by `Issuers.executionV1`, which supplies the RS256 signing input;
customer identity adapters never construct JWTs. Alternative signers implement this
narrow signing operation and must check withdrawal/generation at acquisition. Managed
store signing uses the maintained implementation. Snapshot byte arrays are defensive
copies. Spring exposes `PublicJwksExporter` using `applicationSigner`; equivalent
standalone Java is `PublicJwksExporters.from(applicationSigner).export(output,
ExportMode.CREATE_IF_ABSENT)`. No Spring container/server is required for export.

Property mode `jwks.endpoint.*` maps to `JwksEndpointSettings`. Alternative Java
registers that bean with `new JwksEndpointSettings(true,"/internal/loomspan-keys/jwks.json",
JwksAccess.PUBLIC_READ)`; any simultaneous endpoint properties fail binding. Merely
providing a source/exporter bean never enables an endpoint. Exactly one source must
be selected and initialized; there is no independent endpoint-only key generator.
Property export path invokes the exporter once after normal source initialization
without a customer listener/hook. A custom exporter bean replaces the default only
if it implements these same safety/read-only semantics; duplicate definitions fail.

Endpoint defaults disabled; then no route, security chain, listener or port is added.
Enabling requires an existing supported servlet application server; standalone,
nonweb or unsupported reactive configuration fails clearly at startup. Register
GET only at a normalized absolute path (HEAD follows normal read semantics); route
collision, callback-namespace overlap or ambiguous security wiring fails startup.
`public-read` explicitly permits only this public key route; `application-policy`
requires an application security rule permitting the configured validators to read
it. Neither permits callback business requests or changes browser login/CSRF/session
rules. Respond with the current public snapshot, JSON, revision ETag and `no-store`;
unavailable/invalid source returns 503 rather than stale withdrawn keys or new identity.
Endpoint snapshot selection is serialized with signer withdrawal publication.

File export is disabled without an explicit path. Path is absolute; parent must
exist and be trusted/owner-writable, outside the entire signing-store directory and
its backups/lock/temp paths. Reject symlink/reparse-point traversal at every ancestor,
nonregular existing files, hard-linked destinations, store aliases/canonical collisions
and untrusted/shared writable parents. Use directory-relative no-follow operations
and revalidate under the export lock; if platform cannot guarantee the safety contract,
fail with `ExportPathUnsafe`. All writes use a private same-directory temporary file,
public-only serialized snapshot, fsync and atomic publication; never copy/delete target
as a fallback. Concurrent exporters serialize per output; external writers in a trusted
parent are unsupported. The original is intact on failure; clean up owned temp files.

`CREATE_IF_ABSENT` (startup default) validates path safety, then checks existence
**before requesting a snapshot**. Existing safe regular file returns
`SKIPPED_EXISTING` with null exported revision and unchanged bytes/mtime, including
after key change or source outage. It does not assert the existing file is current or
valid trust. Normal issuance/source initialization may independently fail startup;
skipping export cannot make an unusable signer ready. Missing file requests one
snapshot and publishes atomically without overwrite; if an existing target wins a
race, skip. Unsupported atomic no-replace publication fails, never silently overwrites.

`REPLACE_EXISTING` is an explicit Java invocation only; require a current validated
snapshot, safe existing regular target, output lock and atomic replacement. Missing
target is an error (use create). No automatic watcher/update and no property that
silently replaces an existing startup export. Atomic-move-unavailable, unwritable
parent, unavailable source or invalid public material returns typed error
`ExportAtomicUnsupported|ExportWriteFailed|PublicationUnavailable|PublicationInvalid`;
startup missing-file export errors fail startup. No success message on error. Neither
API changes state bytes/revision, generates/rotates keys or marks them published to
validators, even on success.

Local usage: configure a protected public output directory, initialize normal signing,
then retrieve its public JSON and use the interactive console's upload form together
with issuer/audience/role policy. Compose mounts only `/exports` public output to an
operator-readable host directory; never mount signing storage to Sidecar or the host
export directory. Startup skip means operators explicitly call the bean/API to replace,
then upload the new set. Outbound-only apps select upload or separately hosted JWKS
and leave endpoint/callback server disabled. Default automatic Compose bootstrap
delivers public trust directly and needs no manual export/upload. Separately hosted
JWKS must atomically serve the same approved snapshot; copying a file does not prove
every validator has installed it. Callback trust is always separately installed.

## Automatic isolated Compose bootstrap

Use maintained Smallstep `step-ca`/`step` tooling for TLS issuance/renewal, separate
from SDK-managed JWT generation. It is a private deployment CA, not an application
login/identity server. Primary mechanism sources are
[Smallstep Docker bootstrap](https://smallstep.com/docs/tutorials/docker-tls-certificate-authority/),
[CA ownership](https://smallstep.com/docs/step-ca/certificate-authority-server-production/)
and [renewal](https://smallstep.com/docs/step-ca/renewal/). The proposed composition,
membership gates and reload behavior remain unimplemented and must be proven locally.
PR-8.0.3 pins images/versions, exact automation commands and concrete networks/routes.
Customers start the recipe; init/renewal containers run certificate commands automatically.

| Order | Owner, persisted material and startup gate |
| --- | --- |
| 1 | Operator selects strategy, deployment ID, exact issuers/audiences, file/database authority, independent transports, peer IDs and callback presence. Init takes exclusive deployment lock and records `BootstrapState{version:1,deploymentId,stage,status,authority}` in a protected control volume |
| 2 | CA initializer creates a unique deployment CA and protected CA volume; renewal service alone has CA enrollment authority. No application/Sidecar receives CA private key. CA health gates certificate enrollment |
| 3 | SDK signing initializer runs as application signing owner and creates state only with matching `NEW` bootstrap record and empty store. Generate deployment-specific JWT keys separately from all TLS keys; public snapshot alone goes to public delivery volume |
| 4 | TLS provisioner generates distinct app/Sidecar-proxy/server/client key pairs, exact SAN identities and EKUs; deliver owner-only private volumes and public CA chains/member policy. Native SDK mounts its client material, client Caddy mounts its own when selected; Sidecar Caddy holds Sidecar ingress identity. Callback egress/ingress identities are separately provisioned if used |
| 5 | Offline Sidecar initializer installs policy/public keys and empty ledger only into a provably new selected authority: absent database trust plus matching NEW marker, or new complete file document. Callback initializer separately installs its receiver-specific policy. Each records content digest/revision and acknowledges installation |
| 6 | Signing owner records validator publication acknowledgements, activates initial key, and bootstrap record becomes ESTABLISHED. Start servers only after complete valid state; admission readiness requires matching trust/member generation. Start renewal/material delivery services |

Sidecar never mounts application signing storage, CA private storage or another
workload's TLS private volume. Read-only public delivery cannot mutate the signer.
Compose protected networks, OS mounts and unpublished listeners must prevent direct
ingress bypass and borrowing outbound proxy identity; unpublished ports alone are
insufficient. Use exact workload SAN/issuer membership policy at TLS ingress in
addition to CA validation. Callback origin policy may admit only Sidecar's identity.
No callback means no callback listener, server key or TLS chain, but invocation
client/proxy and separately elected JWKS hosting retain their own requirements.

Each initialization stage persists an idempotency record/digest before advancing.
Concurrent/repeated init takes the same lock and resumes only verified matching
partial state; never overwrites material. Stage failure leaves services unready.
ESTABLISHED always means read/validate, never write initial trust or generate keys.
If established state is absent/corrupt or an initial marker disagrees with nonempty
authority, stop and require explicit operator recovery from protected backup. A lost
marker is not proof of a new deployment. Database transaction/file atomic publication
must prevent a crash from installing partial trust. Repeated bootstrap ignores original
public seed for mutation; newer runtime withdrawal/tombstones remain authoritative.
Restore of a backup must reconcile current withdrawal ledger before admission, otherwise
remain stopped. Explicit database disaster restoration is operator administration,
never an unauthenticated runtime bootstrap endpoint or management-token privilege.

In file mode, operator changes require stopped-service replacement/restart under file
authority; in database mode initial offline installation permanently yields to runtime
interactive administration. Normal runtime never accepts bootstrap credentials. No
automation token may use initialization as a path to restore withdrawn trust.

Customer-managed signing/TLS/trust is an explicit alternative for each owner. Require
complete valid key/chain/identity/permission and receiver policy before startup. Missing
or invalid customer inputs stop startup; never generate replacements or relax TLS.
Relay/Exchange use configured provider trust and no local signing initialization;
TLS automation may still run. Certificate renewal is independent of provider JWT keys.
First start and independent deployment uniqueness must be tested across real services,
not inferred from key generation alone.

## JWT lifecycle and enforcement clocks

`L=300s` maximum accepted lifetime and `S=60s` skew remain the preceding reference.
`tLast` is the last issuance time with an old key. Keep routine verification overlap
until strictly after `tLast+L+S`; clocks must meet that skew budget. Withdrawn keys
receive **no** overlap. Issuer snapshot state and validator trust are separate owners.

| Transition | Uploaded trust | JWKS URL trust | Enforcement/owner |
| --- | --- | --- | --- |
| Stage/publish routine replacement | Explicitly export/upload union old+new to Sidecar and each callback validator; acknowledge revisions | Serve union old+new; confirm each validator fetched it within normal refresh bounds | Signing owner cannot activate before all intended validators acknowledge |
| Activate new signing key | Operator releases common revision barrier | Same | New issuance uses new kid; replicas that have not acknowledged stay stopped; old becomes overlap |
| Retire old | After `tLast+360s`, explicitly upload set without old | After same bound, remove old from URL; validators detect normally within 65s or retain at most 300s since last successful retrieval during outage | Owner removes private old key from usable set; old trust retention never extends JWT lifetime |
| Emergency key/issuer withdrawal | Interactive local commit records tombstone and invalidates caches | Same local commit; ignore matching URL keys including stale in-flight refresh | New Sidecar authentication and queue handoff reject after local durable/publication gate; success response marks this point |
| URL removal without local emergency commit | Not applicable | Up to 65s normal detection; up to 300s from last good snapshot in outage | Never advertise as immediate distrust |
| Restart/outage | Durable uploaded state/ledger restored before admission | Durable revision/ledger plus finite snapshot age, otherwise fail closed | No startup age reset, fallback or bootstrap restoration |
| Explicit restoration | Interactive exact tombstone/current revision plus validated candidate | Same; validate current source and exclude other tombstones | A new revision is intentional trust; ordinary rollback/import/refresh cannot restore |

On incident: stop compromised issuance, withdraw at Sidecar, then independently
withdraw at every callback validator (interactive operator-controlled application
configuration/administration, not a Sidecar bearer token). Record commit times and
unreachable validators; stop affected routes/peers at ingress until they acknowledge.
SDK callback validators use the same protected revision/tombstone/refresh semantics,
but have their own store and authorization. There is no central revocation broadcast
claim. A validator not yet locally withdrawn may accept valid tokens until its own
commit, source age bound or token expiry/skew; a static upload can continue until its
operator updates it. For a stolen signing key, token lifetime alone does not bound
exposure because the attacker can mint new tokens. Report this coordination window
explicitly; local commit is the measurable emergency boundary.

Sidecar serializes emergency revision with new admission and queue-to-Framework
handoff. Queued credentials retain exact bytes/role snapshot and ordinary expiry
semantics; a withdrawal-only check of issuer/kid/fingerprint runs before handoff.
Admission stores those identifiers from the actually validated policy/key together
with its trust revision; queue checks never derive a new trusted identity from
unverified token claims or perform URL discovery.
Do not silently re-decode expiry or refresh credentials at that boundary. Withdrawn
queued records terminate denied without Framework invocation. Work already admitted
to Framework keeps its generation and supported authorization; no kill/revoke SPI is
available in beta.8. It may finish or fail when a callback independently rejects it.
Already authenticated callbacks/in-flight business effects are not undone; new callback
requests authenticate under that receiver's current policy. No guaranteed rollback,
cancellation or transactionality follows from distrust.

Live callback authority policy remains `TOKEN_SNAPSHOT` or explicit `LIVE_ACCOUNT`:
the latter checks enabled account and intersection of live rights on each new callback,
the former cannot observe account/logout changes without a resolver. Local logout
stops new credential acquisition but not valid issued bearer JWTs. Management tokens
always have the per-request/post-wait live checks above; they are not execution
credentials. Same `(iss,sub)` renewal permits later polling; changed pair does not
inherit ownership. Poll timeout/interruption/expiry never renews captured work or
resubmits/cancels it. All recovery, refresh and reconnection preserve no automatic
submission/callback replay, even if the previous response/effect is uncertain.

## TLS generation renewal and member removal

JWT signer/source state is independent of TLS private keys, CA roots and peer policy.
Deployment renews leaf certificates automatically via Smallstep at two-thirds of
their validity, with a scheduler interval at most 60s. Deliver a complete versioned
directory `{generation,leaf,chain,privateKey,trustRoots,peerPolicy,manifestDigest}`
and atomically switch its pointer after validation. Never watch a partially overwritten
PEM as a complete update. Loader checks key match, chain, exact SAN, EKU, validity,
permissions and monotonic generation before acknowledging. Customer-delivered material
obeys the same loading contract; cloud delivery is separately verified future scope.

| Event | SDK-native client path | Client-Caddy/proxy path |
| --- | --- | --- |
| Routine valid leaf update | SDK loader validates within 5s of complete delivery, creates a new SSL context/pool and atomically sends new requests to it | Deployment reload controller validates and applies a complete Caddy configuration/material generation within 5s, confirms active generation before acknowledging |
| Existing connections on routine update | Close idle old pool immediately; in-flight requests finish under old generation up to their existing request deadline, capped at 30s from switch, then close | Stop old generation accepting new streams/requests; close idle/upstream keepalives and drain active requests at most 30s, then terminate old workers/connections |
| Invalid/partial/missing update | Reject entire candidate; retain previous valid generation, raise degraded status; no partial key/trust mix | Same; failed reload never acknowledges the candidate |
| Active leaf expires before valid replacement | At notAfter stop new requests and close old connections, including in-flight; fail readiness. No grace, HTTP downgrade or identity substitution | Controller enforces same cutoff even for existing TLS sessions and client-Caddy protected local listener |
| Recovery | Valid higher generation restores readiness after validation and fresh handshake; failed/uncertain operations stay failed | Same; restart/reconnect never replays pending requests |
| CA rotation | Install explicit dual-root transition and test new chains before switch; retire old root with policy generation and pool retirement | Same for each direction; broadening roots never broadens exact workload membership |

Native invocation retains Sidecar Caddy as server/member enforcement. Callback egress
uses Sidecar's managed REST pools and the same generation rules, or a reference
egress proxy with these rules. Callback ingress uses its separately selected server/
proxy material. Client-Caddy local listener is restricted to its application and fixed
destination, including no-callback invocation. Neither path exposes an open proxy.

Membership removal is an emergency **deployment** policy change, independent of
JWT issuer withdrawal. Remove exact SAN/member ID at all accepting ingress points,
disable its enrollment/renewal, distribute a higher policy generation and acknowledge
only after old connections are gone. The local commit gate first stops admission/new
streams for the removed peer, then closes all its connections within 5s, including
pooled, active and HTTP/2 streams. Disable session resumption across the policy
generation (invalidate tickets/cache); a new connection or resumed session cannot
reuse removed membership. When peer-selective closure is unavailable, drain/terminate
the whole old proxy/server generation and its upstream connections within 5s. This
deliberate availability cost avoids an unsupported per-peer disconnect promise.

Removal is complete only after every enforcing ingress acknowledges. If one cannot
apply within 5s of delivery, stop that ingress/network route; no continue-with-old-policy
fallback. Operator propagation time before delivery is an explicit unbounded
coordination window, measured separately from the local 5s gate. New business
requests cannot use old pools after local commit; already forwarded work may have
effects. Termination cannot cancel Framework work or roll effects back. Management
browser ingress remains separately authenticated and cannot route around execution
membership policy. Tests must count real attempts across the native and proxy paths;
configuration reload success alone does not prove connection retirement or no replay.

## Dependencies, gaps and evidence ownership

| Responsibility | Implementation owner / phase | Required attributable evidence |
| --- | --- | --- |
| Source schemas, protected trust store/admin, single administrator, all preset/indirect gates, durable withdrawals, initial queue gate | Sidecar root, Phase 1 | Parser/HTTP/database/file/restart tests here; no evidence inherited from current static JWT configuration |
| One signer/source, store and replicas, endpoint/export and initial isolated bootstrap | `sdks/java/` and Sidecar integration, Phase 1 | SDK files/component tests here; actual Compose initialization in external suite |
| Independent callback trust and current-policy checks | SDK Phase 1 | Both validators separately tested, both callback arrangements from preceding reference |
| Finite multi-issuer entries and distinct group members | Sidecar/SDK Phase 2 | Cross-issuer authority and group isolation in root plus external suite |
| Rollover/outage races, guarded recovery, replica barrier, TLS reload/renewal/removal mechanisms | Sidecar/SDK/deployment Phase 3 | Focused state/pool tests here plus both real mTLS paths/faults in external suite; Phase 3 implements mechanisms, not only stress tests |
| Complete runtime/commands/deployment/evidence matrix | PR-8.0.3 design, Phases 1–3 evidence | `C:/opendev/code/loomspan-sidecar-test-suite`, preserving existing examples; record SDK/Sidecar/Framework revisions, config, command, result |
| Execution semantics and admitted lifetime | Framework beta.8 public API | No required new Framework contract identified; no cancellation promised. A later kill requirement must be planned there |

[Research](../research/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md)
demonstrates current gaps: one static process issuer, no persisted trust/tombstones,
multi-account management without token names, no SDK signer/export/endpoint/bootstrap,
captured TLS pools without reload and queue handoff without withdrawal checks. Existing
tests are not proof of these planned behaviors. Only closed `ai.loomspan.api` types,
including supported `RestSkillHandler`, are allowed; retain production/test ArchUnit
bans on `ai.loomspan.internal..`/`ai.loomspan.autoconfigure..` and Java `@SkillMethod`
skills. Use documented Framework `loomspan.*`, Sidecar `loomspan-sidecar.*`, SDK
`loomspan-sdk.*` and standard Boot SSL namespaces. Framework stays beta.8; no rebuild
or snapshot repository is part of Sidecar development.

No unresolved material decision is knowingly deferred within this ticket's scope.
An implementation unable to meet atomic file safety, callback isolation, publication
barriers or connection cutoff must stop affected sign-off and obtain a recorded
contract correction, never quietly weaken the requirement. Independent Step 5 review
is required before this contract unit completes. All runtime evidence remains planned.
