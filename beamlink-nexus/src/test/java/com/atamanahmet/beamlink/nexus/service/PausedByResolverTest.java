package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PausedByResolverTest {

    @Test
    void sourceWinsInAllCombinations() {
        assertThat(PausedByResolver.resolvePausedBy(null, PauseOwner.SOURCE))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(null, PauseOwner.TARGET))
                .isEqualTo(PauseOwner.TARGET);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.SOURCE, PauseOwner.SOURCE))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.SOURCE, PauseOwner.TARGET))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.TARGET, PauseOwner.SOURCE))
                .isEqualTo(PauseOwner.SOURCE);
        assertThat(PausedByResolver.resolvePausedBy(PauseOwner.TARGET, PauseOwner.TARGET))
                .isEqualTo(PauseOwner.TARGET);
    }

    @Test
    void nullPausedByRejectedOnPause() {
        assertThatThrownBy(() -> PausedByResolver.resolvePausedBy(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pausedBy required");
        assertThatThrownBy(() -> PausedByResolver.resolvePausedBy(PauseOwner.SOURCE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pausedBy required");
        assertThatThrownBy(() -> PausedByResolver.resolvePausedBy(PauseOwner.TARGET, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pausedBy required");
    }
}
