package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferListPagingTest {

    @Test
    void clampSizeMissingZeroNegativeDefaultFifty() {
        assertEquals(50, TransferListPaging.clampSize(null));
        assertEquals(50, TransferListPaging.clampSize(0));
        assertEquals(50, TransferListPaging.clampSize(-3));
    }

    @Test
    void clampSizeFiftyUnchanged() {
        assertEquals(50, TransferListPaging.clampSize(50));
    }

    @Test
    void clampSizeOverMaxBecomesTwoHundred() {
        assertEquals(200, TransferListPaging.clampSize(500));
    }
}
