# Scaffold a buildable Loomspan Sidecar against the beta 4 framework

## Outcome

Establish a Java 21 / Spring Boot 4 servlet application that hosts the Loomspan
starter, loads mounted model-backed YAML skills, and builds reproducibly against
the implemented framework snapshot. This is the foundation for a language-neutral
asynchronous skill API; execution routes and outbound REST handling arrive later.

## Requirements

- Create the Maven application and wrapper using Boot 4.1.0 and its aligned
  Spring Framework 7.0.8 / Security 7.1.0 platform. Declare the web, security,
  JWT/JOSE, Actuator and HTTP-client integration dependencies explicitly. Use
  Boot's Jackson 3 (`tools.jackson`), not framework internal codec beans.
- Depend on `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`.
  Record framework commit `385729a254261de128df491505acd8898cc0a021` as the initial
  source baseline. Develop locally using the framework snapshot already installed
  in the developer's local Maven repository. The developer installs after
  framework changes so the artifact matches the local framework source checkout.
  No separate artifact/source-revision verification is required for SC1; rerun
  affected checks after framework changes. Sidecar builds do
  not rebuild the framework. No snapshot repository or framework-source build
  job in Sidecar CI is required.
- Use the local-first release sequence: complete local Sidecar integration against
  the snapshot, complete framework release checks and publish framework
  `1.0.0-beta.4` to Maven Central, then switch Sidecar to that released dependency
  before its final build/tests and final commit. Prepare ordinary push/PR CI that
  resolves the published Maven dependency. Successful hosted CI verification is
  deferred until that dependency is published; it does not block local SC1
  acceptance. Do not claim CI success before it runs.
- Enforce the closed `ai.loomspan.api` boundary for Sidecar production and test
  code with ArchUnit. Forbid `ai.loomspan.internal..` and
  `ai.loomspan.autoconfigure..` dependencies. No internal replacement beans,
  reflection bypass, undocumented settings, or new framework SPI.
- Keep documented framework properties under `loomspan.*`, Sidecar-owned
  properties under `loomspan-sidecar.*`, and standard Boot keys under their
  standard namespaces. Supply secrets through environment variables.
- Default `loomspan.skills.locations` to `file:/sidecar/skills/**/*.yaml` and
  `file:/sidecar/skills/**/*.yml`; allow overrides. Reserve
  `loomspan-sidecar.rest-routes-location` with default
  `file:/sidecar/rest-routes.yaml` for SC4. The scaffold must not parse or scan
  that route file as a skill. Do not add a framework scanner exclusion.
- Prove a mounted model-backed YAML skill registers with explicitly supplied
  model/connection configuration. A manifest alone does not configure a model.
  Use local deterministic fixtures rather than requiring an external provider
  account. No production REST handler or Java `@SkillMethod` skills in SC1.
- Expose Actuator health/readiness on a separate management port. Keep other
  management endpoints unexposed; do not introduce execution/catalog routes or
  a temporary production authentication path. SC2/SC3 deliver those together.
- Preserve the committed repository workflow, guidance, phase authority and
  dated ticket conventions. Complete a README describing build, configuration,
  mounted-skill startup and current phase limits, without claiming later features.

## Acceptance criteria

- [x] Clean committed Sidecar source builds and passes `mvn verify` using the
  developer-installed snapshot, wrapper and aligned platform. README
  commands establish the same result; no external model account is required.
- [x] The architecture check covers production and test classes, passes for the
  supported API, and is proved to fail with a temporary forbidden dependency
  that is removed before completion. No forbidden integration survives.
- [x] A YAML skill under the default mounted directory registers with explicit
  model/connection settings. Both YAML suffixes and location overrides work;
  the separate route file is not loaded as a skill or parsed by Sidecar yet.
- [x] Health/readiness work on the separate management port; unrelated management
  endpoints are not exposed. No execution API, temporary authentication path,
  production REST handler or Sidecar Java skill is introduced.
- [x] Configuration, environment-secret guidance and README match the implemented
  scaffold and preserve prefix ownership, startup loading and public API limits.
  Repository workflow and planning links remain usable from this checkout.
- [x] Push and pull-request CI is prepared to build/test Sidecar using the published
  Maven dependency, without checking out or building framework source. Local SC1
  verification uses the installed snapshot. Record hosted CI execution as deferred
  until framework publication; final Sidecar verification must pass against the
  published dependency before the final commit and release.

## Context

[SC1](../phases/phase-sc1.md) owns this scope.
[Handoff](../beta4-handoff.md) and
[alignment review](../beta4-framework-alignment.md) record dependency evidence
and limits. Sidecar baseline `20d7f92` contains the workflow but no application.
Framework source is locally available at `C:/opendev/code/loomspan-framework`.

Subsequent delivery is SC2+SC3 (JWT, asynchronous API, ownership, queue/store and
shutdown gate), SC4 (generic handler), then SC5 (packaging and real integration).
SC5 must pass local snapshot integration before the framework's final release
checks; retain exact commands, results and tested source state as local evidence.
Sidecar then switches to the published dependency for its final build/tests,
hosted CI verification, final commit and eventual release. SC1 does not publish
the framework or wait for its publication to finish local scaffold work.
Internal framework gaps are corrected there, never bypassed in this application.

Excluded: route parser/client behavior, HTTP execution/catalog endpoints, worker
queue/store, production JWT decoder wiring, container publication, hot reload,
retries, streaming, durable storage, token exchange and framework API changes.
Exact application layout, Sidecar Maven coordinates/version, dependency artifact
selection and fixture structure are ordinary implementation choices within the
requirements, not a request to reopen the product design.

## Execution profile

- **Recommended:** full
- **Confidence:** high
- **Rationale:** The initial application establishes cross-repository build,
  startup, security exposure and deployment/configuration boundaries; material
  implementation choices require research, planning and independent review.
- **Reassessment triggers:** Reassess against the current checkout if scaffold
  implementation has already landed or the framework pin changes. These
  requirements remain binding under every profile.

## Execution notes

- 2026-09-13 simplicity review: removed the temporary controller prohibition;
  retained the permanent public-API and no-Java-skills architecture rules.
  Removed redundant default assertions, duplicate web-mode configuration and an
  ineffective decoy fixture. The custom-location test proves registration from
  an override, not replacement of files at the actual default mount.
- Removed three redundant Victools version pins; the selected dependency tree
  remains unchanged. The Guava and Error Prone pins remain pending justification
  of their compatibility purpose; they change transitive version selection.
- Verification after cleanup: `./mvnw.cmd -B -ntp verify` passed against the
  installed beta.4 snapshot. This is cleanup verification, not completion of all
  SC1 acceptance criteria or hosted CI evidence.

## SC1 closeout

- SC1 is locally complete. The developer confirmed responsibility for installing
  after framework changes so the installed snapshot matches the local framework
  checkout. Separate artifact/source-revision verification is removed as an SC1
  gate; SC5 release evidence requirements remain unchanged.
- Verified Sidecar `e3c3f58` with Java 21.0.2 and Maven wrapper 3.9.11:
  `.\mvnw.cmd -B -ntp clean verify` passed all 8 tests, including after removal
  of temporary probes. A fresh `git archive HEAD` extraction also passed
  `.\mvnw.cmd -B -ntp verify` (8 tests). Git clone was blocked by local ownership
  checks; the clean-source build used the archive instead.
- Temporary `DefaultMountProbeTest` passed with actual `C:/sidecar/skills/`
  defaults, both YAML suffixes, explicit local model configuration, and a sibling
  route-file decoy. Temporary `ForbiddenDependencyProbe` made the architecture
  test fail on `ai.loomspan.autoconfigure.LoomspanProperties` as required.
  All probe sources and mount files were removed before final verification.
- Local ignored logs: `sc1-final-verify.log`, `sc1-clean-source-verify.log`,
  `sc1-default-mount.log`, and `sc1-negative.log`. Hosted CI remains deferred
  until framework publication and is not claimed by this closeout.
- The earlier Guava/Error Prone pin rationale note remains a cleanup follow-up;
  no dependency change was made during this verification.
