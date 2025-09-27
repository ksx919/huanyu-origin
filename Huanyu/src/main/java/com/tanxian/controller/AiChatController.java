package com.tanxian.controller;

import com.tanxian.common.LoginUserContext;
import com.tanxian.resp.ChatMessageResp;
import com.tanxian.service.AiChatService;
import com.tanxian.service.ChatMessageService;
import io.lettuce.core.dynamic.annotation.Param;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private ChatMessageService chatMessageService;


    @GetMapping(value = "/chat" ,produces = "text/html;charset=utf-8")
    @Operation(summary = "与AI角色对话", description = "根据指定的角色类型与AI进行对话")
    public Flux<String> chat(
            @Parameter(name = "message", description = "用户消息", required = true) String message,
            @Parameter(name = "type", description = "角色类型: 0=宵宫, 1=温迪, 2=胡桃", required = true) short type){
        String sessionId = LoginUserContext.getId()+""+type;
        return aiChatService.chat(sessionId,message,type);
    }

    @GetMapping("/get-chat")
    @Operation(summary = "获取聊天记录", description = "根据指定的会话ID获取聊天记录")
    public List<ChatMessageResp> getMessage(@RequestParam @Param("type") short type){
        String sessionId = LoginUserContext.getId()+""+type;
        return chatMessageService.getContentsBySessionId(sessionId);
    }
}