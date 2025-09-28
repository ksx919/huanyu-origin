package com.tanxian.handler;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanxian.service.impl.MyChatMemoryStoreImpl;
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
    @Autowired
    private MyChatMemoryStoreImpl memoryStore;
    private static final Logger LOG = LoggerFactory.getLogger(PcmAudioWebSocketHandler.class);
    private volatile String sessionId;
    // 存储每个会话对应的SpeechTranscriberTool实例
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSpeechTranscriber> sessionTools = new ConcurrentHashMap<>();
    private final Map<String, String> voiceSessionIds = new ConcurrentHashMap<>();
    // 重点：处理客户端发来的二进制消息
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof BinaryMessage binaryMessage) {
            this.handleBinaryMessage(session, binaryMessage);
            ByteBuffer byteBuffer = (ByteBuffer) message.getPayload();
            byte[] pcmBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(pcmBytes);
            // 获取当前会话对应的SpeechTranscriberTool实例
            WebSocketSpeechTranscriber webSocketSpeechTranscriber = sessionTools.get(session.getId());
            if (webSocketSpeechTranscriber != null) {
                webSocketSpeechTranscriber.process(pcmBytes,sessionId);
            }
        }
        if (message instanceof TextMessage textMessage) {
            this.handleTextMessage(session, textMessage);
            // 1. 获取前端发送的消息内容（JSON 字符串）
            String payload = (String)message.getPayload();

            // 2. 解析 JSON 为 JsonNode 对象
            JsonNode jsonNode = objectMapper.readTree(payload);
            // 先处理打断逻辑
            if (jsonNode.has("type") && "interrupt".equals(jsonNode.get("type").asText())) {
                WebSocketSpeechTranscriber webSocketSpeechTranscriber = sessionTools.get(session.getId());
                if (webSocketSpeechTranscriber != null) {
                    webSocketSpeechTranscriber.interrupt();
                }
                return;
            }
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
            // 标记该会话为临时（语音通话），避免消息入库
            memoryStore.markEphemeralSession(sessionId);
            // 记录映射，方便断开连接时取消标记
            voiceSessionIds.put(session.getId(), sessionId);
        }
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 为每个新连接创建一个新的SpeechTranscriberTool实例
        WebSocketSpeechTranscriber webSocketSpeechTranscriber = applicationContext.getBean(WebSocketSpeechTranscriber.class);
        sessionTools.put(session.getId(), webSocketSpeechTranscriber);
        // 绑定当前会话，用于将AI文本与TTS音频回传到前端
        webSocketSpeechTranscriber.bindSession(session);
        Object uidObj = session.getAttributes().get("userId");
        String nickname = String.valueOf(session.getAttributes().get("nickname"));
        LOG.info("WebSocket 已连接，Session={}, 用户Id={}, 昵称={}", session.getId(), uidObj, nickname);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        String webSocketId = session.getId();
        WebSocketSpeechTranscriber webSocketSpeechTranscriber = sessionTools.get(webSocketId);
        // 取消临时会话标记
        String ephemeralSessionId = voiceSessionIds.remove(webSocketId);
        if (ephemeralSessionId != null) {
            memoryStore.unmarkEphemeralSession(ephemeralSessionId);
        }
        
        if (webSocketSpeechTranscriber != null) {
            // 清理资源
            if (webSocketSpeechTranscriber.transcriber != null) {
                webSocketSpeechTranscriber.transcriber.stop();
                webSocketSpeechTranscriber.transcriber.close();
            }
            // 调用shutdown方法清理NlsClient
            webSocketSpeechTranscriber.shutdown();
            // 从映射中移除
            sessionTools.remove(webSocketId);
        }
        
        LOG.info("WebSocket 已断开，Session={}, 已清理SpeechTranscriberTool实例", webSocketId);
    }

}