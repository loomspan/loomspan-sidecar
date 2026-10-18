package ai.loomspan.sidecar.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
public class JwtSecurityConfiguration {
    @Bean
    CurrentFileJwtTrust currentFileJwtTrust(SidecarTrustProperties properties,
            org.springframework.core.env.Environment environment) {
        return new CurrentFileJwtTrust(properties.getMode(), properties.resolve(environment));
    }

    @Bean("executionTrust")
    CurrentFileTrustHealthIndicator executionTrust(CurrentFileJwtTrust trust) {
        return new CurrentFileTrustHealthIndicator(trust);
    }

    @Bean
    JwtDecoder sidecarJwtDecoder(SidecarJwtProperties properties, CurrentFileJwtTrust trust,
            java.time.Clock clock) {
        properties.validate();
        return new ExecutionJwtDecoder(trust, properties.getAudience(), clock);
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> sidecarJwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        return jwt -> new JwtAuthenticationToken(jwt, authorities.convert(jwt), jwt.getSubject());
    }

    @Bean
    GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("");
    }

    @Bean
    @Order(2)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain sidecarSecurityFilterChain(HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> converter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/_loomspan/observability/v1/**").permitAll()
                        .requestMatchers("/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> writeProblem(response, 401, "Unauthorized"))
                        .accessDeniedHandler((request, response, failure) -> writeProblem(response, 403, "Forbidden")))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, failure) -> writeProblem(response, 401, "Unauthorized"))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response, int status,
            String title) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + "}");
    }
}
