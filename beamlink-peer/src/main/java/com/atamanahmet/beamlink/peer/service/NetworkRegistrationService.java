package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import com.atamanahmet.beamlink.peer.event.NexusLostDeviceEvent;
import com.atamanahmet.beamlink.peer.event.NexusOfflineEvent;
import com.atamanahmet.beamlink.peer.event.NexusOnlineEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkRegistrationService {

    private final DeviceService deviceService;
    private final DeviceEnrollmentClient enrollmentClient;
    private final NexusAddressHolder nexusAddressHolder;
    private final NexusTlsPin nexusTlsPin;

    private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);

    /** Reset device when nexus lost the record. User re-registers. */
    @Async
    @EventListener
    public void onNexusLostDevice(NexusLostDeviceEvent event) {
        log.warn("NexusLostDeviceEvent received, reason: {}. Clearing local identity.", event.reason());
        deviceService.forceReset();
    }

    @EventListener
    public void onNexusOffline(NexusOfflineEvent event) {
        log.info("NexusOfflineEvent received. Registration will verify on reconnect.");
    }

    @EventListener
    public void onNexusOnline(NexusOnlineEvent event) {
        log.info("NexusOnlineEvent received. Checking registration state...");
        DeviceState state = deviceService.getState();
        if (state == DeviceState.UNREGISTERED) {
            registerWithNexus();
        } else if (state == DeviceState.PENDING_APPROVAL) {
            enrollmentClient.resumePollIfPending();
        }
    }

    /**
     * Register with the device public key then poll tokens.
     */
    public void registerWithNexus() {
        if (!nexusAddressHolder.isResolved() || !nexusTlsPin.hasPin()) {
            log.warn("Nexus URL or TLS pin not ready. Cannot register.");
            return;
        }
        if (!registrationInProgress.compareAndSet(false, true)) {
            log.info("Registration already in progress. Skipping.");
            return;
        }
        try {
            enrollmentClient.registerAndPoll();
        } finally {
            registrationInProgress.set(false);
        }
    }

    /**
     * Startup: enroll if unregistered, poll if pending.
     */
    public void startEnrollment() {
        DeviceState state = deviceService.getState();
        if (state == DeviceState.UNREGISTERED) {
            registerWithNexus();
        } else if (state == DeviceState.PENDING_APPROVAL) {
            enrollmentClient.resumePollIfPending();
        }
    }

    /**
     * One challenge/token recovery. Does not register.
     */
    public void recoverAfterAuthLoss() {
        enrollmentClient.recoverOnce();
    }

    public boolean isRegistrationInProgress() {
        return registrationInProgress.get();
    }
}
