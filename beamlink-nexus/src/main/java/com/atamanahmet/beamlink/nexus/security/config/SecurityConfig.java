package com.atamanahmet.beamlink.nexus.security.config;

import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.ProblemDetailWriter;
import com.atamanahmet.beamlink.nexus.security.DynamicCorsFilter;
import com.atamanahmet.beamlink.nexus.security.enums.Role;
import com.atamanahmet.beamlink.nexus.security.filter.DeviceTokenFilter;
import com.atamanahmet.beamlink.nexus.security.filter.LoopbackOnlyFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN = Role.ADMIN.name();
    private static final String DEVICE = Role.DEVICE.name();
    private static final String DEVICE_PUBLIC = Role.DEVICE_PUBLIC.name();

    private static final String[] PUBLIC_ASSETS = {
            "/", "/index.html", "/assets/**",
            "/*.js", "/*.css", "/*.svg", "/vite.svg", "/h2-console/**"
    };

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/api/nexus/auth/login",
            "/api/nexus/auth/status",
            "/api/nexus/auth/setup",
            "/api/nexus/auth/reset-password",
            "/api/nexus/auth/erase"
    };

    private static final String[] UNAUTHED_DEVICE = {
            "/api/devices/register",
            "/api/devices/challenge",
            "/api/devices/token",
            "/api/devices/status",
            "/api/devices/ping",
            "/api/devices/identify",
            "/api/devices/*/exists",
            "/ws/devices"
    };

    private static final String[] DEVICE_ONLY = {
            "/api/devices/me"
    };

    private static final String[] DEVICE_PUBLIC_AND_ADMIN = {
            "/api/nexus/peers/**",
            "/api/nexus/device/**",
            "/api/transfers/**"
    };

    private static final String[] ADMIN_ONLY = {
            "/api/nexus/admin/**",
            "/api/nexus/storage/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           DeviceTokenFilter deviceTokenFilter,
                                           DynamicCorsFilter dynamicCorsFilter,
                                           LoopbackOnlyFilter loopbackOnlyFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(dynamicCorsFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(deviceTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loopbackOnlyFilter, DeviceTokenFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ASSETS).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(UNAUTHED_DEVICE).permitAll()
                        .requestMatchers(DEVICE_ONLY).hasRole(DEVICE)
                        .requestMatchers(DEVICE_PUBLIC_AND_ADMIN).hasAnyRole(DEVICE, DEVICE_PUBLIC, ADMIN)
                        .requestMatchers(ADMIN_ONLY).hasRole(ADMIN)
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
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
}