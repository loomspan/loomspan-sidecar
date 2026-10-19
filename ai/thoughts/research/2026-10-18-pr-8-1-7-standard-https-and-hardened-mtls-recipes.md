---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: e2aad75c40f4e6b524853a170c60d3c672a0a11c
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md
tags: [sdk, tls, mtls, caddy, integration]
---

# Standard HTTPS and Hardened mTLS Research

## Research Question

What TLS, signing, callback, deployment and evidence facilities currently exist,
and which ticket obligations have no executable composed evidence?

Research checklist completed: Sidecar outbound/listener configuration; SDK outbound
and signing; file trust in both ConfigurationModes; reference-app authentication and
callback authorization; external runner/report/cleanup; certificate and Caddy
fixtures; prior evidence and operational boundaries.

## Summary

Both Java callers already accept Spring Boot SSL bundles. Sidecar REST targets
select a named bundle; SDK starter selects one through `loomspan-sdk.ssl-bundle`.
Both clients disable automatic retries and redirects. Sidecar's TLS integration
test proves per-target bundle/client identity selection; SDK starter testing
proves trusted HTTPS and hostname rejection. This is existing component evidence,
not completion of the remote recipes.

The external SDK lane has a real form-login reference app and authenticated
callbacks with method and record authorization. Its `standard` and `hardened`
selectors are explicitly empty and fail before builds. The Local recipe and
deployment baseline do not establish bidirectional Standard HTTPS or Hardened mTLS.
Current provided signing is implemented/documented in the SDK, but the composed
reference app uses colocated signing. No Hardened two-namespace composition or
group-scoped mutual TLS fixture exists in the SDK lane.

## Repository State

- Inspection time: 2026-10-18 18:29 PDT; Full 5-Step Pipeline, research stage.
- Sidecar: branch `main`, commit above; clean before this research artifact.
- External suite: `C:/opendev/code/loomspan-sidecar-test-suite`, clean,
  commit `61007447077a6907569876a289aa2601a00afa0e`.
- Root POM pins Framework `1.0.0-beta.8`, Boot `4.1.0`, Java 21 and Sidecar
  `1.0.0-beta.4-SNAPSHOT` (`pom.xml:10`). SDK is in `sdks/java`.
- Local Framework checkout is ahead of beta.8 (`v1.0.0-beta.8-12-g52f11dd`);
  matching-tag public REST documentation was inspected with `git show
  v1.0.0-beta.8:agent-skills/loomspan-docs/references/java-api/rest-skills.md`.
- No production code changed; no integration, build, provider or live service
  actions were run during research.

## Current Behavior and Data Flow

### Sidecar invocation and callback

`JwtSecurityConfiguration.java:27` builds current filesystem trust independently
of managed application configuration. `/v1/**` authenticates bearer JWTs in a
stateless chain; exact authority strings come from `roles` without a prefix
(`:41`, `:62`). `SidecarTrustProperties.java:27` accepts only explicit `colocated`
or `file` trust, and file mode resolves `trust.file.jwks` to a filesystem path.

`RestRouteLoader.java:35` includes `ssl-bundle` in target fields. At `:140` it
looks up a named Boot bundle and rejects unavailable/unknown bundles. Base URLs
accept HTTP and HTTPS (`:225`); configuration does not impose paired transport
recipes. `RestTargetClients.java:54` creates a separate pooled Apache client per
target with target timeouts. A selected bundle supplies the SSLContext (`:60`),
with the normal hostname-verifying socket factory. Redirects, retries and cookies
are disabled (`:74`). Generation-owned clients close through the existing
generation lifecycle. This layer is Sidecar's authorized public REST-handler SPI
implementation, not Framework-owned callback transport.

Standard Boot server SSL configuration controls the listener. Existing production
documentation describes `SERVER_SSL_CERTIFICATE` and private-key settings
(`examples/production/README.md:121`); no Sidecar TLS-specific server factory exists.

### SDK and provided signing

`sdks/java/core/.../SidecarClient.java:58` uses the normal Apache TLS strategy,
optionally applying a caller SSLContext. It disables retries and redirects (`:66`).
`sdks/java/spring-boot-starter/.../SidecarClientAutoConfiguration.java:18` obtains
the configured Boot bundle and supplies its SSLContext. The documented mapping
and sample are in `sdks/java/README.md:94` and `:112`.

The SDK documents provided RSA signing from PKCS12/JKS/PEM, public JWKS export,
startup-only private-key loading, and password references at
`sdks/java/README.md:198`. `SigningProperties.java:40` binds provided locations,
alias and JWKS export. Export parents and key material are operator responsibilities;
export cannot overlap the private key/certificate/password files. Current reference
app `application.yaml:9` selects colocated signing and `/keys` instead.

### External reference application

Paths below with `suite/` mean the external suite checkout.

`suite/sdk/reference-app/.../SecurityConfiguration.java:14` supplies Alice/Bob
with `ROLE_ORDER_EDITOR` and a negative user with literal `ORDER_EDITOR`.
Its ordinary chain uses real form login and CSRF. SDK callback authentication
is an independent security chain; method security is enabled on the reference app.

`ExecutionController.java:28` exposes CSRF and fixed SDK operations. Submission
allows only listed fixture skills (`:43`), calls the real SDK with credentials
issued from Spring's authenticated user, and polls with fresh credentials. For
callback scenarios it captures the opaque issued credential to a fixed local
fixture before submission. The two capture clients currently hard-code
`http://127.0.0.1:8090/capture` (`:35`, `:49`).

`CallbackController.java:26` counts arrivals before security. Its fixed owned
callback uses `hasRole('ORDER_EDITOR')` and then checks issuer `loomspan-app` and
subject Alice (`:40`). A fixed local comparison endpoint checks equality with
the captured token; only the equality boolean escapes. Arrival/body/effect counters
distinguish authentication, method denial and record denial. Authenticated count
reads remain outside the callback prefix. The app currently binds loopback 8081.

### Existing compositions and runner

`suite/scripts/run_sdk.py:27` lists Local scenarios but leaves Standard/Hardened
empty. `main` records failed evidence with `no-scenarios-for-recipe` for either.
Its build path performs root `clean verify`, SDK `clean install`, plain driver and
Boot consumer packaging, then the actual checkout Docker image build. It validates
Framework beta.8 from the packaged artifact. There is no cached-build bypass.

`suite/scripts/sdk_lane/local.py:60` derives a unique Local composition from the
shipped quickstart, retaining loopback and colocated trust. The app and deterministic
fixture share Sidecar's namespace. The key volume is uniquely named and prepared
with explicit ownership; cleanup removes only that project and external volume.
Configuration creates fixed REST targets and caller-passthrough callback routes
(`:120`, `:162`). `local_probe.py:43` performs real form login; callback and expiry
probes start at `:184` and `:231`.

The baseline `suite/sdk/compose.yaml:1` has internal Docker networking and no host
ports. Its driver is a separate Java 21 consumer. These namespace patterns differ
from Hardened's required app/Caddy pair and Sidecar/Caddy pair.

Reports atomically initialize/finalize under a unique `evidence/sdk` directory and
record source dirty state, artifact SHA-256s, Framework version, scenario results,
stage timings, readiness, counters and cleanup. Fixed diagnostic categories replace
raw logs/exceptions. `child_environment` allowlists build/runtime environment names
and excludes provider variables; private JWT keys stay out of reports.

## Key Components

- `src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java:54` — existing outbound TLS and no-retry behavior.
- `src/main/java/ai/loomspan/sidecar/rest/RestRouteLoader.java:140` — named Boot SSL bundle validation.
- `src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java:27` — process-file JWT trust.
- `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java:18` — SDK TLS bundle mapping.
- `suite/scripts/run_sdk.py:27` — recipe selection, build, report and orchestration.
- `suite/scripts/sdk_lane/local.py:60` — existing namespace/configuration/cleanup precedent.
- `suite/scripts/sdk_lane/transport.py:13` — temporary certificate generation precedent.
- `examples/production/Caddyfile.supplied:1` — current supplied-server-certificate ingress only.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Standard invocation | SDK Boot bundle binding exists; no real reference-app-to-Sidecar HTTPS recipe. |
| Standard callback | Sidecar per-target bundle exists; no app HTTPS callback recipe. |
| Hardened | Existing supplied ingress Caddy does not implement bidirectional mTLS or separate app login/callback listeners. |
| Provided/file signing | SDK implementation exists; current Local composition uses colocated signing/trust. |
| ConfigurationMode | Existing parameterized component evidence keeps filesystem JWKS authoritative in file/database modes. |
| No replay | Both Java clients disable automatic retries; Local lost-response counters prove one submission/forward/effect. Remote Caddy path has no composed response-loss proof. |
| Operator docs | Generic direct HTTPS and supplied Caddy ingress exist; complete Standard/Hardened references do not. |

## Existing Tests and Fixtures

- `RestTransportTlsIntegrationTest.java:61` generates temporary RSA CA/server/client
  certificates using Bouncy Castle; a client-auth-required HTTPS fixture accepts the
  correct selected bundle and rejects wrong-CA/absent client identities. It does
  not currently isolate trust-only HTTPS, wrong server hostname or expired server.
- `GenericRestSkillHandlerTest.java:160` proves connect timeout without retry;
  `:302` covers redirects/retries/read bounds; `:370` proves expired passthrough
  credentials cause no send.
- `sdks/java/spring-boot-starter/.../SidecarClientAutoConfigurationTest.java:147`
  proves Boot-configured trust and normal hostname rejection with temporary keytool
  material. `sdks/java/core/.../SidecarClientTlsTest.java:26` independently exercises
  core TLS and hostname verification.
- `ConfigurationModeIntegrationTest.java:178` parameterizes file/database trust
  withdrawal, valid recovery and exact configured JWKS path. This already tests
  process JWKS sourcing without duplicating whole remote recipes.
- `suite/scripts/sdk_lane/transport.py:13` creates valid/expired server material per
  run. The baseline checks wrong CA/hostname/expired server and supplied Caddy
  normalization/fixed upstream/backend isolation. Its certs have serverAuth EKU;
  there are no client-auth or alternate client/server peers for Hardened.
- `suite/tests/test_sdk_runner.py:602` requires empty selectors to fail; `:1101`
  rejects interpreting unavailable services as TLS rejection; `:1112`/`:1162`
  validate certificate contents and actual TLS. Callback fixture/oracle coverage
  begins at `:1211`; report-redaction and cleanup failure tests also exist.

## Dependencies and Operational Constraints

- Root guidance restricts Java Framework dependencies to `ai.loomspan.api`, with
  `RestSkillHandler` the authorized SPI. Matching beta.8 docs confirm exactly one
  handler, generation-captured invocation context and one handler call. These
  Framework claims are source context, not Sidecar integration evidence.
- Docker and Maven are available. `java` is absent from PATH in this shell, but
  `JAVA_HOME=c:/hamdev/jbrsdk21`; execution must account for that environment.
  Docker daemon availability was not probed during research.
- SDK runner prerequisites include Java 21, Maven, Docker and suite Python with
  cryptography, httpx and PyYAML. Current Caddy baseline pins `caddy:2.10.2`.
- Suite AGENTS/README/project agreement prohibit provider calls, operational-data
  resets and alteration of maintained reference packs. The SDK lane is isolated
  from beta.9 business integrations and uses actual Sidecar beta.8 artifacts.
- User explicitly permits composed tests in the external suite when they do not
  specifically prove Sidecar component abilities. Ticket already assigns most
  composition work there and operator documentation to Sidecar.
- Ticket prohibits CA services, committed certificates, transport enums/runtime
  recipe allowlists, network-proof controllers and TLS lifecycle testing. It
  requires independent transport configuration, accepted-TLS JWT rejection,
  no plaintext cross-host traffic and no submission/callback replay.
- No development-data reset follows from current repository behavior or scope.

## Historical Context

`suite/docs/sdk-lane.md` records complete Local report
`20261018T232143Z-36a99814fb53`: 12 scenarios, 538.779 seconds, root 261 tests,
SDK core 25/starter 30, focused harness 79/full safe Python 189. These are prior
recorded results; research did not rerun or independently certify them. The earlier
failed callback attempt remains recorded and explains the input-required leaf that
forces beta.8's planner through a real callback tool action.

The deployment simplification ownership inventory (`docs/verification.md`) assigns
existing transport/TERM composition to this lane without claiming future recipes.
Canonical recipes (`ai/thoughts/phases/2026-10-15-sdk-v1-deployment-recipes.md`)
define the two protected Caddy namespaces and separate browser ingress; both
directions remain independently configurable and other compositions stay permitted.

## Open Questions

- Exact remote composition, ports, certificate identities and Caddy client/server
  verification syntax are planning/implementation work; no existing fixture settles
  them. A Caddy client certificate verifies chain/EKU rather than automatically
  applying server-style DNS identity checks, so the chosen wrong-peer negative must
  accurately identify the peer rule actually enforced.
- The fixture's hard-coded local credential comparison/capture and default app
  configuration currently assume the Local topology. How remote compositions retain
  those fixed local fixture boundaries is unresolved by current code.
- Ticket requires representative invalid server certificates in each Standard
  direction and invalid peers in both Hardened directions. Precise negative inventory
  and evidence oracles remain to be specified; connection refusal alone does not
  establish certificate rejection.
- Existing Java component TLS support may already suffice. Whether focused tests
  expose a real production gap remains unverified; no new TLS contract is implied
  by this research.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-18-pr-8-1-7-standard-https-and-hardened-mtls-recipes.md
SUMMARY: Traced existing TLS, signing, JWT file trust, reference callbacks, external runner and evidence boundaries. Both callers already support Boot SSL bundles; the two remote recipe selectors have no scenarios. No production code or operational data changed.
DECISIONS:
  - Existing component tests and prior Local reports are described separately from unimplemented composed recipe evidence.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run implementation planning and test planning for the Full 5-Step Pipeline.
