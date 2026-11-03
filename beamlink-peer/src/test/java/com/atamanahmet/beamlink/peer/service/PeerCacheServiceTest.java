package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.domain.Peer;
import com.atamanahmet.beamlink.peer.dto.PeerDTO;
import com.atamanahmet.beamlink.peer.repository.PeerRepository;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeerCacheServiceTest {

    @Test
    void refreshFromNexusFillsCacheWithoutTokenField() {
        assertThat(Arrays.stream(PeerDTO.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(n -> n.toLowerCase().contains("token"))).isFalse();

        UUID peerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID publicId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID selfId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String body = """
                {
                  "version": 7,
                  "peers": [
                    {
                      "id": "%s",
                      "deviceName": "peer-a",
                      "ipAddress": "10.0.0.2",
                      "port": 8080,
                      "online": true,
                      "publicId": "%s",
                      "lastSeen": 123
                    }
                  ]
                }
                """.formatted(peerId, publicId);

        ExchangeFunction exchange = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        PeerRepository peerRepository = mock(PeerRepository.class);
        when(peerRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        NexusAddressHolder nexusAddressHolder = mock(NexusAddressHolder.class);
        when(nexusAddressHolder.isResolved()).thenReturn(true);
        when(nexusAddressHolder.getNexusUrl()).thenReturn("http://127.0.0.1:7472");
        NexusTlsPin pin = mock(NexusTlsPin.class);
        when(pin.hasPin()).thenReturn(true);

        PeerCacheService cache = new PeerCacheService(
                webClient, peerRepository, nexusAddressHolder, mock(ApplicationEventPublisher.class),
                pin);
        cache.refreshPeersFromNexus(selfId, "own-public-token");

        List<Peer> peers = cache.getAllPeers(selfId, "own-public-token");
        assertThat(peers).hasSize(1);
        Peer peer = peers.get(0);
        assertThat(peer.getDeviceId()).isEqualTo(peerId);
        assertThat(peer.getDeviceName()).isEqualTo("peer-a");
        assertThat(peer.getIpAddress()).isEqualTo("10.0.0.2");
        assertThat(peer.getPort()).isEqualTo(8080);
        assertThat(peer.isOnline()).isTrue();
        assertThat(peer.getPublicId()).isEqualTo(publicId);
        verify(peerRepository).deleteAll();
        verify(peerRepository).saveAll(anyList());
    }
}
