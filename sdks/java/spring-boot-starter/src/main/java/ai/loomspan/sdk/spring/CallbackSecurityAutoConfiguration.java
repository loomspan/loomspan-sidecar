package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.SigningKeySource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** Independent request-scoped authentication; application method security owns authorization. */
@AutoConfiguration(after = ExecutionTokenAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CallbackProperties.class)
public class CallbackSecurityAutoConfiguration {
    @Bean(destroyMethod="close") CallbackJwtTrust callbackJwtTrust(SigningKeySource source, CallbackProperties properties) {
        return new CallbackJwtTrust(source, properties);
    }

    @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain loomspanCallbackSecurityFilterChain(HttpSecurity http, CallbackJwtTrust trust,
            CallbackProperties properties, IssuanceProperties issuance) throws Exception {
        String prefix = properties.validatedPathPrefix();
        var decoder = new CallbackJwtDecoder(trust, issuance.getIssuer(), properties.audienceOrDefault(issuance.getIssuer()));
        org.springframework.security.web.AuthenticationEntryPoint unauthorized = (request, response, failure) -> {
            response.setStatus(401); response.setHeader("WWW-Authenticate", "Bearer");
        };
        org.springframework.security.web.access.AccessDeniedHandler forbidden = (request, response, failure) -> response.setStatus(403);
        var bearerConverter = new org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter();
        bearerConverter.setBearerTokenResolver(CallbackSecurityAutoConfiguration::bearer);
        // Spring's default details builder reads the session even in a stateless chain.
        bearerConverter.setAuthenticationDetailsSource(request -> null);
        var anonymous = new org.springframework.security.web.authentication.AnonymousAuthenticationFilter("loomspan-sdk-callbacks");
        anonymous.setAuthenticationDetailsSource(request -> null);
        return http.securityMatcher(request -> {
                String path = request.getServletPath();
                if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
                return path.equals(prefix) || path.startsWith(prefix + "/");
            })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .sessionAuthenticationStrategy(new org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy()))
            .securityContext(s -> s.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(c -> c.requestCache(new NullRequestCache()))
            .csrf(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .anonymous(c -> c.key("loomspan-sdk-callbacks").authenticationFilter(anonymous))
            .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/**").authenticated().anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
            .oauth2ResourceServer(o -> o.authenticationConverter(bearerConverter)
                .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)
                .jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt,
                    jwt.getClaimAsStringList("roles").stream().map(SimpleGrantedAuthority::new).toList(), jwt.getSubject()))))
            .build();
    }

    private static String bearer(HttpServletRequest request) {
        // Inspect the query only: request parameter APIs can read a form body.
        String query = request.getQueryString();
        if (query != null) {
            for (String parameter : query.split("&")) {
                String name = parameter.split("=", 2)[0];
                try { name = java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8); }
                catch (IllegalArgumentException ignored) { throw invalidBearer(); }
                if (name.equals("access_token")) throw invalidBearer();
            }
        }
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() > 1) throw invalidBearer();
        if (!headers.isEmpty() && !headers.getFirst().matches("(?i)Bearer [A-Za-z0-9\\-._~+/]+=*")) throw invalidBearer();
        return new DefaultBearerTokenResolver().resolve(request);
    }
    private static OAuth2AuthenticationException invalidBearer() {
        return new OAuth2AuthenticationException(new OAuth2Error(BearerTokenErrorCodes.INVALID_REQUEST));
    }
}
