# HTTP executor integration and completion — Code Review Cycle 2

## Scope and Repository State

Independent Step 5, pipeline mode, profile `full`. Reviewed the complete current
ticket scope from Git in both repositories, including unstaged and untracked
files. Baselines are Sidecar `27108f1b46ad037bfc54054897282c67d82c3626` and suite
`1a79ba7c5ad106665bddfec363d5737a2df8ebe4`; both originally started clean.
No prior review document was read. Read the controlling ticket, research, both
plans, automation/review protocols, repository instructions and design lens.

Sidecar changes reconcile release-line safety and two obsolete test assumptions.
Suite changes replace equipment/SDK declarations and generators, separate login
and execution authentication, stage the current built runtime, update packaging
and guidance, and introduce the explicitly authorized separate namespace contract.
Reviewed connected public invocation, configuration, security, response, lifecycle
and test paths beyond changed lines against matching local Framework documentation.
No implementation artifact was changed in this context.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. Only this review document was written.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Invocation-only HTTP domain validation; structural/integrity checks remain | Captured executor maps; runtime public reloader preparation/publication; no HTTP preflight | `HttpConfigurationIntegrationTest`: invalid startup/publication/import/rollback/restoration and invocation rejection; management/recovery tests | implemented |
| All equipment/SDK consumers use executor YAML | Nine operation-local leaves, whole-input equipment bodies, bodyless SDK callbacks, target maps and current shipped remote execution YAML | Full generator pytest; all nine leaves independently compared against original full schemas/RBAC/descriptions; composed smoke receipt | implemented |
| Preserve business contract under authorized namespace change | Direct selection of separate `reference-namespaced`; unchanged strict matcher and original model/business files | Recursive 316-change review regression; all 120 actual recorded requests uniquely match current strict contracts; eight actual terminal results exactly match accepted business results after case-only rebinding | implemented |
| Direct/tool-call fidelity, terminal/recoverable behavior, authentication/expiry, bounds and no replay | Supported annotated HTTP executor; request-local headers/timeouts; disabled retry/redirect/cookie handling; bounded decoder | Public `HttpExecutorIntegrationTest`, `HttpSkillExecutorTest`, mapper/configuration/TLS/failure tests; SDK independent attempt/forward/effect counters | implemented |
| Captured publication/restoration, ownership and shutdown lifetime | Public handoff/captured configuration, durable-generation registry and Framework-aligned client lifecycle | Configuration/correlation/shutdown integration; Local real TERM completion/cutoff receipts | implemented |
| Standard HTTPS and Caddy-owned mTLS | Normal JVM public-only truststore; Caddy owns client identity and loopback/fixed-destination boundary | Complete Standard and Hardened receipts, including positive/rejection/isolation/loss/rotation/outbound scenarios | implemented |
| Reconcile prior blockers and current build/package assumptions | Beta.9 release-plan handling and snapshot packaging guard; current JAR staging; portable manifest integrity | Independent Maven/Python/pytest checks below; maintained SDK mandatory build receipts | implemented |
| Current public API/guidance and honest release boundary | No production Framework internal imports; ArchUnit includes tests; refreshed docs match local Framework; POM remains beta.4-SNAPSHOT / Framework beta.9-SNAPSHOT | Active scans, architecture tests, independent package/source inspection | implemented |

## Active Project Guardrails

- Full remains the appropriate profile: the scope crosses protocols, authentication,
  lifecycle and serialized client contracts. No reduced-assurance route was used.
- Superseded REST declarations/routes are removed directly. No adapter, fallback,
  new SPI, internal import, Framework bean replacement or Java skill in Sidecar.
- Public login verification precedes execution issuance. Issuance retains verified
  subject and exact allowlisted roles and bounds expiry by the source credential.
  Callbacks independently validate signature, issuer/audience, type/purpose and
  expiry using current public trust; business permission/record checks remain.
- Private execution signing material mounts only into Python; Sidecar receives
  public JWKS. Docker context excludes `.runtime`. No authentication credential
  was introduced into model/business payloads or diagnostic output by this change.
- Original reference assets and model declarations remain unchanged. Independently
  checked all nine protected raw reference hashes against the pre-run receipt and
  tracked contents against Git. Namespace case/reply changes are exactly the
  allowlisted structural identity substitutions; other reply characters and all
  business expected values are preserved. Manifest changes are only current file
  hashes plus explicit namespace-review metadata.
- Build tools consume the installed snapshot without Framework rebuild or a
  source/artifact provenance gate. Portable package checks retain per-artifact and
  source/configuration integrity. Preparation preserves existing keys/credentials;
  installation refuses existing runtime state.
- No paid provider, live commitment, store reset, source commit/push/tag or release
  was performed. Unit-test commits/tags are confined to disposable test repositories.

## Open Questions and Assumptions

None affecting current correctness. The developer-installed snapshot and matching
local Framework checkout are used under the repository's stated workflow.

## Verification Results

Commands were executed independently from their named repositories in this context.

- PASS — Sidecar `./mvnw.cmd -B -ntp verify`: 300 tests, zero failures/errors/skips;
  executable JAR packaging/repackaging succeeds (4:59).
- PASS — Sidecar `python -m unittest discover -s scripts -p 'test_*.py' -v`: 21 tests.
- PASS — Sidecar `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`: 11 tests.
- PASS — suite `.venv/Scripts/python.exe -m pytest -q`: 269 tests, including the clean-LF-checkout metadata regression.
- PASS — suite `mvn -B -ntp -f apps/java/pom.xml verify`: two tests and packaging.
- PASS — `git -c core.safecrlf=false diff --check` in both repositories.
- PASS — active removed-contract/version searches in both repositories: remaining
  beta.8 matches are a negative coordinate fixture and an explicitly historical
  agreement entry; no active removed-contract dependency.
- PASS — independent Python stdin audits: nine original leaf contracts preserved;
  nine protected reference hashes preserved; 27 refreshed Framework documentation
  paths match the matching local checkout; namespace manifest differences bounded;
  all grouped report checks true (8 equipment + 224 TypeSafe); actual 120 captured
  model requests strictly and uniquely match all 15 steps per case/host; eight
  terminal results match original accepted business values.
- PASS — inspected complete maintained receipts under suite `evidence/sdk/`:
  `pr9-3-local-2-4716d69d0f` (13 scenarios),
  `pr9-3-standard-3-4716d69d0f` (8), and
  `pr9-3-hardened-4716d69d0f` (9). Every selected scenario and cleanup passes;
  response-loss attempt/forward/effect counters are exactly one, with five seconds
  of quiet observation. Standard rotation records independent trust adoption and
  removal, signer restarts, old-token rejection before expiry and admitted completion.
- PASS — retained composed-workspace source comparison: all changed/new paths match
  current source except the four explicitly documented later metadata files:
  `tests/test_replay.py` and namespaced README/manifest/ledger. Runtime source and
  the four case-file bytes match. Runtime receipts precede those metadata-only edits.
- NOT RUN — full Docker SDK/business matrix again in this context: independently
  inspected complete current receipts and bundles from the retained isolated
  workspace, rechecked their observable outcomes and strict matching, and ran fresh
  broad source tests. Repeating the expensive matrix adds no material assurance for
  the four later metadata/test edits; current pytest covers both source-manifest forms.

## Residual Risks and Optional Developer Checks

The documented isolated diagnostic header-read failure did not reproduce in the
complete grouped rerun; no retry or assertion relaxation was added. This remains
an observation, with current complete deterministic and component evidence passing.
No configured external deployment was exercised. Snapshot proof does not satisfy
the released-artifact/hosted-CI gate: publish Framework beta.9 first, pin its released
artifact, repeat Sidecar final build/tests/final commit and verify CI before Sidecar
beta.4 release. Never overwrite releases. Existing development stores/credentials
remain untouched; any later state replacement remains developer-owned.

## Disposition

`clean`. Full source review found no actionable issue, made no implementation
changes, and completed sufficient independent verification. Only this review
document changed; no further fresh review cycle is required.
