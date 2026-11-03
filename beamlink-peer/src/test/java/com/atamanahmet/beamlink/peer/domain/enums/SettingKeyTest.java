package com.atamanahmet.beamlink.peer.domain.enums;

import com.atamanahmet.beamlink.peer.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingKeyTest {

    @Test
    void minFreeSpaceBelowMinIsInvalid() {
        assertThatThrownBy(() -> SettingKey.MIN_FREE_SPACE_BYTES.validate("268435455"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> {
                    InvalidRequestException e = (InvalidRequestException) ex;
                    assertThat(e.getCode()).isEqualTo("INVALID_SETTING");
                    assertThat(e.getMessage()).contains("268435456");
                    assertThat(e.getMessage()).contains("1099511627776");
                });
    }

    @Test
    void minFreeSpaceMinIsValid() {
        assertThatCode(() -> SettingKey.MIN_FREE_SPACE_BYTES.validate("268435456"))
                .doesNotThrowAnyException();
    }

    @Test
    void minFreeSpaceMaxIsValid() {
        assertThatCode(() -> SettingKey.MIN_FREE_SPACE_BYTES.validate("1099511627776"))
                .doesNotThrowAnyException();
    }

    @Test
    void minFreeSpaceAboveMaxIsInvalid() {
        assertThatThrownBy(() -> SettingKey.MIN_FREE_SPACE_BYTES.validate("1099511627777"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_SETTING");
    }

    @Test
    void minFreeSpaceWrongTypeIsInvalid() {
        assertThatThrownBy(() -> SettingKey.MIN_FREE_SPACE_BYTES.validate("nope"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_SETTING");
    }
}
