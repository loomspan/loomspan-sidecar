# SDK v1 trust, bootstrap and lifecycle contracts Testing Plan

## Change Summary

Documentation-only reference, named scenario matrix and narrow reconciliation of
standing plans/lens for PR-8.0.2. Full 5-Step Pipeline is approved. Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt): destructively replace
superseded development contracts; no compatibility shims, legacy adapters or parallel
obsolete APIs. No development-data reset or data deletion occurs in this unit.
All runtime scenarios described here and in the delivered matrix remain **planned**.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Trust sources | Private material, SSRF, unbounded stale keys | Upload/URL validators; finite retrieval/cold-start/outage/restart cases |
| Administration | Token bypass through direct or indirect edits | Every preset against protected actions, atomic state comparison and stale commit race |
| Signing/publication | Competing owner, premature use, private leakage | Shared snapshot/source precedence, overlap and no-state-change assertions |
| Bootstrap | Restored distrust, shared deployments, missing certificates | Unique deployment/restart/partial failure and file/database authority cases |
| Lifecycle | Old pools, independent caches, false cancellation | Separate clocks, queue/admitted boundary and both-path connection tests |
| Documentation | Conflicting normative rules or invented support | AC/roadmap traceability, relative-link/scope checks and fresh independent review |

## Existing Coverage and Environment Constraints

Inspected existing JUnit 5/AssertJ tests use Spring random-port HTTP, temporary
SQLite databases and mutable clocks. `ManagementPersonalTokenHttpIntegrationTest`
covers presets, creation-only disclosure, hashed secrets, expiry/revocation, mixed
credentials, CSRF and authoring gates under today's multi-user model. It does not
prove single-administrator/protected-trust behavior. `JwtStartupIntegrationTest`
uses an in-process JWKS/discovery server; it has no administrative source controls or
durable emergency state. `RestTransportTlsIntegrationTest` generates BouncyCastle
certificates and exercises real mTLS and per-target bundles, not lifecycle reload.
`ExecutionCoordinatorTest` and shutdown integration exercise current handoff lifetime.
`SupportedLoomspanApiArchitectureTest` already forbids internal/autoconfigure imports
from production and tests, and Sidecar Java skills. Preserve these boundaries.

Root `pom.xml` and CI use Java 21, Boot 4.1.0 and Framework beta.8; Maven wrapper
is the repository build interface. No SDK build/runtime trust/export/provisioner
exists yet. Do not create tests against imaginary classes or run unrelated full
Maven/browser/Docker suites as evidence for prose. Focused future SDK tests belong
in `sdks/java/`; complex real service lifecycle belongs in
`C:/opendev/code/loomspan-sidecar-test-suite`. Its commands and exact SDK baselines
are PR-8.0.3 work. No paid model, hosted provider or deployed-data operations are
needed for this ticket.

## Failing Test First
- Name: `contractCoverageAndConsistencyReview`.
- Type: documentation acceptance review; no behavioral red test applies.
- Location: the new reference/scenario matrix under `ai/thoughts/phases/`.
- Arrange/Act/Assert: compare each ticket criterion and detailed normative roadmap
  obligation with a concrete reference clause and named, owned planned scenario.
- Expected pre-change deficiency: no concrete trust/bootstrap/lifecycle reference
  or matrix exists; a Java runtime failure would not establish this missing artifact.

## Tests to Add or Update

No production or test code is added now. The implementation must write the following
named **planned** scenario families into
`ai/thoughts/phases/2026-10-15-sdk-v1-trust-bootstrap-lifecycle-scenarios.md`.
Each row names setup, action/assertion, phase and repository owner, without marking
runtime checks passed. Stable row IDs may refine these family names.

### 1. `TRUST-sourceValidationAndPersistentWithdrawal`
- Type: future Sidecar/SDK parser, HTTP retrieval and persistence integration.
- Location: existing `src/test/java/ai/loomspan/sidecar/security/` conventions;
  future SDK validation tests under `sdks/java/`.
- Proves: accepted public RSA JWK/JWKS/PEM, concrete policy association, rejection
  of private/symmetric/invalid/duplicate keys and wrong algorithms; no partial apply.
- Inputs: sanitized generated RSA keys, explicit issuer/audience/role maps, bounded
  local JWKS server responses and mutable clock.
- Isolation: local server with controlled redirect/DNS/destination/failure responses;
  no arbitrary network fetch or token-supplied URL.
- Edges: size/time limits, cache age boundary, unknown kid, concurrent refresh,
  stale refresh after withdrawal, URL source change, cold restart and unavailable cache.
- Owners: Phase 1 initial source validation/state/restart; Phase 2 finite group issuer
  isolation; Phase 3 refresh/outage/withdrawal races and stress.

### 2. `MGMT-cumulativeScopesAndAtomicProtectedTrust`
- Type: future Sidecar HTTP/database integration and targeted browser CSRF tests.
- Location: existing management/configuration test packages, extending
  `ManagementPersonalTokenHttpIntegrationTest` and `FileModeManagementHttpIntegrationTest`.
- Proves: Read inspection, Edit authoring and Publish activation including approved
  new passthrough destination; single admin, names, hash-only storage, one-time
  disclosure, expiry/revocation and live disabled-account/credential checks.
- Inputs: each preset crossed with every protected direct route and import,
  rollback, load, publication and stale-draft path; exact before/after state hashes.
- Isolation: random-port app, temporary DB, deterministic waits/latches and clock.
- Edges: post-lock expiry/revocation/authority reduction, identical versus changed
  protected section, unknown fields, concurrent trust revision, mixed credentials,
  browser CSRF, execution/management denial, file-mode mutation, lease/revision/base
  validation and unchanged saved draft on failed commit.
- Owners: Phase 1 initial denial/success and commit race; Phase 3 repeated lifecycle
  and contention, not deferred first enforcement.

### 3. `SIGN-ENDPOINT-EXPORT-oneOwnerPublicSnapshot`
- Type: future SDK state/file/component and application-server integration.
- Location: future SDK test tree in `sdks/java/`; Compose retrieval in external suite.
- Proves: one persistent signer for Spring/custom, exclusive writer/replicas,
  alternative source replacement, publication-before-use, same immutable snapshot for
  endpoint and Java/property export, public metadata/kids and no private fields.
- Inputs: active/overlap/withdrawn fixture keys, temp signing/output directories,
  supplied source, no-server and servlet contexts.
- Isolation: filesystem and local embedded server only; no cloud signer claims.
- Edges: endpoint disabled, enabled without supported server, route collision,
  property/bean precedence, outbound-only uploads, source unavailable, existing file
  unchanged before/after key change, explicit atomic replacement, unsupported atomic
  filesystem, unwritable parent, symlink/hard-link/store-path collisions and simultaneous
  writers. Assert signing-state bytes/revision unchanged after every export/error.
- Owners: Phase 1 ownership/source/endpoint/export safety; Phase 3 rollover and
  withdrawal snapshots, crash/replica/repeated-start evidence.

### 4. `BOOT-independentDeploymentsAndGuardedInitialization`
- Type: future focused state tests plus real automatic Compose integration.
- Location: SDK/Sidecar focused tests here; external test-suite Compose scenarios.
- Proves: two fresh deployments have different signing keys and TLS identities;
  protected storage is delivered only to its owner; public-only admission/callback
  trust delivery, no manual certificate commands, stable restart and no new login.
- Inputs: two isolated volume sets, file/database authority, existing customer inputs.
- Isolation: disposable nonproduction volumes, real maintained CA tooling, synthetic
  operations; tests may reset only their own fixtures.
- Edges: concurrent/repeated init, crash between stages, established marker plus
  missing/corrupt store, failed CA/startup, withdrawn trust, customer-supplied invalid
  material and missing credentials with no generated/permissive fallback.
- Owners: Phase 1 initial automatic recipe and restart guard; Phase 2 membership
  extension; Phase 3 corruption/recovery and renewal stress.

### 5. `LIFE-routineRolloverAndEmergencyWithdrawal`
- Type: future state/queue HTTP integration plus external cross-service faults.
- Location: Sidecar security/execution/rest tests, SDK callback tests and external suite.
- Proves: publication before use, explicit upload versus URL overlap/retirement,
  local emergency commit rejection, persistent tombstones and explicit restoration;
  independently committed callback trust, queued handoff denial and no implicit kill
  of Framework-admitted work or rollback of effects.
- Inputs: controlled trust revisions/clocks and queued/running work barriers.
- Isolation: real signed JWT/TLS, synthetic deterministic business counters and no
  live provider; Framework behavior only through public API.
- Edges: stale refresh/import/rollback/bootstrap resurrection, retrieval outage,
  restart, clock/expiry boundaries, callbacks already authenticated/in flight,
  independently delayed validator withdrawal, snapshot/live-account permissions,
  stopped/expired polling and no automatic submission/callback replay.
- Owners: Phase 1 initial enforcement boundaries, Phase 2 group isolation,
  Phase 3 lifecycle mechanisms/timing/stress.

### 6. `TLS-nativeAndProxyGenerationRetirement`
- Type: future native HTTP-client and Caddy real-connection lifecycle integration.
- Location: focused root REST/SDK tests; external suite for both end-to-end paths.
- Proves: independent JWT/TLS keys, automatic renewal, atomic valid generation swap,
  peer-specific membership, invalid-update/expiry failure and controlled recovery.
- Inputs: native and proxy client paths, both directions, all four credential strategies,
  fresh/pooled/active connections and no-callback client configurations.
- Isolation: disposable local CA and networks, request counters and connection IDs.
- Edges: removed member on existing/new connections, resumed TLS sessions,
  active/pooled request policy, incorrect SAN/EKU/chain, stale/missing partial files,
  CA rotation, renewal outage/expiry and reconnect without replaying any business request.
- Owners: Phase 1 isolated initial mTLS/member enforcement; Phase 2 group membership;
  Phase 3 reload/renewal/connection retirement and fault timing.

## Safe Verification Commands

For this documentation unit:
- Focused: `git diff --check`.
- Related scope: `git diff --name-only` and `git status --short`; all ticket changes
  must remain in documentation artifacts. Inspect untracked documents explicitly.
- Full safe relevant suite: relative-link check below, requirement/scenario coverage
  review and independent Step 5 review. No repository Markdown linter is configured;
  do not add one or run a Maven build for prose-only verification.

PowerShell relative Markdown file-link validation (ignores fragment-only and remote
URLs; anchors require semantic review):

```powershell
$docFiles = @((git diff --name-only -- '*.md'), (git ls-files --others --exclude-standard -- '*.md')) | ForEach-Object { $_ } | Where-Object { $_ }
foreach ($docFile in $docFiles) {
    $docBase = Split-Path -Parent (Join-Path (Get-Location) $docFile)
    foreach ($docLink in [regex]::Matches((Get-Content -LiteralPath $docFile -Raw), '\]\(([^)]+)\)')) {
        $docTarget = $docLink.Groups[1].Value.Split('#')[0]
        if (!$docTarget -or $docTarget -match '^[a-zA-Z][a-zA-Z0-9+.-]*:|^/') { continue }
        if (!(Test-Path -LiteralPath (Join-Path $docBase $docTarget))) { throw "Broken link in ${docFile}: $docTarget" }
    }
}
```

Future implementation uses focused root Maven tests, e.g.
`.\mvnw.cmd -B -ntp "-Dtest=ManagementPersonalTokenHttpIntegrationTest,JwtStartupIntegrationTest,RestTransportTlsIntegrationTest,SupportedLoomspanApiArchitectureTest" test`,
then `.\mvnw.cmd -B -ntp verify` for the broad safe root suite with its prerequisites.
These commands are **NOT RUN and not required here**. Future SDK and external suite
commands must be selected from their actual implemented builds by PR-8.0.3 and owning
tickets. Do not manufacture a currently runnable SDK/Compose acceptance command.

## Optional Developer Checks

None for this contract ticket. Hosted/cloud compatibility checks remain separately
authorized future manual evidence; no manual check substitutes for local gates.

## Exit Criteria
- [x] No runtime red test is claimed; missing contract artifacts are supplied.
- [x] Relative links resolve and `git diff --check` passes.
- [x] All six ticket criteria and detailed normative obligations map to concrete
  reference clauses and named planned scenarios with phase/repository owners.
- [x] All presets, both trust sources, both TLS paths and independent validators
  have positive, negative, race and recovery coverage where applicable.
- [x] Initial enforcement evidence belongs to Phase 1; Phase 3 adds actual lifecycle
  mechanisms and stress rather than inheriting imaginary support.
- [x] Scope is documentation only; no data deletion, new runtime tests or live effects.
- [x] No unrun scenario or unrelated existing test is presented as completed support.
- [x] No unresolved material decision remains for affected contract sign-off.
- [ ] Independent review verifies semantic consistency and security boundaries.
