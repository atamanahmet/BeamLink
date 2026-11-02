package com.atamanahmet.beamlink.nexus.service;

import org.springframework.stereotype.Component;

/**
 * Login failures per remote address. Same window as AddressThrottle.
 */
@Component
public class LoginFailureThrottle extends AddressThrottle {

    public LoginFailureThrottle() {
        super();
    }

    LoginFailureThrottle(long windowMs, int maxFailures) {
        super(windowMs, maxFailures);
    }

    LoginFailureThrottle(long windowMs, int maxFailures, int maxAddresses) {
        super(windowMs, maxFailures, maxAddresses);
    }
}
