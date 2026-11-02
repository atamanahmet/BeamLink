package com.atamanahmet.beamlink.nexus.security.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum TokenType {
    ADMIN,
    DEVICE_AUTH,
    DEVICE_PUBLIC,
    NEXUS;
}