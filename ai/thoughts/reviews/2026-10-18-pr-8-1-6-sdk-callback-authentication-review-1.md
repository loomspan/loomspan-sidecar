# PR 8.1.6 Code Review — Cycle 1

## Scope and Repository State

Independent pipeline Step 5, selected profile `full` (Full 5-Step Pipeline).
Read the ticket, research, implementation/testing plans, command/protocol,
repository instructions and design lens. No previous review was read. Both
repositories began this ticket clean, according to the supplied initial state;
current changes were independently inventoried using Git status/diffs, including
all untracked production and test files. Sidecar base is `40fa6e1`; suite base is
`88e5fce`. No branch comparison or unrelated prior implementation is included.

Reviewed all four new starter production classes, three callback test classes,
POM/discovery/issuance-test changes, SDK documentation, ticket/plans and coverage
inventory. Across `C:/opendev/code/loomspan-sidecar-test-suite`, reviewed callback
controller, ordinary security/submission changes, Local composition/probes/model
fixture, runner stages, tests and documentation. Traced existing signing,
Sidecar pre-send expiry, process-file trust and architecture safeguards beyond
the changed hunks. The initial independent defect review completed before any
write; this review document is the sole review mutation.

## Findings

No actionable findings.

The dedicated chain owns its decoder/converter, rejects ambiguous credentials
directly and cannot adopt the unrelated application decoder. Its null security
repository/cache and explicit details sources prevent session reads during
security processing; the executable session trap covers authenticated and denied
requests. The prefix has a segment boundary and rejects ambiguous configuration.
Claim checks precede Spring claim access/conversion, exact authorities survive,
and all decoder failures are constant, credential-free authentication errors.

Overlap trust publishes complete immutable snapshots, including empty snapshots
on detected configured-file failure. The own signer cannot mask that failure.
File-byte comparison detects changes independent of metadata, unknown-kid reloads
are globally limited, and synchronized close prevents later reads. Existing
in-flight authentication may finish with the snapshot it already captured;
the contract does not cancel admitted work.

The composed authorization probe distinguishes arrivals, method bodies and
effects. The expiry probe observes RUNNING and model entry before expiry,
requires the actual CALL_TOOL turn, checks the exact propagated child failure,
and observes no HTTP arrivals through terminal completion and a quiet interval.
The required-input leaf avoids accidentally proving only queue expiry. All
receiver/forwarding observations remain synthetic and credential values are
kept in memory.

## Findings Resolved in This Context

None. No implementation artifacts were edited.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Missing/multiple/invalid bearer, query token, cookie/session rejection and direct Bearer 401 | Callback resolver and direct entry point | CallbackSecurityAutoConfigurationTest request matrix/session case | implemented |
| Strict signature, issuer, audience, typ/purpose, expiry and raw claim shapes | CallbackJwtDecoder | CallbackJwtDecoderTest raw signed variants and fixed skew clock | implemented |
| Stateless chain, scoped CSRF, no fallback and context cleanup | Null repository/cache, explicit details sources, first chain | Actual FilterChainProxy session trap, subsequent anonymous request, independent browser/CSRF cases | implemented |
| Configurable prefix; GET/POST; documented normal method/firewall handling | CallbackProperties, literal boundary and method rules | Override/root/sibling/startup, method and firewall tests | implemented |
| Verified issuer/subject and exact authorities | JwtAuthenticationToken converter | Exact identity/authority endpoint assertions and real Local callback result | implemented |
| Own key works without overlap in both signing modes | Startup SigningKeySource public identity | CallbackJwtTrustTest signs/decodes with real colocated/provided sources | implemented |
| Configured unusable overlap withdraws/reloads/recovers and removes old keys | CallbackJwtTrust atomic snapshot | Whole-file failure, conflicts, missing/unreadable, recovery/removal, concurrent/rate-limit/poll/close tests | implemented |
| Authorized forwarded callback, method denial before body, business denial before effect | Passthrough Local routes and method/owner checks | Fresh real Local callbacks probe, exact credential equality and independent counters | implemented |
| Ordinary form login/routes/CSRF coexist | Separate lower-priority application chain | Fresh Local form login and CSRF regression plus starter chain tests | implemented |
| Callback expiry fails before sending | Existing GenericRestSkillHandler strict check, admitted planner fixture | Fresh real callback-expiry probe, exact failure and zero arrival/body/effect deltas | implemented |
| Complete Local invocation/callback recipe | Existing isolated composition and complete runner stages | Twelve-scenario source-backed final report plus fresh callback probes | implemented |
| Sidecar JWKS only in file/database configuration | Existing process-owned trust | Root ConfigurationModeIntegrationTest XML: 8 passing cases | implemented |
| Wiring, ownership, overlap failure, sessions and limits documented | SDK README and both coverage inventories | Compared docs with implementation and executable behavior | implemented |
| Independent dependency/public-API boundary | SDK enforcer and unchanged Sidecar architecture test | Fresh SDK reactor and boundary mutation suite; root architecture evidence | implemented |

Tests are consolidated rather than implementing every suggested test-name or
auxiliary counter literally. Direct response setters, no redirect/forward
assertions, endpoint counters and actual two-chain execution provide the planned
rejection/fallback evidence without a separate error-dispatch fixture. This is
a safe mechanical deviation, not a missing behavior.

## Active Project Guardrails

- Direct replacement policy/design lens applied; no compatibility shim, schema
  migration, historical trust ledger or data reset added.
- SDK remains independent of Sidecar/Framework; Sidecar production contracts are
  unchanged, beta.8 remains pinned, and the existing ArchUnit prohibition on
  `ai.loomspan.internal..`/`ai.loomspan.autoconfigure..` remains intact.
- Authentication owns identity; Spring method security and application code own
  authorization. No model input or role-prefix mapping selects identity.
- Complete current trust is authoritative; signing state and callback trust are
  independent of Sidecar's process JWKS.
- Verification used isolated synthetic SDK resources, standard helpers and
  provider-disabled fixtures. Business runtimes/reference packs, deployed data,
  commits, publication and releases were untouched.
- Full remains appropriate for security/lifecycle/cross-repository changes.

## Open Questions and Assumptions

None affecting correctness or completion. Applications must keep callbacks under
the configured prefix and avoid competing highest-precedence chains, as required
and documented; arbitrary application security configuration is not analyzed.

## Verification Results

From Sidecar:

- PASS — `mvn -B -ntp -f sdks/java/pom.xml test` — 25 core and 30 starter tests,
  zero failures/errors/skips; dependency enforcers passed. Existing classes were
  current, and Maven reported nothing to compile.
- PASS — `python sdks/java/tests/test_build_boundary.py` — disposable real Maven
  control plus direct/transitive forbidden dependencies in both SDK modules and
  core Boot-runtime exclusion; 3 tests passed in 162.054s.
- PASS — `git diff --check` — no whitespace errors.

From the suite:

- PASS — `.venv/Scripts/python.exe -m pytest -q` — 189 passed in 26.18s.
- PASS — `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  — 7 files already formatted.
- PASS — `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  — all checks passed.
- PASS — `git diff --check` — no whitespace errors.
- NOT RUN successfully — `.venv/Scripts/ruff.exe format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  and `.venv/Scripts/ruff.exe check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  — that executable path does not exist; corrected to the available `ruff`
  commands above, both passed. This was a tool-location error, not a code failure.

The following exact fresh targeted runtime command passed from the suite. It
uses the full report's identical already-built artifacts, current fixture sources
and a new isolated project/key volume; finally cleanup and both resource-absence
assertions passed. Its output contained only fixed booleans/counters.

```powershell
@'
import json, sys, tempfile, uuid
from pathlib import Path
sys.path.insert(0, str(Path('scripts').resolve()))
import run_sdk as lane
checkout=Path('C:/opendev/code/loomspan-sidecar')
report=json.loads(Path('evidence/sdk/20261018T232143Z-36a99814fb53/report.json').read_text())
_, actual=lane.artifact_identity(checkout)
for name in ('frameworkVersion','sidecarJarSha256','sdk'):
    assert actual[name] == report[name]
assert lane.digest(lane.find_jar(Path('sdk/reference-app/target'))) == report['referenceApp']['sha256']
project='sdk-review-'+uuid.uuid4().hex[:12]
with tempfile.TemporaryDirectory(prefix='sdk-review-') as temporary:
    runtime=lane.local.Runtime(Path(temporary)/'local', checkout, lane.ROOT, project, 'loomspan-sdk-smoke:sdk-1c806c9925a64c11', lane.command, lane.LaneFailure)
    try:
        runtime.start()
        print(json.dumps({'callbacks':runtime.probe('callbacks'),'callbackExpiry':runtime.probe('callback-expiry')}), flush=True)
    finally:
        runtime.cleanup()
    assert not lane.command(['docker','ps','-aq','--filter','label=com.docker.compose.project='+runtime.project]).strip()
    assert not lane.command(['docker','volume','ls','-q','--filter','name='+runtime.volume]).strip()
    print('PASS current artifact identities, real callbacks, admitted expiry, cleanup/resource absence', flush=True)
'@ | .venv/Scripts/python.exe -
```

Independently read the complete final twelve-scenario report at
`C:/opendev/code/loomspan-sidecar-test-suite/evidence/sdk/20261018T232143Z-36a99814fb53/report.json`.
Every build/scenario/cleanup stage passed. Recomputed current JAR identities
match the report: Sidecar `fec7bf3a…`, core `f6748071…`, starter `2668b94e…`,
reference app `3082cf3c…`. Read root Surefire XML directly: 61 classes, 261 tests,
zero failures/errors/skips, including eight configuration-mode integration cases.
This establishes existing regression evidence independently of Step 4 receipts.

- NOT RUN again — `./mvnw.cmd -B -ntp clean verify` and
  `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  — unchanged full-build/baseline evidence was independently inspected and hashes
  verified; fresh SDK tests, all safe Python tests and real callback/expiry probes
  cover this ticket's changed behavior. Repeating the unchanged expensive build
  adds no needed assurance.

## Residual Risks and Optional Developer Checks

No optional completion checks. Standard/Hardened recipes and receiver-rotation
composition remain future ticket scope. Filesystem changes take effect when
detected by the documented poll/unknown-kid reload; already admitted work is not
cancelled. No general malformed-wire/container behavior beyond the documented
normal Spring firewall handling is claimed.

## Disposition

`clean`: full review completed, no actionable findings, no implementation edits,
and sufficient independently established automated acceptance evidence.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-18-pr-8-1-6-sdk-callback-authentication-review-1.md
SUMMARY: Independently reviewed the complete two-repository change with no actionable findings and no implementation edits. Fresh SDK, boundary and full Python suites passed; fresh real callback authorization/expiry and cleanup checks passed with artifact identities verified against the complete Local report.
DECISIONS:
  - Reused independently verified unchanged full-build evidence and reran real callback scenarios with identical artifacts rather than repeating the expensive root build.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `mvn -B -ntp -f sdks/java/pom.xml test`: 55 tests.
  - PASS — `python sdks/java/tests/test_build_boundary.py`: 3 tests, 162.054s.
  - PASS — `.venv/Scripts/python.exe -m pytest -q`: 189 tests.
  - PASS — `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  - PASS — `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  - PASS — `git diff --check`: both repositories.
  - PASS — Targeted runtime command reproduced exactly above: current artifact identities, real callbacks, admitted expiry, cleanup/resource absence.
  - NOT RUN — `./mvnw.cmd -B -ntp clean verify`: unchanged existing full-build XML and artifact evidence independently verified.
  - NOT RUN — `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`: existing complete report independently verified; fresh real callback scenarios rerun.
OPTIONAL_DEVELOPER_CHECKS:
  - none
REVIEW_RESULT: clean
NEXT: Complete the pipeline with this clean independent review.
