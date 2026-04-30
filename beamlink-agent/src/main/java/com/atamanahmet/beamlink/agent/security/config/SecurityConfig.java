package com.atamanahmet.beamlink.agent.security.config;

import com.atamanahmet.beamlink.agent.security.AgentUiTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AgentUiTokenFilter agentUiTokenFilter;

    private static final String[] PUBLIC_ASSETS = {
            "/", "/index.html", "/assets/**", "/static/**", "/favicon.ico", "/error"
    };

    private static final String[] PUBLIC_AUTH = {
            "/api/auth/login", "/api/auth/logout", "/api/auth/me"
    };

    private static final String[] AGENT_TO_AGENT = {
            "/api/ping",
            "/api/upload/check",
            "/api/upload",
            "/api/update/receive",
            "/api/transfers/receive",
            "/api/transfers/*/chunk",
            "/api/transfers/*/offset",
            "/api/transfers/*/resume"
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
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(agentUiTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ASSETS).permitAll()
                        .requestMatchers(PUBLIC_AUTH).permitAll()
                        .requestMatchers(AGENT_TO_AGENT).permitAll()
                        .requestMatchers(NEXUS_FACING).permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}