package com.tanxian.service;

import com.tanxian.tool.SpeechTranscriberTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Service
public class PcmAudioWebSocketHandler extends AbstractWebSocketHandler {

    SpeechTranscriberTool speechtranscribertool;
    // 重点：处理客户端发来的二进制消息
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer byteBuffer = message.getPayload();
        byte[] pcmBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(pcmBytes);

        speechtranscribertool.process(pcmBytes);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        speechtranscribertool =  new SpeechTranscriberTool();
        System.out.println("WebSocket 已连接，Session ID: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        if (null != speechtranscribertool.transcriber) {
            long now = System.currentTimeMillis();
            speechtranscribertool.transcriber.stop();
            speechtranscribertool.transcriber.close();
        }
        System.out.println("WebSocket 已断开，Session ID: " + session.getId());
    }

}