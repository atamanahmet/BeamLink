package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NexusTlsCertTest {

    @TempDir
    Path dir;

    @Test
    void generatesParseableEcCertWithExpectedSubject() {
        Path store = dir.resolve("nexus-tls.p12");
        Path pass = dir.resolve("nexus-tls.pass");
        NexusTlsCert cert = new NexusTlsCert(store, pass);
        cert.loadOrCreate();
        X509Certificate leaf = cert.getLeaf();
        assertThat(leaf).isNotNull();
        assertThat(leaf.getSubjectX500Principal().getName()).isEqualTo("CN=beamlink-nexus");
        PublicKey key = leaf.getPublicKey();
        assertThat(key).isInstanceOf(ECPublicKey.class);
        assertThat(((ECPublicKey) key).getParams().getCurve().getField().getFieldSize()).isEqualTo(256);
        assertThat(NexusTlsFingerprint.of(leaf)).isNotBlank();
        NexusTlsCert loaded = new NexusTlsCert(store, pass);
        loaded.loadOrCreate();
        assertThat(NexusTlsFingerprint.of(loaded.getLeaf())).isEqualTo(NexusTlsFingerprint.of(leaf));
    }

    @Test
    void keystoreIsOwnerOnly() throws IOException {
        Path store = dir.resolve("nexus-tls.p12");
        Path pass = dir.resolve("nexus-tls.pass");
        new NexusTlsCert(store, pass).loadOrCreate();
        assertOwnerOnly(store);
        assertOwnerOnly(pass);
    }

    @Test
    void writeFailureLeavesNoFinalOrTemp() throws IOException {
        Path store = dir.resolve("nexus-tls.p12");
        Files.createDirectory(store);
        Files.writeString(store.resolve("keep"), "x");
        Path pass = dir.resolve("nexus-tls.pass");
        assertThatThrownBy(() -> new NexusTlsCert(store, pass).loadOrCreate())
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.isDirectory(store)).isTrue();
        assertThat(Files.exists(pass)).isFalse();
        assertNoTempFiles(dir);
    }

    @Test
    void loadHardensDefaultPermissions() throws IOException {
        Path store = dir.resolve("nexus-tls.p12");
        Path pass = dir.resolve("nexus-tls.pass");
        new NexusTlsCert(store, pass).loadOrCreate();
        byte[] storeBytes = Files.readAllBytes(store);
        byte[] passBytes = Files.readAllBytes(pass);
        Files.delete(store);
        Files.delete(pass);
        writeDefaultPerms(store, storeBytes);
        writeDefaultPerms(pass, passBytes);
        new NexusTlsCert(store, pass).loadOrCreate();
        assertOwnerOnly(store);
        assertOwnerOnly(pass);
    }

    @Test
    void leftoverPassCreatesBoth() throws IOException {
        Path store = dir.resolve("nexus-tls.p12");
        Path pass = dir.resolve("nexus-tls.pass");
        Files.writeString(pass, "stale");
        NexusTlsCert created = new NexusTlsCert(store, pass);
        created.loadOrCreate();
        assertThat(Files.isRegularFile(store)).isTrue();
        assertThat(Files.isRegularFile(pass)).isTrue();
        assertThat(created.getLeaf()).isNotNull();
    }

    @Test
    void markerWithoutPassFails() throws IOException {
        Path store = dir.resolve("nexus-tls.p12");
        Path pass = dir.resolve("nexus-tls.pass");
        Files.write(store, new byte[] {1, 2, 3});
        assertThatThrownBy(() -> new NexusTlsCert(store, pass).loadOrCreate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(pass.toString())
                .hasMessageContaining("Delete both files to create a new key, peers must approve again");
        assertThat(Files.exists(pass)).isFalse();
    }

    @Test
    void missingAliasFailsAsCorrupt() throws Exception {
        Path store = dir.resolve("nexus-tls.p12");
        Path pass = dir.resolve("nexus-tls.pass");
        String passText = "test-pass";
        Files.writeString(pass, passText);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        char[] password = passText.toCharArray();
        ks.load(null, password);
        try (OutputStream out = Files.newOutputStream(store)) {
            ks.store(out, password);
        }
        assertThatThrownBy(() -> new NexusTlsCert(store, pass).loadOrCreate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreadable or corrupt")
                .hasMessageContaining(store.toString())
                .hasMessageContaining("Delete both files to create a new key, peers must approve again")
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alias missing");
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

    private static void assertNoTempFiles(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            assertThat(stream.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".tmp")))
                    .isEmpty();
        }
    }
}
