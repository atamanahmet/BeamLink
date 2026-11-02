package com.atamanahmet.beamlink.nexus.config;

import com.atamanahmet.beamlink.nexus.websocket.DeviceHandshakeInterceptor;
import com.atamanahmet.beamlink.nexus.websocket.DeviceWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeviceWebSocketHandler deviceWebSocketHandler;
    private final DeviceHandshakeInterceptor deviceHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deviceWebSocketHandler, "/ws/devices")
                .addInterceptors(deviceHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}