---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: 77aa70c05a3e6ec7a5fc66895be767f0f7b115ea
ticket: ai/thoughts/tickets/2026-10-18-simplify-deployment-and-composed-test-ownership.md
tags: [deployment, verification, sdk, coverage-ownership]
---

# Deployment verification and composed coverage research

## Research Question

What do the current deployment verifiers prove, where does focused component
coverage overlap them, and what executable SDK-lane coverage exists today?
Research follows the approved Full 5-Step Pipeline. This document describes
current source and available historical evidence; it does not select an implementation.

Research checklist completed: repository state; image/production/TLS/shutdown
paths; CI/release coupling; component overlap; neighboring SDK lane and its
instructions; future-ticket boundaries; fixture/data safety; available process
counts and duration evidence.

## Summary

CI and release verification both run the quickstart image verifier and the large
production verifier after root Maven verification. The production verifier
launches Maven repeatedly for an environment-driven browser class, recreates
two five-service deployments, exercises recovery, then launches two additional
three-service shutdown deployments. Certificate lifecycle assertions are in a
separate TLS verifier that neither workflow currently invokes.

The external SDK lane already owns a small Compose harness, plain Java SDK
consumer, deterministic model/REST fixture, no-replay counters and sanitized
failure evidence. Only Local has executable scenario definitions. Its trust
fixture and health URL predate PR 8.1.4 and do not match current Sidecar source.
Standard/Hardened explicitly fail selection; no SDK Spring reference app or SDK
callback receiver exists yet. Future PRs 8.1.5–8.1.8 retain those obligations.

## Repository State

- Observation time: 2026-10-18 13:58 America/Los_Angeles.
- Sidecar: `main`, HEAD `77aa70c05a3e6ec7a5fc66895be767f0f7b115ea`.
  Initial dirty state was only the supplied untracked ticket. Research adds only
  this artifact; production/test/configuration files remain untouched.
- External suite: `C:/opendev/code/loomspan-sidecar-test-suite`, `main`, HEAD
  `f0d34fa0ae3182999528aec2979d3b8af2bc1b49`; initially clean.
- Read supplied ticket, research command, automation protocol and design lens;
  read suite `AGENTS.md`, README, project agreement, SDK lane, and relevant
  foundation/runtime sections. No provider, live CA/DNS, deployment, test run,
  operational-data mutation, release, commit or push was performed in research.

## Current Behavior and Data Flow

### Sidecar entry points and deployment topology

- `.github/workflows/ci.yml:30` runs root `verify`; lines 35–38 run the shutdown
  helper unit checks, build the image, then execute image and production scripts.
  `.github/workflows/release.yml:47` has the same image/production pair after
  release-profile verification. Neither references the neighboring checkout.
- `scripts/verify-image.py:70` inspects image user/JVM options and admin token
  generation, then starts `examples/quickstart/compose.yaml`. It checks namespace
  sharing, loopback host bindings, readiness/liveness, authenticated catalog and
  unknown-skill lookup, management setup, a busy/offline password-reset command,
  recreation without setup credential and form login. It creates a separate key
  volume and uses failure-sensitive cleanup (`:37`, `:173`). This is not solely
  an image boot probe and does not use the production recipe.
- `examples/production/compose.yaml:1` has Sidecar and Caddy, with Caddy sharing
  Sidecar's namespace. Sidecar's application listener binds `127.0.0.1:8080`;
  only Caddy's TLS and HTTP redirect listeners are published. Caddy fixed-proxies
  to loopback and removes incoming forwarded headers. The recipe defaults to
  colocated JWKS trust and database application configuration. The healthcheck
  uses application-listener HTTP locally; there is no management port.
- `examples/production/compose.supplied.yaml:1` adds a read-only supplied TLS
  directory. `Caddyfile.supplied:1` uses supplied PEM files and the same fixed
  loopback upstream/header policy. Other shipped modes remain internal, public
  ACME and Cloudflare DNS. Operator lifecycle guidance is in
  `examples/production/README.md:39–117`; it is deployment support, not test code.
- `src/main/resources/application.yml:58` exposes only health, with anonymous
  status and authorization-gated details/components. Readiness includes execution
  trust. `ManagementEndpointIntegrationTest.java:54` asserts one listener,
  anonymous status without components/details/paths/kids, and readiness refusal
  during close. Dockerfile's own healthcheck also uses port 8080 (`Dockerfile:17`).

### Large production verifier

`scripts/verify-production.py` imports general subprocess/HTTP helpers from
`verify-image.py:37`. It adds host, fixture ingress and SMTP to the shipped
production services (`:245–267`), resulting in five services per source/destination
deployment. The host and fixture ingress share Sidecar's namespace. It uses a
temporary public JWKS export and disposable named data/Caddy volumes.

Its scenario order and existing assertion families are:

| Source anchors | Current assertions and effects |
| --- | --- |
| `:274–330` | Rendered topology/user/env/mounts/grace period; fresh UID-owned database; no published application port; application readiness/liveness; TLS rather than cleartext ingress; JWT catalog positive/missing JWT; execution JWT rejected by management; management locked without setup. |
| `:332–384` | Setup/recreation; Chromium login and Secure cookie; captured invitations; editor/viewer role boundaries; CSRF and management/execution authentication separation. |
| `:385–400` | Publish three quickstart skills, deterministic model and REST execution, durable snapshot correlation, authored URL variable preserved; private draft/concurrent browser handoff/stale grant. |
| `:402–438` | Host-header-injected recovery request still yields configured HTTPS link; mail outage does not prevent login/execution; account/configuration survive recreation without setup token. |
| `:440–600` | Export authored bundle excluding accounts/history; independent second deployment; URL-binding and malformed-import failures preserve state; draft-only load, credential replacement, publication/local provenance; callback reaches destination-bound host; rollback and restart selection; browser history/import inspection. |
| `:605–693` | Stop and copy complete database companion set; corrupt selected pointer; assert startup/dispatch refusal; copy diagnostics; replace disposable volume; restore prior selection/history/accounts and execute restored REST/model configuration. |
| `:695–720` | Busy/offline manual password reset; launch separate shutdown subprocess for completion and cutoff. |

`browser():190–199` starts another Maven process each time and supplies origin,
test credentials and optional setup/recovery/editor/history flags in its environment.
The three tests in `ProductionComposeBrowserIntegrationTest.java:23,59,120`
skip without corresponding flags. Root Maven therefore does not provide this
class's composed evidence, but executes separate focused management browser tests.

`scripts/verify-shutdown.py:29` creates completion/cutoff projects from quickstart,
copies the stopped temporary production database read-only into new volumes,
mounts `scripts/fixtures/shutdown-host.py`, then sends real SIGTERM. Assertions
cover survival until completion/budget, callback detail/finish effects, normal
exit/no OOM and bounded cutoff. It imports `verify-image.py:14`; its only normal
active caller is production `verify_shutdown():202`. `scripts/test_verify_shutdown.py`
tests environment forwarding and cleanup after startup/logging failures, and
imports both verifiers. Shutdown source logs raw fixture logs in its finally block.

### Certificate test boundary

`scripts/verify-production-tls.py:94–168` uses a stub HTTP backend and Caddy's
internal CA, asserting private trust, wrong-CA rejection, forwarded-header
normalization, root-key permissions, leaf/CA persistence across recreation, then
deleting the disposable leaf to assert automatic reissuance. `:176–199` generates
replacement supplied material and force-reloads Caddy until a different leaf is
served. Those persistence/reissue/replacement assertions are certificate lifecycle.

The same script also contains non-lifecycle transport/configuration assertions:
production/supplied Compose shape (`:94–108`), redirect/header normalization and
untrusted-CA rejection (`:127–159`), direct Sidecar HTTP and supplied Spring TLS
login reachability (`:201–265`), and public/DNS Caddy adaptation without issuance
(`:268–292`). Its cleanup ignores subprocess return codes and has no timeout
(`:294–302`). No active certificate lifecycle tests were found in external-suite
tests/scripts/SDK paths; business warranty "certificate" fields are unrelated.

### External SDK lane

All following paths are relative to `C:/opendev/code/loomspan-sidecar-test-suite`.

- `scripts/run_sdk.py:25` selects Local `foundation-smoke` and `blocking-client`;
  Standard/Hardened have empty lists. Empty selection fails before builds (`:588`).
- `build_checkout():477` runs Sidecar root `clean verify`, SDK reactor
  `clean install`, driver `clean package`, then runtime builds the image. Root
  Maven has no suite invocation today, so no recursive verification exists.
  Root browser tests are part of every Local suite build as currently written.
- `sdk/compose.yaml:1` has two normal services, Sidecar and Python fixture, plus
  a profile-selected ephemeral Java driver. Internal Docker network, no host
  published ports, read-only mounts and tmpfs database avoid operational data.
- `write_config():193–310` emits temporary RSA PEM and JWT, model/REST skill and
  application YAML. The current YAML uses retired
  `auth.jwt.issuer-uri/public-key-location`, lacks current file/colocated trust,
  and JWT has `typ=JWT` without `kid` or `token_use`. Current Sidecar fixture
  `src/test/java/ai/loomspan/sidecar/support/JwtTestTokens.java:54–58` emits
  `typ=loomspan-execution+jwt`, `token_use=loomspan_execution`; Sidecar now requires
  configured JWKS. This is a source-confirmed mismatch, not a fresh runtime result.
- `readiness():360–385` polls `http://localhost:9091/actuator/health/readiness`,
  also inconsistent with Sidecar's current single application listener.
- `BlockingClientDriver.java:26–54` uses real SDK core discovery/submission/poll,
  exact opaque model result, REST 503 failure, and dropped submission response
  producing `AcceptanceUnknownException`. `scripts/sdk_lane/fixture.py:107`
  forwards one fixed submission to one fixed Sidecar destination, consumes the
  response and drops the connection; synchronized independent counters prove one
  incoming attempt, one forward and one effect. This is plain Java SDK evidence,
  not SDK Spring issuance or independent callback authentication evidence.
- Runner `command():78` whitelists child environment, captures raw output and
  exposes fixed failure categories only. `run_smoke():525` always attempts
  cleanup, preserves primary failure, and makes cleanup failure fail the report.
  Images remain under unique tags. Reports include source/build hashes but no
  elapsed stage durations (`stage():98` only records status).

## Existing Tests and Fixtures

The following table records observed overlaps, not a decision to remove them:

| Broad assertion family | Current focused executable source |
| --- | --- |
| Admin token/reset/locked store | `management/ManagementAdminCommandTest.java:32,44` covers canonical generation, invalid arguments, eligibility, offline reset and busy store. `ManagementLockedExecutionIntegrationTest` covers locked management with execution. |
| Management authentication, CSRF, roles/session expiry | `management/ManagementHttpIntegrationTest.java:158,221`; `ManagementPersonalTokenHttpIntegrationTest`; `ManagementEditingHttpIntegrationTest.java:169,225,242,270`. |
| Invitation/recovery configured origin and SMTP failures | `ManagementMailHttpIntegrationTest.java:60` sends spoofed forwarded host; `ManagementMailIntegrationTest.java:34,52,95` covers STARTTLS, bootstrap and invitation/recovery failure/retry. |
| Private browser draft, handoff, stale writers | `ManagementEditorBrowserIntegrationTest.java:64,113,133`; uses real Chromium and local Spring server. |
| Browser import/history | `ManagementImportBrowserIntegrationTest.java:47`, `ManagementHistoryBrowserIntegrationTest.java:51`; management HTTP tests also check shells/roles. |
| Export/import/rollback and local binding | `configuration/ManagementConfigurationHttpIntegrationTest.java:70,129,159`; `RuntimeConfigurationIntegrationTest.java:460`; `rest/RestGenerationIntegrationTest.java:49`; `CredentialPortabilityIntegrationTest.java:29,57`. |
| Persistence/backup/restore/refuse unusable state | `configuration/RuntimeConfigurationRecoveryIntegrationTest.java:100` copies full database set and restores selected configuration/history/admin; `:149,162` covers selected startup failures; `RestRouteRestartIntegrationTest.java:32`, `RuntimeConfigurationIntegrationTest.java:204` cover durable selected runtime. Exact Docker-volume ownership/restore shell operations are additional deployment-specific mechanics. |
| REST/model execution and generation correlation | `execution/AuthenticatedExecutionApiIntegrationTest.java:261,364,395,456`; `ExecutionConfigurationCorrelationIntegrationTest.java:35,143,191`; `rest/RestRouteStartupIntegrationTest.java:25`. |
| Current trust/health, JWT profile, ownership/exact roles | `security/CurrentFileJwtTrustTest`, `JwtSecurityConfigurationTest`, `JwtStartupIntegrationTest.java:24`; `configuration/ConfigurationModeIntegrationTest.java:179`; `execution/ExactRoleExecutionIntegrationTest.java:63`, `AuthenticatedExecutionApiIntegrationTest.java:332`. |
| Queue and callback expiry | `execution/ExecutionCoordinatorTest.java:496`; `rest/GenericRestSkillHandlerTest.java:370` asserts strict expiry and no outbound request. JWT trust withdrawal/rotation is distinct from TLS certificate lifecycle. |
| Framework close/client lifetime | `rest/RestHandlerLifecycleIntegrationTest.java:36,119` covers normal close and Framework cutoff, web/async variants through public API; `execution/ExecutionShutdownIntegrationTest.java:24,35,53` covers close coordination. No framework tests are used as Sidecar evidence. |
| Public Framework boundary | `architecture/SupportedLoomspanApiArchitectureTest.java:17` forbids internal/autoconfigure imports in app and tests; `:28` forbids Java skills. |
| Temporary TLS per-target positive and client rejection | `rest/RestTransportTlsIntegrationTest.java:59` uses generated CA/server/two clients, selected Spring SSL bundle, successful mTLS, wrong client CA and absent identity rejection. It does not prove real SDK/Caddy topology, direct inbound TLS or every hostname/expired-material variant. |

Paths in the table are beneath `src/test/java/ai/loomspan/sidecar/` unless stated.
No component tests were run during research. Presence and assertion inspection
establish coverage intent; fresh execution remains an implementation gate.

## Dependencies and Operational Constraints

`release/ReleasePreparationTest.java:17–38` reads verifier/workflow source text and
asserts both legacy verifier names and helper implementation strings. This couples
workflow/script consolidation to a focused test update. The Python shutdown tests
also depend on old verifier modules and host fixture. `smtp-capture.py` is only
used by the broad production script; quickstart host/skills are shipped examples,
not automatically unused solely because verifiers disappear.

Suite `AGENTS.md` and agreement require preserving operational `.runtime`, local
credentials, business apps/skills and reference packs (`fixtures/reference` and
TypeSafe references). SDK lane runs separately from those five-service business
deployments. Sidecar normal build must stay independent. No reset is required or
authorized. Standard Spring Boot/Caddy deployment support and current pinned
Framework beta.8 remain unchanged by the ticket. No framework internal API is
needed to research or modify test ownership.

Most direct verifier command prose now lives in
`ai/throughts/readme-maintainer-material.md`; its opening expressly marks it parked
historical material, not revalidated instructions. Current README and operator
docs do not provide a central current maintainer verification guide. Historical
plans/reviews contain many references and must remain distinguishable from active
instructions. CI, release workflow, SDK lane docs and active 8.1.5–8.1.8 planning
are the operative cross-references.

## Historical Context and Existing Cost Evidence

- HEAD contains PR 8.1.4; earlier commits are 8.1.3 signing, 8.1.2 blocking
  client, 8.1.1 lane foundation. This explains the suite's stale trust fixture.
- `ai/thoughts/reviews/2026-10-18-pr-8-1-4-sidecar-current-file-jwt-trust-and-ownership-review-2.md:70–75`
  records passing image/production runs and explicitly did not rerun the TLS
  lifecycle suite. These are historical results, not this ticket's fresh passes.
- Available `target/pr814-review2-production.log` has eight nested Maven browser
  invocations, reported Maven durations totaling **109.750 seconds**, twelve
  Compose `up` commands (including SMTP restart), and SIGTERM completion/cutoff
  observations of **2.48/4.53 seconds**. These metrics were freshly extracted
  without printing logs/payloads. They do not establish total wall time.
- Source gives five Compose projects across the CI pair: one quickstart image
  project, source and destination production projects, plus completion/cutoff
  shutdown projects. Normal services are 3 + 5 + 5 + 3 + 3 = **19 service starts
  before recreations**, excluding admin/helper containers. The production path
  alone contributes four projects, 16 initial service starts and eight nested
  Maven browser invocations. CI first runs the independent component/browser suite.
- Suite historical Local report
  `evidence/sdk/20261018T172812Z-d8a63bfd928f/report.json` passes foundation/blocking
  scenarios against dirty Sidecar HEAD `a1ab2d80d9d575614039b01ffe13341bf9c2ea11`.
  It is older than current trust changes and cannot establish current compatibility.
  Standard/Hardened reports explicitly fail with empty scenario lists.
- Before/after full wall-time observations and fresh current source suite results
  do not exist in this research. No benchmark harness or broad baseline rerun was
  added; existing measurable historical durations/counts are separated from gaps.

## Future-ticket Ownership and Open Questions

- PR 8.1.5 explicitly owns restoration of old plain-Java scenarios to JWKS plus a
  real Spring/form-login SDK reference app, Local issuance/roles/ownership,
  colocated restart/trust correction and queued expiry.
- PR 8.1.6 owns the SDK callback security chain (currently absent), independent
  receiver trust, method/business checks and complete Local callbacks/expiry.
- PR 8.1.7 owns full Standard/Hardened invocation and callback recipes, provided
  signing, per-run temporary certificates, transport rejection, fixed destination
  and network no-bypass in both directions. Empty recipe selectors are not evidence.
- PR 8.1.8 owns trusted second app, JWT rotation/recovery and no-callback coverage
  in all three recipes. None of these tickets authorizes TLS lifecycle testing.

The material planning question is which currently nonredundant transport checks
from `verify-production-tls.py` and the broad production path require executable
suite replacement in this ticket before deletion, versus future SDK-specific
scenarios that never existed and must remain explicitly pending. Current source
does not equate plain-Java invocation, Python quickstart callbacks or a stub Caddy
transport check with the future SDK Spring callback chain. No runtime evidence for
those future contracts exists. This is a sequencing/design question for planning;
research itself is complete without a developer answer.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-18-simplify-deployment-and-composed-test-ownership.md
SUMMARY: Mapped all broad verifier families to their current focused overlaps and documented SDK-lane gaps, coupling, safety constraints and historical cost evidence. No tests or deployments were run.
DECISIONS:
  - Historical reports are identified as historical; future recipe selectors and stale Local fixtures are not current executable replacement evidence.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Create implementation and testing plans that resolve minimum replacement evidence and preserve future-ticket obligations.
