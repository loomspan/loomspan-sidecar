# Independent instruction replay observations — 2026-09-25

This is a bounded behavioral evaluation of the actual candidate instructions, not an installation, published compatibility evidence, or a mechanical assertion against expected answers. The evaluator read `README.md` and `scenarios.json` under `C:/opendev/code/loomspan-framework/scripts/tests/fixtures/skill-install`, then the actual installer, actual fixture project/source files, actual router, and actual Sidecar skill/integration reference. It did not read `expected.json`, tickets, plans, or research. No network or installations were performed. Only this report and a temporary before-hash capture were written.

Actual read order: installer and scenario inputs; all project files and source-file inventory; before hashes and source-file contents, router and Sidecar SKILL; Sidecar integration reference. Shared source reads were batched, rather than physically repeated for each independent scenario. Each scenario below applies its own overrides: in particular, the compatibility text read for R5 is not usable in R6a. Decisions below were made from these reads before any reviewer comparison.

## Candidate identity and source observations

SHA256 of actual evaluated files:

| File | SHA256 |
| --- | --- |
| `C:/opendev/code/loomspan-framework/agent-skills/loomspan-install/SKILL.md` | `5FD649D58DF049762D898459D1E625D4EA2D5FBBD60B4B0B0E249AFD638D0A1F` |
| `C:/opendev/code/loomspan-framework/agent-skills/loomspan/SKILL.md` | `ECE3029BD16326B973462660029F4136AEA52308169529F3A86DBE033D6E55EC` |
| `C:/opendev/code/loomspan-sidecar/agent-skills/loomspan-sidecar-authoring/SKILL.md` | `7A09252D75B6E21B18FEBAE96514A5EAA059595FF2AE2C960567A37C8763D7EE` |
| `C:/opendev/code/loomspan-sidecar/agent-skills/loomspan-sidecar-authoring/references/integration.md` | `023279FF5EBA484EFCD051A379C64A1CBBE8886790E1B4329075DE6BE1E267C6` |

For compact exact path reporting, `F` below means `C:/opendev/code/loomspan-framework/scripts/tests/fixtures/skill-install`. These fixture directories are immutable revisions by the supplied host contract; no actual commit SHA is supplied, so none is invented.

Observed source groups (every listed file was read):

- `S`: `F/sources/sidecar/v2.3.0/pom.xml` declares root 2.3.0 and direct starter `${loomspan.version}` whose same-POM literal is 4.5.0. `agent-skills/loomspan-sidecar-authoring/SKILL.md` beneath that revision declares name `loomspan-sidecar-authoring`, component `sidecar`, version 2.3.0, framework marker 4.5.0. Exact official source stand-in: `https://github.com/loomspan/loomspan-sidecar`, tag `v2.3.0`.
- `J`: `F/sources/framework/v4.5.0/pom.xml` declares root 4.5.0. Its `agent-skills/loomspan/SKILL.md`, `agent-skills/loomspan-docs/SKILL.md`, and `loomspan-console/agent-skills/loomspan-console/SKILL.md` have matching names and versions; components are framework, framework, console respectively. Exact official source stand-in: `https://github.com/loomspan/loomspan-framework`, tag `v4.5.0`.
- `K`: `F/sources/sdk-python/v7.8.0/package.json` maps published identity `loomspan-fixture-sdk-python`, version 7.8.0, to `agent-skills/loomspan-sdk-python`. Its SKILL declares matching name, component `sdk-python`, version 7.8.0. The complete folder includes `references/compatibility.md`. That test-only table says 7.8.0/2.3.0 supported and 7.8.0/2.4.0 incompatible. The README supplies the official exact source mapping; no real SDK owner URL is supplied. This is synthetic compatibility provenance, never a real published SDK promise.
- `O`: `F/sources/framework/v4.4.0/pom.xml` declares root 4.4.0; directory inventory contains no skill folders.
- `W`: `F/sources/framework/wrong-marker/pom.xml` declares root 4.5.0. Its same three manifests as J have correct versions, but `loomspan-docs` component is `sidecar`, not `framework`.
- `M`: `F/sources/framework/main/pom.xml` declares 4.6.0-SNAPSHOT, not 4.5.0-SNAPSHOT.

J/S/K manifests are deliberately minimal complete fixture bundles; they assert no other mandatory resources. The compatibility table is optional evidence, not an implied hard dependency merely because the recommended filename exists in another case. Real distributable closure remains a separate check.

## Proposed host calls and application of supplied outcomes

The following are conceptual host calls, not claims that a particular concrete skill-manager API exists. `install`/`replace` always means the complete exact folder in the chosen immutable source group, not merely SKILL.md. Shared ordered sequence for a successful case: (1) inspect project and resolve component versions; (2) inspect exact source root versions/manifests/resources for the whole selected set; (3) validate scope/capabilities; (4) inspect existing selected installations; (5) install/replace in the listed order; (6) read installed names and markers, including Sidecar's framework marker. Full source preflight precedes all writes. Scope defaults to project. Installer itself is never added. Calls in this section are simulated under README, and all result labels below refer only to that simulation.

Ordered set `JS` is: J/router `loomspan`, J/`loomspan-docs`, J/`loomspan-console`, S/`loomspan-sidecar-authoring`. `J` alone is the first three; `JSK` adds K/`loomspan-sdk-python` last. All successful marker reads compare against the versions and components above. Successful cases require a new session for refresh. Unknown SDK compatibility does not obstruct installing exact documentation unless explicit incompatibility is published.

### R1 — direct API

Inspected `F/projects/sidecar/deployment.md`: selected production Sidecar 2.3.0, Python direct HTTP, no SDK. Decision: choose S, resolve 4.5.0 from S POM, choose J; no SDK question, no application POM needed. Validate S/J before host inventory. Proposed calls: inspect existing project skills; install JS in order; read back four names/markers. Supplied result: all four completed and verified. Versions: Sidecar 2.3.0, framework/Console 4.5.0. SDK compatibility is not applicable; refresh new session.

### R2a and R2b — embedded Java

R2a inspected `F/projects/java-literal/pom.xml`, whose direct starter version is literal 4.5.0. R2b independently inspected `F/projects/java-property/pom.xml`, whose direct starter `${loomspan.version}` resolves to same-POM literal 4.5.0. Decision in each: select J only, source v4.5.0, project scope. Proposed calls after J preflight: inspect existing; install router, docs, Console; read back three markers. Supplied result for each: three completed and verified; refresh new session. No Sidecar or SDK skills, no question, no dependency edits or Maven resolution.

### R3a–R3f — focused resolution stops

These cases have zero proposed installation/replacement calls and no completed writes. Default project scope is available but cannot solve missing component selection. Questions are recorded rather than sent to the real developer because these are simulated requests without supplied answers.

| Case | Actual inspected project path relative to F | Decision and necessary question |
| --- | --- | --- |
| R3a | `projects/absent/README.md` | Non-Java project with no deployment target. Ask whether the project uses Sidecar or embedded Java and for its exact target version. Do not infer latest or 2.3.0 from another case. |
| R3b | `projects/conflict/deployment.md` | Production 2.3.0 and staging 2.4.0 conflict because environment unspecified. Ask which environment/version to target. Do not select production silently or seek unsupplied 2.4.0 sources. |
| R3c | `projects/java-chain/pom.xml` | Starter property points to another property. Even though its eventual literal 4.5.0 is visible, bounded rule excludes chains. Ask for the exact resolved starter version; disclose inability to resolve this chain under the skill. |
| R3d | `projects/java-inherited/pom.xml` | Direct dependency lacks version, parent supplied. Ask for exact resolved starter version; no parent traversal or Maven engine. |
| R3e | `projects/java-bom/pom.xml` | Only dependencyManagement entry exists, not a direct starter dependency. Ask whether this is embedded Java and for its exact resolved direct starter version. Do not treat managed 4.5.0 as the application dependency. |
| R3f | `projects/java-ambiguous/pom.xml` | Two direct starter dependencies give 4.5.0 and 4.4.0. Ask which exact resolved version applies; do not choose by newest or first occurrence. |

### R4a–R4c — source preflight stops

R4a inspected `projects/java-old/pom.xml`: direct 4.4.0. Inspect O; root matches but required router/docs/Console folders absent. Mark matching bundle unavailable and stop before host writes. Do not substitute main or legacy Console `loomspan`.

R4b inspected `projects/java-literal/pom.xml`: direct 4.5.0. Scenario maps selected official v4.5.0 source to W. Root passes; router and Console markers pass; docs component fails. Reject the entire preflight, zero writes, no partial router install. Need corrected exact source before retry.

R4c inspected `projects/java-snapshot/pom.xml`: direct 4.5.0-SNAPSHOT. Inspect M as snapshot main candidate; root 4.6.0-SNAPSHOT mismatches. Reject before skill writes. Report exact snapshot unavailable in supplied sources; neither change project nor silently accept current main. No successful installation or refresh claim for these three cases.

### R5 — SDK alignment and independent compatibility

Inspected `projects/sdk/deployment.md` and `requirements.txt`: Sidecar 2.3.0, Python package `loomspan-fixture-sdk-python==7.8.0`. Select S/J/K and inspect their actual files listed above. Component alignment: Sidecar 2.3.0, framework/Console 4.5.0, SDK 7.8.0. Compatibility evidence: K `agent-skills/loomspan-sdk-python/references/compatibility.md` at immutable v7.8.0, exact pair SDK 7.8.0 and Sidecar 2.3.0. It says supported in this synthetic replay only; no real compatibility claim follows. Proposed calls: inspect existing project skills; install JSK in order; read five installed markers. Supplied result: five completed and verified, new session required.

### R6a and R6b — compatibility is separate

Both independently start with R5's project/source selections. R6a makes K's compatibility file unavailable, so the R5 table cannot be consumed here. SKILL/package manifests still align; no required-resource reference is broken by this optional evidence absence. Report SDK 7.8.0/Sidecar 2.3.0 compatibility **unknown**. Proposed calls: inspect existing; install JSK project scope; read markers. Supplied result: five exact documentation skills completed and verified, new session required; no supported-runtime assertion.

R6b explicitly overrides owner evidence to declare SDK 7.8.0 incompatible with Sidecar 2.3.0, citing scenario's `sdk-python/v7.8.0/references/compatibility.md`. Apply that override rather than baseline supported table. Stop entire installation before writes; ask developer to resolve the incompatible runtime combination. Do not switch Sidecar/SDK versions or install the non-SDK subset while claiming the requested set is resolved.

### R7a and R7b — scope

Both inspect `projects/sidecar/deployment.md` and preflight S/J. R7a explicitly requests supported user scope: inspect existing user skills, install JS in user scope, read four markers. All completed and verified; new session required.

R7b supplies only user scope but makes no explicit user-scope request. Default project scope is unsupported. Stop with host limitation; zero install calls. Explain that a host supporting project scope or an explicit supported-scope choice is needed. Do not silently fall back to user scope or write directories manually.

### R8a–R8c — replacements and conflicts

Each uses the sidecar project, S/J versions, project scope, full preflight, then existing-installation inspection.

R8a existing router/docs/Console are 4.4.0 and Sidecar skill 2.2.0. Explicit consent covers all selected replacements. Proposed calls: replace router 4.5.0; replace docs 4.5.0; replace Console 4.5.0; replace authoring 2.3.0; read all markers including framework 4.5.0. Supplied result: four completed and verified. No new per-skill approval; no unrelated skills changed; new session.

R8b existing docs 4.4.0 must not be replaced. Proposed calls: install router 4.5.0; skip docs replacement; install Console 4.5.0; install authoring 2.3.0; read actual markers. Supplied result: three completed and verified; docs remains 4.4.0, skipped by instruction. Report partial, incoherent selected set because Sidecar requires docs 4.5.0. New session exposes changes but cannot cure docs mismatch. Do not report all aligned.

R8c existing `loomspan` is old Console runtime guidance, unmarked, 4.4.0; removal/replacement explicitly prohibited. Classify the conflict by responsibility rather than trusting its name as a router. Proposed calls: skip `loomspan`; install docs 4.5.0; install renamed Console 4.5.0; install authoring 2.3.0; inspect/read installed markers. Supplied result: three completed and verified, router skipped, old Console remains. No deletion or alias. Report incomplete routing/coherence and remaining developer resolution of the name conflict; new session for the three changes.

### R9a and R9b — actual versus planned outcomes

Both preflight S/J for sidecar project and inspect existing project skills. R9a proposed calls are install router then docs. Apply supplied outcomes immediately: router installation succeeds; docs installation fails. Stop further installs because subsequent outcomes are not supplied. Console and authoring remain planned, not attempted. A readback result for this override is not supplied, so router is host-reported completed but marker verification is unestablished; docs failed. Do not invent rollback, atomicity, cleanup, or later success. A new session may be needed to expose the one reported success; report partial and remaining work.

R9b proposed calls: install JS in order, then read markers. Four install calls report success; marker readback unavailable. Report four host-reported completions with installed component/version verification **unverifiable**. Planned source versions are S/J; do not promote these to verified installed markers. New session required, but refresh is not substitute evidence of alignment.

### R10 — actual router selection

Read actual router identified by hash above. Available skills are supplied scenario state, not inferred real installations. No installation requested or executed. For each independent request, choose only relevant guidance:

1. Investigate runtime failure: `loomspan-console`, read-only runtime evidence tools.
2. Explain Java invocation semantics: `loomspan-docs`, aligned to framework 4.5.0 selected from sidecar deployment/S POM.
3. Configure Sidecar direct API: `loomspan-sidecar-authoring`, aligned to Sidecar 2.3.0; direct HTTP needs no SDK.
4. Implement SDK authentication/callback lifecycle: available `loomspan-sdk-python` owns application integration. The sidecar project itself says no SDK; therefore do not invent an SDK version from this fixture or begin implementation. Explain/request exact SDK dependency/version if implementing rather than merely routing; framework/Sidecar versions cannot determine it.
5. Repeat with SDK specialist absent: explain missing SDK guidance and relevant `loomspan-install` option; no automatic installation, no invented SDK protocol or fallback to Sidecar as SDK owner.

Router choice itself is not proof of specialist version alignment. No runtime connection, writes, or speculative code was performed.

### R11 — actual Sidecar boundary response

Inspected `projects/sdk/deployment.md`, `requirements.txt`, actual Sidecar SKILL and `references/integration.md`. Response derived from those actual instructions: No. Management tokens authorize configuration authoring and cannot invoke execution `/v1/**`; never put one into execution input or use it as an application data credential. Execution requests travel from application to Sidecar and use independently issued execution JWTs. Callbacks travel from Sidecar into the application. Callback endpoints must independently authenticate their caller and authorize each requested record; a caller-supplied owner ID or management token is not ownership proof.

For caller-passthrough routes, the original execution token is forwarded unchanged; the application independently validates signature, issuer, appropriate audience, lifetime, subject, and roles. Invocation input does not become an authentication header. Sidecar does not mint, refresh, or exchange that token. Server JWT/route configuration belongs to Sidecar guidance. SDK language setup, authentication integration, callback/request-context/lifecycle handling and application changes belong to the SDK specialist at the actual SDK dependency version, with application tests for allowed and denied record access. The fixture's SDK metadata is not an SDK implementation protocol, so none was invented. Direct API users need no SDK.

Limitation: actual Sidecar instructions identify development 1.0.0-beta.1-SNAPSHOT / framework 1.0.0-beta.5-SNAPSHOT, while the scenario's synthetic deployment is 2.3.0 / 4.5.0. R11 explicitly permits this actual-guidance boundary evaluation. This response tests its stated security/ownership boundary, not a claim that current development instructions prove synthetic 2.3.0 behavior. No execution, publication, token administration, or code modification proposed.

## Observed instruction issues and limits

No blocking instruction defect was encountered in these supplied paths. The installer provides enough direction to reject invalid exact sources, honor scope/replacement choices, keep component versions independent, and report partial verification honestly. Two edges remain judgment calls rather than deterministic call scripts: installation order is unspecified (this replay uses the skill table's order), and a declined replacement permits unrelated authorized installs but requires explicit incomplete/coherence reporting. R8 follows that permissive reading. R9a conservatively stops after the first failure and does not assume readback results that its override omits.

R10 supplies specialist names but no installed versions, and its SDK request contradicts the direct-API project's absence of SDK dependency. Routing is answerable; actual implementation would still require dependency/version alignment. R11's actual skill and synthetic target have different versions as explicitly noted above. Synthetic manifests do not test real packaging closure. These limitations are retained rather than hidden behind a blanket pass count.

## Project hashes before and after

Before capture was taken after read-only project inspection and before simulation conclusions/report writing. After capture is appended below from a fresh filesystem hash pass; paths are relative to F. No fixture project files were written.

| Project file | Before SHA256 | After SHA256 |
| --- | --- | --- |
| projects/absent/README.md | 87186BF69A311F6E75E9A0B4D8D8653A2D64AB8339C541E9D78615D42AF3FB2E | 87186BF69A311F6E75E9A0B4D8D8653A2D64AB8339C541E9D78615D42AF3FB2E |
| projects/conflict/deployment.md | 5336C9ED4AF3A47C6FDF092C29E075F01021C30FB2EC1377149099D09EB094BA | 5336C9ED4AF3A47C6FDF092C29E075F01021C30FB2EC1377149099D09EB094BA |
| projects/java-ambiguous/pom.xml | DFCB9E1E279B2CEF287A0F8E24CAD4D860C1CA42CA93D39BCE45FC8A3B868CB6 | DFCB9E1E279B2CEF287A0F8E24CAD4D860C1CA42CA93D39BCE45FC8A3B868CB6 |
| projects/java-bom/pom.xml | D00A004725B0FA20421241B6AEC19F7DFE6F42F7066E13A6F9E8347D12BD5174 | D00A004725B0FA20421241B6AEC19F7DFE6F42F7066E13A6F9E8347D12BD5174 |
| projects/java-chain/pom.xml | 11D14533CBBEEAB7F7A5434C60C7A92C6D52AAA97420E31259C9B47245F12C21 | 11D14533CBBEEAB7F7A5434C60C7A92C6D52AAA97420E31259C9B47245F12C21 |
| projects/java-inherited/pom.xml | 2B495C4B47234930D041EA7F83958944B5C9153DC02A43A6CBE0ADFAD45202E5 | 2B495C4B47234930D041EA7F83958944B5C9153DC02A43A6CBE0ADFAD45202E5 |
| projects/java-literal/pom.xml | 99035CDE299AB7D992B0E089EBD65C5BEA8E904DEB06ACAA4671373516F648E0 | 99035CDE299AB7D992B0E089EBD65C5BEA8E904DEB06ACAA4671373516F648E0 |
| projects/java-old/pom.xml | 63CAA2363B901F9CA3125E0393E5C01230C1ED042C1C1C83EFA979BDE8336D96 | 63CAA2363B901F9CA3125E0393E5C01230C1ED042C1C1C83EFA979BDE8336D96 |
| projects/java-property/pom.xml | DA83811073DF597DBEAFC2B701F796D6BD1945EF01D9F862EC6540829D13B694 | DA83811073DF597DBEAFC2B701F796D6BD1945EF01D9F862EC6540829D13B694 |
| projects/java-snapshot/pom.xml | 7923303F3B4BE793A3E61454C4C5EAAA7734F50FD551DAF05120F2EC54D6FDFC | 7923303F3B4BE793A3E61454C4C5EAAA7734F50FD551DAF05120F2EC54D6FDFC |
| projects/sdk/deployment.md | C8281D96E6864BF61EDD21987A501849716766441FAB875B2E05BF7C60788540 | C8281D96E6864BF61EDD21987A501849716766441FAB875B2E05BF7C60788540 |
| projects/sdk/requirements.txt | 61278FD7CECCF35F5E4B259B19096E963184EC5235B31EFE761EBB157FF2EB25 | 61278FD7CECCF35F5E4B259B19096E963184EC5235B31EFE761EBB157FF2EB25 |
| projects/sidecar/deployment.md | BEAA228D9D08DF16969866BC6B1BAF78A62258B0AAB4933410CEF55898155752 | BEAA228D9D08DF16969866BC6B1BAF78A62258B0AAB4933410CEF55898155752 |

All 13 project-file hashes match. No simulated host installation performed any actual host writes.
