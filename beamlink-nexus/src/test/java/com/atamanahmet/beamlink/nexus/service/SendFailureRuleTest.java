package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.ChunkHttpException;
import com.atamanahmet.beamlink.nexus.exception.ChunkRetryExhaustedException;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SendFailureRuleTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(
                        "connect",
                        new ConnectException("refused"),
                        SendFailureKind.OFFLINE),
                Arguments.of(
                        "timeout",
                        new HttpTimeoutException("slow"),
                        SendFailureKind.OFFLINE),
                Arguments.of(
                        "http500",
                        new ChunkHttpException(500, "Chunk rejected. Status: 500"),
                        SendFailureKind.RETRY),
                Arguments.of(
                        "http408",
                        new ChunkHttpException(408, "Chunk rejected. Status: 408"),
                        SendFailureKind.RETRY),
                Arguments.of(
                        "http429",
                        new ChunkHttpException(429, "Chunk rejected. Status: 429"),
                        SendFailureKind.RETRY),
                Arguments.of(
                        "http400",
                        new ChunkHttpException(400, "Chunk rejected. Status: 400"),
                        SendFailureKind.FATAL),
                Arguments.of(
                        "protocol",
                        new TransferProtocolException("X", "fail", HttpStatus.CONFLICT),
                        SendFailureKind.FATAL),
                Arguments.of(
                        "checksum",
                        new FileTransferException("checksum mismatch", null),
                        SendFailureKind.FATAL),
                Arguments.of(
                        "unknown",
                        new IllegalStateException("weird"),
                        SendFailureKind.RETRY),
                Arguments.of(
                        "exhaustedRetryCause",
                        new ChunkRetryExhaustedException(
                                "done",
                                new ChunkHttpException(503, "Chunk rejected. Status: 503")),
                        SendFailureKind.RETRY),
                Arguments.of(
                        "exhaustedOfflineCause",
                        new ChunkRetryExhaustedException(
                                "done",
                                new ConnectException("refused")),
                        SendFailureKind.OFFLINE)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void classifies(
            String name,
            Throwable error,
            SendFailureKind expected) {
        assertThat(SendFailureRule.classify(error)).isEqualTo(expected);
    }
}
