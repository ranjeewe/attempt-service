package com.mcqbuddy.attempt.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AttemptTimerWebSocketConfig implements WebSocketConfigurer {

    private final AttemptTimerWebSocketHandler handler;

    public AttemptTimerWebSocketConfig(AttemptTimerWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/attempt-ws")
                .setAllowedOrigins("*");
    }
}

