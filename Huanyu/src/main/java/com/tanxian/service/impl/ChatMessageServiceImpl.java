package com.tanxian.service.impl;

import com.tanxian.mapper.ChatMessageMapper;
import com.tanxian.resp.ChatMessageResp;
import com.tanxian.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

// ChatMessageServiceImpl.java
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Override
    public List<ChatMessageResp> getContentsBySessionId(String sessionId) {
        return chatMessageMapper.findBySessionId(sessionId);
    }
}
