package com.tanxian.service.impl;

import com.tanxian.service.AiChatService;
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

    @Override
    public Flux<String> chat(String sessionId, String message, short type) {
        switch (type){
            case 0: return yoimiyaService.chat(sessionId, message);
            case 1: return ventiService.chat(sessionId, message);
            case 2: return huTaoService.chat(sessionId, message);
            default: return yoimiyaService.chat(sessionId, message);
        }
    }
}
