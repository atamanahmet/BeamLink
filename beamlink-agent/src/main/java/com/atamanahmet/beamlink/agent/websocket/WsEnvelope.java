package com.atamanahmet.beamlink.agent.websocket;

import com.atamanahmet.beamlink.agent.websocket.enums.WsMessageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsEnvelope<T> {

    private WsMessageType type;
    private T payload;
    private Long version;

    public static <T> WsEnvelope<T> of(WsMessageType type, T payload) {
        return WsEnvelope.<T>builder()
                .type(type)
                .payload(payload)
                .build();
    }

    public static <T> WsEnvelope<T> of(WsMessageType type, T payload, long version) {
        return WsEnvelope.<T>builder()
                .type(type)
                .payload(payload)
                .version(version)
                .build();
    }
}