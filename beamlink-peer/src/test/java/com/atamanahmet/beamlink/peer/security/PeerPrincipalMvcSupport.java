package com.atamanahmet.beamlink.peer.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;

import java.util.List;

/**
 * Wires PeerPrincipal into standalone MockMvc for inbound tests.
 */
public final class PeerPrincipalMvcSupport {

    private PeerPrincipalMvcSupport() {
    }

    /**
     * Sets the security principal and registers the principal resolver.
     */
    public static StandaloneMockMvcBuilder withPrincipal(
            StandaloneMockMvcBuilder builder, PeerPrincipal principal) {
        if (principal == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_PEER"))));
        }
        return builder.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver());
    }
}
