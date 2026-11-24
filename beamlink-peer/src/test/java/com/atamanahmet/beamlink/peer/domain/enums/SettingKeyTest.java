package com.atamanahmet.beamlink.peer.domain.enums;

import com.atamanahmet.beamlink.peer.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void booleanAcceptsExactLiterals(String raw) {
        assertThatCode(() -> SettingKey.FIRST_RUN_COMPLETED.validate(raw))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "FALSE", "True", "garbage", "1", "0"})
    void booleanRejectsNonExactLiterals(String raw) {
        assertThatThrownBy(() -> SettingKey.FIRST_RUN_COMPLETED.validate(raw))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_SETTING");
    }

    @Test
    void booleanRejectsEmpty() {
        assertThatThrownBy(() -> SettingKey.FIRST_RUN_COMPLETED.validate(""))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_SETTING");
    }

    @Test
    void firstRunCompletedDefaultsToFalse() {
        assertThat(SettingKey.FIRST_RUN_COMPLETED.defaultValue()).isEqualTo("false");
        assertThat(SettingKey.FIRST_RUN_COMPLETED.type())
                .isEqualTo(SettingKey.SettingType.BOOLEAN);
    }

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
