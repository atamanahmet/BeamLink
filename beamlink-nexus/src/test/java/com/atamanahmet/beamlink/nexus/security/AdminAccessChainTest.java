package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.repository.DeviceRepository;
import com.atamanahmet.beamlink.nexus.service.NexusAuthService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.websocket.DeviceHandshakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.socket.WebSocketHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAccessChainTest {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-nexus-admin-access-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/nexus-admin-access.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
        registry.add("nexus.tls.port", () -> 17477);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NexusService nexusService;

    @Autowired
    private NexusAuthService nexusAuthService;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceHandshakeInterceptor handshakeInterceptor;

    private String adminToken;
    private String deviceAuthToken;
    private String devicePublicToken;

    @BeforeEach
    void tokens() {
        if (!nexusService.hasPassword()) {
            nexusAuthService.setup("admin-access", "adminpass12");
        }
        adminToken = nexusAuthService.generateAdminToken(nexusService.getUsername(), "1");

        Device device = deviceRepository.save(Device.builder()
                .name("access-device")
                .ipAddress("10.0.0.40")
                .port(9090)
                .state(DeviceState.APPROVED)
                .publicId(UUID.randomUUID())
                .registeredAt(Instant.now())
                .approvedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build());
        deviceAuthToken = deviceTokenService.generateAuthToken(device.getId());
        devicePublicToken = deviceTokenService.generatePublicToken(device.getId(), device.getPublicId());
    }

    @Test
    void settingsRejectDeviceAuth() throws Exception {
        mvc.perform(get("/api/nexus/settings/speed-cap")
                        .header("X-Auth-Token", deviceAuthToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsRejectDevicePublic() throws Exception {
        mvc.perform(put("/api/nexus/settings/speed-cap")
                        .contentType("application/json")
                        .content("{\"value\":1.0}")
                        .header("X-Auth-Token", devicePublicToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsRejectNoToken() throws Exception {
        mvc.perform(get("/api/nexus/settings/speed-cap").with(remote("127.0.0.1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void settingsRejectAdminFromLan() throws Exception {
        mvc.perform(get("/api/nexus/settings/speed-cap")
                        .header("X-Auth-Token", adminToken)
                        .with(remote("192.168.1.20")))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsAllowAdminFromLoopback() throws Exception {
        mvc.perform(get("/api/nexus/settings/speed-cap")
                        .header("X-Auth-Token", adminToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/nexus/settings/speed-cap")
                        .contentType("application/json")
                        .content("{}")
                        .header("X-Auth-Token", adminToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isOk());
    }

    @Test
    void storageRejectDeviceAuth() throws Exception {
        mvc.perform(get("/api/nexus/storage/upload-dir")
                        .header("X-Auth-Token", deviceAuthToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void storageRejectDevicePublic() throws Exception {
        mvc.perform(get("/api/nexus/storage/upload-dir")
                        .header("X-Auth-Token", devicePublicToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void storageRejectNoToken() throws Exception {
        mvc.perform(get("/api/nexus/storage/upload-dir").with(remote("127.0.0.1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storageRejectAdminFromLan() throws Exception {
        mvc.perform(get("/api/nexus/storage/upload-dir")
                        .header("X-Auth-Token", adminToken)
                        .with(remote("192.168.1.20")))
                .andExpect(status().isForbidden());
    }

    @Test
    void storageAllowAdminGetUploadDirFromLoopback() throws Exception {
        mvc.perform(get("/api/nexus/storage/upload-dir")
                        .header("X-Auth-Token", adminToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isOk());
    }

    @Test
    void storageOpenRoutesRejectDeviceAuth() throws Exception {
        mvc.perform(post("/api/nexus/storage/open-uploads-folder")
                        .header("X-Auth-Token", deviceAuthToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/nexus/storage/open-logs")
                        .header("X-Auth-Token", deviceAuthToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void storageOpenRoutesRejectNoTokenAndLanAdmin() throws Exception {
        mvc.perform(post("/api/nexus/storage/open-uploads-folder").with(remote("127.0.0.1")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/nexus/storage/open-logs")
                        .header("X-Auth-Token", adminToken)
                        .with(remote("192.168.1.20")))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutAllRejectsDeviceAuthWithoutBumpingVersion() throws Exception {
        int before = nexusService.getTokenVersion();
        mvc.perform(post("/api/nexus/auth/logout-all")
                        .header("X-Auth-Token", deviceAuthToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
        assertThat(nexusService.getTokenVersion()).isEqualTo(before);
    }

    @Test
    void logoutAllAllowsAdminFromLoopback() throws Exception {
        int before = nexusService.getTokenVersion();
        mvc.perform(post("/api/nexus/auth/logout-all")
                        .header("X-Auth-Token", adminToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isOk());
        assertThat(nexusService.getTokenVersion()).isGreaterThan(before);
        adminToken = nexusAuthService.generateAdminToken(nexusService.getUsername(), "1");
    }

    @Test
    void defaultRuleRejectsDeviceAuthOnAuthMe() throws Exception {
        mvc.perform(get("/api/nexus/auth/me")
                        .header("X-Auth-Token", deviceAuthToken)
                        .with(remote("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void wsHandshakeRejectsDevicePublic() {
        assertThat(handshake(devicePublicToken)).isFalse();
    }

    @Test
    void wsHandshakeRejectsAdmin() {
        assertThat(handshake(adminToken)).isFalse();
    }

    @Test
    void wsHandshakeAcceptsDeviceAuth() {
        assertThat(handshake(deviceAuthToken)).isTrue();
    }

    private boolean handshake(String token) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/devices");
        servletRequest.addHeader("X-Auth-Token", token);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attrs = new HashMap<>();
        return handshakeInterceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                attrs);
    }

    private static RequestPostProcessor remote(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }
}
