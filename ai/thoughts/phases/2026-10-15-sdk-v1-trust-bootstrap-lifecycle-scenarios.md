# SDK v1 trust, bootstrap and lifecycle planned scenarios

**Development policy:** Destructively replace superseded development contracts;
no compatibility shims, legacy adapters or parallel obsolete APIs. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt). No reset or deployed-data
deletion is authorized by this documentation unit.

Companion acceptance matrix for the
[normative reference](2026-10-15-sdk-v1-trust-bootstrap-lifecycle-reference.md) and
[PR-8.0.2](../tickets/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md).
**Every row is PLANNED; none is executed runtime evidence.** Each row is a test
family with explicit parameter variants, not permission to skip negative cases.
PR-8.0.3 consolidates this with the identity matrix and remaining roadmap families,
selects actual build/runtime baselines and commands. No imaginary SDK command is
claimed runnable here.

Owners: **Root** = Sidecar focused tests in this repository; **SDK** = independent
`sdks/java/` focused tests here; **Suite** = real local Compose/services in
`C:/opendev/code/loomspan-sidecar-test-suite`. Phase 1 owns initial enforcement,
Phase 2 group extension, Phase 3 lifecycle mechanisms and stress. Rows with multiple
phases require initial evidence first, not deferral of the entire row to Phase 3.

Use generated RSA fixtures, temporary databases/directories and deterministic clocks,
barriers and local HTTP servers for focused tests. Suite uses real signed JWTs,
Smallstep/Caddy/TLS handshakes and synthetic business counters with deterministic
model fixtures; no paid or hosted services, deployed data or real business effects.
Record tested revisions/versions, configuration, exact command/result and measured
timing for every implemented row. Framework tests alone are insufficient.

## Trust source and persistence scenarios

| ID / status | Arrange | Action and required assertion | Owner / phase |
| --- | --- | --- | --- |
| TRUST-01 / PLANNED | Each public RSA `jwks`, `jwk`, `pem-spki` with explicit policy, generated 2048/3072-bit keys | Interactive create, read, restart; policy/source/kid/fingerprint survive, issuer/audience/role checks succeed only as approved | Root + SDK / 1 |
| TRUST-02 / PLANNED | Valid multi-key set plus one private/symmetric/non-RSA/short/malformed key; private/encrypted/PKCS#1/certificate PEM variants | Reject whole candidate; compare trust/draft/active bytes/revision unchanged, no rejected content in logs | Root + SDK / 1 |
| TRUST-03 / PLANNED | Empty/oversized set, >32 keys, missing/duplicate/overlong kid, duplicate JSON fields, wrong alg/use/key_ops, unknown fields/external references | Reject atomically; no external key URL contacted and no partially accepted keys | Root + SDK / 1 |
| TRUST-04 / PLANNED | Local JWKS server at fixed allowed TLS endpoint, both application-hosted and separately hosted variants | Fetch valid public response; wrong TLS chain/hostname, redirect, DNS-rebinding/all-address/CIDR/IPv6 bypass, metadata/link-local and undeclared destination fail; no token-derived URL fetched | Root + SDK / 1; Suite host variants / 1 |
| TRUST-05 / PLANNED | Slow DNS/connect/body, compressed oversized body, invalid JSON/empty/304 without snapshot, failing status and unknown-kid storm | Observe 2s connect/3s response/5s total and size caps, single-flight/10s coalescing, normal refresh not starved; invalid response never replaces valid snapshot | Root + SDK / 1; stress / 3 |
| TRUST-06 / PLANNED | Durable URL snapshot at age 299s, 300s, old revision, future timestamp; no-cache cold start | Outage then restart; only same-source snapshot younger than 300s usable; no age reset/fallback, fail readiness without usable trust; valid refresh recovers | Root + SDK / 1 initial; Suite outage / 3 |
| TRUST-07 / PLANNED | Suspended fetch of pre-withdrawal keys, persisted key and issuer tombstones | Withdraw then release fetch/change URL/restart; kid or thumbprint matches remain denied, issuer blocks future keys; no refresh/import/rollback/bootstrap resurrection | Root + SDK / 1 initial; race stress / 3 |
| TRUST-08 / PLANNED | Key withdrawn, same material renamed kid, different material reusing old kid, stale restoration revision | Ordinary updates and stale restore fail; explicit interactive exact-tombstone restoration succeeds only for specified entries, other tombstones persist | Root + SDK / 1; Suite / 3 |
| TRUST-09 / PLANNED | One issuer Phase 1; two distinct issuers with equal subjects/kids/role strings Phase 2 | Enforce finite dispatch/source and issuer-specific roles; unknown issuer/workload member gains no issuer rights; group certificate never creates trust | Root + SDK / 1 single; Suite / 2 group |

## Shared management authorization scenarios

Cross **each** Read/Edit/Publish preset with every protected route/action below;
do not use Publish alone as a proxy for all presets. Compare complete protected and
ordinary state, draft, active generation, credential and lease revisions on denial.

| ID / status | Arrange | Action and required assertion | Owner / phase |
| --- | --- | --- | --- |
| MGMT-01 / PLANNED | Single administrator and named R/E/P tokens; complete reference route table | Read inspects; Edit acquires/authors/validates without publication; Publish publishes. Undeclared operations deny. No invitation/secondary role path survives replacement | Root / 1 |
| MGMT-02 / PLANNED | Valid Publish lease/draft/base/proof and new fixed caller-passthrough destination | Publish successfully without browser approval; exact approved target receives unchanged token; egress/peer/receiver checks still enforce limits | Root + Suite / 1; group / 2 |
| MGMT-03 / PLANNED | Each preset, direct issuer/source/audience/profile/role/withdraw/restore/account/token-create/token-list/revoke/takeover request | All deny; interactive valid session plus CSRF succeeds for allowed mode; no security lifecycle privilege inferred from Publish | Root / 1 |
| MGMT-04 / PLANNED | Each preset and changed protected section in draft save, import review/load, rollback review/load, reconcile, validate, publish; repeat unknown protected fields | Reject entire operation without silent removal/partial draft/config/credential apply; unchanged current section and revision permits otherwise authorized action | Root / 1 |
| MGMT-05 / PLANNED | Each preset, valid old draft/proof; block on model validation or lock, then interactive trust change | Recheck current trust under gate; reject stale save/load/publish preserving saved draft and active state. Explicit reconcile/revalidate permits later ordinary publication | Root / 1; repeated contention / 3 |
| MGMT-06 / PLANNED | Waiting mutator for each token; expiry/revoke/disable/credential reset/live authority reduction during wait | Request and precommit checks prevent operation; release originating lease, preserve draft. Re-enable cannot revive invalidated credentials; completed publication not rolled back | Root / 1; lifecycle stress / 3 |
| MGMT-07 / PLANNED | Name/expiry boundary and duplicate-name tokens, revoke twice, restart and DB inspection | Enforce 1–80 character name, default 7/max 30 days, creation-only secret, hash-only storage, no-store metadata; durable idempotent revoke, safe logs and client protected storage guidance | Root / 1 |
| MGMT-08 / PLANNED | Bearer plus session (valid or stale), duplicate Authorization, cookie-only writes, pure bearer, execution JWT and management token | Mixed denies; browser CSRF retained; pure authorized bearer authoring works; execution/management credential domains deny each other | Root HTTP/browser / 1 |
| MGMT-09 / PLANNED | File/database mode crossed with interactive and each preset | File inspection works, all lease/draft/import/rollback/publish/trust mutations deny; account/token security lifecycle remains interactive; DB mutations retain exact lease/revision/base/proof checks | Root / 1 |
| MGMT-10 / PLANNED | Two clients of single administrator, one saved draft/lease, handoff, lease expiry and stale runtime base | Same-account reads allowed; origin/generation and expiry protect writes; explicit handoff invalidates old holder; publish clears draft, failure preserves it; no automatic merge | Root / 1 |

## Signing, endpoint and export scenarios

For every export/error assertion capture signing-state bytes and revision before/after;
require equality. Compare endpoint and both exporters at the **same** pinned snapshot
revision rather than across an intentional concurrent key transition.

| ID / status | Arrange | Action and required assertion | Owner / phase |
| --- | --- | --- | --- |
| SIGN-01 / PLANNED | Guarded new deployment, Spring/custom strategies referencing applicationSigner | Both issue through same initialized store, issuer/kid persist on restart; Relay/Exchange never create local signing state; private store inaccessible to Sidecar | SDK + Suite / 1 |
| SIGN-02 / PLANNED | Concurrent initializer/writer processes, writer plus read-only replicas | Exactly one lock owner; competing writer fails. Stop/install/ack barrier keeps lagging replica stopped; no signing with mixed/unacknowledged generation | SDK / 1 initial; Suite crash/replica stress / 3 |
| SIGN-03 / PLANNED | Supplied SigningStateSource, configured managed-store conflict, unavailable/invalid alternative | Supplied source replaces default; conflicting owner fails startup; no store generated/fallback; common issuance/public snapshot generation holds | SDK / 1 |
| SIGN-04 / PLANNED | Established marker with missing/corrupt/wrong-ID/issuer/private-permission state; protected backup | Fail closed without regeneration; explicit backup recovery reconciles current withdrawals; intentionally changed deployment identity requires explicit trust transition | SDK + Suite / 1 initial; fault recovery / 3 |
| SIGN-05 / PLANNED | staged/active/overlap/retired/withdrawn keys with publication acknowledgements | Only staged/active/overlap publish; activation impossible until all validator and replica acknowledgements; retirement obeys last issuance +360s; no withdrawn private signing | SDK / 1 initial; lifecycle / 3 |
| ENDPOINT-01 / PLANNED | Disabled/explicit enabled properties or Java settings, existing servlet server | Disabled adds nothing; enabled GET/HEAD serves public same-owner snapshot, ETag/no-store; neighboring routes/CSRF/browser security unchanged | SDK / 1 |
| ENDPOINT-02 / PLANNED | Nonweb/reactive/no-server, route/callback collision, duplicate property/bean settings, application-policy access | Clear startup errors for unsupported/ambiguous arrangement; explicit application-policy permits configured retriever only; source failure yields 503, never new signer | SDK / 1 |
| ENDPOINT-03 / PLANNED | Outbound-only application without callback listener; upload/separately hosted/application-hosted variants | Invocation/polling works without listener for upload/separate host; only elected own-host endpoint requires existing server; local/Compose public retrieval and upload retain separate receiver policy | SDK + Suite / 1 |
| EXPORT-01 / PLANNED | Initialized owner plus no listener; explicit path or no export path; Java bean/standalone factory | Disabled writes nothing; startup property and Java export create public identical snapshot at fixed revision; no customer startup hook/server/CLI/plugin required | SDK / 1 |
| EXPORT-02 / PLANNED | Active/overlap/staged/withdrawn keys; malicious private/symmetric custom source | Both APIs/endpoint show valid publishable public keys/metadata only; reject entire invalid source rather than filter. Withdrawn absent; signing bytes/revision unchanged | SDK / 1 initial; changing snapshot / 3 |
| EXPORT-03 / PLANNED | Safe existing file, modified signing keys or unavailable snapshot, repeated startup | Skip unchanged bytes/mtime before snapshot access, null exported revision; no claim existing file current; independently invalid signing initialization still fails startup | SDK / 1; repeat/key changes / 3 |
| EXPORT-04 / PLANNED | New and existing outputs, concurrent exporters and forced atomic-move failure | Create never overwrites; explicit Java replacement atomic, missing target error; no fallback/partial output; result accurately reports created/skipped/replaced; state unchanged | SDK / 1; concurrency/crash / 3 |
| EXPORT-05 / PLANNED | Unwritable/missing parent, signing directory/store/backup aliases, symlink/reparse ancestors, hard links and unsafe shared parent | Safe typed failure, original output/store intact, no false success or temp-secret leak; unsupported platform safety fails closed | SDK / 1 |

## Automatic bootstrap scenarios

| ID / status | Arrange | Action and required assertion | Owner / phase |
| --- | --- | --- | --- |
| BOOT-01 / PLANNED | Two fresh isolated volume sets; Spring/custom; independently selected HTTP/HTTPS/mTLS and native/proxy paths | One recipe start performs all init/certificate commands automatically; different JWT/TLS keys/CAs per deployment; public admission and independent callback trust correct, no new login service/manual exchange | SDK + Root focused; Suite real recipe / 1 |
| BOOT-02 / PLANNED | Complete first start, both authority modes, persisted private/public/control volumes | Repeated restart preserves issuer/kids/TLS identity, skips established installation; Sidecar never receives app signer or CA private volume, public export host mount contains no secrets | Root + SDK + Suite / 1 |
| BOOT-03 / PLANNED | Concurrent init, interruption after each stage, failed CA/enrollment/startup, mismatched digest/marker | Exclusive lock/resume verified partial state only, no overwrite/partial trust; services unready until full acknowledgements; no regenerating established missing state | Root + SDK focused / 1; Suite crash/recovery / 3 |
| BOOT-04 / PLANNED | Runtime key/issuer withdrawal then repeat bootstrap/restart; lost marker plus nonempty authority; stale backup | Seed cannot restore trust/tombstone; lost marker is not new deployment; inconsistent recovery stops; explicit current-ledger reconciliation required | Root + SDK / 1 initial; Suite / 3 |
| BOOT-05 / PLANNED | Explicit customer signer/certificates/trust, valid then incomplete/invalid inputs; Relay/Exchange configured | Valid inputs usable; invalid fails without generated/permissive fallback, strategy unchanged; provider JWT keys separate from TLS renewal | SDK + Root + Suite / 1 |
| BOOT-06 / PLANNED | No callbacks; native and client-Caddy invocation across all four strategies; elected hosted JWKS separately | No callback listener/TLS setup; client Caddy still required only on its invocation path; prevent direct ingress bypass and unauthorized local proxy identity borrowing | Suite / 1; group / 2 |

## JWT lifecycle scenarios

Parameterize uploads and URLs independently in Sidecar and callback validators;
each validator must enforce its own committed revision. Never infer callback distrust
from a Sidecar success response.

| ID / status | Arrange | Action and required assertion | Owner / phase |
| --- | --- | --- | --- |
| LIFE-01 / PLANNED | Old active/new staged, uploads or URLs, delayed validator acknowledgement and replica | Before ack old signs; after all ack activate new; old tokens accepted through lifetime/skew; retire only strictly after last issuance +360s, independent source handling | SDK + Root initial gate / 1; Suite timing / 3 |
| LIFE-02 / PLANNED | Cached old key, suspended refresh/queue handoff; admission barrier | Emergency local withdraw commits durable tombstone; new admission and not-yet-handed-off queue work denied at gate after commit, no overlap; admitted Framework work not implicitly cancelled | Root / 1; SDK callback + Suite timing / 3 |
| LIFE-03 / PLANNED | URL key removal with healthy refresh then outage; mutable clock at 60/65/299/300s and restart | Measure normal <=65s visibility and outage age cutoff, no restart reset; local emergency commit bypasses cache wait. Unknown key/failed fetch never widens trust | Root + SDK initial bounded checks / 1; Suite timing / 3 |
| LIFE-04 / PLANNED | Sidecar withdrawn, independently delayed callback validators, static upload and URL variants | Record each local commit and propagation window; before receiver commit exposure remains, after commit new callbacks reject; isolate unreachable route/peer. Do not bound stolen-key exposure by one token lifetime | SDK + Suite / 3, independent validators initial / 1 |
| LIFE-05 / PLANNED | Withdrawn queue work versus already admitted work and authenticated/in-flight callbacks with synthetic committed effect | Queue denies without Framework invoke; admitted work may finish/fail callback; effects never rolled back, no submission/callback replay on recovery | Root + SDK / 1 initial; Suite / 3 |
| LIFE-06 / PLANNED | Expiring captured JWT, renewed SDK credential, logout, disabled account and reduced rights; snapshot/live policies | Exact captured bytes persist and expiry may fail callback; LIVE_ACCOUNT intersects each new callback, snapshot cannot infer account change. Same pair later polls; changed pair denied; no silent renewal/resubmission | Root + SDK initial / 1; Suite lifecycle / 3 |
| LIFE-07 / PLANNED | Stopped/expired poll, uncertain prior submission/callback response, refresh/reconnect/restart | Preserve known execution ID; bounded safe status-read policy belongs to PR-8.0.3; count exactly one automatic submission/callback attempt, no replay/cancel/rollback advice | SDK + Suite / 3, initial no-retry proof / 1 |

## TLS generation and membership scenarios

Run both native/client-Caddy invocation paths, Sidecar callback native/proxy egress
where selected, both directions with callback ingress, and no-callback invocation.
Phase 1 isolated and Phase 2 group smoke/enforcement cover all four credential
strategies. Phase 3 reuses justified component proofs but retains real lifecycle
tests for **both** mTLS paths, not configuration-only evidence.

| ID / status | Arrange | Action and required assertion | Owner / phase |
| --- | --- | --- | --- |
| TLS-01 / PLANNED | Independent JWT/TLS keys, exact SAN/EKU/chain/member policy, fresh real connections | Valid member admitted, nonmember/wrong SAN/EKU/chain or broad-CA-only peer denied; no downgrade or bypass; management ingress cannot reach execution backend | Root + SDK focused; Suite isolated / 1, group / 2 |
| TLS-02 / PLANNED | Real renewer at two-thirds validity, complete new generation, idle/pooled/active connections with counters | Load within 5s delivery, new requests use new generation, idle closes, old active drains <=30s then closes; JWT keys unaffected; no automatic attempt replay | SDK + Root pools; Suite both paths / 3 |
| TLS-03 / PLANNED | Partial/missing/wrong-key/invalid-chain/SAN/EKU/permission or lower generation candidate | Reject whole candidate, old valid generation continues degraded; no mixed files or false acknowledgement; valid higher generation recovers | SDK + Root; Suite both paths / 3 |
| TLS-04 / PLANNED | Renewal outage through notAfter, idle/active/resumed sessions | At expiry stop new requests and close even existing connections; fail readiness without grace/fallback. Valid recovery fresh handshake, uncertain work never replayed | SDK + Root; Suite both paths / 3 |
| TLS-05 / PLANNED | Member removal with pooled/active/HTTP2/resumed connections at every ingress, including delayed/broken policy delivery | Gate denies new requests then closes all old peer connections <=5s; disable ticket reuse. Whole-generation termination when selective close unavailable; missing ack stops route, propagation window separately measured | Suite / 2 initial membership; SDK/Root retirement + Suite / 3 |
| TLS-06 / PLANNED | Dual-root CA transition, explicit peer allowlist, old sessions and customer-managed update | Validate transition before switch, retire old root/pools with generation gate; root broadening grants no extra member, no reconnect replay; explicit recovery documented | SDK + Root + Suite / 3 |

## Acceptance traceability

| Ticket criterion | Reference clause | Named planned scenarios |
| --- | --- | --- |
| AC1 sources/protected data/retrieval | Verification sources and protected state | TRUST-01–09 |
| AC2 management/authoring/atomic protection | One management API and one administrator | MGMT-01–10, TRUST-07–08 |
| AC3 signer/endpoint/export | One SDK signing and public publication owner; Endpoint and public file export | SIGN-01–05, ENDPOINT-01–03, EXPORT-01–05 |
| AC4 automatic isolated bootstrap | Automatic isolated Compose bootstrap | BOOT-01–06, SIGN-04 |
| AC5 separate lifecycle clocks/validators/TLS | JWT lifecycle and enforcement clocks; TLS generation renewal and member removal | LIFE-01–07, TLS-01–06 |
| AC6 gaps/boundary/owners/reconciliation | Dependencies, gaps and evidence ownership | All rows carry phase/repository owners; no new Framework API required for stated boundaries |

This matrix supplies contract coverage only. Implementation tickets must convert rows
into tests and documentation at the named responsibility; initial denial, persistence
and publication enforcement may not wait until Phase 3. A row is complete only with
attributable passing evidence for its required variants and honest timing limitations.
