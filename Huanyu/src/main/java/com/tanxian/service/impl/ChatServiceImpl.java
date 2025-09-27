package com.tanxian.service.impl;

import com.tanxian.mapper.ChatMessageMapper;
import com.tanxian.resp.ChatMessageResp;
import com.tanxian.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

// ChatServiceImpl.java
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Override
    public List<ChatMessageResp> getContentsBySessionId(String sessionId) {
        return chatMessageMapper.findBySessionId(sessionId);
    }
}
