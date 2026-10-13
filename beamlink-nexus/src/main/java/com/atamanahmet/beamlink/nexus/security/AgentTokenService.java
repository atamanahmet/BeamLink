package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.config.JwtConfig;
import com.atamanahmet.beamlink.nexus.config.JwtSecretConfig;
import com.atamanahmet.beamlink.nexus.config.RsaKeyConfig;
import com.atamanahmet.beamlink.nexus.security.enums.TokenType;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@DependsOn({"jwtSecretConfig", "rsaKeyConfig"})
public class AgentTokenService {

    private static final String ISSUER = "beamlink-nexus";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_PUBLIC_ID = "publicId";
    private final Algorithm hmacAlgorithm;
    private final Algorithm rsaAlgorithm;
    private final JwtConfig jwtConfig;

    public AgentTokenService(JwtConfig jwtConfig, JwtSecretConfig jwtSecretConfig, RsaKeyConfig rsaKeyConfig) {
        this.jwtConfig = jwtConfig;
        this.hmacAlgorithm = Algorithm.HMAC512(jwtSecretConfig.getResolvedSecret());
        this.rsaAlgorithm = Algorithm.RSA256(rsaKeyConfig.getPublicKey(), rsaKeyConfig.getPrivateKey());
    }

    public String generateAdminToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAdminExpirationMinutes(), ChronoUnit.MINUTES);

        return JWT.create()
                .withIssuer("beamlink-nexus")
                .withSubject(username)
                .withClaim("type", TokenType.ADMIN.name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(this.hmacAlgorithm);
    }

    public String generateNexusToken(UUID nexusId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(this.jwtConfig.getAgentAuthExpirationDays(), ChronoUnit.DAYS);
        return JWT.create()
                .withIssuer("beamlink-nexus")
                .withSubject(nexusId.toString())
                .withClaim("type", TokenType.NEXUS.name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(this.hmacAlgorithm);
    }

    public String generateAuthToken(UUID agentId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAgentAuthExpirationDays(), ChronoUnit.DAYS);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(agentId.toString())
                .withAudience("agent")
                .withClaim(CLAIM_TYPE, TokenType.AGENT_AUTH.name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(this.hmacAlgorithm);
    }

    // publicId is stored in DB and embedded in the token as a stable peer identity reference
    public String generatePublicToken(UUID agentId, UUID publicId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAgentPublicExpirationDays(), ChronoUnit.DAYS);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(agentId.toString())
                .withAudience("agent")
                .withClaim(CLAIM_TYPE, TokenType.AGENT_PUBLIC.name())
                .withClaim(CLAIM_PUBLIC_ID, publicId.toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(this.rsaAlgorithm);
    }

    public boolean validateToken(String token) {
        try {
            Algorithm algo = this.resolveAlgorithm(token);
            JWT.require(algo).withIssuer("beamlink-nexus").build().verify(token);
            return true;
        } catch (JWTVerificationException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public UUID extractAgentId(String token) {
        DecodedJWT decoded = decode(token);
        TokenType type = extractTokenType(decoded);
        if (type == TokenType.ADMIN) {
            throw new InvalidTokenException("Admin token cannot be used as agent token", null);
        }
        return UUID.fromString(decoded.getSubject());
    }

    public UUID extractPublicId(String token) {
        String value = decode(token).getClaim(CLAIM_PUBLIC_ID).asString();
        if (value == null) {
            throw new InvalidTokenException("Token does not contain publicId claim", null);
        }
        return UUID.fromString(value);
    }

    public String extractSubject(String token) {
        return decode(token).getSubject();
    }

    public TokenType extractTokenType(String token) {
        return extractTokenType(decode(token));
    }

    private TokenType extractTokenType(DecodedJWT decoded) {
        return TokenType
                .valueOf(decoded.getClaim("type")
                        .asString());
    }

    private DecodedJWT decode(String token) {
        try {
            Algorithm algo = this.resolveAlgorithm(token);
            return JWT
                    .require(algo)
                    .withIssuer("beamlink-nexus")
                    .build()
                    .verify(token);

        } catch (JWTVerificationException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }

    private Algorithm resolveAlgorithm(String token) {
        try {
            DecodedJWT peeked = JWT.decode(token);
            String typeValue = peeked.getClaim("type").asString();
            TokenType type = TokenType.valueOf(typeValue);
            return type == TokenType.AGENT_PUBLIC ? this.rsaAlgorithm : this.hmacAlgorithm;
        } catch (Exception var5) {
            return this.hmacAlgorithm;
        }
    }
}