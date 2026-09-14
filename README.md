# Loomspan Sidecar

Loomspan Sidecar is a Java 21 / Spring Boot 4.1 application that loads mounted,
model-backed Loomspan YAML skills. This SC1 scaffold establishes startup loading,
management probes, dependency policy, and the supported Java API boundary. The
asynchronous API, authentication, workers, REST handler, and container packaging
arrive in SC2-SC5.

## Build locally

Local development uses
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` installed from
framework commit `385729a254261de128df491505acd8898cc0a021`. Install that revision
in `C:\opendev\code\loomspan-framework` after any framework change, then run:

```powershell
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd spring-boot:run
```

On POSIX systems use `./mvnw`. Tests use temporary local files and loopback-only
configuration; they do not need a model provider account.

## Mounted configuration

The default mount layout is:

```text
/sidecar/skills/**/*.yaml
/sidecar/skills/**/*.yml
/sidecar/rest-routes.yaml
```

`loomspan.skills.locations` contains only the two skills patterns. The sibling
route file is reserved by `loomspan-sidecar.rest-routes-location` for SC4 and is
not parsed in this phase. Override skill locations at startup, for example:

```powershell
java -jar target/loomspan-sidecar-1.0.0-beta.4-SNAPSHOT.jar `
  "--loomspan.skills.locations=file:C:/mounted/skills/**/*.yaml,file:C:/mounted/skills/**/*.yml"
```

Each model-backed manifest names a model. Configure its connection and provider
model explicitly; the manifest does not create them:

```yaml
loomspan:
  connections:
    primary:
      driver: openai
      base-url: ${MODEL_BASE_URL}
      api-key: ${MODEL_API_KEY}
  models:
    primary:
      connection: primary
      provider-model: ${MODEL_NAME}
```

Keep credentials in environment variables. Skills are loaded only at startup;
restart the process after changing mounted skill files. SC4 will apply the same
startup-only rule when it adds route-file loading.

## Management endpoints

The application port remains Boot's default `8080`. Health is exposed separately
on management port `9091` at `/actuator/health` and
`/actuator/health/readiness`. Other Actuator endpoints are not exposed.
Loomspan observability routes are disabled in SC1, so the scaffold exposes no
execution or catalog API.

## Dependency and release boundary

Production and test code may use Loomspan Java types only from `ai.loomspan.api`.

Push and pull-request CI is prepared to override the dependency with published
`1.0.0-beta.4`. Hosted verification is deferred until that artifact exists on
Maven Central. The delivery order is local Sidecar integration against the
snapshot, framework release checks and publication, then Sidecar verification
against the released artifact. This project does not build framework source in
its own build or CI.

See the [delivery handoff](ai/thoughts/beta4-handoff.md),
[SC1 phase](ai/thoughts/phases/phase-sc1.md), and
[scaffold ticket](ai/thoughts/tickets/2026-09-13-sidecar-scaffold.md).
