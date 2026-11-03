package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds RFC 9457 ProblemDetail with code, reason, and correlation id.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /**
     * Fills title, detail, status, and the three extensions.
     */
    public static ProblemDetail of(
            HttpStatus status,
            String title,
            String detail,
            String code,
            ErrorReason reason) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("code", code);
        pd.setProperty("reason", reason.name());
        pd.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return pd;
    }

    /**
     * Flat JSON map matching Spring MVC ProblemDetail output.
     */
    public static Map<String, Object> asMap(ProblemDetail pd) {
        Map<String, Object> map = new LinkedHashMap<>();
        URI type = pd.getType();
        map.put("type", type == null ? "about:blank" : type.toString());
        map.put("title", pd.getTitle());
        map.put("status", pd.getStatus());
        map.put("detail", pd.getDetail());
        if (pd.getProperties() != null) {
            map.putAll(pd.getProperties());
        }
        return map;
    }
}
