package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.security.enums.TokenType;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies peer public tokens offline using nexus RSA public key.
 * No nexus call needed. Called before accepting any transfer from a peer.
 */
@Slf4j
@Component
public class PeerTokenVerifier {

    private static final String ISSUER         = "beamlink-nexus";
    private static final String CLAIM_TYPE     = "type";
    private static final String CLAIM_PUBLIC_ID = "publicId";

    /**
     * Verifies the public token and returns the caller identity.
     */
    public Optional<PeerPrincipal> verify(String publicToken, UUID claimedPublicId, String nexusPublicKeyBase64) {
        if (publicToken == null || claimedPublicId == null || nexusPublicKeyBase64 == null) {
            log.warn("Peer verification failed: missing token, publicId, or nexus public key");
            return Optional.empty();
        }

        try {
            RSAPublicKey publicKey = loadPublicKey(nexusPublicKeyBase64);
            Algorithm algorithm   = Algorithm.RSA256(publicKey, null);

            DecodedJWT decoded = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(publicToken);

            String tokenType = decoded.getClaim(CLAIM_TYPE).asString();
            if (!TokenType.DEVICE_PUBLIC.name().equals(tokenType)) {
                log.warn("Peer verification failed: wrong token type '{}'", tokenType);
                return Optional.empty();
            }

            String tokenPublicId = decoded.getClaim(CLAIM_PUBLIC_ID).asString();
            if (!claimedPublicId.toString().equals(tokenPublicId)) {
                log.warn("Peer verification failed: publicId mismatch. claimed={}, token={}",
                        claimedPublicId, tokenPublicId);
                return Optional.empty();
            }

            String subject = decoded.getSubject();
            if (subject == null || subject.isBlank()) {
                log.warn("Peer verification failed: missing subject");
                return Optional.empty();
            }
            UUID deviceId;
            try {
                deviceId = UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                log.warn("Peer verification failed: invalid subject");
                return Optional.empty();
            }

            return Optional.of(new PeerPrincipal(tokenPublicId, deviceId));

        } catch (JWTVerificationException e) {
            log.warn("Peer token verification failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error during peer token verification", e);
            return Optional.empty();
        }
    }

    private RSAPublicKey loadPublicKey(String base64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(keyBytes));
    }
}
