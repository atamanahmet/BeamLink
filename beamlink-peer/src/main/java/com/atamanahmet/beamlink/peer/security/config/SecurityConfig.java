package com.atamanahmet.beamlink.peer.security.config;

import com.atamanahmet.beamlink.peer.exception.ErrorReason;
import com.atamanahmet.beamlink.peer.exception.ProblemDetailWriter;
import com.atamanahmet.beamlink.peer.security.DeviceUiSecretFilter;
import com.atamanahmet.beamlink.peer.security.filter.PeerTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DeviceUiSecretFilter deviceUiSecretFilter;
    private final PeerTokenFilter peerTokenFilter;

    /**
     * Public matchers that must stay open (not UI-secret).
     */
    public static final String[] PUBLIC_MATCHERS = {
            "/", "/index.html", "/assets/**", "/static/**", "/favicon.ico", "/error",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(deviceUiSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(peerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_MATCHERS).permitAll()
                        .requestMatchers("/api/device/**").hasRole("UI")
                        .requestMatchers("/api/transfers/**").hasRole("PEER")
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                ProblemDetailWriter.write(
                                        res,
                                        HttpStatus.UNAUTHORIZED,
                                        "Unauthorized",
                                        "Authentication required",
                                        "UNAUTHORIZED",
                                        ErrorReason.UNKNOWN))
                        .accessDeniedHandler((req, res, ex) ->
                                ProblemDetailWriter.write(
                                        res,
                                        HttpStatus.FORBIDDEN,
                                        "Forbidden",
                                        "Access denied",
                                        "FORBIDDEN",
                                        ErrorReason.UNKNOWN)));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("null");
        config.addAllowedOrigin("http://localhost:5174");
        config.addAllowedOrigin("http://127.0.0.1:5174");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/device/**", config);
        source.registerCorsConfiguration("/api/transfers/**", config);
        return source;
    }
}
