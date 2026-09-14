# Sidecar phases aligned with the implemented beta 4 framework

## Result and revisions

Subsequent developer decision: use local-first development against the installed
framework snapshot, then publish framework beta.4 to Maven Central before
Sidecar's final build/tests and final commit. The current ticket, SC1/SC5,
AGENTS.md and handoff implement that sequence. References below to exact-pin CI
describe the original review baseline and are superseded; no framework-source
build job or pre-publication Sidecar CI evidence is now required. The reviewed
framework contracts and recorded test results are unchanged.

Reviewed 2026-09-13 against framework commit
`385729a254261de128df491505acd8898cc0a021`, version `1.0.0-beta.4-SNAPSHOT`,
and Sidecar baseline `20d7f92`. All five phases remain viable with their
agreed product scope and four delivery units. No new framework API or SPI is
required by this review. This is a contract alignment review, not exhaustive
framework code review or proof of an implemented Sidecar application.

The developer confirmed completed framework manual acceptance checks and that
the framework AGENTS.md SPI rule intentionally gates unplanned extensions.
The deliberately approved `RestSkillHandler` satisfies that gate. The rule is
unchanged. The explicit SC5 and final release-validation gates remain pending.

The [handoff](beta4-handoff.md) identifies the authoritative transferred phases,
framework source/documentation pin and release sequence. Framework's old SC
files are forwarding links. Historical design reports remain in framework Git
history; they are not copied as Sidecar implementation evidence.

## Phase findings

All source anchors below are relative to the pinned framework repository.
`main/java` and `test/java` refer to the starter's `src` directories.

| Phase | Classification and evidence | Required Sidecar follow-through |
| --- | --- | --- |
| SC1 | Aligned. Root POM selects Java 21 and Boot 4.1.0. The locally resolved Boot BOM selects Framework 7.0.8, Security 7.1.0 and Jackson 3.1.4. `DefaultSkillCatalog` completes registration eagerly; `LoomspanPublicSurfaceArchitectureTest` closes the thirteen-type public API. | Pin the implemented commit, use Boot's platform and public catalog, prove mounted YAML with model configuration and exact-pin CI. Workflow was already committed; guidance/phase transfer is now prepared. |
| SC2 | Aligned with clarification. `DefaultSkillTemplate#prepareMap`, `#validatePrepared` and `#invokePrepared` establish shared preparation, caller-thread authorization and observation. `DefaultSkillCatalogTest` proves unfiltered immutable exact schemas; `DefaultSkillTemplateTest` covers null overloads, pre-checks, missing callbacks and exception precedence. | Pass the parsed map to both overloads; validation returns no normalized/prepared request and reserves nothing. Publish outcome after invoke settles, with available selected events; never wait for a guaranteed callback. |
| SC3 | Aligned. `DefaultSkillTemplateTest#capturesCurrentSecurityContextAuthenticationForRootInvocation`, `SkillRoleEvaluator` and the supported-surface integration fixture establish caller capture and role behavior. Framework uses standard `GrantedAuthorityDefaults`. | Propagate the actual request authentication onto the invoking worker; align role-prefix configuration. JWT decoder wiring, issuer/audience verification, queued expiry and verified callback identity still need Sidecar tests. |
| SC4 | Aligned with clarification. `RestSkillInvocation` freezes map/list containers while retaining other leaf identity. `YamlSkillCapabilityRegistrar` resolves one handler during registration; `DefaultSkillCatalog` completes that registration before snapshot creation. `DefaultSkillTemplate` preserves existing `SkillException` but generically wraps other runtime exceptions. | Construct handler independently of the catalog, then validate route correspondence using the eager public catalog. Reject non-JSON resolved values before HTTP. Throw `SkillException` for deliberately constructed bounded HTTP diagnostics that must reach the failure response; do not unwrap arbitrary causes. |
| SC5 | Aligned; actual application proof pending. `FrameworkExecutionLifecycle` closes its owning context synchronously, waits in lifecycle stop, owns one deadline and provides nonwaiting destruction fallback. Current internal phase is `Integer.MAX_VALUE`; this is source evidence, not a new supported integration constant or bean contract. `FrameworkExecutionLifecycleTest` and `FrameworkShutdownIntegrationTest` cover deadline, blocked writer, observer lifetime, both listener orders and shorter Spring phase timeout. | Keep Sidecar clients and caller workers alive until framework completion/cutoff, then perform bounded cleanup. Prove actual packaged wiring, immediate gates and queue discard through supported contracts, without internal imports, a second timer or shared listener priority. |

The framework README and checked-in `agent-skills/loomspan-docs/references/java-api`
topics for catalog/validation, REST, and observation/errors agree with those
contracts. The installed `0.1.0-SNAPSHOT` skill was used only as a routing protocol;
its old topic content was not used as beta 4 evidence.

## Changes made to the plans

- Replaced pre-implementation grounding introductions with the implemented
  framework pin and explicit Sidecar evidence limits.
- Preserved all original SC acceptance items and delivery ownership. Extended
  existing SC2 criteria to cover terminal failure without an observer callback;
  clarified that diagnostic presence tests use a mappable public view. There is
  no new response field, retrieval API, output cap or history reconstruction.
- Clarified SC4 failure-message construction and eager catalog startup. Its
  negative HTTP tests must verify the intended bounded diagnostic reaches the
  execution response without leaking the response body in that message.
- Updated SC5's framework prerequisites from future work to implemented
  framework proof, while retaining every packaged Sidecar integration gate.
- Updated SC1 bootstrap assumptions for the committed workflow and added the
  actual framework pin. Existing dated ticket naming supersedes the old
  framework handoff's proposed-PR-number instruction for this repository.

No route, authentication, queue, retention, configuration default or deferred
feature decision was reopened. SC2+SC3 remain one authenticated API unit. SC4
remains a complete handler unit; SC5 owns packaging and cross-repository release.

## Verification

Fresh focused verification against the unchanged framework production tree:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,ApplicationApiValueTest,YamlSkillCapabilityRegistrarTests,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test
```

Result: **74 tests passed, zero failures/errors/skips**, 2026-09-13 11:16
America/Los_Angeles. This session's earlier version-consistency check passed for
`1.0.0-beta.4-SNAPSHOT`; the earlier full-build evidence remains in the framework
readiness record. No full reactor/Console suite, new snapshot install, Sidecar
application test, remote CI validation or publication was performed in this pass.
SC1 must build/install the exact pin before relying on a local snapshot artifact.

Documentation verification preserved all **60 SC acceptance items** (6/21/7/16/10),
with the clarifications described above, and checked local Markdown link targets
and whitespace in both repository diffs. Pinned framework URL paths were checked
against local Git objects; remote reachability is not claimed.

## Remaining work

The [SC1 ticket](tickets/2026-09-13-sidecar-scaffold.md) is ready for the full
pipeline. This review prepares it without starting implementation. Provisioning
and actual CI access remain implementation/release inputs. The separate framework
observation-truncation proposal is not made a Sidecar phase prerequisite; the
available-history contract and current projection limits remain in force.
