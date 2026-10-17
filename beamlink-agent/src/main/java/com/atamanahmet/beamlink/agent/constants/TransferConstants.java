package com.atamanahmet.beamlink.agent.constants;

public final class TransferConstants {

    private TransferConstants() {}

    public static final long RETRY_DELAY_MS = 2000L;
    public static final long PAUSE_HOLD_POLL_MS = 500L;
    public static final long CHUNK_TIMEOUT_SECONDS = 30L;
    public static final long NOTIFY_TIMEOUT_SECONDS = 5L;
    public static final long REQUEST_TIMEOUT_SECONDS = 30L;
    public static final long REGISTER_TIMEOUT_SECONDS = 120L;
    public static final long STALL_TIMEOUT_SECONDS = 60L;
}