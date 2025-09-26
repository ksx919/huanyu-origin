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
        Flux<String> aiReplies = aiChatService.chat(sessionId,message,type);
        aiReplies.subscribe(
                reply -> System.out.println("【AI回复】：" + reply), // onNext 事件（收到数据）
                error -> System.err.println("【错误】：" + error.getMessage()), // onError 事件（发生异常）
                () -> System.out.println("【流结束】") // onComplete 事件（流完成）
        );

        return new byte[0];
    }
}
