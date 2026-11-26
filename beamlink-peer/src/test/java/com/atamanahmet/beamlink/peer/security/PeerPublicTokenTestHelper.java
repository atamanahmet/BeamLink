package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.security.enums.TokenType;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

/**
 * Builds RSA keys and DEVICE_PUBLIC JWTs for peer token tests.
 */
public final class PeerPublicTokenTestHelper {

    public final RSAPublicKey publicKey;
    public final RSAPrivateKey privateKey;
    public final String nexusPublicKeyB64;

    /**
     * Creates RSA keys for signing test public tokens.
     */
    public PeerPublicTokenTestHelper() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            nexusPublicKeyB64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Signs a DEVICE_PUBLIC token with subject and publicId claims.
     */
    public String sign(UUID deviceId, UUID publicId) {
        var builder = JWT.create()
                .withIssuer("beamlink-nexus")
                .withClaim("type", TokenType.DEVICE_PUBLIC.name())
                .withClaim("publicId", publicId.toString());
        if (deviceId != null) {
            builder = builder.withSubject(deviceId.toString());
        }
        return builder.sign(Algorithm.RSA256(publicKey, privateKey));
    }

    /**
     * Signs a DEVICE_PUBLIC token with a raw subject string.
     */
    public String signWithRawSubject(String subject, UUID publicId) {
        var builder = JWT.create()
                .withIssuer("beamlink-nexus")
                .withClaim("type", TokenType.DEVICE_PUBLIC.name())
                .withClaim("publicId", publicId.toString());
        if (subject != null) {
            builder = builder.withSubject(subject);
        }
        return builder.sign(Algorithm.RSA256(publicKey, privateKey));
    }
}
