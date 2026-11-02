package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusAuthServicePasswordTest {

    @Test
    void setupAndChangeRejectPasswordUnder8() {
        NexusService nexusService = mock(NexusService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NexusAuthService auth = new NexusAuthService(
                nexusService, mock(DeviceTokenService.class), encoder);

        when(nexusService.hasPassword()).thenReturn(false);
        assertThrows(InvalidRequestException.class, () -> auth.setup("admin", "short12"));

        when(nexusService.getEncodedPassword()).thenReturn("hash");
        when(encoder.matches("old-pass", "hash")).thenReturn(true);
        assertThrows(InvalidRequestException.class,
                () -> auth.changeCredentials("old-pass", "admin", "short12"));
    }
}
