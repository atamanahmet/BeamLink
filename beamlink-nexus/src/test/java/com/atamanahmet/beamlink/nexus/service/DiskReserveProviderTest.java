package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiskReserveProviderTest {

    @Test
    void refreshReturnsSavedValue() {
        SettingsService settings = mock(SettingsService.class);
        DiskReserveProvider provider = new DiskReserveProvider(settings);
        when(settings.get(SettingKey.MIN_FREE_SPACE_BYTES)).thenReturn(Optional.empty());
        provider.refresh();
        assertThat(provider.getBytes()).isEqualTo(1073741824L);

        when(settings.get(SettingKey.MIN_FREE_SPACE_BYTES))
                .thenReturn(Optional.of("268435456"));
        provider.refresh();
        assertThat(provider.getBytes()).isEqualTo(268435456L);
    }
}
