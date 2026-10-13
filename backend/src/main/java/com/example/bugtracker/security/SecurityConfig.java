package com.example.bugtracker.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT bearer-token security.
 *
 * <p>Why CSRF is disabled: CSRF protection exists because browsers attach cookies
 * to cross-site requests automatically, so an attacker can ride the user's
 * session. We send a Bearer token in an {@code Authorization} header instead —
 * the browser never attaches that on its own, so there is nothing to ride.
 *
 * <p>Authorization beyond "is this a valid token" lives in the service layer
 * ({@code AccessControlService}), not here — see the plan, §10.4.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The resource-server filter chain. Anything not explicitly permitted needs
     * a valid, non-expired Bearer token or gets 401.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // Container probe. Public by design and therefore deliberately
                        // returns nothing but a coarse UP/DOWN — see HealthController.
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** Verifies the same secret {@link JwtService} signs with. */
    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        byte[] secret = jwtService.secretBytes();
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    /**
     * BCrypt at cost 12. Deliberately not MD5/SHA — those are too fast to resist
     * offline brute force.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CORS at the security filter level, not per-controller.
     *
     * <p>Origins come from {@code app.cors.allowed-origins} rather than being hardcoded,
     * because the default has to change with the deployment. The deceptively important
     * part: Spring's {@code CorsFilter} rejects <em>any</em> request carrying an
     * {@code Origin} header that is not allowlisted — it returns 403 before the request
     * ever reaches a controller — and browsers send {@code Origin} on every POST, PUT,
     * PATCH and DELETE, <em>including same-origin ones</em>. So a hardcoded
     * localhost-only allowlist makes every write fail with 403 the moment the app is
     * served from any other hostname, even though nginx is proxying the call
     * same-origin. Local development hid this completely: the Vite dev server genuinely
     * is localhost:5173, so it was the one origin that happened to be allowed.
     *
     * <p>Patterns rather than plain origins because PaaS hosts are generated
     * ({@code https://<service>-production.up.railway.app}) and change on redeploy; a
     * {@code https://*.up.railway.app} pattern survives that. Note this is a list of
     * patterns, not {@code *}: {@code allowCredentials} is enabled, and the browser
     * refuses wildcarded credentialed origins anyway.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOriginPatterns) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}