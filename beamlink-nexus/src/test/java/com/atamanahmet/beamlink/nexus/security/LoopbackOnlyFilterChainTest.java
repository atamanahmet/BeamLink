package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoopbackOnlyFilterChainTest {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-nexus-loopback-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/nexus-loopback.db");
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
        registry.add("nexus.tls.port", () -> 17476);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void remoteAdminAuditIsForbidden() throws Exception {
        mvc.perform(get("/api/nexus/admin/audit").with(remote("192.168.1.20")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void remoteEncodedAdminAuditIsForbidden() throws Exception {
        mvc.perform(get(URI.create("/api/nexus/%61dmin/audit")).with(remote("192.168.1.20")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void remoteAuthLoginIsForbidden() throws Exception {
        mvc.perform(post("/api/nexus/auth/login").with(remote("192.168.1.20")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void loopbackAdminAuditWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/nexus/admin/audit").with(remote("127.0.0.1")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Sets the TCP remote address on the mock request.
     */
    private static RequestPostProcessor remote(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }
}
