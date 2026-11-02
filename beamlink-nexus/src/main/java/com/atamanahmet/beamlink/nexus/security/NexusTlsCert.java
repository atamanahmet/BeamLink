package com.atamanahmet.beamlink.nexus.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Self-signed nexus TLS leaf. PKCS12 plus password file in the data dir.
 */
@Slf4j
@Component
public class NexusTlsCert {

    static final String KEYSTORE_FILE = "nexus-tls.p12";
    static final String PASS_FILE = "nexus-tls.pass";
    static final String ALIAS = "beamlink-nexus";
    private static final String CN = "CN=beamlink-nexus";
    private static final String CURVE = "secp256r1";
    private static final String SIG_ALG = "SHA256withECDSA";
    private static final String STORE_TYPE = "PKCS12";

    private final Path keystorePath;
    private final Path passPath;

    @Getter
    private char[] password;
    @Getter
    private X509Certificate leaf;

    @Autowired
    public NexusTlsCert(@Value("${beamlink.runtime.dir}") String runtimeDir) {
        Path data = Path.of(runtimeDir, "data");
        this.keystorePath = data.resolve(KEYSTORE_FILE);
        this.passPath = data.resolve(PASS_FILE);
    }

    NexusTlsCert(Path keystorePath, Path passPath) {
        this.keystorePath = keystorePath;
        this.passPath = passPath;
    }

    /**
     * Loads the keystore or creates it once.
     */
    @PostConstruct
    public void loadOrCreate() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            Files.createDirectories(keystorePath.getParent());
            if (Files.exists(keystorePath) && Files.exists(passPath)) {
                loadExisting();
                return;
            }
            if (Files.exists(keystorePath)) {
                throw new IllegalStateException(
                        "Nexus TLS password file missing: " + passPath
                                + ". Delete both files to create a new key, peers must approve again.");
            }
            createNew();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Nexus TLS keystore is unreadable or corrupt: " + keystorePath
                            + ". Delete both files to create a new key, peers must approve again.",
                    e);
        }
    }

    /**
     * Absolute PKCS12 path for the Tomcat connector.
     */
    public String keystoreAbsolutePath() {
        return keystorePath.toAbsolutePath().toString();
    }

    /**
     * Keystore alias for the leaf.
     */
    public String alias() {
        return ALIAS;
    }

    private void loadExisting() throws Exception {
        password = Files.readString(passPath, StandardCharsets.UTF_8).toCharArray();
        KeyStore store = KeyStore.getInstance(STORE_TYPE);
        try (InputStream in = Files.newInputStream(keystorePath)) {
            store.load(in, password);
        }
        leaf = (X509Certificate) store.getCertificate(ALIAS);
        if (leaf == null) {
            throw new IllegalStateException(
                    "Nexus TLS keystore is unreadable or corrupt: " + keystorePath
                            + ". Delete both files to create a new key, peers must approve again.",
                    new IllegalStateException("Nexus TLS alias missing: " + ALIAS));
        }
        ownerOnly(keystorePath);
        ownerOnly(passPath);
    }

    private void createNew() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        gen.initialize(new ECGenParameterSpec(CURVE));
        KeyPair pair = gen.generateKeyPair();

        Instant now = Instant.now();
        X500Name subject = new X500Name(CN);
        BigInteger serial = new BigInteger(64, new SecureRandom());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                Date.from(now.minus(1, ChronoUnit.MINUTES)),
                Date.from(now.plus(3650, ChronoUnit.DAYS)),
                subject,
                pair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(SIG_ALG)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(pair.getPrivate());
        leaf = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));

        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String passText = HexFormat.of().formatHex(raw);
        password = passText.toCharArray();

        KeyStore store = KeyStore.getInstance(STORE_TYPE);
        store.load(null, password);
        store.setKeyEntry(ALIAS, pair.getPrivate(), password, new java.security.cert.Certificate[]{leaf});
        ByteArrayOutputStream p12 = new ByteArrayOutputStream();
        store.store(p12, password);
        writePair(p12.toByteArray(), passText.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the keystore and password so both exist or neither does.
     */
    private void writePair(byte[] p12, byte[] pass) throws IOException {
        try {
            writeOwnerOnly(passPath, pass);
            writeOwnerOnly(keystorePath, p12);
        } catch (IOException e) {
            deleteRegularFile(keystorePath, e);
            deleteRegularFile(passPath, e);
            throw e;
        }
    }

    /**
     * Writes dest via an owner-only temp file and an atomic move.
     */
    static void writeOwnerOnly(Path dest, byte[] data) throws IOException {
        Path tmp = Files.createTempFile(dest.getParent(), dest.getFileName().toString(), ".tmp");
        try {
            ownerOnly(tmp);
            try (OutputStream out = Files.newOutputStream(tmp)) {
                out.write(data);
                out.flush();
            }
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * Deletes dest when it is a regular file. Adds failures to the cause.
     */
    private static void deleteRegularFile(Path path, IOException cause) {
        try {
            if (Files.isRegularFile(path)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException suppressed) {
            cause.addSuppressed(suppressed);
        }
    }

    /**
     * POSIX 600, or owner-only ACL on Windows.
     */
    static void ownerOnly(Path path) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl == null) {
                boolean ok = path.toFile().setReadable(true, true)
                        && path.toFile().setWritable(true, true)
                        && path.toFile().setExecutable(false, false);
                if (!ok) {
                    log.error("Failed to set owner-only permissions: {}", path);
                    throw new IOException("Failed to set owner-only permissions: " + path);
                }
                return;
            }
            UserPrincipal owner = acl.getOwner();
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.READ_NAMED_ATTRS,
                            AclEntryPermission.WRITE_NAMED_ATTRS,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.WRITE_ATTRIBUTES,
                            AclEntryPermission.READ_ACL,
                            AclEntryPermission.DELETE,
                            AclEntryPermission.SYNCHRONIZE)
                    .build();
            acl.setAcl(List.of(entry));
        }
    }
}
