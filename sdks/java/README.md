# Java SDK build

From the Sidecar checkout, build and locally install the independent Java 21 SDK:

```powershell
mvn -B -ntp -f sdks/java/pom.xml clean install
python sdks/java/tests/test_build_boundary.py
```

This reactor is separate from the root Sidecar application. It produces
`ai.loomspan:loomspan-sdk-core:0.1.0-SNAPSHOT` and
`ai.loomspan:loomspan-sdk-spring-boot-starter:0.1.0-SNAPSHOT`. This development
version makes no release commitment. Both JARs intentionally contain no public
API yet; subsequent feature tickets supply client and integration behavior.

Core has no runtime dependencies. The starter depends on core and optional Spring
Boot 4.1.0 autoconfiguration/servlet support. Normal builds enforce Java 21+ and
reject direct or transitive `ai.loomspan` dependencies except these two SDK
artifacts; core also rejects Spring Boot dependencies. The boundary test builds
disposable reactors with synthetic direct/transitive Framework and Sidecar
artifacts, without overwriting real release artifacts or installing fixtures.
The test driver needs only Python's standard library and Maven; it does not
depend on the external suite's Python packages.

The neighboring `loomspan-sidecar-test-suite` documents its separate SDK startup
lane in `docs/sdk-lane.md`. No Framework build, publication or CI is involved.
