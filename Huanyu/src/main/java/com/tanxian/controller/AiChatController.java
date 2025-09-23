package com.tanxian.controller;

import com.tanxian.service.AiChatService;
import com.tanxian.service.MyChatMemoryStore;
import com.tanxian.service.impl.MyChatMemoryStoreImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/ai")
@Tag(name = "AI对话接口", description = "提供与AI角色进行对话的接口")
public class AiChatController {
    
    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private MyChatMemoryStore myChatMemoryStore;

    @GetMapping("/chat")
    @Operation(summary = "与AI角色对话", description = "根据指定的角色类型与AI进行对话")
    public Flux<String> chat(
            @Parameter(name = "sessionId", description = "会话ID", required = true) String sessionId,
            @Parameter(name = "message", description = "用户消息", required = true) String message,
            @Parameter(name = "type", description = "角色类型: 0=宵宫, 1=温迪, 2=胡桃", required = true) short type){
        return aiChatService.chat(sessionId,message,type);
    }

    //根据sessionId获取聊天记录
    @GetMapping("/getChat")
    @Operation(summary = "获取聊天记录", description = "根据指定的会话ID获取聊天记录")
    public List<MyChatMemoryStoreImpl.SerializableChatMessage> getMessage(String sessionId){
        return myChatMemoryStore.getSerializableMessages(sessionId);
    }
}