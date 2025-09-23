package com.tanxian.service;

import com.tanxian.service.impl.MyChatMemoryStoreImpl;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.List;

public interface MyChatMemoryStore extends ChatMemoryStore {
    List<MyChatMemoryStoreImpl.SerializableChatMessage> getSerializableMessages(String sessionId);
}
