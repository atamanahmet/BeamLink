package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.security.filter.PeerTokenFilter;
import com.atamanahmet.beamlink.peer.service.NetworkRegistrationService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks UI and PEER role rules on the device security chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceSecurityChainTest {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-peer-sec-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/agent-sec.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @MockitoBean
    private NetworkRegistrationService networkRegistrationService;

    @MockitoBean
    private DeviceUiSecretFilter deviceUiSecretFilter;

    @MockitoBean
    private PeerTokenFilter peerTokenFilter;

    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
        registry.add("PEER_UI_SECRET", () -> "test-ui-secret-for-context-load");
    }

    @Autowired
    private MockMvc mvc;

    /**
     * Lets the mock user reach authorizeHttpRequests.
     */
    @BeforeEach
    void filtersPass() throws Exception {
        Answer<Void> pass = invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        };
        doAnswer(pass).when(deviceUiSecretFilter).doFilter(any(), any(), any());
        doAnswer(pass).when(peerTokenFilter).doFilter(any(), any(), any());
    }

    /**
     * PEER must not read the UI status path.
     */
    @Test
    void peerRoleOnDeviceStatusIsForbidden() throws Exception {
        mvc.perform(get("/api/device/status").with(user("peer").roles("PEER")))
                .andExpect(status().isForbidden());
    }

    /**
     * UI must not read a peer transfer offset path.
     */
    @Test
    void uiRoleOnTransferOffsetIsForbidden() throws Exception {
        mvc.perform(get("/api/transfers/" + UUID.randomUUID() + "/offset")
                        .with(user("ui").roles("UI")))
                .andExpect(status().isForbidden());
    }

    /**
     * Anonymous UI status is unauthorized.
     */
    @Test
    void noAuthOnDeviceStatusIsUnauthorized() throws Exception {
        mvc.perform(get("/api/device/status"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * UI may call UI status without 401 or 403.
     */
    @Test
    void uiRoleOnDeviceStatusIsNotAuthError() throws Exception {
        int status = mvc.perform(get("/api/device/status").with(user("ui").roles("UI")))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }
}
