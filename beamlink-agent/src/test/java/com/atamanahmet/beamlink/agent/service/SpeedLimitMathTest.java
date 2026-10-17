package com.atamanahmet.beamlink.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeedLimitMathTest {

    @Test
    void resolveFileLimitAndMbpsConversion() {
        assertThat(SpeedLimitMath.resolveFileLimit(12.0, 80.0)).isEqualTo(12.0);
        assertThat(SpeedLimitMath.resolveFileLimit(null, 80.0)).isEqualTo(80.0);
        assertThat(SpeedLimitMath.resolveFileLimit(null, null)).isNull();
        assertThat(SpeedLimitMath.toBytesPerSecond(1.0)).isEqualTo(125_000L);
        assertThat(SpeedLimitMath.toBytesPerSecond(8.0)).isEqualTo(1_000_000L);
    }
}
