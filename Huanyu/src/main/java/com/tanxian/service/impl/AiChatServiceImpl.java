package com.tanxian.service.impl;

import com.tanxian.exception.BusinessException;
import com.tanxian.exception.BusinessExceptionEnum;
import com.tanxian.service.AiChatService;
import com.tanxian.service.MyChatMemoryStore;
import com.tanxian.service.ai.HuTaoService;
import com.tanxian.service.ai.VentiService;
import com.tanxian.service.ai.YoimiyaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiChatServiceImpl implements AiChatService {
    @Autowired
    private YoimiyaService yoimiyaService;

    @Autowired
    private VentiService ventiService;

    @Autowired
    private HuTaoService huTaoService;

    @Autowired
    private MyChatMemoryStore myChatMemoryStore;

    @Override
    public Flux<String> chat(String sessionId, String message, short type) {
        // 记录会话信息（如果是新会话）
        myChatMemoryStore.recordChatSession(sessionId, type);
        
        return switch (type) {
            case 0 -> yoimiyaService.chat(sessionId, message);
            case 1 -> ventiService.chat(sessionId, message);
            case 2 -> huTaoService.chat(sessionId, message);
            default -> throw new BusinessException(BusinessExceptionEnum.CHAT_TYPE_ERROR);
        };
    }
}
