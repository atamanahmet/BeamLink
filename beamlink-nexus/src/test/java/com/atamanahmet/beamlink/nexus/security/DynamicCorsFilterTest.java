package com.atamanahmet.beamlink.nexus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class DynamicCorsFilterTest {

    private DynamicCorsRegistry registry;
    private DynamicCorsFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        registry = new DynamicCorsRegistry("");
        filter = new DynamicCorsFilter(registry);
        chain = mock(FilterChain.class);
    }

    @Test
    void nullOriginFromLoopbackAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "null");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals("null", response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
        assertEquals("Origin", response.getHeader("Vary"));
    }

    @Test
    void nullOriginFromNonLoopbackGetsNoAcaoHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "null");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("Origin", response.getHeader("Vary"));
    }

    @Test
    void allowedGetExposesRetryAfter() throws Exception {
        registry.register("http://localhost:5173");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/nexus/auth/login");
        request.addHeader("Origin", "http://localhost:5173");
        request.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain live = new MockFilterChain();

        filter.doFilter(request, response, live);

        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("Retry-After", response.getHeader("Access-Control-Expose-Headers"));
        assertEquals(request, live.getRequest());
    }

    @Test
    void nullOriginLoopbackExposesRetryAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/nexus/auth/login");
        request.addHeader("Origin", "null");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain live = new MockFilterChain();

        filter.doFilter(request, response, live);

        assertEquals("null", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("Retry-After", response.getHeader("Access-Control-Expose-Headers"));
        assertEquals(request, live.getRequest());
    }

    @Test
    void unknownOriginHasNoAcaoOrExpose() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/nexus/auth/login");
        request.addHeader("Origin", "http://evil.example");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain live = new MockFilterChain();

        filter.doFilter(request, response, live);

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Expose-Headers"));
        assertEquals(request, live.getRequest());
    }

    @Test
    void allowedOptionsDoesNotCallChain() throws Exception {
        registry.register("http://localhost:5173");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/nexus/auth/login");
        request.addHeader("Origin", "http://localhost:5173");
        request.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain live = new MockFilterChain();

        filter.doFilter(request, response, live);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("Retry-After", response.getHeader("Access-Control-Expose-Headers"));
        assertNull(live.getRequest());
    }
}
