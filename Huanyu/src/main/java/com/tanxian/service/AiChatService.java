package com.tanxian.service;

import reactor.core.publisher.Flux;

public interface AiChatService {

    /**
     * 与AI角色进行对话
     * @param sessionId 会话ID
     * @param message 用户消息
     * @param type 角色类型: 0=宵宫, 1=温迪, 2=胡桃
     * @return 流式响应结果
     */
    Flux<String> chat(String sessionId,String message,short type);
}