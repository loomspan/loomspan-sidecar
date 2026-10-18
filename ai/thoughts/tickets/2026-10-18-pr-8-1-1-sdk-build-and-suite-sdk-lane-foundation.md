# PR 8.1.1: Independent Java SDK build and external-suite SDK lane foundation

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

The Java/Spring SDK v1 has its own Maven build in the Sidecar repository. The
external test suite has a separate SDK lane that builds and runs the actual
Sidecar revision against its pinned Framework dependency and records evidence.
Every later Phase 1 ticket adds SDK code and composed scenarios on this
foundation, so it must exist early and stay small. It is a foundation, not a
generic deployment platform.

## Requirements

### SDK build (loomspan-sidecar)

- Create an independent Maven reactor under `sdks/java` with two modules:
  - `ai.loomspan:loomspan-sdk-core`: will hold the blocking HTTP client, DTOs,
    credentials and signing. It has no Spring Boot runtime, no Loomspan Framework
    dependency and no Sidecar Java dependency.
  - `ai.loomspan:loomspan-sdk-spring-boot-starter`: core plus optional Spring
    Boot servlet integration. It has no Framework or Sidecar Java dependency.
- Use Java 21 as the baseline and Spring Boot 4.1.0 for the starter.
- The root Sidecar build stays independent. It does not include the SDK
  reactor, and its Framework dependency stays
  `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.8`.
- The SDK build must fail if either module gains a dependency on Framework or
  Sidecar artifacts, including transitive ones.
- The SDK development version is an implementation detail, not a release
  commitment. No publication, release or hosted CI configuration is in scope.
- Modules may be minimal skeletons in this ticket. Later tickets add the
  behavior. Do not add placeholder public APIs that later tickets would have to
  delete.

### External-suite SDK lane (loomspan-sidecar-test-suite)

- Add an SDK lane in `C:/opendev/code/loomspan-sidecar-test-suite`. It is
  separate from the suite's existing lanes.
- The lane builds Sidecar from the actual local Sidecar checkout with its pinned
  Framework beta.8 dependency. It also builds and locally installs the SDK
  artifacts so later scenario drivers and the reference app can consume them.
  The lane does not rebuild Framework source.
- Provide a small runner that selects one of the three recipes: `local`,
  `standard` or `hardened`. Only the foundation smoke scenario exists in this
  ticket. Selecting a recipe that has no scenarios yet fails clearly rather than
  passing vacuously.
- Smoke scenario: start the built Sidecar with deterministic model fixtures,
  temporary test state and no provider access. Confirm it reaches readiness,
  then shut it down cleanly.
- Record evidence for every run: selected recipe, tested Sidecar revision, the
  Framework version Sidecar resolved, the SDK build, and concise pass/fail
  results with safe diagnostics.
- Never log credentials, private keys or business payloads.
- Preserve the suite's existing beta.9 embedded lane, beta.3-based
  Python/Sidecar lane, business examples, reference fixtures and maintained
  reference packs unchanged. Do not restore discarded test infrastructure.
  Evidence from the existing lanes does not prove SDK integration.
- Keep harness functions small and validation readable (no one-line validation
  chains). Apply a formatter. Follow the test suite's own repository guidance.

## Acceptance criteria

- [ ] `sdks/java` builds both modules on Java 21 independently of the root
      Sidecar build. The starter compiles against Spring Boot 4.1.0.
- [ ] The root Sidecar build is unchanged in scope and still depends on
      Framework `1.0.0-beta.8`.
- [ ] Adding a Framework or Sidecar dependency, direct or transitive, to either
      SDK module fails the SDK build.
- [ ] The suite's SDK lane builds the actual Sidecar checkout and the SDK
      artifacts, and runs the smoke scenario to readiness with deterministic
      fixtures and no provider calls.
- [ ] The runner accepts `local`, `standard` and `hardened`. A recipe with no
      scenarios yet fails clearly.
- [ ] Each run records the recipe, Sidecar revision, resolved Framework version,
      SDK build and pass/fail evidence, with no secrets or payloads.
- [ ] The existing beta.9 embedded lane, the Python/Sidecar lane and their
      fixtures and reference packs still run, and none of their content was
      changed.

## Context

- Canonical sources:
  - [Roadmap](../phases/2026-10-11-java-spring-sdk-roadmap.md)
  - [Phase 1](../phases/2026-10-15-sdk-v1-phase-1-implementation-integration.md), delivery group 1
  - [API reference: build and packaging](../phases/2026-10-15-sdk-v1-api-reference.md#build-and-packaging)
  - [Acceptance matrix](../phases/2026-10-15-sdk-v1-acceptance-matrix.md): "Framework/build boundary" and "Harness and evidence"
- First of eight Phase 1 tickets (PR 8.1.1–8.1.8). Later tickets:
  - 8.1.2 adds the blocking client and its first composed scenario.
  - 8.1.5–8.1.8 add the reference app and the recipes.
- Nonbinding hints:
  - The Maven Enforcer plugin's banned-dependencies rule is one way to enforce
    isolation.
  - The suite's existing tooling, layout and run commands should guide where
    the lane lives and how it runs Sidecar (process or container). Planning
    selects the approach.
- Out of scope: client behavior, signing, trust, callbacks and recipe-specific
  networking.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** This sets up a new cross-repository build and test-harness
  structure that every later ticket depends on. How the lane builds, runs and
  isolates Sidecar from the existing lanes still needs discovery and design.
- **Reassessment triggers:** none

## Pipeline notes

- The work spans two repositories: the SDK build in `loomspan-sidecar` and the
  SDK lane in `loomspan-sidecar-test-suite`. This is intentional.
