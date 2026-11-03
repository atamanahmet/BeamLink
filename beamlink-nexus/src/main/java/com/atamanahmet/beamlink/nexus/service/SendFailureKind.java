package com.atamanahmet.beamlink.nexus.service;

/**
 * How the sender should treat a chunk send failure.
 */
public enum SendFailureKind {
    OFFLINE,
    RETRY,
    RESYNC,
    CHUNK_CORRUPT,
    FATAL
}
