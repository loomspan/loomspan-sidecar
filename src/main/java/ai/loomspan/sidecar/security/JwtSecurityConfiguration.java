package ai.loomspan.sidecar.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
public class JwtSecurityConfiguration {
    @Bean
    JwtDecoder sidecarJwtDecoder(SidecarJwtProperties properties) throws IOException {
        properties.validate();
        JwtDecoder decoder;
        if (properties.getPublicKeyLocation() != null) {
            try (var input = properties.getPublicKeyLocation().getInputStream()) {
                RSAPublicKey key = (RSAPublicKey) RsaKeyConverters.x509().convert(input);
                decoder = NimbusJwtDecoder.withPublicKey(key).build();
            }
        } else if (properties.getJwkSetUri() != null && !properties.getJwkSetUri().isBlank()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        } else {
            decoder = JwtDecoders.fromIssuerLocation(properties.getIssuerUri());
        }

        var timestamp = new JwtTimestampValidator(properties.getClockSkew());
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(timestamp);
        validators.add(new JwtIssuerValidator(properties.getIssuerUri()));
        validators.add(new JwtAudienceValidator(properties.getAudience()));
        validators.add(requiredClaims());
        ((NimbusJwtDecoder) decoder).setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> requiredClaims() {
        return jwt -> {
            if (jwt.getIssuer() == null || jwt.getIssuer().toString().isBlank()
                    || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || jwt.getExpiresAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Nonblank iss and sub and an exp claim are required", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    @Bean
    JwtAuthenticationConverter sidecarJwtAuthenticationConverter(SidecarJwtProperties properties) {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(properties.getRolesClaim());
        authorities.setAuthorityPrefix(properties.getRolePrefix());
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    GrantedAuthorityDefaults grantedAuthorityDefaults(SidecarJwtProperties properties) {
        return new GrantedAuthorityDefaults(properties.getRolePrefix());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain sidecarSecurityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/_loomspan/observability/v1/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
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
