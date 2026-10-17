package com.atamanahmet.beamlink.agent.service;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;

/**
 * Detects connection-level peer down errors in a cause chain.
 */
public final class PeerUnreachable {

    private static final int MAX_DEPTH = 10;

    private PeerUnreachable() {}

    /**
     * True when the chain has connect, timeout, unknown host, or JDK wrap causes.
     */
    public static boolean isPeerUnreachable(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof HttpTimeoutException
                    || current instanceof ClosedChannelException
                    || current instanceof UnresolvedAddressException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
