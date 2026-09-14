# Beta 4 Sidecar delivery handoff

## Status and authority

Prepared 2026-09-13. Framework FW1–FW3 implementation and FW4 documentation/local
preparation are complete. The developer confirmed completed framework manual
acceptance checks; final integration-dependent release gates remain pending.
Sidecar baseline `20d7f92` contains the committed workflow, README and license,
with no application yet. This handoff supplies repository guidance, phase
authority and the scaffold ticket; it does not implement or release Sidecar.

The five local phases below are authoritative for Sidecar. Framework's former
SC phase paths now forward here. The
[pinned original roadmap](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md)
and [original planning handoff](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/ai/thoughts/beta4-ticket-readiness.md)
preserve product rationale. Their old bootstrap state, PR-numbering instruction,
and pre-implementation framework status are superseded by this handoff.
The framework roadmap remains the cross-repository release map; its Sidecar
summaries defer to the local phases. Do not maintain two editable phase sets.

Historical [design decisions](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/ai/thoughts/beta4-design-review.md)
and [source grounding](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/ai/thoughts/beta4-code-grounding.md)
retain the D/G/R/S rationale referenced by the phases. Their descriptions of
missing framework implementation are superseded by the current alignment review.

## Delivery units

| Unit | Requirements | Dependency and complete outcome |
| --- | --- | --- |
| Scaffold | [SC1](phases/phase-sc1.md) | Buildable Boot application, public-surface guard, mounted YAML loading, management health and CI prepared for the published Maven dependency. Local verification uses the installed snapshot. |
| Authenticated execution API | [SC2](phases/phase-sc2.md) + [SC3](phases/phase-sc3.md) | After SC1; JWT verification, ownership, HTTP API, bounded queue/store, diagnostic selection and independent shutdown gates together. |
| Generic REST handler | [SC4](phases/phase-sc4.md) | After authenticated API; route-file/startup validation, binding, auth, transport bounds, TLS and verified host callback together. |
| Packaging and release | [SC5](phases/phase-sc5.md) | After handler; image/probes, packaged resource lifecycle, runnable quick start and release workflow; snapshot integration before framework release. |

SC2/SC3 use a test handler; the production generic handler arrives in SC4.
No temporary production authentication or handler path. Reuse one Sidecar
application fixture; focused boundary tests and packaged checks prove different
requirements. Each unit includes its own correctness and documentation.

## Framework pin and documentation

- Full commit: `385729a254261de128df491505acd8898cc0a021`.
- Dependency: `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`.
- Platform from the pinned POM/BOM: Java 21, Boot 4.1.0, Spring Framework 7.0.8,
  Security 7.1.0 and Boot's Jackson 3 mapper (`tools.jackson`). Use the BOM,
  rather than independently selecting transitive platform versions.
- Local checkout: `C:/opendev/code/loomspan-framework`. Consume its already-installed
  snapshot from local Maven and record the actual installed source revision.
  Reinstall and rerun affected checks after framework changes. Sidecar builds
  and CI do not rebuild framework source; no snapshot repository is introduced.
  Hosted CI uses the published `1.0.0-beta.4` artifact after framework publication.
- [Framework README](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/README.md),
  [Java API guidance](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/agent-skills/loomspan-docs/references/java-api/README.md),
  [skill authoring](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/agent-skills/loomspan-docs/references/skill-authoring/README.md),
  [supported-surface authority](https://github.com/loomspan/loomspan-framework/blob/385729a254261de128df491505acd8898cc0a021/loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java).
  Source inspection does not authorize dependencies on internal types.

## Release evidence boundary

SC5 must prove pre-dispatch checks, async JWT propagation through a YAML planner
and REST callback, all diagnostic modes, and packaged shutdown/resource behavior.
Record the tested Sidecar source state (commit plus any uncommitted changes),
installed framework revision, exact suite commands and retained local results
in the framework readiness record. Pre-publication Sidecar CI is not required.
Any discovered framework defects are fixed
and reinstalled before repeating affected integration. Then final framework
version/script/full-build/release-profile and nonpublishing workflow validation
run on the final framework release commit. After publishing framework beta.4 to
Maven Central, switch Sidecar to that released dependency, run its final build/tests
and hosted CI verification, then make the final Sidecar commit and eventual release.
CI evidence must identify the tested revision; local-first development does not
waive final release verification or any SC5 integration behavior.

[Alignment review](beta4-framework-alignment.md) records current evidence and
its limits. It is not packaged integration evidence or publication permission.

## Next ticket

[Scaffold ticket](tickets/2026-09-13-sidecar-scaffold.md) follows this repository's
dated naming convention and recommends the full profile. All later tickets live
here too. Ticket preparation does not start the implementation pipeline.
