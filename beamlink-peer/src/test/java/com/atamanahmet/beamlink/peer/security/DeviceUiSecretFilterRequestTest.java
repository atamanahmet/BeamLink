package com.atamanahmet.beamlink.peer.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DeviceUiSecretFilterRequestTest {

    private DeviceUiSecretFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new DeviceUiSecretFilter();
        ReflectionTestUtils.setField(filter, "uiSecret", "correct-secret");
        chain = mock(FilterChain.class);
    }

    @Test
    void rejectsMissingHeader() throws Exception {
        MockHttpServletRequest request = loopbackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsWrongHeader() throws Exception {
        MockHttpServletRequest request = loopbackRequest();
        request.addHeader(DeviceUiSecretFilter.HEADER, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptsRightHeaderOnLoopback() throws Exception {
        MockHttpServletRequest request = loopbackRequest();
        request.addHeader(DeviceUiSecretFilter.HEADER, "correct-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsNonLoopback() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/device/status");
        request.setRemoteAddr("8.8.8.8");
        request.addHeader(DeviceUiSecretFilter.HEADER, "correct-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Builds a loopback UI API request.
     */
    private static MockHttpServletRequest loopbackRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/device/status");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
