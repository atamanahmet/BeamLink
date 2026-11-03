package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.NexusMdnsDiscovery;
import com.atamanahmet.beamlink.peer.domain.Peer;
import com.atamanahmet.beamlink.peer.repository.PeerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ResetServiceFactoryResetTest {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-peer-reset-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/agent-reset.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @MockitoBean
    private NetworkRegistrationService networkRegistrationService;

    @MockitoBean
    private NexusMdnsDiscovery mdnsDiscovery;

    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
        registry.add("PEER_UI_SECRET", () -> "test-ui-secret-for-reset-it");
    }

    @Autowired
    private ResetService resetService;

    @Autowired
    private PeerRepository peerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Factory reset must empty data tables and keep Flyway history.
     */
    @Test
    void factoryResetDeletesRowsAndKeepsFlywayHistory() {
        peerRepository.save(Peer.builder()
                .deviceId(UUID.randomUUID())
                .deviceName("peer-a")
                .ipAddress("10.0.0.2")
                .port(8080)
                .lastSeen(1L)
                .online(true)
                .build());
        assertThat(count("peers")).isGreaterThan(0);

        resetService.factoryReset(false);

        assertThat(count("peers")).isZero();
        assertThat(count("file_transfer")).isZero();
        assertThat(count("batch_transfer")).isZero();
        assertThat(count("directory_transfer")).isZero();
        assertThat(count("transfer_logs")).isZero();
        assertThat(count("settings")).isZero();
        assertThat(count("flyway_schema_history")).isGreaterThan(0);
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }
}
