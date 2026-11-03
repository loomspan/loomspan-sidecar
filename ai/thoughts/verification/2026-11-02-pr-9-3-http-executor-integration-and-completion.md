# HTTP executor integration and completion verification

Date: 2026-11-02, America/Los_Angeles. Pipeline Step 4, profile `full`.

**Development policy: replace superseded contracts directly; no compatibility
shims, adapters, dual APIs or migration machinery.** Apply the
[design lens](../design-lens.md#simplicity-and-technical-debt).

## Scope and repository state

Sidecar baseline: `27108f1b46ad037bfc54054897282c67d82c3626`, `main`.
Suite baseline: `1a79ba7c5ad106665bddfec363d5737a2df8ebe4`, `main`.
Both started clean. The pipeline added research/plans and owns the current changes.
No Framework sources/build, operational stores, credentials or accepted references
are changed. No paid provider calls, source-repository commits, pushes, tags or
publications run. Version-tool unit tests create only their owned disposable
fixture commits/tags, as planned.

## Implemented changes and decisions

- Equipment generator/checked-in leaves use per-skill HTTP operations and
  whole-input POST bodies. Lookup inputs remain flat; creation retains approval.
  Shared model declarations remain byte-identical. Separate route catalogs and
  assembly were removed; duplicate skill-name checks remain.
- SDK foundation, Local, Standard, Hardened, shutdown and outbound-only generators
  use the same executor contract. Bodyless callbacks omit body configuration.
  Remote targets come from shipped `execution.yaml`. Outbound-only keeps the
  required empty selected `http` map for the annotated executor, without targets.
- Python public authentication remains Keycloak-owned. App-issued execution JWTs
  retain verified subject, exact allowlisted roles and source-bounded expiry.
  Callback execution authentication independently enforces current public trust,
  purpose/type, audience/issuer and expiry. Recovery looks up the same app issuer
  as callback-created records. Private signing material reaches only Python.
- Runtime preparation preserves existing keys/credentials. Current Sidecar JAR
  staging replaces source-tag export. Recording verifies declared Framework
  coordinates and per-package/configuration integrity without source provenance.
- Release preparation targets beta.9, CI follows the POM pin, and the disposable
  tag fixture explicitly uses a released dependency. Actual packaging/tag guards
  still reject snapshot dependencies. No released pin/publication is performed.
- Current suite guidance describes active executor/build/authentication behavior.
  One invalid recovery fixture now references a nonexistent executor instead of
  the removed REST declaration; it still tests safe rejection and pointer retention.

## Isolation and data preservation

Fresh detached workspace: `C:/opendev/code/loomspan-suite-pr9-3-4716d69d0f`.
Compose project: `pr9-3-4716d69d0f`; host port offset: `10000`.
Only ticket source changes are synchronized; retained `.runtime`, keys, stores,
outputs and `.build` are not copied. The fresh workspace owns new credentials,
keys and stores. Reference hashes are recorded in the maintained suite's ignored
`.runtime/pr9-3-workspace.json` for before/after comparison. No reset is required.

The new Python execution identity issuer is `equipment-app`; callback-created
request ownership and authenticated recovery consistently use that issuer and
the verified source subject. Existing development receipts created under the
superseded external-token issuer are not silently relabeled or migrated. Their
bytes remain untouched, as do the already-incompatible pre-9.2 Sidecar stores.
Adopting this configuration in a retained workspace requires developer-owned
assessment of that existing development state; no reset or deletion is performed.

## Verification results

Commands below are relative to their named repository. Results are actual runs.
Step 4 implementation and required safe local verification are complete; fresh
independent Step 5 review remains separate.

| Repository / command | Result |
| --- | --- |
| Sidecar `./mvnw.cmd -B -ntp '-Dtest=ReleasePreparationTest' test` before changes | Expected FAIL: 1 test, successful-plan assertion rejected the obsolete Framework line |
| Sidecar `python -m unittest discover -s scripts -p 'test_sidecar_version.py' -v` before changes | Expected FAIL: tag success fixture inherited snapshot dependency; 6 passed, 1 error |
| Sidecar `python -m unittest discover -s scripts -p 'test_*.py' -v` | PASS: 21 tests |
| Sidecar `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` | PASS: 11 tests |
| Suite `.venv/Scripts/python.exe -m pytest tests/test_execution_identity.py tests/test_runtime_build.py -q` | PASS: 15 tests |
| Suite `.venv/Scripts/python.exe -m pytest tests/test_sdk_remote.py -q` | PASS: 47 tests after correcting the shipped target file path |
| Suite `.venv/Scripts/python.exe -m pytest -q` | PASS: final 268 tests in 65.62 seconds, `.runtime/pr9-3-pytest-final.log` |
| Suite `mvn -B -ntp -f apps/java/pom.xml verify` | PASS: 2 tests, packaged host |
| Fresh workspace `mvn -B -ntp -f apps/java/pom.xml clean package` | PASS: 2 tests, packaged isolated host |

## Deferred blocker reconciliation

| Earlier deferred boundary | Current 9.3 evidence |
| --- | --- |
| 9.1 coordinator/runtime references to deleted generation/REST types; incomplete namespace calls | Full clean Maven compile/test/package/verify succeeds using the 9.2 generation and complete-namespace implementation; no old types remain in active source |
| 9.1 execution/catalog namespace alignment | Authenticated/correlation public API tests, qualified SDK catalog assertion and all eight composed equipment paths |
| 9.1 full startup, managed persistence, management header scanner and shutdown | Runtime/management/configuration tests, `HttpConfigurationIntegrationTest`, `HttpConfigurationShutdownIntegrationTest`, Local normal/cutoff shutdown; HTTP-invalid settings accepted without network |
| 9.1 full-project supported API architecture | Both `SupportedLoomspanApiArchitectureTest` cases and HTTP architecture case pass in the full 300-test build |
| 9.2 beta.8 release-preparation failure preventing package/verify | Beta.9 prospective plan and actual snapshot release guards pass; normal clean package/verify completes |
| 9.2 successful-tag fixture inherited a snapshot | Disposable released-dependency fixture corrected; all 21 script tests pass and real snapshot rejection remains |
| Earlier Docker/representative transport checks unrun | Isolated business smoke and 8+224 grouped replay pass; Local complete pass, final Standard/Hardened results recorded below |

## Acceptance evidence map

| Ticket criterion | Implementation and executable evidence |
| --- | --- |
| 1. Invocation-only HTTP domain validation | `HttpConfigurationIntegrationTest` (5), configuration/file/management/recovery tests: startup/publication/restoration, Framework-invalid rejection and zero-network invalid invocation |
| 2. Equipment/SDK executor integration | Nine generated leaves, whole-input bodies, current target maps; generator pytest, real eight-lookup/nested-approval smoke, exact 8+224 grouped replay and maintained SDK lanes |
| 3. Direct/tool fidelity, auth, bounds and no replay/state | `HttpExecutorIntegrationTest` (2), `HttpSkillExecutorTest` (14), `HttpRequestMapperTest` (12), configuration (27), failure envelope; real SDK exact token/bodyless and one-attempt/effect response-loss counters |
| 4. Publication/restoration/shutdown ownership/lifetime | HTTP lifecycle (4), configuration/shutdown (5+2), execution correlation/shutdown (5+3), Local completion/cutoff and ownership observations |
| 5. HTTPS/Caddy trust boundaries | `HttpExecutorTlsIntegrationTest`, Standard normal JVM public CA trust and Hardened actual Caddy client/peer/isolation cases; no executor client-certificate feature |
| 6. Prior blockers and full checks | Reconciliation table, complete clean Sidecar 300-test build, Python 21+11, suite 268, embedded Java 2, complete maintained lanes and grouped replay receipts |
| 7. Current guidance and public-only dependencies | Direct obsolete schema/route removal; matching local docs refresh; active scans below; supported API ArchUnit and full compilation |
| 8. Honest local/release evidence | This artifact, field-level namespace ledger and unchanged pack hashes; explicit transient failure and remaining publication/hosted-CI gates; fresh Step 5 is separate |

The exact root integration test names/counts above are observed current Surefire
receipts, not inherited Framework test claims. Complete maintained lane results
are recorded below.

### Initial replay incompatibility and resolved developer decision

The real grouped mock run stopped at the first baseline/Java request with
`unexpected or ambiguous stage`. Actual `request_contract.skill` is
`default-resolveEquipment`; frozen `requestContract.skill` is `resolveEquipment`.
All other top-level semantic request fields compare equal for this first request.
The frozen pack contains 15 steps for that scenario; none was consumed. Evidence:
isolated `evidence/pr9-3-4716d69d0f/equipment-service/report.json` and `bundle.zip`.
The report records failure, zero paid calls/commitments, prior-row preservation
and provider-disabled runtime restoration. No reference or matching rule changed.
The developer subsequently authorized "Authorize namespace-only contract update".
The separately preserved current contract is `fixtures/reference-namespaced`;
the original `fixtures/reference` and reviewed TypeSafe pack remain byte-identical.
`namespace-review.json` records source/current hashes and 316 explicit before/after
identity changes. Only request skill/evidence identifiers, the explicit assigned
capability line, and structural planner capability/tool identifiers are qualified.
All other reply characters, business inputs/evidence/judgments and complete expected
results remain unchanged. There is no runtime converter, fallback or relaxed matcher.
Fourteen replay tests include an independent recursive diff that rejects changes
outside those exact positions and checks all 316 ledger entries against the files.

The first current-contract baseline/Java run consumed all 15 responses exactly
once, completed, and published the exact original accepted result with no fixture
rejection. Its remaining failed checks exposed unqualified route selectors in the
equipment trace analyzer. Those selectors and synthetic trace fixtures now use
exact canonical names; all 22 recorded baseline business/publication checks pass
when evaluated against its preserved trace. The fresh complete grouped run passes
as recorded below.

Initial TypeSafe reference import failed because Git's new worktree checkout
converted fixture line endings. Copying maintained tracked fixture sources
byte-for-byte into the isolated workspace restored original checksums. This was
source synchronization, not a pack change; before/after maintained hashes match.

### Additional executed results

- PASS — full Sidecar `mvn -B -ntp -f C:/opendev/code/loomspan-sidecar/pom.xml clean verify`
  inside the first maintained Local command: 300 tests, no failures/errors/skips.
- PASS — isolated `scripts/start.py --build`, all five ready/provider-disabled,
  after correcting stale readiness/Docker health port 9091 to application port 8080.
- PASS — isolated `scripts/smoke.py`: both real integrations, eight flat lookups,
  nested creation rejected, no request rows created, prior rows preserved and
  zero model calls. Evidence `.runtime/smoke.json` and `smoke-run-4.log`.
- FAIL — first maintained Local SDK command, output
  `evidence/sdk/pr9-3-local-4716d69d0f`: full required builds, foundation and exact
  client/failure/no-replay checks passed; shutdown fixture did not enter work
  because it still returned obsolete unqualified model identities. Cleanup passed.
- PASS — focused actual SDK normal/cutoff shutdown after qualifying owned canned
  fixture names: completion observed entered/detail/finished; cutoff observed
  entered with no detail/finish, normal exit 143, no OOM. This diagnostic does not
  replace a fresh full maintained Local run; that subsequent complete pass is below.
- PASS — suite full pytest after body/health changes: 262 tests. Later SDK canned
  fixture assertions use qualified identities and pass in the final 268-test run.
- PASS — fresh maintained Local command
  `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar --output evidence/sdk/pr9-3-local-2-4716d69d0f`:
  all 13 scenarios and cleanup pass, including the complete mandatory build,
  normal/cutoff shutdown, HTTPS/Caddy baseline, caller/token/bodyless callbacks,
  expiry/trust/ownership and outbound-only operation. Its fresh component build
  has 300 tests, zero failures/errors/skips.
- PASS — isolated TypeSafe command
  `scripts/run_suite.py mock --suite typesafe-ai --output evidence/pr9-3-typesafe-2-4716d69d0f`:
  224 executions across both real hosts, all `PROTOCOL_PASS`, runtime restored and
  no provider calls. Only owned planner names and current trace correlation were
  aligned; accepted native responses were unchanged.
- PASS — 59 focused runner/offer/replay tests after current trace selectors and
  namespace review; 19 identity/package tests including malformed trust rejection.
- At this point Standard, final full pytest and grouped replay were still open;
  subsequent executed results below supersede that interim disposition.

### Subsequent complete replay and transport fixture results

- PASS — current-contract grouped mock at
  `C:/opendev/code/loomspan-suite-pr9-3-4716d69d0f/evidence/pr9-3-complete-4716d69d0f`:
  eight equipment cases (`REPLAY_PASS`) and 224 TypeSafe cases (`PROTOCOL_PASS`),
  both hosts, runtime restored/provider-disabled. Original protected reference
  packs remain byte-identical. Active current contract is separate and strict.
- The preceding grouped run retained all eight equipment passes but stopped its
  native group at case 210 after one malformed HTTP header during a diagnostic
  trace read (`RemoteProtocolError: illegal header line ': '`). The execution,
  exact native response/policy checks and single request had already passed.
  Its explicit isolated case then passed; ten read-only collections of the same
  original artifact passed. No retry, replay, connection workaround, relaxed
  matcher or source change was introduced for this occurrence. The fresh complete
  grouped run above passes. This nonreproduced wire-read occurrence remains an
  accurately recorded limitation, not a passing historical receipt.
- Standard first run passed all required builds, then its positive callback
  exposed missing fixture JVM trust for its private test CA. Standard now mounts
  a public-only PKCS12 CA store via standard JVM trust properties; 47 remote unit
  tests and an actual positive composition pass. No private key/client identity
  or executor TLS option was added.
- Standard second full run passed positive, invocation-server rejection,
  callback-host/expired rejection and both response-loss/no-replay scenarios.
  Its rotation add failed because a direct credential probe still POSTed `{}`
  to the owned bodyless callback. The probe now sends no entity; blocker
  submissions keep `{}`. A regression checks the actual request boundary and
  original token. Focused real rotation passes all shared-signer, independent
  add/removal, restarted signer, old-token-before-expiry and admitted-completion
  observations. Timing, trust and authorization assertions were unchanged.
- PASS — full maintained Hardened command:
  `.venv/Scripts/python.exe scripts/run_sdk.py hardened --sidecar-checkout C:/opendev/code/loomspan-sidecar --output evidence/sdk/pr9-3-hardened-4716d69d0f`.
  All nine selected scenarios and their cleanup stages pass: positive, both
  invocation-peer rejections, both callback-peer rejections, no-bypass isolation,
  both response-loss/no-replay cases and outbound-only. Mandatory Sidecar, SDK,
  driver/reference-app and image builds pass. Receipt `report.json`; 885.764 s.
- PASS — fresh full maintained Standard command:
  `.venv/Scripts/python.exe scripts/run_sdk.py standard --sidecar-checkout C:/opendev/code/loomspan-sidecar --output evidence/sdk/pr9-3-standard-3-4716d69d0f`.
  All eight selected scenarios and cleanup stages pass: positive, invocation
  server, callback host/expiry, both response-loss/no-replay cases, full trusted
  group rotation and outbound-only. All mandatory builds pass; 821.413 s.
  Current root Surefire XML independently totals 62 classes, 300 tests, zero
  failures/errors/skips; preserved receipt
  `.runtime/pr9-3-standard-3-component-counts.json`. No scenario is disabled.
- PASS — current suite full pytest before the last direct-probe regression:
  267 tests. The subsequent final 268-test run includes that regression.
- PASS — final current-source full pytest: 268 tests, zero failures.
- EXPECTED FAIL — new generator acceptance assertion run against the frozen original
  `git show HEAD:scripts/suites/equipment_service/author_workflow.py` module in
  memory: expected failure, zero executor leaves versus nine required. This is
  retrospective red evidence; the release JUnit/Python reds were observed before
  their fixes. Working source was untouched; the same current acceptance test
  passes in the final full 268-test run. Receipt `.runtime/pr9-3-generator-red.log`.
- PASS — final source synchronization: all 102 changed/new source paths match
  the isolated workspace byte-for-byte, all nine protected reference hashes match
  the initial baseline, and `git diff --name-only -- config/skills` is empty.
- PASS — fresh business project stopped through `environment.compose() + ['down']`
  without `--volumes`; stores, volume, credentials, workspace and evidence remain.
  Receipt isolated `.runtime/pr9-3-down.log`.
- Project-owned `.agents/skills/loomspan-docs` was refreshed from matching local
  Framework documentation using its existing project installer/lockfile. This
  removes obsolete REST authoring guidance; it is not a Framework build, code
  change or runtime artifact/source provenance gate.
- The generator now declares `cryptography>=45.0.0`, the version introducing
  [Java truststore serialization](https://cryptography.io/en/45.0.4/hazmat/primitives/asymmetric/serialization/).
  Current tested 50.0.2 already satisfies it; no installed package was changed.
  PASS — `.venv/Scripts/python.exe -m pip check`: no broken requirements.

Namespace review hashes:

- Original preserved worktree manifest (raw CRLF bytes):
  `8e6108586a1af5a228e3d09d7a946a6c604fbf6ff8373165496b6f92527307b8`.
- Original manifest repository LF representation (`sourceManifestLfSha256`):
  `adc448c928c4260d5fcea785be391597fcc14feb26f29254eba3ddb0c083a22b`.
- Current manifest after review metadata portability correction:
  `1a0d72784dc519479e70fa49e852656ae378bac2cf3c5f0ed82d2fcf0574d403`.
- Current full field-level ledger:
  `1019e07f98e662da1937e5b699762fd5f6b508ae1a14554597dcc580ce5ca11d`.

Step 4's composed receipts used the preceding metadata hashes (manifest
`e50d10e00f5d1943e121e3023e79c3a6ff150a7862e21498ce70b2822b729768`,
ledger `eca48fef3ec1f452abb6a8f6b4547e966751c2ebbb57f126d8dcf0d88c4491f4`).
The subsequent review changes only source-manifest digest metadata and its
integrity check; all four active case files and their 316 identity changes,
original pack bytes, strict matching, business values and runtime responses remain
unchanged. The original manifest's LF representation is pinned without rewriting
the preserved CRLF worktree. Current replay tests cover both source-manifest forms.

9.1's deleted generation resources, namespace-less management/catalog APIs and
incompatible management validation were replaced by 9.2's current implementation.
Their complete build and composed proof were executed here, not inherited
from Framework tests. 9.2's remaining beta.8 release-plan guard and snapshot tag
fixture are directly corrected above. Required broad/runtime checks pass
and their final results are recorded above.

During implementation, the first 300-test broad run retained one release failure
because an escaped beta.8 POM expression had not yet been updated. A focused
release/recovery run then exposed Windows line endings in its exact workflow text
assertion. Both were corrected directly (current beta.9 expression and newline
normalization), followed by complete 300-test clean builds with zero skips.
Neither intermediate failure is represented as a passing receipt.

Active-source audit commands (both run; no obsolete dependency):

```powershell
# Sidecar: no matches; diff check passes
rg -n 'rest-skills|rest-routes|rest:\s*true|enable-rest|beta\.8' src scripts docs agent-skills .github pom.xml
git -c core.safecrlf=false diff --check
# Suite: removed-contract search has no matches; diff check passes
rg -n 'rest-skills|rest-routes|rest:\s*true|enable-rest|ROLE_PREFIX' scripts config deploy apps tests docs README.md .agents/skills/loomspan-docs
rg -n 'beta\.8' scripts config deploy apps tests docs README.md .agents/skills/loomspan-docs
git -c core.safecrlf=false diff --check
git diff --name-only -- fixtures/reference fixtures/typesafe-ai/reference config/skills
```

Suite beta.8 matches are only the deliberately rejected JAR-version fixture and
the explicitly historical project-agreement baseline. SDK consumer app's own
`0.1.0-SNAPSHOT` is independent and is not a Framework version. No protected
reference/model diff, secret/private-key artifact, operational store or unrelated
change entered the reviewable source diff. Negative tests and current guidance
may describe absence/rejection of superseded contracts.

## Release boundary and current disposition

Implementation and all required safe local verification are complete. Fresh
independent Step 5 has not run and is not claimed by Step 4. No missing supported
Framework contract was established; the retained nonreproduced diagnostic
wire-read failure above remains an observation, without a workaround or relaxed
assertion. Final results are 300 Sidecar Java tests, 21 script and 11 authoring
client Python tests, 268 suite pytest cases, two embedded Java tests, all 30
selected SDK scenarios (13 Local, eight Standard, nine Hardened), and 232 grouped
deterministic cases (eight equipment, 224 TypeSafe). All selected SDK cleanup
stages passed; the isolated business project is down with its data retained.
Snapshot evidence cannot satisfy the published-artifact or hosted-CI gate.
The developer must publish Framework beta.9 to Maven Central first, then pin
Sidecar to that published version, repeat final build/tests/final commit, and
verify hosted CI before Sidecar `1.0.0-beta.4` / `v1.0.0-beta.4` release.
Never overwrite a release. External configured deployment observations are optional;
no such environment is contacted by this work.
