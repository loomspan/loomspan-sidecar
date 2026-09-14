# Sidecar Packaging and Release Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md`
- Research: `ai/thoughts/research/2026-09-13-sidecar-packaging-release.md`
- Outcome: Package Sidecar as a non-root image, prove its complete authenticated planner/REST and shutdown behavior against the installed beta 4 snapshot, provide runnable deployment examples and accurate configuration guidance, and prepare nonpublishing release automation while preserving the framework-first release boundary.

## Current State

`pom.xml` creates a Java 21 executable Spring Boot JAR and resolves `loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` from the developer's local Maven repository. There is no container build, archive/checksum assembly, or release workflow. `.github/workflows/ci.yml` only runs Maven verification with a future released-dependency override.

`src/main/resources/application.yml` already defaults skill discovery to `/sidecar/skills/**/*.yaml` and `/**/*.yml`, loads `/sidecar/rest-routes.yaml` separately, exposes health probes on management port 9091, and leaves the application port at 8080. Startup registration and `RestRouteLoader` validation are synchronous, so a context cannot become ready with invalid mounted definitions.

`ExecutionCoordinator` owns HTTP admission, queue reservations, retained records, identity handoff, and its prompt owner-context close listener. Review cycle 3 confirmed that the original `begin()`-then-`SkillTemplate.invoke()` transition was not atomic. Framework remediation now supplies the supported public `SkillInvocationHandoff`/`AdmittedSkillInvocation` contract, and Sidecar acquires that admission while holding its existing gate before running the admitted invocation outside the gate.

`GenericRestSkillHandler` is the supported public `RestSkillHandler` implementation and a phase-0 `SmartLifecycle`; its clients remain available while the framework's highest-phase lifecycle waits for admitted roots, then close without a second wait. `RestHandlerLifecycleIntegrationTest` proves this for direct framework calls but not for actual Sidecar workers, HTTP callers, observer delivery, management contexts, asynchronous event multicasting, or all cutoff variants.

`AuthenticatedExecutionApiIntegrationTest` already has the broad local-key, loopback model, YAML planner, production REST callback, JWT forwarding, owner-polling, and diagnostic machinery. It is currently a monolithic test rather than common support that packaged and lifecycle scenarios can reuse.

## Desired End State

The locally built JAR can be placed in a documented Dockerfile image that runs under a fixed non-root UID/GID, declares `/sidecar` as the configuration volume, exposes 8080/9091, and supplies bounded container-aware Java defaults that operators can override. A deterministic Compose quick start mounts one planner, two REST leaves, and one separate route document, and runs a bundled local host that issues/verifies JWTs, emulates the configured model, serves both callbacks, and records restored caller identity and roles. The documented `curl` flow obtains a token, submits work, polls to a terminal result, and requires no external account.

Readiness is unavailable/down until registration and route validation complete, is `UP` for a valid context, and is changed to refusing immediately when the owning application closes. Liveness stays `UP` through long execution. Shutdown returns `503` for new Sidecar admission, discards queued requests and their reservations/references, and prevents any dequeued request from acquiring framework admission after close wins. Work represented by an `AdmittedSkillInvocation` is already framework-owned. Sidecar proves its actual caller, client, and public-observer wiring survives normal accepted-work completion; the framework exclusively owns listener ordering, trace finalization, the single `loomspan.shutdown.timeout` budget, and cutoff before bounded later teardown.

CI verifies Maven and image construction once the released framework is available. A `v<version>` tag workflow rejects a Sidecar or framework SNAPSHOT, requires framework `1.0.0-beta.4`, builds/tests, publishes the versioned image and a JAR archive, and attaches SHA-256 checksums. Local preparation validates this plumbing without tagging, pushing, dispatching, or publishing.

The local snapshot stage records exact commands/results, the Sidecar commit plus uncommitted state, and the installed framework checkout revision in `C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md`. It stops at the intentional publication boundary. Released-dependency, hosted-CI, final-commit, tag/publication, and published-image quick-start gates remain explicitly pending until separately authorized and actually run.

## Scope

### In scope
- Close the coordinator's dequeue/begin-to-facade-entry race within its existing gate and retain direct queue discard/reference cleanup.
- Extract reusable local Sidecar application support and add public-surface integration coverage for the full lifecycle and snapshot behavior matrix.
- Add a Dockerfile image, deterministic Compose quick start, mounted sample `/sidecar`, bundled issuer/model/callback host, and Kubernetes sidecar example.
- Complete README/configuration/security/diagnostics/update/shutdown documentation and assert agreement with all bound `loomspan-sidecar.*` properties.
- Extend ordinary CI with image build/verification and prepare a guarded tag release workflow, archive, and checksums.
- Capture local snapshot evidence in the existing framework readiness record, and later resume the explicitly staged released-dependency gates after authorized framework publication.

### Out of scope
- Publishing/tagging either repository, pushing an image, uploading release assets, or claiming hosted CI success during local preparation.
- Building framework source from Sidecar, adding a snapshot repository, or implementing framework release mechanics here.
- New framework SPIs, imports from `ai.loomspan.internal..` or `ai.loomspan.autoconfigure..`, bean replacement, reflection into framework internals, or listener priority coupling.
- Hot reload, durable records, token minting/refresh/exchange in production Sidecar, another execution queue/store, or another shutdown budget.
- Repeating exhaustive REST binder/JWT boundary cases in container tests.

## Active Project Guardrails

- Choose the smallest complete implementation; new dependencies and abstractions need a concrete current benefit.
- Loomspan's public catalog, validation, `SkillInvocationHandoff`/`AdmittedSkillInvocation`, observer, and `RestSkillHandler` SPI remain the only framework boundary. Framework owns execution authorization, nesting, and accepted-work lifetime; Sidecar owns transport, pre-handoff admission, discard, and retention.
- Keep one admission/queue owner and one retained record. Count/TTL/queued bytes are distinct bounds, not a total-heap promise.
- Preserve JWT identity outside model inputs. Forward the originally verified credential; Sidecar never mints, refreshes, or exchanges it outside the local example host.
- Skills/routes activate only at startup. Sidecar closes dispatch and discards waiting work immediately; the framework owns the single shutdown budget for admitted work.
- Preserve unchanged result text, primary failure, and all selected public events without new Sidecar truncation or sanitization.

## Impact and Risk Analysis

The dispatch fix is concurrency-sensitive: the existing gate must cover queue-reservation release, the close decision, and the public `SkillInvocationHandoff.handoff` call so ownership is atomic, while `AdmittedSkillInvocation.invoke` must run after the gate is released so long-running framework work never blocks Sidecar admission/close. The implementation avoids a new staging queue or lifecycle coupling; tests use a deterministic hook/latch immediately before the guarded handoff rather than timing sleeps.

Shutdown spans Spring close-event delivery, lifecycle phases, application and management contexts, framework observers/trace finalization, Apache HTTP clients, and Sidecar workers. Sidecar tests must observe only supported public APIs plus standard Spring behavior and prove only Sidecar-owned close/handoff, discard/cleanup, wiring, and normal accepted-work completion. Matching framework tests own listener ordering, blocked trace finalization, the single budget, and cutoff. No Sidecar test may discover or replace internal framework beans or assert their names/phases.

The quick-start private key is intentionally a local test credential. It must be visibly scoped to the example and never copied into production defaults. Callback verification must independently validate issuer, audience, signature, expiry, subject, and roles; a mere Authorization-header equality assertion is insufficient.

Image integration depends on Docker and local ports. Keep Maven unit/integration verification deterministic and loopback-only; make the separate image script fail clearly when Docker is unavailable, run it in Docker-capable CI, and clean up named containers/networks on success or failure. Do not require registry credentials for local verification.

The release is staged around an external artifact. Snapshot-stage success cannot satisfy release-resolution, hosted CI, registry publication, or published-image checks. Evidence must identify dirty state accurately and must be refreshed after any framework remediation/reinstall.

## Implementation Approach

Use a checked-in Dockerfile rather than Spring Boot build-image. The repository already owns a repackaged JAR, so a small runtime-only Dockerfile avoids buildpack/plugin configuration and avoids trying to resolve the developer-local framework snapshot inside an image build. Maven builds the JAR first; Docker copies it into a pinned Java 21 runtime base, creates a fixed non-root account, and sets documented `JAVA_TOOL_OPTIONS` that remain environment-overridable.

Keep the dispatch boundary in `ExecutionCoordinator`. Release the queue reservation, check open/record state, restore the captured security context, call the supported public `SkillInvocationHandoff.handoff`, and publish RUNNING under the existing gate. Clear the task input/authentication once framework ownership is established, then invoke and release the returned `AdmittedSkillInvocation` outside the gate. A package-private pre-handoff test hook coordinates the deterministic race but is neither configuration nor a production SPI. Close either wins first and discards the task, or handoff wins first and the framework owns the admitted root; long execution never holds the gate.

Extract the existing loopback integration machinery into `src/test/java/ai/loomspan/sidecar/support/SidecarApplicationFixture.java`. Reuse it for HTTP/JWT/queue/route/diagnostic and lifecycle tests rather than creating a parallel runtime. Keep small binder/security edge cases in their existing focused classes. Use standard Spring event multicasting and public observer output to establish Sidecar's actual caller/coordinator/client wiring and normal accepted-work completion without referencing framework internals or undocumented configuration. Retain the framework's actual-order and blocked-writer tests as the framework-owned evidence for listener ordering, blocked trace finalization, the single budget, and cutoff, never as substitutes for Sidecar-owned behavior.

Put the runnable artifacts under `examples/quickstart`: a Compose file, `sidecar/skills` planner and two REST leaves, the separate `sidecar/rest-routes.yaml`, and a small bundled host using only its runtime's standard libraries plus checked-in example-only keys. The host exposes token, OpenAI-compatible model, two callbacks, and a verification/status endpoint. Put the pod example under `examples/kubernetes`. A portable script under `scripts/` performs nonpublishing image/quick-start checks and is called by CI.

Keep release safeguards executable and close to Maven/workflow ownership: add a Maven release profile/enforcer rules for a release Sidecar version, exact released framework version, and no snapshot dependencies; make the tag workflow derive and compare the tag/project version before build/push; and add a nonpublishing validation script that exercises metadata/archive/checksum creation locally. Do not make the snapshot development build pretend to be a release.

## Phase 1: Close the dispatch race and establish reusable integration support

### Changes
- [x] `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java` — acquire the public framework admission handle under the coordinator's existing gate, release queue reservations exactly once, clear discarded/admitted task references, and invoke the admitted handle outside the gate.
- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java` — add latch-controlled tests proving close can discard a dequeued task before public handoff, handoff can win and remain framework-owned, and framework-first closure fails the dequeued task without invocation; assert queue accounting and task-reference cleanup.
- [x] `src/test/java/ai/loomspan/sidecar/support/SidecarApplicationFixture.java` — extract temporary skills/routes, local JWT tokens, loopback model/callback servers, HTTP helpers, terminal polling, and controlled blocking from `AuthenticatedExecutionApiIntegrationTest` into one reusable fixture.
- [x] `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java` — migrate existing planner/REST/JWT, pre-dispatch, owner-polling, and diagnostics cases to the common fixture without weakening their assertions.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp -Dtest=ExecutionCoordinatorTest,AuthenticatedExecutionApiIntegrationTest test` — the deterministic race and unchanged authenticated execution behavior pass.

### Optional developer checks
- [x] None.

## Phase 2: Prove the real Sidecar shutdown and probe matrix

### Changes
- [x] `src/test/java/ai/loomspan/sidecar/execution/ExecutionShutdownIntegrationTest.java` — use the common real application fixture to cover owning versus management-context close events, prompt readiness refusal, HTTP `503`, direct queued discard/accounting/reference cleanup, asynchronous standard event multicasting, and Sidecar close-listener registration before/after a test listener. Actual application/framework listener ordering remains framework-owned.
- [x] `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java` — drive work through the Sidecar HTTP coordinator instead of direct facade-only calls; prove REST clients, caller workers, public observer events/selected diagnostics, and normal accepted-work completion. Retain a black-box cutoff integration smoke check for bounded client teardown without claiming framework-internal listener, trace-finalization, budget, or cutoff proof.
- [x] `src/test/java/ai/loomspan/sidecar/management/ManagementEndpointIntegrationTest.java` — observe separate-port readiness/liveness for valid startup, long-running execution, and the close gate; assert an invalid mounted skill or route never reaches ready state.
- [x] `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java` — retain the closed API guard over all new production and test code.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp -Dtest=ExecutionShutdownIntegrationTest,RestHandlerLifecycleIntegrationTest,ManagementEndpointIntegrationTest,SupportedLoomspanApiArchitectureTest test` — the Sidecar wiring matrix and public-surface guard pass.
- [x] `C:\opendev\code\loomspan-framework\mvnw.cmd -B -ntp -pl loomspan-spring-boot-starter -Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest test` — matching-source framework-owned evidence confirms the public handoff implementation, both actual application/framework close orders, and lock-safe blocked-trace cutoff; Sidecar tests independently prove its coordinator, HTTP caller, client, public-observer, and normal-completion behavior.

### Optional developer checks
- [x] None.

## Phase 3: Add the image and deterministic quick start

### Changes
- [x] `Dockerfile` — package the prebuilt executable JAR on a Java 21 runtime, create/use a fixed non-root UID/GID, declare `/sidecar`, expose 8080/9091, set documented container-aware JVM defaults, and use an exec-form Java entry point so SIGTERM reaches Spring.
- [x] `.dockerignore` — limit image context to the packaged artifact and required metadata while excluding VCS, plans, tests, and unrelated build output.
- [x] `examples/quickstart/compose.yaml` — run the local host and Sidecar image on an isolated network, mount the sample `/sidecar` read-only, pass issuer/model/callback configuration and secrets through environment variables, and add management readiness/liveness health checks.
- [x] `examples/quickstart/sidecar/skills/planner.yaml`, `examples/quickstart/sidecar/skills/identity-leaf.yaml`, `examples/quickstart/sidecar/skills/detail-leaf.yaml`, and `examples/quickstart/sidecar/rest-routes.yaml` — provide one explicitly configured YAML planner, two REST capabilities, two targets/routes, and environment placeholders with no embedded production secret.
- [x] `examples/quickstart/host/Dockerfile`, `examples/quickstart/host/host.py`, and `examples/quickstart/host/keys/*` — bundle the deterministic local-only issuer/token endpoint, OpenAI-compatible planner model stub, two callback endpoints, and callback verification/status output proving the original token's verified identity/roles.
- [x] `scripts/verify-image.py` — build the Maven JAR and Docker image, inspect the runtime user/JVM settings, run valid and invalid mounted configuration, assert management probe behavior during startup/long work/shutdown, execute the documented token/submit/poll/callback flow, and always remove created containers/networks.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp package` — creates the executable snapshot JAR from the locally installed framework artifact.
- [x] `docker build --tag loomspan-sidecar:sc5-local .` — builds the Dockerfile image without resolving framework source or Maven dependencies inside Docker.
- [x] `python scripts/verify-image.py --image loomspan-sidecar:sc5-local` — proves non-root/JVM/mount behavior, probes, deterministic quick start, JWT callback identity, diagnostics, and packaged shutdown locally.

### Optional developer checks
- [ ] Run the README `curl` commands interactively and inspect the callback host's human-readable verified subject/roles output; the automated script remains the acceptance gate.

## Phase 4: Add deployment and complete configuration guidance

### Changes
- [x] `examples/kubernetes/deployment.yaml` — add an application-plus-Sidecar pod example with ConfigMap-mounted `skills/` and separate `rest-routes.yaml`, Secret-derived environment values, ports, management startup/readiness/liveness probes, and a termination grace period exceeding the documented framework shutdown budget plus bounded cleanup margin.
- [x] `README.md` — replace the SC5-pending description with image build/run and verified quick-start commands; document non-root/JVM defaults, mount/location overrides, all `loomspan-sidecar.*` defaults and constraints, unified targets/routes and placeholders, Boot SSL bundles/HTTPS/mTLS, issuer/key/audience/lifetime/no-exchange guidance, session-backend tokens, diagnostics/business-data and heap-limit caveats, optional Console pairing, probes, one-budget shutdown, restart-only updates, queue discard, cutoff/state loss, and the staged release boundary.
- [x] `src/test/java/ai/loomspan/sidecar/config/ConfigurationReferenceTest.java` — enumerate the own-property bean prefixes/getters and compare them with the README reference so every implemented `loomspan-sidecar.*` key appears exactly once with its actual default/required status and examples keep routes separate from skills.
- [x] `src/test/java/ai/loomspan/sidecar/config/SidecarDefaultsTest.java` — retain executable assertions for image-relevant mount and management defaults.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp -Dtest=ConfigurationReferenceTest,SidecarDefaultsTest,MountedSkillRegistrationIntegrationTest,RestRouteStartupIntegrationTest,RestRouteRestartIntegrationTest test` — proves the reference/defaults and startup-only separate-file examples agree with bound behavior.
- [x] `python scripts/verify-image.py --image loomspan-sidecar:sc5-local --verify-kubernetes` — parses/validates the example and compares its mounts, environment, probes, and ports with the quick-start configuration.

### Optional developer checks
- [ ] Review the Kubernetes termination grace and resource recommendations against the intended production cluster policy; no live cluster is required for this ticket.

## Phase 5: Prepare CI, release artifacts, and nonpublishing safeguards

### Changes
- [x] `pom.xml` — add the minimal release-profile enforcement/assembly configuration needed to require a non-SNAPSHOT Sidecar version, exact framework `1.0.0-beta.4`, and release dependencies without SNAPSHOTs, while leaving snapshot development on the installed artifact and adding no repository/source build.
- [x] `.github/workflows/ci.yml` — after released framework availability, run full Maven verification, build the Dockerfile image, and execute the safe image verification path without publishing.
- [x] `.github/workflows/release.yml` — on `v<version>` tags, verify tag/project agreement and released framework pin, run tests/image checks, authenticate only in the publish job, push immutable version tags to GHCR, assemble the executable JAR archive, generate SHA-256 files, and upload release assets; expose no local-preparation path that publishes.
- [x] `scripts/prepare-release.py` — implement the shared nonpublishing version/dependency checks plus reproducible JAR archive/checksum creation used locally and by the workflow.
- [x] `src/test/java/ai/loomspan/sidecar/release/ReleasePreparationTest.java` — verify the workflow trigger/jobs, snapshot refusal, exact framework pin, nonpublishing CI path, image/JAR/checksum outputs, and that publication steps are gated to an actual tag.

### Automated verification
- [x] `.\mvnw.cmd -B -ntp -Dtest=ReleasePreparationTest test` — release plumbing and guards are structurally executable.
- [x] `python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.4 --loomspan-version 1.0.0-beta.4` — validates release metadata/archive/checksum preparation without credentials or publication.
- [x] `.\mvnw.cmd -B -ntp verify` — the broad Sidecar snapshot suite passes before external handoff.

### Optional developer checks
- [x] None; do not dispatch the workflow, create a tag, authenticate to GHCR, or upload artifacts in local preparation.

## Phase 6: Retain snapshot evidence and stop at the framework publication boundary

### Changes
- [x] `C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md` — append the exact Sidecar/framework revisions, Sidecar uncommitted-state inventory, exact local commands/results, image ID, and links to Sidecar-local tests/evidence; mark remediation/reinstall accurately and leave final framework publication gates pending.
- [x] `ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md` — if needed for handoff, record only material execution decisions and the precise remaining external gates; do not copy test logs or claim full ticket completion.

### Automated verification
- [x] `git -C C:/opendev/code/loomspan-framework status --short` — captures the readiness-record change and any framework state that must be disclosed.
- [x] `git status --short` — captures the tested Sidecar commit plus uncommitted state for the readiness record.
- [x] `.\mvnw.cmd -B -ntp dependency:tree -Dincludes=ai.loomspan:loomspan-spring-boot-starter` — records the installed snapshot dependency used by local SC5 verification.

### Optional developer checks
- [ ] Separately authorize and perform final framework release checks/publication under the framework process. This is the intentional pipeline pause, not a failed Sidecar gate.

## Phase 7: Resume after separately authorized framework publication

### Changes
- [ ] `pom.xml` — change the default `loomspan.version` to published `1.0.0-beta.4`, verify it resolves from Maven Central, and retain release-profile safeguards.
- [ ] `.github/workflows/ci.yml` and `.github/workflows/release.yml` — run hosted CI on the exact final Sidecar source state and retain its URL/result before making the final release commit; change workflow files only if actual published-environment defects require it.
- [ ] `C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md` — record released-dependency resolution, exact final Sidecar commands/revision, hosted CI, final commit, and later authorized Sidecar artifact/published-image results without overwriting prior evidence.

### Automated verification
- [ ] `.\mvnw.cmd -B -ntp -U verify` — resolves and verifies against published framework `1.0.0-beta.4`.
- [ ] `.\mvnw.cmd -B -ntp -U dependency:tree -Dincludes=ai.loomspan:loomspan-spring-boot-starter` — proves the released dependency and absence of the snapshot.
- [ ] `docker build --tag loomspan-sidecar:1.0.0-beta.4 .` and `python scripts/verify-image.py --image loomspan-sidecar:1.0.0-beta.4` — repeat the image/quick-start gate against the released dependency.
- [ ] Hosted `.github/workflows/ci.yml` run on the recorded final revision — required external evidence, never represented as passed by local execution.
- [ ] After separate Sidecar release authorization, the `v1.0.0-beta.4` workflow and published-image quick start — required publication evidence, not run by this implementation pipeline.

### Optional developer checks
- [ ] Inspect Maven Central, GHCR, and GitHub release pages after authorized publication to confirm public artifact discoverability; checksums and quick-start remain executable gates.

## Test Strategy

Start with the deterministic coordinator race because it demonstrates the only identified production-code gap. Then migrate the existing broad application test onto common support before expanding lifecycle scenarios. Use focused unit tests for the gate/accounting transition, real Spring application integration for JWT/planner/REST/observer/lifecycle behavior, and Docker/Compose integration only for packaging, probes, mounts, signals, and the documented quick start. Keep all fixtures local and sanitized.

Run focused tests while implementing each phase, then the full Maven suite, Docker build, portable image verification, release-preparation validation, and architecture guard. Run the matching framework's focused lifecycle suite as framework-owned evidence for internal listener-order, cutoff, and blocked-writer semantics; do not use it in place of Sidecar-owned lifecycle proof. No routine check contacts an external IdP/model/registry or publishes anything.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Local non-root image, JVM defaults, `/sidecar` mount, overrides, no framework source build/repository | `Dockerfile`, `.dockerignore`, `pom.xml`, `README.md` | Docker build plus `scripts/verify-image.py`; dependency tree |
| Startup/readiness validation and separate management probes; long-work liveness | existing eager loaders and `application.yml`; Compose/Kubernetes probes | `ManagementEndpointIntegrationTest`; valid/invalid image and long-work checks |
| Snapshot pre-dispatch, async JWT planner/REST propagation, owner polling, three diagnostics modes | common fixture and existing production handler/coordinator | migrated `AuthenticatedExecutionApiIntegrationTest`; image quick start |
| Prompt readiness refusal, `503`, closed dispatch including dequeue race, direct queue cleanup | atomic `ExecutionCoordinator` handoff | `ExecutionCoordinatorTest`; `ExecutionShutdownIntegrationTest` |
| Sidecar close-or-handoff, discard/reference cleanup, real caller/client/public-observer wiring, normal accepted-work completion, management context, and async multicaster | public handoff plus existing coordinator/handler lifecycle and standard Spring configuration | `ExecutionCoordinatorTest`; `RestHandlerLifecycleIntegrationTest`; `ExecutionShutdownIntegrationTest` |
| Framework listener ordering, blocked trace finalization, single shutdown budget, and cutoff | matching framework implementation at the recorded checkout | framework `FrameworkExecutionLifecycleTest`; `FrameworkShutdownIntegrationTest` |
| Self-contained planner/two-leaf quick start with local model, issuer, token-verifying callback | `examples/quickstart/**`, `README.md` | `scripts/verify-image.py` executes the documented flow |
| Kubernetes example and complete accurate configuration/security/diagnostic/update guidance | `examples/kubernetes/deployment.yaml`, `README.md` | `ConfigurationReferenceTest`; image script Kubernetes checks |
| Public-surface architecture and broad Sidecar integration pass | no new framework boundary | `SupportedLoomspanApiArchitectureTest`; full Maven verify |
| CI image tests and guarded tag workflow for image/JAR/checksums with no local publication | CI/release workflows, Maven release profile, preparation script | `ReleasePreparationTest`; `prepare-release.py --validate-only` |
| Exact local evidence and honest framework-first handoff | existing framework readiness record | captured status/dependency commands and actual result table |
| Published framework pin, final tests/hosted CI/final commit | post-publication `pom.xml` and evidence update | deferred `-U verify`, dependency tree, hosted CI, image verification |
| Authorized Sidecar release artifacts and published-image quick start | tag workflow | deferred real tag workflow/assets/checksums and published-image test |

## Risks and Rollback/Recovery

If the atomic dispatch change causes contention or deadlock, revert only that handoff refactor and its tests; the packaging/documentation work remains independent. The correct recovery is not a second queue or timeout—rework the gate so it protects only the close-or-synchronous-entry transition.

Docker/Compose failures must preserve logs and clean up resources. The image is derived from a prebuilt JAR, so recovery can independently distinguish Maven, Dockerfile, host-fixture, and runtime configuration failures.

If snapshot integration exposes a framework defect, stop treating affected evidence as current, fix it in the framework under its own process, have the developer reinstall the snapshot, record the new framework revision, and rerun every affected Sidecar gate. Do not work around a missing public contract with internals.

At the publication boundary, preserve the snapshot implementation and evidence. Do not change to the release dependency until `1.0.0-beta.4` is actually resolvable. If final hosted verification fails, fix and repeat before the final Sidecar commit/tag; never overwrite either project's release.

## References
- `ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md`
- `ai/thoughts/research/2026-09-13-sidecar-packaging-release.md`
- `ai/thoughts/design-lens.md`
- `ai/thoughts/phases/phase-sc5.md`
- `ai/thoughts/beta4-handoff.md`
- `src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java`
- `src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java`
- `src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java`
- `src/test/java/ai/loomspan/sidecar/rest/RestHandlerLifecycleIntegrationTest.java`
- `C:/opendev/code/loomspan-framework/ai/thoughts/release-readiness/1.0.0-beta.4.md`
