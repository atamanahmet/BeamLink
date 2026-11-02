package com.atamanahmet.beamlink.nexus.service;

import org.springframework.stereotype.Component;

/**
 * Register, challenge and token failures per remote address.
 */
@Component
public class EnrollmentThrottle extends AddressThrottle {
}
