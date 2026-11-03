package com.atamanahmet.beamlink.nexus.exception;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes ProblemDetail JSON for filters and handshake. Not a servlet error page.
 */
public final class ProblemDetailWriter {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ProblemDetailWriter() {
    }

    /**
     * Writes the RFC 9457 body and status on a servlet response.
     */
    public static void write(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code,
            ErrorReason reason) throws IOException {
        ProblemDetail pd = ProblemDetails.of(status, title, detail, code, reason);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), ProblemDetails.asMap(pd));
    }

    /**
     * Writes the same body on a handshake ServerHttpResponse.
     */
    public static void write(
            ServerHttpResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code,
            ErrorReason reason) throws IOException {
        if (response instanceof ServletServerHttpResponse servlet) {
            write(servlet.getServletResponse(), status, title, detail, code, reason);
            return;
        }
        ProblemDetail pd = ProblemDetails.of(status, title, detail, code, reason);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        MAPPER.writeValue(response.getBody(), ProblemDetails.asMap(pd));
    }
}
