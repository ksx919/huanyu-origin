package com.tanxian.controller;

import com.tanxian.service.YoimiyaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private YoimiyaService yoimiyaService;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @GetMapping( value = "/chat" ,produces = "text/html;charset=utf-8")
    public Flux<String> chat(String sessionId,String message){
        return yoimiyaService.chat(sessionId,message);
    }

    @GetMapping("/redis")
    public String redis(String message){
        return redisTemplate.opsForValue().get(message);
    }
}
