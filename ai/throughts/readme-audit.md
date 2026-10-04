# README and supporting documentation audit

Date: 2026-10-04
Audience: Sidecar owner and documentation maintainers
Status: editorial rewrite complete; validation and technical uncertainties recorded below

## Scope and placement

Inspected README, all existing docs, the portable authoring skill and references,
quickstart/production examples, repository instructions and command eligibility,
configuration tests, selected controllers/security/execution code and verifiers.
Read Framework's current README and docs for editorial consistency; used its
local `v1.0.0-beta.7` tree for version-specific contracts and link targets.
Framework main is newer and is not technical compatibility evidence for Sidecar.

The user explicitly requested implementation of this documentation rewrite.
Scope is documentation and application examples; no runtime, dependency, schema,
or security-contract changes. This is bounded editorial work with targeted
verification; no release or change to an existing deployment is part of it. The repository's
Fast-Track eligibility fits the breadth of relocation better than a mechanical
Direct change. The user did not invoke the numbered pipeline; no numbered-stage
completion or independent pipeline review is claimed.

Applied the [design lens](../thoughts/design-lens.md#simplicity-and-technical-debt):
replace superseded documentation directly, add no compatibility shims or parallel
contract definitions, and keep verification proportional to the documentation scope.

Docs explain adoption, architecture, setup, operations, and upgrades. Portable
references retain advanced application contracts and can be read without an AI.
Maintainer material is parked in [readme-maintainer-material.md](readme-maintainer-material.md)
for later keep/move/delete review, using the intentionally requested `ai/throughts`
spelling. Its historical claims are not new acceptance evidence.

## Verified findings and disposition

| Finding | Evidence and significance | Disposition |
| --- | --- | --- |
| README stopped before useful execution | Original quickstart returned an empty catalog; copying fixtures did not publish them. | Complete database-mode triage walkthrough now covers key provisioning, administrator setup, publication and polling. |
| Evaluation and maintainer audiences were mixed | README had an SDK directory roadmap; operations contained release commands and beta.6 test receipts. | Parked maintainer extracts; README now leads with value, integration choice and application work. |
| Version prose contradicted checkout metadata | POM and authoring skill say beta.2; AGENTS and former release section say beta.2-SNAPSHOT/next release beta.2. | User docs report actual metadata without claiming publication. Repository policy not changed; owner should reconcile release status. |
| Quickstart route file was not directly valid authored YAML | Defaulted base URL expression conflicts with `RestRouteLoader`'s exact-variable policy; `verify-production.py` substitutes `${TARGET_URL}` before use. | Kept verifier input unchanged; labeled it as a fixture and explicitly warned against pasting it. New application quickstart needs no REST route. |
| Production guidance referenced a removed workflow | Draft-loss confirmation prose contradicted shared draft load/validate/publish APIs and current management UI. | Replaced with current draft replacement and explicit publication behavior. |
| Historical recovery prose was misleading about encryption | Former operations said backups had no “v1 encryption”; current provider credentials are encrypted separately, while the database is not wholly encrypted. | Clarified backup sensitivity and the need for the separately retained key. |
| “V1 limits” appeared in a format 3 contract | Operations repeated format limits and named the old rejection fixture. | Kept limits and compatibility contract in bundle reference; removed duplicate and test-fixture narration. |
| Process configuration table broke midway | Blank line separated trailing table rows from its header. | Rejoined the table; retained configuration markers and test-owned defaults. |
| Application contracts buried in operations | Exact leases, grants, failure stages, bundle format, accounts, and snapshot selection lived in a 900+ line guide. | Relocated to focused portable references, preserving advanced application-facing behavior. |
| “Console” meant two products | Embedded management UI and separate runtime Console were insufficiently distinguished. | Added architecture and Console/MCP setup, with separate credential purposes and owning-project links. |
| Skills had an ambiguous audience | Runtime skill execution and assistant guidance were mixed in overview text. | Explicit application-skill/Agent-Skill distinction, copyable AI discovery prompt, and human-readable map. |
| Main-only Framework guidance could drift | Current Framework docs describe beta.8-SNAPSHOT; beta.7 lacks some new docs paths. | Pinned technical links to existing beta.7 references and Console package paths; current README used only for orientation. |
| Reset notice lacked release provenance | Former operations specified rewritten V1/V3 development schemas without exact source/target versions. | Preserved original notice and explained the uncertainty in upgrades; no data reset performed. |
| Client save example could not validate | It declared a REST skill but supplied an empty route map. | Added a matching record route to the illustrative complete body. Destination endpoint and credentials remain application-owned. |

## Remaining uncertainties

- Published Sidecar artifacts, release tags, registry availability, and hosted CI
  have not been verified by reading local metadata. No publication claim is made.
- Exact supported upgrade pairs for earlier development databases and format
  transitions need a version-to-version migration ledger. Do not infer a deployed
  reset policy from the historical development notice.
- A provider-backed triage run depends on the developer's model account and
  credentials. Local fixture checks cannot prove its factual output quality.
- Detailed relocated contracts are preserved from the repository and spot-checked;
  this editorial audit is not a fresh proof of every security or failure-mode claim.
- The original fixture base URL and AGENTS version drift remain for maintainer
  resolution; changing verifier behavior or repository release policy is outside
  this documentation-only scope.

## Validation

- PASS — `python -m unittest discover -s scripts -p test_agent_skills.py -v`:
  six tests, including copying the complete portable bundle to an unrelated path.
- PASS — `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`:
  eleven tests for client behavior, credentials, conflicts, redirects and commands.
- PASS — `python scripts/sidecar_version.py check`: beta.2 / Framework beta.7
  metadata is aligned. This does not resolve stale policy prose or prove publication.
- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ConfigurationReferenceTest,RestRouteLoaderTest' test`:
  seven tests; documented configuration defaults and the route parser.
- PASS — `.\mvnw.cmd -B -ntp -DskipTests package` and
  `docker build --tag loomspan-sidecar:doc-audit-local .`. Packaging skipped tests;
  focused tests above ran separately.
- PASS — ad hoc local link/anchor scan of README, docs, skill bundle, example
  guides and parked material. Pinned Framework paths and anchors were checked
  against the local beta.7 Git tree. The scan also compared README YAML with the
  quickstart manifest and compiled the Python example. External HTTP availability
  was not checked; no new non-GitHub external source links were introduced.
- PASS — disposable Compose smoke check using the current image, isolated host
  ports, a generated setup credential/encryption key and a fresh named volume.
  Created an administrator, saved the exact triage manifest and empty routes,
  saved a dummy provider credential, validated and published, then ran the new
  polling example with only its local ports changed. The connection used the
  existing deterministic provider fixture instead of OpenAI; it verified 202,
  Location polling, COMPLETED, unchanged result text, 400 for missing required
  input and 401 for missing JWT. Fixture output is protocol evidence, not triage
  quality evidence. The disposable containers, network and volume were removed.
  A follow-up record-example check initially failed preparation after replacing
  the model YAML while retaining its managed credential. The verifier was corrected
  to remove that unused identifier, as the credential contract requires; client
  guidance now makes this transition explicit. Server validation then caught
  missing required per-target limits in the new record-route example; those
  fields were added before revalidation. The final run passed both the triage
  execution and the record-example save/validation checks, then removed its
  disposable deployment.
- PASS — management page labels and automatic validation behavior checked against
  `ManagementPagesController` and `editor.js`; quickstart instructions corrected.
- NOT RUN — real OpenAI invocation, interactive browser walkthrough, production
  deployment, external artifact availability, hosted CI, full Maven suite and
  the production/shutdown verifier. This change does not modify runtime behavior.

The final link scan checked 162 local/pinned links and anchors across 26 Markdown
files. Ad hoc validation scripts were kept under ignored `target/` during this pass;
they are not new runtime or test-suite dependencies. `git diff --check` passed.
