package com.atamanahmet.beamlink.peer.config;

import com.atamanahmet.beamlink.peer.security.DeviceTlsCert;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Extra Tomcat TLS connector. Main HTTP stays on server.port.
 */
@Configuration
public class DeviceTlsConnectorConfig {

    /**
     * Adds a TLS connector on DEVICE_TLS_PORT bound to all interfaces.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tlsConnector(
            DeviceTlsCert cert,
            @Value("${peer.tls.port}") int tlsPort) {
        return factory -> factory.addAdditionalConnectors(httpsConnector(cert, tlsPort));
    }

    private static Connector httpsConnector(DeviceTlsCert cert, int tlsPort) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(tlsPort);
        connector.setScheme("https");
        connector.setSecure(true);
        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);
        try {
            protocol.setAddress(InetAddress.getByName("0.0.0.0"));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("TLS bind address failed", e);
        }
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(
                sslHostConfig, SSLHostConfigCertificate.Type.EC);
        certificate.setCertificateKeystoreFile(cert.keystoreAbsolutePath());
        certificate.setCertificateKeystorePassword(new String(cert.getPassword()));
        certificate.setCertificateKeystoreType("PKCS12");
        certificate.setCertificateKeyAlias(cert.alias());
        sslHostConfig.addCertificate(certificate);
        protocol.addSslHostConfig(sslHostConfig);
        return connector;
    }
}
