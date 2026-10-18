# Current-file JWT trust and ownership Code Review — Cycle 1

## Scope and Repository State

Independent Step 5, pipeline mode, approved profile `full`. Comparison base is
the initially clean HEAD `ab96258c17fb49e60ea52e51ceb4048e89569983` on main.
Reviewed staged/unstaged/untracked ticket work, including all new security
classes, fixtures, research/plans, deployment samples, Dockerfile, scripts and
operator/portable guidance. No prior review documents were read. Read the ticket,
research, implementation plan, testing plan, AGENTS.md, design lens, review command
and automation protocol. Traced callers and cleanup beyond changed hunks.

The developer correction governs: shipped health shares the application listener;
existing management sessions and read PATs control details. No shared-session
subsystem or shipped additional listener is required.

## Findings

### [P2] Name the actual production namespace services in recreation commands
- Location: `docs/admin-access.md:99` and `docs/admin-access.md:104`.
- Scenario: after setup, an operator follows the production command to remove
  SETUP_TOKEN. It named `sidecar host ingress`, but production compose defines
  `sidecar caddy` and expects the operator to supply their application.
- Impact: Compose rejects the command because host/ingress do not exist; the
  intended credential-environment cleanup and namespace recreation fail.
- Evidence: compared both documented commands with the fully rendered production
  service definitions and the production namespace deployment guidance.
- Fix: use `sidecar caddy`; explicitly include the operator's colocated app in
  the same recreation command.

### [P2] Isolate malformed-key mutations from duplicate-kid rejection
- Location: `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java:48`
  and `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java:57`.
- Scenario: malformed n/e/use/alg and omitted n/e cases paired a valid original
  key with a mutated copy retaining its kid. The validator rejects duplicate kid
  before reaching those checks.
- Impact: required negative security tests could stay green after removal of
  the very validations they claimed to prove.
- Evidence: followed validator check ordering against each generated candidate;
  private-member and small-modulus cases already had distinct kids.
- Fix: give mutation candidates a distinct valid kid before the individual
  mutation; prove the otherwise-valid two-key candidate is accepted as a control.

## Findings Resolved in This Context

Both findings above are fixed. The complete change was reviewed before editing.
Only `docs/admin-access.md` and `CurrentFileJwtTrustTest.java` were changed by this
review, apart from this audit artifact. No production code or governing plan was
changed. Final internal re-review found no remaining actionable findings.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Current file in both application modes | Trust bean is process configuration, outside managed publication | ConfigurationModeIntegrationTest replaces/withdraws file in both modes | implemented |
| Required mode, selected path, loopback and no packaged mode | SidecarTrustProperties.resolve; application.yml | JwtStartupIntegrationTest; SidecarDefaultsTest; ConfigurationReferenceTest | implemented |
| Whole-file RSA/key/private-member rules; ignored x5c | CurrentFileJwtTrust.validate publishes only complete immutable maps | CurrentFileJwtTrustTest, with isolated negative candidates corrected here | implemented |
| Missing/unreadable/malformed/empty withdrawal and recovery | Empty waiting snapshots; serialized one-second polling | Loader state/I/O tests and snapshot-only HTTP scheduler recovery in both trust modes | implemented |
| Atomic publication and globally bounded unknown kid | Volatile immutable snapshot; synchronized loader; monotonic global limiter | Atomic readers/equal-metadata test; concurrent unknown kids and newly added kid | implemented |
| Signed execution profile, raw claims, audience and skew | ExecutionJwtDecoder uses fixed RS256/purpose/type and current key; raw shape checks precede conversion | JwtSecurityConfigurationTest signed negative matrix, wrong key/tampering, fractional dates, skew and URL trap | implemented |
| Removed configuration and credential separation | Reduced SidecarJwtProperties; distinct management and execution chains | Reference/defaults, authenticated API, console and PAT regressions | implemented |
| Public health status, management details and safe logging | executionTrust; MGT_VIEWER visibility; existing session/PAT guards | HTTP session/PAT/revoked/expired/disabled/idle checks; captured safe logger | implemented |
| Exact roles and public Framework boundary | Empty GrantedAuthorityDefaults and literal role converter | Real Framework ExactRoleExecutionIntegrationTest; architecture tests | implemented |
| Issuer/subject owner and captured-token preservation | ExecutionOwner reads exact validated string; retained owner comparison | AuthenticatedExecutionApiIntegrationTest fresh owner, other subject/issuer and unchanged callback bytes | implemented |
| Strict queued expiry/current trust; admitted work continues | Re-decode captured bytes plus strict checks before public handoff; existing completion cleanup | Coordinator mutable-clock/latch tests and real current-file withdrawal integration | implemented |
| Strict callback expiry and approved unchanged forwarding | GenericRestSkillHandler checks before exchange; existing disabled redirects/retries and configured targets | Handler zero-send/equality/missing-exp/second-callback/redirect tests; nested integration | implemented |
| Samples and Local documentation | Colocated namespace sharing, deployment-created directory, RO trust mounts and ingress; operations/portable reference | Reference tests; Compose rendering; independent image smoke; Python guidance checks | implemented |
| Single application listener correction | Removed packaged port and extra ingress; management health matcher | Same-listener startup/management tests and image verifier | safe deviation from superseded additional-listener plan |
| No data reset, aliases or Framework extension | Process-only trust; unchanged schema and dependency; public API usage | Full regression and architecture coverage; matching beta.8 public authorization docs inspected | implemented |

## Active Project Guardrails

- Destructive replacement is coherent: old key sources/prefix/skew settings have
  no binding or alias; no migration, trust ledger or last-good path was added.
- Framework beta.8 public documentation expressly supports empty
  GrantedAuthorityDefaults for YAML roles. Application and test architecture
  enforcement retains the internal/autoconfigure prohibition and no Java skills.
- One coordinator owns capacity/queue accounting. Revalidation failures flow
  through existing immutable terminal publication and task-reference cleanup.
- Credentials remain outside model/business inputs and are captured unchanged.
  No signing/refresh in Sidecar, token-directed fetch, redirect following or
  automatic retry was introduced. Callback receivers keep independent trust.
- Managed publications remain separate from process trust. No database/account/
  token/draft reset, deployed data action, Framework rebuild or commit occurred.
- Polling and unknown-kid reads share one serialized loader; authentication may
  finish on its captured snapshot as explicitly documented. Already admitted work
  is not cancelled. Scheduler closure is owned by the Spring bean lifecycle.

## Open Questions and Assumptions

None affecting completion. Standard Boot operator overrides may add a listener;
colocated still validates its loopback bind, while none is shipped. Development
database publications contain no removed JWT process properties.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp verify` — 264 tests, zero failures/errors, three environment-gated production browser skips; all ordinary management browser tests ran. Includes architecture, profile, startup/health, current-file wiring, ownership, queue/callback lifetime and shutdown regressions (`target/pr814-review1-verify.log`).
- PASS — `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest' test` — 5 tests after recompiling the unique-kid mutation/control fix, zero failures/errors/skips (`target/pr814-review1-jwks.log`).
- PASS — `python scripts/sidecar_version.py check` — Sidecar beta.4-SNAPSHOT and Framework beta.8 metadata agree.
- PASS — `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v` — 7 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_agent_skills.py' -v` — 6 tests.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — 11 tests.
- PASS — `python scripts/test_verify_shutdown.py` — 2 tests; printed log-timeout messages are intentional mocked failure cases.
- PASS — `python -m py_compile scripts/verify-image.py scripts/verify-production.py scripts/verify-production-tls.py scripts/verify-shutdown.py examples/quickstart/host/host.py scripts/fixtures/shutdown-host.py`.
- PASS — `docker compose -f examples/quickstart/compose.yaml config --quiet`.
- PASS — `docker compose --env-file examples/production/production.env.example -f examples/production/compose.yaml config --quiet`.
- PASS — `docker compose --env-file examples/production/production.env.example -f examples/production/compose.yaml config --services` — actual services are sidecar and caddy, matching corrected commands.
- PASS — `python scripts/verify-image.py --image loomspan-sidecar:pr-8-1-4 --api-port 38080 --host-port 38081` — independent disposable colocated smoke, health, authentication, setup, credential removal/recreation and recovery; cleanup succeeded (`target/pr814-review1-image.log`). This context changed no packaged runtime artifact.
- PASS — `git diff --check`.
- NOT RUN — `python scripts/verify-production.py --image loomspan-sidecar:pr-8-1-4` in this review context — reviewed complete changed verifier/topology and used independent Compose rendering and image execution; management browser workflows are additionally covered by full Maven tests. Step 4 records its full production/SIGTERM execution, which is not relabeled as an independent pass here.
- NOT RUN — `python scripts/verify-production-tls.py --image loomspan-sidecar:pr-8-1-4` — syntax and changed direct/namespace assumptions reviewed; no new TLS behavior was introduced or required by the ticket.

## Residual Risks and Optional Developer Checks

Operator-specific OS permissions, UID/fsGroup behavior and real TLS material are
optional deployment observations. Kubernetes is documentation only. External
PR 8.1.2 composed work remains intentionally excluded by ticket Pipeline notes.

## Disposition

`fixes-applied`. Both findings are resolved; final internal re-review and
verification found no remaining actionable issue. Launch a fresh independent
Step 5 context because this cycle changed implementation artifacts.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership-review-1.md
  - docs/admin-access.md
  - src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java
SUMMARY: Reviewed the full ticket change independently and resolved two bounded findings. Production recreation instructions now name real services, and malformed-JWKS tests isolate individual key failures from duplicate-kid rejection. No remaining actionable issues were found in internal re-review.
DECISIONS:
  - Preserved the approved full profile and developer single-listener correction; no new product or framework contract was needed.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `.\mvnw.cmd -B -ntp verify`: 264 tests, zero failures/errors, three environment-gated production-browser skips.
  - PASS — `.\mvnw.cmd -B -ntp '-Dtest=CurrentFileJwtTrustTest' test`: 5 tests after the review fix.
  - PASS — `python scripts/verify-image.py --image loomspan-sidecar:pr-8-1-4 --api-port 38080 --host-port 38081`: disposable smoke and cleanup.
  - PASS — Python guidance/version/client/shutdown checks, syntax, both Compose renderings and `git diff --check`; exact commands above.
  - NOT RUN — Full production/TLS verifier in this review context; independent static/sample and HTTP evidence above covers this review's bounded fixes.
OPTIONAL_DEVELOPER_CHECKS:
  - Operator-specific directory permissions, Kubernetes fsGroup behavior and actual TLS material remain optional deployment observations.
REVIEW_RESULT: fixes-applied
NEXT: Launch a fresh independent Step 5 review context.
