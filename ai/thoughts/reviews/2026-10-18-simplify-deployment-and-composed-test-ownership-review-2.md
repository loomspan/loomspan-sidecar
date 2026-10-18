# Deployment verification and composed test ownership Code Review — Cycle 2

## Scope and Repository State

Independent pipeline Step 5, approved `full` profile, against Sidecar HEAD
`77aa70c05a3e6ec7a5fc66895be767f0f7b115ea` and the neighboring suite's current
working tree. Inspected staged, unstaged and untracked scope in both repositories;
there are no staged changes. Initial dirty-state attribution supplied by the
orchestrator was only the untracked ticket; current implementation changes are
ticket-scoped. Prior review documents were neither read nor used.

Read the complete controlling ticket, research, implementation/testing plans,
both AGENTS files, Sidecar design lens, and relevant suite agreement/runtime
guidance. Reconstructed current code before validating its planning narrative.
Read new smoke, helper tests, runner, fixture and transport implementation; inspected
removed verifier source from HEAD, removed browser assertions, workflow changes,
connected Compose/Caddy files, retained focused regression assertions and active
ownership/future-ticket documentation. Initial review completed before any edit.
Only this review artifact was written.

## Findings

No actionable findings.

The smoke uses the actual production recipe/supplied overlay and verifies image
settings, fresh storage, verified TLS ingress, exact anonymous health, authenticated
catalog, missing-JWT rejection, a Secure JSESSIONID and external backend refusal.
It contains no browser authoring, recovery or shutdown collection. Fixed diagnostic
categories suppress captured command output; diagnostic interruption cannot bypass
cleanup, and cleanup attempts every labeled resource category after Compose down.

The suite reuses its existing runtime. Current public JWKS/JWT claims replace the
retired fixture; the plain Java driver still asserts completion, original failure
and acceptance-unknown/no-replay with independent counters. Shutdown scenarios
wait on actual admitted callback entry before TERM and distinguish completion
from the single Framework budget cutoff. Transport tests establish positive
readiness before specific TLS rejections; unavailable services cannot count as
certificate rejection. The supplied Caddy probe verifies upstream/forwarded-header,
redirect and namespace boundaries. Per-run certificates are prepared before
startup; no persistence, replacement, renewal or reload scenario was reintroduced.

Deleted browser/management/recovery collections have concrete retained assertion
owners, including browser draft/handoff, import/history, HTTP authorization/CSRF,
mail-origin/failure, credential portability, destination URL binding, complete
SQLite backup/restore and selected-state startup refusal. Removed Docker-volume
shell choreography and public/DNS adaptation checks are explicit plan decisions.
Actual-process shutdown and direct TLS/Caddy checks retain executable suite owners.

## Findings Resolved in This Context

None; no implementation artifact edits.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One bounded shipped-recipe smoke with success/failure cleanup | `scripts/verify-deployment.py`; one invocation in each workflow | Fresh real smoke and post-start failure injection; eight helper tests | implemented |
| Retained component, browser, trust/lifetime and API-boundary regression coverage | Only environment-composed browser class removed; focused owners retained | Fresh root clean verification through normal SDK command: 61 classes, 261 tests, no failures/errors/skips | implemented |
| Unique composed checks retained before legacy removal; duplicates accounted for | `docs/verification.md` inventory and existing SDK Runtime extensions | Fresh five-scenario Local report linked below; inspected retained assertions | implemented |
| Current baselines distinct from future full SDK recipes | Explicit baseline scenario names; Standard/Hardened empty selectors fail closed; 8.1.5–8.1.8 reconciliation | 48 infrastructure tests include future selection failure; real current baselines pass | implemented |
| Certificate lifecycle removed, transport/support retained | Old TLS verifier/helpers deleted; per-run transport material; unchanged operator recipes | Wrong CA/hostname/expired rejection and supplied Caddy runtime checks; retained outbound mTLS test passes in root suite | implemented |
| Independent Sidecar CI and nonrecursive suite | Root POM/workflows do not call suite; suite builds root once without smoke invocation | ReleasePreparationTest in fresh root suite; runner build-call tests | implemented |
| Honest reduction evidence | Guide distinguishes old project/service/process counts, historical durations and current observations | Fresh Local total 452.501s, root build 304.846s; smoke 18.568s excludes fixture preparation | implemented |
| Disposable/provider-free verification and preserved data | Temporary configs/tokens/certs, internal SDK network, fixed local model endpoints, unique projects, environment filtering | Sanitization tests; actual cleanup; independent project-label checks | implemented |

## Active Project Guardrails

- Full profile remains appropriate for cross-repository coverage ownership and
  deployment/security assurance. No profile upgrade or scope decision is required.
- Superseded verifier paths are deleted directly; no wrappers or compatibility
  paths. Existing runtime is reused with proportional fixtures and no production
  recipe allowlist.
- Application behavior, shipped listener topology, framework pin, versions and
  supported public API boundary are unchanged. Fresh ArchUnit/lifecycle tests
  pass; Framework tests were not substituted for Sidecar evidence.
- DevOps certificate lifecycle ownership and standard Spring/Caddy guidance are
  preserved. JWT trust/lifetime obligations remain distinct.
- No credentials, operational databases, business runtime or maintained reference
  packs were modified. No reset, paid/provider/live CA/DNS, release, commit or push
  operation was performed.

## Open Questions and Assumptions

None affecting current correctness. Future SDK Spring issuance/callback security,
full Standard/Hardened bidirectional recipes and group/JWT rotation remain their
explicit owning tickets; current baselines do not claim those contracts.

## Verification Results

- PASS — `python -m unittest discover -s scripts -p 'test_verify_deployment.py' -v`
  — eight tests, including cookie negatives, primary failure preservation,
  diagnostic interruption and fallback cleanup continuation.
- PASS — `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`
  (suite root) — 48 tests.
- PASS — `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  (suite root).
- PASS — `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`
  (suite root) — normal full build path and all five scenarios/cleanup pass.
  Fresh report: [20261018T214548Z-9780433dac88](../../../../loomspan-sidecar-test-suite/evidence/sdk/20261018T214548Z-9780433dac88/report.json).
  Root clean verify produced 261 tests/61 classes, zero failures/errors/skips.
  TERM completion 2.590s, cutoff 3.901s, both exit 143/no OOM; expected independent
  detail/finished state. Direct CA/hostname/expiry rejections and Caddy normalization,
  fixed upstream, redirect and wrong-trust rejection pass. Total 452.501s.
- PASS — `python scripts/verify-deployment.py --image loomspan-sidecar:deployment-smoke`
  — all assertions and cleanup passed, 18.568s orchestration time, using the existing
  actual built image. No additional product inputs required rebuilding it for
  this review; the normal SDK invocation independently rebuilt the current image.
- PASS — Docker-backed inline `python -` failure exercise below — retained fixed
  primary diagnostic, failed status, passed cleanup (19.685s), no labeled resources.
- PASS — independent `docker container ls -aq`, `docker network ls -q`, and
  `docker volume ls -q` with `--filter label=com.docker.compose.project=sdk-bc339042db754666`
  — no resources after the fresh Local run.
- PASS — `git diff --check` in both repositories; active legacy-entrypoint and
  lifecycle-operation source audits found no active retired caller/lifecycle test.
- NOT RUN — hosted CI/release workflows, live provider/CA/DNS and future full
  Standard/Hardened recipes; outside authorized scope. Their absence is not counted
  as current baseline evidence.

Reproducible post-start failure exercise (PowerShell here-string piped to Python):

```powershell
@'
import importlib.util,tempfile,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('deployment',Path('scripts/verify-deployment.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
with tempfile.TemporaryDirectory(prefix='review2-failure-') as temporary:
    runtime=m.Runtime(Path(temporary),'loomspan-sidecar:deployment-smoke')
    original=runtime.assertions
    def injected():
        original()
        raise m.Failure('controlled-review2-failure')
    runtime.assertions=injected
    report=m.verify(runtime)
    assert report['status']=='failed' and report['diagnostic']=='controlled-review2-failure' and report['cleanup']=='passed'
    for resource in ('container','network','volume'):
        assert not m.command(['docker',resource,'ls','-q','--filter','label=com.docker.compose.project='+runtime.project]).strip()
    print(json.dumps({'project':runtime.project,**report,'resourcesRemaining':False}))
'@ | python -
```

## Residual Risks and Optional Developer Checks

Hosted Linux CI was not executed here. Local Docker exercised the Linux container
boundaries; source inspection confirms ordinary Ubuntu tool prerequisites and
independent workflow wiring. No optional developer check is needed for this ticket.
Existing SDK image tags remain intentionally available, as documented; containers,
networks and data volumes are disposable. Future full recipe obligations remain
required in their own tickets, not manual substitutes for missing current evidence.

## Disposition

`clean` — no actionable findings, no implementation edits, and sufficient fresh
component, composed runtime, smoke and failure-cleanup evidence.
