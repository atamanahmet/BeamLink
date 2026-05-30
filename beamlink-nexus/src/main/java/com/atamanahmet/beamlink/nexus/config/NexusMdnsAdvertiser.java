package com.atamanahmet.beamlink.nexus.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

/**
 * Advertises Nexus on the LAN via mDNS so agents can discover it
 * without hardcoded IP/port config.
 *
 * Service type: _beamlink._tcp.local.
 * Agents query this type and get back the Nexus IP + bound port.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusMdnsAdvertiser implements ApplicationListener<WebServerInitializedEvent> {

    private static final String SERVICE_TYPE = "_beamlink._tcp.local.";
    private static final String SERVICE_NAME = "BeamlinkNexus";

    private final NexusNetworkConfig networkConfig;
    private final NexusConfig nexusConfig;

    private JmDNS jmDNS;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String ip = networkConfig.getResolvedIp();

        try {
            InetAddress address = InetAddress.getByName(ip);
            jmDNS = JmDNS.create(address);

            ServiceInfo serviceInfo = ServiceInfo.create(
                    SERVICE_TYPE,
                    SERVICE_NAME,
                    port,
                    "name=" + nexusConfig.getName()
            );

            jmDNS.registerService(serviceInfo);
            log.info("mDNS: Nexus advertised as '{}' on {}:{}", SERVICE_NAME, ip, port);

        } catch (IOException e) {
            log.warn("mDNS advertisement failed — agents must use manual config. Reason: {}", e.getMessage());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        if (jmDNS != null) {
            try {
                jmDNS.unregisterAllServices();
                jmDNS.close();
                log.info("mDNS: Nexus advertisement stopped");
            } catch (IOException e) {
                log.warn("mDNS shutdown error: {}", e.getMessage());
            }
        }
    }
}