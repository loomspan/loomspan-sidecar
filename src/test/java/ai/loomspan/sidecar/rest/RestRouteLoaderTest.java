package ai.loomspan.sidecar.rest;

import ai.loomspan.sidecar.config.RestRoutesProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestRouteLoaderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void loadsDefaultOverrideAndExplicitEmptyDocuments() throws Exception {
        RestRoutesProperties defaults = new RestRoutesProperties();
        assertThat(defaults.getRestRoutesLocation()).isEqualTo("file:/sidecar/rest-routes.yaml");
        Path file = write("empty.yaml", "targets: {}\nroutes: {}\n");
        assertThat(load(file, new MockEnvironment()).configuration().targets()).isEmpty();
        assertThat(load(file, new MockEnvironment()).configuration().routes()).isEmpty();
    }

    @Test
    void rejectsDuplicateUnknownAndMalformedRouteConfigurationWithSafeDiagnostics() throws Exception {
        assertInvalid("duplicate.yaml", """
                targets:
                  same: {base-url: http://localhost, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                  same: {base-url: http://localhost, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """, "duplicate mapping key");
        assertInvalid("unknown.yaml", "targets: {}\nroutes: {}\nextra: true\n", "extra", "unknown field");
        assertInvalid("path.yaml", """
                targets:
                  target: {base-url: http://localhost/api, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes:
                  call: {target: target, method: DELETE, path: //elsewhere}
                """, "routes.call.method", "GET and POST");
        assertInvalid("invalid-uri-path.yaml", """
                targets:
                  target: {base-url: http://localhost/api, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes:
                  call: {target: target, method: GET, path: '/bad[path'}
                """, "routes.call.path", "valid URI path");
        assertInvalid("auth.yaml", """
                targets:
                  target: {base-url: http://localhost/api, auth: {mode: none, headers: {X-Key: secret}}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """, "auth.headers", "static");
        assertInvalid("tiny-timeout.yaml", """
                targets:
                  target: {base-url: http://localhost/api, auth: {mode: none}, connect-timeout: 1ns, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """, "connect-timeout", "positive duration");
        assertInvalid("invalid-port.yaml", """
                targets:
                  target: {base-url: 'http://localhost:65536/api', auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """, "base-url", "absolute HTTP(S)");
        assertInvalid("duplicate-header-case.yaml", """
                targets:
                  target: {base-url: http://localhost/api, auth: {mode: static, headers: {X-Key: first, x-key: second}}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """, "headers.x-key", "duplicate header");
    }

    @Test
    void resolvesEveryStringPlaceholderBeforeValidationWithoutLeakingSecrets() throws Exception {
        Path valid = write("resolved.yaml", """
                targets:
                  callback:
                    base-url: ${CALLBACK_URL}/api
                    auth: {mode: static, headers: {X-Key: '${CALLBACK_SECRET}'}}
                    connect-timeout: ${TIMEOUT}
                    read-timeout: ${TIMEOUT}
                    max-response-size: 1KB
                routes:
                  call: {target: callback, method: POST, path: '/${PATH_PART}'}
                """);
        var environment = new MockEnvironment().withProperty("CALLBACK_URL", "http://localhost")
                .withProperty("CALLBACK_SECRET", "sentinel-secret")
                .withProperty("TIMEOUT", "2s").withProperty("PATH_PART", "echo");
        var configuration = load(valid, environment).configuration();
        assertThat(configuration.targets().get("callback").auth().headers()).containsEntry("X-Key", "sentinel-secret");
        assertThat(configuration.routes().get("call").path()).isEqualTo("/echo");

        Path unresolved = write("unresolved.yaml", """
                targets:
                  callback: {base-url: '${MISSING}', auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """);
        assertThatThrownBy(() -> load(unresolved, new MockEnvironment()))
                .hasMessageContaining("unresolved.yaml", "targets.callback.base-url", "${MISSING}");

        Path secretFailure = write("secret-failure.yaml", """
                targets:
                  callback: {base-url: '${SECRET}', auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """);
        assertThatThrownBy(() -> load(secretFailure,
                new MockEnvironment().withProperty("SECRET", "sentinel-resolved-secret")))
                .hasMessageNotContaining("sentinel-resolved-secret")
                .hasMessageContaining("base-url");

        Path malformedSecret = write("malformed-secret.yaml", """
                targets:
                  callback: {base-url: 'http://${SECRET}', auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes: {}
                """);
        assertThatThrownBy(() -> load(malformedSecret,
                new MockEnvironment().withProperty("SECRET", "resolved secret with spaces")))
                .satisfies(failure -> assertThat(allMessages(failure))
                        .doesNotContain("resolved secret with spaces")
                        .contains("targets.callback.base-url"));
    }

    private RestRouteLoader load(Path file, MockEnvironment environment) {
        RestRoutesProperties properties = new RestRoutesProperties();
        properties.setRestRoutesLocation(file.toUri().toString());
        var beanFactory = new DefaultListableBeanFactory();
        return new RestRouteLoader(properties, environment, new DefaultResourceLoader(),
                beanFactory.getBeanProvider(SslBundles.class));
    }

    private void assertInvalid(String name, String yaml, String... messages) throws Exception {
        Path file = write(name, yaml);
        var assertion = assertThatThrownBy(() -> load(file, new MockEnvironment()));
        for (String message : messages) assertion.hasMessageContaining(message);
    }

    private Path write(String name, String contents) throws Exception {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, contents);
        return file;
    }

    private static String allMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
