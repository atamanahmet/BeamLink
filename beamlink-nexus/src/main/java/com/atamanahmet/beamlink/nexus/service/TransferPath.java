package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Wire path join and receive path checks. Reject only, never sanitize.
 */
public final class TransferPath {

    public static final String DETAIL = "Invalid path";

    static final int MAX_SEGMENT_CHARS = 255;
    static final int MAX_TEXT_CHARS = 1024;

    private static final Set<String> RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private TransferPath() {
    }

    /**
     * Joins path names with / for the wire. Does not use Path.toString.
     */
    public static String join(Path path) {
        if (path == null || path.getNameCount() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.getNameCount(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(path.getName(i).toString());
        }
        return sb.toString();
    }

    /**
     * Checks one path segment and resolves it under root.
     */
    public static Path resolveSegment(Path root, String segment) {
        String rule = segmentRule(segment);
        if (rule != null) {
            throw rejected(rule);
        }
        return resolveUnderValidated(root, segment);
    }

    /**
     * Checks slash-joined relative text and resolves it under root.
     */
    public static Path resolveRelative(Path root, String relativeText) {
        String rule = relativeRule(relativeText);
        if (rule != null) {
            throw rejected(rule);
        }
        return resolveUnderValidated(root, relativeText);
    }

    /**
     * Logs rule, deviceId and length, then rethrows.
     */
    public static void logReject(org.slf4j.Logger log, InvalidRequestException e, UUID deviceId, String text) {
        int length = text == null ? -1 : text.length();
        log.warn("path rejected rule={} deviceId={} length={}", e.getCode(), deviceId, length);
    }

    static String segmentRule(String segment) {
        if (segment == null) {
            return "PATH_NULL";
        }
        if (segment.isEmpty() || segment.isBlank()) {
            return "PATH_BLANK";
        }
        if (segment.length() > MAX_SEGMENT_CHARS) {
            return "PATH_SEGMENT_LENGTH";
        }
        if (".".equals(segment) || "..".equals(segment)) {
            return "PATH_DOT";
        }
        if (segment.indexOf('/') >= 0) {
            return "PATH_SLASH";
        }
        if (segment.indexOf('\\') >= 0) {
            return "PATH_BACKSLASH";
        }
        if (segment.indexOf(':') >= 0) {
            return "PATH_COLON";
        }
        if (segment.charAt(segment.length() - 1) == '.' || segment.charAt(segment.length() - 1) == ' ') {
            return "PATH_TRAILING";
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c <= 0x1F || c == 0x7F) {
                return "PATH_CONTROL";
            }
        }
        if (isReserved(segment)) {
            return "PATH_RESERVED";
        }
        return null;
    }

    static String relativeRule(String text) {
        if (text == null) {
            return "PATH_NULL";
        }
        if (text.isEmpty() || text.isBlank()) {
            return "PATH_BLANK";
        }
        if (text.length() > MAX_TEXT_CHARS) {
            return "PATH_TEXT_LENGTH";
        }
        if (text.indexOf('\\') >= 0) {
            return "PATH_BACKSLASH";
        }
        if (text.charAt(0) == '/' || text.charAt(text.length() - 1) == '/') {
            return "PATH_EMPTY_SEGMENT";
        }
        if (text.contains("//")) {
            return "PATH_EMPTY_SEGMENT";
        }
        String[] parts = text.split("/", -1);
        for (String part : parts) {
            String rule = segmentRule(part);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    private static Path resolveUnderValidated(Path root, String relativeText) {
        Path rootNorm = root.toAbsolutePath().normalize();
        Path resolved;
        try {
            resolved = rootNorm.resolve(relativeText).normalize();
        } catch (InvalidPathException e) {
            throw rejected("PATH_INVALID");
        }
        assertUnderRoot(rootNorm, resolved);
        return resolved;
    }

    /**
     * Rejects paths outside root or equal to root. Uses Path.startsWith.
     */
    static void assertUnderRoot(Path rootNormalizedAbsolute, Path candidate) {
        if (!candidate.startsWith(rootNormalizedAbsolute)) {
            throw rejected("PATH_ESCAPE");
        }
        if (candidate.equals(rootNormalizedAbsolute)) {
            throw rejected("PATH_EQUALS_ROOT");
        }
    }

    private static boolean isReserved(String segment) {
        String base = segment;
        int dot = segment.indexOf('.');
        if (dot >= 0) {
            base = segment.substring(0, dot);
        }
        return RESERVED.contains(base.toUpperCase(Locale.ROOT));
    }

    private static InvalidRequestException rejected(String rule) {
        return new InvalidRequestException(DETAIL, rule);
    }
}
