package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeStoreTest {

    @Test
    void singleUse() {
        ChallengeStore store = new ChallengeStore(8, 60_000L);
        String ch = store.issue("device-a", 1_000L).orElseThrow();
        assertThat(store.consume("device-a", ch, 1_001L)).isEqualTo(ChallengeStore.ConsumeResult.OK);
        assertThat(store.consume("device-a", ch, 1_002L)).isEqualTo(ChallengeStore.ConsumeResult.MISSING);
    }

    @Test
    void ttlExpired() {
        ChallengeStore store = new ChallengeStore(8, 60_000L);
        String ch = store.issue("device-a", 1_000L).orElseThrow();
        assertThat(store.consume("device-a", ch, 1_000L + 60_000L))
                .isEqualTo(ChallengeStore.ConsumeResult.EXPIRED);
    }

    @Test
    void boundToDeviceId() {
        ChallengeStore store = new ChallengeStore(8, 60_000L);
        String ch = store.issue("device-a", 1_000L).orElseThrow();
        assertThat(store.consume("device-b", ch, 1_001L))
                .isEqualTo(ChallengeStore.ConsumeResult.WRONG_DEVICE);
        assertThat(store.consume("device-a", ch, 1_002L))
                .isEqualTo(ChallengeStore.ConsumeResult.MISSING);
    }

    @Test
    void perDeviceCapRejectsWithoutEvict() {
        ChallengeStore store = new ChallengeStore(1024, 60_000L, 5);
        String first = store.issue("a", 1L).orElseThrow();
        for (int i = 0; i < 4; i++) {
            assertThat(store.issue("a", 2L + i)).isPresent();
        }
        assertThat(store.issue("a", 10L)).isEmpty();
        assertThat(store.size()).isEqualTo(5);
        assertThat(store.consume("a", first, 11L)).isEqualTo(ChallengeStore.ConsumeResult.OK);
    }

    @Test
    void globalCapRejectsWithoutEvict() {
        ChallengeStore store = new ChallengeStore(2, 60_000L, 5);
        String first = store.issue("a", 1L).orElseThrow();
        assertThat(store.issue("b", 2L)).isPresent();
        assertThat(store.issue("c", 3L)).isEmpty();
        assertThat(store.size()).isEqualTo(2);
        assertThat(store.consume("a", first, 4L)).isEqualTo(ChallengeStore.ConsumeResult.OK);
    }

    @Test
    void ttlSweepFreesCap() {
        ChallengeStore store = new ChallengeStore(8, 60_000L, 1);
        assertThat(store.issue("a", 1_000L)).isPresent();
        assertThat(store.issue("a", 1_001L)).isEmpty();
        store.purge(1_000L + 60_000L);
        assertThat(store.size()).isZero();
        assertThat(store.issue("a", 1_000L + 60_000L)).isPresent();
    }
}
