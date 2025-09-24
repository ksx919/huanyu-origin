package com.tanxian.service;

import com.tanxian.tool.SpeechTranscriberTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class PcmAudioWebSocketHandler extends AbstractWebSocketHandler {

    @Autowired
    private ApplicationContext applicationContext;
    
    // 存储每个会话对应的SpeechTranscriberTool实例
    private final Map<String, SpeechTranscriberTool> sessionTools = new ConcurrentHashMap<>();
    // 重点：处理客户端发来的二进制消息
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer byteBuffer = message.getPayload();
        byte[] pcmBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(pcmBytes);

        // 获取当前会话对应的SpeechTranscriberTool实例
        SpeechTranscriberTool speechTranscriberTool = sessionTools.get(session.getId());
        if (speechTranscriberTool != null) {
            speechTranscriberTool.process(pcmBytes);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 为每个新连接创建一个新的SpeechTranscriberTool实例
        SpeechTranscriberTool speechTranscriberTool = applicationContext.getBean(SpeechTranscriberTool.class);
        sessionTools.put(session.getId(), speechTranscriberTool);
        System.out.println("WebSocket 已连接，Session ID: " + session.getId() + "，已创建新的SpeechTranscriberTool实例");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        String sessionId = session.getId();
        SpeechTranscriberTool speechTranscriberTool = sessionTools.get(sessionId);
        
        if (speechTranscriberTool != null) {
            // 清理资源
            if (speechTranscriberTool.transcriber != null) {
                speechTranscriberTool.transcriber.stop();
                speechTranscriberTool.transcriber.close();
            }
            // 调用shutdown方法清理NlsClient
            speechTranscriberTool.shutdown();
            // 从映射中移除
            sessionTools.remove(sessionId);
        }
        
        System.out.println("WebSocket 已断开，Session ID: " + sessionId + "，已清理SpeechTranscriberTool实例");
    }

}