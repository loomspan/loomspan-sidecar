# Portable version-aligned skills — implementation verification

Date: 2026-09-25. Step 4, Full 5-Step Pipeline. Both repositories were inspected
before edits: framework clean; Sidecar contained only the ticket and preceding
pipeline artifacts. No dependency versions, runtime Java implementation, deployment
targets, release tags or published artifacts were changed. No data reset occurred.

## Red evidence

`python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` initially
ran five tests and failed two. `test_sidecar_bundle_is_self_contained` reported
missing `loomspan-component`, `loomspan-version`, and `loomspan-framework-version`,
plus the escaping example and repository-document references. The canonical
example test failed because examples still lived outside the bundle. The real
detached client request already passed; it was never claimed as new behavior.

Independent agent `/root/implementation/baseline_replay` read the original
bootstrap in full before replacement. SHA256:
`BE09065E225A8C7023A1EB32CC218268FF699A9E2A9E66C4B725076C8CAF7FB1`.
Its synthetic request was a non-Java direct API project targeting Sidecar 2.3.0,
without application POM/server; simulated source access offered that Sidecar
release's framework 4.5.0 dependency. Following the old instructions, the agent
stopped on absent POM, proposed listing framework release tags/main, and asked
which exact framework version and which of the two supported skills to install.
It did not request the Sidecar POM because old bootstrap provided no such step.
Zero install calls occurred. This is the observed baseline capability failure,
not a claim that a real host installation was attempted.

## Automated results

All commands below ran on this Windows workstation. Tests use temporary/local
fixtures; Java used the existing local framework snapshot and Java 21.0.2 through
the configured Maven wrapper. No framework rebuild/install was performed.

| Working directory | Exact command | Actual result |
| --- | --- | --- |
| Sidecar | `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` | PASS, 5 tests; final run includes wrong/missing markers and escaped/missing resource mutations, symlink rejection (no skip), relocated examples, detached real client request from unrelated cwd with spaces |
| Sidecar | `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` | PASS, 9 tests; loopback authentication/redaction, handoff, denied-write behavior and redirect isolation |
| Sidecar | `.\mvnw.cmd -B -ntp test '-Dtest=RemoteAuthoringWorkflowIntegrationTest'` | PASS, 2 tests, no failures/errors/skips; actual bundled YAML, browser and remote publication, allowed/denied callback access; 55.838 seconds build time |
| Framework | `python -m unittest discover -s scripts/tests -v` | PASS, 13 tests; instruction-only distribution, current metadata, synthetic input integrity and version-command regressions in temporary Git repositories |
| Framework | `python scripts/loomspan_version.py check` | PASS, unchanged 1.0.0-beta.6-SNAPSHOT across all four framework-owned skills |
| Framework Console | `go test ./internal/agentskills ./internal/buildtool ./internal/agenteval` | PASS; renamed six-file package, exact metadata, unsafe variants, deterministic archive bytes and evaluation consumers |
| Framework Console | `go test ./...` | PASS after fixing edit-introduced CRLF in Console README. First run failed `TestREADMEConfigurationExampleParses` because its LF fence was missing; no production behavior defect. |
| Framework Console | `go test ./internal/buildtool ./internal/config` | PASS after final current-doc/version wording and documentation assertion changes |
| Framework Console | `uv run --frozen --project skills-ref-validation skills-ref validate ./agent-skills/loomspan-console` | PASS |
| Framework Console | `uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan-docs` | PASS |
| Framework Console | `uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan` | PASS |
| Framework Console | `uv run --frozen --project skills-ref-validation skills-ref validate ../agent-skills/loomspan-install` | PASS |
| Framework Console | `uv run --frozen --project skills-ref-validation skills-ref validate ../../loomspan-sidecar/agent-skills/loomspan-sidecar-authoring` | PASS |
| Both repositories | `git diff --check` | PASS |

Supplementary skill-creator validation command
`python C:/Users/mgiacomi/.codex/skills/.system/skill-creator/scripts/quick_validate.py <skill-folder>`
was attempted for all five folders and failed before validation with
`ModuleNotFoundError: No module named 'yaml'`. Retrying the installer via the
existing frozen uv project produced the same missing optional dependency.
No dependency was installed just for that helper; pinned official skills-ref
success above supplies frontmatter/skill-standard validation instead. This does
not substitute for behavioral replay.

NOT RUN by planned scope: full Maven runtime suites, Docker, native binary
`buildtool verify`, race certification, live host installation and publication.
Hosted Sidecar CI remains publication-dependent. Python version tests create
temporary fixture tags only; neither working repository was set/tagged.

## Actual instruction replay and rubric comparison

Fresh independent agent `/root/implementation/candidate_replay` followed the
actual installer and fixture README/scenarios without reading expected.json,
ticket, plans or research. Its complete observed decisions, inspected paths,
simulated ordered host calls/results and before/after hashes are preserved in
[the replay record](2026-09-25-portable-version-aligned-agent-skills-replay.md).
The implementing agent compared that record against the testing plan afterward.

Installer SHA256, verified unchanged after replay:
`5FD649D58DF049762D898459D1E625D4EA2D5FBBD60B4B0B0E249AFD638D0A1F`.
The replay also hashes actual router/Sidecar guidance and all 13 fixture project
files, unchanged. Fixture root is
`C:/opendev/code/loomspan-framework/scripts/tests/fixtures/skill-install/`.

| Rubric | Comparison of actual observations | Result |
| --- | --- | --- |
| R1 | No-POM Sidecar target selects 2.3.0 authoring and framework/Console 4.5.0 trio; project scope; no service prerequisite | PASS |
| R2a/b | Literal and same-POM-property Java inputs select only framework 4.5.0 trio | PASS |
| R3a–f | Absent/conflicting target, chain/inheritance/BOM/duplicate dependency inputs produce focused questions and no writes | PASS |
| R4a–c | Missing old-release folders, wrong component marker and main snapshot mismatch stop whole preflight without fallback | PASS |
| R5/R6a/b | SDK 7.8.0 selected independently; exact synthetic fact cited; absent fact stays unknown; explicit incompatible override blocks writes | PASS |
| R7a/b | Explicit user scope honored; unsupported project default stops with limitation | PASS |
| R8a–c | Existing consent honored; declined replacements skipped; legacy Console/router collision kept explicit; partial sets never reported coherent | PASS |
| R9a/b | Second-call failure yields actual partial outcomes; unavailable marker readback remains unverifiable; no invented rollback | PASS |
| R10 | Focused Console/docs/Sidecar/SDK ownership; unavailable SDK explicit, no automatic installation; absent SDK dependency is not guessed | PASS |
| R11 | Management tokens, execution JWTs and application callback/data authorization remain distinct; no invented SDK protocol | PASS |

R8's authorized nonconflicting installs after a declined replacement are allowed
by the plan; the observed result correctly reports an incomplete set. R10 is a
routing probe, not implementation of an SDK absent from the project. R11 reads
actual development Sidecar guidance and explicitly limits its conclusion to the
credential/ownership boundary, not synthetic release runtime behavior. Source
fixtures are minimal metadata folders, while actual bundle/archive tests prove
real resource closure independently. All host results are simulated, never native
installation evidence.

## Acceptance mapping and final inspection

Ticket criteria 1–9 are supported respectively by: (1) family metadata/Console
archive checks and R10; (2) R1 and detached bundle; (3) R2–R4;
(4) R5–R6 and documented future SDK convention; (5) R1/R7;
(6) R8–R9 and immutable project hashes; (7) Sidecar five bundle tests, nine client
tests and two Java workflow tests; (8) R10–R11 and client/Java authorization checks;
(9) the combined checks and actual replay above. These mappings are carried into
the implementation plan for independent Step 5 review.

Full ticket diff inspection included active stale-name searches and comparing
moved YAML to tracked originals (unchanged). Operational guide comparison showed
only resource navigation and repository-specific deployment wording adjustments;
runtime/API semantics were preserved. The additional active operations and MCP
verification links were updated. Constant-based package/evaluation consumers
needed no code changes. Generated caches and pre-existing ignored bootstrap
directories are not distributable sources and were not deleted. Historical
reports/evaluation receipts remain untouched. No credentials or sensitive data
were added to source/evidence; temporary fixture credentials stay test-only.

Current exact-source limitation remains: Sidecar beta.1-SNAPSHOT requires
framework beta.5-SNAPSHOT, while framework main is beta.6-SNAPSHOT; older beta.5
sources lack the new skill family. This change establishes no retroactive
release availability and no real SDK compatibility promise.

Optional nonblocking follow-up: once matching new-family release sources exist,
use a developer-selected real host to observe native discovery/scope/refresh.
Future real SDK compatibility facts remain SDK-owner work.
