package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusConnectionStateService {

    private final NexusEventPublisher nexusEventPublisher;

    private final AtomicBoolean nexusWasOffline = new AtomicBoolean(false);

    /**
     * Call when Nexus offline, HTTP or WS disconnect
     */
    public void reportOffline() {
        if (nexusWasOffline.compareAndSet(false, true)) {
            log.warn("Nexus is offline. Publishing offline event.");
            nexusEventPublisher.publishOffline();
        }
    }

    /**
     * Call when Nexus online, HTTP or WS reconnect.
     */
    public void reportOnline() {
        if (nexusWasOffline.compareAndSet(true, false)) {
            log.info("Nexus is back online. Publishing online event.");
            nexusEventPublisher.publishOnline();
        }
    }

    public boolean isNexusConsideredOffline() {
        return nexusWasOffline.get();
    }
}