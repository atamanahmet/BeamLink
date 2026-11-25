package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectorySenderWirePathTest {

    @Test
    void relativePathUsesSlashJoin() {
        assertThat(TransferPath.join(Path.of("a", "b", "c.txt"))).isEqualTo("a/b/c.txt");
    }

    @Test
    void emptyDirectoryEntryUsesSlashJoin() {
        assertThat(TransferPath.join(Path.of("empty"))).isEqualTo("empty");
    }
}
