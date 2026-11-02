package com.atamanahmet.beamlink.nexus.security.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {

    ADMIN("ROLE_ADMIN"),
    DEVICE("ROLE_DEVICE"),
    DEVICE_PUBLIC("ROLE_DEVICE_PUBLIC");

    private final String authority;
}