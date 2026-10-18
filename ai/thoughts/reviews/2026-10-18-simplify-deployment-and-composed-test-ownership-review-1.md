# Deployment verification and composed test ownership Code Review — Cycle 1

## Scope and Repository State

Pipeline Step 5, approved profile `full`, review cycle 1. Reviewed staged,
unstaged and untracked ticket work in Sidecar at HEAD
`77aa70c05a3e6ec7a5fc66895be767f0f7b115ea` and the external suite at HEAD
`f0d34fa0ae3182999528aec2979d3b8af2bc1b49`. The supplied initial-state record
identifies only the original ticket as pre-existing dirty work; the remaining
changes belong to this pipeline. No staged changes or unrelated source changes
were present. No previous review document was consulted.

Read the complete ticket, research, implementation/testing plans, repository
instructions and design lens. Reconstructed the deleted verifier families from
Git, traced retained component assertions, read the new smoke, suite runtime,
transport helper, fixture, tests, workflows and documentation. Completed the
initial independent review before changing implementation artifacts. Assessed
security/privacy, transport failure classification, process lifetime, disposable
state, cleanup, coverage ownership, timing claims and cross-repository build
independence. Full remains the appropriate profile; no route upgrade is needed.

## Findings

No remaining actionable findings. The following finding was resolved in this context.

## Findings Resolved in This Context

### [P2] Preserve the Secure session cookie assertion

- Location: `docs/verification.md:51`; deleted
  `src/test/java/ai/loomspan/sidecar/deployment/ProductionComposeBrowserIntegrationTest.java:49`;
  retained management/browser fixture configuration.
- Scenario: removing the composed browser class removed its unique check that
  the production HTTPS session cookie is Secure. All retained management/browser
  HTTP fixtures explicitly set `server.servlet.session.cookie.secure=false`.
- Impact: the inventory incorrectly classified the assertion as component-owned;
  a shipped secure-cookie regression could pass the remaining verification.
- Evidence: the deleted class inspected the browser's `JSESSIONID.secure` flag;
  source inspection found no corresponding retained secure-cookie assertion.
- Fix: the bounded smoke makes one anonymous login-page request through verified
  production HTTPS and requires a Secure `JSESSIONID`. It performs no setup,
  credentialed login or browser collection. The request helper returns the
  response cookie header; a regression test rejects absent, insecure and
  wrong-name cookies. Corrected the maintainer inventory and both governing
  plans to assign this unique assertion to the smoke.
- Verification: eight smoke safety cases passed, and the real shipped-recipe
  smoke including the new assertion passed with cleanup in 15.967 seconds.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One bounded shipped-recipe smoke, authenticated/unauthenticated reachability, status-only health and no bypass | `verify-deployment.py`, shipped production/supplied Compose and Caddy files | Independent real smoke passed before and after the cookie fix; eight orchestration cases | implemented |
| Component regressions and listener topology retained | Only obsolete environment-driven composed browser class removed; no product/recipe changes | Independent normal SDK command rebuilds root components; XML reports show 61 classes / 261 tests, zero failure/error/skip, all retained Chromium classes and ArchUnit | implemented |
| Broad assertions have explicit owners | Maintainer inventory; retained management, persistence, portability, trust and shutdown assertions | Read actual component assertions and old scripts; fresh suite/runtime evidence below | implemented |
| Existing SDK lane retains invocation, transport and actual TERM checks | Same `Runtime`, fixture and internal Compose lane; current JWKS/JWT profile | 48 independent pytest passes; normal Local runtime evidence below | implemented |
| Future Local Spring/callback/Standard/Hardened obligations stay pending | Explicit empty recipe failure and reconciled 8.1.5–8.1.8 references | Empty-selector and report tests; no baseline claimed as absent Spring callback evidence | implemented |
| Certificate lifecycle removed; transport validation and operator support retained | Old TLS lifecycle helper deleted; independent valid/expired servers and supplied Caddy transport baseline | Strict TLS material tests; transport probe checks specific certificate failures rather than refused connections; unchanged outbound mTLS component test | implemented |
| CI independent and nonrecursive | One smoke per workflow; root build has no suite call; suite builds actual checkout | `ReleasePreparationTest` passed in independent root build; build-call tests | implemented |
| Cost observations distinguish measured/historical limits | Guide labels historical counts and limited timings; suite stage durations | New run records complete build/runtime duration separately from smoke orchestration | implemented |
| Disposable verification and failure-sensitive cleanup | UUID projects, temporary material, internal network, captured diagnostics, environment filtering | Sentinel/error-path tests; independent Docker-backed injected failure preserved primary error and left no labeled resources | implemented |

The cookie assertion is a safe narrow adaptation of the smoke inventory to retain
existing security assurance. No application behavior, supported contract,
deployment topology, dependency or data migration changed.

## Active Project Guardrails

- Replaced superseded scripts directly, with no wrappers or compatibility path;
  retained focused tests and the existing SDK harness under the design lens.
- No Framework implementation imports, bean replacement, reflection or Java
  skills were added. The existing public API ArchUnit checks passed.
- Health stays on the application listener; JWT trust/identity/expiry remains
  distinct from transport. Framework shutdown retains one budget, proven with
  admitted fixture work and actual TERM.
- TLS material is temporary transport input. No issuance/renewal/replacement/
  reload/persistence/revocation test was moved or introduced.
- Business runtime, credentials, operational data and maintained reference packs
  were untouched. No reset, provider/live CA/DNS operation, Framework build,
  release/version action, commit or push occurred.

## Open Questions and Assumptions

None affecting correctness. Full future SDK Spring and remote-recipe obligations
remain explicitly assigned to their existing tickets.

## Verification Results

- PASS — `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`
  — eight cases after the fix.
- PASS — `python scripts/verify-deployment.py --image loomspan-sidecar:deployment-smoke`
  — 18.759s before the fix; 15.967s after it; all assertions and cleanup passed.
- PASS — `python -` with the exact PowerShell stdin script below — independent Docker-backed post-start failure
  injection wrapped `Runtime.assertions`, called the real assertions, then raised
  fixed `review-controlled-failure`. Report remained failed, cleanup passed in
  16.589s, and exact project-label listings found no containers/networks/volumes
  for `sidecar-smoke-90534a6c60504a3a`. No production fault-injection CLI was added.
- PASS — `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`
  in the suite — 48 passed.
- PASS — `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  in the suite — five files already formatted.
- PASS — `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  in the suite.
- PASS — `git diff --check` in both repositories.
- PASS — `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  in the suite — all five scenarios and every build/runtime/cleanup stage passed,
  453.354s total. Independent report:
  [`20261018T213609Z-42ef8fabc4c3`](../../../../loomspan-sidecar-test-suite/evidence/sdk/20261018T213609Z-42ef8fabc4c3/report.json).
  Root `clean verify` took 304.677s and produced 61 classes / 261 tests with no
  failure/error/skip. TERM completion took 2.469s with detail/finished effects;
  cutoff took 3.860s without them; both exited 143 without OOM. Wrong CA,
  hostname and expired material were rejected; supplied Caddy normalization,
  fixed upstream, redirect, wrong trust and backend refusal passed.
- PASS — `docker container ls -a -q --filter 'label=com.docker.compose.project=sdk-cb433897e2354fe3'`,
  `docker network ls -q --filter 'label=com.docker.compose.project=sdk-cb433897e2354fe3'`
  and `docker volume ls -q --filter 'label=com.docker.compose.project=sdk-cb433897e2354fe3'`
  — no resources remained for the independent Local project.

```powershell
@'
import importlib.util,json,tempfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('deployment',Path('scripts/verify-deployment.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
with tempfile.TemporaryDirectory(prefix='review-smoke-failure-') as temporary:
    runtime=m.Runtime(Path(temporary),'loomspan-sidecar:deployment-smoke')
    original=runtime.assertions
    def fail_after_assertions():
        original()
        raise m.Failure('review-controlled-failure')
    runtime.assertions=fail_after_assertions
    report=m.verify(runtime)
    assert report['status']=='failed' and report['diagnostic']=='review-controlled-failure' and report['cleanup']=='passed'
    for resource in ('container','network','volume'):
        assert not m.command(['docker',resource,'ls','-q','--filter','label=com.docker.compose.project='+runtime.project]).strip()
    print(json.dumps({'project':runtime.project,**report,'residualResources':False}))
'@ | python -
```

## Residual Risks and Optional Developer Checks

Hosted CI/release was not run; local standard commands exercise the affected
boundaries. No release is authorized. Future complete SDK Spring callback and
Standard/Hardened recipes are outside this change and remain required by their
own tickets. The smoke timing excludes fixture preparation, as already documented.

## Disposition

`fixes-applied`. Complete initial review preceded edits; final re-review found
no remaining actionable issue, and affected runtime verification passed. A fresh
independent Step 5 context is required because this cycle changed implementation
artifacts. No developer decision remains.
