package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.exception.ErrorReason;
import com.atamanahmet.beamlink.peer.exception.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Guards UI-only device API paths with a per-launch secret and loopback check.
 */
@Component
public class DeviceUiSecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-UI-Secret";

    @Value("${PEER_UI_SECRET}")
    private String uiSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/device");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        if (!LoopbackAddresses.isLoopback(request.getRemoteAddr())) {
            ProblemDetailWriter.write(
                    response,
                    HttpStatus.FORBIDDEN,
                    "Forbidden",
                    "Loopback only",
                    "FORBIDDEN",
                    ErrorReason.UNKNOWN);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null || !secretsEqual(provided, uiSecret)) {
            ProblemDetailWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Invalid UI secret",
                    "UNAUTHORIZED",
                    ErrorReason.UNKNOWN);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "device-ui",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_UI"))
                )
        );
        chain.doFilter(request, response);
    }

    /**
     * Compares secrets in constant time.
     */
    static boolean secretsEqual(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        byte[] a = provided.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
