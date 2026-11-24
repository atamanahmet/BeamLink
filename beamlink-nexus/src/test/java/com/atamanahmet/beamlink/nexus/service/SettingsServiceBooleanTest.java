package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Setting;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.repository.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceBooleanTest {

    private SettingsRepository settingsRepository;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(SettingsRepository.class);
        settingsService = new SettingsService(settingsRepository);
    }

    @Test
    void getBooleanUsesDefaultFalseWhenMissing() {
        when(settingsRepository.findByKey(SettingKey.FIRST_RUN_COMPLETED))
                .thenReturn(Optional.empty());

        assertThat(settingsService.getBoolean(SettingKey.FIRST_RUN_COMPLETED)).isFalse();
    }

    @Test
    void getBooleanCorruptValueThrowsInvalidRequest() {
        when(settingsRepository.findByKey(SettingKey.FIRST_RUN_COMPLETED))
                .thenReturn(Optional.of(new Setting(SettingKey.FIRST_RUN_COMPLETED, "TRUE")));

        assertThatThrownBy(() -> settingsService.getBoolean(SettingKey.FIRST_RUN_COMPLETED))
                .isInstanceOf(com.atamanahmet.beamlink.nexus.exception.InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_SETTING");
    }

    @Test
    void booleanRoundTrip() {
        when(settingsRepository.findByKey(SettingKey.FIRST_RUN_COMPLETED))
                .thenReturn(Optional.empty());

        settingsService.set(SettingKey.FIRST_RUN_COMPLETED, true);

        ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
        verify(settingsRepository).save(saved.capture());
        assertThat(saved.getValue().getKey()).isEqualTo(SettingKey.FIRST_RUN_COMPLETED);
        assertThat(saved.getValue().getValue()).isEqualTo("true");

        when(settingsRepository.findByKey(SettingKey.FIRST_RUN_COMPLETED))
                .thenReturn(Optional.of(new Setting(SettingKey.FIRST_RUN_COMPLETED, "true")));
        assertThat(settingsService.getBoolean(SettingKey.FIRST_RUN_COMPLETED)).isTrue();

        settingsService.set(SettingKey.FIRST_RUN_COMPLETED, false);
        when(settingsRepository.findByKey(SettingKey.FIRST_RUN_COMPLETED))
                .thenReturn(Optional.of(new Setting(SettingKey.FIRST_RUN_COMPLETED, "false")));
        assertThat(settingsService.getBoolean(SettingKey.FIRST_RUN_COMPLETED)).isFalse();
    }
}
