package com.atamanahmet.beamlink.peer.config;

import com.atamanahmet.beamlink.peer.service.MdnsDiscoveryRule;
import com.atamanahmet.beamlink.peer.service.NexusFirstContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Discovers Nexus on the LAN via mDNS and suggests the address.
 * Scans until a suggestion exists, then the listener stays for later announces.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusMdnsDiscovery implements ApplicationListener<WebServerInitializedEvent> {

    private static final String SERVICE_TYPE = "_beamlink._tcp.local.";
    private static final int    TIMEOUT_MS   = 3000;
    private static final int    RETRY_SEC    = 30;

    private final NexusAddressHolder nexusAddressHolder;
    private final NexusFirstContactService firstContact;
    private final DeviceNetworkConfig networkConfig;

    private JmDNS jmDNS;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mdns-discovery");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> retryTask;
    private final AtomicBoolean listenerAttached = new AtomicBoolean(false);

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        startOrRestart();
    }

    /**
     * Starts or restarts the one mDNS scan. Used on boot and after factory reset.
     */
    public synchronized void startOrRestart() {
        if (nexusAddressHolder.isResolved()) {
            cancelRetry();
            log.info("mDNS discovery skipped, Nexus URL already known: {}", nexusAddressHolder.getNexusUrl());
            return;
        }
        if (!ensureJmDns()) {
            return;
        }
        cancelRetry();
        retryTask = scheduler.scheduleWithFixedDelay(
                this::tryDiscover, 0, RETRY_SEC, TimeUnit.SECONDS);
        log.info("mDNS retry scheduled every {}s", RETRY_SEC);
    }

    /**
     * Passive listener, fires when nexus announces itself on the network.
     */
    private void attachListener() {
        if (!listenerAttached.compareAndSet(false, true)) {
            return;
        }
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

                String url = "https://" + addresses[0] + ":" + info.getPort();
                applyNexusUrl(url, "mDNS listener");
            }
        });
    }

    private void tryDiscover() {
        if (MdnsDiscoveryRule.stopScan(firstContact.hasSuggestion(), nexusAddressHolder.isResolved())) {
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

        String url = "https://" + addresses[0] + ":" + services[0].getPort();
        applyNexusUrl(url, "mDNS scan");
    }

    private void applyNexusUrl(String url, String source) {
        firstContact.suggest(url);
        log.info("Nexus suggested via {} at {}", source, url);
        cancelRetry();
    }

    private boolean ensureJmDns() {
        if (jmDNS != null) {
            return true;
        }
        try {
            InetAddress localAddress = InetAddress.getByName(networkConfig.getResolvedIp());
            jmDNS = JmDNS.create(localAddress);
            attachListener();
            return true;
        } catch (IOException e) {
            log.warn("mDNS init failed: {}. Manual setup required.", e.getMessage());
            return false;
        }
    }

    /**
     * Stops the retry. Safe twice.
     */
    public synchronized void cancelRetry() {
        ScheduledFuture<?> task = retryTask;
        retryTask = null;
        if (task != null) {
            task.cancel(false);
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
