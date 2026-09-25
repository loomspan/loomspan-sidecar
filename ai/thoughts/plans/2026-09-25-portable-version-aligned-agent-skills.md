# Portable, Version-Aligned Agent Skills Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-25-portable-version-aligned-agent-skills.md`
- Research: `ai/thoughts/research/2026-09-25-portable-version-aligned-agent-skills.md`
- Outcome: Customers install independently discoverable, coherent Loomspan skills through their host, selected from their actual Sidecar, framework and optional SDK versions without a Loomspan checkout or live service.
- Execution: Full 5-Step Pipeline (`full`); Step 4 implemented and verified; independent Step 5 review pending.

**Development policy: destructively replace superseded contracts and paths; no compatibility shims, legacy adapters or parallel legacy APIs.** Apply the [Sidecar design lens](../design-lens.md), especially simplicity and technical debt, and the framework lens at `C:/opendev/code/loomspan-framework/ai/thoughts/framework-feature-design-lens.md`. No runtime-data reset is needed or authorized.

Paths prefixed `framework:` below are relative to `C:/opendev/code/loomspan-framework`; other paths are relative to `C:/opendev/code/loomspan-sidecar`.

## Current State

- `framework:agent-skills/bootstrap/SKILL.md` is instruction-only, reads a direct Maven dependency, validates exact sources, and delegates installation to the host. It only selects docs and Console, and asks for scope instead of defaulting to project scope.
- `framework:loomspan-console/agent-skills/loomspan/SKILL.md` occupies the proposed router name. `RuntimeDebuggingSkillName`, `RuntimeDebuggingFiles`, `ValidateRuntimeDebugging`, archive construction, smoke verification and evaluation digests couple to that location.
- `framework:scripts/loomspan_version.py` checks two skills against the framework root POM. Console's `validateManifest` accepts exactly one metadata key today.
- `agent-skills/loomspan-sidecar-authoring/` already contains the real Python client, but required references escape into `docs/integration.md` and `examples/remote-authoring/`. `RemoteAuthoringWorkflowIntegrationTest.EXAMPLE` reads those external YAML files.
- Sidecar is `1.0.0-beta.1-SNAPSHOT`, pinned to framework `1.0.0-beta.5-SNAPSHOT`; framework main and skills are `1.0.0-beta.6-SNAPSHOT`. Existing local beta.5 release sources lack the proposed family. Local observations do not establish remote release availability.
- Both remotes are the official `https://github.com/loomspan/loomspan-sidecar` and `https://github.com/loomspan/loomspan-framework`. Sidecar's release ZIP packages its JAR, not skills; canonical tagged source folders already provide the required distribution mechanism.
- Baseline: Sidecar's ticket and research are pipeline changes; framework was clean. Inspect status again before edits and preserve unrelated work.

## Desired End State

The installed family has a small `loomspan` router, `loomspan-docs`, `loomspan-console`, and, for Sidecar customers, `loomspan-sidecar-authoring`. `loomspan-install` is the instruction-only installation/update entry, available to read directly or invoke when installed by an explicit host-supported choice; it is not automatically added to the customer runtime-guidance set. Specialists remain directly usable and live with their owners.

Sidecar customers select a target Sidecar version independently of the application language. The exact Sidecar source POM selects framework guidance. Embedded Java customers select framework guidance from the application's direct dependency. SDK guidance adds an independent component selected from a real application dependency. Unknown compatibility stays unknown. A router invocation never automatically installs or loads all specialists.

Current source mismatch remains a truthful failure case. Completing this ticket does not make the new skills retroactively available at beta.5, nor prove that a released customer combination is installable. Success fixtures must be labeled synthetic. No dependency, runtime target, release, Java API, HTTP API, authentication protocol or runtime behavior changes.

## Scope
### In scope
- Framework router, Console rename, instruction-only installer replacement, minimal metadata and future SDK ownership conventions.
- Existing Console archive, validator, version tooling, CI and current customer documentation updates.
- Standalone Sidecar bundle, canonical internal examples and targeted Sidecar-owned checks.
- Local fixture validation and focused agent decision evidence for the actual installer instructions.

### Out of scope
- An executable installer/resolver, package manager, installation ledger, client adapters or hard-coded host directories.
- SDK implementations, new compatibility promises, runtime discovery endpoints, dependency upgrades, releasing, tagging or pushing.
- Rewriting historical plans, completed reports or archived evaluation evidence solely to remove old names.
- Adding a Sidecar skill ZIP or changing its JAR packaging: installation consumes complete exact-revision source folders. Console's existing skill archive remains supported and must change coherently.

## Active Project Guardrails

The Sidecar lens keeps framework semantics authoritative, credentials outside model inputs, management/execution identities distinct, draft ownership explicit and clients agent-neutral. Bundled guidance must preserve these rules without imposing UI approval or a specific deployment workflow. The framework lens favors one authority and visible failure over guessed repairs. The public Java surface and sole `RestSkillHandler` SPI are untouched; no production Java change is planned. Console's breaking-path policy permits the intentional name change with all current consumers updated atomically.

Contract classification: Agent Skill names, metadata and routing are deliberately supported author-facing distribution contracts being replaced per this ticket. Repository validators and packaging helpers are internal implementation. Java API/SPI, runtime configuration/manifest syntax, persistence, trace formats and compatibility markers have zero delta. No protected external legacy consumer is identified, and the explicit ticket calls for replacement; therefore no old-name alias. Existing Console runtime compatibility version remains unchanged because this is a skill distribution change, not a runtime protocol change.

## Impact and Risk Analysis

- The old Console name now denotes a different responsibility. Updates must inspect existing installed skills and use the host's replacement/removal authorization; an old Console `loomspan` must not silently become the router. Report a skipped/conflicting installation accurately; never claim a coherent set when an old conflicting name remains.
- Matching component versions are independent evidence, not a compatibility assertion. Exact Sidecar POM resolution is the only framework selector for that path; an SDK cannot override it.
- Source validation must complete for the requested coherent set before host writes. Host operations may fail partially; report per-skill outcomes and remaining work without inventing atomicity or deleting prior installations as rollback.
- Moving canonical examples affects the Java integration test. Running a real client from a detached temporary bundle and exercising the existing walkthrough provides Sidecar evidence rather than borrowing framework test claims.
- Instruction tests cannot prove agent decisions by string presence. A focused scenario replay of the actual instructions is required, alongside objective fixture and bundle checks. This is bounded rewrite evidence, not recurring client certification.

## Implementation Approach

### Minimal metadata and source ownership

Retain `metadata.loomspan-version` as the owning component's exact version and add `metadata.loomspan-component`: `framework` for router/docs/installer, `console` for Console, `sidecar` for Sidecar, and `sdk-<language>` for future SDKs. Console is selected at the framework release version under today's coordinated framework/Console release policy; this does not couple Sidecar or SDK release numbers. Sidecar additionally declares `metadata.loomspan-framework-version`, equal to its actual starter dependency in the source POM. This makes its required matching `loomspan-docs` dependency explicit in metadata and prose. Do not add a redundant installation record or generic dependency schema.

Document conventions in the installer's single `SKILL.md` and the framework onboarding README. Future SDKs publish a real `loomspan-sdk-<language>` folder within their owning source and identify the package/dependency and exact version source in their own guidance. Use explicitly published compatibility statements (source URL/revision, SDK version and supported Sidecar version(s)); a small SDK-owned `references/compatibility.md` table of exact version pairs is the recommended initial form, not a new machine package format. No table, version equality or fixture constitutes a real compatibility promise. Do not create SDK skills now.

### Instruction-only selection and host execution

1. Establish customer path and intended deployment from existing project/deployment information or explicit user choice. Sidecar examples include a version-pinned image tag or project documentation identifying the selected release. Do not mandate a new config file/key, infer from `latest`/an opaque digest, or select between conflicting environments. Ask a focused question for missing/ambiguous input; no live connection is required.
2. Select Sidecar release `X` from official Sidecar tag `vX`; select snapshots from main only if the root POM exactly matches. Resolve that revision once (pin its commit when host source access permits) to avoid moving-main races. Inspect its direct starter dependency, accepting a literal or one local literal property. Unresolved inherited/BOM/profile/property-chain inputs require clarification or an explicit limitation, not a new Maven engine. Embedded Java uses the same deliberately bounded direct-dependency rules on its application POM. Explicit user resolution must be reported as such and must not modify the POM.
3. Resolve the framework exact tag or exact matching main snapshot. Select `agent-skills/loomspan`, `agent-skills/loomspan-docs`, and `loomspan-console/agent-skills/loomspan-console`. Sidecar adds its own `agent-skills/loomspan-sidecar-authoring` at its independently selected revision. Optional SDK guidance uses the application's resolved SDK dependency and its official exact version source. Missing/unresolved SDK versions are not derived from the server.
4. Validate every selected folder, manifest name, component/version markers, Sidecar framework marker and required bundled resources. A missing renamed skill in an old release fails preflight; never replace it with current main or old Console instructions. Inspect published SDK compatibility facts separately: report supported, explicitly incompatible or unknown with provenance. Unknown compatibility does not prevent installing otherwise exact documentation, but must remain a limitation and must never become a supported-runtime claim. An explicitly incompatible selected combination needs developer resolution before installation; do not silently change targets.
5. Default to project-local through the host's supported skill manager. Honor an explicit supported scope. If the host cannot support the requested/default scope or inspect/install exact complete sources, explain the limitation without substituting a user/global scope or manual directory convention. Resolve all sources first, then apply existing host authorization, including already-given explicit update consent. Do not create per-skill confirmation requirements beyond that authorization. Handle obsolete installed Console-name collisions explicitly using supported host management and user authorization; no auto-deletion or compatibility alias.
6. Update only the selected skills. Never edit application dependency/deployment files, connect to runtime as an installation prerequisite, publish configuration, execute a skill or install dependencies. Verify installed markers when the host exposes them; distinguish planned, completed, skipped, failed and unverifiable outcomes. Report selected component versions, exact sources, scope, compatibility evidence/limitations and any host refresh requirement. On partial failure stop further unsafe work and report actual results; do not promise transactional rollback.

### Portable Sidecar bundle

Move the canonical `examples/remote-authoring/{README.md,record.yaml,rest-routes.yaml}` into `agent-skills/loomspan-sidecar-authoring/examples/remote-authoring/`. Update all current links and `RemoteAuthoringWorkflowIntegrationTest.EXAMPLE`; do not keep duplicate YAML.

Put the customer-required operational portions of `docs/integration.md` into `agent-skills/loomspan-sidecar-authoring/references/integration.md` as their canonical home: runtime configuration, routes, JWTs, execution requests/polling/errors, remote authoring and relevant retention/diagnostic limits. Keep `docs/integration.md` as repository onboarding/navigation to the canonical sections plus repository-specific deployment links. Do not mirror operational prose. The bundled guide must not require those repository-only links; use the bundled record example and explicit installed `loomspan-docs` dependency. Preserve supported semantics verbatim where practical. Client commands resolve `client/sidecar_authoring.py` relative to the host-provided installed skill root, without changing the customer's working directory assumptions or inventing host paths. SDK routing assigns language/application lifecycle, authentication integration, request context and callbacks to SDK guidance; Sidecar owns server setup. Direct API integration remains complete without SDKs.

## Phase 1: Establish the family and installer contract

### Changes
- [x] `framework:agent-skills/loomspan/SKILL.md` — create compact orientation/router with focused names and no automatic installations; add metadata.
- [x] Replace `framework:agent-skills/bootstrap/SKILL.md` with `framework:agent-skills/loomspan-install/SKILL.md` — implement the six selection/installation steps above; remain instruction-only.
- [x] Rename `framework:loomspan-console/agent-skills/loomspan/` to `loomspan-console/agent-skills/loomspan-console/`; preserve its six-file evidence workflow and rename manifest/routing text.
- [x] `framework:agent-skills/loomspan-docs/SKILL.md`, `references/skill-authoring/README.md`, `references/skill-authoring/traces-and-debugging.md` — update Console references and ownership metadata without changing framework semantics.
- [x] `framework:scripts/loomspan_version.py` — extend `VERSIONED_SKILLS` for router/installer and renamed Console; keep framework-only consistency scope. Update `framework:scripts/tests/test_loomspan_version.py` fixture construction and assertions.
- [x] Replace `framework:scripts/tests/test_bootstrap_distribution.py` with `test_install_distribution.py`; add local scenarios under `framework:scripts/tests/fixtures/skill-install/` and fixture/metadata checks in `framework:scripts/tests/test_skill_install_contract.py`. Fixtures contain project inputs, exact release POMs/skill manifests and separately held expected decisions, not a production resolver.
- [x] `framework:README.md` — replace bootstrap onboarding with Sidecar-first and embedded-Java paths, component/source table, update/scope behavior and explicit current-release availability limitation.

### Automated verification
- [x] From framework root: `python -m unittest discover -s scripts/tests -v` — new distribution/metadata/fixture checks and version-tool regression tests pass.
- [x] From framework root: `python scripts/loomspan_version.py check` — all framework-owned markers equal unchanged root version. Do not run `set` or `tag` on either working repository.

### Optional developer checks
- None; instruction behavior is a required focused evaluation in Phase 3, not an optional manual substitute.

## Phase 2: Make Sidecar standalone and distribution consumers coherent

### Changes
- [x] `agent-skills/loomspan-sidecar-authoring/SKILL.md`, `references/client-usage.md`, `references/rest-configuration.md`, new `references/integration.md`, new `examples/remote-authoring/{README.md,record.yaml,rest-routes.yaml}` — implement the portability and ownership rules above.
- [x] `docs/integration.md`, `README.md` — link canonical bundled content and document installation, current exact-source limitation and future SDK ownership. Remove old canonical `examples/remote-authoring/` assets after all references move.
- [x] `src/test/java/ai/loomspan/sidecar/management/RemoteAuthoringWorkflowIntegrationTest.java` — change `EXAMPLE` to the bundled path; retain allowed/denied callback and publication coverage, with no production runtime edits.
- [x] `scripts/test_agent_skills.py` — add dependency-free unittest checks for metadata against Sidecar POM, detached bundle closure, examples and real client invocation from a temporary standalone copy. Reject escaping/missing resources and linked bundle assets; exclude generated caches from distribution expectations.
- [x] `.github/workflows/ci.yml` — add the Sidecar bundle unittest command and existing client suite; retain its publication-dependent framework policy unchanged.
- [x] `framework:loomspan-console/internal/agentskills/validate.go` and `validate_test.go` — change `RuntimeDebuggingSkillName`, accept exactly the planned Console metadata with resolved version, keep complete-file and unsafe-link validation.
- [x] `framework:loomspan-console/internal/buildtool/package_test.go`, `projectdeclarations_test.go`, `smoke.go`, `framework:loomspan-console/internal/agenteval/cases.go` and affected tests — update literals where present; constant-based paths should continue to work. Preserve archive determinism, exact bytes and evaluation digest semantics.
- [x] `framework:.github/workflows/console-ci.yml`, `console-release.yml`, `framework:loomspan-console/README.md`, `loomspan-console/release/README.md` — validate four framework skill folders and document `skills/loomspan-console/`. Update other active references found by scoped searches; leave archived evidence intact.

### Automated verification
- [x] Sidecar: `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` and `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — bundle and actual client checks pass independently of framework tests.
- [x] Sidecar: `.\mvnw.cmd -B -ntp test '-Dtest=RemoteAuthoringWorkflowIntegrationTest'` — existing integration consumes relocated examples and still exercises publication and allowed/denied execution on local fixtures.
- [x] Framework Console: `go test ./internal/agentskills ./internal/buildtool ./internal/agenteval` — renamed archive, safety validation, active declarations and evaluation consumers pass.
- [x] Pinned skills-ref commands listed in the testing plan validate each actual folder; no new lockfile/tool version.

### Optional developer checks
- Native host installation is nonblocking; any unavailable host scope must be described, not emulated through hard-coded directories.

## Phase 3: Demonstrate installer decisions and close acceptance evidence

### Changes
- [x] `framework:scripts/tests/fixtures/skill-install/README.md` — document sanitized scenario inputs, expected host capabilities, test-only source mapping and replay procedure. No real host installation or network services.
- [x] `ai/thoughts/validation/2026-09-25-portable-version-aligned-agent-skills.md` — record focused actual-agent decisions from reading the rewritten installer and local scenario inputs: chosen paths/versions, questions, mutation requests, scope and limitations. Separate observations from expected rubric and note instruction digest/date, environment and test-only releases/SDKs. A bounded fresh pipeline agent may perform the replay; if done by the implementing agent, disclose that limitation and have Step 5 independently examine it. Do not substitute a hand-coded selector or a written expected answer for observed agent behavior.
- [x] The same evidence artifact records router probes and detached Sidecar client results, acceptance mapping and real source availability limits. Make no real compatibility or publication claims from fixtures.

### Automated verification
- [x] Run all Python and focused Go commands above, then framework Console `go test ./...` as the broad safe related Go suite.
- [x] Evaluate the observed replay against the scenario rubric in the testing plan; any wrong version, guessed compatibility, unauthorized mutation or silent scope fallback fails acceptance and requires instruction correction and affected-scenario replay.
- [x] `git diff --check` in each repository; inspect status/diff to confirm unchanged runtime sources, dependency versions, deployment configuration, release artifacts and historical evidence.

### Optional developer checks
- A later exact released-family install through a chosen host can substantiate actual publication/host integration. It is unavailable-release follow-up, not a claim required or made by this source change.

## Test Strategy

Use Python unittest for metadata/bundle fixtures, existing Console Go tests for affected distribution, a detached real Sidecar client test for portability, and the existing local Java walkthrough for moved assets. Use a bounded actual-agent decision replay for the instruction-only installer's selection/routing behavior. Do not add a duplicate executable installer just to make it testable. The detailed scenario matrix and commands are in the paired testing plan.

## Acceptance-Criteria Traceability
| Acceptance criterion (ticket order) | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| 1. Common entry, specialist names and consistent replacement | Router, installer, Console folder and active docs/CI/archive paths | Distribution unittest; Console validator/archive tests; router replay |
| 2. Non-Java direct API selects exact Sidecar/framework | Installer Sidecar selection and release-POM preflight | No-POM, offline Sidecar fixture replay; detached Sidecar bundle checks |
| 3. Embedded Java exact dependency or explicit uncertainty | Installer bounded Maven rules | Literal/property, unresolved, ambiguous and unavailable-source replays |
| 4. Independent SDK/version facts | Installer SDK convention and routing; Sidecar SDK prose | Synthetic SDK independently versioned, known/unknown/incompatible fact scenarios |
| 5. Project default and explicit host scope | Installer host workflow | Default, explicit scope and unsupported-scope replay |
| 6. Update scope and truthful reporting | Installer preflight/update/failure handling | Authorized update, declined/colliding old name, partial host failure and read-only project evidence |
| 7. Complete standalone Sidecar/direct API bundle | Bundled integration/examples/client plus explicit docs dependency | Detached link closure, actual client tests and relocated Java walkthrough |
| 8. Ownership and authorization routing | All specialist/router descriptions and portable credential guidance | Focused router/security probes; existing client credential isolation and allowed/denied workflow |
| 9. Proportionate local evidence | Python fixtures, Go checks, Sidecar tests and replay record | All required commands plus observed decision rubric; no client matrix |

## Risks and Rollback/Recovery

Exact new-family sources for existing releases may not exist. Fail preflight and report the missing source; do not update a release tag or dependency to manufacture success. The current Sidecar/framework mismatch is an expected limitation. No installation, publication or runtime mutation happens during implementation tests. If reverting this source change, revert only ticket-owned edits in both repositories while preserving unrelated work. No schema or data rollback is involved. Host partial installation recovery belongs to its supported management operations under user authorization, not a new Loomspan rollback engine.

## References
- Ticket, research and paired `2026-09-25-portable-version-aligned-agent-skills-testing.md` in this plan directory.
- Sidecar `AGENTS.md`, `ai/thoughts/design-lens.md`, `docs/integration.md`, `pom.xml`.
- Framework `AGENTS.md`, `loomspan-console/AGENTS.md`, `ai/thoughts/framework-feature-design-lens.md`, `pom.xml`.
- Existing symbols: `VERSIONED_SKILLS`, `RuntimeDebuggingSkillName`, `ValidateRuntimeDebugging`, `RuntimeSkillDigest`, `RemoteAuthoringWorkflowIntegrationTest.EXAMPLE`.

## Implementation decisions and observed evidence (2026-09-25)

All scoped phases implemented. [Verification record](../validation/2026-09-25-portable-version-aligned-agent-skills.md) records exact commands, initial red failures, final results and criterion mapping. [Independent instruction replay](../validation/2026-09-25-portable-version-aligned-agent-skills-replay.md) records actual R1–R11 choices, source paths, instruction digests and unchanged project hashes; it was performed without reading the expected rubric.

Routine adaptations: updated additional active `docs/operations.md` route link and framework `loomspan-console/docs/mcp-contract-verification.md`/Java observation guide references found by scoped searches. Constant-based package/archive/evaluation consumers needed no source edits and their tests passed. Ignored old bootstrap subdirectories were preserved; the obsolete tracked manifest is removed. Console README LF was restored after its parsing test exposed an edit-induced CRLF issue. No runtime code, dependency version, published source or deployment target changed.

Observed acceptance mapping replaces the planned test expectations above: criteria 1–9 map to the verification record's numbered mapping, its exact passing automated commands and R1–R11 comparisons. Sidecar bundle 5 tests, existing client 9 tests and real Java workflow 2 tests all pass; framework Python 13 tests, four-skill version check, focused/full Go suites and five pinned skills-ref validations pass. The skill-creator supplementary helper could not import optional PyYAML; pinned official validation supplies the required structural evidence without adding a dependency. No instruction defect required candidate revision after the independent replay.

Real-host installation and published new-family availability remain optional follow-up; simulated host outcomes and synthetic SDK facts are never claimed as real release/host evidence. Independent Step 5 review determines final pipeline completion.
