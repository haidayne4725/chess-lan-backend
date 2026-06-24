package com.chesslan.game.common.config;

import com.chesslan.game.infrastructure.websocket.ChessHandshakeInterceptor;
import com.chesslan.game.infrastructure.websocket.ChessWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChessWebSocketHandler chessWebSocketHandler;
    private final ChessHandshakeInterceptor chessHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chessWebSocketHandler, "/ws/chess")
                .addInterceptors(chessHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
