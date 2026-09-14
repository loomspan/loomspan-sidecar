# Phase SC1 — Sidecar repository scaffold

Authoritative Sidecar phase, transferred and aligned on 2026-09-13 against
framework `385729a254261de128df491505acd8898cc0a021` (`1.0.0-beta.4-SNAPSHOT`).
See the [delivery handoff](../beta4-handoff.md) and
[framework alignment review](../beta4-framework-alignment.md).
The [original product roadmap](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md)
records settled intent; this local phase owns current Sidecar requirements.
Sidecar implementation and application integration evidence remain pending.

## Goal

A buildable, testable Spring Boot 4 / Java 21 application depending on the
locally installed `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`, with the guardrails that
make every later Sidecar phase safe: public-API-only enforcement,
configuration conventions, CI, and version coordination.

## In scope

- Maven project (wrapper included) with the starter dependency pinned to the
  development snapshot; Spring Boot web (servlet) and Spring Security.
- Align initially with Java 21, Boot 4.1.0, Spring Framework 7.0.8, and
  Security 7.1.0 from the framework POM/BOM. Declare Sidecar's web, JWT/JOSE,
  Actuator, and HTTP-client integration dependencies explicitly. Use Boot's
  Jackson 3 mapper (`tools.jackson`), not internal Loomspan codec beans.
- ArchUnit test forbidding any import of `ai.loomspan.internal..` and
  `ai.loomspan.autoconfigure..` from Sidecar code and tests.
- `application.yml` conventions: framework keys under `loomspan.*` only as
  documented; Sidecar keys under `loomspan-sidecar.*`; secrets via
  environment variables.
- Default mount layout: `/sidecar/skills/` for skill manifests and
  `/sidecar/rest-routes.yaml` for Sidecar's targets and route mappings. Framework skill
  scanning is restricted to the skills subdirectory; route loading is SC4.
- Default location configuration (overridable):

  ```yaml
  loomspan:
    skills:
      locations:
        - file:/sidecar/skills/**/*.yaml
        - file:/sidecar/skills/**/*.yml
  loomspan-sidecar:
    rest-routes-location: file:/sidecar/rest-routes.yaml
  ```
- Health/readiness via Actuator on a separate management port, as required
  by SC3 and SC5. SC5 verifies the packaged deployment and shutdown behavior.
- Initial framework source baseline: `385729a254261de128df491505acd8898cc0a021`.
  Local development consumes the already-installed Maven snapshot; record its
  actual source revision and reinstall after framework changes. Sidecar builds
  and CI do not check out or rebuild framework source. Prepare ordinary push/PR
  CI using the published Maven dependency, with hosted execution deferred until
  framework `1.0.0-beta.4` is published.
- `AGENTS.md` for the Sidecar repo carrying the public-API-only rule, the
  prefix rule, and the framework-first dependency policy.
- The committed Sidecar workflow already supports Full/Fast/Direct profiles.
  Preserve its repository-specific commands and dated ticket naming. Repository
  guidance and phase transfer are supplied by this handoff; scaffold work verifies
  their consistency without copying framework implementation history.

## Binding decisions

- Sidecar never implements or overrides framework internals; if a gap is
  found, it is a framework issue.
- Sidecar hosts no Java `@SkillMethod` skills. SC1 proves mounted model-backed
  YAML loading with explicit model configuration; REST handler behavior arrives
  in SC4, with test-only handlers available to SC2/SC3. Do not add a temporary
  production handler to make REST manifests start during SC1.
- Skills and routes load only at startup in the initial release. Disk edits
  require a restart. Reuse framework startup loading; no watcher, reload
  operation, or framework scanner exception for `rest-routes.yaml`.
- Ordinary beta 4 development uses the locally installed framework snapshot.
  Re-run framework `mvn install` after framework changes; record the installed
  source revision. No snapshot repository is introduced. After local SC5
  integration and framework release checks, publish the framework to Maven
  Central. Switch Sidecar to the released starter before its final build/tests
  and final commit. Hosted CI then verifies against that published dependency.

## Acceptance criteria

- [ ] After building and installing the recorded framework commit,
  `mvn verify` passes on a clean Sidecar clone using the snapshot dependency.
- [ ] ArchUnit test fails on an internal import (proved by a deliberately
  failing example, then removed).
- [ ] A YAML skill in the mounted directory registers at startup with its
  required model/connection configuration. Mounting a planner manifest alone
  does not configure its model.
- [ ] Default scanning includes skills under `/sidecar/skills/` and excludes
  `/sidecar/rest-routes.yaml`; custom skill locations remain supported.
- [ ] Push/PR CI is prepared to consume the published framework dependency.
  Hosted execution is deferred until framework publication and does not block
  local SC1 acceptance; successful final CI evidence remains required afterward.

## Ticket boundary

The Sidecar scaffold unit in the [planning handoff](../beta4-handoff.md)
owns the buildable application, dependency/architecture checks, mounts,
health and CI. Its ticket lives in
`C:\opendev\code\loomspan-sidecar\ai\thoughts\tickets`, as do all later
Sidecar tickets, after minimal local
repository/workflow seeding; the handoff records that prerequisite. No
Sidecar application implementation belongs in the framework repository.
