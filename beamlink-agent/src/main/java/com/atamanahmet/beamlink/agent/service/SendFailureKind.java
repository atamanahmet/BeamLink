package com.atamanahmet.beamlink.agent.service;

/**
 * How the sender should treat a chunk send failure.
 */
public enum SendFailureKind {
    OFFLINE,
    RETRY,
    FATAL
}
