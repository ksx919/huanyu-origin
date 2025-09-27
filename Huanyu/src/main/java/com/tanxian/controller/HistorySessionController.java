package com.tanxian.controller;

import com.tanxian.common.LoginUserContext;
import com.tanxian.resp.ChatMessageResp;
import com.tanxian.service.AiChatService;
import com.tanxian.service.ChatService;
import com.tanxian.service.MyChatMemoryStore;
import com.tanxian.service.impl.MyChatMemoryStoreImpl;
import io.lettuce.core.dynamic.annotation.Param;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@Tag(name = "历史记录接口", description = "根据sessionId获取历史聊天记录")
public class HistorySessionController {

    @Autowired
    private ChatService chatService;

    //根据sessionId获取聊天记录
    @GetMapping("/getchat")
    @Operation(summary = "获取聊天记录", description = "根据指定的会话ID获取聊天记录")
    public List<ChatMessageResp> getMessage(@RequestParam @Param("type") short type){
        String sessionId = LoginUserContext.getId()+""+type;
        return chatService.getContentsBySessionId(sessionId);
    }
}