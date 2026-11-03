package com.atamanahmet.beamlink.peer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsTlsPinMigrationTest {

    @Test
    void v2KeepsExistingRowsAndAcceptsTlsPin() throws Exception {
        Path tempDb = Files.createTempFile("beamlink-peer-settings-v2-", ".db");
        Files.deleteIfExists(tempDb);
        String url = "jdbc:sqlite:" + tempDb.toAbsolutePath();

        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.executeUpdate(
                    "INSERT INTO settings (updated_at, key, value) VALUES "
                            + "('2020-01-01 00:00:00', 'NEXUS_URL', 'https://10.0.0.5:7473')");
        }

        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .target("2")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            ResultSet kept = s.executeQuery(
                    "SELECT value FROM settings WHERE key = 'NEXUS_URL'");
            assertThat(kept.next()).isTrue();
            assertThat(kept.getString(1)).isEqualTo("https://10.0.0.5:7473");
            kept.close();
            s.executeUpdate(
                    "INSERT INTO settings (updated_at, key, value) VALUES "
                            + "('2020-01-01 00:00:00', 'NEXUS_TLS_PIN', 'aaaa bbbb cccc dddd eeee ffff 0000 1111')");
            ResultSet pin = s.executeQuery(
                    "SELECT value FROM settings WHERE key = 'NEXUS_TLS_PIN'");
            assertThat(pin.next()).isTrue();
            assertThat(pin.getString(1)).contains("aaaa");
        }
    }
}
