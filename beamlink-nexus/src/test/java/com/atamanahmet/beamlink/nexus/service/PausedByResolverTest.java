package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PausedByResolverTest {

    @Test
    void sourceWinsInAllCombinations() {
        assertThat(PausedByResolver.resolvePausedBy(null, null)).isNull();
        assertThat(PausedByResolver.resolvePausedBy(null, PauseOwner.SOURCE))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(null, PauseOwner.TARGET))
                .isEqualTo(PauseOwner.TARGET);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.SOURCE, null))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.SOURCE, PauseOwner.SOURCE))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.SOURCE, PauseOwner.TARGET))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.TARGET, null))
                .isNull();
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.TARGET, PauseOwner.SOURCE))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.TARGET, PauseOwner.TARGET))
                .isEqualTo(PauseOwner.TARGET);
    }
}
