package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSessionDaysTest {

    @Test
    void whitelistAcceptsKnownValues() {
        assertTrue(AdminSessionDays.isAllowed("1"));
        assertTrue(AdminSessionDays.isAllowed("7"));
        assertTrue(AdminSessionDays.isAllowed("30"));
        assertTrue(AdminSessionDays.isAllowed("365"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "14", "90", "forever", "until_logout", "UNTIL_LOGOUT"})
    void whitelistRejectsUnknownValues(String value) {
        assertThrows(InvalidRequestException.class, () -> AdminSessionDays.normalize(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void blankDefaultsToOneDay(String value) {
        assertEquals("1", AdminSessionDays.normalize(value));
        assertEquals(1, AdminSessionDays.toExpirationDays(value));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "7, 7",
            "30, 30",
            "365, 365"
    })
    void sessionDaysToExpMapping(String sessionDays, int expectedDays) {
        assertEquals(expectedDays, AdminSessionDays.toExpirationDays(sessionDays));
    }
}
