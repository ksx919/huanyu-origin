package com.tanxian.service;

import com.tanxian.tool.SpeechTranscriberTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@Service
public class PcmAudioWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(PcmAudioWebSocketHandler.class);

    @Autowired
    SpeechTranscriberTool speechtranscribertool;
    
    // 重点：处理客户端发来的二进制消息
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        logger.info("收到来自客户端的二进制消息，Session ID: {}", session.getId());
        
        ByteBuffer byteBuffer = message.getPayload();
        byte[] pcmBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(pcmBytes);
        
        logger.info("PCM音频数据大小: {} bytes", pcmBytes.length);

        // 下面这一段是你提到的"示例代码"，请问你真正启用它了吗？
        try (FileOutputStream fos = new FileOutputStream("output.pcm", true)) {
            fos.write(pcmBytes);
            logger.info("PCM音频数据已写入文件 output.pcm");
        } catch (IOException e) {
            logger.error("写入PCM音频文件时发生错误: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket连接已建立，Session ID: {}, 远程地址: {}", 
                   session.getId(), session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        logger.info("WebSocket连接已关闭，Session ID: {}, 关闭状态: {} - {}", 
                   session.getId(), status.getCode(), status.getReason());
    }
}