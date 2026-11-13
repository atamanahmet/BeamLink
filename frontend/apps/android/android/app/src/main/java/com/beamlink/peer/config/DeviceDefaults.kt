package com.beamlink.peer.config

/**
 * Named defaults matching the desktop device sender and register port.
 */
object DeviceDefaults {

    /** Desktop device default from beamlink-peer application.yaml peer port. */
    const val DEVICE_PORT = 9090

    /** Desktop TransferProperties.chunkSizeBytes default. */
    const val CHUNK_SIZE_BYTES = 524_288

    /** Desktop FileTransfer.initiate maxRetries default. */
    const val MAX_RETRIES = 5

    /** Desktop TransferConstants.RETRY_DELAY_MS. */
    const val RETRY_DELAY_MS = 2000L

    /** Desktop TransferConstants.CHUNK_TIMEOUT_SECONDS. */
    const val CHUNK_TIMEOUT_SECONDS = 30L

    /** Desktop TransferConstants.REQUEST_TIMEOUT_SECONDS. */
    const val REQUEST_TIMEOUT_SECONDS = 30L

    /** OkHttp connect timeout for nexus and peer calls. */
    const val CONNECT_TIMEOUT_MS = 15_000L

    /**
     * OkHttp read timeout. Long enough for one 512 KB chunk on slow Wi-Fi.
     */
    const val READ_TIMEOUT_MS = 60_000L

    /** Poll identify while the registration screen is open. */
    const val IDENTIFY_POLL_INTERVAL_MS = 2000L

    /** Desktop TransferConstants.CHUNK_SHA256_HEADER. */
    const val CHUNK_SHA256_HEADER = "X-Chunk-SHA256"

    /** Desktop TransferConstants.STALL_TIMEOUT_SECONDS. */
    const val STALL_TIMEOUT_SECONDS = 60L
}
