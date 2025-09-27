package com.tanxian.service;

import com.tanxian.resp.ChatMessageResp;
import com.tanxian.service.impl.MyChatMemoryStoreImpl;

import java.util.List;

// ChatService.java
public interface ChatService {
    List<ChatMessageResp> getContentsBySessionId(String sessionId);
}
