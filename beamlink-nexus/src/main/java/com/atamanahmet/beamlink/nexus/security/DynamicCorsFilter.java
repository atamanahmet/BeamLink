package com.atamanahmet.beamlink.nexus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DynamicCorsFilter extends OncePerRequestFilter {

    private final DynamicCorsRegistry corsRegistry;

    public DynamicCorsFilter(DynamicCorsRegistry corsRegistry) {
        this.corsRegistry = corsRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        response.setHeader("Vary", "Origin");

        if (isAllowedOrigin(origin, request.getRemoteAddr())) {
            String allowOrigin = (origin == null || origin.isBlank()) ? "null" : origin;
            response.setHeader("Access-Control-Allow-Origin", allowOrigin);
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token, Authorization");
            response.setHeader("Access-Control-Expose-Headers", "Retry-After");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Allows null origin on loopback only. Other origins use exact match.
     */
    private boolean isAllowedOrigin(String origin, String remoteAddr) {
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return LoopbackAddresses.isLoopback(remoteAddr);
        }
        return corsRegistry.isAllowed(origin);
    }
}
