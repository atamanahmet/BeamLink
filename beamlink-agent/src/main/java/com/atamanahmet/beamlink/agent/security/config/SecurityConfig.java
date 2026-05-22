package com.atamanahmet.beamlink.agent.security.config;

import com.atamanahmet.beamlink.agent.security.AgentUiTokenFilter;
import com.atamanahmet.beamlink.agent.security.filter.PeerTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AgentUiTokenFilter agentUiTokenFilter;
    private final PeerTokenFilter peerTokenFilter;

    private static final String[] PUBLIC_ASSETS = {
            "/", "/index.html", "/assets/**", "/static/**", "/favicon.ico", "/error"
    };

    private static final String[] PUBLIC_AUTH = {
            "/api/agent/auth/login",
            "/api/agent/auth/logout",
            "/api/agent/auth/me",
            "/api/agent/auth/status",
            "/api/agent/auth/register"
    };

    private static final String[] NEXUS_FACING = {
            "/api/approval",
            "/api/agents/register",
            "/api/agents/status",
            "/api/agents/check-approval",
            "/api/agent/events"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(agentUiTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(peerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ASSETS).permitAll()
                        .requestMatchers(PUBLIC_AUTH).permitAll()
                        .requestMatchers(NEXUS_FACING).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/agent/**", config);
        source.registerCorsConfiguration("/api/transfers/**", config);
        return source;
    }
}