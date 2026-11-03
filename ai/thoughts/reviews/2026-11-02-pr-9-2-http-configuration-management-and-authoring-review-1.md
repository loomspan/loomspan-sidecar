# HTTP configuration management and authoring Code Review — Cycle 1

## Scope and Repository State

Full pipeline Step 5, cycle 1, on `main` against baseline
`736315dc0c67b179b1835f359604ffcf734d998e`. Reviewed staged, unstaged and
untracked ticket work, including the new bundle implementation, generation
registry, bootstrap resource, HTTP acceptance/shutdown tests and examples.
No staged changes were present. Several status-only Java paths have no semantic
Git diff; no unrelated developer content was removed. Preserved the developer's
prior validation-decision edits in the lens, design notes and related tickets.

Read the ticket, research, implementation/testing plans, repository guidance and
design lens. Reconstructed startup, candidate assembly, publication/failure,
restoration, import/export, editing authorization, execution correlation and
shutdown from source before comparing plan conformance. Initial review was
completed before applying the documentation fixes below. No prior review was read.

The revised invocation-only validation boundary is binding. Lack of a startup
executor-map accessor is not a defect or pending Framework task.

## Findings

No remaining actionable findings after the fixes below and internal re-review.
No runtime implementation defect was found in this cycle.

## Findings Resolved in This Context

### [P2] Describe the route-free format-4 contract consistently

- Location: `agent-skills/loomspan-sidecar-authoring/references/configuration-lifecycle.md:37` and `:47`.
- Scenario: an author follows the lifecycle reference to construct a format-3
  archive or a separate REST draft document.
- Impact: the current strict bundle reader rejects format 3 and management rejects
  the removed route field, contradicting the packaged authoring contract.
- Evidence: `ConfigurationBundleV4` requires integer 4 and only execution/skill
  payloads; `ManagedConfiguration` has no routes property.
- Fix: documented format 4 and skill/execution YAML with shared encrypted credentials.

### [P2] Document the actual inspection payload

- Location: `agent-skills/loomspan-sidecar-authoring/references/management-api.md:132`.
- Scenario: a remote author relies on the documented `effectiveExecutionYaml`
  response member, or the history guide's separate REST YAML.
- Impact: API guidance promises a member deliberately excluded to protect captured
  encrypted records, and describes an obsolete representation.
- Evidence: `storage/ConfigurationSnapshot.java:8` marks effective YAML ignored;
  the HTTP credential unit test verifies ciphertext and that member are absent.
- Fix: described authored skill/execution data and explicitly internal effective YAML.

### [P2] Reconcile the supported executor boundary and setup representation

- Location: `ai/thoughts/design-lens.md:67`; `docs/setup.md:15` and `:40`.
- Scenario: a contributor follows the lens's removed REST handler authorization,
  or an operator follows setup's separate route files and beta.8 guidance.
- Impact: these directly conflict with this ticket's annotated executor and
  skill/execution configuration, or send the author to a stale Framework contract.
- Evidence: root guidance and the executable bean use `@SkillExecutor`; POM pins
  beta.9-SNAPSHOT, with the developer-owned local install workflow.
- Fix: retained the closed API boundary while naming the annotated method; setup
  now describes complete YAML, the installed snapshot and matching-checkout guidance.
  Removed the quickstart fixture README's stale claim that this replacement needs
  no reset; schema reset consequences remain in internal planning artifacts.

Only five Markdown files were changed by review. No production/test code,
credentials, database, dependency, release tool or developer planning decision
was changed.

## Acceptance-Criteria and Plan Conformance

Java paths below are relative to `src/main/java/ai/loomspan/sidecar/`.

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| File/database activation; invocation-only HTTP errors; no inheritance | `ConfigurationModeConfiguration` fixed bootstrap and excluded managed roots; `RuntimeConfigurationService.startFiles/run`; `HttpSkillExecutor.execute` | `ConfigurationModeIntegrationTest`, `HttpConfigurationIntegrationTest` including Boot import/profile selection, absent maps and omitted credentials/targets | implemented |
| Coherent Framework validation/publication without probes | `RuntimeConfigurationService.validateWithCandidate/publishCandidate` uses explicit public namespace APIs, no HTTP domain validator; durable selection revert retained | Runtime publication/recovery and HTTP configuration tests | implemented |
| Ownership, lease/revision/base, permission, token scope, CSRF and approval | Existing editing/security services retain shared API/admission/rechecks; new Save shape rejects obsolete fields | Editing service/HTTP, personal-token, browser, remote-authoring, import/rollback and file restrictions suites | implemented |
| Exact credential restoration and confidentiality | `EffectiveExecutionConfiguration.assemble/restore` partitions provider values, authenticates ciphertext, restores complete authored content and compares effective values; snapshot hides effective YAML | Effective credential, portability, separate-JVM environment, inspection serialization and bundle tests | implemented |
| Durable identity independent of clients | `ConfigurationGenerationRegistry` synchronizes opaque Framework-ID mapping; runtime stages before publication and protects live IDs; coordinator uses captured entry generation | HTTP retained admission, model-only correlation and handoff/publication race tests | implemented |
| Captured old/new settings, failed publication, single shutdown budget | Invocation uses captured maps; shared application client never retires on publication; queue stops on own context close | HTTP configuration capture/failure, execution shutdown, HTTP lifecycle and composed shutdown completion/cutoff | implemented |
| Browser/remote same representation, protected portable round trips | Skill/execution editor state and shared API; format-4 ZIP has no routes payload; existing CRC/digest/size/UTF-8/inventory checks retained | Browser editor/history/import, remote workflow, bundle-v4 and management transfer tests | implemented |
| Remove obsolete contracts; record reset without deleting data | Direct V1/V3 replacement; old properties/types/resources removed; format 3 rejected; plan records loss of colocated accounts/tokens/drafts/history | Fresh storage schemas, bundle rejection and old-save-field test; source/documentation search | implemented |
| Current executable guidance and framework boundary | Examples use executor-backed operations; guides describe invocation errors and normal HTTPS/Caddy ownership; lens/setup/reference corrections above | Architecture checks, packaged guidance/client and JS syntax checks | implemented after documentation fixes |
| Unit-owned verification and precise residual blockers | Tests remain enabled; independent results below; release-only blockers remain assigned to PR 9.3 | Focused and broad independent runs, exact release CLI and version-fixture reproduction | implemented under ticket's explicit intermediate-blocker allowance |

## Active Project Guardrails

- Direct schema and serialized-contract replacement; no compatibility reader,
  adapter, Framework stub or disabled test introduced.
- Architecture enforcement passes for application and tests against the closed
  public Framework API. Standard Spring loader APIs load only a fixed Sidecar
  bootstrap resource, without inspecting/reconstructing operator Config Data.
- Framework retains generation/lifetime authority. The registry allocates no
  parallel versions, owns no HTTP clients and performs no SQL in retirement callbacks.
- Managed credentials stay outside ordinary authored YAML/export and captured
  caller identity stays outside inputs. Management checks remain in shared services.
- No live service, provider, production/development reset, commit, release or
  hosted CI operation was performed. Loopback fixtures and temporary stores only.

## Open Questions and Assumptions

None requiring a developer decision. The installed snapshot is used according
to repository policy, without rebuilding Framework or adding provenance machinery.

## Verification Results

- PASS — `./mvnw.cmd -B -ntp "-Dtest=EffectiveExecutionConfigurationTest,ConfigurationBundleV4Test,HttpConfigurationIntegrationTest,HttpConfigurationShutdownIntegrationTest,SupportedLoomspanApiArchitectureTest,HttpExecutorArchitectureTest" test`: 31 tests, no failures/errors/skips.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`: six tests, rerun after documentation corrections.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`: 11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`: eight tests; these do not launch deployments.
- PASS — `node --check src/main/resources/static/management/assets/editor.js` and `node --check src/main/resources/static/management/assets/console.js`.
- PASS — `git diff --check` and targeted obsolete-contract guidance search after corrections.
- FAIL — `./mvnw.cmd -B -ntp verify`: 300 tests, 299 passed, one failure,
  zero errors/skips. The sole failure is the unchanged
  `ReleasePreparationTest.releasePreparationRejectsSnapshotsAndProducesExpectedArtifactPlan`
  beta.8-only release guard independently reproduced below. All functional,
  browser, security, storage and lifecycle tests passed, including both composed
  shutdown tests. Maven stops before package/verify completion; these phases are
  not claimed. The ticket's PR 9.3 release reconciliation allowance applies.
- FAIL — `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v`: seven tests, one existing error; the temporary annotated-tag fixture retains the snapshot dependency and the release guard rejects it. No real tag was created. PR 9.3 owns release/test-fixture reconciliation.
- FAIL — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.8 --tag v1.0.0-beta.2`: exit 1, `pom.xml does not use the beta 8 framework line`. The required POM is beta.9-SNAPSHOT; unchanged release tooling is assigned to PR 9.3.

## Residual Risks and Optional Developer Checks

HTTP-invalid but Framework-valid configuration may start, publish, restore and
report ready; invocation validates before dispatch. This is the explicit accepted
contract, not an unperformed HTTP preflight guarantee. Release publication/pinning,
hosted CI and composed Docker/SDK recipes are not established by this local review.
The ticket assigns final release reconciliation to PR 9.3. Existing development
databases still require developer-owned replacement because V1/V3 changed; no
reset was performed. No optional manual check is required.

## Disposition

`fixes-applied`. Documentation fixes applied; another fresh Step 5 context must independently
review the resulting work before pipeline completion.
