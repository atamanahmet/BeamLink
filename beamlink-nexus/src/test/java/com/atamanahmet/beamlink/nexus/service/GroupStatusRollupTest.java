package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GroupStatusRollupTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void rollsUpChildStatuses(String name, List<TransferStatus> children, GroupTransferStatus expected) {
        assertThat(GroupStatusRollup.fromChildren(children)).isEqualTo(expected);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("empty", Collections.emptyList(), GroupTransferStatus.CANCELLED),
                Arguments.of("any ACTIVE",
                        List.of(TransferStatus.PENDING, TransferStatus.ACTIVE),
                        GroupTransferStatus.ACTIVE),
                Arguments.of("any PAUSED without ACTIVE",
                        List.of(TransferStatus.PENDING, TransferStatus.PAUSED),
                        GroupTransferStatus.PAUSED),
                Arguments.of("any PENDING without ACTIVE or PAUSED",
                        List.of(TransferStatus.PENDING, TransferStatus.COMPLETED),
                        GroupTransferStatus.PENDING),
                Arguments.of("all COMPLETED",
                        List.of(TransferStatus.COMPLETED, TransferStatus.COMPLETED),
                        GroupTransferStatus.COMPLETED),
                Arguments.of("COMPLETED plus FAILED",
                        List.of(TransferStatus.COMPLETED, TransferStatus.FAILED),
                        GroupTransferStatus.PARTIAL),
                Arguments.of("COMPLETED plus CANCELLED",
                        List.of(TransferStatus.COMPLETED, TransferStatus.CANCELLED),
                        GroupTransferStatus.PARTIAL),
                Arguments.of("all FAILED",
                        List.of(TransferStatus.FAILED, TransferStatus.FAILED),
                        GroupTransferStatus.FAILED),
                Arguments.of("FAILED without COMPLETED",
                        List.of(TransferStatus.FAILED, TransferStatus.CANCELLED),
                        GroupTransferStatus.FAILED),
                Arguments.of("all CANCELLED",
                        List.of(TransferStatus.CANCELLED, TransferStatus.CANCELLED),
                        GroupTransferStatus.CANCELLED),
                Arguments.of("ACTIVE wins over PAUSED",
                        Arrays.asList(TransferStatus.PAUSED, TransferStatus.ACTIVE),
                        GroupTransferStatus.ACTIVE)
        );
    }
}
