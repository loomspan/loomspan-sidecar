# HTTP configuration management and authoring Code Review — Cycle 3

## Scope and Repository State

Full-profile pipeline Step 5, cycle 3, against baseline
`736315dc0c67b179b1835f359604ffcf734d998e` on `main`. Reviewed staged,
unstaged and untracked ticket changes. There are no staged changes. Additional
status-only Java/configuration paths have no semantic Git diff. Preserved the
developer's earlier planning changes in the design lens, HTTP notes and related
tickets. No prior review documents were read.

Read the governing ticket, research, implementation/testing plans, repository
guidance and design lens. Reconstructed startup/bootstrap, candidate assembly,
secret scanning, publication/failure, restoration, bundle inventory/integrity,
editing authorization, execution correlation and shutdown. Checked connected
callers/tests, active documentation, packaged references, examples and UI copy.
Completed the initial independent review before editing; internally re-reviewed
the resulting changes. The accepted invocation-only HTTP validation decision
governs review; no new startup accessor or Framework change is required.

## Findings

No remaining actionable findings after the corrections below and internal
re-review. No runtime defect found in this context.

## Findings Resolved in This Context

### [P2] Describe the actual remote-authoring fixture and validation boundary

- Location: `agent-skills/loomspan-sidecar-authoring/examples/remote-authoring/README.md:3` and `:7`.
- Scenario: an author follows the example's claim that Sidecar validates a route,
  expects process URL-variable support, or searches for the obsolete paired route
  manifest despite the example's final invocation-only paragraph.
- Impact: guidance contradicts the current complete skill/execution representation
  and promises HTTP checks that management deliberately does not perform.
- Evidence: `RemoteAuthoringWorkflowIntegrationTest` reads `execution.yaml` and
  replaces its literal `https://records.example.test` with a loopback destination
  before saving. Production HTTP validators are called only by
  `HttpSkillExecutor.execute`.
- Fix: describe skill/execution YAML, actual pre-submission literal replacement,
  Framework management validation and invocation-time HTTP checks; link to target
  configuration instead of removed URL-variable guidance.

### [P2] Point matching-version guidance at the installed Framework checkout

- Locations: `docs/console-setup.md:6`, `docs/architecture.md:27`, `docs/upgrades.md:18`.
- Scenario: the operator follows hardcoded beta.8 skill/Console references while
  the current Sidecar explicitly uses beta.9-SNAPSHOT.
- Impact: the guide selects stale executor/namespace architecture and incorrectly
  identifies the bundled Framework version. This conflicts with the repository's
  matching-checkout workflow.
- Evidence: POM declares beta.9-SNAPSHOT. The cited mental-model, observability,
  installer and Console documents exist in the matching local Framework checkout.
- Fix: identify the current snapshot and direct readers to exact document paths
  in that checkout, using the existing setup instructions. No unpublished release
  tag, compatibility claim or Framework API was invented.

### [P2] Advertise the accepted bundle version on the import page

- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementPagesController.java:374`.
- Scenario: an operator follows the import page's instruction to choose a format-3
  bundle.
- Impact: the strict format-4 reader rejects the advertised input.
- Evidence: `ConfigurationBundleV4.read` accepts only integer version 4 and the
  bundle regression test rejects version 3.
- Fix: change the import-page copy to format 4. No import behavior changed.

Four Markdown files and one UI string changed in this context. No execution,
credential, schema, dependency, release-tool or developer decision was changed.

## Acceptance-Criteria and Plan Conformance

Java paths below are relative to `src/main/java/ai/loomspan/sidecar/`.

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| File/DB startup, invocation-only HTTP checks, no inheritance | Fixed database bootstrap through standard Boot loader; runtime publishes complete public candidates; HTTP validators called only by executor | Configuration modes, composed HTTP acceptance and actual Boot import/profile invocation tests | implemented |
| Coherent validated publication with no probes | Runtime validates/prepares public namespace candidate before durable selection; publication failure discards mapping and reverts selection | Runtime publication/recovery, HTTP-invalid candidate, stale base and injected failure tests | implemented |
| Ownership, lease/revision, permissions, scopes, CSRF, approval | Shared editing services retain exact saved candidate, live account/token and ownership checks; strict route-free Save | Management HTTP/editing/PAT, browser, remote workflow, file restriction, import/rollback suites | implemented |
| Exact restart/credential semantics and confidentiality | Effective configuration partitions provider references, attaches authenticated encrypted records, compares parsed captured content; snapshot omits effective YAML from JSON | Cross-JVM restoration, portability, cipher/scanner and inspection serialization tests | implemented |
| Durable correlation independent of clients | Small synchronized generation registry, stage before activation, retire through public callback, coordinator resolves admitted entry generation | Model-only correlation, handoff/publication race and protected-history tests | implemented |
| Captured old/new work and one shutdown budget | Executor reads captured maps; application-owned client never retires on publication; queue stops on own context close | Captured target/credential, failed publication, queue shutdown, HTTP lifecycle and composed completion/cutoff tests | implemented |
| Browser/remote same representation and protected bundles | Skill/execution editor state, shared endpoints, format-4 skill/execution inventory with optional ciphertext | Browser editor/history/import, remote authoring, bundle and management transfer tests | implemented |
| Obsolete contracts removed; reset decision recorded | Direct V1/V3 schema replacement, removed route types/resources/API fields and strict old-format rejection; internal plan documents shared-store losses | Fresh-store tests, unknown save-field rejection and old bundle rejection | implemented |
| Current guidance and supported Framework boundary | Active docs/examples/UI now describe format 4, invocation-only checks, matching guidance and shared-client ownership | Architecture tests, Python package/client checks, source/context scans and import browser | implemented after narrow guidance/UI fixes |
| Unit-owned assurance and exact permitted blockers | Tests remain enabled; local independent commands recorded below; release-only residuals assigned to PR 9.3 | Broad Maven and Python checks | implemented under explicit intermediate-blocker allowance |

## Active Project Guardrails

- Direct destructive replacement of obsolete contracts; no compatibility readers,
  adapters, Framework stubs or disabled tests.
- Application/test boundary remains `ai.loomspan.api`. Standard Boot loader loads
  only Sidecar's fixed empty bootstrap, not reconstructed operator Config Data.
- Framework retains generation and admitted-lifetime authority; registry allocates
  no parallel versions, owns no client and performs no SQL on retirement.
- Complete database publications have no startup/prior-publication fallback.
  HTTP operational validity is intentionally checked at invocation.
- Infrastructure secrets remain in authenticated encrypted records or ephemeral
  provider values. Effective YAML is excluded from inspection; default export
  carries authored references, explicit authorized export carries ciphertext.
- No production/live-provider operation, actual database reset, commit, tag,
  push, Framework build or release was performed. Test services are loopback and
  databases are temporary.

## Open Questions and Assumptions

No developer decision required. Use installed snapshot and matching checkout per
repository policy. Precisely assigned release tooling/fixture reconciliation
remains with PR 9.3 rather than changing the required development dependency.

## Verification Results

- FAIL — `./mvnw.cmd -B -ntp verify`: **300 tests, 299 passed, one failure,
  zero errors/skips**. The sole failure is unchanged
  `ReleasePreparationTest.releasePreparationRejectsSnapshotsAndProducesExpectedArtifactPlan`.
  All functional, security, browser, storage, architecture, HTTP and lifecycle
  cases passed, including composed startup/capture/restoration and both full
  shutdown scenarios. The exact guard failure is independently reproduced below;
  PR 9.3 owns reconciliation under the ticket allowance. Maven stopped in tests;
  package/repackage/verify lifecycle completion is not claimed.
- PASS — `./mvnw.cmd -B -ntp "-Dtest=ManagementImportBrowserIntegrationTest" test`:
  one browser test, after recompiling the final format-4 UI string. This narrow
  rerun supplements the broad run, whose classes were compiled before that
  copy correction; no runtime behavior changed afterward.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v`:
  six tests, rerun after guidance edits.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`:
  11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`:
  eight orchestration tests, no actual deployment.
- PASS — `node --check src/main/resources/static/management/assets/editor.js`
  and `node --check src/main/resources/static/management/assets/console.js`.
- PASS — `git diff --check` and expanded active source/documentation obsolete
  contract searches, followed by contextual reads of linked guidance/examples.
- FAIL — `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v`:
  seven tests, one unchanged error. The annotated-tag temporary fixture retains
  the snapshot Framework dependency and is rejected with
  `Cannot release with a SNAPSHOT framework dependency`.
- FAIL — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.8 --tag v1.0.0-beta.2`:
  exit 1, `pom.xml does not use the beta 8 framework line`. Independently inspected
  the beta.8-only script/test against required beta.9-SNAPSHOT POM; these paths
  have no ticket changes. Both release-only issues are assigned to PR 9.3.

## Residual Risks and Optional Developer Checks

Readiness/publication may accept Framework-valid but HTTP-invalid configuration;
invocation rejects HTTP domain errors before dispatch. This is intentional, not
an operational preflight guarantee. Existing development SQLite databases require
developer-owned replacement because V1/V3 definitions changed; the shared store's
accounts, tokens, drafts, snapshots and credentials are affected as recorded in
the plan. No reset occurred. No optional manual check is required. Hosted CI,
released-dependency pinning and live composed deployments remain outside local
component evidence and are not represented as passing.

## Disposition

`fixes-applied`. Another fresh Step 5 context must review the resulting work
before pipeline completion because this context corrected implementation artifacts.
