package com.tanxian.service;

import java.io.InputStream;

public interface MessageTurnToAiVoiceTool {
    /**
     * 将文本消息转换为AI语音字节数据
     * 
     * @param message 需要转换为语音的文本消息
     * @param sessionId 会话ID，用于标识特定的对话会话
     * @return 包含AI语音数据的字节数组
     */
    byte[] turnToAiVoice(String message,String sessionId);

    /**
     * 将文本消息转换为AI语音输入流
     * 
     * @param message 需要转换为语音的文本消息
     * @param sessionId 会话ID，用于标识特定的对话会话
     * @return 包含AI语音数据的输入流
     */
    InputStream streamToAiVoice(String message, String sessionId);
}
