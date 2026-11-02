package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.ChunkHttpException;
import com.atamanahmet.beamlink.nexus.exception.ChunkRetryExhaustedException;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.OffsetMismatchException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
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
                        "connectTimeout",
                        new HttpConnectTimeoutException("connect"),
                        SendFailureKind.OFFLINE),
                Arguments.of(
                        "nestedConnectTimeout",
                        new FileTransferException("wrap", new HttpConnectTimeoutException("connect")),
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
                        "http422",
                        new ChunkHttpException(422, "Chunk rejected. Status: 422"),
                        SendFailureKind.CHUNK_CORRUPT),
                Arguments.of(
                        "offsetMismatch",
                        new OffsetMismatchException(120L),
                        SendFailureKind.RESYNC),
                Arguments.of(
                        "http409Other",
                        new ChunkHttpException(409, "Chunk rejected. Status: 409"),
                        SendFailureKind.FATAL),
                Arguments.of(
                        "http507",
                        new ChunkHttpException(507, "Chunk rejected. Status: 507"),
                        SendFailureKind.FATAL),
                Arguments.of(
                        "insufficientDisk",
                        new TransferProtocolException(
                                DiskReserveRule.CODE,
                                DiskReserveRule.DETAIL,
                                HttpStatus.INSUFFICIENT_STORAGE),
                        SendFailureKind.FATAL),
                Arguments.of(
                        "protocol",
                        new TransferProtocolException("X", "fail", HttpStatus.CONFLICT),
                        SendFailureKind.FATAL),
                Arguments.of(
                        "chunkChecksum",
                        new TransferProtocolException(
                                "CHUNK_CHECKSUM_MISMATCH",
                                "CHUNK_CHECKSUM_MISMATCH",
                                HttpStatus.UNPROCESSABLE_ENTITY),
                        SendFailureKind.CHUNK_CORRUPT),
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
                        SendFailureKind.OFFLINE),
                Arguments.of(
                        "exhaustedChunkCorrupt",
                        new ChunkRetryExhaustedException(
                                "done",
                                new TransferProtocolException(
                                        "CHUNK_CHECKSUM_MISMATCH",
                                        "CHUNK_CHECKSUM_MISMATCH",
                                        HttpStatus.UNPROCESSABLE_ENTITY)),
                        SendFailureKind.CHUNK_CORRUPT)
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

    @Test
    void chunkCorruptAllowsSameOffsetRetry() {
        assertThat(SendFailureRule.allowsChunkRetry(SendFailureKind.CHUNK_CORRUPT)).isTrue();
        assertThat(SendFailureRule.allowsChunkRetry(SendFailureKind.FATAL)).isFalse();
        assertThat(SendFailureRule.allowsChunkRetry(SendFailureKind.RESYNC)).isFalse();
    }
}
