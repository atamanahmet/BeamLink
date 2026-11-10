package com.atamanahmet.beamlink.nexus.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atamanahmet.beamlink.nexus.service.FileTransferErrorRule;
import com.atamanahmet.beamlink.nexus.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.assertThat;

class FileTransferExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void putCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-cid");
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void clearMdc() {
        if (logger != null && appender != null) {
            logger.detachAppender(appender);
        }
        MDC.clear();
    }

    @Test
    void unreachableIsBadGatewayWithStableCode() {
        FileTransferException ex = new FileTransferException(
                "Cannot reach target peer: https://192.168.1.2:9090/api/transfers/receive",
                new ConnectException());
        ResponseEntity<ProblemDetail> res = handler.handleFileTransfer(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getTitle()).isEqualTo("Target unreachable");
        assertThat(res.getBody().getDetail()).isEqualTo(FileTransferErrorRule.TARGET_UNREACHABLE_MESSAGE);
        assertThat(res.getBody().getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(res.getBody().getProperties()).containsEntry("code", FileTransferErrorRule.TARGET_UNREACHABLE);
        assertThat(res.getBody().getProperties()).containsEntry("reason", ErrorReason.REFUSED.name());
        assertThat(res.getBody().getProperties()).containsEntry("correlationId", "test-cid");
        assertThat(res.getBody().getProperties()).doesNotContainKey("message");
        assertThat(res.getBody().getProperties()).doesNotContainKey("success");
        assertLogged(Level.ERROR, false);
    }

    @Test
    void tlsTrustIsBadGatewayWarnNoStack() {
        SSLHandshakeException ssl = new SSLHandshakeException("certificate_unknown");
        ssl.initCause(new CertificateException("No subject alternative names present"));
        FileTransferException ex = new FileTransferException(
                "Cannot reach target peer: https://192.168.1.2:9091/api/transfers/receive",
                ssl);
        ResponseEntity<ProblemDetail> res = handler.handleFileTransfer(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getDetail()).isEqualTo("TLS handshake with the peer failed");
        assertThat(res.getBody().getProperties()).containsEntry("code", FileTransferErrorRule.TARGET_UNREACHABLE);
        assertThat(res.getBody().getProperties()).containsEntry("reason", ErrorReason.TLS_TRUST.name());
        assertLogged(Level.WARN, false);
    }

    @Test
    void tlsOtherIsBadGatewayWarnNoStack() {
        FileTransferException ex = new FileTransferException(
                "Cannot reach target peer: https://192.168.1.2:9091/api/transfers/receive",
                new SSLException("handshake_failure"));
        ResponseEntity<ProblemDetail> res = handler.handleFileTransfer(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getDetail()).isEqualTo("TLS handshake with the peer failed");
        assertThat(res.getBody().getProperties()).containsEntry("code", FileTransferErrorRule.TARGET_UNREACHABLE);
        assertThat(res.getBody().getProperties()).containsEntry("reason", ErrorReason.TLS_OTHER.name());
        assertLogged(Level.WARN, false);
    }

    @Test
    void connectTimeoutIsGatewayTimeoutWithConnectReason() {
        FileTransferException ex = new FileTransferException(
                "Timed out registering transfer with target: https://192.168.1.2:9091/api/transfers/receive",
                new HttpConnectTimeoutException("connect"));
        ResponseEntity<ProblemDetail> res = handler.handleFileTransfer(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getProperties()).containsEntry("code", FileTransferErrorRule.TARGET_UNREACHABLE);
        assertThat(res.getBody().getProperties()).containsEntry("reason", ErrorReason.CONNECT_TIMEOUT.name());
        assertLogged(Level.ERROR, false);
    }

    @Test
    void loopbackConnectTimeoutStaysTimeoutReason() {
        FileTransferException ex = new FileTransferException(
                "Timed out registering transfer with target: https://127.0.0.1:9091/api/transfers/receive",
                new HttpConnectTimeoutException("connect"),
                "127.0.0.1");
        ResponseEntity<ProblemDetail> res = handler.handleFileTransfer(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getProperties()).containsEntry("code", FileTransferErrorRule.TARGET_UNREACHABLE);
        assertThat(res.getBody().getProperties()).containsEntry("reason", ErrorReason.TIMEOUT.name());
        assertLogged(Level.ERROR, false);
    }

    @Test
    void timeoutIsGatewayTimeout() {
        FileTransferException ex = new FileTransferException(
                "Timed out registering transfer with target: u",
                new HttpTimeoutException("t"));
        ResponseEntity<ProblemDetail> res = handler.handleFileTransfer(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getProperties()).containsEntry("code", FileTransferErrorRule.TARGET_UNREACHABLE);
        assertThat(res.getBody().getProperties()).containsEntry("reason", ErrorReason.TIMEOUT.name());
        assertLogged(Level.ERROR, false);
    }

    @Test
    void missingRouteIsNotFoundWarnNoStack() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "", "/actuator/health");
        ResponseEntity<Object> res = handler.handleNoResourceFoundException(
                ex,
                new HttpHeaders(),
                HttpStatus.NOT_FOUND,
                new ServletWebRequest(new MockHttpServletRequest()));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail body = (ProblemDetail) res.getBody();
        assertThat(body.getProperties()).containsEntry("code", "NOT_FOUND");
        assertLogged(Level.WARN, false);
    }

    @Test
    void otherFileTransferStaysInternalError() {
        FileTransferException ex = new FileTransferException("File not found: a.txt", null);
        ResponseEntity<ProblemDetail> res = handler.handleFileTransfer(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getTitle()).isEqualTo("File transfer failed");
        assertThat(res.getBody().getDetail()).isEqualTo("File transfer failed");
        assertThat(res.getBody().getProperties()).containsEntry("code", "FILE_TRANSFER_FAILED");
        assertThat(res.getBody().getProperties()).containsEntry("reason", ErrorReason.UNKNOWN.name());
        assertThat(res.getBody().getProperties()).containsEntry("correlationId", "test-cid");
        assertThat(res.getBody().getProperties()).doesNotContainKey("message");
        assertThat(res.getBody().getProperties()).doesNotContainKey("success");
        assertThat(res.getBody().getProperties()).doesNotContainKey("error");
        assertLogged(Level.ERROR, true);
    }

    /**
     * Last log line must match level and whether a stack was attached.
     */
    private void assertLogged(Level level, boolean stack) {
        assertThat(appender.list).isNotEmpty();
        ILoggingEvent event = appender.list.get(appender.list.size() - 1);
        assertThat(event.getLevel()).isEqualTo(level);
        if (stack) {
            assertThat(event.getThrowableProxy()).isNotNull();
        } else {
            assertThat(event.getThrowableProxy()).isNull();
        }
    }
}
