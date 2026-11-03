package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.config.NexusMdnsDiscovery;
import com.atamanahmet.beamlink.peer.exception.InvalidRequestException;
import com.atamanahmet.beamlink.peer.exception.NexusTlsPinMismatchException;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Probe and confirm the nexus TLS pin before any nexus request.
 */
@Service
public class NexusFirstContactService {

    private final NexusTlsPin nexusTlsPin;
    private final NexusAddressHolder nexusAddressHolder;
    private final NetworkRegistrationService networkRegistrationService;
    private final NexusMdnsDiscovery mdnsDiscovery;
    private final AtomicReference<String> suggestedUrl = new AtomicReference<>();
    private final AtomicReference<String> lastProbeUrl = new AtomicReference<>();
    private final AtomicReference<String> lastProbeFingerprint = new AtomicReference<>();

    public NexusFirstContactService(
            NexusTlsPin nexusTlsPin,
            NexusAddressHolder nexusAddressHolder,
            @Lazy NetworkRegistrationService networkRegistrationService,
            @Lazy NexusMdnsDiscovery mdnsDiscovery) {
        this.nexusTlsPin = nexusTlsPin;
        this.nexusAddressHolder = nexusAddressHolder;
        this.networkRegistrationService = networkRegistrationService;
        this.mdnsDiscovery = mdnsDiscovery;
    }

    /**
     * mDNS may only suggest an address. Never stores URL or pin.
     */
    public void suggest(String url) {
        suggestedUrl.set(url);
    }

    /**
     * True when mDNS has offered an address that is not stored yet.
     */
    public boolean hasSuggestion() {
        String url = suggestedUrl.get();
        return url != null && !url.isBlank();
    }

    /**
     * Handshake-only probe. Does not store the pin.
     */
    public ProbeResult probe(String ip, int port) {
        String url = httpsUrl(ip, port);
        try {
            String fingerprint = nexusTlsPin.probe(ip.trim(), port);
            lastProbeUrl.set(url);
            lastProbeFingerprint.set(fingerprint);
            return new ProbeResult(url, fingerprint);
        } catch (RuntimeException e) {
            throw new InvalidRequestException(e.getMessage(), e);
        }
    }

    /**
     * Stores pin and URL, then starts enrollment with the same register call the scheduler uses.
     */
    public void confirm(String ip, int port, String fingerprint) {
        String url = httpsUrl(ip, port);
        String probed = lastProbeFingerprint.get();
        String probedUrl = lastProbeUrl.get();
        if (probed == null || probedUrl == null || !probedUrl.equals(url) || !probed.equals(fingerprint)) {
            throw new InvalidRequestException("Confirm does not match the last Connect");
        }
        String existing = nexusTlsPin.storedPin();
        if (existing != null && !existing.equals(fingerprint)) {
            throw new NexusTlsPinMismatchException();
        }
        nexusTlsPin.store(fingerprint);
        nexusAddressHolder.setNexusUrl(url);
        suggestedUrl.set(null);
        lastProbeUrl.set(null);
        lastProbeFingerprint.set(null);
        mdnsDiscovery.cancelRetry();
        networkRegistrationService.registerWithNexus();
    }

    /**
     * Clears pin and URL. Next use is first contact again.
     */
    public void clearAddressAndPin() {
        nexusTlsPin.clear();
        nexusAddressHolder.clear();
        resetMemory();
    }

    /**
     * Drops in-memory suggestion and last probe. URL and pin stay as they are.
     */
    public void resetMemory() {
        suggestedUrl.set(null);
        lastProbeUrl.set(null);
        lastProbeFingerprint.set(null);
    }

    /**
     * Clears only the pin. Address stays. User must probe and confirm again.
     */
    public void clearPin() {
        nexusTlsPin.clear();
        lastProbeUrl.set(null);
        lastProbeFingerprint.set(null);
    }

    /**
     * Snapshot for the settings UI.
     */
    public Status status() {
        return new Status(
                nexusAddressHolder.getNexusUrl() == null ? "" : nexusAddressHolder.getNexusUrl(),
                nexusTlsPin.hasPin(),
                suggestedUrl.get() == null ? "" : suggestedUrl.get(),
                lastProbeFingerprint.get() == null ? "" : lastProbeFingerprint.get());
    }

    private static String httpsUrl(String ip, int port) {
        return "https://" + ip.trim() + ":" + port;
    }

    public record ProbeResult(String url, String fingerprint) {
    }

    public record Status(String url, boolean pinSet, String suggestedUrl, String pendingFingerprint) {
    }
}
