package com.atamanahmet.beamlink.nexus.security.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LoopbackOnlyFilterTest {

    private LoopbackOnlyFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new LoopbackOnlyFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    void restrictedLoopbackIpv4PassesChain() throws Exception {
        MockHttpServletRequest request = restricted("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void restrictedRemoteGetsForbiddenAndSkipsChain() throws Exception {
        MockHttpServletRequest request = restricted("192.168.1.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("FORBIDDEN"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void deviceRouteFromRemotePassesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/ping");
        request.setRemoteAddr("192.168.1.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void restrictedLoopbackIpv6PassesChain() throws Exception {
        MockHttpServletRequest request = restricted("::1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void restrictedIpv4MappedLoopbackPassesChain() throws Exception {
        MockHttpServletRequest request = restricted("::ffff:127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void encodedAdminPrefixFromRemoteGetsForbiddenAndSkipsChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/nexus/%61dmin/devices");
        request.setServletPath("/api/nexus/admin/devices");
        request.setRemoteAddr("192.168.1.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("FORBIDDEN"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void exactAdminPathFromRemoteGetsForbiddenAndSkipsChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/nexus/admin");
        request.setRemoteAddr("192.168.1.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("FORBIDDEN"));
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Builds a restricted auth request from the given remote address.
     */
    private static MockHttpServletRequest restricted(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/nexus/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
