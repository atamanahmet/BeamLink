package com.atamanahmet.beamlink.nexus.config;

import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.service.SettingsService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * RSA keypair resolution strategy:
 * 1. Both keys must exist in DB together, partial state is invalid, regenerate
 * 2. If missing, generate 2048-bit RSA pair and persist both
 * 3. Private key never leaves this class over the network
 * 4. Public key is exposed via getPublicKeyBase64() for device distribution
 */
@Slf4j
@Component
@Getter
@RequiredArgsConstructor
public class RsaKeyConfig {

    private final SettingsService settingsService;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String publicKeyBase64;

    /**
     * Resolves public and private RSA, Both must exist.
     * If one is missing (corrupted state), regenerate both.
     * Devices will need to re-register if keys are regenerated
     */
    @PostConstruct
    public void resolve() {

        Optional<String> privateSetting = settingsService.get(SettingKey.RSA_PRIVATE_KEY);
        Optional<String> publicSetting  = settingsService.get(SettingKey.RSA_PUBLIC_KEY);


        if (privateSetting.isPresent() && publicSetting.isPresent()) {
            loadFromDb(privateSetting.get(), publicSetting.get());
        } else {
            log.warn("RSA keypair missing or incomplete, generating new pair");
            generateAndPersist();
        }
    }

    private void loadFromDb(String privateBase64, String publicBase64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");

            byte[] privateBytes = Base64.getDecoder().decode(privateBase64);
            byte[] publicBytes  = Base64.getDecoder().decode(publicBase64);

            this.privateKey     = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            this.publicKey      = (RSAPublicKey)  factory.generatePublic(new X509EncodedKeySpec(publicBytes));
            this.publicKeyBase64 = publicBase64;

            log.info("RSA keypair loaded from DB");
        } catch (Exception e) {
            log.error("Failed to load RSA keypair from DB, regenerating", e);
            generateAndPersist();
        }
    }

    private void generateAndPersist() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();

            this.privateKey      = (RSAPrivateKey) pair.getPrivate();
            this.publicKey       = (RSAPublicKey)  pair.getPublic();
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

            String privateBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());

            settingsService.set(SettingKey.RSA_PRIVATE_KEY, privateBase64);
            settingsService.set(SettingKey.RSA_PUBLIC_KEY,  publicKeyBase64);

            log.info("RSA keypair generated and persisted");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA keypair", e);
        }
    }
}