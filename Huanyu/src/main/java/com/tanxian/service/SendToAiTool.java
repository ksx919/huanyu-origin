package com.tanxian.service;

public interface SendToAiTool {
    public byte[] sendToAi(String sessionId,String message,short type);
}
