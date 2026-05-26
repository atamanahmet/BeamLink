package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.Nexus;
import com.atamanahmet.beamlink.nexus.dto.ChangeCredentialsRequest;
import com.atamanahmet.beamlink.nexus.dto.LoginRequest;
import com.atamanahmet.beamlink.nexus.dto.NexusMeResponse;
import com.atamanahmet.beamlink.nexus.service.NexusAuthService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/nexus/auth")
@RequiredArgsConstructor
public class NexusAuthController {

    private final NexusAuthService nexusAuthService;
    private final NexusService nexusService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.username());

        if (!nexusAuthService.validateCredentials(request.username(), request.password())) {
            log.warn("Failed login attempt for user: {}", request.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        String token = nexusAuthService.generateAdminToken(request.username());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Nexus nexus = nexusService.getNexus();
        return ResponseEntity.ok(new NexusMeResponse(
                nexus.getNexusId(),
                nexus.getNexusName(),
                nexus.getIpAddress(),
                nexus.getPort(),
                nexus.getUsername()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-credentials")
    public ResponseEntity<?> changeCredentials(@RequestBody ChangeCredentialsRequest request) {
        try {
            nexusAuthService.changeCredentials(
                    request.currentPassword(),
                    request.newUsername(),
                    request.newPassword()
            );
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}