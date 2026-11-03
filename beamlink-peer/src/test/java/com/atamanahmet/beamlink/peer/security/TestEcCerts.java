package com.atamanahmet.beamlink.peer.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;

final class TestEcCerts {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private TestEcCerts() {
    }

    static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    static X509Certificate selfSigned(String cn, Instant notBefore, Instant notAfter) throws Exception {
        return selfSigned(cn, ecKeyPair(), notBefore, notAfter);
    }

    static X509Certificate selfSigned(String cn, KeyPair pair, Instant notBefore, Instant notAfter)
            throws Exception {
        X500Name subject = new X500Name("CN=" + cn);
        var builder = new JcaX509v3CertificateBuilder(
                subject,
                new BigInteger(64, new SecureRandom()),
                Date.from(notBefore),
                Date.from(notAfter),
                subject,
                pair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(pair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }
}
