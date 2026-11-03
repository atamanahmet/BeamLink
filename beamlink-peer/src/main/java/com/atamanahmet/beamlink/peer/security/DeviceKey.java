package com.atamanahmet.beamlink.peer.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * EC P-256 device key. PKCS8 file in the device data dir. Never logged.
 */
@Component
public class DeviceKey {

    private static final String CURVE = "secp256r1";
    private static final String SIG_ALG = "SHA256withECDSA";
    static final String FILE_NAME = "device.p8";

    private final Path keyFile;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @Autowired
    public DeviceKey(@Value("${beamlink.runtime.dir}") String runtimeDir) {
        this.keyFile = Path.of(runtimeDir, "data", FILE_NAME);
    }

    DeviceKey(Path keyFile) {
        this.keyFile = keyFile;
    }

    /**
     * Loads the key file or creates it once.
     */
    @PostConstruct
    public void loadOrCreate() {
        try {
            if (!Files.exists(keyFile)) {
                createNew();
                return;
            }
            byte[] der = Files.readAllBytes(keyFile);
            KeyFactory kf = KeyFactory.getInstance("EC");
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            publicKey = publicFromPrivate((ECPrivateKey) privateKey);
            OwnerOnlyFiles.ownerOnly(keyFile);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Device key file is unreadable or corrupt: " + keyFile
                            + ". Fix the file or delete it only if you accept a new identity.",
                    e);
        }
    }

    /**
     * Standard-base64 X.509 SPKI public key.
     */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * SHA-256 of SPKI, hex, first 32 chars, groups of 4.
     */
    public String fingerprint() {
        return fingerprint(publicKey.getEncoded());
    }

    /**
     * SHA-256 of the bytes, first 32 hex, lowercase, no spaces.
     */
    public static String canonicalFingerprint(byte[] der) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(der);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("fingerprint failed", e);
        }
    }

    /**
     * Display form of the canonical fingerprint, groups of 4.
     */
    public static String fingerprint(byte[] spki) {
        String first = canonicalFingerprint(spki);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < first.length(); i += 4) {
            if (i > 0) {
                grouped.append(' ');
            }
            grouped.append(first, i, i + 4);
        }
        return grouped.toString();
    }

    /**
     * Deletes the key file then creates a new pair. Used by factory reset.
     */
    public void deleteAndRecreate() {
        try {
            Files.deleteIfExists(keyFile);
        } catch (IOException e) {
            throw new IllegalStateException("Device key file delete failed: " + keyFile, e);
        }
        privateKey = null;
        publicKey = null;
        loadOrCreate();
    }

    /**
     * DER signature in standard base64 over UTF-8 message bytes.
     */
    public String sign(byte[] message) {
        try {
            Signature signer = Signature.getInstance(SIG_ALG);
            signer.initSign(privateKey);
            signer.update(message);
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("device sign failed", e);
        }
    }

    private void createNew() throws Exception {
        Files.createDirectories(keyFile.getParent());
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec(CURVE));
        KeyPair pair = gen.generateKeyPair();
        OwnerOnlyFiles.writeOwnerOnly(keyFile, pair.getPrivate().getEncoded());
        privateKey = pair.getPrivate();
        publicKey = pair.getPublic();
    }

    /**
     * Derives the EC public key from a PKCS8 private key.
     */
    static PublicKey publicFromPrivate(ECPrivateKey priv) throws Exception {
        ECParameterSpec params = priv.getParams();
        ECPoint w = scale(params.getGenerator(), priv.getS(), params.getCurve());
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, params));
    }

    private static ECPoint scale(ECPoint point, BigInteger k, EllipticCurve curve) {
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = point;
        for (int i = 0; i < k.bitLength(); i++) {
            if (k.testBit(i)) {
                result = add(result, addend, curve);
            }
            addend = add(addend, addend, curve);
        }
        return result;
    }

    private static ECPoint add(ECPoint p, ECPoint q, EllipticCurve curve) {
        if (p.equals(ECPoint.POINT_INFINITY)) {
            return q;
        }
        if (q.equals(ECPoint.POINT_INFINITY)) {
            return p;
        }
        BigInteger mod = ((java.security.spec.ECFieldFp) curve.getField()).getP();
        BigInteger px = p.getAffineX();
        BigInteger py = p.getAffineY();
        BigInteger qx = q.getAffineX();
        BigInteger qy = q.getAffineY();
        if (px.equals(qx)) {
            if (py.add(qy).mod(mod).equals(BigInteger.ZERO)) {
                return ECPoint.POINT_INFINITY;
            }
            BigInteger lambda = px.multiply(px).multiply(BigInteger.valueOf(3))
                    .add(curve.getA())
                    .multiply(py.shiftLeft(1).modInverse(mod))
                    .mod(mod);
            BigInteger rx = lambda.multiply(lambda).subtract(px.shiftLeft(1)).mod(mod);
            BigInteger ry = lambda.multiply(px.subtract(rx)).subtract(py).mod(mod);
            return new ECPoint(rx, ry);
        }
        BigInteger lambda = qy.subtract(py).multiply(qx.subtract(px).modInverse(mod)).mod(mod);
        BigInteger rx = lambda.multiply(lambda).subtract(px).subtract(qx).mod(mod);
        BigInteger ry = lambda.multiply(px.subtract(rx)).subtract(py).mod(mod);
        return new ECPoint(rx, ry);
    }
}
