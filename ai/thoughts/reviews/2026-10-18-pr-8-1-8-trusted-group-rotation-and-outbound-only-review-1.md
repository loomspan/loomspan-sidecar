# PR 8.1.8 Code Review — Cycle 1

## Scope and Repository State

Independent pipeline-mode Step 5 under the approved Full 5-Step Pipeline. Read
the ticket, research, implementation plan, testing plan, design lens, both root
AGENTS files and suite project/runtime guidance. No prior review was read.

Comparison is the complete current working-tree change from HEAD in both
repositories: Sidecar `f0ea254e0c41e09ab900f58c1773e407575df986`, suite
`6e65846ee833ee91f308e07ecb8662fe56839a16`, both on `main`. Both were clean before
the pipeline; no staged changes or unrelated dirty work were found. Included
untracked plans, research, verification/cleanup evidence, three Python modules
and both Java fixture entry points, rather than relying on ordinary diff alone.

Reviewed all changed code, tests, configuration composition, operational guidance
and evidence. Production Sidecar/SDK source is unchanged. Traced the supporting
current-file trust loaders, startup signer initialization, callback servlet
security, authenticated issuance, method/record checks, current trust before
Framework admission, and result polling beyond the changed hunks.

## Findings

No actionable findings.

The independent behavior/security review preceded requirements comparison:

- Standard really starts two JVMs, using the same signer and directory definition
  but different HTTPS ports and exports. B's authenticated submission reaches A's
  callback. B's unchanged counters plus A's exact arrival/body/effect counts
  distinguish the receiver and verify denied users do not perform effects.
- The candidate helper issues using public SDK APIs and a separate startup signer;
  it does not replace a bean or mutate either normal app's signer. Distinct
  operator files survive app export/restart. Before/after receiver probes establish
  signature acceptance, rather than trusting decoded JWT headers as validation.
- Add/removal steps mutate only one receiver's file at a time and probe the
  unchanged opposite receiver. Successful replacement probes prevent an unavailable
  receiver from satisfying the rejection oracle. Monotonic five-second checks
  bracket each publication; old-token expiry remains more than ten seconds away.
  Both normal signers restart before callback own-key retirement.
- The old-key blocker is submitted once after restarts. Real model entry plus
  RUNNING establishes Framework admission before retirement; the barrier is released
  on failure and exact terminal completion is checked with fresh owner credentials.
- Outbound-only is a non-web JVM with conditional servlet beans, model-only skills
  and empty targets/routes. It waits for live listener checks before the single
  submission. Successful Java result validation, actual model counts and clean
  process exit jointly back the observation schema. Hardened retains invocation
  mTLS and omits callback/browser ingress and callback egress.
- Fixed fixture destinations/slots keep credentials in memory; no arbitrary URL
  relay or credential-bearing report path was added. Failure categories remain
  sanitized. Temporary projects have scoped cleanup even on partial startup or
  restart/probe failure. No persistence/schema migration is introduced.

The constant success schemas are receipts after executable assertions, not their
substitute. Harness fakes prove composition, sequencing, typed observation rejection
and cleanup; actual formal Docker reports supply composed authentication evidence.
The live-listener regression can detect a genuinely open port. No speculative
cleanup, style-only or pre-existing issue is promoted to a finding.

## Findings Resolved in This Context

None. This context changed only this required review document.

## Acceptance-Criteria and Plan Conformance

Suite paths below are relative to `C:/opendev/code/loomspan-sidecar-test-suite`.

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Two apps share issuer/key/Sidecar; cross-app callback accepted | `remote.py` group composition/config; `rotation_probe.py::add`; shared Java directory | Group topology regression; real Standard group asserts B submission/A identity, exact roles and opaque equality; same public kid | implemented |
| Unauthorized group users/records denied | Existing `CallbackController` method/issuer/subject checks; authenticated execution reads | Group literal/Bob failures, A counters 3/2/1, Bob result 404, same-owner A read | implemented |
| Independent add/restart/overlap/remove | Separate `/trust/sidecar.json`, `/trust/callback.json`, per-app exports; `Runtime.rotation`; fixed old/candidate/current slots | Candidate initially 401/401, staged add 200/401 then 200/200; real restarts and fresh kids; old overlap; staged removal 401/200 then 401/401; replacement accepted | implemented |
| Bounded adoption before expiry | `rotation_probe.py::publish`, metadata expiry guard | Actual receiver acceptance observed within five seconds from each atomic publication; >10 seconds old-token validity checked after removals; timeout harness regression | implemented |
| Admitted work continues | Fixed model barrier; `retire` saved-old submission/RUNNING/terminal assertions | Standard group RUNNING before retirement, model entry marker, exact COMPLETED result afterward | implemented |
| Trust changes only JWKS, both application modes | File publication only in group; existing `CurrentFileJwtTrust` wiring | Fresh focused `ConfigurationModeIntegrationTest` file/database trust checks; mandatory-build evidence; no duplicated mode composition | implemented |
| No-listener invocation under all recipes | `OutboundOnlyRunner`, `outbound.configure/adapt`, Hardened Caddy omission | Local/Standard/Hardened formal outbound-only results; runtime refused ports while app alive; no callback routes; one model call; clean exit | implemented |
| Operator guidance | `docs/operations.md`, `docs/remote-recipes.md` | Reviewed against actual startup-only signers, callback own-key behavior, independent polling, no-callback transports and full mutual-trust boundary | implemented |
| Scenario/revision/result evidence, no secrets | Formal reports, validation artifact, both verification guides | Report stage/schema/hash inspection; current final JAR hash equality; secret scan; independent scoped Docker absence | implemented |
| Stable namespace and cross-namespace marker adaptation | Sleep-only `app-network`, fixed barrier directory mounted only to fixtures | Group composition regression and final Standard run; bounded model timeout retained | safe deviation |
| Proportional tests/public API/pins | One Standard group; three focused no-callback cases; SDK and Spring public APIs only | Broad Python pass; focused public API architecture pass; no production, business-lane, release or dependency-pin edits | implemented |

## Active Project Guardrails

- Destructive development replacement policy and design lens are prominent in
  the ticket/plans. No compatibility machinery or development-data reset added.
- Public Framework boundary remains intact. New Java helpers use public SDK and
  standard Spring/JDK APIs; the existing architecture test independently passes.
- Framework remains admission/lifetime authority. Assertions observe actual
  Sidecar/Framework behavior; framework-only tests are not used as Sidecar proof.
- Signing and receiver trust have explicit independent owners/timing. No private
  key hot reload, database/management trust update, cancellation promise or
  uninterrupted-rotation claim was added.
- Full mutual trust and separate-Sidecar isolation guidance are explicit.
  Authenticated identities retain literal authority names and issuer/subject ownership.
- Credentials remain in protected in-memory fixture slots; only public kids,
  booleans, statuses, counters, revisions and hashes enter evidence.
- Sidecar beta.4-SNAPSHOT and Framework beta.8 pins are preserved. Business beta.9
  runtime, operational data/credentials, maintained packs and provider access are untouched.

## Open Questions and Assumptions

None affecting correctness or completion. The fixture's fixed Alice identity in
the standalone candidate/non-web process is inside the explicitly trusted test-app
boundary; the group user-facing flow still authenticates through real form login.

## Verification Results

Commands in suite cwd unless marked Sidecar. These results were obtained in this
fresh review context, after reviewing the implementation:

- PASS — `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py tests/test_sdk_remote.py -q` — 136 passed, 44.60 seconds.
- PASS — `.venv/Scripts/python.exe -m pytest tests -q` — broadest safe Python regression, 246 passed, 58.26 seconds.
- PASS — `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py` — 16 files formatted.
- PASS — `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py tests/test_sdk_remote.py` — all checks passed.
- PASS — `git diff --check` — both repository working directories.
- PASS — `mvn -B -ntp '-Dtest=ConfigurationModeIntegrationTest,CurrentFileJwtTrustTest,SupportedLoomspanApiArchitectureTest' test` — Sidecar cwd; 15 tests, zero failures/errors/skips, BUILD SUCCESS, 33.009 seconds.
- PASS — read-only Python report/artifact/XML audit through `.venv/Scripts/python.exe -` — all three final reports have 13/8/9 passed scenarios respectively, every mandatory build stage passed, no failed stage, beta.8 pin, revision identities and well-formed SHA-256 values. Current built Sidecar/reference-app JARs exactly match the final Hardened report. Before the fresh focused Maven run, inspected full-build XML showed root 261, SDK core 25, starter 30 tests, zero failures/errors/skips.
- PASS — read-only exact-project Docker audit through `.venv/Scripts/python.exe -` — 20 final projects, 99 empty listings. Commands executed for each project were `docker ps -aq --filter label=com.docker.compose.project=PROJECT`, `docker network ls -q --filter label=com.docker.compose.project=PROJECT`, `docker volume ls -q --filter label=com.docker.compose.project=PROJECT`, and `docker ps -aq --filter label=loomspan.sdk.helper=PROJECT`; each explicit external volume also used `docker volume ls -q --filter name=^VOLUME$`. PROJECT/VOLUME values were read directly from the final three reports, plus Local base project `sdk-eea384b57cea4bcc`. The retained implementation cleanup JSON independently has 34 projects/169 checks, all marked absent; this review did not substitute those claims for its fresh final-project listings.
- PASS — source/report secret-pattern scan through `.venv/Scripts/python.exe -` — 32 changed/untracked/evidence files, no actual private PEM payload or compact JWT match; matching content would never be printed.
- NOT RUN — `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`, and equivalent `standard`/`hardened` full commands in this review context. Fresh Step 4 reports were independently inspected against actual runner assertions, build artifacts and scoped cleanup; all selected scenarios passed. No production source, Java implementation or executed behavior changed after those runs. Repeating all mandatory builds/compositions adds no identified assurance benefit after this review's focused/broad regressions. No cached-build bypass was used.

Formal composed reports inspected:

| Recipe | Run ID | Observed final result |
| --- | --- | --- |
| Local | `20261019T032901Z-8d4833bcc6c8` | 13 passed, including non-web outbound-only |
| Standard | `20261019T031355Z-aa841eadf316` | 8 passed, including group/rotation and outbound-only |
| Hardened | `20261019T033931Z-5bb5875da104` | 9 passed, including outbound-only mTLS |

The retained failed initial Standard run is not counted as acceptance. Final
successful evidence covers its corrected topology/mounts. Reports identify dirty
source checkouts; HEAD alone is not a complete identity for uncommitted inputs,
which the documentation explicitly acknowledges.

## Residual Risks and Optional Developer Checks

Evidence is limited deliberately to one fully trusted Standard group and focused
no-callback cases in three recipes. Unknown-kid reads can accelerate adoption;
five seconds is the test observation allowance, not an exact production cutoff.
No private-key hot reload, uninterrupted rollover, multi-issuer/replica behavior,
application isolation, certificate lifecycle or production rollout is certified.
None of those is required by the ticket. Optional developer checks: none.

## Disposition

`clean`: full independent review completed, no actionable findings and no
implementation-artifact edits. All acceptance criteria have sufficient executable
or operator-document evidence. Fresh focused Maven verification also passed.
