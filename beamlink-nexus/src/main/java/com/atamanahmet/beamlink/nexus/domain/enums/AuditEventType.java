package com.atamanahmet.beamlink.nexus.domain.enums;

public enum AuditEventType {
    LOGIN,
    SETUP,
    CREDENTIALS_CHANGED,
    PASSWORD_RESET,
    RECOVERY_CODE_REGENERATED,
    LOGOUT_ALL,
    ERASE_ALL,
    FACTORY_RESET,
    DEVICE_APPROVED,
    DEVICE_REJECTED,
    DEVICE_REMOVED,
    DEVICE_RENAMED
}
