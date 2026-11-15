# Preserve Sidecar integration coverage with Responses fixtures

## Development constraints

**Replace obsolete development contracts directly. No compatibility shims,
legacy adapters, parallel APIs, or migration machinery for superseded behavior.**
Apply the [design lens](../design-lens.md), keep the solution simple and technical
debt minimal. No development-data reset is needed; no deployed data may be deleted.

## Outcome

Exercise Sidecar integration through the installed beta.10-SNAPSHOT Framework
using local Responses providers without provider charges or weaker assertions.

## Requirements

- Replace Java and quickstart mock provider wire formats with Responses.
- Preserve credential capture, publication generation, JWT forwarding, diagnostics,
  exact output, retry/blocking, recoverable feedback, and terminal-error coverage.
- Require the actual tool-result item and matching call ID before model success.
- Update the obsolete mounted-skill driver fixture to the supported public contract.
- Isolate release-test positive fixtures from the development POM; preserve
  snapshot and invalid release rejection without modifying release production code.
- Keep application code and Framework sources unchanged. Use supported public
  Framework APIs only. Preserve prior dependency/docs changes in the worktree.

## Acceptance criteria

- [x] All affected Java integration cases pass without removing or relaxing assertions.
- [x] Local quickstart Responses output and obsolete route rejection are verified.
- [x] Full local verification and independent review complete; limitations recorded.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** high
- **Rationale:** Bounded fixture updates for established behavior; no production
  contract changes. Existing regression coverage plus independent review applies.
- **Reassessment triggers:** Production behavior changes or unexplained failures.

## Execution notes

- User explicitly requests updating mocks with discernment against weakening tests.
- Prior snapshot assessment found 9 failures and 3 errors. Those red results are
  available in `snapshot-verify.log`; this task replaces obsolete fixtures directly.
- Release gate stays on published beta.9; no publication or live-provider test.

### Coverage decisions

- Shared provider retains response queues, credential/request capture, BLOCK
  synchronization and RETRY behavior. Only endpoint and JSON envelope change.
- HTTP executor still executes the real annotated executor through the public
  Framework API: exact recoverable feedback, original token propagation, two
  model calls for recovery, one call for each terminal status, and diagnostic
  classification remain asserted. Added endpoint, offered-tool and call-ID checks
  ensure the fake provider cannot return success without the real tool result.
- Configuration/publication/captured-generation assertions remain. Model-only
  retained output additionally checks exact whitespace, newlines, quotes, Unicode,
  tabs and backslashes before and after publication.
- Mounted registration retains catalog names/kinds and invalid startup tests;
  use supported OpenAI configuration with existing encrypted-credential fixtures.
- Release checks run the actual unmodified script in a temporary repository with
  an explicit published-version POM. Existing negative cases remain; extra cases
  reject a snapshot argument and snapshot POM with misleading release arguments.
- Quickstart preserves the four original planner payloads and callback verification;
  only the local provider envelope and route change. No legacy route remains.

## Verification

- Initial focused run: 34 of 36 passed; mounted registration needed the existing
  test credential cipher after introducing an encrypted provider credential.
  Added `SidecarApplicationFixture.Credentials` to that test context.
- PASS: `.\mvnw.cmd -B -ntp -o
  '-Dtest=MountedSkillRegistrationIntegrationTest,ReleasePreparationTest,ExecutionConfigurationCorrelationIntegrationTest'
  test`: 10 tests, zero failures/errors, including added exact-output assertions.
- PASS: `.\mvnw.cmd -B -ntp -o verify`: 301 tests, 300 passed, zero
  failures/errors, one skipped; executable jar packaged. `responses-verify.log`.
- PASS: `python -m unittest discover -s scripts -p 'test_*.py' -v`: 21 tests.
- PASS: `python scripts/sidecar_version.py check` and `git diff --check`.
- PASS: `docker build --tag loomspan-sidecar:responses-local .` and
  `python scripts/verify-deployment.py --image loomspan-sidecar:responses-local`:
  local deployment smoke passed; cleanup passed. No real model request.
- Independent quickstart loopback probe: four Responses replies preserve the
  original planner payloads; old Chat Completions route returns 404.
- The optional Python issuance interop case remains skipped because
  `LOOMSPAN_PYTHON_ISSUANCE_FIXTURES` is absent. No live model/provider,
  hosted CI, or release publication was run. No paid provider traffic is needed.
- Independent review: clean, no findings or implementation edits. Reviewer ran
  HttpExecutorIntegrationTest, ExecutionConfigurationCorrelationIntegrationTest,
  ReleasePreparationTest and MountedSkillRegistrationIntegrationTest: 12 passed.
  Review: `../reviews/2026-11-15-responses-test-fixtures-review-1.md`.
