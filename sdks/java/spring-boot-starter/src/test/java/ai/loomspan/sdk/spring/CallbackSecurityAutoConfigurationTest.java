package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class CallbackSecurityAutoConfigurationTest {
    private static final String OVERLAP_PROPERTY = "loomspan-sdk.callbacks.overlap-jwks=";
    private static final String UNAVAILABLE = "loomspan-sdk.callbacks.overlap-jwks is unavailable. Provide a readable overlap JWKS file.";
    private static final String INVALID = "loomspan-sdk.callbacks.overlap-jwks is invalid. Provide a valid nonempty public RSA JWKS file.";

    @TempDir Path directory;
    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withUserConfiguration(App.class).withPropertyValues(properties());
    }
    private String[] properties() {
        return new String[] {"loomspan-sdk.base-url=http://localhost:12345", "loomspan-sdk.signing.mode=colocated",
                "loomspan-sdk.signing.colocated.key-directory=" + directory,
                "loomspan-sdk.issuance.roles=ROLE_ORDER_EDITOR,ORDER_EDITOR,OTHER"};
    }

    @Test void configuredUnusableOverlapFailsStartupWithSafeDiagnostic() throws Exception {
        assertOverlapStartupFailure(directory.resolve("synthetic-missing.json"), UNAVAILABLE);
        assertOverlapStartupFailure(Files.createDirectory(directory.resolve("synthetic-unreadable.json")), UNAVAILABLE);
        assertOverlapStartupFailure(Files.writeString(directory.resolve("synthetic-malformed.json"), "{synthetic-content"), INVALID);
        assertOverlapStartupFailure(Files.writeString(directory.resolve("synthetic-empty-file.json"), ""), INVALID);
        assertOverlapStartupFailure(Files.writeString(directory.resolve("synthetic-empty-keys.json"), "{\"keys\":[]}"), INVALID);
        assertOverlapStartupFailure(Files.writeString(directory.resolve("synthetic-invalid-key.json"),
                "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"synthetic-content\",\"n\":\"AQ\",\"e\":\"AQAB\"}]}"), INVALID);
    }

    private void assertOverlapStartupFailure(Path overlap, String diagnostic) {
        runner().withPropertyValues(OVERLAP_PROPERTY + overlap)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseExactlyInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage(diagnostic);
                    assertThat(context.getStartupFailure().getMessage())
                            .doesNotContain(overlap.getFileName().toString(), "synthetic-content");
                });
    }

    @Test void lazyServletApplicationStillFailsOnUnusableOverlap() {
        assertThatThrownBy(() -> {
            try (var ignored = new SpringApplicationBuilder(App.class).properties(properties()).run(
                    "--spring.main.lazy-initialization=true", "--server.address=127.0.0.1", "--server.port=0",
                    "--" + OVERLAP_PROPERTY + directory.resolve("synthetic-missing.json"))) {
                // Close the server even if it starts unexpectedly.
            }
        }).hasRootCauseExactlyInstanceOf(IllegalStateException.class).hasRootCauseMessage(UNAVAILABLE);
    }

    @Test void pollingWithdrawsAndRestoresNewCallbacksWithoutCancellingAnAdmittedRequest() throws Exception {
        Path peerDirectory = Files.createDirectory(directory.resolve("peer"));
        SigningKeySource peer = SigningKeys.initialize(SigningSettings.colocated(peerDirectory));
        String validOverlap = Files.readString(peerDirectory.resolve("jwks.json"));
        Path overlap = Files.writeString(directory.resolve("overlap.json"), validOverlap);

        runner().withPropertyValues(OVERLAP_PROPERTY + overlap).run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var own = context.getBean(SigningKeySource.class);
            var trust = context.getBean(CallbackJwtTrust.class);
            var endpoints = context.getBean(Endpoints.class);
            var ownBearer = "Bearer " + token(own, List.of("ORDER_EDITOR"));
            var peerBearer = "Bearer " + token(peer, List.of("OTHER"));
            String plainPath = "/loomspan/callbacks/plain";
            mvc.perform(get(plainPath).header("Authorization", ownBearer)).andExpect(status().isOk());
            mvc.perform(get(plainPath).header("Authorization", peerBearer)).andExpect(status().isOk());

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var admitted = executor.submit(() -> mvc.perform(
                        get("/loomspan/callbacks/held").header("Authorization", peerBearer)).andReturn());
                try {
                    assertThat(endpoints.awaitHeldEntry()).isTrue();
                    replaceOverlap(overlap, "invalid");
                    awaitKeys(trust, Set.of());
                    for (String bearer : List.of(ownBearer, peerBearer)) {
                        mvc.perform(get(plainPath).header("Authorization", bearer))
                                .andExpect(status().isUnauthorized())
                                .andExpect(header().string("WWW-Authenticate", "Bearer"));
                    }
                    assertThat(endpoints.count()).isEqualTo(2);
                    assertThat(admitted.isDone()).isFalse();
                    endpoints.releaseHeld();
                    var response = admitted.get(5, TimeUnit.SECONDS).getResponse();
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(response.getContentAsString()).isEqualTo("loomspan-app:alice:[OTHER]");
                    assertThat(trust.snapshot().keys()).isEmpty();
                } finally {
                    endpoints.releaseHeld();
                }
            }

            replaceOverlap(overlap, validOverlap);
            awaitKeys(trust, Set.of(own.kid(), peer.kid()));
            mvc.perform(get(plainPath).header("Authorization", ownBearer)).andExpect(status().isOk());
            mvc.perform(get(plainPath).header("Authorization", peerBearer)).andExpect(status().isOk());
            assertThat(endpoints.count()).isEqualTo(5);
        });
    }

    private static void replaceOverlap(Path target, String content) throws Exception {
        Path replacement = Files.writeString(Files.createTempFile(target.getParent(), "replace-", ".json"), content);
        Files.move(replacement, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void awaitKeys(CallbackJwtTrust trust, Set<String> expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!trust.snapshot().keys().keySet().equals(expected) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(trust.snapshot().keys().keySet()).isEqualTo(expected);
    }
    @Test void callbackRequiresBearerEvenWithAuthenticatedBrowserSession() {
        runner().run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var session = new MockHttpSession();
            var security = SecurityContextHolder.createEmptyContext();
            security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("alice", "", List.of()));
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
            mvc.perform(get("/loomspan/callbacks/plain").session(session)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer")).andExpect(header().doesNotExist("Location"))
                .andExpect(forwardedUrl(null));
            mvc.perform(get("/loomspan/callbacks/plain").session(session).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
            assertThat(context.getBean(Endpoints.class).count()).isZero();
        });
    }
    private static String token(org.springframework.context.ApplicationContext context, List<String> roles) {
        return token(context.getBean(SigningKeySource.class), roles);
    }
    private static String token(SigningKeySource source, List<String> roles) {
        var claims = new java.util.LinkedHashMap<String,Object>();
        claims.put("iss", "loomspan-app"); claims.put("sub", "alice"); claims.put("aud", List.of("loomspan-app"));
        claims.put("exp", java.time.Instant.now().plusSeconds(300).getEpochSecond());
        claims.put("roles", roles); claims.put("token_use", "loomspan_execution");
        var object = new com.nimbusds.jose.JWSObject(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
            .type(new com.nimbusds.jose.JOSEObjectType("loomspan-execution+jwt")).keyID(source.kid()).build(), new com.nimbusds.jose.Payload(claims));
        var signature = source.signRs256(object.getSigningInput());
        return object.getHeader().toBase64URL() + "." + object.getPayload().toBase64URL() + "." + com.nimbusds.jose.util.Base64URL.encode(signature);
    }
    @Test void requestMatrixAndExactIdentityAndContextIsolation() {
        runner().run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            String token = token(context, List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR", "OTHER"));
            String bearer = "Bearer " + token;
            mvc.perform(get("/loomspan/callbacks/plain").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(content().string("loomspan-app:alice:[ROLE_ORDER_EDITOR, ORDER_EDITOR, OTHER]"))
                .andExpect(header().doesNotExist("Set-Cookie"));
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            mvc.perform(post("/loomspan/callbacks/plain").header("Authorization", bearer)).andExpect(status().isOk());
            for (var request : List.of(get("/loomspan/callbacks/plain"),
                    get("/loomspan/callbacks/plain").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "browser")),
                    get("/loomspan/callbacks/plain").header("Authorization", ""),
                    get("/loomspan/callbacks/plain").header("Authorization", "Basic anything"),
                    get("/loomspan/callbacks/plain").header("Authorization", "Bearer bad"),
                    get("/loomspan/callbacks/plain").header("Authorization", bearer, bearer),
                    get("/loomspan/callbacks/plain").header("Authorization", bearer, "Bearer other"),
                    get("/loomspan/callbacks/plain").header("Authorization", bearer + ", " + bearer),
                    get("/loomspan/callbacks/plain").header("Authorization", bearer + " " + bearer),
                    get("/loomspan/callbacks/plain?access_token=" + token),
                    get("/loomspan/callbacks/plain?access_token=a&access_token=b"),
                    get(java.net.URI.create("/loomspan/callbacks/plain?%61ccess_token=a")).header("Authorization", bearer))) {
                mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(header().doesNotExist("Location")).andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(forwardedUrl(null)).andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(token));
            }
            assertThat(context.getBean(Endpoints.class).count()).isEqualTo(2);
            // Isolate security from MVC's session flash-map/event bookkeeping.
            var trap = new org.springframework.mock.web.MockHttpServletRequest(context.getServletContext()) {
                @Override public jakarta.servlet.http.HttpSession getSession() { throw new AssertionError("session accessed"); }
                @Override public jakarta.servlet.http.HttpSession getSession(boolean create) { throw new AssertionError("session accessed"); }
            };
            trap.setMethod("GET"); trap.setRequestURI("/loomspan/callbacks/plain"); trap.addHeader("Authorization", bearer);
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class).doFilter(trap, response,
                (request, result) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull());
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            trap.removeHeader("Authorization");
            var rejected = new org.springframework.mock.web.MockHttpServletResponse();
            context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class).doFilter(trap, rejected,
                (request, result) -> { throw new AssertionError("unauthenticated callback reached servlet"); });
            assertThat(rejected.getStatus()).isEqualTo(401);
        });
    }
    @Test void methodSecurityAndBusinessAuthorizationAndOrdinaryCsrfRemainIndependent() {
        runner().run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            String permitted = "Bearer " + token(context, List.of("ROLE_ORDER_EDITOR"));
            String literal = "Bearer " + token(context, List.of("ORDER_EDITOR"));
            mvc.perform(get("/loomspan/callbacks/secured").header("Authorization", literal)).andExpect(status().isForbidden());
            assertThat(context.getBean(Endpoints.class).count()).isZero();
            mvc.perform(get("/loomspan/callbacks/secured").header("Authorization", permitted)).andExpect(status().isOk());
            mvc.perform(get("/loomspan/callbacks/business").header("Authorization", permitted)).andExpect(status().isForbidden());
            assertThat(context.getBean(Endpoints.class).count()).isEqualTo(2);
            mvc.perform(get("/ordinary")).andExpect(status().is3xxRedirection());
            mvc.perform(get("/ordinary").with(user("browser"))).andExpect(status().isOk());
            mvc.perform(post("/ordinary").with(user("browser"))).andExpect(status().isForbidden());
            mvc.perform(post("/ordinary").with(user("browser")).with(csrf())).andExpect(status().isOk());
            mvc.perform(get("/login")).andExpect(status().isOk());
        });
    }
    @Test void methodsAndFirewallUseNormalPolicy() {
        runner().run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            String bearer = "Bearer " + token(context, List.of());
            for (String method : List.of("PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")) {
                mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/loomspan/callbacks/plain").header("Authorization", bearer))
                    .andExpect(status().isForbidden());
                mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/loomspan/callbacks/plain"))
                    .andExpect(status().isUnauthorized());
            }
            mvc.perform(request(org.springframework.http.HttpMethod.TRACE, "/loomspan/callbacks/plain")).andExpect(status().isBadRequest());
            mvc.perform(get("/loomspan/callbacks/plain;bad=1")).andExpect(status().isBadRequest());
            mvc.perform(get("/loomspan/callbacks/plain").with(request -> { request.setRequestURI("/loomspan//callbacks/plain"); return request; })).andExpect(status().isBadRequest());
        });
    }
    @Test void prefixOverrideAndSegmentBoundary() {
        runner().withPropertyValues("loomspan-sdk.callbacks.path-prefix=/custom/hooks/").run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/custom/hooks")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
            mvc.perform(get("/custom/hooks/child")).andExpect(status().isUnauthorized());
            mvc.perform(get("/custom/hooks-other")).andExpect(status().is3xxRedirection());
            mvc.perform(get("/loomspan/callbacks/plain")).andExpect(status().is3xxRedirection());
        });
        for (String prefix : List.of("", "/", "/a/*", "/a?x", "/a#b", "/a\\b", "/a/../b", "/a//b")) {
            runner().withPropertyValues("loomspan-sdk.callbacks.path-prefix=" + prefix).run(context -> assertThat(context).hasFailed());
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @EnableWebMvc @EnableMethodSecurity
    static class App {
        @Bean org.springframework.security.core.userdetails.UserDetailsService users() {
            return new org.springframework.security.provisioning.InMemoryUserDetailsManager(
                org.springframework.security.core.userdetails.User.withUsername("browser").password("{noop}fixture").roles("USER").build());
        }
        @Bean SecurityFilterChain ordinary(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).formLogin(f -> {}).build();
        }
        @Bean Endpoints endpoints() { return new Endpoints(); }
        @Bean org.springframework.security.oauth2.jwt.JwtDecoder unrelatedDecoder() {
            return token -> { throw new AssertionError("application decoder must not validate callbacks"); };
        }
    }
    @RestController static class Endpoints {
        final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        int count() { return calls.get(); }
        boolean awaitHeldEntry() throws InterruptedException { return entered.await(5, TimeUnit.SECONDS); }
        void releaseHeld() { release.countDown(); }
        @RequestMapping("/loomspan/callbacks/plain") String callback(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken authentication) {
            calls.incrementAndGet(); return authentication.getToken().getClaimAsString("iss") + ":" + authentication.getName() + ":" + authentication.getAuthorities();
        }
        @GetMapping("/loomspan/callbacks/held")
        String heldCallback(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken authentication) throws InterruptedException {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("test did not release admitted callback");
            return callback(authentication);
        }
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ORDER_EDITOR')")
        @GetMapping("/loomspan/callbacks/secured") String secured() { calls.incrementAndGet(); return "ok"; }
        @GetMapping("/loomspan/callbacks/business") String business() { calls.incrementAndGet(); throw new org.springframework.security.access.AccessDeniedException("record denied"); }
        @RequestMapping("/ordinary") String ordinary() { return "ordinary"; }
    }
}



