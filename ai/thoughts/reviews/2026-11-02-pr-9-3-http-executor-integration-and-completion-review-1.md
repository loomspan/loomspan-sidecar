# HTTP executor integration and completion — Code Review Cycle 1

## Scope and Repository State

Pipeline Step 5, profile `full`, 2026-11-02 America/Los_Angeles. Reviewed the
complete ticket-scoped unstaged and untracked changes in both repositories.
There are no staged changes. Sidecar is on `main` at
`27108f1b46ad037bfc54054897282c67d82c3626`; the maintained suite is on `main` at
`1a79ba7c5ad106665bddfec363d5737a2df8ebe4`. Both began clean, so the controlling
ticket, research and plans attribute the current work. No prior review was read.

Scope included release scripts/workflows/tests; Python issuance, callback and
recovery identity; all nine executor declarations and their generator; shared
configuration/Compose/readiness; SDK foundation/Local/remote/outbound/shutdown
fixtures and consumers; runtime staging/distribution; trace correlation; the
separately authorized namespace reference contract and integrity tests; current
guidance and the copied Framework documentation package. Existing Sidecar HTTP,
configuration, admission and shutdown code/tests were traced where they establish
the composed requirements. All 28 copied Framework documentation files were
independently compared with the matching local checkout.

The full review preceded implementation fixes. One concrete portability finding
was reproduced, fixed and re-reviewed. This context cannot certify its own fix;
a fresh Step 5 cycle is required.

## Findings

No remaining actionable findings after the fix and re-review below.

## Findings Resolved in This Context

### [P2] Pin replay-source manifest identity independently of checkout line endings

- Location: suite `tests/test_replay.py:90`–`94`,
  `fixtures/reference-namespaced/namespace-review.json:5`,
  `fixtures/reference-namespaced/manifest.json` namespace metadata, and
  `.gitattributes`.
- Scenario: the preserved original worktree manifest has CRLF bytes, while its
  Git blob and the new `eol=lf` checkout rule use LF. The ledger originally pinned
  the worktree-only SHA-256
  `8e6108586a1af5a228e3d09d7a946a6c604fbf6ff8373165496b6f92527307b8`.
  A fresh LF checkout produces
  `adc448c928c4260d5fcea785be391597fcc14feb26f29254eba3ddb0c083a22b`.
- Impact: the namespace integrity test passes in the development worktree but
  fails on a clean checkout, preventing the maintained suite from verifying its
  accepted replay contract.
- Evidence: a temporary copy of both packs with only the original manifest
  replaced by its exact Git HEAD bytes reproduces the failure. The added
  `test_namespace_review_accepts_source_manifest_from_a_clean_lf_checkout`
  failed before the fix at the source-manifest digest comparison.
- Fix: the explicit `sourceManifestLfSha256` field pins the repository LF
  representation. Only that review-metadata digest normalizes CRLF to LF;
  original worktree bytes are never rewritten. Case files, embedded replies,
  source facts, expected business results, 316 identity changes and strict
  runtime matching remain unchanged. Updated the ledger checksum in the active
  manifest, explained the convention in its README/governing plan, and recorded
  current versus Step 4 metadata hashes in verification.
- Post-fix proof: all replay tests pass, including the new clean-checkout test
  and the original preserved-worktree integrity test. Full suite pytest also
  passes. No runtime adapter, legacy fallback or reference-response change was
  introduced.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Invocation-only HTTP validation; structural validation stays active | `RuntimeConfigurationService`, `EffectiveExecutionConfiguration`, annotated executor validation path | Independently executed `HttpConfigurationIntegrationTest`/recovery/management tests in full Sidecar verify; HTTP-invalid publication/restoration and zero-network assertions | Implemented |
| Equipment and SDK clients use executor declarations with correct bodies | Nine generated leaves use whole input; SDK owned callbacks omit body; targets are execution configuration | Full suite generator/receiver tests; inspected isolated smoke and all eight equipment replay executions; SDK callback body assertion and receipts | Implemented |
| Public direct/standard-tool behavior, fidelity, auth/expiry, bounds and no replay/state | `HttpSkillExecutor`, application-owned pool, response mapper, Python execution identity and separate callback validator | Independently executed complete HTTP/public-API suite; 268 then 269 suite tests; inspected exact-attempt SDK observations | Implemented |
| Captured publication, restart, durable ownership and single-budget shutdown | Public reload/handoff, generation correlation, coordinator and HTTP lifecycle | Independently executed configuration/shutdown tests; Local completion/cutoff and retained-admission receipts | Implemented |
| Representative HTTPS/Caddy boundaries | Standard public-only JVM truststore; Caddy-owned mTLS; no executor client identity | Inspected eight Standard and nine Hardened selected scenario results and cleanup; JVM truststore unit checks; independent Sidecar TLS test | Implemented |
| Earlier blockers reconciled; complete local checks | Current release line, disposable released-tag fixture and current client contracts | Independent Sidecar 300-test verify, Python 21/11, suite 269 and embedded Java verify; inspected all 30 SDK scenarios | Implemented |
| Active contracts/guidance are current; Framework dependencies public-only | Removed route catalogs/REST leaves; copied docs match local source; no Sidecar Java skills | Active-source scans, generator assertions, independently executed ArchUnit rules; no protected model declaration diff | Implemented |
| Honest evidence and pending publication/CI gate | Internal verification maps results, preserved failures and limits; release packaging still rejects snapshots | Receipt/bundle checksum audits, original-pack preservation, no paid execution; publication and hosted CI explicitly unrun | Implemented |
| Developer-authorized bounded namespace change | Separate active pack; original pack retained; no matcher modification | Recursive field/embedded-reply comparison proves exactly 316 identity-position changes; original business input/output/provenance and acceptance metadata retained | Implemented |

The portability fix is within the approved contract-preservation intent and changes
review metadata only. It preserves both the existing original manifest bytes and
fresh-checkout reproducibility. The separate original pack is an immutable review
source; runtime has one active reference contract and no compatibility path.

## Active Project Guardrails

- Superseded declarations/routes were removed directly. No legacy adapter,
  alternate API or migration machinery was added. The original reference pack
  remains an audit asset under the developer's explicit preservation decision.
- Application/test Framework use remains on supported `ai.loomspan.api` types.
  ArchUnit checks both production and test classes, internal/autoconfigure
  boundaries and absence of Sidecar `@SkillMethod` methods.
- Identity comes from verified login, with allowlisted exact roles and bounded
  source expiry. Callback verification separately enforces signature, issuer,
  audience, purpose/type and current-file trust. Only public JWKS reaches Sidecar.
- Receiving authorization, retained ownership, queue trust and Framework lifecycle
  remain independent boundaries. No token minting/refresh is performed by Sidecar.
- Standard trust uses normal JVM facilities; Hardened identity remains Caddy-owned.
  No executor certificate feature or deployment allowlist was added.
- Original reference/business/model assets remain unchanged. No existing data,
  credential or key was reset, and no Framework build, provider call, source
  commit, push or publication was performed by this review.

## Open Questions and Assumptions

None affecting the implementation or the bounded fix. Snapshot integration uses
the developer-installed Framework artifact as instructed; no source/artifact
provenance gate was introduced.

## Verification Results

Commands ran from their respective repository roots; logs are ignored test outputs.

- PASS — Sidecar `./mvnw.cmd -B -ntp verify` (captured in
  `target/pr9-3-review-1-verify.log`): 62 classes, 300 tests, zero failures,
  errors or skipped tests. Surefire totals independently counted from XML.
- PASS — Sidecar `python -m unittest discover -s scripts -p 'test_*.py' -v`:
  21 tests.
- PASS — Sidecar `python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v`:
  11 tests.
- PASS — suite `.venv/Scripts/python.exe -m pytest -q`: 268 tests before the
  portability fix; final post-fix run passes 269 tests in 56.07 seconds, including
  its additional regression (`.runtime/pr9-3-review-1-pytest-final.log`).
- EXPECTED FAIL — suite `.venv/Scripts/python.exe -m pytest tests/test_replay.py::test_namespace_review_accepts_source_manifest_from_a_clean_lf_checkout -q`:
  source-manifest CRLF/LF mismatch before the fix.
- PASS — suite `.venv/Scripts/python.exe -m pytest tests/test_replay.py -q`:
  15 tests after the fix.
- PASS — suite `mvn -B -ntp -f apps/java/pom.xml verify`: two tests and executable
  packaging; log `.runtime/pr9-3-review-1-java.log`.
- PASS — `git -c core.safecrlf=false diff --check` in both repositories.
- PASS — source scans for removed contract dependencies; Framework
  internal/autoconfigure matches are only the ArchUnit prohibition literals.
- PASS — independent receipt audit: all selected scenario results and selected
  cleanups pass for `pr9-3-local-2-4716d69d0f` (13),
  `pr9-3-standard-3-4716d69d0f` (eight), and
  `pr9-3-hardened-4716d69d0f` (nine). Inspected their mapped request/effect,
  role/trust/lifetime and transport assertions beyond status labels.
- PASS — inspected complete grouped reports and every bundle checksum at isolated
  `evidence/pr9-3-complete-4716d69d0f`: eight equipment `REPLAY_PASS` executions
  (240 passing checks, zero paid calls), 224 TypeSafe `PROTOCOL_PASS` executions
  (3,242 passing checks), both runtime-restored. Metadata-only review fix does
  not change any recorded case or executed response.
- PASS — preservation audit: all original pack content equals Git HEAD apart
  from the pre-existing original manifest checkout line endings; its raw
  preserved SHA-256 remains unchanged. Accepted manifest metadata, all four
  cases/60 steps' business inputs, expected outputs and provenance are retained.
  The structural test additionally verifies every embedded reply difference.
- PASS — inspected source synchronization before review fixes: all 91 changed/new
  suite source files byte-match the isolated workspace and removed files are
  absent. The four metadata/test/README changes from this review are described
  above; historical runtime receipts and isolated sources are not overwritten.
- NOT RUN — repeat full Local/Standard/Hardened/grouped Docker executions in this
  review. Current complete receipts/checksums, independent full test runs, source
  comparison and the runtime-neutral nature of the fix provide sufficient evidence.
- NOT RUN — released-artifact final build/hosted CI: Framework publication and
  the released dependency pin remain external release gates.

## Residual Risks and Optional Developer Checks

The earlier single diagnostic trace wire-read failure remains preserved as a
nonreproduced observation. Focused and complete reruns passed without automatic
retry or a protocol workaround. Bounded deterministic tests establish integration
and accepted-result fidelity, not new model judgment or an exactly-once guarantee.

Optional configured external nonproduction deployment observations remain optional;
none was contacted. Release sequence remains Framework beta.9 publication to
Central, Sidecar released pin and repeated final build/tests/final commit, then
hosted CI verification before Sidecar beta.4 release. Never overwrite releases.

## Disposition

`fixes-applied`. One P2 clean-checkout defect was fixed. A fresh independent Step 5
review must evaluate the entire current change, including this metadata/test fix.
