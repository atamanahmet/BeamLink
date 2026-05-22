package com.atamanahmet.beamlink.agent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

/**
 * Queries mDNS for a Nexus node after the web server starts.
 * Skipped entirely if NexusAddressHolder already has a URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusMdnsDiscovery implements ApplicationListener<WebServerInitializedEvent> {

    private static final String SERVICE_TYPE  = "_beamlink._tcp.local.";
    private static final int    TIMEOUT_MS    = 3000;
    private static final int    DEFAULT_PORT  = 7472;

    private final NexusAddressHolder nexusAddressHolder;
    private final AgentNetworkConfig networkConfig;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (nexusAddressHolder.isResolved()) {
            log.info("mDNS discovery skipped — Nexus URL already known: {}", nexusAddressHolder.getNexusUrl());
            return;
        }

        log.info("Starting mDNS discovery for Nexus...");
        discover();
    }

    private void discover() {
        try {
            InetAddress localAddress = InetAddress.getByName(networkConfig.getResolvedIp());

            try (JmDNS jmDNS = JmDNS.create(localAddress)) {
                ServiceInfo[] services = jmDNS.list(SERVICE_TYPE, TIMEOUT_MS);

                if (services == null || services.length == 0) {
                    log.warn("mDNS: No Nexus found on the network. Manual setup required.");
                    return;
                }

                ServiceInfo nexus = services[0];
                String[] addresses = nexus.getHostAddresses();

                if (addresses == null || addresses.length == 0) {
                    log.warn("mDNS: Nexus service found but has no address. Manual setup required.");
                    return;
                }

                String url = "http://" + addresses[0] + ":" + nexus.getPort();
                nexusAddressHolder.setNexusUrl(url);
                log.info("mDNS: Nexus discovered at {}", url);
            }

        } catch (IOException e) {
            // Non-fatal — manual setup UI handles this case
            log.warn("mDNS discovery failed: {}", e.getMessage());
        }
    }
}