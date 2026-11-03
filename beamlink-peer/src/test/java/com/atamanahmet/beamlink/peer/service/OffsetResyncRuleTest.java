package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OffsetResyncRuleTest {

    @Test
    void validOffsetContinues() {
        assertThat(OffsetResyncRule.nextNoProgressCount(100L, null, 0)).isEqualTo(1);
        assertThat(OffsetResyncRule.decide(100L, 1000L, 1))
                .isEqualTo(OffsetResyncRule.Decision.CONTINUE);
        assertThat(OffsetResyncRule.nextNoProgressCount(200L, 100L, 1)).isEqualTo(1);
        assertThat(OffsetResyncRule.decide(200L, 1000L, 1))
                .isEqualTo(OffsetResyncRule.Decision.CONTINUE);
    }

    @Test
    void noProgressThreeTimesPauses() {
        int count = OffsetResyncRule.nextNoProgressCount(100L, null, 0);
        count = OffsetResyncRule.nextNoProgressCount(100L, 100L, count);
        count = OffsetResyncRule.nextNoProgressCount(100L, 100L, count);
        assertThat(count).isEqualTo(3);
        assertThat(OffsetResyncRule.decide(100L, 1000L, count))
                .isEqualTo(OffsetResyncRule.Decision.PAUSE);
    }

    @Test
    void outOfRangePauses() {
        assertThat(OffsetResyncRule.decide(-1L, 1000L, 1))
                .isEqualTo(OffsetResyncRule.Decision.PAUSE);
        assertThat(OffsetResyncRule.decide(1001L, 1000L, 1))
                .isEqualTo(OffsetResyncRule.Decision.PAUSE);
    }
}
