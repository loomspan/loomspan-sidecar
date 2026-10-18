---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: 05518e790e6e5db8ff0a3dc670beaeabfd0f3048
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md
tags: [sdk, signing, jwt, issuance, identity]
---

# SDK signing and token issuance Research

## Research Question

What code, configuration, data flow and executable evidence currently exist for
the ticket's RSA signing modes, public JWKS export, authenticated identity capture
and immutable execution credentials?

Research checklist completed: repository/build boundaries; credential transport;
Boot entry points/configuration; signing/identity implementations; canonical
contracts; focused tests/fixtures; operational and verification prerequisites.

## Summary

The independent Java SDK currently implements blocking HTTP client operations and
the immutable `CredentialSnapshot` used by them. Its starter wires only that
client and connection properties. There is no SDK signer, key loader, JWKS export,
token issuer, authenticated identity model or Spring identity capture adapter.
Those names occur in planned reference contracts rather than executable SDK code.

Core has HTTP/JSON dependencies and JUnit; the starter has optional Boot wiring
and MVC plus test support. Neither SDK module currently declares a JWT library or
Spring Security dependency. The root Sidecar has separate existing JWT receiver
configuration, whose current contracts differ from the planned trust contract;
changing that receiver belongs to PR 8.1.4, outside this ticket.

The development policy and design lens apply: superseded contracts are replaced
directly, without compatibility shims, legacy adapters or parallel legacy APIs.
No current SDK signing state or persisted signing schema was found to migrate.

## Repository State

- Observed 2026-10-18 11:30 America/Los_Angeles.
- Branch `main`, commit `05518e790e6e5db8ff0a3dc670beaeabfd0f3048`.
- `git status --short` was empty before research. This research document is the
  only modification made by this step; no implementation or fixture was edited.
- Pipeline mode, selected profile `full` (Full 5-Step Pipeline), approved by the
  developer. No tests, live-service calls or external writes were performed.

## Current Behavior and Data Flow

### Credential entry and transport

`sdks/java/core/src/main/java/ai/loomspan/sdk/CredentialSnapshot.java:4` is a final
class holding a private final token string. `of` rejects null, blank, whitespace,
control and non-ASCII transport values (`:7`); it does not parse or verify JWTs.
Only package-private methods build the authorization header and redact token
echoes (`:12`); `toString` is redacted (`:14`). It has no token getter, issuance,
identity information, refresh behavior or serialization annotations.

Every public client operation receives a snapshot explicitly:
`SidecarClient.java:94` (`listSkills`), `:104` (`getSkill`), `:109` (`submit`),
`:120` (`getExecution`) and `:131` (`await`). `submit` also accepts a JSON object;
the credential is a separate argument. The common request path validates the
credential and sets its unchanged authorization value (`:151`, `:160`, `:232`).
It does not query Spring security or infer identity from input. HTTP problem
details recursively redact credential echoes (`:201`, `:236`).

### Spring entry points and startup

`sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java:13`
registers after Boot SSL configuration. It enables `SidecarClientProperties` and
creates an application-owned `SidecarClient` bean unless one exists (`:16`).
Its bean uses the same core builder and optionally a Boot SSL bundle (`:18`).
There are no identity, issuer or signing beans. The auto-configuration imports
resource contains only `ai.loomspan.sdk.spring.SidecarClientAutoConfiguration`.

`SidecarClientProperties.java:8` binds `loomspan-sdk` with base URL, positive
timeouts, response-size bound and optional SSL-bundle name. It has defaults of
2 seconds, 10 seconds and 8 MiB (`:10`), and no signing/issuance properties.
No application YAML or other packaged SDK default configuration exists in the
starter resources. Therefore no packaged signing mode exists today.

### Signing and identity absence

The complete SDK Java source inventory contains client/DTO/exception classes,
`CredentialSnapshot`, the client property class and client auto-configuration.
There is no implementation that generates or loads RSA material, resolves signing
password references, exports JWKS, computes thumbprint kids or signs tokens.
There is no SDK API accepting authenticated custom identity and no use of
`SecurityContextHolder` or authenticated Spring principals in SDK code.

### Existing Sidecar receiver is a separate boundary

`src/main/java/ai/loomspan/sidecar/security/SidecarJwtProperties.java:8` binds the
existing `loomspan-sidecar.auth.jwt` receiver configuration: issuer URI, audience,
JWK-set URI or public-key resource, roles claim, role prefix and skew. Its role
prefix currently defaults to `ROLE_` (`:16`).

`JwtSecurityConfiguration.java:36` builds a decoder from a configured public key,
JWK-set URI or issuer discovery; `:50` adds timestamp, issuer, audience and required
claim validation. `:73` applies the configured authority prefix. This is receiver
behavior, not SDK issuance evidence. The ticket and canonical references place
replacement trust/role semantics in PR 8.1.4. Current root configuration does not
establish this ticket's token-profile or exact-authority acceptance.

## Key Components

- `sdks/java/pom.xml:11` — independent two-module reactor; Java 21; inherited
  Surefire 3.5.4 and compiler 3.14.1; dependency boundary at `:42`.
- `sdks/java/core/pom.xml:13` — HttpClient 5.6.1, Jackson 3.1.4 and JUnit 6.0.3;
  transitive Boot runtime ban at `:42`.
- `sdks/java/spring-boot-starter/pom.xml:13` — Boot 4.1.0 dependency management;
  core dependency, optional auto-configuration/MVC and Boot test support.
- `sdks/java/core/src/main/java/ai/loomspan/sdk/CredentialSnapshot.java:4` —
  immutable credential result/transport value already available.
- `sdks/java/core/src/main/java/ai/loomspan/sdk/SidecarClient.java:151` — shared
  request path consumes the snapshot without issuer knowledge.
- `sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/SidecarClientAutoConfiguration.java:13`
  — current Boot wiring and lifecycle ownership.
- `sdks/java/README.md:1` — current SDK documentation, including independent
  build, explicit credential use, transport configuration and credential privacy.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Explicit signing mode/startup | Absent from SDK properties and wiring; canonical signing reference `:139` requires explicit mode with no packaged default. |
| Colocated key lifecycle | Planned only: signing reference `:7` describes RAM-only private key/public atomic export; `:14` states sole writer and restart constraints. |
| Provided material/passwords | Planned only: signing reference `:24` covers RSA minimum/password references and `:35` startup-only private loading. No existing SDK secret-reference API. |
| Identity/issuance | Planned identity reference `:7` specifies one issuance implementation and two trusted identity sources; current client only consumes supplied credentials. |
| JWT profile | Identity reference `:41` gives RS256/thumbprint/typ/purpose/issuer/subject/audiences/timestamps/jti/roles; `:64` gives default audiences and `:71` lifetime/warning. |
| Exact roles | Identity reference `:78` records exact authority intersection and Spring `hasRole`/`hasAuthority` examples. No SDK allowlist currently exists. |
| Export trust use | Signing reference `:50` and `:166` describe planned authoritative file trust in both application configuration modes. Current Sidecar decoder has the older sources described above. |
| Documentation | SDK README describes an already-issued token, and only client connection settings; signing and issuance usage is absent. |

## Existing Tests and Fixtures

All observations below come from source inspection; this research did not run the
tests and does not claim they currently pass.

- `sdks/java/core/src/test/java/ai/loomspan/sdk/SidecarClientTest.java:13` covers
  discovery, submission and polling using a supplied test credential.
- `SidecarClientEdgeTest.java:100` covers local input/credential failures and
  authorization transport; `:145` covers HTTP/protocol/unknown acceptance;
  `:201` covers wait deadlines, `:229` interruption and `:247` resource lifecycle.
- `SidecarClientEdgeTest.java:270` checks token secrecy in `toString`, Jackson
  serialization/refusal, error stacks, problem details and default error output.
  This is evidence for existing credential/error behavior, not new signer logs.
- `SidecarClientTlsTest.java:23` covers TLS certificate/hostname verification
  using temporary material. This is transport coverage, separate from JWT keys.
- `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/SidecarClientAutoConfigurationTest.java:39`
  covers auto-configuration discovery and connection defaults; `:56` covers
  configured values/client close; `:127` invalid configuration; `:138` user client
  override. `:147` generates temporary RSA PKCS12 material with JDK `keytool`
  (`keytool.exe` fallback handling at `:149`) to test a standard Boot SSL bundle.
- `sdks/java/tests/test_build_boundary.py:37` copies SDK sources to disposable
  reactors and runs Maven `verify`; `:94` onward checks clean control, direct and
  transitive Boot bans in core, and Framework/Sidecar bans in both modules. It
  runs multiple complete Maven invocations and has a 300-second per-build timeout.
- `src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java:17`
  checks application and test classes against `ai.loomspan.internal..` and
  `ai.loomspan.autoconfigure..`; the SDK has the stronger artifact dependency ban.

No SDK tests currently cover RSA signing, private-key loading, certificate expiry
warnings, atomic JWKS export, startup mode rejection, issuance claims/roles,
Spring authenticated capture or custom identity. There are no signing fixtures.
The acceptance matrix `:29` and `:30` assigns focused issuance/signing evidence
here; composed form-login/restart/callback/provided deployment evidence is assigned
to later tickets (8.1.5, 8.1.6 and 8.1.7), rather than this research.

## Dependencies and Operational Constraints

- The independent SDK reactor does not inherit root dependency management and is
  not a root Maven module. SDK builds need their own `-f sdks/java/pom.xml`.
- Core's runtime must remain usable without Boot, Sidecar or Framework. The
  inherited Maven Enforcer rule bans all `ai.loomspan:*` except SDK artifacts,
  including transitive dependencies. No Framework API lookup was necessary for
  this research: this ticket's existing SDK boundary has no Framework dependency.
- Root `pom.xml:17` remains pinned to Framework `1.0.0-beta.8`.
  `.github/workflows/ci.yml:15` also pins beta 8 and its jobs currently verify the
  root Sidecar build/image; it does not invoke the independent SDK reactor.
- `mvn -version` was run read-only: Maven 3.9.6, Java 21.0.2, Windows 11.
  `JAVA_HOME` is `c:\hamdev\jbrsdk21`. `java` is not on this shell's PATH, but
  Maven resolves Java successfully from JAVA_HOME. Python 3.13 is available.
- Local documented commands are `mvn -B -ntp -f sdks/java/pom.xml clean install`
  and `python sdks/java/tests/test_build_boundary.py`. They were not run here.
- Filesystem behavior is a real local boundary: reference contracts require an
  existing writable directory, public-only temporary write and atomic replacement;
  no creation, permission mutation/checking policy or writer coordination exists.
- Tokens/private material must not enter logs or diagnostics. Existing transport
  docs warn against explicitly enabling third-party HTTP wire/header logging.

## Historical Context

`git log -4 --oneline` shows PR 8.1.1 (`a1ab2d8`) introduced the independent SDK
build and suite lane; PR 8.1.2 (`05518e7`, current HEAD) introduced the blocking
client. These explain the already-available immutable credential and test patterns.
Canonical phase references are explicitly marked planned behavior; their signing,
issuance and callback descriptions are not implementation evidence.

## Open Questions

The ticket intentionally permits final property naming during planning. Existing
SDK code supplies no prior naming/compatibility contract for signing or issuance.
The following details are not specified by an executable existing implementation
or settled in the ticket/reference text and remain ordinary planning work:

- Exact password-reference representation and resolution (environment and/or
  secret-file reference), and how literal password settings are rejected.
- Supported PEM encodings/encryption combinations and certificate association;
  selection of keystore type/alias and export-path requirements.
- The concrete threshold for a near-expiry signing certificate warning.
- How the public Spring operation-entry convenience is exposed beside the
  explicit-credential blocking client, without thread-context wrappers.
- What deterministic local evidence demonstrates atomic replacement failures and
  unwritable-directory errors on Windows without introducing permission checks.

No developer decision is required to finish research. These are not proposed
solutions or changes to scope.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-18-pr-8-1-3-sdk-signing-and-token-issuance.md
SUMMARY: Traced SDK credential transport, Boot wiring, build boundaries and existing tests against the planned signing/issuance contracts. No signing or issuance implementation exists; no tests or live external actions were run.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Continue to implementation planning and testing planning using this research and the ticket.
