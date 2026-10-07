# Framework beta.8 Upgrade Code Review — Cycle 1

## Scope and Repository State

Standalone independent review-only execution of `ai/commands/5_code_review.md`.
Read the command, shared automation protocol, design lens, AGENTS.md and complete
ticket. The developer explicitly authorized implementation followed by this
review; research and plan artifacts are intentionally absent.

Reviewed all 17 tracked changed files and the untracked dated ticket against
HEAD, including the ticket's final verification update. There were no staged
changes or unrelated edits. Scope includes dependency/release pins, CI,
documentation, authoring metadata and three Java tests. Production Java code,
Sidecar's beta.2 version and database schemas are unchanged. This context changed
only this review artifact.

Independently compared the local framework's beta.7 and beta.8 tags. The public
Java API change is the fifth `SkillDescriptor` component, nullable String
`outputSchema`. Traced both HTTP catalog routes through `SkillReloader.snapshot()`;
both serialize the public descriptor directly. Reviewed connected route
validation, runtime preparation/publication, credential configuration and the
framework's matching public documentation. The added field is exposed under the
existing authenticated, unfiltered catalog contract. No new secret, identity,
authorization or persistence path is introduced. Additive framework configuration
and authored binding features remain framework-owned.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation edits were made.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Resolve beta.8 and build/integrate Sidecar | `pom.xml`; executable JAR contains `BOOT-INF/lib/loomspan-spring-boot-starter-1.0.0-beta.8.jar` | Current full verify log: 256 tests, zero failures/errors, three opt-in deployment skips | implemented |
| Match public contracts and preserve boundaries | Five-argument constructor fixture; catalog returns framework descriptors; unchanged ArchUnit prohibition of internal/autoconfigure dependencies and Java skills | Fresh architecture report: 2 passed; authenticated API: 14 passed, including actual HTTP field-presence/null assertions; route startup: 2 passed | implemented |
| Match release validation and authoring metadata | POM release enforcer, preparation constant/line check, release workflow and skill metadata all select beta.8 | Fresh release test: 1 passed; independently exercised accepted beta.8 and rejected beta.7 release validation; portable Python checks pass | implemented |
| Update current guidance and reset requirements | README, setup, architecture, Console, upgrade guidance, authoring references and AGENTS.md agree on beta.8 | Independently verified six tagged framework documentation targets exist; metadata check and bundle checks pass | implemented |
| Preserve Sidecar version and avoid publication/data actions | POM and skill retain beta.2; no release/tag/schema edits or data reset | JAR name and portable version check retain beta.2; only validate-only release script was invoked | implemented |
| Developer-selected implementation then independent review | Ticket records explicit developer override; no substitute planning artifacts created | This fresh review independently reconstructs diff and executable evidence | safe deviation |

## Active Project Guardrails

- Simplicity/development policy: direct replacement of version pins and the
  obsolete constructor shape; no adapters, compatibility paths or migration
  machinery. Ticket prominently states the policy and links the design lens.
- Framework boundary: matching beta.8 public API documentation consulted; only
  supported API types are used by application/tests. Fresh ArchUnit tests pass.
- Framework remains execution authority; no internal bean replacement, reflection,
  new SPI, identity handling or ownership mechanism was introduced.
- Existing Sidecar integration covers publication, restoration, nested REST
  execution, JWT propagation, failure handling and shutdown against beta.8. No
  framework test report was substituted for Sidecar evidence.
- Dependency/release policy: no framework rebuild or snapshot repository; exact
  released pin preserved through CI/release tooling. No publishing, tagging or
  release overwrite occurred. Sidecar's existing version is preserved as directed.
- Data reset: no schema change; upgrade documentation explicitly says this
  dependency upgrade requires no Sidecar development-data reset.

## Open Questions and Assumptions

None affecting this upgrade's correctness. The pre-existing AGENTS.md statement
that development is beta.2-SNAPSHOT differs from the existing beta.2 POM; this
ticket explicitly preserves the existing version.

## Verification Results

- PASS — `git diff --check` — no whitespace errors.
- PASS — `python scripts/sidecar_version.py check` — beta.2 / beta.8 metadata matches.
- PASS — `python -m unittest discover -s scripts -p test_sidecar_version.py -v` — 7 tests.
- PASS — `python -m unittest discover -s scripts -p test_agent_skills.py -v` — 6 tests.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v` — 11 tests.
- PASS — `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.8 --tag v1.0.0-beta.2` — nonpublishing validation succeeds.
- PASS — `./mvnw.cmd -B -ntp verify` — executed by the implementing context; independently inspected its completed `target/beta8-verify.log`, current affected Surefire XML reports and packaged JAR. BUILD SUCCESS; 256 tests, zero failures/errors, three deployment skips. Stale Oct 1 reports were excluded until replaced by this run.
- PASS — `./mvnw.cmd -B -ntp -Prelease validate` — independently run with Java 21 from `C:/hamdev/jbrsdk21`; all three release enforcer rules pass.
- PASS — `./mvnw.cmd -B -ntp -Prelease '-Dloomspan.version=1.0.0-beta.7' validate` — negative check: expected exit 1 with `Release requires Loomspan 1.0.0-beta.8 exactly.` The initial unquoted PowerShell invocation split the property argument and was corrected before checking enforcement.
- PASS — inline `python -` checks — six current beta.8 framework documentation targets validated with `git cat-file -e` against the exact local tag; executable JAR inspected with `zipfile` to establish the bundled beta.8 dependency; fresh affected Surefire report counts independently read.
- NOT RUN — `python scripts/verify-image.py --image loomspan-sidecar:ci` and `python scripts/verify-production.py --image loomspan-sidecar:ci` — no local image build/deployment verification. Three production Compose browser tests remain opt-in and were skipped by the ordinary suite.

## Residual Risks and Optional Developer Checks

Local executable build and integration evidence establish the bounded upgrade;
container packaging/configuration is unchanged. Hosted CI and container deployment
checks remain required before a Sidecar release, particularly for the resulting
image and HTTPS Compose deployment. Live provider and Console connections were
not exercised; no claim of their runtime verification or artifact publication is
made. Consumers enforcing an exact HTTP descriptor shape must accept the documented
additional field. No additional work is required for this review's disposition.

## Disposition

`clean`

## Step Report: 5_code_review

STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-07-upgrade-framework-beta-8-review-1.md
SUMMARY: Independently reviewed the complete upgrade and found no actionable defects. Build and integration evidence, release enforcement and portable guidance checks establish beta.8 compatibility within scope.
DECISIONS:
  - Honor the developer-authorized standalone route; skipped planning artifacts are not prerequisites.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `git diff --check`
  - PASS — portable Python metadata, bundle, client and release validation checks listed above
  - PASS — `./mvnw.cmd -B -ntp verify`: independently inspected completed root-run evidence
  - PASS — `./mvnw.cmd -B -ntp -Prelease validate`
  - PASS — `./mvnw.cmd -B -ntp -Prelease '-Dloomspan.version=1.0.0-beta.7' validate`: expected rejection
OPTIONAL_DEVELOPER_CHECKS:
  - Run hosted CI/container deployment verification before releasing Sidecar.
REVIEW_RESULT: clean
NEXT: Return the reviewed upgrade to the developer.
