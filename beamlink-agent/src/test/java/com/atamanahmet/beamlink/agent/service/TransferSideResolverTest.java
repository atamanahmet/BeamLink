package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.enums.PauseOwner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSideResolverTest {

    @Test
    void oppositeFlipsLocalSide() {
        assertThat(TransferSideResolver.opposite(PauseOwner.SOURCE))
                .isEqualTo(PauseOwner.TARGET);
        assertThat(TransferSideResolver.opposite(PauseOwner.TARGET))
                .isEqualTo(PauseOwner.SOURCE);
    }
}
