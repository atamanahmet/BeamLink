package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodeFormatTest {

    @Test
    void normalizeStripsDashesSpacesAndCase() {
        assertThat(RecoveryCodeFormat.normalize("ABCD-efgh 1234-5678"))
                .isEqualTo("abcdefgh12345678");
        assertThat(RecoveryCodeFormat.normalize(null)).isEmpty();
        assertThat(RecoveryCodeFormat.normalize("  ")).isEmpty();
    }

    @Test
    void formatDisplayGroupsHexByFour() {
        String hex = "0123456789abcdef0123456789abcdef";
        assertThat(RecoveryCodeFormat.formatDisplay(hex))
                .isEqualTo("0123-4567-89ab-cdef-0123-4567-89ab-cdef");
    }
}
