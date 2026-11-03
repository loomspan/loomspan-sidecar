# HTTP configuration management and authoring Code Review — Cycle 2

## Scope and Repository State

Full-profile pipeline Step 5, cycle 2. Baseline is
`736315dc0c67b179b1835f359604ffcf734d998e` on `main`. Reviewed staged,
unstaged and untracked ticket changes, including new format-4 implementation,
generation registry, database bootstrap, examples, HTTP configuration and shutdown
tests. There are no staged changes. Status-only paths without semantic Git diffs
are line-ending noise. Preserved developer planning edits in the design lens,
design notes and related tickets. No prior review document was read.

Read ticket, research, implementation/testing plans, repository instructions and
design lens. Independently traced startup/bootstrap, candidate assembly, retained
credentials, publication/reversion, editing/authorization, bundle integrity,
durable execution correlation and shutdown. Initial review preceded edits.
The accepted invocation-only HTTP validation boundary is binding; an unavailable
startup executor-map accessor is not a remaining blocker.

## Findings

No remaining actionable findings after the documentation corrections below and
internal re-review. No runtime defect was found in this context.

## Findings Resolved in This Context

### [P2] Remove the obsolete activation limitation from the entry-point README

- Location: `README.md:12`.
- Scenario: an integrator reads that startup/management cannot activate HTTP
  configuration and that examples use a separate route contract.
- Impact: the primary product entry point denies the implemented feature and
  contradicts its linked executable contract.
- Evidence: current runtime uses public namespace prepare/publish and composed
  HTTP file/database tests exercise activation/restoration.
- Fix: describe complete skill/execution configuration and invocation-only HTTP
  validation without a delivery-history narrative.

### [P2] Describe the current management and bundle representation throughout guidance

- Locations: `agent-skills/loomspan-sidecar-authoring/references/management-api.md:11`,
  `:93`, `:125`; `references/management-access.md:64`;
  `references/integration.md:158`; `examples/production/README.md:220`.
- Scenario: a browser/remote author follows three-part REST draft guidance,
  expects format-3 export, or tries to use process URL allowlist bindings when
  moving a complete database publication.
- Impact: these instructions conflict with the strict route-free save API,
  format-4 bundle and no-inheritance contract.
- Evidence: `ManagedConfiguration` and editing Save have skill/execution content
  only; `ConfigurationBundleV4` requires format 4 and excludes rest payloads;
  database publication carries complete opaque executor settings.
- Fix: describe skill-owned operations, execution-owned shared targets, format 4,
  source provenance and explicit destination settings. Retain secret-protection,
  lease, authorization and import/publication rules.

### [P2] State the invocation-only validation and shared-client lifetime accurately

- Locations: `docs/operations.md:311`;
  `agent-skills/loomspan-sidecar-authoring/references/integration.md:202`.
- Scenario: an operator relies on eager route validation/readiness to certify
  HTTP configuration, or assumes admitted work owns separate REST resources.
- Impact: HTTP-invalid but Framework-valid configuration intentionally starts
  and publishes; client ownership is application-wide, so both claims misstate
  operational guarantees.
- Evidence: production HTTP domain validator calls occur only inside
  `HttpSkillExecutor.execute`; runtime owns no HTTP clients and the composed
  shutdown tests assert one Framework budget with a shared client.
- Fix: distinguish Framework/storage integrity startup failures from HTTP
  invocation errors, and describe captured settings with the shared client.

### [P2] Make the quickstart validation action explicit

- Location: `docs/quickstart.md:89`.
- Scenario: a new user waits for documented automatic validation after saving
  credentials before publishing.
- Impact: the editor leaves publication disabled until the user invokes the
  validation action; the walkthrough stalls.
- Evidence: `editor.js` binds validation to `editor-recheck`, labeled
  **Retry validation**, while save and credential replacement clear validation.
- Fix: instruct the user to choose that action after save and reacquisition.

Seven Markdown files changed in this context. No production/test code, schema,
credential, dependency, release tool or developer decision changed. An exhaustive
active-guidance scan was followed by context checks; unrelated REST endpoint,
Caddy route and certificate-validation wording remains valid.

## Acceptance-Criteria and Plan Conformance

Java paths are under `src/main/java/ai/loomspan/sidecar/` unless qualified.

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| File/DB startup, invocation-only HTTP errors, no inheritance | `ConfigurationModeConfiguration` uses fixed empty bootstrap; runtime uses public namespace APIs; HTTP validator is invoked only by executor | Configuration-mode, composed HTTP/profile-import and missing-map/omitted-target cases | implemented |
| Coherent Framework validation/publication; no probes or partial replacement | Runtime candidate validation/preparation precedes durable selection and publication; failed activation reverts selection | Runtime publication/recovery, HTTP invalid candidate and failure-injection tests | implemented |
| Lease/revision/base, ownership, permissions, scope, CSRF, approval | Existing editing/security services retain post-lock live authorization and exact-candidate checks; shared route-free Save | Editing, management HTTP/PAT, browser ownership/handoff, import/rollback and remote authoring suites | implemented |
| Selected restart and exact protected credentials | Effective candidate partitions provider references, authenticates records, attaches captured ciphertext and checks restored content; snapshot excludes effective YAML from JSON | Effective credential/scanner, cross-JVM restoration, portability and bundle tests | implemented |
| Durable correlation independent of HTTP client | Synchronized `ConfigurationGenerationRegistry` maps only Framework IDs, stages before publish and retires through supported API | Model-only, HTTP retained admission, protected history and handoff/publication race tests | implemented |
| Captured old/new settings and one shutdown budget | Executor reads captured maps; reusable client has no publication retirement; queue closes on own-context shutdown | Captured target/credential test, execution shutdown, HTTP lifecycle and full-Sidecar completion/cutoff tests | implemented |
| Browser/remote representation and portable round trip | Skill/execution state, shared management API, format-4 inventory and integrity checks | Editor/history/import browser, remote workflow, bundle-v4 and management transfer tests | implemented |
| Remove old contracts; document reset without deleting data | Direct V1/V3 route-column removal, removed properties/resources/types, strict save and bundle shape; internal plan records shared SQLite loss | Fresh-store/schema, old-field rejection and format-3 rejection tests | implemented |
| Accurate docs and supported Framework boundary | Active docs/examples use current representation and validation limits; corrected seven guidance files above; closed API imports | Packaged guidance/client, architecture and source searches | implemented after documentation fixes |
| Own executable verification and exact allowed residual blockers | No disabled tests, compatibility shims or copied Framework evidence | Independent commands below; unchanged release-only failures attributed to PR 9.3 | implemented under ticket's intermediate-blocker allowance |

## Active Project Guardrails

- Direct obsolete-format/schema replacement, without migration machinery or
  compatibility readers. Reset remains developer-owned; no store was reset.
- Public `ai.loomspan.api` dependencies only. Standard Boot loader interface
  loads a fixed Sidecar resource; no operator Config Data reconstruction,
  internal Raw access, new SPI or Framework accessor.
- Framework owns runtime generations and admitted lifetime. Registry allocates
  no parallel versions, owns no clients and runs no SQL in retirement callbacks.
- Management publication is complete, with no omitted-setting inheritance.
  Caller identity remains captured authentication, never skill inputs.
- Retained encrypted credentials preserve authenticated identity/version and
  the external key remains outside database/export. Inspection excludes effective
  ciphertext; default export contains authored references.
- No live provider/deployment, hosted CI, reset, commit, push or release operation
  was performed. Tests use temporary databases and loopback fixtures.

## Open Questions and Assumptions

No developer decision required. The installed snapshot and matching local
Framework checkout are used under repository policy. Release-tooling reconciliation
remains explicitly assigned to PR 9.3; it is not an HTTP integration workaround.

## Verification Results

- FAIL — `./mvnw.cmd -B -ntp verify`: 300 tests, 299 passed, one failure,
  zero errors/skips. The sole failure is unchanged
  `ReleasePreparationTest.releasePreparationRejectsSnapshotsAndProducesExpectedArtifactPlan`.
  Its beta.8 release-plan success assertion encounters the independently
  reproduced guard below. All functional, security, browser, storage, HTTP and
  lifecycle tests passed, including both composed shutdown cases and architecture
  enforcement. Maven stopped in tests; package/repackage/verify completion is not
  claimed. The ticket's precise PR 9.3 intermediate-blocker allowance applies.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`:
  six tests after documentation corrections.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`:
  11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`:
  eight helper tests, without launching deployments.
- PASS — `node --check src/main/resources/static/management/assets/editor.js`
  and `node --check src/main/resources/static/management/assets/console.js`.
- PASS — `git diff --check` and active-guidance obsolete-contract search after
  fixes. No relevant stale routes-document/format-3/eager-HTTP-validation claim
  remains in the searched product Markdown.
- FAIL — `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v`:
  seven tests, one unchanged error in `test_annotated_tag_and_duplicate_guard`.
  The copied snapshot dependency is correctly rejected with
  `Cannot release with a SNAPSHOT framework dependency`. No real tag is created.
- FAIL — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.8 --tag v1.0.0-beta.2`:
  exit 1, `pom.xml does not use the beta 8 framework line`.
  POM and release/version scripts have no ticket diff; required development uses
  beta.9-SNAPSHOT. Both release-only issues belong to PR 9.3 reconciliation under
  the explicit ticket allowance.

## Residual Risks and Optional Developer Checks

Framework-valid HTTP-invalid configurations may report ready and publish; HTTP
invocation performs domain validation before dispatch. No operational HTTP
preflight guarantee is claimed. Existing development SQLite files need
developer-owned replacement because schema definitions changed; colocated accounts,
tokens, drafts/history and encrypted records are affected as recorded in the plan.
No reset was performed. Hosted CI, release publication/pinning and live composed
deployment recipes remain outside this local review. No optional manual check
is required.

## Disposition

`fixes-applied`. Completed the full independent review, documentation corrections,
internal re-review and verification. Another fresh Step 5 context must review
the resulting work before pipeline completion. No actionable findings remain
in this context; no new product decision or runtime blocker was found.
