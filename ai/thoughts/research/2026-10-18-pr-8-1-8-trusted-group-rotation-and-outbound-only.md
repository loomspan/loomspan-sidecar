---
date: 2026-10-18
repository: loomspan-sidecar
branch: main
commit: f0ea254e0c41e09ab900f58c1773e407575df986
ticket: ai/thoughts/tickets/2026-10-18-pr-8-1-8-trusted-group-rotation-and-outbound-only.md
tags: [sdk, trusted-group, jwt, rotation, recipes]
---

# Trusted group, rotation and outbound-only Research

## Research Question

What current Sidecar/SDK contracts, external-suite fixtures and evidence support
PR 8.1.8, and what remains unproved before its implementation planning?

Research coverage: repository constraints/state; provided signing and independent
trust; composed authorization and lifecycle; each recipe's topology; harness and
evidence conventions; operator instructions. Each area was traced in current source.

## Summary

The suite already runs Local colocated signing and Standard/Hardened provided
signing against the actual Sidecar checkout. Each has one reference Spring app;
there is no composed second-app, add/switch/remove rotation or callback-free case.
Sidecar and the SDK callback validator each independently poll configured JWKS
bytes every second. Provided signing loads once at startup.

The callback receiver always includes its own startup signer's public key in its
valid trust set. Removing a key from its overlap file alone cannot revoke that
same startup key. Restarting the receiver with the replacement provided signer is
therefore material to the existing removal contract. This is documented behavior,
not a missing framework API. Sidecar current-file trust does not have an own key.

## Repository State

Read-only state captured 2026-10-18 19:44:36 America/Los_Angeles:

| Repository | Branch / commit | Initial working tree |
| --- | --- | --- |
| Sidecar | `main`, `f0ea254e0c41e09ab900f58c1773e407575df986` | clean |
| External suite | `main`, `6e65846ee833ee91f308e07ecb8662fe56839a16` | clean |

Framework current checkout describes `v1.0.0-beta.8-12-g52f11dd`; the matching
`v1.0.0-beta.8` documentation was read through `git show`, not current development
docs or the stale installed documentation skill. No builds, services or tests were
started during research. Only this research artifact was written.

Both repositories' AGENTS instructions apply. The suite's README, project
agreement, foundation/runtime guides and SDK lane guide establish a separate,
provider-free SDK lane. Its retained beta.9 business lanes, credentials, databases
and maintained reference packs are outside this ticket. No data reset is implied.
Apply Sidecar's destructive development replacement policy and
[design lens](../design-lens.md#simplicity-and-technical-debt); no compatibility
machinery is required by the current contracts.

## Current Behavior and Data Flow

### Signing and independent verification

`sdks/java/core/src/main/java/ai/loomspan/sdk/SigningKeys.java:23` initializes one
signer, reads provided PEM/keystore material, derives the RFC 7638 public-key
thumbprint kid (`:93`), atomically exports its public JWK (`:100`) and returns an
in-memory signing source. There is no private-key watcher. Multiple apps using
the same provided key derive the same kid; export is public material, not a
receiver-controlled overlap set.

`src/main/java/ai/loomspan/sidecar/security/CurrentFileJwtTrust.java:45` reads the
configured file immediately and schedules fixed-delay one-second reloads. Byte
content changes publish complete immutable snapshots; missing/malformed data
withdraws trust. `forKid` (`:53`) permits additional unknown-kid reads globally
limited to once per second. No trust database or management API is involved.

`sdks/java/spring-boot-starter/src/main/java/ai/loomspan/sdk/spring/CallbackJwtTrust.java:38`
captures the startup signer's public key as `own`, independently loads optional
`loomspan-sdk.callbacks.overlap-jwks`, and schedules its own one-second polling
only when that path is configured. `reload` (`:62`) merges valid overlap keys
into `own`. Missing/unreadable/malformed/empty configured overlap publishes empty
trust including withdrawal of the own key. Corrected valid content restores it.
Without an overlap path the own key alone is sufficient. The two loaders neither
share state nor notify each other.

`CallbackProperties.java:10` defaults the receiver audience to the issuance
issuer; an explicit audience requires matching issuance additional audiences.
`CallbackSecurityAutoConfiguration.java:29` installs a highest-precedence
stateless callback chain on servlet apps and validates signature, exact configured
issuance issuer, receiver audience, execution purpose and timestamps. Callback
controllers remain application-owned. The starter does not require one to exist.
Sidecar, in contrast, does not have an expected-issuer setting: configured signing
keys establish origin and issuer/subject establishes execution ownership.

### Authorization and admitted work

The external reference app's `SecurityConfiguration.java:14` uses one fixed
in-memory directory: Alice/Bob have `ROLE_ORDER_EDITOR`, while literal has
`ORDER_EDITOR`. `ExecutionController.java:61` accepts a bounded list of fixture
skills, derives credentials from Spring authentication, captures selected opaque
credentials only into an in-memory fixture, and submits through the SDK. Status
reads issue fresh owner tokens (`:75`). No token is returned in controller output.

`CallbackController.java:38` applies `hasRole('ORDER_EDITOR')`, then checks signed
issuer `loomspan-app` and subject Alice before effect. A fixed fixture comparison
checks exact captured credential equality. Arrival/body/effect counters distinguish
authentication/method denial, record denial and successful effects. Existing
`local_probe.py:184` exercises Alice success, literal method denial and Bob record
denial; fresh polls from a different owner return 404 in `:86`.

Sidecar `ExecutionCoordinator.java:177` checks the original captured credential's
strict expiry and current decoder trust immediately before supported Framework
handoff. It does not continuously revalidate admitted Framework work.
`GenericRestSkillHandler.java:56` forwards the original bearer bytes only to the
configured passthrough target and checks strict expiry before each send (`:69`).
It does not recheck Sidecar origin trust before a callback: the receiving SDK has
its own current trust. Thus trust withdrawal, callback acceptance and admitted
work completion are separate observable boundaries.

### Existing deployment compositions

All suite paths below are relative to `C:/opendev/code/loomspan-sidecar-test-suite`.

| Area | Current behavior and evidence |
| --- | --- |
| Local | `scripts/sdk_lane/local.py:60` derives the actual shipped quickstart Compose, keeps Sidecar loopback/colocated settings, shares its namespace with reference app and fixture, and uses a unique app-writable/Sidecar-read-only public-key volume. |
| Local skills/routes | `local.py:120` emits normal model/blocking/planner and REST fixtures. `:162` always emits app callback routes plus a separate synthetic effect route. `:203` uses file application configuration and one worker. |
| Standard | `remote.py:45` overlays supplied `examples/remote/standard` app/Sidecar/routes with fixture endpoints. App and Sidecar are separate network services with Spring TLS; app exports to public volume, Sidecar reads it. |
| Hardened | `remote.py:117` runs app/Sidecar in their respective Caddy namespaces. Loopback backend/outbound listeners, fixed remote upstreams, mTLS callback ingress and independent browser ingress come from shipped fragments. |
| Remote inventory | `remote.py:15` has Standard six variants and Hardened eight variants: positive, relevant transport negatives, response loss, and Hardened isolation. No group/rotation/outbound-only variant exists. |
| Public delivery | `remote.py:329` checks the exported public modulus/exponent match generated provided signer and contain no private members before Sidecar start. Its current public volume is the export itself; there is no separately controlled callback overlap. |
| Model barriers | `fixture.py:262` holds captured credentials in memory, exposes booleans/counters, and provides bounded `entered`/`release` barriers. `:442` holds a normal blocking model for 30 seconds; `:376` holds the planner before its required-input callback child. |
| Transport driver | `remote_probe.py:70` configures verified remote invocation/control, reuses Local browser helpers, and `:146` checks exact positive/negative schemas. Current helpers assume one app/receiver. |

The existing reference app includes a callback controller on its ordinary Spring
listener. A model-only success in the current topology would therefore not by
itself prove absence of a callback listener or omission of configured routes.
Hardened currently always includes its callback-facing Caddy listener and the
Sidecar outbound callback proxy. This is a concrete coverage gap.

## Existing Tests and Fixtures

Current executable component evidence (located, not rerun during research):

- `src/test/java/ai/loomspan/sidecar/configuration/ConfigurationModeIntegrationTest.java:179`
  parameterizes file/database application mode, verifies configured process JWKS
  authority, empties/restores that file and verifies rejection/recovery. This owns
  mode wiring; the ticket explicitly excludes duplicating recipe scenarios by mode.
- `security/CurrentFileJwtTrustTest.java:12` owns current-file withdrawal/recovery;
  `:40`, `:90`, `:108`, `:139` cover whole-candidate validation, unavailable files,
  unknown-kid rate limit and complete snapshots/content detection.
- `sdks/java/spring-boot-starter/src/test/java/ai/loomspan/sdk/spring/CallbackJwtTrustTest.java:25`
  owns startup own-key behavior, overlap merge/removal (`:53`), unusable overlap
  withdrawal/recovery (`:67`), rate limits (`:134`) and scheduled reload/close (`:172`).
- External `tests/test_sdk_remote.py:132` checks shipped-fragment composition and
  signer isolation; `:168` dispatch; `:213`/`:230` strict schemas; `:316` failure
  cleanup; `:391` public export equality before Sidecar start.
- External `tests/test_sdk_runner.py:250` checks credential redaction/in-memory
  capture, `:339` bounded barrier/effect separation, `:408` scoped failure cleanup,
  `:1198` real planner protocol and `:1287` exact evidence schemas.

Existing reports are historical results, not new verification of this ticket.
`docs/verification.md` and external `docs/sdk-lane.md` identify current pre-ticket
Local 12-scenario report `20261018T232143Z-36a99814fb53`, Standard six-scenario
`20261019T015656Z-c2445d463665` and Hardened eight-scenario
`20261019T014927Z-b5ab2b9224e2`. Their recorded durations are 538.779s,
777.899s and 927.972s respectively, including mandatory builds, not performance
claims. These reports explicitly leave trusted-group/rotation work outstanding.

## Dependencies and Operational Constraints

The suite runner `scripts/run_sdk.py:897` accepts local/standard/hardened, a
Sidecar checkout path and a fresh output directory. It writes a report before
preflight, builds root Sidecar `clean verify`, SDK `clean install`, plain Java
driver, reference app and actual Dockerfile, then executes the selected recipe.
No cached-build bypass exists. `run_remote` (`:843`) names each startup/scenario/
cleanup stage, strictly matches observations and requires all selected outcomes
passed. `remote_contract.py:4` holds exact secret-free observation schemas.

Reports retain Git commits/dirty flags and artifact hashes plus bounded counters
and booleans, with fixed error categories rather than raw subprocess diagnostics.
Temporary material is removed after unique-project cleanup; independent exact
Docker label checks are part of prior verification practice. Private signer
material is delivered only to the app; Sidecar receives public trust. No provider,
live CA/DNS, business runtime mutation, commit, push or release is authorized.

Required tools are Java 21 via JAVA_HOME, Maven, Docker Compose, suite Python
environment including cryptography/httpx/PyYAML, and root build's existing
Chromium prerequisite. Public dependency downloads/container pulls may occur.
Normal checks from the suite are `.venv/Scripts/python.exe -m pytest
tests/test_sdk_runner.py tests/test_sdk_remote.py -q`, Ruff format/check across
runner/sdk_lane/affected tests, and each `scripts/run_sdk.py` recipe. Root owns
its own component checks; suite and deployment smoke never invoke one another.

Matching Framework beta.8 `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md`
states that REST manifests use `rest: true`, literal `rbac_roles`, supported
`RestSkillHandler`, and immutable captured generations for admitted work. A
model-only manifest has no REST handler cardinality requirement. Framework tests
remain explanatory contract evidence, never substitutes for Sidecar integration.

## Historical Context

The canonical phase signing/trust reference already specifies add public keys,
restart provided signers, then remove old public keys independently at each
receiver. It explicitly describes startup own-key replacement on restart. The
deployment reference and acceptance matrix require one trusted-group recipe and
focused callback-free coverage per recipe, without an exhaustive matrix.

Operator `docs/operations.md:40` already documents file/colocated trust, one-second
polling, strict queued/callback expiry and admitted-work noncancellation. It says
shared apps can assert each other's identity/roles and need separate Sidecars for
isolation. `sdks/java/README.md:346` documents independent callback own/overlap
trust. `docs/remote-recipes.md:26` documents provided-key transport but currently
has no complete trusted-group/JWT rotation procedure or callback-free deployment
instructions. No production implementation change is established as necessary
by research; missing evidence lies primarily in external composition and guidance.

## Open Questions

These are planning decisions, not research blockers:

- Which single provided-key recipe carries the second app and rotation? Both
  remote recipes exist, but neither currently models an independent app instance.
- How will each receiver's file update/adoption be observed independently, including
  the unchanged other receiver, within documented polling bounds and scheduling
  allowance? Unknown-kid probes can themselves trigger a rate-limited reload.
- Where will old opaque credentials survive app restart without becoming report,
  process argument or log output? Current capture is in fixture memory and currently
  has a single credential slot.
- How will callback-free application startup omit a real callback listener/controller
  while retaining authenticated issuance/control and preserving each recipe's
  transport configuration? Current app and Caddy fixtures always include callbacks.
- Which existing bounded model barrier provides admitted-work completion evidence
  after trust withdrawal without depending on a callback that the receiver must reject?
