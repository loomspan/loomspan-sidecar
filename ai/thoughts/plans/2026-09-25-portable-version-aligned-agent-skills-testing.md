# Portable, Version-Aligned Agent Skills Testing Plan

## Change Summary

The family gains a router and Sidecar-aware instruction-only installer, renames Console guidance, establishes independent component metadata and makes the Sidecar folder self-contained. Runtime code, dependency versions and published artifacts remain unchanged. This plan follows the paired implementation plan and governing ticket `ai/thoughts/tickets/2026-09-25-portable-version-aligned-agent-skills.md`.

**Destructively replace superseded development contracts; no compatibility shims or legacy paths.** Apply the [design lens](../design-lens.md). No development-data reset is required. `framework:` paths below resolve under `C:/opendev/code/loomspan-framework`; other paths resolve under the Sidecar root.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Selection | Framework latest/SDK version accidentally replaces exact Sidecar POM pin | Differently versioned release fixtures and observed installer decisions |
| Source availability | Current beta.6 source is falsely presented as beta.5 guidance | Missing-source and snapshot mismatch replay; explicit fixture-only limitation |
| Host operations | Scope fallback, overwriting or project/runtime mutation | Host-capability/update/failure replay with action trace and immutable project inputs |
| Portability | Required link or CLI path relies on checkout | Detached bundle validation and real CLI requests from unrelated cwd |
| Distribution | Archive includes old Console name or incomplete bytes | Existing Go exact-entry/determinism tests with renamed path |
| Authority | Router expands rights or mixes application/server credentials | Routing probes, client suite and existing Sidecar workflow |
| Evidence | Literal string tests or simulated resolver mistaken for actual instruction behavior | Separate structural assertions and actual-agent replay record |

## Existing Coverage and Environment Constraints

Read and retain framework `scripts/tests/test_loomspan_version.py`, replace the narrow `test_bootstrap_distribution.py`, and reuse Console `internal/agentskills`, `internal/buildtool` and `internal/agenteval` tests. `package_test.go` already checks deterministic fixture archives on three supported targets without building native binaries. `projectdeclarations_test.go` checks current docs/workflow wiring and framework docs closure.

Sidecar's existing nine Python CLI tests use loopback HTTP and sanitized tokens. `RemoteAuthoringWorkflowIntegrationTest` reads the canonical YAML and uses local Spring/SQLite/callback/Chromium fixtures. Moving its example path warrants that test; Java production changes and a full framework Maven build are not warranted. `ReleasePreparationTest` is not affected because Sidecar JAR release packaging remains unchanged.

Python, Go, Maven and uv were discoverable during research. Java was not on the observed command path; this is unverified setup, not a known test failure. Step 4 should inspect the existing configured toolchain, use available Java 21 and cached/local Maven dependencies, and report concrete blockers if missing. Do not build/install the framework, change its dependency or replace its snapshot to make tests pass. Chromium must already be available or installed through the repository's standard local Playwright setup. No credentials or live Sidecar/Console are required. Official skills-ref uses the existing pinned uv project and can require a one-time dependency fetch; record a network/cache limitation if encountered. Hosted Sidecar CI remains deferred until its dependency publication per repository policy.

## Failing Test First
- Name: `test_sidecar_bundle_is_self_contained`.
- Type: filesystem contract and detached CLI boundary.
- Location: new `scripts/test_agent_skills.py`.
- Arrange/Act/Assert: copy only `agent-skills/loomspan-sidecar-authoring/` to a temporary root; resolve every required local Markdown/resource reference relative to its declaring file, ensure it stays within that root and exists; assert declared component/framework versions match the unmodified source POM before copy. Current escape links and absent metadata must fail. Inspect both Markdown links and explicitly required backtick resource paths; do not treat external informational URLs or an explicit skill dependency as local resources.
- Expected pre-fix failure: missing component/version metadata and escaping `../../examples/remote-authoring/` / repository-document dependencies, not an unavailable Java or network dependency.
- Also run a baseline no-POM Sidecar instruction replay against the old bootstrap before replacement: it cannot derive Sidecar release/framework selections. Record actual failure/limitation rather than inventing an expected transcript. This demonstrates the new behavioral capability independently of text checks.

## Tests to Add or Update

### 1. `InstallDistributionTest` and metadata/fixture contract checks
- Type: Python standard-library unittest.
- Locations: `framework:scripts/tests/test_install_distribution.py`, `test_skill_install_contract.py`, fixtures under `framework:scripts/tests/fixtures/skill-install/`.
- Proves: installer remains instruction-only with exactly `SKILL.md`; router has focused routes and no automatic installer behavior; canonical names exist, old source paths are absent, current metadata has exact component identities and versions. Version tooling covers every framework-owned skill.
- Inputs: real current skill sources; sanitized fixture POMs for direct literal and same-POM-property forms; fixture Sidecar release, framework release and SDK skill sources at deliberately different versions (for example Sidecar `2.3.0`, framework `4.5.0`, SDK `7.8.0`, all explicitly test-only).
- Isolation: fixture directories represent official exact revisions through a documented test-only mapping; nothing fetches or installs them in a real host. SDK metadata/source is a fixture, not a shipped empty SDK implementation.
- Edges: conflicting project target declarations; unresolved versions; missing complete source folder; wrong component/version/framework marker; main declaring a different snapshot. Fixture checks establish input integrity and expected source relationships; they do not claim to execute the Markdown installer.

### 2. `VersionCommandTest` and Console distribution tests
- Type: existing Python/Go suites.
- Locations: `framework:scripts/tests/test_loomspan_version.py`; `framework:loomspan-console/internal/agentskills/validate_test.go`; `internal/buildtool/package_test.go`, `projectdeclarations_test.go`; affected `internal/agenteval` tests.
- Proves: updated `VERSIONED_SKILLS` works in temporary git fixtures; exact Console name/component metadata validates; invalid metadata and links still fail; all native fixture archives contain canonical six files only at `skills/loomspan-console/`, byte-for-byte and deterministically; docs/CI validate the four framework skills.
- Edges: old skill name rejected, unresolved/wrong/extra metadata rejected according to the planned exact Console contract; existing nonregular/linked file and credential-content checks remain intact. Archived evaluation receipts are not rewritten or treated as current names.

### 3. `SidecarAgentSkillsTest`
- Type: Python filesystem/black-box tests.
- Location: `scripts/test_agent_skills.py`.
- Proves: Sidecar and framework metadata derive independently from root POM project version and starter dependency, both match exactly; all required assets resolve after detaching the single folder; examples exist only at the canonical bundled path and name a matching route/skill; a wrong/missing marker or escaping/missing resource is rejected.
- Inputs: temporary copy of real bundle; mutated temporary copies for negative cases. Preserve UTF-8 and avoid generated `__pycache__` in expected distribution files. Do not copy repository docs/framework sources into the temporary environment.
- CLI isolation: launch the copied `client/sidecar_authoring.py` by absolute path from an unrelated temporary cwd, with fake management token and loopback server. Assert an actual `current` or `draft` request and decoded response, not just `--help`; ensure token is absent from stdout/stderr. Reuse the existing client test conventions without adding a runtime dependency or changing production client behavior solely for testing.
- Edges: client path with spaces; local links normalized and confined; linked assets rejected where filesystem link support exists (record platform skips); explicit `loomspan-docs` dependency is by name/version, never a local filesystem escape.

### 4. Existing Sidecar client and workflow regression
- Type: existing Python loopback suite and Java local integration.
- Locations: `agent-skills/loomspan-sidecar-authoring/client/test_sidecar_authoring.py`; `src/test/java/ai/loomspan/sidecar/management/RemoteAuthoringWorkflowIntegrationTest.java`.
- Proves: client redaction, grant handoff rereads, no automatic conflict retries, request bodies and redirect isolation remain correct; the actual moved YAML still supports malformed-draft repair, publish, allowed callback and denied data ownership in the established Java walkthrough.
- Isolation: test SQLite, loopback services, fixture tokens and local browser only. No new test-owned framework internals or Java skills. Existing production snapshots/data are untouched.

### 5. Focused installer decision replay (required during rewrite)
- Type: actual agent reads and follows the real candidate `loomspan-install/SKILL.md` against local input/source fixtures, with host operations represented as explicit proposed calls and supplied results. This is instruction behavior evidence; it is not native-host integration or a general resolver implementation.
- Inputs: `framework:scripts/tests/fixtures/skill-install/README.md` and scenario files. Keep the expected rubric separate from the replay input. For each case instruct the agent to inspect the actual files, state selected versions/sources, necessary questions, ordered requested host operations, scope and final outcome. Provide only scenario host capabilities/results; do not give it the expected answer as an instruction.
- Execution: Step 4 can perform the replay directly in its authorized agent context and disclose that it authored the candidate. It must preserve actual intermediate decisions and inspected paths, not reconstruct a success narrative from expected outputs. Step 5 independently compares the record with the instructions and fixtures and reruns any ambiguous case. A separately delegated replay is optional only within already-authorized pipeline delegation; do not create user-owned tasks or require new client credentials.
- Evidence location: `ai/thoughts/validation/2026-09-25-portable-version-aligned-agent-skills.md`. Record date, agent/context, candidate SHA-256, exact scenario input paths, observed choices/actions/questions, supplied simulated host outcomes, pass/fail against the rubric and limitations. After instruction changes, replay affected cases against the final digest; no unexplained stale candidate evidence.
- These are mandatory observable checks, not optional developer checks. Automated fixture/metadata tests cannot replace them. No live installation, execution or publication is allowed during replay.

| Case | Scenario input | Objective expected decision/action |
| --- | --- | --- |
| R1 | Non-Java project, explicit Sidecar `2.3.0`, no POM/server, exact Sidecar POM pins framework `4.5.0` | Select router/docs/Console at framework `4.5.0` plus Sidecar authoring `2.3.0`; project scope; no server or local-source-checkout requirement |
| R2 | Java direct literal dependency; variant with same-POM literal property | Select exactly router/docs/Console at the resolved version; no Sidecar skill |
| R3 | Absent/conflicting Sidecar target; Java inherited/BOM/chained or ambiguous dependency | Ask for exact unresolved selection; zero install requests, no guessed/latest value or file edit |
| R4 | Missing renamed Console/router at selected old release; variant wrong metadata or main snapshot mismatch | Preflight fails before all host writes; identify absent/mismatched exact source; no old-name/current-main fallback |
| R5 | R1 plus SDK `7.8.0` and published exact compatibility fact | Add only SDK `7.8.0` skill from its owning exact source; retain Sidecar/framework versions and cite fact source |
| R6 | SDK source exists, but compatibility fact missing; variant explicitly incompatible | Unknown remains explicit without inferred support; exact docs may install. Explicit incompatible combination asks for resolution before writes |
| R7 | Default scope; explicit user scope; project scope unsupported by host | Project default, honor explicit supported choice; unsupported project default reports limitation with zero silent global installs |
| R8 | Authorized update with installed older skills; variant replacement declined or legacy Console named `loomspan` | Respect actual authorization and skips; identify old-name collision, use only authorized host management; no dependency/runtime/deployment edits and no coherent-success claim with conflict |
| R9 | Validated sources, host fails on second install; variant post-install marker unreadable | Report actual completed/failed/unverified outcomes, no claim of rollback/atomicity/full success and no unauthorized cleanup |
| R10 | Router prompts for runtime investigation, Java semantics, Sidecar direct API and SDK/application callback integration; missing specialist variant | Route to Console/docs/Sidecar/SDK ownership respectively, only needed specialist, no automatic install; unavailable skill is explicit |
| R11 | Request conflates management token, execution JWT and callback data authorization | Keep three boundaries separate, no management token for execution/callback, no new authority from guidance; SDK protocol not invented |

Record file hashes or a before/after diff of fixture project/deployment inputs across replay; no application modification is acceptable. Simulated host results are labeled as such. This is one focused rewrite evaluation, not a recurring multi-client certification matrix.

## Safe Verification Commands

Commands are proposed here, not already executed. New unittest files below are implementation deliverables.

From Sidecar root:

```powershell
python -m unittest discover -s scripts -p 'test_agent_skills.py' -v
python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v
.\mvnw.cmd -B -ntp test '-Dtest=RemoteAuthoringWorkflowIntegrationTest'
git diff --check
```

From framework root:

```powershell
python -m unittest discover -s scripts/tests -v
python scripts/loomspan_version.py check
git diff --check
```

From framework `loomspan-console`:

```powershell
go test ./internal/agentskills ./internal/buildtool ./internal/agenteval
go test ./...
uv run --frozen --project skills-ref-validation skills-ref validate ./agent-skills/loomspan-console
uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan-docs
uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan
uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan-install
uv run --frozen --project skills-ref-validation skills-ref validate ../../loomspan-sidecar/agent-skills/loomspan-sidecar-authoring
```

The focused suites plus full Console Go suite are the broadest safe relevant automated checks. Full Maven runtime suites, Docker, native binary `buildtool verify`, race certification and publication are not required for instruction/path-only changes. If implementation expands into production behavior, reassess and add corresponding repository-required checks rather than silently relying on this boundary. The Java walkthrough is required because it consumes the moved fixture. If unavailable tooling blocks it, report NOT RUN with exact reason/residual risk; do not claim complete runtime walkthrough evidence from Python mocks.

Use `Get-FileHash <actual-installer-path> -Algorithm SHA256` to identify the replayed instructions; follow the fixture README replay protocol and persist observed results. There is intentionally no shell command that pretends to execute arbitrary Markdown semantics or installs skills. Search active references and inspect both diffs for stale old paths, excluding historical audit records. No `set`/`tag`, publishing workflows, global skill writes or deployment commands are safe verification steps.

## Optional Developer Checks
- Once exact new-family released sources exist, install through a developer-selected real host and inspect native discovery/scope/refresh behavior. This check is nonblocking and must not be represented as completed by the fixture replay.
- Existing SDK release compatibility remains future work for SDK owners; no real compatibility is established by the synthetic SDK fixture.

## Exit Criteria
- [x] The initial detached-bundle test and baseline instruction scenario fail for the intended missing behavior before implementation.
- [x] Framework metadata/distribution/version tests and Console archive/validator tests pass with final names and unchanged release versions.
- [x] Sidecar metadata/closure/detached-client tests and existing client suite pass independently.
- [x] The existing Java workflow consumes bundled YAML successfully, or the exact environment blocker and residual unverified behavior are surfaced in the Step Report without a false pass.
- [x] Actual instruction replay covers R1-R11 against the final candidate, with source paths and observed actions; any failed behavior is corrected and rerun.
- [x] Each acceptance criterion is mapped in the implementation plan to the corresponding executable tests and instruction observations; structural-only evidence is not presented as behavioral proof.
- [x] No test performs real host installation, unintended network/runtime mutations, dependency upgrades or publication; fixture-only sources/SDKs are clearly identified.
- [x] Existing beta.5/beta.6 source mismatch and lack of retroactively renamed released skills remain explicit limitations.
- [x] Optional host/release checks are reported as nonblocking and not already performed.

## Execution evidence

All exit criteria are backed by [the verification record](../validation/2026-09-25-portable-version-aligned-agent-skills.md) and its linked independent actual instruction replay. The relocated Java workflow ran successfully (2 tests), with no environment blocker. Pinned skills-ref validated all five real folders. The optional skill-creator helper could not import PyYAML; no dependency was added. See the record for initial red failures and the corrected Console README LF regression. Step 5 remains pending.
