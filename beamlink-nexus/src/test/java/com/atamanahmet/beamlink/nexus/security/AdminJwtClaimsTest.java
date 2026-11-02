package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.config.JwtSecretConfig;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import com.atamanahmet.beamlink.nexus.security.enums.TokenType;
import com.atamanahmet.beamlink.nexus.security.filter.DeviceTokenFilter;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AdminJwtClaimsTest {

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private JwtSecretConfig jwtSecretConfig;

    @Autowired
    private DeviceTokenFilter deviceTokenFilter;

    @Test
    void token365HasExp365DaysAhead() {
        Instant before = Instant.now();
        String token = deviceTokenService.generateAdminToken("admin", 0, 365);
        Instant after = Instant.now();
        Date exp = JWT.decode(token).getExpiresAt();
        assertNotNull(exp);
        Instant expInstant = exp.toInstant();
        assertTrue(!expInstant.isBefore(before.plus(365, ChronoUnit.DAYS).minusSeconds(5)));
        assertTrue(!expInstant.isAfter(after.plus(365, ChronoUnit.DAYS).plusSeconds(5)));
    }

    @Test
    void tokenWithoutExpRejected() throws Exception {
        String token = JWT.create()
                .withIssuer("beamlink-nexus")
                .withSubject("admin")
                .withClaim("type", TokenType.ADMIN.name())
                .withClaim("tv", 0)
                .withIssuedAt(new Date())
                .sign(Algorithm.HMAC512(jwtSecretConfig.getResolvedSecret()));

        assertThrows(InvalidTokenException.class, () -> deviceTokenService.extractSubject(token));

        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Token", token);
        deviceTokenFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenWithoutTvRejected() throws Exception {
        Instant now = Instant.now();
        String token = JWT.create()
                .withIssuer("beamlink-nexus")
                .withSubject("admin")
                .withClaim("type", TokenType.ADMIN.name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(1, ChronoUnit.DAYS)))
                .sign(Algorithm.HMAC512(jwtSecretConfig.getResolvedSecret()));

        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Token", token);
        deviceTokenFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
