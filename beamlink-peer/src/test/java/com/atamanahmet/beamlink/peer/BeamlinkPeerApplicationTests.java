package com.atamanahmet.beamlink.peer;

import com.atamanahmet.beamlink.peer.service.NetworkRegistrationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BeamlinkPeerApplicationTests {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-peer-test-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/agent-test.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @MockitoBean
    private NetworkRegistrationService networkRegistrationService;

    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
        registry.add("PEER_UI_SECRET", () -> "test-ui-secret-for-context-load");
    }

    @Test
    void contextLoads() {
    }

    /**
     * Second migrate on same temp file must leave schema unchanged.
     */
    @Test
    void migrationsTwiceOnSameTempFileChangeNothing() throws Exception {
        Path tempDb = Files.createTempFile("beamlink-peer-flyway-", ".db");
        Files.deleteIfExists(tempDb);
        String url = "jdbc:sqlite:" + tempDb.toAbsolutePath();

        Flyway flyway = Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        List<String> first = dumpSchemaSql(url);

        flyway.migrate();
        List<String> second = dumpSchemaSql(url);

        assertThat(second).isEqualTo(first);
        assertThat(tempDb.toAbsolutePath().toString()).doesNotContain("/data/");
        assertThat(tempDb.toAbsolutePath().toString()).doesNotContain("\\data\\");
    }

    private static List<String> dumpSchemaSql(String jdbcUrl) throws Exception {
        List<String> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT name || '|' || COALESCE(sql, '') FROM sqlite_master "
                             + "WHERE name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
        }
        return rows;
    }

}
