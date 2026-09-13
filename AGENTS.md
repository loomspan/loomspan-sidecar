# Loomspan Sidecar repository guidance

## Framework boundary

- Application and test code may depend on Loomspan Java types only from the
  closed supported `ai.loomspan.api` surface. An ArchUnit test must forbid
  dependencies on `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`.
- `RestSkillHandler` is the deliberately planned, supported SPI for the generic
  outbound handler. This authorization does not permit new SPIs, framework bean
  replacement, reflection into internals, or undocumented configuration.
  New extension contracts require explicit developer planning.
- An unavailable public contract is a framework issue to resolve there, not
  permission to import an internal type. Sidecar hosts no Java `@SkillMethod`
  skills. Standard Spring APIs are allowed.
- Use documented `loomspan.*` keys for framework behavior and
  `loomspan-sidecar.*` for Sidecar-owned configuration. Boot configuration
  such as SSL bundles remains under its standard namespace.

## Plans, evidence, and workflow

- [Delivery handoff](ai/thoughts/beta4-handoff.md) and the local SC phase files
  own current Sidecar scope and sequencing. Keep one authoritative set here.
- Use this repository's `ai/commands`, including Full/Fast/Direct eligibility
  and dated ticket filenames. Do not replace them with framework commands or
  copy completed framework reports as Sidecar implementation evidence.
- [Design lens](ai/thoughts/design-lens.md) records cross-ticket constraints.
  Each implementation unit must supply its own tests and documentation.
- Do not infer completed Sidecar behavior from framework tests. Use only the
  public surface in Sidecar tests, including shutdown integration tests.

## Dependency and release policy

- Initial framework source pin: `385729a254261de128df491505acd8898cc0a021`;
  Maven dependency: `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`.
  Build/install that framework commit before Sidecar locally and in CI. Reinstall
  and retest after framework changes; update the recorded pin deliberately.
  No snapshot repository is introduced.
- Consult documentation from the same framework commit during development.
  The separately installed `0.1.0-SNAPSHOT` documentation skill is stale for
  beta 4. See the handoff's version-aligned source links.
- Prove SC5 integration against the snapshot before final framework release
  checks. Release the framework first, then pin and verify Sidecar against the
  published dependency before releasing Sidecar. Never overwrite a release.
