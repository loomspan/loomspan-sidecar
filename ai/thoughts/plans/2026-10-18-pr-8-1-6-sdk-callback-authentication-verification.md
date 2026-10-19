# SDK callback authentication verification

Pipeline Step 4, profile `full`. Implementation and automated acceptance are
complete; independent Step 5 review remains. `suite/` below refers to
`C:/opendev/code/loomspan-sidecar-test-suite/`.

## Red test

Before callback production changes, ran:

```powershell
mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am '-Dtest=CallbackSecurityAutoConfigurationTest#callbackRequiresBearerEvenWithAuthenticatedBrowserSession' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected failure observed: one test, expected HTTP 401 but actual HTTP 200.
The existing browser session reached the callback controller via the ordinary
application chain. This is executable evidence of the missing boundary.
The initial unquoted PowerShell `-Dsurefire.failIfNoSpecifiedTests=false` attempt
failed Maven argument parsing and is not the red proof.

## Implementation decisions

- The callback bearer converter has a chain-local details source that does not
  read sessions. Spring's default bearer details builder calls `getSession(false)`
  even with stateless sessions; the component request trap detected this.
  The chain also uses a null session authentication strategy. The session trap
  runs the actual security filter chain with a terminal servlet chain, isolating
  MVC's separate flash-map/event bookkeeping; normal MockMvc requests still
  exercise real controllers and application method security.
- The anonymous filter likewise has a chain-local non-session details source,
  preserving normal unauthenticated 401 versus authenticated method-denial 403.
- The starter depends on Boot's security starter so servlet security infrastructure
  is discovered without an application `@EnableWebSecurity`; the component fixture
  proves this through auto-configuration. It still requires applications to enable
  method security deliberately.
- Blank authority strings are rejected before Spring conversion, consistent with
  the issuer's nonblank exact authority contract, avoiding converter failures.
- Read the verified issuer with `Jwt.getClaimAsString("iss")`: issuance permits
  a non-URL issuer such as the default `loomspan-app`, while `Jwt.getIssuer()`
  coerces it into a URL. This also applies to application examples.

## Results

- PASS: focused `CallbackJwtTrustTest,CallbackJwtDecoderTest`, 13 JUnit tests,
  including looped invalid-claim/key permutations; exact command is the red
  command above with `-Dtest=CallbackJwtTrustTest,CallbackJwtDecoderTest`.
- PASS: `mvn -B -ntp -f sdks/java/pom.xml clean install`: complete reactor,
  starter 30 tests, zero failures/errors/skips. During development the session
  trap exposed and drove the converter/strategy fixes; all production changes
  passed this complete build. Two added negative session assertions are included
  in the composed runner's subsequent SDK rebuild.
- PASS (suite): `.venv/Scripts/python.exe -m pytest tests/test_sdk_runner.py -q`:
  79 tests; `.venv/Scripts/python.exe -m pytest tests -q`: 189 tests.
- PASS (suite): `ruff format --check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`
  and `ruff check scripts/run_sdk.py scripts/sdk_lane tests/test_sdk_runner.py`.
- PASS: `python sdks/java/tests/test_build_boundary.py`: three test families,
  real disposable Maven reactors, 199.233 seconds.
- PASS: `mvn -B -ntp -f sdks/java/pom.xml -pl spring-boot-starter -am '-Dtest=CallbackJwtDecoderTest,CallbackSecurityAutoConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`:
  final auto-discovery, converter/anonymous/session strategy and blank-role changes,
  10 tests, zero failures/errors/skips.
- FAIL (suite): `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`,
  report `suite/evidence/sdk/20261018T230730Z-15907b2c1449/report.json`.
  All builds, five baseline scenarios, existing Local scenarios and new callback
  authorization passed; only `local-callback-expiry` failed (3.563 seconds).
  Both cleanup stages passed. Root: 61 classes / 261 tests, zero failures/errors/skips;
  final SDK starter: 6 classes / 30 tests, zero failures/errors/skips.
  Isolated Local fixture debugging is in progress before a fresh complete run.
  Isolated diagnosis observed the exact required terminal failure and zero
  callback effects, but the extra `toolIssued` oracle failed: beta.8 automatically
  dispatched the zero-input assigned leaf without a step-model CALL_TOOL turn.
  The expiry fixture therefore uses a dedicated leaf requiring a fixed `message`
  input, so the model emits a real CALL_TOOL with that input after release. This
  is fixture protocol adaptation; the sender and framework are unchanged.
- PASS: corrected isolated Local Runtime using the already-built image and Boot
  JAR: callback authorization and callback expiry probes both returned every
  required strict observation, including real CALL_TOOL, exact expiry failure,
  zero callback arrivals/bodies/effects; cleanup completed with process exit 0.
  The inline `.venv/Scripts/python.exe -u -` helper imported `sdk_lane.local`
  and `run_sdk` from `scripts`, created a `TemporaryDirectory` and a unique
  `sdk-targeted-` Runtime with image
  `loomspan-sdk-smoke:sdk-708c8ca0aa304ee9`, invoked `start`, `probe("callbacks")`,
  `probe("callback-expiry")`, and unconditionally called `cleanup` in `finally`.
  It rebuilt no artifacts and retained no helper. Independent
  `docker ps -a --filter 'name=sdk-targeted-' --format '{{.Names}}'`,
  `docker network ls --filter 'name=sdk-targeted-' --format '{{.Name}}'` and
  `docker volume ls --filter 'name=sdk-targeted-' --format '{{.Name}}'`
  returned no resources afterward.
- PASS (suite, final fixture): focused pytest 79 passed (13.47s), full safe Python
  suite 189 passed (27.39s), Ruff format/check passed.
- PASS (suite, final complete run): `.venv/Scripts/python.exe scripts/run_sdk.py local --sidecar-checkout C:/opendev/code/loomspan-sidecar`:
  [report 20261018T232143Z-36a99814fb53](../../../../loomspan-sidecar-test-suite/evidence/sdk/20261018T232143Z-36a99814fb53/report.json),
  all 12 scenarios and every build/runtime/cleanup stage passed in 538.779s.
  Report SHA256: `a8c081eb2490c4ac417d024aeb31b4903d24883920624fa8b0248915395caa1f`.
  Root `clean verify`: 61 classes / 261 tests; SDK `clean install`: core 25 and
  starter 30 tests; all zero failures/errors/skips. Both real consumers packaged.
  Root build 309.188s, SDK 40.621s, Boot consumer 6.025s; callback authorization
  1.626s, callback expiry 4.731s, Local cleanup 21.890s, baseline cleanup 21.633s.

## Composed acceptance evidence and limits

The final report's `localRecipe.callbacks` records verified initiating identity,
exact forwarded credential equality, standard method denial before invocation,
record-ownership denial before effect, and preserved form/CSRF behavior.
`localRecipe.callbackExpiry` records RUNNING admission and planner barrier entry,
the exact `execution credential expired` child failure, fresh polling, and zero
HTTP arrivals, body invocations and effects through terminal completion and a
bounded quiet interval. The required-input fixture emits an actual CALL_TOOL;
queue expiry remains a distinct earlier failure regression.

The complete root run includes `ConfigurationModeIntegrationTest` (8 passing
tests) proving configured process-JWKS trust in file and database modes. It does
not substitute for SDK trust tests. Standard/Hardened recipes and full receiver
rotation remain outside this ticket. No paid/provider operation, business runtime,
operational/reference-data reset, commit, push or release occurred.

Final independent resource queries all exited 0 with empty output: `docker ps -a`,
`docker network ls` and `docker volume ls` filtered by exact
`com.docker.compose.project=sdk-1c806c9925a64c11` label, container/network queries
for `sdk-1c806c9925a64c11-local`, and
`docker volume ls --filter 'name=sdk-1c806c9925a64c11-local-keys' --format '{{.Name}}'`.
The uniquely tagged built image is retained by the existing lane policy.

## Component acceptance evidence

The complete SDK build executes `CallbackSecurityAutoConfigurationTest` (5
component tests, loops covering credential/path/method variants),
`CallbackJwtDecoderTest` (5 unit tests, raw header/claim/signature and fixed-clock
skew matrices), and `CallbackJwtTrustTest` (8 tests covering generated colocated
and provided signing, complete overlap replacement, no fallback, removal,
malformed candidates, concurrency, rate limiting, automatic polling and close).
`ExecutionTokenAutoConfigurationTest` also verifies discovery and a non-web
context with neither callback chain nor trust owner.

The servlet fixture has a real first SDK chain, a second ordinary form-login
chain, controllers and Spring method security. It installs an unrelated
application decoder that fails if called, proving receiver ownership. Direct
401 responses have the Bearer challenge, no Location, forward or new session
cookie; valid/invalid/session-only credentials, exact authorities, context
cleanup, method/body distinction, business denial, ordinary CSRF, prefix
boundaries and default firewall behavior are asserted.
