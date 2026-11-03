package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.web.CorrelationIdFilter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * One log line per error. Detail and cause chain stay in the log.
 */
public final class ErrorLog {

    private ErrorLog() {
    }

    /**
     * WARN for 4xx and TLS, no stack. ERROR for other 5xx. UNKNOWN 5xx also stack.
     */
    public static void log(
            Logger log,
            HttpStatus status,
            String code,
            ErrorReason reason,
            String detail,
            Throwable ex) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String chain = formatChain(ex);
        boolean client = status.is4xxClientError();
        boolean tls = reason == ErrorReason.TLS_TRUST || reason == ErrorReason.TLS_OTHER;
        boolean stack = !client && !tls && reason == ErrorReason.UNKNOWN;
        String pattern = "correlationId={} status={} code={} reason={} detail={} chain={}";
        if (client || tls) {
            log.warn(pattern, correlationId, status.value(), code, reason, detail, chain);
        } else if (stack) {
            log.error(pattern, correlationId, status.value(), code, reason, detail, chain, ex);
        } else {
            log.error(pattern, correlationId, status.value(), code, reason, detail, chain);
        }
    }

    /**
     * Class and message for each cause, outer first.
     */
    public static String formatChain(Throwable ex) {
        if (ex == null) {
            return "";
        }
        List<Throwable> list = ExceptionUtils.getThrowableList(ex);
        StringBuilder sb = new StringBuilder();
        for (Throwable t : list) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getName()).append(": ").append(t.getMessage());
        }
        return sb.toString();
    }
}
