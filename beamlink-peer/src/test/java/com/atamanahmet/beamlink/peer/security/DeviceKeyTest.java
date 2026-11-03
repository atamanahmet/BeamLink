package com.atamanahmet.beamlink.peer.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceKeyTest {

    @TempDir
    Path dir;

    @Test
    void fingerprintKnownVector() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();
        byte[] spki = pair.getPublic().getEncoded();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(spki);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        String expected = hex.substring(0, 32).replaceAll("(.{4})", "$1 ").trim();
        assertThat(DeviceKey.fingerprint(spki)).isEqualTo(expected);
    }

    @Test
    void createThenLoadSameFingerprint() {
        Path file = dir.resolve("device.p8");
        DeviceKey first = new DeviceKey(file);
        first.loadOrCreate();
        String fp = first.fingerprint();
        DeviceKey second = new DeviceKey(file);
        second.loadOrCreate();
        assertThat(second.fingerprint()).isEqualTo(fp);
        assertThat(second.publicKeyBase64()).isEqualTo(first.publicKeyBase64());
    }

    @Test
    void deleteAndRecreateChangesFingerprint() {
        Path file = dir.resolve("device.p8");
        DeviceKey key = new DeviceKey(file);
        key.loadOrCreate();
        String before = key.fingerprint();
        key.deleteAndRecreate();
        assertThat(key.fingerprint()).isNotEqualTo(before);
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void corruptFileFailsLoudly() throws Exception {
        Path file = dir.resolve("device.p8");
        Files.writeString(file, "not-a-key");
        DeviceKey key = new DeviceKey(file);
        assertThatThrownBy(key::loadOrCreate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreadable or corrupt");
    }

    @Test
    void writeFailureLeavesNoFinalOrTemp() throws Exception {
        Path file = dir.resolve("device.p8");
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep"), "x");
        DeviceKey key = new DeviceKey(file);
        assertThatThrownBy(key::loadOrCreate).isInstanceOf(IllegalStateException.class);
        assertThat(Files.isDirectory(file)).isTrue();
        try (var stream = Files.list(dir)) {
            assertThat(stream.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".tmp")))
                    .isEmpty();
        }
    }

    @Test
    void loadHardensDefaultPermissions() throws Exception {
        Path file = dir.resolve("device.p8");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();
        writeDefaultPerms(file, pair.getPrivate().getEncoded());
        DeviceKey key = new DeviceKey(file);
        key.loadOrCreate();
        assertThat(key.fingerprint()).isNotBlank();
        assertOwnerOnly(file);
    }

    private static void writeDefaultPerms(Path path, byte[] data) throws IOException {
        Files.write(path, data);
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ));
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void assertOwnerOnly(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> expected = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            assertThat(Files.getPosixFilePermissions(path)).isEqualTo(expected);
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        Assumptions.assumeTrue(acl != null, "cannot check owner-only permissions on this OS");
        UserPrincipal owner = acl.getOwner();
        for (AclEntry entry : acl.getAcl()) {
            if (entry.type() == AclEntryType.ALLOW) {
                assertThat(entry.principal()).isEqualTo(owner);
            }
        }
        assertThat(acl.getAcl())
                .filteredOn(entry -> entry.type() == AclEntryType.ALLOW)
                .isNotEmpty();
    }
}
