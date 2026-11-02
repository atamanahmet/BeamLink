package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.repository.DeviceRepository;
import com.atamanahmet.beamlink.nexus.repository.SettingsRepository;
import com.atamanahmet.beamlink.nexus.security.filter.DeviceTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ResetServiceFactoryResetTest {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-nexus-reset-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/nexus-reset.db");
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
        registry.add("nexus.tls.port", () -> 17475);
    }

    @Autowired
    private ResetService resetService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private NexusService nexusService;

    @Autowired
    private NexusAuthService nexusAuthService;

    @Autowired
    private DeviceTokenFilter deviceTokenFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Factory reset must empty network rows, keep Flyway history and JWT keys.
     */
    @Test
    void factoryResetDeletesRowsAndKeepsFlywayHistory() {
        deviceRepository.save(Device.builder()
                .name("device-a")
                .ipAddress("10.0.0.2")
                .port(8080)
                .state(DeviceState.PENDING_APPROVAL)
                .registeredAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build());
        assertThat(count("registered_devices")).isGreaterThan(0);
        assertThat(settingsRepository.findByKey(SettingKey.JWT_SECRET)).isPresent();

        resetService.factoryReset(false);

        assertThat(count("registered_devices")).isZero();
        assertThat(count("file_transfer")).isZero();
        assertThat(count("batch_transfer")).isZero();
        assertThat(count("directory_transfer")).isZero();
        assertThat(count("transfer_logs")).isZero();
        assertThat(count("flyway_schema_history")).isGreaterThan(0);
        assertThat(settingsRepository.findByKey(SettingKey.JWT_SECRET)).isPresent();
        assertThat(nexusService.hasPassword()).isFalse();
    }

    /**
     * Old admin JWT must fail after factory reset plus a new first-run setup.
     */
    @Test
    void oldAdminTokenRejectedAfterFactoryResetAndSetup() throws Exception {
        if (!nexusService.hasPassword()) {
            nexusAuthService.setup("old-admin", "oldpass12");
        }
        int tvBefore = nexusService.getTokenVersion();
        String oldToken = nexusAuthService.generateAdminToken(nexusService.getUsername(), "1");

        resetService.factoryReset(false);

        assertThat(nexusService.getTokenVersion()).isGreaterThan(tvBefore);
        assertThat(nexusService.hasPassword()).isFalse();

        nexusAuthService.setup("new-admin", "newpass12");
        assertThat(nexusService.getTokenVersion()).isGreaterThan(tvBefore);

        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Token", oldToken);
        deviceTokenFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"" + table + "\"", Long.class);
        return value == null ? 0L : value;
    }
}
