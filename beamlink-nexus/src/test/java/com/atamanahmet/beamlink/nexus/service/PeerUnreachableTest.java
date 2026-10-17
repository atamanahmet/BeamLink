package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;

import static org.assertj.core.api.Assertions.assertThat;

class PeerUnreachableTest {

    @Test
    void detectsDirectAndWrappedPeerDownErrors() {
        assertThat(PeerUnreachable.isPeerUnreachable(new ConnectException())).isTrue();
        assertThat(PeerUnreachable.isPeerUnreachable(new UnknownHostException())).isTrue();
        assertThat(PeerUnreachable.isPeerUnreachable(new HttpTimeoutException("t"))).isTrue();
        assertThat(PeerUnreachable.isPeerUnreachable(new ClosedChannelException())).isTrue();
        assertThat(PeerUnreachable.isPeerUnreachable(new UnresolvedAddressException())).isTrue();

        ConnectException refused = new ConnectException();
        refused.initCause(new ClosedChannelException());
        assertThat(PeerUnreachable.isPeerUnreachable(
                new FileTransferException("Chunk failed after 5 attempts", refused))).isTrue();

        ConnectException badHost = new ConnectException();
        badHost.initCause(new UnresolvedAddressException());
        assertThat(PeerUnreachable.isPeerUnreachable(
                new FileTransferException("Chunk failed after 5 attempts", badHost))).isTrue();

        assertThat(PeerUnreachable.isPeerUnreachable(new IllegalStateException("other"))).isFalse();
        assertThat(PeerUnreachable.isPeerUnreachable(null)).isFalse();
        assertThat(PeerUnreachable.isPeerUnreachable(new RuntimeException("x", null))).isFalse();
    }
}
