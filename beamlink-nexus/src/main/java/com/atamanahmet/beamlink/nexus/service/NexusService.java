package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Nexus;
import com.atamanahmet.beamlink.nexus.repository.NexusRepository;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusService {

    private final NexusConfig nexusConfig;
    private final NexusRepository nexusRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceTokenService deviceTokenService;

    /** In-memory cache, in sync with DB */
    private Nexus nexus;

    /** Cheap cache of token_version, cleared on bump. */
    private volatile Integer tokenVersionCache;

    @PostConstruct
    public synchronized void init() {

        nexus = nexusRepository.findById(1).orElseGet(() -> {
            Nexus n = new Nexus();
            n.setId(1);
            n.setNexusId(UUID.randomUUID());
            n.setNexusName(nexusConfig.getName());
            n.setIpAddress(nexusConfig.getIp());
            n.setPort(nexusConfig.getNexusPort());
            n.setTokenVersion(0);
            if (hasAdminSeed()) {
                n.setUsername(nexusConfig.getAdminUsername().trim());
                n.setEncodedPassword(passwordEncoder.encode(nexusConfig.getAdminPassword()));
                log.info("No nexus record found, seeding admin from NEXUS_ADMIN_* env");
            } else {
                n.setUsername("");
                n.setEncodedPassword("");
                log.info("No nexus record found, waiting for first-run password setup");
            }
            return nexusRepository.save(n);
        });
        tokenVersionCache = nexus.getTokenVersion();

        if (nexus.getPublicToken() == null || !isRsaToken(nexus.getPublicToken())) {
            nexus.setPublicToken(
                    deviceTokenService.generatePublicToken(nexus.getNexusId(), nexus.getNexusId()));
            persist();
            log.info("Nexus peer public token generated.");
        }

        log.info("Nexus loaded: name={}, nexusId={}", nexus.getNexusName(), nexus.getNexusId());
    }

    private boolean hasAdminSeed() {
        String user = nexusConfig.getAdminUsername();
        String pass = nexusConfig.getAdminPassword();
        return user != null && !user.isBlank() && pass != null && !pass.isBlank();
    }

    private synchronized void persist() {
        nexus = nexusRepository.save(nexus);
        tokenVersionCache = nexus.getTokenVersion();
    }

    /**
     * Peek at token type without verifying.
     */
    private boolean isRsaToken(String token) {
        try {
            DecodedJWT decoded = com.auth0.jwt.JWT.decode(token);
            String type = decoded.getClaim("type").asString();
            return "DEVICE_PUBLIC".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * RSA-signed token nexus uses to identify itself to other nodes as a peer.
     */
    public String getPeerPublicToken() {
        return nexus.getPublicToken();
    }

    /**
     * Updates credentials at runtime without restart.
     * Caller must have already validated currentPassword before invoking.
     */
    public synchronized void updateCredentials(String newUsername, String newEncodedPassword) {
        nexus.setUsername(newUsername);
        nexus.setEncodedPassword(newEncodedPassword);
        bumpTokenVersionLocked();
        persist();
        log.info("Nexus credentials updated. New username: {}", newUsername);
    }

    /**
     * Sets the first admin password when none exists yet.
     * Keeps token_version. Does not restart it.
     */
    public synchronized void setupCredentials(String username, String encodedPassword) {
        if (hasPassword()) {
            throw new IllegalStateException("Password already set");
        }
        nexus.setUsername(username);
        nexus.setEncodedPassword(encodedPassword);
        persist();
        log.info("Nexus first-run credentials set. Username: {}", username);
    }

    /**
     * Clears admin login so first-run setup is required again.
     * Bumps nexus.token_version so the counter keeps growing.
     */
    public synchronized void clearAdminAccount() {
        nexus.setUsername("");
        nexus.setEncodedPassword("");
        nexus.setEncodedRecoveryCode(null);
        bumpTokenVersionLocked();
        persist();
        log.info("Nexus admin account cleared. Token version {}", nexus.getTokenVersion());
    }

    /**
     * Bumps token_version so existing admin JWTs stop working.
     */
    public synchronized void bumpTokenVersion() {
        bumpTokenVersionLocked();
        persist();
        log.info("Nexus admin token version bumped to {}", nexus.getTokenVersion());
    }

    private void bumpTokenVersionLocked() {
        nexus.setTokenVersion(nexus.getTokenVersion() + 1);
        tokenVersionCache = null;
    }

    public synchronized void updateName(String newName) {
        nexus.setNexusName(newName);
        persist();
        log.info("Nexus renamed to: {}", newName);
    }

    public synchronized Nexus getNexus() {
        return nexus;
    }

    public UUID getNexusId() {
        return nexus.getNexusId();
    }

    public String getNexusIp() {
        return nexus.getIpAddress();
    }

    public Integer getNexusPort() {
        return nexus.getPort();
    }

    public String getNexusName() {
        return nexus.getNexusName();
    }

    public String getUsername() {
        return nexus.getUsername();
    }

    public String getEncodedPassword() {
        return nexus.getEncodedPassword();
    }

    /**
     * Returns true when an admin password has been configured.
     */
    public boolean hasPassword() {
        String encoded = nexus.getEncodedPassword();
        return encoded != null && !encoded.isBlank();
    }

    /**
     * Stored recovery code hash, or null when none.
     */
    public String getEncodedRecoveryCode() {
        return nexus.getEncodedRecoveryCode();
    }

    /**
     * True when a recovery code hash is stored.
     */
    public boolean hasRecoveryCode() {
        String encoded = nexus.getEncodedRecoveryCode();
        return encoded != null && !encoded.isBlank();
    }

    /**
     * Replaces the stored recovery code hash.
     */
    public synchronized void setEncodedRecoveryCode(String encodedRecoveryCode) {
        nexus.setEncodedRecoveryCode(encodedRecoveryCode);
        persist();
    }

    /**
     * Cached token_version for admin JWT checks.
     */
    public int getTokenVersion() {
        Integer cached = tokenVersionCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (tokenVersionCache == null) {
                tokenVersionCache = nexus.getTokenVersion();
            }
            return tokenVersionCache;
        }
    }

    public String generateNexusToken() {
        return deviceTokenService.generateNexusToken(nexus.getNexusId());
    }

    public String getPublicToken() {
        return nexus.getPublicToken();
    }

    public synchronized void storePublicToken(String token) {
        nexus.setPublicToken(token);
        persist();
    }
}
