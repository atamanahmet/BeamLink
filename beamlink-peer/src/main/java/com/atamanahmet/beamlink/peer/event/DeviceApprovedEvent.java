package com.atamanahmet.beamlink.peer.event;

import org.springframework.context.ApplicationEvent;

public class DeviceApprovedEvent extends ApplicationEvent {
    public DeviceApprovedEvent(Object source) {
        super(source);
    }
}