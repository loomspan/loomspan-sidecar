---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: b747fd9e9f57e5ae1cf8972d79801df8c9c6fa38
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-1-sdk-build-and-suite-sdk-lane-foundation.md
tags: [java-sdk, maven, external-suite, readiness, build-boundary]
---

# SDK build and external-suite SDK lane foundation research

## Research Question

What currently exists in the Sidecar build and external suite, which public configuration and test facilities support provider-free startup/readiness/shutdown, and what evidence and operational boundaries govern the new independent SDK build and lane?

This is pipeline-mode Step 1 of the approved **Full 5-Step Pipeline**. The developer approved the profile with “proceed”; no further profile approval is pending. Research describes current behavior; implementation choices belong to planning.

## Summary

There is currently no `sdks/java` reactor or SDK lane. Sidecar is a single Java 21 Maven application with Spring Boot 4.1.0 and Framework beta.8. It already packages an executable Boot JAR, exposes readiness on a separate management port, supports file-backed configuration, and has loopback deterministic model fixtures in its own tests.

The external suite has an established five-service Docker Compose runtime, named business suites, a Python runner, and retained deterministic reference packs. Its embedded app and exported Sidecar beta.3 both use Framework beta.9-SNAPSHOT. Their build identity checks and scenario runner assume that existing runtime, so their evidence does not establish behavior of the current Sidecar checkout against beta.8.

Maven, Java 21 through `JAVA_HOME`, Python 3.13, the suite virtual environment, Docker, Ruff, and the installed Framework beta.8 JAR are present. Research did not build, run tests, start services, contact model providers, or change runtime data.

## Repository State

Observed on 2026-10-18 America/Los_Angeles:

| Repository | Branch / HEAD | Initial working tree |
| --- | --- | --- |
| `C:/opendev/code/loomspan-sidecar` | `main`, `b747fd9e9f57e5ae1cf8972d79801df8c9c6fa38` | Clean before this research document |
| `C:/opendev/code/loomspan-sidecar-test-suite` | `main`, `14233ef4eca50bd7f0051f954a65812324142632` | Existing unstaged `docs/project-agreement.md`, 72 inserted lines |

The suite agreement's existing change adds SDK integration direction beginning at `docs/project-agreement.md:211`. It is developer work predating this pipeline and must be preserved and identified separately from implementation changes. The ticket intentionally authorizes changes in both repositories. All pipeline artifacts stay in Sidecar's `ai/thoughts`; the suite agreement repeats this at lines 234–236.

The local Framework checkout is ahead of beta.8 (`git describe`: `v1.0.0-beta.8-12-g52f11dd`). The matching tag resolves to `052e8b47edaf95c0d2013d9e6351284a98207d6b`. Framework documentation consulted during this research came from that tag via `git show`, not the current branch or stale installed documentation skill.

### Research checklist

- [x] Repository instructions, design lens, ticket, canonical build/acceptance references.
- [x] Current Sidecar Maven build, packaging, supported dependency boundary.
- [x] Startup configuration, readiness, local JWT verification, state, lifecycle.
- [x] External-suite runtime, lane selection, fixtures, evidence and regression tools.
- [x] Existing tests, installed prerequisites and environment-dependent boundaries.
- [x] Historical intent and unresolved planning questions.

## Current Behavior and Data Flow

### Sidecar build

`pom.xml:7–17` declares `ai.loomspan:loomspan-sidecar:1.0.0-beta.4-SNAPSHOT`, Java/release 21, Boot 4.1.0, and Framework `1.0.0-beta.8`. `pom.xml:39–44` introduces the Framework starter dependency. The root has no `<modules>` and is not an aggregator. It imports Boot dependency management and directly declares application dependencies, including Web MVC, Security, OAuth2, Actuator, JDBC/SQLite, mail and HTTP client libraries.

`pom.xml:116–151` configures resources, compiler 3.14.1, Surefire 3.5.4 and Boot's `repackage` execution. Normal package output is `target/loomspan-sidecar-1.0.0-beta.4-SNAPSHOT.jar`; `Dockerfile:12` copies the packaged JAR into an image. `pom.xml:154–184` has release-only Enforcer checks for release versions/dependencies and exact Framework beta.8, but no SDK dependency ban exists.

The existing architecture test at `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:16` imports application and test classes; its first test forbids `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`, and its second test forbids Sidecar `@SkillMethod` declarations. That class boundary is distinct from the ticket's future Maven dependency boundary for SDK artifacts.

### Startup, state and configuration

The executable entry point is `src/main/java/ai/loomspan/sidecar/LoomspanSidecarApplication.java:24`. It starts Spring normally unless the first argument is `admin`, which takes a separate command path.

`src/main/java/ai/loomspan/sidecar/configuration/ConfigurationModeConfiguration.java:20` selects `database` by default, accepts exactly `file` or `database`, and makes Framework startup skills empty in database mode. Its binding advisor deliberately ignores environment connection/model/session settings in database mode. Therefore supplying only model settings does not imply those settings become the active database-managed configuration.

`RuntimeConfigurationService.java:97` activates the selected database snapshot through supported `SkillReloader` APIs. `RuntimeConfigurationService.java:176` handles file mode using the already loaded Framework catalog, configured REST routes, and file execution configuration; it records safe startup inspection and opens admission at line 208. File mode still writes startup records to SQLite. `src/main/resources/application.yml:7` and `SidecarStorageProperties.java:8` default storage to `/sidecar/data/sidecar.db`; tests explicitly choose temporary paths.

`src/test/java/ai/loomspan/sidecar/LoomspanSidecarApplicationTest.java:19` shows an empty fresh database configuration starting and closing without a model provider. It uses a temporary database, disabled observability, issuer/audience, and a classpath RSA public key.

`src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java:90` exercises file-mode model configuration. Lines 97–102 provide an `openai` connection whose base URL ends in `/v1` on a loopback fixture port, a synthetic credential, model alias `fixture-model`, an additional YAML settings file, and a mounted test skill. The test runs the model, changes file settings, verifies no in-process credential change, then restarts and observes the new setting.

### JWT and management readiness

`src/main/java/ai/loomspan/sidecar/security/SidecarJwtProperties.java:34` requires issuer URI, audience and roles configuration and disallows simultaneous remote JWKS and public-key sources. `JwtSecurityConfiguration.java:38` chooses local RSA public-key decoding first, configured JWKS second, and issuer discovery otherwise. Local-key configuration avoids issuer discovery network access; this is current pre-SDK trust behavior, which later tickets replace.

`src/main/resources/application.yml:64–73` sets management port 9091, enables health probes and exposes health. `JwtSecurityConfiguration.java:105` permits `/actuator/health` and its children; execution APIs remain authenticated. `Dockerfile:20–22` uses `/actuator/health/readiness` as its readiness check. Main and management ports are separate configuration values.

No startup smoke driver for the packaged current revision exists. Existing in-process Spring tests establish fixture/configuration patterns, not the new external-lane acceptance evidence.

### Deterministic model fixture and shutdown

`src/test/java/ai/loomspan/sidecar/support/SidecarApplicationFixture.java:88` starts a JDK HTTP server on loopback and an ephemeral port; `/v1/chat/completions` dequeues deterministic completions. Its helper at line 146 emits an OpenAI completion envelope. This test fixture captures request bodies and authorization for its assertions, so it is evidence of protocol shape, not a safe external diagnostic logging policy.

Matching Framework beta.8 documentation, `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md:17`, defines skill alias → model alias → connection → built-in driver. Its connection rules explain that OpenAI appends `/chat/completions` and conventional versioned endpoints have a `/v1` base URL. The same document states connection retry defaults; no Framework-source rebuild is required to use the installed beta.8 artifact.

`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:231` synchronously closes admission, publishes `REFUSING_TRAFFIC` and discards queued tasks on its own context's close event. Destruction at line 252 shuts down its executors. `GenerationRestResources.java:115` closes owned resources after lifecycle teardown. Existing `ExecutionShutdownIntegrationTest.java:23` checks synchronous event behavior and owner-context readiness transition, including an asynchronous multicaster. These tests do not prove a packaged child process received a graceful termination signal on Windows.

The current executable has no dedicated shutdown API. The Dockerfile uses Java directly as entry point, allowing container lifecycle signals to reach Java. A native child-process shutdown mechanism and a container signal mechanism have different platform behavior; the new lane has not selected either.

### External-suite retained lanes

All paths in this subsection are relative to `C:/opendev/code/loomspan-sidecar-test-suite`.

`README.md:11` describes named `equipment-service` and `typesafe-ai` suites sharing Java and Python hosts. `config/runtime-source.json:2–7` pins Framework beta.9-SNAPSHOT / commit `3265354b6c4975c8ae82ac9f111eff0ffcf77b9d` and exported Sidecar beta.3 / commit `3eff6befd95f73b7d4091ed0da32498e0f64c488`. Both existing hosts use the beta.9 snapshot; comments in `compose.runtime.yaml:1` and older prose in `docs/foundation.md` still mention beta.8 and are historical disagreement, not authority over the pin/build override.

`scripts/environment.py:16` selects the existing or isolated Compose project. `compose()` at line 36 adds the base, runtime and offline overlays, optional supplied-artifact and isolated overlays, and an explicit run overlay. It invokes shared suite configuration preparation. `scripts/start.py:12` runs that Compose stack, polls all five services, and verifies providers are disabled. `scripts/readiness.py:4` expects Java, Python, fixtures, Keycloak and Sidecar and validates the issuer. Its fixed service model is not an existing standalone SDK-lane abstraction.

`docs/runtime.md:31–56` documents prerequisite Framework installation, exported beta.3 source and explicit `-Dloomspan.version=1.0.0-beta.9-SNAPSHOT` for that source. `scripts/record_build.py:38` requires clean matching Framework source, matching local installed bytes and equal Framework JAR bytes embedded in both hosts, and writes `.runtime/build-baseline.json`. These existing lane requirements do not imply a new Framework-source verification/rebuild requirement for Sidecar SDK work; Sidecar AGENTS expressly relies on installed dependencies and forbids normal Sidecar builds from rebuilding Framework.

`scripts/run_suite.py:367` accepts `mock`, `live`, `evaluate`, or `capture`, suite/path/scenario selection and evidence destination. It has no `local`/`standard`/`hardened` SDK selection. `run_lock()` at line 61 locks `.runtime/run-suite.lock` because retained scenarios temporarily alter runtime overlays. `scripts/smoke.py:25` verifies fixture provider keys are disabled; its smoke runs real authenticated quote executions through both integrations, creates isolated quote rows, and checks prior business rows remain unchanged.

`compose.offline.yaml:4–8` clears provider keys/live forwarding. The retained fixture service includes provider forwarding support for separately authorized paid activity; ordinary startup applies the disabling overlay. The ticket's separate SDK lane has no provider credential or forwarding requirement.

### Evidence

The retained runner's `Bundle` (`scripts/run_suite.py:42`) rejects known secret values in artifact bytes and replaces known secrets in exception strings. `command()` at line 76 suppresses command stdout and captures stderr, with a comment prohibiting Compose/environment credential echo. `write_report()` at line 323 writes the existing report bundle. These are current lane facilities; no SDK build/revision/recipe report exists.

`evidence/README.md:6` documents `evidence/latest/<suite>` reports and a grouped summary; explicit named destinations must be new. `.gitignore` ignores all evidence except the README, plus `.runtime`, `.build`, targets and Python caches. The accepted reference packs are outside replaceable evidence: `fixtures/reference` and `fixtures/typesafe-ai/reference`. Existing tests verify reference integrity and altered-input rejection.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Independent SDK build | Absent; root remains a single application POM with beta.8 dependency |
| Core/starter packaging | Planned coordinates and Boot 4.1.0 baseline in canonical API reference; no implementation/public placeholder APIs |
| Dependency isolation | Existing Java ArchUnit boundary covers Sidecar code, not SDK Maven direct/transitive dependency graphs |
| SDK lane selection | Absent; retained runner selects named business suites and evaluation modes |
| Sidecar runtime | Executable current-revision JAR, public file/database modes, local public-key JWT option, management readiness |
| Fixture protocol | Loopback OpenAI completion fixture exists in Sidecar tests; retained suite has larger business/provider fixtures |
| Shutdown | Context lifecycle covered in Sidecar tests; no current standalone SDK process driver |
| SDK evidence | Absent; retained build record/evidence targets beta.9 + beta.3 exported source |
| Preserved regressions | Python tests, five-service smoke, equipment reference replay, TypeSafe grouped replay |

## Existing Tests and Fixtures

Sidecar's focused examples include `LoomspanSidecarApplicationTest`, `ConfigurationModeIntegrationTest`, `JwtStartupIntegrationTest`, `SupportedLoomspanApiArchitectureTest`, and `ExecutionShutdownIntegrationTest`. Broader REST lifecycle tests cover admitted-work/resource behavior through supported public APIs. No SDK dependency-injection/negative-build test exists.

Suite tests under `tests/` use pytest and local fixtures. `tests/test_runner.py:27` checks dry-run selection avoids runtime access; tests also cover runtime restoration on failures/interrupts, lock exclusion, secret rejection, and report replacement. `tests/test_replay.py:59` checks frozen pack integrity and identity rebinding; TypeSafe has its own protocol, live-boundary and application-policy tests. No SDK runner unit tests exist.

Documented executable checks for later planning, **not run during research**:

| Check | Existing command / prerequisites |
| --- | --- |
| Root tests/package | `mvn -B -ntp clean verify`; Maven and installed/resolvable beta.8; browser/deployment tests may have their own environment gates |
| Suite focused/local tests | `.venv/Scripts/python.exe -m pytest tests -q` from suite root; local Python dependencies |
| Retained runtime startup | `.venv/Scripts/python.exe scripts/start.py` using provided Compose helpers; five service images/artifacts/secrets required |
| Existing smoke | `.venv/Scripts/python.exe scripts/smoke.py`; running provider-disabled retained stack; adds fresh quote rows |
| Both retained suites | `.venv/Scripts/python.exe scripts/run_suite.py mock --suite all`; running stack and maintained fixtures; creates fresh synthetic business rows |

Existing-lane runtime verification cannot be claimed from the new lane or Python unit tests. Conversely, existing-lane tests cannot establish the new SDK integration. No paid/provider live mode is authorized.

## Dependencies and Operational Constraints

Observed read-only prerequisite checks:

- Maven 3.9.6 reports Java `21.0.2` (JetBrains) at `c:/hamdev/jbrsdk21`; `JAVA_HOME` points there and `bin/java.exe` exists. Plain `java` is absent from PATH in this shell. Maven is available as `c:/hamdev/maven/bin/mvn.cmd`.
- Python is 3.13; the retained `.venv/Scripts/python.exe` exists and reports pytest 8.4.1.
- Ruff 0.14.7 is available; no repository Ruff/formatter configuration was found in the two repositories' searched configuration paths. The ticket requires formatting new harness code, not a broad reformat of existing tools.
- Docker is available; `docker ps --format '{{.Names}}'` reported only `azurite-qa`, not the suite stack. Starting retained lanes will therefore require their documented startup rather than assuming services are live.
- The local Maven repository contains `ai/loomspan/loomspan-spring-boot-starter/1.0.0-beta.8/loomspan-spring-boot-starter-1.0.0-beta.8.jar`. Presence is not a completed build result; source/artifact revision auditing is not required by Sidecar policy.
- `requirements.lock` already includes pytest, httpx, PyYAML and cryptography. No public SDK runtime/client dependencies have yet been selected or added.

Temporary SDK test state must be separate from retained credentials/SQLite data. No development-data reset is identified by this foundation ticket, and none was performed. Secrets, private keys and business payloads must not appear in default logs, reports, build-error excerpts or exception diagnostics. Shell/child-process environment inheritance is an operational boundary even when a configured model URL is loopback.

## Historical Context

The reconciled roadmap (`ai/thoughts/phases/2026-10-11-java-spring-sdk-roadmap.md`, current content reconciled 2026-10-18) states no SDK implementation remains after reset. Recent history has roadmap reconciliation followed by current ticket creation; discarded infrastructure is not a source of runtime prerequisites.

Canonical Phase 1 delivery group 1 establishes this foundation early and assigns subsequent feature tests to their owning tickets. There is a wording discrepancy: the Phase 1 delivery list describes the preserved “beta.9 embedded/Python lane,” while the ticket more precisely describes its Python host as beta.3-based Sidecar. Current suite source confirms both statements: exported Sidecar beta.3 is rebuilt against beta.9. No requirement authorizes changing those pins.

The API reference assigns Java 21/Boot 4.1.0, independent core/starter Maven artifacts and no Framework/Sidecar dependency. The acceptance matrix assigns small external composed fixtures, deterministic models, temporary state and safe evidence; detailed feature rule variants stay in component tests. Its eventual recipes and public SDK APIs remain planned, not existing functionality or scope for placeholder implementation.

## Open Questions

These are planning decisions supported by current evidence, not developer blockers for research:

1. Process or container execution for the isolated foundation smoke, including an actual graceful shutdown mechanism on this Windows host and objective failure evidence when only forced cleanup succeeds.
2. Exact SDK parent/version/dependency policy representation and executable direct/transitive negative-build cases for both modules without changing root build scope.
3. Minimal deterministic fixture configuration for startup smoke, and evidence that no external provider traffic or inherited provider configuration is used; this ticket does not require executing SDK client operations.
4. Small runner/module layout, per-run evidence destination/identity, and how empty recipe selections and build/startup/shutdown failures retain safe reports.
5. Concrete regression execution against the currently stopped five-service retained stack, preserving credentials, data, suite content and maintained fixture bytes.

## Step Report: 1_research_codebase

STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-18-pr-8-1-1-sdk-build-and-suite-sdk-lane-foundation.md
SUMMARY: Current build, startup/lifecycle, external-suite lanes, tests, fixtures and installed prerequisites are documented with source anchors. The existing suite agreement change is attributed and protected. No implementation, test run, service startup, provider call or data reset occurred.
DECISIONS:
  - Used Framework beta.8 tagged documentation because the local Framework branch has advanced.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Continue to implementation planning and test planning in one fresh planning context.
