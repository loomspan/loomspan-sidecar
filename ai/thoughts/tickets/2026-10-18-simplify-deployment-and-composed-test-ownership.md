# Simplify deployment verification and consolidate composed integration coverage

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.
No development-data reset is expected for this test-ownership change. Preserve
credentials, operational data and maintained reference fixtures in both repositories.

## Outcome

Sidecar keeps a small, useful smoke test for its shipped deployment recipe.
`loomspan-sidecar-test-suite` owns composed SDK/application/Caddy integration
scenarios. Certificate-lifecycle tests are removed rather than relocated.
Developers can verify a component change without repeatedly running overlapping
deployment, browser and recovery suites, while meaningful integration evidence
remains executable and has a clear owner.

## Requirements

### Keep component and shipped-recipe assurance in Sidecar

- Retain focused Sidecar tests for JWT validation, trust reload/readiness,
  ownership, exact roles, queue/callback expiry, management authorization,
  browser behavior, configuration persistence and Framework shutdown integration.
  Moving composed scenarios must not remove their component regression coverage.
- Retain a small deployment smoke using the actual built Sidecar image and
  shipped Compose/Caddy configuration. Prove startup/readiness, reachability
  through the intended ingress, a valid authenticated request, rejection of an
  unauthenticated execution request, and absence of external backend bypass.
  Health stays on the application listener, with anonymous status only.
- Keep the smoke bounded and disposable, with deterministic local fixtures,
  safe diagnostics, failure-sensitive cleanup and no paid providers or live
  CA/DNS services. It must not run a broad browser-authoring, import/rollback/
  backup/restore, certificate-lifecycle or shutdown scenario collection.
- Consolidate overlapping image/production smoke paths. Do not retain duplicate
  scripts or thin legacy entry points solely to preserve old verification commands.

### Consolidate composed scenarios in the external suite

- Use the existing SDK lane in `C:/opendev/code/loomspan-sidecar-test-suite` for
  real SDK-to-Sidecar invocation, Sidecar-to-application callbacks, and composed
  Caddy/TLS/mTLS behavior. Extend or reuse its scenarios instead of creating a
  second deployment harness or copying the existing large verifier wholesale.
- Preserve its three representative recipes: Local localhost HTTP, Standard
  direct HTTPS and Hardened Caddy mTLS in both directions. Keep meaningful
  authentication, peer-rejection, fixed-destination and no-bypass coverage;
  do not add a transport/configuration cross-product or runtime recipe allowlist.
- Map current broad verifier assertions to one owner: a retained Sidecar
  component test, the small deployment smoke, a suite scenario, or explicit
  removal with rationale. Remove redundant composed management/browser/recovery
  checks when existing component coverage already proves their behavior; do not
  move them merely to preserve test volume.
- Before deleting a nonredundant required integration check from Sidecar, prove
  its replacement in the suite. Document genuine pending coverage explicitly;
  do not present future SDK recipes as executable evidence.
- Coordinate with PRs 8.1.5 through 8.1.8, which already own Local, callback,
  remote-recipe and rotation integration. This ticket consolidates ownership and
  existing coverage; it does not independently require implementing every future
  scenario in those tickets. Reconcile references and dependencies so that work
  is not duplicated or accidentally waived.
- Normal Sidecar build/CI must remain independent of the neighboring checkout.
  Document separate component/smoke and composed-suite commands and evidence.
  Avoid recursive verification between the suite's Sidecar build and suite runs.

### Remove certificate-lifecycle testing

- Delete tests for certificate issuance/renewal, forced replacement/live reload,
  leaf regeneration, CA-state persistence/rollover and revocation propagation.
  Remove dedicated helpers and fixtures left unused by those deletions. Do not
  migrate these tests to the external suite.
- Keep transport integration positives and negatives using temporary per-run
  certificates, including wrong CA, hostname/peer and expired material where
  needed. Static Caddy configuration checks may remain when they validate a
  shipped recipe. Invalid-certificate rejection is transport validation, not a
  certificate-lifecycle test.
- Preserve operator deployment guidance and standard Spring Boot/Caddy TLS
  support. DevOps continues to own certificate lifecycle; removing tests must
  not introduce custom lifecycle code or remove supported deployment options.
- Update CI, scripts, documentation and active planning references to the new
  ownership and commands. Keep historical verification reports as history.

## Acceptance criteria

- [x] A single documented Sidecar deployment-smoke path proves the bounded
      shipped-recipe checks above, passes locally, and cleans up on both success
      and failure. It does not invoke broad scenario collections.
- [x] Sidecar component and focused integration regressions remain green,
      including management/browser, trust/lifetime and Framework API-boundary
      checks. No application behavior or shipped listener topology changes.
- [x] An ownership inventory accounts for existing broad verifier assertions,
      identifies duplicate coverage removed, and links nonredundant retained
      composed checks to executable suite scenarios and fresh evidence.
- [x] The suite uses its existing SDK lane for composed SDK/Caddy coverage;
      Local/Standard/Hardened ownership and any still-planned scenarios agree
      with PRs 8.1.5 through 8.1.8. No required check disappears behind a future
      ticket or an undocumented skip.
- [x] Neither repository contains active certificate-lifecycle tests or helpers
      used only by them. Temporary-certificate transport rejection coverage and
      operator TLS guidance remain intact.
- [x] Sidecar CI runs its component checks and small deployment smoke without
      requiring or invoking the suite checkout. Suite commands have no recursive
      build/verification path. Documentation makes both verification scopes clear.
- [x] Before/after commands, scenario/process counts and observed durations
      demonstrate reduced duplicate deployment work without weakening required
      coverage. No arbitrary timing threshold or new benchmark harness is needed.
- [x] Disposable verification uses no paid provider/live CA/DNS calls, modifies
      no operational data or maintained reference packs, and reports cleanup
      failures as failures. Any required development-data reset is documented.

## Context

- The developer explicitly requested this ownership split after PR 8.1.4
  verification repeatedly exercised broad production deployments during
  implementation and independent review.
- Starting points in Sidecar: `scripts/verify-image.py`,
  `scripts/verify-production.py`, `scripts/verify-production-tls.py`,
  `scripts/verify-shutdown.py`, `ProductionComposeBrowserIntegrationTest`,
  `.github/workflows/ci.yml`, `examples/production/` and related operator docs.
  These are investigation pointers, not a prescribed file-move plan.
- External suite: `docs/project-agreement.md` (SDK v1 integration direction),
  `docs/sdk-lane.md`, and its existing SDK runner. Respect that repository's
  instructions, business examples, credentials and maintained reference packs.
- Related tickets: [Local](2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md),
  [callbacks](2026-10-18-pr-8-1-6-sdk-callback-authentication.md),
  [HTTPS/mTLS](2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md),
  and [rotation/groups](2026-10-18-pr-8-1-8-trusted-group-rotation-and-outbound-only.md).
- JWT signing-key rotation, JWKS withdrawal/recovery, queued credential checks
  and callback expiry remain required. They are distinct from the removed TLS
  certificate-lifecycle tests.
- No release/version change, commit, push, provider evaluation or deployed-data
  operation is authorized by this ticket.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** The desired ownership is settled, but mapping an existing broad
  verifier to component tests and an incomplete external SDK lane requires
  material discovery and sequencing across repositories before safe deletion.
  Security terminology alone does not determine this recommendation.
- **Reassessment triggers:** If current-checkout evidence establishes a bounded,
  fully mapped change with executable replacements already present, a lighter
  profile may suffice under the shared eligibility rules.
