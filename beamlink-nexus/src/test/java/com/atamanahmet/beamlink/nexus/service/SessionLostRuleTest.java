package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SessionLostRuleTest {

    @Test
    void sessionNotFoundIsFatalWithOwnerMessage() {
        assertThat(SessionLostRule.isLost("SESSION_NOT_FOUND")).isTrue();
        assertThat(SessionLostRule.isLost("NOT_FOUND")).isFalse();
        assertThat(SessionLostRule.isLost(null)).isFalse();
        assertThat(SessionLostRule.MESSAGE)
                .isEqualTo("Receiver lost the transfer, send again");
        TransferProtocolException lost = new TransferProtocolException(
                SessionLostRule.CODE,
                SessionLostRule.MESSAGE,
                HttpStatus.NOT_FOUND);
        assertThat(SendFailureRule.classify(lost)).isEqualTo(SendFailureKind.FATAL);
        assertThat(SendFailureRule.allowsChunkRetry(SendFailureKind.FATAL)).isFalse();
        assertThat(SendFailureRule.classify(new java.net.ConnectException("refused")))
                .isEqualTo(SendFailureKind.OFFLINE);
    }
}
