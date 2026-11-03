# HTTP executor integration and completion implementation plan

**Development policy: replace superseded contracts directly. Do not add compatibility
shims, legacy adapters, parallel legacy APIs or migration machinery.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt).

## Overview

- Ticket: `ai/thoughts/tickets/2026-11-02-pr-9-3-http-executor-integration-and-completion.md`
- Research: `ai/thoughts/research/2026-11-02-pr-9-3-http-executor-integration-and-completion.md`
- Pipeline: `full`, explicitly approved by the developer after Step 0 on 2026-11-02.
- Outcome: maintained clients use executor declarations and the assembled system
  passes local snapshot integration checks, with release publication still pending.
- Paths prefixed `suite:` refer to `C:/opendev/code/loomspan-sidecar-test-suite`;
  other paths refer to `C:/opendev/code/loomspan-sidecar`.
- Initial state: both repositories clean; Step 1 added only its research artifact.
  Steps 2/3 add only these plans. Preserve any subsequent unrelated developer work.

## Current State

Sidecar's `http/HttpSkillExecutor`, `HttpExecutorClient` and response/mapping classes
already implement the selected contract. `HttpExecutorIntegrationTest`,
`HttpConfigurationIntegrationTest` and `HttpConfigurationShutdownIntegrationTest`
exercise actual public Framework invocation, managed publication/restoration and
resource lifetime. The research maps their source evidence; none is a new pass.

Suite `scripts/suites/equipment_service/author_workflow.py` generates nine REST
leaves and `config/routes/equipment-service.yaml`; `scripts/suite_config.py`
assembles the obsolete route catalog. SDK generators in `scripts/run_sdk.py` and
`scripts/sdk_lane/{local,remote,outbound}.py` do the same. Remote generation reads
removed `rest-routes.yaml` examples. `BlockingClientDriver` assumes unqualified
catalog names and an obsolete error wrapper.

Suite `scripts/prepare.py` exports beta.3 source, Compose builds that export,
and build/portable-package tools hard-code old paths or Framework beta.8. Its build
recorder also enforces obsolete Framework source revision/hash provenance.
Sidecar release-plan tests/tooling retain beta.8 assumptions; the tag success fixture
inherits a snapshot dependency that the real tag guard correctly rejects.

Planning's final source check also found an obsolete authentication integration:
`suite:apps/python/infrastructure/sidecar_client.py` forwards its external Keycloak
access token unchanged, `infrastructure/security.py` uses that same external-token
validator for `/skills` callbacks, and `config/sidecar.yaml` configures removed
issuer/JWKS/role-prefix settings. Current Sidecar `security/ExecutionJwtDecoder`
requires `typ=loomspan-execution+jwt`, `token_use=loomspan_execution`, exact `roles`,
and current-file public JWKS trust. This client must adopt the documented custom-app
execution credential contract; ordinary user login remains Keycloak-owned.

## Desired End State

Equipment leaves use `executor: http` and operation-local `config` with named
targets in `loomspan.executor-config.default.http.targets`. Every equipment POST
uses `body: {source: input}`: lookup input remains flat `{caseId, assetId}`, and creation
input remains `{approval: ...}`. SDK callbacks that previously had no entity omit
`body`. Whole-input and selected-field mapping remain the existing product contract;
do not flatten approval or invent receiver-specific field mappings.

The suite builds the current neighboring Sidecar against the installed
`1.0.0-beta.9-SNAPSHOT`, stages its executable JAR for Compose, and retains package
integrity checks without requiring Framework source commits or source-build hashes.
Existing public-API tests plus actual deterministic client compositions establish
the ticket's protocol, identity, lifecycle and trust obligations.

## Scope

### In scope

- Equipment and SDK declarations, generators, current configuration, assertions,
  runtime build/packaging assumptions and corresponding current documentation.
- Deferred 9.1/9.2 build checks and any concrete ticket-exposed integration defects.
- Existing public-API integration tests, missing composed client assertions and
  isolated provider-free equipment/SDK lanes.

### Out of scope

- Provider calls, model tuning, business redesign, reference-output replacement,
  operational database/credential resets, Framework rebuild or implementation.
- New extension contracts, executor client certificates, compatibility layers,
  exhaustive deployment combinations, commits, pushes, tags and publication.

## Active Project Guardrails

- Only supported `ai.loomspan.api` types; retain ArchUnit's production/test checks.
  Sidecar hosts no Java `@SkillMethod` skills. The suite's embedded Java skills
  remain a separate application and are not copied into Sidecar.
- Runtime HTTP domain validation remains invocation-only; management integrity,
  authorization and Framework structural/reference validation remain enforced.
- Preserve captured caller credentials and settings, durable ownership, current
  trust/expiry before queued admission, and one Framework shutdown budget.
- Preserve result/error content and original failures; never replay unknown effects.
- Trust normal HTTPS verification and Caddy's owned mTLS boundary. No runtime
  allowlist of test recipes or additional TLS identity feature.
- Product guidance describes actual current behavior; internal artifacts hold
  implementation history, limitations and release gates.

## Impact and Risk Analysis

The largest risks are incomplete generator replacement, sending an empty or
incorrectly wrapped body, outdated catalog/error assertions hiding real behavior,
and exercising retained stores with the new schema. Prevent these with generated
configuration tests, receiver-observed requests and a fresh isolated suite workspace.
The accepted replay pack hashes model contracts; transport leaf changes must not
change prompts, model choices, accepted responses, or source business facts. A
material frozen-request mismatch is an escalation, not permission to loosen replay.

Build recording should identify what runs without recreating a provenance system.
Keep whole-JAR/configuration checksums for runtime and supplied-package integrity;
remove Framework revision/clean-tree/source-target/installed-byte equivalence gates.
The developer-installed Maven artifact is authoritative for ordinary development.

## Implementation Approach

Keep model skill ownership separate from Sidecar executor leaves. Rename
`config/rest-skills` to `config/executor-skills`, put operation data in each leaf,
and move the single maintained equipment target into `config/sidecar.yaml`.
Remove route-fragment assembly instead of renaming it into another parallel catalog.
Keep `suite_config.prepare` only for its useful cross-tree duplicate-name check;
Framework and invocation retain their respective validation responsibilities.

For the Python equipment client, preserve Keycloak login/validation at public
business endpoints. Issue a supported RS256 execution JWT only from that verified
identity, with an explicit app issuer, original subject, intended Sidecar audience,
execution purpose/type, key ID, bounded lifetime no later than the source token's
expiry, and an allowlisted subset of the user's exact existing business roles.
Use it for Sidecar submissions/status reads. Authenticate callbacks with the app's
execution public key and the same purpose/type/audience/time rules; do not accept
the external login token at the callback boundary. Receiving business role/record
checks remain unchanged. Store the provided fixture private key only in the Python
app mount, public JWKS in Sidecar/callback trust, and never payloads/model context.
This implements the supported custom-app client pattern, not a Sidecar special case.

Use the current neighboring Sidecar executable rather than exporting an old tag.
`prepare.py` prepares fresh local credentials only. Extend `record_build.py` with
`--sidecar-checkout` (default neighboring checkout): locate the current built JAR
from its POM coordinates, stage it at `runtime-artifacts/sidecar.jar`, and record
host/configuration hashes and actual version coordinates. Compose's Sidecar build
uses existing `deploy/sidecar.Dockerfile`. Java retains its existing build path.
Portable packaging reads the same staged Sidecar artifact, and installation checks
declared package hashes/embedded Framework coordinates without old hard-coded bytes.
This replaces obsolete source-export/provenance machinery without a new build system.

## Phase 1: Reconcile local build and release safety

### Changes

- [x] `scripts/prepare-release.py`, `pom.xml` release profile,
  `.github/workflows/ci.yml`, `.github/workflows/release.yml` — replace beta.8
  release-line assumptions with beta.9. Keep the development POM and packaged
  skill metadata at beta.9-SNAPSHOT. Hosted CI must not override the POM with an
  older release; it will be executable against Central after publication/pinning.
- [x] `src/test/java/ai/loomspan/sidecar/release/ReleasePreparationTest.java` —
  test prospective beta.9 release-plan validation and negative snapshot/version/tag
  cases. `--validate-only` may validate a future release plan from the matching
  development line; actual packaging/release still requires a released POM pin.
- [x] `scripts/test_sidecar_version.py` — explicitly set a released Framework
  dependency in the disposable successful-tag fixture; preserve the separate
  snapshot-rejection case. Never tag the real checkout.
- [x] `suite:scripts/run_sdk.py` preflight/artifact tests — select exactly the
  installed beta.9 snapshot and preserve detection of missing/mixed coordinates.

### Automated verification

- [x] `./mvnw.cmd -B -ntp '-Dtest=ReleasePreparationTest' test`
- [x] `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v`
- [x] Suite `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`
  after its generated fixtures are updated in Phase 3.

### Optional developer checks

None. Published-artifact/hosted-CI checks are a later release gate, not a local pass.

## Phase 2: Replace equipment declarations and runtime consumers

### Changes

- [x] `suite:scripts/suites/equipment_service/author_workflow.py`,
  `suite:config/executor-skills/equipment-service/*.yaml` — generate/commit all
  nine executor leaves, retaining names, descriptions, RBAC and schemas; use
  operation target/method/path/body locally. Delete superseded REST leaves/routes.
  Regeneration must leave model skills and maintained replay assets unchanged.
- [x] `suite:config/sidecar.yaml` — add the `equipment` HTTP target under
  `loomspan.executor-config.default.http.targets`, load executor leaves, remove
  `rest-routes` and removed issuer/JWKS/role-prefix keys. Configure documented
  current-file public JWKS trust and execution audience.
- [x] `suite:apps/python/infrastructure/{security,sidecar_client}.py`,
  `suite:apps/python/suites/equipment_service/skill_routes.py` and other actual
  callback routes — implement the supported custom-app execution issuance and
  separate callback validation described above. Preserve public Keycloak login,
  exact role vocabulary, issuer/subject ownership and receiving business checks.
  Test untrusted/expired/wrong-purpose credentials and source-expiry bounds.
- [x] `suite:scripts/prepare.py`, `suite:scripts/configure_isolated.py`,
  `suite:compose.yaml` — supply the app-owned execution signing key/public JWKS
  through isolated development files/mounts, without replacing any existing key
  or credential. Remove obsolete Sidecar issuer overrides from isolated config.
  Verify private material is never mounted into Sidecar or packaged as source.
- [x] `suite:scripts/suite_config.py`, `suite:tests/test_suites.py` — remove
  route merge/output/reference checks; retain duplicate skill-name detection
  across model and executor trees. Do not add HTTP operational preflight.
- [x] `suite:compose.yaml`, `suite:compose.runtime.yaml` — mount executor leaves,
  remove route mounts and beta.3 source-export build, consume staged Sidecar JAR
  through `deploy/sidecar.Dockerfile`. Preserve offline overlays and data locations.
- [x] `suite:scripts/{prepare,record_build,package_runtime,install_runtime}.py`,
  `suite:config/runtime-source.json` — remove old source export and Framework
  revision/hash provenance requirements, stage/read the current built Sidecar JAR,
  derive version-sensitive paths, retain runtime/distribution integrity checks.
  Do not overwrite operational credentials/stores or maintained evidence.
- [x] `suite:tests/test_runtime_build.py` (new) — exercise staging/recording and
  portable validation using synthetic JARs/temp roots, including mismatched
  coordinates, changed package content and missing artifacts.
- [x] `suite:tests/test_execution_identity.py` (new) — prove issuance derives only
  verified user identity/allowlisted exact roles, callbacks validate execution
  purpose/trust/expiry, and no private credential enters requests or diagnostics.
- [x] `suite:scripts/smoke.py` and `suite:tests/test_offer_context.py` or an adjacent
  focused test — add representative local composed evidence for all eight flat
  lookup requests and a nested creation request. Use fresh synthetic data; for
  creation a business validation rejection after parsing the nested envelope is
  sufficient transport evidence, paired with existing business authorization tests.
  Do not create a real service commitment or weaken receiving authorization.

### Automated verification

- [x] Suite `.venv/Scripts/python.exe -m pytest tests/test_suites.py tests/test_offer_context.py tests/test_replay.py tests/test_runtime_build.py -q`
- [x] In the isolated workspace described in the testing plan: build/package Java,
  record current Sidecar, start offline, run `scripts/smoke.py`, then
  `scripts/run_suite.py mock --suite all --output NEW_DIRECTORY`.

### Optional developer checks

None; maintained reference compatibility is required executable evidence.

## Phase 3: Replace SDK generators and verify transport compositions

### Changes

- [x] `suite:scripts/run_sdk.py` `write_config` and shutdown fixture — replace
  `rest` declarations/routes with per-leaf operations and executor target maps.
  Keep existing synthetic-effect/shutdown counters and body contracts.
- [x] `suite:scripts/sdk_lane/local.py` `configure` — put callbacks/effects in
  executor leaves and targets in application execution configuration; bodyless
  owned POSTs omit `body`, including the required-input expiry leaf.
- [x] `suite:scripts/sdk_lane/remote.py` `configure` — read the actual shipped
  execution configuration for app targets, retain Boot trust and Caddy fragments,
  and apply response-loss endpoint changes in the new target map.
- [x] `suite:scripts/sdk_lane/outbound.py` `configure` — remove unused executor
  targets/leaves directly; do not create an empty legacy routes file.
- [x] `suite:sdk/driver/src/main/java` `BlockingClientDriver` — expect qualified
  catalog descriptor names and the exact current `HTTP 503\n<body>` failure.
  Preserve result strings, unknown-acceptance handling and no-resubmit behavior.
- [x] `suite:tests/test_sdk_runner.py`, `suite:tests/test_sdk_remote.py` — assert
  executor shape/body omission, target overlay fidelity, beta.9 snapshot coordinates,
  absence of legacy files, credential separation and independent request/effect
  counters. Update fixture assumptions rather than Sidecar's product responses.

### Automated verification

- [x] Suite `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q`
- [x] Suite `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar --output NEW_DIRECTORY`
- [x] Repeat the same runner for `standard` and `hardened`, each with a distinct
  new output directory. These three existing recipe selectors form the bounded
  maintained matrix; do not introduce a new combinations framework.

### Optional developer checks

None. Actual HTTPS/Caddy positive/negative evidence is required by this ticket.

## Phase 4: Complete evidence and current guidance

### Changes

- [x] Re-run Sidecar's existing `HttpExecutorIntegrationTest`, `HttpSkillExecutorTest`,
  mapping/response/TLS tests, `HttpConfigurationIntegrationTest`,
  `HttpConfigurationShutdownIntegrationTest` and supported-API architecture tests.
  Add a focused regression only for a concrete uncovered defect; do not duplicate
  previously owned suites merely to create a new ticket-specific test class.
- [x] `suite:README.md`, `suite:docs/{runtime,foundation,suites,sdk-lane,project-agreement}.md`
  and any current consumer guidance found by final search — describe executor
  layouts, current-checkout builds and the installed snapshot workflow directly.
  Keep business responsibilities, model assignment and Java/Python equivalence.
  Historical internal reports may remain historical; active recipes must work.
- [x] Sidecar current docs/examples/packaged authoring guidance — fix any active
  obsolete consumer assumptions discovered by final search; preserve actual
  invocation-only validation/security explanations.
- [x] `ai/thoughts/verification/2026-11-02-pr-9-3-http-executor-integration-and-completion.md`
  — record exact commands/results, source state, isolated project/workspace,
  all acceptance-criterion evidence, reconciliation of both prior blocker lists,
  maintained-reference preservation, Framework gaps (or none), and release limits.
- [x] Update completed implementation/testing/ticket checkboxes only when backed
  by actual passing evidence. Any required failed/unrun check prevents completion.

### Automated verification

- [x] `./mvnw.cmd -B -ntp verify`
- [x] `python -m unittest discover -s scripts -p 'test_*.py' -v`
- [x] `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`
- [x] Suite `.venv/Scripts/python.exe -m pytest -q` and embedded Java
  `mvn -B -ntp -f apps/java/pom.xml verify`.
- [x] SDK reactor/driver/reference-app builds and actual runtime lanes are included
  in the three maintained `run_sdk.py` commands; retain their reports.
- [x] Search active code/config/tests/guidance in both repositories for removed
  declarations, `rest-routes`, separate route files, old SPI/class references and
  stale beta.8/beta.3 dependencies; inspect each match rather than blanket replacing
  ordinary REST terminology or historical records. Run `git diff --check` in both.

### Optional developer checks

Configured external deployment checks remain optional; no live environment is used.

## Test Strategy

First demonstrate the known obsolete release/generator assumptions with focused
tests. Use Python generated-configuration tests for inventory and wrapper/body
contracts; use actual Sidecar + deterministic receiver/model fixtures for invocation
and runtime trust/lifetime. Existing Sidecar public-API tests establish protocol
details, actual SDK recipes establish composed HTTPS/Caddy behavior, and the
unchanged accepted replay pack establishes business-lane equivalence in its scope.
The testing plan supplies safe commands, fixture isolation and completion gates.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Invocation-only HTTP validation; Framework-invalid still rejected | Existing runtime configuration/public reloader paths | `HttpConfigurationIntegrationTest`, management/file-mode tests; zero outbound counter before invalid invocation |
| Equipment/SDK clients use executors and preserve business contracts | Phases 2/3 generators, nine leaves, target maps, driver | Generated shape tests, eight lookup/nested creation transport checks, unchanged mock replay, Local SDK |
| Direct/tool success/failure, exact fidelity, auth/expiry, no replay/state, bounds | Existing annotated executor/client/response mapping | Existing Sidecar public integration + HTTP tests; SDK caller-token/bodyless/response-loss counters |
| Captured publication/restoration/shutdown ownership/lifetime | Existing runtime publication/generation registry/coordinator | `HttpConfigurationIntegrationTest`, `HttpConfigurationShutdownIntegrationTest`, Local shutdown recipes |
| HTTPS and Caddy mTLS trust boundaries | Existing normal client TLS and shipped Boot/Caddy recipes | `HttpExecutorTlsIntegrationTest`, Standard/Hardened SDK scenarios including rejection/no-bypass |
| All earlier blockers reconciled, full local checks green | Phase 1 release tooling, Phase 2/3 maintained clients | Full Maven/Python/SDK builds, deterministic business lanes, explicit prior-blocker table |
| No active obsolete consumers; public Framework dependencies only | Direct removal and current guidance | Search inventory, generated config tests, supported-API ArchUnit, full builds |
| Honest local evidence/limitations/pending release gate | Internal verification artifact | Exact executed receipts/reports, preserved pack hashes, final fresh independent review |

## Risks and Rollback/Recovery

No development-data reset is required for this implementation or its verification:
use new stores only. Existing stores from before 9.2 have incompatible schema/bundle
contracts; any later developer-owned reset would lose accounts, tokens, drafts,
history and credentials. This plan does not authorize that reset, delete a store,
or overwrite operational credentials. New synthetic validation data is disposable.

Create a fresh detached suite worktree at the suite's current HEAD, copy the current
ticket source changes into it, and use `configure_isolated.py` before `prepare.py`.
Give it a unique project, unused nonzero port offset and new report destinations.
Do not copy `.runtime`, `.build`, credentials, existing output or databases.
Keep accepted reference packs byte-identical. Stop/clean only the verified unique
project with the repository environment helper; do not remove retained volumes or
other containers. Leave evidence/workspace paths in the verification artifact.

If the installed snapshot lacks a supported capability, stop the affected work
and surface a separate Framework planning decision. If replay needs a material
contract change, preserve the failure and ask the developer; do not fabricate a
new reference or weaken matching. Ordinary bugs in the changed client adapters
remain in scope to fix.

Release sequence remains pending: developer publishes Framework beta.9 to Maven
Central first; then pins Sidecar to the released dependency and repeats final
build/tests and final commit; hosted CI runs after publication and is verified
before Sidecar `1.0.0-beta.4` / `v1.0.0-beta.4`. Never overwrite a release. This
ticket establishes snapshot evidence only and performs none of those publications.

## References

### Implementation decisions (Step 4)

- The namespace review metadata identifies the original manifest by
  `sourceManifestLfSha256`, hashing its repository LF representation. This
  preserves an already-present Windows manifest without rewriting original
  bytes, while fresh checkouts use the same review identity. All case-file,
  reply and ledger byte checks remain exact; strict runtime matching is unchanged.
- Outbound-only clients remove callback leaves and targets but retain the empty
  selected `default.http` map, required by the registered executor's public contract.
- Current Sidecar exposes readiness on its application port. Suite readiness,
  Docker health, Compose and isolated port configuration use that actual boundary;
  the former 9091 check was an obsolete consumer assumption.
- Public Python recovery derives the app execution issuer from verified login
  identity, matching callback-created record ownership after execution issuance.
- The existing embedded Java API reports `SkillException`/`SKILL_FAILURE` for
  business failures. Composed creation asserts that wrapper after accepted nested
  input; Sidecar additionally asserts the complete receiver business rejection.
- Release-workflow text assertions normalize Windows line endings while retaining
  the exact permission/overwrite checks. Release packaging still requires the
  published dependency pin; prospective validation remains nonpublishing.
- Standard's temporary test CA is supplied to Sidecar through a public-only Java
  PKCS12 truststore and standard JVM trust properties. The HTTP executor uses
  normal JVM HTTPS validation, independently of inbound Boot/SDK SSL bundles;
  no client certificate, executor SSL setting or validation bypass is added.
- The direct rotation credential probe sends no callback entity, matching the
  current owned bodyless callback. Its blocker submissions retain their input.
  A request-boundary regression preserves the original token and all existing
  bounded trust-change observations remain required.
- Declare `cryptography>=45.0.0` explicitly for the SDK generator's Java truststore
  serialization API; existing tested environment is 50.0.2 and no library upgrade
  or alternative TLS implementation is performed.
- Release JUnit/Python red failures were captured before fixing them. The new
  generator acceptance was additionally exercised retrospectively against the
  frozen original module, failing specifically at zero executor leaves versus
  nine required; the current generator passes the complete 268-test suite.

### Replay compatibility finding (resolved by developer decision below)

The real grouped mock run in fresh workspace
`C:/opendev/code/loomspan-suite-pr9-3-4716d69d0f` stopped at baseline/Java's first
model request. `fixtures.replay.request_contract` parses the actual identity as
`default-resolveEquipment`; the frozen first `requestContract.skill` is
`resolveEquipment`. Model (`openai/gpt-6-luna`), reasoning (`medium`), mission,
assigned task and completed evidence are otherwise identical at that boundary.
The strict fixture rejects it as `unexpected or ambiguous stage`, consuming zero
accepted responses. No matching or reference bytes were changed.

Evidence is the isolated `evidence/pr9-3-4716d69d0f/equipment-service/report.json`
and `bundle.zip` (`baseline/java/journal.json` and `terminal.json`). Runtime is
restored/provider-disabled; no commitments or paid calls occurred. The current
public Framework docs require qualified runtime/model identities. This is a
material replay-contract change, not permission for an automatic adapter.

Developer question (presented by the orchestrator): authorize a reviewed,
namespace-only reference-contract update preserving the original pack and
accepted business content, or retain this incompatibility as a ticket blocker?
The recommended bounded change would update only Framework identity positions
under explicit review, retain strict request matching, original pack bytes and
accepted business values, and rerun complete both-host replay. That proposal is
authorized by the subsequent recorded developer decision below. Implementation uses
`fixtures/reference-namespaced` directly, preserving the original pack without a
runtime adapter or fallback. The 316 explicit identity changes are ledgered and
independently tested; business content and strict matching remain unchanged.

- Ticket and research above; `ai/thoughts/phases/http-skill-executor-design-notes.md`.
- Existing 9.1/9.2 verification artifacts for the explicit deferred-blocker lists.
- `docs/verification.md`; suite `AGENTS.md`, `docs/project-agreement.md`,
  `docs/{runtime,foundation,sdk-lane,mid-tier-skill-design}.md`.
- Matching local Framework `docs/quickstart-executors.md` and public API source.

## Developer decision — maintained replay namespace contract

On 2026-11-02 the developer explicitly authorized: "Authorize namespace-only contract update" in response to the observed frozen `resolveEquipment` versus actual `default-resolveEquipment` request identity mismatch. Apply a reviewed namespace-only update to a separately preserved replay contract. Preserve the original pack and accepted business content unchanged. This authorizes qualified identity alignment only; it does not authorize changed business responses, relaxed semantic matching, provider access, or unrelated reference changes. Rerun affected deterministic replay and include exact before/after contract evidence in verification for independent review.
