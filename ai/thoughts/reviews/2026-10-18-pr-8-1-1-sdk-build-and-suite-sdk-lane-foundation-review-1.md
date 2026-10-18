# PR 8.1.1 Code Review — Cycle 1

## Scope and Repository State

Independent pipeline Step 5 under the approved Full 5-Step Pipeline. Reviewed
the ticket, research, implementation and testing plans, both repository
instructions, and the Sidecar design lens. No prior reviews were consulted.

Sidecar HEAD is `b747fd9e9f57e5ae1cf8972d79801df8c9c6fa38`; suite HEAD is
`14233ef4eca50bd7f0051f954a65812324142632`. This is working-tree work on main:
the comparison base is each current HEAD. Inventory included staged, unstaged
and untracked files, not only ordinary Git diff hunks.

Reviewed all three SDK POMs, SDK README and real Maven boundary test; suite
runner, fixture package, Compose definition, tests, SDK guide and README link;
and pipeline research/plans. The suite's pre-existing 72-line
`docs/project-agreement.md` addition is developer work, excluded from attribution
and preserved byte-for-byte. Root application, tests, POM, Dockerfile and
dockerignore have no changes. No implementation artifact changed in this review.

## Findings

No actionable findings.

Reconstructed behavior before checking plan conformance: normal Maven lifecycle
inherits the transitive ban in both modules; starter permits core and optional
Boot while core rejects Boot. The runner builds the supplied checkout using root
verify and SDK install, validates packaged identities, then builds that checkout's
Dockerfile. Runtime configuration uses public settings, a temporary public key,
file skills and tmpfs SQLite. Readiness checks actual HTTP `UP`; shutdown requires
normal exit plus Spring completion before scoped cleanup. Failure and interrupt
paths retain failed evidence; cleanup cannot convert failure into success.

Security/privacy review traced child environment projection, explicit container
environment/mounts, internal-only networking, fixture behavior and diagnostics.
There is no provider forwarding, credentials mount, published application port or
raw subprocess/log persistence. Sentinel tests exercise hostile exceptions and
command output. Only ephemeral public verification material is serialized.
No production authorization, persistent contract or Framework execution policy
changes occur. Resource/lifecycle, failure recovery, build provenance and
documentation match this startup-only foundation's bounded scope.

## Findings Resolved in This Context

None. No fixes were needed or applied.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Independent Java 21 core/starter; Boot 4.1.0 | SDK aggregator, compiler release/Java guard, starter BOM and optional dependencies | Fresh real Maven control; actual installed/built artifacts and metadata | implemented |
| Root scope and beta.8 unchanged | No root POM/application/Dockerfile diff; packaged version checked | Independent Git checks, packaged JAR metadata/hash comparison; existing 61 Surefire XML files total 256 tests, zero failures/errors, three skips | implemented |
| Direct/transitive Framework/Sidecar rejected in either module | Inherited always-active Enforcer ban, exact SDK exceptions | Fresh clean control and all eight synthetic negative graphs pass; assertions require named forbidden coordinate and consuming SDK module | implemented |
| Build actual checkout and SDK, provider-free deterministic readiness smoke | Explicit root verify/SDK install and checkout Docker build; separate fixture/internal network/tmpfs | Full CLI report matches current JAR hashes; fresh real image/start/readiness/shutdown/zero-request/cleanup run passes | implemented |
| Three recipes, empty selections fail | Explicit recipe map; selection stage before builds | Fresh actual standard/hardened CLI failures and parsed reports; local pytest acceptance | implemented |
| Per-run provenance/results; no credentials/private keys/payloads | Initialized/atomic JSON report, fixed diagnostics, allowlisted identity fields | Fresh full pytest, sentinel tests, actual report parser and hash checks | implemented |
| Preserve existing lanes, content and reference packs | Existing code/config/fixtures unchanged; new composition separate | Independently recomputed all 190 baseline hashes, only allowed README differs; fresh retained smoke; retained replay reports show 8 equipment and 224 TypeSafe cases with restoration true | implemented |

Docker rather than native Windows process termination, container-local readiness,
executable tmpfs for SQLite native extraction, OS path environment projection,
and root verify within the lane are coherent plan adaptations. They do not
expand API or recipe claims. Empty JARs intentionally expose no placeholder API.

## Active Project Guardrails

- Applied the design lens: two minimal independent modules and a small isolated
  lane; no generalized deployment platform or compatibility machinery.
- No development-data reset, deployed-data deletion, migration or legacy adapter.
- Existing Sidecar public API architecture guard remains unchanged. SDK has no
  Framework/Sidecar Java dependency and introduces no Java skill or SPI.
- Framework stays pinned to beta.8; no Framework source build, version override,
  release, hosted CI configuration, commit or push.
- Retained business content, operational data, credentials and reference packs
  remain separate. All executable review work is provider-free.
- Full profile remains appropriate for cross-repository build/lifecycle assurance.

## Open Questions and Assumptions

None affecting correctness or review confidence. Docker/Maven and installed
Framework beta.8 are development prerequisites. Startup smoke intentionally
proves no SDK invocation behavior; subsequent tickets own that coverage.

## Verification Results

Commands run from the appropriate repository root:

- PASS — `C:/opendev/code/loomspan-sidecar-test-suite/.venv/Scripts/python.exe sdks/java/tests/test_build_boundary.py`: two unittest methods, normal control plus eight negative graphs, 38.281 seconds.
- PASS — `.venv/Scripts/python.exe -m pytest tests -q`: 142 passed in 13.79 seconds.
- PASS — `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`: four files formatted.
- PASS — `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- PASS — `git diff --check` in both repositories.
- PASS — `git diff --exit-code -- pom.xml Dockerfile .dockerignore src`: root behavior/build files unchanged.
- PASS — `.venv/Scripts/python.exe scripts/run_sdk.py standard`: expected exit 1; parsed new report `evidence/sdk/20261018T102708Z-8ea1f2af5850/report.json`, selection failed and all later stages not run.
- PASS — `.venv/Scripts/python.exe scripts/run_sdk.py hardened`: expected exit 1; parsed new report `evidence/sdk/20261018T102709Z-12e00dedd5ed/report.json`, same explicit empty-recipe semantics.
- PASS — independent Python stdin verification using `.venv/Scripts/python.exe -`: called `artifact_identity`, compared every returned field with actual CLI report `evidence/sdk/20261018T101613Z-a272c3d8addc/report.json`, recomputed SHA-256 for all 190 entries of `.runtime/sdk-preservation-before.json`, and asserted only README differs. Agreement exactly preserved.
- PASS — same independent stdin verification called `run_smoke` in a fresh `TemporaryDirectory` against the actual checkout: image build, startup, readiness, shutdown, fixture and cleanup all passed; `UP`, exit 143, graceful true, no OOM, zero model requests.
- PASS — independent XML/JSON stdin parser verified current root Surefire counts, actual CLI identities and retained replay statuses: equipment `REPLAY_PASS` (8 cases), TypeSafe `PROTOCOL_PASS` (224 cases), both `runtimeRestored=true`.
- PASS — `.venv/Scripts/python.exe scripts/smoke.py`: both actual retained integrations, deterministic quotes, identity/permission checks, prior records preserved, providers disabled and zero model calls. Updated existing `.runtime/smoke.json` only.
- PASS — `docker ps --filter name=sdk- --format '{{.Names}}'` and `docker network ls --filter name=sdk- --format '{{.Name}}'`: empty after independent smoke completed.

The independent runtime check was deliberately executed without repeating root
clean verify: current packaged bytes match the completed full CLI report, root
files are unchanged, and current Surefire outputs independently substantiate its
results. Fresh Maven boundary builds and full local tests supplement that evidence.
No claim is made that this review reran the full root build or all 232 retained
replays; rerunning those unchanged expensive checks supplies little extra coverage.

## Residual Risks and Optional Developer Checks

No blocking residual risk or optional manual check. This proves foundation
startup/readiness/shutdown and packaging isolation, not future client, callback,
JWT lifecycle or secure recipe implementation. Unique local smoke image tags
remain as documented build artifacts; containers, networks and temporary state
are cleaned up.

## Disposition

`clean`: full independent review complete, no actionable findings, no
implementation-artifact changes, and sufficient executable evidence for every
acceptance criterion.
