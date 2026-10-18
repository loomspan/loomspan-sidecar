# PR 8.1.2 Blocking SDK client Code Review — Cycle 2

## Scope and Repository State

Independent Step 5 review under the approved Full 5-Step Pipeline. Reviewed the
ticket, complete research and both plans, repository instructions and design lens,
and reconstructed behavior from the working tree before comparing requirements.
No prior review document was read.

Sidecar HEAD is `a1ab2d80d9d575614039b01ffe13341bf9c2ea11`; external suite HEAD is
`4ade9b8b1d051f70fc93446a9ce44fca78719941`. Both are on main with ticket changes
unstaged/untracked and no staged changes. Scope includes every new core/starter
Java source and test, build files, boundary test, SDK README and ticket artifacts;
external runner, fixture/proxy, Compose, untracked plain-Java driver, tests and
changed documentation. Root application, root tests and root POM have no diff.
Read connected server admission/status/failure code and matching Framework beta.8
public event definitions. Existing business/runtime/reference material is outside
the change and was preserved.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation artifact was edited in this context.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Discovery, immediate submit, status and await | `SidecarClient` five operations, explicit metadata/envelope decoding | `discoversSubmitsAndAwaitsOpaqueResult`; fresh real driver | implemented |
| Distinct outcomes and reliable acceptance ID only | Safe exception categories; submit takes UUID only from complete 202 body; no Location use | HTTP/challenge/redirect/lost/malformed/duplicate-ID tests; real response-loss proxy | implemented |
| Opaque result and original primary failure | String-only result decoding, status-driven termination, nullable/blank failure message preserved | Exact empty/plain/malformed-looking/Unicode/null string tests; nullable/blank failure tests; real completion/failure | implemented |
| Local validation, object input, safe single path segment | ObjectNode signature, null validation, prohibited-name checks and UTF-8 segment encoding | Zero-send local tests and exact encoded prefixed URI/header assertions | implemented |
| Validate status envelope and expected ID | UUID/type/time/status/failure/event decoding and expected-ID comparison | Missing/type-invalid/mismatched envelope tests, requested ID and last snapshot assertions | implemented |
| Deadline, first-read failure and interruption | Monotonic remaining budget, scheduled request cancellation, bounded sleep, preserved interrupt flag | Slow headers/body/drip, HTTP/protocol read failure, sleep/network interruption tests | implemented |
| Exactly one attempt; no resubmission | Apache retries/redirects/auth handling disabled; await only GETs | Server/destination request counters, real proxy one-attempt/one-forward/one-effect assertions | implemented |
| Response bound, release and close | Content-length and streamed cap, abort/immediate close in finally, idempotent pool/timer close | Exact/exceeded caps, chunked/truncated/malformed responses, repeated follow-up success, all operations rejected after close | implemented |
| Finite settings/defaults and verified TLS | Core validation/defaults and standard TLS strategy accepting optional SSLContext | Invalid config/default tests; real trusted, untrusted and wrong-host TLS tests | implemented |
| Credential and default diagnostic privacy | Private non-serializable holder, fixed exception messages/no causes, redacted structured HTTP problems, no payload logging | Serialization/introspection/stack/default stderr sentinels and exact transmitted Authorization | implemented |
| Equivalent optional Boot client | Properties call core builder, Boot SSL bundle resolution, missing-bean condition and bean destroy | Six starter tests including real HTTP/TLS, limit/timeout, override and close | implemented |
| Core dependency boundary | Independent reactor Enforcer bans Framework/Sidecar; core bans Boot | Fresh real disposable-reactor control and direct/transitive negative variants | implemented |
| Plain-Java real composition | Core-only driver; fixed proxy destination; synchronized independent counters; internal network and ephemeral JWT mounts | Fresh isolated runtime run described below, real COMPLETED/FAILED/unknown outcomes and cleanup | implemented |
| Documentation and narrow scope | SDK README and external lane docs match defaults, uncertainty, exact text, bounded waiting, temporary JWT and recipe limits | Source/API comparison and actual compiling/packaged consumer | implemented |

The final-class client instead of an interface and separate edge-test class are
safe planned organization choices. No business-schema compiler, async/cancellation
API, compatibility path, production trust change or release change was added.

## Active Project Guardrails

- The simplest one-client implementation uses maintained HTTP/JSON libraries;
  an internal deadline scheduler provides bounded slow-body reads and interruption
  without introducing a public executor contract.
- Existing ArchUnit restrictions remain unchanged. SDK sources import no Framework,
  Sidecar or internal/autoconfigure types. The plain Java driver depends directly
  only on SDK core. Root Framework pin remains beta.8.
- Public result, failure and event content is preserved rather than rewritten or
  reconstructed. Credentials enter only the Authorization header; structured HTTP
  error token echoes are redacted without modifying business results.
- Standard TLS validates certificates and hostnames. Core accepts SSLContext;
  starter uses standard Boot SSL bundles and `loomspan-sdk.*` settings.
- Current JWT fixture wiring is explicitly authorized by the ticket. Temporary
  signer private key stays in memory, token mount belongs only to the driver,
  and no provider forwarding or business/runtime/data mount was introduced.
- No development-data reset, Framework rebuild, publication, commit or push.
  Obsolete-contract compatibility machinery was not introduced.

## Open Questions and Assumptions

None affecting correctness or review confidence.

## Verification Results

Commands below were actually executed in this review, with external commands run
from `C:/opendev/code/loomspan-sidecar-test-suite`.

- PASS — `mvn -B -ntp -f sdks/java/pom.xml test`: 12 core tests and 6 starter tests,
  zero failures/errors/skips, including real TLS and deadline/interruption cases.
- PASS — `python sdks/java/tests/test_build_boundary.py`: all three unittest
  methods passed in 88.636 seconds: clean control, eight Framework/Sidecar
  direct/transitive negatives and two core Boot direct/transitive negatives.
- PASS — external `.venv/Scripts/python.exe -m pytest tests -q`: 146 passed.
- PASS — external `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- PASS — external `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- PASS — `git diff --check` in both checkouts.
- PASS — the exact PowerShell/Python command below independently checked current
  packaged SDK classes and consumer dependency hashes, rebuilt the local image,
  and ran the actual isolated runtime without repeating unchanged Maven builds.
  Report: `C:/opendev/code/loomspan-sidecar-test-suite/evidence/sdk/review-2-independent-runtime/report.json`.
  All eight runtime stages passed: startup modelRequests=0; five driver outcomes
  true; final modelRequests=1, submissionAttempts=1, forwardAttempts=1 and
  syntheticEffects=1; graceful exit 143; cleanup passed.
- NOT RUN — `mvn -B -ntp clean verify` and the complete composed CLI build sequence
  were not repeated in this context. Root production/test/POM files are unchanged;
  independently read root Surefire XML totals were 256 tests, zero failures/errors,
  three existing skips. Current Sidecar/SDK/driver hashes match the successful
  complete composed report `20261018T172812Z-d8a63bfd928f/report.json`, and fresh
  SDK tests plus the current-artifact runtime exercise the affected contracts.

Exact runtime verification command:

```powershell
@'
import sys, json, tempfile, hashlib, zipfile
from pathlib import Path
sys.path.insert(0, 'scripts')
import run_sdk as r
checkout = Path('C:/opendev/code/loomspan-sidecar')
report_path = Path('evidence/sdk/20261018T172812Z-d8a63bfd928f/report.json')
prior = json.loads(report_path.read_text())
jar, identity = r.artifact_identity(checkout)
assert identity['sidecarJarSha256'] == prior['sidecarJarSha256']
assert identity['sdk'] == prior['sdk']
for module in ('core', 'spring-boot-starter'):
    target = checkout / 'sdks/java' / module / 'target'
    with zipfile.ZipFile(r.find_jar(target)) as z:
        for p in (target/'classes').rglob('*'):
            if p.is_file():
                assert z.read(p.relative_to(target/'classes').as_posix()) == p.read_bytes(), p.name
driver = Path('sdk/driver/target')
assert r.digest(r.find_jar(driver)) == prior['driver']['sha256']
core_dependency = next((driver/'dependency').glob('loomspan-sdk-core-*.jar'))
assert r.digest(core_dependency) == identity['sdk'][0]['sha256']
with zipfile.ZipFile(r.find_jar(driver)) as z:
    for p in (driver/'classes').rglob('*.class'):
        assert z.read(p.relative_to(driver/'classes').as_posix()) == p.read_bytes()
print('Current packaged classes, core consumer dependency and prior composed build hashes match.')
report = {'status':'failed', 'diagnostic':None, 'stages':{}, **identity, 'driver':prior['driver']}
output = Path('evidence/sdk/review-2-independent-runtime')
output.mkdir(exist_ok=False)
with tempfile.TemporaryDirectory(prefix='loomspan-sdk-review-2-') as temporary:
    try:
        r.run_smoke(report, checkout, Path(temporary))
    finally:
        r.write_report(output, report)
print(json.dumps(report))
assert report['status'] == 'passed'
'@ | .venv/Scripts/python.exe -
```

## Residual Risks and Optional Developer Checks

The composed fixture proves bounded no-replay behavior, not production exactly-once
effects. Standard/hardened recipes and future trust/callback contracts remain
outside this ticket. Root configured production-browser checks retain three existing
skips; SDK transport TLS is independently exercised by focused tests. No optional
developer check is needed to complete this ticket.

## Disposition

`clean`: independent review completed, no actionable findings, sufficient fresh
verification, and no implementation-artifact edits.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-18-pr-8-1-2-blocking-sdk-client-review-2.md
  - C:/opendev/code/loomspan-sidecar-test-suite/evidence/sdk/review-2-independent-runtime/report.json
SUMMARY: Reviewed the complete ticket change in both repositories independently and found no actionable defects. Fresh SDK, dependency-boundary, offline-suite and isolated composed runtime verification passed. No implementation artifacts changed.
DECISIONS:
  - Reused hash-verified packaged artifacts for fresh runtime composition instead of repeating unchanged root and consumer builds.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `mvn -B -ntp -f sdks/java/pom.xml test`
  - PASS — `python sdks/java/tests/test_build_boundary.py`
  - PASS — external `.venv/Scripts/python.exe -m pytest tests -q`
  - PASS — external `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  - PASS — external `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  - PASS — `git diff --check` in both repositories
  - PASS — exact external PowerShell/Python runtime command reproduced above
  - NOT RUN — root `mvn -B -ntp clean verify`: unchanged root and hash-verified prior build, with fresh affected tests/runtime passing
OPTIONAL_DEVELOPER_CHECKS:
  - none
REVIEW_RESULT: clean
NEXT: Return the clean independent review to the pipeline orchestrator for completion.
