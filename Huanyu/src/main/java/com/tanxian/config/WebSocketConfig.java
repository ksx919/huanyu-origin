package com.tanxian.config;

import com.tanxian.service.impl.PcmAudioWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PcmAudioWebSocketHandler pcmHandler;
    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(PcmAudioWebSocketHandler pcmHandler, WebSocketAuthInterceptor authInterceptor) {
        this.pcmHandler = pcmHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pcmHandler, "/ws-audio")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*"); // 生产环境请设置具体域名
    }
}