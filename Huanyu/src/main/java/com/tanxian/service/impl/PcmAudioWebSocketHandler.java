package com.tanxian.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class PcmAudioWebSocketHandler extends AbstractWebSocketHandler {

    @Autowired
    private ApplicationContext applicationContext;
    private static final Logger LOG = LoggerFactory.getLogger(PcmAudioWebSocketHandler.class);
    private volatile String sessionId;
    // 存储每个会话对应的SpeechTranscriberTool实例
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SpeechTranscriberTool> sessionTools = new ConcurrentHashMap<>();
    // 重点：处理客户端发来的二进制消息
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof BinaryMessage binaryMessage) {
            this.handleBinaryMessage(session, binaryMessage);
            ByteBuffer byteBuffer = (ByteBuffer) message.getPayload();
            byte[] pcmBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(pcmBytes);
            // 获取当前会话对应的SpeechTranscriberTool实例
            SpeechTranscriberTool speechTranscriberTool = sessionTools.get(session.getId());
            if (speechTranscriberTool != null) {
                speechTranscriberTool.process(pcmBytes,sessionId);
            }
        }
        if (message instanceof TextMessage textMessage) {
            this.handleTextMessage(session, textMessage);
            // 1. 获取前端发送的消息内容（JSON 字符串）
            String payload = (String)message.getPayload();

            // 2. 解析 JSON 为 JsonNode 对象
            JsonNode jsonNode = objectMapper.readTree(payload);
            String characterName = jsonNode.get("characterId").asText();
            // 从握手拦截器注入的会话属性中获取用户ID
            Object uidObj = session.getAttributes().get("userId");
            long userId = 0L;
            if (uidObj instanceof Number) {
                userId = ((Number) uidObj).longValue();
            } else if (uidObj != null) {
                try { userId = Long.parseLong(String.valueOf(uidObj)); } catch (NumberFormatException ignored) {}
            }

            LOG.info("WebSocket消息：用户Id={}, 角色={}", userId, characterName);
            sessionId = String.valueOf(userId);
            sessionId += switch (characterName) {
                case "Hutao" -> "2";
                case "Venti" -> "1";
                case "Xiaogong" -> "0";
                default -> "unknown";
            };
        }
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 为每个新连接创建一个新的SpeechTranscriberTool实例
        SpeechTranscriberTool speechTranscriberTool = applicationContext.getBean(SpeechTranscriberTool.class);
        sessionTools.put(session.getId(), speechTranscriberTool);
        Object uidObj = session.getAttributes().get("userId");
        String nickname = String.valueOf(session.getAttributes().get("nickname"));
        LOG.info("WebSocket 已连接，Session={}, 用户Id={}, 昵称={}", session.getId(), uidObj, nickname);
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
        
        LOG.info("WebSocket 已断开，Session={}, 已清理SpeechTranscriberTool实例", sessionId);
    }

}