package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.JwtSecretConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class AgentAuthService {

    private final AgentService agentService;
    private final JwtSecretConfig jwtSecretConfig;
    private final PasswordEncoder passwordEncoder;

    public boolean isRegistered() {

        return agentService.isRegistered();
    }

    public void register(String username, String password) {
        if (agentService.isRegistered()) {
            throw new IllegalStateException("Already registered.");
        } else if (username != null && !username.isBlank()) {
            if (password != null && password.length() >= 6) {
                agentService.updateCredentials(username, passwordEncoder.encode(password));
                agentService.markRegistered();
            } else {
                throw new IllegalArgumentException("Password must be at least 6 characters.");
            }
        } else {
            throw new IllegalArgumentException("Username cannot be blank.");
        }
    }

    public boolean validateCredentials(String username, String password) {
        return username.equals(agentService.getUsername()) && passwordEncoder.matches(password, agentService.getEncodedPassword());
    }

    public String generateToken(String username) {
        return JWT.create().withSubject(username).withClaim("scope", "agent-ui").withIssuedAt(new Date()).sign(Algorithm.HMAC512(jwtSecretConfig.getResolvedSecret()));
    }

    public boolean validateToken(String token) {
        try {
            JWT.require(Algorithm.HMAC512(jwtSecretConfig.getResolvedSecret())).build().verify(token);
            return true;
        } catch (JWTVerificationException var3) {
            return false;
        }
    }

    public void changeCredentials(String currentPassword, String newUsername, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, agentService.getEncodedPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        } else if (newUsername != null && !newUsername.isBlank()) {
            if (newPassword != null && newPassword.length() >= 6) {
                agentService.updateCredentials(newUsername, passwordEncoder.encode(newPassword));
            } else {
                throw new IllegalArgumentException("Password must be at least 6 characters.");
            }
        } else {
            throw new IllegalArgumentException("Username cannot be blank.");
        }
    }

    public Agent getAgent() {
        return agentService.getAgent();
    }

    public String getPublicToken() {
        return agentService.getPublicToken();
    }
}
