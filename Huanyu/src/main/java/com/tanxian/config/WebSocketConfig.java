package com.tanxian.config;

import com.tanxian.service.PcmAudioWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PcmAudioWebSocketHandler pcmHandler;

    public WebSocketConfig(PcmAudioWebSocketHandler pcmHandler) {
        this.pcmHandler = pcmHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pcmHandler, "/ws-audio")
                .setAllowedOrigins("*"); // 生产环境请设置具体域名
    }
}