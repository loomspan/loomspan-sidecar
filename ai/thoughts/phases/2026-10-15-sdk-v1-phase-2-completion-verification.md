# Phase 2: completion verification

**Development policy: replace superseded contracts directly; no compatibility shims,
legacy adapters or parallel obsolete APIs.**
Apply the [design lens](../design-lens.md#simplicity-and-technical-debt).
No data reset is required by these verification activities.

Verify the completed [Phase 1](2026-10-15-sdk-v1-phase-1-implementation-integration.md)
against the [bounded acceptance matrix](2026-10-15-sdk-v1-acceptance-matrix.md).
This phase adds neither features nor a second expanded test suite.

- Run relevant Sidecar and SDK builds and owning tests.
- Verify clean plain-Java and Boot consumers of locally built SDK artifacts,
  dependency isolation and public Framework boundaries.
- Review the three real recipe results, focused failure/recovery tests and
  no-replay evidence; rerun only where changes or unresolved failures justify it.
- Check documentation examples, exact-role guidance, callback configuration,
  trust/expiry consequences and DevOps TLS ownership against implementation.
- Obtain an independent security review of the implemented boundaries and evidence.
  Findings are resolved with proportionate tests; new product requirements require
  a scope discussion.

Keep existing external-suite assets and lanes intact. No provider calls are needed.
Completion records what was tested and its limits; it is not a claim of testing
every customer network configuration.

Release timing, versions, tags, publication and hosted release gates are outside
this effort. The repository's separate release policy remains applicable if the
developer later requests a release. No tickets are created by this plan.