package com.atamanahmet.beamlink.agent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Discovers Nexus node via mDNS on startup.
 * Retries every 30s until found, then switches to passive listener.
 * Skipped if NexusAddressHolder already has a URL (manual setup).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusMdnsDiscovery implements ApplicationListener<WebServerInitializedEvent> {

    private static final String SERVICE_TYPE = "_beamlink._tcp.local.";
    private static final int    TIMEOUT_MS   = 3000;
    private static final int    RETRY_SEC    = 30;

    private final NexusAddressHolder nexusAddressHolder;
    private final AgentNetworkConfig networkConfig;

    private JmDNS jmDNS;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mdns-discovery");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> retryTask;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (nexusAddressHolder.isResolved()) {
            log.info("mDNS discovery skipped, Nexus URL already known: {}", nexusAddressHolder.getNexusUrl());
            return;
        }

        try {
            InetAddress localAddress = InetAddress.getByName(networkConfig.getResolvedIp());
            jmDNS = JmDNS.create(localAddress);
            attachListener();
        } catch (IOException e) {
            log.warn("mDNS init failed: {}. Manual setup required.", e.getMessage());
            return;
        }

        // try immediately, then retry every 30s until nexus is found
        tryDiscover();
        retryTask = scheduler.scheduleWithFixedDelay(
                this::tryDiscover, RETRY_SEC, RETRY_SEC, TimeUnit.SECONDS
        );
        log.info("mDNS retry scheduled every {}s", RETRY_SEC);
    }

    /**
     * Passive listener, fires when nexus announces itself on the network.
     * No polling, OS multicast handles it.
     */
    private void attachListener() {
        jmDNS.addServiceListener(SERVICE_TYPE, new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                log.info("mDNS: Nexus service removed from network.");
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                ServiceInfo info = event.getInfo();
                String[] addresses = info.getHostAddresses();
                if (addresses == null || addresses.length == 0) return;

                String url = "http://" + addresses[0] + ":" + info.getPort();
                applyNexusUrl(url, "mDNS listener");
            }
        });
    }

    private void tryDiscover() {
        if (nexusAddressHolder.isResolved()) {
            cancelRetry();
            return;
        }

        log.debug("mDNS: Scanning for Nexus...");
        ServiceInfo[] services = jmDNS.list(SERVICE_TYPE, TIMEOUT_MS);

        if (services == null || services.length == 0) {
            log.debug("mDNS: No Nexus found, will retry in {}s", RETRY_SEC);
            return;
        }

        String[] addresses = services[0].getHostAddresses();
        if (addresses == null || addresses.length == 0) {
            log.warn("mDNS: Nexus found but no address, will retry in {}s", RETRY_SEC);
            return;
        }

        String url = "http://" + addresses[0] + ":" + services[0].getPort();
        applyNexusUrl(url, "mDNS scan");
    }

    private void applyNexusUrl(String url, String source) {
        if (nexusAddressHolder.isResolved()) return;

        nexusAddressHolder.setNexusUrl(url);
        log.info("Nexus discovered via {} at {}", source, url);
        cancelRetry();
    }

    private void cancelRetry() {
        if (retryTask != null && !retryTask.isDone()) {
            retryTask.cancel(false);
            log.debug("mDNS retry cancelled, Nexus is known.");
        }
    }

    /**
     * Keep JmDNS alive until app shuts down, listener needs it open.
     */
    @PreDestroy
    public void shutdown() {
        cancelRetry();
        scheduler.shutdownNow();
        if (jmDNS != null) {
            try {
                jmDNS.close();
            } catch (IOException e) {
                log.warn("JmDNS close error: {}", e.getMessage());
            }
        }
    }
}