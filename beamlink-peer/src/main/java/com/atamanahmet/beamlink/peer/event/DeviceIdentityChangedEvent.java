package com.atamanahmet.beamlink.peer.event;

import org.springframework.context.ApplicationEvent;

public class DeviceIdentityChangedEvent extends ApplicationEvent {
    public DeviceIdentityChangedEvent(Object source) {
        super(source);
    }
}