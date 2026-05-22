package com.atamanahmet.beamlink.agent.security;

import com.atamanahmet.beamlink.agent.service.AgentAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentUiTokenFilter extends OncePerRequestFilter {

    private final AgentAuthService agentAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = request.getHeader("X-Auth-Token");

        if (token != null && agentAuthService.validateToken(token)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "agent-ui",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_UI"))
                    )
            );
        }

        chain.doFilter(request, response);
    }
}