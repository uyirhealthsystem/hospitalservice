package com.uyir.hospital.live;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// Plain WebSocket (no STOMP): any web or mobile client connects with its standard WebSocket API.
@Configuration
@EnableWebSocket
public class LiveWebSocketConfig implements WebSocketConfigurer {

    public static final String PATH = "/api/v1/hospital/ws";

    private final LiveWebSocketHandler handler;
    private final LiveHandshakeInterceptor handshakeInterceptor;
    private final LiveProperties properties;

    public LiveWebSocketConfig(
            LiveWebSocketHandler handler, LiveHandshakeInterceptor handshakeInterceptor, LiveProperties properties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // HospitalController's GET /api/v1/hospital/{id} would otherwise take /ws (as hospital id "ws") and 404:
        // check the WebSocket path before the @RequestMapping controllers (order 0).
        if (registry instanceof ServletWebSocketHandlerRegistry servletRegistry) {
            servletRegistry.setOrder(-1);
        }
        registry.addHandler(handler, PATH)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
    }
    // Incoming messages use Tomcat's default 8 KB cap (clients only send tiny PINGs); a dead connection is
    // found and dropped when the 10s ping to it fails (LiveUpdateHub.heartbeat).
}
