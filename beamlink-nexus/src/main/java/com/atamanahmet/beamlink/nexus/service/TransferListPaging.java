package com.atamanahmet.beamlink.nexus.service;

/**
 * Clamps admin transfer list page size.
 */
public final class TransferListPaging {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private TransferListPaging() {}

    /**
     * Missing, zero or negative become 50. Values over 200 become 200.
     */
    public static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * Missing or negative page becomes 0.
     */
    public static int clampPage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }
}
