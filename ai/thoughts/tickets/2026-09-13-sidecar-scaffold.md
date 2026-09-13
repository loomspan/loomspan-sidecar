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
  source pin. Local instructions and push/PR CI must build/install that exact
  framework revision before building Sidecar. Use the local Maven repository,
  with no snapshot repository. Reinstall and reverify after framework changes.
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
- Establish Maven/release checks for Sidecar's own version and recorded framework
  dependency/source pin. Keep them proportional to this application; do not copy
  the framework's multi-artifact version script without that need. Development
  uses the snapshot; eventual publication requires the released framework after
  SC5 integration and framework-first publication. No publication in this ticket.

## Acceptance criteria

- [ ] A clean Sidecar clone builds and passes `mvn verify` after installing the
  recorded framework revision, using the wrapper and aligned platform. README
  commands establish the same result; no external model account is required.
- [ ] The architecture check covers production and test classes, passes for the
  supported API, and is proved to fail with a temporary forbidden dependency
  that is removed before completion. No forbidden integration survives.
- [ ] A YAML skill under the default mounted directory registers with explicit
  model/connection settings. Both YAML suffixes and location overrides work;
  the separate route file is not loaded as a skill or parsed by Sidecar yet.
- [ ] Health/readiness work on the separate management port; unrelated management
  endpoints are not exposed. No execution API, temporary authentication path,
  production REST handler or Sidecar Java skill is introduced.
- [ ] Configuration, environment-secret guidance and README match the implemented
  scaffold and preserve prefix ownership, startup loading and public API limits.
  Repository workflow and planning links remain usable from this checkout.
- [ ] Push and pull-request CI checks out the exact framework source pin, installs
  its snapshot, then builds/tests Sidecar. Record actual CI evidence when run;
  missing remote access is an evidence gap, never a claimed CI pass.
- [ ] Version/dependency checks accept the intended development snapshot and
  reject an inconsistent recorded dependency. Release-mode checks reject a
  SNAPSHOT framework dependency. No tag, upload or publication is performed.

## Context

[SC1](../phases/phase-sc1.md) owns this scope.
[Handoff](../beta4-handoff.md) and
[alignment review](../beta4-framework-alignment.md) record dependency evidence
and limits. Sidecar baseline `20d7f92` contains the workflow but no application.
Framework source is locally available at `C:/opendev/code/loomspan-framework`.

Subsequent delivery is SC2+SC3 (JWT, asynchronous API, ownership, queue/store and
shutdown gate), SC4 (generic handler), then SC5 (packaging and real integration).
SC5 must pass snapshot integration before the framework's final release checks;
Sidecar then pins and verifies the published dependency before its own release.
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
