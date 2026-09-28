# PR 7.1 Code Review — Cycle 4

## Scope and Repository State

Fresh Step 5 context, Full 5-Step Pipeline, reviewed on 2026-09-27. Base is the
current `main` HEAD `87ffc54dd6cb0663f80144693dc7772653c14c4d`; the supplied
ticket is uncommitted work against that base, with no staged changes. Inspected
tracked modifications/deletions and untracked production/test files, including
the format-3 replacement, mode/bootstrap components, cipher and startup store.
The scope also includes Console assets, Python authoring client, examples,
operations guidance, schema replacement, fixtures and integration tests.

Read AGENTS.md, automation protocol, design lens, revised ticket, research and
both plans. Prior review documents were not read. Implementation claims and
checked boxes were not used as proof. Traced connected security filters,
storage transactions, generation ownership and matching local framework public
API/documentation. Completed the initial independent review before editing.
The approved Full profile remains appropriate for the persisted and security
contracts. No unrelated developer changes were reverted.

## Findings

### [P1] Admit credential mutations through the PAT route map

- Location: `src/main/java/ai/loomspan/sidecar/management/ManagementBearerFilter.java:81`.
- Scenario: An operator uses the bundled client's `replace-credential` or
  `remove-credential` with a valid Edit or Publish personal token and its exact
  editing grant. Both new endpoints reach the bearer filter's closed route map,
  which previously recognized only PUT/DELETE on `/editing/draft`.
- Impact: Both credential operations return 403 before reaching the editing
  service. Remote clients cannot provision or rotate database credentials,
  violating the shared Console/API/client workflow and acceptance criterion 2.
- Evidence: New real HTTP parameterized regression failed for both Edit and
  Publish presets with expected 200 / actual 403. Existing browser credential
  coverage does not use this filter, and Python client tests use a fake server.
- Fix: Add the exact `/api/management/editing/draft/credentials` path for PUT
  and DELETE with the existing `edit` requirement. Keep all downstream live
  authority, lease, revision and base checks.

No other actionable findings remained after internal re-review.

## Findings Resolved in This Context

Updated `ManagementBearerFilter.required` with the exact credential path;
no wildcard or additional privilege was introduced. Added
`ManagementPersonalTokenHttpIntegrationTest.credentialRoutesUseTheSameTokenGrantAndRevisionChecks`
for Edit and Publish tokens. It proves both credential routes work, Read tokens
are denied, stale revisions conflict, returned content excludes the submitted
value, storage contains ciphertext, and the active configuration is unchanged.
The test uses the existing Sidecar test cipher fixture and temporary SQLite.

Only these two implementation files were edited in this context. Existing
documentation already specifies this intended behavior and needed no change.
The complete ticket diff and connected authorization paths were re-reviewed
after the fix; no remaining actionable issue was identified.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1: File authority, restart-only changes and read-only management | Immutable mode, `startFiles`, separate safe startup store, shared mutation gate | Six mode integration cases; file model and REST calls before/after restart; file-mode HTTP/Chromium mutation denial | Implemented |
| AC2: Complete database providers, Console/API/client and no deployment fallback | Spring binding advisor; explicit candidate/map; write-only credential services; Console builder; client; corrected PAT map | Poisoned startup; effective-candidate and runtime tests; browser credential publication; new Edit/Publish HTTP regression; Python client tests | Implemented after this fix |
| AC3: Draft-only edits and exact proof/ownership | Transactional draft versions; validation candidate; publication/transition gates; live authorization checks | Editing service/HTTP, PAT, remote-authoring and runtime failure tests; credential revision checks | Implemented |
| AC4: Encrypted durable material and actionable key failures | AES-GCM with random nonce and authenticated identifier/version; environment-only key; redacted ordinary views | Cipher tests; draft/snapshot SQL assertions; real child-JVM key environment recovery; secret-free credential HTTP reads | Implemented |
| AC5: Selected database restart authority, history and rollback | Complete retained YAML/credentials; source restore; existing pending/revert bookkeeping; file mode preserves pointer | Runtime integration/recovery, credential environment and portability restarts, store tests and history workflows | Implemented |
| AC6: Optional encrypted portability and explicit replacements | Bounded format-3 inventory; captured encrypted export; authentication before draft load; explicit configuration-only policy | Same-key runtime execution and restart; missing/wrong key and metadata tamper rejection; replacement execution; browser/client options; export authority checks | Implemented |
| AC7: Captured execution isolation, correlation and retirement | Public complete `prepare` overload; staged generation mapping; retained candidate; public retirement callback | Root, delayed-child and physical retry correlation cases; REST generation/lifecycle and shutdown suites; failed preparation tests | Implemented |
| AC8: Supported boundary and operator guidance | Only supported Loomspan APIs; standard Spring binder; unchanged installed snapshot dependency; revised docs/examples/lens/client | ArchUnit in broad and post-fix runs; configuration-reference and Python guidance tests; manual documentation review | Implemented |

Plan conformance is evaluated separately from correctness. Verification is
composed from focused tests and existing integration/lifecycle tests, rather
than claiming every illustrative scenario in the test plan has its own new
test. For example, endpoint/model/key/quota overlap is demonstrated by real
captured work, while full settings persistence and trace YAML are covered by
runtime configuration tests. This is a safe proportional adaptation. The PAT
route omission was a concrete implementation defect, now corrected.

## Active Project Guardrails

- Destructive development replacement is retained: no provider-override adapter,
  format-2 compatibility path or development migration machinery was added.
  Operations guidance explains backup and development reset; no data reset ran.
- Framework validation/execution remains authoritative. Reviewed public
  `SkillReloader` overloads and retirement contract in the matching local checkout;
  Sidecar ArchUnit checks forbid internal/autoconfigure dependencies and Java skills.
- Single complete publication, private drafts, lease ownership, trusted JWT
  execution identity and live management authority remain intact. The fix uses
  the existing cumulative PAT Edit permission and exact service checks.
- Existing physical resource retirement and one framework shutdown budget remain
  covered by Sidecar tests; framework tests were not substituted as evidence.
- Framework dependency stays `1.0.0-beta.6-SNAPSHOT` from the developer's local
  Maven repository. Sidecar remains `1.0.0-beta.1-SNAPSHOT`. No framework rebuild,
  source/artifact revision audit, release switch or hosted CI was performed.

## Open Questions and Assumptions

None requiring a developer decision. The existing REST YAML secret contract is
explicitly outside the provider-credential redesign. Tests use only synthetic
credentials, temporary databases and loopback services.

## Verification Results

IDE diagnostics write class files to the ordinary target directories. Created
an isolated temporary POM with this exact PowerShell command:

```powershell
$reviewPom = (Get-Content -Raw pom.xml).Replace('<build>', '<build><directory>${project.basedir}/target/review4</directory>'); Set-Content -LiteralPath .codex-review4-pom.xml -Value $reviewPom -Encoding utf8
```

Only the build output directory differs; source/dependencies/plugins remain the
repository configuration. Removed it after tests with
`Remove-Item -LiteralPath .codex-review4-pom.xml`.

- PASS — `.\mvnw.cmd -f .codex-review4-pom.xml -B -ntp '-Dtest=ConfigurationModeIntegrationTest,ProviderCredentialCipherTest,EffectiveExecutionConfigurationTest,ConfigurationBundleV3Test,CredentialPortabilityIntegrationTest' test` — 27 tests, no failures/errors/skips. Log: `target/review4-focused.log`.
- PASS before fix — `.\mvnw.cmd -f .codex-review4-pom.xml -B -ntp verify` — 252 tests, zero failures/errors, 3 gated production skips; JAR packaged. This ran before the added regression/fix was compiled. Log: `target/review4-verify.log`.
- FAIL, intended regression RED — `.\mvnw.cmd -f .codex-review4-pom.xml -B -ntp '-Dtest=ManagementPersonalTokenHttpIntegrationTest#credentialRoutesUseTheSameTokenGrantAndRevisionChecks' test` — both presets failed on the actual 403 response; no setup errors. Log: `target/review4-pat-red.log`.
- PASS after fix — `.\mvnw.cmd -f .codex-review4-pom.xml -B -ntp '-Dtest=ManagementPersonalTokenHttpIntegrationTest,ManagementEditingHttpIntegrationTest,RemoteAuthoringWorkflowIntegrationTest,FileModeManagementHttpIntegrationTest,SupportedLoomspanApiArchitectureTest' test` — 31 tests, zero failures/errors/skips, including both new regression cases and Chromium file-mode checks. Log: `target/review4-pat-green.log`.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py'` — 11 tests.
- PASS — `python -m unittest discover -s scripts -p 'test_*.py'` — 7 tests; a deliberately mocked Docker timeout message is expected test output, not an executed deployment.
- PASS — `python -m py_compile scripts/verify-production.py`.
- PASS — `git -c core.safecrlf=false diff --check` after the fix.
- NOT RUN — production Compose/browser verifier, live provider checks, deployment,
  framework build/release and hosted CI. These are excluded from safe routine
  verification. The broad run's three skips are the explicitly gated
  `ProductionComposeBrowserIntegrationTest` cases; local browser tests ran.

The broad suite establishes the reviewed baseline; the post-fix suite covers the
actual changed authorization boundary and its connected management workflows.
No unresolved verification failure remains. A fresh reviewer must independently
certify the updated implementation before pipeline completion.

## Residual Risks and Optional Developer Checks

On a disposable installation, assess file-mode inspection and wrong-key/import
wording and exercise backup restoration with separately provisioned key material.
These remain optional operator/usability observations. Provider-side revocation
can invalidate old captured work as explicitly accepted by the ticket. Hosted
release verification remains deferred until the framework artifact is published.

## Disposition

`fixes-applied`. One actionable finding was fixed and verified; no open finding
remains. This context changed implementation artifacts and cannot certify its
own fix as a fresh clean review. Launch Step 5 in a new context for review 5.
