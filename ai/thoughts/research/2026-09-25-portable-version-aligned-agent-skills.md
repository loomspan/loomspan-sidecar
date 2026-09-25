---
date: 2026-09-25
repository: loomspan-sidecar
branch: main
commit: cda9d0c58c7c2d44f3dafdea92ccbf46a4cb04ae
ticket: ai/thoughts/tickets/2026-09-25-portable-version-aligned-agent-skills.md
tags: [agent-skills, portability, version-selection, distribution]
---

# Portable version-aligned Agent Skills Research

## Research Question

What are the current skill sources, installation/version contracts, distribution dependencies, Sidecar portability boundaries and executable coverage relevant to the ticket?

Research checklist completed: skill discovery and ownership; bootstrap selection and mutations; component version sources; Console packaging and validation; Sidecar resources/client/security; existing tests and fixtures; source availability and operational constraints.

## Summary

The framework currently publishes an instruction-only Maven bootstrap plus framework documentation; Console owns the skill currently named `loomspan`. There is no common ecosystem router, Sidecar-aware installer, SDK selector or compatibility-fact convention. Sidecar owns a standard-library Python management client and two short references, but its skill depends on repository documentation and examples outside its folder and has no component version metadata.

The framework checkout is `1.0.0-beta.6-SNAPSHOT`; Sidecar remains `1.0.0-beta.1-SNAPSHOT` with framework dependency `1.0.0-beta.5-SNAPSHOT`. Existing exact-version bootstrap rules cannot select current framework main for that Sidecar pin. Local framework release tag `v1.0.0-beta.5` contains the old bootstrap and Console names, not the new family; Sidecar has no local tags. These are local source observations, not an inventory of remotely published sources. No remote release lookup or installation was performed.

## Repository State

- Observed 2026-09-25 at approximately 10:01 America/Los_Angeles.
- Sidecar: `C:/opendev/code/loomspan-sidecar`, branch `main`, commit `cda9d0c58c7c2d44f3dafdea92ccbf46a4cb04ae`; initial status only the untracked governing ticket.
- Framework: `C:/opendev/code/loomspan-framework`, branch `main`, commit `558adbf18cc5166f681d3352d2e3dccc1d4795bb`; initial status clean.
- The orchestrator recorded the developer's Full 5-Step Pipeline confirmation and baseline scope in the ticket's Execution notes. Profile is `full`; no further gate is pending.
- This step writes only this research artifact. Preserve any unrelated changes introduced during later work.
- Applicable policies: Sidecar `AGENTS.md` and `ai/thoughts/design-lens.md`; framework `AGENTS.md`; framework `loomspan-console/AGENTS.md`. The ticket authorizes replacement of superseded development contracts without compatibility shims; no runtime-data reset is expected or performed. Framework's Java supported-surface restrictions remain unchanged.

## Current Behavior and Data Flow

### Installation

Framework `agent-skills/bootstrap/SKILL.md:1` declares `name: bootstrap`. Lines 8–20 make it a one-shot instruction-only reader using native host capabilities, not an installed helper. It installs only requested `loomspan` and/or `loomspan-docs`.

At `:24`, version detection reads the nearest POM's direct `ai.loomspan:loomspan-spring-boot-starter` dependency, accepting a literal or a single local property. Parent inheritance, BOMs, profiles, chained properties and effective-POM resolution are expressly unsupported. Missing or ambiguous inputs lead to a developer question, not a fallback. At `:43`, releases map to `vX` and snapshots to main only if the root POM matches exactly. At `:56`, fixed paths map Console and docs; each selected source must declare the matching `metadata.loomspan-version` before installation.

At `:68`, host-native installation determines physical placement; unspecified scope currently prompts among supported scopes instead of defaulting to project-local. Existing installations are individually approved before replacement. Sources are validated first, followed by host installation and metadata verification; partial host failures have no rollback contract. Bootstrap does not modify POMs or invoke runtimes. Current coverage does not exercise its behavioral decisions.

### Skill ownership and routing

Framework `agent-skills/loomspan-docs/SKILL.md:1` owns `skill-authoring` and `java-api`, uses progressive reference loading, and routes runtime questions to the old `loomspan` specialist. Its references are already bundled. Source investigation hints do not imply a customer checkout; unavailable matching source is reported explicitly.

Framework `loomspan-console/agent-skills/loomspan/SKILL.md:1` owns read-only runtime evidence through Console MCP. Its six-file bundle includes five references; no server mutation or installer responsibility exists. Missing tools or evidence limit only dependent investigation. Its current compatibility text requires an already configured Console MCP connection for live inspection. No SDK skill sources exist in the inspected skill trees.

Sidecar `agent-skills/loomspan-sidecar-authoring/SKILL.md:8` starts in the customer application's checkout, preserving application-side authentication and per-record authorization. It describes full draft save, server validation, lease handoff and explicit publication. Management tokens, execution JWTs and application credentials remain separate. Console runtime evidence guidance does not own these editing operations.

### Sidecar resource portability and direct API behavior

Sidecar `SKILL.md:10` refers to the matching framework checkout and `docs/integration.md` as further required guidance. Its final example link escapes the bundle. `references/rest-configuration.md:5` repeats framework-checkout and Sidecar-doc assumptions; `:9` links to the external examples directory. `references/client-usage.md:3` uses a repository-relative client invocation and tells customers to copy the skill themselves. The bundled references contain no full execution API setup/polling guide.

The authoritative existing Sidecar operational guide is `docs/integration.md`: runtime configuration at `:25`, REST routes at `:60`, JWT authentication at `:155`, execution API at `:191`, remote authoring at `:234`. Routes permit GET/POST; target auth is none/static/caller-passthrough; URL environment references require the Sidecar allowlist. The execution caller's JWT goes unchanged to a passthrough application, which independently authenticates it and authorizes data. Management bearer tokens do not execute `/v1/**`, and execution JWTs do not manage drafts.

The client `client/sidecar_authoring.py:76` implements HTTP requests; `:126` is its CLI. It requires Python 3.9+ with only the standard library. Credential input is `LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN`; mutation bodies use stdin/files; redirects are rejected; ordinary errors are not retried. Handoff rereads current publication and draft. Import/export are optional configuration-transfer operations, not installers. No client change is required merely to locate it inside an installed folder.

`examples/remote-authoring/record.yaml:1` and `rest-routes.yaml:1` are a complete matching `readRecord` REST manifest and caller-passthrough route. The README describes callback JWT verification and owner denial. The Java walkthrough reads these exact external paths, so moving their canonical location affects test inputs as well as documentation.

## Key Components

Paths below are repository-relative; `framework:` means `C:/opendev/code/loomspan-framework`, otherwise Sidecar.

- `pom.xml:9` / `:17` — independent Sidecar version and framework dependency property; starter dependency uses that property.
- `framework:pom.xml:9` — framework current development version.
- `framework:scripts/loomspan_version.py:18` — exact list of two versioned skills; `:88` compares metadata to root POM; `:166` performs version changes; `:210` creates release tags. These operations are not authorized by this ticket.
- `framework:loomspan-console/internal/agentskills/validate.go:18` — `RuntimeDebuggingSkillName = "loomspan"`; `:25` exact file list; `validateManifest` requires exactly one nonempty resolved `loomspan-version` metadata field. Adding component metadata affects this restriction.
- `framework:loomspan-console/internal/buildtool/package.go:44` — package input path derives from the name constant; `:130` archives the canonical files under `skills/<name>/`.
- `framework:loomspan-console/internal/buildtool/smoke.go:75` — validates extracted skill; `internal/agenteval/cases.go:68` locates canonical Console guidance by the same constant.
- `framework:.github/workflows/console-ci.yml:42` and `console-release.yml:32` — skills-ref validation paths explicitly include old Console and bootstrap names.
- `framework:README.md:248`, `loomspan-console/README.md:423`, `loomspan-console/release/README.md:15` — customer-facing onboarding and archive installation instructions.
- `scripts/prepare-release.py:46` — Sidecar current release output is JAR plus a ZIP containing only that JAR; no skill archive exists.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Common entry point | Name `loomspan` is already occupied by Console; no ecosystem entry point exists. |
| Source selection | Bootstrap selects one framework version from Maven; no Sidecar release-POM or SDK dependency selection exists. |
| Version metadata | Framework docs and Console have `loomspan-version`; Sidecar has none. No explicit Sidecar/framework skill-dependency marker exists. |
| Source availability | Current Sidecar pin is beta.5-SNAPSHOT but framework main is beta.6-SNAPSHOT. Existing release tags contain old contracts. |
| Distribution | Console archive construction, smoke checks, declaration tests, CI and docs refer to old names. Sidecar release script packages runtime JAR only. |
| Portability | Framework docs have a self-containment test; Console has exact-bundle validation; Sidecar resource links escape its skill root. |
| Authorization | Existing operational guides separate management, execution and callback/data authorization; installer has no runtime authority. |
| Updates | Current bootstrap supports explicit replacement but no dependency/target-aware component set or SDK compatibility facts. |

## Existing Tests and Fixtures

Tests were located and read; none were executed during research.

- Framework `scripts/tests/test_bootstrap_distribution.py:9` verifies only that bootstrap contains one `SKILL.md` and no named client-specific/executable dependencies. This is structural evidence, not behavioral installation evidence.
- Framework `scripts/tests/test_loomspan_version.py:31` creates Console/docs fixture directories with current names; tests cover version tooling. Root command pattern: `python -m unittest discover -s scripts/tests`.
- Framework `loomspan-console/internal/agentskills/validate_test.go:11` checks canonical package validity; `:76` rejects unsafe/nonportable variants including linked files/roots. `validate.go` bounds body size, validates exact references, and rejects credential/endpoint/generated-trace content.
- Framework `loomspan-console/internal/buildtool/package_test.go:42` constructs local fixture archives for Windows, Linux and macOS, compares deterministic bytes, exact entries/modes and every packaged skill byte. It uses fixture executables; no native cross-build or publishing is needed for these checks.
- Framework `loomspan-console/internal/buildtool/projectdeclarations_test.go:81` checks official-validator wiring; `:132` checks release/authoring docs; `:179` walks docs Markdown links, rejecting escapes or missing resources; `:251` checks workflow contracts. `internal/agenteval` consumes canonical Console guidance for its existing evaluation inputs.
- Focused Go command locations are the `loomspan-console` module: `go test ./internal/agentskills ./internal/buildtool ./internal/agenteval`. Existing Console guidance also names `go test ./...` and `go run ./internal/buildtool verify` as standard verification; the latter is broader than skill-only validation.
- Official skills-ref is pinned in `loomspan-console/skills-ref-validation/pyproject.toml` and its lockfile to agentskills revision `69ef37e9424c0a7ea9dd2293b559e43ec8176379`; it requires Python >=3.12 and uv. Current commands use `uv run --frozen --project skills-ref-validation skills-ref validate <skill-path>` from the Console module; fetching uncached dependencies can require network.
- Sidecar `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py:48` runs the real CLI against a loopback HTTP fixture. Nine tests cover environment credentials/redaction, handoff rereads, no conflict retry, multipart/export, complete route bodies, permissions, URL/JSON rejection, and redirect credential isolation. Command: `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'`.
- Sidecar `src/test/java/ai/loomspan/sidecar/management/RemoteAuthoringWorkflowIntegrationTest.java:71` pins client and example paths; `:79` exercises malformed draft repair, browser handoff/publication, remote Publish, and allowed/denied callback execution; another test covers competition, expiry, staleness and revocation. It launches the Python client (`:245`) and Chromium via Playwright. It uses local Spring/SQLite/callback fixtures, not live services, but requires Java, Maven dependency availability and installed browser binaries.
- Sidecar `src/test/java/ai/loomspan/sidecar/release/ReleasePreparationTest.java:10` checks release safety wiring and runs prepare-release in validation-only mode. No skill bundle completeness/version test currently exists.
- Sidecar CI `.github/workflows/ci.yml:23` runs browser install, Maven verify and Docker production checks; it does not independently invoke the Python authoring client unittest suite. Hosted CI uses published framework beta.5 whereas local development uses the installed snapshot per repository policy.

## Dependencies and Operational Constraints

Python, Go, uv and Maven commands are discoverable on this host. `Get-Command java` did not resolve; Java/Maven toolchain readiness remains unverified, not a demonstrated build failure. No environment credentials were inspected. No network, runtime mutation, dependency update, release publication, installation or test run occurred.

The ticket explicitly permits local fixtures for unavailable release combinations and future SDK selection, provided they are labeled as fixtures. It forbids upgrades merely to make selection pass, live prerequisites and overwriting published releases. A live Sidecar connection is optional; direct API consumers may have no POM. The existing installer is declarative instruction text, so a structural test passing does not establish the rewritten installer's decisions.

Local framework tags `v1.0.0-beta.1` through `v1.0.0-beta.5` exist. `git ls-tree` confirms beta.5 contains `agent-skills/bootstrap` and `loomspan-console/agent-skills/loomspan`, and lacks the proposed new router and renamed Console locations. Updating current source does not retroactively make those release bundles available. SDK versions and compatibility facts are absent from inspected current skill sources; equal component versions therefore supply no compatibility evidence.

## Historical Context

Sidecar commit `c312150` introduced the complete remote-authoring workflow and the current skill/client/example assets (ticket `PR-6.3-remote-authoring-skill-and-client.md`). Current source remains the authority; the older report is not reused as verification. Framework history shows `3b50284` releasing beta.5 and `cec7863` beginning beta.6 development, explaining the checked-out metadata difference. Historical process documents may mention obsolete names without being current customer-facing distribution contracts.

## Open Questions

No developer decision blocks research. Planning still owns the concrete minimal metadata fields, Sidecar target configuration conventions, future SDK published-fact representation, exact-source validation ordering, and focused behavioral-evidence method for instruction-only installation. Current evidence does not establish remotely available new release bundles, host-specific install-scope support, or compatibility for future SDKs. These limitations must remain explicit; they are not permission to infer versions or expand installation machinery.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-25-portable-version-aligned-agent-skills.md
SUMMARY: Documented current skill ownership, source selection, portability gaps, packaging dependencies and executable coverage across both repositories. Exact-version availability and behavioral-evidence limitations are explicit; no implementation or tests were performed.
DECISIONS:
  - No material developer question is required; design choices remain with planning under the confirmed full profile.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run implementation planning and test planning in their shared fresh context.
