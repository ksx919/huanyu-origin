package com.tanxian.service;

public interface SendToAiTool {
    byte[] sendToAi(String sessionId,String message,short type);
}
