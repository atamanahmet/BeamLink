package com.atamanahmet.beamlink.nexus.security.filter;

import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.ProblemDetailWriter;
import com.atamanahmet.beamlink.nexus.security.LoopbackAddresses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LoopbackOnlyFilter extends OncePerRequestFilter {

    private final RequestMatcher restricted;

    public LoopbackOnlyFilter() {
        this.restricted = RequestMatchers.anyOf(
                PathPatternRequestMatcher.pathPattern("/api/nexus/auth/**"),
                PathPatternRequestMatcher.pathPattern("/api/nexus/admin/**"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !restricted(request);
    }

    /**
     * Rejects non-loopback callers on auth and admin paths.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!LoopbackAddresses.isLoopback(request.getRemoteAddr())) {
            ProblemDetailWriter.write(
                    response,
                    HttpStatus.FORBIDDEN,
                    "Forbidden",
                    "Forbidden",
                    "FORBIDDEN",
                    ErrorReason.UNKNOWN);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * True for /api/nexus/auth and /api/nexus/admin paths.
     */
    private boolean restricted(HttpServletRequest request) {
        return this.restricted.matches(request);
    }
}
