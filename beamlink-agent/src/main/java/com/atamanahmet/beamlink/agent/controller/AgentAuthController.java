package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.dto.AgentLoginResponse;
import com.atamanahmet.beamlink.agent.dto.AgentMeResponse;
import com.atamanahmet.beamlink.agent.dto.ChangeCredentialsRequest;
import com.atamanahmet.beamlink.agent.dto.LoginRequest;
import com.atamanahmet.beamlink.agent.dto.RegisterRequest;
import com.atamanahmet.beamlink.agent.service.AgentAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/auth")
@RequiredArgsConstructor
public class AgentAuthController {

    private final AgentAuthService agentAuthService;

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("registered", agentAuthService.isRegistered()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            agentAuthService.register(request.username(), request.password());
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (!agentAuthService.validateCredentials(request.username(), request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        String token = agentAuthService.generateToken(request.username());
        Agent agent = agentAuthService.getAgent();

        return ResponseEntity.ok(new AgentLoginResponse(
                token,
                agent.getAgentId(),
                agent.getAgentName(),
                agent.getState() != null ? agent.getState().name() : "",
                agent.getPublicToken() != null ? agent.getPublicToken() : "",
                agent.getAuthToken() != null ? agent.getAuthToken() : ""
        ));
    }

    /**
     * Returns current agent info if session token is valid.
     * publicToken null means agent is not approved by nexus yet.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Agent agent = agentAuthService.getAgent();

        if (agent.getPublicToken() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new AgentMeResponse(
                agent.getAgentId(),
                agent.getAgentName(),
                agent.getState() != null ? agent.getState().name() : "",
                agent.getPublicToken(),
                agent.getAuthToken() != null ? agent.getAuthToken() : ""
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-credentials")
    public ResponseEntity<?> changeCredentials(@RequestBody ChangeCredentialsRequest request) {
        try {
            agentAuthService.changeCredentials(request.currentPassword(), request.newUsername(), request.newPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}