package com.atamanahmet.beamlink.peer.security.filter;

import com.atamanahmet.beamlink.peer.security.PeerPrincipal;
import com.atamanahmet.beamlink.peer.security.PeerPublicTokenTestHelper;
import com.atamanahmet.beamlink.peer.security.PeerTokenVerifier;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeerTokenFilterTest {

    private PeerPublicTokenTestHelper keys;
    private DeviceService deviceService;
    private PeerTokenFilter filter;

    @BeforeEach
    void setUp() {
        keys = new PeerPublicTokenTestHelper();
        deviceService = mock(DeviceService.class);
        when(deviceService.getNexusPublicKey()).thenReturn(keys.nexusPublicKeyB64);
        filter = new PeerTokenFilter(new PeerTokenVerifier(), deviceService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterSetsPeerPrincipalWithDeviceId() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String token = keys.sign(deviceId, publicId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transfers/x/offset");
        request.addHeader("X-Public-Token", token);
        request.addHeader("X-Public-Id", publicId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).isInstanceOf(PeerPrincipal.class);
        PeerPrincipal peer = (PeerPrincipal) principal;
        assertThat(peer.deviceId()).isEqualTo(deviceId);
        assertThat(peer.publicId()).isEqualTo(publicId.toString());
    }
}
