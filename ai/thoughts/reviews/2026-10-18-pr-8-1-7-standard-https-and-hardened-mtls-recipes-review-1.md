# PR 8.1.7 Code Review — Cycle 1

## Scope and Repository State

Independent pipeline Step 5, profile `full`. Reviewed the complete supplied ticket,
research, implementation/testing plans, both repository instructions, Sidecar design
lens, external suite README/agreement/foundation/runtime guidance, and the current
staged, unstaged and untracked ticket changes. No prior review documents were read.

Sidecar starts from `e2aad75c40f4e6b524853a170c60d3c672a0a11c`; the external suite
starts from `61007447077a6907569876a289aa2601a00afa0e`. Both working trees contain
the uncommitted ticket work; the orchestrator established that both were initially
clean. No commits, release actions, live providers or business-runtime operations
were performed. This context changed only this review document.

Scope included Sidecar's new `docs/remote-recipes.md` and all eight supplied remote
configuration files; documentation links/verification claims; ticket/research/plans;
external runner dispatch, all five remote modules, remote harness tests, reference
app `lossSubmit`, and SDK-lane documentation. Connected code inspection included
the existing SDK/Sidecar TLS clients, local configuration/fixture/probes, reference
callback/security behavior, public-file ConfigurationMode test and ArchUnit boundary.
The matching Framework beta.8 public REST SPI documentation was consulted directly
from its local tag; Framework tests were not used as Sidecar implementation evidence.

The independent defect review preceded final requirements comparison. It traced
positive and negative request flow, authentication/authorization, temporary key
ownership, startup sequencing, response loss, bounded waiting, report sanitization
and scoped cleanup. No implementation edits were necessary.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No code, tests, configuration, plans, ticket or operator documentation changed.

## Acceptance-Criteria and Plan Conformance

Paths prefixed `suite/` refer to `C:/opendev/code/loomspan-sidecar-test-suite`.

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Both complete remote recipes; fresh temporary material | `suite/scripts/run_sdk.py` dispatch; `remote.py` variants/runtime; `remote_material.py` offline generation | Original complete six/eight-scenario reports independently inspected; fresh complete Standard and all-eight Hardened runtime passed | Implemented |
| Standard verified HTTPS in both directions | Shipped Boot listeners and independent outbound bundles; existing Java clients preserve hostname checking | Fresh `standard-positive`; wrong-CA invocation and wrong-host/expired callback variants | Implemented |
| TLS negatives distinguish rejection from unavailable infrastructure | Independent typed TLS probes; wrong-CA readiness verified with its actual CA; callback counters and real SDK/terminal failures | Fresh real variants plus live loopback CA/SAN/expiry harness coverage | Implemented |
| Hardened peer rejection in both directions | Supplied group CA, `require_and_verify`, fixed SNI, verified upstream trust and client identities | Fresh invocation/callback wrong-CA client/server variants; missing-client rejection in isolation | Implemented |
| Protected namespaces, no bypass, fixed destinations and separate browser ingress | Paired `network_mode: service:<gateway>`, loopback HTTP listeners, literal upstreams, callback/browser path restrictions | Fresh outsider TCP refusal, accepted intended TLS listeners, Host/absolute-form fixed-target probes and separate ingress checks | Implemented |
| JWT and user authorization survive accepted TLS/mTLS | Existing callback security and exact role/record checks reused by remote probes | Both fresh positives: HTTP 401 over accepted verified transport; Alice identity/token equality; literal-role method denial; Bob record denial; form CSRF | Implemented |
| Provided signing and exported JWKS file trust | Only app receives signing subdirectory; exported RSA public numbers checked before Sidecar starts; public-only read-only mount | Every fresh startup validates export; actual issuance/callback works; fresh root ConfigurationMode test covers file/database authority | Implemented |
| No automatic submission/callback replay | Existing Java no-retry/no-redirect settings; one Caddy upstream with zero retry; fixed receiver-local fault instrument | Both fresh recipes pass submission/callback loss with one attempt/forward/effect after five-second quiet interval; direct unknown acceptance distinguished from proxy HTTP 502 | Implemented |
| Standard Boot facilities and focused component tests | Existing `RestTargetClients`, SDK client and Boot bundle selection suffice; no production TLS change | Fresh root 261 and SDK 25/30 tests, including existing TLS and architecture tests | Implemented; no new wiring regression needed |
| Usable references and DevOps ownership | `docs/remote-recipes.md`, supplied fragments and integration links | Fresh runtimes consume shipped fragments; pinned Caddy validation executes at startup; independent documentation/source correspondence checked | Implemented |
| Proportional verification and exclusions | No production transport selector, network-proof controller, lifecycle controller, migration, dependency or schema change | Diff/source inventory, actual composed runs, safe harness and secret/resource checks | Implemented |

Mechanical test names/splitting differ from planning, but dispatch, failure handling,
strict schemas, material, public export and cleanup are covered by the current
harness and real executions. Namespace ownership is established by the actual
composition and outsider probes; a redundant standalone namespace-inode test is
unnecessary. No acceptance requirement was replaced with a static configuration
check. Certificate variants start independently rather than exercising reload or
rotation. These are safe, bounded implementation choices.

## Active Project Guardrails

- Destructive replacement policy and design-lens reference are prominent in the
  ticket and new plans. No legacy adapters, dual contracts, data migration or reset.
- Sidecar remains on Framework `1.0.0-beta.8`; no Framework rebuild/version change.
  Existing ArchUnit tests forbid internal/autoconfigure dependencies and Java skills;
  their fresh report has two passing tests. New Java code uses SDK contracts only.
- SSL listeners/bundles use standard Boot keys; REST target/SDK selectors use their
  existing documented contracts. Caddy uses operator-supplied material and scoped CA.
- Identity originates in real form authentication. mTLS does not replace execution
  JWT verification, method authorization or record ownership.
- TLS signing keys and JWT signing keys remain distinct. Only public exported JWKS
  reaches Sidecar; no credential bodies appear in reviewed report content.
- HTTP fixture/control/backend traffic stays within its own namespace. No host ports,
  provider calls, certificate authority service or certificate lifecycle tests.
- Cleanup targets unique project/helper labels and the named public volume; no global
  prune, unrelated resource removal or business/reference-pack changes.
- Runtime recipe names exist only in the external harness; operator documentation
  permits other independently configured compositions without runtime allowlisting.

## Open Questions and Assumptions

None affecting completion. The test-only Standard bad-callback browser control pins
the supplied negative certificate; normal positive traffic and actual Java callers
retain verification. This existing recorded fixture choice is accurately bounded
in the SDK guide and is absent from operator configurations.

## Verification Results

All commands below were executed in this context. Suite commands use
`C:/opendev/code/loomspan-sidecar-test-suite` as working directory.

- PASS — `.venv/Scripts/python.exe -m pytest tests -q`: 213 tests, including all
  focused remote/runner tests and existing Local harness coverage.
- PASS — `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py`: 13 files formatted.
- PASS — `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py`.
- PASS — `git diff --check` in each repository.
- PASS — `.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar`:
  fresh complete report `suite/evidence/sdk/20261019T021246Z-c36288a6e4ba/report.json`,
  656.834 seconds, all six scenarios and their startup/cleanup stages. Root clean
  verify, SDK clean install, both consumer packages and checkout image build passed.
- PASS — independent XML aggregation from `target/surefire-reports/TEST-*.xml`,
  `sdks/java/core/target/surefire-reports/TEST-*.xml`, and starter equivalent:
  root 61 classes / 261 tests, SDK core 25 and starter 30; zero failures/errors/skips.
- PASS — fresh all-eight Hardened runtime, exact command below:
  `suite/evidence/sdk/20261019T022352Z-review1-1a78a32c/report.json`, 385.515 seconds.
  New checkout image, certificates, configurations, Caddy validation, actual
  startup/scenarios/cleanup all passed. Report explicitly identifies runtime-only
  verification and the fresh Standard common-build report.
- PASS — report/schema/artifact audit: all eight Hardened results and every recorded
  stage passed; its Sidecar, SDK and reference-app hashes exactly equal the fresh
  Standard build artifacts. No implementation changes occurred between executions.
- PASS — original complete reports `20261019T015656Z-c2445d463665` and
  `20261019T014927Z-b5ab2b9224e2` were independently read, including required common
  build, image, scenario and cleanup stages. These are existing full-run evidence,
  not commands claimed as freshly rerun in this review.
- PASS — for each project read from those two original reports and the two fresh
  reports, executed `docker ps -aq --filter label=com.docker.compose.project=<project>`,
  `docker network ls -q --filter label=com.docker.compose.project=<project>` and
  `docker volume ls -q --filter label=com.docker.compose.project=<project>` through
  checked subprocess calls. All 28 project inventories were empty after completion.
- PASS — exact reviewed passing reports scanned for PEM blocks, bearer credentials
  and JWT values; changed-file inventories from `git diff --name-only HEAD` and
  `git ls-files --others --exclude-standard` in both repositories contain no
  certificate/key/keystore/JWT files. No sensitive values were emitted.
- NOT RUN — a second identical common Maven/consumer rebuild for fresh Hardened:
  unnecessary because current shared artifacts were freshly built/tested by Standard,
  their hashes match, and all Hardened runtime paths were freshly exercised. The
  original full Hardened report separately establishes its mandatory runner build
  path. This is not represented as a new full Hardened runner invocation.
- NOT RUN — complete Local runtime: its composition/probe/fixture are unchanged;
  broad harness regression passed and the modified reference-app operation is
  additive and exercised in both remote recipes.

### Exact fresh Hardened command

The following PowerShell here-string was piped directly to Python; it created no
implementation/helper file. It invokes the real runner runtime and records the
reused build identity explicitly.

```powershell
@'
from datetime import datetime, timezone
from pathlib import Path
import sys
import tempfile
import time
import uuid
sys.path.insert(0, 'scripts')
import run_sdk as runner
checkout = Path('C:/opendev/code/loomspan-sidecar')
run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + 'review1-' + uuid.uuid4().hex[:8]
output = runner.ROOT / 'evidence/sdk' / run_id
output.mkdir()
jar, identity = runner.artifact_identity(checkout)
report = {'runId': run_id, 'recipe': 'hardened', 'verificationKind': 'independent-composed-runtime', 'commonBuildReport': '20261019T021246Z-c36288a6e4ba', 'source': runner.revision(checkout), 'suiteSource': runner.revision(runner.ROOT), 'artifacts': identity, 'referenceAppSha256': runner.digest(runner.ROOT / 'sdk/reference-app/target/sdk-reference-app-0.1.0-SNAPSHOT.jar'), 'scenarioResults': {s: 'not-run' for s in runner.RECIPES['hardened']}, 'stages': {}, 'status': 'failed', 'diagnostic': None}
runner.write_report(output, report)
started = time.monotonic()
try:
    with tempfile.TemporaryDirectory(prefix='loomspan-review1-') as temporary:
        runner.run_remote(report, checkout, Path(temporary))
except runner.LaneFailure as failure:
    report['diagnostic'] = str(failure)
except BaseException:
    report['diagnostic'] = 'review-runtime-interrupted-or-unexpected-error'
finally:
    report['elapsedSeconds'] = round(time.monotonic() - started, 3)
    runner.write_report(output, report)
print(run_id, report['status'])
raise SystemExit(0 if report['status'] == 'passed' else 1)
'@ | .venv/Scripts/python.exe -
```

## Residual Risks and Optional Developer Checks

These representative isolated deployments do not establish arbitrary customer
network/firewall policy, certificate lifecycle behavior, established-connection
revocation cutoffs or the later trusted-group/rotation ticket. No such claim is
made by the implementation. An operator-owned non-production deployment can
optionally validate its own DNS, firewall and secret delivery; this is not a gate.

## Disposition

`clean`: complete independent review, no actionable findings, no implementation
artifact changes, and sufficient fresh component/harness/composed verification.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes-review-1.md
SUMMARY: Independent full review found no actionable defects and made no implementation changes. Fresh Standard full runner and all-eight Hardened composed runtime pass, alongside broad safe Python and common Java verification. Exact provenance, skipped duplicate builds and resource/secret audits are recorded above.
DECISIONS:
  - Reused freshly verified unchanged common artifacts for Hardened; matching hashes and all actual runtime variants supply sufficient risk-based verification without a redundant Maven rebuild.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `.venv/Scripts/python.exe -m pytest tests -q`: 213 tests.
  - PASS — `.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar`: six scenarios and required builds.
  - PASS — exact Hardened here-string command above: eight real composed scenarios.
  - PASS — Ruff formatting/lint, both repository diff checks, actual artifact identity, report/credential audits and 28 scoped resource inventories.
  - NOT RUN — duplicate common build for Hardened and complete unchanged Local runtime, for reasons above.
OPTIONAL_DEVELOPER_CHECKS:
  - Operator-owned non-production DNS/firewall/secret-delivery validation, outside portable recipe evidence.
REVIEW_RESULT: clean
NEXT: Complete the Full 5-Step Pipeline.
