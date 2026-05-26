package com.atamanahmet.beamlink.nexus.security.config;

import com.atamanahmet.beamlink.nexus.security.DynamicCorsFilter;
import com.atamanahmet.beamlink.nexus.security.enums.Role;
import com.atamanahmet.beamlink.nexus.security.filter.AgentTokenFilter;
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
public class SecurityConfig {

    private static final String ADMIN = Role.ADMIN.name();
    private static final String AGENT = Role.AGENT.name();
    private static final String AGENT_PUBLIC = Role.AGENT_PUBLIC.name();

    private static final String[] PUBLIC_ASSETS = {
            "/", "/index.html", "/assets/**",
            "/*.js", "/*.css", "/*.svg", "/vite.svg", "/h2-console/**"
    };

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/api/nexus/auth/login"
    };

    private static final String[] UNAUTHED_AGENT = {
            "/api/agents/register",
            "/api/agents/status",
            "/api/agents/ping",
            "/api/agents/identify",
            "/api/agents/check-approval",
            "/api/agents/*/exists",
            "/ws/agents"
    };

    private static final String[] UPLOAD = {
            "/api/upload/check",
            "/api/upload"
    };

    private static final String[] AGENT_AND_ADMIN = {
            "/api/nexus/auth/identity"
    };

    private static final String[] AGENT_PUBLIC_AND_ADMIN = {
            "/api/nexus/peers/**",
            "/api/nexus/agent/**",
            "/api/transfers/**"
    };

    private static final String[] ADMIN_ONLY = {
            "/api/nexus/admin/**",
            "/api/nexus/storage/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AgentTokenFilter agentTokenFilter,
                                           DynamicCorsFilter dynamicCorsFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(dynamicCorsFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(agentTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ASSETS).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(UNAUTHED_AGENT).permitAll()
                        .requestMatchers(UPLOAD).hasAnyRole(AGENT, AGENT_PUBLIC)
                        .requestMatchers(AGENT_AND_ADMIN).hasAnyRole(AGENT, ADMIN)
                        .requestMatchers(AGENT_PUBLIC_AND_ADMIN).hasAnyRole(AGENT, AGENT_PUBLIC, ADMIN)
                        .requestMatchers(ADMIN_ONLY).hasRole(ADMIN)
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}