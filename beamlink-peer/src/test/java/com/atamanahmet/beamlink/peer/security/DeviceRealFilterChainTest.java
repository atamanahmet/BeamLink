package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.security.enums.TokenType;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Real UI secret and peer token filters on the device chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceRealFilterChainTest {

    private static final String SECRET = "test-ui-secret-for-context-load";
    private static final Path runtimeDir;
    private static final Path dbFile;
    private static final RSAPublicKey rsaPublic;
    private static final RSAPrivateKey rsaPrivate;
    private static final String nexusPublicKeyB64;
    private static final UUID peerPublicId = UUID.randomUUID();
    private static final String peerJwt;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-peer-real-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/peer-real.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            rsaPublic = (RSAPublicKey) pair.getPublic();
            rsaPrivate = (RSAPrivateKey) pair.getPrivate();
            nexusPublicKeyB64 = Base64.getEncoder().encodeToString(rsaPublic.getEncoded());
            peerJwt = JWT.create()
                    .withIssuer("beamlink-nexus")
                    .withClaim("type", TokenType.DEVICE_PUBLIC.name())
                    .withClaim("publicId", peerPublicId.toString())
                    .sign(Algorithm.RSA256(rsaPublic, rsaPrivate));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Points the test context at a temp sqlite file and UI secret.
     */
    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
        registry.add("PEER_UI_SECRET", () -> SECRET);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DeviceService deviceService;

    /**
     * Stores the test nexus public key on the device.
     */
    @BeforeEach
    void storeNexusPublicKey() {
        deviceService.applyIssuedTokens(
                UUID.randomUUID(),
                "test-device",
                "auth-token",
                "public-token",
                peerPublicId,
                nexusPublicKeyB64);
    }

    /**
     * Sets the remote address on the mock request.
     */
    private static RequestPostProcessor remoteAddr(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    /**
     * Correct UI secret on status is not 401 or 403.
     */
    @Test
    void correctUiSecretOnStatusIsNotAuthError() throws Exception {
        int status = mvc.perform(get("/api/device/status").header(DeviceUiSecretFilter.HEADER, SECRET))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }

    /**
     * Wrong UI secret on status is 401.
     */
    @Test
    void wrongUiSecretOnStatusIsUnauthorized() throws Exception {
        int status = mvc.perform(get("/api/device/status").header(DeviceUiSecretFilter.HEADER, "wrong"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isEqualTo(401);
    }

    /**
     * Correct UI secret from a non-loopback address is 403.
     */
    @Test
    void correctUiSecretFromNonLoopbackIsForbidden() throws Exception {
        int status = mvc.perform(get("/api/device/status")
                        .header(DeviceUiSecretFilter.HEADER, SECRET)
                        .with(remoteAddr("192.168.1.20")))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isEqualTo(403);
    }

    /**
     * Transfer offset with UI secret and no peer headers is 401.
     */
    @Test
    void transferOffsetWithoutPeerHeadersIsUnauthorized() throws Exception {
        int status = mvc.perform(get("/api/transfers/" + UUID.randomUUID() + "/offset")
                        .header(DeviceUiSecretFilter.HEADER, SECRET))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isEqualTo(401);
    }

    /**
     * Valid peer token on UI status with no secret is 401.
     */
    @Test
    void validPeerTokenOnStatusWithoutSecretIsUnauthorized() throws Exception {
        int status = mvc.perform(get("/api/device/status")
                        .header("X-Public-Token", peerJwt)
                        .header("X-Public-Id", peerPublicId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isEqualTo(401);
    }

    /**
     * Valid peer token on transfer offset is not 401 or 403.
     */
    @Test
    void validPeerTokenOnTransferOffsetIsNotAuthError() throws Exception {
        int status = mvc.perform(get("/api/transfers/" + UUID.randomUUID() + "/offset")
                        .header("X-Public-Token", peerJwt)
                        .header("X-Public-Id", peerPublicId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }

    /**
     * Encoded UI status path with a correct secret is not 2xx.
     */
    @Test
    void encodedDeviceStatusIsNot2xx() throws Exception {
        int status = mvc.perform(get(URI.create("/api/%61gent/status"))
                        .header(DeviceUiSecretFilter.HEADER, SECRET))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status < 200 || status > 299).isTrue();
    }
}
