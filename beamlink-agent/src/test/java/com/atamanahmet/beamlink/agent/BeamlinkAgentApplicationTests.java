package com.atamanahmet.beamlink.agent;

import com.atamanahmet.beamlink.agent.service.NetworkRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest
class BeamlinkAgentApplicationTests {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-agent-test-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("data/temp"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/agent-test.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @MockBean
    private NetworkRegistrationService networkRegistrationService;

    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
    }

    @Test
    void contextLoads() {
    }

}
