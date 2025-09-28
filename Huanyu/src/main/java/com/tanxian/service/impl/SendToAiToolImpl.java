package com.tanxian.service.impl;

import com.tanxian.service.AiChatService;
import com.tanxian.service.SendToAiTool;
import dev.langchain4j.service.spring.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class SendToAiToolImpl implements SendToAiTool {

    @Autowired
    AiChatService aiChatService;
    @Override
    public byte[] sendToAi(String sessionId, String message, short type) {
        //Mark:SessionId and type must receive from front
        aiChatService.chat(sessionId,message,type);
        return new byte[0];
    }
}
