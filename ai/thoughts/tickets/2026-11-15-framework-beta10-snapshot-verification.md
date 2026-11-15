# Verify Sidecar against Framework beta.10 snapshot

## Development constraints

**Replace superseded development contracts directly; no compatibility shims,
legacy adapters, parallel legacy APIs, or migration machinery solely to preserve
obsolete behavior.** Apply the [design lens](../design-lens.md). Choose the simplest
solution and minimize technical debt. Document any development-data reset; this
does not authorize deleting deployed data.

## Outcome

Use the already installed Framework `1.0.0-beta.10-SNAPSHOT` and establish whether
Sidecar still works after the major Framework change.

## Requirements

- Change the development dependency and matching current-checkout documentation
  and authoring metadata. Keep Sidecar at `1.0.0-beta.5-SNAPSHOT`.
- Use the local Maven artifact and matching local Framework documentation.
  Do not rebuild Framework or add snapshot repositories.
- Run Sidecar's local verification and report actual compatibility failures.
- Preserve public API boundaries. Do not change behavior to hide regressions.
- Leave release gates pinned to the published version until release preparation;
  no publication, hosted CI, live provider calls, or deployment is requested.

## Acceptance criteria

- [x] Development POM and current-checkout guidance identify beta.10-SNAPSHOT.
- [x] Sidecar local build and regression results are recorded, including failures
  and checks unavailable in this environment.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The dependency switch is settled and bounded; broad existing
  integration verification and independent review address compatibility risk.
- **Reassessment triggers:** Required application contract changes or material
  unresolved compatibility failures need separate design and planning.

## Execution notes

- User explicitly authorized the installed snapshot switch and compatibility
  verification. Initial working tree was clean. No data reset is required.
- Release-only beta.9 pins intentionally remain until Framework publication.
- Verification is local Sidecar evidence, not inferred from Framework tests.

## Verification results

The requested switch and assessment are complete; compatibility verification
failed. No application or fixture behavior was changed to suppress failures.

- FAIL: `.\mvnw.cmd -B -ntp -o clean verify`: 301 tests, 288 passed,
  9 failures, 3 errors, 1 skipped. Production and test compilation passed.
  Log: `snapshot-verify.log`; original reports: `target/beta10-surefire-reports/`.
- PASS: `.\mvnw.cmd -B -ntp -o '-Dloomspan.version=1.0.0-beta.9'
  '-Dtest=ConfigurationModeIntegrationTest,CredentialPortabilityIntegrationTest,HttpExecutorIntegrationTest,MountedSkillRegistrationIntegrationTest'
  test`: all 16 tests pass. This command-line comparison did not change the POM.
  Log: `snapshot-beta9-comparison.log`.
- PASS: `.\mvnw.cmd -B -ntp -o -DskipTests package`: restored the beta.10
  build and produced the executable jar. This is packaging evidence only, not
  passing regression evidence. Log: `snapshot-package.log`.
- PASS: `python scripts/sidecar_version.py check`.
- PASS: `python -m unittest discover -s scripts -p 'test_*.py' -v`: 21 tests.
- PASS: `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client
  -p 'test_*.py' -v`: 11 tests.
- PASS: `git diff --check`.
- SKIPPED by suite: `PythonIssuanceInteropTest` requires the absent
  `LOOMSPAN_PYTHON_ISSUANCE_FIXTURES` environment variable.
- NOT RUN: container deployment smoke, hosted CI, release verification, and
  real-provider execution. Local compatibility already fails; no release or
  configured external environment verification is claimed.

### Failure classification and next implementation work

1. Eight failures across ConfigurationModeIntegrationTest,
   CredentialPortabilityIntegrationTest, AuthenticatedExecutionApiIntegrationTest,
   and ExecutionConfigurationCorrelationIntegrationTest use the shared provider
   fixture serving `/v1/chat/completions`. beta.10 calls `/v1/responses` and receives
   HTTP 404. HttpExecutorIntegrationTest adds one error because its separate
   fixture returns `choices` instead of Responses `output` (`output is not set`).
   Update both fixtures directly to Responses and preserve their assertions.
2. MountedSkillRegistrationIntegrationTest has two startup errors; its fixture
   uses removed `driver: ollama`. Matching Framework documentation requires
   `openai` with a Responses-compatible service and credentials.
3. ReleasePreparationTest has one failure: `scripts/prepare-release.py` rejects
   the current POM with `pom.xml does not use the beta 9 framework line`.
   Preserve release refusal for snapshots; separate release-test fixture setup
   from the development dependency in follow-up work.
4. The shipped quickstart host also only serves Chat Completions. Independent
   loopback probing confirms POST `/v1/responses` returns 404 and POST
   `/v1/chat/completions` returns 200. Update the quickstart provider fixture
   directly alongside the Java fixtures, then rerun integration/deployment checks.

Framework source documentation used:
`C:/opendev/code/loomspan-framework/agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`.
These results establish fixture/configuration incompatibilities, not successful
live-provider behavior or a demonstrated defect in Sidecar's production adapter.
