package com.atamanahmet.beamlink.peer.event;

import com.atamanahmet.beamlink.peer.websocket.WsEnvelope;
import tools.jackson.databind.JsonNode;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WsMessageEvent extends ApplicationEvent {

    private final WsEnvelope<JsonNode> message;

    public WsMessageEvent(Object source, WsEnvelope<JsonNode> message) {
        super(source);
        this.message = message;
    }
}