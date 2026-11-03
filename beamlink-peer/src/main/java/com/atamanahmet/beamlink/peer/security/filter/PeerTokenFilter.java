package com.atamanahmet.beamlink.peer.security.filter;

import com.atamanahmet.beamlink.peer.exception.ErrorReason;
import com.atamanahmet.beamlink.peer.exception.ProblemDetailWriter;
import com.atamanahmet.beamlink.peer.security.PeerTokenVerifier;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Verifies RSA-signed public token on all device-to-device transfer endpoints.
 * Source device sends its own public token + publicId in headers.
 * This filter verifies the token was signed by nexus and matches claimed identity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeerTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_PUBLIC_TOKEN = "X-Public-Token";
    private static final String HEADER_PUBLIC_ID    = "X-Public-Id";

    private final PeerTokenVerifier peerTokenVerifier;
    private final DeviceService deviceService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!isPeerEndpoint(request.getRequestURI())) {
            chain.doFilter(request, response);
        } else {
            String publicToken = request.getHeader(HEADER_PUBLIC_TOKEN);
            String publicIdHeader = request.getHeader(HEADER_PUBLIC_ID);
            if (publicToken != null && publicIdHeader != null) {
                UUID claimedPublicId;
                try {
                    claimedPublicId = UUID.fromString(publicIdHeader);
                } catch (IllegalArgumentException var9) {
                    log.warn("Invalid publicId header format: {}", publicIdHeader);
                    ProblemDetailWriter.write(
                            response,
                            HttpStatus.UNAUTHORIZED,
                            "Unauthorized",
                            "Invalid publicId format",
                            "UNAUTHORIZED",
                            ErrorReason.UNKNOWN);
                    return;
                }

                String nexusPublicKey = deviceService.getNexusPublicKey();
                if (nexusPublicKey == null) {
                    log.warn("Nexus public key not available, rejecting peer request");
                    ProblemDetailWriter.write(
                            response,
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Not approved",
                            "Not yet approved by nexus",
                            "NOT_APPROVED",
                            ErrorReason.UNKNOWN);
                } else {
                    boolean valid = peerTokenVerifier.verify(publicToken, claimedPublicId, nexusPublicKey);
                    if (!valid) {
                        log.warn("Peer token verification failed for publicId={} on {}", claimedPublicId, request.getRequestURI());
                        ProblemDetailWriter.write(
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "Unauthorized",
                                "Invalid peer token",
                                "UNAUTHORIZED",
                                ErrorReason.UNKNOWN);
                    } else {
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(claimedPublicId.toString(), (Object)null, List.of(new SimpleGrantedAuthority("ROLE_PEER"))));
                        chain.doFilter(request, response);
                    }
                }
            } else {
                log.warn("Peer request missing token headers: {}", request.getRequestURI());
                ProblemDetailWriter.write(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        "Missing peer token headers",
                        "UNAUTHORIZED",
                        ErrorReason.UNKNOWN);
            }
        }
    }

    /**
     * Only runs on peer-facing transfer endpoints.
     * UI paths are guarded by DeviceUiSecretFilter.
     */
    private boolean isPeerEndpoint(String uri) {
        return uri.startsWith("/api/transfers/");
    }


}