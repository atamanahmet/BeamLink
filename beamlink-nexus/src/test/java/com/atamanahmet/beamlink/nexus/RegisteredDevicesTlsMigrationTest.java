package com.atamanahmet.beamlink.nexus;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class RegisteredDevicesTlsMigrationTest {

    @Test
    void v9KeepsExistingRowsAndAcceptsNullTlsColumns() throws Exception {
        Path tempDb = Files.createTempFile("beamlink-nexus-peer-tls-v9-", ".db");
        Files.deleteIfExists(tempDb);
        String url = "jdbc:sqlite:" + tempDb.toAbsolutePath();

        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .target("8")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.executeUpdate(
                    "INSERT INTO registered_devices (id, name, ip_address, port, state) VALUES "
                            + "('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'keep', '10.0.0.8', 9090, 'APPROVED')");
        }

        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .target("9")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            ResultSet kept = s.executeQuery(
                    "SELECT name, tls_fingerprint, tls_port FROM registered_devices "
                            + "WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'");
            assertThat(kept.next()).isTrue();
            assertThat(kept.getString(1)).isEqualTo("keep");
            assertThat(kept.getString(2)).isNull();
            kept.getObject(3);
            assertThat(kept.wasNull()).isTrue();
            kept.close();
            s.executeUpdate(
                    "INSERT INTO registered_devices (id, name, ip_address, port, state, tls_fingerprint, tls_port) "
                            + "VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'phone', '10.0.0.9', 9090, "
                            + "'PENDING_APPROVAL', NULL, NULL)");
            ResultSet phone = s.executeQuery(
                    "SELECT tls_fingerprint, tls_port FROM registered_devices "
                            + "WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'");
            assertThat(phone.next()).isTrue();
            assertThat(phone.getString(1)).isNull();
            phone.getObject(2);
            assertThat(phone.wasNull()).isTrue();
        }
    }
}
