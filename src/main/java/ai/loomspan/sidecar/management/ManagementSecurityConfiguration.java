package ai.loomspan.sidecar.management;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
public class ManagementSecurityConfiguration {
    @Bean
    FilterRegistrationBean<ManagementSessionGuard> managementGuardRegistration(ManagementSessionGuard guard) {
        var registration = new FilterRegistrationBean<>(guard);
        registration.setEnabled(false);
        return registration;
    }
    @Bean
    @Order(1)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain managementSecurityFilterChain(HttpSecurity http, ManagementUserDetailsService users,
            ManagementIdentityService identity, ManagementAttemptLimiter attempts, ManagementSessionGuard guard) throws Exception {
        var provider = new DaoAuthenticationProvider(users);
        var protectionEncoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        provider.setPasswordEncoder(new PasswordEncoder() {
            @Override public String encode(CharSequence rawPassword) {
                return "{pbkdf2@SpringSecurity_v5_8}" + protectionEncoder.encode(rawPassword);
            }
            @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return identity.matches(rawPassword.toString(), encodedPassword);
            }
        });
        http.securityMatcher("/management/**", "/api/management/**")
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/management/login", "/management/setup", "/management/forgot",
                                "/management/password/set", "/management/password/reset", "/management/assets/console.css",
                                "/management/assets/console.js",
                                "/api/management/setup", "/api/management/password/forgot",
                                "/api/management/password/set", "/api/management/password/reset").permitAll()
                        .requestMatchers("/management/accounts").hasAuthority("MGT_ADMIN")
                        .requestMatchers("/api/management/accounts", "/api/management/accounts/**").hasAuthority("MGT_ADMIN")
                        .requestMatchers("/api/management/editing/lease/takeover").hasAuthority("MGT_ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/management/editing", "/api/management/editing/draft").authenticated()
                        .requestMatchers("/api/management/editing/lease", "/api/management/editing/lease/**",
                                "/api/management/editing/draft", "/api/management/editing/draft/validate",
                                "/api/management/configuration/publish").hasAuthority("MGT_EDITOR")
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/management/login").loginProcessingUrl("/management/login")
                        .usernameParameter("email").passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            guard.mark(request);
                            attempts.clear(accountKey(request.getParameter("email")));
                            response.sendRedirect("/management/home");
                        })
                        .failureHandler((request, response, failure) -> {
                            attempts.record(accountKey(request.getParameter("email")), 5, Duration.ofMinutes(15));
                            attempts.record(ipKey(request), 20, Duration.ofMinutes(15));
                            response.sendRedirect("/management/login?error");
                        }))
                .logout(logout -> logout.logoutUrl("/management/logout").logoutSuccessUrl("/management/login?logout")
                        .invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> {
                            if (request.getRequestURI().startsWith("/api/")) problem(response, 401, "Unauthorized");
                            else response.sendRedirect("/management/login");
                        })
                        .accessDeniedHandler((request, response, failure) -> problem(response, 403, "Forbidden")))
                .addFilterBefore(new LoginLimitFilter(attempts), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(guard, SecurityContextHolderFilter.class);
        return http.build();
    }

    static String accountKey(String supplied) {
        try { return "login:account:" + ManagementPolicy.email(supplied); }
        catch (IllegalArgumentException invalid) { return "login:account:invalid"; }
    }
    static String ipKey(HttpServletRequest request) { return "login:ip:" + request.getRemoteAddr(); }
    static void problem(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + "}");
    }
    private static final class LoginLimitFilter extends OncePerRequestFilter {
        private final ManagementAttemptLimiter attempts;
        private LoginLimitFilter(ManagementAttemptLimiter attempts) { this.attempts = attempts; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, IOException {
            if ("POST".equals(request.getMethod()) && "/management/login".equals(request.getRequestURI())
                    && (!attempts.allowed(accountKey(request.getParameter("email")), 5, Duration.ofMinutes(15))
                            || !attempts.allowed(ipKey(request), 20, Duration.ofMinutes(15)))) {
                response.sendRedirect("/management/login?error");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
