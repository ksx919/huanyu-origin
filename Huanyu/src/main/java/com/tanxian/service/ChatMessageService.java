package com.tanxian.service;

import com.tanxian.resp.ChatMessageResp;

import java.util.List;

// ChatMessageService.java
public interface ChatMessageService {
    List<ChatMessageResp> getContentsBySessionId(String sessionId);
}
