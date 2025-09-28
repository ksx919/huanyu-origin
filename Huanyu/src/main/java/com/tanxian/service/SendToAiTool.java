package com.tanxian.service;

public interface SendToAiTool {
    /**
     * 将消息发送到AI处理并返回结果的字节数组
     * 
     * @param sessionId 会话ID，用于标识特定的对话会话
     * @param message 需要发送给AI处理的消息内容
     * @param type 消息类型，使用short类型标识不同的消息类别
     * @return AI处理结果的字节数组
     */
    byte[] sendToAi(String sessionId,String message,short type);
}
