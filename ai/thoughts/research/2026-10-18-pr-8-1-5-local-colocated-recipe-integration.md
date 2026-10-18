---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: 939564ac3a79e083b7cd141a8d795a33b7a2a600
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-5-local-colocated-recipe-integration.md
tags: [sdk, local, colocated, spring-security, integration]
---

# Local colocated recipe integration Research

## Research Question

What current SDK, Sidecar and external-suite contracts support the ticket's form-login Boot consumer, exact authorities, ownership, colocated restart, trust correction and queued expiry evidence?

Research checklist completed: repository/instructions; SDK signing and issuance; shipped Local configuration; Sidecar authentication/ownership/queue/trust; external runner and fixtures; existing tests and prior evidence; operational boundaries. This is Step 1 in the user-approved Full 5-Step Pipeline; it contains source observations, not an implementation design.

## Summary

The SDK already contains the needed startup signer, Spring authenticated-user issuance adapter and blocking client. Sidecar already implements current-file JWKS reload, literal role matching, issuer/subject ownership and strict queued-token expiry. The remaining ticket scope is composed evidence through a new clean Boot reference application in the external suite, which currently has only a plain-Java consumer and deployment baselines.

The shipped colocated configuration is in `examples/quickstart/compose.yaml` (and production Compose), not a separate application YAML. Its key topology uses one writable app mount, one read-only Sidecar mount and shared loopback namespace. The current suite's baseline Compose uses an internal bridge network and file trust, so its existing passing evidence does not establish the new colocated recipe.

## Repository State

Observed 2026-10-18 at 15:08 PDT (-07:00):

| Repository | Branch / commit | Initial status |
| --- | --- | --- |
| Sidecar, `C:/opendev/code/loomspan-sidecar` | `main`, `939564ac3a79e083b7cd141a8d795a33b7a2a600` | Clean |
| External suite, `C:/opendev/code/loomspan-sidecar-test-suite` | `main`, `1d32501b16dde6736981e44fe82fa1abd28f2389` | Clean |
| Framework, `C:/opendev/code/loomspan-framework` | Working checkout describes `v1.0.0-beta.8-12-g52f11dd`; source reference is tag `v1.0.0-beta.8`, resolving to `052e8b47edaf95c0d2013d9e6351284a98207d6b` | Read-only source lookup; current branch is newer than pinned dependency |

The SDK is owned inside Sidecar at `sdks/java`; there is no neighboring `loomspan-sdk` checkout. Research changes only this artifact. No tests, builds, containers, provider requests or external live actions ran.

## Current Behavior and Data Flow

### Spring authentication to execution issuance

`sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfiguration.java:11` registers a startup `SigningKeySource`, core `ExecutionTokenIssuer` and `SpringExecutionTokenIssuer`. It installs no application security chain. `SpringExecutionTokenIssuer.java:12` reads the current SecurityContext; null, unauthenticated and anonymous identities fail. It captures `Authentication.getName()` and exact authorities into `AuthenticatedIdentity`, then invokes the core issuer.

`sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionTokenIssuer.java:26` issues RS256 credentials with fixed execution purpose/type, issuer, subject, audiences, issuance/expiry timestamps, unique JTI and the intersection of user authorities with the configured exact allowlist. `IssuanceSettings` defaults to issuer `loomspan-app`, lifetime 15m and no allowed roles; `sdks/java/README.md:260` documents the profile and duration behavior. `ROLE_ORDER_EDITOR` and `ORDER_EDITOR` remain distinct.

`sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java:16` creates the application-owned blocking `SidecarClient` and closes it with the Spring context. Its base URL and bounds come from `loomspan-sdk.*`. The starter POM targets Boot 4.1.0 and makes webmvc optional; its direct security dependency is security-core. There is no servlet reference application in `sdk/` today.

`sdks/java/core/src/main/java/ai/loomspan/sdk/CredentialSnapshot.java:4` deliberately has no public token getter. Applications pass credential objects to `SidecarClient` explicitly. A fresh credential does not mutate a previously captured one. Existing SDK exceptions and snapshot types support status-preserving HTTP failure handling and bounded terminal waits (`sdks/java/README.md:39`). The credential transport value is not a supported reference-app response contract.

### Colocated signing and receiver trust

`sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeys.java:27` resolves colocated export to `<key-directory>/jwks.json`, requires an existing directory, generates a fresh RSA-2048 pair at startup and retains private material only in memory. `SigningKeys.java:91` derives the public thumbprint kid; `SigningKeys.java:150` publishes public-only JWKS via a sibling temporary file and atomic replacement, with no ordinary-move fallback. New app startup means a new key and kid.

`src/main/java/ai/loomspan/sidecar/security/SidecarTrustProperties.java:28` requires explicit `colocated` or `file` mode and rejects settings from the other mode. Colocated resolves the same `jwks.json` filename and requires explicit loopback binding, including a separately configured management listener. Filesystem paths are used; network key discovery is absent.

`src/main/java/ai/loomspan/sidecar/security/CurrentFileJwtTrust.java:45` performs initial load and schedules reload every second. `forKid` at line 54 can trigger another load for an unknown kid, globally limited to one per second. `reload` at line 68 compares file bytes, validates a complete candidate, and publishes an immutable current snapshot. Missing/unreadable/malformed/empty content clears accepted keys; no last-good fallback remains. Restoring valid content restores trust. A valid new file replaces the old key set, rather than retaining old keys.

`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:30` registers `executionTrust` health; `src/main/resources/application.yml:80` includes it in readiness. Readiness is 503 while trust is unusable, independently of liveness. Health reads memory, not the file. Anonymous health is status only. The trust implementation logs safe metadata (path, kids, state, reason), never token or raw JWKS content.

`src/main/java/ai/loomspan/sidecar/security/ExecutionJwtDecoder.java:27` validates exact JOSE type/algorithm, execution purpose, trusted key, nonblank signed issuer/subject, role shape, audience and timestamps. The receive timestamp validator has fixed **60-second skew** (`ExecutionJwtDecoder.java:23`). Issuer is an ownership identity claim, not a separately configured expected issuer. Thus new-key acceptance and old-key rejection depend on reload adoption, not a synchronous restart guarantee.

### Roles, ownership and strict queue expiry

`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:44` maps `roles` with an empty authority prefix and configures empty `GrantedAuthorityDefaults`. YAML `rbac_roles` therefore matches literal incoming authorities. The matching beta.8 Framework source at `agent-skills/loomspan-docs/references/skill-authoring/authorization.md:50` documents its normal prefix behavior and supported customization; Sidecar supplies the empty prefix explicitly.

`src/main/java/ai/loomspan/sidecar/api/ExecutionController.java:41` validates skill/input before admitting captured authentication. Polling at line 54 calls coordinator `find` with `ExecutionOwner.from(authentication)`. `src/main/java/ai/loomspan/sidecar/security/ExecutionOwner.java:7` keys ownership by both signed issuer and subject. A different owner sees **404 Execution not found**, not 403. Same owner with a newly issued token can poll without replacing the execution's captured credential.

`src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:165` begins handoff by releasing queue reservation and checks `now < original exp`; an expired credential throws exactly `execution credential expired before start`. It re-decodes the original JWT using current trust and repeats strict expiry immediately before the public Framework handoff. These checks do not apply receive skew. A single busy worker leaves additional submissions QUEUED when `loomspan-sidecar.executions.max-concurrent=1` and queue capacity permits. Runtime failures become retained FAILED records (`ExecutionCoordinator.java:216`), while public handoff is never called for expired queued work. Fresh polling authentication cannot renew that queued credential.

### Shipped Local configuration and external composition

`examples/quickstart/compose.yaml:33` runs Sidecar as UID/GID 10001, explicitly sets loopback address and colocated trust with `/sidecar/keys`, mounts the pre-created external key volume read-only and shares the Sidecar namespace with app/ingress. App mounts the volume writable (`compose.yaml:11`). Its example app is Python, not the SDK Boot consumer. `examples/quickstart/README.md:10` documents preparing/chown/chmod of the external key volume before startup. The initially empty database does not publish its example skills automatically (`README.md:27`). `examples/production/compose.yaml:23` also sets colocated trust with a read-only operator-provided directory. Neither sample supplies a standalone colocated application YAML file.

`docs/operations.md:119` also documents native same-user developer processes sharing an absolute directory and loopback. Container loopback backends require a namespace-sharing ingress because published port NAT cannot directly reach them (`docs/operations.md:128`). Readiness must not gate app startup: Sidecar waits for the app's initial public export. App and proxy depend on Sidecar started, not healthy.

External paths below are relative to `C:/opendev/code/loomspan-sidecar-test-suite`:

- `scripts/run_sdk.py:26`: `local` selects five scenarios: foundation smoke, blocking client, shutdown completion, shutdown cutoff and deployment transport baseline. Standard and hardened selectors are empty and fail explicitly.
- `scripts/run_sdk.py:222`: current baseline generates its own temporary public JWKS and execution token, file-mode config, deterministic model and REST skills. This is intentionally separate from Spring issuance.
- `sdk/compose.yaml:29`: baseline Sidecar and Python fixtures run on an internal bridge network, with read-only config, tmpfs database and no published host ports; the plain-Java driver is an optional service.
- `scripts/run_sdk.py:678`: one actual Sidecar `clean verify`, separate SDK `clean install`, artifact identity collection and plain-Java driver package per runner invocation. Framework is not rebuilt. No cached-build flag exists.
- `scripts/sdk_lane/fixture.py:12`: deterministic fixture exposes model/REST counters and explicit entered/release events for existing shutdown choreography. It captures no business payloads and disables access logs. The bounded lost-response proxy forwards only one fixed path/destination.
- `scripts/run_sdk.py:726`: runtime stages execute retained baselines and finalize scenario outcomes; cleanup is in `finally`. Any cleanup failure fails the run without overwriting a primary failure.
- `scripts/run_sdk.py:761`: each invocation writes a unique durable atomic JSON report before preflight and after failures/interruption. Reports include recipe, source revision/dirty state, packaged artifact hashes, SDK coordinates, consumer hash, stage/total durations, booleans/counters and fixed diagnostic categories. Child environments are allowlisted (`run_sdk.py:63`); raw process output/errors do not reach evidence (`run_sdk.py:89`).

## Existing Tests and Fixtures

| Area | Located evidence and limits |
| --- | --- |
| Both application modes use process JWKS | `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java:179`, parameterized file/database wiring with withdrawal/restoration. No composed matrix is needed to repeat it. |
| Automatic trust and readiness recovery | `src/test/java/ai/loomspan/sidecar/security/JwtStartupIntegrationTest.java:24`, file/colocated trust modes; 4s bounded HTTP poll, missing/empty/corrected file and status-only health. |
| Trust candidate validation, byte changes and reload throttle | `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java:12`, malformed/unreadable/private-member candidates, full withdrawal, identical restoration and atomic snapshots. |
| Exact role behavior through real Framework | `src/test/java/ai/loomspan/sidecar/execution/ExactRoleExecutionIntegrationTest.java:63`, ROLE_ORDER_EDITOR succeeds; unprefixed, double-prefixed and empty roles fail 403. |
| Queue expiry/current trust | `src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java:496`, captured-token expiry/withdrawal and no handoff; line 537 expiry during revalidation. These are component tests, not composed SDK evidence. |
| SDK signing/issuance | `sdks/java/core/src/test/java/ai/loomspan/sdk/SigningKeysTest.java`, `ExecutionTokenIssuerTest.java`; Spring `ExecutionTokenAutoConfigurationTest.java` and `SpringExecutionTokenIssuerTest.java`. Existing focused tests are not a form-login servlet consumer. |
| External runner orchestration and secrecy | `tests/test_sdk_runner.py:149` fake-lane build/results; line 239 failure/cleanup/secrets; line 332 command environment/output safety; line 434 fixture mounts; line 581 current trust contract; line 621 failed scenario outcome; line 643 latch ordering; line 762 actual local TLS rejection. |
| Existing composed evidence | `evidence/sdk/20261018T211932Z-43ccfbc20a42/report.json` records five passes and cleanup, source `77aa70c05a3e6ec7a5fc66895be767f0f7b115ea` with dirty inputs and hashes. It is historical baseline evidence, not current ticket completion. |

No acceptance test was executed in research. Source presence is not a fresh pass claim.

## Dependencies and Operational Constraints

The ticket and design lens require direct replacement of obsolete development contracts, public Framework APIs only, proportional tests/documentation, no compatibility machinery, no operational-data deletion and no provider calls. No development-data reset is indicated by current contracts; new recipe state is temporary.

External `AGENTS.md`, `README.md`, `docs/project-agreement.md`, and relevant foundation/runtime/SDK-lane guides were consulted. The SDK lane is separate from the beta.9 business applications, their runtime configuration, Keycloak, credentials, operational SQLite and maintained reference packs. The SDK work agreement authorizes provider-free integration, packaging, documentation and independent security review; no paid run, commit, push or release is implied.

Prerequisites recorded in `docs/verification.md:9` and external `docs/sdk-lane.md:8`: Java 21 (`JAVA_HOME`), Maven/wrapper, Docker Compose, suite Python environment (httpx/PyYAML/cryptography), installed/resolvable Framework beta.8, and Chromium needed by the root Sidecar component build. Public dependency downloads/image pulls may occur. The runner always rebuilds Sidecar components and SDK before runtime evidence. The SDK Boot consumer adds its own packaging/dependency boundary evidence; starter consumption alone does not supply the web/security application dependencies.

Existing maintainer commands from the suite root are `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`, `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`, `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`, and `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`. Research did not execute them.

## Historical Context

The deployment simplification ticket already restored current-JWKS plain-Java completed, failed and lost-response scenarios and moved deployment/TERM baselines to this runner. `docs/verification.md:86` explicitly excludes form-login issuance, callback security and full future transport recipes from those passes. The acceptance matrix and ticket assign callbacks to PR 8.1.6, TLS recipes to 8.1.7 and a second app to 8.1.8. No prior report substitutes for this ticket's composed evidence.

## Open Questions

Implementation planning still owns concrete fixture design: how it consumes the shipped Compose colocated sample without recreating its trust settings, where form-login requests/CSRF handling live, how bounded restart/reload observation retains an old credential inside supported SDK use, and how deterministic model entry/release plus counters prove QUEUED expiry and absence of dispatch. Current sources provide these contracts, but no existing reference app resolves those design details. They are ordinary planning choices rather than a missing developer decision.
