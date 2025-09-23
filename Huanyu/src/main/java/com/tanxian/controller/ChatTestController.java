package com.tanxian.controller;

import com.tanxian.repository.MyChatMemoryStore;
import com.tanxian.service.AiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/test")
public class ChatTestController {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private MyChatMemoryStore myChatMemoryStore;

    @GetMapping( value = "/chat" ,produces = "text/html;charset=utf-8")
    public Flux<String> chat(String sessionId,String message,short type){
        return aiChatService.chat(sessionId,message,type);
    }

    @GetMapping("/redis")
    public String redis(String message){
        return redisTemplate.opsForValue().get(message);
    }

    @GetMapping("/getChat")
    public List<MyChatMemoryStore.SerializableChatMessage> getMessage(String sessionId){
        return myChatMemoryStore.getSerializableMessages(sessionId);
    }
}