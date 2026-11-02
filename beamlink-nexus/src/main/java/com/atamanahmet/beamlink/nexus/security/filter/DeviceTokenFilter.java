package com.atamanahmet.beamlink.nexus.security.filter;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import com.atamanahmet.beamlink.nexus.security.AdminTokenVersionRule;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import com.atamanahmet.beamlink.nexus.security.enums.Role;
import com.atamanahmet.beamlink.nexus.security.enums.TokenType;
import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import com.atamanahmet.beamlink.nexus.exception.ProblemDetailWriter;
import com.atamanahmet.beamlink.nexus.service.NexusService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTokenFilter extends OncePerRequestFilter {

    private final DeviceTokenService deviceTokenService;
    private final DeviceService deviceService;
    private final NexusService nexusService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                processToken(token);
            }
        } catch (InvalidTokenException e) {
            log.warn("Token processing failed", e);
            ProblemDetailWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Invalid token",
                    "UNAUTHORIZED",
                    ErrorReason.UNKNOWN);
            return;
        }

        chain.doFilter(request, response);
    }

    private void processToken(String token) {
        TokenType tokenType = deviceTokenService.extractTokenType(token);

        switch (tokenType) {
            case ADMIN -> setAdminAuthentication(token);
            case DEVICE_AUTH -> setDeviceAuthAuthentication(token);
            case DEVICE_PUBLIC -> setDevicePublicAuthentication(token);
            case NEXUS -> setNexusAuthentication(token);
        }
    }

    /**
     * Extracts JWT from auth headers only.
     */
    private String extractToken(HttpServletRequest request) {
        String authToken = request.getHeader("X-Auth-Token");
        return authToken != null ? authToken : request.getHeader("X-Public-Token");
    }

    /**
     * Sets admin authentication when tv matches the Nexus row.
     */
    private void setAdminAuthentication(String token) {
        Integer claimTv = deviceTokenService.extractTokenVersion(token);
        if (claimTv == null) {
            throw new InvalidTokenException("Token does not contain tv claim", null);
        }
        if (!AdminTokenVersionRule.matches(claimTv, nexusService.getTokenVersion())) {
            log.warn("Admin token rejected: token version mismatch");
            return;
        }

        String subject = deviceTokenService.extractSubject(token);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        subject,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.ADMIN.getAuthority()))
                )
        );
    }

    private void setNexusAuthentication(String token) {
        UUID claimedId = UUID.fromString(deviceTokenService.extractSubject(token));

        if (!claimedId.equals(nexusService.getNexusId())) {
            log.warn("Nexus token subject {} does not match known nexus id", claimedId);
        } else {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            claimedId,
                            null,
                            List.of(new SimpleGrantedAuthority(Role.DEVICE.getAuthority()))));
        }
    }

    /**
     * Sets device authentication after verifying token and device state.
     */
    private void setDeviceAuthAuthentication(String token) {
        UUID deviceId = deviceTokenService.extractDeviceId(token);

        Device device = deviceService.findOptional(deviceId).orElse(null);
        if (device == null) {
            log.warn("Auth token references unknown device: {}", deviceId);
            return;
        }

        if (device.getState() != DeviceState.APPROVED) {
            log.warn("Auth token rejected for non-approved device: {}", deviceId);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        deviceId,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.DEVICE.getAuthority()))
                )
        );
    }

    private void setDevicePublicAuthentication(String token) {
        UUID publicId = deviceTokenService.extractPublicId(token);

        Device device = deviceService.findByPublicId(publicId).orElse(null);
        if (device == null) {
            log.warn("Public token references unknown publicId: {}", publicId);
            return;
        }

        if (device.getState() != DeviceState.APPROVED) {
            log.warn("Public token rejected for non-approved device: {}", device.getId());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        device.getId(),
                        null,
                        List.of(new SimpleGrantedAuthority(Role.DEVICE_PUBLIC.getAuthority()))
                )
        );
    }
}
