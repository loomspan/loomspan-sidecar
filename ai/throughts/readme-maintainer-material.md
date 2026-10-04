# Sidecar maintainer material — pending review

Parked on 2026-10-04 for a later keep/move/delete decision. These historical
extracts are not revalidated release instructions. Commands assume the repository
root. Release prose conflicts with the POM (already beta.2); beta.6 acceptance
does not establish current beta.7 integration.

## Local image and shutdown checks

The development Compose stack uses an example-only issuer, deterministic model
stub, and persistent SQLite volume. The image smoke check verifies empty
database selection, JWT protection, readiness/liveness probes, and bounded
process exit:

```powershell
python scripts/verify-image.py --image loomspan-sidecar:sc5-local
```

The Compose and verifier host ports default to `8080`, `8081`, and `9091`.
When those ports are in use, leave container ports unchanged and choose
isolated host ports:

```powershell
python scripts/verify-image.py --image loomspan-sidecar:sc5-local `
  --api-port 18080 --host-port 18081 --management-port 19091
```

With a stopped, isolated test database containing the authored quickstart
planner and routes, verify SIGTERM during nested work and at the framework
deadline.

If the selected database configuration uses encrypted provider credentials,
provision `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` in the verifier's process environment
with the same key that encrypted them before running the command. The production
verifier passes its fixture environment to the shutdown verifier automatically.
Database mode needs this key when encrypting or decrypting provider credentials;
an empty database can start without it. File mode neither requires nor inspects
the key.

```powershell
python scripts/verify-shutdown.py --image loomspan-sidecar:sc5-local --database-dir C:/path/to/stopped-test-data
```

The shutdown verifier mounts the source database directory read-only and copies
the database set into each isolated Compose project's disposable named volume,
with ownership `10001:10001` and owner-only read/write access. This also works
on Linux CI runners where host bind mounts retain host ownership. It prints
Compose logs before cleanup, including when startup readiness fails, and uses
ports `28080`, `28081`, and `29091` (overridable with the same port flags).
Its test callback gate holds admitted execution across SIGTERM. One case
releases it and checks completion; the other holds it past a three-second
framework budget and checks bounded exit without a forced kill.

## Phase 6 local acceptance (2026-09-19)

Local integration was checked against the developer-installed
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.6-SNAPSHOT` with these exact
commands from the repository root:

```powershell
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd -B -ntp package
docker build --tag loomspan-sidecar:sc5-local .
python scripts/verify-production.py --image loomspan-sidecar:sc5-local
```

`verify` and `package` passed with zero test failures or errors. The package
run executed 260 tests; three Compose-only browser methods skipped in ordinary
Maven runs and ran through the deployment verifier instead. The image build
passed. The complete verifier passed with disposable HTTPS Compose projects,
test-only `.test` mail capture, and local JWT/model/REST fixtures. It checked
emailed setup, invitations, recovery, Secure cookies, role and CSRF isolation,
management lock and SMTP outage behavior, concurrent browser editing and stale
grant rejection, authored publication and REST/model execution, two independent
database stores with destination URL bindings, invalid import preservation,
fresh import/rollback identities, and selection after recreation. It also
showed startup failure with an invalid selected pointer, replacement from a
stopped full database file set with stale WAL/SHM removal and UID/GID 10001,
restored accounts/current/history and REST/model execution. The embedded
shutdown verifier reported admitted-work completion in 2.32 seconds and
framework cutoff in 4.46 seconds, both with SIGTERM exit 143.

The earlier deferred startup and publication boundary checks are covered by
Sidecar's `RuntimeConfigurationIntegrationTest` and
`RuntimeConfigurationRecoveryIntegrationTest`, including before-commit,
committed pending selection, reversion, status failure, and activation before
dispatch. `ManagementConfigurationHttpIntegrationTest` covers accepted updates
after client disconnect/logout. `ExecutionConfigurationCorrelationIntegrationTest`
and `RestGenerationIntegrationTest` cover captured snapshot IDs and retained
REST resources across publication; the Compose shutdown check covers the
existing completion/cutoff budget. These Sidecar tests ran in the Maven suite;
framework-only tests are not used as Sidecar acceptance evidence.

Use a stopped full database backup for installation recovery, a configuration
ZIP for authored-content transfer between instances, and retained-history
rollback to load a retained snapshot into a draft for later publication. ZIP and rollback do
not restore management accounts or repair an unusable database. Protect the
database backup and exported ZIP because authored values may contain secrets.

Local integration verification against the installed beta 6 snapshot precedes framework beta 6 publication.
After framework publication, switch the Sidecar dependency to released
`1.0.0-beta.6`, then run the final Sidecar build/tests and commit. Hosted CI must
resolve that published artifact and pass before the Sidecar release. None of
those release gates, publication steps, commits, or tags are claimed by this
local acceptance run.

## Dependency and release boundary

Production and test code may use Loomspan Java types only from `ai.loomspan.api`.

The Guava dependency override selects `33.4.0-jre` for Sidecar's Java 21 runtime.
Without it, the framework's Google GenAI / Google Auth dependency chain selects
`33.4.0-android`. The override changes the runtime flavor at the same version.
Error Prone annotations use the transitive dependency version; Sidecar does not
require a separate annotation-version override.

Local builds and push/pull-request CI use published framework `1.0.0-beta.7`.
CI pins that same version through job-level `MAVEN_ARGS`, including Maven
subprocesses launched by the production Compose browser verifier. For future
framework upgrades, the delivery order is local Sidecar integration against
the development snapshot, framework release checks and publication, then
Sidecar verification against the released artifact. This project does not
build framework source in its own build or CI.

### Version commands

Use `python scripts/sidecar_version.py check` to check the root POM and
authoring skill metadata, including the pinned framework version. From a clean
worktree, prepare a version change with:

```powershell
python scripts/sidecar_version.py set 1.0.0-beta.2
python scripts/sidecar_version.py check
```

`set` changes only the Sidecar version in the root POM and authoring skill.
It preserves the framework dependency, historical evidence, and release examples.
Review, verify, and commit the changes before creating the local annotated tag:

```powershell
python scripts/sidecar_version.py tag 1.0.0-beta.2
```

`tag` requires a clean worktree, matching POM and skill metadata, a matching
release version, a non-SNAPSHOT framework dependency, and an unused local tag.
It does not build, verify remote tag availability, push, or publish. Check remote
tags and complete release verification before pushing the commit and tag.
After release, use `set <next-version>-SNAPSHOT` to start the next development
version. The packaging helper below remains responsible for release downloads.

Release tags are exactly `v<project-version>`. The guarded workflow requires a
non-SNAPSHOT Sidecar version and framework `1.0.0-beta.7`, reruns Maven and image
verification, then publishes an immutable GHCR version tag and a GitHub release
containing the executable JAR, a reproducible ZIP archive, and SHA-256 files.
Local preparation is intentionally nonpublishing:

```powershell
python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.7 --tag v1.0.0-beta.2
```

Sidecar development is on `1.0.0-beta.2-SNAPSHOT`; the next planned release is
`1.0.0-beta.2`, tagged `v1.0.0-beta.2`, bundling framework `1.0.0-beta.7`.
Sidecar and framework versions advance independently. Historical acceptance
evidence retains the versions tested then.

### Publish Sidecar 1.0.0-beta.2

Commit the reviewed framework upgrade first: the version helper requires a
clean worktree. Confirm framework beta 7 is resolvable from Maven Central and
that the remote Sidecar tag is unused (`git ls-remote --tags origin
refs/tags/v1.0.0-beta.2` must print no matching tag). On Windows, prepare and
verify the release from the repository root:

```powershell
python scripts/sidecar_version.py set 1.0.0-beta.2
python scripts/sidecar_version.py check
python scripts/prepare-release.py --validate-only --project-version 1.0.0-beta.2 --loomspan-version 1.0.0-beta.7 --tag v1.0.0-beta.2
python -m unittest discover -s scripts -p 'test_*.py' -v
python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v
.\mvnw.cmd -B -ntp -Prelease verify
docker build --tag loomspan-sidecar:beta2-release-check .
python scripts/verify-image.py --image loomspan-sidecar:beta2-release-check
python scripts/verify-production.py --image loomspan-sidecar:beta2-release-check
```

Stop on any failure. These image checks use disposable local fixture
deployments and require Docker. On a fresh machine, install Playwright's
Chromium before running the production verifier:

```powershell
.\mvnw.cmd -B -ntp test-compile org.codehaus.mojo:exec-maven-plugin:3.6.2:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.classpathScope=test" "-Dexec.args=install chromium"
```

Review the version diff and commit it, then push the
release commit and wait for its CI to pass before pushing the tag:

```powershell
git add pom.xml agent-skills/loomspan-sidecar-authoring/SKILL.md
git commit -m "Release Sidecar 1.0.0-beta.2 with Loomspan 1.0.0-beta.7"
git push origin main
# Wait for this commit's CI to pass, then:
python scripts/sidecar_version.py tag 1.0.0-beta.2
git push origin v1.0.0-beta.2
```

Pushing the tag triggers `.github/workflows/release.yml`. Wait for both its
verification and publication jobs to pass, then check the GitHub release
downloads/checksums and GHCR image tag `1.0.0-beta.2`. Never overwrite an
existing release or image tag. After successful publication, start the next
development version from a clean worktree:

```powershell
python scripts/sidecar_version.py set 1.0.0-beta.3-SNAPSHOT
git add pom.xml agent-skills/loomspan-sidecar-authoring/SKILL.md
git commit -m "Start Sidecar 1.0.0-beta.3-SNAPSHOT development"
git push origin main
```

## Former README SDK roadmap


Client SDKs for Sidecar's public HTTP API will live in this repository under
`sdks/`, with independently built and published language packages. These are
placeholder directories only; no SDKs are implemented or published yet.

```text
sdks/
  java/     # Java; planned standalone Maven library
  go/       # Go
  node/     # Node.js (JavaScript)
  ruby/     # Ruby / Ruby on Rails
  python/   # Python
  dotnet/   # .NET
```

The Java SDK will have its own `sdks/java/pom.xml` when implemented. The root
`pom.xml` continues to build the Sidecar service.


## Production fixture verification

For local verification, run `python scripts/verify-production-tls.py --image
loomspan-sidecar:sc5-local` and `python scripts/verify-production.py --image
loomspan-sidecar:sc5-local` from the repository root. They create only
disposable containers, Compose projects, volumes, certificates, fixture
issuer/model and SMTP capture; they do not use `production.env`, contact real
mail recipients, request public certificates, or modify DNS. The TLS fixture
tests local private-CA automation, trust and recreation, supplied replacement,
and Caddyfile adaptation for public and Cloudflare DNS modes. Actual public CA
issuance and Cloudflare DNS updates require a separately authorized,
configured-environment check and are not established by the local tests.

