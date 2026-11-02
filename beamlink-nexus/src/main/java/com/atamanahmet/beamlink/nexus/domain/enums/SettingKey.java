package com.atamanahmet.beamlink.nexus.domain.enums;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;

/**
 * Typed settings registry. Bounds live here, not in the DB.
 */
public enum SettingKey {
    JWT_SECRET(SettingType.STRING, null, null, null),
    STORAGE_UPLOAD_DIR(SettingType.STRING, null, null, null),
    NEXUS_NAME(SettingType.STRING, null, null, null),
    RSA_PRIVATE_KEY(SettingType.STRING, null, null, null),
    RSA_PUBLIC_KEY(SettingType.STRING, null, null, null),
    TRANSFER_SPEED_CAP_MBPS(SettingType.DOUBLE, null, null, null),
    TOTAL_SPEED_LIMIT_MBPS(SettingType.DOUBLE, null, null, null),
    TRANSFER_RESUME_POLICY(SettingType.STRING, null, null, null),
    MIN_FREE_SPACE_BYTES(SettingType.LONG, "1073741824", 268435456L, 1099511627776L);

    public enum SettingType {
        STRING,
        DOUBLE,
        LONG
    }

    private final SettingType type;
    private final String defaultValue;
    private final Long min;
    private final Long max;

    SettingKey(SettingType type, String defaultValue, Long min, Long max) {
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    public SettingType type() {
        return type;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public Long min() {
        return min;
    }

    public Long max() {
        return max;
    }

    /**
     * Rejects a value that does not match type or bounds.
     */
    public void validate(String raw) {
        if (raw == null) {
            throw invalid();
        }
        if (type == SettingType.STRING) {
            return;
        }
        if (type == SettingType.LONG) {
            long n;
            try {
                n = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                throw invalid();
            }
            if ((min != null && n < min) || (max != null && n > max)) {
                throw invalid();
            }
            return;
        }
        double n;
        try {
            n = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if ((min != null && n < min) || (max != null && n > max)) {
            throw invalid();
        }
    }

    private InvalidRequestException invalid() {
        if (min != null && max != null) {
            return new InvalidRequestException(
                    "minFreeSpaceBytes must be between " + min + " and " + max,
                    "INVALID_SETTING");
        }
        return new InvalidRequestException("Invalid setting value", "INVALID_SETTING");
    }
}
