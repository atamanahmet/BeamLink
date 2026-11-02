package com.atamanahmet.beamlink.nexus.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.security.RecoveryCodeFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoveryCodeServiceTest {

    private NexusService nexusService;
    private PasswordEncoder encoder;
    private RecoveryCodeService service;

    @BeforeEach
    void setUp() {
        nexusService = mock(NexusService.class);
        encoder = mock(PasswordEncoder.class);
        service = new RecoveryCodeService(nexusService, encoder);
        when(encoder.encode(anyString())).thenAnswer(inv -> "hash-" + inv.getArgument(0));
    }

    @Test
    void hashCheckAcceptsNormalizedCode() {
        when(nexusService.getEncodedRecoveryCode()).thenReturn("stored");
        when(encoder.matches("abcdefgh12345678", "stored")).thenReturn(true);
        when(nexusService.getUsername()).thenReturn("admin");
        when(encoder.encode("newpass12")).thenReturn("pw-hash");

        Optional<String> next = service.resetPassword("ABCD-EFGH 1234-5678", "newpass12");

        assertThat(next).isPresent();
        verify(nexusService).updateCredentials("admin", "pw-hash");
        verify(nexusService).setEncodedRecoveryCode(anyString());
    }

    @Test
    void wrongCodeDoesNotReset() {
        when(nexusService.getEncodedRecoveryCode()).thenReturn("stored");
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        assertThat(service.resetPassword("ffff-ffff-ffff-ffff-ffff-ffff-ffff-ffff", "newpass12"))
                .isEmpty();
        verify(nexusService, never()).updateCredentials(anyString(), anyString());
    }

    @Test
    void wrongCodeCallerCanThrottle() {
        LoginFailureThrottle throttle = new LoginFailureThrottle(60_000L, 5);
        String addr = "127.0.0.1";
        long now = 1_000_000L;
        when(nexusService.getEncodedRecoveryCode()).thenReturn("stored");
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThat(service.resetPassword("nope", "newpass12")).isEmpty();
            throttle.recordFailure(addr, now + i);
        }
        assertThat(throttle.isThrottled(addr, now + 10)).isTrue();
    }

    @Test
    void singleUseRejectsOldCodeAfterReset() {
        when(nexusService.getUsername()).thenReturn("admin");
        when(nexusService.getEncodedRecoveryCode()).thenReturn("hash-old");
        when(encoder.matches("oldcodeoldcodeoldcodeoldcodeoldc", "hash-old")).thenReturn(true);
        when(encoder.matches("oldcodeoldcodeoldcodeoldcodeoldc", "hash-0123456789abcdef0123456789abcdef"))
                .thenReturn(false);
        when(encoder.encode("newpass12")).thenReturn("pw-hash");

        Optional<String> first = service.resetPassword(
                RecoveryCodeFormat.formatDisplay("oldcodeoldcodeoldcodeoldcodeoldc"),
                "newpass12");
        assertThat(first).isPresent();

        when(nexusService.getEncodedRecoveryCode()).thenReturn("hash-next");
        when(encoder.matches("oldcodeoldcodeoldcodeoldcodeoldc", "hash-next")).thenReturn(false);
        assertThat(service.resetPassword(
                RecoveryCodeFormat.formatDisplay("oldcodeoldcodeoldcodeoldcodeoldc"),
                "newpass12")).isEmpty();
    }

    @Test
    void resetRevokesSessionsViaUpdateCredentials() {
        when(nexusService.getEncodedRecoveryCode()).thenReturn("stored");
        when(encoder.matches("aa", "stored")).thenReturn(true);
        when(nexusService.getUsername()).thenReturn("admin");
        when(encoder.encode("newpass12")).thenReturn("pw-hash");

        service.resetPassword("AA", "newpass12");

        verify(nexusService).updateCredentials("admin", "pw-hash");
    }

    @Test
    void regenerateNeedsCurrentPassword() {
        when(nexusService.hasPassword()).thenReturn(true);
        when(nexusService.getEncodedPassword()).thenReturn("pw");
        when(encoder.matches("wrong", "pw")).thenReturn(false);

        assertThatThrownBy(() -> service.regenerate("wrong"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Current password is incorrect.");
        verify(nexusService, never()).setEncodedRecoveryCode(anyString());

        when(encoder.matches("right-pass", "pw")).thenReturn(true);
        String code = service.regenerate("right-pass");
        assertThat(code).isNotBlank();
        verify(nexusService).setEncodedRecoveryCode(anyString());
    }

    @Test
    void issuedCodeNeverAppearsInLogLine() {
        Logger logger = (Logger) LoggerFactory.getLogger(RecoveryCodeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        when(nexusService.getEncodedRecoveryCode()).thenReturn("stored");
        when(encoder.matches("deadbeefdeadbeefdeadbeefdeadbeef", "stored")).thenReturn(true);
        when(nexusService.getUsername()).thenReturn("admin");
        when(encoder.encode("newpass12")).thenReturn("pw-hash");

        Optional<String> next = service.resetPassword(
                "dead-beef-dead-beef-dead-beef-dead-beef", "newpass12");
        assertThat(next).isPresent();
        String shown = next.get();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(line -> {
                    assertThat(line).doesNotContain(shown);
                    assertThat(line).doesNotContain("deadbeef");
                });
        logger.detachAppender(appender);
    }
}
