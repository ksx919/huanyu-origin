package com.tanxian.service;

import com.tanxian.demo.SpeechTranscriberTool;
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

    @Autowired
    SpeechTranscriberTool speechtranscribertool;
    // 重点：处理客户端发来的二进制消息
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        System.out.println(2222);
        ByteBuffer byteBuffer = message.getPayload();
        byte[] pcmBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(pcmBytes);

        System.out.println("✅ 收到 PCM 数据，长度 = " + pcmBytes.length + " 字节"); // ✅ 有没有打印？

        // 下面这一段是你提到的“示例代码”，请问你真正启用它了吗？
        try (FileOutputStream fos = new FileOutputStream("output.pcm", true)) { // 注意路径！！
            fos.write(pcmBytes);
            System.out.println("📁 PCM 数据已写入到 output.pcm"); // ✅ 有没有打印？
        } catch (IOException e) {
            e.printStackTrace(); // ✅ 有没有看到报错？
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("WebSocket 已连接，Session ID: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        System.out.println("WebSocket 已断开，Session ID: " + session.getId());
    }
}