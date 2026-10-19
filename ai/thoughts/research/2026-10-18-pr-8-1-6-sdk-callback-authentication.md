---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: 40fa6e1ed292a90cc0d9d4cd607e20dc38cbee79
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-6-sdk-callback-authentication.md
tags: [sdk, callbacks, authentication, spring-security, jwks, local-recipe]
---

# SDK callback authentication research

## Research Question

What signing, trust, authentication, outbound callback and composed Local test
behavior exists, and which ticket obligations have no current executable evidence?
This is pipeline Step 1, selected profile `full`, approved by the developer with
“proceed.” Research describes current sources; it does not choose implementation.

## Summary

The independent SDK reactor currently issues execution JWTs and creates blocking
clients. Its starter has no callback filter chain, callback properties, independent
JWT validator, or overlap-file trust owner. Its signing SPI already supplies the
current public key and kid without exposing private material.

Sidecar already forwards the captured JWT unchanged and checks strict expiry just
before every passthrough send. Its authoritative JWKS file reloads atomically,
withdraws trust on detected failure, and recovers. Those Sidecar classes belong to
the Sidecar application and cannot be imported by the independently bounded SDK.

The external suite has a working form-login Boot reference app and a Local group
derived from the shipped quickstart composition. It covers issuance, exact roles,
ownership, restart, Sidecar trust recovery and queue expiry. Its only Local REST
skill currently calls an unauthenticated Python effect endpoint; there is no
authenticated reference-app callback or callback-expiry composed scenario.

## Repository State

Inspected 2026-10-18 around 15:55 America/Los_Angeles.

| Repository | Branch / commit | Initial working tree |
| --- | --- | --- |
| `C:/opendev/code/loomspan-sidecar` | `main`, `40fa6e1ed292a90cc0d9d4cd607e20dc38cbee79` | Clean |
| `C:/opendev/code/loomspan-sidecar-test-suite` | `main`, `88e5fcef0451f4ba7407656393610acbd78594b1` | Clean |

The sole research mutation is this document. No builds, tests, network services,
containers, paid providers, commits or data resets were run. The framework checkout
describes itself as `v1.0.0-beta.8-12-g52f11dd`; Sidecar remains pinned to beta.8.
No framework implementation contract is required to establish these SDK findings.

Research checklist: [x] SDK signing/issuance/build; [x] existing Sidecar trust and
callback sending; [x] reference app security; [x] Local composition/probes/evidence;
[x] tests and documentation; [x] operational scope and unresolved planning details.

## Current Behavior and Data Flow

### SDK startup and issuance

`sdks/java/spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`
lists only `SidecarClientAutoConfiguration` and `ExecutionTokenAutoConfiguration`.
The latter binds signing/issuance and registers `SigningKeySource`,
`ExecutionTokenIssuer` and `SpringExecutionTokenIssuer`
(`sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfiguration.java:11`).
Its class documentation explicitly says it does not install an application chain.

`SigningKeySource.publicKey()` and `kid()` expose the app's startup-owned public
identity (`sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeySource.java:6`).
`ExecutionTokenIssuer.issue` constructs RS256 JWTs with exact issuer/subject,
audiences `loomspan-sidecar`, issuer and additional configured audiences, numeric
`iat`/`exp`, unique `jti`, execution purpose and allowlisted exact authority names
(`sdks/java/core/src/main/java/ai/loomspan/sdk/ExecutionTokenIssuer.java:25`).
`IssuanceProperties` defaults issuer to `loomspan-app`, lifetime to 15m and roles
to empty; it has no callback audience property
(`sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/IssuanceProperties.java:9`).

### Sidecar validation and sending

`CurrentFileJwtTrust` owns a volatile immutable snapshot, reads the entire file,
compares content rather than metadata, validates all keys and replaces the complete
snapshot. Initial load happens synchronously; a daemon repeats with one-second
fixed delay. Unknown kids trigger a globally rate-limited reload (one per second).
Missing/unreadable/malformed/empty candidates publish empty trust; prior keys are
never kept as fallback (`src/main/java/ai/loomspan/sidecar/security/CurrentFileJwtTrust.java:42`,
`:53`, `:68`, `:86`). RSA minimum size, unique nonblank kids, public-only members,
optional `sig`/`RS256`, and integer modulus/exponent checks apply; `x5c` and unknown
members do not confer trust. Closing stops the scheduler (`:110`).

`ExecutionJwtDecoder.decode` parses raw claim shapes before registered claim
conversion, verifies RS256/type/kid/purpose, nonblank issuer and subject, roles,
audience and numeric timestamps, then verifies signature and 60s-skew timestamps.
It deliberately has no expected issuer comparison
(`src/main/java/ai/loomspan/sidecar/security/ExecutionJwtDecoder.java:27`).
`JwtSecurityConfiguration` sets the roles claim and empty authority prefix and
registers process-owned file trust independently of ConfigurationMode
(`src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:28`, `:48`).
Its application filter-chain policy and error writer are Sidecar behavior, not
existing SDK callback behavior (`:62`).

`GenericRestSkillHandler.handle` takes the captured `JwtAuthenticationToken` from
the execution security context for `CALLER_PASSTHROUGH`, adds its same token to the
outbound Authorization header, and checks `clock.instant().isBefore(expiration)`
immediately before `exchange`. Missing/expired expiration throws exactly
`execution credential expired`, with no exchange
(`src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java:57`).
It uses only the supported `ai.loomspan.api.RestSkillHandler` SPI.

### Reference app and Local recipe

Paths in this subsection are relative to `C:/opendev/code/loomspan-sidecar-test-suite`.

`sdk/reference-app/src/main/java/ai/loomspan/sdk/example/SecurityConfiguration.java:13`
defines Alice/Bob with `ROLE_ORDER_EDITOR` and a negative user with literal
`ORDER_EDITOR`. One ordinary catch-all authenticated form-login chain owns all
routes (`:19`); method security is not enabled. There is no callback controller.

`sdk/reference-app/src/main/java/ai/loomspan/sdk/example/ExecutionController.java:27`
builds a second issuer with a two-second credential for `localEffect`.
`/csrf`, `/skills`, `/capture`, `/submit/{skill}` and `/executions/{id}` are
authenticated app routes. Submission accepts only three fixed skills; identity
comes from Spring and the input is an empty object (`:46`). All general exceptions
are converted to HTTP 500 by an advice method (`:60`), a relevant adjacent error
handling boundary when tracing application authorization.

`scripts/sdk_lane/local.py:42` derives composition from the supplied Sidecar
checkout's quickstart YAML and records its hash. App and Python fixtures share
Sidecar's loopback namespace; the network is internal and no host ports are exposed.
One unique app-writable/Sidecar-readable key volume is prepared for UID 10001;
Sidecar waits for startup JWKS readiness (`:202`). `configure` creates three
temporary skills, one worker and a bounded queue (`:102`). All three currently
require `ROLE_ORDER_EDITOR`; `localEffect` POSTs `/effect` on the Python fixture
with `auth.mode=none` (`:124`). No Local target forwards a caller credential.

`scripts/sdk_lane/local_probe.py:43` performs real form login and retains sessions
and CSRF in memory. `issuance` proves protected app routes, CSRF, model completion,
literal-role denial without dispatch, other-owner 404 and fresh-owner polling
(`:86`). `queue` holds the one worker, queues the two-second REST skill, waits
past expiry, releases and observes exact pre-start expiry plus zero effects
(`:150`). This does not prove expiry after Framework admission but before callback.

`scripts/sdk_lane/fixture.py:262` defines the Local fixed capture/model/effect
server. Credential capture remains in memory, and probing is restricted to the
fixed Sidecar catalog path (`LocalHandler.do_GET`). Model blocking uses bounded
events; effects have a separate synchronized count. No current fixture route
executes a reference-app callback. `scripts/run_sdk.py:779` runs the Local group
after baseline consumer/deployment/TERM scenarios and always attempts cleanup.

## Key Components

| Area | Current behavior and evidence |
| --- | --- |
| Independent SDK build | `sdks/java/pom.xml:47` bans transitive Framework/Sidecar artifacts; core additionally bans Boot runtime in `sdks/java/core/pom.xml:47`. |
| Starter dependencies | `sdks/java/spring-boot-starter/pom.xml:28` currently has security-core, core, optional Boot autoconfigure/webmvc and Boot tests; no resource-server/security-config dependency. Boot version is 4.1.0. |
| Public signing source | `SigningKeySource.java:6` supplies current RSA public key/kid; no callback key-hosting endpoint exists. |
| Operator trust | `src/main/java/ai/loomspan/sidecar/security/SidecarTrustProperties.java:31` resolves colocated/file paths, with colocated loopback restrictions. Database application configuration does not store trust. |
| SDK documentation | `sdks/java/README.md:178` onward documents signing and issuance, but its export section still describes callback validation as separate future work; no callback wiring documentation exists. |
| Verification ownership | `docs/verification.md:60` assigns current Local issuance/trust/queue coverage to the external lane and expressly limits its claims. |

## Existing Tests and Fixtures

These are source-inspected tests, not fresh run results.

| Location | Existing coverage / limit |
| --- | --- |
| `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/ExecutionTokenAutoConfigurationTest.java:23` | Defaults, explicit signing, bad/mixed settings, missing mode, safe secret diagnostics and provided PEM export; no servlet callback authentication. |
| `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SpringExecutionTokenIssuerTest.java:22` | Authenticated identity snapshot and exact roles; null/anonymous/unauthenticated rejection. |
| `src/test/java/ai/loomspan/sidecar/security/CurrentFileJwtTrustTest.java:13` | Whole-file failure/recovery, malformed candidate variants, unreadable I/O, unknown-kid rate limit/concurrency, atomic snapshots and content changes with unchanged metadata. These do not prove callback receiver behavior. |
| `src/test/java/ai/loomspan/sidecar/security/JwtSecurityConfigurationTest.java:20` | Raw JWT invalid variants, fixed skew, untrusted same-kid key, algorithms, token URLs ignored and literal roles; Sidecar intentionally allows different signed issuers. |
| `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java:177` | Both `file` and `database` use configured process JWKS; empty-file withdrawal, restoration and source path are asserted. |
| `src/test/java/ai/loomspan/sidecar/rest/GenericRestSkillHandlerTest.java:370` | Strict callback expiry before send using controlled clock and real HTTP fixture; no equivalent composed reference-app scenario. |
| External `tests/test_sdk_runner.py:183`, `:205`, `:248`, `:326`, `:382` | Local report/scenario wiring, composition boundary, in-memory capture, bounded queue barrier/counter, sanitized failures and cleanup. |
| `sdks/java/tests/test_build_boundary.py:113` | Disposable real Maven reactors exercise direct/transitive forbidden dependencies for both SDK modules. |

No starter test currently covers callback bearer multiplicity/query credentials,
session/cookie isolation, direct 401 challenge/error handling, request context
cleanup, prefix override/method policy, method authorization or overlap trust.

## Dependencies and Operational Constraints

The SDK and reference app target Java 21 and Boot 4.1.0; Nimbus 10.10 is already
in SDK core. Root Sidecar continues to use its published Framework beta.8 artifact.
Matching local framework tag documentation is the authority if framework details
are needed; the installed old documentation skill is not used.

Documented verification commands are `mvn -B -ntp -f sdks/java/pom.xml clean install`,
`python sdks/java/tests/test_build_boundary.py`, root `./mvnw.cmd -B -ntp clean verify`,
and, from the external suite, `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`,
`ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`,
`ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`, and
`.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`.
The composed runner rebuilds actual sources, requires Docker and local images,
and records safe evidence in a unique directory; it is not a provider-backed run.
Its root component build runs once. No test-suite invocation belongs inside
Sidecar CI/build and no maintained reference pack or operational data is disposable.

The ticket explicitly spans both repositories. Test-suite AGENTS, README,
project agreement, foundation and runtime documentation were read. They forbid
unapproved paid runs/commits and preserve the separate business runtime/reference
packs. This work requires no existing development-data reset.

## Historical Context

Current Sidecar HEAD is PR 8.1.5 (Local colocated reference app), preceded by the
deployment simplification, PR 8.1.4 current-file trust/forwarding/lifetime, PR 8.1.3
signing and PR 8.1.2 blocking client. These are implemented baselines, not proof of
the new callback boundary. The reconciled identity, signing/trust and acceptance
phase references describe planned callback behavior. The external SDK lane docs
link historical ten-scenario Local evidence and expressly exclude callback
authorization; those recorded passes were not rerun during research.

## Open Questions

No developer answer is needed to finish research. Planning owns the currently
unimplemented callback property names and optional audience configuration,
concrete Spring responses for authenticated disallowed methods/firewall rejection,
prefix validation, overlap-file merge/conflict semantics and lifecycle wiring,
and the composed fixture that proves expiry between admission and callback.
The canonical contract requires a dedicated highest-precedence stateless chain,
own issuer/key validation, exact authorities and fail-closed configured overlap;
there is no existing contrary SDK contract to preserve.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-18-pr-8-1-6-sdk-callback-authentication.md
SUMMARY: Traced current SDK issuance, Sidecar JWT trust and passthrough expiry, plus the external Local reference app and harness. Recorded existing tests and callback acceptance gaps with source anchors. No tests or services were run.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run implementation and testing planning in one fresh context using this research and the ticket.
