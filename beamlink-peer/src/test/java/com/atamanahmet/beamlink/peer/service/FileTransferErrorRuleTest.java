package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.exception.ErrorReason;
import com.atamanahmet.beamlink.peer.exception.FileTransferException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.assertThat;

class FileTransferErrorRuleTest {

    @Test
    void detectsConnectAndCannotReachMessage() {
        assertThat(FileTransferErrorRule.isTargetUnreachable(new ConnectException())).isTrue();
        assertThat(FileTransferErrorRule.isTargetUnreachable(
                new FileTransferException("Cannot reach target peer: https://x:9090/api/transfers/receive", null)))
                .isTrue();
        assertThat(FileTransferErrorRule.isTargetUnreachable(
                new FileTransferException("Timed out registering transfer with target: u", null)))
                .isTrue();
        assertThat(FileTransferErrorRule.isTargetUnreachable(
                new FileTransferException("File not found: a.txt", null)))
                .isFalse();
        assertThat(FileTransferErrorRule.isTargetUnreachable(
                new FileTransferException("Cannot reach target", new CertificateException("pin"))))
                .isTrue();
        assertThat(FileTransferErrorRule.isTargetUnreachable(
                new FileTransferException("Cannot reach target", new SSLException("handshake_failure"))))
                .isTrue();
    }

    @Test
    void lanConnectTimeoutKeepsConnectTimeoutReason() {
        FileTransferException lan = new FileTransferException(
                "wrap",
                new HttpConnectTimeoutException("connect"),
                "192.168.1.5");
        assertThat(FileTransferErrorRule.reason(lan)).isEqualTo(ErrorReason.CONNECT_TIMEOUT);
        assertThat(FileTransferErrorRule.isTargetUnreachable(lan)).isTrue();
    }

    @Test
    void loopbackConnectTimeoutDemotesToTimeout() {
        HttpConnectTimeoutException cause = new HttpConnectTimeoutException("connect");
        assertThat(FileTransferErrorRule.reason(
                new FileTransferException("wrap", cause, "127.0.0.1")))
                .isEqualTo(ErrorReason.TIMEOUT);
        assertThat(FileTransferErrorRule.reason(
                new FileTransferException("wrap", cause, "localhost")))
                .isEqualTo(ErrorReason.TIMEOUT);
        assertThat(FileTransferErrorRule.reason(
                new FileTransferException("wrap", cause, "::1")))
                .isEqualTo(ErrorReason.TIMEOUT);
    }
}
