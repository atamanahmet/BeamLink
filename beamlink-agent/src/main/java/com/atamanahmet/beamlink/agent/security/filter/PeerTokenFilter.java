package com.atamanahmet.beamlink.agent.security.filter;

import com.atamanahmet.beamlink.agent.security.PeerTokenVerifier;
import com.atamanahmet.beamlink.agent.service.AgentService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Verifies RSA-signed public token on all agent-to-agent transfer endpoints.
 * Source agent sends its own public token + publicId in headers.
 * This filter verifies the token was signed by nexus and matches claimed identity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeerTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_PUBLIC_TOKEN = "X-Public-Token";
    private static final String HEADER_PUBLIC_ID    = "X-Public-Id";

    private final PeerTokenVerifier peerTokenVerifier;
    private final AgentService agentService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!isPeerEndpoint(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String publicToken    = request.getHeader(HEADER_PUBLIC_TOKEN);
        String publicIdHeader = request.getHeader(HEADER_PUBLIC_ID);

        if (publicToken == null || publicIdHeader == null) {
            log.warn("Peer request missing token headers: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing peer token headers");
            return;
        }

        UUID claimedPublicId;
        try {
            claimedPublicId = UUID.fromString(publicIdHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid publicId header format: {}", publicIdHeader);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid publicId format");
            return;
        }

        String nexusPublicKey = agentService.getNexusPublicKey();
        if (nexusPublicKey == null) {
            log.warn("Nexus public key not available, rejecting peer request");
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Not yet approved by nexus");
            return;
        }

        boolean valid = peerTokenVerifier.verify(publicToken, claimedPublicId, nexusPublicKey);
        if (!valid) {
            log.warn("Peer token verification failed for publicId={} on {}",
                    claimedPublicId, request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid peer token");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        claimedPublicId.toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PEER"))
                )
        );

        chain.doFilter(request, response);
    }

    /**
     * Only runs on peer-facing transfer and ping endpoints.
     * UI and nexus-facing endpoints handled by AgentUiTokenFilter.
     */
    private boolean isPeerEndpoint(String uri) {
        return uri.startsWith("/api/transfers/")
                || uri.equals("/api/ping")
                || uri.equals("/api/upload")
                || uri.equals("/api/upload/check");
    }


}