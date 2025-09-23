package com.tanxian.service;

import com.tanxian.service.impl.MyChatMemoryStoreImpl;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 自定义聊天记忆存储接口
 * 继承 LangChain4j 的 ChatMemoryStore 接口，并添加自定义方法
 */
public interface MyChatMemoryStore extends ChatMemoryStore {

    /**
     * 记录会话信息
     * @param sessionId 会话ID
     * @param characterType 角色类型
     */
    void recordChatSession(String sessionId, Short characterType);

    /**
     * 获取可序列化的消息列表，用于API返回
     * @param sessionId 会话ID
     * @return 可序列化的消息列表
     */
    java.util.List<MyChatMemoryStoreImpl.SerializableChatMessage> getSerializableMessages(String sessionId);
}
